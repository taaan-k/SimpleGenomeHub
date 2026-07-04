package simplegenomehub.util.fileio;

import JJpolt2.Example.DualSyntenyPlotter;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prepares and launches TBtools Dual Synteny Plot from Genome Compare outputs.
 */
public final class DualSyntenyPlotLauncher {

    private static final Logger logger = Logger.getLogger(DualSyntenyPlotLauncher.class.getName());
    private static final int MIN_PLOT_WIDTH = 4200;
    private static final int PLOT_WIDTH_PER_CHROMOSOME = 180;
    private static final int DEFAULT_PLOT_HEIGHT = 720;
    private static final String SELF_SUFFIX = ".1";
    private static final String HIGHLIGHT_GENE_LIST_FILE_NAME = "HighlightGeneList.txt";
    private static final String SANITIZED_HIGHLIGHT_GENE_LIST_FILE_NAME = "dual_synteny.highlight.ids.txt";
    private static final String TBTOOLS_CTL_SUFFIX = ".dualSynteny.ctl";

    private DualSyntenyPlotLauncher() {
    }

    public static void launch(GenomeCompareService.Result result) throws Exception {
        launch(result, false);
    }

    public static void launch(GenomeCompareService.Result result, boolean reorderChromosomes) throws Exception {
        if (result == null) {
            throw new IllegalArgumentException("Genome Compare result is required.");
        }

        File sourceCollinearityFile = result.getPrimaryCollinearityFile();
        if (sourceCollinearityFile == null || !sourceCollinearityFile.isFile()) {
            throw new IOException("Primary .collinearity file is missing.");
        }

        File sourceGffFile = findSimplifiedGff(result.getOutputDir());
        if (sourceGffFile == null || !sourceGffFile.isFile()) {
            throw new IOException("Simplified .gff file is missing.");
        }

        RunMetadata metadata = readRunMetadata(result.getOutputDir());
        String prefix1 = metadata.prefix1 != null ? metadata.prefix1 : result.getPrefix1();
        String prefix2 = metadata.prefix2 != null ? metadata.prefix2 : result.getPrefix2();
        boolean selfComparison = metadata.nativeSelfComparisonMode != null
            ? metadata.nativeSelfComparisonMode
            : result.isNativeSelfComparisonMode();

        VisualizationInputs inputs = prepareVisualizationInputs(result.getOutputDir(), metadata, prefix1,
            prefix2, selfComparison, sourceGffFile, sourceCollinearityFile, reorderChromosomes);
        launch(inputs);
    }

    public static void launchFromOutputDirectory(File outputDir) throws Exception {
        launchFromOutputDirectory(outputDir, false);
    }

    public static void launchFromOutputDirectory(File outputDir, boolean reorderChromosomes) throws Exception {
        launch(prepareVisualizationInputsFromOutputDirectory(outputDir, reorderChromosomes));
    }

    static File prepareCtlFileFromOutputDirectory(File outputDir, boolean reorderChromosomes) throws IOException {
        return prepareVisualizationInputsFromOutputDirectory(outputDir, reorderChromosomes).ctlFile;
    }

    private static VisualizationInputs prepareVisualizationInputsFromOutputDirectory(File outputDir,
                                                                                     boolean reorderChromosomes)
        throws IOException {
        if (outputDir == null || !outputDir.isDirectory()) {
            throw new IOException("Genome Compare output directory is missing.");
        }

        RunMetadata metadata = readRunMetadata(outputDir);
        File sourceCollinearityFile = findPrimaryCollinearityFile(outputDir, metadata);
        if (sourceCollinearityFile == null || !sourceCollinearityFile.isFile()) {
            throw new IOException("Primary .collinearity file is missing.");
        }

        File sourceGffFile = findSimplifiedGff(outputDir);
        if (sourceGffFile == null || !sourceGffFile.isFile()) {
            throw new IOException("Simplified .gff file is missing.");
        }

        boolean selfComparison = metadata.nativeSelfComparisonMode != null
            ? metadata.nativeSelfComparisonMode
            : isSelfComparison(metadata.prefix1, metadata.prefix2);
        return prepareVisualizationInputs(outputDir, metadata, metadata.prefix1, metadata.prefix2,
            selfComparison,
            sourceGffFile, sourceCollinearityFile, reorderChromosomes);
    }

    private static void launch(VisualizationInputs inputs) throws Exception {
        File highlightFile = sanitizeHighlightGeneListFile(inputs);
        Exception highlightLaunchFailure = null;
        if (highlightFile != null) {
            try {
                launchPlotter(inputs, highlightFile);
                return;
            } catch (Exception ex) {
                highlightLaunchFailure = ex;
                logger.log(Level.WARNING,
                    "Failed to launch Dual Synteny Plot with highlight gene list. Retrying without highlights.", ex);
            }
        }
        try {
            launchPlotter(inputs, null);
        } catch (Exception ex) {
            if (highlightLaunchFailure != null) {
                ex.addSuppressed(highlightLaunchFailure);
            }
            throw ex;
        }
    }

    private static void launchPlotter(VisualizationInputs inputs, File highlightFile) throws Exception {
        DualSyntenyPlotter plotter = createConfiguredPlotter(inputs, highlightFile);
        List<java.awt.Window> previousWindows = DualSyntenyPreviewExporter.captureWindows();
        try {
            plotter.plot();
            Thread previewExportThread = new Thread(() -> {
                try {
                    DualSyntenyPreviewExporter.exportPreview(inputs.outputDir, previousWindows);
                } catch (IOException previewEx) {
                    logger.log(Level.WARNING,
                        "Failed to export Dual Synteny preview image for " + inputs.outputDir.getAbsolutePath(),
                        previewEx
                    );
                }
            }, "dual-synteny-preview-export");
            previewExportThread.setDaemon(true);
            previewExportThread.start();
        } catch (Exception ex) {
            String message = buildLaunchFailureMessage(inputs, highlightFile, ex);
            logger.log(Level.SEVERE, message, ex);
            throw new IOException(message, ex);
        }
    }

    static DualSyntenyPlotter createPlotterFromOutputDirectory(File outputDir, boolean reorderChromosomes)
        throws IOException {
        VisualizationInputs inputs = prepareVisualizationInputsFromOutputDirectory(outputDir, reorderChromosomes);
        File highlightFile = sanitizeHighlightGeneListFile(inputs);
        return createConfiguredPlotter(inputs, highlightFile);
    }

    private static DualSyntenyPlotter createConfiguredPlotter(VisualizationInputs inputs, File highlightFile) {
        DualSyntenyPlotter plotter = new DualSyntenyPlotter();
        configurePlotter(plotter, inputs, highlightFile);
        return plotter;
    }

    static void configurePlotter(DualSyntenyPlotter plotter, VisualizationInputs inputs, File highlightFile) {
        if (plotter == null) {
            throw new IllegalArgumentException("Dual Synteny plotter is required.");
        }
        if (inputs == null) {
            throw new IllegalArgumentException("Visualization inputs are required.");
        }

        configurePlotter(plotter, inputs.ctlFile, inputs.gffFile, inputs.collinearityFile, highlightFile,
            inputs.selfComparison);
    }

    static void configurePlotter(DualSyntenyPlotter plotter, File ctlFile, File gffFile, File collinearityFile,
                                 File highlightFile, boolean selfComparison) {
        if (plotter == null) {
            throw new IllegalArgumentException("Dual Synteny plotter is required.");
        }
        if (ctlFile == null || gffFile == null || collinearityFile == null) {
            throw new IllegalArgumentException("Plotter input files are required.");
        }

        plotter.setCtlFile(ctlFile.getAbsolutePath());
        plotter.setInSimplifiedGff(gffFile.getAbsolutePath());
        plotter.setCollinerFile(collinearityFile.getAbsolutePath());
        if (highlightFile != null && highlightFile.isFile()) {
            plotter.setGeneColorFile(highlightFile.getAbsolutePath());
        }
        plotter.setFilterSameGenomeUp(selfComparison);
        plotter.setFilterSameGenomeDown(selfComparison);
        plotter.setMinGenesInOneBlock(0);
    }

    private static File sanitizeHighlightGeneListFile(VisualizationInputs inputs) throws IOException {
        if (inputs == null || inputs.outputDir == null || !inputs.outputDir.isDirectory()
            || inputs.highlightGeneListFile == null || !inputs.highlightGeneListFile.isFile()
            || inputs.gffFile == null || !inputs.gffFile.isFile()) {
            return null;
        }

        Set<String> availableGeneIds = readGeneIdsFromSimplifiedGff(inputs.gffFile);
        if (availableGeneIds.isEmpty()) {
            return null;
        }

        LinkedHashSet<String> sanitizedIds = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(inputs.highlightGeneListFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && availableGeneIds.contains(trimmed)) {
                    sanitizedIds.add(trimmed);
                }
            }
        }

        if (sanitizedIds.isEmpty()) {
            return null;
        }

        File sanitizedFile = new File(inputs.outputDir, SANITIZED_HIGHLIGHT_GENE_LIST_FILE_NAME);
        try (BufferedWriter writer = Files.newBufferedWriter(sanitizedFile.toPath(), StandardCharsets.UTF_8)) {
            for (String geneId : sanitizedIds) {
                writer.write(geneId);
                writer.newLine();
            }
        }
        return sanitizedFile;
    }

    private static Set<String> readGeneIdsFromSimplifiedGff(File gffFile) throws IOException {
        LinkedHashSet<String> geneIds = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(gffFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] parts = trimmed.split("\t");
                if (parts.length >= 2) {
                    String geneId = parts[1].trim();
                    if (!geneId.isEmpty()) {
                        geneIds.add(geneId);
                    }
                }
            }
        }
        return geneIds;
    }

    private static String buildLaunchFailureMessage(VisualizationInputs inputs, File highlightFile, Exception ex) {
        String exceptionType = ex == null ? "UnknownException" : ex.getClass().getSimpleName();
        String detail = ex == null ? "" : ex.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = "(no detail message)";
        }

        return "Dual Synteny Plot launch failed [" + exceptionType + "]: " + detail +
            "\nOutput directory: " + describeFile(inputs != null ? inputs.outputDir : null) +
            "\nCTL file: " + describeFile(inputs != null ? inputs.ctlFile : null) +
            "\nSimplified GFF: " + describeFile(inputs != null ? inputs.gffFile : null) +
            "\nCollinearity file: " + describeFile(inputs != null ? inputs.collinearityFile : null) +
            "\nHighlight file: " + describeFile(highlightFile != null ? highlightFile
                : inputs != null ? inputs.highlightGeneListFile : null);
    }

    private static String describeFile(File file) {
        if (file == null) {
            return "(null)";
        }
        return file.getAbsolutePath() + (file.exists() ? "" : " (missing)");
    }

    private static VisualizationInputs prepareVisualizationInputs(File outputDir,
                                                                  RunMetadata metadata,
                                                                  String prefix1,
                                                                  String prefix2,
                                                                  boolean selfComparison,
                                                                  File sourceGffFile,
                                                                  File sourceCollinearityFile,
                                                                  boolean reorderChromosomes) throws IOException {
        File preferredCtlTemplate = findTbtoolsCtlFile(outputDir);
        if (!selfComparison) {
            DualSyntenyChromosomeOrderBuilder.OrderResult orderResult = reorderChromosomes
                ? DualSyntenyChromosomeOrderBuilder.loadOrBuild(outputDir, sourceGffFile)
                : null;
            List<String> upperBaseChromosomes = resolveChromosomeOrder(preferredCtlTemplate,
                metadata.genome1Chromosomes, metadata.annotationFile1, sourceGffFile, true);
            List<String> lowerBaseChromosomes = resolveChromosomeOrder(preferredCtlTemplate,
                metadata.genome2Chromosomes, metadata.annotationFile2, sourceGffFile, false);
            List<String> upperChromosomes = orderResult != null
                ? constrainChromosomeOrder(orderResult.getUpperChromosomes(), upperBaseChromosomes)
                : upperBaseChromosomes;
            List<String> lowerChromosomes = orderResult != null
                ? constrainChromosomeOrder(orderResult.getLowerChromosomes(), lowerBaseChromosomes)
                : lowerBaseChromosomes;
            File ctlFile = writeCtlFile(outputDir, new LinkedHashSet<>(upperChromosomes),
                new LinkedHashSet<>(lowerChromosomes));
            return new VisualizationInputs(
                outputDir,
                sourceGffFile,
                sourceCollinearityFile,
                ctlFile,
                resolveHighlightGeneListFile(outputDir),
                false
            );
        }

        DualSyntenyChromosomeOrderBuilder.OrderResult orderResult = reorderChromosomes
            ? DualSyntenyChromosomeOrderBuilder.loadOrBuild(outputDir, sourceGffFile)
            : null;
        List<String> originalChromosomes = new ArrayList<>();
        Map<String, String> chromosomeRenameMap = new LinkedHashMap<>();
        Map<String, String> geneRenameMap = new LinkedHashMap<>();

        File visualGffFile = new File(outputDir, "dual_synteny.visual.gff");
        try (BufferedReader reader = Files.newBufferedReader(sourceGffFile.toPath(), StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(visualGffFile.toPath(), StandardCharsets.UTF_8)) {

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

                String chromosome = parts[0];
                String geneId = parts[1];
                if (!chromosomeRenameMap.containsKey(chromosome)) {
                    chromosomeRenameMap.put(chromosome, chromosome + SELF_SUFFIX);
                    originalChromosomes.add(chromosome);
                }
                geneRenameMap.put(geneId, geneId + SELF_SUFFIX);

                writer.write(trimmed);
                writer.newLine();

                String[] duplicatedParts = parts.clone();
                duplicatedParts[0] = chromosomeRenameMap.get(chromosome);
                duplicatedParts[1] = geneRenameMap.get(geneId);
                writer.write(String.join("\t", duplicatedParts));
                writer.newLine();
            }
        }

        File visualCollinearityFile = new File(outputDir, "dual_synteny.visual.collinearity");
        try (BufferedReader reader = Files.newBufferedReader(sourceCollinearityFile.toPath(), StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(visualCollinearityFile.toPath(), StandardCharsets.UTF_8)) {

            String line;
            while ((line = reader.readLine()) != null) {
                String updatedLine = line;

                if (line.startsWith("## Alignment ")) {
                    updatedLine = renameAlignmentChromosomes(line, chromosomeRenameMap);
                } else if (line.matches("^\\s*\\d+-\\s*\\d+:\\t.*")) {
                    updatedLine = renameAlignmentGenes(line, geneRenameMap);
                }

                writer.write(updatedLine);
                writer.newLine();
            }
        }

        List<String> upperChromosomes = orderResult != null
            ? constrainChromosomeOrder(orderResult.getUpperChromosomes(),
                resolveChromosomeOrder(preferredCtlTemplate, metadata.genome1Chromosomes,
                    metadata.annotationFile1, sourceGffFile, true))
            : resolveChromosomeOrder(preferredCtlTemplate, metadata.genome1Chromosomes,
                metadata.annotationFile1, sourceGffFile, true);
        List<String> lowerChromosomes = orderResult != null
            ? constrainChromosomeOrder(orderResult.getLowerChromosomes(),
                resolveChromosomeOrder(preferredCtlTemplate, metadata.genome2Chromosomes,
                    metadata.annotationFile2, sourceGffFile, false))
            : resolveChromosomeOrder(preferredCtlTemplate, metadata.genome2Chromosomes,
                metadata.annotationFile2, sourceGffFile, false);

        List<String> renamedChromosomes = new ArrayList<>();
        for (String chromosome : lowerChromosomes) {
            String renamed = chromosomeRenameMap.get(chromosome);
            if (renamed != null) {
                renamedChromosomes.add(renamed);
            }
        }
        if (renamedChromosomes.isEmpty()) {
            for (String chromosome : originalChromosomes) {
                String renamed = chromosomeRenameMap.get(chromosome);
                if (renamed != null) {
                    renamedChromosomes.add(renamed);
                }
            }
        }

        File ctlFile = writeCtlFile(outputDir, new LinkedHashSet<>(upperChromosomes),
            new LinkedHashSet<>(renamedChromosomes));
        return new VisualizationInputs(
            outputDir,
            visualGffFile,
            visualCollinearityFile,
            ctlFile,
            resolveHighlightGeneListFile(outputDir),
            true
        );
    }

    private static File resolveHighlightGeneListFile(File outputDir) {
        if (outputDir == null || !outputDir.isDirectory()) {
            return null;
        }
        File highlightFile = new File(outputDir, HIGHLIGHT_GENE_LIST_FILE_NAME);
        return highlightFile.isFile() ? highlightFile : null;
    }

    private static String renameAlignmentChromosomes(String line, Map<String, String> chromosomeRenameMap) {
        int lastSpace = line.lastIndexOf(' ');
        if (lastSpace < 0 || lastSpace >= line.length() - 1) {
            return line;
        }

        String orientation = line.substring(lastSpace + 1);
        String beforeOrientation = line.substring(0, lastSpace);
        int secondLastSpace = beforeOrientation.lastIndexOf(' ');
        if (secondLastSpace < 0 || secondLastSpace >= beforeOrientation.length() - 1) {
            return line;
        }

        String pairToken = beforeOrientation.substring(secondLastSpace + 1);
        String[] chromosomes = pairToken.split("&", 2);
        if (chromosomes.length != 2) {
            return line;
        }

        String renamedSecond = chromosomeRenameMap.getOrDefault(chromosomes[1], chromosomes[1] + SELF_SUFFIX);
        return beforeOrientation.substring(0, secondLastSpace + 1) +
            chromosomes[0] + "&" + renamedSecond + " " + orientation;
    }

    private static String renameAlignmentGenes(String line, Map<String, String> geneRenameMap) {
        String[] parts = line.split("\t");
        if (parts.length < 3) {
            return line;
        }

        String renamedGene = geneRenameMap.get(parts[2]);
        if (renamedGene == null) {
            return line;
        }

        parts[2] = renamedGene;
        return String.join("\t", parts);
    }

    private static boolean isSelfComparison(GenomeCompareService.Result result) {
        if (result == null) {
            return false;
        }

        return isSelfComparison(result.getPrefix1(), result.getPrefix2());
    }

    private static boolean isSelfComparison(String prefix1, String prefix2) {
        if (prefix1 == null || prefix2 == null) {
            return false;
        }

        String normalized1 = normalizePrefix(prefix1);
        String normalized2 = normalizePrefix(prefix2);
        return normalized1.equals(normalized2);
    }

    private static String normalizePrefix(String prefix) {
        return prefix.replaceFirst("_[0-9]+$", "");
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

    private static File writeCtlFile(File outputDir, Set<String> upperChromosomes,
                                     Set<String> lowerChromosomes) throws IOException {
        File ctlFile = new File(outputDir, "dual_synteny.ctl");
        int chromosomeCount = Math.max(upperChromosomes.size(), lowerChromosomes.size());
        int plotWidth = Math.max(MIN_PLOT_WIDTH, chromosomeCount * PLOT_WIDTH_PER_CHROMOSOME);

        try (BufferedWriter writer = Files.newBufferedWriter(ctlFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write(String.valueOf(plotWidth));
            writer.newLine();
            writer.write(String.valueOf(DEFAULT_PLOT_HEIGHT));
            writer.newLine();
            writer.write(String.join(",", upperChromosomes));
            writer.newLine();
            writer.write(String.join(",", lowerChromosomes));
            writer.newLine();
        }

        return ctlFile;
    }

    private static File findSimplifiedGff(File outputDir) {
        if (outputDir == null || !outputDir.isDirectory()) {
            return null;
        }

        File[] gffFiles = outputDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(".gff"));
        if (gffFiles == null || gffFiles.length == 0) {
            return null;
        }

        File preferred = findPreferredSourceFile(gffFiles, "dual_synteny.visual.gff");
        return preferred != null ? preferred : gffFiles[0];
    }

    private static File findPrimaryCollinearityFile(File outputDir, RunMetadata metadata) {
        File metadataFile = metadata.primaryCollinearityFile;
        if (metadataFile != null && metadataFile.isFile()) {
            return metadataFile;
        }

        File[] collinearityFiles = outputDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(".collinearity"));
        if (collinearityFiles == null || collinearityFiles.length == 0) {
            return null;
        }

        File preferred = findPreferredSourceFile(collinearityFiles, "dual_synteny.visual.collinearity");
        return preferred != null ? preferred : collinearityFiles[0];
    }

    private static File findPreferredSourceFile(File[] files, String excludedFileName) {
        for (File file : files) {
            if (file != null && !excludedFileName.equalsIgnoreCase(file.getName())) {
                return file;
            }
        }
        return null;
    }

    private static RunMetadata readRunMetadata(File outputDir) throws IOException {
        File metadataFile = new File(outputDir, "run-metadata.txt");
        if (!metadataFile.isFile()) {
            return new RunMetadata(null, null, null, null, null, null, null, null);
        }

        String prefix1 = null;
        String prefix2 = null;
        Boolean nativeSelfComparisonMode = null;
        File annotationFile1 = null;
        File annotationFile2 = null;
        File primaryCollinearityFile = null;
        List<String> genome1Chromosomes = null;
        List<String> genome2Chromosomes = null;
        boolean inCollinearitySection = false;

        try (BufferedReader reader = Files.newBufferedReader(metadataFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    inCollinearitySection = "[collinearityFiles]".equalsIgnoreCase(trimmed);
                    continue;
                }
                if (inCollinearitySection && primaryCollinearityFile == null) {
                    File candidate = new File(trimmed);
                    if (candidate.isFile()) {
                        primaryCollinearityFile = candidate;
                    }
                    continue;
                }
                if (trimmed.startsWith("prefix1=")) {
                    prefix1 = trimmed.substring("prefix1=".length()).trim();
                    continue;
                }
                if (trimmed.startsWith("annotationFile1=")) {
                    annotationFile1 = parseMetadataFile(trimmed.substring("annotationFile1=".length()));
                    continue;
                }
                if (trimmed.startsWith("genome1Chromosomes=")) {
                    genome1Chromosomes = parseValueList(trimmed.substring("genome1Chromosomes=".length()));
                    continue;
                }
                if (trimmed.startsWith("prefix2=")) {
                    prefix2 = trimmed.substring("prefix2=".length()).trim();
                    continue;
                }
                if (trimmed.startsWith("nativeSelfComparisonMode=")) {
                    nativeSelfComparisonMode = Boolean.parseBoolean(
                        trimmed.substring("nativeSelfComparisonMode=".length()).trim());
                    continue;
                }
                if (trimmed.startsWith("annotationFile2=")) {
                    annotationFile2 = parseMetadataFile(trimmed.substring("annotationFile2=".length()));
                    continue;
                }
                if (trimmed.startsWith("genome2Chromosomes=")) {
                    genome2Chromosomes = parseValueList(trimmed.substring("genome2Chromosomes=".length()));
                }
            }
        }

        return new RunMetadata(emptyToNull(prefix1), emptyToNull(prefix2), annotationFile1,
            annotationFile2, primaryCollinearityFile, nativeSelfComparisonMode,
            genome1Chromosomes, genome2Chromosomes);
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

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private static List<String> resolveChromosomeOrder(File preferredCtlFile,
                                                       List<String> selectedChromosomes,
                                                       File annotationSourceFile,
                                                       File fallbackSourceFile,
                                                       boolean upperGenome)
        throws IOException {
        Set<String> chromosomes = new LinkedHashSet<>();
        if (selectedChromosomes != null && !selectedChromosomes.isEmpty()
            && fallbackSourceFile != null && fallbackSourceFile.isFile()) {
            ChromosomeNamespace namespace = readChromosomeNamespace(fallbackSourceFile);
            chromosomes.addAll(normalizeChromosomeOrder(selectedChromosomes,
                upperGenome ? namespace.upperByRawName : namespace.lowerByRawName));
        }
        if (chromosomes.isEmpty() && preferredCtlFile != null && preferredCtlFile.isFile()) {
            chromosomes.addAll(readChromosomeOrderFromCtl(preferredCtlFile, upperGenome));
        }
        if (chromosomes.isEmpty() && annotationSourceFile != null && annotationSourceFile.isFile()) {
            chromosomes.addAll(readChromosomeOrder(annotationSourceFile));
        }
        if (chromosomes.isEmpty() && fallbackSourceFile != null && fallbackSourceFile.isFile()) {
            chromosomes.addAll(readChromosomeOrder(fallbackSourceFile));
        }
        return new ArrayList<>(chromosomes);
    }

    private static List<String> constrainChromosomeOrder(List<String> preferredOrder, List<String> allowedOrder) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (allowedOrder == null || allowedOrder.isEmpty()) {
            return new ArrayList<>(merged);
        }
        Set<String> allowed = new LinkedHashSet<>(allowedOrder);
        if (preferredOrder != null) {
            for (String chromosome : preferredOrder) {
                if (allowed.contains(chromosome)) {
                    merged.add(chromosome);
                }
            }
        }
        for (String chromosome : allowedOrder) {
            merged.add(chromosome);
        }
        return new ArrayList<>(merged);
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

    private static Set<String> readChromosomeOrder(File chromosomeSourceFile) throws IOException {
        Set<String> chromosomes = new LinkedHashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(chromosomeSourceFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] parts = trimmed.split("\t");
                if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                    chromosomes.add(parts[0].trim());
                }
            }
        }

        return chromosomes;
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

    private static ChromosomeNamespace readChromosomeNamespace(File simplifiedGffFile) throws IOException {
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
                if (!upperByRawName.containsKey(rawName)) {
                    upperByRawName.put(rawName, chromosome);
                } else if (!lowerByRawName.containsKey(rawName)
                    && !chromosome.equals(upperByRawName.get(rawName))) {
                    lowerByRawName.put(rawName, chromosome);
                }
            }
        }

        for (Map.Entry<String, String> entry : upperByRawName.entrySet()) {
            lowerByRawName.putIfAbsent(entry.getKey(), entry.getValue());
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

    private static final class VisualizationInputs {
        private final File outputDir;
        private final File gffFile;
        private final File collinearityFile;
        private final File ctlFile;
        private final File highlightGeneListFile;
        private final boolean selfComparison;

        private VisualizationInputs(File outputDir, File gffFile, File collinearityFile, File ctlFile,
                                    File highlightGeneListFile, boolean selfComparison) {
            this.outputDir = outputDir;
            this.gffFile = gffFile;
            this.collinearityFile = collinearityFile;
            this.ctlFile = ctlFile;
            this.highlightGeneListFile = highlightGeneListFile;
            this.selfComparison = selfComparison;
        }
    }

    private static final class RunMetadata {
        private final String prefix1;
        private final String prefix2;
        private final File annotationFile1;
        private final File annotationFile2;
        private final File primaryCollinearityFile;
        private final Boolean nativeSelfComparisonMode;
        private final List<String> genome1Chromosomes;
        private final List<String> genome2Chromosomes;

        private RunMetadata(String prefix1, String prefix2, File annotationFile1,
                            File annotationFile2, File primaryCollinearityFile,
                            Boolean nativeSelfComparisonMode,
                            List<String> genome1Chromosomes, List<String> genome2Chromosomes) {
            this.prefix1 = prefix1;
            this.prefix2 = prefix2;
            this.annotationFile1 = annotationFile1;
            this.annotationFile2 = annotationFile2;
            this.primaryCollinearityFile = primaryCollinearityFile;
            this.nativeSelfComparisonMode = nativeSelfComparisonMode;
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
