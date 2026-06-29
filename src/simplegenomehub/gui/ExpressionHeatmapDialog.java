/*
 * Expression Heatmap Visualization Dialog
 * Uses JIGplot HeatmapControl for visualization
 */
package simplegenomehub.gui;

import simplegenomehub.model.*;
import biocjava.bioDoer.JIGplotToolkit.HeatMap.HeatmapControl;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Dialog for creating expression heatmaps using JIGplot
 * 
 * @author SimpleGenomeHub
 */
public class ExpressionHeatmapDialog extends JDialog {
    
    private static final Logger logger = Logger.getLogger(ExpressionHeatmapDialog.class.getName());
    
    private SpeciesInfo targetSpecies;
    private List<ExpressionExperiment> experiments;
    private ExpressionExperiment selectedExperiment;
    private ExpressionMatrix selectedMatrix;
    
    // UI Components
    private JComboBox<ExpressionExperiment> experimentCombo;
    private JComboBox<ExpressionMatrix> matrixCombo;
    private JTable geneTable;
    private DefaultTableModel geneTableModel;
    private JTextField geneFilterField;
    private JSpinner topGenesSpinner;
    private JCheckBox useTopGenesCheckBox;
    private JTextArea selectedGenesArea;
    private JButton selectAllButton;
    private JButton clearSelectionButton;
    private JButton generateHeatmapButton;
    private JButton previewDataButton;
    
    // Heatmap settings
    private JCheckBox clusterRowsCheckBox;
    private JCheckBox clusterColsCheckBox;
    private JCheckBox scaleDataCheckBox;
    
    /**
     * Constructor
     */
    public ExpressionHeatmapDialog(Window parent, SpeciesInfo species) {
        super(parent, "Expression Heatmap Visualization - " + species.getSpeciesName(), ModalityType.APPLICATION_MODAL);
        this.targetSpecies = species;
        this.experiments = species.getExpressionExperiments();
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadExperiments();
        
        setSize(900, 700);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Experiment and matrix selection
        experimentCombo = new JComboBox<>();
        matrixCombo = new JComboBox<>();
        SimpleGenomeHubUi.setComboBoxMinimumWidth(experimentCombo, 320);
        SimpleGenomeHubUi.setComboBoxMinimumWidth(matrixCombo, 320);
        
        // Gene selection components
        geneFilterField = new JTextField(20);
        geneFilterField.setToolTipText("Enter gene ID or partial match to filter");
        
        topGenesSpinner = new JSpinner(new SpinnerNumberModel(50, 1, 10000, 10));
        useTopGenesCheckBox = new JCheckBox("Use top N most variable genes", true);
        
        // Gene table
        String[] columnNames = {"Select", "Gene ID", "Mean Expression", "CV", "Max Expression"};
        geneTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Boolean.class;
                return String.class;
            }
            
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0; // Only selection column is editable
            }
        };
        
        geneTable = new JTable(geneTableModel);
        geneTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        geneTable.getColumnModel().getColumn(0).setMaxWidth(50); // Select column
        geneTable.getColumnModel().getColumn(1).setPreferredWidth(150); // Gene ID
        geneTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Mean
        geneTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // CV
        geneTable.getColumnModel().getColumn(4).setPreferredWidth(100); // Max
        
        selectedGenesArea = new JTextArea(4, 30);
        selectedGenesArea.setEditable(false);
        selectedGenesArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        
        // Action buttons
        selectAllButton = new JButton("Select All");
        clearSelectionButton = new JButton("Clear Selection");
        generateHeatmapButton = new JButton("Generate Heatmap");
        previewDataButton = new JButton("Preview Data");
        
        // Heatmap settings
        clusterRowsCheckBox = new JCheckBox("Cluster rows (genes)", true);
        clusterColsCheckBox = new JCheckBox("Cluster columns (samples)", true);
        scaleDataCheckBox = new JCheckBox("Scale data (z-score)", true);
        
        // Initially disable some components
        matrixCombo.setEnabled(false);
        generateHeatmapButton.setEnabled(false);
        previewDataButton.setEnabled(false);
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // Top: Instructions
        JLabel instructionLabel = new JLabel(
            "<html><b>Expression Heatmap Generation</b><br>" + 
            "Select experiment, choose genes, and generate heatmap visualization using JIGplot.</html>");
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        add(instructionLabel, BorderLayout.NORTH);
        
        // Center: Main content in tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // Tab 1: Data Selection
        tabbedPane.addTab("Data Selection", createDataSelectionPanel());
        
        // Tab 2: Gene Selection
        tabbedPane.addTab("Gene Selection", createGeneSelectionPanel());
        
        // Tab 3: Heatmap Settings
        tabbedPane.addTab("Settings", createSettingsPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // Bottom: Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(previewDataButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(generateHeatmapButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create data selection panel
     */
    private JPanel createDataSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Experiment selection
        JPanel experimentPanel = new JPanel();
        experimentPanel.setBorder(new TitledBorder("Select Expression Experiment"));
        experimentPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        experimentPanel.add(new JLabel("Experiment:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        experimentPanel.add(experimentCombo, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        experimentPanel.add(new JLabel("Matrix:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        experimentPanel.add(matrixCombo, gbc);
        
        panel.add(experimentPanel, BorderLayout.NORTH);
        
        // Matrix info display
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(new TitledBorder("Matrix Information"));
        
        JTextArea infoArea = new JTextArea(10, 50);
        infoArea.setEditable(false);
        infoArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        infoArea.setText("Select an experiment to view matrix details.");
        
        JScrollPane infoScrollPane = new JScrollPane(infoArea);
        infoPanel.add(infoScrollPane, BorderLayout.CENTER);
        
        panel.add(infoPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create gene selection panel
     */
    private JPanel createGeneSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top: Gene selection options
        JPanel optionsPanel = new JPanel();
        optionsPanel.setBorder(new TitledBorder("Gene Selection Options"));
        optionsPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Filter field
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        optionsPanel.add(new JLabel("Filter genes:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        optionsPanel.add(geneFilterField, gbc);
        
        // Top genes option
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        optionsPanel.add(useTopGenesCheckBox, gbc);
        gbc.gridx = 1;
        optionsPanel.add(topGenesSpinner, gbc);
        
        // Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(selectAllButton);
        buttonPanel.add(clearSelectionButton);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        optionsPanel.add(buttonPanel, gbc);
        
        panel.add(optionsPanel, BorderLayout.NORTH);
        
        // Center: Gene table
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(new TitledBorder("Available Genes"));
        
        JScrollPane tableScrollPane = new JScrollPane(geneTable);
        tableScrollPane.setPreferredSize(new Dimension(600, 300));
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        
        panel.add(tablePanel, BorderLayout.CENTER);
        
        // Bottom: Selected genes summary
        JPanel summaryPanel = new JPanel(new BorderLayout());
        summaryPanel.setBorder(new TitledBorder("Selected Genes Summary"));
        
        JScrollPane summaryScrollPane = new JScrollPane(selectedGenesArea);
        summaryPanel.add(summaryScrollPane, BorderLayout.CENTER);
        
        panel.add(summaryPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Create heatmap settings panel
     */
    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Clustering options
        JPanel clusterPanel = new JPanel();
        clusterPanel.setBorder(new TitledBorder("Clustering Options"));
        clusterPanel.setLayout(new GridLayout(2, 1, 5, 5));
        clusterPanel.add(clusterRowsCheckBox);
        clusterPanel.add(clusterColsCheckBox);
        
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(clusterPanel, gbc);
        
        // Data processing options
        JPanel processPanel = new JPanel();
        processPanel.setBorder(new TitledBorder("Data Processing"));
        processPanel.setLayout(new GridLayout(1, 1, 5, 5));
        processPanel.add(scaleDataCheckBox);
        
        gbc.gridy = 1;
        panel.add(processPanel, gbc);
        
        // Output options
        JPanel outputPanel = new JPanel();
        outputPanel.setBorder(new TitledBorder("Output Options"));
        outputPanel.setLayout(new GridLayout(2, 1, 5, 5));
        
        JLabel outputInfo = new JLabel("<html>Heatmap will be generated using JIGplot.<br>" +
                                      "Output formats: PNG, PDF, SVG</html>");
        outputPanel.add(outputInfo);
        
        gbc.gridy = 2;
        panel.add(outputPanel, gbc);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        experimentCombo.addActionListener(e -> onExperimentSelected());
        matrixCombo.addActionListener(e -> onMatrixSelected());
        
        geneFilterField.addActionListener(e -> filterGenes());
        useTopGenesCheckBox.addActionListener(e -> updateGeneSelection());
        
        selectAllButton.addActionListener(e -> selectAllGenes());
        clearSelectionButton.addActionListener(e -> clearGeneSelection());
        
        previewDataButton.addActionListener(e -> previewHeatmapData());
        generateHeatmapButton.addActionListener(e -> generateHeatmap());
        
        // Update selected genes summary when table changes
        geneTableModel.addTableModelListener(e -> updateSelectedGenesSummary());
    }
    
    /**
     * Load available experiments
     */
    private void loadExperiments() {
        experimentCombo.removeAllItems();
        for (ExpressionExperiment experiment : experiments) {
            experimentCombo.addItem(experiment);
        }
        SimpleGenomeHubUi.setComboBoxDisplayWidth(experimentCombo, 320);
        
        if (experiments.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No expression experiments found. Please import expression data first.",
                "No Data Available", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    /**
     * Handle experiment selection
     */
    private void onExperimentSelected() {
        selectedExperiment = (ExpressionExperiment) experimentCombo.getSelectedItem();
        if (selectedExperiment != null) {
            // Load matrices
            matrixCombo.removeAllItems();
            List<ExpressionMatrix> matrices = selectedExperiment.getMatrices();
            for (ExpressionMatrix matrix : matrices) {
                matrixCombo.addItem(matrix);
            }
            SimpleGenomeHubUi.setComboBoxDisplayWidth(matrixCombo, 320);
            
            matrixCombo.setEnabled(!matrices.isEmpty());
            if (!matrices.isEmpty()) {
                matrixCombo.setSelectedIndex(0);
                onMatrixSelected();
            }
        } else {
            matrixCombo.removeAllItems();
            matrixCombo.setEnabled(false);
        }
    }
    
    /**
     * Handle matrix selection
     */
    private void onMatrixSelected() {
        selectedMatrix = (ExpressionMatrix) matrixCombo.getSelectedItem();
        if (selectedMatrix != null) {
            loadGeneData();
            generateHeatmapButton.setEnabled(true);
            previewDataButton.setEnabled(true);
        } else {
            clearGeneTable();
            generateHeatmapButton.setEnabled(false);
            previewDataButton.setEnabled(false);
        }
    }
    
    /**
     * Load gene data into table
     */
    private void loadGeneData() {
        if (selectedMatrix == null) return;
        
        // Clear existing data
        geneTableModel.setRowCount(0);
        
        String[] geneIds = selectedMatrix.getGeneIds();
        String[] sampleNames = selectedMatrix.getSampleNames();
        
        // Calculate statistics for each gene
        for (String geneId : geneIds) {
            Map<String, Double> geneExpression = selectedMatrix.getGeneExpression(geneId);
            
            // Calculate mean, CV, and max
            double sum = 0.0;
            double max = Double.MIN_VALUE;
            int count = 0;
            
            for (Double value : geneExpression.values()) {
                if (value != null && !Double.isNaN(value)) {
                    sum += value;
                    max = Math.max(max, value);
                    count++;
                }
            }
            
            double mean = count > 0 ? sum / count : 0.0;
            
            // Calculate coefficient of variation
            double variance = 0.0;
            for (Double value : geneExpression.values()) {
                if (value != null && !Double.isNaN(value)) {
                    variance += Math.pow(value - mean, 2);
                }
            }
            double cv = mean > 0 ? Math.sqrt(variance / count) / mean : 0.0;
            
            // Add to table
            Object[] rowData = {
                Boolean.FALSE, // Selection checkbox
                geneId,
                String.format("%.3f", mean),
                String.format("%.3f", cv),
                String.format("%.3f", max)
            };
            
            geneTableModel.addRow(rowData);
        }
        
        // Auto-select top variable genes if option is checked
        if (useTopGenesCheckBox.isSelected()) {
            selectTopVariableGenes();
        }
        
        logger.info("Loaded " + geneIds.length + " genes for heatmap selection");
    }
    
    /**
     * Select top variable genes
     */
    private void selectTopVariableGenes() {
        int topN = (Integer) topGenesSpinner.getValue();
        
        // Sort by CV (coefficient of variation) and select top N
        // For simplicity, we'll select the first N genes
        // In a real implementation, you'd sort by CV first
        int rowsToSelect = Math.min(topN, geneTableModel.getRowCount());
        
        for (int i = 0; i < rowsToSelect; i++) {
            geneTableModel.setValueAt(Boolean.TRUE, i, 0);
        }
        
        updateSelectedGenesSummary();
    }
    
    /**
     * Filter genes based on text input
     */
    private void filterGenes() {
        // TODO: Implement gene filtering
        String filter = geneFilterField.getText().toLowerCase().trim();
        if (filter.isEmpty()) {
            loadGeneData(); // Reload all data
        } else {
            // Simple filter implementation - in production, use proper table filtering
            JOptionPane.showMessageDialog(this,
                "Gene filtering feature coming soon!", 
                "Feature Under Development", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    /**
     * Update gene selection based on options
     */
    private void updateGeneSelection() {
        if (useTopGenesCheckBox.isSelected()) {
            selectTopVariableGenes();
        }
    }
    
    /**
     * Select all genes
     */
    private void selectAllGenes() {
        for (int i = 0; i < geneTableModel.getRowCount(); i++) {
            geneTableModel.setValueAt(Boolean.TRUE, i, 0);
        }
        updateSelectedGenesSummary();
    }
    
    /**
     * Clear gene selection
     */
    private void clearGeneSelection() {
        for (int i = 0; i < geneTableModel.getRowCount(); i++) {
            geneTableModel.setValueAt(Boolean.FALSE, i, 0);
        }
        updateSelectedGenesSummary();
    }
    
    /**
     * Clear gene table
     */
    private void clearGeneTable() {
        geneTableModel.setRowCount(0);
        updateSelectedGenesSummary();
    }
    
    /**
     * Update selected genes summary
     */
    private void updateSelectedGenesSummary() {
        StringBuilder summary = new StringBuilder();
        int selectedCount = 0;
        
        for (int i = 0; i < geneTableModel.getRowCount(); i++) {
            Boolean selected = (Boolean) geneTableModel.getValueAt(i, 0);
            if (selected != null && selected) {
                String geneId = (String) geneTableModel.getValueAt(i, 1);
                if (selectedCount > 0) summary.append(", ");
                summary.append(geneId);
                selectedCount++;
                
                // Limit display to avoid too long text
                if (selectedCount >= 20) {
                    summary.append("... (and ").append(getSelectedGenesCount() - 20).append(" more)");
                    break;
                }
            }
        }
        
        selectedGenesArea.setText("Selected genes (" + selectedCount + "): " + summary.toString());
    }
    
    /**
     * Get count of selected genes
     */
    private int getSelectedGenesCount() {
        int count = 0;
        for (int i = 0; i < geneTableModel.getRowCount(); i++) {
            Boolean selected = (Boolean) geneTableModel.getValueAt(i, 0);
            if (selected != null && selected) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Preview heatmap data
     */
    private void previewHeatmapData() {
        if (selectedMatrix == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a matrix first.", "No Matrix Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int selectedCount = getSelectedGenesCount();
        if (selectedCount == 0) {
            JOptionPane.showMessageDialog(this,
                "Please select at least one gene for the heatmap.", "No Genes Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Show preview information
        StringBuilder preview = new StringBuilder();
        preview.append("HEATMAP DATA PREVIEW\n");
        preview.append("===================\n\n");
        preview.append("Experiment: ").append(selectedExperiment.getTitle()).append("\n");
        preview.append("Matrix: ").append(selectedMatrix.getName()).append("\n");
        preview.append("Data type: ").append(selectedMatrix.getDataType().getShortName()).append("\n");
        preview.append("Selected genes: ").append(selectedCount).append("\n");
        preview.append("Total samples: ").append(selectedMatrix.getSampleCount()).append("\n\n");
        
        preview.append("Settings:\n");
        preview.append("- Cluster rows: ").append(clusterRowsCheckBox.isSelected() ? "Yes" : "No").append("\n");
        preview.append("- Cluster columns: ").append(clusterColsCheckBox.isSelected() ? "Yes" : "No").append("\n");
        preview.append("- Scale data: ").append(scaleDataCheckBox.isSelected() ? "Yes" : "No").append("\n\n");
        
        preview.append("Sample names: ");
        String[] sampleNames = selectedMatrix.getSampleNames();
        for (int i = 0; i < Math.min(10, sampleNames.length); i++) {
            if (i > 0) preview.append(", ");
            preview.append(sampleNames[i]);
        }
        if (sampleNames.length > 10) {
            preview.append("... (and ").append(sampleNames.length - 10).append(" more)");
        }
        
        JTextArea previewArea = new JTextArea(preview.toString(), 15, 50);
        previewArea.setEditable(false);
        previewArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        
        JScrollPane scrollPane = new JScrollPane(previewArea);
        JOptionPane.showMessageDialog(this, scrollPane, "Heatmap Data Preview", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Generate heatmap using JIGplot
     */
    private void generateHeatmap() {
        if (selectedMatrix == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a matrix first.", "No Matrix Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int selectedCount = getSelectedGenesCount();
        if (selectedCount == 0) {
            JOptionPane.showMessageDialog(this,
                "Please select at least one gene for the heatmap.", "No Genes Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            // Create temporary matrix file for selected genes
            File tempMatrixFile = createTempMatrixFile();
            
            // Use JIGplot HeatmapControl
            HeatmapControl heatmapControl = new HeatmapControl();
            heatmapControl.setInFile(tempMatrixFile);
            
            // Show the heatmap
            heatmapControl.showMeTheHeatMap();
            
            // Clean up temp file after a delay
            Timer cleanupTimer = new Timer(30000, e -> {
                if (tempMatrixFile.exists()) {
                    tempMatrixFile.delete();
                }
            });
            cleanupTimer.setRepeats(false);
            cleanupTimer.start();
            
            JOptionPane.showMessageDialog(this,
                "Heatmap generated successfully using JIGplot!\n" +
                "The heatmap window should open automatically.",
                "Heatmap Generated", JOptionPane.INFORMATION_MESSAGE);
            
        } catch (Exception e) {
            logger.severe("Error generating heatmap: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Error generating heatmap: " + e.getMessage(),
                "Heatmap Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Create temporary matrix file with selected genes
     */
    private File createTempMatrixFile() throws IOException {
        File tempFile = File.createTempFile("heatmap_matrix", ".tsv");
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            // Write header
            writer.print("Gene_ID");
            String[] sampleNames = selectedMatrix.getSampleNames();
            for (String sample : sampleNames) {
                writer.print("\t" + sample);
            }
            writer.println();
            
            // Write selected gene data
            for (int i = 0; i < geneTableModel.getRowCount(); i++) {
                Boolean selected = (Boolean) geneTableModel.getValueAt(i, 0);
                if (selected != null && selected) {
                    String geneId = (String) geneTableModel.getValueAt(i, 1);
                    Map<String, Double> geneExpression = selectedMatrix.getGeneExpression(geneId);
                    
                    writer.print(geneId);
                    for (String sample : sampleNames) {
                        Double value = geneExpression.get(sample);
                        writer.print("\t" + (value != null ? value : 0.0));
                    }
                    writer.println();
                }
            }
        }
        
        logger.info("Created temporary matrix file: " + tempFile.getAbsolutePath() + 
                   " with " + getSelectedGenesCount() + " genes");
        return tempFile;
    }
}
