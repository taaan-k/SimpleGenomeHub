/*
 * SimpleGenomeHub BLAST Results Panel
 * Displays BLAST results in a user-friendly format
 */
package simplegenomehub.gui;

import simplegenomehub.blast.model.BlastResult;
import simplegenomehub.blast.model.BlastResult.BlastHit;
import simplegenomehub.blast.model.BlastQuery;
import simplegenomehub.blast.BlastExecutor;
import simplegenomehub.config.ApplicationLayout;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.TranscriptToGeneMapper;
import biocjava.bioDoer.BLAST.BlastZone.BlastZone.FecthSeqFromDB;
import biocjava.bioDoer.JIGplotToolkit.newickParser.PhyloTreeNode;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.DefaultListSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.function.Function;
import java.io.BufferedReader;
import java.io.File;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Panel for displaying BLAST results
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastResultsPanel extends JPanel {
    private static final int DEFAULT_BATCH_SIZE = 500;
    private static final int LARGE_RESULTS_NOTICE_THRESHOLD = 2000;
    private static final int SHOW_ALL_CONFIRMATION_THRESHOLD = 10000;
    private static final String HIT_DETAILS_ID_SEPARATOR = "     ";

    private BlastResult currentResult;
    private BlastExecutor.BlastType currentBlastType;
    private File currentBlastDatabase;  // Store BLAST database path for tree building
    private TranscriptToGeneMapper transcriptToGeneMapper;
    private File transcriptToGeneMapperSourceFile;
    private JLabel summaryLabel;
    private JLabel hitRangeLabel;
    private JTable resultsTable;
    private BlastHitsTableModel tableModel;
    private JTextArea detailsArea;
    private JScrollPane detailsScrollPane;
    private JButton buildTreeButton;
    private JButton exportButton;
    private JButton loadMoreButton;
    private JButton showAllButton;
    private JComboBox<Integer> pageSizeCombo;
    private DecimalFormat scientificFormat;
    private int loadedHitCount;
    private int lastSortedColumn = -1;
    private boolean lastSortAscending = true;
    
    /**
     * Initialize results panel
     */
    public BlastResultsPanel() {
        this.scientificFormat = new DecimalFormat("0.##E0");
        initializeGUI();
    }
    
    /**
     * Initialize GUI components
     */
    private void initializeGUI() {
        setLayout(new BorderLayout());
        setBorder(new TitledBorder("BLAST Results"));
        
        // Summary panel
        JPanel summaryPanel = createSummaryPanel();
        add(summaryPanel, BorderLayout.NORTH);
        
        // Main content with split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        SimpleGenomeHubUi.styleSplitPane(splitPane);
        splitPane.setTopComponent(createResultsTablePanel());
        splitPane.setBottomComponent(createDetailsPanel());
        splitPane.setDividerLocation(300);
        splitPane.setResizeWeight(0.7);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Initially show empty state
        showEmptyState();
    }
    
    /**
     * Create summary panel
     */
    private JPanel createSummaryPanel() {
        JPanel summaryPanel = new JPanel(new BorderLayout());
        summaryPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        summaryLabel = new JLabel("BLAST Results");
        summaryLabel.setFont(SimpleGenomeHubStyle.bold(summaryLabel.getFont()));
        summaryPanel.add(summaryLabel, BorderLayout.WEST);

        hitRangeLabel = new JLabel("No hits loaded");
        hitRangeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        summaryPanel.add(hitRangeLabel, BorderLayout.CENTER);

        // Action buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        // Build Tree button (only for BLASTP)
        buildTreeButton = new JButton("Build a Tree with Top N Hits");
        buildTreeButton.addActionListener(new BuildTreeActionListener());
        buildTreeButton.setEnabled(false);
        buildTreeButton.setToolTipText("Build phylogenetic tree with query and top N hit sequences (BLASTP only)");
        actionPanel.add(buildTreeButton);

        exportButton = new JButton("Export");
        exportButton.addActionListener(new ExportActionListener());
        exportButton.setEnabled(false);
        actionPanel.add(exportButton);

        summaryPanel.add(actionPanel, BorderLayout.EAST);
        
        return summaryPanel;
    }
    
    /**
     * Create results table panel
     */
    private JPanel createResultsTablePanel() {
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(new TitledBorder("Hit List"));
        
        // Create table
        tableModel = new BlastHitsTableModel();
        resultsTable = new JTable(tableModel);
        
        // Enable both row and column selection for cell-level selection
        resultsTable.setRowSelectionAllowed(true);
        resultsTable.setColumnSelectionAllowed(true);
        resultsTable.setCellSelectionEnabled(true);
        resultsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resultsTable.getColumnModel().setSelectionModel(new DefaultListSelectionModel());
        resultsTable.getColumnModel().getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        
        // Set light orange selection colors
        resultsTable.setSelectionBackground(new Color(255, 239, 213)); // Light orange
        resultsTable.setSelectionForeground(Color.BLACK); // Keep text black for readability
        
        // Set column widths
        setColumnWidths();
        
        // Set cell renderers
        setCellRenderers();
        
        // Add selection listener for both row and column selections
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showHitDetails();
            }
        });
        
        // Add column selection listener
        resultsTable.getColumnModel().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                // Column selection changed, but we still show hit details for the first selected row
                showHitDetails();
            }
        });
        
        // Ensure table can receive focus
        resultsTable.setFocusable(true);
        resultsTable.setRequestFocusEnabled(true);

        // Apply full-result sorting via header clicks
        setupHeaderSorting();
        
        // Add right-click context menu for columns
        setupColumnContextMenu();
        
        JScrollPane tableScrollPane = new JScrollPane(resultsTable);
        tableScrollPane.setPreferredSize(new Dimension(800, 300));
        tablePanel.add(tableScrollPane, BorderLayout.CENTER);
        tablePanel.add(createTableControlsPanel(), BorderLayout.SOUTH);
        
        return tablePanel;
    }

    private JPanel createTableControlsPanel() {
        JPanel controlsPanel = new JPanel(new BorderLayout());
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));

        JLabel pageSizeLabel = new JLabel("Batch Size:");
        pageSizeCombo = new JComboBox<>(new Integer[]{100, 500, 1000});
        pageSizeCombo.setSelectedItem(DEFAULT_BATCH_SIZE);
        SimpleGenomeHubUi.setComboBoxDisplayWidth(pageSizeCombo, 100);
        pageSizeCombo.addActionListener(e -> {
            if (currentResult != null) {
                loadedHitCount = Math.min(currentResult.getTotalHits(), getSelectedBatchSize());
                refreshVisibleHits(true);
            }
        });

        loadMoreButton = new JButton("Load More");
        loadMoreButton.addActionListener(e -> loadMoreHits());

        showAllButton = new JButton("Show All");
        showAllButton.addActionListener(e -> showAllHits());

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightControls.add(pageSizeLabel);
        rightControls.add(pageSizeCombo);
        rightControls.add(loadMoreButton);
        rightControls.add(showAllButton);

        controlsPanel.add(rightControls, BorderLayout.EAST);
        return controlsPanel;
    }
    
    /**
     * Create details panel
     */
    private JPanel createDetailsPanel() {
        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.setBorder(new TitledBorder("Hit Details"));
        
        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        detailsArea.setText("Select a hit to view detailed information...");
        
        detailsScrollPane = new JScrollPane(detailsArea);
        detailsScrollPane.setPreferredSize(new Dimension(800, 200));
        detailsPanel.add(detailsScrollPane, BorderLayout.CENTER);
        
        return detailsPanel;
    }
    
    /**
     * Set column widths
     */
    private void setColumnWidths() {
        int[] columnWidths = {40, 120, 180, 160, 80, 80, 80, 80, 80, 80, 80, 80};
        for (int i = 0; i < columnWidths.length && i < resultsTable.getColumnCount(); i++) {
            resultsTable.getColumnModel().getColumn(i).setPreferredWidth(columnWidths[i]);
        }
    }
    
    /**
     * Set cell renderers
     */
    private void setCellRenderers() {
        // Scientific notation renderer for E-value
        DefaultTableCellRenderer scientificRenderer = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                if (value instanceof Double) {
                    setText(scientificFormat.format(value));
                } else {
                    super.setValue(value);
                }
            }
        };
        scientificRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        resultsTable.getColumnModel().getColumn(5).setCellRenderer(scientificRenderer);
        
        // Percentage renderer
        DefaultTableCellRenderer percentageRenderer = new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                if (value instanceof Double) {
                    setText(String.format("%.1f%%", (Double) value));
                } else {
                    super.setValue(value);
                }
            }
        };
        percentageRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        resultsTable.getColumnModel().getColumn(6).setCellRenderer(percentageRenderer);
        
        // Right-aligned renderer for numbers
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 4; i < resultsTable.getColumnCount(); i++) {
            if (i != 5 && i != 6) {
                resultsTable.getColumnModel().getColumn(i).setCellRenderer(rightRenderer);
            }
        }
    }
    
    /**
     * Display BLAST results with blast type information and database path
     */
    public void setResults(BlastResult result, BlastExecutor.BlastType blastType, File blastDatabase) {
        this.currentResult = result;
        this.currentBlastType = blastType;
        this.currentBlastDatabase = blastDatabase;
        this.transcriptToGeneMapper = null;
        this.transcriptToGeneMapperSourceFile = null;
        this.lastSortedColumn = -1;
        this.lastSortAscending = true;

        if (result == null) {
            showEmptyState();
            return;
        }

        // Enable buttons based on results and blast type
        boolean hasResults = result.getTotalHits() > 0;
        exportButton.setEnabled(hasResults);

        // Build Tree only for BLASTP with results
        buildTreeButton.setEnabled(hasResults && blastType == BlastExecutor.BlastType.BLASTP);

        // Clear details
        detailsArea.setText("Select a hit to view detailed information...");

        loadedHitCount = Math.min(result.getTotalHits(), getSelectedBatchSize());
        refreshVisibleHits(true);

        if (result.getTotalHits() > LARGE_RESULTS_NOTICE_THRESHOLD) {
            JOptionPane.showMessageDialog(this,
                "Large BLAST result detected (" + result.getTotalHits() + " hits).\n" +
                "The table is using incremental loading to keep memory usage and UI latency under control.",
                "Large Result Set",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Display BLAST results with blast type information (legacy method)
     */
    public void setResults(BlastResult result, BlastExecutor.BlastType blastType) {
        setResults(result, blastType, null);
    }

    /**
     * Display BLAST results (legacy method)
     */
    public void displayResults(BlastResult result) {
        // Default to unknown blast type - disables Build Tree button
        setResults(result, null, null);
    }
    
    /**
     * Show empty state
     */
    private void showEmptyState() {
        summaryLabel.setText("Waiting for BLAST results...");
        tableModel.setHits(new ArrayList<>());
        detailsArea.setText("Waiting for BLAST results...");
        hitRangeLabel.setText("No hits loaded");
        exportButton.setEnabled(false);
        buildTreeButton.setEnabled(false);
        loadMoreButton.setEnabled(false);
        showAllButton.setEnabled(false);
        loadedHitCount = 0;
    }
    
    /**
     * Add some test data for debugging table interaction
     */
    public void addTestData() {
        List<BlastHit> testHits = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            BlastHit hit = new BlastHit();
            hit.setQueryId("Test_Query_" + i);
            hit.setHitId("Test_Hit_" + i);
            hit.setHitDef("Test description " + i);
            hit.setHitLength(100 + i * 10);
            hit.setBitScore(50.0 + i * 10);
            hit.setEvalue(Math.pow(10, -i));
            hit.setIdentity(80 + i);
            hit.setPositives(85 + i);
            hit.setAlignLength(100);
            hit.setQueryStart(1);
            hit.setQueryEnd(100);
            hit.setSubjectStart(i * 10);
            hit.setSubjectEnd(i * 10 + 100);
            testHits.add(hit);
        }
        tableModel.setHits(testHits);
        loadedHitCount = testHits.size();
        exportButton.setEnabled(true);
        summaryLabel.setText("Test data loaded - 5 hits");
        hitRangeLabel.setText("Showing 1-5 of 5 hits");
    }
    
    /**
     * Update summary information
     */
    private void updateSummary(BlastResult result) {
        if (result.isSuccessful()) {
            summaryLabel.setText("BLAST Type: " + result.getBlastType() + 
                               " | Hits: " + result.getTotalHits() + 
                               " | Duration: " + result.getExecutionDurationMs() + "ms");
            int totalHits = result.getTotalHits();
            if (totalHits == 0) {
                hitRangeLabel.setText("No hits loaded");
            } else {
                hitRangeLabel.setText("Showing 1-" + loadedHitCount + " of " + totalHits + " hits");
            }
        } else {
            summaryLabel.setText("BLAST execution failed: " + result.getErrorMessage());
            hitRangeLabel.setText("No hits loaded");
        }
    }

    private int getSelectedBatchSize() {
        Integer selectedValue = pageSizeCombo != null ? (Integer) pageSizeCombo.getSelectedItem() : null;
        return selectedValue != null ? selectedValue : DEFAULT_BATCH_SIZE;
    }

    private void refreshVisibleHits(boolean resetSelection) {
        if (currentResult == null) {
            tableModel.setHits(new ArrayList<>());
            summaryLabel.setText("Waiting for BLAST results...");
            hitRangeLabel.setText("No hits loaded");
            updateLoadButtons();
            return;
        }

        int totalHits = currentResult.getTotalHits();
        loadedHitCount = Math.min(Math.max(loadedHitCount, 0), totalHits);
        List<BlastHit> visibleHits = currentResult.getHitsPage(0, loadedHitCount);
        tableModel.setHits(visibleHits);
        updateSummary(currentResult);
        updateLoadButtons();

        if (resetSelection) {
            if (!visibleHits.isEmpty()) {
                resultsTable.setRowSelectionInterval(0, 0);
            } else {
                resultsTable.clearSelection();
                detailsArea.setText("Select a hit to view detailed information...");
            }
        }
    }

    private void updateLoadButtons() {
        int totalHits = currentResult != null ? currentResult.getTotalHits() : 0;
        boolean canLoadMore = currentResult != null && loadedHitCount < totalHits;
        loadMoreButton.setEnabled(canLoadMore);
        showAllButton.setEnabled(canLoadMore);
    }

    private void loadMoreHits() {
        if (currentResult == null) {
            return;
        }
        loadedHitCount = Math.min(currentResult.getTotalHits(), loadedHitCount + getSelectedBatchSize());
        refreshVisibleHits(false);
    }

    private void showAllHits() {
        if (currentResult == null) {
            return;
        }

        int totalHits = currentResult.getTotalHits();
        if (totalHits > SHOW_ALL_CONFIRMATION_THRESHOLD) {
            int choice = JOptionPane.showConfirmDialog(
                this,
                "Showing all " + totalHits + " hits may reduce UI responsiveness.\nDo you want to continue?",
                "Show All Hits",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        loadedHitCount = totalHits;
        refreshVisibleHits(false);
    }

    private void setupHeaderSorting() {
        JTableHeader header = resultsTable.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || currentResult == null) {
                    return;
                }

                int viewColumn = resultsTable.columnAtPoint(e.getPoint());
                if (viewColumn < 0) {
                    return;
                }

                boolean ascending = (lastSortedColumn == viewColumn) ? !lastSortAscending : true;
                sortColumn(viewColumn, ascending);
            }
        });
    }
    
    /**
     * Setup column context menu for right-click operations
     */
    private void setupColumnContextMenu() {
        resultsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showColumnContextMenu(e);
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showColumnContextMenu(e);
                }
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showColumnContextMenu(e);
                }
            }
        });
    }
    
    /**
     * Show context menu for column operations
     */
    private void showColumnContextMenu(MouseEvent e) {
        int column = resultsTable.columnAtPoint(e.getPoint());
        if (column >= 0) {
            int row = resultsTable.rowAtPoint(e.getPoint());
            if (row >= 0 && column >= 0 && !resultsTable.isCellSelected(row, column)) {
                resultsTable.changeSelection(row, column, false, false);
            }

            JPopupMenu contextMenu = new JPopupMenu();

            JMenuItem copySelectedItem = new JMenuItem("Copy Select Content");
            copySelectedItem.addActionListener(ev -> copySelectedTableContent());
            copySelectedItem.setEnabled(resultsTable.getSelectedRowCount() > 0 && resultsTable.getSelectedColumnCount() > 0);
            contextMenu.add(copySelectedItem);
            
            if (column > 0) {
                contextMenu.addSeparator();

                JMenuItem removeDuplicatesItem = new JMenuItem("Remove Duplicates");
                removeDuplicatesItem.addActionListener(ev -> removeDuplicatesInColumn(column));
                contextMenu.add(removeDuplicatesItem);

                contextMenu.addSeparator();

                JMenuItem sortAscItem = new JMenuItem("Sort Ascending (A-Z, 0-9)");
                sortAscItem.addActionListener(ev -> sortColumn(column, true));
                contextMenu.add(sortAscItem);

                JMenuItem sortDescItem = new JMenuItem("Sort Descending (Z-A, 9-0)");
                sortDescItem.addActionListener(ev -> sortColumn(column, false));
                contextMenu.add(sortDescItem);
            }
            
            contextMenu.show(resultsTable, e.getX(), e.getY());
        }
    }

    private void copySelectedTableContent() {
        int[] selectedRows = resultsTable.getSelectedRows();
        int[] selectedColumns = resultsTable.getSelectedColumns();
        if (selectedRows.length == 0 || selectedColumns.length == 0) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int rowIndex = 0; rowIndex < selectedRows.length; rowIndex++) {
            if (rowIndex > 0) {
                builder.append('\n');
            }

            for (int columnIndex = 0; columnIndex < selectedColumns.length; columnIndex++) {
                if (columnIndex > 0) {
                    builder.append('\t');
                }

                Object value = resultsTable.getValueAt(selectedRows[rowIndex], selectedColumns[columnIndex]);
                if (value != null) {
                    builder.append(value);
                }
            }
        }

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
            new StringSelection(builder.toString()),
            null
        );
    }
    
    /**
     * Remove duplicates in specified column
     */
    private void removeDuplicatesInColumn(int column) {
        if (currentResult == null) {
            return;
        }
        if (column == 0) {
            JOptionPane.showMessageDialog(this,
                "Duplicate removal is not applicable to the row number column.",
                "Remove Duplicates",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<BlastHit> currentHits = currentResult.getHits();
        Set<String> seenValues = new HashSet<>();
        List<BlastHit> uniqueHits = new ArrayList<>();
        
        for (BlastHit hit : currentHits) {
            String columnValue = getColumnValue(hit, column);
            if (!seenValues.contains(columnValue)) {
                seenValues.add(columnValue);
                uniqueHits.add(hit);
            }
        }
        
        currentResult.setHits(uniqueHits);
        loadedHitCount = Math.min(Math.max(getSelectedBatchSize(), loadedHitCount), uniqueHits.size());
        refreshVisibleHits(true);
        
        JOptionPane.showMessageDialog(this, 
            "Removed " + (currentHits.size() - uniqueHits.size()) + " duplicate entries.",
            "Remove Duplicates", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Sort column in specified order using TableRowSorter (same as header click)
     */
    private void sortColumn(int column, boolean ascending) {
        if (currentResult == null || column == 0) {
            return;
        }

        List<BlastHit> sortedHits = currentResult.getHits();
        Comparator<BlastHit> comparator = buildHitComparator(column);
        if (comparator == null) {
            return;
        }
        if (!ascending) {
            comparator = comparator.reversed();
        }

        sortedHits.sort(comparator);
        currentResult.setHits(sortedHits);
        lastSortedColumn = column;
        lastSortAscending = ascending;
        refreshVisibleHits(true);
    }
    
    /**
     * Get column value as string for sorting/duplicate removal
     */
    private String getColumnValue(BlastHit hit, int column) {
        Object value = tableModel.getValueForHit(hit, column);
        return value != null ? value.toString() : "";
    }

    private String getQueryIdForHit(BlastHit hit) {
        if (hit.getQueryId() != null && !hit.getQueryId().isEmpty()) {
            return hit.getQueryId();
        }
        if (currentResult != null && currentResult.getQueryId() != null) {
            return currentResult.getQueryId();
        }
        return "Query_1";
    }

    private Comparator<BlastHit> buildHitComparator(int column) {
        switch (column) {
            case 1:
                return Comparator.comparing(this::getQueryIdForHit, String.CASE_INSENSITIVE_ORDER);
            case 2:
                return Comparator.comparing(this::getHitTranscriptId, String.CASE_INSENSITIVE_ORDER);
            case 3:
                return Comparator.comparing(this::getHitGeneId, String.CASE_INSENSITIVE_ORDER);
            case 4:
                return Comparator.comparingDouble(BlastHit::getBitScore);
            case 5:
                return Comparator.comparingDouble(BlastHit::getEvalue);
            case 6:
                return Comparator.comparingDouble(BlastHit::getIdentityPercentage);
            case 7:
                return Comparator.comparingInt(BlastHit::getHitLength);
            case 8:
                return Comparator.comparingInt(BlastHit::getQueryStart);
            case 9:
                return Comparator.comparingInt(BlastHit::getQueryEnd);
            case 10:
                return Comparator.comparingInt(BlastHit::getSubjectStart);
            case 11:
                return Comparator.comparingInt(BlastHit::getSubjectEnd);
            default:
                return null;
        }
    }
    
    /**
     * Show details for selected hit
     */
    private void showHitDetails() {
        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow >= 0 && selectedRow < resultsTable.getRowCount()) {
            BlastHit hit = tableModel.getHitAt(selectedRow);
            displayHitDetails(hit);
        }
    }
    
    /**
     * Display details for a specific hit
     */
    private void displayHitDetails(BlastHit hit) {
        if (currentResult == null || currentResult.getResultFile() == null) {
            detailsArea.setText("No detailed alignment information available.");
            return;
        }
        
        try {
            // Generate pairwise alignment for this specific hit using TBtools
            String pairwiseAlignment = generatePairwiseAlignment(hit);
            detailsArea.setText(buildHitDetailsHeader(hit) + "\n\n" + pairwiseAlignment);
            detailsArea.setCaretPosition(0);
        } catch (Exception e) {
            // Fallback to simple details if pairwise generation fails
            displaySimpleHitDetails(hit);
        }
    }
    
    /**
     * Generate pairwise alignment for a specific hit using TBtools
     */
    private String generatePairwiseAlignment(BlastHit hit) throws Exception {
        // Create a temporary file for the pairwise output
        java.io.File tempPairwiseFile = java.io.File.createTempFile("blast_pairwise_", ".txt");
        tempPairwiseFile.deleteOnExit();
        
        // Use TBtools to convert XML to pairwise format
        biocjava.bioIO.BlastXml.BlastXMLToPairwise.parse(currentResult.getResultFile(), tempPairwiseFile);
        
        // Read the pairwise file and extract information for the specific hit
        StringBuilder pairwiseContent = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(tempPairwiseFile))) {
            String line;
            boolean foundHit = false;
            boolean inHitSection = false;
            
            while ((line = reader.readLine()) != null) {
                // Look for the specific hit by ID
                if (line.startsWith("> ") && line.contains(hit.getHitId())) {
                    foundHit = true;
                    inHitSection = true;
                    pairwiseContent.append(line).append("\n");
                } else if (line.startsWith("> ") && foundHit) {
                    // We've reached another hit, stop reading
                    break;
                } else if (inHitSection) {
                    pairwiseContent.append(line).append("\n");
                }
            }
        }
        
        if (pairwiseContent.length() == 0) {
            throw new Exception("Hit not found in pairwise output");
        }
        
        return pairwiseContent.toString();
    }
    
    /**
     * Fallback method to display simple hit details
     */
    private void displaySimpleHitDetails(BlastHit hit) {
        StringBuilder details = new StringBuilder();
        
        details.append(buildHitDetailsHeader(hit)).append("\n\n");
        details.append("Hit mRNA ID: ").append(getHitTranscriptId(hit)).append("\n");
        details.append("Hit Gene ID: ").append(getHitGeneId(hit)).append("\n");
        details.append("Description: ").append(hit.getHitDef()).append("\n");
        details.append("Length: ").append(hit.getHitLength()).append(" aa/bp\n");
        details.append("Bit Score: ").append(String.format("%.1f", hit.getBitScore())).append("\n");
        details.append("E-value: ").append(scientificFormat.format(hit.getEvalue())).append("\n");
        details.append("Identity: ").append(hit.getIdentity()).append("/").append(hit.getAlignLength());
        details.append(" (").append(String.format("%.1f%%", hit.getIdentityPercentage())).append(")\n");
        details.append("Positives: ").append(hit.getPositives()).append("/").append(hit.getAlignLength());
        details.append(" (").append(String.format("%.1f%%", hit.getPositivePercentage())).append(")\n");
        details.append("Query Range: ").append(hit.getQueryStart()).append("-").append(hit.getQueryEnd()).append("\n");
        details.append("Subject Range: ").append(hit.getSubjectStart()).append("-").append(hit.getSubjectEnd()).append("\n\n");
        
        // Add alignment if available
        if (hit.getAlignment() != null && !hit.getAlignment().isEmpty() && !hit.getAlignment().equals("-")) {
            details.append("Alignment:\n");
            details.append("Query:   ").append(hit.getQuerySeq()).append("\n");
            details.append("         ").append(hit.getAlignment()).append("\n");
            details.append("Subject: ").append(hit.getSubjectSeq()).append("\n");
        } else {
            details.append("Note: Detailed alignment information requires XML parsing.\n");
        }
        
        detailsArea.setText(details.toString());
        detailsArea.setCaretPosition(0);
    }

    private String buildHitDetailsHeader(BlastHit hit) {
        String transcriptId = getHitTranscriptId(hit);
        String parentGeneId = resolveParentGeneId(transcriptId);
        return "Transcript_ID=" + transcriptId + HIT_DETAILS_ID_SEPARATOR + "Parent_Gene_ID=" + parentGeneId;
    }

    private String getHitTranscriptId(BlastHit hit) {
        return normalizeTranscriptId(hit != null ? hit.getHitId() : null);
    }

    private String getHitGeneId(BlastHit hit) {
        return resolveParentGeneId(getHitTranscriptId(hit));
    }

    private String normalizeTranscriptId(String hitId) {
        if (hitId == null || hitId.trim().isEmpty()) {
            return "N/A";
        }

        String normalizedId = hitId.trim();
        int commaIndex = normalizedId.indexOf(',');
        if (commaIndex >= 0) {
            normalizedId = normalizedId.substring(0, commaIndex).trim();
        }

        String[] suffixes = {"_protein", "_cds", "_CDS", "-Protein"};
        for (String suffix : suffixes) {
            if (normalizedId.endsWith(suffix) && normalizedId.length() > suffix.length()) {
                normalizedId = normalizedId.substring(0, normalizedId.length() - suffix.length());
                break;
            }
        }

        return normalizedId;
    }

    private String resolveParentGeneId(String transcriptId) {
        if (transcriptId == null || transcriptId.trim().isEmpty() || "N/A".equals(transcriptId)) {
            return "N/A";
        }

        TranscriptToGeneMapper mapper = getTranscriptToGeneMapper();
        if (mapper == null) {
            return transcriptId;
        }

        String parentGeneId = mapper.mapTranscriptToGene(transcriptId);
        if (parentGeneId == null || parentGeneId.trim().isEmpty()) {
            return transcriptId;
        }

        return parentGeneId.trim();
    }

    private TranscriptToGeneMapper getTranscriptToGeneMapper() {
        File annotationFile = getCurrentAnnotationFile();
        if (transcriptToGeneMapper != null &&
                ((annotationFile == null && transcriptToGeneMapperSourceFile == null) ||
                 (annotationFile != null && annotationFile.equals(transcriptToGeneMapperSourceFile)))) {
            return transcriptToGeneMapper;
        }

        TranscriptToGeneMapper mapper = new TranscriptToGeneMapper();
        if (annotationFile != null && annotationFile.exists()) {
            mapper.loadFromGFF3(annotationFile);
        }

        transcriptToGeneMapper = mapper;
        transcriptToGeneMapperSourceFile = annotationFile;
        return transcriptToGeneMapper;
    }

    private File getCurrentAnnotationFile() {
        if (currentResult == null) {
            return null;
        }

        BlastQuery originalQuery = currentResult.getOriginalQuery();
        if (originalQuery == null) {
            return null;
        }

        SpeciesInfo targetSpecies = originalQuery.getTargetSpecies();
        if (targetSpecies == null) {
            return null;
        }

        return targetSpecies.getAnnotationFile();
    }


    /**
     * Build Tree action listener
     */
    private class BuildTreeActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (currentResult == null || currentResult.getHits().isEmpty()) {
                JOptionPane.showMessageDialog(BlastResultsPanel.this,
                    "No BLAST results available for tree building.",
                    "No Results", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (currentBlastType != BlastExecutor.BlastType.BLASTP) {
                JOptionPane.showMessageDialog(BlastResultsPanel.this,
                    "Phylogenetic tree building is only supported for protein BLAST (BLASTP).",
                    "Invalid BLAST Type", JOptionPane.WARNING_MESSAGE);
                return;
            }

            buildPhylogeneticTree();
        }
    }

    /**
     * Build phylogenetic tree with top N hits
     */
    private void buildPhylogeneticTree() {
        List<BlastHit> allHits = currentResult.getHits();
        String treeQueryId = resolveTreeQueryId(allHits);
        List<BlastHit> queryHits = filterHitsByQueryId(allHits, treeQueryId);
        if (queryHits.isEmpty()) {
            JOptionPane.showMessageDialog(BlastResultsPanel.this,
                "No hits available for the selected query.",
                "No Results", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Step 1: Calculate unique hits count (deduplicate by Hit ID)
        Set<String> uniqueHitIds = new HashSet<>();
        for (BlastHit hit : queryHits) {
            uniqueHitIds.add(hit.getHitId());
        }
        int maxUniqueHits = uniqueHitIds.size();

        // Show input dialog with unique hits count
        String input = (String) JOptionPane.showInputDialog(
            BlastResultsPanel.this,
            "Enter number of top hits to include in tree\n" +
            "(Query ID: " + treeQueryId + ", Total hits: " + queryHits.size() + ", Unique hits: " + maxUniqueHits + "):",
            "Build Phylogenetic Tree",
            JOptionPane.QUESTION_MESSAGE,
            null,
            null,
            "10"  // Default value
        );

        if (input == null) {
            return;  // User cancelled
        }

        int n;
        try {
            n = Integer.parseInt(input);
            if (n <= 0 || n > maxUniqueHits) {
                JOptionPane.showMessageDialog(BlastResultsPanel.this,
                    "Please enter a valid number between 1 and " + maxUniqueHits,
                    "Invalid Input", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(BlastResultsPanel.this,
                "Please enter a valid number.",
                "Invalid Input", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Step 2: Execute tree building in background
        final String selectedQueryId = treeQueryId;
        SwingWorker<File, Void> worker = new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                return executeTreeBuilding(n, selectedQueryId);
            }

            @Override
            protected void done() {
                try {
                    File treeFile = get();
                    if (treeFile != null && treeFile.exists()) {
                        displayTree(treeFile, selectedQueryId, n);
                    } else {
                        JOptionPane.showMessageDialog(BlastResultsPanel.this,
                            "Failed to build phylogenetic tree.",
                            "Tree Building Failed", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(BlastResultsPanel.this,
                        "Error building tree: " + e.getMessage(),
                        "Tree Building Error", JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
            }
        };

        worker.execute();
    }

    /**
     * Execute tree building with top N hits
     */
    private File executeTreeBuilding(int n, String treeQueryId) throws Exception {
        List<BlastHit> allHits = currentResult.getHits();
        List<BlastHit> queryHits = filterHitsByQueryId(allHits, treeQueryId);
        if (queryHits.isEmpty()) {
            throw new Exception("No BLAST hits found for query ID: " + treeQueryId);
        }

        FastaRecord queryRecord = resolveQuerySequenceRecord(treeQueryId);

        // Get all hits and deduplicate by Hit ID (keep first occurrence from top)
        Set<String> seenHitIds = new HashSet<>();
        List<BlastHit> uniqueHits = new ArrayList<>();

        for (BlastHit hit : queryHits) {
            String hitId = hit.getHitId();
            if (!seenHitIds.contains(hitId)) {
                seenHitIds.add(hitId);
                uniqueHits.add(hit);
            }
        }

        // Get top N unique hits
        int actualN = Math.min(n, uniqueHits.size());
        List<BlastHit> topHits = uniqueHits.subList(0, actualN);

        System.err.println("DEBUG: Tree query ID: " + treeQueryId);
        System.err.println("DEBUG: Tree query sequence ID: " + queryRecord.id);
        System.err.println("DEBUG: Total hits: " + allHits.size() +
                         ", Query-specific hits: " + queryHits.size() +
                         ", Unique hits: " + uniqueHits.size() +
                         ", Selected top N: " + actualN);

        // Create temporary files
        File tmpIdFile = File.createTempFile("SimpleGenomeHub_hit_ids_", ".txt");
        tmpIdFile.deleteOnExit();
        File tmpHitSeq = File.createTempFile("SimpleGenomeHub_hit_seq_", ".fasta");
        tmpHitSeq.deleteOnExit();
        File tmpFasta = File.createTempFile("SimpleGenomeHub_blast_tree_", ".fasta");
        tmpFasta.deleteOnExit();

        // Write hit IDs to file for FecthSeqFromDB
        try (PrintWriter idWriter = new PrintWriter(new FileWriter(tmpIdFile))) {
            for (BlastHit hit : topHits) {
                idWriter.println(hit.getHitId());
            }
        }

        // Extract hit sequences from BLAST database using TBtools FecthSeqFromDB
        if (currentBlastDatabase != null) {
            File dbFile = currentBlastDatabase;
            System.err.println("DEBUG: Database path: " + dbFile.getAbsolutePath());

            // Check if at least one database file exists (e.g., .pin for protein, .nin for nucleotide)
            File dbDir = dbFile.getParentFile();
            String dbName = dbFile.getName();
            boolean dbExists = false;

            if (dbDir != null && dbDir.exists()) {
                System.err.println("DEBUG: Database directory exists: " + dbDir.getAbsolutePath());
                System.err.println("DEBUG: Database name: " + dbName);

                // Check for protein database files (.pin)
                File pinFile = new File(dbDir, dbName + ".pin");
                // Check for nucleotide database files (.nin)
                File ninFile = new File(dbDir, dbName + ".nin");

                System.err.println("DEBUG: Checking .pin file: " + pinFile.getAbsolutePath() + " exists=" + pinFile.exists());
                System.err.println("DEBUG: Checking .nin file: " + ninFile.getAbsolutePath() + " exists=" + ninFile.exists());

                dbExists = pinFile.exists() || ninFile.exists();
            } else {
                System.err.println("DEBUG: Database directory does NOT exist or is null");
            }

            if (dbExists) {
                FecthSeqFromDB fetcher = new FecthSeqFromDB();
                fetcher.setDbFile(dbFile);
                fetcher.setInFile(tmpIdFile);
                fetcher.setOutFile(tmpHitSeq);
                fetcher.process();
                ensureNonEmptyFile(tmpHitSeq, "Extracted hit sequence FASTA");
            } else {
                throw new Exception("Target database files not found at: " + dbFile.getAbsolutePath() +
                                  "\nPlease ensure BLAST database was created successfully.");
            }
        } else {
            throw new Exception("BLAST database path not set. Please run BLAST search first.");
        }

        // Combine query + hit sequences into final FASTA file
        List<FastaRecord> hitRecords = readFastaRecords(tmpHitSeq, "Extracted hit sequence FASTA");
        List<FastaRecord> orderedHitRecords = orderHitRecords(topHits, hitRecords);
        System.err.println("DEBUG: Extracted hit FASTA records: " + hitRecords.size());
        try (PrintWriter writer = new PrintWriter(new FileWriter(tmpFasta))) {
            writer.println(">" + queryRecord.id);
            writer.println(queryRecord.sequence);

            for (FastaRecord hitRecord : orderedHitRecords) {
                writer.println(">" + hitRecord.id);
                writer.println(hitRecord.sequence);
            }
        }

        int sequenceCount = 1 + orderedHitRecords.size();
        ensureNonEmptyFile(tmpFasta, "Combined tree input FASTA");
        System.err.println("DEBUG: Combined tree FASTA: " + tmpFasta.getAbsolutePath());

        File treeWorkDir = createTreeWorkDirectory();
        File alnFile = new File(treeWorkDir, "blast_tree.aln");
        File trimmedFile = new File(treeWorkDir, "blast_tree.trimAl.fa");
        File iqtreePrefix = new File(treeWorkDir, "blast_tree");
        File treeFile = new File(iqtreePrefix.getAbsolutePath() + ".treefile");

        System.err.println("DEBUG: Tree working directory: " + treeWorkDir.getAbsolutePath());

        Exception muscle5Error = null;
        try {
            runTreeCommand(
                Arrays.asList(
                    resolveTreeExecutable("muscle5"),
                    "-align", tmpFasta.getAbsolutePath(),
                    "-output", alnFile.getAbsolutePath()
                ),
                treeWorkDir,
                alnFile,
                "MUSCLE5 alignment"
            );
        } catch (Exception ex) {
            muscle5Error = ex;
            System.err.println("DEBUG: MUSCLE5 alignment failed, falling back to MUSCLE. " + ex.getMessage());
        }

        if (!isUsableFile(alnFile)) {
            try {
                runTreeCommand(
                    Arrays.asList(
                        resolveTreeExecutable("muscle"),
                        "-in", tmpFasta.getAbsolutePath(),
                        "-out", alnFile.getAbsolutePath()
                    ),
                    treeWorkDir,
                    alnFile,
                    "MUSCLE alignment"
                );
            } catch (Exception ex) {
                if (muscle5Error != null) {
                    throw new Exception(
                        "Failed to generate multiple alignment.\n"
                        + "MUSCLE5 error: " + muscle5Error.getMessage() + "\n"
                        + "MUSCLE error: " + ex.getMessage(),
                        ex
                    );
                }
                throw ex;
            }
        }

        runTreeCommand(
            Arrays.asList(
                resolveTreeExecutable("trimal"),
                "-in", alnFile.getAbsolutePath(),
                "-out", trimmedFile.getAbsolutePath(),
                "-fasta",
                "-automated1",
                "-keepheader"
            ),
            treeWorkDir,
            trimmedFile,
            "trimAl"
        );

        List<String> iqtreeCommand = new ArrayList<>(Arrays.asList(
            resolveTreeExecutable("iqtree"),
            "-s", trimmedFile.getAbsolutePath(),
            "-pre", iqtreePrefix.getName(),
            "-m", "MFP",
            "-redo",
            "-nt", "2"
        ));
        if (sequenceCount >= 4) {
            iqtreeCommand.add("-bb");
            iqtreeCommand.add("1000");
            iqtreeCommand.add("-bnni");
        }

        runTreeCommand(iqtreeCommand, treeWorkDir, treeFile, "IQ-TREE");
        return treeFile;
    }

    private String resolveTreeQueryId(List<BlastHit> allHits) {
        int selectedRow = resultsTable != null ? resultsTable.getSelectedRow() : -1;
        if (selectedRow >= 0 && selectedRow < tableModel.getRowCount()) {
            BlastHit selectedHit = tableModel.getHitAt(selectedRow);
            String selectedQueryId = normalizeFastaIdentifier(selectedHit.getQueryId());
            if (!selectedQueryId.isEmpty()) {
                return selectedQueryId;
            }
        }

        for (BlastHit hit : allHits) {
            String queryId = normalizeFastaIdentifier(hit.getQueryId());
            if (!queryId.isEmpty()) {
                return queryId;
            }
        }

        return normalizeFastaIdentifier(currentResult.getOriginalQueryId());
    }

    private List<BlastHit> filterHitsByQueryId(List<BlastHit> allHits, String treeQueryId) {
        String normalizedQueryId = normalizeFastaIdentifier(treeQueryId);
        if (normalizedQueryId.isEmpty()) {
            return new ArrayList<>(allHits);
        }

        List<BlastHit> filteredHits = new ArrayList<>();
        for (BlastHit hit : allHits) {
            if (normalizedQueryId.equals(normalizeFastaIdentifier(hit.getQueryId()))) {
                filteredHits.add(hit);
            }
        }
        return filteredHits;
    }

    private FastaRecord resolveQuerySequenceRecord(String treeQueryId) throws Exception {
        String normalizedTreeQueryId = normalizeFastaIdentifier(treeQueryId);
        BlastQuery originalQuery = currentResult.getOriginalQuery();

        if (originalQuery == null) {
            List<FastaRecord> fallbackRecords = parseFastaText(
                currentResult.getQuerySequence(),
                normalizedTreeQueryId,
                "BLAST query sequence"
            );
            if (fallbackRecords.isEmpty()) {
                throw new Exception("BLAST query sequence is empty.");
            }
            return fallbackRecords.get(0);
        }

        List<FastaRecord> queryRecords = parseFastaText(
            originalQuery.getQuerySequence(),
            normalizedTreeQueryId,
            "BLAST query input"
        );
        if (queryRecords.isEmpty()) {
            throw new Exception("BLAST query input does not contain any sequences.");
        }

        for (FastaRecord record : queryRecords) {
            if (normalizedTreeQueryId.equals(normalizeFastaIdentifier(record.id))) {
                return record;
            }
        }

        if (queryRecords.size() == 1) {
            return queryRecords.get(0);
        }

        throw new Exception(
            "Could not find query sequence for query ID: " + normalizedTreeQueryId
            + ". Available query IDs: " + joinFastaIds(queryRecords)
        );
    }

    private List<FastaRecord> readFastaRecords(File fastaFile, String description) throws Exception {
        ensureNonEmptyFile(fastaFile, description);
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(fastaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
        }

        List<FastaRecord> records = parseFastaText(content.toString(), null, description);
        if (records.isEmpty()) {
            throw new Exception(description + " does not contain any FASTA records: " + fastaFile.getAbsolutePath());
        }
        return records;
    }

    private List<FastaRecord> parseFastaText(String fastaText, String defaultId, String description) throws Exception {
        List<FastaRecord> records = new ArrayList<>();
        if (fastaText == null) {
            return records;
        }

        String normalizedText = fastaText.replace("\r", "").trim();
        if (normalizedText.isEmpty()) {
            return records;
        }

        if (!normalizedText.startsWith(">")) {
            String sequence = sanitizeFastaSequence(normalizedText);
            if (sequence.isEmpty()) {
                throw new Exception(description + " is empty after whitespace normalization.");
            }
            records.add(new FastaRecord(
                defaultId == null || defaultId.trim().isEmpty() ? "Query_1" : defaultId.trim(),
                sequence
            ));
            return records;
        }

        try (BufferedReader reader = new BufferedReader(new StringReader(normalizedText))) {
            String line;
            String currentHeader = null;
            StringBuilder currentSequence = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty()) {
                    continue;
                }

                if (trimmedLine.startsWith(">")) {
                    if (currentHeader != null) {
                        addFastaRecord(records, currentHeader, currentSequence.toString(), description);
                    }
                    currentHeader = trimmedLine.substring(1).trim();
                    currentSequence.setLength(0);
                } else {
                    if (currentHeader == null) {
                        throw new Exception(description + " is not valid FASTA: sequence data found before the first header.");
                    }
                    currentSequence.append(trimmedLine);
                }
            }

            if (currentHeader != null) {
                addFastaRecord(records, currentHeader, currentSequence.toString(), description);
            }
        }

        return records;
    }

    private void addFastaRecord(List<FastaRecord> records, String header, String sequence, String description) throws Exception {
        String normalizedId = normalizeFastaIdentifier(header);
        String sanitizedSequence = sanitizeFastaSequence(sequence);
        if (sanitizedSequence.isEmpty()) {
            throw new Exception(description + " contains an empty sequence for header: " + header);
        }
        if (normalizedId.isEmpty()) {
            normalizedId = "Sequence_" + (records.size() + 1);
        }
        records.add(new FastaRecord(normalizedId, sanitizedSequence));
    }

    private List<FastaRecord> orderHitRecords(List<BlastHit> topHits, List<FastaRecord> hitRecords) throws Exception {
        Map<String, FastaRecord> recordsById = new LinkedHashMap<>();
        for (FastaRecord hitRecord : hitRecords) {
            String normalizedId = normalizeFastaIdentifier(hitRecord.id);
            if (!recordsById.containsKey(normalizedId)) {
                recordsById.put(normalizedId, hitRecord);
            }
        }

        List<FastaRecord> orderedRecords = new ArrayList<>();
        for (BlastHit hit : topHits) {
            String normalizedHitId = normalizeFastaIdentifier(hit.getHitId());
            FastaRecord hitRecord = recordsById.get(normalizedHitId);
            if (hitRecord == null) {
                throw new Exception(
                    "Failed to extract sequence for hit ID: " + normalizedHitId
                    + ". Extracted IDs: " + String.join(", ", recordsById.keySet())
                );
            }
            orderedRecords.add(hitRecord);
        }
        return orderedRecords;
    }

    private String joinFastaIds(List<FastaRecord> records) {
        List<String> ids = new ArrayList<>();
        for (FastaRecord record : records) {
            ids.add(record.id);
        }
        return String.join(", ", ids);
    }

    private String normalizeFastaIdentifier(String rawId) {
        if (rawId == null) {
            return "";
        }

        String normalizedId = rawId.trim();
        if (normalizedId.isEmpty()) {
            return "";
        }

        if (normalizedId.startsWith(">")) {
            normalizedId = normalizedId.substring(1).trim();
        }

        int whitespaceIndex = normalizedId.indexOf(' ');
        if (whitespaceIndex > 0) {
            normalizedId = normalizedId.substring(0, whitespaceIndex);
        }

        return normalizedId.trim();
    }

    private String sanitizeFastaSequence(String rawSequence) {
        if (rawSequence == null) {
            return "";
        }
        return rawSequence.replaceAll("\\s+", "");
    }

    private File createTreeWorkDirectory() throws IOException {
        File treeWorkDir = Files.createTempDirectory("SimpleGenomeHub_IQTree_").toFile();
        treeWorkDir.deleteOnExit();
        return treeWorkDir;
    }

    private String resolveTreeExecutable(String... baseNames) throws Exception {
        List<File> candidateDirectories = new ArrayList<>();
        File appHome = ApplicationLayout.getAppHomeDirectory();
        String userDir = System.getProperty("user.dir", ".");
        File userDirFile = new File(userDir).getAbsoluteFile();

        for (File directory : ApplicationLayout.getToolSearchDirectories(appHome)) {
            addToolDirectory(candidateDirectories, directory);
        }
        if (!userDirFile.getAbsolutePath().equalsIgnoreCase(appHome.getAbsolutePath())) {
            for (File directory : ApplicationLayout.getToolSearchDirectories(userDirFile)) {
                addToolDirectory(candidateDirectories, directory);
            }
        }

        for (String baseName : baseNames) {
            for (File directory : candidateDirectories) {
                File executable = ApplicationLayout.resolveExecutable(directory, baseName);
                if (executable != null) {
                    return executable.getAbsolutePath();
                }
            }
        }

        if (baseNames.length == 0) {
            throw new Exception("No executable was specified for tree building.");
        }
        return baseNames[0];
    }

    private void addToolDirectory(List<File> directories, File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }

        String candidatePath = directory.getAbsolutePath();
        for (File existing : directories) {
            if (existing.getAbsolutePath().equalsIgnoreCase(candidatePath)) {
                return;
            }
        }
        directories.add(directory);
    }

    private void runTreeCommand(List<String> command, File workingDirectory, File expectedOutput, String stepName)
            throws Exception {
        String commandLine = String.join(" ", command);
        Process process;

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(workingDirectory);
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();
        } catch (IOException ex) {
            throw new Exception(stepName + " failed to start.\nCommand: " + commandLine + "\n" + ex.getMessage(), ex);
        }

        StringBuilder output = new StringBuilder();
        Thread outputCollector = collectProcessOutput(process, output);

        boolean finished = process.waitFor(30, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            outputCollector.join(2000);
            throw new Exception(stepName + " timed out.\nCommand: " + commandLine);
        }

        outputCollector.join(5000);
        String commandOutput = output.toString().trim();

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new Exception(
                stepName + " failed with exit code " + exitCode + ".\n"
                + "Command: " + commandLine + "\n"
                + "Output:\n" + commandOutput
            );
        }

        if (expectedOutput != null) {
            ensureNonEmptyFile(expectedOutput, stepName + " output");
        }
    }

    private Thread collectProcessOutput(Process process, StringBuilder output) {
        Thread collector = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (output) {
                        output.append(line).append(System.lineSeparator());
                    }
                }
            } catch (IOException ioEx) {
                synchronized (output) {
                    output.append("Failed to read process output: ").append(ioEx.getMessage()).append(System.lineSeparator());
                }
            }
        }, "tree-command-output");
        collector.setDaemon(true);
        collector.start();
        return collector;
    }

    private void ensureNonEmptyFile(File file, String description) throws Exception {
        if (file == null || !file.isFile()) {
            throw new Exception(description + " was not created: " + (file == null ? "<null>" : file.getAbsolutePath()));
        }
        if (file.length() <= 0) {
            throw new Exception(description + " is empty: " + file.getAbsolutePath());
        }
    }

    private boolean isUsableFile(File file) {
        return file != null && file.isFile() && file.length() > 0;
    }

    /**
     * Display phylogenetic tree
     */
    private void displayTree(File treeFile, String queryIdForTree, int n) {
        try {
            // Read tree file content as Newick string
            StringBuilder newickString = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(treeFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    newickString.append(line);
                }
            }

            System.err.println("Newick string: " + newickString.toString());

            // Use PhyloTreeMan to parse and plot tree from Newick string
            biocjava.bioDoer.JIGplotToolkit.newickParser.PhyloTreeMan ptm =
                new biocjava.bioDoer.JIGplotToolkit.newickParser.PhyloTreeMan();

            PhyloTreeNode rootedNode = null;

            // Try to apply MAD rooting
            try {
                System.err.println("Applying MAD rooting to phylogenetic tree...");

                // Apply MAD rooting to the tree
                rootedNode = ptm.madPoints(newickString.toString());

                System.err.println("MAD rooting completed successfully");
            } catch (Exception madError) {
                // Fall back to unrooted tree if MAD rooting fails
                System.err.println("MAD rooting failed, will display unrooted tree: " + madError.getMessage());
                madError.printStackTrace();
            }

            // Plot the tree (quickPlotTree will create and show its own JFrame window)
            // If MAD rooting succeeded, use rooted tree; otherwise use original Newick string
            jigplot.engine.JIGSubPanel treePanel;
            if (rootedNode != null) {
                treePanel = ptm.quickPlotTree(rootedNode);
            } else {
                treePanel = ptm.quickPlotTree(newickString.toString());
            }

            // Highlight query sequence ID in red
            String queryId = normalizeFastaIdentifier(queryIdForTree);
            for (jigplot.engine.JIGElement curEle : treePanel.getElementList()) {
                if (curEle.getElementType() == jigplot.engine.JIGConstants.ElementType.Text) {
                    if (queryId.equals(curEle.getText().trim())) {
                        curEle.setDrawColor(Color.RED);
                    }
                }
            }

            // Note: quickPlotTree() creates and displays its own JFrame window automatically
            // We don't need to create another window here

        } catch (Exception e) {
            JOptionPane.showMessageDialog(BlastResultsPanel.this,
                "Error displaying tree: " + e.getMessage(),
                "Display Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private static final class FastaRecord {
        private final String id;
        private final String sequence;

        private FastaRecord(String id, String sequence) {
            this.id = id;
            this.sequence = sequence;
        }
    }

    private void exportResults(File outputFile) throws Exception {
        if (currentResult == null) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write("#\tQuery ID\tHit mRNA ID\tHit Gene ID\tBit Score\tE-value\tIdentity%\tLength\tQuery Start\tQuery End\tSubject Start\tSubject End");
            writer.newLine();

            List<BlastHit> allHits = currentResult.getHits();
            for (int i = 0; i < allHits.size(); i++) {
                BlastHit hit = allHits.get(i);
                writer.write(Integer.toString(i + 1));
                writer.write('\t');
                writer.write(getQueryIdForHit(hit));
                writer.write('\t');
                writer.write(getHitTranscriptId(hit));
                writer.write('\t');
                writer.write(getHitGeneId(hit));
                writer.write('\t');
                writer.write(Double.toString(hit.getBitScore()));
                writer.write('\t');
                writer.write(Double.toString(hit.getEvalue()));
                writer.write('\t');
                writer.write(Double.toString(hit.getIdentityPercentage()));
                writer.write('\t');
                writer.write(Integer.toString(hit.getHitLength()));
                writer.write('\t');
                writer.write(Integer.toString(hit.getQueryStart()));
                writer.write('\t');
                writer.write(Integer.toString(hit.getQueryEnd()));
                writer.write('\t');
                writer.write(Integer.toString(hit.getSubjectStart()));
                writer.write('\t');
                writer.write(Integer.toString(hit.getSubjectEnd()));
                writer.newLine();
            }
        }
    }


    /**
     * Export action listener
     */
    private class ExportActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            if (currentResult == null) return;
            
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Export BLAST Results");
            fileChooser.setSelectedFile(new java.io.File("blast_results.tsv"));
            
            if (fileChooser.showSaveDialog(BlastResultsPanel.this) == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                if (selectedFile != null) {
                    try {
                        exportResults(selectedFile);
                        JOptionPane.showMessageDialog(
                            BlastResultsPanel.this,
                            "Exported " + currentResult.getTotalHits() + " hits to:\n" + selectedFile.getAbsolutePath(),
                            "Export Complete",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                        return;
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(
                            BlastResultsPanel.this,
                            "Failed to export BLAST results:\n" + ex.getMessage(),
                            "Export Failed",
                            JOptionPane.ERROR_MESSAGE
                        );
                        return;
                    }
                }
                JOptionPane.showMessageDialog(BlastResultsPanel.this, 
                                            "Export feature not implemented yet", "Info", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }
    
    // Save action listener removed as requested
    
    /**
     * Table model for BLAST hits
     */
    private class BlastHitsTableModel extends AbstractTableModel {
        private List<BlastHit> hits = new ArrayList<>();
        private String[] columnNames = {
            "#", "Query ID", "Hit mRNA ID", "Hit Gene ID", "Bit Score", "E-value", "Identity%", "Length", "Query Start", "Query End", "Subject Start", "Subject End"
        };
        
        public void setHits(List<BlastHit> hits) {
            this.hits = new ArrayList<>(hits);
            fireTableDataChanged();
        }
        
        public BlastHit getHitAt(int row) {
            return hits.get(row);
        }
        
        public List<BlastHit> getAllHits() {
            return new ArrayList<>(hits);
        }
        
        public Object getValueForHit(BlastHit hit, int column) {
            switch (column) {
                case 0: return hits.indexOf(hit) + 1;
                case 1: return getQueryIdForHit(hit);
                case 2: return getHitTranscriptId(hit);
                case 3: return getHitGeneId(hit);
                case 4: return hit.getBitScore();
                case 5: return hit.getEvalue();
                case 6: return hit.getIdentityPercentage();
                case 7: return hit.getHitLength();
                case 8: return hit.getQueryStart();
                case 9: return hit.getQueryEnd();
                case 10: return hit.getSubjectStart();
                case 11: return hit.getSubjectEnd();
                default: return "";
            }
        }
        
        @Override
        public int getRowCount() {
            return hits.size();
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
        public Class<?> getColumnClass(int column) {
            switch (column) {
                case 0: case 7: case 8: case 9: case 10: case 11: return Integer.class;
                case 4: case 5: case 6: return Double.class;
                default: return String.class;
            }
        }
        
        @Override
        public boolean isCellEditable(int row, int column) {
            // Keep cells non-editable for normal interaction
            // Users can still select and copy using Ctrl+C
            return false;
        }
        
        @Override
        public Object getValueAt(int row, int column) {
            BlastHit hit = hits.get(row);
            return getValueForHit(hit, column);
        }
        
        @Override
        public void setValueAt(Object value, int row, int column) {
            // Allow editing but don't actually modify the underlying data
            // This enables selection and copy operations
            // Don't call fireTableCellUpdated to avoid interfering with selection
        }
        
        /**
         * Get Query ID for a specific hit
         */
        private String getQueryIdForHit(BlastHit hit) {
            // Use the hit's own query ID if available
            if (hit.getQueryId() != null && !hit.getQueryId().isEmpty()) {
                return hit.getQueryId();
            }
            // Fallback to result's query ID
            else if (currentResult != null && currentResult.getQueryId() != null) {
                return currentResult.getQueryId();
            } 
            // Last resort fallback
            else {
                return "Query_1";
            }
        }
    }
}
