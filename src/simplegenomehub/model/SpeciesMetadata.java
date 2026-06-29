/*
 * Species Metadata Management
 */
package simplegenomehub.model;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Manages species metadata persistence using JSON format
 * Handles reading/writing species.info files for metadata storage
 * 
 * @author SimpleGenomeHub
 */
public class SpeciesMetadata {
    
    private static final Logger logger = Logger.getLogger(SpeciesMetadata.class.getName());
    private static final String METADATA_FILENAME = "species.info";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    private String speciesName;
    private String version;
    private LocalDateTime importTime;
    private String notes;
    private LocalDateTime lastModified;
    
    /**
     * Constructor
     */
    public SpeciesMetadata(String speciesName, String version) {
        this.speciesName = speciesName;
        this.version = version;
        this.importTime = LocalDateTime.now();
        this.notes = "";
        this.lastModified = LocalDateTime.now();
    }
    
    /**
     * Create metadata from SpeciesInfo
     */
    public static SpeciesMetadata fromSpeciesInfo(SpeciesInfo species) {
        SpeciesMetadata metadata = new SpeciesMetadata(
            species.getSpeciesName(), 
            species.getVersion()
        );
        metadata.setImportTime(species.getImportTime());
        metadata.setNotes(species.getNotes());
        return metadata;
    }
    
    /**
     * Apply metadata to SpeciesInfo
     */
    public void applyToSpeciesInfo(SpeciesInfo species) {
        if (species.getSpeciesName().equals(this.speciesName) && 
            species.getVersion().equals(this.version)) {
            species.setImportTime(this.importTime);
            species.setNotes(this.notes);
        }
    }
    
    /**
     * Save metadata to species directory
     */
    public boolean saveToFile(File speciesDir) {
        if (speciesDir == null || !speciesDir.exists()) {
            logger.warning("Cannot save metadata: species directory does not exist");
            return false;
        }
        
        File metadataFile = new File(speciesDir, METADATA_FILENAME);
        this.lastModified = LocalDateTime.now();
        
        try (PrintWriter writer = new PrintWriter(new FileWriter(metadataFile, java.nio.charset.StandardCharsets.UTF_8))) {
            writer.println("{");
            writer.println("  \"speciesName\": \"" + escapeJson(speciesName) + "\",");
            writer.println("  \"version\": \"" + escapeJson(version) + "\",");
            writer.println("  \"importTime\": \"" + (importTime != null ? importTime.format(DATE_FORMATTER) : "") + "\",");
            writer.println("  \"notes\": \"" + escapeJson(notes != null ? notes : "") + "\",");
            writer.println("  \"lastModified\": \"" + lastModified.format(DATE_FORMATTER) + "\"");
            writer.println("}");
            
            logger.info("Saved metadata for " + speciesName + "." + version);
            return true;
            
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save metadata for " + speciesName + "." + version, e);
            return false;
        }
    }
    
    /**
     * Load metadata from species directory
     */
    public static SpeciesMetadata loadFromFile(File speciesDir) {
        if (speciesDir == null || !speciesDir.exists()) {
            return null;
        }
        
        File metadataFile = new File(speciesDir, METADATA_FILENAME);
        if (!metadataFile.exists()) {
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(metadataFile, java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            
            return parseJsonMetadata(content.toString());
            
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to load metadata from " + metadataFile.getAbsolutePath(), e);
            return null;
        }
    }
    
    /**
     * Parse JSON metadata (simple JSON parsing without external dependencies)
     */
    private static SpeciesMetadata parseJsonMetadata(String jsonContent) {
        try {
            String speciesName = extractJsonValue(jsonContent, "speciesName");
            String version = extractJsonValue(jsonContent, "version");
            String importTimeStr = extractJsonValue(jsonContent, "importTime");
            String notes = extractJsonValue(jsonContent, "notes");
            String lastModifiedStr = extractJsonValue(jsonContent, "lastModified");
            
            if (speciesName == null || version == null) {
                logger.warning("Invalid metadata format: missing required fields");
                return null;
            }
            
            SpeciesMetadata metadata = new SpeciesMetadata(speciesName, version);
            
            if (importTimeStr != null && !importTimeStr.isEmpty()) {
                try {
                    metadata.setImportTime(LocalDateTime.parse(importTimeStr, DATE_FORMATTER));
                } catch (Exception e) {
                    logger.warning("Failed to parse import time: " + importTimeStr);
                }
            }
            
            metadata.setNotes(notes != null ? notes : "");
            
            if (lastModifiedStr != null && !lastModifiedStr.isEmpty()) {
                try {
                    metadata.setLastModified(LocalDateTime.parse(lastModifiedStr, DATE_FORMATTER));
                } catch (Exception e) {
                    logger.warning("Failed to parse last modified time: " + lastModifiedStr);
                }
            }
            
            return metadata;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to parse JSON metadata", e);
            return null;
        }
    }
    
    /**
     * Extract value from JSON string (simple implementation)
     */
    private static String extractJsonValue(String json, String key) {
        // Find the key with quotes
        String keyPattern = "\"" + key + "\"";
        int keyIndex = json.indexOf(keyPattern);
        if (keyIndex == -1) {
            return null;
        }
        
        // Find the colon after the key
        int colonIndex = json.indexOf(":", keyIndex + keyPattern.length());
        if (colonIndex == -1) {
            return null;
        }
        
        // Skip whitespace and find the opening quote
        int startIndex = colonIndex + 1;
        while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
            startIndex++;
        }
        
        if (startIndex >= json.length() || json.charAt(startIndex) != '"') {
            return null;
        }
        
        startIndex++; // Skip the opening quote
        
        // Find the closing quote (handle escaped quotes)
        int endIndex = startIndex;
        while (endIndex < json.length()) {
            if (json.charAt(endIndex) == '"') {
                // Check if it's escaped
                if (endIndex == startIndex || json.charAt(endIndex - 1) != '\\') {
                    break;
                }
            }
            endIndex++;
        }
        
        if (endIndex >= json.length()) {
            return null;
        }
        
        return unescapeJson(json.substring(startIndex, endIndex));
    }
    
    /**
     * Escape JSON string
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }
    
    /**
     * Unescape JSON string
     */
    private static String unescapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\\"", "\"")
                 .replace("\\\\", "\\")
                 .replace("\\n", "\n")
                 .replace("\\r", "\r")
                 .replace("\\t", "\t");
    }
    
    /**
     * Check if metadata file exists for species directory
     */
    public static boolean hasMetadataFile(File speciesDir) {
        if (speciesDir == null || !speciesDir.exists()) {
            return false;
        }
        File metadataFile = new File(speciesDir, METADATA_FILENAME);
        return metadataFile.exists() && metadataFile.canRead();
    }
    
    // Getters and Setters
    public String getSpeciesName() {
        return speciesName;
    }
    
    public void setSpeciesName(String speciesName) {
        this.speciesName = speciesName;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public LocalDateTime getImportTime() {
        return importTime;
    }
    
    public void setImportTime(LocalDateTime importTime) {
        this.importTime = importTime;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
        this.lastModified = LocalDateTime.now();
    }
    
    public LocalDateTime getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }
    
    @Override
    public String toString() {
        return String.format("SpeciesMetadata{name='%s', version='%s', notes='%s'}", 
            speciesName, version, notes);
    }
}