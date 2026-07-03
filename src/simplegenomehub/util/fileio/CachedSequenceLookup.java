/*
 * Cached Sequence Lookup Engine
 */
package simplegenomehub.util.fileio;

import simplegenomehub.model.SpeciesInfo;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Engine for looking up sequences using cached FASTA files first,
 * fallback to real-time extraction from genome and annotation files
 * 
 * @author SimpleGenomeHub
 */
public class CachedSequenceLookup {
    
    private static final Logger logger = Logger.getLogger(CachedSequenceLookup.class.getName());
    
    private SpeciesInfo species;
    private Map<String, String> transcriptCache;
    private Map<String, String> cdsCache;
    private Map<String, String> proteinCache;
    private boolean cacheAvailable;
    
    // Complete sequence caches for gene queries
    private Map<String, String> allTranscriptCache;
    private Map<String, String> allCdsCache;
    private Map<String, String> allProteinCache;
    
    // Gene ID to transcript IDs mapping for efficient lookup
    private Map<String, List<String>> geneToTranscriptsMap;
    private boolean completeCacheGenerationAttempted;
    
    /**
     * Constructor
     */
    public CachedSequenceLookup(SpeciesInfo species) {
        this.species = species;
        loadCacheFiles();
    }
    
    /**
     * Load cached sequence files into memory
     */
    private void loadCacheFiles() {
        try {
            String prefix = species.getSpeciesDirectoryName();
            File sequenceDir = species.getSequenceDir();
            
            // Load representative transcript cache
            File transcriptFile = new File(sequenceDir, prefix + ".transcripts.fasta");
            transcriptCache = loadFastaSequences(transcriptFile);
            
            // Load representative CDS cache
            File cdsFile = new File(sequenceDir, prefix + ".cds.fasta");
            cdsCache = loadFastaSequences(cdsFile);
            
            // Load representative protein cache
            File proteinFile = new File(sequenceDir, prefix + ".proteins.fasta");
            proteinCache = loadFastaSequences(proteinFile);
            
            // Load complete sequence caches for gene queries
            File allTranscriptFile = new File(sequenceDir, prefix + ".all_transcripts.fasta");
            allTranscriptCache = loadFastaSequences(allTranscriptFile);
            
            File allCdsFile = new File(sequenceDir, prefix + ".all_cds.fasta");
            allCdsCache = loadFastaSequences(allCdsFile);
            
            File allProteinFile = new File(sequenceDir, prefix + ".all_proteins.fasta");
            allProteinCache = loadFastaSequences(allProteinFile);
            
            // Build gene-to-transcripts mapping from all transcript cache
            buildGeneToTranscriptsMapping();
            
            // Cache is available if at least one representative or complete cache exists
            cacheAvailable = !transcriptCache.isEmpty() || !cdsCache.isEmpty() || !proteinCache.isEmpty()
                || !allTranscriptCache.isEmpty() || !allCdsCache.isEmpty() || !allProteinCache.isEmpty();
            
            if (cacheAvailable) {
                logger.info("Loaded cached sequences: " + 
                           transcriptCache.size() + " transcripts, " +
                           cdsCache.size() + " CDS, " +
                           proteinCache.size() + " proteins");
                if (!allTranscriptCache.isEmpty()) {
                    logger.info("Loaded complete sequences: " + 
                               allTranscriptCache.size() + " all transcripts, " +
                               allCdsCache.size() + " all CDS, " +
                               allProteinCache.size() + " all proteins");
                }
            } else {
                logger.info("No cached sequence files found, using real-time mode");
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error loading cache files", e);
            cacheAvailable = false;
            transcriptCache = new HashMap<>();
            cdsCache = new HashMap<>();
            proteinCache = new HashMap<>();
            allTranscriptCache = new HashMap<>();
            allCdsCache = new HashMap<>();
            allProteinCache = new HashMap<>();
            geneToTranscriptsMap = new HashMap<>();
        }
    }
    
    /**
     * Load sequences from a FASTA file into a map
     */
    private Map<String, String> loadFastaSequences(File fastaFile) {
        Map<String, String> sequences = new HashMap<>();
        
        if (!fastaFile.exists() || !fastaFile.canRead()) {
            return sequences;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(fastaFile))) {
            String line;
            StringBuilder currentSequence = new StringBuilder();
            String currentId = null;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (line.startsWith(">")) {
                    // Save previous sequence
                    if (currentId != null && currentSequence.length() > 0) {
                        sequences.put(currentId, currentSequence.toString());
                    }
                    
                    // Extract ID from header
                    currentId = extractSequenceId(line);
                    currentSequence = new StringBuilder();
                    
                } else {
                    currentSequence.append(line);
                }
            }
            
            // Save last sequence
            if (currentId != null && currentSequence.length() > 0) {
                sequences.put(currentId, currentSequence.toString());
            }
            
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error reading FASTA file: " + fastaFile.getName(), e);
        }
        
        return sequences;
    }
    
    /**
     * Extract sequence ID from FASTA header
     */
    private String extractSequenceId(String header) {
        // Remove '>' and get the first part before space or '[' 
        String id = header.substring(1).split("\\s+")[0].split("\\[")[0];
        
        // Remove common suffixes
        if (id.endsWith("_transcript") || id.endsWith("_cds") || id.endsWith("_protein")) {
            int lastUnder = id.lastIndexOf('_');
            if (lastUnder > 0) {
                id = id.substring(0, lastUnder);
            }
        }
        
        return id;
    }
    
    /**
     * Build gene-to-transcripts mapping from transcript cache
     */
    private void buildGeneToTranscriptsMapping() {
        geneToTranscriptsMap = new HashMap<>();
        
        // Use all transcript cache if available, otherwise use representative cache
        Map<String, String> sourceCache = allTranscriptCache.isEmpty() ? transcriptCache : allTranscriptCache;
        
        for (String transcriptId : sourceCache.keySet()) {
            String geneId = extractGeneIdFromTranscriptId(transcriptId);
            if (geneId != null && !geneId.equals(transcriptId)) {
                geneToTranscriptsMap.computeIfAbsent(geneId, k -> new ArrayList<>()).add(transcriptId);
            }
        }
        
        // Sort transcript lists for consistent output
        for (List<String> transcripts : geneToTranscriptsMap.values()) {
            transcripts.sort(String::compareTo);
        }
        
        logger.info("Built gene-to-transcripts mapping: " + geneToTranscriptsMap.size() + " genes mapped");
    }
    
    /**
     * Extract gene ID from transcript ID using consistent patterns
     */
    private String extractGeneIdFromTranscriptId(String transcriptId) {
        if (transcriptId == null || transcriptId.isEmpty()) {
            return null;
        }
        
        String geneId = transcriptId.trim();

        // Common patterns:
        // AT1G01010.1 -> AT1G01010
        // gene_12345.t1 -> gene_12345
        // Gene1-T001 -> Gene1
        geneId = geneId.replaceFirst("\\.[0-9]+$", "");
        geneId = geneId.replaceFirst("\\.[Tt][0-9]+$", "");
        geneId = geneId.replaceFirst("_[Tt][0-9]+$", "");
        geneId = geneId.replaceFirst("-[Tt][0-9]+$", "");
        geneId = geneId.replaceFirst("(?i)_transcript_[0-9]+$", "");
        geneId = geneId.replaceFirst("(?i)-mrna-[0-9]+$", "");

        // Only return if different from original transcript ID
        return geneId.equals(transcriptId.trim()) ? null : geneId;
    }
    
    /**
     * Check if cache is available
     */
    public boolean isCacheAvailable() {
        return cacheAvailable;
    }
    
    /**
     * Lookup sequences for a given gene or transcript ID
     */
    public LookupResult lookupSequences(String searchId) {
        LookupResult result = new LookupResult();
        result.setSearchId(searchId);
        
        // If cache is not available, generate it first
        if (!cacheAvailable) {
            if (generateCacheFiles()) {
                loadCacheFiles(); // Reload after generation
            } else {
                result.setSuccess(false);
                result.setErrorMessage("Failed to generate cache files");
                return result;
            }
        }

        ensureCompleteCachesAvailable();

        // Exact transcript IDs in GeneSet should resolve from complete caches first,
        // instead of falling back to a representative transcript of the same gene.
        if (searchExactFromAvailableCaches(searchId, result)) {
            result.setFromCache(true);
            return result;
        }
        
        // Search from cache
        if (searchFromCache(searchId, result)) {
            result.setFromCache(true);
            return result;
        }
        
        result.setSuccess(false);
        result.setErrorMessage("Sequence not found");
        result.setFromCache(true);
        
        return result;
    }
    
    /**
     * Lookup sequences for multiple IDs (batch query)
     */
    public List<LookupResult> lookupSequencesBatch(String[] searchIds) {
        List<LookupResult> results = new ArrayList<>();
        
        for (String searchId : searchIds) {
            if (searchId != null && !searchId.trim().isEmpty()) {
                LookupResult result = lookupSequences(searchId.trim());
                results.add(result);
            }
        }
        
        return results;
    }
    
    /**
     * Enhanced lookup that returns all transcripts for a gene ID
     */
    public LookupResult lookupSequencesEnhanced(String searchId) {
        LookupResult result = new LookupResult();
        result.setSearchId(searchId);
        
        // If cache is not available, generate it first
        if (!cacheAvailable) {
            if (generateCacheFiles()) {
                loadCacheFiles(); // Reload after generation
            } else {
                result.setSuccess(false);
                result.setErrorMessage("Failed to generate cache files");
                return result;
            }
        }

        ensureCompleteCachesAvailable();
        
        // Check if this is a gene ID by looking it up in the gene-to-transcripts mapping
        List<String> geneTranscripts = geneToTranscriptsMap.get(searchId);
        boolean isGeneId = (geneTranscripts != null && !geneTranscripts.isEmpty());
        result.setGeneQuery(isGeneId);
        
        if (isGeneId) {
            // Use the pre-built mapping to get all transcripts for this gene
            
            if (!geneTranscripts.isEmpty()) {
                result.setTotalTranscripts(geneTranscripts.size());
                
                // Get sequences for all transcripts (use complete caches for gene queries)
                for (String transcriptId : geneTranscripts) {
                    SequenceEntry entry = new SequenceEntry(transcriptId);
                    
                    // Use complete sequence caches if available, otherwise fall back to representative
                    Map<String, String> useTranscriptCache = allTranscriptCache.isEmpty() ? transcriptCache : allTranscriptCache;
                    Map<String, String> useCdsCache = allCdsCache.isEmpty() ? cdsCache : allCdsCache;
                    Map<String, String> useProteinCache = allProteinCache.isEmpty() ? proteinCache : allProteinCache;
                    
                    String transcriptSeq = findSequenceInCache(transcriptId, useTranscriptCache, "transcript");
                    String cdsSeq = findSequenceInCache(transcriptId, useCdsCache, "cds");
                    String proteinSeq = findSequenceInCache(transcriptId, useProteinCache, "protein");

                    if (transcriptSeq != null) entry.setTranscriptSequence(transcriptSeq);
                    if (cdsSeq != null) entry.setCdsSequence(cdsSeq);
                    if (proteinSeq != null) entry.setProteinSequence(proteinSeq);
                    
                    result.addSequenceEntry(entry);
                }
                
                // Also set the traditional fields for backward compatibility (first transcript)
                if (!result.getAllSequences().isEmpty()) {
                    SequenceEntry first = result.getAllSequences().get(0);
                    result.setTranscriptSequence(first.getTranscriptSequence());
                    result.setCdsSequence(first.getCdsSequence());
                    result.setProteinSequence(first.getProteinSequence());
                    result.setFoundId(searchId + " (" + geneTranscripts.size() + " transcripts)");
                }
                
                result.setSuccess(true);
                result.setFromCache(true);
                return result;
            }
        }
        
        // Fall back to single sequence lookup with exact transcript handling.
        return lookupSequences(searchId);
    }
    
    
    /**
     * Search sequences from cache
     */
    private boolean searchFromCache(String searchId, LookupResult result) {
        boolean found = false;
        
        // Try direct lookup first
        String transcriptSeq = findSequenceInCache(searchId, transcriptCache, "transcript");
        String cdsSeq = findSequenceInCache(searchId, cdsCache, "cds");
        String proteinSeq = findSequenceInCache(searchId, proteinCache, "protein");
        
        if (transcriptSeq != null || cdsSeq != null || proteinSeq != null) {
            result.setTranscriptSequence(transcriptSeq != null ? transcriptSeq : "");
            result.setCdsSequence(cdsSeq != null ? cdsSeq : "");
            result.setProteinSequence(proteinSeq != null ? proteinSeq : "");
            result.setFoundId(searchId);
            result.setSuccess(true);
            found = true;
        } else {
            // Try fuzzy matching
            List<String> suggestions = performFuzzySearch(searchId);
            if (!suggestions.isEmpty()) {
                result.setSuggestedIds(suggestions);
                // Try first suggestion
                String firstSuggestion = suggestions.get(0);
                transcriptSeq = findSequenceInCache(firstSuggestion, transcriptCache, "transcript");
                cdsSeq = findSequenceInCache(firstSuggestion, cdsCache, "cds");
                proteinSeq = findSequenceInCache(firstSuggestion, proteinCache, "protein");
                
                if (transcriptSeq != null || cdsSeq != null || proteinSeq != null) {
                    result.setTranscriptSequence(transcriptSeq != null ? transcriptSeq : "");
                    result.setCdsSequence(cdsSeq != null ? cdsSeq : "");
                    result.setProteinSequence(proteinSeq != null ? proteinSeq : "");
                    result.setFoundId(firstSuggestion + " (similar to " + searchId + ")");
                    result.setSuccess(true);
                    found = true;
                }
            }
        }
        
        return found;
    }

    /**
     * Search exact IDs from complete caches first, then representative caches.
     */
    private boolean searchExactFromAvailableCaches(String searchId, LookupResult result) {
        if (searchId == null || searchId.trim().isEmpty()) {
            return false;
        }

        String transcriptSeq = findExactSequenceInCache(searchId, allTranscriptCache, "transcript");
        if (transcriptSeq == null) {
            transcriptSeq = findExactSequenceInCache(searchId, transcriptCache, "transcript");
        }

        String cdsSeq = findExactSequenceInCache(searchId, allCdsCache, "cds");
        if (cdsSeq == null) {
            cdsSeq = findExactSequenceInCache(searchId, cdsCache, "cds");
        }

        String proteinSeq = findExactSequenceInCache(searchId, allProteinCache, "protein");
        if (proteinSeq == null) {
            proteinSeq = findExactSequenceInCache(searchId, proteinCache, "protein");
        }

        if (transcriptSeq == null && cdsSeq == null && proteinSeq == null) {
            return false;
        }

        result.setTranscriptSequence(transcriptSeq != null ? transcriptSeq : "");
        result.setCdsSequence(cdsSeq != null ? cdsSeq : "");
        result.setProteinSequence(proteinSeq != null ? proteinSeq : "");
        result.setFoundId(searchId);
        result.setSuccess(true);
        return true;
    }

    private String findExactSequenceInCache(String searchId, Map<String, String> cache, String type) {
        if (cache == null || cache.isEmpty()) {
            return null;
        }

        String sequence = cache.get(searchId);
        if (sequence == null) {
            return null;
        }

        return formatSequence(searchId, sequence, type);
    }
    
    /**
     * Find sequence in cache with proper formatting
     */
    private String findSequenceInCache(String searchId, Map<String, String> cache, String type) {
        String sequence = cache.get(searchId);
        if (sequence != null) {
            return formatSequence(searchId, sequence, type);
        }
        
        // Try variations of the ID
        for (String cacheId : cache.keySet()) {
            if (matchesId(searchId, cacheId)) {
                sequence = cache.get(cacheId);
                return formatSequence(cacheId, sequence, type);
            }
        }
        
        return null;
    }

    /**
     * Check if two IDs match (considering variations)
     */
    private boolean matchesId(String searchId, String cacheId) {
        // Exact match
        if (searchId.equals(cacheId)) return true;
        
        // Remove version numbers for comparison
        String searchBase = searchId.contains(".") ? searchId.split("\\.")[0] : searchId;
        String cacheBase = cacheId.contains(".") ? cacheId.split("\\.")[0] : cacheId;
        
        // Base name match
        if (searchBase.equals(cacheBase)) return true;
        
        // Check if one contains the other
        if (searchId.contains(cacheId) || cacheId.contains(searchId)) return true;
        
        return false;
    }
    
    /**
     * Perform fuzzy search for similar IDs
     */
    private List<String> performFuzzySearch(String searchId) {
        Set<String> allIds = new HashSet<>();
        allIds.addAll(transcriptCache.keySet());
        allIds.addAll(cdsCache.keySet());
        allIds.addAll(proteinCache.keySet());
        
        List<String> suggestions = new ArrayList<>();
        String searchLower = searchId.toLowerCase();
        
        for (String id : allIds) {
            String idLower = id.toLowerCase();
            if (idLower.contains(searchLower) || searchLower.contains(idLower)) {
                suggestions.add(id);
            }
        }
        
        // Limit suggestions
        if (suggestions.size() > 5) {
            suggestions = suggestions.subList(0, 5);
        }
        
        return suggestions;
    }

    private void ensureCompleteCachesAvailable() {
        if (completeCacheGenerationAttempted || hasCompleteCaches()) {
            return;
        }

        completeCacheGenerationAttempted = true;
        generateCompleteCacheFiles();
        loadCacheFiles();
    }

    private boolean hasCompleteCaches() {
        return !allTranscriptCache.isEmpty() && !allCdsCache.isEmpty() && !allProteinCache.isEmpty();
    }
    
    /**
     * Format sequence with FASTA header
     */
    private String formatSequence(String id, String sequence, String type) {
        StringBuilder formatted = new StringBuilder();
        formatted.append(">").append(id).append(" [").append(type).append("]\n");
        
        // Break sequence into lines of 80 characters
        for (int i = 0; i < sequence.length(); i += 80) {
            int end = Math.min(i + 80, sequence.length());
            formatted.append(sequence.substring(i, end)).append("\n");
        }
        
        return formatted.toString();
    }
    
    /**
     * Generate cached sequence files if they don't exist
     */
    private boolean generateCacheFiles() {
        try {
            File genomeFile = species.getGenomeFile();
            File annotationFile = species.getAnnotationFile();
            
            if (genomeFile == null || !genomeFile.exists() || 
                annotationFile == null || !annotationFile.exists()) {
                logger.warning("Cannot generate cache files: missing genome or annotation files");
                return false;
            }
            
            String prefix = species.getSpeciesDirectoryName();
            File sequenceDir = species.getSequenceDir();
            boolean success = true;
            
            logger.info("Generating cached sequence files for " + prefix + "...");
            
            // Generate representative transcripts using TBtools
            File transcriptFile = new File(sequenceDir, prefix + ".transcripts.fasta");
            if (!transcriptFile.exists()) {
                TBtoolsSequenceExtractor.ExtractionResult transcriptResult = 
                    TBtoolsSequenceExtractor.extractRepresentativeTranscripts(
                        genomeFile, annotationFile, transcriptFile, "LONGEST_CDS");
                if (!transcriptResult.isSuccess()) {
                    logger.warning("Failed to extract transcript sequences: " + transcriptResult.getMessage());
                    success = false;
                } else {
                    logger.info("Extracted " + transcriptResult.getSequencesExtracted() + " representative transcripts");
                }
            }
            
            // Generate representative CDS sequences using TBtools
            File cdsFile = new File(sequenceDir, prefix + ".cds.fasta");
            if (!cdsFile.exists()) {
                TBtoolsSequenceExtractor.ExtractionResult cdsResult = 
                    TBtoolsSequenceExtractor.extractRepresentativeCDS(
                        genomeFile, annotationFile, cdsFile, "LONGEST_CDS");
                if (!cdsResult.isSuccess()) {
                    logger.warning("Failed to extract CDS sequences: " + cdsResult.getMessage());
                    success = false;
                } else {
                    logger.info("Extracted " + cdsResult.getSequencesExtracted() + " representative CDS sequences");
                }
            }
            
            // Generate representative protein sequences using TBtools
            File proteinFile = new File(sequenceDir, prefix + ".proteins.fasta");
            if (!proteinFile.exists()) {
                TBtoolsSequenceExtractor.ExtractionResult proteinResult = 
                    TBtoolsSequenceExtractor.extractRepresentativeProteins(
                        genomeFile, annotationFile, proteinFile, "LONGEST_CDS");
                if (!proteinResult.isSuccess()) {
                    logger.warning("Failed to extract protein sequences: " + proteinResult.getMessage());
                    success = false;
                } else {
                    logger.info("Extracted " + proteinResult.getSequencesExtracted() + " representative proteins");
                }
            }
            
            if (success) {
                logger.info("Successfully generated cached sequence files");
            }

            generateCompleteCacheFiles();
            
            return success;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error generating cached sequence files", e);
            return false;
        }
    }

    private void generateCompleteCacheFiles() {
        try {
            File genomeFile = species.getGenomeFile();
            File annotationFile = species.getAnnotationFile();

            if (genomeFile == null || !genomeFile.exists()
                || annotationFile == null || !annotationFile.exists()) {
                logger.warning("Cannot generate complete sequence caches: missing genome or annotation files");
                return;
            }

            String prefix = species.getSpeciesDirectoryName();
            File sequenceDir = species.getSequenceDir();

            File allTranscriptFile = new File(sequenceDir, prefix + ".all_transcripts.fasta");
            if (!allTranscriptFile.exists() || allTranscriptCache.isEmpty()) {
                TBtoolsSequenceExtractor.ExtractionResult transcriptResult =
                    TBtoolsSequenceExtractor.extractAllTranscripts(genomeFile, annotationFile, allTranscriptFile);
                if (!transcriptResult.isSuccess()) {
                    logger.warning("Failed to extract all transcript sequences: " + transcriptResult.getMessage());
                } else {
                    logger.info("Extracted " + transcriptResult.getSequencesExtracted() + " complete transcripts");
                }
            }

            File allCdsFile = new File(sequenceDir, prefix + ".all_cds.fasta");
            if (!allCdsFile.exists() || allCdsCache.isEmpty()) {
                TBtoolsSequenceExtractor.ExtractionResult cdsResult =
                    TBtoolsSequenceExtractor.extractAllCDS(genomeFile, annotationFile, allCdsFile);
                if (!cdsResult.isSuccess()) {
                    logger.warning("Failed to extract all CDS sequences: " + cdsResult.getMessage());
                } else {
                    logger.info("Extracted " + cdsResult.getSequencesExtracted() + " complete CDS sequences");
                }
            }

            File allProteinFile = new File(sequenceDir, prefix + ".all_proteins.fasta");
            if (!allProteinFile.exists() || allProteinCache.isEmpty()) {
                TBtoolsSequenceExtractor.ExtractionResult proteinResult =
                    TBtoolsSequenceExtractor.extractAllProteins(genomeFile, annotationFile, allProteinFile);
                if (!proteinResult.isSuccess()) {
                    logger.warning("Failed to extract all protein sequences: " + proteinResult.getMessage());
                } else {
                    logger.info("Extracted " + proteinResult.getSequencesExtracted() + " complete proteins");
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error generating complete cached sequence files", e);
        }
    }
    
    /**
     * Individual sequence entry for a single transcript
     */
    public static class SequenceEntry {
        private String transcriptId;
        private String transcriptSequence = "";
        private String cdsSequence = "";
        private String proteinSequence = "";
        
        public SequenceEntry(String transcriptId) {
            this.transcriptId = transcriptId;
        }
        
        // Getters and setters
        public String getTranscriptId() { return transcriptId; }
        public void setTranscriptId(String transcriptId) { this.transcriptId = transcriptId; }
        public String getTranscriptSequence() { return transcriptSequence; }
        public void setTranscriptSequence(String transcriptSequence) { this.transcriptSequence = transcriptSequence; }
        public String getCdsSequence() { return cdsSequence; }
        public void setCdsSequence(String cdsSequence) { this.cdsSequence = cdsSequence; }
        public String getProteinSequence() { return proteinSequence; }
        public void setProteinSequence(String proteinSequence) { this.proteinSequence = proteinSequence; }
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
        private boolean fromCache = false;
        
        // New fields for multiple sequences
        private List<SequenceEntry> allSequences = new ArrayList<>();
        private boolean isGeneQuery = false;
        private int totalTranscripts = 0;
        
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
        public boolean isFromCache() { return fromCache; }
        public void setFromCache(boolean fromCache) { this.fromCache = fromCache; }
        
        // New getters and setters
        public List<SequenceEntry> getAllSequences() { return allSequences; }
        public void setAllSequences(List<SequenceEntry> allSequences) { this.allSequences = allSequences; }
        public void addSequenceEntry(SequenceEntry entry) { this.allSequences.add(entry); }
        public boolean isGeneQuery() { return isGeneQuery; }
        public void setGeneQuery(boolean geneQuery) { this.isGeneQuery = geneQuery; }
        public int getTotalTranscripts() { return totalTranscripts; }
        public void setTotalTranscripts(int totalTranscripts) { this.totalTranscripts = totalTranscripts; }
    }
}
