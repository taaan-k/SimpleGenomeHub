package simplegenomehub.util.fileio;

import JJpolt2.Example.DualSyntenyPlotter;
import jjplot2.JJplot2GUI;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Exports a full-canvas preview image for a Dual Synteny Plot result folder.
 */
public final class DualSyntenyPreviewExporter {

    public static final String PREVIEW_FILE_NAME = "preview.png";
    private static final String DUAL_SYNTENY_WINDOW_TITLE = "Dual Systeny Plotter";
    private static final long WINDOW_WAIT_TIMEOUT_MS = 4000L;
    private static final long WINDOW_WAIT_INTERVAL_MS = 60L;

    private DualSyntenyPreviewExporter() {
    }

    public static List<Window> captureWindows() {
        List<Window> windows = new ArrayList<>();
        Collections.addAll(windows, Window.getWindows());
        return windows;
    }

    public static File exportPreview(File outputDir, List<Window> previousWindows) throws IOException {
        return exportPreviewFromWindows(outputDir, findLaunchedWindows(previousWindows));
    }

    public static File exportPreviewFromOutputDirectory(File outputDir, boolean reorderChromosomes) throws Exception {
        if (outputDir == null || !outputDir.isDirectory()) {
            throw new IllegalArgumentException("Genome Compare output directory is invalid.");
        }

        List<Window> previousWindows = captureWindows();
        DualSyntenyPlotter plotter = DualSyntenyPlotLauncher.createPlotterFromOutputDirectory(
            outputDir,
            reorderChromosomes
        );
        plotter.plot();

        List<Window> launchedWindows = findLaunchedWindows(previousWindows);
        try {
            return exportPreviewFromWindows(outputDir, launchedWindows);
        } finally {
            if (launchedWindows.isEmpty()) {
                launchedWindows = findLaunchedWindows(previousWindows);
            }
            disposeWindows(launchedWindows);
        }
    }

    public static File getPreviewFile(File outputDir) {
        return new File(outputDir, PREVIEW_FILE_NAME);
    }

    static List<Window> findLaunchedWindows(List<Window> previousWindows) {
        Set<Window> knownWindows = Collections.newSetFromMap(new IdentityHashMap<Window, Boolean>());
        if (previousWindows != null) {
            knownWindows.addAll(previousWindows);
        }

        if (EventQueue.isDispatchThread()) {
            return collectNewWindows(knownWindows);
        }

        long deadline = System.currentTimeMillis() + WINDOW_WAIT_TIMEOUT_MS;
        List<Window> launchedWindows = collectNewWindows(knownWindows);
        while (launchedWindows.isEmpty() && System.currentTimeMillis() < deadline) {
            flushEventQueue();
            try {
                Thread.sleep(WINDOW_WAIT_INTERVAL_MS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            launchedWindows = collectNewWindows(knownWindows);
        }
        return launchedWindows;
    }

    private static File exportPreviewFromWindows(File outputDir, List<Window> candidateWindows) throws IOException {
        if (outputDir == null || !outputDir.isDirectory()) {
            throw new IllegalArgumentException("Genome Compare output directory is invalid.");
        }

        JJplot2GUI plotGui = null;
        if (candidateWindows != null) {
            for (Window window : candidateWindows) {
                plotGui = findPlotGui(window);
                if (plotGui != null) {
                    break;
                }
            }
        }

        if (plotGui == null) {
            Window[] windows = Window.getWindows();
            for (int i = windows.length - 1; i >= 0; i--) {
                plotGui = findPlotGui(windows[i]);
                if (plotGui != null) {
                    break;
                }
            }
        }

        if (plotGui == null) {
            throw new IOException("Failed to resolve Dual Synteny Plot GUI for preview export.");
        }

        File previewFile = getPreviewFile(outputDir);
        savePreview(plotGui, previewFile);
        return previewFile;
    }

    private static List<Window> collectNewWindows(Set<Window> knownWindows) {
        List<Window> launchedWindows = new ArrayList<>();
        for (Window window : Window.getWindows()) {
            if (window == null || knownWindows.contains(window) || !window.isDisplayable()
                || !isDualSyntenyWindow(window)) {
                continue;
            }
            launchedWindows.add(window);
        }
        return launchedWindows;
    }

    private static void flushEventQueue() {
        if (EventQueue.isDispatchThread()) {
            return;
        }
        try {
            EventQueue.invokeAndWait(() -> { });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ignored) {
            // The preview export still has a chance to succeed with the current UI state.
        }
    }

    private static JJplot2GUI findPlotGui(Component component) throws IOException {
        if (component == null) {
            return null;
        }
        if (component instanceof Window && !isDualSyntenyWindow((Window) component)) {
            return null;
        }

        JJplot2GUI plotGui = extractPlotGui(component);
        if (plotGui != null) {
            return plotGui;
        }

        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JJplot2GUI childPlotGui = findPlotGui(child);
                if (childPlotGui != null) {
                    return childPlotGui;
                }
            }
        }
        return null;
    }

    private static JJplot2GUI extractPlotGui(Component component) throws IOException {
        Class<?> type = component.getClass();
        while (type != null) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                if (!JJplot2GUI.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(component);
                    if (value instanceof JJplot2GUI) {
                        return (JJplot2GUI) value;
                    }
                } catch (IllegalAccessException ex) {
                    throw new IOException("Failed to access Dual Synteny Plot GUI instance.", ex);
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean isDualSyntenyWindow(Window window) {
        if (!(window instanceof Frame)) {
            return false;
        }
        String title = ((Frame) window).getTitle();
        return DUAL_SYNTENY_WINDOW_TITLE.equals(title);
    }

    private static void savePreview(JJplot2GUI plotGui, File previewFile) throws IOException {
        if (plotGui == null) {
            throw new IllegalArgumentException("Dual Synteny plot GUI is required.");
        }
        if (previewFile == null) {
            throw new IllegalArgumentException("Preview file is required.");
        }

        if (EventQueue.isDispatchThread()) {
            plotGui.saveImageAsPNG(previewFile.getAbsolutePath());
            return;
        }

        try {
            EventQueue.invokeAndWait(() -> {
                try {
                    plotGui.saveImageAsPNG(previewFile.getAbsolutePath());
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while exporting Dual Synteny preview image.", ex);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException && cause.getCause() instanceof IOException) {
                throw (IOException) cause.getCause();
            }
            throw new IOException("Failed to export Dual Synteny preview image.", cause != null ? cause : ex);
        }
    }

    private static void disposeWindows(List<Window> windows) {
        if (windows == null) {
            return;
        }
        for (int i = windows.size() - 1; i >= 0; i--) {
            Window window = windows.get(i);
            if (window != null) {
                window.dispose();
            }
        }
    }
}
