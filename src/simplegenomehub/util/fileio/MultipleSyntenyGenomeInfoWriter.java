package simplegenomehub.util.fileio;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Writes Genome.info.txt.
 */
final class MultipleSyntenyGenomeInfoWriter {

    private MultipleSyntenyGenomeInfoWriter() {
    }

    static void write(File outputFile, MultipleSyntenyBatchContext context) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("#format=SGH.MultipleSynteny.GenomeInfo.v1");
            writer.newLine();
            writer.write("[Genome]");
            writer.newLine();
            writer.write("genome_id\tdisplay_name\tz_order\tleft_bottom_x\tleft_bottom_y\trect_width\trect_height\trotation_deg");
            writer.newLine();

            for (MultipleSyntenyService.GenomeLayout genomeLayout : context.getGenomeLayouts()) {
                if (genomeLayout == null) {
                    continue;
                }
                writer.write(safe(genomeLayout.getGenomeId()));
                writer.write('\t');
                writer.write(safe(genomeLayout.getDisplayName()));
                writer.write('\t');
                writer.write("z" + Math.max(1, genomeLayout.getZOrder()));
                writer.write('\t');
                writer.write(formatCoordinate(genomeLayout.getLeftBottomX()));
                writer.write('\t');
                writer.write(formatCoordinate(genomeLayout.getLeftBottomY()));
                writer.write('\t');
                writer.write(String.valueOf(Math.max(1, genomeLayout.getRectWidth())));
                writer.write('\t');
                writer.write(String.valueOf(Math.max(1, genomeLayout.getRectHeight())));
                writer.write('\t');
                writer.write(String.valueOf(genomeLayout.getRotationDeg()));
                writer.newLine();
            }

            writer.newLine();
            writer.write("[Chromosome]");
            writer.newLine();
            writer.write("genome_id\tchr_order\tchr_name\tdisplay_start\tdisplay_end\tbp_length");
            writer.newLine();

            for (MultipleSyntenyService.GenomeLayout genomeLayout : context.getGenomeLayouts()) {
                if (genomeLayout == null) {
                    continue;
                }
                for (MultipleSyntenyService.ChromosomeLayout chromosomeLayout : genomeLayout.getChromosomes()) {
                    if (chromosomeLayout == null) {
                        continue;
                    }
                    writer.write(safe(genomeLayout.getGenomeId()));
                    writer.write('\t');
                    writer.write(String.valueOf(Math.max(1, chromosomeLayout.getChromosomeOrder())));
                    writer.write('\t');
                    writer.write(safe(chromosomeLayout.getChromosomeName()));
                    writer.write('\t');
                    writer.write(String.valueOf(Math.max(0, chromosomeLayout.getDisplayStart())));
                    writer.write('\t');
                    writer.write(String.valueOf(Math.max(0, chromosomeLayout.getDisplayEnd())));
                    writer.write('\t');
                    writer.write(String.valueOf(Math.max(1L, chromosomeLayout.getBpLength())));
                    writer.newLine();
                }
            }
        }
    }

    private static String formatCoordinate(double value) {
        if (Math.rint(value) == value) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
