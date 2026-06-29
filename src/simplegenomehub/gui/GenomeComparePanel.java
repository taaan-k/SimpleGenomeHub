/*
 * SimpleGenomeHub Genome Compare Panel
 * UI layout for selecting two genomes and editing comparison parameters
 */
package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;
import simplegenomehub.model.GenomeData;
import simplegenomehub.util.fileio.DualSyntenyChromosomeOrderBuilder;
import simplegenomehub.util.fileio.DualSyntenyPlotLauncher;
import simplegenomehub.util.fileio.GenomeCompareService;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Main panel for genome compare UI
 */
public class GenomeComparePanel extends JPanel {

    private static final Color ACTIVE_SLOT_COLOR = new Color(70, 130, 180);
    private static final Color ACTIVE_SLOT_BACKGROUND = new Color(235, 244, 255);
    private static final Color INACTIVE_SLOT_BORDER = new Color(190, 190, 190);
    private static final Color INACTIVE_SLOT_BACKGROUND = Color.WHITE;

    private final SpeciesManager speciesManager;

    private JTree speciesTree;
    private DefaultTreeModel treeModel;
    private JButton selectButton;
    private JButton startButton;

    private JPanel genome1Panel;
    private JPanel genome2Panel;
    private JTextField genome1Field;
    private JTextField genome2Field;
    private JTextArea genome1ChromosomeArea;
    private JTextArea genome2ChromosomeArea;

    private JTextField cpuField;
    private JTextField evalueField;
    private JTextField numHitsField;
    private JCheckBox directPlotCheckBox;
    private JCheckBox reorderChromosomesCheckBox;
    private JTextArea highlightGeneArea;
    private JComboBox<String> geneSetComboBox;
    private JTextArea logArea;
    private List<GeneSetChoice> availableGeneSets;

    private SpeciesInfo genome1Species;
    private SpeciesInfo genome2Species;
    private int activeGenomeSlot = 1;
    private boolean adjustingGeneSetSelection;

    private static final String GENE_SET_NONE_OPTION = "<None>";

    public GenomeComparePanel(SpeciesManager speciesManager) {
        this.speciesManager = speciesManager;

        initializeGui();
        refreshSpeciesTree();
        updateGenomeSlotStyles();
    }

    private void initializeGui() {
        setLayout(new BorderLayout());

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        SimpleGenomeHubUi.styleSplitPane(mainSplitPane);
        mainSplitPane.setLeftComponent(createLeftPanel());
        mainSplitPane.setRightComponent(createRightPanel());
        mainSplitPane.setDividerLocation(320);

        add(mainSplitPane, BorderLayout.CENTER);
    }

    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(0, 12));

        JPanel speciesPanel = new JPanel(new BorderLayout());
        speciesPanel.setBorder(new TitledBorder("Target Species Selection"));

        createSpeciesTree();
        JScrollPane treeScrollPane = new JScrollPane(speciesTree);
        treeScrollPane.setPreferredSize(new Dimension(300, 450));
        speciesPanel.add(treeScrollPane, BorderLayout.CENTER);

        leftPanel.add(speciesPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        selectButton = new JButton("Select");
        selectButton.setPreferredSize(new Dimension(120, 34));
        selectButton.setEnabled(speciesManager != null);
        selectButton.addActionListener(e -> assignSelectedGenome());
        buttonPanel.add(selectButton);

        leftPanel.add(buttonPanel, BorderLayout.SOUTH);
        return leftPanel;
    }

    private void createSpeciesTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Species List");
        treeModel = new DefaultTreeModel(root);
        speciesTree = new JTree(treeModel);
        speciesTree.setRootVisible(true);
        speciesTree.setShowsRootHandles(true);
        speciesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        speciesTree.setCellRenderer(new SpeciesTreeCellRenderer());
        SpeciesDragDropSupport.installSpeciesDragSource(speciesTree);
        speciesTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    assignSelectedGenome();
                }
            }
        });
    }

    private JPanel createRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout(0, 12));
        rightPanel.add(createSelectedGenomesPanel(), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout(0, 12));

        JPanel controlsPanel = new JPanel(new BorderLayout(0, 12));
        controlsPanel.add(createParametersPanel(), BorderLayout.NORTH);
        controlsPanel.add(createSettingsPanel(), BorderLayout.SOUTH);

        centerPanel.add(controlsPanel, BorderLayout.NORTH);
        centerPanel.add(createLogPanel(), BorderLayout.CENTER);
        rightPanel.add(centerPanel, BorderLayout.CENTER);
        rightPanel.add(createStartPanel(), BorderLayout.SOUTH);

        return rightPanel;
    }

    private JPanel createSelectedGenomesPanel() {
        JPanel selectedPanel = new JPanel(new BorderLayout(0, 8));
        selectedPanel.setBorder(new TitledBorder("Selected Genomes"));

        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        hintPanel.add(new JLabel("Drag a species from the left tree to Genome 1 or Genome 2, or click to choose the target."));
        selectedPanel.add(hintPanel, BorderLayout.NORTH);

        JPanel slotsPanel = new JPanel(new GridLayout(1, 2, 12, 0));

        genome1Field = createGenomeField();
        genome1Panel = createGenomeSlotPanel("Genome 1", genome1Field, 1);
        slotsPanel.add(genome1Panel);

        genome2Field = createGenomeField();
        genome2Panel = createGenomeSlotPanel("Genome 2", genome2Field, 2);
        slotsPanel.add(genome2Panel);

        selectedPanel.add(slotsPanel, BorderLayout.CENTER);
        selectedPanel.add(createHighlightPanel(), BorderLayout.SOUTH);
        return selectedPanel;
    }

    private JPanel createHighlightPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(4, 0, 0, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 6, 8);
        panel.add(new JLabel("Gene Highlight List"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.35;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 6, 0);
        panel.add(new JLabel("Gene Set"), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.65;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 8);
        highlightGeneArea = new JTextArea(6, 28);
        highlightGeneArea.setLineWrap(false);
        highlightGeneArea.setWrapStyleWord(false);
        highlightGeneArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        JScrollPane highlightScrollPane = new JScrollPane(highlightGeneArea);
        int highlightHeight = 30 * 3;
        highlightScrollPane.setPreferredSize(new Dimension(420, highlightHeight));
        panel.add(highlightScrollPane, gbc);

        geneSetComboBox = new JComboBox<>();
        geneSetComboBox.setPreferredSize(new Dimension(260, 30));
        availableGeneSets = new ArrayList<>();

        JLabel geneSetHintLabel = new JLabel("Links for genes in the list/set will be highlighted.");
        geneSetHintLabel.setFont(SimpleGenomeHubStyle.italic(geneSetHintLabel.getFont(), 11f));
        geneSetHintLabel.setForeground(Color.GRAY);

        JPanel geneSetPanel = new JPanel();
        geneSetPanel.setOpaque(false);
        geneSetPanel.setLayout(new BoxLayout(geneSetPanel, BoxLayout.Y_AXIS));
        geneSetComboBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        geneSetHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        geneSetPanel.add(geneSetComboBox);
        geneSetPanel.add(geneSetHintLabel);

        gbc.gridx = 1;
        gbc.weightx = 0.35;
        gbc.weighty = 0.0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(geneSetPanel, gbc);

        refreshGeneSetOptions();
        geneSetComboBox.addActionListener(e -> applySelectedGeneSet());
        return panel;
    }

    private JTextField createGenomeField() {
        JTextField field = new JTextField("No genome selected");
        field.setEditable(false);
        field.setBackground(Color.WHITE);
        return field;
    }

    private JPanel createGenomeSlotPanel(String title, JTextField field, int slotIndex) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JTextArea chromosomeArea = createChromosomeArea();
        if (slotIndex == 1) {
            genome1ChromosomeArea = chromosomeArea;
        } else {
            genome2ChromosomeArea = chromosomeArea;
        }

        JPanel topPanel = new JPanel(new BorderLayout(0, 6));
        topPanel.setOpaque(false);
        topPanel.add(field, BorderLayout.NORTH);

        JPanel chromosomePanel = new JPanel(new BorderLayout(0, 4));
        chromosomePanel.setOpaque(false);
        chromosomePanel.add(new JLabel("Chromosomes"), BorderLayout.NORTH);
        JScrollPane chromosomeScrollPane = new JScrollPane(chromosomeArea);
        chromosomeScrollPane.setPreferredSize(new Dimension(220, 120));
        chromosomePanel.add(chromosomeScrollPane, BorderLayout.CENTER);
        topPanel.add(chromosomePanel, BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.CENTER);
        panel.setBackground(INACTIVE_SLOT_BACKGROUND);
        panel.setPreferredSize(new Dimension(220, 210));
        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        field.setToolTipText("Drop a species here to assign " + title + ".");
        chromosomeArea.setToolTipText("Drop a species here to auto-fill chromosomes for " + title + ".");
        SpeciesDragDropSupport.installSpeciesDropTarget(panel, species -> assignGenomeToSlot(species, slotIndex));
        SpeciesDragDropSupport.installSpeciesDropTarget(field, species -> assignGenomeToSlot(species, slotIndex));
        SpeciesDragDropSupport.installSpeciesDropTarget(chromosomeArea, species -> assignGenomeToSlot(species, slotIndex));

        MouseAdapter activateSlotListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setActiveGenomeSlot(slotIndex);
            }
        };

        panel.addMouseListener(activateSlotListener);
        field.addMouseListener(activateSlotListener);
        panel.setBorder(createGenomeSlotBorder(title, false));

        return panel;
    }

    private JTextArea createChromosomeArea() {
        JTextArea area = new JTextArea(7, 18);
        area.setLineWrap(false);
        area.setWrapStyleWord(false);
        area.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        area.setText("");
        return area;
    }

    private JPanel createParametersPanel() {
        JPanel parametersPanel = new JPanel(new GridBagLayout());
        parametersPanel.setBorder(new TitledBorder("Parameters"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0.0;

        gbc.gridx = 0;
        gbc.gridy = 0;
        parametersPanel.add(new JLabel("CPU for similarity search"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        cpuField = new JTextField(String.valueOf(Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()))), 14);
        parametersPanel.add(cpuField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        parametersPanel.add(new JLabel("E-value"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        evalueField = new JTextField("1e-5", 14);
        parametersPanel.add(evalueField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        parametersPanel.add(new JLabel("Num of hits"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        numHitsField = new JTextField("5", 14);
        parametersPanel.add(numHitsField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        directPlotCheckBox = new JCheckBox("Direct Plot", true);
        parametersPanel.add(directPlotCheckBox, gbc);

        return parametersPanel;
    }

    private JPanel createStartPanel() {
        JPanel startPanel = new JPanel(new BorderLayout());
        startButton = new JButton("Start");
        startButton.addActionListener(e -> startGenomeCompare());
        startPanel.add(startButton, BorderLayout.CENTER);
        return startPanel;
    }

    private JPanel createSettingsPanel() {
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBorder(new TitledBorder("Settings"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        reorderChromosomesCheckBox = new JCheckBox("Reordering of chromosomes", false);
        reorderChromosomesCheckBox.setToolTipText(
            "Rank chromosome pairs by link count and aligned span before launching Dual Synteny Plot."
        );
        settingsPanel.add(reorderChromosomesCheckBox, gbc);

        return settingsPanel;
    }

    private JPanel createLogPanel() {
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(new TitledBorder("Log"));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        logArea.setText("Log output will appear here.\n");

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        return logPanel;
    }

    private void refreshSpeciesTree() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();

        if (speciesManager != null) {
            List<SpeciesInfo> allSpecies = speciesManager.getAllSpecies();
            for (SpeciesInfo species : allSpecies) {
                root.add(new DefaultMutableTreeNode(species));
            }
        }

        treeModel.reload();
        for (int i = 0; i < speciesTree.getRowCount(); i++) {
            speciesTree.expandRow(i);
        }
    }

    private SpeciesInfo getSelectedSpecies() {
        if (speciesTree.getSelectionPath() == null) {
            return null;
        }

        DefaultMutableTreeNode node =
            (DefaultMutableTreeNode) speciesTree.getSelectionPath().getLastPathComponent();
        Object userObject = node.getUserObject();

        if (userObject instanceof SpeciesInfo) {
            return (SpeciesInfo) userObject;
        }
        return null;
    }

    private void assignSelectedGenome() {
        SpeciesInfo selectedSpecies = getSelectedSpecies();
        if (selectedSpecies == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a genome from Target Species Selection first.",
                "No Genome Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        assignGenomeToSlot(selectedSpecies, activeGenomeSlot);
    }

    private void startGenomeCompare() {
        if (genome1Species == null) {
            JOptionPane.showMessageDialog(this,
                "Please select Genome 1 before starting genome compare.",
                "Genome 1 Missing",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (genome2Species == null) {
            JOptionPane.showMessageDialog(this,
                "Please select Genome 2 before starting genome compare.",
                "Genome 2 Missing",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (genome1Species.equals(genome2Species)) {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Genome 1 and Genome 2 are the same species.\n" +
                    "Continue with a self-comparison?",
                "Confirm Self Comparison",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        final GenomeCompareService.Parameters parameters;
        try {
            parameters = buildParameters();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                ex.getMessage(),
                "Genome Compare Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        startButton.setEnabled(false);
        resetLogArea();
        appendLogLine("Preparing genome compare...");

        SwingWorker<GenomeCompareService.Result, String> worker =
            new SwingWorker<GenomeCompareService.Result, String>() {
                @Override
                protected GenomeCompareService.Result doInBackground() throws Exception {
                    return GenomeCompareService.run(
                        genome1Species,
                        genome2Species,
                        parameters,
                        this::publish
                    );
                }

                @Override
                protected void process(List<String> chunks) {
                    if (chunks.isEmpty()) {
                        return;
                    }

                    for (String chunk : chunks) {
                        appendLogLine(chunk);
                    }
                }

                @Override
                protected void done() {
                    startButton.setEnabled(true);
                    try {
                        GenomeCompareService.Result result = get();
                        File outputDir = result.getOutputDir();
                        StringBuilder message = new StringBuilder();
                        String dualPlotStatus;
                        boolean reorderChromosomes = reorderChromosomesCheckBox.isSelected();

                        try {
                            DualSyntenyPlotLauncher.launch(result, reorderChromosomes);
                            dualPlotStatus = "Dual Synteny Plot: launched";
                        } catch (Exception visualizationEx) {
                            dualPlotStatus = "Dual Synteny Plot: failed to launch\n" +
                                visualizationEx.getMessage();
                        }

                        message.append("Genome Compare completed.\n\n");
                        message.append("Output folder:\n").append(outputDir.getAbsolutePath()).append("\n\n");

                        File collinearityFile = result.getPrimaryCollinearityFile();
                        if (collinearityFile != null) {
                            message.append("Primary collinearity file:\n")
                                .append(collinearityFile.getAbsolutePath())
                                .append("\n");
                        }

                        if (result.getHtmlOutputs().isEmpty()) {
                            message.append("\nDirect Plot: off");
                        } else {
                            message.append("\nDirect Plot: on");
                        }

                        if (reorderChromosomes) {
                            File reorderingReportFile =
                                DualSyntenyChromosomeOrderBuilder.getDefaultReportFile(outputDir);
                            message.append("\nReordering of chromosomes: on");
                            if (reorderingReportFile.isFile()) {
                                message.append("\nChromosome reordering file:\n")
                                    .append(reorderingReportFile.getAbsolutePath());
                            }
                        } else {
                            message.append("\nReordering of chromosomes: off");
                        }

                        message.append("\n").append(dualPlotStatus);
                        appendLogLine("Genome Compare completed.");
                        appendLogLine("Output folder: " + outputDir.getAbsolutePath());
                        appendLogLine("Direct Plot: " + (result.getHtmlOutputs().isEmpty() ? "off" : "on"));
                        appendLogLine("Reordering of chromosomes: " + (reorderChromosomes ? "on" : "off"));
                        appendLogLine(dualPlotStatus);

                        JOptionPane.showMessageDialog(GenomeComparePanel.this,
                            message.toString(),
                            "Genome Compare Finished",
                            JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        appendLogLine("Genome Compare failed: " + cause.getMessage());
                        JOptionPane.showMessageDialog(GenomeComparePanel.this,
                            "Genome Compare failed:\n" + cause.getMessage(),
                            "Genome Compare Error",
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            };

        worker.execute();
    }

    private void resetLogArea() {
        if (logArea == null) {
            return;
        }
        logArea.setText("");
    }

    private void appendLogLine(String text) {
        if (logArea == null || text == null) {
            return;
        }
        logArea.append(text);
        if (!text.endsWith("\n")) {
            logArea.append("\n");
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private GenomeCompareService.Parameters buildParameters() {
        int cpu = parsePositiveInteger(cpuField, "CPU for similarity search");
        double evalue = parsePositiveDouble(evalueField, "E-value");
        int numHits = parsePositiveInteger(numHitsField, "Num of hits");
        List<String> highlightGeneIds = collectHighlightGeneIds();
        List<String> genome1Chromosomes = parseChromosomeList(genome1ChromosomeArea);
        List<String> genome2Chromosomes = parseChromosomeList(genome2ChromosomeArea);

        return new GenomeCompareService.Parameters(
            cpu,
            evalue,
            numHits,
            directPlotCheckBox.isSelected(),
            genome1Chromosomes,
            genome2Chromosomes,
            highlightGeneIds
        );
    }

    private List<String> parseChromosomeList(JTextArea area) {
        LinkedHashSet<String> chromosomes = new LinkedHashSet<>();
        if (area == null) {
            return null;
        }
        String content = area.getText();
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = content.split("[\\s,;]+");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                chromosomes.add(trimmed);
            }
        }
        return new ArrayList<>(chromosomes);
    }

    private List<String> collectHighlightGeneIds() {
        LinkedHashSet<String> resolvedIds = new LinkedHashSet<>();
        List<String> typedIds = GeneSetFileSupport.parseGeneIds(highlightGeneArea.getText());
        if (!typedIds.isEmpty()) {
            HighlightResolutionResult typedResult = resolveHighlightIdsAcrossSelectedGenomes(typedIds);
            if (typedResult.validationResult != null && typedResult.validationResult.hasIssues()) {
                GeneSetGeneIdValidationSupport.showValidationResult(this, typedResult.validationResult);
            }
            if (!typedResult.missingIds.isEmpty()) {
                throw new IllegalArgumentException("Some highlight IDs could not be validated:\n"
                    + String.join("\n", typedResult.missingIds));
            }
            resolvedIds.addAll(typedResult.resolvedIds);
            highlightGeneArea.setText(GeneSetFileSupport.formatGeneIds(new ArrayList<>(resolvedIds)));
        }

        return new ArrayList<>(resolvedIds);
    }

    private HighlightResolutionResult resolveHighlightIdsAcrossSelectedGenomes(List<String> inputIds) {
        LinkedHashSet<String> resolvedIds = new LinkedHashSet<>();
        List<String> missingIds = new ArrayList<>();
        List<String> correctedLines = new ArrayList<>();

        List<SpeciesInfo> targets = new ArrayList<>();
        if (genome1Species != null) {
            targets.add(genome1Species);
        }
        if (genome2Species != null && !genome2Species.equals(genome1Species)) {
            targets.add(genome2Species);
        }

        for (String inputId : inputIds) {
            boolean matched = false;
            for (SpeciesInfo species : targets) {
                try {
                    GeneSetGeneIdValidationSupport.ValidationResult result =
                        GeneSetGeneIdValidationSupport.validateGeneIds(species, Collections.singletonList(inputId));
                    if (!result.getResolvedGeneIds().isEmpty()) {
                        resolvedIds.addAll(result.getResolvedGeneIds());
                        correctedLines.addAll(result.getCorrectedGeneLines());
                        matched = true;
                    }
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Failed to validate highlight ID '" + inputId + "': "
                        + ex.getMessage(), ex);
                }
            }
            if (!matched) {
                missingIds.add(inputId);
            }
        }

        GeneSetGeneIdValidationSupport.ValidationResult validationResult =
            new GeneSetGeneIdValidationSupport.ValidationResult(
                new ArrayList<>(resolvedIds),
                missingIds,
                correctedLines
            );
        return new HighlightResolutionResult(new ArrayList<>(resolvedIds), missingIds, validationResult);
    }

    private int parsePositiveInteger(JTextField field, String fieldName) {
        String value = field.getText().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 1) {
                throw new IllegalArgumentException(fieldName + " must be at least 1.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be an integer.");
        }
    }

    private double parsePositiveDouble(JTextField field, String fieldName) {
        String value = field.getText().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        try {
            double parsed = Double.parseDouble(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(fieldName + " must be greater than 0.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid number.");
        }
    }

    private void populateChromosomeArea(JTextArea area, SpeciesInfo species) {
        if (area == null) {
            return;
        }
        List<String> chromosomes = resolveChromosomeNames(species);
        area.setText(String.join(System.lineSeparator(), chromosomes));
        area.setCaretPosition(0);
    }

    private void assignGenomeToSlot(SpeciesInfo species, int slotIndex) {
        if (species == null) {
            return;
        }

        if (slotIndex == 1) {
            genome1Species = species;
            genome1Field.setText(formatGenomeName(species));
            populateChromosomeArea(genome1ChromosomeArea, species);
        } else {
            genome2Species = species;
            genome2Field.setText(formatGenomeName(species));
            populateChromosomeArea(genome2ChromosomeArea, species);
        }
        setActiveGenomeSlot(slotIndex);
        refreshGeneSetOptions();
    }

    private List<String> resolveChromosomeNames(SpeciesInfo species) {
        List<String> chromosomes = new ArrayList<>();
        if (species == null) {
            return chromosomes;
        }
        if (species.getGenomeData() != null) {
            for (GenomeData.ChromosomeStat chromosomeStat : species.getGenomeData().getChromosomeStats()) {
                if (chromosomeStat == null || chromosomeStat.getName() == null) {
                    continue;
                }
                String name = chromosomeStat.getName().trim();
                if (!name.isEmpty()) {
                    chromosomes.add(name);
                }
            }
        }
        if (!chromosomes.isEmpty()) {
            return chromosomes;
        }

        File genomeFile = species.getGenomeFile();
        if (genomeFile == null || !genomeFile.isFile()) {
            return chromosomes;
        }

        LinkedHashSet<String> fallbackChromosomes = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(genomeFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith(">")) {
                    continue;
                }
                String header = trimmed.substring(1).trim();
                if (header.isEmpty()) {
                    continue;
                }
                fallbackChromosomes.add(header.split("\\s+")[0]);
            }
        } catch (IOException ignored) {
            return chromosomes;
        }
        chromosomes.addAll(fallbackChromosomes);
        return chromosomes;
    }

    private void setActiveGenomeSlot(int slotIndex) {
        activeGenomeSlot = slotIndex;
        updateGenomeSlotStyles();
    }

    private void updateGenomeSlotStyles() {
        updateSingleSlotStyle(genome1Panel, genome1Field, "Genome 1", activeGenomeSlot == 1);
        updateSingleSlotStyle(genome2Panel, genome2Field, "Genome 2", activeGenomeSlot == 2);
    }

    private void updateSingleSlotStyle(JPanel panel, JTextField field, String title, boolean active) {
        if (panel == null || field == null) {
            return;
        }

        panel.setBorder(createGenomeSlotBorder(title, active));
        panel.setBackground(active ? ACTIVE_SLOT_BACKGROUND : INACTIVE_SLOT_BACKGROUND);
        field.setBackground(active ? ACTIVE_SLOT_BACKGROUND : INACTIVE_SLOT_BACKGROUND);
    }

    private Border createGenomeSlotBorder(String title, boolean active) {
        Border outerBorder = new LineBorder(active ? ACTIVE_SLOT_COLOR : INACTIVE_SLOT_BORDER, active ? 2 : 1, true);
        TitledBorder titledBorder = BorderFactory.createTitledBorder(outerBorder, title);
        titledBorder.setTitleColor(active ? ACTIVE_SLOT_COLOR : Color.DARK_GRAY);
        return new CompoundBorder(titledBorder, new EmptyBorder(12, 12, 12, 12));
    }

    private String formatGenomeName(SpeciesInfo species) {
        return species.getSpeciesName() + " (" + species.getVersion() + ")";
    }

    public void initializeWithPrimaryGenome(SpeciesInfo species) {
        if (species == null) {
            return;
        }

        assignGenomeToSlot(species, 1);
        selectTargetSpecies(species);
        setActiveGenomeSlot(2);
    }

    public void selectTargetSpecies(SpeciesInfo species) {
        if (species == null) {
            return;
        }

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            Object userObject = child.getUserObject();
            if (species.equals(userObject)) {
                TreePath path = new TreePath(child.getPath());
                speciesTree.setSelectionPath(path);
                speciesTree.scrollPathToVisible(path);
                break;
            }
        }
    }

    public void cleanup() {
        // Reserved for future genome comparison resources.
    }

    private void refreshGeneSetOptions() {
        if (geneSetComboBox == null) {
            return;
        }

        availableGeneSets = loadAvailableGeneSetChoices();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(GENE_SET_NONE_OPTION);
        for (GeneSetChoice choice : availableGeneSets) {
            model.addElement(choice.getDisplayLabel());
        }

        adjustingGeneSetSelection = true;
        try {
            geneSetComboBox.setModel(model);
            geneSetComboBox.setSelectedIndex(0);
            geneSetComboBox.setEnabled(!availableGeneSets.isEmpty());
        } finally {
            adjustingGeneSetSelection = false;
        }
    }

    private List<GeneSetChoice> loadAvailableGeneSetChoices() {
        List<GeneSetChoice> choices = new ArrayList<>();
        appendGeneSetChoices(choices, genome1Species);
        if (genome2Species != null && !genome2Species.equals(genome1Species)) {
            appendGeneSetChoices(choices, genome2Species);
        }
        return choices;
    }

    private void appendGeneSetChoices(List<GeneSetChoice> choices, SpeciesInfo species) {
        if (species == null) {
            return;
        }

        File geneSetDir = species.getGeneSetDir();
        if (geneSetDir == null || !geneSetDir.isDirectory()) {
            return;
        }

        File[] geneSetFiles = geneSetDir.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase(Locale.ROOT);
            return lowerName.endsWith(GeneSetFileSupport.GENE_SET_SUFFIX);
        });
        if (geneSetFiles == null || geneSetFiles.length == 0) {
            return;
        }

        java.util.Arrays.sort(geneSetFiles, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File geneSetFile : geneSetFiles) {
            choices.add(new GeneSetChoice(species, geneSetFile));
        }
    }

    private void applySelectedGeneSet() {
        if (adjustingGeneSetSelection || geneSetComboBox == null) {
            return;
        }

        GeneSetChoice choice = getSelectedGeneSetChoice();
        if (choice == null) {
            return;
        }

        try {
            GeneSetImportSupport.ImportedGeneSet importedGeneSet =
                GeneSetImportSupport.importIds(choice.species, choice.file, GeneSetImportSupport.OutputIdType.TRANSCRIPT);
            LinkedHashSet<String> mergedIds = new LinkedHashSet<>(GeneSetFileSupport.parseGeneIds(highlightGeneArea.getText()));
            mergedIds.addAll(importedGeneSet.getIds());
            highlightGeneArea.setText(String.join("\n", mergedIds));
            highlightGeneArea.setCaretPosition(0);
        } catch (Exception ex) {
            adjustingGeneSetSelection = true;
            try {
                geneSetComboBox.setSelectedIndex(0);
            } finally {
                adjustingGeneSetSelection = false;
            }

            Throwable cause = ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                ? ex.getCause()
                : ex;
            JOptionPane.showMessageDialog(
                this,
                "Failed to import selected Gene Set:\n" + cause.getMessage(),
                "Import Gene Set Failed",
                cause instanceof GeneSetImportSupport.NoGeneFoundException
                    ? JOptionPane.INFORMATION_MESSAGE
                    : JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private GeneSetChoice getSelectedGeneSetChoice() {
        if (geneSetComboBox == null) {
            return null;
        }
        int selectedIndex = geneSetComboBox.getSelectedIndex();
        if (selectedIndex <= 0) {
            return null;
        }

        int choiceIndex = selectedIndex - 1;
        if (choiceIndex < 0 || choiceIndex >= availableGeneSets.size()) {
            return null;
        }
        return availableGeneSets.get(choiceIndex);
    }

    private static final class GeneSetChoice {
        private final SpeciesInfo species;
        private final File file;

        private GeneSetChoice(SpeciesInfo species, File file) {
            this.species = species;
            this.file = file;
        }

        private String getDisplayLabel() {
            return species.getSpeciesName() + " (" + species.getVersion() + ") / "
                + GeneSetFileSupport.extractDisplayName(file);
        }
    }

    private static final class HighlightResolutionResult {
        private final List<String> resolvedIds;
        private final List<String> missingIds;
        private final GeneSetGeneIdValidationSupport.ValidationResult validationResult;

        private HighlightResolutionResult(List<String> resolvedIds, List<String> missingIds,
                                          GeneSetGeneIdValidationSupport.ValidationResult validationResult) {
            this.resolvedIds = resolvedIds;
            this.missingIds = missingIds;
            this.validationResult = validationResult;
        }
    }

    private class SpeciesTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row,
                                                      boolean hasFocus) {
            Component component = super.getTreeCellRendererComponent(
                tree, value, selected, expanded, leaf, row, hasFocus
            );

            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObject instanceof SpeciesInfo) {
                    setText(formatGenomeName((SpeciesInfo) userObject));
                }
            }

            return component;
        }
    }

    private static final class SpeciesDragDropSupport {

    private static final DataFlavor SPECIES_FLAVOR = createSpeciesFlavor();

    private SpeciesDragDropSupport() {
    }

    public static void installSpeciesDragSource(JTree tree) {
        if (tree == null) {
            return;
        }

        tree.setDragEnabled(true);
        tree.setTransferHandler(new TransferHandler() {
            @Override
            protected Transferable createTransferable(JComponent c) {
                SpeciesInfo species = getSelectedSpecies(tree);
                return species == null ? null : new SpeciesTransferable(species);
            }

            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }
        });
    }

    public static void installSpeciesDropTarget(Component target, java.util.function.Consumer<SpeciesInfo> dropConsumer) {
        if (target == null || dropConsumer == null) {
            return;
        }

        new DropTarget(target, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    if (!dtde.isDataFlavorSupported(SPECIES_FLAVOR)) {
                        dtde.rejectDrop();
                        dtde.dropComplete(false);
                        return;
                    }

                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = dtde.getTransferable();
                    SpeciesInfo species = (SpeciesInfo) transferable.getTransferData(SPECIES_FLAVOR);
                    dropConsumer.accept(species);
                    dtde.dropComplete(true);
                } catch (UnsupportedFlavorException | IOException ex) {
                    dtde.dropComplete(false);
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
        }, true);
    }

    private static SpeciesInfo getSelectedSpecies(JTree tree) {
        if (tree == null) {
            return null;
        }

        TreePath selectionPath = tree.getSelectionPath();
        if (selectionPath == null) {
            return null;
        }

        Object node = selectionPath.getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode) node).getUserObject();
            if (userObject instanceof SpeciesInfo) {
                return (SpeciesInfo) userObject;
            }
        }
        return null;
    }

    private static DataFlavor createSpeciesFlavor() {
        return new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=" + SpeciesInfo.class.getName(),
            "SpeciesInfo"
        );
    }

    private static final class SpeciesTransferable implements Transferable {
        private final SpeciesInfo species;

        private SpeciesTransferable(SpeciesInfo species) {
            this.species = species;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { SPECIES_FLAVOR };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return SPECIES_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return species;
        }
    }
    }
}
