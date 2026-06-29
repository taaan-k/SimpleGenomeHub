/*
 * Species Edit Dialog
 */
package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;
import simplegenomehub.model.GenomeData;
import simplegenomehub.util.fileio.GenomeStatsCalculator;
import simplegenomehub.util.fileio.TBtoolsSequenceExtractor;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dialog for editing species information and managing species data
 * 
 * @author SimpleGenomeHub
 */
public class SpeciesEditDialog extends JDialog {
    
    private static final Logger logger = Logger.getLogger(SpeciesEditDialog.class.getName());
    
    private SpeciesInfo species;
    private SpeciesManager speciesManager;
    
    // Input components
    private JTextField speciesNameField;
    private JTextField versionField;
    private JTextArea notesArea;
    
    // File information components
    private JLabel genomeFileLabel;
    private JLabel annotationFileLabel;
    private JLabel importTimeLabel;
    private JTextArea statisticsArea;
    
    // Action components
    private JButton updateStatsButton;
    private JButton previewGenomeButton;
    private JButton previewAnnotationButton;
    private JButton openDirectoryButton;
    
    // Control buttons
    private JButton saveButton;
    private JButton cancelButton;
    
    /**
     * Constructor
     */
    public SpeciesEditDialog(Window parent, SpeciesInfo species, SpeciesManager speciesManager) {
        super(parent, "Edit Species - " + species.getDisplayName(), ModalityType.APPLICATION_MODAL);
        
        this.species = species;
        this.speciesManager = speciesManager;
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadSpeciesData();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(600, 500);
        setLocationRelativeTo(parent);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Input components
        speciesNameField = new JTextField(30);
        versionField = new JTextField(15);
        notesArea = new JTextArea(3, 40);
        
        // Configure notes area
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // File information components
        genomeFileLabel = new JLabel();
        annotationFileLabel = new JLabel();
        importTimeLabel = new JLabel();
        statisticsArea = new JTextArea(6, 40);
        
        // Configure statistics area
        statisticsArea.setEditable(false);
        statisticsArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        statisticsArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // Action buttons
        updateStatsButton = new JButton("Update Caches");
        previewGenomeButton = new JButton("Preview Genome");
        previewAnnotationButton = new JButton("Preview Annotation");
        openDirectoryButton = new JButton("Open Directory");
        
        // Control buttons
        saveButton = new JButton("Save Changes");
        cancelButton = new JButton("Cancel");
        
        // Set button sizes
        Dimension buttonSize = new Dimension(130, 25);
        updateStatsButton.setPreferredSize(buttonSize);
        previewGenomeButton.setPreferredSize(buttonSize);
        previewAnnotationButton.setPreferredSize(buttonSize);
        openDirectoryButton.setPreferredSize(buttonSize);
        saveButton.setPreferredSize(new Dimension(120, 30));
        cancelButton.setPreferredSize(new Dimension(80, 30));
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // Create main content panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        // Top: Basic information editing
        JPanel editPanel = createEditPanel();
        mainPanel.add(editPanel, BorderLayout.NORTH);
        
        // Center: File information and statistics
        JPanel infoPanel = createInfoPanel();
        mainPanel.add(infoPanel, BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Bottom: Control buttons
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create basic information editing panel
     */
    private JPanel createEditPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("Species Information"));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        // Species name
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 10);
        panel.add(new JLabel("Species Name:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(speciesNameField, gbc);
        
        // Version
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Version:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(versionField, gbc);
        
        // Import time (read-only)
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Imported:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(importTimeLabel, gbc);
        
        // Notes
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("Notes:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 0.3;
        panel.add(new JScrollPane(notesArea), gbc);
        
        return panel;
    }
    
    /**
     * Create file information and statistics panel
     */
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // File information section
        JPanel filePanel = new JPanel();
        filePanel.setBorder(new TitledBorder("File Information"));
        filePanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        // Genome file
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 10);
        filePanel.add(new JLabel("Genome File:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        filePanel.add(genomeFileLabel, gbc);
        
        // Annotation file
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        filePanel.add(new JLabel("Annotation File:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        filePanel.add(annotationFileLabel, gbc);
        
        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actionPanel.add(previewGenomeButton);
        actionPanel.add(previewAnnotationButton);
        actionPanel.add(openDirectoryButton);
        actionPanel.add(updateStatsButton);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        filePanel.add(actionPanel, gbc);
        
        panel.add(filePanel, BorderLayout.NORTH);
        
        // Statistics section
        JPanel statsPanel = new JPanel(new BorderLayout());
        statsPanel.setBorder(new TitledBorder("Genome Statistics"));
        
        JScrollPane statsScrollPane = new JScrollPane(statisticsArea);
        statsScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        statsPanel.add(statsScrollPane, BorderLayout.CENTER);
        
        panel.add(statsPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create control button panel
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEtchedBorder());
        
        panel.add(saveButton);
        panel.add(cancelButton);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        previewGenomeButton.addActionListener(e -> previewGenomeFile());
        previewAnnotationButton.addActionListener(e -> previewAnnotationFile());
        openDirectoryButton.addActionListener(e -> openSpeciesDirectory());
        updateStatsButton.addActionListener(e -> updateStatistics());
        
        saveButton.addActionListener(e -> saveChanges());
        cancelButton.addActionListener(e -> dispose());
    }
    
    /**
     * Load species data into dialog
     */
    private void loadSpeciesData() {
        speciesNameField.setText(species.getSpeciesName());
        versionField.setText(species.getVersion());
        notesArea.setText(species.getNotes() != null ? species.getNotes() : "");
        
        // Format import time
        if (species.getImportTime() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            importTimeLabel.setText(sdf.format(java.sql.Timestamp.valueOf(species.getImportTime())));
        } else {
            importTimeLabel.setText("Unknown");
        }
        
        // Update file information
        updateFileInfo();
        
        // Update statistics display
        updateStatisticsDisplay();
    }
    
    /**
     * Update file information labels
     */
    private void updateFileInfo() {
        // Find genome file
        if (species.getSequenceDir() != null && species.getSequenceDir().exists()) {
            File[] genomeFiles = species.getSequenceDir().listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".fa") || 
                name.toLowerCase().endsWith(".fasta") ||
                name.toLowerCase().endsWith(".fas"));
            
            if (genomeFiles != null && genomeFiles.length > 0) {
                genomeFileLabel.setText(genomeFiles[0].getName() + " (" + formatFileSize(genomeFiles[0].length()) + ")");
                previewGenomeButton.setEnabled(true);
            } else {
                genomeFileLabel.setText("No genome file found");
                previewGenomeButton.setEnabled(false);
            }
        } else {
            genomeFileLabel.setText("Sequence directory not found");
            previewGenomeButton.setEnabled(false);
        }
        
        // Find annotation file
        if (species.getAnnotationDir() != null && species.getAnnotationDir().exists()) {
            File[] annotationFiles = species.getAnnotationDir().listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".gff") || 
                name.toLowerCase().endsWith(".gff3") ||
                name.toLowerCase().endsWith(".gtf"));
            
            if (annotationFiles != null && annotationFiles.length > 0) {
                annotationFileLabel.setText(annotationFiles[0].getName() + " (" + formatFileSize(annotationFiles[0].length()) + ")");
                previewAnnotationButton.setEnabled(true);
            } else {
                annotationFileLabel.setText("No annotation file found");
                previewAnnotationButton.setEnabled(false);
            }
        } else {
            annotationFileLabel.setText("Annotation directory not found");
            previewAnnotationButton.setEnabled(false);
        }
        
        // Enable/disable directory button
        openDirectoryButton.setEnabled(species.getSpeciesDir() != null && species.getSpeciesDir().exists());
    }
    
    /**
     * Update statistics display
     */
    private void updateStatisticsDisplay() {
        if (species.getGenomeData() != null) {
            String report = GenomeStatsCalculator.generateStatsReport(species.getGenomeData());
            statisticsArea.setText(report);
        } else {
            statisticsArea.setText("No statistics available.\nClick 'Update Caches' to regenerate statistics and sequence caches.");
        }
    }
    
    /**
     * Format file size for display
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " bytes";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
    
    /**
     * Preview genome file
     */
    private void previewGenomeFile() {
        if (species.getSequenceDir() != null) {
            File[] genomeFiles = species.getSequenceDir().listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".fa") || 
                name.toLowerCase().endsWith(".fasta") ||
                name.toLowerCase().endsWith(".fas"));
                
            if (genomeFiles != null && genomeFiles.length > 0) {
                FilePreviewDialog dialog = new FilePreviewDialog(this, genomeFiles[0]);
                dialog.setVisible(true);
            }
        }
    }
    
    /**
     * Preview annotation file
     */
    private void previewAnnotationFile() {
        if (species.getAnnotationDir() != null) {
            File[] annotationFiles = species.getAnnotationDir().listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".gff") || 
                name.toLowerCase().endsWith(".gff3") ||
                name.toLowerCase().endsWith(".gtf"));
                
            if (annotationFiles != null && annotationFiles.length > 0) {
                FilePreviewDialog dialog = new FilePreviewDialog(this, annotationFiles[0]);
                dialog.setVisible(true);
            }
        }
    }
    
    /**
     * Open species directory
     */
    private void openSpeciesDirectory() {
        if (species.getSpeciesDir() != null && species.getSpeciesDir().exists()) {
            try {
                Desktop.getDesktop().open(species.getSpeciesDir());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to open directory: " + e.getMessage(),
                    "Open Directory Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Update caches (statistics and representative sequence files)
     */
    private void updateStatistics() {
        // Find genome and annotation files
        File genomeFile = species.getGenomeFile();
        File annotationFile = species.getAnnotationFile();
        
        if (genomeFile == null || !genomeFile.exists() || 
            annotationFile == null || !annotationFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "Cannot update caches: genome or annotation file not found.",
                "Update Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Check annotation file format
        String formatType = TBtoolsSequenceExtractor.getFileFormatString(annotationFile);
        
        // Create progress dialog
        JDialog progressDialog = new JDialog(this, "Updating Caches", true);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Initializing...");
        JLabel statusLabel = new JLabel("Preparing to update caches...");
        
        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        progressPanel.add(statusLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        
        progressDialog.add(progressPanel);
        progressDialog.setSize(400, 120);
        progressDialog.setLocationRelativeTo(this);
        
        // Update caches in background
        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                publish("Detected annotation format: " + formatType);
                setProgress(10);
                
                // Update statistics
                publish("Calculating genome statistics...");
                setProgress(20);
                GenomeData newStats = GenomeStatsCalculator.calculateGenomeStats(genomeFile, annotationFile);
                species.setGenomeData(newStats);
                
                // Save stats to file
                if (species.getStatsFile() != null) {
                    newStats.saveToFile(species.getStatsFile());
                }
                setProgress(30);
                
                // Remove existing cache files to force regeneration
                String prefix = species.getSpeciesDirectoryName();
                File sequenceDir = species.getSequenceDir();
                
                // Representative sequence files
                File transcriptFile = new File(sequenceDir, prefix + ".transcripts.fasta");
                File cdsFile = new File(sequenceDir, prefix + ".cds.fasta");
                File proteinFile = new File(sequenceDir, prefix + ".proteins.fasta");
                
                // Complete sequence files (all transcripts/CDS/proteins)
                File allTranscriptFile = new File(sequenceDir, prefix + ".all_transcripts.fasta");
                File allCdsFile = new File(sequenceDir, prefix + ".all_cds.fasta");
                File allProteinFile = new File(sequenceDir, prefix + ".all_proteins.fasta");
                
                // Remove existing files
                if (transcriptFile.exists()) transcriptFile.delete();
                if (cdsFile.exists()) cdsFile.delete();
                if (proteinFile.exists()) proteinFile.delete();
                if (allTranscriptFile.exists()) allTranscriptFile.delete();
                if (allCdsFile.exists()) allCdsFile.delete();
                if (allProteinFile.exists()) allProteinFile.delete();
                
                // Extract representative transcripts using TBtools
                publish("Extracting representative transcripts...");
                setProgress(35);
                TBtoolsSequenceExtractor.ExtractionResult transcriptResult = 
                    TBtoolsSequenceExtractor.extractRepresentativeTranscripts(
                        genomeFile, annotationFile, transcriptFile, "LONGEST_CDS");
                
                if (!transcriptResult.isSuccess()) {
                    throw new Exception("Failed to extract transcripts: " + transcriptResult.getMessage());
                }
                
                // Extract ALL transcripts using TBtools
                publish("Extracting all transcripts...");
                setProgress(42);
                TBtoolsSequenceExtractor.ExtractionResult allTranscriptResult = 
                    TBtoolsSequenceExtractor.extractAllTranscripts(
                        genomeFile, annotationFile, allTranscriptFile);
                
                if (!allTranscriptResult.isSuccess()) {
                    throw new Exception("Failed to extract all transcripts: " + allTranscriptResult.getMessage());
                }
                
                // Extract representative CDS sequences using TBtools
                publish("Extracting representative CDS sequences...");
                setProgress(50);
                TBtoolsSequenceExtractor.ExtractionResult cdsResult = 
                    TBtoolsSequenceExtractor.extractRepresentativeCDS(
                        genomeFile, annotationFile, cdsFile, "LONGEST_CDS");
                
                if (!cdsResult.isSuccess()) {
                    throw new Exception("Failed to extract CDS: " + cdsResult.getMessage());
                }
                
                // Extract ALL CDS sequences using TBtools
                publish("Extracting all CDS sequences...");
                setProgress(58);
                TBtoolsSequenceExtractor.ExtractionResult allCdsResult = 
                    TBtoolsSequenceExtractor.extractAllCDS(
                        genomeFile, annotationFile, allCdsFile);
                
                if (!allCdsResult.isSuccess()) {
                    throw new Exception("Failed to extract all CDS: " + allCdsResult.getMessage());
                }
                
                // Extract representative protein sequences using TBtools
                publish("Extracting representative proteins...");
                setProgress(67);
                TBtoolsSequenceExtractor.ExtractionResult proteinResult = 
                    TBtoolsSequenceExtractor.extractRepresentativeProteins(
                        genomeFile, annotationFile, proteinFile, "LONGEST_CDS");
                
                if (!proteinResult.isSuccess()) {
                    throw new Exception("Failed to extract proteins: " + proteinResult.getMessage());
                }
                
                // Extract ALL protein sequences using TBtools
                publish("Extracting all proteins...");
                setProgress(75);
                TBtoolsSequenceExtractor.ExtractionResult allProteinResult = 
                    TBtoolsSequenceExtractor.extractAllProteins(
                        genomeFile, annotationFile, allProteinFile);
                
                if (!allProteinResult.isSuccess()) {
                    throw new Exception("Failed to extract all proteins: " + allProteinResult.getMessage());
                }
                
                publish("Update completed successfully!");
                setProgress(100);
                
                // Log results
                logger.info("Cache update completed for " + species.getDisplayName());
                logger.info("Extracted " + transcriptResult.getSequencesExtracted() + " representative transcripts");
                logger.info("Extracted " + allTranscriptResult.getSequencesExtracted() + " total transcripts");
                logger.info("Extracted " + cdsResult.getSequencesExtracted() + " representative CDS sequences");
                logger.info("Extracted " + allCdsResult.getSequencesExtracted() + " total CDS sequences");
                logger.info("Extracted " + proteinResult.getSequencesExtracted() + " representative proteins");
                logger.info("Extracted " + allProteinResult.getSequencesExtracted() + " total proteins");
                
                return true;
            }
            
            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    statusLabel.setText(message);
                }
            }
            
            @Override
            protected void done() {
                progressDialog.dispose();
                
                try {
                    Boolean success = get();
                    if (success) {
                        updateStatisticsDisplay();
                        
                        JOptionPane.showMessageDialog(SpeciesEditDialog.this,
                            "Caches updated successfully!\n" +
                            "Statistics recalculated and both representative and complete sequence files generated using TBtools.\n\n" +
                            "Files generated:\n" +
                            "- Representative sequences: .transcripts.fasta, .cds.fasta, .proteins.fasta\n" +
                            "- Complete sequences: .all_transcripts.fasta, .all_cds.fasta, .all_proteins.fasta",
                            "Update Complete", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Cache update failed", e);
                    JOptionPane.showMessageDialog(SpeciesEditDialog.this,
                        "Failed to update caches: " + e.getMessage(),
                        "Update Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        // Show progress dialog and start worker
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        
        worker.execute();
        progressDialog.setVisible(true);
    }
    
    /**
     * Save changes
     */
    private void saveChanges() {
        String newName = speciesNameField.getText().trim();
        String newVersion = versionField.getText().trim();
        String newNotes = notesArea.getText().trim();
        
        if (newName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a species name.",
                "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (newVersion.isEmpty()) {
            newVersion = "1.0";
        }
        
        // Check if name/version changed
        boolean nameChanged = !newName.equals(species.getSpeciesName());
        boolean versionChanged = !newVersion.equals(species.getVersion());
        
        if (nameChanged || versionChanged) {
            int result = JOptionPane.showConfirmDialog(this,
                "Changing the species name or version will rename the species entry\n" +
                "and update its underlying directory and metadata.\n" +
                "Do you want to continue?",
                "Confirm Changes",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
                
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        boolean saved = speciesManager.applySpeciesEdits(
            species,
            newName,
            newVersion,
            newNotes
        );

        if (!saved) {
            return;
        }
        
        JOptionPane.showMessageDialog(this,
            "Species information updated successfully.",
            "Save Complete", JOptionPane.INFORMATION_MESSAGE);
            
        dispose();
    }
}
