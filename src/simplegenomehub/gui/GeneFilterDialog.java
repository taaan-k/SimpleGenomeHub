/*
 * Gene Filter Dialog
 * Batch filter genes from expression matrix using gene ID lists
 */
package simplegenomehub.gui;

import simplegenomehub.model.ExpressionMatrix;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.gui.components.ExpressionDataTable;
import biocjava.bioDoer.JIGplotToolkit.HeatMap.HeatmapControl2;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * Dialog for batch filtering genes from expression matrix
 * Similar to TBtools filtering functionality
 * 
 * @author SimpleGenomeHub
 */
public class GeneFilterDialog extends JDialog {
    
    private static final Logger logger = Logger.getLogger(GeneFilterDialog.class.getName());
    
    private SpeciesInfo species;
    private ExpressionMatrix matrix;
    
    // UI Components
    private JTextArea geneListArea;
    private JRadioButton includeRadio;
    private JRadioButton excludeRadio;
    private JCheckBox caseInsensitiveCheckBox;
    private JCheckBox partialMatchCheckBox;
    private JCheckBox preserveOrderCheckBox;
    private ExpressionDataTable resultTable;
    private JButton applyFilterButton;
    private JComboBox<String> importGeneSetComboBox;
    private JButton heatmapButton;
    private JButton exportFilteredButton;
    private JButton clearButton;
    private JLabel statusLabel;
    
    // Filtered results
    private Set<String> filteredGenes;
    private Map<String, Map<String, Double>> filteredData;
    private List<File> availableGeneSetFiles;
    
    /**
     * Constructor
     */
    public GeneFilterDialog(Window parent, SpeciesInfo species, ExpressionMatrix matrix) {
        super(parent, "Gene Filter - " + matrix.getName(), ModalityType.MODELESS);
        this.species = species;
        this.matrix = matrix;
        this.filteredGenes = new LinkedHashSet<>();
        this.filteredData = new HashMap<>();
        this.availableGeneSetFiles = new ArrayList<>();
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        setSize(800, 800); // Increased height from 700 to 800
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        // Set minimum visible rows after UI is setup
        SwingUtilities.invokeLater(() -> resultTable.setMinimumVisibleRows(5));
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Gene list input
        geneListArea = new JTextArea(8, 40);
        geneListArea.setLineWrap(true);
        geneListArea.setWrapStyleWord(true);
        geneListArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        geneListArea.setToolTipText("Enter gene IDs, one per line or separated by spaces/commas");
        
        // Filter options
        includeRadio = new JRadioButton("Include matching genes", true);
        excludeRadio = new JRadioButton("Exclude matching genes");
        ButtonGroup filterGroup = new ButtonGroup();
        filterGroup.add(includeRadio);
        filterGroup.add(excludeRadio);
        
        caseInsensitiveCheckBox = new JCheckBox("Case insensitive matching", true);
        partialMatchCheckBox = new JCheckBox("Allow partial matching", false);
        preserveOrderCheckBox = new JCheckBox("Preserve input order", true);
        preserveOrderCheckBox.setToolTipText("Keep filtered genes in the same order as input gene list (similar to TBtools Table Manipulator)");
        
        // Results table
        resultTable = new ExpressionDataTable();
        resultTable.setHeatmapColors(true); // Enable heatmap colors by default
        
        // Action buttons
        applyFilterButton = new JButton("Search");
        importGeneSetComboBox = new JComboBox<>();
        importGeneSetComboBox.setToolTipText("Import a Gene Set or resolve genes from a Region Set");
        refreshImportGeneSetOptions();
        heatmapButton = new JButton("Heatmap");
        exportFilteredButton = new JButton("Export Filtered Data");
        clearButton = new JButton("Clear");
        
        // Status label
        statusLabel = new JLabel("Ready");
        
        // Initially disable some buttons
        heatmapButton.setEnabled(false);
        exportFilteredButton.setEnabled(false);
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // Top: Instructions
        JLabel instructionLabel = new JLabel(
            "<html><b>Gene Filter</b> - Filter expression matrix by gene IDs<br>" + 
            "Enter gene IDs and configure filter options below.</html>");
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        add(instructionLabel, BorderLayout.NORTH);
        
        // Center: Main content panel with proper height distribution
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top part: Gene Input and Filter Options in a split layout
        JPanel topPanel = new JPanel(new BorderLayout(10, 0));
        
        // Gene Input
        JPanel geneInputPanel = createGeneInputPanel();
        geneInputPanel.setPreferredSize(new Dimension(0, 180)); // Fixed height for gene input
        topPanel.add(geneInputPanel, BorderLayout.NORTH);
        
        // Filter Options
        JPanel optionsPanel = createFilterOptionsPanel();
        optionsPanel.setPreferredSize(new Dimension(0, 120)); // Give checkbox row enough vertical space
        topPanel.add(optionsPanel, BorderLayout.SOUTH);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Center: Filter Results (gets remaining space)
        JPanel resultsPanel = createResultsPanel();
        mainPanel.add(resultsPanel, BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Bottom: Status and buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        
        bottomPanel.add(statusLabel, BorderLayout.WEST);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(clearButton);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(heatmapButton);
        buttonPanel.add(exportFilteredButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create gene input panel
     */
    private JPanel createGeneInputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Gene Input"));

        Dimension comboPreferredSize = importGeneSetComboBox.getPreferredSize();
        Dimension buttonPreferredSize = applyFilterButton.getPreferredSize();
        int controlWidth = Math.max(comboPreferredSize.width, 160);
        int controlHeight = Math.max(buttonPreferredSize.height, 28);
        Dimension alignedControlSize = new Dimension(controlWidth, controlHeight);

        importGeneSetComboBox.setPreferredSize(alignedControlSize);
        importGeneSetComboBox.setMinimumSize(alignedControlSize);
        importGeneSetComboBox.setMaximumSize(alignedControlSize);
        importGeneSetComboBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        applyFilterButton.setPreferredSize(alignedControlSize);
        applyFilterButton.setMinimumSize(alignedControlSize);
        applyFilterButton.setMaximumSize(alignedControlSize);
        applyFilterButton.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Center: JTextArea with scroll pane
        JScrollPane geneScrollPane = new JScrollPane(geneListArea);
        geneScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(geneScrollPane, BorderLayout.CENTER);

        // East: Gene Set selector above Search, with matched control height
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 0)); // Left padding

        buttonPanel.add(importGeneSetComboBox);
        buttonPanel.add(Box.createVerticalStrut(6));
        buttonPanel.add(applyFilterButton);

        panel.add(buttonPanel, BorderLayout.EAST);

        // South: Hint label
        JLabel hintLabel = new JLabel("Enter gene IDs (one per line or separated by spaces/commas)");
        hintLabel.setFont(SimpleGenomeHubStyle.italic(hintLabel.getFont(), 11f));
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
        panel.add(hintLabel, BorderLayout.SOUTH);

        return panel;
    }
    
    /**
     * Create filter results panel
     */
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Filter Results"));
        panel.setPreferredSize(new Dimension(0, 300)); // Set minimum preferred height
        
        // Add the enhanced table directly
        panel.add(resultTable, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create filter options panel
     */
    private JPanel createFilterOptionsPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("Filter Options"));
        panel.setLayout(new BorderLayout());
        
        // Radio buttons for include/exclude
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        radioPanel.setBorder(BorderFactory.createEmptyBorder(4, 5, 2, 5));
        radioPanel.add(includeRadio);
        radioPanel.add(Box.createHorizontalStrut(20)); // Add spacing
        radioPanel.add(excludeRadio);
        panel.add(radioPanel, BorderLayout.NORTH);
        
        // Checkboxes in a grid layout to ensure proper width allocation
        JPanel checkboxPanel = new JPanel(new GridLayout(1, 3, 10, 0));
        checkboxPanel.setBorder(BorderFactory.createEmptyBorder(4, 10, 8, 10));
        checkboxPanel.add(caseInsensitiveCheckBox);
        checkboxPanel.add(partialMatchCheckBox);
        checkboxPanel.add(preserveOrderCheckBox);
        panel.add(checkboxPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        applyFilterButton.addActionListener(e -> applyFilter());
        importGeneSetComboBox.addActionListener(e -> importSelectedGeneSet());
        heatmapButton.addActionListener(e -> generateHeatmapFromFilter());
        exportFilteredButton.addActionListener(e -> exportFilteredData());
        clearButton.addActionListener(e -> clearAll());
    }
    
    /**
     * Apply filter and prepare filtered data
     */
    private void applyFilter() {
        String geneListText = geneListArea.getText().trim();
        if (geneListText.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter gene IDs to filter.", "No Gene IDs", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Parse gene list
        Set<String> filterGenes = parseGeneList(geneListText);
        List<String> filterGenesList = new ArrayList<>(parseGeneListPreserveOrder(geneListText));
        if (filterGenes.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No valid gene IDs found.", "No Valid Genes", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Apply filter logic
        Set<String> allMatrixGenes = new HashSet<>(Arrays.asList(matrix.getGeneIds()));
        filteredGenes.clear();
        
        boolean includeMode = includeRadio.isSelected();
        boolean caseInsensitive = caseInsensitiveCheckBox.isSelected();
        boolean partialMatch = partialMatchCheckBox.isSelected();
        boolean preserveOrder = preserveOrderCheckBox.isSelected();
        
        if (preserveOrder && includeMode) {
            // Preserve input order for include mode
            for (String inputGene : filterGenesList) {
                for (String matrixGene : allMatrixGenes) {
                    Set<String> singleGeneSet = new HashSet<>();
                    singleGeneSet.add(inputGene);
                    if (matchesFilter(matrixGene, singleGeneSet, caseInsensitive, partialMatch)) {
                        filteredGenes.add(matrixGene);
                        break; // Only add the first match for each input gene
                    }
                }
            }
        } else if (includeMode) {
            // Include matching genes (original behavior)
            for (String matrixGene : allMatrixGenes) {
                if (matchesFilter(matrixGene, filterGenes, caseInsensitive, partialMatch)) {
                    filteredGenes.add(matrixGene);
                }
            }
        } else {
            // Exclude matching genes (preserve order not applicable for exclude mode)
            for (String matrixGene : allMatrixGenes) {
                if (!matchesFilter(matrixGene, filterGenes, caseInsensitive, partialMatch)) {
                    filteredGenes.add(matrixGene);
                }
            }
        }
        
        if (filteredGenes.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No genes match the filter criteria.", "No Results", JOptionPane.INFORMATION_MESSAGE);
            statusLabel.setText("No genes match filter criteria");
            return;
        }
        
        // Prepare filtered data (maintain order using LinkedHashMap)
        filteredData.clear();
        Map<String, Map<String, Double>> orderedFilteredData = new LinkedHashMap<>();
        for (String geneId : filteredGenes) {
            Map<String, Double> geneExpression = matrix.getGeneExpression(geneId);
            orderedFilteredData.put(geneId, new HashMap<>(geneExpression));
        }
        filteredData.putAll(orderedFilteredData);
        
        // Update the table with filtered data
        resultTable.setFilteredData(filteredGenes, filteredData, matrix.getSampleNames());
        
        statusLabel.setText("Filter applied: " + filteredGenes.size() + " genes ready for export");
        heatmapButton.setEnabled(true);
        exportFilteredButton.setEnabled(true);
    }
    
    /**
     * Check if a gene matches the filter criteria
     */
    private boolean matchesFilter(String matrixGene, Set<String> filterGenes, boolean caseInsensitive, boolean partialMatch) {
        String geneToCheck = caseInsensitive ? matrixGene.toLowerCase() : matrixGene;
        
        for (String filterGene : filterGenes) {
            String filterToCheck = caseInsensitive ? filterGene.toLowerCase() : filterGene;
            
            if (partialMatch) {
                if (geneToCheck.contains(filterToCheck) || filterToCheck.contains(geneToCheck)) {
                    return true;
                }
            } else {
                if (geneToCheck.equals(filterToCheck)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Parse gene list from text
     */
    private Set<String> parseGeneList(String text) {
        Set<String> genes = new LinkedHashSet<>();
        
        // Split by various delimiters
        String[] tokens = text.split("[\\n\\r\\s,;\\t]+");
        for (String token : tokens) {
            token = token.trim();
            if (!token.isEmpty()) {
                genes.add(token);
            }
        }
        
        return genes;
    }
    
    /**
     * Parse gene list from text preserving order
     */
    private List<String> parseGeneListPreserveOrder(String text) {
        List<String> genes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        
        // Split by various delimiters
        String[] tokens = text.split("[\\n\\r\\s,;\\t]+");
        for (String token : tokens) {
            token = token.trim();
            if (!token.isEmpty() && !seen.contains(token)) {
                genes.add(token);
                seen.add(token);
            }
        }
        
        return genes;
    }
    
    /**
     * Generate heatmap using HeatmapControl2
     */
    private void generateHeatmapFromFilter() {
        if (filteredData.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No filtered data available. Please apply filter first.",
                "No Data", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            // Create temporary matrix file for filtered genes
            File tempMatrixFile = createTempMatrixFileForHeatmap();
            
            // Use HeatmapControl2
            HeatmapControl2 heatmapControl = new HeatmapControl2();
            heatmapControl.setInFile(tempMatrixFile);
            
            // Show the heatmap directly without dialog
            heatmapControl.showMeTheHeatMap();
            
            // Clean up temp file after a delay
            javax.swing.Timer cleanupTimer = new javax.swing.Timer(30000, e -> {
                if (tempMatrixFile.exists()) {
                    tempMatrixFile.delete();
                }
            });
            cleanupTimer.setRepeats(false);
            cleanupTimer.start();
            
        } catch (Exception e) {
            logger.severe("Error generating heatmap: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Error generating heatmap: " + e.getMessage(),
                "Heatmap Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Create temporary matrix file for heatmap visualization
     */
    private File createTempMatrixFileForHeatmap() throws Exception {
        File tempFile = File.createTempFile("gene_filter_heatmap_", ".tsv");
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            // Write header
            writer.print("Gene_ID");
            String[] sampleNames = matrix.getSampleNames();
            for (String sample : sampleNames) {
                writer.print("\t" + sample);
            }
            writer.println();
            
            // Write filtered gene data
            for (String geneId : filteredGenes) {
                writer.print(geneId);
                Map<String, Double> geneData = filteredData.get(geneId);
                for (String sample : sampleNames) {
                    Double value = geneData.get(sample);
                    writer.print("\t" + (value != null ? value : 0.0));
                }
                writer.println();
            }
        }
        
        logger.info("Created temporary heatmap matrix file: " + tempFile.getAbsolutePath() + 
                   " with " + filteredGenes.size() + " genes");
        return tempFile;
    }
    
    /**
     * Export filtered data to file
     */
    private void exportFilteredData() {
        if (filteredData.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No filtered data available. Please apply filter first.",
                "No Data", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Tab-separated values (*.tsv)", "tsv"));
        fileChooser.setSelectedFile(new File(matrix.getName().replaceAll("\\s+", "_") + "_filtered.tsv"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".tsv")) {
                file = new File(file.getAbsolutePath() + ".tsv");
            }
            
            try {
                exportFilteredToFile(file);
                JOptionPane.showMessageDialog(this,
                    "Filtered data exported successfully to:\\n" + file.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                logger.warning("Export error: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Export error: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Export filtered data to file
     */
    private void exportFilteredToFile(File file) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Write header
            writer.print("Gene_ID");
            String[] sampleNames = matrix.getSampleNames();
            for (String sample : sampleNames) {
                writer.print("\t" + sample);
            }
            writer.println();
            
            // Write filtered data
            for (String geneId : filteredGenes) {
                writer.print(geneId);
                Map<String, Double> geneData = filteredData.get(geneId);
                for (String sample : sampleNames) {
                    Double value = geneData.get(sample);
                    writer.print("\t" + (value != null ? value : 0.0));
                }
                writer.println();
            }
        }
        
        logger.info("Exported filtered data: " + filteredGenes.size() + " genes to " + file.getAbsolutePath());
    }

    private void refreshImportGeneSetOptions() {
        availableGeneSetFiles = GeneSetImportSupport.loadAvailableSetFiles(species);

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(availableGeneSetFiles.isEmpty() ? "Import Gene Set (No Sets)" : "Import Gene Set");
        for (File geneSetFile : availableGeneSetFiles) {
            model.addElement(GeneSetImportSupport.buildImportLabel(geneSetFile));
        }

        importGeneSetComboBox.setModel(model);
        importGeneSetComboBox.setSelectedIndex(0);
        importGeneSetComboBox.setEnabled(!availableGeneSetFiles.isEmpty());
    }

    private void importSelectedGeneSet() {
        int selectedIndex = importGeneSetComboBox.getSelectedIndex();
        if (selectedIndex <= 0) {
            return;
        }

        int fileIndex = selectedIndex - 1;
        if (fileIndex < 0 || fileIndex >= availableGeneSetFiles.size()) {
            importGeneSetComboBox.setSelectedIndex(0);
            return;
        }

        File selectedFile = availableGeneSetFiles.get(fileIndex);
        importGeneSetComboBox.setEnabled(false);
        statusLabel.setText("Importing " + GeneSetFileSupport.extractDisplayName(selectedFile) + "...");

        SwingWorker<GeneSetImportSupport.ImportedGeneSet, Void> worker =
            new SwingWorker<GeneSetImportSupport.ImportedGeneSet, Void>() {
                @Override
                protected GeneSetImportSupport.ImportedGeneSet doInBackground() throws Exception {
                    return GeneSetImportSupport.importIds(species, selectedFile, GeneSetImportSupport.OutputIdType.TRANSCRIPT);
                }

                @Override
                protected void done() {
                    importGeneSetComboBox.setEnabled(!availableGeneSetFiles.isEmpty());
                    importGeneSetComboBox.setSelectedIndex(0);

                    try {
                        GeneSetImportSupport.ImportedGeneSet importedGeneSet = get();
                        List<String> importedIds = importedGeneSet.getIds();
                        geneListArea.setText(String.join("\n", importedIds));
                        geneListArea.setCaretPosition(0);

                        String sourceType = importedGeneSet.getSetKind() == GeneSetFileSupport.SetKind.REGION
                            ? "Region Set"
                            : "Gene Set";
                        statusLabel.setText("Imported " + importedIds.size() + " "
                            + importedGeneSet.getOutputIdType().getDisplayName().toLowerCase()
                            + "s from " + sourceType);
                    } catch (Exception ex) {
                        Throwable cause = ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                            ? ex.getCause()
                            : ex;

                        String message = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                        statusLabel.setText("Import failed");

                        int messageType = cause instanceof GeneSetImportSupport.NoGeneFoundException
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.ERROR_MESSAGE;

                        JOptionPane.showMessageDialog(
                            GeneFilterDialog.this,
                            "Failed to import selected set:\n" + message,
                            "Import Gene Set Failed",
                            messageType
                        );
                    }
                }
            };

        worker.execute();
    }

    /**
     * Clear all inputs and results
     */
    private void clearAll() {
        geneListArea.setText("");
        resultTable.setData(new ArrayList<>()); // Clear table data
        filteredGenes.clear();
        filteredData.clear();
        
        heatmapButton.setEnabled(false);
        exportFilteredButton.setEnabled(false);
        statusLabel.setText("Ready");
    }
}
