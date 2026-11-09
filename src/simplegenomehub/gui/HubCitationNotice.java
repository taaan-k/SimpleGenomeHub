package simplegenomehub.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;

/** A page-local, one-shot citation card. All state and animation belong to the EDT. */
final class HubCitationNotice extends JLayeredPane {
    private static final long DURATION_NANOS = 3_000_000_000L;
    private static final long FADE_NANOS = 150_000_000L;
    private final BooleanSupplier active;
    private final CitationCard card = new CitationCard();
    private final JTextArea message = new JTextArea(
        "For analyses performed in this module, please cite the Simple Genome Hub paper rather than TBtools alone.");
    private final Timer animation = new Timer(30, event -> tick());
    private final HierarchyListener hierarchy = event -> {
        if ((event.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.PARENT_CHANGED)) != 0) {
            updateOwner();
            if (!isShowing()) dismiss(false);
            else scheduleShow();
        }
    };
    private final ComponentAdapter geometry = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { revalidate(); scheduleShow(); }
    };
    private final WindowStateListener windowState = event -> {
        if ((event.getNewState() & Frame.ICONIFIED) != 0) dismiss(false);
    };
    private JComponent content;
    private Frame owner;
    private Component previousFocus;
    private boolean shown;
    private boolean queued;
    private boolean disposed;
    private long firstPaint;
    private float opacity;

    HubCitationNotice(BooleanSupplier active, Runnable openPaper) {
        this.active = active;
        setOpaque(false);
        card.setOpaque(false);
        card.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_13);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        JLabel title = new JLabel("Please cite Simple Genome Hub");
        title.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_18);
        title.setForeground(SimpleGenomeHubUi.TITLE_BLUE);
        JButton close = button("\u00d7", "Dismiss citation reminder");
        close.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_18);
        close.addActionListener(event -> dismiss(true));
        JPanel heading = new JPanel(new BorderLayout(12, 0));
        heading.setOpaque(false);
        heading.add(title, BorderLayout.CENTER);
        heading.add(close, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);
        message.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_13);
        message.setForeground(new Color(55, 65, 80));
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setEditable(false);
        message.setFocusable(false);
        message.setOpaque(false);
        message.setBorder(BorderFactory.createEmptyBorder());
        JLabel reference = new JLabel("<html>Wang et al. (2026). <i>Genomics Communications</i> 3: e021.</html>");
        reference.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_13);
        reference.setForeground(new Color(70, 80, 95));
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(message, BorderLayout.CENTER);
        body.add(reference, BorderLayout.SOUTH);
        card.add(body, BorderLayout.CENTER);
        JButton paper = button("<html><u>10.48130/gcomm-0026-0021</u> &#8599;</html>", HubCitationPanel.PAPER_URI.toString());
        paper.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_13);
        paper.setForeground(SimpleGenomeHubUi.TITLE_BLUE);
        paper.setHorizontalAlignment(SwingConstants.LEFT);
        paper.getAccessibleContext().setAccessibleName("Open the Simple Genome Hub paper");
        paper.addActionListener(event -> { dismiss(true); if (active.getAsBoolean()) openPaper.run(); });
        card.add(paper, BorderLayout.SOUTH);
        FocusAdapter rememberFocus = new FocusAdapter() {
            @Override public void focusGained(FocusEvent event) {
                Component previous = event.getOppositeComponent();
                if (previous != null && !SwingUtilities.isDescendingFrom(previous, card)) previousFocus = previous;
            }
        };
        close.addFocusListener(rememberFocus);
        paper.addFocusListener(rememberFocus);
        card.setVisible(false);
        add(card, JLayeredPane.POPUP_LAYER);
        addHierarchyListener(hierarchy);
        addComponentListener(geometry);
    }

    private static JButton button(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pressEnter");
        button.getActionMap().put("pressEnter", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { button.doClick(); }
        });
        return button;
    }

    void setContent(JComponent replacement) {
        if (content != null) remove(content);
        content = replacement;
        add(content, JLayeredPane.DEFAULT_LAYER);
        revalidate();
    }

    @Override public Dimension getPreferredSize() { return content == null ? new Dimension() : content.getPreferredSize(); }
    @Override public Dimension getMinimumSize() { return content == null ? new Dimension() : content.getMinimumSize(); }

    @Override public void doLayout() {
        if (content != null) content.setBounds(0, 0, getWidth(), getHeight());
        int width = Math.max(1, Math.min(getWidth() - 32, Math.round(620 * card.getFont().getSize2D() / 13f)));
        Insets insets = card.getInsets();
        message.setSize(Math.max(1, width - insets.left - insets.right), Short.MAX_VALUE);
        int height = Math.max(1, Math.min(getHeight() - 32, card.getPreferredSize().height));
        card.setBounds((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
        scheduleShow();
    }

    private void scheduleShow() {
        if (shown || queued || disposed) return;
        queued = true;
        SwingUtilities.invokeLater(() -> {
            queued = false;
            if (shown || disposed || !isShowing() || getWidth() <= 32 || getHeight() <= 32 || !active.getAsBoolean()) return;
            shown = true;
            previousFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            firstPaint = 0;
            opacity = 0;
            card.setVisible(true);
            revalidate();
            card.repaint();
            animation.start();
        });
    }

    private void tick() {
        if (!isShowing() || !active.getAsBoolean()) { dismiss(false); return; }
        if (firstPaint != 0) {
            long elapsed = System.nanoTime() - firstPaint;
            if (elapsed >= DURATION_NANOS) { dismiss(true); return; }
            opacity = (float)Math.min(1.0, Math.min((double)elapsed / FADE_NANOS,
                (double)(DURATION_NANOS - elapsed) / FADE_NANOS));
        }
        card.repaint();
    }

    private void dismiss(boolean restoreFocus) {
        animation.stop();
        Component focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean ownedFocus = focused != null && SwingUtilities.isDescendingFrom(focused, card);
        card.setVisible(false);
        if (restoreFocus && ownedFocus && isShowing()) {
            Component target = previousFocus;
            if (target == null || !target.isShowing() || !target.isEnabled() || content == null
                || !SwingUtilities.isDescendingFrom(target, content)) target = firstFocusable(content);
            if (target != null) target.requestFocusInWindow();
        }
        repaint();
    }

    private static Component firstFocusable(Component component) {
        if (component == null || !component.isVisible() || !component.isEnabled()) return null;
        if (component.isFocusable()) return component;
        if (component instanceof Container) for (Component child : ((Container)component).getComponents()) {
            Component candidate = firstFocusable(child); if (candidate != null) return candidate;
        }
        return null;
    }

    private void updateOwner() {
        Window window = SwingUtilities.getWindowAncestor(this);
        Frame next = window instanceof Frame ? (Frame)window : null;
        if (next == owner) return;
        if (owner != null) owner.removeWindowStateListener(windowState);
        owner = disposed ? null : next;
        if (owner != null) owner.addWindowStateListener(windowState);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        dismiss(false);
        removeHierarchyListener(hierarchy);
        removeComponentListener(geometry);
        if (owner != null) { owner.removeWindowStateListener(windowState); owner = null; }
        previousFocus = null;
    }

    private final class CitationCard extends JPanel {
        @Override public void paint(Graphics graphics) {
            if (firstPaint == 0 && isShowing()) firstPaint = System.nanoTime();
            Graphics2D g = (Graphics2D)graphics.create();
            try {
                g.setComposite(AlphaComposite.SrcOver.derive(opacity));
                super.paint(g);
            } finally { g.dispose(); }
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D)graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(0, 0, 0, 25));
                g.fillRoundRect(4, 5, getWidth() - 5, getHeight() - 6, 22, 22);
                g.setColor(new Color(255, 255, 255, 217));
                g.fillRoundRect(0, 0, getWidth() - 5, getHeight() - 6, 22, 22);
                g.setColor(new Color(165, 185, 210, 200));
                g.drawRoundRect(0, 0, getWidth() - 6, getHeight() - 7, 22, 22);
            } finally { g.dispose(); }
        }
    }
}
