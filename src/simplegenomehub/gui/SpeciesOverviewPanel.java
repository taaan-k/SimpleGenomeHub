package simplegenomehub.gui;

import simplegenomehub.model.GenomeData;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.GenomeStatsCalculator;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
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

    private SpeciesInfo currentSpecies;
    private File currentGeneSetFile;
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

        return SpeciesInfoUiSupport.createSectionCard(
            "Other Data",
            SimpleGenomeHubUi.TITLE_BLUE,
            Color.WHITE,
            new Color(216, 226, 238),
            contentPanel
        );
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
