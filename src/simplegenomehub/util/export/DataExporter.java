/*
 * Data Export Utility
 */
package simplegenomehub.util.export;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.GenomeData;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility for exporting species data in various formats
 * Supports individual species export and batch export operations
 * 
 * @author SimpleGenomeHub
 */
public class DataExporter {
    
    private static final Logger logger = Logger.getLogger(DataExporter.class.getName());
    
    /**
     * Export formats supported
     */
    public enum ExportFormat {
        ARCHIVE,    // Complete file archive (ZIP)
        FASTA_ONLY, // Genome sequences only
        ANNOTATION_ONLY, // Annotation files only
        STATISTICS, // Statistics and metadata only
        REPORT     // Comprehensive text report
    }
    
    /**
     * Export single species data
     */
    public static boolean exportSpecies(SpeciesInfo species, File outputDir, ExportFormat format) {
        try {
            logger.info("Exporting species: " + species.getDisplayName() + " in format: " + format);
            
            switch (format) {
                case ARCHIVE:
                    return exportAsArchive(species, outputDir);
                case FASTA_ONLY:
                    return exportFastaOnly(species, outputDir);
                case ANNOTATION_ONLY:
                    return exportAnnotationOnly(species, outputDir);
                case STATISTICS:
                    return exportStatistics(species, outputDir);
                case REPORT:
                    return exportReport(species, outputDir);
                default:
                    logger.warning("Unsupported export format: " + format);
                    return false;
            }
            
        } catch (Exception e) {
            logger.severe("Export failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Export multiple species data
     */
    public static boolean exportMultipleSpecies(List<SpeciesInfo> speciesList, File outputDir, ExportFormat format) {
        try {
            logger.info("Exporting " + speciesList.size() + " species in format: " + format);
            
            boolean allSuccess = true;
            for (SpeciesInfo species : speciesList) {
                if (!exportSpecies(species, outputDir, format)) {
                    allSuccess = false;
                    logger.warning("Failed to export species: " + species.getDisplayName());
                }
            }
            
            // Create batch export summary
            if (format == ExportFormat.REPORT) {
                createBatchSummary(speciesList, outputDir);
            }
            
            return allSuccess;
            
        } catch (Exception e) {
            logger.severe("Batch export failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Export species as complete archive (ZIP)
     */
    private static boolean exportAsArchive(SpeciesInfo species, File outputDir) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File zipFile = new File(outputDir, species.getSpeciesDirectoryName() + "_" + timestamp + ".zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            
            // Add genome files
            if (species.getSequenceDir() != null && species.getSequenceDir().exists()) {
                addDirectoryToZip(zos, species.getSequenceDir(), "sequences/");
            }
            
            // Add annotation files
            if (species.getAnnotationDir() != null && species.getAnnotationDir().exists()) {
                addDirectoryToZip(zos, species.getAnnotationDir(), "annotations/");
            }
            
            // Add extracted sequences if they exist
            File extractedDir = new File(species.getSpeciesDir(), "extracted");
            if (extractedDir.exists()) {
                addDirectoryToZip(zos, extractedDir, "extracted/");
            }
            
            // Add statistics and metadata
            addMetadataToZip(zos, species);
            
            logger.info("Archive created: " + zipFile.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            logger.severe("Failed to create archive: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Export only FASTA genome files
     */
    private static boolean exportFastaOnly(SpeciesInfo species, File outputDir) {
        if (species.getSequenceDir() == null || !species.getSequenceDir().exists()) {
            logger.warning("No sequence directory found for species: " + species.getDisplayName());
            return false;
        }
        
        File speciesDir = new File(outputDir, species.getSpeciesDirectoryName());
        if (!speciesDir.exists()) {
            speciesDir.mkdirs();
        }
        
        try {
            File[] fastaFiles = species.getSequenceDir().listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".fa") || 
                name.toLowerCase().endsWith(".fasta") ||
                name.toLowerCase().endsWith(".fas"));
            
            if (fastaFiles == null || fastaFiles.length == 0) {
                logger.warning("No FASTA files found for species: " + species.getDisplayName());
                return false;
            }
            
            for (File fastaFile : fastaFiles) {
                File targetFile = new File(speciesDir, fastaFile.getName());
                Files.copy(fastaFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Exported FASTA: " + targetFile.getAbsolutePath());
            }
            
            return true;
            
        } catch (IOException e) {
            logger.severe("Failed to export FASTA files: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Export only annotation files
     */
    private static boolean exportAnnotationOnly(SpeciesInfo species, File outputDir) {
        if (species.getAnnotationDir() == null || !species.getAnnotationDir().exists()) {
            logger.warning("No annotation directory found for species: " + species.getDisplayName());
            return false;
        }
        
        File speciesDir = new File(outputDir, species.getSpeciesDirectoryName());
        if (!speciesDir.exists()) {
            speciesDir.mkdirs();
        }
        
        try {
            File[] annotationFiles = species.getAnnotationDir().listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".gff") || 
                name.toLowerCase().endsWith(".gff3") ||
                name.toLowerCase().endsWith(".gtf"));
            
            if (annotationFiles == null || annotationFiles.length == 0) {
                logger.warning("No annotation files found for species: " + species.getDisplayName());
                return false;
            }
            
            for (File annotationFile : annotationFiles) {
                File targetFile = new File(speciesDir, annotationFile.getName());
                Files.copy(annotationFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Exported annotation: " + targetFile.getAbsolutePath());
            }
            
            return true;
            
        } catch (IOException e) {
            logger.severe("Failed to export annotation files: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Export statistics and metadata only
     */
    private static boolean exportStatistics(SpeciesInfo species, File outputDir) {
        File statsFile = new File(outputDir, species.getSpeciesDirectoryName() + "_statistics.txt");
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(statsFile))) {
            
            // Basic information
            writer.println("Species Statistics Report");
            writer.println("========================");
            writer.println();
            writer.println("Species Name: " + species.getSpeciesName());
            writer.println("Version: " + species.getVersion());
            writer.println("Directory Name: " + species.getSpeciesDirectoryName());
            
            if (species.getImportTime() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                writer.println("Import Time: " + sdf.format(java.sql.Timestamp.valueOf(species.getImportTime())));
            }
            
            if (species.getNotes() != null && !species.getNotes().isEmpty()) {
                writer.println("Notes: " + species.getNotes());
            }
            
            writer.println();
            
            // File information
            writer.println("File Information:");
            writer.println("================");
            
            if (species.getSequenceDir() != null && species.getSequenceDir().exists()) {
                writer.println("Sequence Directory: " + species.getSequenceDir().getAbsolutePath());
                File[] fastaFiles = species.getSequenceDir().listFiles((dir, name) -> 
                    name.toLowerCase().endsWith(".fa") || 
                    name.toLowerCase().endsWith(".fasta") ||
                    name.toLowerCase().endsWith(".fas"));
                if (fastaFiles != null) {
                    for (File file : fastaFiles) {
                        writer.println("  - " + file.getName() + " (" + formatFileSize(file.length()) + ")");
                    }
                }
            }
            
            if (species.getAnnotationDir() != null && species.getAnnotationDir().exists()) {
                writer.println("Annotation Directory: " + species.getAnnotationDir().getAbsolutePath());
                File[] annotationFiles = species.getAnnotationDir().listFiles((dir, name) -> 
                    name.toLowerCase().endsWith(".gff") || 
                    name.toLowerCase().endsWith(".gff3") ||
                    name.toLowerCase().endsWith(".gtf"));
                if (annotationFiles != null) {
                    for (File file : annotationFiles) {
                        writer.println("  - " + file.getName() + " (" + formatFileSize(file.length()) + ")");
                    }
                }
            }
            
            writer.println();
            
            // Genome statistics
            if (species.getGenomeData() != null) {
                writer.println("Genome Statistics:");
                writer.println("=================");
                GenomeData data = species.getGenomeData();
                
                writer.println("Genome Size: " + String.format("%,d bp", data.getGenomeSize()));
                writer.println("GC Content: " + String.format("%.2f%%", data.getGcContent()));
                
                if (data.getN50() > 0) {
                    writer.println("N50: " + String.format("%,d bp", data.getN50()));
                }
                
                writer.println("Chromosome Count: " + data.getChromosomeCount());
                writer.println("Scaffold Count: " + data.getScaffoldCount());
                
                if (data.getGeneCount() > 0) {
                    writer.println();
                    writer.println("Annotation Statistics:");
                    writer.println("Gene Count: " + data.getGeneCount());
                }
                if (data.getTranscriptCount() > 0) {
                    writer.println("Transcript Count: " + data.getTranscriptCount());
                }
                if (data.getCdsCount() > 0) {
                    writer.println("CDS Count: " + data.getCdsCount());
                }
            }
            
            writer.println();
            writer.println("Report generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            
            logger.info("Statistics exported: " + statsFile.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            logger.severe("Failed to export statistics: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Export comprehensive report
     */
    private static boolean exportReport(SpeciesInfo species, File outputDir) {
        return exportStatistics(species, outputDir); // For now, same as statistics
    }
    
    /**
     * Add directory contents to ZIP archive
     */
    private static void addDirectoryToZip(ZipOutputStream zos, File dir, String basePath) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, basePath + file.getName() + "/");
            } else {
                addFileToZip(zos, file, basePath + file.getName());
            }
        }
    }
    
    /**
     * Add single file to ZIP archive
     */
    private static void addFileToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
        }
        
        zos.closeEntry();
    }
    
    /**
     * Add metadata to ZIP archive
     */
    private static void addMetadataToZip(ZipOutputStream zos, SpeciesInfo species) throws IOException {
        // Create metadata string
        StringBuilder metadata = new StringBuilder();
        metadata.append("Species: ").append(species.getSpeciesName()).append("\n");
        metadata.append("Version: ").append(species.getVersion()).append("\n");
        metadata.append("Directory: ").append(species.getSpeciesDirectoryName()).append("\n");
        
        if (species.getImportTime() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            metadata.append("Import Time: ").append(sdf.format(java.sql.Timestamp.valueOf(species.getImportTime()))).append("\n");
        }
        
        if (species.getNotes() != null && !species.getNotes().isEmpty()) {
            metadata.append("Notes: ").append(species.getNotes()).append("\n");
        }
        
        metadata.append("Export Time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        
        // Add to ZIP
        ZipEntry entry = new ZipEntry("metadata.txt");
        zos.putNextEntry(entry);
        zos.write(metadata.toString().getBytes("UTF-8"));
        zos.closeEntry();
    }
    
    /**
     * Create batch export summary
     */
    private static void createBatchSummary(List<SpeciesInfo> speciesList, File outputDir) {
        File summaryFile = new File(outputDir, "batch_export_summary.txt");
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(summaryFile))) {
            writer.println("Batch Export Summary");
            writer.println("===================");
            writer.println();
            writer.println("Export Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writer.println("Total Species: " + speciesList.size());
            writer.println();
            
            writer.println("Exported Species:");
            for (int i = 0; i < speciesList.size(); i++) {
                SpeciesInfo species = speciesList.get(i);
                writer.println(String.format("%3d. %s", i + 1, species.getDisplayName()));
            }
            
            logger.info("Batch summary created: " + summaryFile.getAbsolutePath());
            
        } catch (IOException e) {
            logger.warning("Failed to create batch summary: " + e.getMessage());
        }
    }
    
    /**
     * Format file size for display
     */
    private static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " bytes";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
}