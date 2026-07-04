/*
 * Species Tree Panel
 */
package simplegenomehub.gui;

import simplegenomehub.blast.BlastConfig;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;
import simplegenomehub.util.fileio.DemoGeneSetGenerator;
import simplegenomehub.util.fileio.DualSyntenyPlotLauncher;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;

/**
 * Tree view panel for displaying species in hierarchical format
 * Supports selection, context menu, and drag-drop operations
 * 
 * @author SimpleGenomeHub
 */
public class SpeciesTreePanel extends JPanel {

    private static final SimpleDateFormat NODE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    
    private SpeciesManager speciesManager;
    private JTree speciesTree;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private List<SelectionListener> selectionListeners;
    private List<SpeciesInfo> filteredSpecies; // For search results
    private Runnable refreshAction;
    private Runnable configurationAction;
    private Runnable importGenomeAction;
    
    /**
     * Selection listener interface
     */
    public interface SelectionListener {
        void onSpeciesSelected(SpeciesInfo species);
    }

    public enum SelectionKind {
        NONE,
        ADVANCE_CIRCOS_RESULT,
        GENOME_COMPARE_RESULT,
        MULTIPLE_SYNTENY_RESULT
    }

    public static final class SelectionContext {
        private final SpeciesInfo species;
        private final File selectedFile;
        private final SelectionKind selectionKind;

        private SelectionContext(SpeciesInfo species, File selectedFile, SelectionKind selectionKind) {
            this.species = species;
            this.selectedFile = selectedFile;
            this.selectionKind = selectionKind == null ? SelectionKind.NONE : selectionKind;
        }

        public SpeciesInfo getSpecies() {
            return species;
        }

        public File getSelectedFile() {
            return selectedFile;
        }

        public SelectionKind getSelectionKind() {
            return selectionKind;
        }
    }
    
    /**
     * Constructor
     */
    public SpeciesTreePanel(SpeciesManager speciesManager) {
        this.speciesManager = speciesManager;
        this.selectionListeners = new ArrayList<>();
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        buildTree();
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        rootNode = new DefaultMutableTreeNode("Species Collection");
        treeModel = new DefaultTreeModel(rootNode);
        speciesTree = new JTree(treeModel);
        
        // Configure tree appearance
        speciesTree.setRootVisible(true);
        speciesTree.setShowsRootHandles(true);
        speciesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        speciesTree.setRowHeight(24);
        speciesTree.setBackground(Color.WHITE);
        speciesTree.setBorder(SimpleGenomeHubUi.createInnerPadding(6, 6, 6, 6));
        
        // Set custom cell renderer
        speciesTree.setCellRenderer(new SpeciesTreeCellRenderer());
        
        // Expand root by default
        speciesTree.expandRow(0);
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setOpaque(false);
        setLayout(new BorderLayout());

        SimpleGenomeHubUi.RoundedPanel cardPanel = new SimpleGenomeHubUi.RoundedPanel(
            new BorderLayout(0, 0),
            new Color(233, 242, 252),
            new Color(202, 218, 236),
            28
        );
        cardPanel.setBorder(SimpleGenomeHubUi.createInnerPadding(14, 14, 14, 14));

        JPanel headerPanel = new JPanel(new BorderLayout(0, 10));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(SimpleGenomeHubUi.createInnerPadding(2, 4, 12, 4));

        JLabel titleLabel = new JLabel("Species Collection");
        titleLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_18);
        titleLabel.setForeground(SimpleGenomeHubUi.TITLE_BLUE);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JButton configButton = SimpleGenomeHubUi.createSoftButton("Config", new Dimension(96, 34));
        configButton.addActionListener(e -> triggerConfiguration());

        JButton importGenomeButton = SimpleGenomeHubUi.createSoftButton("Import Genome", new Dimension(172, 34));
        importGenomeButton.addActionListener(e -> triggerImportGenome());

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(configButton);
        actionPanel.add(importGenomeButton);
        headerPanel.add(actionPanel, BorderLayout.SOUTH);

        cardPanel.add(headerPanel, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(speciesTree);
        scrollPane.setPreferredSize(new Dimension(280, 400));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(224, 233, 244)));
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setOpaque(false);
        ModernScrollBarStyle.applyTo(scrollPane);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(true);
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 230, 242)),
            SimpleGenomeHubUi.createInnerPadding(12, 12, 12, 12)
        ));
        contentPanel.add(scrollPane, BorderLayout.CENTER);

        cardPanel.add(contentPanel, BorderLayout.CENTER);

        JPanel toolPanel = createToolPanel();
        toolPanel.setBorder(SimpleGenomeHubUi.createInnerPadding(12, 0, 0, 0));
        cardPanel.add(toolPanel, BorderLayout.SOUTH);

        add(cardPanel, BorderLayout.CENTER);
    }
    
    /**
     * Create tool panel with action buttons
     */
    private JPanel createToolPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 8, 0));
        panel.setOpaque(false);

        Dimension buttonSize = new Dimension(72, 34);
        JButton refreshButton = SimpleGenomeHubUi.createSoftButton("Refresh", buttonSize);
        JButton editButton = SimpleGenomeHubUi.createSoftButton("Edit", buttonSize);
        JButton deleteButton = SimpleGenomeHubUi.createSoftButton("Delete", buttonSize);
        JButton exportButton = SimpleGenomeHubUi.createSoftButton("Export", buttonSize);

        refreshButton.addActionListener(e -> triggerRefresh());
        editButton.addActionListener(e -> editSelectedSpecies());
        deleteButton.addActionListener(e -> deleteSelectedSpecies());
        exportButton.addActionListener(e -> exportSelectedSpecies());

        panel.add(refreshButton);
        panel.add(editButton);
        panel.add(deleteButton);
        panel.add(exportButton);

        return panel;
    }

    public void setRefreshAction(Runnable refreshAction) {
        this.refreshAction = refreshAction;
    }

    public void setConfigurationAction(Runnable configurationAction) {
        this.configurationAction = configurationAction;
    }

    public void setImportGenomeAction(Runnable importGenomeAction) {
        this.importGenomeAction = importGenomeAction;
    }

    private void triggerRefresh() {
        if (refreshAction != null) {
            refreshAction.run();
            return;
        }
        refreshTree();
    }

    private void triggerConfiguration() {
        if (configurationAction != null) {
            configurationAction.run();
        }
    }

    private void triggerImportGenome() {
        if (importGenomeAction != null) {
            importGenomeAction.run();
        }
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        // Tree selection listener
        speciesTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) speciesTree.getLastSelectedPathComponent();
            SpeciesInfo species = getSpeciesFromNode(node);
            if (species != null) {
                notifySelectionListeners(species);
            } else {
                notifySelectionListeners(null);
            }
        });
        
        // Double-click listener
        speciesTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showContextMenu(e);
                }
            }
        });
    }
    
    /**
     * Build the species tree
     */
    private void buildTree() {
        rootNode.removeAllChildren();
        
        // Use filtered species if available, otherwise use all species
        List<SpeciesInfo> allSpecies = filteredSpecies != null ? filteredSpecies : speciesManager.getAllSpecies();
        
        // Group species by name for better organization
        java.util.Map<String, java.util.List<SpeciesInfo>> speciesGroups = new java.util.HashMap<>();
        
        for (SpeciesInfo species : allSpecies) {
            speciesGroups.computeIfAbsent(species.getSpeciesName(), k -> new ArrayList<>()).add(species);
        }
        
        // Add grouped species to tree
        for (java.util.Map.Entry<String, java.util.List<SpeciesInfo>> entry : speciesGroups.entrySet()) {
            String speciesName = entry.getKey();
            java.util.List<SpeciesInfo> versions = entry.getValue();
            
            if (versions.size() == 1) {
                // Single version - add directly
                SpeciesTreeNode speciesNode = new SpeciesTreeNode(versions.get(0));
                addSpeciesChildren(speciesNode, versions.get(0));
                rootNode.add(speciesNode);
            } else {
                // Multiple versions - create group node
                DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(speciesName + " (" + versions.size() + " versions)");
                
                // Sort versions
                versions.sort((s1, s2) -> s1.getVersion().compareToIgnoreCase(s2.getVersion()));
                
                for (SpeciesInfo species : versions) {
                    SpeciesTreeNode versionNode = new SpeciesTreeNode(species);
                    addSpeciesChildren(versionNode, species);
                    groupNode.add(versionNode);
                }
                
                rootNode.add(groupNode);
            }
        }
        
        treeModel.reload();

        // Update root node display
        String rootTitle = filteredSpecies != null ? 
            "Search Results (" + allSpecies.size() + " species)" :
            "Species Collection (" + allSpecies.size() + " species)";
        rootNode.setUserObject(rootTitle);
        treeModel.nodeChanged(rootNode);

        speciesTree.expandRow(0);
    }
    
    /**
     * Refresh the tree
     */
    public void refreshTree() {
        buildTree();
    }
    
    /**
     * Set filtered species for search results
     */
    public void setFilteredSpecies(List<SpeciesInfo> filteredSpecies) {
        this.filteredSpecies = filteredSpecies;
        buildTree();
    }
    
    /**
     * Clear search filter and show all species
     */
    public void clearFilter() {
        this.filteredSpecies = null;
        buildTree();
    }
    
    /**
     * Add selection listener
     */
    public void addSelectionListener(SelectionListener listener) {
        selectionListeners.add(listener);
    }
    
    /**
     * Remove selection listener
     */
    public void removeSelectionListener(SelectionListener listener) {
        selectionListeners.remove(listener);
    }
    
    /**
     * Notify selection listeners
     */
    private void notifySelectionListeners(SpeciesInfo species) {
        for (SelectionListener listener : selectionListeners) {
            listener.onSpeciesSelected(species);
        }
    }
    
    /**
     * Get currently selected species
     */
    public SpeciesInfo getSelectedSpecies() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) speciesTree.getLastSelectedPathComponent();
        return getSpeciesFromNode(node);
    }

    public SelectionContext getCurrentSelectionContext() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) speciesTree.getLastSelectedPathComponent();
        return buildSelectionContext(node);
    }
    
    /**
     * Edit selected species
     */
    private void editSelectedSpecies() {
        SpeciesInfo selected = getSelectedSpecies();
        if (selected != null) {
            SpeciesEditDialog dialog = new SpeciesEditDialog(
                SwingUtilities.getWindowAncestor(this), selected, speciesManager);
            dialog.setVisible(true);
        }
    }
    
    /**
     * Delete selected species
     */
    private void deleteSelectedSpecies() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) speciesTree.getLastSelectedPathComponent();
        if (selectedNode == null) {
            JOptionPane.showMessageDialog(this,
                "Please select a species, folder, or file to delete.",
                "Nothing Selected",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        Object userObject = selectedNode.getUserObject();
        if (userObject instanceof TreeFileNodeData) {
            deleteSelectedFileNode((TreeFileNodeData) userObject);
            return;
        }
        if (userObject instanceof TreeSectionNodeData) {
            deleteSelectedSectionNode((TreeSectionNodeData) userObject);
            return;
        }

        SpeciesInfo selected = getSpeciesFromNode(selectedNode);
        if (selected != null) {
            int result = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete species: " + selected.getSpeciesDirectoryName() + "?\n" +
                "This will permanently delete all associated files.",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
                
            if (result == JOptionPane.YES_OPTION) {
                boolean success = speciesManager.removeSpeciesWithoutConfirmation(
                    selected.getSpeciesName(), selected.getVersion());
                if (!success) {
                    JOptionPane.showMessageDialog(this,
                        "Failed to delete species. Some files may still exist.",
                        "Deletion Failed",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
            return;
        }

        JOptionPane.showMessageDialog(this,
            "The selected node cannot be deleted.",
            "Delete Not Available",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Export selected species data
     */
    private void exportSelectedSpecies() {
        SpeciesInfo selected = getSelectedSpecies();
        if (selected != null) {
            ExportDialog dialog = new ExportDialog(SwingUtilities.getWindowAncestor(this), selected);
            dialog.setVisible(true);
        }
    }

    private void openSelectedSpeciesDirectory() {
        SpeciesInfo selected = getSelectedSpecies();
        if (selected != null && selected.getSpeciesDir() != null) {
            try {
                Desktop.getDesktop().open(selected.getSpeciesDir());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "Failed to open directory:\n" + ex.getMessage(),
                    "Open Directory Failed",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Show context menu
     */
    private void showContextMenu(MouseEvent e) {
        TreePath path = speciesTree.getPathForLocation(e.getX(), e.getY());
        if (path != null) {
            speciesTree.setSelectionPath(path);
            
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Object userObject = node.getUserObject();
            if (userObject instanceof TreeFileNodeData) {
                TreeFileNodeData data = (TreeFileNodeData) userObject;

                JPopupMenu contextMenu = new JPopupMenu();
                if (data.isGeneSetFile()) {
                    boolean isRegionSet =
                        GeneSetFileSupport.detectSetKind(data.getFile()) == GeneSetFileSupport.SetKind.REGION;
                    JMenuItem openFilePathItem = new JMenuItem("Open File Path");
                    JMenuItem editItem = new JMenuItem("Edit");
                    JMenuItem runRegionSequenceBlastItem = new JMenuItem("Run Region Sequence BLAST");
                    JMenuItem runTranscriptBlastItem = new JMenuItem("Run Transcript BLAST");
                    JMenuItem runCdsBlastItem = new JMenuItem("Run CDS BLAST");
                    JMenuItem runProteinBlastItem = new JMenuItem("Run Protein BLAST");
                    JMenuItem deleteItem = new JMenuItem("Delete");

                    openFilePathItem.addActionListener(ev -> openTreeNodeDirectory(data));
                    editItem.addActionListener(ev -> editGeneSetFile(node, data));
                    runRegionSequenceBlastItem.addActionListener(ev -> runRegionSequenceBlast(node, data));
                    runTranscriptBlastItem.addActionListener(ev ->
                        runSetBlast(node, data, BlastConfig.SequenceType.TRANSCRIPT));
                    runCdsBlastItem.addActionListener(ev ->
                        runSetBlast(node, data, BlastConfig.SequenceType.CDS));
                    runProteinBlastItem.addActionListener(ev ->
                        runSetBlast(node, data, BlastConfig.SequenceType.PROTEIN));
                    deleteItem.addActionListener(ev -> deleteSelectedSpecies());

                    contextMenu.add(openFilePathItem);
                    contextMenu.add(editItem);
                    if (isRegionSet) {
                        contextMenu.add(runRegionSequenceBlastItem);
                    }
                    contextMenu.add(runTranscriptBlastItem);
                    contextMenu.add(runCdsBlastItem);
                    contextMenu.add(runProteinBlastItem);
                    contextMenu.addSeparator();
                    contextMenu.add(deleteItem);
                    SimpleGenomeHubUi.stylePopupMenu(contextMenu);
                    contextMenu.show(speciesTree, e.getX(), e.getY());
                    return;
                }

                JMenuItem openDirectoryItem = new JMenuItem("Open Directory");
                if (data.isAdvanceCircosProject()) {
                    JMenuItem openWithAdvanceCircosItem = new JMenuItem("Open with AdvanceCircos");
                    openWithAdvanceCircosItem.addActionListener(ev -> openWithAdvanceCircos(data));
                    contextMenu.add(openWithAdvanceCircosItem);
                }
                if (data.isMultipleSyntenyProject()) {
                    JMenuItem openWithMultipleSyntenyItem = new JMenuItem("Open with Multiple Synteny Viewer");
                    openWithMultipleSyntenyItem.addActionListener(ev -> openWithMultipleSyntenyViewer(data));
                    contextMenu.add(openWithMultipleSyntenyItem);
                }
                if (data.isGenomeCompareProject()) {
                    JMenuItem openWithDualSyntenyPlotItem = new JMenuItem("Open with Dual Synteny Plot");
                    openWithDualSyntenyPlotItem.addActionListener(ev -> openWithDualSyntenyPlot(data));
                    contextMenu.add(openWithDualSyntenyPlotItem);
                }
                JMenuItem deleteItem = new JMenuItem(data.isDirectory() ? "Delete Folder" : "Delete File");
                openDirectoryItem.addActionListener(ev -> openTreeNodeDirectory(data));
                if (contextMenu.getComponentCount() > 0) {
                    contextMenu.addSeparator();
                }
                contextMenu.add(openDirectoryItem);
                contextMenu.addSeparator();
                deleteItem.addActionListener(ev -> deleteSelectedSpecies());
                contextMenu.add(deleteItem);
                SimpleGenomeHubUi.stylePopupMenu(contextMenu);
                contextMenu.show(speciesTree, e.getX(), e.getY());
            } else if (userObject instanceof TreeSectionNodeData) {
                TreeSectionNodeData data = (TreeSectionNodeData) userObject;

                JPopupMenu contextMenu = new JPopupMenu();
                JMenuItem openDirectoryItem = new JMenuItem("Open Directory");
                JMenuItem deleteItem = new JMenuItem("Delete Folder");
                openDirectoryItem.addActionListener(ev -> openTreeSectionDirectory(data));
                deleteItem.addActionListener(ev -> deleteSelectedSpecies());
                contextMenu.add(openDirectoryItem);
                contextMenu.addSeparator();
                contextMenu.add(deleteItem);
                openDirectoryItem.setEnabled(data.getDirectory() != null && data.getDirectory().exists());
                if (data.getDirectory() == null) {
                    deleteItem.setEnabled(false);
                }
                SimpleGenomeHubUi.stylePopupMenu(contextMenu);
                contextMenu.show(speciesTree, e.getX(), e.getY());
            } else if (getSpeciesFromNode(node) != null) {
                
                JPopupMenu contextMenu = new JPopupMenu();
                
                JMenuItem editItem = new JMenuItem("Edit Species");
                JMenuItem deleteItem = new JMenuItem("Delete Species");
                JMenuItem exportItem = new JMenuItem("Export Data");
                JMenuItem openDirectoryItem = new JMenuItem("Open Directory");
                JMenuItem viewStatsItem = new JMenuItem("View Statistics");
                JMenuItem demoGeneSetItem = new JMenuItem("Random Gene Set");
                
                editItem.addActionListener(ev -> editSelectedSpecies());
                deleteItem.addActionListener(ev -> deleteSelectedSpecies());
                exportItem.addActionListener(ev -> exportSelectedSpecies());
                openDirectoryItem.addActionListener(ev -> openSelectedSpeciesDirectory());
                viewStatsItem.addActionListener(ev -> viewSpeciesStatistics());
                demoGeneSetItem.addActionListener(ev -> generateRandomGeneSetForNode(node));
                
                contextMenu.add(editItem);
                contextMenu.add(deleteItem);
                contextMenu.addSeparator();
                contextMenu.add(demoGeneSetItem);
                contextMenu.add(exportItem);
                contextMenu.add(openDirectoryItem);
                contextMenu.add(viewStatsItem);

                SimpleGenomeHubUi.stylePopupMenu(contextMenu);
                
                contextMenu.show(speciesTree, e.getX(), e.getY());
            }
        }
    }

    private void generateRandomGeneSetForNode(DefaultMutableTreeNode node) {
        SpeciesInfo species = getSpeciesFromNode(node);
        if (species == null) {
            JOptionPane.showMessageDialog(this,
                "Unable to resolve the selected GeneSet folder to a species.",
                "Random Gene Set Failed",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            DemoGeneSetGenerator.Result result = DemoGeneSetGenerator.generateDemoGeneSet(species);
            speciesManager.updateSpecies(species);
            refreshTree();
            selectSpecies(species);
            notifySelectionListeners(species);
            JOptionPane.showMessageDialog(this,
                "Random Gene Set created successfully:\n"
                    + result.getOutputFile().getName() + "\n"
                    + "Representative transcripts: " + result.getSelectedTranscriptCount(),
                "Random Gene Set Created",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to create Random Gene Set:\n" + ex.getMessage(),
                "Random Gene Set Failed",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editGeneSetFile(DefaultMutableTreeNode node, TreeFileNodeData data) {
        if (data == null || data.getFile() == null || !data.getFile().exists()) {
            JOptionPane.showMessageDialog(this,
                "The selected file no longer exists.",
                "Edit Failed",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        SpeciesInfo species = getSpeciesFromNode(node);
        if (species == null) {
            JOptionPane.showMessageDialog(this,
                "Unable to resolve the selected gene set to a species.",
                "Edit Failed",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(this);
        if (GeneSetFileSupport.detectSetKind(data.getFile()) == GeneSetFileSupport.SetKind.REGION) {
            EditRegionSetDialog dialog = new EditRegionSetDialog(
                owner,
                species,
                data.getFile(),
                () -> speciesManager.updateSpecies(species));
            dialog.setVisible(true);
        } else {
            EditGeneSetDialog dialog = new EditGeneSetDialog(
                owner,
                species,
                data.getFile(),
                () -> speciesManager.updateSpecies(species));
            dialog.setVisible(true);
        }
    }

    private void runSetBlast(DefaultMutableTreeNode node, TreeFileNodeData data,
                             BlastConfig.SequenceType sequenceType) {
        if (data == null || data.getFile() == null || !data.getFile().exists()) {
            JOptionPane.showMessageDialog(this,
                "The selected set file no longer exists.",
                "Run BLAST Failed",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        SpeciesInfo species = getSpeciesFromNode(node);
        if (species == null) {
            JOptionPane.showMessageDialog(this,
                "Unable to resolve the selected set to a species.",
                "Run BLAST Failed",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (GeneSetFileSupport.detectSetKind(data.getFile()) == GeneSetFileSupport.SetKind.REGION) {
            RegionSetBlastLauncher.launchGeneOverlapBlast(this, speciesManager, species, data.getFile(), sequenceType);
        } else {
            GeneSetBlastLauncher.launch(this, speciesManager, species, data.getFile(), sequenceType);
        }
    }

    private void runRegionSequenceBlast(DefaultMutableTreeNode node, TreeFileNodeData data) {
        if (data == null || data.getFile() == null || !data.getFile().exists()) {
            JOptionPane.showMessageDialog(this,
                "The selected region set file no longer exists.",
                "Run BLAST Failed",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        SpeciesInfo species = getSpeciesFromNode(node);
        if (species == null) {
            JOptionPane.showMessageDialog(this,
                "Unable to resolve the selected region set to a species.",
                "Run BLAST Failed",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        RegionSetBlastLauncher.launchRegionSequenceBlast(this, speciesManager, species, data.getFile());
    }

    private void openTreeNodeDirectory(TreeFileNodeData data) {
        if (data == null || data.getFile() == null) {
            return;
        }

        File target = data.isDirectory() ? data.getFile() : data.getFile().getParentFile();
        openDirectory(target);
    }

    private void openTreeSectionDirectory(TreeSectionNodeData data) {
        if (data == null) {
            return;
        }
        openDirectory(data.getDirectory());
    }

    private void openDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            JOptionPane.showMessageDialog(this,
                "Directory not found.",
                "Open Directory Failed",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Desktop.getDesktop().open(directory);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to open directory:\n" + ex.getMessage(),
                "Open Directory Failed",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openWithAdvanceCircos(TreeFileNodeData data) {
        if (data == null || data.getFile() == null) {
            return;
        }

        try {
            AdvancedCircosLaunchDialog.openProjectDirectly(data.getFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Failed to open Advanced Circos project:\n" + ex.getMessage(),
                "Advanced Circos Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openWithDualSyntenyPlot(TreeFileNodeData data) {
        if (data == null || data.getFile() == null) {
            return;
        }

        int reorderChoice = JOptionPane.showConfirmDialog(this,
            "Display Dual Synteny Plot with reordered chromosome order?\n" +
                "Choose Yes to use the chromosome reordering txt in this result folder,\n" +
                "or create it automatically if it does not exist.",
            "Dual Synteny Plot",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        if (reorderChoice == JOptionPane.CLOSED_OPTION) {
            return;
        }
        boolean reorderChromosomes = reorderChoice == JOptionPane.YES_OPTION;

        try {
            DualSyntenyPlotLauncher.launchFromOutputDirectory(data.getFile(), reorderChromosomes);
        } catch (Exception ex) {
            showDetailedErrorDialog(
                "Dual Synteny Plot Error",
                "Failed to open Dual Synteny Plot.",
                ex
            );
        }
    }

    private void openWithMultipleSyntenyViewer(TreeFileNodeData data) {
        if (data == null || data.getFile() == null) {
            return;
        }

        try {
            MultipleSyntenyResultViewerDialog dialog = new MultipleSyntenyResultViewerDialog(
                SwingUtilities.getWindowAncestor(this),
                data.getFile()
            );
            dialog.setVisible(true);
        } catch (Exception ex) {
            showDetailedErrorDialog(
                "Multiple Synteny Viewer Error",
                "Failed to open Multiple Synteny result viewer.",
                ex
            );
        }
    }

    private void showDetailedErrorDialog(String title, String summary, Throwable throwable) {
        JTextArea detailsArea = new JTextArea(22, 88);
        detailsArea.setEditable(false);
        detailsArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        detailsArea.setText(buildThrowableDetails(summary, throwable));
        detailsArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(detailsArea);
        scrollPane.setPreferredSize(new Dimension(980, 520));

        JOptionPane.showMessageDialog(
            this,
            scrollPane,
            title,
            JOptionPane.ERROR_MESSAGE
        );
    }

    private String buildThrowableDetails(String summary, Throwable throwable) {
        StringBuilder details = new StringBuilder();
        if (summary != null && !summary.trim().isEmpty()) {
            details.append(summary.trim()).append("\n\n");
        }

        if (throwable == null) {
            details.append("No exception details were provided.");
            return details.toString();
        }

        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "(no detail message)";
        }

        details.append("Exception: ")
            .append(throwable.getClass().getName())
            .append("\nMessage: ")
            .append(message)
            .append("\n\nStack trace:\n");

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        printWriter.flush();
        details.append(stringWriter);

        return details.toString();
    }

    /**
     * View species statistics
     */
    private void viewSpeciesStatistics() {
        SpeciesInfo selected = getSelectedSpecies();
        if (selected != null && selected.getGenomeData() != null) {
            String stats = selected.getGenomeData().getSummaryStats();
            JOptionPane.showMessageDialog(this,
                "Statistics for " + selected.getSpeciesDirectoryName() + ":\n\n" + stats,
                "Species Statistics",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private SpeciesInfo getSpeciesFromNode(DefaultMutableTreeNode node) {
        if (node == null) {
            return null;
        }

        if (node.getUserObject() instanceof SpeciesInfo) {
            return (SpeciesInfo) node.getUserObject();
        }

        TreeNode[] pathNodes = node.getPath();
        for (int i = pathNodes.length - 1; i >= 0; i--) {
            Object pathObject = ((DefaultMutableTreeNode) pathNodes[i]).getUserObject();
            if (pathObject instanceof SpeciesInfo) {
                return (SpeciesInfo) pathObject;
            }
        }
        return null;
    }

    private SelectionContext buildSelectionContext(DefaultMutableTreeNode node) {
        SpeciesInfo species = getSpeciesFromNode(node);
        if (node == null) {
            return new SelectionContext(species, null, SelectionKind.NONE);
        }

        Object userObject = node.getUserObject();
        if (userObject instanceof TreeFileNodeData) {
            TreeFileNodeData data = (TreeFileNodeData) userObject;
            if (data.isDirectory() && data.isAdvanceCircosProject()) {
                return new SelectionContext(species, data.getFile(), SelectionKind.ADVANCE_CIRCOS_RESULT);
            }
            if (data.isDirectory() && data.isGenomeCompareProject()) {
                return new SelectionContext(species, data.getFile(), SelectionKind.GENOME_COMPARE_RESULT);
            }
            if (data.isDirectory() && data.isMultipleSyntenyProject()) {
                return new SelectionContext(species, data.getFile(), SelectionKind.MULTIPLE_SYNTENY_RESULT);
            }
        }

        return new SelectionContext(species, null, SelectionKind.NONE);
    }

    private void addSpeciesChildren(DefaultMutableTreeNode speciesNode, SpeciesInfo species) {
        addExpressionNodes(speciesNode, species);
        addGeneSetNodes(speciesNode, species);
        addFunctionalAnnotationNodes(speciesNode, species);
    }

    private void addExpressionNodes(DefaultMutableTreeNode speciesNode, SpeciesInfo species) {
        File expressionDir = species != null ? species.getExpressionDir() : null;
        if (expressionDir == null || !expressionDir.isDirectory()) {
            return;
        }

        File[] tsvFiles = expressionDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(".tsv"));
        if (tsvFiles == null || tsvFiles.length == 0) {
            return;
        }

        Arrays.sort(tsvFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        DefaultMutableTreeNode expressionNode = new DefaultMutableTreeNode(
            new TreeSectionNodeData("Expression", expressionDir));
        for (File tsvFile : tsvFiles) {
            expressionNode.add(new DefaultMutableTreeNode(TreeFileNodeData.forExpressionFile(tsvFile)));
        }
        speciesNode.add(expressionNode);
    }

    private void addGeneSetNodes(DefaultMutableTreeNode speciesNode, SpeciesInfo species) {
        File geneSetDir = species != null ? species.getGeneSetDir() : null;
        if (geneSetDir == null || !geneSetDir.isDirectory()) {
            return;
        }

        File[] geneSetFiles = geneSetDir.listFiles((dir, name) ->
            GeneSetFileSupport.isStandardSetFileName(name));
        if (geneSetFiles == null || geneSetFiles.length == 0) {
            return;
        }

        Arrays.sort(geneSetFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        DefaultMutableTreeNode geneSetNode = new DefaultMutableTreeNode(
            new TreeSectionNodeData("GeneSet", geneSetDir));
        for (File geneSetFile : geneSetFiles) {
            geneSetNode.add(new DefaultMutableTreeNode(TreeFileNodeData.forGeneSetFile(geneSetFile)));
        }
        speciesNode.add(geneSetNode);
    }

    private void addFunctionalAnnotationNodes(DefaultMutableTreeNode speciesNode, SpeciesInfo species) {
        File functionalDir = species != null ? species.getFunctionalAnnotationDir() : null;
        if (functionalDir == null || !functionalDir.isDirectory()) {
            return;
        }

        File[] subDirs = functionalDir.listFiles(File::isDirectory);
        if (subDirs == null || subDirs.length == 0) {
            return;
        }

        Arrays.sort(subDirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File subDir : subDirs) {
            if (!containsAnyFile(subDir)) {
                continue;
            }

            DefaultMutableTreeNode subDirNode = createFunctionalSubtree(subDir);
            if (subDirNode != null) {
                speciesNode.add(subDirNode);
            }
        }
    }

    private DefaultMutableTreeNode createFunctionalSubtree(File subDir) {
        if (subDir == null || !subDir.isDirectory()) {
            return null;
        }

        String dirName = subDir.getName();
        String lowerName = dirName.toLowerCase(Locale.ROOT);

        if ("advancecircos".equals(lowerName)
            || "genomecompare".equals(lowerName)
            || "multiplecompare".equals(lowerName)) {
            return createDateFolderCollectionNode(subDir);
        }
        if ("go".equals(lowerName)) {
            return createFilteredFileCollectionNode(subDir, ".obo");
        }
        if ("kegg".equals(lowerName)) {
            return createFilteredFileCollectionNode(subDir, ".tbtoolskeggbackend");
        }

        return new DefaultMutableTreeNode(TreeFileNodeData.forFunctionalFolder(subDir));
    }

    private DefaultMutableTreeNode createDateFolderCollectionNode(File directory) {
        File[] childDirs = directory.listFiles(File::isDirectory);
        if (childDirs == null || childDirs.length == 0) {
            return null;
        }

        Arrays.sort(childDirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        DefaultMutableTreeNode parentNode = new DefaultMutableTreeNode(
            new TreeSectionNodeData(directory.getName(), directory));
        boolean hasChildren = false;

        for (File childDir : childDirs) {
            if (!containsAnyFile(childDir)) {
                continue;
            }
            String directoryName = directory.getName().toLowerCase(Locale.ROOT);
            boolean isAdvanceCircos = "advancecircos".equals(directoryName);
            boolean isGenomeCompare = "genomecompare".equals(directoryName);
            boolean isMultipleSynteny = "multiplecompare".equals(directoryName);
            parentNode.add(new DefaultMutableTreeNode(
                TreeFileNodeData.forDatedFolder(childDir, isAdvanceCircos, isGenomeCompare, isMultipleSynteny)));
            hasChildren = true;
        }

        return hasChildren ? parentNode : null;
    }

    private DefaultMutableTreeNode createFilteredFileCollectionNode(File directory, String suffixLowerCase) {
        File[] files = directory.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).endsWith(suffixLowerCase));
        if (files == null || files.length == 0) {
            return null;
        }

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        DefaultMutableTreeNode parentNode = new DefaultMutableTreeNode(
            new TreeSectionNodeData(directory.getName(), directory));
        for (File file : files) {
            parentNode.add(new DefaultMutableTreeNode(TreeFileNodeData.forTimestampedFile(file)));
        }
        return parentNode;
    }

    private boolean containsAnyFile(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return false;
        }

        File[] children = directory.listFiles();
        if (children == null || children.length == 0) {
            return false;
        }

        for (File child : children) {
            if (child.isFile()) {
                return true;
            }
            if (child.isDirectory() && containsAnyFile(child)) {
                return true;
            }
        }
        return false;
    }

    private void deleteSelectedFileNode(TreeFileNodeData data) {
        if (data == null || data.getFile() == null) {
            JOptionPane.showMessageDialog(this,
                "The selected path is invalid.",
                "Delete Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        SpeciesInfo ownerSpecies = getSelectedSpecies();
        File file = data.getFile();
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this,
                "The selected path no longer exists:\n" + file.getAbsolutePath(),
                "Path Not Found",
                JOptionPane.WARNING_MESSAGE);
            refreshTree();
            return;
        }

        String itemType = data.isDirectory() ? "folder" : "file";
        String message = "Are you sure you want to delete this " + itemType + "?\n\n" +
            file.getAbsolutePath() + "\n\n" +
            "This action cannot be undone.";

        int result = JOptionPane.showConfirmDialog(this,
            message,
            "Confirm Delete " + capitalize(itemType),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        boolean success = deleteFileSystemPath(file);
        if (!success) {
            JOptionPane.showMessageDialog(this,
                "Failed to delete the selected " + itemType + ".\nPlease check whether the file is in use.",
                "Delete Failed",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        refreshTree();
        if (ownerSpecies != null) {
            selectSpecies(ownerSpecies);
            notifySelectionListeners(ownerSpecies);
        } else {
            notifySelectionListeners(null);
        }
    }

    private void deleteSelectedSectionNode(TreeSectionNodeData data) {
        if (data == null || data.getDirectory() == null) {
            JOptionPane.showMessageDialog(this,
                "The selected folder is invalid.",
                "Delete Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        deleteSelectedFileNode(TreeFileNodeData.forFunctionalFolder(data.getDirectory()));
    }

    private boolean deleteFileSystemPath(File target) {
        if (target == null || !target.exists()) {
            return true;
        }

        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteFileSystemPath(child)) {
                        return false;
                    }
                }
            }
        }

        try {
            return Files.deleteIfExists(target.toPath());
        } catch (IOException ex) {
            return false;
        }
    }

    private String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void selectSpecies(SpeciesInfo species) {
        if (species == null) {
            return;
        }

        DefaultMutableTreeNode foundNode = findSpeciesNode(rootNode, species);
        if (foundNode == null) {
            return;
        }

        TreePath path = new TreePath(foundNode.getPath());
        speciesTree.setSelectionPath(path);
        speciesTree.scrollPathToVisible(path);
    }

    private DefaultMutableTreeNode findSpeciesNode(DefaultMutableTreeNode currentNode, SpeciesInfo species) {
        if (currentNode == null || species == null) {
            return null;
        }

        Object userObject = currentNode.getUserObject();
        if (userObject instanceof SpeciesInfo && species.equals(userObject)) {
            return currentNode;
        }

        for (int i = 0; i < currentNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) currentNode.getChildAt(i);
            DefaultMutableTreeNode match = findSpeciesNode(child, species);
            if (match != null) {
                return match;
            }
        }
        return null;
    }
    
    /**
     * Custom tree node for species
     */
    private static class SpeciesTreeNode extends DefaultMutableTreeNode {
        public SpeciesTreeNode(SpeciesInfo species) {
            super(species);
        }
        
        @Override
        public String toString() {
            SpeciesInfo species = (SpeciesInfo) getUserObject();
            return species.getDisplayName();
        }
    }
    
    /**
     * Custom cell renderer for species tree
     */
    private static class SpeciesTreeCellRenderer extends DefaultTreeCellRenderer {
        
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            
            if (value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                
                if (node.getUserObject() instanceof SpeciesInfo) {
                    SpeciesInfo species = (SpeciesInfo) node.getUserObject();
                    
                    // Set icon based on completeness
                    if (species.isComplete()) {
                        setIcon(UIManager.getIcon("FileView.fileIcon"));
                    } else {
                        setIcon(UIManager.getIcon("OptionPane.warningIcon"));
                    }
                    
                    // Set tooltip
                    if (species.getGenomeData() != null) {
                        setToolTipText("<html>" + species.getGenomeData().getSummaryStats().replace(" | ", "<br>") + "</html>");
                    } else {
                        setToolTipText("Species data incomplete");
                    }
                } else if (node.getUserObject() instanceof TreeFileNodeData) {
                    TreeFileNodeData data = (TreeFileNodeData) node.getUserObject();
                    setText(data.getDisplayText());
                    setToolTipText(data.getFile() != null ? data.getFile().getAbsolutePath() : null);
                    if (data.isDirectory()) {
                        setIcon(UIManager.getIcon("FileView.directoryIcon"));
                    } else {
                        setIcon(UIManager.getIcon("FileView.fileIcon"));
                    }
                } else if (node.getUserObject() instanceof TreeSectionNodeData) {
                    TreeSectionNodeData data = (TreeSectionNodeData) node.getUserObject();
                    setText(data.toString());
                    setToolTipText(data.getDirectory() != null ? data.getDirectory().getAbsolutePath() : null);
                    setIcon(UIManager.getIcon("FileView.directoryIcon"));
                } else {
                    // Group node or root
                    setIcon(UIManager.getIcon("FileView.directoryIcon"));
                    setToolTipText(null);
                }
            }
            
            return this;
        }
    }

    private static final class TreeSectionNodeData {
        private final String label;
        private final File directory;

        private TreeSectionNodeData(String label, File directory) {
            this.label = label;
            this.directory = directory;
        }

        public File getDirectory() {
            return directory;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final class TreeFileNodeData {
        private final File file;
        private final boolean directory;
        private final String displayText;
        private final boolean advanceCircosProject;
        private final boolean genomeCompareProject;
        private final boolean multipleSyntenyProject;
        private final boolean geneSetFile;

        private TreeFileNodeData(File file, boolean directory, String displayText, boolean advanceCircosProject,
                                 boolean genomeCompareProject, boolean multipleSyntenyProject,
                                 boolean geneSetFile) {
            this.file = file;
            this.directory = directory;
            this.displayText = displayText;
            this.advanceCircosProject = advanceCircosProject;
            this.genomeCompareProject = genomeCompareProject;
            this.multipleSyntenyProject = multipleSyntenyProject;
            this.geneSetFile = geneSetFile;
        }

        public static TreeFileNodeData forExpressionFile(File file) {
            String display = file.getName() + "    " + NODE_TIME_FORMAT.format(new Date(file.lastModified()));
            return new TreeFileNodeData(file, false, display, false, false, false, false);
        }

        public static TreeFileNodeData forGeneSetFile(File file) {
            String display = GeneSetFileSupport.extractDisplayName(file)
                + "    " + NODE_TIME_FORMAT.format(new Date(file.lastModified()));
            return new TreeFileNodeData(file, false, display, false, false, false, true);
        }

        public static TreeFileNodeData forFunctionalFolder(File directory) {
            return new TreeFileNodeData(directory, true, directory.getName(), false, false, false, false);
        }

        public static TreeFileNodeData forDatedFolder(File directory, boolean advanceCircosProject,
                                                       boolean genomeCompareProject,
                                                       boolean multipleSyntenyProject) {
            return new TreeFileNodeData(directory, true, directory.getName(), advanceCircosProject,
                genomeCompareProject, multipleSyntenyProject, false);
        }

        public static TreeFileNodeData forTimestampedFile(File file) {
            String display = file.getName() + "    " + NODE_TIME_FORMAT.format(new Date(file.lastModified()));
            return new TreeFileNodeData(file, false, display, false, false, false, false);
        }

        public File getFile() {
            return file;
        }

        public boolean isDirectory() {
            return directory;
        }

        public String getDisplayText() {
            return displayText;
        }

        public boolean isAdvanceCircosProject() {
            return advanceCircosProject;
        }

        public boolean isGenomeCompareProject() {
            return genomeCompareProject;
        }

        public boolean isMultipleSyntenyProject() {
            return multipleSyntenyProject;
        }

        public boolean isGeneSetFile() {
            return geneSetFile;
        }

        @Override
        public String toString() {
            return displayText;
        }
    }
}
