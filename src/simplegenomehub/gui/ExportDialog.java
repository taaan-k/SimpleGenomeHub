/*
 * Export Dialog
 */
package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.export.DataExporter;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

/**
 * Dialog for exporting species data in various formats
 * Supports single and multiple species export
 * 
 * @author SimpleGenomeHub
 */
public class ExportDialog extends JDialog {
    
    private List<SpeciesInfo> selectedSpecies;
    
    // Selection components
    private JList<SpeciesInfo> speciesList;
    private DefaultListModel<SpeciesInfo> listModel;
    private JButton selectAllButton;
    private JButton selectNoneButton;
    
    // Export options
    private JRadioButton archiveRadio;
    private JRadioButton fastaOnlyRadio;
    private JRadioButton annotationOnlyRadio;
    private JRadioButton statisticsRadio;
    private JRadioButton reportRadio;
    private ButtonGroup formatGroup;
    
    // Output options
    private JTextField outputDirField;
    private JButton browseButton;
    
    // Control components
    private JButton exportButton;
    private JButton cancelButton;
    
    // Progress components
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JTextArea logArea;
    
    /**
     * Constructor for single species export
     */
    public ExportDialog(Window parent, SpeciesInfo species) {
        this(parent, java.util.Arrays.asList(species));
    }
    
    /**
     * Constructor for multiple species export
     */
    public ExportDialog(Window parent, List<SpeciesInfo> species) {
        super(parent, "Export Species Data", ModalityType.APPLICATION_MODAL);
        
        this.selectedSpecies = new ArrayList<>(species);
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadSpeciesList();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(parent);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Species selection components
        listModel = new DefaultListModel<>();
        speciesList = new JList<>(listModel);
        speciesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        speciesList.setCellRenderer(new SpeciesListCellRenderer());
        
        selectAllButton = new JButton("Select All");
        selectNoneButton = new JButton("Select None");
        
        // Export format options
        archiveRadio = new JRadioButton("Complete Archive (ZIP)", true);
        fastaOnlyRadio = new JRadioButton("Genome Sequences Only (FASTA)");
        annotationOnlyRadio = new JRadioButton("Annotation Files Only");
        statisticsRadio = new JRadioButton("Statistics and Metadata");
        reportRadio = new JRadioButton("Comprehensive Report");
        
        formatGroup = new ButtonGroup();
        formatGroup.add(archiveRadio);
        formatGroup.add(fastaOnlyRadio);
        formatGroup.add(annotationOnlyRadio);
        formatGroup.add(statisticsRadio);
        formatGroup.add(reportRadio);
        
        // Output directory
        outputDirField = new JTextField(40);
        browseButton = new JButton("Browse...");
        
        // Control buttons
        exportButton = new JButton("Export Data");
        cancelButton = new JButton("Cancel");
        
        // Progress components
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("");
        statusLabel = new JLabel("Ready to export");
        
        logArea = new JTextArea(6, 50);
        logArea.setEditable(false);
        logArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        logArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // Set button sizes
        selectAllButton.setPreferredSize(new Dimension(100, 25));
        selectNoneButton.setPreferredSize(new Dimension(100, 25));
        browseButton.setPreferredSize(new Dimension(80, 25));
        exportButton.setPreferredSize(new Dimension(120, 30));
        cancelButton.setPreferredSize(new Dimension(80, 30));
        
        // Set default output directory
        outputDirField.setText(System.getProperty("user.home") + File.separator + "Desktop");
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        // Top: Species selection
        JPanel selectionPanel = createSelectionPanel();
        mainPanel.add(selectionPanel, BorderLayout.NORTH);
        
        // Center: Export options
        JPanel optionsPanel = createOptionsPanel();
        mainPanel.add(optionsPanel, BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Bottom: Progress and controls
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create species selection panel
     */
    private JPanel createSelectionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Species Selection"));
        
        // Species list
        JScrollPane listScrollPane = new JScrollPane(speciesList);
        listScrollPane.setPreferredSize(new Dimension(550, 120));
        panel.add(listScrollPane, BorderLayout.CENTER);
        
        // Selection buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(selectAllButton);
        buttonPanel.add(selectNoneButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Create export options panel
     */
    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Format selection
        JPanel formatPanel = new JPanel();
        formatPanel.setBorder(new TitledBorder("Export Format"));
        formatPanel.setLayout(new BoxLayout(formatPanel, BoxLayout.Y_AXIS));
        
        formatPanel.add(archiveRadio);
        formatPanel.add(fastaOnlyRadio);
        formatPanel.add(annotationOnlyRadio);
        formatPanel.add(statisticsRadio);
        formatPanel.add(reportRadio);
        
        // Add format descriptions
        JTextArea descArea = new JTextArea(
            "• Complete Archive: All files in ZIP format\n" +
            "• Genome Sequences: Only FASTA genome files\n" +
            "• Annotation Files: Only GFF3/GTF annotation files\n" +
            "• Statistics: Numerical data and metadata only\n" +
            "• Report: Comprehensive text-based report");
        descArea.setEditable(false);
        descArea.setOpaque(false);
        descArea.setFont(SimpleGenomeHubStyle.plain(descArea.getFont(), 11f));
        descArea.setBorder(BorderFactory.createEmptyBorder(5, 20, 5, 5));
        formatPanel.add(descArea);
        
        panel.add(formatPanel, BorderLayout.NORTH);
        
        // Output directory
        JPanel outputPanel = new JPanel();
        outputPanel.setBorder(new TitledBorder("Output Directory"));
        outputPanel.setLayout(new BorderLayout());
        
        JPanel dirPanel = new JPanel(new BorderLayout());
        dirPanel.add(new JLabel("Directory: "), BorderLayout.WEST);
        dirPanel.add(outputDirField, BorderLayout.CENTER);
        dirPanel.add(browseButton, BorderLayout.EAST);
        
        outputPanel.add(dirPanel, BorderLayout.NORTH);
        
        panel.add(outputPanel, BorderLayout.CENTER);
        
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
        logPanel.setBorder(new TitledBorder("Export Log"));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(580, 100));
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        panel.add(logPanel, BorderLayout.CENTER);
        
        // Control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(exportButton);
        buttonPanel.add(cancelButton);
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
        
        browseButton.addActionListener(e -> browseForOutputDirectory());
        
        exportButton.addActionListener(e -> performExport());
        
        cancelButton.addActionListener(e -> dispose());
    }
    
    /**
     * Load species into the list
     */
    private void loadSpeciesList() {
        listModel.clear();
        for (SpeciesInfo species : selectedSpecies) {
            listModel.addElement(species);
        }
        
        // Select all by default
        if (!selectedSpecies.isEmpty()) {
            speciesList.setSelectionInterval(0, selectedSpecies.size() - 1);
        }
    }
    
    /**
     * Browse for output directory
     */
    private void browseForOutputDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Output Directory");
        
        String currentPath = outputDirField.getText();
        if (!currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists()) {
                fileChooser.setCurrentDirectory(currentDir);
            }
        }
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedDir = fileChooser.getSelectedFile();
            outputDirField.setText(selectedDir.getAbsolutePath());
        }
    }
    
    /**
     * Perform the export operation
     */
    private void performExport() {
        List<SpeciesInfo> exportList = speciesList.getSelectedValuesList();
        if (exportList.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please select at least one species to export.",
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        String outputPath = outputDirField.getText().trim();
        if (outputPath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please specify an output directory.",
                "No Output Directory", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            int result = JOptionPane.showConfirmDialog(this,
                "Output directory does not exist. Create it?",
                "Create Directory",
                JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                if (!outputDir.mkdirs()) {
                    JOptionPane.showMessageDialog(this,
                        "Failed to create output directory.",
                        "Directory Creation Failed", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                return;
            }
        }
        
        // Determine export format
        DataExporter.ExportFormat format;
        if (archiveRadio.isSelected()) {
            format = DataExporter.ExportFormat.ARCHIVE;
        } else if (fastaOnlyRadio.isSelected()) {
            format = DataExporter.ExportFormat.FASTA_ONLY;
        } else if (annotationOnlyRadio.isSelected()) {
            format = DataExporter.ExportFormat.ANNOTATION_ONLY;
        } else if (statisticsRadio.isSelected()) {
            format = DataExporter.ExportFormat.STATISTICS;
        } else {
            format = DataExporter.ExportFormat.REPORT;
        }
        
        // Disable controls during export
        setControlsEnabled(false);
        clearLog();
        updateStatus("Starting export...");
        progressBar.setValue(0);
        
        // Perform export in background
        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    publish("Export started: " + exportList.size() + " species, format: " + format);
                    
                    boolean success = true;
                    for (int i = 0; i < exportList.size(); i++) {
                        SpeciesInfo species = exportList.get(i);
                        
                        publish("Exporting: " + species.getDisplayName());
                        setProgress((i * 100) / exportList.size());
                        
                        if (!DataExporter.exportSpecies(species, outputDir, format)) {
                            success = false;
                            publish("ERROR: Failed to export " + species.getDisplayName());
                        } else {
                            publish("✓ Exported: " + species.getDisplayName());
                        }
                    }
                    
                    setProgress(100);
                    
                    if (success) {
                        publish("Export completed successfully!");
                    } else {
                        publish("Export completed with some errors.");
                    }
                    
                    return success;
                    
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
            }
            
            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        updateStatus("Export completed successfully");
                        JOptionPane.showMessageDialog(ExportDialog.this,
                            "Export completed successfully!\n" +
                            "Files saved to: " + outputDir.getAbsolutePath(),
                            "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        updateStatus("Export completed with errors");
                    }
                } catch (Exception e) {
                    updateStatus("Export failed: " + e.getMessage());
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
     * Enable/disable controls during export
     */
    private void setControlsEnabled(boolean enabled) {
        speciesList.setEnabled(enabled);
        selectAllButton.setEnabled(enabled);
        selectNoneButton.setEnabled(enabled);
        archiveRadio.setEnabled(enabled);
        fastaOnlyRadio.setEnabled(enabled);
        annotationOnlyRadio.setEnabled(enabled);
        statisticsRadio.setEnabled(enabled);
        reportRadio.setEnabled(enabled);
        outputDirField.setEnabled(enabled);
        browseButton.setEnabled(enabled);
        exportButton.setEnabled(enabled);
    }
    
    /**
     * Update status label
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Clear log area
     */
    private void clearLog() {
        logArea.setText("");
    }
    
    /**
     * Append message to log
     */
    private void appendToLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    /**
     * Custom cell renderer for species list
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
