package simplegenomehub.util.fileio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prepends highlighted links to LinkRegion.tab for selected gene IDs after
 * Genome Compare completes so they render on top after TBtools reverses
 * the link order internally.
 */
public final class GenomeCompareHighlightedLinkAppender {

    public static final String HIGHLIGHT_COLOR = "252,141,98";

    private GenomeCompareHighlightedLinkAppender() {
    }

    public static HighlightAppendResult prependHighlightedLinks(File outputDir, File collinearityFile,
                                                                File linkRegionFile, Set<String> highlightedGeneIds)
        throws IOException {
        if (outputDir == null || !outputDir.isDirectory()) {
            throw new IOException("Genome Compare output directory is missing.");
        }
        if (collinearityFile == null || !collinearityFile.isFile()) {
            throw new IOException("Primary .collinearity file is missing.");
        }
        if (linkRegionFile == null || !linkRegionFile.isFile()) {
            throw new IOException("LinkRegion.tab is missing.");
        }
        if (highlightedGeneIds == null || highlightedGeneIds.isEmpty()) {
            return HighlightAppendResult.empty();
        }

        File simplifiedGffFile = GenomeCompareLinkRegionExporter.findSimplifiedGff(outputDir);
        if (simplifiedGffFile == null || !simplifiedGffFile.isFile()) {
            throw new IOException("Simplified .gff file is missing.");
        }

        Map<String, GenomeCompareLinkRegionExporter.GeneRegion> geneRegions =
            GenomeCompareLinkRegionExporter.readGeneRegions(simplifiedGffFile);
        if (geneRegions.isEmpty()) {
            throw new IOException("No gene coordinates were found in the simplified .gff file.");
        }

        TranscriptResolution transcriptResolution =
            resolveHighlightedTranscriptIds(simplifiedGffFile, highlightedGeneIds);
        if (transcriptResolution.isEmpty()) {
            return HighlightAppendResult.empty();
        }

        HighlightCollection highlightCollection =
            collectHighlightedLines(collinearityFile, geneRegions, transcriptResolution);
        if (highlightCollection.lines.isEmpty()) {
            return HighlightAppendResult.empty();
        }

        List<String> existingLines = Files.readAllLines(linkRegionFile.toPath(), StandardCharsets.UTF_8);
        try (BufferedWriter writer = Files.newBufferedWriter(linkRegionFile.toPath(), StandardCharsets.UTF_8)) {
            for (String line : highlightCollection.lines) {
                writer.write(line);
                writer.newLine();
            }
            for (String line : existingLines) {
                writer.write(line);
                writer.newLine();
            }
        }

        return new HighlightAppendResult(
            highlightCollection.matchedInputGeneIds,
            highlightCollection.lines.size()
        );
    }

    private static HighlightCollection collectHighlightedLines(
        File collinearityFile,
        Map<String, GenomeCompareLinkRegionExporter.GeneRegion> geneRegions,
        TranscriptResolution transcriptResolution
    ) throws IOException {
        List<String> lines = new ArrayList<>();
        Set<String> deduplicatedLines = new LinkedHashSet<>();
        LinkedHashSet<String> matchedInputGeneIds = new LinkedHashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(collinearityFile.toPath(), StandardCharsets.UTF_8)) {
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

                if (!inAlignmentBlock || !GenomeCompareLinkRegionExporter.isCollinearPairLine(line)) {
                    continue;
                }

                String[] parts = line.split("\t");
                if (parts.length < 3) {
                    continue;
                }

                String leftGeneId = parts[1].trim();
                String rightGeneId = parts[2].trim();
                boolean matchesLeft = transcriptResolution.isHighlightedTranscript(leftGeneId);
                boolean matchesRight = transcriptResolution.isHighlightedTranscript(rightGeneId);
                if (!matchesLeft && !matchesRight) {
                    continue;
                }

                GenomeCompareLinkRegionExporter.GeneRegion left = geneRegions.get(leftGeneId);
                GenomeCompareLinkRegionExporter.GeneRegion right = geneRegions.get(rightGeneId);
                if (left == null || right == null) {
                    continue;
                }

                if (matchesLeft) {
                    matchedInputGeneIds.addAll(transcriptResolution.getInputGeneIds(leftGeneId));
                }
                if (matchesRight) {
                    matchedInputGeneIds.addAll(transcriptResolution.getInputGeneIds(rightGeneId));
                }

                String renderedLine = renderLine(left, right);
                if (deduplicatedLines.add(renderedLine)) {
                    lines.add(renderedLine);
                }
            }
        }

        return new HighlightCollection(lines, matchedInputGeneIds);
    }

    private static TranscriptResolution resolveHighlightedTranscriptIds(File simplifiedGffFile,
                                                                        Set<String> highlightedGeneIds)
        throws IOException {
        LinkedHashSet<String> normalizedInputIds = new LinkedHashSet<>();
        for (String geneId : highlightedGeneIds) {
            if (geneId == null) {
                continue;
            }
            String trimmed = geneId.trim();
            if (!trimmed.isEmpty()) {
                normalizedInputIds.add(trimmed);
            }
        }
        if (normalizedInputIds.isEmpty()) {
            return TranscriptResolution.empty();
        }

        TranscriptResolution resolution = new TranscriptResolution();
        TranscriptToGeneMapper patternMapper = new TranscriptToGeneMapper();

        try (BufferedReader reader = Files.newBufferedReader(simplifiedGffFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                String[] parts = trimmed.split("\t");
                if (parts.length < 2) {
                    continue;
                }

                String transcriptId = parts[1].trim();
                if (transcriptId.isEmpty()) {
                    continue;
                }

                LinkedHashSet<String> aliasIds = new LinkedHashSet<>();
                aliasIds.add(transcriptId);

                String derivedGeneId = patternMapper.mapTranscriptToGene(transcriptId);
                if (derivedGeneId != null && !derivedGeneId.trim().isEmpty()) {
                    aliasIds.add(derivedGeneId.trim());
                }

                aliasIds.addAll(extractAliasIds(parts));
                LinkedHashSet<String> matchedInputIds = collectMatchedInputIds(normalizedInputIds, aliasIds);
                if (!matchedInputIds.isEmpty()) {
                    resolution.addTranscriptMapping(transcriptId, matchedInputIds);
                }
            }
        }

        return resolution;
    }

    private static LinkedHashSet<String> collectMatchedInputIds(Set<String> expectedIds, Set<String> candidateIds) {
        LinkedHashSet<String> matchedInputIds = new LinkedHashSet<>();
        for (String candidateId : candidateIds) {
            if (expectedIds.contains(candidateId)) {
                matchedInputIds.add(candidateId);
            }
        }
        return matchedInputIds;
    }

    private static Set<String> extractAliasIds(String[] parts) {
        LinkedHashSet<String> aliasIds = new LinkedHashSet<>();
        for (int i = 2; i < parts.length; i++) {
            String token = parts[i] == null ? "" : parts[i].trim();
            if (token.isEmpty()) {
                continue;
            }

            int equalsIndex = token.indexOf('=');
            if (equalsIndex > 0 && equalsIndex < token.length() - 1) {
                String key = token.substring(0, equalsIndex).trim();
                String value = token.substring(equalsIndex + 1).trim();
                addAliasValue(aliasIds, key, value);
                continue;
            }

            if (i + 1 < parts.length) {
                String nextToken = parts[i + 1] == null ? "" : parts[i + 1].trim();
                if (!nextToken.isEmpty() && isAliasKey(token)) {
                    addAliasValue(aliasIds, token, nextToken);
                    i++;
                }
            }
        }
        return aliasIds;
    }

    private static boolean isAliasKey(String key) {
        String normalizedKey = key.toLowerCase();
        return "parent".equals(normalizedKey)
            || "gene".equals(normalizedKey)
            || "geneid".equals(normalizedKey)
            || "gene_id".equals(normalizedKey)
            || "id".equals(normalizedKey)
            || "name".equals(normalizedKey);
    }

    private static void addAliasValue(Set<String> aliasIds, String key, String rawValue) {
        if (!isAliasKey(key) || rawValue == null) {
            return;
        }

        String[] values = rawValue.split(",");
        for (String value : values) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                aliasIds.add(trimmed);
            }
        }
    }

    private static String renderLine(GenomeCompareLinkRegionExporter.GeneRegion left,
                                     GenomeCompareLinkRegionExporter.GeneRegion right) {
        StringBuilder builder = new StringBuilder(96);
        builder.append(left.chromosome).append('\t')
            .append(left.start).append('\t')
            .append(left.end).append('\t')
            .append(right.chromosome).append('\t')
            .append(right.start).append('\t')
            .append(right.end).append('\t')
            .append(HIGHLIGHT_COLOR);
        return builder.toString();
    }

    public static final class HighlightAppendResult {
        private final LinkedHashSet<String> matchedInputGeneIds;
        private final int prependedLinkCount;

        private HighlightAppendResult(Set<String> matchedInputGeneIds, int prependedLinkCount) {
            this.matchedInputGeneIds = new LinkedHashSet<>(matchedInputGeneIds);
            this.prependedLinkCount = prependedLinkCount;
        }

        public static HighlightAppendResult empty() {
            return new HighlightAppendResult(new LinkedHashSet<>(), 0);
        }

        public Set<String> getMatchedInputGeneIds() {
            return new LinkedHashSet<>(matchedInputGeneIds);
        }

        public int getPrependedLinkCount() {
            return prependedLinkCount;
        }
    }

    private static final class TranscriptResolution {
        private final LinkedHashSet<String> transcriptIds = new LinkedHashSet<>();
        private final Map<String, LinkedHashSet<String>> inputGeneIdsByTranscriptId = new LinkedHashMap<>();

        private static TranscriptResolution empty() {
            return new TranscriptResolution();
        }

        private void addTranscriptMapping(String transcriptId, Set<String> inputGeneIds) {
            if (transcriptId == null || transcriptId.trim().isEmpty() || inputGeneIds == null || inputGeneIds.isEmpty()) {
                return;
            }
            transcriptIds.add(transcriptId);
            inputGeneIdsByTranscriptId
                .computeIfAbsent(transcriptId, ignored -> new LinkedHashSet<>())
                .addAll(inputGeneIds);
        }

        private boolean isEmpty() {
            return transcriptIds.isEmpty();
        }

        private boolean isHighlightedTranscript(String transcriptId) {
            return transcriptIds.contains(transcriptId);
        }

        private Set<String> getInputGeneIds(String transcriptId) {
            LinkedHashSet<String> inputGeneIds = inputGeneIdsByTranscriptId.get(transcriptId);
            if (inputGeneIds == null) {
                return new LinkedHashSet<>();
            }
            return new LinkedHashSet<>(inputGeneIds);
        }
    }

    private static final class HighlightCollection {
        private final List<String> lines;
        private final LinkedHashSet<String> matchedInputGeneIds;

        private HighlightCollection(List<String> lines, Set<String> matchedInputGeneIds) {
            this.lines = new ArrayList<>(lines);
            this.matchedInputGeneIds = new LinkedHashSet<>(matchedInputGeneIds);
        }
    }
}
