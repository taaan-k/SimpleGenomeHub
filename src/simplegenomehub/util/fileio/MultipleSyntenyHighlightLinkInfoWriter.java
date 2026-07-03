package simplegenomehub.util.fileio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds Highlight.Link.info.txt from Link.info.txt and Highlight.info.txt.
 */
final class MultipleSyntenyHighlightLinkInfoWriter {

    private MultipleSyntenyHighlightLinkInfoWriter() {
    }

    static void write(File outputFile, File linkInfoFile, File highlightInfoFile) throws IOException {
        List<HighlightRegion> highlightRegions = readHighlightRegions(highlightInfoFile);
        List<HighlightedLink> highlightedLinks = readHighlightedLinks(linkInfoFile, highlightRegions);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("#format=SGH.MultipleSynteny.HighlightLinkInfo.v1");
            writer.newLine();
            writer.write("genome1_id\tchr1\tstart1\tend1\tgenome2_id\tchr2\tstart2\tend2\tcolor\talpha\tz_order");
            writer.newLine();

            for (HighlightedLink highlightedLink : highlightedLinks) {
                writer.write(highlightedLink.genome1Id);
                writer.write('\t');
                writer.write(highlightedLink.chr1);
                writer.write('\t');
                writer.write(String.valueOf(highlightedLink.start1));
                writer.write('\t');
                writer.write(String.valueOf(highlightedLink.end1));
                writer.write('\t');
                writer.write(highlightedLink.genome2Id);
                writer.write('\t');
                writer.write(highlightedLink.chr2);
                writer.write('\t');
                writer.write(String.valueOf(highlightedLink.start2));
                writer.write('\t');
                writer.write(String.valueOf(highlightedLink.end2));
                writer.write('\t');
                writer.write(MultipleSyntenyService.DEFAULT_HIGHLIGHT_COLOR);
                writer.write('\t');
                writer.write(String.format(Locale.US, "%.2f", MultipleSyntenyService.DEFAULT_HIGHLIGHT_ALPHA));
                writer.write('\t');
                writer.write(String.valueOf(MultipleSyntenyService.DEFAULT_HIGHLIGHT_Z_ORDER));
                writer.newLine();
            }
        }
    }

    private static List<HighlightRegion> readHighlightRegions(File highlightInfoFile) throws IOException {
        List<HighlightRegion> regions = new ArrayList<>();
        if (highlightInfoFile == null || !highlightInfoFile.isFile()) {
            return regions;
        }

        try (BufferedReader reader = Files.newBufferedReader(highlightInfoFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("gene_id\t")) {
                    continue;
                }

                String[] columns = line.split("\t");
                if (columns.length < 5) {
                    continue;
                }

                long start = parseCoordinate(columns[3]);
                long end = parseCoordinate(columns[4]);
                if (start < 1 || end < 1) {
                    continue;
                }

                regions.add(new HighlightRegion(
                    columns[1].trim(),
                    columns[2].trim(),
                    Math.min(start, end),
                    Math.max(start, end)
                ));
            }
        }
        return regions;
    }

    private static List<HighlightedLink> readHighlightedLinks(File linkInfoFile,
                                                              List<HighlightRegion> highlightRegions)
        throws IOException {
        List<HighlightedLink> highlightedLinks = new ArrayList<>();
        if (linkInfoFile == null || !linkInfoFile.isFile()) {
            return highlightedLinks;
        }

        LinkedHashSet<String> seenKeys = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(linkInfoFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("genome1_id\t")) {
                    continue;
                }

                String[] columns = line.split("\t");
                if (columns.length < 8) {
                    continue;
                }

                HighlightedLink link = new HighlightedLink(
                    columns[0].trim(),
                    columns[1].trim(),
                    parseCoordinate(columns[2]),
                    parseCoordinate(columns[3]),
                    columns[4].trim(),
                    columns[5].trim(),
                    parseCoordinate(columns[6]),
                    parseCoordinate(columns[7])
                );
                if (!link.isValid()) {
                    continue;
                }

                if (!matchesAnyHighlight(link, highlightRegions)) {
                    continue;
                }

                String key = link.primaryKey();
                if (seenKeys.add(key)) {
                    highlightedLinks.add(link);
                }
            }
        }
        return highlightedLinks;
    }

    private static boolean matchesAnyHighlight(HighlightedLink link, List<HighlightRegion> highlightRegions) {
        for (HighlightRegion highlightRegion : highlightRegions) {
            if (highlightRegion.matches(link.genome1Id, link.chr1, link.start1, link.end1)) {
                return true;
            }
            if (highlightRegion.matches(link.genome2Id, link.chr2, link.start2, link.end2)) {
                return true;
            }
        }
        return false;
    }

    private static long parseCoordinate(String rawValue) {
        try {
            return Long.parseLong(rawValue.trim());
        } catch (Exception ex) {
            return -1L;
        }
    }

    private static final class HighlightRegion {
        private final String genomeId;
        private final String chromosomeName;
        private final long start;
        private final long end;

        private HighlightRegion(String genomeId, String chromosomeName, long start, long end) {
            this.genomeId = genomeId;
            this.chromosomeName = chromosomeName;
            this.start = start;
            this.end = end;
        }

        private boolean matches(String linkGenomeId, String linkChromosomeName, long linkStart, long linkEnd) {
            return genomeId.equals(linkGenomeId)
                && chromosomeName.equals(linkChromosomeName)
                && linkStart <= end
                && start <= linkEnd;
        }
    }

    private static final class HighlightedLink {
        private final String genome1Id;
        private final String chr1;
        private final long start1;
        private final long end1;
        private final String genome2Id;
        private final String chr2;
        private final long start2;
        private final long end2;

        private HighlightedLink(String genome1Id, String chr1, long start1, long end1,
                                String genome2Id, String chr2, long start2, long end2) {
            this.genome1Id = genome1Id;
            this.chr1 = chr1;
            this.start1 = Math.min(start1, end1);
            this.end1 = Math.max(start1, end1);
            this.genome2Id = genome2Id;
            this.chr2 = chr2;
            this.start2 = Math.min(start2, end2);
            this.end2 = Math.max(start2, end2);
        }

        private boolean isValid() {
            return start1 >= 1 && end1 >= 1 && start2 >= 1 && end2 >= 1;
        }

        private String primaryKey() {
            return genome1Id + '\t' + chr1 + '\t' + start1 + '\t' + end1 + '\t'
                + genome2Id + '\t' + chr2 + '\t' + start2 + '\t' + end2;
        }
    }
}
