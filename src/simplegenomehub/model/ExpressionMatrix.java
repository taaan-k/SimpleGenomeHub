/*
 * Expression Matrix Data Model
 * Integrates with TBtools table processing capabilities
 */
package simplegenomehub.model;

import biocjava.bioDoer.Table.ExpressionCorr;
import biocjava.bioDoer.Table.TableMerger;
import biocjava.bioDoer.Table.TableTransposer;
import biocjava.bioDoer.ExpressionLevelCalculator.TPMcalculator;
import biocjava.bioDoer.ExpressionLevelCalculator.RPKMcalculator;
import biocjava.bioDoer.ExpressionLevelCalculator.FPKMtoTPM;
import biocjava.bioDoer.Table.TAUCalc;
import toolsKit.FileReader.BioFileReader;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.LocalDateTime;

/**
 * Expression matrix data model that wraps TBtools table functionality
 * Supports RNA-seq expression data in TPM, FPKM, and count formats
 * 
 * @author SimpleGenomeHub
 */
public class ExpressionMatrix {
    
    private static final Logger logger = Logger.getLogger(ExpressionMatrix.class.getName());
    
    public enum DataType {
        TPM("TPM", "Transcripts Per Million"),
        FPKM("FPKM", "Fragments Per Kilobase Million"),
        COUNTS("Counts", "Raw Read Counts"),
        RPKM("RPKM", "Reads Per Kilobase Million");
        
        private final String shortName;
        private final String fullName;
        
        DataType(String shortName, String fullName) {
            this.shortName = shortName;
            this.fullName = fullName;
        }
        
        public String getShortName() { return shortName; }
        public String getFullName() { return fullName; }
    }
    
    // Core data
    private String matrixId;
    private String name;
    private DataType dataType;
    private File sourceFile;
    private LocalDateTime importTime;
    private String notes;
    
    // Matrix data - using String arrays for compatibility with TBtools
    private String[][] dataMatrix;
    private String[] geneIds;
    private String[] sampleNames;
    private Map<String, String> sampleMetadata; // sample -> metadata JSON
    
    // Statistics cache
    private double[][] correlationMatrix;
    private Map<String, Double> tissueSelectorScores; // TAU scores
    private boolean statisticsComputed = false;
    
    // Data validation
    private boolean isValid = false;
    private List<String> validationErrors = new ArrayList<>();
    
    /**
     * Constructor for new matrix
     */
    public ExpressionMatrix(String matrixId, String name, DataType dataType) {
        this.matrixId = matrixId;
        this.name = name;
        this.dataType = dataType;
        this.importTime = LocalDateTime.now();
        this.notes = "";
        this.sampleMetadata = new HashMap<>();
    }
    
    /**
     * Load matrix from file using TBtools BioFileReader with auto-delimiter detection
     */
    public boolean loadFromFile(File file) {
        this.sourceFile = file;
        validationErrors.clear();
        
        try {
            // Use TBtools BioFileReader for robust file parsing
            List<String> lines = new ArrayList<>();
            BioFileReader bio = new BioFileReader(file);
            String line;
            while ((line = bio.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
            bio.close();
            
            if (lines.isEmpty()) {
                validationErrors.add("File is empty");
                return false;
            }
            
            // Auto-detect delimiter from first line
            String delimiter = detectDelimiter(lines.get(0));
            logger.info("Detected delimiter: " + (delimiter.equals("\t") ? "TAB" : "COMMA"));
            
            // Parse header line to get sample names
            String[] headerParts = lines.get(0).split(delimiter, -1);
            if (headerParts.length < 2) {
                validationErrors.add("Invalid header format - need at least gene ID column and one sample");
                return false;
            }
            
            // First column should be gene IDs
            sampleNames = Arrays.copyOfRange(headerParts, 1, headerParts.length);
            
            // Parse data lines
            List<String> geneIdsList = new ArrayList<>();
            List<String[]> dataRows = new ArrayList<>();
            
            for (int i = 1; i < lines.size(); i++) {
                String[] parts = lines.get(i).split(delimiter, -1);
                if (parts.length != headerParts.length) {
                    validationErrors.add("Line " + (i + 1) + " has inconsistent number of columns (" + 
                                       parts.length + " vs expected " + headerParts.length + ")");
                    continue;
                }
                
                geneIdsList.add(parts[0]);
                String[] dataRow = Arrays.copyOfRange(parts, 1, parts.length);
                dataRows.add(dataRow);
            }
            
            // Convert to arrays
            geneIds = geneIdsList.toArray(new String[0]);
            dataMatrix = dataRows.toArray(new String[0][]);
            
            // Validate data values
            validateDataValues();
            
            isValid = validationErrors.isEmpty();
            
            if (isValid) {
                logger.info("Successfully loaded expression matrix: " + file.getName() + 
                          " (" + geneIds.length + " genes, " + sampleNames.length + " samples)");
            } else {
                logger.warning("Matrix validation failed: " + String.join(", ", validationErrors));
            }
            
            return isValid;
            
        } catch (IOException e) {
            validationErrors.add("IO Error: " + e.getMessage());
            logger.log(Level.SEVERE, "Failed to load expression matrix from " + file, e);
            return false;
        }
    }
    
    /**
     * Validate that all data values are numeric
     */
    private void validateDataValues() {
        for (int i = 0; i < dataMatrix.length; i++) {
            for (int j = 0; j < dataMatrix[i].length; j++) {
                String value = dataMatrix[i][j];
                if (value == null || value.trim().isEmpty()) {
                    dataMatrix[i][j] = "0"; // Replace empty with 0
                    continue;
                }
                
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    validationErrors.add("Invalid numeric value at gene " + geneIds[i] + 
                                       ", sample " + sampleNames[j] + ": " + value);
                }
            }
        }
    }
    
    /**
     * Save matrix to file
     */
    public boolean saveToFile(File file) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            // Write header
            writer.print("Gene_ID");
            for (String sample : sampleNames) {
                writer.print("\t" + sample);
            }
            writer.println();
            
            // Write data rows
            for (int i = 0; i < geneIds.length; i++) {
                writer.print(geneIds[i]);
                for (int j = 0; j < dataMatrix[i].length; j++) {
                    writer.print("\t" + dataMatrix[i][j]);
                }
                writer.println();
            }
            
            logger.info("Expression matrix saved to: " + file.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save expression matrix to " + file, e);
            return false;
        }
    }
    
    /**
     * Calculate sample correlation matrix using TBtools ExpressionCorr
     */
    public double[][] calculateCorrelationMatrix() {
        if (correlationMatrix != null) {
            return correlationMatrix;
        }
        
        try {
            // Convert matrix to double format for correlation calculation
            double[][] numericMatrix = convertToNumericMatrix();
            
            // Use TBtools ExpressionCorr for Pearson correlation
            // Create temporary files for processing
            File tempInput = File.createTempFile("expr_matrix", ".tsv");
            File tempOutput = File.createTempFile("corr_matrix", ".tsv");
            
            // Save matrix to temporary file
            saveMatrixToTempFile(tempInput, numericMatrix);
            
            ExpressionCorr corrCalculator = new ExpressionCorr();
            corrCalculator.setInFPKM(tempInput);
            corrCalculator.setOutCorrMat(tempOutput);
            corrCalculator.process();
            
            // Load correlation matrix from output file
            correlationMatrix = loadCorrelationMatrix(tempOutput);
            
            // Clean up temporary files
            tempInput.delete();
            tempOutput.delete();
            
            statisticsComputed = true;
            logger.info("Correlation matrix calculated for " + sampleNames.length + " samples");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to calculate correlation matrix", e);
            // Create identity matrix as fallback
            correlationMatrix = new double[sampleNames.length][sampleNames.length];
            for (int i = 0; i < sampleNames.length; i++) {
                correlationMatrix[i][i] = 1.0;
            }
        }
        
        return correlationMatrix;
    }
    
    /**
     * Calculate tissue specificity scores using TBtools TAUCalc
     */
    public Map<String, Double> calculateTissueSpecificity() {
        if (tissueSelectorScores != null) {
            return tissueSelectorScores;
        }
        
        tissueSelectorScores = new HashMap<>();
        
        try {
            // Use TBtools TAUCalc for tissue specificity calculation
            File tempInput = File.createTempFile("tau_input", ".tsv");
            File tempOutput = File.createTempFile("tau_output", ".tsv");
            
            // Save expression matrix to temporary file
            saveToFile(tempInput);
            
            TAUCalc tauCalc = new TAUCalc();
            tauCalc.setInExpTab(tempInput);
            tauCalc.setOutTAU(tempOutput);
            tauCalc.process();
            
            // Load TAU scores from output file
            loadTAUScores(tempOutput);
            
            // Clean up temporary files
            tempInput.delete();
            tempOutput.delete();
            
            logger.info("TAU scores calculated for " + geneIds.length + " genes");
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to calculate tissue specificity scores", e);
        }
        
        return tissueSelectorScores;
    }
    
    /**
     * Convert expression matrix to TPM using TBtools calculator
     */
    public ExpressionMatrix convertToTPM(Map<String, Integer> geneLengths) {
        if (dataType == DataType.TPM) {
            return this; // Already TPM
        }
        
        try {
            ExpressionMatrix tpmMatrix = new ExpressionMatrix(
                matrixId + "_TPM", name + " (TPM)", DataType.TPM);
            
            // Use TBtools conversion utilities
            double[][] numericMatrix = convertToNumericMatrix();
            double[][] tpmData;
            
            if (dataType == DataType.FPKM) {
                // Convert FPKM to TPM using TBtools
                File tempInput = File.createTempFile("fpkm_input", ".tsv");
                File tempOutput = File.createTempFile("tpm_output", ".tsv");
                
                // Save current matrix to temporary file
                saveToFile(tempInput);
                
                FPKMtoTPM converter = new FPKMtoTPM();
                converter.setFPKMFile(tempInput);
                converter.setOutFile(tempOutput);
                converter.getTPM();
                
                // Load TPM data from output file
                ExpressionMatrix tpmFromFile = new ExpressionMatrix("temp", "temp", DataType.TPM);
                tpmFromFile.loadFromFile(tempOutput);
                tpmData = tpmFromFile.convertToNumericMatrix();
                
                // Clean up
                tempInput.delete();
                tempOutput.delete();
                
            } else if (dataType == DataType.COUNTS) {
                // Convert counts to TPM - simplified version since we need gene lengths
                throw new UnsupportedOperationException("Count to TPM conversion requires gene length information. Please provide FPKM data instead.");
            } else {
                throw new UnsupportedOperationException("Cannot convert " + dataType + " to TPM");
            }
            
            // Set converted data
            tpmMatrix.geneIds = this.geneIds.clone();
            tpmMatrix.sampleNames = this.sampleNames.clone();
            tpmMatrix.dataMatrix = convertToStringMatrix(tpmData);
            tpmMatrix.sampleMetadata = new HashMap<>(this.sampleMetadata);
            tpmMatrix.isValid = true;
            
            logger.info("Converted " + dataType + " matrix to TPM");
            return tpmMatrix;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to convert matrix to TPM", e);
            return null;
        }
    }
    
    /**
     * Get expression values for a specific gene
     */
    public Map<String, Double> getGeneExpression(String geneId) {
        Map<String, Double> expression = new HashMap<>();
        
        int geneIndex = Arrays.asList(geneIds).indexOf(geneId);
        if (geneIndex == -1) {
            return expression; // Empty map if gene not found
        }
        
        for (int i = 0; i < sampleNames.length; i++) {
            try {
                double value = Double.parseDouble(dataMatrix[geneIndex][i]);
                expression.put(sampleNames[i], value);
            } catch (NumberFormatException e) {
                expression.put(sampleNames[i], 0.0);
            }
        }
        
        return expression;
    }
    
    /**
     * Get expression values for a specific sample
     */
    public Map<String, Double> getSampleExpression(String sampleName) {
        Map<String, Double> expression = new HashMap<>();
        
        int sampleIndex = Arrays.asList(sampleNames).indexOf(sampleName);
        if (sampleIndex == -1) {
            return expression; // Empty map if sample not found
        }
        
        for (int i = 0; i < geneIds.length; i++) {
            try {
                double value = Double.parseDouble(dataMatrix[i][sampleIndex]);
                expression.put(geneIds[i], value);
            } catch (NumberFormatException e) {
                expression.put(geneIds[i], 0.0);
            }
        }
        
        return expression;
    }
    
    /**
     * Save matrix to temporary file for TBtools processing
     */
    private void saveMatrixToTempFile(File tempFile, double[][] numericMatrix) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            // Write header
            writer.print("Gene_ID");
            for (String sample : sampleNames) {
                writer.print("\t" + sample);
            }
            writer.println();
            
            // Write data rows
            for (int i = 0; i < geneIds.length; i++) {
                writer.print(geneIds[i]);
                for (int j = 0; j < numericMatrix[i].length; j++) {
                    writer.print("\t" + numericMatrix[i][j]);
                }
                writer.println();
            }
        }
    }
    
    /**
     * Load correlation matrix from TBtools output file
     */
    private double[][] loadCorrelationMatrix(File outputFile) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(outputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        
        if (lines.size() < 2) {
            return new double[sampleNames.length][sampleNames.length];
        }
        
        // Parse correlation matrix (skip header)
        double[][] matrix = new double[sampleNames.length][sampleNames.length];
        for (int i = 1; i < lines.size() && i <= sampleNames.length; i++) {
            String[] parts = lines.get(i).split("\t");
            for (int j = 1; j < parts.length && j <= sampleNames.length; j++) {
                try {
                    matrix[i-1][j-1] = Double.parseDouble(parts[j]);
                } catch (NumberFormatException e) {
                    matrix[i-1][j-1] = (i == j) ? 1.0 : 0.0; // Identity fallback
                }
            }
        }
        
        return matrix;
    }
    
    /**
     * Load TAU scores from TBtools output file
     */
    private void loadTAUScores(File outputFile) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(outputFile))) {
            String line = reader.readLine(); // Skip header
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 2) {
                    String geneId = parts[0];
                    try {
                        double tauScore = Double.parseDouble(parts[1]);
                        tissueSelectorScores.put(geneId, tauScore);
                    } catch (NumberFormatException e) {
                        tissueSelectorScores.put(geneId, 0.0);
                    }
                }
            }
        }
    }
    
    /**
     * Auto-detect delimiter from line content
     */
    private String detectDelimiter(String line) {
        if (line == null || line.trim().isEmpty()) {
            return "\t"; // Default to tab
        }
        
        // Count tabs and commas
        int tabCount = line.length() - line.replace("\t", "").length();
        int commaCount = line.length() - line.replace(",", "").length();
        
        // Use the delimiter that appears more frequently
        if (commaCount > tabCount && commaCount > 0) {
            return ",";
        } else if (tabCount > 0) {
            return "\t";
        } else {
            // If no clear delimiter, try to detect by pattern
            // Look for common patterns like quoted strings (CSV) vs simple values (TSV)
            if (line.contains("\"")) {
                return ","; // Likely CSV with quoted values
            } else {
                return "\t"; // Default to tab
            }
        }
    }
    
    /**
     * Convert string matrix to numeric matrix for calculations
     */
    private double[][] convertToNumericMatrix() {
        double[][] numeric = new double[geneIds.length][sampleNames.length];
        
        for (int i = 0; i < geneIds.length; i++) {
            for (int j = 0; j < sampleNames.length; j++) {
                try {
                    numeric[i][j] = Double.parseDouble(dataMatrix[i][j]);
                } catch (NumberFormatException e) {
                    numeric[i][j] = 0.0;
                }
            }
        }
        
        return numeric;
    }
    
    /**
     * Convert numeric matrix back to string matrix
     */
    private String[][] convertToStringMatrix(double[][] numeric) {
        String[][] stringMatrix = new String[numeric.length][numeric[0].length];
        
        for (int i = 0; i < numeric.length; i++) {
            for (int j = 0; j < numeric[i].length; j++) {
                stringMatrix[i][j] = String.valueOf(numeric[i][j]);
            }
        }
        
        return stringMatrix;
    }
    
    // Getters and Setters
    public String getMatrixId() { return matrixId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public DataType getDataType() { return dataType; }
    public File getSourceFile() { return sourceFile; }
    public LocalDateTime getImportTime() { return importTime; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String[] getGeneIds() { return geneIds; }
    public String[] getSampleNames() { return sampleNames; }
    
    public int getGeneCount() { return geneIds != null ? geneIds.length : 0; }
    public int getSampleCount() { return sampleNames != null ? sampleNames.length : 0; }
    
    public boolean isValid() { return isValid; }
    public List<String> getValidationErrors() { return new ArrayList<>(validationErrors); }
    
    public Map<String, String> getSampleMetadata() { return sampleMetadata; }
    public void setSampleMetadata(String sampleName, String metadata) {
        sampleMetadata.put(sampleName, metadata);
    }
    
    public boolean hasStatistics() { return statisticsComputed; }
    
    @Override
    public String toString() {
        return name + " (" + dataType.getShortName() + ", " + 
               getGeneCount() + " genes, " + getSampleCount() + " samples)";
    }
}