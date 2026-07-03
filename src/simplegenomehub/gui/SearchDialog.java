/*
 * Search Dialog
 */
package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;
import simplegenomehub.util.search.SearchCriteria;
import simplegenomehub.util.search.SpeciesSearchEngine;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Advanced search dialog for filtering species collections
 * Provides comprehensive search capabilities with multiple criteria
 * 
 * @author SimpleGenomeHub
 */
public class SearchDialog extends JDialog {
    
    private SpeciesManager speciesManager;
    private SearchResultListener resultListener;
    
    // Search criteria
    private SearchCriteria currentCriteria;
    
    // Text search components
    private JTextField speciesNameField;
    private JTextField versionField;
    private JTextArea notesField;
    private JCheckBox caseSensitiveCheck;
    private JCheckBox regexCheck;
    
    // File existence filters
    private JCheckBox hasGenomeCheck;
    private JCheckBox hasAnnotationCheck;
    private JCheckBox hasExtractedCheck;
    private JCheckBox hasStatisticsCheck;
    
    // Numerical range components
    private JTextField minGenomeSizeField;
    private JTextField maxGenomeSizeField;
    private JTextField minGeneCountField;
    private JTextField maxGeneCountField;
    private JTextField minGcContentField;
    private JTextField maxGcContentField;
    
    // Date components
    private JSpinner importedAfterSpinner;
    private JSpinner importedBeforeSpinner;
    
    // Metadata components
    private JTextField assemblyLevelField;
    private JTextField annotationSourceField;
    
    // Control components
    private JButton searchButton;
    private JButton clearButton;
    private JButton closeButton;
    
    // Results components
    private JLabel resultsLabel;
    private JTextArea statisticsArea;
    
    /**
     * Interface for search result callbacks
     */
    public interface SearchResultListener {
        void onSearchResults(List<SpeciesInfo> results, SpeciesSearchEngine.SearchStatistics statistics);
    }
    
    /**
     * Constructor
     */
    public SearchDialog(Window parent, SpeciesManager speciesManager, SearchResultListener resultListener) {
        super(parent, "Advanced Species Search", ModalityType.APPLICATION_MODAL);
        
        this.speciesManager = speciesManager;
        this.resultListener = resultListener;
        this.currentCriteria = new SearchCriteria();
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(600, 700);
        setLocationRelativeTo(parent);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Text search components
        speciesNameField = new JTextField(20);
        versionField = new JTextField(10);
        notesField = new JTextArea(2, 20);
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        caseSensitiveCheck = new JCheckBox("Case sensitive");
        regexCheck = new JCheckBox("Use regular expressions");
        
        // File existence checkboxes (tri-state)
        hasGenomeCheck = new JCheckBox("Has genome file");
        hasAnnotationCheck = new JCheckBox("Has annotation file");
        hasExtractedCheck = new JCheckBox("Has extracted sequences");
        hasStatisticsCheck = new JCheckBox("Has statistics data");
        
        // Numerical range fields
        minGenomeSizeField = new JTextField(10);
        maxGenomeSizeField = new JTextField(10);
        minGeneCountField = new JTextField(8);
        maxGeneCountField = new JTextField(8);
        minGcContentField = new JTextField(6);
        maxGcContentField = new JTextField(6);
        
        // Date spinners
        importedAfterSpinner = new JSpinner(new SpinnerDateModel());
        importedBeforeSpinner = new JSpinner(new SpinnerDateModel());
        
        JSpinner.DateEditor afterEditor = new JSpinner.DateEditor(importedAfterSpinner, "yyyy-MM-dd");
        JSpinner.DateEditor beforeEditor = new JSpinner.DateEditor(importedBeforeSpinner, "yyyy-MM-dd");
        importedAfterSpinner.setEditor(afterEditor);
        importedBeforeSpinner.setEditor(beforeEditor);
        
        // Metadata fields
        assemblyLevelField = new JTextField(15);
        annotationSourceField = new JTextField(15);
        
        // Control buttons
        searchButton = new JButton("Search");
        clearButton = new JButton("Clear All");
        closeButton = new JButton("Close");
        
        // Results components
        resultsLabel = new JLabel("Enter search criteria and click Search");
        statisticsArea = new JTextArea(4, 50);
        statisticsArea.setEditable(false);
        statisticsArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_11);
        statisticsArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // Set button sizes
        searchButton.setPreferredSize(new Dimension(100, 30));
        clearButton.setPreferredSize(new Dimension(100, 30));
        closeButton.setPreferredSize(new Dimension(80, 30));
        
        // Set default date values
        Date now = new Date();
        Date weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000L);
        importedAfterSpinner.setValue(weekAgo);
        importedBeforeSpinner.setValue(now);
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        // Create tabbed pane for different search categories
        JTabbedPane tabbedPane = new JTabbedPane();
        
        // Text search tab
        JPanel textPanel = createTextSearchPanel();
        tabbedPane.addTab("Text Search", textPanel);
        
        // File filters tab
        JPanel filePanel = createFileFiltersPanel();
        tabbedPane.addTab("File Filters", filePanel);
        
        // Numerical filters tab
        JPanel numericalPanel = createNumericalFiltersPanel();
        tabbedPane.addTab("Numerical Filters", numericalPanel);
        
        // Date and metadata tab
        JPanel metadataPanel = createMetadataPanel();
        tabbedPane.addTab("Date & Metadata", metadataPanel);
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        // Results panel
        JPanel resultsPanel = createResultsPanel();
        mainPanel.add(resultsPanel, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create text search panel
     */
    private JPanel createTextSearchPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Species name
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Species Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(speciesNameField, gbc);
        
        // Version
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Version:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(versionField, gbc);
        
        // Notes
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(new JLabel("Notes:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weighty = 0.3;
        panel.add(new JScrollPane(notesField), gbc);
        
        // Options
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionsPanel.add(caseSensitiveCheck);
        optionsPanel.add(regexCheck);
        panel.add(optionsPanel, gbc);
        
        return panel;
    }
    
    /**
     * Create file filters panel
     */
    private JPanel createFileFiltersPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("File Existence Filters"));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        
        panel.add(hasGenomeCheck);
        panel.add(hasAnnotationCheck);
        panel.add(hasExtractedCheck);
        panel.add(hasStatisticsCheck);
        
        // Add description
        JTextArea descArea = new JTextArea(
            "Check boxes to filter species based on file availability:\n" +
            "• Checked: Must have the file type\n" +
            "• Unchecked: File type is ignored in search\n\n" +
            "Use this to find complete or incomplete species entries.");
        descArea.setEditable(false);
        descArea.setOpaque(false);
        descArea.setFont(SimpleGenomeHubStyle.plain(descArea.getFont(), 11f));
        descArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        panel.add(descArea);
        
        return panel;
    }
    
    /**
     * Create numerical filters panel
     */
    private JPanel createNumericalFiltersPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Genome size range
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Genome Size (bp):"), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.CENTER;
        panel.add(new JLabel("Min:"), gbc);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.3;
        panel.add(minGenomeSizeField, gbc);
        gbc.gridx = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Max:"), gbc);
        gbc.gridx = 4; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.3;
        panel.add(maxGenomeSizeField, gbc);
        
        // Gene count range
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Gene Count:"), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel("Min:"), gbc);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.3;
        panel.add(minGeneCountField, gbc);
        gbc.gridx = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Max:"), gbc);
        gbc.gridx = 4; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.3;
        panel.add(maxGeneCountField, gbc);
        
        // GC content range
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("GC Content (%):"), gbc);
        gbc.gridx = 1;
        panel.add(new JLabel("Min:"), gbc);
        gbc.gridx = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.3;
        panel.add(minGcContentField, gbc);
        gbc.gridx = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        panel.add(new JLabel("Max:"), gbc);
        gbc.gridx = 4; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 0.3;
        panel.add(maxGcContentField, gbc);
        
        // Add filler
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 5; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JPanel(), gbc);
        
        return panel;
    }
    
    /**
     * Create metadata panel
     */
    private JPanel createMetadataPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Date range
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Imported After:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(importedAfterSpinner, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Imported Before:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(importedBeforeSpinner, gbc);
        
        // Assembly level
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Assembly Level:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(assemblyLevelField, gbc);
        
        // Annotation source
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Annotation Source:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(annotationSourceField, gbc);
        
        // Add filler
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(new JPanel(), gbc);
        
        return panel;
    }
    
    /**
     * Create results panel
     */
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Search Results"));
        
        panel.add(resultsLabel, BorderLayout.NORTH);
        
        JScrollPane scrollPane = new JScrollPane(statisticsArea);
        scrollPane.setPreferredSize(new Dimension(580, 80));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create button panel
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEtchedBorder());
        
        panel.add(searchButton);
        panel.add(clearButton);
        panel.add(closeButton);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        searchButton.addActionListener(e -> performSearch());
        clearButton.addActionListener(e -> clearAllFields());
        closeButton.addActionListener(e -> dispose());
    }
    
    /**
     * Perform search based on current criteria
     */
    private void performSearch() {
        try {
            // Build search criteria from form
            buildSearchCriteria();
            
            // Perform search
            List<SpeciesInfo> allSpecies = speciesManager.getAllSpecies();
            List<SpeciesInfo> results = SpeciesSearchEngine.search(allSpecies, currentCriteria);
            SpeciesSearchEngine.SearchStatistics statistics = 
                SpeciesSearchEngine.getSearchStatistics(allSpecies, results);
            
            // Update results display
            updateResultsDisplay(statistics);
            
            // Notify listener
            if (resultListener != null) {
                resultListener.onSearchResults(results, statistics);
            }
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Search error: " + e.getMessage(),
                "Search Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Build search criteria from form inputs
     */
    private void buildSearchCriteria() {
        currentCriteria.clear();
        
        // Text criteria
        String speciesName = speciesNameField.getText().trim();
        if (!speciesName.isEmpty()) {
            currentCriteria.setSpeciesNamePattern(speciesName);
        }
        
        String version = versionField.getText().trim();
        if (!version.isEmpty()) {
            currentCriteria.setVersionPattern(version);
        }
        
        String notes = notesField.getText().trim();
        if (!notes.isEmpty()) {
            currentCriteria.setNotesPattern(notes);
        }
        
        currentCriteria.setCaseSensitive(caseSensitiveCheck.isSelected());
        currentCriteria.setUseRegex(regexCheck.isSelected());
        
        // File existence criteria
        if (hasGenomeCheck.isSelected()) {
            currentCriteria.setHasGenomeFile(true);
        }
        if (hasAnnotationCheck.isSelected()) {
            currentCriteria.setHasAnnotationFile(true);
        }
        if (hasExtractedCheck.isSelected()) {
            currentCriteria.setHasExtractedSequences(true);
        }
        if (hasStatisticsCheck.isSelected()) {
            currentCriteria.setHasStatistics(true);
        }
        
        // Numerical criteria
        try {
            String minGenomeSize = minGenomeSizeField.getText().trim();
            if (!minGenomeSize.isEmpty()) {
                currentCriteria.setMinGenomeSize(Long.parseLong(minGenomeSize));
            }
            
            String maxGenomeSize = maxGenomeSizeField.getText().trim();
            if (!maxGenomeSize.isEmpty()) {
                currentCriteria.setMaxGenomeSize(Long.parseLong(maxGenomeSize));
            }
            
            String minGeneCount = minGeneCountField.getText().trim();
            if (!minGeneCount.isEmpty()) {
                currentCriteria.setMinGeneCount(Integer.parseInt(minGeneCount));
            }
            
            String maxGeneCount = maxGeneCountField.getText().trim();
            if (!maxGeneCount.isEmpty()) {
                currentCriteria.setMaxGeneCount(Integer.parseInt(maxGeneCount));
            }
            
            String minGcContent = minGcContentField.getText().trim();
            if (!minGcContent.isEmpty()) {
                currentCriteria.setMinGcContent(Double.parseDouble(minGcContent));
            }
            
            String maxGcContent = maxGcContentField.getText().trim();
            if (!maxGcContent.isEmpty()) {
                currentCriteria.setMaxGcContent(Double.parseDouble(maxGcContent));
            }
            
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numerical value: " + e.getMessage());
        }
        
        // Date criteria (only if different from defaults)
        Date defaultAfter = new Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L);
        Date defaultBefore = new Date();
        
        Date selectedAfter = (Date) importedAfterSpinner.getValue();
        Date selectedBefore = (Date) importedBeforeSpinner.getValue();
        
        if (Math.abs(selectedAfter.getTime() - defaultAfter.getTime()) > 60000) { // More than 1 minute difference
            currentCriteria.setImportedAfter(selectedAfter);
        }
        
        if (Math.abs(selectedBefore.getTime() - defaultBefore.getTime()) > 60000) {
            currentCriteria.setImportedBefore(selectedBefore);
        }
        
        // Metadata criteria
        String assemblyLevel = assemblyLevelField.getText().trim();
        if (!assemblyLevel.isEmpty()) {
            currentCriteria.setAssemblyLevel(assemblyLevel);
        }
        
        String annotationSource = annotationSourceField.getText().trim();
        if (!annotationSource.isEmpty()) {
            currentCriteria.setAnnotationSource(annotationSource);
        }
    }
    
    /**
     * Update results display
     */
    private void updateResultsDisplay(SpeciesSearchEngine.SearchStatistics statistics) {
        resultsLabel.setText(statistics.getSummary());
        
        StringBuilder statsText = new StringBuilder();
        statsText.append("Search Results Summary:\n");
        statsText.append("======================\n");
        statsText.append(String.format("Total species in collection: %d\n", statistics.getTotalSpecies()));
        statsText.append(String.format("Species matching criteria: %d (%.1f%%)\n", 
            statistics.getMatchingSpecies(), statistics.getMatchPercentage()));
        statsText.append("\nMatching species breakdown:\n");
        statsText.append(String.format("  With genome files: %d\n", statistics.getSpeciesWithGenome()));
        statsText.append(String.format("  With annotation files: %d\n", statistics.getSpeciesWithAnnotation()));
        statsText.append(String.format("  With statistics: %d\n", statistics.getSpeciesWithStatistics()));
        statsText.append(String.format("  With extracted sequences: %d\n", statistics.getSpeciesWithExtracted()));
        
        statisticsArea.setText(statsText.toString());
    }
    
    /**
     * Clear all search fields
     */
    private void clearAllFields() {
        speciesNameField.setText("");
        versionField.setText("");
        notesField.setText("");
        caseSensitiveCheck.setSelected(false);
        regexCheck.setSelected(false);
        
        hasGenomeCheck.setSelected(false);
        hasAnnotationCheck.setSelected(false);
        hasExtractedCheck.setSelected(false);
        hasStatisticsCheck.setSelected(false);
        
        minGenomeSizeField.setText("");
        maxGenomeSizeField.setText("");
        minGeneCountField.setText("");
        maxGeneCountField.setText("");
        minGcContentField.setText("");
        maxGcContentField.setText("");
        
        Date now = new Date();
        Date weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000L);
        importedAfterSpinner.setValue(weekAgo);
        importedBeforeSpinner.setValue(now);
        
        assemblyLevelField.setText("");
        annotationSourceField.setText("");
        
        resultsLabel.setText("Enter search criteria and click Search");
        statisticsArea.setText("");
        
        currentCriteria.clear();
    }
}
