package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.logging.Logger;

final class EditGeneSetDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(EditGeneSetDialog.class.getName());

    private final SpeciesInfo species;
    private final File originalFile;
    private final Runnable onUpdateSuccess;
    private final JTextField geneSetNameField;
    private final JTextArea geneIdsArea;
    private final JButton saveButton;
    private boolean geneSetUpdated;

    EditGeneSetDialog(Window parent, SpeciesInfo species, File geneSetFile, Runnable onUpdateSuccess) {
        super(parent, "Edit Gene Set", ModalityType.MODELESS);
        this.species = species;
        this.originalFile = geneSetFile;
        this.onUpdateSuccess = onUpdateSuccess;
        this.geneSetNameField = new JTextField();
        this.geneIdsArea = new JTextArea(14, 48);
        this.saveButton = new JButton("Save");

        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadExistingContent();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(640, 520);
        setMinimumSize(new Dimension(560, 460));
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        geneSetNameField.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
        geneSetNameField.setPreferredSize(SimpleGenomeHubStyle.SIZE_FIELD_260_X_30);

        geneIdsArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        geneIdsArea.setLineWrap(false);
        geneIdsArea.setWrapStyleWord(false);
        geneIdsArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SimpleGenomeHubUi.CARD_BORDER, 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        saveButton.setEnabled(false);
        saveButton.setBackground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON);
        saveButton.setForeground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_TEXT);
        saveButton.setFocusPainted(false);
        saveButton.setPreferredSize(new Dimension(120, 38));
    }

    private void setupLayout() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.setBackground(SimpleGenomeHubUi.DIALOG_BACKGROUND);

        JPanel formPanel = new JPanel(new BorderLayout(0, 10));
        formPanel.setOpaque(false);

        JPanel namePanel = new JPanel(new BorderLayout(0, 6));
        namePanel.setOpaque(false);
        JLabel nameLabel = new JLabel("Gene Set Name");
        nameLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        namePanel.add(nameLabel, BorderLayout.NORTH);
        namePanel.add(geneSetNameField, BorderLayout.CENTER);
        formPanel.add(namePanel, BorderLayout.NORTH);

        JPanel geneIdPanel = new JPanel(new BorderLayout(0, 6));
        geneIdPanel.setOpaque(false);
        JLabel geneIdLabel = new JLabel("Gene / mRNA / Transcript ID");
        geneIdLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        geneIdPanel.add(geneIdLabel, BorderLayout.NORTH);
        geneIdPanel.add(new JScrollPane(geneIdsArea), BorderLayout.CENTER);
        formPanel.add(geneIdPanel, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(saveButton);

        root.add(formPanel, BorderLayout.CENTER);
        root.add(actionPanel, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void setupEventHandlers() {
        DocumentListener updateListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSaveButtonState();
            }
        };

        geneSetNameField.getDocument().addDocumentListener(updateListener);
        geneIdsArea.getDocument().addDocumentListener(updateListener);
        saveButton.addActionListener(e -> saveGeneSet());
    }

    private void loadExistingContent() {
        if (originalFile == null) {
            return;
        }

        String fileName = originalFile.getName();
        geneSetNameField.setText(GeneSetFileSupport.extractDisplayName(fileName));

        try {
            geneIdsArea.setText(GeneSetFileSupport.readGeneSetContent(originalFile));
            geneIdsArea.setCaretPosition(0);
        } catch (Exception ex) {
            logger.warning("Failed to read gene set file: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                "Failed to load gene set file:\n" + ex.getMessage(),
                "Load Failed",
                JOptionPane.ERROR_MESSAGE);
        }

        updateSaveButtonState();
    }

    private void updateSaveButtonState() {
        boolean hasName = !geneSetNameField.getText().trim().isEmpty();
        boolean hasGeneIds = !geneIdsArea.getText().trim().isEmpty();
        saveButton.setEnabled(hasName && hasGeneIds);
    }

    private void saveGeneSet() {
        String geneSetName = geneSetNameField.getText().trim();
        List<String> geneIds = GeneSetFileSupport.parseGeneIds(geneIdsArea.getText());

        if (geneSetName.isEmpty() || geneIds.isEmpty()) {
            return;
        }

        if (GeneSetFileSupport.containsInvalidFileNameChars(geneSetName)) {
            JOptionPane.showMessageDialog(this,
                "Gene Set Name contains invalid filename characters.",
                "Invalid Gene Set Name",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        File geneSetDir = species.getGeneSetDir();
        if (geneSetDir == null || !geneSetDir.exists()) {
            JOptionPane.showMessageDialog(this,
                "GeneSet directory is not available.",
                "Directory Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        File targetFile = new File(geneSetDir,
            GeneSetFileSupport.buildStandardFileName(geneSetName, GeneSetFileSupport.SetKind.GENE));
        if (!targetFile.equals(originalFile) && targetFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "Gene Set Name conflict.",
                "Gene Set Name Conflict",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        setControlsEnabled(false);

        SwingWorker<GeneSetGeneIdValidationSupport.ValidationResult, Void> worker =
            new SwingWorker<GeneSetGeneIdValidationSupport.ValidationResult, Void>() {
            @Override
            protected GeneSetGeneIdValidationSupport.ValidationResult doInBackground() throws Exception {
                return GeneSetGeneIdValidationSupport.validateGeneIds(species, geneIds);
            }

            @Override
            protected void done() {
                if (!EditGeneSetDialog.this.isDisplayable()) {
                    return;
                }

                try {
                    GeneSetGeneIdValidationSupport.ValidationResult validationResult = get();
                    if (validationResult.hasIssues()) {
                        GeneSetGeneIdValidationSupport.showValidationResult(
                            EditGeneSetDialog.this, validationResult);
                    }

                    if (!validationResult.getMissingGeneIds().isEmpty()) {
                        return;
                    }

                    GeneSetFileSupport.writeGeneSetFile(targetFile, validationResult.getResolvedGeneIds());
                    if (!targetFile.equals(originalFile) && originalFile.exists() && !originalFile.delete()) {
                        throw new IllegalStateException("Failed to remove the original gene set file.");
                    }

                    geneSetUpdated = true;
                    JOptionPane.showMessageDialog(EditGeneSetDialog.this,
                        "Gene set updated successfully:\n" + targetFile.getName(),
                        "Save Complete",
                        JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                    if (onUpdateSuccess != null) {
                        onUpdateSuccess.run();
                    }
                } catch (Exception ex) {
                    logger.warning("Failed to update gene set: " + ex.getMessage());
                    JOptionPane.showMessageDialog(EditGeneSetDialog.this,
                        "Failed to update gene set:\n" + ex.getMessage(),
                        "Save Failed",
                        JOptionPane.ERROR_MESSAGE);
                } finally {
                    setControlsEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void setControlsEnabled(boolean enabled) {
        geneSetNameField.setEnabled(enabled);
        geneIdsArea.setEnabled(enabled);
        saveButton.setEnabled(enabled
            && !geneSetNameField.getText().trim().isEmpty()
            && !geneIdsArea.getText().trim().isEmpty());
    }

    boolean isGeneSetUpdated() {
        return geneSetUpdated;
    }
}
