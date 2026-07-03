/*
 * Data Validator
 */
package simplegenomehub.util.validation;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.GenomeData;
import simplegenomehub.util.fileio.GenomeFileValidator;
import simplegenomehub.util.fileio.FileCompatibilityChecker;
import simplegenomehub.util.fileio.GenomeStatsCalculator;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Comprehensive data validation utility for species collections
 * Validates file integrity, compatibility, and data completeness
 * 
 * @author SimpleGenomeHub
 */
public class DataValidator {
    
    private static final Logger logger = Logger.getLogger(DataValidator.class.getName());
    
    /**
     * Validation result for a single species
     */
    public static class ValidationResult {
        private final SpeciesInfo species;
        private final boolean isValid;
        private final List<ValidationIssue> issues;
        private final List<String> warnings;
        
        public ValidationResult(SpeciesInfo species, boolean isValid, 
                              List<ValidationIssue> issues, List<String> warnings) {
            this.species = species;
            this.isValid = isValid;
            this.issues = new ArrayList<>(issues);
            this.warnings = new ArrayList<>(warnings);
        }
        
        public SpeciesInfo getSpecies() { return species; }
        public boolean isValid() { return isValid; }
        public List<ValidationIssue> getIssues() { return new ArrayList<>(issues); }
        public List<String> getWarnings() { return new ArrayList<>(warnings); }
        
        public boolean hasErrors() {
            return issues.stream().anyMatch(issue -> issue.getSeverity() == ValidationIssue.Severity.ERROR);
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty() || 
                   issues.stream().anyMatch(issue -> issue.getSeverity() == ValidationIssue.Severity.WARNING);
        }
        
        public int getErrorCount() {
            return (int) issues.stream().filter(issue -> issue.getSeverity() == ValidationIssue.Severity.ERROR).count();
        }
        
        public int getWarningCount() {
            return warnings.size() + 
                   (int) issues.stream().filter(issue -> issue.getSeverity() == ValidationIssue.Severity.WARNING).count();
        }
    }
    
    /**
     * Validation issue types
     */
    public static class ValidationIssue {
        public enum Severity { ERROR, WARNING, INFO }
        public enum Category { FILE_MISSING, FILE_INVALID, COMPATIBILITY, DATA_INTEGRITY, PERFORMANCE }
        
        private final Category category;
        private final Severity severity;
        private final String message;
        private final String details;
        private final boolean autoRepairable;
        
        public ValidationIssue(Category category, Severity severity, String message, 
                             String details, boolean autoRepairable) {
            this.category = category;
            this.severity = severity;
            this.message = message;
            this.details = details;
            this.autoRepairable = autoRepairable;
        }
        
        public Category getCategory() { return category; }
        public Severity getSeverity() { return severity; }
        public String getMessage() { return message; }
        public String getDetails() { return details; }
        public boolean isAutoRepairable() { return autoRepairable; }
    }
    
    /**
     * Validate a single species
     */
    public static ValidationResult validateSpecies(SpeciesInfo species) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean isValid = true;
        
        try {
            logger.info("Validating species: " + species.getDisplayName());
            
            // Check directory structure
            if (!validateDirectoryStructure(species, issues)) {
                isValid = false;
            }
            
            // Check file existence and validity
            if (!validateFiles(species, issues, warnings)) {
                isValid = false;
            }
            
            // Check data integrity
            if (!validateDataIntegrity(species, issues, warnings)) {
                isValid = false;
            }
            
            // Check performance considerations
            validatePerformanceIssues(species, issues, warnings);
            
        } catch (Exception e) {
            logger.severe("Validation failed for " + species.getDisplayName() + ": " + e.getMessage());
            issues.add(new ValidationIssue(
                ValidationIssue.Category.DATA_INTEGRITY,
                ValidationIssue.Severity.ERROR,
                "Validation process failed",
                "Exception: " + e.getMessage(),
                false
            ));
            isValid = false;
        }
        
        return new ValidationResult(species, isValid, issues, warnings);
    }
    
    /**
     * Validate multiple species
     */
    public static List<ValidationResult> validateMultipleSpecies(List<SpeciesInfo> speciesList) {
        List<ValidationResult> results = new ArrayList<>();
        
        for (SpeciesInfo species : speciesList) {
            results.add(validateSpecies(species));
        }
        
        return results;
    }
    
    /**
     * Validate directory structure
     */
    private static boolean validateDirectoryStructure(SpeciesInfo species, List<ValidationIssue> issues) {
        boolean valid = true;
        
        // Check main species directory
        if (species.getSpeciesDir() == null || !species.getSpeciesDir().exists()) {
            issues.add(new ValidationIssue(
                ValidationIssue.Category.FILE_MISSING,
                ValidationIssue.Severity.ERROR,
                "Species directory missing",
                "Main directory for " + species.getDisplayName() + " does not exist",
                true
            ));
            valid = false;
        }
        
        // Check sequence directory
        if (species.getSequenceDir() == null || !species.getSequenceDir().exists()) {
            issues.add(new ValidationIssue(
                ValidationIssue.Category.FILE_MISSING,
                ValidationIssue.Severity.ERROR,
                "Sequence directory missing",
                "sequences/ subdirectory does not exist",
                true
            ));
            valid = false;
        }
        
        // Check annotation directory
        if (species.getAnnotationDir() == null || !species.getAnnotationDir().exists()) {
            issues.add(new ValidationIssue(
                ValidationIssue.Category.FILE_MISSING,
                ValidationIssue.Severity.ERROR,
                "Annotation directory missing",
                "annotations/ subdirectory does not exist",
                true
            ));
            valid = false;
        }
        
        return valid;
    }
    
    /**
     * Validate files
     */
    private static boolean validateFiles(SpeciesInfo species, List<ValidationIssue> issues, List<String> warnings) {
        boolean valid = true;
        
        // Check genome files
        File genomeFile = findGenomeFile(species);
        if (genomeFile == null) {
            issues.add(new ValidationIssue(
                ValidationIssue.Category.FILE_MISSING,
                ValidationIssue.Severity.ERROR,
                "Genome file missing",
                "No valid genome file found in sequences directory",
                false
            ));
            valid = false;
        } else {
            // Validate genome file format
            GenomeFileValidator.ValidationResult genomeResult = GenomeFileValidator.validateFile(genomeFile);
            if (!genomeResult.isValid()) {
                issues.add(new ValidationIssue(
                    ValidationIssue.Category.FILE_INVALID,
                    ValidationIssue.Severity.ERROR,
                    "Genome file invalid",
                    "File format validation failed: " + genomeResult.getErrorMessage(),
                    false
                ));
                valid = false;
            }
            
            // Check for FASTA index
            File indexFile = new File(genomeFile.getAbsolutePath() + ".fai");
            if (!indexFile.exists()) {
                issues.add(new ValidationIssue(
                    ValidationIssue.Category.PERFORMANCE,
                    ValidationIssue.Severity.WARNING,
                    "FASTA index missing",
                    "Index file (.fai) not found - sequence extraction will be slower",
                    true
                ));
            }
        }
        
        // Check annotation files
        File annotationFile = findAnnotationFile(species);
        if (annotationFile == null) {
            issues.add(new ValidationIssue(
                ValidationIssue.Category.FILE_MISSING,
                ValidationIssue.Severity.ERROR,
                "Annotation file missing",
                "No valid annotation file found in annotations directory",
                false
            ));
            valid = false;
        } else {
            // Validate annotation file format
            GenomeFileValidator.ValidationResult annotationResult = GenomeFileValidator.validateFile(annotationFile);
            if (!annotationResult.isValid()) {
                issues.add(new ValidationIssue(
                    ValidationIssue.Category.FILE_INVALID,
                    ValidationIssue.Severity.ERROR,
                    "Annotation file invalid",
                    "File format validation failed: " + annotationResult.getErrorMessage(),
                    false
                ));
                valid = false;
            }
        }
        
        // Check file compatibility
        if (genomeFile != null && annotationFile != null) {
            FileCompatibilityChecker.CompatibilityResult compatibility = 
                FileCompatibilityChecker.checkCompatibility(genomeFile, annotationFile);
            
            if (!compatibility.isCompatible()) {
                issues.add(new ValidationIssue(
                    ValidationIssue.Category.COMPATIBILITY,
                    ValidationIssue.Severity.ERROR,
                    "Files not compatible",
                    compatibility.getMessage(),
                    false
                ));
                valid = false;
            }
            
            // Add compatibility warnings
            for (String warning : compatibility.getWarnings()) {
                warnings.add("Compatibility warning: " + warning);
            }
        }
        
        return valid;
    }
    
    /**
     * Validate data integrity
     */
    private static boolean validateDataIntegrity(SpeciesInfo species, List<ValidationIssue> issues, List<String> warnings) {
        boolean valid = true;
        
        // Check statistics file
        if (species.getStatsFile() != null && species.getStatsFile().exists()) {
            // Try to load statistics
            try {
                GenomeData loadedData = GenomeData.loadFromFile(species.getStatsFile());
                if (loadedData == null) {
                    issues.add(new ValidationIssue(
                        ValidationIssue.Category.DATA_INTEGRITY,
                        ValidationIssue.Severity.WARNING,
                        "Statistics file corrupted",
                        "Statistics file exists but cannot be loaded",
                        true
                    ));
                }
            } catch (Exception e) {
                issues.add(new ValidationIssue(
                    ValidationIssue.Category.DATA_INTEGRITY,
                    ValidationIssue.Severity.WARNING,
                    "Statistics file error",
                    "Error reading statistics: " + e.getMessage(),
                    true
                ));
            }
        } else {
            issues.add(new ValidationIssue(
                ValidationIssue.Category.FILE_MISSING,
                ValidationIssue.Severity.WARNING,
                "Statistics missing",
                "No statistics file found - genome analysis incomplete",
                true
            ));
        }
        
        // Check extracted sequences directory
        File extractedDir = new File(species.getSpeciesDir(), "extracted");
        if (!extractedDir.exists()) {
            warnings.add("No extracted sequences found - consider running sequence extraction");
        }
        
        // Validate species metadata
        if (species.getSpeciesName() == null || species.getSpeciesName().trim().isEmpty()) {
            issues.add(new ValidationIssue(
                ValidationIssue.Category.DATA_INTEGRITY,
                ValidationIssue.Severity.ERROR,
                "Species name missing",
                "Species name is required but not set",
                false
            ));
            valid = false;
        }
        
        if (species.getVersion() == null || species.getVersion().trim().isEmpty()) {
            issues.add(new ValidationIssue(
                ValidationIssue.Category.DATA_INTEGRITY,
                ValidationIssue.Severity.WARNING,
                "Version missing",
                "Species version is not set",
                true
            ));
        }
        
        return valid;
    }
    
    /**
     * Check performance issues
     */
    private static void validatePerformanceIssues(SpeciesInfo species, List<ValidationIssue> issues, List<String> warnings) {
        
        // Check file sizes
        File genomeFile = findGenomeFile(species);
        if (genomeFile != null) {
            long fileSizeMB = genomeFile.length() / (1024 * 1024);
            
            if (fileSizeMB > 1000) { // > 1GB
                issues.add(new ValidationIssue(
                    ValidationIssue.Category.PERFORMANCE,
                    ValidationIssue.Severity.INFO,
                    "Large genome file",
                    String.format("Genome file is %d MB - consider performance optimization", fileSizeMB),
                    false
                ));
            }
        }
        
        // Check for excessive file count
        if (species.getSpeciesDir() != null) {
            File[] allFiles = species.getSpeciesDir().listFiles();
            if (allFiles != null && allFiles.length > 100) {
                warnings.add("Species directory contains many files (" + allFiles.length + 
                           ") - consider cleanup");
            }
        }
    }
    
    /**
     * Find genome file in species
     */
    private static File findGenomeFile(SpeciesInfo species) {
        if (species.getSequenceDir() == null) return null;
        
        File[] genomeFiles = species.getSequenceDir().listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".fa") || 
            name.toLowerCase().endsWith(".fasta") ||
            name.toLowerCase().endsWith(".fas"));
        
        return (genomeFiles != null && genomeFiles.length > 0) ? genomeFiles[0] : null;
    }
    
    /**
     * Find annotation file in species
     */
    private static File findAnnotationFile(SpeciesInfo species) {
        if (species.getAnnotationDir() == null) return null;
        
        File[] annotationFiles = species.getAnnotationDir().listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".gff") || 
            name.toLowerCase().endsWith(".gff3") ||
            name.toLowerCase().endsWith(".gtf"));
        
        return (annotationFiles != null && annotationFiles.length > 0) ? annotationFiles[0] : null;
    }
    
    /**
     * Generate validation summary report
     */
    public static String generateValidationReport(List<ValidationResult> results) {
        StringBuilder report = new StringBuilder();
        
        int totalSpecies = results.size();
        int validSpecies = (int) results.stream().filter(ValidationResult::isValid).count();
        int errorsTotal = results.stream().mapToInt(ValidationResult::getErrorCount).sum();
        int warningsTotal = results.stream().mapToInt(ValidationResult::getWarningCount).sum();
        
        report.append("Validation Report Summary\n");
        report.append("========================\n\n");
        report.append(String.format("Total species validated: %d\n", totalSpecies));
        report.append(String.format("Valid species: %d (%.1f%%)\n", validSpecies, 
            totalSpecies > 0 ? (validSpecies * 100.0) / totalSpecies : 0));
        report.append(String.format("Total errors: %d\n", errorsTotal));
        report.append(String.format("Total warnings: %d\n\n", warningsTotal));
        
        // Species with issues
        if (validSpecies < totalSpecies) {
            report.append("Species with Issues:\n");
            report.append("-------------------\n");
            
            for (ValidationResult result : results) {
                if (!result.isValid()) {
                    report.append(String.format("• %s - %d errors, %d warnings\n",
                        result.getSpecies().getDisplayName(),
                        result.getErrorCount(),
                        result.getWarningCount()));
                }
            }
        }
        
        return report.toString();
    }
}