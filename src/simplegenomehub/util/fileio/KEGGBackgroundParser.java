package simplegenomehub.util.fileio;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Parser for KEGG background/reference files
 * Extracts detailed information for K numbers including descriptions, pathways, and hierarchies
 */
public class KEGGBackgroundParser {
    
    private static final Logger logger = Logger.getLogger(KEGGBackgroundParser.class.getName());
    
    /**
     * KEGG term information
     */
    public static class KEGGTerm {
        private String kNumber;
        private String description;
        private Set<String> pathways;
        private Set<String> hierarchies;
        
        public KEGGTerm(String kNumber) {
            this.kNumber = kNumber;
            this.pathways = new HashSet<>();
            this.hierarchies = new HashSet<>();
        }
        
        // Getters and setters
        public String getKNumber() { return kNumber; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Set<String> getPathways() { return pathways; }
        public void addPathway(String pathway) { this.pathways.add(pathway); }
        
        public Set<String> getHierarchies() { return hierarchies; }
        public void addHierarchy(String hierarchy) { this.hierarchies.add(hierarchy); }
        
        public String getPathwaysString() {
            return String.join("; ", pathways);
        }
        
        public String getHierarchiesString() {
            return String.join("; ", hierarchies);
        }
        
        @Override
        public String toString() {
            return String.format("KEGGTerm{kNumber='%s', description='%s', pathways=%d, hierarchies=%d}",
                    kNumber, description, pathways.size(), hierarchies.size());
        }
    }
    
    /**
     * Parse KEGG background file and return map of K numbers to term information
     */
    public static Map<String, KEGGTerm> parseKEGGBackground(File keggBackgroundFile) throws IOException {
        Map<String, KEGGTerm> keggTerms = new HashMap<>();
        
        if (!keggBackgroundFile.exists()) {
            throw new IOException("KEGG background file not found: " + keggBackgroundFile.getAbsolutePath());
        }
        
        logger.info("Parsing KEGG background file: " + keggBackgroundFile.getName());
        
        try (BufferedReader reader = new BufferedReader(new FileReader(keggBackgroundFile))) {
            String line;
            int lineCount = 0;
            int validEntries = 0;
            
            while ((line = reader.readLine()) != null) {
                lineCount++;
                
                // Skip empty lines and comments
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                // Split by tab
                String[] parts = line.split("\\t", -1);
                if (parts.length >= 3) {
                    String kNumber = parts[0].trim();
                    String description = parts[1].trim();
                    String pathway = parts.length > 2 ? parts[2].trim() : "";
                    String hierarchyB = parts.length > 3 ? parts[3].trim() : "";
                    String hierarchyA = parts.length > 4 ? parts[4].trim() : "";
                    
                    if (kNumber.isEmpty()) {
                        continue;
                    }
                    
                    // Get or create KEGGTerm
                    KEGGTerm term = keggTerms.get(kNumber);
                    if (term == null) {
                        term = new KEGGTerm(kNumber);
                        keggTerms.put(kNumber, term);
                    }
                    
                    // Set description (use first non-empty description)
                    if (term.getDescription() == null && !description.isEmpty()) {
                        term.setDescription(description);
                    }
                    
                    // Add pathway information
                    if (!pathway.isEmpty()) {
                        term.addPathway(pathway);
                    }
                    
                    // Add hierarchy information
                    if (!hierarchyB.isEmpty()) {
                        term.addHierarchy(hierarchyB);
                    }
                    if (!hierarchyA.isEmpty()) {
                        term.addHierarchy(hierarchyA);
                    }
                    
                    validEntries++;
                } else {
                    logger.warning("Invalid line format at line " + lineCount + ": " + line);
                }
            }
            
            logger.info("Parsed KEGG background file: " + lineCount + " lines, " + 
                       validEntries + " valid entries, " + keggTerms.size() + " unique K numbers");
        }
        
        return keggTerms;
    }
    
    /**
     * Get term information for a specific K number
     */
    public static KEGGTerm getKEGGTerm(String kNumber, Map<String, KEGGTerm> keggTerms) {
        if (kNumber == null || keggTerms == null) {
            return null;
        }
        
        // Try exact match first
        KEGGTerm term = keggTerms.get(kNumber);
        if (term != null) {
            return term;
        }
        
        // Try with K prefix if not present
        if (!kNumber.startsWith("K")) {
            term = keggTerms.get("K" + kNumber);
            if (term != null) {
                return term;
            }
        }
        
        // Try without K prefix if present
        if (kNumber.startsWith("K")) {
            term = keggTerms.get(kNumber.substring(1));
            if (term != null) {
                return term;
            }
        }
        
        return null;
    }
    
    /**
     * Test method to validate KEGG background file parsing
     */
    public static void main(String[] args) {
        try {
            String testFile = "I:\\GenomeDB\\Ananas_comosus.GP\\extracted\\Plants.20250428.TBtoolsKeggBackEnd";
            File keggFile = new File(testFile);
            
            System.out.println("Testing KEGG background parser...");
            System.out.println("File: " + testFile);
            System.out.println("File exists: " + keggFile.exists());
            
            if (keggFile.exists()) {
                Map<String, KEGGTerm> terms = parseKEGGBackground(keggFile);
                
                System.out.println("Total K numbers parsed: " + terms.size());
                
                // Show some examples
                int count = 0;
                for (KEGGTerm term : terms.values()) {
                    System.out.println(term);
                    count++;
                    if (count >= 5) break;
                }
                
                // Test specific K number lookup
                String testK = "K14400";
                KEGGTerm testTerm = getKEGGTerm(testK, terms);
                if (testTerm != null) {
                    System.out.println("\nTest lookup for " + testK + ":");
                    System.out.println("Description: " + testTerm.getDescription());
                    System.out.println("Pathways: " + testTerm.getPathwaysString());
                    System.out.println("Hierarchies: " + testTerm.getHierarchiesString());
                } else {
                    System.out.println("Test K number " + testK + " not found");
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}