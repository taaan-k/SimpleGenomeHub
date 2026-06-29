package simplegenomehub.gui;

import simplegenomehub.util.MultipleSynteny.MultipleSyntenyLinkModel;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenyLinkRouteMode;
import simplegenomehub.util.fileio.GenomeCompareExistingResultScanner;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.util.ArrayList;
import java.util.List;

/**
 * Combined right-side Link and global Settings sections for the Multiple Synteny workspace.
 */
public class MultipleSyntenyLinkAndSettingsPanel extends JPanel {

    private final LinkAndSettingsListener listener;

    private JPanel linkCardsContainer;
    private JTextField routingBoxHalfHeightField;
    private JTextField gapLengthField;
    private JTextField canvasWidthField;
    private JTextField canvasHeightField;
    private JTextField equalLengthSizeField;
    private JTextField minimumGenomeLengthField;
    private JCheckBox equalLengthCheckBox;

    private List<MultipleSyntenyLinkModel> currentLinks;
    private MultipleSyntenyLinkModel selectedLink;
    private boolean updating;

    public MultipleSyntenyLinkAndSettingsPanel(LinkAndSettingsListener listener) {
        this.listener = listener;
        this.currentLinks = new ArrayList<>();
        initializeGui();
    }

    private void initializeGui() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);

        add(createLinkSection());
        add(Box.createVerticalStrut(12));
        add(createGlobalSettingsSection());
    }

    private JPanel createLinkSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder("Link Properties"));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 760));

        linkCardsContainer = new JPanel();
        linkCardsContainer.setOpaque(false);
        linkCardsContainer.setLayout(new BoxLayout(linkCardsContainer, BoxLayout.Y_AXIS));
        linkCardsContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));

        rebuildLinkCards();
        panel.add(linkCardsContainer);
        return panel;
    }

    private CollapsibleSectionPanel createGlobalSettingsSection() {
        CollapsibleSectionPanel sectionPanel = new CollapsibleSectionPanel("Global Settings", false);
        sectionPanel.setContent(createGlobalSettingsContentPanel());
        return sectionPanel;
    }

    private JPanel createGlobalSettingsContentPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(8, 8, 6, 8);

        panel.add(new JLabel("Box Height"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        routingBoxHalfHeightField = new JTextField();
        panel.add(routingBoxHalfHeightField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Gap Length"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gapLengthField = new JTextField();
        panel.add(gapLengthField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Canvas Width"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        canvasWidthField = new JTextField();
        panel.add(canvasWidthField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Canvas Height"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        canvasHeightField = new JTextField();
        panel.add(canvasHeightField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 1;
        equalLengthCheckBox = new JCheckBox("Equal Genome Length");
        equalLengthCheckBox.setOpaque(false);
        equalLengthCheckBox.addActionListener(e -> {
            updateLengthFieldEnabledState(equalLengthCheckBox.isSelected());
            if (updating || listener == null) {
                return;
            }
            listener.onEqualLengthChanged(equalLengthCheckBox.isSelected());
        });
        panel.add(equalLengthCheckBox, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        equalLengthSizeField = new JTextField();
        panel.add(equalLengthSizeField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(8, 8, 6, 8);
        panel.add(new JLabel("Minimum Genome Length"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        minimumGenomeLengthField = new JTextField();
        panel.add(minimumGenomeLengthField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(2, 8, 8, 8);
        JLabel hintLabel = new JLabel("Box Height sets the routing range for link edge detection.");
        hintLabel.setFont(SimpleGenomeHubStyle.italic(hintLabel.getFont(), 10f));
        hintLabel.setForeground(new Color(118, 128, 142));
        panel.add(hintLabel, gbc);

        installNumericCommit(routingBoxHalfHeightField, ValueType.ROUTING_BOX_HALF_HEIGHT);
        installNumericCommit(gapLengthField, ValueType.GAP_LENGTH);
        installNumericCommit(canvasWidthField, ValueType.CANVAS_WIDTH);
        installNumericCommit(canvasHeightField, ValueType.CANVAS_HEIGHT);
        installNumericCommit(equalLengthSizeField, ValueType.EQUAL_LENGTH_SIZE);
        installNumericCommit(minimumGenomeLengthField, ValueType.MINIMUM_GENOME_LENGTH);
        return panel;
    }

    private CollapsibleSectionPanel createRunSettingsSection(MultipleSyntenyLinkModel linkModel) {
        CollapsibleSectionPanel sectionPanel = new CollapsibleSectionPanel("Run Settings", false);
        sectionPanel.setContent(createRunSettingsContentPanel(linkModel));
        sectionPanel.setHeaderEnabled(!linkModel.usesExistingResult());
        return sectionPanel;
    }

    private JPanel createRunSettingsContentPanel(MultipleSyntenyLinkModel linkModel) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 0, 6, 8);

        panel.add(new JLabel("Link Mode"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JComboBox<MultipleSyntenyLinkRouteMode> routeModeCombo = new JComboBox<>(MultipleSyntenyLinkRouteMode.values());
        routeModeCombo.setSelectedItem(linkModel.getRouteMode());
        routeModeCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
            javax.swing.JLabel label = new javax.swing.JLabel(routeModeLabel(value));
            label.setOpaque(isSelected);
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });
        routeModeCombo.addActionListener(e -> {
            if (updating || listener == null) {
                return;
            }
            Object sel = routeModeCombo.getSelectedItem();
            if (!(sel instanceof MultipleSyntenyLinkRouteMode)) {
                return;
            }
            notifyLinkSelected(linkModel);
            listener.onRouteModeChanged(linkModel, (MultipleSyntenyLinkRouteMode) sel);
        });
        panel.add(routeModeCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Bend Height"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField bendHeightField = new JTextField(String.valueOf(linkModel.getBendHeight()));
        panel.add(bendHeightField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        panel.add(new JLabel("CPU for similarity search"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField cpuField = new JTextField(String.valueOf(linkModel.getRunCpu()));
        panel.add(cpuField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        panel.add(new JLabel("E-value"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField evalueField = new JTextField(String.valueOf(linkModel.getRunEvalue()));
        panel.add(evalueField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.0;
        panel.add(new JLabel("Num of hits"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        JTextField numHitsField = new JTextField(String.valueOf(linkModel.getRunNumHits()));
        panel.add(numHitsField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        JCheckBox directPlotCheckBox = new JCheckBox("Direct Plot");
        directPlotCheckBox.setOpaque(false);
        directPlotCheckBox.setSelected(linkModel.isRunDirectPlot());
        directPlotCheckBox.addActionListener(e -> {
            if (updating || listener == null) {
                return;
            }
            notifyLinkSelected(linkModel);
            listener.onRunDirectPlotChanged(linkModel, directPlotCheckBox.isSelected());
        });
        panel.add(directPlotCheckBox, gbc);

        installNumericCommit(bendHeightField, ValueType.BEND_HEIGHT, linkModel);
        installNumericCommit(cpuField, ValueType.RUN_CPU, linkModel);
        installNumericCommit(numHitsField, ValueType.RUN_NUM_HITS, linkModel);
        installDoubleCommit(evalueField, ValueType.RUN_EVALUE, linkModel);
        return panel;
    }

    private static String routeModeLabel(MultipleSyntenyLinkRouteMode mode) {
        if (mode == null) {
            return "";
        }
        switch (mode) {
            case FORCE_SINGLE: return "Single Arc";
            case FORCE_DOUBLE: return "Double Arc";
            default:           return "Auto";
        }
    }

    public void updateLinkRouteMode(MultipleSyntenyLinkModel linkModel) {
        rebuildLinkCards();
    }

    public void setLinks(List<MultipleSyntenyLinkModel> links, MultipleSyntenyLinkModel selectedLink) {
        this.currentLinks = links == null ? new ArrayList<>() : new ArrayList<>(links);
        this.selectedLink = selectedLink;
        rebuildLinkCards();
    }

    public void setVisualSettings(int routingBoxHalfHeight, int gapLength,
                                  int canvasWidth, int canvasHeight,
                                  boolean equalLength, int equalLengthSize,
                                  int minimumGenomeLength) {
        updating = true;
        try {
            routingBoxHalfHeightField.setText(String.valueOf(routingBoxHalfHeight));
            gapLengthField.setText(String.valueOf(gapLength));
            canvasWidthField.setText(String.valueOf(canvasWidth));
            canvasHeightField.setText(String.valueOf(canvasHeight));
            equalLengthCheckBox.setSelected(equalLength);
            equalLengthSizeField.setText(String.valueOf(equalLengthSize));
            minimumGenomeLengthField.setText(String.valueOf(minimumGenomeLength));
            updateLengthFieldEnabledState(equalLength);
        } finally {
            updating = false;
        }
    }

    private void updateLengthFieldEnabledState(boolean equalLength) {
        if (equalLengthSizeField != null) {
            equalLengthSizeField.setEnabled(equalLength);
        }
        if (minimumGenomeLengthField != null) {
            minimumGenomeLengthField.setEnabled(!equalLength);
        }
    }

    private void rebuildLinkCards() {
        if (linkCardsContainer == null) {
            return;
        }

        linkCardsContainer.removeAll();
        if (currentLinks.isEmpty()) {
            JPanel emptyPanel = new JPanel(new BorderLayout(0, 8));
            emptyPanel.setOpaque(false);

            JLabel emptyLabel = new JLabel("No links yet. Right-click two genomes in the layout workspace to add one.");
            emptyLabel.setFont(SimpleGenomeHubStyle.italic(emptyLabel.getFont(), 11f));
            emptyLabel.setForeground(new Color(118, 128, 142));
            emptyLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
            emptyPanel.add(emptyLabel, BorderLayout.CENTER);
            linkCardsContainer.add(emptyPanel);
        } else {
            for (int i = 0; i < currentLinks.size(); i++) {
                MultipleSyntenyLinkModel linkModel = currentLinks.get(i);
                JPanel card = createLinkCard(linkModel, linkModel == selectedLink);
                card.setAlignmentX(Component.LEFT_ALIGNMENT);
                linkCardsContainer.add(card);
                if (i < currentLinks.size() - 1) {
                    linkCardsContainer.add(Box.createVerticalStrut(8));
                }
            }
        }

        linkCardsContainer.revalidate();
        linkCardsContainer.repaint();
    }

    private JPanel createLinkCard(MultipleSyntenyLinkModel linkModel, boolean selected) {
        Color fill = selected ? new Color(255, 244, 225) : Color.WHITE;
        Color border = selected ? new Color(225, 151, 63) : SimpleGenomeHubUi.CARD_BORDER;

        JPanel card = new SimpleGenomeHubUi.RoundedPanel(new BorderLayout(0, 10), fill, border, 18);
        card.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel titleLabel = new JLabel(linkModel.getDisplayLabel());
        titleLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);

        JComboBox<LinkChoiceItem> linkTypeCombo = new JComboBox<>();
        for (LinkChoiceItem choiceItem : buildLinkChoiceItems(linkModel)) {
            linkTypeCombo.addItem(choiceItem);
        }
        linkTypeCombo.setSelectedItem(resolveSelectedChoice(linkModel));
        linkTypeCombo.addActionListener(e -> {
            if (updating || listener == null) {
                return;
            }
            Object selectedItem = linkTypeCombo.getSelectedItem();
            if (!(selectedItem instanceof LinkChoiceItem)) {
                return;
            }
            notifyLinkSelected(linkModel);
            listener.onLinkSelectionChanged(linkModel, ((LinkChoiceItem) selectedItem).reusableResult);
        });
        SimpleGenomeHubUi.setComboBoxMinimumWidth(linkTypeCombo, 170);

        JButton removeButton = new JButton("Remove");
        removeButton.setPreferredSize(new Dimension(88, 28));
        removeButton.addActionListener(e -> {
            if (listener != null) {
                listener.onLinkRemoved(linkModel);
            }
        });

        JPanel topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.setOpaque(false);
        topPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel rowPanel = new JPanel(new BorderLayout(8, 0));
        rowPanel.setOpaque(false);
        rowPanel.add(linkTypeCombo, BorderLayout.CENTER);
        rowPanel.add(removeButton, BorderLayout.EAST);
        topPanel.add(rowPanel, BorderLayout.CENTER);
        card.add(topPanel, BorderLayout.NORTH);

        CollapsibleSectionPanel runSettingsPanel = createRunSettingsSection(linkModel);
        card.add(runSettingsPanel, BorderLayout.CENTER);

        MouseAdapter selectionListener = new MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (listener != null) {
                    listener.onLinkSelected(linkModel);
                }
            }
        };
        card.addMouseListener(selectionListener);
        topPanel.addMouseListener(selectionListener);
        titleLabel.addMouseListener(selectionListener);
        rowPanel.addMouseListener(selectionListener);
        runSettingsPanel.addMouseListener(selectionListener);
        return card;
    }

    private void installNumericCommit(JTextField field, ValueType valueType) {
        installNumericCommit(field, valueType, null);
    }

    private void installNumericCommit(JTextField field, ValueType valueType, MultipleSyntenyLinkModel linkModel) {
        field.addActionListener(e -> commitNumericValue(field, valueType, linkModel));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitNumericValue(field, valueType, linkModel);
            }
        });
    }

    private void installDoubleCommit(JTextField field, ValueType valueType, MultipleSyntenyLinkModel linkModel) {
        field.addActionListener(e -> commitDoubleValue(field, valueType, linkModel));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitDoubleValue(field, valueType, linkModel);
            }
        });
    }

    private void commitNumericValue(JTextField field, ValueType valueType, MultipleSyntenyLinkModel linkModel) {
        if (updating || listener == null) {
            return;
        }

        Integer value = parseInteger(field.getText());
        if (value == null) {
            return;
        }

        int normalized = Math.max(0, value);
        if (valueType == ValueType.ROUTING_BOX_HALF_HEIGHT) {
            listener.onRoutingBoxHalfHeightChanged(Math.max(1, normalized));
        } else if (valueType == ValueType.GAP_LENGTH) {
            listener.onGapLengthChanged(normalized);
        } else if (valueType == ValueType.CANVAS_WIDTH) {
            listener.onCanvasWidthChanged(Math.max(1, normalized));
        } else if (valueType == ValueType.CANVAS_HEIGHT) {
            listener.onCanvasHeightChanged(Math.max(1, normalized));
        } else if (valueType == ValueType.EQUAL_LENGTH_SIZE) {
            listener.onEqualLengthSizeChanged(Math.max(1, normalized));
        } else if (valueType == ValueType.MINIMUM_GENOME_LENGTH) {
            listener.onMinimumGenomeLengthChanged(Math.max(1, normalized));
        } else if (valueType == ValueType.RUN_CPU) {
            notifyLinkSelected(linkModel);
            listener.onRunCpuChanged(linkModel, Math.max(1, normalized));
        } else if (valueType == ValueType.RUN_NUM_HITS) {
            notifyLinkSelected(linkModel);
            listener.onRunNumHitsChanged(linkModel, Math.max(1, normalized));
        } else if (valueType == ValueType.BEND_HEIGHT) {
            notifyLinkSelected(linkModel);
            listener.onBendHeightChanged(linkModel, Math.max(0, normalized));
        }
    }

    private void commitDoubleValue(JTextField field, ValueType valueType, MultipleSyntenyLinkModel linkModel) {
        if (updating || listener == null) {
            return;
        }

        Double value = parseDouble(field.getText());
        if (value == null) {
            return;
        }
        if (valueType == ValueType.RUN_EVALUE) {
            notifyLinkSelected(linkModel);
            listener.onRunEvalueChanged(linkModel, Math.max(Double.MIN_VALUE, value));
        }
    }

    private void notifyLinkSelected(MultipleSyntenyLinkModel linkModel) {
        if (listener != null && linkModel != null) {
            listener.onLinkSelected(linkModel);
        }
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

    private Double parseDouble(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private enum ValueType {
        ROUTING_BOX_HALF_HEIGHT,
        GAP_LENGTH,
        CANVAS_WIDTH,
        CANVAS_HEIGHT,
        EQUAL_LENGTH_SIZE,
        MINIMUM_GENOME_LENGTH,
        RUN_CPU,
        RUN_EVALUE,
        RUN_NUM_HITS,
        BEND_HEIGHT
    }

    private List<LinkChoiceItem> buildLinkChoiceItems(MultipleSyntenyLinkModel linkModel) {
        List<LinkChoiceItem> items = new ArrayList<>();
        items.add(new LinkChoiceItem(MultipleSyntenyLinkModel.DEFAULT_TYPE, null));
        for (GenomeCompareExistingResultScanner.ReusableResult reusableResult : linkModel.getReusableResults()) {
            items.add(new LinkChoiceItem(reusableResult.getSelectionLabel(), reusableResult));
        }
        return items;
    }

    private LinkChoiceItem resolveSelectedChoice(MultipleSyntenyLinkModel linkModel) {
        GenomeCompareExistingResultScanner.ReusableResult selectedReusableResult = linkModel.getSelectedReusableResult();
        if (selectedReusableResult == null) {
            return new LinkChoiceItem(MultipleSyntenyLinkModel.DEFAULT_TYPE, null);
        }
        return new LinkChoiceItem(selectedReusableResult.getSelectionLabel(), selectedReusableResult);
    }

    private static final class LinkChoiceItem {
        private final String label;
        private final GenomeCompareExistingResultScanner.ReusableResult reusableResult;

        private LinkChoiceItem(String label, GenomeCompareExistingResultScanner.ReusableResult reusableResult) {
            this.label = label == null ? "" : label;
            this.reusableResult = reusableResult;
        }

        @Override
        public String toString() {
            return label;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof LinkChoiceItem)) {
                return false;
            }
            LinkChoiceItem other = (LinkChoiceItem) obj;
            if (reusableResult == null || other.reusableResult == null) {
                return reusableResult == null && other.reusableResult == null;
            }
            return reusableResult.equals(other.reusableResult);
        }

        @Override
        public int hashCode() {
            return reusableResult == null ? 0 : reusableResult.hashCode();
        }
    }

    public interface LinkAndSettingsListener {
        void onLinkSelected(MultipleSyntenyLinkModel linkModel);

        void onLinkRemoved(MultipleSyntenyLinkModel linkModel);

        void onLinkSelectionChanged(MultipleSyntenyLinkModel linkModel,
                                    GenomeCompareExistingResultScanner.ReusableResult reusableResult);

        void onRoutingBoxHalfHeightChanged(int value);

        void onGapLengthChanged(int value);

        void onCanvasWidthChanged(int value);

        void onCanvasHeightChanged(int value);

        void onEqualLengthChanged(boolean value);

        void onEqualLengthSizeChanged(int value);

        void onMinimumGenomeLengthChanged(int value);

        void onRouteModeChanged(MultipleSyntenyLinkModel linkModel, MultipleSyntenyLinkRouteMode mode);

        void onBendHeightChanged(MultipleSyntenyLinkModel linkModel, int value);

        void onRunCpuChanged(MultipleSyntenyLinkModel linkModel, int value);

        void onRunEvalueChanged(MultipleSyntenyLinkModel linkModel, double value);

        void onRunNumHitsChanged(MultipleSyntenyLinkModel linkModel, int value);

        void onRunDirectPlotChanged(MultipleSyntenyLinkModel linkModel, boolean value);
    }
}
