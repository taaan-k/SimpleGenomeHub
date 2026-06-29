/*
 * Genome Statistics Calculator
 */
package simplegenomehub.util.fileio;

import simplegenomehub.model.GenomeData;
import java.io.*;
import java.util.*;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Calculates comprehensive genome statistics from FASTA and annotation files
 * Generates stat.txt files for species data
 * 
 * @author SimpleGenomeHub
 */
public class GenomeStatsCalculator {
    
    private static final Logger logger = Logger.getLogger(GenomeStatsCalculator.class.getName());
    
    /**
     * Calculate comprehensive genome statistics
     */
    public static GenomeData calculateGenomeStats(File genomeFile, File annotationFile) {
        logger.info("Calculating genome statistics for: " + genomeFile.getName());
        
        GenomeData stats = new GenomeData();
        
        try {
            // Set file names
            stats.setGenomeFileName(genomeFile.getName());
            if (annotationFile != null) {
                stats.setAnnotationFileName(annotationFile.getName());
            }
            
            // Calculate genome statistics
            calculateGenomeSequenceStats(genomeFile, stats);
            
            // Calculate annotation statistics if available
            if (annotationFile != null && annotationFile.exists()) {
                calculateAnnotationStats(annotationFile, stats);
            }
            
            // Check for FASTA index
            File indexFile = new File(genomeFile.getAbsolutePath() + ".fai");
            stats.setHasIndex(indexFile.exists());
            
            logger.info("Genome statistics calculation completed");
            return stats;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error calculating genome statistics", e);
            return stats; // Return partial stats
        }
    }
    
    /**
     * Calculate statistics from genome FASTA file
     */
    private static void calculateGenomeSequenceStats(File genomeFile, GenomeData stats) throws IOException {
        logger.info("Analyzing genome sequences...");
        
        long totalLength = 0;
        int sequenceCount = 0;
        int chromosomeCount = 0;
        int scaffoldCount = 0;
        List<Integer> sequenceLengths = new ArrayList<>();
        
        // GC content calculation
        long gcCount = 0;
        long totalBases = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(genomeFile))) {
            String line;
            StringBuilder currentSequence = new StringBuilder();
            String currentSeqId = null;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (line.startsWith(">")) {
                    // Process previous sequence
                    if (currentSeqId != null && currentSequence.length() > 0) {
                        SequenceStats sequenceStats = summarizeSequence(currentSeqId, currentSequence.toString());
                        recordSequenceStats(sequenceStats, stats, sequenceLengths);

                        totalLength += sequenceStats.length;
                        sequenceCount++;
                        gcCount += sequenceStats.gcCount;
                        totalBases += sequenceStats.totalBases;

                        if (sequenceStats.chromosomeLike) {
                            chromosomeCount++;
                        } else {
                            scaffoldCount++;
                        }
                    }
                    
                    // Start new sequence
                    currentSeqId = line.substring(1).split("\\s+")[0];
                    currentSequence = new StringBuilder();
                    
                } else {
                    currentSequence.append(line);
                }
            }
            
            // Process last sequence
            if (currentSeqId != null && currentSequence.length() > 0) {
                SequenceStats sequenceStats = summarizeSequence(currentSeqId, currentSequence.toString());
                recordSequenceStats(sequenceStats, stats, sequenceLengths);
                totalLength += sequenceStats.length;
                sequenceCount++;
                gcCount += sequenceStats.gcCount;
                totalBases += sequenceStats.totalBases;

                if (sequenceStats.chromosomeLike) {
                    chromosomeCount++;
                } else {
                    scaffoldCount++;
                }
            }
        }
        
        // Set calculated statistics
        stats.setGenomeSize(totalLength);
        stats.setChromosomeCount(chromosomeCount);
        stats.setScaffoldCount(scaffoldCount);
        
        // Calculate GC content
        if (totalBases > 0) {
            stats.setGcContent((double) gcCount / totalBases * 100);
        }
        
        // Calculate N50
        if (!sequenceLengths.isEmpty()) {
            stats.setN50(calculateN50(sequenceLengths, totalLength));
        }
        
        // Determine assembly level
        if (chromosomeCount > 0) {
            stats.setAssemblyLevel("chromosome");
        } else if (scaffoldCount > 0) {
            stats.setAssemblyLevel("scaffold");
        } else {
            stats.setAssemblyLevel("contig");
        }
        
        logger.info(String.format("Genome stats: %d sequences, %d bp total, %.2f%% GC",
                                sequenceCount, totalLength, stats.getGcContent()));
    }
    
    /**
     * Process individual sequence for statistics
     */
    private static void recordSequenceStats(SequenceStats sequenceStats, GenomeData stats,
                                            List<Integer> sequenceLengths) {
        sequenceLengths.add(sequenceStats.length);
        // Persist each imported FASTA entry in stat.txt for downstream use.
        stats.addChromosomeStat(sequenceStats.sequenceId, sequenceStats.length, sequenceStats.gcContent);
    }

    private static SequenceStats summarizeSequence(String seqId, String sequence) {
        long gcCount = 0;
        long totalBases = 0;
        String normalized = sequence.toUpperCase(Locale.ROOT);

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == 'G' || c == 'C') {
                gcCount++;
            }
            if (c == 'A' || c == 'T' || c == 'G' || c == 'C') {
                totalBases++;
            }
        }

        double gcContent = totalBases > 0 ? (double) gcCount / totalBases * 100.0 : 0.0;
        return new SequenceStats(
            seqId,
            sequence.length(),
            gcCount,
            totalBases,
            gcContent,
            isChromosome(seqId)
        );
    }
    
    /**
     * Determine if sequence is a chromosome based on ID
     */
    private static boolean isChromosome(String seqId) {
        String id = seqId.toLowerCase();
        return id.startsWith("chr") || 
               id.matches("^\\d+$") ||  // Just a number
               id.matches("^[ivx]+$") || // Roman numerals
               id.contains("chromosome");
    }
    
    /**
     * Calculate N50 statistic
     */
    private static long calculateN50(List<Integer> lengths, long totalLength) {
        // Sort lengths in descending order
        List<Integer> sortedLengths = new ArrayList<>(lengths);
        sortedLengths.sort(Collections.reverseOrder());
        
        long target = totalLength / 2;
        long cumulative = 0;
        
        for (int length : sortedLengths) {
            cumulative += length;
            if (cumulative >= target) {
                return length;
            }
        }
        
        return 0;
    }
    
    /**
     * Calculate statistics from annotation file
     */
    private static void calculateAnnotationStats(File annotationFile, GenomeData stats) throws IOException {
        logger.info("Analyzing annotation features...");
        
        Set<String> uniqueGenes = new HashSet<>();
        Set<String> uniqueTranscripts = new HashSet<>();
        Set<String> uniqueProteins = new HashSet<>();
        int cdsCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(annotationFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] fields = line.split("\t");
                if (fields.length >= 9) {
                    String featureType = fields[2].toLowerCase();
                    String attributes = fields[8];
                    
                    // Extract IDs based on feature type and attributes
                    extractFeatureIds(featureType, attributes, uniqueGenes, uniqueTranscripts, uniqueProteins);
                    
                    // Count CDS features (these are numerous, not unique entities)
                    if (featureType.equals("cds")) {
                        cdsCount++;
                    }
                }
            }
        }
        
        // Set statistics using unique counts
        stats.setGeneCount(uniqueGenes.size());
        stats.setTranscriptCount(uniqueTranscripts.size());
        stats.setCdsCount(cdsCount);
        // Use transcript count as protein count since each transcript produces one protein
        stats.setProteinCount(uniqueTranscripts.size());
        
        // Try to determine annotation source
        determineAnnotationSource(annotationFile, stats);
        
        logger.info(String.format("Annotation stats: %d genes, %d transcripts, %d CDS",
                                stats.getGeneCount(), stats.getTranscriptCount(), stats.getCdsCount()));
    }
    
    /**
     * Extract feature IDs based on feature type and attributes
     */
    private static void extractFeatureIds(String featureType, String attributes, 
                                        Set<String> genes, Set<String> transcripts, Set<String> proteins) {
        Map<String, String> attrMap = parseAttributeString(attributes);
        
        switch (featureType.toLowerCase()) {
            case "gene":
                // Gene features: ID is gene ID
                String geneId = getAttributeValue(attrMap, "ID", "gene_id");
                if (geneId != null) {
                    genes.add(geneId);
                }
                break;
                
            case "mrna":
            case "transcript":
                // Transcript features: ID is transcript ID
                String transcriptId = getAttributeValue(attrMap, "ID", "transcript_id");
                if (transcriptId != null) {
                    transcripts.add(transcriptId);
                }
                break;
                
            case "protein":
                // Protein features rarely exist in GFF3 - proteins are derived from CDS
                // Skipping explicit protein counting (using transcript count instead)
                break;
        }
    }
    
    /**
     * Parse attribute string into key-value map
     */
    private static Map<String, String> parseAttributeString(String attributes) {
        Map<String, String> attrMap = new HashMap<>();
        
        if (attributes.contains("=")) {
            // GFF3 format: key=value
            for (String pair : attributes.split(";")) {
                String[] kv = pair.trim().split("=", 2);
                if (kv.length == 2) {
                    attrMap.put(kv[0].trim(), kv[1].trim());
                }
            }
        } else {
            // GTF format: key "value"
            for (String pair : attributes.split(";")) {
                pair = pair.trim();
                if (pair.contains(" \"")) {
                    String[] parts = pair.split(" \"", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].replace("\"", "").trim();
                        attrMap.put(key, value);
                    }
                }
            }
        }
        
        return attrMap;
    }
    
    /**
     * Get attribute value by trying multiple keys
     */
    private static String getAttributeValue(Map<String, String> attributes, String... keys) {
        for (String key : keys) {
            String value = attributes.get(key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }
    
    /**
     * Determine annotation source from file headers or content
     */
    private static void determineAnnotationSource(File annotationFile, GenomeData stats) {
        try (BufferedReader reader = new BufferedReader(new FileReader(annotationFile))) {
            String line;
            int lineCount = 0;
            
            while ((line = reader.readLine()) != null && lineCount < 100) {
                line = line.toLowerCase();
                
                if (line.contains("ensembl")) {
                    stats.setAnnotationSource("Ensembl");
                    return;
                } else if (line.contains("ncbi") || line.contains("refseq")) {
                    stats.setAnnotationSource("NCBI/RefSeq");
                    return;
                } else if (line.contains("tair")) {
                    stats.setAnnotationSource("TAIR");
                    return;
                } else if (line.contains("jgi") || line.contains("phytozome")) {
                    stats.setAnnotationSource("JGI/Phytozome");
                    return;
                }
                
                lineCount++;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error determining annotation source", e);
        }
        
        stats.setAnnotationSource("Unknown");
    }
    
    /**
     * Generate comprehensive statistics report
     */
    public static String generateStatsReport(GenomeData stats) {
        StringBuilder report = new StringBuilder();
        
        report.append("=== Genome Statistics Report ===\n\n");
        
        // Basic information
        report.append("Files:\n");
        report.append("  Genome: ").append(stats.getGenomeFileName()).append("\n");
        if (stats.getAnnotationFileName() != null) {
            report.append("  Annotation: ").append(stats.getAnnotationFileName()).append("\n");
        }
        report.append("\n");
        
        // Genome statistics
        report.append("Genome Statistics:\n");
        report.append("  Total Size: ").append(stats.getFormattedGenomeSize()).append("\n");
        report.append("  Assembly Level: ").append(stats.getAssemblyLevel()).append("\n");
        report.append("  GC Content: ").append(String.format("%.2f%%", stats.getGcContent())).append("\n");
        
        if (stats.getChromosomeCount() > 0) {
            report.append("  Chromosomes: ").append(stats.getChromosomeCount()).append("\n");
        }
        if (stats.getScaffoldCount() > 0) {
            report.append("  Scaffolds: ").append(stats.getScaffoldCount()).append("\n");
        }
        if (stats.getN50() > 0) {
            report.append("  N50: ").append(String.format("%,d bp", stats.getN50())).append("\n");
        }
        report.append("\n");
        
        // Annotation statistics
        if (stats.getGeneCount() > 0) {
            report.append("Annotation Statistics:\n");
            report.append("  Source: ").append(stats.getAnnotationSource()).append("\n");
            report.append("  Genes: ").append(String.format("%,d", stats.getGeneCount())).append("\n");
            report.append("  Transcripts: ").append(String.format("%,d", stats.getTranscriptCount())).append("\n");
            report.append("  CDS Features: ").append(String.format("%,d", stats.getCdsCount())).append("\n");
            report.append("  Proteins: ").append(String.format("%,d", stats.getProteinCount())).append("\n");
            report.append("\n");
        }
        
        // Index information
        report.append("Indexing:\n");
        report.append("  FASTA Index: ").append(stats.isHasIndex() ? "Present" : "Not found").append("\n");
        
        return report.toString();
    }

    private static final class SequenceStats {
        private final String sequenceId;
        private final int length;
        private final long gcCount;
        private final long totalBases;
        private final double gcContent;
        private final boolean chromosomeLike;

        private SequenceStats(String sequenceId, int length, long gcCount, long totalBases,
                              double gcContent, boolean chromosomeLike) {
            this.sequenceId = sequenceId;
            this.length = length;
            this.gcCount = gcCount;
            this.totalBases = totalBases;
            this.gcContent = gcContent;
            this.chromosomeLike = chromosomeLike;
        }
    }
}
