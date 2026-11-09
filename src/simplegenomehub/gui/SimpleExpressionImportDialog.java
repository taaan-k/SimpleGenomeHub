/*
 * Simplified Expression Data Import Dialog
 */
package simplegenomehub.gui;


import simplegenomehub.model.*;
import simplegenomehub.model.ExpressionMatrix.DataType;
import simplegenomehub.util.identification.SpeciesSequenceIndex;

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
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * Simplified dialog for importing expression data
 * Auto-detects file format (tab/csv) and only requires basic information
 * 
 * @author SimpleGenomeHub
 */
public class SimpleExpressionImportDialog extends JDialog {
    private boolean closed;

    @Override public void dispose() { closed = true; super.dispose(); }

    
    private static final Logger logger = Logger.getLogger(SimpleExpressionImportDialog.class.getName());
    
    private SpeciesInfo targetSpecies;
    private ExpressionExperiment currentExperiment;
    private ExpressionMatrix previewMatrix;
    private ExpressionIdCompatibilityResult previewIdCompatibility;
    private volatile boolean importSuccessful = false;
    private String importFailureMessage;
    
    // UI Components
    private JTextField filePathField;
    private JButton browseButton;
    private JComboBox<DataType> dataTypeCombo;
    private JTextArea previewArea;
    private JLabel fileInfoLabel;
    
    // Simplified metadata
    private JTextField experimentNameField;
    private JTextArea notesArea;
    
    // Progress
    private JProgressBar progressBar;
    private JTextArea summaryArea;
    private JPanel progressPanel;
    
    // Action buttons
    private JButton importButton;
    private JButton cancelButton;
    
    /**
     * Constructor
     */
    public SimpleExpressionImportDialog(Window parent, SpeciesInfo species) {
        super(parent, "Import Expression Data", ModalityType.MODELESS);
        this.targetSpecies = species;
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        setMinimumSize(new Dimension(300, 500));
        setSize(450, 700);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // File selection
        filePathField = new JTextField(18);
        browseButton = new JButton("Browse...");
        dataTypeCombo = new JComboBox<>(DataType.values());
        dataTypeCombo.setSelectedItem(DataType.TPM); // Default to TPM
        SimpleGenomeHubUi.setComboBoxDisplayWidth(dataTypeCombo, 220);
        
        previewArea = new JTextArea(12, 28);
        previewArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        previewArea.setEditable(false);
        previewArea.setBackground(new Color(248, 248, 248));
        
        fileInfoLabel = new JLabel();
        setFileInfoMessage("No file selected");
        
        // Simplified metadata
        experimentNameField = new JTextField(18);
        notesArea = new JTextArea(4, 28);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        
        // Generate default experiment name
        String defaultName = "Expression_" + targetSpecies.getSpeciesName().replace(" ", "_") + 
                           "_" + System.currentTimeMillis();
        experimentNameField.setText(defaultName);
        
        // Progress components
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        
        summaryArea = new JTextArea(5, 28);
        summaryArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        summaryArea.setEditable(false);
        summaryArea.setLineWrap(true);
        summaryArea.setWrapStyleWord(true);
        summaryArea.setVisible(false);
        
        // Action buttons
        importButton = new JButton("Import");
        cancelButton = new JButton("Cancel");
        importButton.setEnabled(false);
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // Main content panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top: File selection
        JPanel filePanel = createFileSelectionPanel();
        mainPanel.add(filePanel, BorderLayout.NORTH);
        
        // Center: Preview
        JPanel previewPanel = createPreviewPanel();
        mainPanel.add(previewPanel, BorderLayout.CENTER);
        
        // Bottom: Metadata
        JPanel metadataPanel = createMetadataPanel();
        mainPanel.add(metadataPanel, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(importButton);

        progressPanel = createProgressPanel();

        JPanel southPanel = new JPanel(new BorderLayout(0, 8));
        southPanel.add(progressPanel, BorderLayout.CENTER);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);
        add(southPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create file selection panel
     */
    private JPanel createFileSelectionPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("Expression Matrix File"));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0;
        panel.add(new JLabel("File"), gbc);
        
        gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(filePathField, gbc);
        
        gbc.gridx = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.EAST;
        panel.add(browseButton, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Data Type"), gbc);
        
        gbc.gridy = 3;
        panel.add(dataTypeCombo, gbc);
        
        gbc.gridy = 4;
        panel.add(fileInfoLabel, gbc);
        
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
     * Create metadata panel
     */
    private JPanel createMetadataPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("Experiment Information"));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        panel.add(new JLabel("Experiment Name"), gbc);
        
        gbc.gridy = 1;
        panel.add(experimentNameField, gbc);
        
        gbc.gridy = 2; gbc.weighty = 0; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Notes"), gbc);
        
        gbc.gridy = 3; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 1.0;
        panel.add(new JScrollPane(notesArea), gbc);
        
        return panel;
    }
    
    /**
     * Create progress panel
     */
    private JPanel createProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        panel.setVisible(false);
        
        panel.add(progressBar, BorderLayout.NORTH);
        
        JScrollPane summaryScrollPane = new JScrollPane(summaryArea);
        summaryScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(summaryScrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        browseButton.addActionListener(e -> browseForFile());
        filePathField.addActionListener(e -> loadFilePreview());
        dataTypeCombo.addActionListener(e -> updateFileInfo());
        
        importButton.addActionListener(e -> performImport());
        cancelButton.addActionListener(e -> dispose());
        
        // Add drag and drop support for file path field
        setupDragAndDrop();
    }
    
    /**
     * Browse for expression matrix file
     */
    private void browseForFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            "Expression Matrix Files", "tsv", "csv", "txt", "tab"));
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            filePathField.setText(selectedFile.getAbsolutePath());
            loadFilePreview();
        }
    }
    
    /**
     * Load and preview selected file with automatic format detection
     */
    private void loadFilePreview() {
        String filePath = filePathField.getText().trim();
        if (filePath.isEmpty()) {
            return;
        }
        
        File file = new File(filePath);
        if (!file.exists()) {
            previewMatrix = null;
            previewIdCompatibility = null;
            setFileInfoMessage("File not found");
            previewArea.setText("");
            importButton.setEnabled(false);
            return;
        }
        
        try {
            // Auto-detect file format (tab vs csv)
            String delimiter = detectDelimiter(file);
            
            // Create temporary matrix for validation
            String matrixId = "preview_" + System.currentTimeMillis();
            DataType selectedType = (DataType) dataTypeCombo.getSelectedItem();
            previewMatrix = new ExpressionMatrix(matrixId, "Preview", selectedType);
            
            if (previewMatrix.loadFromFile(file)) {
                previewIdCompatibility = validateExpressionIdsAgainstSpecies(previewMatrix);

                // Update file info
                String formatInfo = delimiter.equals("\t") ? "Tab-delimited" : "Comma-separated";
                setFileInfoMessage(String.format("%s\n%s\n%.2f MB, %d genes, %d samples\nID matches: %d/%d",
                    formatInfo, file.getName(), file.length() / 1024.0 / 1024.0,
                    previewMatrix.getGeneCount(), previewMatrix.getSampleCount(),
                    previewIdCompatibility.matchedCount, previewMatrix.getGeneCount()));
                
                // Show preview
                showPreview();
                
                // Enable import only when the file is valid and at least one ID matches the species.
                importButton.setEnabled(previewMatrix.isValid() && previewIdCompatibility.matchedCount > 0);

                if (previewIdCompatibility.matchedCount == 0) {
                    JOptionPane.showMessageDialog(this,
                        "No IDs in the expression file match the current genome.\nImport has been disabled.",
                        "No Matching IDs", JOptionPane.ERROR_MESSAGE);
                } else if (previewIdCompatibility.hasUnmatchedIds()) {
                    JOptionPane.showMessageDialog(this,
                        buildUnmatchedIdWarningMessage(previewIdCompatibility),
                        "Unmatched IDs Found", JOptionPane.WARNING_MESSAGE);
                }
                
                if (!previewMatrix.isValid()) {
                    List<String> errors = previewMatrix.getValidationErrors();
                    JOptionPane.showMessageDialog(this, 
                        "File validation issues:\n" + String.join("\n", errors.subList(0, Math.min(5, errors.size()))),
                        "Validation Warning", JOptionPane.WARNING_MESSAGE);
                }
                
            } else {
                previewIdCompatibility = null;
                setFileInfoMessage("Failed to load file.\nPlease check the file format.");
                previewArea.setText("Error: Unable to parse file. Please ensure it's a tab-delimited or CSV file with headers.");
                importButton.setEnabled(false);
            }
            
        } catch (Exception e) {
            previewMatrix = null;
            previewIdCompatibility = null;
            logger.warning("Error loading file preview: " + e.getMessage());
            setFileInfoMessage("Error loading file");
            previewArea.setText("Error: " + e.getMessage());
            importButton.setEnabled(false);
        }
    }
    
    /**
     * Auto-detect file delimiter (tab vs comma)
     */
    private String detectDelimiter(File file) {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
            String firstLine = reader.readLine();
            if (firstLine != null) {
                int tabCount = firstLine.length() - firstLine.replace("\t", "").length();
                int commaCount = firstLine.length() - firstLine.replace(",", "").length();
                
                if (tabCount > commaCount) {
                    return "\t";
                } else if (commaCount > 0) {
                    return ",";
                }
            }
        } catch (Exception e) {
            logger.warning("Error detecting delimiter: " + e.getMessage());
        }
        
        return "\t"; // Default to tab
    }
    
    /**
     * Show file preview
     */
    private void showPreview() {
        StringBuilder preview = new StringBuilder();
        String[] geneIds = previewMatrix.getGeneIds();
        String[] sampleNames = previewMatrix.getSampleNames();
        
        // Header
        preview.append("Gene_ID");
        for (int i = 0; i < Math.min(6, sampleNames.length); i++) {
            preview.append("\t").append(sampleNames[i]);
        }
        if (sampleNames.length > 6) {
            preview.append("\t... (").append(sampleNames.length - 6).append(" more)");
        }
        preview.append("\n");
        
        // First few rows
        for (int i = 0; i < Math.min(15, geneIds.length); i++) {
            preview.append(geneIds[i]);
            Map<String, Double> geneExpression = previewMatrix.getGeneExpression(geneIds[i]);
            for (int j = 0; j < Math.min(6, sampleNames.length); j++) {
                Double value = geneExpression.get(sampleNames[j]);
                preview.append("\t").append(value != null ? String.format("%.3f", value) : "0.000");
            }
            if (sampleNames.length > 6) {
                preview.append("\t...");
            }
            preview.append("\n");
        }
        
        if (geneIds.length > 15) {
            preview.append("... (").append(geneIds.length - 15).append(" more genes)\n");
        }
        
        previewArea.setText(preview.toString());
    }
    
    /**
     * Update file info when data type changes
     */
    private void updateFileInfo() {
        if (previewMatrix != null && previewMatrix.isValid()) {
            File file = new File(filePathField.getText().trim());
            if (file.exists()) {
                String delimiter = detectDelimiter(file);
                String formatInfo = delimiter.equals("\t") ? "Tab-delimited" : "Comma-separated";
                DataType selectedType = (DataType) dataTypeCombo.getSelectedItem();
                setFileInfoMessage(String.format("%s\n%s data\n%s\n%.2f MB, %d genes, %d samples",
                    formatInfo,
                    selectedType.getShortName(),
                    file.getName(),
                    file.length() / 1024.0 / 1024.0,
                    previewMatrix.getGeneCount(),
                    previewMatrix.getSampleCount()));
            }
        }
    }
    
    /**
     * Perform the import operation
     */
    private void performImport() {
        if (previewMatrix == null || !previewMatrix.isValid()) {
            JOptionPane.showMessageDialog(this, 
                "Please select a valid expression matrix file first.",
                "No Valid File", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (previewIdCompatibility != null && previewIdCompatibility.matchedCount == 0) {
            JOptionPane.showMessageDialog(this,
                "No IDs in the expression file match the current genome.\nImport has been disabled.",
                "No Matching IDs", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        String experimentName = experimentNameField.getText().trim();
        if (experimentName.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please provide an experiment name.",
                "Missing Information", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Show progress components
        progressPanel.setVisible(true);
        progressBar.setVisible(true);
        summaryArea.setVisible(true);
        importButton.setEnabled(false);
        revalidate();
        repaint();
        
        final String notesSnapshot = notesArea.getText();
        final DataType selectedDataType = (DataType) dataTypeCombo.getSelectedItem();
        final File selectedSourceFile = new File(filePathField.getText());

        // Perform import in background
        SwingWorker<Boolean, String> importWorker = new SwingWorker<Boolean, String>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                importFailureMessage = null;
                publish("Creating experiment...");
                setProgress(10);
                
                // Create simplified experiment
                String experimentId = "EXP_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
                currentExperiment = new ExpressionExperiment(experimentId, experimentName);
                currentExperiment.setDescription(notesSnapshot);
                currentExperiment.setExperimentType(ExpressionExperiment.ExperimentType.BULK_RNA);
                
                setProgress(30);
                
                // Create final expression matrix
                publish("Processing expression matrix...");
                String finalMatrixId = experimentId + "_matrix";
                DataType dataType = selectedDataType;
                ExpressionMatrix finalMatrix = new ExpressionMatrix(finalMatrixId, experimentName, dataType);
                
                File sourceFile = selectedSourceFile;
                if (!finalMatrix.loadFromFile(sourceFile)) {
                    importFailureMessage = "Failed to import expression data. Please check the file format.";
                    return false;
                }

                ExpressionIdCompatibilityResult finalCompatibility = validateExpressionIdsAgainstSpecies(finalMatrix);
                if (finalCompatibility.matchedCount == 0) {
                    importFailureMessage = "No IDs in the expression file match the current genome.\nImport was cancelled.";
                    return false;
                }
                
                setProgress(60);
                
                // Add matrix to experiment
                currentExperiment.setStoredDataType(dataType);
                currentExperiment.addMatrix(finalMatrix);
                
                // Save to species directory
                publish("Saving experiment data...");
                File expressionDir = targetSpecies.getExpressionDir();
                if (!expressionDir.exists()) {
                    expressionDir.mkdirs();
                }
                
                File metadataFile = new File(expressionDir, experimentId + ".metadata.json");
                File targetMatrixFile = new File(expressionDir, experimentId + ".matrix.tsv");
                java.nio.file.Path stagedMetadata = simplegenomehub.util.fileio.FileSetTransaction.stageSibling(metadataFile.toPath());
                java.nio.file.Path stagedMatrix = null;
                try {
                    if (!currentExperiment.saveMetadata(stagedMetadata.toFile()))
                        throw new java.io.IOException("Failed to save expression metadata: " + metadataFile);
                    setProgress(80);
                    stagedMatrix = simplegenomehub.util.fileio.FileSetTransaction.stageSibling(targetMatrixFile.toPath());
                    if (!finalMatrix.saveToFile(stagedMatrix.toFile()))
                        throw new java.io.IOException("Failed to save expression matrix: " + targetMatrixFile);
                    java.util.Map<java.nio.file.Path, java.nio.file.Path> publication = new java.util.LinkedHashMap<>();
                    publication.put(stagedMetadata, metadataFile.toPath());
                    publication.put(stagedMatrix, targetMatrixFile.toPath());
                    simplegenomehub.util.fileio.FileSetTransaction.commit(publication, java.util.Collections.emptyList(), publication.values());
                } finally {
                    simplegenomehub.util.fileio.FileSetTransaction.discardStage(stagedMetadata);
                    if (stagedMatrix != null) simplegenomehub.util.fileio.FileSetTransaction.discardStage(stagedMatrix);
                }
                currentExperiment.setMetadataFile(metadataFile);
                targetSpecies.addExpressionExperiment(currentExperiment);
                ExpressionExperimentLoader.invalidate(targetSpecies);
                
                setProgress(100);
                publish("Import completed successfully!");
                
                // Show summary
                StringBuilder summary = new StringBuilder();
                summary.append("IMPORT SUMMARY:\n");
                summary.append("- Experiment: ").append(experimentName).append("\n");
                summary.append("- Data Type: ").append(dataType.getShortName()).append("\n");
                summary.append("- Genes: ").append(finalMatrix.getGeneCount()).append("\n");
                summary.append("- Samples: ").append(finalMatrix.getSampleCount()).append("\n");
                summary.append("- File: ").append(sourceFile.getName()).append("\n");
                summary.append("- Saved to: ").append(expressionDir.getAbsolutePath()).append("\n");
                
                SwingUtilities.invokeLater(() -> { if ((!closed)) summaryArea.setText(summary.toString()); });
                
                importSuccessful = true;
                return true;
            }
            
            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    progressBar.setString(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    if (!(!closed)) return;
                    try {
                        importSuccessful = get();
                        if (importSuccessful) {
                            JOptionPane.showMessageDialog(SimpleExpressionImportDialog.this,
                                "Expression data imported successfully!\n" +
                                "You can now use 'Explore Expression Data' to view the data.",
                                "Import Complete", JOptionPane.INFORMATION_MESSAGE);
                            dispose();
                        } else {
                            JOptionPane.showMessageDialog(SimpleExpressionImportDialog.this,
                                importFailureMessage != null ? importFailureMessage : "Failed to import expression data. Please check the file format.",
                                "Import Failed", JOptionPane.ERROR_MESSAGE);
                            progressBar.setString("Import failed");

                        }
                    } catch (Exception e) {
                        logger.warning("Import error: " + e.getMessage());
                        JOptionPane.showMessageDialog(SimpleExpressionImportDialog.this,
                            "Import error: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()),
                            "Import Error", JOptionPane.ERROR_MESSAGE);
                        progressBar.setString("Import error");

                    }
                } finally {
                    progressBar.setVisible(false);
                    progressBar.setIndeterminate(false);
                    importButton.setEnabled((!closed));
                }
            }
        };
        
        importWorker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) progressBar.setValue((Integer) event.getNewValue());
        });
        importWorker.execute();
    }
    
    /**
     * Check if import was successful
     */
    public boolean isImportSuccessful() {
        return importSuccessful;
    }
    
    /**
     * Get the imported experiment
     */
    public ExpressionExperiment getImportedExperiment() {
        return currentExperiment;
    }

    private void setFileInfoMessage(String message) {
        fileInfoLabel.setText("<html><div style='width:220px;'>" +
            escapeHtml(message).replace("\n", "<br>") +
            "</div></html>");
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private ExpressionIdCompatibilityResult validateExpressionIdsAgainstSpecies(ExpressionMatrix matrix) {
        ExpressionIdCompatibilityResult result = new ExpressionIdCompatibilityResult();
        if (matrix == null || matrix.getGeneIds() == null || matrix.getGeneIds().length == 0) {
            return result;
        }

        SpeciesSequenceIndex sequenceIndex = new SpeciesSequenceIndex(targetSpecies);
        Set<String> knownSequenceIds = sequenceIndex.getAllSequenceIds();
        Set<String> knownGeneIds = sequenceIndex.getAllGeneIds();
        Set<String> normalizedKnownIds = new HashSet<>();

        for (String knownId : knownSequenceIds) {
            normalizedKnownIds.add(normalizeExpressionId(knownId));
        }
        for (String knownId : knownGeneIds) {
            normalizedKnownIds.add(normalizeExpressionId(knownId));
        }

        LinkedHashSet<String> unmatchedPreviewIds = new LinkedHashSet<>();
        for (String rawId : matrix.getGeneIds()) {
            String geneId = rawId == null ? "" : rawId.trim();
            if (geneId.isEmpty()) {
                result.unmatchedCount++;
                if (unmatchedPreviewIds.size() < 5) {
                    unmatchedPreviewIds.add("(empty ID)");
                }
                continue;
            }

            boolean matched = knownSequenceIds.contains(geneId)
                || knownGeneIds.contains(geneId)
                || normalizedKnownIds.contains(normalizeExpressionId(geneId));

            if (matched) {
                result.matchedCount++;
            } else {
                result.unmatchedCount++;
                if (unmatchedPreviewIds.size() < 5) {
                    unmatchedPreviewIds.add(geneId);
                }
            }
        }

        result.firstUnmatchedIds.addAll(unmatchedPreviewIds);
        return result;
    }

    private String normalizeExpressionId(String id) {
        if (id == null) {
            return "";
        }

        String normalized = id.trim().toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\.T\\d+$", "");
        normalized = normalized.replaceAll("\\.\\d+$", "");
        normalized = normalized.replaceAll("_T\\d+$", "");
        normalized = normalized.replaceAll("_TRANSCRIPT_\\d+$", "");
        return normalized;
    }

    private String buildUnmatchedIdWarningMessage(ExpressionIdCompatibilityResult compatibility) {
        StringBuilder message = new StringBuilder();
        message.append("Some IDs in the expression file do not match the current genome.\n");
        message.append("First unmatched IDs:\n");
        for (String unmatchedId : compatibility.firstUnmatchedIds) {
            message.append("- ").append(unmatchedId).append("\n");
        }
        return message.toString().trim();
    }

    private static final class ExpressionIdCompatibilityResult {
        private int matchedCount;
        private int unmatchedCount;
        private final List<String> firstUnmatchedIds = new ArrayList<>(5);

        private boolean hasUnmatchedIds() {
            return unmatchedCount > 0;
        }
    }
    
    /**
     * Setup drag and drop support for file input
     */
    private void setupDragAndDrop() {
        filePathField.setDropTarget(new DropTarget() {
            @Override
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable transferable = evt.getTransferable();
                    
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        List<File> droppedFiles = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        
                        if (!droppedFiles.isEmpty()) {
                            File droppedFile = droppedFiles.get(0);
                            if (droppedFile.isFile()) {
                                filePathField.setText(droppedFile.getAbsolutePath());
                                loadFilePreview();
                                evt.getDropTargetContext().dropComplete(true);
                                return;
                            }
                        }
                    }
                    
                    evt.getDropTargetContext().dropComplete(false);
                } catch (Exception e) {
                    logger.warning("Error handling file drop: " + e.getMessage());
                    evt.getDropTargetContext().dropComplete(false);
                }
            }
            
            @Override
            public synchronized void dragOver(DropTargetDragEvent evt) {
                if (evt.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    evt.acceptDrag(DnDConstants.ACTION_COPY);
                } else {
                    evt.rejectDrag();
                }
            }
        });
        
        // Visual feedback for drag and drop
        filePathField.setToolTipText("You can drag and drop a file here, or click Browse to select a file");
    }
}
