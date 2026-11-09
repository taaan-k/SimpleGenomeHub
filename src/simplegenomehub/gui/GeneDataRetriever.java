/*
 * Gene Data Retriever
 * Unified data access layer for gene information from multiple sources
 */
package simplegenomehub.gui;

import simplegenomehub.model.*;
import simplegenomehub.util.fileio.CachedSequenceLookup;
import simplegenomehub.util.fileio.GeneSetSupport;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Retrieves gene data from various sources including:
 * - Sequence data (transcripts, CDS, proteins)
 * - Functional annotations (GO, KEGG, InterPro, etc.)
 * - Expression data from experiments
 * - Gene structure information from GFF3/GTF
 * 
 * @author SimpleGenomeHub Team
 */
public class GeneDataRetriever {
    
    private static final Logger logger = Logger.getLogger(GeneDataRetriever.class.getName());
    
    // Cache for frequently accessed data
    private Map<String, CachedSequenceLookup> sequenceLookupCache = new HashMap<>();
    private Map<String, Map<String, List<GeneAnnotationData.GeneAnnotation>>> annotationCache = new HashMap<>();
    private Map<String, GeneSetImportSupport.AnnotationIndex> geneIdIndexCache = new HashMap<>();
    
    /**
     * Search for comprehensive gene information
     */
    public GeneSearchResult searchGene(String geneId, SpeciesInfo species) throws Exception {
        logger.info("Searching for gene: " + geneId + " in species: " + species.getDisplayName());
        GeneSearchResult result = searchGeneCore(geneId, species);

        try {
            // Annotation and structure loading stays in the full path only.
            List<GeneAnnotationData.GeneAnnotation> annotations = getGeneAnnotations(result.getResolvedGeneId(), species);
            result.setAnnotations(annotations);

            GeneStructureInfo structureInfo = getGeneStructure(result.getResolvedGeneId(), species);
            result.setStructureInfo(structureInfo);

            boolean found = result.isFound() || result.hasSequenceData() || result.hasExpressionData() ||
                           !annotations.isEmpty() || structureInfo != null;
            result.setFound(found);

            if (found) {
                result.setGeneDescription(generateGeneDescription(result.getResolvedGeneId(), annotations));
                logger.info("Successfully retrieved gene data for: " + result.getResolvedGeneId());
            } else {
                logger.warning("No data found for gene: " + geneId);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error retrieving gene data for: " + geneId, e);
            throw e;
        }

        return result;
    }

    /**
     * Search only the fast data sources needed for immediate UI rendering.
     * This intentionally skips functional annotations and structure parsing.
     */
    public GeneSearchResult searchGeneCore(String geneId, SpeciesInfo species) throws Exception {
        GeneSearchResult result = new GeneSearchResult(geneId, species);

        try {
            Map<String, String> sequences = getGeneSequences(geneId, species);
            String resolvedGeneId = resolveGeneId(geneId, sequences);
            boolean exactGffGeneMatch = false;

            // Sequence IDs retain priority. Parse the GFF only when exact and
            // approximate sequence lookup both fail.
            if (sequences.isEmpty()) {
                GffGeneSequenceMatch gffMatch = findSequencesForExactGffGene(geneId, species);
                if (gffMatch != null) {
                    exactGffGeneMatch = true;
                    resolvedGeneId = gffMatch.getGeneId();
                    sequences = gffMatch.getSequences();
                }
            }

            result.setSequences(sequences);
            result.setResolvedGeneId(resolvedGeneId);

            ExpressionExperimentLoader.LoadResult expressionLoad = ExpressionExperimentLoader.load(species);
            result.setExpressionLoadResult(expressionLoad);
            Map<String, ExpressionData> expressionData = getGeneExpressionData(geneId, species, expressionLoad);
            result.setExpressionData(expressionData);

            boolean found = !sequences.isEmpty() || !expressionData.isEmpty() || exactGffGeneMatch;
            result.setFound(found);
            result.setGeneDescription(generateGeneDescription(result.getResolvedGeneId(), Collections.emptyList()));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error retrieving core gene data for: " + geneId, e);
            throw e;
        }

        return result;
    }
    
    /**
     * Get gene sequences (transcript, CDS, protein)
     */
    public Map<String, String> getGeneSequences(String geneId, SpeciesInfo species) throws Exception {
        Map<String, String> sequences = new HashMap<>();
        
        try {
            // Get or create cached sequence lookup
            String speciesKey = species.getSpeciesDirectoryName();
            CachedSequenceLookup lookup = sequenceLookupCache.get(speciesKey);
            
            if (lookup == null) {
                lookup = new CachedSequenceLookup(species);
                sequenceLookupCache.put(speciesKey, lookup);
            }
            
            // Search for different sequence types
            searchSequenceType(lookup, geneId, "transcript", sequences);
            searchSequenceType(lookup, geneId, "cds", sequences);
            searchSequenceType(lookup, geneId, "protein", sequences);
            
            // Also try common gene ID variations
            if (sequences.isEmpty()) {
                String[] variations = generateGeneIdVariations(geneId);
                for (String variation : variations) {
                    searchSequenceType(lookup, variation, "transcript", sequences);
                    searchSequenceType(lookup, variation, "cds", sequences);
                    searchSequenceType(lookup, variation, "protein", sequences);
                    
                    if (!sequences.isEmpty()) {
                        logger.info("Found sequences using gene ID variation: " + variation);
                        break;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting sequences for gene: " + geneId, e);
        }
        
        return sequences;
    }

    private GffGeneSequenceMatch findSequencesForExactGffGene(String geneId, SpeciesInfo species) {
        try {
            GeneSetImportSupport.AnnotationIndex annotationIndex = getGeneIdIndex(species);
            String canonicalGeneId = annotationIndex.findExactCanonicalGeneId(geneId);
            if (canonicalGeneId == null) {
                return null;
            }

            for (String transcriptId : annotationIndex.getTranscriptsForGene(canonicalGeneId)) {
                Map<String, String> transcriptSequences = getGeneSequences(transcriptId, species);
                if (!transcriptSequences.isEmpty()) {
                    logger.info("Resolved GFF gene ID " + geneId + " to transcript " + transcriptId);
                    return new GffGeneSequenceMatch(canonicalGeneId, transcriptSequences);
                }
            }

            logger.info("Found exact GFF gene ID without a matching cached sequence: " + canonicalGeneId);
            return new GffGeneSequenceMatch(canonicalGeneId, Collections.emptyMap());
        } catch (Exception e) {
            logger.log(Level.FINE, "Unable to resolve gene ID from GFF: " + geneId, e);
            return null;
        }
    }

    /**
     * Chromosome and 1-based location of a gene or transcript id, taken from the annotation
     * index that is already cached for this species - so a jump to the genome browser does not
     * parse the annotation again.
     *
     * @return the located row, or {@code null} when the id is not in the annotation
     */
    public GeneSetSupport.Entry findAnnotationEntry(SpeciesInfo species, String geneId) {
        if (species == null || geneId == null || geneId.trim().isEmpty()) {
            return null;
        }

        try {
            GeneSetImportSupport.AnnotationIndex annotationIndex = getGeneIdIndex(species);
            Map<String, GeneSetSupport.Entry> entriesById = annotationIndex.entriesById();
            String trimmedGeneId = geneId.trim();

            GeneSetSupport.Entry entry = entriesById.get(trimmedGeneId);
            if (entry == null) {
                String canonicalTranscriptId = annotationIndex.findCanonicalTranscriptId(trimmedGeneId);
                if (canonicalTranscriptId != null) {
                    entry = entriesById.get(canonicalTranscriptId);
                }
            }
            if (entry == null) {
                // A gene id resolves to its transcripts; the first one carries the gene span.
                for (String transcriptId : annotationIndex.getTranscriptsForGene(trimmedGeneId)) {
                    entry = entriesById.get(transcriptId);
                    if (entry != null) {
                        break;
                    }
                }
            }
            return entry;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Unable to locate " + geneId + " in the annotation", e);
            return null;
        }
    }

    private GeneSetImportSupport.AnnotationIndex getGeneIdIndex(SpeciesInfo species) throws Exception {
        String speciesKey = species.getSpeciesDirectoryName();
        GeneSetImportSupport.AnnotationIndex annotationIndex = geneIdIndexCache.get(speciesKey);
        if (annotationIndex == null) {
            annotationIndex = GeneSetImportSupport.loadAnnotationIndex(species);
            geneIdIndexCache.put(speciesKey, annotationIndex);
        }
        return annotationIndex;
    }

    private static final class GffGeneSequenceMatch {
        private final String geneId;
        private final Map<String, String> sequences;

        private GffGeneSequenceMatch(String geneId, Map<String, String> sequences) {
            this.geneId = geneId;
            this.sequences = sequences;
        }

        private String getGeneId() {
            return geneId;
        }

        private Map<String, String> getSequences() {
            return sequences;
        }
    }
    
    /**
     * Search for a specific sequence type
     */
    private void searchSequenceType(CachedSequenceLookup lookup, String geneId, String seqType, Map<String, String> sequences) {
        try {
            CachedSequenceLookup.LookupResult result = lookup.lookupSequences(geneId);
            if (result != null && result.isSuccess()) {
                // Extract the specific sequence type from the result
                String rawSequence = null;
                
                switch (seqType) {
                    case "transcript":
                        rawSequence = result.getTranscriptSequence();
                        break;
                    case "cds":
                        rawSequence = result.getCdsSequence();
                        break;
                    case "protein":
                        rawSequence = result.getProteinSequence();
                        break;
                }
                
                if (rawSequence != null && !rawSequence.isEmpty()) {
                    // Clean the sequence: remove FASTA header if present and extra whitespace
                    String cleanedSequence = cleanSequenceData(rawSequence);
                    if (!cleanedSequence.isEmpty()) {
                        String header = extractFastaHeader(rawSequence);
                        if (header == null || header.isEmpty()) {
                            header = ">" + geneId;
                        }
                        sequences.put(seqType, cleanedSequence);
                        sequences.put(seqType + "_header", header);
                        logger.fine("Found " + seqType + " sequence for: " + geneId + " (length: " + cleanedSequence.length() + ")");
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("No " + seqType + " sequence found for: " + geneId);
        }
    }
    
    /**
     * Get gene functional annotations
     */
    public List<GeneAnnotationData.GeneAnnotation> getGeneAnnotations(String geneId, SpeciesInfo species) {
        List<GeneAnnotationData.GeneAnnotation> annotations = new ArrayList<>();
        
        try {
            // Check cache first
            String speciesKey = species.getSpeciesDirectoryName();
            Map<String, List<GeneAnnotationData.GeneAnnotation>> speciesCache = annotationCache.get(speciesKey);
            
            if (speciesCache != null && speciesCache.containsKey(geneId)) {
                return new ArrayList<>(speciesCache.get(geneId));
            }
            
            // Load annotations from species functional annotation data
            GeneAnnotationData functionalData = species.getFunctionalAnnotations();
            if (functionalData == null || functionalData.getAnnotatedGenes() == 0) {
                // Try to force-load functional annotations if not loaded
                functionalData = loadFunctionalAnnotationsIfNeeded(species);
            }
            
            if (functionalData != null) {
                annotations.addAll(functionalData.getAllGeneAnnotations(geneId));
                
                // Also try gene ID variations
                if (annotations.isEmpty()) {
                    String[] variations = generateGeneIdVariations(geneId);
                    for (String variation : variations) {
                        List<GeneAnnotationData.GeneAnnotation> varAnnotations = functionalData.getAllGeneAnnotations(variation);
                        if (!varAnnotations.isEmpty()) {
                            annotations.addAll(varAnnotations);
                            logger.info("Found annotations using gene ID variation: " + variation);
                            break;
                        }
                    }
                }
            }
            
            // Cache the result
            if (speciesCache == null) {
                speciesCache = new HashMap<>();
                annotationCache.put(speciesKey, speciesCache);
            }
            speciesCache.put(geneId, new ArrayList<>(annotations));
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting annotations for gene: " + geneId, e);
        }
        
        return annotations;
    }
    
    /**
     * Get gene expression data from all experiments
     * For gene IDs, also searches for corresponding transcript IDs
     */
    public Map<String, ExpressionData> getGeneExpressionData(String geneId, SpeciesInfo species) {
        return getGeneExpressionData(geneId, species, ExpressionExperimentLoader.load(species));
    }

    private Map<String, ExpressionData> getGeneExpressionData(String geneId, SpeciesInfo species,
            ExpressionExperimentLoader.LoadResult loaded) {
        Map<String, ExpressionData> expressionData = new LinkedHashMap<>();
        if (loaded.getReadyMatrixCount() == 0) return expressionData;
        
        try {
            List<ExpressionExperiment> experiments = loaded.getExperiments();
            
            // First try the gene ID directly
            for (ExpressionExperiment experiment : experiments) {
                ExpressionData geneExpression = getGeneExpressionFromExperiment(geneId, experiment);
                if (geneExpression != null && !geneExpression.getExpressionValues().isEmpty()) {
                    expressionData.put(experiment.getExperimentId(), geneExpression);
                }
            }
            
            // If no data found with gene ID, try transcript ID variations
            if (expressionData.isEmpty()) {
                String[] transcriptVariations = generateTranscriptIdVariations(geneId);
                logger.info("Trying transcript ID variations for gene: " + geneId + " -> " + java.util.Arrays.toString(transcriptVariations));
                
                for (String transcriptId : transcriptVariations) {
                    for (ExpressionExperiment experiment : experiments) {
                        ExpressionData geneExpression = getGeneExpressionFromExperiment(transcriptId, experiment);
                        if (geneExpression != null && !geneExpression.getExpressionValues().isEmpty()) {
                            expressionData.put(experiment.getExperimentId(), geneExpression);
                        }
                    }
                    
                    if (!expressionData.isEmpty()) {
                        logger.info("Found expression data using transcript ID: " + transcriptId);
                        break;
                    }
                }
            }
            
            // Try general gene ID variations if still no data found
            if (expressionData.isEmpty()) {
                String[] variations = generateGeneIdVariations(geneId);
                for (String variation : variations) {
                    for (ExpressionExperiment experiment : experiments) {
                        ExpressionData geneExpression = getGeneExpressionFromExperiment(variation, experiment);
                        if (geneExpression != null && !geneExpression.getExpressionValues().isEmpty()) {
                            expressionData.put(experiment.getExperimentId(), geneExpression);
                        }
                    }
                    
                    if (!expressionData.isEmpty()) {
                        logger.info("Found expression data using gene ID variation: " + variation);
                        break;
                    }
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting expression data for gene: " + geneId, e);
        }
        
        return expressionData;
    }
    
    /**
     * Get gene expression from a specific experiment
     */
    private ExpressionData getGeneExpressionFromExperiment(String geneId, ExpressionExperiment experiment) {
        try {
            for (ExpressionMatrix matrix : experiment.getMatrices()) {
                Map<String, Double> values = matrix.getGeneExpression(geneId);
                if (values != null && !values.isEmpty()) {
                    return new ExpressionData(geneId, experiment.getExperimentId(), values);
                }
            }
        } catch (Exception e) {
            logger.fine("No expression data found for gene " + geneId + " in experiment " + experiment.getExperimentId());
        }
        
        return null;
    }
    
    /**
     * Get gene structure information
     */
    public GeneStructureInfo getGeneStructure(String geneId, SpeciesInfo species) {
        try {
            // Extract structure from GFF3/GTF annotation files
            File annotationDir = species.getAnnotationDir();
            if (annotationDir != null && annotationDir.exists()) {
                File[] gffFiles = annotationDir.listFiles((dir, name) -> 
                    name.toLowerCase().endsWith(".gff3") || name.toLowerCase().endsWith(".gtf"));
                
                if (gffFiles != null) {
                    for (File gffFile : gffFiles) {
                        GeneStructureInfo structure = parseGeneStructureFromGFF(geneId, gffFile);
                        if (structure != null) {
                            return structure;
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error getting gene structure for: " + geneId, e);
        }
        
        return null;
    }
    
    /**
     * Parse gene structure from GFF3/GTF file
     */
    private GeneStructureInfo parseGeneStructureFromGFF(String geneId, File gffFile) {
        // Simplified implementation - in practice would use TBtools GFF parsers
        // For now, return null to indicate structure parsing is not yet implemented
        return null;
    }
    
    /**
     * Generate transcript ID variations specifically for expression data search
     * Expression data is often stored by transcript ID rather than gene ID
     */
    private String[] generateTranscriptIdVariations(String geneId) {
        Set<String> variations = new HashSet<>();
        
        // Primary transcript variations for expression data
        if (!geneId.contains(".t")) {
            // Add common transcript suffixes
            variations.add(geneId + ".t1");
            variations.add(geneId + ".T1");
            variations.add(geneId + "_t1");
            variations.add(geneId + "_T1");
        }
        
        // Try with and without version numbers
        if (geneId.contains(".") && !geneId.contains(".t")) {
            String baseId = geneId.substring(0, geneId.lastIndexOf("."));
            variations.add(baseId + ".t1");
            variations.add(baseId + ".T1");
            variations.add(baseId + "_t1");
            variations.add(baseId + "_T1");
        }
        
        // Also add the original ID in case it's already a transcript ID
        variations.add(geneId);
        
        return variations.toArray(new String[0]);
    }
    
    /**
     * Generate gene ID variations for search
     */
    private String[] generateGeneIdVariations(String geneId) {
        Set<String> variations = new HashSet<>();
        variations.add(geneId);
        
        // Common variations
        variations.add(geneId.toUpperCase());
        variations.add(geneId.toLowerCase());
        
        // Remove version numbers (e.g., AT1G01010.1 -> AT1G01010)
        if (geneId.contains(".")) {
            variations.add(geneId.substring(0, geneId.lastIndexOf(".")));
        }
        
        // Add version .1 if not present
        if (!geneId.contains(".")) {
            variations.add(geneId + ".1");
        }
        
        // Gene vs transcript ID conversions
        if (geneId.endsWith(".t1") || geneId.endsWith(".T1")) {
            variations.add(geneId.substring(0, geneId.length() - 3));
        } else {
            variations.add(geneId + ".t1");
            variations.add(geneId + ".T1");
        }
        
        return variations.toArray(new String[0]);
    }
    
    /**
     * Generate gene description from annotation data
     */
    private String generateGeneDescription(String geneId, List<GeneAnnotationData.GeneAnnotation> annotations) {
        if (annotations.isEmpty()) {
            return "Gene: " + geneId;
        }
        
        // Look for GO or KEGG descriptions
        for (GeneAnnotationData.GeneAnnotation annotation : annotations) {
            if (annotation.getDescription() != null && !annotation.getDescription().trim().isEmpty()) {
                String desc = annotation.getDescription().trim();
                if (!desc.equalsIgnoreCase("unknown") && !desc.equalsIgnoreCase("hypothetical protein")) {
                    return geneId + " - " + desc;
                }
            }
        }
        
        // Fallback to annotation type count
        Map<GeneAnnotationData.AnnotationType, Integer> typeCounts = new HashMap<>();
        for (GeneAnnotationData.GeneAnnotation annotation : annotations) {
            typeCounts.merge(annotation.getType(), 1, Integer::sum);
        }
        
        StringBuilder desc = new StringBuilder(geneId + " (");
        boolean first = true;
        for (Map.Entry<GeneAnnotationData.AnnotationType, Integer> entry : typeCounts.entrySet()) {
            if (!first) desc.append(", ");
            desc.append(entry.getValue()).append(" ").append(entry.getKey().getShortName());
            first = false;
        }
        desc.append(" annotations)");
        
        return desc.toString();
    }
    
    /**
     * Clean sequence data by removing FASTA headers and extra whitespace
     */
    private String cleanSequenceData(String rawSequence) {
        if (rawSequence == null || rawSequence.isEmpty()) {
            return "";
        }
        
        StringBuilder cleaned = new StringBuilder();
        String[] lines = rawSequence.split("\\r?\\n");
        
        for (String line : lines) {
            line = line.trim();
            // Skip FASTA header lines
            if (!line.startsWith(">") && !line.isEmpty()) {
                cleaned.append(line);
            }
        }
        
        return cleaned.toString();
    }

    /**
     * Extract the original FASTA header line from sequence content.
     */
    private String extractFastaHeader(String rawSequence) {
        if (rawSequence == null || rawSequence.isEmpty()) {
            return null;
        }

        String[] lines = rawSequence.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(">")) {
                return trimmed;
            }
        }

        return null;
    }

    /**
     * Resolve the matched gene ID from returned sequence headers.
     */
    private String resolveGeneId(String searchedGeneId, Map<String, String> sequences) {
        if (sequences == null || sequences.isEmpty()) {
            return searchedGeneId;
        }

        String[] headerKeys = {"transcript_header", "cds_header", "protein_header"};
        for (String headerKey : headerKeys) {
            String resolvedGeneId = extractGeneIdFromHeader(sequences.get(headerKey));
            if (resolvedGeneId != null && !resolvedGeneId.isEmpty()) {
                return resolvedGeneId;
            }
        }

        return searchedGeneId;
    }

    private String extractGeneIdFromHeader(String header) {
        if (header == null || header.trim().isEmpty()) {
            return null;
        }

        String normalized = header.trim();
        if (normalized.startsWith(">")) {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.isEmpty()) {
            return null;
        }

        String primaryId = normalized.split("\\s+")[0];
        if (primaryId.isEmpty()) {
            return null;
        }

        if (primaryId.matches(".*\\.[0-9]+$")) {
            return primaryId.substring(0, primaryId.lastIndexOf('.'));
        }
        if (primaryId.matches(".*\\.t[0-9]+$")) {
            return primaryId.substring(0, primaryId.lastIndexOf(".t"));
        }
        if (primaryId.matches(".*\\.T[0-9]+$")) {
            return primaryId.substring(0, primaryId.lastIndexOf(".T"));
        }
        if (primaryId.matches(".*_t[0-9]+$")) {
            return primaryId.substring(0, primaryId.lastIndexOf("_t"));
        }
        if (primaryId.matches(".*_T[0-9]+$")) {
            return primaryId.substring(0, primaryId.lastIndexOf("_T"));
        }

        return primaryId;
    }
    
    /**
     * Force-load functional annotations if they haven't been loaded yet
     */
    private GeneAnnotationData loadFunctionalAnnotationsIfNeeded(SpeciesInfo species) {
        try {
            // First try the existing load method
            species.loadFunctionalAnnotations();
            GeneAnnotationData annotations = species.getFunctionalAnnotations();
            
            // If still no annotations loaded, try to load the TSV files directly
            if (annotations == null || annotations.getAnnotatedGenes() == 0) {
                annotations = new GeneAnnotationData("gene_viewer_" + species.getSpeciesDirectoryName(), 
                                                   "Functional Annotations for " + species.getDisplayName());
                
                File functionalDir = species.getFunctionalAnnotationDir();
                if (functionalDir != null && functionalDir.exists()) {
                    
                    // Load GO annotations
                    File goFile = new File(functionalDir, "GO/GO.tsv");
                    if (goFile.exists()) {
                        boolean loaded = annotations.loadGOAnnotations(goFile);
                        logger.info("Loaded GO annotations: " + loaded + " from " + goFile.getAbsolutePath());
                    }
                    
                    // Load KEGG annotations
                    File keggFile = new File(functionalDir, "KEGG/KEGG.tsv");
                    if (keggFile.exists()) {
                        boolean loaded = annotations.loadKEGGAnnotations(keggFile);
                        logger.info("Loaded KEGG annotations: " + loaded + " from " + keggFile.getAbsolutePath());
                    }
                    
                    // Load Custom annotations
                    File customFile = new File(functionalDir, "Custom/Custom.tsv");
                    if (customFile.exists()) {
                        boolean loaded = annotations.loadCustomAnnotations(customFile, GeneAnnotationData.AnnotationType.CUSTOM);
                        logger.info("Loaded Custom annotations: " + loaded + " from " + customFile.getAbsolutePath());
                    }

                    // Load PFAM annotations
                    File pfamFile = new File(functionalDir, "Pfam/Pfam.tsv");
                    if (pfamFile.exists()) {
                        boolean loaded = annotations.loadCustomAnnotations(pfamFile, GeneAnnotationData.AnnotationType.PFAM);
                        logger.info("Loaded Pfam annotations: " + loaded + " from " + pfamFile.getAbsolutePath());
                    }
                }
                
                // Set the loaded annotations back to the species
                species.setFunctionalAnnotations(annotations);
                logger.info("Force-loaded functional annotations for " + species.getDisplayName() + 
                           " (" + annotations.getAnnotatedGenes() + " genes)");
            }
            
            return annotations;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to force-load functional annotations for " + species.getDisplayName(), e);
            return null;
        }
    }
    
    /**
     * Clear all caches
     */
    public void clearCaches() {
        sequenceLookupCache.clear();
        annotationCache.clear();
        geneIdIndexCache.clear();
        logger.info("Gene data caches cleared");
    }
}
