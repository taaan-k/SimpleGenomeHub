/*
 * Chromosome ID Consistency Checker
 */
package simplegenomehub.util.fileio;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Checks consistency between chromosome IDs in genome and annotation files
 * Ensures that the sequence IDs in FASTA headers match those in GFF3/GTF files
 * 
 * @author SimpleGenomeHub
 */
public class ChromosomeIdChecker {
    
    private static final Logger logger = Logger.getLogger(ChromosomeIdChecker.class.getName());
    
    /**
     * Result of chromosome ID consistency check
     */
    public static class ConsistencyResult {
        private boolean consistent;
        private String message;
        private Set<String> genomeIds;
        private Set<String> annotationIds;
        private Set<String> commonIds;
        private Set<String> genomeOnlyIds;
        private Set<String> annotationOnlyIds;
        private double overlapPercentage;
        
        public ConsistencyResult(boolean consistent, String message) {
            this.consistent = consistent;
            this.message = message;
            this.genomeIds = new HashSet<>();
            this.annotationIds = new HashSet<>();
            this.commonIds = new HashSet<>();
            this.genomeOnlyIds = new HashSet<>();
            this.annotationOnlyIds = new HashSet<>();
        }
        
        // Getters
        public boolean isConsistent() { return consistent; }
        public String getMessage() { return message; }
        public Set<String> getGenomeIds() { return genomeIds; }
        public Set<String> getAnnotationIds() { return annotationIds; }
        public Set<String> getCommonIds() { return commonIds; }
        public Set<String> getGenomeOnlyIds() { return genomeOnlyIds; }
        public Set<String> getAnnotationOnlyIds() { return annotationOnlyIds; }
        public double getOverlapPercentage() { return overlapPercentage; }
        
        public void setGenomeIds(Set<String> genomeIds) { this.genomeIds = genomeIds; }
        public void setAnnotationIds(Set<String> annotationIds) { this.annotationIds = annotationIds; }
        public void calculateOverlap() {
            commonIds.clear();
            genomeOnlyIds.clear();
            annotationOnlyIds.clear();
            
            // Find common IDs
            for (String id : genomeIds) {
                if (annotationIds.contains(id)) {
                    commonIds.add(id);
                } else {
                    genomeOnlyIds.add(id);
                }
            }
            
            // Find annotation-only IDs
            for (String id : annotationIds) {
                if (!genomeIds.contains(id)) {
                    annotationOnlyIds.add(id);
                }
            }
            
            // Calculate overlap percentage
            int totalUnique = genomeIds.size() + annotationIds.size() - commonIds.size();
            if (totalUnique > 0) {
                overlapPercentage = (double) commonIds.size() / Math.max(genomeIds.size(), annotationIds.size()) * 100;
            } else {
                overlapPercentage = 100.0;
            }
        }
    }
    
    /**
     * Check chromosome ID consistency between genome and annotation files
     */
    public static ConsistencyResult checkConsistency(File genomeFile, File annotationFile) {
        try {
            logger.info("Checking chromosome ID consistency between files");
            
            // Extract chromosome IDs from genome file
            Set<String> genomeIds = extractGenomeIds(genomeFile);
            if (genomeIds.isEmpty()) {
                return new ConsistencyResult(false, "No chromosome IDs found in genome file");
            }
            
            // Extract chromosome IDs from annotation file
            Set<String> annotationIds = extractAnnotationIds(annotationFile);
            if (annotationIds.isEmpty()) {
                return new ConsistencyResult(false, "No chromosome IDs found in annotation file");
            }
            
            // Create result and calculate overlap
            ConsistencyResult result = new ConsistencyResult(true, "");
            result.setGenomeIds(genomeIds);
            result.setAnnotationIds(annotationIds);
            result.calculateOverlap();
            
            // Determine consistency based on overlap
            double minOverlapThreshold = 50.0; // Require at least 50% overlap
            
            if (result.getOverlapPercentage() >= minOverlapThreshold) {
                result.consistent = true;
                result.message = String.format("Chromosome IDs are consistent (%.1f%% overlap, %d common IDs)", 
                    result.getOverlapPercentage(), result.getCommonIds().size());
            } else {
                result.consistent = false;
                result.message = String.format("Low chromosome ID overlap (%.1f%%, %d common IDs). " +
                    "Expected at least %.1f%% overlap.", 
                    result.getOverlapPercentage(), result.getCommonIds().size(), minOverlapThreshold);
            }
            
            // Log detailed information
            logger.info("Chromosome ID analysis: " + result.getMessage());
            logger.info(String.format("Genome IDs: %d, Annotation IDs: %d, Common: %d", 
                genomeIds.size(), annotationIds.size(), result.getCommonIds().size()));
            
            if (!result.getGenomeOnlyIds().isEmpty()) {
                logger.info("Genome-only IDs: " + result.getGenomeOnlyIds());
            }
            if (!result.getAnnotationOnlyIds().isEmpty()) {
                logger.info("Annotation-only IDs: " + result.getAnnotationOnlyIds());
            }
            
            return result;
            
        } catch (IOException e) {
            logger.severe("Error checking chromosome ID consistency: " + e.getMessage());
            return new ConsistencyResult(false, "Error reading files: " + e.getMessage());
        }
    }
    
    /**
     * Extract chromosome IDs from FASTA genome file
     */
    private static Set<String> extractGenomeIds(File genomeFile) throws IOException {
        Set<String> ids = new HashSet<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(genomeFile))) {
            String line;
            int linesRead = 0;
            int maxLines = 10000; // Read more lines to catch all chromosomes
            
            while ((line = reader.readLine()) != null && linesRead < maxLines) {
                linesRead++;
                line = line.trim();
                
                if (line.startsWith(">")) {
                    // Extract sequence ID (first part of header)
                    String header = line.substring(1); // Remove '>'
                    String seqId = header.split("\\s+")[0]; // Take first part before space
                    ids.add(seqId);
                }
            }
        }
        
        logger.info("Extracted " + ids.size() + " chromosome IDs from genome file");
        return ids;
    }
    
    /**
     * Extract chromosome IDs from annotation file (GFF3/GTF)
     */
    private static Set<String> extractAnnotationIds(File annotationFile) throws IOException {
        Set<String> ids = new HashSet<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(annotationFile))) {
            String line;
            int linesRead = 0;
            int maxLines = 50000; // Read more lines for large annotation files
            
            while ((line = reader.readLine()) != null && linesRead < maxLines) {
                linesRead++;
                line = line.trim();
                
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip empty lines and comments
                }
                
                String[] fields = line.split("\\t");
                if (fields.length >= 1) {
                    String seqId = fields[0]; // First column is sequence ID
                    ids.add(seqId);
                }
            }
        }
        
        logger.info("Extracted " + ids.size() + " unique chromosome IDs from annotation file");
        return ids;
    }
}