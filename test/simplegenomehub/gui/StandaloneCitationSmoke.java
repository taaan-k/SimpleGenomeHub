package simplegenomehub.gui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.*;
import simplegenomehub.config.SimpleGenomeHubConfig;

/** Native upstream UI smoke, compiled and run against the repository's own bundled libraries. */
public final class StandaloneCitationSmoke {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sgh-standalone-citation-");
        Path data = Files.createDirectory(root.resolve("data"));
        SimpleGenomeHubConfig.getInstance().setDataRootDir(data.toFile());
        JFrame frame = edt(() -> new JFrame("Standalone SGH citation"));
        SimpleGenomeHubMainPanel hub = edt(SimpleGenomeHubMainPanel::new);
        try {
            HubCitationNotice notice = find(hub, HubCitationNotice.class);
            Component card = notice.getComponentsInLayer(JLayeredPane.POPUP_LAYER)[0];
            edt(() -> { frame.add(hub); frame.setSize(1100, 800); frame.setVisible(true); return null; });
            waitFor(() -> edt(card::isShowing));
            Thread.sleep(200);
            for (int scale : new int[]{100, 140}) {
                edt(() -> { SimpleGenomeHubFontManager.previewScalePercent(scale); frame.setSize(900, 800); frame.validate(); return null; });
                Thread.sleep(80);
                edt(() -> {
                    frame.validate();
                    JTextArea body = find((Container)card, JTextArea.class);
                    check(body.modelToView2D(body.getDocument().getLength() - 1).getMaxY() <= body.getHeight(), "Clipped notice");
                    check(Math.abs(card.getX() * 2 + card.getWidth() - notice.getWidth()) <= 1, "Not centered");
                    BufferedImage image = new BufferedImage(hub.getWidth(), hub.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = image.createGraphics(); try { hub.paint(g); } finally { g.dispose(); }
                    ImageIO.write(image, "png", root.resolve("notice-" + scale + ".png").toFile());
                    return null;
                });
            }
            waitFor(() -> edt(() -> !card.isVisible()));
            edt(() -> { frame.setVisible(false); frame.setVisible(true); return null; });
            Thread.sleep(80);
            check(!edt(card::isVisible), "Notice replayed after hide/show");
            check(find(hub, HubCitationPanel.class) != null, "Bottom citation missing");
        } finally { edt(() -> { frame.dispose(); return null; }); }

        AtomicInteger opened = new AtomicInteger();
        HubCitationPanel footer = edt(() -> new HubCitationPanel(uri -> {
            check(HubCitationPanel.PAPER_URI.equals(uri), "Wrong DOI");
            check(!SwingUtilities.isEventDispatchThread(), "Browser blocks EDT"); opened.incrementAndGet();
        }));
        HubCitationNotice notice = edt(() -> new HubCitationNotice(() -> true, footer::openPaper));
        JFrame linkFrame = edt(() -> {
            JFrame f = new JFrame("Standalone citation activation"); JPanel page = new JPanel(new BorderLayout());
            page.add(footer, BorderLayout.SOUTH); notice.setContent(page); f.add(notice); f.setSize(900, 500); f.setVisible(true); return f;
        });
        try {
            Component card = notice.getComponentsInLayer(JLayeredPane.POPUP_LAYER)[0];
            waitFor(() -> edt(card::isShowing));
            JButton link = button((Container)card);
            edt(() -> { link.doClick(); check(!card.isVisible(), "Link did not dismiss notice"); return null; });
            waitFor(() -> opened.get() == 1);
        } finally { edt(() -> { notice.dispose(); linkFrame.dispose(); return null; }); }
        System.out.println("PASS standalone citation: wrap/scale, first-open notice, expiry, hide/show, permanent footer and background DOI activation; screenshots=" + root);
    }
    private static JButton button(Container parent) {
        if (parent instanceof JButton && HubCitationPanel.PAPER_URI.toString().equals(((JButton)parent).getToolTipText())) return (JButton)parent;
        for (Component child : parent.getComponents()) if (child instanceof Container) { JButton result = button((Container)child); if (result != null) return result; }
        return null;
    }
    private static <T extends Component> T find(Container parent, Class<T> type) {
        if (type.isInstance(parent)) return type.cast(parent);
        for (Component child : parent.getComponents()) if (child instanceof Container) { T result = find((Container)child, type); if (result != null) return result; }
        return null;
    }
    private static <T> T edt(Callable<T> action) throws Exception { FutureTask<T> task = new FutureTask<>(action); SwingUtilities.invokeAndWait(task); return task.get(); }
    private static void waitFor(Callable<Boolean> condition) throws Exception { long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8); while (!condition.call()) { if (System.nanoTime() > end) throw new AssertionError("UI timeout"); Thread.sleep(20); } }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
