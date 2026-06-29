/*
 * Species Information Data Model
 */
package simplegenomehub.model;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Data model for species information in SimpleGenomeHub
 * Represents a species with its genome data and metadata
 * 
 * @author SimpleGenomeHub
 */
public class SpeciesInfo {
    
    private static final Logger logger = Logger.getLogger(SpeciesInfo.class.getName());
    
    private String speciesName;
    private String version;
    private LocalDateTime importTime;
    private String notes;
    private File speciesDir;
    private GenomeData genomeData;
    
    // File paths within species directory
    private File sequenceDir;
    private File annotationDir;
    private File statsFile;
    
    // Expression and functional annotation data
    private File expressionDir;
    private File functionalAnnotationDir;
    private File geneSetDir;
    private List<ExpressionExperiment> expressionExperiments;
    private GeneAnnotationData functionalAnnotations;
    
    // Type-specific annotation directories
    private Map<GeneAnnotationData.AnnotationType, File> annotationTypeDirs;
    private Map<GeneAnnotationData.AnnotationType, AnnotationMetadata> annotationMetadata;
    
    /**
     * Constructor for new species
     */
    public SpeciesInfo(String speciesName, String version) {
        this.speciesName = speciesName;
        this.version = version;
        this.importTime = LocalDateTime.now();
        this.notes = "";
        this.expressionExperiments = new ArrayList<>();
        this.annotationTypeDirs = new HashMap<>();
        this.annotationMetadata = new HashMap<>();
    }
    
    /**
     * Constructor for loading existing species
     */
    public SpeciesInfo(String speciesName, String version, LocalDateTime importTime, String notes) {
        this.speciesName = speciesName;
        this.version = version;
        this.importTime = importTime;
        this.notes = notes != null ? notes : "";
        this.expressionExperiments = new ArrayList<>();
        this.annotationTypeDirs = new HashMap<>();
        this.annotationMetadata = new HashMap<>();
    }
    
    /**
     * Initialize file structure based on species directory
     */
    public void initializeFileStructure(File rootDir) {
        if (rootDir == null || !rootDir.exists()) {
            throw new IllegalArgumentException("Root directory must exist");
        }
        
        String dirName = getSpeciesDirectoryName();
        this.speciesDir = new File(rootDir, dirName);
        
        // Create subdirectories
        this.sequenceDir = new File(speciesDir, "Sequence");
        this.annotationDir = new File(speciesDir, "Annotation");
        this.statsFile = new File(speciesDir, "stat.txt");
        
        // Expression and functional annotation directories
        this.expressionDir = new File(speciesDir, "Expression");
        this.functionalAnnotationDir = new File(speciesDir, "FunctionalAnnotation");
        this.geneSetDir = new File(speciesDir, "GeneSet");
        
        // Initialize type-specific annotation directories
        initializeAnnotationTypeDirectories();
        
        // Create directories if they don't exist
        createDirectoryStructure();
    }
    
    /**
     * Initialize type-specific annotation directories
     */
    private void initializeAnnotationTypeDirectories() {
        if (functionalAnnotationDir == null) return;
        
        for (GeneAnnotationData.AnnotationType type : GeneAnnotationData.AnnotationType.values()) {
            File typeDir = new File(functionalAnnotationDir, type.getShortName());
            annotationTypeDirs.put(type, typeDir);
        }
    }
    
    /**
     * Create the directory structure for this species
     */
    public boolean createDirectoryStructure() {
        if (speciesDir == null) {
            return false;
        }
        
        try {
            if (!speciesDir.exists()) {
                speciesDir.mkdirs();
            }
            if (!sequenceDir.exists()) {
                sequenceDir.mkdirs();
            }
            if (!annotationDir.exists()) {
                annotationDir.mkdirs();
            }
            if (!expressionDir.exists()) {
                expressionDir.mkdirs();
            }
            if (!functionalAnnotationDir.exists()) {
                functionalAnnotationDir.mkdirs();
            }
            if (!geneSetDir.exists()) {
                geneSetDir.mkdirs();
            }
            
            // Create type-specific annotation directories
            for (File typeDir : annotationTypeDirs.values()) {
                if (!typeDir.exists()) {
                    typeDir.mkdirs();
                }
            }
            
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
    
    /**
     * Get the directory name for this species (Species.version format)
     */
    public String getSpeciesDirectoryName() {
        return speciesName + "." + version;
    }
    
    /**
     * Get display name for UI (includes import time and notes if available)
     */
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder();
        sb.append(speciesName).append(" (").append(version).append(")");
        
        if (importTime != null) {
            sb.append(" - ").append(importTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
        
        if (notes != null && !notes.trim().isEmpty()) {
            sb.append(" - ").append(notes);
        }
        
        return sb.toString();
    }
    
    /**
     * Check if species data files exist
     */
    public boolean isComplete() {
        return speciesDir != null && 
               speciesDir.exists() && 
               sequenceDir.exists() && 
               annotationDir.exists() &&
               hasGenomeFiles();
    }
    
    /**
     * Check if genome files exist in sequence directory
     */
    public boolean hasGenomeFiles() {
        if (sequenceDir == null || !sequenceDir.exists()) {
            return false;
        }
        
        File[] files = sequenceDir.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".fa") || 
            name.toLowerCase().endsWith(".fasta") ||
            name.toLowerCase().endsWith(".fas"));
        
        return files != null && files.length > 0;
    }
    
    /**
     * Check if annotation files exist
     */
    public boolean hasAnnotationFiles() {
        if (annotationDir == null || !annotationDir.exists()) {
            return false;
        }
        
        File[] files = annotationDir.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".gff") || 
            name.toLowerCase().endsWith(".gff3") ||
            name.toLowerCase().endsWith(".gtf"));
        
        return files != null && files.length > 0;
    }
    
    // Getters and Setters
    public String getSpeciesName() {
        return speciesName;
    }
    
    public void setSpeciesName(String speciesName) {
        this.speciesName = speciesName;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public LocalDateTime getImportTime() {
        return importTime;
    }
    
    public void setImportTime(LocalDateTime importTime) {
        this.importTime = importTime;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public File getSpeciesDir() {
        return speciesDir;
    }
    
    public File getSequenceDir() {
        return sequenceDir;
    }
    
    public File getAnnotationDir() {
        return annotationDir;
    }
    
    public File getStatsFile() {
        return statsFile;
    }
    
    public GenomeData getGenomeData() {
        return genomeData;
    }
    
    public void setGenomeData(GenomeData genomeData) {
        this.genomeData = genomeData;
    }
    
    /**
     * Get first genome file from sequence directory
     */
    public File getGenomeFile() {
        if (sequenceDir == null || !sequenceDir.exists()) {
            return null;
        }
        
        File[] genomeFiles = sequenceDir.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".fa") || 
            name.toLowerCase().endsWith(".fasta") ||
            name.toLowerCase().endsWith(".fas"));
        
        if (genomeFiles == null || genomeFiles.length == 0) {
            return null;
        }
        
        // Prefer files with "genome" in the name over "cds", "transcript", etc.
        for (File file : genomeFiles) {
            String fileName = file.getName().toLowerCase();
            if (fileName.contains("genome")) {
                return file;
            }
        }
        
        // If no "genome" file found, return the first available
        return genomeFiles[0];
    }
    
    /**
     * Get first annotation file from annotation directory
     */
    public File getAnnotationFile() {
        if (annotationDir == null || !annotationDir.exists()) {
            return null;
        }
        
        File[] annotationFiles = annotationDir.listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".gff") || 
            name.toLowerCase().endsWith(".gff3") ||
            name.toLowerCase().endsWith(".gtf"));
        
        return (annotationFiles != null && annotationFiles.length > 0) ? annotationFiles[0] : null;
    }
    
    // Expression data management methods
    
    /**
     * Add expression experiment to this species
     */
    public boolean addExpressionExperiment(ExpressionExperiment experiment) {
        if (experiment == null) {
            return false;
        }
        
        // Check if experiment already exists
        String experimentId = experiment.getExperimentId();
        for (ExpressionExperiment existing : expressionExperiments) {
            if (existing.getExperimentId().equals(experimentId)) {
                logger.warning("Expression experiment already exists: " + experimentId);
                return false;
            }
        }
        
        expressionExperiments.add(experiment);
        logger.info("Added expression experiment: " + experimentId + " to species: " + getSpeciesDirectoryName());
        return true;
    }
    
    /**
     * Remove expression experiment
     */
    public boolean removeExpressionExperiment(String experimentId) {
        ExpressionExperiment toRemove = null;
        for (ExpressionExperiment experiment : expressionExperiments) {
            if (experiment.getExperimentId().equals(experimentId)) {
                toRemove = experiment;
                break;
            }
        }
        
        if (toRemove != null) {
            expressionExperiments.remove(toRemove);
            logger.info("Removed expression experiment: " + experimentId + " from species: " + getSpeciesDirectoryName());
            return true;
        }
        return false;
    }
    
    /**
     * Remove expression experiment by object
     */
    public boolean removeExpressionExperiment(ExpressionExperiment experiment) {
        if (experiment != null && expressionExperiments.remove(experiment)) {
            logger.info("Removed expression experiment: " + experiment.getExperimentId() + " from species: " + getSpeciesDirectoryName());
            return true;
        }
        return false;
    }
    
    /**
     * Get expression experiment by ID
     */
    public ExpressionExperiment getExpressionExperiment(String experimentId) {
        for (ExpressionExperiment experiment : expressionExperiments) {
            if (experiment.getExperimentId().equals(experimentId)) {
                return experiment;
            }
        }
        return null;
    }
    
    /**
     * Get all expression experiments
     */
    public List<ExpressionExperiment> getExpressionExperiments() {
        return new ArrayList<>(expressionExperiments);
    }
    
    /**
     * Check if species has expression data
     */
    public boolean hasExpressionData() {
        return !expressionExperiments.isEmpty() || 
               (expressionDir != null && expressionDir.exists() && 
                expressionDir.listFiles() != null && expressionDir.listFiles().length > 0);
    }
    
    /**
     * Get all expression matrices across all experiments
     */
    public List<ExpressionMatrix> getAllExpressionMatrices() {
        List<ExpressionMatrix> allMatrices = new ArrayList<>();
        for (ExpressionExperiment experiment : expressionExperiments) {
            allMatrices.addAll(experiment.getMatrices());
        }
        return allMatrices;
    }
    
    // Functional annotation management methods
    
    /**
     * Set functional annotation data for this species
     */
    public void setFunctionalAnnotations(GeneAnnotationData annotations) {
        this.functionalAnnotations = annotations;
        if (annotations != null) {
            logger.info("Set functional annotations for species: " + getSpeciesDirectoryName() + 
                       " (" + annotations.getAnnotatedGenes() + " genes)");
        }
    }
    
    /**
     * Get functional annotation data
     */
    public GeneAnnotationData getFunctionalAnnotations() {
        return functionalAnnotations;
    }
    
    /**
     * Check if species has functional annotations
     * Uses the same logic as getAvailableAnnotationTypes() for consistency
     */
    public boolean hasFunctionalAnnotations() {
        // First check if annotations are already loaded in memory
        if (functionalAnnotations != null && functionalAnnotations.getAnnotatedGenes() > 0) {
            return true;
        }
        
        // Use the same logic as getAvailableAnnotationTypes() for consistency
        Set<GeneAnnotationData.AnnotationType> availableTypes = getAvailableAnnotationTypes();
        boolean hasFiles = !availableTypes.isEmpty();
        
        System.out.println("DEBUG SpeciesInfo.hasFunctionalAnnotations() for " + getSpeciesDirectoryName() + ": " + 
                          "functionalAnnotations=" + (functionalAnnotations != null) + 
                          ", annotatedGenes=" + (functionalAnnotations != null ? functionalAnnotations.getAnnotatedGenes() : "null") + 
                          ", functionalAnnotationDir=" + (functionalAnnotationDir != null ? functionalAnnotationDir.getAbsolutePath() : "null") +
                          ", dirExists=" + (functionalAnnotationDir != null ? functionalAnnotationDir.exists() : "false") +
                          ", availableTypes=" + availableTypes +
                          ", result=" + hasFiles);
        
        return hasFiles;
    }
    
    /**
     * Prepare functional annotations metadata for lazy loading
     * This method only loads metadata, not the actual annotation data
     */
    public boolean loadFunctionalAnnotations() {
        System.out.println("DEBUG loadFunctionalAnnotations() for " + getSpeciesDirectoryName() + ": " +
                          "functionalAnnotationDir=" + (functionalAnnotationDir != null ? functionalAnnotationDir.getAbsolutePath() : "null") +
                          ", exists=" + (functionalAnnotationDir != null ? functionalAnnotationDir.exists() : "false"));
        
        if (functionalAnnotationDir == null || !functionalAnnotationDir.exists()) {
            return false;
        }
        
        try {
            boolean hasAnyAnnotations = false;
            
            // Prepare metadata for type-specific directories (new structure)
            for (GeneAnnotationData.AnnotationType type : GeneAnnotationData.AnnotationType.values()) {
                File typeDir = annotationTypeDirs.get(type);
                if (typeDir != null && typeDir.exists()) {
                    File dataFile = new File(typeDir, type.getShortName() + ".tsv");
                    if (dataFile.exists()) {
                        // Load or generate metadata for this type
                        if (!loadAnnotationMetadata(type)) {
                            // Generate metadata if not available
                            logger.info("Generating metadata for " + type.getShortName());
                            AnnotationMetadata metadata = AnnotationMetadata.analyzeFile(dataFile, type);
                            setAnnotationMetadata(type, metadata);
                        }
                        
                        hasAnyAnnotations = true;
                        logger.info("Prepared metadata for " + type.getShortName() + " annotations: " + dataFile.getName());
                    }
                }
            }
            
            // Fallback: Handle legacy flat structure for backward compatibility
            if (!hasAnyAnnotations) {
                hasAnyAnnotations = prepareLegacyAnnotationMetadata();
            }
            
            // Create a minimal GeneAnnotationData object for compatibility
            if (hasAnyAnnotations) {
                String datasetId = getSpeciesDirectoryName() + "_annotations";
                GeneAnnotationData annotations = new GeneAnnotationData(datasetId, 
                    speciesName + " " + version + " Annotations");
                
                // Set basic info without loading actual data
                setFunctionalAnnotations(annotations);
                
                logger.info("Prepared annotation metadata for species: " + getSpeciesDirectoryName());
                return true;
            }
            
        } catch (Exception e) {
            logger.warning("Failed to prepare functional annotation metadata: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Prepare metadata for legacy flat annotation structure
     */
    private boolean prepareLegacyAnnotationMetadata() {
        boolean hasAnyAnnotations = false;
        
        try {
            // Check for legacy flat files
            File[] legacyFiles = functionalAnnotationDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".tsv") && 
                (name.toLowerCase().startsWith("go") || 
                 name.toLowerCase().startsWith("kegg") ||
                 name.toLowerCase().startsWith("custom") ||
                 name.toLowerCase().startsWith("interpro") ||
                 name.toLowerCase().startsWith("pfam")));
            
            if (legacyFiles != null) {
                for (File legacyFile : legacyFiles) {
                    String fileName = legacyFile.getName().toLowerCase();
                    GeneAnnotationData.AnnotationType type = null;
                    
                    if (fileName.startsWith("go")) {
                        type = GeneAnnotationData.AnnotationType.GO;
                    } else if (fileName.startsWith("kegg")) {
                        type = GeneAnnotationData.AnnotationType.KEGG;
                    } else if (fileName.startsWith("custom")) {
                        type = GeneAnnotationData.AnnotationType.CUSTOM;
                    } else if (fileName.startsWith("interpro")) {
                        type = GeneAnnotationData.AnnotationType.INTERPRO;
                    } else if (fileName.startsWith("pfam")) {
                        type = GeneAnnotationData.AnnotationType.PFAM;
                    }
                    
                    if (type != null) {
                        logger.info("Generating metadata for legacy file: " + legacyFile.getName());
                        AnnotationMetadata metadata = AnnotationMetadata.analyzeFile(legacyFile, type);
                        setAnnotationMetadata(type, metadata);
                        hasAnyAnnotations = true;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warning("Failed to prepare legacy annotation metadata: " + e.getMessage());
        }
        
        return hasAnyAnnotations;
    }
    
    // Legacy loading methods removed - replaced by metadata-driven system
    
    /**
     * Save functional annotations to directory
     */
    public boolean saveFunctionalAnnotations() {
        if (functionalAnnotations == null || functionalAnnotationDir == null) {
            return false;
        }
        
        try {
            // Ensure all type-specific directories exist
            createDirectoryStructure();
            
            // Save all annotation types to their respective directories
            Map<GeneAnnotationData.AnnotationType, Integer> counts = functionalAnnotations.getAnnotationCounts();
            boolean savedAny = false;
            
            for (GeneAnnotationData.AnnotationType type : GeneAnnotationData.AnnotationType.values()) {
                int count = counts.getOrDefault(type, 0);
                if (count > 0) {
                    File typeDir = annotationTypeDirs.get(type);
                    if (typeDir != null) {
                        File dataFile = new File(typeDir, type.getShortName() + ".tsv");
                        if (functionalAnnotations.saveToFile(dataFile, type)) {
                            savedAny = true;
                            logger.info("Saved " + count + " " + type.getShortName() + " annotations to: " + dataFile.getName());
                            
                            // Save metadata if available
                            AnnotationMetadata metadata = annotationMetadata.get(type);
                            if (metadata != null) {
                                File metadataFile = new File(typeDir, type.getShortName() + ".metadata.json");
                                metadata.saveToFile(metadataFile);
                            }
                        }
                    }
                }
            }
            
            if (savedAny) {
                logger.info("Saved functional annotations for species: " + getSpeciesDirectoryName());
                return true;
            }
            
        } catch (Exception e) {
            logger.warning("Failed to save functional annotations: " + e.getMessage());
            return false;
        }
        
        return false;
    }
    
    /**
     * Get genes that have both expression and annotation data
     */
    public Set<String> getIntegratedGenes() {
        Set<String> integratedGenes = new HashSet<>();
        
        if (functionalAnnotations != null) {
            Set<String> annotatedGenes = functionalAnnotations.getAllAnnotatedGenes();
            
            for (ExpressionExperiment experiment : expressionExperiments) {
                for (ExpressionMatrix matrix : experiment.getMatrices()) {
                    String[] geneIds = matrix.getGeneIds();
                    if (geneIds != null) {
                        for (String geneId : geneIds) {
                            if (annotatedGenes.contains(geneId)) {
                                integratedGenes.add(geneId);
                            }
                        }
                    }
                }
            }
        }
        
        return integratedGenes;
    }
    
    // Type-specific annotation directory and metadata management
    
    /**
     * Get type-specific annotation directory
     */
    public File getAnnotationTypeDirectory(GeneAnnotationData.AnnotationType type) {
        return annotationTypeDirs.get(type);
    }
    
    /**
     * Get annotation metadata for specific type
     */
    public AnnotationMetadata getAnnotationMetadata(GeneAnnotationData.AnnotationType type) {
        return annotationMetadata.get(type);
    }
    
    /**
     * Set annotation metadata for specific type
     */
    public void setAnnotationMetadata(GeneAnnotationData.AnnotationType type, AnnotationMetadata metadata) {
        annotationMetadata.put(type, metadata);
        
        // Save metadata to file
        File typeDir = annotationTypeDirs.get(type);
        if (typeDir != null && typeDir.exists()) {
            File metadataFile = new File(typeDir, type.getShortName() + ".metadata.json");
            metadata.saveToFile(metadataFile);
        }
    }
    
    /**
     * Load annotation metadata for specific type
     */
    public boolean loadAnnotationMetadata(GeneAnnotationData.AnnotationType type) {
        File typeDir = annotationTypeDirs.get(type);
        if (typeDir == null || !typeDir.exists()) {
            return false;
        }
        
        File metadataFile = new File(typeDir, type.getShortName() + ".metadata.json");
        if (!metadataFile.exists()) {
            return false;
        }
        
        AnnotationMetadata metadata = AnnotationMetadata.loadFromFile(metadataFile);
        if (metadata != null) {
            annotationMetadata.put(type, metadata);
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if annotation type has metadata
     */
    public boolean hasAnnotationMetadata(GeneAnnotationData.AnnotationType type) {
        return annotationMetadata.containsKey(type) || 
               (annotationTypeDirs.get(type) != null && 
                new File(annotationTypeDirs.get(type), type.getShortName() + ".metadata.json").exists());
    }
    
    /**
     * Get all annotation types with available data (either loaded or files exist)
     * Supports both new type-specific directories and legacy flat structure
     */
    public Set<GeneAnnotationData.AnnotationType> getAvailableAnnotationTypes() {
        Set<GeneAnnotationData.AnnotationType> availableTypes = new HashSet<>();
        
        // Check new type-specific directory structure
        for (GeneAnnotationData.AnnotationType type : GeneAnnotationData.AnnotationType.values()) {
            File typeDir = annotationTypeDirs.get(type);
            if (typeDir != null && typeDir.exists()) {
                // Check for data file in type-specific directory
                File dataFile = new File(typeDir, type.getShortName() + ".tsv");
                if (dataFile.exists()) {
                    availableTypes.add(type);
                }
            }
        }
        
        // Fallback: Check legacy flat structure if no type-specific files found  
        if (availableTypes.isEmpty() && functionalAnnotationDir != null && functionalAnnotationDir.exists()) {
            File[] legacyFiles = functionalAnnotationDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".tsv"));
            
            if (legacyFiles != null) {
                for (File legacyFile : legacyFiles) {
                    String fileName = legacyFile.getName().toLowerCase();
                    
                    if (fileName.startsWith("go")) {
                        availableTypes.add(GeneAnnotationData.AnnotationType.GO);
                    } else if (fileName.startsWith("kegg")) {
                        availableTypes.add(GeneAnnotationData.AnnotationType.KEGG);
                    } else if (fileName.startsWith("custom")) {
                        availableTypes.add(GeneAnnotationData.AnnotationType.CUSTOM);
                    } else if (fileName.startsWith("interpro")) {
                        availableTypes.add(GeneAnnotationData.AnnotationType.INTERPRO);
                    } else if (fileName.startsWith("pfam")) {
                        availableTypes.add(GeneAnnotationData.AnnotationType.PFAM);
                    }
                }
            }
        }
        
        return availableTypes;
    }
    
    /**
     * Get annotation statistics summary for all types
     */
    public Map<GeneAnnotationData.AnnotationType, String> getAnnotationStatisticsSummary() {
        Map<GeneAnnotationData.AnnotationType, String> summary = new HashMap<>();
        
        for (GeneAnnotationData.AnnotationType type : getAvailableAnnotationTypes()) {
            AnnotationMetadata metadata = getAnnotationMetadata(type);
            if (metadata == null) {
                // Try to load metadata
                loadAnnotationMetadata(type);
                metadata = getAnnotationMetadata(type);
            }
            
            if (metadata != null) {
                summary.put(type, String.format("%d genes, %d annotations", 
                    metadata.getUniqueGenes(), metadata.getTotalAnnotations()));
            } else {
                summary.put(type, "Available (metadata not loaded)");
            }
        }
        
        return summary;
    }
    
    // Additional getters for new directories
    
    public File getExpressionDir() {
        return expressionDir;
    }
    
    public File getFunctionalAnnotationDir() {
        return functionalAnnotationDir;
    }

    public File getGeneSetDir() {
        return geneSetDir;
    }

    public boolean hasGeneSets() {
        if (geneSetDir == null || !geneSetDir.exists()) {
            return false;
        }

        File[] geneSetFiles = geneSetDir.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase(Locale.ROOT);
            return lowerName.endsWith(".gene.txt") || lowerName.endsWith(".region.txt");
        });
        return geneSetFiles != null && geneSetFiles.length > 0;
    }
    
    public int getExpressionExperimentCount() {
        return expressionExperiments.size();
    }
    
    public int getTotalExpressionMatrixCount() {
        return getAllExpressionMatrices().size();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpeciesInfo that = (SpeciesInfo) o;
        return Objects.equals(speciesName, that.speciesName) &&
               Objects.equals(version, that.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(speciesName, version);
    }
    
    @Override
    public String toString() {
        return getDisplayName();
    }
}
