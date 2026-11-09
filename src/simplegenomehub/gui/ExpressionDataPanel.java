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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Panel for managing expression experiments and data
 * 
 * @author SimpleGenomeHub
 */
public class ExpressionDataPanel extends JDialog {
    private boolean closed;

    @Override public void dispose() { closed = true; super.dispose(); }

    
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
    private JButton dataTypeButton;
    private JButton refreshButton;
    private JPanel tablePanel;
    private ExpressionExperimentLoader.LoadResult loadResult;
    private boolean loading;
    private long loadGeneration;
    
    /**
     * Constructor
     */
    public ExpressionDataPanel(Window parent, SpeciesInfo species) {
        super(parent, "Expression Data Management - " + species.getSpeciesName(), ModalityType.MODELESS);
        this.targetSpecies = species;
        this.experiments = new ArrayList<>();
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        setSize(800, 600);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        loadExpressionExperiments();
    }
    
    /**
     * Load expression experiments from species directory
     */
    private void loadExpressionExperiments() {
        final long generation = ++loadGeneration;
        loading = true;
        updateButtons();
        detailsArea.setText("Loading expression experiments...");
        SwingWorker<ExpressionExperimentLoader.LoadResult, Void> worker =
            new SwingWorker<ExpressionExperimentLoader.LoadResult, Void>() {
                @Override protected ExpressionExperimentLoader.LoadResult doInBackground() {
                    return ExpressionExperimentLoader.load(targetSpecies);
                }
                @Override protected void done() {
                    if (!(!closed) || generation != loadGeneration) return;
                    try { applyLoadedExperiments(get()); }
                    catch (Exception failure) { detailsArea.setText("Unable to load expression experiments: " + failure.getMessage()); }
                    finally { loading = false; updateButtons(); }
                }
            };
        worker.execute();
    }

    private void applyLoadedExperiments(ExpressionExperimentLoader.LoadResult loaded) {
        loadResult = loaded;
        experiments = new ArrayList<>(loaded.getExperiments());
        tableModel.fireTableDataChanged();
        SimpleGenomeHubUi.setTitledBorderTitle(tablePanel, "Expression Experiments (" + experiments.size() + ")");
        refreshDetailsArea();
        updateButtons();
        logger.info(loaded.getSummary());
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
                refreshDetailsArea();
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
        dataTypeButton = new JButton("Set Expression Data Type...");
        dataTypeButton.setEnabled(false);
        dataTypeButton.addActionListener(e -> chooseLegacyDataType());
        deleteButton = new JButton("Delete Experiment");
        exportButton = new JButton("Export Data");
        browseMatrixButton = new JButton("Browse Matrix");
        filterGenesButton = new JButton("Explore Multi. Gene Expression");
        refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> {
            ExpressionExperimentLoader.invalidate(targetSpecies);
            loadExpressionExperiments();
        });

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
        tablePanel = new JPanel(new BorderLayout());
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
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1));
        JPanel matrixButtons = new JPanel(new FlowLayout());
        matrixButtons.add(dataTypeButton);
        matrixButtons.add(browseMatrixButton);
        matrixButtons.add(filterGenesButton);
        JPanel fileButtons = new JPanel(new FlowLayout());
        fileButtons.add(refreshButton);
        fileButtons.add(exportButton);
        fileButtons.add(deleteButton);
        buttonPanel.add(matrixButtons);
        buttonPanel.add(fileButtons);

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
        int selected = experimentsTable.getSelectedRow();
        boolean hasSelection = !loading && selected >= 0 && selected < experiments.size();
        boolean hasMatrix = hasSelection && !experiments.get(selected).getMatrices().isEmpty();
        dataTypeButton.setEnabled(hasSelection && selected < experiments.size()
            && experiments.get(selected).getStoredDataType() == null);
        deleteButton.setEnabled(hasSelection);
        exportButton.setEnabled(hasMatrix);
        browseMatrixButton.setEnabled(hasMatrix);
        filterGenesButton.setEnabled(hasMatrix);
        refreshButton.setEnabled(!loading);
        experimentsTable.setEnabled(!loading);
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
                loading = true;
                updateButtons();
                SwingWorker<ExpressionExperimentLoader.LoadResult, Void> worker =
                    new SwingWorker<ExpressionExperimentLoader.LoadResult, Void>() {
                    @Override protected ExpressionExperimentLoader.LoadResult doInBackground() throws Exception {
                        File expressionDir = targetSpecies.getExpressionDir();
                        String id = experiment.getExperimentId();
                        simplegenomehub.util.fileio.FileSetTransaction.commit(java.util.Collections.emptyMap(),
                            java.util.Arrays.asList(new File(expressionDir, id + ".metadata.json").toPath(),
                                new File(expressionDir, id + ".matrix.tsv").toPath()));
                        targetSpecies.removeExpressionExperiment(id);
                        ExpressionExperimentLoader.invalidate(targetSpecies);
                        return ExpressionExperimentLoader.load(targetSpecies);
                    }
                    @Override protected void done() {
                        try {
                            if (!(!closed)) return;
                            applyLoadedExperiments(get());
                            JOptionPane.showMessageDialog(ExpressionDataPanel.this,
                                "Experiment deleted successfully.", "Delete Complete", JOptionPane.INFORMATION_MESSAGE);
                        } catch (Exception failure) {
                            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                            JOptionPane.showMessageDialog(ExpressionDataPanel.this,
                                "Error deleting experiment: " + cause.getMessage(), "Delete Error", JOptionPane.ERROR_MESSAGE);
                        } finally { loading = false; updateButtons(); }
                    }
                };
                worker.execute();
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

    private void chooseLegacyDataType() {
        int row = experimentsTable.getSelectedRow();
        if (row < 0 || row >= experiments.size()) return;
        final ExpressionExperiment experiment = experiments.get(row);
        if (experiment.getStoredDataType() != null) return;
        Object selected = JOptionPane.showInputDialog(this,
            "This older experiment has no recorded data type.\nChoose the original measurement type; values will not be converted.",
            "Set Expression Data Type", JOptionPane.QUESTION_MESSAGE, null,
            ExpressionMatrix.DataType.values(), null);
        if (!(selected instanceof ExpressionMatrix.DataType)) return;
        loading = true;
        updateButtons();
        SwingWorker<ExpressionExperimentLoader.LoadResult, Void> worker =
            new SwingWorker<ExpressionExperimentLoader.LoadResult, Void>() {
            private volatile boolean dataTypeSaved;

            @Override protected ExpressionExperimentLoader.LoadResult doInBackground() throws Exception {
                experiment.saveExplicitDataType((ExpressionMatrix.DataType) selected);
                dataTypeSaved = true;
                ExpressionExperimentLoader.invalidate(targetSpecies);
                return ExpressionExperimentLoader.load(targetSpecies);
            }
            @Override protected void done() {
                try {
                    if (!(!closed)) return;
                    applyLoadedExperiments(get());
                } catch (Exception failure) {
                    Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                    logger.log(Level.WARNING, dataTypeSaved
                        ? "Expression data type saved, but refreshing experiments failed"
                        : "Unable to save expression data type", failure);
                    if (dataTypeSaved) {
                        JOptionPane.showMessageDialog(ExpressionDataPanel.this,
                            "The data type was saved, but the experiment list could not be refreshed.\n"
                                + "Use Refresh or reopen this window.\n\n" + cause.getMessage(),
                            "Data Type Saved; Refresh Failed", JOptionPane.WARNING_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(ExpressionDataPanel.this, cause.getMessage(),
                            "Unable to Save Data Type", JOptionPane.ERROR_MESSAGE);
                    }
                } finally { loading = false; updateButtons(); }
            }
        };
        worker.execute();
    }

    private void refreshDetailsArea() {
        int selectedRow = experimentsTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < experiments.size()) {
            ExpressionExperiment experiment = experiments.get(selectedRow);
            String details = ExpressionExperimentDisplaySupport.buildExperimentDetailsText(experiment);
            if (loadResult != null && !loadResult.getIssues().isEmpty()) details += "\nLOADING DETAILS\n" + loadResult.getDetails();
            detailsArea.setText(details);
            detailsArea.setCaretPosition(0);
        } else {
            detailsArea.setText(loadResult != null ? loadResult.getDetails() : "Select an experiment to view details.");
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
