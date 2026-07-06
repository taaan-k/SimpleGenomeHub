package simplegenomehub.util.fileio;

import simplegenomehub.config.SimpleGenomeHubVersion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles startup data-version checks and automatic migration of
 * legacy genome analysis result folders.
 */
public final class SpeciesDataVersionManager {

    private static final Logger logger = Logger.getLogger(SpeciesDataVersionManager.class.getName());

    private static final String STATS_FILE_NAME = "stat.txt";
    private static final String FUNCTIONAL_ANNOTATION_DIR = "FunctionalAnnotation";
    private static final String GENOME_ANALYSIS_DIR = "GenomeAnalysis";
    private static final String[] LEGACY_ANALYSIS_DIRS = {
        "AdvanceCircos",
        "GenomeCompare",
        "MultipleCompare"
    };

    private SpeciesDataVersionManager() {
    }

    public static void migrateLegacyDataIfNeeded(File dataRootDir) {
        if (dataRootDir == null || !dataRootDir.isDirectory()) {
            return;
        }

        File[] speciesDirs = dataRootDir.listFiles(File::isDirectory);
        if (speciesDirs == null || speciesDirs.length == 0) {
            return;
        }

        int migratedSpeciesCount = 0;
        int stampedStatsCount = 0;

        for (File speciesDir : speciesDirs) {
            try {
                boolean needsMigration = isLegacySpeciesDirectory(speciesDir);
                if (needsMigration) {
                    migrateLegacyGenomeAnalysisFolders(speciesDir);
                    migratedSpeciesCount++;
                }

                if (stampStatsFileVersion(speciesDir)) {
                    stampedStatsCount++;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING,
                    "Failed to process species data version for: " + speciesDir.getAbsolutePath(), e);
            }
        }

        if (migratedSpeciesCount > 0 || stampedStatsCount > 0) {
            logger.info(String.format(Locale.ROOT,
                "Species data version check finished. Migrated %d species folder(s), updated %d stat.txt file(s).",
                migratedSpeciesCount, stampedStatsCount));
        }
    }

    private static boolean isLegacySpeciesDirectory(File speciesDir) throws IOException {
        File statsFile = new File(speciesDir, STATS_FILE_NAME);
        String firstLine = readFirstLine(statsFile);
        return firstLine == null || !firstLine.contains("v." + SimpleGenomeHubVersion.APPLICATION_VERSION);
    }

    private static String readFirstLine(File file) throws IOException {
        if (file == null || !file.isFile()) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return reader.readLine();
        }
    }

    private static void migrateLegacyGenomeAnalysisFolders(File speciesDir) throws IOException {
        File functionalDir = new File(speciesDir, FUNCTIONAL_ANNOTATION_DIR);
        if (!functionalDir.isDirectory()) {
            return;
        }

        File genomeAnalysisDir = new File(speciesDir, GENOME_ANALYSIS_DIR);
        if (!genomeAnalysisDir.exists() && !genomeAnalysisDir.mkdirs()) {
            throw new IOException("Failed to create GenomeAnalysis directory: " + genomeAnalysisDir.getAbsolutePath());
        }

        for (String dirName : LEGACY_ANALYSIS_DIRS) {
            File sourceDir = new File(functionalDir, dirName);
            if (!sourceDir.isDirectory()) {
                continue;
            }

            File targetDir = new File(genomeAnalysisDir, dirName);
            if (targetDir.isDirectory()) {
                moveDirectoryContents(sourceDir.toPath(), targetDir.toPath());
                deleteDirectoryIfEmpty(sourceDir.toPath());
            } else {
                Files.move(sourceDir.toPath(), targetDir.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            logger.info("Migrated legacy analysis folder: "
                + sourceDir.getAbsolutePath() + " -> " + targetDir.getAbsolutePath());
        }
    }

    private static void moveDirectoryContents(Path sourceDir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        List<Path> children = new ArrayList<>();
        try (java.util.stream.Stream<Path> stream = Files.list(sourceDir)) {
            stream.sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                .forEach(children::add);
        }

        for (Path child : children) {
            Path targetPath = resolveAvailableTarget(targetDir.resolve(child.getFileName()));
            Files.move(child, targetPath);
        }
    }

    private static Path resolveAvailableTarget(Path targetPath) {
        if (!Files.exists(targetPath)) {
            return targetPath;
        }

        String fileName = targetPath.getFileName().toString();
        int index = 2;
        while (true) {
            Path candidate = targetPath.resolveSibling(fileName + "_migrated_" + index);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private static void deleteDirectoryIfEmpty(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }

        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            if (stream.findAny().isPresent()) {
                return;
            }
        }
        Files.deleteIfExists(directory);
    }

    private static boolean stampStatsFileVersion(File speciesDir) throws IOException {
        File statsFile = new File(speciesDir, STATS_FILE_NAME);
        if (!statsFile.isFile()) {
            return false;
        }

        List<String> lines = Files.readAllLines(statsFile.toPath(), StandardCharsets.UTF_8);
        if (!lines.isEmpty() && SimpleGenomeHubVersion.STATS_FILE_HEADER.equals(lines.get(0))) {
            return false;
        }

        List<String> updatedLines = new ArrayList<>();
        updatedLines.add(SimpleGenomeHubVersion.STATS_FILE_HEADER);
        if (lines.isEmpty()) {
            // keep only the version header for an empty stat file
        } else if (!lines.get(0).startsWith("#")) {
            updatedLines.addAll(lines);
        } else {
            updatedLines.addAll(lines.subList(1, lines.size()));
        }

        try (BufferedWriter writer = Files.newBufferedWriter(statsFile.toPath(), StandardCharsets.UTF_8)) {
            for (int i = 0; i < updatedLines.size(); i++) {
                writer.write(updatedLines.get(i));
                if (i < updatedLines.size() - 1) {
                    writer.newLine();
                }
            }
        }
        return true;
    }
}
