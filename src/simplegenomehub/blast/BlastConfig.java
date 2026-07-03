/*
 * SimpleGenomeHub BLAST Configuration Management
 * Manages global BLAST settings and species-specific database paths
 */
package simplegenomehub.blast;

import simplegenomehub.config.ApplicationLayout;
import simplegenomehub.config.SimpleGenomeHubConfig;
import simplegenomehub.model.SpeciesInfo;
import java.io.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration management for BLAST functionality
 * Handles global settings and species-specific database path organization
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastConfig {
    
    private static final Logger logger = Logger.getLogger(BlastConfig.class.getName());
    private static final String CONFIG_FILE_NAME = "blast_config.properties";
    
    // Default configuration values
    private static final int DEFAULT_NUM_THREADS = 4;
    private static final double DEFAULT_EVALUE = 1e-5;
    private static final int DEFAULT_MAX_TARGET_SEQS = 500;
    private static final boolean DEFAULT_AUTO_CREATE_DATABASE = true;
    private static final boolean DEFAULT_CACHE_UNUSED_DATABASES = true;
    private static final long DEFAULT_MAX_CACHE_AGE_DAYS = 30;
    
    // Configuration properties
    private String globalBlastDbRoot;
    private String blastExecutablePath;
    private int defaultNumThreads;
    private double defaultEvalue;
    private int defaultMaxTargetSeqs;
    private boolean autoCreateDatabase;
    private boolean cacheUnusedDatabases;
    private long maxCacheAgeDays;
    
    private Properties configProperties;
    private File configFile;
    
    /**
     * Initialize BLAST configuration
     */
    public BlastConfig() throws IOException {
        this.configProperties = new Properties();
        this.configFile = new File(SimpleGenomeHubConfig.getInstance().getHomeDir(), CONFIG_FILE_NAME);
        
        loadConfiguration();
        setDefaultValues();
    }
    
    /**
     * Load configuration from file or create with defaults
     */
    private void loadConfiguration() throws IOException {
        if (configFile.exists()) {
            try (InputStream is = new FileInputStream(configFile)) {
                configProperties.load(is);
                logger.info("Loaded BLAST config file: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                logger.warning("Unable to load BLAST config file, using defaults: " + e.getMessage());
            }
        } else {
            // Create config directory if it doesn't exist
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
            logger.info("Creating new BLAST config file: " + configFile.getAbsolutePath());
        }
    }
    
    /**
     * Set default values from properties or use built-in defaults
     */
    private void setDefaultValues() {
        // Global database root directory
        this.globalBlastDbRoot = configProperties.getProperty("global.blast.db.root", 
            new File(SimpleGenomeHubConfig.getInstance().getHomeDir(), "blast_databases").getAbsolutePath());
        
        // BLAST executable path (auto-detect or use system PATH)
        this.blastExecutablePath = normalizePath(configProperties.getProperty("blast.executable.path", ""));
        
        if (!this.blastExecutablePath.isEmpty() && !isUsableBlastDir(new File(this.blastExecutablePath))) {
            logger.warning("Configured BLAST path is not usable and will be ignored: " + this.blastExecutablePath);
            this.blastExecutablePath = "";
        }
        
        // Auto-detect bundled or system BLAST path if not configured
        if (this.blastExecutablePath.isEmpty()) {
            this.blastExecutablePath = autoDetectBlastPath();
        }
        
        // Execution parameters
        this.defaultNumThreads = Integer.parseInt(
            configProperties.getProperty("default.num.threads", String.valueOf(DEFAULT_NUM_THREADS)));
        this.defaultEvalue = Double.parseDouble(
            configProperties.getProperty("default.evalue", String.valueOf(DEFAULT_EVALUE)));
        this.defaultMaxTargetSeqs = Integer.parseInt(
            configProperties.getProperty("default.max.target.seqs", String.valueOf(DEFAULT_MAX_TARGET_SEQS)));
        
        // Database management settings
        this.autoCreateDatabase = Boolean.parseBoolean(
            configProperties.getProperty("auto.create.database", String.valueOf(DEFAULT_AUTO_CREATE_DATABASE)));
        this.cacheUnusedDatabases = Boolean.parseBoolean(
            configProperties.getProperty("cache.unused.databases", String.valueOf(DEFAULT_CACHE_UNUSED_DATABASES)));
        this.maxCacheAgeDays = Long.parseLong(
            configProperties.getProperty("max.cache.age.days", String.valueOf(DEFAULT_MAX_CACHE_AGE_DAYS)));
    }
    
    /**
     * Save current configuration to file
     */
    public void saveConfiguration() throws IOException {
        configProperties.setProperty("global.blast.db.root", globalBlastDbRoot);
        configProperties.setProperty("blast.executable.path", blastExecutablePath);
        configProperties.setProperty("default.num.threads", String.valueOf(defaultNumThreads));
        configProperties.setProperty("default.evalue", String.valueOf(defaultEvalue));
        configProperties.setProperty("default.max.target.seqs", String.valueOf(defaultMaxTargetSeqs));
        configProperties.setProperty("auto.create.database", String.valueOf(autoCreateDatabase));
        configProperties.setProperty("cache.unused.databases", String.valueOf(cacheUnusedDatabases));
        configProperties.setProperty("max.cache.age.days", String.valueOf(maxCacheAgeDays));
        
        try (OutputStream os = new FileOutputStream(configFile)) {
            configProperties.store(os, "SimpleGenomeHub BLAST Configuration");
            logger.info("BLAST configuration saved to: " + configFile.getAbsolutePath());
        }
    }
    
    /**
     * Auto-detect BLAST executable path from common locations
     */
    private String autoDetectBlastPath() {
        String bundledBlastDir = null;
        File bundledDir = ApplicationLayout.findBundledBlastDir();
        if (bundledDir != null) {
            bundledBlastDir = bundledDir.getAbsolutePath();
        }
        
        String tbtoolsHome = System.getenv("TBTOOLS_HOME");
        String[] candidatePaths = {
            bundledBlastDir,
            tbtoolsHome != null && !tbtoolsHome.trim().isEmpty() ? tbtoolsHome + "\\bin" : null,
            "C:\\Program Files\\TBtools\\bin",
            "C:\\TBtools\\bin",
            "" // Empty string means use system PATH
        };
        
        for (String candidatePath : candidatePaths) {
            if (candidatePath == null) continue;
            
            if (candidatePath.isEmpty()) {
                // Test if blastp is available in system PATH
                if (isBlastAvailable("blastp")) {
                    logger.info("Found BLAST in system PATH");
                    return "";
                }
            } else {
                File blastDir = new File(candidatePath);
                if (isUsableBlastDir(blastDir)) {
                    logger.info("Found BLAST at: " + candidatePath);
                    return blastDir.getAbsolutePath();
                }
            }
        }
        
        logger.warning("Could not auto-detect BLAST path. Configure it manually, place BLAST+ under "
            + new File(ApplicationLayout.getAppHomeDirectory(), "bin\\tools").getAbsolutePath()
            + ", or add it to system PATH.");
        return "";
    }
    
    /**
     * Test if BLAST is available in system PATH
     */
    private boolean isBlastAvailable(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command, "-version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isUsableBlastDir(File blastDir) {
        if (blastDir == null || !blastDir.isDirectory()) {
            return false;
        }
        File blastp = ApplicationLayout.resolveExecutable(blastDir, "blastp");
        File makeblastdb = ApplicationLayout.resolveExecutable(blastDir, "makeblastdb");
        return blastp != null && makeblastdb != null;
    }

    private String normalizePath(String path) {
        return path == null ? "" : path.trim();
    }
    
    /**
     * Get species-specific BLAST database directory
     * Path: Species.version/BlastDB/
     */
    public File getSpeciesBlastDbDir(SpeciesInfo species) {
        return new File(species.getSpeciesDir(), "BlastDB");
    }
    
    /**
     * Get sequence type specific database directory
     * Path: Species.version/BlastDB/sequenceType/
     */
    public File getSequenceTypeDbDir(SpeciesInfo species, SequenceType sequenceType) {
        return new File(getSpeciesBlastDbDir(species), sequenceType.toString().toLowerCase());
    }
    
    /**
     * Get database file path for a specific sequence type
     * Path: Species.version/BlastDB/sequenceType/sequenceType
     */
    public File getDatabaseFile(SpeciesInfo species, SequenceType sequenceType) {
        File dbDir = getSequenceTypeDbDir(species, sequenceType);
        return new File(dbDir, sequenceType.toString().toLowerCase());
    }
    
    /**
     * Check if BLAST databases directory exists and is writable
     */
    public boolean validateDatabaseRoot() {
        File dbRoot = new File(globalBlastDbRoot);
        if (!dbRoot.exists()) {
            try {
                return dbRoot.mkdirs();
            } catch (SecurityException e) {
                logger.log(Level.SEVERE, "Unable to create BLAST database root directory: " + dbRoot.getAbsolutePath(), e);
                return false;
            }
        }
        return dbRoot.isDirectory() && dbRoot.canWrite();
    }
    
    /**
     * Get the maximum cache age in milliseconds
     */
    public long getMaxCacheAgeMillis() {
        return maxCacheAgeDays * 24 * 60 * 60 * 1000L;
    }
    
    // Getters and Setters
    public String getGlobalBlastDbRoot() {
        return globalBlastDbRoot;
    }
    
    public void setGlobalBlastDbRoot(String globalBlastDbRoot) {
        this.globalBlastDbRoot = globalBlastDbRoot;
    }
    
    public String getBlastExecutablePath() {
        return blastExecutablePath;
    }
    
    public void setBlastExecutablePath(String blastExecutablePath) {
        this.blastExecutablePath = normalizePath(blastExecutablePath);
    }

    public String resolveBlastCommand(String commandBaseName) {
        File blastDir = blastExecutablePath.isEmpty() ? null : new File(blastExecutablePath);
        File executable = ApplicationLayout.resolveExecutable(blastDir, commandBaseName);
        return executable != null ? executable.getAbsolutePath() : commandBaseName;
    }
    
    public int getDefaultNumThreads() {
        return defaultNumThreads;
    }
    
    public void setDefaultNumThreads(int defaultNumThreads) {
        this.defaultNumThreads = defaultNumThreads;
    }
    
    public double getDefaultEvalue() {
        return defaultEvalue;
    }
    
    public void setDefaultEvalue(double defaultEvalue) {
        this.defaultEvalue = defaultEvalue;
    }
    
    public int getDefaultMaxTargetSeqs() {
        return defaultMaxTargetSeqs;
    }
    
    public void setDefaultMaxTargetSeqs(int defaultMaxTargetSeqs) {
        this.defaultMaxTargetSeqs = defaultMaxTargetSeqs;
    }
    
    public boolean isAutoCreateDatabase() {
        return autoCreateDatabase;
    }
    
    public void setAutoCreateDatabase(boolean autoCreateDatabase) {
        this.autoCreateDatabase = autoCreateDatabase;
    }
    
    public boolean isCacheUnusedDatabases() {
        return cacheUnusedDatabases;
    }
    
    public void setCacheUnusedDatabases(boolean cacheUnusedDatabases) {
        this.cacheUnusedDatabases = cacheUnusedDatabases;
    }
    
    public long getMaxCacheAgeDays() {
        return maxCacheAgeDays;
    }
    
    public void setMaxCacheAgeDays(long maxCacheAgeDays) {
        this.maxCacheAgeDays = maxCacheAgeDays;
    }
    
    /**
     * Sequence types supported by BLAST
     */
    public enum SequenceType {
        GENOME("genome"),
        TRANSCRIPT("transcript"), 
        CDS("CDS"),
        PROTEIN("protein");
        
        private final String fileName;
        
        SequenceType(String fileName) {
            this.fileName = fileName;
        }
        
        public String getFileName() {
            return fileName;
        }
        
        /**
         * Get corresponding FASTA file for this sequence type
         */
        public File getSourceFile(SpeciesInfo species) {
            return new File(species.getSequenceDir(), fileName + ".fasta");
        }
        
        /**
         * Determine molecular type (nucleotide or protein)
         */
        public MolecularType getMolecularType() {
            return this == PROTEIN ? MolecularType.PROTEIN : MolecularType.NUCLEOTIDE;
        }
    }
    
    /**
     * Molecular types for BLAST databases
     */
    public enum MolecularType {
        NUCLEOTIDE("nucl", new String[]{".nhr", ".nin", ".nsq"}),
        PROTEIN("prot", new String[]{".phr", ".pin", ".psq"});
        
        private final String blastDbType;
        private final String[] requiredExtensions;
        
        MolecularType(String blastDbType, String[] requiredExtensions) {
            this.blastDbType = blastDbType;
            this.requiredExtensions = requiredExtensions;
        }
        
        public String getBlastDbType() {
            return blastDbType;
        }
        
        public String[] getRequiredExtensions() {
            return requiredExtensions;
        }
    }
}
