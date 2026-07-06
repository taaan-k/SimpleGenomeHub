package simplegenomehub.util.fileio;

import simplegenomehub.model.GenomeData;
import simplegenomehub.model.SpeciesInfo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates the directory and template files required by TBtools Advanced Circos.
 */
public final class AdvancedCircosTemplateGenerator {

    private static final DateTimeFormatter OUTPUT_DIR_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-M-d-H-m");

    private static final String OTHER_PARA_TEMPLATE =
        "Cirlize:\ttrue\n" +
        "Graph Width:\t1200\n" +
        "Graph Height:\t1200\n" +
        "LeftUp Space:\t50\n" +
        "Start Angle:\t0\n" +
        "End Angle:\t360\n" +
        "ChrBar StartPos:\t80\n" +
        "ChrBar EndPos:\t90\n" +
        "ChrLabel Pos:\t85\n" +
        "Interval Ratio:\t50\n" +
        "ChrBorder Color:\t0,0,0\n" +
        "ChrFill Color:\t:240,240,240\n" +
        "Label StartPos:\t90\n" +
        "Label EndPos:\t95\n" +
        "Label Color:\t64,64,64\n" +
        "Label Font:\tArial,0,14\n" +
        "ShowLabel:\ttrue\n" +
        "Text Spread Mode:\tTogether\n" +
        "ChrLabel Color:\t255,200,0\n" +
        "ChrLabel Font:\tArial,1,18\n" +
        "ChrLabel Type:\tTextPolarOut\n" +
        "ChrBorder Size:\t1.0\n" +
        "Link Pos:\t79\n" +
        "Link Stroke:\t2.0\n" +
        "Reduce Obj:\ttrue\n" +
        "Hide Obj:\tfalse\n" +
        "Tick Color:\t255,0,0\n" +
        "MajorTick Len:\t20\n" +
        "MajorTick Interval:\t10000000\n" +
        "MinorTick Len:\t10\n" +
        "MinorTick Interval:\t2000000\n" +
        "Tick Text Space:\t12\n" +
        "Overlap Weight:\t-7.0\n" +
        "Median Mean:\tfalse\n" +
        "Quantile Scale:\tfalse\n" +
        "Legend Decimal Format:\t0.00\n" +
        "Show Chr Label:true\n" +
        "Show Chr Bar:\ttrue\n" +
        "Show Interval Tick:\ttrue\n" +
        "Link Seg Color:\t64,64,64\n";

    private AdvancedCircosTemplateGenerator() {
    }

    public static File generateTemplate(SpeciesInfo species) throws IOException {
        if (species == null) {
            throw new IllegalArgumentException("Species cannot be null.");
        }
        if (species.getGenomeAnalysisDir() == null) {
            throw new IOException("GenomeAnalysis directory is not available.");
        }

        File advanceCircosRoot = new File(species.getGenomeAnalysisDir(), "AdvanceCircos");
        ensureDirectory(advanceCircosRoot);

        File outputDir = new File(advanceCircosRoot, OUTPUT_DIR_FORMAT.format(LocalDateTime.now()));
        ensureDirectory(outputDir);

        writeChrInfoFile(new File(outputDir, "ChrInfo.tab"), species.getGenomeData());
        writeTextFile(new File(outputDir, "TrackInfo.tab"), "");
        writeTextFile(new File(outputDir, "OtherPara.tab"), OTHER_PARA_TEMPLATE);

        ensureDirectory(new File(outputDir, CircosTrackGenerator.WORK_DIR_NAME));
        ensureDirectory(new File(outputDir, "TrakFile.Cache"));
        File otherFileDir = new File(outputDir, "OtherFile");
        ensureDirectory(otherFileDir);
        ensureDirectory(new File(otherFileDir, "LinkFile"));

        return outputDir;
    }

    private static void writeChrInfoFile(File targetFile, GenomeData genomeData) throws IOException {
        if (genomeData == null) {
            throw new IOException("Genome statistics are not available.");
        }

        List<GenomeData.ChromosomeStat> chromosomeStats = genomeData.getChromosomeStats();
        if (chromosomeStats.isEmpty()) {
            throw new IOException("Chromosome statistics are not available.");
        }

        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            for (GenomeData.ChromosomeStat chromosomeStat : chromosomeStats) {
                writer.write(chromosomeStat.getName());
                writer.write('\t');
                writer.write(String.valueOf(chromosomeStat.getSize()));
                writer.write('\t');
                writer.write("255,255,255");
                writer.newLine();
            }
        }
    }

    private static void writeTextFile(File targetFile, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            writer.write(content != null ? content : "");
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IOException("Path exists but is not a directory: " + directory.getAbsolutePath());
            }
            return;
        }

        if (!directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }
}
