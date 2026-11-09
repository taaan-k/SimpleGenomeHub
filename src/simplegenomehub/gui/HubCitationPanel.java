package simplegenomehub.gui;

import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.View;

/** Local, selectable citation; opening a browser is the only external action. */
final class HubCitationPanel extends JPanel {
    static final URI PAPER_URI = URI.create("https://doi.org/10.48130/gcomm-0026-0021");
    private static final Logger LOGGER = Logger.getLogger(HubCitationPanel.class.getName());

    @FunctionalInterface
    interface BrowserLauncher { void browse(URI uri) throws Exception; }

    private final BrowserLauncher browser;
    private final JEditorPane citation = new WrappingCitationPane();
    private boolean opening;

    HubCitationPanel() { this(HubCitationPanel::browseWithDesktop); }

    HubCitationPanel(BrowserLauncher browser) {
        super(new BorderLayout());
        this.browser = browser;
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(218, 226, 236)),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        citation.setContentType("text/html");
        citation.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        citation.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_11);
        citation.setForeground(new Color(91, 101, 115));
        citation.setOpaque(false);
        citation.setBorder(BorderFactory.createEmptyBorder());
        citation.setMargin(new Insets(0, 0, 0, 0));
        citation.setEditable(false);
        citation.setFocusable(true);
        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        citation.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
            focusManager.getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS));
        citation.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
            focusManager.getDefaultFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS));
        citation.setText("<html><head><style>body { margin: 0; } "
            + "a { color: #245b9e; text-decoration: underline; }</style></head><body>"
            + "Please cite: Wang Z, Liang Z, Tang S, Huang L, Chen C. 2026. "
            + "<a href='" + PAPER_URI + "'>Simple Genome Hub: a local platform for genome data management and analysis.</a> "
            + "<i>Genomics Communications</i> 3: e021. doi: "
            + "<a href='" + PAPER_URI + "'>10.48130/gcomm-0026-0021</a></body></html>");
        citation.setCaretPosition(0);
        citation.setToolTipText(PAPER_URI.toString());
        citation.getAccessibleContext().setAccessibleName("Simple Genome Hub citation");
        citation.getAccessibleContext().setAccessibleDescription("Select text to copy, or press Enter to open the paper. " + PAPER_URI);
        citation.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) openPaper();
            else if (event.getEventType() == HyperlinkEvent.EventType.ENTERED)
                citation.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            else if (event.getEventType() == HyperlinkEvent.EventType.EXITED)
                citation.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
        });
        citation.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openPaper");
        citation.getActionMap().put("openPaper", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { openPaper(); }
        });
        // A width/font change can alter the number of lines without changing the text.
        citation.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) { revalidate(); }
        });
        citation.addPropertyChangeListener("font", event -> revalidate());
        add(citation, BorderLayout.CENTER);
    }

    private static void browseWithDesktop(URI uri) throws Exception {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            throw new UnsupportedOperationException("Opening a browser is not supported on this system.");
        Desktop.getDesktop().browse(uri);
    }

    void openPaper() {
        if (opening) return;
        opening = true;
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                browser.browse(PAPER_URI);
                return null;
            }
            @Override protected void done() {
                try { get(); }
                catch (Exception failure) {
                    LOGGER.log(Level.WARNING, "Unable to open the Simple Genome Hub paper", failure);
                    if (isShowing()) {
                        JTextField address = new JTextField(PAPER_URI.toString());
                        address.setEditable(false);
                        address.selectAll();
                        JOptionPane.showMessageDialog(HubCitationPanel.this,
                            new Object[]{"Unable to open your browser. Open this address manually:", address},
                            "Unable to Open Paper", JOptionPane.WARNING_MESSAGE);
                    }
                } finally { opening = false; }
            }
        };
        worker.execute();
    }

    /** Calculate height from the allocated width, rather than the HTML's unwrapped width. */
    private static final class WrappingCitationPane extends JEditorPane {
        @Override public Dimension getPreferredSize() {
            Container parent = getParent();
            Insets outer = parent == null ? new Insets(0, 0, 0, 0) : parent.getInsets();
            int width = parent == null ? getWidth() : parent.getWidth() - outer.left - outer.right;
            if (width <= 0) width = 600;
            Insets insets = getInsets();
            View view = getUI().getRootView(this);
            view.setSize(Math.max(1, width - insets.left - insets.right), Integer.MAX_VALUE);
            return new Dimension(width, (int)Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom);
        }
        @Override public Dimension getMinimumSize() { return new Dimension(0, getPreferredSize().height); }
    }
}
