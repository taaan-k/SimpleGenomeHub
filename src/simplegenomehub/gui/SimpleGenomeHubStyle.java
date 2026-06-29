package simplegenomehub.gui;

import javax.swing.*;
import java.awt.*;

/**
 * Centralized UI style registry for fonts, colors, and common widget sizes.
 */
public final class SimpleGenomeHubStyle {

    public static final double FONT_SCALE_FACTOR = 1.2;

    public static final Font FONT_SANS_PLAIN_11 = createScaledFont(Font.SANS_SERIF, Font.PLAIN, 11); // SequenceLookupDialog.java-114, SequenceExtractionDialog.java-141
    public static final Font FONT_SANS_PLAIN_12 = createScaledFont(Font.SANS_SERIF, Font.PLAIN, 12); // BlastDialog.java-207, HelpDialog.java-201, KEGGEnrichmentDialog.java-281
    public static final Font FONT_SANS_PLAIN_14 = createScaledFont(Font.SANS_SERIF, Font.PLAIN, 14); // GeneInfoViewerDialog.java-591, SpeciesInfoUiSupport.java-66
    public static final Font FONT_SANS_PLAIN_15 = createScaledFont(Font.SANS_SERIF, Font.PLAIN, 15); // SpeciesOverviewPanel.java-54

    public static final Font FONT_SANS_BOLD_12 = createScaledFont(Font.SANS_SERIF, Font.BOLD, 12); // BlastPanel.java-345, ChromosomeRegionExtractorDialog.java-81, GeneInfoViewerDialog.java-132, KEGGEnrichmentDialog.java-282, SequenceLookupDialog.java-171, SpeciesIdentificationDialog.java-94
    public static final Font FONT_SANS_BOLD_13 = createScaledFont(Font.SANS_SERIF, Font.BOLD, 13); // SimpleGenomeHubUi.java-169, SpeciesOverviewPanel.java-567, SpeciesOverviewPanel.java-582
    public static final Font FONT_SANS_BOLD_15 = createScaledFont(Font.SANS_SERIF, Font.BOLD, 15); // SpeciesInfoUiSupport.java-59, SpeciesOverviewPanel.java-581
    public static final Font FONT_SANS_BOLD_18 = createScaledFont(Font.SANS_SERIF, Font.BOLD, 18); // AdvancedCircosLaunchDialog.java-84, SpeciesActionsPanel.java-36, SpeciesTreePanel.java-108
    public static final Font FONT_SANS_BOLD_19 = createScaledFont(Font.SANS_SERIF, Font.BOLD, 19); // SpeciesInfoUiSupport.java-27
    public static final Font FONT_SANS_BOLD_24 = createScaledFont(Font.SANS_SERIF, Font.BOLD, 24); // SimpleGenomeHubMainPanel.java-95
    public static final Font FONT_SANS_BOLD_28 = createScaledFont(Font.SANS_SERIF, Font.BOLD, 28); // AdvancedCircosLaunchDialog.java-124

    public static final Font FONT_SANS_ITALIC_10 = createScaledFont(Font.SANS_SERIF, Font.ITALIC, 10); // SequenceLookupDialog.java-177

    public static final Font FONT_MONOSPACED_PLAIN_11 = createScaledFont(Font.MONOSPACED, Font.PLAIN, 11); // AnnotationDataPanel.java-259, AnnotationImportDialog.java-171, AnnotationImportDialog.java-340, AutoFunctionalAnnotationDialog.java-85, BatchImportDialog.java-126, BlastPanel.java-391, ExportDialog.java-122, ExpressionHeatmapDialog.java-117, FunctionalEnrichmentPanel.java-452, SearchDialog.java-146, SequenceExtractionDialog.java-130, SequenceLookupDialog.java-792, SimpleExpressionImportDialog.java-88, SimpleExpressionImportDialog.java-112, SpeciesEditDialog.java-95, SpeciesImportDialog.java-111, ValidationDialog.java-119
    public static final Font FONT_MONOSPACED_PLAIN_12 = createScaledFont(Font.MONOSPACED, Font.PLAIN, 12); // AnnotationDataPanel.java-244, AnnotationTypeSelectionDialog.java-77, BlastPanel.java-333, BlastResultsPanel.java-239, ConfigurationDialog.java-167, ConfigurationDialog.java-374, ExpressionDataPanel.java-206, ExpressionHeatmapDialog.java-204, ExpressionHeatmapDialog.java-610, FilePreviewDialog.java-63, FunctionalEnrichmentPanel.java-108, FunctionalEnrichmentPanel.java-508, GeneFilterDialog.java-85, GeneInfoViewerDialog.java-1282, GeneInfoViewerDialog.java-1288, GeneInfoViewerDialog.java-1294, GOEnrichmentDialog.java-328, GenomeComparePanel.java-280, KEGGEnrichmentDialog.java-163, SequenceLookupDialog.java-137, SpeciesOverviewPanel.java-71

    public static final Font FONT_COURIER_NEW_PLAIN_12 = createScaledFont("Courier New", Font.PLAIN, 12); // ChromosomeRegionExtractorDialog.java-51, SpeciesIdentificationDialog.java-67

    public static final Font FONT_ARIAL_PLAIN_8 = createScaledFont("Arial", Font.PLAIN, 8); // GeneInfoViewerDialog.java-2108
    public static final Font FONT_ARIAL_PLAIN_9 = createScaledFont("Arial", Font.PLAIN, 9); // GeneInfoViewerDialog.java-2102
    public static final Font FONT_ARIAL_BOLD_12 = createScaledFont("Arial", Font.BOLD, 12); // GeneInfoViewerDialog.java-2122
    public static final Font FONT_ARIAL_ITALIC_10 = createScaledFont("Arial", Font.ITALIC, 10); // GeneInfoViewerDialog.java-2128

    public static final Color APP_BACKGROUND = Color.WHITE;
    public static final Color TITLE_BLUE = new Color(29, 71, 127);
    public static final Color CARD_BORDER = new Color(210, 220, 233);
    public static final Color SOFT_BUTTON_TOP = new Color(255, 255, 255);
    public static final Color SOFT_BUTTON_BOTTOM = new Color(232, 239, 249);
    public static final Color SOFT_BUTTON_BORDER = new Color(180, 199, 224);
    public static final Color DIALOG_BACKGROUND = new Color(246, 250, 255);
    public static final Color DIALOG_PANEL_BACKGROUND = new Color(252, 254, 255);
    public static final Color DIALOG_PRIMARY_BUTTON = new Color(233, 143, 39);
    public static final Color DIALOG_PRIMARY_BUTTON_BORDER = new Color(206, 120, 22);
    public static final Color DIALOG_PRIMARY_BUTTON_TEXT = Color.WHITE;
    public static final Color DIALOG_SECONDARY_BUTTON = new Color(241, 246, 253);
    public static final Color DIALOG_SECONDARY_BUTTON_TEXT = new Color(47, 66, 92);
    public static final Color DIALOG_TABLE_HEADER = new Color(228, 237, 249);
    public static final Color MENU_BACKGROUND = new Color(239, 246, 253);
    public static final Color MENU_HOVER_BACKGROUND = new Color(214, 228, 246);
    public static final Color MENU_BORDER = new Color(184, 201, 224);
    public static final Color MENU_TEXT = new Color(45, 63, 90);

    public static final Color TEXT_PREVIEW_BACKGROUND = new Color(248, 248, 248);
    public static final Color STATUS_SUCCESS_TEXT = new Color(0, 128, 0);
    public static final Color STATUS_WARNING_TEXT = new Color(180, 120, 0);
    public static final Color STATUS_INFO_TEXT = Color.BLUE;
    public static final Color BUTTON_ACTION_ORANGE = new Color(255, 165, 0);
    public static final Color BUTTON_SUCCESS_GREEN = new Color(40, 167, 69);
    public static final Color EMPHASIS_STEEL_BLUE = new Color(70, 130, 180);
    public static final Color EMPHASIS_DARK_GREEN = new Color(0, 100, 0);
    public static final Color TABLE_SELECTION_ORANGE = new Color(255, 239, 213);
    public static final Color TABLE_SELECTION_BLUE = new Color(230, 240, 255);
    public static final Color FEEDBACK_SUCCESS_BACKGROUND = new Color(220, 255, 220);
    public static final Color FEEDBACK_ERROR_BACKGROUND = new Color(255, 220, 220);

    public static final Dimension SIZE_FIELD_260_X_30 = new Dimension(260, 30);
    public static final Dimension SIZE_BUTTON_80_X_30 = new Dimension(80, 30);
    public static final Dimension SIZE_BUTTON_84_X_30 = new Dimension(84, 30);
    public static final Dimension SIZE_BUTTON_100_X_30 = new Dimension(100, 30);
    public static final Dimension SIZE_BUTTON_120_X_30 = new Dimension(120, 30);
    public static final Dimension SIZE_BUTTON_120_X_38 = new Dimension(120, 38);
    public static final Dimension SIZE_BUTTON_150_X_35 = new Dimension(150, 35);
    public static final Dimension SIZE_COMBO_280_X_30 = new Dimension(280, 30);

    private SimpleGenomeHubStyle() {
    }

    public static void installGlobalFontDefaults() {
        UIManager.put("Button.font", FONT_SANS_PLAIN_12);
        UIManager.put("Label.font", FONT_SANS_PLAIN_12);
        UIManager.put("TextField.font", FONT_SANS_PLAIN_12);
        UIManager.put("TextArea.font", FONT_SANS_PLAIN_12);
        UIManager.put("TextPane.font", FONT_SANS_PLAIN_12);
        UIManager.put("EditorPane.font", FONT_SANS_PLAIN_12);
        UIManager.put("ComboBox.font", FONT_SANS_PLAIN_12);
        UIManager.put("TabbedPane.font", FONT_SANS_PLAIN_12);
        UIManager.put("Menu.font", FONT_SANS_PLAIN_12);
        UIManager.put("MenuItem.font", FONT_SANS_PLAIN_12);
        UIManager.put("CheckBox.font", FONT_SANS_PLAIN_12);
        UIManager.put("RadioButton.font", FONT_SANS_PLAIN_12);
        UIManager.put("TitledBorder.font", FONT_SANS_BOLD_12);
        UIManager.put("OptionPane.messageFont", FONT_SANS_PLAIN_12);
        UIManager.put("OptionPane.buttonFont", FONT_SANS_PLAIN_12);
        UIManager.put("Table.font", FONT_SANS_PLAIN_12);
        UIManager.put("TableHeader.font", FONT_SANS_BOLD_12);
        UIManager.put("Tree.font", FONT_SANS_PLAIN_12);
        UIManager.put("List.font", FONT_SANS_PLAIN_12);
        UIManager.put("Spinner.font", FONT_SANS_PLAIN_12);
    }

    public static Font bold(Font baseFont) {
        return derive(baseFont, Font.BOLD, null);
    }

    public static Font bold(Font baseFont, float size) {
        return derive(baseFont, Font.BOLD, size);
    }

    public static Font plain(Font baseFont) {
        return derive(baseFont, Font.PLAIN, null);
    }

    public static Font plain(Font baseFont, float size) {
        return derive(baseFont, Font.PLAIN, size);
    }

    public static Font italic(Font baseFont) {
        return derive(baseFont, Font.ITALIC, null);
    }

    public static Font italic(Font baseFont, float size) {
        return derive(baseFont, Font.ITALIC, size);
    }

    public static Font withStyle(Font baseFont, int style) {
        return derive(baseFont, style, null);
    }

    public static Font resize(Font baseFont, float size) {
        Font base = safeBaseFont(baseFont);
        return base.deriveFont(base.getStyle(), scaleSize(size));
    }

    private static Font derive(Font baseFont, int style, Float size) {
        Font base = safeBaseFont(baseFont);
        if (size == null) {
            return base.deriveFont(style);
        }
        return base.deriveFont(style, scaleSize(size));
    }

    private static Font safeBaseFont(Font baseFont) {
        return baseFont != null ? baseFont : FONT_SANS_PLAIN_12;
    }

    private static Font createScaledFont(String family, int style, int baseSize) {
        return new Font(family, style, scaleSize(baseSize));
    }

    private static int scaleSize(int baseSize) {
        return Math.max(1, (int) Math.round(baseSize * FONT_SCALE_FACTOR));
    }

    private static float scaleSize(float baseSize) {
        return (float) (baseSize * FONT_SCALE_FACTOR);
    }
}
