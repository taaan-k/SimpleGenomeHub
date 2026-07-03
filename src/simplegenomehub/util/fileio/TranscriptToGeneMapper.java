/*
 * Transcript to Gene ID Mapper
 * Handles mapping between transcript IDs and gene IDs based on GFF3 annotation files
 */
package simplegenomehub.util.fileio;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utility class for mapping transcript IDs to gene IDs
 * Based on GFF3 annotation files and common ID patterns
 * 
 * @author SimpleGenomeHub
 */
public class TranscriptToGeneMapper {
    
    private static final Logger logger = Logger.getLogger(TranscriptToGeneMapper.class.getName());
    
    // Common patterns for transcript-to-gene mapping
    private static final Pattern[] TRANSCRIPT_PATTERNS = {
        Pattern.compile("(AT\\d+G\\d+)\\.\\d+"),           // Arabidopsis: AT1G01010.1 -> AT1G01010
        Pattern.compile("(LOC_\\w+)\\.\\d+"),             // Rice: LOC_Os01g01010.1 -> LOC_Os01g01010
        Pattern.compile("(GRMZM\\w+)_T\\d+"),             // Maize: GRMZM2G000001_T01 -> GRMZM2G000001
        Pattern.compile("(\\w+)\\.[t|T]\\d+"),            // Generic: Gene.t1 -> Gene
        Pattern.compile("(\\w+)-mRNA-\\d+"),              // Generic: Gene-mRNA-1 -> Gene
        Pattern.compile("(\\w+)_transcript_\\d+"),        // Generic: Gene_transcript_1 -> Gene
        Pattern.compile("(\\w+)[\\._][Tt]ranscript[\\._]\\d+"), // Various transcript formats
    };
    
    private Map<String, String> transcriptToGeneMap;
    private Map<String, Set<String>> geneToTranscriptsMap;
    private boolean isLoaded = false;
    
    /**
     * Constructor
     */
    public TranscriptToGeneMapper() {
        this.transcriptToGeneMap = new HashMap<>();
        this.geneToTranscriptsMap = new HashMap<>();
    }
    
    /**
     * Load mapping from GFF3 annotation file
     */
    public boolean loadFromGFF3(File gffFile) {
        if (gffFile == null || !gffFile.exists()) {
            return false;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(gffFile))) {
            String line;
            int genesLoaded = 0;
            int transcriptsLoaded = 0;
            
            logger.info("Loading transcript-gene mapping from GFF3: " + gffFile.getName());
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }
                
                String[] fields = line.split("\t");
                if (fields.length < 9) {
                    continue;
                }
                
                String feature = fields[2];
                String attributes = fields[8];
                
                if ("gene".equals(feature)) {
                    String geneId = extractGeneId(attributes);
                    if (geneId != null) {
                        geneToTranscriptsMap.putIfAbsent(geneId, new HashSet<>());
                        genesLoaded++;
                    }
                } else if ("mRNA".equals(feature) || "transcript".equals(feature)) {
                    String transcriptId = extractTranscriptId(attributes);
                    String parentGeneId = extractParentGeneId(attributes);
                    
                    if (transcriptId != null && parentGeneId != null) {
                        transcriptToGeneMap.put(transcriptId, parentGeneId);
                        geneToTranscriptsMap.computeIfAbsent(parentGeneId, k -> new HashSet<>()).add(transcriptId);
                        transcriptsLoaded++;
                    }
                }
            }
            
            isLoaded = true;
            logger.info("Loaded mapping: " + genesLoaded + " genes, " + transcriptsLoaded + " transcripts");
            return transcriptsLoaded > 0;
            
        } catch (IOException e) {
            logger.warning("Failed to load GFF3 mapping: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Try to map transcript ID to gene ID using pattern matching
     */
    public String mapTranscriptToGene(String transcriptId) {
        if (transcriptId == null || transcriptId.trim().isEmpty()) {
            return null;
        }
        
        // First try exact mapping from GFF3
        if (isLoaded && transcriptToGeneMap.containsKey(transcriptId)) {
            return transcriptToGeneMap.get(transcriptId);
        }
        
        // Try pattern-based mapping
        for (Pattern pattern : TRANSCRIPT_PATTERNS) {
            Matcher matcher = pattern.matcher(transcriptId);
            if (matcher.matches()) {
                String geneId = matcher.group(1);
                logger.fine("Pattern mapping: " + transcriptId + " -> " + geneId);
                return geneId;
            }
        }
        
        // If no pattern matches, assume it's already a gene ID
        return transcriptId;
    }
    
    /**
     * Get all transcripts for a gene
     */
    public Set<String> getTranscriptsForGene(String geneId) {
        return geneToTranscriptsMap.getOrDefault(geneId, new HashSet<>());
    }
    
    /**
     * Check if transcript-to-gene mapping is available
     */
    public boolean isMappingAvailable() {
        return isLoaded && !transcriptToGeneMap.isEmpty();
    }
    
    /**
     * Get mapping statistics
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("genes", geneToTranscriptsMap.size());
        stats.put("transcripts", transcriptToGeneMap.size());
        stats.put("loaded", isLoaded ? 1 : 0);
        return stats;
    }
    
    /**
     * Extract gene ID from GFF3 attributes
     */
    private String extractGeneId(String attributes) {
        // Look for ID=gene: or ID= patterns
        String[] attrs = attributes.split(";");
        for (String attr : attrs) {
            if (attr.startsWith("ID=")) {
                String id = attr.substring(3);
                if (id.startsWith("gene:")) {
                    return id.substring(5);
                }
                return id;
            }
        }
        return null;
    }
    
    /**
     * Extract transcript ID from GFF3 attributes
     */
    private String extractTranscriptId(String attributes) {
        String[] attrs = attributes.split(";");
        for (String attr : attrs) {
            if (attr.startsWith("ID=")) {
                String id = attr.substring(3);
                if (id.startsWith("transcript:")) {
                    return id.substring(11);
                }
                return id;
            }
        }
        return null;
    }
    
    /**
     * Extract parent gene ID from GFF3 attributes
     */
    private String extractParentGeneId(String attributes) {
        String[] attrs = attributes.split(";");
        for (String attr : attrs) {
            if (attr.startsWith("Parent=")) {
                String parent = attr.substring(7);
                if (parent.startsWith("gene:")) {
                    return parent.substring(5);
                }
                return parent;
            }
        }
        return null;
    }
    
    /**
     * Clear all mapping data
     */
    public void clear() {
        transcriptToGeneMap.clear();
        geneToTranscriptsMap.clear();
        isLoaded = false;
    }
}