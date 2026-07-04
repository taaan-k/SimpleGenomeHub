/*
 * Sequence Lookup Dialog with Cache Support
 */
package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.CachedSequenceLookup;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Dialog for looking up sequences by gene or transcript ID
 * Uses cached sequence files for fast lookup
 *
 * @author SimpleGenomeHub
 */
public class SequenceLookupDialog extends JDialog {

    private static final String GENE_SET_DEFAULT_OPTION = "Load Gene Set";
    private static final String GENE_SET_EMPTY_OPTION = "Load Gene Set (No Sets)";

    private SpeciesInfo species;
    private CachedSequenceLookup lookupEngine;

    // Input components
    private JTextArea idInputArea;
    private JButton searchButton;
    private JButton clearButton;
    private JComboBox<String> geneSetComboBox;
    private List<File> availableGeneSetFiles;

    // Result display components
    private JTabbedPane resultTabs;
    private JTextArea transcriptArea;
    private JTextArea cdsArea;
    private JTextArea proteinArea;

    // Control buttons
    private JButton exportButton;
    private JButton closeButton;

    // Status components
    private JLabel statusLabel;

    /**
     * Constructor
     */
    public SequenceLookupDialog(Window parent, SpeciesInfo species) {
        super(parent, "Sequence Lookup - " + species.getDisplayName(), ModalityType.MODELESS);

        this.species = species;
        this.lookupEngine = new CachedSequenceLookup(species);
        this.availableGeneSetFiles = new ArrayList<>();

        initializeComponents();
        setupLayout();
        setupEventHandlers();

        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(parent);
    }

    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Input components
        idInputArea = new JTextArea(3, 20);
        idInputArea.setLineWrap(true);
        idInputArea.setWrapStyleWord(true);
        idInputArea.setBorder(BorderFactory.createLoweredBevelBorder());
        searchButton = new JButton("Search");
        clearButton = new JButton("Clear");
        geneSetComboBox = new JComboBox<>();
        geneSetComboBox.setToolTipText("Select a Gene Set to load its IDs into the input box");

        // Result display components
        resultTabs = new JTabbedPane();
        transcriptArea = new JTextArea(15, 60);
        cdsArea = new JTextArea(15, 60);
        proteinArea = new JTextArea(15, 60);

        // Configure text areas
        configureTextArea(transcriptArea);
        configureTextArea(cdsArea);
        configureTextArea(proteinArea);

        // Add tabs
        resultTabs.addTab("Transcript Sequence", new JScrollPane(transcriptArea));
        resultTabs.addTab("CDS Sequence", new JScrollPane(cdsArea));
        resultTabs.addTab("Protein Sequence", new JScrollPane(proteinArea));

        // Control buttons
        exportButton = new JButton("Export Results");
        closeButton = new JButton("Close");

        // Status components
        statusLabel = new JLabel(generateExampleText());

        // Set button sizes and fonts
        Dimension buttonSize = SimpleGenomeHubStyle.SIZE_BUTTON_100_X_30;
        Font buttonFont = SimpleGenomeHubStyle.FONT_SANS_PLAIN_11;

        searchButton.setPreferredSize(buttonSize);
        clearButton.setPreferredSize(SimpleGenomeHubStyle.SIZE_BUTTON_80_X_30);
        geneSetComboBox.setPreferredSize(new Dimension(190, 30));
        exportButton.setPreferredSize(SimpleGenomeHubStyle.SIZE_BUTTON_120_X_30);
        closeButton.setPreferredSize(SimpleGenomeHubStyle.SIZE_BUTTON_80_X_30);

        searchButton.setFont(buttonFont);
        clearButton.setFont(buttonFont);
        geneSetComboBox.setFont(buttonFont);
        exportButton.setFont(buttonFont);
        closeButton.setFont(buttonFont);

        refreshGeneSetOptions();

        // Initially disable export button
        exportButton.setEnabled(false);
    }

    /**
     * Configure text area properties
     */
    private void configureTextArea(JTextArea textArea) {
        textArea.setEditable(false);
        textArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(false);
        textArea.setBorder(BorderFactory.createLoweredBevelBorder());
    }

    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());

        // Top panel: Species info and input
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);

        // Center: Result display
        add(resultTabs, BorderLayout.CENTER);

        // Bottom: Status and buttons
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Create top panel with species info and input controls
     */
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Species info
        JPanel speciesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        speciesPanel.setBorder(new TitledBorder("Current Species"));
        JLabel speciesLabel = new JLabel(species.getDisplayName());
        speciesLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        speciesPanel.add(speciesLabel);

        // Cache status (only show if cache is available)
        if (lookupEngine.isCacheAvailable()) {
            JLabel cacheLabel = new JLabel(" [Cache Available]");
            cacheLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_ITALIC_10);
            cacheLabel.setForeground(Color.GREEN.darker());
            speciesPanel.add(cacheLabel);
        }

        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBorder(new TitledBorder("Sequence ID Lookup"));

        JScrollPane idScrollPane = new JScrollPane(idInputArea);

        JPanel idPanel = new JPanel(new BorderLayout(6, 0));
        idPanel.setOpaque(false);
        idPanel.add(new JLabel("ID:"), BorderLayout.WEST);
        idPanel.add(idScrollPane, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        controlPanel.setOpaque(false);
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

        geneSetComboBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        controlPanel.add(geneSetComboBox);
        controlPanel.add(Box.createVerticalStrut(8));

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actionPanel.setOpaque(false);
        actionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionPanel.add(searchButton);
        actionPanel.add(clearButton);
        controlPanel.add(actionPanel);

        inputPanel.add(idPanel, BorderLayout.CENTER);
        inputPanel.add(controlPanel, BorderLayout.EAST);

        panel.add(speciesPanel, BorderLayout.NORTH);
        panel.add(inputPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Create bottom panel with status and control buttons
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(exportButton);
        buttonPanel.add(closeButton);

        panel.add(statusPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.EAST);

        return panel;
    }

    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        searchButton.addActionListener(e -> performSearch());
        clearButton.addActionListener(e -> clearResults());
        geneSetComboBox.addActionListener(e -> loadSelectedGeneSetIntoInput());

        exportButton.addActionListener(e -> exportResults());
        closeButton.addActionListener(e -> dispose());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    /**
     * Perform sequence search
     */
    private void performSearch() {
        String searchText = idInputArea.getText().trim();
        if (searchText.isEmpty()) {
            statusLabel.setText("Please enter an ID to search");
            return;
        }

        // Parse input for batch query
        String[] searchIds = parseSearchInput(searchText);

        // Check if cache is available, show appropriate status
        if (!lookupEngine.isCacheAvailable()) {
            statusLabel.setText("Generating sequence cache files, please wait...");
        } else if (searchIds.length == 1) {
            statusLabel.setText("Searching for: " + searchIds[0] + "...");
        } else {
            statusLabel.setText("Searching for " + searchIds.length + " IDs...");
        }
        searchButton.setEnabled(false);

        // Perform search in background
        SwingWorker<List<CachedSequenceLookup.LookupResult>, String> worker =
            new SwingWorker<List<CachedSequenceLookup.LookupResult>, String>() {
                @Override
                protected List<CachedSequenceLookup.LookupResult> doInBackground() throws Exception {
                    if (!lookupEngine.isCacheAvailable()) {
                        publish("Extracting transcript sequences...");
                        Thread.sleep(500);
                        publish("Extracting CDS sequences...");
                        Thread.sleep(500);
                        publish("Extracting protein sequences...");
                        Thread.sleep(500);
                        publish("Processing completed, searching...");
                    }

                    List<CachedSequenceLookup.LookupResult> results = new ArrayList<>();
                    for (String id : searchIds) {
                        results.add(lookupEngine.lookupSequencesEnhanced(id));
                    }
                    return results;
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String message : chunks) {
                        statusLabel.setText(message);
                    }
                }

                @Override
                protected void done() {
                    try {
                        List<CachedSequenceLookup.LookupResult> results = get();
                        displayBatchResults(results);
                    } catch (Exception e) {
                        statusLabel.setText("Search failed: " + e.getMessage());
                    } finally {
                        searchButton.setEnabled(true);
                    }
                }
            };

        worker.execute();
    }

    /**
     * Parse search input to handle multiple IDs
     */
    private String[] parseSearchInput(String searchText) {
        String[] parts = searchText.split("[,;\\n\\r\\s]+");

        List<String> validIds = new ArrayList<>();
        for (String part : parts) {
            String id = part.trim();
            if (!id.isEmpty()) {
                validIds.add(id);
            }
        }

        return validIds.toArray(new String[0]);
    }

    /**
     * Display batch search results
     */
    private void displayBatchResults(List<CachedSequenceLookup.LookupResult> results) {
        if (results.isEmpty()) {
            clearResults();
            statusLabel.setText("No search results");
            return;
        }

        if (results.size() == 1) {
            displayResults(results.get(0));
            return;
        }

        StringBuilder transcriptContent = new StringBuilder();
        StringBuilder cdsContent = new StringBuilder();
        StringBuilder proteinContent = new StringBuilder();
        int successCount = 0;
        List<String> notFoundIds = new ArrayList<>();

        for (CachedSequenceLookup.LookupResult result : results) {
            if (result.isSuccess()) {
                successCount++;

                if (result.isGeneQuery() && !result.getAllSequences().isEmpty()) {
                    for (CachedSequenceLookup.SequenceEntry entry : result.getAllSequences()) {
                        if (!entry.getTranscriptSequence().isEmpty()) {
                            transcriptContent.append(entry.getTranscriptSequence());
                        }
                        if (!entry.getCdsSequence().isEmpty()) {
                            cdsContent.append(entry.getCdsSequence());
                        }
                        if (!entry.getProteinSequence().isEmpty()) {
                            proteinContent.append(entry.getProteinSequence());
                        }
                    }
                } else {
                    if (!result.getTranscriptSequence().isEmpty()) {
                        transcriptContent.append(result.getTranscriptSequence());
                    }
                    if (!result.getCdsSequence().isEmpty()) {
                        cdsContent.append(result.getCdsSequence());
                    }
                    if (!result.getProteinSequence().isEmpty()) {
                        proteinContent.append(result.getProteinSequence());
                    }
                }
            } else {
                notFoundIds.add(result.getSearchId());
            }
        }

        transcriptArea.setText(transcriptContent.toString());
        cdsArea.setText(cdsContent.toString());
        proteinArea.setText(proteinContent.toString());

        StringBuilder statusText = new StringBuilder();
        statusText.append("Found sequences for ").append(successCount).append("/").append(results.size()).append(" IDs");
        if (!notFoundIds.isEmpty()) {
            statusText.append(" (Not found: ").append(String.join(", ", notFoundIds)).append(")");
        }
        statusLabel.setText(statusText.toString());

        exportButton.setEnabled(successCount > 0);

        if (transcriptContent.length() > 0) {
            resultTabs.setSelectedIndex(0);
        } else if (cdsContent.length() > 0) {
            resultTabs.setSelectedIndex(1);
        } else if (proteinContent.length() > 0) {
            resultTabs.setSelectedIndex(2);
        }
    }

    /**
     * Display search results
     */
    private void displayResults(CachedSequenceLookup.LookupResult result) {
        if (result.isSuccess()) {
            if (result.isGeneQuery() && !result.getAllSequences().isEmpty()) {
                displayMultipleTranscripts(result);
            } else {
                transcriptArea.setText(result.getTranscriptSequence());
                cdsArea.setText(result.getCdsSequence());
                proteinArea.setText(result.getProteinSequence());
            }

            statusLabel.setText("Found sequences for: " + result.getFoundId());
            exportButton.setEnabled(true);

            if (!result.getTranscriptSequence().isEmpty()
                    || (!result.getAllSequences().isEmpty()
                    && !result.getAllSequences().get(0).getTranscriptSequence().isEmpty())) {
                resultTabs.setSelectedIndex(0);
            } else if (!result.getCdsSequence().isEmpty()
                    || (!result.getAllSequences().isEmpty()
                    && !result.getAllSequences().get(0).getCdsSequence().isEmpty())) {
                resultTabs.setSelectedIndex(1);
            } else if (!result.getProteinSequence().isEmpty()
                    || (!result.getAllSequences().isEmpty()
                    && !result.getAllSequences().get(0).getProteinSequence().isEmpty())) {
                resultTabs.setSelectedIndex(2);
            }
        } else {
            clearResults();
            statusLabel.setText("No sequences found for: " + idInputArea.getText().trim());
            if (!result.getSuggestedIds().isEmpty()) {
                String suggestions = String.join(", ", result.getSuggestedIds());
                statusLabel.setText(statusLabel.getText() + " (Similar: " + suggestions + ")");
            }
        }
    }

    /**
     * Display multiple transcripts for a gene query
     */
    private void displayMultipleTranscripts(CachedSequenceLookup.LookupResult result) {
        StringBuilder transcriptContent = new StringBuilder();
        StringBuilder cdsContent = new StringBuilder();
        StringBuilder proteinContent = new StringBuilder();

        for (CachedSequenceLookup.SequenceEntry entry : result.getAllSequences()) {
            if (!entry.getTranscriptSequence().isEmpty()) {
                transcriptContent.append(entry.getTranscriptSequence());
            }
            if (!entry.getCdsSequence().isEmpty()) {
                cdsContent.append(entry.getCdsSequence());
            }
            if (!entry.getProteinSequence().isEmpty()) {
                proteinContent.append(entry.getProteinSequence());
            }
        }

        transcriptArea.setText(transcriptContent.toString());
        cdsArea.setText(cdsContent.toString());
        proteinArea.setText(proteinContent.toString());
    }

    /**
     * Clear all results
     */
    private void clearResults() {
        transcriptArea.setText("");
        cdsArea.setText("");
        proteinArea.setText("");
        idInputArea.setText("");
        exportButton.setEnabled(false);
        if (geneSetComboBox.getItemCount() > 0) {
            geneSetComboBox.setSelectedIndex(0);
        }
        statusLabel.setText(generateExampleText());
    }

    /**
     * Export search results to FASTA file
     */
    private void exportResults() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Sequences");
        fileChooser.setSelectedFile(new File(idInputArea.getText().trim() + "_sequences.fasta"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File outputFile = fileChooser.getSelectedFile();

            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                if (!transcriptArea.getText().isEmpty()) {
                    writer.println(transcriptArea.getText());
                    writer.println();
                }

                if (!cdsArea.getText().isEmpty()) {
                    writer.println(cdsArea.getText());
                    writer.println();
                }

                if (!proteinArea.getText().isEmpty()) {
                    writer.println(proteinArea.getText());
                    writer.println();
                }

                statusLabel.setText("Sequences exported to: " + outputFile.getName());

            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to export sequences: " + e.getMessage(),
                    "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Generate dynamic example text based on actual sequence IDs from the species
     */
    private String generateExampleText() {
        try {
            String geneExample = null;
            String transcriptExample = null;

            String speciesPrefix = species.getSpeciesDirectoryName();
            File transcriptFile = new File(species.getSequenceDir(), speciesPrefix + ".transcripts.fasta");

            if (transcriptFile.exists()) {
                List<String> examples = extractExampleIds(transcriptFile, 2);
                if (examples.size() >= 1) {
                    transcriptExample = examples.get(0);
                    geneExample = extractGeneIdFromTranscriptId(transcriptExample);
                }
                if (examples.size() >= 2 && geneExample == null) {
                    geneExample = examples.get(1);
                }
            }

            if (geneExample == null && transcriptExample == null) {
                File cdsFile = new File(species.getSequenceDir(), speciesPrefix + ".cds.fasta");
                if (cdsFile.exists()) {
                    List<String> examples = extractExampleIds(cdsFile, 2);
                    if (examples.size() >= 1) {
                        transcriptExample = examples.get(0);
                        geneExample = extractGeneIdFromTranscriptId(transcriptExample);
                    }
                }
            }

            StringBuilder exampleText = new StringBuilder("Enter a sequence ID");
            if (geneExample != null && transcriptExample != null) {
                exampleText.append(" (e.g., gene: ").append(geneExample)
                    .append(", transcript: ").append(transcriptExample).append(")");
            } else if (transcriptExample != null) {
                exampleText.append(" (e.g., ").append(transcriptExample).append(")");
            } else {
                exampleText.append(" to search for sequences");
            }

            return exampleText.toString();

        } catch (Exception e) {
            return "Enter a gene ID or transcript ID to search for sequences";
        }
    }

    /**
     * Extract example sequence IDs from FASTA file
     */
    private List<String> extractExampleIds(File fastaFile, int maxCount) {
        List<String> examples = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(fastaFile))) {
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null && count < maxCount) {
                if (line.startsWith(">")) {
                    String header = line.substring(1);
                    String id = extractIdFromFastaHeader(header);
                    if (id != null && !id.isEmpty()) {
                        examples.add(id);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors and return what we have
        }

        return examples;
    }

    /**
     * Extract sequence ID from FASTA header
     */
    private String extractIdFromFastaHeader(String header) {
        try {
            String id = header.trim();

            int tabIndex = id.indexOf('\t');
            if (tabIndex > 0) {
                id = id.substring(0, tabIndex);
            }

            int spaceIndex = id.indexOf(' ');
            if (spaceIndex > 0) {
                id = id.substring(0, spaceIndex);
            }

            int pipeIndex = id.indexOf('|');
            if (pipeIndex > 0) {
                id = id.substring(0, pipeIndex);
            }

            return id.trim();
        } catch (Exception e) {
            return header;
        }
    }

    /**
     * Try to extract gene ID from transcript ID
     */
    private String extractGeneIdFromTranscriptId(String transcriptId) {
        try {
            if (transcriptId == null || transcriptId.isEmpty()) {
                return null;
            }

            String geneId = transcriptId;

            if (geneId.matches(".*\\.\\d+$")) {
                int lastDot = geneId.lastIndexOf('.');
                geneId = geneId.substring(0, lastDot);
            }

            if (geneId.matches(".*\\.t\\d+$")) {
                int lastDot = geneId.lastIndexOf(".t");
                geneId = geneId.substring(0, lastDot);
            }

            return geneId.equals(transcriptId) ? null : geneId;

        } catch (Exception e) {
            return null;
        }
    }

    private void refreshGeneSetOptions() {
        availableGeneSetFiles = loadGeneSetFiles();

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(availableGeneSetFiles.isEmpty() ? GENE_SET_EMPTY_OPTION : GENE_SET_DEFAULT_OPTION);
        for (File geneSetFile : availableGeneSetFiles) {
            model.addElement(extractGeneSetDisplayName(geneSetFile));
        }

        geneSetComboBox.setModel(model);
        geneSetComboBox.setSelectedIndex(0);
        geneSetComboBox.setEnabled(!availableGeneSetFiles.isEmpty());
    }

    private List<File> loadGeneSetFiles() {
        List<File> files = new ArrayList<>();
        File geneSetDir = species.getGeneSetDir();
        if (geneSetDir == null || !geneSetDir.isDirectory()) {
            return files;
        }

        File[] geneSetFiles = geneSetDir.listFiles((dir, name) ->
            name.toLowerCase().endsWith(".gene.txt"));
        if (geneSetFiles == null || geneSetFiles.length == 0) {
            return files;
        }

        java.util.Arrays.sort(geneSetFiles, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File geneSetFile : geneSetFiles) {
            files.add(geneSetFile);
        }
        return files;
    }

    private void loadSelectedGeneSetIntoInput() {
        int selectedIndex = geneSetComboBox.getSelectedIndex();
        if (selectedIndex <= 0) {
            return;
        }

        int fileIndex = selectedIndex - 1;
        if (fileIndex < 0 || fileIndex >= availableGeneSetFiles.size()) {
            geneSetComboBox.setSelectedIndex(0);
            return;
        }

        File selectedGeneSetFile = availableGeneSetFiles.get(fileIndex);
        try {
            List<String> geneIds = readGeneSetIds(selectedGeneSetFile);
            if (geneIds.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "The selected Gene Set does not contain any IDs.",
                    "Empty Gene Set",
                    JOptionPane.WARNING_MESSAGE);
                geneSetComboBox.setSelectedIndex(0);
                return;
            }

            idInputArea.setText(String.join("\n", geneIds));
            idInputArea.setCaretPosition(0);
            idInputArea.requestFocusInWindow();
            clearDisplayedResults();
            statusLabel.setText("Loaded " + geneIds.size() + " IDs from Gene Set: "
                + extractGeneSetDisplayName(selectedGeneSetFile));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Failed to load Gene Set:\n" + e.getMessage(),
                "Gene Set Load Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<String> readGeneSetIds(File geneSetFile) throws IOException {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(geneSetFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#####")) {
                    continue;
                }
                ids.add(trimmed);
            }
        }
        return new ArrayList<>(ids);
    }

    private String extractGeneSetDisplayName(File geneSetFile) {
        if (geneSetFile == null) {
            return "";
        }

        String fileName = geneSetFile.getName();
        if (fileName.toLowerCase().endsWith(".gene.txt")) {
            return fileName.substring(0, fileName.length() - ".gene.txt".length());
        }
        return fileName;
    }

    private void clearDisplayedResults() {
        transcriptArea.setText("");
        cdsArea.setText("");
        proteinArea.setText("");
        exportButton.setEnabled(false);
    }
}
