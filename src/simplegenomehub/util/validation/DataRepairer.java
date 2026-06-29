/*
 * Data Repairer
 */
package simplegenomehub.util.validation;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.GenomeData;
import simplegenomehub.util.fileio.FastaIndexBuilder;
import simplegenomehub.util.fileio.GenomeStatsCalculator;
import simplegenomehub.util.validation.DataValidator.ValidationResult;
import simplegenomehub.util.validation.DataValidator.ValidationIssue;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Data repair utility for fixing common species data issues
 * Provides automated repair for recoverable problems
 * 
 * @author SimpleGenomeHub
 */
public class DataRepairer {
    
    private static final Logger logger = Logger.getLogger(DataRepairer.class.getName());
    
    /**
     * Repair result for tracking what was fixed
     */
    public static class RepairResult {
        private final SpeciesInfo species;
        private final List<String> repairsPerformed;
        private final List<String> repairsFailed;
        private final boolean success;
        
        public RepairResult(SpeciesInfo species, boolean success) {
            this.species = species;
            this.success = success;
            this.repairsPerformed = new ArrayList<>();
            this.repairsFailed = new ArrayList<>();
        }
        
        public SpeciesInfo getSpecies() { return species; }
        public boolean isSuccess() { return success; }
        public List<String> getRepairsPerformed() { return new ArrayList<>(repairsPerformed); }
        public List<String> getRepairsFailed() { return new ArrayList<>(repairsFailed); }
        
        public void addRepair(String repair) { repairsPerformed.add(repair); }
        public void addFailedRepair(String repair) { repairsFailed.add(repair); }
        
        public int getRepairCount() { return repairsPerformed.size(); }
        public int getFailureCount() { return repairsFailed.size(); }
    }
    
    /**
     * Attempt to repair species based on validation results
     */
    public static RepairResult repairSpecies(SpeciesInfo species, ValidationResult validationResult) {
        RepairResult result = new RepairResult(species, true);
        
        logger.info("Attempting to repair species: " + species.getDisplayName());
        
        try {
            // Process each repairable issue
            for (ValidationIssue issue : validationResult.getIssues()) {
                if (issue.isAutoRepairable()) {
                    if (repairIssue(species, issue, result)) {
                        result.addRepair(issue.getMessage());
                        logger.info("Repaired: " + issue.getMessage());
                    } else {
                        result.addFailedRepair(issue.getMessage());
                        logger.warning("Failed to repair: " + issue.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            logger.severe("Repair failed for " + species.getDisplayName() + ": " + e.getMessage());
            result.addFailedRepair("General repair failure: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Repair multiple species
     */
    public static List<RepairResult> repairMultipleSpecies(List<SpeciesInfo> speciesList, 
                                                          List<ValidationResult> validationResults) {
        List<RepairResult> results = new ArrayList<>();
        
        for (int i = 0; i < speciesList.size() && i < validationResults.size(); i++) {
            results.add(repairSpecies(speciesList.get(i), validationResults.get(i)));
        }
        
        return results;
    }
    
    /**
     * Repair a specific issue
     */
    private static boolean repairIssue(SpeciesInfo species, ValidationIssue issue, RepairResult result) {
        switch (issue.getCategory()) {
            case FILE_MISSING:
                return repairMissingFile(species, issue);
            case PERFORMANCE:
                return repairPerformanceIssue(species, issue);
            case DATA_INTEGRITY:
                return repairDataIntegrityIssue(species, issue);
            default:
                return false;
        }
    }
    
    /**
     * Repair missing file issues
     */
    private static boolean repairMissingFile(SpeciesInfo species, ValidationIssue issue) {
        try {
            String message = issue.getMessage().toLowerCase();
            
            // Create missing directories
            if (message.contains("directory missing")) {
                if (message.contains("sequence")) {
                    File sequenceDir = species.getSequenceDir();
                    if (sequenceDir != null && !sequenceDir.exists()) {
                        return sequenceDir.mkdirs();
                    }
                } else if (message.contains("annotation")) {
                    File annotationDir = species.getAnnotationDir();
                    if (annotationDir != null && !annotationDir.exists()) {
                        return annotationDir.mkdirs();
                    }
                } else if (message.contains("species directory")) {
                    File speciesDir = species.getSpeciesDir();
                    if (speciesDir != null && !speciesDir.exists()) {
                        return speciesDir.mkdirs();
                    }
                }
            }
            
            return false;
            
        } catch (Exception e) {
            logger.warning("Failed to repair missing file issue: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Repair performance issues
     */
    private static boolean repairPerformanceIssue(SpeciesInfo species, ValidationIssue issue) {
        try {
            String message = issue.getMessage().toLowerCase();
            
            // Create missing FASTA index
            if (message.contains("fasta index missing")) {
                File genomeFile = findGenomeFile(species);
                if (genomeFile != null) {
                    return FastaIndexBuilder.buildIndex(genomeFile);
                }
            }
            
            return false;
            
        } catch (Exception e) {
            logger.warning("Failed to repair performance issue: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Repair data integrity issues
     */
    private static boolean repairDataIntegrityIssue(SpeciesInfo species, ValidationIssue issue) {
        try {
            String message = issue.getMessage().toLowerCase();
            
            // Regenerate statistics
            if (message.contains("statistics")) {
                File genomeFile = findGenomeFile(species);
                File annotationFile = findAnnotationFile(species);
                
                if (genomeFile != null && annotationFile != null) {
                    GenomeData newStats = GenomeStatsCalculator.calculateGenomeStats(genomeFile, annotationFile);
                    if (newStats != null) {
                        species.setGenomeData(newStats);
                        if (species.getStatsFile() != null) {
                            newStats.saveToFile(species.getStatsFile());
                        }
                        return true;
                    }
                }
            }
            
            // Set default version if missing
            if (message.contains("version missing")) {
                if (species.getVersion() == null || species.getVersion().trim().isEmpty()) {
                    species.setVersion("1.0");
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            logger.warning("Failed to repair data integrity issue: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Comprehensive repair - fixes all auto-repairable issues
     */
    public static RepairResult performComprehensiveRepair(SpeciesInfo species) {
        logger.info("Performing comprehensive repair for: " + species.getDisplayName());
        
        RepairResult result = new RepairResult(species, true);
        
        try {
            // 1. Ensure directory structure exists
            if (ensureDirectoryStructure(species)) {
                result.addRepair("Created missing directory structure");
            }
            
            // 2. Build FASTA index if missing
            File genomeFile = findGenomeFile(species);
            if (genomeFile != null) {
                File indexFile = new File(genomeFile.getAbsolutePath() + ".fai");
                if (!indexFile.exists()) {
                    if (FastaIndexBuilder.buildIndex(genomeFile)) {
                        result.addRepair("Created FASTA index");
                    } else {
                        result.addFailedRepair("Failed to create FASTA index");
                    }
                }
            }
            
            // 3. Generate statistics if missing or corrupted
            if (regenerateStatistics(species)) {
                result.addRepair("Regenerated genome statistics");
            }
            
            // 4. Set default values for missing metadata
            if (repairMetadata(species)) {
                result.addRepair("Fixed missing metadata");
            }
            
            // 5. Clean up temporary files
            if (cleanupTemporaryFiles(species)) {
                result.addRepair("Cleaned up temporary files");
            }
            
        } catch (Exception e) {
            logger.severe("Comprehensive repair failed: " + e.getMessage());
            result.addFailedRepair("Comprehensive repair failed: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Ensure directory structure exists
     */
    private static boolean ensureDirectoryStructure(SpeciesInfo species) {
        boolean created = false;
        
        if (species.getSpeciesDir() != null && !species.getSpeciesDir().exists()) {
            if (species.getSpeciesDir().mkdirs()) {
                created = true;
            }
        }
        
        if (species.getSequenceDir() != null && !species.getSequenceDir().exists()) {
            if (species.getSequenceDir().mkdirs()) {
                created = true;
            }
        }
        
        if (species.getAnnotationDir() != null && !species.getAnnotationDir().exists()) {
            if (species.getAnnotationDir().mkdirs()) {
                created = true;
            }
        }
        
        return created;
    }
    
    /**
     * Regenerate statistics
     */
    private static boolean regenerateStatistics(SpeciesInfo species) {
        try {
            File genomeFile = findGenomeFile(species);
            File annotationFile = findAnnotationFile(species);
            
            if (genomeFile != null && annotationFile != null) {
                // Check if statistics need regeneration
                boolean needsRegeneration = false;
                
                if (species.getGenomeData() == null) {
                    needsRegeneration = true;
                } else if (species.getStatsFile() != null && !species.getStatsFile().exists()) {
                    needsRegeneration = true;
                } else if (species.getStatsFile() != null) {
                    // Check if stats file is older than genome/annotation files
                    long statsModified = species.getStatsFile().lastModified();
                    long genomeModified = genomeFile.lastModified();
                    long annotationModified = annotationFile.lastModified();
                    
                    if (statsModified < genomeModified || statsModified < annotationModified) {
                        needsRegeneration = true;
                    }
                }
                
                if (needsRegeneration) {
                    GenomeData newStats = GenomeStatsCalculator.calculateGenomeStats(genomeFile, annotationFile);
                    if (newStats != null) {
                        species.setGenomeData(newStats);
                        if (species.getStatsFile() != null) {
                            newStats.saveToFile(species.getStatsFile());
                        }
                        return true;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warning("Failed to regenerate statistics: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Repair metadata
     */
    private static boolean repairMetadata(SpeciesInfo species) {
        boolean repaired = false;
        
        // Set default version if missing
        if (species.getVersion() == null || species.getVersion().trim().isEmpty()) {
            species.setVersion("1.0");
            repaired = true;
        }
        
        // Set import time if missing
        if (species.getImportTime() == null) {
            species.setImportTime(java.time.LocalDateTime.now());
            repaired = true;
        }
        
        return repaired;
    }
    
    /**
     * Clean up temporary files
     */
    private static boolean cleanupTemporaryFiles(SpeciesInfo species) {
        boolean cleaned = false;
        
        if (species.getSpeciesDir() != null && species.getSpeciesDir().exists()) {
            // Clean up common temporary file patterns
            File[] tempFiles = species.getSpeciesDir().listFiles((dir, name) -> 
                name.startsWith(".") || 
                name.endsWith(".tmp") || 
                name.endsWith(".temp") ||
                name.equals("Thumbs.db") ||
                name.equals(".DS_Store"));
            
            if (tempFiles != null) {
                for (File tempFile : tempFiles) {
                    if (tempFile.delete()) {
                        cleaned = true;
                    }
                }
            }
        }
        
        return cleaned;
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
     * Generate repair summary report
     */
    public static String generateRepairReport(List<RepairResult> results) {
        StringBuilder report = new StringBuilder();
        
        int totalSpecies = results.size();
        int repairedSpecies = (int) results.stream().filter(r -> r.getRepairCount() > 0).count();
        int totalRepairs = results.stream().mapToInt(RepairResult::getRepairCount).sum();
        int totalFailures = results.stream().mapToInt(RepairResult::getFailureCount).sum();
        
        report.append("Data Repair Report\n");
        report.append("==================\n\n");
        report.append(String.format("Total species processed: %d\n", totalSpecies));
        report.append(String.format("Species with repairs: %d\n", repairedSpecies));
        report.append(String.format("Successful repairs: %d\n", totalRepairs));
        report.append(String.format("Failed repairs: %d\n\n", totalFailures));
        
        // Detail successful repairs
        if (totalRepairs > 0) {
            report.append("Repair Details:\n");
            report.append("--------------\n");
            
            for (RepairResult result : results) {
                if (result.getRepairCount() > 0) {
                    report.append(String.format("• %s: %d repairs performed\n",
                        result.getSpecies().getDisplayName(),
                        result.getRepairCount()));
                    
                    for (String repair : result.getRepairsPerformed()) {
                        report.append(String.format("  - %s\n", repair));
                    }
                }
            }
        }
        
        // Detail failed repairs
        if (totalFailures > 0) {
            report.append("\nFailed Repairs:\n");
            report.append("---------------\n");
            
            for (RepairResult result : results) {
                if (result.getFailureCount() > 0) {
                    report.append(String.format("• %s: %d repairs failed\n",
                        result.getSpecies().getDisplayName(),
                        result.getFailureCount()));
                    
                    for (String failure : result.getRepairsFailed()) {
                        report.append(String.format("  - %s\n", failure));
                    }
                }
            }
        }
        
        return report.toString();
    }
}