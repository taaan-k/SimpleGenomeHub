package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.SequenceExtractor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class GeneSetImportSupport {

    enum OutputIdType {
        GENE("Gene ID"),
        TRANSCRIPT("Transcript ID");

        private final String displayName;

        OutputIdType(String displayName) {
            this.displayName = displayName;
        }

        String getDisplayName() {
            return displayName;
        }
    }

    private GeneSetImportSupport() {
    }

    static List<File> loadAvailableSetFiles(SpeciesInfo species) {
        List<File> files = new ArrayList<>();
        if (species == null) {
            return files;
        }

        File geneSetDir = species.getGeneSetDir();
        if (geneSetDir == null || !geneSetDir.isDirectory()) {
            return files;
        }

        File[] setFiles = geneSetDir.listFiles((dir, name) -> GeneSetFileSupport.isStandardSetFileName(name));
        if (setFiles == null || setFiles.length == 0) {
            return files;
        }

        Arrays.sort(setFiles, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        Collections.addAll(files, setFiles);
        return files;
    }

    static ImportedGeneSet importIds(SpeciesInfo species, File setFile, OutputIdType outputIdType) throws Exception {
        if (species == null) {
            throw new IllegalArgumentException("Species context is required.");
        }
        if (setFile == null || !setFile.isFile()) {
            throw new IllegalArgumentException("Selected gene set file is not available.");
        }

        GeneSetFileSupport.SetKind setKind = GeneSetFileSupport.detectSetKind(setFile);
        if (setKind == null) {
            throw new IllegalArgumentException("Unsupported gene set file format.");
        }

        if (setKind == GeneSetFileSupport.SetKind.GENE) {
            String content = GeneSetFileSupport.readGeneSetContent(setFile);
            List<String> inputIds = GeneSetFileSupport.parseGeneIds(content);
            if (inputIds.isEmpty()) {
                throw new IllegalArgumentException("The selected Gene Set does not contain any IDs.");
            }

            AnnotationIndex annotationIndex = loadAnnotationIndex(species);
            if (outputIdType == OutputIdType.GENE) {
                List<String> geneIds = resolveGeneIdsFromGeneSet(annotationIndex, inputIds);
                if (geneIds.isEmpty()) {
                    throw new NoGeneFoundException("No Gene Found");
                }
                return new ImportedGeneSet(setKind, outputIdType, geneIds);
            }

            List<String> transcriptIds = resolveTranscriptIdsFromGeneSet(annotationIndex, inputIds);
            if (transcriptIds.isEmpty()) {
                throw new NoGeneFoundException("No Transcript Found");
            }
            return new ImportedGeneSet(setKind, outputIdType, transcriptIds);
        }

        List<String> overlappingIds = collectOverlappingIds(species, setFile, outputIdType);
        if (overlappingIds.isEmpty()) {
            throw new NoGeneFoundException(outputIdType == OutputIdType.TRANSCRIPT
                ? "No Transcript Found"
                : "No Gene Found");
        }
        return new ImportedGeneSet(setKind, outputIdType, overlappingIds);
    }

    static List<String> collectOverlappingGeneIds(SpeciesInfo species, File regionSetFile) throws Exception {
        return collectOverlappingIds(species, regionSetFile, OutputIdType.GENE);
    }

    static List<String> collectOverlappingTranscriptIds(SpeciesInfo species, File regionSetFile) throws Exception {
        return collectOverlappingIds(species, regionSetFile, OutputIdType.TRANSCRIPT);
    }

    static List<String> collectOverlappingIds(SpeciesInfo species, File regionSetFile,
                                              OutputIdType outputIdType) throws Exception {
        if (species == null) {
            throw new IllegalArgumentException("Species context is required.");
        }
        if (regionSetFile == null || !regionSetFile.isFile()) {
            throw new IllegalArgumentException("Selected Region Set file is not available.");
        }

        List<GeneSetFileSupport.RegionEntry> regionEntries = loadRegionEntries(regionSetFile);
        if (regionEntries.isEmpty()) {
            return new ArrayList<>();
        }

        AnnotationIndex annotationIndex = loadAnnotationIndex(species);
        List<? extends GenomicRecord> records = outputIdType == OutputIdType.TRANSCRIPT
            ? annotationIndex.getTranscriptRecords()
            : annotationIndex.getGeneRecords();
        if (records.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<? extends GenomicRecord>> recordsByChromosome = groupRecordsByChromosome(records);
        Set<String> overlappingIds = new LinkedHashSet<>();
        for (GeneSetFileSupport.RegionEntry entry : regionEntries) {
            List<? extends GenomicRecord> chromosomeRecords = recordsByChromosome.get(entry.getChromosomeName());
            if (chromosomeRecords == null || chromosomeRecords.isEmpty()) {
                continue;
            }

            long regionStart = Math.min(entry.getStartPos(), entry.getEndPos());
            long regionEnd = Math.max(entry.getStartPos(), entry.getEndPos());
            for (GenomicRecord record : chromosomeRecords) {
                if (intervalsOverlap(regionStart, regionEnd, record.start, record.end)) {
                    overlappingIds.add(record.primaryId);
                }
            }
        }

        return new ArrayList<>(overlappingIds);
    }

    static String buildImportLabel(File setFile) {
        if (setFile == null) {
            return "";
        }

        String displayName = GeneSetFileSupport.extractDisplayName(setFile);
        GeneSetFileSupport.SetKind setKind = GeneSetFileSupport.detectSetKind(setFile);
        if (setKind == GeneSetFileSupport.SetKind.REGION) {
            return displayName + " [Region Set]";
        }
        return displayName + " [Gene Set]";
    }

    private static List<GeneSetFileSupport.RegionEntry> loadRegionEntries(File regionSetFile) throws Exception {
        String rawContent = GeneSetFileSupport.readGeneSetContent(regionSetFile);
        return GeneSetFileSupport.parseRegionEntries(rawContent);
    }

    static AnnotationIndex loadAnnotationIndex(SpeciesInfo species) throws Exception {
        File annotationFile = requireAnnotationFile(species);
        Map<String, List<SequenceExtractor.FeatureInfo>> featuresByType = SequenceExtractor.parseAnnotationFile(annotationFile);
        return buildAnnotationIndex(featuresByType);
    }

    private static File requireAnnotationFile(SpeciesInfo species) {
        File annotationFile = species.getAnnotationFile();
        if (annotationFile == null || !annotationFile.isFile()) {
            throw new IllegalStateException("Annotation GFF/GTF file is missing for species: "
                + species.getSpeciesDirectoryName());
        }
        return annotationFile;
    }

    private static AnnotationIndex buildAnnotationIndex(Map<String, List<SequenceExtractor.FeatureInfo>> featuresByType) {
        List<GeneRecord> geneRecords = new ArrayList<>();
        List<TranscriptRecord> transcriptRecords = new ArrayList<>();
        Map<String, LinkedHashSet<String>> transcriptsByGene = new LinkedHashMap<>();
        Map<String, String> transcriptAliasToCanonical = new LinkedHashMap<>();
        Map<String, String> geneAliasToCanonical = new LinkedHashMap<>();
        Map<String, String> geneIdByTranscriptId = new LinkedHashMap<>();

        List<SequenceExtractor.FeatureInfo> geneFeatures = getFeaturesIgnoreCase(featuresByType, "gene");
        if (geneFeatures != null) {
            for (SequenceExtractor.FeatureInfo feature : geneFeatures) {
                String geneId = resolveGeneId(feature);
                if (geneId == null || geneId.trim().isEmpty()) {
                    continue;
                }
                geneRecords.add(new GeneRecord(feature.getSeqId(), normalizeStart(feature), normalizeEnd(feature), geneId));
                registerGeneAliases(geneAliasToCanonical, geneId, feature, true);
            }
        }

        List<SequenceExtractor.FeatureInfo> pseudogeneFeatures = getFeaturesIgnoreCase(featuresByType, "pseudogene");
        if (pseudogeneFeatures != null) {
            for (SequenceExtractor.FeatureInfo feature : pseudogeneFeatures) {
                String geneId = resolveGeneId(feature);
                if (geneId == null || geneId.trim().isEmpty()) {
                    continue;
                }
                geneRecords.add(new GeneRecord(feature.getSeqId(), normalizeStart(feature), normalizeEnd(feature), geneId));
                registerGeneAliases(geneAliasToCanonical, geneId, feature, true);
            }
        }

        for (String featureType : new String[]{"mrna", "transcript", "rna"}) {
            List<SequenceExtractor.FeatureInfo> features = getFeaturesIgnoreCase(featuresByType, featureType);
            collectTranscriptRecords(
                features,
                transcriptRecords,
                transcriptsByGene,
                transcriptAliasToCanonical,
                geneAliasToCanonical,
                geneIdByTranscriptId
            );
        }

        if (geneRecords.isEmpty() && !transcriptRecords.isEmpty()) {
            Map<String, MergedGeneRecord> mergedTranscriptRecords = new LinkedHashMap<>();
            for (TranscriptRecord transcriptRecord : transcriptRecords) {
                String geneId = transcriptRecord.geneId != null && !transcriptRecord.geneId.trim().isEmpty()
                    ? transcriptRecord.geneId
                    : transcriptRecord.transcriptId;
                String key = transcriptRecord.chromosome + '\t' + geneId;
                MergedGeneRecord existing = mergedTranscriptRecords.get(key);
                if (existing == null) {
                    mergedTranscriptRecords.put(key,
                        new MergedGeneRecord(transcriptRecord.chromosome, transcriptRecord.start, transcriptRecord.end, geneId));
                } else {
                    existing.expand(transcriptRecord.start, transcriptRecord.end);
                }
            }

            for (MergedGeneRecord mergedRecord : mergedTranscriptRecords.values()) {
                geneRecords.add(new GeneRecord(mergedRecord.chromosome, mergedRecord.start, mergedRecord.end, mergedRecord.geneId));
            }
        }

        for (GeneRecord geneRecord : geneRecords) {
            registerAlias(geneAliasToCanonical, geneRecord.geneId, geneRecord.geneId);
        }

        return new AnnotationIndex(
            geneRecords,
            transcriptRecords,
            transcriptsByGene,
            transcriptAliasToCanonical,
            geneAliasToCanonical,
            geneIdByTranscriptId
        );
    }

    private static List<SequenceExtractor.FeatureInfo> getFeaturesIgnoreCase(
        Map<String, List<SequenceExtractor.FeatureInfo>> featuresByType, String targetType) {
        if (featuresByType == null || targetType == null) {
            return null;
        }
        for (Map.Entry<String, List<SequenceExtractor.FeatureInfo>> entry : featuresByType.entrySet()) {
            if (targetType.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void collectTranscriptRecords(List<SequenceExtractor.FeatureInfo> features,
                                                 List<TranscriptRecord> transcriptRecords,
                                                 Map<String, LinkedHashSet<String>> transcriptsByGene,
                                                 Map<String, String> transcriptAliasToCanonical,
                                                 Map<String, String> geneAliasToCanonical,
                                                 Map<String, String> geneIdByTranscriptId) {
        if (features == null) {
            return;
        }

        for (SequenceExtractor.FeatureInfo feature : features) {
            String transcriptId = resolveTranscriptId(feature);
            if (transcriptId == null || transcriptId.trim().isEmpty()) {
                transcriptId = feature.getSeqId() + ":" + normalizeStart(feature) + "-" + normalizeEnd(feature);
            }

            String geneId = resolveParentGeneId(feature);
            transcriptRecords.add(new TranscriptRecord(
                feature.getSeqId(),
                normalizeStart(feature),
                normalizeEnd(feature),
                transcriptId,
                geneId
            ));

            registerTranscriptAliases(transcriptAliasToCanonical, transcriptId, feature);
            if (geneId != null && !geneId.trim().isEmpty()) {
                registerGeneAliases(geneAliasToCanonical, geneId, feature, false);
                transcriptsByGene.computeIfAbsent(geneId, ignored -> new LinkedHashSet<>()).add(transcriptId);
                geneIdByTranscriptId.putIfAbsent(transcriptId, geneId);
            }
        }
    }

    private static List<String> resolveTranscriptIdsFromGeneSet(AnnotationIndex annotationIndex, List<String> inputIds) {
        LinkedHashSet<String> transcriptIds = new LinkedHashSet<>();

        for (String inputId : inputIds) {
            String canonicalTranscriptId = annotationIndex.findCanonicalTranscriptId(inputId);
            if (canonicalTranscriptId != null) {
                transcriptIds.add(canonicalTranscriptId);
                continue;
            }

            List<String> mappedTranscripts = annotationIndex.getTranscriptsForGene(inputId);
            if (!mappedTranscripts.isEmpty()) {
                transcriptIds.addAll(mappedTranscripts);
            }
        }

        return new ArrayList<>(transcriptIds);
    }

    private static List<String> resolveGeneIdsFromGeneSet(AnnotationIndex annotationIndex, List<String> inputIds) {
        LinkedHashSet<String> geneIds = new LinkedHashSet<>();

        for (String inputId : inputIds) {
            String canonicalTranscriptId = annotationIndex.findCanonicalTranscriptId(inputId);
            if (canonicalTranscriptId != null) {
                String geneId = annotationIndex.getGeneIdForTranscript(canonicalTranscriptId);
                if (hasText(geneId)) {
                    geneIds.add(geneId);
                }
                continue;
            }

            String canonicalGeneId = annotationIndex.findCanonicalGeneId(inputId);
            if (hasText(canonicalGeneId)) {
                geneIds.add(canonicalGeneId);
            }
        }

        return new ArrayList<>(geneIds);
    }

    private static Map<String, List<? extends GenomicRecord>> groupRecordsByChromosome(List<? extends GenomicRecord> records) {
        Map<String, List<? extends GenomicRecord>> grouped = new LinkedHashMap<>();
        for (GenomicRecord record : records) {
            @SuppressWarnings("unchecked")
            List<GenomicRecord> chromosomeRecords = (List<GenomicRecord>) grouped.computeIfAbsent(
                record.chromosome, ignored -> new ArrayList<GenomicRecord>());
            chromosomeRecords.add(record);
        }
        return grouped;
    }

    private static boolean intervalsOverlap(long start1, long end1, long start2, long end2) {
        return start1 <= end2 && start2 <= end1;
    }

    private static int normalizeStart(SequenceExtractor.FeatureInfo feature) {
        return Math.min(feature.getStart(), feature.getEnd());
    }

    private static int normalizeEnd(SequenceExtractor.FeatureInfo feature) {
        return Math.max(feature.getStart(), feature.getEnd());
    }

    private static String resolveGeneId(SequenceExtractor.FeatureInfo feature) {
        if (feature == null) {
            return null;
        }
        if (hasText(feature.getGeneId())) {
            return feature.getGeneId().trim();
        }
        if (hasText(feature.getAttribute("gene_id"))) {
            return feature.getAttribute("gene_id").trim();
        }
        if (hasText(feature.getAttribute("geneID"))) {
            return feature.getAttribute("geneID").trim();
        }
        if (hasText(feature.getAttribute("gene"))) {
            return feature.getAttribute("gene").trim();
        }
        if (hasText(feature.getAttribute("Name"))) {
            return feature.getAttribute("Name").trim();
        }
        if (hasText(feature.getAttribute("ID"))) {
            return feature.getAttribute("ID").trim();
        }
        return null;
    }

    private static String resolveParentGeneId(SequenceExtractor.FeatureInfo feature) {
        if (feature == null) {
            return null;
        }
        if (hasText(feature.getGeneId())) {
            return feature.getGeneId().trim();
        }
        if (hasText(feature.getAttribute("gene_id"))) {
            return feature.getAttribute("gene_id").trim();
        }
        if (hasText(feature.getAttribute("geneID"))) {
            return feature.getAttribute("geneID").trim();
        }
        if (hasText(feature.getAttribute("gene"))) {
            return feature.getAttribute("gene").trim();
        }
        if (hasText(feature.getAttribute("Parent"))) {
            return feature.getAttribute("Parent").trim();
        }
        return null;
    }

    private static String resolveTranscriptId(SequenceExtractor.FeatureInfo feature) {
        if (feature == null) {
            return null;
        }
        if (hasText(feature.getTranscriptId())) {
            return feature.getTranscriptId().trim();
        }
        if (hasText(feature.getAttribute("ID"))) {
            return feature.getAttribute("ID").trim();
        }
        if (hasText(feature.getAttribute("transcript_id"))) {
            return feature.getAttribute("transcript_id").trim();
        }
        if (hasText(feature.getAttribute("Name"))) {
            return feature.getAttribute("Name").trim();
        }
        if (hasText(feature.getAttribute("Parent"))) {
            return feature.getAttribute("Parent").trim();
        }
        return null;
    }

    private static void registerGeneAliases(Map<String, String> geneAliasToCanonical, String canonicalGeneId,
                                            SequenceExtractor.FeatureInfo feature, boolean includeNameAliases) {
        registerAlias(geneAliasToCanonical, canonicalGeneId, canonicalGeneId);
        if (feature == null) {
            return;
        }

        registerAlias(geneAliasToCanonical, feature.getGeneId(), canonicalGeneId);
        registerAlias(geneAliasToCanonical, feature.getAttribute("gene_id"), canonicalGeneId);
        registerAlias(geneAliasToCanonical, feature.getAttribute("geneID"), canonicalGeneId);
        registerAlias(geneAliasToCanonical, feature.getAttribute("gene"), canonicalGeneId);
        registerAlias(geneAliasToCanonical, feature.getAttribute("Parent"), canonicalGeneId);

        if (includeNameAliases) {
            registerAlias(geneAliasToCanonical, feature.getAttribute("ID"), canonicalGeneId);
            registerAlias(geneAliasToCanonical, feature.getAttribute("Name"), canonicalGeneId);
        }
    }

    private static void registerTranscriptAliases(Map<String, String> transcriptAliasToCanonical,
                                                  String canonicalTranscriptId,
                                                  SequenceExtractor.FeatureInfo feature) {
        registerAlias(transcriptAliasToCanonical, canonicalTranscriptId, canonicalTranscriptId);
        if (feature == null) {
            return;
        }

        registerAlias(transcriptAliasToCanonical, feature.getTranscriptId(), canonicalTranscriptId);
        registerAlias(transcriptAliasToCanonical, feature.getAttribute("ID"), canonicalTranscriptId);
        registerAlias(transcriptAliasToCanonical, feature.getAttribute("transcript_id"), canonicalTranscriptId);
        registerAlias(transcriptAliasToCanonical, feature.getAttribute("Name"), canonicalTranscriptId);
    }

    private static void registerAlias(Map<String, String> aliasMap, String alias, String canonicalId) {
        if (!hasText(alias) || !hasText(canonicalId)) {
            return;
        }
        aliasMap.putIfAbsent(alias.trim(), canonicalId.trim());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static final class ImportedGeneSet {
        private final GeneSetFileSupport.SetKind setKind;
        private final OutputIdType outputIdType;
        private final List<String> ids;

        ImportedGeneSet(GeneSetFileSupport.SetKind setKind, OutputIdType outputIdType, Collection<String> ids) {
            this.setKind = setKind;
            this.outputIdType = outputIdType;
            this.ids = new ArrayList<>(ids);
        }

        GeneSetFileSupport.SetKind getSetKind() {
            return setKind;
        }

        OutputIdType getOutputIdType() {
            return outputIdType;
        }

        List<String> getIds() {
            return new ArrayList<>(ids);
        }
    }

    static final class NoGeneFoundException extends Exception {
        NoGeneFoundException(String message) {
            super(message);
        }
    }

    private abstract static class GenomicRecord {
        protected final String chromosome;
        protected final long start;
        protected final long end;
        protected final String primaryId;

        private GenomicRecord(String chromosome, long start, long end, String primaryId) {
            this.chromosome = chromosome;
            this.start = start;
            this.end = end;
            this.primaryId = primaryId;
        }
    }

    private static final class GeneRecord extends GenomicRecord {
        private final String geneId;

        private GeneRecord(String chromosome, long start, long end, String geneId) {
            super(chromosome, start, end, geneId);
            this.geneId = geneId;
        }
    }

    private static final class TranscriptRecord extends GenomicRecord {
        private final String transcriptId;
        private final String geneId;

        private TranscriptRecord(String chromosome, long start, long end, String transcriptId, String geneId) {
            super(chromosome, start, end, transcriptId);
            this.transcriptId = transcriptId;
            this.geneId = geneId;
        }
    }

    private static final class MergedGeneRecord {
        private final String chromosome;
        private long start;
        private long end;
        private final String geneId;

        private MergedGeneRecord(String chromosome, long start, long end, String geneId) {
            this.chromosome = chromosome;
            this.start = start;
            this.end = end;
            this.geneId = geneId;
        }

        private void expand(long newStart, long newEnd) {
            start = Math.min(start, newStart);
            end = Math.max(end, newEnd);
        }
    }

    static final class AnnotationIndex {
        private final List<GeneRecord> geneRecords;
        private final List<TranscriptRecord> transcriptRecords;
        private final Map<String, List<String>> transcriptsByGene;
        private final Map<String, String> transcriptAliasToCanonical;
        private final Map<String, String> geneAliasToCanonical;
        private final Map<String, String> geneIdByTranscriptId;

        private AnnotationIndex(List<GeneRecord> geneRecords,
                                List<TranscriptRecord> transcriptRecords,
                                Map<String, LinkedHashSet<String>> transcriptsByGene,
                                Map<String, String> transcriptAliasToCanonical,
                                Map<String, String> geneAliasToCanonical,
                                Map<String, String> geneIdByTranscriptId) {
            this.geneRecords = new ArrayList<>(geneRecords);
            this.transcriptRecords = new ArrayList<>(transcriptRecords);
            this.transcriptsByGene = new LinkedHashMap<>();
            for (Map.Entry<String, LinkedHashSet<String>> entry : transcriptsByGene.entrySet()) {
                List<String> transcriptList = new ArrayList<>(entry.getValue());
                transcriptList.sort(String::compareTo);
                this.transcriptsByGene.put(entry.getKey(), transcriptList);
            }
            this.transcriptAliasToCanonical = new LinkedHashMap<>(transcriptAliasToCanonical);
            this.geneAliasToCanonical = new LinkedHashMap<>(geneAliasToCanonical);
            this.geneIdByTranscriptId = new LinkedHashMap<>(geneIdByTranscriptId);
        }

        private List<GeneRecord> getGeneRecords() {
            return new ArrayList<>(geneRecords);
        }

        private List<TranscriptRecord> getTranscriptRecords() {
            return new ArrayList<>(transcriptRecords);
        }

        List<String> getTranscriptsForGene(String geneId) {
            String canonicalGeneId = findCanonicalGeneId(geneId);
            if (!hasText(canonicalGeneId)) {
                return Collections.emptyList();
            }
            List<String> transcripts = transcriptsByGene.get(canonicalGeneId);
            return transcripts == null ? Collections.emptyList() : new ArrayList<>(transcripts);
        }

        String findCanonicalTranscriptId(String transcriptId) {
            if (!hasText(transcriptId)) {
                return null;
            }
            return transcriptAliasToCanonical.get(transcriptId.trim());
        }

        String findCanonicalGeneId(String geneId) {
            if (!hasText(geneId)) {
                return null;
            }

            String trimmed = geneId.trim();
            String canonicalGeneId = geneAliasToCanonical.get(trimmed);
            if (canonicalGeneId != null) {
                return canonicalGeneId;
            }

            String canonicalTranscriptId = findCanonicalTranscriptId(trimmed);
            if (canonicalTranscriptId != null) {
                return geneIdByTranscriptId.get(canonicalTranscriptId);
            }

            return null;
        }

        String getGeneIdForTranscript(String transcriptId) {
            String canonicalTranscriptId = findCanonicalTranscriptId(transcriptId);
            if (!hasText(canonicalTranscriptId)) {
                return null;
            }
            return geneIdByTranscriptId.get(canonicalTranscriptId);
        }

        String findBestTranscriptMatch(String queryId) {
            return findBestAliasMatch(transcriptAliasToCanonical, queryId);
        }

        String findBestGeneMatch(String queryId) {
            String canonicalGeneId = findBestAliasMatch(geneAliasToCanonical, queryId);
            if (hasText(canonicalGeneId)) {
                return canonicalGeneId;
            }

            String canonicalTranscriptId = findBestTranscriptMatch(queryId);
            if (!hasText(canonicalTranscriptId)) {
                return null;
            }
            return geneIdByTranscriptId.get(canonicalTranscriptId);
        }

        private String findBestAliasMatch(Map<String, String> aliasMap, String queryId) {
            if (!hasText(queryId) || aliasMap.isEmpty()) {
                return null;
            }

            String trimmedQuery = queryId.trim();
            String normalizedQuery = normalizeForMatching(trimmedQuery);
            int bestScore = Integer.MIN_VALUE;
            String bestCanonicalId = null;

            for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
                String alias = entry.getKey();
                int score = scoreAliasMatch(trimmedQuery, normalizedQuery, alias);
                if (score > bestScore) {
                    bestScore = score;
                    bestCanonicalId = entry.getValue();
                }
            }

            return bestScore >= 60 ? bestCanonicalId : null;
        }

        private int scoreAliasMatch(String rawQuery, String normalizedQuery, String alias) {
            if (!hasText(alias)) {
                return Integer.MIN_VALUE;
            }

            String trimmedAlias = alias.trim();
            if (trimmedAlias.equals(rawQuery)) {
                return 120;
            }
            if (trimmedAlias.equalsIgnoreCase(rawQuery)) {
                return 115;
            }

            String normalizedAlias = normalizeForMatching(trimmedAlias);
            if (normalizedAlias.equals(normalizedQuery)) {
                return 110;
            }
            if (trimmedAlias.contains(rawQuery) || rawQuery.contains(trimmedAlias)) {
                return 90;
            }
            if (normalizedAlias.contains(normalizedQuery) || normalizedQuery.contains(normalizedAlias)) {
                return 80;
            }

            double similarity = calculateSimilarity(normalizedQuery, normalizedAlias);
            if (similarity >= 0.92d) {
                return 75;
            }
            if (similarity >= 0.85d) {
                return 65;
            }

            return Integer.MIN_VALUE;
        }

        private String normalizeForMatching(String id) {
            if (!hasText(id)) {
                return "";
            }

            String normalized = id.trim().toUpperCase(Locale.ROOT);
            normalized = normalized.replaceAll("\\.[0-9]+$", "");
            normalized = normalized.replaceAll("\\.[Tt][0-9]+$", "");
            normalized = normalized.replaceAll("_[Tt][0-9]+$", "");
            normalized = normalized.replaceAll("-MRNA-[0-9]+$", "");
            normalized = normalized.replaceAll("_TRANSCRIPT_[0-9]+$", "");
            return normalized;
        }

        private double calculateSimilarity(String left, String right) {
            int maxLength = Math.max(left.length(), right.length());
            if (maxLength == 0) {
                return 1.0d;
            }
            int distance = levenshteinDistance(left, right);
            return 1.0d - (double) distance / (double) maxLength;
        }

        private int levenshteinDistance(String left, String right) {
            int[][] dp = new int[left.length() + 1][right.length() + 1];
            for (int i = 0; i <= left.length(); i++) {
                dp[i][0] = i;
            }
            for (int j = 0; j <= right.length(); j++) {
                dp[0][j] = j;
            }

            for (int i = 1; i <= left.length(); i++) {
                for (int j = 1; j <= right.length(); j++) {
                    int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                    );
                }
            }
            return dp[left.length()][right.length()];
        }
    }
}
