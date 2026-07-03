package simplegenomehub.gui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;

public final class CollapsibleSectionPanel extends JPanel {

    private final String title;
    private final JToggleButton headerButton;
    private final JPanel contentWrapper;
    private boolean expanded;

    public CollapsibleSectionPanel(String title, boolean expanded) {
        this.title = title == null ? "" : title;
        setLayout(new BorderLayout(0, 6));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        headerButton = new JToggleButton();
        headerButton.setOpaque(true);
        headerButton.setFocusPainted(false);
        headerButton.setHorizontalAlignment(JToggleButton.LEFT);
        headerButton.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        headerButton.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        headerButton.addActionListener(e -> setExpanded(headerButton.isSelected()));
        add(headerButton, BorderLayout.NORTH);

        contentWrapper = new JPanel();
        contentWrapper.setOpaque(false);
        contentWrapper.setLayout(new BoxLayout(contentWrapper, BoxLayout.Y_AXIS));
        contentWrapper.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        add(contentWrapper, BorderLayout.CENTER);

        setExpanded(expanded);
    }

    public void setContent(JComponent content) {
        contentWrapper.removeAll();
        if (content != null) {
            content.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentWrapper.add(content);
        }
        contentWrapper.revalidate();
        contentWrapper.repaint();
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        applyState();
    }

    public void setHeaderEnabled(boolean enabled) {
        headerButton.setEnabled(enabled);
        if (!enabled) {
            expanded = false;
        }
        applyState();
    }

    private void applyState() {
        headerButton.setSelected(expanded);
        headerButton.setText((expanded ? "\u25be " : "\u25b8 ") + title);
        if (headerButton.isEnabled()) {
            headerButton.setBackground(expanded ? new Color(239, 244, 252) : new Color(247, 249, 252));
            headerButton.setForeground(SimpleGenomeHubUi.TITLE_BLUE);
        } else {
            headerButton.setBackground(new Color(241, 241, 241));
            headerButton.setForeground(new Color(145, 145, 145));
        }
        contentWrapper.setVisible(expanded && headerButton.isEnabled());
        revalidate();
        repaint();
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension preferredSize = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, preferredSize.height);
    }
}
