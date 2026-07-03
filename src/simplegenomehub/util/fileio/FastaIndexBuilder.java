/*
 * FASTA Index Builder for Fast Sequence Retrieval
 */
package simplegenomehub.util.fileio;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates and manages FASTA index files for efficient sequence retrieval
 * Based on samtools faidx format and TBtools FastaChunkReader patterns
 * 
 * @author SimpleGenomeHub
 */
public class FastaIndexBuilder {
    
    private static final Logger logger = Logger.getLogger(FastaIndexBuilder.class.getName());
    
    /**
     * FASTA index entry
     */
    public static class IndexEntry {
        private String seqId;
        private long length;
        private long offset;
        private int lineLength;
        private int lineLengthWithNewline;
        
        public IndexEntry(String seqId, long length, long offset, int lineLength, int lineLengthWithNewline) {
            this.seqId = seqId;
            this.length = length;
            this.offset = offset;
            this.lineLength = lineLength;
            this.lineLengthWithNewline = lineLengthWithNewline;
        }
        
        // Getters
        public String getSeqId() { return seqId; }
        public long getLength() { return length; }
        public long getOffset() { return offset; }
        public int getLineLength() { return lineLength; }
        public int getLineLengthWithNewline() { return lineLengthWithNewline; }
        
        @Override
        public String toString() {
            return String.format("%s\t%d\t%d\t%d\t%d", seqId, length, offset, lineLength, lineLengthWithNewline);
        }
    }
    
    /**
     * Build FASTA index for a file
     */
    public static boolean buildIndex(File fastaFile) {
        File indexFile = new File(fastaFile.getAbsolutePath() + ".fai");
        return buildIndex(fastaFile, indexFile);
    }
    
    /**
     * Build FASTA index with custom index file location
     */
    public static boolean buildIndex(File fastaFile, File indexFile) {
        logger.info("Building FASTA index for: " + fastaFile.getName());
        
        try {
            List<IndexEntry> entries = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(new FileReader(fastaFile))) {
                String line;
                String currentSeqId = null;
                long currentLength = 0;
                long currentOffset = 0;
                long filePosition = 0;
                int lineLength = 0;
                int lineLengthWithNewline = 0;
                boolean firstSequenceLine = true;
                
                while ((line = reader.readLine()) != null) {
                    int lineBytes = line.getBytes().length + 1; // +1 for newline
                    
                    if (line.startsWith(">")) {
                        // Save previous sequence index entry
                        if (currentSeqId != null) {
                            entries.add(new IndexEntry(currentSeqId, currentLength, currentOffset, 
                                                     lineLength, lineLengthWithNewline));
                        }
                        
                        // Start new sequence
                        currentSeqId = line.substring(1).split("\\s+")[0];
                        currentLength = 0;
                        currentOffset = filePosition + lineBytes;
                        firstSequenceLine = true;
                        
                    } else if (currentSeqId != null) {
                        // Sequence line
                        if (firstSequenceLine) {
                            lineLength = line.length();
                            lineLengthWithNewline = lineBytes;
                            firstSequenceLine = false;
                        }
                        currentLength += line.length();
                    }
                    
                    filePosition += lineBytes;
                }
                
                // Save last sequence
                if (currentSeqId != null) {
                    entries.add(new IndexEntry(currentSeqId, currentLength, currentOffset, 
                                             lineLength, lineLengthWithNewline));
                }
            }
            
            // Write index file
            try (PrintWriter writer = new PrintWriter(new FileWriter(indexFile))) {
                for (IndexEntry entry : entries) {
                    writer.println(entry.toString());
                }
            }
            
            logger.info("FASTA index created: " + entries.size() + " sequences indexed");
            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error building FASTA index", e);
            return false;
        }
    }
    
    /**
     * Load FASTA index from file
     */
    public static Map<String, IndexEntry> loadIndex(File indexFile) {
        Map<String, IndexEntry> index = new HashMap<>();
        
        if (!indexFile.exists()) {
            logger.warning("Index file not found: " + indexFile.getAbsolutePath());
            return index;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(indexFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                String[] parts = line.split("\t");
                if (parts.length >= 5) {
                    try {
                        String seqId = parts[0];
                        long length = Long.parseLong(parts[1]);
                        long offset = Long.parseLong(parts[2]);
                        int lineLength = Integer.parseInt(parts[3]);
                        int lineLengthWithNewline = Integer.parseInt(parts[4]);
                        
                        IndexEntry entry = new IndexEntry(seqId, length, offset, lineLength, lineLengthWithNewline);
                        index.put(seqId, entry);
                        
                    } catch (NumberFormatException e) {
                        logger.warning("Invalid index entry: " + line);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Error loading FASTA index", e);
        }
        
        logger.info("Loaded FASTA index: " + index.size() + " sequences");
        return index;
    }
    
    /**
     * Extract sequence using index (efficient for large files)
     */
    public static String extractSequence(File fastaFile, Map<String, IndexEntry> index, String seqId) {
        return extractSequence(fastaFile, index, seqId, 1, -1);
    }
    
    /**
     * Extract subsequence using index
     */
    public static String extractSequence(File fastaFile, Map<String, IndexEntry> index, 
                                       String seqId, int start, int end) {
        IndexEntry entry = index.get(seqId);
        if (entry == null) {
            logger.warning("Sequence not found in index: " + seqId);
            return null;
        }
        
        // Validate coordinates
        if (start < 1) start = 1;
        if (end < 0 || end > entry.getLength()) end = (int) entry.getLength();
        if (start > end) {
            logger.warning("Invalid coordinates: start=" + start + ", end=" + end);
            return null;
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(fastaFile, "r")) {
            StringBuilder sequence = new StringBuilder();
            
            // Calculate file positions for the requested region
            long startPos = calculateFilePosition(entry, start);
            long endPos = calculateFilePosition(entry, end + 1);
            
            raf.seek(startPos);
            
            byte[] buffer = new byte[(int) (endPos - startPos)];
            int bytesRead = raf.read(buffer);
            
            // Parse the read data and extract sequence
            String data = new String(buffer, 0, bytesRead);
            for (String line : data.split("\n")) {
                if (!line.startsWith(">")) {
                    sequence.append(line.trim());
                }
            }
            
            // Extract the exact subsequence
            String fullSequence = sequence.toString();
            int seqStart = start - 1; // Convert to 0-based
            int seqEnd = Math.min(end, fullSequence.length());
            
            if (seqStart < fullSequence.length()) {
                return fullSequence.substring(seqStart, seqEnd);
            }
            
            return "";
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error extracting sequence: " + seqId, e);
            return null;
        }
    }
    
    /**
     * Calculate file position for a given sequence coordinate
     */
    private static long calculateFilePosition(IndexEntry entry, int seqPos) {
        if (seqPos <= 1) {
            return entry.getOffset();
        }
        
        int adjustedPos = seqPos - 1; // Convert to 0-based
        int fullLines = adjustedPos / entry.getLineLength();
        int remainingBases = adjustedPos % entry.getLineLength();
        
        return entry.getOffset() + (fullLines * entry.getLineLengthWithNewline()) + remainingBases;
    }
    
    /**
     * Get sequence information from index
     */
    public static Map<String, Object> getSequenceInfo(Map<String, IndexEntry> index) {
        Map<String, Object> info = new HashMap<>();
        
        int sequenceCount = index.size();
        long totalLength = index.values().stream().mapToLong(IndexEntry::getLength).sum();
        
        // Find longest and shortest sequences
        OptionalLong maxLength = index.values().stream().mapToLong(IndexEntry::getLength).max();
        OptionalLong minLength = index.values().stream().mapToLong(IndexEntry::getLength).min();
        
        info.put("sequenceCount", sequenceCount);
        info.put("totalLength", totalLength);
        info.put("maxSequenceLength", maxLength.orElse(0));
        info.put("minSequenceLength", minLength.orElse(0));
        info.put("averageLength", sequenceCount > 0 ? totalLength / sequenceCount : 0);
        
        // Get sequence IDs
        List<String> seqIds = new ArrayList<>(index.keySet());
        seqIds.sort(String::compareTo);
        info.put("sequenceIds", seqIds);
        
        return info;
    }
    
    /**
     * Validate existing index against FASTA file
     */
    public static boolean validateIndex(File fastaFile, File indexFile) {
        if (!indexFile.exists()) {
            return false;
        }
        
        // Check if index is newer than FASTA file
        if (indexFile.lastModified() < fastaFile.lastModified()) {
            logger.info("Index file is older than FASTA file, needs rebuilding");
            return false;
        }
        
        Map<String, IndexEntry> index = loadIndex(indexFile);
        if (index.isEmpty()) {
            return false;
        }
        
        // Quick validation: check if we can read first sequence
        try {
            String firstSeqId = index.keySet().iterator().next();
            String sequence = extractSequence(fastaFile, index, firstSeqId, 1, 100);
            return sequence != null && !sequence.isEmpty();
            
        } catch (Exception e) {
            logger.warning("Index validation failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Ensure index exists and is valid, create if necessary
     */
    public static boolean ensureIndex(File fastaFile) {
        File indexFile = new File(fastaFile.getAbsolutePath() + ".fai");
        
        if (validateIndex(fastaFile, indexFile)) {
            logger.info("Valid FASTA index found for: " + fastaFile.getName());
            return true;
        }
        
        logger.info("Creating FASTA index for: " + fastaFile.getName());
        return buildIndex(fastaFile, indexFile);
    }
}