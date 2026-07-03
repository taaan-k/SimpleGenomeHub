package simplegenomehub.gui;

import simplegenomehub.util.identification.SpeciesIdentificationEngine;
import simplegenomehub.util.identification.SpeciesMatchResult;
import simplegenomehub.util.identification.SpeciesMatchResult.SequenceMatch;
import toolsKit.GUItools.DragDropFileEnterListener;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dialog for identifying species based on sequence IDs
 */
public class SpeciesIdentificationDialog extends JDialog {
    
    private SpeciesIdentificationEngine engine;
    private simplegenomehub.model.SpeciesManager speciesManager;
    private JTextArea sequenceIdTextArea;
    private JTable resultsTable;
    private ResultsTableModel resultsTableModel;
    private JButton searchButton;
    private JButton loadFileButton;
    private JButton clearButton;
    private JButton sampleDataButton;
    private JButton viewDetailsButton;
    private JButton exportResultsButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    
    // Search options
    private JCheckBox fuzzyMatchCheckBox;
    private JCheckBox patternMatchCheckBox;
    private JCheckBox searchAllVersionsCheckBox;
    private JCheckBox caseSensitiveCheckBox;
    private JSpinner confidenceSpinner;
    
    private List<SpeciesMatchResult> currentResults;
    
    public SpeciesIdentificationDialog(Frame parent, simplegenomehub.model.SpeciesManager speciesManager) {
        super(parent, "Species Identification from Sequence IDs", false);
        this.speciesManager = speciesManager;
        this.engine = new SpeciesIdentificationEngine(speciesManager);
        this.currentResults = new ArrayList<>();
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(parent);
    }
    
    private void initializeComponents() {
        // Input area
        sequenceIdTextArea = new JTextArea(10, 60);
        sequenceIdTextArea.setFont(SimpleGenomeHubStyle.FONT_COURIER_NEW_PLAIN_12);
        sequenceIdTextArea.setLineWrap(false);
        sequenceIdTextArea.setWrapStyleWord(false);
        sequenceIdTextArea.setText("# Enter sequence IDs (one per line)\n" +
                "# Example IDs:\n" +
                "# AT1G01010.1\n" +
                "# LOC_Os01g01010.1\n" +
                "# GRMZM2G000001_T01\n" +
                "\n");
        
        // Results table
        resultsTableModel = new ResultsTableModel();
        resultsTable = new JTable(resultsTableModel);
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultsTable.setRowHeight(25);
        resultsTable.getTableHeader().setReorderingAllowed(false);
        
        // Configure column renderers
        setupTableRenderers();
        
        // Buttons
        loadFileButton = new JButton("Load from File");
        clearButton = new JButton("Clear");
        sampleDataButton = new JButton("Sample Data");
        searchButton = new JButton("Start Search");
        searchButton.setBackground(new Color(40, 167, 69));
        searchButton.setForeground(Color.WHITE);
        searchButton.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        
        viewDetailsButton = new JButton("View Details");
        exportResultsButton = new JButton("Export Results");
        
        // Search options
        fuzzyMatchCheckBox = new JCheckBox("Include fuzzy matching", true);
        patternMatchCheckBox = new JCheckBox("Pattern recognition", true);
        searchAllVersionsCheckBox = new JCheckBox("Search all versions", true);
        caseSensitiveCheckBox = new JCheckBox("Case sensitive", false);
        
        confidenceSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 100.0, 1.0));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(confidenceSpinner, "0.0");
        confidenceSpinner.setEditor(editor);
        
        // Status components
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready to search");
        statusLabel = new JLabel("Enter sequence IDs and click 'Start Search'");
        
        // Setup drag and drop
        new DropTarget(sequenceIdTextArea, new DragDropFileEnterListener(sequenceIdTextArea));
    }
    
    private void setupTableRenderers() {
        // Confidence column renderer
        resultsTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (value instanceof Double) {
                    double confidence = (Double) value;
                    setText(String.format("%.1f%%", confidence * 100));
                    
                    // Color coding based on confidence
                    if (!isSelected) {
                        if (confidence > 0.8) {
                            setBackground(new Color(212, 237, 218)); // Light green
                        } else if (confidence > 0.5) {
                            setBackground(new Color(255, 243, 205)); // Light yellow
                        } else {
                            setBackground(new Color(248, 215, 218)); // Light red
                        }
                    }
                }
                
                return this;
            }
        });
        
        // Match type column renderer
        resultsTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (value instanceof SpeciesMatchResult.MatchType) {
                    SpeciesMatchResult.MatchType type = (SpeciesMatchResult.MatchType) value;
                    setText(type.getDescription());
                    
                    // Color coding based on match type
                    if (!isSelected) {
                        switch (type) {
                            case EXACT:
                                setBackground(new Color(212, 237, 218)); // Light green
                                break;
                            case PATTERN:
                                setBackground(new Color(217, 237, 247)); // Light blue
                                break;
                            case FUZZY:
                                setBackground(new Color(255, 243, 205)); // Light yellow
                                break;
                            default:
                                setBackground(Color.WHITE);
                                break;
                        }
                    }
                }
                
                return this;
            }
        });
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top panel - input area
        JPanel topPanel = new JPanel(new BorderLayout());
        
        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(new TitledBorder("Sequence IDs Input"));
        
        // Input buttons
        JPanel inputButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        inputButtonPanel.add(loadFileButton);
        inputButtonPanel.add(sampleDataButton);
        inputButtonPanel.add(clearButton);
        inputButtonPanel.add(new JLabel("  |  Drag & drop files here"));
        inputPanel.add(inputButtonPanel, BorderLayout.NORTH);
        
        JScrollPane inputScrollPane = new JScrollPane(sequenceIdTextArea);
        inputScrollPane.setPreferredSize(new Dimension(850, 200));
        inputScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        
        topPanel.add(inputPanel, BorderLayout.CENTER);
        
        // Options panel
        JPanel optionsPanel = new JPanel(new GridLayout(2, 3, 10, 5));
        optionsPanel.setBorder(new TitledBorder("Search Options"));
        optionsPanel.add(fuzzyMatchCheckBox);
        optionsPanel.add(patternMatchCheckBox);
        optionsPanel.add(searchAllVersionsCheckBox);
        optionsPanel.add(caseSensitiveCheckBox);
        
        JPanel confidencePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        confidencePanel.add(new JLabel("Min confidence %:"));
        confidencePanel.add(confidenceSpinner);
        optionsPanel.add(confidencePanel);
        
        JPanel searchButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        searchButtonPanel.add(searchButton);
        optionsPanel.add(searchButtonPanel);
        
        topPanel.add(optionsPanel, BorderLayout.SOUTH);
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Center panel - results
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(new TitledBorder("Results (ranked by confidence)"));
        
        JScrollPane resultsScrollPane = new JScrollPane(resultsTable);
        resultsScrollPane.setPreferredSize(new Dimension(850, 300));
        centerPanel.add(resultsScrollPane, BorderLayout.CENTER);
        
        // Results buttons
        JPanel resultsButtonPanel = new JPanel(new FlowLayout());
        resultsButtonPanel.add(viewDetailsButton);
        resultsButtonPanel.add(exportResultsButton);
        centerPanel.add(resultsButtonPanel, BorderLayout.SOUTH);
        
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // Bottom panel - status
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        bottomPanel.add(progressBar, BorderLayout.NORTH);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        add(mainPanel, BorderLayout.CENTER);
    }
    
    private void setupEventHandlers() {
        loadFileButton.addActionListener(e -> loadSequenceFile());
        clearButton.addActionListener(e -> clearInput());
        sampleDataButton.addActionListener(e -> insertSampleData());
        searchButton.addActionListener(e -> performSearch());
        viewDetailsButton.addActionListener(e -> showMatchDetails());
        exportResultsButton.addActionListener(e -> exportResults());
        
        // Update engine configuration when options change
        fuzzyMatchCheckBox.addActionListener(e -> 
            engine.setEnableFuzzyMatching(fuzzyMatchCheckBox.isSelected()));
        patternMatchCheckBox.addActionListener(e -> 
            engine.setEnablePatternMatching(patternMatchCheckBox.isSelected()));
        
        confidenceSpinner.addChangeListener(e -> {
            double threshold = ((Number) confidenceSpinner.getValue()).doubleValue() / 100.0;
            engine.setSignificanceThreshold(threshold);
        });
    }
    
    private void loadSequenceFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Sequence ID File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || 
                       f.getName().toLowerCase().endsWith(".txt") ||
                       f.getName().toLowerCase().endsWith(".tsv") ||
                       f.getName().toLowerCase().endsWith(".csv") ||
                       f.getName().toLowerCase().endsWith(".list");
            }
            
            @Override
            public String getDescription() {
                return "Text files (*.txt, *.tsv, *.csv, *.list)";
            }
        });
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                StringBuilder content = new StringBuilder(sequenceIdTextArea.getText());
                if (!content.toString().trim().isEmpty() && 
                    !content.toString().trim().endsWith("\\n")) {
                    content.append("\\n");
                }
                
                BufferedReader reader = new BufferedReader(new FileReader(selectedFile));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        content.append(line).append("\\n");
                    }
                }
                reader.close();
                
                sequenceIdTextArea.setText(content.toString());
                statusLabel.setText("Loaded sequence IDs from: " + selectedFile.getName());
                
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, 
                    "Error loading file: " + ex.getMessage(),
                    "File Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void clearInput() {
        sequenceIdTextArea.setText("# Enter sequence IDs (one per line)\n\n");
        currentResults.clear();
        resultsTableModel.fireTableDataChanged();
        statusLabel.setText("Input cleared - ready for new sequence IDs");
    }
    
    private void insertSampleData() {
        StringBuilder sampleData = new StringBuilder();
        sampleData.append("# Sample sequence IDs from current database\n");
        sampleData.append("# Automatically generated from available species\n\n");
        
        try {
            // Get sample IDs from each species in the database
            java.util.List<simplegenomehub.model.SpeciesInfo> allSpecies = speciesManager.getAllSpecies();
            int speciesSampled = 0;
            
            for (simplegenomehub.model.SpeciesInfo species : allSpecies) {
                if (speciesSampled >= 4) break; // Limit to 4 species for readability
                
                try {
                    // Get sample IDs from this species
                    java.util.List<String> sampleIds = getSampleIdsFromSpecies(species, 3);
                    if (!sampleIds.isEmpty()) {
                        sampleData.append("# ").append(species.getSpeciesName())
                                  .append(" v").append(species.getVersion()).append("\n");
                        for (String id : sampleIds) {
                            sampleData.append(id).append("\n");
                        }
                        sampleData.append("\n");
                        speciesSampled++;
                    }
                } catch (Exception e) {
                    System.err.println("Error sampling from " + species.getSpeciesName() + ": " + e.getMessage());
                }
            }
            
            // If we couldn't get samples from database, use fallback
            if (speciesSampled == 0) {
                sampleData.append("# Fallback sample data\n");
                sampleData.append("AT1G01010.1\n");
                sampleData.append("LOC_Os01g01010.1\n");
                sampleData.append("lcfv2_26152\n");
                sampleData.append("lcfv2_26189\n");
            }
            
        } catch (Exception e) {
            System.err.println("Error generating sample data: " + e.getMessage());
            sampleData = new StringBuilder("# Error generating samples - using defaults\n");
            sampleData.append("AT1G01010.1\n");
            sampleData.append("LOC_Os01g01010.1\n");
            sampleData.append("lcfv2_26152\n");
        }
        
        sequenceIdTextArea.setText(sampleData.toString());
        statusLabel.setText("Sample data from current database inserted - ready for analysis");
    }
    
    /**
     * Get sample sequence IDs from a species
     */
    private java.util.List<String> getSampleIdsFromSpecies(simplegenomehub.model.SpeciesInfo species, int maxSamples) {
        java.util.List<String> sampleIds = new java.util.ArrayList<>();
        
        try {
            // Try to get sample IDs from sequence files
            java.io.File sequenceDir = species.getSequenceDir();
            if (sequenceDir != null && sequenceDir.exists()) {
                java.io.File[] fastaFiles = sequenceDir.listFiles((dir, name) -> {
                    String lowerName = name.toLowerCase();
                    return (lowerName.contains("transcript") || lowerName.contains("cds") || lowerName.contains("protein")) &&
                           (lowerName.endsWith(".fasta") || lowerName.endsWith(".fa"));
                });
                
                if (fastaFiles != null && fastaFiles.length > 0) {
                    // Read first suitable FASTA file
                    java.io.File targetFile = fastaFiles[0];
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(targetFile))) {
                        String line;
                        while ((line = reader.readLine()) != null && sampleIds.size() < maxSamples) {
                            if (line.startsWith(">")) {
                                String id = extractSequenceId(line);
                                if (id != null && !id.isEmpty()) {
                                    sampleIds.add(id);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error sampling from " + species.getSpeciesName() + ": " + e.getMessage());
        }
        
        return sampleIds;
    }
    
    /**
     * Extract sequence ID from FASTA header line
     */
    private String extractSequenceId(String headerLine) {
        // Remove ">" and get first token (ID)
        String line = headerLine.substring(1).trim();
        
        // Handle both space and tab separators
        int spaceIndex = line.indexOf(' ');
        int tabIndex = line.indexOf('\t');
        
        int separatorIndex = -1;
        if (spaceIndex > 0 && tabIndex > 0) {
            separatorIndex = Math.min(spaceIndex, tabIndex);
        } else if (spaceIndex > 0) {
            separatorIndex = spaceIndex;
        } else if (tabIndex > 0) {
            separatorIndex = tabIndex;
        }
        
        return separatorIndex > 0 ? line.substring(0, separatorIndex) : line;
    }
    
    private void performSearch() {
        String inputText = sequenceIdTextArea.getText();
        if (inputText == null || inputText.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Please enter sequence IDs to search.",
                "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Parse sequence IDs
        List<String> sequenceIds = parseSequenceIds(inputText);
        if (sequenceIds.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "No valid sequence IDs found in the input.",
                "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Disable controls during search
        searchButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setString("Searching across species...");
        statusLabel.setText("Processing " + sequenceIds.size() + " sequence IDs...");
        
        // Perform search in background
        SwingWorker<List<SpeciesMatchResult>, String> worker = 
            new SwingWorker<List<SpeciesMatchResult>, String>() {
            
            @Override
            protected List<SpeciesMatchResult> doInBackground() throws Exception {
                publish("Initializing species identification engine...");
                
                // Configure engine based on options
                engine.setEnableFuzzyMatching(fuzzyMatchCheckBox.isSelected());
                engine.setEnablePatternMatching(patternMatchCheckBox.isSelected());
                double threshold = ((Number) confidenceSpinner.getValue()).doubleValue() / 100.0;
                engine.setSignificanceThreshold(threshold);
                
                publish("Searching across all loaded species...");
                return engine.identifySpecies(sequenceIds);
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
                    currentResults = get();
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    progressBar.setString("Search completed");
                    
                    resultsTableModel.fireTableDataChanged();
                    
                    String message;
                    if (currentResults.isEmpty()) {
                        message = "No matching species found for the provided sequence IDs";
                    } else {
                        message = "Found " + currentResults.size() + " matching species";
                        // Auto-select best match
                        if (resultsTable.getRowCount() > 0) {
                            resultsTable.setRowSelectionInterval(0, 0);
                        }
                    }
                    statusLabel.setText(message);
                    
                } catch (Exception ex) {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(0);
                    progressBar.setString("Search failed");
                    statusLabel.setText("Error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(SpeciesIdentificationDialog.this,
                        "Error during search: " + ex.getMessage(),
                        "Search Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    searchButton.setEnabled(true);
                }
            }
        };
        
        worker.execute();
    }
    
    private List<String> parseSequenceIds(String inputText) {
        List<String> ids = new ArrayList<>();
        String[] lines = inputText.split("\\n");
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                // Split by whitespace in case multiple IDs per line
                String[] parts = line.split("\\s+");
                for (String part : parts) {
                    part = part.trim();
                    if (!part.isEmpty()) {
                        ids.add(part);
                    }
                }
            }
        }
        
        return ids;
    }
    
    private void showMatchDetails() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow < 0 || selectedRow >= currentResults.size()) {
            JOptionPane.showMessageDialog(this, 
                "Please select a species from the results table.",
                "Selection Required", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        SpeciesMatchResult result = currentResults.get(selectedRow);
        SpeciesMatchDetailsDialog detailsDialog = new SpeciesMatchDetailsDialog(this, result);
        detailsDialog.setVisible(true);
    }
    
    private void exportResults() {
        if (currentResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "No results to export. Please run a search first.",
                "Export Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Search Results");
        fileChooser.setSelectedFile(new File("species_identification_results.txt"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File outputFile = fileChooser.getSelectedFile();
            try {
                exportResultsToFile(outputFile);
                statusLabel.setText("Results exported to: " + outputFile.getName());
                JOptionPane.showMessageDialog(this,
                    "Results exported successfully!",
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "Error exporting results: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exportResultsToFile(File outputFile) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
        
        writer.write("Species Identification Results\\n");
        writer.write("Generated: " + new java.util.Date() + "\\n");
        writer.write("=".repeat(50) + "\\n\\n");
        
        for (int i = 0; i < currentResults.size(); i++) {
            SpeciesMatchResult result = currentResults.get(i);
            
            writer.write("Rank " + (i + 1) + ": " + result.getSpecies().getSpeciesName() + 
                        " (" + result.getSpecies().getVersion() + ")\\n");
            writer.write("Confidence: " + result.getMatchPercentage() + 
                        " (" + result.getMatchRatio() + " sequences)\\n");
            writer.write("Primary Match Type: " + result.getPrimaryMatchType().getDescription() + "\\n");
            
            writer.write("\\nMatched IDs:\\n");
            for (SequenceMatch match : result.getDetailedMatches()) {
                writer.write("  " + match.getQueryId() + " → " + match.getMatchedId() + 
                           " (" + match.getMatchType().getDescription() + ")\\n");
            }
            
            if (!result.getUnmatchedIds().isEmpty()) {
                writer.write("\\nUnmatched IDs:\\n");
                for (String unmatchedId : result.getUnmatchedIds()) {
                    writer.write("  " + unmatchedId + "\\n");
                }
            }
            
            writer.write("\\n" + "-".repeat(30) + "\\n\\n");
        }
        
        writer.close();
    }
    
    // Results table model
    private class ResultsTableModel extends AbstractTableModel {
        private final String[] columnNames = {
            "Species", "Version", "Confidence", "Matched", "Type"
        };
        
        @Override
        public int getRowCount() {
            return currentResults.size();
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
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex >= currentResults.size()) return null;
            
            SpeciesMatchResult result = currentResults.get(rowIndex);
            switch (columnIndex) {
                case 0: return result.getSpecies().getSpeciesName();
                case 1: return result.getSpecies().getVersion();
                case 2: return result.getConfidenceScore();
                case 3: return result.getMatchRatio();
                case 4: return result.getPrimaryMatchType();
                default: return null;
            }
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 2: return Double.class;
                case 4: return SpeciesMatchResult.MatchType.class;
                default: return String.class;
            }
        }
    }
    
    @Override
    public void dispose() {
        if (engine != null) {
            engine.shutdown();
        }
        super.dispose();
    }
}
