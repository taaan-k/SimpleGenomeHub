package simplegenomehub.gui;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GeneSetFileSupport {

    static final String REGION_SET_HEADER = "#####Region Set:";
    static final String GENE_SET_SUFFIX = ".gene.txt";
    static final String REGION_SET_SUFFIX = ".region.txt";

    enum SetKind {
        GENE,
        REGION
    }

    private GeneSetFileSupport() {
    }

    static List<String> parseGeneIds(String rawText) {
        String[] lines = normalizeLineEndings(rawText).split("\n");
        LinkedHashSet<String> uniqueGeneIds = new LinkedHashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                uniqueGeneIds.add(trimmed);
            }
        }
        return new ArrayList<>(uniqueGeneIds);
    }

    static String formatGeneIds(List<String> geneIds) {
        return String.join("\n", geneIds);
    }

    static boolean hasRegionEntries(String rawText) {
        String[] lines = normalizeLineEndings(rawText).split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !REGION_SET_HEADER.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    static List<RegionEntry> parseRegionEntries(String rawText) {
        String[] lines = normalizeLineEndings(rawText).split("\n");
        List<RegionEntry> entries = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty() || REGION_SET_HEADER.equals(trimmed)) {
                continue;
            }

            String[] columns = trimmed.split("\\s+");
            int lineNumber = i + 1;
            if (columns.length != 3) {
                errors.add("Line " + lineNumber + ": expected 'ChrName StartPos EndPos'");
                continue;
            }

            long startPos;
            long endPos;
            try {
                startPos = Long.parseLong(columns[1]);
                endPos = Long.parseLong(columns[2]);
            } catch (NumberFormatException e) {
                errors.add("Line " + lineNumber + ": StartPos and EndPos must be integers");
                continue;
            }

            if (startPos < 1 || endPos < 1) {
                errors.add("Line " + lineNumber + ": StartPos and EndPos must be greater than 0");
                continue;
            }

            if (startPos > endPos) {
                errors.add("Line " + lineNumber + ": StartPos cannot be greater than EndPos");
                continue;
            }

            entries.add(new RegionEntry(columns[0], startPos, endPos, lineNumber));
        }

        if (entries.isEmpty()) {
            errors.add("No valid region entries were provided.");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errors));
        }
        return entries;
    }

    static String formatRegionSetContent(List<RegionEntry> entries) {
        StringBuilder builder = new StringBuilder(REGION_SET_HEADER);
        for (RegionEntry entry : entries) {
            builder.append('\n')
                .append(entry.getChromosomeName()).append(' ')
                .append(entry.getStartPos()).append(' ')
                .append(entry.getEndPos());
        }
        return builder.toString();
    }

    static String readGeneSetContent(File geneSetFile) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(geneSetFile), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (!firstLine) {
                    builder.append('\n');
                }
                builder.append(line);
                firstLine = false;
            }
        }
        return builder.toString();
    }

    static SetKind detectSetKind(File geneSetFile) {
        if (geneSetFile == null) {
            return null;
        }
        return detectSetKind(geneSetFile.getName());
    }

    static SetKind detectSetKind(String fileName) {
        if (fileName == null) {
            return null;
        }

        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(REGION_SET_SUFFIX)) {
            return SetKind.REGION;
        }
        if (lowerName.endsWith(GENE_SET_SUFFIX)) {
            return SetKind.GENE;
        }
        return null;
    }

    static boolean looksLikeRegionSetFile(File geneSetFile) {
        return detectSetKind(geneSetFile) == SetKind.REGION;
    }

    static boolean isStandardSetFileName(String fileName) {
        return detectSetKind(fileName) != null;
    }

    static String buildStandardFileName(String baseName, SetKind setKind) {
        if (setKind == null) {
            throw new IllegalArgumentException("Set kind is required.");
        }
        return baseName + (setKind == SetKind.REGION ? REGION_SET_SUFFIX : GENE_SET_SUFFIX);
    }

    static String extractDisplayName(File geneSetFile) {
        if (geneSetFile == null) {
            return "";
        }
        return extractDisplayName(geneSetFile.getName());
    }

    static String extractDisplayName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "";
        }

        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(GENE_SET_SUFFIX)) {
            return fileName.substring(0, fileName.length() - GENE_SET_SUFFIX.length());
        }
        if (lowerName.endsWith(REGION_SET_SUFFIX)) {
            return fileName.substring(0, fileName.length() - REGION_SET_SUFFIX.length());
        }

        if (lowerName.endsWith(".txt")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }

    static boolean containsInvalidFileNameChars(String fileName) {
        return fileName.contains("\\")
            || fileName.contains("/")
            || fileName.contains(":")
            || fileName.contains("*")
            || fileName.contains("?")
            || fileName.contains("\"")
            || fileName.contains("<")
            || fileName.contains(">")
            || fileName.contains("|");
    }

    static Set<String> loadGeneIdsFromAnnotation(File annotationFile) throws IOException {
        Set<String> geneIds = new LinkedHashSet<>();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(annotationFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }

                String[] fields = line.split("\t");
                if (fields.length < 9) {
                    continue;
                }

                String featureType = fields[2].trim();
                if (!isGeneFeature(featureType)) {
                    continue;
                }

                String geneId = extractGeneId(fields[8]);
                if (geneId != null && !geneId.isEmpty()) {
                    geneIds.add(geneId);
                }
            }
        }

        return geneIds;
    }

    static Map<String, Long> loadChromosomeSizesFromStats(File statsFile) throws IOException {
        Map<String, String> chromosomeNamesByPrefix = new LinkedHashMap<>();
        Map<String, Long> chromosomeSizesByPrefix = new LinkedHashMap<>();
        Map<String, Long> chromosomeSizes = new LinkedHashMap<>();
        boolean inChromosomeBlock = false;

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(statsFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!inChromosomeBlock) {
                    if ("#####################chromosome stat".equals(trimmed)) {
                        inChromosomeBlock = true;
                    }
                    continue;
                }

                if (trimmed.startsWith("#####################")) {
                    break;
                }
                if (trimmed.isEmpty()) {
                    continue;
                }

                int separatorIndex = trimmed.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, separatorIndex).trim();
                String value = trimmed.substring(separatorIndex + 1).trim();
                if (key.endsWith(".name")) {
                    chromosomeNamesByPrefix.put(key.substring(0, key.length() - 5), value);
                } else if (key.endsWith(".size")) {
                    try {
                        chromosomeSizesByPrefix.put(
                            key.substring(0, key.length() - 5),
                            Long.parseLong(value.replace(",", ""))
                        );
                    } catch (NumberFormatException ignored) {
                        // Ignore malformed size lines and let validation fail later if needed.
                    }
                }
            }
        }

        for (Map.Entry<String, String> entry : chromosomeNamesByPrefix.entrySet()) {
            Long chromosomeSize = chromosomeSizesByPrefix.get(entry.getKey());
            if (chromosomeSize != null) {
                chromosomeSizes.put(entry.getValue(), chromosomeSize);
            }
        }
        return chromosomeSizes;
    }

    static List<String> validateRegionEntries(List<RegionEntry> entries, Map<String, Long> chromosomeSizes) {
        List<String> errors = new ArrayList<>();
        for (RegionEntry entry : entries) {
            Long chromosomeSize = chromosomeSizes.get(entry.getChromosomeName());
            if (chromosomeSize == null) {
                errors.add("Line " + entry.getLineNumber() + ": chromosome not found - " + entry.getChromosomeName());
                continue;
            }

            if (entry.getStartPos() > chromosomeSize || entry.getEndPos() > chromosomeSize) {
                errors.add("Line " + entry.getLineNumber() + ": region exceeds chromosome size for "
                    + entry.getChromosomeName() + " (size=" + chromosomeSize + ")");
            }
        }
        return errors;
    }

    static void writeTextFile(File outputFile, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            writer.write(normalizeLineEndings(content));
        }
    }

    static void writeGeneSetFile(File outputFile, List<String> geneIds) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            for (String geneId : geneIds) {
                writer.write(geneId);
                writer.newLine();
            }
        }
    }

    private static boolean isGeneFeature(String featureType) {
        String normalized = featureType.toLowerCase();
        return normalized.equals("gene") || normalized.equals("pseudogene");
    }

    private static String extractGeneId(String attributes) {
        String[] parts = attributes.split(";");
        String fallbackName = null;

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("ID=")) {
                return trimmed.substring(3).trim();
            }
            if (trimmed.startsWith("gene_id=")) {
                return trimmed.substring("gene_id=".length()).trim();
            }
            if (trimmed.startsWith("geneID=")) {
                return trimmed.substring("geneID=".length()).trim();
            }
            if (trimmed.startsWith("Name=")) {
                fallbackName = trimmed.substring(5).trim();
            }
        }

        return fallbackName;
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    static final class RegionEntry {
        private final String chromosomeName;
        private final long startPos;
        private final long endPos;
        private final int lineNumber;

        RegionEntry(String chromosomeName, long startPos, long endPos, int lineNumber) {
            this.chromosomeName = chromosomeName;
            this.startPos = startPos;
            this.endPos = endPos;
            this.lineNumber = lineNumber;
        }

        String getChromosomeName() {
            return chromosomeName;
        }

        long getStartPos() {
            return startPos;
        }

        long getEndPos() {
            return endPos;
        }

        int getLineNumber() {
            return lineNumber;
        }
    }
}
