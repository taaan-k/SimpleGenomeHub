/*
 * Enhanced Enrichment Result with comprehensive statistical information
 * Compatible with TBtools enrichment analysis output
 */
package simplegenomehub.util.enrichment;

import java.util.*;

/**
 * Enhanced enrichment result that extends the basic EnrichmentResult
 * with additional statistical information and metadata
 * 
 * @author SimpleGenomeHub
 */
public class EnhancedEnrichmentResult {
    
    // Basic identification
    private String termId;
    private String termName;
    private String category;
    private String description;
    
    // Statistical measures
    private double pValue;
    private double adjustedPValue;
    private double enrichmentRatio;
    private double foldEnrichment;
    private double oddsRatio;
    
    // Count information
    private int geneCount;           // Genes in test set with this term
    private int termSize;            // Total genes with this term in background
    private int testSetSize;         // Total genes in test set
    private int backgroundCount;     // Total genes in background
    
    // Gene information
    private List<String> geneIds;
    private List<String> geneSymbols;
    private String geneListString;   // Comma-separated gene list
    
    // Confidence and quality measures
    private double confidenceScore;
    private int evidenceLevel;
    private boolean isSignificant;
    
    // Additional metadata
    private Map<String, Object> metadata;
    private String analysisMethod;
    private Date analysisDate;
    
    // Hierarchy information (for GO terms)
    private List<String> parentTerms;
    private List<String> childTerms;
    private int hierarchyLevel;
    
    /**
     * Default constructor
     */
    public EnhancedEnrichmentResult() {
        this.geneIds = new ArrayList<>();
        this.geneSymbols = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.parentTerms = new ArrayList<>();
        this.childTerms = new ArrayList<>();
        this.analysisDate = new Date();
        this.isSignificant = false;
    }
    
    /**
     * Constructor with basic information
     */
    public EnhancedEnrichmentResult(String termId, String termName, String category) {
        this();
        this.termId = termId;
        this.termName = termName;
        this.category = category;
    }
    
    /**
     * Calculate derived statistics
     */
    public void calculateDerivedStatistics() {
        // Calculate fold enrichment
        if (termSize > 0 && testSetSize > 0 && backgroundCount > 0) {
            double expectedRatio = (double) termSize / backgroundCount;
            double observedRatio = (double) geneCount / testSetSize;
            this.foldEnrichment = observedRatio / expectedRatio;
            this.enrichmentRatio = foldEnrichment;
        }
        
        // Calculate odds ratio
        if (termSize > 0 && testSetSize > 0 && backgroundCount > 0) {
            int a = geneCount;  // test genes with term
            int b = testSetSize - geneCount;  // test genes without term
            int c = termSize - geneCount;  // background genes with term (excluding test)
            int d = backgroundCount - termSize - b;  // background genes without term
            
            if (b > 0 && d > 0) {
                this.oddsRatio = ((double) a * d) / ((double) b * c);
            }
        }
        
        // Calculate confidence score based on multiple factors
        calculateConfidenceScore();
        
        // Determine significance
        this.isSignificant = (adjustedPValue <= 0.05 && geneCount >= 3);
        
        // Create gene list string
        this.geneListString = String.join(",", geneIds);
    }
    
    /**
     * Calculate confidence score based on multiple quality metrics
     */
    private void calculateConfidenceScore() {
        double score = 0.0;
        
        // P-value contribution (0-40 points)
        if (adjustedPValue <= 0.001) score += 40;
        else if (adjustedPValue <= 0.01) score += 30;
        else if (adjustedPValue <= 0.05) score += 20;
        else score += 10;
        
        // Gene count contribution (0-30 points)
        if (geneCount >= 10) score += 30;
        else if (geneCount >= 5) score += 20;
        else if (geneCount >= 3) score += 10;
        
        // Enrichment ratio contribution (0-20 points)
        if (enrichmentRatio >= 5.0) score += 20;
        else if (enrichmentRatio >= 2.0) score += 15;
        else if (enrichmentRatio >= 1.5) score += 10;
        else score += 5;
        
        // Term size contribution (0-10 points) - prefer moderate term sizes
        if (termSize >= 20 && termSize <= 200) score += 10;
        else if (termSize >= 10 && termSize <= 500) score += 7;
        else score += 3;
        
        this.confidenceScore = Math.min(100.0, score);
    }
    
    /**
     * Get formatted statistical summary
     */
    public String getStatisticalSummary() {
        return String.format(
            "P-value: %.2e, FDR: %.2e, Enrichment: %.2fx, Genes: %d/%d, Confidence: %.0f%%",
            pValue, adjustedPValue, enrichmentRatio, geneCount, termSize, confidenceScore
        );
    }
    
    /**
     * Get enrichment strength category
     */
    public String getEnrichmentStrength() {
        if (enrichmentRatio >= 5.0) return "Very Strong";
        else if (enrichmentRatio >= 2.0) return "Strong";
        else if (enrichmentRatio >= 1.5) return "Moderate";
        else if (enrichmentRatio >= 1.2) return "Weak";
        else return "Not Enriched";
    }
    
    /**
     * Get significance level
     */
    public String getSignificanceLevel() {
        if (adjustedPValue <= 0.001) return "Highly Significant";
        else if (adjustedPValue <= 0.01) return "Very Significant";
        else if (adjustedPValue <= 0.05) return "Significant";
        else return "Not Significant";
    }
    
    /**
     * Add metadata entry
     */
    public void addMetadata(String key, Object value) {
        this.metadata.put(key, value);
    }
    
    /**
     * Get metadata value
     */
    public Object getMetadata(String key) {
        return this.metadata.get(key);
    }
    
    /**
     * Check if result passes quality thresholds
     */
    public boolean passesQualityThresholds(double pThreshold, int minGenes, double minEnrichment) {
        return adjustedPValue <= pThreshold && 
               geneCount >= minGenes && 
               enrichmentRatio >= minEnrichment;
    }
    
    /**
     * Export to TSV format
     */
    public String toTSV() {
        return String.join("\t",
            termId != null ? termId : "",
            termName != null ? termName : "",
            category != null ? category : "",
            String.valueOf(geneCount),
            String.valueOf(termSize),
            String.format("%.2e", pValue),
            String.format("%.2e", adjustedPValue),
            String.format("%.3f", enrichmentRatio),
            String.format("%.3f", oddsRatio),
            geneListString != null ? geneListString : "",
            String.format("%.1f", confidenceScore)
        );
    }
    
    /**
     * Get TSV header
     */
    public static String getTSVHeader() {
        return String.join("\t",
            "Term_ID", "Term_Name", "Category", "Gene_Count", "Term_Size",
            "P_Value", "Adjusted_P_Value", "Enrichment_Ratio", "Odds_Ratio",
            "Gene_List", "Confidence_Score"
        );
    }
    
    // Standard getters and setters
    public String getTermId() { return termId; }
    public void setTermId(String termId) { this.termId = termId; }
    
    public String getTermName() { return termName; }
    public void setTermName(String termName) { this.termName = termName; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public double getPValue() { return pValue; }
    public void setPValue(double pValue) { this.pValue = pValue; }
    
    public double getAdjustedPValue() { return adjustedPValue; }
    public void setAdjustedPValue(double adjustedPValue) { this.adjustedPValue = adjustedPValue; }
    
    public double getEnrichmentRatio() { return enrichmentRatio; }
    public void setEnrichmentRatio(double enrichmentRatio) { this.enrichmentRatio = enrichmentRatio; }
    
    public double getFoldEnrichment() { return foldEnrichment; }
    public void setFoldEnrichment(double foldEnrichment) { this.foldEnrichment = foldEnrichment; }
    
    public double getOddsRatio() { return oddsRatio; }
    public void setOddsRatio(double oddsRatio) { this.oddsRatio = oddsRatio; }
    
    public int getGeneCount() { return geneCount; }
    public void setGeneCount(int geneCount) { 
        this.geneCount = geneCount;
        calculateDerivedStatistics();
    }
    
    public int getTermSize() { return termSize; }
    public void setTermSize(int termSize) { 
        this.termSize = termSize;
        calculateDerivedStatistics();
    }
    
    public int getTestSetSize() { return testSetSize; }
    public void setTestSetSize(int testSetSize) { 
        this.testSetSize = testSetSize;
        calculateDerivedStatistics();
    }
    
    public int getBackgroundCount() { return backgroundCount; }
    public void setBackgroundCount(int backgroundCount) { 
        this.backgroundCount = backgroundCount;
        calculateDerivedStatistics();
    }
    
    public List<String> getGeneIds() { return new ArrayList<>(geneIds); }
    public void setGeneIds(List<String> geneIds) { 
        this.geneIds = new ArrayList<>(geneIds);
        this.geneListString = String.join(",", this.geneIds);
    }
    
    public void addGeneId(String geneId) {
        if (!this.geneIds.contains(geneId)) {
            this.geneIds.add(geneId);
            this.geneCount = this.geneIds.size();
            this.geneListString = String.join(",", this.geneIds);
        }
    }
    
    public List<String> getGeneSymbols() { return new ArrayList<>(geneSymbols); }
    public void setGeneSymbols(List<String> geneSymbols) { this.geneSymbols = new ArrayList<>(geneSymbols); }
    
    public String getGeneListString() { return geneListString; }
    public String getGeneList() { return geneListString; }  // Alias for TBtools compatibility
    
    public double getConfidenceScore() { return confidenceScore; }
    
    // TBtools compatible methods
    public int getGoLevel() { return hierarchyLevel; }
    public void setGoLevel(int goLevel) { this.hierarchyLevel = goLevel; }
    
    public int getHitInBackground() { return termSize - geneCount; }
    public int getNumOfSet() { return testSetSize; }
    public int getNumOfBackground() { return backgroundCount; }
    
    public int getEvidenceLevel() { return evidenceLevel; }
    public void setEvidenceLevel(int evidenceLevel) { this.evidenceLevel = evidenceLevel; }
    
    public boolean isSignificant() { return isSignificant; }
    
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = new HashMap<>(metadata); }
    
    public String getAnalysisMethod() { return analysisMethod; }
    public void setAnalysisMethod(String analysisMethod) { this.analysisMethod = analysisMethod; }
    
    public Date getAnalysisDate() { return analysisDate; }
    public void setAnalysisDate(Date analysisDate) { this.analysisDate = analysisDate; }
    
    public List<String> getParentTerms() { return new ArrayList<>(parentTerms); }
    public void setParentTerms(List<String> parentTerms) { this.parentTerms = new ArrayList<>(parentTerms); }
    
    public List<String> getChildTerms() { return new ArrayList<>(childTerms); }
    public void setChildTerms(List<String> childTerms) { this.childTerms = new ArrayList<>(childTerms); }
    
    public int getHierarchyLevel() { return hierarchyLevel; }
    public void setHierarchyLevel(int hierarchyLevel) { this.hierarchyLevel = hierarchyLevel; }
    
    @Override
    public String toString() {
        return String.format("%s (%s): %s [%d genes, %.2fx enriched, p=%.2e]",
                           termName != null ? termName : termId,
                           termId,
                           getSignificanceLevel(),
                           geneCount,
                           enrichmentRatio,
                           adjustedPValue);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        EnhancedEnrichmentResult other = (EnhancedEnrichmentResult) obj;
        return Objects.equals(termId, other.termId) && 
               Objects.equals(category, other.category);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(termId, category);
    }
}