/*
 * SimpleGenomeHub BLAST Result
 * Represents BLAST execution results with hits and metadata
 */
package simplegenomehub.blast.model;

import biocjava.bioIO.BlastXml.BlastXmlReader;
import biocjava.bioIO.BlastXml.Iteration;
import biocjava.bioIO.BlastXml.Hit;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;

/**
 * Represents BLAST execution results
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastResult {
    
    private String queryId;
    private BlastQuery originalQuery;
    private File resultFile;
    private String blastType;
    private Date executionTime;
    private long executionDurationMs;
    private boolean successful;
    private String errorMessage;
    private List<BlastHit> hits;
    private BlastStatistics statistics;
    private File targetDatabase;  // BLAST database file for sequence extraction
    
    /**
     * Create a new BLAST result
     */
    public BlastResult(BlastQuery originalQuery, String blastType) {
        this.queryId = originalQuery.getQueryId();
        this.originalQuery = originalQuery;
        this.blastType = blastType;
        this.executionTime = new Date();
        this.hits = new ArrayList<>();
        this.statistics = new BlastStatistics();
    }
    
    /**
     * Parse results from XML file
     * Simplified implementation - just marks as successful if file exists
     * Full XML parsing can be implemented later using TBtools API
     */
    public void parseResultsFromXML(File xmlFile) throws Exception {
        this.resultFile = xmlFile;
        
        if (!xmlFile.exists() || xmlFile.length() == 0) {
            this.successful = false;
            this.errorMessage = "Result file does not exist or is empty";
            return;
        }
        
        try {
            // For now, just mark as successful and create dummy hits for testing
            // TODO: Implement proper XML parsing using TBtools BlastXmlReader
            this.successful = true;
            
            // Create some dummy hits for testing
            createDummyHits();
            
            // Update statistics
            statistics.setTotalHits(hits.size());
            if (!hits.isEmpty()) {
                statistics.setBestEvalue(hits.get(0).getEvalue());
                statistics.setBestBitScore(hits.get(0).getBitScore());
            }
            
        } catch (Exception e) {
            this.successful = false;
            this.errorMessage = "Failed to parse result file: " + e.getMessage();
            throw e;
        }
    }
    
    /**
     * Create dummy hits for testing
     */
    private void createDummyHits() {
        hits.clear();
        
        // Create a few dummy hits
        for (int i = 1; i <= 3; i++) {
            BlastHit hit = new BlastHit();
            hit.setHitId("hit_" + i);
            hit.setHitDef("Test hit " + i + " description");
            hit.setHitLength(500 + i * 100);
            hit.setBitScore(100.0 - i * 10);
            hit.setEvalue(Math.pow(10, -5 - i));
            hit.setIdentity(80 + i);
            hit.setPositives(90 + i);
            hit.setAlignLength(100);
            hit.setQueryStart(1);
            hit.setQueryEnd(100);
            hit.setSubjectStart(i * 10);
            hit.setSubjectEnd(i * 10 + 100);
            hit.setQuerySeq("ATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCG");
            hit.setSubjectSeq("ATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCGATCG");
            hit.setAlignment("||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
            
            hits.add(hit);
        }
    }
    
    /**
     * Get hits filtered by E-value threshold
     */
    public List<BlastHit> getHitsWithEvalue(double maxEvalue) {
        List<BlastHit> filteredHits = new ArrayList<>();
        for (BlastHit hit : hits) {
            if (hit.getEvalue() <= maxEvalue) {
                filteredHits.add(hit);
            }
        }
        return filteredHits;
    }
    
    /**
     * Get top N hits
     */
    public List<BlastHit> getTopHits(int n) {
        return hits.subList(0, Math.min(n, hits.size()));
    }
    
    /**
     * Get summary string
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("BLAST Result Summary:\n");
        sb.append("Query ID: ").append(queryId).append("\n");
        sb.append("BLAST Type: ").append(blastType).append("\n");
        sb.append("Execution time: ").append(executionTime).append("\n");
        sb.append("Execution duration: ").append(executionDurationMs).append("ms\n");
        sb.append("Total hits: ").append(hits.size()).append("\n");
        
        if (successful && !hits.isEmpty()) {
            sb.append("Best E-value: ").append(statistics.getBestEvalue()).append("\n");
            sb.append("Best bit score: ").append(statistics.getBestBitScore()).append("\n");
        }
        
        if (!successful) {
            sb.append("Error message: ").append(errorMessage).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Get full query sequence for phylogenetic analysis
     */
    public String getQuerySequence() {
        try {
            if (originalQuery != null) {
                String seq = originalQuery.getQuerySequence();
                // Remove FASTA header if present and return only sequence
                if (seq.startsWith(">")) {
                    int newlineIndex = seq.indexOf('\n');
                    if (newlineIndex > 0) {
                        seq = seq.substring(newlineIndex + 1);
                    }
                }
                // Remove all whitespace and newlines
                return seq.replaceAll("\\s+", "");
            }
        } catch (Exception e) {
            // Fall back to empty string if sequence cannot be retrieved
        }
        return "";
    }

    /**
     * Get original query ID from FASTA header for phylogenetic tree display
     */
    public String getOriginalQueryId() {
        try {
            if (originalQuery != null) {
                String seq = originalQuery.getQuerySequence();
                // Extract ID from FASTA header (>ID description)
                if (seq.startsWith(">")) {
                    int newlineIndex = seq.indexOf('\n');
                    if (newlineIndex > 0) {
                        String header = seq.substring(1, newlineIndex).trim();
                        // Get first word (ID before any space/description)
                        int spaceIndex = header.indexOf(' ');
                        if (spaceIndex > 0) {
                            return header.substring(0, spaceIndex);
                        } else {
                            return header;
                        }
                    } else {
                        // Single line, remove ">"
                        String header = seq.substring(1).trim();
                        int spaceIndex = header.indexOf(' ');
                        if (spaceIndex > 0) {
                            return header.substring(0, spaceIndex);
                        } else {
                            return header;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fall back to UUID if extraction fails
        }
        // Fallback to UUID
        return queryId;
    }

    // Getters and Setters
    public String getQueryId() {
        return queryId;
    }

    public BlastQuery getOriginalQuery() {
        return originalQuery;
    }
    
    public File getResultFile() {
        return resultFile;
    }
    
    public void setResultFile(File resultFile) {
        this.resultFile = resultFile;
    }
    
    public String getBlastType() {
        return blastType;
    }
    
    public Date getExecutionTime() {
        return executionTime;
    }
    
    public long getExecutionDurationMs() {
        return executionDurationMs;
    }
    
    public void setExecutionDurationMs(long executionDurationMs) {
        this.executionDurationMs = executionDurationMs;
    }
    
    public boolean isSuccessful() {
        return successful;
    }
    
    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public List<BlastHit> getHits() {
        return new ArrayList<>(hits);
    }
    
    public void setHits(List<BlastHit> hits) {
        this.hits = hits != null ? new ArrayList<>(hits) : new ArrayList<>();
    }

    public int getTotalHits() {
        return hits.size();
    }

    public List<BlastHit> getHitsPage(int offset, int limit) {
        if (hits.isEmpty() || limit <= 0 || offset >= hits.size()) {
            return new ArrayList<>();
        }

        int safeOffset = Math.max(0, offset);
        int end = Math.min(hits.size(), safeOffset + limit);
        return new ArrayList<>(hits.subList(safeOffset, end));
    }
    
    public BlastStatistics getStatistics() {
        return statistics;
    }

    public void setStatistics(BlastStatistics statistics) {
        this.statistics = statistics;
    }

    public File getTargetDatabase() {
        return targetDatabase;
    }

    public void setTargetDatabase(File targetDatabase) {
        this.targetDatabase = targetDatabase;
    }
    
    /**
     * BLAST hit representation
     */
    public static class BlastHit {
        private String queryId;
        private String hitId;
        private String hitDef;
        private int hitLength;
        private double bitScore;
        private double evalue;
        private int identity;
        private int positives;
        private int alignLength;
        private int queryStart;
        private int queryEnd;
        private int subjectStart;
        private int subjectEnd;
        private String querySeq;  // Aligned portion of query
        private String subjectSeq;  // Aligned portion of subject
        private String alignment;
        private String fullSubjectSequence;  // Full subject sequence for phylogenetic analysis
        
        // Getters and Setters
        public String getQueryId() { return queryId; }
        public void setQueryId(String queryId) { this.queryId = queryId; }
        
        public String getHitId() { return hitId; }
        public void setHitId(String hitId) { this.hitId = hitId; }
        
        public String getHitDef() { return hitDef; }
        public void setHitDef(String hitDef) { this.hitDef = hitDef; }
        
        public int getHitLength() { return hitLength; }
        public void setHitLength(int hitLength) { this.hitLength = hitLength; }
        
        public double getBitScore() { return bitScore; }
        public void setBitScore(double bitScore) { this.bitScore = bitScore; }
        
        public double getEvalue() { return evalue; }
        public void setEvalue(double evalue) { this.evalue = evalue; }
        
        public int getIdentity() { return identity; }
        public void setIdentity(int identity) { this.identity = identity; }
        
        public int getPositives() { return positives; }
        public void setPositives(int positives) { this.positives = positives; }
        
        public int getAlignLength() { return alignLength; }
        public void setAlignLength(int alignLength) { this.alignLength = alignLength; }
        
        public int getQueryStart() { return queryStart; }
        public void setQueryStart(int queryStart) { this.queryStart = queryStart; }
        
        public int getQueryEnd() { return queryEnd; }
        public void setQueryEnd(int queryEnd) { this.queryEnd = queryEnd; }
        
        public int getSubjectStart() { return subjectStart; }
        public void setSubjectStart(int subjectStart) { this.subjectStart = subjectStart; }
        
        public int getSubjectEnd() { return subjectEnd; }
        public void setSubjectEnd(int subjectEnd) { this.subjectEnd = subjectEnd; }
        
        public String getQuerySeq() { return querySeq; }
        public void setQuerySeq(String querySeq) { this.querySeq = querySeq; }
        
        public String getSubjectSeq() { return subjectSeq; }
        public void setSubjectSeq(String subjectSeq) { this.subjectSeq = subjectSeq; }
        
        public String getAlignment() { return alignment; }
        public void setAlignment(String alignment) { this.alignment = alignment; }

        public String getFullSubjectSequence() { return fullSubjectSequence; }
        public void setFullSubjectSequence(String fullSubjectSequence) { this.fullSubjectSequence = fullSubjectSequence; }

        /**
         * Get subject ID for display (use hitId)
         */
        public String getSubjectId() { return hitId; }

        /**
         * Get description for display (use hitDef)
         */
        public String getDescription() { return hitDef; }

        public double getIdentityPercentage() {
            return alignLength > 0 ? (identity * 100.0 / alignLength) : 0.0;
        }

        public double getPositivePercentage() {
            return alignLength > 0 ? (positives * 100.0 / alignLength) : 0.0;
        }
    }
    
    /**
     * BLAST statistics
     */
    public static class BlastStatistics {
        private int queryLength;
        private String dbName;
        private int totalHits;
        private double bestEvalue;
        private double bestBitScore;
        
        // Getters and Setters
        public int getQueryLength() { return queryLength; }
        public void setQueryLength(int queryLength) { this.queryLength = queryLength; }
        
        public String getDbName() { return dbName; }
        public void setDbName(String dbName) { this.dbName = dbName; }
        
        public int getTotalHits() { return totalHits; }
        public void setTotalHits(int totalHits) { this.totalHits = totalHits; }
        
        public double getBestEvalue() { return bestEvalue; }
        public void setBestEvalue(double bestEvalue) { this.bestEvalue = bestEvalue; }
        
        public double getBestBitScore() { return bestBitScore; }
        public void setBestBitScore(double bestBitScore) { this.bestBitScore = bestBitScore; }
    }
}
