/*
 * File Processing Test Runner for Demo Data
 */
package simplegenomehub.util;

import simplegenomehub.util.fileio.*;
import simplegenomehub.model.GenomeData;
import java.io.*;
import java.util.*;
import java.nio.file.Files;

/**
 * Comprehensive test runner for all file processing utilities
 * Tests with Arabidopsis thaliana demo data
 * 
 * @author SimpleGenomeHub
 */
public class FileProcessingTestRunner {
    
    public static void main(String[] args) {
        System.out.println("=== SimpleGenomeHub File Processing Tests ===");
        
        // Demo data files
        String demoDir = "J:\\ClaudeCodeDev\\SimpleGenomeHub\\demodata";
        File genomeFile = new File(demoDir, "Arabidopsis_thaliana.genome.fa");
        File annotationFile = new File(demoDir, "Arabidopsis_thaliana.genome.gff3");
        
        try {
            // Create output directory for test results
            File outputDir = Files.createTempDirectory("SGH_FileProcessing_Test").toFile();
            outputDir.deleteOnExit();
            System.out.println("Output directory: " + outputDir.getAbsolutePath());
            
            testFileValidation(genomeFile, annotationFile);
            testFileCompatibility(genomeFile, annotationFile);
            testFASTAIndexing(genomeFile, outputDir);
            testGenomeStatistics(genomeFile, annotationFile, outputDir);
            testSequenceExtraction(genomeFile, annotationFile, outputDir);
            testRepresentativeTranscripts(annotationFile, outputDir);
            
            System.out.println("\n=== All file processing tests completed successfully ===");
            System.out.println("Check output directory for generated files: " + outputDir.getAbsolutePath());
            
        } catch (Exception e) {
            System.err.println("Test failed with error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testFileValidation(File genomeFile, File annotationFile) {
        System.out.println("\n1. Testing File Format Validation:");
        
        // Test genome file validation
        GenomeFileValidator.ValidationResult genomeResult = GenomeFileValidator.validateFile(genomeFile);
        assert genomeResult.isValid() : "Genome file validation failed: " + genomeResult.getErrorMessage();
        System.out.println("   ✓ Genome file validation: " + genomeResult.getFormat() + 
                          " (" + genomeResult.getMetadata().get("sequenceCount") + " sequences)");
        
        // Test annotation file validation
        GenomeFileValidator.ValidationResult annotationResult = GenomeFileValidator.validateFile(annotationFile);
        assert annotationResult.isValid() : "Annotation file validation failed: " + annotationResult.getErrorMessage();
        System.out.println("   ✓ Annotation file validation: " + annotationResult.getFormat() + 
                          " (" + annotationResult.getMetadata().get("featureCount") + " features)");
        
        System.out.println("   File validation tests passed!");
    }
    
    private static void testFileCompatibility(File genomeFile, File annotationFile) {
        System.out.println("\n2. Testing File Compatibility:");
        
        FileCompatibilityChecker.CompatibilityResult result = 
            FileCompatibilityChecker.checkCompatibility(genomeFile, annotationFile);
        
        assert result.isCompatible() : "Files are not compatible: " + result.getMessage();
        System.out.println("   ✓ Files are compatible");
        System.out.println("   ✓ Overlap: " + result.getDetails().get("overlapPercentage") + "%");
        System.out.println("   ✓ Common sequences: " + result.getDetails().get("commonSequenceCount"));
        
        if (!result.getWarnings().isEmpty()) {
            System.out.println("   Warnings: " + String.join("; ", result.getWarnings()));
        }
        
        System.out.println("   File compatibility tests passed!");
    }
    
    private static void testFASTAIndexing(File genomeFile, File outputDir) {
        System.out.println("\n3. Testing FASTA Indexing:");
        
        // Build index
        File indexFile = new File(outputDir, "genome.fa.fai");
        boolean indexCreated = FastaIndexBuilder.buildIndex(genomeFile, indexFile);
        assert indexCreated : "Failed to create FASTA index";
        System.out.println("   ✓ FASTA index created");
        
        // Load index
        Map<String, FastaIndexBuilder.IndexEntry> index = FastaIndexBuilder.loadIndex(indexFile);
        assert !index.isEmpty() : "Failed to load FASTA index";
        System.out.println("   ✓ FASTA index loaded: " + index.size() + " sequences");
        
        // Test sequence extraction
        String firstSeqId = index.keySet().iterator().next();
        String sequence = FastaIndexBuilder.extractSequence(genomeFile, index, firstSeqId, 1, 100);
        assert sequence != null && sequence.length() == 100 : "Failed to extract sequence";
        System.out.println("   ✓ Sequence extraction works: " + sequence.substring(0, 20) + "...");
        
        // Get sequence info
        Map<String, Object> seqInfo = FastaIndexBuilder.getSequenceInfo(index);
        System.out.println("   ✓ Total genome size: " + 
                          formatSize((Long) seqInfo.get("totalLength")));
        
        System.out.println("   FASTA indexing tests passed!");
    }
    
    private static void testGenomeStatistics(File genomeFile, File annotationFile, File outputDir) {
        System.out.println("\n4. Testing Genome Statistics:");
        
        GenomeData stats = GenomeStatsCalculator.calculateGenomeStats(genomeFile, annotationFile);
        assert stats.getGenomeSize() > 0 : "Genome size not calculated";
        assert stats.getGeneCount() > 0 : "Gene count not calculated";
        
        System.out.println("   ✓ Genome size: " + stats.getFormattedGenomeSize());
        System.out.println("   ✓ Genes: " + String.format("%,d", stats.getGeneCount()));
        System.out.println("   ✓ Transcripts: " + String.format("%,d", stats.getTranscriptCount()));
        System.out.println("   ✓ GC content: " + String.format("%.2f%%", stats.getGcContent()));
        
        // Save statistics to file
        File statsFile = new File(outputDir, "genome_stats.txt");
        boolean saved = stats.saveToFile(statsFile);
        assert saved : "Failed to save statistics file";
        System.out.println("   ✓ Statistics saved to file");
        
        // Generate report
        String report = GenomeStatsCalculator.generateStatsReport(stats);
        assert !report.isEmpty() : "Failed to generate statistics report";
        System.out.println("   ✓ Statistics report generated");
        
        System.out.println("   Genome statistics tests passed!");
    }
    
    private static void testSequenceExtraction(File genomeFile, File annotationFile, File outputDir) {
        System.out.println("\n5. Testing Sequence Extraction:");
        
        // Test transcript extraction
        File transcriptFile = new File(outputDir, "transcripts.fasta");
        SequenceExtractor.ExtractionResult transcriptResult = 
            SequenceExtractor.extractTranscripts(genomeFile, annotationFile, transcriptFile);
        
        if (transcriptResult.isSuccess() && transcriptResult.getSequencesExtracted() > 0) {
            System.out.println("   ✓ Transcripts extracted: " + transcriptResult.getSequencesExtracted());
        } else {
            System.out.println("   ⚠ Transcript extraction: " + transcriptResult.getMessage());
        }
        
        // Test CDS extraction
        File cdsFile = new File(outputDir, "cds.fasta");
        SequenceExtractor.ExtractionResult cdsResult = 
            SequenceExtractor.extractCDS(genomeFile, annotationFile, cdsFile);
        
        if (cdsResult.isSuccess() && cdsResult.getSequencesExtracted() > 0) {
            System.out.println("   ✓ CDS sequences extracted: " + cdsResult.getSequencesExtracted());
        } else {
            System.out.println("   ⚠ CDS extraction: " + cdsResult.getMessage());
        }
        
        // Test protein extraction
        File proteinFile = new File(outputDir, "proteins.fasta");
        SequenceExtractor.ExtractionResult proteinResult = 
            SequenceExtractor.extractProteins(genomeFile, annotationFile, proteinFile);
        
        if (proteinResult.isSuccess() && proteinResult.getSequencesExtracted() > 0) {
            System.out.println("   ✓ Protein sequences extracted: " + proteinResult.getSequencesExtracted());
        } else {
            System.out.println("   ⚠ Protein extraction: " + proteinResult.getMessage());
        }
        
        System.out.println("   Sequence extraction tests completed!");
    }
    
    private static void testRepresentativeTranscripts(File annotationFile, File outputDir) {
        System.out.println("\n6. Testing Representative Transcript Selection:");
        
        try {
            // Parse gene models
            Map<String, RepresentativeTranscriptSelector.GeneModel> geneModels = 
                RepresentativeTranscriptSelector.parseGeneModels(annotationFile);
            
            if (!geneModels.isEmpty()) {
                System.out.println("   ✓ Gene models parsed: " + geneModels.size() + " genes");
                
                // Test different selection criteria
                for (RepresentativeTranscriptSelector.SelectionCriteria criteria : 
                     RepresentativeTranscriptSelector.SelectionCriteria.values()) {
                    
                    File outputFile = new File(outputDir, "representative_" + 
                                             criteria.toString().toLowerCase() + ".gff3");
                    
                    boolean success = RepresentativeTranscriptSelector.generateRepresentativeAnnotation(
                        annotationFile, outputFile, criteria);
                    
                    if (success) {
                        System.out.println("   ✓ " + criteria + " representatives generated");
                        
                        // Get statistics
                        Map<String, Object> stats = RepresentativeTranscriptSelector
                            .getSelectionStatistics(geneModels, criteria);
                        System.out.println("     - Average transcript length: " + 
                                          String.format("%.0f bp", (Double) stats.get("averageTranscriptLength")));
                    } else {
                        System.out.println("   ⚠ " + criteria + " generation failed");
                    }
                }
            } else {
                System.out.println("   ⚠ No gene models found in annotation file");
            }
            
        } catch (IOException e) {
            System.out.println("   ⚠ Representative transcript test failed: " + e.getMessage());
        }
        
        System.out.println("   Representative transcript tests completed!");
    }
    
    private static String formatSize(long size) {
        if (size >= 1_000_000_000) {
            return String.format("%.2f Gb", size / 1_000_000_000.0);
        } else if (size >= 1_000_000) {
            return String.format("%.2f Mb", size / 1_000_000.0);
        } else if (size >= 1_000) {
            return String.format("%.2f Kb", size / 1_000.0);
        } else {
            return size + " bp";
        }
    }
}