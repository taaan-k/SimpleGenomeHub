/*
 * Annotation Import Dialog
 * Supports importing GO, KEGG, and other functional annotation files
 */
package simplegenomehub.gui;

import biocjava.bioDoer.Kegg.KeggBackEndConstants;
import simplegenomehub.model.*;
import simplegenomehub.model.GeneAnnotationData.AnnotationType;
import simplegenomehub.util.fileio.GoOboManager;
import simplegenomehub.util.fileio.KeggBackendManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Dialog for importing functional annotation files
 * Supports GO.obo, Gene2GO, KEGG Gene2KO, and custom annotation formats
 * 
 * @author SimpleGenomeHub
 */
public class AnnotationImportDialog extends JDialog {
    
    private static final Logger logger = Logger.getLogger(AnnotationImportDialog.class.getName());
    
    private SpeciesInfo targetSpecies;
    private GeneAnnotationData annotationData;
    private boolean importSuccessful = false;
    
    // UI Components
    private ButtonGroup annotationTypeGroup;
    private JRadioButton goRadioButton;
    private JRadioButton keggRadioButton;
    private JRadioButton interproRadioButton;
    private JRadioButton pfamRadioButton;
    private JRadioButton customRadioButton;
    private JTextField annotationFileField;
    private JButton browseAnnotationButton;
    private JTextField oboFileField;
    private JButton browseOboButton;
    private JButton downloadOboButton;
    private JButton updateOboButton;
    private JLabel oboLabel;
    private JLabel oboStatusLabel;
    private JComboBox<String> keggBackendModeCombo;
    private JComboBox<String> keggBackendTypeCombo;
    private JTextField keggRefFileField;
    private JButton browseKeggRefButton;
    private JButton downloadKeggBackendButton;
    private JButton updateKeggBackendButton;
    private JLabel keggRefLabel;
    private JLabel keggStatusLabel;
    private JTextArea previewArea;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JComboBox<String> delimiterCombo;
    private JLabel validationLabel;
    
    // Action buttons
    private JButton importButton;
    private JButton cancelButton;
    private File selectedGoOboOverride;
    private GoOboManager.ResolutionResult goOboResolution;
    private File selectedKeggBackendOverride;
    private KeggBackendManager.ResolutionResult keggBackendResolution;
    
    /**
     * Constructor
     */
    public AnnotationImportDialog(Window parent, SpeciesInfo species) {
        super(parent, "Import Functional Annotations - " + species.getSpeciesName(), ModalityType.APPLICATION_MODAL);
        this.targetSpecies = species;
        this.annotationData = species.getFunctionalAnnotations();
        
        // Create new annotation data if none exists
        if (this.annotationData == null) {
            this.annotationData = new GeneAnnotationData("ANNO_" + species.getSpeciesName(), 
                                                        species.getSpeciesName() + " Annotations");
            species.setFunctionalAnnotations(this.annotationData);
        }
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        setSize(750, 650);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(700, 600));
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Annotation type selection with radio buttons
        annotationTypeGroup = new ButtonGroup();
        goRadioButton = new JRadioButton("GO (Gene Ontology)", true);
        keggRadioButton = new JRadioButton("KEGG (Pathway)");
        interproRadioButton = new JRadioButton("InterPro (Domain)");
        pfamRadioButton = new JRadioButton("Pfam (Protein Family)");
        customRadioButton = new JRadioButton("Custom Annotation");
        
        annotationTypeGroup.add(goRadioButton);
        annotationTypeGroup.add(keggRadioButton);
        annotationTypeGroup.add(interproRadioButton);
        annotationTypeGroup.add(pfamRadioButton);
        annotationTypeGroup.add(customRadioButton);
        
        // File selection fields
        annotationFileField = new JTextField(50);
        browseAnnotationButton = new JButton("Browse...");
        
        oboFileField = new JTextField(50);
        oboFileField.setEditable(false);
        browseOboButton = new JButton("Choose...");
        downloadOboButton = new JButton("Download Latest");
        updateOboButton = new JButton("Update");
        oboLabel = new JLabel("GO OBO Resource:");
        oboStatusLabel = new JLabel("Resolving go-basic.obo status");
        oboStatusLabel.setForeground(Color.GRAY);
        
        keggBackendModeCombo = new JComboBox<>(new String[]{"Preset Backend", "Custom Backend"});
        keggBackendTypeCombo = new JComboBox<>(KeggBackEndConstants.BACKEND_TYPES);
        keggBackendTypeCombo.setSelectedItem("Plants");
        SimpleGenomeHubUi.setComboBoxDisplayWidth(keggBackendModeCombo, 180);
        SimpleGenomeHubUi.setComboBoxDisplayWidth(keggBackendTypeCombo, 220);
        keggRefFileField = new JTextField(50);
        keggRefFileField.setEditable(false);
        browseKeggRefButton = new JButton("Choose...");
        downloadKeggBackendButton = new JButton("Download Selected");
        updateKeggBackendButton = new JButton("Update");
        keggRefLabel = new JLabel("KEGG Backend:");
        keggStatusLabel = new JLabel("Select KEGG backend mode");
        keggStatusLabel.setForeground(Color.GRAY);
        String savedKeggMode = simplegenomehub.config.SimpleGenomeHubConfig.getInstance()
            .getProperty(simplegenomehub.config.SimpleGenomeHubConfig.KEGG_BACKEND_MODE);
        if ("CUSTOM".equalsIgnoreCase(savedKeggMode)) {
            keggBackendModeCombo.setSelectedItem("Custom Backend");
        }
        String savedKeggType = simplegenomehub.config.SimpleGenomeHubConfig.getInstance()
            .getProperty(simplegenomehub.config.SimpleGenomeHubConfig.KEGG_BACKEND_TYPE);
        if (savedKeggType != null && !savedKeggType.trim().isEmpty()) {
            keggBackendTypeCombo.setSelectedItem(savedKeggType);
        }
        
        // Delimiter selection
        delimiterCombo = new JComboBox<>(new String[]{"Auto Detect", "Tab", "Comma", "Semicolon"});
        delimiterCombo.setSelectedItem("Auto Detect");
        SimpleGenomeHubUi.setComboBoxDisplayWidth(delimiterCombo, 160);
        
        // Validation status
        validationLabel = new JLabel("File format will be validated");
        validationLabel.setForeground(Color.GRAY);
        
        // Preview area
        previewArea = new JTextArea(8, 70);
        previewArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        previewArea.setEditable(false);
        previewArea.setBackground(new Color(248, 248, 248));
        
        // Progress components
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        
        statusLabel = new JLabel("Select annotation files to import");
        
        // Action buttons
        importButton = new JButton("Import Annotations");
        cancelButton = new JButton("Cancel");
        importButton.setEnabled(false);
        
        // Setup drag and drop for file fields
        setupDragAndDrop();
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // Top: Instructions
        JLabel instructionLabel = new JLabel(
            "<html><b>Functional Annotation Import</b><br>" + 
            "Import GO, KEGG, and other functional annotation files for enrichment analysis.</html>");
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        add(instructionLabel, BorderLayout.NORTH);
        
        // Center: Main content
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top: File selection
        JPanel filePanel = createFileSelectionPanel();
        mainPanel.add(filePanel, BorderLayout.NORTH);
        
        // Center: Preview
        JPanel previewPanel = createPreviewPanel();
        mainPanel.add(previewPanel, BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Bottom: Status and controls
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        
        // Status panel
        JPanel statusPanel = new JPanel(new BorderLayout(5, 5));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        bottomPanel.add(statusPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(cancelButton);
        buttonPanel.add(importButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create file selection panel
     */
    private JPanel createFileSelectionPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("Annotation Files"));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Annotation type selection with radio buttons
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("Annotation Type:"), gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel radioPanel = new JPanel(new GridLayout(3, 2, 5, 2));
        radioPanel.add(goRadioButton);
        radioPanel.add(keggRadioButton);
        radioPanel.add(interproRadioButton);
        radioPanel.add(pfamRadioButton);
        radioPanel.add(customRadioButton);
        radioPanel.add(new JLabel("")); // Empty placeholder for alignment
        panel.add(radioPanel, gbc);
        
        // Main annotation file
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Annotation File:"), gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(annotationFileField, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(browseAnnotationButton, gbc);
        
        // Delimiter selection
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("File Format:"), gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(delimiterCombo, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(validationLabel, gbc);
        
        // GO OBO file (conditional)
        gbc.gridx = 0; gbc.gridy = 3; gbc.anchor = GridBagConstraints.WEST;
        panel.add(oboLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(oboFileField, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(browseOboButton, gbc);

        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 2; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel oboControlPanel = new JPanel(new BorderLayout(8, 0));
        oboControlPanel.add(oboStatusLabel, BorderLayout.CENTER);
        JPanel oboButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        oboButtonPanel.add(downloadOboButton);
        oboButtonPanel.add(updateOboButton);
        oboControlPanel.add(oboButtonPanel, BorderLayout.EAST);
        panel.add(oboControlPanel, gbc);
        
        // KEGG reference file (conditional)
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(keggRefLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel keggModePanel = new JPanel(new BorderLayout(5, 0));
        keggModePanel.add(keggBackendModeCombo, BorderLayout.WEST);
        keggModePanel.add(keggBackendTypeCombo, BorderLayout.CENTER);
        panel.add(keggModePanel, gbc);
        
        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(downloadKeggBackendButton, gbc);

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Backend File:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(keggRefFileField, gbc);

        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        JPanel keggActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        keggActionPanel.add(browseKeggRefButton);
        keggActionPanel.add(updateKeggBackendButton);
        panel.add(keggActionPanel, gbc);

        gbc.gridx = 1; gbc.gridy = 7; gbc.gridwidth = 2; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(keggStatusLabel, gbc);
        
        // File format info - optimized for minimum 3 lines visibility
        gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.3; // Allow vertical expansion
        
        JTextArea formatInfo = new JTextArea(
            "File format requirements:\n" +
            "• Minimum 2 columns: Gene_ID and Annotation_ID\n" +
            "• GO: Gene_ID, GO_ID [, Term, Evidence, Category]\n" +
            "• KEGG: Gene_ID, KEGG_ID [, Pathway_Name]\n" +
            "• Supports TAB, CSV, or other delimited formats\n" +
            "• Drag and drop files directly into text fields", 6, 60);
        formatInfo.setEditable(false);
        formatInfo.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        formatInfo.setBackground(new Color(248, 248, 248));
        formatInfo.setLineWrap(true);
        formatInfo.setWrapStyleWord(true);
        
        JScrollPane formatScrollPane = new JScrollPane(formatInfo);
        formatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        formatScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        
        // Calculate minimum height for 3 lines of text - reduced to 2/3 of original
        FontMetrics fm = formatInfo.getFontMetrics(formatInfo.getFont());
        int lineHeight = fm.getHeight();
        int minHeight = Math.max(lineHeight * 3 + 15, 60); // At least 3 lines + reduced padding, minimum 60px
        
        formatScrollPane.setMinimumSize(new Dimension(600, minHeight));
        formatScrollPane.setPreferredSize(new Dimension(600, Math.max(minHeight, 80))); // Reduced preferred size to 2/3
        
        panel.add(formatScrollPane, gbc);
        
        // Reset weighty for subsequent components
        gbc.weighty = 0;
        
        return panel;
    }
    
    /**
     * Create preview panel
     */
    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("File Preview"));
        
        JScrollPane scrollPane = new JScrollPane(previewArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        // Radio button listeners
        goRadioButton.addActionListener(e -> updateFileLabels());
        keggRadioButton.addActionListener(e -> updateFileLabels());
        interproRadioButton.addActionListener(e -> updateFileLabels());
        pfamRadioButton.addActionListener(e -> updateFileLabels());
        customRadioButton.addActionListener(e -> updateFileLabels());
        keggBackendModeCombo.addActionListener(e -> updateKeggBackendControls());
        keggBackendTypeCombo.addActionListener(e -> updateKeggBackendControls());
        
        browseAnnotationButton.addActionListener(e -> browseForAnnotationFile());
        browseOboButton.addActionListener(e -> browseForOboFile());
        downloadOboButton.addActionListener(e -> downloadLatestOboFile());
        updateOboButton.addActionListener(e -> updateOboFile());
        browseKeggRefButton.addActionListener(e -> browseForKeggRefFile());
        downloadKeggBackendButton.addActionListener(e -> downloadSelectedKeggBackend());
        updateKeggBackendButton.addActionListener(e -> updateSelectedKeggBackend());
        
        annotationFileField.addActionListener(e -> loadFilePreview());
        
        // Add text change listener for real-time validation
        annotationFileField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateAndPreview(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateAndPreview(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateAndPreview(); }
        });
        
        delimiterCombo.addActionListener(e -> validateAndPreview());
        
        importButton.addActionListener(e -> performImport());
        cancelButton.addActionListener(e -> dispose());
        
        // Update labels when annotation type changes
        updateFileLabels();
    }
    
    /**
     * Get selected annotation type from radio buttons
     */
    private AnnotationType getSelectedAnnotationType() {
        if (goRadioButton.isSelected()) return AnnotationType.GO;
        if (keggRadioButton.isSelected()) return AnnotationType.KEGG;
        if (interproRadioButton.isSelected()) return AnnotationType.INTERPRO;
        if (pfamRadioButton.isSelected()) return AnnotationType.PFAM;
        if (customRadioButton.isSelected()) return AnnotationType.CUSTOM;
        return AnnotationType.GO; // Default
    }
    
    /**
     * Update file labels based on annotation type
     */
    private void updateFileLabels() {
        AnnotationType selectedType = getSelectedAnnotationType();
        
        // Show/hide optional file fields based on type
        boolean isGoType = (selectedType == AnnotationType.GO);
        boolean isKeggType = (selectedType == AnnotationType.KEGG);
        
        // GO OBO file - only visible for GO annotations
        oboLabel.setVisible(isGoType);
        oboFileField.setVisible(isGoType);
        browseOboButton.setVisible(isGoType);
        downloadOboButton.setVisible(isGoType);
        updateOboButton.setVisible(isGoType);
        oboStatusLabel.setVisible(isGoType);
        
        // KEGG reference file - only visible for KEGG annotations
        keggRefLabel.setVisible(isKeggType);
        keggBackendModeCombo.setVisible(isKeggType);
        keggBackendTypeCombo.setVisible(isKeggType);
        keggRefFileField.setVisible(isKeggType);
        browseKeggRefButton.setVisible(isKeggType);
        downloadKeggBackendButton.setVisible(isKeggType);
        updateKeggBackendButton.setVisible(isKeggType);
        keggStatusLabel.setVisible(isKeggType);
        
        if (!isGoType) {
            selectedGoOboOverride = null;
            goOboResolution = null;
            oboFileField.setText("");
            oboStatusLabel.setText("");
        }
        if (!isKeggType) {
            selectedKeggBackendOverride = null;
            keggBackendResolution = null;
            keggRefFileField.setText("");
            keggStatusLabel.setText("");
        }
        if (isGoType) {
            refreshGoOboResolution();
        }
        if (isKeggType) {
            updateKeggBackendControls();
        }
        
        // Update status and revalidate
        statusLabel.setText("Select " + selectedType.getShortName() + " annotation file");
        validateAndPreview();
        
        // Refresh the layout
        revalidate();
        repaint();
    }
    
    /**
     * Browse for annotation file
     */
    private void browseForAnnotationFile() {
        JFileChooser fileChooser = new JFileChooser();
        AnnotationType selectedType = getSelectedAnnotationType();
        
        if (selectedType == AnnotationType.GO) {
            fileChooser.setFileFilter(new FileNameExtensionFilter(
                "GO Annotation Files", "txt", "tsv", "gaf", "goa"));
        } else if (selectedType == AnnotationType.KEGG) {
            fileChooser.setFileFilter(new FileNameExtensionFilter(
                "KEGG Annotation Files", "txt", "tsv", "ko"));
        } else {
            fileChooser.setFileFilter(new FileNameExtensionFilter(
                "Annotation Files", "txt", "tsv", "csv"));
        }
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            annotationFileField.setText(selectedFile.getAbsolutePath());
            loadFilePreview();
        }
    }
    
    /**
     * Browse for GO OBO file
     */
    private void browseForOboFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            "GO OBO Files", "obo"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (!GoOboManager.validateOboFile(selectedFile)) {
                JOptionPane.showMessageDialog(this,
                    "Selected file is not a readable go-basic.obo file.",
                    "Invalid GO OBO File", JOptionPane.ERROR_MESSAGE);
                return;
            }

            selectedGoOboOverride = selectedFile;
            GoOboManager.cacheOboForSpecies(targetSpecies, selectedFile, true);
            refreshGoOboResolution();
        }
    }

    private void downloadLatestOboFile() {
        setGoOboButtonsEnabled(false);
        oboStatusLabel.setText("Downloading latest go-basic.obo via TBtools provider...");
        GoOboManager.downloadLatestToGlobalCache(this, () -> {
            GoOboManager.cacheOboForSpecies(targetSpecies, GoOboManager.getGlobalOboFile(), false);
            setGoOboButtonsEnabled(true);
            refreshGoOboResolution();
            validateAndPreview();
        });
    }

    private void updateOboFile() {
        downloadLatestOboFile();
    }

    private void refreshGoOboResolution() {
        goOboResolution = GoOboManager.resolveForSpecies(targetSpecies, selectedGoOboOverride);

        if (goOboResolution == null || !goOboResolution.exists()) {
            oboFileField.setText("");
            oboStatusLabel.setText("No go-basic.obo found. Choose a local file or download the latest OBO.");
            oboStatusLabel.setForeground(Color.RED);
            updateOboButton.setEnabled(false);
            return;
        }

        File resolvedFile = goOboResolution.getResolvedFile();
        oboFileField.setText(resolvedFile != null ? resolvedFile.getAbsolutePath() : "");
        oboStatusLabel.setText(goOboResolution.getMessage());
        oboStatusLabel.setForeground(goOboResolution.isUpdateRecommended() ? new Color(180, 120, 0) : new Color(0, 128, 0));
        updateOboButton.setEnabled(true);
    }

    private void setGoOboButtonsEnabled(boolean enabled) {
        browseOboButton.setEnabled(enabled);
        downloadOboButton.setEnabled(enabled);
        updateOboButton.setEnabled(enabled && goRadioButton.isSelected());
    }
    
    /**
     * Browse for KEGG reference file
     */
    private void browseForKeggRefFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            "KEGG Reference Files", "keg", "txt", "tsv"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (!KeggBackendManager.validateBackendFile(selectedFile)) {
                JOptionPane.showMessageDialog(this,
                    "Selected file is not a readable KEGG backend/reference file.",
                    "Invalid KEGG Backend File", JOptionPane.ERROR_MESSAGE);
                return;
            }
            selectedKeggBackendOverride = selectedFile;
            KeggBackendManager.cacheCustomForSpecies(targetSpecies, selectedFile);
            refreshKeggBackendResolution();
            validateAndPreview();
        }
    }

    private void updateKeggBackendControls() {
        boolean presetMode = isKeggPresetMode();
        simplegenomehub.config.SimpleGenomeHubConfig config = simplegenomehub.config.SimpleGenomeHubConfig.getInstance();
        config.setProperty(simplegenomehub.config.SimpleGenomeHubConfig.KEGG_BACKEND_MODE,
            presetMode ? "PRESET" : "CUSTOM");
        config.setProperty(simplegenomehub.config.SimpleGenomeHubConfig.KEGG_BACKEND_TYPE, getSelectedKeggBackendType());
        keggBackendTypeCombo.setEnabled(presetMode);
        downloadKeggBackendButton.setEnabled(presetMode);
        browseKeggRefButton.setEnabled(!presetMode);
        keggRefFileField.setEditable(false);
        refreshKeggBackendResolution();
        validateAndPreview();
    }

    private boolean isKeggPresetMode() {
        return "Preset Backend".equals(String.valueOf(keggBackendModeCombo.getSelectedItem()));
    }

    private String getSelectedKeggBackendType() {
        Object selected = keggBackendTypeCombo.getSelectedItem();
        return selected != null ? selected.toString() : "Plants";
    }

    private void refreshKeggBackendResolution() {
        KeggBackendManager.BackendMode mode = isKeggPresetMode()
            ? KeggBackendManager.BackendMode.PRESET
            : KeggBackendManager.BackendMode.CUSTOM;
        keggBackendResolution = KeggBackendManager.resolveForSpecies(
            targetSpecies, mode, getSelectedKeggBackendType(), selectedKeggBackendOverride);

        if (keggBackendResolution == null || !keggBackendResolution.exists()) {
            keggRefFileField.setText("");
            keggStatusLabel.setText(isKeggPresetMode()
                ? "No preset KEGG backend available. Download the selected backend."
                : "No custom KEGG backend selected.");
            keggStatusLabel.setForeground(Color.RED);
            updateKeggBackendButton.setEnabled(isKeggPresetMode());
            return;
        }

        File resolvedFile = keggBackendResolution.getResolvedFile();
        keggRefFileField.setText(resolvedFile != null ? resolvedFile.getAbsolutePath() : "");
        keggStatusLabel.setText(keggBackendResolution.getMessage());
        keggStatusLabel.setForeground(keggBackendResolution.isUpdateRecommended()
            ? new Color(180, 120, 0) : new Color(0, 128, 0));
        updateKeggBackendButton.setEnabled(isKeggPresetMode());
    }

    private void downloadSelectedKeggBackend() {
        setKeggButtonsEnabled(false);
        keggStatusLabel.setText("Downloading KEGG backend via TBtools provider...");
        String backendType = getSelectedKeggBackendType();
        KeggBackendManager.downloadLatestPresetBackend(this, backendType, () -> {
            File globalFile = KeggBackendManager.getGlobalPresetBackendFile(backendType);
            if (globalFile != null && globalFile.isFile()) {
                KeggBackendManager.cachePresetForSpecies(targetSpecies, backendType, globalFile);
            }
            setKeggButtonsEnabled(true);
            refreshKeggBackendResolution();
            validateAndPreview();
        });
    }

    private void updateSelectedKeggBackend() {
        downloadSelectedKeggBackend();
    }

    private void setKeggButtonsEnabled(boolean enabled) {
        boolean presetMode = isKeggPresetMode();
        downloadKeggBackendButton.setEnabled(enabled && presetMode);
        updateKeggBackendButton.setEnabled(enabled && presetMode);
        browseKeggRefButton.setEnabled(enabled && !presetMode);
        keggBackendTypeCombo.setEnabled(enabled && presetMode);
        keggBackendModeCombo.setEnabled(enabled);
    }
    
    /**
     * Load and preview selected annotation file
     */
    private void loadFilePreview() {
        validateAndPreview();
    }
    
    /**
     * Validate file format and show preview
     */
    private void validateAndPreview() {
        String filePath = annotationFileField.getText().trim();
        if (filePath.isEmpty()) {
            previewArea.setText("Select an annotation file to see preview");
            validationLabel.setText("No file selected");
            validationLabel.setForeground(Color.GRAY);
            importButton.setEnabled(false);
            return;
        }
        
        File file = new File(filePath);
        if (!file.exists()) {
            previewArea.setText("File not found: " + filePath);
            validationLabel.setText("File not found");
            validationLabel.setForeground(Color.RED);
            importButton.setEnabled(false);
            return;
        }
        
        // Check for required supplementary files based on annotation type
        AnnotationType currentSelectedType = getSelectedAnnotationType();
        
        if (currentSelectedType == AnnotationType.GO) {
            refreshGoOboResolution();
            if (goOboResolution == null || !goOboResolution.exists()) {
                previewArea.setText("GO import requires a usable go-basic.obo resource.\nChoose a local file or download the latest OBO.");
                validationLabel.setText("GO OBO resource required");
                validationLabel.setForeground(Color.RED);
                importButton.setEnabled(false);
                return;
            }
        }
        
        // For KEGG annotations, reference file is mandatory
        if (currentSelectedType == AnnotationType.KEGG) {
            refreshKeggBackendResolution();
            if (keggBackendResolution == null || !keggBackendResolution.exists()) {
                previewArea.setText(isKeggPresetMode()
                    ? "KEGG preset backend is required.\nDownload the selected backend or switch to custom backend."
                    : "KEGG custom backend file is required.\nChoose a readable KEGG backend file.");
                validationLabel.setText("KEGG backend required");
                validationLabel.setForeground(Color.RED);
                importButton.setEnabled(false);
                return;
            }
        }
        
        try {
            // Detect delimiter and validate format
            FileFormatInfo formatInfo = analyzeFileFormat(file);
            
            if (!formatInfo.isValid) {
                previewArea.setText("Invalid file format:\n" + formatInfo.errorMessage + "\n\nFirst few lines:\n" + formatInfo.preview);
                validationLabel.setText(formatInfo.errorMessage);
                validationLabel.setForeground(Color.RED);
                importButton.setEnabled(false);
                return;
            }
            
            // Show preview with detected format info
            StringBuilder preview = new StringBuilder();
            preview.append("Format: ").append(formatInfo.delimiter.equals("\t") ? "Tab-delimited" : 
                          formatInfo.delimiter.equals(",") ? "CSV" : "Other delimited").append("\n");
            preview.append("Columns: ").append(formatInfo.columnCount).append("\n");
            preview.append("Sample data rows: ").append(formatInfo.dataRows).append("\n\n");
            preview.append(formatInfo.preview);
            
            previewArea.setText(preview.toString());
            
            // Update validation status
            AnnotationType selectedType = getSelectedAnnotationType();
            validationLabel.setText("✓ Valid " + selectedType.getShortName() + " format");
            validationLabel.setForeground(new Color(0, 128, 0));
            
            if (selectedType == AnnotationType.GO && goOboResolution != null && goOboResolution.exists()) {
                statusLabel.setText("Ready to import " + formatInfo.dataRows + " GO annotations using "
                    + goOboResolution.getSourceType().name().toLowerCase());
            } else if (selectedType == AnnotationType.KEGG && keggBackendResolution != null && keggBackendResolution.exists()) {
                statusLabel.setText("Ready to import " + formatInfo.dataRows + " KEGG annotations using "
                    + keggBackendResolution.getBackendMode().name().toLowerCase());
            } else {
                statusLabel.setText("Ready to import " + formatInfo.dataRows + " " + selectedType.getShortName() + " annotations");
            }
            importButton.setEnabled(true);
            
        } catch (Exception e) {
            previewArea.setText("Error reading file: " + e.getMessage());
            validationLabel.setText("Read error");
            validationLabel.setForeground(Color.RED);
            importButton.setEnabled(false);
            logger.warning("Error analyzing annotation file: " + e.getMessage());
        }
    }
    
    /**
     * Perform the import operation
     */
    private void performImport() {
        String annotationFilePath = annotationFileField.getText().trim();
        if (annotationFilePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please select an annotation file first.", "No File Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        File annotationFile = new File(annotationFilePath);
        if (!annotationFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "Annotation file not found: " + annotationFilePath, "File Not Found", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Validate required supplementary files
        AnnotationType selectedType = getSelectedAnnotationType();
        File resolvedGoOboFile = null;
        File resolvedKeggBackendFile = null;
        
        if (selectedType == AnnotationType.GO) {
            refreshGoOboResolution();
            if (goOboResolution == null || !goOboResolution.exists()) {
                JOptionPane.showMessageDialog(this,
                    "GO import requires a usable go-basic.obo resource.\nChoose a local file or download the latest OBO.",
                    "GO OBO Resource Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            resolvedGoOboFile = goOboResolution.getResolvedFile();
        }
        
        // For KEGG annotations, reference file is mandatory
        if (selectedType == AnnotationType.KEGG) {
            refreshKeggBackendResolution();
            if (keggBackendResolution == null || !keggBackendResolution.exists()) {
                JOptionPane.showMessageDialog(this,
                    "KEGG import requires a usable backend.\nDownload the selected preset backend or choose a custom backend file.",
                    "KEGG Backend Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            resolvedKeggBackendFile = keggBackendResolution.getResolvedFile();
        }
        
        // Show progress
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        importButton.setEnabled(false);
        
        // Perform import in background
        final File goOboFileForImport = resolvedGoOboFile;
        final File keggBackendFileForImport = resolvedKeggBackendFile;
        SwingWorker<Boolean, String> importWorker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                final AnnotationType selectedType = getSelectedAnnotationType();
                
                publish("Setting up annotation data...");
                
                // Optional files are handled during import process
                // GO OBO and KEGG reference files are used for term descriptions
                // but the current implementation loads them directly during annotation import
                
                publish("Loading annotations from file...");
                
                // For Custom annotations, clear existing data of that type to avoid mixing with old data
                if (selectedType == AnnotationType.CUSTOM) {
                    publish("Clearing existing Custom annotations...");
                    annotationData.clearAnnotationsOfType(AnnotationType.CUSTOM);
                }
                
                // Import annotations based on type with delimiter detection
                boolean success = false;
                switch (selectedType) {
                    case GO:
                        success = loadGOAnnotationsWithDelimiter(annotationData, annotationFile);
                        break;
                    case KEGG:
                        success = loadKEGGAnnotationsWithDelimiter(annotationData, annotationFile);
                        break;
                    case INTERPRO:
                    case PFAM:
                    case CUSTOM:
                        success = loadCustomAnnotationsWithDelimiter(annotationData, annotationFile, selectedType);
                        break;
                }
                
                if (success) {
                    publish("Processing transcript-to-gene mapping...");
                    
                    // Check if we need to convert transcript IDs to gene IDs
                    int originalGeneCount = annotationData.getAnnotatedGenes();
                    if (originalGeneCount == 0) {
                        // Try to find GFF3 file for mapping
                        File gffFile = targetSpecies.getAnnotationFile();
                        if (gffFile != null) {
                            publish("Converting transcript IDs to gene IDs...");
                            boolean converted = annotationData.convertTranscriptToGeneAnnotations(gffFile);
                            if (converted) {
                                System.out.println("DEBUG: Converted transcript IDs to gene IDs");
                                System.out.println("DEBUG: After conversion - Annotated genes: " + annotationData.getAnnotatedGenes());
                            }
                        }
                    }
                    
                    // Enhance GO annotations with OBO term information if available
                    if (selectedType == AnnotationType.GO && goOboFileForImport != null && goOboFileForImport.exists()) {
                        publish("Enhancing GO annotations with OBO term information...");
                        boolean enhanced = annotationData.enhanceGOAnnotationsWithOBO(goOboFileForImport);
                        if (enhanced) {
                            System.out.println("DEBUG: Enhanced GO annotations with term names and descriptions");
                        }

                        publish("Caching GO OBO file for this species...");
                        GoOboManager.cacheOboForSpecies(targetSpecies, goOboFileForImport, true);
                    }
                    
                    // Enhance KEGG annotations with background information if available
                    if (selectedType == AnnotationType.KEGG && keggBackendFileForImport != null && keggBackendFileForImport.exists()) {
                        publish("Enhancing KEGG annotations with background information...");
                        boolean enhanced = annotationData.enhanceKEGGAnnotationsWithBackground(keggBackendFileForImport);
                        if (enhanced) {
                            System.out.println("DEBUG: Enhanced KEGG annotations with pathway and hierarchy information");
                        }

                        publish("Caching KEGG backend for this species...");
                        if (isKeggPresetMode()) {
                            KeggBackendManager.cachePresetForSpecies(targetSpecies, getSelectedKeggBackendType(), keggBackendFileForImport);
                        } else {
                            KeggBackendManager.cacheCustomForSpecies(targetSpecies, keggBackendFileForImport);
                        }
                    }
                    
                    publish("Saving annotation data...");
                    // Set the updated annotation data to species first
                    targetSpecies.setFunctionalAnnotations(annotationData);
                    
                    // Save functional annotations to species
                    targetSpecies.saveFunctionalAnnotations();
                    
                    // Debug: Print final annotation counts
                    System.out.println("DEBUG: Import successful. Annotation counts: " + annotationData.getAnnotationCounts());
                    System.out.println("DEBUG: Final annotated genes: " + annotationData.getAnnotatedGenes());
                }
                
                return success;
            }
            
            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    progressBar.setString(message);
                    statusLabel.setText(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    importSuccessful = get();
                    
                    if (importSuccessful) {
                        AnnotationType selectedType = getSelectedAnnotationType();
                        int annotationCount = annotationData.getAnnotationCounts().get(selectedType);
                        
                        JOptionPane.showMessageDialog(AnnotationImportDialog.this,
                            "Successfully imported " + annotationCount + " " + 
                            selectedType.getShortName() + " annotations!",
                            "Import Complete", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(AnnotationImportDialog.this,
                            "Failed to import annotations. Please check the file format and try again.",
                            "Import Failed", JOptionPane.ERROR_MESSAGE);
                    }
                    
                } catch (Exception e) {
                    logger.warning("Import error: " + e.getMessage());
                    JOptionPane.showMessageDialog(AnnotationImportDialog.this,
                        "Import error: " + e.getMessage(),
                        "Import Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    progressBar.setVisible(false);
                    progressBar.setIndeterminate(false);
                    importButton.setEnabled(true);
                }
            }
        };
        
        importWorker.execute();
    }
    
    /**
     * Analyze file format and validate structure
     */
    private FileFormatInfo analyzeFileFormat(File file) throws Exception {
        FileFormatInfo info = new FileFormatInfo();
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            StringBuilder preview = new StringBuilder();
            String line;
            int lineCount = 0;
            int dataRows = 0;
            String detectedDelimiter = null;
            int columnCount = 0;
            
            while ((line = reader.readLine()) != null && lineCount < 20) {
                lineCount++;
                preview.append(line).append("\n");
                
                // Skip empty lines and comments
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Auto-detect delimiter on first data line
                if (detectedDelimiter == null) {
                    String selectedDelimiter = (String) delimiterCombo.getSelectedItem();
                    if ("Auto Detect".equals(selectedDelimiter)) {
                        // Count potential delimiters
                        int tabCount = line.split("\t", -1).length - 1;
                        int commaCount = line.split(",", -1).length - 1;
                        int semicolonCount = line.split(";", -1).length - 1;
                        
                        if (tabCount >= commaCount && tabCount >= semicolonCount && tabCount > 0) {
                            detectedDelimiter = "\t";
                        } else if (commaCount >= semicolonCount && commaCount > 0) {
                            detectedDelimiter = ",";
                        } else if (semicolonCount > 0) {
                            detectedDelimiter = ";";
                        } else {
                            detectedDelimiter = "\t"; // Default
                        }
                    } else {
                        // Use manual selection
                        switch (selectedDelimiter) {
                            case "Tab": detectedDelimiter = "\t"; break;
                            case "Comma": detectedDelimiter = ","; break;
                            case "Semicolon": detectedDelimiter = ";"; break;
                            default: detectedDelimiter = "\t";
                        }
                    }
                }
                
                // Count columns
                String[] parts = line.split(detectedDelimiter, -1);
                if (columnCount == 0) {
                    columnCount = parts.length;
                } else if (parts.length != columnCount) {
                    // Allow some variation in column count
                    if (Math.abs(parts.length - columnCount) > 1) {
                        info.isValid = false;
                        info.errorMessage = "Inconsistent column count (expected " + columnCount + ", found " + parts.length + ")"; 
                        info.preview = preview.toString();
                        return info;
                    }
                }
                
                dataRows++;
            }
            
            // Validate minimum requirements
            if (columnCount < 2) {
                info.isValid = false;
                info.errorMessage = "Minimum 2 columns required (Gene_ID, Annotation_ID)";
                info.preview = preview.toString();
                return info;
            }
            
            if (dataRows == 0) {
                info.isValid = false;
                info.errorMessage = "No data rows found";
                info.preview = preview.toString();
                return info;
            }
            
            // Set successful validation info
            info.isValid = true;
            info.delimiter = detectedDelimiter;
            info.columnCount = columnCount;
            info.dataRows = dataRows;
            info.preview = preview.toString();
            
            if (lineCount == 20) {
                info.preview += "... (file continues)";
            }
            
            return info;
        }
    }
    
    /**
     * Load GO annotations with custom delimiter detection
     */
    private boolean loadGOAnnotationsWithDelimiter(GeneAnnotationData data, File file) throws Exception {
        FileFormatInfo formatInfo = analyzeFileFormat(file);
        if (!formatInfo.isValid) {
            throw new Exception("Invalid file format: " + formatInfo.errorMessage);
        }
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            int imported = 0;
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                
                String[] parts = line.split(java.util.regex.Pattern.quote(formatInfo.delimiter), -1);
                if (parts.length < 2) continue;
                
                String geneId = parts[0].trim();
                String goId = parts[1].trim();
                String term = parts.length > 2 ? parts[2].trim() : "";
                String evidence = parts.length > 3 ? parts[3].trim() : "";
                String category = parts.length > 4 ? parts[4].trim() : "";
                
                if (geneId.isEmpty() || goId.isEmpty()) continue;
                
                GeneAnnotationData.GeneAnnotation annotation = 
                    new GeneAnnotationData.GeneAnnotation(geneId, goId, term, GeneAnnotationData.AnnotationType.GO);
                annotation.setDescription(term);
                annotation.setEvidence(evidence);
                annotation.setAttribute("category", category);
                
                data.addAnnotation(annotation);
                imported++;
            }
            
            logger.info("Imported " + imported + " GO annotations using delimiter: " + 
                       (formatInfo.delimiter.equals("\t") ? "TAB" : formatInfo.delimiter));
            return imported > 0;
        }
    }
    
    /**
     * Load KEGG annotations with custom delimiter detection
     */
    private boolean loadKEGGAnnotationsWithDelimiter(GeneAnnotationData data, File file) throws Exception {
        FileFormatInfo formatInfo = analyzeFileFormat(file);
        if (!formatInfo.isValid) {
            throw new Exception("Invalid file format: " + formatInfo.errorMessage);
        }
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            int imported = 0;
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                
                String[] parts = line.split(java.util.regex.Pattern.quote(formatInfo.delimiter), -1);
                if (parts.length < 2) continue;
                
                String geneId = parts[0].trim();
                String keggId = parts[1].trim();
                String pathway = parts.length > 2 ? parts[2].trim() : "";
                
                if (geneId.isEmpty() || keggId.isEmpty()) continue;
                
                GeneAnnotationData.GeneAnnotation annotation = 
                    new GeneAnnotationData.GeneAnnotation(geneId, keggId, pathway, GeneAnnotationData.AnnotationType.KEGG);
                annotation.setDescription(pathway);
                
                data.addAnnotation(annotation);
                imported++;
            }
            
            logger.info("Imported " + imported + " KEGG annotations using delimiter: " + 
                       (formatInfo.delimiter.equals("\t") ? "TAB" : formatInfo.delimiter));
            return imported > 0;
        }
    }
    
    /**
     * Load custom annotations with delimiter detection
     */
    private boolean loadCustomAnnotationsWithDelimiter(GeneAnnotationData data, File file, 
                                                        GeneAnnotationData.AnnotationType type) throws Exception {
        FileFormatInfo formatInfo = analyzeFileFormat(file);
        if (!formatInfo.isValid) {
            throw new Exception("Invalid file format: " + formatInfo.errorMessage);
        }
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String line;
            int imported = 0;
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                
                String[] parts = line.split(java.util.regex.Pattern.quote(formatInfo.delimiter), -1);
                if (parts.length < 2) continue;
                
                String geneId = parts[0].trim();
                String annotationId = parts[1].trim();
                String description = parts.length > 2 ? parts[2].trim() : "";
                String evidence = parts.length > 3 ? parts[3].trim() : "";
                
                if (geneId.isEmpty() || annotationId.isEmpty()) continue;
                
                GeneAnnotationData.GeneAnnotation annotation = 
                    new GeneAnnotationData.GeneAnnotation(geneId, annotationId, description, type);
                annotation.setDescription(description);
                if (!evidence.isEmpty()) {
                    annotation.setEvidence(evidence);
                }
                
                data.addAnnotation(annotation);
                imported++;
            }
            
            logger.info("Imported " + imported + " " + type.getShortName() + " annotations using delimiter: " + 
                       (formatInfo.delimiter.equals("\t") ? "TAB" : formatInfo.delimiter));
            return imported > 0;
        }
    }
    
    /**
     * File format analysis result
     */
    private static class FileFormatInfo {
        boolean isValid = false;
        String delimiter;
        int columnCount;
        int dataRows;
        String preview;
        String errorMessage;
    }
    
    /**
     * Setup drag and drop support for file input fields
     */
    private void setupDragAndDrop() {
        // Enable drag and drop for annotation file field
        enableFileDragDrop(annotationFileField, "annotation");
        
        // Enable drag and drop for optional file fields
        enableFileDragDrop(oboFileField, "obo");
        enableFileDragDrop(keggRefFileField, "kegg");
    }
    
    /**
     * Enable file drag and drop for a text field
     */
    private void enableFileDragDrop(JTextField textField, String fieldType) {
        textField.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = evt.getTransferable();
                    
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        
                        if (!droppedFiles.isEmpty()) {
                            File droppedFile = droppedFiles.get(0); // Take first file
                            
                            // Validate file type based on field
                            if (validateDroppedFile(droppedFile, fieldType)) {
                                if ("obo".equals(fieldType)) {
                                    if (!GoOboManager.validateOboFile(droppedFile)) {
                                        statusLabel.setText("Dropped OBO file is not readable");
                                        evt.dropComplete(false);
                                        return;
                                    }
                                    selectedGoOboOverride = droppedFile;
                                    GoOboManager.cacheOboForSpecies(targetSpecies, droppedFile, true);
                                    refreshGoOboResolution();
                                    validateAndPreview();
                                } else if ("kegg".equals(fieldType)) {
                                    if (!KeggBackendManager.validateBackendFile(droppedFile)) {
                                        statusLabel.setText("Dropped KEGG backend file is not readable");
                                        evt.dropComplete(false);
                                        return;
                                    }
                                    selectedKeggBackendOverride = droppedFile;
                                    KeggBackendManager.cacheCustomForSpecies(targetSpecies, droppedFile);
                                    if (!"Custom Backend".equals(String.valueOf(keggBackendModeCombo.getSelectedItem()))) {
                                        keggBackendModeCombo.setSelectedItem("Custom Backend");
                                    }
                                    refreshKeggBackendResolution();
                                    validateAndPreview();
                                } else {
                                    textField.setText(droppedFile.getAbsolutePath());
                                }

                                if ("annotation".equals(fieldType)) {
                                    validateAndPreview();
                                }
                                
                                // Update status
                                statusLabel.setText("File dropped: " + droppedFile.getName());
                            } else {
                                statusLabel.setText("Invalid file type for " + fieldType + " field");
                                textField.setBackground(new Color(255, 220, 220)); // Light red
                                
                                // Reset background after 2 seconds
                                Timer timer = new Timer(2000, e -> textField.setBackground(Color.WHITE));
                                timer.setRepeats(false);
                                timer.start();
                            }
                        }
                    }
                    evt.dropComplete(true);
                } catch (Exception e) {
                    logger.warning("Drag and drop error: " + e.getMessage());
                    evt.dropComplete(false);
                }
            }
            
            @Override
            public synchronized void dragOver(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    
                    // Visual feedback - highlight the field
                    textField.setBackground(new Color(220, 255, 220)); // Light green
                } else {
                    dtde.rejectDrag();
                }
            }
            
            @Override
            public synchronized void dragExit(DropTargetEvent dte) {
                // Remove visual feedback
                textField.setBackground(Color.WHITE);
            }
        });
    }
    
    /**
     * Validate dropped file based on field type
     */
    private boolean validateDroppedFile(File file, String fieldType) {
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        
        String fileName = file.getName().toLowerCase();
        
        switch (fieldType) {
            case "annotation":
                // Accept most text-based annotation files
                return fileName.endsWith(".txt") || fileName.endsWith(".tsv") || 
                       fileName.endsWith(".csv") || fileName.endsWith(".gaf") || 
                       fileName.endsWith(".goa") || fileName.endsWith(".ko");
                       
            case "obo":
                // Only accept OBO files
                return fileName.endsWith(".obo");
                
            case "kegg":
                // Accept KEGG reference files
                // TBtools KEGG backend files often have no extension or special names
                return fileName.endsWith(".keg") || fileName.endsWith(".txt") || 
                       fileName.endsWith(".tsv") || fileName.contains("kegg") ||
                       fileName.contains("tbtools") || fileName.contains("backend") ||
                       fileName.contains("ko") || fileName.contains("pathway");
                       
            default:
                return true; // Accept any file for unknown types
        }
    }
    
    /**
     * Check if import was successful
     */
    public boolean isImportSuccessful() {
        return importSuccessful;
    }
    
    /**
     * Get the updated annotation data
     */
    public GeneAnnotationData getAnnotationData() {
        return annotationData;
    }
    
    /**
     * Copy OBO file to species functional annotation directory
     */
    private void copyOboFileToSpeciesDirectory(File oboFile) {
        try {
            File functionalAnnotationDir = targetSpecies.getFunctionalAnnotationDir();
            if (functionalAnnotationDir == null) {
                System.err.println("ERROR: No functional annotation directory available");
                return;
            }
            
            // Create directory if it doesn't exist
            if (!functionalAnnotationDir.exists()) {
                functionalAnnotationDir.mkdirs();
            }
            
            // Copy OBO file to species directory
            File destOboFile = new File(functionalAnnotationDir, "go-basic.obo");
            
            try (FileInputStream fis = new FileInputStream(oboFile);
                 FileOutputStream fos = new FileOutputStream(destOboFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                
                System.out.println("DEBUG: Successfully copied OBO file to: " + destOboFile.getAbsolutePath());
                
            } catch (IOException e) {
                System.err.println("ERROR: Failed to copy OBO file: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("ERROR: Error copying OBO file: " + e.getMessage());
        }
    }
    
    /**
     * Copy KEGG background file to species functional annotation directory
     */
    private void copyKeggBackgroundFileToSpeciesDirectory(File keggBackgroundFile) {
        try {
            File functionalAnnotationDir = targetSpecies.getFunctionalAnnotationDir();
            if (functionalAnnotationDir == null) {
                System.err.println("ERROR: No functional annotation directory available");
                return;
            }
            
            // Create directory if it doesn't exist
            if (!functionalAnnotationDir.exists()) {
                functionalAnnotationDir.mkdirs();
            }
            
            // Copy KEGG background file to species directory, preserving original name
            String originalName = keggBackgroundFile.getName();
            File destKeggFile = new File(functionalAnnotationDir, "kegg-background-" + originalName);
            
            try (FileInputStream fis = new FileInputStream(keggBackgroundFile);
                 FileOutputStream fos = new FileOutputStream(destKeggFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                
                System.out.println("DEBUG: Successfully copied KEGG background file to: " + destKeggFile.getAbsolutePath());
                
            } catch (IOException e) {
                System.err.println("ERROR: Failed to copy KEGG background file: " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("ERROR: Error copying KEGG background file: " + e.getMessage());
        }
    }
}
