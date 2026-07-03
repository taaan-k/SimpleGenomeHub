/*
 * Metadata-Driven Table Model for Annotation Data
 * Completely dynamic table structure based on actual file metadata
 */
package simplegenomehub.gui;

import simplegenomehub.model.*;
import simplegenomehub.model.GeneAnnotationData.AnnotationType;
import simplegenomehub.model.GeneAnnotationData.GeneAnnotation;

import javax.swing.table.AbstractTableModel;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.Logger;

/**
 * A completely dynamic table model that adapts to any annotation format
 * based on metadata and actual file structure
 * 
 * @author SimpleGenomeHub
 */
public class MetadataDrivenTableModel extends AbstractTableModel {
    
    private static final Logger logger = Logger.getLogger(MetadataDrivenTableModel.class.getName());
    
    private AnnotationMetadata metadata;
    private AnnotationType annotationType;
    private File dataFile;
    private String[] columnHeaders;
    private List<String[]> tableData;
    private Map<String, String> geneFilterMap; // gene -> first occurrence row data
    
    // Pagination support
    private int currentPage = 0;
    private int pageSize = 1000;
    private int totalRows = 0;
    private boolean isPaginated = false;
    
    // Loading state
    private boolean isLoaded = false;
    private boolean isLoading = false;
    
    /**
     * Constructor with metadata
     */
    public MetadataDrivenTableModel(AnnotationMetadata metadata, AnnotationType type, File dataFile) {
        this.metadata = metadata;
        this.annotationType = type;
        this.dataFile = dataFile;
        this.tableData = new ArrayList<>();
        this.geneFilterMap = new HashMap<>();
        
        initializeFromMetadata();
    }
    
    /**
     * Constructor for direct file loading (without metadata)
     */
    public MetadataDrivenTableModel(AnnotationType type, File dataFile) {
        this.annotationType = type;
        this.dataFile = dataFile;
        this.tableData = new ArrayList<>();
        this.geneFilterMap = new HashMap<>();
        
        // Generate metadata on-the-fly
        generateMetadataFromFile();
        initializeFromMetadata();
    }
    
    /**
     * Initialize table structure from metadata
     */
    private void initializeFromMetadata() {
        if (metadata != null) {
            columnHeaders = metadata.getColumnHeaders();
            totalRows = metadata.getDataLines();
            
            // Determine if pagination is needed
            isPaginated = metadata.isLargeDataset() || totalRows > 5000;
            if (isPaginated) {
                // Adjust page size based on metadata recommendations
                Object recommendedPageSize = metadata.getCustomProperties().get("recommend_page_size");
                if (recommendedPageSize instanceof Number) {
                    pageSize = ((Number) recommendedPageSize).intValue();
                }
            }
            
            logger.info("Initialized table model for " + annotationType.getShortName() + 
                       ": " + totalRows + " rows, " + 
                       (columnHeaders != null ? columnHeaders.length : 0) + " columns, " +
                       "paginated=" + isPaginated);
        } else {
            // Fallback for missing metadata
            generateBasicStructure();
        }
    }
    
    /**
     * Generate metadata from file if not available
     */
    private void generateMetadataFromFile() {
        if (dataFile != null && dataFile.exists()) {
            logger.info("Generating metadata for " + dataFile.getName());
            metadata = AnnotationMetadata.analyzeFile(dataFile, annotationType);
        }
    }
    
    /**
     * Generate basic structure without metadata
     */
    private void generateBasicStructure() {
        logger.warning("No metadata available, using basic structure detection");
        
        if (dataFile != null && dataFile.exists()) {
            try {
                detectColumnsFromFile();
            } catch (IOException e) {
                logger.severe("Failed to detect file structure: " + e.getMessage());
                setDefaultHeaders();
            }
        } else {
            setDefaultHeaders();
        }
    }
    
    /**
     * Detect column structure directly from file
     */
    private void detectColumnsFromFile() throws IOException {
        List<String> detectedHeaders = new ArrayList<>();
        int maxColumns = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            boolean headerFound = false;
            
            while ((line = reader.readLine()) != null && !headerFound) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts;
                if (line.startsWith("#")) {
                    // Header line
                    String headerLine = line.substring(1).trim();
                    parts = splitLine(headerLine);
                    for (String part : parts) {
                        detectedHeaders.add(part.trim());
                    }
                    headerFound = true;
                } else {
                    // Data line - count columns
                    parts = splitLine(line);
                    maxColumns = Math.max(maxColumns, parts.length);
                    
                    // Check if this looks like a header
                    if (parts.length > 1 && parts[0].toLowerCase().contains("gene")) {
                        for (String part : parts) {
                            detectedHeaders.add(part.trim());
                        }
                        headerFound = true;
                    } else {
                        // Stop after checking a few lines
                        break;
                    }
                }
            }
        }
        
        if (!detectedHeaders.isEmpty()) {
            columnHeaders = detectedHeaders.toArray(new String[0]);
        } else if (maxColumns > 0) {
            // Generate default headers based on column count
            columnHeaders = generateDefaultHeaders(maxColumns);
        } else {
            setDefaultHeaders();
        }
        
        logger.info("Detected " + columnHeaders.length + " columns from file");
    }
    
    /**
     * Split line with automatic delimiter detection
     */
    private String[] splitLine(String line) {
        // Try tab first
        if (line.contains("\t")) {
            return line.split("\t", -1);
        }
        // Try comma
        if (line.contains(",")) {
            return line.split(",", -1);
        }
        // Try semicolon
        if (line.contains(";")) {
            return line.split(";", -1);
        }
        // Default to tab
        return line.split("\t", -1);
    }
    
    /**
     * Generate default headers based on annotation type and column count
     */
    private String[] generateDefaultHeaders(int columnCount) {
        String[] headers = new String[columnCount];
        
        for (int i = 0; i < columnCount; i++) {
            switch (i) {
                case 0:
                    headers[i] = "Gene_ID";
                    break;
                case 1:
                    headers[i] = annotationType != null ? 
                        annotationType.getShortName() + "_ID" : "Annotation_ID";
                    break;
                case 2:
                    headers[i] = "Term";
                    break;
                case 3:
                    headers[i] = "Description";
                    break;
                default:
                    headers[i] = "Column_" + (i + 1);
                    break;
            }
        }
        
        return headers;
    }
    
    /**
     * Set default headers for annotation type
     */
    private void setDefaultHeaders() {
        if (annotationType != null) {
            switch (annotationType) {
                case GO:
                    columnHeaders = new String[]{"Gene_ID", "GO_ID", "Term", "Description"};
                    break;
                case KEGG:
                    columnHeaders = new String[]{"Gene_ID", "KEGG_ID", "Term", "Description"};
                    break;
                case INTERPRO:
                    columnHeaders = new String[]{"Gene_ID", "InterPro_ID", "Term", "Description", "Type"};
                    break;
                case PFAM:
                    columnHeaders = new String[]{"Gene_ID", "Pfam_ID", "Term", "Description", "Domain"};
                    break;
                case CUSTOM:
                default:
                    columnHeaders = new String[]{"Gene_ID", "Description"};
                    break;
            }
        } else {
            columnHeaders = new String[]{"Gene_ID", "Description"};
        }
    }
    
    /**
     * Load data from file with pagination support
     */
    public void loadData() {
        if (isLoading || dataFile == null || !dataFile.exists()) {
            return;
        }
        
        isLoading = true;
        tableData.clear();
        geneFilterMap.clear();
        
        try {
            if (isPaginated) {
                loadPagedData();
            } else {
                loadAllData();
            }
            
            isLoaded = true;
            logger.info("Loaded " + tableData.size() + " rows from " + dataFile.getName());
            
        } catch (IOException e) {
            logger.severe("Failed to load data from " + dataFile.getName() + ": " + e.getMessage());
        } finally {
            isLoading = false;
            fireTableDataChanged();
        }
    }
    
    /**
     * Load all data (for small files)
     */
    private void loadAllData() throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            boolean skipFirstDataLine = false;
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                // Skip comments
                if (line.startsWith("#")) {
                    continue;
                }
                
                // Skip header line if it exists and we haven't set skipFirstDataLine
                String[] parts = splitLine(line);
                if (!skipFirstDataLine && parts.length > 1 && 
                    parts[0].toLowerCase().contains("gene") && 
                    !parts[1].matches(".*\\d.*")) {
                    skipFirstDataLine = true;
                    continue;
                }
                
                // Process data line
                if (parts.length >= 1) {
                    // Ensure we have enough columns to match headers
                    String[] rowData = new String[columnHeaders.length];
                    for (int i = 0; i < rowData.length; i++) {
                        rowData[i] = (i < parts.length) ? parts[i].trim() : "";
                    }
                    
                    tableData.add(rowData);
                    
                    // Track gene for filtering
                    String geneId = rowData[0];
                    if (!geneId.isEmpty() && !geneFilterMap.containsKey(geneId)) {
                        geneFilterMap.put(geneId, String.join("\t", rowData));
                    }
                }
            }
        }
    }
    
    /**
     * Load paged data (for large files)
     */
    private void loadPagedData() throws IOException {
        int startRow = currentPage * pageSize;
        int endRow = startRow + pageSize;
        int currentRow = 0;
        int dataRowCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            boolean skipFirstDataLine = false;
            
            while ((line = reader.readLine()) != null && dataRowCount < pageSize) {
                if (line.trim().isEmpty()) continue;
                
                // Skip comments
                if (line.startsWith("#")) {
                    continue;
                }
                
                // Skip header line if it exists
                String[] parts = splitLine(line);
                if (!skipFirstDataLine && parts.length > 1 && 
                    parts[0].toLowerCase().contains("gene") && 
                    !parts[1].matches(".*\\d.*")) {
                    skipFirstDataLine = true;
                    continue;
                }
                
                // Check if we're in the current page range
                if (currentRow >= startRow && currentRow < endRow) {
                    if (parts.length >= 1) {
                        // Ensure we have enough columns to match headers
                        String[] rowData = new String[columnHeaders.length];
                        for (int i = 0; i < rowData.length; i++) {
                            rowData[i] = (i < parts.length) ? parts[i].trim() : "";
                        }
                        
                        tableData.add(rowData);
                        dataRowCount++;
                        
                        // Track gene for filtering
                        String geneId = rowData[0];
                        if (!geneId.isEmpty() && !geneFilterMap.containsKey(geneId)) {
                            geneFilterMap.put(geneId, String.join("\t", rowData));
                        }
                    }
                }
                
                currentRow++;
            }
        }
    }
    
    /**
     * Load specific page
     */
    public void loadPage(int page) {
        if (page < 0 || (isPaginated && page >= getTotalPages())) {
            return;
        }
        
        currentPage = page;
        loadData();
    }
    
    /**
     * Get total number of pages
     */
    public int getTotalPages() {
        if (!isPaginated) {
            return 1;
        }
        return (int) Math.ceil((double) totalRows / pageSize);
    }
    
    /**
     * Get current page number
     */
    public int getCurrentPage() {
        return currentPage;
    }
    
    /**
     * Check if pagination is enabled
     */
    public boolean isPaginated() {
        return isPaginated;
    }
    
    /**
     * Get page size
     */
    public int getPageSize() {
        return pageSize;
    }
    
    /**
     * Set page size and reload if needed
     */
    public void setPageSize(int newPageSize) {
        if (newPageSize != pageSize && newPageSize > 0) {
            pageSize = newPageSize;
            currentPage = 0; // Reset to first page
            if (isLoaded) {
                loadData();
            }
        }
    }
    
    /**
     * Filter by specific gene IDs (exact match)
     */
    public void filterByGenes(Set<String> geneIds) {
        filterByGenes(geneIds, false);
    }
    
    /**
     * Filter by specific gene IDs with optional partial matching
     * Always searches the complete file, not just loaded table data
     */
    public void filterByGenes(Set<String> geneIds, boolean partialMatch) {
        if (geneIds == null || geneIds.isEmpty()) {
            // Clear filter - reload original data
            loadData();
            return;
        }
        
        List<String[]> filteredData = new ArrayList<>();
        
        // Always search the complete file for accurate results
        try {
            if (partialMatch) {
                searchFileForPartialGeneIds(geneIds, filteredData);
            } else {
                searchFileForGenes(geneIds, filteredData);
            }
        } catch (IOException e) {
            logger.warning("Failed to search file for gene IDs: " + e.getMessage());
        }
        
        tableData = filteredData;
        fireTableDataChanged();
        
        logger.info("Filtered annotations: found " + filteredData.size() + " entries for " + 
                   geneIds.size() + " gene IDs (partial=" + partialMatch + ")");
    }
    
    /**
     * Filter by partial gene ID match
     * Always searches the complete file, not just loaded table data
     */
    public void filterByPartialGeneId(String partialGeneId) {
        if (partialGeneId == null || partialGeneId.trim().isEmpty()) {
            // Clear filter - reload original data
            loadData();
            return;
        }
        
        String filterText = partialGeneId.toLowerCase().trim();
        List<String[]> filteredData = new ArrayList<>();
        
        // Always search the complete file for accurate results
        try {
            searchFileForPartialGeneId(filterText, filteredData);
        } catch (IOException e) {
            logger.warning("Failed to search file for partial gene ID: " + e.getMessage());
        }
        
        tableData = filteredData;
        fireTableDataChanged();
        
        logger.info("Filtered annotations: found " + filteredData.size() + " entries for partial gene ID '" + partialGeneId + "'");
    }
    
    /**
     * Search file for specific genes (exact match)
     */
    private void searchFileForGenes(Set<String> geneIds, List<String[]> resultData) throws IOException {
        if (dataFile == null || !dataFile.exists()) {
            return;
        }
        
        Set<String> foundGenes = new HashSet<>();
        Set<String> remainingGenes = new HashSet<>(geneIds);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            boolean skipFirstDataLine = false;
            
            while ((line = reader.readLine()) != null && !remainingGenes.isEmpty()) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] parts = splitLine(line);
                
                // Skip header line
                if (!skipFirstDataLine && parts.length > 1 && 
                    parts[0].toLowerCase().contains("gene") && 
                    !parts[1].matches(".*\\d.*")) {
                    skipFirstDataLine = true;
                    continue;
                }
                
                if (parts.length >= 1) {
                    String geneId = parts[0].trim();
                    if (remainingGenes.contains(geneId) && !foundGenes.contains(geneId)) {
                        // Ensure we have enough columns to match headers
                        String[] rowData = new String[columnHeaders.length];
                        for (int i = 0; i < rowData.length; i++) {
                            rowData[i] = (i < parts.length) ? parts[i].trim() : "";
                        }
                        
                        resultData.add(rowData);
                        remainingGenes.remove(geneId);
                        foundGenes.add(geneId);
                    }
                }
            }
        }
    }
    
    /**
     * Search file for genes matching multiple partial IDs (used in batch partial filtering)
     */
    private void searchFileForPartialGeneIds(Set<String> searchTerms, List<String[]> resultData) throws IOException {
        if (dataFile == null || !dataFile.exists()) {
            return;
        }
        
        Set<String> foundGenes = new HashSet<>();
        
        // Convert search terms to lowercase for case-insensitive matching
        Set<String> lowerSearchTerms = new HashSet<>();
        for (String term : searchTerms) {
            lowerSearchTerms.add(term.toLowerCase());
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            boolean skipFirstDataLine = false;
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] parts = splitLine(line);
                
                // Skip header line
                if (!skipFirstDataLine && parts.length > 1 && 
                    parts[0].toLowerCase().contains("gene") && 
                    !parts[1].matches(".*\\d.*")) {
                    skipFirstDataLine = true;
                    continue;
                }
                
                if (parts.length >= 1) {
                    String geneId = parts[0].trim();
                    String lowerGeneId = geneId.toLowerCase();
                    
                    // Check if this gene matches any search term and hasn't been found yet
                    if (!foundGenes.contains(geneId)) {
                        boolean matches = false;
                        for (String searchTerm : lowerSearchTerms) {
                            if (lowerGeneId.contains(searchTerm)) {
                                matches = true;
                                break;
                            }
                        }
                        
                        if (matches) {
                            // Ensure we have enough columns to match headers
                            String[] rowData = new String[columnHeaders.length];
                            for (int i = 0; i < rowData.length; i++) {
                                rowData[i] = (i < parts.length) ? parts[i].trim() : "";
                            }
                            
                            resultData.add(rowData);
                            foundGenes.add(geneId);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Search file for genes matching partial ID (used in single gene ID filtering)
     */
    private void searchFileForPartialGeneId(String filterText, List<String[]> resultData) throws IOException {
        if (dataFile == null || !dataFile.exists()) {
            return;
        }
        
        Set<String> foundGenes = new HashSet<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            boolean skipFirstDataLine = false;
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] parts = splitLine(line);
                
                // Skip header line
                if (!skipFirstDataLine && parts.length > 1 && 
                    parts[0].toLowerCase().contains("gene") && 
                    !parts[1].matches(".*\\d.*")) {
                    skipFirstDataLine = true;
                    continue;
                }
                
                if (parts.length >= 1) {
                    String geneId = parts[0].trim();
                    
                    // Check if this gene matches the filter and hasn't been found yet
                    if (!foundGenes.contains(geneId) && geneId.toLowerCase().contains(filterText)) {
                        // Ensure we have enough columns to match headers
                        String[] rowData = new String[columnHeaders.length];
                        for (int i = 0; i < rowData.length; i++) {
                            rowData[i] = (i < parts.length) ? parts[i].trim() : "";
                        }
                        
                        resultData.add(rowData);
                        foundGenes.add(geneId);
                    }
                }
            }
        }
    }
    
    // TableModel implementation
    
    @Override
    public int getRowCount() {
        return tableData.size();
    }
    
    @Override
    public int getColumnCount() {
        return columnHeaders != null ? columnHeaders.length : 0;
    }
    
    @Override
    public String getColumnName(int column) {
        if (columnHeaders != null && column >= 0 && column < columnHeaders.length) {
            return columnHeaders[column];
        }
        return "Column " + (column + 1);
    }
    
    @Override
    public Object getValueAt(int row, int column) {
        if (row >= 0 && row < tableData.size() && column >= 0) {
            String[] rowData = tableData.get(row);
            if (column < rowData.length) {
                return rowData[column];
            }
        }
        return "";
    }
    
    @Override
    public Class<?> getColumnClass(int column) {
        return String.class;
    }
    
    @Override
    public boolean isCellEditable(int row, int column) {
        return false; // Read-only for now
    }
    
    // Getters for metadata and statistics
    
    public AnnotationMetadata getMetadata() {
        return metadata;
    }
    
    public AnnotationType getAnnotationType() {
        return annotationType;
    }
    
    public String[] getColumnHeaders() {
        return columnHeaders != null ? columnHeaders.clone() : null;
    }
    
    public int getTotalRows() {
        return totalRows;
    }
    
    public boolean isLoaded() {
        return isLoaded;
    }
    
    public boolean isLoading() {
        return isLoading;
    }
    
    /**
     * Get summary information for display
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(annotationType != null ? annotationType.getShortName() : "Unknown");
        sb.append(", Columns: ").append(getColumnCount());
        sb.append(", Rows: ").append(getRowCount());
        if (isPaginated) {
            sb.append(" (Page ").append(currentPage + 1).append(" of ").append(getTotalPages()).append(")");
        }
        sb.append(", Total: ").append(totalRows);
        
        if (metadata != null) {
            sb.append(", Quality: ").append(String.format("%.1f%%", metadata.getDataQualityScore() * 100));
        }
        
        return sb.toString();
    }
    
    /**
     * Clear all data
     */
    public void clear() {
        tableData.clear();
        geneFilterMap.clear();
        isLoaded = false;
        currentPage = 0;
        fireTableDataChanged();
    }
}