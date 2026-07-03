/*
 * Annotation Data Management Panel
 * Displays and manages imported functional annotations
 */
package simplegenomehub.gui;

import simplegenomehub.model.*;
import simplegenomehub.model.GeneAnnotationData.AnnotationType;
import simplegenomehub.model.GeneAnnotationData.GeneAnnotation;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.table.TableModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;

/**
 * Panel for managing functional annotation data
 * 
 * @author SimpleGenomeHub
 */
public class AnnotationDataPanel extends JDialog {
    
    private static final Logger logger = Logger.getLogger(AnnotationDataPanel.class.getName());
    
    private SpeciesInfo targetSpecies;
    private GeneAnnotationData annotationData;
    
    // UI Components
    private JTable annotationTable;
    private MetadataDrivenTableModel tableModel;
    private MixedAnnotationTableModel mixedTableModel;
    private JComboBox<AnnotationType> typeFilterCombo;
    private JTextField geneFilterField;
    private JTextArea batchGeneIdArea;
    private JButton batchFilterButton;
    private JCheckBox batchPartialMatchCheckbox;
    private JButton clearFiltersButton;
    private boolean isTypeSpecificView = false;
    private AnnotationType specificType = null;
    private JTextArea detailsArea;
    private JButton deleteButton;
    private JButton deleteAllButton;
    private JButton editButton;
    private JButton exportButton;
    private JButton statisticsButton;
    private JButton refreshButton;
    private JButton importMoreButton;
    private JLabel statusLabel;
    
    private static final String[] MIXED_VIEW_COLUMNS = {
        "Gene ID", "Type", "Annotation ID", "Term", "Description"
    };
    
    /**
     * Constructor
     */
    public AnnotationDataPanel(Window parent, SpeciesInfo species) {
        super(parent, "Annotation Data Management - " + species.getSpeciesName(), ModalityType.MODELESS);
        this.targetSpecies = species;
        this.annotationData = species.getFunctionalAnnotations();
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        refreshData();
        
        setSize(900, 600);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }
    
    /**
     * Set initial type filter (called from AnnotationTypeSelectionDialog)
     */
    public void setInitialTypeFilter(AnnotationType type) {
        if (type != null) {
            this.isTypeSpecificView = true;
            this.specificType = type;
            
            typeFilterCombo.setSelectedItem(type);
            
            // Refresh data immediately with the correct type context
            refreshData();
            
            // Update window title to reflect the filter
            setTitle("Annotation Data Management - " + targetSpecies.getSpeciesName() + 
                    " [" + type.getShortName() + " Only]");
            
            // Simplify interface for single type view
            simplifyInterfaceForSingleType();
        }
    }
    
    /**
     * Simplify interface when viewing only one annotation type
     */
    private void simplifyInterfaceForSingleType() {
        if (isTypeSpecificView && specificType != null) {
            // Hide type filter since we're viewing only one type
            typeFilterCombo.setVisible(false);
            Component typeLabel = findComponentWithText("Annotation Type:");
            if (typeLabel != null) {
                typeLabel.setVisible(false);
            }
            
            // Hide "Show All Types" button since user came from type selection
            Component showAllButton = findComponentWithText("Show All Types");
            if (showAllButton != null) {
                showAllButton.setVisible(false);
            }
            
            // Update instruction text
            updateInstructionForSingleType();
            
            // Table model already initialized for single type view
            
            // Update Clear All Filters button for single-type view
            updateClearFiltersButton();
            
            // Force revalidate to ensure UI updates are visible
            SwingUtilities.invokeLater(() -> {
                revalidate();
                repaint();
            });
        }
    }
    
    /**
     * Find component by text (helper method)
     */
    private Component findComponentWithText(String text) {
        return findComponentWithTextRecursive(this, text);
    }
    
    private Component findComponentWithTextRecursive(Container container, String text) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JLabel && ((JLabel) comp).getText().equals(text)) {
                return comp;
            } else if (comp instanceof JButton && ((JButton) comp).getText().equals(text)) {
                return comp;
            } else if (comp instanceof Container) {
                Component found = findComponentWithTextRecursive((Container) comp, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    
    /**
     * Update instruction text for single type view
     */
    private void updateInstructionForSingleType() {
        if (specificType != null) {
            // Find and update the instruction label
            Container topPanel = (Container) ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.NORTH);
            if (topPanel != null) {
                Component[] components = topPanel.getComponents();
                for (Component comp : components) {
                    if (comp instanceof JLabel) {
                        JLabel label = (JLabel) comp;
                        if (label.getText().contains("Functional Annotation Data Management")) {
                            label.setText(
                                "<html><b>" + specificType.getDescription() + " Annotation Management</b><br>" + 
                                "Viewing and managing " + specificType.getShortName() + " annotations for " + targetSpecies.getSpeciesName() + "<br>" +
                                "<i>Tip: Use gene ID filters and batch operations for focused analysis</i></html>");
                            break;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Update Clear All Filters button for single type view
     */
    private void updateClearFiltersButton() {
        if (isTypeSpecificView && specificType != null) {
            clearFiltersButton.setText("Clear Gene Filters");
            clearFiltersButton.setToolTipText("Clear gene ID and batch filters (keep viewing " + 
                                            specificType.getShortName() + " annotations only)");
        }
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Table for annotations
        // Table will be initialized when data is loaded
        annotationTable = new JTable();
        annotationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        annotationTable.setAutoCreateRowSorter(true);
        
        // Enhanced table selection and interaction similar to BlastResultsPanel
        annotationTable.setRowSelectionAllowed(true);
        annotationTable.setColumnSelectionAllowed(true);
        annotationTable.setCellSelectionEnabled(true);
        annotationTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        annotationTable.getColumnModel().setSelectionModel(new DefaultListSelectionModel());
        annotationTable.getColumnModel().getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        annotationTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        
        // Set light blue selection colors for better visibility
        annotationTable.setSelectionBackground(new Color(230, 240, 255)); // Light blue
        annotationTable.setSelectionForeground(Color.BLACK);
        
        // Enable focus and keyboard navigation
        annotationTable.setFocusable(true);
        annotationTable.setRequestFocusEnabled(true);
        
        // Add context menu for right-click operations
        setupTableContextMenu();
        
        // Column widths will be set dynamically based on actual data structure
        
        // Filter components
        typeFilterCombo = new JComboBox<>();
        typeFilterCombo.addItem(null); // "All types" option
        for (AnnotationType type : AnnotationType.values()) {
            typeFilterCombo.addItem(type);
        }
        SimpleGenomeHubUi.setComboBoxMinimumWidth(typeFilterCombo, 220);
        
        geneFilterField = new JTextField(20);
        geneFilterField.setToolTipText("Filter by gene ID (partial match)");
        
        // Batch gene ID input area
        batchGeneIdArea = new JTextArea(3, 30);
        batchGeneIdArea.setToolTipText("Enter multiple gene IDs (one per line or comma-separated) for batch filtering");
        batchGeneIdArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        batchGeneIdArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // Batch filter controls
        batchFilterButton = new JButton("Filter by List");
        batchFilterButton.setToolTipText("Apply filter using the gene ID list");
        batchPartialMatchCheckbox = new JCheckBox("Partial Match");
        batchPartialMatchCheckbox.setToolTipText("Enable partial matching for batch gene ID filtering (e.g., 'AT1G' matches 'AT1G01010')");
        batchPartialMatchCheckbox.setSelected(false); // Default to exact match
        clearFiltersButton = new JButton("Clear All Filters");
        clearFiltersButton.setToolTipText("Clear all applied filters");
        
        // Details area
        detailsArea = new JTextArea(6, 40);
        detailsArea.setEditable(false);
        detailsArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        detailsArea.setBackground(new Color(248, 248, 248));
        
        // Action buttons
        deleteButton = new JButton("Delete Selected");
        deleteButton.setToolTipText("Delete the selected annotation (select a row first)");
        deleteAllButton = new JButton("Delete All Type");
        deleteAllButton.setToolTipText("Delete all annotations of the selected type");
        exportButton = new JButton("Export Annotations");
        statisticsButton = new JButton("Show Statistics");
        refreshButton = new JButton("Refresh");
        importMoreButton = new JButton("Import More");
        
        // Add edit button
        editButton = new JButton("Edit Selected");
        editButton.setEnabled(false);
        editButton.setToolTipText("Edit the selected annotation (select a row first)");
        
        deleteButton.setEnabled(false);
        
        // Status label
        statusLabel = new JLabel("Ready");
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // Top: Instructions and filters
        JPanel topPanel = new JPanel(new BorderLayout());
        
        JLabel instructionLabel = new JLabel(
            "<html><b>Functional Annotation Data Management</b><br>" + 
            "View, filter, and manage imported functional annotations for " + targetSpecies.getSpeciesName() + "<br>" +
            "<i>Tip: Use filters to focus on specific annotation types or gene sets</i></html>");
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        topPanel.add(instructionLabel, BorderLayout.NORTH);
        
        // Enhanced filter panel with batch operations
        JPanel filterPanel = new JPanel(new BorderLayout());
        filterPanel.setBorder(new TitledBorder("Filters"));
        
        // Top row: Type and single gene ID filters
        JPanel simpleFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        simpleFilterPanel.add(new JLabel("Annotation Type:"));
        simpleFilterPanel.add(typeFilterCombo);
        simpleFilterPanel.add(Box.createHorizontalStrut(20));
        simpleFilterPanel.add(new JLabel("Gene ID:"));
        simpleFilterPanel.add(geneFilterField);
        
        // Add quick filter buttons
        JButton showAllTypesButton = new JButton("Show All Types");
        showAllTypesButton.setToolTipText("Clear type filter to show all annotation types");
        showAllTypesButton.addActionListener(e -> {
            typeFilterCombo.setSelectedIndex(0);
            setTitle("Annotation Data Management - " + targetSpecies.getSpeciesName());
        });
        simpleFilterPanel.add(Box.createHorizontalStrut(20));
        simpleFilterPanel.add(showAllTypesButton);
        
        // Bottom section: Batch gene ID filtering
        JPanel batchFilterPanel = new JPanel(new BorderLayout());
        batchFilterPanel.setBorder(BorderFactory.createTitledBorder("Batch Gene ID Filter"));
        
        JScrollPane batchScrollPane = new JScrollPane(batchGeneIdArea);
        batchScrollPane.setPreferredSize(new Dimension(300, 80));
        batchFilterPanel.add(batchScrollPane, BorderLayout.CENTER);
        
        JPanel batchButtonPanel = new JPanel(new BorderLayout());
        
        // Top: Partial match option
        JPanel optionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPanel.add(batchPartialMatchCheckbox);
        batchButtonPanel.add(optionPanel, BorderLayout.NORTH);
        
        // Bottom: Action buttons
        JPanel batchActionPanel = new JPanel(new FlowLayout());
        batchActionPanel.add(batchFilterButton);
        batchActionPanel.add(clearFiltersButton);
        batchButtonPanel.add(batchActionPanel, BorderLayout.SOUTH);
        
        batchFilterPanel.add(batchButtonPanel, BorderLayout.EAST);
        
        filterPanel.add(simpleFilterPanel, BorderLayout.NORTH);
        filterPanel.add(batchFilterPanel, BorderLayout.CENTER);
        
        topPanel.add(filterPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);
        
        // Center: Split pane with table and details
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        SimpleGenomeHubUi.styleSplitPane(splitPane);
        
        // Top part: annotation table
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(new TitledBorder("Annotations"));
        
        JScrollPane tableScrollPane = new JScrollPane(annotationTable);
        tableScrollPane.setPreferredSize(new Dimension(850, 300));
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        
        splitPane.setTopComponent(tablePanel);
        
        // Bottom part: details
        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.setBorder(new TitledBorder("Annotation Details"));
        
        JScrollPane detailsScrollPane = new JScrollPane(detailsArea);
        detailsPanel.add(detailsScrollPane, BorderLayout.CENTER);
        
        splitPane.setBottomComponent(detailsPanel);
        splitPane.setDividerLocation(350);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Bottom: Controls and status
        JPanel bottomPanel = new JPanel(new BorderLayout());
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(importMoreButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(deleteAllButton);
        buttonPanel.add(exportButton);
        buttonPanel.add(statisticsButton);
        buttonPanel.add(refreshButton);
        
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);
        
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        // Table selection listener
        annotationTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectionDetails();
                boolean hasSelection = annotationTable.getSelectedRow() != -1;
                deleteButton.setEnabled(hasSelection);
                editButton.setEnabled(hasSelection);
                
                if (hasSelection) {
                    statusLabel.setText("Row selected - Edit/Delete buttons are now available");
                } else {
                    statusLabel.setText("Select a table row to enable Edit/Delete operations");
                }
            }
        });
        
        // Enhanced filter listeners with batch support
        typeFilterCombo.addActionListener(e -> applyFilters());
        geneFilterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
        });
        batchGeneIdArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
        });
        batchPartialMatchCheckbox.addActionListener(e -> applyFilters());
        
        // Batch filter button listeners
        batchFilterButton.addActionListener(e -> applyBatchGeneFilter());
        clearFiltersButton.addActionListener(e -> clearAllFilters());
        
        // Add column selection listener for enhanced interaction
        annotationTable.getColumnModel().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectionDetails();
            }
        });
        
        // Button listeners
        editButton.addActionListener(e -> editSelectedAnnotation());
        deleteButton.addActionListener(e -> deleteSelectedAnnotation());
        deleteAllButton.addActionListener(e -> deleteAllAnnotationsOfType());
        exportButton.addActionListener(e -> exportAnnotations());
        statisticsButton.addActionListener(e -> showStatistics());
        refreshButton.addActionListener(e -> refreshData());
        importMoreButton.addActionListener(e -> importMoreAnnotations());
    }
    
    /**
     * Refresh annotation data
     */
    /**
     * Initialize table with metadata-driven model for specific type
     */
    private void initializeTableForType(AnnotationType type) {
        if (type == null || targetSpecies == null) {
            return;
        }
        
        // Get type-specific directory and files
        File typeDir = targetSpecies.getAnnotationTypeDirectory(type);
        if (typeDir == null || !typeDir.exists()) {
            logger.warning("No directory found for annotation type: " + type.getShortName());
            clearTable();
            return;
        }
        
        File dataFile = new File(typeDir, type.getShortName() + ".tsv");
        if (!dataFile.exists()) {
            logger.warning("No data file found for annotation type: " + type.getShortName());
            clearTable();
            return;
        }
        
        // Load or create metadata
        AnnotationMetadata metadata = targetSpecies.getAnnotationMetadata(type);
        if (metadata == null) {
            // Try to load metadata from file
            if (!targetSpecies.loadAnnotationMetadata(type)) {
                // Generate metadata on-the-fly
                logger.info("Generating metadata for " + type.getShortName());
                metadata = AnnotationMetadata.analyzeFile(dataFile, type);
                targetSpecies.setAnnotationMetadata(type, metadata);
            } else {
                metadata = targetSpecies.getAnnotationMetadata(type);
            }
        }
        
        // Create new table model
        tableModel = new MetadataDrivenTableModel(metadata, type, dataFile);
        annotationTable.setModel(tableModel);
        
        // Set up table appearance
        setupTableForMetadataModel();
        
        // Load data
        tableModel.loadData();
        
        logger.info("Initialized table for " + type.getShortName() + " with " + 
                   tableModel.getColumnCount() + " columns, " + 
                   tableModel.getRowCount() + " rows");
    }
    
    /**
     * Setup table appearance for metadata-driven model
     */
    private void setupTableForMetadataModel() {
        // Set up auto-resize and column widths
        annotationTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        
        // Adjust column widths based on column count and content
        SwingUtilities.invokeLater(() -> {
            adjustColumnWidthsForCurrentModel();
        });
        
        // Update row sorter
        annotationTable.setAutoCreateRowSorter(true);
        
        // Set selection colors
        annotationTable.setSelectionBackground(new Color(230, 240, 255));
        annotationTable.setSelectionForeground(Color.BLACK);
    }
    
    /**
     * Adjust column widths based on metadata and content
     */
    private void adjustColumnWidthsForMetadata() {
        adjustColumnWidthsForCurrentModel();
    }
    
    /**
     * Adjust column widths for the currently active table model
     */
    private void adjustColumnWidthsForCurrentModel() {
        if (annotationTable == null || annotationTable.getColumnCount() == 0) {
            return;
        }
        
        int columnCount = annotationTable.getColumnCount();
        
        // Get available width
        int availableWidth = annotationTable.getParent() != null ? 
            annotationTable.getParent().getWidth() - 50 : 800;
        
        if (columnCount <= 4) {
            // Standard layout for small number of columns
            if (columnCount >= 1) annotationTable.getColumnModel().getColumn(0).setPreferredWidth(120); // Gene ID
            if (columnCount >= 2) {
                int remainingWidth = availableWidth - 120;
                int columnWidth = remainingWidth / (columnCount - 1);
                for (int i = 1; i < columnCount; i++) {
                    annotationTable.getColumnModel().getColumn(i).setPreferredWidth(Math.max(100, columnWidth));
                }
            }
        } else {
            // Dynamic layout for many columns
            int baseWidth = Math.max(80, availableWidth / columnCount);
            
            for (int i = 0; i < columnCount; i++) {
                if (i == 0) {
                    // Gene ID column - fixed width
                    annotationTable.getColumnModel().getColumn(i).setPreferredWidth(120);
                } else {
                    // Other columns - distribute space
                    annotationTable.getColumnModel().getColumn(i).setPreferredWidth(baseWidth);
                }
            }
        }
        
        // Enable horizontal scrolling for many columns
        if (columnCount > 6) {
            annotationTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        }
    }
    
    /**
     * Clear table when no data is available
     */
    private void clearTable() {
        if (tableModel != null) {
            tableModel.clear();
        } else if (mixedTableModel != null) {
            mixedTableModel.setAnnotations(Collections.emptyList());
        } else {
            // Create empty model
            annotationTable.setModel(new javax.swing.table.DefaultTableModel());
        }
    }
    
    private void refreshData() {
        if (isTypeSpecificView && specificType != null) {
            // Refresh specific type view using metadata-driven approach
            initializeTableForType(specificType);
            applyFiltersToMetadataModel();
        } else {
            initializeMixedTable();
            applyMixedViewFilters();
        }
        updateStatusLabel();
    }
    
    /**
     * Initialize the real mixed annotation table that combines all available types
     */
    private void initializeMixedTable() {
        if (!ensureAnnotationDataLoaded()) {
            logger.info("No annotation data available for mixed view: " + targetSpecies.getSpeciesName());
            clearTable();
            return;
        }
        
        tableModel = null;
        if (mixedTableModel == null) {
            mixedTableModel = new MixedAnnotationTableModel();
        }
        
        annotationTable.setModel(mixedTableModel);
        annotationTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        annotationTable.setAutoCreateRowSorter(true);
        SwingUtilities.invokeLater(this::adjustColumnWidthsForCurrentModel);
    }
    
    /**
     * Ensure the full annotation dataset is loaded in memory for mixed view filtering
     */
    private boolean ensureAnnotationDataLoaded() {
        if (annotationData != null && annotationData.getAnnotatedGenes() > 0) {
            return true;
        }
        
        if (targetSpecies == null) {
            return false;
        }
        
        targetSpecies.loadFunctionalAnnotations();
        annotationData = targetSpecies.getFunctionalAnnotations();
        
        if (annotationData != null && annotationData.getAnnotatedGenes() > 0) {
            return true;
        }
        
        GeneAnnotationData loadedData = new GeneAnnotationData(
            targetSpecies.getSpeciesDirectoryName() + "_annotations",
            targetSpecies.getSpeciesName() + " Annotations");
        boolean loadedAny = false;
        
        for (AnnotationType type : targetSpecies.getAvailableAnnotationTypes()) {
            File dataFile = getAnnotationDataFile(type);
            if (dataFile == null || !dataFile.exists()) {
                continue;
            }
            
            boolean loaded = loadAnnotationsForType(loadedData, type, dataFile);
            loadedAny = loadedAny || loaded;
        }
        
        if (loadedAny) {
            annotationData = loadedData;
            targetSpecies.setFunctionalAnnotations(loadedData);
        }
        
        return loadedAny;
    }
    
    /**
     * Resolve the data file for a specific annotation type
     */
    private File getAnnotationDataFile(AnnotationType type) {
        File typeDir = targetSpecies.getAnnotationTypeDirectory(type);
        if (typeDir != null) {
            File typeFile = new File(typeDir, type.getShortName() + ".tsv");
            if (typeFile.exists()) {
                return typeFile;
            }
        }
        
        File functionalDir = targetSpecies.getFunctionalAnnotationDir();
        if (functionalDir != null) {
            File legacyFile = new File(functionalDir, type.getShortName() + ".tsv");
            if (legacyFile.exists()) {
                return legacyFile;
            }
        }
        
        return null;
    }
    
    /**
     * Load one annotation type into an in-memory GeneAnnotationData container
     */
    private boolean loadAnnotationsForType(GeneAnnotationData targetData, AnnotationType type, File dataFile) {
        switch (type) {
            case GO:
                return targetData.loadGOAnnotations(dataFile);
            case KEGG:
                return targetData.loadKEGGAnnotations(dataFile);
            case INTERPRO:
            case PFAM:
            case CUSTOM:
                return targetData.loadCustomAnnotations(dataFile, type);
            default:
                return false;
        }
    }
    
    /**
     * Get all annotations as a flat list
     */
    private java.util.List<GeneAnnotation> getAllAnnotations() {
        java.util.List<GeneAnnotation> allAnnotations = new ArrayList<>();
        
        if (!ensureAnnotationDataLoaded()) {
            return allAnnotations;
        }
        
        for (AnnotationType type : AnnotationType.values()) {
            Set<String> genes = annotationData.getAnnotatedGenes(type);
            for (String geneId : genes) {
                java.util.List<GeneAnnotation> geneAnnotations = annotationData.getGeneAnnotations(geneId, type);
                allAnnotations.addAll(geneAnnotations);
            }
        }
        
        allAnnotations.sort(Comparator
            .comparing((GeneAnnotation annotation) -> safeLower(annotation.getGeneId()))
            .thenComparing(annotation -> annotation.getType() != null ? annotation.getType().getShortName() : "")
            .thenComparing(annotation -> safeLower(getDisplayAnnotationId(annotation))));
        
        return allAnnotations;
    }
    
    /**
     * Apply filters to the table
     */
    private void applyFilters() {
        if (isTypeSpecificView && specificType != null) {
            // For specific type view, use metadata-driven filtering
            applyFiltersToMetadataModel();
        } else {
            // For mixed view, filter the combined mixed table in real time
            applyMixedViewFilters();
        }
        updateStatusLabel();
    }
    
    /**
     * Apply filters to metadata-driven table model
     */
    private void applyFiltersToMetadataModel() {
        if (tableModel == null) {
            return;
        }
        
        String geneFilter = geneFilterField.getText().trim().toLowerCase();
        Set<String> batchGeneIds = getBatchFilterGeneIds();
        boolean hasBatchFilter = !batchGeneIds.isEmpty();
        
        if (hasBatchFilter) {
            // Use batch filter with optional partial matching
            Set<String> filterGenes = new HashSet<>();
            for (String geneId : batchGeneIds) {
                filterGenes.add(geneId.toLowerCase());
            }
            // Check if partial matching is enabled for batch filter
            boolean usePartialMatch = batchPartialMatchCheckbox != null && batchPartialMatchCheckbox.isSelected();
            tableModel.filterByGenes(filterGenes, usePartialMatch);
        } else if (!geneFilter.isEmpty()) {
            // Use single gene filter with partial matching
            tableModel.filterByPartialGeneId(geneFilter);
        } else {
            // No filter - reload all data
            tableModel.loadData();
        }
    }
    
    /**
     * Apply legacy filters for mixed annotation view
     */
    private void applyMixedViewFilters() {
        if (!ensureAnnotationDataLoaded()) {
            return;
        }
        
        if (mixedTableModel == null || annotationTable.getModel() != mixedTableModel) {
            initializeMixedTable();
        }
        
        java.util.List<GeneAnnotation> filteredAnnotations = new ArrayList<>();
        java.util.List<GeneAnnotation> allAnnotations = getAllAnnotations();
        
        AnnotationType selectedType = (AnnotationType) typeFilterCombo.getSelectedItem();
        String geneFilter = geneFilterField.getText().trim().toLowerCase();
        
        // Get batch filter gene IDs if any
        Set<String> batchGeneIds = getBatchFilterGeneIds();
        boolean hasBatchFilter = !batchGeneIds.isEmpty();
        boolean usePartialBatchMatch = batchPartialMatchCheckbox != null && batchPartialMatchCheckbox.isSelected();
        
        for (GeneAnnotation annotation : allAnnotations) {
            // Type filter
            if (selectedType != null && annotation.getType() != selectedType) {
                continue;
            }
            
            String lowerGeneId = safeLower(annotation.getGeneId());
            
            // Gene ID filter (single field)
            if (!hasBatchFilter && !geneFilter.isEmpty() && !lowerGeneId.contains(geneFilter)) {
                continue;
            }
            
            // Batch gene ID filter (if active, takes precedence)
            if (hasBatchFilter) {
                boolean matchesBatchFilter = false;
                
                if (usePartialBatchMatch) {
                    for (String batchGeneId : batchGeneIds) {
                        if (lowerGeneId.contains(batchGeneId)) {
                            matchesBatchFilter = true;
                            break;
                        }
                    }
                } else {
                    matchesBatchFilter = batchGeneIds.contains(lowerGeneId);
                }
                
                if (!matchesBatchFilter) {
                    continue;
                }
            }
            
            filteredAnnotations.add(annotation);
        }
        
        if (mixedTableModel != null) {
            mixedTableModel.setAnnotations(filteredAnnotations);
        }
        
        // Enhanced status message
        String statusMsg = "Showing " + filteredAnnotations.size() + " annotations";
        if (hasBatchFilter) {
            statusMsg += " (batch filter: " + batchGeneIds.size() + " gene IDs)";
        } else if (!geneFilter.isEmpty()) {
            statusMsg += " (filtered by: " + geneFilter + ")";
        }
        if (selectedType != null) {
            statusMsg += " [" + selectedType.getShortName() + " only]";
        }
        statusLabel.setText(statusMsg);
    }
    
    /**
     * Apply batch gene ID filter
     */
    private void applyBatchGeneFilter() {
        String batchText = batchGeneIdArea.getText().trim();
        if (batchText.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter gene IDs in the batch filter area.",
                "No Gene IDs", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Apply filters
        applyFilters();
        
        // Show confirmation with type context
        Set<String> batchGeneIds = getBatchFilterGeneIds();
        String typeContext = isTypeSpecificView && specificType != null ? 
            " (in " + specificType.getShortName() + " annotations)" : "";
        statusLabel.setText("Applied batch filter with " + batchGeneIds.size() + " gene IDs" + typeContext);
    }
    
    /**
     * Clear all applied filters (but preserve type filter in single-type view)
     */
    private void clearAllFilters() {
        // In single-type view, don't clear the type filter
        if (!isTypeSpecificView) {
            typeFilterCombo.setSelectedIndex(0); // "All types"
        }
        
        // Always clear gene ID and batch filters
        geneFilterField.setText("");
        batchGeneIdArea.setText("");
        
        applyFilters();
        
        // Update status message based on view mode
        if (isTypeSpecificView && specificType != null) {
            statusLabel.setText("Gene ID filters cleared (still showing " + specificType.getShortName() + " only)");
        } else {
            statusLabel.setText("All filters cleared");
        }
    }
    
    /**
     * Get batch filter gene IDs from the text area
     */
    private Set<String> getBatchFilterGeneIds() {
        Set<String> geneIds = new HashSet<>();
        String batchText = batchGeneIdArea.getText().trim();
        
        if (batchText.isEmpty()) {
            return geneIds;
        }
        
        // Split by lines and commas
        String[] lines = batchText.split("\n");
        for (String line : lines) {
            String[] parts = line.split(",");
            for (String part : parts) {
                String geneId = part.trim();
                if (!geneId.isEmpty()) {
                    geneIds.add(geneId.toLowerCase());
                }
            }
        }
        
        return geneIds;
    }
    
    /**
     * Update selection details
     */
    private void updateSelectionDetails() {
        int selectedRow = annotationTable.getSelectedRow();
        if (selectedRow == -1) {
            detailsArea.setText("Select an annotation to view details");
            return;
        }
        
        int modelRow = annotationTable.convertRowIndexToModel(selectedRow);
        if (mixedTableModel != null && annotationTable.getModel() == mixedTableModel) {
            GeneAnnotation annotation = mixedTableModel.getAnnotationAt(modelRow);
            if (annotation != null) {
                detailsArea.setText(buildMixedAnnotationDetails(annotation, modelRow));
                return;
            }
        }
        
        // For now, show basic selection info until we implement getAnnotationAt in MetadataDrivenTableModel
        String geneId = String.valueOf(annotationTable.getModel().getValueAt(modelRow, 0));
        String details = "Selected gene: " + geneId + "\nRow: " + selectedRow + "\n\nNote: Detailed annotation view will be enhanced in next update.";
        
        detailsArea.setText(details);
    }
    
    /**
     * Build a readable details block for one mixed-view annotation row
     */
    private String buildMixedAnnotationDetails(GeneAnnotation annotation, int modelRow) {
        StringBuilder details = new StringBuilder();
        details.append("Gene ID: ").append(safeText(annotation.getGeneId())).append("\n");
        details.append("Type: ").append(annotation.getType() != null ? annotation.getType().getShortName() : "").append("\n");
        details.append("Annotation ID: ").append(getDisplayAnnotationId(annotation)).append("\n");
        
        String term = getDisplayTerm(annotation);
        if (!term.isEmpty()) {
            details.append("Term: ").append(term).append("\n");
        }
        
        String description = getDisplayDescription(annotation);
        if (!description.isEmpty()) {
            details.append("Description: ").append(description).append("\n");
        }
        
        if (annotation.getEvidence() != null && !annotation.getEvidence().trim().isEmpty()) {
            details.append("Evidence: ").append(annotation.getEvidence().trim()).append("\n");
        }
        
        String[] headers = annotation.getColumnHeaders();
        String[] values = annotation.getColumnValues();
        if (headers != null && values != null && headers.length == values.length) {
            details.append("\nColumns:\n");
            for (int i = 0; i < headers.length; i++) {
                String header = headers[i] != null ? headers[i].trim() : "";
                String value = values[i] != null ? values[i].trim() : "";
                if (!header.isEmpty() && !value.isEmpty()) {
                    details.append(header).append(": ").append(value).append("\n");
                }
            }
        }
        
        details.append("\nRow: ").append(modelRow);
        return details.toString();
    }
    
    /**
     * Edit selected annotation
     */
    private void editSelectedAnnotation() {
        int selectedRow = annotationTable.getSelectedRow();
        if (selectedRow == -1) return;
        
        // For now, show basic info until we implement editing in MetadataDrivenTableModel
        JOptionPane.showMessageDialog(this, 
            "Editing is not yet implemented for the new metadata-driven annotation system.\n" +
            "This feature will be enhanced in the next update.", 
            "Edit Not Available", JOptionPane.INFORMATION_MESSAGE);
        return;
        
        /*
        // TODO: Implement editing for MetadataDrivenTableModel
        // Get selected row data
        int modelRow = annotationTable.convertRowIndexToModel(selectedRow);
        // GeneAnnotation annotation = tableModel.getAnnotationAt(modelRow);
        
        // TODO: Implement editing dialog for metadata-driven model
        */
    }
    
    /**
     * Delete selected annotation
     */
    private void deleteSelectedAnnotation() {
        int selectedRow = annotationTable.getSelectedRow();
        if (selectedRow == -1) return;
        
        // For now, show basic info until we implement deletion in MetadataDrivenTableModel
        JOptionPane.showMessageDialog(this, 
            "Individual annotation deletion is not yet implemented for the new metadata-driven system.\n" +
            "Please use 'Delete All Type' for now, or this feature will be enhanced in the next update.", 
            "Delete Not Available", JOptionPane.INFORMATION_MESSAGE);
        return;
        
        /*
        // TODO: Implement deletion for MetadataDrivenTableModel
        // Get selected row data
        int modelRow = annotationTable.convertRowIndexToModel(selectedRow);
        // GeneAnnotation annotation = tableModel.getAnnotationAt(modelRow);
        
        // TODO: Implement deletion confirmation and processing
        */
    }
    
    /**
     * Export annotations
     */
    private void exportAnnotations() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Tab-separated values (*.tsv)", "tsv"));
            
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".tsv")) {
                file = new File(file.getAbsolutePath() + ".tsv");
            }
            
            // Export current filtered annotations
            try {
                exportTableToFile(file);
                JOptionPane.showMessageDialog(this,
                    "Annotations exported successfully to:\n" + file.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Export failed: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Show annotation statistics
     */
    private void showStatistics() {
        if (annotationData == null) return;
        
        StringBuilder stats = new StringBuilder();
        stats.append("ANNOTATION STATISTICS\n");
        stats.append("=====================\n\n");
        stats.append("Total annotated genes: ").append(annotationData.getAnnotatedGenes()).append("\n\n");
        
        Map<AnnotationType, Integer> counts = annotationData.getAnnotationCounts();
        for (AnnotationType type : AnnotationType.values()) {
            int count = counts.getOrDefault(type, 0);
            if (count > 0) {
                stats.append(type.getDescription()).append(": ").append(count).append(" annotations\n");
            }
        }
        
        stats.append("\nImport time: ");
        if (annotationData.getImportTime() != null) {
            stats.append(annotationData.getImportTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } else {
            stats.append("Unknown");
        }
        
        JOptionPane.showMessageDialog(this, stats.toString(), 
            "Annotation Statistics", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Export table data to file (for metadata-driven model)
     */
    private void exportTableToFile(File file) throws Exception {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file))) {
            // Write header
            for (int col = 0; col < annotationTable.getColumnCount(); col++) {
                if (col > 0) writer.print("\t");
                writer.print(annotationTable.getColumnName(col));
            }
            writer.println();
            
            // Write data
            for (int row = 0; row < annotationTable.getRowCount(); row++) {
                for (int col = 0; col < annotationTable.getColumnCount(); col++) {
                    if (col > 0) writer.print("\t");
                    Object value = annotationTable.getValueAt(row, col);
                    writer.print(value != null ? value.toString() : "");
                }
                writer.println();
            }
        }
    }
    
    /**
     * Import more annotations
     */
    private void importMoreAnnotations() {
        AnnotationImportDialog dialog = new AnnotationImportDialog(this, targetSpecies);
        dialog.setVisible(true);
        
        if (dialog.isImportSuccessful()) {
            refreshData();
            JOptionPane.showMessageDialog(this,
                "Additional annotations imported successfully!",
                "Import Complete", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    /**
     * Remove annotation from data model using proper deletion method
     */
    private boolean removeAnnotationFromData(GeneAnnotation annotation) {
        if (annotationData == null) return false;
        
        try {
            // Use the new removeAnnotation method in GeneAnnotationData
            boolean removed = annotationData.removeAnnotation(annotation);
            
            if (removed) {
                logger.info("Successfully removed annotation: " + annotation.getGeneId() + " -> " + annotation.getAnnotationId());
            } else {
                logger.warning("Failed to remove annotation: " + annotation.getGeneId() + " -> " + annotation.getAnnotationId());
            }
            
            return removed;
            
        } catch (Exception e) {
            logger.warning("Failed to remove annotation: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Delete all annotations of selected type
     */
    private void deleteAllAnnotationsOfType() {
        if (annotationData == null) return;
        
        // Get selected type from filter combo
        AnnotationType selectedType = (AnnotationType) typeFilterCombo.getSelectedItem();
        if (selectedType == null) {
            JOptionPane.showMessageDialog(this,
                "Please select an annotation type from the filter to delete.",
                "No Type Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Get count for confirmation
        int count = annotationData.getAnnotationCounts().get(selectedType);
        if (count == 0) {
            JOptionPane.showMessageDialog(this,
                "No annotations of type " + selectedType.getShortName() + " found.",
                "Nothing to Delete", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Confirmation dialog
        String message = String.format(
            "Are you sure you want to delete ALL annotations of type '%s'?\n\n" +
            "This will permanently remove %d annotations and cannot be undone.\n\n" +
            "Type: %s\n" +
            "Count: %d annotations\n" +
            "This will also delete the corresponding file: %s.tsv",
            selectedType.getShortName(),
            count,
            selectedType.getDescription(),
            count,
            selectedType.getShortName()
        );
        
        int result = JOptionPane.showConfirmDialog(this, message,
            "Confirm Delete All " + selectedType.getShortName() + " Annotations",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            
        if (result == JOptionPane.YES_OPTION) {
            try {
                // Remove all annotations of this type
                int removed = annotationData.removeAllAnnotationsOfType(selectedType);
                
                if (removed > 0) {
                    // Delete the physical file
                    deleteAnnotationFile(selectedType);
                    
                    // Save changes to update remaining files
                    saveAnnotationChanges();
                    
                    // Refresh the display
                    refreshData();
                    
                    // Clear selection and update status
                    annotationTable.clearSelection();
                    detailsArea.setText("All " + selectedType.getShortName() + " annotations deleted successfully");
                    
                    statusLabel.setText("Deleted " + removed + " " + selectedType.getShortName() + " annotations");
                    
                    logger.info("Deleted all " + selectedType.getShortName() + " annotations: " + removed + " items");
                    
                    JOptionPane.showMessageDialog(this,
                        "Successfully deleted " + removed + " " + selectedType.getShortName() + " annotations.",
                        "Deletion Complete", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                        "Failed to delete annotations from data model.",
                        "Delete Error", JOptionPane.ERROR_MESSAGE);
                }
                
            } catch (Exception e) {
                logger.warning("Error deleting all annotations of type " + selectedType + ": " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Error deleting annotations: " + e.getMessage(),
                    "Delete Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Delete the physical annotation file for a type
     */
    private void deleteAnnotationFile(AnnotationType type) {
        if (targetSpecies == null || targetSpecies.getFunctionalAnnotationDir() == null) {
            return;
        }
        
        try {
            File annotationFile = new File(targetSpecies.getFunctionalAnnotationDir(), 
                                         type.getShortName() + ".tsv");
            
            if (annotationFile.exists()) {
                if (annotationFile.delete()) {
                    logger.info("Deleted annotation file: " + annotationFile.getAbsolutePath());
                } else {
                    logger.warning("Failed to delete annotation file: " + annotationFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            logger.warning("Error deleting annotation file: " + e.getMessage());
        }
    }
    
    /**
     * Save annotation changes to file system
     */
    private void saveAnnotationChanges() {
        if (annotationData == null || targetSpecies == null) return;
        
        try {
            // Save to species functional annotation directory
            targetSpecies.saveFunctionalAnnotations();
            
            logger.info("Saved annotation changes for species: " + targetSpecies.getSpeciesName());
            
        } catch (Exception e) {
            logger.warning("Failed to save annotation changes: " + e.getMessage());
            throw new RuntimeException("Failed to save changes to file system", e);
        }
    }
    
    /**
     * Update status label
     */
    private void updateStatusLabel() {
        if (mixedTableModel != null && annotationTable.getModel() == mixedTableModel) {
            int totalAnnotations = annotationData != null
                ? annotationData.getAnnotationCounts().values().stream().mapToInt(Integer::intValue).sum()
                : mixedTableModel.getRowCount();
            AnnotationType selectedType = (AnnotationType) typeFilterCombo.getSelectedItem();
            String viewLabel = selectedType != null ? selectedType.getShortName() : "All Types";
            statusLabel.setText(String.format("View: %s | Rows: %d | Total annotations: %d",
                viewLabel, mixedTableModel.getRowCount(), totalAnnotations));
            return;
        }
        
        if (tableModel != null && tableModel.isLoaded()) {
            // Use metadata-driven information
            AnnotationMetadata metadata = tableModel.getMetadata();
            if (metadata != null) {
                String status = String.format("Type: %s | Rows: %d | Columns: %d | Quality: %.1f%%",
                    tableModel.getAnnotationType() != null ? tableModel.getAnnotationType().getShortName() : "Mixed",
                    tableModel.getRowCount(),
                    tableModel.getColumnCount(),
                    metadata.getDataQualityScore() * 100);
                
                if (tableModel.isPaginated()) {
                    status += String.format(" | Page %d/%d", 
                        tableModel.getCurrentPage() + 1, 
                        tableModel.getTotalPages());
                }
                
                statusLabel.setText(status);
            } else {
                statusLabel.setText(String.format("Rows: %d | Columns: %d", 
                    tableModel.getRowCount(), tableModel.getColumnCount()));
            }
        } else if (annotationData != null) {
            // Fallback to old method for mixed view
            int totalAnnotations = annotationData.getAnnotationCounts().values().stream()
                .mapToInt(Integer::intValue).sum();
            statusLabel.setText("Total: " + totalAnnotations + " annotations for " + 
                              annotationData.getAnnotatedGenes() + " genes");
        } else {
            statusLabel.setText("No data loaded");
        }
    }
    
    /**
     * Setup table context menu for right-click operations (similar to BlastResultsPanel)
     */
    private void setupTableContextMenu() {
        annotationTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTableContextMenu(e);
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTableContextMenu(e);
                }
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showTableContextMenu(e);
                }
            }
        });
    }
    
    /**
     * Show context menu for table operations
     */
    private void showTableContextMenu(MouseEvent e) {
        int row = annotationTable.rowAtPoint(e.getPoint());
        int column = annotationTable.columnAtPoint(e.getPoint());
        
        if (row >= 0 && column >= 0) {
            // Select the cell/row if not already selected
            if (!annotationTable.isRowSelected(row)) {
                annotationTable.setRowSelectionInterval(row, row);
            }
            
            JPopupMenu contextMenu = new JPopupMenu();
            
            // Copy selected cell
            JMenuItem copyItem = new JMenuItem("Copy Cell Value");
            copyItem.addActionListener(ev -> copyCellValue(row, column));
            contextMenu.add(copyItem);
            
            // Copy selected rows
            JMenuItem copyRowsItem = new JMenuItem("Copy Selected Rows");
            copyRowsItem.addActionListener(ev -> copySelectedRows());
            contextMenu.add(copyRowsItem);
            
            contextMenu.addSeparator();
            
            // Column operations
            if (column >= 0) {
                JMenu columnMenu = new JMenu("Column: " + annotationTable.getColumnName(column));
                
                JMenuItem sortAscItem = new JMenuItem("Sort Ascending");
                sortAscItem.addActionListener(ev -> sortColumn(column, true));
                columnMenu.add(sortAscItem);
                
                JMenuItem sortDescItem = new JMenuItem("Sort Descending");
                sortDescItem.addActionListener(ev -> sortColumn(column, false));
                columnMenu.add(sortDescItem);
                
                columnMenu.addSeparator();
                
                JMenuItem removeDuplicatesItem = new JMenuItem("Remove Duplicates in Column");
                removeDuplicatesItem.addActionListener(ev -> removeDuplicatesInColumn(column));
                columnMenu.add(removeDuplicatesItem);
                
                contextMenu.add(columnMenu);
            }
            
            contextMenu.addSeparator();
            
            // Selection operations
            JMenuItem selectAllItem = new JMenuItem("Select All Rows");
            selectAllItem.addActionListener(ev -> annotationTable.selectAll());
            contextMenu.add(selectAllItem);
            
            JMenuItem clearSelectionItem = new JMenuItem("Clear Selection");
            clearSelectionItem.addActionListener(ev -> annotationTable.clearSelection());
            contextMenu.add(clearSelectionItem);
            
            contextMenu.show(annotationTable, e.getX(), e.getY());
        }
    }
    
    /**
     * Copy selected cell value to clipboard
     */
    private void copyCellValue(int row, int column) {
        if (row >= 0 && column >= 0) {
            Object value = annotationTable.getValueAt(row, column);
            String text = value != null ? value.toString() : "";
            
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            
            statusLabel.setText("Copied cell value to clipboard");
        }
    }
    
    /**
     * Copy selected rows to clipboard
     */
    private void copySelectedRows() {
        int[] selectedRows = annotationTable.getSelectedRows();
        if (selectedRows.length == 0) {
            statusLabel.setText("No rows selected");
            return;
        }
        
        StringBuilder content = new StringBuilder();
        
        // Add header
        for (int col = 0; col < annotationTable.getColumnCount(); col++) {
            if (col > 0) content.append("\t");
            content.append(annotationTable.getColumnName(col));
        }
        content.append("\n");
        
        // Add selected rows
        for (int row : selectedRows) {
            for (int col = 0; col < annotationTable.getColumnCount(); col++) {
                if (col > 0) content.append("\t");
                Object value = annotationTable.getValueAt(row, col);
                content.append(value != null ? value.toString() : "");
            }
            content.append("\n");
        }
        
        StringSelection selection = new StringSelection(content.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        
        statusLabel.setText("Copied " + selectedRows.length + " rows to clipboard");
    }
    
    /**
     * Sort column in specified order using TableRowSorter
     */
    private void sortColumn(int column, boolean ascending) {
        RowSorter<? extends TableModel> sorter = annotationTable.getRowSorter();
        if (sorter != null) {
            List<RowSorter.SortKey> sortKeys = new ArrayList<>();
            SortOrder order = ascending ? SortOrder.ASCENDING : SortOrder.DESCENDING;
            sortKeys.add(new RowSorter.SortKey(column, order));
            sorter.setSortKeys(sortKeys);
            
            statusLabel.setText("Sorted by " + annotationTable.getColumnName(column) + 
                              (ascending ? " (ascending)" : " (descending)"));
        }
    }
    
    /**
     * Remove duplicates in specified column
     */
    private void removeDuplicatesInColumn(int column) {
        List<GeneAnnotation> currentAnnotations = getAllAnnotations();
        Set<String> seenValues = new HashSet<>();
        List<GeneAnnotation> uniqueAnnotations = new ArrayList<>();
        
        for (GeneAnnotation annotation : currentAnnotations) {
            String columnValue = "TODO"; // getColumnValue method removed - needs reimplementation
            if (!seenValues.contains(columnValue)) {
                seenValues.add(columnValue);
                uniqueAnnotations.add(annotation);
            }
        }
        
        // TODO: Implement setAnnotations for MetadataDrivenTableModel
        // tableModel.setAnnotations(uniqueAnnotations);
        statusLabel.setText("Remove duplicates feature needs to be reimplemented for metadata-driven model");
        
        JOptionPane.showMessageDialog(this,
            "Remove duplicates feature is not yet implemented for the new metadata-driven system.\n" +
            "This feature will be enhanced in the next update.",
            "Feature Not Available", JOptionPane.INFORMATION_MESSAGE);
    }
    
    // Legacy table model completely removed - replaced by MetadataDrivenTableModel
    
    /**
     * Safely normalize text for matching/sorting
     */
    private String safeLower(String text) {
        return text != null ? text.toLowerCase() : "";
    }
    
    private String safeText(String text) {
        return text != null ? text.trim() : "";
    }
    
    private String getDisplayAnnotationId(GeneAnnotation annotation) {
        String dynamicValue = getDynamicColumnValue(annotation, 1);
        if (!dynamicValue.isEmpty()) {
            return dynamicValue;
        }
        return safeText(annotation.getAnnotationId());
    }
    
    private String getDisplayTerm(GeneAnnotation annotation) {
        String dynamicValue = getDynamicColumnValue(annotation, 2);
        if (!dynamicValue.isEmpty()) {
            return dynamicValue;
        }
        return safeText(annotation.getTerm());
    }
    
    private String getDisplayDescription(GeneAnnotation annotation) {
        String dynamicValue = getDynamicColumnValue(annotation, 3);
        if (!dynamicValue.isEmpty()) {
            return dynamicValue;
        }
        
        String description = safeText(annotation.getDescription());
        if (!description.isEmpty()) {
            return description;
        }
        
        String[] values = annotation.getColumnValues();
        if (values != null && values.length > 1) {
            StringBuilder fallback = new StringBuilder();
            for (int i = 1; i < values.length; i++) {
                String value = values[i] != null ? values[i].trim() : "";
                if (!value.isEmpty()) {
                    if (fallback.length() > 0) {
                        fallback.append(" | ");
                    }
                    fallback.append(value);
                }
            }
            return fallback.toString();
        }
        
        return "";
    }
    
    private String getDynamicColumnValue(GeneAnnotation annotation, int index) {
        String[] values = annotation.getColumnValues();
        if (values != null && index >= 0 && index < values.length) {
            return safeText(values[index]);
        }
        return "";
    }
    
    /**
     * Lightweight mixed-view table model used by "View All Types Mixed"
     */
    private static class MixedAnnotationTableModel extends AbstractTableModel {
        private final List<GeneAnnotation> annotations = new ArrayList<>();
        
        public void setAnnotations(List<GeneAnnotation> newAnnotations) {
            annotations.clear();
            if (newAnnotations != null) {
                annotations.addAll(newAnnotations);
            }
            fireTableDataChanged();
        }
        
        public GeneAnnotation getAnnotationAt(int row) {
            if (row < 0 || row >= annotations.size()) {
                return null;
            }
            return annotations.get(row);
        }
        
        @Override
        public int getRowCount() {
            return annotations.size();
        }
        
        @Override
        public int getColumnCount() {
            return MIXED_VIEW_COLUMNS.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return MIXED_VIEW_COLUMNS[column];
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            GeneAnnotation annotation = getAnnotationAt(rowIndex);
            if (annotation == null) {
                return "";
            }
            
            switch (columnIndex) {
                case 0:
                    return annotation.getGeneId() != null ? annotation.getGeneId() : "";
                case 1:
                    return annotation.getType() != null ? annotation.getType().getShortName() : "";
                case 2:
                    return getMixedDisplayValue(annotation, 1, annotation.getAnnotationId());
                case 3:
                    return getMixedDisplayValue(annotation, 2, annotation.getTerm());
                case 4:
                    String dynamicDescription = getMixedDisplayValue(annotation, 3, annotation.getDescription());
                    if (!dynamicDescription.isEmpty()) {
                        return dynamicDescription;
                    }
                    return buildFallbackDescription(annotation);
                default:
                    return "";
            }
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }
        
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
        
        private static String getMixedDisplayValue(GeneAnnotation annotation, int dynamicIndex, String fallback) {
            String[] values = annotation.getColumnValues();
            if (values != null && dynamicIndex >= 0 && dynamicIndex < values.length) {
                String dynamicValue = values[dynamicIndex] != null ? values[dynamicIndex].trim() : "";
                if (!dynamicValue.isEmpty()) {
                    return dynamicValue;
                }
            }
            return fallback != null ? fallback.trim() : "";
        }
        
        private static String buildFallbackDescription(GeneAnnotation annotation) {
            String[] values = annotation.getColumnValues();
            if (values == null || values.length <= 1) {
                return "";
            }
            
            StringBuilder fallback = new StringBuilder();
            for (int i = 1; i < values.length; i++) {
                String value = values[i] != null ? values[i].trim() : "";
                if (!value.isEmpty()) {
                    if (fallback.length() > 0) {
                        fallback.append(" | ");
                    }
                    fallback.append(value);
                }
            }
            return fallback.toString();
        }
    }
}
