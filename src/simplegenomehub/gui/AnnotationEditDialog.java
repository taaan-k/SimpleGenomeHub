/*
 * Annotation Edit Dialog
 * Allows editing of functional annotation details
 */
package simplegenomehub.gui;

import simplegenomehub.model.GeneAnnotationData.GeneAnnotation;
import simplegenomehub.model.GeneAnnotationData.AnnotationType;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Dialog for editing annotation details
 * 
 * @author SimpleGenomeHub
 */
public class AnnotationEditDialog extends JDialog {
    
    private GeneAnnotation originalAnnotation;
    private GeneAnnotation updatedAnnotation;
    private boolean modified = false;
    
    // UI Components
    private JTextField geneIdField;
    private JTextField annotationIdField;
    private JTextField termField;
    private JTextArea descriptionArea;
    private JTextField evidenceField;
    private JSpinner scoreSpinner;
    private JComboBox<AnnotationType> typeCombo;
    
    private JButton saveButton;
    private JButton cancelButton;
    
    /**
     * Constructor
     */
    public AnnotationEditDialog(Window parent, GeneAnnotation annotation) {
        super(parent, "Edit Annotation", ModalityType.MODELESS);
        this.originalAnnotation = annotation;
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadAnnotationData();
        
        setSize(500, 400);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        // Input fields
        geneIdField = new JTextField(20);
        annotationIdField = new JTextField(20);
        termField = new JTextField(30);
        descriptionArea = new JTextArea(4, 30);
        evidenceField = new JTextField(20);
        scoreSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1.0, 0.01));
        
        typeCombo = new JComboBox<>(AnnotationType.values());
        SimpleGenomeHubUi.setComboBoxMinimumWidth(typeCombo, 220);
        
        // Configure text area
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setBorder(BorderFactory.createLoweredBevelBorder());
        
        // Buttons
        saveButton = new JButton("Save Changes");
        cancelButton = new JButton("Cancel");
        
        // Set tooltips
        geneIdField.setToolTipText("Gene identifier");
        annotationIdField.setToolTipText("Annotation identifier (GO ID, KEGG ID, etc.)");
        termField.setToolTipText("Short term or name");
        descriptionArea.setToolTipText("Detailed description of the annotation");
        evidenceField.setToolTipText("Evidence code or source");
        scoreSpinner.setToolTipText("Confidence score (0.0 - 1.0)");
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout(10, 10));
        
        // Top: Instructions
        JLabel instructionLabel = new JLabel(
            "<html><b>Edit Annotation</b><br>" + 
            "Modify the annotation details and click Save to apply changes.</html>");
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        add(instructionLabel, BorderLayout.NORTH);
        
        // Center: Form
        JPanel formPanel = new JPanel();
        formPanel.setBorder(new TitledBorder("Annotation Details"));
        formPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Gene ID
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(new JLabel("Gene ID:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(geneIdField, gbc);
        
        // Annotation Type
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Type:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(typeCombo, gbc);
        
        // Annotation ID
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Annotation ID:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(annotationIdField, gbc);
        
        // Term
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Term:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(termField, gbc);
        
        // Evidence
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Evidence:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(evidenceField, gbc);
        
        // Score
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Score:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        formPanel.add(scoreSpinner, gbc);
        
        // Description
        gbc.gridx = 0; gbc.gridy = 6; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.BOTH; gbc.weightx = 1.0; gbc.weighty = 1.0;
        formPanel.add(new JScrollPane(descriptionArea), gbc);
        
        add(formPanel, BorderLayout.CENTER);
        
        // Bottom: Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        saveButton.addActionListener(e -> saveChanges());
        cancelButton.addActionListener(e -> dispose());
        
        // ESC key to cancel
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke("ESCAPE");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }
    
    /**
     * Load annotation data into form fields
     */
    private void loadAnnotationData() {
        if (originalAnnotation == null) return;
        
        geneIdField.setText(originalAnnotation.getGeneId());
        typeCombo.setSelectedItem(originalAnnotation.getType());
        annotationIdField.setText(originalAnnotation.getAnnotationId());
        termField.setText(originalAnnotation.getTerm() != null ? originalAnnotation.getTerm() : "");
        descriptionArea.setText(originalAnnotation.getDescription() != null ? originalAnnotation.getDescription() : "");
        evidenceField.setText(originalAnnotation.getEvidence() != null ? originalAnnotation.getEvidence() : "");
        scoreSpinner.setValue(originalAnnotation.getScore());
    }
    
    /**
     * Save changes and create updated annotation
     */
    private void saveChanges() {
        try {
            // Validate input
            String geneId = geneIdField.getText().trim();
            String annotationId = annotationIdField.getText().trim();
            
            if (geneId.isEmpty() || annotationId.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "Gene ID and Annotation ID are required fields.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            // Create updated annotation
            AnnotationType type = (AnnotationType) typeCombo.getSelectedItem();
            String term = termField.getText().trim();
            
            updatedAnnotation = new GeneAnnotation(geneId, annotationId, term, type);
            updatedAnnotation.setDescription(descriptionArea.getText().trim());
            updatedAnnotation.setEvidence(evidenceField.getText().trim());
            updatedAnnotation.setScore((Double) scoreSpinner.getValue());
            
            // Copy attributes from original
            if (originalAnnotation.getAttributes() != null) {
                for (java.util.Map.Entry<String, String> entry : originalAnnotation.getAttributes().entrySet()) {
                    updatedAnnotation.setAttribute(entry.getKey(), entry.getValue());
                }
            }
            
            modified = true;
            dispose();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error saving annotation: " + e.getMessage(),
                "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Check if annotation was modified
     */
    public boolean isModified() {
        return modified;
    }
    
    /**
     * Get the updated annotation
     */
    public GeneAnnotation getUpdatedAnnotation() {
        return updatedAnnotation;
    }
}
