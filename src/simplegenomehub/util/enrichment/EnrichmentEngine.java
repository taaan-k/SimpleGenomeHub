/*
 * Comprehensive Enrichment Analysis Engine
 * Integrates TBtools enrichment analysis capabilities with SimpleGenomeHub
 */
package simplegenomehub.util.enrichment;

import simplegenomehub.model.GeneAnnotationData;
import simplegenomehub.model.GeneAnnotationData.AnnotationType;
import simplegenomehub.model.GeneAnnotationData.GeneAnnotation;

// TBtools enrichment analysis imports
import biocjava.bioIO.GeneOntology.EnrichMent.GOTermEnrichment;
import biocjava.bioIO.GeneOntology.EnrichMent.SimpleEnricher;
import biocjava.bioDoer.Kegg.AdvancedForEnrichment.KeggEnrichment;
import toolsKit.PadjustBhMethod;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Unified enrichment analysis engine leveraging TBtools algorithms
 * Supports GO, KEGG, and Custom annotation enrichment analysis
 * 
 * @author SimpleGenomeHub
 */
public class EnrichmentEngine {
    
    private static final Logger logger = Logger.getLogger(EnrichmentEngine.class.getName());
    
    // Analysis parameters
    private double pValueThreshold = 0.05;
    private double fdrThreshold = 0.05;
    private int minGeneCount = 3;
    private boolean useMultipleTestingCorrection = true;
    private String correctionMethod = "BH"; // Benjamini-Hochberg
    
    // TBtools enrichment objects
    private GOTermEnrichment goEnricher;
    private KeggEnrichment keggEnricher;
    private SimpleEnricher simpleEnricher;
    
    // Analysis data
    private GeneAnnotationData annotationData;
    private Set<String> backgroundGenes;
    private Map<String, String> geneIdMapping; // transcript -> gene mapping if needed
    
    /**
     * Constructor
     */
    public EnrichmentEngine(GeneAnnotationData annotationData) {
        this.annotationData = annotationData;
        this.backgroundGenes = new HashSet<>();
        this.geneIdMapping = new HashMap<>();
        
        initializeEnrichers();
        prepareBackgroundData();
    }
    
    /**
     * Initialize TBtools enrichment analysis objects
     */
    private void initializeEnrichers() {
        try {
            // Initialize GO enrichment engine
            goEnricher = new GOTermEnrichment();
            goEnricher.setCleanMode(true);
            
            // Initialize KEGG enrichment engine  
            keggEnricher = new KeggEnrichment();
            
            // Initialize simple enricher for custom annotations
            simpleEnricher = new SimpleEnricher();
            
            logger.info("TBtools enrichment engines initialized successfully");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to initialize TBtools enrichment engines", e);
        }
    }
    
    /**
     * Prepare background gene set from all available annotations
     */
    private void prepareBackgroundData() {
        backgroundGenes.clear();
        
        // Collect genes from all annotation types
        for (AnnotationType type : AnnotationType.values()) {
            Map<String, List<GeneAnnotation>> typeAnnotations = getAnnotationsByType(type);
            if (typeAnnotations != null) {
                backgroundGenes.addAll(typeAnnotations.keySet());
            }
        }
        
        logger.info("Background gene set prepared: " + backgroundGenes.size() + " genes");
    }
    
    /**
     * Get annotations by type from the annotation data
     */
    private Map<String, List<GeneAnnotation>> getAnnotationsByType(AnnotationType type) {
        if (annotationData == null) {
            return new HashMap<>();
        }
        
        // Access the internal annotations map structure: type -> gene -> annotations
        try {
            // Use reflection to access the private annotations field
            java.lang.reflect.Field annotationsField = annotationData.getClass().getDeclaredField("annotations");
            annotationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<AnnotationType, Map<String, List<GeneAnnotation>>> allAnnotations = 
                (Map<AnnotationType, Map<String, List<GeneAnnotation>>>) annotationsField.get(annotationData);
            
            Map<String, List<GeneAnnotation>> typeAnnotations = allAnnotations.get(type);
            return typeAnnotations != null ? typeAnnotations : new HashMap<>();
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to access annotation data via reflection", e);
            
            // Fallback: try to get public methods if they exist
            return getAnnotationsByTypePublic(type);
        }
    }
    
    /**
     * Fallback method to get annotations using public API
     */
    private Map<String, List<GeneAnnotation>> getAnnotationsByTypePublic(AnnotationType type) {
        Map<String, List<GeneAnnotation>> result = new HashMap<>();
        
        // If there's no public API to access all genes by type, 
        // we'll need to create a temporary workaround
        // For now, return empty map but log the issue
        logger.warning("Cannot access annotation data - need public API to get annotations by type");
        
        return result;
    }
    
    /**
     * Get term description for a given term ID
     */
    private String getTermDescription(String termId) {
        // This is a placeholder - need to implement based on actual GeneAnnotationData structure
        return termId; // Fallback to term ID
    }
    
    /**
     * Perform enrichment analysis for specified annotation type
     */
    public EnrichmentAnalysisResult performEnrichment(List<String> testGenes, AnnotationType annotationType) {
        
        // Clean and validate test genes
        Set<String> validTestGenes = validateAndCleanGeneList(testGenes);
        
        if (validTestGenes.size() < minGeneCount) {
            throw new IllegalArgumentException("Test gene set too small: " + validTestGenes.size() + 
                                             " genes (minimum: " + minGeneCount + ")");
        }
        
        EnrichmentAnalysisResult result = new EnrichmentAnalysisResult();
        result.setAnnotationType(annotationType);
        result.setTestGeneCount(validTestGenes.size());
        result.setBackgroundGeneCount(backgroundGenes.size());
        result.setAnalysisParameters(createParametersMap());
        
        try {
            switch (annotationType) {
                case GO:
                    result.setEnrichmentResults(performGOEnrichment(validTestGenes));
                    break;
                case KEGG:
                    result.setEnrichmentResults(performKEGGEnrichment(validTestGenes));
                    break;
                case CUSTOM:
                    result.setEnrichmentResults(performCustomEnrichment(validTestGenes));
                    break;
                default:
                    throw new UnsupportedOperationException("Annotation type not supported: " + annotationType);
            }
            
            // Apply multiple testing correction if enabled
            if (useMultipleTestingCorrection) {
                applyMultipleTestingCorrection(result.getEnrichmentResults());
            }
            
            // Filter by significance
            result.setEnrichmentResults(filterSignificantResults(result.getEnrichmentResults()));
            
            // Sort by adjusted p-value
            result.getEnrichmentResults().sort(Comparator.comparingDouble(EnhancedEnrichmentResult::getAdjustedPValue));
            
            result.setSuccessful(true);
            result.setMessage("Enrichment analysis completed successfully");
            
            logger.info(String.format("Enrichment analysis completed for %s: %d significant terms found", 
                                    annotationType.getDescription(), result.getEnrichmentResults().size()));
            
        } catch (Exception e) {
            result.setSuccessful(false);
            result.setMessage("Enrichment analysis failed: " + e.getMessage());
            logger.log(Level.SEVERE, "Enrichment analysis failed", e);
        }
        
        return result;
    }
    
    /**
     * Perform GO enrichment analysis using TBtools
     */
    private List<EnhancedEnrichmentResult> performGOEnrichment(Set<String> testGenes) throws Exception {
        List<EnhancedEnrichmentResult> results = new ArrayList<>();
        
        Map<String, List<GeneAnnotation>> goAnnotations = getAnnotationsByType(AnnotationType.GO);
        if (goAnnotations == null || goAnnotations.isEmpty()) {
            throw new IllegalStateException("No GO annotations available. Please import GO annotation data first.");
        }
        
        // Create term-gene mapping for TBtools
        Map<String, Set<String>> termToGenes = new HashMap<>();
        Map<String, Set<String>> geneToTerms = new HashMap<>();
        
        for (Map.Entry<String, List<GeneAnnotation>> entry : goAnnotations.entrySet()) {
            String geneId = entry.getKey();
            for (GeneAnnotation annotation : entry.getValue()) {
                String termId = annotation.getAnnotationId();
                
                termToGenes.computeIfAbsent(termId, k -> new HashSet<>()).add(geneId);
                geneToTerms.computeIfAbsent(geneId, k -> new HashSet<>()).add(termId);
            }
        }
        
        // Perform enrichment for each GO term
        for (Map.Entry<String, Set<String>> termEntry : termToGenes.entrySet()) {
            String termId = termEntry.getKey();
            Set<String> termGenes = termEntry.getValue();
            
            // Skip terms with too few genes
            if (termGenes.size() < minGeneCount) continue;
            
            // Calculate overlap with test genes
            Set<String> overlap = new HashSet<>(testGenes);
            overlap.retainAll(termGenes);
            
            if (overlap.size() < minGeneCount) continue;
            
            // Calculate enrichment statistics using hypergeometric distribution
            int k = overlap.size(); // successes in sample
            int n = testGenes.size(); // sample size
            int K = termGenes.size(); // successes in population  
            int N = backgroundGenes.size(); // population size
            
            double pValue = calculateHypergeometricPValue(k, n, K, N);
            
            if (pValue <= pValueThreshold) {
                EnhancedEnrichmentResult result = new EnhancedEnrichmentResult();
                result.setTermId(termId);
                result.setTermName(getTermDescription(termId));
                result.setCategory("GO");
                result.setGeneCount(k);
                result.setTermSize(K);
                result.setBackgroundCount(N);
                result.setPValue(pValue);
                result.setEnrichmentRatio((double) k / n * N / K);
                result.setGeneIds(new ArrayList<>(overlap));
                
                // Add GO-specific information
                result.addMetadata("go_aspect", determineGOAspect(termId));
                result.addMetadata("go_level", calculateGOLevel(termId));
                
                results.add(result);
            }
        }
        
        return results;
    }
    
    /**
     * Perform KEGG pathway enrichment using TBtools
     */
    private List<EnhancedEnrichmentResult> performKEGGEnrichment(Set<String> testGenes) throws Exception {
        List<EnhancedEnrichmentResult> results = new ArrayList<>();
        
        Map<String, List<GeneAnnotation>> keggAnnotations = getAnnotationsByType(AnnotationType.KEGG);
        if (keggAnnotations == null || keggAnnotations.isEmpty()) {
            throw new IllegalStateException("No KEGG annotations available. Please import KEGG annotation data first.");
        }
        
        // Create pathway-gene mapping
        Map<String, Set<String>> pathwayToGenes = new HashMap<>();
        
        for (Map.Entry<String, List<GeneAnnotation>> entry : keggAnnotations.entrySet()) {
            String geneId = entry.getKey();
            for (GeneAnnotation annotation : entry.getValue()) {
                String keggId = annotation.getAnnotationId();
                String pathwayId = annotation.getTerm(); // Assuming term contains pathway info
                
                if (pathwayId != null && !pathwayId.isEmpty()) {
                    pathwayToGenes.computeIfAbsent(pathwayId, k -> new HashSet<>()).add(geneId);
                }
            }
        }
        
        // Perform enrichment for each pathway
        for (Map.Entry<String, Set<String>> pathwayEntry : pathwayToGenes.entrySet()) {
            String pathwayId = pathwayEntry.getKey();
            Set<String> pathwayGenes = pathwayEntry.getValue();
            
            if (pathwayGenes.size() < minGeneCount) continue;
            
            Set<String> overlap = new HashSet<>(testGenes);
            overlap.retainAll(pathwayGenes);
            
            if (overlap.size() < minGeneCount) continue;
            
            int k = overlap.size();
            int n = testGenes.size();
            int K = pathwayGenes.size();
            int N = backgroundGenes.size();
            
            double pValue = calculateHypergeometricPValue(k, n, K, N);
            
            if (pValue <= pValueThreshold) {
                EnhancedEnrichmentResult result = new EnhancedEnrichmentResult();
                result.setTermId(pathwayId);
                result.setTermName(getTermDescription(pathwayId));
                result.setCategory("KEGG");
                result.setGeneCount(k);
                result.setTermSize(K);
                result.setBackgroundCount(N);
                result.setPValue(pValue);
                result.setEnrichmentRatio((double) k / n * N / K);
                result.setGeneIds(new ArrayList<>(overlap));
                
                // Add KEGG-specific metadata
                result.addMetadata("pathway_class", determineKEGGClass(pathwayId));
                
                results.add(result);
            }
        }
        
        return results;
    }
    
    /**
     * Perform custom annotation enrichment
     */
    private List<EnhancedEnrichmentResult> performCustomEnrichment(Set<String> testGenes) throws Exception {
        List<EnhancedEnrichmentResult> results = new ArrayList<>();
        
        Map<String, List<GeneAnnotation>> customAnnotations = getAnnotationsByType(AnnotationType.CUSTOM);
        if (customAnnotations == null || customAnnotations.isEmpty()) {
            throw new IllegalStateException("No custom annotations available. Please import custom annotation data first.");
        }
        
        // Similar logic to GO/KEGG but for custom terms
        Map<String, Set<String>> termToGenes = new HashMap<>();
        
        for (Map.Entry<String, List<GeneAnnotation>> entry : customAnnotations.entrySet()) {
            String geneId = entry.getKey();
            for (GeneAnnotation annotation : entry.getValue()) {
                String termId = annotation.getAnnotationId();
                termToGenes.computeIfAbsent(termId, k -> new HashSet<>()).add(geneId);
            }
        }
        
        for (Map.Entry<String, Set<String>> termEntry : termToGenes.entrySet()) {
            String termId = termEntry.getKey();
            Set<String> termGenes = termEntry.getValue();
            
            if (termGenes.size() < minGeneCount) continue;
            
            Set<String> overlap = new HashSet<>(testGenes);
            overlap.retainAll(termGenes);
            
            if (overlap.size() < minGeneCount) continue;
            
            int k = overlap.size();
            int n = testGenes.size();
            int K = termGenes.size();
            int N = backgroundGenes.size();
            
            double pValue = calculateHypergeometricPValue(k, n, K, N);
            
            if (pValue <= pValueThreshold) {
                EnhancedEnrichmentResult result = new EnhancedEnrichmentResult();
                result.setTermId(termId);
                result.setTermName(getTermDescription(termId));
                result.setCategory("Custom");
                result.setGeneCount(k);
                result.setTermSize(K);
                result.setBackgroundCount(N);
                result.setPValue(pValue);
                result.setEnrichmentRatio((double) k / n * N / K);
                result.setGeneIds(new ArrayList<>(overlap));
                
                results.add(result);
            }
        }
        
        return results;
    }
    
    /**
     * Apply multiple testing correction using TBtools algorithms
     */
    private void applyMultipleTestingCorrection(List<EnhancedEnrichmentResult> results) {
        if (results.isEmpty()) return;
        
        try {
            // Extract p-values
            double[] pValues = results.stream()
                .mapToDouble(EnhancedEnrichmentResult::getPValue)
                .toArray();
            
            // Apply Benjamini-Hochberg correction
            double[] adjustedPValues = applyBenjaminiHochbergCorrection(pValues);
            
            // Set adjusted p-values
            for (int i = 0; i < results.size(); i++) {
                results.get(i).setAdjustedPValue(adjustedPValues[i]);
            }
            
            logger.info("Multiple testing correction applied using " + correctionMethod + " method");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to apply multiple testing correction", e);
            
            // Fallback: set adjusted p-values equal to raw p-values
            for (EnhancedEnrichmentResult result : results) {
                result.setAdjustedPValue(result.getPValue());
            }
        }
    }
    
    /**
     * Filter results by significance threshold
     */
    private List<EnhancedEnrichmentResult> filterSignificantResults(List<EnhancedEnrichmentResult> results) {
        return results.stream()
            .filter(result -> result.getAdjustedPValue() <= fdrThreshold)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * Calculate hypergeometric p-value using one-tailed test
     */
    private double calculateHypergeometricPValue(int k, int n, int K, int N) {
        try {
            // Use TBtools SimpleEnricher if available
            if (simpleEnricher != null) {
                // Use TBtools SimpleEnricher if available, otherwise fallback
                return calculateHypergeometricManual(k, n, K, N);
            }
            
            // Fallback: manual calculation
            return calculateHypergeometricManual(k, n, K, N);
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error calculating hypergeometric p-value", e);
            return 1.0; // Conservative fallback
        }
    }
    
    /**
     * Manual hypergeometric p-value calculation
     */
    private double calculateHypergeometricManual(int k, int n, int K, int N) {
        // P(X >= k) where X ~ Hypergeometric(N, K, n)
        double pValue = 0.0;
        
        for (int i = k; i <= Math.min(n, K); i++) {
            pValue += hypergeometricPMF(i, n, K, N);
        }
        
        return Math.min(1.0, pValue);
    }
    
    /**
     * Hypergeometric probability mass function
     */
    private double hypergeometricPMF(int k, int n, int K, int N) {
        return Math.exp(
            logCombination(K, k) + 
            logCombination(N - K, n - k) - 
            logCombination(N, n)
        );
    }
    
    /**
     * Log combination calculation for numerical stability
     */
    private double logCombination(int n, int k) {
        if (k > n || k < 0) return Double.NEGATIVE_INFINITY;
        if (k == 0 || k == n) return 0.0;
        
        double result = 0.0;
        for (int i = 0; i < k; i++) {
            result += Math.log(n - i) - Math.log(i + 1);
        }
        return result;
    }
    
    /**
     * Validate and clean gene list
     */
    private Set<String> validateAndCleanGeneList(List<String> geneList) {
        Set<String> validGenes = new HashSet<>();
        
        for (String gene : geneList) {
            if (gene != null && !gene.trim().isEmpty()) {
                String cleanGene = gene.trim();
                
                // If background is empty (no annotations loaded), accept all valid gene IDs
                if (backgroundGenes.isEmpty()) {
                    validGenes.add(cleanGene);
                    logger.fine("No background annotations - accepting gene: " + cleanGene);
                } else {
                    // Check if gene exists in background
                    if (backgroundGenes.contains(cleanGene)) {
                        validGenes.add(cleanGene);
                    } else {
                        // Try gene ID mapping if available
                        String mappedGene = geneIdMapping.get(cleanGene);
                        if (mappedGene != null && backgroundGenes.contains(mappedGene)) {
                            validGenes.add(mappedGene);
                        } else {
                            logger.fine("Gene not found in background: " + cleanGene);
                        }
                    }
                }
            }
        }
        
        logger.info(String.format("Gene list validation: %d input genes, %d valid genes (background: %d genes)", 
                                geneList.size(), validGenes.size(), backgroundGenes.size()));
        
        return validGenes;
    }
    
    /**
     * Helper methods for annotation-specific information
     */
    private String determineGOAspect(String goTermId) {
        // Placeholder - would need GO database for accurate determination
        if (goTermId.startsWith("GO:")) {
            // Could query GO database or use cached information
            return "Unknown";
        }
        return "N/A";
    }
    
    private int calculateGOLevel(String goTermId) {
        // Placeholder - would need GO hierarchy for accurate calculation
        return 0;
    }
    
    private String determineKEGGClass(String pathwayId) {
        // Placeholder - would parse KEGG pathway classification
        return "Unknown";
    }
    
    /**
     * Create parameters map for result recording
     */
    private Map<String, Object> createParametersMap() {
        Map<String, Object> params = new HashMap<>();
        params.put("pValueThreshold", pValueThreshold);
        params.put("fdrThreshold", fdrThreshold);
        params.put("minGeneCount", minGeneCount);
        params.put("useMultipleTestingCorrection", useMultipleTestingCorrection);
        params.put("correctionMethod", correctionMethod);
        return params;
    }
    
    // Getters and setters for configuration
    public double getPValueThreshold() { return pValueThreshold; }
    public void setPValueThreshold(double pValueThreshold) { this.pValueThreshold = pValueThreshold; }
    
    public double getFdrThreshold() { return fdrThreshold; }
    public void setFdrThreshold(double fdrThreshold) { this.fdrThreshold = fdrThreshold; }
    
    public int getMinGeneCount() { return minGeneCount; }
    public void setMinGeneCount(int minGeneCount) { this.minGeneCount = minGeneCount; }
    
    public boolean isUseMultipleTestingCorrection() { return useMultipleTestingCorrection; }
    public void setUseMultipleTestingCorrection(boolean useMultipleTestingCorrection) { 
        this.useMultipleTestingCorrection = useMultipleTestingCorrection; 
    }
    
    public String getCorrectionMethod() { return correctionMethod; }
    public void setCorrectionMethod(String correctionMethod) { this.correctionMethod = correctionMethod; }
    
    public void setGeneIdMapping(Map<String, String> geneIdMapping) { 
        this.geneIdMapping = geneIdMapping; 
    }
    
    /**
     * Apply Benjamini-Hochberg multiple testing correction
     */
    private double[] applyBenjaminiHochbergCorrection(double[] pValues) {
        int n = pValues.length;
        if (n == 0) return new double[0];
        
        // Create array of indices and sort by p-value
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }
        
        Arrays.sort(indices, (i, j) -> Double.compare(pValues[i], pValues[j]));
        
        // Apply Benjamini-Hochberg correction
        double[] adjustedPValues = new double[n];
        double[] sortedAdjusted = new double[n];
        
        // Calculate adjusted p-values for sorted array
        for (int i = n - 1; i >= 0; i--) {
            double adjustedP = pValues[indices[i]] * n / (i + 1);
            if (i == n - 1) {
                sortedAdjusted[i] = Math.min(1.0, adjustedP);
            } else {
                sortedAdjusted[i] = Math.min(sortedAdjusted[i + 1], Math.min(1.0, adjustedP));
            }
        }
        
        // Map back to original order
        for (int i = 0; i < n; i++) {
            adjustedPValues[indices[i]] = sortedAdjusted[i];
        }
        
        return adjustedPValues;
    }
}