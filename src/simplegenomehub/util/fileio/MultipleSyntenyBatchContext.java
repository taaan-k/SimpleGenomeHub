package simplegenomehub.util.fileio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable batch context shared by Multiple Synteny export steps.
 */
final class MultipleSyntenyBatchContext {

    private final MultipleSyntenyService.RunRequest runRequest;
    private final List<MultipleSyntenyService.PairResult> pairResults;
    private final Map<Integer, MultipleSyntenyService.GenomeSelection> selectionsBySlot;
    private final Map<Integer, MultipleSyntenyService.GenomeLayout> layoutsBySlot;
    private final Map<Integer, MultipleSyntenyService.LinkRun> linkRunsByNumber;
    private final Map<Integer, MultipleSyntenyService.LinkLayout> linkLayoutsByNumber;

    MultipleSyntenyBatchContext(MultipleSyntenyService.RunRequest runRequest,
                                List<MultipleSyntenyService.PairResult> pairResults) {
        if (runRequest == null) {
            throw new IllegalArgumentException("Multiple Synteny run request is required.");
        }

        this.runRequest = runRequest;
        this.pairResults = pairResults == null ? new ArrayList<>() : new ArrayList<>(pairResults);
        this.selectionsBySlot = new LinkedHashMap<>();
        this.layoutsBySlot = new LinkedHashMap<>();
        this.linkRunsByNumber = new LinkedHashMap<>();
        this.linkLayoutsByNumber = new LinkedHashMap<>();

        for (MultipleSyntenyService.GenomeSelection selection : runRequest.getGenomeSelections()) {
            if (selection != null) {
                selectionsBySlot.put(selection.getSlotNumber(), selection);
            }
        }
        for (MultipleSyntenyService.GenomeLayout layout : runRequest.getGenomeLayouts()) {
            if (layout != null) {
                layoutsBySlot.put(layout.getSlotNumber(), layout);
            }
        }
        for (MultipleSyntenyService.LinkRun linkRun : runRequest.getLinkRuns()) {
            if (linkRun != null) {
                linkRunsByNumber.put(linkRun.getLinkNumber(), linkRun);
            }
        }
        for (MultipleSyntenyService.LinkLayout linkLayout : runRequest.getLinkLayouts()) {
            if (linkLayout != null) {
                linkLayoutsByNumber.put(linkLayout.getLinkNumber(), linkLayout);
            }
        }
    }

    MultipleSyntenyService.RunRequest getRunRequest() {
        return runRequest;
    }

    List<MultipleSyntenyService.PairResult> getPairResults() {
        return new ArrayList<>(pairResults);
    }

    List<MultipleSyntenyService.GenomeLayout> getGenomeLayouts() {
        return runRequest.getGenomeLayouts();
    }

    List<String> getHighlightGeneIds() {
        return runRequest.getHighlightGeneIds();
    }

    List<String> getHighlightGeneIds(int slotNumber) {
        Map<Integer, List<String>> highlightGeneIdsBySlot = runRequest.getHighlightGeneIdsBySlot();
        if (highlightGeneIdsBySlot == null) {
            return new ArrayList<>();
        }
        List<String> values = highlightGeneIdsBySlot.get(slotNumber);
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    List<MultipleSyntenyService.HighlightRegion> getHighlightRegions() {
        return runRequest.getHighlightRegions();
    }

    MultipleSyntenyService.GlobalSettings getGlobalSettings() {
        return runRequest.getGlobalSettings();
    }

    MultipleSyntenyService.GenomeSelection getGenomeSelection(int slotNumber) {
        return selectionsBySlot.get(slotNumber);
    }

    MultipleSyntenyService.GenomeLayout getGenomeLayout(int slotNumber) {
        return layoutsBySlot.get(slotNumber);
    }

    MultipleSyntenyService.LinkRun getLinkRun(int linkNumber) {
        return linkRunsByNumber.get(linkNumber);
    }

    MultipleSyntenyService.LinkLayout getLinkLayout(int linkNumber) {
        return linkLayoutsByNumber.get(linkNumber);
    }

    String getGenomeId(int slotNumber) {
        MultipleSyntenyService.GenomeLayout layout = getGenomeLayout(slotNumber);
        if (layout != null && hasText(layout.getGenomeId())) {
            return layout.getGenomeId();
        }
        return "Genome" + slotNumber;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static final class LinkInfoEntry {
        private final String genome1Id;
        private final String chr1;
        private final long start1;
        private final long end1;
        private final String genome2Id;
        private final String chr2;
        private final long start2;
        private final long end2;
        private final String linkType;
        private final String edge1;
        private final String edge2;
        private final int bendValue;
        private final String bulgeDir;
        private final String color;
        private final double alpha;
        private final int zOrder;

        LinkInfoEntry(String genome1Id, String chr1, long start1, long end1,
                      String genome2Id, String chr2, long start2, long end2,
                      String linkType, String edge1, String edge2,
                      int bendValue, String bulgeDir,
                      String color, double alpha, int zOrder) {
            this.genome1Id = genome1Id;
            this.chr1 = chr1;
            this.start1 = start1;
            this.end1 = end1;
            this.genome2Id = genome2Id;
            this.chr2 = chr2;
            this.start2 = start2;
            this.end2 = end2;
            this.linkType = linkType;
            this.edge1 = edge1;
            this.edge2 = edge2;
            this.bendValue = bendValue;
            this.bulgeDir = bulgeDir;
            this.color = color;
            this.alpha = alpha;
            this.zOrder = zOrder;
        }

        String getGenome1Id() {
            return genome1Id;
        }

        String getChr1() {
            return chr1;
        }

        long getStart1() {
            return start1;
        }

        long getEnd1() {
            return end1;
        }

        String getGenome2Id() {
            return genome2Id;
        }

        String getChr2() {
            return chr2;
        }

        long getStart2() {
            return start2;
        }

        long getEnd2() {
            return end2;
        }

        String getLinkType() {
            return linkType;
        }

        String getEdge1() {
            return edge1;
        }

        String getEdge2() {
            return edge2;
        }

        int getBendValue() {
            return bendValue;
        }

        String getBulgeDir() {
            return bulgeDir;
        }

        String getColor() {
            return color;
        }

        double getAlpha() {
            return alpha;
        }

        int getZOrder() {
            return zOrder;
        }
    }

    static final class HighlightInfoEntry {
        private final String geneId;
        private final String genomeId;
        private final String chromosomeName;
        private final long start;
        private final long end;
        private final String color;
        private final String label;

        HighlightInfoEntry(String geneId, String genomeId, String chromosomeName,
                           long start, long end, String color, String label) {
            this.geneId = geneId;
            this.genomeId = genomeId;
            this.chromosomeName = chromosomeName;
            this.start = start;
            this.end = end;
            this.color = color;
            this.label = label;
        }

        String getGeneId() {
            return geneId;
        }

        String getGenomeId() {
            return genomeId;
        }

        String getChromosomeName() {
            return chromosomeName;
        }

        long getStart() {
            return start;
        }

        long getEnd() {
            return end;
        }

        String getColor() {
            return color;
        }

        String getLabel() {
            return label;
        }
    }
}
