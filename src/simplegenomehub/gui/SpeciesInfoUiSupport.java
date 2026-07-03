package simplegenomehub.gui;

import javax.swing.*;
import java.awt.*;

final class SpeciesInfoUiSupport {

    private SpeciesInfoUiSupport() {
    }

    static JPanel createSectionCard(String title, Color titleColor, Color fillColor,
                                    Color borderColor, JComponent content) {
        SimpleGenomeHubUi.RoundedPanel card = new SimpleGenomeHubUi.RoundedPanel(
            new BorderLayout(0, 0),
            fillColor,
            borderColor,
            26
        );
        card.setBorder(SimpleGenomeHubUi.createInnerPadding(14, 16, 16, 16));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(SimpleGenomeHubUi.createInnerPadding(0, 0, 12, 0));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(titleColor);
        titleLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_19);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        card.add(headerPanel, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    static JPanel createContainerCard(Color fillColor, Color borderColor, JComponent content) {
        SimpleGenomeHubUi.RoundedPanel card = new SimpleGenomeHubUi.RoundedPanel(
            new BorderLayout(),
            fillColor,
            borderColor,
            26
        );
        card.setBorder(SimpleGenomeHubUi.createInnerPadding(12, 12, 12, 12));
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    static JScrollPane createTextScrollPane(JTextArea textArea, Color borderColor, int preferredHeight) {
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setBorder(BorderFactory.createLineBorder(borderColor));
        scrollPane.setPreferredSize(new Dimension(260, preferredHeight));
        ModernScrollBarStyle.applyTo(scrollPane);
        return scrollPane;
    }

    static JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_15);
        label.setForeground(new Color(73, 82, 97));
        return label;
    }

    static JTextField createInputField() {
        JTextField field = new JTextField();
        field.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_14);
        field.setForeground(new Color(76, 84, 99));
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 224, 241)),
            SimpleGenomeHubUi.createInnerPadding(6, 10, 6, 10)
        ));
        return field;
    }

    static JComponent createSolidAccentBar(Color color) {
        JPanel accentBar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setColor(color);
                    int lineHeight = 4;
                    int y = Math.max(0, (getHeight() - lineHeight) / 2);
                    g2.fillRect(0, y, getWidth(), lineHeight);
                } finally {
                    g2.dispose();
                }
            }
        };
        accentBar.setOpaque(false);
        accentBar.setPreferredSize(new Dimension(10, 4));
        accentBar.setMinimumSize(new Dimension(80, 4));
        return accentBar;
    }
}
