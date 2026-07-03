/*
 * Representative Transcript Selector
 */
package simplegenomehub.util.fileio;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Selects representative transcripts from gene models
 * Based on TBtools GXFToRepresentativeGXF patterns
 * 
 * @author SimpleGenomeHub
 */
public class RepresentativeTranscriptSelector {
    
    private static final Logger logger = Logger.getLogger(RepresentativeTranscriptSelector.class.getName());
    
    /**
     * Selection criteria for representative transcripts
     */
    public enum SelectionCriteria {
        LONGEST_TRANSCRIPT,    // Select longest transcript per gene
        LONGEST_CDS,           // Select transcript with longest CDS
        MOST_EXONS,            // Select transcript with most exons
        CANONICAL              // Select canonical transcript (if annotated)
    }
    
    /**
     * Gene model with all its transcripts
     */
    public static class GeneModel {
        private String geneId;
        private String seqId;
        private int start;
        private int end;
        private String strand;
        private List<TranscriptModel> transcripts;
        private Map<String, String> attributes;
        
        public GeneModel(String geneId, String seqId, int start, int end, String strand) {
            this.geneId = geneId;
            this.seqId = seqId;
            this.start = start;
            this.end = end;
            this.strand = strand;
            this.transcripts = new ArrayList<>();
            this.attributes = new HashMap<>();
        }
        
        // Getters and methods
        public String getGeneId() { return geneId; }
        public String getSeqId() { return seqId; }
        public int getStart() { return start; }
        public int getEnd() { return end; }
        public String getStrand() { return strand; }
        public List<TranscriptModel> getTranscripts() { return transcripts; }
        public Map<String, String> getAttributes() { return attributes; }
        
        public void addTranscript(TranscriptModel transcript) { transcripts.add(transcript); }
        public void setAttribute(String key, String value) { attributes.put(key, value); }
        
        public TranscriptModel getRepresentativeTranscript(SelectionCriteria criteria) {
            if (transcripts.isEmpty()) return null;
            if (transcripts.size() == 1) return transcripts.get(0);
            
            switch (criteria) {
                case LONGEST_TRANSCRIPT:
                    return transcripts.stream()
                        .max(Comparator.comparingInt(t -> t.getTranscriptLength()))
                        .orElse(transcripts.get(0));
                        
                case LONGEST_CDS:
                    return transcripts.stream()
                        .max(Comparator.comparingInt(t -> t.getCdsLength()))
                        .orElse(transcripts.get(0));
                        
                case MOST_EXONS:
                    return transcripts.stream()
                        .max(Comparator.comparingInt(t -> t.getExons().size()))
                        .orElse(transcripts.get(0));
                        
                case CANONICAL:
                    // Look for canonical transcript markers
                    Optional<TranscriptModel> canonical = transcripts.stream()
                        .filter(t -> isCanonicalTranscript(t))
                        .findFirst();
                    return canonical.orElse(transcripts.get(0));
                        
                default:
                    return transcripts.get(0);
            }
        }
        
        private boolean isCanonicalTranscript(TranscriptModel transcript) {
            // Check for canonical markers in transcript ID or attributes
            String id = transcript.getTranscriptId().toLowerCase();
            return id.contains("canonical") || 
                   id.contains(".1") || 
                   id.endsWith("-ra") ||
                   transcript.getAttributes().containsKey("canonical") ||
                   transcript.getAttributes().containsKey("representative");
        }
    }
    
    /**
     * Transcript model with exons and CDS
     */
    public static class TranscriptModel {
        private String transcriptId;
        private String geneId;
        private String seqId;
        private int start;
        private int end;
        private String strand;
        private List<ExonModel> exons;
        private List<ExonModel> cdsExons;
        private Map<String, String> attributes;
        
        public TranscriptModel(String transcriptId, String geneId, String seqId, 
                             int start, int end, String strand) {
            this.transcriptId = transcriptId;
            this.geneId = geneId;
            this.seqId = seqId;
            this.start = start;
            this.end = end;
            this.strand = strand;
            this.exons = new ArrayList<>();
            this.cdsExons = new ArrayList<>();
            this.attributes = new HashMap<>();
        }
        
        // Getters and methods
        public String getTranscriptId() { return transcriptId; }
        public String getGeneId() { return geneId; }
        public String getSeqId() { return seqId; }
        public int getStart() { return start; }
        public int getEnd() { return end; }
        public String getStrand() { return strand; }
        public List<ExonModel> getExons() { return exons; }
        public List<ExonModel> getCdsExons() { return cdsExons; }
        public Map<String, String> getAttributes() { return attributes; }
        
        public void addExon(ExonModel exon) { exons.add(exon); }
        public void addCdsExon(ExonModel cds) { cdsExons.add(cds); }
        public void setAttribute(String key, String value) { attributes.put(key, value); }
        
        public int getTranscriptLength() {
            return exons.stream().mapToInt(e -> e.getLength()).sum();
        }
        
        public int getCdsLength() {
            return cdsExons.stream().mapToInt(e -> e.getLength()).sum();
        }
    }
    
    /**
     * Exon model
     */
    public static class ExonModel {
        private int start;
        private int end;
        private String phase;
        
        public ExonModel(int start, int end, String phase) {
            this.start = start;
            this.end = end;
            this.phase = phase;
        }
        
        public int getStart() { return start; }
        public int getEnd() { return end; }
        public String getPhase() { return phase; }
        public int getLength() { return Math.abs(end - start) + 1; }
    }
    
    /**
     * Parse annotation file and build gene models
     */
    public static Map<String, GeneModel> parseGeneModels(File annotationFile) throws IOException {
        Map<String, GeneModel> geneModels = new HashMap<>();
        Map<String, TranscriptModel> transcriptModels = new HashMap<>();
        
        logger.info("Parsing gene models from: " + annotationFile.getName());
        
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
                    parseFeatureLine(line, geneModels, transcriptModels);
                } catch (Exception e) {
                    logger.warning("Error parsing line " + lineNumber + ": " + e.getMessage());
                }
            }
        }
        
        // Link transcripts to genes
        for (TranscriptModel transcript : transcriptModels.values()) {
            String geneId = transcript.getGeneId();
            GeneModel gene = geneModels.get(geneId);
            if (gene != null) {
                gene.addTranscript(transcript);
            }
        }
        
        logger.info("Parsed " + geneModels.size() + " genes with " + 
                   transcriptModels.size() + " transcripts");
        
        return geneModels;
    }
    
    /**
     * Parse single feature line and update models
     */
    private static void parseFeatureLine(String line, Map<String, GeneModel> geneModels,
                                       Map<String, TranscriptModel> transcriptModels) {
        String[] fields = line.split("\t");
        if (fields.length < 9) return;
        
        String seqId = fields[0];
        String featureType = fields[2];
        int start = Integer.parseInt(fields[3]);
        int end = Integer.parseInt(fields[4]);
        String strand = fields[6];
        String attributes = fields[8];
        
        Map<String, String> attrMap = parseAttributes(attributes);
        
        switch (featureType.toLowerCase()) {
            case "gene":
                String geneId = getAttributeValue(attrMap, "ID", "gene_id");
                if (geneId != null) {
                    GeneModel gene = new GeneModel(geneId, seqId, start, end, strand);
                    gene.getAttributes().putAll(attrMap);
                    geneModels.put(geneId, gene);
                }
                break;
                
            case "mrna":
            case "transcript":
                String transcriptId = getAttributeValue(attrMap, "ID", "transcript_id");
                String parentGeneId = getAttributeValue(attrMap, "Parent", "gene_id");
                
                if (transcriptId != null && parentGeneId != null) {
                    TranscriptModel transcript = new TranscriptModel(transcriptId, parentGeneId, 
                                                                   seqId, start, end, strand);
                    transcript.getAttributes().putAll(attrMap);
                    transcriptModels.put(transcriptId, transcript);
                }
                break;
                
            case "exon":
                String exonParent = getAttributeValue(attrMap, "Parent", "transcript_id");
                if (exonParent != null) {
                    TranscriptModel transcript = transcriptModels.get(exonParent);
                    if (transcript != null) {
                        transcript.addExon(new ExonModel(start, end, ""));
                    }
                }
                break;
                
            case "cds":
            case "CDS":
                String cdsParent = getAttributeValue(attrMap, "Parent", "transcript_id");
                String phase = fields.length > 7 ? fields[7] : "0";
                
                if (cdsParent != null) {
                    // Handle multiple parents separated by comma (e.g., "AT1G01010.1,AT1G01010.1-Protein")
                    // Take the first one which should be the transcript ID
                    if (cdsParent.contains(",")) {
                        cdsParent = cdsParent.split(",")[0].trim();
                    }
                    
                    TranscriptModel transcript = transcriptModels.get(cdsParent);
                    if (transcript != null) {
                        transcript.addCdsExon(new ExonModel(start, end, phase));
                    }
                }
                break;
        }
    }
    
    /**
     * Parse attributes string into map
     */
    private static Map<String, String> parseAttributes(String attributes) {
        Map<String, String> attrMap = new HashMap<>();
        
        if (attributes.contains("=")) {
            // GFF3 format
            for (String pair : attributes.split(";")) {
                String[] kv = pair.trim().split("=", 2);
                if (kv.length == 2) {
                    attrMap.put(kv[0].trim(), kv[1].trim());
                }
            }
        } else {
            // GTF format
            for (String pair : attributes.split(";")) {
                pair = pair.trim();
                if (pair.contains(" \"")) {
                    String[] kv = pair.split(" \"", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim();
                        String value = kv[1].replace("\"", "").trim();
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
     * Generate representative GFF3/GTF file
     */
    public static boolean generateRepresentativeAnnotation(File inputFile, File outputFile, 
                                                         SelectionCriteria criteria) {
        logger.info("Generating representative annotation using " + criteria + " criteria");
        
        try {
            Map<String, GeneModel> geneModels = parseGeneModels(inputFile);
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
                // Write header
                writer.println("##gff-version 3");
                writer.println("# Representative transcript annotation generated by SimpleGenomeHub");
                writer.println("# Selection criteria: " + criteria);
                
                // Process each gene
                for (GeneModel gene : geneModels.values()) {
                    TranscriptModel representative = gene.getRepresentativeTranscript(criteria);
                    
                    if (representative != null) {
                        writeGeneFeatures(writer, gene, representative);
                    }
                }
            }
            
            logger.info("Representative annotation generated: " + outputFile.getName());
            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error generating representative annotation", e);
            return false;
        }
    }
    
    /**
     * Write gene features to GFF3 format
     */
    private static void writeGeneFeatures(PrintWriter writer, GeneModel gene, TranscriptModel transcript) {
        // Write gene feature
        writer.printf("%s\t%s\tgene\t%d\t%d\t.\t%s\t.\tID=%s%s%n",
                     gene.getSeqId(), "SimpleGenomeHub", gene.getStart(), gene.getEnd(), 
                     gene.getStrand(), gene.getGeneId(),
                     formatAttributes(gene.getAttributes(), "ID"));
        
        // Write transcript feature
        writer.printf("%s\t%s\tmRNA\t%d\t%d\t.\t%s\t.\tID=%s;Parent=%s%s%n",
                     transcript.getSeqId(), "SimpleGenomeHub", 
                     transcript.getStart(), transcript.getEnd(), transcript.getStrand(),
                     transcript.getTranscriptId(), gene.getGeneId(),
                     formatAttributes(transcript.getAttributes(), "ID", "Parent"));
        
        // Write exons
        List<ExonModel> exons = new ArrayList<>(transcript.getExons());
        exons.sort(Comparator.comparingInt(ExonModel::getStart));
        
        for (int i = 0; i < exons.size(); i++) {
            ExonModel exon = exons.get(i);
            writer.printf("%s\t%s\texon\t%d\t%d\t.\t%s\t.\tID=%s.exon%d;Parent=%s%n",
                         transcript.getSeqId(), "SimpleGenomeHub",
                         exon.getStart(), exon.getEnd(), transcript.getStrand(),
                         transcript.getTranscriptId(), i + 1, transcript.getTranscriptId());
        }
        
        // Write CDS
        List<ExonModel> cdsExons = new ArrayList<>(transcript.getCdsExons());
        if (!cdsExons.isEmpty()) {
            cdsExons.sort(Comparator.comparingInt(ExonModel::getStart));
            
            for (ExonModel cds : cdsExons) {
                writer.printf("%s\t%s\tCDS\t%d\t%d\t.\t%s\t%s\tParent=%s%n",
                             transcript.getSeqId(), "SimpleGenomeHub",
                             cds.getStart(), cds.getEnd(), transcript.getStrand(),
                             cds.getPhase(), transcript.getTranscriptId());
            }
        }
    }
    
    /**
     * Format additional attributes for GFF3 output
     */
    private static String formatAttributes(Map<String, String> attributes, String... excludeKeys) {
        Set<String> exclude = new HashSet<>(Arrays.asList(excludeKeys));
        StringBuilder sb = new StringBuilder();
        
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (!exclude.contains(entry.getKey())) {
                if (sb.length() > 0) sb.append(";");
                sb.append(";").append(entry.getKey()).append("=").append(entry.getValue());
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Get selection statistics
     */
    public static Map<String, Object> getSelectionStatistics(Map<String, GeneModel> geneModels, 
                                                            SelectionCriteria criteria) {
        Map<String, Object> stats = new HashMap<>();
        
        int totalGenes = geneModels.size();
        int totalTranscripts = geneModels.values().stream()
                               .mapToInt(g -> g.getTranscripts().size()).sum();
        int selectedTranscripts = totalGenes; // One per gene
        
        // Calculate transcript length statistics
        List<Integer> lengths = new ArrayList<>();
        List<Integer> cdsLengths = new ArrayList<>();
        
        for (GeneModel gene : geneModels.values()) {
            TranscriptModel representative = gene.getRepresentativeTranscript(criteria);
            if (representative != null) {
                lengths.add(representative.getTranscriptLength());
                cdsLengths.add(representative.getCdsLength());
            }
        }
        
        stats.put("totalGenes", totalGenes);
        stats.put("totalTranscripts", totalTranscripts);
        stats.put("selectedTranscripts", selectedTranscripts);
        stats.put("selectionCriteria", criteria.toString());
        
        if (!lengths.isEmpty()) {
            stats.put("averageTranscriptLength", lengths.stream().mapToInt(i -> i).average().orElse(0));
            stats.put("averageCdsLength", cdsLengths.stream().mapToInt(i -> i).average().orElse(0));
        }
        
        return stats;
    }
}