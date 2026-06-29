package simplegenomehub.util.MultipleSynteny;

import javax.swing.SwingUtilities;
import java.awt.geom.Point2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class MultipleSyntenyLayoutMouseHandler extends MouseAdapter {
    private final MultipleSyntenyLayoutCanvas canvas;
    private boolean dragging;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private double dragStartAnchorX;
    private double dragStartAnchorY;

    public MultipleSyntenyLayoutMouseHandler(MultipleSyntenyLayoutCanvas canvas) {
        this.canvas = canvas;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        canvas.requestFocusInWindow();
        canvas.clearSnapIndicatorPoint();
        Point2D.Double layoutPoint = canvas.toLayoutPoint(e.getX(), e.getY());
        int layoutX = (int) Math.round(layoutPoint.x);
        int layoutY = (int) Math.round(layoutPoint.y);
        if (SwingUtilities.isRightMouseButton(e)) {
            dragging = false;
            canvas.handleRightClickAt(layoutX, layoutY);
            return;
        }

        if (!SwingUtilities.isLeftMouseButton(e)) {
            return;
        }

        MultipleSyntenyGenomeModel genome = canvas.findTopGenome(layoutX, layoutY);
        if (genome == null) {
            dragging = false;
            return;
        }

        canvas.selectGenome(genome);
        dragging = canvas.getSelectedGenome() != null;
        Point2D.Double anchorPoint = genome.getSelectedAnchorPoint();
        dragStartMouseX = layoutPoint.x;
        dragStartMouseY = layoutPoint.y;
        dragStartAnchorX = anchorPoint.x;
        dragStartAnchorY = anchorPoint.y;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!dragging || canvas.getSelectedGenome() == null) {
            return;
        }

        Point2D.Double layoutPoint = canvas.toLayoutPoint(e.getX(), e.getY());
        Point2D.Double snapPoint = canvas.moveSelectedGenomeAnchorTo(
            dragStartAnchorX + layoutPoint.x - dragStartMouseX,
            dragStartAnchorY + layoutPoint.y - dragStartMouseY
        );
        canvas.setSnapIndicatorPoint(snapPoint);
        canvas.updateHoveredLinkAt(
            (int) Math.round(layoutPoint.x),
            (int) Math.round(layoutPoint.y)
        );
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
            Point2D.Double layoutPoint = canvas.toLayoutPoint(e.getX(), e.getY());
            canvas.toggleHoveredLinkTypeAt(
                (int) Math.round(layoutPoint.x),
                (int) Math.round(layoutPoint.y)
            );
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            dragging = false;
            canvas.clearSnapIndicatorPoint();
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        Point2D.Double layoutPoint = canvas.toLayoutPoint(e.getX(), e.getY());
        canvas.updateHoveredLinkAt(
            (int) Math.round(layoutPoint.x),
            (int) Math.round(layoutPoint.y)
        );
    }

    @Override
    public void mouseExited(MouseEvent e) {
        dragging = false;
        canvas.clearSnapIndicatorPoint();
        canvas.clearHoveredLink();
    }
}
