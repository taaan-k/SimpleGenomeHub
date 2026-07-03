package simplegenomehub.util.fileio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exports a standardized LinkRegion.tab file for Genome Compare results by
 * mapping syntenic gene pairs in the primary .collinearity file to coordinates
 * from the simplified output .gff file.
 */
final class GenomeCompareLinkRegionExporter {

    static final String OUTPUT_FILE_NAME = "LinkRegion.tab";
    private static final String DEFAULT_COLOR = "200,200,200";

    private GenomeCompareLinkRegionExporter() {
    }

    static File generate(File outputDir, File collinearityFile) throws IOException {
        if (outputDir == null || !outputDir.isDirectory()) {
            throw new IOException("Genome Compare output directory is missing.");
        }
        if (collinearityFile == null || !collinearityFile.isFile()) {
            throw new IOException("Primary .collinearity file is missing.");
        }

        File simplifiedGffFile = findSimplifiedGff(outputDir);
        if (simplifiedGffFile == null || !simplifiedGffFile.isFile()) {
            throw new IOException("Simplified .gff file is missing.");
        }

        Map<String, GeneRegion> geneRegions = readGeneRegions(simplifiedGffFile);
        if (geneRegions.isEmpty()) {
            throw new IOException("No gene coordinates were found in the simplified .gff file.");
        }

        File outputFile = new File(outputDir, OUTPUT_FILE_NAME);
        int exportedCount = 0;

        try (BufferedReader reader = Files.newBufferedReader(collinearityFile.toPath(), StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {

            String line;
            boolean inAlignmentBlock = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                if (trimmed.startsWith("## Alignment ")) {
                    inAlignmentBlock = true;
                    continue;
                }

                if (!inAlignmentBlock || !isCollinearPairLine(line)) {
                    continue;
                }

                String[] parts = line.split("\t");
                if (parts.length < 3) {
                    continue;
                }

                GeneRegion left = geneRegions.get(parts[1].trim());
                GeneRegion right = geneRegions.get(parts[2].trim());
                if (left == null || right == null) {
                    continue;
                }

                writer.write(left.chromosome);
                writer.write('\t');
                writer.write(String.valueOf(left.start));
                writer.write('\t');
                writer.write(String.valueOf(left.end));
                writer.write('\t');
                writer.write(right.chromosome);
                writer.write('\t');
                writer.write(String.valueOf(right.start));
                writer.write('\t');
                writer.write(String.valueOf(right.end));
                writer.write('\t');
                writer.write(DEFAULT_COLOR);
                writer.newLine();
                exportedCount++;
            }
        }

        if (exportedCount == 0) {
            throw new IOException("No syntenic links could be exported from the .collinearity file.");
        }

        return outputFile;
    }

    static boolean isCollinearPairLine(String line) {
        return line.matches("^\\s*\\d+-\\s*\\d+:\\t.*");
    }

    static File findSimplifiedGff(File outputDir) {
        File[] gffFiles = outputDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(".gff"));
        if (gffFiles == null || gffFiles.length == 0) {
            return null;
        }

        List<File> orderedFiles = new ArrayList<>(Arrays.asList(gffFiles));
        orderedFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : orderedFiles) {
            if (file != null && !"dual_synteny.visual.gff".equalsIgnoreCase(file.getName())) {
                return file;
            }
        }
        return orderedFiles.get(0);
    }

    static Map<String, GeneRegion> readGeneRegions(File simplifiedGffFile) throws IOException {
        Map<String, GeneRegion> geneRegions = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(simplifiedGffFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                String[] parts = trimmed.split("\t");
                if (parts.length < 4) {
                    continue;
                }

                String chromosome = parts[0].trim();
                String geneId = parts[1].trim();
                long start = parseCoordinate(parts[2]);
                long end = parseCoordinate(parts[3]);
                if (chromosome.isEmpty() || geneId.isEmpty() || start < 0 || end < 0) {
                    continue;
                }

                geneRegions.put(geneId, new GeneRegion(chromosome, Math.min(start, end), Math.max(start, end)));
            }
        }
        return geneRegions;
    }

    private static long parseCoordinate(String rawValue) {
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    static final class GeneRegion {
        final String chromosome;
        final long start;
        final long end;

        private GeneRegion(String chromosome, long start, long end) {
            this.chromosome = chromosome;
            this.start = start;
            this.end = end;
        }
    }
}
