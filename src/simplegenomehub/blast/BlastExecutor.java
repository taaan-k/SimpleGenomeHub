/*
 * SimpleGenomeHub BLAST Executor
 * Executes BLAST queries with automatic type detection and database management
 */
package simplegenomehub.blast;

import simplegenomehub.blast.BlastConfig.SequenceType;
import simplegenomehub.blast.model.*;
import biocjava.bioDoer.BLAST.BlastZone.BlastZone.BlastOnTheFly;
import biocjava.bioDoer.BLAST.BlastZone.BlastZone.BlastOnTheFly.OUTBLASTFMT;
import biocjava.bioIO.FastX.GuessMolecularType;
import simplegenomehub.config.ApplicationLayout;
import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes BLAST queries with automatic type detection and result parsing
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastExecutor {
    
    private static final Logger logger = Logger.getLogger(BlastExecutor.class.getName());
    
    private final BlastConfig config;
    private final BlastDatabaseManager dbManager;
    private final ExecutorService executorService;
    private final ProcessBasedBlastExecutor processExecutor;
    
    /**
     * Initialize BLAST executor
     */
    public BlastExecutor(BlastConfig config, BlastDatabaseManager dbManager) {
        this.config = config;
        this.dbManager = dbManager;
        this.executorService = Executors.newFixedThreadPool(2); // Limit concurrent BLAST executions
        this.processExecutor = new ProcessBasedBlastExecutor(config, dbManager);
        
        logger.info("BLAST executor initialized");
    }
    
    /**
     * Execute BLAST query synchronously
     */
    public BlastResult executeBlast(BlastQuery query) throws Exception {
        return executeBlastAsync(query).get();
    }
    
    /**
     * Execute BLAST query asynchronously
     */
    public CompletableFuture<BlastResult> executeBlastAsync(BlastQuery query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return performBlastExecution(query);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "BLAST execution failed: " + query.getQueryId(), e);
                
                // Return failed result
                BlastResult failedResult = new BlastResult(query, "Unknown");
                failedResult.setSuccessful(false);
                failedResult.setErrorMessage(e.getMessage());
                return failedResult;
            }
        }, executorService);
    }
    
    /**
     * Perform the actual BLAST execution
     */
    private BlastResult performBlastExecution(BlastQuery query) throws Exception {
        logger.info("Starting BLAST query: " + query.getQueryId());
        long startTime = System.currentTimeMillis();
        
        // 1. Validate query
        if (!query.isValid()) {
            throw new IllegalArgumentException("Invalid BLAST query");
        }
        
        // 2. Ensure target database exists
        BlastDatabase targetDb = dbManager.ensureDatabaseExists(
            query.getTargetSpecies(), 
            query.getTargetSequenceType()
        );
        
        // 3. Prepare query file
        File queryFile = query.getQueryFile();
        validateQueryFile(queryFile);
        
        // 4. Determine BLAST type
        BlastType blastType = determineBlastType(queryFile, targetDb);
        logger.info("Auto-selected BLAST type: " + blastType.getCommand());
        
        // 5. Execute BLAST using ProcessBuilder (more reliable than TBtools BlastOnTheFly)
        BlastResult result = processExecutor.executeBlast(query);
        
        // Alternative: Use TBtools BlastOnTheFly (commented out due to environment issues)
        // BlastResult result = performBlast(query, queryFile, targetDb, blastType);
        
        // 6. Set execution time
        long duration = System.currentTimeMillis() - startTime;
        result.setExecutionDurationMs(duration);
        
        logger.info("BLAST execution completed: " + query.getQueryId() + " (duration: " + duration + "ms)");
        return result;
    }
    
    /**
     * Validate query file
     */
    private void validateQueryFile(File queryFile) throws IOException {
        if (!queryFile.exists()) {
            throw new FileNotFoundException("Query file does not exist: " + queryFile.getAbsolutePath());
        }
        
        if (queryFile.length() == 0) {
            throw new IOException("Query file is empty");
        }
        
        // Check if file is in FASTA format
        try (BufferedReader reader = new BufferedReader(new FileReader(queryFile))) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.startsWith(">")) {
                throw new IOException("Query file is not a valid FASTA format");
            }
        }
    }
    
    /**
     * Determine appropriate BLAST type based on query and target
     */
    private BlastType determineBlastType(File queryFile, BlastDatabase targetDb) throws IOException {
        // Determine query molecular type
        boolean isQueryNucleotide = GuessMolecularType.isNuclFile(queryFile);
        boolean isTargetNucleotide = (targetDb.getMolecularType() == BlastConfig.MolecularType.NUCLEOTIDE);
        
        if (isQueryNucleotide && isTargetNucleotide) {
            return BlastType.BLASTN;
        } else if (isQueryNucleotide && !isTargetNucleotide) {
            return BlastType.BLASTX;
        } else if (!isQueryNucleotide && isTargetNucleotide) {
            return BlastType.TBLASTN;
        } else {
            return BlastType.BLASTP;
        }
    }
    
    /**
     * Perform the actual BLAST execution using TBtools
     */
    private BlastResult performBlast(BlastQuery query, File queryFile, 
                                   BlastDatabase targetDb, BlastType blastType) throws Exception {
        
        BlastParameters params = query.getParameters();
        
        // Create output file
        File outputFile = File.createTempFile("blast_result_" + query.getQueryId(), ".xml");
        outputFile.deleteOnExit();
        
        // Ensure BLAST is available before execution
        ensureBlastAvailable();
        
        // Configure BlastOnTheFly
        BlastOnTheFly blast = new BlastOnTheFly();
        
        blast.setQueryFile(queryFile);
        blast.setSubjectFile(new File(targetDb.getDatabasePath()));
        blast.setOutFile(outputFile);
        blast.setBlastType(blastType.getCommand());
        blast.setOutFmt(params.getOutputFormat());
        blast.setNumOfThread(params.getNumThreads());
        blast.setEvalue(params.getEvalue());
        blast.setNumberOfHis(params.getMaxTargetSeqs());
        blast.setShortQuery(params.isShortQuery());
        
        logger.info("Executing BLAST command: " + blastType.getCommand() + 
                   " query=" + queryFile.getName() + 
                   " database=" + targetDb.getDatabaseName());
        
        // Execute BLAST with proper environment setup
        try {
            // Set environment variables for BLAST execution if needed
            setupBlastEnvironment();
            
            blast.process();
        } catch (Exception e) {
            throw new Exception("BLAST execution failed: " + e.getMessage() + 
                              ". Please ensure BLAST+ is properly installed and accessible.", e);
        }
        
        // Create result object
        BlastResult result = new BlastResult(query, blastType.getCommand());
        result.setResultFile(outputFile);
        result.setTargetDatabase(new File(targetDb.getDatabasePath()));  // Store database path for sequence extraction

        // Parse results if output format is XML
        if (params.getOutputFormat() == OUTBLASTFMT.XML) {
            try {
                result.parseResultsFromXML(outputFile);
                logger.info("BLAST result parsing completed, found " + result.getHits().size() + " hits");
            } catch (Exception e) {
                logger.warning("BLAST result parsing failed: " + e.getMessage());
                result.setSuccessful(false);
                result.setErrorMessage("Result parsing failed: " + e.getMessage());
            }
        } else {
            // For non-XML formats, just mark as successful if file exists
            result.setSuccessful(outputFile.exists() && outputFile.length() > 0);
            if (!result.isSuccessful()) {
                result.setErrorMessage("BLAST output file is empty or missing");
            }
        }
        
        return result;
    }
    
    /**
     * Check if BLAST is available in the system
     */
    public boolean isBlastAvailable() {
        try {
            // Try to run a simple makeblastdb command to check availability
            ProcessBuilder pb = new ProcessBuilder(config.resolveBlastCommand("makeblastdb"), "-help");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            logger.warning("BLAST unavailable: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get BLAST version information
     */
    public String getBlastVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder(config.resolveBlastCommand("makeblastdb"), "-version");
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            process.waitFor();
            return output.toString().trim();
        } catch (Exception e) {
            logger.warning("Unable to get BLAST version information: " + e.getMessage());
            return "Unknown version";
        }
    }
    
    /**
     * Test BLAST functionality with a simple query
     */
    public boolean testBlastFunctionality() {
        try {
            // Create a simple test query
            File testQuery = File.createTempFile("blast_test_query", ".fasta");
            testQuery.deleteOnExit();
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(testQuery))) {
                writer.write(">test_sequence\n");
                writer.write("ATCGATCGATCGATCG\n");
            }
            
            // Create a simple test database
            File testDb = File.createTempFile("blast_test_db", ".fasta");
            testDb.deleteOnExit();
            
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(testDb))) {
                writer.write(">test_target\n");
                writer.write("ATCGATCGATCGATCGATCGATCG\n");
            }
            
            // Test makeblastdb
            String makeblastdbCmd = config.resolveBlastCommand("makeblastdb");
            String blastnCmd = config.resolveBlastCommand("blastn");
            ProcessBuilder makeDbPb = new ProcessBuilder(
                makeblastdbCmd, "-in", testDb.getAbsolutePath(), 
                "-dbtype", "nucl", "-out", testDb.getAbsolutePath() + "_db"
            );
            Process makeDbProcess = makeDbPb.start();
            if (makeDbProcess.waitFor() != 0) {
                return false;
            }
            
            // Test blastn
            ProcessBuilder blastPb = new ProcessBuilder(
                blastnCmd, "-query", testQuery.getAbsolutePath(),
                "-db", testDb.getAbsolutePath() + "_db",
                "-out", testQuery.getAbsolutePath() + "_result.txt"
            );
            Process blastProcess = blastPb.start();
            return blastProcess.waitFor() == 0;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "BLAST functionality test failed", e);
            return false;
        }
    }
    
    /**
     * Shutdown the executor
     */
    public void shutdown() {
        logger.info("Shutting down BLAST executor...");
        executorService.shutdown();
        
        try {
            if (!executorService.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("BLAST executor closed");
    }
    
    /**
     * BLAST type enumeration
     */
    public enum BlastType {
        BLASTN("blastn", "nucleotide vs nucleotide"),
        BLASTP("blastp", "protein vs protein"),
        BLASTX("blastx", "nucleotide vs protein (translated query)"),
        TBLASTN("tblastn", "protein vs nucleotide (translated database)"),
        TBLASTX("tblastx", "nucleotide vs nucleotide (both translated)");
        
        private final String command;
        private final String description;
        
        BlastType(String command, String description) {
            this.command = command;
            this.description = description;
        }
        
        public String getCommand() {
            return command;
        }
        
        public String getDescription() {
            return description;
        }
        
        @Override
        public String toString() {
            return command + " (" + description + ")";
        }
        
        /**
         * Get BlastType from command string
         */
        public static BlastType fromCommand(String command) {
            for (BlastType type : values()) {
                if (type.command.equalsIgnoreCase(command)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown BLAST type: " + command);
        }
    }
    
    /**
     * Setup environment for BLAST execution
     */
    private void setupBlastEnvironment() {
        String blastPath = config.getBlastExecutablePath();
        
        if (!blastPath.isEmpty()) {
            // Set system property for TBtools to find BLAST executables
            System.setProperty("blast.executable.path", blastPath);
            System.setProperty("user.dir.blast", blastPath);
            
            logger.info("Set BLAST path system properties: " + blastPath);
        }
        
        // Set encoding properties to avoid command line issues
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("sun.jnu.encoding", "UTF-8");
    }
    
    /**
     * Ensure BLAST executable is available
     */
    private void ensureBlastAvailable() throws Exception {
        String blastPath = config.getBlastExecutablePath();
        String blastCommand = config.resolveBlastCommand("blastp");
        
        if (!blastPath.isEmpty()) {
            // Check if BLAST executable exists at configured path
            File blastp = new File(blastCommand);
            
            if (!blastp.exists()) {
                throw new Exception("BLAST executable not found at configured path: " + blastp.getAbsolutePath() + 
                                  ". Please check configuration or install BLAST at this location.");
            }
            
            logger.info("Using BLAST from configured path: " + blastp.getAbsolutePath());
        } else {
            // Test if BLAST is available in system PATH
            try {
                ProcessBuilder pb = new ProcessBuilder(blastCommand, "-version");
                Process process = pb.start();
                int exitCode = process.waitFor();
                
                if (exitCode != 0) {
                    throw new Exception("BLAST test execution failed. Please ensure BLAST+ is installed and available in system PATH, bundled under "
                        + new File(ApplicationLayout.getAppHomeDirectory(), "bin\\tools").getAbsolutePath()
                        + ", or configure the BLAST path in settings.");
                }
                
                logger.info("BLAST found in system PATH");
            } catch (IOException | InterruptedException e) {
                throw new Exception("BLAST not found. Place BLAST+ executables under "
                    + new File(ApplicationLayout.getAppHomeDirectory(), "bin\\tools").getAbsolutePath()
                    + ", add them to PATH, or configure the BLAST path in settings.", e);
            }
        }
    }
}
