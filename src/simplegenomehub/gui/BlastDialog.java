/*
 * SimpleGenomeHub BLAST Dialog
 * Dialog wrapper for BLAST analysis functionality
 */
package simplegenomehub.gui;

import simplegenomehub.blast.BlastConfig.SequenceType;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;
import java.util.logging.Logger;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Dialog for BLAST analysis with integrated GUI
 * 
 * @author SimpleGenomeHub Team
 */
public class BlastDialog extends JDialog {
    
    private static final Logger logger = Logger.getLogger(BlastDialog.class.getName());
    
    private BlastPanel blastPanel;
    private SpeciesManager speciesManager;
    private SpeciesInfo targetSpecies;
    private SequenceType initialSequenceType;
    private String initialQuerySequence;
    
    /**
     * Create BLAST dialog for specific species
     */
    public BlastDialog(Window parent, SpeciesInfo targetSpecies, SpeciesManager mainSpeciesManager) {
        this(parent, targetSpecies, mainSpeciesManager, null, null);
    }

    /**
     * Create BLAST dialog for specific species with preloaded query state
     */
    public BlastDialog(Window parent, SpeciesInfo targetSpecies, SpeciesManager mainSpeciesManager,
                       SequenceType initialSequenceType, String initialQuerySequence) {
        super(parent, "BLAST Analysis - " + targetSpecies.getSpeciesName(), 
              ModalityType.MODELESS);
        
        this.targetSpecies = targetSpecies;
        this.speciesManager = mainSpeciesManager; // Use the main species manager directly
        this.initialSequenceType = initialSequenceType;
        this.initialQuerySequence = initialQuerySequence;
        
        initializeDialog();
        setupBlastPanel();
        setupLayout();
        setupEventHandlers();
        
        // Set dialog properties
        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setSize(1500, 800);
        setLocationRelativeTo(parent);
    }
    
    
    /**
     * Initialize dialog settings
     */
    private void initializeDialog() {
        setIconImage(null); // Set appropriate icon if available
        
        // Make dialog resizable
        setResizable(true);
        
        // Set minimum size
        setMinimumSize(new Dimension(800, 600));
    }
    
    /**
     * Setup BLAST panel
     */
    private void setupBlastPanel() {
        try {
            blastPanel = new BlastPanel(speciesManager);
            // Auto-select the target species
            if (blastPanel != null && targetSpecies != null) {
                blastPanel.selectTargetSpecies(targetSpecies);
                blastPanel.prefillQuery(targetSpecies, initialSequenceType, initialQuerySequence);
            }
        } catch (Exception e) {
            // If BLAST panel creation fails, show error and create placeholder
            showErrorAndCreatePlaceholder(e);
        }
    }
    
    /**
     * Show error and create placeholder panel
     */
    private void showErrorAndCreatePlaceholder(Exception e) {
        JOptionPane.showMessageDialog(this, 
            "BLAST initialization failed: " + e.getMessage(), 
            "BLAST Initialization Error", 
            JOptionPane.ERROR_MESSAGE);
        
        // Create placeholder panel
        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.setBorder(BorderFactory.createTitledBorder("BLAST Functionality Unavailable"));
        
        JTextArea errorArea = new JTextArea();
        errorArea.setEditable(false);
        errorArea.setText("BLAST initialization failed:\n" + e.getMessage() + 
                         "\n\nPlease check:\n" +
                         "1. BLAST+ files are available\n" +
                         "2. SimpleGenomeHub\\bin\\tools contains BLAST executables, or PATH includes them\n" +
                         "3. Sufficient disk space for database creation");
        
        placeholder.add(new JScrollPane(errorArea), BorderLayout.CENTER);
        blastPanel = null;
        
        // Add the placeholder to content pane directly
        getContentPane().add(placeholder, BorderLayout.CENTER);
    }
    
    /**
     * Setup dialog layout
     */
    private void setupLayout() {
        if (blastPanel != null) {
            setLayout(new BorderLayout());
            add(blastPanel, BorderLayout.CENTER);
            
            // Add toolbar with close button
            JPanel toolbarPanel = createToolbarPanel();
            add(toolbarPanel, BorderLayout.SOUTH);
        }
    }
    
    /**
     * Create toolbar panel
     */
    private JPanel createToolbarPanel() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        toolbar.setBorder(BorderFactory.createEtchedBorder());
        
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> closeDialog());
        closeButton.setPreferredSize(new Dimension(80, 30));
        
        JButton helpButton = new JButton("Help");
        helpButton.addActionListener(e -> showHelp());
        helpButton.setPreferredSize(new Dimension(80, 30));
        
        toolbar.add(helpButton);
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(closeButton);
        
        return toolbar;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        // Handle window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeDialog();
            }
        });
        
        // Handle escape key
        KeyStroke escapeKeyStroke = KeyStroke.getKeyStroke("ESCAPE");
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(escapeKeyStroke, "ESCAPE");
        getRootPane().getActionMap().put("ESCAPE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                closeDialog();
            }
        });
    }
    
    /**
     * Close dialog with cleanup
     */
    private void closeDialog() {
        // Cleanup BLAST panel resources
        if (blastPanel != null) {
            blastPanel.cleanup();
        }
        
        // Close dialog
        dispose();
    }
    
    /**
     * Show help information
     */
    private void showHelp() {
        String helpText = 
            "BLAST Analysis Help\n\n" +
            "Features:\n" +
            "- Supports multiple BLAST types (BLASTN, BLASTP, BLASTX, TBLASTN, TBLASTX)\n" +
            "- Automatic query sequence type detection\n" +
            "- On-demand target database creation\n" +
            "- Batch query support\n\n" +
            "Usage Steps:\n" +
            "1. Select target species and sequence type on the left\n" +
            "2. Input FASTA format sequence in query area\n" +
            "3. Adjust BLAST parameters (optional)\n" +
            "4. Click 'Execute BLAST' to start analysis\n" +
            "5. View results on the right panel\n\n" +
            "Notes:\n" +
            "- First use of a database will trigger automatic creation (may take time)\n" +
            "- Ensure query sequence is in valid FASTA format\n" +
            "- Large sequence queries may require extended processing time";
        
        JTextArea helpArea = new JTextArea(helpText);
        helpArea.setEditable(false);
        helpArea.setLineWrap(true);
        helpArea.setWrapStyleWord(true);
        helpArea.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
        
        JScrollPane scrollPane = new JScrollPane(helpArea);
        scrollPane.setPreferredSize(new Dimension(500, 400));
        
        JOptionPane.showMessageDialog(this, scrollPane, "BLAST Analysis Help", 
                                    JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Get the target species for this dialog
     */
    public SpeciesInfo getTargetSpecies() {
        return targetSpecies;
    }
    
    /**
     * Check if BLAST functionality is available
     */
    public boolean isBlastAvailable() {
        return blastPanel != null;
    }
}
