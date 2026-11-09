package simplegenomehub.gui;

import simplegenomehub.model.ExpressionExperiment;
import simplegenomehub.model.ExpressionMatrix;
import simplegenomehub.model.ExpressionExperimentLoader;
import simplegenomehub.model.SpeciesInfo;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Shared loading and formatting helpers for expression experiment displays.
 */
final class ExpressionExperimentDisplaySupport {

    private static final Logger logger = Logger.getLogger(ExpressionExperimentDisplaySupport.class.getName());
    private static final DateTimeFormatter DETAIL_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ExpressionExperimentDisplaySupport() {
    }

    static List<ExpressionExperiment> loadExpressionExperiments(SpeciesInfo targetSpecies) {
        return new ArrayList<>(ExpressionExperimentLoader.load(targetSpecies).getExperiments());
    }

    static String buildExperimentDetailsText(ExpressionExperiment experiment) {
        return buildExperimentDetailsText(experiment, null);
    }

    static String buildExperimentDetailsText(ExpressionExperiment experiment, File selectedMatrixFile) {
        if (experiment == null) {
            return "Select an experiment to view details.";
        }

        StringBuilder details = new StringBuilder();
        details.append("EXPERIMENT DETAILS\n");
        details.append("==================\n\n");
        details.append("ID: ").append(experiment.getExperimentId()).append("\n");
        details.append("Name: ").append(experiment.getTitle()).append("\n");
        details.append("Type: ")
            .append(experiment.getExperimentType() != null ? experiment.getExperimentType() : "Unknown")
            .append("\n");
        details.append("Description: ").append(experiment.getDescription() != null
            ? experiment.getDescription() : "No description").append("\n");
        details.append("Import Time: ").append(experiment.getImportTime().format(DETAIL_TIME_FORMAT)).append("\n\n");

        details.append("DATA MATRICES\n");
        details.append("=============\n");
        List<ExpressionMatrix> matrices = experiment.getMatrices();
        if (matrices.isEmpty()) {
            details.append(experiment.getStoredDataType() == null
                ? "Data type is not recorded. Choose Set Data Type in Expression Data Management before loading this matrix.\n"
                : "No matrices available\n");
        } else {
            for (ExpressionMatrix matrix : matrices) {
                details.append("- ").append(matrix.getName())
                    .append(" (").append(matrix.getDataType().getShortName()).append(")\n");
                details.append("  - Genes: ").append(matrix.getGeneCount()).append("\n");
                details.append("  - Samples: ").append(matrix.getSampleCount()).append("\n");
                details.append("  - Source: ").append(matrix.getSourceFile() != null
                    ? matrix.getSourceFile().getName() : "Unknown").append("\n");
                if (matrix.getNotes() != null && !matrix.getNotes().trim().isEmpty()) {
                    details.append("  - Notes: ").append(matrix.getNotes()).append("\n");
                }
                details.append("\n");
            }
        }

        details.append("SAMPLE INFORMATION\n");
        details.append("==================\n");
        List<String> sampleNames = resolveSampleNames(experiment, selectedMatrixFile);
        if (sampleNames.isEmpty()) {
            details.append("No samples available\n");
        } else {
            details.append("Total samples: ").append(sampleNames.size()).append("\n");
            details.append("Sample names: ").append(formatSampleNames(sampleNames)).append("\n");
        }

        return details.toString();
    }

    static ExpressionOtherDataSummary buildOtherDataSummary(SpeciesInfo species, File selectedMatrixFile) {
        if (species == null || selectedMatrixFile == null) {
            return null;
        }

        List<ExpressionExperiment> experiments = loadExpressionExperiments(species);
        if (experiments.isEmpty()) {
            return null;
        }

        File normalizedSelectedFile = selectedMatrixFile.getAbsoluteFile();
        for (ExpressionExperiment experiment : experiments) {
            ExpressionMatrix selectedMatrix = findMatrixForFile(experiment, normalizedSelectedFile);
            if (selectedMatrix == null) {
                continue;
            }

            return new ExpressionOtherDataSummary(
                experiment.getTitle() != null ? experiment.getTitle() : normalizedSelectedFile.getName(),
                String.valueOf(selectedMatrix.getGeneCount()),
                String.valueOf(selectedMatrix.getSampleCount()),
                formatSampleNames(resolveSampleNames(experiment, normalizedSelectedFile)),
                buildExperimentDetailsText(experiment, normalizedSelectedFile)
            );
        }

        return null;
    }

    private static ExpressionMatrix findMatrixForFile(ExpressionExperiment experiment, File selectedMatrixFile) {
        if (experiment == null || selectedMatrixFile == null) {
            return null;
        }

        File normalizedSelectedFile = selectedMatrixFile.getAbsoluteFile();
        for (ExpressionMatrix matrix : experiment.getMatrices()) {
            File sourceFile = matrix.getSourceFile();
            if (sourceFile != null && sourceFile.getAbsoluteFile().equals(normalizedSelectedFile)) {
                return matrix;
            }
        }
        return null;
    }

    private static List<String> resolveSampleNames(ExpressionExperiment experiment, File selectedMatrixFile) {
        ExpressionMatrix selectedMatrix = findMatrixForFile(experiment, selectedMatrixFile);
        if (selectedMatrix != null && selectedMatrix.getSampleNames() != null) {
            return Arrays.asList(selectedMatrix.getSampleNames());
        }
        return experiment.getSampleNames();
    }

    private static String formatSampleNames(List<String> sampleNames) {
        if (sampleNames == null || sampleNames.isEmpty()) {
            return "No samples available";
        }
        if (sampleNames.size() <= 10) {
            return String.join(", ", sampleNames);
        }
        return String.join(", ", sampleNames.subList(0, 10))
            + "... (and " + (sampleNames.size() - 10) + " more)";
    }

    static final class ExpressionOtherDataSummary {
        private final String name;
        private final String genes;
        private final String totalSamples;
        private final String sampleNames;
        private final String detailsText;

        private ExpressionOtherDataSummary(String name, String genes, String totalSamples,
                                           String sampleNames, String detailsText) {
            this.name = name;
            this.genes = genes;
            this.totalSamples = totalSamples;
            this.sampleNames = sampleNames;
            this.detailsText = detailsText;
        }

        String getName() {
            return name;
        }

        String getGenes() {
            return genes;
        }

        String getTotalSamples() {
            return totalSamples;
        }

        String getSampleNames() {
            return sampleNames;
        }

        String getDetailsText() {
            return detailsText;
        }
    }
}
