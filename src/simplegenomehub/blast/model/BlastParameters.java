/*
 * SimpleGenomeHub BLAST Parameters
 * Configuration parameters for BLAST execution
 */
package simplegenomehub.blast.model;

import biocjava.bioDoer.BLAST.BlastZone.BlastZone.BlastOnTheFly.OUTBLASTFMT;

/**
 * Parameters for BLAST execution
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastParameters {
    
    // Execution parameters
    private int numThreads = 4;
    private double evalue = 1e-5;
    private int maxTargetSeqs = 500;
    private OUTBLASTFMT outputFormat = OUTBLASTFMT.XML;
    private boolean shortQuery = false;
    
    // Advanced parameters
    private String customParameters = "";
    private int wordSize = -1;  // -1 means use default
    private int gapOpen = -1;   // -1 means use default
    private int gapExtend = -1; // -1 means use default
    private String matrix = ""; // empty means use default
    
    /**
     * Create default parameters
     */
    public BlastParameters() {
        // Use default values
    }
    
    /**
     * Create parameters with custom values
     */
    public BlastParameters(int numThreads, double evalue, int maxTargetSeqs, 
                          OUTBLASTFMT outputFormat) {
        this.numThreads = numThreads;
        this.evalue = evalue;
        this.maxTargetSeqs = maxTargetSeqs;
        this.outputFormat = outputFormat;
    }
    
    /**
     * Create a copy of parameters
     */
    public BlastParameters copy() {
        BlastParameters copy = new BlastParameters(numThreads, evalue, maxTargetSeqs, outputFormat);
        copy.shortQuery = this.shortQuery;
        copy.customParameters = this.customParameters;
        copy.wordSize = this.wordSize;
        copy.gapOpen = this.gapOpen;
        copy.gapExtend = this.gapExtend;
        copy.matrix = this.matrix;
        return copy;
    }
    
    /**
     * Validate parameters
     */
    public boolean isValid() {
        return numThreads > 0 && 
               numThreads <= 64 && 
               evalue > 0 && 
               maxTargetSeqs > 0 &&
               outputFormat != null;
    }
    
    /**
     * Get parameters formatted for display
     */
    public String getDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Threads: ").append(numThreads);
        sb.append(", E-value: ").append(evalue);
        sb.append(", Max target sequences: ").append(maxTargetSeqs);
        sb.append(", Output format: ").append(outputFormat);
        
        if (shortQuery) {
            sb.append(", Short query mode");
        }
        
        if (!customParameters.isEmpty()) {
            sb.append(", Custom parameters: ").append(customParameters);
        }
        
        return sb.toString();
    }
    
    // Getters and Setters
    public int getNumThreads() {
        return numThreads;
    }
    
    public void setNumThreads(int numThreads) {
        this.numThreads = Math.max(1, Math.min(64, numThreads));
    }
    
    public double getEvalue() {
        return evalue;
    }
    
    public void setEvalue(double evalue) {
        this.evalue = Math.max(0, evalue);
    }
    
    public int getMaxTargetSeqs() {
        return maxTargetSeqs;
    }
    
    public void setMaxTargetSeqs(int maxTargetSeqs) {
        this.maxTargetSeqs = Math.max(1, maxTargetSeqs);
    }
    
    public OUTBLASTFMT getOutputFormat() {
        return outputFormat;
    }
    
    public void setOutputFormat(OUTBLASTFMT outputFormat) {
        this.outputFormat = outputFormat;
    }
    
    public boolean isShortQuery() {
        return shortQuery;
    }
    
    public void setShortQuery(boolean shortQuery) {
        this.shortQuery = shortQuery;
    }
    
    public String getCustomParameters() {
        return customParameters;
    }
    
    public void setCustomParameters(String customParameters) {
        this.customParameters = customParameters != null ? customParameters : "";
    }
    
    public int getWordSize() {
        return wordSize;
    }
    
    public void setWordSize(int wordSize) {
        this.wordSize = wordSize;
    }
    
    public int getGapOpen() {
        return gapOpen;
    }
    
    public void setGapOpen(int gapOpen) {
        this.gapOpen = gapOpen;
    }
    
    public int getGapExtend() {
        return gapExtend;
    }
    
    public void setGapExtend(int gapExtend) {
        this.gapExtend = gapExtend;
    }
    
    public String getMatrix() {
        return matrix;
    }
    
    public void setMatrix(String matrix) {
        this.matrix = matrix != null ? matrix : "";
    }
    
    @Override
    public String toString() {
        return getDisplayString();
    }
}
