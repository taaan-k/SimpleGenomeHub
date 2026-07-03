/*
 * File Reader Utility with Automatic Gzip Support
 */
package simplegenomehub.util.fileio;

import java.io.*;
import java.util.zip.GZIPInputStream;

/**
 * Utility class for reading files with automatic gzip decompression support
 * Automatically detects .gz files and decompresses them on-the-fly
 *
 * @author SimpleGenomeHub
 */
public class FileReaderUtil {

    /**
     * Create a BufferedReader for the given file with automatic gzip support
     * If the file ends with .gz, it will be automatically decompressed
     *
     * @param file The file to read
     * @return BufferedReader for reading the file
     * @throws IOException If an I/O error occurs
     */
    public static BufferedReader createBufferedReader(File file) throws IOException {
        return createBufferedReader(file, 8192); // Default buffer size
    }

    /**
     * Create a BufferedReader for the given file with automatic gzip support
     * and custom buffer size
     *
     * @param file The file to read
     * @param bufferSize The buffer size to use
     * @return BufferedReader for reading the file
     * @throws IOException If an I/O error occurs
     */
    public static BufferedReader createBufferedReader(File file, int bufferSize) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }

        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + file.getAbsolutePath());
        }

        if (!file.canRead()) {
            throw new IOException("File cannot be read: " + file.getAbsolutePath());
        }

        // Check if file is gzip compressed
        if (isGzipFile(file)) {
            // Create reader with gzip decompression
            InputStream fileStream = new FileInputStream(file);
            GZIPInputStream gzipStream = new GZIPInputStream(fileStream);
            InputStreamReader inputStreamReader = new InputStreamReader(gzipStream);
            return new BufferedReader(inputStreamReader, bufferSize);
        } else {
            // Create normal file reader
            FileReader fileReader = new FileReader(file);
            return new BufferedReader(fileReader, bufferSize);
        }
    }

    /**
     * Check if a file is gzip compressed based on file extension
     *
     * @param file The file to check
     * @return true if the file ends with .gz or .gzip (case-insensitive)
     */
    public static boolean isGzipFile(File file) {
        if (file == null) {
            return false;
        }

        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".gz") || fileName.endsWith(".gzip");
    }

    /**
     * Get the original filename without .gz extension
     * For example: "genome.fasta.gz" -> "genome.fasta"
     *
     * @param file The file
     * @return The filename without .gz extension
     */
    public static String getOriginalFileName(File file) {
        if (file == null) {
            return null;
        }

        String fileName = file.getName();

        if (fileName.toLowerCase().endsWith(".gz")) {
            return fileName.substring(0, fileName.length() - 3);
        } else if (fileName.toLowerCase().endsWith(".gzip")) {
            return fileName.substring(0, fileName.length() - 5);
        }

        return fileName;
    }

    /**
     * Get the file extension from the original filename (ignoring .gz)
     * For example: "genome.fasta.gz" -> "fasta"
     *
     * @param file The file
     * @return The file extension without the dot, or empty string if no extension
     */
    public static String getOriginalFileExtension(File file) {
        String originalName = getOriginalFileName(file);

        if (originalName == null || !originalName.contains(".")) {
            return "";
        }

        int lastDot = originalName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < originalName.length() - 1) {
            return originalName.substring(lastDot + 1);
        }

        return "";
    }
}
