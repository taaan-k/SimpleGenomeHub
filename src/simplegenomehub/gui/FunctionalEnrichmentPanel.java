/*
 * Enhanced Functional Enrichment Analysis Panel
 * Comprehensive gene functional annotation enrichment analysis with TBtools integration
 */
package simplegenomehub.gui;

import simplegenomehub.model.*;
import simplegenomehub.model.GeneAnnotationData.AnnotationType;
import simplegenomehub.util.enrichment.*;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Enhanced panel for functional enrichment analysis of gene sets
 * Integrates with TBtools enrichment analysis algorithms
 * 
 * @author SimpleGenomeHub
 */
public class FunctionalEnrichmentPanel extends JDialog {
    
    private static final Logger logger = Logger.getLogger(FunctionalEnrichmentPanel.class.getName());
    
    private SpeciesInfo targetSpecies;
    private GeneAnnotationData annotationData;
    private EnrichmentEngine enrichmentEngine;
    
    // UI Components
    private JTextArea geneListArea;
    private JComboBox<AnnotationType> annotationTypeCombo;
    private JTable resultsTable;
    private EnhancedEnrichmentTableModel tableModel;
    private JButton loadGeneListButton;
    private JButton loadDemoButton;
    private JButton runAnalysisButton;
    private JButton exportResultsButton;
    private JButton visualizeButton;
    private JButton clearResultsButton;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel geneCountLabel;
    
    // Analysis parameters
    private JSpinner pValueSpinner;
    private JSpinner fdrSpinner;
    private JSpinner minGenesSpinner;
    private JSpinner minEnrichmentSpinner;
    private JCheckBox multipleTestingCheckBox;
    private JComboBox<String> correctionMethodCombo;
    
    // Results and analysis
    private EnrichmentAnalysisResult currentResult;
    private List<String> currentGeneList;
    
    // File chooser
    private JFileChooser fileChooser;
    
    /**
     * Constructor
     */
    public FunctionalEnrichmentPanel(Window parent, SpeciesInfo species) {
        super(parent, "Enhanced Functional Enrichment Analysis - " + species.getSpeciesName(), ModalityType.APPLICATION_MODAL);
        this.targetSpecies = species;
        this.annotationData = species.getFunctionalAnnotations();
        this.currentGeneList = new ArrayList<>();
        
        // Initialize enrichment engine
        try {
            this.enrichmentEngine = new EnrichmentEngine(annotationData);
            logger.info("Enrichment engine initialized successfully");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize enrichment engine", e);
            JOptionPane.showMessageDialog(this, 
                "Failed to initialize enrichment analysis engine: " + e.getMessage(),
                "Initialization Error", JOptionPane.ERROR_MESSAGE);
        }
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        setSize(1200, 800);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        
        // Initialize file chooser
        fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt, *.tsv, *.csv)", "txt", "tsv", "csv"));
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Gene input area
        geneListArea = new JTextArea(10, 50);
        geneListArea.setLineWrap(true);
        geneListArea.setWrapStyleWord(true);
        geneListArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        geneListArea.setToolTipText("Enter gene IDs, one per line or separated by commas/spaces/tabs");
        
        // Gene count label
        geneCountLabel = new JLabel("Genes: 0");
        geneCountLabel.setFont(SimpleGenomeHubStyle.bold(geneCountLabel.getFont()));
        
        // Annotation type selection
        annotationTypeCombo = new JComboBox<>();
        updateAnnotationTypeCombo();
        
        // Results table with enhanced model
        tableModel = new EnhancedEnrichmentTableModel();
        resultsTable = new JTable(tableModel);
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        resultsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resultsTable.getTableHeader().setReorderingAllowed(false);
        
        // Configure table columns
        setupTableColumns();
        
        // Action buttons
        loadGeneListButton = new JButton("Load from File");
        loadGeneListButton.setToolTipText("Load gene list from text file");
        
        loadDemoButton = new JButton("Load Demo Data");
        loadDemoButton.setToolTipText("Load ModuleGID_darkred.txt for testing");
        
        runAnalysisButton = new JButton("Run Enrichment Analysis");
        runAnalysisButton.setFont(SimpleGenomeHubStyle.bold(runAnalysisButton.getFont()));
        runAnalysisButton.setBackground(new Color(51, 122, 183));
        runAnalysisButton.setForeground(Color.WHITE);
        
        exportResultsButton = new JButton("Export Results");
        exportResultsButton.setEnabled(false);
        
        visualizeButton = new JButton("Visualize");
        visualizeButton.setEnabled(false);
        visualizeButton.setToolTipText("Generate enrichment visualization plots");
        
        clearResultsButton = new JButton("Clear Results");
        clearResultsButton.setEnabled(false);
        
        // Progress and status
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        
        statusLabel = new JLabel("Ready for enrichment analysis");
        
        // Enhanced analysis parameters
        pValueSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.001, 1.0, 0.001));
        fdrSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.001, 1.0, 0.001));
        minGenesSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 50, 1));
        minEnrichmentSpinner = new JSpinner(new SpinnerNumberModel(1.5, 1.0, 10.0, 0.1));
        
        multipleTestingCheckBox = new JCheckBox("Apply multiple testing correction", true);
        
        correctionMethodCombo = new JComboBox<>(new String[]{
            "Benjamini-Hochberg (FDR)", "Bonferroni", "Holm", "None"
        });
        correctionMethodCombo.setSelectedIndex(0);
        SimpleGenomeHubUi.setComboBoxDisplayWidth(correctionMethodCombo, 240);
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // Top: Instructions and species info
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);
        
        // Center: Main content in tabs
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(SimpleGenomeHubStyle.bold(tabbedPane.getFont(), 12f));
        
        // Tab 1: Gene Input
        tabbedPane.addTab("Gene Input", createGeneInputPanel());
        
        // Tab 2: Analysis Settings
        tabbedPane.addTab("Analysis Settings", createSettingsPanel());
        
        // Tab 3: Results
        tabbedPane.addTab("Enrichment Results", createResultsPanel());
        
        // Tab 4: Summary & Export
        tabbedPane.addTab("Summary & Export", createSummaryPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // Bottom: Status and controls
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create header panel with instructions
     */
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        JLabel titleLabel = new JLabel(
            "<html><b><font size='+1'>Enhanced Functional Enrichment Analysis</font></b></html>");
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel instructionLabel = new JLabel(
            "<html>Analyze gene sets for functional enrichment using GO, KEGG, and custom annotations.<br>" +
            "Species: <b>" + targetSpecies.getSpeciesName() + " (" + targetSpecies.getVersion() + ")</b><br>" +
            "Available annotations: " + getAvailableAnnotationsSummary() + "</html>");
        instructionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        instructionLabel.setFont(SimpleGenomeHubStyle.plain(instructionLabel.getFont()));
        
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(instructionLabel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create bottom panel with status and main controls
     */
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
        
        // Status panel
        JPanel statusPanel = new JPanel(new BorderLayout(5, 5));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(progressBar, BorderLayout.CENTER);
        bottomPanel.add(statusPanel, BorderLayout.CENTER);
        
        // Main button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(runAnalysisButton);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(visualizeButton);
        buttonPanel.add(exportResultsButton);
        buttonPanel.add(clearResultsButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
        return bottomPanel;
    }
    
    /**
     * Update annotation type combo based on available annotations
     */
    private void updateAnnotationTypeCombo() {
        annotationTypeCombo.removeAllItems();
        
        // Check which annotation types are available
        if (annotationData != null) {
            Map<AnnotationType, Integer> counts = annotationData.getAnnotationCounts();
            
            for (AnnotationType type : AnnotationType.values()) {
                Integer count = counts.get(type);
                if (count != null && count > 0) {
                    annotationTypeCombo.addItem(type);
                }
            }
        }
        
        // If no annotations available, add placeholder
        if (annotationTypeCombo.getItemCount() == 0) {
            annotationTypeCombo.addItem(AnnotationType.GO); // Default fallback
        }
        
        annotationTypeCombo.setRenderer(new AnnotationTypeRenderer());
        SimpleGenomeHubUi.setComboBoxMinimumWidth(annotationTypeCombo, 220);
    }
    
    /**
     * Get summary of available annotations
     */
    private String getAvailableAnnotationsSummary() {
        if (annotationData == null) {
            return "No annotations loaded";
        }
        
        Map<AnnotationType, Integer> counts = annotationData.getAnnotationCounts();
        List<String> summaries = new ArrayList<>();
        
        for (AnnotationType type : AnnotationType.values()) {
            Integer count = counts.get(type);
            if (count != null && count > 0) {
                summaries.add(type.getDescription() + " (" + count + ")");
            }
        }
        
        return summaries.isEmpty() ? "No annotations available" : String.join(", ", summaries);
    }
    
    /**
     * Setup table columns with appropriate widths and renderers
     */
    private void setupTableColumns() {
        TableColumnModel columnModel = resultsTable.getColumnModel();
        
        // Set column widths
        int[] columnWidths = {300, 100, 80, 80, 100, 100, 80, 100, 200, 80};
        for (int i = 0; i < Math.min(columnWidths.length, columnModel.getColumnCount()); i++) {
            TableColumn column = columnModel.getColumn(i);
            column.setPreferredWidth(columnWidths[i]);
            column.setMinWidth(50);
        }
        
        // Add custom renderers
        resultsTable.setDefaultRenderer(Double.class, new ScientificNotationRenderer());
        resultsTable.setDefaultRenderer(String.class, new MultiLineRenderer());
        
        // Enable sorting
        resultsTable.setAutoCreateRowSorter(true);
    }
    
    /**
     * Create gene input panel
     */
    private JPanel createGeneInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top: Instructions and controls
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        
        JLabel instructionLabel = new JLabel(
            "<html><b>Gene Input</b><br>" +
            "Enter gene IDs below, one per line or separated by commas/spaces/tabs.<br>" +
            "You can also load a gene list from a file.</html>");
        topPanel.add(instructionLabel, BorderLayout.NORTH);
        
        // Gene input controls
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.add(new JLabel("Annotation Type:"));
        controlPanel.add(annotationTypeCombo);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(loadGeneListButton);
        controlPanel.add(loadDemoButton);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(geneCountLabel);
        
        topPanel.add(controlPanel, BorderLayout.CENTER);
        panel.add(topPanel, BorderLayout.NORTH);
        
        // Center: Gene input area
        JScrollPane scrollPane = new JScrollPane(geneListArea);
        scrollPane.setBorder(new TitledBorder("Gene List"));
        scrollPane.setPreferredSize(new Dimension(600, 300));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Bottom: Gene list statistics
        JPanel statsPanel = createGeneStatsPanel();
        panel.add(statsPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Create gene statistics panel
     */
    private JPanel createGeneStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 10, 5));
        panel.setBorder(new TitledBorder("Gene List Statistics"));
        
        // Will be populated dynamically when genes are entered
        return panel;
    }
    
    /**
     * Create settings panel
     */
    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Analysis parameters
        JPanel paramsPanel = new JPanel(new GridBagLayout());
        paramsPanel.setBorder(new TitledBorder("Analysis Parameters"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // P-value threshold
        gbc.gridx = 0; gbc.gridy = 0;
        paramsPanel.add(new JLabel("P-value threshold:"), gbc);
        gbc.gridx = 1;
        paramsPanel.add(pValueSpinner, gbc);
        gbc.gridx = 2;
        paramsPanel.add(new JLabel("(Raw p-value cutoff)"), gbc);
        
        // FDR threshold
        gbc.gridx = 0; gbc.gridy = 1;
        paramsPanel.add(new JLabel("FDR threshold:"), gbc);
        gbc.gridx = 1;
        paramsPanel.add(fdrSpinner, gbc);
        gbc.gridx = 2;
        paramsPanel.add(new JLabel("(Adjusted p-value cutoff)"), gbc);
        
        // Minimum genes
        gbc.gridx = 0; gbc.gridy = 2;
        paramsPanel.add(new JLabel("Minimum genes:"), gbc);
        gbc.gridx = 1;
        paramsPanel.add(minGenesSpinner, gbc);
        gbc.gridx = 2;
        paramsPanel.add(new JLabel("(Min genes per term)"), gbc);
        
        // Minimum enrichment
        gbc.gridx = 0; gbc.gridy = 3;
        paramsPanel.add(new JLabel("Min enrichment ratio:"), gbc);
        gbc.gridx = 1;
        paramsPanel.add(minEnrichmentSpinner, gbc);
        gbc.gridx = 2;
        paramsPanel.add(new JLabel("(Fold enrichment cutoff)"), gbc);
        
        // Multiple testing correction
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        paramsPanel.add(multipleTestingCheckBox, gbc);
        
        // Correction method
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 1;
        paramsPanel.add(new JLabel("Correction method:"), gbc);
        gbc.gridx = 1;
        paramsPanel.add(correctionMethodCombo, gbc);
        
        panel.add(paramsPanel, BorderLayout.NORTH);
        
        // Analysis info
        JPanel infoPanel = createAnalysisInfoPanel();
        panel.add(infoPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create analysis info panel
     */
    private JPanel createAnalysisInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Analysis Information"));
        
        JTextArea infoArea = new JTextArea(10, 40);
        infoArea.setEditable(false);
        infoArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        infoArea.setText(
            "Statistical Method: Hypergeometric Test\\n" +
            "Multiple Testing: Benjamini-Hochberg FDR\\n" +
            "Background: All annotated genes in species\\n\\n" +
            "Analysis Engine: TBtools Enhanced\\n" +
            "Integration: SimpleGenomeHub v1.0\\n\\n" +
            "Supported Databases:\\n" +
            "- Gene Ontology (GO): Biological Process, Molecular Function, Cellular Component\\n" +
            "- KEGG Pathways: Metabolic and signaling pathways\\n" +
            "- Custom Annotations: User-defined functional categories\\n\\n" +
            "Requirements:\\n" +
            "- Minimum 3 genes per test set\\n" +
            "- At least 3 genes must overlap with each term\\n" +
            "- Genes must be present in background annotation database"
        );
        
        JScrollPane scrollPane = new JScrollPane(infoArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create results panel
     */
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Results table
        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setBorder(new TitledBorder("Enrichment Results"));
        tableScrollPane.setPreferredSize(new Dimension(800, 400));
        panel.add(tableScrollPane, BorderLayout.CENTER);
        
        // Results controls
        JPanel controlsPanel = new JPanel(new FlowLayout());
        controlsPanel.add(new JLabel("Filter results:"));
        // Add filter controls here if needed
        
        panel.add(controlsPanel, BorderLayout.NORTH);
        
        return panel;
    }
    
    /**
     * Create summary and export panel
     */
    private JPanel createSummaryPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Summary text area
        JTextArea summaryArea = new JTextArea(15, 50);
        summaryArea.setEditable(false);
        summaryArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        summaryArea.setText("No analysis results available.\\nRun enrichment analysis to see summary.");
        
        JScrollPane scrollPane = new JScrollPane(summaryArea);
        scrollPane.setBorder(new TitledBorder("Analysis Summary"));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Export options
        JPanel exportPanel = new JPanel(new GridLayout(3, 2, 10, 5));
        exportPanel.setBorder(new TitledBorder("Export Options"));
        
        JButton exportTSVButton = new JButton("Export TSV");
        JButton exportExcelButton = new JButton("Export Excel");
        JButton exportSummaryButton = new JButton("Export Summary");
        JButton saveSessionButton = new JButton("Save Session");
        
        exportPanel.add(exportTSVButton);
        exportPanel.add(exportExcelButton);
        exportPanel.add(exportSummaryButton);
        exportPanel.add(saveSessionButton);
        
        panel.add(exportPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        // Gene list text area - update gene count on change
        geneListArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateGeneCount(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateGeneCount(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateGeneCount(); }
        });
        
        // Load gene list button
        loadGeneListButton.addActionListener(e -> loadGeneListFromFile());
        
        // Load demo button
        loadDemoButton.addActionListener(e -> loadDemoGeneList());
        
        // Run analysis button
        runAnalysisButton.addActionListener(e -> runEnrichmentAnalysis());
        
        // Export results button
        exportResultsButton.addActionListener(e -> exportResults());
        
        // Visualize button
        visualizeButton.addActionListener(e -> visualizeResults());
        
        // Clear results button
        clearResultsButton.addActionListener(e -> clearResults());
        
        // Multiple testing checkbox
        multipleTestingCheckBox.addActionListener(e -> {
            correctionMethodCombo.setEnabled(multipleTestingCheckBox.isSelected());
        });
    }
    
    /**
     * Update gene count display
     */
    private void updateGeneCount() {
        SwingUtilities.invokeLater(() -> {
            List<String> genes = parseGeneList();
            geneCountLabel.setText("Genes: " + genes.size());
            currentGeneList = genes;
            
            // Enable/disable run button based on gene count
            runAnalysisButton.setEnabled(genes.size() >= 3);
        });
    }
    
    /**
     * Parse gene list from text area
     */
    private List<String> parseGeneList() {
        String text = geneListArea.getText().trim();
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Split by various delimiters and clean
        Set<String> geneSet = new HashSet<>();
        String[] tokens = text.split("[,\\s\\t\\n\\r]+");
        
        for (String token : tokens) {
            String gene = token.trim();
            if (!gene.isEmpty()) {
                geneSet.add(gene);
            }
        }
        
        return new ArrayList<>(geneSet);
    }
    
    /**
     * Load gene list from file
     */
    private void loadGeneListFromFile() {
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                List<String> genes = readGeneListFromFile(file);
                geneListArea.setText(String.join("\\n", genes));
                statusLabel.setText("Loaded " + genes.size() + " genes from " + file.getName());
                logger.info("Loaded gene list from file: " + file.getAbsolutePath());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load gene list from file", e);
                JOptionPane.showMessageDialog(this,
                    "Failed to load gene list: " + e.getMessage(),
                    "File Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Load demo gene list
     */
    private void loadDemoGeneList() {
        File demoFile = new File("I:\\\\GenomeDB\\\\Ananas_comosus.GP\\\\extracted\\\\ModuleGID_darkred.txt");
        if (demoFile.exists()) {
            try {
                List<String> genes = readGeneListFromFile(demoFile);
                geneListArea.setText(String.join("\\n", genes));
                statusLabel.setText("Loaded demo gene set: " + genes.size() + " genes from darkred module");
                logger.info("Loaded demo gene list: " + genes.size() + " genes");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to load demo gene list", e);
                JOptionPane.showMessageDialog(this,
                    "Failed to load demo data: " + e.getMessage(),
                    "Demo Data Error", JOptionPane.WARNING_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Demo file not found: " + demoFile.getAbsolutePath(),
                "Demo Data Not Available", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    /**
     * Read gene list from file
     */
    private List<String> readGeneListFromFile(File file) throws IOException {
        List<String> genes = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    // Handle various file formats
                    if (line.contains("\\t")) {
                        genes.add(line.split("\\t")[0].trim()); // Take first column
                    } else if (line.contains(",")) {
                        genes.add(line.split(",")[0].trim()); // Take first column
                    } else {
                        genes.add(line);
                    }
                }
            }
        }
        
        return genes;
    }
    
    /**
     * Run enrichment analysis
     */
    private void runEnrichmentAnalysis() {
        if (currentGeneList.size() < 3) {
            JOptionPane.showMessageDialog(this,
                "Please enter at least 3 genes for enrichment analysis.",
                "Insufficient Genes", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (enrichmentEngine == null) {
            JOptionPane.showMessageDialog(this,
                "Enrichment engine not initialized. Please restart the application.",
                "Engine Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Get selected annotation type
        AnnotationType selectedType = (AnnotationType) annotationTypeCombo.getSelectedItem();
        if (selectedType == null) {
            JOptionPane.showMessageDialog(this,
                "Please select an annotation type for analysis.",
                "No Annotation Type", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Update engine parameters
        updateEngineParameters();
        
        // Run analysis in background
        SwingWorker<EnrichmentAnalysisResult, Void> worker = new SwingWorker<EnrichmentAnalysisResult, Void>() {
            @Override
            protected EnrichmentAnalysisResult doInBackground() throws Exception {
                return enrichmentEngine.performEnrichment(currentGeneList, selectedType);
            }
            
            @Override
            protected void done() {
                try {
                    currentResult = get();
                    displayResults(currentResult);
                    
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Enrichment analysis failed", e);
                    JOptionPane.showMessageDialog(FunctionalEnrichmentPanel.this,
                        "Analysis failed: " + e.getMessage(),
                        "Analysis Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    // Hide progress bar and restore UI
                    progressBar.setVisible(false);
                    runAnalysisButton.setEnabled(true);
                    statusLabel.setText("Analysis completed");
                }
            }
        };
        
        // Show progress and disable UI
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Running enrichment analysis...");
        runAnalysisButton.setEnabled(false);
        statusLabel.setText("Analyzing " + currentGeneList.size() + " genes...");
        
        worker.execute();
    }
    
    /**
     * Update enrichment engine parameters
     */
    private void updateEngineParameters() {
        if (enrichmentEngine != null) {
            enrichmentEngine.setPValueThreshold((Double) pValueSpinner.getValue());
            enrichmentEngine.setFdrThreshold((Double) fdrSpinner.getValue());
            enrichmentEngine.setMinGeneCount((Integer) minGenesSpinner.getValue());
            enrichmentEngine.setUseMultipleTestingCorrection(multipleTestingCheckBox.isSelected());
            
            // Set correction method
            String method = (String) correctionMethodCombo.getSelectedItem();
            if (method.contains("Benjamini")) {
                enrichmentEngine.setCorrectionMethod("BH");
            } else if (method.contains("Bonferroni")) {
                enrichmentEngine.setCorrectionMethod("Bonferroni");
            } else if (method.contains("Holm")) {
                enrichmentEngine.setCorrectionMethod("Holm");
            } else {
                enrichmentEngine.setCorrectionMethod("None");
            }
        }
    }
    
    /**
     * Display enrichment results
     */
    private void displayResults(EnrichmentAnalysisResult result) {
        if (result == null || !result.isSuccessful()) {
            String message = result != null ? result.getMessage() : "Unknown error";
            JOptionPane.showMessageDialog(this,
                "Analysis failed: " + message,
                "Analysis Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // Update table model
        tableModel.setResults(result.getEnrichmentResults());
        
        // Update summary (if summary panel exists)
        updateSummaryDisplay(result);
        
        // Enable export and visualization buttons
        exportResultsButton.setEnabled(true);
        visualizeButton.setEnabled(result.hasSignificantResults());
        clearResultsButton.setEnabled(true);
        
        // Show results summary in status
        statusLabel.setText(String.format("Found %d significant terms (%.1f%% of %d tested)",
                                         result.getSignificantTerms(),
                                         (double) result.getSignificantTerms() / result.getTotalTermsTested() * 100,
                                         result.getTotalTermsTested()));
        
        // Log results
        logger.info("Enrichment analysis completed: " + result.getSignificantTerms() + 
                   " significant terms found");
    }
    
    /**
     * Update summary display
     */
    private void updateSummaryDisplay(EnrichmentAnalysisResult result) {
        // This would update the summary tab with detailed results
        // Implementation depends on the summary panel components
    }
    
    /**
     * Export results
     */
    private void exportResults() {
        if (currentResult == null || currentResult.getEnrichmentResults().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No results to export. Please run analysis first.",
                "No Results", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("TSV files (*.tsv)", "tsv"));
        fileChooser.setSelectedFile(new File("enrichment_results.tsv"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                String content = currentResult.exportToTSV();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    writer.write(content);
                }
                statusLabel.setText("Results exported to " + file.getName());
                logger.info("Results exported to: " + file.getAbsolutePath());
                
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to export results", e);
                JOptionPane.showMessageDialog(this,
                    "Failed to export results: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Visualize results
     */
    private void visualizeResults() {
        if (currentResult == null || !currentResult.hasSignificantResults()) {
            JOptionPane.showMessageDialog(this,
                "No significant results to visualize.",
                "No Results", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // TODO: Implement visualization using TBtools JIGplot components
        JOptionPane.showMessageDialog(this,
            "Visualization feature will be implemented in the next phase.",
            "Feature Coming Soon", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Clear results
     */
    private void clearResults() {
        tableModel.clearResults();
        currentResult = null;
        exportResultsButton.setEnabled(false);
        visualizeButton.setEnabled(false);
        clearResultsButton.setEnabled(false);
        statusLabel.setText("Results cleared");
    }
    
    // Supporting renderer and model classes
    
    /**
     * Custom renderer for annotation types
     */
    private static class AnnotationTypeRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof AnnotationType) {
                AnnotationType type = (AnnotationType) value;
                setText(type.getDescription());
                setToolTipText(type.getDescription());
            }
            
            return this;
        }
    }
    
    /**
     * Scientific notation renderer for p-values
     */
    private static class ScientificNotationRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (value instanceof Double) {
                double val = (Double) value;
                if (val < 0.001) {
                    setText(String.format("%.2e", val));
                } else {
                    setText(String.format("%.4f", val));
                }
                setHorizontalAlignment(SwingConstants.RIGHT);
            }
            
            return this;
        }
    }
    
    /**
     * Multi-line cell renderer
     */
    private static class MultiLineRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            if (value instanceof String) {
                String text = (String) value;
                if (text.length() > 50) {
                    setToolTipText("<html>" + text.replace(",", ",<br>") + "</html>");
                }
            }
            
            return this;
        }
    }
}
