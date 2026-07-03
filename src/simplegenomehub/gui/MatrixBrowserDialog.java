/*
 * Matrix Browser Dialog
 * Browse expression matrix data in table format
 */
package simplegenomehub.gui;

import simplegenomehub.model.ExpressionMatrix;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Dialog for browsing expression matrix data
 * 
 * @author SimpleGenomeHub
 */
public class MatrixBrowserDialog extends JDialog {
    
    private static final Logger logger = Logger.getLogger(MatrixBrowserDialog.class.getName());
    
    private ExpressionMatrix matrix;
    private JTable dataTable;
    private MatrixTableModel tableModel;
    private TableRowSorter<MatrixTableModel> sorter;
    private JTextField searchField;
    private JLabel statusLabel;
    private JButton exportVisibleButton;
    private JButton exportAllButton;
    
    /**
     * Constructor
     */
    public MatrixBrowserDialog(Window parent, ExpressionMatrix matrix) {
        super(parent, "Matrix Browser - " + matrix.getName(), ModalityType.MODELESS);
        this.matrix = matrix;
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        setSize(1000, 700);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        updateStatusLabel();
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Create table model and table
        tableModel = new MatrixTableModel();
        dataTable = new JTable(tableModel);

        // Setup row sorter for filtering/sorting
        sorter = new TableRowSorter<>(tableModel);
        dataTable.setRowSorter(sorter);

        // Enable cell selection with multiple selection support
        dataTable.setCellSelectionEnabled(true);
        dataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Table appearance
        dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        dataTable.setRowHeight(22);
        dataTable.getColumnModel().getColumn(0).setPreferredWidth(120); // Gene ID column

        // Set sample column widths
        String[] sampleNames = matrix.getSampleNames();
        for (int i = 1; i < dataTable.getColumnCount() && i <= sampleNames.length; i++) {
            dataTable.getColumnModel().getColumn(i).setPreferredWidth(100);
        }

        // Search field
        searchField = new JTextField(20);
        searchField.setToolTipText("Enter gene ID to search (supports partial matching)");

        // Status label
        statusLabel = new JLabel();

        // Action buttons
        exportVisibleButton = new JButton("Export Visible");
        exportAllButton = new JButton("Export All");
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout(5, 5));
        
        // Top panel with search and info
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        // Search panel
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("Search Gene:"));
        searchPanel.add(searchField);
        searchPanel.add(Box.createHorizontalStrut(20));
        searchPanel.add(statusLabel);
        
        topPanel.add(searchPanel, BorderLayout.NORTH);
        
        // Matrix info
        JLabel matrixInfoLabel = new JLabel(String.format(
            "<html><b>Matrix:</b> %s &nbsp;&nbsp; <b>Data Type:</b> %s &nbsp;&nbsp; <b>Genes:</b> %d &nbsp;&nbsp; <b>Samples:</b> %d</html>",
            matrix.getName(), matrix.getDataType().getShortName(), 
            matrix.getGeneCount(), matrix.getSampleCount()));
        topPanel.add(matrixInfoLabel, BorderLayout.SOUTH);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Center: Data table
        JScrollPane scrollPane = new JScrollPane(dataTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);
        
        // Bottom: Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(exportVisibleButton);
        buttonPanel.add(exportAllButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        // Search functionality
        searchField.addActionListener(e -> performSearch());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
        });
        
        // Export buttons
        exportVisibleButton.addActionListener(e -> exportData(true));
        exportAllButton.addActionListener(e -> exportData(false));
        
        // Update status when row filter changes
        sorter.addRowSorterListener(e -> updateStatusLabel());
    }
    
    /**
     * Perform gene search/filter
     */
    private void performSearch() {
        String searchText = searchField.getText().trim();
        if (searchText.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            // Create case-insensitive filter for gene ID column (column 0)
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText, 0));
        }
        updateStatusLabel();
    }
    
    /**
     * Update status label
     */
    private void updateStatusLabel() {
        int totalRows = tableModel.getRowCount();
        int visibleRows = dataTable.getRowCount();
        
        if (visibleRows == totalRows) {
            statusLabel.setText(String.format("Showing %d genes", totalRows));
        } else {
            statusLabel.setText(String.format("Showing %d of %d genes", visibleRows, totalRows));
        }
    }
    
    /**
     * Export data to file
     */
    private void exportData(boolean visibleOnly) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Tab-separated values (*.tsv)", "tsv"));
        fileChooser.setSelectedFile(new File(matrix.getName().replaceAll("\\s+", "_") + 
                                           (visibleOnly ? "_filtered" : "") + ".tsv"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".tsv")) {
                file = new File(file.getAbsolutePath() + ".tsv");
            }
            
            try {
                exportToFile(file, visibleOnly);
                JOptionPane.showMessageDialog(this,
                    "Data exported successfully to:\n" + file.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                logger.warning("Export error: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Export error: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Export table data to file
     */
    private void exportToFile(File file, boolean visibleOnly) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Write header
            writer.print("Gene_ID");
            String[] sampleNames = matrix.getSampleNames();
            for (String sample : sampleNames) {
                writer.print("\t" + sample);
            }
            writer.println();
            
            // Write data rows
            if (visibleOnly) {
                // Export only visible (filtered) rows
                for (int viewRow = 0; viewRow < dataTable.getRowCount(); viewRow++) {
                    int modelRow = dataTable.convertRowIndexToModel(viewRow);
                    writeDataRow(writer, modelRow, sampleNames);
                }
            } else {
                // Export all rows
                for (int modelRow = 0; modelRow < tableModel.getRowCount(); modelRow++) {
                    writeDataRow(writer, modelRow, sampleNames);
                }
            }
        }
        
        logger.info("Exported matrix data to: " + file.getAbsolutePath() + 
                   (visibleOnly ? " (visible rows only)" : " (all rows)"));
    }
    
    /**
     * Write a single data row
     */
    private void writeDataRow(PrintWriter writer, int modelRow, String[] sampleNames) {
        String geneId = (String) tableModel.getValueAt(modelRow, 0);
        writer.print(geneId);
        
        Map<String, Double> geneExpression = matrix.getGeneExpression(geneId);
        for (String sample : sampleNames) {
            Double value = geneExpression.get(sample);
            writer.print("\t" + (value != null ? value : 0.0));
        }
        writer.println();
    }
    
    /**
     * Table model for expression matrix data
     */
    private class MatrixTableModel extends AbstractTableModel {
        private String[] geneIds;
        private String[] sampleNames;
        
        public MatrixTableModel() {
            this.geneIds = matrix.getGeneIds();
            this.sampleNames = matrix.getSampleNames();
        }
        
        @Override
        public int getRowCount() {
            return geneIds != null ? geneIds.length : 0;
        }
        
        @Override
        public int getColumnCount() {
            return 1 + (sampleNames != null ? sampleNames.length : 0);
        }
        
        @Override
        public String getColumnName(int column) {
            if (column == 0) {
                return "Gene_ID";
            } else if (column <= sampleNames.length) {
                return sampleNames[column - 1];
            }
            return "Unknown";
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex >= geneIds.length) return null;
            
            if (columnIndex == 0) {
                return geneIds[rowIndex];
            } else if (columnIndex <= sampleNames.length) {
                String geneId = geneIds[rowIndex];
                String sampleName = sampleNames[columnIndex - 1];
                Map<String, Double> geneExpression = matrix.getGeneExpression(geneId);
                Double value = geneExpression.get(sampleName);
                return value != null ? String.format("%.3f", value) : "0.000";
            }
            return null;
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }
        
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false; // Read-only table
        }
    }
}