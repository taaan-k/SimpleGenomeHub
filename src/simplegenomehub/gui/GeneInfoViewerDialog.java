/*
 * Comprehensive Gene Information Viewer Dialog
 * Integrates sequence, annotation, and expression data visualization
 */
package simplegenomehub.gui;

import biocjava.bioDoer.MEME.DrawMotifPattern.DrawGeneStructureFromGXFfile;
import jigplot.engine.JIGBasePanel;
import jigplot.engine.JIGSubPanel;
import simplegenomehub.blast.BlastConfig;
import simplegenomehub.model.*;
import simplegenomehub.util.fileio.CachedSequenceLookup;
import simplegenomehub.gui.components.EnhancedInteractiveTable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Comprehensive gene information viewer that displays:
 * - Gene sequences (transcript, CDS, protein)
 * - Functional annotations (GO, KEGG, InterPro, etc.)
 * - Expression data visualization
 * - Gene structure information
 * 
 * @author SimpleGenomeHub Team
 */
public class GeneInfoViewerDialog extends JDialog {
    
    private static final Logger logger = Logger.getLogger(GeneInfoViewerDialog.class.getName());
    
    // Data sources
    private SpeciesManager speciesManager;
    private SpeciesInfo currentSpecies;
    private String currentGeneId;
    private GeneDataRetriever dataRetriever;
    private final AtomicLong searchGeneration = new AtomicLong(0L);

    // Example gene IDs management
    private java.util.Map<String, java.util.List<String>> speciesExampleGenes;
    private java.util.Map<String, String> speciesExamplePlaceholders;
    
    // Search panel components
    private PlaceholderTextField geneIdField;
    private JComboBox<SpeciesInfo> speciesCombo;
    private JButton exampleButton;
    private JButton searchButton;
    private JButton clearButton;
    private JLabel statusLabel;
    
    // Main display components
    private JTabbedPane mainTabs;
    private SequenceInfoPanel sequencePanel;
    private AnnotationInfoPanel annotationPanel;
    private ExpressionVisualizationPanel expressionPanel;
    
    // Control buttons
    private JButton geneStructureAdvancedButton;
    private JButton exportButton;
    private JButton closeButton;
    
    /**
     * Constructor
     */
    public GeneInfoViewerDialog(Window parent, SpeciesManager speciesManager) {
        this(parent, speciesManager, null);
    }

    /**
     * Constructor with default species
     */
    public GeneInfoViewerDialog(Window parent, SpeciesManager speciesManager, SpeciesInfo defaultSpecies) {
        super(parent, "Gene Information Viewer", ModalityType.MODELESS);

        this.speciesManager = speciesManager;
        this.dataRetriever = new GeneDataRetriever();

        // Initialize example gene IDs management
        this.speciesExampleGenes = new java.util.HashMap<>();
        this.speciesExamplePlaceholders = new java.util.HashMap<>();

        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadSpeciesList();

        // Set default species if provided
        if (defaultSpecies != null) {
            setCurrentSpecies(defaultSpecies);
        }

        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setSize(1000, 800);
        setLocationRelativeTo(parent);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Search panel components
        speciesCombo = new JComboBox<>();
        speciesCombo.setToolTipText("Select species for gene search");

        geneIdField = new PlaceholderTextField(20);
        geneIdField.setToolTipText("Enter gene ID or select an example");
        geneIdField.setPlaceholder("Select species to see examples");

        exampleButton = new JButton("Examples ▼");
        exampleButton.setToolTipText("Click to see example gene IDs for selected species");
        exampleButton.setText("Examples v");
        exampleButton.setPreferredSize(SimpleGenomeHubStyle.SIZE_BUTTON_120_X_30);

        searchButton = new JButton("Search Gene");
        searchButton.setToolTipText("Search for gene information");
        searchButton.setPreferredSize(SimpleGenomeHubStyle.SIZE_BUTTON_120_X_38);
        searchButton.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        searchButton.setBackground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON);
        searchButton.setForeground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_TEXT);
        searchButton.setBorder(BorderFactory.createLineBorder(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_BORDER, 1));
        searchButton.setFocusPainted(false);
        searchButton.setOpaque(true);

        clearButton = new JButton("Clear");
        clearButton.setToolTipText("Clear all fields and results");
        clearButton.setPreferredSize(SimpleGenomeHubStyle.SIZE_BUTTON_84_X_30);

        statusLabel = new JLabel("Select a species and enter gene ID to search");
        statusLabel.setForeground(SimpleGenomeHubStyle.STATUS_INFO_TEXT);
        
        // Main display tabs
        mainTabs = new JTabbedPane();
        sequencePanel = new SequenceInfoPanel();
        annotationPanel = new AnnotationInfoPanel();
        expressionPanel = new ExpressionVisualizationPanel();

        // Add tabs
        mainTabs.addTab("Sequences", createTabIcon("sequence"), sequencePanel, "Gene sequence information");
        mainTabs.addTab("Annotations", createTabIcon("annotation"), annotationPanel, "Functional annotations");
        mainTabs.addTab("Expression", createTabIcon("expression"), expressionPanel, "Expression data visualization");
        
        // Initially disable tabs until data is loaded
        setTabsEnabled(false);
        
        // Control buttons
        geneStructureAdvancedButton = new JButton("Gene Structure View (Advanced)");
        geneStructureAdvancedButton.setToolTipText("Open TBtools Gene Structure View (Advanced) for the current gene");
        geneStructureAdvancedButton.setEnabled(false);

        exportButton = new JButton("Export All Data");
        exportButton.setToolTipText("Export all gene information to files");
        exportButton.setEnabled(false);
        
        closeButton = new JButton("Close");
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // Search panel
        JPanel searchPanel = createSearchPanel();
        add(searchPanel, BorderLayout.NORTH);
        
        // Main content area
        add(mainTabs, BorderLayout.CENTER);
        
        // Control panel
        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create search panel
     */
    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Gene Search"));

        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        speciesCombo.setPreferredSize(SimpleGenomeHubStyle.SIZE_COMBO_280_X_30);
        inputPanel.add(speciesCombo, gbc);

        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        inputPanel.add(geneIdField, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        inputPanel.add(clearButton, gbc);

        gbc.gridx = 2;
        inputPanel.add(exampleButton, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 3;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        inputPanel.add(searchButton, gbc);

        // Status panel
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(statusLabel);

        panel.add(inputPanel, BorderLayout.CENTER);
        panel.add(statusPanel, BorderLayout.SOUTH);

        return panel;
    }
    
    /**
     * Create control panel
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEtchedBorder());
        
        panel.add(geneStructureAdvancedButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(exportButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(closeButton);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        // Search button
        searchButton.addActionListener(e -> performGeneSearch());

        // Clear button
        clearButton.addActionListener(e -> clearAllData());

        // Gene ID field - Enter key triggers search
        geneIdField.addActionListener(e -> performGeneSearch());

        // Example button
        exampleButton.addActionListener(e -> showExampleGenesPopup());

        // Species combo selection
        speciesCombo.addActionListener(e -> {
            currentSpecies = (SpeciesInfo) speciesCombo.getSelectedItem();
            updatePlaceholderForSpecies(); // Update placeholder based on species
            updateSearchStatus();
        });
        
        // Export button
        exportButton.addActionListener(e -> exportAllData());

        geneStructureAdvancedButton.addActionListener(e -> openGeneStructureAdvancedView());
        
        // Close button
        closeButton.addActionListener(e -> closeDialog());
        
        // Window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> applyPrimarySearchButtonStyle());
            }

            @Override
            public void windowClosing(WindowEvent e) {
                closeDialog();
            }
        });
    }
    
    /**
     * Load species list into combo box
     */
    private void loadSpeciesList() {
        speciesCombo.removeAllItems();
        
        List<SpeciesInfo> species = speciesManager.getAllSpecies();
        for (SpeciesInfo info : species) {
            speciesCombo.addItem(info);
        }
        SimpleGenomeHubUi.setComboBoxDisplayWidth(speciesCombo, 280);
        
        if (!species.isEmpty()) {
            currentSpecies = species.get(0);
            speciesCombo.setSelectedIndex(0);
        }

        updatePlaceholderForSpecies(); // Update placeholder for initial species
        updateSearchStatus();
    }

    /**
     * Set the current species and update the UI accordingly
     */
    private void setCurrentSpecies(SpeciesInfo species) {
        if (species == null) {
            return;
        }

        // Find the species in the combo box and select it
        for (int i = 0; i < speciesCombo.getItemCount(); i++) {
            SpeciesInfo item = speciesCombo.getItemAt(i);
            if (item.getSpeciesDirectoryName().equals(species.getSpeciesDirectoryName())) {
                speciesCombo.setSelectedIndex(i);
                this.currentSpecies = species;
                updatePlaceholderForSpecies();
                updateSearchStatus();
                break;
            }
        }
    }

    /**
     * Perform gene search
     */
    private void performGeneSearch() {
        String geneId = geneIdField.getText().trim();
        
        if (geneId.isEmpty()) {
            showWarning("Please enter a gene ID to search.");
            return;
        }
        
        if (currentSpecies == null) {
            showWarning("Please select a species for the search.");
            return;
        }
        
        // Disable search during processing
        setSearchEnabled(false);
        statusLabel.setText("Searching for gene: " + geneId + "...");
        statusLabel.setForeground(Color.BLUE);

        final long generation = searchGeneration.incrementAndGet();
        final SpeciesInfo searchSpecies = currentSpecies;
        final String searchedGeneId = geneId;
        
        annotationPanel.showLoadingState(geneId);

        // Perform the fast search in background thread
        SwingWorker<GeneSearchResult, Void> worker = new SwingWorker<GeneSearchResult, Void>() {
            @Override
            protected GeneSearchResult doInBackground() throws Exception {
                return dataRetriever.searchGeneCore(searchedGeneId, searchSpecies);
            }
            
            @Override
            protected void done() {
                try {
                    GeneSearchResult result = get();
                    if (generation != searchGeneration.get()) {
                        return;
                    }
                    handleCoreSearchResult(generation, searchedGeneId, searchSpecies, result);
                } catch (Exception e) {
                    logger.severe("Gene search failed: " + e.getMessage());
                    handleSearchError(searchedGeneId, e);
                } finally {
                    setSearchEnabled(true);
                }
            }
        };
        
        worker.execute();
    }
    
    /**
     * Handle successful search result
     */
    private void handleCoreSearchResult(long generation, String searchedGeneId, SpeciesInfo searchSpecies, GeneSearchResult result) {
        if (generation != searchGeneration.get()) {
            return;
        }

        if (result == null) {
            handleSearchError(searchedGeneId, new IllegalStateException("Empty search result"));
            return;
        }

        currentGeneId = result.getResolvedGeneId();

        // Show the fast results immediately.
        sequencePanel.loadGeneData(result);
        expressionPanel.loadGeneData(result);
        annotationPanel.showLoadingState(result.getResolvedGeneId());
        
        // Keep the UI interactive while annotations continue loading.
        setTabsEnabled(true);
        geneStructureAdvancedButton.setEnabled(true);

        if (result.hasSequenceData() || result.hasExpressionData()) {
            statusLabel.setText("Core data loaded for: " + searchedGeneId + " (annotations loading...)");
            statusLabel.setForeground(new Color(0, 128, 0));
        } else {
            statusLabel.setText("Searching annotations for: " + searchedGeneId + "...");
            statusLabel.setForeground(Color.BLUE);
        }

        startAnnotationLoad(generation, searchedGeneId, searchSpecies, result);

        logger.info("Loaded core gene information for: " + searchedGeneId);
    }
    
    /**
     * Handle search error
     */
    private void handleSearchError(String geneId, Exception error) {
        statusLabel.setText("Search error for gene: " + geneId + " - " + error.getMessage());
        statusLabel.setForeground(Color.RED);
        setTabsEnabled(false);
        exportButton.setEnabled(false);
        
        showError("Gene search failed", "Failed to search for gene: " + geneId + "\n\nError: " + error.getMessage());
    }

    private void startAnnotationLoad(long generation, String searchedGeneId, SpeciesInfo searchSpecies, GeneSearchResult coreResult) {
        SwingWorker<List<GeneAnnotationData.GeneAnnotation>, Void> annotationWorker =
            new SwingWorker<List<GeneAnnotationData.GeneAnnotation>, Void>() {
                @Override
                protected List<GeneAnnotationData.GeneAnnotation> doInBackground() {
                    return dataRetriever.getGeneAnnotations(coreResult.getResolvedGeneId(), searchSpecies);
                }

                @Override
                protected void done() {
                    if (generation != searchGeneration.get()) {
                        return;
                    }

                    try {
                        List<GeneAnnotationData.GeneAnnotation> annotations = get();
                        handleAnnotationSearchResult(generation, searchedGeneId, coreResult, annotations);
                    } catch (Exception e) {
                        logger.warning("Annotation loading failed for " + searchedGeneId + ": " + e.getMessage());
                        handleAnnotationSearchError(generation, searchedGeneId, coreResult, e);
                    }
                }
            };

        annotationWorker.execute();
    }

    private void handleAnnotationSearchResult(long generation, String searchedGeneId, GeneSearchResult result,
                                              List<GeneAnnotationData.GeneAnnotation> annotations) {
        if (generation != searchGeneration.get()) {
            return;
        }

        result.setAnnotations(annotations);
        annotationPanel.loadGeneData(result);

        boolean hasAnyData = result.hasSequenceData() || result.hasExpressionData() || !annotations.isEmpty();
        if (hasAnyData) {
            statusLabel.setText("Gene found: " + searchedGeneId + " - annotations loaded");
            statusLabel.setForeground(new Color(0, 128, 0));
            exportButton.setEnabled(true);
        } else {
            statusLabel.setText("Gene not found: " + searchedGeneId);
            statusLabel.setForeground(Color.RED);
            setTabsEnabled(false);
            geneStructureAdvancedButton.setEnabled(false);
            exportButton.setEnabled(false);
        }
    }

    private void handleAnnotationSearchError(long generation, String searchedGeneId, GeneSearchResult result, Exception error) {
        if (generation != searchGeneration.get()) {
            return;
        }

        annotationPanel.clearData();
        if (result.hasSequenceData() || result.hasExpressionData()) {
            statusLabel.setText("Core data loaded for: " + searchedGeneId + " (annotation load failed)");
            statusLabel.setForeground(new Color(184, 134, 11));
            exportButton.setEnabled(true);
        } else {
            statusLabel.setText("Search error for gene: " + searchedGeneId + " - " + error.getMessage());
            statusLabel.setForeground(Color.RED);
            setTabsEnabled(false);
            geneStructureAdvancedButton.setEnabled(false);
            exportButton.setEnabled(false);
        }
    }
    
    /**
     * Clear all data and reset interface
     */
    private void clearAllData() {
        // Clear input
        geneIdField.setText("");
        currentGeneId = null;
        
        // Clear panels
        sequencePanel.clearData();
        annotationPanel.clearData();
        expressionPanel.clearData();
        
        // Reset interface state
        setTabsEnabled(false);
        geneStructureAdvancedButton.setEnabled(false);
        exportButton.setEnabled(false);
        updateSearchStatus();
        
        // Focus on gene ID field
        geneIdField.requestFocus();
    }
    
    /**
     * Export all gene data
     */
    private void exportAllData() {
        if (currentGeneId == null || currentSpecies == null) {
            showWarning("No gene data available to export.");
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Export Directory");
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File exportDir = fileChooser.getSelectedFile();
                String exportPrefix = currentGeneId + "_" + currentSpecies.getSpeciesName().replaceAll("[\\s\\.]+", "_");
                
                // Export from each panel
                sequencePanel.exportData(exportDir, exportPrefix);
                annotationPanel.exportData(exportDir, exportPrefix);
                expressionPanel.exportData(exportDir, exportPrefix);
                
                showInfo("Export Successful", 
                    "Gene data exported successfully to:\n" + exportDir.getAbsolutePath() + 
                    "\n\nFiles exported with prefix: " + exportPrefix);
                
            } catch (Exception e) {
                logger.severe("Export failed: " + e.getMessage());
                showError("Export Failed", "Failed to export gene data:\n" + e.getMessage());
            }
        }
    }
    
    /**
     * Close dialog with confirmation
     */
    private void closeDialog() {
        // No unsaved data to worry about, just close
        dispose();
    }

    private void openGeneStructureAdvancedView() {
        if (currentGeneId == null || currentSpecies == null) {
            showWarning("Please search for a gene first.");
            return;
        }

        File annotationFile = currentSpecies.getAnnotationFile();
        if (annotationFile == null || !annotationFile.isFile()) {
            showWarning("No annotation file is available for the current species.");
            return;
        }

        try {
            String geneGffContent = extractGeneGffContent(currentGeneId, annotationFile);
            if (geneGffContent.trim().isEmpty()) {
                showWarning("No GFF3 records were found for gene: " + currentGeneId);
                return;
            }

            String tempSuffix = annotationFile.getName().toLowerCase(Locale.ROOT).endsWith(".gtf") ? ".gtf" : ".gff3";
            File tempGff = File.createTempFile("sgh_gene_structure_", tempSuffix);
            tempGff.deleteOnExit();
            try (PrintWriter writer = new PrintWriter(new FileWriter(tempGff))) {
                if (".gff3".equals(tempSuffix)) {
                    writer.println("##gff-version 3");
                }
                writer.print(geneGffContent);
            }

            showGeneStructureAdvancedGraph(tempGff);
        } catch (Exception e) {
            logger.severe("Failed to open Gene Structure View (Advanced): " + e.getMessage());
            showError("Open Failed", "Failed to open Gene Structure View (Advanced):\n" + e.getMessage());
        }
    }

    private void showGeneStructureAdvancedGraph(File tempAnnotationFile) throws Exception {
        DrawGeneStructureFromGXFfile drawer = new DrawGeneStructureFromGXFfile();
        drawer.setInFile(tempAnnotationFile);
        drawer.setShowChrName(false);

        int graphWidth = 1000;
        int graphHeight = 680;
        JIGBasePanel basePanel = new JIGBasePanel(graphWidth, graphHeight);
        JIGSubPanel subPanel = drawer.postGraph("", basePanel);
        if (subPanel == null) {
            throw new Exception("TBtools failed to create the gene structure graph.");
        }

        subPanel.setDrawColor(null);
        java.awt.geom.Point2D[] points = subPanel.getPoints();
        if (points != null && points.length >= 2) {
            points[0].setLocation(140.0d, graphHeight);
            points[1].setLocation(graphWidth, 20.0d);
        }

        enlargeGeneStructureGraphText(subPanel);

        basePanel.addSubPanel(subPanel);
        basePanel.addMouseWheelListener(basePanel);
        basePanel.addMouseListener(basePanel);
        basePanel.addMouseMotionListener(basePanel);
        basePanel.setPreferredSize(new Dimension(graphWidth, graphHeight));

        JDialog dialog = new JDialog(this,
            "TBtools - Gene Structure View (Advanced) - " + currentGeneId,
            Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(basePanel), BorderLayout.CENTER);

        JPanel toolPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        toolPanel.add(basePanel.getUndoButton());
        toolPanel.add(basePanel.getRedoButton());
        toolPanel.add(basePanel.getSaveGraphButton());
        dialog.add(toolPanel, BorderLayout.SOUTH);

        dialog.setSize(1180, 820);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void enlargeGeneStructureGraphText(JIGSubPanel subPanel) {
        if (subPanel == null || subPanel.getElementList() == null) {
            return;
        }

        for (Object obj : subPanel.getElementList()) {
            if (!(obj instanceof jigplot.engine.JIGElement)) {
                continue;
            }

            jigplot.engine.JIGElement element = (jigplot.engine.JIGElement) obj;
            String text = element.getText();
            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            Font font = element.getFont();
            if (font == null) {
                element.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_14);
                continue;
            }

            int newSize = Math.max(font.getSize() + 4, 14);
            element.setFont(SimpleGenomeHubStyle.resize(font, (float) newSize));
        }
    }

    private String extractGeneGffContent(String geneId, File annotationFile) throws Exception {
        List<AnnotationRecord> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(annotationFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                String[] fields = line.split("\t");
                if (fields.length < 9) {
                    continue;
                }

                Map<String, String> attrMap = parseGxfAttributes(fields[8]);
                String featureType = fields[2].toLowerCase(Locale.ROOT);
                String id = extractFeatureId(attrMap, featureType);
                List<String> parentIds = extractParentIds(attrMap, featureType);
                records.add(new AnnotationRecord(line, id, parentIds));
            }
        }

        LinkedHashSet<AnnotationRecord> selectedRecords = new LinkedHashSet<>();
        LinkedHashSet<String> selectedIds = new LinkedHashSet<>();

        for (AnnotationRecord record : records) {
            if (matchesGeneId(geneId, record.id) || containsNormalized(record.parentIds, geneId)) {
                if (selectedRecords.add(record) && record.id != null && !record.id.isEmpty()) {
                    selectedIds.add(record.id);
                }
            }
        }

        boolean changed;
        do {
            changed = false;
            Set<String> requiredParentIds = new LinkedHashSet<>();
            for (AnnotationRecord record : selectedRecords) {
                requiredParentIds.addAll(record.parentIds);
            }

            for (AnnotationRecord record : records) {
                if (selectedRecords.contains(record)) {
                    continue;
                }

                boolean isAncestor = record.id != null && requiredParentIds.contains(record.id);
                boolean isDescendant = containsExact(record.parentIds, selectedIds);
                if (isAncestor || isDescendant) {
                    selectedRecords.add(record);
                    if (record.id != null && !record.id.isEmpty()) {
                        selectedIds.add(record.id);
                    }
                    changed = true;
                }
            }
        } while (changed);

        StringBuilder sb = new StringBuilder();
        for (AnnotationRecord record : records) {
            if (selectedRecords.contains(record)) {
                sb.append(record.line).append("\n");
            }
        }

        return sb.toString();
    }

    private Map<String, String> parseGxfAttributes(String attributes) {
        Map<String, String> attrMap = new HashMap<>();
        if (attributes == null) {
            return attrMap;
        }

        if (attributes.contains("=")) {
            for (String pair : attributes.split(";")) {
                String[] kv = pair.trim().split("=", 2);
                if (kv.length == 2) {
                    attrMap.put(kv[0].trim(), kv[1].trim());
                }
            }
        } else {
            for (String pair : attributes.split(";")) {
                pair = pair.trim();
                if (pair.contains(" \"")) {
                    String[] kv = pair.split(" \"", 2);
                    if (kv.length == 2) {
                        attrMap.put(kv[0].trim(), kv[1].replace("\"", "").trim());
                    }
                }
            }
        }

        return attrMap;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean containsNormalized(Collection<String> values, String candidate) {
        for (String value : values) {
            if (matchesGeneId(value, candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsExact(Collection<String> values, Collection<String> candidates) {
        for (String candidate : candidates) {
            if (candidate != null && values.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGeneId(String queryGeneId, String candidate) {
        if (queryGeneId == null || candidate == null) {
            return false;
        }
        return normalizeGeneId(queryGeneId).equals(normalizeGeneId(candidate));
    }

    private String normalizeGeneId(String value) {
        String normalized = value == null ? "" : value.trim();
        normalized = normalized.replaceAll("(?i)(\\.t\\d+|_t\\d+)$", "");
        normalized = normalized.replaceAll("\\.\\d+$", "");
        return normalized;
    }

    private String extractFeatureId(Map<String, String> attrMap, String featureType) {
        if ("gene".equals(featureType)) {
            return firstNonEmpty(attrMap.get("ID"), attrMap.get("gene_id"), attrMap.get("Name"));
        }
        if ("mrna".equals(featureType) || "transcript".equals(featureType)) {
            return firstNonEmpty(attrMap.get("ID"), attrMap.get("transcript_id"), attrMap.get("gene_id"), attrMap.get("Name"));
        }
        return firstNonEmpty(attrMap.get("ID"), attrMap.get("transcript_id"), attrMap.get("gene_id"), attrMap.get("Name"));
    }

    private List<String> extractParentIds(Map<String, String> attrMap, String featureType) {
        LinkedHashSet<String> parentIds = new LinkedHashSet<>();
        addDelimitedIds(parentIds, attrMap.get("Parent"));

        if (parentIds.isEmpty()) {
            if ("mrna".equals(featureType) || "transcript".equals(featureType)) {
                addDelimitedIds(parentIds, attrMap.get("gene_id"));
            } else {
                addDelimitedIds(parentIds, attrMap.get("transcript_id"));
                if (parentIds.isEmpty()) {
                    addDelimitedIds(parentIds, attrMap.get("gene_id"));
                }
            }
        }

        return new ArrayList<>(parentIds);
    }

    private void addDelimitedIds(Set<String> target, String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return;
        }

        for (String value : rawValue.split(",")) {
            String cleaned = value.trim();
            if (!cleaned.isEmpty()) {
                target.add(cleaned);
            }
        }
    }

    private static final class AnnotationRecord {
        private final String line;
        private final String id;
        private final List<String> parentIds;

        private AnnotationRecord(String line, String id, List<String> parentIds) {
            this.line = line;
            this.id = id;
            this.parentIds = parentIds;
        }
    }
    
    /**
     * Update search status display
     */
    private void updateSearchStatus() {
        if (currentSpecies == null) {
            statusLabel.setText("No species available. Please import species data first.");
            statusLabel.setForeground(Color.RED);
            setSearchEnabled(false);
        } else {
            statusLabel.setText("Enter a gene ID and click Search (Species: " + currentSpecies.getDisplayName() + ")");
            statusLabel.setForeground(Color.BLUE);
            setSearchEnabled(true);
        }
    }
    
    /**
     * Enable/disable search controls
     */
    private void setSearchEnabled(boolean enabled) {
        geneIdField.setEnabled(enabled);
        speciesCombo.setEnabled(enabled);
        searchButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
        exampleButton.setEnabled(enabled && currentSpecies != null);
    }

    private void applyPrimarySearchButtonStyle() {
        if (searchButton == null) {
            return;
        }

        searchButton.setBackground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON);
        searchButton.setForeground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_TEXT);
        searchButton.setBorder(BorderFactory.createLineBorder(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_BORDER, 1));
        searchButton.setFocusPainted(false);
        searchButton.setOpaque(true);
        searchButton.setContentAreaFilled(true);
        searchButton.repaint();
    }

    /**
     * Initialize example gene IDs for a species
     */
    private void initializeExampleGenes(String speciesKey) {
        if (speciesExampleGenes.containsKey(speciesKey)) {
            return; // Already initialized
        }

        java.util.List<String> examples = new java.util.ArrayList<>();

        // Always try to extract from actual species data first (优先从真实数据提取)
        java.util.List<String> dynamicExamples = extractExampleGenesFromSpecies(speciesKey);
        if (!dynamicExamples.isEmpty()) {
            examples.addAll(dynamicExamples.subList(0, Math.min(5, dynamicExamples.size())));
            speciesExamplePlaceholders.put(speciesKey, "e.g., " + String.join(", ", examples.subList(0, Math.min(3, examples.size()))));
        } else {
            // Fallback to predefined examples only if no real data available
            if (speciesKey.contains("Arabidopsis") || speciesKey.contains("thaliana")) {
                examples.add("AT1G01010");
                examples.add("AT1G01020");
                examples.add("AT1G01030");
                examples.add("AT1G01040");
                examples.add("AT2G01010");
                speciesExamplePlaceholders.put(speciesKey, "e.g., AT1G01010, AT1G01020");
            } else if (speciesKey.contains("Ananas") || speciesKey.contains("comosus")) {
                examples.add("lcfv2_00788");
                examples.add("lcfv2_18097");
                examples.add("lcfv2_26534");
                examples.add("lcfv2_01236");
                examples.add("lcfv2_25203");
                speciesExamplePlaceholders.put(speciesKey, "e.g., lcfv2_00788, lcfv2_18097");
            } else {
                // Last resort generic examples
                examples.add("Gene001");
                examples.add("Gene002");
                examples.add("Gene003");
                examples.add("Gene004");
                examples.add("Gene005");
                speciesExamplePlaceholders.put(speciesKey, "e.g., Gene001, Gene002");
            }
        }

        speciesExampleGenes.put(speciesKey, examples);
        logger.info("Initialized " + examples.size() + " example genes for species: " + speciesKey +
                   " (from " + (dynamicExamples.isEmpty() ? "predefined" : "real data") + ")");
    }

    /**
     * Extract example gene IDs from species data
     */
    private java.util.List<String> extractExampleGenesFromSpecies(String speciesKey) {
        java.util.List<String> examples = new java.util.ArrayList<>();

        try {
            // Find the exact species that matches the key
            SpeciesInfo targetSpecies = findMatchingSpecies(speciesKey);
            if (targetSpecies == null) {
                logger.warning("Could not find matching species for key: " + speciesKey);
                return examples;
            }

            logger.info("Extracting gene IDs from species: " + targetSpecies.getDisplayName());

            // Try multiple sources to get gene IDs
            examples.addAll(extractGeneIdsFromSequenceFiles(targetSpecies));

            if (examples.size() < 5) {
                // If not enough from sequence files, try annotation files
                examples.addAll(extractGeneIdsFromAnnotationFiles(targetSpecies));
            }

            if (examples.size() < 5) {
                // If still not enough, try GFF files
                examples.addAll(extractGeneIdsFromGffFiles(targetSpecies));
            }

            // Remove duplicates and limit to 5-10 examples
            java.util.Set<String> uniqueExamples = new java.util.LinkedHashSet<>(examples);
            java.util.List<String> uniqueList = new java.util.ArrayList<>(uniqueExamples);
            examples.clear();
            examples.addAll(uniqueList.subList(0, Math.min(10, uniqueList.size())));

        } catch (Exception e) {
            logger.warning("Could not extract example genes from species data: " + e.getMessage());
        }

        return examples;
    }

    /**
     * Find the species that best matches the given key
     */
    private SpeciesInfo findMatchingSpecies(String speciesKey) {
        for (SpeciesInfo species : speciesManager.getAllSpecies()) {
            // Exact match on species directory name (most precise - includes version)
            if (species.getSpeciesDirectoryName().equals(speciesKey)) {
                return species;
            }

            // Fallback: match on display name (more specific)
            if (species.getDisplayName().toLowerCase().contains(speciesKey.toLowerCase())) {
                return species;
            }

            // Last resort: match on species name contains (less precise)
            if (species.getSpeciesName().toLowerCase().contains(speciesKey.toLowerCase())) {
                return species;
            }
        }
        return null;
    }

    /**
     * Extract gene IDs from sequence files
     */
    private java.util.List<String> extractGeneIdsFromSequenceFiles(SpeciesInfo species) {
        java.util.List<String> examples = new java.util.ArrayList<>();

        try {
            // Use the species directory path from SpeciesInfo instead of just the name
            java.io.File speciesDir = species.getSpeciesDir();
            if (speciesDir == null || !speciesDir.exists()) {
                return examples;
            }

            java.io.File sequenceDir = new java.io.File(speciesDir, "Sequence");
            if (!sequenceDir.exists()) {
                return examples;
            }

            // Look for transcript files first (most likely to have gene IDs)
            java.io.File[] transcriptFiles = sequenceDir.listFiles((dir, name) ->
                name.toLowerCase().contains("transcript") &&
                (name.endsWith(".fasta") || name.endsWith(".fa")));

            if (transcriptFiles != null && transcriptFiles.length > 0) {
                examples.addAll(extractGeneIdsFromFasta(transcriptFiles[0], 5));
            }

            // If no transcript files, try any FASTA files
            if (examples.isEmpty()) {
                java.io.File[] fastaFiles = sequenceDir.listFiles((dir, name) ->
                    name.endsWith(".fasta") || name.endsWith(".fa"));

                if (fastaFiles != null && fastaFiles.length > 0) {
                    examples.addAll(extractGeneIdsFromFasta(fastaFiles[0], 5));
                }
            }
        } catch (Exception e) {
            logger.warning("Error extracting from sequence files: " + e.getMessage());
        }

        return examples;
    }

    /**
     * Extract gene IDs from annotation files
     */
    private java.util.List<String> extractGeneIdsFromAnnotationFiles(SpeciesInfo species) {
        java.util.List<String> examples = new java.util.ArrayList<>();

        try {
            // Use the species directory path from SpeciesInfo
            java.io.File speciesDir = species.getSpeciesDir();
            if (speciesDir == null || !speciesDir.exists()) {
                return examples;
            }

            java.io.File annotationDir = new java.io.File(speciesDir, "Annotation");
            if (!annotationDir.exists()) {
                return examples;
            }

            // Look for TSV files that might contain gene IDs
            java.io.File[] tsvFiles = annotationDir.listFiles((dir, name) ->
                name.endsWith(".tsv") || name.endsWith(".txt"));

            if (tsvFiles != null && tsvFiles.length > 0) {
                examples.addAll(extractGeneIdsFromTsv(tsvFiles[0], 5));
            }
        } catch (Exception e) {
            logger.warning("Error extracting from annotation files: " + e.getMessage());
        }

        return examples;
    }

    /**
     * Extract gene IDs from GFF files
     */
    private java.util.List<String> extractGeneIdsFromGffFiles(SpeciesInfo species) {
        java.util.List<String> examples = new java.util.ArrayList<>();

        try {
            // Use the species directory path from SpeciesInfo
            java.io.File speciesDir = species.getSpeciesDir();
            if (speciesDir == null || !speciesDir.exists()) {
                return examples;
            }

            // Look for GFF files in the species directory
            java.io.File[] gffFiles = speciesDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".gff") || name.toLowerCase().endsWith(".gff3"));

            if (gffFiles != null && gffFiles.length > 0) {
                examples.addAll(extractGeneIdsFromGff(gffFiles[0], 5));
            }
        } catch (Exception e) {
            logger.warning("Error extracting from GFF files: " + e.getMessage());
        }

        return examples;
    }

    /**
     * Extract gene IDs from TSV file
     */
    private java.util.List<String> extractGeneIdsFromTsv(java.io.File tsvFile, int maxCount) {
        java.util.List<String> geneIds = new java.util.ArrayList<>();

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(tsvFile))) {
            String line;
            int count = 0;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null && count < maxCount) {
                if (firstLine) {
                    firstLine = false; // Skip header
                    continue;
                }

                String[] columns = line.split("\t");
                if (columns.length > 0) {
                    String geneId = columns[0].trim();
                    if (!geneId.isEmpty() && !geneId.startsWith("#")) {
                        geneIds.add(geneId);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error reading TSV file " + tsvFile.getName() + ": " + e.getMessage());
        }

        return geneIds;
    }

    /**
     * Extract gene IDs from GFF file
     */
    private java.util.List<String> extractGeneIdsFromGff(java.io.File gffFile, int maxCount) {
        java.util.List<String> geneIds = new java.util.ArrayList<>();

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(gffFile))) {
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null && count < maxCount) {
                if (line.startsWith("#")) {
                    continue; // Skip comments
                }

                String[] columns = line.split("\t");
                if (columns.length >= 9 && columns[2].equals("gene")) {
                    String attributes = columns[8];
                    String[] attrParts = attributes.split(";");
                    for (String attr : attrParts) {
                        if (attr.startsWith("ID=") || attr.startsWith("gene_id=")) {
                            String geneId = attr.split("=")[1].trim();
                            geneIds.add(geneId);
                            count++;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error reading GFF file " + gffFile.getName() + ": " + e.getMessage());
        }

        return geneIds;
    }

    /**
     * Extract gene IDs from FASTA file
     */
    private java.util.List<String> extractGeneIdsFromFasta(java.io.File fastaFile, int maxCount) {
        java.util.List<String> geneIds = new java.util.ArrayList<>();

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(fastaFile))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < maxCount) {
                if (line.startsWith(">")) {
                    // Extract gene ID from header
                    String header = line.substring(1).trim();
                    String geneId = header.split("\\s+")[0]; // Take first token
                    if (!geneId.isEmpty()) {
                        geneIds.add(geneId);
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Error reading FASTA file " + fastaFile.getName() + ": " + e.getMessage());
        }

        return geneIds;
    }

    /**
     * Update placeholder text based on selected species
     */
    private void updatePlaceholderForSpecies() {
        if (currentSpecies == null) {
            geneIdField.setPlaceholder("Select species to see examples");
            exampleButton.setEnabled(false);
        } else {
            // Use species directory name as key for precise version matching
            String speciesKey = currentSpecies.getSpeciesDirectoryName();
            initializeExampleGenes(speciesKey);

            String placeholder = speciesExamplePlaceholders.get(speciesKey);
            if (placeholder != null) {
                geneIdField.setPlaceholder(placeholder);
            } else {
                geneIdField.setPlaceholder("Enter gene ID (e.g., " + currentSpecies.getSpeciesName() + "_gene001)");
            }
            exampleButton.setEnabled(true);
        }
    }

    /**
     * Show example gene IDs popup menu
     */
    private void showExampleGenesPopup() {
        if (currentSpecies == null) {
            showWarning("Please select a species first to see example gene IDs.");
            return;
        }

        String speciesKey = currentSpecies.getSpeciesDirectoryName();
        initializeExampleGenes(speciesKey);

        java.util.List<String> examples = speciesExampleGenes.get(speciesKey);
        if (examples == null || examples.isEmpty()) {
            showWarning("No example gene IDs available for this species.");
            return;
        }

        // Create popup menu
        javax.swing.JPopupMenu popupMenu = new javax.swing.JPopupMenu("Example Gene IDs");

        // Add header
        javax.swing.JLabel headerLabel = new javax.swing.JLabel("Example Gene IDs for " + currentSpecies.getDisplayName());
        headerLabel.setFont(SimpleGenomeHubStyle.bold(headerLabel.getFont(), 12f));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        popupMenu.add(headerLabel);
        popupMenu.addSeparator();

        // Add example gene IDs
        for (String geneId : examples) {
            javax.swing.JMenuItem geneItem = new javax.swing.JMenuItem(geneId);
            geneItem.addActionListener(e -> {
                geneIdField.setText(geneId);
                geneIdField.requestFocus();
                // Optionally trigger search automatically
                // searchForGene();
            });
            popupMenu.add(geneItem);
        }

        // Show popup menu below the example button
        popupMenu.show(exampleButton, 0, exampleButton.getHeight());
    }
    
    /**
     * Enable/disable main tabs
     */
    private void setTabsEnabled(boolean enabled) {
        for (int i = 0; i < mainTabs.getTabCount(); i++) {
            mainTabs.setEnabledAt(i, enabled);
        }
    }
    
    /**
     * Create tab icon (placeholder for now)
     */
    private Icon createTabIcon(String type) {
        // Simple colored circle as icon
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Color color;
                switch (type) {
                    case "sequence":
                        color = new Color(70, 130, 180);    // Steel blue
                        break;
                    case "annotation":
                        color = new Color(255, 140, 0);     // Dark orange
                        break;
                    case "expression":
                        color = new Color(220, 20, 60);     // Crimson
                        break;
                    case "structure":
                        color = new Color(50, 205, 50);     // Lime green
                        break;
                    default:
                        color = Color.GRAY;
                        break;
                }
                
                g.setColor(color);
                g.fillOval(x, y, 12, 12);
                g.setColor(color.darker());
                g.drawOval(x, y, 12, 12);
            }
            
            @Override
            public int getIconWidth() { return 12; }
            
            @Override
            public int getIconHeight() { return 12; }
        };
    }
    
    // Utility methods for showing dialogs
    private void showInfo(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }
    
    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void openBlastAnalysisForSequence(SpeciesInfo species, BlastConfig.SequenceType sequenceType,
                                              String fastaSequence) {
        if (species == null) {
            showWarning("Please select a database/species before running BLAST.");
            return;
        }

        if (fastaSequence == null || fastaSequence.trim().isEmpty()) {
            showWarning("No sequence is available to send to BLAST.");
            return;
        }

        try {
            BlastDialog dialog = new BlastDialog(this, species, speciesManager, sequenceType, fastaSequence);
            dialog.setVisible(true);
        } catch (Exception e) {
            logger.severe("Failed to open BLAST Analysis dialog: " + e.getMessage());
            showError("BLAST Analysis Error", "Failed to open BLAST Analysis:\n" + e.getMessage());
        }
    }
    
    // Inner classes for panel components will be implemented separately
    
    /**
     * Sequence information display panel
     */
    private class SequenceInfoPanel extends JPanel {
        
        private JTabbedPane sequenceTabs;
        private JTextArea transcriptArea;
        private JTextArea cdsArea;
        private JTextArea proteinArea;
        private JLabel statsLabel;
        private JButton exportButton;
        private JButton runBlastButton;
        private GeneSearchResult currentResult;
        
        public SequenceInfoPanel() {
            initializeComponents();
            setupLayout();
            setupEventHandlers();
        }
        
        private void initializeComponents() {
            sequenceTabs = new JTabbedPane();
            
            // Create text areas for different sequence types
            transcriptArea = new JTextArea(15, 80);
            transcriptArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
            transcriptArea.setEditable(false);
            transcriptArea.setLineWrap(true);
            transcriptArea.setWrapStyleWord(false);
            
            cdsArea = new JTextArea(15, 80);
            cdsArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
            cdsArea.setEditable(false);
            cdsArea.setLineWrap(true);
            cdsArea.setWrapStyleWord(false);
            
            proteinArea = new JTextArea(15, 80);
            proteinArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
            proteinArea.setEditable(false);
            proteinArea.setLineWrap(true);
            proteinArea.setWrapStyleWord(false);
            
            // Add scroll panes
            sequenceTabs.addTab("Transcript", new JScrollPane(transcriptArea));
            sequenceTabs.addTab("CDS", new JScrollPane(cdsArea));
            sequenceTabs.addTab("Protein", new JScrollPane(proteinArea));
            
            // Stats and controls
            statsLabel = new JLabel("No sequence data loaded");
            exportButton = new JButton("Export Sequences");
            exportButton.setEnabled(false);
            runBlastButton = new JButton("Run BLAST Analysis");
            runBlastButton.setEnabled(false);
        }
        
        private void setupLayout() {
            setLayout(new BorderLayout());
            
            add(sequenceTabs, BorderLayout.CENTER);
            
            // Bottom panel for stats and controls
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.setBorder(BorderFactory.createEtchedBorder());
            bottomPanel.add(statsLabel, BorderLayout.WEST);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
            buttonPanel.add(runBlastButton);
            buttonPanel.add(exportButton);
            bottomPanel.add(buttonPanel, BorderLayout.EAST);

            add(bottomPanel, BorderLayout.SOUTH);
        }

        private void setupEventHandlers() {
            exportButton.addActionListener(e -> exportSequences());
            runBlastButton.addActionListener(e -> runBlastAnalysis());
            sequenceTabs.addChangeListener(e -> updateBlastButtonState());
        }
        
        public void loadGeneData(GeneSearchResult result) {
            this.currentResult = result;
            
            if (result == null || !result.hasSequenceData()) {
                clearData();
                statsLabel.setText("No sequence data available for gene: " + 
                    (result != null ? result.getGeneId() : "unknown"));
                return;
            }
            
            Map<String, String> sequences = result.getSequences();
            
            // Load transcript sequence
            String transcript = sequences.get("transcript");
            String transcriptHeader = sequences.get("transcript_header");
            if (transcript != null && !transcript.isEmpty()) {
                transcriptArea.setText((transcriptHeader != null ? transcriptHeader + "\n" : "") + 
                                     formatSequence(transcript));
            } else {
                transcriptArea.setText("No transcript sequence available");
            }
            
            // Load CDS sequence
            String cds = sequences.get("cds");
            String cdsHeader = sequences.get("cds_header");
            if (cds != null && !cds.isEmpty()) {
                cdsArea.setText((cdsHeader != null ? cdsHeader + "\n" : "") + 
                               formatSequence(cds));
            } else {
                cdsArea.setText("No CDS sequence available");
            }
            
            // Load protein sequence
            String protein = sequences.get("protein");
            String proteinHeader = sequences.get("protein_header");
            if (protein != null && !protein.isEmpty()) {
                proteinArea.setText((proteinHeader != null ? proteinHeader + "\n" : "") + 
                                   formatSequence(protein));
            } else {
                proteinArea.setText("No protein sequence available");
            }
            
            // Update stats
            updateStats(sequences);
            exportButton.setEnabled(true);
            updateBlastButtonState();
        }
        
        private String formatSequence(String sequence) {
            if (sequence == null || sequence.isEmpty()) {
                return "";
            }
            
            // Format sequence with 80 characters per line
            StringBuilder formatted = new StringBuilder();
            for (int i = 0; i < sequence.length(); i += 80) {
                int end = Math.min(i + 80, sequence.length());
                formatted.append(sequence.substring(i, end)).append("\n");
            }
            return formatted.toString();
        }
        
        private void updateStats(Map<String, String> sequences) {
            int transcriptLen = getSequenceLength(sequences.get("transcript"));
            int cdsLen = getSequenceLength(sequences.get("cds"));
            int proteinLen = getSequenceLength(sequences.get("protein"));
            
            String stats = String.format("Sequences loaded - Transcript: %d bp, CDS: %d bp, Protein: %d aa",
                transcriptLen, cdsLen, proteinLen);
            statsLabel.setText(stats);
        }
        
        private int getSequenceLength(String sequence) {
            return sequence != null ? sequence.replaceAll("\\s", "").length() : 0;
        }

        private void runBlastAnalysis() {
            BlastSequenceSelection selection = getCurrentBlastSequenceSelection();
            if (selection == null) {
                updateBlastButtonState();
                return;
            }

            GeneInfoViewerDialog.this.openBlastAnalysisForSequence(
                currentResult != null ? currentResult.getSpecies() : null,
                selection.sequenceType,
                selection.fastaSequence
            );
        }

        private BlastSequenceSelection getCurrentBlastSequenceSelection() {
            if (currentResult == null || !currentResult.hasSequenceData()) {
                return null;
            }

            int selectedIndex = sequenceTabs.getSelectedIndex();
            String sequenceKey;
            BlastConfig.SequenceType sequenceType;
            String defaultHeaderSuffix;

            switch (selectedIndex) {
                case 0:
                    sequenceKey = "transcript";
                    sequenceType = BlastConfig.SequenceType.TRANSCRIPT;
                    defaultHeaderSuffix = "transcript";
                    break;
                case 1:
                    sequenceKey = "cds";
                    sequenceType = BlastConfig.SequenceType.CDS;
                    defaultHeaderSuffix = "cds";
                    break;
                case 2:
                    sequenceKey = "protein";
                    sequenceType = BlastConfig.SequenceType.PROTEIN;
                    defaultHeaderSuffix = "protein";
                    break;
                default:
                    return null;
            }

            String sequence = currentResult.getSequence(sequenceKey);
            if (sequence == null || sequence.trim().isEmpty()) {
                return null;
            }

            String header = currentResult.getSequenceHeader(sequenceKey);
            String fastaSequence = buildFastaSequence(header,
                currentResult.getGeneId() + "_" + defaultHeaderSuffix, sequence);
            return new BlastSequenceSelection(sequenceType, fastaSequence);
        }

        private String buildFastaSequence(String header, String fallbackHeader, String sequence) {
            String cleanedSequence = sequence != null ? sequence.replaceAll("\\s", "") : "";
            if (cleanedSequence.isEmpty()) {
                return "";
            }

            String headerLine = (header != null && !header.trim().isEmpty()) ? header.trim() : ">" + fallbackHeader;
            if (!headerLine.startsWith(">")) {
                headerLine = ">" + headerLine;
            }

            return headerLine + "\n" + formatSequence(cleanedSequence).trim();
        }

        private void updateBlastButtonState() {
            runBlastButton.setEnabled(getCurrentBlastSequenceSelection() != null);
        }
        
        private void exportSequences() {
            if (currentResult == null) return;
            
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Sequences");
            fileChooser.setSelectedFile(new File(currentResult.getGeneId() + "_sequences.fasta"));
            
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    File outputFile = fileChooser.getSelectedFile();
                    exportSequencesToFile(outputFile);
                    JOptionPane.showMessageDialog(this, 
                        "Sequences exported successfully to:\n" + outputFile.getAbsolutePath(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this,
                        "Failed to export sequences:\n" + e.getMessage(),
                        "Export Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        private void exportSequencesToFile(File outputFile) throws Exception {
            Map<String, String> sequences = currentResult.getSequences();
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                // Export transcript
                String transcript = sequences.get("transcript");
                if (transcript != null && !transcript.isEmpty()) {
                    writer.println(resolveExportHeader("transcript", currentResult.getGeneId() + "_transcript"));
                    writer.println(transcript.replaceAll("\\s", ""));
                }
                
                // Export CDS
                String cds = sequences.get("cds");
                if (cds != null && !cds.isEmpty()) {
                    writer.println(resolveExportHeader("cds", currentResult.getGeneId() + "_cds"));
                    writer.println(cds.replaceAll("\\s", ""));
                }
                
                // Export protein
                String protein = sequences.get("protein");
                if (protein != null && !protein.isEmpty()) {
                    writer.println(resolveExportHeader("protein", currentResult.getGeneId() + "_protein"));
                    writer.println(protein.replaceAll("\\s", ""));
                }
            }
        }

        private String resolveExportHeader(String sequenceKey, String fallbackHeader) {
            String header = currentResult.getSequenceHeader(sequenceKey);
            if (header == null || header.trim().isEmpty()) {
                return ">" + fallbackHeader;
            }
            header = header.trim();
            return header.startsWith(">") ? header : ">" + header;
        }
        
        public void clearData() {
            transcriptArea.setText("");
            cdsArea.setText("");
            proteinArea.setText("");
            statsLabel.setText("No sequence data loaded");
            exportButton.setEnabled(false);
            runBlastButton.setEnabled(false);
            currentResult = null;
        }
        
        public void exportData(File exportDir, String prefix) throws Exception {
            if (currentResult != null && currentResult.hasSequenceData()) {
                File outputFile = new File(exportDir, prefix + "_sequences.fasta");
                exportSequencesToFile(outputFile);
            }
        }

        private class BlastSequenceSelection {
            private final BlastConfig.SequenceType sequenceType;
            private final String fastaSequence;

            private BlastSequenceSelection(BlastConfig.SequenceType sequenceType, String fastaSequence) {
                this.sequenceType = sequenceType;
                this.fastaSequence = fastaSequence;
            }
        }
    }
    
    /**
     * Annotation information display panel
     */
    private static class AnnotationInfoPanel extends JPanel {
        
        private JTabbedPane annotationTabs;
        private JTable otherTable;
        private JTable goTable;
        private JTable keggTable;
        private JTable allTable;
        private DefaultTableModel otherTableModel;
        private DefaultTableModel goTableModel;
        private DefaultTableModel keggTableModel;
        private DefaultTableModel allTableModel;
        private JLabel statsLabel;
        private JButton exportButton;
        private GeneSearchResult currentResult;
        
        public AnnotationInfoPanel() {
            initializeComponents();
            setupLayout();
            setupEventHandlers();
        }
        
        private void initializeComponents() {
            annotationTabs = new JTabbedPane();
            
            // Create table models
            String[] columnNames = {"Type", "ID", "Term", "Description"};
            otherTableModel = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            goTableModel = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            keggTableModel = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            allTableModel = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            
            // Create tables
            otherTable = new JTable(otherTableModel);
            goTable = new JTable(goTableModel);
            keggTable = new JTable(keggTableModel);
            allTable = new JTable(allTableModel);
            
            // Configure tables
            configureTable(otherTable);
            configureTable(goTable);
            configureTable(keggTable);
            configureTable(allTable);
            
            // Add to tabs - OTHER ANNOTATIONS FIRST as requested
            annotationTabs.addTab("Other Annotations", new JScrollPane(otherTable));
            annotationTabs.addTab("GO Annotations", new JScrollPane(goTable));
            annotationTabs.addTab("KEGG Pathways", new JScrollPane(keggTable));
            annotationTabs.addTab("All Annotations", new JScrollPane(allTable));
            
            // Stats and controls
            statsLabel = new JLabel("No annotation data loaded");
            exportButton = new JButton("Export Annotations");
            exportButton.setEnabled(false);
        }
        
        private void configureTable(JTable table) {
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            table.getTableHeader().setReorderingAllowed(false);
            
            // Set column widths
            if (table.getColumnModel().getColumnCount() >= 4) {
                table.getColumnModel().getColumn(0).setPreferredWidth(80);  // Type
                table.getColumnModel().getColumn(1).setPreferredWidth(120); // ID
                table.getColumnModel().getColumn(2).setPreferredWidth(150); // Term
                table.getColumnModel().getColumn(3).setPreferredWidth(300); // Description
            }
        }
        
        private void setupLayout() {
            setLayout(new BorderLayout());
            
            add(annotationTabs, BorderLayout.CENTER);
            
            // Bottom panel for stats and controls
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.setBorder(BorderFactory.createEtchedBorder());
            bottomPanel.add(statsLabel, BorderLayout.WEST);
            bottomPanel.add(exportButton, BorderLayout.EAST);
            
            add(bottomPanel, BorderLayout.SOUTH);
        }
        
        private void setupEventHandlers() {
            exportButton.addActionListener(e -> exportAnnotations());
        }
        
        public void loadGeneData(GeneSearchResult result) {
            this.currentResult = result;
            
            if (result == null || !result.hasAnnotationData()) {
                clearData();
                statsLabel.setText("No annotation data available for gene: " + 
                    (result != null ? result.getGeneId() : "unknown"));
                return;
            }
            
            List<GeneAnnotationData.GeneAnnotation> annotations = result.getAnnotations();
            
            // Clear existing data
            otherTableModel.setRowCount(0);
            goTableModel.setRowCount(0);
            keggTableModel.setRowCount(0);
            allTableModel.setRowCount(0);
            
            // Separate annotations by type
            int goCount = 0, keggCount = 0, otherCount = 0;
            
            for (GeneAnnotationData.GeneAnnotation annotation : annotations) {
                String[] rowData = {
                    annotation.getType().getShortName(),
                    annotation.getAnnotationId() != null ? annotation.getAnnotationId() : "",
                    annotation.getTerm() != null ? annotation.getTerm() : "",
                    annotation.getDescription() != null ? annotation.getDescription() : ""
                };
                
                // Add to all table
                allTableModel.addRow(rowData);
                
                // Add to specific type tables
                if (annotation.getType() == GeneAnnotationData.AnnotationType.GO) {
                    goTableModel.addRow(rowData);
                    goCount++;
                } else if (annotation.getType() == GeneAnnotationData.AnnotationType.KEGG) {
                    keggTableModel.addRow(rowData);
                    keggCount++;
                } else {
                    // All other annotation types (Custom, InterPro, Pfam, etc.)
                    otherTableModel.addRow(rowData);
                    otherCount++;
                }
            }
            
            // Update stats
            String stats = String.format("Annotations loaded - Total: %d, Other: %d, GO: %d, KEGG: %d",
                annotations.size(), otherCount, goCount, keggCount);
            statsLabel.setText(stats);
            
            exportButton.setEnabled(true);
        }

        public void showLoadingState(String geneId) {
            clearData();
            statsLabel.setText("Loading annotations for gene: " + geneId + "...");
        }
        
        private void exportAnnotations() {
            if (currentResult == null) return;
            
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Annotations");
            fileChooser.setSelectedFile(new File(currentResult.getGeneId() + "_annotations.tsv"));
            
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    File outputFile = fileChooser.getSelectedFile();
                    exportAnnotationsToFile(outputFile);
                    JOptionPane.showMessageDialog(this, 
                        "Annotations exported successfully to:\n" + outputFile.getAbsolutePath(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this,
                        "Failed to export annotations:\n" + e.getMessage(),
                        "Export Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        private void exportAnnotationsToFile(File outputFile) throws Exception {
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                // Write header
                writer.println("Gene_ID\tType\tAnnotation_ID\tTerm\tDescription");
                
                // Write annotations
                List<GeneAnnotationData.GeneAnnotation> annotations = currentResult.getAnnotations();
                for (GeneAnnotationData.GeneAnnotation annotation : annotations) {
                    writer.printf("%s\t%s\t%s\t%s\t%s\n",
                        currentResult.getGeneId(),
                        annotation.getType().getShortName(),
                        annotation.getAnnotationId() != null ? annotation.getAnnotationId() : "",
                        annotation.getTerm() != null ? annotation.getTerm() : "",
                        annotation.getDescription() != null ? annotation.getDescription() : "");
                }
            }
        }
        
        public void clearData() {
            otherTableModel.setRowCount(0);
            goTableModel.setRowCount(0);
            keggTableModel.setRowCount(0);
            allTableModel.setRowCount(0);
            statsLabel.setText("No annotation data loaded");
            exportButton.setEnabled(false);
            currentResult = null;
        }
        
        public void exportData(File exportDir, String prefix) throws Exception {
            if (currentResult != null && currentResult.hasAnnotationData()) {
                File outputFile = new File(exportDir, prefix + "_annotations.tsv");
                exportAnnotationsToFile(outputFile);
            }
        }
    }
    
    /**
     * Expression visualization panel
     */
    private static class ExpressionVisualizationPanel extends JPanel {
        
        private JTabbedPane experimentTabs;
        private JLabel statsLabel;
        private JButton exportButton;
        private JButton heatmapButton;
        private GeneSearchResult currentResult;
        private Map<String, ExpressionExperimentPanel> experimentPanels;
        
        public ExpressionVisualizationPanel() {
            this.experimentPanels = new HashMap<>();
            initializeComponents();
            setupLayout();
            setupEventHandlers();
        }
        
        private void initializeComponents() {
            // Main tabbed pane for experiments
            experimentTabs = new JTabbedPane();
            
            // Stats and controls
            statsLabel = new JLabel("No expression data loaded");
            exportButton = new JButton("Export All Expression Data");
            exportButton.setEnabled(false);
            heatmapButton = new JButton("Generate Multi-Experiment Heatmap");
            heatmapButton.setEnabled(false);
        }
        
        private void setupLayout() {
            setLayout(new BorderLayout());
            
            add(experimentTabs, BorderLayout.CENTER);
            
            // Bottom panel for stats and controls
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.setBorder(BorderFactory.createEtchedBorder());
            bottomPanel.add(statsLabel, BorderLayout.WEST);
            
            JPanel buttonPanel = new JPanel(new FlowLayout());
            buttonPanel.add(heatmapButton);
            buttonPanel.add(exportButton);
            bottomPanel.add(buttonPanel, BorderLayout.EAST);
            
            add(bottomPanel, BorderLayout.SOUTH);
        }
        
        private void setupEventHandlers() {
            exportButton.addActionListener(e -> exportAllExpressionData());
            heatmapButton.addActionListener(e -> generateMultiExperimentHeatmap());
        }
        
        public void loadGeneData(GeneSearchResult result) {
            this.currentResult = result;
            
            if (result == null || !result.hasExpressionData()) {
                clearData();
                statsLabel.setText("No expression data available for gene: " + 
                    (result != null ? result.getGeneId() : "unknown"));
                return;
            }
            
            // Clear existing tabs and panels
            clearData();
            
            Map<String, ExpressionData> expressionData = result.getExpressionData();
            int totalSamples = 0;
            
            // Create a panel for each experiment
            for (Map.Entry<String, ExpressionData> entry : expressionData.entrySet()) {
                String experimentId = entry.getKey();
                ExpressionData data = entry.getValue();
                
                // Create experiment panel with table and chart
                ExpressionExperimentPanel experimentPanel = new ExpressionExperimentPanel(experimentId, data, result.getGeneId());
                experimentPanels.put(experimentId, experimentPanel);
                
                // Add tab with experiment name
                String tabTitle = getExperimentDisplayName(experimentId);
                experimentTabs.addTab(tabTitle, experimentPanel);
                
                totalSamples += data.getExpressionValues().size();
            }
            
            // Update stats
            String stats = String.format("Expression data loaded - %d experiments, %d samples total",
                expressionData.size(), totalSamples);
            statsLabel.setText(stats);
            
            exportButton.setEnabled(true);
            heatmapButton.setEnabled(totalSamples > 0);
        }
        
        private String getExperimentDisplayName(String experimentId) {
            // Try to get a more user-friendly name based on experiment ID
            if (experimentId.contains("1758030625546")) {
                return "Pineapple Peel Color (" + experimentId.substring(experimentId.length() - 6) + ")";
            } else if (experimentId.contains("1758032734365")) {
                return "Ethylene Flowering (" + experimentId.substring(experimentId.length() - 6) + ")";
            } else if (experimentId.contains("1758079627381")) {
                return "Fruit Development (" + experimentId.substring(experimentId.length() - 6) + ")";
            } else {
                return experimentId.length() > 12 ? experimentId.substring(0, 12) + "..." : experimentId;
            }
        }
        
        private void exportAllExpressionData() {
            if (currentResult == null) return;
            
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export All Expression Data");
            fileChooser.setSelectedFile(new File(currentResult.getGeneId() + "_all_expression.tsv"));
            
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    File outputFile = fileChooser.getSelectedFile();
                    exportExpressionToFile(outputFile);
                    JOptionPane.showMessageDialog(this, 
                        "Expression data exported successfully to:\n" + outputFile.getAbsolutePath(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this,
                        "Failed to export expression data:\n" + e.getMessage(),
                        "Export Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        private void exportExpressionToFile(File outputFile) throws Exception {
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                // Write header
                writer.println("Gene_ID\tExperiment\tSample\tExpression_Value");
                
                // Write expression data
                Map<String, ExpressionData> expressionData = currentResult.getExpressionData();
                for (Map.Entry<String, ExpressionData> entry : expressionData.entrySet()) {
                    String experimentId = entry.getKey();
                    ExpressionData data = entry.getValue();
                    
                    for (Map.Entry<String, Double> sampleEntry : data.getExpressionValues().entrySet()) {
                        writer.printf("%s\t%s\t%s\t%.6f\n",
                            currentResult.getGeneId(),
                            experimentId,
                            sampleEntry.getKey(),
                            sampleEntry.getValue());
                    }
                }
            }
        }
        
        private void generateMultiExperimentHeatmap() {
            JOptionPane.showMessageDialog(this,
                "Multi-experiment heatmap generation will be available in future versions.\n" +
                "For now, you can use individual experiment charts in each tab.",
                "Feature Coming Soon", JOptionPane.INFORMATION_MESSAGE);
        }
        
        public void clearData() {
            experimentTabs.removeAll();
            experimentPanels.clear();
            statsLabel.setText("No expression data loaded");
            exportButton.setEnabled(false);
            heatmapButton.setEnabled(false);
            currentResult = null;
        }
        
        public void exportData(File exportDir, String prefix) throws Exception {
            if (currentResult != null && currentResult.hasExpressionData()) {
                File outputFile = new File(exportDir, prefix + "_expression.tsv");
                exportExpressionToFile(outputFile);
            }
        }
    }
    
    /**
     * Individual experiment panel with table and chart visualization
     */
    private static class ExpressionExperimentPanel extends JPanel {
        
        private String experimentId;
        private ExpressionData expressionData;
        private String geneId;
        
        private JTable dataTable;
        private DefaultTableModel tableModel;
        private JPanel chartPanel;
        private JLabel statsLabel;
        private JButton exportButton;
        private JButton chartButton;
        
        public ExpressionExperimentPanel(String experimentId, ExpressionData expressionData, String geneId) {
            this.experimentId = experimentId;
            this.expressionData = expressionData;
            this.geneId = geneId;
            
            initializeComponents();
            setupLayout();
            setupEventHandlers();
            loadData();
        }
        
        private void initializeComponents() {
            // Create table for detailed data
            String[] columnNames = {"Sample ID", "Expression Value", "Rank"};
            tableModel = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
                
                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    switch (columnIndex) {
                        case 0: return String.class;     // Sample ID
                        case 1: return Double.class;     // Expression Value (for proper numeric sorting)
                        case 2: return Integer.class;    // Rank
                        default: return String.class;
                    }
                }
            };
            
            dataTable = new JTable(tableModel);
            dataTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            dataTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            dataTable.getTableHeader().setReorderingAllowed(false);
            
            // Enable sorting with TableRowSorter (like BLAST Panel)
            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
            dataTable.setRowSorter(sorter);
            
            // Set column widths
            if (dataTable.getColumnModel().getColumnCount() >= 3) {
                dataTable.getColumnModel().getColumn(0).setPreferredWidth(200); // Sample ID
                dataTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Expression Value
                dataTable.getColumnModel().getColumn(2).setPreferredWidth(80);  // Rank
            }
            
            // Chart panel - placeholder for now, will add JIGplot integration
            chartPanel = new JPanel();
            chartPanel.setPreferredSize(new Dimension(600, 300));
            chartPanel.setBorder(BorderFactory.createTitledBorder("Expression Bar Chart"));
            chartPanel.setBackground(Color.WHITE);
            
            // Create a simple bar chart using Java2D for now
            createSimpleBarChart();
            
            // Controls
            statsLabel = new JLabel("Loading expression data...");
            exportButton = new JButton("Export This Experiment");
            chartButton = new JButton("Refresh Chart");
        }
        
        private void setupLayout() {
            setLayout(new BorderLayout());
            
            // Split pane: table on left, chart on right
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
            SimpleGenomeHubUi.styleSplitPane(splitPane);
            splitPane.setLeftComponent(new JScrollPane(dataTable));
            splitPane.setRightComponent(chartPanel);
            splitPane.setDividerLocation(350);
            splitPane.setResizeWeight(0.4);
            
            add(splitPane, BorderLayout.CENTER);
            
            // Bottom panel for stats and controls
            JPanel bottomPanel = new JPanel(new BorderLayout());
            bottomPanel.setBorder(BorderFactory.createEtchedBorder());
            bottomPanel.add(statsLabel, BorderLayout.WEST);
            
            JPanel buttonPanel = new JPanel(new FlowLayout());
            buttonPanel.add(chartButton);
            buttonPanel.add(exportButton);
            bottomPanel.add(buttonPanel, BorderLayout.EAST);
            
            add(bottomPanel, BorderLayout.SOUTH);
        }
        
        private void setupEventHandlers() {
            exportButton.addActionListener(e -> exportExperimentData());
            chartButton.addActionListener(e -> refreshChart());
            
            // Add table selection listener to sync chart with table order (like BLAST Panel)
            dataTable.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    refreshChart(); // Refresh chart when selection changes
                }
            });
            
            // Add table model listener to sync chart when sorting changes
            dataTable.getRowSorter().addRowSorterListener(e -> {
                if (e.getType() == javax.swing.event.RowSorterEvent.Type.SORTED) {
                    refreshChart(); // Refresh chart when table is sorted
                }
            });
        }
        
        private void loadData() {
            // Clear existing data
            tableModel.setRowCount(0);
            
            // Sort expression values for initial ranking (users can re-sort via table headers)
            List<Map.Entry<String, Double>> sortedEntries = new ArrayList<>(expressionData.getExpressionValues().entrySet());
            sortedEntries.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // Descending order
            
            // Load data into table with proper data types for sorting
            for (int i = 0; i < sortedEntries.size(); i++) {
                Map.Entry<String, Double> entry = sortedEntries.get(i);
                Object[] rowData = {
                    entry.getKey(),           // String: Sample ID
                    entry.getValue(),         // Double: Expression Value (for proper numeric sorting)
                    i + 1                     // Integer: Rank
                };
                tableModel.addRow(rowData);
            }
            
            // Update stats
            double maxValue = sortedEntries.isEmpty() ? 0.0 : sortedEntries.get(0).getValue();
            double minValue = sortedEntries.isEmpty() ? 0.0 : sortedEntries.get(sortedEntries.size() - 1).getValue();
            double avgValue = sortedEntries.stream().mapToDouble(Map.Entry::getValue).average().orElse(0.0);
            
            String stats = String.format("Experiment: %s | Samples: %d | Max: %.3f | Min: %.3f | Avg: %.3f",
                getShortExperimentName(), sortedEntries.size(), maxValue, minValue, avgValue);
            statsLabel.setText(stats);
            
            // Refresh the chart
            refreshChart();
        }
        
        private String getShortExperimentName() {
            if (experimentId.length() > 15) {
                return experimentId.substring(0, 15) + "...";
            }
            return experimentId;
        }
        
        private void createSimpleBarChart() {
            chartPanel.removeAll();
            
            // Create a custom panel for drawing the bar chart
            JPanel barChartPanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    drawBarChart(g);
                }
            };
            
            barChartPanel.setBackground(Color.WHITE);
            chartPanel.setLayout(new BorderLayout());
            chartPanel.add(barChartPanel, BorderLayout.CENTER);
        }
        
        private void drawBarChart(Graphics g) {
            if (expressionData == null || expressionData.getExpressionValues().isEmpty()) {
                g.setColor(Color.GRAY);
                g.drawString("No expression data to display", 50, 150);
                return;
            }
            
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Get data in the same order as the current table sorting (like BLAST Panel)
            List<Map.Entry<String, Double>> chartData = new ArrayList<>();
            
            // Extract data from table in its current sorted order
            int rowCount = Math.min(20, dataTable.getRowCount()); // Show top 20 for visibility
            for (int i = 0; i < rowCount; i++) {
                String sampleId = (String) dataTable.getValueAt(i, 0);
                Double expressionValue = (Double) dataTable.getValueAt(i, 1);
                chartData.add(new AbstractMap.SimpleEntry<>(sampleId, expressionValue));
            }
            
            if (chartData.isEmpty()) return;
            
            // Chart dimensions - leave more space for sample labels
            int width = chartPanel.getWidth() - 40;
            int height = chartPanel.getHeight() - 120; // More space for labels
            int x = 20;
            int y = 40;
            
            // Calculate bar dimensions
            int barWidth = Math.max(15, (width / chartData.size()) - 2);
            double maxValue = chartData.stream().mapToDouble(Map.Entry::getValue).max().orElse(1.0);
            
            // Draw bars with sample name labels
            for (int i = 0; i < chartData.size(); i++) {
                Map.Entry<String, Double> entry = chartData.get(i);
                double value = entry.getValue();
                String sampleName = entry.getKey();
                int barHeight = (int) ((value / maxValue) * height);
                
                int barX = x + i * (barWidth + 2);
                int barY = y + height - barHeight;
                
                // Color gradient based on expression value (not position)
                float valueRatio = (float) (value / maxValue);
                Color barColor = new Color(
                    (int) (70 + valueRatio * 100),   // Red component
                    (int) (130 + valueRatio * 70),   // Green component  
                    (int) (180 + valueRatio * 75)    // Blue component
                );
                
                g2d.setColor(barColor);
                g2d.fillRect(barX, barY, barWidth, barHeight);
                
                // Draw value on top of bar
                g2d.setColor(Color.BLACK);
                g2d.setFont(SimpleGenomeHubStyle.FONT_ARIAL_PLAIN_9);
                String valueStr = String.format("%.1f", value);
                int valueWidth = g2d.getFontMetrics().stringWidth(valueStr);
                g2d.drawString(valueStr, barX + (barWidth - valueWidth) / 2, barY - 2);
                
                // Draw sample name at bottom (rotated for better fit)
                g2d.setFont(SimpleGenomeHubStyle.FONT_ARIAL_PLAIN_8);
                AffineTransform originalTransform = g2d.getTransform();
                g2d.translate(barX + barWidth / 2, y + height + 15);
                g2d.rotate(-Math.PI / 4); // 45-degree rotation
                
                // Truncate long sample names
                String displayName = sampleName.length() > 12 ? sampleName.substring(0, 12) + "..." : sampleName;
                g2d.drawString(displayName, 0, 0);
                
                g2d.setTransform(originalTransform);
            }
            
            // Draw title
            g2d.setColor(Color.BLACK);
            g2d.setFont(SimpleGenomeHubStyle.FONT_ARIAL_BOLD_12);
            String title = "Expression for " + geneId + " (Showing " + chartData.size() + " samples - Table Order)";
            int titleWidth = g2d.getFontMetrics().stringWidth(title);
            g2d.drawString(title, (chartPanel.getWidth() - titleWidth) / 2, 20);
            
            // Draw sort indicator
            g2d.setFont(SimpleGenomeHubStyle.FONT_ARIAL_ITALIC_10);
            String sortInfo = "Tip: Click column headers to sort table and chart";
            g2d.setColor(Color.BLUE);
            g2d.drawString(sortInfo, 10, chartPanel.getHeight() - 5);
        }
        
        private void refreshChart() {
            createSimpleBarChart();
            chartPanel.repaint();
        }
        
        private void exportExperimentData() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export Experiment Data");
            fileChooser.setSelectedFile(new File(geneId + "_" + experimentId + "_expression.tsv"));
            
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    File outputFile = fileChooser.getSelectedFile();
                    exportToFile(outputFile);
                    JOptionPane.showMessageDialog(this, 
                        "Experiment data exported successfully to:\n" + outputFile.getAbsolutePath(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this,
                        "Failed to export experiment data:\n" + e.getMessage(),
                        "Export Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
        
        private void exportToFile(File outputFile) throws Exception {
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                // Write header
                writer.println("Gene_ID\tExperiment\tSample_ID\tExpression_Value");
                
                // Write expression data
                for (Map.Entry<String, Double> entry : expressionData.getExpressionValues().entrySet()) {
                    writer.printf("%s\t%s\t%s\t%.6f\n",
                        geneId,
                        experimentId,
                        entry.getKey(),
                        entry.getValue());
                }
            }
        }
    }
}
