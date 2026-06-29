/*
 * Genome File Format Validation Utilities
 */
package simplegenomehub.util.fileio;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Validates FASTA and GFF3/GTF file formats for genome data import
 * Based on TBtools FileFormatDetector patterns
 * 
 * @author SimpleGenomeHub
 */
public class GenomeFileValidator {
    
    private static final Logger logger = Logger.getLogger(GenomeFileValidator.class.getName());
    
    // File format detection patterns
    private static final Pattern FASTA_HEADER = Pattern.compile("^>[^\\s]+.*$");
    private static final Pattern GFF3_HEADER = Pattern.compile("^##gff-version\\s*3.*$");
    private static final Pattern GFF3_LINE = Pattern.compile("^[^#][^\\t]*\\t[^\\t]*\\t[^\\t]*\\t\\d+\\t\\d+\\t[^\\t]*\\t[+-.]\\t[^\\t]*\\t.*$");
    private static final Pattern GTF_LINE = Pattern.compile("^[^#][^\\t]*\\t[^\\t]*\\t[^\\t]*\\t\\d+\\t\\d+\\t[^\\t]*\\t[+-.]\\t[^\\t]*\\t.*gene_id.*$");
    
    // Validation results
    public static class ValidationResult {
        private boolean valid;
        private FileFormat format;
        private String errorMessage;
        private Map<String, Object> metadata;
        
        public ValidationResult(boolean valid, FileFormat format, String errorMessage) {
            this.valid = valid;
            this.format = format;
            this.errorMessage = errorMessage;
            this.metadata = new HashMap<>();
        }
        
        // Getters and setters
        public boolean isValid() { return valid; }
        public FileFormat getFormat() { return format; }
        public String getErrorMessage() { return errorMessage; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(String key, Object value) { metadata.put(key, value); }
    }
    
    public enum FileFormat {
        FASTA, GFF3, GTF, UNKNOWN
    }
    
    /**
     * Validate and detect file format
     */
    public static ValidationResult validateFile(File file) {
        if (file == null || !file.exists() || !file.canRead()) {
            return new ValidationResult(false, FileFormat.UNKNOWN,
                "File does not exist or cannot be read: " + (file != null ? file.getAbsolutePath() : "null"));
        }

        if (file.length() == 0) {
            return new ValidationResult(false, FileFormat.UNKNOWN,
                "File is empty: " + file.getAbsolutePath());
        }

        try (BufferedReader reader = FileReaderUtil.createBufferedReader(file)) {
            return detectAndValidateFormat(reader, file);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error reading file: " + file.getAbsolutePath(), e);
            return new ValidationResult(false, FileFormat.UNKNOWN,
                "Error reading file: " + e.getMessage());
        }
    }
    
    /**
     * Detect file format and perform detailed validation
     */
    private static ValidationResult detectAndValidateFormat(BufferedReader reader, File file) throws IOException {
        String line;
        int lineNumber = 0;
        FileFormat detectedFormat = FileFormat.UNKNOWN;
        List<String> errors = new ArrayList<>();
        Map<String, Object> metadata = new HashMap<>();
        
        // Read first few lines to determine format
        List<String> initialLines = new ArrayList<>();
        boolean formatDetected = false;
        int maxLinesToRead = 500;  // Increase to 500 lines for better detection of large files
        
        while ((line = reader.readLine()) != null && initialLines.size() < maxLinesToRead) {
            initialLines.add(line);
            lineNumber++;
            
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) {
                continue;
            }
            
            // Check for comment lines
            if (trimmedLine.startsWith("#")) {
                // Check for GFF3 version header
                if (GFF3_HEADER.matcher(trimmedLine).matches()) {
                    detectedFormat = FileFormat.GFF3;
                    formatDetected = true;
                }
                continue;
            }
            
            // Detect FASTA format
            if (trimmedLine.startsWith(">")) {
                if (detectedFormat == FileFormat.UNKNOWN) {
                    detectedFormat = FileFormat.FASTA;
                    formatDetected = true;
                }
                // Don't break here - continue reading to get more headers and sequences
            }
            
            // Detect GFF3/GTF format
            if (line.contains("\t")) {
                String[] fields = line.split("\t");
                if (fields.length >= 9) {
                    if (GTF_LINE.matcher(line).matches()) {
                        if (detectedFormat == FileFormat.UNKNOWN) {
                            detectedFormat = FileFormat.GTF;
                        }
                    } else if (GFF3_LINE.matcher(line).matches()) {
                        if (detectedFormat == FileFormat.UNKNOWN) {
                            detectedFormat = FileFormat.GFF3;
                        }
                    }
                    break;
                }
            }
        }
        
        // Perform format-specific validation
        ValidationResult result;
        switch (detectedFormat) {
            case FASTA:
                result = validateFASTA(initialLines, file);
                break;
            case GFF3:
                result = validateGFF3(initialLines, file);
                break;
            case GTF:
                result = validateGTF(initialLines, file);
                break;
            default:
                result = new ValidationResult(false, FileFormat.UNKNOWN, 
                    "Unable to determine file format. Expected FASTA, GFF3, or GTF format.");
        }
        
        return result;
    }
    
    /**
     * Validate FASTA format
     */
    private static ValidationResult validateFASTA(List<String> initialLines, File file) {
        ValidationResult result = new ValidationResult(true, FileFormat.FASTA, null);
        List<String> errors = new ArrayList<>();
        
        boolean hasHeader = false;
        boolean hasSequence = false;
        int sequenceCount = 0;
        long totalLength = 0;
        Set<String> seqIds = new HashSet<>();
        
        StringBuilder currentSequence = new StringBuilder();
        String currentHeader = null;
        
        for (String line : initialLines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.startsWith(">")) {
                // Process previous sequence
                if (currentHeader != null && currentSequence.length() > 0) {
                    totalLength += currentSequence.length();
                    hasSequence = true;
                }
                
                // Validate header format
                if (line.length() == 1) {
                    errors.add("Empty sequence header found");
                } else {
                    hasHeader = true;
                    sequenceCount++;
                    
                    // Extract sequence ID
                    String seqId = line.substring(1).split("\\s+")[0];
                    if (seqIds.contains(seqId)) {
                        errors.add("Duplicate sequence ID found: " + seqId);
                    } else {
                        seqIds.add(seqId);
                    }
                }
                
                currentHeader = line;
                currentSequence = new StringBuilder();
                
            } else {
                // Validate sequence line
                if (currentHeader == null) {
                    errors.add("Sequence data found without header");
                    break;
                }
                
                // Check for invalid characters in DNA/RNA/Protein sequences (more permissive)
                // Allow IUPAC nucleotide codes, protein codes, and common variations
                if (!line.matches("^[ACGTUNRYSWKMBDHVacgtunryswkmbdhvILFPQEDWXZJO*.-]*$")) {
                    // Only flag truly invalid characters (numbers, special symbols)
                    if (line.matches(".*[0-9@#$%^&()+=\\[\\]{}|\\\\:;\"'<>?/~`].*")) {
                        logger.warning("Potentially invalid characters in sequence line: " + 
                                     line.substring(0, Math.min(50, line.length())));
                        // Don't add to errors - just log as warning
                    }
                }
                
                currentSequence.append(line);
            }
        }
        
        // Final sequence processing
        if (currentHeader != null && currentSequence.length() > 0) {
            totalLength += currentSequence.length();
            hasSequence = true;
        }
        
        // Validation checks - more lenient
        if (!hasHeader) {
            errors.add("No FASTA headers found");
        }
        // Only flag as error if we have headers but absolutely no sequence content
        if (!hasSequence && hasHeader && sequenceCount > 0) {
            // This is likely a large file where sequence data starts after our read limit
            // Consider it valid if we found headers
            logger.info("No sequence data found in sample lines, but found " + sequenceCount + " headers. Assuming valid large FASTA file.");
            hasSequence = true; // Override for large files
        } else if (!hasSequence) {
            errors.add("No sequence data found");
        }
        
        // Set metadata
        result.setMetadata("sequenceCount", sequenceCount);
        result.setMetadata("totalLength", totalLength);
        result.setMetadata("uniqueIds", seqIds.size());
        
        if (!errors.isEmpty()) {
            result = new ValidationResult(false, FileFormat.FASTA, String.join("; ", errors));
        }
        
        logger.info("FASTA validation completed for: " + file.getName() + 
                   " (sequences: " + sequenceCount + ", total length: " + totalLength + ")");
        
        return result;
    }
    
    /**
     * Validate GFF3 format
     */
    private static ValidationResult validateGFF3(List<String> initialLines, File file) {
        ValidationResult result = new ValidationResult(true, FileFormat.GFF3, null);
        List<String> errors = new ArrayList<>();
        
        boolean hasVersionHeader = false;
        int featureCount = 0;
        Set<String> seqIds = new HashSet<>();
        Set<String> featureTypes = new HashSet<>();
        
        for (String line : initialLines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.startsWith("##gff-version")) {
                hasVersionHeader = true;
                continue;
            }
            
            if (line.startsWith("#")) {
                continue; // Skip other comments
            }
            
            // Validate feature line
            String[] fields = line.split("\t");
            if (fields.length != 9) {
                errors.add("Invalid number of fields in GFF3 line (expected 9): " + fields.length);
                continue;
            }
            
            featureCount++;
            seqIds.add(fields[0]); // seqid
            featureTypes.add(fields[2]); // type
            
            // Validate coordinates
            try {
                int start = Integer.parseInt(fields[3]);
                int end = Integer.parseInt(fields[4]);
                if (start > end && !fields[4].equals(".")) {
                    errors.add("Start position greater than end position: " + start + " > " + end);
                }
                if (start < 1) {
                    errors.add("Start position less than 1: " + start);
                }
            } catch (NumberFormatException e) {
                if (!fields[3].equals(".") || !fields[4].equals(".")) {
                    errors.add("Invalid coordinate format: " + fields[3] + "-" + fields[4]);
                }
            }
            
            // Validate strand
            if (!fields[6].matches("[+\\-\\.]")) {
                errors.add("Invalid strand format: " + fields[6]);
            }
            
            // Validate attributes (basic check)
            if (fields[8].isEmpty()) {
                errors.add("Empty attributes field");
            }
        }
        
        // Set metadata
        result.setMetadata("hasVersionHeader", hasVersionHeader);
        result.setMetadata("featureCount", featureCount);
        result.setMetadata("seqIdCount", seqIds.size());
        result.setMetadata("featureTypes", featureTypes);
        
        if (!hasVersionHeader) {
            // Warning but not error
            logger.warning("GFF3 file missing version header: " + file.getName());
        }
        
        if (featureCount == 0) {
            errors.add("No feature lines found");
        }
        
        if (!errors.isEmpty()) {
            result = new ValidationResult(false, FileFormat.GFF3, String.join("; ", errors));
        }
        
        logger.info("GFF3 validation completed for: " + file.getName() + 
                   " (features: " + featureCount + ", sequences: " + seqIds.size() + ")");
        
        return result;
    }
    
    /**
     * Validate GTF format
     */
    private static ValidationResult validateGTF(List<String> initialLines, File file) {
        ValidationResult result = new ValidationResult(true, FileFormat.GTF, null);
        List<String> errors = new ArrayList<>();
        
        int featureCount = 0;
        Set<String> seqIds = new HashSet<>();
        Set<String> featureTypes = new HashSet<>();
        boolean hasGeneId = false;
        
        for (String line : initialLines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            String[] fields = line.split("\t");
            if (fields.length != 9) {
                errors.add("Invalid number of fields in GTF line (expected 9): " + fields.length);
                continue;
            }
            
            featureCount++;
            seqIds.add(fields[0]);
            featureTypes.add(fields[2]);
            
            // Check for required gene_id attribute
            if (fields[8].contains("gene_id")) {
                hasGeneId = true;
            }
        }
        
        if (!hasGeneId && featureCount > 0) {
            errors.add("GTF file missing required gene_id attributes");
        }
        
        result.setMetadata("featureCount", featureCount);
        result.setMetadata("seqIdCount", seqIds.size());
        result.setMetadata("featureTypes", featureTypes);
        
        if (!errors.isEmpty()) {
            result = new ValidationResult(false, FileFormat.GTF, String.join("; ", errors));
        }
        
        logger.info("GTF validation completed for: " + file.getName() + 
                   " (features: " + featureCount + ", sequences: " + seqIds.size() + ")");
        
        return result;
    }
}