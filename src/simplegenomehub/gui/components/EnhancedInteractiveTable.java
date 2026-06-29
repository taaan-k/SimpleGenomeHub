/*
 * Enhanced Interactive Table Component
 * Provides advanced JTable functionality with sorting, filtering, context menus, and export
 */
package simplegenomehub.gui.components;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

/**
 * Abstract base class for enhanced interactive tables
 * Provides common functionality for sortable, filterable, exportable tables
 * 
 * @param <T> The data type stored in the table
 * @author SimpleGenomeHub Team
 */
public abstract class EnhancedInteractiveTable<T> extends JPanel {
    
    // Selection modes
    public enum SelectionMode {
        ROW_ONLY,           // Row selection only
        CELL_SELECTION,     // Cell-level selection
        MULTI_SELECTION     // Multiple row/cell selection
    }
    
    // Core components
    protected JTable table;
    protected EnhancedTableModel tableModel;
    protected TableRowSorter<EnhancedTableModel> sorter;
    protected JTextField searchField;
    protected JLabel statusLabel;
    protected JButton exportVisibleButton;
    protected JButton exportAllButton;
    protected JButton copyButton;
    
    // Configuration
    protected SelectionMode selectionMode = SelectionMode.MULTI_SELECTION;
    protected boolean enableContextMenu = true;
    protected boolean enableSearch = true;
    protected boolean enableExport = true;
    protected Color selectionBackground = new Color(255, 239, 213); // Light orange
    protected Color selectionForeground = Color.BLACK;
    
    // Data
    protected List<T> data = new ArrayList<>();
    protected DecimalFormat scientificFormat = new DecimalFormat("0.##E0");
    protected DecimalFormat percentageFormat = new DecimalFormat("0.0");
    
    /**
     * Constructor
     */
    public EnhancedInteractiveTable() {
        initializeComponents();
        setupLayout();
        setupEventHandlers();
    }
    
    /**
     * Initialize components
     */
    protected void initializeComponents() {
        // Create table model and table
        tableModel = new EnhancedTableModel();
        table = new JTable(tableModel);
        
        // Setup sorting
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        
        // Configure table appearance
        configureTableAppearance();
        
        // Setup cell renderers
        configureColumnRenderers();
        
        // Search field
        if (enableSearch) {
            searchField = new JTextField(20);
            searchField.setToolTipText("Enter search terms to filter table");
        }
        
        // Status label
        statusLabel = new JLabel();
        
        // Action buttons
        if (enableExport) {
            exportVisibleButton = new JButton("Export Visible");
            exportAllButton = new JButton("Export All");
        }
        copyButton = new JButton("Copy Selection");
    }
    
    /**
     * Configure table appearance
     */
    protected void configureTableAppearance() {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(22);
        
        // Configure selection based on mode
        switch (selectionMode) {
            case ROW_ONLY:
                table.setRowSelectionAllowed(true);
                table.setColumnSelectionAllowed(false);
                table.setCellSelectionEnabled(false);
                table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                break;
            case CELL_SELECTION:
                table.setRowSelectionAllowed(false);
                table.setColumnSelectionAllowed(false);
                table.setCellSelectionEnabled(true);
                table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                break;
            case MULTI_SELECTION:
                table.setRowSelectionAllowed(true);
                table.setColumnSelectionAllowed(true);
                table.setCellSelectionEnabled(true);
                table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                break;
        }
        
        // Set selection colors
        table.setSelectionBackground(selectionBackground);
        table.setSelectionForeground(selectionForeground);
        
        // Enable focus
        table.setFocusable(true);
        table.setRequestFocusEnabled(true);
    }
    
    /**
     * Setup layout
     */
    protected void setupLayout() {
        setLayout(new BorderLayout(5, 5));
        
        // Top panel with search and controls
        if (enableSearch || enableExport) {
            JPanel topPanel = createTopPanel();
            add(topPanel, BorderLayout.NORTH);
        }
        
        // Center: Table in scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(800, 400));
        add(scrollPane, BorderLayout.CENTER);
        
        // Bottom: Status and action buttons
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create top panel with search and controls
     */
    protected JPanel createTopPanel() {
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        if (enableSearch) {
            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            searchPanel.add(new JLabel("Search:"));
            searchPanel.add(searchField);
            topPanel.add(searchPanel, BorderLayout.WEST);
        }
        
        if (enableExport) {
            JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            exportPanel.add(exportVisibleButton);
            exportPanel.add(exportAllButton);
            topPanel.add(exportPanel, BorderLayout.EAST);
        }
        
        return topPanel;
    }
    
    /**
     * Create bottom panel with status and actions
     */
    protected JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        bottomPanel.add(statusLabel, BorderLayout.WEST);
        
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actionPanel.add(copyButton);
        bottomPanel.add(actionPanel, BorderLayout.EAST);
        
        return bottomPanel;
    }
    
    /**
     * Setup event handlers
     */
    protected void setupEventHandlers() {
        // Search functionality
        if (enableSearch && searchField != null) {
            searchField.addActionListener(e -> performSearch());
            // Real-time search as user types
            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { performSearch(); }
            });
        }
        
        // Export functionality
        if (enableExport) {
            exportVisibleButton.addActionListener(e -> exportVisible());
            exportAllButton.addActionListener(e -> exportAll());
        }
        
        // Copy functionality
        copyButton.addActionListener(e -> copySelection());
        
        // Context menu
        if (enableContextMenu) {
            setupContextMenu();
        }
        
        // Selection change listener
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSelectionChanged();
                updateStatusLabel();
            }
        });
        
        // Column selection listener
        if (table.getColumnModel() != null) {
            table.getColumnModel().getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    onSelectionChanged();
                    updateStatusLabel();
                }
            });
        }
    }
    
    /**
     * Setup context menu
     */
    protected void setupContextMenu() {
        table.addMouseListener(new MouseAdapter() {
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
    }
    
    /**
     * Show context menu
     */
    protected void showContextMenu(MouseEvent e) {
        int row = table.rowAtPoint(e.getPoint());
        int column = table.columnAtPoint(e.getPoint());
        
        if (row >= 0 && column >= 0) {
            JPopupMenu contextMenu = createContextMenu(row, column);
            contextMenu.show(table, e.getX(), e.getY());
        }
    }
    
    /**
     * Create context menu for specific cell
     */
    protected JPopupMenu createContextMenu(int row, int column) {
        JPopupMenu contextMenu = new JPopupMenu();
        
        // Standard actions
        JMenuItem copyItem = new JMenuItem("Copy Selection");
        copyItem.addActionListener(e -> copySelection());
        contextMenu.add(copyItem);
        
        JMenuItem copyColumnItem = new JMenuItem("Copy Column");
        copyColumnItem.addActionListener(e -> copyColumn(column));
        contextMenu.add(copyColumnItem);
        
        contextMenu.addSeparator();
        
        JMenuItem sortAscItem = new JMenuItem("Sort Ascending");
        sortAscItem.addActionListener(e -> sortColumn(column, true));
        contextMenu.add(sortAscItem);
        
        JMenuItem sortDescItem = new JMenuItem("Sort Descending");
        sortDescItem.addActionListener(e -> sortColumn(column, false));
        contextMenu.add(sortDescItem);
        
        // Add custom actions
        List<ContextAction> customActions = getCustomContextActions(row, column);
        if (!customActions.isEmpty()) {
            contextMenu.addSeparator();
            for (ContextAction action : customActions) {
                JMenuItem item = new JMenuItem(action.getName());
                item.addActionListener(e -> action.execute(row, column, this));
                contextMenu.add(item);
            }
        }
        
        return contextMenu;
    }
    
    /**
     * Perform search/filter
     */
    protected void performSearch() {
        if (searchField == null) return;
        
        String searchText = searchField.getText().trim();
        if (searchText.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            // Case-insensitive search across all columns
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText));
        }
        updateStatusLabel();
    }
    
    /**
     * Copy selection to clipboard
     */
    protected void copySelection() {
        StringBuilder selection = new StringBuilder();
        
        int[] selectedRows = table.getSelectedRows();
        int[] selectedColumns = table.getSelectedColumns();
        
        if (selectedRows.length == 0) return;
        
        // Copy with headers if multiple rows/columns
        if (selectedRows.length > 1 || selectedColumns.length > 1) {
            // Add column headers
            for (int i = 0; i < selectedColumns.length; i++) {
                if (i > 0) selection.append("\t");
                selection.append(table.getColumnName(selectedColumns[i]));
            }
            selection.append("\n");
        }
        
        // Add data rows
        for (int i = 0; i < selectedRows.length; i++) {
            for (int j = 0; j < selectedColumns.length; j++) {
                if (j > 0) selection.append("\t");
                Object value = table.getValueAt(selectedRows[i], selectedColumns[j]);
                selection.append(value != null ? value.toString() : "");
            }
            if (i < selectedRows.length - 1) selection.append("\n");
        }
        
        // Copy to clipboard
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(selection.toString()), null);
        
        JOptionPane.showMessageDialog(this, 
            "Copied " + selectedRows.length + " rows × " + selectedColumns.length + " columns to clipboard",
            "Copy Complete", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Copy entire column to clipboard
     */
    protected void copyColumn(int column) {
        StringBuilder columnData = new StringBuilder();
        
        // Add header
        columnData.append(table.getColumnName(column)).append("\n");
        
        // Add all visible rows
        for (int i = 0; i < table.getRowCount(); i++) {
            Object value = table.getValueAt(i, column);
            columnData.append(value != null ? value.toString() : "").append("\n");
        }
        
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(columnData.toString()), null);
        
        JOptionPane.showMessageDialog(this,
            "Copied " + table.getRowCount() + " values from column '" + table.getColumnName(column) + "'",
            "Copy Complete", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Sort column
     */
    protected void sortColumn(int column, boolean ascending) {
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(column, ascending ? SortOrder.ASCENDING : SortOrder.DESCENDING));
        sorter.setSortKeys(sortKeys);
    }
    
    /**
     * Export visible data
     */
    protected void exportVisible() {
        exportData(false);
    }
    
    /**
     * Export all data
     */
    protected void exportAll() {
        exportData(true);
    }
    
    /**
     * Export data to file
     */
    protected void exportData(boolean includeFiltered) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(getDefaultExportFileName()));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                if (includeFiltered) {
                    exportAllDataToFile(file);
                } else {
                    exportVisibleDataToFile(file);
                }
                JOptionPane.showMessageDialog(this,
                    "Data exported successfully to:\n" + file.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Export failed: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Export visible data to file
     */
    protected void exportVisibleDataToFile(File file) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Write headers
            for (int i = 0; i < table.getColumnCount(); i++) {
                if (i > 0) writer.print("\t");
                writer.print(table.getColumnName(i));
            }
            writer.println();
            
            // Write visible rows
            for (int i = 0; i < table.getRowCount(); i++) {
                for (int j = 0; j < table.getColumnCount(); j++) {
                    if (j > 0) writer.print("\t");
                    Object value = table.getValueAt(i, j);
                    writer.print(value != null ? value.toString() : "");
                }
                writer.println();
            }
        }
    }
    
    /**
     * Export all data to file
     */
    protected void exportAllDataToFile(File file) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Write headers
            String[] columnNames = getColumnNames();
            for (int i = 0; i < columnNames.length; i++) {
                if (i > 0) writer.print("\t");
                writer.print(columnNames[i]);
            }
            writer.println();
            
            // Write all data
            for (T item : data) {
                for (int j = 0; j < columnNames.length; j++) {
                    if (j > 0) writer.print("\t");
                    Object value = getValueAt(item, j);
                    writer.print(value != null ? value.toString() : "");
                }
                writer.println();
            }
        }
    }
    
    /**
     * Update status label
     */
    protected void updateStatusLabel() {
        int totalRows = data.size();
        int visibleRows = table.getRowCount();
        int selectedRows = table.getSelectedRowCount();
        int selectedCells = table.getSelectedRowCount() * table.getSelectedColumnCount();
        
        StringBuilder status = new StringBuilder();
        if (visibleRows != totalRows) {
            status.append(visibleRows).append(" of ").append(totalRows).append(" rows shown");
        } else {
            status.append(totalRows).append(" rows");
        }
        
        if (selectedRows > 0) {
            status.append(" | ").append(selectedRows).append(" rows selected");
            if (selectedCells > selectedRows) {
                status.append(" (").append(selectedCells).append(" cells)");
            }
        }
        
        statusLabel.setText(status.toString());
    }
    
    /**
     * Set data for the table
     */
    public void setData(List<T> newData) {
        this.data = new ArrayList<>(newData);
        tableModel.fireTableDataChanged();
        updateStatusLabel();
        
        // Configure column widths after data is set
        SwingUtilities.invokeLater(this::configureColumnWidths);
    }
    
    /**
     * Set minimum number of visible rows for the table
     */
    public void setMinimumVisibleRows(int rows) {
        int rowHeight = table.getRowHeight();
        int minHeight = rows * rowHeight + table.getTableHeader().getPreferredSize().height;
        
        // Find the scroll pane and set minimum size
        Component parent = table.getParent();
        if (parent instanceof JViewport) {
            JViewport viewport = (JViewport) parent;
            Component scrollPane = viewport.getParent();
            if (scrollPane instanceof JScrollPane) {
                JScrollPane sp = (JScrollPane) scrollPane;
                Dimension currentSize = sp.getPreferredSize();
                sp.setMinimumSize(new Dimension(currentSize.width, minHeight));
                sp.setPreferredSize(new Dimension(currentSize.width, Math.max(currentSize.height, minHeight)));
            }
        }
    }
    
    /**
     * Get table component
     */
    public JTable getTable() {
        return table;
    }
    
    // Abstract methods to be implemented by subclasses
    protected abstract String[] getColumnNames();
    protected abstract Class<?>[] getColumnClasses();
    protected abstract Object getValueAt(T item, int column);
    protected abstract void configureColumnRenderers();
    protected abstract void configureColumnWidths();
    protected abstract List<ContextAction> getCustomContextActions(int row, int column);
    protected abstract String getDefaultExportFileName();
    protected abstract void onSelectionChanged();
    
    /**
     * Enhanced table model
     */
    protected class EnhancedTableModel extends AbstractTableModel {
        
        @Override
        public int getRowCount() {
            return data.size();
        }
        
        @Override
        public int getColumnCount() {
            return getColumnNames().length;
        }
        
        @Override
        public String getColumnName(int column) {
            return getColumnNames()[column];
        }
        
        @Override
        public Class<?> getColumnClass(int column) {
            Class<?>[] classes = getColumnClasses();
            return column < classes.length ? classes[column] : String.class;
        }
        
        @Override
        public Object getValueAt(int row, int column) {
            if (row < data.size()) {
                return EnhancedInteractiveTable.this.getValueAt(data.get(row), column);
            }
            return null;
        }
        
        @Override
        public boolean isCellEditable(int row, int column) {
            return false; // Read-only by default
        }
    }
    
    /**
     * Interface for custom context actions
     */
    public interface ContextAction {
        String getName();
        void execute(int row, int column, EnhancedInteractiveTable<?> table);
    }
}