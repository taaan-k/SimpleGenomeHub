package simplegenomehub.gui;

import simplegenomehub.util.identification.SpeciesMatchResult;
import simplegenomehub.util.identification.SpeciesMatchResult.SequenceMatch;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Dialog showing detailed match information for a species identification result
 */
public class SpeciesMatchDetailsDialog extends JDialog {
    
    private SpeciesMatchResult result;
    private JTable matchedTable;
    private JTable unmatchedTable;
    private MatchedTableModel matchedTableModel;
    private UnmatchedTableModel unmatchedTableModel;
    private JButton exportMatchedButton;
    private JButton exportUnmatchedButton;
    private JButton closeButton;
    
    public SpeciesMatchDetailsDialog(Dialog parent, SpeciesMatchResult result) {
        super(parent, "Species Match Details", true);
        this.result = result;
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(parent);
    }
    
    private void initializeComponents() {
        // Matched sequences table
        matchedTableModel = new MatchedTableModel();
        matchedTable = new JTable(matchedTableModel);
        matchedTable.setRowHeight(22);
        matchedTable.getTableHeader().setReorderingAllowed(false);
        matchedTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // Unmatched sequences table
        unmatchedTableModel = new UnmatchedTableModel();
        unmatchedTable = new JTable(unmatchedTableModel);
        unmatchedTable.setRowHeight(22);
        unmatchedTable.getTableHeader().setReorderingAllowed(false);
        unmatchedTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        
        // Setup renderers
        setupTableRenderers();
        
        // Buttons
        exportMatchedButton = new JButton("Export Matched");
        exportUnmatchedButton = new JButton("Export Unmatched");
        closeButton = new JButton("Close");
    }
    
    private void setupTableRenderers() {
        // Match type renderer for matched table
        matchedTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (value instanceof SpeciesMatchResult.MatchType) {
                    SpeciesMatchResult.MatchType type = (SpeciesMatchResult.MatchType) value;
                    setText(type.getDescription());
                    
                    if (!isSelected) {
                        switch (type) {
                            case EXACT:
                                setBackground(new Color(212, 237, 218)); // Light green
                                break;
                            case PATTERN:
                                setBackground(new Color(217, 237, 247)); // Light blue
                                break;
                            case FUZZY:
                                setBackground(new Color(255, 243, 205)); // Light yellow
                                break;
                            default:
                                setBackground(Color.WHITE);
                                break;
                        }
                    }
                }
                
                return this;
            }
        });
        
        // Similarity renderer
        matchedTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (value instanceof Double) {
                    double similarity = (Double) value;
                    setText(String.format("%.1f%%", similarity * 100));
                    setHorizontalAlignment(SwingConstants.RIGHT);
                }
                
                return this;
            }
        });
    }
    
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Header panel with species information
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(new TitledBorder("Species Information"));
        
        JPanel infoPanel = new JPanel(new GridLayout(3, 2, 10, 5));
        infoPanel.add(new JLabel("Species:"));
        infoPanel.add(new JLabel(result.getSpecies().getSpeciesName()));
        infoPanel.add(new JLabel("Version:"));
        infoPanel.add(new JLabel(result.getSpecies().getVersion()));
        infoPanel.add(new JLabel("Overall Confidence:"));
        
        JLabel confidenceLabel = new JLabel(result.getMatchPercentage() + 
            " (" + result.getMatchRatio() + " sequences matched)");
        confidenceLabel.setFont(SimpleGenomeHubStyle.bold(confidenceLabel.getFont()));
        
        // Color code confidence
        double confidence = result.getConfidenceScore();
        if (confidence > 0.8) {
            confidenceLabel.setForeground(new Color(40, 167, 69)); // Green
        } else if (confidence > 0.5) {
            confidenceLabel.setForeground(new Color(255, 193, 7)); // Yellow/Orange
        } else {
            confidenceLabel.setForeground(new Color(220, 53, 69)); // Red
        }
        
        infoPanel.add(confidenceLabel);
        headerPanel.add(infoPanel, BorderLayout.CENTER);
        
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Split pane for matched/unmatched sequences
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        SimpleGenomeHubUi.styleSplitPane(splitPane);
        splitPane.setResizeWeight(0.7); // Give more space to matched sequences
        
        // Matched sequences panel
        JPanel matchedPanel = new JPanel(new BorderLayout());
        matchedPanel.setBorder(new TitledBorder("Matched IDs (" + result.getMatchedCount() + ")"));
        
        JScrollPane matchedScrollPane = new JScrollPane(matchedTable);
        matchedScrollPane.setPreferredSize(new Dimension(750, 250));
        matchedPanel.add(matchedScrollPane, BorderLayout.CENTER);
        
        JPanel matchedButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        matchedButtonPanel.add(exportMatchedButton);
        matchedPanel.add(matchedButtonPanel, BorderLayout.SOUTH);
        
        splitPane.setTopComponent(matchedPanel);
        
        // Unmatched sequences panel
        JPanel unmatchedPanel = new JPanel(new BorderLayout());
        unmatchedPanel.setBorder(new TitledBorder("Unmatched IDs (" + result.getUnmatchedIds().size() + ")"));
        
        JScrollPane unmatchedScrollPane = new JScrollPane(unmatchedTable);
        unmatchedScrollPane.setPreferredSize(new Dimension(750, 150));
        unmatchedPanel.add(unmatchedScrollPane, BorderLayout.CENTER);
        
        JPanel unmatchedButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        unmatchedButtonPanel.add(exportUnmatchedButton);
        unmatchedPanel.add(unmatchedButtonPanel, BorderLayout.SOUTH);
        
        splitPane.setBottomComponent(unmatchedPanel);
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        
        // Bottom panel with close button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(closeButton);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        add(mainPanel, BorderLayout.CENTER);
    }
    
    private void setupEventHandlers() {
        exportMatchedButton.addActionListener(e -> exportMatchedSequences());
        exportUnmatchedButton.addActionListener(e -> exportUnmatchedSequences());
        closeButton.addActionListener(e -> dispose());
    }
    
    private void exportMatchedSequences() {
        if (result.getDetailedMatches().isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "No matched sequences to export.",
                "Export Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Matched Sequences");
        fileChooser.setSelectedFile(new File(
            result.getSpecies().getSpeciesName().replaceAll("\\s+", "_") + "_matched.txt"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File outputFile = fileChooser.getSelectedFile();
            try {
                exportMatchedToFile(outputFile);
                JOptionPane.showMessageDialog(this,
                    "Matched sequences exported successfully!",
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "Error exporting matched sequences: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exportUnmatchedSequences() {
        if (result.getUnmatchedIds().isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "No unmatched sequences to export.",
                "Export Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export Unmatched Sequences");
        fileChooser.setSelectedFile(new File(
            result.getSpecies().getSpeciesName().replaceAll("\\s+", "_") + "_unmatched.txt"));
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File outputFile = fileChooser.getSelectedFile();
            try {
                exportUnmatchedToFile(outputFile);
                JOptionPane.showMessageDialog(this,
                    "Unmatched sequences exported successfully!",
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                    "Error exporting unmatched sequences: " + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void exportMatchedToFile(File outputFile) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
        
        writer.write("Matched Sequences for " + result.getSpecies().getSpeciesName() + 
                    " (" + result.getSpecies().getVersion() + ")\\n");
        writer.write("Generated: " + new java.util.Date() + "\\n");
        writer.write("Confidence: " + result.getMatchPercentage() + "\\n");
        writer.write("=".repeat(60) + "\\n\\n");
        
        writer.write("Query ID\\tMatched ID\\tGene ID\\tMatch Type\\tSimilarity\\n");
        for (SequenceMatch match : result.getDetailedMatches()) {
            writer.write(String.format("%s\\t%s\\t%s\\t%s\\t%.2f\\n",
                match.getQueryId(),
                match.getMatchedId(),
                match.getGeneId(),
                match.getMatchType().getDescription(),
                match.getSimilarity()));
        }
        
        writer.close();
    }
    
    private void exportUnmatchedToFile(File outputFile) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
        
        writer.write("Unmatched Sequences for " + result.getSpecies().getSpeciesName() + 
                    " (" + result.getSpecies().getVersion() + ")\\n");
        writer.write("Generated: " + new java.util.Date() + "\\n");
        writer.write("=".repeat(60) + "\\n\\n");
        
        for (String unmatchedId : result.getUnmatchedIds()) {
            writer.write(unmatchedId + "\\n");
        }
        
        writer.close();
    }
    
    // Table models
    private class MatchedTableModel extends AbstractTableModel {
        private final String[] columnNames = {
            "Query ID", "Matched ID", "Gene ID", "Match Type", "Similarity"
        };
        
        @Override
        public int getRowCount() {
            return result.getDetailedMatches().size();
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<SequenceMatch> matches = result.getDetailedMatches();
            if (rowIndex >= matches.size()) return null;
            
            SequenceMatch match = matches.get(rowIndex);
            switch (columnIndex) {
                case 0: return match.getQueryId();
                case 1: return match.getMatchedId();
                case 2: return match.getGeneId();
                case 3: return match.getMatchType();
                case 4: return match.getSimilarity();
                default: return null;
            }
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 3: return SpeciesMatchResult.MatchType.class;
                case 4: return Double.class;
                default: return String.class;
            }
        }
    }
    
    private class UnmatchedTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Unmatched ID"};
        
        @Override
        public int getRowCount() {
            return result.getUnmatchedIds().size();
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            List<String> unmatched = result.getUnmatchedIds();
            if (rowIndex >= unmatched.size()) return null;
            
            return unmatched.get(rowIndex);
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }
    }
}
