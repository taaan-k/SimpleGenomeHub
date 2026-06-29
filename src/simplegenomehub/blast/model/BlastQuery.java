/*
 * SimpleGenomeHub BLAST Query
 * Represents a BLAST query with target and parameters
 */
package simplegenomehub.blast.model;

import simplegenomehub.blast.BlastConfig.SequenceType;
import simplegenomehub.model.SpeciesInfo;
import java.io.*;
import java.util.UUID;

/**
 * Represents a BLAST query
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastQuery {
    
    private String queryId;
    private String querySequence;
    private File queryFile;
    private SpeciesInfo targetSpecies;
    private SequenceType targetSequenceType;
    private BlastParameters parameters;
    private long timestamp;
    
    /**
     * Create a new BLAST query
     */
    public BlastQuery() {
        this.queryId = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.parameters = new BlastParameters();
    }
    
    /**
     * Create query with sequence text
     */
    public BlastQuery(String querySequence, SpeciesInfo targetSpecies, 
                     SequenceType targetSequenceType) {
        this();
        this.querySequence = querySequence;
        this.targetSpecies = targetSpecies;
        this.targetSequenceType = targetSequenceType;
    }
    
    /**
     * Create query with sequence file
     */
    public BlastQuery(File queryFile, SpeciesInfo targetSpecies, 
                     SequenceType targetSequenceType) {
        this();
        this.queryFile = queryFile;
        this.targetSpecies = targetSpecies;
        this.targetSequenceType = targetSequenceType;
    }
    
    /**
     * Get query sequence as temporary file
     * Creates a temporary file if needed
     */
    public File getQueryFile() throws IOException {
        if (queryFile != null && queryFile.exists()) {
            return queryFile;
        }
        
        if (querySequence != null && !querySequence.trim().isEmpty()) {
            // Create temporary file from sequence text
            File tempFile = File.createTempFile("blast_query_" + queryId, ".fasta");
            tempFile.deleteOnExit();
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                // Ensure sequence is in FASTA format
                String sequence = querySequence.trim();
                if (!sequence.startsWith(">")) {
                    writer.write(">Query_" + queryId + "\n");
                }
                writer.write(sequence);
                writer.write("\n");
            }
            
            return tempFile;
        }
        
        throw new IOException("No query sequence or file specified");
    }
    
    /**
     * Get query sequence text
     */
    public String getQuerySequence() throws IOException {
        if (querySequence != null) {
            return querySequence;
        }
        
        if (queryFile != null && queryFile.exists()) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(queryFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            return sb.toString();
        }
        
        return "";
    }
    
    /**
     * Check if query is valid
     */
    public boolean isValid() {
        try {
            return (querySequence != null && !querySequence.trim().isEmpty()) ||
                   (queryFile != null && queryFile.exists()) &&
                   targetSpecies != null &&
                   targetSequenceType != null &&
                   parameters != null && parameters.isValid();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get query description for display
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        
        if (targetSpecies != null) {
            sb.append("Target species: ").append(targetSpecies.getSpeciesName());
            sb.append(" (").append(targetSpecies.getVersion()).append(")");
        }
        
        if (targetSequenceType != null) {
            sb.append(", Sequence type: ").append(targetSequenceType);
        }
        
        try {
            String sequence = getQuerySequence();
            if (sequence.length() > 50) {
                sb.append(", Query sequence: ").append(sequence.substring(0, 50)).append("...");
            } else {
                sb.append(", Query sequence: ").append(sequence);
            }
        } catch (IOException e) {
            sb.append(", Query file: ").append(queryFile != null ? queryFile.getName() : "Unknown");
        }
        
        return sb.toString();
    }
    
    // Getters and Setters
    public String getQueryId() {
        return queryId;
    }
    
    public void setQueryId(String queryId) {
        this.queryId = queryId;
    }
    
    public void setQuerySequence(String querySequence) {
        this.querySequence = querySequence;
        this.queryFile = null; // Clear file if setting sequence text
    }
    
    public void setQueryFile(File queryFile) {
        this.queryFile = queryFile;
        this.querySequence = null; // Clear sequence text if setting file
    }
    
    public File getSequenceFile() {
        return queryFile;
    }
    
    public SpeciesInfo getTargetSpecies() {
        return targetSpecies;
    }
    
    public void setTargetSpecies(SpeciesInfo targetSpecies) {
        this.targetSpecies = targetSpecies;
    }
    
    public SequenceType getTargetSequenceType() {
        return targetSequenceType;
    }
    
    public void setTargetSequenceType(SequenceType targetSequenceType) {
        this.targetSequenceType = targetSequenceType;
    }
    
    public BlastParameters getParameters() {
        return parameters;
    }
    
    public void setParameters(BlastParameters parameters) {
        this.parameters = parameters != null ? parameters : new BlastParameters();
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "BlastQuery{" +
               "id='" + queryId + '\'' +
               ", target=" + (targetSpecies != null ? targetSpecies.getSpeciesName() : "null") +
               ", type=" + targetSequenceType +
               ", timestamp=" + timestamp +
               '}';
    }
}
