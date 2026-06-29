package simplegenomehub.util.fileio;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Writes Highlight.info.txt.
 */
final class MultipleSyntenyHighlightInfoWriter {

    private MultipleSyntenyHighlightInfoWriter() {
    }

    static void write(File outputFile,
                      List<MultipleSyntenyBatchContext.HighlightInfoEntry> highlightEntries) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("#format=SGH.MultipleSynteny.HighlightInfo.v1");
            writer.newLine();
            writer.write("gene_id\tgenome_id\tchr_name\tstart\tend\tcolor\tlabel");
            writer.newLine();

            if (highlightEntries == null) {
                return;
            }

            for (MultipleSyntenyBatchContext.HighlightInfoEntry entry : highlightEntries) {
                if (entry == null) {
                    continue;
                }
                writer.write(entry.getGeneId());
                writer.write('\t');
                writer.write(entry.getGenomeId());
                writer.write('\t');
                writer.write(entry.getChromosomeName());
                writer.write('\t');
                writer.write(String.valueOf(entry.getStart()));
                writer.write('\t');
                writer.write(String.valueOf(entry.getEnd()));
                writer.write('\t');
                writer.write(entry.getColor());
                writer.write('\t');
                writer.write(entry.getLabel());
                writer.newLine();
            }
        }
    }
}
