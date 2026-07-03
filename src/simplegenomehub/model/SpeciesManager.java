/*
 * Species Data Management
 */
package simplegenomehub.model;

import simplegenomehub.config.SimpleGenomeHubConfig;
import simplegenomehub.util.performance.LazySpeciesLoader;
import simplegenomehub.util.performance.PerformanceMonitor;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/**
 * Central manager for species data in SimpleGenomeHub
 * Handles loading, saving, and managing species collections
 * 
 * @author SimpleGenomeHub
 */
public class SpeciesManager {
    
    private static final Logger logger = Logger.getLogger(SpeciesManager.class.getName());
    
    private SimpleGenomeHubConfig config;
    private Map<String, SpeciesInfo> speciesMap;
    private List<SpeciesManagerListener> listeners;
    
    /**
     * Interface for species manager event listeners
     */
    public interface SpeciesManagerListener {
        void onSpeciesAdded(SpeciesInfo species);
        void onSpeciesRemoved(SpeciesInfo species);
        void onSpeciesUpdated(SpeciesInfo species);
        void onDataDirectoryChanged(File newDir);
    }
    
    /**
     * Constructor
     */
    public SpeciesManager() {
        this.config = SimpleGenomeHubConfig.getInstance();
        this.speciesMap = new HashMap<>();
        this.listeners = new ArrayList<>();
        
        loadSpeciesData();
    }
    
    /**
     * Add listener for species manager events
     */
    public void addListener(SpeciesManagerListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove listener
     */
    public void removeListener(SpeciesManagerListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Load species data from configured data directory with performance optimization
     */
    public void loadSpeciesData() {
        PerformanceMonitor.startOperation("loadSpeciesData");
        speciesMap.clear();
        
        File dataDir = config.getDataRootDir();
        if (dataDir == null || !dataDir.exists()) {
            logger.info("No data directory configured or directory not found");
            PerformanceMonitor.endOperation("loadSpeciesData");
            return;
        }
        
        File[] speciesDirs = dataDir.listFiles(File::isDirectory);
        if (speciesDirs == null) {
            logger.warning("Cannot read data directory: " + dataDir.getAbsolutePath());
            PerformanceMonitor.endOperation("loadSpeciesData");
            return;
        }
        
        logger.info("Loading " + speciesDirs.length + " species directories...");
        int loadedCount = 0;
        
        // Use parallel stream for faster loading with many directories
        if (speciesDirs.length > 10) {
            Arrays.stream(speciesDirs)
                .parallel()
                .forEach(speciesDir -> {
                    SpeciesInfo species = LazySpeciesLoader.loadSpeciesLazy(speciesDir);
                    if (species != null) {
                        String key = getSpeciesKey(species.getSpeciesName(), species.getVersion());
                        synchronized(speciesMap) {
                            speciesMap.put(key, species);
                        }
                    }
                });
            loadedCount = speciesMap.size();
        } else {
            // Sequential loading for smaller datasets
            for (File speciesDir : speciesDirs) {
                SpeciesInfo species = LazySpeciesLoader.loadSpeciesLazy(speciesDir);
                if (species != null) {
                    String key = getSpeciesKey(species.getSpeciesName(), species.getVersion());
                    speciesMap.put(key, species);
                    loadedCount++;
                }
            }
        }
        
        // Start background preloading of genome data
        if (loadedCount > 0) {
            List<SpeciesInfo> allSpecies = new ArrayList<>(speciesMap.values());
            LazySpeciesLoader.preloadGenomeData(allSpecies);
        }
        
        long loadTime = PerformanceMonitor.endOperation("loadSpeciesData");
        logger.info(String.format("Loaded %d species in %d ms (%s)", 
            loadedCount, loadTime, LazySpeciesLoader.getCacheStats()));
        
        config.setIntProperty(SimpleGenomeHubConfig.LAST_SPECIES_COUNT, loadedCount);
        PerformanceMonitor.logStatus();
    }
    
    /**
     * Load species information from directory
     */
    private SpeciesInfo loadSpeciesFromDirectory(File speciesDir) {
        String dirName = speciesDir.getName();
        
        // Parse directory name (Species.version format)
        String[] parts = dirName.split("\\.", 2);
        if (parts.length != 2) {
            logger.warning("Invalid species directory name format: " + dirName);
            return null;
        }
        
        String speciesName = parts[0];
        String version = parts[1];
        
        // Create species info
        SpeciesInfo species = new SpeciesInfo(speciesName, version);
        species.initializeFileStructure(speciesDir.getParentFile());
        
        // Load genome data if stats file exists
        if (species.getStatsFile().exists()) {
            GenomeData genomeData = GenomeData.loadFromFile(species.getStatsFile());
            species.setGenomeData(genomeData);
        }
        
        // Set import time from directory creation time (approximation)
        try {
            long lastModified = speciesDir.lastModified();
            species.setImportTime(LocalDateTime.ofEpochSecond(
                lastModified / 1000, 0, java.time.ZoneOffset.systemDefault().getRules()
                .getOffset(java.time.Instant.ofEpochMilli(lastModified))));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to determine import time for: " + dirName, e);
        }
        
        return species;
    }
    
    /**
     * Add new species to the system
     */
    public boolean addSpecies(SpeciesInfo species) {
        if (species == null) {
            return false;
        }
        
        String key = getSpeciesKey(species.getSpeciesName(), species.getVersion());
        
        // Check if species already exists
        if (speciesMap.containsKey(key)) {
            JOptionPane.showMessageDialog(null, 
                "Species already exists: " + species.getSpeciesDirectoryName(),
                "Species Exists", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        
        // Check if data directory is configured
        if (!config.isDataRootConfigured()) {
            JOptionPane.showMessageDialog(null, 
                "Data root directory not configured. Please set data directory first.",
                "Configuration Required", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        // Initialize file structure
        species.initializeFileStructure(config.getDataRootDir());
        
        // Create directory structure
        if (!species.createDirectoryStructure()) {
            JOptionPane.showMessageDialog(null, 
                "Failed to create directory structure for: " + species.getSpeciesDirectoryName(),
                "Directory Creation Failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        // Add to collection
        speciesMap.put(key, species);
        
        // Notify listeners
        for (SpeciesManagerListener listener : listeners) {
            listener.onSpeciesAdded(species);
        }
        
        logger.info("Added species: " + species.getSpeciesDirectoryName());
        config.setIntProperty(SimpleGenomeHubConfig.LAST_SPECIES_COUNT, speciesMap.size());
        
        return true;
    }
    
    /**
     * Remove species from the system
     */
    public boolean removeSpecies(String speciesName, String version) {
        String key = getSpeciesKey(speciesName, version);
        SpeciesInfo species = speciesMap.get(key);
        
        if (species == null) {
            return false;
        }
        
        // Confirm deletion
        int result = JOptionPane.showConfirmDialog(null, 
            "Are you sure you want to delete species: " + species.getSpeciesDirectoryName() + 
            "\nThis will permanently delete all associated files.",
            "Confirm Deletion", 
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        
        if (result != JOptionPane.YES_OPTION) {
            return false;
        }

        return removeSpeciesWithoutConfirmation(speciesName, version);
    }

    /**
     * Remove species from the system without showing a confirmation dialog.
     * Caller is responsible for any UI confirmation.
     */
    public boolean removeSpeciesWithoutConfirmation(String speciesName, String version) {
        String key = getSpeciesKey(speciesName, version);
        SpeciesInfo species = speciesMap.get(key);

        if (species == null) {
            return false;
        }

        // Delete directory structure
        boolean deleted = deleteDirectory(species.getSpeciesDir());
        if (!deleted) {
            JOptionPane.showMessageDialog(null,
                "Failed to delete some files for: " + species.getSpeciesDirectoryName(),
                "Deletion Warning", JOptionPane.WARNING_MESSAGE);
        }

        // Remove from collection
        speciesMap.remove(key);

        // Notify listeners
        for (SpeciesManagerListener listener : listeners) {
            listener.onSpeciesRemoved(species);
        }

        logger.info("Removed species: " + species.getSpeciesDirectoryName());
        config.setIntProperty(SimpleGenomeHubConfig.LAST_SPECIES_COUNT, speciesMap.size());

        return true;
    }
    
    /**
     * Update species information
     */
    public boolean updateSpecies(SpeciesInfo species) {
        if (species == null) {
            return false;
        }
        
        String key = getSpeciesKey(species.getSpeciesName(), species.getVersion());
        if (!speciesMap.containsKey(key)) {
            logger.warning("Cannot update unknown species: " + species.getSpeciesDirectoryName());
            return false;
        }
        
        if (!persistSpeciesArtifacts(species)) {
            return false;
        }
        
        // Update in collection
        speciesMap.put(key, species);
        
        // Notify listeners
        for (SpeciesManagerListener listener : listeners) {
            listener.onSpeciesUpdated(species);
        }
        
        logger.info("Updated species: " + species.getSpeciesDirectoryName());
        return true;
    }

    /**
     * Apply edited species information, including directory renaming when the
     * species name or version changes.
     */
    public boolean applySpeciesEdits(SpeciesInfo species, String newName, String newVersion, String newNotes) {
        if (species == null) {
            return false;
        }

        String normalizedNotes = (newNotes == null || newNotes.trim().isEmpty()) ? null : newNotes.trim();
        String oldName = species.getSpeciesName();
        String oldVersion = species.getVersion();
        String oldNotes = species.getNotes();

        boolean identityChanged = !oldName.equals(newName) || !oldVersion.equals(newVersion);
        if (!identityChanged) {
            species.setNotes(normalizedNotes);
            return updateSpecies(species);
        }

        if (!config.isDataRootConfigured()) {
            JOptionPane.showMessageDialog(null,
                "Data root directory not configured. Please set data directory first.",
                "Configuration Required", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        String oldKey = getSpeciesKey(oldName, oldVersion);
        if (!speciesMap.containsKey(oldKey)) {
            JOptionPane.showMessageDialog(null,
                "Species entry no longer exists: " + oldName + "." + oldVersion,
                "Species Not Found", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        String newKey = getSpeciesKey(newName, newVersion);
        if (!oldKey.equals(newKey) && speciesMap.containsKey(newKey)) {
            JOptionPane.showMessageDialog(null,
                "Species already exists: " + newName + "." + newVersion,
                "Species Exists", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        File dataRoot = config.getDataRootDir();
        if (dataRoot == null || !dataRoot.exists()) {
            JOptionPane.showMessageDialog(null,
                "Data root directory is unavailable.",
                "Directory Not Available", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        File sourceDir = species.getSpeciesDir() != null
            ? species.getSpeciesDir()
            : new File(dataRoot, oldName + "." + oldVersion);
        File targetDir = new File(dataRoot, newName + "." + newVersion);
        boolean sameDirectoryTarget = sourceDir.getAbsolutePath().equalsIgnoreCase(targetDir.getAbsolutePath());

        if (!sourceDir.exists()) {
            JOptionPane.showMessageDialog(null,
                "Species directory not found: " + sourceDir.getAbsolutePath(),
                "Directory Not Found", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        if (!sameDirectoryTarget && targetDir.exists()) {
            JOptionPane.showMessageDialog(null,
                "Target species directory already exists:\n" + targetDir.getAbsolutePath(),
                "Directory Exists", JOptionPane.WARNING_MESSAGE);
            return false;
        }

        List<MoveOperation> moveOperations = new ArrayList<>();
        String oldPrefix = oldName + "." + oldVersion;
        String newPrefix = newName + "." + newVersion;

        try {
            renamePrefixedEntries(new File(sourceDir, "Sequence"), oldPrefix, newPrefix, moveOperations);
            renamePrefixedEntries(new File(sourceDir, "Annotation"), oldPrefix, newPrefix, moveOperations);
            moveWithRecord(sourceDir.toPath(), targetDir.toPath(), moveOperations);

            species.setSpeciesName(newName);
            species.setVersion(newVersion);
            species.setNotes(normalizedNotes);
            species.initializeFileStructure(dataRoot);

            if (!persistSpeciesArtifacts(species)) {
                throw new IOException("Failed to save species metadata after renaming.");
            }

            speciesMap.remove(oldKey);
            speciesMap.put(newKey, species);
            LazySpeciesLoader.clearCache();

            for (SpeciesManagerListener listener : listeners) {
                listener.onSpeciesUpdated(species);
            }

            logger.info("Renamed species: " + oldPrefix + " -> " + species.getSpeciesDirectoryName());
            return true;

        } catch (IOException e) {
            rollbackMoves(moveOperations);
            species.setSpeciesName(oldName);
            species.setVersion(oldVersion);
            species.setNotes(oldNotes);
            species.initializeFileStructure(dataRoot);

            logger.log(Level.WARNING, "Failed to rename species from " + oldName + "." + oldVersion
                + " to " + newName + "." + newVersion, e);
            JOptionPane.showMessageDialog(null,
                "Failed to save species changes:\n" + e.getMessage(),
                "Save Failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
    
    /**
     * Get species by name and version
     */
    public SpeciesInfo getSpecies(String speciesName, String version) {
        String key = getSpeciesKey(speciesName, version);
        return speciesMap.get(key);
    }
    
    /**
     * Get all species as sorted list
     */
    public List<SpeciesInfo> getAllSpecies() {
        List<SpeciesInfo> species = new ArrayList<>(speciesMap.values());
        species.sort((s1, s2) -> {
            int nameComparison = s1.getSpeciesName().compareToIgnoreCase(s2.getSpeciesName());
            if (nameComparison != 0) {
                return nameComparison;
            }
            return s1.getVersion().compareToIgnoreCase(s2.getVersion());
        });
        return species;
    }
    
    /**
     * Check if species exists
     */
    public boolean hasSpecies(String speciesName, String version) {
        String key = getSpeciesKey(speciesName, version);
        return speciesMap.containsKey(key);
    }
    
    /**
     * Get species count
     */
    public int getSpeciesCount() {
        return speciesMap.size();
    }
    
    /**
     * Set data root directory and reload species
     */
    public boolean setDataRootDirectory(File newDir) {
        if (config.setDataRootDir(newDir)) {
            loadSpeciesData();
            
            // Notify listeners
            for (SpeciesManagerListener listener : listeners) {
                listener.onDataDirectoryChanged(newDir);
            }
            
            return true;
        }
        return false;
    }
    
    /**
     * Get current data root directory
     */
    public File getDataRootDirectory() {
        return config.getDataRootDir();
    }
    
    /**
     * Generate unique species key
     */
    private String getSpeciesKey(String speciesName, String version) {
        return speciesName.toLowerCase() + "." + version.toLowerCase();
    }

    private boolean persistSpeciesArtifacts(SpeciesInfo species) {
        if (species.getSpeciesDir() == null) {
            logger.warning("Cannot persist species without an initialized directory");
            return false;
        }

        if (!species.createDirectoryStructure()) {
            logger.warning("Failed to ensure directory structure for: " + species.getSpeciesDirectoryName());
            return false;
        }

        if (species.getGenomeData() != null && species.getStatsFile() != null) {
            species.getGenomeData().saveToFile(species.getStatsFile());
        }

        SpeciesMetadata metadata = SpeciesMetadata.fromSpeciesInfo(species);
        if (!metadata.saveToFile(species.getSpeciesDir())) {
            logger.warning("Failed to save metadata for: " + species.getSpeciesDirectoryName());
            return false;
        }

        return true;
    }

    private void renamePrefixedEntries(File dir, String oldPrefix, String newPrefix,
                                       List<MoveOperation> moveOperations) throws IOException {
        if (dir == null || !dir.isDirectory() || oldPrefix.equals(newPrefix)) {
            return;
        }

        File[] entries = dir.listFiles(file -> file.getName().startsWith(oldPrefix + "."));
        if (entries == null || entries.length == 0) {
            return;
        }

        Arrays.sort(entries, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File entry : entries) {
            String oldName = entry.getName();
            String newName = newPrefix + oldName.substring(oldPrefix.length());
            moveWithRecord(entry.toPath(), new File(dir, newName).toPath(), moveOperations);
        }
    }

    private void moveWithRecord(Path source, Path target, List<MoveOperation> moveOperations) throws IOException {
        movePath(source, target);
        moveOperations.add(new MoveOperation(source, target));
    }

    private void rollbackMoves(List<MoveOperation> moveOperations) {
        ListIterator<MoveOperation> iterator = moveOperations.listIterator(moveOperations.size());
        while (iterator.hasPrevious()) {
            MoveOperation operation = iterator.previous();
            try {
                if (Files.exists(operation.target)) {
                    movePath(operation.target, operation.source);
                }
            } catch (IOException rollbackError) {
                logger.log(Level.WARNING, "Failed to rollback move: " + operation.target + " -> "
                    + operation.source, rollbackError);
            }
        }
    }

    private void movePath(Path source, Path target) throws IOException {
        if (source.equals(target)) {
            return;
        }

        Path sourceAbsolute = source.toAbsolutePath();
        Path targetAbsolute = target.toAbsolutePath();

        if (sourceAbsolute.toString().equalsIgnoreCase(targetAbsolute.toString())) {
            Path tempTarget = targetAbsolute.resolveSibling(targetAbsolute.getFileName() + ".rename_tmp");
            int suffix = 0;
            while (Files.exists(tempTarget)) {
                suffix++;
                tempTarget = targetAbsolute.resolveSibling(targetAbsolute.getFileName() + ".rename_tmp_" + suffix);
            }

            Files.move(sourceAbsolute, tempTarget);
            try {
                Files.move(tempTarget, targetAbsolute);
            } catch (IOException e) {
                try {
                    Files.move(tempTarget, sourceAbsolute);
                } catch (IOException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
            return;
        }

        Files.move(sourceAbsolute, targetAbsolute);
    }
    
    /**
     * Get performance report
     */
    public String getPerformanceReport() {
        return PerformanceMonitor.getPerformanceReport();
    }
    
    /**
     * Clear performance caches to free memory
     */
    public void clearPerformanceCache() {
        LazySpeciesLoader.clearCache();
        PerformanceMonitor.clear();
        
        // Force garbage collection
        long freedMemory = PerformanceMonitor.forceGarbageCollection();
        logger.info("Performance cache cleared, freed " + (freedMemory / 1024 / 1024) + "MB");
    }
    
    /**
     * Get memory and cache statistics
     */
    public String getCacheStats() {
        StringBuilder stats = new StringBuilder();
        stats.append(PerformanceMonitor.getMemoryStats()).append("\n");
        stats.append(LazySpeciesLoader.getCacheStats());
        return stats.toString();
    }
    
    /**
     * Shutdown performance utilities
     */
    public void shutdown() {
        LazySpeciesLoader.shutdown();
        logger.info("Species manager shutdown complete");
    }
    
    /**
     * Recursively delete directory
     */
    private boolean deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            return true;
        }
        
        boolean success = true;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    success &= deleteDirectory(file);
                }
            }
        }
        
        success &= dir.delete();
        return success;
    }

    private static final class MoveOperation {
        private final Path source;
        private final Path target;

        private MoveOperation(Path source, Path target) {
            this.source = source;
            this.target = target;
        }
    }
}
