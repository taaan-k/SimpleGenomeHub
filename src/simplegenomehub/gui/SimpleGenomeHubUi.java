package simplegenomehub.gui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.WindowEvent;
import java.awt.*;

/**
 * Shared UI helpers for the main SimpleGenomeHub desktop surfaces.
 */
public final class SimpleGenomeHubUi {

    private static final int COMBO_BOX_MIN_HEIGHT = 30;
    private static final int COMBO_BOX_HORIZONTAL_PADDING = 42;
    private static final int COMBO_BOX_MAX_AUTO_WIDTH = 420;
    /**
     * Windows in these packages receive the shared SimpleGenomeHub styling automatically.
     * The application's own packages (including the ported population-genetics windows) are
     * covered so migrated dialogs match the rest of the application without extra wiring.
     */
    private static final String DIALOG_STYLE_PACKAGE_PREFIX = "simplegenomehub";
    /** Marks containers that already watch for dynamically added children. */
    private static final String DYNAMIC_STYLE_INSTALLED = "sgh.dynamicStyleInstalled";

    public static final Color APP_BACKGROUND = SimpleGenomeHubStyle.APP_BACKGROUND;
    public static final Color TITLE_BLUE = SimpleGenomeHubStyle.TITLE_BLUE;
    public static final Color CARD_BORDER = SimpleGenomeHubStyle.CARD_BORDER;
    public static final Color SOFT_BUTTON_TOP = SimpleGenomeHubStyle.SOFT_BUTTON_TOP;
    public static final Color SOFT_BUTTON_BOTTOM = SimpleGenomeHubStyle.SOFT_BUTTON_BOTTOM;
    public static final Color SOFT_BUTTON_BORDER = SimpleGenomeHubStyle.SOFT_BUTTON_BORDER;
    public static final Color DIALOG_BACKGROUND = SimpleGenomeHubStyle.DIALOG_BACKGROUND;
    public static final Color DIALOG_PANEL_BACKGROUND = SimpleGenomeHubStyle.DIALOG_PANEL_BACKGROUND;
    public static final Color DIALOG_PRIMARY_BUTTON = SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON;
    public static final Color DIALOG_PRIMARY_BUTTON_BORDER = SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_BORDER;
    public static final Color DIALOG_PRIMARY_BUTTON_TEXT = SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_TEXT;
    public static final Color DIALOG_SECONDARY_BUTTON = SimpleGenomeHubStyle.DIALOG_SECONDARY_BUTTON;
    public static final Color DIALOG_SECONDARY_BUTTON_TEXT = SimpleGenomeHubStyle.DIALOG_SECONDARY_BUTTON_TEXT;
    public static final Color DIALOG_TABLE_HEADER = SimpleGenomeHubStyle.DIALOG_TABLE_HEADER;
    public static final Color MENU_BACKGROUND = SimpleGenomeHubStyle.MENU_BACKGROUND;
    public static final Color MENU_HOVER_BACKGROUND = SimpleGenomeHubStyle.MENU_HOVER_BACKGROUND;
    public static final Color MENU_BORDER = SimpleGenomeHubStyle.MENU_BORDER;
    public static final Color MENU_TEXT = SimpleGenomeHubStyle.MENU_TEXT;
    /** Foreground used for styled lists, matching the menu text colour. */
    private static final Color TEXT_PRIMARY_LIST = new Color(45, 63, 90);

    private static volatile boolean dialogStylingInstalled;
    private static volatile boolean menuStylingInstalled;

    private SimpleGenomeHubUi() {
    }

    public static void installGlobalDialogStyling() {
        if (dialogStylingInstalled) {
            return;
        }

        synchronized (SimpleGenomeHubUi.class) {
            if (dialogStylingInstalled) {
                return;
            }

            AWTEventListener listener = event -> {
                if (!(event instanceof WindowEvent)) {
                    return;
                }
                if (event.getID() != WindowEvent.WINDOW_OPENED) {
                    return;
                }

                Window window = ((WindowEvent) event).getWindow();
                if (!(window instanceof JDialog) && !(window instanceof JFrame)) {
                    return;
                }
                if (!(window instanceof RootPaneContainer)) {
                    return;
                }

                JRootPane rootPane = ((RootPaneContainer) window).getRootPane();
                if (Boolean.TRUE.equals(rootPane.getClientProperty("sgh.dialogStyled"))) {
                    return;
                }
                if (!shouldApplyGlobalDialogStyle(window)) {
                    return;
                }

                rootPane.putClientProperty("sgh.dialogStyled", Boolean.TRUE);
                if (SwingUtilities.isEventDispatchThread()) {
                    applyWindowStyle(window);
                } else {
                    SwingUtilities.invokeLater(() -> applyWindowStyle(window));
                }
            };

            Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK);
            dialogStylingInstalled = true;
        }
    }

    public static void installGlobalMenuStyling() {
        if (menuStylingInstalled) {
            return;
        }

        synchronized (SimpleGenomeHubUi.class) {
            if (menuStylingInstalled) {
                return;
            }

            UIManager.put("PopupMenu.background", MENU_BACKGROUND);
            UIManager.put("PopupMenu.foreground", MENU_TEXT);
            UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(MENU_BORDER, 1));

            UIManager.put("Menu.background", MENU_BACKGROUND);
            UIManager.put("Menu.foreground", MENU_TEXT);
            UIManager.put("Menu.selectionBackground", MENU_HOVER_BACKGROUND);
            UIManager.put("Menu.selectionForeground", TITLE_BLUE);

            UIManager.put("MenuItem.background", MENU_BACKGROUND);
            UIManager.put("MenuItem.foreground", MENU_TEXT);
            UIManager.put("MenuItem.selectionBackground", MENU_HOVER_BACKGROUND);
            UIManager.put("MenuItem.selectionForeground", TITLE_BLUE);

            UIManager.put("CheckBoxMenuItem.background", MENU_BACKGROUND);
            UIManager.put("CheckBoxMenuItem.foreground", MENU_TEXT);
            UIManager.put("CheckBoxMenuItem.selectionBackground", MENU_HOVER_BACKGROUND);
            UIManager.put("CheckBoxMenuItem.selectionForeground", TITLE_BLUE);

            UIManager.put("RadioButtonMenuItem.background", MENU_BACKGROUND);
            UIManager.put("RadioButtonMenuItem.foreground", MENU_TEXT);
            UIManager.put("RadioButtonMenuItem.selectionBackground", MENU_HOVER_BACKGROUND);
            UIManager.put("RadioButtonMenuItem.selectionForeground", TITLE_BLUE);

            UIManager.put("Separator.background", MENU_BACKGROUND);
            UIManager.put("Separator.foreground", MENU_BORDER);

            menuStylingInstalled = true;
        }
    }

    public static Border createInnerPadding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    public static void styleMenu(JMenu menu) {
        if (menu == null) {
            return;
        }

        styleMenuItem(menu);
        stylePopupMenu(menu.getPopupMenu());
    }

    public static void stylePopupMenu(JPopupMenu popupMenu) {
        if (popupMenu == null) {
            return;
        }

        popupMenu.setOpaque(true);
        popupMenu.setBackground(MENU_BACKGROUND);
        popupMenu.setForeground(MENU_TEXT);
        popupMenu.setBorder(BorderFactory.createLineBorder(MENU_BORDER, 1));

        for (Component component : popupMenu.getComponents()) {
            styleMenuComponent(component);
        }
    }

    public static JButton createSoftButton(String text, Dimension preferredSize) {
        return createSoftButton(text, preferredSize, SOFT_BUTTON_TOP, SOFT_BUTTON_BOTTOM, SOFT_BUTTON_BORDER);
    }

    public static JButton createSoftButton(String text, Dimension preferredSize,
                                           Color topColor, Color bottomColor, Color borderColor) {
        SoftButton button = new SoftButton(text, topColor, bottomColor, borderColor);
        button.setPreferredSize(preferredSize);
        button.setMinimumSize(preferredSize);
        button.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_13);
        return button;
    }

    public static void styleSplitPane(JSplitPane splitPane) {
        // Replacing the UI resets the divider location, so keep whatever the owner configured.
        int configuredDividerLocation = splitPane.getDividerLocation();
        double configuredResizeWeight = splitPane.getResizeWeight();

        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setOpaque(false);
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(24);
        splitPane.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    {
                        setBorder(BorderFactory.createEmptyBorder());
                    }

                    @Override
                    public void paint(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        try {
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            if (splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT) {
                                paintHorizontalGrip(g2);
                            } else {
                                paintVerticalGrip(g2);
                            }
                        } finally {
                            g2.dispose();
                        }
                    }

                    private void paintHorizontalGrip(Graphics2D g2) {
                        int gripWidth = Math.min(Math.max(72, getWidth() / 5), 120);
                        int gripHeight = 8;
                        int gripX = (getWidth() - gripWidth) / 2;
                        int gripY = (getHeight() - gripHeight) / 2;

                        g2.setColor(new Color(240, 246, 255, 150));
                        g2.fillRect(gripX, gripY, gripWidth, gripHeight);
                        g2.setColor(new Color(206, 219, 240, 190));
                        g2.drawRect(gripX, gripY, gripWidth - 1, gripHeight - 1);

                        g2.setColor(new Color(166, 187, 221, 220));
                        int dotY = getHeight() / 2 - 1;
                        for (int i = -2; i <= 2; i++) {
                            int dotX = getWidth() / 2 + i * 12;
                            g2.fillOval(dotX, dotY, 3, 3);
                        }
                    }

                    private void paintVerticalGrip(Graphics2D g2) {
                        int gripWidth = 8;
                        int gripHeight = Math.min(Math.max(72, getHeight() / 5), 120);
                        int gripX = (getWidth() - gripWidth) / 2;
                        int gripY = (getHeight() - gripHeight) / 2;

                        g2.setColor(new Color(240, 246, 255, 150));
                        g2.fillRect(gripX, gripY, gripWidth, gripHeight);
                        g2.setColor(new Color(206, 219, 240, 190));
                        g2.drawRect(gripX, gripY, gripWidth - 1, gripHeight - 1);

                        g2.setColor(new Color(166, 187, 221, 220));
                        int dotX = getWidth() / 2 - 1;
                        for (int i = -2; i <= 2; i++) {
                            int dotY = getHeight() / 2 + i * 12;
                            g2.fillOval(dotX, dotY, 3, 3);
                        }
                    }
                };
            }
        });

        splitPane.setResizeWeight(configuredResizeWeight);
        if (configuredDividerLocation > 0) {
            splitPane.setDividerLocation(configuredDividerLocation);
        }
    }

    private static boolean shouldApplyGlobalDialogStyle(Window window) {
        if (window == null) {
            return false;
        }

        Package windowPackage = window.getClass().getPackage();
        String packageName = windowPackage == null ? "" : windowPackage.getName();
        if (!packageName.startsWith(DIALOG_STYLE_PACKAGE_PREFIX)) {
            return false;
        }
        // The application shell lays itself out; only auxiliary windows are styled here.
        return !(window instanceof JFrame) || !containsApplicationShell((JFrame) window);
    }

    private static boolean containsApplicationShell(JFrame frame) {
        return frame.getContentPane() != null && containsApplicationShell(frame.getContentPane());
    }

    private static boolean containsApplicationShell(Component component) {
        if (component instanceof SimpleGenomeHubMainPanel) {
            return true;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (containsApplicationShell(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Applies the shared SimpleGenomeHub chrome to a dialog or frame. */
    public static void applyWindowStyle(Window window) {
        if (!(window instanceof RootPaneContainer)) {
            return;
        }
        RootPaneContainer container = (RootPaneContainer) window;
        container.getRootPane().setBorder(new LineBorder(new Color(170, 191, 220), 1));
        Container contentPane = container.getContentPane();
        contentPane.setBackground(DIALOG_BACKGROUND);

        if (contentPane instanceof JComponent) {
            ((JComponent) contentPane).setOpaque(true);
        }

        styleComponentTree(contentPane);
        window.repaint();
    }

    public static void applyDialogStyle(JDialog dialog) {
        applyWindowStyle(dialog);
    }

    private static void styleComponentTree(Component component) {
        if (component instanceof Container) {
            installDynamicStyling((Container) component);
        }

        if (component instanceof JSplitPane) {
            styleSplitPane((JSplitPane) component);
        }

        if (component instanceof JTabbedPane) {
            styleTabbedPane((JTabbedPane) component);
        }

        if (component instanceof JTable) {
            styleTable((JTable) component);
        }

        if (component instanceof JTree) {
            styleTree((JTree) component);
        }

        if (component instanceof JList) {
            styleList((JList<?>) component);
        }

        if (component instanceof JProgressBar) {
            styleProgressBar((JProgressBar) component);
        }

        if (component instanceof JScrollPane) {
            styleScrollPane((JScrollPane) component);
        }

        if (component instanceof JTextComponent) {
            styleTextComponent((JTextComponent) component);
        }

        if (component instanceof JComboBox) {
            styleComboBox((JComboBox<?>) component);
        }

        if (component instanceof JSpinner) {
            styleSpinner((JSpinner) component);
        }

        if (component instanceof JButton) {
            styleButton((JButton) component);
        }

        if (component instanceof JLabel) {
            styleLabel((JLabel) component);
        }

        if (component instanceof JComponent) {
            styleTitledBorder((JComponent) component);
            styleSurface((JComponent) component);
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                styleComponentTree(child);
            }
        }
    }

    /**
     * Widgets created after the window was styled (added rows, groups, comparisons…) would
     * otherwise keep the raw look-and-feel. Each styled container therefore styles late children.
     * Only widget-level chrome is applied, so dynamic content can never disturb window geometry.
     */
    private static void installDynamicStyling(Container container) {
        if (container instanceof JComponent) {
            JComponent component = (JComponent) container;
            if (Boolean.TRUE.equals(component.getClientProperty(DYNAMIC_STYLE_INSTALLED))) {
                return;
            }
            component.putClientProperty(DYNAMIC_STYLE_INSTALLED, Boolean.TRUE);
        }

        container.addContainerListener(new ContainerAdapter() {
            @Override
            public void componentAdded(ContainerEvent event) {
                styleAddedComponent(event.getChild());
            }
        });
    }

    private static void styleAddedComponent(Component component) {
        if (component instanceof JButton) {
            styleButton((JButton) component);
        } else if (component instanceof JLabel) {
            styleLabel((JLabel) component);
        } else if (component instanceof JTextComponent) {
            styleTextComponent((JTextComponent) component);
        } else if (component instanceof JTable) {
            styleTable((JTable) component);
        } else if (component instanceof JList) {
            styleList((JList<?>) component);
        } else if (component instanceof JProgressBar) {
            styleProgressBar((JProgressBar) component);
        } else if (component instanceof JScrollPane) {
            styleScrollPane((JScrollPane) component);
        }

        if (component instanceof JComponent) {
            // Backgrounds are layout-neutral, so late content blends in like the rest of the window.
            styleSurface((JComponent) component);
            styleTitledBorder((JComponent) component);
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                styleAddedComponent(child);
            }
        }
    }

    private static void styleSurface(JComponent component) {        if (component instanceof JPanel) {
            component.setBackground(DIALOG_PANEL_BACKGROUND);
        } else if (component instanceof JViewport) {
            component.setBackground(Color.WHITE);
        } else if (component instanceof JMenuBar) {
            component.setBackground(new Color(232, 240, 250));
        }
    }

    /** Updates the visible title even when window styling has wrapped its border. Call on the EDT. */
    public static void setTitledBorderTitle(JComponent component, String title) {
        TitledBorder titled = findTitledBorder(component.getBorder());
        if (titled == null) {
            component.setBorder(BorderFactory.createTitledBorder(component.getBorder(), title));
        } else {
            titled.setTitle(title);
        }
        component.revalidate();
        component.repaint();
    }

    private static TitledBorder findTitledBorder(Border border) {
        if (border instanceof TitledBorder) return (TitledBorder) border;
        if (border instanceof CompoundBorder) {
            CompoundBorder compound = (CompoundBorder) border;
            TitledBorder outside = findTitledBorder(compound.getOutsideBorder());
            return outside != null ? outside : findTitledBorder(compound.getInsideBorder());
        }
        return null;
    }

    private static void styleTitledBorder(JComponent component) {
        Border border = component.getBorder();
        if (!(border instanceof TitledBorder)) {
            return;
        }

        TitledBorder original = (TitledBorder) border;
        Border innerBorder = new LineBorder(CARD_BORDER, 1, true);
        TitledBorder styled = BorderFactory.createTitledBorder(innerBorder, original.getTitle());
        styled.setTitleColor(TITLE_BLUE);
        Font titleFont = original.getTitleFont();
        if (titleFont == null) {
            titleFont = component.getFont();
        }
        if (titleFont != null) {
            styled.setTitleFont(SimpleGenomeHubStyle.bold(titleFont));
        }
        styled.setTitleJustification(original.getTitleJustification());
        styled.setTitlePosition(original.getTitlePosition());

        component.setBorder(new CompoundBorder(styled, BorderFactory.createEmptyBorder(2, 2, 2, 2)));
    }

    private static void styleButton(JButton button) {
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        boolean primary = isPrimaryAction(button.getText());
        if (primary) {
            button.setBackground(DIALOG_PRIMARY_BUTTON);
            button.setForeground(DIALOG_PRIMARY_BUTTON_TEXT);
            button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DIALOG_PRIMARY_BUTTON_BORDER, 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        } else {
            button.setBackground(DIALOG_SECONDARY_BUTTON);
            button.setForeground(DIALOG_SECONDARY_BUTTON_TEXT);
            button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SOFT_BUTTON_BORDER, 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
        }
    }

    private static boolean isPrimaryAction(String text) {
        if (text == null) {
            return false;
        }

        String normalized = text.trim().toLowerCase();
        return normalized.equals("start")
            || normalized.equals("search")
            || normalized.equals("blast")
            || normalized.equals("run")
            || normalized.equals("import")
            || normalized.equals("export")
            || normalized.equals("save")
            || normalized.equals("apply")
            || normalized.equals("ok")
            || normalized.equals("execute")
            || normalized.contains("start ")
            || normalized.contains(" search")
            || normalized.contains(" import")
            || normalized.contains(" export")
            || normalized.contains(" save");
    }

    private static void styleLabel(JLabel label) {
        if (label.getFont() != null && label.getFont().isBold()) {
            label.setForeground(TITLE_BLUE);
        }
    }

    private static void styleTextComponent(JTextComponent textComponent) {
        if (textComponent.isEditable()) {
            textComponent.setBackground(Color.WHITE);
        } else {
            textComponent.setBackground(new Color(250, 252, 255));
        }

        if (!(textComponent.getParent() instanceof JViewport)) {
            textComponent.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
            ));
        }
    }

    /** Sample/entry lists share the table palette so dialogs look consistent. */
    private static void styleList(JList<?> list) {
        list.setBackground(Color.WHITE);
        list.setForeground(TEXT_PRIMARY_LIST);
        list.setSelectionBackground(new Color(255, 236, 205));
        list.setSelectionForeground(new Color(61, 55, 49));
        if (!(list.getParent() instanceof JViewport)) {
            list.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
        }
    }

    private static void styleComboBox(JComboBox<?> comboBox) {        comboBox.setBackground(Color.WHITE);
        comboBox.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));

        Dimension preferredSize = comboBox.getPreferredSize();
        int preferredWidth = preferredSize != null ? preferredSize.width : 0;
        int preferredHeight = preferredSize != null ? preferredSize.height : 0;
        int targetHeight = Math.max(COMBO_BOX_MIN_HEIGHT, preferredHeight);
        int targetWidth = preferredWidth > 0 ? preferredWidth : comboBox.getWidth();
        if (targetWidth <= 0) {
            targetWidth = 120;
        }

        Dimension adjustedSize = new Dimension(targetWidth, targetHeight);
        comboBox.setPreferredSize(adjustedSize);

        Dimension minimumSize = comboBox.getMinimumSize();
        int minimumWidth = minimumSize != null ? minimumSize.width : 0;
        comboBox.setMinimumSize(new Dimension(Math.max(minimumWidth, 80), targetHeight));
    }

    public static void setComboBoxMinimumWidth(JComboBox<?> comboBox, int minWidth) {
        if (comboBox == null || minWidth <= 0) {
            return;
        }

        Dimension preferredSize = comboBox.getPreferredSize();
        int preferredHeight = preferredSize != null ? preferredSize.height : COMBO_BOX_MIN_HEIGHT;
        int targetHeight = Math.max(COMBO_BOX_MIN_HEIGHT, preferredHeight);
        int targetWidth = Math.max(minWidth, preferredSize != null ? preferredSize.width : 0);
        comboBox.setPreferredSize(new Dimension(targetWidth, targetHeight));

        Dimension minimumSize = comboBox.getMinimumSize();
        int minimumHeight = minimumSize != null ? minimumSize.height : targetHeight;
        comboBox.setMinimumSize(new Dimension(minWidth, Math.max(COMBO_BOX_MIN_HEIGHT, minimumHeight)));
    }

    public static void setComboBoxDisplayWidth(JComboBox<?> comboBox) {
        setComboBoxDisplayWidth(comboBox, 120);
    }

    public static void setComboBoxDisplayWidth(JComboBox<?> comboBox, int minWidth) {
        if (comboBox == null) {
            return;
        }

        Font font = comboBox.getFont();
        if (font == null) {
            font = UIManager.getFont("ComboBox.font");
        }
        if (font == null) {
            font = UIManager.getFont("Label.font");
        }
        if (font == null) {
            return;
        }

        FontMetrics metrics = comboBox.getFontMetrics(font);
        if (metrics == null) {
            return;
        }

        int maxTextWidth = 0;
        ComboBoxModel<?> model = comboBox.getModel();
        if (model != null) {
            int size = model.getSize();
            for (int i = 0; i < size; i++) {
                Object item = model.getElementAt(i);
                if (item != null) {
                    maxTextWidth = Math.max(maxTextWidth, metrics.stringWidth(item.toString()));
                }
            }
        }

        if (maxTextWidth == 0) {
            Object selectedItem = comboBox.getSelectedItem();
            if (selectedItem != null) {
                maxTextWidth = metrics.stringWidth(selectedItem.toString());
            }
        }

        int targetWidth = Math.max(minWidth, maxTextWidth + COMBO_BOX_HORIZONTAL_PADDING);
        targetWidth = Math.min(targetWidth, COMBO_BOX_MAX_AUTO_WIDTH);
        setComboBoxMinimumWidth(comboBox, targetWidth);
    }

    private static void styleSpinner(JSpinner spinner) {
        spinner.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
        Component editor = spinner.getEditor();
        if (editor instanceof JComponent) {
            ((JComponent) editor).setBorder(BorderFactory.createEmptyBorder());
        }
    }

    private static void styleScrollPane(JScrollPane scrollPane) {
        Border border = scrollPane.getBorder();
        if (!(border instanceof TitledBorder)) {
            scrollPane.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
        }

        if (scrollPane.getViewport() != null) {
            scrollPane.getViewport().setBackground(Color.WHITE);
        }
    }

    private static void styleTable(JTable table) {
        table.setGridColor(new Color(223, 231, 242));
        table.setSelectionBackground(new Color(255, 236, 205));
        table.setSelectionForeground(new Color(61, 55, 49));

        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setBackground(DIALOG_TABLE_HEADER);
            header.setForeground(TITLE_BLUE);
            header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER));
        }
    }

    private static void styleTree(JTree tree) {
        tree.setBackground(Color.WHITE);
        tree.setRowHeight(Math.max(tree.getRowHeight(), 22));
    }

    private static void styleProgressBar(JProgressBar progressBar) {
        progressBar.setBackground(new Color(236, 241, 249));
        progressBar.setForeground(new Color(92, 143, 205));
        progressBar.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
    }

    private static void styleTabbedPane(JTabbedPane tabbedPane) {
        tabbedPane.setBackground(DIALOG_PANEL_BACKGROUND);
        tabbedPane.setForeground(TITLE_BLUE);
    }

    private static void styleMenuComponent(Component component) {
        if (component instanceof JMenu) {
            JMenu menu = (JMenu) component;
            styleMenuItem(menu);
            stylePopupMenu(menu.getPopupMenu());
            return;
        }

        if (component instanceof JMenuItem) {
            styleMenuItem((JMenuItem) component);
            return;
        }

        if (component instanceof JSeparator) {
            component.setBackground(MENU_BACKGROUND);
            component.setForeground(MENU_BORDER);
        }
    }

    private static void styleMenuItem(JMenuItem menuItem) {
        menuItem.setOpaque(true);
        menuItem.setBackground(MENU_BACKGROUND);

        if (menuItem.isEnabled()) {
            menuItem.setForeground(MENU_TEXT);
        } else {
            menuItem.setForeground(TITLE_BLUE);
        }

        if (menuItem.getFont() != null) {
            menuItem.setFont(SimpleGenomeHubStyle.withStyle(
                menuItem.getFont(),
                menuItem.getFont().isBold() ? Font.BOLD : Font.PLAIN
            ));
        }
    }

    public static class RoundedPanel extends JPanel {
        private final Color fillColor;
        private final Color borderColor;
        private final Color shadowColor;
        private final int radius;
        private final int shadowOffset;

        public RoundedPanel(LayoutManager layout, Color fillColor, Color borderColor, int radius) {
            super(layout);
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            this.radius = radius;
            this.shadowColor = new Color(120, 141, 169, 28);
            this.shadowOffset = 3;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidth() - 1;
                int height = getHeight() - shadowOffset - 1;
                if (width <= 0 || height <= 0) {
                    return;
                }

                g2.setColor(shadowColor);
                g2.fillRect(1, shadowOffset, width - 1, height);

                g2.setColor(fillColor);
                g2.fillRect(0, 0, width, height);

                g2.setColor(borderColor);
                g2.drawRect(0, 0, width, height);
            } finally {
                g2.dispose();
            }

            super.paintComponent(g);
        }
    }

    private static final class SoftButton extends JButton {
        private final Color topColor;
        private final Color bottomColor;
        private final Color borderColor;

        private SoftButton(String text, Color topColor, Color bottomColor, Color borderColor) {
            super(text);
            this.topColor = topColor;
            this.bottomColor = bottomColor;
            this.borderColor = borderColor;

            setOpaque(false);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
            setForeground(new Color(53, 69, 92));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color start = topColor;
                Color end = bottomColor;
                Color border = borderColor;

                ButtonModel model = getModel();
                if (!isEnabled()) {
                    start = new Color(244, 246, 249);
                    end = new Color(231, 235, 241);
                    border = new Color(203, 210, 220);
                } else if (model.isPressed()) {
                    start = bottomColor;
                    end = topColor;
                } else if (model.isRollover()) {
                    start = blend(topColor, Color.WHITE, 0.55f);
                    end = blend(bottomColor, Color.WHITE, 0.25f);
                }

                g2.setPaint(new GradientPaint(0, 0, start, 0, getHeight(), end));
                g2.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
                g2.setColor(border);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            } finally {
                g2.dispose();
            }

            super.paintComponent(g);
        }

        private Color blend(Color base, Color mix, float ratio) {
            float safeRatio = Math.max(0f, Math.min(1f, ratio));
            int red = Math.round(base.getRed() * (1f - safeRatio) + mix.getRed() * safeRatio);
            int green = Math.round(base.getGreen() * (1f - safeRatio) + mix.getGreen() * safeRatio);
            int blue = Math.round(base.getBlue() * (1f - safeRatio) + mix.getBlue() * safeRatio);
            return new Color(red, green, blue);
        }
    }
}
