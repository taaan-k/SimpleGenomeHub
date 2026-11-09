package simplegenomehub.gui;

import java.awt.*;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import simplegenomehub.gui.ExpressionDataPanel;
import simplegenomehub.gui.GeneDataRetriever;
import simplegenomehub.gui.SimpleGenomeHubUi;
import simplegenomehub.model.*;

/** Native Windows GUI actions against owned fixtures; no configured Hub data is opened. */
public final class ExpressionManagementSmoke {
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new AssertionError("This smoke test requires native graphics");
        runScenario(false);
        runScenario(true);
    }

    private static void runScenario(boolean injectFailures) throws Exception {
        Path root = Files.createTempDirectory("sgh-expression-management-");
        BlockingSpecies species = new BlockingSpecies();
        species.initializeFileStructure(root.toFile());
        Path directory = species.getExpressionDir().toPath();
        Files.createDirectories(directory);
        Path metadata = directory.resolve("legacy.metadata.json");
        Files.write(metadata, "{\"experiment_id\":\"legacy\",\"title\":\"中文旧实验\",\"future\":42}".getBytes(Charset.forName("GBK")));
        Path matrix = directory.resolve("legacy.matrix.tsv");
        Files.writeString(matrix, "Gene_ID\tRoot\n lcfv2_09070\t2569.59\n".replace("\n ", "\n"));
        byte[] original = Files.readAllBytes(metadata), matrixBytes = Files.readAllBytes(matrix);
        ExpressionDataPanel panel = null;
        species.block = true;
        try {
            panel = edt(() -> new ExpressionDataPanel(null, species));
            check(species.entered.await(5, TimeUnit.SECONDS), "Background loader was not submitted");
            ExpressionDataPanel owned = panel;
            JPanel tablePanel = field(panel, "tablePanel", JPanel.class);
            edt(() -> {
                SimpleGenomeHubUi.applyDialogStyle(owned);
                check(tablePanel.getBorder() instanceof CompoundBorder, "Styled border was not installed");
                owned.setVisible(true);
                return null;
            });
            CountDownLatch responsive = new CountDownLatch(1);
            SwingUtilities.invokeLater(responsive::countDown);
            check(responsive.await(1, TimeUnit.SECONDS), "Management constructor or loading blocked the EDT");
            species.block = false; species.release.countDown();
            JTable table = field(panel, "experimentsTable", JTable.class);
            JButton type = field(panel, "dataTypeButton", JButton.class);
            waitFor(() -> edt(() -> table.getRowCount() == 1 && table.isEnabled()));
            checkTitle(tablePanel, 1);
            edt(() -> { table.setRowSelectionInterval(0, 0); check(type.isEnabled(), "Legacy type action is disabled"); return null; });
            choose(type, true);
            check(Arrays.equals(original, Files.readAllBytes(metadata)), "Cancelling type selection rewrote metadata");
            if (injectFailures) {
                byte[] invalidMetadata = "{ invalid JSON".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Files.write(metadata, invalidMetadata);
                choose(type, false, "Unable to Save Data Type");
                check(Arrays.equals(invalidMetadata, Files.readAllBytes(metadata)), "Failed save replaced the metadata file");
                Files.write(metadata, original);
                AtomicBoolean failOnce = new AtomicBoolean(true);
                edt(() -> {
                    table.getModel().addTableModelListener(event -> {
                        if (failOnce.getAndSet(false)) throw new IllegalStateException("Injected refresh failure");
                    });
                    return null;
                });
            }
            choose(type, false, injectFailures ? "Data Type Saved; Refresh Failed" : null);
            waitFor(() -> ExpressionExperimentLoader.load(species).getReadyMatrixCount() == 1);
            waitFor(() -> edt(table::isEnabled));
            if (injectFailures) {
                JButton refresh = field(panel, "refreshButton", JButton.class);
                choose(refresh, false);
            }
            checkTitle(tablePanel, 1);
            check(new GeneDataRetriever().getGeneExpressionData("lcfv2_09070", species).size() == 1, "Confirmed type is invisible to a live query");
            check(Arrays.equals(matrixBytes, Files.readAllBytes(matrix)), "GUI type selection converted the source values");
            check(ExpressionExperiment.loadMetadataChecked(metadata.toFile()).getTitle().equals("中文旧实验"), "GUI save corrupted the legacy title");
            check(simplegenomehub.util.fileio.JsonMetadataSupport.read(metadata.toFile()).get("future").getAsInt() == 42,
                "GUI save removed an unknown metadata field");
            JButton delete = field(panel, "deleteButton", JButton.class);
            edt(() -> { table.setRowSelectionInterval(0, 0); return null; });
            choose(delete, false);
            waitFor(() -> edt(() -> table.getRowCount() == 0 && table.isEnabled()));
            checkTitle(tablePanel, 0);
            check(!Files.exists(metadata) && !Files.exists(matrix), "Paired deletion left an expression file behind");
            check(new GeneDataRetriever().getGeneExpressionData("lcfv2_09070", species).isEmpty(), "Deleted experiment survived in the query cache");
            System.out.println("PASS styled native expression management: responsive EDT, cancel, type confirmation, immediate query, unchanged matrix, paired deletion and updated titles; injected failures=" + injectFailures);
        } finally {
            species.block = false; species.release.countDown();
            if (panel != null) { ExpressionDataPanel owned = panel; edt(() -> { owned.dispose(); return null; }); }
        }
    }

    private static void checkTitle(JPanel panel, int count) throws Exception {
        edt(() -> {
            check(panel.getBorder() instanceof CompoundBorder, "Refreshing replaced the styled border");
            CompoundBorder border = (CompoundBorder)panel.getBorder();
            check(border.getOutsideBorder() instanceof TitledBorder, "Styled title is missing");
            check(((TitledBorder)border.getOutsideBorder()).getTitle().equals("Expression Experiments (" + count + ")"),
                "The visible experiment count was not refreshed");
            return null;
        });
    }

    private static void choose(JButton action, boolean cancel) throws Exception {
        choose(action, cancel, null);
    }

    private static void choose(JButton action, boolean cancel, String expectedDialog) throws Exception {
        AtomicReference<String> unexpected = new AtomicReference<>();
        AtomicBoolean expectedSeen = new AtomicBoolean();
        javax.swing.Timer timer = new javax.swing.Timer(80, event -> {
            for (Window window : Window.getWindows()) {
                if (!(window instanceof JDialog) || !window.isShowing()) continue;
                JOptionPane pane = pane((Container)window);
                if (pane == null) continue;
                String title = ((JDialog)window).getTitle();
                if (title.equals("Set Expression Data Type")) {
                    if (!cancel) pane.setInputValue(ExpressionMatrix.DataType.TPM);
                    pane.setValue(cancel ? JOptionPane.CANCEL_OPTION : JOptionPane.OK_OPTION);
                } else if (title.equals("Confirm Delete")) pane.setValue(JOptionPane.YES_OPTION);
                else if (title.equals("Delete Complete")) pane.setValue(JOptionPane.OK_OPTION);
                else if (title.equals(expectedDialog)) {
                    expectedSeen.set(true);
                    pane.setValue(JOptionPane.OK_OPTION);
                }
                else { unexpected.set(title + ": " + pane.getMessage()); pane.setValue(JOptionPane.CANCEL_OPTION); }
                window.dispose();
            }
        });
        edt(() -> { timer.start(); return null; });
        try {
            edt(() -> { action.doClick(); return null; });
            // Keep the timer alive until the real worker completion callback,
            // including its optional modal success dialog, has finished.
            Window owner = edt(() -> SwingUtilities.getWindowAncestor(action));
            if (owner instanceof ExpressionDataPanel)
                waitFor(() -> edt(() -> !field(owner, "loading", Boolean.class)));
            if (unexpected.get() != null) throw new AssertionError(unexpected.get());
            if (expectedDialog != null) check(expectedSeen.get(), "Expected dialog was not shown: " + expectedDialog);
        } finally { edt(() -> { timer.stop(); return null; }); }
    }
    private static JOptionPane pane(Container parent) {
        if (parent instanceof JOptionPane) return (JOptionPane)parent;
        for (Component child : parent.getComponents()) if (child instanceof Container) {
            JOptionPane found = pane((Container)child); if (found != null) return found;
        }
        return null;
    }
    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(owner));
    }
    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); SwingUtilities.invokeAndWait(task); return task.get();
    }
    private static void waitFor(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.call()) {
            if (System.nanoTime() > deadline) throw new AssertionError("GUI/background operation timed out");
            Thread.sleep(30);
        }
    }
    private static void check(boolean success, String message) { if (!success) throw new AssertionError(message); }
    private static final class BlockingSpecies extends SpeciesInfo {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        volatile boolean block;
        BlockingSpecies() { super("Pineapple", "v2"); }
        @Override public java.io.File getExpressionDir() {
            if (block && !SwingUtilities.isEventDispatchThread()) {
                entered.countDown();
                try { if (!release.await(8, TimeUnit.SECONDS)) throw new AssertionError("Blocked fixture loader timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            }
            return super.getExpressionDir();
        }
    }
}
