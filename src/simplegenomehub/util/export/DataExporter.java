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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.zip.Deflater;
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

    /** BLAST indexes are rebuilt from the sequence files, so they are not archived. */
    private static final String BLAST_DB_DIR = "BlastDB";

    /** Manifest written into every complete archive describing its contents. */
    public static final String EXPORT_MANIFEST_NAME = "EXPORT_MANIFEST.txt";
    
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
        return exportSpecies(species, outputDir, format, null);
    }

    /**
     * Export single species data, reporting progress to the given listener.
     *
     * @param listener optional consumer of human-readable progress lines
     */
    public static boolean exportSpecies(SpeciesInfo species, File outputDir, ExportFormat format,
                                        Consumer<String> listener) {
        try {
            logger.info("Exporting species: " + species.getDisplayName() + " in format: " + format);
            
            switch (format) {
                case ARCHIVE:
                    return exportAsArchive(species, outputDir, listener);
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
        return exportMultipleSpecies(speciesList, outputDir, format, null);
    }

    /**
     * Export multiple species data, reporting progress to the given listener.
     */
    public static boolean exportMultipleSpecies(List<SpeciesInfo> speciesList, File outputDir,
                                                ExportFormat format, Consumer<String> listener) {
        try {
            logger.info("Exporting " + speciesList.size() + " species in format: " + format);
            
            boolean allSuccess = true;
            for (SpeciesInfo species : speciesList) {
                if (!exportSpecies(species, outputDir, format, listener)) {
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
     * Export the whole genome data unit as a ZIP archive.
     * <p>
     * The archive mirrors the data unit exactly: every top-level entry of the species
     * directory is copied under its own name, so a restore is a plain extraction.
     * That includes the population results ({@code Population/VCF} datasets and their
     * statistics sidecars, {@code Population/FST} run folders with {@code run.names.tsv}
     * and {@code run.info.txt}), the analysis folders ({@code GenomeAnalysis},
     * {@code FunctionalAnnotation}, {@code Expression}, {@code GeneSet}), extracted
     * sequences and the unit metadata files ({@code species.info}, {@code stat.txt}).
     * {@code BlastDB} holds only regenerable BLAST indexes and is left out by default.
     * <p>
     * Data units can be many gigabytes, so the log reports the entry count and skipped
     * files as the archive is written.
     */
    private static boolean exportAsArchive(SpeciesInfo species, File outputDir,
                                           Consumer<String> listener) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File zipFile = new File(outputDir, species.getSpeciesDirectoryName() + "_" + timestamp + ".zip");
        File speciesDir = species.getSpeciesDir();

        if (speciesDir == null || !speciesDir.isDirectory()) {
            logger.severe("Cannot archive: the data unit directory is missing for "
                + species.getDisplayName());
            report(listener, "ERROR: the data unit directory is missing for "
                + species.getDisplayName());
            return false;
        }

        ArchiveStats stats = new ArchiveStats();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.setLevel(Deflater.DEFAULT_COMPRESSION);

            File[] entries = speciesDir.listFiles();
            if (entries == null) {
                logger.severe("Cannot list the data unit directory: " + speciesDir.getAbsolutePath());
                report(listener, "ERROR: cannot list " + speciesDir.getAbsolutePath());
                return false;
            }
            Arrays.sort(entries, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

            for (File entry : entries) {
                if (!isExportable(entry)) {
                    stats.skipped.add(entry.getName() + " (regenerable / temporary)");
                    logger.info("Skipped from archive: " + entry.getName());
                    report(listener, "  skipped " + entry.getName() + " (regenerable / temporary)");
                    continue;
                }
                String label = entry.isDirectory() ? entry.getName() + "/" : entry.getName();
                report(listener, "  adding " + label);
                if (entry.isDirectory()) {
                    addDirectoryToZip(zos, entry, entry.getName() + "/", stats);
                } else {
                    addFileToZip(zos, entry, entry.getName(), stats);
                }
            }

            addExportManifestToZip(zos, species, stats);

            logger.info("Archive created: " + zipFile.getAbsolutePath()
                + " (" + stats.files + " files, " + formatFileSize(stats.bytes) + ")");
            report(listener, "Archive written: " + zipFile.getName()
                + " (" + stats.files + " files, " + formatFileSize(stats.bytes) + ")");
            return true;

        } catch (IOException e) {
            logger.severe("Failed to create archive: " + e.getMessage());
            report(listener, "ERROR: archive failed: " + e.getMessage());
            deleteQuietly(zipFile);
            return false;
        }
    }

    /** Reports an optional progress line, ignoring listener failures. */
    private static void report(Consumer<String> listener, String message) {
        if (listener != null) {
            try {
                listener.accept(message);
            } catch (RuntimeException ignored) {
                // A broken listener must never abort the export itself.
            }
        }
    }

    /**
     * Whether a top-level data unit entry belongs in the archive.
     * <p>
     * {@code BlastDB} is excluded because it only holds BLAST indexes that the application
     * rebuilds from the sequence files. Hidden directories and VCF import staging files are
     * excluded too: those are in-flight or abandoned transfers, not datasets.
     */
    private static boolean isExportable(File entry) {
        String name = entry.getName();
        if (name.startsWith(".")) {
            return false;
        }
        if (entry.isDirectory() && name.equalsIgnoreCase(BLAST_DB_DIR)) {
            return false;
        }
        return !isImportStagingFile(name);
    }

    /** Staged VCF payloads are named like {@code .data.vcf.gz.<uuid>.part}. */
    private static boolean isImportStagingFile(String name) {
        return name.contains(".part") && (name.startsWith(".") || name.endsWith(".part"));
    }

    /** Counters accumulated while writing one archive. */
    private static final class ArchiveStats {
        long files;
        long bytes;
        final List<String> skipped = new ArrayList<>();
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
    private static void addDirectoryToZip(ZipOutputStream zos, File dir, String basePath,
                                          ArchiveStats stats) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        // Stable order keeps repeated exports of the same data unit comparable.
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File file : files) {
            if (file.isDirectory()) {
                addDirectoryToZip(zos, file, basePath + file.getName() + "/", stats);
            } else if (isImportStagingFile(file.getName())) {
                stats.skipped.add(basePath + file.getName() + " (import staging)");
            } else if (file.isFile()) {
                addFileToZip(zos, file, basePath + file.getName(), stats);
            }
        }
    }

    /**
     * Add single file to ZIP archive
     */
    private static void addFileToZip(ZipOutputStream zos, File file, String entryName,
                                     ArchiveStats stats) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        // Preserve the modification time so restored results keep their run dates.
        entry.setTime(file.lastModified());
        zos.putNextEntry(entry);

        long written = 0;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
                written += length;
            }
        }

        zos.closeEntry();
        stats.files++;
        stats.bytes += written;
    }

    /**
     * Write a manifest listing what the archive contains, so a recipient can tell whether
     * population results and other analysis folders made it into the export.
     */
    private static void addExportManifestToZip(ZipOutputStream zos, SpeciesInfo species,
                                               ArchiveStats stats) throws IOException {
        StringBuilder manifest = new StringBuilder();
        manifest.append("SimpleGenomeHub Data Unit Archive\n");
        manifest.append("================================\n\n");
        manifest.append("Species: ").append(species.getSpeciesName()).append('\n');
        manifest.append("Version: ").append(species.getVersion()).append('\n');
        manifest.append("Directory: ").append(species.getSpeciesDirectoryName()).append('\n');
        manifest.append("Export Time: ")
            .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append('\n');
        manifest.append("Files: ").append(stats.files).append('\n');
        manifest.append("Uncompressed Size: ").append(formatFileSize(stats.bytes)).append('\n');
        manifest.append('\n');
        manifest.append("The archive mirrors the data unit directory, so restoring it is a\n");
        manifest.append("plain extraction into the data root directory.\n\n");

        File speciesDir = species.getSpeciesDir();
        manifest.append("Included Top-Level Entries:\n");
        File[] entries = speciesDir.listFiles();
        if (entries != null) {
            Arrays.sort(entries, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File entry : entries) {
                if (isExportable(entry)) {
                    manifest.append("  - ").append(entry.getName())
                        .append(entry.isDirectory() ? "/" : "").append('\n');
                }
            }
        }

        if (!stats.skipped.isEmpty()) {
            manifest.append("\nExcluded (regenerable or temporary):\n");
            for (String skipped : stats.skipped) {
                manifest.append("  - ").append(skipped).append('\n');
            }
        }

        manifest.append("\nPopulation results included: ")
            .append(species.hasPopulationData() ? "yes" : "no population VCF imported").append('\n');

        ZipEntry entry = new ZipEntry(EXPORT_MANIFEST_NAME);
        entry.setTime(System.currentTimeMillis());
        zos.putNextEntry(entry);
        zos.write(manifest.toString().getBytes("UTF-8"));
        zos.closeEntry();
    }

    /** Deletes a partially written archive so a failed export leaves no broken file behind. */
    private static void deleteQuietly(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            logger.warning("Could not remove the incomplete archive: " + file.getAbsolutePath());
        }
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