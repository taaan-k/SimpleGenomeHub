/*
 * Sequence Extraction Dialog
 */
package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.TBtoolsSequenceExtractor;
import simplegenomehub.util.fileio.RepresentativeTranscriptSelector;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dialog for extracting sequences from genome and annotation files
 * Supports transcript, CDS, and protein sequence extraction
 * 
 * @author SimpleGenomeHub
 */
public class SequenceExtractionDialog extends JDialog {
    
    private SpeciesInfo species;
    
    // Extraction options
    private JCheckBox extractTranscriptsCheck;
    private JCheckBox extractCdsCheck;
    private JCheckBox extractProteinsCheck;
    private JCheckBox extractPromotersCheck;
    private JTextField promoterLengthField;
    private JCheckBox representativeOnlyCheck;
    
    // Representative transcript options
    private JRadioButton longestTranscriptRadio;
    private JRadioButton longestCdsRadio;
    private JRadioButton highestExpressionRadio;
    private JRadioButton firstAnnotationRadio;
    private ButtonGroup representativeGroup;
    
    // Output options
    private JTextField outputDirField;
    private JButton browseButton;
    
    // Control buttons
    private JButton extractButton;
    private JButton extractGffButton;
    private JButton openOutputDirButton;
    private JButton cancelButton;
    
    // Progress components
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JTextArea logArea;
    
    /**
     * Constructor
     */
    public SequenceExtractionDialog(Window parent, SpeciesInfo species) {
        super(parent, "Extract Sequences - " + species.getDisplayName(), ModalityType.APPLICATION_MODAL);
        
        this.species = species;
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        validateInputs();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();  // Automatically adjust dialog size based on content
        setLocationRelativeTo(parent);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Extraction type checkboxes
        extractTranscriptsCheck = new JCheckBox("Extract Transcripts", true);
        extractCdsCheck = new JCheckBox("Extract CDS Sequences", true);
        extractProteinsCheck = new JCheckBox("Extract Protein Sequences", true);
        extractPromotersCheck = new JCheckBox("Extract 'Promoter' Sequences", false);
        promoterLengthField = new JTextField("2000", 8);
        promoterLengthField.setToolTipText("Upstream region length in base pairs");
        representativeOnlyCheck = new JCheckBox("Representative transcripts only", false);
        
        // Representative selection options
        longestTranscriptRadio = new JRadioButton("Longest transcript", true);
        longestCdsRadio = new JRadioButton("Longest CDS");
        highestExpressionRadio = new JRadioButton("Highest expression (if available)");
        firstAnnotationRadio = new JRadioButton("First in annotation");
        
        representativeGroup = new ButtonGroup();
        representativeGroup.add(longestTranscriptRadio);
        representativeGroup.add(longestCdsRadio);
        representativeGroup.add(highestExpressionRadio);
        representativeGroup.add(firstAnnotationRadio);
        
        // Output directory
        outputDirField = new JTextField(40);
        browseButton = new JButton("Browse...");

        // Control buttons
        extractButton = new JButton("Extract Sequences");
        extractGffButton = new JButton("Generate Rep. GFF3");
        openOutputDirButton = new JButton("Open Output Directory");
        cancelButton = new JButton("Cancel");
        
        // Progress components
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("");
        statusLabel = new JLabel("Ready for sequence extraction");
        
        logArea = new JTextArea(8, 60);
        logArea.setEditable(false);
        logArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        logArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // Set button sizes
        browseButton.setPreferredSize(new Dimension(80, 25));
        extractButton.setPreferredSize(new Dimension(140, 30));
        extractGffButton.setPreferredSize(new Dimension(140, 30));
        openOutputDirButton.setPreferredSize(new Dimension(160, 30));
        cancelButton.setPreferredSize(new Dimension(80, 30));
        
        // Set consistent button font
        Font buttonFont = SimpleGenomeHubStyle.FONT_SANS_PLAIN_11;
        browseButton.setFont(buttonFont);
        extractButton.setFont(buttonFont);
        extractGffButton.setFont(buttonFont);
        openOutputDirButton.setFont(buttonFont);
        cancelButton.setFont(buttonFont);
        
        // Set default output directory to species directory
        if (species.getSpeciesDir() != null) {
            File extractedDir = new File(species.getSpeciesDir(), "extracted");
            outputDirField.setText(extractedDir.getAbsolutePath());
        } else {
            outputDirField.setText(System.getProperty("user.home") + File.separator + "Desktop");
        }
        
        // Initially disable representative options
        updateRepresentativeOptions();
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Options panel (Sequence Types + Representative Selection)
        JPanel optionsPanel = createOptionsPanel();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(10, 10, 5, 10);
        add(optionsPanel, gbc);

        // Output options panel
        JPanel outputPanel = createOutputPanel();
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 10, 5, 10);
        add(outputPanel, gbc);

        // Bottom panel (Status, Progress, Log, Buttons)
        JPanel bottomPanel = createBottomPanel();
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(5, 10, 10, 10);
        add(bottomPanel, gbc);
    }
    
    /**
     * Create extraction options panel
     */
    private JPanel createOptionsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Sequence types panel
        JPanel typesPanel = new JPanel();
        typesPanel.setBorder(new TitledBorder("Sequence Types to Extract"));
        typesPanel.setLayout(new BoxLayout(typesPanel, BoxLayout.Y_AXIS));

        typesPanel.add(extractTranscriptsCheck);
        typesPanel.add(extractCdsCheck);
        typesPanel.add(extractProteinsCheck);
        typesPanel.add(extractPromotersCheck);

        // Add parameter panel for promoter length
        JPanel promoterParamPanel = new JPanel();
        promoterParamPanel.setLayout(new BoxLayout(promoterParamPanel, BoxLayout.X_AXIS));
        promoterParamPanel.add(new JLabel("Upstream:"));
        promoterParamPanel.add(Box.createHorizontalStrut(5));
        promoterParamPanel.add(promoterLengthField);
        promoterParamPanel.add(Box.createHorizontalStrut(5));
        promoterParamPanel.add(new JLabel("bp"));
        promoterParamPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        typesPanel.add(promoterParamPanel);

        typesPanel.add(Box.createVerticalStrut(10));
        typesPanel.add(representativeOnlyCheck);

        // Add types panel to main panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;  // Don't expand vertically
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(typesPanel, gbc);

        // Representative selection options
        JPanel representativePanel = new JPanel();
        representativePanel.setBorder(new TitledBorder("Representative Transcript Selection"));
        representativePanel.setLayout(new BoxLayout(representativePanel, BoxLayout.Y_AXIS));

        representativePanel.add(longestTranscriptRadio);
        representativePanel.add(longestCdsRadio);
        representativePanel.add(highestExpressionRadio);
        representativePanel.add(firstAnnotationRadio);

        // Add description
        JTextArea descArea = new JTextArea(
            "Representative selection only applies when multiple transcripts exist for a gene. " +
            "This option reduces redundancy by selecting one transcript per gene based on the chosen criteria.");
        descArea.setEditable(false);
        descArea.setOpaque(false);
        descArea.setFont(SimpleGenomeHubStyle.plain(descArea.getFont(), 11f));
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        representativePanel.add(descArea);

        // Add representative panel to main panel
        gbc.gridy = 1;
        gbc.weighty = 0.0;  // Don't expand vertically
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(representativePanel, gbc);

        return panel;
    }
    
    /**
     * Create output options panel
     */
    private JPanel createOutputPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("Output Options"));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Label
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(5, 5, 5, 5);
        panel.add(new JLabel("Output Directory: "), gbc);

        // TextField - expands horizontally
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(outputDirField, gbc);

        // Button - no expansion
        gbc.gridx = 2;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        panel.add(browseButton, gbc);

        return panel;
    }
    
    /**
     * Create bottom panel with progress and controls
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Status and progress panel
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(statusPanel, gbc);

        // Log area panel
        JPanel logPanel = new JPanel(new GridBagLayout());
        logPanel.setBorder(new TitledBorder("Extraction Log"));
        GridBagConstraints logGbc = new GridBagConstraints();

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(620, 120));

        logGbc.gridx = 0;
        logGbc.gridy = 0;
        logGbc.fill = GridBagConstraints.BOTH;
        logGbc.weightx = 1.0;
        logGbc.weighty = 1.0;
        logPanel.add(logScrollPane, logGbc);

        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 0, 5, 0);
        panel.add(logPanel, gbc);

        // Control buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(extractButton);
        buttonPanel.add(extractGffButton);
        buttonPanel.add(openOutputDirButton);
        buttonPanel.add(cancelButton);

        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(buttonPanel, gbc);

        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        representativeOnlyCheck.addActionListener(e -> updateRepresentativeOptions());

        extractPromotersCheck.addActionListener(e -> {
            promoterLengthField.setEnabled(extractPromotersCheck.isSelected());
        });

        browseButton.addActionListener(e -> browseForOutputDirectory());
        
        extractButton.addActionListener(e -> performExtraction());
        
        extractGffButton.addActionListener(e -> generateRepresentativeGff());
        
        openOutputDirButton.addActionListener(e -> openOutputDirectory());
        
        cancelButton.addActionListener(e -> dispose());
    }
    
    /**
     * Update representative options availability
     */
    private void updateRepresentativeOptions() {
        boolean enabled = representativeOnlyCheck.isSelected();
        longestTranscriptRadio.setEnabled(enabled);
        longestCdsRadio.setEnabled(enabled);
        highestExpressionRadio.setEnabled(enabled);
        firstAnnotationRadio.setEnabled(enabled);
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
     * Open output directory in system file explorer
     */
    private void openOutputDirectory() {
        String outputPath = outputDirField.getText().trim();
        if (outputPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please specify an output directory first.", 
                "No Output Directory", 
                JOptionPane.WARNING_MESSAGE);
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
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                return;
            }
        }
        
        try {
            // Open directory in system file explorer
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(outputDir);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Desktop operations not supported on this system.",
                    "Cannot Open Directory",
                    JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Failed to open output directory: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Validate inputs
     */
    private void validateInputs() {
        // Check if species has necessary files
        boolean hasGenome = species.hasGenomeFiles();
        boolean hasAnnotation = species.hasAnnotationFiles();
        
        if (!hasGenome || !hasAnnotation) {
            String message = "Missing required files:\n";
            if (!hasGenome) message += "- Genome file (FASTA)\n";
            if (!hasAnnotation) message += "- Annotation file (GFF3/GTF)\n";
            message += "\nPlease ensure both files are available before extraction.";
            
            JOptionPane.showMessageDialog(this, message,
                "Missing Files", JOptionPane.WARNING_MESSAGE);
            
            extractButton.setEnabled(false);
        }
    }
    
    /**
     * Perform sequence extraction
     */
    private void performExtraction() {
        // Validate selections
        if (!extractTranscriptsCheck.isSelected() &&
            !extractCdsCheck.isSelected() &&
            !extractProteinsCheck.isSelected() &&
            !extractPromotersCheck.isSelected()) {

            JOptionPane.showMessageDialog(this,
                "Please select at least one sequence type to extract.",
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
            if (!outputDir.mkdirs()) {
                JOptionPane.showMessageDialog(this,
                    "Failed to create output directory.",
                    "Directory Creation Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        // Disable controls during extraction
        setControlsEnabled(false);
        clearLog();
        updateStatus("Starting sequence extraction...");
        progressBar.setValue(0);
        
        // Get input files
        File genomeFile = getGenomeFile();
        File annotationFile = getAnnotationFile();
        
        if (genomeFile == null || annotationFile == null) {
            JOptionPane.showMessageDialog(this,
                "Cannot find genome or annotation files.",
                "Files Not Found", JOptionPane.ERROR_MESSAGE);
            setControlsEnabled(true);
            return;
        }
        
        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    AtomicInteger completedTasks = new AtomicInteger(0);
                    int totalTasks = 0;
                    
                    // Count total tasks
                    if (extractTranscriptsCheck.isSelected()) totalTasks++;
                    if (extractCdsCheck.isSelected()) totalTasks++;
                    if (extractProteinsCheck.isSelected()) totalTasks++;
                    if (extractPromotersCheck.isSelected()) totalTasks++;
                    
                    boolean success = true;
                    
                    // Extract transcripts
                    if (extractTranscriptsCheck.isSelected()) {
                        publish("Extracting transcript sequences...");
                        String transcriptFileName = species.getSpeciesDirectoryName() +
                            (representativeOnlyCheck.isSelected() ? "_representative_transcripts.fasta" : "_transcripts.fasta");
                        File outputFile = new File(outputDir, transcriptFileName);

                        TBtoolsSequenceExtractor.ExtractionResult result;
                        if (representativeOnlyCheck.isSelected()) {
                            String criteria = getSelectionCriteria();
                            publish("Using representative selection criteria: " + criteria);
                            result = TBtoolsSequenceExtractor.extractRepresentativeTranscripts(genomeFile, annotationFile, outputFile, criteria);
                        } else {
                            result = TBtoolsSequenceExtractor.extractAllTranscripts(genomeFile, annotationFile, outputFile);
                        }

                        if (result.isSuccess()) {
                            publish("✓ Extracted transcript sequences to " + outputFile.getName());
                        } else {
                            publish("✗ Transcript extraction failed: " + result.getMessage());
                            success = false;
                        }

                        setProgress((completedTasks.incrementAndGet() * 100) / totalTasks);
                    }
                    
                    // Extract CDS
                    if (extractCdsCheck.isSelected()) {
                        publish("Extracting CDS sequences...");
                        String cdsFileName = species.getSpeciesDirectoryName() +
                            (representativeOnlyCheck.isSelected() ? "_representative_cds.fasta" : "_cds.fasta");
                        File outputFile = new File(outputDir, cdsFileName);

                        TBtoolsSequenceExtractor.ExtractionResult result;
                        if (representativeOnlyCheck.isSelected()) {
                            String criteria = getSelectionCriteria();
                            result = TBtoolsSequenceExtractor.extractRepresentativeCDS(genomeFile, annotationFile, outputFile, criteria);
                        } else {
                            result = TBtoolsSequenceExtractor.extractAllCDS(genomeFile, annotationFile, outputFile);
                        }

                        if (result.isSuccess()) {
                            publish("✓ Extracted CDS sequences to " + outputFile.getName());
                        } else {
                            publish("✗ CDS extraction failed");
                            success = false;
                        }

                        setProgress((completedTasks.incrementAndGet() * 100) / totalTasks);
                    }
                    
                    // Extract proteins
                    if (extractProteinsCheck.isSelected()) {
                        publish("Extracting protein sequences...");
                        String proteinFileName = species.getSpeciesDirectoryName() +
                            (representativeOnlyCheck.isSelected() ? "_representative_proteins.fasta" : "_proteins.fasta");
                        File outputFile = new File(outputDir, proteinFileName);

                        TBtoolsSequenceExtractor.ExtractionResult result;
                        if (representativeOnlyCheck.isSelected()) {
                            String criteria = getSelectionCriteria();
                            result = TBtoolsSequenceExtractor.extractRepresentativeProteins(genomeFile, annotationFile, outputFile, criteria);
                        } else {
                            result = TBtoolsSequenceExtractor.extractAllProteins(genomeFile, annotationFile, outputFile);
                        }

                        if (result.isSuccess()) {
                            publish("✓ Extracted protein sequences to " + outputFile.getName());
                        } else {
                            publish("✗ Protein extraction failed");
                            success = false;
                        }

                        setProgress((completedTasks.incrementAndGet() * 100) / totalTasks);
                    }

                    // Extract promoter sequences if selected
                    if (extractPromotersCheck.isSelected()) {
                        publish("Extracting promoter sequences...");

                        try {
                            int promoterLength = getPromoterLength();
                            publish("Promoter upstream length: " + promoterLength + " bp");

                            String promoterFileName = species.getSpeciesDirectoryName() + "_promoters.fasta";
                            File outputFile = new File(outputDir, promoterFileName);

                            TBtoolsSequenceExtractor.ExtractionResult result =
                                TBtoolsSequenceExtractor.extractPromoters(genomeFile, annotationFile, outputFile, promoterLength);

                            if (result.isSuccess()) {
                                publish("✓ Extracted promoter sequences to " + outputFile.getName() +
                                       " (" + result.getSequencesExtracted() + " sequences)");
                            } else {
                                publish("✗ Promoter extraction failed: " + result.getMessage());
                                success = false;
                            }
                        } catch (NumberFormatException e) {
                            publish("✗ Invalid promoter length: " + e.getMessage());
                            success = false;
                        } catch (Exception e) {
                            publish("✗ Promoter extraction error: " + e.getMessage());
                            success = false;
                        }

                        setProgress((completedTasks.incrementAndGet() * 100) / totalTasks);
                    }

                    if (success) {
                        publish("Sequence extraction completed successfully!");
                    } else {
                        publish("Sequence extraction completed with errors.");
                    }
                    
                    return success;
                    
                } catch (Exception e) {
                    publish("ERROR: " + e.getMessage());
                    return false;
                }
            }
            
            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    appendToLog(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        updateStatus("Extraction completed successfully");
                        JOptionPane.showMessageDialog(SequenceExtractionDialog.this,
                            "Sequence extraction completed successfully!\n" +
                            "Files saved to: " + outputDir.getAbsolutePath(),
                            "Extraction Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        updateStatus("Extraction completed with errors");
                    }
                } catch (Exception e) {
                    updateStatus("Extraction failed: " + e.getMessage());
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
     * Get genome file
     */
    private File getGenomeFile() {
        if (species.getSequenceDir() == null) return null;

        File[] genomeFiles = species.getSequenceDir().listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            // Must contain "genome" and end with fasta extension
            return lowerName.contains("genome") &&
                   (lowerName.endsWith(".fa") || lowerName.endsWith(".fasta") || lowerName.endsWith(".fas"));
        });

        // If genome file found, return it
        if (genomeFiles != null && genomeFiles.length > 0) {
            return genomeFiles[0];
        }

        // Fallback: return any fasta file (but this shouldn't happen in normal cases)
        File[] allFastaFiles = species.getSequenceDir().listFiles((dir, name) ->
            name.toLowerCase().endsWith(".fa") ||
            name.toLowerCase().endsWith(".fasta") ||
            name.toLowerCase().endsWith(".fas"));

        return (allFastaFiles != null && allFastaFiles.length > 0) ? allFastaFiles[0] : null;
    }
    
    /**
     * Get annotation file
     */
    private File getAnnotationFile() {
        if (species.getAnnotationDir() == null) return null;
        
        File[] annotationFiles = species.getAnnotationDir().listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".gff") || 
            name.toLowerCase().endsWith(".gff3") ||
            name.toLowerCase().endsWith(".gtf"));
        
        return (annotationFiles != null && annotationFiles.length > 0) ? annotationFiles[0] : null;
    }

    /**
     * Get and validate promoter length input
     */
    private int getPromoterLength() throws NumberFormatException {
        String text = promoterLengthField.getText().trim();
        int length = Integer.parseInt(text);
        if (length <= 0) {
            throw new NumberFormatException("Promoter length must be positive");
        }
        if (length > 10000) {
            throw new NumberFormatException("Promoter length too large (max 10000 bp)");
        }
        return length;
    }

    /**
     * Get selection criteria (simplified for now)
     */
    private String getSelectionCriteria() {
        if (longestTranscriptRadio.isSelected()) {
            return "LONGEST_TRANSCRIPT";
        } else if (longestCdsRadio.isSelected()) {
            return "LONGEST_CDS";
        } else if (highestExpressionRadio.isSelected()) {
            return "HIGHEST_EXPRESSION";
        } else {
            return "FIRST_IN_ANNOTATION";
        }
    }
    
    /**
     * Enable/disable controls
     */
    private void setControlsEnabled(boolean enabled) {
        extractTranscriptsCheck.setEnabled(enabled);
        extractCdsCheck.setEnabled(enabled);
        extractProteinsCheck.setEnabled(enabled);
        extractPromotersCheck.setEnabled(enabled);
        promoterLengthField.setEnabled(enabled && extractPromotersCheck.isSelected());
        representativeOnlyCheck.setEnabled(enabled);
        longestTranscriptRadio.setEnabled(enabled && representativeOnlyCheck.isSelected());
        longestCdsRadio.setEnabled(enabled && representativeOnlyCheck.isSelected());
        highestExpressionRadio.setEnabled(enabled && representativeOnlyCheck.isSelected());
        firstAnnotationRadio.setEnabled(enabled && representativeOnlyCheck.isSelected());
        outputDirField.setEnabled(enabled);
        browseButton.setEnabled(enabled);
        extractButton.setEnabled(enabled);
        extractGffButton.setEnabled(enabled);
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
     * Generate representative transcript GFF3 file
     */
    private void generateRepresentativeGff() {
        String outputPath = outputDirField.getText().trim();
        if (outputPath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please specify an output directory.",
                "No Output Directory", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        File outputDir = new File(outputPath);
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                JOptionPane.showMessageDialog(this,
                    "Failed to create output directory.",
                    "Directory Creation Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        // Disable controls during generation
        setControlsEnabled(false);
        clearLog();
        updateStatus("Generating representative transcript GFF3...");
        progressBar.setValue(0);
        
        // Get input files
        File genomeFile = getGenomeFile();
        File annotationFile = getAnnotationFile();
        
        if (genomeFile == null || annotationFile == null) {
            JOptionPane.showMessageDialog(this,
                "Cannot find genome or annotation files.",
                "Files Not Found", JOptionPane.ERROR_MESSAGE);
            setControlsEnabled(true);
            return;
        }
        
        SwingWorker<Boolean, String> worker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try {
                    publish("Starting representative transcript GFF3 generation...");
                    
                    setProgress(25);
                    
                    // Convert selection criteria to enum
                    RepresentativeTranscriptSelector.SelectionCriteria criteria = convertSelectionCriteria(getSelectionCriteria());
                    publish("Using selection criteria: " + criteria);
                    
                    setProgress(50);
                    
                    // Generate GFF3 output file
                    File outputFile = new File(outputDir, species.getSpeciesDirectoryName() + "_representative.gff3");
                    
                    publish("Writing representative transcript GFF3 to: " + outputFile.getName());
                    
                    setProgress(75);
                    
                    // Use the existing RepresentativeTranscriptSelector to generate the annotation
                    boolean success = RepresentativeTranscriptSelector.generateRepresentativeAnnotation(
                        annotationFile, outputFile, criteria);
                    
                    setProgress(100);
                    
                    if (success) {
                        publish("✓ Representative transcript GFF3 generated successfully!");
                        publish("Output file: " + outputFile.getAbsolutePath());
                    } else {
                        publish("ERROR: Failed to generate representative transcript GFF3");
                        return false;
                    }
                    
                    return true;
                    
                } catch (Exception e) {
                    publish("ERROR: " + e.getMessage());
                    return false;
                }
            }
            
            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    appendToLog(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        updateStatus("GFF3 generation completed successfully");
                        JOptionPane.showMessageDialog(SequenceExtractionDialog.this,
                            "Representative transcript GFF3 generated successfully!\n" +
                            "File saved to: " + outputDir.getAbsolutePath(),
                            "Generation Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        updateStatus("GFF3 generation failed");
                    }
                } catch (Exception e) {
                    updateStatus("GFF3 generation error: " + e.getMessage());
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
     * Convert string criteria to SelectionCriteria enum
     */
    private RepresentativeTranscriptSelector.SelectionCriteria convertSelectionCriteria(String criteria) {
        switch (criteria) {
            case "LONGEST_TRANSCRIPT":
                return RepresentativeTranscriptSelector.SelectionCriteria.LONGEST_TRANSCRIPT;
            case "LONGEST_CDS":
                return RepresentativeTranscriptSelector.SelectionCriteria.LONGEST_CDS;
            case "HIGHEST_EXPRESSION":
                return RepresentativeTranscriptSelector.SelectionCriteria.MOST_EXONS; // Map to closest available
            case "FIRST_IN_ANNOTATION":
            default:
                return RepresentativeTranscriptSelector.SelectionCriteria.CANONICAL; // Map to default
        }
    }
}
