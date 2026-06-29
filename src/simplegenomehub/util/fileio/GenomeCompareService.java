package simplegenomehub.util.fileio;

import biocjava.bioIO.BioSoftPipeServer.OneStepMCScanXPureJava;
import simplegenomehub.model.SpeciesInfo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Runs TBtools One Step MCScanX (Pure Java) using species files already
 * managed inside SimpleGenomeHub.
 */
public final class GenomeCompareService {

    private static final Logger logger = Logger.getLogger(GenomeCompareService.class.getName());
    public static final int DEFAULT_CPU = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
    public static final double DEFAULT_EVALUE = 1e-5;
    public static final int DEFAULT_NUM_HITS = 5;
    public static final boolean DEFAULT_DIRECT_PLOT = true;

    public interface ProgressListener {
        void onProgress(String message);
    }

    public static final class Parameters {
        private final int cpu;
        private final double evalue;
        private final int numHits;
        private final boolean directPlot;
        private final List<String> genome1Chromosomes;
        private final List<String> genome2Chromosomes;
        private final List<String> highlightGeneIds;

        public Parameters(int cpu, double evalue, int numHits, boolean directPlot) {
            this(cpu, evalue, numHits, directPlot, null, null, null);
        }

        public Parameters(int cpu, double evalue, int numHits, boolean directPlot,
                          List<String> highlightGeneIds) {
            this(cpu, evalue, numHits, directPlot, null, null, highlightGeneIds);
        }

        public Parameters(int cpu, double evalue, int numHits, boolean directPlot,
                          List<String> genome1Chromosomes, List<String> genome2Chromosomes,
                          List<String> highlightGeneIds) {
            this.cpu = cpu;
            this.evalue = evalue;
            this.numHits = numHits;
            this.directPlot = directPlot;
            this.genome1Chromosomes = genome1Chromosomes == null ? null : new ArrayList<>(genome1Chromosomes);
            this.genome2Chromosomes = genome2Chromosomes == null ? null : new ArrayList<>(genome2Chromosomes);
            this.highlightGeneIds = highlightGeneIds == null ? new ArrayList<>() : new ArrayList<>(highlightGeneIds);
        }

        public int getCpu() { return cpu; }
        public double getEvalue() { return evalue; }
        public int getNumHits() { return numHits; }
        public boolean isDirectPlot() { return directPlot; }
        public List<String> getGenome1Chromosomes() {
            return genome1Chromosomes == null ? null : new ArrayList<>(genome1Chromosomes);
        }
        public List<String> getGenome2Chromosomes() {
            return genome2Chromosomes == null ? null : new ArrayList<>(genome2Chromosomes);
        }
        public List<String> getHighlightGeneIds() { return new ArrayList<>(highlightGeneIds); }
    }

    public static final class Result {
        private final File outputDir;
        private final String prefix1;
        private final String prefix2;
        private final boolean nativeSelfComparisonMode;
        private final List<File> collinearityFiles;
        private final List<File> htmlOutputs;

        public Result(File outputDir, String prefix1, String prefix2,
                      boolean nativeSelfComparisonMode,
                      List<File> collinearityFiles, List<File> htmlOutputs) {
            this.outputDir = outputDir;
            this.prefix1 = prefix1;
            this.prefix2 = prefix2;
            this.nativeSelfComparisonMode = nativeSelfComparisonMode;
            this.collinearityFiles = new ArrayList<>(collinearityFiles);
            this.htmlOutputs = new ArrayList<>(htmlOutputs);
        }

        public File getOutputDir() { return outputDir; }
        public String getPrefix1() { return prefix1; }
        public String getPrefix2() { return prefix2; }
        public boolean isNativeSelfComparisonMode() { return nativeSelfComparisonMode; }
        public List<File> getCollinearityFiles() { return new ArrayList<>(collinearityFiles); }
        public List<File> getHtmlOutputs() { return new ArrayList<>(htmlOutputs); }

        public File getPrimaryCollinearityFile() {
            return collinearityFiles.isEmpty() ? null : collinearityFiles.get(0);
        }

        public File getLinkRegionFile() {
            File linkRegionFile = new File(outputDir, GenomeCompareLinkRegionExporter.OUTPUT_FILE_NAME);
            return linkRegionFile.isFile() ? linkRegionFile : null;
        }
    }

    private GenomeCompareService() {
    }

    public static Parameters createDefaultParameters() {
        return new Parameters(DEFAULT_CPU, DEFAULT_EVALUE, DEFAULT_NUM_HITS, DEFAULT_DIRECT_PLOT,
            null, null, null);
    }

    public static Result run(SpeciesInfo genome1Species, SpeciesInfo genome2Species,
                             Parameters parameters, ProgressListener progressListener) throws Exception {
        return runInternal(genome1Species, genome2Species, parameters, null, progressListener);
    }

    public static Result run(SpeciesInfo genome1Species, SpeciesInfo genome2Species,
                             Parameters parameters, File outputDir,
                             ProgressListener progressListener) throws Exception {
        return runInternal(genome1Species, genome2Species, parameters, outputDir, progressListener);
    }

    private static Result runInternal(SpeciesInfo genome1Species, SpeciesInfo genome2Species,
                                      Parameters parameters, File requestedOutputDir,
                                      ProgressListener progressListener) throws Exception {
        if (genome1Species == null) {
            throw new IllegalArgumentException("Genome 1 is required.");
        }
        if (genome2Species == null) {
            throw new IllegalArgumentException("Genome 2 is required.");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("Genome compare parameters are required.");
        }
        validateParameters(parameters);

        File genomeFile1 = requireFile(genome1Species.getGenomeFile(),
            "Genome 1 FASTA is missing. Please confirm the species has a genome sequence file.");
        File genomeFile2 = requireFile(genome2Species.getGenomeFile(),
            "Genome 2 FASTA is missing. Please confirm the species has a genome sequence file.");
        File annotationFile1 = requireFile(genome1Species.getAnnotationFile(),
            "Genome 1 annotation is missing. Please confirm the species has a GFF/GTF file.");
        File annotationFile2 = requireFile(genome2Species.getAnnotationFile(),
            "Genome 2 annotation is missing. Please confirm the species has a GFF/GTF file.");

        String prefix1 = buildAutoPrefix(genome1Species, 1);
        String prefix2 = buildAutoPrefix(genome2Species, 2);
        boolean selfComparison = isSelfComparison(genome1Species, genome2Species, genomeFile1, genomeFile2,
            annotationFile1, annotationFile2);
        boolean reuseSelfComparisonInputs = selfComparison
            && haveEquivalentChromosomeSelections(parameters.getGenome1Chromosomes(), parameters.getGenome2Chromosomes());
        File outputDir = null;

        try {
            report(progressListener, "Creating Genome Compare output directory...");
            outputDir = prepareOutputDirectory(genome1Species, requestedOutputDir);
            writeMetadata(outputDir, "STARTED", genome1Species, genome2Species, parameters,
                prefix1, prefix2, null, null);

            report(progressListener, "Reading genome and annotation files from selected species...");
            report(progressListener, "Using auto prefixes: " + prefix1 + " / " + prefix2);

            FilteredSpeciesInputs filteredInputs1 = prepareFilteredInputs(
                outputDir,
                genomeFile1,
                annotationFile1,
                prefix1,
                parameters.getGenome1Chromosomes(),
                progressListener
            );
            FilteredSpeciesInputs filteredInputs2;
            if (reuseSelfComparisonInputs) {
                filteredInputs2 = filteredInputs1;
                report(progressListener,
                    "Using the same filtered genome/annotation files for both inputs so TBtools runs native self-comparison mode.");
            } else {
                filteredInputs2 = prepareFilteredInputs(
                    outputDir,
                    genomeFile2,
                    annotationFile2,
                    prefix2,
                    parameters.getGenome2Chromosomes(),
                    progressListener
                );
            }

            OneStepMCScanXPureJava runner = new OneStepMCScanXPureJava();
            runner.setWkDirectory(outputDir);
            runner.setInGenome_1(filteredInputs1.genomeFile);
            runner.setInGenome_2(filteredInputs2.genomeFile);
            runner.setInGxf_1(filteredInputs1.annotationFile);
            runner.setInGxf_2(filteredInputs2.annotationFile);
            runner.setNumberOfThread(parameters.getCpu());
            runner.setNumberOfBlastHit(parameters.getNumHits());
            runner.setEvalue(parameters.getEvalue());
            runner.setIdPrefix_1(reuseSelfComparisonInputs ? "" : prefix1);
            runner.setIdPrefix_2(reuseSelfComparisonInputs ? "" : prefix2);

            report(progressListener, "Running TBtools One Step MCScanX (Pure Java)...");
            runner.process();

            List<File> collinearityFiles = findFilesWithSuffix(outputDir, ".collinearity");
            if (collinearityFiles.isEmpty()) {
                throw new IOException(
                    "TBtools One Step MCScanX did not produce a .collinearity result. " +
                    "Please check the genome/annotation files and confirm DIAMOND or BLAST+ is available on PATH."
                );
            }
            if (selfComparison) {
                report(progressListener,
                    "Filtering diagonal self-comparison blocks and same-position pairs from collinearity results...");
                sanitizeSelfComparisonCollinearity(outputDir, collinearityFiles);
            }
            report(progressListener, "Exporting LinkRegion.tab from synteny blocks...");
            GenomeCompareLinkRegionExporter.generate(outputDir, collinearityFiles.get(0));
            writeHighlightGeneList(outputDir, parameters.getHighlightGeneIds());

            List<File> htmlOutputs = findHtmlOutputs(outputDir);
            if (!parameters.isDirectPlot() && !htmlOutputs.isEmpty()) {
                report(progressListener, "Direct Plot is off. Removing generated HTML plot outputs...");
                cleanupHtmlOutputs(htmlOutputs);
                htmlOutputs = findHtmlOutputs(outputDir);
            }

            Result result = new Result(outputDir, prefix1, prefix2, reuseSelfComparisonInputs,
                collinearityFiles, htmlOutputs);
            writeMetadata(outputDir, "SUCCESS", genome1Species, genome2Species, parameters,
                prefix1, prefix2, result, null);
            report(progressListener, "Genome Compare completed.");
            return result;
        } catch (Exception ex) {
            if (outputDir != null) {
                writeMetadata(outputDir, "FAILED", genome1Species, genome2Species, parameters,
                    prefix1, prefix2, null, ex.getMessage());
            }
            throw ex;
        }
    }

    private static File prepareOutputDirectory(SpeciesInfo primaryGenome, File requestedOutputDir) throws IOException {
        if (requestedOutputDir == null) {
            return GenomeCompareProjectGenerator.createProjectDirectory(primaryGenome);
        }

        if (requestedOutputDir.exists()) {
            if (!requestedOutputDir.isDirectory()) {
                throw new IOException("Output path exists but is not a directory: "
                    + requestedOutputDir.getAbsolutePath());
            }
            return requestedOutputDir;
        }

        if (!requestedOutputDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + requestedOutputDir.getAbsolutePath());
        }
        return requestedOutputDir;
    }

    private static void validateParameters(Parameters parameters) {
        if (parameters.getCpu() < 1) {
            throw new IllegalArgumentException("CPU for similarity search must be at least 1.");
        }
        if (parameters.getEvalue() <= 0) {
            throw new IllegalArgumentException("E-value must be greater than 0.");
        }
        if (parameters.getNumHits() < 1) {
            throw new IllegalArgumentException("Num of hits must be at least 1.");
        }
    }

    private static boolean isSelfComparison(SpeciesInfo genome1Species, SpeciesInfo genome2Species,
                                            File genomeFile1, File genomeFile2,
                                            File annotationFile1, File annotationFile2) {
        if (genome1Species != null && genome2Species != null
            && genome1Species == genome2Species) {
            return true;
        }

        if (genome1Species != null && genome2Species != null) {
            String species1 = genome1Species.getSpeciesDirectoryName();
            String species2 = genome2Species.getSpeciesDirectoryName();
            if (species1 != null && species1.equals(species2)) {
                return true;
            }
        }

        return areSameFile(genomeFile1, genomeFile2) && areSameFile(annotationFile1, annotationFile2);
    }

    private static boolean areSameFile(File left, File right) {
        if (left == null || right == null) {
            return false;
        }
        return left.getAbsolutePath().equalsIgnoreCase(right.getAbsolutePath());
    }

    private static boolean haveEquivalentChromosomeSelections(List<String> leftSelections,
                                                              List<String> rightSelections) {
        return normalizeChromosomeSelection(leftSelections).equals(normalizeChromosomeSelection(rightSelections));
    }

    private static LinkedHashSet<String> normalizeChromosomeSelection(List<String> selections) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (selections == null) {
            return normalized;
        }
        for (String selection : selections) {
            if (selection == null) {
                continue;
            }
            String trimmed = selection.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private static void sanitizeSelfComparisonCollinearity(File outputDir, List<File> collinearityFiles)
        throws IOException {
        if (outputDir == null || !outputDir.isDirectory() || collinearityFiles == null || collinearityFiles.isEmpty()) {
            return;
        }

        File simplifiedGffFile = GenomeCompareLinkRegionExporter.findSimplifiedGff(outputDir);
        if (simplifiedGffFile == null || !simplifiedGffFile.isFile()) {
            return;
        }

        java.util.Map<String, GenomeCompareLinkRegionExporter.GeneRegion> geneRegions =
            GenomeCompareLinkRegionExporter.readGeneRegions(simplifiedGffFile);
        if (geneRegions.isEmpty()) {
            return;
        }

        for (File collinearityFile : collinearityFiles) {
            sanitizeSelfComparisonCollinearityFile(collinearityFile, geneRegions);
        }
    }

    private static void sanitizeSelfComparisonCollinearityFile(
        File collinearityFile,
        java.util.Map<String, GenomeCompareLinkRegionExporter.GeneRegion> geneRegions
    ) throws IOException {
        if (collinearityFile == null || !collinearityFile.isFile()) {
            return;
        }

        List<String> sourceLines = Files.readAllLines(collinearityFile.toPath(), StandardCharsets.UTF_8);
        List<String> keptLines = sanitizeSelfComparisonCollinearityLines(sourceLines, geneRegions);

        Files.write(collinearityFile.toPath(), keptLines, StandardCharsets.UTF_8);
    }

    static List<String> sanitizeSelfComparisonCollinearityLines(
        List<String> sourceLines,
        java.util.Map<String, GenomeCompareLinkRegionExporter.GeneRegion> geneRegions
    ) {
        List<String> keptLines = new ArrayList<>();
        if (sourceLines == null || sourceLines.isEmpty()) {
            return keptLines;
        }

        List<String> pendingBlockLines = new ArrayList<>();
        boolean inAlignmentBlock = false;
        boolean blockHasKeptPairs = false;
        boolean blockShouldDrop = false;

        for (String line : sourceLines) {
            String trimmed = line == null ? "" : line.trim();

            if (trimmed.startsWith("## Alignment ")) {
                if (inAlignmentBlock && blockHasKeptPairs && !blockShouldDrop) {
                    keptLines.addAll(pendingBlockLines);
                }

                pendingBlockLines.clear();
                pendingBlockLines.add(line);
                inAlignmentBlock = true;
                blockHasKeptPairs = false;
                blockShouldDrop = isSameChromosomeAlignmentHeader(trimmed);
                continue;
            }

            if (!inAlignmentBlock) {
                keptLines.add(line);
                continue;
            }

            if (!GenomeCompareLinkRegionExporter.isCollinearPairLine(line)) {
                pendingBlockLines.add(line);
                continue;
            }

            if (blockShouldDrop) {
                continue;
            }

            String[] parts = line.split("\t");
            if (parts.length < 3) {
                pendingBlockLines.add(line);
                blockHasKeptPairs = true;
                continue;
            }

            String leftGeneId = parts[1] == null ? "" : parts[1].trim();
            String rightGeneId = parts[2] == null ? "" : parts[2].trim();
            GenomeCompareLinkRegionExporter.GeneRegion leftRegion = geneRegions.get(leftGeneId);
            GenomeCompareLinkRegionExporter.GeneRegion rightRegion = geneRegions.get(rightGeneId);

            if (isSamePositionSelfPair(leftGeneId, rightGeneId, leftRegion, rightRegion)) {
                continue;
            }

            pendingBlockLines.add(line);
            blockHasKeptPairs = true;
        }

        if (inAlignmentBlock && blockHasKeptPairs && !blockShouldDrop) {
            keptLines.addAll(pendingBlockLines);
        } else if (!inAlignmentBlock) {
            keptLines.addAll(pendingBlockLines);
        }

        return keptLines;
    }

    private static boolean isSameChromosomeAlignmentHeader(String trimmedLine) {
        int pairStart = trimmedLine.lastIndexOf(' ');
        if (pairStart <= 0) {
            return false;
        }

        String orientation = trimmedLine.substring(pairStart + 1).trim();
        if (!"plus".equalsIgnoreCase(orientation) && !"minus".equalsIgnoreCase(orientation)) {
            return false;
        }

        String withoutOrientation = trimmedLine.substring(0, pairStart).trim();
        int pairTokenStart = withoutOrientation.lastIndexOf(' ');
        if (pairTokenStart <= 0) {
            return false;
        }

        String pairToken = withoutOrientation.substring(pairTokenStart + 1).trim();
        String[] chromosomes = pairToken.split("&", 2);
        return chromosomes.length == 2 && chromosomes[0].equals(chromosomes[1]);
    }

    private static boolean isSamePositionSelfPair(
        String leftGeneId,
        String rightGeneId,
        GenomeCompareLinkRegionExporter.GeneRegion leftRegion,
        GenomeCompareLinkRegionExporter.GeneRegion rightRegion
    ) {
        if (leftGeneId == null || rightGeneId == null || leftRegion == null || rightRegion == null) {
            return false;
        }

        if (leftGeneId.equals(rightGeneId)) {
            return true;
        }

        return leftRegion.chromosome.equals(rightRegion.chromosome)
            && leftRegion.start == rightRegion.start
            && leftRegion.end == rightRegion.end;
    }

    private static FilteredSpeciesInputs prepareFilteredInputs(File outputDir,
                                                               File genomeFile,
                                                               File annotationFile,
                                                               String prefix,
                                                               List<String> selectedChromosomes,
                                                               ProgressListener progressListener) throws IOException {
        Set<String> allChromosomes = readAllChromosomeIdsFromGenome(genomeFile);
        Set<String> keptChromosomes = resolveKeptChromosomes(allChromosomes, selectedChromosomes);
        if (keptChromosomes.isEmpty()) {
            throw new IOException("No chromosomes were selected for Genome Compare.");
        }

        report(progressListener,
            String.format(Locale.US, "Applying chromosome selection: %d chromosomes kept", keptChromosomes.size()));

        File filteredInputDir = new File(outputDir, "selected-inputs");
        if (!filteredInputDir.exists() && !filteredInputDir.mkdirs()) {
            throw new IOException("Failed to create selected-inputs directory.");
        }
        File filteredGenomeFile = new File(filteredInputDir, prefix + ".selected.genome.fasta");
        File filteredAnnotationFile = new File(filteredInputDir, prefix + ".selected.annotation.gff");
        writeFilteredGenomeFile(genomeFile, filteredGenomeFile, keptChromosomes);
        writeFilteredAnnotationFile(annotationFile, filteredAnnotationFile, keptChromosomes);
        return new FilteredSpeciesInputs(filteredGenomeFile, filteredAnnotationFile, keptChromosomes);
    }

    private static LinkedHashSet<String> readAllChromosomeIdsFromGenome(File genomeFile) throws IOException {
        LinkedHashSet<String> chromosomeIds = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(genomeFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith(">")) {
                    continue;
                }
                String chromosomeId = extractFastaSequenceId(trimmed);
                if (!chromosomeId.isEmpty()) {
                    chromosomeIds.add(chromosomeId);
                }
            }
        }
        return chromosomeIds;
    }

    private static Set<String> resolveKeptChromosomes(Set<String> allChromosomes, List<String> selectedChromosomes) {
        LinkedHashSet<String> keptChromosomes = new LinkedHashSet<>();
        if (allChromosomes == null || allChromosomes.isEmpty()) {
            return keptChromosomes;
        }
        if (selectedChromosomes == null) {
            keptChromosomes.addAll(allChromosomes);
            return keptChromosomes;
        }
        if (selectedChromosomes.isEmpty()) {
            return keptChromosomes;
        }
        for (String chromosome : selectedChromosomes) {
            if (chromosome == null) {
                continue;
            }
            String trimmed = chromosome.trim();
            if (!trimmed.isEmpty() && allChromosomes.contains(trimmed)) {
                keptChromosomes.add(trimmed);
            }
        }
        return keptChromosomes;
    }

    private static String extractFastaSequenceId(String headerLine) {
        String trimmed = headerLine == null ? "" : headerLine.trim();
        if (!trimmed.startsWith(">")) {
            return "";
        }
        String withoutMarker = trimmed.substring(1).trim();
        if (withoutMarker.isEmpty()) {
            return "";
        }
        return withoutMarker.split("\\s+")[0];
    }

    private static void writeFilteredGenomeFile(File sourceFile, File targetFile, Set<String> keptChromosomes)
        throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(sourceFile.toPath(), StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(targetFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            boolean keepCurrentSequence = false;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith(">")) {
                    String chromosomeId = extractFastaSequenceId(trimmed);
                    keepCurrentSequence = keptChromosomes.contains(chromosomeId);
                }
                if (keepCurrentSequence) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }
    }

    private static void writeFilteredAnnotationFile(File sourceFile, File targetFile, Set<String> keptChromosomes)
        throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(sourceFile.toPath(), StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(targetFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    writer.write(line);
                    writer.newLine();
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length < 1) {
                    continue;
                }
                String chromosome = fields[0] == null ? "" : fields[0].trim();
                if (keptChromosomes.contains(chromosome)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }
    }

    private static void writeHighlightGeneList(File outputDir, List<String> highlightGeneIds) throws IOException {
        if (outputDir == null || !outputDir.isDirectory()) {
            return;
        }

        File highlightFile = new File(outputDir, "HighlightGeneList.txt");
        List<String> normalizedIds = new ArrayList<>();
        if (highlightGeneIds != null) {
            for (String geneId : highlightGeneIds) {
                if (geneId == null) {
                    continue;
                }
                String trimmed = geneId.trim();
                if (!trimmed.isEmpty() && !normalizedIds.contains(trimmed)) {
                    normalizedIds.add(trimmed);
                }
            }
        }

        if (normalizedIds.isEmpty()) {
            Files.deleteIfExists(highlightFile.toPath());
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(highlightFile.toPath(), StandardCharsets.UTF_8)) {
            for (String geneId : normalizedIds) {
                writer.write(geneId);
                writer.newLine();
            }
        }
    }

    private static File requireFile(File file, String missingMessage) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException(missingMessage);
        }
        return file;
    }

    private static String buildAutoPrefix(SpeciesInfo species, int slotIndex) {
        String base = species == null ? "" : species.getSpeciesDirectoryName();
        if (base == null) {
            base = "";
        }

        base = base.replaceAll("[^A-Za-z0-9]+", "_");
        base = base.replaceAll("_+", "_");
        base = base.replaceAll("^_+|_+$", "");

        if (base.isEmpty()) {
            base = "genome";
        }
        if (base.length() > 24) {
            base = base.substring(0, 24);
        }
        if (!Character.isLetter(base.charAt(0))) {
            base = "g" + slotIndex + "_" + base;
        }

        return base + "_" + slotIndex;
    }

    private static List<File> findFilesWithSuffix(File outputDir, String suffix) {
        List<File> matches = new ArrayList<>();
        if (outputDir == null || !outputDir.isDirectory()) {
            return matches;
        }

        String normalizedSuffix = suffix.toLowerCase(Locale.ROOT);
        File[] files = outputDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(normalizedSuffix));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                if (file.isFile() && file.length() > 0) {
                    matches.add(file);
                }
            }
        }
        return matches;
    }

    private static List<File> findHtmlOutputs(File outputDir) {
        List<File> matches = new ArrayList<>();
        if (outputDir == null || !outputDir.isDirectory()) {
            return matches;
        }

        File[] files = outputDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).contains("html"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            matches.addAll(Arrays.asList(files));
        }
        return matches;
    }

    private static void cleanupHtmlOutputs(List<File> htmlOutputs) {
        for (File htmlOutput : htmlOutputs) {
            try {
                deleteRecursively(htmlOutput);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Failed to remove Genome Compare HTML output: " + htmlOutput, ex);
            }
        }
    }

    private static void deleteRecursively(File target) throws IOException {
        if (target == null || !target.exists()) {
            return;
        }

        try (Stream<java.nio.file.Path> pathStream = Files.walk(target.toPath())) {
            pathStream.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException) {
                throw (IOException) ex.getCause();
            }
            throw ex;
        }
    }

    private static void writeMetadata(File outputDir, String status,
                                      SpeciesInfo genome1Species, SpeciesInfo genome2Species,
                                      Parameters parameters, String prefix1, String prefix2,
                                      Result result, String errorMessage) {
        if (outputDir == null) {
            return;
        }

        File metadataFile = new File(outputDir, "run-metadata.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(metadataFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("status=" + safe(status));
            writer.newLine();
            writer.write("runTime=" + LocalDateTime.now());
            writer.newLine();
            writer.write("species1=" + safe(genome1Species != null ? genome1Species.getSpeciesDirectoryName() : ""));
            writer.newLine();
            writer.write("species2=" + safe(genome2Species != null ? genome2Species.getSpeciesDirectoryName() : ""));
            writer.newLine();
            writer.write("prefix1=" + safe(prefix1));
            writer.newLine();
            writer.write("prefix2=" + safe(prefix2));
            writer.newLine();
            if (result != null) {
                writer.write("nativeSelfComparisonMode=" + result.isNativeSelfComparisonMode());
                writer.newLine();
            }

            if (parameters != null) {
                writer.write("cpu=" + parameters.getCpu());
                writer.newLine();
                writer.write("evalue=" + parameters.getEvalue());
                writer.newLine();
                writer.write("numHits=" + parameters.getNumHits());
                writer.newLine();
                writer.write("directPlot=" + parameters.isDirectPlot());
                writer.newLine();
                writer.write("genome1Chromosomes=" + joinValues(parameters.getGenome1Chromosomes()));
                writer.newLine();
                writer.write("genome2Chromosomes=" + joinValues(parameters.getGenome2Chromosomes()));
                writer.newLine();
            }

            if (genome1Species != null) {
                File genomeFile1 = genome1Species.getGenomeFile();
                File annotationFile1 = genome1Species.getAnnotationFile();
                writer.write("genomeFile1=" + safe(genomeFile1 != null ? genomeFile1.getAbsolutePath() : ""));
                writer.newLine();
                writer.write("annotationFile1=" + safe(annotationFile1 != null ? annotationFile1.getAbsolutePath() : ""));
                writer.newLine();
            }
            if (genome2Species != null) {
                File genomeFile2 = genome2Species.getGenomeFile();
                File annotationFile2 = genome2Species.getAnnotationFile();
                writer.write("genomeFile2=" + safe(genomeFile2 != null ? genomeFile2.getAbsolutePath() : ""));
                writer.newLine();
                writer.write("annotationFile2=" + safe(annotationFile2 != null ? annotationFile2.getAbsolutePath() : ""));
                writer.newLine();
            }

            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                writer.write("error=" + safe(errorMessage));
                writer.newLine();
            }

            writer.write("[generatedFiles]");
            writer.newLine();
            File[] generatedFiles = outputDir.listFiles();
            if (generatedFiles != null) {
                Arrays.sort(generatedFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File generatedFile : generatedFiles) {
                    writer.write(generatedFile.getName());
                    writer.newLine();
                }
            }

            if (result != null) {
                writer.write("[collinearityFiles]");
                writer.newLine();
                for (File collinearityFile : result.getCollinearityFiles()) {
                    writer.write(collinearityFile.getAbsolutePath());
                    writer.newLine();
                }

                writer.write("[htmlOutputs]");
                writer.newLine();
                for (File htmlOutput : result.getHtmlOutputs()) {
                    writer.write(htmlOutput.getAbsolutePath());
                    writer.newLine();
                }
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to write Genome Compare metadata", ex);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String joinValues(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    private static void report(ProgressListener progressListener, String message) {
        if (progressListener != null) {
            progressListener.onProgress(message);
        }
    }

    private static final class FilteredSpeciesInputs {
        private final File genomeFile;
        private final File annotationFile;
        private final Set<String> keptChromosomes;

        private FilteredSpeciesInputs(File genomeFile, File annotationFile, Set<String> keptChromosomes) {
            this.genomeFile = genomeFile;
            this.annotationFile = annotationFile;
            this.keptChromosomes = new LinkedHashSet<>(keptChromosomes);
        }
    }
}
