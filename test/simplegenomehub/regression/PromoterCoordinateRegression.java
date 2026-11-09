package simplegenomehub.regression;

import java.nio.file.*;
import java.util.*;
import simplegenomehub.util.fileio.SequenceExtractor;

/** Literal bases and genomic bounds; includes nested CDS where sorting alone is insufficient. */
public final class PromoterCoordinateRegression {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sgh-promoter-");
        Path genome = root.resolve("genome.fa"), annotation = root.resolve("features.gff3");
        Files.writeString(genome, ">chr1\nACGTTGCAAGTCCTAGGATCCGTATGACCTAGTCGATACCGTTAAGCGATCCGATGCTAACGTTAGGCCATAGTCGACTAGCATCGGATCCTAACGTTGC\n");
        List<String> cds = new ArrayList<>(Arrays.asList(
            row("positive", 10, 20, "+"), row("positive", 40, 50, "+"),
            row("negative", 10, 20, "-"), row("negative", 40, 50, "-"),
            row("nested", 10, 80, "-"), row("nested", 40, 50, "-"),
            row("leftEdge", 3, 10, "+"), row("rightEdge", 90, 97, "-")));
        for (int order = 0; order < 2; order++) {
            if (order == 1) Collections.reverse(cds);
            Files.write(annotation, cds);
            Path output = root.resolve("promoters-" + order + ".fa");
            if (!SequenceExtractor.extractPromoters(genome.toFile(), annotation.toFile(), output.toFile(), 5).isSuccess())
                throw new AssertionError("Promoter extraction failed");
            Map<String, String> bases = new HashMap<>(), headers = new HashMap<>();
            String id = null;
            for (String line : Files.readAllLines(output)) {
                if (line.startsWith(">")) { id = line.substring(1).split(" ", 2)[0]; headers.put(id, line); }
                else if (!line.isEmpty()) bases.put(id, line);
            }
            check(bases, headers, "positive", "TGCAA", "5-9");
            check(bases, headers, "negative", "ATCGG", "51-55");
            check(bases, headers, "nested", "GATGC", "81-85");
            check(bases, headers, "leftEdge", "AC", "1-2");
            check(bases, headers, "rightEdge", "GCA", "98-100");
        }
        System.out.println("PASS promoter anchors: literal positive/negative, nested CDS, reversed input order and chromosome bounds");
    }
    private static String row(String gene, int start, int end, String strand) {
        return "chr1\ttest\tCDS\t" + start + "\t" + end + "\t.\t" + strand + "\t0\tID=" + gene + start + ";Parent=" + gene;
    }
    private static void check(Map<String,String> bases, Map<String,String> headers, String gene, String expected, String bounds) {
        if (!expected.equals(bases.get(gene)) || !headers.getOrDefault(gene, "").contains("region=" + bounds))
            throw new AssertionError(gene + " expected=" + expected + " " + bounds + ", actual=" + bases.get(gene) + " " + headers.get(gene));
    }
}
