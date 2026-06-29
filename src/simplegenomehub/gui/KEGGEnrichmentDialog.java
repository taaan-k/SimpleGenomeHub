/*
 * KEGG Enrichment Analysis Dialog with TBtools Integration
 * Implements TBtools native KEGG enrichment analysis methods
 */
package simplegenomehub.gui;

import simplegenomehub.config.SimpleGenomeHubConfig;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.GeneAnnotationData;
import simplegenomehub.util.enrichment.EnhancedEnrichmentResult;
import simplegenomehub.util.fileio.KeggBackendManager;

// TBtools KEGG enrichment imports
import biocjava.bioDoer.Kegg.AdvancedForEnrichment.KeggEnrichment;
import biocjava.bioDoer.Kegg.AdvancedForEnrichment.Kterm;
import biocjava.bioIO.GeneOntology.EnrichMent.Enricher;
import toolsKit.PadjustBhMethod;

// TBtools visualization imports
import biocjava.bioDoer.JIGplotToolkit.EnrichmentAnalysisGraph.Barplot;

// TBtools drag-drop imports
import toolsKit.GUItools.DragDropFileEnterListener;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import javax.swing.DefaultListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

/**
 * KEGG富集分析对话框 - 集成TBtools原生KEGG富集分析引擎
 * 
 * 主要特性：
 * 1. TBtools KeggEnrichment类集成
 * 2. 简洁的TBtools风格用户界面
 * 3. KEGG通路分类和层级分析
 * 4. 增强的结果表格显示
 * 5. 单元格选择和复制功能
 * 
 * @author SimpleGenomeHub
 */
public class KEGGEnrichmentDialog extends JDialog {
    
    private final SpeciesInfo targetSpecies;
    private JTextArea geneListTextArea;
    private JComboBox<String> keggCategoryComboBox;
    private JComboBox<String> importGeneSetComboBox;
    private JSpinner minGeneCountSpinner;
    private JSpinner maxPValueSpinner;
    private JTable resultsTable;
    private KEGGEnrichmentTableModel tableModel;
    private JButton runAnalysisButton;
    private JButton loadGeneListButton;
    private JButton clearGeneListButton;
    private JButton exportButton;
    private JButton visualizeButton;
    private JButton clearButton;
    private JLabel statusLabel;
    private JLabel geneCountLabel;
    private JProgressBar progressBar;
    private List<File> availableGeneSetFiles;
    
    // TBtools KEGG enrichment components
    private KeggEnrichment tbToolsKEGGEnricher;
    private File keggReferenceFile;
    private File gene2KeggFile;
    
    // KEGG analysis parameters
    private static final String[] KEGG_CATEGORIES = {
        "All", "Pathway", "Module", "MainClass", "SubClass"
    };
    
    public KEGGEnrichmentDialog(Frame parent, SpeciesInfo species) {
        super(parent, "KEGG Enrichment Analysis - " + species.getSpeciesDirectoryName(), false);
        this.targetSpecies = species;
        this.availableGeneSetFiles = new ArrayList<>();
        
        initComponents();
        initializeTBtoolsComponents();
        setupEventListeners();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1400, 800);
        setLocationRelativeTo(parent);
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // Top: Header panel with title and species info
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);
        
        // Center: Main content area with left-right split
        JPanel inputPanel = createInputPanel();
        JPanel resultsPanel = createResultsPanel();

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, inputPanel, resultsPanel);
        inputPanel.setMinimumSize(new Dimension(500, 300));
        resultsPanel.setMinimumSize(new Dimension(360, 300));
        mainSplitPane.setResizeWeight(0.42);
        mainSplitPane.setOneTouchExpandable(false);
        mainSplitPane.setDividerLocation(520);
        SimpleGenomeHubUi.styleSplitPane(mainSplitPane);
        add(mainSplitPane, BorderLayout.CENTER);
        
        // Bottom: Status and control buttons
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create header panel with title and species information
     */
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        JLabel titleLabel = new JLabel("KEGG Enrichment Analysis");
        titleLabel.setFont(SimpleGenomeHubStyle.bold(titleLabel.getFont(), 16f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        JLabel speciesLabel = new JLabel("Species: " + targetSpecies.getSpeciesName() + " (" + targetSpecies.getVersion() + ")");
        speciesLabel.setHorizontalAlignment(SwingConstants.CENTER);
        speciesLabel.setFont(SimpleGenomeHubStyle.italic(speciesLabel.getFont()));
        
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(speciesLabel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create input panel with gene list and parameters
     */
    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setPreferredSize(new Dimension(520, 500));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        // Gene list area
        JPanel genePanel = new JPanel(new BorderLayout(5, 5));
        genePanel.setBorder(new TitledBorder("Gene List Input"));
        
        // Initialize text area
        geneListTextArea = new JTextArea(8, 30);
        geneListTextArea.setLineWrap(true);
        geneListTextArea.setWrapStyleWord(true);
        geneListTextArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        geneListTextArea.setBorder(BorderFactory.createLoweredBevelBorder());
        geneListTextArea.setToolTipText("Enter gene IDs, one per line or separated by commas/spaces. Drag and drop file here.");
        
        // 添加拖拽功能
        geneListTextArea.setDropTarget(new DropTarget(this, DnDConstants.ACTION_REFERENCE,
                new DragDropFileEnterListener(geneListTextArea), true));
        geneListTextArea.setDragEnabled(true);
        
        // Gene count label
        geneCountLabel = new JLabel("Genes: 0");
        geneCountLabel.setFont(SimpleGenomeHubStyle.bold(geneCountLabel.getFont()));
        geneCountLabel.setForeground(new Color(0, 100, 0));
        
        // Gene list controls
        JPanel geneControlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints controlsGbc = new GridBagConstraints();
        controlsGbc.insets = new Insets(4, 4, 4, 4);
        controlsGbc.anchor = GridBagConstraints.WEST;
        controlsGbc.fill = GridBagConstraints.HORIZONTAL;
        
        loadGeneListButton = new JButton("Load Gene List");
        loadGeneListButton.setToolTipText("Load gene list from file");

        importGeneSetComboBox = new JComboBox<>();
        importGeneSetComboBox.setToolTipText("Import a Gene Set or resolve genes from a Region Set");
        refreshImportGeneSetOptions();
        
        clearGeneListButton = new JButton("Clear");
        
        controlsGbc.gridx = 0;
        controlsGbc.gridy = 0;
        controlsGbc.weightx = 0;
        geneControlPanel.add(loadGeneListButton, controlsGbc);
        
        controlsGbc.gridx = 0;
        controlsGbc.gridy = 1;
        controlsGbc.weightx = 1.0;
        geneControlPanel.add(importGeneSetComboBox, controlsGbc);
        
        controlsGbc.gridx = 1;
        controlsGbc.weightx = 0;
        geneControlPanel.add(clearGeneListButton, controlsGbc);
        
        controlsGbc.gridx = 2;
        geneControlPanel.add(geneCountLabel, controlsGbc);
        
        JScrollPane geneScrollPane = new JScrollPane(geneListTextArea);
        geneScrollPane.setPreferredSize(new Dimension(480, 210));
        
        genePanel.add(geneControlPanel, BorderLayout.NORTH);
        genePanel.add(geneScrollPane, BorderLayout.CENTER);
        
        // Parameter settings area
        JPanel paramPanel = createParameterPanel();
        
        panel.add(genePanel, BorderLayout.CENTER);
        panel.add(paramPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Create parameter panel
     */
    private JPanel createParameterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Analysis Parameters"));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // KEGG category selection
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("KEGG Category:"), gbc);
        gbc.gridx = 1;
        keggCategoryComboBox = new JComboBox<>(KEGG_CATEGORIES);
        keggCategoryComboBox.setSelectedItem("Pathway");
        keggCategoryComboBox.setToolTipText("Select KEGG classification level for analysis");
        SimpleGenomeHubUi.setComboBoxDisplayWidth(keggCategoryComboBox, 180);
        panel.add(keggCategoryComboBox, gbc);
        
        // Max P-value
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Max P-value:"), gbc);
        gbc.gridx = 1;
        maxPValueSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.001, 1.0, 0.01));
        maxPValueSpinner.setToolTipText("Maximum p-value for filtering (default 0.05)");
        panel.add(maxPValueSpinner, gbc);
        
        // Minimum gene count
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Min Gene Count:"), gbc);
        gbc.gridx = 1;
        minGeneCountSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 100, 1));
        minGeneCountSpinner.setToolTipText("Minimum number of genes required in a term");
        panel.add(minGeneCountSpinner, gbc);
        
        // Analysis button
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        runAnalysisButton = new JButton("Run KEGG Enrichment");
        runAnalysisButton.setBackground(new Color(51, 122, 183));
        runAnalysisButton.setForeground(Color.WHITE);
        runAnalysisButton.setFont(SimpleGenomeHubStyle.bold(runAnalysisButton.getFont()));
        runAnalysisButton.setToolTipText("Start KEGG enrichment analysis using TBtools engine");
        panel.add(runAnalysisButton, gbc);
        
        return panel;
    }
    
    /**
     * Create results panel
     */
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new TitledBorder("KEGG Enrichment Results"));
        
        // Create table model and table
        tableModel = new KEGGEnrichmentTableModel();
        resultsTable = new JTable(tableModel);
        
        // Enhanced table configuration (BLAST-style)
        resultsTable.setRowSelectionAllowed(true);
        resultsTable.setColumnSelectionAllowed(true);
        resultsTable.setCellSelectionEnabled(true);
        resultsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resultsTable.getColumnModel().setSelectionModel(new DefaultListSelectionModel());
        resultsTable.getColumnModel().getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        resultsTable.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
        resultsTable.getTableHeader().setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        
        // Set enhanced selection colors (light orange like BLAST)
        resultsTable.setSelectionBackground(new Color(255, 239, 213)); // Light orange
        resultsTable.setSelectionForeground(Color.BLACK); // Keep text readable
        
        // Enable row sorting
        TableRowSorter<KEGGEnrichmentTableModel> sorter = new TableRowSorter<>(tableModel);
        resultsTable.setRowSorter(sorter);
        
        // Make table focusable
        resultsTable.setFocusable(true);
        resultsTable.setRequestFocusEnabled(true);
        
        // Configure column widths and renderers
        configureTableColumns();
        
        // Setup enhanced interactions (BLAST-style)
        setupTableInteractions();
        
        // Enable copying
        resultsTable.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke("control C"), "copy");
        resultsTable.getActionMap().put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copySelectedCells();
            }
        });
        
        // Results table scroll pane
        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setPreferredSize(new Dimension(800, 450));
        panel.add(tableScrollPane, BorderLayout.CENTER);
        
        // Results toolbar
        JPanel toolbarPanel = new JPanel(new FlowLayout());
        
        exportButton = new JButton("Export Results");
        exportButton.setEnabled(false);
        exportButton.setToolTipText("Export enrichment results to TSV file");
        
        visualizeButton = new JButton("Visualize");
        visualizeButton.setEnabled(false);
        visualizeButton.setToolTipText("Create enrichment bar plot visualization");
        visualizeButton.addActionListener(e -> createVisualization());

        clearButton = new JButton("Clear");
        clearButton.setEnabled(false);

        toolbarPanel.add(exportButton);
        toolbarPanel.add(visualizeButton);
        toolbarPanel.add(clearButton);

        panel.add(toolbarPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Progress bar
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        progressBar.setStringPainted(true);
        
        // Status label
        statusLabel = new JLabel("Ready for KEGG enrichment analysis");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        
        panel.add(progressBar, BorderLayout.NORTH);
        panel.add(statusLabel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void configureTableColumns() {
        TableColumnModel columnModel = resultsTable.getColumnModel();
        
        // Set preferred column widths (removed Description column)
        int[] columnWidths = {200, 80, 80, 80, 100, 100, 100, 100, 150};
        for (int i = 0; i < columnWidths.length && i < columnModel.getColumnCount(); i++) {
            columnModel.getColumn(i).setPreferredWidth(columnWidths[i]);
        }
        
        // Configure renderers
        DecimalRenderer decimalRenderer = new DecimalRenderer();
        ScientificRenderer scientificRenderer = new ScientificRenderer();
        
        // Apply renderers to specific columns (adjusted indices)
        if (columnModel.getColumnCount() > 4) {
            columnModel.getColumn(4).setCellRenderer(scientificRenderer); // P-value
            columnModel.getColumn(5).setCellRenderer(scientificRenderer); // Adjusted P-value
            columnModel.getColumn(6).setCellRenderer(decimalRenderer);    // Enrichment Factor
            columnModel.getColumn(7).setCellRenderer(decimalRenderer);    // Fold Change
        }
    }
    
    private void initializeTBtoolsComponents() {
        try {
            // Check for KEGG annotation files
            File keggAnnotationFile = new File(targetSpecies.getFunctionalAnnotationDir(), "KEGG/KEGG.tsv");
            
            if (!keggAnnotationFile.exists()) {
                statusLabel.setText("Warning: KEGG annotation file not found. Please import KEGG annotations first.");
                runAnalysisButton.setEnabled(false);
                return;
            }
            
            System.out.println("Found KEGG annotation file: " + keggAnnotationFile.getAbsolutePath());
            
            // Create gene2Kegg file from species annotation (TBtools format)
            gene2KeggFile = createGene2KeggFile();
            
            // Resolve KEGG backend through shared manager
            String defaultBackendType = SimpleGenomeHubConfig.getInstance()
                .getProperty(SimpleGenomeHubConfig.KEGG_BACKEND_TYPE);
            if (defaultBackendType == null || defaultBackendType.trim().isEmpty()) {
                defaultBackendType = "Plants";
            }
            KeggBackendManager.ResolutionResult presetResolution = KeggBackendManager.resolveForSpecies(
                targetSpecies, KeggBackendManager.BackendMode.PRESET, defaultBackendType, null);
            if (presetResolution.exists()) {
                keggReferenceFile = presetResolution.getResolvedFile();
            } else {
                KeggBackendManager.ResolutionResult customResolution = KeggBackendManager.resolveForSpecies(
                    targetSpecies, KeggBackendManager.BackendMode.CUSTOM, defaultBackendType, null);
                keggReferenceFile = customResolution.exists() ? customResolution.getResolvedFile() : null;
            }
            
            if (gene2KeggFile != null && gene2KeggFile.exists() && 
                keggReferenceFile != null && keggReferenceFile.exists()) {
                
                System.out.println("Using gene2kegg file: " + gene2KeggFile.getAbsolutePath());
                System.out.println("Using KEGG reference file: " + keggReferenceFile.getAbsolutePath());
                
                // Initialize TBtools KEGG enrichment
                tbToolsKEGGEnricher = new KeggEnrichment();
                tbToolsKEGGEnricher.setAnnotation(gene2KeggFile.getAbsolutePath());
                tbToolsKEGGEnricher.setInRefenceKeg(keggReferenceFile.getAbsolutePath());
                
                statusLabel.setText("TBtools KEGG enrichment engine initialized successfully");
            } else {
                statusLabel.setText("Error: Failed to prepare KEGG enrichment files");
                runAnalysisButton.setEnabled(false);
            }
            
        } catch (Exception e) {
            System.err.println("Failed to initialize TBtools KEGG enrichment: " + e.getMessage());
            e.printStackTrace();
            statusLabel.setText("Error: Failed to initialize KEGG enrichment engine");
            runAnalysisButton.setEnabled(false);
        }
    }
    
    private File createGene2KeggFile() {
        try {
            File keggFile = new File(targetSpecies.getFunctionalAnnotationDir(), "KEGG/KEGG.tsv");
            if (!keggFile.exists()) return null;
            
            // Create temporary gene2kegg file in TBtools format
            File tempFile = File.createTempFile("gene2kegg_", ".tsv");
            tempFile.deleteOnExit();
            
            try (BufferedReader reader = new BufferedReader(new FileReader(keggFile));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                
                String line;
                boolean headerSkipped = false;
                
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    
                    // Skip header line
                    if (!headerSkipped && (line.startsWith("GeneID") || line.startsWith("#"))) {
                        headerSkipped = true;
                        continue;
                    }
                    
                    String[] parts = line.split("\t");
                    if (parts.length >= 2) {
                        String geneId = parts[0].trim();
                        String keggIds = parts[1].trim();
                        
                        if (!geneId.isEmpty() && !keggIds.isEmpty() && !keggIds.equals("-")) {
                            // Format: GeneID \t KEGG_ID1,KEGG_ID2,KEGG_ID3
                            writer.write(geneId + "\t" + keggIds);
                            writer.newLine();
                        }
                    }
                }
            }
            
            return tempFile;
            
        } catch (Exception e) {
            System.err.println("Error creating gene2kegg file: " + e.getMessage());
            return null;
        }
    }
    
    private File findExistingTBtoolsReference() {
        // Look for existing TBtools KEGG reference file
        File tbToolsRef = new File(targetSpecies.getFunctionalAnnotationDir(), "kegg-background-Plants.20250428.TBtoolsKeggBackEnd");
        if (tbToolsRef.exists()) {
            System.out.println("Found existing TBtools KEGG reference: " + tbToolsRef.getAbsolutePath());
            return tbToolsRef;
        }
        
        // Fallback: create from annotation if TBtools reference not found
        try {
            File keggAnnotationFile = new File(targetSpecies.getFunctionalAnnotationDir(), "KEGG/KEGG.tsv");
            return createKeggReferenceFromAnnotation(keggAnnotationFile);
        } catch (Exception e) {
            System.err.println("Error creating fallback reference: " + e.getMessage());
            return null;
        }
    }
    
    private File findKeggReferenceFile() {
        // Look for KEGG reference files in common locations
        String[] possiblePaths = {
            targetSpecies.getFunctionalAnnotationDir() + "/KEGG/kegg_reference_comprehensive.tsv",
            "J:/ClaudeCodeDev/TBtoolsCodeBase/dist/lib/kegg_reference.tsv",
            targetSpecies.getFunctionalAnnotationDir() + "/kegg_reference.tsv",
            targetSpecies.getFunctionalAnnotationDir() + "/KEGG/kegg_pathway_info.tsv",
            "kegg_reference.tsv"
        };
        
        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists() && file.canRead()) {
                System.out.println("Using KEGG reference file: " + file.getAbsolutePath());
                return file;
            }
        }
        
        // If not found, create a comprehensive reference file from existing data
        return createComprehensiveKeggReference();
    }
    
    private File createKeggReferenceFromAnnotation(File keggAnnotationFile) {
        try {
            File tempFile = File.createTempFile("kegg_ref_from_annotation_", ".tsv");
            tempFile.deleteOnExit();
            
            System.out.println("Creating KEGG reference from annotation file: " + keggAnnotationFile.getAbsolutePath());
            
            Set<String> processedKNumbers = new HashSet<>();
            
            try (BufferedReader reader = new BufferedReader(new FileReader(keggAnnotationFile));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                
                String line;
                boolean headerSkipped = false;
                
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    
                    // Skip header line
                    if (!headerSkipped && line.startsWith("#")) {
                        headerSkipped = true;
                        continue;
                    }
                    
                    String[] parts = line.split("\t");
                    if (parts.length >= 4) {
                        String keggId = parts[1].trim();
                        String term = parts[2].trim();
                        String description = parts[3].trim();
                        
                        if (!keggId.isEmpty() && !keggId.equals("-") && !processedKNumbers.contains(keggId)) {
                            processedKNumbers.add(keggId);
                            
                            // Parse pathway information from description
                            String[] pathwayInfo = parsePathwayFromDescription(description, term);
                            
                            // TBtools format: K_number \t Description \t Pathway \t SubClass \t MainClass
                            writer.write(keggId + "\t" + 
                                        term + "\t" + 
                                        pathwayInfo[0] + "\t" +  // Pathway
                                        pathwayInfo[1] + "\t" +  // SubClass  
                                        pathwayInfo[2] + "\n");  // MainClass
                        }
                    }
                }
            }
            
            System.out.println("Created KEGG reference with " + processedKNumbers.size() + " unique K numbers");
            return tempFile;
            
        } catch (Exception e) {
            System.err.println("Error creating KEGG reference from annotation: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private String[] parsePathwayFromDescription(String description, String term) {
        // Default values
        String pathway = "General function";
        String subClass = "General function prediction only";
        String mainClass = "Metabolism";
        
        if (description != null && !description.isEmpty()) {
            // Parse pathway numbers and names from description
            // Format examples: "02010 ABC transporters; 04147 Exosome; 04090 CD molecules; 02000 Transporters"
            String[] pathways = description.split(";");
            if (pathways.length > 0) {
                String firstPathway = pathways[0].trim();
                if (firstPathway.matches("\\d{5}\\s+.*")) {
                    pathway = firstPathway;
                    
                    // Determine subclass and mainclass based on pathway content
                    String lowerDesc = description.toLowerCase();
                    if (lowerDesc.contains("transport") || lowerDesc.contains("abc")) {
                        subClass = "Membrane transport";
                        mainClass = "Environmental Information Processing";
                    } else if (lowerDesc.contains("ribosom") || lowerDesc.contains("translation")) {
                        subClass = "Translation";
                        mainClass = "Genetic Information Processing";
                    } else if (lowerDesc.contains("metabolism") || lowerDesc.contains("biosynthesis")) {
                        if (lowerDesc.contains("carbohydrate") || lowerDesc.contains("glucose")) {
                            subClass = "Carbohydrate metabolism";
                        } else if (lowerDesc.contains("lipid") || lowerDesc.contains("fatty")) {
                            subClass = "Lipid metabolism";
                        } else if (lowerDesc.contains("amino acid")) {
                            subClass = "Amino acid metabolism";
                        } else {
                            subClass = "Global and overview maps";
                        }
                        mainClass = "Metabolism";
                    } else if (lowerDesc.contains("degradation") || lowerDesc.contains("rna")) {
                        subClass = "Folding, sorting and degradation";
                        mainClass = "Genetic Information Processing";
                    }
                } else {
                    pathway = firstPathway;
                }
            }
        }
        
        return new String[]{pathway, subClass, mainClass};
    }
    
    private File createComprehensiveKeggReference() {
        try {
            File tempFile = File.createTempFile("kegg_ref_comprehensive_", ".tsv");
            tempFile.deleteOnExit();
            
            // Create comprehensive KEGG reference from actual annotation data
            File keggFile = new File(targetSpecies.getFunctionalAnnotationDir(), "KEGG/KEGG.tsv");
            if (!keggFile.exists()) {
                return null;
            }
            
            Set<String> actualKNumbers = new HashSet<>();
            Map<String, String> kNumberToDescription = new HashMap<>();
            
            // Read actual KEGG annotations
            try (BufferedReader reader = new BufferedReader(new FileReader(keggFile))) {
                String line;
                boolean headerSkipped = false;
                
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    
                    if (!headerSkipped && line.startsWith("#")) {
                        headerSkipped = true;
                        continue;
                    }
                    
                    String[] parts = line.split("\t");
                    if (parts.length >= 3) {
                        String keggId = parts[1].trim();
                        String description = parts[2].trim();
                        
                        if (!keggId.isEmpty() && !keggId.equals("-")) {
                            actualKNumbers.add(keggId);
                            kNumberToDescription.put(keggId, description);
                        }
                    }
                }
            }
            
            // Create comprehensive reference file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                writer.write("K_number\tPathway_ID\tPathway_Name\tCategory\tSubcategory\n");
                
                for (String kNumber : actualKNumbers) {
                    String description = kNumberToDescription.get(kNumber);
                    String[] pathwayInfo = generatePathwayInfo(kNumber, description);
                    
                    writer.write(kNumber + "\t" + 
                                pathwayInfo[0] + "\t" + // Pathway ID
                                pathwayInfo[1] + "\t" + // Pathway Name
                                pathwayInfo[2] + "\t" + // Category
                                pathwayInfo[3] + "\n");  // Subcategory
                }
            }
            
            System.out.println("Created comprehensive KEGG reference with " + actualKNumbers.size() + " K numbers");
            return tempFile;
            
        } catch (Exception e) {
            System.err.println("Error creating comprehensive KEGG reference: " + e.getMessage());
            return null;
        }
    }
    
    private String[] generatePathwayInfo(String kNumber, String description) {
        // Default values
        String pathwayId = "map01100";
        String pathwayName = "Metabolic pathways";
        String category = "Metabolism";
        String subcategory = "Global and overview maps";
        
        // Try to infer pathway from description
        if (description != null && !description.isEmpty()) {
            description = description.toLowerCase();
            
            if (description.contains("carbohydrate") || description.contains("glucose") || 
                description.contains("glyc") || description.contains("starch")) {
                pathwayId = "map00010";
                pathwayName = "Glycolysis / Gluconeogenesis";
                subcategory = "Carbohydrate metabolism";
            } else if (description.contains("lipid") || description.contains("fatty acid") || 
                      description.contains("sterol")) {
                pathwayId = "map00061";
                pathwayName = "Fatty acid biosynthesis";
                subcategory = "Lipid metabolism";
            } else if (description.contains("amino acid") || description.contains("protein")) {
                pathwayId = "map00250";
                pathwayName = "Alanine, aspartate and glutamate metabolism";
                subcategory = "Amino acid metabolism";
            } else if (description.contains("transport") || description.contains("abc")) {
                pathwayId = "map02010";
                pathwayName = "ABC transporters";
                category = "Environmental Information Processing";
                subcategory = "Membrane transport";
            } else if (description.contains("ribosom") || description.contains("translation")) {
                pathwayId = "map03010";
                pathwayName = "Ribosome";
                category = "Genetic Information Processing";
                subcategory = "Translation";
            } else if (description.contains("photosynthesis")) {
                pathwayId = "map00195";
                pathwayName = "Photosynthesis";
                subcategory = "Energy metabolism";
            }
        }
        
        return new String[]{pathwayId, pathwayName, category, subcategory};
    }
    
    private void setupEventListeners() {
        // Add document listener to gene list text area for automatic gene counting
        geneListTextArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                SwingUtilities.invokeLater(() -> updateGeneCount());
            }
            
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                SwingUtilities.invokeLater(() -> updateGeneCount());
            }
            
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                SwingUtilities.invokeLater(() -> updateGeneCount());
            }
        });
        
        // Load gene list from file button event
        loadGeneListButton.addActionListener(e -> loadGeneListFromFile());

        importGeneSetComboBox.addActionListener(e -> importSelectedGeneSet());

        clearGeneListButton.addActionListener(e -> clearGeneListInput());
        
        runAnalysisButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runKEGGEnrichmentAnalysis();
            }
        });
        
        exportButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportResults();
            }
        });

        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearResults();
            }
        });
    }
    
    private void runKEGGEnrichmentAnalysis() {
        // Validate input
        String geneListText = geneListTextArea.getText().trim();
        if (geneListText.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a list of gene IDs for analysis.",
                "Input Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Parse gene list
        List<String> geneList = parseGeneList(geneListText);
        if (geneList.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No valid gene IDs found in the input.",
                "Invalid Input", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Run analysis in background thread
        SwingWorker<List<EnhancedEnrichmentResult>, String> worker = new SwingWorker<List<EnhancedEnrichmentResult>, String>() {
            @Override
            protected List<EnhancedEnrichmentResult> doInBackground() throws Exception {
                publish("Initializing KEGG enrichment analysis...");
                
                progressBar.setVisible(true);
                progressBar.setIndeterminate(true);
                runAnalysisButton.setEnabled(false);
                
                return performKEGGEnrichmentAnalysis(geneList);
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
                    List<EnhancedEnrichmentResult> results = get();
                    
                    System.out.println("=== UI UPDATE: Received " + results.size() + " enrichment results ===");
                    
                    // Clear old results first
                    tableModel.setResults(new ArrayList<>());
                    
                    // Update table with new results
                    tableModel.setResults(results);
                    
                    // Force table to repaint
                    SwingUtilities.invokeLater(() -> {
                        resultsTable.revalidate();
                        resultsTable.repaint();
                    });
                    
                    progressBar.setVisible(false);
                    runAnalysisButton.setEnabled(true);
                    exportButton.setEnabled(!results.isEmpty());
                    visualizeButton.setEnabled(!results.isEmpty());
                    clearButton.setEnabled(!results.isEmpty());
                    
                    if (results.isEmpty()) {
                        statusLabel.setText("No significant enrichment found with current parameters");
                        System.out.println("=== UI UPDATE: No significant results ===");
                    } else {
                        statusLabel.setText(String.format("Analysis complete: %d significant terms found", results.size()));
                        System.out.println("=== UI UPDATE: " + results.size() + " significant terms found ===");
                        
                        // Print first few results for verification
                        for (int i = 0; i < Math.min(3, results.size()); i++) {
                            EnhancedEnrichmentResult result = results.get(i);
                            System.out.println("  " + (i+1) + ". " + result.getTermName() + 
                                             " (p=" + String.format("%.6f", result.getPValue()) + 
                                             ", genes=" + result.getGeneCount() + ")");
                        }
                    }
                    
                } catch (Exception e) {
                    progressBar.setVisible(false);
                    runAnalysisButton.setEnabled(true);
                    clearButton.setEnabled(!tableModel.getResults().isEmpty());
                    statusLabel.setText("Error: " + e.getMessage());
                    
                    JOptionPane.showMessageDialog(KEGGEnrichmentDialog.this,
                        "Analysis failed: " + e.getMessage(),
                        "Analysis Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        worker.execute();
    }
    
    private List<String> parseGeneList(String geneListText) {
        List<String> genes = new ArrayList<>();
        
        // Split by common delimiters
        String[] lines = geneListText.split("[\\n\\r,;\\s]+");
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                genes.add(line);
            }
        }
        
        return genes;
    }
    
    private List<EnhancedEnrichmentResult> performKEGGEnrichmentAnalysis(List<String> geneList) throws Exception {
        if (tbToolsKEGGEnricher == null) {
            throw new Exception("TBtools KEGG enrichment engine not initialized. Please check KEGG annotation files and initialization.");
        }
        
        // Create temporary files for TBtools
        File tempGeneFile = File.createTempFile("selected_genes_", ".txt");
        File tempOutputFile = File.createTempFile("kegg_enrichment_", ".tsv");
        tempGeneFile.deleteOnExit();
        tempOutputFile.deleteOnExit();
        
        // Write selected genes to file
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempGeneFile))) {
            for (String gene : geneList) {
                writer.write(gene);
                writer.newLine();
            }
        }
        
        // Configure TBtools KEGG enrichment
        tbToolsKEGGEnricher.setSelectIdsFile(tempGeneFile.getAbsolutePath());
        tbToolsKEGGEnricher.setOutFile(tempOutputFile.getAbsolutePath());
        
        // Initialize background
        tbToolsKEGGEnricher.initializedBackground();
        
        // Run enrichment analysis
        tbToolsKEGGEnricher.conductEnrichment();
        
        // Parse results
        return parseKEGGEnrichmentResults(tempOutputFile);
    }
    
    private List<EnhancedEnrichmentResult> parseKEGGEnrichmentResults(File resultFile) throws IOException {
        List<EnhancedEnrichmentResult> results = new ArrayList<>();
        
        System.out.println("Parsing TBtools KEGG results from: " + resultFile.getAbsolutePath());
        
        try (BufferedReader reader = new BufferedReader(new FileReader(resultFile))) {
            String line;
            boolean headerSkipped = false;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (!headerSkipped) {
                    System.out.println("Header: " + line);
                    headerSkipped = true;
                    continue; // Skip header
                }
                
                String[] parts = line.split("\t");
                System.out.println("Parsing line with " + parts.length + " parts: " + line.substring(0, Math.min(100, line.length())));
                
                if (parts.length >= 10) { // TBtools format has 10 columns
                    try {
                        EnhancedEnrichmentResult result = new EnhancedEnrichmentResult();
                        
                        // TBtools format: Term Name | MainClass | GeneHitsInSelectedSet | AllGenesInSelectedSet | 
                        //                 GeneHitsInBackground | AllGenesInBackground | p-value | enrichFactor | 
                        //                 GeneListInSelectedSets | corrected p-value(BH method)
                        
                        result.setTermId(parts[0]); // Term Name
                        result.setTermName(parts[0]); // Term Name
                        result.setCategory(parts[1]); // MainClass
                        result.setDescription(parts[0] + " - " + parts[1]); // Combine term name and category
                        
                        result.setGeneCount(Integer.parseInt(parts[2])); // GeneHitsInSelectedSet
                        result.setTestSetSize(Integer.parseInt(parts[3])); // AllGenesInSelectedSet
                        result.setTermSize(Integer.parseInt(parts[4])); // GeneHitsInBackground
                        result.setBackgroundCount(Integer.parseInt(parts[5])); // AllGenesInBackground
                        
                        result.setPValue(Double.parseDouble(parts[6])); // p-value
                        result.setEnrichmentRatio(Double.parseDouble(parts[7])); // enrichFactor
                        
                        // Parse gene list (format: [gene1, gene2, gene3])
                        if (parts.length > 8) {
                            String geneListStr = parts[8].trim();
                            if (geneListStr.startsWith("[") && geneListStr.endsWith("]")) {
                                geneListStr = geneListStr.substring(1, geneListStr.length() - 1);
                            }
                            String[] genes = geneListStr.split(",\\s*");
                            List<String> geneList = new ArrayList<>();
                            for (String gene : genes) {
                                if (!gene.trim().isEmpty()) {
                                    geneList.add(gene.trim());
                                }
                            }
                            result.setGeneIds(geneList);
                        }
                        
                        // Use TBtools corrected p-value if available
                        if (parts.length > 9) {
                            result.setAdjustedPValue(Double.parseDouble(parts[9])); // corrected p-value(BH method)
                        } else {
                            result.setAdjustedPValue(result.getPValue());
                        }
                        
                        result.calculateDerivedStatistics();
                        
                        // Apply filtering based on user parameters
                        double maxPValue = (Double) maxPValueSpinner.getValue();
                        int minGeneCount = (Integer) minGeneCountSpinner.getValue();
                        
                        // Use p-value for filtering
                        if (result.getPValue() <= maxPValue && result.getGeneCount() >= minGeneCount) {
                            results.add(result);
                            System.out.println("Added result: " + result.getTermName() + 
                                             " (p=" + result.getPValue() + 
                                             ", adj_p=" + result.getAdjustedPValue() + 
                                             ", genes=" + result.getGeneCount() + ")");
                        } else {
                            System.out.println("Filtered out: " + result.getTermName() + 
                                             " (p=" + result.getPValue() + 
                                             " > " + maxPValue + " OR genes=" + result.getGeneCount() + 
                                             " < " + minGeneCount + ")");
                        }
                        
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing line: " + line);
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("Skipping line with insufficient columns: " + parts.length);
                }
            }
        }
        
        // Sort by adjusted p-value
        results.sort(Comparator.comparingDouble(EnhancedEnrichmentResult::getAdjustedPValue));
        
        System.out.println("Total results after parsing and filtering: " + results.size());
        return results;
    }
    
    
    private String getSelectedKEGGCategory() {
        return (String) keggCategoryComboBox.getSelectedItem();
    }
    
    /**
     * Setup enhanced table interactions (BLAST-style)
     */
    private void setupTableInteractions() {
        // Add right-click context menu
        resultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e);
                }
            }
        });
        
        // Add selection listener for enhanced feedback
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectionStatus();
            }
        });
        
        resultsTable.getColumnModel().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectionStatus();
            }
        });
    }
    
    /**
     * Show context menu for table operations
     */
    private void showContextMenu(MouseEvent e) {
        int column = resultsTable.columnAtPoint(e.getPoint());
        if (column >= 0) {
            JPopupMenu contextMenu = new JPopupMenu();
            
            // Copy selected cells
            JMenuItem copyItem = new JMenuItem("Copy Selected Cells");
            copyItem.addActionListener(ev -> copySelectedCells());
            copyItem.setAccelerator(KeyStroke.getKeyStroke("control C"));
            contextMenu.add(copyItem);
            
            contextMenu.addSeparator();
            
            // Column operations
            JMenuItem removeDuplicatesItem = new JMenuItem("Remove Duplicates in Column");
            removeDuplicatesItem.addActionListener(ev -> removeDuplicatesInColumn(column));
            contextMenu.add(removeDuplicatesItem);
            
            contextMenu.addSeparator();
            
            // Sorting options
            JMenuItem sortAscItem = new JMenuItem("Sort Ascending (A-Z, 0-9)");
            sortAscItem.addActionListener(ev -> sortColumn(column, true));
            contextMenu.add(sortAscItem);
            
            JMenuItem sortDescItem = new JMenuItem("Sort Descending (Z-A, 9-0)");
            sortDescItem.addActionListener(ev -> sortColumn(column, false));
            contextMenu.add(sortDescItem);
            
            contextMenu.addSeparator();
            
            // Filter by selection
            if (resultsTable.getSelectedRowCount() > 0) {
                JMenuItem filterSelectedItem = new JMenuItem("Show Only Selected Rows");
                filterSelectedItem.addActionListener(ev -> filterBySelectedRows());
                contextMenu.add(filterSelectedItem);
                
                JMenuItem clearFilterItem = new JMenuItem("Clear Filter (Show All)");
                clearFilterItem.addActionListener(ev -> clearTableFilter());
                contextMenu.add(clearFilterItem);
            }
            
            contextMenu.show(resultsTable, e.getX(), e.getY());
        }
    }
    
    /**
     * Remove duplicates in specified column
     */
    private void removeDuplicatesInColumn(int column) {
        List<EnhancedEnrichmentResult> currentResults = tableModel.getResults();
        Set<String> seenValues = new HashSet<>();
        List<EnhancedEnrichmentResult> uniqueResults = new ArrayList<>();
        
        for (EnhancedEnrichmentResult result : currentResults) {
            String columnValue = getColumnValue(result, column);
            if (!seenValues.contains(columnValue)) {
                seenValues.add(columnValue);
                uniqueResults.add(result);
            }
        }
        
        // Update model and clear sort
        tableModel.setResults(uniqueResults);
        RowSorter<? extends javax.swing.table.TableModel> sorter = resultsTable.getRowSorter();
        if (sorter != null) {
            sorter.setSortKeys(null);
        }
        
        JOptionPane.showMessageDialog(this, 
            "Removed " + (currentResults.size() - uniqueResults.size()) + " duplicate entries.",
            "Remove Duplicates", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Sort column in specified order
     */
    private void sortColumn(int column, boolean ascending) {
        RowSorter<? extends javax.swing.table.TableModel> sorter = resultsTable.getRowSorter();
        if (sorter != null) {
            List<RowSorter.SortKey> sortKeys = new ArrayList<>();
            sortKeys.add(new RowSorter.SortKey(column, ascending ? SortOrder.ASCENDING : SortOrder.DESCENDING));
            sorter.setSortKeys(sortKeys);
        }
    }
    
    /**
     * Filter to show only selected rows
     */
    private void filterBySelectedRows() {
        int[] selectedRows = resultsTable.getSelectedRows();
        if (selectedRows.length == 0) return;
        
        // Convert view indices to model indices
        List<EnhancedEnrichmentResult> selectedResults = new ArrayList<>();
        for (int viewRow : selectedRows) {
            int modelRow = resultsTable.convertRowIndexToModel(viewRow);
            selectedResults.add(tableModel.getResults().get(modelRow));
        }
        
        tableModel.setResults(selectedResults);
        statusLabel.setText("Showing " + selectedResults.size() + " filtered results");
    }
    
    /**
     * Clear table filter to show all results
     */
    private void clearTableFilter() {
        // This would need to store original results - for now just show current
        statusLabel.setText("Filter cleared - showing all current results");
    }

    private void clearResults() {
        tableModel.setResults(new ArrayList<>());
        exportButton.setEnabled(false);
        visualizeButton.setEnabled(false);
        clearButton.setEnabled(false);
        statusLabel.setText("Results cleared");
        statusLabel.setForeground(Color.DARK_GRAY);
    }
    
    /**
     * Update selection status
     */
    private void updateSelectionStatus() {
        int selectedRows = resultsTable.getSelectedRowCount();
        int selectedCols = resultsTable.getSelectedColumnCount();
        
        if (selectedRows > 0 && selectedCols > 0) {
            String status = String.format("Selected: %d rows × %d columns", selectedRows, selectedCols);
            if (statusLabel != null) {
                String currentText = statusLabel.getText();
                if (!currentText.contains("Selected:")) {
                    statusLabel.setText(currentText + " | " + status);
                }
            }
        }
    }
    
    /**
     * Get column value for a result
     */
    private String getColumnValue(EnhancedEnrichmentResult result, int column) {
        switch (column) {
            case 0: return result.getTermName();
            case 1: return result.getCategory();
            case 2: return String.valueOf(result.getGeneCount());
            case 3: return String.valueOf(result.getTermSize());
            case 4: return String.valueOf(result.getPValue());
            case 5: return String.valueOf(result.getAdjustedPValue());
            case 6: return String.valueOf(result.getEnrichmentRatio());
            case 7: return String.valueOf(result.getFoldEnrichment());
            case 8: return result.getGeneIds() != null ? String.join(",", result.getGeneIds()) : "";
            default: return "";
        }
    }
    
    private void copySelectedCells() {
        int[] selectedRows = resultsTable.getSelectedRows();
        int[] selectedCols = resultsTable.getSelectedColumns();
        
        if (selectedRows.length == 0 || selectedCols.length == 0) return;
        
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < selectedRows.length; i++) {
            for (int j = 0; j < selectedCols.length; j++) {
                Object value = resultsTable.getValueAt(selectedRows[i], selectedCols[j]);
                sb.append(value != null ? value.toString() : "");
                
                if (j < selectedCols.length - 1) sb.append("\t");
            }
            if (i < selectedRows.length - 1) sb.append("\n");
        }
        
        StringSelection selection = new StringSelection(sb.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, null);
    }
    
    private void exportResults() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                "No results to export.",
                "Export Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("kegg_enrichment_results.tsv"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File outputFile = fileChooser.getSelectedFile();
                tableModel.exportToFile(outputFile);
                
                JOptionPane.showMessageDialog(this,
                    "Results exported successfully to: " + outputFile.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Error exporting results: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Create visualization using TBtools Barplot
     */
    private void createVisualization() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                "No enrichment results available for visualization.",
                "Visualization Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        try {
            // Show configuration dialog
            KEGGVisualizationConfigDialog configDialog = new KEGGVisualizationConfigDialog(this);
            configDialog.setVisible(true);
            
            if (!configDialog.isConfirmed()) {
                return; // User cancelled
            }
            
            // Create temporary TSV file for visualization
            File tempFile = File.createTempFile("kegg_enrichment_viz_", ".tsv");
            tempFile.deleteOnExit();
            
            // Export current results to temp file
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                // Write header compatible with TBtools Barplot
                writer.write("Term Name\tClass\tP_value\tAdjusted_P_value\tEnrichment_Factor\tGene_Count\n");
                
                // Write data (no Description column needed)
                for (EnhancedEnrichmentResult result : tableModel.getResults()) {
                    writer.write(String.format("%s\t%s\t%.6e\t%.6e\t%.4f\t%d\n",
                        result.getTermName(),
                        result.getCategory(),
                        result.getPValue(),
                        result.getAdjustedPValue(),
                        result.getEnrichmentRatio(),
                        result.getGeneCount()));
                }
            }
            
            // Create and configure Barplot
            Barplot barplot = new Barplot();
            barplot.setInTabFile(tempFile);
            barplot.setTermColName("Term Name");
            barplot.setClassColName("Class");
            barplot.setpValueColName(configDialog.getPValueColumn());
            barplot.setXlab(configDialog.getXLabel());
            barplot.setYlab(configDialog.getYLabel());
            barplot.setMaxTermToShow(configDialog.getMaxTerms());
            barplot.setValueFormat(configDialog.getValueFormat());
            barplot.setGraphMode(configDialog.getGraphMode());
            
            // Generate visualization (TBtools will display it automatically)
            statusLabel.setText("Generating KEGG enrichment visualization...");
            barplot.generate();
            statusLabel.setText("KEGG enrichment visualization created successfully");
            
        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error creating visualization: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                "Failed to create visualization: " + e.getMessage(),
                "Visualization Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Configuration dialog for KEGG enrichment visualization
     */
    private static class KEGGVisualizationConfigDialog extends JDialog {
        private JComboBox<String> pValueColumnCombo;
        private JTextField xLabelField;
        private JTextField yLabelField;
        private JSpinner maxTermsSpinner;
        private JTextField valueFormatField;
        private JComboBox<Barplot.GraphMode> graphModeCombo;
        private boolean confirmed = false;
        
        public KEGGVisualizationConfigDialog(Dialog parent) {
            super(parent, "KEGG Visualization Configuration", true);
            initComponents();
            setupEventListeners();
            pack();
            setLocationRelativeTo(parent);
        }
        
        private void initComponents() {
            setLayout(new BorderLayout(10, 10));
            
            JPanel mainPanel = new JPanel(new GridBagLayout());
            mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.anchor = GridBagConstraints.WEST;
            
            // P-value column selection
            gbc.gridx = 0; gbc.gridy = 0;
            mainPanel.add(new JLabel("P-value Column:"), gbc);
            gbc.gridx = 1;
            pValueColumnCombo = new JComboBox<>(new String[]{"P_value", "Adjusted_P_value"});
            pValueColumnCombo.setSelectedItem("Adjusted_P_value");
            SimpleGenomeHubUi.setComboBoxDisplayWidth(pValueColumnCombo, 180);
            mainPanel.add(pValueColumnCombo, gbc);
            
            // X-axis label
            gbc.gridx = 0; gbc.gridy = 1;
            mainPanel.add(new JLabel("X-axis Label:"), gbc);
            gbc.gridx = 1;
            xLabelField = new JTextField("-log10(Adjusted P-value)", 20);
            mainPanel.add(xLabelField, gbc);
            
            // Y-axis label
            gbc.gridx = 0; gbc.gridy = 2;
            mainPanel.add(new JLabel("Y-axis Label:"), gbc);
            gbc.gridx = 1;
            yLabelField = new JTextField("KEGG Pathway", 20);
            mainPanel.add(yLabelField, gbc);
            
            // Max terms to show
            gbc.gridx = 0; gbc.gridy = 3;
            mainPanel.add(new JLabel("Max Terms:"), gbc);
            gbc.gridx = 1;
            maxTermsSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 100, 1));
            mainPanel.add(maxTermsSpinner, gbc);
            
            // Value format
            gbc.gridx = 0; gbc.gridy = 4;
            mainPanel.add(new JLabel("Value Format:"), gbc);
            gbc.gridx = 1;
            valueFormatField = new JTextField("0.00", 10);
            mainPanel.add(valueFormatField, gbc);
            
            // Graph mode
            gbc.gridx = 0; gbc.gridy = 5;
            mainPanel.add(new JLabel("Graph Mode:"), gbc);
            gbc.gridx = 1;
            graphModeCombo = new JComboBox<>(Barplot.GraphMode.values());
            graphModeCombo.setSelectedItem(Barplot.GraphMode.Normal);
            SimpleGenomeHubUi.setComboBoxDisplayWidth(graphModeCombo, 220);
            mainPanel.add(graphModeCombo, gbc);
            
            add(mainPanel, BorderLayout.CENTER);
            
            // Button panel
            JPanel buttonPanel = new JPanel(new FlowLayout());
            JButton okButton = new JButton("Create Visualization");
            JButton cancelButton = new JButton("Cancel");
            
            okButton.addActionListener(e -> {
                confirmed = true;
                dispose();
            });
            
            cancelButton.addActionListener(e -> {
                confirmed = false;
                dispose();
            });
            
            buttonPanel.add(okButton);
            buttonPanel.add(cancelButton);
            add(buttonPanel, BorderLayout.SOUTH);
        }
        
        private void setupEventListeners() {
            // Update X-axis label when P-value column changes
            pValueColumnCombo.addActionListener(e -> {
                String selected = (String) pValueColumnCombo.getSelectedItem();
                if ("P_value".equals(selected)) {
                    xLabelField.setText("-log10(P-value)");
                } else {
                    xLabelField.setText("-log10(Adjusted P-value)");
                }
            });
        }
        
        public boolean isConfirmed() { return confirmed; }
        public String getPValueColumn() { return (String) pValueColumnCombo.getSelectedItem(); }
        public String getXLabel() { return xLabelField.getText(); }
        public String getYLabel() { return yLabelField.getText(); }
        public int getMaxTerms() { return (Integer) maxTermsSpinner.getValue(); }
        public String getValueFormat() { return valueFormatField.getText(); }
        public Barplot.GraphMode getGraphMode() { return (Barplot.GraphMode) graphModeCombo.getSelectedItem(); }
    }
    
    // Custom table model for KEGG enrichment results
    private class KEGGEnrichmentTableModel extends AbstractTableModel {
        private final String[] columnNames = {
            "Pathway_Name", "Category", 
            "Gene_Count", "Term_Size", "P_Value", "Adj_P_Value", 
            "Enrichment_Factor", "Fold_Change", "Gene_List"
        };
        
        private List<EnhancedEnrichmentResult> results = new ArrayList<>();
        
        @Override
        public int getRowCount() {
            return results.size();
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
            if (rowIndex >= results.size()) return null;
            
            EnhancedEnrichmentResult result = results.get(rowIndex);
            
            switch (columnIndex) {
                case 0: return result.getTermName();
                case 1: return result.getCategory();
                case 2: return Integer.valueOf(result.getGeneCount());
                case 3: return Integer.valueOf(result.getTermSize());
                case 4: return Double.valueOf(result.getPValue());
                case 5: return Double.valueOf(result.getAdjustedPValue());
                case 6: return Double.valueOf(result.getEnrichmentRatio());
                case 7: return Double.valueOf(result.getFoldEnrichment());
                case 8: return result.getGeneListString();
                default: return null;
            }
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 0: case 1: case 8: // String columns (Pathway_Name, Category, Gene_List)
                    return String.class;
                case 2: case 3: // Integer columns (Gene_Count, Term_Size)
                    return Integer.class;
                case 4: case 5: case 6: case 7: // Double columns (P_Value, Adj_P_Value, Enrichment_Factor, Fold_Change)
                    return Double.class;
                default:
                    return Object.class;
            }
        }
        
        public void setResults(List<EnhancedEnrichmentResult> results) {
            this.results = new ArrayList<>(results);
            fireTableDataChanged();
        }
        
        public List<EnhancedEnrichmentResult> getResults() {
            return new ArrayList<>(results);
        }
        
        public void exportToFile(File file) throws IOException {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                // Write header
                writer.write(String.join("\t", columnNames));
                writer.newLine();
                
                // Write data
                for (EnhancedEnrichmentResult result : results) {
                    writer.write(result.toTSV());
                    writer.newLine();
                }
            }
        }
    }
    
    // Custom renderers for numeric columns
    private class DecimalRenderer extends DefaultTableCellRenderer {
        private final DecimalFormat formatter = new DecimalFormat("#0.00");
        
        @Override
        public void setValue(Object value) {
            if (value instanceof Number) {
                setText(formatter.format(value));
            } else {
                setText(value != null ? value.toString() : "");
            }
        }
    }
    
    private class ScientificRenderer extends DefaultTableCellRenderer {
        @Override
        public void setValue(Object value) {
            if (value instanceof Number) {
                double val = ((Number) value).doubleValue();
                if (val < 0.01) {
                    setText(String.format("%.2e", val));
                } else {
                    setText(String.format("%.4f", val));
                }
            } else {
                setText(value != null ? value.toString() : "");
            }
        }
    }
    
    /**
     * Update gene count label
     */
    private void updateGeneCount() {
        if (geneListTextArea != null && geneCountLabel != null) {
            String text = geneListTextArea.getText().trim();
            if (text.isEmpty()) {
                geneCountLabel.setText("Genes: 0");
            } else {
                String[] genes = text.split("[\\s,;\\n\\r]+");
                int count = 0;
                for (String gene : genes) {
                    if (!gene.trim().isEmpty()) {
                        count++;
                    }
                }
                geneCountLabel.setText("Genes: " + count);
            }
        }
    }
    
    /**
     * 从文件加载基因列表
     */
    private void loadGeneListFromFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Text files (*.txt, *.tsv)", "txt", "tsv"));
        
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                List<String> genes = readGeneListFromFile(file);
                geneListTextArea.setText(String.join("\n", genes));
                if (statusLabel != null) {
                    statusLabel.setText("Loaded " + genes.size() + " genes from " + file.getName());
                    statusLabel.setForeground(Color.BLUE);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, 
                    "Error loading gene list: " + ex.getMessage(), 
                    "Load Error", JOptionPane.ERROR_MESSAGE);
                if (statusLabel != null) {
                    statusLabel.setText("Failed to load gene list");
                    statusLabel.setForeground(Color.RED);
                }
            }
        }
    }
    
    /**
     * 从文件读取基因列表
     */
    private void clearGeneListInput() {
        geneListTextArea.setText("");
        geneListTextArea.requestFocusInWindow();
        if (statusLabel != null) {
            statusLabel.setText("Gene list cleared");
            statusLabel.setForeground(Color.DARK_GRAY);
        }
    }

    private void refreshImportGeneSetOptions() {
        availableGeneSetFiles = GeneSetImportSupport.loadAvailableSetFiles(targetSpecies);

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
        statusLabel.setForeground(Color.BLUE);

        SwingWorker<GeneSetImportSupport.ImportedGeneSet, Void> worker =
            new SwingWorker<GeneSetImportSupport.ImportedGeneSet, Void>() {
                @Override
                protected GeneSetImportSupport.ImportedGeneSet doInBackground() throws Exception {
                    return GeneSetImportSupport.importIds(targetSpecies, selectedFile, GeneSetImportSupport.OutputIdType.GENE);
                }

                @Override
                protected void done() {
                    importGeneSetComboBox.setEnabled(!availableGeneSetFiles.isEmpty());
                    importGeneSetComboBox.setSelectedIndex(0);

                    try {
                        GeneSetImportSupport.ImportedGeneSet importedGeneSet = get();
                        List<String> importedIds = importedGeneSet.getIds();
                        geneListTextArea.setText(String.join("\n", importedIds));
                        geneListTextArea.setCaretPosition(0);
                        statusLabel.setText("Imported " + importedIds.size() + " gene IDs from "
                            + (importedGeneSet.getSetKind() == GeneSetFileSupport.SetKind.REGION ? "Region Set" : "Gene Set"));
                        statusLabel.setForeground(Color.BLUE);
                    } catch (Exception ex) {
                        Throwable cause = ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                            ? ex.getCause()
                            : ex;

                        String message = cause.getMessage() != null ? cause.getMessage() : "Unknown error";
                        statusLabel.setText("Import failed");
                        statusLabel.setForeground(Color.RED);

                        int messageType = cause instanceof GeneSetImportSupport.NoGeneFoundException
                            ? JOptionPane.INFORMATION_MESSAGE
                            : JOptionPane.ERROR_MESSAGE;

                        JOptionPane.showMessageDialog(
                            KEGGEnrichmentDialog.this,
                            "Failed to import selected set:\n" + message,
                            "Import Gene Set Failed",
                            messageType
                        );
                    }
                }
            };

        worker.execute();
    }

    private List<String> readGeneListFromFile(File file) throws IOException {
        List<String> genes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    // Split by common delimiters and add each gene
                    String[] parts = line.split("[,;\\s\\t]+");
                    for (String part : parts) {
                        part = part.trim();
                        if (!part.isEmpty()) {
                            genes.add(part);
                        }
                    }
                }
            }
        }
        return genes;
    }
}
