package simplegenomehub.util.MultipleSynteny;

import simplegenomehub.gui.SimpleGenomeHubStyle;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultipleSyntenyLayoutCanvas extends JComponent {

    private static final double DEFAULT_ZOOM_FACTOR = 1.0d;
    private static final double MIN_ZOOM_FACTOR = 0.25d;
    private static final double MAX_ZOOM_FACTOR = 10.0d;
    private static final Color GRID_MAJOR = new Color(208, 216, 228);
    private static final int GRID_SPACING = MultipleSyntenyGridSnapHelper.GRID_SPACING;
    private static final Color GENOME_FILL = Color.WHITE;
    private static final Color GENOME_BORDER = new Color(150, 164, 184);
    private static final Color PENDING_BORDER = new Color(66, 122, 206);
    private static final Color SELECTED_BORDER = new Color(222, 143, 40);
    private static final Color CHROMOSOME_FILL = new Color(218, 235, 255);
    private static final Color CHROMOSOME_BORDER = new Color(176, 208, 247);
    private static final Color LABEL_COLOR = new Color(43, 58, 82);
    private static final Color EMPTY_TITLE = new Color(68, 97, 138);
    private static final Color EMPTY_TEXT = new Color(116, 129, 147);
    private static final Color LINK_FILL = new Color(211, 220, 236);
    private static final Color LINK_BORDER = new Color(123, 135, 155);
    private static final Color LINK_HOVER_FILL = new Color(255, 229, 189);
    private static final Color LINK_HOVER_BORDER = new Color(255, 140, 0);

    private List<MultipleSyntenyGenomeModel> genomeItems = Collections.emptyList();
    private List<MultipleSyntenyLinkModel> linkItems = Collections.emptyList();
    private final List<MultipleSyntenyGenomeModel> drawOrder = new ArrayList<>();
    private MultipleSyntenyGenomeModel selectedGenome;
    private MultipleSyntenyGenomeModel pendingConnectionGenome;
    private MultipleSyntenyLinkModel hoveredLink;
    private Point2D.Double snapIndicatorPoint;
    private int routingBoxHalfHeight = 40;
    private int gapLength;
    private int logicalCanvasWidth = 1500;
    private int logicalCanvasHeight = 1000;
    private double zoomFactor = DEFAULT_ZOOM_FACTOR;
    private boolean gridSnapEnabled;

    private final LayoutCanvasListener listener;

    public MultipleSyntenyLayoutCanvas(LayoutCanvasListener listener) {
        this.listener = listener;
        setOpaque(true);
        setBackground(Color.WHITE);
        setDoubleBuffered(true);
        setPreferredSize(new Dimension(logicalCanvasWidth, logicalCanvasHeight));
        setMinimumSize(new Dimension(900, 620));
        setToolTipText("");
        setFocusable(true);
        installDeleteKeyBinding();

        MultipleSyntenyLayoutMouseHandler mouseHandler = new MultipleSyntenyLayoutMouseHandler(this);
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    public void setGenomeItems(List<MultipleSyntenyGenomeModel> genomeItems) {
        this.genomeItems = genomeItems == null ? Collections.emptyList() : genomeItems;
        reconcileDrawOrder();
        if (selectedGenome != null && !this.genomeItems.contains(selectedGenome)) {
            selectedGenome = null;
        }
        if (pendingConnectionGenome != null && !this.genomeItems.contains(pendingConnectionGenome)) {
            pendingConnectionGenome = null;
        }
    }

    public void setLinkItems(List<MultipleSyntenyLinkModel> linkItems) {
        this.linkItems = linkItems == null ? Collections.emptyList() : linkItems;
        if (hoveredLink != null && !this.linkItems.contains(hoveredLink)) {
            hoveredLink = null;
        }
    }

    public List<MultipleSyntenyGenomeModel> getDrawOrder() {
        return new ArrayList<>(drawOrder);
    }

    public void setSelectedGenome(MultipleSyntenyGenomeModel selectedGenome) {
        this.selectedGenome = selectedGenome;
        bringToFront(selectedGenome);
    }

    public MultipleSyntenyGenomeModel getSelectedGenome() {
        return selectedGenome;
    }

    public void setPreviewSettings(int routingBoxHalfHeight, int gapLength) {
        this.routingBoxHalfHeight = Math.max(1, routingBoxHalfHeight);
        this.gapLength = Math.max(0, gapLength);
    }

    public void setGridSnapEnabled(boolean gridSnapEnabled) {
        this.gridSnapEnabled = gridSnapEnabled;
        if (!gridSnapEnabled) {
            clearSnapIndicatorPoint();
        }
    }

    public double getZoomFactor() {
        return zoomFactor;
    }

    public void setZoomFactor(double zoomFactor) {
        double clampedZoom = Math.max(MIN_ZOOM_FACTOR, Math.min(MAX_ZOOM_FACTOR, zoomFactor));
        if (Math.abs(this.zoomFactor - clampedZoom) < 1.0e-6d) {
            return;
        }
        this.zoomFactor = clampedZoom;
        updateScaledViewSize();
    }

    public int toLayoutCoordinate(int displayCoordinate) {
        return (int) Math.round(displayCoordinate / zoomFactor);
    }

    public Point2D.Double toLayoutPoint(double displayX, double displayY) {
        return new Point2D.Double(displayX / zoomFactor, displayY / zoomFactor);
    }

    public void setCanvasSize(int width, int height) {
        logicalCanvasWidth = Math.max(1, width);
        logicalCanvasHeight = Math.max(1, height);
        updateScaledViewSize();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        Point layoutPoint = toLayoutPoint(event.getPoint());
        MultipleSyntenyGenomeModel genome = findGenomeAt(layoutPoint);
        if (genome != null) {
            return genome.getDisplayName();
        }

        MultipleSyntenyLinkModel link = findLinkAt(layoutPoint);
        return link == null ? null : link.getDisplayLabel();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        Graphics2D baseGraphics = (Graphics2D) graphics.create();
        try {
            baseGraphics.setColor(getBackground());
            baseGraphics.fillRect(0, 0, getWidth(), getHeight());

            Graphics2D g2 = (Graphics2D) baseGraphics.create();
            g2.scale(zoomFactor, zoomFactor);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            try {
                paintGrid(g2);
                for (MultipleSyntenyLinkModel linkItem : linkItems) {
                    paintLink(g2, linkItem);
                }
                for (MultipleSyntenyGenomeModel genomeItem : drawOrder) {
                    paintGenome(g2, genomeItem);
                }
                paintSnapIndicator(g2);

                if (genomeItems.isEmpty()) {
                    paintEmptyState(g2);
                }
            } finally {
                g2.dispose();
            }
        } finally {
            baseGraphics.dispose();
        }
    }

    private void paintGrid(Graphics2D g2) {
        Object originalAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setStroke(new BasicStroke((float) (1.0d / zoomFactor)));

        for (int x = 0; x <= logicalCanvasWidth; x += GRID_SPACING) {
            g2.setColor(GRID_MAJOR);
            g2.drawLine(x, 0, x, logicalCanvasHeight);
        }

        for (int y = 0; y <= logicalCanvasHeight; y += GRID_SPACING) {
            g2.setColor(GRID_MAJOR);
            g2.drawLine(0, y, logicalCanvasWidth, y);
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing);
    }

    private void paintEmptyState(Graphics2D g2) {
        String title = "Layout Workspace Ready";
        String detail = "Drag genomes in, left-drag to move them, right-click two genomes to create a link.";

        Font titleFont = SimpleGenomeHubStyle.FONT_SANS_BOLD_18;
        Font detailFont = SimpleGenomeHubStyle.FONT_SANS_PLAIN_14;
        FontMetrics titleMetrics = g2.getFontMetrics(titleFont);
        FontMetrics detailMetrics = g2.getFontMetrics(detailFont);

        int centerX = logicalCanvasWidth / 2;
        int centerY = logicalCanvasHeight / 2;

        g2.setFont(titleFont);
        g2.setColor(EMPTY_TITLE);
        g2.drawString(title, centerX - titleMetrics.stringWidth(title) / 2, centerY - 8);

        g2.setFont(detailFont);
        g2.setColor(EMPTY_TEXT);
        g2.drawString(detail, centerX - detailMetrics.stringWidth(detail) / 2, centerY + 22);
    }

    private void paintGenome(Graphics2D g2, MultipleSyntenyGenomeModel genomeItem) {
        boolean selected = genomeItem == selectedGenome;
        boolean pending = genomeItem == pendingConnectionGenome;

        paintChromosomeTrack(g2, genomeItem);
        if (selected || pending) {
            Shape shape = genomeItem.getShape();
            g2.setColor(selected ? SELECTED_BORDER : PENDING_BORDER);
            g2.setStroke(new BasicStroke(selected ? 2.0f : 1.2f));
            g2.draw(shape);
        }
        paintGenomeLabel(g2, genomeItem, selected);
    }

    private void paintChromosomeTrack(Graphics2D g2, MultipleSyntenyGenomeModel genomeItem) {
        List<MultipleSyntenyGenomeModel.ChromosomeSegment> segments = genomeItem.getChromosomeSegments();
        if (segments.isEmpty()) {
            return;
        }

        for (MultipleSyntenyGenomeModel.ChromosomeSegment segment : segments) {
            Shape segmentShape = createLocalCapsuleShape(
                genomeItem,
                segment.getStartX(),
                -genomeItem.getHeight(),
                segment.getWidth(),
                genomeItem.getHeight()
            );
            g2.setColor(CHROMOSOME_FILL);
            g2.fill(segmentShape);
            g2.setColor(CHROMOSOME_BORDER);
            g2.setStroke(new BasicStroke(0.8f));
            g2.draw(segmentShape);
        }
    }

    private void paintGenomeLabel(Graphics2D g2, MultipleSyntenyGenomeModel genomeItem,
                                  boolean selected) {
        g2.setColor(LABEL_COLOR);
        g2.setFont(selected ? SimpleGenomeHubStyle.FONT_SANS_BOLD_12 : SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);

        String label = genomeItem.getDisplayName();
        FontMetrics metrics = g2.getFontMetrics();
        Rectangle bounds = genomeItem.getVisualBounds();
        int maxWidth = Math.max(90, bounds.width + 40);
        if (metrics.stringWidth(label) > maxWidth) {
            while (label.length() > 4 && metrics.stringWidth(label + "...") > maxWidth) {
                label = label.substring(0, label.length() - 1);
            }
            label = label + "...";
        }

        Point2D.Double center = genomeItem.getPointFromLeftBottom(genomeItem.getWidth() / 2.0, 18);
        g2.drawString(label,
            (int) Math.round(center.x - metrics.stringWidth(label) / 2.0),
            (int) Math.round(center.y + metrics.getAscent() / 2.0));
    }

    private void paintLink(Graphics2D g2, MultipleSyntenyLinkModel linkItem) {
        if (linkItem == null) {
            return;
        }

        Shape shape = linkItem.createShape(routingBoxHalfHeight);
        if (linkItem == hoveredLink) {
            g2.setColor(LINK_HOVER_FILL);
            g2.fill(shape);
            g2.setColor(LINK_HOVER_BORDER);
            g2.setStroke(new BasicStroke(2f));
        } else {
            g2.setColor(LINK_FILL);
            g2.fill(shape);
            g2.setColor(LINK_BORDER);
            g2.setStroke(new BasicStroke(1f));
        }
        g2.draw(shape);
    }

    private void paintSnapIndicator(Graphics2D g2) {
        if (snapIndicatorPoint == null) {
            return;
        }

        double x = snapIndicatorPoint.x;
        double y = snapIndicatorPoint.y;
        double size = 14.0d;

        g2.setColor(new Color(220, 40, 40));
        g2.setStroke(new BasicStroke(1.8f));
        g2.drawLine((int) Math.round(x - size), (int) Math.round(y), (int) Math.round(x + size), (int) Math.round(y));
        g2.drawLine((int) Math.round(x), (int) Math.round(y - size), (int) Math.round(x), (int) Math.round(y + size));
    }

    private MultipleSyntenyGenomeModel findGenomeAt(Point point) {
        for (int i = drawOrder.size() - 1; i >= 0; i--) {
            MultipleSyntenyGenomeModel genome = drawOrder.get(i);
            if (genome.contains(point)) {
                return genome;
            }
        }
        return null;
    }

    private MultipleSyntenyLinkModel findLinkAt(Point point) {
        for (int i = linkItems.size() - 1; i >= 0; i--) {
            MultipleSyntenyLinkModel linkItem = linkItems.get(i);
            if (linkItem.contains(point.x, point.y, routingBoxHalfHeight)) {
                return linkItem;
            }
        }
        return null;
    }

    public MultipleSyntenyGenomeModel findTopGenome(int x, int y) {
        return findGenomeAt(new Point(x, y));
    }

    public void selectGenome(MultipleSyntenyGenomeModel genome) {
        if (genome == null) {
            return;
        }
        selectedGenome = genome;
        bringToFront(genome);
        if (listener != null) {
            listener.onGenomeSelected(genome);
        }
        repaint();
    }

    public void translateSelectedGenome(double deltaX, double deltaY) {
        if (selectedGenome == null) {
            return;
        }
        Point2D.Double anchorPoint = selectedGenome.getSelectedAnchorPoint();
        moveSelectedGenomeAnchorTo(anchorPoint.x + deltaX, anchorPoint.y + deltaY);
    }

    public Point2D.Double moveSelectedGenomeAnchorTo(double x, double y) {
        if (selectedGenome == null) {
            return null;
        }
        selectedGenome.moveSelectedAnchorTo(x, y);
        Point2D.Double snapPoint = applyGridSnapIfNeeded(selectedGenome);
        if (listener != null) {
            listener.onGenomeLayoutChanged(selectedGenome);
        }
        repaint();
        return snapPoint;
    }

    public void handleRightClickAt(int x, int y) {
        MultipleSyntenyGenomeModel genome = findTopGenome(x, y);
        if (genome == null) {
            pendingConnectionGenome = null;
            repaint();
            return;
        }

        if (pendingConnectionGenome == null) {
            pendingConnectionGenome = genome;
            repaint();
            return;
        }

        if (pendingConnectionGenome == genome) {
            pendingConnectionGenome = null;
            repaint();
            return;
        }

        if (listener != null) {
            listener.onCreateLinkRequested(pendingConnectionGenome, genome);
        }
        pendingConnectionGenome = null;
        updateHoveredLinkAt(x, y);
    }

    public void updateHoveredLinkAt(int x, int y) {
        MultipleSyntenyLinkModel nextHoveredLink = findLinkAt(new Point(x, y));
        if (hoveredLink == nextHoveredLink) {
            updateCursor(new Point(x, y));
            return;
        }
        hoveredLink = nextHoveredLink;
        updateCursor(new Point(x, y));
        repaint();
    }

    public void clearHoveredLink() {
        if (hoveredLink == null) {
            setCursor(Cursor.getDefaultCursor());
            return;
        }
        hoveredLink = null;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    public void toggleHoveredLinkTypeAt(int x, int y) {
        if (hoveredLink == null || !hoveredLink.contains(x, y, routingBoxHalfHeight)) {
            return;
        }
        hoveredLink.cycleRouteMode();
        if (listener != null) {
            listener.onLinkToggled(hoveredLink);
        }
        repaint();
    }

    public void deleteSelectedItem() {
        if (hoveredLink != null) {
            MultipleSyntenyLinkModel linkToRemove = hoveredLink;
            hoveredLink = null;
            if (listener != null) {
                listener.onRemoveLinkRequested(linkToRemove);
            }
            repaint();
            return;
        }

        if (selectedGenome != null) {
            MultipleSyntenyGenomeModel genomeToRemove = selectedGenome;
            selectedGenome = null;
            if (pendingConnectionGenome == genomeToRemove) {
                pendingConnectionGenome = null;
            }
            if (listener != null) {
                listener.onRemoveGenomeRequested(genomeToRemove);
            }
            repaint();
        }
    }

    private void updateCursor(Point point) {
        boolean overGenome = findGenomeAt(point) != null;
        boolean overLink = findLinkAt(point) != null;
        setCursor(overGenome || overLink
            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            : Cursor.getDefaultCursor());
    }

    private Point2D.Double applyGridSnapIfNeeded(MultipleSyntenyGenomeModel genome) {
        if (!gridSnapEnabled || genome == null) {
            return null;
        }

        MultipleSyntenyGridSnapHelper.SnapResult snapResult =
            MultipleSyntenyGridSnapHelper.computeSnapResult(genome);
        if (snapResult == null) {
            return null;
        }

        Point2D.Double snapOffset = snapResult.getSnapOffset();
        if (Math.abs(snapOffset.x) < 1.0e-6d && Math.abs(snapOffset.y) < 1.0e-6d) {
            return null;
        }

        Point2D.Double anchorPoint = genome.getSelectedAnchorPoint();
        Point2D.Double snappedPoint = new Point2D.Double(
            anchorPoint.x + snapOffset.x,
            anchorPoint.y + snapOffset.y
        );
        Point2D.Double snappedReferencePoint = snapResult.getReferencePoint();
        snappedReferencePoint.x += snapOffset.x;
        snappedReferencePoint.y += snapOffset.y;
        genome.moveSelectedAnchorTo(snappedPoint.x, snappedPoint.y);
        return snappedReferencePoint;
    }

    public void setSnapIndicatorPoint(Point2D.Double snapIndicatorPoint) {
        this.snapIndicatorPoint = snapIndicatorPoint == null
            ? null
            : new Point2D.Double(snapIndicatorPoint.x, snapIndicatorPoint.y);
        repaint();
    }

    public void clearSnapIndicatorPoint() {
        if (snapIndicatorPoint == null) {
            return;
        }
        snapIndicatorPoint = null;
        repaint();
    }

    private Point toLayoutPoint(Point point) {
        if (point == null) {
            return new Point();
        }
        return new Point(toLayoutCoordinate(point.x), toLayoutCoordinate(point.y));
    }

    private void updateScaledViewSize() {
        int scaledWidth = Math.max(1, (int) Math.round(logicalCanvasWidth * zoomFactor));
        int scaledHeight = Math.max(1, (int) Math.round(logicalCanvasHeight * zoomFactor));
        Dimension targetSize = new Dimension(scaledWidth, scaledHeight);
        if (!targetSize.equals(getPreferredSize())) {
            setPreferredSize(targetSize);
            revalidate();
        }
        repaint();
    }

    private void installDeleteKeyBinding() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelectedItem");
        getActionMap().put("deleteSelectedItem", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectedItem();
            }
        });
    }

    private void reconcileDrawOrder() {
        List<MultipleSyntenyGenomeModel> orderedItems = new ArrayList<>(drawOrder);
        orderedItems.removeIf(item -> !genomeItems.contains(item));
        for (MultipleSyntenyGenomeModel genomeItem : genomeItems) {
            if (!orderedItems.contains(genomeItem)) {
                orderedItems.add(genomeItem);
            }
        }
        drawOrder.clear();
        drawOrder.addAll(orderedItems);
        bringToFront(selectedGenome);
    }

    private void bringToFront(MultipleSyntenyGenomeModel genome) {
        if (genome == null || !drawOrder.remove(genome)) {
            return;
        }
        drawOrder.add(genome);
    }

    private Shape createLocalCapsuleShape(MultipleSyntenyGenomeModel genome,
                                          double x, double y, double width, double height) {
        double safeWidth = Math.max(1.0d, width);
        double safeHeight = Math.max(1.0d, height);
        double arcSize = Math.min(safeWidth, safeHeight);

        RoundRectangle2D.Double localCapsule =
            new RoundRectangle2D.Double(x, y, safeWidth, safeHeight, arcSize, arcSize);
        AffineTransform transform = new AffineTransform();
        transform.translate(genome.getLeftBottomX(), genome.getLeftBottomY());
        transform.rotate(Math.toRadians(-genome.getRotation()));
        return transform.createTransformedShape(localCapsule);
    }


    public interface LayoutCanvasListener {
        void onGenomeSelected(MultipleSyntenyGenomeModel genome);

        void onCreateLinkRequested(MultipleSyntenyGenomeModel first, MultipleSyntenyGenomeModel second);

        void onRemoveLinkRequested(MultipleSyntenyLinkModel linkModel);

        void onRemoveGenomeRequested(MultipleSyntenyGenomeModel genome);

        void onLinkToggled(MultipleSyntenyLinkModel linkModel);

        void onGenomeLayoutChanged(MultipleSyntenyGenomeModel genome);
    }
}
