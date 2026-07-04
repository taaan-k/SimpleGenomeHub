package simplegenomehub.gui;

import biocjava.bioDoer.JIGplotToolkit.Circos.SuperCircos.AmazingSuperCircos2;
import simplegenomehub.model.GenomeData;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;
import simplegenomehub.util.fileio.AdvancedCircosPreviewExporter;
import simplegenomehub.util.fileio.DualSyntenyPreviewExporter;
import simplegenomehub.util.fileio.CircosTrackGenerator;
import simplegenomehub.util.fileio.GenomeCompareExistingResultScanner;
import simplegenomehub.util.fileio.GenomeCompareHighlightedLinkAppender;
import simplegenomehub.util.fileio.GenomeCompareService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Launcher dialog for opening a generated TBtools Advanced Circos project.
 */
public class AdvancedCircosLaunchDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(AdvancedCircosLaunchDialog.class.getName());
    private static final String DIALOG_TITLE = "One-step Circos";
    private static final String GENE_SET_NONE_OPTION = "<None>";
    private static final String LINK_TRACK_OPTION = "Link";

    private final File projectDir;
    private final SpeciesInfo currentSpecies;

    private JButton startButton;
    private JCheckBox gcTrackCheckBox;
    private JCheckBox geneDensityTrackCheckBox;
    private JCheckBox linkTrackCheckBox;
    private JComboBox<LinkChoiceItem> linkReuseComboBox;
    private JTextArea chromosomeShowArea;
    private JTextArea geneHighlightArea;
    private JComboBox<String> geneSetComboBox;
    private List<File> availableGeneSetFiles;

    public AdvancedCircosLaunchDialog(Window parent, File projectDir) {
        this(parent, projectDir, null, null);
    }

    public AdvancedCircosLaunchDialog(Window parent, File projectDir, SpeciesInfo currentSpecies,
                                      SpeciesManager speciesManager) {
        super(parent, DIALOG_TITLE, ModalityType.MODELESS);
        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory cannot be null.");
        }

        this.projectDir = projectDir;
        this.currentSpecies = currentSpecies;

        initializeComponents();
        setupLayout();
        setupEventHandlers();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);
        setMinimumSize(new Dimension(860, 700));
        setSize(900, 800);
        setLocationRelativeTo(parent);
    }

    private void initializeComponents() {
        gcTrackCheckBox = createTrackCheckBox(CircosTrackGenerator.GC_TRACK_OPTION);
        geneDensityTrackCheckBox = createTrackCheckBox(CircosTrackGenerator.GENE_DENSITY_TRACK_OPTION);
        linkTrackCheckBox = createTrackCheckBox(LINK_TRACK_OPTION);
        linkReuseComboBox = new JComboBox<>();
        linkReuseComboBox.setPreferredSize(new Dimension(260, 32));
        linkReuseComboBox.setToolTipText("Reuse an existing Genome Compare result or keep New Link.");

        geneHighlightArea = new JTextArea(7, 28);
        geneHighlightArea.setLineWrap(false);
        geneHighlightArea.setWrapStyleWord(false);
        geneHighlightArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);

        geneSetComboBox = new JComboBox<>();
        geneSetComboBox.setPreferredSize(new Dimension(260, 30));
        availableGeneSetFiles = new ArrayList<>();
        refreshAvailableGeneSets();

        chromosomeShowArea = new JTextArea(7, 28);
        chromosomeShowArea.setLineWrap(false);
        chromosomeShowArea.setWrapStyleWord(false);
        chromosomeShowArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        populateChromosomeArea(chromosomeShowArea, currentSpecies);
        refreshReusableLinks();
        updateLinkReuseState();

        startButton = new JButton("Start");
        startButton.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_18);
        startButton.setPreferredSize(new Dimension(140, 42));
        startButton.setFocusPainted(false);
    }

    private JTextField createReadOnlyField(String text) {
        JTextField field = new JTextField(text);
        field.setEditable(false);
        field.setBackground(Color.WHITE);
        return field;
    }

    private JCheckBox createTrackCheckBox(String text) {
        JCheckBox button = new JCheckBox(text);
        button.setOpaque(false);
        button.setFocusPainted(false);
        return button;
    }

    private void setupLayout() {
        JPanel contentPanel = new JPanel(new BorderLayout(0, 22));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        setContentPane(contentPanel);

        JLabel titleLabel = new JLabel(DIALOG_TITLE, SwingConstants.CENTER);
        titleLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_28);
        contentPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 18, 0));
        centerPanel.setOpaque(false);
        centerPanel.add(createGenomeInfoPanel());
        centerPanel.add(createTrackInfoPanel());
        contentPanel.add(centerPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(startButton);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createGenomeInfoPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder("Genome Info"));

        JPanel contentPanel = new JPanel(new BorderLayout(0, 12));
        contentPanel.setOpaque(false);

        JPanel genomeSelectionPanel = createGenomeSelectionPanel();
        contentPanel.add(genomeSelectionPanel, BorderLayout.NORTH);

        JPanel highlightPanel = createHighlightPanel();
        contentPanel.add(highlightPanel, BorderLayout.CENTER);

        panel.add(contentPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createGenomeSelectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 6, 8);
        panel.add(new JLabel("Genome"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 6, 0);
        panel.add(createReadOnlyField(currentSpecies != null
            ? currentSpecies.getSpeciesName() + " (" + currentSpecies.getVersion() + ")"
            : "No primary species selected"), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(createChromosomeSelectionPanel("Chromosomes", chromosomeShowArea), gbc);

        return panel;
    }

    private JPanel createHighlightPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 6, 0);
        panel.add(new JLabel("Gene Highlight List"), gbc);

        gbc.gridy = 1;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        JScrollPane geneHighlightScrollPane = new JScrollPane(geneHighlightArea);
        geneHighlightScrollPane.setPreferredSize(new Dimension(0, 108));
        geneHighlightScrollPane.setMinimumSize(new Dimension(0, 96));
        panel.add(geneHighlightScrollPane, gbc);

        gbc.gridy = 2;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(12, 0, 6, 0);
        panel.add(new JLabel("GeneSet"), gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 6, 0);
        panel.add(geneSetComboBox, gbc);

        gbc.gridy = 4;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(createHintArea("Gene Highlight List and Selected GeneSet will be shown as labels."), gbc);
        return panel;
    }

    private JPanel createChromosomeSelectionPanel(String title, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.add(new JLabel(title), BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setPreferredSize(new Dimension(0, 120));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createTrackInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createTitledBorder("Track Info"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(gcTrackCheckBox, gbc);

        gbc.gridy = 1;
        panel.add(geneDensityTrackCheckBox, gbc);

        gbc.gridy = 2;
        panel.add(linkTrackCheckBox, gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(6, 24, 0, 0);
        panel.add(linkReuseComboBox, gbc);

        gbc.gridy = 4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(Box.createVerticalGlue(), gbc);
        return panel;
    }

    private JTextArea createHintArea(String text) {
        JTextArea hintArea = new JTextArea(text);
        hintArea.setEditable(false);
        hintArea.setLineWrap(true);
        hintArea.setWrapStyleWord(true);
        hintArea.setOpaque(false);
        hintArea.setFocusable(false);
        hintArea.setBorder(BorderFactory.createEmptyBorder());
        hintArea.setFont(SimpleGenomeHubStyle.italic(hintArea.getFont(), 12f));
        hintArea.setForeground(Color.GRAY);
        return hintArea;
    }

    private void setupEventHandlers() {
        startButton.addActionListener(this::handleStart);
        linkTrackCheckBox.addActionListener(e -> {
            refreshReusableLinks();
            updateLinkReuseState();
        });
        chromosomeShowArea.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                refreshReusableLinks();
            }
        });

        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke("ESCAPE");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(escapeKeyStroke, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    private void handleStart(ActionEvent event) {
        startButton.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        final List<String> selectedTracks = collectSelectedTracks();
        final boolean needLink = linkTrackCheckBox.isSelected();
        final HighlightSelection highlightSelection;
        final List<String> selectedChromosomes;
        final GenomeCompareExistingResultScanner.ReusableResult selectedReusableLink;

        try {
            highlightSelection = collectHighlightSelection();
            selectedChromosomes = parseChromosomeList(chromosomeShowArea);
            refreshReusableLinks();
            selectedReusableLink = getSelectedReusableLink();
        } catch (Exception ex) {
            setCursor(Cursor.getDefaultCursor());
            startButton.setEnabled(true);
            JOptionPane.showMessageDialog(this,
                "Failed to prepare Circos settings:\n" + ex.getMessage(),
                "Circos Settings Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                prepareGenePosFile(highlightSelection.getMergedGeneIds());
                prepareWorkDirectoryAndTracks(selectedTracks);
                CircosTrackGenerator.updateOtherParaLayoutPositions(projectDir, selectedTracks);
                prepareSelectedLinkInfo(needLink, selectedReusableLink,
                    highlightSelection.getMergedGeneIds(), selectedChromosomes);
                applyChromosomeSelectionToProject(new LinkedHashSet<>(selectedChromosomes));
                openProjectDirectly(projectDir);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    dispose();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    logger.log(Level.SEVERE, "Failed to launch Advanced Circos project.", cause);
                    setCursor(Cursor.getDefaultCursor());
                    startButton.setEnabled(true);
                    JOptionPane.showMessageDialog(AdvancedCircosLaunchDialog.this,
                        "Failed to launch Advanced Circos:\n" + cause.getMessage(),
                        "Advanced Circos Error",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                setCursor(Cursor.getDefaultCursor());
            }
        };
        worker.execute();
    }

    private void prepareWorkDirectoryAndTracks(List<String> selectedTracks) throws IOException {
        if ((selectedTracks == null || selectedTracks.isEmpty()) && currentSpecies == null) {
            CircosTrackGenerator.ensureWorkDirectory(projectDir);
            return;
        }
        CircosTrackGenerator.generateSelectedTracks(currentSpecies, projectDir, selectedTracks);
    }

    private void prepareGenePosFile(Set<String> selectedGeneIds) throws IOException {
        if (currentSpecies == null) {
            File genePosFile = new File(projectDir, CircosTrackGenerator.GENE_POS_FILE_NAME);
            Files.deleteIfExists(genePosFile.toPath());
            return;
        }

        CircosTrackGenerator.generateGenePosFile(currentSpecies, projectDir, selectedGeneIds);
    }

    private void prepareSelectedLinkInfo(boolean needLink,
                                         GenomeCompareExistingResultScanner.ReusableResult reusableLink,
                                         Set<String> highlightedGeneIds,
                                         List<String> selectedChromosomes) throws Exception {
        File circosLinkRegionFile = new File(projectDir, "LinkRegion.tab");
        if (!needLink) {
            if (circosLinkRegionFile.exists() && !circosLinkRegionFile.delete()) {
                throw new IOException("Failed to remove existing LinkRegion.tab from Circos project directory.");
            }
            return;
        }

        if (reusableLink != null) {
            copyReusableLinkInfo(reusableLink, circosLinkRegionFile, highlightedGeneIds);
            return;
        }

        if (currentSpecies == null) {
            throw new IllegalStateException("Current species is not available for Genome Compare.");
        }

        GenomeCompareService.Result result = GenomeCompareService.run(
            currentSpecies,
            currentSpecies,
            new GenomeCompareService.Parameters(
                GenomeCompareService.DEFAULT_CPU,
                GenomeCompareService.DEFAULT_EVALUE,
                GenomeCompareService.DEFAULT_NUM_HITS,
                GenomeCompareService.DEFAULT_DIRECT_PLOT,
                selectedChromosomes,
                selectedChromosomes,
                Collections.emptyList()
            ),
            null
        );
        try {
            DualSyntenyPreviewExporter.exportPreviewFromOutputDirectory(result.getOutputDir(), false);
        } catch (Exception previewEx) {
            logger.log(Level.WARNING,
                "Failed to export Dual Synteny preview image for " + result.getOutputDir().getAbsolutePath(),
                previewEx
            );
        }

        File generatedLinkRegionFile = result.getLinkRegionFile();
        if (generatedLinkRegionFile == null || !generatedLinkRegionFile.isFile()) {
            throw new IOException("Genome Compare did not generate LinkRegion.tab.");
        }

        Files.copy(generatedLinkRegionFile.toPath(), circosLinkRegionFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        if (highlightedGeneIds != null && !highlightedGeneIds.isEmpty()) {
            GenomeCompareHighlightedLinkAppender.HighlightAppendResult appendResult =
                GenomeCompareHighlightedLinkAppender.prependHighlightedLinks(
                    result.getOutputDir(),
                    result.getPrimaryCollinearityFile(),
                    circosLinkRegionFile,
                    highlightedGeneIds
                );
            CircosTrackGenerator.updateGenePosColors(
                projectDir,
                appendResult.getMatchedInputGeneIds(),
                CircosTrackGenerator.HIGHLIGHTED_GENE_POS_COLOR
            );
        }
    }

    private void copyReusableLinkInfo(GenomeCompareExistingResultScanner.ReusableResult reusableLink,
                                      File circosLinkRegionFile,
                                      Set<String> highlightedGeneIds) throws IOException {
        File reusableOutputDir = reusableLink == null ? null : reusableLink.getOutputDir();
        if (reusableOutputDir == null || !reusableOutputDir.isDirectory()) {
            throw new IOException("Selected reusable Link result is missing its output directory.");
        }

        File reusableLinkRegionFile = new File(reusableOutputDir, "LinkRegion.tab");
        if (!reusableLinkRegionFile.isFile()) {
            throw new IOException("Selected reusable Link result is missing LinkRegion.tab.");
        }

        Files.copy(reusableLinkRegionFile.toPath(), circosLinkRegionFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        if (highlightedGeneIds == null || highlightedGeneIds.isEmpty()) {
            return;
        }

        File primaryCollinearityFile = findPrimaryCollinearityFile(reusableOutputDir);
        if (primaryCollinearityFile == null || !primaryCollinearityFile.isFile()) {
            throw new IOException("Selected reusable Link result is missing a .collinearity file.");
        }

        GenomeCompareHighlightedLinkAppender.HighlightAppendResult appendResult =
            GenomeCompareHighlightedLinkAppender.prependHighlightedLinks(
                reusableOutputDir,
                primaryCollinearityFile,
                circosLinkRegionFile,
                highlightedGeneIds
            );
        CircosTrackGenerator.updateGenePosColors(
            projectDir,
            appendResult.getMatchedInputGeneIds(),
            CircosTrackGenerator.HIGHLIGHTED_GENE_POS_COLOR
        );
    }

    private void refreshAvailableGeneSets() {
        availableGeneSetFiles = loadGeneSetFiles();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(GENE_SET_NONE_OPTION);
        for (File geneSetFile : availableGeneSetFiles) {
            model.addElement(GeneSetFileSupport.extractDisplayName(geneSetFile));
        }
        geneSetComboBox.setModel(model);
        geneSetComboBox.setSelectedIndex(0);
    }

    private List<File> loadGeneSetFiles() {
        List<File> files = new ArrayList<>();
        if (currentSpecies == null) {
            return files;
        }

        File geneSetDir = currentSpecies.getGeneSetDir();
        if (geneSetDir == null || !geneSetDir.isDirectory()) {
            return files;
        }

        File[] geneSetFiles = geneSetDir.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase(Locale.ROOT);
            return lowerName.endsWith(GeneSetFileSupport.GENE_SET_SUFFIX);
        });

        if (geneSetFiles == null) {
            return files;
        }

        java.util.Arrays.sort(geneSetFiles, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        Collections.addAll(files, geneSetFiles);
        return files;
    }

    private HighlightSelection collectHighlightSelection() throws Exception {
        LinkedHashSet<String> mergedGeneIds = new LinkedHashSet<>();

        List<String> typedGeneIds = GeneSetFileSupport.parseGeneIds(geneHighlightArea.getText());
        if (!typedGeneIds.isEmpty()) {
            GeneSetGeneIdValidationSupport.ValidationResult validationResult =
                GeneSetGeneIdValidationSupport.validateGeneIds(currentSpecies, typedGeneIds);
            if (validationResult.hasIssues()) {
                GeneSetGeneIdValidationSupport.showValidationResult(this, validationResult);
            }
            if (!validationResult.getMissingGeneIds().isEmpty()) {
                throw new IllegalArgumentException("Some typed IDs could not be validated.");
            }

            List<String> resolvedGeneIds = validationResult.getResolvedGeneIds();
            geneHighlightArea.setText(GeneSetFileSupport.formatGeneIds(resolvedGeneIds));
            mergedGeneIds.addAll(resolvedGeneIds);
        }

        File selectedGeneSetFile = resolveSelectedGeneSetFile();
        if (selectedGeneSetFile != null) {
            String content = GeneSetFileSupport.readGeneSetContent(selectedGeneSetFile);
            mergedGeneIds.addAll(GeneSetFileSupport.parseGeneIds(content));
        }

        return new HighlightSelection(mergedGeneIds);
    }

    private List<String> collectSelectedTracks() {
        List<String> selectedTracks = new ArrayList<>();
        if (gcTrackCheckBox.isSelected()) {
            selectedTracks.add(CircosTrackGenerator.GC_TRACK_OPTION);
        }
        if (geneDensityTrackCheckBox.isSelected()) {
            selectedTracks.add(CircosTrackGenerator.GENE_DENSITY_TRACK_OPTION);
        }
        return selectedTracks;
    }

    private GenomeCompareExistingResultScanner.ReusableResult getSelectedReusableLink() {
        if (!linkTrackCheckBox.isSelected()) {
            return null;
        }
        Object selectedItem = linkReuseComboBox.getSelectedItem();
        if (!(selectedItem instanceof LinkChoiceItem)) {
            return null;
        }
        return ((LinkChoiceItem) selectedItem).reusableResult;
    }

    private File resolveSelectedGeneSetFile() {
        int selectedIndex = geneSetComboBox.getSelectedIndex();
        if (selectedIndex <= 0) {
            return null;
        }

        int fileIndex = selectedIndex - 1;
        if (fileIndex < 0 || fileIndex >= availableGeneSetFiles.size()) {
            return null;
        }
        return availableGeneSetFiles.get(fileIndex);
    }

    private void refreshReusableLinks() {
        GenomeCompareExistingResultScanner.ReusableResult previouslySelected = getSelectedReusableLink();
        DefaultComboBoxModel<LinkChoiceItem> model = new DefaultComboBoxModel<>();
        model.addElement(new LinkChoiceItem("New Link", null));

        if (currentSpecies != null) {
            List<String> selectedChromosomes = parseChromosomeList(chromosomeShowArea);
            for (GenomeCompareExistingResultScanner.ReusableResult reusableResult
                : GenomeCompareExistingResultScanner.findReusableResults(
                    currentSpecies, selectedChromosomes, currentSpecies, selectedChromosomes)) {
                model.addElement(new LinkChoiceItem(reusableResult.getSelectionLabel(), reusableResult));
            }
        }

        linkReuseComboBox.setModel(model);
        linkReuseComboBox.setSelectedIndex(resolveReusableSelectionIndex(model, previouslySelected));
        updateLinkReuseState();
    }

    private int resolveReusableSelectionIndex(DefaultComboBoxModel<LinkChoiceItem> model,
                                             GenomeCompareExistingResultScanner.ReusableResult selectedReusableResult) {
        if (model == null || selectedReusableResult == null) {
            return 0;
        }
        for (int i = 0; i < model.getSize(); i++) {
            LinkChoiceItem item = model.getElementAt(i);
            if (item != null && item.matches(selectedReusableResult)) {
                return i;
            }
        }
        return 0;
    }

    private void updateLinkReuseState() {
        if (linkReuseComboBox != null) {
            linkReuseComboBox.setEnabled(linkTrackCheckBox != null && linkTrackCheckBox.isSelected());
        }
    }

    private static final class HighlightSelection {
        private final LinkedHashSet<String> mergedGeneIds;

        private HighlightSelection(Set<String> mergedGeneIds) {
            this.mergedGeneIds = new LinkedHashSet<>(mergedGeneIds);
        }

        private LinkedHashSet<String> getMergedGeneIds() {
            return new LinkedHashSet<>(mergedGeneIds);
        }
    }

    private List<String> parseChromosomeList(JTextArea area) {
        LinkedHashSet<String> chromosomes = new LinkedHashSet<>();
        if (area == null) {
            return null;
        }
        String content = area.getText();
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = content.split("[\\s,;]+");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                chromosomes.add(trimmed);
            }
        }
        return new ArrayList<>(chromosomes);
    }

    private void populateChromosomeArea(JTextArea area, SpeciesInfo species) {
        if (area == null) {
            return;
        }
        List<String> chromosomes = resolveChromosomeNames(species);
        area.setText(String.join(System.lineSeparator(), chromosomes));
        area.setCaretPosition(0);
    }

    private List<String> resolveChromosomeNames(SpeciesInfo species) {
        List<String> chromosomes = new ArrayList<>();
        if (species != null) {
            if (species.getGenomeData() != null) {
                for (GenomeData.ChromosomeStat chromosomeStat : species.getGenomeData().getChromosomeStats()) {
                    if (chromosomeStat == null || chromosomeStat.getName() == null) {
                        continue;
                    }
                    String name = chromosomeStat.getName().trim();
                    if (!name.isEmpty()) {
                        chromosomes.add(name);
                    }
                }
            }
            if (chromosomes.isEmpty()) {
                File genomeFile = species.getGenomeFile();
                if (genomeFile != null && genomeFile.isFile()) {
                    LinkedHashSet<String> fallbackChromosomes = new LinkedHashSet<>();
                    try (BufferedReader reader = Files.newBufferedReader(genomeFile.toPath(), StandardCharsets.UTF_8)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String trimmed = line.trim();
                            if (!trimmed.startsWith(">")) {
                                continue;
                            }
                            String header = trimmed.substring(1).trim();
                            if (!header.isEmpty()) {
                                fallbackChromosomes.add(header.split("\\s+")[0]);
                            }
                        }
                    } catch (IOException ignored) {
                    }
                    chromosomes.addAll(fallbackChromosomes);
                }
            }
        }
        return chromosomes;
    }

    private File findPrimaryCollinearityFile(File outputDir) {
        if (outputDir == null || !outputDir.isDirectory()) {
            return null;
        }
        File[] collinearityFiles = outputDir.listFiles((dir, name) ->
            name != null && name.toLowerCase(Locale.ROOT).endsWith(".collinearity"));
        if (collinearityFiles == null || collinearityFiles.length == 0) {
            return null;
        }
        java.util.Arrays.sort(collinearityFiles, java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return collinearityFiles[0];
    }

    private void applyChromosomeSelectionToProject(Set<String> selectedChromosomes) throws IOException {
        filterChrInfoFileBySelectedOrder(new File(projectDir, "ChrInfo.tab"), selectedChromosomes);
        filterSingleChromosomeFile(new File(projectDir, CircosTrackGenerator.GENE_POS_FILE_NAME), selectedChromosomes);
        filterLinkRegionFile(new File(projectDir, "LinkRegion.tab"), selectedChromosomes);
        filterTrackFiles(selectedChromosomes);
    }

    private void filterChrInfoFileBySelectedOrder(File file, Set<String> selectedChromosomes) throws IOException {
        if (!file.isFile()) {
            return;
        }

        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        java.util.LinkedHashMap<String, String> chromosomeLineMap = new java.util.LinkedHashMap<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length < 1) {
                continue;
            }
            String chromosome = fields[0] == null ? "" : fields[0].trim();
            if (!chromosome.isEmpty() && !chromosomeLineMap.containsKey(chromosome)) {
                chromosomeLineMap.put(chromosome, line);
            }
        }

        List<String> keptLines = new ArrayList<>();
        for (String chromosome : selectedChromosomes) {
            String line = chromosomeLineMap.get(chromosome);
            if (line != null) {
                keptLines.add(line);
            }
        }

        rewriteOrDelete(file, keptLines);
    }

    private void filterTrackFiles(Set<String> selectedChromosomes) throws IOException {
        File trackInfoFile = new File(projectDir, CircosTrackGenerator.TRACK_INFO_FILE_NAME);
        if (!trackInfoFile.isFile()) {
            return;
        }
        List<String> lines = Files.readAllLines(trackInfoFile.toPath(), StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.regionMatches(true, 0, "Track File:", 0, "Track File:".length())) {
                continue;
            }
            String[] fields = line.split("\t", 2);
            if (fields.length < 2) {
                continue;
            }
            String path = fields[1] == null ? "" : fields[1].trim();
            if (!path.isEmpty()) {
                filterSingleChromosomeFile(new File(path), selectedChromosomes);
            }
        }
    }

    private void filterSingleChromosomeFile(File file, Set<String> selectedChromosomes) throws IOException {
        if (!file.isFile()) {
            return;
        }
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        List<String> keptLines = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length < 1) {
                continue;
            }
            String chromosome = fields[0] == null ? "" : fields[0].trim();
            if (selectedChromosomes.contains(chromosome)) {
                keptLines.add(line);
            }
        }
        rewriteOrDelete(file, keptLines);
    }

    private void filterLinkRegionFile(File file, Set<String> selectedChromosomes) throws IOException {
        if (!file.isFile()) {
            return;
        }
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        List<String> keptLines = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length < 6) {
                continue;
            }
            String leftChromosome = fields[0] == null ? "" : fields[0].trim();
            String rightChromosome = fields[3] == null ? "" : fields[3].trim();
            if (selectedChromosomes.contains(leftChromosome) && selectedChromosomes.contains(rightChromosome)) {
                keptLines.add(line);
            }
        }
        rewriteOrDelete(file, keptLines);
    }

    private void rewriteOrDelete(File file, List<String> lines) throws IOException {
        if (lines.isEmpty()) {
            Files.deleteIfExists(file.toPath());
            return;
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }

    public static void openProjectDirectly(File projectDir) throws Exception {
        if (projectDir == null || !projectDir.isDirectory()) {
            throw new IllegalArgumentException("Advanced Circos project directory is not available.");
        }

        File chrInfoFile = new File(projectDir, "ChrInfo.tab");
        if (!chrInfoFile.exists() || !chrInfoFile.canRead()) {
            throw new IllegalArgumentException("ChrInfo.tab is missing or unreadable:\n" + chrInfoFile.getAbsolutePath());
        }

        AmazingSuperCircos2 circos = new AmazingSuperCircos2();
        circos.setInChrInfo(chrInfoFile);

        File geneInfoFile = new File(projectDir, "GenePos.tab");
        if (geneInfoFile.exists() && geneInfoFile.canRead()) {
            circos.setGeneInfo(geneInfoFile);
        }

        File linkInfoFile = new File(projectDir, "LinkRegion.tab");
        if (linkInfoFile.exists() && linkInfoFile.canRead()) {
            circos.setLinkInfo(linkInfoFile);
        }

        circos.readConfigDir(projectDir);
        circos.process();
        try {
            AdvancedCircosPreviewExporter.exportPreview(circos, projectDir);
        } catch (IOException previewEx) {
            logger.log(Level.WARNING,
                "Failed to export Advanced Circos preview image for " + projectDir.getAbsolutePath(),
                previewEx
            );
        }
    }

    private static final class LinkChoiceItem {
        private final String label;
        private final GenomeCompareExistingResultScanner.ReusableResult reusableResult;

        private LinkChoiceItem(String label, GenomeCompareExistingResultScanner.ReusableResult reusableResult) {
            this.label = label == null ? "" : label;
            this.reusableResult = reusableResult;
        }

        private boolean matches(GenomeCompareExistingResultScanner.ReusableResult other) {
            if (reusableResult == null || other == null) {
                return reusableResult == null && other == null;
            }
            return reusableResult.equals(other);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
