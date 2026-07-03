/*
 * Lazy Species Loader for Performance Optimization
 */
package simplegenomehub.util.performance;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.GenomeData;
import simplegenomehub.model.SpeciesMetadata;
import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Lazy loading utility for species data to improve performance
 * with large datasets. Loads basic info immediately but defers
 * heavy operations like genome statistics until actually needed.
 * 
 * @author SimpleGenomeHub
 */
public class LazySpeciesLoader {
    
    private static final Logger logger = Logger.getLogger(LazySpeciesLoader.class.getName());
    private static final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(4);
    
    // Cache for loaded genome data
    private static final ConcurrentHashMap<String, GenomeData> genomeDataCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CompletableFuture<GenomeData>> loadingTasks = new ConcurrentHashMap<>();
    
    /**
     * Load species with lazy loading strategy
     */
    public static SpeciesInfo loadSpeciesLazy(File speciesDir) {
        String dirName = speciesDir.getName();
        
        // Parse directory name (Species.version format)
        String[] parts = dirName.split("\\.", 2);
        if (parts.length != 2) {
            logger.warning("Invalid species directory name format: " + dirName);
            return null;
        }
        
        String speciesName = parts[0];
        String version = parts[1];
        
        // Create species info with basic data only
        SpeciesInfo species = new SpeciesInfo(speciesName, version);
        species.initializeFileStructure(speciesDir.getParentFile());
        
        // Try to load metadata first
        SpeciesMetadata metadata = SpeciesMetadata.loadFromFile(speciesDir);
        if (metadata != null) {
            metadata.applyToSpeciesInfo(species);
            logger.info("Loaded metadata for " + speciesName + "." + version + " with notes: " + 
                       (metadata.getNotes().isEmpty() ? "none" : "'" + metadata.getNotes() + "'"));
        } else {
            // Fallback: Set import time from directory modification time
            try {
                long lastModified = speciesDir.lastModified();
                species.setImportTime(java.time.LocalDateTime.ofEpochSecond(
                    lastModified / 1000, 0, java.time.ZoneOffset.systemDefault().getRules()
                    .getOffset(java.time.Instant.ofEpochMilli(lastModified))));
            } catch (Exception e) {
                logger.warning("Failed to determine import time for: " + dirName);
            }
        }
        
        // Check if genome data is immediately available (cached or quick load)
        String cacheKey = getCacheKey(speciesName, version);
        GenomeData cachedData = genomeDataCache.get(cacheKey);
        if (cachedData != null) {
            species.setGenomeData(cachedData);
        } else if (species.getStatsFile().exists() && species.getStatsFile().length() < 1024 * 1024) {
            // Quick load for small stats files
            try {
                GenomeData genomeData = GenomeData.loadFromFile(species.getStatsFile());
                if (genomeData != null) {
                    species.setGenomeData(genomeData);
                    genomeDataCache.put(cacheKey, genomeData);
                }
            } catch (Exception e) {
                logger.warning("Failed to quick-load stats for: " + dirName);
            }
        }
        
        // Note: Functional annotations are now loaded on-demand to improve startup performance
        
        return species;
    }
    
    /**
     * Load genome data asynchronously
     */
    public static CompletableFuture<GenomeData> loadGenomeDataAsync(SpeciesInfo species) {
        String cacheKey = getCacheKey(species.getSpeciesName(), species.getVersion());
        
        // Return cached data if available
        GenomeData cached = genomeDataCache.get(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        
        // Return existing loading task if already in progress
        CompletableFuture<GenomeData> existingTask = loadingTasks.get(cacheKey);
        if (existingTask != null) {
            return existingTask;
        }
        
        // Start new loading task
        CompletableFuture<GenomeData> loadingTask = CompletableFuture.supplyAsync(() -> {
            try {
                GenomeData genomeData = null;
                
                // Try to load from stats file first
                if (species.getStatsFile().exists()) {
                    genomeData = GenomeData.loadFromFile(species.getStatsFile());
                }
                
                // If no stats file or loading failed, calculate fresh
                if (genomeData == null) {
                    File genomeFile = findGenomeFile(species);
                    File annotationFile = findAnnotationFile(species);
                    
                    if (genomeFile != null && annotationFile != null) {
                        genomeData = simplegenomehub.util.fileio.GenomeStatsCalculator
                            .calculateGenomeStats(genomeFile, annotationFile);
                    }
                }
                
                if (genomeData != null) {
                    genomeDataCache.put(cacheKey, genomeData);
                }
                
                return genomeData;
                
            } catch (Exception e) {
                logger.warning("Failed to load genome data for " + species.getDisplayName() + ": " + e.getMessage());
                return null;
            } finally {
                loadingTasks.remove(cacheKey);
            }
        }, backgroundExecutor);
        
        loadingTasks.put(cacheKey, loadingTask);
        return loadingTask;
    }
    
    /**
     * Preload genome data for multiple species in background
     */
    public static void preloadGenomeData(java.util.List<SpeciesInfo> speciesList) {
        for (SpeciesInfo species : speciesList) {
            if (species.getGenomeData() == null) {
                loadGenomeDataAsync(species).thenAccept(genomeData -> {
                    if (genomeData != null) {
                        species.setGenomeData(genomeData);
                    }
                });
            }
        }
    }
    
    /**
     * Clear cache to free memory
     */
    public static void clearCache() {
        genomeDataCache.clear();
        logger.info("Cleared genome data cache");
    }
    
    /**
     * Get cache statistics
     */
    public static String getCacheStats() {
        return String.format("Cache: %d entries, Loading: %d tasks", 
            genomeDataCache.size(), loadingTasks.size());
    }
    
    /**
     * Generate cache key for species
     */
    private static String getCacheKey(String speciesName, String version) {
        return speciesName + ":" + version;
    }
    
    /**
     * Find genome file in species
     */
    private static File findGenomeFile(SpeciesInfo species) {
        if (species.getSequenceDir() == null) return null;
        
        File[] genomeFiles = species.getSequenceDir().listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".fa") || 
            name.toLowerCase().endsWith(".fasta") ||
            name.toLowerCase().endsWith(".fas"));
        
        return (genomeFiles != null && genomeFiles.length > 0) ? genomeFiles[0] : null;
    }
    
    /**
     * Find annotation file in species
     */
    private static File findAnnotationFile(SpeciesInfo species) {
        if (species.getAnnotationDir() == null) return null;
        
        File[] annotationFiles = species.getAnnotationDir().listFiles((dir, name) -> 
            name.toLowerCase().endsWith(".gff") || 
            name.toLowerCase().endsWith(".gff3") ||
            name.toLowerCase().endsWith(".gtf"));
        
        return (annotationFiles != null && annotationFiles.length > 0) ? annotationFiles[0] : null;
    }
    
    /**
     * Shutdown executor on application exit
     */
    public static void shutdown() {
        backgroundExecutor.shutdown();
        logger.info("Lazy species loader shutdown");
    }
}