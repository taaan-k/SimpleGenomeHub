/*
 * Annotation Type Selection Dialog
 * Provides a selection interface for choosing specific annotation types to view
 */
package simplegenomehub.gui;

import simplegenomehub.model.*;
import simplegenomehub.model.GeneAnnotationData.AnnotationType;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for selecting annotation type to view/manage
 * Provides an organized way to access specific annotation types
 * 
 * @author SimpleGenomeHub
 */
public class AnnotationTypeSelectionDialog extends JDialog {
    
    private SpeciesInfo targetSpecies;
    private GeneAnnotationData annotationData;
    private AnnotationType selectedType;
    private boolean selectionMade = false;
    
    // UI Components
    private JPanel typeButtonPanel;
    private JTextArea summaryArea;
    private JButton viewAllButton;
    private JButton cancelButton;
    
    /**
     * Constructor
     */
    public AnnotationTypeSelectionDialog(Window parent, SpeciesInfo species) {
        super(parent, "Select Annotation Type - " + species.getSpeciesName(), ModalityType.MODELESS);
        this.targetSpecies = species;
        this.annotationData = species.getFunctionalAnnotations();
        
        // If no annotation data is loaded, try to load metadata first
        if (this.annotationData == null && species.hasFunctionalAnnotations()) {
            System.out.println("DEBUG: Annotation data not loaded, attempting to load metadata...");
            boolean loaded = species.loadFunctionalAnnotations();
            if (loaded) {
                this.annotationData = species.getFunctionalAnnotations();
                System.out.println("DEBUG: Functional annotations metadata loaded successfully");
            } else {
                System.out.println("DEBUG: Failed to load functional annotations metadata");
            }
        }
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        updateSummary();
        
        setSize(600, 600);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        typeButtonPanel = new JPanel();
        typeButtonPanel.setLayout(new BoxLayout(typeButtonPanel, BoxLayout.Y_AXIS));
        
        summaryArea = new JTextArea(8, 40);
        summaryArea.setEditable(false);
        summaryArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        summaryArea.setBackground(new Color(248, 248, 248));
        
        viewAllButton = new JButton("View All Types Mixed");
        viewAllButton.setToolTipText("Open annotation management with all types displayed together");
        
        cancelButton = new JButton("Cancel");
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // Top: Instructions
        JLabel instructionLabel = new JLabel(
            "<html><b>Functional Annotation Type Selection</b><br>" + 
            "Choose a specific annotation type to view and manage, or view all types together.</html>");
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        add(instructionLabel, BorderLayout.NORTH);
        
        // Center: Split pane with type selection and summary
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        SimpleGenomeHubUi.styleSplitPane(splitPane);
        
        // Left: Type selection buttons
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(new TitledBorder("Available Annotation Types"));
        
        createTypeSelectionButtons();
        
        JScrollPane typeScrollPane = new JScrollPane(typeButtonPanel);
        typeScrollPane.setPreferredSize(new Dimension(250, 250));
        leftPanel.add(typeScrollPane, BorderLayout.CENTER);
        
        splitPane.setLeftComponent(leftPanel);
        
        // Right: Summary information
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(new TitledBorder("Summary Information"));
        
        JScrollPane summaryScrollPane = new JScrollPane(summaryArea);
        rightPanel.add(summaryScrollPane, BorderLayout.CENTER);
        
        splitPane.setRightComponent(rightPanel);
        splitPane.setDividerLocation(250);
        
        add(splitPane, BorderLayout.CENTER);
        
        // Bottom: Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(viewAllButton);
        buttonPanel.add(cancelButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create type selection buttons based on available data
     */
    private void createTypeSelectionButtons() {
        typeButtonPanel.removeAll();
        
        // Use the new metadata-driven detection logic
        java.util.Set<AnnotationType> availableTypes = targetSpecies.getAvailableAnnotationTypes();
        
        if (availableTypes.isEmpty()) {
            JLabel noAnnotationsLabel = new JLabel("<html><i>No functional annotations imported yet.<br>" +
                "Use 'Import Annotations' to add data.</i></html>");
            noAnnotationsLabel.setHorizontalAlignment(SwingConstants.CENTER);
            noAnnotationsLabel.setForeground(Color.GRAY);
            typeButtonPanel.add(noAnnotationsLabel);
        } else {
            for (AnnotationType type : availableTypes) {
                // Get annotation count from metadata
                int count = getAnnotationCountForType(type);
                
                // Create a detailed button for this type
                JButton typeButton = createTypeButton(type, count);
                typeButtonPanel.add(typeButton);
                typeButtonPanel.add(Box.createVerticalStrut(5));
            }
        }
        
        typeButtonPanel.revalidate();
        typeButtonPanel.repaint();
    }
    
    /**
     * Get annotation count for a specific type from metadata or annotation data
     */
    private int getAnnotationCountForType(AnnotationType type) {
        // First try to get from loaded annotation data
        if (annotationData != null) {
            Map<AnnotationType, Integer> counts = annotationData.getAnnotationCounts();
            Integer count = counts.get(type);
            if (count != null && count > 0) {
                return count;
            }
        }
        
        // Fallback: try to get from metadata
        AnnotationMetadata metadata = targetSpecies.getAnnotationMetadata(type);
        if (metadata != null) {
            return metadata.getTotalAnnotations();
        }
        
        // If metadata is not loaded, try to load it
        if (targetSpecies.loadAnnotationMetadata(type)) {
            metadata = targetSpecies.getAnnotationMetadata(type);
            if (metadata != null) {
                return metadata.getTotalAnnotations();
            }
        }
        
        // Last resort: return 1 to indicate data exists but count unknown
        return 1;
    }
    
    /**
     * Create a button for a specific annotation type
     */
    private JButton createTypeButton(AnnotationType type, int count) {
        // Create button with detailed information
        String buttonText = String.format(
            "<html><b>%s</b> (%d annotations)<br><i>%s</i></html>",
            type.getShortName(),
            count,
            type.getDescription()
        );
        
        JButton button = new JButton(buttonText);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setPreferredSize(new Dimension(220, 70));
        button.setMaximumSize(new Dimension(220, 70));
        
        // Add icon based on type
        button.setIcon(getTypeIcon(type));
        
        // Add action listener
        button.addActionListener(e -> {
            selectedType = type;
            selectionMade = true;
            openAnnotationManagement(type);
        });
        
        return button;
    }
    
    /**
     * Get icon for annotation type
     */
    private Icon getTypeIcon(AnnotationType type) {
        // Create simple colored icons for different types
        Color iconColor;
        switch (type) {
            case GO:
                iconColor = new Color(52, 152, 219); // Blue
                break;
            case KEGG:
                iconColor = new Color(46, 204, 113); // Green
                break;
            case INTERPRO:
                iconColor = new Color(155, 89, 182); // Purple
                break;
            case PFAM:
                iconColor = new Color(230, 126, 34); // Orange
                break;
            case CUSTOM:
                iconColor = new Color(149, 165, 166); // Gray
                break;
            default:
                iconColor = Color.LIGHT_GRAY;
        }
        
        return new ColorIcon(iconColor, 16, 16);
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        viewAllButton.addActionListener(e -> {
            selectedType = null; // No specific type selected
            selectionMade = true;
            openAnnotationManagement(null);
        });
        
        cancelButton.addActionListener(e -> {
            selectionMade = false;
            dispose();
        });
    }
    
    /**
     * Open annotation management for selected type
     */
    private void openAnnotationManagement(AnnotationType type) {
        dispose();
        
        // Create and show annotation data panel with type filter
        AnnotationDataPanel dialog = new AnnotationDataPanel(getOwner(), targetSpecies);
        
        // If a specific type was selected, apply the filter
        if (type != null) {
            dialog.setInitialTypeFilter(type);
        }
        
        dialog.setVisible(true);
    }
    
    /**
     * Update summary information
     */
    private void updateSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("FUNCTIONAL ANNOTATION SUMMARY\n");
        summary.append("================================\n\n");
        summary.append("Species: ").append(targetSpecies.getSpeciesName()).append("\n");
        
        // Use metadata-driven approach to get annotation statistics
        java.util.Set<AnnotationType> availableTypes = targetSpecies.getAvailableAnnotationTypes();
        
        if (availableTypes.isEmpty()) {
            summary.append("Total annotated genes: 0\n\n");
            summary.append("Annotation Types:\n");
            summary.append("-----------------\n");
            summary.append("No annotations imported yet.\n");
            summary.append("\nUse 'Import Annotations' to add\n");
            summary.append("GO, KEGG, or other functional\n");
            summary.append("annotation data.\n");
        } else {
            // Calculate total genes and annotations from metadata
            int totalAnnotatedGenes = 0;
            int totalAnnotations = 0;
            
            summary.append("Annotation Types:\n");
            summary.append("-----------------\n");
            
            for (AnnotationType type : availableTypes) {
                int count = getAnnotationCountForType(type);
                summary.append(String.format("%-12s: %,6d annotations\n", 
                    type.getShortName(), count));
                totalAnnotations += count;
                
                // Try to get unique genes from metadata
                AnnotationMetadata metadata = targetSpecies.getAnnotationMetadata(type);
                if (metadata != null) {
                    totalAnnotatedGenes = Math.max(totalAnnotatedGenes, metadata.getUniqueGenes());
                }
            }
            
            // Add total annotated genes to summary
            int insertPos = summary.indexOf("Annotation Types:");
            summary.insert(insertPos, String.format("Total annotated genes: %d\n\n", totalAnnotatedGenes));
            
            summary.append(String.format("\nTotal: %,d annotations\n", totalAnnotations));
            
            // Try to get import time from annotation data or metadata
            if (annotationData != null && annotationData.getImportTime() != null) {
                summary.append("\nLast import: ");
                summary.append(annotationData.getImportTime().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            } else {
                // Try to get from metadata of first available type
                AnnotationType firstType = availableTypes.iterator().next();
                AnnotationMetadata metadata = targetSpecies.getAnnotationMetadata(firstType);
                if (metadata != null && metadata.getImportDate() != null) {
                    summary.append("\nLast import: ");
                    summary.append(metadata.getImportDate().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                }
            }
        }
        
        summaryArea.setText(summary.toString());
    }
    
    /**
     * Check if user made a selection
     */
    public boolean isSelectionMade() {
        return selectionMade;
    }
    
    /**
     * Get selected annotation type
     */
    public AnnotationType getSelectedType() {
        return selectedType;
    }
    
    /**
     * Simple colored icon class
     */
    private static class ColorIcon implements Icon {
        private Color color;
        private int width;
        private int height;
        
        public ColorIcon(Color color, int width, int height) {
            this.color = color;
            this.width = width;
            this.height = height;
        }
        
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRoundRect(x, y, width, height, 4, 4);
            g.setColor(color.darker());
            g.drawRoundRect(x, y, width, height, 4, 4);
        }
        
        @Override
        public int getIconWidth() {
            return width;
        }
        
        @Override
        public int getIconHeight() {
            return height;
        }
    }
}
