package simplegenomehub.util.fileio;

import simplegenomehub.model.SpeciesInfo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runs link-defined One Step MCScanX jobs for Multiple Synteny.
 */
public final class MultipleSyntenyService {

    public static final String DEFAULT_LINK_COLOR = "220,220,220";
    public static final double DEFAULT_LINK_ALPHA = 0.85d;
    public static final String DEFAULT_HIGHLIGHT_COLOR = "252,141,98";
    public static final double DEFAULT_HIGHLIGHT_ALPHA = 0.95d;
    public static final int DEFAULT_HIGHLIGHT_Z_ORDER = 10;
    private static final int DEFAULT_LINK_OFFSET = 50;
    private static final int DEFAULT_LINK_BEND = 50;
    private static final int DEFAULT_GAP_LENGTH = 10;
    private static final int DEFAULT_CANVAS_WIDTH = 1500;
    private static final int DEFAULT_CANVAS_HEIGHT = 1000;
    private static final int DEFAULT_EQUAL_LENGTH_WIDTH = 600;
    private static final String MULTIPLE_COMPARE_ROOT_NAME = "MultipleCompare";
    private static final String PAIR_RESULTS_DIR_NAME = "PairResults";
    private static final String RUN_METADATA_FILE_NAME = "run-metadata.txt";
    private static final String HIGHLIGHT_GENE_LIST_FILE_NAME = "HighlightGeneList.txt";
    private static final String CHR_LAYOUT_SUFFIX = ".multiplesynteny.chrlayout.tab.xls";

    public interface ProgressListener {
        void onProgress(String message);
    }

    public static final class GenomeSelection {
        private final int slotNumber;
        private final SpeciesInfo species;
        private final List<String> chromosomes;

        public GenomeSelection(int slotNumber, SpeciesInfo species, List<String> chromosomes) {
            this.slotNumber = slotNumber;
            this.species = species;
            this.chromosomes = chromosomes == null ? new ArrayList<>() : new ArrayList<>(chromosomes);
        }

        public int getSlotNumber() {
            return slotNumber;
        }

        public SpeciesInfo getSpecies() {
            return species;
        }

        public List<String> getChromosomes() {
            return new ArrayList<>(chromosomes);
        }
    }

    public static final class ChromosomeLayout {
        private final int chromosomeOrder;
        private final String chromosomeName;
        private final int displayStart;
        private final int displayEnd;
        private final long bpLength;

        public ChromosomeLayout(int chromosomeOrder, String chromosomeName, int displayStart,
                                int displayEnd, long bpLength) {
            this.chromosomeOrder = chromosomeOrder;
            this.chromosomeName = chromosomeName;
            this.displayStart = displayStart;
            this.displayEnd = displayEnd;
            this.bpLength = bpLength;
        }

        public int getChromosomeOrder() {
            return chromosomeOrder;
        }

        public String getChromosomeName() {
            return chromosomeName;
        }

        public int getDisplayStart() {
            return displayStart;
        }

        public int getDisplayEnd() {
            return displayEnd;
        }

        public long getBpLength() {
            return bpLength;
        }
    }

    public static final class GenomeLayout {
        private final int slotNumber;
        private final String genomeId;
        private final String displayName;
        private final int zOrder;
        private final double leftBottomX;
        private final double leftBottomY;
        private final int rectWidth;
        private final int rectHeight;
        private final int rotationDeg;
        private final List<ChromosomeLayout> chromosomes;

        public GenomeLayout(int slotNumber, String genomeId, String displayName, int zOrder,
                            double leftBottomX, double leftBottomY, int rectWidth,
                            int rectHeight, int rotationDeg, List<ChromosomeLayout> chromosomes) {
            this.slotNumber = slotNumber;
            this.genomeId = genomeId;
            this.displayName = displayName;
            this.zOrder = zOrder;
            this.leftBottomX = leftBottomX;
            this.leftBottomY = leftBottomY;
            this.rectWidth = rectWidth;
            this.rectHeight = rectHeight;
            this.rotationDeg = rotationDeg;
            this.chromosomes = chromosomes == null ? new ArrayList<>() : new ArrayList<>(chromosomes);
        }

        public int getSlotNumber() {
            return slotNumber;
        }

        public String getGenomeId() {
            return genomeId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getZOrder() {
            return zOrder;
        }

        public double getLeftBottomX() {
            return leftBottomX;
        }

        public double getLeftBottomY() {
            return leftBottomY;
        }

        public int getRectWidth() {
            return rectWidth;
        }

        public int getRectHeight() {
            return rectHeight;
        }

        public int getRotationDeg() {
            return rotationDeg;
        }

        public List<ChromosomeLayout> getChromosomes() {
            return new ArrayList<>(chromosomes);
        }
    }

    public static final class LinkLayout {
        private final int linkNumber;
        private final int leftSlotNumber;
        private final int rightSlotNumber;
        private final String linkType;
        private final String edge1;
        private final String edge2;
        private final int bendValue;
        private final String bulgeDir;

        public LinkLayout(int linkNumber, int leftSlotNumber, int rightSlotNumber,
                          String linkType, String edge1, String edge2,
                          int bendValue, String bulgeDir) {
            this.linkNumber = linkNumber;
            this.leftSlotNumber = leftSlotNumber;
            this.rightSlotNumber = rightSlotNumber;
            this.linkType = linkType;
            this.edge1 = edge1;
            this.edge2 = edge2;
            this.bendValue = bendValue;
            this.bulgeDir = bulgeDir;
        }

        public int getLinkNumber() {
            return linkNumber;
        }

        public int getLeftSlotNumber() {
            return leftSlotNumber;
        }

        public int getRightSlotNumber() {
            return rightSlotNumber;
        }

        public String getLinkType() {
            return linkType;
        }

        public String getEdge1() {
            return edge1;
        }

        public String getEdge2() {
            return edge2;
        }

        public int getBendValue() {
            return bendValue;
        }

        public String getBulgeDir() {
            return bulgeDir;
        }
    }

    public static final class GlobalSettings {
        private final int linkOffset;
        private final int linkBend;
        private final int gapLength;
        private final int canvasWidth;
        private final int canvasHeight;
        private final boolean equalGenomeLength;
        private final int equalGenomeLengthWidth;

        public GlobalSettings(int linkOffset, int linkBend, int gapLength,
                              int canvasWidth, int canvasHeight,
                              boolean equalGenomeLength, int equalGenomeLengthWidth) {
            this.linkOffset = linkOffset;
            this.linkBend = linkBend;
            this.gapLength = gapLength;
            this.canvasWidth = canvasWidth;
            this.canvasHeight = canvasHeight;
            this.equalGenomeLength = equalGenomeLength;
            this.equalGenomeLengthWidth = equalGenomeLengthWidth;
        }

        public int getLinkOffset() {
            return linkOffset;
        }

        public int getLinkBend() {
            return linkBend;
        }

        public int getGapLength() {
            return gapLength;
        }

        public int getCanvasWidth() {
            return canvasWidth;
        }

        public int getCanvasHeight() {
            return canvasHeight;
        }

        public boolean isEqualGenomeLength() {
            return equalGenomeLength;
        }

        public int getEqualGenomeLengthWidth() {
            return equalGenomeLengthWidth;
        }
    }

    public static final class RunRequest {
        private final List<GenomeSelection> genomeSelections;
        private final List<LinkRun> linkRuns;
        private final List<GenomeLayout> genomeLayouts;
        private final List<LinkLayout> linkLayouts;
        private final GlobalSettings globalSettings;
        private final List<String> highlightGeneIds;
        private final Map<Integer, List<String>> highlightGeneIdsBySlot;
        private final List<HighlightRegion> highlightRegions;
        private final SpeciesInfo batchOutputSpecies;

        public RunRequest(List<GenomeSelection> genomeSelections, List<LinkRun> linkRuns,
                          List<GenomeLayout> genomeLayouts, List<LinkLayout> linkLayouts,
                          GlobalSettings globalSettings, List<String> highlightGeneIds,
                          SpeciesInfo batchOutputSpecies) {
            this(
                genomeSelections,
                linkRuns,
                genomeLayouts,
                linkLayouts,
                globalSettings,
                highlightGeneIds,
                new LinkedHashMap<>(),
                new ArrayList<>(),
                batchOutputSpecies
            );
        }

        public RunRequest(List<GenomeSelection> genomeSelections, List<LinkRun> linkRuns,
                          List<GenomeLayout> genomeLayouts, List<LinkLayout> linkLayouts,
                          GlobalSettings globalSettings, List<String> highlightGeneIds,
                          Map<Integer, List<String>> highlightGeneIdsBySlot,
                          List<HighlightRegion> highlightRegions, SpeciesInfo batchOutputSpecies) {
            this.genomeSelections = genomeSelections == null ? new ArrayList<>() : new ArrayList<>(genomeSelections);
            this.linkRuns = linkRuns == null ? new ArrayList<>() : new ArrayList<>(linkRuns);
            this.genomeLayouts = genomeLayouts == null ? new ArrayList<>() : new ArrayList<>(genomeLayouts);
            this.linkLayouts = linkLayouts == null ? new ArrayList<>() : new ArrayList<>(linkLayouts);
            this.globalSettings = globalSettings;
            this.highlightGeneIds = highlightGeneIds == null ? new ArrayList<>() : new ArrayList<>(highlightGeneIds);
            this.highlightGeneIdsBySlot = copyHighlightGeneIdsBySlot(highlightGeneIdsBySlot);
            this.highlightRegions = highlightRegions == null ? new ArrayList<>() : new ArrayList<>(highlightRegions);
            this.batchOutputSpecies = batchOutputSpecies;
        }

        public List<GenomeSelection> getGenomeSelections() {
            return new ArrayList<>(genomeSelections);
        }

        public List<LinkRun> getLinkRuns() {
            return new ArrayList<>(linkRuns);
        }

        public List<GenomeLayout> getGenomeLayouts() {
            return new ArrayList<>(genomeLayouts);
        }

        public List<LinkLayout> getLinkLayouts() {
            return new ArrayList<>(linkLayouts);
        }

        public GlobalSettings getGlobalSettings() {
            return globalSettings;
        }

        public List<String> getHighlightGeneIds() {
            return new ArrayList<>(highlightGeneIds);
        }

        public Map<Integer, List<String>> getHighlightGeneIdsBySlot() {
            return copyHighlightGeneIdsBySlot(highlightGeneIdsBySlot);
        }

        public List<HighlightRegion> getHighlightRegions() {
            return new ArrayList<>(highlightRegions);
        }

        public SpeciesInfo getBatchOutputSpecies() {
            return batchOutputSpecies;
        }

        private static Map<Integer, List<String>> copyHighlightGeneIdsBySlot(
            Map<Integer, List<String>> highlightGeneIdsBySlot
        ) {
            Map<Integer, List<String>> copied = new LinkedHashMap<>();
            if (highlightGeneIdsBySlot == null) {
                return copied;
            }

            for (Map.Entry<Integer, List<String>> entry : highlightGeneIdsBySlot.entrySet()) {
                Integer slotNumber = entry.getKey();
                if (slotNumber == null) {
                    continue;
                }
                copied.put(
                    slotNumber,
                    entry.getValue() == null ? new ArrayList<>() : new ArrayList<>(entry.getValue())
                );
            }
            return copied;
        }
    }

    public static final class HighlightRegion {
        private final int slotNumber;
        private final String chromosomeName;
        private final long start;
        private final long end;
        private final String label;

        public HighlightRegion(int slotNumber, String chromosomeName, long start, long end, String label) {
            this.slotNumber = slotNumber;
            this.chromosomeName = chromosomeName == null ? "" : chromosomeName.trim();
            this.start = Math.min(start, end);
            this.end = Math.max(start, end);
            this.label = label == null ? "" : label.trim();
        }

        public int getSlotNumber() {
            return slotNumber;
        }

        public String getChromosomeName() {
            return chromosomeName;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        public String getLabel() {
            return label;
        }

        public boolean isValid() {
            return slotNumber > 0 && !chromosomeName.isEmpty() && start >= 1L && end >= 1L;
        }
    }

    public static final class LinkRun {
        private final int linkNumber;
        private final GenomeSelection leftSelection;
        private final GenomeSelection rightSelection;
        private final GenomeCompareService.Parameters parameters;
        private final GenomeCompareExistingResultScanner.ReusableResult reusableResult;

        public LinkRun(int linkNumber, GenomeSelection leftSelection,
                       GenomeSelection rightSelection, GenomeCompareService.Parameters parameters) {
            this(linkNumber, leftSelection, rightSelection, parameters, null);
        }

        public LinkRun(int linkNumber, GenomeSelection leftSelection,
                       GenomeSelection rightSelection, GenomeCompareService.Parameters parameters,
                       GenomeCompareExistingResultScanner.ReusableResult reusableResult) {
            this.linkNumber = linkNumber;
            this.leftSelection = leftSelection;
            this.rightSelection = rightSelection;
            this.parameters = parameters;
            this.reusableResult = reusableResult;
        }

        public int getLinkNumber() {
            return linkNumber;
        }

        public GenomeSelection getLeftSelection() {
            return leftSelection;
        }

        public GenomeSelection getRightSelection() {
            return rightSelection;
        }

        public GenomeCompareService.Parameters getParameters() {
            return parameters;
        }

        public GenomeCompareExistingResultScanner.ReusableResult getReusableResult() {
            return reusableResult;
        }

        public boolean usesExistingResult() {
            return reusableResult != null;
        }

        public String getPairLabel() {
            return buildPairFolderName(leftSelection, rightSelection);
        }

        public String getDisplayLabel() {
            return formatGenomeSelectionLabel(leftSelection) + " x " + formatGenomeSelectionLabel(rightSelection);
        }
    }

    public static final class PairResult {
        private final LinkRun linkRun;
        private final File outputDir;
        private final GenomeCompareService.Result genomeCompareResult;

        public PairResult(LinkRun linkRun, File outputDir, GenomeCompareService.Result genomeCompareResult) {
            this.linkRun = linkRun;
            this.outputDir = outputDir;
            this.genomeCompareResult = genomeCompareResult;
        }

        public LinkRun getLinkRun() {
            return linkRun;
        }

        public GenomeSelection getLeftSelection() {
            return linkRun.getLeftSelection();
        }

        public GenomeSelection getRightSelection() {
            return linkRun.getRightSelection();
        }

        public File getOutputDir() {
            return outputDir;
        }

        public GenomeCompareService.Result getGenomeCompareResult() {
            return genomeCompareResult;
        }

        public String getPairLabel() {
            return linkRun.getPairLabel();
        }

        public String getDisplayLabel() {
            return linkRun.getDisplayLabel();
        }
    }

    public static final class Result {
        private final File outputDir;
        private final List<PairResult> pairResults;

        public Result(File outputDir, List<PairResult> pairResults) {
            this.outputDir = outputDir;
            this.pairResults = new ArrayList<>(pairResults);
        }

        public File getOutputDir() {
            return outputDir;
        }

        public List<PairResult> getPairResults() {
            return new ArrayList<>(pairResults);
        }
    }

    private MultipleSyntenyService() {
    }

    public static Result run(List<GenomeSelection> genomeSelections,
                             List<LinkRun> linkRuns,
                             ProgressListener progressListener) throws Exception {
        return run(buildLegacyRequest(genomeSelections, linkRuns), progressListener);
    }

    public static Result run(RunRequest runRequest,
                             ProgressListener progressListener) throws Exception {
        if (runRequest == null) {
            throw new IllegalArgumentException("Multiple Synteny run request is required.");
        }

        List<GenomeSelection> normalizedSelections = normalizeSelections(runRequest.getGenomeSelections());
        if (normalizedSelections.isEmpty()) {
            throw new IllegalArgumentException("Multiple Synteny requires linked genomes.");
        }
        validateSelections(normalizedSelections);

        List<LinkRun> normalizedLinkRuns = normalizeLinkRuns(runRequest.getLinkRuns(), normalizedSelections);
        validateLinkRuns(normalizedSelections, normalizedLinkRuns);

        GlobalSettings normalizedSettings = normalizeGlobalSettings(runRequest.getGlobalSettings());
        List<GenomeLayout> normalizedGenomeLayouts =
            normalizeGenomeLayouts(runRequest.getGenomeLayouts(), normalizedSelections, normalizedSettings);
        List<LinkLayout> normalizedLinkLayouts =
            normalizeLinkLayouts(runRequest.getLinkLayouts(), normalizedLinkRuns, normalizedSettings);
        List<String> normalizedHighlightGeneIds = normalizeValues(runRequest.getHighlightGeneIds());
        Map<Integer, List<String>> normalizedHighlightGeneIdsBySlot =
            normalizeHighlightGeneIdsBySlot(
                runRequest.getHighlightGeneIdsBySlot(),
                normalizedSelections,
                normalizedHighlightGeneIds
            );
        List<HighlightRegion> normalizedHighlightRegions =
            normalizeHighlightRegions(runRequest.getHighlightRegions(), normalizedSelections);

        RunRequest normalizedRequest = new RunRequest(
            normalizedSelections,
            normalizedLinkRuns,
            normalizedGenomeLayouts,
            normalizedLinkLayouts,
            normalizedSettings,
            normalizedHighlightGeneIds,
            normalizedHighlightGeneIdsBySlot,
            normalizedHighlightRegions,
            resolveBatchOutputSpecies(runRequest.getBatchOutputSpecies(), normalizedSelections, normalizedLinkRuns)
        );

        List<PairResult> pairResults = new ArrayList<>();
        File outputDir = null;
        try {
            SpeciesInfo batchPrimaryGenome = normalizedRequest.getBatchOutputSpecies();
            outputDir = GenomeCompareProjectGenerator.createProjectDirectory(
                batchPrimaryGenome,
                MULTIPLE_COMPARE_ROOT_NAME,
                ""
            );
            report(progressListener, "Created Multiple Synteny batch directory: " + outputDir.getAbsolutePath());
            File pairResultsRootDir = createSubdirectory(outputDir, PAIR_RESULTS_DIR_NAME);
            writeRootMetadata(outputDir, "STARTED", normalizedSelections, normalizedLinkRuns, pairResults, null);

            for (int i = 0; i < normalizedLinkRuns.size(); i++) {
                LinkRun linkRun = normalizedLinkRuns.get(i);
                report(
                    progressListener,
                    String.format(
                        Locale.US,
                        "Resolving link %d/%d: %s",
                        i + 1,
                        normalizedLinkRuns.size(),
                        linkRun.getDisplayLabel()
                    )
                );

                PairResult pairResult = runSingleLink(linkRun, pairResultsRootDir, progressListener);
                pairResults.add(pairResult);
            }

            report(progressListener, "Exporting Multiple Synteny batch text files...");
            MultipleSyntenyBatchExporter.export(outputDir, normalizedRequest, pairResults, progressListener);

            Result result = new Result(outputDir, pairResults);
            writeRootMetadata(outputDir, "SUCCESS", normalizedSelections, normalizedLinkRuns, pairResults, null);
            report(progressListener, "Multiple Synteny completed.");
            return result;
        } catch (Exception ex) {
            if (outputDir != null) {
                writeRootMetadata(outputDir, "FAILED", normalizedSelections, normalizedLinkRuns, pairResults, ex.getMessage());
            }
            throw ex;
        }
    }

    private static PairResult runSingleLink(LinkRun linkRun, File pairResultsRootDir,
                                            ProgressListener progressListener) throws Exception {
        File pairOutputDir = resolvePairOutputDir(pairResultsRootDir, linkRun);
        if (linkRun.usesExistingResult()) {
            File reusableOutputDir = requireReusableOutputDir(linkRun);
            report(progressListener, "Reusing existing result: " + reusableOutputDir.getAbsolutePath());
            materializeReusablePairResult(reusableOutputDir, pairOutputDir);
            return new PairResult(linkRun, pairOutputDir, null);
        }

        GenomeCompareService.Result compareResult = GenomeCompareService.run(
            linkRun.getLeftSelection().getSpecies(),
            linkRun.getRightSelection().getSpecies(),
            linkRun.getParameters(),
            pairOutputDir,
            message -> report(progressListener, "[" + linkRun.getDisplayLabel() + "] " + message)
        );
        return new PairResult(linkRun, compareResult.getOutputDir(), compareResult);
    }

    private static RunRequest buildLegacyRequest(List<GenomeSelection> genomeSelections,
                                                 List<LinkRun> linkRuns) {
        List<LinkRun> safeLinkRuns = linkRuns == null ? new ArrayList<>() : new ArrayList<>(linkRuns);
        List<String> highlightGeneIds = new ArrayList<>();
        for (LinkRun linkRun : safeLinkRuns) {
            GenomeCompareService.Parameters parameters = linkRun == null ? null : linkRun.getParameters();
            if (parameters != null) {
                highlightGeneIds.addAll(parameters.getHighlightGeneIds());
            }
        }
        return new RunRequest(
            genomeSelections,
            safeLinkRuns,
            new ArrayList<>(),
            new ArrayList<>(),
            null,
            highlightGeneIds,
            new LinkedHashMap<>(),
            new ArrayList<>(),
            safeLinkRuns.isEmpty() || safeLinkRuns.get(0) == null
                || safeLinkRuns.get(0).getLeftSelection() == null
                ? null
                : safeLinkRuns.get(0).getLeftSelection().getSpecies()
        );
    }

    private static SpeciesInfo resolveBatchOutputSpecies(SpeciesInfo requestedBatchOutputSpecies,
                                                         List<GenomeSelection> selections,
                                                         List<LinkRun> linkRuns) {
        if (requestedBatchOutputSpecies != null) {
            return requestedBatchOutputSpecies;
        }
        if (selections != null) {
            for (GenomeSelection selection : selections) {
                if (selection != null && selection.getSpecies() != null) {
                    return selection.getSpecies();
                }
            }
        }
        if (linkRuns != null) {
            for (LinkRun linkRun : linkRuns) {
                if (linkRun != null && linkRun.getLeftSelection() != null
                    && linkRun.getLeftSelection().getSpecies() != null) {
                    return linkRun.getLeftSelection().getSpecies();
                }
            }
        }
        throw new IllegalArgumentException("Multiple Synteny output genome could not be resolved.");
    }

    private static GlobalSettings normalizeGlobalSettings(GlobalSettings globalSettings) {
        if (globalSettings == null) {
            return new GlobalSettings(
                DEFAULT_LINK_OFFSET,
                DEFAULT_LINK_BEND,
                DEFAULT_GAP_LENGTH,
                DEFAULT_CANVAS_WIDTH,
                DEFAULT_CANVAS_HEIGHT,
                false,
                DEFAULT_EQUAL_LENGTH_WIDTH
            );
        }
        return new GlobalSettings(
            Math.max(0, globalSettings.getLinkOffset()),
            Math.max(0, globalSettings.getLinkBend()),
            Math.max(0, globalSettings.getGapLength()),
            Math.max(1, globalSettings.getCanvasWidth()),
            Math.max(1, globalSettings.getCanvasHeight()),
            globalSettings.isEqualGenomeLength(),
            Math.max(1, globalSettings.getEqualGenomeLengthWidth())
        );
    }

    private static List<GenomeLayout> normalizeGenomeLayouts(List<GenomeLayout> genomeLayouts,
                                                             List<GenomeSelection> selections,
                                                             GlobalSettings globalSettings) {
        Map<Integer, GenomeLayout> layoutBySlot = new LinkedHashMap<>();
        if (genomeLayouts != null) {
            for (GenomeLayout genomeLayout : genomeLayouts) {
                if (genomeLayout != null) {
                    layoutBySlot.put(genomeLayout.getSlotNumber(), genomeLayout);
                }
            }
        }

        List<GenomeLayout> normalized = new ArrayList<>();
        for (int i = 0; i < selections.size(); i++) {
            GenomeSelection selection = selections.get(i);
            GenomeLayout provided = layoutBySlot.get(selection.getSlotNumber());
            if (provided != null) {
                normalized.add(normalizeGenomeLayout(provided, selection, i + 1));
            } else {
                normalized.add(buildDefaultGenomeLayout(selection, i, globalSettings));
            }
        }
        return normalized;
    }

    private static GenomeLayout normalizeGenomeLayout(GenomeLayout layout,
                                                      GenomeSelection selection,
                                                      int fallbackZOrder) {
        List<ChromosomeLayout> chromosomes = new ArrayList<>();
        if (layout.getChromosomes() != null && !layout.getChromosomes().isEmpty()) {
            for (ChromosomeLayout chromosomeLayout : layout.getChromosomes()) {
                if (chromosomeLayout == null) {
                    continue;
                }
                chromosomes.add(new ChromosomeLayout(
                    Math.max(1, chromosomeLayout.getChromosomeOrder()),
                    chromosomeLayout.getChromosomeName(),
                    chromosomeLayout.getDisplayStart(),
                    chromosomeLayout.getDisplayEnd(),
                    Math.max(1L, chromosomeLayout.getBpLength())
                ));
            }
        }
        if (chromosomes.isEmpty()) {
            chromosomes = buildDefaultChromosomeLayouts(selection, Math.max(1, layout.getRectWidth()), 0);
        }

        return new GenomeLayout(
            selection.getSlotNumber(),
            safe(layout.getGenomeId()).isEmpty() ? "Genome" + selection.getSlotNumber() : layout.getGenomeId(),
            safe(layout.getDisplayName()).isEmpty() ? formatGenomeSelectionLabel(selection) : layout.getDisplayName(),
            Math.max(1, layout.getZOrder() > 0 ? layout.getZOrder() : fallbackZOrder),
            layout.getLeftBottomX(),
            layout.getLeftBottomY(),
            Math.max(1, layout.getRectWidth()),
            Math.max(1, layout.getRectHeight()),
            layout.getRotationDeg(),
            chromosomes
        );
    }

    private static GenomeLayout buildDefaultGenomeLayout(GenomeSelection selection,
                                                         int index,
                                                         GlobalSettings globalSettings) {
        int width = globalSettings.isEqualGenomeLength()
            ? globalSettings.getEqualGenomeLengthWidth()
            : 500;
        double leftBottomX = 390.0d;
        double leftBottomY = 130.0d + index * 110.0d;
        List<ChromosomeLayout> chromosomes = buildDefaultChromosomeLayouts(selection, width, globalSettings.getGapLength());
        return new GenomeLayout(
            selection.getSlotNumber(),
            "Genome" + selection.getSlotNumber(),
            formatGenomeSelectionLabel(selection),
            index + 1,
            leftBottomX,
            leftBottomY,
            Math.max(1, width),
            12,
            0,
            chromosomes
        );
    }

    private static List<ChromosomeLayout> buildDefaultChromosomeLayouts(GenomeSelection selection,
                                                                        int rectWidth,
                                                                        int gapLength) {
        List<String> chromosomes = selection == null ? new ArrayList<>() : selection.getChromosomes();
        List<ChromosomeLayout> layouts = new ArrayList<>();
        if (chromosomes == null || chromosomes.isEmpty()) {
            return layouts;
        }

        int safeGap = Math.max(0, gapLength);
        int totalGap = safeGap * Math.max(0, chromosomes.size() - 1);
        int availableWidth = Math.max(chromosomes.size(), rectWidth - totalGap);
        int baseWidth = Math.max(1, availableWidth / Math.max(1, chromosomes.size()));
        int remainder = Math.max(0, availableWidth - baseWidth * chromosomes.size());
        int currentX = 0;

        for (int i = 0; i < chromosomes.size(); i++) {
            String chromosomeName = chromosomes.get(i);
            int segmentWidth = baseWidth + (i < remainder ? 1 : 0);
            layouts.add(new ChromosomeLayout(
                i + 1,
                chromosomeName,
                currentX,
                currentX + segmentWidth,
                resolveChromosomeLength(selection.getSpecies(), chromosomeName)
            ));
            currentX += segmentWidth + safeGap;
        }
        return layouts;
    }

    private static long resolveChromosomeLength(SpeciesInfo species, String chromosomeName) {
        if (species == null || chromosomeName == null || species.getGenomeData() == null) {
            return 1L;
        }
        for (simplegenomehub.model.GenomeData.ChromosomeStat chromosomeStat
            : species.getGenomeData().getChromosomeStats()) {
            if (chromosomeStat == null || chromosomeStat.getName() == null) {
                continue;
            }
            if (chromosomeName.equals(chromosomeStat.getName())
                || chromosomeName.equalsIgnoreCase(chromosomeStat.getName())) {
                return Math.max(1L, chromosomeStat.getSize());
            }
        }
        return 1L;
    }

    private static List<LinkLayout> normalizeLinkLayouts(List<LinkLayout> linkLayouts,
                                                         List<LinkRun> linkRuns,
                                                         GlobalSettings globalSettings) {
        Map<Integer, LinkLayout> layoutByLinkNumber = new LinkedHashMap<>();
        if (linkLayouts != null) {
            for (LinkLayout linkLayout : linkLayouts) {
                if (linkLayout != null) {
                    layoutByLinkNumber.put(linkLayout.getLinkNumber(), linkLayout);
                }
            }
        }

        List<LinkLayout> normalized = new ArrayList<>();
        for (LinkRun linkRun : linkRuns) {
            LinkLayout provided = layoutByLinkNumber.get(linkRun.getLinkNumber());
            if (provided != null) {
                normalized.add(new LinkLayout(
                    linkRun.getLinkNumber(),
                    linkRun.getLeftSelection().getSlotNumber(),
                    linkRun.getRightSelection().getSlotNumber(),
                    safe(provided.getLinkType()).isEmpty() ? "double_arc" : provided.getLinkType(),
                    safe(provided.getEdge1()).isEmpty() ? "top" : provided.getEdge1(),
                    safe(provided.getEdge2()).isEmpty() ? "top" : provided.getEdge2(),
                    Math.max(0, provided.getBendValue()),
                    safe(provided.getBulgeDir()).isEmpty() ? "auto" : provided.getBulgeDir()
                ));
            } else {
                normalized.add(new LinkLayout(
                    linkRun.getLinkNumber(),
                    linkRun.getLeftSelection().getSlotNumber(),
                    linkRun.getRightSelection().getSlotNumber(),
                    "double_arc",
                    "top",
                    "top",
                    globalSettings.getLinkBend(),
                    "up"
                ));
            }
        }
        return normalized;
    }

    private static List<GenomeSelection> normalizeSelections(List<GenomeSelection> genomeSelections) {
        List<GenomeSelection> normalized = new ArrayList<>();
        for (GenomeSelection selection : genomeSelections) {
            if (selection == null) {
                continue;
            }
            normalized.add(new GenomeSelection(
                selection.getSlotNumber(),
                selection.getSpecies(),
                normalizeChromosomes(selection.getChromosomes())
            ));
        }
        return normalized;
    }

    private static List<LinkRun> normalizeLinkRuns(List<LinkRun> linkRuns, List<GenomeSelection> selections) {
        Map<Integer, GenomeSelection> selectionBySlot = new LinkedHashMap<>();
        for (GenomeSelection selection : selections) {
            selectionBySlot.put(selection.getSlotNumber(), selection);
        }

        List<LinkRun> normalized = new ArrayList<>();
        for (LinkRun linkRun : linkRuns) {
            if (linkRun == null || linkRun.getLeftSelection() == null || linkRun.getRightSelection() == null) {
                continue;
            }

            GenomeSelection leftSelection = selectionBySlot.get(linkRun.getLeftSelection().getSlotNumber());
            GenomeSelection rightSelection = selectionBySlot.get(linkRun.getRightSelection().getSlotNumber());
            if (leftSelection == null || rightSelection == null) {
                continue;
            }

            normalized.add(new LinkRun(
                linkRun.getLinkNumber(),
                leftSelection,
                rightSelection,
                normalizeParameters(linkRun.getParameters(), leftSelection, rightSelection),
                linkRun.getReusableResult()
            ));
        }
        return normalized;
    }

    private static GenomeCompareService.Parameters normalizeParameters(GenomeCompareService.Parameters parameters,
                                                                       GenomeSelection leftSelection,
                                                                       GenomeSelection rightSelection) {
        if (parameters == null) {
            return null;
        }

        List<String> genome1Chromosomes = parameters.getGenome1Chromosomes();
        if (genome1Chromosomes == null) {
            genome1Chromosomes = leftSelection.getChromosomes();
        }

        List<String> genome2Chromosomes = parameters.getGenome2Chromosomes();
        if (genome2Chromosomes == null) {
            genome2Chromosomes = rightSelection.getChromosomes();
        }

        return new GenomeCompareService.Parameters(
            parameters.getCpu(),
            parameters.getEvalue(),
            parameters.getNumHits(),
            parameters.isDirectPlot(),
            normalizeChromosomes(genome1Chromosomes),
            normalizeChromosomes(genome2Chromosomes),
            normalizeValues(parameters.getHighlightGeneIds())
        );
    }

    private static Map<Integer, List<String>> normalizeHighlightGeneIdsBySlot(
        Map<Integer, List<String>> highlightGeneIdsBySlot,
        List<GenomeSelection> selections,
        List<String> fallbackHighlightGeneIds
    ) {
        Map<Integer, List<String>> normalized = new LinkedHashMap<>();
        Map<Integer, GenomeSelection> selectionBySlot = new LinkedHashMap<>();
        if (selections != null) {
            for (GenomeSelection selection : selections) {
                if (selection != null) {
                    selectionBySlot.put(selection.getSlotNumber(), selection);
                }
            }
        }

        if (highlightGeneIdsBySlot != null) {
            for (Map.Entry<Integer, List<String>> entry : highlightGeneIdsBySlot.entrySet()) {
                Integer slotNumber = entry.getKey();
                if (slotNumber == null || !selectionBySlot.containsKey(slotNumber)) {
                    continue;
                }
                normalized.put(slotNumber, normalizeValues(entry.getValue()));
            }
        }

        if (!normalized.isEmpty()) {
            for (Integer slotNumber : selectionBySlot.keySet()) {
                normalized.putIfAbsent(slotNumber, new ArrayList<>());
            }
            return normalized;
        }

        if (fallbackHighlightGeneIds != null && !fallbackHighlightGeneIds.isEmpty()) {
            List<String> normalizedFallback = normalizeValues(fallbackHighlightGeneIds);
            for (Integer slotNumber : selectionBySlot.keySet()) {
                normalized.put(slotNumber, new ArrayList<>(normalizedFallback));
            }
        }
        return normalized;
    }

    private static List<String> normalizeChromosomes(List<String> chromosomes) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (chromosomes == null) {
            return new ArrayList<>();
        }
        for (String chromosome : chromosomes) {
            if (chromosome == null) {
                continue;
            }
            String trimmed = chromosome.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return new ArrayList<>(normalized);
    }

    private static List<String> normalizeValues(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return new ArrayList<>();
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return new ArrayList<>(normalized);
    }

    private static List<HighlightRegion> normalizeHighlightRegions(List<HighlightRegion> highlightRegions,
                                                                   List<GenomeSelection> selections) {
        LinkedHashSet<Integer> validSlots = new LinkedHashSet<>();
        if (selections != null) {
            for (GenomeSelection selection : selections) {
                if (selection != null) {
                    validSlots.add(selection.getSlotNumber());
                }
            }
        }

        List<HighlightRegion> normalized = new ArrayList<>();
        if (highlightRegions == null || highlightRegions.isEmpty()) {
            return normalized;
        }

        LinkedHashSet<String> seenKeys = new LinkedHashSet<>();
        for (HighlightRegion region : highlightRegions) {
            if (region == null || !region.isValid() || !validSlots.contains(region.getSlotNumber())) {
                continue;
            }
            String key = region.getSlotNumber() + "\t"
                + region.getChromosomeName() + "\t"
                + region.getStart() + "\t"
                + region.getEnd() + "\t"
                + region.getLabel();
            if (seenKeys.add(key)) {
                normalized.add(region);
            }
        }
        return normalized;
    }

    private static void validateSelections(List<GenomeSelection> selections) {
        if (selections.size() < 2) {
            throw new IllegalArgumentException("Multiple Synteny requires at least two linked genomes.");
        }

        for (GenomeSelection selection : selections) {
            if (selection.getSpecies() == null) {
                throw new IllegalArgumentException("Please select a genome for Genome "
                    + selection.getSlotNumber() + ".");
            }
            if (selection.getChromosomes().isEmpty()) {
                throw new IllegalArgumentException("Please provide at least one chromosome for Genome "
                    + selection.getSlotNumber() + ".");
            }
        }

        List<String> overlapMessages = findRepeatedGenomeChromosomeOverlaps(selections);
        if (!overlapMessages.isEmpty()) {
            throw new IllegalArgumentException(
                "Repeated genome selections contain overlapping chromosome names.\n"
                    + String.join("\n", overlapMessages)
            );
        }
    }

    private static void validateLinkRuns(List<GenomeSelection> selections, List<LinkRun> linkRuns) {
        if (linkRuns.isEmpty()) {
            throw new IllegalArgumentException("Please create at least one link before starting Multiple Synteny.");
        }

        LinkedHashSet<Integer> availableSlots = new LinkedHashSet<>();
        for (GenomeSelection selection : selections) {
            availableSlots.add(selection.getSlotNumber());
        }

        LinkedHashSet<String> pairKeys = new LinkedHashSet<>();
        for (LinkRun linkRun : linkRuns) {
            if (!linkRun.usesExistingResult() && linkRun.getParameters() == null) {
                throw new IllegalArgumentException("Run settings are missing for " + linkRun.getDisplayLabel() + ".");
            }

            GenomeSelection leftSelection = linkRun.getLeftSelection();
            GenomeSelection rightSelection = linkRun.getRightSelection();
            if (leftSelection == null || rightSelection == null) {
                throw new IllegalArgumentException("A link is missing one or both genomes.");
            }
            if (!availableSlots.contains(leftSelection.getSlotNumber())
                || !availableSlots.contains(rightSelection.getSlotNumber())) {
                throw new IllegalArgumentException("A link references a genome that is not available in this run.");
            }
            if (leftSelection.getSlotNumber() == rightSelection.getSlotNumber()) {
                throw new IllegalArgumentException("A link cannot compare a genome slot with itself.");
            }

            int smallerSlot = Math.min(leftSelection.getSlotNumber(), rightSelection.getSlotNumber());
            int largerSlot = Math.max(leftSelection.getSlotNumber(), rightSelection.getSlotNumber());
            String pairKey = smallerSlot + ":" + largerSlot;
            if (!pairKeys.add(pairKey)) {
                throw new IllegalArgumentException("Duplicate links detected for Genome "
                    + smallerSlot + " and Genome " + largerSlot + ".");
            }
        }
    }

    private static List<String> findRepeatedGenomeChromosomeOverlaps(List<GenomeSelection> selections) {
        Map<String, List<GenomeSelection>> bySpecies = new LinkedHashMap<>();
        List<String> messages = new ArrayList<>();

        for (GenomeSelection selection : selections) {
            String speciesKey = buildSpeciesKey(selection.getSpecies());
            List<GenomeSelection> previousSelections = bySpecies.computeIfAbsent(speciesKey, key -> new ArrayList<>());
            for (GenomeSelection previousSelection : previousSelections) {
                List<String> overlaps = findOverlaps(previousSelection.getChromosomes(), selection.getChromosomes());
                if (!overlaps.isEmpty()) {
                    messages.add(String.format(
                        Locale.US,
                        "Genome %d and Genome %d share chromosome(s): %s",
                        previousSelection.getSlotNumber(),
                        selection.getSlotNumber(),
                        String.join(", ", overlaps)
                    ));
                }
            }
            previousSelections.add(selection);
        }

        return messages;
    }

    private static List<String> findOverlaps(List<String> leftChromosomes, List<String> rightChromosomes) {
        LinkedHashSet<String> leftSet = new LinkedHashSet<>(normalizeChromosomes(leftChromosomes));
        List<String> overlaps = new ArrayList<>();
        for (String chromosome : normalizeChromosomes(rightChromosomes)) {
            if (leftSet.contains(chromosome) && !overlaps.contains(chromosome)) {
                overlaps.add(chromosome);
            }
        }
        return overlaps;
    }

    private static String buildSpeciesKey(SpeciesInfo species) {
        if (species == null) {
            return "";
        }
        File genomeFile = species.getGenomeFile();
        File annotationFile = species.getAnnotationFile();
        return safe(species.getSpeciesDirectoryName()) + "|"
            + safe(genomeFile != null ? genomeFile.getAbsolutePath() : "") + "|"
            + safe(annotationFile != null ? annotationFile.getAbsolutePath() : "");
    }

    private static String buildPairFolderName(GenomeSelection leftSelection, GenomeSelection rightSelection) {
        return "Genome" + leftSelection.getSlotNumber() + "vs" + rightSelection.getSlotNumber();
    }

    private static String formatGenomeSelectionLabel(GenomeSelection selection) {
        if (selection == null || selection.getSpecies() == null) {
            return "Genome " + (selection != null ? selection.getSlotNumber() : "?");
        }
        SpeciesInfo species = selection.getSpecies();
        return species.getSpeciesName() + " (" + species.getVersion() + ")";
    }

    private static File createSubdirectory(File parentDir, String childName) throws IOException {
        if (parentDir == null || !parentDir.isDirectory()) {
            throw new IOException("Parent directory is not available: "
                + (parentDir == null ? "" : parentDir.getAbsolutePath()));
        }
        File childDir = new File(parentDir, childName);
        if (childDir.exists()) {
            if (!childDir.isDirectory()) {
                throw new IOException("Path exists but is not a directory: " + childDir.getAbsolutePath());
            }
            return childDir;
        }
        if (!childDir.mkdirs()) {
            throw new IOException("Failed to create directory: " + childDir.getAbsolutePath());
        }
        return childDir;
    }

    private static File resolvePairOutputDir(File pairResultsRootDir, LinkRun linkRun) throws IOException {
        if (pairResultsRootDir == null || !pairResultsRootDir.isDirectory()) {
            throw new IOException("PairResults directory is not available.");
        }
        if (linkRun == null) {
            throw new IOException("Link definition is missing for pairwise output.");
        }
        return new File(pairResultsRootDir, linkRun.getPairLabel());
    }

    private static void materializeReusablePairResult(File sourceDir, File targetDir) throws IOException {
        if (sourceDir == null || !sourceDir.isDirectory()) {
            throw new IOException("Reusable result directory does not exist: "
                + (sourceDir == null ? "" : sourceDir.getAbsolutePath()));
        }
        File pairDir = createSubdirectory(targetDir.getParentFile(), targetDir.getName());

        copyRequiredFile(
            new File(sourceDir, RUN_METADATA_FILE_NAME),
            new File(pairDir, RUN_METADATA_FILE_NAME)
        );
        copyRequiredFile(
            new File(sourceDir, GenomeCompareLinkRegionExporter.OUTPUT_FILE_NAME),
            new File(pairDir, GenomeCompareLinkRegionExporter.OUTPUT_FILE_NAME)
        );

        File[] mappingFiles = sourceDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(CHR_LAYOUT_SUFFIX));
        if (mappingFiles == null || mappingFiles.length == 0) {
            throw new IOException("Missing *.MultipleSynteny.ChrLayout.tab.xls in "
                + sourceDir.getAbsolutePath());
        }
        for (File mappingFile : mappingFiles) {
            copyRequiredFile(mappingFile, new File(pairDir, mappingFile.getName()));
        }

        File highlightGeneListFile = new File(sourceDir, HIGHLIGHT_GENE_LIST_FILE_NAME);
        if (highlightGeneListFile.isFile()) {
            copyRequiredFile(highlightGeneListFile, new File(pairDir, HIGHLIGHT_GENE_LIST_FILE_NAME));
        }
    }

    private static void copyRequiredFile(File sourceFile, File targetFile) throws IOException {
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new IOException("Missing required file: "
                + (sourceFile == null ? "" : sourceFile.getAbsolutePath()));
        }
        Files.copy(sourceFile.toPath(), targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static File requireReusableOutputDir(LinkRun linkRun) throws IOException {
        if (linkRun == null || linkRun.getReusableResult() == null
            || linkRun.getReusableResult().getOutputDir() == null) {
            throw new IOException("Selected reusable result is missing for "
                + (linkRun != null ? linkRun.getDisplayLabel() : "a link") + ".");
        }

        File outputDir = linkRun.getReusableResult().getOutputDir();
        if (!outputDir.isDirectory()) {
            throw new IOException("Selected reusable result folder does not exist: " + outputDir.getAbsolutePath());
        }

        File metadataFile = new File(outputDir, "run-metadata.txt");
        if (!metadataFile.isFile()) {
            throw new IOException("Selected reusable result is missing run-metadata.txt: " + outputDir.getAbsolutePath());
        }
        return outputDir;
    }

    private static void writeRootMetadata(File outputDir, String status,
                                          List<GenomeSelection> selections,
                                          List<LinkRun> linkRuns,
                                          List<PairResult> pairResults,
                                          String errorMessage) {
        if (outputDir == null) {
            return;
        }

        Map<Integer, PairResult> resultsByLinkNumber = new LinkedHashMap<>();
        for (PairResult pairResult : pairResults) {
            if (pairResult != null && pairResult.getLinkRun() != null) {
                resultsByLinkNumber.put(pairResult.getLinkRun().getLinkNumber(), pairResult);
            }
        }

        File metadataFile = new File(outputDir, "run-metadata.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(metadataFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("status=" + safe(status));
            writer.newLine();
            writer.write("runTime=" + LocalDateTime.now());
            writer.newLine();
            if (errorMessage != null && !errorMessage.trim().isEmpty()) {
                writer.write("error=" + safe(errorMessage));
                writer.newLine();
            }

            writer.write("[genomes]");
            writer.newLine();
            for (GenomeSelection selection : selections) {
                writer.write("Genome" + selection.getSlotNumber() + "="
                    + safe(selection.getSpecies() != null ? selection.getSpecies().getSpeciesDirectoryName() : ""));
                writer.newLine();
                writer.write("Genome" + selection.getSlotNumber() + ".displayName="
                    + safe(formatGenomeSelectionLabel(selection)));
                writer.newLine();
                writer.write("Genome" + selection.getSlotNumber() + ".chromosomes="
                    + joinValues(selection.getChromosomes()));
                writer.newLine();
            }

            writer.write("[links]");
            writer.newLine();
            for (LinkRun linkRun : linkRuns) {
                String key = "Link" + linkRun.getLinkNumber();
                GenomeCompareService.Parameters parameters = linkRun.getParameters();
                writer.write(key + "=" + safe(linkRun.getDisplayLabel()));
                writer.newLine();
                writer.write(key + ".pair=" + safe(linkRun.getPairLabel()));
                writer.newLine();
                writer.write(key + ".genome1=Genome" + linkRun.getLeftSelection().getSlotNumber());
                writer.newLine();
                writer.write(key + ".genome2=Genome" + linkRun.getRightSelection().getSlotNumber());
                writer.newLine();
                writer.write(key + ".reuseExistingResult=" + linkRun.usesExistingResult());
                writer.newLine();
                writer.write(key + ".genome1Chromosomes="
                    + joinValues(linkRun.getLeftSelection().getChromosomes()));
                writer.newLine();
                writer.write(key + ".genome2Chromosomes="
                    + joinValues(linkRun.getRightSelection().getChromosomes()));
                writer.newLine();
                if (parameters != null) {
                    writer.write(key + ".cpu=" + parameters.getCpu());
                    writer.newLine();
                    writer.write(key + ".evalue=" + parameters.getEvalue());
                    writer.newLine();
                    writer.write(key + ".numHits=" + parameters.getNumHits());
                    writer.newLine();
                    writer.write(key + ".directPlot=" + parameters.isDirectPlot());
                    writer.newLine();
                    writer.write(key + ".highlightGeneIds=" + joinValues(parameters.getHighlightGeneIds()));
                    writer.newLine();
                }
                if (linkRun.usesExistingResult() && linkRun.getReusableResult() != null) {
                    writer.write(key + ".reusedSelection=" + safe(linkRun.getReusableResult().getSelectionLabel()));
                    writer.newLine();
                    writer.write(key + ".reusedSourceDir="
                        + safe(linkRun.getReusableResult().getOutputDir() != null
                            ? linkRun.getReusableResult().getOutputDir().getAbsolutePath()
                            : ""));
                    writer.newLine();
                }

                PairResult pairResult = resultsByLinkNumber.get(linkRun.getLinkNumber());
                if (pairResult != null && pairResult.getOutputDir() != null) {
                    writer.write(key + ".outputDir=" + pairResult.getOutputDir().getAbsolutePath());
                    writer.newLine();
                }
            }
        } catch (Exception ignored) {
            // Root metadata is helpful but not critical for the run itself.
        }
    }

    private static String joinValues(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    private static void report(ProgressListener progressListener, String message) {
        if (progressListener != null) {
            progressListener.onProgress(message);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
