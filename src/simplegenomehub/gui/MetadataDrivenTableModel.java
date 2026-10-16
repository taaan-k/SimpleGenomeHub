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
        FileRows scanned = readFileRows(gene -> false, 0, Integer.MAX_VALUE);
        columnHeaders = scanned.headers;
        totalRows = scanned.totalRows;
        logger.info("Detected " + columnHeaders.length + " columns from file");
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
                    headers[i] = annotationType != null && annotationType != AnnotationType.CUSTOM ?
                        annotationType.getShortName() + "_ID" : "Annotation_ID";
                    break;
                case 2:
                    headers[i] = "Term";
                    break;
                case 3:
                    headers[i] = "Description";
                    break;
                case 4:
                    headers[i] = "Evidence";
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
        if (isLoading || dataFile == null || !dataFile.exists()) return;
        isLoading = true;
        try {
            FileRows loaded = readFileRows(gene -> true,
                isPaginated ? (long) currentPage * pageSize : 0,
                isPaginated ? pageSize : Integer.MAX_VALUE);
            applyRows(loaded);
            isLoaded = true;
            logger.info("Loaded " + tableData.size() + " rows from " + dataFile.getName());
        } catch (IOException e) {
            // A rejected reload must not replace the visible rows with a prefix.
            logger.severe("Failed to load data from " + dataFile.getName() + ": " + e.getMessage());
        } finally {
            isLoading = false;
        }
    }

    private static final class FileRows {
        private String[] headers;
        private int totalRows;
        private final List<String[]> rows = new ArrayList<>();
    }

    /** Read once with the same header and encoding rules as annotation import. */
    private FileRows readFileRows(java.util.function.Predicate<String> include,
                                  long startRow, int maximumRows) throws IOException {
        FileRows result = new FileRows();
        String[] declaredHeaders = null;
        String delimiter = null;
        int maximumColumns = 0;
        try (BufferedReader reader = GeneAnnotationData.openAnnotationReader(dataFile)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("\uFEFF")) line = line.substring(1);
                if (line.trim().isEmpty()) continue;
                boolean comment = line.stripLeading().startsWith("#");
                String candidate = comment ? line.stripLeading().substring(1) : line;
                String candidateDelimiter = delimiter == null ? detectDelimiter(candidate) : delimiter;
                String[] parts = candidate.split(java.util.regex.Pattern.quote(candidateDelimiter), -1);
                if (GeneAnnotationData.isAnnotationHeader(parts)) {
                    for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
                    if (declaredHeaders != null && !Arrays.equals(declaredHeaders, parts))
                        throw formatError(lineNumber, "Conflicting annotation header");
                    if (declaredHeaders == null && result.totalRows > 0
                        && !Arrays.equals(generateDefaultHeaders(maximumColumns), parts))
                        throw formatError(lineNumber, "Annotation header changes columns after data has started");
                    declaredHeaders = parts;
                    delimiter = candidateDelimiter;
                    continue;
                }
                if (comment) continue;
                if (delimiter == null) delimiter = candidateDelimiter;
                if (parts.length < 2 || parts[0].trim().isEmpty())
                    throw formatError(lineNumber, "Expected a gene identifier and annotation columns");
                if (declaredHeaders != null && parts.length > declaredHeaders.length)
                    throw formatError(lineNumber, "Data row has more columns than its header");
                parts[0] = parts[0].trim();
                boolean hasContent = false;
                for (int i = 1; i < parts.length; i++)
                    if (!parts[i].trim().isEmpty() && !"-".equals(parts[i].trim())) hasContent = true;
                if (!hasContent) continue;
                maximumColumns = Math.max(maximumColumns, parts.length);
                if (result.totalRows >= startRow && result.rows.size() < maximumRows && include.test(parts[0]))
                    result.rows.add(parts);
                result.totalRows++;
            }
        }
        result.headers = declaredHeaders == null ? generateDefaultHeaders(maximumColumns) : declaredHeaders;
        for (int row = 0; row < result.rows.size(); row++) {
            String[] original = result.rows.get(row);
            String[] padded = Arrays.copyOf(original, result.headers.length);
            for (int column = original.length; column < padded.length; column++) padded[column] = "";
            result.rows.set(row, padded);
        }
        return result;
    }

    private IOException formatError(int line, String reason) {
        return new IOException(reason + " at " + dataFile.getName() + ":" + line);
    }

    private static String detectDelimiter(String line) {
        if (line.indexOf('\t') >= 0) return "\t";
        if (line.indexOf(',') >= 0) return ",";
        if (line.indexOf(';') >= 0) return ";";
        return "\t";
    }

    private void applyRows(FileRows loaded) {
        boolean structureChanged = !Arrays.equals(columnHeaders, loaded.headers);
        columnHeaders = loaded.headers;
        totalRows = loaded.totalRows;
        tableData = loaded.rows;
        geneFilterMap.clear();
        for (String[] row : tableData)
            geneFilterMap.putIfAbsent(row[0], String.join("\t", row));
        if (structureChanged) fireTableStructureChanged();
        else fireTableDataChanged();
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
            loadData();
            return;
        }
        Set<String> requested = new HashSet<>(geneIds);
        Set<String> lower = new HashSet<>();
        for (String gene : requested) lower.add(gene.toLowerCase(Locale.ROOT));
        filterRows(gene -> partialMatch
            ? lower.stream().anyMatch(term -> gene.toLowerCase(Locale.ROOT).contains(term))
            : requested.contains(gene));
    }

    /** Match every annotation row, including multiple annotations for one gene. */
    public void filterByPartialGeneId(String partialGeneId) {
        if (partialGeneId == null || partialGeneId.trim().isEmpty()) {
            loadData();
            return;
        }
        String requested = partialGeneId.trim().toLowerCase(Locale.ROOT);
        filterRows(gene -> gene.toLowerCase(Locale.ROOT).contains(requested));
    }

    private void filterRows(java.util.function.Predicate<String> predicate) {
        if (dataFile == null || !dataFile.exists()) return;
        try {
            applyRows(readFileRows(predicate, 0, Integer.MAX_VALUE));
        } catch (IOException e) {
            logger.warning("Failed to search annotation file: " + e.getMessage());
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
