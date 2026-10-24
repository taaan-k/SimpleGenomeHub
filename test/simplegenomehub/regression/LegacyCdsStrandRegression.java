package simplegenomehub.regression;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import simplegenomehub.util.fileio.SequenceExtractor;

/** Strand-order regression for the legacy helper, independent of GUI and bundled libraries.
 * Every CDS segment is a multiple of three bases with phase 0. Expected DNA and
 * proteins are literal, manually checkable values, never derived with the helper.
 */
public final class LegacyCdsStrandRegression {
    private static final String LONG_CDS = "ATGAAACCCGGGTTTTAA";
    private static final String SHORT_CDS = "ATGAAATTTTAA";
    private static final String LONG_PROTEIN = "MKPGF*";
    private static final String SHORT_PROTEIN = "MKF*";

    private enum Api {
        CDS(false, false), PROTEINS(false, true), REPRESENTATIVE_CDS(true, false), REPRESENTATIVE_PROTEINS(true, true);
        final boolean representative;
        final boolean protein;
        Api(boolean representative, boolean protein) { this.representative = representative; this.protein = protein; }
        String id(String transcript) { return this == REPRESENTATIVE_CDS ? transcript + "_cds" : transcript; }
        SequenceExtractor.ExtractionResult extract(Path genome, Path annotation, Path output) {
            switch (this) {
                case CDS: return SequenceExtractor.extractCDS(genome.toFile(), annotation.toFile(), output.toFile());
                case PROTEINS: return SequenceExtractor.extractProteins(genome.toFile(), annotation.toFile(), output.toFile());
                case REPRESENTATIVE_CDS: return SequenceExtractor.extractRepresentativeCDS(genome.toFile(), annotation.toFile(), output.toFile(), "LONGEST_CDS");
                default: return SequenceExtractor.extractRepresentativeProteins(genome.toFile(), annotation.toFile(), output.toFile(), "LONGEST_CDS");
            }
        }
    }

    private static void check(boolean condition, String message, List<String> failures) {
        if (!condition) { failures.add(message); System.out.println("FAIL " + message); }
    }

    private static Map<String, String> readFasta(Path file) throws IOException {
        Map<String, String> records = new LinkedHashMap<>();
        String id = null;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith(">")) {
                id = line.substring(1).split("\\s+", 2)[0];
                if (records.put(id, "") != null) throw new IOException("Duplicate FASTA ID: " + id);
            } else if (!line.trim().isEmpty()) {
                if (id == null) throw new IOException("Sequence before FASTA header in " + file);
                records.put(id, records.get(id) + line.trim());
            }
        }
        return records;
    }

    /** Reverse CDS feature order only; preserve parent declarations before child features.
     * This isolates strand assembly from the selector's unrelated GFF hierarchy assumptions.
     */
    private static Path reverseCdsRows(Path source, Path output) throws IOException {
        List<String> rows = new ArrayList<>();
        List<String> cds = new ArrayList<>();
        for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
            String[] fields = line.split("\t", -1);
            if (fields.length >= 3 && "CDS".equals(fields[2])) cds.add(line);
            else rows.add(line);
        }
        Collections.reverse(cds);
        rows.addAll(cds);
        Files.write(output, rows, StandardCharsets.UTF_8);
        return output;
    }

    private static void verify(Api api, String order, Path genome, Path annotation, Path outputDirectory,
                               List<String> failures) {
        String label = order + " " + api;
        Path output = outputDirectory.resolve(order + "-" + api + ".fa");
        try {
            SequenceExtractor.ExtractionResult result = api.extract(genome, annotation, output);
            check(result.isSuccess(), label + " extraction failed: " + result.getMessage(), failures);
            Map<String, String> actual = readFasta(output);
            Map<String, String> expected = new LinkedHashMap<>();
            for (String id : new String[]{"pSingle", "mSingle", "pLong", "mLong"})
                expected.put(api.id(id), api.protein ? LONG_PROTEIN : LONG_CDS);
            if (!api.representative) {
                expected.put(api.id("pShort"), api.protein ? SHORT_PROTEIN : SHORT_CDS);
                expected.put(api.id("mShort"), api.protein ? SHORT_PROTEIN : SHORT_CDS);
            }
            check(actual.keySet().equals(expected.keySet()), label + " IDs expected=" + expected.keySet() + " actual=" + actual.keySet(), failures);
            check(result.getSequencesExtracted() == expected.size(), label + " reported incorrect sequence count", failures);
            for (Map.Entry<String, String> entry : expected.entrySet()) {
                String observed = actual.get(entry.getKey());
                boolean equal = entry.getValue().equals(observed);
                check(equal, label + " " + entry.getKey() + " expected=" + entry.getValue() + " actual=" + observed, failures);
                if (equal) System.out.println("PASS " + label + " " + entry.getKey() + "=" + observed);
            }
            if (api.representative) {
                // Each multi-CDS gene has real long and short isoforms in the input.
                check(actual.containsKey(api.id("pLong")) && actual.containsKey(api.id("mLong"))
                    && !actual.containsKey(api.id("pShort")) && !actual.containsKey(api.id("mShort")),
                    label + " did not select only the longest CDS isoform for both strands", failures);
            }
        } catch (Exception error) {
            failures.add(label + " exception: " + error);
            System.out.println("FAIL " + label + " exception: " + error);
        }
    }

    public static void main(String[] args) throws Exception {
        Path fixtures = Path.of(System.getProperty("legacy.fixtures", "test/fixtures/legacy-cds")).toAbsolutePath();
        Path output = Files.createTempDirectory("sgh-legacy-cds-regression-");
        System.out.println("Legacy CDS fixtures: " + fixtures);
        System.out.println("Legacy CDS output: " + output);
        List<String> failures = new ArrayList<>();
        Path annotation = fixtures.resolve("annotation.gff3");
        Path reversed = reverseCdsRows(annotation, output.resolve("annotation-reversed-cds.gff3"));
        Path genome = fixtures.resolve("genome.fa");
        for (Api api : Api.values()) verify(api, "forward-rows", genome, annotation, output, failures);
        for (Api api : Api.values()) verify(api, "reversed-rows", genome, reversed, output, failures);
        if (!failures.isEmpty()) throw new AssertionError(failures.size() + " legacy CDS checks failed:\n" + String.join("\n", failures));
        System.out.println("PASS legacy CDS: four APIs, both strands, single/multiple CDS, reversed CDS rows, and longest isoform selection");
    }
}
