package simplegenomehub.gui;

import simplegenomehub.model.GenomeData;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.AdvancedCircosPreviewExporter;
import simplegenomehub.util.fileio.DualSyntenyPreviewExporter;
import simplegenomehub.util.fileio.GenomeStatsCalculator;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class SpeciesOverviewPanel extends JPanel {

    private static final Color STAT_BORDER_COLOR = new Color(166, 185, 210);
    private static final Color STAT_TEXT_COLOR = new Color(76, 84, 99);
    private static final Color TAB_TEXT_COLOR = new Color(52, 78, 117);
    private static final Color GENE_SET_SCROLL_TRACK_COLOR = new Color(236, 241, 247);
    private static final Color GENE_SET_SCROLL_THUMB_COLOR = new Color(180, 191, 206);
    private static final Color GENE_SET_SCROLL_THUMB_HOVER_COLOR = new Color(153, 168, 188);
    private static final DateTimeFormatter OTHER_DATA_DIR_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-M-d-H-m");
    private static final DateTimeFormatter OTHER_DATA_DISPLAY_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String OTHER_DATA_PROMPT_CARD = "prompt";
    private static final String OTHER_DATA_PREVIEW_CARD = "preview";

    private final JLabel speciesNameLabel;
    private final JLabel versionLabel;
    private final JLabel importTimeLabel;
    private final JTextArea notesArea;
    private final JTextArea statisticsArea;
    private final JTextArea geneSetArea;
    private final StatisticsTabButton genomeStatisticsTab;
    private final StatisticsTabButton chromosomeStatisticsTab;
    private final JPanel geneSetTabSection;
    private final JPanel geneSetTabsPanel;
    private final JViewport geneSetTabsViewport;
    private final JScrollBar geneSetTabsScrollBar;
    private final CardLayout otherDataCardLayout;
    private final JPanel otherDataCardPanel;
    private final JLabel otherDataPromptLabel;
    private final JLabel otherDataTimeLabel;
    private final JLabel otherDataGenome1Label;
    private final JLabel otherDataGenome2Label;
    private final OtherDataPreviewCanvas otherDataPreviewCanvas;
    private final JScrollPane otherDataPreviewScrollPane;
    private final JSplitPane otherDataPreviewSplitPane;
    private final JTextArea otherDataLinkListArea;
    private final JScrollPane otherDataLinkListScrollPane;

    private SpeciesInfo currentSpecies;
    private File currentGeneSetFile;
    private File currentOtherDataPreviewFile;
    private StatisticsView currentStatisticsView;
    private boolean chromosomeStatsReloadInProgress;

    SpeciesOverviewPanel() {
        speciesNameLabel = new JLabel();
        versionLabel = new JLabel();
        importTimeLabel = new JLabel();
        notesArea = new JTextArea(3, 30);
        statisticsArea = new JTextArea(9, 30);
        geneSetArea = new JTextArea(9, 30);
        genomeStatisticsTab = new StatisticsTabButton("Genome Statistics", StatisticsView.GENOME);
        chromosomeStatisticsTab = new StatisticsTabButton("Chromosome Statistics", StatisticsView.CHROMOSOME);
        geneSetTabSection = new JPanel(new BorderLayout(0, 0));
        geneSetTabsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        geneSetTabsViewport = new JViewport();
        geneSetTabsScrollBar = new JScrollBar(JScrollBar.HORIZONTAL);
        otherDataCardLayout = new CardLayout();
        otherDataCardPanel = new JPanel(otherDataCardLayout);
        otherDataPromptLabel = new JLabel("Please select an analysis result first.", SwingConstants.CENTER);
        otherDataTimeLabel = new JLabel("Time: -");
        otherDataGenome1Label = new JLabel("");
        otherDataGenome2Label = new JLabel("");
        otherDataPreviewCanvas = new OtherDataPreviewCanvas();
        otherDataPreviewScrollPane = new JScrollPane(otherDataPreviewCanvas);
        otherDataPreviewSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        otherDataLinkListArea = new JTextArea(3, 20);
        otherDataLinkListScrollPane = new JScrollPane(otherDataLinkListArea);
        currentStatisticsView = StatisticsView.GENOME;

        initializeComponents();
        setupLayout();
    }

    private void initializeComponents() {
        Font valueFont = SimpleGenomeHubStyle.FONT_SANS_PLAIN_15;
        speciesNameLabel.setFont(valueFont);
        versionLabel.setFont(valueFont);
        importTimeLabel.setFont(valueFont);
        speciesNameLabel.setForeground(STAT_TEXT_COLOR);
        versionLabel.setForeground(STAT_TEXT_COLOR);
        importTimeLabel.setForeground(STAT_TEXT_COLOR);

        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setEditable(false);
        notesArea.setBackground(Color.WHITE);
        notesArea.setBorder(SimpleGenomeHubUi.createInnerPadding(10, 10, 10, 10));

        statisticsArea.setEditable(false);
        statisticsArea.setLineWrap(false);
        statisticsArea.setWrapStyleWord(false);
        statisticsArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        statisticsArea.setForeground(STAT_TEXT_COLOR);
        statisticsArea.setBackground(Color.WHITE);
        statisticsArea.setBorder(SimpleGenomeHubUi.createInnerPadding(8, 8, 8, 8));

        geneSetArea.setEditable(false);
        geneSetArea.setLineWrap(false);
        geneSetArea.setWrapStyleWord(false);
        geneSetArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        geneSetArea.setForeground(STAT_TEXT_COLOR);
        geneSetArea.setBackground(Color.WHITE);
        geneSetArea.setBorder(SimpleGenomeHubUi.createInnerPadding(8, 8, 8, 8));

        otherDataPromptLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_15);
        otherDataPromptLabel.setForeground(STAT_TEXT_COLOR);

        otherDataTimeLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_15);
        otherDataTimeLabel.setForeground(STAT_TEXT_COLOR);
        otherDataGenome1Label.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_14);
        otherDataGenome1Label.setForeground(STAT_TEXT_COLOR);
        otherDataGenome2Label.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_14);
        otherDataGenome2Label.setForeground(STAT_TEXT_COLOR);
        otherDataGenome1Label.setVisible(false);
        otherDataGenome2Label.setVisible(false);

        otherDataPreviewScrollPane.setBorder(BorderFactory.createEmptyBorder());
        otherDataPreviewScrollPane.setOpaque(false);
        otherDataPreviewScrollPane.getViewport().setBackground(Color.WHITE);
        otherDataPreviewScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        otherDataPreviewScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        otherDataPreviewScrollPane.setMinimumSize(new Dimension(0, 0));
        otherDataPreviewScrollPane.setWheelScrollingEnabled(false);
        ModernScrollBarStyle.applyTo(otherDataPreviewScrollPane);
        otherDataPreviewScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                otherDataPreviewCanvas.refreshView();
            }
        });
        otherDataPreviewCanvas.addMouseWheelListener(this::handleOtherDataPreviewWheelZoom);
        otherDataPreviewScrollPane.getViewport().addMouseWheelListener(this::handleOtherDataPreviewWheelZoom);

        otherDataPreviewSplitPane.setBorder(BorderFactory.createEmptyBorder());
        otherDataPreviewSplitPane.setOpaque(false);
        otherDataPreviewSplitPane.setContinuousLayout(true);
        otherDataPreviewSplitPane.setResizeWeight(0.5d);
        otherDataPreviewSplitPane.setOneTouchExpandable(false);
        otherDataPreviewSplitPane.setEnabled(false);
        otherDataPreviewSplitPane.setTopComponent(otherDataPreviewScrollPane);
        otherDataPreviewSplitPane.setBottomComponent(otherDataLinkListScrollPane);
        otherDataPreviewSplitPane.setDividerSize(0);
        otherDataPreviewSplitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateOtherDataPreviewSplitLayout();
            }
        });

        otherDataLinkListArea.setEditable(false);
        otherDataLinkListArea.setLineWrap(false);
        otherDataLinkListArea.setWrapStyleWord(false);
        otherDataLinkListArea.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_13);
        otherDataLinkListArea.setForeground(STAT_TEXT_COLOR);
        otherDataLinkListArea.setBackground(Color.WHITE);
        otherDataLinkListArea.setBorder(SimpleGenomeHubUi.createInnerPadding(6, 6, 6, 6));
        otherDataLinkListScrollPane.setBorder(BorderFactory.createEmptyBorder());
        otherDataLinkListScrollPane.setOpaque(false);
        otherDataLinkListScrollPane.getViewport().setBackground(Color.WHITE);
        otherDataLinkListScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        otherDataLinkListScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        otherDataLinkListScrollPane.setMinimumSize(new Dimension(0, 0));
        ModernScrollBarStyle.applyTo(otherDataLinkListScrollPane);
        otherDataLinkListScrollPane.setVisible(false);

        otherDataCardPanel.setOpaque(false);

        geneSetTabSection.setOpaque(false);
        geneSetTabSection.setVisible(false);
        geneSetTabsPanel.setOpaque(false);
        geneSetTabsViewport.setOpaque(false);
        geneSetTabsViewport.setView(geneSetTabsPanel);
        geneSetTabsScrollBar.setOpaque(false);
        geneSetTabsScrollBar.setFocusable(false);
        geneSetTabsScrollBar.setVisible(false);
        geneSetTabsScrollBar.setUnitIncrement(20);
        geneSetTabsScrollBar.setPreferredSize(new Dimension(0, 7));
        geneSetTabsScrollBar.setUI(new GeneSetTabsScrollBarUi());
        geneSetTabsScrollBar.addAdjustmentListener(e ->
            geneSetTabsViewport.setViewPosition(new Point(e.getValue(), 0))
        );
        geneSetTabsViewport.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateGeneSetTabScrollBar();
            }
        });

        updateStatisticsTabs();
        showOtherDataPrompt();
    }

    private void setupLayout() {
        setOpaque(false);
        setLayout(new BorderLayout());
        add(createScrollableOverviewPanel(), BorderLayout.CENTER);
    }

    private JPanel createScrollableOverviewPanel() {
        JPanel cardsRow = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                Container parent = getParent();
                if (parent instanceof JViewport) {
                    size.height = Math.max(size.height, ((JViewport) parent).getHeight());
                }
                return size;
            }
        };
        cardsRow.setOpaque(false);
        cardsRow.setLayout(new BoxLayout(cardsRow, BoxLayout.X_AXIS));
        cardsRow.setBorder(SimpleGenomeHubUi.createInnerPadding(8, 8, 8, 8));

        JPanel infoPanel = createInfoPanel();
        JPanel statsPanel = createStatisticsPanel();
        JPanel geneSetPanel = createGeneSetPanel();
        JPanel otherDataPanel = createOtherDataPanel();
        Dimension cardSize = new Dimension(470, 228);
        infoPanel.setPreferredSize(cardSize);
        infoPanel.setMinimumSize(new Dimension(470, 228));
        infoPanel.setMaximumSize(new Dimension(470, Integer.MAX_VALUE));
        infoPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        statsPanel.setPreferredSize(cardSize);
        statsPanel.setMinimumSize(new Dimension(470, 228));
        statsPanel.setMaximumSize(new Dimension(470, Integer.MAX_VALUE));
        statsPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        geneSetPanel.setPreferredSize(cardSize);
        geneSetPanel.setMinimumSize(new Dimension(470, 228));
        geneSetPanel.setMaximumSize(new Dimension(470, Integer.MAX_VALUE));
        geneSetPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        otherDataPanel.setPreferredSize(cardSize);
        otherDataPanel.setMinimumSize(new Dimension(470, 228));
        otherDataPanel.setMaximumSize(new Dimension(470, Integer.MAX_VALUE));
        otherDataPanel.setAlignmentY(Component.TOP_ALIGNMENT);

        cardsRow.add(infoPanel);
        cardsRow.add(Box.createHorizontalStrut(14));
        cardsRow.add(statsPanel);
        cardsRow.add(Box.createHorizontalStrut(14));
        cardsRow.add(geneSetPanel);
        cardsRow.add(Box.createHorizontalStrut(14));
        cardsRow.add(otherDataPanel);
        cardsRow.add(Box.createHorizontalStrut(8));

        JScrollPane scrollPane = new JScrollPane(cardsRow,
            JScrollPane.VERTICAL_SCROLLBAR_NEVER,
            JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(20);
        ModernScrollBarStyle.applyTo(scrollPane);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel container = SpeciesInfoUiSupport.createContainerCard(
            Color.WHITE,
            new Color(219, 227, 238),
            contentPanel
        );
        container.setPreferredSize(new Dimension(10, 272));
        return container;
    }

    private JPanel createInfoPanel() {
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 10, 12);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(SpeciesInfoUiSupport.createFieldLabel("Species:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(speciesNameLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(SpeciesInfoUiSupport.createFieldLabel("Version:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        contentPanel.add(versionLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        contentPanel.add(SpeciesInfoUiSupport.createFieldLabel("Imported:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 0);
        contentPanel.add(importTimeLabel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 0, 12);
        contentPanel.add(SpeciesInfoUiSupport.createFieldLabel("Notes:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        contentPanel.add(SpeciesInfoUiSupport.createTextScrollPane(notesArea, new Color(210, 224, 241), 52), gbc);

        return SpeciesInfoUiSupport.createSectionCard(
            "Species Information",
            SimpleGenomeHubUi.TITLE_BLUE,
            Color.WHITE,
            new Color(216, 226, 238),
            contentPanel
        );
    }

    private JPanel createStatisticsPanel() {
        SimpleGenomeHubUi.RoundedPanel card = new SimpleGenomeHubUi.RoundedPanel(
            new BorderLayout(0, 0),
            Color.WHITE,
            new Color(216, 226, 238),
            26
        );
        card.setBorder(SimpleGenomeHubUi.createInnerPadding(14, 16, 16, 16));
        card.add(createStatisticsTabPanel(), BorderLayout.NORTH);
        card.add(createStatisticsContentPanel(), BorderLayout.CENTER);
        return card;
    }

    private JPanel createOtherDataPanel() {
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.add(createOtherDataContentPanel(), BorderLayout.CENTER);

        return SpeciesInfoUiSupport.createSectionCard(
            "Other Data",
            SimpleGenomeHubUi.TITLE_BLUE,
            Color.WHITE,
            new Color(216, 226, 238),
            contentPanel
        );
    }

    private JPanel createOtherDataContentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(STAT_BORDER_COLOR, 2),
            SimpleGenomeHubUi.createInnerPadding(6, 6, 6, 6)
        ));

        JPanel promptPanel = new JPanel(new BorderLayout());
        promptPanel.setOpaque(false);
        promptPanel.add(otherDataPromptLabel, BorderLayout.CENTER);

        JPanel previewPanel = new JPanel(new BorderLayout(0, 4));
        previewPanel.setOpaque(false);
        JPanel previewInfoPanel = new JPanel();
        previewInfoPanel.setOpaque(false);
        previewInfoPanel.setLayout(new BoxLayout(previewInfoPanel, BoxLayout.Y_AXIS));
        previewInfoPanel.add(otherDataTimeLabel);
        previewInfoPanel.add(otherDataGenome1Label);
        previewInfoPanel.add(otherDataGenome2Label);
        previewPanel.add(previewInfoPanel, BorderLayout.NORTH);
        previewPanel.add(otherDataPreviewSplitPane, BorderLayout.CENTER);

        otherDataCardPanel.add(promptPanel, OTHER_DATA_PROMPT_CARD);
        otherDataCardPanel.add(previewPanel, OTHER_DATA_PREVIEW_CARD);
        panel.add(otherDataCardPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createGeneSetPanel() {
        SimpleGenomeHubUi.RoundedPanel card = new SimpleGenomeHubUi.RoundedPanel(
            new BorderLayout(0, 0),
            Color.WHITE,
            new Color(216, 226, 238),
            26
        );
        card.setBorder(SimpleGenomeHubUi.createInnerPadding(14, 16, 16, 16));
        card.add(createGeneSetTabPanel(), BorderLayout.NORTH);
        card.add(createGeneSetContentPanel(), BorderLayout.CENTER);
        return card;
    }

    private JPanel createStatisticsTabPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 8, 0));
        panel.setOpaque(false);
        panel.setBorder(SimpleGenomeHubUi.createInnerPadding(0, 0, 0, 0));
        panel.add(genomeStatisticsTab);
        panel.add(chromosomeStatisticsTab);
        return panel;
    }

    private JPanel createStatisticsContentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(260, 108));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(STAT_BORDER_COLOR, 2),
            SimpleGenomeHubUi.createInnerPadding(10, 10, 10, 10)
        ));
        JScrollPane scrollPane = new JScrollPane(statisticsArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        ModernScrollBarStyle.applyTo(scrollPane);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createGeneSetTabPanel() {
        geneSetTabsPanel.setBorder(SimpleGenomeHubUi.createInnerPadding(0, 0, 0, 0));
        geneSetTabsViewport.setPreferredSize(new Dimension(260, 34));
        geneSetTabSection.add(geneSetTabsViewport, BorderLayout.CENTER);
        geneSetTabSection.add(geneSetTabsScrollBar, BorderLayout.SOUTH);
        return geneSetTabSection;
    }

    private JPanel createGeneSetContentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(260, 108));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(STAT_BORDER_COLOR, 2),
            SimpleGenomeHubUi.createInnerPadding(10, 10, 10, 10)
        ));
        JScrollPane scrollPane = new JScrollPane(geneSetArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        ModernScrollBarStyle.applyTo(scrollPane);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    void setSpecies(SpeciesInfo species) {
        boolean speciesChanged = currentSpecies == null || species == null || !currentSpecies.equals(species);
        currentSpecies = species;
        if (species == null) {
            clearDisplay();
            return;
        }

        if (speciesChanged) {
            currentGeneSetFile = null;
        }

        speciesNameLabel.setText(species.getSpeciesName());
        versionLabel.setText(species.getVersion());
        notesArea.setEditable(true);

        if (species.getImportTime() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            importTimeLabel.setText(sdf.format(java.sql.Timestamp.valueOf(species.getImportTime())));
        } else {
            importTimeLabel.setText("-");
        }

        notesArea.setText(species.getNotes() != null ? species.getNotes() : "");
        updateStatisticsText();
        updateGeneSetDisplay();
        showOtherDataPrompt();
    }

    void applyOtherDataSelection(SpeciesTreePanel.SelectionContext selectionContext) {
        if (selectionContext == null || selectionContext.getSelectionKind() == SpeciesTreePanel.SelectionKind.NONE) {
            showOtherDataPrompt();
            return;
        }

        switch (selectionContext.getSelectionKind()) {
            case ADVANCE_CIRCOS_RESULT:
                showAdvanceCircosOtherData(selectionContext.getSelectedFile());
                break;
            case GENOME_COMPARE_RESULT:
                showGenomeCompareOtherData(selectionContext.getSelectedFile());
                break;
            case MULTIPLE_SYNTENY_RESULT:
                showMultipleSyntenyOtherData(selectionContext.getSelectedFile());
                break;
            default:
                showOtherDataPrompt();
                break;
        }
    }

    JTextArea getNotesArea() {
        return notesArea;
    }

    private void clearDisplay() {
        currentSpecies = null;
        currentGeneSetFile = null;
        currentStatisticsView = StatisticsView.GENOME;
        updateStatisticsTabs();
        speciesNameLabel.setText("No species selected");
        versionLabel.setText("-");
        importTimeLabel.setText("-");
        notesArea.setText("");
        notesArea.setEditable(false);
        statisticsArea.setText("Select a species to view statistics.");
        statisticsArea.setCaretPosition(0);
        rebuildGeneSetTabs(new File[0]);
        setGeneSetStatusText("Please select a genome");
        showOtherDataPrompt();
    }

    private void selectStatisticsView(StatisticsView statisticsView) {
        if (currentStatisticsView == statisticsView) {
            return;
        }
        currentStatisticsView = statisticsView;
        updateStatisticsTabs();
        updateStatisticsText();
    }

    private void updateStatisticsTabs() {
        genomeStatisticsTab.setTabSelected(currentStatisticsView == StatisticsView.GENOME);
        chromosomeStatisticsTab.setTabSelected(currentStatisticsView == StatisticsView.CHROMOSOME);
    }

    private void updateStatisticsText() {
        if (currentSpecies == null) {
            statisticsArea.setText("Select a species to view statistics.");
            statisticsArea.setCaretPosition(0);
            return;
        }

        if (currentStatisticsView == StatisticsView.GENOME) {
            GenomeData genomeData = resolveGenomeData();
            if (genomeData != null) {
                statisticsArea.setText(GenomeStatsCalculator.generateStatsReport(genomeData));
            } else {
                statisticsArea.setText("Statistics not available.\nTry refreshing the species data.");
            }
        } else {
            String chromosomeText = readChromosomeStatisticsBlock();
            if (chromosomeText != null && !chromosomeText.isEmpty()) {
                chromosomeText = formatChromosomeStatisticsBlock(chromosomeText);
            }
            if (chromosomeText == null || chromosomeText.isEmpty()) {
                triggerChromosomeStatisticsReload();
                if (chromosomeStatsReloadInProgress) {
                    statisticsArea.setText("Chromosome statistics not found in stat.txt.\nRegenerating chromosome statistics, please wait...");
                } else {
                    GenomeData genomeData = resolveGenomeData();
                    chromosomeText = genomeData != null ? formatChromosomeStatistics(genomeData) : "";
                    if (chromosomeText == null || chromosomeText.isEmpty()) {
                        statisticsArea.setText("Chromosome statistics not available.\nNo chromosome entries were found in stat.txt.");
                    } else {
                        statisticsArea.setText(chromosomeText);
                    }
                }
            } else {
                statisticsArea.setText(chromosomeText);
            }
        }

        statisticsArea.setCaretPosition(0);
    }

    private void updateGeneSetDisplay() {
        if (currentSpecies == null) {
            currentGeneSetFile = null;
            rebuildGeneSetTabs(new File[0]);
            setGeneSetStatusText("Please select a genome");
            return;
        }

        File[] geneSetFiles = listGeneSetFiles();
        if (geneSetFiles.length == 0) {
            currentGeneSetFile = null;
            rebuildGeneSetTabs(geneSetFiles);
            setGeneSetStatusText("No Gene Set");
            return;
        }

        currentGeneSetFile = resolveSelectedGeneSetFile(geneSetFiles);
        rebuildGeneSetTabs(geneSetFiles);
        updateGeneSetText();
    }

    private File[] listGeneSetFiles() {
        if (currentSpecies == null) {
            return new File[0];
        }

        File geneSetDir = currentSpecies.getGeneSetDir();
        if (geneSetDir == null || !geneSetDir.exists()) {
            return new File[0];
        }

        File[] geneSetFiles = geneSetDir.listFiles((dir, name) ->
            GeneSetFileSupport.isStandardSetFileName(name));
        if (geneSetFiles == null || geneSetFiles.length == 0) {
            return new File[0];
        }

        Arrays.sort(geneSetFiles, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return geneSetFiles;
    }

    private File resolveSelectedGeneSetFile(File[] geneSetFiles) {
        if (currentGeneSetFile != null) {
            String selectedFileName = currentGeneSetFile.getName();
            for (File geneSetFile : geneSetFiles) {
                if (geneSetFile.getName().equals(selectedFileName)) {
                    return geneSetFile;
                }
            }
        }
        return geneSetFiles[0];
    }

    private void rebuildGeneSetTabs(File[] geneSetFiles) {
        geneSetTabsPanel.removeAll();
        for (File geneSetFile : geneSetFiles) {
            GeneSetTabButton tabButton = new GeneSetTabButton(geneSetFile);
            tabButton.setTabSelected(geneSetFile.equals(currentGeneSetFile));
            geneSetTabsPanel.add(tabButton);
        }
        geneSetTabSection.setVisible(geneSetFiles.length > 0);
        geneSetTabsPanel.revalidate();
        geneSetTabsPanel.repaint();
        updateGeneSetTabScrollBar();
        SwingUtilities.invokeLater(this::updateGeneSetTabScrollBar);
    }

    private void updateGeneSetTabScrollBar() {
        if (!geneSetTabSection.isVisible() || geneSetTabsPanel.getComponentCount() == 0) {
            geneSetTabsScrollBar.setVisible(false);
            geneSetTabsScrollBar.setValue(0);
            geneSetTabsViewport.setViewPosition(new Point(0, 0));
            geneSetTabSection.revalidate();
            geneSetTabSection.repaint();
            return;
        }

        int viewWidth = geneSetTabsPanel.getPreferredSize().width;
        int extentWidth = geneSetTabsViewport.getExtentSize().width;
        if (extentWidth <= 0) {
            extentWidth = geneSetTabsViewport.getWidth();
        }
        if (extentWidth <= 0) {
            return;
        }

        boolean needsScroll = viewWidth > extentWidth;
        geneSetTabsScrollBar.setVisible(needsScroll);

        if (!needsScroll) {
            geneSetTabsScrollBar.setValue(0);
            geneSetTabsViewport.setViewPosition(new Point(0, 0));
        } else {
            int maxScroll = Math.max(0, viewWidth - extentWidth);
            int value = Math.min(geneSetTabsScrollBar.getValue(), maxScroll);
            geneSetTabsScrollBar.setValues(value, extentWidth, 0, viewWidth);
            geneSetTabsScrollBar.setBlockIncrement(Math.max(20, extentWidth));
            geneSetTabsViewport.setViewPosition(new Point(value, 0));
        }

        geneSetTabSection.revalidate();
        geneSetTabSection.repaint();
    }

    private void selectGeneSetFile(File geneSetFile) {
        if (geneSetFile == null) {
            return;
        }
        currentGeneSetFile = geneSetFile;
        updateGeneSetDisplay();
    }

    private void updateGeneSetText() {
        if (currentGeneSetFile == null) {
            setGeneSetStatusText("No Gene Set");
            return;
        }

        try {
            String content = GeneSetFileSupport.readGeneSetContent(currentGeneSetFile);
            geneSetArea.setText(content);
        } catch (IOException e) {
            geneSetArea.setText("Failed to read Gene Set file.\n" + e.getMessage());
        }
        geneSetArea.setCaretPosition(0);
    }

    private void setGeneSetStatusText(String text) {
        geneSetArea.setText(text);
        geneSetArea.setCaretPosition(0);
    }

    private void showOtherDataPrompt() {
        currentOtherDataPreviewFile = null;
        otherDataTimeLabel.setText("Time: -");
        updateOtherDataGenomeLabels(null, null);
        updateOtherDataLinkList(null);
        otherDataPreviewCanvas.clearPreview();
        otherDataCardLayout.show(otherDataCardPanel, OTHER_DATA_PROMPT_CARD);
    }

    private void showAdvanceCircosOtherData(File resultDir) {
        if (resultDir == null || !resultDir.isDirectory()) {
            showOtherDataPrompt();
            return;
        }

        currentOtherDataPreviewFile = AdvancedCircosPreviewExporter.getPreviewFile(resultDir);
        otherDataTimeLabel.setText("Time: " + formatOtherDataTime(resultDir));
        updateOtherDataGenomeLabels(null, null);
        updateOtherDataLinkList(null);
        otherDataCardLayout.show(otherDataCardPanel, OTHER_DATA_PREVIEW_CARD);
        loadOtherDataPreviewImage();
    }

    private void showGenomeCompareOtherData(File resultDir) {
        if (resultDir == null || !resultDir.isDirectory()) {
            showOtherDataPrompt();
            return;
        }

        GenomeCompareOtherDataInfo info = readGenomeCompareOtherDataInfo(resultDir);
        currentOtherDataPreviewFile = DualSyntenyPreviewExporter.getPreviewFile(resultDir);
        otherDataTimeLabel.setText("Time: " + (info != null ? info.runTime : formatOtherDataTime(resultDir)));
        updateOtherDataGenomeLabels(
            info != null ? info.genome1 : null,
            info != null ? info.genome2 : null
        );
        updateOtherDataLinkList(null);
        otherDataCardLayout.show(otherDataCardPanel, OTHER_DATA_PREVIEW_CARD);
        loadOtherDataPreviewImage();
    }

    private void showMultipleSyntenyOtherData(File resultDir) {
        if (resultDir == null || !resultDir.isDirectory()) {
            showOtherDataPrompt();
            return;
        }

        MultipleSyntenyOtherDataInfo info = readMultipleSyntenyOtherDataInfo(resultDir);
        currentOtherDataPreviewFile = new File(resultDir, "preview.png");
        otherDataTimeLabel.setText("Time: " + (info != null ? info.runTime : formatOtherDataTime(resultDir)));
        updateOtherDataGenomeLabels(null, null);
        updateOtherDataLinkList(info != null ? info.linkLines : null);
        otherDataCardLayout.show(otherDataCardPanel, OTHER_DATA_PREVIEW_CARD);
        loadOtherDataPreviewImage();
    }

    private void updateOtherDataGenomeLabels(String genome1, String genome2) {
        String trimmedGenome1 = genome1 == null ? "" : genome1.trim();
        String trimmedGenome2 = genome2 == null ? "" : genome2.trim();
        boolean showGenome1 = !trimmedGenome1.isEmpty();
        boolean showGenome2 = !trimmedGenome2.isEmpty();

        otherDataGenome1Label.setText(showGenome1 ? "Genome1: " + trimmedGenome1 : "");
        otherDataGenome1Label.setVisible(showGenome1);
        otherDataGenome2Label.setText(showGenome2 ? "Genome2: " + trimmedGenome2 : "");
        otherDataGenome2Label.setVisible(showGenome2);
        otherDataCardPanel.revalidate();
        otherDataCardPanel.repaint();
    }

    private void updateOtherDataLinkList(List<String> linkLines) {
        boolean showLinks = linkLines != null && !linkLines.isEmpty();
        otherDataLinkListArea.setText(showLinks ? String.join("\n", linkLines) : "");
        otherDataLinkListArea.setCaretPosition(0);
        otherDataLinkListScrollPane.setVisible(showLinks);
        updateOtherDataPreviewSplitLayout();
        otherDataCardPanel.revalidate();
        otherDataCardPanel.repaint();
    }

    private void updateOtherDataPreviewSplitLayout() {
        boolean showLinks = otherDataLinkListScrollPane.isVisible();
        otherDataPreviewSplitPane.setDividerSize(showLinks ? 4 : 0);
        SwingUtilities.invokeLater(() -> {
            if (!otherDataPreviewSplitPane.isDisplayable()) {
                return;
            }
            otherDataPreviewSplitPane.setDividerLocation(showLinks ? 0.5d : 1.0d);
        });
    }

    private void loadOtherDataPreviewImage() {
        if (currentOtherDataPreviewFile == null) {
            otherDataPreviewCanvas.clearPreview();
            return;
        }
        if (!currentOtherDataPreviewFile.isFile()) {
            otherDataPreviewCanvas.setMessage("Preview image not available.");
            return;
        }

        try {
            BufferedImage image = ImageIO.read(currentOtherDataPreviewFile);
            if (image == null) {
                otherDataPreviewCanvas.setMessage("Preview image not available.");
                return;
            }
            otherDataPreviewCanvas.setImage(image);
        } catch (IOException ex) {
            otherDataPreviewCanvas.setMessage("Failed to load preview image.");
        }
    }

    private void handleOtherDataPreviewWheelZoom(MouseWheelEvent event) {
        if (event == null || !otherDataPreviewCanvas.hasImage()) {
            return;
        }
        Point anchorPoint = SwingUtilities.convertPoint(
            event.getComponent(),
            event.getPoint(),
            otherDataPreviewCanvas
        );
        otherDataPreviewCanvas.adjustZoom(event.getWheelRotation(), anchorPoint);
        event.consume();
    }

    private String formatOtherDataTime(File resultDir) {
        if (resultDir != null) {
            try {
                LocalDateTime directoryTime = LocalDateTime.parse(resultDir.getName(), OTHER_DATA_DIR_TIME_FORMAT);
                return OTHER_DATA_DISPLAY_TIME_FORMAT.format(directoryTime);
            } catch (DateTimeParseException ignored) {
                // Fallback to file timestamp.
            }

            Instant instant = Instant.ofEpochMilli(resultDir.lastModified());
            return OTHER_DATA_DISPLAY_TIME_FORMAT.format(
                LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            );
        }
        return "-";
    }

    private GenomeCompareOtherDataInfo readGenomeCompareOtherDataInfo(File resultDir) {
        if (resultDir == null || !resultDir.isDirectory()) {
            return null;
        }

        File metadataFile = new File(resultDir, "run-metadata.txt");
        if (!metadataFile.isFile()) {
            return null;
        }

        String runTime = null;
        String genome1 = null;
        String genome2 = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new FileInputStream(metadataFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("runTime=")) {
                    runTime = formatMetadataRunTime(trimmed.substring("runTime=".length()).trim(), resultDir);
                    continue;
                }
                if (trimmed.startsWith("species1=")) {
                    genome1 = emptyToNull(trimmed.substring("species1=".length()));
                    continue;
                }
                if (trimmed.startsWith("species2=")) {
                    genome2 = emptyToNull(trimmed.substring("species2=".length()));
                }
            }
        } catch (IOException ignored) {
            return null;
        }

        return new GenomeCompareOtherDataInfo(
            runTime != null ? runTime : formatOtherDataTime(resultDir),
            genome1,
            genome2
        );
    }

    private MultipleSyntenyOtherDataInfo readMultipleSyntenyOtherDataInfo(File resultDir) {
        if (resultDir == null || !resultDir.isDirectory()) {
            return null;
        }

        File metadataFile = new File(resultDir, "run-metadata.txt");
        if (!metadataFile.isFile()) {
            return null;
        }

        String runTime = null;
        String currentSection = null;
        java.util.LinkedHashMap<String, String> genomeNamesByKey = new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String, LinkSummaryLine> linksByKey = new java.util.LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            new FileInputStream(metadataFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("runTime=")) {
                    runTime = formatMetadataRunTime(trimmed.substring("runTime=".length()).trim(), resultDir);
                    continue;
                }
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    currentSection = trimmed;
                    continue;
                }

                int separatorIndex = trimmed.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, separatorIndex).trim();
                String value = trimmed.substring(separatorIndex + 1).trim();
                if ("[genomes]".equalsIgnoreCase(currentSection)) {
                    String baseKey = resolveGenomeBaseKey(key, ".displayName");
                    if (baseKey != null) {
                        String displayName = emptyToNull(value);
                        if (displayName != null) {
                            genomeNamesByKey.put(baseKey, displayName);
                        }
                        continue;
                    }
                    if (isSimpleGenomeKey(key) && !genomeNamesByKey.containsKey(key)) {
                        String genomeName = emptyToNull(value);
                        if (genomeName != null) {
                            genomeNamesByKey.put(key, genomeName);
                        }
                    }
                    continue;
                }
                if (!"[links]".equalsIgnoreCase(currentSection)) {
                    continue;
                }

                String baseKey = resolveLinkBaseKey(key, ".genome1");
                if (baseKey != null) {
                    getOrCreateLinkSummary(linksByKey, baseKey).genome1 =
                        resolveGenomeDisplayName(genomeNamesByKey, value);
                    continue;
                }
                baseKey = resolveLinkBaseKey(key, ".genome2");
                if (baseKey != null) {
                    getOrCreateLinkSummary(linksByKey, baseKey).genome2 =
                        resolveGenomeDisplayName(genomeNamesByKey, value);
                    continue;
                }
                if (isSimpleLinkKey(key)) {
                    getOrCreateLinkSummary(linksByKey, key).displayLabel = emptyToNull(value);
                }
            }
        } catch (IOException ignored) {
            return null;
        }

        List<String> linkLines = new java.util.ArrayList<>();
        for (LinkSummaryLine linkSummary : linksByKey.values()) {
            String linkLine = linkSummary.formatForDisplay();
            if (linkLine != null) {
                linkLines.add(linkLine);
            }
        }

        return new MultipleSyntenyOtherDataInfo(
            runTime != null ? runTime : formatOtherDataTime(resultDir),
            linkLines
        );
    }

    private LinkSummaryLine getOrCreateLinkSummary(java.util.LinkedHashMap<String, LinkSummaryLine> linksByKey,
                                                   String key) {
        LinkSummaryLine existing = linksByKey.get(key);
        if (existing != null) {
            return existing;
        }
        LinkSummaryLine created = new LinkSummaryLine();
        linksByKey.put(key, created);
        return created;
    }

    private String resolveLinkBaseKey(String key, String suffix) {
        if (key == null || suffix == null || !key.startsWith("Link") || !key.endsWith(suffix)) {
            return null;
        }
        return key.substring(0, key.length() - suffix.length());
    }

    private String resolveGenomeBaseKey(String key, String suffix) {
        if (key == null || suffix == null || !key.startsWith("Genome") || !key.endsWith(suffix)) {
            return null;
        }
        return key.substring(0, key.length() - suffix.length());
    }

    private boolean isSimpleLinkKey(String key) {
        if (key == null || !key.startsWith("Link") || key.length() <= 4) {
            return false;
        }
        for (int i = 4; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isSimpleGenomeKey(String key) {
        if (key == null || !key.startsWith("Genome") || key.length() <= 6) {
            return false;
        }
        for (int i = 6; i < key.length(); i++) {
            if (!Character.isDigit(key.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String resolveGenomeDisplayName(java.util.Map<String, String> genomeNamesByKey, String genomeKey) {
        String normalizedKey = emptyToNull(genomeKey);
        if (normalizedKey == null) {
            return null;
        }
        String displayName = genomeNamesByKey.get(normalizedKey);
        return displayName != null ? displayName : normalizedKey;
    }

    private String formatMetadataRunTime(String rawValue, File resultDir) {
        String trimmed = rawValue == null ? "" : rawValue.trim();
        if (trimmed.isEmpty()) {
            return formatOtherDataTime(resultDir);
        }
        try {
            return OTHER_DATA_DISPLAY_TIME_FORMAT.format(LocalDateTime.parse(trimmed));
        } catch (DateTimeParseException ignored) {
            return formatOtherDataTime(resultDir);
        }
    }

    private String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private GenomeData resolveGenomeData() {
        if (currentSpecies == null) {
            return null;
        }

        GenomeData genomeData = currentSpecies.getGenomeData();
        if (genomeData != null) {
            return genomeData;
        }

        File statsFile = currentSpecies.getStatsFile();
        if (statsFile != null && statsFile.exists()) {
            genomeData = GenomeData.loadFromFile(statsFile);
            currentSpecies.setGenomeData(genomeData);
            return genomeData;
        }

        return null;
    }

    private String readChromosomeStatisticsBlock() {
        if (currentSpecies == null) {
            return null;
        }

        File statsFile = currentSpecies.getStatsFile();
        if (statsFile == null || !statsFile.exists()) {
            return null;
        }

        StringBuilder block = new StringBuilder();
        boolean inChromosomeBlock = false;

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(statsFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!inChromosomeBlock) {
                    if ("#####################chromosome stat".equals(trimmed)) {
                        inChromosomeBlock = true;
                    }
                    continue;
                }

                if (trimmed.startsWith("#####################")) {
                    break;
                }
                if (trimmed.isEmpty()) {
                    continue;
                }

                if (block.length() > 0) {
                    block.append('\n');
                }
                block.append(trimmed);
            }
        } catch (IOException e) {
            return null;
        }

        return block.length() > 0 ? block.toString() : null;
    }

    private String formatChromosomeStatisticsBlock(String rawBlock) {
        if (rawBlock == null || rawBlock.trim().isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        String[] lines = rawBlock.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
                continue;
            }

            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();

            if (key.endsWith(".name")) {
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append(key).append(": ").append(value);
            } else if (key.endsWith(".size")) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append("size: ").append(value);
            } else if (key.endsWith(".gc")) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append("GC: ").append(value);
            } else {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(key).append(": ").append(value);
            }
        }

        return builder.toString();
    }

    private void triggerChromosomeStatisticsReload() {
        if (chromosomeStatsReloadInProgress || currentSpecies == null) {
            return;
        }

        File genomeFile = currentSpecies.getGenomeFile();
        if (genomeFile == null || !genomeFile.exists()) {
            return;
        }

        File statsFile = currentSpecies.getStatsFile();
        if (statsFile == null) {
            return;
        }

        chromosomeStatsReloadInProgress = true;
        File annotationFile = currentSpecies.getAnnotationFile();

        SwingWorker<GenomeData, Void> worker = new SwingWorker<GenomeData, Void>() {
            @Override
            protected GenomeData doInBackground() {
                GenomeData genomeData = GenomeStatsCalculator.calculateGenomeStats(genomeFile, annotationFile);
                genomeData.saveToFile(statsFile);
                return genomeData;
            }

            @Override
            protected void done() {
                chromosomeStatsReloadInProgress = false;
                try {
                    GenomeData genomeData = get();
                    if (genomeData != null) {
                        currentSpecies.setGenomeData(genomeData);
                    }
                } catch (Exception e) {
                    statisticsArea.setText("Failed to regenerate chromosome statistics.\n" + e.getMessage());
                    statisticsArea.setCaretPosition(0);
                    return;
                }

                if (currentStatisticsView == StatisticsView.CHROMOSOME) {
                    updateStatisticsText();
                }
            }
        };
        worker.execute();
    }

    private String formatChromosomeStatistics(GenomeData genomeData) {
        List<GenomeData.ChromosomeStat> chromosomeStats = genomeData.getChromosomeStats();
        if (chromosomeStats.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < chromosomeStats.size(); i++) {
            GenomeData.ChromosomeStat chromosomeStat = chromosomeStats.get(i);
            int chromosomeIndex = i + 1;

            builder.append("chromosome").append(chromosomeIndex).append(".name: ")
                .append(chromosomeStat.getName()).append('\n');
            builder.append("size: ")
                .append(chromosomeStat.getSize()).append('\n');
            builder.append("GC: ")
                .append(String.format(Locale.US, "%.4f", chromosomeStat.getGcContent()));

            if (i < chromosomeStats.size() - 1) {
                builder.append("\n\n");
            }
        }
        return builder.toString();
    }

    private enum StatisticsView {
        GENOME,
        CHROMOSOME
    }

    private static final class GenomeCompareOtherDataInfo {
        private final String runTime;
        private final String genome1;
        private final String genome2;

        private GenomeCompareOtherDataInfo(String runTime, String genome1, String genome2) {
            this.runTime = runTime == null ? "-" : runTime;
            this.genome1 = genome1;
            this.genome2 = genome2;
        }
    }

    private static final class MultipleSyntenyOtherDataInfo {
        private final String runTime;
        private final List<String> linkLines;

        private MultipleSyntenyOtherDataInfo(String runTime, List<String> linkLines) {
            this.runTime = runTime == null ? "-" : runTime;
            this.linkLines = linkLines == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(linkLines);
        }
    }

    private static final class LinkSummaryLine {
        private String genome1;
        private String genome2;
        private String displayLabel;

        private String formatForDisplay() {
            String trimmedGenome1 = genome1 == null ? "" : genome1.trim();
            String trimmedGenome2 = genome2 == null ? "" : genome2.trim();
            if (!trimmedGenome1.isEmpty() && !trimmedGenome2.isEmpty()) {
                return trimmedGenome1 + " x " + trimmedGenome2;
            }
            String trimmedDisplayLabel = displayLabel == null ? "" : displayLabel.trim();
            return trimmedDisplayLabel.isEmpty() ? null : trimmedDisplayLabel;
        }
    }

    private final class OtherDataPreviewCanvas extends JComponent {

        private static final int DEFAULT_WIDTH = 400;
        private static final int DEFAULT_HEIGHT = 140;
        private static final int CONTENT_PADDING = 12;
        private static final double MIN_ZOOM_MULTIPLIER = 0.25d;
        private static final double MAX_ZOOM_MULTIPLIER = 8.0d;
        private static final double ZOOM_STEP_FACTOR = 1.1d;

        private BufferedImage image;
        private String message = "";
        private double zoomMultiplier = 1.0d;

        private OtherDataPreviewCanvas() {
            setOpaque(true);
            setBackground(Color.WHITE);
            setForeground(STAT_TEXT_COLOR);
        }

        private void clearPreview() {
            image = null;
            message = "";
            zoomMultiplier = 1.0d;
            revalidate();
            repaint();
        }

        private void setMessage(String message) {
            image = null;
            this.message = message == null ? "" : message;
            zoomMultiplier = 1.0d;
            revalidate();
            repaint();
        }

        private void setImage(BufferedImage image) {
            this.image = image;
            this.message = "";
            this.zoomMultiplier = 1.0d;
            revalidate();
            repaint();
            SwingUtilities.invokeLater(() ->
                otherDataPreviewScrollPane.getViewport().setViewPosition(new Point(0, 0))
            );
        }

        private boolean hasImage() {
            return image != null;
        }

        private void refreshView() {
            revalidate();
            repaint();
        }

        private void adjustZoom(int wheelRotation, Point anchorInCanvas) {
            if (image == null || wheelRotation == 0) {
                return;
            }

            JViewport viewport = otherDataPreviewScrollPane.getViewport();
            Rectangle viewRect = viewport.getViewRect();
            Point anchorInViewport = SwingUtilities.convertPoint(this, anchorInCanvas, viewport);
            Dimension oldSize = getPreferredSize();
            double anchorRatioX = oldSize.width <= 0
                ? 0.0d
                : (viewRect.x + anchorInViewport.x) / (double) oldSize.width;
            double anchorRatioY = oldSize.height <= 0
                ? 0.0d
                : (viewRect.y + anchorInViewport.y) / (double) oldSize.height;

            double nextZoomMultiplier = zoomMultiplier * Math.pow(ZOOM_STEP_FACTOR, -wheelRotation);
            nextZoomMultiplier = Math.max(MIN_ZOOM_MULTIPLIER, Math.min(MAX_ZOOM_MULTIPLIER, nextZoomMultiplier));
            if (Math.abs(nextZoomMultiplier - zoomMultiplier) < 1.0e-6d) {
                return;
            }

            zoomMultiplier = nextZoomMultiplier;
            revalidate();
            repaint();

            SwingUtilities.invokeLater(() -> {
                Dimension newSize = getPreferredSize();
                int targetX = Math.max(0, Math.min(
                    Math.max(0, newSize.width - viewRect.width),
                    (int) Math.round(anchorRatioX * newSize.width - anchorInViewport.x)
                ));
                int targetY = Math.max(0, Math.min(
                    Math.max(0, newSize.height - viewRect.height),
                    (int) Math.round(anchorRatioY * newSize.height - anchorInViewport.y)
                ));
                viewport.setViewPosition(new Point(targetX, targetY));
            });
        }

        @Override
        public Dimension getPreferredSize() {
            int preferredWidth = resolveStableAvailableWidth();
            if (image == null) {
                int preferredHeight = resolveViewportHeight();
                return new Dimension(preferredWidth, preferredHeight);
            }

            double renderScale = resolveRenderScale();
            int renderWidth = Math.max(1, (int) Math.round(image.getWidth() * renderScale));
            int renderHeight = Math.max(1, (int) Math.round(image.getHeight() * renderScale));
            return new Dimension(Math.max(preferredWidth, renderWidth), renderHeight);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setColor(getBackground());
                g2.fillRect(0, 0, getWidth(), getHeight());

                if (image == null) {
                    if (message != null && !message.isEmpty()) {
                        g2.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_14);
                        g2.setColor(getForeground());
                        FontMetrics metrics = g2.getFontMetrics();
                        int textX = Math.max(0, (getWidth() - metrics.stringWidth(message)) / 2);
                        int textY = Math.max(metrics.getAscent(), (getHeight() + metrics.getAscent()) / 2);
                        g2.drawString(message, textX, textY);
                    }
                    return;
                }

                double renderScale = resolveRenderScale();
                int renderWidth = Math.max(1, (int) Math.round(image.getWidth() * renderScale));
                int renderHeight = Math.max(1, (int) Math.round(image.getHeight() * renderScale));
                int imageX = Math.max(0, (getWidth() - renderWidth) / 2);
                g2.drawImage(image, imageX, 0, renderWidth, renderHeight, null);
            } finally {
                g2.dispose();
            }
        }

        private double resolveRenderScale() {
            if (image == null) {
                return 1.0d;
            }
            double fitScale = Math.min(1.0d, (double) resolveStableAvailableWidth() / Math.max(1, image.getWidth()));
            return Math.max(0.05d, fitScale * zoomMultiplier);
        }

        private int resolveStableAvailableWidth() {
            int scrollPaneWidth = otherDataPreviewScrollPane.getWidth();
            if (scrollPaneWidth > 0) {
                Insets insets = otherDataPreviewScrollPane.getInsets();
                scrollPaneWidth -= insets.left + insets.right;
            } else {
                scrollPaneWidth = otherDataPreviewScrollPane.getViewport().getExtentSize().width;
            }

            int baseWidth = scrollPaneWidth > 0 ? scrollPaneWidth : DEFAULT_WIDTH;
            int availableWidth = Math.max(1, baseWidth - CONTENT_PADDING);
            if (image == null) {
                return availableWidth;
            }

            int viewportHeight = resolveViewportHeight();
            double scaleWithoutScrollbar = Math.min(1.0d, (double) availableWidth / Math.max(1, image.getWidth()));
            int heightWithoutScrollbar = Math.max(
                1,
                (int) Math.round(image.getHeight() * scaleWithoutScrollbar * zoomMultiplier)
            );
            if (heightWithoutScrollbar > viewportHeight) {
                int scrollbarWidth = Math.max(
                    0,
                    otherDataPreviewScrollPane.getVerticalScrollBar().getPreferredSize().width
                );
                availableWidth = Math.max(1, availableWidth - scrollbarWidth);
            }
            return availableWidth;
        }

        private int resolveViewportHeight() {
            Dimension viewportSize = otherDataPreviewScrollPane.getViewport().getExtentSize();
            if (viewportSize.height > CONTENT_PADDING) {
                return viewportSize.height - CONTENT_PADDING;
            }
            return viewportSize.height > 0 ? viewportSize.height : DEFAULT_HEIGHT;
        }
    }

    private abstract class OverviewTabButton extends JComponent {

        private final String title;
        private boolean tabSelected;

        private OverviewTabButton(String title) {
            this.title = title;
            setOpaque(false);
            setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_13);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onTabClicked();
                }
            });
        }

        protected abstract void onTabClicked();

        protected final void setTabSelected(boolean tabSelected) {
            if (this.tabSelected != tabSelected) {
                this.tabSelected = tabSelected;
                Font baseFont = tabSelected
                    ? SimpleGenomeHubStyle.FONT_SANS_BOLD_15
                    : SimpleGenomeHubStyle.FONT_SANS_BOLD_13;
                setFont(baseFont);
                revalidate();
                repaint();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fontMetrics = getFontMetrics(getFont());
            return new Dimension(fontMetrics.stringWidth(title) + 34, 34);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int width = getWidth() - 1;
                int height = getHeight() - 1;
                if (tabSelected) {
                    Polygon trapezoid = new Polygon(
                        new int[]{10, Math.max(10, width - 10), width, 0},
                        new int[]{0, 0, height, height},
                        4
                    );
                    g2.setColor(Color.WHITE);
                    g2.fillPolygon(trapezoid);
                    g2.setColor(STAT_BORDER_COLOR);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawPolygon(trapezoid);
                }

                g2.setFont(getFont());
                g2.setColor(tabSelected ? SimpleGenomeHubUi.TITLE_BLUE : TAB_TEXT_COLOR);
                FontMetrics fontMetrics = g2.getFontMetrics();
                int textX = (getWidth() - fontMetrics.stringWidth(title)) / 2;
                int textY = ((getHeight() - fontMetrics.getHeight()) / 2) + fontMetrics.getAscent();
                g2.drawString(title, textX, textY);
            } finally {
                g2.dispose();
            }
        }
    }

    private final class StatisticsTabButton extends OverviewTabButton {

        private final StatisticsView statisticsView;

        private StatisticsTabButton(String title, StatisticsView statisticsView) {
            super(title);
            this.statisticsView = statisticsView;
        }

        @Override
        protected void onTabClicked() {
            selectStatisticsView(statisticsView);
        }
    }

    private final class GeneSetTabButton extends OverviewTabButton {

        private final File geneSetFile;

        private GeneSetTabButton(File geneSetFile) {
            super(GeneSetFileSupport.extractDisplayName(geneSetFile));
            this.geneSetFile = geneSetFile;
        }

        @Override
        protected void onTabClicked() {
            selectGeneSetFile(geneSetFile);
        }
    }

    private final class GeneSetTabsScrollBarUi extends BasicScrollBarUI {

        @Override
        protected void configureScrollBarColors() {
            trackColor = GENE_SET_SCROLL_TRACK_COLOR;
            thumbColor = GENE_SET_SCROLL_THUMB_COLOR;
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
            return new Dimension(24, 4);
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(trackColor);
                int height = 3;
                int y = bounds.y + Math.max(0, (bounds.height - height) / 2);
                g2.fillRoundRect(bounds.x + 3, y, Math.max(0, bounds.width - 6), height, height, height);
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
                g2.setColor(isThumbRollover() ? GENE_SET_SCROLL_THUMB_HOVER_COLOR : thumbColor);
                int height = 5;
                int y = bounds.y + Math.max(0, (bounds.height - height) / 2);
                g2.fillRoundRect(bounds.x + 2, y, Math.max(10, bounds.width - 4), height, height, height);
            } finally {
                g2.dispose();
            }
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
