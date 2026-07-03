/*
 * File Preview Dialog
 */
package simplegenomehub.gui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.NumberFormat;

/**
 * Dialog for previewing genome and annotation files
 * Shows file information, statistics, and content preview
 * 
 * @author SimpleGenomeHub
 */
public class FilePreviewDialog extends JDialog {
    
    private static final int MAX_PREVIEW_LINES = 1000;
    private static final int MAX_LINE_LENGTH = 1000; // Increased for genome sequences
    private static final long MAX_PREVIEW_BYTES = 1024 * 1024; // 1MB data limit
    
    private File file;
    private JTextArea contentArea;
    private JLabel fileInfoLabel;
    private JLabel fileSizeLabel;
    private JLabel lineCountLabel;
    private JProgressBar loadingProgress;
    private JButton refreshButton;
    private JButton copyPathButton;
    private JButton openFolderButton;
    
    /**
     * Constructor
     */
    public FilePreviewDialog(Window parent, File file) {
        super(parent, "File Preview - " + file.getName(), ModalityType.APPLICATION_MODAL);
        
        this.file = file;
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        loadFileContent();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(parent);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        contentArea = new JTextArea();
        contentArea.setEditable(false);
        contentArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
        contentArea.setBackground(Color.WHITE);
        
        fileInfoLabel = new JLabel();
        fileSizeLabel = new JLabel();
        lineCountLabel = new JLabel();
        
        loadingProgress = new JProgressBar();
        loadingProgress.setIndeterminate(true);
        loadingProgress.setString("Loading file...");
        loadingProgress.setStringPainted(true);
        loadingProgress.setVisible(false);
        
        refreshButton = new JButton("Refresh");
        copyPathButton = new JButton("Copy Path");
        openFolderButton = new JButton("Open Folder");
        
        // Set button sizes
        refreshButton.setPreferredSize(new Dimension(80, 25));
        copyPathButton.setPreferredSize(new Dimension(100, 25));
        openFolderButton.setPreferredSize(new Dimension(100, 25));
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // Top panel with file information
        JPanel infoPanel = createInfoPanel();
        add(infoPanel, BorderLayout.NORTH);
        
        // Center panel with content preview
        JPanel contentPanel = createContentPanel();
        add(contentPanel, BorderLayout.CENTER);
        
        // Bottom panel with buttons and progress
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create file information panel
     */
    private JPanel createInfoPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder("File Information"));
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        
        // File path
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 10);
        panel.add(new JLabel("File:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        fileInfoLabel.setText(file.getAbsolutePath());
        panel.add(fileInfoLabel, gbc);
        
        // File size
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Size:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(fileSizeLabel, gbc);
        
        // Line count
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Lines:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(lineCountLabel, gbc);
        
        return panel;
    }
    
    /**
     * Create content preview panel
     */
    private JPanel createContentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Content Preview"));
        
        JScrollPane scrollPane = new JScrollPane(contentArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Add warning label for large files
        JLabel warningLabel = new JLabel(
            "<html><i>Note: Preview limited to first " + MAX_PREVIEW_LINES + " lines or 1MB data. " +
            "Lines longer than " + MAX_LINE_LENGTH + " characters are truncated.</i></html>");
        warningLabel.setFont(SimpleGenomeHubStyle.italic(warningLabel.getFont(), 10f));
        warningLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panel.add(warningLabel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Create bottom panel with buttons and progress
     */
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Progress bar
        panel.add(loadingProgress, BorderLayout.NORTH);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(refreshButton);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(copyPathButton);
        buttonPanel.add(openFolderButton);
        buttonPanel.add(Box.createHorizontalStrut(5));
        
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        refreshButton.addActionListener(e -> loadFileContent());
        
        copyPathButton.addActionListener(e -> copyPathToClipboard());
        
        openFolderButton.addActionListener(e -> openContainingFolder());
    }
    
    /**
     * Load file content in background
     */
    private void loadFileContent() {
        loadingProgress.setVisible(true);
        contentArea.setText("Loading...");
        
        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            private long fileSize = 0;
            private int lineCount = 0;
            private long bytesRead = 0;
            private boolean hitDataLimit = false;
            
            @Override
            protected String doInBackground() throws Exception {
                StringBuilder content = new StringBuilder();
                fileSize = file.length();
                
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    lineCount = 0;
                    bytesRead = 0;
                    
                    while ((line = reader.readLine()) != null && 
                           lineCount < MAX_PREVIEW_LINES && 
                           bytesRead < MAX_PREVIEW_BYTES) {
                        lineCount++;
                        
                        // Calculate bytes (approximate for UTF-8)
                        long lineBytes = line.length() + 1; // +1 for newline
                        
                        // Check if adding this line would exceed data limit
                        if (bytesRead + lineBytes > MAX_PREVIEW_BYTES) {
                            hitDataLimit = true;
                            break;
                        }
                        
                        // Truncate very long lines for display
                        String displayLine = line;
                        if (line.length() > MAX_LINE_LENGTH) {
                            displayLine = line.substring(0, MAX_LINE_LENGTH) + "... [line truncated - " + line.length() + " chars total]";
                        }
                        
                        content.append(displayLine).append("\n");
                        bytesRead += lineBytes;
                        
                        // Check if we should stop
                        if (isCancelled()) {
                            break;
                        }
                    }
                    
                    // Count remaining lines if we hit any limit
                    if (lineCount >= MAX_PREVIEW_LINES || hitDataLimit) {
                        while (reader.readLine() != null) {
                            lineCount++;
                            if (lineCount % 1000 == 0 && isCancelled()) {
                                break; // Don't spend too long counting
                            }
                        }
                    }
                }
                
                return content.toString();
            }
            
            @Override
            protected void done() {
                try {
                    String content = get();
                    contentArea.setText(content);
                    contentArea.setCaretPosition(0);
                    
                    // Update file information
                    updateFileInfo(fileSize, lineCount, bytesRead, hitDataLimit);
                    
                } catch (Exception e) {
                    contentArea.setText("Error loading file: " + e.getMessage());
                    updateFileInfo(file.length(), 0, 0, false);
                } finally {
                    loadingProgress.setVisible(false);
                }
            }
        };
        
        worker.execute();
    }
    
    /**
     * Update file information labels
     */
    private void updateFileInfo(long size, int lines, long bytesRead, boolean hitDataLimit) {
        NumberFormat numberFormat = NumberFormat.getInstance();
        
        // Format file size
        String sizeText;
        if (size < 1024) {
            sizeText = size + " bytes";
        } else if (size < 1024 * 1024) {
            sizeText = String.format("%.1f KB", size / 1024.0);
        } else {
            sizeText = String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
        
        fileSizeLabel.setText(sizeText + " (" + numberFormat.format(size) + " bytes)");
        
        // Format line count with preview information
        String lineText = numberFormat.format(lines) + " lines";
        if (lines >= MAX_PREVIEW_LINES) {
            lineText += " (limited by line count: " + MAX_PREVIEW_LINES + ")";
        } else if (hitDataLimit) {
            lineText += " (limited by data size: 1MB)";
        }
        lineCountLabel.setText(lineText);
    }
    
    /**
     * Update file information labels (overload for backward compatibility)
     */
    private void updateFileInfo(long size, int lines) {
        updateFileInfo(size, lines, 0, false);
    }
    
    /**
     * Copy file path to clipboard
     */
    private void copyPathToClipboard() {
        try {
            java.awt.datatransfer.StringSelection selection = 
                new java.awt.datatransfer.StringSelection(file.getAbsolutePath());
            java.awt.datatransfer.Clipboard clipboard = 
                Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
            
            JOptionPane.showMessageDialog(this, 
                "File path copied to clipboard:\n" + file.getAbsolutePath(),
                "Path Copied", JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Failed to copy path to clipboard: " + e.getMessage(),
                "Clipboard Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Open containing folder in system file manager
     */
    private void openContainingFolder() {
        try {
            Desktop.getDesktop().open(file.getParentFile());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                "Failed to open folder: " + e.getMessage(),
                "Open Folder Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}
