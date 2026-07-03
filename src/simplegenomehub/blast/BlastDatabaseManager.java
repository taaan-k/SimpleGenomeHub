/*
 * SimpleGenomeHub BLAST Database Manager
 * Manages on-demand creation and caching of BLAST databases
 */
package simplegenomehub.blast;

import simplegenomehub.blast.BlastConfig.SequenceType;
import simplegenomehub.blast.model.BlastDatabase;
import simplegenomehub.model.SpeciesInfo;
import biocjava.bioDoer.BLAST.BlastZone.BlastZone.MakeBlastDBonTheFly;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages BLAST databases with on-demand creation and intelligent caching
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastDatabaseManager {
    
    private static final Logger logger = Logger.getLogger(BlastDatabaseManager.class.getName());
    
    private final BlastConfig config;
    private final ConcurrentMap<String, BlastDatabase> databaseCache;
    private final ConcurrentMap<String, CompletableFuture<BlastDatabase>> creationTasks;
    private final ExecutorService executorService;
    private final ScheduledExecutorService cleanupExecutor;
    
    /**
     * Initialize the database manager
     */
    public BlastDatabaseManager(BlastConfig config) {
        this.config = config;
        this.databaseCache = new ConcurrentHashMap<>();
        this.creationTasks = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(4);
        this.cleanupExecutor = Executors.newScheduledThreadPool(1);
        
        // Schedule periodic cleanup
        cleanupExecutor.scheduleAtFixedRate(this::performCleanup, 1, 1, TimeUnit.HOURS);
        
        logger.info("BLAST database manager initialized");
    }
    
    /**
     * Ensure database exists for the given species and sequence type
     * This is the main entry point for on-demand database creation
     */
    public BlastDatabase ensureDatabaseExists(SpeciesInfo species, SequenceType seqType) 
            throws Exception {
        
        String dbKey = getDatabaseKey(species, seqType);
        
        // 1. Check memory cache first
        BlastDatabase cachedDb = databaseCache.get(dbKey);
        if (cachedDb != null && isValidAndUpToDate(cachedDb, species, seqType)) {
            logger.fine("Returning database from cache: " + dbKey);
            return cachedDb;
        }
        
        // 2. Check if creation is already in progress
        CompletableFuture<BlastDatabase> existingTask = creationTasks.get(dbKey);
        if (existingTask != null) {
            logger.info("Waiting for database creation to complete: " + dbKey);
            return existingTask.get();
        }
        
        // 3. Check disk database
        File dbDir = config.getSequenceTypeDbDir(species, seqType);
        File sourceFile = seqType.getSourceFile(species);
        
        // Try to find the actual source file if the default doesn't exist
        if (!sourceFile.exists()) {
            sourceFile = findActualSourceFile(species, seqType);
            if (sourceFile == null || !sourceFile.exists()) {
                throw new FileNotFoundException("Source sequence file not found for " + 
                    species.getSpeciesName() + " " + seqType + ". Expected: " + seqType.getSourceFile(species).getAbsolutePath());
            }
        }
        
        BlastDatabase diskDb = loadDatabaseFromDisk(dbDir, seqType);
        if (diskDb != null && diskDb.isUpToDate(sourceFile)) {
            databaseCache.put(dbKey, diskDb);
            logger.info("Loaded database from disk: " + dbKey);
            return diskDb;
        }
        
        // 4. Create new database
        logger.info("Starting creation of new database: " + dbKey);
        CompletableFuture<BlastDatabase> createTask = createDatabaseAsync(species, seqType, sourceFile, dbDir);
        creationTasks.put(dbKey, createTask);
        
        try {
            BlastDatabase newDb = createTask.get();
            databaseCache.put(dbKey, newDb);
            return newDb;
        } finally {
            creationTasks.remove(dbKey);
        }
    }
    
    /**
     * Create database asynchronously
     */
    private CompletableFuture<BlastDatabase> createDatabaseAsync(SpeciesInfo species, 
                                                               SequenceType seqType, 
                                                               File sourceFile, 
                                                               File dbDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return createDatabase(species, seqType, sourceFile, dbDir);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Database creation failed: " + getDatabaseKey(species, seqType), e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    /**
     * Actually create the database using TBtools
     */
    private BlastDatabase createDatabase(SpeciesInfo species, SequenceType seqType, 
                                       File sourceFile, File dbDir) throws Exception {
        
        logger.info("Creating " + seqType + " database for " + species.getSpeciesName() + "...");
        
        // Ensure directory exists
        if (!dbDir.exists() && !dbDir.mkdirs()) {
            throw new IOException("Unable to create database directory: " + dbDir.getAbsolutePath());
        }
        
        // Prepare database file
        File dbFile = new File(dbDir, seqType.toString().toLowerCase());
        
        // Use TBtools MakeBlastDBonTheFly
        MakeBlastDBonTheFly makeDb = new MakeBlastDBonTheFly();
        makeDb.setInFile(sourceFile);
        makeDb.setOutFile(dbFile);
        
        // Execute database creation
        long startTime = System.currentTimeMillis();
        makeDb.process();
        long duration = System.currentTimeMillis() - startTime;
        
        logger.info("Database creation completed: " + getDatabaseKey(species, seqType) + 
                   " (duration: " + duration + "ms)");
        
        // Create and return database object
        BlastDatabase database = new BlastDatabase(dbDir, seqType, sourceFile);
        database.addMetadata("creation_duration_ms", String.valueOf(duration));
        database.addMetadata("species_name", species.getSpeciesName());
        database.addMetadata("species_version", species.getVersion());
        
        return database;
    }
    
    /**
     * Load database from disk if it exists
     */
    private BlastDatabase loadDatabaseFromDisk(File dbDir, SequenceType seqType) {
        try {
            if (!dbDir.exists()) {
                return null;
            }
            
            BlastDatabase db = BlastDatabase.loadFromDisk(dbDir, seqType);
            if (db != null && db.isValid()) {
                logger.fine("Loaded database from disk: " + dbDir.getAbsolutePath());
                return db;
            }
        } catch (Exception e) {
            logger.warning("Unable to load database from disk: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Check if database is valid and up to date
     */
    private boolean isValidAndUpToDate(BlastDatabase db, SpeciesInfo species, SequenceType seqType) {
        if (!db.isValid()) {
            return false;
        }
        
        File sourceFile = seqType.getSourceFile(species);
        if (!sourceFile.exists()) {
            sourceFile = findActualSourceFile(species, seqType);
            if (sourceFile == null) return false;
        }
        return db.isUpToDate(sourceFile);
    }
    
    /**
     * Generate unique key for database
     */
    private String getDatabaseKey(SpeciesInfo species, SequenceType seqType) {
        return species.getSpeciesDirectoryName() + "_" + seqType.name();
    }
    
    /**
     * Find actual source file by searching for matching files in species directory
     */
    private File findActualSourceFile(SpeciesInfo species, BlastConfig.SequenceType seqType) {
        if (species.getSequenceDir() == null || !species.getSequenceDir().exists()) {
            return null;
        }
        
        String targetName = seqType.toString().toLowerCase();
        
        // Search for files matching the sequence type
        File[] candidateFiles = species.getSequenceDir().listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            // Support common FASTA file extensions: .fasta, .fa, .fna, .fas
            boolean isFastaFile = lowerName.endsWith(".fasta") ||
                                  lowerName.endsWith(".fa") ||
                                  lowerName.endsWith(".fna") ||
                                  lowerName.endsWith(".fas");
            return lowerName.contains(targetName) && isFastaFile;
        });
        
        if (candidateFiles != null && candidateFiles.length > 0) {
            // Return the first matching file
            logger.info("Found alternative source file: " + candidateFiles[0].getAbsolutePath() + 
                       " for sequence type: " + seqType);
            return candidateFiles[0];
        }
        
        // Special case: for protein sequences, also look for files containing "peptide" or "aa"
        if (seqType == BlastConfig.SequenceType.PROTEIN) {
            candidateFiles = species.getSequenceDir().listFiles((dir, name) -> {
                String lowerName = name.toLowerCase();
                // Support common FASTA file extensions: .fasta, .fa, .fna, .fas
                boolean isFastaFile = lowerName.endsWith(".fasta") ||
                                      lowerName.endsWith(".fa") ||
                                      lowerName.endsWith(".fna") ||
                                      lowerName.endsWith(".fas");
                return (lowerName.contains("peptide") || lowerName.contains("aa") ||
                        lowerName.contains("pep")) && isFastaFile;
            });
            
            if (candidateFiles != null && candidateFiles.length > 0) {
                logger.info("Found protein alternative: " + candidateFiles[0].getAbsolutePath());
                return candidateFiles[0];
            }
        }
        
        return null;
    }
    
    /**
     * Check if database exists for given species and sequence type
     */
    public boolean databaseExists(SpeciesInfo species, SequenceType seqType) {
        String dbKey = getDatabaseKey(species, seqType);
        
        // Check cache
        BlastDatabase cachedDb = databaseCache.get(dbKey);
        if (cachedDb != null && isValidAndUpToDate(cachedDb, species, seqType)) {
            return true;
        }
        
        // Check disk
        File dbDir = config.getSequenceTypeDbDir(species, seqType);
        BlastDatabase diskDb = loadDatabaseFromDisk(dbDir, seqType);
        if (diskDb != null) {
            File sourceFile = seqType.getSourceFile(species);
            return diskDb.isUpToDate(sourceFile);
        }
        
        return false;
    }
    
    /**
     * Get database status for given species and sequence type
     */
    public DatabaseStatus getDatabaseStatus(SpeciesInfo species, SequenceType seqType) {
        File sourceFile = seqType.getSourceFile(species);
        
        if (!sourceFile.exists()) {
            return DatabaseStatus.SOURCE_MISSING;
        }
        
        String dbKey = getDatabaseKey(species, seqType);
        
        // Check if creation is in progress
        if (creationTasks.containsKey(dbKey)) {
            return DatabaseStatus.CREATING;
        }
        
        // Check cache
        BlastDatabase cachedDb = databaseCache.get(dbKey);
        if (cachedDb != null && isValidAndUpToDate(cachedDb, species, seqType)) {
            return DatabaseStatus.UP_TO_DATE;
        }
        
        // Check disk
        File dbDir = config.getSequenceTypeDbDir(species, seqType);
        BlastDatabase diskDb = loadDatabaseFromDisk(dbDir, seqType);
        if (diskDb != null) {
            if (diskDb.isUpToDate(sourceFile)) {
                return DatabaseStatus.UP_TO_DATE;
            } else {
                return DatabaseStatus.OUTDATED;
            }
        }
        
        return DatabaseStatus.NOT_CREATED;
    }
    
    /**
     * Delete database for given species and sequence type
     */
    public boolean deleteDatabase(SpeciesInfo species, SequenceType seqType) {
        String dbKey = getDatabaseKey(species, seqType);
        
        // Remove from cache
        BlastDatabase db = databaseCache.remove(dbKey);
        
        // Delete from disk
        File dbDir = config.getSequenceTypeDbDir(species, seqType);
        if (dbDir.exists()) {
            try {
                if (db == null) {
                    db = loadDatabaseFromDisk(dbDir, seqType);
                }
                
                if (db != null) {
                    return db.delete();
                } else {
                    // Manual cleanup if metadata is corrupted
                    return deleteDirectoryContents(dbDir);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to delete database: " + dbKey, e);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Delete directory contents
     */
    private boolean deleteDirectoryContents(File dir) {
        boolean success = true;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.delete()) {
                    logger.warning("Unable to delete file: " + file.getAbsolutePath());
                    success = false;
                }
            }
        }
        
        if (success && dir.list().length == 0) {
            dir.delete();
        }
        
        return success;
    }
    
    /**
     * Perform periodic cleanup of old databases and cache
     */
    private void performCleanup() {
        logger.fine("Running database cleanup...");
        
        long currentTime = System.currentTimeMillis();
        long maxAge = config.getMaxCacheAgeMillis();
        
        // Clean memory cache
        databaseCache.entrySet().removeIf(entry -> {
            BlastDatabase db = entry.getValue();
            return (currentTime - db.getCreationTime()) > maxAge;
        });
        
        // TODO: Implement disk space-based cleanup if needed
        
        logger.fine("Database cleanup completed");
    }
    
    /**
     * Get cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        CacheStatistics stats = new CacheStatistics();
        stats.memoryCacheSize = databaseCache.size();
        stats.activeTasks = creationTasks.size();
        
        long totalDiskSize = 0;
        int validDatabases = 0;
        
        for (BlastDatabase db : databaseCache.values()) {
            if (db.isValid()) {
                validDatabases++;
                totalDiskSize += db.getDatabaseSize();
            }
        }
        
        stats.validDatabases = validDatabases;
        stats.totalDiskSizeBytes = totalDiskSize;
        
        return stats;
    }
    
    /**
     * Shutdown the database manager
     */
    public void shutdown() {
        logger.info("Shutting down BLAST database manager...");
        
        executorService.shutdown();
        cleanupExecutor.shutdown();
        
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!cleanupExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("BLAST database manager closed");
    }
    
    /**
     * Database status enumeration
     */
    public enum DatabaseStatus {
        UP_TO_DATE("Up to date"),
        OUTDATED("Outdated"),
        NOT_CREATED("Not created"),
        CREATING("Creating"),
        SOURCE_MISSING("Source missing"),
        ERROR("Error");
        
        private final String description;
        
        DatabaseStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStatistics {
        public int memoryCacheSize;
        public int activeTasks;
        public int validDatabases;
        public long totalDiskSizeBytes;
        
        @Override
        public String toString() {
            return String.format("Cache statistics: memory=%d, active tasks=%d, valid databases=%d, disk size=%d bytes",
                               memoryCacheSize, activeTasks, validDatabases, totalDiskSizeBytes);
        }
    }
}
