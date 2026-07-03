package simplegenomehub.util.MultipleSynteny;

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

public final class MultipleSyntenyLinkRoutingHelper {

    private MultipleSyntenyLinkRoutingHelper() {
    }

    public static final class ResolvedEdges {
        public final MultipleSyntenyHorizontalEdgeType edge1Type;
        public final MultipleSyntenyHorizontalEdgeType edge2Type;
        public final Point2D.Double edge1Left;
        public final Point2D.Double edge1Right;
        public final Point2D.Double edge2Left;
        public final Point2D.Double edge2Right;
        public final Point2D.Double genome1Center;
        public final Point2D.Double genome2Center;

        private ResolvedEdges(MultipleSyntenyHorizontalEdgeType edge1Type,
                              MultipleSyntenyHorizontalEdgeType edge2Type,
                              Point2D.Double edge1Left,
                              Point2D.Double edge1Right,
                              Point2D.Double edge2Left,
                              Point2D.Double edge2Right,
                              Point2D.Double genome1Center,
                              Point2D.Double genome2Center) {
            this.edge1Type = edge1Type;
            this.edge2Type = edge2Type;
            this.edge1Left = edge1Left;
            this.edge1Right = edge1Right;
            this.edge2Left = edge2Left;
            this.edge2Right = edge2Right;
            this.genome1Center = genome1Center;
            this.genome2Center = genome2Center;
        }
    }

    public static ResolvedEdges resolveEdges(MultipleSyntenyGenomeModel genome1,
                                             MultipleSyntenyGenomeModel genome2,
                                             int routingBoxHalfHeight) {
        // Step 1: genome1 rectangle top/bottom center vs genome2 body center → select edge1
        Point2D.Double g1RectTop    = genome1.getTopCenterPoint();
        Point2D.Double g1RectBottom = genome1.getBottomCenterPoint();
        Point2D.Double g2BodyCenter = genome2.getCenterPoint();

        MultipleSyntenyHorizontalEdgeType edge1Type =
            distSq(g1RectTop, g2BodyCenter) <= distSq(g1RectBottom, g2BodyCenter)
                ? MultipleSyntenyHorizontalEdgeType.TOP
                : MultipleSyntenyHorizontalEdgeType.BOTTOM;

        // Step 2: genome1 collision box center on the selected side vs genome2 rectangle top/bottom → select edge2
        Point2D.Double g1BoxEdge = boxEdgeCenter(genome1, edge1Type, routingBoxHalfHeight);
        Point2D.Double g2RectTop    = genome2.getTopCenterPoint();
        Point2D.Double g2RectBottom = genome2.getBottomCenterPoint();

        MultipleSyntenyHorizontalEdgeType edge2Type =
            distSq(g1BoxEdge, g2RectTop) <= distSq(g1BoxEdge, g2RectBottom)
                ? MultipleSyntenyHorizontalEdgeType.TOP
                : MultipleSyntenyHorizontalEdgeType.BOTTOM;

        Point2D.Double edge1Left = edge1Type == MultipleSyntenyHorizontalEdgeType.TOP
            ? genome1.getLeftTopPoint() : genome1.getLeftBottomPoint();
        Point2D.Double edge1Right = edge1Type == MultipleSyntenyHorizontalEdgeType.TOP
            ? genome1.getRightTopPoint() : genome1.getRightBottomPoint();
        Point2D.Double edge2Left = edge2Type == MultipleSyntenyHorizontalEdgeType.TOP
            ? genome2.getLeftTopPoint() : genome2.getLeftBottomPoint();
        Point2D.Double edge2Right = edge2Type == MultipleSyntenyHorizontalEdgeType.TOP
            ? genome2.getRightTopPoint() : genome2.getRightBottomPoint();

        return new ResolvedEdges(edge1Type, edge2Type,
                                 edge1Left, edge1Right, edge2Left, edge2Right,
                                 genome1.getCenterPoint(), genome2.getCenterPoint());
    }

    public static MultipleSyntenyLinkType resolveActualType(MultipleSyntenyLinkRouteMode routeMode,
                                                            MultipleSyntenyHorizontalEdgeType edge1Type,
                                                            MultipleSyntenyHorizontalEdgeType edge2Type) {
        if (routeMode == MultipleSyntenyLinkRouteMode.FORCE_SINGLE) {
            return MultipleSyntenyLinkType.SINGLE_ARC;
        }
        if (routeMode == MultipleSyntenyLinkRouteMode.FORCE_DOUBLE) {
            return MultipleSyntenyLinkType.DOUBLE_ARC;
        }
        return edge1Type == edge2Type
            ? MultipleSyntenyLinkType.SINGLE_ARC
            : MultipleSyntenyLinkType.DOUBLE_ARC;
    }

    public static Shape createShape(ResolvedEdges edges,
                                    MultipleSyntenyLinkType actualType,
                                    double bendHeight) {
        double bend = Math.max(0, bendHeight);
        if (actualType == MultipleSyntenyLinkType.SINGLE_ARC) {
            return createSingleArc(edges, bend);
        }
        return createDoubleArc(edges, bend);
    }

    private static Shape createSingleArc(ResolvedEdges edges, double bend) {
        Point2D.Double midRight = midpoint(edges.edge1Right, edges.edge2Right);
        Point2D.Double midLeft  = midpoint(edges.edge2Left, edges.edge1Left);

        // Bend outward from the shared edge side: TOP edges bend away from both genome bodies (upward),
        // BOTTOM edges bend downward. Use the direction from genome1 body center toward edge1.
        Point2D.Double e1Center = midpoint(edges.edge1Left, edges.edge1Right);
        Point2D.Double bendDir = normalize(subtract(e1Center, edges.genome1Center));
        if (bendDir == null) {
            bendDir = edges.edge1Type == MultipleSyntenyHorizontalEdgeType.TOP
                ? new Point2D.Double(0, -1)
                : new Point2D.Double(0,  1);
        }

        Point2D.Double controlRight = add(midRight, scale(bendDir, bend));
        Point2D.Double controlLeft  = add(midLeft,  scale(bendDir, bend));

        Path2D.Double path = new Path2D.Double();
        path.moveTo(edges.edge1Left.x, edges.edge1Left.y);
        path.lineTo(edges.edge1Right.x, edges.edge1Right.y);
        path.quadTo(controlRight.x, controlRight.y, edges.edge2Right.x, edges.edge2Right.y);
        path.lineTo(edges.edge2Left.x, edges.edge2Left.y);
        path.quadTo(controlLeft.x, controlLeft.y, edges.edge1Left.x, edges.edge1Left.y);
        path.closePath();
        return path;
    }

    private static Shape createDoubleArc(ResolvedEdges edges, double bend) {
        Point2D.Double e1Center = midpoint(edges.edge1Left, edges.edge1Right);
        Point2D.Double e2Center = midpoint(edges.edge2Left, edges.edge2Right);

        // Bend outward from each genome's body center
        Point2D.Double dir1 = normalize(subtract(e1Center, edges.genome1Center));
        if (dir1 == null) {
            dir1 = new Point2D.Double(0, -1);
        }
        Point2D.Double dir2 = normalize(subtract(e2Center, edges.genome2Center));
        if (dir2 == null) {
            dir2 = new Point2D.Double(0, 1);
        }

        Point2D.Double ctrl1Right = add(edges.edge1Right, scale(dir1, bend));
        Point2D.Double ctrl2Right = add(edges.edge2Right, scale(dir2, bend));
        Point2D.Double ctrl1Left  = add(edges.edge2Left,  scale(dir2, bend));
        Point2D.Double ctrl2Left  = add(edges.edge1Left,  scale(dir1, bend));

        Path2D.Double path = new Path2D.Double();
        path.moveTo(edges.edge1Left.x, edges.edge1Left.y);
        path.lineTo(edges.edge1Right.x, edges.edge1Right.y);
        path.curveTo(ctrl1Right.x, ctrl1Right.y,
                     ctrl2Right.x, ctrl2Right.y,
                     edges.edge2Right.x, edges.edge2Right.y);
        path.lineTo(edges.edge2Left.x, edges.edge2Left.y);
        path.curveTo(ctrl1Left.x, ctrl1Left.y,
                     ctrl2Left.x, ctrl2Left.y,
                     edges.edge1Left.x, edges.edge1Left.y);
        path.closePath();
        return path;
    }

    private static Point2D.Double boxEdgeCenter(MultipleSyntenyGenomeModel genome,
                                                MultipleSyntenyHorizontalEdgeType edgeType,
                                                int routingBoxHalfHeight) {
        Point2D.Double center = genome.getCenterPoint();
        Point2D.Double topCenterPoint = genome.getTopCenterPoint();
        Point2D.Double bottomCenterPoint = genome.getBottomCenterPoint();

        Point2D.Double outDir;
        if (edgeType == MultipleSyntenyHorizontalEdgeType.TOP) {
            outDir = normalize(subtract(topCenterPoint, center));
            if (outDir == null) {
                outDir = new Point2D.Double(0, -1);
            }
        } else {
            outDir = normalize(subtract(bottomCenterPoint, center));
            if (outDir == null) {
                outDir = new Point2D.Double(0, 1);
            }
        }
        return add(center, scale(outDir, routingBoxHalfHeight));
    }

    private static double distSq(Point2D.Double a, Point2D.Double b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return dx * dx + dy * dy;
    }

    private static Point2D.Double midpoint(Point2D.Double a, Point2D.Double b) {
        return new Point2D.Double((a.x + b.x) * 0.5, (a.y + b.y) * 0.5);
    }

    private static Point2D.Double subtract(Point2D.Double a, Point2D.Double b) {
        return new Point2D.Double(a.x - b.x, a.y - b.y);
    }

    private static Point2D.Double add(Point2D.Double a, Point2D.Double b) {
        return new Point2D.Double(a.x + b.x, a.y + b.y);
    }

    private static Point2D.Double scale(Point2D.Double v, double factor) {
        return new Point2D.Double(v.x * factor, v.y * factor);
    }

    private static Point2D.Double normalize(Point2D.Double v) {
        double len = Math.sqrt(v.x * v.x + v.y * v.y);
        if (len < 1.0e-9) {
            return null;
        }
        return new Point2D.Double(v.x / len, v.y / len);
    }
}
