package simplegenomehub.regression;

import com.google.gson.JsonObject;
import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.logging.*;
import javax.swing.*;
import simplegenomehub.gui.GeneDataRetriever;
import simplegenomehub.gui.GeneSearchResult;
import simplegenomehub.model.*;
import simplegenomehub.model.ExpressionExperimentLoader.IssueKind;
import simplegenomehub.model.ExpressionExperimentLoader.LoadResult;
import simplegenomehub.util.fileio.AtomicFiles;
import simplegenomehub.util.fileio.FileSetTransaction;
import simplegenomehub.util.fileio.JsonMetadataSupport;

/** Actual metadata/matrix/query/UI integration; all writes use isolated fixtures. */
public final class ExpressionLoadingRegression {

    private static final String GENE = "lcfv2_09070";

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sgh-expression-regression-");
        encoding(root.resolve("encoding"));
        SpeciesInfo species = species(root.resolve("legacy"));
        Path first = experiment(species, "old1", Charset.forName("GBK"), null, "2569.59");
        Path second = experiment(species, "old2", Charset.forName("GBK"), null, "2456.48");
        byte[] firstBytes = Files.readAllBytes(first), secondBytes = Files.readAllBytes(second);
        Path matrix = first.resolveSibling("old1.matrix.tsv");
        byte[] matrixBytes = Files.readAllBytes(matrix);

        LoadResult legacy = ExpressionExperimentLoader.load(species);
        check(legacy.getExperiments().size() == 2 && legacy.getReadyMatrixCount() == 0, "Legacy experiments are discovered without guessing a type");
        check(legacy.getIssues().size() == 2 && has(legacy, IssueKind.DATA_TYPE_REQUIRED), "Missing types remain actionable");
        check(legacy.getDetails().contains("Set Expression Data Type"), "Type selection guidance");
        check(Arrays.equals(firstBytes, Files.readAllBytes(first)) && Arrays.equals(secondBytes, Files.readAllBytes(second)), "Opening legacy metadata must not rewrite it");
        check(ExpressionExperimentLoader.load(species) == legacy, "Unchanged snapshots reuse parsed objects");

        List<String> messages = new ArrayList<>();
        Handler capture = new Handler() {
            @Override public void publish(LogRecord record) { messages.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        Logger queryLog = Logger.getLogger(GeneDataRetriever.class.getName());
        queryLog.addHandler(capture);
        GeneDataRetriever retriever = new GeneDataRetriever();
        try { check(retriever.getGeneExpressionData(GENE, species).isEmpty(), "Unconfirmed legacy types cannot be queried"); }
        finally { queryLog.removeHandler(capture); }
        check(messages.stream().noneMatch(message -> message.contains("Trying transcript ID variations")), "No ID fallback without queryable matrices");

        // The fixture's original units are known. Confirmation changes metadata only.
        ExpressionExperiment.loadMetadataChecked(first.toFile()).saveExplicitDataType(ExpressionMatrix.DataType.TPM);
        LoadResult confirmed = ExpressionExperimentLoader.load(species);
        check(confirmed != legacy && confirmed.getReadyMatrixCount() == 1, "Type confirmation invalidates stale experiments even through the old API");
        check(retriever.getGeneExpressionData(GENE, species).size() == 1, "A query immediately sees the confirmed type");
        check(Arrays.equals(matrixBytes, Files.readAllBytes(matrix)), "Type confirmation does not convert matrix values");
        JsonObject saved = JsonMetadataSupport.read(first.toFile());
        check(saved.get("title").getAsString().equals("中文实验 old1") && saved.getAsJsonObject("future").get("kept").getAsInt() == 42, "Save preserves Chinese and unknown fields");
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(first)));
        ExpressionExperiment.loadMetadataChecked(second.toFile()).saveExplicitDataType(ExpressionMatrix.DataType.TPM);
        LoadResult ready = ExpressionExperimentLoader.load(species);
        check(ready.getReadyMatrixCount() == 2 && ready.getIssues().isEmpty(), "Both confirmed legacy matrices load");
        near(2569.59, ready.getExperiments().get(0).getMatrices().get(0).getGeneExpression(GENE).get("Root"), "First experiment value");
        near(2456.48, ready.getExperiments().get(1).getMatrices().get(0).getGeneExpression(GENE).get("Root"), "Second experiment value");
        check(Arrays.equals(new String[]{"Root", "Shoot"}, ready.getExperiments().get(0).getMatrices().get(0).getSampleNames()), "Sample names");
        check(retriever.getGeneExpressionData(GENE, species).size() == 2, "Gene query uses both experiments");
        check(retriever.getGeneExpressionData("not_a_gene", species).isEmpty(), "Missing gene is separate from unavailable matrices");
        ui(species, retriever, legacy, ready, root.resolve("export"));
        partial(species, retriever);
        cache(root.resolve("cache"));
        publication(root.resolve("publication"));
        writeFailure(root.resolve("save-failure"));
        System.out.println("PASS expression loading: strict UTF-8/legacy encoding, types, unchanged files, partial errors, cache freshness, publication, query and UI export");
    }

    private static void encoding(Path root) throws Exception {
        Files.createDirectories(root);
        Path utf8 = root.resolve("utf8.json"), gbk = root.resolve("gbk.json");
        String text = "{\"title\":\"中文实验\",\"future\":{\"kept\":42}}";
        Files.write(utf8, text.getBytes(StandardCharsets.UTF_8));
        Files.write(gbk, text.getBytes(Charset.forName("GBK")));
        String nativeEncoding = System.getProperty("native.encoding");
        try {
            // Java 11 can lack native.encoding; Java 26 defaults to UTF-8 on Windows.
            System.setProperty("native.encoding", "not-an-installed-charset");
            check(JsonMetadataSupport.read(gbk.toFile()).get("title").getAsString().equals("中文实验"), "Explicit GB18030 fallback works independently of the native charset");
            System.setProperty("native.encoding", "GBK");
            check(JsonMetadataSupport.read(gbk.toFile()).get("title").getAsString().equals("中文实验"), "Native charset fallback");
            check(JsonMetadataSupport.read(utf8.toFile()).get("title").getAsString().equals("中文实验"), "UTF-8 retains priority over native GBK");
            Files.write(root.resolve("bom.json"), ("\ufeff" + text).getBytes(StandardCharsets.UTF_8));
            check(JsonMetadataSupport.read(root.resolve("bom.json").toFile()).has("future"), "UTF-8 BOM");
            for (String broken : new String[]{"{\"title\":\"x\",}", "{title:\"x\"}", "{} {}", "[]", "{\"x\":NaN}"}) {
                Path invalid = root.resolve("broken.json");
                Files.write(invalid, broken.getBytes(StandardCharsets.UTF_8));
                try { JsonMetadataSupport.read(invalid.toFile()); throw new AssertionError("Invalid JSON accepted: " + broken); }
                catch (JsonMetadataSupport.MetadataReadException expected) {
                    check(expected.getKind() == JsonMetadataSupport.FailureKind.JSON && expected.getMessage().contains("UTF-8"), "Syntax errors are not encoding fallbacks");
                }
            }
            Path invalidEncoding = root.resolve("broken-encoding.json");
            Files.write(invalidEncoding, new byte[]{(byte)0x81});
            try { JsonMetadataSupport.read(invalidEncoding.toFile()); throw new AssertionError("Incomplete encoded bytes accepted"); }
            catch (JsonMetadataSupport.MetadataReadException expected) { check(expected.getKind() == JsonMetadataSupport.FailureKind.ENCODING, "Encoding errors stay distinct"); }
        } finally {
            if (nativeEncoding == null) System.clearProperty("native.encoding");
            else System.setProperty("native.encoding", nativeEncoding);
        }
    }

    private static void partial(SpeciesInfo species, GeneDataRetriever retriever) throws Exception {
        Path directory = species.getExpressionDir().toPath();
        Files.writeString(directory.resolve("broken.metadata.json"), "{broken}");
        Path missing = experiment(species, "missing", StandardCharsets.UTF_8, "TPM", "1");
        Files.delete(missing.resolveSibling("missing.matrix.tsv"));
        Path invalid = experiment(species, "invalid", StandardCharsets.UTF_8, "TPM", "1");
        Files.writeString(invalid.resolveSibling("invalid.matrix.tsv"), "Gene_ID\tRoot\n" + GENE + "\tinvalid-number\n");
        Files.writeString(directory.resolve("orphan.matrix.tsv"), "Gene_ID\tRoot\n" + GENE + "\t1\n");
        LoadResult loaded = ExpressionExperimentLoader.load(species);
        check(loaded.getReadyMatrixCount() == 2 && has(loaded, IssueKind.INVALID_METADATA)
            && has(loaded, IssueKind.MATRIX_MISSING) && has(loaded, IssueKind.MATRIX_INVALID)
            && has(loaded, IssueKind.METADATA_MISSING), "Per-file errors preserve valid experiments");
        check(retriever.getGeneExpressionData(GENE, species).size() == 2, "Partial failure still returns valid expression values");
    }

    private static void cache(Path root) throws Exception {
        SpeciesInfo left = species(root.resolve("left")), right = species(root.resolve("right"));
        Path metadata = experiment(left, "same", StandardCharsets.UTF_8, "TPM", "10");
        experiment(right, "same", StandardCharsets.UTF_8, "TPM", "20");
        LoadResult first = ExpressionExperimentLoader.load(left);
        near(10, value(first), "Left data root"); near(20, value(ExpressionExperimentLoader.load(right)), "Different root with identical genome and experiment names");
        FileTime stamp = Files.getLastModifiedTime(metadata);
        String old = Files.readString(metadata), changed = old.replace("中文实验", "测试实验");
        check(old.length() == changed.length(), "Same-length cache fixture");
        Files.writeString(metadata, changed); Files.setLastModifiedTime(metadata, stamp);
        LoadResult refreshed = ExpressionExperimentLoader.load(left);
        check(refreshed != first && refreshed.getExperiments().get(0).getTitle().startsWith("测试"), "Metadata content changes invalidate cache even with equal size and timestamp");
        Path matrix = metadata.resolveSibling("same.matrix.tsv");
        AtomicFiles.write(matrix, writer -> writer.write("Gene_ID\tRoot\tShoot\n" + GENE + "\t30\t1\n"));
        near(30, value(ExpressionExperimentLoader.load(left)), "Matrix replacement invalidates cache");
        left.initializeFileStructure(root.resolve("right").toFile());
        near(20, value(ExpressionExperimentLoader.load(left)), "Rebinding the same model to another root isolates cache");
        FileSetTransaction.commit(Collections.emptyMap(), Arrays.asList(metadata, matrix));
        check(ExpressionExperimentLoader.load(species(root.resolve("left"))).getExperiments().isEmpty(), "Deleted experiments disappear");
        Path inaccessible = root.resolve("unreadable");
        SpeciesInfo unavailable = species(inaccessible);
        Files.delete(unavailable.getExpressionDir().toPath()); Files.writeString(unavailable.getExpressionDir().toPath(), "not a directory");
        check(has(ExpressionExperimentLoader.load(unavailable), IssueKind.DIRECTORY_UNREADABLE), "Directory failures are not reported as no imported data");
    }

    private static void publication(Path root) throws Exception {
        SpeciesInfo species = species(root);
        Path meta = experiment(species, "pending", StandardCharsets.UTF_8, "TPM", "5");
        Path journal = Files.createDirectory(meta.getParent().resolve(".sgh-transaction-fixture"));
        Path manifest = journal.resolve("manifest.properties");
        Files.writeString(manifest, "state=PREPARED\ncount=2\ntarget.0=pending.metadata.json\nexisted.0=true\ntarget.1=pending.matrix.tsv\nexisted.1=true\n");
        byte[] before = Files.readAllBytes(manifest);
        LoadResult hidden = ExpressionExperimentLoader.load(species);
        check(hidden.getExperiments().isEmpty() && has(hidden, IssueKind.UNFINISHED_PUBLICATION), "Gene queries also hide unpublished pairs");
        check(Arrays.equals(before, Files.readAllBytes(manifest)), "Loading never recovers or rewrites a transaction");
        FileSetTransaction.recover(meta.getParent());
        check(ExpressionExperimentLoader.load(species).getReadyMatrixCount() == 1, "Recovered publication becomes available without reopening Hub");
    }

    private static void writeFailure(Path root) throws Exception {
        SpeciesInfo species = species(root);
        Path metadata = experiment(species, "protected", StandardCharsets.UTF_8, null, "1");
        ExpressionExperiment experiment = ExpressionExperiment.loadMetadataChecked(metadata.toFile());
        byte[] before = Files.readAllBytes(metadata);
        // A colliding lock directory gives a deterministic failure on both Windows and Unix.
        Path lock = metadata.resolveSibling(metadata.getFileName() + ".lock");
        Files.createDirectory(lock);
        try { experiment.saveExplicitDataType(ExpressionMatrix.DataType.TPM); throw new AssertionError("Failed save reported success"); }
        catch (java.io.IOException expected) {
            check(Arrays.equals(before, Files.readAllBytes(metadata)) && experiment.getStoredDataType() == null, "Write failure retains both original file and in-memory type");
        }
    }

    private static void ui(SpeciesInfo species, GeneDataRetriever retriever, LoadResult legacy,
                           LoadResult ready, Path output) throws Exception {
        Files.createDirectories(output);
        GeneSearchResult result = retriever.searchGeneCore(GENE, species);
        check(result.getExpressionLoadResult().getReadyMatrixCount() == 2 && result.hasExpressionData(), "Core result carries loading diagnostics");
        Class<?> type = Class.forName("simplegenomehub.gui.GeneInfoViewerDialog$ExpressionVisualizationPanel");
        Constructor<?> constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        Method load = type.getDeclaredMethod("loadGeneData", GeneSearchResult.class); load.setAccessible(true);
        Method export = type.getDeclaredMethod("exportData", File.class, String.class); export.setAccessible(true);
        Field current = type.getDeclaredField("currentResult"); current.setAccessible(true);
        Field tabs = type.getDeclaredField("experimentTabs"); tabs.setAccessible(true);
        Field manage = type.getDeclaredField("manageExpressionButton"); manage.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                Object panel = constructor.newInstance();
                load.invoke(panel, result);
                check(current.get(panel) == result, "Rendering must not clear the result required by expression export");
                check(((JTabbedPane)tabs.get(panel)).getTabCount() == 2, "Both experiments render");
                export.invoke(panel, output.toFile(), "gene");
                GeneSearchResult unconfirmed = new GeneSearchResult(GENE, species);
                unconfirmed.setExpressionLoadResult(legacy);
                load.invoke(panel, unconfirmed);
                check(((JButton)manage.get(panel)).isEnabled(), "Missing types offer Expression Data Management");
                JTabbedPane tabbed = (JTabbedPane)tabs.get(panel);
                JTextArea details = (JTextArea)((JScrollPane)tabbed.getComponentAt(0)).getViewport().getView();
                check(details.getText().contains("Set Expression Data Type"), "Gene viewer explains missing types rather than claiming the gene has no expression");
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
        String exported = Files.readString(output.resolve("gene_expression.tsv"));
        check(exported.contains("2569.59") && exported.contains("2456.48"), "Expression export retains both experiments and exact source values");
    }

    private static SpeciesInfo species(Path root) throws Exception {
        Files.createDirectories(root);
        SpeciesInfo species = new SpeciesInfo("Pineapple", "v2");
        species.initializeFileStructure(root.toFile());
        Files.createDirectories(species.getExpressionDir().toPath());
        return species;
    }
    private static Path experiment(SpeciesInfo species, String id, Charset encoding, String dataType, String value) throws Exception {
        Path directory = species.getExpressionDir().toPath();
        Path meta = directory.resolve(id + ".metadata.json");
        String json = "{\"experiment_id\":\"" + id + "\",\"title\":\"中文实验 " + id + "\",\"future\":{\"kept\":42}"
            + (dataType != null ? ",\"data_type\":\"" + dataType + "\"" : "") + "}";
        Files.write(meta, json.getBytes(encoding));
        Files.writeString(directory.resolve(id + ".matrix.tsv"), "Gene_ID\tRoot\tShoot\n" + GENE + "\t" + value + "\t1489.55\notherGene\t0\t2\n");
        return meta;
    }
    private static boolean has(LoadResult result, IssueKind kind) { return result.getIssues().stream().anyMatch(issue -> issue.getKind() == kind); }
    private static double value(LoadResult result) { return result.getExperiments().get(0).getMatrices().get(0).getGeneExpression(GENE).get("Root"); }
    private static void near(double expected, double actual, String label) { check(Math.abs(expected - actual) < 0.0000001, label + ": " + actual); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
