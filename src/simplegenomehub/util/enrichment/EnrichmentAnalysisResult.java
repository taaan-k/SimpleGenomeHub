/*
 * Container for complete enrichment analysis results
 * Includes analysis metadata, parameters, and statistical summary
 */
package simplegenomehub.util.enrichment;

import simplegenomehub.model.GeneAnnotationData.AnnotationType;

import java.util.*;

/**
 * Complete enrichment analysis result container
 * Contains results, metadata, and analysis summary
 * 
 * @author SimpleGenomeHub
 */
public class EnrichmentAnalysisResult {
    
    // Analysis identification
    private String analysisId;
    private Date analysisDate;
    private AnnotationType annotationType;
    private boolean successful;
    private String message;
    
    // Input information
    private int testGeneCount;
    private int backgroundGeneCount;
    private List<String> testGenes;
    private Map<String, Object> analysisParameters;
    
    // Results
    private List<EnhancedEnrichmentResult> enrichmentResults;
    private Map<String, Object> summary;
    
    // Quality metrics
    private int significantTerms;
    private int totalTermsTested;
    private double averageEnrichmentRatio;
    private double medianPValue;
    
    // Analysis statistics
    private long analysisTimeMs;
    private String softwareVersion;
    private String analysisMethod;
    
    /**
     * Constructor
     */
    public EnrichmentAnalysisResult() {
        this.analysisId = generateAnalysisId();
        this.analysisDate = new Date();
        this.enrichmentResults = new ArrayList<>();
        this.analysisParameters = new HashMap<>();
        this.summary = new HashMap<>();
        this.testGenes = new ArrayList<>();
        this.successful = false;
        this.analysisMethod = "TBtools-Enhanced";
        this.softwareVersion = "SimpleGenomeHub-1.0";
    }
    
    /**
     * Generate unique analysis ID
     */
    private String generateAnalysisId() {
        return "ENR_" + System.currentTimeMillis() + "_" + Math.random();
    }
    
    /**
     * Calculate analysis summary statistics
     */
    public void calculateSummary() {
        if (enrichmentResults.isEmpty()) {
            summary.put("message", "No enrichment results available");
            return;
        }
        
        // Count significant results
        significantTerms = (int) enrichmentResults.stream()
            .filter(EnhancedEnrichmentResult::isSignificant)
            .count();
        
        totalTermsTested = enrichmentResults.size();
        
        // Calculate average enrichment ratio
        averageEnrichmentRatio = enrichmentResults.stream()
            .mapToDouble(EnhancedEnrichmentResult::getEnrichmentRatio)
            .average()
            .orElse(0.0);
        
        // Calculate median p-value
        List<Double> pValues = enrichmentResults.stream()
            .map(EnhancedEnrichmentResult::getAdjustedPValue)
            .sorted()
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        if (!pValues.isEmpty()) {
            int middle = pValues.size() / 2;
            if (pValues.size() % 2 == 0) {
                medianPValue = (pValues.get(middle - 1) + pValues.get(middle)) / 2.0;
            } else {
                medianPValue = pValues.get(middle);
            }
        }
        
        // Populate summary map
        summary.put("totalTermsTested", totalTermsTested);
        summary.put("significantTerms", significantTerms);
        summary.put("significanceRate", (double) significantTerms / totalTermsTested);
        summary.put("averageEnrichmentRatio", averageEnrichmentRatio);
        summary.put("medianPValue", medianPValue);
        summary.put("testGeneCount", testGeneCount);
        summary.put("backgroundGeneCount", backgroundGeneCount);
        summary.put("annotationType", annotationType.getDescription());
        summary.put("analysisDate", analysisDate);
        summary.put("analysisTimeMs", analysisTimeMs);
        
        // Category breakdown
        Map<String, Long> categoryBreakdown = new HashMap<>();
        for (EnhancedEnrichmentResult result : enrichmentResults) {
            String category = result.getCategory();
            categoryBreakdown.put(category, categoryBreakdown.getOrDefault(category, 0L) + 1L);
        }
        summary.put("categoryBreakdown", categoryBreakdown);
        
        // Top enriched terms (top 5 by enrichment ratio)
        List<Map<String, Object>> topTerms = new ArrayList<>();
        enrichmentResults.stream()
            .filter(EnhancedEnrichmentResult::isSignificant)
            .sorted(Comparator.comparingDouble(EnhancedEnrichmentResult::getEnrichmentRatio).reversed())
            .limit(5)
            .forEach(result -> {
                Map<String, Object> termInfo = new HashMap<>();
                termInfo.put("termId", result.getTermId());
                termInfo.put("termName", result.getTermName());
                termInfo.put("enrichmentRatio", result.getEnrichmentRatio());
                termInfo.put("adjustedPValue", result.getAdjustedPValue());
                termInfo.put("geneCount", result.getGeneCount());
                topTerms.add(termInfo);
            });
        summary.put("topEnrichedTerms", topTerms);
    }
    
    /**
     * Get formatted summary report
     */
    public String getSummaryReport() {
        calculateSummary();
        
        StringBuilder report = new StringBuilder();
        report.append("=== Enrichment Analysis Summary ===\n");
        report.append(String.format("Analysis ID: %s\n", analysisId));
        report.append(String.format("Date: %s\n", analysisDate));
        report.append(String.format("Annotation Type: %s\n", annotationType.getDescription()));
        report.append(String.format("Test Genes: %d\n", testGeneCount));
        report.append(String.format("Background Genes: %d\n", backgroundGeneCount));
        report.append(String.format("Terms Tested: %d\n", totalTermsTested));
        report.append(String.format("Significant Terms: %d (%.1f%%)\n", 
                                   significantTerms, 
                                   (double) significantTerms / totalTermsTested * 100));
        report.append(String.format("Average Enrichment: %.2fx\n", averageEnrichmentRatio));
        report.append(String.format("Median P-value: %.2e\n", medianPValue));
        
        if (analysisTimeMs > 0) {
            report.append(String.format("Analysis Time: %d ms\n", analysisTimeMs));
        }
        
        report.append("\n=== Top Enriched Terms ===\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topTerms = (List<Map<String, Object>>) summary.get("topEnrichedTerms");
        if (topTerms != null) {
            for (int i = 0; i < topTerms.size(); i++) {
                Map<String, Object> term = topTerms.get(i);
                report.append(String.format("%d. %s (%s): %.2fx enriched, p=%.2e, %d genes\n",
                                           i + 1,
                                           term.get("termName"),
                                           term.get("termId"),
                                           term.get("enrichmentRatio"),
                                           term.get("adjustedPValue"),
                                           term.get("geneCount")));
            }
        }
        
        return report.toString();
    }
    
    /**
     * Export results to TSV format
     */
    public String exportToTSV() {
        StringBuilder tsv = new StringBuilder();
        
        // Add header
        tsv.append(EnhancedEnrichmentResult.getTSVHeader()).append("\n");
        
        // Add results
        for (EnhancedEnrichmentResult result : enrichmentResults) {
            tsv.append(result.toTSV()).append("\n");
        }
        
        return tsv.toString();
    }
    
    /**
     * Filter results by various criteria
     */
    public EnrichmentAnalysisResult filterResults(double pValueThreshold, 
                                                 int minGenes, 
                                                 double minEnrichment) {
        EnrichmentAnalysisResult filtered = new EnrichmentAnalysisResult();
        
        // Copy basic information
        filtered.analysisId = this.analysisId + "_filtered";
        filtered.annotationType = this.annotationType;
        filtered.testGeneCount = this.testGeneCount;
        filtered.backgroundGeneCount = this.backgroundGeneCount;
        filtered.testGenes = new ArrayList<>(this.testGenes);
        filtered.analysisParameters = new HashMap<>(this.analysisParameters);
        filtered.successful = this.successful;
        filtered.message = this.message + " (filtered)";
        
        // Filter results
        filtered.enrichmentResults = enrichmentResults.stream()
            .filter(result -> result.passesQualityThresholds(pValueThreshold, minGenes, minEnrichment))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        
        // Recalculate summary
        filtered.calculateSummary();
        
        return filtered;
    }
    
    /**
     * Get results by category
     */
    public List<EnhancedEnrichmentResult> getResultsByCategory(String category) {
        return enrichmentResults.stream()
            .filter(result -> category.equals(result.getCategory()))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Get top N results by enrichment ratio
     */
    public List<EnhancedEnrichmentResult> getTopEnrichedTerms(int n) {
        return enrichmentResults.stream()
            .filter(EnhancedEnrichmentResult::isSignificant)
            .sorted(Comparator.comparingDouble(EnhancedEnrichmentResult::getEnrichmentRatio).reversed())
            .limit(n)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Check if analysis has significant results
     */
    public boolean hasSignificantResults() {
        return enrichmentResults.stream().anyMatch(EnhancedEnrichmentResult::isSignificant);
    }
    
    // Standard getters and setters
    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; }
    
    public Date getAnalysisDate() { return analysisDate; }
    public void setAnalysisDate(Date analysisDate) { this.analysisDate = analysisDate; }
    
    public AnnotationType getAnnotationType() { return annotationType; }
    public void setAnnotationType(AnnotationType annotationType) { this.annotationType = annotationType; }
    
    public boolean isSuccessful() { return successful; }
    public void setSuccessful(boolean successful) { this.successful = successful; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public int getTestGeneCount() { return testGeneCount; }
    public void setTestGeneCount(int testGeneCount) { this.testGeneCount = testGeneCount; }
    
    public int getBackgroundGeneCount() { return backgroundGeneCount; }
    public void setBackgroundGeneCount(int backgroundGeneCount) { this.backgroundGeneCount = backgroundGeneCount; }
    
    public List<String> getTestGenes() { return new ArrayList<>(testGenes); }
    public void setTestGenes(List<String> testGenes) { this.testGenes = new ArrayList<>(testGenes); }
    
    public Map<String, Object> getAnalysisParameters() { return new HashMap<>(analysisParameters); }
    public void setAnalysisParameters(Map<String, Object> analysisParameters) { 
        this.analysisParameters = new HashMap<>(analysisParameters); 
    }
    
    public List<EnhancedEnrichmentResult> getEnrichmentResults() { return new ArrayList<>(enrichmentResults); }
    public void setEnrichmentResults(List<EnhancedEnrichmentResult> enrichmentResults) { 
        this.enrichmentResults = new ArrayList<>(enrichmentResults);
        calculateSummary();
    }
    
    public Map<String, Object> getSummary() { 
        calculateSummary();
        return new HashMap<>(summary); 
    }
    
    public int getSignificantTerms() { return significantTerms; }
    public int getTotalTermsTested() { return totalTermsTested; }
    public double getAverageEnrichmentRatio() { return averageEnrichmentRatio; }
    public double getMedianPValue() { return medianPValue; }
    
    public long getAnalysisTimeMs() { return analysisTimeMs; }
    public void setAnalysisTimeMs(long analysisTimeMs) { this.analysisTimeMs = analysisTimeMs; }
    
    public String getSoftwareVersion() { return softwareVersion; }
    public void setSoftwareVersion(String softwareVersion) { this.softwareVersion = softwareVersion; }
    
    public String getAnalysisMethod() { return analysisMethod; }
    public void setAnalysisMethod(String analysisMethod) { this.analysisMethod = analysisMethod; }
    
    @Override
    public String toString() {
        return String.format("EnrichmentAnalysisResult[%s: %d/%d significant terms, %.2fx average enrichment]",
                           analysisId,
                           significantTerms,
                           totalTermsTested,
                           averageEnrichmentRatio);
    }
}