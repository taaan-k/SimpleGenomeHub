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

final class CreateRegionSetDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(CreateRegionSetDialog.class.getName());
    private static final String REGION_SET_TEMPLATE = GeneSetFileSupport.REGION_SET_HEADER + "\n"
        + "ChrName1 StartPos EndPos\n"
        + "ChrName2 StartPos EndPos\n"
        + "...";

    private final SpeciesInfo species;
    private final Runnable onCreateSuccess;
    private final JTextField regionSetNameField;
    private final JTextArea regionSetArea;
    private final JButton createButton;
    private boolean regionSetCreated;
    private boolean showingTemplate;

    CreateRegionSetDialog(Window parent, SpeciesInfo species, Runnable onCreateSuccess) {
        super(parent, "Create a Region Set", ModalityType.MODELESS);
        this.species = species;
        this.onCreateSuccess = onCreateSuccess;
        this.regionSetNameField = new JTextField();
        this.regionSetArea = new JTextArea(14, 48);
        this.createButton = new JButton("Create");

        initializeComponents();
        setupLayout();
        setupEventHandlers();
        installTemplateText();

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

        regionSetNameField.getDocument().addDocumentListener(updateListener);
        regionSetArea.getDocument().addDocumentListener(updateListener);
        regionSetArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (showingTemplate) {
                    regionSetArea.setText("");
                    regionSetArea.setForeground(STAT_TEXT_COLOR());
                    showingTemplate = false;
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (regionSetArea.getText().trim().isEmpty()) {
                    installTemplateText();
                }
            }
        });
        createButton.addActionListener(e -> createRegionSet());
    }

    private void installTemplateText() {
        showingTemplate = true;
        regionSetArea.setForeground(new Color(142, 150, 164));
        regionSetArea.setText(REGION_SET_TEMPLATE);
        regionSetArea.setCaretPosition(0);
        updateCreateButtonState();
    }

    private void updateCreateButtonState() {
        boolean hasName = !regionSetNameField.getText().trim().isEmpty();
        boolean hasContent = !showingTemplate && GeneSetFileSupport.hasRegionEntries(regionSetArea.getText());
        createButton.setEnabled(hasName && hasContent);
    }

    private void createRegionSet() {
        String regionSetName = regionSetNameField.getText().trim();
        if (regionSetName.isEmpty() || showingTemplate) {
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
            GeneSetFileSupport.buildStandardFileName(regionSetName, GeneSetFileSupport.SetKind.REGION));
        if (outputFile.exists()) {
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
                if (!CreateRegionSetDialog.this.isDisplayable()) {
                    return;
                }

                try {
                    List<String> validationErrors = get();
                    if (!validationErrors.isEmpty()) {
                        JTextArea errorArea = new JTextArea(String.join("\n", validationErrors), 12, 42);
                        errorArea.setEditable(false);
                        errorArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
                        JOptionPane.showMessageDialog(
                            CreateRegionSetDialog.this,
                            new JScrollPane(errorArea),
                            "Region Set Validation Failed",
                            JOptionPane.WARNING_MESSAGE
                        );
                        return;
                    }

                    GeneSetFileSupport.writeTextFile(outputFile,
                        GeneSetFileSupport.formatRegionSetContent(regionEntries));
                    regionSetCreated = true;
                    JOptionPane.showMessageDialog(CreateRegionSetDialog.this,
                        "Region set created successfully:\n" + outputFile.getName(),
                        "Create Complete",
                        JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                    if (onCreateSuccess != null) {
                        onCreateSuccess.run();
                    }
                } catch (Exception ex) {
                    logger.warning("Failed to create region set: " + ex.getMessage());
                    JOptionPane.showMessageDialog(CreateRegionSetDialog.this,
                        "Failed to create region set:\n" + ex.getMessage(),
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
        regionSetNameField.setEnabled(enabled);
        regionSetArea.setEnabled(enabled);
        createButton.setEnabled(enabled
            && !regionSetNameField.getText().trim().isEmpty()
            && !showingTemplate
            && GeneSetFileSupport.hasRegionEntries(regionSetArea.getText()));
    }

    private Color STAT_TEXT_COLOR() {
        return new Color(76, 84, 99);
    }

    boolean isRegionSetCreated() {
        return regionSetCreated;
    }
}
