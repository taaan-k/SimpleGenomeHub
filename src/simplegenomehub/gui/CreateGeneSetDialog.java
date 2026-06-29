package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.logging.Logger;

final class CreateGeneSetDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(CreateGeneSetDialog.class.getName());

    private final SpeciesInfo species;
    private final Runnable onCreateSuccess;
    private final JTextField geneSetNameField;
    private final JTextArea geneIdsArea;
    private final JButton createButton;
    private boolean geneSetCreated;

    CreateGeneSetDialog(Window parent, SpeciesInfo species, Runnable onCreateSuccess) {
        super(parent, "Create a Gene Set", ModalityType.MODELESS);
        this.species = species;
        this.onCreateSuccess = onCreateSuccess;
        this.geneSetNameField = new JTextField();
        this.geneIdsArea = new JTextArea(14, 48);
        this.createButton = new JButton("Create");

        initializeComponents();
        setupLayout();
        setupEventHandlers();

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

        createButton.setEnabled(false);
        createButton.setBackground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON);
        createButton.setForeground(SimpleGenomeHubStyle.DIALOG_PRIMARY_BUTTON_TEXT);
        createButton.setFocusPainted(false);
        createButton.setPreferredSize(new Dimension(120, 38));
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
        actionPanel.add(createButton);

        root.add(formPanel, BorderLayout.CENTER);
        root.add(actionPanel, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void setupEventHandlers() {
        DocumentListener updateListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCreateButtonState();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCreateButtonState();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCreateButtonState();
            }
        };

        geneSetNameField.getDocument().addDocumentListener(updateListener);
        geneIdsArea.getDocument().addDocumentListener(updateListener);
        createButton.addActionListener(e -> createGeneSet());
    }

    private void updateCreateButtonState() {
        boolean hasName = !geneSetNameField.getText().trim().isEmpty();
        boolean hasGeneIds = !geneIdsArea.getText().trim().isEmpty();
        createButton.setEnabled(hasName && hasGeneIds);
    }

    private void createGeneSet() {
        String geneSetName = geneSetNameField.getText().trim();
        List<String> geneIds = parseGeneIds(geneIdsArea.getText());

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
        if (geneSetDir == null && !species.createDirectoryStructure()) {
            JOptionPane.showMessageDialog(this,
                "Failed to initialize GeneSet directory.",
                "Directory Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        geneSetDir = species.getGeneSetDir();
        if (geneSetDir == null) {
            JOptionPane.showMessageDialog(this,
                "GeneSet directory is not available.",
                "Directory Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!geneSetDir.exists() && !geneSetDir.mkdirs()) {
            JOptionPane.showMessageDialog(this,
                "Failed to create GeneSet directory:\n" + geneSetDir.getAbsolutePath(),
                "Directory Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        File outputFile = new File(geneSetDir,
            GeneSetFileSupport.buildStandardFileName(geneSetName, GeneSetFileSupport.SetKind.GENE));
        if (outputFile.exists()) {
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
                if (!CreateGeneSetDialog.this.isDisplayable()) {
                    return;
                }

                try {
                    GeneSetGeneIdValidationSupport.ValidationResult validationResult = get();
                    if (validationResult.hasIssues()) {
                        GeneSetGeneIdValidationSupport.showValidationResult(
                            CreateGeneSetDialog.this, validationResult);
                    }

                    if (!validationResult.getMissingGeneIds().isEmpty()) {
                        return;
                    }

                    GeneSetFileSupport.writeGeneSetFile(outputFile, validationResult.getResolvedGeneIds());
                    geneSetCreated = true;
                    JOptionPane.showMessageDialog(CreateGeneSetDialog.this,
                        "Gene set created successfully:\n" + outputFile.getName(),
                        "Create Complete",
                        JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                    if (onCreateSuccess != null) {
                        onCreateSuccess.run();
                    }
                } catch (Exception ex) {
                    logger.warning("Failed to create gene set: " + ex.getMessage());
                    JOptionPane.showMessageDialog(CreateGeneSetDialog.this,
                        "Failed to create gene set:\n" + ex.getMessage(),
                        "Create Failed",
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
        createButton.setEnabled(enabled && !geneSetNameField.getText().trim().isEmpty()
            && !geneIdsArea.getText().trim().isEmpty());
    }

    private List<String> parseGeneIds(String rawText) {
        return GeneSetFileSupport.parseGeneIds(rawText);
    }

    boolean isGeneSetCreated() {
        return geneSetCreated;
    }
}
