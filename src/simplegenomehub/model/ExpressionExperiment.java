/*
 * Expression Experiment Metadata Model
 */
package simplegenomehub.model;

import java.io.*;
import com.google.gson.*;
import simplegenomehub.util.fileio.JsonMetadataSupport;
import simplegenomehub.util.fileio.AtomicFiles;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Represents metadata for an expression experiment
 * Includes sample information, experimental conditions, and technical details
 * 
 * @author SimpleGenomeHub
 */
public class ExpressionExperiment {
    
    private static final Logger logger = Logger.getLogger(ExpressionExperiment.class.getName());
    
    public enum ExperimentType {
        RNA_SEQ("RNA-seq", "RNA Sequencing"),
        SINGLE_CELL("scRNA-seq", "Single Cell RNA Sequencing"),
        BULK_RNA("Bulk RNA-seq", "Bulk RNA Sequencing"),
        TIME_COURSE("Time Course", "Time Course Expression"),
        TISSUE_COMPARISON("Tissue Comparison", "Multi-tissue Expression"),
        TREATMENT_RESPONSE("Treatment Response", "Treatment Response Analysis");
        
        private final String shortName;
        private final String description;
        
        ExperimentType(String shortName, String description) {
            this.shortName = shortName;
            this.description = description;
        }
        
        public String getShortName() { return shortName; }
        public String getDescription() { return description; }
    }
    
    // Basic experiment information
    private String experimentId;
    private String title;
    private String description;
    private ExperimentType experimentType;
    private LocalDateTime importTime;
    private LocalDateTime experimentDate;
    private String investigator;
    private String institution;
    
    // Sample information
    private List<SampleInfo> samples;
    private Map<String, String> sampleGroups; // sample -> group mapping
    private Map<String, Map<String, String>> sampleAttributes; // sample -> attribute -> value
    
    // Technical information  
    private String platform; // sequencing platform
    private String libraryPrep; // library preparation method
    private String readType; // single-end/paired-end
    private String strandedness; // stranded/unstranded
    private Map<String, String> technicalDetails;
    
    // Associated data files
    private List<ExpressionMatrix> matrices;
    /**
     * Data type persisted in the experiment metadata.  The current UI imports
     * one primary matrix per experiment, so this field provides an explicit,
     * stable type value when the matrix is reloaded from disk.
     */
    private ExpressionMatrix.DataType storedDataType;
    private File metadataFile;
    private String notes;
    private JsonObject originalMetadata;
    
    /**
     * Sample information class
     */
    public static class SampleInfo {
        private String sampleId;
        private String sampleName;
        private String tissue;
        private String condition;
        private String treatment;
        private String timePoint;
        private String biologicalReplicate;
        private String technicalReplicate;
        private Map<String, String> customAttributes;
        
        public SampleInfo(String sampleId, String sampleName) {
            this.sampleId = sampleId;
            this.sampleName = sampleName;
            this.customAttributes = new HashMap<>();
        }
        
        // Getters and setters
        public String getSampleId() { return sampleId; }
        public String getSampleName() { return sampleName; }
        public void setSampleName(String sampleName) { this.sampleName = sampleName; }
        
        public String getTissue() { return tissue; }
        public void setTissue(String tissue) { this.tissue = tissue; }
        
        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }
        
        public String getTreatment() { return treatment; }
        public void setTreatment(String treatment) { this.treatment = treatment; }
        
        public String getTimePoint() { return timePoint; }
        public void setTimePoint(String timePoint) { this.timePoint = timePoint; }
        
        public String getBiologicalReplicate() { return biologicalReplicate; }
        public void setBiologicalReplicate(String biologicalReplicate) { 
            this.biologicalReplicate = biologicalReplicate; 
        }
        
        public String getTechnicalReplicate() { return technicalReplicate; }
        public void setTechnicalReplicate(String technicalReplicate) { 
            this.technicalReplicate = technicalReplicate; 
        }
        
        public Map<String, String> getCustomAttributes() { return customAttributes; }
        public void setCustomAttribute(String key, String value) { 
            customAttributes.put(key, value); 
        }
        
        @Override
        public String toString() {
            return sampleName + " (" + sampleId + ")";
        }
    }
    
    /**
     * Constructor
     */
    public ExpressionExperiment(String experimentId, String title) {
        this.experimentId = experimentId;
        this.title = title;
        this.importTime = LocalDateTime.now();
        this.samples = new ArrayList<>();
        this.sampleGroups = new HashMap<>();
        this.sampleAttributes = new HashMap<>();
        this.technicalDetails = new HashMap<>();
        this.matrices = new ArrayList<>();
        this.notes = "";
    }
    
    /**
     * Add sample to experiment
     */
    public void addSample(SampleInfo sample) {
        if (sample != null && !containsSample(sample.getSampleId())) {
            samples.add(sample);
            logger.info("Added sample: " + sample.getSampleId() + " to experiment: " + experimentId);
        }
    }
    
    /**
     * Remove sample from experiment
     */
    public boolean removeSample(String sampleId) {
        SampleInfo toRemove = null;
        for (SampleInfo sample : samples) {
            if (sample.getSampleId().equals(sampleId)) {
                toRemove = sample;
                break;
            }
        }
        
        if (toRemove != null) {
            samples.remove(toRemove);
            sampleGroups.remove(sampleId);
            sampleAttributes.remove(sampleId);
            logger.info("Removed sample: " + sampleId + " from experiment: " + experimentId);
            return true;
        }
        return false;
    }
    
    /**
     * Check if experiment contains sample
     */
    public boolean containsSample(String sampleId) {
        return samples.stream().anyMatch(s -> s.getSampleId().equals(sampleId));
    }
    
    /**
     * Get sample by ID
     */
    public SampleInfo getSample(String sampleId) {
        return samples.stream()
                .filter(s -> s.getSampleId().equals(sampleId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Add expression matrix to experiment
     */
    public void addMatrix(ExpressionMatrix matrix) {
        if (matrix != null && !matrices.contains(matrix)) {
            matrices.add(matrix);
            if (storedDataType == null) {
                storedDataType = matrix.getDataType();
            }
            logger.info("Added matrix: " + matrix.getName() + " to experiment: " + experimentId);
        }
    }
    
    /**
     * Remove expression matrix from experiment
     */
    public boolean removeMatrix(ExpressionMatrix matrix) {
        if (matrices.remove(matrix)) {
            logger.info("Removed matrix: " + matrix.getName() + " from experiment: " + experimentId);
            return true;
        }
        return false;
    }
    
    /**
     * Assign sample to group
     */
    public void assignSampleToGroup(String sampleId, String groupName) {
        if (containsSample(sampleId)) {
            sampleGroups.put(sampleId, groupName);
        }
    }
    
    /**
     * Get all unique groups
     */
    public Set<String> getAllGroups() {
        return new HashSet<>(sampleGroups.values());
    }
    
    /**
     * Get samples in a specific group
     */
    public List<SampleInfo> getSamplesInGroup(String groupName) {
        List<SampleInfo> groupSamples = new ArrayList<>();
        for (SampleInfo sample : samples) {
            String group = sampleGroups.get(sample.getSampleId());
            if (groupName.equals(group)) {
                groupSamples.add(sample);
            }
        }
        return groupSamples;
    }
    
    /**
     * Save experiment metadata to JSON file
     */
    public boolean saveMetadata(File file) {
        try {
            AtomicFiles.withLock(file.toPath(), () -> {
                JsonObject existing = file.isFile() && file.length() > 0 ? JsonMetadataSupport.read(file) : originalMetadata;
                JsonObject metadata = new JsonObject();
                metadata.addProperty("experiment_id", experimentId);
                metadata.addProperty("title", title);
                metadata.addProperty("description", description);
                metadata.addProperty("experiment_type", experimentType == null ? "" : experimentType.getShortName());
                metadata.addProperty("data_type", getStoredDataType() == null ? "" : getStoredDataType().getShortName());
                metadata.addProperty("import_time", importTime == null ? "" : importTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                metadata.addProperty("experiment_date", experimentDate == null ? "" : experimentDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                metadata.addProperty("investigator", investigator);
                metadata.addProperty("institution", institution);
                metadata.addProperty("platform", platform);
                metadata.addProperty("library_prep", libraryPrep);
                metadata.addProperty("read_type", readType);
                metadata.addProperty("strandedness", strandedness);
                metadata.addProperty("notes", notes);
                metadata.add("technical_details", new Gson().toJsonTree(technicalDetails));
                JsonArray sampleList = new JsonArray();
                for (SampleInfo sample : samples) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("sample_id", sample.sampleId);
                    entry.addProperty("sample_name", sample.sampleName);
                    entry.addProperty("tissue", sample.tissue);
                    entry.addProperty("condition", sample.condition);
                    entry.addProperty("treatment", sample.treatment);
                    entry.addProperty("time_point", sample.timePoint);
                    entry.addProperty("biological_replicate", sample.biologicalReplicate);
                    entry.addProperty("technical_replicate", sample.technicalReplicate);
                    entry.addProperty("group", sampleGroups.get(sample.sampleId));
                    sampleList.add(entry);
                }
                metadata.add("samples", sampleList);
                JsonObject merged = JsonMetadataSupport.merge(existing, metadata).getAsJsonObject();
                JsonMetadataSupport.write(file, merged);
                originalMetadata = merged;
                metadataFile = file;
                return null;
            });
            return true;
        } catch (IOException | RuntimeException failure) {
            logger.log(Level.SEVERE, "Failed to save experiment metadata", failure);
            return false;
        }
    }

    /** Update only the explicitly chosen legacy data type, keeping every other JSON field. */
    public void saveExplicitDataType(ExpressionMatrix.DataType type) throws IOException {
        if (type == null || metadataFile == null) throw new IOException("A data type and metadata file are required");
        AtomicFiles.withLock(metadataFile.toPath(), () -> {
            JsonObject current = JsonMetadataSupport.read(metadataFile);
            current.addProperty("data_type", type.getShortName());
            JsonMetadataSupport.write(metadataFile, current);
            originalMetadata = current;
            storedDataType = type;
            return null;
        });
    }

    public static ExpressionExperiment loadMetadata(File file) {
        if (file == null || !simplegenomehub.util.fileio.FileSetTransaction.isPublished(file.toPath())) return null;
        try {
            return loadMetadataChecked(file);
        } catch (Exception failure) {
            logger.log(Level.SEVERE, "Failed to load experiment metadata from " + file, failure);
            return null;
        }
    }

    /** Shared loaders need the original failure instead of an indistinguishable null. */
    public static ExpressionExperiment loadMetadataChecked(File file) throws IOException {
        if (file == null) throw new IOException("Expression metadata file is required");
        if (!simplegenomehub.util.fileio.FileSetTransaction.isPublished(file.toPath()))
            throw new IOException("Expression metadata publication is unfinished: " + file);
        try {
            JsonObject json = JsonMetadataSupport.read(file);
            String id = JsonMetadataSupport.text(json, "experiment_id");
            if (id.trim().isEmpty() || id.contains("/") || id.contains("\\") || id.equals(".") || id.equals(".."))
                throw new IOException("Missing or invalid experiment_id in " + file);
            ExpressionExperiment experiment = new ExpressionExperiment(id, JsonMetadataSupport.text(json, "title"));
            experiment.originalMetadata = json;
            experiment.metadataFile = file;
            experiment.description = JsonMetadataSupport.text(json, "description");
            experiment.storedDataType = parseDataType(JsonMetadataSupport.text(json, "data_type"));
            experiment.investigator = JsonMetadataSupport.text(json, "investigator");
            experiment.institution = JsonMetadataSupport.text(json, "institution");
            experiment.platform = JsonMetadataSupport.text(json, "platform");
            experiment.libraryPrep = JsonMetadataSupport.text(json, "library_prep");
            experiment.readType = JsonMetadataSupport.text(json, "read_type");
            experiment.strandedness = JsonMetadataSupport.text(json, "strandedness");
            experiment.notes = JsonMetadataSupport.text(json, "notes");
            String typeStr = JsonMetadataSupport.text(json, "experiment_type");
            for (ExperimentType type : ExperimentType.values()) if (type.getShortName().equals(typeStr)) experiment.experimentType = type;
            String imported = JsonMetadataSupport.text(json, "import_time");
            if (!imported.isEmpty()) {
                try { experiment.importTime = LocalDateTime.parse(imported); }
                catch (java.time.format.DateTimeParseException invalid) { logger.warning("Unrecognized legacy import date: " + imported); }
            }
            String dated = JsonMetadataSupport.text(json, "experiment_date");
            if (!dated.isEmpty()) {
                try { experiment.experimentDate = LocalDateTime.parse(dated); }
                catch (java.time.format.DateTimeParseException invalid) { logger.warning("Unrecognized legacy experiment date: " + dated); }
            }
            if (json.has("technical_details") && json.get("technical_details").isJsonObject())
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("technical_details").entrySet())
                    if (entry.getValue().isJsonPrimitive()) experiment.technicalDetails.put(entry.getKey(), entry.getValue().getAsString());
            if (json.has("samples") && json.get("samples").isJsonArray()) for (JsonElement value : json.getAsJsonArray("samples")) {
                JsonObject entry = value.getAsJsonObject();
                SampleInfo sample = new SampleInfo(JsonMetadataSupport.text(entry, "sample_id"), JsonMetadataSupport.text(entry, "sample_name"));
                sample.tissue = JsonMetadataSupport.text(entry, "tissue");
                sample.condition = JsonMetadataSupport.text(entry, "condition");
                sample.treatment = JsonMetadataSupport.text(entry, "treatment");
                sample.timePoint = JsonMetadataSupport.text(entry, "time_point");
                sample.biologicalReplicate = JsonMetadataSupport.text(entry, "biological_replicate");
                sample.technicalReplicate = JsonMetadataSupport.text(entry, "technical_replicate");
                experiment.samples.add(sample);
                experiment.sampleGroups.put(sample.sampleId, JsonMetadataSupport.text(entry, "group"));
            }
            return experiment;
        } catch (RuntimeException failure) {
            throw new IOException("Invalid expression metadata fields: " + file + ": " + failure.getMessage(), failure);
        }
    }

    private static ExpressionMatrix.DataType parseDataType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (ExpressionMatrix.DataType type : ExpressionMatrix.DataType.values()) {
            if (type.name().equals(normalized) || type.getShortName().toUpperCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Escape JSON special characters
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    // Getters and Setters
    public String getExperimentId() { return experimentId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public ExperimentType getExperimentType() { return experimentType; }
    public void setExperimentType(ExperimentType experimentType) { 
        this.experimentType = experimentType; 
    }
    
    public LocalDateTime getImportTime() { return importTime; }
    public LocalDateTime getExperimentDate() { return experimentDate; }
    public void setExperimentDate(LocalDateTime experimentDate) { 
        this.experimentDate = experimentDate; 
    }
    
    public String getInvestigator() { return investigator; }
    public void setInvestigator(String investigator) { this.investigator = investigator; }
    
    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }
    
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    
    public String getLibraryPrep() { return libraryPrep; }
    public void setLibraryPrep(String libraryPrep) { this.libraryPrep = libraryPrep; }
    
    public String getReadType() { return readType; }
    public void setReadType(String readType) { this.readType = readType; }
    
    public String getStrandedness() { return strandedness; }
    public void setStrandedness(String strandedness) { this.strandedness = strandedness; }
    
    public Map<String, String> getTechnicalDetails() { return technicalDetails; }
    public void setTechnicalDetail(String key, String value) { 
        technicalDetails.put(key, value); 
    }
    
    public List<SampleInfo> getSamples() { return new ArrayList<>(samples); }
    public int getSampleCount() { return samples.size(); }
    
    /**
     * Get all sample names from matrices
     */
    public List<String> getSampleNames() {
        List<String> sampleNames = new ArrayList<>();
        if (!matrices.isEmpty()) {
            String[] names = matrices.get(0).getSampleNames();
            if (names != null) {
                sampleNames.addAll(Arrays.asList(names));
            }
        }
        return sampleNames;
    }
    
    public List<ExpressionMatrix> getMatrices() { return new ArrayList<>(matrices); }

    /**
     * Return the explicitly persisted matrix type.  Unlike the matrix object,
     * this value remains available before the matrix file is reloaded.
     */
    public ExpressionMatrix.DataType getStoredDataType() {
        if (storedDataType != null) {
            return storedDataType;
        }
        if (!matrices.isEmpty() && matrices.get(0) != null) {
            return matrices.get(0).getDataType();
        }
        return null;
    }

    public void setStoredDataType(ExpressionMatrix.DataType dataType) {
        this.storedDataType = dataType;
    }
    public int getMatrixCount() { return matrices.size(); }
    
    public File getMetadataFile() { return metadataFile; }
    /** Rebind a successfully published experiment from its staging file to its final metadata. */
    public void setMetadataFile(File file) { this.metadataFile = file; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    @Override
    public String toString() {
        return title + " (" + experimentId + ", " + getSampleCount() + " samples)";
    }
}
