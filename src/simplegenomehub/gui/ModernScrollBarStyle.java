package simplegenomehub.gui;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

final class ModernScrollBarStyle {

    private static final Color TRACK_COLOR = new Color(244, 247, 251);
    private static final Color THUMB_COLOR = new Color(188, 199, 214);
    private static final Color THUMB_HOVER_COLOR = new Color(156, 173, 194);
    private static final Color THUMB_DRAG_COLOR = new Color(126, 146, 171);
    private static final int THICKNESS = 12;

    private ModernScrollBarStyle() {
    }

    static void applyTo(JScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }

        scrollPane.getVerticalScrollBar().setOpaque(false);
        scrollPane.getHorizontalScrollBar().setOpaque(false);
        configure(scrollPane.getVerticalScrollBar(), true);
        configure(scrollPane.getHorizontalScrollBar(), false);
    }

    private static void configure(JScrollBar scrollBar, boolean vertical) {
        scrollBar.setUnitIncrement(20);
        scrollBar.setBlockIncrement(72);
        if (vertical) {
            scrollBar.setPreferredSize(new Dimension(THICKNESS, 0));
        } else {
            scrollBar.setPreferredSize(new Dimension(0, THICKNESS));
        }
        scrollBar.setUI(new ScrollBarUi(vertical));
    }

    private static final class ScrollBarUi extends BasicScrollBarUI {
        private final boolean vertical;

        private ScrollBarUi(boolean vertical) {
            this.vertical = vertical;
        }

        @Override
        protected void configureScrollBarColors() {
            trackColor = TRACK_COLOR;
            thumbColor = THUMB_COLOR;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return vertical ? new Dimension(8, 34) : new Dimension(34, 8);
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Rectangle trackBounds = shrink(bounds);
                g2.setColor(TRACK_COLOR);
                g2.fillRoundRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height, 10, 10);
            } finally {
                g2.dispose();
            }
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Rectangle thumbBounds = shrink(bounds);
                g2.setColor(resolveThumbColor());
                g2.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, 10, 10);
            } finally {
                g2.dispose();
            }
        }

        private Rectangle shrink(Rectangle bounds) {
            if (vertical) {
                int x = bounds.x + 2;
                int y = bounds.y + 4;
                int width = Math.max(6, bounds.width - 4);
                int height = Math.max(10, bounds.height - 8);
                return new Rectangle(x, y, width, height);
            }

            int x = bounds.x + 4;
            int y = bounds.y + 2;
            int width = Math.max(10, bounds.width - 8);
            int height = Math.max(6, bounds.height - 4);
            return new Rectangle(x, y, width, height);
        }

        private Color resolveThumbColor() {
            if (isDragging) {
                return THUMB_DRAG_COLOR;
            }
            if (isThumbRollover()) {
                return THUMB_HOVER_COLOR;
            }
            return THUMB_COLOR;
        }

        private JButton createZeroButton() {
            JButton button = new JButton();
            Dimension zeroSize = new Dimension(0, 0);
            button.setPreferredSize(zeroSize);
            button.setMinimumSize(zeroSize);
            button.setMaximumSize(zeroSize);
            button.setOpaque(false);
            button.setFocusable(false);
            button.setBorder(BorderFactory.createEmptyBorder());
            return button;
        }
    }
}
