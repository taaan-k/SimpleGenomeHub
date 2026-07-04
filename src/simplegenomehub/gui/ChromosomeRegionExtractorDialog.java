package simplegenomehub.gui;

import biocjava.bioIO.FastX.Fasta;
import biocjava.bioIO.FastX.FastaIndex.MakeFastaIndex;
import biocjava.bioIO.FastX.FastaIndex.SeqExtracterAccordingFAindex;
import simplegenomehub.model.SpeciesInfo;
import toolsKit.GUItools.DragDropFileEnterListener;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

/**
 * Dialog for extracting chromosome region sequences using TBtools SeqExtracterAccordingFAindex
 * Supports two input formats:
 * 1. ChrID    StartPos    EndPos
 * 2. GeneID    ChrID    StartPos    EndPos
 */
public class ChromosomeRegionExtractorDialog extends JDialog {

    private static final String REGION_SET_DEFAULT_OPTION = "Load Region Set";
    private static final String REGION_SET_EMPTY_OPTION = "Load Region Set (No Sets)";

    private SpeciesInfo speciesInfo;
    private JTextArea regionListTextArea;
    private JTextArea outputTextArea;
    private JTextField outputFileField;
    private JButton extractButton;
    private JButton browseOutputButton;
    private JButton loadRegionFileButton;
    private JComboBox<String> regionSetComboBox;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JCheckBox includeGeneNameCheckBox;
    private JCheckBox reverseComplementCheckBox;
    private java.util.List<File> availableRegionSetFiles;
    
    public ChromosomeRegionExtractorDialog(Frame parent, SpeciesInfo speciesInfo) {
        super(parent, "Chromosome Region Sequence Extractor", Dialog.ModalityType.MODELESS);
        this.speciesInfo = speciesInfo;
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 700);
        setLocationRelativeTo(parent);
    }
    
    private void initializeComponents() {
        // Region list input area
        regionListTextArea = new JTextArea(15, 50);
        regionListTextArea.setFont(SimpleGenomeHubStyle.FONT_COURIER_NEW_PLAIN_12);
        regionListTextArea.setLineWrap(false);  // Disable line wrapping to preserve formatting
        regionListTextArea.setWrapStyleWord(false);  // Don't wrap at word boundaries
        regionListTextArea.setTabSize(8);  // Larger tab size for better column separation
        
        // Use explicit spaces instead of relying on tabs for the example text
        regionListTextArea.setText("# Enter chromosome regions (one per line)\n" +
                "# Format 1 (3 columns): ChrID        StartPos    EndPos\n" +
                "# Example: Chr_1        100000      102000\n" +
                "# \n" +
                "# Format 2 (4 columns): GeneID       ChrID       StartPos    EndPos\n" +
                "# Example: FinalGeneID  Chr_1        100000      120000\n" +
                "# \n" +
                "# Lines starting with # are ignored\n" +
                "# Paste your data below (spaces or tabs as separators):\n" +
                "\n");
        
        // File selection components
        outputTextArea = new JTextArea(10, 50);
        outputTextArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        outputTextArea.setEditable(false);
        outputTextArea.setLineWrap(false);
        outputTextArea.setWrapStyleWord(false);
        outputFileField = new JTextField(30);
        browseOutputButton = new JButton("Browse...");
        loadRegionFileButton = new JButton("Load Region File");
        regionSetComboBox = new JComboBox<>();
        regionSetComboBox.setToolTipText("Select a Region Set to load its content into the input box");
        availableRegionSetFiles = new ArrayList<>();
        
        // Options
        includeGeneNameCheckBox = new JCheckBox("Include custom gene names (4-column format)", false);
        reverseComplementCheckBox = new JCheckBox("Auto reverse complement for negative strand", true);
        
        // Action buttons
        extractButton = new JButton("Extract Sequences");
        extractButton.setBackground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON);
        extractButton.setForeground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_TEXT);
        extractButton.setBorder(BorderFactory.createLineBorder(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_BORDER, 1));
        extractButton.setFocusPainted(false);
        extractButton.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        extractButton.setPreferredSize(new Dimension(160, 36));
        
        // Status components
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        statusLabel = new JLabel("Ready to extract chromosome region sequences");
        
        // Setup drag and drop for region list
        new DropTarget(regionListTextArea, new DragDropFileEnterListener(regionListTextArea));

        regionSetComboBox.setPreferredSize(new Dimension(220, 28));
        regionSetComboBox.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_11);
        refreshRegionSetOptions();
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top panel - species info
        JPanel speciesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        speciesPanel.setBorder(new TitledBorder("Species Information"));
        speciesPanel.add(new JLabel("Species: " + speciesInfo.getSpeciesName() + " " + speciesInfo.getVersion()));
        if (speciesInfo.getGenomeFile() != null) {
            speciesPanel.add(new JLabel("  |  Genome: " + speciesInfo.getGenomeFile().getName()));
        }
        mainPanel.add(speciesPanel, BorderLayout.NORTH);
        
        // Center panel - region input
        JPanel centerPanel = new JPanel(new BorderLayout());
        
        // Region list panel
        JPanel regionPanel = new JPanel(new BorderLayout());
        regionPanel.setBorder(new TitledBorder("Chromosome Regions Input"));
        
        JPanel regionButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        regionButtonPanel.add(loadRegionFileButton);
        
        // Add sample data button
        JButton sampleDataButton = new JButton("Sample Data");
        sampleDataButton.setToolTipText("Insert sample chromosome region data");
        sampleDataButton.addActionListener(e -> insertSampleData());
        regionButtonPanel.add(sampleDataButton);

        regionButtonPanel.add(regionSetComboBox);
        
        regionButtonPanel.add(new JLabel("  |  Drag & drop region files here"));
        regionPanel.add(regionButtonPanel, BorderLayout.NORTH);
        
        JScrollPane regionScrollPane = new JScrollPane(regionListTextArea);
        regionScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        regionScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        regionPanel.add(regionScrollPane, BorderLayout.CENTER);

        // Add format cleanup button after layout is complete
        SwingUtilities.invokeLater(() -> addFormatCleanupButton());

        // Options panel
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 4));
        optionsPanel.setBorder(new TitledBorder("Extraction Options"));
        optionsPanel.setOpaque(false);
        optionsPanel.add(includeGeneNameCheckBox);
        optionsPanel.add(reverseComplementCheckBox);
        centerPanel.add(regionPanel, BorderLayout.CENTER);
        centerPanel.add(optionsPanel, BorderLayout.SOUTH);

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 10));
        JPanel outputPreviewPanel = new JPanel(new BorderLayout());
        outputPreviewPanel.setBorder(new TitledBorder("Output"));
        JScrollPane outputScrollPane = new JScrollPane(outputTextArea);
        outputScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        outputScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        outputPreviewPanel.add(outputScrollPane, BorderLayout.CENTER);

        JSplitPane contentSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerPanel, outputPreviewPanel);
        contentSplitPane.setResizeWeight(0.72);
        contentSplitPane.setDividerLocation(430);
        SimpleGenomeHubUi.styleSplitPane(contentSplitPane);
        mainPanel.add(contentSplitPane, BorderLayout.CENTER);

        JPanel outputPanel = new JPanel(new BorderLayout());
        outputPanel.setBorder(new TitledBorder("Output File"));
        JPanel outputFieldPanel = new JPanel(new BorderLayout());
        outputFieldPanel.add(outputFileField, BorderLayout.CENTER);
        JPanel outputActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        outputActionPanel.add(browseOutputButton);
        outputActionPanel.add(extractButton);
        outputFieldPanel.add(outputActionPanel, BorderLayout.EAST);
        outputPanel.add(outputFieldPanel, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.add(progressBar, BorderLayout.NORTH);
        statusPanel.add(statusLabel, BorderLayout.SOUTH);
        bottomPanel.add(outputPanel, BorderLayout.NORTH);
        bottomPanel.add(statusPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }
    
    private void setupEventHandlers() {
        loadRegionFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadRegionFile();
            }
        });
        
        browseOutputButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectOutputFile();
            }
        });

        regionSetComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadSelectedRegionSetIntoInput();
            }
        });
        
        extractButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performExtraction();
            }
        });
    }
    
    private void loadRegionFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Region List File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || 
                       f.getName().toLowerCase().endsWith(".txt") ||
                       f.getName().toLowerCase().endsWith(".tsv") ||
                       f.getName().toLowerCase().endsWith(".csv");
            }
            
            @Override
            public String getDescription() {
                return "Text files (*.txt, *.tsv, *.csv)";
            }
        });
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                StringBuilder content = new StringBuilder();
                BufferedReader reader = new BufferedReader(new FileReader(selectedFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                regionListTextArea.setText(content.toString());
                statusLabel.setText("Loaded region file: " + selectedFile.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, 
                    "Error loading file: " + ex.getMessage(),
                    "File Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void refreshRegionSetOptions() {
        availableRegionSetFiles = loadRegionSetFiles();

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(availableRegionSetFiles.isEmpty() ? REGION_SET_EMPTY_OPTION : REGION_SET_DEFAULT_OPTION);
        for (File regionSetFile : availableRegionSetFiles) {
            model.addElement(GeneSetFileSupport.extractDisplayName(regionSetFile));
        }

        regionSetComboBox.setModel(model);
        regionSetComboBox.setSelectedIndex(0);
        regionSetComboBox.setEnabled(!availableRegionSetFiles.isEmpty());
    }

    private java.util.List<File> loadRegionSetFiles() {
        java.util.List<File> files = new ArrayList<>();
        File geneSetDir = speciesInfo.getGeneSetDir();
        if (geneSetDir == null || !geneSetDir.isDirectory()) {
            return files;
        }

        File[] regionSetFiles = geneSetDir.listFiles((dir, name) ->
            GeneSetFileSupport.detectSetKind(name) == GeneSetFileSupport.SetKind.REGION);
        if (regionSetFiles == null || regionSetFiles.length == 0) {
            return files;
        }

        java.util.Arrays.sort(regionSetFiles, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File regionSetFile : regionSetFiles) {
            files.add(regionSetFile);
        }
        return files;
    }

    private void loadSelectedRegionSetIntoInput() {
        int selectedIndex = regionSetComboBox.getSelectedIndex();
        if (selectedIndex <= 0) {
            return;
        }

        int fileIndex = selectedIndex - 1;
        if (fileIndex < 0 || fileIndex >= availableRegionSetFiles.size()) {
            regionSetComboBox.setSelectedIndex(0);
            return;
        }

        File selectedRegionSetFile = availableRegionSetFiles.get(fileIndex);
        try {
            String content = GeneSetFileSupport.readGeneSetContent(selectedRegionSetFile).trim();
            if (content.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "The selected Region Set does not contain any content.",
                    "Empty Region Set",
                    JOptionPane.WARNING_MESSAGE);
                regionSetComboBox.setSelectedIndex(0);
                return;
            }

            regionListTextArea.setText(content + "\n");
            regionListTextArea.setCaretPosition(0);
            regionListTextArea.requestFocusInWindow();
            statusLabel.setText("Loaded Region Set: " + GeneSetFileSupport.extractDisplayName(selectedRegionSetFile));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Failed to load Region Set:\n" + e.getMessage(),
                "Region Set Load Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void selectOutputFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Output FASTA File");
        fileChooser.setSelectedFile(new File("extracted_sequences.fasta"));
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || 
                       f.getName().toLowerCase().endsWith(".fasta") ||
                       f.getName().toLowerCase().endsWith(".fa") ||
                       f.getName().toLowerCase().endsWith(".fas");
            }
            
            @Override
            public String getDescription() {
                return "FASTA files (*.fasta, *.fa, *.fas)";
            }
        });
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            outputFileField.setText(fileChooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    private void performExtraction() {
        String regionText = regionListTextArea.getText().trim();
        String outputPath = outputFileField.getText().trim();
        
        // Validate inputs
        if (regionText.isEmpty() || regionText.replaceAll("#[^\n]*\n", "").trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please enter chromosome regions to extract.",
                "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (speciesInfo.getGenomeFile() == null || !speciesInfo.getGenomeFile().exists()) {
            JOptionPane.showMessageDialog(this, 
                "Genome file not found for this species.",
                "Genome Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        outputTextArea.setText("");
        
        // Disable controls during extraction
        extractButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("Extracting sequences...");
        statusLabel.setText("Processing chromosome regions...");
        
        // Perform extraction in background thread
        SwingWorker<String, String> worker = new SwingWorker<String, String>() {
            @Override
            protected String doInBackground() throws Exception {
                publish("Initializing sequence extractor...");
                
                // Initialize TBtools sequence extractor
                SeqExtracterAccordingFAindex seaf = new SeqExtracterAccordingFAindex();
                File genomeFile = speciesInfo.getGenomeFile();
                
                try {
                    // Check if index exists, build if necessary
                    String formattedGenomePath = genomeFile.getAbsolutePath() + ".TBtools.fa";
                    String indexPath = genomeFile.getAbsolutePath() + ".TBtools.fa.fai";

                    if (!new File(indexPath).exists()) {
                        publish("Building genome index...");
                        MakeFastaIndex.FormatBigFastaFileAndMakeIndex(genomeFile, 60, new File(formattedGenomePath));
                    }

                    publish("Loading genome index...");
                    seaf.initialize(formattedGenomePath, indexPath);
                    seaf.setZeroStart(false); // Use 1-based positioning

                    // Parse region text and extract sequences
                    StringBuilder extractedContent = new StringBuilder();
                    String[] lines = regionText.split("\n");

                    int processedCount = 0;
                    int totalCount = 0;
                    int extractedSequenceCount = 0;

                    // Count non-comment lines
                    for (String line : lines) {
                        if (!line.trim().isEmpty() && !line.trim().startsWith("#")) {
                            totalCount++;
                        }
                    }

                    publish("Extracting " + totalCount + " chromosome regions...");

                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }

                        processedCount++;
                        publish("Processing region " + processedCount + "/" + totalCount + ": " + line);

                        try {
                            String[] parts = line.split("\\s+");
                            Fasta extractedSeq = null;
                            String fallbackHeader = null;

                            if (parts.length == 3) {
                                // Format: ChrID StartPos EndPos
                                String chrId = parts[0];
                                long startPos = Long.parseLong(parts[1]);
                                long endPos = Long.parseLong(parts[2]);
                                fallbackHeader = chrId + "_" + startPos + "_" + endPos;

                                extractedSeq = seaf.getOneFastaSubSeq(chrId, startPos, endPos);
                                if (extractedSeq == null) {
                                    System.err.println("Warning: Could not extract sequence for " + chrId + ":" + startPos + "-" + endPos);
                                }
                            } else if (parts.length == 4) {
                                // Format: GeneID ChrID StartPos EndPos
                                String geneId = parts[0];
                                String chrId = parts[1];
                                long startPos = Long.parseLong(parts[2]);
                                long endPos = Long.parseLong(parts[3]);
                                fallbackHeader = geneId + "_" + chrId + "_" + startPos + "_" + endPos;

                                extractedSeq = seaf.getOneFastaSubSeq(geneId, chrId, startPos, endPos);
                                if (extractedSeq == null) {
                                    System.err.println("Warning: Could not extract sequence for " + geneId + " (" + chrId + ":" + startPos + "-" + endPos + ")");
                                }
                            } else {
                                System.err.println("Warning: Invalid format for line: " + line);
                            }

                            if (extractedSeq != null) {
                                String fastaText = GeneSetBlastLauncher.buildFastaEntry(
                                    extractedSeq.getId(),
                                    fallbackHeader != null ? fallbackHeader : extractedSeq.getId(),
                                    extractedSeq.getSeq()
                                );
                                if (!fastaText.isEmpty()) {
                                    if (extractedContent.length() > 0) {
                                        extractedContent.append(System.lineSeparator());
                                    }
                                    extractedContent.append(fastaText);
                                    extractedSequenceCount++;
                                }
                            }

                        } catch (NumberFormatException ex) {
                            System.err.println("Warning: Invalid position numbers in line: " + line);
                        } catch (Exception ex) {
                            System.err.println("Warning: Error processing line: " + line + " - " + ex.getMessage());
                        }
                    }

                    if (extractedSequenceCount == 0) {
                        throw new IllegalStateException(
                            "No sequences could be extracted from the provided regions. "
                                + "Please check whether the chromosome IDs and coordinates match the genome."
                        );
                    }

                    if (!outputPath.isEmpty()) {
                        Files.writeString(new File(outputPath).toPath(), extractedContent.toString(), StandardCharsets.UTF_8);
                    }

                    publish("Extraction completed - " + processedCount + " regions processed, "
                        + extractedSequenceCount + " sequences extracted");
                    return extractedContent.toString();
                } finally {
                    seaf.close();
                }
            }
            
            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    statusLabel.setText(message);
                }
            }
            
            @Override
            protected void done() {
                try {
                    String extractedContent = get();
                    progressBar.setIndeterminate(false);
                    outputTextArea.setText(extractedContent);
                    outputTextArea.setCaretPosition(0);
                    progressBar.setValue(100);
                    progressBar.setString("Extraction completed");
                    if (outputPath.isEmpty()) {
                        statusLabel.setText("Sequences extracted successfully to Output");
                        JOptionPane.showMessageDialog(ChromosomeRegionExtractorDialog.this,
                            "Chromosome region sequences extracted successfully!",
                            "Extraction Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        statusLabel.setText("Sequences extracted successfully to: " + outputPath);
                        JOptionPane.showMessageDialog(ChromosomeRegionExtractorDialog.this,
                            "Chromosome region sequences extracted successfully!",
                            "Extraction Complete", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    progressBar.setString("Error occurred");
                    statusLabel.setText("Error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(ChromosomeRegionExtractorDialog.this,
                        "Error during extraction: " + ex.getMessage(),
                        "Extraction Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    extractButton.setEnabled(true);
                }
            }
        };
        
        worker.execute();
    }
    
    /**
     * Add a button to clean up text formatting
     */
    private void addFormatCleanupButton() {
        JButton cleanupButton = new JButton("Format Text");
        cleanupButton.setToolTipText("Clean up and format pasted text for better alignment");
        cleanupButton.addActionListener(e -> cleanupTextFormatting());
        
        // Find the region button panel and add the cleanup button
        Container parent = regionListTextArea.getParent().getParent();
        if (parent instanceof JPanel) {
            JPanel regionPanel = (JPanel) parent;
            Component[] components = regionPanel.getComponents();
            for (Component comp : components) {
                if (comp instanceof JPanel) {
                    JPanel buttonPanel = (JPanel) comp;
                    if (buttonPanel.getLayout() instanceof FlowLayout) {
                        buttonPanel.add(new JLabel("  |  "));
                        buttonPanel.add(cleanupButton);
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Clean up text formatting to ensure proper column alignment
     */
    private void cleanupTextFormatting() {
        String text = regionListTextArea.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        // Split into lines and clean each line
        String[] lines = text.split("\n");
        StringBuilder cleanedText = new StringBuilder();
        
        for (String line : lines) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                // Keep comment lines and empty lines as-is
                cleanedText.append(line).append("\n");
            } else {
                // Clean up data lines by normalizing whitespace
                String cleanedLine = line.trim();
                // Split by any whitespace and rejoin with consistent spacing
                String[] parts = cleanedLine.split("\\s+");
                if (parts.length >= 3) {
                    // Format as fixed-width columns for better alignment
                    StringBuilder formattedLine = new StringBuilder();
                    for (int i = 0; i < parts.length; i++) {
                        if (i == 0) {
                            // First column (ID) - pad to 16 characters
                            formattedLine.append(String.format("%-16s", parts[i]));
                        } else if (i == 1 && parts.length == 4) {
                            // Second column in 4-column format (ChrID) - pad to 12 characters
                            formattedLine.append(String.format("%-12s", parts[i]));
                        } else if (i < parts.length - 1) {
                            // Numeric columns - pad to 12 characters, right-aligned
                            formattedLine.append(String.format("%12s", parts[i]));
                            formattedLine.append("    "); // 4 spaces between columns
                        } else {
                            // Last column
                            formattedLine.append(String.format("%12s", parts[i]));
                        }
                    }
                    cleanedText.append(formattedLine.toString()).append("\n");
                } else {
                    // Keep lines with insufficient columns as-is
                    cleanedText.append(cleanedLine).append("\n");
                }
            }
        }
        
        // Update text
        String newText = cleanedText.toString();
        if (!newText.equals(text)) {
            // Save cursor position
            int caretPosition = regionListTextArea.getCaretPosition();
            
            // Update text
            regionListTextArea.setText(newText);
            
            // Restore cursor position (with bounds checking)
            try {
                if (caretPosition <= newText.length()) {
                    regionListTextArea.setCaretPosition(caretPosition);
                }
            } catch (Exception e) {
                // If position is invalid, place at end
                regionListTextArea.setCaretPosition(newText.length());
            }
            
            statusLabel.setText("Text formatting cleaned up - columns aligned with fixed spacing");
        }
    }
    
    /**
     * Insert sample data with proper formatting
     */
    private void insertSampleData() {
        String currentText = regionListTextArea.getText();
        
        // Clear existing content if it's just the default template
        if (currentText.contains("# Enter chromosome regions")) {
            currentText = "";
        }
        
        // Sample data with explicit spacing that works well in monospace font
        String sampleData = 
            "# Sample chromosome regions - copy and modify as needed\n" +
            "#\n" +
            "# Format 1 (3 columns): ChrID        StartPos    EndPos\n" +
            "contig01        12051346    12074215\n" +
            "Chr_1           100000      102000\n" +
            "scaffold_40     613021      617108\n" +
            "#\n" +
            "# Format 2 (4 columns): GeneID       ChrID       StartPos    EndPos\n" +
            "Gene001         contig01    12051346    12074215\n" +
            "TestGene1       Chr_1       100000      120000\n" +
            "FinalGeneID     scaffold_40 613021      617108\n" +
            "\n";
        
        // Add sample data to existing content
        String newText = currentText + (currentText.isEmpty() ? "" : "\n") + sampleData;
        regionListTextArea.setText(newText);
        
        // Move cursor to end
        regionListTextArea.setCaretPosition(newText.length());
        
        statusLabel.setText("Sample data inserted - modify chromosome IDs and positions as needed");
    }
}
