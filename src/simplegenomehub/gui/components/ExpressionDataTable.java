/*
 * Expression Data Table Component
 * Specialized table for gene expression data with scientific notation and heatmap-style rendering
 */
package simplegenomehub.gui.components;

import simplegenomehub.model.ExpressionMatrix;
import simplegenomehub.gui.SimpleGenomeHubStyle;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.Map;

/**
 * Enhanced table specifically for gene expression data
 * Provides specialized rendering and context actions for expression matrices
 * 
 * @author SimpleGenomeHub Team
 */
public class ExpressionDataTable extends EnhancedInteractiveTable<ExpressionDataTable.ExpressionRow> {
    
    private ExpressionMatrix matrix;
    private String[] sampleNames;
    private boolean enableHeatmapColors = false;
    private DecimalFormat expressionFormat = new DecimalFormat("0.000");
    
    // Color scheme for heatmap-style rendering
    private static final Color LOW_EXPRESSION = new Color(240, 248, 255);  // Light blue
    private static final Color HIGH_EXPRESSION = new Color(255, 69, 0);    // Red orange
    private static final Color ZERO_EXPRESSION = Color.WHITE;
    
    /**
     * Data holder for expression row
     */
    public static class ExpressionRow {
        private String geneId;
        private Map<String, Double> expressionValues;
        
        public ExpressionRow(String geneId, Map<String, Double> expressionValues) {
            this.geneId = geneId;
            this.expressionValues = new HashMap<>(expressionValues);
        }
        
        public String getGeneId() { return geneId; }
        public Map<String, Double> getExpressionValues() { return expressionValues; }
        public Double getExpressionValue(String sample) { return expressionValues.get(sample); }
    }
    
    /**
     * Constructor
     */
    public ExpressionDataTable() {
        super();
        // Enable all features for expression data
        this.enableContextMenu = true;
        this.enableSearch = true;
        this.enableExport = true;
        this.selectionMode = SelectionMode.MULTI_SELECTION;
    }
    
    /**
     * Set expression matrix data
     */
    public void setExpressionMatrix(ExpressionMatrix matrix) {
        this.matrix = matrix;
        this.sampleNames = matrix.getSampleNames();
        
        // Convert matrix data to row format
        List<ExpressionRow> rows = new ArrayList<>();
        for (String geneId : matrix.getGeneIds()) {
            Map<String, Double> expressionValues = matrix.getGeneExpression(geneId);
            rows.add(new ExpressionRow(geneId, expressionValues));
        }
        
        setData(rows);
    }
    
    /**
     * Set filtered expression data
     */
    public void setFilteredData(Set<String> filteredGenes, Map<String, Map<String, Double>> filteredData, String[] samples) {
        this.sampleNames = samples;
        
        // Convert filtered data to row format
        List<ExpressionRow> rows = new ArrayList<>();
        for (String geneId : filteredGenes) {
            Map<String, Double> expressionValues = filteredData.get(geneId);
            if (expressionValues != null) {
                rows.add(new ExpressionRow(geneId, expressionValues));
            }
        }
        
        // Update data
        this.data = new ArrayList<>(rows);
        
        // Force table structure change to update columns
        tableModel.fireTableStructureChanged();
        updateStatusLabel();
        
        // Configure rendering after structure change
        SwingUtilities.invokeLater(() -> {
            configureColumnRenderers();
            configureColumnWidths();
        });
    }
    
    /**
     * Enable/disable heatmap-style color rendering
     */
    public void setHeatmapColors(boolean enabled) {
        this.enableHeatmapColors = enabled;
        configureColumnRenderers();
        table.repaint();
    }
    
    @Override
    protected String[] getColumnNames() {
        if (sampleNames == null) {
            return new String[]{"Gene_ID"};
        }
        
        String[] columnNames = new String[1 + sampleNames.length];
        columnNames[0] = "Gene_ID";
        System.arraycopy(sampleNames, 0, columnNames, 1, sampleNames.length);
        return columnNames;
    }
    
    @Override
    protected Class<?>[] getColumnClasses() {
        if (sampleNames == null) {
            return new Class<?>[]{String.class};
        }
        
        Class<?>[] classes = new Class<?>[1 + sampleNames.length];
        classes[0] = String.class;
        for (int i = 1; i < classes.length; i++) {
            classes[i] = Double.class;
        }
        return classes;
    }
    
    @Override
    protected Object getValueAt(ExpressionRow item, int column) {
        if (column == 0) {
            return item.getGeneId();
        } else if (column <= sampleNames.length && sampleNames != null) {
            String sampleName = sampleNames[column - 1];
            Double value = item.getExpressionValue(sampleName);
            return value != null ? value : 0.0;
        }
        return null;
    }
    
    @Override
    protected void configureColumnRenderers() {
        if (table.getColumnCount() == 0) return;
        
        // Gene ID column - left aligned
        DefaultTableCellRenderer geneIdRenderer = new DefaultTableCellRenderer();
        geneIdRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        geneIdRenderer.setFont(SimpleGenomeHubStyle.bold(geneIdRenderer.getFont()));
        table.getColumnModel().getColumn(0).setCellRenderer(geneIdRenderer);
        
        // Expression value columns
        for (int i = 1; i < table.getColumnCount(); i++) {
            ExpressionCellRenderer renderer = new ExpressionCellRenderer();
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }
    
    @Override
    protected void configureColumnWidths() {
        if (table.getColumnCount() == 0) return;
        
        // Gene ID column
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(0).setMinWidth(100);
        
        // Sample columns
        for (int i = 1; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(80);
            table.getColumnModel().getColumn(i).setMinWidth(60);
        }
    }
    
    @Override
    protected List<ContextAction> getCustomContextActions(int row, int column) {
        List<ContextAction> actions = new ArrayList<>();
        
        // Generate heatmap for selected genes
        if (table.getSelectedRowCount() > 0) {
            actions.add(new ContextAction() {
                @Override
                public String getName() {
                    return "Generate Heatmap";
                }
                
                @Override
                public void execute(int row, int column, EnhancedInteractiveTable<?> table) {
                    generateHeatmapForSelection();
                }
            });
        }
        
        // Toggle heatmap colors
        actions.add(new ContextAction() {
            @Override
            public String getName() {
                return enableHeatmapColors ? "Disable Heatmap Colors" : "Enable Heatmap Colors";
            }
            
            @Override
            public void execute(int row, int column, EnhancedInteractiveTable<?> table) {
                setHeatmapColors(!enableHeatmapColors);
            }
        });
        
        // Copy gene IDs only
        if (table.getSelectedRowCount() > 0) {
            actions.add(new ContextAction() {
                @Override
                public String getName() {
                    return "Copy Gene IDs";
                }
                
                @Override
                public void execute(int row, int column, EnhancedInteractiveTable<?> table) {
                    copySelectedGeneIds();
                }
            });
        }
        
        // Statistical summary
        if (column > 0 && table.getSelectedRowCount() > 0) {
            actions.add(new ContextAction() {
                @Override
                public String getName() {
                    return "Show Statistics";
                }
                
                @Override
                public void execute(int row, int column, EnhancedInteractiveTable<?> table) {
                    showColumnStatistics(column);
                }
            });
        }
        
        return actions;
    }
    
    @Override
    protected String getDefaultExportFileName() {
        String matrixName = matrix != null ? matrix.getName() : "expression_data";
        return matrixName.replaceAll("\\s+", "_") + "_filtered.tsv";
    }
    
    @Override
    protected void onSelectionChanged() {
        // Update any selection-dependent UI elements
        // Could be used to update detail panels, etc.
    }
    
    /**
     * Generate heatmap for selected genes
     */
    private void generateHeatmapForSelection() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select genes first.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // TODO: Integrate with HeatmapControl2
        JOptionPane.showMessageDialog(this, 
            "Heatmap generation for " + selectedRows.length + " selected genes.\n" +
            "Integration with HeatmapControl2 to be implemented.",
            "Heatmap Generation", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Copy selected gene IDs to clipboard
     */
    private void copySelectedGeneIds() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) return;
        
        StringBuilder geneIds = new StringBuilder();
        for (int i = 0; i < selectedRows.length; i++) {
            if (i > 0) geneIds.append("\n");
            Object value = table.getValueAt(selectedRows[i], 0); // Gene ID column
            geneIds.append(value != null ? value.toString() : "");
        }
        
        java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new java.awt.datatransfer.StringSelection(geneIds.toString()), null);
        
        JOptionPane.showMessageDialog(this,
            "Copied " + selectedRows.length + " gene IDs to clipboard",
            "Copy Complete", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Show statistics for selected column
     */
    private void showColumnStatistics(int column) {
        if (column <= 0 || column > sampleNames.length) return;
        
        String sampleName = sampleNames[column - 1];
        int[] selectedRows = table.getSelectedRows();
        
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select rows first.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Calculate statistics for selected rows
        java.util.List<Double> values = new ArrayList<>();
        for (int row : selectedRows) {
            Object value = table.getValueAt(row, column);
            if (value instanceof Double) {
                values.add((Double) value);
            }
        }
        
        if (values.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No numeric values found.", "No Data", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Calculate basic statistics
        double sum = values.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / values.size();
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        
        // Calculate standard deviation
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        
        String stats = String.format(
            "Statistics for %s (%d values):\n\n" +
            "Mean: %.3f\n" +
            "Min: %.3f\n" +
            "Max: %.3f\n" +
            "Std Dev: %.3f\n" +
            "Sum: %.3f",
            sampleName, values.size(), mean, min, max, stdDev, sum
        );
        
        JOptionPane.showMessageDialog(this, stats, "Column Statistics", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Custom cell renderer for expression values
     */
    private class ExpressionCellRenderer extends DefaultTableCellRenderer {
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, 
                boolean isSelected, boolean hasFocus, int row, int column) {
            
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            // Format numeric values
            if (value instanceof Double) {
                Double doubleValue = (Double) value;
                if (doubleValue == 0.0) {
                    setText("0.000");
                } else if (Math.abs(doubleValue) < 0.001) {
                    setText(scientificFormat.format(doubleValue));
                } else {
                    setText(expressionFormat.format(doubleValue));
                }
            }
            
            // Right-align numeric columns
            setHorizontalAlignment(SwingConstants.RIGHT);
            
            // Apply heatmap coloring if enabled
            if (enableHeatmapColors && !isSelected && value instanceof Double) {
                Color backgroundColor = calculateHeatmapColor((Double) value);
                setBackground(backgroundColor);
                
                // Adjust text color based on background
                float[] hsb = Color.RGBtoHSB(backgroundColor.getRed(), backgroundColor.getGreen(), backgroundColor.getBlue(), null);
                setForeground(hsb[2] > 0.5f ? Color.BLACK : Color.WHITE);
            } else if (!isSelected) {
                setBackground(Color.WHITE);
                setForeground(Color.BLACK);
            }
            
            return component;
        }
        
        /**
         * Calculate heatmap color based on expression value
         */
        private Color calculateHeatmapColor(Double value) {
            if (value == null || value == 0.0) {
                return ZERO_EXPRESSION;
            }
            
            // Simple linear interpolation between low and high expression colors
            // This could be made more sophisticated with log scaling, percentiles, etc.
            double maxExpression = getMaxExpressionValue();
            if (maxExpression == 0) return ZERO_EXPRESSION;
            
            double normalizedValue = Math.min(1.0, Math.abs(value) / maxExpression);
            
            int red = (int) (LOW_EXPRESSION.getRed() + normalizedValue * (HIGH_EXPRESSION.getRed() - LOW_EXPRESSION.getRed()));
            int green = (int) (LOW_EXPRESSION.getGreen() + normalizedValue * (HIGH_EXPRESSION.getGreen() - LOW_EXPRESSION.getGreen()));
            int blue = (int) (LOW_EXPRESSION.getBlue() + normalizedValue * (HIGH_EXPRESSION.getBlue() - LOW_EXPRESSION.getBlue()));
            
            return new Color(red, green, blue);
        }
        
        /**
         * Get maximum expression value for normalization
         */
        private double getMaxExpressionValue() {
            double max = 0.0;
            for (ExpressionRow row : data) {
                for (Double value : row.getExpressionValues().values()) {
                    if (value != null && Math.abs(value) > max) {
                        max = Math.abs(value);
                    }
                }
            }
            return max;
        }
    }
}
