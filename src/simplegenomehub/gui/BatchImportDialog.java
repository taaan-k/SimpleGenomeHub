/*
 * Batch Import Dialog
 */
package simplegenomehub.gui;

import simplegenomehub.model.SpeciesManager;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.GenomeData;
import simplegenomehub.util.fileio.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.dnd.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * Dialog for batch importing multiple species from directories
 * Supports drag-drop of directories and automatic file detection
 * 
 * @author SimpleGenomeHub
 */
public class BatchImportDialog extends JDialog {
    
    private SpeciesManager speciesManager;
    
    // Data model
    private List<ImportEntry> importEntries;
    private ImportTableModel tableModel;
    
    // GUI components
    private JTable importTable;
    private JButton addDirButton;
    private JButton removeButton;
    private JButton scanButton;
    private JButton importButton;
    private JButton cancelButton;
    
    // Progress components
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JTextArea logArea;
    
    // Import entry class
    private static class ImportEntry {
        File directory;
        String speciesName;
        String version;
        File genomeFile;
        File annotationFile;
        String status;
        boolean selected;
        
        ImportEntry(File directory) {
            this.directory = directory;
            this.speciesName = "";
            this.version = "1.0";
            this.status = "Pending";
            this.selected = true;
        }
    }
    
    /**
     * Constructor
     */
    public BatchImportDialog(Window parent, SpeciesManager speciesManager) {
        super(parent, "Batch Import Species", ModalityType.APPLICATION_MODAL);
        
        this.speciesManager = speciesManager;
        this.importEntries = new ArrayList<>();
        this.tableModel = new ImportTableModel();
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setupDragDrop();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(parent);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Table for import entries
        importTable = new JTable(tableModel);
        importTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        importTable.setRowHeight(25);
        
        // Set column widths
        importTable.getColumnModel().getColumn(0).setPreferredWidth(50);   // Select
        importTable.getColumnModel().getColumn(1).setPreferredWidth(200);  // Directory
        importTable.getColumnModel().getColumn(2).setPreferredWidth(150);  // Species Name
        importTable.getColumnModel().getColumn(3).setPreferredWidth(80);   // Version
        importTable.getColumnModel().getColumn(4).setPreferredWidth(120);  // Genome File
        importTable.getColumnModel().getColumn(5).setPreferredWidth(120);  // Annotation File
        importTable.getColumnModel().getColumn(6).setPreferredWidth(100);  // Status
        
        // Custom cell renderers
        importTable.getColumnModel().getColumn(6).setCellRenderer(new StatusCellRenderer());
        
        // Control buttons
        addDirButton = new JButton("Add Directory");
        removeButton = new JButton("Remove Selected");
        scanButton = new JButton("Scan Directories");
        importButton = new JButton("Import All");
        cancelButton = new JButton("Cancel");
        
        // Progress components
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("");
        statusLabel = new JLabel("Ready for batch import");
        
        logArea = new JTextArea(8, 80);
        logArea.setEditable(false);
        logArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        logArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // Set button sizes
        addDirButton.setPreferredSize(new Dimension(120, 25));
        removeButton.setPreferredSize(new Dimension(120, 25));
        scanButton.setPreferredSize(new Dimension(120, 25));
        importButton.setPreferredSize(new Dimension(120, 30));
        cancelButton.setPreferredSize(new Dimension(80, 30));
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        // Top: Instructions and controls
        JPanel topPanel = createTopPanel();
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Center: Import table
        JPanel centerPanel = createCenterPanel();
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Bottom: Progress and controls
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create top panel with instructions and controls
     */
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Instructions
        JTextArea instructions = new JTextArea(
            "Batch Import Instructions:\n" +
            "1. Add directories containing genome data (drag & drop or use 'Add Directory')\n" +
            "2. Click 'Scan Directories' to automatically detect genome and annotation files\n" +
            "3. Review and edit species names and versions in the table\n" +
            "4. Select which species to import and click 'Import All'");
        instructions.setEditable(false);
        instructions.setOpaque(false);
        instructions.setFont(SimpleGenomeHubStyle.plain(instructions.getFont(), 12f));
        instructions.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));
        
        panel.add(instructions, BorderLayout.NORTH);
        
        // Control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(addDirButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(scanButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Create center panel with import table
     */
    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Import Queue"));
        
        JScrollPane scrollPane = new JScrollPane(importTable);
        scrollPane.setPreferredSize(new Dimension(880, 250));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create bottom panel with progress and controls
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Status and progress
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        panel.add(statusPanel, BorderLayout.NORTH);
        
        // Log area
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(new TitledBorder("Import Log"));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(880, 120));
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        panel.add(logPanel, BorderLayout.CENTER);
        
        // Control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(importButton);
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        addDirButton.addActionListener(e -> addDirectory());
        removeButton.addActionListener(e -> removeSelectedEntries());
        scanButton.addActionListener(e -> scanDirectories());
        importButton.addActionListener(e -> performBatchImport());
        cancelButton.addActionListener(e -> dispose());
    }
    
    /**
     * Setup drag and drop functionality
     */
    private void setupDragDrop() {
        new DropTarget(importTable, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }
            
            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY);
            }
            
            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {}
            
            @Override
            public void dragExit(DropTargetEvent dte) {}
            
            @Override
            public void drop(DropTargetDropEvent dtde) {
                handleDrop(dtde);
            }
        });
    }
    
    /**
     * Handle drag and drop
     */
    private void handleDrop(DropTargetDropEvent dtde) {
        try {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            Transferable transferable = dtde.getTransferable();
            
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                
                for (File file : droppedFiles) {
                    if (file.isDirectory()) {
                        addDirectoryEntry(file);
                    }
                }
                
                tableModel.fireTableDataChanged();
                updateStatus("Added " + droppedFiles.size() + " directories");
            }
            
            dtde.dropComplete(true);
            
        } catch (Exception e) {
            updateStatus("Drop failed: " + e.getMessage());
            dtde.dropComplete(false);
        }
    }
    
    /**
     * Add directory using file chooser
     */
    private void addDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Directory Containing Genome Data");
        fileChooser.setMultiSelectionEnabled(true);
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] selectedDirs = fileChooser.getSelectedFiles();
            for (File dir : selectedDirs) {
                addDirectoryEntry(dir);
            }
            tableModel.fireTableDataChanged();
            updateStatus("Added " + selectedDirs.length + " directories");
        }
    }
    
    /**
     * Add directory entry to the list
     */
    private void addDirectoryEntry(File directory) {
        // Check if directory already exists
        for (ImportEntry entry : importEntries) {
            if (entry.directory.equals(directory)) {
                return; // Already exists
            }
        }
        
        ImportEntry entry = new ImportEntry(directory);
        // Try to guess species name from directory name
        String dirName = directory.getName();
        if (dirName.contains(".")) {
            String[] parts = dirName.split("\\.", 2);
            entry.speciesName = parts[0];
            entry.version = parts[1];
        } else {
            entry.speciesName = dirName;
        }
        
        importEntries.add(entry);
    }
    
    /**
     * Remove selected entries
     */
    private void removeSelectedEntries() {
        int[] selectedRows = importTable.getSelectedRows();
        if (selectedRows.length == 0) return;
        
        // Remove in reverse order to maintain indices
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            importEntries.remove(selectedRows[i]);
        }
        
        tableModel.fireTableDataChanged();
        updateStatus("Removed " + selectedRows.length + " entries");
    }
    
    /**
     * Scan directories for genome files
     */
    private void scanDirectories() {
        if (importEntries.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No directories added. Please add directories first.",
                "No Directories", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        updateStatus("Scanning directories...");
        
        SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (ImportEntry entry : importEntries) {
                    publish("Scanning: " + entry.directory.getName());
                    scanDirectoryEntry(entry);
                }
                return null;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    appendToLog(message);
                }
            }
            
            @Override
            protected void done() {
                tableModel.fireTableDataChanged();
                updateStatus("Directory scan completed");
            }
        };
        
        worker.execute();
    }
    
    /**
     * Scan single directory entry
     */
    private void scanDirectoryEntry(ImportEntry entry) {
        // Look for genome files
        File[] genomeFiles = entry.directory.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".fa") || 
            name.toLowerCase().endsWith(".fasta") ||
            name.toLowerCase().endsWith(".fas"));
        
        if (genomeFiles != null && genomeFiles.length > 0) {
            entry.genomeFile = genomeFiles[0]; // Take first one
        }
        
        // Look for annotation files
        File[] annotationFiles = entry.directory.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".gff") || 
            name.toLowerCase().endsWith(".gff3") ||
            name.toLowerCase().endsWith(".gtf"));
        
        if (annotationFiles != null && annotationFiles.length > 0) {
            entry.annotationFile = annotationFiles[0]; // Take first one
        }
        
        // Update status
        if (entry.genomeFile != null && entry.annotationFile != null) {
            entry.status = "Ready";
        } else if (entry.genomeFile != null || entry.annotationFile != null) {
            entry.status = "Partial";
        } else {
            entry.status = "No files";
        }
    }
    
    /**
     * Perform batch import
     */
    private void performBatchImport() {
        List<ImportEntry> selectedEntries = new ArrayList<>();
        for (ImportEntry entry : importEntries) {
            if (entry.selected && "Ready".equals(entry.status)) {
                selectedEntries.add(entry);
            }
        }
        
        if (selectedEntries.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No valid entries selected for import.",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Disable controls
        setControlsEnabled(false);
        clearLog();
        updateStatus("Starting batch import...");
        progressBar.setValue(0);
        
        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    boolean allSuccess = true;
                    
                    for (int i = 0; i < selectedEntries.size(); i++) {
                        ImportEntry entry = selectedEntries.get(i);
                        
                        publish("Importing: " + entry.speciesName);
                        setProgress((i * 100) / selectedEntries.size());
                        
                        if (importSingleEntry(entry)) {
                            entry.status = "Imported";
                            publish("✓ Imported: " + entry.speciesName);
                        } else {
                            entry.status = "Failed";
                            publish("✗ Failed: " + entry.speciesName);
                            allSuccess = false;
                        }
                    }
                    
                    setProgress(100);
                    return allSuccess;
                    
                } catch (Exception e) {
                    publish("ERROR: " + e.getMessage());
                    return false;
                }
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    appendToLog(message);
                }
                tableModel.fireTableDataChanged();
            }
            
            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        updateStatus("Batch import completed successfully");
                        JOptionPane.showMessageDialog(BatchImportDialog.this,
                            "Batch import completed successfully!",
                            "Import Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        updateStatus("Batch import completed with errors");
                    }
                } catch (Exception e) {
                    updateStatus("Batch import failed: " + e.getMessage());
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
     * Import single entry
     */
    private boolean importSingleEntry(ImportEntry entry) {
        try {
            // Create species info
            SpeciesInfo species = new SpeciesInfo(entry.speciesName, entry.version);
            
            // Add to species manager
            if (!speciesManager.addSpecies(species)) {
                return false;
            }
            
            // Copy genome file
            if (entry.genomeFile != null) {
                File targetGenome = new File(species.getSequenceDir(), 
                    "genome." + getFileExtension(entry.genomeFile));
                java.nio.file.Files.copy(entry.genomeFile.toPath(), targetGenome.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Copy annotation file
            if (entry.annotationFile != null) {
                File targetAnnotation = new File(species.getAnnotationDir(), 
                    "annotation." + getFileExtension(entry.annotationFile));
                java.nio.file.Files.copy(entry.annotationFile.toPath(), targetAnnotation.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Generate FASTA index
            if (entry.genomeFile != null) {
                File targetGenome = new File(species.getSequenceDir(), 
                    "genome." + getFileExtension(entry.genomeFile));
                FastaIndexBuilder.buildIndex(targetGenome);
            }
            
            // Calculate statistics
            if (entry.genomeFile != null && entry.annotationFile != null) {
                File targetGenome = new File(species.getSequenceDir(), 
                    "genome." + getFileExtension(entry.genomeFile));
                File targetAnnotation = new File(species.getAnnotationDir(), 
                    "annotation." + getFileExtension(entry.annotationFile));
                
                GenomeData stats = GenomeStatsCalculator.calculateGenomeStats(targetGenome, targetAnnotation);
                species.setGenomeData(stats);
                stats.saveToFile(species.getStatsFile());
            }
            
            try {
                DemoGeneSetGenerator.generateDemoGeneSet(species);
            } catch (Exception ignored) {
                // Demo Gene Set generation is best-effort during batch import.
            }

            // Update species in manager
            speciesManager.updateSpecies(species);
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get file extension
     */
    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : "txt";
    }
    
    /**
     * Enable/disable controls
     */
    private void setControlsEnabled(boolean enabled) {
        importTable.setEnabled(enabled);
        addDirButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);
        scanButton.setEnabled(enabled);
        importButton.setEnabled(enabled);
    }
    
    /**
     * Update status
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Clear log
     */
    private void clearLog() {
        logArea.setText("");
    }
    
    /**
     * Append to log
     */
    private void appendToLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    /**
     * Table model for import entries
     */
    private class ImportTableModel extends AbstractTableModel {
        private final String[] columnNames = {
            "Select", "Directory", "Species Name", "Version", "Genome File", "Annotation File", "Status"
        };
        
        @Override
        public int getRowCount() {
            return importEntries.size();
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
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Boolean.class;
            return String.class;
        }
        
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0 || columnIndex == 2 || columnIndex == 3; // Select, Species Name, Version
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ImportEntry entry = importEntries.get(rowIndex);
            switch (columnIndex) {
                case 0: return entry.selected;
                case 1: return entry.directory.getName();
                case 2: return entry.speciesName;
                case 3: return entry.version;
                case 4: return entry.genomeFile != null ? entry.genomeFile.getName() : "";
                case 5: return entry.annotationFile != null ? entry.annotationFile.getName() : "";
                case 6: return entry.status;
                default: return "";
            }
        }
        
        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            ImportEntry entry = importEntries.get(rowIndex);
            switch (columnIndex) {
                case 0: entry.selected = (Boolean) value; break;
                case 2: entry.speciesName = (String) value; break;
                case 3: entry.version = (String) value; break;
            }
            fireTableCellUpdated(rowIndex, columnIndex);
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
            
            String status = (String) value;
            if ("Ready".equals(status)) {
                setBackground(isSelected ? table.getSelectionBackground() : Color.GREEN.brighter());
            } else if ("Partial".equals(status)) {
                setBackground(isSelected ? table.getSelectionBackground() : Color.YELLOW.brighter());
            } else if ("No files".equals(status)) {
                setBackground(isSelected ? table.getSelectionBackground() : Color.RED.brighter());
            } else if ("Imported".equals(status)) {
                setBackground(isSelected ? table.getSelectionBackground() : Color.CYAN.brighter());
            } else if ("Failed".equals(status)) {
                setBackground(isSelected ? table.getSelectionBackground() : Color.MAGENTA.brighter());
            } else {
                setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            }
            
            return this;
        }
    }
}
