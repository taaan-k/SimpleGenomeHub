/*
 * SimpleGenomeHub Main GUI Panel
 */
package simplegenomehub.gui;

import simplegenomehub.config.SimpleGenomeHubConfig;
import simplegenomehub.model.*;
import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Main GUI panel for SimpleGenomeHub application
 * Based on TBtools GUI patterns with Swing components
 * 
 * @author SimpleGenomeHub
 */
public class SimpleGenomeHubMainPanel extends JPanel implements SpeciesManager.SpeciesManagerListener {
    
    // Core components
    private SimpleGenomeHubConfig config;
    private SpeciesManager speciesManager;
    
    // GUI components
    private SpeciesTreePanel speciesTreePanel;
    private SpeciesInfoPanel speciesInfoPanel;
    private JLabel statusLabel;
        
    /**
     * Constructor
     */
    public SimpleGenomeHubMainPanel() {
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        // Don't call refreshData() here since SpeciesManager constructor already loads data
        // refreshData();
        
        // Just update UI components to reflect the loaded data
        SwingUtilities.invokeLater(() -> {
            speciesTreePanel.clearFilter();
            speciesInfoPanel.setSpecies(null);
            setStatus("Ready - " + speciesManager.getSpeciesCount() + " species loaded");
        });
    }

    /**
     * Initialize core components
     */
    private void initializeComponents() {
        SimpleGenomeHubUi.installGlobalMenuStyling();
        SimpleGenomeHubUi.installGlobalDialogStyling();
        config = SimpleGenomeHubConfig.getInstance();
        speciesManager = new SpeciesManager();
        speciesManager.addListener(this);
        
        // Create GUI components
        speciesTreePanel = new SpeciesTreePanel(speciesManager);
        speciesInfoPanel = new SpeciesInfoPanel(speciesManager);
        statusLabel = new JLabel("Ready");
    }
    
    /**
     * Setup panel layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);
        
        add(createTitleBannerPanel(), BorderLayout.NORTH);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(450);
        mainSplitPane.setResizeWeight(0.33);
        SimpleGenomeHubUi.styleSplitPane(mainSplitPane);
        mainSplitPane.setLeftComponent(speciesTreePanel);
        mainSplitPane.setRightComponent(speciesInfoPanel);

        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);
        contentPanel.setBorder(SimpleGenomeHubUi.createInnerPadding(14, 14, 12, 14));
        contentPanel.add(mainSplitPane, BorderLayout.CENTER);

        add(contentPanel, BorderLayout.CENTER);

        JPanel statusPanel = createStatusPanel();
        add(statusPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Create the centered title banner based on the AAAA reference image
     */
    private JPanel createTitleBannerPanel() {
        final String titleText = "SimpleGenomeHub - Genome Management System";
        final Font titleFont = SimpleGenomeHubStyle.FONT_SANS_BOLD_24;
        final Color sideBarColor = new Color(218, 232, 247);

        JPanel titlePanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setPaint(new LinearGradientPaint(
                        0, 0, getWidth(), 0,
                        new float[]{0.0f, 0.16f, 0.5f, 0.84f, 1.0f},
                        new Color[]{
                            new Color(218, 232, 247),
                            new Color(235, 243, 252),
                            new Color(249, 254, 255),
                            new Color(235, 243, 252),
                            new Color(218, 232, 247)
                        }
                    ));
                    g2.fillRect(0, 0, getWidth(), getHeight());

                    FontMetrics fontMetrics = g2.getFontMetrics(titleFont);
                    int titleWidth = fontMetrics.stringWidth(titleText);
                    int titleLeft = (getWidth() - titleWidth) / 2;
                    int titleRight = titleLeft + titleWidth;
                    int sidePadding = 34;
                    int titleGap = 30;
                    int barHeight = 4;
                    int barY = getHeight() / 2 - barHeight / 2 + 3;

                    g2.setColor(sideBarColor);
                    int leftBarWidth = titleLeft - titleGap - sidePadding;
                    if (leftBarWidth > 24) {
                        g2.fillRect(sidePadding, barY, leftBarWidth, barHeight);
                    }

                    int rightBarX = titleRight + titleGap;
                    int rightBarWidth = getWidth() - sidePadding - rightBarX;
                    if (rightBarWidth > 24) {
                        g2.fillRect(rightBarX, barY, rightBarWidth, barHeight);
                    }

                    g2.setColor(new Color(156, 179, 204));
                    g2.drawLine(0, 0, getWidth(), 0);
                    g2.setColor(new Color(124, 151, 180));
                    g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                } finally {
                    g2.dispose();
                }
            }
        };
        titlePanel.setOpaque(false);
        titlePanel.setPreferredSize(new Dimension(10, 84));

        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setForeground(SimpleGenomeHubUi.TITLE_BLUE);
        titleLabel.setFont(titleFont);
        titlePanel.add(titleLabel);

        return titlePanel;
    }
    
    /**
     * Create status panel
     */
    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.setBackground(new Color(236, 243, 252));
        statusPanel.add(statusLabel, BorderLayout.WEST);
        
        // Add data directory info
        String dataDir = config.isDataRootConfigured() ? 
            config.getDataRootDir().getAbsolutePath() : "Not configured";
        JLabel dataDirLabel = new JLabel("Data Directory: " + dataDir);
        dataDirLabel.setFont(SimpleGenomeHubStyle.plain(dataDirLabel.getFont(), 11f));
        statusPanel.add(dataDirLabel, BorderLayout.EAST);
        
        return statusPanel;
    }
    
    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        speciesTreePanel.setRefreshAction(this::refreshData);
        speciesTreePanel.setConfigurationAction(this::showConfigurationDialog);
        speciesTreePanel.setImportGenomeAction(this::showImportDialog);

        // Listen to species tree selection
        speciesTreePanel.addSelectionListener(new SpeciesTreePanel.SelectionListener() {
            @Override
            public void onSpeciesSelected(SpeciesInfo species) {
                speciesInfoPanel.setSpecies(species);
            }
        });
    }
    
    /**
     * Show configuration dialog
     */
    private void showConfigurationDialog() {
        ConfigurationDialog dialog = new ConfigurationDialog(
            SwingUtilities.getWindowAncestor(this), config, speciesManager);
        dialog.setVisible(true);
    }
    
    /**
     * Show import dialog
     */
    private void showImportDialog() {
        if (!config.isDataRootConfigured()) {
            JOptionPane.showMessageDialog(this, 
                "Please configure the data directory first using the Config button.",
                "Configuration Required", JOptionPane.WARNING_MESSAGE);
            showConfigurationDialog();
            return;
        }
        
        SpeciesImportDialog dialog = new SpeciesImportDialog(
            SwingUtilities.getWindowAncestor(this), speciesManager);
        dialog.setVisible(true);
    }

    /**
     * Removed methods: showSearchDialog, showValidationDialog, showPerformanceDialog, showHelpDialog
     * These features were deemed unnecessary for the current application scope
     */
    
    /**
     * Refresh all data
     */
    private void refreshData() {
        SwingUtilities.invokeLater(() -> {
            setStatus("Refreshing species data...");
            
            // Refresh species manager
            speciesManager.loadSpeciesData();
            
            // Clear any search filters and update tree
            speciesTreePanel.clearFilter();
            
            // Clear info panel
            speciesInfoPanel.setSpecies(null);
            
            int count = speciesManager.getSpeciesCount();
            setStatus("Ready - " + count + " species loaded");
        });
    }
    
    /**
     * Set status message
     */
    private void setStatus(String message) {
        statusLabel.setText(message);
    }
    
    // SpeciesManagerListener implementation
    
    @Override
    public void onSpeciesAdded(SpeciesInfo species) {
        SwingUtilities.invokeLater(() -> {
            speciesTreePanel.refreshTree();
            setStatus("Species added: " + species.getSpeciesDirectoryName());
        });
    }
    
    @Override
    public void onSpeciesRemoved(SpeciesInfo species) {
        SwingUtilities.invokeLater(() -> {
            speciesTreePanel.refreshTree();
            speciesInfoPanel.setSpecies(null);
            setStatus("Species removed: " + species.getSpeciesDirectoryName());
        });
    }
    
    @Override
    public void onSpeciesUpdated(SpeciesInfo species) {
        SwingUtilities.invokeLater(() -> {
            speciesTreePanel.refreshTree();
            if (speciesInfoPanel.getCurrentSpecies() == species) {
                speciesInfoPanel.setSpecies(species);
            }
            setStatus("Species updated: " + species.getSpeciesDirectoryName());
        });
    }
    
    @Override
    public void onDataDirectoryChanged(File newDir) {
        SwingUtilities.invokeLater(() -> {
            refreshData();
            setStatus("Data directory changed to: " + newDir.getAbsolutePath());
            
            // Update status panel
            removeAll();
            setupLayout();
            revalidate();
            repaint();
        });
    }
    
    
    /**
     * Main method for running as standalone application
     */
    /**
     * Get the species manager for external access (e.g., from Gene Viewer)
     */
    public SpeciesManager getSpeciesManager() {
        return speciesManager;
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimpleGenomeHubStyle.installGlobalFontDefaults();
            // Use default Swing look and feel
            
            // Create main frame
            JFrame frame = new JFrame("SimpleGenomeHub");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1510, 860);
            frame.setLocationRelativeTo(null);
            
            // Add main panel
            SimpleGenomeHubMainPanel mainPanel = new SimpleGenomeHubMainPanel();
            frame.add(mainPanel);
            
            // Set window icon (if available)
            try {
                // You could add an icon here
                // frame.setIconImage(Toolkit.getDefaultToolkit().getImage("icon.png"));
            } catch (Exception e) {
                // Ignore icon loading errors
            }
            
            frame.setVisible(true);
        });
    }
}
