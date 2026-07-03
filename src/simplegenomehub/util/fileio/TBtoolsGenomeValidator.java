/*
 * TBtools Genome Validation Wrapper
 * Integrates TBtools GXF Genome Match functionality into SimpleGenomeHub
 */
package simplegenomehub.util.fileio;

import biocjava.bioDoer.GXFUtils.GxfGenomeMatch;
import biocjava.bioIO.FastX.FastaIndex.FastaChunkReader;
import biocjava.bioIO.GXF.GXFReader;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Validates genome and annotation file compatibility using TBtools functionality
 * Provides enhanced validation results with detailed chromosome ID matching information
 * 
 * @author SimpleGenomeHub
 */
public class TBtoolsGenomeValidator {
    
    private static final Logger logger = Logger.getLogger(TBtoolsGenomeValidator.class.getName());
    
    /**
     * Enhanced validation result with detailed information
     */
    public static class TBtoolsValidationResult {
        private boolean isValid;
        private String message;
        private Set<String> genomeIds;
        private Set<String> gxfIds;
        private Set<String> intersectionIds;
        private Set<String> genomeOnlyIds;
        private Set<String> gxfOnlyIds;
        private int intersectionSize;
        private int differenceGxfSize;
        private int differenceFastaSize;
        private Exception exception;
        
        public TBtoolsValidationResult(boolean isValid, String message) {
            this.isValid = isValid;
            this.message = message;
            this.genomeIds = new HashSet<>();
            this.gxfIds = new HashSet<>();
            this.intersectionIds = new HashSet<>();
            this.genomeOnlyIds = new HashSet<>();
            this.gxfOnlyIds = new HashSet<>();
        }
        
        // Getters
        public boolean isValid() { return isValid; }
        public String getMessage() { return message; }
        public Set<String> getGenomeIds() { return genomeIds; }
        public Set<String> getGxfIds() { return gxfIds; }
        public Set<String> getIntersectionIds() { return intersectionIds; }
        public Set<String> getGenomeOnlyIds() { return genomeOnlyIds; }
        public Set<String> getGxfOnlyIds() { return gxfOnlyIds; }
        public int getIntersectionSize() { return intersectionSize; }
        public int getDifferenceGxfSize() { return differenceGxfSize; }
        public int getDifferenceFastaSize() { return differenceFastaSize; }
        public Exception getException() { return exception; }
        
        // Setters
        public void setGenomeIds(Set<String> genomeIds) { this.genomeIds = genomeIds; }
        public void setGxfIds(Set<String> gxfIds) { this.gxfIds = gxfIds; }
        public void setIntersectionIds(Set<String> intersectionIds) { this.intersectionIds = intersectionIds; }
        public void setGenomeOnlyIds(Set<String> genomeOnlyIds) { this.genomeOnlyIds = genomeOnlyIds; }
        public void setGxfOnlyIds(Set<String> gxfOnlyIds) { this.gxfOnlyIds = gxfOnlyIds; }
        public void setIntersectionSize(int size) { this.intersectionSize = size; }
        public void setDifferenceGxfSize(int size) { this.differenceGxfSize = size; }
        public void setDifferenceFastaSize(int size) { this.differenceFastaSize = size; }
        public void setException(Exception exception) { this.exception = exception; }
        
        /**
         * Get detailed validation report
         */
        public String getDetailedReport() {
            StringBuilder report = new StringBuilder();
            report.append("=== TBtools Genome-Annotation Validation Report ===\n");
            report.append("Validation Result: ").append(isValid ? "PASSED" : "FAILED").append("\n");
            report.append("Message: ").append(message).append("\n\n");
            
            if (!genomeIds.isEmpty() || !gxfIds.isEmpty()) {
                report.append("Statistics:\n");
                report.append("- Genome chromosome IDs: ").append(genomeIds.size()).append("\n");
                report.append("- GXF chromosome IDs: ").append(gxfIds.size()).append("\n");
                report.append("- Common IDs (intersection): ").append(intersectionSize).append("\n");
                report.append("- GXF-only IDs: ").append(differenceGxfSize).append("\n");
                report.append("- Genome-only IDs: ").append(differenceFastaSize).append("\n\n");
                
                if (!intersectionIds.isEmpty()) {
                    report.append("Common chromosome IDs:\n");
                    for (String id : intersectionIds) {
                        report.append("  ✓ ").append(id).append("\n");
                    }
                    report.append("\n");
                }
                
                if (!gxfOnlyIds.isEmpty()) {
                    report.append("GXF-only chromosome IDs (ERROR - not found in genome):\n");
                    for (String id : gxfOnlyIds) {
                        report.append("  ✗ ").append(id).append("\n");
                    }
                    report.append("\n");
                }
                
                if (!genomeOnlyIds.isEmpty()) {
                    report.append("Genome-only chromosome IDs (no annotations):\n");
                    for (String id : genomeOnlyIds) {
                        report.append("  ⚠ ").append(id).append("\n");
                    }
                    report.append("\n");
                }
            }
            
            if (exception != null) {
                report.append("Exception: ").append(exception.getMessage()).append("\n");
            }
            
            return report.toString();
        }
    }
    
    /**
     * Validate genome and annotation file compatibility using TBtools GxfGenomeMatch
     * 
     * @param genomeFile The genome FASTA file
     * @param gxfFile The annotation GXF/GFF3/GTF file
     * @return TBtoolsValidationResult with detailed validation information
     */
    public static TBtoolsValidationResult validateGenomeAnnotationMatch(File genomeFile, File gxfFile) {
        logger.info("Starting TBtools-based genome-annotation validation");
        
        try {
            // First check if files exist and are readable
            if (!genomeFile.exists() || !genomeFile.canRead()) {
                return new TBtoolsValidationResult(false, "Genome file does not exist or is not readable: " + genomeFile.getPath());
            }
            
            if (!gxfFile.exists() || !gxfFile.canRead()) {
                return new TBtoolsValidationResult(false, "GXF file does not exist or is not readable: " + gxfFile.getPath());
            }
            
            // Use TBtools GxfGenomeMatch for validation
            boolean isMatch = GxfGenomeMatch.isMatch(gxfFile, genomeFile);
            
            // Get detailed information for enhanced reporting
            TBtoolsValidationResult result = getDetailedMatchInformation(genomeFile, gxfFile, isMatch);
            
            if (isMatch) {
                result.message = "Files are compatible - all GXF chromosome IDs found in genome";
                logger.info("TBtools validation PASSED: " + result.message);
            } else {
                result.message = "Files are NOT compatible - some GXF chromosome IDs not found in genome";
                logger.warning("TBtools validation FAILED: " + result.message);
            }
            
            return result;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "IO error during TBtools validation", e);
            TBtoolsValidationResult result = new TBtoolsValidationResult(false, "IO error during validation: " + e.getMessage());
            result.setException(e);
            return result;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during TBtools validation", e);
            TBtoolsValidationResult result = new TBtoolsValidationResult(false, "Unexpected error during validation: " + e.getMessage());
            result.setException(e);
            return result;
        }
    }
    
    /**
     * Get detailed chromosome ID matching information
     * Similar to GxfGenomeMatch.isMatch() but with enhanced data collection
     */
    private static TBtoolsValidationResult getDetailedMatchInformation(File genomeFile, File gxfFile, boolean isMatch) throws IOException {
        TBtoolsValidationResult result = new TBtoolsValidationResult(isMatch, "");
        
        Set<String> gxfChrIDSet = new HashSet<>();
        Set<String> faChrIDSet = new HashSet<>();
        
        // Extract GXF chromosome IDs
        logger.fine("Extracting chromosome IDs from GXF file");
        GXFReader gxfReader = new GXFReader(gxfFile);
        try {
            while (gxfReader.hasNext()) {
                String curId = gxfReader.getNext().getChrId();
                gxfChrIDSet.add(curId);
            }
        } finally {
            gxfReader.close();
        }
        
        // Extract FASTA chromosome IDs (same logic as TBtools)
        logger.fine("Extracting chromosome IDs from genome file");
        FastaChunkReader bfcr = new FastaChunkReader(genomeFile);
        try {
            while (bfcr.hasNext()) {
                StringBuilder idSb = new StringBuilder();
                while (bfcr.isIdChunk() && bfcr.hasNext()) {
                    idSb.append(bfcr.getNext());
                }
                
                // Apply same ID simplification as TBtools (line 84 in GxfGenomeMatch.java)
                if (idSb.length() > 1) {
                    String simplifiedId = idSb.substring(1).replaceAll("^(\\S+).*?$", "$1");
                    faChrIDSet.add(simplifiedId);
                }
                
                // Skip sequence chunks
                while (!bfcr.isIdChunk() && bfcr.hasNext()) {
                    bfcr.getNext();
                }
            }
        } finally {
            bfcr.close();
        }
        
        // Calculate intersections and differences (same logic as TBtools)
        Set<String> intersection = new HashSet<>(gxfChrIDSet);
        intersection.retainAll(faChrIDSet);
        
        Set<String> differenceGxf = new HashSet<>(gxfChrIDSet);
        differenceGxf.removeAll(faChrIDSet);
        
        Set<String> differenceFasta = new HashSet<>(faChrIDSet);
        differenceFasta.removeAll(gxfChrIDSet);
        
        // Set detailed information
        result.setGenomeIds(faChrIDSet);
        result.setGxfIds(gxfChrIDSet);
        result.setIntersectionIds(intersection);
        result.setGxfOnlyIds(differenceGxf);
        result.setGenomeOnlyIds(differenceFasta);
        result.setIntersectionSize(intersection.size());
        result.setDifferenceGxfSize(differenceGxf.size());
        result.setDifferenceFastaSize(differenceFasta.size());
        
        logger.fine(String.format("Validation stats - Intersection: %d, GXF-only: %d, Genome-only: %d", 
                    intersection.size(), differenceGxf.size(), differenceFasta.size()));
        
        return result;
    }
    
    /**
     * Quick validation using TBtools (for cases where detailed info is not needed)
     */
    public static boolean isGenomeAnnotationMatch(File genomeFile, File gxfFile) throws IOException {
        return GxfGenomeMatch.isMatch(gxfFile, genomeFile);
    }
    
    /**
     * Get a simple validation message for UI display
     */
    public static String getSimpleValidationMessage(File genomeFile, File gxfFile) {
        try {
            TBtoolsValidationResult result = validateGenomeAnnotationMatch(genomeFile, gxfFile);
            if (result.isValid()) {
                return String.format("✓ Files are compatible (%d common chromosome IDs)", result.getIntersectionSize());
            } else {
                return String.format("✗ Files are NOT compatible (%d GXF IDs not found in genome)", result.getDifferenceGxfSize());
            }
        } catch (Exception e) {
            return "✗ Validation error: " + e.getMessage();
        }
    }
}