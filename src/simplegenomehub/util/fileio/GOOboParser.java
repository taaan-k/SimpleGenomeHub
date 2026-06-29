/*
 * GO OBO File Parser
 * Parses Gene Ontology OBO format files to extract term names and descriptions
 */
package simplegenomehub.util.fileio;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Parser for GO OBO format files
 * Extracts GO term information including ID, name, and definition
 * 
 * @author SimpleGenomeHub
 */
public class GOOboParser {
    
    private static final Logger logger = Logger.getLogger(GOOboParser.class.getName());
    
    public static class GOTerm {
        private String id;
        private String name;
        private String definition;
        private String namespace;
        private boolean isObsolete = false;
        private Set<String> altIds = new HashSet<>();
        private Set<String> synonyms = new HashSet<>();
        
        public GOTerm(String id) {
            this.id = id;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDefinition() { return definition; }
        public void setDefinition(String definition) { this.definition = definition; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public boolean isObsolete() { return isObsolete; }
        public void setObsolete(boolean obsolete) { isObsolete = obsolete; }
        public Set<String> getAltIds() { return altIds; }
        public Set<String> getSynonyms() { return synonyms; }
        
        public void addAltId(String altId) { altIds.add(altId); }
        public void addSynonym(String synonym) { synonyms.add(synonym); }
        
        @Override
        public String toString() {
            return id + ": " + (name != null ? name : "Unknown");
        }
    }
    
    private Map<String, GOTerm> goTerms;
    private boolean isParsed = false;
    
    /**
     * Constructor
     */
    public GOOboParser() {
        this.goTerms = new HashMap<>();
    }
    
    /**
     * Parse GO OBO file
     */
    public boolean parseOboFile(File oboFile) {
        if (oboFile == null || !oboFile.exists()) {
            return false;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(oboFile))) {
            String line;
            GOTerm currentTerm = null;
            boolean inTermSection = false;
            int termsLoaded = 0;
            
            logger.info("Parsing GO OBO file: " + oboFile.getName());
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                if (line.isEmpty() || line.startsWith("!")) {
                    continue;
                }
                
                if (line.equals("[Term]")) {
                    // Save previous term before starting new one
                    if (currentTerm != null) {
                        saveCurrentTerm(currentTerm);
                        termsLoaded++;
                    }
                    inTermSection = true;
                    currentTerm = null;
                    continue;
                } else if (line.startsWith("[") && line.endsWith("]")) {
                    // Other sections like [Typedef]
                    if (currentTerm != null) {
                        // Save previous term
                        saveCurrentTerm(currentTerm);
                        termsLoaded++;
                        currentTerm = null;
                    }
                    inTermSection = false;
                    continue;
                }
                
                if (!inTermSection) {
                    continue;
                }
                
                // Parse term properties
                if (line.startsWith("id: ")) {
                    String id = line.substring(4).trim();
                    currentTerm = new GOTerm(id);
                } else if (currentTerm != null) {
                    if (line.startsWith("name: ")) {
                        currentTerm.setName(line.substring(6).trim());
                    } else if (line.startsWith("def: ")) {
                        String def = line.substring(5).trim();
                        // Remove quotes and references
                        if (def.startsWith("\"") && def.contains("\"")) {
                            int endQuote = def.indexOf("\"", 1);
                            if (endQuote > 0) {
                                def = def.substring(1, endQuote);
                            }
                        }
                        currentTerm.setDefinition(def);
                    } else if (line.startsWith("namespace: ")) {
                        currentTerm.setNamespace(line.substring(11).trim());
                    } else if (line.startsWith("is_obsolete: ")) {
                        currentTerm.setObsolete(line.substring(13).trim().equals("true"));
                    } else if (line.startsWith("alt_id: ")) {
                        currentTerm.addAltId(line.substring(8).trim());
                    } else if (line.startsWith("synonym: ")) {
                        String synonym = line.substring(9).trim();
                        // Extract synonym text from quotes
                        if (synonym.startsWith("\"")) {
                            int endQuote = synonym.indexOf("\"", 1);
                            if (endQuote > 0) {
                                synonym = synonym.substring(1, endQuote);
                                currentTerm.addSynonym(synonym);
                            }
                        }
                    }
                }
            }
            
            // Save last term
            if (currentTerm != null) {
                saveCurrentTerm(currentTerm);
                termsLoaded++;
            }
            
            isParsed = true;
            logger.info("Loaded " + termsLoaded + " GO terms from OBO file");
            return termsLoaded > 0;
            
        } catch (IOException e) {
            logger.warning("Failed to parse OBO file: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Save current term and its alternative IDs
     */
    private void saveCurrentTerm(GOTerm term) {
        if (term.getId() != null) {
            goTerms.put(term.getId(), term);
            
            // Also map alternative IDs to the same term
            for (String altId : term.getAltIds()) {
                goTerms.put(altId, term);
            }
            
            // Log obsolete terms for debugging
            if (term.isObsolete()) {
                logger.fine("Including obsolete term: " + term.getId() + " - " + term.getName());
            }
        }
    }
    
    /**
     * Get GO term by ID
     */
    public GOTerm getGoTerm(String goId) {
        return goTerms.get(goId);
    }
    
    /**
     * Get GO term name
     */
    public String getGoTermName(String goId) {
        GOTerm term = goTerms.get(goId);
        return term != null ? term.getName() : null;
    }
    
    /**
     * Get GO term definition
     */
    public String getGoTermDefinition(String goId) {
        GOTerm term = goTerms.get(goId);
        return term != null ? term.getDefinition() : null;
    }
    
    /**
     * Get GO term namespace (biological_process, molecular_function, cellular_component)
     */
    public String getGoTermNamespace(String goId) {
        GOTerm term = goTerms.get(goId);
        return term != null ? term.getNamespace() : null;
    }
    
    /**
     * Check if parser has data
     */
    public boolean isParsed() {
        return isParsed && !goTerms.isEmpty();
    }
    
    /**
     * Get all loaded GO terms
     */
    public Map<String, GOTerm> getAllGoTerms() {
        return new HashMap<>(goTerms);
    }
    
    /**
     * Get statistics
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("total_terms", goTerms.size());
        stats.put("parsed", isParsed ? 1 : 0);
        
        // Count by namespace
        Map<String, Integer> namespaceCount = new HashMap<>();
        for (GOTerm term : goTerms.values()) {
            String namespace = term.getNamespace();
            if (namespace != null) {
                namespaceCount.merge(namespace, 1, Integer::sum);
            }
        }
        stats.putAll(namespaceCount);
        
        return stats;
    }
    
    /**
     * Clear all data
     */
    public void clear() {
        goTerms.clear();
        isParsed = false;
    }
}