package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;

import javax.swing.*;
import java.awt.Component;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class GeneSetGeneIdValidationSupport {

    private GeneSetGeneIdValidationSupport() {
    }

    static ValidationResult validateGeneIds(SpeciesInfo species, List<String> inputIds) throws Exception {
        GeneDataRetriever dataRetriever = new GeneDataRetriever();
        GeneSetImportSupport.AnnotationIndex annotationIndex = GeneSetImportSupport.loadAnnotationIndex(species);

        LinkedHashSet<String> resolvedTranscriptIds = new LinkedHashSet<>();
        List<String> missingIds = new ArrayList<>();
        List<String> correctedLines = new ArrayList<>();

        for (String inputId : inputIds) {
            if (inputId == null || inputId.trim().isEmpty()) {
                continue;
            }

            String trimmedInputId = inputId.trim();
            String canonicalTranscriptId = annotationIndex.findCanonicalTranscriptId(trimmedInputId);
            if (canonicalTranscriptId != null) {
                resolvedTranscriptIds.add(canonicalTranscriptId);
                if (!canonicalTranscriptId.equals(trimmedInputId)) {
                    correctedLines.add("Transcript found: " + trimmedInputId + " -> " + canonicalTranscriptId);
                }
                continue;
            }

            List<String> geneTranscriptIds = annotationIndex.getTranscriptsForGene(trimmedInputId);
            if (!geneTranscriptIds.isEmpty()) {
                resolvedTranscriptIds.addAll(geneTranscriptIds);
                correctedLines.add("Gene expanded: " + trimmedInputId + " -> "
                    + geneTranscriptIds.size() + " transcripts");
                continue;
            }

            GeneSearchResult searchResult = dataRetriever.searchGene(trimmedInputId, species);
            String matchedTranscriptId = annotationIndex.findBestTranscriptMatch(trimmedInputId);
            if (matchedTranscriptId != null) {
                resolvedTranscriptIds.add(matchedTranscriptId);
                if (!matchedTranscriptId.equals(trimmedInputId)) {
                    correctedLines.add("Transcript found: " + trimmedInputId + " -> " + matchedTranscriptId);
                }
                continue;
            }

            if (searchResult != null && searchResult.isFound()) {
                String resolvedGeneId = searchResult.getResolvedGeneId();
                List<String> resolvedGeneTranscripts = annotationIndex.getTranscriptsForGene(resolvedGeneId);
                if (!resolvedGeneTranscripts.isEmpty()) {
                    resolvedTranscriptIds.addAll(resolvedGeneTranscripts);
                    correctedLines.add("Gene expanded: " + trimmedInputId + " -> "
                        + resolvedGeneTranscripts.size() + " transcripts");
                    continue;
                }
            }

            String matchedGeneId = annotationIndex.findBestGeneMatch(trimmedInputId);
            if (matchedGeneId != null) {
                List<String> matchedGeneTranscripts = annotationIndex.getTranscriptsForGene(matchedGeneId);
                if (!matchedGeneTranscripts.isEmpty()) {
                    resolvedTranscriptIds.addAll(matchedGeneTranscripts);
                    correctedLines.add("Gene expanded: " + trimmedInputId + " -> "
                        + matchedGeneTranscripts.size() + " transcripts");
                    continue;
                }
            }

            missingIds.add(trimmedInputId);
        }

        return new ValidationResult(
            new ArrayList<>(resolvedTranscriptIds),
            missingIds,
            correctedLines
        );
    }

    static void showValidationResult(Component parent, ValidationResult validationResult) {
        JTextArea messageArea = new JTextArea(buildValidationMessage(validationResult), 16, 56);
        messageArea.setEditable(false);
        messageArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        messageArea.setCaretPosition(0);

        String title = validationResult.getMissingGeneIds().isEmpty()
            ? "Transcript IDs Corrected"
            : "ID Validation";

        JOptionPane.showMessageDialog(
            parent,
            new JScrollPane(messageArea),
            title,
            validationResult.getMissingGeneIds().isEmpty()
                ? JOptionPane.INFORMATION_MESSAGE
                : JOptionPane.WARNING_MESSAGE
        );
    }

    private static String buildValidationMessage(ValidationResult validationResult) {
        StringBuilder builder = new StringBuilder();

        if (!validationResult.getMissingGeneIds().isEmpty()) {
            builder.append("IDs Not Found:\n");
            for (String geneId : validationResult.getMissingGeneIds()) {
                builder.append(geneId).append('\n');
            }
        }

        if (!validationResult.getCorrectedGeneLines().isEmpty()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("Resolved IDs:\n");
            for (String correctedLine : validationResult.getCorrectedGeneLines()) {
                builder.append(correctedLine).append('\n');
            }
        }

        return builder.toString().trim();
    }

    static final class ValidationResult {
        private final List<String> resolvedGeneIds;
        private final List<String> missingGeneIds;
        private final List<String> correctedGeneLines;

        ValidationResult(List<String> resolvedGeneIds, List<String> missingGeneIds,
                         List<String> correctedGeneLines) {
            this.resolvedGeneIds = resolvedGeneIds;
            this.missingGeneIds = missingGeneIds;
            this.correctedGeneLines = correctedGeneLines;
        }

        List<String> getResolvedGeneIds() {
            return resolvedGeneIds;
        }

        List<String> getMissingGeneIds() {
            return missingGeneIds;
        }

        List<String> getCorrectedGeneLines() {
            return correctedGeneLines;
        }

        boolean hasIssues() {
            return !missingGeneIds.isEmpty() || !correctedGeneLines.isEmpty();
        }
    }
}
