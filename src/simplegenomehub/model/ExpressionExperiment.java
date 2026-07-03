/*
 * Expression Experiment Metadata Model
 */
package simplegenomehub.model;

import java.io.*;
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
    private File metadataFile;
    private String notes;
    
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
        this.metadataFile = file;
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("{");
            writer.println("  \"experiment_id\": \"" + escapeJson(experimentId) + "\",");
            writer.println("  \"title\": \"" + escapeJson(title) + "\",");
            writer.println("  \"description\": \"" + escapeJson(description) + "\",");
            writer.println("  \"experiment_type\": \"" + experimentType.getShortName() + "\",");
            writer.println("  \"import_time\": \"" + importTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",");
            
            if (experimentDate != null) {
                writer.println("  \"experiment_date\": \"" + experimentDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\",");
            }
            
            writer.println("  \"investigator\": \"" + escapeJson(investigator) + "\",");
            writer.println("  \"institution\": \"" + escapeJson(institution) + "\",");
            writer.println("  \"platform\": \"" + escapeJson(platform) + "\",");
            writer.println("  \"library_prep\": \"" + escapeJson(libraryPrep) + "\",");
            writer.println("  \"read_type\": \"" + escapeJson(readType) + "\",");
            writer.println("  \"strandedness\": \"" + escapeJson(strandedness) + "\",");
            
            // Technical details
            writer.println("  \"technical_details\": {");
            boolean first = true;
            for (Map.Entry<String, String> entry : technicalDetails.entrySet()) {
                if (!first) writer.println(",");
                writer.print("    \"" + escapeJson(entry.getKey()) + "\": \"" + escapeJson(entry.getValue()) + "\"");
                first = false;
            }
            writer.println();
            writer.println("  },");
            
            // Samples
            writer.println("  \"samples\": [");
            for (int i = 0; i < samples.size(); i++) {
                SampleInfo sample = samples.get(i);
                writer.println("    {");
                writer.println("      \"sample_id\": \"" + escapeJson(sample.getSampleId()) + "\",");
                writer.println("      \"sample_name\": \"" + escapeJson(sample.getSampleName()) + "\",");
                writer.println("      \"tissue\": \"" + escapeJson(sample.getTissue()) + "\",");
                writer.println("      \"condition\": \"" + escapeJson(sample.getCondition()) + "\",");
                writer.println("      \"treatment\": \"" + escapeJson(sample.getTreatment()) + "\",");
                writer.println("      \"time_point\": \"" + escapeJson(sample.getTimePoint()) + "\",");
                writer.println("      \"biological_replicate\": \"" + escapeJson(sample.getBiologicalReplicate()) + "\",");
                writer.println("      \"technical_replicate\": \"" + escapeJson(sample.getTechnicalReplicate()) + "\",");
                writer.println("      \"group\": \"" + escapeJson(sampleGroups.get(sample.getSampleId())) + "\"");
                writer.print("    }");
                if (i < samples.size() - 1) writer.println(",");
            }
            writer.println();
            writer.println("  ],");
            
            writer.println("  \"notes\": \"" + escapeJson(notes) + "\"");
            writer.println("}");
            
            logger.info("Experiment metadata saved to: " + file.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save experiment metadata", e);
            return false;
        }
    }
    
    /**
     * Load experiment metadata from JSON file
     */
    public static ExpressionExperiment loadMetadata(File file) {
        // Simple JSON parsing - in production should use proper JSON library
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            
            String json = content.toString();
            
            // Extract basic fields
            String experimentId = extractJsonValue(json, "experiment_id");
            String title = extractJsonValue(json, "title");
            
            ExpressionExperiment experiment = new ExpressionExperiment(experimentId, title);
            experiment.metadataFile = file;
            experiment.description = extractJsonValue(json, "description");
            experiment.investigator = extractJsonValue(json, "investigator");
            experiment.institution = extractJsonValue(json, "institution");
            experiment.platform = extractJsonValue(json, "platform");
            experiment.libraryPrep = extractJsonValue(json, "library_prep");
            experiment.readType = extractJsonValue(json, "read_type");
            experiment.strandedness = extractJsonValue(json, "strandedness");
            experiment.notes = extractJsonValue(json, "notes");
            
            // Parse experiment type
            String typeStr = extractJsonValue(json, "experiment_type");
            for (ExperimentType type : ExperimentType.values()) {
                if (type.getShortName().equals(typeStr)) {
                    experiment.experimentType = type;
                    break;
                }
            }
            
            logger.info("Loaded experiment metadata: " + experimentId);
            return experiment;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load experiment metadata from " + file, e);
            return null;
        }
    }
    
    /**
     * Simple JSON value extraction
     */
    private static String extractJsonValue(String json, String key) {
        String keyPattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(keyPattern);
        if (keyIndex == -1) return "";
        
        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return "";
        
        int startIndex = json.indexOf("\"", colonIndex) + 1;
        if (startIndex == 0) return "";
        
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) return "";
        
        return json.substring(startIndex, endIndex);
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
    public int getMatrixCount() { return matrices.size(); }
    
    public File getMetadataFile() { return metadataFile; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    @Override
    public String toString() {
        return title + " (" + experimentId + ", " + getSampleCount() + " samples)";
    }
}