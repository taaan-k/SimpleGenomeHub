/*
 * Configuration Dialog
 */
package simplegenomehub.gui;

import simplegenomehub.config.SimpleGenomeHubConfig;
import simplegenomehub.model.SpeciesManager;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Configuration dialog for SimpleGenomeHub settings
 * Allows users to configure data directory and other settings
 * 
 * @author SimpleGenomeHub
 */
public class ConfigurationDialog extends JDialog {
    
    private SimpleGenomeHubConfig config;
    private SpeciesManager speciesManager;
    
    // Components
    private JTextField dataDirectoryField;
    private JButton browseButton;
    private JButton testButton;
    private JLabel statusLabel;
    private JButton okButton;
    private JButton cancelButton;
    private JButton applyButton;
    
    // Original values for cancel functionality
    private File originalDataDir;
    
    /**
     * Constructor
     */
    public ConfigurationDialog(Window parent, SimpleGenomeHubConfig config, SpeciesManager speciesManager) {
        super(parent, "SimpleGenomeHub Configuration", ModalityType.APPLICATION_MODAL);
        
        this.config = config;
        this.speciesManager = speciesManager;
        this.originalDataDir = config.getDataRootDir();
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadCurrentSettings();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(parent);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        dataDirectoryField = new JTextField(40);
        browseButton = new JButton("Browse...");
        testButton = new JButton("Test Directory");
        statusLabel = new JLabel("Ready");
        
        okButton = new JButton("OK");
        cancelButton = new JButton("Cancel");
        applyButton = new JButton("Apply");
        
        // Set preferred sizes
        browseButton.setPreferredSize(new Dimension(100, 25));
        testButton.setPreferredSize(new Dimension(120, 25));
        okButton.setPreferredSize(new Dimension(80, 25));
        cancelButton.setPreferredSize(new Dimension(80, 25));
        applyButton.setPreferredSize(new Dimension(80, 25));
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // Main content panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Data directory configuration panel
        JPanel dataDirPanel = createDataDirectoryPanel();
        mainPanel.add(dataDirPanel, BorderLayout.NORTH);
        
        // Information panel
        JPanel infoPanel = createInformationPanel();
        mainPanel.add(infoPanel, BorderLayout.CENTER);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = createButtonPanel();
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Status panel
        JPanel statusPanel = createStatusPanel();
        add(statusPanel, BorderLayout.NORTH);
    }
    
    /**
     * Create data directory configuration panel
     */
    private JPanel createDataDirectoryPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("Data Directory Configuration"));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        // Description
        JTextArea descArea = new JTextArea(
            "Select a directory to store genome data. The directory path must:\n" +
            "• Not contain spaces\n" +
            "• Use only English characters (A-Z, a-z, 0-9, -, _)\n" +
            "• Be writable by the application");
        descArea.setEditable(false);
        descArea.setOpaque(false);
        descArea.setFont(SimpleGenomeHubStyle.plain(descArea.getFont(), 11f));
        descArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 10, 5));
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        panel.add(descArea, gbc);
        
        // Data directory label and field
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Data Directory:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        gbc.insets = new Insets(5, 10, 5, 5);
        panel.add(dataDirectoryField, gbc);
        
        gbc.gridx = 2; gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.insets = new Insets(5, 0, 5, 0);
        panel.add(browseButton, gbc);
        
        // Test button
        gbc.gridx = 1; gbc.gridy = 2;
        gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 10, 10, 5);
        panel.add(testButton, gbc);
        
        return panel;
    }
    
    /**
     * Create information panel
     */
    private JPanel createInformationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Current Configuration"));
        
        JTextArea infoArea = new JTextArea();
        infoArea.setEditable(false);
        infoArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        infoArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        updateInfoArea(infoArea);
        
        JScrollPane scrollPane = new JScrollPane(infoArea);
        scrollPane.setPreferredSize(new Dimension(550, 150));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create status panel
     */
    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEtchedBorder());
        panel.add(new JLabel("Status:"));
        panel.add(statusLabel);
        return panel;
    }
    
    /**
     * Create button panel
     */
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(BorderFactory.createEtchedBorder());
        
        panel.add(applyButton);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(okButton);
        panel.add(cancelButton);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                browseForDirectory();
            }
        });
        
        testButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                testDirectory();
            }
        });
        
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (applySettings()) {
                    dispose();
                }
            }
        });
        
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelChanges();
            }
        });
        
        applyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applySettings();
            }
        });
    }
    
    /**
     * Load current settings into the dialog
     */
    private void loadCurrentSettings() {
        File currentDataDir = config.getDataRootDir();
        if (currentDataDir != null) {
            dataDirectoryField.setText(currentDataDir.getAbsolutePath());
        } else {
            dataDirectoryField.setText("");
        }
        
        updateStatus("Configuration loaded");
    }
    
    /**
     * Browse for data directory
     */
    private void browseForDirectory() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setDialogTitle("Select Data Directory");
        
        // Set initial directory
        String currentPath = dataDirectoryField.getText();
        if (!currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists()) {
                fileChooser.setCurrentDirectory(currentDir);
            }
        }
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedDir = fileChooser.getSelectedFile();
            dataDirectoryField.setText(selectedDir.getAbsolutePath());
            updateStatus("Directory selected: " + selectedDir.getName());
        }
    }
    
    /**
     * Test the selected directory
     */
    private void testDirectory() {
        String dirPath = dataDirectoryField.getText().trim();
        if (dirPath.isEmpty()) {
            updateStatus("Please specify a directory path");
            return;
        }
        
        File dir = new File(dirPath);
        
        // Test directory requirements
        StringBuilder results = new StringBuilder("Directory Test Results:\n\n");
        boolean allTestsPassed = true;
        
        // Test 1: Directory exists or can be created
        if (dir.exists()) {
            if (dir.isDirectory()) {
                results.append("✓ Directory exists\n");
            } else {
                results.append("✗ Path exists but is not a directory\n");
                allTestsPassed = false;
            }
        } else {
            try {
                if (dir.mkdirs()) {
                    results.append("✓ Directory created successfully\n");
                } else {
                    results.append("✗ Cannot create directory\n");
                    allTestsPassed = false;
                }
            } catch (SecurityException e) {
                results.append("✗ Permission denied creating directory\n");
                allTestsPassed = false;
            }
        }
        
        // Test 2: No spaces in path
        if (dirPath.contains(" ")) {
            results.append("✗ Path contains spaces (not allowed)\n");
            allTestsPassed = false;
        } else {
            results.append("✓ Path contains no spaces\n");
        }
        
        // Test 3: English characters only
        if (dirPath.matches("^[\\x00-\\x7F]*$")) {
            results.append("✓ Path uses only English characters\n");
        } else {
            results.append("✗ Path contains non-English characters\n");
            allTestsPassed = false;
        }
        
        // Test 4: Directory is writable
        if (dir.exists() && dir.canWrite()) {
            results.append("✓ Directory is writable\n");
        } else {
            results.append("✗ Directory is not writable\n");
            allTestsPassed = false;
        }
        
        // Test 5: Try creating a test file
        try {
            File testFile = new File(dir, "test_write_permissions.tmp");
            if (testFile.createNewFile()) {
                testFile.delete();
                results.append("✓ Write permissions confirmed\n");
            } else {
                results.append("✗ Cannot create test file\n");
                allTestsPassed = false;
            }
        } catch (Exception e) {
            results.append("✗ Write test failed: " + e.getMessage() + "\n");
            allTestsPassed = false;
        }
        
        results.append("\n");
        if (allTestsPassed) {
            results.append("✓ Directory passes all tests and can be used.");
            updateStatus("Directory test passed");
        } else {
            results.append("✗ Directory failed one or more tests.");
            updateStatus("Directory test failed");
        }
        
        // Show results
        JTextArea resultArea = new JTextArea(results.toString());
        resultArea.setEditable(false);
        resultArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        
        JScrollPane scrollPane = new JScrollPane(resultArea);
        scrollPane.setPreferredSize(new Dimension(400, 250));
        
        JOptionPane.showMessageDialog(this, scrollPane, "Directory Test Results",
            allTestsPassed ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }
    
    /**
     * Apply settings
     */
    private boolean applySettings() {
        String dirPath = dataDirectoryField.getText().trim();
        
        if (dirPath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please specify a data directory path.",
                "Configuration Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        
        File newDataDir = new File(dirPath);
        
        // Validate and set new directory
        if (speciesManager.setDataRootDirectory(newDataDir)) {
            updateStatus("Configuration applied successfully");
            
            // Update info area
            Component[] components = getContentPane().getComponents();
            for (Component comp : components) {
                if (comp instanceof JPanel) {
                    updateInfoPanels((Container) comp);
                }
            }
            
            return true;
        } else {
            updateStatus("Failed to set data directory");
            return false;
        }
    }
    
    /**
     * Cancel changes and revert to original settings
     */
    private void cancelChanges() {
        if (originalDataDir != null) {
            dataDirectoryField.setText(originalDataDir.getAbsolutePath());
        } else {
            dataDirectoryField.setText("");
        }
        
        updateStatus("Changes cancelled");
        dispose();
    }
    
    /**
     * Update status label
     */
    private void updateStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Update information area with current configuration
     */
    private void updateInfoArea(JTextArea infoArea) {
        StringBuilder info = new StringBuilder();
        
        info.append("Configuration Information:\n");
        info.append("========================\n\n");
        
        // Data directory info
        File dataDir = config.getDataRootDir();
        if (dataDir != null) {
            info.append("Data Directory: ").append(dataDir.getAbsolutePath()).append("\n");
            info.append("Directory Exists: ").append(dataDir.exists() ? "Yes" : "No").append("\n");
            info.append("Directory Writable: ").append(dataDir.canWrite() ? "Yes" : "No").append("\n");
        } else {
            info.append("Data Directory: Not configured\n");
        }
        
        info.append("\n");
        
        // Species count
        int speciesCount = speciesManager.getSpeciesCount();
        info.append("Current Species Count: ").append(speciesCount).append("\n");
        
        // Configuration file info
        File configFile = config.getConfigFile();
        info.append("Configuration File: ").append(configFile.getAbsolutePath()).append("\n");
        info.append("Config File Exists: ").append(configFile.exists() ? "Yes" : "No").append("\n");
        
        infoArea.setText(info.toString());
    }
    
    /**
     * Recursively update info panels
     */
    private void updateInfoPanels(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JTextArea && 
                comp.getParent().getParent() instanceof JScrollPane) {
                updateInfoArea((JTextArea) comp);
            } else if (comp instanceof Container) {
                updateInfoPanels((Container) comp);
            }
        }
    }
}
