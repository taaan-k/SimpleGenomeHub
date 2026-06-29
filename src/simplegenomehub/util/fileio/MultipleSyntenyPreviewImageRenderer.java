package simplegenomehub.util.fileio;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * Renders a static preview image for Multiple Synteny result browsing.
 */
public final class MultipleSyntenyPreviewImageRenderer {

    private MultipleSyntenyPreviewImageRenderer() {
    }

    public static BufferedImage render(JComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("Component must not be null");
        }

        Dimension preferredSize = component.getPreferredSize();
        int width = Math.max(1, preferredSize.width);
        int height = Math.max(1, preferredSize.height);

        component.setSize(width, height);
        component.doLayout();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            component.paint(g2);
        } finally {
            g2.dispose();
        }
        return image;
    }
}
