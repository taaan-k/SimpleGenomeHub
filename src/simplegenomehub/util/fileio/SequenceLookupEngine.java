/*
 * Sequence Lookup Engine
 */
package simplegenomehub.util.fileio;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.SequenceExtractor.FeatureInfo;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Engine for looking up sequences by gene or transcript ID
 * Searches within genome and annotation files of a specific species
 * 
 * @author SimpleGenomeHub
 */
public class SequenceLookupEngine {
    
    private static final Logger logger = Logger.getLogger(SequenceLookupEngine.class.getName());
    
    private SpeciesInfo species;
    private Map<String, String> genomeSequences;
    private Map<String, List<FeatureInfo>> annotationFeatures;
    
    /**
     * Constructor
     */
    public SequenceLookupEngine(SpeciesInfo species) {
        this.species = species;
        loadSpeciesData();
    }
    
    /**
     * Load genome and annotation data for the species
     */
    private void loadSpeciesData() {
        try {
            // Load genome sequences
            File genomeFile = species.getGenomeFile();
            if (genomeFile != null && genomeFile.exists()) {
                genomeSequences = SequenceExtractor.loadGenomeSequences(genomeFile);
                logger.info("Loaded " + genomeSequences.size() + " genome sequences");
            }
            
            // Load annotation features
            File annotationFile = species.getAnnotationFile();
            if (annotationFile != null && annotationFile.exists()) {
                annotationFeatures = SequenceExtractor.parseAnnotationFile(annotationFile);
                logger.info("Loaded annotation features");
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading species data", e);
        }
    }
    
    /**
     * Lookup sequences for a given gene or transcript ID
     */
    public LookupResult lookupSequences(String searchId) {
        LookupResult result = new LookupResult();
        result.setSearchId(searchId);
        
        if (genomeSequences == null || annotationFeatures == null) {
            result.setSuccess(false);
            result.setErrorMessage("Species data not loaded");
            return result;
        }
        
        try {
            // Try different lookup strategies
            if (searchId.contains(".")) {
                // Looks like a transcript ID (e.g., AT1G01010.1)
                lookupByTranscriptId(searchId, result);
            } else {
                // Looks like a gene ID (e.g., AT1G01010)
                lookupByGeneId(searchId, result);
            }
            
            // If no exact match, try fuzzy matching
            if (!result.isSuccess()) {
                performFuzzySearch(searchId, result);
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during sequence lookup", e);
            result.setSuccess(false);
            result.setErrorMessage("Lookup failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Lookup sequences by transcript ID
     */
    private void lookupByTranscriptId(String transcriptId, LookupResult result) {
        // Find transcript feature
        List<FeatureInfo> transcripts = annotationFeatures.getOrDefault("mRNA", new ArrayList<>());
        transcripts.addAll(annotationFeatures.getOrDefault("transcript", new ArrayList<>()));
        
        for (FeatureInfo transcript : transcripts) {
            if (transcriptId.equals(transcript.getTranscriptId()) || 
                transcriptId.equals(transcript.getAttribute("ID"))) {
                
                result.setFoundId(transcriptId);
                result.setSuccess(true);
                
                // Extract transcript sequence
                String transcriptSeq = SequenceExtractor.extractFeatureSequence(genomeSequences, transcript);
                if (transcriptSeq != null) {
                    result.setTranscriptSequence(formatSequence(transcriptId + "_transcript", transcriptSeq));
                }
                
                // Find and extract CDS
                extractCdsForTranscript(transcriptId, result);
                
                // Extract protein (translate CDS)
                if (!result.getCdsSequence().isEmpty()) {
                    // Remove FASTA header and all whitespace characters
                    String cdsOnly = result.getCdsSequence();
                    // Remove header line (everything up to and including first newline)
                    int firstNewline = cdsOnly.indexOf('\n');
                    if (firstNewline != -1) {
                        cdsOnly = cdsOnly.substring(firstNewline + 1);
                    }
                    // Remove all whitespace
                    cdsOnly = cdsOnly.replaceAll("\\s", "");
                    
                    if (!cdsOnly.isEmpty()) {
                        String proteinSeq = SequenceExtractor.translateToProtein(cdsOnly);
                        if (!proteinSeq.isEmpty()) {
                            result.setProteinSequence(formatSequence(transcriptId + "_protein", proteinSeq));
                        }
                    }
                }
                
                break;
            }
        }
    }
    
    /**
     * Lookup sequences by gene ID
     */
    private void lookupByGeneId(String geneId, LookupResult result) {
        // Find all transcripts for this gene
        List<FeatureInfo> transcripts = annotationFeatures.getOrDefault("mRNA", new ArrayList<>());
        transcripts.addAll(annotationFeatures.getOrDefault("transcript", new ArrayList<>()));
        
        List<String> geneTranscripts = new ArrayList<>();
        for (FeatureInfo transcript : transcripts) {
            String parentGene = transcript.getAttribute("Parent");
            if (geneId.equals(parentGene) || geneId.equals(transcript.getGeneId())) {
                String transcriptId = transcript.getTranscriptId();
                if (transcriptId == null) {
                    transcriptId = transcript.getAttribute("ID");
                }
                if (transcriptId != null) {
                    geneTranscripts.add(transcriptId);
                }
            }
        }
        
        if (!geneTranscripts.isEmpty()) {
            // Use the first transcript (could be enhanced to use representative transcript)
            String primaryTranscript = geneTranscripts.get(0);
            result.setFoundId(geneId + " (using transcript: " + primaryTranscript + ")");
            
            // Lookup sequences for the primary transcript
            lookupByTranscriptId(primaryTranscript, result);
            
            // If multiple transcripts, add note
            if (geneTranscripts.size() > 1) {
                result.setSuggestedIds(geneTranscripts);
            }
        }
    }
    
    /**
     * Extract CDS sequences for a transcript
     */
    private void extractCdsForTranscript(String transcriptId, LookupResult result) {
        List<FeatureInfo> cdsList = annotationFeatures.getOrDefault("CDS", new ArrayList<>());
        List<FeatureInfo> transcriptCds = new ArrayList<>();
        
        for (FeatureInfo cds : cdsList) {
            String parent = cds.getAttribute("Parent");
            if (parent != null && parent.contains(transcriptId)) {
                // Handle multi-parent case (e.g., "AT1G01010.1,AT1G01010.1-Protein")
                if (parent.contains(",")) {
                    parent = parent.split(",")[0].trim();
                }
                if (transcriptId.equals(parent) || transcriptId.equals(cds.getTranscriptId())) {
                    transcriptCds.add(cds);
                }
            }
        }
        
        if (!transcriptCds.isEmpty()) {
            // Sort CDS by position (considering strand direction)
            String strand = transcriptCds.get(0).getStrand();
            if ("-".equals(strand)) {
                // For negative strand, sort by end position in descending order
                transcriptCds.sort((a, b) -> Integer.compare(b.getEnd(), a.getEnd()));
            } else {
                // For positive strand, sort by start position in ascending order  
                transcriptCds.sort((a, b) -> Integer.compare(a.getStart(), b.getStart()));
            }
            
            // Concatenate CDS sequences
            StringBuilder cdsSequence = new StringBuilder();
            for (FeatureInfo cds : transcriptCds) {
                String sequence = SequenceExtractor.extractFeatureSequence(genomeSequences, cds);
                if (sequence != null) {
                    cdsSequence.append(sequence);
                }
            }
            
            if (cdsSequence.length() > 0) {
                result.setCdsSequence(formatSequence(transcriptId + "_cds", cdsSequence.toString()));
            }
        }
    }
    
    /**
     * Perform fuzzy search for similar IDs
     */
    private void performFuzzySearch(String searchId, LookupResult result) {
        Set<String> similarIds = new HashSet<>();
        String searchLower = searchId.toLowerCase();
        
        // Search through transcript IDs
        List<FeatureInfo> transcripts = annotationFeatures.getOrDefault("mRNA", new ArrayList<>());
        transcripts.addAll(annotationFeatures.getOrDefault("transcript", new ArrayList<>()));
        
        for (FeatureInfo transcript : transcripts) {
            String transcriptId = transcript.getTranscriptId();
            if (transcriptId == null) {
                transcriptId = transcript.getAttribute("ID");
            }
            
            if (transcriptId != null) {
                // Check for partial matches
                if (transcriptId.toLowerCase().contains(searchLower) || 
                    searchLower.contains(transcriptId.toLowerCase())) {
                    similarIds.add(transcriptId);
                }
                
                // Check for gene ID matches
                String geneId = transcript.getGeneId();
                if (geneId != null && (geneId.toLowerCase().contains(searchLower) || 
                    searchLower.contains(geneId.toLowerCase()))) {
                    similarIds.add(geneId);
                }
            }
        }
        
        result.setSuggestedIds(new ArrayList<>(similarIds));
    }
    
    /**
     * Format sequence with FASTA header
     */
    private String formatSequence(String header, String sequence) {
        StringBuilder formatted = new StringBuilder();
        formatted.append(">").append(header).append("\n");
        
        // Break sequence into lines of 80 characters
        for (int i = 0; i < sequence.length(); i += 80) {
            int end = Math.min(i + 80, sequence.length());
            formatted.append(sequence.substring(i, end)).append("\n");
        }
        
        return formatted.toString();
    }
    
    /**
     * Result class for lookup operations
     */
    public static class LookupResult {
        private String searchId;
        private String foundId;
        private boolean success;
        private String errorMessage;
        private String transcriptSequence = "";
        private String cdsSequence = "";
        private String proteinSequence = "";
        private List<String> suggestedIds = new ArrayList<>();
        
        // Getters and setters
        public String getSearchId() { return searchId; }
        public void setSearchId(String searchId) { this.searchId = searchId; }
        public String getFoundId() { return foundId; }
        public void setFoundId(String foundId) { this.foundId = foundId; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public String getTranscriptSequence() { return transcriptSequence; }
        public void setTranscriptSequence(String transcriptSequence) { this.transcriptSequence = transcriptSequence; }
        public String getCdsSequence() { return cdsSequence; }
        public void setCdsSequence(String cdsSequence) { this.cdsSequence = cdsSequence; }
        public String getProteinSequence() { return proteinSequence; }
        public void setProteinSequence(String proteinSequence) { this.proteinSequence = proteinSequence; }
        public List<String> getSuggestedIds() { return suggestedIds; }
        public void setSuggestedIds(List<String> suggestedIds) { this.suggestedIds = suggestedIds; }
    }
}