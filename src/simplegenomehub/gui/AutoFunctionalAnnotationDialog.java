package simplegenomehub.gui;

import biocjava.bioDoer.Eggnog.EggnogDbConstants;
import biocjava.bioDoer.Kegg.KeggBackEndConstants;
import simplegenomehub.config.SimpleGenomeHubConfig;
import simplegenomehub.model.GeneAnnotationData;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.fileio.AutoFunctionalAnnotationService;
import simplegenomehub.util.fileio.KeggBackendManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.Map;

/**
 * Lightweight configuration dialog for automatic eggNOG-based annotation.
 */
public class AutoFunctionalAnnotationDialog extends JDialog {

    private final SpeciesInfo species;

    private JComboBox<String> taxScopeCombo;
    private JSpinner cpuSpinner;
    private JTextField evalueField;
    private JTextField outputPrefixField;
    private JTextField proteinFileField;
    private JComboBox<String> keggModeCombo;
    private JComboBox<String> keggTypeCombo;
    private JTextField customKeggField;
    private JButton browseCustomKeggButton;
    private JTextArea statusArea;
    private JProgressBar progressBar;
    private JButton runButton;
    private JButton cancelButton;

    public AutoFunctionalAnnotationDialog(Window parent, SpeciesInfo species) {
        super(parent, "Auto Func. Annotations - " + species.getSpeciesName(), ModalityType.APPLICATION_MODAL);
        this.species = species;
        initializeComponents();
        setupLayout();
        setupEvents();
        setSize(720, 520);
        setMinimumSize(new Dimension(680, 480));
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        SimpleGenomeHubConfig config = SimpleGenomeHubConfig.getInstance();

        taxScopeCombo = new JComboBox<>(EggnogDbConstants.allTaxScopes());
        taxScopeCombo.setSelectedItem(config.getProperty(SimpleGenomeHubConfig.EGGNOG_TAX_SCOPE));
        SimpleGenomeHubUi.setComboBoxDisplayWidth(taxScopeCombo, 260);

        cpuSpinner = new JSpinner(new SpinnerNumberModel(
            config.getIntProperty(SimpleGenomeHubConfig.EGGNOG_CPU, 8), 1, 256, 1));
        evalueField = new JTextField(config.getProperty(SimpleGenomeHubConfig.EGGNOG_EVALUE) != null
            ? config.getProperty(SimpleGenomeHubConfig.EGGNOG_EVALUE) : "0.001", 12);
        outputPrefixField = new JTextField(buildDefaultOutputPrefix(), 24);

        proteinFileField = new JTextField(resolveProteinPath(), 42);
        proteinFileField.setEditable(false);

        keggModeCombo = new JComboBox<>(new String[]{"Preset Backend", "Custom Backend"});
        String savedMode = config.getProperty(SimpleGenomeHubConfig.EGGNOG_KEGG_BACKEND_MODE);
        keggModeCombo.setSelectedItem("CUSTOM".equalsIgnoreCase(savedMode) ? "Custom Backend" : "Preset Backend");
        SimpleGenomeHubUi.setComboBoxDisplayWidth(keggModeCombo, 180);

        keggTypeCombo = new JComboBox<>(KeggBackEndConstants.BACKEND_TYPES);
        String savedType = config.getProperty(SimpleGenomeHubConfig.EGGNOG_KEGG_BACKEND_TYPE);
        keggTypeCombo.setSelectedItem(savedType != null && !savedType.isEmpty() ? savedType : "Plants");
        SimpleGenomeHubUi.setComboBoxDisplayWidth(keggTypeCombo, 220);

        customKeggField = new JTextField(36);
        String savedCustom = config.getProperty(SimpleGenomeHubConfig.KEGG_CUSTOM_BACKEND_PATH);
        if (savedCustom != null) {
            customKeggField.setText(savedCustom);
        }
        browseCustomKeggButton = new JButton("Browse...");

        statusArea = new JTextArea(12, 60);
        statusArea.setEditable(false);
        statusArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        statusArea.setText("Auto annotation will use representative proteins and integrate GO/KEGG/PFAM/Description.\n");

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        runButton = new JButton("Run Auto Annotation");
        cancelButton = new JButton("Cancel");

        updateKeggControls();
    }

    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(createConfigPanel(), BorderLayout.NORTH);
        root.add(createStatusPanel(), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(progressBar, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancelButton);
        buttons.add(runButton);
        bottom.add(buttons, BorderLayout.EAST);

        add(root, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Auto Annotation Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        panel.add(new JLabel("Input proteins:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0; gbc.gridwidth = 3;
        panel.add(proteinFileField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.gridwidth = 1;
        panel.add(new JLabel("Tax scope:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0;
        panel.add(taxScopeCombo, gbc);
        gbc.gridx = 2; gbc.gridy = 1; gbc.weightx = 0;
        panel.add(new JLabel("CPU:"), gbc);
        gbc.gridx = 3; gbc.gridy = 1; gbc.weightx = 0.3;
        panel.add(cpuSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel("E-value:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1.0;
        panel.add(evalueField, gbc);
        gbc.gridx = 2; gbc.gridy = 2; gbc.weightx = 0;
        panel.add(new JLabel("Output prefix:"), gbc);
        gbc.gridx = 3; gbc.gridy = 2; gbc.weightx = 1.0;
        panel.add(outputPrefixField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        panel.add(new JLabel("KEGG mode:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1.0;
        panel.add(keggModeCombo, gbc);
        gbc.gridx = 2; gbc.gridy = 3; gbc.weightx = 0;
        panel.add(new JLabel("Backend type:"), gbc);
        gbc.gridx = 3; gbc.gridy = 3; gbc.weightx = 1.0;
        panel.add(keggTypeCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        panel.add(new JLabel("Custom backend:"), gbc);
        gbc.gridx = 1; gbc.gridy = 4; gbc.gridwidth = 2; gbc.weightx = 1.0;
        panel.add(customKeggField, gbc);
        gbc.gridx = 3; gbc.gridy = 4; gbc.gridwidth = 1; gbc.weightx = 0;
        panel.add(browseCustomKeggButton, gbc);

        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(new TitledBorder("Run Status"));
        wrapper.add(new JScrollPane(statusArea), BorderLayout.CENTER);
        return wrapper;
    }

    private void setupEvents() {
        cancelButton.addActionListener(e -> dispose());
        browseCustomKeggButton.addActionListener(e -> browseCustomKeggBackend());
        keggModeCombo.addActionListener(e -> updateKeggControls());
        runButton.addActionListener(e -> startRun());
    }

    private void browseCustomKeggBackend() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("KEGG backend files", "TBtoolsKeggBackEnd", "txt", "tsv"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            customKeggField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void updateKeggControls() {
        boolean presetMode = isPresetKeggMode();
        keggTypeCombo.setEnabled(presetMode);
        customKeggField.setEnabled(!presetMode);
        browseCustomKeggButton.setEnabled(!presetMode);
    }

    private boolean isPresetKeggMode() {
        return "Preset Backend".equals(String.valueOf(keggModeCombo.getSelectedItem()));
    }

    private void startRun() {
        runButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        appendStatus("Starting auto annotation...");

        AutoFunctionalAnnotationService.Parameters parameters;
        try {
            parameters = buildParameters();
        } catch (Exception ex) {
            runButton.setEnabled(true);
            progressBar.setVisible(false);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid configuration", JOptionPane.ERROR_MESSAGE);
            return;
        }

        persistSelections(parameters);

        SwingWorker<AutoFunctionalAnnotationService.Result, String> worker =
            new SwingWorker<AutoFunctionalAnnotationService.Result, String>() {
                @Override
                protected AutoFunctionalAnnotationService.Result doInBackground() throws Exception {
                    return AutoFunctionalAnnotationService.run(
                        AutoFunctionalAnnotationDialog.this,
                        species,
                        parameters,
                        message -> publish(message)
                    );
                }

                @Override
                protected void process(java.util.List<String> chunks) {
                    for (String chunk : chunks) {
                        appendStatus(chunk);
                        progressBar.setString(chunk);
                    }
                }

                @Override
                protected void done() {
                    progressBar.setVisible(false);
                    progressBar.setIndeterminate(false);
                    runButton.setEnabled(true);
                    try {
                        AutoFunctionalAnnotationService.Result result = get();
                        StringBuilder summary = new StringBuilder();
                        summary.append("Auto annotation completed.\n");
                        summary.append("Raw outputs: ").append(result.getRawOutputDir().getAbsolutePath()).append("\n");
                        for (Map.Entry<GeneAnnotationData.AnnotationType, Integer> entry : result.getImportedCounts().entrySet()) {
                            if (entry.getValue() != null && entry.getValue() > 0) {
                                summary.append(entry.getKey().getShortName())
                                    .append(": ")
                                    .append(entry.getValue())
                                    .append("\n");
                            }
                        }
                        appendStatus(summary.toString());
                        JOptionPane.showMessageDialog(AutoFunctionalAnnotationDialog.this,
                            summary.toString(), "Auto annotation complete", JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    } catch (Exception ex) {
                        appendStatus("FAILED: " + ex.getMessage());
                        JOptionPane.showMessageDialog(AutoFunctionalAnnotationDialog.this,
                            ex.getMessage(), "Auto annotation failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
        worker.execute();
    }

    private AutoFunctionalAnnotationService.Parameters buildParameters() {
        String evalue = evalueField.getText().trim();
        if (evalue.isEmpty()) {
            throw new IllegalArgumentException("E-value is required.");
        }
        Double.parseDouble(evalue);

        String prefix = outputPrefixField.getText().trim();
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("Output prefix is required.");
        }

        KeggBackendManager.BackendMode mode = isPresetKeggMode()
            ? KeggBackendManager.BackendMode.PRESET
            : KeggBackendManager.BackendMode.CUSTOM;

        File customBackend = null;
        if (mode == KeggBackendManager.BackendMode.CUSTOM) {
            String path = customKeggField.getText().trim();
            if (path.isEmpty()) {
                throw new IllegalArgumentException("Custom KEGG backend file is required in Custom mode.");
            }
            customBackend = new File(path);
            if (!customBackend.isFile()) {
                throw new IllegalArgumentException("Custom KEGG backend file does not exist.");
            }
        }

        return new AutoFunctionalAnnotationService.Parameters(
            String.valueOf(taxScopeCombo.getSelectedItem()),
            ((Number) cpuSpinner.getValue()).intValue(),
            evalue,
            prefix,
            mode,
            String.valueOf(keggTypeCombo.getSelectedItem()),
            customBackend
        );
    }

    private void persistSelections(AutoFunctionalAnnotationService.Parameters parameters) {
        SimpleGenomeHubConfig config = SimpleGenomeHubConfig.getInstance();
        config.setProperty(SimpleGenomeHubConfig.EGGNOG_TAX_SCOPE, parameters.getTaxScope());
        config.setIntProperty(SimpleGenomeHubConfig.EGGNOG_CPU, parameters.getCpu());
        config.setProperty(SimpleGenomeHubConfig.EGGNOG_EVALUE, parameters.getEvalue());
        config.setProperty(SimpleGenomeHubConfig.EGGNOG_KEGG_BACKEND_MODE, parameters.getKeggBackendMode().name());
        config.setProperty(SimpleGenomeHubConfig.EGGNOG_KEGG_BACKEND_TYPE, parameters.getKeggBackendType());
        if (parameters.getCustomKeggBackendFile() != null) {
            config.setProperty(SimpleGenomeHubConfig.KEGG_CUSTOM_BACKEND_PATH,
                parameters.getCustomKeggBackendFile().getAbsolutePath());
        }
    }

    private String buildDefaultOutputPrefix() {
        String proteinPath = resolveProteinPath();
        if (proteinPath != null && !proteinPath.trim().isEmpty()) {
            String fileName = new File(proteinPath).getName().trim();
            if (!fileName.isEmpty()) {
                String stem = stripFastaExtension(fileName);
                if (!stem.isEmpty()) {
                    return stem + "_eggnog_auto";
                }
            }
        }
        return species.getSpeciesDirectoryName() + "_eggnog_auto";
    }

    private String stripFastaExtension(String fileName) {
        String lower = fileName.toLowerCase();
        String[] extensions = {".fasta", ".fa", ".fas", ".faa", ".fsa", ".fna", ".ffn", ".pep"};
        for (String extension : extensions) {
            if (lower.endsWith(extension) && fileName.length() > extension.length()) {
                return fileName.substring(0, fileName.length() - extension.length());
            }
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String resolveProteinPath() {
        File proteinFile = new File(species.getSequenceDir(), species.getSpeciesDirectoryName() + ".proteins.fasta");
        return proteinFile.getAbsolutePath();
    }

    private void appendStatus(String message) {
        statusArea.append(message);
        if (!message.endsWith("\n")) {
            statusArea.append("\n");
        }
        statusArea.setCaretPosition(statusArea.getDocument().getLength());
    }
}
