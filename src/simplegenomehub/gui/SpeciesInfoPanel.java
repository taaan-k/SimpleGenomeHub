/*
 * Species Information Display Panel
 */
package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesMetadata;
import simplegenomehub.model.GeneAnnotationData;
import simplegenomehub.model.GeneAnnotationData.AnnotationType;
import simplegenomehub.model.AnnotationMetadata;
import simplegenomehub.gui.AnnotationTypeSelectionDialog;
import simplegenomehub.gui.GOEnrichmentDialog;
import simplegenomehub.gui.KEGGEnrichmentDialog;
import simplegenomehub.model.GenomeData;
import simplegenomehub.util.fileio.AdvancedCircosTemplateGenerator;
import simplegenomehub.util.fileio.DemoGeneSetGenerator;
import simplegenomehub.util.fileio.GenomeStatsCalculator;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.SwingWorker;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Panel for displaying detailed species information and file operations
 * 
 * @author SimpleGenomeHub
 */
public class SpeciesInfoPanel extends JPanel {
    
    private static final Logger logger = Logger.getLogger(SpeciesInfoPanel.class.getName());
    
    private SpeciesInfo currentSpecies;
    private simplegenomehub.model.SpeciesManager speciesManager;

    private SpeciesOverviewPanel overviewPanel;
    private SpeciesActionsPanel actionsPanel;
    
    /**
     * Constructor
     */
    public SpeciesInfoPanel() {
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setSpecies(null); // Initialize with no selection
    }
    
    /**
     * Constructor with SpeciesManager
     */
    public SpeciesInfoPanel(simplegenomehub.model.SpeciesManager speciesManager) {
        this.speciesManager = speciesManager;
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setSpecies(null); // Initialize with no selection
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        overviewPanel = new SpeciesOverviewPanel();
        actionsPanel = new SpeciesActionsPanel();
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 12));
        add(createOverviewAndActionsPanel(), BorderLayout.CENTER);
    }

    private JPanel createOverviewAndActionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        panel.add(overviewPanel, BorderLayout.CENTER);
        panel.add(actionsPanel, BorderLayout.SOUTH);
        return panel;
    }
    
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        actionsPanel.getFileOperationsButton().addActionListener(e -> showFileOperationsMenu());
        actionsPanel.getSequenceLookupButton().addActionListener(e -> showSequenceToolsMenu());
        actionsPanel.getExpressionDataButton().addActionListener(e -> showExpressionDataMenu());
        actionsPanel.getFunctionAnnotationButton().addActionListener(e -> showFunctionAnnotationMenu());
        actionsPanel.getGeneSetButton().addActionListener(e -> showGeneSetMenu());
        actionsPanel.getGeneViewerButton().addActionListener(e -> showGeneInfoMenu());
        actionsPanel.getGenomeAnalysisButton().addActionListener(e -> showGenomeAnalysisMenu());
        actionsPanel.getBlastAnalysisButton().addActionListener(e -> openBlastAnalysisDialog());

        overviewPanel.getNotesArea().addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveNotesIfChanged();
            }
        });
    }
    
    /**
     * Set the species to display
     */
    public void setSpecies(SpeciesInfo species) {
        this.currentSpecies = species;
        updateDisplay();
        overviewPanel.applyOtherDataSelection(null);
    }

    public void applyTreeSelection(SpeciesTreePanel.SelectionContext selectionContext) {
        SpeciesInfo species = selectionContext != null ? selectionContext.getSpecies() : null;
        this.currentSpecies = species;
        updateDisplay();
        overviewPanel.applyOtherDataSelection(selectionContext);
    }
    
    /**
     * Get current species
     */
    public SpeciesInfo getCurrentSpecies() {
        return currentSpecies;
    }
    
    /**
     * Update the display with current species information
     */
    private void updateDisplay() {
        overviewPanel.setSpecies(currentSpecies);
        if (currentSpecies == null) {
            actionsPanel.setAllEnabled(false);
            return;
        }
        updateFileOperationButtons();
    }
    
    /**
     * Update file operation buttons based on file availability
     */
    private void updateFileOperationButtons() {
        if (currentSpecies == null) {
            actionsPanel.setAllEnabled(false);
            return;
        }

        boolean hasGenome = currentSpecies.hasGenomeFiles();
        boolean hasAnnotation = currentSpecies.hasAnnotationFiles();
        boolean hasStats = currentSpecies.getStatsFile() != null && currentSpecies.getStatsFile().exists();
        boolean hasDirectory = currentSpecies.getSpeciesDir() != null && currentSpecies.getSpeciesDir().exists();
        boolean hasGeneSetSupport = hasDirectory && (hasAnnotation || hasStats);

        boolean hasAnyFileOperation = (hasGenome || hasAnnotation) || hasDirectory || (hasGenome && hasAnnotation);
        actionsPanel.updateButtonStates(
            hasAnyFileOperation,
            hasGenome && hasAnnotation,
            hasDirectory,
            hasDirectory,
            hasGeneSetSupport,
            hasDirectory,
            hasDirectory,
            hasDirectory
        );
    }
    
    /**
     * Copy genome file path to clipboard
     */
    private void copyGenomePath() {
        if (currentSpecies != null) {
            File genomeFile = currentSpecies.getGenomeFile();
            if (genomeFile != null && genomeFile.exists()) {
                String path = genomeFile.getAbsolutePath();
                copyToClipboard(path);
                JOptionPane.showMessageDialog(this, "Genome file path copied to clipboard:\n" + path,
                    "Path Copied", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                    "No genome file found for this species.",
                    "Genome File Not Found",
                    JOptionPane.WARNING_MESSAGE);
            }
        }
    }
    
    /**
     * Copy annotation file path to clipboard
     */
    private void copyAnnotationPath() {
        if (currentSpecies != null && currentSpecies.getAnnotationDir() != null) {
            File[] annotationFiles = currentSpecies.getAnnotationDir().listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".gff") || 
                name.toLowerCase().endsWith(".gff3") ||
                name.toLowerCase().endsWith(".gtf"));
                
            if (annotationFiles != null && annotationFiles.length > 0) {
                String path = annotationFiles[0].getAbsolutePath();
                copyToClipboard(path);
                JOptionPane.showMessageDialog(this, "Annotation file path copied to clipboard:\n" + path,
                    "Path Copied", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }
    
    /**
     * Open species directory in system file manager
     */
    private void openSpeciesDirectory() {
        if (currentSpecies != null && currentSpecies.getSpeciesDir() != null) {
            try {
                Desktop.getDesktop().open(currentSpecies.getSpeciesDir());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Failed to open directory:\n" + e.getMessage(),
                    "Open Directory Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Preview genome file
     */
    private void previewGenomeFile() {
        if (currentSpecies != null) {
            File genomeFile = currentSpecies.getGenomeFile();
            if (genomeFile != null && genomeFile.exists()) {
                FilePreviewDialog dialog = new FilePreviewDialog(
                    SwingUtilities.getWindowAncestor(this), genomeFile);
                dialog.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this,
                    "No genome file found for this species.",
                    "Genome File Not Found",
                    JOptionPane.WARNING_MESSAGE);
            }
        }
    }
    
    /**
     * Preview annotation file
     */
    private void previewAnnotationFile() {
        if (currentSpecies != null && currentSpecies.getAnnotationDir() != null) {
            File[] annotationFiles = currentSpecies.getAnnotationDir().listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".gff") || 
                name.toLowerCase().endsWith(".gff3") ||
                name.toLowerCase().endsWith(".gtf"));
                
            if (annotationFiles != null && annotationFiles.length > 0) {
                FilePreviewDialog dialog = new FilePreviewDialog(
                    SwingUtilities.getWindowAncestor(this), annotationFiles[0]);
                dialog.setVisible(true);
            }
        }
    }
    
    
    /**
     * Copy text to system clipboard
     */
    private void copyToClipboard(String text) {
        try {
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
            java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to copy to clipboard: " + e.getMessage(),
                "Clipboard Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Extract sequences from genome and annotation
     */
    private void extractSequences() {
        if (currentSpecies != null) {
            SequenceExtractionDialog dialog = new SequenceExtractionDialog(
                SwingUtilities.getWindowAncestor(this), currentSpecies);
            dialog.setVisible(true);
        }
    }
    
    /**
     * Open sequence lookup dialog for current species
     */
    private void openSequenceLookup() {
        if (currentSpecies != null) {
            SequenceLookupDialog dialog = new SequenceLookupDialog(
                SwingUtilities.getWindowAncestor(this), currentSpecies);
            dialog.setVisible(true);
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
        }
    }
    
    /**
     * Show sequence tools menu (combining sequence lookup, region extraction, and species identification)
     */
    private void showSequenceToolsMenu() {
        JPopupMenu menu = new JPopupMenu();

        // Sequence lookup option (requires species selection)
        JMenuItem lookupItem = new JMenuItem("Fasta Extract");
        lookupItem.setToolTipText("Search and retrieve sequences by gene/transcript ID");
        if (currentSpecies == null) {
            lookupItem.setEnabled(false);
            lookupItem.setToolTipText("Please select a species first");
        } else {
            lookupItem.addActionListener(e -> openSequenceLookup());
        }
        menu.add(lookupItem);

        // Extract regions option (requires species selection)
        JMenuItem extractItem = new JMenuItem("Fasta Region Extract");
        extractItem.setToolTipText("Extract chromosome region sequences using TBtools engine");
        if (currentSpecies == null) {
            extractItem.setEnabled(false);
            extractItem.setToolTipText("Please select a species first");
        } else {
            extractItem.addActionListener(e -> openChromosomeRegionExtractor());
        }
        menu.add(extractItem);

        // Extract CDS/Protein sequences option (requires species selection)
        JMenuItem extractSequencesItem = new JMenuItem("Get CDS/Pep./Promoter Seq. Set");
        extractSequencesItem.setToolTipText("Extract CDS, protein, and promoter sequences from genome and annotation");
        if (currentSpecies == null) {
            extractSequencesItem.setEnabled(false);
            extractSequencesItem.setToolTipText("Please select a species first");
        } else {
            extractSequencesItem.addActionListener(e -> extractSequences());
        }
        menu.add(extractSequencesItem);

        // Show menu below the button
        JButton anchor = actionsPanel.getSequenceLookupButton();
        SimpleGenomeHubUi.stylePopupMenu(menu);
        menu.show(anchor, 0, anchor.getHeight());
    }
    
    /**
     * Open BLAST analysis dialog for current species
     */
    private void openBlastAnalysis() {
        if (currentSpecies != null) {
            try {
                BlastDialog dialog = new BlastDialog(
                    SwingUtilities.getWindowAncestor(this), currentSpecies, speciesManager);
                dialog.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to open BLAST analysis: " + e.getMessage(),
                    "BLAST Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Open BLAST analysis dialog from external UI actions such as the main menu
     */
    public void openBlastAnalysisDialog() {
        openBlastAnalysis();
    }

    /**
     * Open genome compare dialog for current species
     */
    private void openGenomeCompareDialog() {
        if (currentSpecies != null) {
            try {
                GenomeCompareDialog dialog = new GenomeCompareDialog(
                    SwingUtilities.getWindowAncestor(this), currentSpecies, speciesManager);
                dialog.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to open MCscanX (Pure Java): " + e.getMessage(),
                    "MCscanX (Pure Java) Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    private void openMultipleSyntenyDialog() {
        if (currentSpecies != null) {
            try {
                MultipleSyntenyDialog dialog = new MultipleSyntenyDialog(
                    SwingUtilities.getWindowAncestor(this), currentSpecies, speciesManager);
                dialog.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to open Multiple Synteny: " + e.getMessage(),
                    "Multiple Synteny Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
        }
    }

    private void showPendingFeatureMessage(String featureName) {
        JOptionPane.showMessageDialog(this,
            featureName + " layout is ready. Functionality will be added next.",
            "Coming Soon", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Save notes if they have been changed
     */
    private void saveNotesIfChanged() {
        if (currentSpecies == null) {
            return;
        }
        
        JTextArea notesArea = overviewPanel.getNotesArea();
        String currentNotes = notesArea.getText().trim();
        String originalNotes = currentSpecies.getNotes() != null ? currentSpecies.getNotes().trim() : "";
        
        // Check if notes have actually changed
        if (!currentNotes.equals(originalNotes)) {
            // Update species object
            currentSpecies.setNotes(currentNotes);
            
            // Save metadata to disk
            SpeciesMetadata metadata = SpeciesMetadata.fromSpeciesInfo(currentSpecies);
            if (metadata.saveToFile(currentSpecies.getSpeciesDir())) {
                // Update in species manager if available
                if (speciesManager != null) {
                    speciesManager.updateSpecies(currentSpecies);
                }
                
                // Show subtle confirmation
                notesArea.setBackground(new Color(220, 255, 220)); // Light green
                Timer timer = new Timer(2000, e -> notesArea.setBackground(Color.WHITE));
                timer.setRepeats(false);
                timer.start();
            } else {
                // Show error indication
                notesArea.setBackground(new Color(255, 220, 220)); // Light red
                Timer timer = new Timer(3000, e -> notesArea.setBackground(Color.WHITE));
                timer.setRepeats(false);
                timer.start();
                
                JOptionPane.showMessageDialog(this,
                    "Failed to save notes. Please check file permissions.",
                    "Save Error", JOptionPane.WARNING_MESSAGE);
            }
        }
    }
    
    // Expression analysis methods
    
    /**
     * Show expression data menu with import/manage options
     */
    private void showExpressionDataMenu() {
        if (currentSpecies == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Create popup menu
        JPopupMenu menu = new JPopupMenu();
        
        // Import option
        JMenuItem importItem = new JMenuItem("Import Expression Data");
        importItem.addActionListener(e -> importExpressionData());
        menu.add(importItem);
        
        // Manage option (only if data exists)
        if (currentSpecies.hasExpressionData()) {
            JMenuItem manageItem = new JMenuItem("Explore Expression Data");
            manageItem.addActionListener(e -> manageExpressionData());
            menu.add(manageItem);
        }
        
        // Show menu below the button
        JButton anchor = actionsPanel.getExpressionDataButton();
        SimpleGenomeHubUi.stylePopupMenu(menu);
        menu.show(anchor, 0, anchor.getHeight());
    }
    
    /**
     * Open expression data import dialog
     */
    private void importExpressionData() {
        if (currentSpecies == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        SimpleExpressionImportDialog dialog = new SimpleExpressionImportDialog(
            SwingUtilities.getWindowAncestor(this), currentSpecies);
        dialog.setVisible(true);
        
        if (dialog.isImportSuccessful()) {
            // Refresh the UI to reflect new expression data
            updateFileOperationButtons();
            JOptionPane.showMessageDialog(this,
                "Expression data imported successfully!\nYou can now use other expression analysis functions.",
                "Import Complete", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Open expression import dialog from external UI actions such as the main menu
     */
    public void openExpressionDataImportDialog() {
        importExpressionData();
    }
    
    /**
     * Open expression data management interface
     */
    private void manageExpressionData() {
        if (currentSpecies.hasExpressionData()) {
            ExpressionDataPanel panel = new ExpressionDataPanel(
                SwingUtilities.getWindowAncestor(this), currentSpecies);
            panel.setVisible(true);
            
            // Refresh buttons in case data was modified
            updateFileOperationButtons();
        } else {
            JOptionPane.showMessageDialog(this,
                "No expression data available. Please import expression data first.",
                "No Expression Data",
                JOptionPane.WARNING_MESSAGE);
        }
    }
    
        
    /**
     * Open chromosome region extraction dialog
     */
    private void openChromosomeRegionExtractor() {
        if (currentSpecies != null) {
            try {
                ChromosomeRegionExtractorDialog dialog = new ChromosomeRegionExtractorDialog(
                    (Frame) SwingUtilities.getWindowAncestor(this), currentSpecies);
                dialog.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Failed to open chromosome region extractor: " + e.getMessage(),
                    "Extractor Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
        }
    }
    /**
     * Show consolidated file operations menu
     */
    private void showFileOperationsMenu() {
        if (currentSpecies == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Create popup menu
        JPopupMenu menu = new JPopupMenu();

        // Copy Paths section
        JMenuItem copyPathsHeader = new JMenuItem("Copy Paths");
        copyPathsHeader.setEnabled(false);
        copyPathsHeader.setFont(SimpleGenomeHubStyle.bold(copyPathsHeader.getFont()));
        menu.add(copyPathsHeader);
        menu.addSeparator();

        JMenuItem copyGenomeItem = new JMenuItem("Copy Genome Path");
        copyGenomeItem.addActionListener(e -> copyGenomePath());
        menu.add(copyGenomeItem);

        JMenuItem copyAnnotationItem = new JMenuItem("Copy Annotation Path");
        copyAnnotationItem.addActionListener(e -> copyAnnotationPath());
        menu.add(copyAnnotationItem);

        menu.addSeparator();

        // Preview Files section
        JMenuItem previewFilesHeader = new JMenuItem("Preview Files");
        previewFilesHeader.setEnabled(false);
        previewFilesHeader.setFont(SimpleGenomeHubStyle.bold(previewFilesHeader.getFont()));
        menu.add(previewFilesHeader);
        menu.addSeparator();

        JMenuItem previewGenomeItem = new JMenuItem("Preview Genome");
        previewGenomeItem.addActionListener(e -> previewGenomeFile());
        menu.add(previewGenomeItem);

        JMenuItem previewAnnotationItem = new JMenuItem("Preview Annotation");
        previewAnnotationItem.addActionListener(e -> previewAnnotationFile());
        menu.add(previewAnnotationItem);

        menu.addSeparator();

        // Data Access section
        JMenuItem dataAccessHeader = new JMenuItem("Data Access");
        dataAccessHeader.setEnabled(false);
        dataAccessHeader.setFont(SimpleGenomeHubStyle.bold(dataAccessHeader.getFont()));
        menu.add(dataAccessHeader);
        menu.addSeparator();

        JMenuItem openDirectoryItem = new JMenuItem("Open Directory");
        openDirectoryItem.addActionListener(e -> openSpeciesDirectory());
        menu.add(openDirectoryItem);

        // Show menu below the button
        JButton anchor = actionsPanel.getFileOperationsButton();
        SimpleGenomeHubUi.stylePopupMenu(menu);
        menu.show(anchor, 0, anchor.getHeight());
    }

    /**
     * Show consolidated function annotation menu
     */
    private void showFunctionAnnotationMenu() {
        if (currentSpecies == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Create popup menu
        JPopupMenu menu = new JPopupMenu();

        // Annotation Management section
        JMenuItem annotationManagementHeader = new JMenuItem("Annotation Management");
        annotationManagementHeader.setEnabled(false);
        annotationManagementHeader.setFont(SimpleGenomeHubStyle.bold(annotationManagementHeader.getFont()));
        menu.add(annotationManagementHeader);
        menu.addSeparator();

        JMenuItem autoImportItem = new JMenuItem("Auto Func. Annotations");
        autoImportItem.setToolTipText("Run local eggNOG-mapper on representative proteins and import GO/KEGG/PFAM/Description");
        autoImportItem.addActionListener(e -> autoFunctionalAnnotations());
        menu.add(autoImportItem);

        // Import Annotations
        JMenuItem importItem = new JMenuItem("Import Annotations");
        importItem.addActionListener(e -> importFunctionalAnnotations());
        menu.add(importItem);

        // Explore Annotations
        JMenuItem manageItem = new JMenuItem("Explore Annotations");
        manageItem.addActionListener(e -> manageFunctionalAnnotations());

        // Enable/disable based on annotation availability
        boolean hasAnnotations = currentSpecies.hasFunctionalAnnotations();
        if (!hasAnnotations) {
            manageItem.setEnabled(false);
            manageItem.setToolTipText("Import annotations first to enable management");
        } else {
            manageItem.setEnabled(true);
            manageItem.setToolTipText("Manage imported functional annotations");
        }
        menu.add(manageItem);

        // Delete Annotations submenu
        JMenu deleteMenu = new JMenu("Delete Annotations");
        deleteMenu.setEnabled(hasAnnotations);
        if (!hasAnnotations) {
            deleteMenu.setToolTipText("No annotations to delete");
        }

        if (hasAnnotations) {
            // Get available annotation types from files
            Map<AnnotationType, Integer> counts = getAnnotationCountsFromFiles();

            for (AnnotationType type : AnnotationType.values()) {
                int count = counts.getOrDefault(type, 0);
                if (count > 0) {
                    String menuText = "Delete All " + type.getShortName();
                    JMenuItem deleteItem = new JMenuItem(menuText);
                    deleteItem.addActionListener(e -> deleteAllAnnotations());
                    deleteMenu.add(deleteItem);
                }
            }
        }
        menu.add(deleteMenu);

        menu.addSeparator();

        // Enrichment Analysis section
        JMenuItem enrichmentAnalysisHeader = new JMenuItem("Enrichment Analysis");
        enrichmentAnalysisHeader.setEnabled(false);
        enrichmentAnalysisHeader.setFont(SimpleGenomeHubStyle.bold(enrichmentAnalysisHeader.getFont()));
        menu.add(enrichmentAnalysisHeader);
        menu.addSeparator();

        // GO Enrichment Analysis
        JMenuItem goEnrichmentItem = new JMenuItem("GO Enrichment Analysis");
        goEnrichmentItem.setToolTipText("Gene Ontology functional enrichment analysis using TBtools engine");
        goEnrichmentItem.addActionListener(e -> {
            GOEnrichmentDialog dialog = new GOEnrichmentDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), currentSpecies);
            dialog.setVisible(true);
        });
        menu.add(goEnrichmentItem);

        // KEGG Enrichment Analysis
        JMenuItem keggEnrichmentItem = new JMenuItem("KEGG Enrichment Analysis");
        keggEnrichmentItem.setToolTipText("KEGG pathway enrichment analysis using TBtools engine");
        keggEnrichmentItem.addActionListener(e -> {
            KEGGEnrichmentDialog dialog = new KEGGEnrichmentDialog(
                (Frame) SwingUtilities.getWindowAncestor(this), currentSpecies);
            dialog.setVisible(true);
        });
        menu.add(keggEnrichmentItem);

        // Show menu below the button
        JButton anchor = actionsPanel.getFunctionAnnotationButton();
        SimpleGenomeHubUi.stylePopupMenu(menu);
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void showGeneSetMenu() {
        if (currentSpecies == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean hasAnnotation = currentSpecies.hasAnnotationFiles();
        boolean hasStats = currentSpecies.getStatsFile() != null && currentSpecies.getStatsFile().exists();
        if (!hasAnnotation && !hasStats) {
            JOptionPane.showMessageDialog(this,
                "The selected species does not have an annotation file or stat.txt.",
                "Required Data Missing",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        JPopupMenu menu = new JPopupMenu();

        JMenuItem createItem = new JMenuItem("Create a Gene Set");
        createItem.setEnabled(hasAnnotation);
        createItem.setToolTipText(hasAnnotation ? null : "Annotation file required");
        createItem.addActionListener(e -> openCreateGeneSetDialog());
        menu.add(createItem);

        JMenuItem createRegionItem = new JMenuItem("Create a Region Set");
        createRegionItem.setEnabled(hasStats);
        createRegionItem.setToolTipText(hasStats ? null : "stat.txt required");
        createRegionItem.addActionListener(e -> openCreateRegionSetDialog());
        menu.add(createRegionItem);

        JMenuItem randomItem = new JMenuItem("Random Gene Set");
        randomItem.setEnabled(hasAnnotation);
        randomItem.setToolTipText(hasAnnotation ? null : "Annotation file required");
        randomItem.addActionListener(e -> generateRandomGeneSetForCurrentSpecies());
        menu.add(randomItem);

        JButton anchor = actionsPanel.getGeneSetButton();
        SimpleGenomeHubUi.stylePopupMenu(menu);
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void openCreateGeneSetDialog() {
        if (currentSpecies == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        CreateGeneSetDialog dialog = new CreateGeneSetDialog(
            SwingUtilities.getWindowAncestor(this),
            currentSpecies,
            () -> {
                if (speciesManager != null) {
                    speciesManager.updateSpecies(currentSpecies);
                }
            });
        dialog.setVisible(true);
    }

    private void generateRandomGeneSetForCurrentSpecies() {
        if (currentSpecies == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            DemoGeneSetGenerator.Result result = DemoGeneSetGenerator.generateDemoGeneSet(currentSpecies);
            if (speciesManager != null) {
                speciesManager.updateSpecies(currentSpecies);
            }
            JOptionPane.showMessageDialog(this,
                "Random Gene Set created successfully:\n"
                    + result.getOutputFile().getName() + "\n"
                    + "Representative transcripts: " + result.getSelectedTranscriptCount(),
                "Random Gene Set Created",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            logger.warning("Failed to create Random Gene Set: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                "Failed to create Random Gene Set:\n" + ex.getMessage(),
                "Random Gene Set Failed",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openCreateRegionSetDialog() {
        if (currentSpecies == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        CreateRegionSetDialog dialog = new CreateRegionSetDialog(
            SwingUtilities.getWindowAncestor(this),
            currentSpecies,
            () -> {
                if (speciesManager != null) {
                    speciesManager.updateSpecies(currentSpecies);
                }
            });
        dialog.setVisible(true);
    }

    /**
     * Show placeholder genome analysis menu
     */
    private void showGenomeAnalysisMenu() {
        if (currentSpecies == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        JPopupMenu menu = new JPopupMenu();
        JMenuItem circosItem = new JMenuItem("Circos");
        circosItem.addActionListener(e -> prepareAdvancedCircosTemplate());
        menu.add(circosItem);
        JMenuItem genomeCompareItem = new JMenuItem("MCscanX (Pure Java)");
        genomeCompareItem.addActionListener(e -> openGenomeCompareDialog());
        menu.add(genomeCompareItem);
        JMenuItem multipleSyntenyItem = new JMenuItem("Multiple Synteny");
        multipleSyntenyItem.addActionListener(e -> openMultipleSyntenyDialog());
        menu.add(multipleSyntenyItem);

        JButton anchor = actionsPanel.getGenomeAnalysisButton();
        SimpleGenomeHubUi.stylePopupMenu(menu);
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void prepareAdvancedCircosTemplate() {
        if (currentSpecies == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a species first.",
                "No Species Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        File functionalAnnotationDir = currentSpecies.getFunctionalAnnotationDir();
        if (functionalAnnotationDir == null) {
            JOptionPane.showMessageDialog(this,
                "FunctionalAnnotation directory is not available for the selected species.",
                "Directory Missing",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog progressDialog = new JDialog(
            SwingUtilities.getWindowAncestor(this),
            "Preparing Advanced Circos Template",
            Dialog.ModalityType.APPLICATION_MODAL
        );
        JLabel statusLabel = new JLabel("Preparing Advanced Circos files...");
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        JPanel progressPanel = new JPanel(new BorderLayout(10, 10));
        progressPanel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        progressPanel.add(statusLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(progressPanel);
        progressDialog.setSize(420, 120);
        progressDialog.setLocationRelativeTo(this);

        SwingWorker<File, String> worker = new SwingWorker<File, String>() {
            @Override
            protected File doInBackground() throws Exception {
                publish("Checking chromosome statistics...");
                GenomeData genomeData = ensureGenomeDataWithChromosomeStats();
                currentSpecies.setGenomeData(genomeData);
                publish("Creating Advanced Circos template files...");
                return AdvancedCircosTemplateGenerator.generateTemplate(currentSpecies);
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusLabel.setText(chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    File outputDir = get();
                    Window parentWindow = SwingUtilities.getWindowAncestor(SpeciesInfoPanel.this);
                    AdvancedCircosLaunchDialog launchDialog =
                        new AdvancedCircosLaunchDialog(
                            parentWindow,
                            outputDir,
                            currentSpecies,
                            speciesManager
                        );
                    launchDialog.setVisible(true);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(SpeciesInfoPanel.this,
                        "Failed to prepare Advanced Circos template:\n" + e.getMessage(),
                        "Circos Error",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private GenomeData ensureGenomeDataWithChromosomeStats() throws IOException {
        GenomeData genomeData = currentSpecies.getGenomeData();
        if (genomeData != null && !genomeData.getChromosomeStats().isEmpty()) {
            return genomeData;
        }

        File genomeFile = currentSpecies.getGenomeFile();
        if (genomeFile == null || !genomeFile.exists()) {
            throw new IOException("Genome file is missing.");
        }

        File annotationFile = currentSpecies.getAnnotationFile();
        genomeData = GenomeStatsCalculator.calculateGenomeStats(genomeFile, annotationFile);
        currentSpecies.setGenomeData(genomeData);

        File statsFile = currentSpecies.getStatsFile();
        if (statsFile != null) {
            genomeData.saveToFile(statsFile);
        }
        if (speciesManager != null) {
            speciesManager.updateSpecies(currentSpecies);
        }

        if (genomeData.getChromosomeStats().isEmpty()) {
            throw new IOException("No chromosome statistics were generated from stat.txt or genome data.");
        }
        return genomeData;
    }

    /**
     * Show Gene Info. menu using GeneInfoMenuManager
     */
    private void showGeneInfoMenu() {
        // Create GeneInfoMenuManager with current context
        GeneInfoMenuManager menuManager = new GeneInfoMenuManager(
            SwingUtilities.getWindowAncestor(this),
            speciesManager,
            currentSpecies
        );

        // Show the menu positioned relative to the Gene Info button
        menuManager.showGeneInfoMenu(actionsPanel.getGeneViewerButton());
    }

    /**
     * Import functional annotations for the current species
     */
    private void importFunctionalAnnotations() {
        // Open annotation import dialog
        final AnnotationImportDialog dialog = new AnnotationImportDialog(
            SwingUtilities.getWindowAncestor(this), currentSpecies);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (!dialog.isImportSuccessful()) {
                    return;
                }

                if (speciesManager != null) {
                    speciesManager.updateSpecies(currentSpecies);
                }

                updateFileOperationButtons();
                JOptionPane.showMessageDialog(
                    SpeciesInfoPanel.this,
                    "Functional annotations imported successfully!\nThe Functional Analysis button is now available.",
                    "Import Complete",
                    JOptionPane.INFORMATION_MESSAGE
                );
            }
        });
        dialog.setVisible(true);
    }

    /**
     * Run automatic eggNOG-based functional annotation for the current species
     */
    private void autoFunctionalAnnotations() {
        AutoFunctionalAnnotationDialog dialog = new AutoFunctionalAnnotationDialog(
            SwingUtilities.getWindowAncestor(this), currentSpecies);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (speciesManager != null) {
                    speciesManager.updateSpecies(currentSpecies);
                }
                updateFileOperationButtons();
            }
        });
        dialog.setVisible(true);
    }
    
    /**
     * Open functional annotation management interface with type selection
     */
    private void manageFunctionalAnnotations() {
        if (currentSpecies.hasFunctionalAnnotations()) {
            // Now load the actual annotation data if not already loaded
            if (currentSpecies.getFunctionalAnnotations() == null) {
                // Show loading indicator
                Window parentWindow = SwingUtilities.getWindowAncestor(this);
                JDialog loadingDialog;
                if (parentWindow instanceof Frame) {
                    loadingDialog = new JDialog((Frame) parentWindow, "Loading Annotations", false);
                } else if (parentWindow instanceof Dialog) {
                    loadingDialog = new JDialog((Dialog) parentWindow, "Loading Annotations", false);
                } else {
                    loadingDialog = new JDialog((Frame) null, "Loading Annotations", false);
                }
                JProgressBar progressBar = new JProgressBar();
                progressBar.setIndeterminate(true);
                progressBar.setString("Loading functional annotations...");
                progressBar.setStringPainted(true);
                
                JPanel panel = new JPanel(new BorderLayout());
                panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
                panel.add(new JLabel("Loading annotation data for " + currentSpecies.getSpeciesName()), BorderLayout.NORTH);
                panel.add(progressBar, BorderLayout.CENTER);
                
                loadingDialog.add(panel);
                loadingDialog.setSize(400, 120);
                loadingDialog.setLocationRelativeTo(this);
                
                // Load annotations in background thread
                SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
                    @Override
                    protected Boolean doInBackground() throws Exception {
                        return currentSpecies.loadFunctionalAnnotations();
                    }
                    
                    @Override
                    protected void done() {
                        loadingDialog.dispose();
                        try {
                            boolean loaded = get();
                            if (loaded) {
                                // Show annotation type selection dialog
                                AnnotationTypeSelectionDialog selectionDialog = new AnnotationTypeSelectionDialog(
                                    SwingUtilities.getWindowAncestor(SpeciesInfoPanel.this), currentSpecies);
                                selectionDialog.setVisible(true);
                            } else {
                                JOptionPane.showMessageDialog(SpeciesInfoPanel.this,
                                    "Failed to load functional annotations.",
                                    "Loading Error",
                                    JOptionPane.ERROR_MESSAGE);
                            }
                        } catch (Exception e) {
                            JOptionPane.showMessageDialog(SpeciesInfoPanel.this,
                                "Error loading annotations: " + e.getMessage(),
                                "Loading Error",
                                JOptionPane.ERROR_MESSAGE);
                        }
                        
                        // Refresh buttons in case data was modified
                        updateFileOperationButtons();
                    }
                };
                
                worker.execute();
                loadingDialog.setVisible(true);
            } else {
                // Data already loaded, show dialog directly
                AnnotationTypeSelectionDialog selectionDialog = new AnnotationTypeSelectionDialog(
                    SwingUtilities.getWindowAncestor(this), currentSpecies);
                selectionDialog.setVisible(true);
                
                // Refresh buttons in case data was modified
                updateFileOperationButtons();
            }
        } else {
            JOptionPane.showMessageDialog(this,
                "No functional annotations available. Please import annotations first.",
                "No Annotation Data",
                JOptionPane.WARNING_MESSAGE);
        }
    }
    
    /**
     * Get annotation counts from files without loading the data
     * Uses metadata-driven system for accurate counts
     */
    private Map<AnnotationType, Integer> getAnnotationCountsFromFiles() {
        Map<AnnotationType, Integer> counts = new HashMap<>();
        
        if (currentSpecies == null) {
            // Initialize all counts to 0
            for (AnnotationType type : AnnotationType.values()) {
                counts.put(type, 0);
            }
            return counts;
        }
        
        // Check if annotations are already loaded in memory and have data
        GeneAnnotationData annotationData = currentSpecies.getFunctionalAnnotations();
        if (annotationData != null && annotationData.getAnnotatedGenes() > 0) {
            return annotationData.getAnnotationCounts();
        }
        
        // Use metadata-driven system to get accurate counts
        boolean foundAnyMetadata = false;
        for (AnnotationType type : AnnotationType.values()) {
            AnnotationMetadata metadata = currentSpecies.getAnnotationMetadata(type);
            
            if (metadata != null) {
                // Use accurate count from metadata
                counts.put(type, metadata.getUniqueGenes());
                foundAnyMetadata = true;
            } else {
                // Check type-specific directory structure
                File typeDir = currentSpecies.getAnnotationTypeDirectory(type);
                if (typeDir != null && typeDir.exists()) {
                    File dataFile = new File(typeDir, type.getShortName() + ".tsv");
                    if (dataFile.exists() && dataFile.length() > 0) {
                        // File exists but no metadata - generate on demand
                        logger.info("Generating metadata for count check: " + type.getShortName());
                        AnnotationMetadata newMetadata = AnnotationMetadata.analyzeFile(dataFile, type);
                        currentSpecies.setAnnotationMetadata(type, newMetadata);
                        counts.put(type, newMetadata.getUniqueGenes());
                        foundAnyMetadata = true;
                    } else {
                        counts.put(type, 0);
                    }
                } else {
                    // Fallback: check legacy flat structure
                    File functionalAnnotationDir = currentSpecies.getFunctionalAnnotationDir();
                    if (functionalAnnotationDir != null && functionalAnnotationDir.exists()) {
                        File legacyFile = new File(functionalAnnotationDir, type.getShortName() + ".tsv");
                        if (legacyFile.exists() && legacyFile.length() > 0) {
                            counts.put(type, 1); // Simplified count for legacy files
                        } else {
                            counts.put(type, 0);
                        }
                    } else {
                        counts.put(type, 0);
                    }
                }
            }
        }
        
        // If we didn't find any metadata and no files, but species claims to have annotations,
        // try to trigger metadata loading
        if (!foundAnyMetadata && currentSpecies.hasFunctionalAnnotations()) {
            boolean loaded = currentSpecies.loadFunctionalAnnotations();
            if (loaded) {
                // Recursively call this method to get the counts now that metadata should be available
                return getAnnotationCountsFromFiles();
            }
        }
        
        return counts;
    }
    
    /**
     * Delete all annotations
     */
    private void deleteAllAnnotations() {
        if (currentSpecies == null) {
            return;
        }
        
        // Get annotation counts from files if data is not loaded in memory
        Map<AnnotationType, Integer> counts = getAnnotationCountsFromFiles();
        int totalCount = counts.values().stream().mapToInt(Integer::intValue).sum();
        
        if (totalCount == 0) {
            JOptionPane.showMessageDialog(this,
                "No annotation files found to delete.",
                "Nothing to Delete", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        String message = String.format(
            "Are you sure you want to delete ALL functional annotations?\n\n" +
            "This will permanently remove ALL %d annotations and cannot be undone.\n\n" +
            "This includes:\n" +
            "- GO annotations: %d\n" +
            "- KEGG annotations: %d\n" +
            "- InterPro annotations: %d\n" +
            "- Pfam annotations: %d\n" +
            "- Custom annotations: %d\n\n" +
            "All annotation files will be deleted from the file system.",
            totalCount,
            counts.getOrDefault(AnnotationType.GO, 0),
            counts.getOrDefault(AnnotationType.KEGG, 0),
            counts.getOrDefault(AnnotationType.INTERPRO, 0),
            counts.getOrDefault(AnnotationType.PFAM, 0),
            counts.getOrDefault(AnnotationType.CUSTOM, 0)
        );
        
        int result = JOptionPane.showConfirmDialog(this, message,
            "Confirm Delete ALL Annotations",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            
        if (result == JOptionPane.YES_OPTION) {
            try {
                // Delete all annotation files directly
                int deletedFiles = 0;
                for (AnnotationType type : AnnotationType.values()) {
                    if (counts.getOrDefault(type, 0) > 0) {
                        if (deleteAnnotationFile(type)) {
                            deletedFiles++;
                        }
                    }
                }
                
                // If annotations are loaded in memory, also clear them
                GeneAnnotationData annotationData = currentSpecies.getFunctionalAnnotations();
                if (annotationData != null) {
                    annotationData.clearAllAnnotations();
                    // Don't save here since we already deleted files
                }
                
                if (deletedFiles > 0) {
                    
                    // Clear the functional annotations from species
                    currentSpecies.setFunctionalAnnotations(null);
                    
                    // Update buttons
                    updateFileOperationButtons();
                    
                    logger.info("Deleted all annotation files: " + deletedFiles + " files");
                    
                    JOptionPane.showMessageDialog(this,
                        "Successfully deleted all annotations (" + deletedFiles + " files).",
                        "Deletion Complete", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this,
                        "No annotations found to delete.",
                        "Nothing to Delete", JOptionPane.INFORMATION_MESSAGE);
                }
                
            } catch (Exception e) {
                logger.warning("Error deleting all annotations: " + e.getMessage());
                JOptionPane.showMessageDialog(this,
                    "Error deleting annotations: " + e.getMessage(),
                    "Delete Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Delete the physical annotation file for a type
     */
    private boolean deleteAnnotationFile(AnnotationType type) {
        if (currentSpecies == null || currentSpecies.getFunctionalAnnotationDir() == null) {
            return false;
        }
        
        try {
            File annotationFile = new File(currentSpecies.getFunctionalAnnotationDir(), 
                                         type.getShortName() + ".tsv");
            
            if (annotationFile.exists()) {
                if (annotationFile.delete()) {
                    logger.info("Deleted annotation file: " + annotationFile.getAbsolutePath());
                    return true;
                } else {
                    logger.warning("Failed to delete annotation file: " + annotationFile.getAbsolutePath());
                    return false;
                }
            } else {
                logger.info("Annotation file does not exist: " + annotationFile.getAbsolutePath());
                return true; // Consider non-existent file as successfully "deleted"
            }
        } catch (Exception e) {
            logger.warning("Error deleting annotation file: " + e.getMessage());
            return false;
        }
    }
}
