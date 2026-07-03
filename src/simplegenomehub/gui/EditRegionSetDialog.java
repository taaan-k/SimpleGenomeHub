package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

final class EditRegionSetDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(EditRegionSetDialog.class.getName());

    private final SpeciesInfo species;
    private final File originalFile;
    private final Runnable onUpdateSuccess;
    private final JTextField regionSetNameField;
    private final JTextArea regionSetArea;
    private final JButton saveButton;
    private boolean regionSetUpdated;

    EditRegionSetDialog(Window parent, SpeciesInfo species, File regionSetFile, Runnable onUpdateSuccess) {
        super(parent, "Edit Region Set", ModalityType.MODELESS);
        this.species = species;
        this.originalFile = regionSetFile;
        this.onUpdateSuccess = onUpdateSuccess;
        this.regionSetNameField = new JTextField();
        this.regionSetArea = new JTextArea(14, 48);
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
        regionSetNameField.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
        regionSetNameField.setPreferredSize(SimpleGenomeHubStyle.SIZE_FIELD_260_X_30);

        regionSetArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        regionSetArea.setLineWrap(false);
        regionSetArea.setWrapStyleWord(false);
        regionSetArea.setBorder(BorderFactory.createCompoundBorder(
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
        JLabel nameLabel = new JLabel("Region Set Name");
        nameLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        namePanel.add(nameLabel, BorderLayout.NORTH);
        namePanel.add(regionSetNameField, BorderLayout.CENTER);
        formPanel.add(namePanel, BorderLayout.NORTH);

        JPanel regionPanel = new JPanel(new BorderLayout(0, 6));
        regionPanel.setOpaque(false);
        JLabel regionLabel = new JLabel("Region Set");
        regionLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        JScrollPane scrollPane = new JScrollPane(regionSetArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        regionPanel.add(regionLabel, BorderLayout.NORTH);
        regionPanel.add(scrollPane, BorderLayout.CENTER);
        formPanel.add(regionPanel, BorderLayout.CENTER);

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

        regionSetNameField.getDocument().addDocumentListener(updateListener);
        regionSetArea.getDocument().addDocumentListener(updateListener);
        saveButton.addActionListener(e -> saveRegionSet());
    }

    private void loadExistingContent() {
        if (originalFile == null) {
            return;
        }

        String fileName = originalFile.getName();
        regionSetNameField.setText(GeneSetFileSupport.extractDisplayName(fileName));

        try {
            regionSetArea.setText(GeneSetFileSupport.readGeneSetContent(originalFile));
            regionSetArea.setCaretPosition(0);
        } catch (Exception ex) {
            logger.warning("Failed to read region set file: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                "Failed to load region set file:\n" + ex.getMessage(),
                "Load Failed",
                JOptionPane.ERROR_MESSAGE);
        }

        updateSaveButtonState();
    }

    private void updateSaveButtonState() {
        boolean hasName = !regionSetNameField.getText().trim().isEmpty();
        boolean hasContent = GeneSetFileSupport.hasRegionEntries(regionSetArea.getText());
        saveButton.setEnabled(hasName && hasContent);
    }

    private void saveRegionSet() {
        String regionSetName = regionSetNameField.getText().trim();
        if (regionSetName.isEmpty()) {
            return;
        }

        if (GeneSetFileSupport.containsInvalidFileNameChars(regionSetName)) {
            JOptionPane.showMessageDialog(this,
                "Region Set Name contains invalid filename characters.",
                "Invalid Region Set Name",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<GeneSetFileSupport.RegionEntry> regionEntries;
        try {
            regionEntries = GeneSetFileSupport.parseRegionEntries(regionSetArea.getText());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this,
                ex.getMessage(),
                "Invalid Region Set",
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
            GeneSetFileSupport.buildStandardFileName(regionSetName, GeneSetFileSupport.SetKind.REGION));
        if (!targetFile.equals(originalFile) && targetFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "Region Set Name conflict.",
                "Region Set Name Conflict",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        File statsFile = species.getStatsFile();
        if (statsFile == null || !statsFile.exists()) {
            JOptionPane.showMessageDialog(this,
                "stat.txt was not found for the selected species.",
                "stat.txt Missing",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        setControlsEnabled(false);

        SwingWorker<List<String>, Void> worker = new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                Map<String, Long> chromosomeSizes = GeneSetFileSupport.loadChromosomeSizesFromStats(statsFile);
                return GeneSetFileSupport.validateRegionEntries(regionEntries, chromosomeSizes);
            }

            @Override
            protected void done() {
                if (!EditRegionSetDialog.this.isDisplayable()) {
                    return;
                }

                try {
                    List<String> validationErrors = get();
                    if (!validationErrors.isEmpty()) {
                        JTextArea errorArea = new JTextArea(String.join("\n", validationErrors), 12, 42);
                        errorArea.setEditable(false);
                        errorArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
                        JOptionPane.showMessageDialog(
                            EditRegionSetDialog.this,
                            new JScrollPane(errorArea),
                            "Region Set Validation Failed",
                            JOptionPane.WARNING_MESSAGE
                        );
                        return;
                    }

                    GeneSetFileSupport.writeTextFile(targetFile,
                        GeneSetFileSupport.formatRegionSetContent(regionEntries));
                    if (!targetFile.equals(originalFile) && originalFile.exists() && !originalFile.delete()) {
                        throw new IllegalStateException("Failed to remove the original region set file.");
                    }

                    regionSetUpdated = true;
                    JOptionPane.showMessageDialog(EditRegionSetDialog.this,
                        "Region set updated successfully:\n" + targetFile.getName(),
                        "Save Complete",
                        JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                    if (onUpdateSuccess != null) {
                        onUpdateSuccess.run();
                    }
                } catch (Exception ex) {
                    logger.warning("Failed to update region set: " + ex.getMessage());
                    JOptionPane.showMessageDialog(EditRegionSetDialog.this,
                        "Failed to update region set:\n" + ex.getMessage(),
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
        regionSetNameField.setEnabled(enabled);
        regionSetArea.setEnabled(enabled);
        saveButton.setEnabled(enabled
            && !regionSetNameField.getText().trim().isEmpty()
            && GeneSetFileSupport.hasRegionEntries(regionSetArea.getText()));
    }

    boolean isRegionSetUpdated() {
        return regionSetUpdated;
    }
}
