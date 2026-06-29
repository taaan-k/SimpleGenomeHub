package simplegenomehub.gui;

import javax.swing.*;
import java.awt.*;

final class SpeciesActionsPanel extends JPanel {

    private static final String MENU_SUFFIX = " \u25BE";

    private final JButton fileOperationsButton;
    private final JButton sequenceLookupButton;
    private final JButton expressionDataButton;
    private final JButton functionAnnotationButton;
    private final JButton geneSetButton;
    private final JButton geneViewerButton;
    private final JButton genomeAnalysisButton;
    private final JButton blastAnalysisButton;

    SpeciesActionsPanel() {
        Dimension buttonSize = new Dimension(162, 30);
        fileOperationsButton = createMenuButton("File Operations", buttonSize);
        sequenceLookupButton = createMenuButton("Sequence Tools", buttonSize);
        expressionDataButton = createMenuButton("Gene Expression", buttonSize);
        functionAnnotationButton = createMenuButton("Function Annotation", buttonSize);
        geneSetButton = createMenuButton("Gene Set", buttonSize);
        geneViewerButton = createMenuButton("Gene Info.", buttonSize);
        genomeAnalysisButton = createMenuButton("Genome Analysis", buttonSize);
        blastAnalysisButton = SimpleGenomeHubUi.createSoftButton("BLAST Analysis", buttonSize);

        setupLayout();
    }

    private JButton createMenuButton(String label, Dimension buttonSize) {
        return SimpleGenomeHubUi.createSoftButton(label + MENU_SUFFIX, buttonSize);
    }

    private void setupLayout() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 10));

        Color titleColor = new Color(79, 122, 68);
        JLabel titleLabel = new JLabel("File Operations & Functional Analysis");
        titleLabel.setForeground(titleColor);
        titleLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_18);

        JPanel headerPanel = new JPanel(new BorderLayout(14, 0));
        headerPanel.setOpaque(false);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(SpeciesInfoUiSupport.createSolidAccentBar(titleColor), BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        JPanel buttonsPanel = new JPanel(new GridLayout(3, 3, 12, 12));
        buttonsPanel.setOpaque(false);
        buttonsPanel.add(fileOperationsButton);
        buttonsPanel.add(sequenceLookupButton);
        buttonsPanel.add(geneViewerButton);
        buttonsPanel.add(expressionDataButton);
        buttonsPanel.add(functionAnnotationButton);
        buttonsPanel.add(genomeAnalysisButton);
        buttonsPanel.add(blastAnalysisButton);
        buttonsPanel.add(geneSetButton);
        buttonsPanel.add(Box.createGlue());

        add(buttonsPanel, BorderLayout.CENTER);
    }

    JButton getFileOperationsButton() {
        return fileOperationsButton;
    }

    JButton getSequenceLookupButton() {
        return sequenceLookupButton;
    }

    JButton getExpressionDataButton() {
        return expressionDataButton;
    }

    JButton getFunctionAnnotationButton() {
        return functionAnnotationButton;
    }

    JButton getGeneSetButton() {
        return geneSetButton;
    }

    JButton getGeneViewerButton() {
        return geneViewerButton;
    }

    JButton getGenomeAnalysisButton() {
        return genomeAnalysisButton;
    }

    JButton getBlastAnalysisButton() {
        return blastAnalysisButton;
    }

    void setAllEnabled(boolean enabled) {
        fileOperationsButton.setEnabled(enabled);
        sequenceLookupButton.setEnabled(enabled);
        expressionDataButton.setEnabled(enabled);
        functionAnnotationButton.setEnabled(enabled);
        geneSetButton.setEnabled(enabled);
        geneViewerButton.setEnabled(enabled);
        genomeAnalysisButton.setEnabled(enabled);
        blastAnalysisButton.setEnabled(enabled);
    }

    void updateButtonStates(boolean fileOperationsEnabled,
                            boolean sequenceLookupEnabled,
                            boolean expressionDataEnabled,
                            boolean functionAnnotationEnabled,
                            boolean geneSetEnabled,
                            boolean geneViewerEnabled,
                            boolean genomeAnalysisEnabled,
                            boolean blastAnalysisEnabled) {
        fileOperationsButton.setEnabled(fileOperationsEnabled);
        sequenceLookupButton.setEnabled(sequenceLookupEnabled);
        expressionDataButton.setEnabled(expressionDataEnabled);
        functionAnnotationButton.setEnabled(functionAnnotationEnabled);
        geneSetButton.setEnabled(geneSetEnabled);
        geneViewerButton.setEnabled(geneViewerEnabled);
        genomeAnalysisButton.setEnabled(genomeAnalysisEnabled);
        blastAnalysisButton.setEnabled(blastAnalysisEnabled);
    }
}
