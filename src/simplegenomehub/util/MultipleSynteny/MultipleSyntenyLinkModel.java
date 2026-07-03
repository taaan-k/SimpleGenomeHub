package simplegenomehub.util.MultipleSynteny;

import simplegenomehub.util.fileio.GenomeCompareExistingResultScanner;
import simplegenomehub.util.fileio.GenomeCompareService;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

public final class MultipleSyntenyLinkModel {

    public static final String DEFAULT_TYPE = "New Link";
    private static final int DEFAULT_BEND_HEIGHT = 40;

    private final MultipleSyntenyGenomeModel genome1;
    private final MultipleSyntenyGenomeModel genome2;
    private MultipleSyntenyLinkRouteMode routeMode;
    private int bendHeight;
    private int runCpu;
    private double runEvalue;
    private int runNumHits;
    private boolean runDirectPlot;
    private List<GenomeCompareExistingResultScanner.ReusableResult> reusableResults;
    private GenomeCompareExistingResultScanner.ReusableResult selectedReusableResult;

    public MultipleSyntenyLinkModel(MultipleSyntenyGenomeModel genome1, MultipleSyntenyGenomeModel genome2) {
        this.genome1 = genome1;
        this.genome2 = genome2;
        this.routeMode = MultipleSyntenyLinkRouteMode.AUTO;
        this.bendHeight = DEFAULT_BEND_HEIGHT;
        this.runCpu = GenomeCompareService.DEFAULT_CPU;
        this.runEvalue = GenomeCompareService.DEFAULT_EVALUE;
        this.runNumHits = GenomeCompareService.DEFAULT_NUM_HITS;
        this.runDirectPlot = GenomeCompareService.DEFAULT_DIRECT_PLOT;
        this.reusableResults = new ArrayList<>();
        this.selectedReusableResult = null;
    }

    public MultipleSyntenyGenomeModel getGenome1() {
        return genome1;
    }

    public MultipleSyntenyGenomeModel getGenome2() {
        return genome2;
    }

    public MultipleSyntenyLinkRouteMode getRouteMode() {
        return routeMode;
    }

    public void setRouteMode(MultipleSyntenyLinkRouteMode routeMode) {
        if (routeMode != null) {
            this.routeMode = routeMode;
        }
    }

    public void cycleRouteMode() {
        switch (routeMode) {
            case AUTO:
                routeMode = MultipleSyntenyLinkRouteMode.FORCE_SINGLE;
                break;
            case FORCE_SINGLE:
                routeMode = MultipleSyntenyLinkRouteMode.FORCE_DOUBLE;
                break;
            default:
                routeMode = MultipleSyntenyLinkRouteMode.AUTO;
                break;
        }
    }

    public int getBendHeight() {
        return bendHeight;
    }

    public void setBendHeight(int bendHeight) {
        this.bendHeight = Math.max(0, bendHeight);
    }

    public List<GenomeCompareExistingResultScanner.ReusableResult> getReusableResults() {
        return new ArrayList<>(reusableResults);
    }

    public void setReusableResults(List<GenomeCompareExistingResultScanner.ReusableResult> reusableResults) {
        this.reusableResults = reusableResults == null ? new ArrayList<>() : new ArrayList<>(reusableResults);
        if (selectedReusableResult == null) {
            return;
        }
        for (GenomeCompareExistingResultScanner.ReusableResult reusableResult : this.reusableResults) {
            if (reusableResult.equals(selectedReusableResult)) {
                selectedReusableResult = reusableResult;
                return;
            }
        }
        selectNewLink();
    }

    public GenomeCompareExistingResultScanner.ReusableResult getSelectedReusableResult() {
        return selectedReusableResult;
    }

    public void selectReusableResult(GenomeCompareExistingResultScanner.ReusableResult reusableResult) {
        selectedReusableResult = reusableResult;
    }

    public void selectNewLink() {
        selectedReusableResult = null;
    }

    public boolean usesExistingResult() {
        return selectedReusableResult != null;
    }

    public int getRunCpu() {
        return runCpu;
    }

    public void setRunCpu(int runCpu) {
        this.runCpu = Math.max(1, runCpu);
    }

    public double getRunEvalue() {
        return runEvalue;
    }

    public void setRunEvalue(double runEvalue) {
        this.runEvalue = runEvalue > 0.0d ? runEvalue : GenomeCompareService.DEFAULT_EVALUE;
    }

    public int getRunNumHits() {
        return runNumHits;
    }

    public void setRunNumHits(int runNumHits) {
        this.runNumHits = Math.max(1, runNumHits);
    }

    public boolean isRunDirectPlot() {
        return runDirectPlot;
    }

    public void setRunDirectPlot(boolean runDirectPlot) {
        this.runDirectPlot = runDirectPlot;
    }

    public String getDisplayLabel() {
        return genome1.getDisplayName() + " x " + genome2.getDisplayName();
    }

    public boolean matches(MultipleSyntenyGenomeModel first, MultipleSyntenyGenomeModel second) {
        return (genome1 == first && genome2 == second) || (genome1 == second && genome2 == first);
    }

    public boolean references(MultipleSyntenyGenomeModel genomeModel) {
        return genome1 == genomeModel || genome2 == genomeModel;
    }

    public boolean contains(int x, int y, int routingBoxHalfHeight) {
        return createShape(routingBoxHalfHeight).contains(x, y);
    }

    public MultipleSyntenyLinkRoutingHelper.ResolvedEdges resolveEdges(int routingBoxHalfHeight) {
        if (genome1 == null || genome2 == null) {
            return null;
        }
        return MultipleSyntenyLinkRoutingHelper.resolveEdges(genome1, genome2, routingBoxHalfHeight);
    }

    public Shape createShape(int routingBoxHalfHeight) {
        if (genome1 == null || genome2 == null) {
            return new Path2D.Double();
        }
        MultipleSyntenyLinkRoutingHelper.ResolvedEdges edges =
            MultipleSyntenyLinkRoutingHelper.resolveEdges(genome1, genome2, routingBoxHalfHeight);
        MultipleSyntenyLinkType actualType =
            MultipleSyntenyLinkRoutingHelper.resolveActualType(routeMode, edges.edge1Type, edges.edge2Type);
        return MultipleSyntenyLinkRoutingHelper.createShape(edges, actualType, bendHeight);
    }
}
