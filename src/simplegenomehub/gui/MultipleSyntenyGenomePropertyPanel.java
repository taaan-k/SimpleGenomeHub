package simplegenomehub.gui;

import simplegenomehub.util.MultipleSynteny.MultipleSyntenyAnchorMode;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenyGenomeModel;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.geom.Point2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Right-side property panel for the selected genome bar.
 */
public class MultipleSyntenyGenomePropertyPanel extends JPanel {

    private static final int TEXT_AREA_HEIGHT = 104;
    private static final Color TEXT_AREA_BACKGROUND = new Color(246, 248, 251);

    private final GenomePropertyListener listener;
    private final JComponent highlightsPanel;

    private JTextField nameField;
    private JTextArea chromosomeArea;
    private JToggleButton leftAnchorButton;
    private JToggleButton centerAnchorButton;
    private JToggleButton rightAnchorButton;
    private JTextField xField;
    private JTextField yField;
    private JTextField rotationField;
    private JLabel emptyHintLabel;
    private CollapsibleSectionPanel layoutControlsSection;
    private int positionOffsetX;
    private int positionOffsetY;

    private MultipleSyntenyGenomeModel currentGenome;
    private boolean updating;

    public MultipleSyntenyGenomePropertyPanel(JComponent highlightsPanel, GenomePropertyListener listener) {
        this.highlightsPanel = highlightsPanel;
        this.listener = listener;
        initializeGui();
        setGenome(null);
    }

    private void initializeGui() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Genome Properties"));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 680));

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 6, 0);

        content.add(createSectionLabel("Name"), gbc);

        gbc.gridy++;
        nameField = new JTextField();
        nameField.setEditable(false);
        content.add(nameField, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(12, 0, 6, 0);
        content.add(createSectionLabel("Chromosome"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 6, 0);
        chromosomeArea = new JTextArea(4, 20);
        chromosomeArea.setLineWrap(false);
        chromosomeArea.setWrapStyleWord(false);
        chromosomeArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        chromosomeArea.setBackground(TEXT_AREA_BACKGROUND);
        JScrollPane chromosomeScrollPane = new JScrollPane(chromosomeArea);
        chromosomeScrollPane.getViewport().setBackground(TEXT_AREA_BACKGROUND);
        chromosomeScrollPane.setBorder(BorderFactory.createLineBorder(new Color(207, 214, 224), 1));
        chromosomeScrollPane.setPreferredSize(new Dimension(0, TEXT_AREA_HEIGHT));
        chromosomeScrollPane.setMinimumSize(new Dimension(0, TEXT_AREA_HEIGHT));
        content.add(chromosomeScrollPane, gbc);

        if (highlightsPanel != null) {
            gbc.gridy++;
            gbc.insets = new Insets(16, 0, 6, 0);
            content.add(createSectionLabel("Highlights"), gbc);

            gbc.gridy++;
            gbc.insets = new Insets(0, 0, 0, 0);
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;
            highlightsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(highlightsPanel, gbc);
            gbc.weighty = 0.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
        }

        gbc.gridy++;
        gbc.insets = new Insets(10, 0, 0, 0);
        emptyHintLabel = new JLabel("Select a genome bar in the layout workspace to edit it.");
        emptyHintLabel.setFont(SimpleGenomeHubStyle.italic(emptyHintLabel.getFont(), 11f));
        emptyHintLabel.setForeground(new Color(118, 128, 142));
        content.add(emptyHintLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(16, 0, 0, 0);
        layoutControlsSection = new CollapsibleSectionPanel("Layout Controls", false);
        layoutControlsSection.setContent(createLayoutControlsPanel());
        content.add(layoutControlsSection, gbc);

        add(content, BorderLayout.CENTER);
        installListeners();
    }

    private JLabel createSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        return label;
    }

    private JPanel createAnchorSelector() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 8, 0));
        panel.setOpaque(false);

        leftAnchorButton = createAnchorButton("L", MultipleSyntenyAnchorMode.LEFT);
        centerAnchorButton = createAnchorButton("C", MultipleSyntenyAnchorMode.CENTER);
        rightAnchorButton = createAnchorButton("R", MultipleSyntenyAnchorMode.RIGHT);

        ButtonGroup group = new ButtonGroup();
        group.add(leftAnchorButton);
        group.add(centerAnchorButton);
        group.add(rightAnchorButton);

        panel.add(leftAnchorButton);
        panel.add(centerAnchorButton);
        panel.add(rightAnchorButton);
        return panel;
    }

    private JPanel createLayoutControlsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 6, 0);

        panel.add(createSectionLabel("Center Select"), gbc);

        gbc.gridy++;
        panel.add(createAnchorSelector(), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(12, 0, 6, 0);
        panel.add(createSectionLabel("Position"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 6, 0);
        panel.add(createPositionPanel(), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(12, 0, 6, 0);
        panel.add(createSectionLabel("Rotation"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 0, 0);
        rotationField = new JTextField();
        panel.add(rotationField, gbc);
        return panel;
    }

    private JToggleButton createAnchorButton(String text, MultipleSyntenyAnchorMode mode) {
        JToggleButton button = new JToggleButton(text);
        button.putClientProperty("anchorMode", mode);
        button.setFocusPainted(false);
        button.setMinimumSize(new Dimension(0, 36));
        button.setPreferredSize(new Dimension(0, 36));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        button.setOpaque(true);
        button.setBorder(BorderFactory.createLineBorder(SimpleGenomeHubUi.SOFT_BUTTON_BORDER, 1));
        button.addActionListener(e -> {
            if (updating || listener == null) {
                return;
            }
            listener.onAnchorModeChanged(mode);
            updateAnchorButtonStyles();
        });
        return button;
    }

    private JPanel createPositionPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 8, 0));
        panel.setOpaque(false);
        xField = new JTextField();
        yField = new JTextField();
        panel.add(wrapField("X", xField));
        panel.add(wrapField("Y", yField));
        return panel;
    }

    private JPanel wrapField(String labelText, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_11);
        panel.add(label, BorderLayout.NORTH);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    private void installListeners() {
        chromosomeArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                previewChromosomes();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                previewChromosomes();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                previewChromosomes();
            }
        });
        chromosomeArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    commitChromosomes();
                }
            }
        });
        chromosomeArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitChromosomes();
            }
        });

        installCommitBehavior(xField);
        installCommitBehavior(yField);

        rotationField.addActionListener(e -> commitRotation());
        rotationField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitRotation();
            }
        });
    }

    private void installCommitBehavior(JTextField field) {
        field.addActionListener(e -> commitPosition());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitPosition();
            }
        });
    }

    public void setGenome(MultipleSyntenyGenomeModel genome) {
        MultipleSyntenyGenomeModel previousGenome = currentGenome;
        currentGenome = genome;
        updating = true;
        try {
            boolean enabled = genome != null;
            nameField.setEnabled(enabled);
            chromosomeArea.setEnabled(enabled);
            leftAnchorButton.setEnabled(enabled);
            centerAnchorButton.setEnabled(enabled);
            rightAnchorButton.setEnabled(enabled);
            xField.setEnabled(enabled);
            yField.setEnabled(enabled);
            rotationField.setEnabled(enabled);
            emptyHintLabel.setVisible(!enabled);

            if (!enabled) {
                nameField.setText("");
                chromosomeArea.setText("");
                xField.setText("");
                yField.setText("");
                rotationField.setText("");
                leftAnchorButton.setSelected(false);
                centerAnchorButton.setSelected(false);
                rightAnchorButton.setSelected(false);
                return;
            }

            nameField.setText(genome.getDisplayName());
            if (!shouldPreserveChromosomeEditing(previousGenome, genome)) {
                chromosomeArea.setText(genome.getChromosomeText());
            }

            MultipleSyntenyAnchorMode anchorMode = genome.getAnchorMode();
            leftAnchorButton.setSelected(anchorMode == MultipleSyntenyAnchorMode.LEFT);
            centerAnchorButton.setSelected(anchorMode == MultipleSyntenyAnchorMode.CENTER);
            rightAnchorButton.setSelected(anchorMode == MultipleSyntenyAnchorMode.RIGHT);

            updateGeometryFieldsInternal(genome);
        } finally {
            updating = false;
            updateAnchorButtonStyles();
        }
    }

    public void refreshGeometryFields(MultipleSyntenyGenomeModel genome) {
        if (genome == null || genome != currentGenome) {
            return;
        }

        updating = true;
        try {
            updateGeometryFieldsInternal(genome);
        } finally {
            updating = false;
        }
    }

    public void setPositionDisplayOffset(int offsetX, int offsetY) {
        positionOffsetX = Math.max(0, offsetX);
        positionOffsetY = Math.max(0, offsetY);
        if (currentGenome != null) {
            refreshGeometryFields(currentGenome);
        }
    }

    private boolean shouldPreserveChromosomeEditing(MultipleSyntenyGenomeModel previousGenome,
                                                    MultipleSyntenyGenomeModel nextGenome) {
        return previousGenome != null
            && previousGenome == nextGenome
            && chromosomeArea != null
            && chromosomeArea.isFocusOwner();
    }

    private void updateGeometryFieldsInternal(MultipleSyntenyGenomeModel genome) {
        Point2D.Double anchorPoint = genome.getSelectedAnchorPoint();
        xField.setText(String.valueOf((int) Math.round(anchorPoint.x - positionOffsetX)));
        yField.setText(String.valueOf((int) Math.round(anchorPoint.y - positionOffsetY)));
        rotationField.setText(String.valueOf(genome.getRotation()));
    }

    private void previewChromosomes() {
        if (updating || listener == null || currentGenome == null) {
            return;
        }
        listener.onChromosomesChanged(chromosomeArea.getText());
    }

    private void commitChromosomes() {
        if (updating || currentGenome == null) {
            return;
        }

        updating = true;
        try {
            chromosomeArea.setText(currentGenome.getChromosomeText());
        } finally {
            updating = false;
        }
    }

    private void commitPosition() {
        if (updating || listener == null || currentGenome == null) {
            return;
        }

        Integer x = parseInteger(xField.getText());
        Integer y = parseInteger(yField.getText());
        if (x == null || y == null) {
            setGenome(currentGenome);
            return;
        }
        listener.onAnchorPositionChanged(x + positionOffsetX, y + positionOffsetY);
    }

    private void commitRotation() {
        if (updating || listener == null || currentGenome == null) {
            return;
        }

        Integer rotation = parseInteger(rotationField.getText());
        if (rotation == null) {
            setGenome(currentGenome);
            return;
        }
        listener.onRotationChanged(rotation);
    }

    private Integer parseInteger(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void updateAnchorButtonStyles() {
        styleAnchorButton(leftAnchorButton);
        styleAnchorButton(centerAnchorButton);
        styleAnchorButton(rightAnchorButton);
    }

    private void styleAnchorButton(AbstractButton button) {
        if (button == null) {
            return;
        }
        if (button.isSelected()) {
            button.setBackground(SimpleGenomeHubUi.DIALOG_PRIMARY_BUTTON);
            button.setForeground(Color.WHITE);
        } else {
            button.setBackground(SimpleGenomeHubUi.DIALOG_SECONDARY_BUTTON);
            button.setForeground(SimpleGenomeHubUi.DIALOG_SECONDARY_BUTTON_TEXT);
        }
    }

    public interface GenomePropertyListener {
        void onAnchorModeChanged(MultipleSyntenyAnchorMode anchorMode);

        void onAnchorPositionChanged(int x, int y);

        void onRotationChanged(int rotation);

        void onChromosomesChanged(String chromosomeText);
    }
}
