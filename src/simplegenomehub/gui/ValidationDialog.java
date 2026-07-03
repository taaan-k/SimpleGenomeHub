/*
 * Validation Dialog
 */
package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;
import simplegenomehub.util.validation.DataValidator;
import simplegenomehub.util.validation.DataRepairer;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.ArrayList;

/**
 * Dialog for validating and repairing species data
 * Provides comprehensive data validation and automated repair capabilities
 * 
 * @author SimpleGenomeHub
 */
public class ValidationDialog extends JDialog {
    
    private SpeciesManager speciesManager;
    
    // Data models
    private List<SpeciesInfo> selectedSpecies;
    private List<DataValidator.ValidationResult> validationResults;
    private List<DataRepairer.RepairResult> repairResults;
    private ValidationTableModel tableModel;
    
    // GUI components
    private JList<SpeciesInfo> speciesList;
    private DefaultListModel<SpeciesInfo> listModel;
    private JButton selectAllButton;
    private JButton selectNoneButton;
    
    private JTable resultsTable;
    private JButton validateButton;
    private JButton repairButton;
    private JButton repairAllButton;
    
    // Progress and status
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JTextArea reportArea;
    
    // Control buttons
    private JButton exportReportButton;
    private JButton closeButton;
    
    /**
     * Constructor
     */
    public ValidationDialog(Window parent, SpeciesManager speciesManager) {
        super(parent, "Data Validation & Repair", ModalityType.APPLICATION_MODAL);
        
        this.speciesManager = speciesManager;
        this.selectedSpecies = new ArrayList<>();
        this.validationResults = new ArrayList<>();
        this.repairResults = new ArrayList<>();
        this.tableModel = new ValidationTableModel();
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadSpecies();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(parent);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Species selection
        listModel = new DefaultListModel<>();
        speciesList = new JList<>(listModel);
        speciesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        speciesList.setCellRenderer(new SpeciesListCellRenderer());
        
        selectAllButton = new JButton("Select All");
        selectNoneButton = new JButton("Select None");
        
        // Results table
        resultsTable = new JTable(tableModel);
        resultsTable.setRowHeight(25);
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(200); // Species
        resultsTable.getColumnModel().getColumn(1).setPreferredWidth(80);  // Status
        resultsTable.getColumnModel().getColumn(2).setPreferredWidth(60);  // Errors
        resultsTable.getColumnModel().getColumn(3).setPreferredWidth(80);  // Warnings
        resultsTable.getColumnModel().getColumn(4).setPreferredWidth(300); // Issues
        resultsTable.getColumnModel().getColumn(5).setPreferredWidth(100); // Actions
        
        // Custom cell renderers
        resultsTable.getColumnModel().getColumn(1).setCellRenderer(new StatusCellRenderer());
        resultsTable.getColumnModel().getColumn(5).setCellRenderer(new ActionCellRenderer());
        
        // Action buttons
        validateButton = new JButton("Validate Selected");
        repairButton = new JButton("Repair Selected");
        repairAllButton = new JButton("Repair All Issues");
        
        // Progress and status
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("");
        statusLabel = new JLabel("Select species and click Validate to check data integrity");
        
        // Report area
        reportArea = new JTextArea(8, 80);
        reportArea.setEditable(false);
        reportArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        reportArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // Control buttons
        exportReportButton = new JButton("Export Report");
        closeButton = new JButton("Close");
        
        // Set button sizes
        selectAllButton.setPreferredSize(new Dimension(100, 25));
        selectNoneButton.setPreferredSize(new Dimension(100, 25));
        validateButton.setPreferredSize(new Dimension(130, 30));
        repairButton.setPreferredSize(new Dimension(130, 30));
        repairAllButton.setPreferredSize(new Dimension(130, 30));
        exportReportButton.setPreferredSize(new Dimension(120, 30));
        closeButton.setPreferredSize(new Dimension(80, 30));
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        // Left: Species selection
        JPanel leftPanel = createSpeciesSelectionPanel();
        
        // Right: Results and controls
        JPanel rightPanel = createResultsPanel();
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        SimpleGenomeHubUi.styleSplitPane(splitPane);
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.3);
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Bottom: Progress and controls
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create species selection panel
     */
    private JPanel createSpeciesSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Species Selection"));
        
        // Species list
        JScrollPane listScrollPane = new JScrollPane(speciesList);
        listScrollPane.setPreferredSize(new Dimension(280, 400));
        panel.add(listScrollPane, BorderLayout.CENTER);
        
        // Selection buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(selectAllButton);
        buttonPanel.add(selectNoneButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Create results panel
     */
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionPanel.setBorder(new TitledBorder("Actions"));
        actionPanel.add(validateButton);
        actionPanel.add(repairButton);
        actionPanel.add(repairAllButton);
        
        panel.add(actionPanel, BorderLayout.NORTH);
        
        // Results table
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(new TitledBorder("Validation Results"));
        
        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setPreferredSize(new Dimension(580, 300));
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        
        panel.add(tablePanel, BorderLayout.CENTER);
        
        // Report area
        JPanel reportPanel = new JPanel(new BorderLayout());
        reportPanel.setBorder(new TitledBorder("Detailed Report"));
        
        JScrollPane reportScrollPane = new JScrollPane(reportArea);
        reportScrollPane.setPreferredSize(new Dimension(580, 150));
        reportPanel.add(reportScrollPane, BorderLayout.CENTER);
        
        panel.add(reportPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Create bottom panel
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Status and progress
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        panel.add(statusPanel, BorderLayout.NORTH);
        
        // Control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(exportReportButton);
        buttonPanel.add(closeButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        selectAllButton.addActionListener(e -> {
            speciesList.setSelectionInterval(0, listModel.getSize() - 1);
        });
        
        selectNoneButton.addActionListener(e -> {
            speciesList.clearSelection();
        });
        
        validateButton.addActionListener(e -> performValidation());
        repairButton.addActionListener(e -> performRepair(false));
        repairAllButton.addActionListener(e -> performRepair(true));
        
        exportReportButton.addActionListener(e -> exportReport());
        closeButton.addActionListener(e -> dispose());
    }
    
    /**
     * Load species into list
     */
    private void loadSpecies() {
        listModel.clear();
        List<SpeciesInfo> allSpecies = speciesManager.getAllSpecies();
        
        for (SpeciesInfo species : allSpecies) {
            listModel.addElement(species);
        }
        
        // Select all by default
        if (!allSpecies.isEmpty()) {
            speciesList.setSelectionInterval(0, allSpecies.size() - 1);
        }
    }
    
    /**
     * Perform validation
     */
    private void performValidation() {
        List<SpeciesInfo> selected = speciesList.getSelectedValuesList();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please select at least one species to validate.",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Clear previous results
        validationResults.clear();
        repairResults.clear();
        
        // Disable controls
        setControlsEnabled(false);
        statusLabel.setText("Validating species data...");
        progressBar.setValue(0);
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                
                for (int i = 0; i < selected.size(); i++) {
                    SpeciesInfo species = selected.get(i);
                    
                    publish("Validating: " + species.getDisplayName());
                    setProgress((i * 100) / selected.size());
                    
                    DataValidator.ValidationResult result = DataValidator.validateSpecies(species);
                    validationResults.add(result);
                }
                
                setProgress(100);
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                // Could update status here if needed
            }
            
            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    
                    // Update table
                    selectedSpecies.clear();
                    selectedSpecies.addAll(selected);
                    tableModel.fireTableDataChanged();
                    
                    // Generate report
                    String report = DataValidator.generateValidationReport(validationResults);
                    reportArea.setText(report);
                    
                    // Update status
                    int validCount = (int) validationResults.stream().filter(DataValidator.ValidationResult::isValid).count();
                    statusLabel.setText(String.format("Validation complete: %d of %d species valid", 
                        validCount, validationResults.size()));
                    
                } catch (Exception e) {
                    statusLabel.setText("Validation failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(ValidationDialog.this,
                        "Validation error: " + e.getMessage(),
                        "Validation Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setControlsEnabled(true);
                    progressBar.setValue(0);
                    progressBar.setString("");
                }
            }
        };
        
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        
        worker.execute();
    }
    
    /**
     * Perform repair
     */
    private void performRepair(boolean repairAll) {
        if (validationResults.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please run validation first.",
                "No Validation Results", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Find species that need repair
        List<Integer> repairIndices = new ArrayList<>();
        if (repairAll) {
            for (int i = 0; i < validationResults.size(); i++) {
                if (!validationResults.get(i).isValid()) {
                    repairIndices.add(i);
                }
            }
        } else {
            int[] selectedRows = resultsTable.getSelectedRows();
            for (int row : selectedRows) {
                if (row < validationResults.size() && !validationResults.get(row).isValid()) {
                    repairIndices.add(row);
                }
            }
        }
        
        if (repairIndices.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No species with repairable issues selected.",
                "Nothing to Repair", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Disable controls
        setControlsEnabled(false);
        statusLabel.setText("Repairing species data...");
        progressBar.setValue(0);
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                
                repairResults.clear();
                
                for (int i = 0; i < repairIndices.size(); i++) {
                    int index = repairIndices.get(i);
                    SpeciesInfo species = selectedSpecies.get(index);
                    DataValidator.ValidationResult validation = validationResults.get(index);
                    
                    publish("Repairing: " + species.getDisplayName());
                    setProgress((i * 100) / repairIndices.size());
                    
                    DataRepairer.RepairResult repairResult = DataRepairer.repairSpecies(species, validation);
                    repairResults.add(repairResult);
                    
                    // Re-validate after repair
                    DataValidator.ValidationResult newValidation = DataValidator.validateSpecies(species);
                    validationResults.set(index, newValidation);
                }
                
                setProgress(100);
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                // Could update status here if needed
            }
            
            @Override
            protected void done() {
                try {
                    get(); // Check for exceptions
                    
                    // Update table
                    tableModel.fireTableDataChanged();
                    
                    // Generate repair report
                    String repairReport = DataRepairer.generateRepairReport(repairResults);
                    String validationReport = DataValidator.generateValidationReport(validationResults);
                    
                    reportArea.setText("REPAIR RESULTS:\n" + repairReport + "\n\nUPDATED VALIDATION:\n" + validationReport);
                    
                    // Update status
                    int repairedCount = (int) repairResults.stream().filter(r -> r.getRepairCount() > 0).count();
                    statusLabel.setText(String.format("Repair complete: %d species processed, %d repaired", 
                        repairResults.size(), repairedCount));
                    
                } catch (Exception e) {
                    statusLabel.setText("Repair failed: " + e.getMessage());
                    JOptionPane.showMessageDialog(ValidationDialog.this,
                        "Repair error: " + e.getMessage(),
                        "Repair Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setControlsEnabled(true);
                    progressBar.setValue(0);
                    progressBar.setString("");
                }
            }
        };
        
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        
        worker.execute();
    }
    
    /**
     * Export validation report
     */
    private void exportReport() {
        if (validationResults.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No validation results to export.",
                "No Data", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new java.io.File("validation_report.txt"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(fileChooser.getSelectedFile())) {
                writer.println("SimpleGenomeHub Validation Report");
                writer.println("Generated: " + new java.util.Date());
                writer.println("=" + "=".repeat(50));
                writer.println();
                writer.println(reportArea.getText());
                
                JOptionPane.showMessageDialog(this,
                    "Report exported successfully!",
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                    
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to export report: " + e.getMessage(),
                    "Export Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Enable/disable controls
     */
    private void setControlsEnabled(boolean enabled) {
        speciesList.setEnabled(enabled);
        selectAllButton.setEnabled(enabled);
        selectNoneButton.setEnabled(enabled);
        validateButton.setEnabled(enabled);
        repairButton.setEnabled(enabled);
        repairAllButton.setEnabled(enabled);
        exportReportButton.setEnabled(enabled);
        resultsTable.setEnabled(enabled);
    }
    
    /**
     * Table model for validation results
     */
    private class ValidationTableModel extends AbstractTableModel {
        private final String[] columnNames = {
            "Species", "Status", "Errors", "Warnings", "Issues", "Actions"
        };
        
        @Override
        public int getRowCount() {
            return validationResults.size();
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
            if (rowIndex >= validationResults.size()) return "";
            
            DataValidator.ValidationResult result = validationResults.get(rowIndex);
            
            switch (columnIndex) {
                case 0: return result.getSpecies().getDisplayName();
                case 1: return result.isValid() ? "Valid" : "Invalid";
                case 2: return result.getErrorCount();
                case 3: return result.getWarningCount();
                case 4: 
                    if (result.getIssues().isEmpty()) return "No issues";
                    return result.getIssues().get(0).getMessage() + 
                           (result.getIssues().size() > 1 ? " (+" + (result.getIssues().size() - 1) + " more)" : "");
                case 5: return result.isValid() ? "No action needed" : "Repair available";
                default: return "";
            }
        }
    }
    
    /**
     * Custom cell renderer for status column
     */
    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, 
                boolean isSelected, boolean hasFocus, int row, int column) {
            
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if ("Valid".equals(value)) {
                setBackground(isSelected ? table.getSelectionBackground() : Color.GREEN.brighter());
            } else if ("Invalid".equals(value)) {
                setBackground(isSelected ? table.getSelectionBackground() : Color.RED.brighter());
            } else {
                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            }
            
            return this;
        }
    }
    
    /**
     * Custom cell renderer for action column
     */
    private static class ActionCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, 
                boolean isSelected, boolean hasFocus, int row, int column) {
            
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if ("Repair available".equals(value)) {
                setBackground(isSelected ? table.getSelectionBackground() : Color.YELLOW.brighter());
            } else {
                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            }
            
            return this;
        }
    }
    
    /**
     * Custom list cell renderer for species
     */
    private static class SpeciesListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof SpeciesInfo) {
                SpeciesInfo species = (SpeciesInfo) value;
                setText(species.getDisplayName());
                
                // Set icon based on completeness
                if (species.isComplete()) {
                    setIcon(UIManager.getIcon("FileView.fileIcon"));
                } else {
                    setIcon(UIManager.getIcon("OptionPane.warningIcon"));
                }
            }
            
            return this;
        }
    }
}
