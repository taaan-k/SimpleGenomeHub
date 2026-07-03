/*
 * Species Import Dialog with Drag-Drop Support
 */
package simplegenomehub.gui;

import simplegenomehub.model.*;
import simplegenomehub.util.fileio.*;
import simplegenomehub.model.SpeciesMetadata;
import simplegenomehub.util.fileio.ChromosomeIdChecker;
import simplegenomehub.util.fileio.TBtoolsGenomeValidator;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Dialog for importing new species with drag-drop file support
 * Handles validation, processing, and import workflow
 * 
 * @author SimpleGenomeHub
 */
public class SpeciesImportDialog extends JDialog {
    
    private static final Logger logger = Logger.getLogger(SpeciesImportDialog.class.getName());
    
    private SpeciesManager speciesManager;
    
    // Input components
    private JTextField speciesNameField;
    private JTextField versionField;
    private JTextField genomeFileField;
    private JTextField annotationFileField;
    private JTextArea notesArea;
    
    // File selection components
    private JButton browseGenomeButton;
    private JButton browseAnnotationButton;
    
    // Control components
    private JButton validateButton;
    private JButton importButton;
    private JButton cancelButton;
    
    // Status and progress components
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JTextArea logArea;
    
    // Prepared annotation state from validation
    private byte[] validatedAnnotationBytes;
    private String validatedAnnotationFileName;
    private String validatedImportKey;
    
    // Removed drag-drop panels - using direct file selection only
    
    /**
     * Constructor
     */
    public SpeciesImportDialog(Window parent, SpeciesManager speciesManager) {
        super(parent, "Import New Species", ModalityType.APPLICATION_MODAL);
        
        this.speciesManager = speciesManager;
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setupDragDrop(); // Setup drag-drop on text fields
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack(); // Auto-size the dialog to fit content
        setLocationRelativeTo(parent);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Input fields
        speciesNameField = new JTextField(30);
        versionField = new JTextField(15);
        genomeFileField = new JTextField(40);
        annotationFileField = new JTextField(40);
        notesArea = new JTextArea(3, 40);
        
        // Configure notes area
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // File selection buttons
        browseGenomeButton = new JButton("Browse...");
        browseAnnotationButton = new JButton("Browse...");
        
        // Control buttons
        validateButton = new JButton("Validate Files");
        importButton = new JButton("Import Species");
        cancelButton = new JButton("Cancel");
        
        // Status and progress
        statusLabel = new JLabel("Ready to import species");
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("");
        
        // Log area
        logArea = new JTextArea(8, 60);
        logArea.setEditable(false);
        logArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        logArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // Removed drag-drop panel initialization
        
        // Set button sizes
        browseGenomeButton.setPreferredSize(new Dimension(80, 25));
        browseAnnotationButton.setPreferredSize(new Dimension(80, 25));
        validateButton.setPreferredSize(new Dimension(100, 30));
        importButton.setPreferredSize(new Dimension(120, 30));
        cancelButton.setPreferredSize(new Dimension(80, 30));
        
        // Initially disable import button
        importButton.setEnabled(false);
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // Main content panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        // Top: File selection
        JPanel filePanel = createFilePanel();
        mainPanel.add(filePanel, BorderLayout.NORTH);
        
        // Center: Species information
        JPanel inputPanel = createInputPanel();
        mainPanel.add(inputPanel, BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Bottom: Control and status
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create input fields panel
     */
    private JPanel createInputPanel() {
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
        
        // Notes
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("Notes:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 0.3;
        panel.add(new JScrollPane(notesArea), gbc);
        
        return panel;
    }
    
    /**
     * Create file selection panel with drag-drop
     */
    private JPanel createFilePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("File Selection"));
        
        // File input section
        JPanel fileInputPanel = new JPanel();
        fileInputPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        // Genome file
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 10);
        fileInputPanel.add(new JLabel("Genome File:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        fileInputPanel.add(genomeFileField, gbc);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.insets = new Insets(5, 5, 5, 5);
        fileInputPanel.add(browseGenomeButton, gbc);
        
        // Annotation file
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 10);
        fileInputPanel.add(new JLabel("Annotation File:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        fileInputPanel.add(annotationFileField, gbc);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.insets = new Insets(5, 5, 5, 5);
        fileInputPanel.add(browseAnnotationButton, gbc);
        
        panel.add(fileInputPanel, BorderLayout.CENTER); // Changed from NORTH to CENTER
        
        return panel;
    }
    
    /**
     * Create bottom panel with controls and status
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Progress and status
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        
        panel.add(statusPanel, BorderLayout.NORTH);
        
        // Log area
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(new TitledBorder("Import Log"));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(600, 100)); // Reduced size for better fit
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        
        panel.add(logPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(validateButton);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(importButton);
        buttonPanel.add(cancelButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        browseGenomeButton.addActionListener(e -> browseForFile(genomeFileField, "Select Genome File", "FASTA Files", "fa", "fasta", "fas", "fa.gz", "fasta.gz", "fas.gz"));
        browseAnnotationButton.addActionListener(e -> browseForFile(annotationFileField, "Select Annotation File", "Annotation Files", "gff", "gff3", "gtf", "gff.gz", "gff3.gz", "gtf.gz"));
        
        validateButton.addActionListener(e -> validateFilesWithFix());
        importButton.addActionListener(e -> importSpeciesWithPreparedAnnotation());
        cancelButton.addActionListener(e -> dispose());
    }
    
    /**
     * Setup drag and drop functionality on text fields
     */
    private void setupDragDrop() {
        // Enable drag-drop on genome file text field
        genomeFileField.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent dtde) {
                handleFileFieldDrop(dtde, genomeFileField, "genome");
            }
        });
        
        // Enable drag-drop on annotation file text field
        annotationFileField.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent dtde) {
                handleFileFieldDrop(dtde, annotationFileField, "annotation");
            }
        });
    }
    
    /**
     * Handle file drop events on text fields
     */
    private void handleFileFieldDrop(DropTargetDropEvent dtde, JTextField targetField, String fileType) {
        try {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            Transferable transferable = dtde.getTransferable();
            
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                
                if (!droppedFiles.isEmpty()) {
                    File file = droppedFiles.get(0);
                    
                    // Basic file type validation
                    String fileName = file.getName().toLowerCase();
                    boolean validFile = false;
                    
                    if ("genome".equals(fileType)) {
                        validFile = fileName.endsWith(".fa") || fileName.endsWith(".fasta") || fileName.endsWith(".fas");
                        if (!validFile) {
                            updateStatus("Warning: Expected FASTA file for genome, got: " + file.getName());
                        }
                    } else if ("annotation".equals(fileType)) {
                        validFile = fileName.endsWith(".gff") || fileName.endsWith(".gff3") || fileName.endsWith(".gtf");
                        if (!validFile) {
                            updateStatus("Warning: Expected GFF3/GTF file for annotation, got: " + file.getName());
                        }
                    }
                    
                    targetField.setText(file.getAbsolutePath());
                    updateStatus("File dropped: " + file.getName() + (validFile ? "" : " (check file type)"));
                    
                    // Auto-populate species name if not set
                    if (speciesNameField.getText().trim().isEmpty()) {
                        String baseName = file.getName().replaceAll("\\.[^.]*$", ""); // Remove extension
                        // Remove common suffixes
                        baseName = baseName.replaceAll("\\.(genome|annotation)$", "");
                        speciesNameField.setText(baseName);
                    }
                }
            }
            
            dtde.dropComplete(true);
            
        } catch (Exception e) {
            logger.warning("Error handling file drop: " + e.getMessage());
            updateStatus("Error handling file drop: " + e.getMessage());
            dtde.dropComplete(false);
        }
    }
    
    /**
     * Browse for file dialog
     */
    private void browseForFile(JTextField targetField, String title, String description, String... extensions) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(title);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        // Set file filter
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                for (String ext : extensions) {
                    if (name.endsWith("." + ext)) return true;
                }
                return false;
            }
            
            @Override
            public String getDescription() {
                return description + " (*." + String.join(", *.", extensions) + ")";
            }
        });
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            targetField.setText(selectedFile.getAbsolutePath());
            
            // Auto-populate species name if not set
            if (speciesNameField.getText().trim().isEmpty()) {
                String fileName = selectedFile.getName();
                String baseName = fileName.replaceAll("\\.[^.]*$", "");
                speciesNameField.setText(baseName);
            }
        }
    }
    
    /**
     * Validate selected files
     */
    private void validateFilesLegacy() {
        String speciesName = speciesNameField.getText().trim();
        if (speciesName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a species name before validation.",
                "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String version = versionField.getText().trim();
        if (version.isEmpty()) {
            version = "1.0";
            versionField.setText(version);
        }

        final String validationSpeciesName = speciesName;
        final String validationVersion = version;

        clearLog();
        clearPreparedAnnotationState();
        importButton.setEnabled(false);
        updateStatus("Validating files...");
        progressBar.setValue(0);
        
        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                publish("Starting file validation...");
                
                String genomeFile = genomeFileField.getText().trim();
                String annotationFile = annotationFileField.getText().trim();
                
                if (genomeFile.isEmpty() || annotationFile.isEmpty()) {
                    publish("ERROR: Please select both genome and annotation files");
                    return false;
                }
                
                File genomeFileObj = new File(genomeFile);
                File annotationFileObj = new File(annotationFile);

                if (speciesManager.hasSpecies(validationSpeciesName, validationVersion)) {
                    publish("ERROR: Species already exists: " + validationSpeciesName + "." + validationVersion);
                    return false;
                }
                
                // Validate genome file
                publish("Validating genome file format...");
                setProgress(25);
                GenomeFileValidator.ValidationResult genomeResult = GenomeFileValidator.validateFile(genomeFileObj);
                if (!genomeResult.isValid()) {
                    publish("ERROR: Genome file validation failed - " + genomeResult.getErrorMessage());
                    return false;
                }
                publish("✓ Genome file format valid: " + genomeResult.getFormat());
                
                // Validate annotation file with TBtools format detection
                publish("Validating annotation file format...");
                setProgress(50);
                
                // Use TBtools to detect GFF3/GTF format
                if (TBtoolsSequenceExtractor.isSupportedFormat(annotationFileObj)) {
                    String formatType = TBtoolsSequenceExtractor.getFileFormatString(annotationFileObj);
                    publish("✓ Annotation file format valid: " + formatType + " (detected by TBtools)");
                } else {
                    // Fallback to original validation
                    GenomeFileValidator.ValidationResult annotationResult = GenomeFileValidator.validateFile(annotationFileObj);
                    if (!annotationResult.isValid()) {
                        publish("ERROR: Annotation file validation failed - " + annotationResult.getErrorMessage());
                        return false;
                    }
                    publish("✓ Annotation file format valid: " + annotationResult.getFormat());
                }
                
                // TBtools Genome-Annotation Match Validation
                publish("Running TBtools genome-annotation compatibility check...");
                setProgress(60);
                TBtoolsGenomeValidator.TBtoolsValidationResult tbValidation = 
                    TBtoolsGenomeValidator.validateGenomeAnnotationMatch(genomeFileObj, annotationFileObj);
                
                if (!tbValidation.isValid()) {
                    publish("ERROR: TBtools validation failed - " + tbValidation.getMessage());
                    publish("Details: " + tbValidation.getDifferenceGxfSize() + " GXF chromosome IDs not found in genome");
                    if (!tbValidation.getGxfOnlyIds().isEmpty()) {
                        String missingIds = tbValidation.getGxfOnlyIds().toString();
                        publish("Missing IDs: " + missingIds.substring(0, Math.min(200, missingIds.length())));
                    }
                    return false;
                }
                
                // Report successful validation with statistics
                publish("✓ TBtools validation PASSED - Files are compatible");
                publish("Statistics: " + tbValidation.getIntersectionSize() + " common chromosome IDs, " +
                       tbValidation.getDifferenceFastaSize() + " genome-only IDs");
                
                // Show first few common chromosome IDs for confirmation
                if (!tbValidation.getIntersectionIds().isEmpty()) {
                    java.util.List<String> commonIdsList = new java.util.ArrayList<>(tbValidation.getIntersectionIds());
                    int showCount = Math.min(5, commonIdsList.size());
                    String idsToShow = commonIdsList.subList(0, showCount).toString();
                    publish("Common chromosome IDs (first " + showCount + "): " + 
                           idsToShow.replaceAll("[\\[\\]]", ""));
                    
                    if (commonIdsList.size() > 5) {
                        publish("... and " + (commonIdsList.size() - 5) + " more");
                    }
                }
                
                // Show warnings for genome-only sequences (sequences without annotations)
                if (tbValidation.getDifferenceFastaSize() > 0) {
                    publish("INFO: " + tbValidation.getDifferenceFastaSize() + " genome sequences have no annotations");
                    if (tbValidation.getDifferenceFastaSize() <= 10 && !tbValidation.getGenomeOnlyIds().isEmpty()) {
                        publish("Unannotated sequences: " + tbValidation.getGenomeOnlyIds().toString().replaceAll("[\\[\\]]", ""));
                    }
                }
                
                setProgress(100);
                publish("Validation completed successfully");
                
                return true;
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
                    boolean valid = get();
                    if (valid) {
                        updateStatus("Files validated successfully");
                        importButton.setEnabled(true);
                    } else {
                        updateStatus("File validation failed");
                        importButton.setEnabled(false);
                    }
                } catch (Exception e) {
                    updateStatus("Validation error: " + e.getMessage());
                    importButton.setEnabled(false);
                }
                progressBar.setValue(0);
                progressBar.setString("");
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
     * Import species
     */
    private void importSpeciesLegacy() {
        // Validate input
        String speciesName = speciesNameField.getText().trim();
        String version = versionField.getText().trim();
        String notes = notesArea.getText().trim();
        
        if (speciesName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a species name.", "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (version.isEmpty()) {
            version = "1.0"; // Default version
            versionField.setText(version);
        }
        
        clearLog();
        updateStatus("Importing species...");
        progressBar.setValue(0);
        
        // Disable controls during import
        setControlsEnabled(false);
        
        // Make variables effectively final for lambda
        final String finalSpeciesName = speciesName;
        final String finalVersion = version;
        final String finalNotes = notes;
        
        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    publish("Starting species import: " + finalSpeciesName + " v" + finalVersion);
                    
                    // Create species info
                    SpeciesInfo species = new SpeciesInfo(finalSpeciesName, finalVersion);
                    species.setNotes(finalNotes);
                    
                    // Add to species manager
                    setProgress(10);
                    publish("Creating species directory structure...");
                    if (!speciesManager.addSpecies(species)) {
                        publish("ERROR: Failed to create species in manager");
                        return false;
                    }
                    
                    // Copy files to species directories with species name prefix
                    String filePrefix = species.getSpeciesName() + "." + species.getVersion();
                    
                    setProgress(25);
                    publish("Copying genome file...");
                    File sourceGenome = new File(genomeFileField.getText().trim());
                    File targetGenome = new File(species.getSequenceDir(), filePrefix + ".genome." + getFileExtension(sourceGenome));
                    if (!copyFile(sourceGenome, targetGenome)) {
                        publish("ERROR: Failed to copy genome file");
                        return false;
                    }
                    
                    setProgress(50);
                    publish("Copying annotation file...");
                    File sourceAnnotation = new File(annotationFileField.getText().trim());
                    File targetAnnotation = new File(species.getAnnotationDir(), filePrefix + ".annotation." + getFileExtension(sourceAnnotation));
                    if (!copyFile(sourceAnnotation, targetAnnotation)) {
                        publish("ERROR: Failed to copy annotation file");
                        return false;
                    }

                    setProgress(60);
                    publish("Running TBtools GXF Fix on copied annotation...");
                    TBtoolsSequenceExtractor.ExtractionResult fixResult =
                        TBtoolsSequenceExtractor.fixAnnotationFileInPlace(targetAnnotation);
                    if (!fixResult.isSuccess()) {
                        publish("ERROR: Failed to fix annotation file - " + fixResult.getMessage());
                        return false;
                    }
                    publish("OK TBtools GXF Fix completed");
                    
                    // Generate FASTA index
                    setProgress(70);
                    publish("Creating FASTA index...");
                    if (!FastaIndexBuilder.buildIndex(targetGenome)) {
                        publish("WARNING: Failed to create FASTA index");
                    } else {
                        publish("✓ FASTA index created");
                    }
                    
                    // Extract cached sequence files
                    setProgress(75);
                    publish("Extracting transcript, CDS, and protein sequences...");
                    if (!extractCachedSequenceFiles(targetGenome, targetAnnotation, species, filePrefix)) {
                        publish("WARNING: Failed to extract some sequence files");
                    } else {
                        publish("✓ Cached sequence files generated");
                    }
                    
                    setProgress(82);
                    publish("Generating Random Gene Set...");
                    try {
                        DemoGeneSetGenerator.Result demoResult = DemoGeneSetGenerator.generateDemoGeneSet(species);
                        publish("Random Gene Set created: " + demoResult.getOutputFile().getName()
                            + " (" + demoResult.getSelectedTranscriptCount() + " representative transcripts)");
                    } catch (Exception demoException) {
                        publish("WARNING: Failed to create Random Gene Set - " + demoException.getMessage());
                        logger.log(Level.WARNING, "Failed to generate demo Gene Set during import", demoException);
                    }

                    // Calculate statistics
                    setProgress(90);
                    publish("Calculating genome statistics...");
                    GenomeData stats = GenomeStatsCalculator.calculateGenomeStats(targetGenome, targetAnnotation);
                    species.setGenomeData(stats);
                    stats.saveToFile(species.getStatsFile());
                    publish("✓ Statistics calculated and saved");
                    
                    // Save species metadata
                    setProgress(95);
                    publish("Saving species metadata...");
                    SpeciesMetadata metadata = SpeciesMetadata.fromSpeciesInfo(species);
                    if (metadata.saveToFile(species.getSpeciesDir())) {
                        publish("✓ Species metadata saved");
                    } else {
                        publish("WARNING: Failed to save species metadata");
                    }
                    
                    // Update species in manager
                    setProgress(98);
                    speciesManager.updateSpecies(species);
                    
                    setProgress(100);
                    publish("Species import completed successfully!");
                    
                    return true;
                    
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
                        updateStatus("Import completed successfully");
                        JOptionPane.showMessageDialog(SpeciesImportDialog.this,
                            "Species imported successfully!",
                            "Import Complete", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        updateStatus("Import failed");
                        setControlsEnabled(true);
                    }
                } catch (Exception e) {
                    updateStatus("Import error: " + e.getMessage());
                    setControlsEnabled(true);
                }
                progressBar.setValue(0);
                progressBar.setString("");
            }
        };
        
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });
        
        worker.execute();
    }

    private void validateFilesWithFix() {
        String speciesName = speciesNameField.getText().trim();
        if (speciesName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a species name before validation.",
                "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String version = versionField.getText().trim();
        if (version.isEmpty()) {
            version = "1.0";
            versionField.setText(version);
        }

        final String validationSpeciesName = speciesName;
        final String validationVersion = version;

        clearLog();
        clearPreparedAnnotationState();
        importButton.setEnabled(false);
        updateStatus("Validating files...");
        progressBar.setValue(0);

        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                publish("Starting file validation...");

                String genomeFile = genomeFileField.getText().trim();
                String annotationFile = annotationFileField.getText().trim();

                if (genomeFile.isEmpty() || annotationFile.isEmpty()) {
                    publish("ERROR: Please select both genome and annotation files");
                    return false;
                }

                if (speciesManager.hasSpecies(validationSpeciesName, validationVersion)) {
                    publish("ERROR: Species already exists: " + validationSpeciesName + "." + validationVersion);
                    return false;
                }

                File genomeFileObj = new File(genomeFile);
                File annotationFileObj = new File(annotationFile);

                publish("Validating genome file format...");
                setProgress(25);
                GenomeFileValidator.ValidationResult genomeResult = GenomeFileValidator.validateFile(genomeFileObj);
                if (!genomeResult.isValid()) {
                    publish("ERROR: Genome file validation failed - " + genomeResult.getErrorMessage());
                    return false;
                }
                publish("OK Genome file format valid: " + genomeResult.getFormat());

                publish("Preparing annotation file in target Annotation directory...");
                setProgress(40);
                File stagedAnnotationFile = prepareStagedAnnotationFile(
                    validationSpeciesName, validationVersion, annotationFileObj);

                publish("Running TBtools GXF Fix on staged annotation...");
                setProgress(50);
                TBtoolsSequenceExtractor.ExtractionResult fixResult =
                    TBtoolsSequenceExtractor.fixAnnotationFileInPlace(stagedAnnotationFile);
                if (!fixResult.isSuccess()) {
                    publish("ERROR: Failed to fix annotation file - " + fixResult.getMessage());
                    return false;
                }
                publish("OK " + fixResult.getMessage());

                byte[] stagedAnnotationBytes = java.nio.file.Files.readAllBytes(stagedAnnotationFile.toPath());
                if (stagedAnnotationBytes.length == 0) {
                    publish("ERROR: Fixed annotation result is empty");
                    return false;
                }

                publish("Validating fixed annotation file format...");
                if (TBtoolsSequenceExtractor.isSupportedFormat(stagedAnnotationFile)) {
                    String formatType = TBtoolsSequenceExtractor.getFileFormatString(stagedAnnotationFile);
                    publish("OK Annotation file format valid: " + formatType + " (detected by TBtools)");
                } else {
                    GenomeFileValidator.ValidationResult annotationResult = GenomeFileValidator.validateFile(stagedAnnotationFile);
                    if (!annotationResult.isValid()) {
                        publish("ERROR: Annotation file validation failed - " + annotationResult.getErrorMessage());
                        return false;
                    }
                    publish("OK Annotation file format valid: " + annotationResult.getFormat());
                }

                publish("Running TBtools genome-annotation compatibility check...");
                setProgress(60);
                TBtoolsGenomeValidator.TBtoolsValidationResult tbValidation =
                    TBtoolsGenomeValidator.validateGenomeAnnotationMatch(genomeFileObj, stagedAnnotationFile);

                if (!tbValidation.isValid()) {
                    publish("ERROR: TBtools validation failed - " + tbValidation.getMessage());
                    publish("Details: " + tbValidation.getDifferenceGxfSize() + " GXF chromosome IDs not found in genome");
                    if (!tbValidation.getGxfOnlyIds().isEmpty()) {
                        String missingIds = tbValidation.getGxfOnlyIds().toString();
                        publish("Missing IDs: " + missingIds.substring(0, Math.min(200, missingIds.length())));
                    }
                    return false;
                }

                publish("OK TBtools validation PASSED - Files are compatible");
                publish("Statistics: " + tbValidation.getIntersectionSize() + " common chromosome IDs, "
                    + tbValidation.getDifferenceFastaSize() + " genome-only IDs");

                if (!tbValidation.getIntersectionIds().isEmpty()) {
                    java.util.List<String> commonIdsList = new java.util.ArrayList<>(tbValidation.getIntersectionIds());
                    int showCount = Math.min(5, commonIdsList.size());
                    String idsToShow = commonIdsList.subList(0, showCount).toString();
                    publish("Common chromosome IDs (first " + showCount + "): "
                        + idsToShow.replaceAll("[\\[\\]]", ""));

                    if (commonIdsList.size() > 5) {
                        publish("... and " + (commonIdsList.size() - 5) + " more");
                    }
                }

                if (tbValidation.getDifferenceFastaSize() > 0) {
                    publish("INFO: " + tbValidation.getDifferenceFastaSize() + " genome sequences have no annotations");
                    if (tbValidation.getDifferenceFastaSize() <= 10 && !tbValidation.getGenomeOnlyIds().isEmpty()) {
                        publish("Unannotated sequences: " + tbValidation.getGenomeOnlyIds().toString().replaceAll("[\\[\\]]", ""));
                    }
                }

                validatedAnnotationBytes = stagedAnnotationBytes;
                validatedAnnotationFileName = stagedAnnotationFile.getName();
                validatedImportKey = buildImportValidationKey(
                    validationSpeciesName, validationVersion, genomeFile, annotationFile);

                setProgress(100);
                publish("Validation completed successfully");
                return true;
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
                    boolean valid = get();
                    if (valid) {
                        updateStatus("Files validated successfully");
                        importButton.setEnabled(true);
                    } else {
                        updateStatus("File validation failed");
                        importButton.setEnabled(false);
                    }
                } catch (Exception e) {
                    updateStatus("Validation error: " + e.getMessage());
                    importButton.setEnabled(false);
                }
                progressBar.setValue(0);
                progressBar.setString("");
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });

        worker.execute();
    }

    private void importSpeciesWithPreparedAnnotation() {
        String speciesName = speciesNameField.getText().trim();
        String version = versionField.getText().trim();
        String notes = notesArea.getText().trim();

        if (speciesName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a species name.",
                "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (version.isEmpty()) {
            version = "1.0";
            versionField.setText(version);
        }

        String currentImportKey = buildImportValidationKey(
            speciesName,
            version,
            genomeFileField.getText().trim(),
            annotationFileField.getText().trim()
        );
        if (validatedAnnotationBytes == null
                || validatedAnnotationBytes.length == 0
                || validatedAnnotationFileName == null
                || validatedImportKey == null
                || !validatedImportKey.equals(currentImportKey)) {
            JOptionPane.showMessageDialog(this,
                "Please validate files first, or re-validate after changing species name, version, genome, or annotation.",
                "Validation Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        clearLog();
        updateStatus("Importing species...");
        progressBar.setValue(0);
        setControlsEnabled(false);

        final String finalSpeciesName = speciesName;
        final String finalVersion = version;
        final String finalNotes = notes;
        final byte[] finalAnnotationBytes = java.util.Arrays.copyOf(
            validatedAnnotationBytes, validatedAnnotationBytes.length);
        final String finalAnnotationFileName = validatedAnnotationFileName;

        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    publish("Starting species import: " + finalSpeciesName + " v" + finalVersion);

                    SpeciesInfo species = new SpeciesInfo(finalSpeciesName, finalVersion);
                    species.setNotes(finalNotes);

                    setProgress(10);
                    publish("Creating species directory structure...");
                    if (!speciesManager.addSpecies(species)) {
                        publish("ERROR: Failed to create species in manager");
                        return false;
                    }

                    String filePrefix = species.getSpeciesName() + "." + species.getVersion();

                    setProgress(25);
                    publish("Copying genome file...");
                    File sourceGenome = new File(genomeFileField.getText().trim());
                    File targetGenome = new File(species.getSequenceDir(),
                        filePrefix + ".genome." + getFileExtension(sourceGenome));
                    if (!copyFile(sourceGenome, targetGenome)) {
                        publish("ERROR: Failed to copy genome file");
                        return false;
                    }

                    setProgress(50);
                    publish("Cleaning Annotation directory...");
                    clearDirectoryContents(species.getAnnotationDir());
                    File targetAnnotation = new File(species.getAnnotationDir(), finalAnnotationFileName);
                    publish("Writing validated annotation file...");
                    writeBytesToFile(targetAnnotation, finalAnnotationBytes);

                    setProgress(70);
                    publish("Creating FASTA index...");
                    if (!FastaIndexBuilder.buildIndex(targetGenome)) {
                        publish("WARNING: Failed to create FASTA index");
                    } else {
                        publish("OK FASTA index created");
                    }

                    setProgress(75);
                    publish("Extracting transcript, CDS, and protein sequences...");
                    if (!extractCachedSequenceFiles(targetGenome, targetAnnotation, species, filePrefix)) {
                        publish("WARNING: Failed to extract some sequence files");
                    } else {
                        publish("OK Cached sequence files generated");
                    }

                    setProgress(82);
                    publish("Generating Random Gene Set...");
                    try {
                        DemoGeneSetGenerator.Result demoResult = DemoGeneSetGenerator.generateDemoGeneSet(species);
                        publish("Random Gene Set created: " + demoResult.getOutputFile().getName()
                            + " (" + demoResult.getSelectedTranscriptCount() + " representative transcripts)");
                    } catch (Exception demoException) {
                        publish("WARNING: Failed to create Random Gene Set - " + demoException.getMessage());
                        logger.log(Level.WARNING, "Failed to generate demo Gene Set during import", demoException);
                    }

                    setProgress(90);
                    publish("Calculating genome statistics...");
                    GenomeData stats = GenomeStatsCalculator.calculateGenomeStats(targetGenome, targetAnnotation);
                    species.setGenomeData(stats);
                    stats.saveToFile(species.getStatsFile());
                    publish("OK Statistics calculated and saved");

                    setProgress(95);
                    publish("Saving species metadata...");
                    SpeciesMetadata metadata = SpeciesMetadata.fromSpeciesInfo(species);
                    if (metadata.saveToFile(species.getSpeciesDir())) {
                        publish("OK Species metadata saved");
                    } else {
                        publish("WARNING: Failed to save species metadata");
                    }

                    setProgress(98);
                    speciesManager.updateSpecies(species);

                    setProgress(100);
                    publish("Species import completed successfully!");
                    return true;

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
                        clearPreparedAnnotationState();
                        updateStatus("Import completed successfully");
                        JOptionPane.showMessageDialog(SpeciesImportDialog.this,
                            "Species imported successfully!",
                            "Import Complete", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        updateStatus("Import failed");
                        setControlsEnabled(true);
                    }
                } catch (Exception e) {
                    updateStatus("Import error: " + e.getMessage());
                    setControlsEnabled(true);
                }
                progressBar.setValue(0);
                progressBar.setString("");
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                progressBar.setValue((Integer) evt.getNewValue());
            }
        });

        worker.execute();
    }

    private void clearPreparedAnnotationState() {
        validatedAnnotationBytes = null;
        validatedAnnotationFileName = null;
        validatedImportKey = null;
    }

    private String buildImportValidationKey(String speciesName, String version, String genomeFile, String annotationFile) {
        return speciesName.trim() + "|" + version.trim() + "|" + genomeFile.trim() + "|" + annotationFile.trim();
    }

    private File prepareStagedAnnotationFile(String speciesName, String version, File sourceAnnotation) throws java.io.IOException {
        File annotationDir = resolveStagingAnnotationDirectory(speciesName, version);
        clearDirectoryContents(annotationDir);

        String filePrefix = speciesName + "." + version;
        File stagedAnnotation = new File(annotationDir, filePrefix + ".annotation." + getFileExtension(sourceAnnotation));
        if (!copyFile(sourceAnnotation, stagedAnnotation)) {
            throw new java.io.IOException("Failed to stage annotation file for validation");
        }
        return stagedAnnotation;
    }

    private File resolveStagingAnnotationDirectory(String speciesName, String version) throws java.io.IOException {
        File dataRoot = speciesManager.getDataRootDirectory();
        if (dataRoot == null || !dataRoot.exists()) {
            throw new java.io.IOException("Data root directory is not configured");
        }

        if (speciesManager.hasSpecies(speciesName, version)) {
            throw new java.io.IOException("Species already exists: " + speciesName + "." + version);
        }

        File speciesDir = new File(dataRoot, speciesName + "." + version);
        File annotationDir = new File(speciesDir, "Annotation");
        if (!annotationDir.exists() && !annotationDir.mkdirs()) {
            throw new java.io.IOException("Failed to create Annotation directory: " + annotationDir.getAbsolutePath());
        }
        return annotationDir;
    }

    private void clearDirectoryContents(File dir) throws java.io.IOException {
        if (dir == null) {
            throw new java.io.IOException("Directory is null");
        }

        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new java.io.IOException("Failed to create directory: " + dir.getAbsolutePath());
            }
            return;
        }

        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            deleteRecursively(child);
        }
    }

    private void deleteRecursively(File file) throws java.io.IOException {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        if (!file.delete()) {
            throw new java.io.IOException("Failed to delete: " + file.getAbsolutePath());
        }
    }

    private void writeBytesToFile(File target, byte[] content) throws java.io.IOException {
        if (target == null) {
            throw new java.io.IOException("Target file is null");
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new java.io.IOException("Failed to create parent directory: " + parent.getAbsolutePath());
        }

        java.nio.file.Files.write(target.toPath(), content);
    }
    
    /**
     * Copy file with automatic decompression for .gz files
     * If source is .gz, it will be decompressed to target
     * Otherwise, it will be copied normally
     */
    private boolean copyFile(File source, File target) {
        try {
            // Check if source is gzipped
            if (FileReaderUtil.isGzipFile(source)) {
                // Decompress .gz file while copying
                try (java.io.InputStream is = new java.io.FileInputStream(source);
                     java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(is);
                     java.io.OutputStream os = new java.io.FileOutputStream(target)) {

                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = gzis.read(buffer)) > 0) {
                        os.write(buffer, 0, len);
                    }
                }
                logger.info("Decompressed and copied: " + source.getName() + " -> " + target.getName());
            } else {
                // Normal file copy
                java.nio.file.Files.copy(source.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied: " + source.getName() + " -> " + target.getName());
            }
            return true;
        } catch (Exception e) {
            logger.severe("Failed to copy file: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get file extension (ignoring .gz compression)
     * For example: "genome.fasta.gz" returns "fasta"
     */
    private String getFileExtension(File file) {
        // Use FileReaderUtil to get original extension (without .gz)
        String extension = FileReaderUtil.getOriginalFileExtension(file);
        return extension.isEmpty() ? "txt" : extension;
    }
    
    /**
     * Enable/disable controls
     */
    private void setControlsEnabled(boolean enabled) {
        speciesNameField.setEnabled(enabled);
        versionField.setEnabled(enabled);
        notesArea.setEnabled(enabled);
        genomeFileField.setEnabled(enabled);
        annotationFileField.setEnabled(enabled);
        browseGenomeButton.setEnabled(enabled);
        browseAnnotationButton.setEnabled(enabled);
        validateButton.setEnabled(enabled);
        importButton.setEnabled(enabled && importButton.isEnabled());
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
     * Custom drag-drop panel
     */
    private static class DropTargetPanel extends JPanel {
        private String message;
        
        public DropTargetPanel(String message) {
            this.message = message;
            setBackground(Color.LIGHT_GRAY);
            setBorder(BorderFactory.createDashedBorder(Color.GRAY, 2, 5, 5, false));
            setPreferredSize(new Dimension(200, 80));
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.DARK_GRAY);
            FontMetrics fm = g.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(message)) / 2;
            int y = (getHeight() + fm.getAscent()) / 2;
            g.drawString(message, x, y);
        }
    }
    
    /**
     * Extract cached sequence files (transcripts, CDS, proteins) during import
     */
    private boolean extractCachedSequenceFiles(File genomeFile, File annotationFile, 
            SpeciesInfo species, String filePrefix) {
        try {
            File sequenceDir = species.getSequenceDir();
            boolean allSuccess = true;
            
            // Check annotation file format first
            String formatType = TBtoolsSequenceExtractor.getFileFormatString(annotationFile);
            logger.info("Processing annotation file in " + formatType + " format");
            
            // Extract representative transcripts using TBtools
            File transcriptFile = new File(sequenceDir, filePrefix + ".transcripts.fasta");
            TBtoolsSequenceExtractor.ExtractionResult transcriptResult = 
                TBtoolsSequenceExtractor.extractRepresentativeTranscripts(
                    genomeFile, annotationFile, transcriptFile, "LONGEST_CDS");
            if (!transcriptResult.isSuccess()) {
                logger.warning("Failed to extract transcripts: " + transcriptResult.getMessage());
                allSuccess = false;
            } else {
                logger.info("Extracted " + transcriptResult.getSequencesExtracted() + " representative transcripts");
            }
            
            // Extract ALL transcripts using TBtools
            File allTranscriptFile = new File(sequenceDir, filePrefix + ".all_transcripts.fasta");
            TBtoolsSequenceExtractor.ExtractionResult allTranscriptResult = 
                TBtoolsSequenceExtractor.extractAllTranscripts(
                    genomeFile, annotationFile, allTranscriptFile);
            if (!allTranscriptResult.isSuccess()) {
                logger.warning("Failed to extract all transcripts: " + allTranscriptResult.getMessage());
                allSuccess = false;
            } else {
                logger.info("Extracted " + allTranscriptResult.getSequencesExtracted() + " total transcripts");
            }
            
            // Extract representative CDS sequences using TBtools
            File cdsFile = new File(sequenceDir, filePrefix + ".cds.fasta");
            TBtoolsSequenceExtractor.ExtractionResult cdsResult = 
                TBtoolsSequenceExtractor.extractRepresentativeCDS(
                    genomeFile, annotationFile, cdsFile, "LONGEST_CDS");
            if (!cdsResult.isSuccess()) {
                logger.warning("Failed to extract CDS: " + cdsResult.getMessage());
                allSuccess = false;
            } else {
                logger.info("Extracted " + cdsResult.getSequencesExtracted() + " representative CDS sequences");
            }
            
            // Extract ALL CDS sequences using TBtools
            File allCdsFile = new File(sequenceDir, filePrefix + ".all_cds.fasta");
            TBtoolsSequenceExtractor.ExtractionResult allCdsResult = 
                TBtoolsSequenceExtractor.extractAllCDS(
                    genomeFile, annotationFile, allCdsFile);
            if (!allCdsResult.isSuccess()) {
                logger.warning("Failed to extract all CDS: " + allCdsResult.getMessage());
                allSuccess = false;
            } else {
                logger.info("Extracted " + allCdsResult.getSequencesExtracted() + " total CDS sequences");
            }
            
            // Extract representative protein sequences using TBtools
            File proteinFile = new File(sequenceDir, filePrefix + ".proteins.fasta");
            TBtoolsSequenceExtractor.ExtractionResult proteinResult = 
                TBtoolsSequenceExtractor.extractRepresentativeProteins(
                    genomeFile, annotationFile, proteinFile, "LONGEST_CDS");
            if (!proteinResult.isSuccess()) {
                logger.warning("Failed to extract proteins: " + proteinResult.getMessage());
                allSuccess = false;
            } else {
                logger.info("Extracted " + proteinResult.getSequencesExtracted() + " representative proteins");
            }
            
            // Extract ALL protein sequences using TBtools
            File allProteinFile = new File(sequenceDir, filePrefix + ".all_proteins.fasta");
            TBtoolsSequenceExtractor.ExtractionResult allProteinResult = 
                TBtoolsSequenceExtractor.extractAllProteins(
                    genomeFile, annotationFile, allProteinFile);
            if (!allProteinResult.isSuccess()) {
                logger.warning("Failed to extract all proteins: " + allProteinResult.getMessage());
                allSuccess = false;
            } else {
                logger.info("Extracted " + allProteinResult.getSequencesExtracted() + " total proteins");
            }
            
            return allSuccess;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error extracting cached sequence files", e);
            return false;
        }
    }
}
