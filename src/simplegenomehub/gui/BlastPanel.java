/*
 * SimpleGenomeHub BLAST Panel
 * GUI interface for BLAST functionality
 */
package simplegenomehub.gui;

import simplegenomehub.blast.*;
import simplegenomehub.blast.BlastConfig.SequenceType;
import simplegenomehub.blast.model.*;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;
import biocjava.bioDoer.BLAST.BlastZone.BlastZone.BlastOnTheFly.OUTBLASTFMT;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Main panel for BLAST functionality
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastPanel extends JPanel {
    
    private static final Logger logger = Logger.getLogger(BlastPanel.class.getName());
    
    // Core components
    private BlastConfig blastConfig;
    private BlastDatabaseManager dbManager;
    private BlastExecutor blastExecutor;
    private SpeciesManager speciesManager;
    
    // GUI components
    private JTree speciesTree;
    private DefaultTreeModel treeModel;
    private JComboBox<SequenceType> sequenceTypeCombo;
    private JTextArea queryTextArea;
    private JScrollPane queryScrollPane;
    private JComboBox<BlastExecutor.BlastType> blastTypeCombo;
    private JComboBox<OUTBLASTFMT> outputFormatCombo;
    private JSpinner threadsSpinner;
    private JTextField evalueField;
    private JSpinner maxTargetsSpinner;
    private JCheckBox shortQueryCheckBox;
    private JButton executeButton;
    private JProgressBar progressBar;
    private JTextArea statusArea;
    private JScrollPane statusScrollPane;
    private BlastResultsPanel resultsPanel;
    
    /**
     * Initialize BLAST panel
     */
    public BlastPanel(SpeciesManager speciesManager) {
        this.speciesManager = speciesManager;
        
        try {
            initializeBlastComponents();
            initializeGUI();
            refreshSpeciesTree();
            
            logger.info("BLAST panel initialization completed");
        } catch (Exception e) {
            logger.severe("BLAST panel initialization failed: " + e.getMessage());
            showErrorMessage("BLAST Initialization Failed", e.getMessage());
        }
    }
    
    /**
     * Initialize BLAST core components
     */
    private void initializeBlastComponents() throws Exception {
        blastConfig = new BlastConfig();
        dbManager = new BlastDatabaseManager(blastConfig);
        blastExecutor = new BlastExecutor(blastConfig, dbManager);
    }
    
    /**
     * Initialize GUI components
     */
    private void initializeGUI() {
        setLayout(new BorderLayout());
        
        // Create main split pane
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        SimpleGenomeHubUi.styleSplitPane(mainSplitPane);
        mainSplitPane.setLeftComponent(createLeftPanel());
        mainSplitPane.setRightComponent(createRightPanel());
        mainSplitPane.setDividerLocation(400);
        
        add(mainSplitPane, BorderLayout.CENTER);
        add(createStatusPanel(), BorderLayout.SOUTH);
    }
    
    /**
     * Create left panel with species selection and query
     */
    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        
        // Species selection panel
        JPanel speciesPanel = new JPanel(new BorderLayout());
        speciesPanel.setBorder(new TitledBorder("Target Species Selection"));
        
        // Species tree
        createSpeciesTree();
        JScrollPane treeScrollPane = new JScrollPane(speciesTree);
        treeScrollPane.setPreferredSize(new Dimension(300, 250));
        speciesPanel.add(treeScrollPane, BorderLayout.CENTER);
        
        // Sequence type selection
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.add(new JLabel("Database Type:"));
        sequenceTypeCombo = new JComboBox<>(SequenceType.values());
        sequenceTypeCombo.setSelectedItem(SequenceType.PROTEIN);
        sequenceTypeCombo.setPreferredSize(new Dimension(140, 30));
        sequenceTypeCombo.addActionListener(e -> syncBlastTypeWithSequenceType());
        typePanel.add(sequenceTypeCombo);
        speciesPanel.add(typePanel, BorderLayout.SOUTH);
        
        leftPanel.add(speciesPanel, BorderLayout.NORTH);
        
        // Query input panel - now gets more space
        leftPanel.add(createQueryPanel(), BorderLayout.CENTER);
        
        return leftPanel;
    }
    
    /**
     * Create species tree
     */
    private void createSpeciesTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Species List");
        treeModel = new DefaultTreeModel(root);
        speciesTree = new JTree(treeModel);
        speciesTree.setRootVisible(true);
        speciesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        speciesTree.setShowsRootHandles(true);
    }
    
    /**
     * Create compact parameters panel for right side
     */
    private JComponent createCompactParametersPanel() {
        final JPanel parametersPanel = new JPanel();
        parametersPanel.setBorder(new TitledBorder("BLAST Parameters"));
        parametersPanel.setLayout(new GridBagLayout());
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 8, 3, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Row 1: BLAST Type, Output Format, E-value
        gbc.gridx = 0; gbc.gridy = 0;
        parametersPanel.add(new JLabel("BLAST Type:"), gbc);
        gbc.gridx = 1;
        blastTypeCombo = new JComboBox<>(BlastExecutor.BlastType.values());
        blastTypeCombo.setSelectedItem(BlastExecutor.BlastType.BLASTP);
        blastTypeCombo.setPreferredSize(new Dimension(260, 30));
        SimpleGenomeHubUi.setComboBoxDisplayWidth(blastTypeCombo, 260);
        
        // Add tooltips for BLAST types
        blastTypeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BlastExecutor.BlastType) {
                    BlastExecutor.BlastType type = (BlastExecutor.BlastType) value;
                    setToolTipText(getBlastTypeTooltip(type));
                }
                return this;
            }
        });
        
        parametersPanel.add(blastTypeCombo, gbc);
        
        gbc.gridx = 2;
        parametersPanel.add(new JLabel("Output:"), gbc);
        gbc.gridx = 3;
        outputFormatCombo = new JComboBox<>(OUTBLASTFMT.values());
        outputFormatCombo.setSelectedItem(OUTBLASTFMT.XML);
        outputFormatCombo.setPreferredSize(new Dimension(150, 30));
        parametersPanel.add(outputFormatCombo, gbc);
        
        gbc.gridx = 4;
        parametersPanel.add(new JLabel("E-value:"), gbc);
        gbc.gridx = 5;
        evalueField = new JTextField("1e-5", 8);
        parametersPanel.add(evalueField, gbc);
        
        // Row 2: Threads, Max Targets, Short Query Mode
        gbc.gridx = 0; gbc.gridy = 1;
        parametersPanel.add(new JLabel("Threads:"), gbc);
        gbc.gridx = 1;
        threadsSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 64, 1));
        threadsSpinner.setPreferredSize(new Dimension(100, 25));
        parametersPanel.add(threadsSpinner, gbc);
        
        gbc.gridx = 2;
        parametersPanel.add(new JLabel("Max Targets:"), gbc);
        gbc.gridx = 3;
        maxTargetsSpinner = new JSpinner(new SpinnerNumberModel(500, 1, 10000, 10));
        maxTargetsSpinner.setPreferredSize(new Dimension(80, 25));
        parametersPanel.add(maxTargetsSpinner, gbc);
        
        gbc.gridx = 4; gbc.gridwidth = 2;
        shortQueryCheckBox = new JCheckBox("Short Query Mode");
        parametersPanel.add(shortQueryCheckBox, gbc);

        JScrollPane parametersScrollPane = new JScrollPane(parametersPanel) {
            @Override
            public Dimension getPreferredSize() {
                Dimension contentSize = parametersPanel.getPreferredSize();
                Insets insets = getInsets();
                int scrollbarHeight = getHorizontalScrollBar().getPreferredSize().height;
                int width = contentSize.width + insets.left + insets.right;
                int height = contentSize.height + insets.top + insets.bottom + scrollbarHeight + 6;
                return new Dimension(width, height);
            }
        };
        parametersScrollPane.setBorder(BorderFactory.createEmptyBorder());
        parametersScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        parametersScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        parametersScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        return parametersScrollPane;
    }
    
    /**
     * Create parameters panel with fixed layout (unused)
     */
    private JPanel createParametersPanel_Old() {
        JPanel parametersPanel = new JPanel(new GridBagLayout());
        parametersPanel.setBorder(new TitledBorder("BLAST Parameters"));
        parametersPanel.setPreferredSize(new Dimension(280, 280));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 8, 5, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // BLAST type
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0.0;
        JLabel blastTypeLabel = new JLabel("BLAST Type:");
        blastTypeLabel.setPreferredSize(new Dimension(100, 25));
        parametersPanel.add(blastTypeLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        blastTypeCombo = new JComboBox<>(BlastExecutor.BlastType.values());
        blastTypeCombo.setSelectedItem(BlastExecutor.BlastType.BLASTP);
        blastTypeCombo.setPreferredSize(new Dimension(150, 25));
        parametersPanel.add(blastTypeCombo, gbc);
        
        // Output format
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0.0;
        JLabel outputLabel = new JLabel("Output Format:");
        outputLabel.setPreferredSize(new Dimension(100, 25));
        parametersPanel.add(outputLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        outputFormatCombo = new JComboBox<>(OUTBLASTFMT.values());
        outputFormatCombo.setSelectedItem(OUTBLASTFMT.XML);
        outputFormatCombo.setPreferredSize(new Dimension(150, 25));
        parametersPanel.add(outputFormatCombo, gbc);
        
        // Add separator
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2; gbc.weightx = 1.0;
        gbc.insets = new Insets(10, 8, 10, 8);
        JSeparator separator = new JSeparator();
        parametersPanel.add(separator, gbc);
        
        // Reset for advanced parameters
        gbc.gridwidth = 1;
        gbc.insets = new Insets(5, 8, 5, 8);
        
        // Threads
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.weightx = 0.0;
        JLabel threadsLabel = new JLabel("Threads:");
        threadsLabel.setPreferredSize(new Dimension(100, 25));
        parametersPanel.add(threadsLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        threadsSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 64, 1));
        threadsSpinner.setPreferredSize(new Dimension(100, 25));
        parametersPanel.add(threadsSpinner, gbc);
        
        // E-value
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.weightx = 0.0;
        JLabel evalueLabel = new JLabel("E-value:");
        evalueLabel.setPreferredSize(new Dimension(100, 25));
        parametersPanel.add(evalueLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        evalueField = new JTextField("1e-5", 10);
        parametersPanel.add(evalueField, gbc);
        
        // Max targets
        gbc.gridx = 0; gbc.gridy = 5;
        gbc.weightx = 0.0;
        JLabel maxTargetsLabel = new JLabel("Max Targets:");
        maxTargetsLabel.setPreferredSize(new Dimension(100, 25));
        parametersPanel.add(maxTargetsLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0;
        maxTargetsSpinner = new JSpinner(new SpinnerNumberModel(500, 1, 10000, 10));
        maxTargetsSpinner.setPreferredSize(new Dimension(100, 25));
        parametersPanel.add(maxTargetsSpinner, gbc);
        
        // Short query checkbox
        gbc.gridx = 0; gbc.gridy = 6;
        gbc.gridwidth = 2; gbc.weightx = 1.0;
        gbc.insets = new Insets(10, 8, 5, 8);
        shortQueryCheckBox = new JCheckBox("Short Query Mode");
        shortQueryCheckBox.setPreferredSize(new Dimension(200, 25));
        parametersPanel.add(shortQueryCheckBox, gbc);
        
        // Add vertical glue to push everything to the top
        gbc.gridx = 0; gbc.gridy = 7;
        gbc.gridwidth = 2; gbc.weightx = 1.0; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        parametersPanel.add(Box.createVerticalGlue(), gbc);
        
        return parametersPanel;
    }
    
    /**
     * Create query input panel
     */
    private JPanel createQueryPanel() {
        JPanel queryPanel = new JPanel(new BorderLayout());
        queryPanel.setBorder(new TitledBorder("Query Sequence"));
        
        queryTextArea = new JTextArea(10, 40); // Adjust height to accommodate button
        queryTextArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        queryTextArea.setLineWrap(true);
        queryTextArea.setWrapStyleWord(true);
        queryTextArea.setText(">Query_Sequence\nPlease input FASTA format sequence here");
        
        queryScrollPane = new JScrollPane(queryTextArea);
        queryPanel.add(queryScrollPane, BorderLayout.CENTER);
        
        // Execute button at bottom - more intuitive user flow
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        executeButton = new JButton("BLAST");
        executeButton.setPreferredSize(new Dimension(100, 35));
        executeButton.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        executeButton.setBackground(new Color(255, 165, 0)); // Orange color
        executeButton.setOpaque(true);
        executeButton.setBorderPainted(false);
        executeButton.setForeground(Color.WHITE);
        executeButton.addActionListener(new ExecuteBlastListener());
        buttonPanel.add(executeButton);
        
        queryPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        return queryPanel;
    }
    
    /**
     * Create right panel with parameters and results
     */
    private JPanel createRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        
        // Parameters panel at top
        JComponent parametersPanel = createCompactParametersPanel();
        rightPanel.add(parametersPanel, BorderLayout.NORTH);
        
        // Results panel in center
        resultsPanel = new BlastResultsPanel();
        rightPanel.add(resultsPanel, BorderLayout.CENTER);
        
        return rightPanel;
    }
    
    /**
     * Create status panel
     */
    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                return new Dimension(size.width, 140);
            }

            @Override
            public Dimension getMinimumSize() {
                Dimension size = super.getMinimumSize();
                return new Dimension(size.width, 140);
            }
        };
        statusPanel.setBorder(new TitledBorder("Status Information"));
        
        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        statusPanel.add(progressBar, BorderLayout.NORTH);
        
        // Status text area
        statusArea = new JTextArea(4, 50);
        statusArea.setEditable(false);
        statusArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        statusScrollPane = new JScrollPane(statusArea);
        statusPanel.add(statusScrollPane, BorderLayout.CENTER);
        
        return statusPanel;
    }
    
    /**
     * Refresh species tree
     */
    private void refreshSpeciesTree() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();
        
        List<SpeciesInfo> allSpecies = speciesManager.getAllSpecies();
        for (SpeciesInfo species : allSpecies) {
            DefaultMutableTreeNode speciesNode = new DefaultMutableTreeNode(species);
            root.add(speciesNode);
        }
        
        treeModel.reload();
        
        // Expand all nodes
        for (int i = 0; i < speciesTree.getRowCount(); i++) {
            speciesTree.expandRow(i);
        }
    }
    
    /**
     * Get selected species from tree
     */
    private SpeciesInfo getSelectedSpecies() {
        if (speciesTree.getSelectionPath() == null) {
            return null;
        }
        
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) speciesTree.getSelectionPath().getLastPathComponent();
        Object userObject = node.getUserObject();
        
        if (userObject instanceof SpeciesInfo) {
            return (SpeciesInfo) userObject;
        }
        
        return null;
    }
    
    /**
     * Create BLAST parameters from GUI
     */
    private BlastParameters createParametersFromGUI() {
        BlastParameters params = new BlastParameters();
        
        params.setNumThreads((Integer) threadsSpinner.getValue());
        params.setMaxTargetSeqs((Integer) maxTargetsSpinner.getValue());
        params.setOutputFormat((OUTBLASTFMT) outputFormatCombo.getSelectedItem());
        params.setShortQuery(shortQueryCheckBox.isSelected());
        
        try {
            double evalue = Double.parseDouble(evalueField.getText().trim());
            params.setEvalue(evalue);
        } catch (NumberFormatException e) {
            params.setEvalue(1e-5);
            evalueField.setText("1e-5");
        }
        
        return params;
    }
    
    /**
     * Add status message
     */
    private void addStatusMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            statusArea.append("[" + java.time.LocalTime.now().toString() + "] " + message + "\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }
    
    /**
     * Show error message
     */
    private void showErrorMessage(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }
    
    /**
     * Execute BLAST listener
     */
    private class ExecuteBlastListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            executeBlast();
        }
    }
    
    /**
     * Execute BLAST query
     */
    private void executeBlast() {
        // Validate input
        SpeciesInfo selectedSpecies = getSelectedSpecies();
        if (selectedSpecies == null) {
            showErrorMessage("Input Error", "Please select a target species");
            return;
        }
        
        String queryText = queryTextArea.getText().trim();
        if (queryText.isEmpty() || queryText.equals(">Query_Sequence\nPlease input FASTA format sequence here")) {
            showErrorMessage("Input Error", "Please input query sequence");
            return;
        }
        
        // Disable execute button
        executeButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("Running...");
        
        // Create BLAST query
        BlastQuery query = new BlastQuery();
        query.setQuerySequence(queryText);
        query.setTargetSpecies(selectedSpecies);
        query.setTargetSequenceType((SequenceType) sequenceTypeCombo.getSelectedItem());
        query.setParameters(createParametersFromGUI());
        
        addStatusMessage("Starting BLAST query: " + selectedSpecies.getSpeciesName() + 
                        " (" + query.getTargetSequenceType() + ")");
        
        // Display the command that will be executed
        String blastCommand = generateBlastCommand(query, selectedSpecies);
        addStatusMessage("Command: " + blastCommand);
        
        // Get BLAST type for results display
        BlastExecutor.BlastType blastType = (BlastExecutor.BlastType) blastTypeCombo.getSelectedItem();

        // Get database path for tree building
        File databasePath = null;
        try {
            simplegenomehub.blast.model.BlastDatabase targetDb = dbManager.ensureDatabaseExists(
                selectedSpecies,
                query.getTargetSequenceType()
            );
            databasePath = new File(targetDb.getDatabasePath());
            addStatusMessage("Using database: " + databasePath.getAbsolutePath());
        } catch (Exception e) {
            addStatusMessage("Warning: Could not determine database path: " + e.getMessage());
        }

        // Make database path final for lambda access
        final File finalDatabasePath = databasePath;

        // Execute asynchronously
        CompletableFuture<BlastResult> future = blastExecutor.executeBlastAsync(query);

        future.whenComplete((result, throwable) -> {
            SwingUtilities.invokeLater(() -> {
                progressBar.setIndeterminate(false);
                executeButton.setEnabled(true);

                if (throwable != null) {
                    progressBar.setString("Failed");
                    addStatusMessage("BLAST execution failed: " + throwable.getMessage());
                    showErrorMessage("BLAST Execution Failed", throwable.getMessage());
                } else {
                    progressBar.setString("Completed");
                    addStatusMessage("BLAST execution completed, found " + result.getHits().size() + " hits");
                    addStatusMessage("Query ID: " + result.getQueryId());

                    // Display results with blast type information and database path
                    resultsPanel.setResults(result, blastType, finalDatabasePath);
                }
            });
        });
    }
    
    /**
     * Generate BLAST command string for display
     */
    private String generateBlastCommand(BlastQuery query, SpeciesInfo targetSpecies) {
        StringBuilder cmd = new StringBuilder();
        
        // Get BLAST type
        BlastExecutor.BlastType blastType = (BlastExecutor.BlastType) blastTypeCombo.getSelectedItem();
        cmd.append(blastType.getCommand());
        
        // Add basic parameters
        cmd.append(" -query <query_file>");
        cmd.append(" -db ").append(targetSpecies.getSpeciesName()).append("/").append(query.getTargetSequenceType());
        cmd.append(" -out <output_file>");
        cmd.append(" -outfmt 5"); // XML format
        
        // Add parameters from GUI
        BlastParameters params = query.getParameters();
        cmd.append(" -evalue ").append(params.getEvalue());
        cmd.append(" -num_threads ").append(params.getNumThreads());
        cmd.append(" -max_target_seqs ").append(params.getMaxTargetSeqs());
        
        if (params.isShortQuery()) {
            cmd.append(" -task ").append(blastType.getCommand()).append("-short");
        }
        
        return cmd.toString();
    }
    
    /**
     * Get tooltip text for BLAST type
     */
    private String getBlastTypeTooltip(BlastExecutor.BlastType type) {
        switch (type) {
            case BLASTN:
                return "BLASTN - Nucleotide query vs nucleotide database";
            case BLASTP:
                return "BLASTP - Protein query vs protein database";
            case BLASTX:
                return "BLASTX - Nucleotide query translated vs protein database";
            case TBLASTN:
                return "TBLASTN - Protein query vs translated nucleotide database";
            case TBLASTX:
                return "TBLASTX - Translated nucleotide query vs translated nucleotide database";
            default:
                return "BLAST search type";
        }
    }
    
    /**
     * Select target species in the tree
     */
    public void selectTargetSpecies(SpeciesInfo targetSpecies) {
        if (targetSpecies == null) return;
        
        // Find and select the target species in the tree
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) root.getChildAt(i);
            Object userObject = childNode.getUserObject();
            
            if (userObject instanceof SpeciesInfo) {
                SpeciesInfo species = (SpeciesInfo) userObject;
                if (species.getSpeciesName().equals(targetSpecies.getSpeciesName())) {
                    // Select this node in the tree
                    speciesTree.setSelectionPath(new javax.swing.tree.TreePath(childNode.getPath()));
                    speciesTree.scrollPathToVisible(new javax.swing.tree.TreePath(childNode.getPath()));
                    logger.info("Auto-selected target species: " + targetSpecies.getSpeciesName());
                    break;
                }
            }
        }
    }

    /**
     * Preselect species, database type, and query sequence.
     */
    public void prefillQuery(SpeciesInfo targetSpecies, SequenceType sequenceType, String querySequence) {
        if (targetSpecies != null) {
            selectTargetSpecies(targetSpecies);
        }

        if (sequenceType != null && sequenceTypeCombo != null) {
            sequenceTypeCombo.setSelectedItem(sequenceType);
            syncBlastTypeWithSequenceType();
        }

        if (querySequence != null && !querySequence.trim().isEmpty() && queryTextArea != null) {
            queryTextArea.setText(querySequence.trim());
            queryTextArea.setCaretPosition(0);
        }
    }

    private void syncBlastTypeWithSequenceType() {
        if (sequenceTypeCombo == null || blastTypeCombo == null) {
            return;
        }

        SequenceType selectedType = (SequenceType) sequenceTypeCombo.getSelectedItem();
        if (selectedType == null) {
            return;
        }

        BlastExecutor.BlastType suggestedType =
            selectedType == SequenceType.PROTEIN ? BlastExecutor.BlastType.BLASTP : BlastExecutor.BlastType.BLASTN;
        blastTypeCombo.setSelectedItem(suggestedType);
    }
    
    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (dbManager != null) {
            dbManager.shutdown();
        }
        if (blastExecutor != null) {
            blastExecutor.shutdown();
        }
    }
}
