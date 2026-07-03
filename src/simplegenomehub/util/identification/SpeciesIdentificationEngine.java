package simplegenomehub.util.identification;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.identification.SpeciesMatchResult.MatchType;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Main engine for identifying species based on sequence IDs
 * Supports multi-level matching: exact, pattern, and fuzzy matching
 */
public class SpeciesIdentificationEngine {
    
    private Map<String, SpeciesSequenceIndex> speciesIndexCache;
    private ExecutorService executorService;
    private boolean cacheWarmedUp = false;
    private simplegenomehub.model.SpeciesManager speciesManager;
    
    // Configuration parameters
    private double fuzzyMatchThreshold = 0.8;
    private double significanceThreshold = 0.1;
    private int maxFuzzyMatches = 5;
    private boolean enableFuzzyMatching = true;
    private boolean enablePatternMatching = true;
    
    public SpeciesIdentificationEngine(simplegenomehub.model.SpeciesManager speciesManager) {
        this.speciesManager = speciesManager;
        this.speciesIndexCache = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
    }
    
    /**
     * Identify species for a list of sequence IDs
     */
    public List<SpeciesMatchResult> identifySpecies(List<String> sequenceIds) {
        if (sequenceIds == null || sequenceIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Clean and validate input
        List<String> cleanIds = sequenceIds.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .distinct()
            .collect(Collectors.toList());
        
        if (cleanIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        System.out.println("Identifying species for " + cleanIds.size() + " sequence IDs...");
        
        // Ensure cache is warmed up
        if (!cacheWarmedUp) {
            warmUpCache();
        }
        
        // Process all species in parallel
        List<CompletableFuture<SpeciesMatchResult>> futures = new ArrayList<>();
        
        for (SpeciesInfo species : speciesManager.getAllSpecies()) {
            CompletableFuture<SpeciesMatchResult> future = CompletableFuture
                .supplyAsync(() -> processSpecies(species, cleanIds), executorService);
            futures.add(future);
        }
        
        // Collect results
        List<SpeciesMatchResult> results = new ArrayList<>();
        for (CompletableFuture<SpeciesMatchResult> future : futures) {
            try {
                SpeciesMatchResult result = future.get(30, TimeUnit.SECONDS);
                if (result.isSignificantMatch(significanceThreshold)) {
                    results.add(result);
                }
            } catch (Exception e) {
                System.err.println("Error processing species: " + e.getMessage());
            }
        }
        
        // Sort by confidence score (descending)
        Collections.sort(results);
        
        System.out.println("Found " + results.size() + " significant matches");
        return results;
    }
    
    /**
     * Process a single species against the query IDs
     */
    private SpeciesMatchResult processSpecies(SpeciesInfo species, List<String> queryIds) {
        SpeciesMatchResult result = new SpeciesMatchResult(species);
        SpeciesSequenceIndex index = getOrCreateIndex(species);
        
        for (String queryId : queryIds) {
            boolean matched = false;
            
            // Level 1: Exact match
            if (index.containsSequenceId(queryId)) {
                result.addMatch(queryId, queryId, queryId, MatchType.EXACT, 1.0);
                matched = true;
            }
            // Level 2: Flexible match (partial/suffix matching)
            else if (index.containsSequenceIdFlexible(queryId)) {
                result.addMatch(queryId, queryId, queryId, MatchType.PATTERN, 0.95);
                matched = true;
            }
            // Level 3: Pattern match
            else if (enablePatternMatching) {
                String patternMatch = index.findByPattern(queryId);
                if (patternMatch != null) {
                    result.addMatch(queryId, patternMatch, patternMatch, MatchType.PATTERN, 0.9);
                    matched = true;
                }
            }
            // Level 4: Fuzzy match
            else if (enableFuzzyMatching) {
                List<String> fuzzyMatches = index.findByFuzzyMatch(queryId, fuzzyMatchThreshold);
                if (!fuzzyMatches.isEmpty()) {
                    String bestMatch = fuzzyMatches.get(0); // Take the first (best) match
                    result.addMatch(queryId, bestMatch, bestMatch, MatchType.FUZZY, fuzzyMatchThreshold);
                    matched = true;
                }
            }
            
            if (!matched) {
                result.addUnmatched(queryId);
            }
        }
        
        result.finalize(queryIds.size());
        return result;
    }
    
    /**
     * Get or create species sequence index
     */
    private SpeciesSequenceIndex getOrCreateIndex(SpeciesInfo species) {
        String key = species.getSpeciesName() + "." + species.getVersion();
        return speciesIndexCache.computeIfAbsent(key, k -> {
            SpeciesSequenceIndex index = new SpeciesSequenceIndex(species);
            index.buildIndex();
            return index;
        });
    }
    
    /**
     * Warm up cache by building indexes for all species
     */
    public void warmUpCache() {
        if (cacheWarmedUp) return;
        
        System.out.println("Warming up species identification cache...");
        List<SpeciesInfo> allSpecies = speciesManager.getAllSpecies();
        
        // Build indexes in parallel
        List<CompletableFuture<Void>> futures = allSpecies.stream()
            .map(species -> CompletableFuture.runAsync(() -> {
                try {
                    getOrCreateIndex(species);
                } catch (Exception e) {
                    System.err.println("Failed to build index for " + species.getSpeciesName() + ": " + e.getMessage());
                }
            }, executorService))
            .collect(Collectors.toList());
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .orTimeout(5, TimeUnit.MINUTES)
            .join();
        
        cacheWarmedUp = true;
        System.out.println("Cache warm-up completed for " + speciesIndexCache.size() + " species");
    }
    
    /**
     * Get the best match from a list of results
     */
    public SpeciesMatchResult getBestMatch(List<SpeciesMatchResult> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        
        // Results are already sorted by confidence, so return the first one
        return results.get(0);
    }
    
    /**
     * Get statistics about the identification process
     */
    public Map<String, Object> getIdentificationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cachedSpecies", speciesIndexCache.size());
        stats.put("cacheWarmedUp", cacheWarmedUp);
        stats.put("fuzzyMatchThreshold", fuzzyMatchThreshold);
        stats.put("significanceThreshold", significanceThreshold);
        stats.put("enableFuzzyMatching", enableFuzzyMatching);
        stats.put("enablePatternMatching", enablePatternMatching);
        
        // Individual species stats
        Map<String, Object> speciesStats = new HashMap<>();
        for (Map.Entry<String, SpeciesSequenceIndex> entry : speciesIndexCache.entrySet()) {
            speciesStats.put(entry.getKey(), entry.getValue().getIndexStats());
        }
        stats.put("speciesDetails", speciesStats);
        
        return stats;
    }
    
    /**
     * Clear the cache and rebuild indexes
     */
    public void clearCache() {
        speciesIndexCache.clear();
        cacheWarmedUp = false;
        System.out.println("Species identification cache cleared");
    }
    
    /**
     * Search for specific sequence IDs across all species
     */
    public Map<String, List<SpeciesMatchResult>> searchSequenceIds(List<String> sequenceIds) {
        Map<String, List<SpeciesMatchResult>> results = new HashMap<>();
        
        for (String queryId : sequenceIds) {
            List<SpeciesMatchResult> idResults = identifySpecies(Collections.singletonList(queryId));
            if (!idResults.isEmpty()) {
                results.put(queryId, idResults);
            }
        }
        
        return results;
    }
    
    /**
     * Get species that contain any of the provided sequence IDs
     */
    public Set<SpeciesInfo> getSpeciesContaining(List<String> sequenceIds) {
        List<SpeciesMatchResult> results = identifySpecies(sequenceIds);
        return results.stream()
            .map(SpeciesMatchResult::getSpecies)
            .collect(Collectors.toSet());
    }
    
    // Configuration setters
    public void setFuzzyMatchThreshold(double threshold) {
        this.fuzzyMatchThreshold = Math.max(0.0, Math.min(1.0, threshold));
    }
    
    public void setSignificanceThreshold(double threshold) {
        this.significanceThreshold = Math.max(0.0, Math.min(1.0, threshold));
    }
    
    public void setMaxFuzzyMatches(int max) {
        this.maxFuzzyMatches = Math.max(1, max);
    }
    
    public void setEnableFuzzyMatching(boolean enable) {
        this.enableFuzzyMatching = enable;
    }
    
    public void setEnablePatternMatching(boolean enable) {
        this.enablePatternMatching = enable;
    }
    
    // Configuration getters
    public double getFuzzyMatchThreshold() { return fuzzyMatchThreshold; }
    public double getSignificanceThreshold() { return significanceThreshold; }
    public int getMaxFuzzyMatches() { return maxFuzzyMatches; }
    public boolean isEnableFuzzyMatching() { return enableFuzzyMatching; }
    public boolean isEnablePatternMatching() { return enablePatternMatching; }
    public boolean isCacheWarmedUp() { return cacheWarmedUp; }
    
    /**
     * Shutdown the engine and cleanup resources
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        speciesIndexCache.clear();
        cacheWarmedUp = false;
        System.out.println("Species identification engine shutdown completed");
    }
}