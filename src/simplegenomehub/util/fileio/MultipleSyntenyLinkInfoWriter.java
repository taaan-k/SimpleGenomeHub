package simplegenomehub.util.fileio;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * Writes Link.info.txt.
 */
final class MultipleSyntenyLinkInfoWriter {

    private MultipleSyntenyLinkInfoWriter() {
    }

    static void write(File outputFile,
                      List<MultipleSyntenyBatchContext.LinkInfoEntry> linkEntries) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("#format=SGH.MultipleSynteny.LinkInfo.v1");
            writer.newLine();
            writer.write("genome1_id\tchr1\tstart1\tend1\tgenome2_id\tchr2\tstart2\tend2\tlink_type\tedge1\tedge2\tbend_value\tbulge_dir\tcolor\talpha\tz_order");
            writer.newLine();

            if (linkEntries == null) {
                return;
            }

            for (MultipleSyntenyBatchContext.LinkInfoEntry entry : linkEntries) {
                if (entry == null) {
                    continue;
                }
                writer.write(entry.getGenome1Id());
                writer.write('\t');
                writer.write(entry.getChr1());
                writer.write('\t');
                writer.write(String.valueOf(entry.getStart1()));
                writer.write('\t');
                writer.write(String.valueOf(entry.getEnd1()));
                writer.write('\t');
                writer.write(entry.getGenome2Id());
                writer.write('\t');
                writer.write(entry.getChr2());
                writer.write('\t');
                writer.write(String.valueOf(entry.getStart2()));
                writer.write('\t');
                writer.write(String.valueOf(entry.getEnd2()));
                writer.write('\t');
                writer.write(entry.getLinkType());
                writer.write('\t');
                writer.write(entry.getEdge1());
                writer.write('\t');
                writer.write(entry.getEdge2());
                writer.write('\t');
                writer.write(String.valueOf(entry.getBendValue()));
                writer.write('\t');
                writer.write(entry.getBulgeDir());
                writer.write('\t');
                writer.write(entry.getColor());
                writer.write('\t');
                writer.write(formatDouble(entry.getAlpha()));
                writer.write('\t');
                writer.write(String.valueOf(entry.getZOrder()));
                writer.newLine();
            }
        }
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
