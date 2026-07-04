package simplegenomehub.util.fileio;

import biocjava.bioDoer.JIGplotToolkit.Circos.SuperCircos.AmazingSuperCircos2;
import jigplot.engine.JIGBasePanel;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Exports a full-canvas preview image for an Advanced Circos result folder.
 */
public final class AdvancedCircosPreviewExporter {

    public static final String PREVIEW_FILE_NAME = "preview.png";

    private AdvancedCircosPreviewExporter() {
    }

    public static File exportPreview(AmazingSuperCircos2 circos, File projectDir) throws IOException {
        if (circos == null) {
            throw new IllegalArgumentException("Circos instance is required.");
        }
        if (projectDir == null || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("Project directory is invalid.");
        }

        File previewFile = getPreviewFile(projectDir);
        JIGBasePanel basePanel = extractBasePanel(circos);
        if (basePanel == null) {
            throw new IOException("Failed to resolve Advanced Circos canvas for preview export.");
        }

        basePanel.save2PNG(previewFile);
        return previewFile;
    }

    public static File getPreviewFile(File projectDir) {
        return new File(projectDir, PREVIEW_FILE_NAME);
    }

    private static JIGBasePanel extractBasePanel(AmazingSuperCircos2 circos) throws IOException {
        try {
            Field basePanelField = AmazingSuperCircos2.class.getDeclaredField("jbp");
            basePanelField.setAccessible(true);
            Object value = basePanelField.get(circos);
            if (value instanceof JIGBasePanel) {
                return (JIGBasePanel) value;
            }
            return null;
        } catch (ReflectiveOperationException ex) {
            throw new IOException("Failed to access Advanced Circos canvas.", ex);
        }
    }
}
