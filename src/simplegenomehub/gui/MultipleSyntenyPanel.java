/*
 * SimpleGenomeHub Multiple Synteny Panel
 * Redesigned workspace for selecting genomes and preparing a multiple synteny layout.
 */
package simplegenomehub.gui;

import simplegenomehub.model.GenomeData;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenyAnchorMode;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenyGenomeModel;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenyGridSnapHelper;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenyHorizontalEdgeType;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenyLinkRouteMode;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenyLayoutCanvas;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenyLinkRoutingHelper;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenyLinkModel;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenyLinkType;
import simplegenomehub.util.MultipleSynteny.MultipleSyntenySpeciesDragDropSupport;
import simplegenomehub.util.fileio.GenomeCompareExistingResultScanner;
import simplegenomehub.util.fileio.GenomeCompareService;
import simplegenomehub.util.fileio.MultipleSyntenyService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Main panel for multiple synteny UI.
 */
public class MultipleSyntenyPanel extends JPanel {

    private static final int DEFAULT_ROUTING_BOX_HALF_HEIGHT = 40;
    private static final int DEFAULT_GAP_LENGTH = 5;
    private static final int DEFAULT_CANVAS_WIDTH = 1500;
    private static final int DEFAULT_CANVAS_HEIGHT = 1000;
    private static final int DEFAULT_EQUAL_LENGTH_WIDTH = 600;
    private static final int DEFAULT_MINIMUM_GENOME_WIDTH = 400;
    private static final String GENE_SET_NONE_OPTION = "<None>";
    private static final double[] LAYOUT_ZOOM_LEVELS = {
        0.25d, 0.5d, 0.75d, 1.0d, 1.25d, 1.5d, 2.0d, 3.0d, 4.0d, 5.0d
    };

    private final SpeciesManager speciesManager;
    private final List<MultipleSyntenyGenomeModel> genomeModels;
    private final List<MultipleSyntenyLinkModel> linkModels;

    private JTree speciesTree;
    private DefaultTreeModel treeModel;
    private JButton addGenomeButton;
    private JButton startButton;
    private JTextArea highlightGeneArea;
    private JComboBox<String> geneSetComboBox;
    private JTextArea logArea;
    private JToggleButton gridSnapToggleButton;

    private List<GeneSetChoice> availableGeneSets;
    private final List<SelectedHighlightRegion> selectedHighlightRegions;
    private final Map<MultipleSyntenyGenomeModel, GenomeHighlightState> highlightStateByGenome;
    private boolean adjustingGeneSetSelection;
    private SpeciesInfo initialSpecies;

    private MultipleSyntenyLayoutCanvas layoutCanvas;
    private JScrollPane layoutCanvasScrollPane;
    private MultipleSyntenyGenomePropertyPanel genomePropertyPanel;
    private MultipleSyntenyLinkAndSettingsPanel linkAndSettingsPanel;

    private MultipleSyntenyGenomeModel selectedGenome;
    private MultipleSyntenyLinkModel selectedLink;

    private int routingBoxHalfHeight = DEFAULT_ROUTING_BOX_HALF_HEIGHT;
    private int gapLength = DEFAULT_GAP_LENGTH;
    private int canvasWidth = DEFAULT_CANVAS_WIDTH;
    private int canvasHeight = DEFAULT_CANVAS_HEIGHT;
    private boolean equalGenomeLength;
    private int equalGenomeLengthWidth = DEFAULT_EQUAL_LENGTH_WIDTH;
    private int minimumGenomeLengthWidth = DEFAULT_MINIMUM_GENOME_WIDTH;
    private boolean gridSnapEnabled;

    public MultipleSyntenyPanel(SpeciesManager speciesManager) {
        this.speciesManager = speciesManager;
        this.genomeModels = new ArrayList<>();
        this.linkModels = new ArrayList<>();
        this.availableGeneSets = new ArrayList<>();
        this.selectedHighlightRegions = new ArrayList<>();
        this.highlightStateByGenome = new LinkedHashMap<>();

        initializeGui();
        refreshSpeciesTree();
        refreshGeneSetOptions();
        refreshWorkspace();
    }

    private void initializeGui() {
        setLayout(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setBackground(SimpleGenomeHubUi.DIALOG_BACKGROUND);

        javax.swing.JSplitPane mainSplitPane = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT);
        SimpleGenomeHubUi.styleSplitPane(mainSplitPane);
        mainSplitPane.setLeftComponent(createLeftPanel());
        mainSplitPane.setRightComponent(createWorkspacePanel());
        mainSplitPane.setDividerLocation(300);

        add(mainSplitPane, BorderLayout.CENTER);
    }

    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(0, 12));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Genome Selection"));

        createSpeciesTree();
        JScrollPane treeScrollPane = new JScrollPane(speciesTree);
        treeScrollPane.setPreferredSize(new Dimension(280, 620));
        leftPanel.add(treeScrollPane, BorderLayout.CENTER);

        addGenomeButton = new JButton("Add Genome");
        addGenomeButton.setPreferredSize(new Dimension(140, 38));
        addGenomeButton.addActionListener(e -> addSelectedGenome());

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setOpaque(false);
        buttonPanel.add(addGenomeButton, BorderLayout.CENTER);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);
        return leftPanel;
    }

    private void createSpeciesTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Species List");
        treeModel = new DefaultTreeModel(root);
        speciesTree = new JTree(treeModel);
        speciesTree.setRootVisible(false);
        speciesTree.setShowsRootHandles(true);
        speciesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        speciesTree.setCellRenderer(new SpeciesTreeCellRenderer());
        MultipleSyntenySpeciesDragDropSupport.installSpeciesDragSource(speciesTree);
        speciesTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    addSelectedGenome();
                }
            }
        });
    }

    private JPanel createWorkspacePanel() {
        JPanel workspacePanel = new JPanel(new BorderLayout());
        workspacePanel.setOpaque(false);

        javax.swing.JSplitPane workspaceSplitPane =
            new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT);
        SimpleGenomeHubUi.styleSplitPane(workspaceSplitPane);
        workspaceSplitPane.setLeftComponent(createLayoutWorkspacePanel());
        workspaceSplitPane.setRightComponent(createInspectorPanel());
        workspaceSplitPane.setDividerLocation(0.36);
        workspaceSplitPane.setResizeWeight(0.25);

        workspacePanel.add(workspaceSplitPane, BorderLayout.CENTER);
        return workspacePanel;
    }

    private JPanel createLayoutWorkspacePanel() {
        JPanel layoutPanel = new JPanel(new BorderLayout(0, 8));
        layoutPanel.setBorder(BorderFactory.createTitledBorder("Layout Workspace"));

        JLabel hintLabel = new JLabel(
            "<html>Left-drag genomes to move them. Right-click two genomes to create a link. "
                + "Double-click a hovered link to switch its shape.<br>"
                + "Use the mouse wheel to zoom. Click the left-side button to enable genome position snapping.</html>"
        );
        hintLabel.setFont(SimpleGenomeHubStyle.italic(hintLabel.getFont(), 11f));
        hintLabel.setForeground(new Color(104, 119, 142));

        JPanel workspaceHeaderPanel = new JPanel(new BorderLayout(8, 0));
        workspaceHeaderPanel.setOpaque(false);
        workspaceHeaderPanel.add(createGridSnapToggleButton(), BorderLayout.WEST);
        workspaceHeaderPanel.add(hintLabel, BorderLayout.CENTER);
        layoutPanel.add(workspaceHeaderPanel, BorderLayout.NORTH);

        layoutCanvas = new MultipleSyntenyLayoutCanvas(new MultipleSyntenyLayoutCanvas.LayoutCanvasListener() {
            @Override
            public void onGenomeSelected(MultipleSyntenyGenomeModel genome) {
                selectGenome(genome);
            }

            @Override
            public void onCreateLinkRequested(MultipleSyntenyGenomeModel first,
                                              MultipleSyntenyGenomeModel second) {
                createLink(first, second);
            }

            @Override
            public void onRemoveLinkRequested(MultipleSyntenyLinkModel linkModel) {
                if (linkModel == null) {
                    return;
                }
                linkModels.remove(linkModel);
                if (selectedLink == linkModel) {
                    selectedLink = null;
                }
                appendLogLine("Removed link: " + linkModel.getDisplayLabel());
                refreshWorkspace();
            }

            @Override
            public void onLinkToggled(MultipleSyntenyLinkModel linkModel) {
                selectedLink = linkModel;
                refreshInspectorState();
                layoutCanvas.repaint();
            }

            @Override
            public void onRemoveGenomeRequested(MultipleSyntenyGenomeModel genome) {
                removeGenome(genome);
            }

            @Override
            public void onGenomeLayoutChanged(MultipleSyntenyGenomeModel genome) {
                if (selectedGenome == genome) {
                    genomePropertyPanel.refreshGeometryFields(genome);
                }
                layoutCanvas.repaint();
            }
        });
        layoutCanvas.setCanvasSize(canvasWidth, canvasHeight);
        layoutCanvas.setPreviewSettings(routingBoxHalfHeight, gapLength);
        layoutCanvas.setGridSnapEnabled(gridSnapEnabled);
        MultipleSyntenySpeciesDragDropSupport.installSpeciesDropTarget(layoutCanvas, this::addGenomeFromDrop);

        layoutCanvasScrollPane = new JScrollPane(layoutCanvas);
        layoutCanvasScrollPane.setPreferredSize(new Dimension(760, 640));
        layoutCanvasScrollPane.setWheelScrollingEnabled(false);
        layoutCanvasScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        layoutCanvasScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        layoutCanvas.addMouseWheelListener(this::handleLayoutMouseWheelZoom);
        layoutCanvasScrollPane.getViewport().addMouseWheelListener(this::handleLayoutMouseWheelZoom);
        layoutCanvasScrollPane.getViewport().addChangeListener(createWorkspaceViewportListener());
        layoutPanel.add(layoutCanvasScrollPane, BorderLayout.CENTER);
        return layoutPanel;
    }

    private JToggleButton createGridSnapToggleButton() {
        gridSnapToggleButton = new JToggleButton("+");
        gridSnapToggleButton.setSelected(false);
        gridSnapToggleButton.setFocusPainted(false);
        gridSnapToggleButton.setOpaque(true);
        gridSnapToggleButton.setFont(SimpleGenomeHubStyle.bold(gridSnapToggleButton.getFont(), 15f));
        gridSnapToggleButton.setPreferredSize(new Dimension(38, 38));
        gridSnapToggleButton.setToolTipText(
            "Toggle grid snap (" + MultipleSyntenyGridSnapHelper.GRID_SPACING
                + "-unit grid, "
                + (int) MultipleSyntenyGridSnapHelper.SNAP_TOLERANCE
                + "-unit tolerance)"
        );
        gridSnapToggleButton.addActionListener(e -> {
            gridSnapEnabled = gridSnapToggleButton.isSelected();
            if (layoutCanvas != null) {
                layoutCanvas.setGridSnapEnabled(gridSnapEnabled);
            }
            updateGridSnapToggleStyle();
        });
        updateGridSnapToggleStyle();
        return gridSnapToggleButton;
    }

    private void updateGridSnapToggleStyle() {
        if (gridSnapToggleButton == null) {
            return;
        }

        if (gridSnapToggleButton.isSelected()) {
            gridSnapToggleButton.setBackground(SimpleGenomeHubUi.DIALOG_PRIMARY_BUTTON);
            gridSnapToggleButton.setForeground(SimpleGenomeHubUi.DIALOG_PRIMARY_BUTTON_TEXT);
            gridSnapToggleButton.setBorder(BorderFactory.createLineBorder(SimpleGenomeHubUi.DIALOG_PRIMARY_BUTTON_BORDER, 1));
        } else {
            gridSnapToggleButton.setBackground(SimpleGenomeHubUi.DIALOG_SECONDARY_BUTTON);
            gridSnapToggleButton.setForeground(SimpleGenomeHubUi.DIALOG_SECONDARY_BUTTON_TEXT);
            gridSnapToggleButton.setBorder(BorderFactory.createLineBorder(SimpleGenomeHubUi.SOFT_BUTTON_BORDER, 1));
        }
    }

    private ChangeListener createWorkspaceViewportListener() {
        return e -> refreshWorkspaceCoordinateReference();
    }

    private JPanel createInspectorPanel() {
        JPanel inspectorPanel = new JPanel(new BorderLayout(0, 12));
        inspectorPanel.setOpaque(false);

        JComponent highlightsPanel = createHighlightPanel();
        genomePropertyPanel = new MultipleSyntenyGenomePropertyPanel(
            highlightsPanel,
            new MultipleSyntenyGenomePropertyPanel.GenomePropertyListener() {
                @Override
                public void onAnchorModeChanged(MultipleSyntenyAnchorMode anchorMode) {
                    if (selectedGenome == null || anchorMode == null) {
                        return;
                    }
                    selectedGenome.setAnchorMode(anchorMode);
                    refreshSelectedGenomeView();
                }

                @Override
                public void onAnchorPositionChanged(int x, int y) {
                    if (selectedGenome == null) {
                        return;
                    }
                    selectedGenome.moveSelectedAnchorTo(x, y);
                    refreshSelectedGenomeView();
                }

                @Override
                public void onRotationChanged(int rotation) {
                    if (selectedGenome == null) {
                        return;
                    }
                    selectedGenome.setRotationAroundSelectedAnchor(rotation);
                    refreshSelectedGenomeView();
                }

                @Override
                public void onChromosomesChanged(String chromosomeText) {
                    if (selectedGenome == null) {
                        return;
                    }
                    selectedGenome.setChromosomeText(chromosomeText);
                    refreshLinkReusableResultsForGenome(selectedGenome);
                    refreshWorkspace();
                }
            }
        );

        linkAndSettingsPanel = new MultipleSyntenyLinkAndSettingsPanel(
            new MultipleSyntenyLinkAndSettingsPanel.LinkAndSettingsListener() {
                @Override
                public void onLinkSelected(MultipleSyntenyLinkModel linkModel) {
                    selectedLink = linkModel;
                    refreshInspectorState();
                }

                @Override
                public void onLinkRemoved(MultipleSyntenyLinkModel linkModel) {
                    if (linkModel == null) {
                        return;
                    }
                    linkModels.remove(linkModel);
                    if (selectedLink == linkModel) {
                        selectedLink = null;
                    }
                    refreshWorkspace();
                }

                @Override
                public void onLinkSelectionChanged(MultipleSyntenyLinkModel linkModel,
                                                   GenomeCompareExistingResultScanner.ReusableResult reusableResult) {
                    if (linkModel == null) {
                        return;
                    }
                    if (reusableResult == null) {
                        linkModel.selectNewLink();
                    } else {
                        linkModel.selectReusableResult(reusableResult);
                    }
                    refreshInspectorState();
                }

                @Override
                public void onRoutingBoxHalfHeightChanged(int value) {
                    routingBoxHalfHeight = Math.max(1, value);
                    for (MultipleSyntenyLinkModel lm : linkModels) {
                        lm.setBendHeight(routingBoxHalfHeight);
                    }
                    layoutCanvas.setPreviewSettings(routingBoxHalfHeight, gapLength);
                    refreshInspectorState();
                    layoutCanvas.repaint();
                }

                @Override
                public void onGapLengthChanged(int value) {
                    gapLength = Math.max(0, value);
                    refreshWorkspace();
                }

                @Override
                public void onEqualLengthChanged(boolean value) {
                    equalGenomeLength = value;
                    refreshWorkspace();
                }

                @Override
                public void onCanvasWidthChanged(int value) {
                    canvasWidth = Math.max(1, value);
                    refreshWorkspace();
                }

                @Override
                public void onCanvasHeightChanged(int value) {
                    canvasHeight = Math.max(1, value);
                    refreshWorkspace();
                }

                @Override
                public void onEqualLengthSizeChanged(int value) {
                    equalGenomeLengthWidth = Math.max(1, value);
                    refreshWorkspace();
                }

                @Override
                public void onMinimumGenomeLengthChanged(int value) {
                    minimumGenomeLengthWidth = Math.max(1, value);
                    refreshWorkspace();
                }

                @Override
                public void onRouteModeChanged(MultipleSyntenyLinkModel linkModel,
                                               MultipleSyntenyLinkRouteMode mode) {
                    if (linkModel == null) {
                        return;
                    }
                    linkModel.setRouteMode(mode);
                    layoutCanvas.repaint();
                }

                @Override
                public void onBendHeightChanged(MultipleSyntenyLinkModel linkModel, int value) {
                    if (linkModel == null) {
                        return;
                    }
                    linkModel.setBendHeight(value);
                    layoutCanvas.repaint();
                }

                @Override
                public void onRunCpuChanged(MultipleSyntenyLinkModel linkModel, int value) {
                    if (linkModel == null) {
                        return;
                    }
                    linkModel.setRunCpu(value);
                    refreshInspectorState();
                }

                @Override
                public void onRunEvalueChanged(MultipleSyntenyLinkModel linkModel, double value) {
                    if (linkModel == null) {
                        return;
                    }
                    linkModel.setRunEvalue(value);
                    refreshInspectorState();
                }

                @Override
                public void onRunNumHitsChanged(MultipleSyntenyLinkModel linkModel, int value) {
                    if (linkModel == null) {
                        return;
                    }
                    linkModel.setRunNumHits(value);
                    refreshInspectorState();
                }

                @Override
                public void onRunDirectPlotChanged(MultipleSyntenyLinkModel linkModel, boolean value) {
                    if (linkModel == null) {
                        return;
                    }
                    linkModel.setRunDirectPlot(value);
                    refreshInspectorState();
                }
            }
        );

        InspectorScrollablePanel sidebar = new InspectorScrollablePanel();
        sidebar.setOpaque(false);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(0, 0, 0, 0));
        sidebar.add(genomePropertyPanel);
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(linkAndSettingsPanel);
        sidebar.add(Box.createVerticalStrut(12));
        sidebar.add(createLogPanel());
        sidebar.add(Box.createVerticalGlue());

        JScrollPane inspectorScrollPane = new JScrollPane(sidebar);
        inspectorScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        inspectorScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        inspectorScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        inspectorScrollPane.setPreferredSize(new Dimension(430, 640));
        inspectorPanel.add(inspectorScrollPane, BorderLayout.CENTER);

        startButton = new JButton("Start");
        startButton.setPreferredSize(new Dimension(0, 44));
        startButton.addActionListener(e -> startMultipleSynteny());

        JPanel startPanel = new JPanel(new BorderLayout());
        startPanel.setOpaque(false);
        startPanel.add(startButton, BorderLayout.CENTER);
        inspectorPanel.add(startPanel, BorderLayout.SOUTH);
        return inspectorPanel;
    }

    private JComponent createHighlightPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);

        JPanel geneSetPanel = new JPanel(new BorderLayout(0, 4));
        geneSetPanel.setOpaque(false);

        JPanel comboHeader = new JPanel(new BorderLayout(8, 0));
        comboHeader.setOpaque(false);
        comboHeader.add(new JLabel("Highlight Set"), BorderLayout.WEST);

        geneSetComboBox = new JComboBox<>();
        geneSetComboBox.setPreferredSize(SimpleGenomeHubStyle.SIZE_COMBO_280_X_30);
        comboHeader.add(geneSetComboBox, BorderLayout.CENTER);
        geneSetPanel.add(comboHeader, BorderLayout.NORTH);

        JLabel geneSetHintLabel = new JLabel(
            "<html>Only sets from the selected genome are shown.<br>"
                + "Region Sets keep original intervals.</html>"
        );
        geneSetHintLabel.setFont(SimpleGenomeHubStyle.italic(geneSetHintLabel.getFont(), 11f));
        geneSetHintLabel.setForeground(new Color(118, 128, 142));
        geneSetPanel.add(geneSetHintLabel, BorderLayout.SOUTH);
        panel.add(geneSetPanel, BorderLayout.NORTH);

        highlightGeneArea = new JTextArea(4, 20);
        highlightGeneArea.setLineWrap(false);
        highlightGeneArea.setWrapStyleWord(false);
        highlightGeneArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        Color textAreaBackground = new Color(246, 248, 251);
        highlightGeneArea.setBackground(textAreaBackground);
        JScrollPane highlightScrollPane = new JScrollPane(highlightGeneArea);
        highlightScrollPane.getViewport().setBackground(textAreaBackground);
        highlightScrollPane.setPreferredSize(new Dimension(0, 104));
        highlightScrollPane.setMinimumSize(new Dimension(0, 104));
        highlightScrollPane.setBorder(BorderFactory.createTitledBorder("Gene Highlight List"));
        panel.add(highlightScrollPane, BorderLayout.CENTER);

        geneSetComboBox.addActionListener(e -> applySelectedGeneSet());
        return panel;
    }

    private JComponent createLogPanel() {
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setOpaque(false);
        logPanel.setBorder(BorderFactory.createTitledBorder("Log"));
        logPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        logPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        logArea.setText("Log output will appear here.\n");

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(0, 180));
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        return logPanel;
    }

    private void refreshSpeciesTree() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();

        if (speciesManager != null) {
            List<SpeciesInfo> allSpecies = speciesManager.getAllSpecies();
            for (SpeciesInfo species : allSpecies) {
                root.add(new DefaultMutableTreeNode(species));
            }
        }

        treeModel.reload();
        for (int i = 0; i < speciesTree.getRowCount(); i++) {
            speciesTree.expandRow(i);
        }
    }

    private SpeciesInfo getSelectedSpecies() {
        if (speciesTree.getSelectionPath() == null) {
            return null;
        }

        DefaultMutableTreeNode node =
            (DefaultMutableTreeNode) speciesTree.getSelectionPath().getLastPathComponent();
        Object userObject = node.getUserObject();
        return userObject instanceof SpeciesInfo ? (SpeciesInfo) userObject : null;
    }

    private void addSelectedGenome() {
        SpeciesInfo selectedSpecies = getSelectedSpecies();
        if (selectedSpecies == null) {
            JOptionPane.showMessageDialog(
                this,
                "Please select a genome from the left tree first.",
                "No Genome Selected",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        addGenome(selectedSpecies);
    }

    private void addGenomeFromDrop(SpeciesInfo species) {
        if (species != null) {
            addGenome(species);
        }
    }

    private void addGenome(SpeciesInfo species) {
        if (species == null) {
            return;
        }

        MultipleSyntenyGenomeModel genomeModel = new MultipleSyntenyGenomeModel(
            genomeModels.size() + 1,
            species,
            resolveChromosomeNames(species),
            computeDefaultAnchorX(genomeModels.size()),
            computeDefaultAnchorY(genomeModels.size(), gapLength)
        );
        genomeModels.add(genomeModel);
        appendLogLine("Added " + genomeModel.getGenomeTitle() + ": " + genomeModel.getDisplayName());
        selectGenome(genomeModel);
        refreshGeneSetOptions();
        refreshWorkspace();
    }

    private double computeDefaultAnchorX(int index) {
        return 640;
    }

    private double computeDefaultAnchorY(int index, int currentGapLength) {
        int rowSpacing = 110 + Math.max(0, currentGapLength / 2);
        return 130 + index * rowSpacing;
    }

    private void selectGenome(MultipleSyntenyGenomeModel genomeModel) {
        saveCurrentGenomeHighlightState();
        selectedGenome = genomeModel;
        if (selectedGenome != null) {
            selectedLink = null;
        }
        loadSelectedGenomeHighlightState();
        refreshInspectorState();
        if (layoutCanvas != null) {
            layoutCanvas.setSelectedGenome(selectedGenome);
            layoutCanvas.repaint();
        }
        refreshGeneSetOptions();
    }

    private void refreshSelectedGenomeView() {
        refreshWorkspaceCoordinateReference();
        genomePropertyPanel.setGenome(selectedGenome);
        layoutCanvas.setSelectedGenome(selectedGenome);
        layoutCanvas.repaint();
    }

    private void createLink(MultipleSyntenyGenomeModel first, MultipleSyntenyGenomeModel second) {
        if (first == null || second == null || first == second) {
            return;
        }

        MultipleSyntenyLinkModel existing = findLink(first, second);
        if (existing != null) {
            selectedLink = existing;
            refreshInspectorState();
            return;
        }

        MultipleSyntenyLinkModel linkModel = new MultipleSyntenyLinkModel(first, second);
        refreshReusableResults(linkModel);
        linkModels.add(linkModel);
        selectedLink = linkModel;
        appendLogLine("Added link: " + linkModel.getDisplayLabel());
        refreshWorkspace();
    }

    private MultipleSyntenyLinkModel findLink(MultipleSyntenyGenomeModel first,
                                              MultipleSyntenyGenomeModel second) {
        for (MultipleSyntenyLinkModel linkModel : linkModels) {
            if (linkModel.matches(first, second)) {
                return linkModel;
            }
        }
        return null;
    }

    private void removeGenome(MultipleSyntenyGenomeModel genomeModel) {
        if (genomeModel == null) {
            return;
        }

        saveCurrentGenomeHighlightState();
        String genomeTitle = genomeModel.getGenomeTitle();
        genomeModels.remove(genomeModel);
        linkModels.removeIf(linkModel -> linkModel.references(genomeModel));
        selectedHighlightRegions.removeIf(highlightRegion -> highlightRegion.references(genomeModel));
        highlightStateByGenome.remove(genomeModel);
        renumberGenomes();

        if (selectedGenome == genomeModel) {
            selectedGenome = genomeModels.isEmpty() ? null : genomeModels.get(Math.max(0, genomeModels.size() - 1));
        }
        if (selectedLink != null && selectedLink.references(genomeModel)) {
            selectedLink = null;
        }

        appendLogLine("Removed " + genomeTitle + ".");
        loadSelectedGenomeHighlightState();
        refreshGeneSetOptions();
        refreshWorkspace();
    }

    private void renumberGenomes() {
        for (int i = 0; i < genomeModels.size(); i++) {
            genomeModels.get(i).setSlotNumber(i + 1);
        }
    }

    private void refreshWorkspace() {
        updateGenomeVisualScaling();
        if (layoutCanvas != null) {
            layoutCanvas.setCanvasSize(canvasWidth, canvasHeight);
            layoutCanvas.setGenomeItems(genomeModels);
            layoutCanvas.setLinkItems(linkModels);
            layoutCanvas.setSelectedGenome(selectedGenome);
            layoutCanvas.setPreviewSettings(routingBoxHalfHeight, gapLength);
            layoutCanvas.setGridSnapEnabled(gridSnapEnabled);
            layoutCanvas.repaint();
        }
        refreshInspectorState();
    }

    private void refreshLinkReusableResultsForGenome(MultipleSyntenyGenomeModel genomeModel) {
        if (genomeModel == null) {
            return;
        }
        for (MultipleSyntenyLinkModel linkModel : linkModels) {
            if (linkModel.references(genomeModel)) {
                refreshReusableResults(linkModel);
            }
        }
    }

    private void refreshReusableResults(MultipleSyntenyLinkModel linkModel) {
        if (linkModel == null || linkModel.getGenome1() == null || linkModel.getGenome2() == null) {
            return;
        }

        List<GenomeCompareExistingResultScanner.ReusableResult> reusableResults =
            GenomeCompareExistingResultScanner.findReusableResults(
                linkModel.getGenome1().getSpecies(),
                linkModel.getGenome1().getChromosomeList(),
                linkModel.getGenome2().getSpecies(),
                linkModel.getGenome2().getChromosomeList()
            );
        linkModel.setReusableResults(reusableResults);
    }

    private void refreshInspectorState() {
        refreshWorkspaceCoordinateReference();
        if (genomePropertyPanel != null) {
            genomePropertyPanel.setGenome(selectedGenome);
        }
        if (linkAndSettingsPanel != null) {
            linkAndSettingsPanel.setLinks(linkModels, selectedLink);
            linkAndSettingsPanel.setVisualSettings(
                routingBoxHalfHeight, gapLength,
                canvasWidth, canvasHeight,
                equalGenomeLength, equalGenomeLengthWidth,
                minimumGenomeLengthWidth
            );
        }
    }

    private void refreshWorkspaceCoordinateReference() {
        if (genomePropertyPanel == null) {
            return;
        }
        if (layoutCanvasScrollPane == null || layoutCanvasScrollPane.getViewport() == null) {
            genomePropertyPanel.setPositionDisplayOffset(0, 0);
            return;
        }
        java.awt.Point viewPosition = layoutCanvasScrollPane.getViewport().getViewPosition();
        if (layoutCanvas == null) {
            genomePropertyPanel.setPositionDisplayOffset(viewPosition.x, viewPosition.y);
            return;
        }
        genomePropertyPanel.setPositionDisplayOffset(
            layoutCanvas.toLayoutCoordinate(viewPosition.x),
            layoutCanvas.toLayoutCoordinate(viewPosition.y)
        );
    }

    private void handleLayoutMouseWheelZoom(MouseWheelEvent event) {
        if (layoutCanvas == null || layoutCanvasScrollPane == null || event.getWheelRotation() == 0) {
            return;
        }

        int currentIndex = findNearestZoomIndex(LAYOUT_ZOOM_LEVELS, layoutCanvas.getZoomFactor());
        int nextIndex = clamp(currentIndex - Integer.signum(event.getWheelRotation()), 0, LAYOUT_ZOOM_LEVELS.length - 1);
        if (nextIndex == currentIndex) {
            event.consume();
            return;
        }

        Point anchorInViewport = convertToViewportPoint(event, layoutCanvasScrollPane);
        setLayoutZoom(LAYOUT_ZOOM_LEVELS[nextIndex], anchorInViewport);
        event.consume();
    }

    private void setLayoutZoom(double zoomFactor, Point anchorInViewport) {
        if (layoutCanvas == null || layoutCanvasScrollPane == null) {
            return;
        }

        JViewport viewport = layoutCanvasScrollPane.getViewport();
        Rectangle viewRect = viewport.getViewRect();
        Point anchorPoint = anchorInViewport == null
            ? new Point(viewRect.width / 2, viewRect.height / 2)
            : anchorInViewport;

        Point viewPosition = viewport.getViewPosition();
        Point2D.Double layoutAnchorPoint = layoutCanvas.toLayoutPoint(
            viewPosition.x + anchorPoint.x,
            viewPosition.y + anchorPoint.y
        );

        layoutCanvas.setZoomFactor(zoomFactor);

        Dimension newSize = layoutCanvas.getPreferredSize();
        int targetX = clamp(
            (int) Math.round(layoutAnchorPoint.x * layoutCanvas.getZoomFactor() - anchorPoint.x),
            0,
            Math.max(0, newSize.width - viewRect.width)
        );
        int targetY = clamp(
            (int) Math.round(layoutAnchorPoint.y * layoutCanvas.getZoomFactor() - anchorPoint.y),
            0,
            Math.max(0, newSize.height - viewRect.height)
        );
        viewport.setViewPosition(new Point(targetX, targetY));
        refreshWorkspaceCoordinateReference();
    }

    private Point convertToViewportPoint(MouseWheelEvent event, JScrollPane scrollPane) {
        Component source = event.getComponent();
        return SwingUtilities.convertPoint(source, event.getPoint(), scrollPane.getViewport());
    }

    private int findNearestZoomIndex(double[] zoomLevels, double zoomFactor) {
        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < zoomLevels.length; i++) {
            double distance = Math.abs(zoomLevels[i] - zoomFactor);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private int clamp(int value, int minValue, int maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    private void updateGenomeVisualScaling() {
        if (genomeModels.isEmpty()) {
            return;
        }

        long referenceGenomeLength = 1L;
        int targetWidth = Math.max(1, equalGenomeLength ? equalGenomeLengthWidth : minimumGenomeLengthWidth);
        if (equalGenomeLength) {
            referenceGenomeLength = findMaxSelectedGenomeLength();
        } else {
            MultipleSyntenyGenomeModel shortestGenome = findShortestGenomeModel();
            if (shortestGenome != null) {
                referenceGenomeLength = Math.max(1L, shortestGenome.getSelectedGenomeLength());
                int shortestGenomeGapWidth =
                    Math.max(0, gapLength) * Math.max(0, shortestGenome.getSelectedChromosomeCount() - 1);
                targetWidth = Math.max(1, targetWidth - shortestGenomeGapWidth);
            }
        }
        for (MultipleSyntenyGenomeModel genomeModel : genomeModels) {
            genomeModel.applyVisualScale(referenceGenomeLength, targetWidth, gapLength, equalGenomeLength);
        }
    }

    private long findMaxSelectedGenomeLength() {
        long longestGenomeLength = 1L;
        for (MultipleSyntenyGenomeModel genomeModel : genomeModels) {
            longestGenomeLength = Math.max(longestGenomeLength, genomeModel.getSelectedGenomeLength());
        }
        return longestGenomeLength;
    }

    private MultipleSyntenyGenomeModel findShortestGenomeModel() {
        MultipleSyntenyGenomeModel shortestGenome = null;
        long shortestLength = Long.MAX_VALUE;
        for (MultipleSyntenyGenomeModel genomeModel : genomeModels) {
            if (genomeModel == null) {
                continue;
            }
            long genomeLength = Math.max(1L, genomeModel.getSelectedGenomeLength());
            if (shortestGenome == null || genomeLength < shortestLength) {
                shortestGenome = genomeModel;
                shortestLength = genomeLength;
            }
        }
        return shortestGenome;
    }

    private List<String> resolveChromosomeNames(SpeciesInfo species) {
        List<String> chromosomes = new ArrayList<>();
        if (species == null) {
            return chromosomes;
        }

        if (species.getGenomeData() != null) {
            for (GenomeData.ChromosomeStat chromosomeStat : species.getGenomeData().getChromosomeStats()) {
                if (chromosomeStat == null || chromosomeStat.getName() == null) {
                    continue;
                }
                String name = chromosomeStat.getName().trim();
                if (!name.isEmpty()) {
                    chromosomes.add(name);
                }
            }
        }
        if (!chromosomes.isEmpty()) {
            return chromosomes;
        }

        File genomeFile = species.getGenomeFile();
        if (genomeFile == null || !genomeFile.isFile()) {
            return chromosomes;
        }

        LinkedHashSet<String> fallbackChromosomes = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(genomeFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.startsWith(">")) {
                    continue;
                }
                String header = trimmed.substring(1).trim();
                if (!header.isEmpty()) {
                    fallbackChromosomes.add(header.split("\\s+")[0]);
                }
            }
        } catch (IOException ignored) {
            return chromosomes;
        }

        chromosomes.addAll(fallbackChromosomes);
        return chromosomes;
    }

    private void appendLogLine(String text) {
        if (logArea == null || text == null) {
            return;
        }

        logArea.append(text);
        if (!text.endsWith("\n")) {
            logArea.append("\n");
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void resetLogArea() {
        if (logArea != null) {
            logArea.setText("");
        }
    }

    private void startMultipleSynteny() {
        final MultipleSyntenyRunInputs runInputs;
        try {
            runInputs = buildRunInputs();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "Multiple Synteny Error",
                JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        startButton.setEnabled(false);
        resetLogArea();
        appendLogLine("Preparing Multiple Synteny...");

        SwingWorker<MultipleSyntenyService.Result, String> worker =
            new SwingWorker<MultipleSyntenyService.Result, String>() {
                @Override
                protected MultipleSyntenyService.Result doInBackground() throws Exception {
                    return MultipleSyntenyService.run(runInputs.request, this::publish);
                }

                @Override
                protected void process(List<String> chunks) {
                    for (String chunk : chunks) {
                        appendLogLine(chunk);
                    }
                }

                @Override
                protected void done() {
                    startButton.setEnabled(true);
                    try {
                        MultipleSyntenyService.Result result = get();
                        appendLogLine("Multiple Synteny completed.");
                        appendLogLine("Batch folder: " + result.getOutputDir().getAbsolutePath());
                        for (MultipleSyntenyService.PairResult pairResult : result.getPairResults()) {
                            appendLogLine(pairResult.getDisplayLabel() + ": " + pairResult.getOutputDir().getAbsolutePath());
                        }

                        StringBuilder message = new StringBuilder();
                        message.append("Multiple Synteny completed.\n\n");
                        message.append("Batch folder:\n").append(result.getOutputDir().getAbsolutePath()).append("\n\n");
                        message.append("Pairwise result folders:\n");
                        for (MultipleSyntenyService.PairResult pairResult : result.getPairResults()) {
                            message.append(pairResult.getDisplayLabel())
                                .append(": ")
                                .append(pairResult.getOutputDir().getAbsolutePath())
                                .append("\n");
                        }

                        Object[] options = { "Open Viewer", "Close" };
                        int option = JOptionPane.showOptionDialog(
                            MultipleSyntenyPanel.this,
                            message.toString() + "\nOpen the rendered result viewer now?",
                            "Multiple Synteny Finished",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE,
                            null,
                            options,
                            options[0]
                        );
                        if (option == JOptionPane.YES_OPTION) {
                            openResultViewer(result.getOutputDir());
                        }
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        appendLogLine("Multiple Synteny failed: " + cause.getMessage());
                        JOptionPane.showMessageDialog(
                            MultipleSyntenyPanel.this,
                            "Multiple Synteny failed:\n" + cause.getMessage(),
                            "Multiple Synteny Error",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                }
            };

        worker.execute();
    }

    private void openResultViewer(File resultDir) {
        try {
            MultipleSyntenyResultViewerDialog dialog = new MultipleSyntenyResultViewerDialog(
                SwingUtilities.getWindowAncestor(this),
                resultDir
            );
            dialog.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to open Multiple Synteny result viewer:\n" + ex.getMessage(),
                "Result Viewer Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private MultipleSyntenyRunInputs buildRunInputs() {
        if (linkModels.isEmpty()) {
            throw new IllegalArgumentException("Please create at least one link before starting Multiple Synteny.");
        }

        saveCurrentGenomeHighlightState();
        updateGenomeVisualScaling();

        LinkedHashSet<MultipleSyntenyGenomeModel> linkedGenomes = new LinkedHashSet<>();
        for (MultipleSyntenyLinkModel linkModel : linkModels) {
            if (linkModel == null || linkModel.getGenome1() == null || linkModel.getGenome2() == null) {
                continue;
            }
            linkedGenomes.add(linkModel.getGenome1());
            linkedGenomes.add(linkModel.getGenome2());
        }
        if (linkedGenomes.size() < 2) {
            throw new IllegalArgumentException("Multiple Synteny requires at least two linked genomes.");
        }

        Map<MultipleSyntenyGenomeModel, List<String>> highlightGeneIdsByGenome =
            collectHighlightGeneIdsByGenome(linkedGenomes);
        List<String> highlightGeneIds = flattenHighlightGeneIds(highlightGeneIdsByGenome);
        List<MultipleSyntenyService.HighlightRegion> highlightRegions = collectHighlightRegions(linkedGenomes);
        List<MultipleSyntenyService.GenomeSelection> selections = new ArrayList<>();
        Map<MultipleSyntenyGenomeModel, MultipleSyntenyService.GenomeSelection> selectionByGenome =
            new LinkedHashMap<>();
        for (MultipleSyntenyGenomeModel genomeModel : genomeModels) {
            if (!linkedGenomes.contains(genomeModel)) {
                continue;
            }
            List<String> chromosomes = genomeModel.getChromosomeList();
            if (chromosomes.isEmpty()) {
                throw new IllegalArgumentException(
                    "Please provide at least one chromosome for " + genomeModel.getGenomeTitle() + "."
                );
            }
            MultipleSyntenyService.GenomeSelection selection = new MultipleSyntenyService.GenomeSelection(
                genomeModel.getSlotNumber(),
                genomeModel.getSpecies(),
                chromosomes
            );
            selections.add(selection);
            selectionByGenome.put(genomeModel, selection);
        }

        List<MultipleSyntenyService.LinkRun> linkRuns = new ArrayList<>();
        for (int i = 0; i < linkModels.size(); i++) {
            MultipleSyntenyLinkModel linkModel = linkModels.get(i);
            MultipleSyntenyService.GenomeSelection leftSelection = selectionByGenome.get(linkModel.getGenome1());
            MultipleSyntenyService.GenomeSelection rightSelection = selectionByGenome.get(linkModel.getGenome2());
            if (leftSelection == null || rightSelection == null) {
                throw new IllegalArgumentException(
                    "A link references a genome that is not available for this run."
                );
            }

            linkRuns.add(new MultipleSyntenyService.LinkRun(
                i + 1,
                leftSelection,
                rightSelection,
                buildParameters(
                    linkModel,
                    linkModel.getGenome1(),
                    linkModel.getGenome2(),
                    leftSelection.getChromosomes(),
                    rightSelection.getChromosomes(),
                    highlightGeneIdsByGenome
                ),
                linkModel.getSelectedReusableResult()
            ));
        }

        List<MultipleSyntenyService.GenomeLayout> genomeLayouts =
            buildGenomeLayouts(linkedGenomes);
        List<MultipleSyntenyService.LinkLayout> linkLayouts =
            buildLinkLayouts();
        MultipleSyntenyService.GlobalSettings globalSettings =
            new MultipleSyntenyService.GlobalSettings(
                routingBoxHalfHeight,
                routingBoxHalfHeight,
                gapLength,
                canvasWidth,
                canvasHeight,
                equalGenomeLength,
                equalGenomeLengthWidth
            );

        MultipleSyntenyService.RunRequest request =
            new MultipleSyntenyService.RunRequest(
                selections,
                linkRuns,
                genomeLayouts,
                linkLayouts,
                globalSettings,
                highlightGeneIds,
                convertHighlightGeneIdsBySlot(highlightGeneIdsByGenome),
                highlightRegions,
                resolveBatchOutputSpecies()
            );
        return new MultipleSyntenyRunInputs(request);
    }

    private List<MultipleSyntenyService.GenomeLayout> buildGenomeLayouts(
        LinkedHashSet<MultipleSyntenyGenomeModel> linkedGenomes
    ) {
        List<MultipleSyntenyService.GenomeLayout> layouts = new ArrayList<>();
        List<MultipleSyntenyGenomeModel> drawOrder = layoutCanvas != null
            ? layoutCanvas.getDrawOrder()
            : new ArrayList<>(genomeModels);
        Map<MultipleSyntenyGenomeModel, Integer> zOrderByGenome = new LinkedHashMap<>();
        for (int i = 0; i < drawOrder.size(); i++) {
            zOrderByGenome.put(drawOrder.get(i), i + 1);
        }

        for (MultipleSyntenyGenomeModel genomeModel : genomeModels) {
            if (!linkedGenomes.contains(genomeModel)) {
                continue;
            }

            List<MultipleSyntenyService.ChromosomeLayout> chromosomeLayouts = new ArrayList<>();
            List<MultipleSyntenyGenomeModel.ChromosomeSegment> segments = genomeModel.getChromosomeSegments();
            for (int i = 0; i < segments.size(); i++) {
                MultipleSyntenyGenomeModel.ChromosomeSegment segment = segments.get(i);
                chromosomeLayouts.add(new MultipleSyntenyService.ChromosomeLayout(
                    i + 1,
                    segment.getName(),
                    segment.getStartX(),
                    segment.getStartX() + segment.getWidth(),
                    segment.getLength()
                ));
            }

            layouts.add(new MultipleSyntenyService.GenomeLayout(
                genomeModel.getSlotNumber(),
                "Genome" + genomeModel.getSlotNumber(),
                genomeModel.getDisplayName(),
                zOrderByGenome.getOrDefault(genomeModel, genomeModel.getSlotNumber()),
                genomeModel.getLeftBottomX(),
                genomeModel.getLeftBottomY(),
                genomeModel.getWidth(),
                genomeModel.getHeight(),
                genomeModel.getRotation(),
                chromosomeLayouts
            ));
        }
        return layouts;
    }

    private List<MultipleSyntenyService.LinkLayout> buildLinkLayouts() {
        List<MultipleSyntenyService.LinkLayout> layouts = new ArrayList<>();
        for (int i = 0; i < linkModels.size(); i++) {
            MultipleSyntenyLinkModel linkModel = linkModels.get(i);
            MultipleSyntenyLinkRoutingHelper.ResolvedEdges edges =
                linkModel.resolveEdges(routingBoxHalfHeight);
            String edge1 = edges == null ? "top" : toEdgeLabel(edges.edge1Type);
            String edge2 = edges == null ? "top" : toEdgeLabel(edges.edge2Type);
            MultipleSyntenyLinkType actualType = edges == null
                ? MultipleSyntenyLinkType.DOUBLE_ARC
                : MultipleSyntenyLinkRoutingHelper.resolveActualType(
                    linkModel.getRouteMode(), edges.edge1Type, edges.edge2Type);
            String linkType = actualType == MultipleSyntenyLinkType.SINGLE_ARC
                ? "single_arc"
                : "double_arc";
            String bulgeDir = resolveBulgeDirection(edge1, edge2);

            layouts.add(new MultipleSyntenyService.LinkLayout(
                i + 1,
                linkModel.getGenome1().getSlotNumber(),
                linkModel.getGenome2().getSlotNumber(),
                linkType,
                edge1,
                edge2,
                linkModel.getBendHeight(),
                bulgeDir
            ));
        }
        return layouts;
    }

    private String toEdgeLabel(MultipleSyntenyHorizontalEdgeType edgeType) {
        return edgeType == MultipleSyntenyHorizontalEdgeType.BOTTOM ? "bottom" : "top";
    }

    private String resolveBulgeDirection(String edge1, String edge2) {
        if ("top".equalsIgnoreCase(edge1) && "top".equalsIgnoreCase(edge2)) {
            return "up";
        }
        if ("bottom".equalsIgnoreCase(edge1) && "bottom".equalsIgnoreCase(edge2)) {
            return "down";
        }
        return "auto";
    }

    private GenomeCompareService.Parameters buildParameters(MultipleSyntenyLinkModel linkModel,
                                                           MultipleSyntenyGenomeModel genome1Model,
                                                           MultipleSyntenyGenomeModel genome2Model,
                                                           List<String> genome1Chromosomes,
                                                           List<String> genome2Chromosomes,
                                                           Map<MultipleSyntenyGenomeModel, List<String>> highlightGeneIdsByGenome) {
        LinkedHashSet<String> linkHighlightGeneIds = new LinkedHashSet<>();
        if (genome1Model != null && highlightGeneIdsByGenome != null) {
            linkHighlightGeneIds.addAll(highlightGeneIdsByGenome.getOrDefault(genome1Model, new ArrayList<>()));
        }
        if (genome2Model != null && highlightGeneIdsByGenome != null) {
            linkHighlightGeneIds.addAll(highlightGeneIdsByGenome.getOrDefault(genome2Model, new ArrayList<>()));
        }
        return new GenomeCompareService.Parameters(
            linkModel.getRunCpu(),
            linkModel.getRunEvalue(),
            linkModel.getRunNumHits(),
            linkModel.isRunDirectPlot(),
            genome1Chromosomes,
            genome2Chromosomes,
            new ArrayList<>(linkHighlightGeneIds)
        );
    }

    private Map<MultipleSyntenyGenomeModel, List<String>> collectHighlightGeneIdsByGenome(
        Iterable<MultipleSyntenyGenomeModel> targetGenomes
    ) {
        Map<MultipleSyntenyGenomeModel, List<String>> resolvedIdsByGenome = new LinkedHashMap<>();
        for (MultipleSyntenyGenomeModel genomeModel : targetGenomes) {
            if (genomeModel == null) {
                continue;
            }

            String highlightGeneText = resolveHighlightGeneText(genomeModel);
            List<String> typedIds = GeneSetFileSupport.parseGeneIds(highlightGeneText);
            if (typedIds.isEmpty()) {
                resolvedIdsByGenome.put(genomeModel, new ArrayList<>());
                continue;
            }

            HighlightResolutionResult typedResult = resolveHighlightIdsForGenome(genomeModel, typedIds);
            if (typedResult.validationResult != null && typedResult.validationResult.hasIssues()) {
                GeneSetGeneIdValidationSupport.showValidationResult(this, typedResult.validationResult);
            }
            if (!typedResult.missingIds.isEmpty()) {
                throw new IllegalArgumentException(
                    "Some highlight IDs could not be validated for " + genomeModel.getGenomeTitle() + ":\n"
                        + String.join("\n", typedResult.missingIds)
                );
            }

            List<String> resolvedIds = new ArrayList<>(typedResult.resolvedIds);
            resolvedIdsByGenome.put(genomeModel, resolvedIds);
            updateHighlightState(genomeModel, GeneSetFileSupport.formatGeneIds(resolvedIds));
        }
        return resolvedIdsByGenome;
    }

    private List<MultipleSyntenyService.HighlightRegion> collectHighlightRegions(
        Iterable<MultipleSyntenyGenomeModel> targetGenomes
    ) {
        LinkedHashSet<MultipleSyntenyGenomeModel> targetGenomeSet = new LinkedHashSet<>();
        for (MultipleSyntenyGenomeModel genomeModel : targetGenomes) {
            if (genomeModel != null) {
                targetGenomeSet.add(genomeModel);
            }
        }

        List<MultipleSyntenyService.HighlightRegion> regions = new ArrayList<>();
        for (SelectedHighlightRegion highlightRegion : selectedHighlightRegions) {
            if (highlightRegion == null || !targetGenomeSet.contains(highlightRegion.genomeModel)) {
                continue;
            }
            regions.add(highlightRegion.toServiceRegion());
        }
        return regions;
    }

    private HighlightResolutionResult resolveHighlightIdsForGenome(
        MultipleSyntenyGenomeModel genomeModel,
        List<String> inputIds
    ) {
        if (genomeModel == null || genomeModel.getSpecies() == null) {
            return new HighlightResolutionResult(new ArrayList<>(), new ArrayList<>(), null);
        }

        try {
            GeneSetGeneIdValidationSupport.ValidationResult validationResult =
                GeneSetGeneIdValidationSupport.validateGeneIds(genomeModel.getSpecies(), inputIds);
            return new HighlightResolutionResult(
                new ArrayList<>(validationResult.getResolvedGeneIds()),
                new ArrayList<>(validationResult.getMissingGeneIds()),
                validationResult
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                "Failed to validate highlight IDs for " + genomeModel.getGenomeTitle() + ": " + ex.getMessage(),
                ex
            );
        }
    }

    public void initializeWithPrimaryGenome(SpeciesInfo species) {
        if (species == null) {
            return;
        }
        initialSpecies = species;
        selectTargetSpecies(species);
        if (genomeModels.isEmpty()) {
            addGenome(species);
        }
    }

    private SpeciesInfo resolveBatchOutputSpecies() {
        if (initialSpecies != null) {
            return initialSpecies;
        }
        SpeciesInfo selectedSpecies = resolveSelectedSpeciesFromTree();
        if (selectedSpecies != null) {
            return selectedSpecies;
        }
        if (!genomeModels.isEmpty() && genomeModels.get(0) != null) {
            return genomeModels.get(0).getSpecies();
        }
        return null;
    }

    private SpeciesInfo resolveSelectedSpeciesFromTree() {
        if (speciesTree == null) {
            return null;
        }
        TreePath selectionPath = speciesTree.getSelectionPath();
        if (selectionPath == null) {
            return null;
        }
        Object nodeObject = selectionPath.getLastPathComponent();
        if (!(nodeObject instanceof DefaultMutableTreeNode)) {
            return null;
        }
        Object userObject = ((DefaultMutableTreeNode) nodeObject).getUserObject();
        return userObject instanceof SpeciesInfo ? (SpeciesInfo) userObject : null;
    }

    public void selectTargetSpecies(SpeciesInfo species) {
        if (species == null) {
            return;
        }

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            if (species.equals(child.getUserObject())) {
                TreePath path = new TreePath(child.getPath());
                speciesTree.setSelectionPath(path);
                speciesTree.scrollPathToVisible(path);
                break;
            }
        }
    }

    public void cleanup() {
        // Reserved for future multiple synteny resources.
    }

    private void refreshGeneSetOptions() {
        if (geneSetComboBox == null) {
            return;
        }

        availableGeneSets = loadAvailableGeneSetChoices();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(GENE_SET_NONE_OPTION);
        for (GeneSetChoice choice : availableGeneSets) {
            model.addElement(choice.getDisplayLabel());
        }

        adjustingGeneSetSelection = true;
        try {
            geneSetComboBox.setModel(model);
            geneSetComboBox.setSelectedIndex(0);
            geneSetComboBox.setEnabled(!availableGeneSets.isEmpty());
        } finally {
            adjustingGeneSetSelection = false;
        }
    }

    private List<GeneSetChoice> loadAvailableGeneSetChoices() {
        List<GeneSetChoice> choices = new ArrayList<>();
        if (selectedGenome != null) {
            appendGeneSetChoices(choices, selectedGenome);
        }
        return choices;
    }

    private void appendGeneSetChoices(List<GeneSetChoice> choices, MultipleSyntenyGenomeModel genomeModel) {
        if (genomeModel == null || genomeModel.getSpecies() == null) {
            return;
        }

        for (File geneSetFile : GeneSetImportSupport.loadAvailableSetFiles(genomeModel.getSpecies())) {
            choices.add(new GeneSetChoice(genomeModel, geneSetFile));
        }
    }

    private void applySelectedGeneSet() {
        if (adjustingGeneSetSelection || geneSetComboBox == null) {
            return;
        }

        GeneSetChoice choice = getSelectedGeneSetChoice();
        if (choice == null) {
            return;
        }

        if (selectedGenome == null) {
            return;
        }

        try {
            if (choice.isRegionSet()) {
                importSelectedRegionSet(choice);
            } else {
                GeneSetImportSupport.ImportedGeneSet importedGeneSet =
                    GeneSetImportSupport.importIds(
                        choice.getSpecies(),
                        choice.file,
                        GeneSetImportSupport.OutputIdType.TRANSCRIPT
                    );
                LinkedHashSet<String> mergedIds = new LinkedHashSet<>(
                    GeneSetFileSupport.parseGeneIds(resolveHighlightGeneText(selectedGenome))
                );
                mergedIds.addAll(importedGeneSet.getIds());
                updateHighlightState(selectedGenome, GeneSetFileSupport.formatGeneIds(new ArrayList<>(mergedIds)));
            }
        } catch (Exception ex) {
            adjustingGeneSetSelection = true;
            try {
                geneSetComboBox.setSelectedIndex(0);
            } finally {
                adjustingGeneSetSelection = false;
            }

            Throwable cause = ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null
                ? ex.getCause()
                : ex;
            JOptionPane.showMessageDialog(
                this,
                "Failed to import selected Highlight Set:\n" + cause.getMessage(),
                "Import Highlight Set Failed",
                cause instanceof GeneSetImportSupport.NoGeneFoundException
                    ? JOptionPane.INFORMATION_MESSAGE
                    : JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void importSelectedRegionSet(GeneSetChoice choice) throws IOException {
        String content = GeneSetFileSupport.readGeneSetContent(choice.file);
        List<GeneSetFileSupport.RegionEntry> regionEntries = GeneSetFileSupport.parseRegionEntries(content);
        String setName = GeneSetFileSupport.extractDisplayName(choice.file);
        int addedCount = 0;

        for (GeneSetFileSupport.RegionEntry regionEntry : regionEntries) {
            SelectedHighlightRegion highlightRegion = new SelectedHighlightRegion(
                choice.genomeModel,
                choice.file,
                regionEntry.getChromosomeName(),
                regionEntry.getStartPos(),
                regionEntry.getEndPos(),
                buildRegionHighlightLabel(setName, regionEntry)
            );
            if (!selectedHighlightRegions.contains(highlightRegion)) {
                selectedHighlightRegions.add(highlightRegion);
                addedCount++;
            }
        }

        appendLogLine(String.format(
            Locale.US,
            "Imported Region Set for %s: %s (%d new / %d total regions).",
            choice.genomeModel.getGenomeTitle(),
            setName,
            addedCount,
            regionEntries.size()
        ));
    }

    private String buildRegionHighlightLabel(String setName, GeneSetFileSupport.RegionEntry regionEntry) {
        String safeSetName = setName == null || setName.trim().isEmpty() ? "RegionSet" : setName.trim();
        return safeSetName + ":"
            + regionEntry.getChromosomeName() + ":"
            + regionEntry.getStartPos() + "-"
            + regionEntry.getEndPos();
    }

    private GeneSetChoice getSelectedGeneSetChoice() {
        if (geneSetComboBox == null) {
            return null;
        }
        int selectedIndex = geneSetComboBox.getSelectedIndex();
        if (selectedIndex <= 0) {
            return null;
        }

        int choiceIndex = selectedIndex - 1;
        if (choiceIndex < 0 || choiceIndex >= availableGeneSets.size()) {
            return null;
        }
        return availableGeneSets.get(choiceIndex);
    }

    private void saveCurrentGenomeHighlightState() {
        if (selectedGenome == null || highlightGeneArea == null) {
            return;
        }
        getOrCreateHighlightState(selectedGenome).setHighlightGeneText(highlightGeneArea.getText());
    }

    private void loadSelectedGenomeHighlightState() {
        if (highlightGeneArea == null) {
            return;
        }
        if (selectedGenome == null) {
            highlightGeneArea.setText("");
            highlightGeneArea.setCaretPosition(0);
            return;
        }

        String highlightGeneText = getOrCreateHighlightState(selectedGenome).getHighlightGeneText();
        highlightGeneArea.setText(highlightGeneText);
        highlightGeneArea.setCaretPosition(0);
    }

    private GenomeHighlightState getOrCreateHighlightState(MultipleSyntenyGenomeModel genomeModel) {
        GenomeHighlightState state = highlightStateByGenome.get(genomeModel);
        if (state == null) {
            state = new GenomeHighlightState();
            highlightStateByGenome.put(genomeModel, state);
        }
        return state;
    }

    private String resolveHighlightGeneText(MultipleSyntenyGenomeModel genomeModel) {
        if (genomeModel == null) {
            return "";
        }
        if (genomeModel == selectedGenome && highlightGeneArea != null) {
            return highlightGeneArea.getText();
        }
        return getOrCreateHighlightState(genomeModel).getHighlightGeneText();
    }

    private void updateHighlightState(MultipleSyntenyGenomeModel genomeModel, String highlightGeneText) {
        if (genomeModel == null) {
            return;
        }
        getOrCreateHighlightState(genomeModel).setHighlightGeneText(highlightGeneText);
        if (genomeModel == selectedGenome && highlightGeneArea != null) {
            highlightGeneArea.setText(highlightGeneText);
            highlightGeneArea.setCaretPosition(0);
        }
    }

    private List<String> flattenHighlightGeneIds(
        Map<MultipleSyntenyGenomeModel, List<String>> highlightGeneIdsByGenome
    ) {
        LinkedHashSet<String> mergedIds = new LinkedHashSet<>();
        if (highlightGeneIdsByGenome != null) {
            for (List<String> highlightGeneIds : highlightGeneIdsByGenome.values()) {
                if (highlightGeneIds != null) {
                    mergedIds.addAll(highlightGeneIds);
                }
            }
        }
        return new ArrayList<>(mergedIds);
    }

    private Map<Integer, List<String>> convertHighlightGeneIdsBySlot(
        Map<MultipleSyntenyGenomeModel, List<String>> highlightGeneIdsByGenome
    ) {
        Map<Integer, List<String>> highlightGeneIdsBySlot = new LinkedHashMap<>();
        if (highlightGeneIdsByGenome == null) {
            return highlightGeneIdsBySlot;
        }

        for (Map.Entry<MultipleSyntenyGenomeModel, List<String>> entry : highlightGeneIdsByGenome.entrySet()) {
            MultipleSyntenyGenomeModel genomeModel = entry.getKey();
            if (genomeModel == null) {
                continue;
            }
            highlightGeneIdsBySlot.put(
                genomeModel.getSlotNumber(),
                entry.getValue() == null ? new ArrayList<>() : new ArrayList<>(entry.getValue())
            );
        }
        return highlightGeneIdsBySlot;
    }

    private static final class MultipleSyntenyRunInputs {
        private final MultipleSyntenyService.RunRequest request;

        private MultipleSyntenyRunInputs(MultipleSyntenyService.RunRequest request) {
            this.request = request;
        }
    }

    private static final class HighlightResolutionResult {
        private final List<String> resolvedIds;
        private final List<String> missingIds;
        private final GeneSetGeneIdValidationSupport.ValidationResult validationResult;

        private HighlightResolutionResult(List<String> resolvedIds, List<String> missingIds,
                                          GeneSetGeneIdValidationSupport.ValidationResult validationResult) {
            this.resolvedIds = resolvedIds;
            this.missingIds = missingIds;
            this.validationResult = validationResult;
        }
    }

    private static final class GenomeHighlightState {
        private String highlightGeneText = "";

        private String getHighlightGeneText() {
            return highlightGeneText == null ? "" : highlightGeneText;
        }

        private void setHighlightGeneText(String highlightGeneText) {
            this.highlightGeneText = highlightGeneText == null ? "" : highlightGeneText;
        }
    }

    private static final class GeneSetChoice {
        private final MultipleSyntenyGenomeModel genomeModel;
        private final File file;

        private GeneSetChoice(MultipleSyntenyGenomeModel genomeModel, File file) {
            this.genomeModel = genomeModel;
            this.file = file;
        }

        private SpeciesInfo getSpecies() {
            return genomeModel == null ? null : genomeModel.getSpecies();
        }

        private boolean isRegionSet() {
            return GeneSetFileSupport.detectSetKind(file) == GeneSetFileSupport.SetKind.REGION;
        }

        private String getDisplayLabel() {
            return GeneSetImportSupport.buildImportLabel(file);
        }
    }

    private static final class SelectedHighlightRegion {
        private final MultipleSyntenyGenomeModel genomeModel;
        private final File sourceFile;
        private final String chromosomeName;
        private final long start;
        private final long end;
        private final String label;

        private SelectedHighlightRegion(MultipleSyntenyGenomeModel genomeModel, File sourceFile,
                                        String chromosomeName, long start, long end, String label) {
            this.genomeModel = genomeModel;
            this.sourceFile = sourceFile;
            this.chromosomeName = chromosomeName == null ? "" : chromosomeName.trim();
            this.start = Math.min(start, end);
            this.end = Math.max(start, end);
            this.label = label == null ? "" : label.trim();
        }

        private boolean references(MultipleSyntenyGenomeModel genomeModel) {
            return this.genomeModel == genomeModel;
        }

        private MultipleSyntenyService.HighlightRegion toServiceRegion() {
            return new MultipleSyntenyService.HighlightRegion(
                genomeModel.getSlotNumber(),
                chromosomeName,
                start,
                end,
                label
            );
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SelectedHighlightRegion)) {
                return false;
            }
            SelectedHighlightRegion other = (SelectedHighlightRegion) obj;
            return genomeModel == other.genomeModel
                && start == other.start
                && end == other.end
                && java.util.Objects.equals(sourceFile, other.sourceFile)
                && java.util.Objects.equals(chromosomeName, other.chromosomeName)
                && java.util.Objects.equals(label, other.label);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(System.identityHashCode(genomeModel), sourceFile, chromosomeName, start, end, label);
        }
    }

    private final class SpeciesTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                      boolean expanded, boolean leaf, int row,
                                                      boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            if (value instanceof DefaultMutableTreeNode) {
                Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
                if (userObject instanceof SpeciesInfo) {
                    SpeciesInfo species = (SpeciesInfo) userObject;
                    setText(species.getSpeciesName() + " (" + species.getVersion() + ")");
                }
            }

            return this;
        }
    }

    private static final class InspectorScrollablePanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return 20;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
