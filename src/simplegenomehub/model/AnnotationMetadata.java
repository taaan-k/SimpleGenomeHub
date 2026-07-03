/*
 * Annotation Metadata Model
 * Comprehensive metadata storage for functional annotation files
 */
package simplegenomehub.model;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Comprehensive metadata storage for annotation files
 * Includes file information, data structure, statistics, quality metrics, and performance hints
 * 
 * @author SimpleGenomeHub
 */
public class AnnotationMetadata {
    
    private static final Logger logger = Logger.getLogger(AnnotationMetadata.class.getName());
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    
    // File Information
    private String originalFileName;
    private String originalFilePath;
    private long originalFileSize;
    private LocalDateTime importDate;
    private String importVersion;
    private String fileHash; // MD5 hash for integrity checking
    
    // Data Structure
    private String[] columnHeaders;
    private int columnCount;
    private String delimiter;
    private boolean hasHeaderRow;
    private String encoding;
    private GeneAnnotationData.AnnotationType annotationType;
    
    // Statistics
    private int totalLines;
    private int dataLines;
    private int commentLines;
    private int emptyLines;
    private int uniqueGenes;
    private int uniqueTranscripts;
    private int totalAnnotations;
    private Map<String, Integer> geneAnnotationCounts; // gene -> annotation count
    private Map<String, Integer> termFrequency; // term/pathway -> frequency
    private Map<String, Integer> columnValueCounts; // per-column value distribution
    
    // Quality Metrics
    private int malformedLines;
    private List<String> warnings;
    private double completenessScore; // % of genes with annotations
    private double dataQualityScore; // overall quality score 0-1
    private Map<String, Object> customProperties;
    
    // Performance Hints
    private boolean isLargeDataset; // > 100K annotations
    private boolean needsIndexing;
    private long estimatedMemoryUsage;
    private String recommendedLoadingStrategy; // "full", "lazy", "stream"
    
    // Preview Data
    private List<String> sampleLines; // First few lines for preview
    private Map<String, String> columnSamples; // Sample values per column
    
    /**
     * Default constructor
     */
    public AnnotationMetadata() {
        this.importDate = LocalDateTime.now();
        this.importVersion = "SimpleGenomeHub-2.0";
        this.geneAnnotationCounts = new HashMap<>();
        this.termFrequency = new HashMap<>();
        this.columnValueCounts = new HashMap<>();
        this.warnings = new ArrayList<>();
        this.customProperties = new HashMap<>();
        this.sampleLines = new ArrayList<>();
        this.columnSamples = new HashMap<>();
        this.encoding = "UTF-8";
        this.delimiter = "\t";
        this.hasHeaderRow = false;
    }
    
    /**
     * Constructor with basic file information
     */
    public AnnotationMetadata(File sourceFile, GeneAnnotationData.AnnotationType type) {
        this();
        this.originalFileName = sourceFile.getName();
        this.originalFilePath = sourceFile.getAbsolutePath();
        this.originalFileSize = sourceFile.length();
        this.annotationType = type;
        
        // Calculate file hash for integrity
        try {
            this.fileHash = calculateFileHash(sourceFile);
        } catch (Exception e) {
            logger.warning("Failed to calculate file hash: " + e.getMessage());
            this.fileHash = "unknown";
        }
    }
    
    /**
     * Analyze file and generate comprehensive metadata
     */
    public static AnnotationMetadata analyzeFile(File file, GeneAnnotationData.AnnotationType type) {
        AnnotationMetadata metadata = new AnnotationMetadata(file, type);
        
        try {
            metadata.performFileAnalysis(file);
            metadata.calculateQualityMetrics();
            metadata.determinePerformanceHints();
            
            logger.info("Generated metadata for " + file.getName() + ": " + 
                       metadata.totalLines + " lines, " + 
                       metadata.uniqueGenes + " genes, " + 
                       metadata.totalAnnotations + " annotations");
            
        } catch (Exception e) {
            logger.severe("Failed to analyze file " + file.getName() + ": " + e.getMessage());
            metadata.addWarning("File analysis failed: " + e.getMessage());
        }
        
        return metadata;
    }
    
    /**
     * Perform comprehensive file analysis
     */
    private void performFileAnalysis(File file) throws IOException {
        Set<String> uniqueGeneSet = new HashSet<>();
        Set<String> uniqueTranscriptSet = new HashSet<>();
        Set<String> uniqueTermSet = new HashSet<>();
        Map<String, Set<String>> columnValueSets = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            boolean headerProcessed = false;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                totalLines++;
                
                // Store sample lines for preview
                if (sampleLines.size() < 20) {
                    sampleLines.add(line);
                }
                
                // Handle empty lines
                if (line.trim().isEmpty()) {
                    emptyLines++;
                    continue;
                }
                
                // Handle comment lines
                if (line.startsWith("#")) {
                    commentLines++;
                    
                    // Check if comment line contains headers
                    if (!headerProcessed && line.length() > 1) {
                        String headerLine = line.substring(1).trim();
                        if (headerLine.contains("\t") || headerLine.contains(",")) {
                            analyzeHeaders(headerLine);
                            headerProcessed = true;
                        }
                    }
                    continue;
                }
                
                // Handle data lines
                dataLines++;
                
                // Auto-detect delimiter if not set
                if (delimiter.equals("\t") && !line.contains("\t") && line.contains(",")) {
                    delimiter = ",";
                } else if (delimiter.equals("\t") && !line.contains("\t") && line.contains(";")) {
                    delimiter = ";";
                }
                
                // Parse data line
                String[] parts = line.split(delimiter, -1);
                
                // Update column count
                if (columnCount == 0 || parts.length > columnCount) {
                    columnCount = parts.length;
                }
                
                // Check for header row (first data line)
                if (!headerProcessed && lineNumber <= 3) {
                    if (isLikelyHeaderRow(parts)) {
                        analyzeHeaders(line);
                        hasHeaderRow = true;
                        headerProcessed = true;
                        dataLines--; // Don't count header as data
                        continue;
                    }
                }
                
                // Validate line format
                if (parts.length < 2) {
                    malformedLines++;
                    addWarning("Line " + lineNumber + ": insufficient columns (" + parts.length + ")");
                    continue;
                }
                
                // Analyze data content
                analyzeDataLine(parts, uniqueGeneSet, uniqueTranscriptSet, uniqueTermSet, columnValueSets);
                totalAnnotations++;
                
                // Update gene annotation counts
                String geneId = parts[0].trim();
                if (!geneId.isEmpty()) {
                    geneAnnotationCounts.put(geneId, geneAnnotationCounts.getOrDefault(geneId, 0) + 1);
                }
            }
        }
        
        // Final statistics
        uniqueGenes = uniqueGeneSet.size();
        uniqueTranscripts = uniqueTranscriptSet.size();
        
        // Process term frequency
        for (String term : uniqueTermSet) {
            termFrequency.put(term, termFrequency.getOrDefault(term, 0) + 1);
        }
        
        // Generate column samples
        generateColumnSamples(columnValueSets);
        
        // Set default headers if not detected
        if (columnHeaders == null && columnCount > 0) {
            generateDefaultHeaders();
        }
    }
    
    /**
     * Analyze header line to extract column information
     */
    private void analyzeHeaders(String headerLine) {
        String[] headers = headerLine.split(delimiter, -1);
        columnHeaders = new String[headers.length];
        
        for (int i = 0; i < headers.length; i++) {
            columnHeaders[i] = headers[i].trim();
        }
        
        columnCount = headers.length;
        hasHeaderRow = true;
    }
    
    /**
     * Check if a line is likely a header row
     */
    private boolean isLikelyHeaderRow(String[] parts) {
        if (parts.length < 2) return false;
        
        // Check for common header patterns
        String firstColumn = parts[0].toLowerCase().trim();
        return firstColumn.contains("gene") || 
               firstColumn.contains("id") || 
               firstColumn.equals("gene_id") ||
               firstColumn.equals("geneid") ||
               parts.length > 2 && parts[1].toLowerCase().contains("annotation");
    }
    
    /**
     * Analyze individual data line
     */
    private void analyzeDataLine(String[] parts, Set<String> geneSet, Set<String> transcriptSet, 
                                Set<String> termSet, Map<String, Set<String>> columnValueSets) {
        
        for (int i = 0; i < parts.length; i++) {
            String value = parts[i].trim();
            if (value.isEmpty() || value.equals("-")) continue;
            
            // Track unique values per column
            columnValueSets.computeIfAbsent("col_" + i, k -> new HashSet<>()).add(value);
            
            // Analyze specific columns
            switch (i) {
                case 0: // Gene/Transcript ID
                    geneSet.add(value);
                    if (value.contains(".") || value.toLowerCase().contains("transcript")) {
                        transcriptSet.add(value);
                    }
                    break;
                case 1: // Annotation ID/Term
                    termSet.add(value);
                    break;
                case 2: // Term/Description
                    if (value.length() > 10) { // Likely description
                        termSet.add(value);
                    }
                    break;
            }
        }
    }
    
    /**
     * Generate column samples for preview
     */
    private void generateColumnSamples(Map<String, Set<String>> columnValueSets) {
        for (Map.Entry<String, Set<String>> entry : columnValueSets.entrySet()) {
            Set<String> values = entry.getValue();
            if (!values.isEmpty()) {
                // Take first few unique values as samples
                String sample = values.stream()
                    .limit(3)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
                
                columnSamples.put(entry.getKey(), sample);
                
                // Store column value counts
                columnValueCounts.put(entry.getKey(), values.size());
            }
        }
    }
    
    /**
     * Generate default column headers
     */
    private void generateDefaultHeaders() {
        columnHeaders = new String[columnCount];
        
        for (int i = 0; i < columnCount; i++) {
            switch (i) {
                case 0:
                    columnHeaders[i] = "Gene_ID";
                    break;
                case 1:
                    columnHeaders[i] = annotationType != null ? 
                        annotationType.getShortName() + "_ID" : "Annotation_ID";
                    break;
                case 2:
                    columnHeaders[i] = "Term";
                    break;
                case 3:
                    columnHeaders[i] = "Description";
                    break;
                default:
                    columnHeaders[i] = "Column_" + (i + 1);
                    break;
            }
        }
    }
    
    /**
     * Calculate quality metrics
     */
    private void calculateQualityMetrics() {
        // Completeness score: ratio of genes with annotations
        if (totalAnnotations > 0 && uniqueGenes > 0) {
            completenessScore = (double) uniqueGenes / Math.max(uniqueGenes, totalAnnotations);
        }
        
        // Data quality score based on various factors
        double malformedRatio = (double) malformedLines / totalLines;
        double emptyRatio = (double) emptyLines / totalLines;
        double columnConsistency = columnCount > 0 ? 1.0 : 0.0;
        
        dataQualityScore = Math.max(0.0, 
            1.0 - (malformedRatio * 0.5) - (emptyRatio * 0.2) + (columnConsistency * 0.3));
        
        // Add quality warnings
        if (malformedLines > totalLines * 0.05) {
            addWarning("High number of malformed lines: " + malformedLines + " (" + 
                      String.format("%.1f%%", malformedRatio * 100) + ")");
        }
        
        if (uniqueGenes == 0) {
            addWarning("No valid gene IDs detected");
        }
        
        if (dataQualityScore < 0.7) {
            addWarning("Low data quality score: " + String.format("%.2f", dataQualityScore));
        }
    }
    
    /**
     * Determine performance optimization hints
     */
    private void determinePerformanceHints() {
        // Determine if large dataset
        isLargeDataset = totalAnnotations > 100000 || originalFileSize > 50 * 1024 * 1024; // 50MB
        
        // Determine if indexing is needed
        needsIndexing = uniqueGenes > 10000 || termFrequency.size() > 5000;
        
        // Estimate memory usage (rough calculation)
        estimatedMemoryUsage = (long) (totalAnnotations * 200 + uniqueGenes * 50 + termFrequency.size() * 100);
        
        // Recommend loading strategy
        if (totalAnnotations < 10000) {
            recommendedLoadingStrategy = "full";
        } else if (totalAnnotations < 100000) {
            recommendedLoadingStrategy = "lazy";
        } else {
            recommendedLoadingStrategy = "stream";
        }
        
        // Add performance hints as custom properties
        customProperties.put("recommend_page_size", Math.min(1000, Math.max(100, totalAnnotations / 100)));
        customProperties.put("memory_efficient_mode", isLargeDataset);
        customProperties.put("enable_search_index", needsIndexing);
    }
    
    /**
     * Calculate MD5 hash of file for integrity checking
     */
    private String calculateFileHash(File file) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hash = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    /**
     * Save metadata to JSON file
     */
    public boolean saveToFile(File metadataFile) {
        try (FileWriter writer = new FileWriter(metadataFile)) {
            gson.toJson(this, writer);
            logger.info("Saved metadata to: " + metadataFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            logger.severe("Failed to save metadata: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Load metadata from JSON file
     */
    public static AnnotationMetadata loadFromFile(File metadataFile) {
        try (FileReader reader = new FileReader(metadataFile)) {
            AnnotationMetadata metadata = gson.fromJson(reader, AnnotationMetadata.class);
            logger.info("Loaded metadata from: " + metadataFile.getAbsolutePath());
            return metadata;
        } catch (IOException | JsonSyntaxException e) {
            logger.severe("Failed to load metadata: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Add warning message
     */
    public void addWarning(String warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
        logger.warning("Annotation metadata warning: " + warning);
    }
    
    /**
     * Get summary statistics as formatted string
     */
    public String getSummaryString() {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(originalFileName).append("\n");
        sb.append("Size: ").append(formatFileSize(originalFileSize)).append("\n");
        sb.append("Lines: ").append(totalLines).append(" (").append(dataLines).append(" data, ");
        sb.append(commentLines).append(" comments)").append("\n");
        sb.append("Genes: ").append(uniqueGenes).append(" unique").append("\n");
        sb.append("Annotations: ").append(totalAnnotations).append("\n");
        sb.append("Columns: ").append(columnCount);
        if (columnHeaders != null) {
            sb.append(" (").append(String.join(", ", columnHeaders)).append(")");
        }
        sb.append("\n");
        sb.append("Quality Score: ").append(String.format("%.2f", dataQualityScore)).append("\n");
        sb.append("Estimated Memory: ").append(formatFileSize(estimatedMemoryUsage)).append("\n");
        sb.append("Loading Strategy: ").append(recommendedLoadingStrategy);
        
        return sb.toString();
    }
    
    /**
     * Format file size for display
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    // Getters and Setters
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    
    public String getOriginalFilePath() { return originalFilePath; }
    public void setOriginalFilePath(String originalFilePath) { this.originalFilePath = originalFilePath; }
    
    public long getOriginalFileSize() { return originalFileSize; }
    public void setOriginalFileSize(long originalFileSize) { this.originalFileSize = originalFileSize; }
    
    public LocalDateTime getImportDate() { return importDate; }
    public void setImportDate(LocalDateTime importDate) { this.importDate = importDate; }
    
    public String getImportVersion() { return importVersion; }
    public void setImportVersion(String importVersion) { this.importVersion = importVersion; }
    
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    
    public String[] getColumnHeaders() { return columnHeaders != null ? columnHeaders.clone() : null; }
    public void setColumnHeaders(String[] columnHeaders) { 
        this.columnHeaders = columnHeaders != null ? columnHeaders.clone() : null; 
    }
    
    public int getColumnCount() { return columnCount; }
    public void setColumnCount(int columnCount) { this.columnCount = columnCount; }
    
    public String getDelimiter() { return delimiter; }
    public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
    
    public boolean hasHeaderRow() { return hasHeaderRow; }
    public void setHasHeaderRow(boolean hasHeaderRow) { this.hasHeaderRow = hasHeaderRow; }
    
    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }
    
    public GeneAnnotationData.AnnotationType getAnnotationType() { return annotationType; }
    public void setAnnotationType(GeneAnnotationData.AnnotationType annotationType) { 
        this.annotationType = annotationType; 
    }
    
    public int getTotalLines() { return totalLines; }
    public void setTotalLines(int totalLines) { this.totalLines = totalLines; }
    
    public int getDataLines() { return dataLines; }
    public void setDataLines(int dataLines) { this.dataLines = dataLines; }
    
    public int getCommentLines() { return commentLines; }
    public void setCommentLines(int commentLines) { this.commentLines = commentLines; }
    
    public int getEmptyLines() { return emptyLines; }
    public void setEmptyLines(int emptyLines) { this.emptyLines = emptyLines; }
    
    public int getUniqueGenes() { return uniqueGenes; }
    public void setUniqueGenes(int uniqueGenes) { this.uniqueGenes = uniqueGenes; }
    
    public int getUniqueTranscripts() { return uniqueTranscripts; }
    public void setUniqueTranscripts(int uniqueTranscripts) { this.uniqueTranscripts = uniqueTranscripts; }
    
    public int getTotalAnnotations() { return totalAnnotations; }
    public void setTotalAnnotations(int totalAnnotations) { this.totalAnnotations = totalAnnotations; }
    
    public Map<String, Integer> getGeneAnnotationCounts() { return new HashMap<>(geneAnnotationCounts); }
    public void setGeneAnnotationCounts(Map<String, Integer> geneAnnotationCounts) { 
        this.geneAnnotationCounts = new HashMap<>(geneAnnotationCounts); 
    }
    
    public Map<String, Integer> getTermFrequency() { return new HashMap<>(termFrequency); }
    public void setTermFrequency(Map<String, Integer> termFrequency) { 
        this.termFrequency = new HashMap<>(termFrequency); 
    }
    
    public Map<String, Integer> getColumnValueCounts() { return new HashMap<>(columnValueCounts); }
    public void setColumnValueCounts(Map<String, Integer> columnValueCounts) { 
        this.columnValueCounts = new HashMap<>(columnValueCounts); 
    }
    
    public int getMalformedLines() { return malformedLines; }
    public void setMalformedLines(int malformedLines) { this.malformedLines = malformedLines; }
    
    public List<String> getWarnings() { return new ArrayList<>(warnings); }
    public void setWarnings(List<String> warnings) { this.warnings = new ArrayList<>(warnings); }
    
    public double getCompletenessScore() { return completenessScore; }
    public void setCompletenessScore(double completenessScore) { this.completenessScore = completenessScore; }
    
    public double getDataQualityScore() { return dataQualityScore; }
    public void setDataQualityScore(double dataQualityScore) { this.dataQualityScore = dataQualityScore; }
    
    public Map<String, Object> getCustomProperties() { return new HashMap<>(customProperties); }
    public void setCustomProperties(Map<String, Object> customProperties) { 
        this.customProperties = new HashMap<>(customProperties); 
    }
    
    public boolean isLargeDataset() { return isLargeDataset; }
    public void setLargeDataset(boolean largeDataset) { this.isLargeDataset = largeDataset; }
    
    public boolean needsIndexing() { return needsIndexing; }
    public void setNeedsIndexing(boolean needsIndexing) { this.needsIndexing = needsIndexing; }
    
    public long getEstimatedMemoryUsage() { return estimatedMemoryUsage; }
    public void setEstimatedMemoryUsage(long estimatedMemoryUsage) { 
        this.estimatedMemoryUsage = estimatedMemoryUsage; 
    }
    
    public String getRecommendedLoadingStrategy() { return recommendedLoadingStrategy; }
    public void setRecommendedLoadingStrategy(String recommendedLoadingStrategy) { 
        this.recommendedLoadingStrategy = recommendedLoadingStrategy; 
    }
    
    public List<String> getSampleLines() { return new ArrayList<>(sampleLines); }
    public void setSampleLines(List<String> sampleLines) { this.sampleLines = new ArrayList<>(sampleLines); }
    
    public Map<String, String> getColumnSamples() { return new HashMap<>(columnSamples); }
    public void setColumnSamples(Map<String, String> columnSamples) { 
        this.columnSamples = new HashMap<>(columnSamples); 
    }
    
    @Override
    public String toString() {
        return String.format("AnnotationMetadata[%s: %d lines, %d genes, %d annotations]", 
                           originalFileName, totalLines, uniqueGenes, totalAnnotations);
    }
    
    /**
     * Custom adapter for LocalDateTime JSON serialization
     */
    private static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>, 
                                                         com.google.gson.JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime dateTime, java.lang.reflect.Type type, 
                                                    com.google.gson.JsonSerializationContext context) {
            return new com.google.gson.JsonPrimitive(dateTime.format(formatter));
        }
        
        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type type, 
                                       com.google.gson.JsonDeserializationContext context) {
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }
}