/*
 * File Compatibility Checker for Genome and Annotation Files
 */
package simplegenomehub.util.fileio;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Checks compatibility between genome FASTA files and annotation GFF3/GTF files
 * Ensures sequence IDs match and coordinates are valid
 * 
 * @author SimpleGenomeHub
 */
public class FileCompatibilityChecker {
    
    private static final Logger logger = Logger.getLogger(FileCompatibilityChecker.class.getName());
    
    /**
     * Compatibility check result
     */
    public static class CompatibilityResult {
        private boolean compatible;
        private String message;
        private Map<String, Object> details;
        private List<String> warnings;
        
        public CompatibilityResult(boolean compatible, String message) {
            this.compatible = compatible;
            this.message = message;
            this.details = new HashMap<>();
            this.warnings = new ArrayList<>();
        }
        
        // Getters and setters
        public boolean isCompatible() { return compatible; }
        public String getMessage() { return message; }
        public Map<String, Object> getDetails() { return details; }
        public List<String> getWarnings() { return warnings; }
        
        public void setDetail(String key, Object value) { details.put(key, value); }
        public void addWarning(String warning) { warnings.add(warning); }
    }
    
    /**
     * Check compatibility between genome and annotation files
     */
    public static CompatibilityResult checkCompatibility(File genomeFile, File annotationFile) {
        logger.info("Checking compatibility between " + genomeFile.getName() + " and " + annotationFile.getName());
        
        try {
            // First validate both files individually
            GenomeFileValidator.ValidationResult genomeResult = GenomeFileValidator.validateFile(genomeFile);
            GenomeFileValidator.ValidationResult annotationResult = GenomeFileValidator.validateFile(annotationFile);
            
            if (!genomeResult.isValid()) {
                return new CompatibilityResult(false, "Genome file validation failed: " + genomeResult.getErrorMessage());
            }
            
            if (!annotationResult.isValid()) {
                return new CompatibilityResult(false, "Annotation file validation failed: " + annotationResult.getErrorMessage());
            }
            
            // Check if genome is FASTA and annotation is GFF3/GTF
            if (genomeResult.getFormat() != GenomeFileValidator.FileFormat.FASTA) {
                return new CompatibilityResult(false, "Genome file must be in FASTA format, found: " + genomeResult.getFormat());
            }
            
            if (annotationResult.getFormat() != GenomeFileValidator.FileFormat.GFF3 && 
                annotationResult.getFormat() != GenomeFileValidator.FileFormat.GTF) {
                return new CompatibilityResult(false, "Annotation file must be in GFF3 or GTF format, found: " + annotationResult.getFormat());
            }
            
            // Extract sequence IDs from both files
            Set<String> genomeSeqIds = extractFASTASequenceIds(genomeFile);
            Set<String> annotationSeqIds = extractAnnotationSequenceIds(annotationFile);
            
            return checkSequenceCompatibility(genomeSeqIds, annotationSeqIds, genomeFile, annotationFile);
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error checking file compatibility", e);
            return new CompatibilityResult(false, "Error reading files: " + e.getMessage());
        }
    }
    
    /**
     * Extract sequence IDs from FASTA file
     */
    private static Set<String> extractFASTASequenceIds(File fastaFile) throws IOException {
        Set<String> seqIds = new HashSet<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(fastaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith(">")) {
                    // Extract sequence ID (first word after >)
                    String header = line.substring(1);
                    String seqId = header.split("\\s+")[0];
                    seqIds.add(seqId);
                }
            }
        }
        
        logger.info("Extracted " + seqIds.size() + " sequence IDs from FASTA file");
        return seqIds;
    }
    
    /**
     * Extract sequence IDs from annotation file (GFF3/GTF)
     */
    private static Set<String> extractAnnotationSequenceIds(File annotationFile) throws IOException {
        Set<String> seqIds = new HashSet<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(annotationFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] fields = line.split("\t");
                if (fields.length >= 1) {
                    seqIds.add(fields[0]); // seqid is first field
                }
            }
        }
        
        logger.info("Extracted " + seqIds.size() + " sequence IDs from annotation file");
        return seqIds;
    }
    
    /**
     * Check sequence ID compatibility between genome and annotation files
     */
    private static CompatibilityResult checkSequenceCompatibility(Set<String> genomeSeqIds, 
                                                                 Set<String> annotationSeqIds, 
                                                                 File genomeFile, 
                                                                 File annotationFile) {
        CompatibilityResult result = new CompatibilityResult(true, "Files are compatible");
        
        // Find sequences in annotation but not in genome
        Set<String> missingInGenome = new HashSet<>(annotationSeqIds);
        missingInGenome.removeAll(genomeSeqIds);
        
        // Find sequences in genome but not in annotation
        Set<String> missingInAnnotation = new HashSet<>(genomeSeqIds);
        missingInAnnotation.removeAll(annotationSeqIds);
        
        // Calculate overlap
        Set<String> commonSeqIds = new HashSet<>(genomeSeqIds);
        commonSeqIds.retainAll(annotationSeqIds);
        
        result.setDetail("genomeSequenceCount", genomeSeqIds.size());
        result.setDetail("annotationSequenceCount", annotationSeqIds.size());
        result.setDetail("commonSequenceCount", commonSeqIds.size());
        result.setDetail("missingInGenome", missingInGenome);
        result.setDetail("missingInAnnotation", missingInAnnotation);
        
        // Determine compatibility
        if (commonSeqIds.isEmpty()) {
            return new CompatibilityResult(false, 
                "No common sequence IDs found between genome and annotation files. " +
                "Please check that sequence names match exactly.");
        }
        
        if (!missingInGenome.isEmpty()) {
            if (missingInGenome.size() > annotationSeqIds.size() * 0.1) { // More than 10% missing
                return new CompatibilityResult(false, 
                    "Too many sequences referenced in annotation are missing from genome: " + 
                    missingInGenome.size() + " out of " + annotationSeqIds.size());
            } else {
                result.addWarning("Some sequences referenced in annotation are missing from genome: " + 
                    missingInGenome.size() + " sequences (" + 
                    String.join(", ", missingInGenome.size() > 5 ? 
                        new ArrayList<>(missingInGenome).subList(0, 5) : missingInGenome) + 
                    (missingInGenome.size() > 5 ? "..." : "") + ")");
            }
        }
        
        if (!missingInAnnotation.isEmpty()) {
            result.addWarning("Some genome sequences have no annotations: " + 
                missingInAnnotation.size() + " sequences");
        }
        
        // Calculate overlap percentage
        double overlapPercentage = (double) commonSeqIds.size() / Math.max(genomeSeqIds.size(), annotationSeqIds.size()) * 100;
        result.setDetail("overlapPercentage", overlapPercentage);
        
        if (overlapPercentage < 80) {
            result.addWarning(String.format("Low sequence overlap between files: %.1f%%", overlapPercentage));
        }
        
        // Additional checks for coordinate ranges (sample-based)
        try {
            checkCoordinateRanges(genomeFile, annotationFile, commonSeqIds, result);
        } catch (IOException e) {
            result.addWarning("Could not validate coordinate ranges: " + e.getMessage());
        }
        
        // Log results
        logger.info(String.format("Compatibility check completed: %s (%.1f%% overlap, %d common sequences)",
            result.isCompatible() ? "COMPATIBLE" : "INCOMPATIBLE",
            overlapPercentage, commonSeqIds.size()));
        
        if (!result.getWarnings().isEmpty()) {
            logger.warning("Warnings: " + String.join("; ", result.getWarnings()));
        }
        
        return result;
    }
    
    /**
     * Check coordinate ranges (basic validation)
     */
    private static void checkCoordinateRanges(File genomeFile, File annotationFile, 
                                            Set<String> commonSeqIds, CompatibilityResult result) throws IOException {
        
        // Get approximate sequence lengths from FASTA (sample first few common sequences)
        Map<String, Integer> sequenceLengths = new HashMap<>();
        Set<String> samplesToCheck = new HashSet<>();
        
        int sampleCount = Math.min(5, commonSeqIds.size());
        Iterator<String> iter = commonSeqIds.iterator();
        for (int i = 0; i < sampleCount && iter.hasNext(); i++) {
            samplesToCheck.add(iter.next());
        }
        
        // Extract lengths for sample sequences
        try (BufferedReader reader = new BufferedReader(new FileReader(genomeFile))) {
            String line;
            String currentSeqId = null;
            int currentLength = 0;
            
            while ((line = reader.readLine()) != null && !samplesToCheck.isEmpty()) {
                line = line.trim();
                if (line.startsWith(">")) {
                    // Save previous sequence length
                    if (currentSeqId != null && samplesToCheck.contains(currentSeqId)) {
                        sequenceLengths.put(currentSeqId, currentLength);
                        samplesToCheck.remove(currentSeqId);
                    }
                    
                    // Start new sequence
                    String seqId = line.substring(1).split("\\s+")[0];
                    if (samplesToCheck.contains(seqId)) {
                        currentSeqId = seqId;
                        currentLength = 0;
                    } else {
                        currentSeqId = null;
                    }
                } else if (currentSeqId != null) {
                    currentLength += line.length();
                }
            }
            
            // Save last sequence
            if (currentSeqId != null && samplesToCheck.contains(currentSeqId)) {
                sequenceLengths.put(currentSeqId, currentLength);
            }
        }
        
        // Check annotation coordinates against sequence lengths
        int outOfRangeCount = 0;
        int totalChecked = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(annotationFile))) {
            String line;
            while ((line = reader.readLine()) != null && totalChecked < 1000) { // Sample check
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] fields = line.split("\t");
                if (fields.length >= 5) {
                    String seqId = fields[0];
                    if (sequenceLengths.containsKey(seqId)) {
                        try {
                            int end = Integer.parseInt(fields[4]);
                            int seqLength = sequenceLengths.get(seqId);
                            
                            if (end > seqLength) {
                                outOfRangeCount++;
                            }
                            totalChecked++;
                        } catch (NumberFormatException e) {
                            // Skip invalid coordinates
                        }
                    }
                }
            }
        }
        
        if (outOfRangeCount > 0) {
            result.addWarning(String.format("Found %d features with coordinates beyond sequence length (checked %d features)", 
                outOfRangeCount, totalChecked));
        }
        
        result.setDetail("coordinateCheckSample", totalChecked);
        result.setDetail("outOfRangeFeatures", outOfRangeCount);
    }
}