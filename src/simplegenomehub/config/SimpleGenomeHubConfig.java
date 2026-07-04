/*
 * SimpleGenomeHub Configuration Management
 * Based on TBtools configuration pattern
 */
package simplegenomehub.config;

import java.io.*;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/**
 * Configuration management for SimpleGenomeHub
 * Handles data directory settings and persistent configuration storage
 * 
 * @author SimpleGenomeHub
 */
public class SimpleGenomeHubConfig {
    
    private static final Logger logger = Logger.getLogger(SimpleGenomeHubConfig.class.getName());
    
    // Configuration keys
    public static final String DATA_ROOT_DIR = "data.root.directory";
    public static final String LAST_SPECIES_COUNT = "data.species.count";
    public static final String CONFIG_VERSION = "config.version";
    public static final String GO_OBO_GLOBAL_PATH = "go.obo.global.path";
    public static final String GO_OBO_LAST_SOURCE = "go.obo.last.source";
    public static final String GO_OBO_LAST_VERIFIED_EPOCH = "go.obo.last.verified.epoch";
    public static final String KEGG_BACKEND_MODE = "kegg.backend.mode";
    public static final String KEGG_BACKEND_TYPE = "kegg.backend.type";
    public static final String KEGG_CUSTOM_BACKEND_PATH = "kegg.custom.backend.path";
    public static final String EGGNOG_TAX_SCOPE = "eggnog.tax.scope";
    public static final String EGGNOG_CPU = "eggnog.cpu";
    public static final String EGGNOG_EVALUE = "eggnog.evalue";
    public static final String EGGNOG_OUTPUT_PREFIX = "eggnog.output.prefix";
    public static final String EGGNOG_KEGG_BACKEND_MODE = "eggnog.kegg.backend.mode";
    public static final String EGGNOG_KEGG_BACKEND_TYPE = "eggnog.kegg.backend.type";
    public static final String UI_FONT_SCALE_PERCENT = "ui.font.scale.percent";
    
    // Default values
    private static final String DEFAULT_CONFIG_VERSION = "1.0";
    private static final String CONFIG_FILE_NAME = "SimpleGenomeHub.config";
    
    // Instance variables
    private File homeDir;
    private File configFile;
    private File dataRootDir;
    private Properties configProperties;
    
    private static SimpleGenomeHubConfig instance;
    
    /**
     * Private constructor for singleton pattern
     */
    private SimpleGenomeHubConfig() {
        initializeConfig();
    }
    
    /**
     * Get singleton instance
     */
    public static SimpleGenomeHubConfig getInstance() {
        if (instance == null) {
            instance = new SimpleGenomeHubConfig();
        }
        return instance;
    }
    
    /**
     * Initialize configuration system
     */
    private void initializeConfig() {
        try {
            // Set up home directory (TBtools userHome pattern)
            String userHome = System.getProperty("user.home");
            homeDir = new File(userHome, ".SimpleGenomeHub");
            
            if (!homeDir.exists()) {
                homeDir.mkdirs();
            }
            
            configFile = new File(homeDir, CONFIG_FILE_NAME);
            configProperties = new Properties();
            
            loadConfig();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize configuration", e);
            JOptionPane.showMessageDialog(null, 
                "Failed to initialize configuration: " + e.getMessage(),
                "Configuration Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Load configuration from file
     */
    private void loadConfig() {
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                configProperties.load(fis);
                logger.info("Configuration loaded from: " + configFile.getAbsolutePath());
                
                // Validate data root directory
                String dataRootPath = configProperties.getProperty(DATA_ROOT_DIR);
                if (dataRootPath != null) {
                    dataRootDir = new File(dataRootPath);
                    if (!dataRootDir.exists() || !dataRootDir.isDirectory()) {
                        logger.warning("Data root directory not found: " + dataRootPath);
                        dataRootDir = null;
                    }
                }
                
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to load configuration", e);
                setDefaultConfig();
            }
        } else {
            setDefaultConfig();
        }
    }
    
    /**
     * Set default configuration values
     */
    private void setDefaultConfig() {
        configProperties.setProperty(CONFIG_VERSION, DEFAULT_CONFIG_VERSION);
        configProperties.setProperty(LAST_SPECIES_COUNT, "0");
        configProperties.setProperty(EGGNOG_TAX_SCOPE, "Viridiplantae");
        configProperties.setProperty(EGGNOG_CPU, "8");
        configProperties.setProperty(EGGNOG_EVALUE, "0.001");
        configProperties.setProperty(EGGNOG_KEGG_BACKEND_MODE, "PRESET");
        configProperties.setProperty(EGGNOG_KEGG_BACKEND_TYPE, "Plants");
        configProperties.setProperty(UI_FONT_SCALE_PERCENT, "100");
        saveConfig();
    }
    
    /**
     * Save configuration to file
     */
    public void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            configProperties.store(fos, "SimpleGenomeHub Configuration File");
            logger.info("Configuration saved to: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save configuration", e);
        }
    }
    
    /**
     * Get data root directory
     */
    public File getDataRootDir() {
        return dataRootDir;
    }
    
    /**
     * Set data root directory with validation
     */
    public boolean setDataRootDir(File newDataDir) {
        if (newDataDir == null) {
            return false;
        }
        
        // Validate directory path (no spaces, English only)
        String path = newDataDir.getAbsolutePath();
        if (path.contains(" ")) {
            JOptionPane.showMessageDialog(null, 
                "Directory path cannot contain spaces: " + path,
                "Invalid Path", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        // Check for non-ASCII characters
        if (!path.matches("^[\\x00-\\x7F]*$")) {
            JOptionPane.showMessageDialog(null, 
                "Directory path must contain only English characters: " + path,
                "Invalid Path", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        // Create directory if it doesn't exist
        if (!newDataDir.exists()) {
            if (!newDataDir.mkdirs()) {
                JOptionPane.showMessageDialog(null, 
                    "Failed to create directory: " + path,
                    "Directory Creation Failed", JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        
        // Verify it's a directory and writable
        if (!newDataDir.isDirectory() || !newDataDir.canWrite()) {
            JOptionPane.showMessageDialog(null, 
                "Directory is not accessible or writable: " + path,
                "Directory Access Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        this.dataRootDir = newDataDir;
        configProperties.setProperty(DATA_ROOT_DIR, path);
        saveConfig();
        
        logger.info("Data root directory set to: " + path);
        return true;
    }
    
    /**
     * Check if data root directory is configured
     */
    public boolean isDataRootConfigured() {
        return dataRootDir != null && dataRootDir.exists() && dataRootDir.isDirectory();
    }
    
    /**
     * Get configuration property
     */
    public String getProperty(String key) {
        return configProperties.getProperty(key);
    }
    
    /**
     * Set configuration property
     */
    public void setProperty(String key, String value) {
        String currentValue = configProperties.getProperty(key);
        if (value == null) {
            if (currentValue == null) {
                return;
            }
            configProperties.remove(key);
            saveConfig();
            return;
        }

        if (value.equals(currentValue)) {
            return;
        }

        configProperties.setProperty(key, value);
        saveConfig();
    }
    
    /**
     * Get configuration property as integer
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = configProperties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warning("Invalid integer property: " + key + " = " + value);
            }
        }
        return defaultValue;
    }
    
    /**
     * Set configuration property as integer
     */
    public void setIntProperty(String key, int value) {
        setProperty(key, String.valueOf(value));
    }

    public int getUiFontScalePercent() {
        return getIntProperty(UI_FONT_SCALE_PERCENT, 100);
    }

    public void setUiFontScalePercent(int scalePercent) {
        setIntProperty(UI_FONT_SCALE_PERCENT, scalePercent);
    }
    
    /**
     * Get home directory
     */
    public File getHomeDir() {
        return homeDir;
    }
    
    /**
     * Get config file
     */
    public File getConfigFile() {
        return configFile;
    }
}
