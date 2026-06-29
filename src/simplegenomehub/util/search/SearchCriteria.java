/*
 * Search Criteria
 */
package simplegenomehub.util.search;

import java.util.Date;

/**
 * Search criteria for filtering species collections
 * Supports multiple search parameters and combinations
 * 
 * @author SimpleGenomeHub
 */
public class SearchCriteria {
    
    // Text search fields
    private String speciesNamePattern;
    private String versionPattern;
    private String notesPattern;
    private boolean caseSensitive;
    private boolean useRegex;
    
    // File existence filters
    private Boolean hasGenomeFile;
    private Boolean hasAnnotationFile;
    private Boolean hasExtractedSequences;
    private Boolean hasStatistics;
    
    // Genome size filters
    private Long minGenomeSize;
    private Long maxGenomeSize;
    
    // Gene count filters
    private Integer minGeneCount;
    private Integer maxGeneCount;
    
    // GC content filters
    private Double minGcContent;
    private Double maxGcContent;
    
    // Date filters
    private Date importedAfter;
    private Date importedBefore;
    
    // Assembly level filters
    private String assemblyLevel;
    
    // Annotation source filters
    private String annotationSource;
    
    /**
     * Default constructor
     */
    public SearchCriteria() {
        this.caseSensitive = false;
        this.useRegex = false;
    }
    
    // Getters and setters
    
    public String getSpeciesNamePattern() {
        return speciesNamePattern;
    }
    
    public void setSpeciesNamePattern(String speciesNamePattern) {
        this.speciesNamePattern = speciesNamePattern;
    }
    
    public String getVersionPattern() {
        return versionPattern;
    }
    
    public void setVersionPattern(String versionPattern) {
        this.versionPattern = versionPattern;
    }
    
    public String getNotesPattern() {
        return notesPattern;
    }
    
    public void setNotesPattern(String notesPattern) {
        this.notesPattern = notesPattern;
    }
    
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
    
    public boolean isUseRegex() {
        return useRegex;
    }
    
    public void setUseRegex(boolean useRegex) {
        this.useRegex = useRegex;
    }
    
    public Boolean getHasGenomeFile() {
        return hasGenomeFile;
    }
    
    public void setHasGenomeFile(Boolean hasGenomeFile) {
        this.hasGenomeFile = hasGenomeFile;
    }
    
    public Boolean getHasAnnotationFile() {
        return hasAnnotationFile;
    }
    
    public void setHasAnnotationFile(Boolean hasAnnotationFile) {
        this.hasAnnotationFile = hasAnnotationFile;
    }
    
    public Boolean getHasExtractedSequences() {
        return hasExtractedSequences;
    }
    
    public void setHasExtractedSequences(Boolean hasExtractedSequences) {
        this.hasExtractedSequences = hasExtractedSequences;
    }
    
    public Boolean getHasStatistics() {
        return hasStatistics;
    }
    
    public void setHasStatistics(Boolean hasStatistics) {
        this.hasStatistics = hasStatistics;
    }
    
    public Long getMinGenomeSize() {
        return minGenomeSize;
    }
    
    public void setMinGenomeSize(Long minGenomeSize) {
        this.minGenomeSize = minGenomeSize;
    }
    
    public Long getMaxGenomeSize() {
        return maxGenomeSize;
    }
    
    public void setMaxGenomeSize(Long maxGenomeSize) {
        this.maxGenomeSize = maxGenomeSize;
    }
    
    public Integer getMinGeneCount() {
        return minGeneCount;
    }
    
    public void setMinGeneCount(Integer minGeneCount) {
        this.minGeneCount = minGeneCount;
    }
    
    public Integer getMaxGeneCount() {
        return maxGeneCount;
    }
    
    public void setMaxGeneCount(Integer maxGeneCount) {
        this.maxGeneCount = maxGeneCount;
    }
    
    public Double getMinGcContent() {
        return minGcContent;
    }
    
    public void setMinGcContent(Double minGcContent) {
        this.minGcContent = minGcContent;
    }
    
    public Double getMaxGcContent() {
        return maxGcContent;
    }
    
    public void setMaxGcContent(Double maxGcContent) {
        this.maxGcContent = maxGcContent;
    }
    
    public Date getImportedAfter() {
        return importedAfter;
    }
    
    public void setImportedAfter(Date importedAfter) {
        this.importedAfter = importedAfter;
    }
    
    public Date getImportedBefore() {
        return importedBefore;
    }
    
    public void setImportedBefore(Date importedBefore) {
        this.importedBefore = importedBefore;
    }
    
    public String getAssemblyLevel() {
        return assemblyLevel;
    }
    
    public void setAssemblyLevel(String assemblyLevel) {
        this.assemblyLevel = assemblyLevel;
    }
    
    public String getAnnotationSource() {
        return annotationSource;
    }
    
    public void setAnnotationSource(String annotationSource) {
        this.annotationSource = annotationSource;
    }
    
    /**
     * Check if any search criteria are set
     */
    public boolean hasAnyCriteria() {
        return speciesNamePattern != null || versionPattern != null || notesPattern != null ||
               hasGenomeFile != null || hasAnnotationFile != null || hasExtractedSequences != null ||
               hasStatistics != null || minGenomeSize != null || maxGenomeSize != null ||
               minGeneCount != null || maxGeneCount != null || minGcContent != null ||
               maxGcContent != null || importedAfter != null || importedBefore != null ||
               assemblyLevel != null || annotationSource != null;
    }
    
    /**
     * Clear all search criteria
     */
    public void clear() {
        speciesNamePattern = null;
        versionPattern = null;
        notesPattern = null;
        hasGenomeFile = null;
        hasAnnotationFile = null;
        hasExtractedSequences = null;
        hasStatistics = null;
        minGenomeSize = null;
        maxGenomeSize = null;
        minGeneCount = null;
        maxGeneCount = null;
        minGcContent = null;
        maxGcContent = null;
        importedAfter = null;
        importedBefore = null;
        assemblyLevel = null;
        annotationSource = null;
        caseSensitive = false;
        useRegex = false;
    }
    
    /**
     * Create a copy of this criteria
     */
    public SearchCriteria copy() {
        SearchCriteria copy = new SearchCriteria();
        copy.speciesNamePattern = this.speciesNamePattern;
        copy.versionPattern = this.versionPattern;
        copy.notesPattern = this.notesPattern;
        copy.caseSensitive = this.caseSensitive;
        copy.useRegex = this.useRegex;
        copy.hasGenomeFile = this.hasGenomeFile;
        copy.hasAnnotationFile = this.hasAnnotationFile;
        copy.hasExtractedSequences = this.hasExtractedSequences;
        copy.hasStatistics = this.hasStatistics;
        copy.minGenomeSize = this.minGenomeSize;
        copy.maxGenomeSize = this.maxGenomeSize;
        copy.minGeneCount = this.minGeneCount;
        copy.maxGeneCount = this.maxGeneCount;
        copy.minGcContent = this.minGcContent;
        copy.maxGcContent = this.maxGcContent;
        copy.importedAfter = this.importedAfter;
        copy.importedBefore = this.importedBefore;
        copy.assemblyLevel = this.assemblyLevel;
        copy.annotationSource = this.annotationSource;
        return copy;
    }
}