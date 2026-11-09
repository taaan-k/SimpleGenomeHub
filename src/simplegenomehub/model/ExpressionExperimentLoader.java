package simplegenomehub.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;
import simplegenomehub.util.fileio.FileSetTransaction;
import simplegenomehub.util.fileio.JsonMetadataSupport;

/** One read-only discovery path for gene queries and expression management. */
public final class ExpressionExperimentLoader {
    private static final Logger LOGGER = Logger.getLogger(ExpressionExperimentLoader.class.getName());
    // Values do not retain SpeciesInfo, so closing a Hub can release its matrices.
    private static final Map<SpeciesInfo, Cached> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<SpeciesInfo, Object> LOAD_MONITORS = new WeakHashMap<>();
    private ExpressionExperimentLoader() { }

    public enum IssueKind {
        DIRECTORY_UNREADABLE, METADATA_ENCODING, METADATA_MISSING, INVALID_METADATA, DATA_TYPE_REQUIRED,
        MATRIX_MISSING, MATRIX_INVALID, UNFINISHED_PUBLICATION, DATA_CHANGED
    }

    public static final class Issue {
        private final IssueKind kind;
        private final File file;
        private final String message;
        Issue(IssueKind kind, File file, String message) {
            this.kind = kind; this.file = file; this.message = message;
        }
        public IssueKind getKind() { return kind; }
        public File getFile() { return file; }
        public String getMessage() { return message; }
        @Override public String toString() {
            return (file != null ? file.getName() + ": " : "") + message;
        }
    }

    public static final class LoadResult {
        private final File directory;
        private final List<ExpressionExperiment> experiments;
        private final List<Issue> issues;
        LoadResult(File directory, List<ExpressionExperiment> experiments, List<Issue> issues) {
            this.directory = directory;
            this.experiments = Collections.unmodifiableList(new ArrayList<>(experiments));
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        }
        public List<ExpressionExperiment> getExperiments() { return experiments; }
        public List<Issue> getIssues() { return issues; }
        public File getDirectory() { return directory; }
        public int getReadyMatrixCount() {
            int count = 0;
            for (ExpressionExperiment experiment : experiments) count += experiment.getMatrices().size();
            return count;
        }
        public String getSummary() {
            if (experiments.isEmpty() && issues.isEmpty()) return "No expression experiments imported for this genome.";
            return experiments.size() + " expression experiments; " + getReadyMatrixCount() + " matrices ready"
                + (issues.isEmpty() ? "." : "; " + issues.size() + " item(s) need attention.");
        }
        public String getDetails() {
            StringBuilder text = new StringBuilder(getSummary());
            text.append("\nDirectory: ").append(directory != null ? directory.getAbsolutePath() : "not configured");
            for (Issue issue : issues) text.append("\n\n").append(issue);
            return text.toString();
        }
        private boolean cacheable() {
            return issues.stream().allMatch(issue -> issue.kind == IssueKind.DATA_TYPE_REQUIRED);
        }
    }

    public static void invalidate(SpeciesInfo species) { CACHE.remove(species); }

    public static LoadResult load(SpeciesInfo species) {
        if (species == null) return failure(null, IssueKind.DIRECTORY_UNREADABLE, "No genome is selected.");
        final Object monitor;
        synchronized (LOAD_MONITORS) { monitor = LOAD_MONITORS.computeIfAbsent(species, ignored -> new Object()); }
        // Do not hold the model's monitor during matrix I/O: EDT model reads stay responsive.
        synchronized (monitor) {
            File directory = species.getExpressionDir();
            if (directory == null) return failure(null, IssueKind.DIRECTORY_UNREADABLE, "Expression directory is not configured.");
            try {
                for (int attempt = 0; attempt < 2; attempt++) {
                    Snapshot before = snapshot(directory);
                    Cached cached = CACHE.get(species);
                    if (cached != null && cached.version.equals(before.version)) {
                        species.replaceExpressionExperiments(cached.result.experiments);
                        return cached.result;
                    }
                    LoadResult result = read(before);
                    Snapshot after = snapshot(directory);
                    if (!before.version.equals(after.version)) { CACHE.remove(species); continue; }
                    species.replaceExpressionExperiments(result.experiments);
                    if (result.cacheable()) CACHE.put(species, new Cached(after.version, result));
                    else CACHE.remove(species);
                    return result;
                }
                CACHE.remove(species);
                return failure(directory, IssueKind.DATA_CHANGED, "Expression files changed while loading. Please search again.");
            } catch (IOException | SecurityException failure) {
                CACHE.remove(species);
                return failure(directory, IssueKind.DIRECTORY_UNREADABLE,
                    "Cannot read expression directory. Check the path and permissions: " + failure.getMessage());
            }
        }
    }

    private static LoadResult read(Snapshot snapshot) {
        List<ExpressionExperiment> experiments = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path metadata : snapshot.metadata) {
            if (!FileSetTransaction.isPublished(metadata)) {
                issues.add(new Issue(IssueKind.UNFINISHED_PUBLICATION, metadata.toFile(),
                    "Publication is unfinished. This experiment is not yet available."));
                continue;
            }
            try {
                ExpressionExperiment experiment = ExpressionExperiment.loadMetadataChecked(metadata.toFile());
                if (!ids.add(experiment.getExperimentId())) throw new IOException("Duplicate experiment_id: " + experiment.getExperimentId());
                experiments.add(experiment);
                Issue issue = loadMatrix(experiment, snapshot.directory);
                if (issue != null) issues.add(issue);
            } catch (IOException failure) {
                IssueKind kind = failure instanceof JsonMetadataSupport.MetadataReadException
                    && ((JsonMetadataSupport.MetadataReadException) failure).getKind() == JsonMetadataSupport.FailureKind.ENCODING
                    ? IssueKind.METADATA_ENCODING : IssueKind.INVALID_METADATA;
                issues.add(new Issue(kind, metadata.toFile(), failure.getMessage()));
                LOGGER.warning("Unable to load expression metadata " + metadata + ": " + failure.getMessage());
            }
        }
        for (Path matrix : snapshot.matrices) {
            String name = matrix.getFileName().toString();
            Path metadata = matrix.resolveSibling(name.substring(0, name.length() - ".matrix.tsv".length()) + ".metadata.json");
            if (!Files.exists(metadata) && FileSetTransaction.isPublished(matrix))
                issues.add(new Issue(IssueKind.METADATA_MISSING, metadata.toFile(),
                    "The matrix exists but its experiment metadata is missing. Restore the metadata or import the experiment again."));
        }
        return new LoadResult(snapshot.directory, experiments, issues);
    }

    private static Issue loadMatrix(ExpressionExperiment experiment, File directory) {
        File matrixFile = new File(directory, experiment.getExperimentId() + ".matrix.tsv");
        if (!FileSetTransaction.isPublished(matrixFile.toPath()))
            return new Issue(IssueKind.UNFINISHED_PUBLICATION, matrixFile, "Matrix publication is unfinished.");
        if (!matrixFile.isFile()) return new Issue(IssueKind.MATRIX_MISSING, matrixFile, "Expression matrix is missing.");
        if (experiment.getStoredDataType() == null)
            return new Issue(IssueKind.DATA_TYPE_REQUIRED, experiment.getMetadataFile(),
                "The original measurement type is not recorded. Open Expression Data Management and use "
                + "Set Expression Data Type before loading this matrix. Original values will not be converted.");
        ExpressionMatrix matrix = new ExpressionMatrix(experiment.getExperimentId() + "_matrix",
            experiment.getTitle(), experiment.getStoredDataType());
        if (!matrix.loadFromFile(matrixFile))
            return new Issue(IssueKind.MATRIX_INVALID, matrixFile, "Cannot parse expression matrix: " + matrix.getValidationErrors());
        experiment.addMatrix(matrix);
        return null;
    }

    private static LoadResult failure(File directory, IssueKind kind, String message) {
        return new LoadResult(directory, Collections.emptyList(),
            Collections.singletonList(new Issue(kind, directory, message)));
    }

    private static Snapshot snapshot(File directory) throws IOException {
        File canonical = directory.getCanonicalFile();
        Path root = canonical.toPath();
        List<Path> files = new ArrayList<>();
        if (Files.exists(root)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
                for (Path file : children) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".metadata.json") || name.endsWith(".matrix.tsv")) files.add(file);
                }
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        StringBuilder version = new StringBuilder(canonical.getPath());
        List<Path> metadata = new ArrayList<>();
        List<Path> matrices = new ArrayList<>();
        for (Path file : files) {
            version.append('\n').append(file.getFileName()).append(':');
            try {
                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                version.append(attributes.size()).append(':').append(attributes.lastModifiedTime())
                    .append(':').append(attributes.fileKey()).append(':').append(FileSetTransaction.isPublished(file));
            } catch (IOException failure) { version.append("unreadable:").append(failure.getClass().getName()); }
            if (file.getFileName().toString().endsWith(".metadata.json")) {
                metadata.add(file);
                try { version.append(':').append(Arrays.toString(sha256(Files.readAllBytes(file)))); }
                catch (IOException failure) { version.append(":unreadable"); }
            } else matrices.add(file);
        }
        return new Snapshot(canonical, metadata, matrices, version.toString());
    }

    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static final class Snapshot {
        final File directory; final List<Path> metadata; final List<Path> matrices; final String version;
        Snapshot(File directory, List<Path> metadata, List<Path> matrices, String version) {
            this.directory = directory; this.metadata = metadata; this.matrices = matrices; this.version = version;
        }
    }
    private static final class Cached {
        final String version; final LoadResult result;
        Cached(String version, LoadResult result) { this.version = version; this.result = result; }
    }
}
