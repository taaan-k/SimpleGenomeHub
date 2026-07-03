package simplegenomehub.util.MultipleSynteny;

import java.awt.geom.Point2D;

/**
 * Grid snapping helper for the Multiple Synteny layout workspace.
 */
public final class MultipleSyntenyGridSnapHelper {

    public static final int GRID_SPACING = 100;
    public static final double SNAP_TOLERANCE = 10.0d;

    private MultipleSyntenyGridSnapHelper() {
    }

    public static SnapResult computeSnapResult(MultipleSyntenyGenomeModel genome) {
        if (genome == null) {
            return null;
        }

        Double bestOffsetX = null;
        Double bestOffsetY = null;
        Point2D.Double bestReferencePoint = null;
        for (Point2D.Double snapPoint : genome.getSnapReferencePoints()) {
            if (snapPoint == null) {
                continue;
            }

            double candidateOffsetX = computeAxisSnapOffset(snapPoint.x);
            if (!Double.isNaN(candidateOffsetX)
                && (bestOffsetX == null || Math.abs(candidateOffsetX) < Math.abs(bestOffsetX))) {
                bestOffsetX = candidateOffsetX;
                bestReferencePoint = new Point2D.Double(snapPoint.x, snapPoint.y);
            }

            double candidateOffsetY = computeAxisSnapOffset(snapPoint.y);
            if (!Double.isNaN(candidateOffsetY)
                && (bestOffsetY == null || Math.abs(candidateOffsetY) < Math.abs(bestOffsetY))) {
                bestOffsetY = candidateOffsetY;
                if (bestReferencePoint == null) {
                    bestReferencePoint = new Point2D.Double(snapPoint.x, snapPoint.y);
                }
            }
        }

        if (bestOffsetX == null && bestOffsetY == null) {
            return null;
        }

        Point2D.Double snapOffset = new Point2D.Double(
            bestOffsetX == null ? 0.0d : bestOffsetX,
            bestOffsetY == null ? 0.0d : bestOffsetY
        );
        Point2D.Double referencePoint = bestReferencePoint == null
            ? new Point2D.Double()
            : bestReferencePoint;
        return new SnapResult(referencePoint, snapOffset);
    }

    private static double computeAxisSnapOffset(double value) {
        double nearestGridValue = Math.round(value / GRID_SPACING) * (double) GRID_SPACING;
        double offset = nearestGridValue - value;
        return Math.abs(offset) <= SNAP_TOLERANCE ? offset : Double.NaN;
    }

    public static final class SnapResult {
        private final Point2D.Double referencePoint;
        private final Point2D.Double snapOffset;

        private SnapResult(Point2D.Double referencePoint, Point2D.Double snapOffset) {
            this.referencePoint = referencePoint == null ? new Point2D.Double() : referencePoint;
            this.snapOffset = snapOffset == null ? new Point2D.Double() : snapOffset;
        }

        public Point2D.Double getReferencePoint() {
            return new Point2D.Double(referencePoint.x, referencePoint.y);
        }

        public Point2D.Double getSnapOffset() {
            return new Point2D.Double(snapOffset.x, snapOffset.y);
        }
    }
}
