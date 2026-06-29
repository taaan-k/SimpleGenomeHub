package simplegenomehub.util.fileio;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Writes Render.settings.txt.
 */
final class MultipleSyntenyRenderSettingsWriter {

    private static final String DEFAULT_RECT_BORDER_COLOR = "150,164,184";
    private static final String DEFAULT_CHR_FILL_COLOR = "218,235,255";

    private MultipleSyntenyRenderSettingsWriter() {
    }

    static void write(File outputFile,
                      MultipleSyntenyService.GlobalSettings globalSettings) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("canvas_width=" + safeCanvasWidth(globalSettings));
            writer.newLine();
            writer.write("canvas_height=" + safeCanvasHeight(globalSettings));
            writer.newLine();
            writer.write("coord_origin=top_left");
            writer.newLine();
            writer.write("rect_anchor=left_bottom");
            writer.newLine();
            writer.write("rotation_positive=clockwise");
            writer.newLine();
            writer.write("bp_coordinate_mode=1_based_inclusive");
            writer.newLine();
            writer.write("default_chr_gap=" + safeGapLength(globalSettings));
            writer.newLine();
            writer.write("default_link_color=" + MultipleSyntenyService.DEFAULT_LINK_COLOR);
            writer.newLine();
            writer.write("default_link_alpha=" + formatDouble(MultipleSyntenyService.DEFAULT_LINK_ALPHA));
            writer.newLine();
            writer.write("default_highlight_link_color=" + MultipleSyntenyService.DEFAULT_HIGHLIGHT_COLOR);
            writer.newLine();
            writer.write("default_highlight_gene_color=" + MultipleSyntenyService.DEFAULT_HIGHLIGHT_COLOR);
            writer.newLine();
            writer.write("default_bend_value=" + safeLinkBend(globalSettings));
            writer.newLine();
            writer.write("default_rect_border_color=" + DEFAULT_RECT_BORDER_COLOR);
            writer.newLine();
            writer.write("default_chr_fill_color=" + DEFAULT_CHR_FILL_COLOR);
            writer.newLine();
        }
    }

    private static int safeCanvasWidth(MultipleSyntenyService.GlobalSettings globalSettings) {
        return globalSettings == null ? 1500 : Math.max(1, globalSettings.getCanvasWidth());
    }

    private static int safeCanvasHeight(MultipleSyntenyService.GlobalSettings globalSettings) {
        return globalSettings == null ? 1000 : Math.max(1, globalSettings.getCanvasHeight());
    }

    private static int safeGapLength(MultipleSyntenyService.GlobalSettings globalSettings) {
        return globalSettings == null ? 10 : Math.max(0, globalSettings.getGapLength());
    }

    private static int safeLinkBend(MultipleSyntenyService.GlobalSettings globalSettings) {
        return globalSettings == null ? 50 : Math.max(0, globalSettings.getLinkBend());
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
