package simplegenomehub.blast;

import simplegenomehub.blast.model.*;
import simplegenomehub.model.SpeciesInfo;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * ProcessBuilder-based BLAST executor with full environment control
 * Replaces TBtools BlastOnTheFly for better control over execution environment
 */
public class ProcessBasedBlastExecutor {
    
    private static final Logger logger = Logger.getLogger(ProcessBasedBlastExecutor.class.getName());
    
    private final BlastConfig config;
    private final BlastDatabaseManager dbManager;
    
    // Cache for sequence ID mappings: database path -> (ordinal -> original ID)
    private static final Map<String, Map<Integer, String>> sequenceIdMappings = new ConcurrentHashMap<>();
    
    public ProcessBasedBlastExecutor(BlastConfig config, BlastDatabaseManager dbManager) {
        this.config = config;
        this.dbManager = dbManager;
    }
    
    /**
     * Execute BLAST using ProcessBuilder with full environment control
     */
    public BlastResult executeBlast(BlastQuery query) throws Exception {
        logger.info("Starting ProcessBuilder-based BLAST execution: " + query.getQueryId());
        long startTime = System.currentTimeMillis();
        
        // 1. Ensure target database exists
        BlastDatabase targetDb = dbManager.ensureDatabaseExists(
            query.getTargetSpecies(), 
            query.getTargetSequenceType()
        );
        
        // 2. Prepare query file
        File queryFile = query.getQueryFile();
        validateQueryFile(queryFile);
        
        // 3. Determine BLAST type
        BlastExecutor.BlastType blastType = determineBlastType(queryFile, targetDb);
        logger.info("Auto-selected BLAST type: " + blastType.getCommand());
        
        // 4. Create makeblastdb if needed
        if (!isDatabaseValid(targetDb)) {
            createBlastDatabase(targetDb);
        }
        
        // 5. Execute BLAST query
        BlastResult result = executeBlastQuery(query, queryFile, targetDb, blastType);
        
        // 6. Set execution time
        long duration = System.currentTimeMillis() - startTime;
        result.setExecutionDurationMs(duration);
        
        logger.info("ProcessBuilder BLAST execution completed: " + query.getQueryId() + 
                   " (Duration: " + duration + "ms)");
        return result;
    }
    
    /**
     * Create BLAST database using ProcessBuilder
     */
    private void createBlastDatabase(BlastDatabase targetDb) throws Exception {
        logger.info("Creating BLAST database using ProcessBuilder: " + targetDb.getDatabaseName());
        
        String makeblastdbCmd = config.resolveBlastCommand("makeblastdb");
        
        File sourceFile = new File(targetDb.getSourceFilePath());
        File dbDir = new File(targetDb.getDatabasePath()).getParentFile();
        
        // Ensure database directory exists
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        
        // Build makeblastdb command
        String dbtype = targetDb.getMolecularType() == BlastConfig.MolecularType.PROTEIN ? "prot" : "nucl";
        File outFile = new File(dbDir, targetDb.getDatabaseName());
        
        List<String> command = Arrays.asList(
            makeblastdbCmd,
            "-in", sourceFile.getAbsolutePath(),
            "-dbtype", dbtype,
            "-out", outFile.getAbsolutePath(),
            "-title", targetDb.getDatabaseName(),
            "-parse_seqids"
        );
        
        logger.info("Database output path: " + outFile.getAbsolutePath());
        logger.info("Database directory exists: " + dbDir.exists());
        logger.info("Source file exists: " + sourceFile.exists());
        
        logger.info("makeblastdb command: " + String.join(" ", command));
        
        // Execute makeblastdb
        ProcessBuilder pb = new ProcessBuilder(command);
        setupProcessEnvironment(pb);
        pb.directory(dbDir);
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Capture output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\\n");
                logger.info("makeblastdb: " + line);
            }
        }
        
        // Wait for completion
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("makeblastdb execution timed out after 5 minutes");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new Exception("makeblastdb failed with exit code " + exitCode + 
                              ". Output: " + output.toString());
        }
        
        logger.info("BLAST database created successfully: " + targetDb.getDatabaseName());
    }
    
    /**
     * Execute BLAST query using ProcessBuilder
     */
    private BlastResult executeBlastQuery(BlastQuery query, File queryFile, 
                                        BlastDatabase targetDb, BlastExecutor.BlastType blastType) throws Exception {
        
        BlastParameters params = query.getParameters();
        String blastCmd = config.resolveBlastCommand(blastType.getCommand());
        
        // Create output file
        File outputFile = File.createTempFile("blast_result_" + query.getQueryId(), ".xml");
        outputFile.deleteOnExit();
        
        // Build BLAST command - use only database name, set working directory to avoid path issues
        File dbDir = new File(new File(targetDb.getDatabasePath()).getParent());
        String dbName = targetDb.getDatabaseName();
        
        List<String> command = new ArrayList<>(Arrays.asList(
            blastCmd,
            "-query", queryFile.getAbsolutePath(),
            "-db", dbName, // Use only database name, not full path
            "-out", outputFile.getAbsolutePath(),
            "-outfmt", "5", // XML format
            "-evalue", String.valueOf(params.getEvalue()),
            "-num_threads", String.valueOf(params.getNumThreads()),
            "-max_target_seqs", String.valueOf(params.getMaxTargetSeqs())
        ));
        
        // Add additional parameters for short sequences if needed
        if (params.isShortQuery()) {
            command.addAll(Arrays.asList("-task", "blastp-short"));
        }
        
        logger.info("BLAST command: " + String.join(" ", command));
        logger.info("Working directory: " + dbDir.getAbsolutePath());
        logger.info("Database name: " + dbName);
        
        // Execute BLAST
        ProcessBuilder pb = new ProcessBuilder(command);
        setupProcessEnvironment(pb);
        pb.directory(dbDir); // Set working directory to database directory
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Capture output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\\n");
                logger.fine("BLAST: " + line);
            }
        }
        
        // Wait for completion
        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("BLAST execution timed out after 10 minutes");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new Exception("BLAST failed with exit code " + exitCode + 
                              ". Output: " + output.toString());
        }
        
        // Create result object
        BlastResult result = new BlastResult(query, blastType.getCommand());
        result.setResultFile(outputFile);
        
        // Parse results - we're always using XML format (outfmt 5)
        parseXmlResults(result, outputFile, targetDb);
        
        logger.info("BLAST query completed successfully. Found " + result.getHits().size() + " hits");
        logger.info("Result object hits size: " + result.getHits().size());
        logger.info("Result object class: " + result.getClass().getName());
        return result;
    }
    
    /**
     * Setup process environment with BLAST path and proper encoding
     */
    private void setupProcessEnvironment(ProcessBuilder pb) {
        Map<String, String> env = pb.environment();
        
        // Add BLAST path to PATH if configured
        String blastPath = config.getBlastExecutablePath();
        if (!blastPath.isEmpty()) {
            String currentPath = env.get("PATH");
            if (currentPath == null) currentPath = "";
            
            if (!currentPath.toLowerCase().contains(blastPath.toLowerCase())) {
                env.put("PATH", blastPath + File.pathSeparator + currentPath);
                logger.info("Added BLAST path to process environment: " + blastPath);
            }
        }
        
        // Set encoding for Windows compatibility
        env.put("LANG", "en_US.UTF-8");
        env.put("LC_ALL", "en_US.UTF-8");
        
        // Debug: log environment
        logger.fine("Process PATH: " + env.get("PATH"));
    }
    
    /**
     * Validate query file
     */
    private void validateQueryFile(File queryFile) throws IOException {
        if (!queryFile.exists()) {
            throw new FileNotFoundException("Query file not found: " + queryFile.getAbsolutePath());
        }
        
        if (queryFile.length() == 0) {
            throw new IOException("Query file is empty");
        }
        
        // Check if file is in FASTA format
        try (BufferedReader reader = new BufferedReader(new FileReader(queryFile))) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.startsWith(">")) {
                throw new IOException("Query file is not in valid FASTA format");
            }
        }
    }
    
    /**
     * Check if database is valid and up-to-date
     */
    private boolean isDatabaseValid(BlastDatabase database) {
        try {
            return database.isValid();
        } catch (Exception e) {
            logger.warning("Database validation failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Determine BLAST type based on query and database molecular types
     */
    private BlastExecutor.BlastType determineBlastType(File queryFile, BlastDatabase targetDb) throws IOException {
        boolean isQueryNucleotide = isNucleotideSequence(queryFile);
        boolean isTargetNucleotide = (targetDb.getMolecularType() == BlastConfig.MolecularType.NUCLEOTIDE);
        
        if (isQueryNucleotide && isTargetNucleotide) {
            return BlastExecutor.BlastType.BLASTN;
        } else if (isQueryNucleotide && !isTargetNucleotide) {
            return BlastExecutor.BlastType.BLASTX;
        } else if (!isQueryNucleotide && isTargetNucleotide) {
            return BlastExecutor.BlastType.TBLASTN;
        } else {
            return BlastExecutor.BlastType.BLASTP;
        }
    }
    
    /**
     * Simple nucleotide sequence detection
     */
    private boolean isNucleotideSequence(File fastaFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(fastaFile))) {
            String line;
            StringBuilder sequence = new StringBuilder();
            
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(">")) {
                    sequence.append(line.trim().toUpperCase());
                    if (sequence.length() > 1000) break; // Sample first 1000 chars
                }
            }
            
            if (sequence.length() == 0) return false;
            
            // Count nucleotide vs amino acid characters
            int nucCount = 0;
            int totalCount = 0;
            
            for (char c : sequence.toString().toCharArray()) {
                if (Character.isLetter(c)) {
                    totalCount++;
                    if (c == 'A' || c == 'T' || c == 'G' || c == 'C' || c == 'U' || c == 'N') {
                        nucCount++;
                    }
                }
            }
            
            // If >80% nucleotides, consider it nucleotide sequence
            return totalCount > 0 && (double) nucCount / totalCount > 0.8;
        }
    }
    
    /**
     * Parse XML results using TBtools BlastXmlReader
     */
    private void parseXmlResults(BlastResult result, File outputFile, BlastDatabase targetDb) {
        try {
            logger.info("Starting XML parsing with TBtools BlastXmlToBlastFoolTable: " + outputFile.getAbsolutePath());
            
            // Step 1: Convert XML to table format using TBtools
            File tempTableFile = File.createTempFile("blast_table_", ".tsv");
            tempTableFile.deleteOnExit();
            
            biocjava.bioIO.BlastXml.BlastXmlToBlastFoolTable.xml2ShowerTable(outputFile, tempTableFile);
            logger.info("XML converted to table format: " + tempTableFile.getAbsolutePath());
            
            // Step 2: Read table format using BlastFoolTableReader
            biocjava.bioIO.BlastXml.BlastFoolTableReader tableReader = 
                new biocjava.bioIO.BlastXml.BlastFoolTableReader(tempTableFile);
            
            java.util.List<BlastResult.BlastHit> hits = new java.util.ArrayList<>();
            int totalRecords = 0;
            
            // Read each record from the table
            while (tableReader.hasNext()) {
                biocjava.bioIO.BlastXml.BlastTableRecord record = tableReader.getNext();
                totalRecords++;
                
                // Skip header row (it contains "BitScore" string instead of number)
                if (totalRecords == 1 && record.getBitscore().equals("BitScore")) {
                    continue;
                }
                
                // Convert table record to our BlastHit format
                BlastResult.BlastHit hit = convertTableRecord(record, targetDb);
                if (hit != null) {
                    hits.add(hit);
                }
                
            }
            
            // Set hits to result using proper setter method
            result.setHits(hits);
            result.setSuccessful(true);
            
            logger.info("XML parsing completed successfully. Processed " + totalRecords + " records, " +
                       "converted " + hits.size() + " hits to result");
            logger.info("First hit: " + (hits.isEmpty() ? "none" : hits.get(0).getHitId() + " - " + hits.get(0).getHitDef()));
            
        } catch (Exception e) {
            logger.severe("Failed to parse XML results: " + e.getMessage());
            e.printStackTrace();
            result.setSuccessful(false);
            result.setErrorMessage("XML parsing failed: " + e.getMessage());
        }
    }
    
    /**
     * Convert BlastTableRecord to our BlastHit format
     */
    private BlastResult.BlastHit convertTableRecord(biocjava.bioIO.BlastXml.BlastTableRecord record, BlastDatabase targetDb) {
        try {
            BlastResult.BlastHit hit = new BlastResult.BlastHit();
            
            // Extract query ID from table record
            hit.setQueryId(record.getQseqid());
            
            // Basic hit information from table record - extract original sequence ID from FASTA
            String originalHitId = extractOriginalSequenceId(record.getSseqid(), targetDb);
            hit.setHitId(originalHitId);
            hit.setHitDef(originalHitId); // Use original sequence ID as definition
            
            // Parse numeric values
            hit.setBitScore(Double.parseDouble(record.getBitscore()));
            hit.setEvalue(Double.parseDouble(record.getEvalue()));
            hit.setAlignLength(Integer.parseInt(record.getLength()));
            hit.setQueryStart(Integer.parseInt(record.getQstart()));
            hit.setQueryEnd(Integer.parseInt(record.getQend()));
            hit.setSubjectStart(Integer.parseInt(record.getSstart()));
            hit.setSubjectEnd(Integer.parseInt(record.getSend()));
            
            // Calculate identity from percentage
            double identityPercent = Double.parseDouble(record.getPident());
            int alignLen = Integer.parseInt(record.getLength());
            hit.setIdentity((int) Math.round(identityPercent * alignLen / 100.0));
            
            // Calculate positives (assume similar to identity for now)
            hit.setPositives(hit.getIdentity());
            
            // Set a reasonable hit length (we don't have this in the table format)
            hit.setHitLength(hit.getSubjectEnd() - hit.getSubjectStart() + 1);
            
            // We don't have alignment sequences in table format, set placeholders
            hit.setQuerySeq("-");
            hit.setSubjectSeq("-");
            hit.setAlignment("-");
            
            return hit;
            
        } catch (Exception e) {
            logger.warning("Failed to convert table record: " + e.getMessage() + 
                          " for record: " + record.toString());
            return null;
        }
    }
    
    /**
     * Simple method to extract original sequence ID from BLAST internal ID format
     * For immediate use without database context
     */
    private String extractSimpleOriginalId(String blastInternalId) {
        // If not in BLAST internal format, return as-is
        if (!blastInternalId.startsWith("gnl|BL_ORD_ID|")) {
            return blastInternalId;
        }
        
        // For gnl|BL_ORD_ID|XXXX format, return a simplified ID
        try {
            String ordinalStr = blastInternalId.substring("gnl|BL_ORD_ID|".length());
            return "SeqID_" + ordinalStr;
        } catch (Exception e) {
            return blastInternalId;
        }
    }
    
    /**
     * Extract original sequence ID from BLAST internal ID format
     * Handles formats like "gnl|BL_ORD_ID|7259" by mapping back to original sequence IDs
     */
    private String extractOriginalSequenceId(String blastInternalId, BlastDatabase targetDb) {
        // If not in BLAST internal format, return as-is
        if (!blastInternalId.startsWith("gnl|BL_ORD_ID|")) {
            return blastInternalId;
        }
        
        // Extract the ordinal number from gnl|BL_ORD_ID|XXXX format
        try {
            String ordinalStr = blastInternalId.substring("gnl|BL_ORD_ID|".length());
            int ordinal = Integer.parseInt(ordinalStr);
            
            // Get or build sequence ID mapping for this database
            String dbKey = targetDb.getSourceFilePath();
            Map<Integer, String> idMapping = sequenceIdMappings.get(dbKey);
            
            if (idMapping == null) {
                // Build mapping from FASTA file
                idMapping = buildSequenceIdMapping(new File(targetDb.getSourceFilePath()));
                sequenceIdMappings.put(dbKey, idMapping);
            }
            
            // Look up original ID by ordinal
            String originalId = idMapping.get(ordinal);
            if (originalId != null) {
                logger.fine("Mapped ordinal " + ordinal + " to original ID: " + originalId);
                return originalId;
            } else {
                logger.warning("No mapping found for ordinal " + ordinal + " in database " + dbKey + 
                              ". Available mappings: " + idMapping.size());
                return "Unknown_" + ordinal;
            }
            
        } catch (Exception e) {
            logger.warning("Failed to parse BLAST internal ID: " + blastInternalId + ". Using as-is.");
            return blastInternalId;
        }
    }
    
    /**
     * Create mapping from BLAST ordinal IDs to original sequence IDs
     * This method reads the original FASTA file to build the mapping
     */
    private Map<Integer, String> buildSequenceIdMapping(File fastaFile) {
        Map<Integer, String> mapping = new HashMap<>();
        
        if (!fastaFile.exists()) {
            logger.warning("FASTA file not found for ID mapping: " + fastaFile.getAbsolutePath());
            return mapping;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(fastaFile))) {
            String line;
            int sequenceOrdinal = 0; // BLAST ordinals are 0-based
            
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(">")) {
                    // Extract sequence ID from FASTA header
                    String header = line.substring(1); // Remove ">"
                    String sequenceId = extractSequenceIdFromHeader(header);
                    
                    // Map ordinal to original sequence ID
                    mapping.put(sequenceOrdinal, sequenceId);
                    sequenceOrdinal++;
                }
            }
            
            logger.info("Built sequence ID mapping for " + fastaFile.getName() + 
                       ": " + mapping.size() + " sequences");
            
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to build sequence ID mapping for " + fastaFile.getAbsolutePath(), e);
        }
        
        return mapping;
    }
    
    /**
     * Extract the primary sequence ID from a FASTA header line
     * Handles various FASTA header formats
     */
    private String extractSequenceIdFromHeader(String header) {
        // Common formats:
        // >AT1G01010.1 | Symbols: NAC001 | chr1:3631-5899 FORWARD
        // >Aco015073.1 hypothetical protein
        // >gi|123456|ref|NP_123456.1| description
        
        // Split by whitespace and take the first part as the primary ID
        String[] parts = header.trim().split("\\s+");
        if (parts.length > 0) {
            String primaryId = parts[0];
            
            // Remove common prefixes if present
            if (primaryId.startsWith("gi|")) {
                // Handle NCBI format: gi|123456|ref|NP_123456.1|
                String[] giParts = primaryId.split("\\|");
                if (giParts.length >= 4) {
                    return giParts[3]; // Return the accession part
                }
            }
            
            // For most cases, just return the first part
            return primaryId;
        }
        
        // Fallback: return the entire header if parsing fails
        return header;
    }
}
