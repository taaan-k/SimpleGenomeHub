/*
 * SimpleGenomeHub BLAST Database Model
 * Represents a BLAST database with metadata and validation capabilities
 */
package simplegenomehub.blast.model;

import simplegenomehub.blast.BlastConfig;
import simplegenomehub.blast.BlastConfig.SequenceType;
import simplegenomehub.blast.BlastConfig.MolecularType;
import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Model class representing a BLAST database
 * Handles database metadata, validation, and integrity checking
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastDatabase {
    
    private static final Logger logger = Logger.getLogger(BlastDatabase.class.getName());
    private static final String METADATA_FILE_NAME = "database.info";
    
    private File databaseDir;
    private String databaseName;
    private SequenceType sequenceType;
    private MolecularType molecularType;
    private long creationTime;
    private long sourceFileModificationTime;
    private long sourceFileSize;
    private int sequenceCount;
    private String sourceFilePath;
    private Map<String, String> customMetadata;
    
    /**
     * Create a new BlastDatabase instance
     */
    public BlastDatabase(File databaseDir, SequenceType sequenceType, 
                        File sourceFile) throws IOException {
        this.databaseDir = databaseDir;
        this.databaseName = sequenceType.toString().toLowerCase();
        this.sequenceType = sequenceType;
        this.molecularType = sequenceType.getMolecularType();
        this.creationTime = System.currentTimeMillis();
        this.sourceFileModificationTime = sourceFile.lastModified();
        this.sourceFileSize = sourceFile.length();
        this.sourceFilePath = sourceFile.getAbsolutePath();
        this.customMetadata = new HashMap<>();
        
        // Count sequences in source file
        this.sequenceCount = countSequencesInFasta(sourceFile);
        
        // Save metadata
        saveMetadata();
    }
    
    /**
     * Load existing BlastDatabase from disk
     */
    public static BlastDatabase loadFromDisk(File databaseDir, SequenceType sequenceType) 
            throws IOException {
        File metadataFile = new File(databaseDir, METADATA_FILE_NAME);
        if (!metadataFile.exists()) {
            return null;
        }
        
        Properties props = new Properties();
        try (InputStream is = new FileInputStream(metadataFile)) {
            props.load(is);
        }
        
        BlastDatabase db = new BlastDatabase();
        db.databaseDir = databaseDir;
        db.databaseName = sequenceType.toString().toLowerCase();
        db.sequenceType = sequenceType;
        db.molecularType = MolecularType.valueOf(props.getProperty("molecular.type"));
        db.creationTime = Long.parseLong(props.getProperty("creation.time"));
        db.sourceFileModificationTime = Long.parseLong(props.getProperty("source.modification.time"));
        db.sourceFileSize = Long.parseLong(props.getProperty("source.file.size", "0"));
        db.sequenceCount = Integer.parseInt(props.getProperty("sequence.count", "0"));
        db.sourceFilePath = props.getProperty("source.file.path", "");
        db.customMetadata = new HashMap<>();
        
        // Load custom metadata
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("custom.")) {
                db.customMetadata.put(key.substring(7), props.getProperty(key));
            }
        }
        
        return db;
    }
    
    /**
     * Private constructor for loading from disk
     */
    private BlastDatabase() {
        // Used for loading from disk
    }
    
    /**
     * Check if database is valid (all required files exist)
     */
    public boolean isValid() {
        return databaseDir.exists() && 
               hasRequiredFiles() && 
               !isCorrupted();
    }
    
    /**
     * Check if database is up to date compared to source file
     */
    public boolean isUpToDate(File sourceFile) {
        if (!sourceFile.exists()) {
            return false;
        }
        
        return sourceFile.lastModified() <= sourceFileModificationTime &&
               sourceFile.length() == sourceFileSize;
    }
    
    /**
     * Check if all required database files exist
     */
    private boolean hasRequiredFiles() {
        String[] extensions = molecularType.getRequiredExtensions();
        
        for (String ext : extensions) {
            File dbFile = new File(databaseDir, databaseName + ext);
            if (!dbFile.exists() || dbFile.length() == 0) {
                logger.warning("Missing database file: " + dbFile.getAbsolutePath());
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if database files are corrupted
     */
    private boolean isCorrupted() {
        // Basic corruption check - ensure files have reasonable sizes
        String[] extensions = molecularType.getRequiredExtensions();
        
        for (String ext : extensions) {
            File dbFile = new File(databaseDir, databaseName + ext);
            if (dbFile.exists() && dbFile.length() < 10) {
                logger.warning("Database file may be corrupted (file too small): " + dbFile.getAbsolutePath());
                return true;
            }
        }
        
        // Check if .nin/.pin file exists (index file)
        String indexExt = molecularType == MolecularType.NUCLEOTIDE ? ".nin" : ".pin";
        File indexFile = new File(databaseDir, databaseName + indexExt);
        if (!indexFile.exists()) {
            logger.warning("Missing index file: " + indexFile.getAbsolutePath());
            return true;
        }
        
        return false;
    }
    
    /**
     * Get the database path for BLAST commands
     */
    public String getDatabasePath() {
        return new File(databaseDir, databaseName).getAbsolutePath();
    }
    
    /**
     * Get database size in bytes
     */
    public long getDatabaseSize() {
        long totalSize = 0;
        String[] extensions = molecularType.getRequiredExtensions();
        
        for (String ext : extensions) {
            File dbFile = new File(databaseDir, databaseName + ext);
            if (dbFile.exists()) {
                totalSize += dbFile.length();
            }
        }
        
        return totalSize;
    }
    
    /**
     * Save database metadata to file
     */
    private void saveMetadata() throws IOException {
        File metadataFile = new File(databaseDir, METADATA_FILE_NAME);
        Properties props = new Properties();
        
        props.setProperty("database.name", databaseName);
        props.setProperty("sequence.type", sequenceType.name());
        props.setProperty("molecular.type", molecularType.name());
        props.setProperty("creation.time", String.valueOf(creationTime));
        props.setProperty("source.modification.time", String.valueOf(sourceFileModificationTime));
        props.setProperty("source.file.size", String.valueOf(sourceFileSize));
        props.setProperty("sequence.count", String.valueOf(sequenceCount));
        props.setProperty("source.file.path", sourceFilePath);
        
        // Save custom metadata
        for (Map.Entry<String, String> entry : customMetadata.entrySet()) {
            props.setProperty("custom." + entry.getKey(), entry.getValue());
        }
        
        try (OutputStream os = new FileOutputStream(metadataFile)) {
            props.store(os, "SimpleGenomeHub BLAST Database Metadata");
        }
    }
    
    /**
     * Count sequences in FASTA file
     */
    private int countSequencesInFasta(File fastaFile) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(fastaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(">")) {
                    count++;
                }
            }
        } catch (IOException e) {
            logger.warning("Unable to count sequences in FASTA file: " + e.getMessage());
        }
        return count;
    }
    
    /**
     * Delete database files and metadata
     */
    public boolean delete() {
        boolean success = true;
        
        // Delete database files
        String[] extensions = molecularType.getRequiredExtensions();
        for (String ext : extensions) {
            File dbFile = new File(databaseDir, databaseName + ext);
            if (dbFile.exists() && !dbFile.delete()) {
                logger.warning("Unable to delete database file: " + dbFile.getAbsolutePath());
                success = false;
            }
        }
        
        // Delete metadata file
        File metadataFile = new File(databaseDir, METADATA_FILE_NAME);
        if (metadataFile.exists() && !metadataFile.delete()) {
            logger.warning("Unable to delete metadata file: " + metadataFile.getAbsolutePath());
            success = false;
        }
        
        // Try to delete directory if empty
        if (databaseDir.exists() && databaseDir.list().length == 0) {
            databaseDir.delete();
        }
        
        return success;
    }
    
    /**
     * Add custom metadata
     */
    public void addMetadata(String key, String value) {
        customMetadata.put(key, value);
        try {
            saveMetadata();
        } catch (IOException e) {
            logger.warning("Unable to save custom metadata: " + e.getMessage());
        }
    }
    
    /**
     * Get custom metadata
     */
    public String getMetadata(String key) {
        return customMetadata.get(key);
    }
    
    // Getters
    public File getDatabaseDir() {
        return databaseDir;
    }
    
    public String getDatabaseName() {
        return databaseName;
    }
    
    public SequenceType getSequenceType() {
        return sequenceType;
    }
    
    public MolecularType getMolecularType() {
        return molecularType;
    }
    
    public long getCreationTime() {
        return creationTime;
    }
    
    public long getSourceFileModificationTime() {
        return sourceFileModificationTime;
    }
    
    public long getSourceFileSize() {
        return sourceFileSize;
    }
    
    public int getSequenceCount() {
        return sequenceCount;
    }
    
    public String getSourceFilePath() {
        return sourceFilePath;
    }
    
    public Map<String, String> getCustomMetadata() {
        return new HashMap<>(customMetadata);
    }
    
    @Override
    public String toString() {
        return String.format("BlastDatabase{name='%s', type=%s, sequences=%d, size=%d bytes}", 
                           databaseName, sequenceType, sequenceCount, getDatabaseSize());
    }
}
