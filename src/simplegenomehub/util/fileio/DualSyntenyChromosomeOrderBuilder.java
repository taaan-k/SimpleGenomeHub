package simplegenomehub.util.fileio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds a chromosome reordering report for Dual Synteny Plot by summarizing
 * link density between chromosome pairs in Genome Compare outputs.
 */
public final class DualSyntenyChromosomeOrderBuilder {

    private static final String REPORT_FILE_NAME = "dual_synteny.chromosome_reordering.txt";
    private static final String STANDARD_LINK_REGION_FILE_NAME = "LinkRegion.tab";
    private static final String TBTOOLS_CTL_SUFFIX = ".dualSynteny.ctl";

    private DualSyntenyChromosomeOrderBuilder() {
    }

    public static OrderResult build(File outputDir, File simplifiedGffFile) throws IOException {
        if (outputDir == null || !outputDir.isDirectory()) {
            throw new IOException("Genome Compare output directory is missing.");
        }
        if (simplifiedGffFile == null || !simplifiedGffFile.isFile()) {
            throw new IOException("Simplified .gff file is missing.");
        }

        File regionFile = findStandardRegionFile(outputDir);
        if (regionFile == null) {
            regionFile = findResultFile(outputDir, ".geneLinkedRegion.tab.xls");
        }
        File linksFile = regionFile != null ? regionFile : findResultFile(outputDir, ".geneLinks.tab.xls");
        if (linksFile == null) {
            throw new IOException("Genome Compare links file is missing.");
        }

        RunMetadata metadata = readRunMetadata(outputDir);
        File tbtoolsCtlFile = findTbtoolsCtlFile(outputDir);
        LinkSummary summary = regionFile != null
            ? summarizeFromRegionFile(regionFile)
            : summarizeFromLinksFile(linksFile, simplifiedGffFile);

        ChromosomeNamespace namespace = readChromosomeNamespace(simplifiedGffFile);
        List<String> upperChromosomes = resolveBaseChromosomeOrder(tbtoolsCtlFile, metadata.genome1Chromosomes,
            metadata.annotationFile1, namespace.upperByRawName, true);
        List<String> lowerChromosomes = resolveBaseChromosomeOrder(tbtoolsCtlFile, metadata.genome2Chromosomes,
            metadata.annotationFile2, namespace.lowerByRawName, false);
        appendMissingChromosomes(upperChromosomes, summary.observedUpperChromosomes);
        appendMissingChromosomes(lowerChromosomes, summary.observedLowerChromosomes);

        if (upperChromosomes.isEmpty()) {
            upperChromosomes.addAll(summary.observedUpperChromosomes);
        }
        if (lowerChromosomes.isEmpty()) {
            lowerChromosomes.addAll(summary.observedLowerChromosomes);
        }

        Map<String, Integer> lowerOrderIndex = buildOrderIndex(lowerChromosomes);
        ReorderingPlan reorderingPlan = buildReorderingPlan(
            upperChromosomes, lowerChromosomes, summary.pairStats.values(), lowerOrderIndex
        );
        File reportFile = writeReport(outputDir, linksFile, upperChromosomes, lowerChromosomes,
            reorderingPlan, regionFile != null);

        return new OrderResult(upperChromosomes, reorderingPlan.reorderedLowerChromosomes, reportFile);
    }

    public static OrderResult loadOrBuild(File outputDir, File simplifiedGffFile) throws IOException {
        File reportFile = getDefaultReportFile(outputDir);
        if (reportFile.isFile()) {
            OrderResult loaded = load(reportFile);
            if (loaded != null && isValidLoadedOrder(outputDir, simplifiedGffFile, loaded)) {
                return loaded;
            }
        }
        return build(outputDir, simplifiedGffFile);
    }

    public static OrderResult load(File reportFile) throws IOException {
        if (reportFile == null || !reportFile.isFile()) {
            return null;
        }

        List<String> upperChromosomes = new ArrayList<>();
        List<String> lowerChromosomes = new ArrayList<>();
        String section = "";

        try (BufferedReader reader = Files.newBufferedReader(reportFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    section = trimmed;
                    continue;
                }
                if ("[upperChromosomeOrder]".equals(section)) {
                    if (!trimmed.startsWith("upperOrder")) {
                        String chromosome = parseChromosomeValue(trimmed);
                        if (chromosome != null) {
                            upperChromosomes.add(chromosome);
                        }
                    }
                    continue;
                }
                if ("[reorderedLowerChromosomeOrder]".equals(section)) {
                    String chromosome = parseChromosomeValue(trimmed);
                    if (chromosome != null) {
                        lowerChromosomes.add(chromosome);
                    }
                }
            }
        }

        if (upperChromosomes.isEmpty() || lowerChromosomes.isEmpty()) {
            return null;
        }
        return new OrderResult(upperChromosomes, lowerChromosomes, reportFile);
    }

    public static File getDefaultReportFile(File outputDir) {
        return new File(outputDir, REPORT_FILE_NAME);
    }

    private static String parseChromosomeValue(String line) {
        String[] parts = line.split("\t");
        if (parts.length < 2) {
            return null;
        }
        String chromosome = parts[1].trim();
        return chromosome.isEmpty() || "-".equals(chromosome) ? null : chromosome;
    }

    private static File findResultFile(File outputDir, String suffixLowerCase) {
        File[] files = outputDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(suffixLowerCase.toLowerCase(Locale.ROOT)));
        if (files == null || files.length == 0) {
            return null;
        }

        List<File> orderedFiles = new ArrayList<>();
        Collections.addAll(orderedFiles, files);
        orderedFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return orderedFiles.get(0);
    }

    private static File findStandardRegionFile(File outputDir) {
        if (outputDir == null || !outputDir.isDirectory()) {
            return null;
        }

        File regionFile = new File(outputDir, STANDARD_LINK_REGION_FILE_NAME);
        return regionFile.isFile() ? regionFile : null;
    }

    private static LinkSummary summarizeFromRegionFile(File regionFile) throws IOException {
        LinkSummary summary = new LinkSummary();
        try (BufferedReader reader = Files.newBufferedReader(regionFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                String[] parts = trimmed.split("\t");
                if (parts.length < 6) {
                    continue;
                }

                String upperChromosome = parts[0];
                String lowerChromosome = parts[3];
                long upperLength = parseSpan(parts[1], parts[2]);
                long lowerLength = parseSpan(parts[4], parts[5]);

                summary.record(upperChromosome, lowerChromosome, upperLength + lowerLength);
            }
        }
        return summary;
    }

    private static LinkSummary summarizeFromLinksFile(File linksFile, File simplifiedGffFile) throws IOException {
        Map<String, String> geneToChromosome = readGeneToChromosomeMap(simplifiedGffFile);
        LinkSummary summary = new LinkSummary();

        try (BufferedReader reader = Files.newBufferedReader(linksFile.toPath(), StandardCharsets.UTF_8)) {
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

                String upperChromosome = geneToChromosome.get(parts[0]);
                String lowerChromosome = geneToChromosome.get(parts[1]);
                if (upperChromosome == null || lowerChromosome == null) {
                    continue;
                }

                summary.record(upperChromosome, lowerChromosome, 0L);
            }
        }
        return summary;
    }

    private static Map<String, String> readGeneToChromosomeMap(File simplifiedGffFile) throws IOException {
        Map<String, String> geneToChromosome = new LinkedHashMap<>();
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
                geneToChromosome.put(parts[1], parts[0]);
            }
        }
        return geneToChromosome;
    }

    private static ChromosomeNamespace readChromosomeNamespace(File simplifiedGffFile) throws IOException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> firstSeenName = new LinkedHashMap<>();
        Map<String, String> upperByRawName = new LinkedHashMap<>();
        Map<String, String> lowerByRawName = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(simplifiedGffFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                String[] parts = trimmed.split("\t");
                if (parts.length < 1) {
                    continue;
                }

                String chromosome = parts[0].trim();
                if (chromosome.isEmpty()) {
                    continue;
                }

                String rawName = extractRawChromosomeName(chromosome);
                counts.put(rawName, counts.getOrDefault(rawName, 0) + 1);
                firstSeenName.putIfAbsent(rawName, chromosome);

                if (!upperByRawName.containsKey(rawName)) {
                    upperByRawName.put(rawName, chromosome);
                } else if (!lowerByRawName.containsKey(rawName)
                    && !chromosome.equals(upperByRawName.get(rawName))) {
                    lowerByRawName.put(rawName, chromosome);
                }
            }
        }

        for (Map.Entry<String, String> entry : firstSeenName.entrySet()) {
            String rawName = entry.getKey();
            String chromosome = entry.getValue();
            if (!upperByRawName.containsKey(rawName)) {
                upperByRawName.put(rawName, chromosome);
            }
            if (!lowerByRawName.containsKey(rawName)) {
                lowerByRawName.put(rawName, chromosome);
            }
        }

        return new ChromosomeNamespace(upperByRawName, lowerByRawName);
    }

    private static String extractRawChromosomeName(String chromosome) {
        if (chromosome == null) {
            return "";
        }
        int markerIndex = chromosome.lastIndexOf("c-");
        if (markerIndex >= 0 && markerIndex + 2 < chromosome.length()) {
            return chromosome.substring(markerIndex + 2);
        }
        return chromosome;
    }

    private static long parseSpan(String startValue, String endValue) {
        try {
            long start = Long.parseLong(startValue);
            long end = Long.parseLong(endValue);
            return Math.max(0L, Math.abs(end - start) + 1L);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static List<String> readChromosomeOrder(File annotationFile,
                                                    Map<String, String> normalizedChromosomesByRawName)
        throws IOException {
        if (annotationFile == null || !annotationFile.isFile()) {
            return new ArrayList<>();
        }

        Set<String> chromosomes = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(annotationFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] parts = trimmed.split("\t");
                if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                    String rawChromosome = parts[0].trim();
                    String normalizedChromosome = normalizedChromosomesByRawName == null
                        ? null
                        : normalizedChromosomesByRawName.get(rawChromosome);
                    chromosomes.add(normalizedChromosome != null ? normalizedChromosome : rawChromosome);
                }
            }
        }
        return new ArrayList<>(chromosomes);
    }

    private static List<String> resolveBaseChromosomeOrder(File tbtoolsCtlFile,
                                                           List<String> selectedChromosomes,
                                                           File annotationFile,
                                                           Map<String, String> normalizedChromosomesByRawName,
                                                           boolean upperGenome) throws IOException {
        LinkedHashSet<String> chromosomes = new LinkedHashSet<>();
        chromosomes.addAll(readChromosomeOrderFromCtl(tbtoolsCtlFile, upperGenome));
        if (chromosomes.isEmpty()) {
            chromosomes.addAll(normalizeChromosomeOrder(selectedChromosomes, normalizedChromosomesByRawName));
        }
        if (chromosomes.isEmpty()) {
            chromosomes.addAll(readChromosomeOrder(annotationFile, normalizedChromosomesByRawName));
        }
        return new ArrayList<>(chromosomes);
    }

    private static Set<String> normalizeChromosomeOrder(List<String> selectedChromosomes,
                                                        Map<String, String> normalizedChromosomesByRawName) {
        LinkedHashSet<String> chromosomes = new LinkedHashSet<>();
        if (selectedChromosomes == null || selectedChromosomes.isEmpty()) {
            return chromosomes;
        }
        for (String chromosome : selectedChromosomes) {
            if (chromosome == null) {
                continue;
            }
            String rawChromosome = chromosome.trim();
            if (rawChromosome.isEmpty()) {
                continue;
            }
            String normalizedChromosome = normalizedChromosomesByRawName == null
                ? null
                : normalizedChromosomesByRawName.get(rawChromosome);
            chromosomes.add(normalizedChromosome != null ? normalizedChromosome : rawChromosome);
        }
        return chromosomes;
    }

    private static boolean isValidLoadedOrder(File outputDir, File simplifiedGffFile, OrderResult loaded)
        throws IOException {
        if (outputDir == null || simplifiedGffFile == null || !simplifiedGffFile.isFile() || loaded == null) {
            return false;
        }

        RunMetadata metadata = readRunMetadata(outputDir);
        File tbtoolsCtlFile = findTbtoolsCtlFile(outputDir);
        ChromosomeNamespace namespace = readChromosomeNamespace(simplifiedGffFile);
        List<String> allowedUpperChromosomes = resolveBaseChromosomeOrder(tbtoolsCtlFile, metadata.genome1Chromosomes,
            metadata.annotationFile1, namespace.upperByRawName, true);
        List<String> allowedLowerChromosomes = resolveBaseChromosomeOrder(tbtoolsCtlFile, metadata.genome2Chromosomes,
            metadata.annotationFile2, namespace.lowerByRawName, false);

        return containsOnlyAllowedChromosomes(loaded.getUpperChromosomes(), allowedUpperChromosomes)
            && containsOnlyAllowedChromosomes(loaded.getLowerChromosomes(), allowedLowerChromosomes);
    }

    private static boolean containsOnlyAllowedChromosomes(List<String> candidateChromosomes,
                                                          List<String> allowedChromosomes) {
        if (candidateChromosomes == null || candidateChromosomes.isEmpty()
            || allowedChromosomes == null || allowedChromosomes.isEmpty()) {
            return false;
        }
        Set<String> allowed = new LinkedHashSet<>(allowedChromosomes);
        for (String chromosome : candidateChromosomes) {
            if (!allowed.contains(chromosome)) {
                return false;
            }
        }
        return true;
    }

    private static RunMetadata readRunMetadata(File outputDir) throws IOException {
        File metadataFile = new File(outputDir, "run-metadata.txt");
        if (!metadataFile.isFile()) {
            return new RunMetadata(null, null, null, null);
        }

        File annotationFile1 = null;
        File annotationFile2 = null;
        List<String> genome1Chromosomes = null;
        List<String> genome2Chromosomes = null;
        try (BufferedReader reader = Files.newBufferedReader(metadataFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("annotationFile1=")) {
                    annotationFile1 = parseMetadataFile(trimmed.substring("annotationFile1=".length()));
                    continue;
                }
                if (trimmed.startsWith("annotationFile2=")) {
                    annotationFile2 = parseMetadataFile(trimmed.substring("annotationFile2=".length()));
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
        }
        return new RunMetadata(annotationFile1, annotationFile2, genome1Chromosomes, genome2Chromosomes);
    }

    private static File parseMetadataFile(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        File file = new File(trimmed);
        return file.isFile() ? file : null;
    }

    private static List<String> parseValueList(String rawValue) {
        List<String> values = new ArrayList<>();
        if (rawValue == null) {
            return values;
        }
        for (String value : rawValue.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static File findTbtoolsCtlFile(File outputDir) {
        if (outputDir == null || !outputDir.isDirectory()) {
            return null;
        }

        File[] ctlFiles = outputDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(TBTOOLS_CTL_SUFFIX.toLowerCase(Locale.ROOT)));
        if (ctlFiles == null || ctlFiles.length == 0) {
            return null;
        }

        File preferred = findPreferredSourceFile(ctlFiles, "dual_synteny.ctl");
        return preferred != null ? preferred : ctlFiles[0];
    }

    private static File findPreferredSourceFile(File[] files, String excludedFileName) {
        if (files == null || files.length == 0) {
            return null;
        }
        for (File file : files) {
            if (file != null && !excludedFileName.equalsIgnoreCase(file.getName())) {
                return file;
            }
        }
        return null;
    }

    private static Set<String> readChromosomeOrderFromCtl(File ctlFile, boolean upperGenome) throws IOException {
        LinkedHashSet<String> chromosomes = new LinkedHashSet<>();
        if (ctlFile == null || !ctlFile.isFile()) {
            return chromosomes;
        }

        try (BufferedReader reader = Files.newBufferedReader(ctlFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == (upperGenome ? 3 : 4)) {
                    for (String chromosome : line.split(",")) {
                        String trimmed = chromosome.trim();
                        if (!trimmed.isEmpty()) {
                            chromosomes.add(trimmed);
                        }
                    }
                    break;
                }
            }
        }

        return chromosomes;
    }

    private static void appendMissingChromosomes(List<String> chromosomeOrder,
                                                 Collection<String> observedChromosomes) {
        if (chromosomeOrder == null || observedChromosomes == null || observedChromosomes.isEmpty()) {
            return;
        }

        Set<String> existing = new LinkedHashSet<>();
        existing.addAll(chromosomeOrder);
        for (String chromosome : observedChromosomes) {
            if (existing.add(chromosome)) {
                chromosomeOrder.add(chromosome);
            }
        }
    }

    private static Map<String, Integer> buildOrderIndex(List<String> chromosomeOrder) {
        Map<String, Integer> orderIndex = new LinkedHashMap<>();
        if (chromosomeOrder == null) {
            return orderIndex;
        }

        for (int i = 0; i < chromosomeOrder.size(); i++) {
            orderIndex.putIfAbsent(chromosomeOrder.get(i), i);
        }
        return orderIndex;
    }

    private static Comparator<ChromosomePairStats> createPerUpperComparator(Map<String, Integer> lowerOrderIndex) {
        return Comparator
            .comparingLong(ChromosomePairStats::getLinkCount).reversed()
            .thenComparing(Comparator.comparingLong(ChromosomePairStats::getTotalAlignedLength).reversed())
            .thenComparingInt(stats -> lowerOrderIndex.getOrDefault(stats.getLowerChromosome(), Integer.MAX_VALUE))
            .thenComparing(ChromosomePairStats::getLowerChromosome, String.CASE_INSENSITIVE_ORDER);
    }

    private static ReorderingPlan buildReorderingPlan(List<String> upperChromosomes,
                                                      List<String> lowerChromosomes,
                                                      Collection<ChromosomePairStats> pairStats,
                                                      Map<String, Integer> lowerOrderIndex) {
        Map<String, List<ChromosomePairStats>> rankingByUpperChromosome = new LinkedHashMap<>();
        for (ChromosomePairStats stats : pairStats) {
            rankingByUpperChromosome
                .computeIfAbsent(stats.getUpperChromosome(), key -> new ArrayList<>())
                .add(stats);
        }

        Comparator<ChromosomePairStats> perUpperComparator = createPerUpperComparator(lowerOrderIndex);
        List<ChromosomePairStats> orderedPairRanking = new ArrayList<>();
        Map<String, ChromosomePairStats> selectedMatches = new LinkedHashMap<>();

        List<String> reorderedLowerChromosomes = new ArrayList<>();
        Set<String> addedLowerChromosomes = new LinkedHashSet<>();

        for (String upperChromosome : upperChromosomes) {
            List<ChromosomePairStats> matches = rankingByUpperChromosome.get(upperChromosome);
            if (matches == null || matches.isEmpty()) {
                continue;
            }

            matches.sort(perUpperComparator);
            orderedPairRanking.addAll(matches);
            for (ChromosomePairStats stats : matches) {
                if (addedLowerChromosomes.add(stats.getLowerChromosome())) {
                    selectedMatches.put(upperChromosome, stats);
                    reorderedLowerChromosomes.add(stats.getLowerChromosome());
                    break;
                }
            }
        }

        for (String lowerChromosome : lowerChromosomes) {
            if (addedLowerChromosomes.add(lowerChromosome)) {
                reorderedLowerChromosomes.add(lowerChromosome);
            }
        }

        for (ChromosomePairStats stats : orderedPairRanking) {
            if (addedLowerChromosomes.add(stats.getLowerChromosome())) {
                reorderedLowerChromosomes.add(stats.getLowerChromosome());
            }
        }

        return new ReorderingPlan(reorderedLowerChromosomes, orderedPairRanking, selectedMatches);
    }

    private static File writeReport(File outputDir, File linksFile, List<String> upperChromosomes,
                                    List<String> originalLowerChromosomes, ReorderingPlan reorderingPlan,
                                    boolean includesAlignedLength) throws IOException {
        File reportFile = getDefaultReportFile(outputDir);
        try (BufferedWriter writer = Files.newBufferedWriter(reportFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("# Dual Synteny Plot chromosome reordering");
            writer.newLine();
            writer.write("# generatedAt=" + LocalDateTime.now());
            writer.newLine();
            writer.write("# sourceLinksFile=" + linksFile.getAbsolutePath());
            writer.newLine();
            writer.write("# sortRule=upperChromosomeOrder(anchor),perUpperBest(linkCount desc,totalAlignedLength desc),remainingLowerChromosomes(original order)");
            writer.newLine();
            writer.write("# alignedLengthIncluded=" + includesAlignedLength);
            writer.newLine();
            writer.newLine();

            writer.write("[bestMatchByUpperChromosome]");
            writer.newLine();
            writer.write("upperOrder\tupperChromosome\tselectedLowerChromosome\tlinkCount\ttotalAlignedLength");
            writer.newLine();
            for (int i = 0; i < upperChromosomes.size(); i++) {
                String upperChromosome = upperChromosomes.get(i);
                ChromosomePairStats selected = reorderingPlan.selectedMatches.get(upperChromosome);
                if (selected == null) {
                    writer.write((i + 1) + "\t" + upperChromosome + "\t-\t0\t0");
                } else {
                    writer.write((i + 1) + "\t" + upperChromosome + "\t" + selected.getLowerChromosome() +
                        "\t" + selected.getLinkCount() + "\t" + selected.getTotalAlignedLength());
                }
                writer.newLine();
            }

            writer.newLine();
            writer.write("[pairRanking]");
            writer.newLine();
            writer.write("rank\tupperChromosome\tmatchRankWithinUpper\tselected\tlowerChromosome\tlinkCount\ttotalAlignedLength");
            writer.newLine();
            Map<String, Integer> perUpperMatchRank = new LinkedHashMap<>();
            for (int i = 0; i < reorderingPlan.orderedPairRanking.size(); i++) {
                ChromosomePairStats stats = reorderingPlan.orderedPairRanking.get(i);
                int matchRank = perUpperMatchRank.merge(stats.getUpperChromosome(), 1, Integer::sum);
                boolean selected = reorderingPlan.selectedMatches.get(stats.getUpperChromosome()) == stats;
                writer.write((i + 1) + "\t" + stats.getUpperChromosome() + "\t" + matchRank + "\t" +
                    (selected ? "yes" : "no") + "\t" + stats.getLowerChromosome() + "\t" +
                    stats.getLinkCount() + "\t" + stats.getTotalAlignedLength());
                writer.newLine();
            }

            writer.newLine();
            writer.write("[upperChromosomeOrder]");
            writer.newLine();
            for (int i = 0; i < upperChromosomes.size(); i++) {
                writer.write((i + 1) + "\t" + upperChromosomes.get(i));
                writer.newLine();
            }

            writer.newLine();
            writer.write("[originalLowerChromosomeOrder]");
            writer.newLine();
            for (int i = 0; i < originalLowerChromosomes.size(); i++) {
                writer.write((i + 1) + "\t" + originalLowerChromosomes.get(i));
                writer.newLine();
            }

            writer.newLine();
            writer.write("[reorderedLowerChromosomeOrder]");
            writer.newLine();
            for (int i = 0; i < reorderingPlan.reorderedLowerChromosomes.size(); i++) {
                writer.write((i + 1) + "\t" + reorderingPlan.reorderedLowerChromosomes.get(i));
                writer.newLine();
            }
        }
        return reportFile;
    }

    public static final class OrderResult {
        private final List<String> upperChromosomes;
        private final List<String> lowerChromosomes;
        private final File reportFile;

        private OrderResult(List<String> upperChromosomes, List<String> lowerChromosomes, File reportFile) {
            this.upperChromosomes = new ArrayList<>(upperChromosomes);
            this.lowerChromosomes = new ArrayList<>(lowerChromosomes);
            this.reportFile = reportFile;
        }

        public List<String> getUpperChromosomes() {
            return new ArrayList<>(upperChromosomes);
        }

        public List<String> getLowerChromosomes() {
            return new ArrayList<>(lowerChromosomes);
        }

        public File getReportFile() {
            return reportFile;
        }
    }

    public static final class ChromosomePairStats {
        private final String upperChromosome;
        private final String lowerChromosome;
        private long linkCount;
        private long totalAlignedLength;

        private ChromosomePairStats(String upperChromosome, String lowerChromosome) {
            this.upperChromosome = upperChromosome;
            this.lowerChromosome = lowerChromosome;
        }

        public String getUpperChromosome() {
            return upperChromosome;
        }

        public String getLowerChromosome() {
            return lowerChromosome;
        }

        public long getLinkCount() {
            return linkCount;
        }

        public long getTotalAlignedLength() {
            return totalAlignedLength;
        }
    }

    private static final class ReorderingPlan {
        private final List<String> reorderedLowerChromosomes;
        private final List<ChromosomePairStats> orderedPairRanking;
        private final Map<String, ChromosomePairStats> selectedMatches;

        private ReorderingPlan(List<String> reorderedLowerChromosomes,
                               List<ChromosomePairStats> orderedPairRanking,
                               Map<String, ChromosomePairStats> selectedMatches) {
            this.reorderedLowerChromosomes = new ArrayList<>(reorderedLowerChromosomes);
            this.orderedPairRanking = new ArrayList<>(orderedPairRanking);
            this.selectedMatches = new LinkedHashMap<>(selectedMatches);
        }
    }

    private static final class LinkSummary {
        private final Map<String, ChromosomePairStats> pairStats = new LinkedHashMap<>();
        private final Set<String> observedUpperChromosomes = new LinkedHashSet<>();
        private final Set<String> observedLowerChromosomes = new LinkedHashSet<>();

        private void record(String upperChromosome, String lowerChromosome, long alignedLength) {
            if (upperChromosome == null || lowerChromosome == null) {
                return;
            }

            observedUpperChromosomes.add(upperChromosome);
            observedLowerChromosomes.add(lowerChromosome);

            String key = upperChromosome + '\t' + lowerChromosome;
            ChromosomePairStats stats = pairStats.computeIfAbsent(key,
                ignored -> new ChromosomePairStats(upperChromosome, lowerChromosome));
            stats.linkCount++;
            stats.totalAlignedLength += Math.max(0L, alignedLength);
        }
    }

    private static final class RunMetadata {
        private final File annotationFile1;
        private final File annotationFile2;
        private final List<String> genome1Chromosomes;
        private final List<String> genome2Chromosomes;

        private RunMetadata(File annotationFile1, File annotationFile2,
                            List<String> genome1Chromosomes, List<String> genome2Chromosomes) {
            this.annotationFile1 = annotationFile1;
            this.annotationFile2 = annotationFile2;
            this.genome1Chromosomes = genome1Chromosomes == null ? null : new ArrayList<>(genome1Chromosomes);
            this.genome2Chromosomes = genome2Chromosomes == null ? null : new ArrayList<>(genome2Chromosomes);
        }
    }

    private static final class ChromosomeNamespace {
        private final Map<String, String> upperByRawName;
        private final Map<String, String> lowerByRawName;

        private ChromosomeNamespace(Map<String, String> upperByRawName, Map<String, String> lowerByRawName) {
            this.upperByRawName = new LinkedHashMap<>(upperByRawName);
            this.lowerByRawName = new LinkedHashMap<>(lowerByRawName);
        }
    }
}
