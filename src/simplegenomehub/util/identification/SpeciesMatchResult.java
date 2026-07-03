package simplegenomehub.util.identification;

import simplegenomehub.model.SpeciesInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a species identification result with confidence score and match details
 */
public class SpeciesMatchResult implements Comparable<SpeciesMatchResult> {
    
    public enum MatchType {
        EXACT("Exact Match"),
        PATTERN("Pattern Match"), 
        FUZZY("Fuzzy Match"),
        NONE("No Match");
        
        private final String description;
        
        MatchType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private SpeciesInfo species;
    private double confidenceScore;      // 0.0-1.0 confidence score
    private int matchedCount;           // Number of matched sequences
    private int totalCount;             // Total query sequences
    private List<String> matchedIds;    // List of matched sequence IDs
    private List<String> unmatchedIds;  // List of unmatched sequence IDs
    private MatchType primaryMatchType; // Primary match type for this result
    private List<SequenceMatch> detailedMatches; // Detailed match information
    
    /**
     * Represents a single sequence match
     */
    public static class SequenceMatch {
        private String queryId;
        private String matchedId;
        private String geneId;
        private MatchType matchType;
        private double similarity;
        
        public SequenceMatch(String queryId, String matchedId, String geneId, MatchType matchType, double similarity) {
            this.queryId = queryId;
            this.matchedId = matchedId;
            this.geneId = geneId;
            this.matchType = matchType;
            this.similarity = similarity;
        }
        
        // Getters
        public String getQueryId() { return queryId; }
        public String getMatchedId() { return matchedId; }
        public String getGeneId() { return geneId; }
        public MatchType getMatchType() { return matchType; }
        public double getSimilarity() { return similarity; }
    }
    
    public SpeciesMatchResult(SpeciesInfo species) {
        this.species = species;
        this.confidenceScore = 0.0;
        this.matchedCount = 0;
        this.totalCount = 0;
        this.matchedIds = new ArrayList<>();
        this.unmatchedIds = new ArrayList<>();
        this.primaryMatchType = MatchType.NONE;
        this.detailedMatches = new ArrayList<>();
    }
    
    /**
     * Add a sequence match to this result
     */
    public void addMatch(String queryId, String matchedId, String geneId, MatchType matchType, double similarity) {
        matchedIds.add(queryId);
        detailedMatches.add(new SequenceMatch(queryId, matchedId, geneId, matchType, similarity));
        matchedCount++;
        updatePrimaryMatchType(matchType);
    }
    
    /**
     * Add an unmatched sequence ID
     */
    public void addUnmatched(String queryId) {
        unmatchedIds.add(queryId);
    }
    
    /**
     * Set the total count and calculate confidence score
     */
    public void finalize(int totalCount) {
        this.totalCount = totalCount;
        calculateConfidenceScore();
    }
    
    /**
     * Calculate confidence score based on matches and match types
     */
    private void calculateConfidenceScore() {
        if (totalCount == 0) {
            confidenceScore = 0.0;
            return;
        }
        
        double baseScore = (double) matchedCount / totalCount;
        double weightFactor = getMatchTypeWeight(primaryMatchType);
        double patternBonus = calculatePatternBonus();
        
        confidenceScore = Math.min(1.0, baseScore * weightFactor + patternBonus);
    }
    
    /**
     * Get weight factor based on match type
     */
    private double getMatchTypeWeight(MatchType matchType) {
        switch (matchType) {
            case EXACT: return 1.0;
            case PATTERN: return 0.85;
            case FUZZY: return 0.65;
            default: return 0.0;
        }
    }
    
    /**
     * Calculate pattern consistency bonus
     */
    private double calculatePatternBonus() {
        if (detailedMatches.isEmpty()) return 0.0;
        
        // Count exact matches for bonus
        long exactMatches = detailedMatches.stream()
            .mapToLong(match -> match.getMatchType() == MatchType.EXACT ? 1 : 0)
            .sum();
        
        if (exactMatches > matchedCount * 0.8) {
            return 0.1; // High consistency bonus
        } else if (exactMatches > matchedCount * 0.5) {
            return 0.05; // Medium consistency bonus
        }
        
        return 0.0;
    }
    
    /**
     * Update primary match type based on new match
     */
    private void updatePrimaryMatchType(MatchType newMatchType) {
        if (primaryMatchType == MatchType.NONE || 
            newMatchType.ordinal() < primaryMatchType.ordinal()) {
            primaryMatchType = newMatchType;
        }
    }
    
    /**
     * Get match percentage as formatted string
     */
    public String getMatchPercentage() {
        return String.format("%.1f%%", confidenceScore * 100);
    }
    
    /**
     * Get match ratio as string (e.g., "45/47")
     */
    public String getMatchRatio() {
        return matchedCount + "/" + totalCount;
    }
    
    /**
     * Check if this is a significant match (confidence > threshold)
     */
    public boolean isSignificantMatch(double threshold) {
        return confidenceScore >= threshold;
    }
    
    // Getters
    public SpeciesInfo getSpecies() { return species; }
    public double getConfidenceScore() { return confidenceScore; }
    public int getMatchedCount() { return matchedCount; }
    public int getTotalCount() { return totalCount; }
    public List<String> getMatchedIds() { return new ArrayList<>(matchedIds); }
    public List<String> getUnmatchedIds() { return new ArrayList<>(unmatchedIds); }
    public MatchType getPrimaryMatchType() { return primaryMatchType; }
    public List<SequenceMatch> getDetailedMatches() { return new ArrayList<>(detailedMatches); }
    
    /**
     * Compare results by confidence score (descending order)
     */
    @Override
    public int compareTo(SpeciesMatchResult other) {
        int scoreComparison = Double.compare(other.confidenceScore, this.confidenceScore);
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        
        // If confidence scores are equal, compare by matched count
        int countComparison = Integer.compare(other.matchedCount, this.matchedCount);
        if (countComparison != 0) {
            return countComparison;
        }
        
        // If still equal, compare by match type quality
        return this.primaryMatchType.compareTo(other.primaryMatchType);
    }
    
    @Override
    public String toString() {
        return String.format("SpeciesMatchResult{species=%s, confidence=%.2f, matches=%d/%d, type=%s}",
            species.getSpeciesName(), confidenceScore, matchedCount, totalCount, primaryMatchType);
    }
}