package simplegenomehub.util.fileio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves highlight transcript or gene IDs against original annotations.
 */
final class MultipleSyntenyHighlightResolver {

    private MultipleSyntenyHighlightResolver() {
    }

    static List<MultipleSyntenyBatchContext.HighlightInfoEntry> resolve(
        MultipleSyntenyBatchContext context
    ) throws IOException {
        LinkedHashMap<String, MultipleSyntenyBatchContext.HighlightInfoEntry> entriesByKey = new LinkedHashMap<>();
        collectRegionHighlightEntries(entriesByKey, context);

        for (MultipleSyntenyService.GenomeLayout genomeLayout : context.getGenomeLayouts()) {
            if (genomeLayout == null) {
                continue;
            }

            MultipleSyntenyService.GenomeSelection selection =
                context.getGenomeSelection(genomeLayout.getSlotNumber());
            if (selection == null || selection.getSpecies() == null) {
                continue;
            }

            LinkedHashSet<String> requestedIds = normalizeValues(
                context.getHighlightGeneIds(selection.getSlotNumber())
            );
            if (requestedIds.isEmpty()) {
                continue;
            }

            Map<String, List<String>> requestedIdsByLower = new LinkedHashMap<>();
            for (String requestedId : requestedIds) {
                requestedIdsByLower
                    .computeIfAbsent(requestedId.toLowerCase(Locale.ROOT), ignored -> new ArrayList<>())
                    .add(requestedId);
            }

            File annotationFile = selection.getSpecies().getAnnotationFile();
            if (annotationFile == null || !annotationFile.isFile()) {
                throw new IOException("Annotation file is missing for "
                    + selection.getSpecies().getSpeciesDirectoryName() + ".");
            }

            Map<String, List<SequenceExtractor.FeatureInfo>> featuresByType =
                SequenceExtractor.parseAnnotationFile(annotationFile);

            LinkedHashSet<String> unmatchedIds = new LinkedHashSet<>(requestedIds);
            collectEntriesForFeatureTypes(
                entriesByKey,
                genomeLayout.getGenomeId(),
                unmatchedIds,
                requestedIds,
                requestedIdsByLower,
                featureLists(featuresByType, "mrna", "transcript", "rna")
            );

            if (!unmatchedIds.isEmpty()) {
                collectEntriesForFeatureTypes(
                    entriesByKey,
                    genomeLayout.getGenomeId(),
                    unmatchedIds,
                    requestedIds,
                    requestedIdsByLower,
                    featureLists(featuresByType, "gene", "pseudogene")
                );
            }
        }

        return new ArrayList<>(entriesByKey.values());
    }

    private static void collectRegionHighlightEntries(
        Map<String, MultipleSyntenyBatchContext.HighlightInfoEntry> entriesByKey,
        MultipleSyntenyBatchContext context
    ) {
        for (MultipleSyntenyService.HighlightRegion region : context.getHighlightRegions()) {
            if (region == null || !region.isValid()) {
                continue;
            }

            String genomeId = context.getGenomeId(region.getSlotNumber());
            String label = hasText(region.getLabel())
                ? region.getLabel()
                : buildRegionLabel(region.getChromosomeName(), region.getStart(), region.getEnd());
            String entryKey = genomeId + '\t' + label + '\t'
                + region.getChromosomeName() + '\t' + region.getStart() + '\t' + region.getEnd();
            entriesByKey.putIfAbsent(
                entryKey,
                new MultipleSyntenyBatchContext.HighlightInfoEntry(
                    label,
                    genomeId,
                    region.getChromosomeName(),
                    region.getStart(),
                    region.getEnd(),
                    MultipleSyntenyService.DEFAULT_HIGHLIGHT_COLOR,
                    label
                )
            );
        }
    }

    private static String buildRegionLabel(String chromosomeName, long start, long end) {
        return "Region:" + chromosomeName + ":" + Math.min(start, end) + "-" + Math.max(start, end);
    }

    private static void collectEntriesForFeatureTypes(
        Map<String, MultipleSyntenyBatchContext.HighlightInfoEntry> entriesByKey,
        String genomeId,
        Set<String> unmatchedIds,
        Set<String> requestedIds,
        Map<String, List<String>> requestedIdsByLower,
        List<SequenceExtractor.FeatureInfo> features
    ) {
        for (SequenceExtractor.FeatureInfo feature : features) {
            if (feature == null) {
                continue;
            }

            Set<String> matchedRequestedIds = matchRequestedIds(
                collectAliases(feature),
                requestedIds,
                requestedIdsByLower
            );
            if (matchedRequestedIds.isEmpty()) {
                continue;
            }

            String primaryId = resolvePrimaryId(feature);
            if (!hasText(primaryId) || !hasText(feature.getSeqId())) {
                continue;
            }

            long start = Math.min(feature.getStart(), feature.getEnd());
            long end = Math.max(feature.getStart(), feature.getEnd());
            if (start < 1 || end < 1) {
                continue;
            }

            String entryKey = genomeId + '\t' + primaryId + '\t'
                + feature.getSeqId() + '\t' + start + '\t' + end;
            entriesByKey.putIfAbsent(
                entryKey,
                new MultipleSyntenyBatchContext.HighlightInfoEntry(
                    primaryId,
                    genomeId,
                    feature.getSeqId(),
                    start,
                    end,
                    MultipleSyntenyService.DEFAULT_HIGHLIGHT_COLOR,
                    primaryId
                )
            );
            unmatchedIds.removeAll(matchedRequestedIds);
        }
    }

    private static List<SequenceExtractor.FeatureInfo> featureLists(
        Map<String, List<SequenceExtractor.FeatureInfo>> featuresByType,
        String... featureTypes
    ) {
        List<SequenceExtractor.FeatureInfo> features = new ArrayList<>();
        if (featuresByType == null || featureTypes == null) {
            return features;
        }

        for (String featureType : featureTypes) {
            for (Map.Entry<String, List<SequenceExtractor.FeatureInfo>> entry : featuresByType.entrySet()) {
                if (featureType.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                    features.addAll(entry.getValue());
                }
            }
        }
        return features;
    }

    private static Set<String> collectAliases(SequenceExtractor.FeatureInfo feature) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        addAlias(aliases, feature.getTranscriptId());
        addAlias(aliases, feature.getGeneId());
        addAlias(aliases, feature.getAttribute("ID"));
        addAlias(aliases, feature.getAttribute("Name"));
        addAlias(aliases, feature.getAttribute("Parent"));
        addAlias(aliases, feature.getAttribute("gene_id"));
        addAlias(aliases, feature.getAttribute("geneID"));
        addAlias(aliases, feature.getAttribute("gene"));
        addAlias(aliases, feature.getAttribute("transcript_id"));
        return aliases;
    }

    private static Set<String> matchRequestedIds(Set<String> aliases,
                                                 Set<String> requestedIds,
                                                 Map<String, List<String>> requestedIdsByLower) {
        LinkedHashSet<String> matchedRequestedIds = new LinkedHashSet<>();
        for (String alias : aliases) {
            if (!hasText(alias)) {
                continue;
            }

            if (requestedIds.contains(alias)) {
                matchedRequestedIds.add(alias);
                continue;
            }

            List<String> caseInsensitiveMatches = requestedIdsByLower.get(alias.toLowerCase(Locale.ROOT));
            if (caseInsensitiveMatches != null) {
                matchedRequestedIds.addAll(caseInsensitiveMatches);
            }
        }
        return matchedRequestedIds;
    }

    private static String resolvePrimaryId(SequenceExtractor.FeatureInfo feature) {
        if (hasText(feature.getTranscriptId())) {
            return feature.getTranscriptId().trim();
        }
        if (hasText(feature.getGeneId())) {
            return feature.getGeneId().trim();
        }
        if (hasText(feature.getAttribute("ID"))) {
            return feature.getAttribute("ID").trim();
        }
        if (hasText(feature.getAttribute("Name"))) {
            return feature.getAttribute("Name").trim();
        }
        return "";
    }

    private static void addAlias(Set<String> aliases, String alias) {
        if (hasText(alias)) {
            aliases.add(alias.trim());
        }
    }

    private static LinkedHashSet<String> normalizeValues(List<String> values) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            if (hasText(value)) {
                normalized.add(value.trim());
            }
        }
        return normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
