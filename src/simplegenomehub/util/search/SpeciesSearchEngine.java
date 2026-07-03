/*
 * Species Search Engine
 */
package simplegenomehub.util.search;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.GenomeData;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.logging.Logger;

/**
 * Search engine for filtering species collections based on multiple criteria
 * Supports text search, numerical ranges, file existence, and date filters
 * 
 * @author SimpleGenomeHub
 */
public class SpeciesSearchEngine {
    
    private static final Logger logger = Logger.getLogger(SpeciesSearchEngine.class.getName());
    
    /**
     * Search species collection based on criteria
     */
    public static List<SpeciesInfo> search(List<SpeciesInfo> allSpecies, SearchCriteria criteria) {
        if (criteria == null || !criteria.hasAnyCriteria()) {
            return new ArrayList<>(allSpecies);
        }
        
        List<SpeciesInfo> results = new ArrayList<>();
        
        for (SpeciesInfo species : allSpecies) {
            if (matchesCriteria(species, criteria)) {
                results.add(species);
            }
        }
        
        logger.info("Search completed: " + results.size() + " of " + allSpecies.size() + " species match criteria");
        return results;
    }
    
    /**
     * Check if a species matches the search criteria
     */
    private static boolean matchesCriteria(SpeciesInfo species, SearchCriteria criteria) {
        
        // Text pattern matching
        if (!matchesTextCriteria(species, criteria)) {
            return false;
        }
        
        // File existence filters
        if (!matchesFileExistenceCriteria(species, criteria)) {
            return false;
        }
        
        // Numerical range filters
        if (!matchesNumericalCriteria(species, criteria)) {
            return false;
        }
        
        // Date filters
        if (!matchesDateCriteria(species, criteria)) {
            return false;
        }
        
        // Assembly and annotation filters
        if (!matchesMetadataCriteria(species, criteria)) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Match text criteria
     */
    private static boolean matchesTextCriteria(SpeciesInfo species, SearchCriteria criteria) {
        
        // Species name pattern
        if (criteria.getSpeciesNamePattern() != null) {
            if (!matchesTextPattern(species.getSpeciesName(), criteria.getSpeciesNamePattern(), criteria)) {
                return false;
            }
        }
        
        // Version pattern
        if (criteria.getVersionPattern() != null) {
            if (!matchesTextPattern(species.getVersion(), criteria.getVersionPattern(), criteria)) {
                return false;
            }
        }
        
        // Notes pattern
        if (criteria.getNotesPattern() != null) {
            String notes = species.getNotes() != null ? species.getNotes() : "";
            if (!matchesTextPattern(notes, criteria.getNotesPattern(), criteria)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Match file existence criteria
     */
    private static boolean matchesFileExistenceCriteria(SpeciesInfo species, SearchCriteria criteria) {
        
        // Has genome file
        if (criteria.getHasGenomeFile() != null) {
            boolean hasGenome = species.hasGenomeFiles();
            if (criteria.getHasGenomeFile() != hasGenome) {
                return false;
            }
        }
        
        // Has annotation file
        if (criteria.getHasAnnotationFile() != null) {
            boolean hasAnnotation = species.hasAnnotationFiles();
            if (criteria.getHasAnnotationFile() != hasAnnotation) {
                return false;
            }
        }
        
        // Has extracted sequences
        if (criteria.getHasExtractedSequences() != null) {
            boolean hasExtracted = hasExtractedSequences(species);
            if (criteria.getHasExtractedSequences() != hasExtracted) {
                return false;
            }
        }
        
        // Has statistics
        if (criteria.getHasStatistics() != null) {
            boolean hasStats = species.getGenomeData() != null;
            if (criteria.getHasStatistics() != hasStats) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Match numerical criteria
     */
    private static boolean matchesNumericalCriteria(SpeciesInfo species, SearchCriteria criteria) {
        GenomeData data = species.getGenomeData();
        
        // Skip numerical filters if no genome data available
        if (data == null) {
            // If any numerical criteria are specified and we don't have data, exclude this species
            return criteria.getMinGenomeSize() == null && criteria.getMaxGenomeSize() == null &&
                   criteria.getMinGeneCount() == null && criteria.getMaxGeneCount() == null &&
                   criteria.getMinGcContent() == null && criteria.getMaxGcContent() == null;
        }
        
        // Genome size range
        if (criteria.getMinGenomeSize() != null || criteria.getMaxGenomeSize() != null) {
            long genomeSize = data.getGenomeSize();
            
            if (criteria.getMinGenomeSize() != null && genomeSize < criteria.getMinGenomeSize()) {
                return false;
            }
            if (criteria.getMaxGenomeSize() != null && genomeSize > criteria.getMaxGenomeSize()) {
                return false;
            }
        }
        
        // Gene count range
        if (criteria.getMinGeneCount() != null || criteria.getMaxGeneCount() != null) {
            int geneCount = data.getGeneCount();
            
            if (criteria.getMinGeneCount() != null && geneCount < criteria.getMinGeneCount()) {
                return false;
            }
            if (criteria.getMaxGeneCount() != null && geneCount > criteria.getMaxGeneCount()) {
                return false;
            }
        }
        
        // GC content range
        if (criteria.getMinGcContent() != null || criteria.getMaxGcContent() != null) {
            double gcContent = data.getGcContent();
            
            if (criteria.getMinGcContent() != null && gcContent < criteria.getMinGcContent()) {
                return false;
            }
            if (criteria.getMaxGcContent() != null && gcContent > criteria.getMaxGcContent()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Match date criteria
     */
    private static boolean matchesDateCriteria(SpeciesInfo species, SearchCriteria criteria) {
        
        if (criteria.getImportedAfter() != null || criteria.getImportedBefore() != null) {
            if (species.getImportTime() == null) {
                return false; // Exclude species without import time if date filtering is requested
            }
            
            java.util.Date importDate = java.sql.Timestamp.valueOf(species.getImportTime());
            
            if (criteria.getImportedAfter() != null && importDate.before(criteria.getImportedAfter())) {
                return false;
            }
            if (criteria.getImportedBefore() != null && importDate.after(criteria.getImportedBefore())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Match metadata criteria
     */
    private static boolean matchesMetadataCriteria(SpeciesInfo species, SearchCriteria criteria) {
        GenomeData data = species.getGenomeData();
        
        // Assembly level
        if (criteria.getAssemblyLevel() != null) {
            if (data == null || !criteria.getAssemblyLevel().equals(data.getAssemblyLevel())) {
                return false;
            }
        }
        
        // Annotation source
        if (criteria.getAnnotationSource() != null) {
            if (data == null || !criteria.getAnnotationSource().equals(data.getAnnotationSource())) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if text matches pattern
     */
    private static boolean matchesTextPattern(String text, String pattern, SearchCriteria criteria) {
        if (text == null) {
            text = "";
        }
        
        if (criteria.isUseRegex()) {
            try {
                Pattern p = criteria.isCaseSensitive() ? 
                    Pattern.compile(pattern) : 
                    Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                return p.matcher(text).find();
            } catch (PatternSyntaxException e) {
                logger.warning("Invalid regex pattern: " + pattern + " - " + e.getMessage());
                return false;
            }
        } else {
            // Simple text matching
            String searchText = criteria.isCaseSensitive() ? text : text.toLowerCase();
            String searchPattern = criteria.isCaseSensitive() ? pattern : pattern.toLowerCase();
            return searchText.contains(searchPattern);
        }
    }
    
    /**
     * Check if species has extracted sequences
     */
    private static boolean hasExtractedSequences(SpeciesInfo species) {
        if (species.getSpeciesDir() == null) {
            return false;
        }
        
        File extractedDir = new File(species.getSpeciesDir(), "extracted");
        if (!extractedDir.exists() || !extractedDir.isDirectory()) {
            return false;
        }
        
        File[] fastaFiles = extractedDir.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".fasta") || 
            name.toLowerCase().endsWith(".fa") ||
            name.toLowerCase().endsWith(".fas"));
        
        return fastaFiles != null && fastaFiles.length > 0;
    }
    
    /**
     * Get search statistics
     */
    public static SearchStatistics getSearchStatistics(List<SpeciesInfo> allSpecies, List<SpeciesInfo> filteredSpecies) {
        return new SearchStatistics(allSpecies, filteredSpecies);
    }
    
    /**
     * Search statistics class
     */
    public static class SearchStatistics {
        private final int totalSpecies;
        private final int matchingSpecies;
        private final int speciesWithGenome;
        private final int speciesWithAnnotation;
        private final int speciesWithStatistics;
        private final int speciesWithExtracted;
        
        public SearchStatistics(List<SpeciesInfo> allSpecies, List<SpeciesInfo> filteredSpecies) {
            this.totalSpecies = allSpecies.size();
            this.matchingSpecies = filteredSpecies.size();
            
            int withGenome = 0, withAnnotation = 0, withStatistics = 0, withExtracted = 0;
            
            for (SpeciesInfo species : filteredSpecies) {
                if (species.hasGenomeFiles()) withGenome++;
                if (species.hasAnnotationFiles()) withAnnotation++;
                if (species.getGenomeData() != null) withStatistics++;
                if (hasExtractedSequences(species)) withExtracted++;
            }
            
            this.speciesWithGenome = withGenome;
            this.speciesWithAnnotation = withAnnotation;
            this.speciesWithStatistics = withStatistics;
            this.speciesWithExtracted = withExtracted;
        }
        
        public int getTotalSpecies() { return totalSpecies; }
        public int getMatchingSpecies() { return matchingSpecies; }
        public int getSpeciesWithGenome() { return speciesWithGenome; }
        public int getSpeciesWithAnnotation() { return speciesWithAnnotation; }
        public int getSpeciesWithStatistics() { return speciesWithStatistics; }
        public int getSpeciesWithExtracted() { return speciesWithExtracted; }
        
        public double getMatchPercentage() {
            return totalSpecies > 0 ? (matchingSpecies * 100.0) / totalSpecies : 0.0;
        }
        
        public String getSummary() {
            return String.format("Found %d of %d species (%.1f%%) matching search criteria",
                matchingSpecies, totalSpecies, getMatchPercentage());
        }
    }
}