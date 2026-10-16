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
import simplegenomehub.util.fileio.AtomicFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
        private Map<String, String> originalStandardFields;
        
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
            
            // A declared schema can reorder or extend standard columns. Keep its
            // raw values intact while exposing the named fields to the editor.
            int idColumn = annotationIdColumn();
            this.annotationId = idColumn >= 0 ? columnValue(idColumn)
                : type == AnnotationType.CUSTOM ? "custom_" + UUID.randomUUID()
                : columnValue(1);
            this.term = columnValue(findColumn("Term"));
            this.description = columnValue(findColumn("Description"));
            this.evidence = columnValue(findColumn("Evidence", "Class", "Domain", "Type"));
            String scoreValue = columnValue(findColumn("Score"));
            if (!scoreValue.isEmpty()) {
                try { this.score = Double.parseDouble(scoreValue); }
                catch (NumberFormatException ignored) { /* Preserve nonnumeric raw columns. */ }
            }
            this.originalStandardFields = standardFields();
        }
        
        // Getters and setters
        public String getGeneId() { return geneId; }
        public String getAnnotationId() { return annotationId; }
        public String getTerm() { return term; }
        public void setTerm(String term) {
            if (!Objects.equals(this.term, term)) updateColumn(term, "Term");
            this.term = term;
        }
        
        public String getDescription() { return description; }
        public void setDescription(String description) {
            if (!Objects.equals(this.description, description)) updateColumn(description, "Description");
            this.description = description;
        }
        
        public AnnotationType getType() { return type; }
        public String getEvidence() { return evidence; }
        public void setEvidence(String evidence) {
            if (!Objects.equals(this.evidence, evidence)) updateColumn(evidence, "Evidence", "Class", "Domain", "Type");
            this.evidence = evidence;
        }
        
        public double getScore() { return score; }
        public void setScore(double score) {
            if (Double.compare(this.score, score) != 0) updateColumn(Double.toString(score), "Score");
            this.score = score;
        }

        private int findColumn(String... names) {
            if (columnHeaders == null) return -1;
            for (String name : names) {
                for (int i = 1; i < columnHeaders.length; i++) {
                    if (columnHeaders[i] != null && normalizeColumn(columnHeaders[i]).equals(normalizeColumn(name)))
                        return i;
                }
            }
            return -1;
        }

        private int annotationIdColumn() {
            return findColumn("Annotation_ID", type.getShortName() + "_ID", "GO_ID", "KEGG_ID", "InterPro_ID", "Pfam_ID");
        }

        private static String normalizeColumn(String header) {
            return header.trim().replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
        }

        private String columnValue(int index) {
            return columnValues == null || index < 0 ? "" : annotationValue(columnValues, index);
        }

        private void updateColumn(String value, String... names) {
            int index = findColumn(names);
            if (columnValues == null || index < 0) return;
            if (columnValues.length <= index) columnValues = Arrays.copyOf(columnValues, index + 1);
            columnValues[index] = value == null ? "" : value;
        }

        private Map<String, String> standardFields() {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("Term", term);
            fields.put("Description", description);
            fields.put("Evidence", evidence);
            fields.put("Score", Double.toString(score));
            return fields;
        }

        private void validateStandardFields() throws IOException {
            if (columnValues == null || originalStandardFields == null) return;
            for (Map.Entry<String, String> field : standardFields().entrySet()) {
                String value = field.getValue();
                if (value == null || value.isEmpty() || ("Score".equals(field.getKey()) && score == 0.0)
                    || Objects.equals(value, originalStandardFields.get(field.getKey()))) continue;
                int index = "Evidence".equals(field.getKey())
                    ? findColumn("Evidence", "Class", "Domain", "Type") : findColumn(field.getKey());
                if (index < 0) throw new IOException("Cannot save edited " + field.getKey()
                    + " for gene " + geneId + ": annotation header has no matching column");
            }
        }
        
        public Map<String, String> getAttributes() { return attributes; }
        public void setAttribute(String key, String value) { attributes.put(key, value); }
        
        // Dynamic column support
        public String[] getColumnHeaders() { return columnHeaders != null ? columnHeaders.clone() : null; }
        public String[] getColumnValues() { return columnValues != null ? columnValues.clone() : null; }
        public void setColumnData(String[] headers, String[] values) { 
            this.columnHeaders = headers != null ? headers.clone() : null;
            this.columnValues = values != null ? values.clone() : null;
            this.originalStandardFields = standardFields();
        }

        /** Preserve the loaded schema and unknown columns before applying editor setters. */
        public GeneAnnotation copyForEdit(String newGeneId, String newAnnotationId, AnnotationType newType) {
            GeneAnnotation copy = new GeneAnnotation(newGeneId, newAnnotationId, term, newType);
            copy.description = description;
            copy.evidence = evidence;
            copy.score = score;
            copy.attributes.putAll(attributes);
            copy.setColumnData(columnHeaders, columnValues);
            if (originalStandardFields != null) copy.originalStandardFields = new LinkedHashMap<>(originalStandardFields);
            if (copy.columnValues != null) {
                if (copy.columnValues.length == 0)
                    throw new IllegalArgumentException("Annotation has no gene identifier column");
                copy.columnValues[0] = newGeneId;
                int idColumn = annotationIdColumn();
                if (idColumn < 0 && type != AnnotationType.CUSTOM && copy.columnValues.length > 1) idColumn = 1;
                if (idColumn >= 0) {
                    if (copy.columnValues.length <= idColumn) copy.columnValues = Arrays.copyOf(copy.columnValues, idColumn + 1);
                    copy.columnValues[idColumn] = newAnnotationId;
                } else if (!Objects.equals(annotationId, newAnnotationId)) {
                    throw new IllegalArgumentException("Cannot edit annotation ID: annotation header has no matching column");
                }
            }
            return copy;
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
    private byte[] originalSourceDigest;
    private byte[] originalCustomState;
    // Distinguish an explicit deletion from annotation types not loaded yet.
    private final Set<AnnotationType> explicitlyClearedTypes = EnumSet.noneOf(AnnotationType.class);
    
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
        try (BufferedReader bio = openAnnotationReader(file)) {
            List<GeneAnnotation> parsed = new ArrayList<>();
            String line;
            int lineNum = 0;
            
            while ((line = bio.readLine()) != null) {
                lineNum++;
                if (line.startsWith("\uFEFF")) line = line.substring(1);
                if (line.trim().startsWith("#") || line.trim().isEmpty()) continue;
                
                String[] parts = line.split("\t", -1);
                if (isAnnotationHeader(parts)) continue;
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
                
                parsed.add(annotation);
            }
            for (GeneAnnotation annotation : parsed) addAnnotation(annotation);
            
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
        try (BufferedReader bio = openAnnotationReader(file)) {
            List<GeneAnnotation> parsed = new ArrayList<>();
            String line;
            
            while ((line = bio.readLine()) != null) {
                if (line.startsWith("\uFEFF")) line = line.substring(1);
                if (line.trim().startsWith("#") || line.trim().isEmpty()) continue;
                
                String[] parts = line.split("\t", -1);
                if (isAnnotationHeader(parts)) continue;
                if (parts.length < 2) continue;  // 只需要至少2列：geneId和keggId
                
                String geneId = parts[0].trim();
                String keggId = parts[1].trim();
                String term = parts.length > 2 ? parts[2].trim() : "";
                String description = parts.length > 3 ? parts[3].trim() : "";
                String evidence = parts.length > 4 ? parts[4].trim() : "";
                
                GeneAnnotation annotation = new GeneAnnotation(geneId, keggId, term, AnnotationType.KEGG);
                annotation.setDescription(description.isEmpty() ? term : description);
                annotation.setEvidence(evidence);
                
                parsed.add(annotation);
            }
            for (GeneAnnotation annotation : parsed) addAnnotation(annotation);
            
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
        try { return loadCustomAnnotations(file, type, "\t", false); }
        catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load custom annotations from " + file, e);
            return false;
        }
    }

    /** Parse completely before replacing or appending any live annotation data. */
    public boolean loadCustomAnnotations(File file, AnnotationType type, String delimiter,
                                         boolean replaceExisting) throws IOException {
        if (file == null || type == null || delimiter == null || delimiter.isEmpty())
            throw new IOException("Annotation file, type and delimiter are required");
        boolean preserveSource = type == AnnotationType.CUSTOM && "\t".equals(delimiter)
            && !file.getName().toLowerCase(Locale.ROOT).endsWith(".gz")
            && (replaceExisting || annotations.get(type).isEmpty());
        byte[] sourceDigest = preserveSource ? digestFile(file.toPath()) : null;
        List<String[]> rows = new ArrayList<>();
        String[] fileHeaders = null;
        int maximumColumns = 0;
        int maximumColumnsLine = 0;
        int lineNumber = 0;
        boolean headerlessDataStarted = false;
        // Canonical files are UTF-8. Published Windows versions also wrote
        // native-charset files; retry those only after strict UTF-8 decoding fails.
        java.nio.charset.Charset charset = StandardCharsets.UTF_8;
        while (true) {
            rows.clear();
            fileHeaders = null;
            maximumColumns = 0;
            maximumColumnsLine = 0;
            lineNumber = 0;
            headerlessDataStarted = false;
            try (BufferedReader reader = openAnnotationReader(file, charset)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (lineNumber == 1 && line.startsWith("\uFEFF")) line = line.substring(1);
                    if (line.trim().isEmpty()) continue;
                    String trimmed = line.trim();
                    boolean comment = trimmed.startsWith("#");
                    String candidate = comment ? line.stripLeading().substring(1) : line;
                    String[] parts = candidate.split(java.util.regex.Pattern.quote(delimiter), -1);
                    if (isAnnotationHeader(parts)) {
                        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
                        if (fileHeaders == null && !rows.isEmpty()) {
                            String[] establishedHeaders = legacyHeaders(type, maximumColumns);
                            if (!Arrays.equals(establishedHeaders, parts))
                                throw annotationFormatError(file, lineNumber,
                                    "Annotation header changes columns after data has started");
                        }
                        if (fileHeaders != null && !Arrays.equals(fileHeaders, parts))
                            throw annotationFormatError(file, lineNumber, "Conflicting annotation header");
                        fileHeaders = parts;
                        continue;
                    }
                    if (comment) continue;
                    if (parts.length < 2)
                        throw annotationFormatError(file, lineNumber, "Expected a gene identifier and annotation columns");
                    parts[0] = parts[0].trim();
                    if (parts[0].isEmpty())
                        throw annotationFormatError(file, lineNumber, "Gene identifier is empty");
                    boolean hasContent = false;
                    for (int i = 1; i < parts.length; i++)
                        if (!parts[i].trim().isEmpty() && !"-".equals(parts[i].trim())) hasContent = true;
                    if (!hasContent) continue;
                    if (fileHeaders == null) headerlessDataStarted = true;
                    rows.add(parts);
                    if (parts.length > maximumColumns) {
                        maximumColumns = parts.length;
                        maximumColumnsLine = lineNumber;
                    }
                }
            } catch (java.nio.charset.CharacterCodingException badEncoding) {
                java.nio.charset.Charset legacy = java.nio.charset.Charset.defaultCharset();
                if (!charset.equals(StandardCharsets.UTF_8) || legacy.equals(StandardCharsets.UTF_8)) throw badEncoding;
                charset = legacy;
                continue;
            }
            break;
        }
        if (rows.isEmpty()) return false;
        if (fileHeaders != null && maximumColumns > fileHeaders.length)
            throw annotationFormatError(file, maximumColumnsLine, "A data row has more columns than the annotation header");
        boolean headerless = fileHeaders == null || headerlessDataStarted;
        if (headerless) fileHeaders = legacyHeaders(type, maximumColumns);
        if (!replaceExisting && !annotations.get(type).isEmpty()
            && !Arrays.equals(getColumnHeaders(type), fileHeaders))
            throw annotationFormatError(file, 1, "Annotation columns differ from the existing dataset");

        Map<String, List<GeneAnnotation>> parsed = new LinkedHashMap<>();
        for (String[] row : rows) {
            String[] values = Arrays.copyOf(row, fileHeaders.length);
            for (int i = row.length; i < values.length; i++) values[i] = "";
            GeneAnnotation annotation;
            if (headerless) {
                String id = annotationValue(values, 1);
                String term = annotationValue(values, 2);
                String description = annotationValue(values, 3);
                String legacyDescription = !description.isEmpty() ? description : !term.isEmpty() ? term : id;
                annotation = new GeneAnnotation(values[0], id, legacyDescription, type);
                annotation.setDescription(legacyDescription);
                annotation.setEvidence(annotationValue(values, 4));
                annotation.setColumnData(fileHeaders, values);
            } else {
                annotation = new GeneAnnotation(values[0], type, fileHeaders, values);
            }
            parsed.computeIfAbsent(values[0], ignored -> new ArrayList<>()).add(annotation);
        }
        if (preserveSource && !Arrays.equals(sourceDigest, digestFile(file.toPath())))
            throw new IOException("Annotation source changed while being read: " + file);
        Map<String, List<GeneAnnotation>> committed = new LinkedHashMap<>();
        if (!replaceExisting) for (Map.Entry<String, List<GeneAnnotation>> entry : annotations.get(type).entrySet())
            committed.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        for (Map.Entry<String, List<GeneAnnotation>> entry : parsed.entrySet())
            committed.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).addAll(entry.getValue());
        annotations.put(type, committed);
        explicitlyClearedTypes.remove(type);
        columnHeaders.put(type, fileHeaders.clone());
        annotationCounts.put(type, committed.values().stream().mapToInt(List::size).sum());
        // Preserve descriptions enriched from GO/KEGG backends for other types.
        for (List<GeneAnnotation> geneAnnotations : parsed.values())
            for (GeneAnnotation annotation : geneAnnotations)
                if (annotation.getDescription() != null)
                    termDescriptions.put(annotation.getAnnotationId(), annotation.getDescription());
        updateStatistics();
        if (type == AnnotationType.CUSTOM) {
            originalSourceFile = preserveSource ? file : null;
            originalSourceDigest = sourceDigest;
            originalCustomState = preserveSource ? digestAnnotations(type) : null;
        }
        logger.info("Loaded " + type.getShortName() + " annotations from: " + file.getName()
            + " (" + rows.size() + " annotations)");
        return true;
    }

    public static boolean isAnnotationHeader(String[] fields) {
        if (fields.length < 2 || !fields[0].trim().matches("(?i)gene(?:_?id)?")) return false;
        for (int i = 1; i < fields.length; i++) if (!fields[i].trim().isEmpty()) return true;
        return false;
    }

    /** Read a bounded preview using the same raw UTF-8/native and gzip decoding as imports.
     * Comments do not consume the data-row limit, so long annotation preambles remain importable.
     */
    public static List<String> readAnnotationPreview(File file, int maximumDataLines) throws IOException {
        if (maximumDataLines < 1) throw new IllegalArgumentException("Preview size must be positive");
        java.nio.charset.Charset charset = StandardCharsets.UTF_8;
        while (true) {
            List<String> lines = new ArrayList<>();
            int dataLines = 0;
            int comments = 0;
            boolean keptHeader = false;
            try (BufferedReader reader = openAnnotationReader(file, charset)) {
                String line;
                boolean first = true;
                while (dataLines < maximumDataLines && (line = reader.readLine()) != null) {
                    if (first && line.startsWith("\uFEFF")) line = line.substring(1);
                    first = false;
                    if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                        boolean header = false;
                        if (line.trim().startsWith("#")) {
                            String candidate = line.stripLeading().substring(1);
                            for (String delimiter : new String[]{"\t", ",", ";"})
                                header |= isAnnotationHeader(candidate.split(java.util.regex.Pattern.quote(delimiter), -1));
                        }
                        if (comments++ < 20 || (header && !keptHeader)) lines.add(line);
                        keptHeader |= header;
                    } else {
                        lines.add(line);
                        dataLines++;
                    }
                }
                return lines;
            } catch (java.nio.charset.CharacterCodingException badEncoding) {
                java.nio.charset.Charset legacy = java.nio.charset.Charset.defaultCharset();
                if (!charset.equals(StandardCharsets.UTF_8) || legacy.equals(StandardCharsets.UTF_8)) throw badEncoding;
                charset = legacy;
            }
        }
    }

    /** True only after an explicit model clear, not for a lazily unloaded type. */
    public boolean isAnnotationTypeExplicitlyCleared(AnnotationType type) {
        return explicitlyClearedTypes.contains(type);
    }

    /** Open unfiltered annotation text after validating its encoding completely.
     * This lets table and metadata readers use the same UTF-8/native fallback
     * without publishing half a parse before a decoding error near the end.
     */
    public static BufferedReader openAnnotationReader(File file) throws IOException {
        java.nio.charset.Charset charset = StandardCharsets.UTF_8;
        while (true) {
            try (BufferedReader reader = openAnnotationReader(file, charset)) {
                char[] buffer = new char[8192];
                while (reader.read(buffer) != -1) { /* Validate before returning a live reader. */ }
            } catch (java.nio.charset.CharacterCodingException badEncoding) {
                java.nio.charset.Charset legacy = java.nio.charset.Charset.defaultCharset();
                if (!charset.equals(StandardCharsets.UTF_8) || legacy.equals(StandardCharsets.UTF_8)) throw badEncoding;
                charset = legacy;
                continue;
            }
            return openAnnotationReader(file, charset);
        }
    }

    private static BufferedReader openAnnotationReader(File file, java.nio.charset.Charset charset) throws IOException {
        InputStream input = Files.newInputStream(file.toPath());
        try {
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".gz"))
                input = new java.util.zip.GZIPInputStream(input);
            return new BufferedReader(new InputStreamReader(input, charset.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)));
        } catch (IOException | RuntimeException failure) {
            input.close();
            throw failure;
        }
    }

    private static IOException annotationFormatError(File file, int line, String message) {
        return new IOException(message + " at " + file.getName() + ":" + line);
    }

    private static String annotationValue(String[] values, int index) {
        if (index >= values.length || values[index] == null) return "";
        String value = values[index].trim();
        return "-".equals(value) ? "" : value;
    }

    private static String[] legacyHeaders(AnnotationType type, int count) {
        String[] standard = {"Gene_ID", type == AnnotationType.CUSTOM ? "Annotation_ID" : type.getShortName() + "_ID",
            "Term", "Description", "Evidence"};
        String[] headers = new String[count];
        for (int i = 0; i < count; i++) headers[i] = i < standard.length ? standard[i] : "Column_" + (i + 1);
        return headers;
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
        explicitlyClearedTypes.remove(type);
        
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
        int previousSize = geneAnnotationList.size();
        boolean removed = geneAnnotationList.removeIf(ann ->
            Objects.equals(ann.getAnnotationId(), annotation.getAnnotationId())
                && Objects.equals(ann.getTerm(), annotation.getTerm()));
        
        if (removed) {
            // If no annotations left for this gene, remove the gene entry
            if (geneAnnotationList.isEmpty()) {
                typeAnnotations.remove(geneId);
            }
            
            // Update count
            annotationCounts.put(type, annotationCounts.get(type) - (previousSize - geneAnnotationList.size()));
            if (typeAnnotations.isEmpty()) explicitlyClearedTypes.add(type);
            updateStatistics();

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
            if (typeAnnotations.isEmpty()) explicitlyClearedTypes.add(type);
            updateStatistics();
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
        explicitlyClearedTypes.add(type);
        
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
                if (typeAnnotations.isEmpty()) explicitlyClearedTypes.add(type);
            }
        }
        updateStatistics();

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
        explicitlyClearedTypes.add(type);
        
        // Update statistics
        rebuildStatisticsFromAnnotations();
        
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
            explicitlyClearedTypes.add(type);
        }
        
        // Clear additional data
        termDescriptions.clear();
        termHierarchy.clear();
        
        // Update statistics
        rebuildStatisticsFromAnnotations();
        
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
                    String[] columnValues = originalAnnotation.getColumnValues();
                    if (columnValues != null) {
                        columnValues[0] = geneId;
                        geneAnnotation.setColumnData(originalAnnotation.getColumnHeaders(), columnValues);
                    }
                    
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
        rebuildStatisticsFromAnnotations();
        
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
     * Refresh annotation statistics using incrementally maintained annotation counts.
     */
    private void updateStatistics() {
        Set<String> allGenes = new HashSet<>();
        totalGenes = 0;

        for (AnnotationType type : AnnotationType.values()) {
            Map<String, List<GeneAnnotation>> typeMap = annotations.get(type);
            if (typeMap == null || typeMap.isEmpty()) {
                continue;
            }

            allGenes.addAll(typeMap.keySet());
            int typeCount = annotationCounts.getOrDefault(type, 0);
            if (typeCount > 0) {
                logger.info("Updated statistics for " + type.getShortName() + ": " + typeCount + " annotations");
            }
        }

        annotatedGenes = allGenes.size();
    }

    /**
     * Fully rebuild annotation statistics from the current in-memory maps.
     * Use this after bulk structural changes that bypass incremental counters.
     */
    private void rebuildStatisticsFromAnnotations() {
        Set<String> allGenes = new HashSet<>();
        totalGenes = 0;

        for (AnnotationType type : AnnotationType.values()) {
            Map<String, List<GeneAnnotation>> typeMap = annotations.get(type);
            int typeCount = 0;

            if (typeMap != null && !typeMap.isEmpty()) {
                allGenes.addAll(typeMap.keySet());
                for (List<GeneAnnotation> geneAnnotations : typeMap.values()) {
                    typeCount += geneAnnotations.size();
                }
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
        rebuildStatisticsFromAnnotations();
        logger.info("Final statistics update completed");
    }
    
    /**
     * Save annotation data to file
     */
    public boolean saveToFile(File file, AnnotationType type) {
        try { saveToFileChecked(file, type); return true; }
        catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save annotations to " + file, e);
            return false;
        }
    }

    /** Publish a complete file; failure leaves an existing destination intact. */
    public void saveToFileChecked(File file, AnnotationType type) throws IOException {
        if (file == null || type == null) throw new IOException("Annotation destination and type are required");
        boolean unchangedCustom = type == AnnotationType.CUSTOM && originalSourceFile != null
            && originalSourceFile.isFile() && Arrays.equals(originalCustomState, digestAnnotations(type))
            && Arrays.equals(originalSourceDigest, digestFile(originalSourceFile.toPath()));
        AtomicFiles.withLock(file.toPath(), () -> {
            if (unchangedCustom) {
                Path destination = file.toPath().toAbsolutePath();
                if (Files.exists(destination) && Files.isSameFile(originalSourceFile.toPath(), destination)) return null;
                Path temporary = Files.createTempFile(destination.getParent(), ".sgh-annotation-", ".tmp");
                try {
                    Files.copy(originalSourceFile.toPath(), temporary, StandardCopyOption.REPLACE_EXISTING);
                    if (!Arrays.equals(originalSourceDigest, digestFile(temporary)))
                        throw new IOException("Annotation source changed while being copied: " + originalSourceFile);
                    AtomicFiles.replace(temporary, destination);
                } finally { Files.deleteIfExists(temporary); }
            } else {
                AtomicFiles.write(file.toPath(), writer -> writeAnnotations(writer, type));
            }
            return null;
        });
        logger.info("Saved " + type.getShortName() + " annotations to: " + file.getAbsolutePath());
    }

    private void writeAnnotations(Writer target, AnnotationType type) throws IOException {
        writeAnnotations(target, type, true);
    }

    private void writeAnnotations(Writer target, AnnotationType type, boolean validate) throws IOException {
        PrintWriter writer = new PrintWriter(target);
        String[] headers = getColumnHeaders(type);
        writer.println("#" + String.join("\t", headers));
        Map<String, List<GeneAnnotation>> typeAnnotations = annotations.get(type);
        for (String geneId : new TreeSet<>(typeAnnotations.keySet())) {
            for (GeneAnnotation annotation : typeAnnotations.get(geneId)) {
                if (validate) annotation.validateStandardFields();
                String[] values = annotation.getColumnValues();
                if (validate && values != null && annotation.columnHeaders != null
                    && !Arrays.equals(headers, annotation.columnHeaders))
                    throw new IOException("Annotation columns differ from the dataset for gene " + geneId);
                if (values != null && values.length > headers.length)
                    throw new IOException("Annotation has more values than its column headers: " + geneId);
                for (int i = 0; i < headers.length; i++) {
                    if (i > 0) writer.print("\t");
                    String value;
                    if (i == 0) value = geneId;
                    else if (values != null) value = i < values.length ? values[i] : "";
                    else switch (i) {
                        case 0: value = geneId; break;
                        case 1: value = annotation.getAnnotationId(); break;
                        case 2: value = annotation.getTerm(); break;
                        case 3: value = annotation.getDescription(); break;
                        case 4:
                            value = type == AnnotationType.GO
                                ? annotation.getAttributes().getOrDefault("go_namespace", annotation.getEvidence())
                                : annotation.getEvidence();
                            break;
                        default: value = "";
                    }
                    if (validate && value != null
                        && (value.indexOf('\t') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0))
                        throw new IOException("Cannot save gene " + geneId + ", column " + headers[i]
                            + " (" + (i + 1) + "): value contains a tab or line break");
                    writer.print(value == null ? "" : value);
                }
                writer.println();
            }
        }
        writer.flush();
        if (writer.checkError()) throw new IOException("Failed writing annotation data");
    }

    private static MessageDigest newDigest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static byte[] digestFile(Path path) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16384];
            int length;
            while ((length = input.read(buffer)) >= 0) if (length != 0) digest.update(buffer, 0, length);
        }
        return digest.digest();
    }

    private byte[] digestAnnotations(AnnotationType type) throws IOException {
        MessageDigest digest = newDigest();
        try (Writer writer = new OutputStreamWriter(
                new DigestOutputStream(OutputStream.nullOutputStream(), digest), StandardCharsets.UTF_8)) {
            writeAnnotations(writer, type, false);
        }
        // Include edited standard fields even when the original dynamic schema
        // has no column for them, so a source copy cannot silently hide the edit.
        try (DataOutputStream state = new DataOutputStream(
                new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            Map<String, List<GeneAnnotation>> typed = annotations.get(type);
            for (String geneId : new TreeSet<>(typed.keySet())) {
                for (GeneAnnotation annotation : typed.get(geneId)) {
                    for (String value : annotation.standardFields().values()) {
                        if (value == null) state.writeInt(-1);
                        else {
                            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                            state.writeInt(bytes.length);
                            state.write(bytes);
                        }
                    }
                }
            }
        }
        return digest.digest();
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
