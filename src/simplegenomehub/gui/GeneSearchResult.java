/*
 * Gene Search Result Data Model
 * Contains all information retrieved for a gene search
 */
package simplegenomehub.gui;

import simplegenomehub.model.*;
import java.util.*;

/**
 * Comprehensive result object for gene searches
 * Contains all types of gene information retrieved from various data sources
 * 
 * @author SimpleGenomeHub Team
 */
public class GeneSearchResult {
    
    private String geneId;
    private String resolvedGeneId;
    private SpeciesInfo species;
    private boolean found;
    private String geneDescription;
    
    // Sequence data
    private Map<String, String> sequences;
    
    // Annotation data
    private List<GeneAnnotationData.GeneAnnotation> annotations;
    
    // Expression data
    private Map<String, ExpressionData> expressionData;
    
    // Gene structure data
    private GeneStructureInfo structureInfo;
    
    // Search metadata
    private long searchTimestamp;
    private long searchDurationMs;
    
    /**
     * Constructor
     */
    public GeneSearchResult(String geneId, SpeciesInfo species) {
        this.geneId = geneId;
        this.species = species;
        this.found = false;
        this.sequences = new HashMap<>();
        this.annotations = new ArrayList<>();
        this.expressionData = new HashMap<>();
        this.searchTimestamp = System.currentTimeMillis();
    }
    
    // Getters and setters
    
    public String getGeneId() {
        return geneId;
    }

    public String getResolvedGeneId() {
        return resolvedGeneId != null && !resolvedGeneId.trim().isEmpty() ? resolvedGeneId : geneId;
    }

    public void setResolvedGeneId(String resolvedGeneId) {
        this.resolvedGeneId = resolvedGeneId;
    }
    
    public SpeciesInfo getSpecies() {
        return species;
    }
    
    public boolean isFound() {
        return found;
    }
    
    public void setFound(boolean found) {
        this.found = found;
    }
    
    public String getGeneDescription() {
        return geneDescription;
    }
    
    public void setGeneDescription(String geneDescription) {
        this.geneDescription = geneDescription;
    }
    
    public Map<String, String> getSequences() {
        return sequences;
    }
    
    public void setSequences(Map<String, String> sequences) {
        this.sequences = sequences != null ? sequences : new HashMap<>();
    }
    
    public List<GeneAnnotationData.GeneAnnotation> getAnnotations() {
        return annotations;
    }
    
    public void setAnnotations(List<GeneAnnotationData.GeneAnnotation> annotations) {
        this.annotations = annotations != null ? annotations : new ArrayList<>();
    }
    
    public Map<String, ExpressionData> getExpressionData() {
        return expressionData;
    }
    
    public void setExpressionData(Map<String, ExpressionData> expressionData) {
        this.expressionData = expressionData != null ? expressionData : new HashMap<>();
    }
    
    public GeneStructureInfo getStructureInfo() {
        return structureInfo;
    }
    
    public void setStructureInfo(GeneStructureInfo structureInfo) {
        this.structureInfo = structureInfo;
    }
    
    public long getSearchTimestamp() {
        return searchTimestamp;
    }
    
    public long getSearchDurationMs() {
        return searchDurationMs;
    }
    
    public void setSearchDurationMs(long searchDurationMs) {
        this.searchDurationMs = searchDurationMs;
    }
    
    // Convenience methods
    
    /**
     * Check if gene has sequence data
     */
    public boolean hasSequenceData() {
        return !sequences.isEmpty();
    }
    
    /**
     * Check if gene has annotation data
     */
    public boolean hasAnnotationData() {
        return !annotations.isEmpty();
    }
    
    /**
     * Check if gene has expression data
     */
    public boolean hasExpressionData() {
        return !expressionData.isEmpty();
    }
    
    /**
     * Check if gene has structure data
     */
    public boolean hasStructureData() {
        return structureInfo != null;
    }
    
    /**
     * Get specific sequence by type
     */
    public String getSequence(String type) {
        return sequences.get(type);
    }
    
    /**
     * Get sequence header for specific type
     */
    public String getSequenceHeader(String type) {
        return sequences.get(type + "_header");
    }
    
    /**
     * Get annotations by type
     */
    public List<GeneAnnotationData.GeneAnnotation> getAnnotationsByType(GeneAnnotationData.AnnotationType type) {
        return annotations.stream()
            .filter(ann -> ann.getType() == type)
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Get expression data for specific experiment
     */
    public ExpressionData getExpressionForExperiment(String experimentId) {
        return expressionData.get(experimentId);
    }
    
    /**
     * Get summary statistics
     */
    public Map<String, Object> getSummaryStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("gene_id", geneId);
        stats.put("resolved_gene_id", getResolvedGeneId());
        stats.put("species", species.getDisplayName());
        stats.put("found", found);
        stats.put("sequence_types", sequences.keySet().size() / 2); // Divide by 2 because headers are also stored
        stats.put("annotation_count", annotations.size());
        stats.put("expression_experiments", expressionData.size());
        stats.put("has_structure", hasStructureData());
        stats.put("search_duration_ms", searchDurationMs);
        
        return stats;
    }
    
    @Override
    public String toString() {
        return String.format("GeneSearchResult{geneId='%s', resolvedGeneId='%s', species='%s', found=%s, sequences=%d, annotations=%d, expression=%d}",
            geneId, getResolvedGeneId(), species.getDisplayName(), found, sequences.size()/2, annotations.size(), expressionData.size());
    }
}

/**
 * Expression data for a specific gene in an experiment
 */
class ExpressionData {
    private String geneId;
    private String experimentId;
    private Map<String, Double> expressionValues; // sample -> expression value
    private Map<String, Object> metadata;
    
    public ExpressionData(String geneId, String experimentId, Map<String, Double> expressionValues) {
        this.geneId = geneId;
        this.experimentId = experimentId;
        this.expressionValues = expressionValues != null ? expressionValues : new HashMap<>();
        this.metadata = new HashMap<>();
    }
    
    // Getters and setters
    public String getGeneId() { return geneId; }
    public String getExperimentId() { return experimentId; }
    public Map<String, Double> getExpressionValues() { return expressionValues; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }
    
    // Convenience methods
    public double getExpressionForSample(String sampleId) {
        return expressionValues.getOrDefault(sampleId, 0.0);
    }
    
    public Set<String> getSampleIds() {
        return expressionValues.keySet();
    }
    
    public Collection<Double> getAllExpressionValues() {
        return expressionValues.values();
    }
    
    public double getMaxExpression() {
        return expressionValues.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }
    
    public double getMinExpression() {
        return expressionValues.values().stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
    }
    
    public double getMeanExpression() {
        return expressionValues.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}

/**
 * Gene structure information
 */
class GeneStructureInfo {
    private String geneId;
    private String chromosome;
    private int start;
    private int end;
    private String strand;
    private List<ExonInfo> exons;
    private List<TranscriptInfo> transcripts;
    private Map<String, Object> attributes;
    
    public GeneStructureInfo(String geneId) {
        this.geneId = geneId;
        this.exons = new ArrayList<>();
        this.transcripts = new ArrayList<>();
        this.attributes = new HashMap<>();
    }
    
    // Getters and setters
    public String getGeneId() { return geneId; }
    public String getChromosome() { return chromosome; }
    public void setChromosome(String chromosome) { this.chromosome = chromosome; }
    public int getStart() { return start; }
    public void setStart(int start) { this.start = start; }
    public int getEnd() { return end; }
    public void setEnd(int end) { this.end = end; }
    public String getStrand() { return strand; }
    public void setStrand(String strand) { this.strand = strand; }
    public List<ExonInfo> getExons() { return exons; }
    public List<TranscriptInfo> getTranscripts() { return transcripts; }
    public Map<String, Object> getAttributes() { return attributes; }
    
    // Convenience methods
    public int getGeneLength() {
        return end - start + 1;
    }
    
    public int getExonCount() {
        return exons.size();
    }
    
    public int getTranscriptCount() {
        return transcripts.size();
    }
    
    /**
     * Exon information
     */
    public static class ExonInfo {
        private int start;
        private int end;
        private int number;
        private String transcriptId;
        
        public ExonInfo(int start, int end, int number) {
            this.start = start;
            this.end = end;
            this.number = number;
        }
        
        // Getters and setters
        public int getStart() { return start; }
        public int getEnd() { return end; }
        public int getNumber() { return number; }
        public String getTranscriptId() { return transcriptId; }
        public void setTranscriptId(String transcriptId) { this.transcriptId = transcriptId; }
        
        public int getLength() { return end - start + 1; }
    }
    
    /**
     * Transcript information
     */
    public static class TranscriptInfo {
        private String transcriptId;
        private int start;
        private int end;
        private List<ExonInfo> exons;
        private String biotype;
        
        public TranscriptInfo(String transcriptId) {
            this.transcriptId = transcriptId;
            this.exons = new ArrayList<>();
        }
        
        // Getters and setters
        public String getTranscriptId() { return transcriptId; }
        public int getStart() { return start; }
        public void setStart(int start) { this.start = start; }
        public int getEnd() { return end; }
        public void setEnd(int end) { this.end = end; }
        public List<ExonInfo> getExons() { return exons; }
        public String getBiotype() { return biotype; }
        public void setBiotype(String biotype) { this.biotype = biotype; }
        
        public int getLength() { return end - start + 1; }
        public int getExonCount() { return exons.size(); }
    }
}
