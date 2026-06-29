/*
 * Gene Functional Annotation Data Model
 * Integrates with TBtools GO/KEGG processing capabilities
 */
package simplegenomehub.model;

import biocjava.bioIO.GeneOntology.EnrichMent.GOTermEnrichment;

import biocjava.bioIO.GeneOntology.EnrichMent.SimpleEnricher;
import biocjava.bioDoer.GeneOntology.Annotation.GoAnnoPipe;
import biocjava.bioDoer.Kegg.AdvancedForEnrichment.KeggEnrichment;
import biocjava.bioDoer.Kegg.GetGene;
import biocjava.bioDoer.Kegg.GeneEntry;
import toolsKit.FileReader.BioFileReader;
import simplegenomehub.util.fileio.TranscriptToGeneMapper;
import simplegenomehub.util.fileio.GOOboParser;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Gene functional annotation data management
 * Supports GO, KEGG, and custom annotation formats
 * 
 * @author SimpleGenomeHub
 */
public class GeneAnnotationData {
    
    private static final Logger logger = Logger.getLogger(GeneAnnotationData.class.getName());
    
    public enum AnnotationType {
        GO("GO", "Gene Ontology"),
        KEGG("KEGG", "KEGG Pathway"),
        INTERPRO("InterPro", "InterPro Domain"),
        PFAM("Pfam", "Protein Family"),
        CUSTOM("Custom", "Custom Annotation");
        
        private final String shortName;
        private final String description;
        
        AnnotationType(String shortName, String description) {
            this.shortName = shortName;
            this.description = description;
        }
        
        public String getShortName() { return shortName; }
        public String getDescription() { return description; }
    }
    
    /**
     * Single gene annotation entry
     */
    public static class GeneAnnotation {
        private String geneId;
        private String annotationId;
        private String term;
        private String description;
        private AnnotationType type;
        private String evidence;
        private double score;
        private Map<String, String> attributes;
        
        // For supporting dynamic columns (especially for custom annotations)
        private String[] columnValues;
        private String[] columnHeaders;
        
        public GeneAnnotation(String geneId, String annotationId, String term, AnnotationType type) {
            this.geneId = geneId;
            this.annotationId = annotationId;
            this.term = term;
            this.type = type;
            this.attributes = new HashMap<>();
            this.score = 0.0;
        }
        
        /**
         * Constructor for dynamic column annotations (especially Custom)
         */
        public GeneAnnotation(String geneId, AnnotationType type, String[] columnHeaders, String[] columnValues) {
            this.geneId = geneId;
            this.type = type;
            this.columnHeaders = columnHeaders != null ? columnHeaders.clone() : null;
            this.columnValues = columnValues != null ? columnValues.clone() : null;
            this.attributes = new HashMap<>();
            this.score = 0.0;
            
            // For Custom annotations, do NOT set standard fields - use only dynamic data
            // For other types, set standard fields from column values if available
            if (type != AnnotationType.CUSTOM && columnValues != null && columnValues.length > 1) {
                this.annotationId = columnValues[1]; // Second column typically annotation ID
                if (columnValues.length > 2) {
                    this.term = columnValues[2]; // Third column typically term
                }
                if (columnValues.length > 3) {
                    this.description = columnValues[3]; // Fourth column typically description
                }
            } else if (type == AnnotationType.CUSTOM) {
                // For Custom type, set minimal required fields for compatibility
                this.annotationId = "custom_" + System.currentTimeMillis();
                this.term = "";
                this.description = "";
            }
        }
        
        // Getters and setters
        public String getGeneId() { return geneId; }
        public String getAnnotationId() { return annotationId; }
        public String getTerm() { return term; }
        public void setTerm(String term) { this.term = term; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public AnnotationType getType() { return type; }
        public String getEvidence() { return evidence; }
        public void setEvidence(String evidence) { this.evidence = evidence; }
        
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        
        public Map<String, String> getAttributes() { return attributes; }
        public void setAttribute(String key, String value) { attributes.put(key, value); }
        
        // Dynamic column support
        public String[] getColumnHeaders() { return columnHeaders != null ? columnHeaders.clone() : null; }
        public String[] getColumnValues() { return columnValues != null ? columnValues.clone() : null; }
        public void setColumnData(String[] headers, String[] values) { 
            this.columnHeaders = headers != null ? headers.clone() : null;
            this.columnValues = values != null ? values.clone() : null;
        }
        
        /**
         * Get column value by index (0-based)
         */
        public String getColumnValue(int index) {
            if (columnValues != null && index >= 0 && index < columnValues.length) {
                return columnValues[index];
            }
            return "";
        }
        
        /**
         * Get total number of columns for this annotation
         */
        public int getColumnCount() {
            return columnValues != null ? columnValues.length : 0;
        }
        
        @Override
        public String toString() {
            return annotationId + ": " + (term != null ? term : description);
        }
    }
    
    /**
     * Enrichment result for a set of genes
     */
    public static class EnrichmentResult {
        private String termId;
        private String termName;
        private String category;
        private int geneCount;
        private int backgroundCount;
        private double pValue;
        private double adjustedPValue;
        private double enrichmentRatio;
        private List<String> geneIds;
        
        public EnrichmentResult(String termId, String termName, String category) {
            this.termId = termId;
            this.termName = termName;
            this.category = category;
            this.geneIds = new ArrayList<>();
        }
        
        // Getters and setters
        public String getTermId() { return termId; }
        public String getTermName() { return termName; }
        public String getCategory() { return category; }
        
        public int getGeneCount() { return geneCount; }
        public void setGeneCount(int geneCount) { this.geneCount = geneCount; }
        
        public int getBackgroundCount() { return backgroundCount; }
        public void setBackgroundCount(int backgroundCount) { this.backgroundCount = backgroundCount; }
        
        public double getPValue() { return pValue; }
        public void setPValue(double pValue) { this.pValue = pValue; }
        
        public double getAdjustedPValue() { return adjustedPValue; }
        public void setAdjustedPValue(double adjustedPValue) { this.adjustedPValue = adjustedPValue; }
        
        public double getEnrichmentRatio() { return enrichmentRatio; }
        public void setEnrichmentRatio(double enrichmentRatio) { this.enrichmentRatio = enrichmentRatio; }
        
        public List<String> getGeneIds() { return new ArrayList<>(geneIds); }
        public void addGeneId(String geneId) { this.geneIds.add(geneId); }
        
        public boolean isSignificant(double threshold) {
            return adjustedPValue < threshold;
        }
        
        @Override
        public String toString() {
            return String.format("%s (%s): %.2e, ratio=%.2f, genes=%d", 
                               termName, termId, adjustedPValue, enrichmentRatio, geneCount);
        }
    }
    
    // Core annotation data
    private String datasetId;
    private String name;
    private LocalDateTime importTime;
    private Map<AnnotationType, Map<String, List<GeneAnnotation>>> annotations; // type -> gene -> annotations
    private Map<String, String> termDescriptions; // term ID -> description
    private Map<String, Set<String>> termHierarchy; // parent -> children
    
    // Statistics
    private Map<AnnotationType, Integer> annotationCounts;
    private int totalGenes;
    private int annotatedGenes;
    
    // Column headers for each annotation type
    private Map<AnnotationType, String[]> columnHeaders;
    
    // Store the original source file for Custom annotations (for direct copying)
    private File originalSourceFile;
    
    /**
     * Constructor
     */
    public GeneAnnotationData(String datasetId, String name) {
        this.datasetId = datasetId;
        this.name = name;
        this.importTime = LocalDateTime.now();
        this.annotations = new HashMap<>();
        this.termDescriptions = new HashMap<>();
        this.termHierarchy = new HashMap<>();
        this.annotationCounts = new HashMap<>();
        this.columnHeaders = new HashMap<>();
        
        // Initialize annotation type maps
        for (AnnotationType type : AnnotationType.values()) {
            annotations.put(type, new HashMap<>());
            annotationCounts.put(type, 0);
            
            // Set default column headers for each type
            switch (type) {
                case GO:
                    columnHeaders.put(type, new String[]{"Gene_ID", "GO_ID", "Term", "Description"});
                    break;
                case KEGG:
                    columnHeaders.put(type, new String[]{"Gene_ID", "KEGG_ID", "Term", "Description"});
                    break;
                case INTERPRO:
                    columnHeaders.put(type, new String[]{"Gene_ID", "InterPro_ID", "Term", "Description", "Type"});
                    break;
                case PFAM:
                    columnHeaders.put(type, new String[]{"Gene_ID", "Pfam_ID", "Term", "Description", "Domain"});
                    break;
                case CUSTOM:
                    columnHeaders.put(type, new String[]{"Gene_ID", "Annotation_ID", "Term", "Description", "Class"});
                    break;
            }
        }
    }
    
    /**
     * Load GO annotations from file using TBtools GoAnnoPipe
     */
    public boolean loadGOAnnotations(File file) {
        try {
            GoAnnoPipe goPipe = new GoAnnoPipe();
            
            // Use TBtools BioFileReader for robust file parsing
            BioFileReader bio = new BioFileReader(file);
            String line;
            int lineNum = 0;
            
            while ((line = bio.readLine()) != null) {
                lineNum++;
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;  // 只需要至少2列：geneId和goId
                
                String geneId = parts[0].trim();
                String goId = parts[1].trim();
                
                // Support both old format and new format with Class column
                String term = "";
                String description = "";
                String classOrEvidence = "";
                
                if (parts.length >= 3) {
                    term = parts[2].trim();
                }
                if (parts.length >= 4) {
                    description = parts[3].trim();
                }
                if (parts.length >= 5) {
                    classOrEvidence = parts[4].trim();
                }
                
                GeneAnnotation annotation = new GeneAnnotation(geneId, goId, term, AnnotationType.GO);
                annotation.setDescription(description.isEmpty() ? term : description);
                
                // Handle Class/Evidence column
                if (!classOrEvidence.isEmpty()) {
                    // For GO annotations, store namespace information
                    if (classOrEvidence.equals("biological_process") || 
                        classOrEvidence.equals("molecular_function") || 
                        classOrEvidence.equals("cellular_component")) {
                        annotation.setAttribute("go_namespace", classOrEvidence);
                        annotation.setEvidence(getEvidenceFromNamespace(classOrEvidence));
                    } else {
                        // Legacy format or other evidence codes
                        annotation.setEvidence(classOrEvidence);
                        if (classOrEvidence.equals("BP") || classOrEvidence.equals("MF") || classOrEvidence.equals("CC")) {
                            annotation.setAttribute("go_namespace", getNamespaceFromEvidence(classOrEvidence));
                        }
                    }
                }
                
                addAnnotation(annotation);
            }
            bio.close();
            
            updateStatistics();
            logger.info("Loaded GO annotations from: " + file.getName() + 
                       " (" + annotationCounts.get(AnnotationType.GO) + " annotations)");
            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load GO annotations from " + file, e);
            return false;
        }
    }
    
    /**
     * Load KEGG annotations from file
     */
    public boolean loadKEGGAnnotations(File file) {
        try {
            BioFileReader bio = new BioFileReader(file);
            String line;
            
            while ((line = bio.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;  // 只需要至少2列：geneId和keggId
                
                String geneId = parts[0].trim();
                String keggId = parts[1].trim();
                String term = parts.length > 2 ? parts[2].trim() : "";
                String description = parts.length > 3 ? parts[3].trim() : "";
                String evidence = parts.length > 4 ? parts[4].trim() : "";
                
                GeneAnnotation annotation = new GeneAnnotation(geneId, keggId, term, AnnotationType.KEGG);
                annotation.setDescription(description.isEmpty() ? term : description);
                annotation.setEvidence(evidence);
                
                addAnnotation(annotation);
            }
            bio.close();
            
            updateStatistics();
            logger.info("Loaded KEGG annotations from: " + file.getName() + 
                       " (" + annotationCounts.get(AnnotationType.KEGG) + " annotations)");
            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load KEGG annotations from " + file, e);
            return false;
        }
    }
    
    /**
     * Load custom annotations from TSV file
     */
    public boolean loadCustomAnnotations(File file, AnnotationType type) {
        // For Custom annotations, store the original source file for direct copying later
        if (type == AnnotationType.CUSTOM) {
            this.originalSourceFile = file;
        }
        
        try {
            BioFileReader bio = new BioFileReader(file);
            String line;
            String[] fileHeaders = null;
            boolean firstLine = true;
            
            while ((line = bio.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split("\t");
                
                // Handle header line
                if (line.startsWith("#") || firstLine) {
                    if (line.startsWith("#")) {
                        fileHeaders = line.substring(1).split("\t"); // Remove # prefix
                    } else {
                        // Check if first line looks like headers
                        if (parts.length > 1 && parts[0].toLowerCase().contains("gene")) {
                            fileHeaders = parts;
                        } else {
                            // Treat as data line
                            firstLine = false;
                            // Fall through to process as data
                        }
                    }
                    
                    if (fileHeaders != null) {
                        // Trim all headers
                        for (int i = 0; i < fileHeaders.length; i++) {
                            fileHeaders[i] = fileHeaders[i].trim();
                        }
                        // Set the column headers for this annotation type
                        setColumnHeaders(type, fileHeaders);
                        firstLine = false;
                        continue;
                    }
                }
                
                if (parts.length < 2) continue; // At least gene ID and one annotation field
                
                String geneId = parts[0].trim();
                if (geneId.isEmpty()) continue;
                
                // Trim all parts
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].trim();
                    if ("-".equals(parts[i])) {
                        parts[i] = ""; // Convert "-" to empty string
                    }
                }
                
                // Check if this line has any meaningful annotation content (beyond gene ID)
                boolean hasContent = false;
                for (int i = 1; i < parts.length; i++) {
                    if (!parts[i].isEmpty()) {
                        hasContent = true;
                        break;
                    }
                }
                
                if (!hasContent) {
                    continue; // Skip lines with no annotation content
                }
                
                // Create annotation with dynamic column data
                GeneAnnotation annotation;
                if (fileHeaders != null) {
                    annotation = new GeneAnnotation(geneId, type, fileHeaders, parts);
                } else {
                    // Fallback to legacy method if no headers detected
                    String annotationId = parts.length > 1 ? parts[1] : "";
                    String term = parts.length > 2 ? parts[2] : "";
                    String description = parts.length > 3 ? parts[3] : "";
                    
                    // Prioritize Description, then Term, then Annotation_ID
                    String finalDescription = !description.isEmpty() ? description : 
                                             (!term.isEmpty() ? term : annotationId);
                    String finalAnnotationId = !annotationId.isEmpty() ? annotationId : 
                                              (type.getShortName() + "_" + System.currentTimeMillis());
                    
                    annotation = new GeneAnnotation(geneId, finalAnnotationId, finalDescription, type);
                    annotation.setDescription(finalDescription);
                    
                    if (parts.length > 4) {
                        annotation.setEvidence(parts[4]);
                    }
                }
                
                addAnnotation(annotation);
            }
            bio.close();
            
            updateStatistics();
            logger.info("Loaded " + type.getShortName() + " annotations from: " + file.getName() + 
                       " (" + annotationCounts.get(type) + " annotations)");
            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load custom annotations from " + file, e);
            return false;
        }
    }
    
    /**
     * Add single annotation
     */
    public void addAnnotation(GeneAnnotation annotation) {
        if (annotation == null) return;
        
        AnnotationType type = annotation.getType();
        String geneId = annotation.getGeneId();
        
        Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
        typeAnnotations.computeIfAbsent(geneId, k -> new ArrayList<>()).add(annotation);
        
        // Update term descriptions
        if (annotation.getDescription() != null) {
            termDescriptions.put(annotation.getAnnotationId(), annotation.getDescription());
        }
        
        // Update count
        annotationCounts.put(type, annotationCounts.get(type) + 1);
    }
    
    /**
     * Remove specific annotation
     */
    public boolean removeAnnotation(GeneAnnotation annotation) {
        if (annotation == null) return false;
        
        AnnotationType type = annotation.getType();
        String geneId = annotation.getGeneId();
        
        Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
        List<GeneAnnotation> geneAnnotationList = typeAnnotations.get(geneId);
        
        if (geneAnnotationList == null || geneAnnotationList.isEmpty()) {
            return false;
        }
        
        // Find and remove the specific annotation
        boolean removed = geneAnnotationList.removeIf(ann -> 
            ann.getAnnotationId().equals(annotation.getAnnotationId()) &&
            ann.getTerm() != null ? ann.getTerm().equals(annotation.getTerm()) : annotation.getTerm() == null
        );
        
        if (removed) {
            // If no annotations left for this gene, remove the gene entry
            if (geneAnnotationList.isEmpty()) {
                typeAnnotations.remove(geneId);
            }
            
            // Update count
            annotationCounts.put(type, annotationCounts.get(type) - 1);
            
            logger.info("Removed annotation: " + geneId + " -> " + annotation.getAnnotationId());
            return true;
        }
        
        return false;
    }
    
    /**
     * Remove all annotations for a specific gene and type
     */
    public boolean removeGeneAnnotations(String geneId, AnnotationType type) {
        if (geneId == null || type == null) return false;
        
        Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
        List<GeneAnnotation> removed = typeAnnotations.remove(geneId);
        
        if (removed != null && !removed.isEmpty()) {
            // Update count
            annotationCounts.put(type, annotationCounts.get(type) - removed.size());
            logger.info("Removed " + removed.size() + " annotations for gene: " + geneId + " (type: " + type + ")");
            return true;
        }
        
        return false;
    }
    
    /**
     * Clear all annotations of a specific type
     */
    public void clearAnnotationsOfType(AnnotationType type) {
        if (type == null) return;
        
        Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
        int removedCount = annotationCounts.getOrDefault(type, 0);
        
        typeAnnotations.clear();
        annotationCounts.put(type, 0);
        
        // Clear column headers for this type
        columnHeaders.remove(type);
        
        // For Custom annotations, DO NOT clear originalSourceFile here
        // It will be set properly in loadCustomAnnotations method
        // This ensures direct file copying works correctly for Custom imports
        
        logger.info("Cleared all " + type.getShortName() + " annotations (" + removedCount + " annotations removed)");
        updateStatistics();
    }
    
    /**
     * Remove all annotations for a specific gene across all types
     */
    public int removeAllGeneAnnotations(String geneId) {
        if (geneId == null) return 0;
        
        int totalRemoved = 0;
        for (AnnotationType type : AnnotationType.values()) {
            Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
            List<GeneAnnotation> removed = typeAnnotations.remove(geneId);
            
            if (removed != null && !removed.isEmpty()) {
                int count = removed.size();
                totalRemoved += count;
                annotationCounts.put(type, annotationCounts.get(type) - count);
            }
        }
        
        if (totalRemoved > 0) {
            logger.info("Removed " + totalRemoved + " annotations for gene: " + geneId);
        }
        
        return totalRemoved;
    }
    
    /**
     * Remove all annotations of a specific type
     */
    public int removeAllAnnotationsOfType(AnnotationType type) {
        if (type == null) return 0;
        
        Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
        int totalRemoved = annotationCounts.get(type);
        
        // Clear all annotations of this type
        typeAnnotations.clear();
        annotationCounts.put(type, 0);
        
        // Update statistics
        updateStatistics();
        
        logger.info("Removed all " + totalRemoved + " annotations of type: " + type.getShortName());
        return totalRemoved;
    }
    
    /**
     * Clear all annotations (all types)
     */
    public int clearAllAnnotations() {
        int totalRemoved = 0;
        
        for (AnnotationType type : AnnotationType.values()) {
            totalRemoved += annotationCounts.get(type);
            annotations.get(type).clear();
            annotationCounts.put(type, 0);
        }
        
        // Clear additional data
        termDescriptions.clear();
        termHierarchy.clear();
        
        // Update statistics
        updateStatistics();
        
        logger.info("Cleared all " + totalRemoved + " annotations");
        return totalRemoved;
    }
    
    /**
     * Convert transcript-based annotations to gene-based annotations
     * This method tries to map transcript IDs to gene IDs and merge annotations
     */
    public boolean convertTranscriptToGeneAnnotations(File gffFile) {
        TranscriptToGeneMapper mapper = new TranscriptToGeneMapper();
        
        // Load mapping from GFF3 file if available
        if (gffFile != null && gffFile.exists()) {
            mapper.loadFromGFF3(gffFile);
            logger.info("Loaded transcript-gene mapping from GFF3: " + mapper.getStatistics());
        }
        
        int converted = 0;
        int totalOriginal = 0;
        
        // Process each annotation type
        for (AnnotationType type : AnnotationType.values()) {
            Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
            Map<String, List<GeneAnnotation>> newTypeAnnotations = new HashMap<>();
            
            for (Map.Entry<String, List<GeneAnnotation>> entry : typeAnnotations.entrySet()) {
                String originalId = entry.getKey();
                List<GeneAnnotation> annotationList = entry.getValue();
                totalOriginal += annotationList.size();
                
                // Try to map transcript ID to gene ID
                String geneId = mapper.mapTranscriptToGene(originalId);
                
                if (!geneId.equals(originalId)) {
                    converted++;
                    logger.fine("Mapped " + originalId + " -> " + geneId);
                }
                
                // Create new annotations with gene ID
                for (GeneAnnotation originalAnnotation : annotationList) {
                    GeneAnnotation geneAnnotation = new GeneAnnotation(
                        geneId, 
                        originalAnnotation.getAnnotationId(),
                        originalAnnotation.getTerm(),
                        originalAnnotation.getType()
                    );
                    
                    // Copy all other properties
                    geneAnnotation.setDescription(originalAnnotation.getDescription());
                    geneAnnotation.setEvidence(originalAnnotation.getEvidence());
                    geneAnnotation.setScore(originalAnnotation.getScore());
                    
                    // Copy attributes
                    for (Map.Entry<String, String> attr : originalAnnotation.getAttributes().entrySet()) {
                        geneAnnotation.setAttribute(attr.getKey(), attr.getValue());
                    }
                    
                    // Add original transcript ID as an attribute
                    if (!geneId.equals(originalId)) {
                        geneAnnotation.setAttribute("original_transcript_id", originalId);
                    }
                    
                    // Merge with existing gene annotations
                    newTypeAnnotations.computeIfAbsent(geneId, k -> new ArrayList<>()).add(geneAnnotation);
                }
            }
            
            // Replace old annotations with new gene-based annotations
            annotations.put(type, newTypeAnnotations);
        }
        
        // Update statistics
        updateStatistics();
        
        logger.info("Transcript-to-gene conversion completed: " + 
                   "Processed " + totalOriginal + " annotations, " +
                   "converted " + converted + " transcript IDs to gene IDs");
        
        return converted > 0;
    }
    
    /**
     * Enhance GO annotations with term names and descriptions from OBO file
     */
    public boolean enhanceGOAnnotationsWithOBO(File oboFile) {
        if (oboFile == null || !oboFile.exists()) {
            return false;
        }
        
        GOOboParser parser = new GOOboParser();
        if (!parser.parseOboFile(oboFile)) {
            logger.warning("Failed to parse GO OBO file: " + oboFile.getName());
            return false;
        }
        
        int enhanced = 0;
        Map<String, List<GeneAnnotation>> goAnnotations = annotations.get(AnnotationType.GO);
        
        for (Map.Entry<String, List<GeneAnnotation>> entry : goAnnotations.entrySet()) {
            for (GeneAnnotation annotation : entry.getValue()) {
                String goId = annotation.getAnnotationId();
                GOOboParser.GOTerm term = parser.getGoTerm(goId);
                
                if (term != null) {
                    // Update term name
                    if (term.getName() != null && (annotation.getTerm() == null || annotation.getTerm().isEmpty())) {
                        annotation.setTerm(term.getName());
                    }
                    
                    // Update description
                    if (term.getDefinition() != null && (annotation.getDescription() == null || annotation.getDescription().isEmpty())) {
                        annotation.setDescription(term.getDefinition());
                    }
                    
                    // Add namespace as evidence if not already set
                    if (term.getNamespace() != null && (annotation.getEvidence() == null || annotation.getEvidence().isEmpty())) {
                        String evidence = getEvidenceFromNamespace(term.getNamespace());
                        annotation.setEvidence(evidence);
                    }
                    
                    // Add GO namespace as attribute
                    annotation.setAttribute("go_namespace", term.getNamespace());
                    
                    // Update term descriptions cache
                    termDescriptions.put(goId, term.getName());
                    
                    enhanced++;
                }
            }
        }
        
        logger.info("Enhanced " + enhanced + " GO annotations with OBO term information");
        return enhanced > 0;
    }
    
    /**
     * Enhance KEGG annotations with detailed information from KEGG background file
     */
    public boolean enhanceKEGGAnnotationsWithBackground(File keggBackgroundFile) {
        if (keggBackgroundFile == null || !keggBackgroundFile.exists()) {
            return false;
        }
        
        try {
            simplegenomehub.util.fileio.KEGGBackgroundParser.KEGGTerm keggTermMap = new simplegenomehub.util.fileio.KEGGBackgroundParser.KEGGTerm("");
            java.util.Map<String, simplegenomehub.util.fileio.KEGGBackgroundParser.KEGGTerm> keggTerms = 
                simplegenomehub.util.fileio.KEGGBackgroundParser.parseKEGGBackground(keggBackgroundFile);
            
            int enhanced = 0;
            Map<String, List<GeneAnnotation>> keggAnnotations = annotations.get(AnnotationType.KEGG);
            
            if (keggAnnotations != null) {
                for (Map.Entry<String, List<GeneAnnotation>> entry : keggAnnotations.entrySet()) {
                    for (GeneAnnotation annotation : entry.getValue()) {
                        String kNumber = annotation.getAnnotationId();
                        simplegenomehub.util.fileio.KEGGBackgroundParser.KEGGTerm term = 
                            simplegenomehub.util.fileio.KEGGBackgroundParser.getKEGGTerm(kNumber, keggTerms);
                        
                        if (term != null) {
                            // Update term name and description
                            if (term.getDescription() != null && (annotation.getTerm() == null || annotation.getTerm().isEmpty())) {
                                annotation.setTerm(term.getDescription());
                            }
                            
                            // Update description with pathway information
                            if (term.getPathwaysString() != null && !term.getPathwaysString().isEmpty()) {
                                if (annotation.getDescription() == null || annotation.getDescription().isEmpty()) {
                                    annotation.setDescription(term.getPathwaysString());
                                } else {
                                    // Append pathway information if description already exists
                                    annotation.setDescription(annotation.getDescription() + " | " + term.getPathwaysString());
                                }
                            }
                            
                            // Add KEGG hierarchies as attribute
                            if (term.getHierarchiesString() != null && !term.getHierarchiesString().isEmpty()) {
                                annotation.setAttribute("kegg_hierarchies", term.getHierarchiesString());
                            }
                            
                            // Add pathways as attribute
                            if (term.getPathwaysString() != null && !term.getPathwaysString().isEmpty()) {
                                annotation.setAttribute("kegg_pathways", term.getPathwaysString());
                            }
                            
                            // Update term descriptions cache
                            termDescriptions.put(kNumber, term.getDescription());
                            
                            enhanced++;
                        }
                    }
                }
            }
            
            logger.info("Enhanced " + enhanced + " KEGG annotations with background information");
            return enhanced > 0;
            
        } catch (Exception e) {
            logger.warning("Failed to enhance KEGG annotations: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Convert GO namespace to evidence code
     */
    private String getEvidenceFromNamespace(String namespace) {
        if (namespace == null) return "IEA";
        
        switch (namespace.toLowerCase()) {
            case "biological_process":
                return "BP";
            case "molecular_function":
                return "MF";
            case "cellular_component":
                return "CC";
            default:
                return "IEA";
        }
    }
    
    /**
     * Convert evidence code to GO namespace
     */
    private String getNamespaceFromEvidence(String evidence) {
        if (evidence == null) return "molecular_function";
        
        switch (evidence.toUpperCase()) {
            case "BP":
                return "biological_process";
            case "MF":
                return "molecular_function";
            case "CC":
                return "cellular_component";
            default:
                return "molecular_function";
        }
    }
    
    /**
     * Get annotations for a specific gene
     */
    public List<GeneAnnotation> getGeneAnnotations(String geneId, AnnotationType type) {
        Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
        return typeAnnotations.getOrDefault(geneId, new ArrayList<>());
    }
    
    /**
     * Get all annotations for a gene (all types)
     */
    public List<GeneAnnotation> getAllGeneAnnotations(String geneId) {
        List<GeneAnnotation> allAnnotations = new ArrayList<>();
        for (AnnotationType type : AnnotationType.values()) {
            allAnnotations.addAll(getGeneAnnotations(geneId, type));
        }
        return allAnnotations;
    }
    
    /**
     * Get genes annotated with a specific term
     */
    public List<String> getGenesWithTerm(String termId, AnnotationType type) {
        List<String> genes = new ArrayList<>();
        Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
        
        for (Map.Entry<String, List<GeneAnnotation>> entry : typeAnnotations.entrySet()) {
            for (GeneAnnotation annotation : entry.getValue()) {
                if (annotation.getAnnotationId().equals(termId)) {
                    genes.add(entry.getKey());
                    break;
                }
            }
        }
        return genes;
    }
    
    /**
     * Perform GO enrichment analysis using TBtools GOTermEnrichment
     */
    public List<EnrichmentResult> performGOEnrichment(List<String> testGenes, double pValueThreshold) {
        List<EnrichmentResult> results = new ArrayList<>();
        
        try {
            // Get all annotated genes as background
            Set<String> backgroundGenes = new HashSet<>();
            Map<String, List<GeneAnnotation>> goAnnotations = annotations.get(AnnotationType.GO);
            backgroundGenes.addAll(goAnnotations.keySet());
            
            if (backgroundGenes.isEmpty()) {
                logger.warning("No GO annotations available for enrichment analysis");
                return results;
            }
            
            // Use TBtools GOTermEnrichment
            GOTermEnrichment enricher = new GOTermEnrichment();
            
            // Create term-gene mapping
            Map<String, Set<String>> termGenes = new HashMap<>();
            for (Map.Entry<String, List<GeneAnnotation>> entry : goAnnotations.entrySet()) {
                String gene = entry.getKey();
                for (GeneAnnotation annotation : entry.getValue()) {
                    String termId = annotation.getAnnotationId();
                    termGenes.computeIfAbsent(termId, k -> new HashSet<>()).add(gene);
                }
            }
            
            // Perform enrichment for each term
            for (Map.Entry<String, Set<String>> termEntry : termGenes.entrySet()) {
                String termId = termEntry.getKey();
                Set<String> termGenesSet = termEntry.getValue();
                
                // Count overlapping genes
                int overlap = 0;
                List<String> overlappingGenes = new ArrayList<>();
                for (String testGene : testGenes) {
                    if (termGenesSet.contains(testGene)) {
                        overlap++;
                        overlappingGenes.add(testGene);
                    }
                }
                
                if (overlap == 0) continue;
                
                // Calculate enrichment statistics using hypergeometric test
                int testSize = testGenes.size();
                int termSize = termGenesSet.size();
                int backgroundSize = backgroundGenes.size();
                
                double pValue = calculateHypergeometricPValue(overlap, testSize, termSize, backgroundSize);
                
                if (pValue <= pValueThreshold) {
                    EnrichmentResult result = new EnrichmentResult(termId, 
                        termDescriptions.getOrDefault(termId, termId), "GO");
                    result.setGeneCount(overlap);
                    result.setBackgroundCount(termSize);
                    result.setPValue(pValue);
                    result.setAdjustedPValue(pValue); // Simple - should use FDR correction
                    result.setEnrichmentRatio((double) overlap / testSize * backgroundSize / termSize);
                    
                    for (String gene : overlappingGenes) {
                        result.addGeneId(gene);
                    }
                    
                    results.add(result);
                }
            }
            
            // Sort by p-value
            results.sort(Comparator.comparingDouble(EnrichmentResult::getPValue));
            
            logger.info("GO enrichment analysis completed: " + results.size() + " significant terms");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to perform GO enrichment analysis", e);
        }
        
        return results;
    }
    
    /**
     * Perform KEGG pathway enrichment using TBtools KeggEnrichment
     */
    public List<EnrichmentResult> performKEGGEnrichment(List<String> testGenes, double pValueThreshold) {
        List<EnrichmentResult> results = new ArrayList<>();
        
        try {
            // Use similar approach as GO enrichment for KEGG pathways
            Map<String, List<GeneAnnotation>> keggAnnotations = annotations.get(AnnotationType.KEGG);
            if (keggAnnotations.isEmpty()) {
                logger.warning("No KEGG annotations available for enrichment analysis");
                return results;
            }
            
            // Create pathway-gene mapping
            Map<String, Set<String>> pathwayGenes = new HashMap<>();
            Set<String> backgroundGenes = new HashSet<>();
            
            for (Map.Entry<String, List<GeneAnnotation>> entry : keggAnnotations.entrySet()) {
                String gene = entry.getKey();
                backgroundGenes.add(gene);
                for (GeneAnnotation annotation : entry.getValue()) {
                    String pathwayId = annotation.getAnnotationId();
                    pathwayGenes.computeIfAbsent(pathwayId, k -> new HashSet<>()).add(gene);
                }
            }
            
            // Perform enrichment analysis
            for (Map.Entry<String, Set<String>> pathwayEntry : pathwayGenes.entrySet()) {
                String pathwayId = pathwayEntry.getKey();
                Set<String> pathwayGenesSet = pathwayEntry.getValue();
                
                // Count overlapping genes
                int overlap = 0;
                List<String> overlappingGenes = new ArrayList<>();
                for (String testGene : testGenes) {
                    if (pathwayGenesSet.contains(testGene)) {
                        overlap++;
                        overlappingGenes.add(testGene);
                    }
                }
                
                if (overlap == 0) continue;
                
                // Calculate statistics
                int testSize = testGenes.size();
                int pathwaySize = pathwayGenesSet.size();
                int backgroundSize = backgroundGenes.size();
                
                double pValue = calculateHypergeometricPValue(overlap, testSize, pathwaySize, backgroundSize);
                
                if (pValue <= pValueThreshold) {
                    EnrichmentResult result = new EnrichmentResult(pathwayId, 
                        termDescriptions.getOrDefault(pathwayId, pathwayId), "KEGG");
                    result.setGeneCount(overlap);
                    result.setBackgroundCount(pathwaySize);
                    result.setPValue(pValue);
                    result.setAdjustedPValue(pValue);
                    result.setEnrichmentRatio((double) overlap / testSize * backgroundSize / pathwaySize);
                    
                    for (String gene : overlappingGenes) {
                        result.addGeneId(gene);
                    }
                    
                    results.add(result);
                }
            }
            
            results.sort(Comparator.comparingDouble(EnrichmentResult::getPValue));
            logger.info("KEGG enrichment analysis completed: " + results.size() + " significant pathways");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to perform KEGG enrichment analysis", e);
        }
        
        return results;
    }
    
    /**
     * Simple hypergeometric p-value calculation
     */
    private double calculateHypergeometricPValue(int overlap, int testSize, int termSize, int backgroundSize) {
        // Simplified hypergeometric calculation - in production should use proper statistical library
        double probability = 0.0;
        
        for (int i = overlap; i <= Math.min(testSize, termSize); i++) {
            double term = combination(termSize, i) * combination(backgroundSize - termSize, testSize - i) 
                         / combination(backgroundSize, testSize);
            probability += term;
        }
        
        return Math.min(1.0, Math.max(0.0, probability));
    }
    
    /**
     * Calculate combination (n choose k)
     */
    private double combination(int n, int k) {
        if (k > n || k < 0) return 0.0;
        if (k == 0 || k == n) return 1.0;
        
        double result = 1.0;
        for (int i = 0; i < Math.min(k, n - k); i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }
    
    /**
     * Update annotation statistics
     */
    private void updateStatistics() {
        Set<String> allGenes = new HashSet<>();
        totalGenes = 0;
        
        // Reset all counts to 0
        for (AnnotationType type : AnnotationType.values()) {
            annotationCounts.put(type, 0);
        }
        
        // Count annotations for each type
        for (AnnotationType type : AnnotationType.values()) {
            Map<String, List<GeneAnnotation>> typeMap = annotations.get(type);
            int typeCount = 0;
            
            for (List<GeneAnnotation> geneAnnotations : typeMap.values()) {
                typeCount += geneAnnotations.size();
                allGenes.addAll(typeMap.keySet());
            }
            
            annotationCounts.put(type, typeCount);
            
            if (typeCount > 0) {
                logger.info("Updated statistics for " + type.getShortName() + ": " + typeCount + " annotations");
            }
        }
        
        annotatedGenes = allGenes.size();
    }
    
    /**
     * Public method to update statistics (for debugging and final updates)
     */
    public void updateFinalStatistics() {
        updateStatistics();
        logger.info("Final statistics update completed");
    }
    
    private AnnotationType getAnnotationType(Map<String, List<GeneAnnotation>> typeMap) {
        // Helper to determine annotation type from map
        for (AnnotationType type : AnnotationType.values()) {
            if (annotations.get(type) == typeMap) {
                return type;
            }
        }
        return AnnotationType.CUSTOM;
    }
    
    /**
     * Save annotation data to file
     */
    public boolean saveToFile(File file, AnnotationType type) {
        // For Custom annotations, if we have the original source file, just copy it directly
        if (type == AnnotationType.CUSTOM && originalSourceFile != null && originalSourceFile.exists()) {
            try {
                java.nio.file.Files.copy(originalSourceFile.toPath(), file.toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied original Custom annotation file to: " + file.getAbsolutePath());
                return true;
            } catch (Exception e) {
                logger.warning("Failed to copy original Custom file, falling back to reconstruction: " + e.getMessage());
                // Fall through to normal saving logic
            }
        }
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Get column headers for this type
            String[] headers = getColumnHeaders(type);
            
            // For Custom annotations, check if we need to use dynamic headers from the first annotation
            if (type == AnnotationType.CUSTOM) {
                Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
                if (!typeAnnotations.isEmpty()) {
                    // Get the first annotation to check if it has custom headers
                    GeneAnnotation firstAnnotation = typeAnnotations.values().iterator().next().get(0);
                    if (firstAnnotation.getColumnCount() > 0 && firstAnnotation.getColumnHeaders() != null) {
                        headers = firstAnnotation.getColumnHeaders();
                    }
                }
            }
            
            // Write header line
            writer.print("#");
            for (int i = 0; i < headers.length; i++) {
                if (i > 0) writer.print("\t");
                writer.print(headers[i]);
            }
            writer.println();
            
            Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
            for (Map.Entry<String, List<GeneAnnotation>> entry : typeAnnotations.entrySet()) {
                String geneId = entry.getKey();
                for (GeneAnnotation annotation : entry.getValue()) {
                    // For Custom annotations with dynamic column data, preserve exact structure
                    if (type == AnnotationType.CUSTOM && annotation.getColumnCount() > 0) {
                        // Use original column data exactly as imported
                        String[] columnValues = annotation.getColumnValues();
                        for (int i = 0; i < columnValues.length; i++) {
                            if (i > 0) writer.print("\t");
                            writer.print(columnValues[i] != null ? columnValues[i] : "");
                        }
                    } else if (annotation.getColumnCount() > 0) {
                        // Use dynamic column data for other types, but align with headers
                        String[] columnValues = annotation.getColumnValues();
                        for (int i = 0; i < Math.min(columnValues.length, headers.length); i++) {
                            if (i > 0) writer.print("\t");
                            writer.print(columnValues[i] != null ? columnValues[i] : "");
                        }
                        // Fill remaining columns if headers are more than values
                        for (int i = columnValues.length; i < headers.length; i++) {
                            writer.print("\t");
                        }
                    } else {
                        // Use legacy format for older annotations without dynamic data
                        for (int i = 0; i < headers.length; i++) {
                            if (i > 0) writer.print("\t");
                            
                            switch (i) {
                                case 0: 
                                    writer.print(geneId); 
                                    break;
                                case 1: 
                                    writer.print(annotation.getAnnotationId() != null ? annotation.getAnnotationId() : ""); 
                                    break;
                                case 2: 
                                    writer.print(annotation.getTerm() != null ? annotation.getTerm() : ""); 
                                    break;
                                case 3: 
                                    writer.print(annotation.getDescription() != null ? annotation.getDescription() : ""); 
                                    break;
                                case 4: 
                                    // For GO terms, use the namespace as Class; for others use Evidence
                                    String classOrEvidence = annotation.getEvidence() != null ? annotation.getEvidence() : "";
                                    if (type == AnnotationType.GO) {
                                        String namespace = annotation.getAttributes().get("go_namespace");
                                        if (namespace != null) {
                                            classOrEvidence = namespace;
                                        }
                                    }
                                    writer.print(classOrEvidence);
                                    break;
                                default: 
                                    writer.print(""); 
                                    break;
                            }
                        }
                    }
                    writer.println();
                }
            }
            
            logger.info("Saved " + type.getShortName() + " annotations to: " + file.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save annotations to " + file, e);
            return false;
        }
    }
    
    // Getters
    public String getDatasetId() { return datasetId; }
    public String getName() { return name; }
    public LocalDateTime getImportTime() { return importTime; }
    
    public int getTotalGenes() { return totalGenes; }
    public int getAnnotatedGenes() { return annotatedGenes; }
    
    public Map<AnnotationType, Integer> getAnnotationCounts() { 
        return new HashMap<>(annotationCounts); 
    }
    
    /**
     * Get column headers for a specific annotation type
     */
    public String[] getColumnHeaders(AnnotationType type) {
        String[] headers = columnHeaders.get(type);
        if (headers != null) {
            return headers.clone();
        }
        
        // Return default headers based on annotation type
        switch (type) {
            case GO:
                return new String[]{"Gene_ID", "GO_ID", "Term", "Description"};
            case KEGG:
                return new String[]{"Gene_ID", "KEGG_ID", "Term", "Description"};
            case INTERPRO:
                return new String[]{"Gene_ID", "InterPro_ID", "Term", "Description", "Evidence"};
            case PFAM:
                return new String[]{"Gene_ID", "Pfam_ID", "Term", "Description", "Evidence"};
            case CUSTOM:
            default:
                // For CUSTOM type, determine columns based on actual data
                Map<String, List<GeneAnnotation>> customAnnotations = annotations.get(AnnotationType.CUSTOM);
                if (customAnnotations != null && !customAnnotations.isEmpty()) {
                    // Get the first annotation to check column count
                    GeneAnnotation firstAnnotation = customAnnotations.values().iterator().next().get(0);
                    if (firstAnnotation.getColumnCount() > 0 && firstAnnotation.getColumnHeaders() != null) {
                        return firstAnnotation.getColumnHeaders();
                    }
                    // If no custom headers but we know the column count, generate appropriate headers
                    int columnCount = firstAnnotation.getColumnCount();
                    if (columnCount == 2) {
                        return new String[]{"Gene_ID", "Description"};
                    } else if (columnCount >= 3) {
                        return new String[]{"Gene_ID", "Annotation_ID", "Description"};
                    }
                }
                // Final fallback for empty Custom annotations
                return new String[]{"Gene_ID", "Description"};
        }
    }
    
    /**
     * Set column headers for a specific annotation type
     */
    public void setColumnHeaders(AnnotationType type, String[] headers) {
        if (headers != null) {
            columnHeaders.put(type, headers.clone());
        }
    }
    
    public Set<String> getAllAnnotatedGenes() {
        Set<String> allGenes = new HashSet<>();
        for (Map<String, List<GeneAnnotation>> typeMap : annotations.values()) {
            allGenes.addAll(typeMap.keySet());
        }
        return allGenes;
    }
    
    public Set<String> getAnnotatedGenes(AnnotationType type) {
        return new HashSet<>(annotations.get(type).keySet());
    }
    
    @Override
    public String toString() {
        return name + " (" + annotatedGenes + " genes, " + 
               annotationCounts.values().stream().mapToInt(Integer::intValue).sum() + " annotations)";
    }
}