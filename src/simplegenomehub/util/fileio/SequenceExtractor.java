/*
 * Sequence Extraction Utilities
 */
package simplegenomehub.util.fileio;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts sequences from genome files based on annotation data
 * Supports extraction of transcripts, CDS, and protein sequences
 * Based on TBtools ExtractFasta and GXF processing patterns
 * 
 * @author SimpleGenomeHub
 */
public class SequenceExtractor {
    
    private static final Logger logger = Logger.getLogger(SequenceExtractor.class.getName());
    
    // Genetic code table (standard)
    private static final Map<String, String> GENETIC_CODE = createGeneticCodeTable();
    
    /**
     * Feature information from GFF3/GTF
     */
    public static class FeatureInfo {
        private String seqId;
        private String type;
        private int start;
        private int end;
        private String strand;
        private String geneId;
        private String transcriptId;
        private String proteinId;
        private Map<String, String> attributes;
        
        public FeatureInfo(String seqId, String type, int start, int end, String strand) {
            this.seqId = seqId;
            this.type = type;
            this.start = start;
            this.end = end;
            this.strand = strand;
            this.attributes = new HashMap<>();
        }
        
        // Getters and setters
        public String getSeqId() { return seqId; }
        public String getType() { return type; }
        public int getStart() { return start; }
        public int getEnd() { return end; }
        public String getStrand() { return strand; }
        public String getGeneId() { return geneId; }
        public void setGeneId(String geneId) { this.geneId = geneId; }
        public String getTranscriptId() { return transcriptId; }
        public void setTranscriptId(String transcriptId) { this.transcriptId = transcriptId; }
        public String getProteinId() { return proteinId; }
        public void setProteinId(String proteinId) { this.proteinId = proteinId; }
        public Map<String, String> getAttributes() { return attributes; }
        public String getAttribute(String key) { return attributes.get(key); }
        public void setAttribute(String key, String value) { attributes.put(key, value); }
        
        public int getLength() { return Math.abs(end - start) + 1; }
    }
    
    /**
     * Extraction result
     */
    public static class ExtractionResult {
        private boolean success;
        private String message;
        private int sequencesExtracted;
        private Map<String, List<FeatureInfo>> features;
        
        public ExtractionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.sequencesExtracted = 0;
            this.features = new HashMap<>();
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getSequencesExtracted() { return sequencesExtracted; }
        public void setSequencesExtracted(int count) { this.sequencesExtracted = count; }
        public Map<String, List<FeatureInfo>> getFeatures() { return features; }
        public void addFeature(String type, FeatureInfo feature) {
            features.computeIfAbsent(type, k -> new ArrayList<>()).add(feature);
        }
    }
    
    /**
     * Load genome sequences into memory (for small genomes) or create index
     */
    public static Map<String, String> loadGenomeSequences(File genomeFile) throws IOException {
        Map<String, String> sequences = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(genomeFile))) {
            String line;
            StringBuilder currentSequence = new StringBuilder();
            String currentSeqId = null;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (line.startsWith(">")) {
                    // Save previous sequence
                    if (currentSeqId != null && currentSequence.length() > 0) {
                        sequences.put(currentSeqId, currentSequence.toString());
                    }
                    
                    // Start new sequence
                    currentSeqId = line.substring(1).split("\\s+")[0];
                    currentSequence = new StringBuilder();
                    
                } else {
                    currentSequence.append(line.toUpperCase()); // Convert to uppercase for consistency
                }
            }
            
            // Save last sequence
            if (currentSeqId != null && currentSequence.length() > 0) {
                sequences.put(currentSeqId, currentSequence.toString());
            }
        }
        
        logger.info("Loaded " + sequences.size() + " sequences from genome file");
        return sequences;
    }
    
    /**
     * Parse annotation file and extract features
     */
    public static Map<String, List<FeatureInfo>> parseAnnotationFile(File annotationFile) throws IOException {
        Map<String, List<FeatureInfo>> features = new HashMap<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(annotationFile))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                try {
                    FeatureInfo feature = parseFeatureLine(line);
                    if (feature != null) {
                        features.computeIfAbsent(feature.getType(), k -> new ArrayList<>()).add(feature);
                    }
                } catch (Exception e) {
                    logger.warning("Error parsing line " + lineNumber + ": " + e.getMessage());
                }
            }
        }
        
        logger.info("Parsed annotation file: " + features.size() + " feature types found");
        return features;
    }
    
    /**
     * Parse single feature line from GFF3/GTF
     */
    private static FeatureInfo parseFeatureLine(String line) {
        String[] fields = line.split("\t");
        if (fields.length < 9) {
            return null;
        }
        
        try {
            String seqId = fields[0];
            String type = fields[2];
            int start = Integer.parseInt(fields[3]);
            int end = Integer.parseInt(fields[4]);
            String strand = fields[6];
            String attributes = fields[8];
            
            FeatureInfo feature = new FeatureInfo(seqId, type, start, end, strand);
            
            // Parse attributes
            parseAttributes(attributes, feature);
            
            return feature;
            
        } catch (NumberFormatException e) {
            logger.warning("Invalid coordinates in feature line: " + line);
            return null;
        }
    }
    
    /**
     * Parse GFF3/GTF attributes
     */
    private static void parseAttributes(String attributeString, FeatureInfo feature) {
        // Handle both GFF3 (ID=value) and GTF (key "value") formats
        if (attributeString.contains("=")) {
            // GFF3 format
            String[] pairs = attributeString.split(";");
            for (String pair : pairs) {
                String[] kv = pair.trim().split("=", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim();
                    String value = kv[1].trim();
                    
                    feature.setAttribute(key, value);
                    
                    // Set standard IDs based on feature type and attribute
                    if (key.equals("ID")) {
                        // For transcript/mRNA features, ID is the transcript ID
                        if (feature.getType().equals("transcript") || feature.getType().equals("mRNA")) {
                            feature.setTranscriptId(value);
                        } else if (feature.getType().equals("gene")) {
                            feature.setGeneId(value);
                        }
                    } else if (key.equals("Parent")) {
                        // For CDS/exon features, Parent is usually transcript ID
                        if (feature.getType().equals("CDS") || feature.getType().equals("exon")) {
                            feature.setTranscriptId(value);
                        }
                    } else if (key.equals("gene_id")) {
                        feature.setGeneId(value);
                    } else if (key.equals("transcript_id")) {
                        feature.setTranscriptId(value);
                    } else if (key.equals("protein_id")) {
                        feature.setProteinId(value);
                    }
                }
            }
        } else {
            // GTF format
            String[] pairs = attributeString.split(";");
            for (String pair : pairs) {
                pair = pair.trim();
                if (pair.contains(" \"")) {
                    String[] kv = pair.split(" \"", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim();
                        String value = kv[1].replace("\"", "").trim();
                        
                        feature.setAttribute(key, value);
                        
                        if (key.equals("gene_id")) {
                            feature.setGeneId(value);
                        } else if (key.equals("transcript_id")) {
                            feature.setTranscriptId(value);
                        } else if (key.equals("protein_id")) {
                            feature.setProteinId(value);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Extract representative transcript sequences only
     */
    public static ExtractionResult extractRepresentativeTranscripts(File genomeFile, File annotationFile, File outputFile, String criteria) {
        try {
            Map<String, String> genomeSeqs = loadGenomeSequences(genomeFile);
            Map<String, List<FeatureInfo>> features = parseAnnotationFile(annotationFile);
            
            // Get all transcripts
            List<FeatureInfo> allTranscripts = new ArrayList<>();
            allTranscripts.addAll(features.getOrDefault("mRNA", new ArrayList<>()));
            allTranscripts.addAll(features.getOrDefault("transcript", new ArrayList<>()));
            
            // For representative transcript extraction, we need to create a temporary file
            // and use the RepresentativeTranscriptSelector to generate it, then parse it back
            File tempGffFile = File.createTempFile("temp_representative", ".gff3");
            tempGffFile.deleteOnExit();
            
            // Convert string criteria to enum
            RepresentativeTranscriptSelector.SelectionCriteria enumCriteria;
            switch (criteria) {
                case "LONGEST_TRANSCRIPT":
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.LONGEST_TRANSCRIPT;
                    break;
                case "LONGEST_CDS":
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.LONGEST_CDS;
                    break;
                case "HIGHEST_EXPRESSION":
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.MOST_EXONS;
                    break;
                default:
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.CANONICAL;
                    break;
            }
            
            // Generate representative annotation to temp file
            if (!RepresentativeTranscriptSelector.generateRepresentativeAnnotation(annotationFile, tempGffFile, enumCriteria)) {
                throw new IOException("Failed to generate representative transcripts");
            }
            
            // Parse the representative transcripts from the temp file
            Map<String, List<FeatureInfo>> repFeatures = parseAnnotationFile(tempGffFile);
            List<FeatureInfo> representativeTranscripts = new ArrayList<>();
            representativeTranscripts.addAll(repFeatures.getOrDefault("mRNA", new ArrayList<>()));
            representativeTranscripts.addAll(repFeatures.getOrDefault("transcript", new ArrayList<>()));
            
            int extracted = 0;
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                for (FeatureInfo transcript : representativeTranscripts) {
                    String sequence = extractSequence(genomeSeqs, transcript);
                    if (sequence != null && !sequence.isEmpty()) {
                        writeSequence(writer, transcript.getTranscriptId(), sequence, "transcript");
                        extracted++;
                    }
                }
            }
            
            ExtractionResult result = new ExtractionResult(true, "Representative transcript extraction completed");
            result.setSequencesExtracted(extracted);
            return result;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error extracting representative transcripts", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Extract transcript sequences
     */
    public static ExtractionResult extractTranscripts(File genomeFile, File annotationFile, File outputFile) {
        try {
            Map<String, String> genomeSeqs = loadGenomeSequences(genomeFile);
            Map<String, List<FeatureInfo>> features = parseAnnotationFile(annotationFile);
            
            int extracted = 0;
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                
                // Process mRNA/transcript features
                List<FeatureInfo> transcripts = new ArrayList<>();
                transcripts.addAll(features.getOrDefault("mRNA", new ArrayList<>()));
                transcripts.addAll(features.getOrDefault("transcript", new ArrayList<>()));
                
                for (FeatureInfo transcript : transcripts) {
                    String sequence = extractSequence(genomeSeqs, transcript);
                    if (sequence != null && !sequence.isEmpty()) {
                        writeSequence(writer, transcript.getTranscriptId(), sequence, "transcript");
                        extracted++;
                    }
                }
            }
            
            ExtractionResult result = new ExtractionResult(true, "Transcript extraction completed");
            result.setSequencesExtracted(extracted);
            return result;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error extracting transcripts", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Extract CDS sequences
     */
    public static ExtractionResult extractCDS(File genomeFile, File annotationFile, File outputFile) {
        try {
            Map<String, String> genomeSeqs = loadGenomeSequences(genomeFile);
            Map<String, List<FeatureInfo>> features = parseAnnotationFile(annotationFile);
            
            // Group CDS by transcript/gene
            Map<String, List<FeatureInfo>> cdsGroups = groupCDSByTranscript(features.getOrDefault("CDS", new ArrayList<>()));
            
            int extracted = 0;
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                
                for (Map.Entry<String, List<FeatureInfo>> entry : cdsGroups.entrySet()) {
                    String transcriptId = entry.getKey();
                    List<FeatureInfo> cdsList = entry.getValue();
                    
                    // Sort CDS by start position
                    cdsList.sort((a, b) -> Integer.compare(a.getStart(), b.getStart()));
                    
                    // Concatenate CDS sequences
                    StringBuilder cdsSequence = new StringBuilder();
                    for (FeatureInfo cds : cdsList) {
                        String sequence = extractSequence(genomeSeqs, cds);
                        if (sequence != null) {
                            cdsSequence.append(sequence);
                        }
                    }
                    
                    if (cdsSequence.length() > 0) {
                        writeSequence(writer, transcriptId + "_CDS", cdsSequence.toString(), "CDS");
                        extracted++;
                    }
                }
            }
            
            ExtractionResult result = new ExtractionResult(true, "CDS extraction completed");
            result.setSequencesExtracted(extracted);
            return result;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error extracting CDS", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Extract protein sequences (translate CDS)
     */
    public static ExtractionResult extractProteins(File genomeFile, File annotationFile, File outputFile) {
        try {
            Map<String, String> genomeSeqs = loadGenomeSequences(genomeFile);
            Map<String, List<FeatureInfo>> features = parseAnnotationFile(annotationFile);
            
            Map<String, List<FeatureInfo>> cdsGroups = groupCDSByTranscript(features.getOrDefault("CDS", new ArrayList<>()));
            
            int extracted = 0;
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                
                for (Map.Entry<String, List<FeatureInfo>> entry : cdsGroups.entrySet()) {
                    String transcriptId = entry.getKey();
                    List<FeatureInfo> cdsList = entry.getValue();
                    
                    // Sort and concatenate CDS
                    cdsList.sort((a, b) -> Integer.compare(a.getStart(), b.getStart()));
                    StringBuilder cdsSequence = new StringBuilder();
                    for (FeatureInfo cds : cdsList) {
                        String sequence = extractSequence(genomeSeqs, cds);
                        if (sequence != null) {
                            cdsSequence.append(sequence);
                        }
                    }
                    
                    // Translate to protein
                    if (cdsSequence.length() > 0) {
                        String proteinSeq = translateSequence(cdsSequence.toString());
                        if (!proteinSeq.isEmpty()) {
                            writeSequence(writer, transcriptId + "_protein", proteinSeq, "protein");
                            extracted++;
                        }
                    }
                }
            }
            
            ExtractionResult result = new ExtractionResult(true, "Protein extraction completed");
            result.setSequencesExtracted(extracted);
            return result;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error extracting proteins", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }

    /**
     * Extract promoter sequences (upstream regions of ATG start codon)
     *
     * @param genomeFile Genome FASTA file
     * @param annotationFile GFF3/GTF annotation file
     * @param outputFile Output FASTA file for promoters
     * @param upstreamLength Length of upstream region to extract (in bp)
     * @return Extraction result with statistics
     */
    public static ExtractionResult extractPromoters(File genomeFile, File annotationFile,
                                                    File outputFile, int upstreamLength) {
        try {
            logger.info("Extracting promoter sequences (upstream of ATG: " + upstreamLength + " bp)...");

            // Load genome sequences
            Map<String, String> genomeSeqs = loadGenomeSequences(genomeFile);
            logger.info("Loaded " + genomeSeqs.size() + " chromosome sequences");

            // Parse ALL features to find CDS positions for each gene
            Map<String, List<FeatureInfo>> features = parseAnnotationFile(annotationFile);
            List<FeatureInfo> cdsFeatures = features.getOrDefault("CDS", new ArrayList<>());

            // Group CDS by parent gene/transcript to find ATG position
            Map<String, List<FeatureInfo>> cdsByGene = groupCDSByGene(cdsFeatures);
            logger.info("Parsed " + cdsByGene.size() + " genes with CDS features");

            // Extract promoter sequences
            int extractedCount = 0;
            int skippedCount = 0;

            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                for (Map.Entry<String, List<FeatureInfo>> entry : cdsByGene.entrySet()) {
                    String geneId = entry.getKey();
                    List<FeatureInfo> cdsList = entry.getValue();

                    if (cdsList.isEmpty()) {
                        skippedCount++;
                        continue;
                    }

                    // Sort CDS by position to find the first one (ATG location)
                    cdsList.sort((a, b) -> Integer.compare(a.getStart(), b.getStart()));

                    FeatureInfo firstCds = cdsList.get(0);
                    String chrSeq = genomeSeqs.get(firstCds.getSeqId());
                    if (chrSeq == null) {
                        logger.warning("Chromosome not found for gene: " + geneId);
                        skippedCount++;
                        continue;
                    }

                    // Calculate promoter region coordinates based on ATG position
                    int promoterStart, promoterEnd;
                    int atgPosition;

                    if ("+".equals(firstCds.getStrand())) {
                        // Forward strand: ATG is at CDS start, promoter is BEFORE (upstream = lower coordinates)
                        atgPosition = firstCds.getStart();
                        promoterEnd = atgPosition - 1;  // Exclusive of ATG
                        promoterStart = Math.max(1, promoterEnd - upstreamLength + 1);
                    } else {
                        // Reverse strand: ATG is at CDS end (in genomic sense), promoter is AFTER (higher coordinates)
                        // But biologically upstream - needs reverse complement
                        atgPosition = cdsList.get(cdsList.size() - 1).getEnd();  // Last CDS end
                        promoterStart = atgPosition + 1;  // Exclusive of ATG
                        promoterEnd = Math.min(chrSeq.length(), promoterStart + upstreamLength - 1);
                    }

                    // Validate coordinates
                    if (promoterStart < 1 || promoterEnd > chrSeq.length() || promoterStart > promoterEnd) {
                        logger.warning("Invalid promoter coordinates for gene: " + geneId +
                                     " (ATG=" + atgPosition + ", region=" + promoterStart + "-" + promoterEnd + ")");
                        skippedCount++;
                        continue;
                    }

                    // Extract sequence (1-based to 0-based indexing)
                    String promoterSeq = chrSeq.substring(promoterStart - 1, promoterEnd);

                    // Reverse complement for reverse strand (biological upstream)
                    if ("-".equals(firstCds.getStrand())) {
                        promoterSeq = reverseComplement(promoterSeq);
                    }

                    // Write to output
                    writer.println(">" + geneId + " promoter upstream=" + upstreamLength +
                                 " chr=" + firstCds.getSeqId() + " strand=" + firstCds.getStrand() +
                                 " atg=" + atgPosition + " region=" + promoterStart + "-" + promoterEnd);
                    writer.println(promoterSeq);
                    extractedCount++;
                }
            }

            String message = "Extracted " + extractedCount + " promoter sequences";
            if (skippedCount > 0) {
                message += " (" + skippedCount + " skipped)";
            }
            logger.info(message);

            ExtractionResult result = new ExtractionResult(true, message);
            result.setSequencesExtracted(extractedCount);
            return result;

        } catch (Exception e) {
            String errorMsg = "Failed to extract promoters: " + e.getMessage();
            logger.log(Level.SEVERE, errorMsg, e);
            return new ExtractionResult(false, errorMsg);
        }
    }

    /**
     * Group CDS features by transcript ID
     */
    private static Map<String, List<FeatureInfo>> groupCDSByTranscript(List<FeatureInfo> cdsList) {
        Map<String, List<FeatureInfo>> groups = new HashMap<>();
        
        for (FeatureInfo cds : cdsList) {
            String key = cds.getTranscriptId();
            if (key == null || key.isEmpty()) {
                key = cds.getGeneId(); // Fallback to gene ID
            }
            if (key == null || key.isEmpty()) {
                key = "unknown_" + cds.getSeqId() + "_" + cds.getStart(); // Last resort
            }
            
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(cds);
        }
        
        return groups;
    }
    
    /**
     * Group CDS features by their parent gene/transcript ID
     * This helps find the ATG (start codon) position for promoter extraction
     */
    private static Map<String, List<FeatureInfo>> groupCDSByGene(List<FeatureInfo> cdsFeatures) {
        Map<String, List<FeatureInfo>> grouped = new HashMap<>();

        for (FeatureInfo cds : cdsFeatures) {
            // Try to get gene ID first (preferred for promoter extraction)
            String geneId = cds.getGeneId();
            if (geneId == null || geneId.isEmpty()) {
                // Fallback to transcript ID if gene ID not available
                geneId = cds.getTranscriptId();
            }
            if (geneId == null || geneId.isEmpty()) {
                // Last resort: use Parent attribute from GFF3
                geneId = cds.getAttribute("Parent");
            }

            if (geneId != null && !geneId.isEmpty()) {
                grouped.computeIfAbsent(geneId, k -> new ArrayList<>()).add(cds);
            } else {
                // Skip CDS features without identifiable parent
                logger.warning("CDS feature without gene/transcript ID: " +
                             cds.getSeqId() + ":" + cds.getStart() + "-" + cds.getEnd());
            }
        }

        return grouped;
    }

    /**
     * Public method to extract sequence for a feature from genome
     */
    public static String extractFeatureSequence(Map<String, String> genomeSeqs, FeatureInfo feature) {
        return extractSequence(genomeSeqs, feature);
    }
    
    /**
     * Extract sequence for a feature from genome
     */
    private static String extractSequence(Map<String, String> genomeSeqs, FeatureInfo feature) {
        String seqId = feature.getSeqId();
        String genomeSeq = genomeSeqs.get(seqId);
        
        if (genomeSeq == null) {
            logger.warning("Sequence not found: " + seqId);
            return null;
        }
        
        int start = Math.min(feature.getStart(), feature.getEnd()) - 1; // Convert to 0-based
        int end = Math.max(feature.getStart(), feature.getEnd());
        
        if (start < 0 || end > genomeSeq.length()) {
            logger.warning("Coordinates out of range: " + seqId + ":" + (start+1) + "-" + end);
            return null;
        }
        
        String sequence = genomeSeq.substring(start, end);
        
        // Reverse complement for negative strand
        if ("-".equals(feature.getStrand())) {
            sequence = reverseComplement(sequence);
        }
        
        return sequence;
    }
    
    /**
     * Reverse complement DNA sequence
     */
    private static String reverseComplement(String sequence) {
        StringBuilder sb = new StringBuilder();
        for (int i = sequence.length() - 1; i >= 0; i--) {
            char base = sequence.charAt(i);
            switch (base) {
                case 'A': sb.append('T'); break;
                case 'T': sb.append('A'); break;
                case 'G': sb.append('C'); break;
                case 'C': sb.append('G'); break;
                case 'N': sb.append('N'); break;
                default: sb.append(base); break;
            }
        }
        return sb.toString();
    }
    
    /**
     * Translate DNA sequence to protein (public method)
     */
    public static String translateToProtein(String dnaSequence) {
        return translateSequence(dnaSequence);
    }
    
    /**
     * Translate DNA sequence to protein
     */
    private static String translateSequence(String dnaSequence) {
        StringBuilder protein = new StringBuilder();
        
        for (int i = 0; i < dnaSequence.length() - 2; i += 3) {
            String codon = dnaSequence.substring(i, i + 3).toUpperCase();
            String aminoAcid = GENETIC_CODE.getOrDefault(codon, "X");
            protein.append(aminoAcid);
            
            // Stop at stop codon
            if ("*".equals(aminoAcid)) {
                break;
            }
        }
        
        return protein.toString();
    }
    
    /**
     * Write sequence to output file with simplified ID format
     */
    private static void writeSequence(PrintWriter writer, String id, String sequence, String type) {
        // Clean up the ID - remove complex suffixes and redundancy
        String cleanId = cleanSequenceId(id, type);
        writer.println(">" + cleanId + " [" + type + " length=" + sequence.length() + "]");
        
        // Write sequence in lines of 80 characters
        for (int i = 0; i < sequence.length(); i += 80) {
            writer.println(sequence.substring(i, Math.min(i + 80, sequence.length())));
        }
    }
    
    /**
     * Clean sequence ID to remove redundancy and complexity
     */
    private static String cleanSequenceId(String originalId, String type) {
        if (originalId == null || originalId.isEmpty()) {
            return "unknown_" + type;
        }
        
        String cleanId = originalId;
        
        // For CDS: remove complex suffixes but keep transcript isoform info
        if (type.equals("CDS")) {
            // Remove "_CDS" suffix if present
            if (cleanId.endsWith("_CDS")) {
                cleanId = cleanId.substring(0, cleanId.length() - 4);
            }
            // Remove protein ID and complex parts, keep only the transcript ID
            if (cleanId.contains(",")) {
                cleanId = cleanId.split(",")[0];  // Take first part before comma
            }
            if (cleanId.contains("-")) {
                String[] parts = cleanId.split("-");
                cleanId = parts[0];  // Take gene/transcript ID part
            }
        }
        
        // For transcripts: ensure we have proper isoform notation
        if (type.equals("transcript")) {
            // If the transcript ID doesn't have isoform suffix (.1, .2, etc.) but should have
            if (!cleanId.matches(".*\\.[0-9]+$") && !cleanId.contains(".")) {
                // Check if there's a number or letter at the end that could be isoform
                if (cleanId.matches(".*[0-9A-Za-z]$")) {
                    // Keep as is - might already have isoform info
                } else {
                    // Add default isoform suffix
                    cleanId = cleanId + ".1";
                }
            }
        }
        
        // For proteins: use transcript ID without complex suffixes
        if (type.equals("protein")) {
            if (cleanId.endsWith("_protein")) {
                cleanId = cleanId.substring(0, cleanId.length() - 8);
            }
            // Keep consistent with CDS naming
            if (cleanId.contains(",")) {
                cleanId = cleanId.split(",")[0];
            }
            if (cleanId.contains("-")) {
                String[] parts = cleanId.split("-");
                cleanId = parts[0];
            }
        }
        
        return cleanId;
    }
    
    /**
     * Create standard genetic code table
     */
    private static Map<String, String> createGeneticCodeTable() {
        Map<String, String> code = new HashMap<>();
        
        // Standard genetic code
        code.put("TTT", "F"); code.put("TTC", "F"); code.put("TTA", "L"); code.put("TTG", "L");
        code.put("TCT", "S"); code.put("TCC", "S"); code.put("TCA", "S"); code.put("TCG", "S");
        code.put("TAT", "Y"); code.put("TAC", "Y"); code.put("TAA", "*"); code.put("TAG", "*");
        code.put("TGT", "C"); code.put("TGC", "C"); code.put("TGA", "*"); code.put("TGG", "W");
        
        code.put("CTT", "L"); code.put("CTC", "L"); code.put("CTA", "L"); code.put("CTG", "L");
        code.put("CCT", "P"); code.put("CCC", "P"); code.put("CCA", "P"); code.put("CCG", "P");
        code.put("CAT", "H"); code.put("CAC", "H"); code.put("CAA", "Q"); code.put("CAG", "Q");
        code.put("CGT", "R"); code.put("CGC", "R"); code.put("CGA", "R"); code.put("CGG", "R");
        
        code.put("ATT", "I"); code.put("ATC", "I"); code.put("ATA", "I"); code.put("ATG", "M");
        code.put("ACT", "T"); code.put("ACC", "T"); code.put("ACA", "T"); code.put("ACG", "T");
        code.put("AAT", "N"); code.put("AAC", "N"); code.put("AAA", "K"); code.put("AAG", "K");
        code.put("AGT", "S"); code.put("AGC", "S"); code.put("AGA", "R"); code.put("AGG", "R");
        
        code.put("GTT", "V"); code.put("GTC", "V"); code.put("GTA", "V"); code.put("GTG", "V");
        code.put("GCT", "A"); code.put("GCC", "A"); code.put("GCA", "A"); code.put("GCG", "A");
        code.put("GAT", "D"); code.put("GAC", "D"); code.put("GAA", "E"); code.put("GAG", "E");
        code.put("GGT", "G"); code.put("GGC", "G"); code.put("GGA", "G"); code.put("GGG", "G");
        
        return code;
    }
    
    /**
     * Extract representative CDS sequences only
     */
    public static ExtractionResult extractRepresentativeCDS(File genomeFile, File annotationFile, File outputFile, String criteria) {
        try {
            Map<String, String> genomeSeqs = loadGenomeSequences(genomeFile);
            
            // Create temporary representative annotation file
            File tempGffFile = File.createTempFile("temp_representative_cds", ".gff3");
            tempGffFile.deleteOnExit();
            
            // Convert string criteria to enum
            RepresentativeTranscriptSelector.SelectionCriteria enumCriteria;
            switch (criteria) {
                case "LONGEST_TRANSCRIPT":
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.LONGEST_TRANSCRIPT;
                    break;
                case "LONGEST_CDS":
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.LONGEST_CDS;
                    break;
                case "HIGHEST_EXPRESSION":
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.MOST_EXONS;
                    break;
                default:
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.CANONICAL;
                    break;
            }
            
            // Generate representative annotation to temp file
            if (!RepresentativeTranscriptSelector.generateRepresentativeAnnotation(annotationFile, tempGffFile, enumCriteria)) {
                throw new IOException("Failed to generate representative transcripts");
            }
            
            // Parse representative features and extract CDS
            Map<String, List<FeatureInfo>> repFeatures = parseAnnotationFile(tempGffFile);
            Map<String, List<FeatureInfo>> cdsGroups = groupCDSByTranscript(repFeatures.getOrDefault("CDS", new ArrayList<>()));
            
            int extracted = 0;
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                for (Map.Entry<String, List<FeatureInfo>> entry : cdsGroups.entrySet()) {
                    String transcriptId = entry.getKey();
                    List<FeatureInfo> cdsList = entry.getValue();
                    
                    // Sort and concatenate CDS
                    cdsList.sort((a, b) -> Integer.compare(a.getStart(), b.getStart()));
                    StringBuilder cdsSequence = new StringBuilder();
                    for (FeatureInfo cds : cdsList) {
                        String sequence = extractSequence(genomeSeqs, cds);
                        if (sequence != null) {
                            cdsSequence.append(sequence);
                        }
                    }
                    
                    if (cdsSequence.length() > 0) {
                        writeSequence(writer, transcriptId + "_cds", cdsSequence.toString(), "cds");
                        extracted++;
                    }
                }
            }
            
            ExtractionResult result = new ExtractionResult(true, "Representative CDS extraction completed");
            result.setSequencesExtracted(extracted);
            return result;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error extracting representative CDS", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Extract representative protein sequences only
     */
    public static ExtractionResult extractRepresentativeProteins(File genomeFile, File annotationFile, File outputFile, String criteria) {
        try {
            Map<String, String> genomeSeqs = loadGenomeSequences(genomeFile);
            
            // Create temporary representative annotation file
            File tempGffFile = File.createTempFile("temp_representative_proteins", ".gff3");
            tempGffFile.deleteOnExit();
            
            // Convert string criteria to enum
            RepresentativeTranscriptSelector.SelectionCriteria enumCriteria;
            switch (criteria) {
                case "LONGEST_TRANSCRIPT":
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.LONGEST_TRANSCRIPT;
                    break;
                case "LONGEST_CDS":
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.LONGEST_CDS;
                    break;
                case "HIGHEST_EXPRESSION":
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.MOST_EXONS;
                    break;
                default:
                    enumCriteria = RepresentativeTranscriptSelector.SelectionCriteria.CANONICAL;
                    break;
            }
            
            // Generate representative annotation to temp file
            if (!RepresentativeTranscriptSelector.generateRepresentativeAnnotation(annotationFile, tempGffFile, enumCriteria)) {
                throw new IOException("Failed to generate representative transcripts");
            }
            
            // Parse representative features and extract proteins
            Map<String, List<FeatureInfo>> repFeatures = parseAnnotationFile(tempGffFile);
            Map<String, List<FeatureInfo>> cdsGroups = groupCDSByTranscript(repFeatures.getOrDefault("CDS", new ArrayList<>()));
            
            int extracted = 0;
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                for (Map.Entry<String, List<FeatureInfo>> entry : cdsGroups.entrySet()) {
                    String transcriptId = entry.getKey();
                    List<FeatureInfo> cdsList = entry.getValue();
                    
                    // Sort and concatenate CDS
                    cdsList.sort((a, b) -> Integer.compare(a.getStart(), b.getStart()));
                    StringBuilder cdsSequence = new StringBuilder();
                    for (FeatureInfo cds : cdsList) {
                        String sequence = extractSequence(genomeSeqs, cds);
                        if (sequence != null) {
                            cdsSequence.append(sequence);
                        }
                    }
                    
                    // Translate to protein
                    if (cdsSequence.length() > 0) {
                        String proteinSeq = translateSequence(cdsSequence.toString());
                        if (!proteinSeq.isEmpty()) {
                            writeSequence(writer, transcriptId + "_protein", proteinSeq, "protein");
                            extracted++;
                        }
                    }
                }
            }
            
            ExtractionResult result = new ExtractionResult(true, "Representative protein extraction completed");
            result.setSequencesExtracted(extracted);
            return result;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error extracting representative proteins", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }
}