package simplegenomehub.util.fileio;

import simplegenomehub.config.SimpleGenomeHubConfig;
import simplegenomehub.model.SpeciesInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Finds finished Genome Compare result folders that can be reused by Multiple Synteny links.
 */
public final class GenomeCompareExistingResultScanner {

    private static final DateTimeFormatter DISPLAY_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int SEARCH_DEPTH = 8;

    private GenomeCompareExistingResultScanner() {
    }

    public static List<ReusableResult> findReusableResults(SpeciesInfo genome1Species,
                                                           List<String> genome1Chromosomes,
                                                           SpeciesInfo genome2Species,
                                                           List<String> genome2Chromosomes) {
        if (genome1Species == null || genome2Species == null) {
            return new ArrayList<>();
        }

        LinkedHashSet<String> expectedGenome1Chromosomes = normalizeValues(genome1Chromosomes);
        LinkedHashSet<String> expectedGenome2Chromosomes = normalizeValues(genome2Chromosomes);
        if (expectedGenome1Chromosomes.isEmpty() || expectedGenome2Chromosomes.isEmpty()) {
            return new ArrayList<>();
        }

        LinkedHashMap<String, ReusableResult> matchesByPath = new LinkedHashMap<>();
        for (File candidateDir : collectMetadataDirectories(resolveSearchRoot())) {
            ReusableResult result = readReusableResult(candidateDir);
            if (result == null || !result.isSuccessful()) {
                continue;
            }
            if (!matchesTarget(result, genome1Species, expectedGenome1Chromosomes,
                genome2Species, expectedGenome2Chromosomes)) {
                continue;
            }
            matchesByPath.putIfAbsent(result.getOutputDir().getAbsolutePath(), result);
        }

        List<ReusableResult> sortedMatches = new ArrayList<>(matchesByPath.values());
        sortedMatches.sort(Comparator
            .comparing(ReusableResult::getRunTimeOrMin)
            .reversed()
            .thenComparing(result -> result.getOutputDir().getAbsolutePath(), String.CASE_INSENSITIVE_ORDER));
        return sortedMatches;
    }

    private static File resolveSearchRoot() {
        File dataRootDir = SimpleGenomeHubConfig.getInstance().getDataRootDir();
        return dataRootDir != null && dataRootDir.isDirectory() ? dataRootDir : null;
    }

    private static List<File> collectMetadataDirectories(File searchRoot) {
        List<File> directories = new ArrayList<>();
        if (searchRoot == null || !searchRoot.isDirectory()) {
            return directories;
        }

        try (Stream<Path> pathStream = Files.walk(searchRoot.toPath(), SEARCH_DEPTH)) {
            pathStream
                .filter(Files::isDirectory)
                .map(Path::toFile)
                .filter(GenomeCompareExistingResultScanner::isReusableResultDirectory)
                .forEach(directories::add);
        } catch (IOException ignored) {
            return directories;
        }
        return directories;
    }

    private static boolean isReusableResultDirectory(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return false;
        }
        if (!new File(directory, "run-metadata.txt").isFile()) {
            return false;
        }
        return new File(directory, GenomeCompareLinkRegionExporter.OUTPUT_FILE_NAME).isFile();
    }

    private static ReusableResult readReusableResult(File outputDir) {
        if (outputDir == null || !outputDir.isDirectory()) {
            return null;
        }

        File metadataFile = new File(outputDir, "run-metadata.txt");
        if (!metadataFile.isFile()) {
            return null;
        }

        String status = null;
        LocalDateTime runTime = null;
        String species1 = null;
        String species2 = null;
        List<String> genome1Chromosomes = Collections.emptyList();
        List<String> genome2Chromosomes = Collections.emptyList();

        try (BufferedReader reader = Files.newBufferedReader(metadataFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                    continue;
                }
                if (trimmed.startsWith("status=")) {
                    status = trimmed.substring("status=".length()).trim();
                    continue;
                }
                if (trimmed.startsWith("runTime=")) {
                    runTime = parseRunTime(trimmed.substring("runTime=".length()).trim(), outputDir);
                    continue;
                }
                if (trimmed.startsWith("species1=")) {
                    species1 = emptyToNull(trimmed.substring("species1=".length()).trim());
                    continue;
                }
                if (trimmed.startsWith("species2=")) {
                    species2 = emptyToNull(trimmed.substring("species2=".length()).trim());
                    continue;
                }
                if (trimmed.startsWith("genome1Chromosomes=")) {
                    genome1Chromosomes = parseValueList(trimmed.substring("genome1Chromosomes=".length()));
                    continue;
                }
                if (trimmed.startsWith("genome2Chromosomes=")) {
                    genome2Chromosomes = parseValueList(trimmed.substring("genome2Chromosomes=".length()));
                }
            }
        } catch (IOException ignored) {
            return null;
        }

        if (species1 == null || species2 == null) {
            return null;
        }
        if (runTime == null) {
            runTime = fallbackRunTime(outputDir);
        }

        return new ReusableResult(
            outputDir,
            runTime,
            safe(status),
            species1,
            species2,
            genome1Chromosomes,
            genome2Chromosomes
        );
    }

    private static LocalDateTime parseRunTime(String rawValue, File outputDir) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            return fallbackRunTime(outputDir);
        }
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ex) {
            return fallbackRunTime(outputDir);
        }
    }

    private static LocalDateTime fallbackRunTime(File outputDir) {
        if (outputDir == null) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(
                Files.getLastModifiedTime(outputDir.toPath()).toInstant(),
                ZoneId.systemDefault()
            );
        } catch (IOException ex) {
            return null;
        }
    }

    private static boolean matchesTarget(ReusableResult result,
                                         SpeciesInfo genome1Species,
                                         LinkedHashSet<String> genome1Chromosomes,
                                         SpeciesInfo genome2Species,
                                         LinkedHashSet<String> genome2Chromosomes) {
        if (result == null || genome1Species == null || genome2Species == null) {
            return false;
        }

        String genome1Key = safe(genome1Species.getSpeciesDirectoryName());
        String genome2Key = safe(genome2Species.getSpeciesDirectoryName());
        LinkedHashSet<String> resultGenome1Chromosomes = normalizeValues(result.getGenome1Chromosomes());
        LinkedHashSet<String> resultGenome2Chromosomes = normalizeValues(result.getGenome2Chromosomes());

        boolean directMatch = genome1Key.equals(result.getSpecies1())
            && genome2Key.equals(result.getSpecies2())
            && genome1Chromosomes.equals(resultGenome1Chromosomes)
            && genome2Chromosomes.equals(resultGenome2Chromosomes);
        if (directMatch) {
            return true;
        }

        return genome1Key.equals(result.getSpecies2())
            && genome2Key.equals(result.getSpecies1())
            && genome1Chromosomes.equals(resultGenome2Chromosomes)
            && genome2Chromosomes.equals(resultGenome1Chromosomes);
    }

    private static List<String> parseValueList(String rawValue) {
        LinkedHashSet<String> values = normalizeValues(splitValues(rawValue));
        return new ArrayList<>(values);
    }

    private static List<String> splitValues(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = rawValue.split("[,;\\s]+");
        List<String> values = new ArrayList<>(parts.length);
        Collections.addAll(values, parts);
        return values;
    }

    private static LinkedHashSet<String> normalizeValues(List<String> rawValues) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (rawValues == null) {
            return normalized;
        }
        for (String rawValue : rawValues) {
            if (rawValue == null) {
                continue;
            }
            String trimmed = rawValue.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class ReusableResult {
        private final File outputDir;
        private final LocalDateTime runTime;
        private final String status;
        private final String species1;
        private final String species2;
        private final List<String> genome1Chromosomes;
        private final List<String> genome2Chromosomes;

        private ReusableResult(File outputDir, LocalDateTime runTime, String status,
                               String species1, String species2,
                               List<String> genome1Chromosomes, List<String> genome2Chromosomes) {
            this.outputDir = outputDir;
            this.runTime = runTime;
            this.status = status == null ? "" : status;
            this.species1 = safe(species1);
            this.species2 = safe(species2);
            this.genome1Chromosomes = genome1Chromosomes == null
                ? new ArrayList<>()
                : new ArrayList<>(genome1Chromosomes);
            this.genome2Chromosomes = genome2Chromosomes == null
                ? new ArrayList<>()
                : new ArrayList<>(genome2Chromosomes);
        }

        public File getOutputDir() {
            return outputDir;
        }

        public LocalDateTime getRunTime() {
            return runTime;
        }

        public LocalDateTime getRunTimeOrMin() {
            return runTime == null ? LocalDateTime.MIN : runTime;
        }

        public String getStatus() {
            return status;
        }

        public boolean isSuccessful() {
            return "SUCCESS".equalsIgnoreCase(status);
        }

        public String getSpecies1() {
            return species1;
        }

        public String getSpecies2() {
            return species2;
        }

        public List<String> getGenome1Chromosomes() {
            return new ArrayList<>(genome1Chromosomes);
        }

        public List<String> getGenome2Chromosomes() {
            return new ArrayList<>(genome2Chromosomes);
        }

        public String getSelectionLabel() {
            StringBuilder builder = new StringBuilder();
            if (runTime != null) {
                builder.append(runTime.format(DISPLAY_TIME_FORMAT)).append(" | ");
            }
            builder.append(outputDir != null ? outputDir.getName() : "<Missing>");
            return builder.toString();
        }

        @Override
        public String toString() {
            return getSelectionLabel();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ReusableResult)) {
                return false;
            }
            ReusableResult other = (ReusableResult) obj;
            return Objects.equals(
                outputDir != null ? outputDir.getAbsolutePath() : null,
                other.outputDir != null ? other.outputDir.getAbsolutePath() : null
            );
        }

        @Override
        public int hashCode() {
            return Objects.hash(outputDir != null ? outputDir.getAbsolutePath() : null);
        }
    }
}
