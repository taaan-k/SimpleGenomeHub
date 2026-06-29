/*
 * Enhanced table model for enrichment analysis results
 * Supports comprehensive enrichment result display
 */
package simplegenomehub.gui;

import simplegenomehub.util.enrichment.EnhancedEnrichmentResult;

import javax.swing.table.AbstractTableModel;
import java.util.*;
import java.util.List;

/**
 * Table model for displaying enhanced enrichment results
 * 
 * @author SimpleGenomeHub
 */
public class EnhancedEnrichmentTableModel extends AbstractTableModel {
    
    private static final String[] COLUMN_NAMES = {
        "Term Name", "Term ID", "Category", "P-Value", "Adj. P-Value", 
        "Enrichment Ratio", "Gene Count", "Term Size", "Gene List", "Confidence"
    };
    
    private static final Class<?>[] COLUMN_CLASSES = {
        String.class, String.class, String.class, Double.class, Double.class,
        Double.class, Integer.class, Integer.class, String.class, Double.class
    };
    
    private List<EnhancedEnrichmentResult> results;
    private boolean showOnlySignificant = false;
    
    /**
     * Constructor
     */
    public EnhancedEnrichmentTableModel() {
        this.results = new ArrayList<>();
    }
    
    /**
     * Set enrichment results
     */
    public void setResults(List<EnhancedEnrichmentResult> results) {
        this.results = new ArrayList<>(results);
        fireTableDataChanged();
    }
    
    /**
     * Clear all results
     */
    public void clearResults() {
        this.results.clear();
        fireTableDataChanged();
    }
    
    /**
     * Get result at specified row
     */
    public EnhancedEnrichmentResult getResultAt(int row) {
        if (row >= 0 && row < results.size()) {
            return results.get(row);
        }
        return null;
    }
    
    /**
     * Filter to show only significant results
     */
    public void setShowOnlySignificant(boolean showOnlySignificant) {
        this.showOnlySignificant = showOnlySignificant;
        fireTableDataChanged();
    }
    
    /**
     * Get filtered results based on current settings
     */
    private List<EnhancedEnrichmentResult> getFilteredResults() {
        if (showOnlySignificant) {
            List<EnhancedEnrichmentResult> filtered = new ArrayList<>();
            for (EnhancedEnrichmentResult result : results) {
                if (result.isSignificant()) {
                    filtered.add(result);
                }
            }
            return filtered;
        }
        return results;
    }
    
    @Override
    public int getRowCount() {
        return getFilteredResults().size();
    }
    
    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }
    
    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }
    
    @Override
    public Class<?> getColumnClass(int column) {
        return COLUMN_CLASSES[column];
    }
    
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        List<EnhancedEnrichmentResult> filteredResults = getFilteredResults();
        
        if (rowIndex >= filteredResults.size()) {
            return null;
        }
        
        EnhancedEnrichmentResult result = filteredResults.get(rowIndex);
        
        switch (columnIndex) {
            case 0: return result.getTermName();
            case 1: return result.getTermId();
            case 2: return result.getCategory();
            case 3: return result.getPValue();
            case 4: return result.getAdjustedPValue();
            case 5: return result.getEnrichmentRatio();
            case 6: return result.getGeneCount();
            case 7: return result.getTermSize();
            case 8: return result.getGeneListString();
            case 9: return result.getConfidenceScore();
            default: return null;
        }
    }
    
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false; // All cells are read-only
    }
    
    /**
     * Get tool tip text for a cell
     */
    public String getToolTipText(int row, int column) {
        List<EnhancedEnrichmentResult> filteredResults = getFilteredResults();
        
        if (row >= filteredResults.size()) {
            return null;
        }
        
        EnhancedEnrichmentResult result = filteredResults.get(row);
        
        switch (column) {
            case 0: // Term Name
                return "<html><b>" + result.getTermName() + "</b><br>" +
                       "ID: " + result.getTermId() + "<br>" +
                       "Description: " + (result.getDescription() != null ? result.getDescription() : "N/A") +
                       "</html>";
            
            case 3: // P-Value
            case 4: // Adj. P-Value
                return "<html>Statistical significance:<br>" +
                       "Raw p-value: " + String.format("%.2e", result.getPValue()) + "<br>" +
                       "Adjusted p-value: " + String.format("%.2e", result.getAdjustedPValue()) + "<br>" +
                       "Significance: " + result.getSignificanceLevel() +
                       "</html>";
            
            case 5: // Enrichment Ratio
                return "<html>Enrichment statistics:<br>" +
                       "Fold enrichment: " + String.format("%.2f", result.getEnrichmentRatio()) + "<br>" +
                       "Strength: " + result.getEnrichmentStrength() + "<br>" +
                       "Odds ratio: " + String.format("%.2f", result.getOddsRatio()) +
                       "</html>";
            
            case 6: // Gene Count
            case 7: // Term Size
                return "<html>Gene counts:<br>" +
                       "Genes in test set: " + result.getGeneCount() + "<br>" +
                       "Total genes in term: " + result.getTermSize() + "<br>" +
                       "Test set size: " + result.getTestSetSize() + "<br>" +
                       "Background size: " + result.getBackgroundCount() +
                       "</html>";
            
            case 8: // Gene List
                String geneList = result.getGeneListString();
                if (geneList != null && geneList.length() > 100) {
                    return "<html><b>Genes in this term:</b><br>" +
                           geneList.substring(0, 100) + "...<br>" +
                           "<i>(" + result.getGeneCount() + " genes total)</i>" +
                           "</html>";
                } else {
                    return "<html><b>Genes in this term:</b><br>" +
                           (geneList != null ? geneList.replace(",", ", ") : "N/A") +
                           "</html>";
                }
            
            case 9: // Confidence
                return "<html>Confidence score: " + String.format("%.1f%%", result.getConfidenceScore()) + "<br>" +
                       "Based on p-value, gene count, enrichment ratio, and term size<br>" +
                       "Higher scores indicate more reliable enrichment" +
                       "</html>";
            
            default:
                return null;
        }
    }
    
    /**
     * Get summary statistics
     */
    public Map<String, Object> getSummaryStatistics() {
        Map<String, Object> stats = new HashMap<>();
        List<EnhancedEnrichmentResult> filteredResults = getFilteredResults();
        
        stats.put("totalResults", filteredResults.size());
        stats.put("significantResults", filteredResults.stream()
            .mapToInt(result -> result.isSignificant() ? 1 : 0)
            .sum());
        
        if (!filteredResults.isEmpty()) {
            stats.put("averageEnrichment", filteredResults.stream()
                .mapToDouble(EnhancedEnrichmentResult::getEnrichmentRatio)
                .average().orElse(0.0));
            
            stats.put("medianPValue", filteredResults.stream()
                .mapToDouble(EnhancedEnrichmentResult::getAdjustedPValue)
                .sorted()
                .skip(filteredResults.size() / 2)
                .findFirst().orElse(1.0));
            
            // Category breakdown
            Map<String, Long> categoryBreakdown = new HashMap<>();
            for (EnhancedEnrichmentResult result : filteredResults) {
                String category = result.getCategory();
                categoryBreakdown.put(category, categoryBreakdown.getOrDefault(category, 0L) + 1L);
            }
            stats.put("categoryBreakdown", categoryBreakdown);
        }
        
        return stats;
    }
    
    /**
     * Export results to TSV format
     */
    public String exportToTSV() {
        StringBuilder sb = new StringBuilder();
        
        // Header
        sb.append(String.join("\t", COLUMN_NAMES)).append("\n");
        
        // Data rows
        List<EnhancedEnrichmentResult> filteredResults = getFilteredResults();
        for (EnhancedEnrichmentResult result : filteredResults) {
            sb.append(result.getTermName()).append("\t");
            sb.append(result.getTermId()).append("\t");
            sb.append(result.getCategory()).append("\t");
            sb.append(String.format("%.6e", result.getPValue())).append("\t");
            sb.append(String.format("%.6e", result.getAdjustedPValue())).append("\t");
            sb.append(String.format("%.4f", result.getEnrichmentRatio())).append("\t");
            sb.append(result.getGeneCount()).append("\t");
            sb.append(result.getTermSize()).append("\t");
            sb.append(result.getGeneListString() != null ? result.getGeneListString() : "").append("\t");
            sb.append(String.format("%.1f", result.getConfidenceScore())).append("\n");
        }
        
        return sb.toString();
    }
}