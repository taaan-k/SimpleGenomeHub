package simplegenomehub.util.fileio;

import simplegenomehub.model.SpeciesInfo;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Creates the output directory structure for genome compare tasks.
 */
public final class GenomeCompareProjectGenerator {

    private static final DateTimeFormatter OUTPUT_DIR_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-M-d-H-m-s");

    private GenomeCompareProjectGenerator() {
    }

    public static File createProjectDirectory(SpeciesInfo primaryGenome) throws IOException {
        return createProjectDirectory(primaryGenome, "GenomeCompare", "");
    }

    public static File createProjectDirectory(SpeciesInfo primaryGenome, String namePrefix) throws IOException {
        return createProjectDirectory(primaryGenome, "GenomeCompare", namePrefix);
    }

    public static File createProjectDirectory(SpeciesInfo primaryGenome, String rootFolderName,
                                              String namePrefix) throws IOException {
        if (primaryGenome == null) {
            throw new IllegalArgumentException("Primary genome cannot be null.");
        }
        if (primaryGenome.getGenomeAnalysisDir() == null) {
            throw new IOException("GenomeAnalysis directory is not available.");
        }

        String normalizedRootFolderName = rootFolderName == null ? "" : rootFolderName.trim();
        if (normalizedRootFolderName.isEmpty()) {
            throw new IllegalArgumentException("Output root folder name cannot be empty.");
        }

        File outputRoot = new File(primaryGenome.getGenomeAnalysisDir(), normalizedRootFolderName);
        ensureDirectory(outputRoot);

        String normalizedPrefix = namePrefix == null ? "" : namePrefix.trim();
        String baseName = normalizedPrefix + OUTPUT_DIR_FORMAT.format(LocalDateTime.now());
        File outputDir = new File(outputRoot, baseName);
        int duplicateIndex = 2;
        while (outputDir.exists()) {
            outputDir = new File(outputRoot, baseName + "_" + duplicateIndex);
            duplicateIndex++;
        }
        ensureDirectory(outputDir);

        return outputDir;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IOException("Path exists but is not a directory: " + directory.getAbsolutePath());
            }
            return;
        }

        if (!directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }
}
