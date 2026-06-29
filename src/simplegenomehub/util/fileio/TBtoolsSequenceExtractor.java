/*
 * TBtools Sequence Extractor Wrapper
 * Integrates TBtools' mature GFF3/GTF processing capabilities
 */
package simplegenomehub.util.fileio;

import biocjava.bioIO.GFF.ExtractFeaturefromGFF3andGenome;
import biocjava.bioIO.GTF.ExtractFeaturefromGTFandGenome;
import biocjava.bioIO.GXF.GussorOfGtfAndGff;
import biocjava.bioDoer.GXFUtils.GXFToRepresentativeGXF;
import biocjava.bioDoer.GXFUtils.GXFfixer.GXFFix;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper class that integrates TBtools' proven sequence extraction logic
 * Supports both GFF3 and GTF formats with automatic format detection
 * Handles CDS phase, strand direction, and proper feature concatenation
 * 
 * @author SimpleGenomeHub
 */
public class TBtoolsSequenceExtractor {
    
    private static final Logger logger = Logger.getLogger(TBtoolsSequenceExtractor.class.getName());
    
    /**
     * Extraction result
     */
    public static class ExtractionResult {
        private boolean success;
        private String message;
        private int sequencesExtracted;
        private String outputFile;
        
        public ExtractionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.sequencesExtracted = 0;
        }
        
        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public int getSequencesExtracted() { return sequencesExtracted; }
        public void setSequencesExtracted(int count) { this.sequencesExtracted = count; }
        public String getOutputFile() { return outputFile; }
        public void setOutputFile(String outputFile) { this.outputFile = outputFile; }
    }
    
    /**
     * Extract representative transcripts using TBtools logic
     */
    public static ExtractionResult extractRepresentativeTranscripts(
            File genomeFile, File annotationFile, File outputFile, String criteria) {
        try {
            // Auto-detect file format
            GussorOfGtfAndGff.FileType fileType = GussorOfGtfAndGff.predictFileType(annotationFile);
            logger.info("Detected annotation format: " + fileType);
            
            // Create temporary representative annotation file
            File tempRepGff = File.createTempFile("temp_representative_transcripts", ".gff3");
            tempRepGff.deleteOnExit();
            
            // Generate representative annotation using TBtools
            GXFToRepresentativeGXF.filter(annotationFile, tempRepGff, "CDS", "(.*exon.*)|(.*UTR.*)");
            
            // Extract sequences using appropriate TBtools extractor
            StringBuffer result;
            if (fileType == GussorOfGtfAndGff.FileType.GFF3) {
                ExtractFeaturefromGFF3andGenome extractor = new ExtractFeaturefromGFF3andGenome();
                extractor.setGff3File(tempRepGff.getAbsolutePath());
                extractor.setInGenome(genomeFile.getAbsolutePath());
                extractor.setOutFeatureFile(outputFile.getAbsolutePath());
                result = extractor.process("exon", "Parent", true);
                extractor.close();
            } else {
                ExtractFeaturefromGTFandGenome extractor = new ExtractFeaturefromGTFandGenome();
                extractor.setGtfFile(tempRepGff.getAbsolutePath());
                extractor.setInGenome(genomeFile.getAbsolutePath());
                extractor.setOutFeatureFile(outputFile.getAbsolutePath());
                result = extractor.process("exon", "transcript_id", true);
                // GTF extractor doesn't have close method
            }
            
            // Parse extraction count from result
            String resultStr = result.toString();
            int extractedCount = parseExtractionCount(resultStr);
            
            ExtractionResult extractResult = new ExtractionResult(true, "Representative transcript extraction completed");
            extractResult.setSequencesExtracted(extractedCount);
            extractResult.setOutputFile(outputFile.getAbsolutePath());
            
            logger.info("Extracted " + extractedCount + " representative transcripts");
            return extractResult;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error extracting representative transcripts", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Extract representative CDS sequences using TBtools logic
     */
    public static ExtractionResult extractRepresentativeCDS(
            File genomeFile, File annotationFile, File outputFile, String criteria) {
        try {
            // Auto-detect file format
            GussorOfGtfAndGff.FileType fileType = GussorOfGtfAndGff.predictFileType(annotationFile);
            logger.info("Detected annotation format: " + fileType);
            
            // Create temporary representative annotation file
            File tempRepGff = File.createTempFile("temp_representative_cds", ".gff3");
            tempRepGff.deleteOnExit();
            
            // Generate representative annotation using TBtools
            GXFToRepresentativeGXF.filter(annotationFile, tempRepGff, "CDS");
            
            // Extract CDS sequences using appropriate TBtools extractor
            StringBuffer result;
            if (fileType == GussorOfGtfAndGff.FileType.GFF3) {
                ExtractFeaturefromGFF3andGenome extractor = new ExtractFeaturefromGFF3andGenome();
                extractor.setGff3File(tempRepGff.getAbsolutePath());
                extractor.setInGenome(genomeFile.getAbsolutePath());
                extractor.setOutFeatureFile(outputFile.getAbsolutePath());
                result = extractor.process("CDS", "Parent", true);
                extractor.close();
            } else {
                ExtractFeaturefromGTFandGenome extractor = new ExtractFeaturefromGTFandGenome();
                extractor.setGtfFile(tempRepGff.getAbsolutePath());
                extractor.setInGenome(genomeFile.getAbsolutePath());
                extractor.setOutFeatureFile(outputFile.getAbsolutePath());
                result = extractor.process("CDS", "transcript_id", true);
                // GTF extractor doesn't have close method
            }
            
            // Parse extraction count from result
            String resultStr = result.toString();
            int extractedCount = parseExtractionCount(resultStr);
            
            ExtractionResult extractResult = new ExtractionResult(true, "Representative CDS extraction completed");
            extractResult.setSequencesExtracted(extractedCount);
            extractResult.setOutputFile(outputFile.getAbsolutePath());
            
            logger.info("Extracted " + extractedCount + " representative CDS sequences");
            return extractResult;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error extracting representative CDS", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Extract representative protein sequences using TBtools logic with translation
     */
    public static ExtractionResult extractRepresentativeProteins(
            File genomeFile, File annotationFile, File outputFile, String criteria) {
        try {
            // First extract CDS sequences to temporary file
            File tempCdsFile = File.createTempFile("temp_cds_for_proteins", ".fasta");
            tempCdsFile.deleteOnExit();
            
            ExtractionResult cdsResult = extractRepresentativeCDS(genomeFile, annotationFile, tempCdsFile, criteria);
            if (!cdsResult.isSuccess()) {
                return new ExtractionResult(false, "Failed to extract CDS for protein translation: " + cdsResult.getMessage());
            }
            
            // Translate CDS to proteins
            int proteinCount = translateCDSToProteins(tempCdsFile, outputFile);
            
            ExtractionResult extractResult = new ExtractionResult(true, "Representative protein extraction completed");
            extractResult.setSequencesExtracted(proteinCount);
            extractResult.setOutputFile(outputFile.getAbsolutePath());
            
            logger.info("Translated " + proteinCount + " representative proteins");
            return extractResult;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error extracting representative proteins", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Translate CDS sequences to protein sequences
     */
    private static int translateCDSToProteins(File cdsFile, File proteinFile) throws IOException {
        // Standard genetic code table
        java.util.Map<String, String> geneticCode = createGeneticCodeTable();
        
        int proteinCount = 0;
        
        try (BufferedReader reader = new BufferedReader(new FileReader(cdsFile));
             PrintWriter writer = new PrintWriter(new FileWriter(proteinFile))) {
            
            String line;
            StringBuilder currentSeq = new StringBuilder();
            String currentHeader = null;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (line.startsWith(">")) {
                    // Process previous sequence
                    if (currentHeader != null && currentSeq.length() > 0) {
                        String protein = translateSequence(currentSeq.toString(), geneticCode);
                        if (!protein.isEmpty()) {
                            // Convert CDS header to protein header
                            String proteinHeader = currentHeader.replace("[CDS", "[protein").replace("_cds", "_protein");
                            writer.println(proteinHeader);
                            
                            // Write protein sequence in lines of 80 characters
                            for (int i = 0; i < protein.length(); i += 80) {
                                writer.println(protein.substring(i, Math.min(i + 80, protein.length())));
                            }
                            proteinCount++;
                        }
                    }
                    
                    // Start new sequence
                    currentHeader = line;
                    currentSeq = new StringBuilder();
                } else {
                    currentSeq.append(line.toUpperCase());
                }
            }
            
            // Process last sequence
            if (currentHeader != null && currentSeq.length() > 0) {
                String protein = translateSequence(currentSeq.toString(), geneticCode);
                if (!protein.isEmpty()) {
                    String proteinHeader = currentHeader.replace("[CDS", "[protein").replace("_cds", "_protein");
                    writer.println(proteinHeader);
                    
                    for (int i = 0; i < protein.length(); i += 80) {
                        writer.println(protein.substring(i, Math.min(i + 80, protein.length())));
                    }
                    proteinCount++;
                }
            }
        }
        
        return proteinCount;
    }
    
    /**
     * Translate DNA sequence to protein
     */
    private static String translateSequence(String dnaSequence, java.util.Map<String, String> geneticCode) {
        StringBuilder protein = new StringBuilder();
        
        for (int i = 0; i < dnaSequence.length() - 2; i += 3) {
            String codon = dnaSequence.substring(i, i + 3).toUpperCase();
            String aminoAcid = geneticCode.getOrDefault(codon, "X");
            protein.append(aminoAcid);
            
            // Stop at stop codon
            if ("*".equals(aminoAcid)) {
                break;
            }
        }
        
        return protein.toString();
    }
    
    /**
     * Parse extraction count from TBtools result string
     */
    private static int parseExtractionCount(String resultStr) {
        try {
            // Look for pattern like "Extract 123 sequences in total."
            if (resultStr.contains("Extract ") && resultStr.contains(" sequences in total.")) {
                String[] parts = resultStr.split("Extract ");
                if (parts.length > 1) {
                    String countPart = parts[1].split(" sequences in total.")[0].trim();
                    return Integer.parseInt(countPart);
                }
            }
        } catch (Exception e) {
            logger.warning("Could not parse extraction count from result: " + resultStr);
        }
        return 0;
    }
    
    /**
     * Create standard genetic code table
     */
    private static java.util.Map<String, String> createGeneticCodeTable() {
        java.util.Map<String, String> code = new java.util.HashMap<>();
        
        // Standard genetic code
        code.put("TTT", "F"); code.put("TTC", "F"); code.put("TTA", "L"); code.put("TTG", "L");
        code.put("TCT", "S"); code.put("TCC", "S"); code.put("TCA", "S"); code.put("TCG", "S");
        code.put("TAT", "Y"); code.put("TAC", "Y"); code.put("TAA", "*"); code.put("TAG", "*");
        code.put("TGT", "C"); code.put("TGC", "C"); code.put("TGA", "*"); code.put("TGG", "W");
        
        code.put("CTT", "L"); code.put("CTC", "L"); code.put("CTA", "L"); code.put("CTG", "L");
        code.put("CCT", "P"); code.put("CCC", "P"); code.put("CCA", "P"); code.put("CCG", "P");
        code.put("CAT", "H"); code.put("CAC", "H"); code.put("CAA", "Q"); code.put("CAG", "Q");
        code.put("CGT", "R"); code.put("CGC", "R"); code.put("CGA", "R"); code.put("CGG", "R");
        
        code.put("ATT", "I"); code.put("ATC", "I"); code.put("ATA", "I"); code.put("ATG", "M");
        code.put("ACT", "T"); code.put("ACC", "T"); code.put("ACA", "T"); code.put("ACG", "T");
        code.put("AAT", "N"); code.put("AAC", "N"); code.put("AAA", "K"); code.put("AAG", "K");
        code.put("AGT", "S"); code.put("AGC", "S"); code.put("AGA", "R"); code.put("AGG", "R");
        
        code.put("GTT", "V"); code.put("GTC", "V"); code.put("GTA", "V"); code.put("GTG", "V");
        code.put("GCT", "A"); code.put("GCC", "A"); code.put("GCA", "A"); code.put("GCG", "A");
        code.put("GAT", "D"); code.put("GAC", "D"); code.put("GAA", "E"); code.put("GAG", "E");
        code.put("GGT", "G"); code.put("GGC", "G"); code.put("GGA", "G"); code.put("GGG", "G");
        
        return code;
    }
    
    /**
     * Extract ALL transcripts using TBtools logic (not just representative)
     */
    public static ExtractionResult extractAllTranscripts(
            File genomeFile, File annotationFile, File outputFile) {
        try {
            // Auto-detect file format
            GussorOfGtfAndGff.FileType fileType = GussorOfGtfAndGff.predictFileType(annotationFile);
            logger.info("Detected annotation format: " + fileType);
            
            // Extract sequences using appropriate TBtools extractor directly (no representative filtering)
            StringBuffer result;
            if (fileType == GussorOfGtfAndGff.FileType.GFF3) {
                ExtractFeaturefromGFF3andGenome extractor = new ExtractFeaturefromGFF3andGenome();
                extractor.setGff3File(annotationFile.getAbsolutePath());
                extractor.setInGenome(genomeFile.getAbsolutePath());
                extractor.setOutFeatureFile(outputFile.getAbsolutePath());
                result = extractor.process("exon", "Parent", true);
                extractor.close();
            } else {
                ExtractFeaturefromGTFandGenome extractor = new ExtractFeaturefromGTFandGenome();
                extractor.setGtfFile(annotationFile.getAbsolutePath());
                extractor.setInGenome(genomeFile.getAbsolutePath());
                extractor.setOutFeatureFile(outputFile.getAbsolutePath());
                result = extractor.process("exon", "transcript_id", true);
                // GTF extractor doesn't have close method
            }
            
            // Parse extraction count from result
            String resultStr = result.toString();
            int extractedCount = parseExtractionCount(resultStr);
            
            ExtractionResult extractResult = new ExtractionResult(true, "All transcript extraction completed");
            extractResult.setSequencesExtracted(extractedCount);
            extractResult.setOutputFile(outputFile.getAbsolutePath());
            
            logger.info("Extracted " + extractedCount + " transcripts (all)");
            return extractResult;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error extracting all transcripts", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Extract ALL CDS sequences using TBtools logic (not just representative)
     */
    public static ExtractionResult extractAllCDS(
            File genomeFile, File annotationFile, File outputFile) {
        try {
            // Auto-detect file format
            GussorOfGtfAndGff.FileType fileType = GussorOfGtfAndGff.predictFileType(annotationFile);
            logger.info("Detected annotation format: " + fileType);
            
            // Extract CDS sequences using appropriate TBtools extractor directly (no representative filtering)
            StringBuffer result;
            if (fileType == GussorOfGtfAndGff.FileType.GFF3) {
                ExtractFeaturefromGFF3andGenome extractor = new ExtractFeaturefromGFF3andGenome();
                extractor.setGff3File(annotationFile.getAbsolutePath());
                extractor.setInGenome(genomeFile.getAbsolutePath());
                extractor.setOutFeatureFile(outputFile.getAbsolutePath());
                result = extractor.process("CDS", "Parent", true);
                extractor.close();
            } else {
                ExtractFeaturefromGTFandGenome extractor = new ExtractFeaturefromGTFandGenome();
                extractor.setGtfFile(annotationFile.getAbsolutePath());
                extractor.setInGenome(genomeFile.getAbsolutePath());
                extractor.setOutFeatureFile(outputFile.getAbsolutePath());
                result = extractor.process("CDS", "transcript_id", true);
                // GTF extractor doesn't have close method
            }
            
            // Parse extraction count from result
            String resultStr = result.toString();
            int extractedCount = parseExtractionCount(resultStr);
            
            ExtractionResult extractResult = new ExtractionResult(true, "All CDS extraction completed");
            extractResult.setSequencesExtracted(extractedCount);
            extractResult.setOutputFile(outputFile.getAbsolutePath());
            
            logger.info("Extracted " + extractedCount + " CDS sequences (all)");
            return extractResult;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error extracting all CDS", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }
    
    /**
     * Extract ALL protein sequences using TBtools logic with translation (not just representative)
     */
    public static ExtractionResult extractAllProteins(
            File genomeFile, File annotationFile, File outputFile) {
        try {
            // First extract all CDS sequences to temporary file
            File tempCdsFile = File.createTempFile("temp_all_cds_for_proteins", ".fasta");
            tempCdsFile.deleteOnExit();
            
            ExtractionResult cdsResult = extractAllCDS(genomeFile, annotationFile, tempCdsFile);
            if (!cdsResult.isSuccess()) {
                return new ExtractionResult(false, "Failed to extract CDS for protein translation: " + cdsResult.getMessage());
            }
            
            // Translate CDS to proteins
            int proteinCount = translateCDSToProteins(tempCdsFile, outputFile);
            
            ExtractionResult extractResult = new ExtractionResult(true, "All protein extraction completed");
            extractResult.setSequencesExtracted(proteinCount);
            extractResult.setOutputFile(outputFile.getAbsolutePath());
            
            logger.info("Translated " + proteinCount + " proteins (all)");
            return extractResult;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error extracting all proteins", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        }
    }

    /**
     * Extract promoter sequences (upstream regions of CDS) using TBtools
     *
     * @param genomeFile Genome FASTA file
     * @param annotationFile GFF3/GTF annotation file
     * @param outputFile Output FASTA file for promoters
     * @param upstreamLength Length of upstream region to extract (in bp)
     * @return Extraction result with statistics
     */
    public static ExtractionResult extractPromoters(File genomeFile, File annotationFile,
                                                   File outputFile, int upstreamLength) {
        try {
            logger.info("Extracting promoter sequences (upstream of CDS: " + upstreamLength + " bp)...");

            // Detect annotation file format
            GussorOfGtfAndGff.FileType fileType = GussorOfGtfAndGff.predictFileType(annotationFile);

            if (fileType == GussorOfGtfAndGff.FileType.GFF3) {
                // Use GFF3 extractor
                ExtractFeaturefromGFF3andGenome extractor = new ExtractFeaturefromGFF3andGenome();
                extractor.setGff3File(annotationFile.getAbsolutePath());
                extractor.setInGenome(genomeFile.getAbsolutePath());
                extractor.setOutFeatureFile(outputFile.getAbsolutePath());

                // SET UPSTREAM EXTRACTION
                extractor.setUpStreamBases(upstreamLength);
                extractor.setDownStreamBases(0);  // No downstream for promoters
                extractor.setOnlyUpOrDownStreamBases(true);  // Extract ONLY upstream region
                extractor.setAddUpDownN(true);  // Pad with 'N' when exceeding boundaries

                // Extract from CDS features (upstream of ATG = promoter)
                extractor.process("CDS", "Parent", true);

                int count = countSequencesInFile(outputFile);
                String message = "Extracted " + count + " promoter sequences";
                logger.info(message);

                ExtractionResult result = new ExtractionResult(true, message);
                result.setSequencesExtracted(count);
                result.setOutputFile(outputFile.getAbsolutePath());
                return result;

            } else if (fileType == GussorOfGtfAndGff.FileType.GTF) {
                // Use GTF extractor
                ExtractFeaturefromGTFandGenome extractor = new ExtractFeaturefromGTFandGenome();
                extractor.setGtfFile(annotationFile.getAbsolutePath());
                extractor.setInGenome(genomeFile.getAbsolutePath());
                extractor.setOutFeatureFile(outputFile.getAbsolutePath());

                // SET UPSTREAM EXTRACTION
                extractor.setUpStreamBases(upstreamLength);
                extractor.setDownStreamBases(0);
                extractor.setOnlyUpOrDownStreamBases(true);
                extractor.setAddUpDownN(true);  // Pad with 'N' when exceeding boundaries

                // Extract from CDS features
                extractor.process("CDS", "transcript_id", true);

                int count = countSequencesInFile(outputFile);
                String message = "Extracted " + count + " promoter sequences";
                logger.info(message);

                ExtractionResult result = new ExtractionResult(true, message);
                result.setSequencesExtracted(count);
                result.setOutputFile(outputFile.getAbsolutePath());
                return result;

            } else {
                throw new IllegalArgumentException("Unsupported annotation format: " + fileType);
            }

        } catch (Exception e) {
            String errorMsg = "Failed to extract promoters: " + e.getMessage();
            logger.log(Level.SEVERE, errorMsg, e);
            return new ExtractionResult(false, errorMsg);
        }
    }

    /**
     * Count sequences in a FASTA file
     */
    private static int countSequencesInFile(File fastaFile) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(fastaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(">")) {
                    count++;
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to count sequences in file: " + e.getMessage());
        }
        return count;
    }

    /**
     * Check if file format is supported
     */
    public static boolean isSupportedFormat(File annotationFile) {
        try {
            GussorOfGtfAndGff.FileType fileType = GussorOfGtfAndGff.predictFileType(annotationFile);
            return fileType == GussorOfGtfAndGff.FileType.GFF3 || fileType == GussorOfGtfAndGff.FileType.GTF;
        } catch (Exception e) {
            logger.warning("Could not determine file format: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get file format string for display
     */
    public static String getFileFormatString(File annotationFile) {
        try {
            GussorOfGtfAndGff.FileType fileType = GussorOfGtfAndGff.predictFileType(annotationFile);
            return fileType.toString();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * Run TBtools GXF Fix on a copied annotation file and replace it in place.
     * Only applies to GFF3 annotations; other supported formats are left unchanged.
     */
    public static ExtractionResult fixAnnotationFileInPlace(File annotationFile) {
        File tempOutput = null;

        try {
            if (annotationFile == null || !annotationFile.isFile() || !annotationFile.canRead()) {
                return new ExtractionResult(false, "Annotation file is missing or unreadable");
            }

            GussorOfGtfAndGff.FileType fileType = GussorOfGtfAndGff.predictFileType(annotationFile);
            if (fileType != GussorOfGtfAndGff.FileType.GFF3) {
                ExtractionResult skipped = new ExtractionResult(true,
                    "Skipped GXF Fix for non-GFF3 annotation: " + fileType);
                skipped.setOutputFile(annotationFile.getAbsolutePath());
                return skipped;
            }

            File parentDir = annotationFile.getParentFile();
            String fileName = annotationFile.getName();
            String suffix = fileName.toLowerCase().endsWith(".gff3") ? ".gff3" : ".gff";
            tempOutput = File.createTempFile("tbtools_gxffix_", suffix, parentDir);

            GXFFix fixer = new GXFFix();
            fixer.setInGXF(annotationFile);
            fixer.setOutGXF(tempOutput);
            fixer.process();

            if (!tempOutput.exists() || tempOutput.length() == 0L) {
                return new ExtractionResult(false, "TBtools GXF Fix produced an empty output file");
            }

            java.nio.file.Files.move(
                tempOutput.toPath(),
                annotationFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            ExtractionResult result = new ExtractionResult(true, "TBtools GXF Fix completed");
            result.setOutputFile(annotationFile.getAbsolutePath());
            return result;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error running TBtools GXF Fix", e);
            return new ExtractionResult(false, "Error: " + e.getMessage());
        } finally {
            if (tempOutput != null && tempOutput.exists()) {
                tempOutput.delete();
            }
        }
    }
}
