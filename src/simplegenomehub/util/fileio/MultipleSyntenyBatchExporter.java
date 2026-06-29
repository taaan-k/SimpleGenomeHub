package simplegenomehub.util.fileio;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the Multiple Synteny batch text files.
 */
final class MultipleSyntenyBatchExporter {

    private MultipleSyntenyBatchExporter() {
    }

    static void export(File outputDir,
                       MultipleSyntenyService.RunRequest runRequest,
                       List<MultipleSyntenyService.PairResult> pairResults,
                       MultipleSyntenyService.ProgressListener progressListener) throws Exception {
        if (outputDir == null || !outputDir.isDirectory()) {
            throw new IllegalArgumentException("Multiple Synteny batch output directory is required.");
        }

        MultipleSyntenyBatchContext context = new MultipleSyntenyBatchContext(runRequest, pairResults);
        List<MultipleSyntenyBatchContext.LinkInfoEntry> linkEntries = new ArrayList<>();
        for (MultipleSyntenyService.PairResult pairResult : context.getPairResults()) {
            report(progressListener, "Parsing pairwise result: " + pairResult.getDisplayLabel());
            linkEntries.addAll(MultipleSyntenyPairwiseResultParser.parse(pairResult, context));
        }

        report(progressListener, "Resolving highlight gene positions...");
        List<MultipleSyntenyBatchContext.HighlightInfoEntry> highlightEntries =
            MultipleSyntenyHighlightResolver.resolve(context);

        File genomeInfoFile = new File(outputDir, "Genome.info.txt");
        File linkInfoFile = new File(outputDir, "Link.info.txt");
        File highlightInfoFile = new File(outputDir, "Highlight.info.txt");
        File highlightLinkInfoFile = new File(outputDir, "Highlight.Link.info.txt");
        File renderSettingsFile = new File(outputDir, "Render.settings.txt");

        report(progressListener, "Writing Genome.info.txt...");
        MultipleSyntenyGenomeInfoWriter.write(genomeInfoFile, context);
        report(progressListener, "Writing Link.info.txt...");
        MultipleSyntenyLinkInfoWriter.write(linkInfoFile, linkEntries);
        report(progressListener, "Writing Highlight.info.txt...");
        MultipleSyntenyHighlightInfoWriter.write(highlightInfoFile, highlightEntries);
        report(progressListener, "Writing Render.settings.txt...");
        MultipleSyntenyRenderSettingsWriter.write(renderSettingsFile, context.getGlobalSettings());
        report(progressListener, "Writing Highlight.Link.info.txt...");
        MultipleSyntenyHighlightLinkInfoWriter.write(highlightLinkInfoFile, linkInfoFile, highlightInfoFile);

        verifyOutputFile(genomeInfoFile);
        verifyOutputFile(linkInfoFile);
        verifyOutputFile(highlightInfoFile);
        verifyOutputFile(highlightLinkInfoFile);
        verifyOutputFile(renderSettingsFile);
    }

    private static void report(MultipleSyntenyService.ProgressListener progressListener, String message) {
        if (progressListener != null) {
            progressListener.onProgress(message);
        }
    }

    private static void verifyOutputFile(File outputFile) throws IOException {
        if (outputFile == null || !outputFile.isFile()) {
            throw new IOException("Missing expected Multiple Synteny output file: "
                + (outputFile == null ? "" : outputFile.getAbsolutePath()));
        }
    }
}
