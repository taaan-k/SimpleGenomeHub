package simplegenomehub.util.fileio;

import biocjava.GUIexcutors.TBtools;
import biocjava.bioDoer.Eggnog.EggnogDbConstants;
import biocjava.bioDoer.Eggnog.EggnogDbProvider;
import biocjava.bioDoer.Kegg.KeggBackEndConstants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import eggnogmapper.cli.CliArgs;
import eggnogmapper.cli.CliParser;
import eggnogmapper.emapper.EmapperPipeline;
import simplegenomehub.config.ApplicationLayout;
import simplegenomehub.model.GeneAnnotationData;
import simplegenomehub.model.SpeciesInfo;

import javax.swing.*;
import java.awt.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs local eggNOG-mapper on the selected species and integrates the results
 * into the existing functional annotation storage layout.
 */
public final class AutoFunctionalAnnotationService {

    private static final Logger logger = Logger.getLogger(AutoFunctionalAnnotationService.class.getName());

    public interface ProgressListener {
        void onProgress(String message);
    }

    public static final class Parameters {
        private final String taxScope;
        private final int cpu;
        private final String evalue;
        private final String outputPrefix;
        private final KeggBackendManager.BackendMode keggBackendMode;
        private final String keggBackendType;
        private final File customKeggBackendFile;

        public Parameters(String taxScope, int cpu, String evalue, String outputPrefix,
                          KeggBackendManager.BackendMode keggBackendMode,
                          String keggBackendType, File customKeggBackendFile) {
            this.taxScope = taxScope;
            this.cpu = cpu;
            this.evalue = evalue;
            this.outputPrefix = outputPrefix;
            this.keggBackendMode = keggBackendMode;
            this.keggBackendType = keggBackendType;
            this.customKeggBackendFile = customKeggBackendFile;
        }

        public String getTaxScope() { return taxScope; }
        public int getCpu() { return cpu; }
        public String getEvalue() { return evalue; }
        public String getOutputPrefix() { return outputPrefix; }
        public KeggBackendManager.BackendMode getKeggBackendMode() { return keggBackendMode; }
        public String getKeggBackendType() { return keggBackendType; }
        public File getCustomKeggBackendFile() { return customKeggBackendFile; }
    }

    public static final class Result {
        private final File rawOutputDir;
        private final Map<GeneAnnotationData.AnnotationType, Integer> importedCounts;

        public Result(File rawOutputDir, Map<GeneAnnotationData.AnnotationType, Integer> importedCounts) {
            this.rawOutputDir = rawOutputDir;
            this.importedCounts = importedCounts;
        }

        public File getRawOutputDir() { return rawOutputDir; }
        public Map<GeneAnnotationData.AnnotationType, Integer> getImportedCounts() { return importedCounts; }
    }

    private AutoFunctionalAnnotationService() {
    }

    public static Result run(Component parent, SpeciesInfo species, Parameters parameters,
                             ProgressListener progressListener) throws Exception {
        if (species == null) {
            throw new IllegalArgumentException("Species is required");
        }

        File genomeFile = species.getGenomeFile();
        File annotationFile = species.getAnnotationFile();
        if (genomeFile == null || !genomeFile.isFile()) {
            throw new IllegalStateException("Genome FASTA is missing for species: " + species.getSpeciesDirectoryName());
        }
        if (annotationFile == null || !annotationFile.isFile()) {
            throw new IllegalStateException("Annotation GFF/GTF is missing for species: " + species.getSpeciesDirectoryName());
        }

        File proteinFile = ensureRepresentativeProteins(species, genomeFile, annotationFile, progressListener);
        File outputDir = new File(species.getFunctionalAnnotationDir(), "eggNOG");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Cannot create eggNOG output directory: " + outputDir.getAbsolutePath());
        }

        report(progressListener, "Resolving eggNOG database...");
        File eggnogDbDir = ensureEggnogDatabase(parent, parameters.getTaxScope());

        report(progressListener, "Running eggNOG-mapper...");
        runEggnogPipeline(outputDir, proteinFile, eggnogDbDir, parameters);

        File annotationsFile = new File(outputDir, parameters.getOutputPrefix() + ".emapper.annotations");
        if (!annotationsFile.isFile()) {
            throw new FileNotFoundException("eggNOG output not found: " + annotationsFile.getAbsolutePath());
        }

        report(progressListener, "Splitting eggNOG annotation outputs...");
        runEggnogHelper(annotationsFile, outputDir);

        report(progressListener, "Preparing GO OBO resource...");
        File goOboFile = ensureGoObo(parent, species);

        report(progressListener, "Preparing KEGG backend...");
        File keggBackendFile = ensureKeggBackend(parent, species, parameters);

        report(progressListener, "Importing annotations into species database...");
        Result result = importOutputs(species, annotationFile, outputDir, parameters, goOboFile, keggBackendFile);

        report(progressListener, "Writing run metadata...");
        writeRunMetadata(species, proteinFile, outputDir, parameters, goOboFile, keggBackendFile, result);

        report(progressListener, "Auto annotation completed.");
        return result;
    }

    private static File ensureRepresentativeProteins(SpeciesInfo species, File genomeFile, File annotationFile,
                                                     ProgressListener progressListener) throws Exception {
        File proteinFile = new File(species.getSequenceDir(), species.getSpeciesDirectoryName() + ".proteins.fasta");
        if (proteinFile.isFile() && proteinFile.length() > 0) {
            return proteinFile;
        }

        report(progressListener, "Representative proteins not found. Rebuilding from genome and annotation...");
        TBtoolsSequenceExtractor.ExtractionResult extractionResult =
            TBtoolsSequenceExtractor.extractRepresentativeProteins(genomeFile, annotationFile, proteinFile, "LONGEST_CDS");
        if (!extractionResult.isSuccess() || !proteinFile.isFile()) {
            throw new IOException("Failed to build representative proteins: " + extractionResult.getMessage());
        }
        return proteinFile;
    }

    private static File ensureEggnogDatabase(Component parent, String taxScope) throws Exception {
        if (!EggnogDbConstants.isKnownTaxScope(taxScope)) {
            throw new IllegalArgumentException("Unsupported eggNOG tax scope: " + taxScope);
        }

        File homeDir = TBtools.USERHOME();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<File> resolved = new AtomicReference<>();
        EggnogDbProvider.ensureDatabaseAndThen(homeDir, taxScope, parent, resolved::set, latch::countDown);
        latch.await(6, TimeUnit.HOURS);

        File categoryDir = resolved.get();
        if (categoryDir == null) {
            categoryDir = EggnogDbProvider.getCategoryDir(homeDir, taxScope);
        }
        if (!EggnogDbProvider.isDatabaseReady(categoryDir)) {
            throw new IOException("eggNOG database unavailable for tax scope: " + taxScope);
        }
        return categoryDir;
    }

    private static File ensureGoObo(Component parent, SpeciesInfo species) throws Exception {
        GoOboManager.ResolutionResult resolution = GoOboManager.resolveForSpecies(species, null);
        if (resolution.exists()) {
            return resolution.getResolvedFile();
        }

        CountDownLatch latch = new CountDownLatch(1);
        GoOboManager.downloadLatestToGlobalCache(parent, latch::countDown);
        latch.await(2, TimeUnit.HOURS);

        GoOboManager.ResolutionResult refreshed = GoOboManager.resolveForSpecies(species, null);
        if (!refreshed.exists()) {
            throw new IOException("GO OBO resource is unavailable after download.");
        }

        GoOboManager.cacheOboForSpecies(species, refreshed.getResolvedFile(), true);
        return GoOboManager.resolveForSpecies(species, null).getResolvedFile();
    }

    private static File ensureKeggBackend(Component parent, SpeciesInfo species, Parameters parameters) throws Exception {
        KeggBackendManager.ResolutionResult resolution = KeggBackendManager.resolveForSpecies(
            species, parameters.getKeggBackendMode(), parameters.getKeggBackendType(), parameters.getCustomKeggBackendFile());
        if (resolution.exists()) {
            if (parameters.getKeggBackendMode() == KeggBackendManager.BackendMode.CUSTOM) {
                KeggBackendManager.cacheCustomForSpecies(species, resolution.getResolvedFile());
                return KeggBackendManager.getSpeciesCustomBackendFile(species);
            }
            return resolution.getResolvedFile();
        }

        if (parameters.getKeggBackendMode() == KeggBackendManager.BackendMode.CUSTOM) {
            throw new IOException("Custom KEGG backend is required but not available.");
        }

        CountDownLatch latch = new CountDownLatch(1);
        KeggBackendManager.downloadLatestPresetBackend(parent, parameters.getKeggBackendType(), latch::countDown);
        latch.await(2, TimeUnit.HOURS);

        File globalBackend = KeggBackendManager.getGlobalPresetBackendFile(parameters.getKeggBackendType());
        if (globalBackend == null || !globalBackend.isFile()) {
            throw new IOException("Preset KEGG backend is unavailable after download.");
        }
        if (!KeggBackendManager.cachePresetForSpecies(species, parameters.getKeggBackendType(), globalBackend)) {
            throw new IOException("Failed to cache preset KEGG backend for species.");
        }

        File speciesBackend = KeggBackendManager.getSpeciesPresetBackendFile(species, parameters.getKeggBackendType());
        if (speciesBackend == null || !speciesBackend.isFile()) {
            throw new IOException("Species-local KEGG backend was not created.");
        }
        return speciesBackend;
    }

    private static void runEggnogPipeline(File outputDir, File proteinFile, File eggnogDbDir,
                                          Parameters parameters) throws Exception {
        ensureDiamondAvailable();
        String dmndPath = new File(eggnogDbDir, EggnogDbConstants.getDiamondDbFileName(parameters.getTaxScope())).getAbsolutePath();
        String[] args = new String[]{
            "-m", "diamond",
            "-i", proteinFile.getAbsolutePath(),
            "-o", parameters.getOutputPrefix(),
            "--output_dir", outputDir.getAbsolutePath(),
            "--data_dir", eggnogDbDir.getAbsolutePath(),
            "--dmnd_db", dmndPath,
            "--cpu", String.valueOf(parameters.getCpu()),
            "--evalue", parameters.getEvalue(),
            "--override"
        };
        CliArgs cliArgs = CliParser.parse(args);
        EmapperPipeline pipeline = new EmapperPipeline(cliArgs);
        pipeline.run();
    }

    private static void ensureDiamondAvailable() throws Exception {
        Process process = new ProcessBuilder("diamond", "version").redirectErrorStream(true).start();
        boolean done = process.waitFor(45, TimeUnit.SECONDS);
        if (!done || process.exitValue() != 0) {
            throw new IOException("Cannot run diamond. Place diamond under "
                + new File(ApplicationLayout.getAppHomeDirectory(), "bin\\tools").getAbsolutePath()
                + " or ensure it is available on PATH.");
        }
    }

    private static void runEggnogHelper(File annotationsFile, File outputDir) throws Exception {
        biocjava.bioIO.BioSoftPipeServer.eggnogMapperHelper helper =
            new biocjava.bioIO.BioSoftPipeServer.eggnogMapperHelper();
        helper.setInEggNOGMapperAnno(annotationsFile);
        helper.setOutDir(outputDir);
        helper.process();
    }

    private static Result importOutputs(SpeciesInfo species, File annotationFile, File outputDir,
                                        Parameters parameters, File goOboFile, File keggBackendFile) throws Exception {
        GeneAnnotationData annotationData = new GeneAnnotationData(
            species.getSpeciesDirectoryName() + "_auto_eggnog",
            species.getSpeciesName() + " Auto Functional Annotations");

        File goFile = new File(outputDir, parameters.getOutputPrefix() + ".emapper.annotations.GO.txt");
        File keggFile = new File(outputDir, parameters.getOutputPrefix() + ".emapper.annotations.KEGG_Knum.txt");
        File pfamFile = new File(outputDir, parameters.getOutputPrefix() + ".emapper.annotations.pfam.domain.txt");
        File descFile = new File(outputDir, parameters.getOutputPrefix() + ".emapper.annotations.description.txt");

        if (goFile.isFile()) {
            annotationData.loadGOAnnotations(goFile);
            annotationData.convertTranscriptToGeneAnnotations(annotationFile);
            annotationData.enhanceGOAnnotationsWithOBO(goOboFile);
        }
        if (keggFile.isFile()) {
            annotationData.loadKEGGAnnotations(keggFile);
            annotationData.convertTranscriptToGeneAnnotations(annotationFile);
            annotationData.enhanceKEGGAnnotationsWithBackground(keggBackendFile);
        }
        if (pfamFile.isFile()) {
            File normalizedPfamFile = normalizeTwoColumnFile(
                pfamFile, "Gene_ID", "Pfam_ID", "pfam_import_", ".tsv");
            annotationData.loadCustomAnnotations(normalizedPfamFile, GeneAnnotationData.AnnotationType.PFAM);
            annotationData.convertTranscriptToGeneAnnotations(annotationFile);
        }

        File existingCustomFile = new File(species.getFunctionalAnnotationDir(), "Custom/Custom.tsv");
        File mergedCustomFile = mergeCustomDescriptionFile(existingCustomFile, descFile);
        if (mergedCustomFile != null && mergedCustomFile.isFile()) {
            annotationData.loadCustomAnnotations(mergedCustomFile, GeneAnnotationData.AnnotationType.CUSTOM);
            annotationData.convertTranscriptToGeneAnnotations(annotationFile);
        }

        species.setFunctionalAnnotations(annotationData);
        if (!species.saveFunctionalAnnotations()) {
            throw new IOException("Failed to save functional annotations into species directory.");
        }

        GoOboManager.cacheOboForSpecies(species, goOboFile, true);
        if (parameters.getKeggBackendMode() == KeggBackendManager.BackendMode.CUSTOM) {
            KeggBackendManager.cacheCustomForSpecies(species, keggBackendFile);
        } else {
            KeggBackendManager.cachePresetForSpecies(species, parameters.getKeggBackendType(), keggBackendFile);
        }

        Map<GeneAnnotationData.AnnotationType, Integer> counts = annotationData.getAnnotationCounts();
        return new Result(outputDir, counts);
    }

    private static File normalizeTwoColumnFile(File inputFile, String firstHeader, String secondHeader,
                                               String prefix, String suffix) throws IOException {
        File tempFile = File.createTempFile(prefix, suffix);
        tempFile.deleteOnExit();
        try (BufferedReader reader = Files.newBufferedReader(inputFile.toPath(), StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(tempFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("#" + firstHeader + "\t" + secondHeader);
            writer.newLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                writer.write(line);
                writer.newLine();
            }
        }
        return tempFile;
    }

    private static File mergeCustomDescriptionFile(File existingCustomFile, File descriptionFile) throws IOException {
        boolean hasExisting = existingCustomFile != null && existingCustomFile.isFile();
        boolean hasDescription = descriptionFile != null && descriptionFile.isFile();
        if (!hasExisting && !hasDescription) {
            return null;
        }

        List<String[]> existingRows = new ArrayList<>();
        String[] headers = new String[]{"Gene_ID", "Description", "Source"};
        int sourceIndex = 2;
        int descriptionIndex = 1;

        if (hasExisting) {
            ParsedTable parsed = parseDelimitedTable(existingCustomFile);
            headers = parsed.headers;
            descriptionIndex = findDescriptionIndex(headers);
            sourceIndex = findHeaderIndex(headers, "Source");
            if (sourceIndex < 0) {
                headers = appendHeader(headers, "Source");
                sourceIndex = headers.length - 1;
            }
            for (String[] row : parsed.rows) {
                String[] normalized = normalizeRow(row, headers.length);
                if ("eggnog".equalsIgnoreCase(normalized[sourceIndex])) {
                    continue;
                }
                existingRows.add(normalized);
            }
        }

        if (descriptionIndex < 0) {
            headers = headers.length == 0 ? new String[]{"Gene_ID", "Description", "Source"} : headers;
            if (headers.length == 1) {
                headers = new String[]{headers[0], "Description", "Source"};
            }
            descriptionIndex = Math.min(1, headers.length - 1);
            sourceIndex = findHeaderIndex(headers, "Source");
            if (sourceIndex < 0) {
                headers = appendHeader(headers, "Source");
                sourceIndex = headers.length - 1;
            }
        }

        File mergedFile = File.createTempFile("custom_eggnog_merge_", ".tsv");
        mergedFile.deleteOnExit();
        try (BufferedWriter writer = Files.newBufferedWriter(mergedFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("#" + String.join("\t", headers));
            writer.newLine();

            for (String[] row : existingRows) {
                writer.write(String.join("\t", row));
                writer.newLine();
            }

            if (hasDescription) {
                try (BufferedReader reader = Files.newBufferedReader(descriptionFile.toPath(), StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        String[] parts = line.split("\t", -1);
                        if (parts.length < 2) {
                            continue;
                        }
                        String[] row = new String[headers.length];
                        row[0] = parts[0].trim();
                        row[descriptionIndex] = parts[1].trim();
                        row[sourceIndex] = "eggNOG";
                        for (int i = 0; i < row.length; i++) {
                            if (row[i] == null) {
                                row[i] = "";
                            }
                        }
                        writer.write(String.join("\t", row));
                        writer.newLine();
                    }
                }
            }
        }
        return mergedFile;
    }

    private static ParsedTable parseDelimitedTable(File file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        String[] headers = new String[]{"Gene_ID", "Description", "Source"};
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            boolean firstData = true;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (line.startsWith("#")) {
                    headers = trimArray(line.substring(1).split("\t", -1));
                    firstData = false;
                    continue;
                }
                if (firstData && looksLikeHeader(parts)) {
                    headers = trimArray(parts);
                    firstData = false;
                    continue;
                }
                rows.add(trimArray(parts));
                firstData = false;
            }
        }
        return new ParsedTable(headers, rows);
    }

    private static boolean looksLikeHeader(String[] parts) {
        return parts.length > 0 && parts[0].toLowerCase(Locale.ROOT).contains("gene");
    }

    private static String[] trimArray(String[] values) {
        String[] trimmed = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            trimmed[i] = values[i] == null ? "" : values[i].trim();
        }
        return trimmed;
    }

    private static int findDescriptionIndex(String[] headers) {
        int idx = findHeaderIndex(headers, "Description");
        return idx >= 0 ? idx : (headers.length > 1 ? 1 : -1);
    }

    private static int findHeaderIndex(String[] headers, String target) {
        for (int i = 0; i < headers.length; i++) {
            if (target.equalsIgnoreCase(headers[i])) {
                return i;
            }
        }
        return -1;
    }

    private static String[] appendHeader(String[] headers, String newHeader) {
        String[] updated = new String[headers.length + 1];
        System.arraycopy(headers, 0, updated, 0, headers.length);
        updated[headers.length] = newHeader;
        return updated;
    }

    private static String[] normalizeRow(String[] row, int length) {
        String[] normalized = new String[length];
        for (int i = 0; i < length; i++) {
            normalized[i] = i < row.length ? row[i] : "";
        }
        return normalized;
    }

    private static void writeRunMetadata(SpeciesInfo species, File proteinFile, File outputDir,
                                         Parameters parameters, File goOboFile, File keggBackendFile,
                                         Result result) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("species", species.getSpeciesDirectoryName());
            metadata.put("proteinFasta", proteinFile.getAbsolutePath());
            metadata.put("taxScope", parameters.getTaxScope());
            metadata.put("cpu", parameters.getCpu());
            metadata.put("evalue", parameters.getEvalue());
            metadata.put("outputPrefix", parameters.getOutputPrefix());
            metadata.put("keggBackendMode", parameters.getKeggBackendMode().name());
            metadata.put("keggBackendType", parameters.getKeggBackendType());
            metadata.put("goOboPath", goOboFile != null ? goOboFile.getAbsolutePath() : "");
            metadata.put("keggBackendPath", keggBackendFile != null ? keggBackendFile.getAbsolutePath() : "");
            metadata.put("runTime", LocalDateTime.now().toString());

            Map<String, Integer> imported = new LinkedHashMap<>();
            for (Map.Entry<GeneAnnotationData.AnnotationType, Integer> entry : result.getImportedCounts().entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0) {
                    imported.put(entry.getKey().getShortName(), entry.getValue());
                }
            }
            metadata.put("importedCounts", imported);

            List<String> rawFiles = new ArrayList<>();
            File[] outputFiles = outputDir.listFiles();
            if (outputFiles != null) {
                for (File file : outputFiles) {
                    if (file.isFile()) {
                        rawFiles.add(file.getName());
                    }
                }
            }
            metadata.put("rawOutputFiles", rawFiles);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            File metadataFile = new File(outputDir, "run-metadata.json");
            try (Writer writer = Files.newBufferedWriter(metadataFile.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(metadata, writer);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to write eggNOG run metadata", e);
        }
    }

    private static void report(ProgressListener progressListener, String message) {
        if (progressListener != null) {
            progressListener.onProgress(message);
        }
    }

    private static final class ParsedTable {
        private final String[] headers;
        private final List<String[]> rows;

        private ParsedTable(String[] headers, List<String[]> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }
}
