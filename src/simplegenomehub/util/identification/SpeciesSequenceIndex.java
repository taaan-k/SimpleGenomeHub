package simplegenomehub.util.identification;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.CachedSequenceLookup;
import simplegenomehub.util.fileio.TranscriptToGeneMapper;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Sequence index for a specific species, providing fast lookup and pattern matching
 */
public class SpeciesSequenceIndex {
    
    private SpeciesInfo speciesInfo;
    private Set<String> allSequenceIds;           // All sequence IDs (transcripts, genes, etc.)
    private Set<String> allGeneIds;               // All gene IDs
    private Map<String, String> transcriptToGene; // Transcript ID → Gene ID mapping
    private Map<String, Set<String>> geneToTranscripts; // Gene ID → Transcript IDs
    private Set<String> idPatterns;               // Common ID patterns for this species
    private Map<String, String> normalizedIdMap;  // Normalized ID → Original ID mapping
    private boolean indexBuilt = false;
    
    // Pattern matching for common ID formats
    private static final Map<String, Pattern> COMMON_PATTERNS = new HashMap<>();
    static {
        // Arabidopsis: AT1G01010, AT1G01010.1
        COMMON_PATTERNS.put("ARABIDOPSIS", Pattern.compile("AT[1-5MC]G\\d{5}(\\.\\d+)?"));
        // Rice: LOC_Os01g01010, LOC_Os01g01010.1
        COMMON_PATTERNS.put("RICE", Pattern.compile("LOC_Os\\d{2}g\\d{5}(\\.\\d+)?"));
        // Maize: GRMZM2G000001, GRMZM2G000001_T01
        COMMON_PATTERNS.put("MAIZE", Pattern.compile("GRMZM\\d+G\\d+(_T\\d+)?"));
        // Generic gene patterns: Gene001, Gene001.t1, Gene001_transcript_1
        COMMON_PATTERNS.put("GENERIC", Pattern.compile("\\w+\\.?(t\\d+|transcript_\\d+)?$"));
    }
    
    public SpeciesSequenceIndex(SpeciesInfo speciesInfo) {
        this.speciesInfo = speciesInfo;
        this.allSequenceIds = ConcurrentHashMap.newKeySet();
        this.allGeneIds = ConcurrentHashMap.newKeySet();
        this.transcriptToGene = new ConcurrentHashMap<>();
        this.geneToTranscripts = new ConcurrentHashMap<>();
        this.idPatterns = ConcurrentHashMap.newKeySet();
        this.normalizedIdMap = new ConcurrentHashMap<>();
    }
    
    /**
     * Build the sequence index by loading all sequence data
     */
    public synchronized void buildIndex() {
        if (indexBuilt) return;
        
        try {
            System.out.println("Building sequence index for " + speciesInfo.getSpeciesName());
            
            // Load sequence files from sequence directory
            loadSequenceFilesFromDirectory();
            
            // Build transcript-to-gene mapping
            buildTranscriptGeneMapping();
            
            // Extract ID patterns
            extractIdPatterns();
            
            // Build normalized ID mapping for fuzzy search
            buildNormalizedIdMapping();
            
            indexBuilt = true;
            System.out.println("Index built for " + speciesInfo.getSpeciesName() + 
                             ": " + allSequenceIds.size() + " sequences, " + 
                             allGeneIds.size() + " genes");
            
        } catch (Exception e) {
            System.err.println("Error building index for " + speciesInfo.getSpeciesName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Load sequence files from the species sequence directory
     */
    private void loadSequenceFilesFromDirectory() {
        File sequenceDir = speciesInfo.getSequenceDir();
        if (sequenceDir == null || !sequenceDir.exists()) {
            System.out.println("No sequence directory found for " + speciesInfo.getSpeciesName());
            return;
        }
        
        File[] fastaFiles = sequenceDir.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            return lowerName.endsWith(".fasta") || lowerName.endsWith(".fa") || lowerName.endsWith(".fas");
        });
        
        if (fastaFiles != null) {
            for (File fastaFile : fastaFiles) {
                String fileName = fastaFile.getName().toLowerCase();
                String sequenceType = "unknown";
                
                if (fileName.contains("transcript")) {
                    sequenceType = "transcript";
                } else if (fileName.contains("cds")) {
                    sequenceType = "cds";
                } else if (fileName.contains("protein")) {
                    sequenceType = "protein";
                } else if (fileName.contains("genome")) {
                    sequenceType = "genome";
                }
                
                loadSequenceIds(fastaFile, sequenceType);
            }
        }
    }
    
    /**
     * Load sequence IDs from a FASTA file
     */
    private void loadSequenceIds(File fastaFile, String sequenceType) {
        if (fastaFile == null || !fastaFile.exists()) return;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(fastaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(">")) {
                    String id = extractSequenceId(line);
                    if (id != null && !id.isEmpty()) {
                        allSequenceIds.add(id);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading " + fastaFile.getName() + ": " + e.getMessage());
        }
    }
    
    /**
     * Extract sequence ID from FASTA header line
     */
    private String extractSequenceId(String headerLine) {
        // Remove ">" and get first token (ID)
        String line = headerLine.substring(1).trim();
        
        // Handle both space and tab separators
        int spaceIndex = line.indexOf(' ');
        int tabIndex = line.indexOf('\t');
        
        int separatorIndex = -1;
        if (spaceIndex > 0 && tabIndex > 0) {
            separatorIndex = Math.min(spaceIndex, tabIndex);
        } else if (spaceIndex > 0) {
            separatorIndex = spaceIndex;
        } else if (tabIndex > 0) {
            separatorIndex = tabIndex;
        }
        
        return separatorIndex > 0 ? line.substring(0, separatorIndex) : line;
    }
    
    /**
     * Build transcript-to-gene mapping using TranscriptToGeneMapper
     */
    private void buildTranscriptGeneMapping() {
        TranscriptToGeneMapper mapper = new TranscriptToGeneMapper();
        
        for (String transcriptId : allSequenceIds) {
            String geneId = mapper.mapTranscriptToGene(transcriptId);
            if (geneId != null && !geneId.equals(transcriptId)) {
                transcriptToGene.put(transcriptId, geneId);
                allGeneIds.add(geneId);
                
                // Build reverse mapping
                geneToTranscripts.computeIfAbsent(geneId, k -> ConcurrentHashMap.newKeySet()).add(transcriptId);
            } else {
                // Consider it a gene ID if no mapping found
                allGeneIds.add(transcriptId);
            }
        }
    }
    
    /**
     * Extract common ID patterns from the sequence data
     */
    private void extractIdPatterns() {
        Map<String, Integer> patternCounts = new HashMap<>();
        
        for (String id : allSequenceIds) {
            for (Map.Entry<String, Pattern> entry : COMMON_PATTERNS.entrySet()) {
                if (entry.getValue().matcher(id).matches()) {
                    patternCounts.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }
        
        // Add patterns that match significant portion of IDs
        int totalIds = allSequenceIds.size();
        for (Map.Entry<String, Integer> entry : patternCounts.entrySet()) {
            double ratio = (double) entry.getValue() / totalIds;
            if (ratio > 0.1) { // Pattern matches >10% of sequences
                idPatterns.add(entry.getKey());
            }
        }
    }
    
    /**
     * Build normalized ID mapping for fuzzy search
     */
    private void buildNormalizedIdMapping() {
        for (String id : allSequenceIds) {
            String normalized = normalizeId(id);
            normalizedIdMap.put(normalized, id);
        }
    }
    
    /**
     * Normalize ID for fuzzy matching (remove version numbers, convert to uppercase)
     */
    private String normalizeId(String id) {
        return id.replaceAll("\\.(\\d+)$", "")  // Remove .1, .2, etc.
                 .replaceAll("_t\\d+$", "")     // Remove _t1, _t2, etc.
                 .replaceAll("_transcript_\\d+$", "") // Remove _transcript_1, etc.
                 .toUpperCase();
    }
    
    /**
     * Check if this species contains a specific sequence ID (exact match)
     */
    public boolean containsSequenceId(String id) {
        if (!indexBuilt) buildIndex();
        return allSequenceIds.contains(id);
    }
    
    /**
     * Enhanced sequence ID search with partial matching support
     */
    public boolean containsSequenceIdFlexible(String queryId) {
        if (!indexBuilt) buildIndex();
        
        // Direct exact match
        if (allSequenceIds.contains(queryId)) {
            return true;
        }
        
        // Try with common suffixes (.t1, .1, etc.)
        String[] commonSuffixes = {".t1", ".1", "_t1", "_transcript_1"};
        for (String suffix : commonSuffixes) {
            if (allSequenceIds.contains(queryId + suffix)) {
                return true;
            }
        }
        
        // Try partial prefix matching
        for (String seqId : allSequenceIds) {
            if (seqId.startsWith(queryId + ".") || seqId.startsWith(queryId + "_")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Find sequence ID using pattern matching
     */
    public String findByPattern(String queryId) {
        if (!indexBuilt) buildIndex();
        
        // Try transcript-to-gene mapping
        String geneId = transcriptToGene.get(queryId);
        if (geneId != null && allGeneIds.contains(geneId)) {
            return geneId;
        }
        
        // Try reverse lookup (gene to any transcript)
        Set<String> transcripts = geneToTranscripts.get(queryId);
        if (transcripts != null && !transcripts.isEmpty()) {
            return transcripts.iterator().next(); // Return first transcript
        }
        
        return null;
    }
    
    /**
     * Find sequence ID using fuzzy matching
     */
    public List<String> findByFuzzyMatch(String queryId, double similarityThreshold) {
        if (!indexBuilt) buildIndex();
        
        List<String> matches = new ArrayList<>();
        String normalizedQuery = normalizeId(queryId);
        
        // Check normalized exact match first
        String exactMatch = normalizedIdMap.get(normalizedQuery);
        if (exactMatch != null) {
            matches.add(exactMatch);
            return matches;
        }
        
        // Calculate edit distance for fuzzy matching
        for (Map.Entry<String, String> entry : normalizedIdMap.entrySet()) {
            double similarity = calculateSimilarity(normalizedQuery, entry.getKey());
            if (similarity >= similarityThreshold) {
                matches.add(entry.getValue());
            }
        }
        
        // Sort by similarity (this is simplified - in real implementation, 
        // we'd store similarity scores)
        return matches;
    }
    
    /**
     * Calculate similarity between two strings using Levenshtein distance
     */
    private double calculateSimilarity(String s1, String s2) {
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        
        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLen;
    }
    
    /**
     * Calculate Levenshtein distance between two strings
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i-1) == s2.charAt(j-1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                    dp[i-1][j] + 1,      // deletion
                    dp[i][j-1] + 1),     // insertion
                    dp[i-1][j-1] + cost  // substitution
                );
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    /**
     * Calculate match score for a list of query IDs with enhanced matching
     */
    public double calculateMatchScore(List<String> queryIds) {
        if (!indexBuilt) buildIndex();
        if (queryIds.isEmpty()) return 0.0;
        
        int exactMatches = 0;
        int flexibleMatches = 0;
        int patternMatches = 0;
        int fuzzyMatches = 0;
        
        for (String queryId : queryIds) {
            if (containsSequenceId(queryId)) {
                exactMatches++;
            } else if (containsSequenceIdFlexible(queryId)) {
                flexibleMatches++;
            } else if (findByPattern(queryId) != null) {
                patternMatches++;
            } else if (!findByFuzzyMatch(queryId, 0.8).isEmpty()) {
                fuzzyMatches++;
            }
        }
        
        int totalMatches = exactMatches + flexibleMatches + patternMatches + fuzzyMatches;
        if (totalMatches == 0) return 0.0;
        
        // Weighted score: exact=1.0, flexible=0.9, pattern=0.8, fuzzy=0.6
        double weightedScore = (exactMatches * 1.0 + flexibleMatches * 0.9 + patternMatches * 0.8 + fuzzyMatches * 0.6);
        return weightedScore / queryIds.size();
    }
    
    /**
     * Get detailed match information for debugging
     */
    public Map<String, Object> getMatchDetails(List<String> queryIds) {
        if (!indexBuilt) buildIndex();
        
        Map<String, Object> details = new HashMap<>();
        Map<String, String> matchResults = new HashMap<>();
        
        int exactMatches = 0;
        int flexibleMatches = 0;
        int patternMatches = 0;
        int fuzzyMatches = 0;
        int noMatches = 0;
        
        for (String queryId : queryIds) {
            if (containsSequenceId(queryId)) {
                exactMatches++;
                matchResults.put(queryId, "EXACT");
            } else if (containsSequenceIdFlexible(queryId)) {
                flexibleMatches++;
                matchResults.put(queryId, "FLEXIBLE");
            } else if (findByPattern(queryId) != null) {
                patternMatches++;
                matchResults.put(queryId, "PATTERN");
            } else if (!findByFuzzyMatch(queryId, 0.8).isEmpty()) {
                fuzzyMatches++;
                matchResults.put(queryId, "FUZZY");
            } else {
                noMatches++;
                matchResults.put(queryId, "NO_MATCH");
            }
        }
        
        details.put("totalQueries", queryIds.size());
        details.put("exactMatches", exactMatches);
        details.put("flexibleMatches", flexibleMatches);
        details.put("patternMatches", patternMatches);
        details.put("fuzzyMatches", fuzzyMatches);
        details.put("noMatches", noMatches);
        details.put("matchResults", matchResults);
        details.put("totalMatches", exactMatches + flexibleMatches + patternMatches + fuzzyMatches);
        
        return details;
    }
    
    /**
     * Get species-specific ID patterns
     */
    public Set<String> getIdPatterns() {
        if (!indexBuilt) buildIndex();
        return new HashSet<>(idPatterns);
    }
    
    // Getters
    public SpeciesInfo getSpeciesInfo() { return speciesInfo; }
    public Set<String> getAllSequenceIds() { 
        if (!indexBuilt) buildIndex();
        return new HashSet<>(allSequenceIds); 
    }
    public Set<String> getAllGeneIds() { 
        if (!indexBuilt) buildIndex();
        return new HashSet<>(allGeneIds); 
    }
    public boolean isIndexBuilt() { return indexBuilt; }
    
    /**
     * Get index statistics
     */
    public Map<String, Object> getIndexStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("species", speciesInfo.getSpeciesName());
        stats.put("version", speciesInfo.getVersion());
        stats.put("sequenceCount", allSequenceIds.size());
        stats.put("geneCount", allGeneIds.size());
        stats.put("transcriptMappings", transcriptToGene.size());
        stats.put("patterns", idPatterns);
        stats.put("indexBuilt", indexBuilt);
        return stats;
    }
}