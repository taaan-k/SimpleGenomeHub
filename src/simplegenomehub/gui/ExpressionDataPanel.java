/*
 * Expression Data Management Panel
 * Displays and manages imported expression experiments
 */
package simplegenomehub.gui;

import simplegenomehub.model.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Panel for managing expression experiments and data
 * 
 * @author SimpleGenomeHub
 */
public class ExpressionDataPanel extends JDialog {
    
    private static final Logger logger = Logger.getLogger(ExpressionDataPanel.class.getName());
    
    private SpeciesInfo targetSpecies;
    private List<ExpressionExperiment> experiments;
    
    // UI Components
    private JTable experimentsTable;
    private ExperimentTableModel tableModel;
    private JTextArea detailsArea;
    private JButton deleteButton;
    private JButton exportButton;
    private JButton browseMatrixButton;
    private JButton filterGenesButton;
    
    /**
     * Constructor
     */
    public ExpressionDataPanel(Window parent, SpeciesInfo species) {
        super(parent, "Expression Data Management - " + species.getSpeciesName(), ModalityType.MODELESS);
        this.targetSpecies = species;
        this.experiments = new ArrayList<>();
        
        loadExpressionExperiments();
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        setSize(800, 600);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }
    
    /**
     * Load expression experiments from species directory
     */
    private void loadExpressionExperiments() {
        experiments.clear();
        File expressionDir = targetSpecies.getExpressionDir();
        
        if (expressionDir != null && expressionDir.exists()) {
            File[] metadataFiles = expressionDir.listFiles((dir, name) -> name.endsWith(".metadata.json"));
            if (metadataFiles != null) {
                for (File metadataFile : metadataFiles) {
                    try {
                        ExpressionExperiment experiment = ExpressionExperiment.loadMetadata(metadataFile);
                        if (experiment != null) {
                            // Load associated matrix data
                            loadExperimentMatrices(experiment, expressionDir);
                            experiments.add(experiment);
                        }
                    } catch (Exception e) {
                        logger.warning("Failed to load experiment from " + metadataFile.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
        
        logger.info("Loaded " + experiments.size() + " expression experiments");
    }
    
    /**
     * Load matrix data for an experiment
     */
    private void loadExperimentMatrices(ExpressionExperiment experiment, File expressionDir) {
        String experimentId = experiment.getExperimentId();
        File matrixFile = new File(expressionDir, experimentId + ".matrix.tsv");
        
        if (matrixFile.exists()) {
            try {
                // Infer data type from filename or metadata
                ExpressionMatrix.DataType dataType = inferDataTypeFromFile(matrixFile, experiment);
                
                // Create ExpressionMatrix and load data
                String matrixId = experimentId + "_matrix";
                ExpressionMatrix matrix = new ExpressionMatrix(matrixId, experiment.getTitle(), dataType);
                
                if (matrix.loadFromFile(matrixFile)) {
                    experiment.addMatrix(matrix);
                    logger.info("Loaded matrix data for experiment: " + experimentId + 
                              " (" + matrix.getGeneCount() + " genes, " + matrix.getSampleCount() + " samples)" +
                              " - Type: " + dataType.getShortName());
                } else {
                    logger.warning("Failed to load matrix data from: " + matrixFile.getName());
                }
                
            } catch (Exception e) {
                logger.warning("Error loading matrix for experiment " + experimentId + ": " + e.getMessage());
            }
        } else {
            logger.warning("Matrix file not found for experiment: " + experimentId);
        }
    }
    
    /**
     * Infer data type from file name or experiment metadata
     */
    private ExpressionMatrix.DataType inferDataTypeFromFile(File matrixFile, ExpressionExperiment experiment) {
        String fileName = matrixFile.getName().toLowerCase();
        String title = experiment.getTitle().toLowerCase();
        String description = experiment.getDescription() != null ? experiment.getDescription().toLowerCase() : "";
        
        // Check filename first
        if (fileName.contains("tpm")) {
            return ExpressionMatrix.DataType.TPM;
        } else if (fileName.contains("fpkm")) {
            return ExpressionMatrix.DataType.FPKM;
        } else if (fileName.contains("count") || fileName.contains("raw")) {
            return ExpressionMatrix.DataType.COUNTS;
        } else if (fileName.contains("rpkm")) {
            return ExpressionMatrix.DataType.RPKM;
        }
        
        // Check experiment title and description
        if (title.contains("tpm") || description.contains("tpm")) {
            return ExpressionMatrix.DataType.TPM;
        } else if (title.contains("fpkm") || description.contains("fpkm")) {
            return ExpressionMatrix.DataType.FPKM;
        } else if (title.contains("count") || description.contains("count") || 
                   title.contains("raw") || description.contains("raw")) {
            return ExpressionMatrix.DataType.COUNTS;
        } else if (title.contains("rpkm") || description.contains("rpkm")) {
            return ExpressionMatrix.DataType.RPKM;
        }
        
        // Try to read metadata file for data type information
        try {
            File metadataFile = new File(matrixFile.getParent(), experiment.getExperimentId() + ".metadata.json");
            if (metadataFile.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(metadataFile.toPath()), StandardCharsets.UTF_8).toLowerCase();
                if (content.contains("\"tpm\"") || content.contains("\"datatype\":\"tpm\"")) {
                    return ExpressionMatrix.DataType.TPM;
                } else if (content.contains("\"fpkm\"") || content.contains("\"datatype\":\"fpkm\"")) {
                    return ExpressionMatrix.DataType.FPKM;
                } else if (content.contains("\"counts\"") || content.contains("\"datatype\":\"counts\"") ||
                          content.contains("\"raw\"") || content.contains("\"datatype\":\"raw\"")) {
                    return ExpressionMatrix.DataType.COUNTS;
                } else if (content.contains("\"rpkm\"") || content.contains("\"datatype\":\"rpkm\"")) {
                    return ExpressionMatrix.DataType.RPKM;
                }
            }
        } catch (Exception e) {
            logger.fine("Could not read metadata file for data type inference: " + e.getMessage());
        }
        
        // Default to TPM if no clear indication
        logger.info("Could not determine data type for " + matrixFile.getName() + ", defaulting to TPM");
        return ExpressionMatrix.DataType.TPM;
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Create table model and table
        tableModel = new ExperimentTableModel();
        experimentsTable = new JTable(tableModel);
        experimentsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        experimentsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetailsArea();
                updateButtons();
            }
        });
        
        // Set up table appearance
        experimentsTable.setRowHeight(25);
        experimentsTable.getColumnModel().getColumn(0).setPreferredWidth(200); // Name
        experimentsTable.getColumnModel().getColumn(1).setPreferredWidth(100); // Type
        experimentsTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Genes
        experimentsTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Samples
        experimentsTable.getColumnModel().getColumn(4).setPreferredWidth(120); // Import Date
        
        // Details area
        detailsArea = new JTextArea(8, 50);
        detailsArea.setEditable(false);
        detailsArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        detailsArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // Action buttons
        deleteButton = new JButton("Delete Experiment");
        exportButton = new JButton("Export Data");
        browseMatrixButton = new JButton("Browse Matrix");
        filterGenesButton = new JButton("Explore Multi. Gene Expression");

        // Initially disable buttons that require selection
        deleteButton.setEnabled(false);
        exportButton.setEnabled(false);
        browseMatrixButton.setEnabled(false);
        filterGenesButton.setEnabled(false);
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // Top: Instructions
        JLabel instructionLabel = new JLabel(
            "<html><b>Expression Data Management</b><br>" + 
            "Select an experiment to view details and perform operations.</html>");
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        add(instructionLabel, BorderLayout.NORTH);
        
        // Center: Split pane with table and details
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        SimpleGenomeHubUi.styleSplitPane(splitPane);
        
        // Top of split: Experiments table
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(new TitledBorder("Expression Experiments (" + experiments.size() + ")"));
        
        JScrollPane tableScrollPane = new JScrollPane(experimentsTable);
        tableScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        
        splitPane.setTopComponent(tablePanel);
        
        // Bottom of split: Details area
        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.setBorder(new TitledBorder("Experiment Details"));
        
        JScrollPane detailsScrollPane = new JScrollPane(detailsArea);
        detailsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        detailsPanel.add(detailsScrollPane, BorderLayout.CENTER);
        
        splitPane.setBottomComponent(detailsPanel);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.6);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Bottom: Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(browseMatrixButton);
        buttonPanel.add(filterGenesButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(exportButton);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(deleteButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        deleteButton.addActionListener(e -> deleteSelectedExperiment());
        exportButton.addActionListener(e -> exportSelectedExperiment());
        browseMatrixButton.addActionListener(e -> browseMatrix());
        filterGenesButton.addActionListener(e -> filterGenes());
    }
    
    /**
     * Update details area with selected experiment info
     */
    private void updateDetailsArea() {
        int selectedRow = experimentsTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < experiments.size()) {
            ExpressionExperiment experiment = experiments.get(selectedRow);
            StringBuilder details = new StringBuilder();
            
            details.append("EXPERIMENT DETAILS\n");
            details.append("==================\n\n");
            details.append("ID: ").append(experiment.getExperimentId()).append("\n");
            details.append("Name: ").append(experiment.getTitle()).append("\n");
            details.append("Type: ").append(experiment.getExperimentType()).append("\n");
            details.append("Description: ").append(experiment.getDescription() != null ? 
                                                  experiment.getDescription() : "No description").append("\n");
            details.append("Import Time: ").append(experiment.getImportTime().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
            
            details.append("DATA MATRICES\n");
            details.append("=============\n");
            List<ExpressionMatrix> matrices = experiment.getMatrices();
            if (matrices.isEmpty()) {
                details.append("No matrices available\n");
            } else {
                for (ExpressionMatrix matrix : matrices) {
                    details.append("• ").append(matrix.getName())
                           .append(" (").append(matrix.getDataType().getShortName()).append(")\n");
                    details.append("  - Genes: ").append(matrix.getGeneCount()).append("\n");
                    details.append("  - Samples: ").append(matrix.getSampleCount()).append("\n");
                    details.append("  - Source: ").append(matrix.getSourceFile() != null ? 
                                                        matrix.getSourceFile().getName() : "Unknown").append("\n");
                    if (matrix.getNotes() != null && !matrix.getNotes().trim().isEmpty()) {
                        details.append("  - Notes: ").append(matrix.getNotes()).append("\n");
                    }
                    details.append("\n");
                }
            }
            
            details.append("SAMPLE INFORMATION\n");
            details.append("==================\n");
            List<String> sampleNames = experiment.getSampleNames();
            if (sampleNames.isEmpty()) {
                details.append("No samples available\n");
            } else {
                details.append("Total samples: ").append(sampleNames.size()).append("\n");
                details.append("Sample names: ");
                if (sampleNames.size() <= 10) {
                    details.append(String.join(", ", sampleNames));
                } else {
                    details.append(String.join(", ", sampleNames.subList(0, 10)))
                           .append("... (and ").append(sampleNames.size() - 10).append(" more)");
                }
                details.append("\n");
            }
            
            detailsArea.setText(details.toString());
            detailsArea.setCaretPosition(0); // Scroll to top
        } else {
            detailsArea.setText("Select an experiment to view details.");
        }
    }
    
    /**
     * Update button states based on selection
     */
    private void updateButtons() {
        boolean hasSelection = experimentsTable.getSelectedRow() >= 0;
        deleteButton.setEnabled(hasSelection);
        exportButton.setEnabled(hasSelection);
        browseMatrixButton.setEnabled(hasSelection);
        filterGenesButton.setEnabled(hasSelection);
    }
    
    /**
     * Delete selected experiment
     */
    private void deleteSelectedExperiment() {
        int selectedRow = experimentsTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < experiments.size()) {
            ExpressionExperiment experiment = experiments.get(selectedRow);
            
            int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete experiment '" + experiment.getTitle() + "'?\n" +
                "This will permanently remove all associated data files.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            
            if (result == JOptionPane.YES_OPTION) {
                try {
                    // Delete experiment files
                    File expressionDir = targetSpecies.getExpressionDir();
                    String experimentId = experiment.getExperimentId();
                    
                    File metadataFile = new File(expressionDir, experimentId + ".metadata.json");
                    File matrixFile = new File(expressionDir, experimentId + ".matrix.tsv");
                    
                    boolean success = true;
                    if (metadataFile.exists()) {
                        success &= metadataFile.delete();
                    }
                    if (matrixFile.exists()) {
                        success &= matrixFile.delete();
                    }
                    
                    if (success) {
                        // Remove from species
                        targetSpecies.removeExpressionExperiment(experiment);

                        // Refresh display
                        loadExpressionExperiments();
                        tableModel.fireTableDataChanged();
                        updateDetailsArea();
                        updateButtons();

                        // Update table border title
                        JPanel tablePanel = (JPanel) ((JSplitPane) getContentPane().getComponent(1)).getTopComponent();
                        ((TitledBorder) tablePanel.getBorder()).setTitle("Expression Experiments (" + experiments.size() + ")");
                        tablePanel.repaint();

                        JOptionPane.showMessageDialog(this,
                            "Experiment deleted successfully.",
                            "Delete Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this,
                            "Failed to delete some experiment files. Please check file permissions.",
                            "Delete Failed", JOptionPane.ERROR_MESSAGE);
                    }
                    
                } catch (Exception e) {
                    logger.warning("Error deleting experiment: " + e.getMessage());
                    JOptionPane.showMessageDialog(this,
                        "Error deleting experiment: " + e.getMessage(),
                        "Delete Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
    
    /**
     * Export selected experiment data
     */
    private void exportSelectedExperiment() {
        int selectedRow = experimentsTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < experiments.size()) {
            ExpressionExperiment experiment = experiments.get(selectedRow);
            
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Expression Data");
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File targetDir = fileChooser.getSelectedFile();
                
                try {
                    // Export matrices
                    List<ExpressionMatrix> matrices = experiment.getMatrices();
                    int exportCount = 0;
                    
                    for (ExpressionMatrix matrix : matrices) {
                        String filename = experiment.getExperimentId() + "_" + 
                                        matrix.getDataType().getShortName().toLowerCase() + ".tsv";
                        File exportFile = new File(targetDir, filename);
                        
                        if (matrix.saveToFile(exportFile)) {
                            exportCount++;
                        }
                    }
                    
                    // Export metadata
                    File metadataExportFile = new File(targetDir, experiment.getExperimentId() + "_metadata.json");
                    experiment.saveMetadata(metadataExportFile);
                    
                    JOptionPane.showMessageDialog(this,
                        "Exported " + exportCount + " data files and metadata to:\n" + targetDir.getAbsolutePath(),
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                    
                } catch (Exception e) {
                    logger.warning("Error exporting experiment: " + e.getMessage());
                    JOptionPane.showMessageDialog(this,
                        "Error exporting experiment: " + e.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }
    
    /**
     * Browse matrix data in table format
     */
    private void browseMatrix() {
        int selectedRow = experimentsTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < experiments.size()) {
            ExpressionExperiment experiment = experiments.get(selectedRow);
            List<ExpressionMatrix> matrices = experiment.getMatrices();
            
            if (matrices.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "No matrix data available for this experiment.",
                    "No Matrix Data", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Use the first matrix for browsing
            ExpressionMatrix matrix = matrices.get(0);
            MatrixBrowserDialog browserDialog = new MatrixBrowserDialog(this, matrix);
            browserDialog.setVisible(true);
        }
    }
    
    /**
     * Filter genes using batch gene ID input
     */
    private void filterGenes() {
        int selectedRow = experimentsTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < experiments.size()) {
            ExpressionExperiment experiment = experiments.get(selectedRow);
            List<ExpressionMatrix> matrices = experiment.getMatrices();
            
            if (matrices.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "No matrix data available for this experiment.",
                    "No Matrix Data", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Use the first matrix for filtering
            ExpressionMatrix matrix = matrices.get(0);
            GeneFilterDialog filterDialog = new GeneFilterDialog(this, targetSpecies, matrix);
            filterDialog.setVisible(true);
        }
    }
    
    /**
     * Table model for experiments
     */
    private class ExperimentTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Experiment Name", "Type", "Genes", "Samples", "Import Date"};
        
        @Override
        public int getRowCount() {
            return experiments.size();
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex >= experiments.size()) return null;
            
            ExpressionExperiment experiment = experiments.get(rowIndex);
            
            switch (columnIndex) {
                case 0: return experiment.getTitle();
                case 1: {
                    // Display matrix data types instead of experiment type
                    List<ExpressionMatrix> matrices = experiment.getMatrices();
                    if (matrices.isEmpty()) {
                        return "N/A";
                    } else if (matrices.size() == 1) {
                        return matrices.get(0).getDataType().getShortName();
                    } else {
                        // Multiple matrices, show all types
                        Set<String> types = new HashSet<>();
                        for (ExpressionMatrix matrix : matrices) {
                            types.add(matrix.getDataType().getShortName());
                        }
                        return String.join(", ", types);
                    }
                }
                case 2: {
                    int totalGenes = experiment.getMatrices().stream()
                        .mapToInt(ExpressionMatrix::getGeneCount)
                        .max().orElse(0);
                    return totalGenes > 0 ? String.valueOf(totalGenes) : "N/A";
                }
                case 3: return String.valueOf(experiment.getSampleNames().size());
                case 4: return experiment.getImportTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
                default: return null;
            }
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }
    }
}
