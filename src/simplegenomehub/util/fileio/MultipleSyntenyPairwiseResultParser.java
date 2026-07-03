package simplegenomehub.util.fileio;

import simplegenomehub.model.SpeciesInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parses one pairwise Genome Compare result into Link.info rows.
 */
final class MultipleSyntenyPairwiseResultParser {

    private MultipleSyntenyPairwiseResultParser() {
    }

    static List<MultipleSyntenyBatchContext.LinkInfoEntry> parse(
        MultipleSyntenyService.PairResult pairResult,
        MultipleSyntenyBatchContext context
    ) throws IOException {
        if (pairResult == null || pairResult.getLinkRun() == null) {
            throw new IOException("Pairwise result is missing its link definition.");
        }

        MultipleSyntenyService.LinkRun linkRun = pairResult.getLinkRun();
        MultipleSyntenyService.LinkLayout linkLayout = context.getLinkLayout(linkRun.getLinkNumber());
        if (linkLayout == null) {
            throw new IOException("Missing link layout for link " + linkRun.getLinkNumber() + ".");
        }

        File outputDir = pairResult.getOutputDir();
        if (outputDir == null || !outputDir.isDirectory()) {
            throw new IOException("Genome Compare result folder is missing for " + linkRun.getDisplayLabel() + ".");
        }

        PairResultMetadata metadata = readMetadata(outputDir);
        boolean reverseDirection = shouldReverseToCurrentLink(linkRun, metadata);
        Map<String, String> renamedToOriginalChromosomes = readChromosomeRenameMap(outputDir);

        File linkRegionFile = new File(outputDir, GenomeCompareLinkRegionExporter.OUTPUT_FILE_NAME);
        if (!linkRegionFile.isFile()) {
            throw new IOException("Missing LinkRegion.tab in " + outputDir.getAbsolutePath());
        }

        List<MultipleSyntenyBatchContext.LinkInfoEntry> entries = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(linkRegionFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] columns = line.split("\t");
                if (columns.length < 6) {
                    continue;
                }

                Endpoint leftEndpoint = new Endpoint(
                    restoreOriginalChromosome(columns[0], renamedToOriginalChromosomes),
                    parseCoordinate(columns[1]),
                    parseCoordinate(columns[2])
                );
                Endpoint rightEndpoint = new Endpoint(
                    restoreOriginalChromosome(columns[3], renamedToOriginalChromosomes),
                    parseCoordinate(columns[4]),
                    parseCoordinate(columns[5])
                );
                if (!leftEndpoint.isValid() || !rightEndpoint.isValid()) {
                    continue;
                }

                if (reverseDirection) {
                    Endpoint swapped = leftEndpoint;
                    leftEndpoint = rightEndpoint;
                    rightEndpoint = swapped;
                }

                entries.add(new MultipleSyntenyBatchContext.LinkInfoEntry(
                    context.getGenomeId(linkRun.getLeftSelection().getSlotNumber()),
                    leftEndpoint.chromosome,
                    leftEndpoint.start,
                    leftEndpoint.end,
                    context.getGenomeId(linkRun.getRightSelection().getSlotNumber()),
                    rightEndpoint.chromosome,
                    rightEndpoint.start,
                    rightEndpoint.end,
                    hasText(linkLayout.getLinkType()) ? linkLayout.getLinkType() : "double_arc",
                    hasText(linkLayout.getEdge1()) ? linkLayout.getEdge1() : "top",
                    hasText(linkLayout.getEdge2()) ? linkLayout.getEdge2() : "top",
                    Math.max(0, linkLayout.getBendValue()),
                    hasText(linkLayout.getBulgeDir()) ? linkLayout.getBulgeDir() : "auto",
                    MultipleSyntenyService.DEFAULT_LINK_COLOR,
                    MultipleSyntenyService.DEFAULT_LINK_ALPHA,
                    1
                ));
            }
        }

        if (entries.isEmpty()) {
            throw new IOException("No link coordinates could be parsed from " + linkRegionFile.getAbsolutePath());
        }
        return entries;
    }

    private static PairResultMetadata readMetadata(File outputDir) throws IOException {
        File metadataFile = new File(outputDir, "run-metadata.txt");
        if (!metadataFile.isFile()) {
            throw new IOException("Missing run-metadata.txt in " + outputDir.getAbsolutePath());
        }

        String species1 = "";
        String species2 = "";
        List<String> genome1Chromosomes = new ArrayList<>();
        List<String> genome2Chromosomes = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(metadataFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("[") || !trimmed.contains("=")) {
                    continue;
                }

                if (trimmed.startsWith("species1=")) {
                    species1 = trimmed.substring("species1=".length()).trim();
                } else if (trimmed.startsWith("species2=")) {
                    species2 = trimmed.substring("species2=".length()).trim();
                } else if (trimmed.startsWith("genome1Chromosomes=")) {
                    genome1Chromosomes = splitValues(trimmed.substring("genome1Chromosomes=".length()));
                } else if (trimmed.startsWith("genome2Chromosomes=")) {
                    genome2Chromosomes = splitValues(trimmed.substring("genome2Chromosomes=".length()));
                }
            }
        }

        return new PairResultMetadata(species1, species2, genome1Chromosomes, genome2Chromosomes);
    }

    private static boolean shouldReverseToCurrentLink(MultipleSyntenyService.LinkRun linkRun,
                                                      PairResultMetadata metadata) throws IOException {
        String leftSpecies = speciesKey(linkRun.getLeftSelection().getSpecies());
        String rightSpecies = speciesKey(linkRun.getRightSelection().getSpecies());
        Set<String> leftChromosomes = normalizeValues(linkRun.getLeftSelection().getChromosomes());
        Set<String> rightChromosomes = normalizeValues(linkRun.getRightSelection().getChromosomes());
        Set<String> metadataLeftChromosomes = normalizeValues(metadata.genome1Chromosomes);
        Set<String> metadataRightChromosomes = normalizeValues(metadata.genome2Chromosomes);

        boolean directMatch = leftSpecies.equals(metadata.species1)
            && rightSpecies.equals(metadata.species2)
            && leftChromosomes.equals(metadataLeftChromosomes)
            && rightChromosomes.equals(metadataRightChromosomes);
        boolean reverseMatch = leftSpecies.equals(metadata.species2)
            && rightSpecies.equals(metadata.species1)
            && leftChromosomes.equals(metadataRightChromosomes)
            && rightChromosomes.equals(metadataLeftChromosomes);

        if (directMatch) {
            return false;
        }
        if (reverseMatch) {
            return true;
        }

        throw new IOException("Pairwise result metadata does not match the current link direction for "
            + linkRun.getDisplayLabel() + ".");
    }

    private static Map<String, String> readChromosomeRenameMap(File outputDir) throws IOException {
        File[] mappingFiles = outputDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(".multiplesynteny.chrlayout.tab.xls"));
        if (mappingFiles == null || mappingFiles.length == 0) {
            throw new IOException("Missing *.MultipleSynteny.ChrLayout.tab.xls in " + outputDir.getAbsolutePath());
        }

        Arrays.sort(mappingFiles, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        Map<String, String> renamedToOriginal = new LinkedHashMap<>();
        for (File mappingFile : mappingFiles) {
            List<String> renamedChromosomes = null;
            try (BufferedReader reader = Files.newBufferedReader(mappingFile.toPath(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }

                    if (trimmed.startsWith("#DISPLAY_ORIG_CHR:")) {
                        if (renamedChromosomes != null) {
                            List<String> originalChromosomes = splitTabValues(afterFirstTab(line));
                            int count = Math.min(renamedChromosomes.size(), originalChromosomes.size());
                            for (int i = 0; i < count; i++) {
                                renamedToOriginal.putIfAbsent(
                                    renamedChromosomes.get(i).trim(),
                                    originalChromosomes.get(i).trim()
                                );
                            }
                            renamedChromosomes = null;
                        }
                        continue;
                    }

                    List<String> possibleRenamedChromosomes = parseRenamedChromosomeLine(line);
                    if (!possibleRenamedChromosomes.isEmpty()) {
                        renamedChromosomes = possibleRenamedChromosomes;
                    }
                }
            }
        }
        return renamedToOriginal;
    }

    private static List<String> parseRenamedChromosomeLine(String line) {
        int firstColon = line.indexOf(':');
        if (firstColon < 0) {
            return new ArrayList<>();
        }
        int secondColon = line.indexOf(':', firstColon + 1);
        if (secondColon < 0 || secondColon + 1 >= line.length()) {
            return new ArrayList<>();
        }
        return splitTabValues(line.substring(secondColon + 1));
    }

    private static String afterFirstTab(String line) {
        int tabIndex = line.indexOf('\t');
        return tabIndex >= 0 && tabIndex + 1 < line.length() ? line.substring(tabIndex + 1) : "";
    }

    private static List<String> splitTabValues(String rawValue) {
        List<String> values = new ArrayList<>();
        if (!hasText(rawValue)) {
            return values;
        }

        String[] tokens = rawValue.split("\t");
        for (String token : tokens) {
            if (hasText(token)) {
                values.add(token.trim());
            }
        }
        return values;
    }

    private static List<String> splitValues(String rawValue) {
        List<String> values = new ArrayList<>();
        if (!hasText(rawValue)) {
            return values;
        }

        String[] tokens = rawValue.split("[,;\\s]+");
        for (String token : tokens) {
            if (hasText(token)) {
                values.add(token.trim());
            }
        }
        return values;
    }

    private static Set<String> normalizeValues(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            if (hasText(value)) {
                normalized.add(value.trim());
            }
        }
        return normalized;
    }

    private static String speciesKey(SpeciesInfo speciesInfo) {
        return speciesInfo == null || speciesInfo.getSpeciesDirectoryName() == null
            ? ""
            : speciesInfo.getSpeciesDirectoryName().trim();
    }

    private static String restoreOriginalChromosome(String chromosomeName, Map<String, String> renamedToOriginal) {
        if (!hasText(chromosomeName)) {
            return "";
        }
        String trimmed = chromosomeName.trim();
        String restored = renamedToOriginal.get(trimmed);
        return hasText(restored) ? restored : trimmed;
    }

    private static long parseCoordinate(String rawValue) {
        try {
            return Long.parseLong(rawValue.trim());
        } catch (Exception ex) {
            return -1L;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class PairResultMetadata {
        private final String species1;
        private final String species2;
        private final List<String> genome1Chromosomes;
        private final List<String> genome2Chromosomes;

        private PairResultMetadata(String species1, String species2,
                                   List<String> genome1Chromosomes, List<String> genome2Chromosomes) {
            this.species1 = species1 == null ? "" : species1.trim();
            this.species2 = species2 == null ? "" : species2.trim();
            this.genome1Chromosomes = genome1Chromosomes == null
                ? new ArrayList<>()
                : new ArrayList<>(genome1Chromosomes);
            this.genome2Chromosomes = genome2Chromosomes == null
                ? new ArrayList<>()
                : new ArrayList<>(genome2Chromosomes);
        }
    }

    private static final class Endpoint {
        private final String chromosome;
        private final long start;
        private final long end;

        private Endpoint(String chromosome, long start, long end) {
            this.chromosome = chromosome == null ? "" : chromosome.trim();
            this.start = Math.min(start, end);
            this.end = Math.max(start, end);
        }

        private boolean isValid() {
            return hasText(chromosome) && start >= 0L && end >= 0L;
        }
    }
}
