/*
 * Help Dialog
 */
package simplegenomehub.gui;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Comprehensive help dialog with tabbed help content
 * Provides user documentation and usage instructions
 * 
 * @author SimpleGenomeHub
 */
public class HelpDialog extends JDialog {
    
    private JTabbedPane tabbedPane;
    
    /**
     * Constructor
     */
    public HelpDialog(Window parent) {
        super(parent, "SimpleGenomeHub Help", ModalityType.APPLICATION_MODAL);
        
        initializeComponents();
        setupLayout();
        loadHelpContent();
        
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(parent);
    }
    
    /**
     * Initialize components
     */
    private void initializeComponents() {
        tabbedPane = new JTabbedPane();
    }
    
    /**
     * Setup layout
     */
    private void setupLayout() {
        setLayout(new BorderLayout());
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // Control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    /**
     * Load help content into tabs
     */
    private void loadHelpContent() {
        
        // Overview tab
        tabbedPane.addTab("Overview", createOverviewPanel());
        
        // Getting Started tab
        tabbedPane.addTab("Getting Started", createGettingStartedPanel());
        
        // Species Management tab  
        tabbedPane.addTab("Species Management", createSpeciesManagementPanel());
        
        // Search & Validation tab
        tabbedPane.addTab("Search & Validation", createSearchValidationPanel());
        
        // File Formats tab
        tabbedPane.addTab("File Formats", createFileFormatsPanel());
        
        // Performance tab
        tabbedPane.addTab("Performance", createPerformancePanel());
        
        // FAQ tab
        tabbedPane.addTab("FAQ", createFAQPanel());
    }
    
    /**
     * Create overview panel
     */
    private JPanel createOverviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea textArea = createHelpTextArea();
        textArea.setText(getOverviewContent());
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create getting started panel
     */
    private JPanel createGettingStartedPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea textArea = createHelpTextArea();
        textArea.setText(getGettingStartedContent());
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create species management panel
     */
    private JPanel createSpeciesManagementPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea textArea = createHelpTextArea();
        textArea.setText(getSpeciesManagementContent());
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create search and validation panel
     */
    private JPanel createSearchValidationPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea textArea = createHelpTextArea();
        textArea.setText(getSearchValidationContent());
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create file formats panel
     */
    private JPanel createFileFormatsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea textArea = createHelpTextArea();
        textArea.setText(getFileFormatsContent());
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create performance panel
     */
    private JPanel createPerformancePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea textArea = createHelpTextArea();
        textArea.setText(getPerformanceContent());
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create FAQ panel
     */
    private JPanel createFAQPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JTextArea textArea = createHelpTextArea();
        textArea.setText(getFAQContent());
        
        JScrollPane scrollPane = new JScrollPane(textArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Create formatted help text area
     */
    private JTextArea createHelpTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        textArea.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
        textArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return textArea;
    }
    
    /**
     * Get overview content
     */
    private String getOverviewContent() {
        return "SimpleGenomeHub - Genome Management System\\n" +
               "===========================================\\n\\n" +
               "SimpleGenomeHub is a comprehensive genome data management system designed for " +
               "organizing, validating, and analyzing genomic data collections.\\n\\n" +
               "Key Features:\\n" +
               "• Species-based organization with version control\\n" +
               "• FASTA sequence management and indexing\\n" +
               "• GFF3/GTF annotation file support\\n" +
               "• Data validation and automated repair\\n" +
               "• Advanced search and filtering capabilities\\n" +
               "• Sequence extraction and export tools\\n" +
               "• Performance monitoring and optimization\\n" +
               "• Batch import/export operations\\n\\n" +
               "Directory Structure:\\n" +
               "SimpleGenomeHub organizes data using a Species.Version directory structure:\\n" +
               "  DataRoot/\\n" +
               "    ├── Species1.v1.0/\\n" +
               "    │   ├── sequences/     (FASTA files)\\n" +
               "    │   ├── annotations/   (GFF3/GTF files)\\n" +
               "    │   ├── extracted/     (extracted sequences)\\n" +
               "    │   └── stats.json     (genome statistics)\\n" +
               "    └── Species2.v2.1/\\n" +
               "        ├── sequences/\\n" +
               "        └── annotations/\\n\\n" +
               "This help system provides detailed information about all features and workflows.";
    }
    
    /**
     * Get getting started content
     */
    private String getGettingStartedContent() {
        return "Getting Started with SimpleGenomeHub\\n" +
               "====================================\\n\\n" +
               "Step 1: Configure Data Directory\\n" +
               "---------------------------------\\n" +
               "1. Click the 'Configure' button in the toolbar\\n" +
               "2. Select or create a directory for storing genome data\\n" +
               "3. Click 'Apply' to save the configuration\\n\\n" +
               "Step 2: Import Your First Species\\n" +
               "----------------------------------\\n" +
               "1. Click 'Import Species' button\\n" +
               "2. Enter species name and version (e.g., 'Arabidopsis', '1.0')\\n" +
               "3. Select genome FASTA file (required)\\n" +
               "4. Select annotation GFF3/GTF file (recommended)\\n" +
               "5. Click 'Import' to process the files\\n\\n" +
               "Step 3: Explore the Interface\\n" +
               "-----------------------------\\n" +
               "• Left Panel: Species tree showing all imported species\\n" +
               "• Right Panel: Detailed information about selected species\\n" +
               "• Toolbar: Access to all major functions\\n" +
               "• Status Bar: Current operation status and data directory\\n\\n" +
               "Step 4: Basic Operations\\n" +
               "------------------------\\n" +
               "• Click on species in tree to view details\\n" +
               "• Use 'Search' to find specific species\\n" +
               "• Use 'Validate' to check data integrity\\n" +
               "• Use 'Export' to extract sequence data\\n\\n" +
               "Step 5: Advanced Features\\n" +
               "-------------------------\\n" +
               "• Batch Import: Import multiple species at once\\n" +
               "• Batch Export: Export multiple species data\\n" +
               "• Performance: Monitor system performance and cache\\n" +
               "• Validation: Comprehensive data validation and repair\\n\\n" +
               "Tips for New Users:\\n" +
               "• Start with small datasets to familiarize yourself\\n" +
               "• Always validate data after import\\n" +
               "• Use descriptive species names and versions\\n" +
               "• Check the Performance monitor if operations seem slow";
    }
    
    /**
     * Get species management content  
     */
    private String getSpeciesManagementContent() {
        return "Species Management\\n" +
               "==================\\n\\n" +
               "Adding Species\\n" +
               "--------------\\n" +
               "Single Import:\\n" +
               "• Use 'Import Species' for individual species\\n" +
               "• Provide species name, version, genome FASTA, and annotation files\\n" +
               "• System automatically creates directory structure\\n" +
               "• Genome statistics are calculated during import\\n\\n" +
               "Batch Import:\\n" +
               "• Use 'Batch Import' for multiple species\\n" +
               "• Select a directory containing species subdirectories\\n" +
               "• Each subdirectory should follow Species.Version format\\n" +
               "• Contains sequences/ and annotations/ folders\\n\\n" +
               "Managing Species\\n" +
               "----------------\\n" +
               "Editing Species:\\n" +
               "• Right-click species in tree → 'Edit Species'\\n" +
               "• Modify species name, version, or description\\n" +
               "• Changes are applied immediately\\n\\n" +
               "Deleting Species:\\n" +
               "• Right-click species → 'Delete Species'\\n" +
               "• Or select species and click 'Delete' button\\n" +
               "• Confirmation dialog prevents accidental deletion\\n" +
               "• All files and directories are permanently removed\\n\\n" +
               "Species Information\\n" +
               "-------------------\\n" +
               "The species information panel shows:\\n" +
               "• Basic metadata (name, version, import date)\\n" +
               "• File locations and sizes\\n" +
               "• Genome statistics (if calculated)\\n" +
               "• Completeness status\\n\\n" +
               "Directory Structure Management\\n" +
               "------------------------------\\n" +
               "• Species directories follow Species.Version naming\\n" +
               "• sequences/ folder contains FASTA files\\n" +
               "• annotations/ folder contains GFF3/GTF files\\n" +
               "• extracted/ folder contains extracted sequences\\n" +
               "• stats.json contains calculated genome statistics\\n\\n" +
               "Best Practices:\\n" +
               "• Use consistent naming conventions\\n" +
               "• Include version numbers for different assemblies\\n" +
               "• Validate data after any manual changes\\n" +
               "• Keep original files as backups";
    }
    
    /**
     * Get search and validation content
     */
    private String getSearchValidationContent() {
        return "Search & Validation\\n" +
               "===================\\n\\n" +
               "Advanced Search\\n" +
               "---------------\\n" +
               "The search dialog provides multiple search criteria:\\n\\n" +
               "Text Search:\\n" +
               "• Species name pattern matching\\n" +
               "• Version pattern matching\\n" +
               "• Case sensitive/insensitive options\\n" +
               "• Regular expression support\\n\\n" +
               "Numerical Filters:\\n" +
               "• Genome size range (base pairs)\\n" +
               "• Gene count range\\n" +
               "• File size filters\\n\\n" +
               "Date Filters:\\n" +
               "• Import date range\\n" +
               "• File modification date\\n\\n" +
               "File Existence Filters:\\n" +
               "• Has genome file\\n" +
               "• Has annotation file\\n" +
               "• Has extracted sequences\\n" +
               "• Has statistics\\n\\n" +
               "Data Validation\\n" +
               "---------------\\n" +
               "The validation system checks:\\n\\n" +
               "Directory Structure:\\n" +
               "• Species directory exists\\n" +
               "• Required subdirectories present\\n" +
               "• Proper naming conventions\\n\\n" +
               "File Integrity:\\n" +
               "• FASTA file format validation\\n" +
               "• GFF3/GTF file format validation\\n" +
               "• File accessibility and permissions\\n\\n" +
               "Data Completeness:\\n" +
               "• Required files present\\n" +
               "• Statistics file validity\\n" +
               "• Metadata completeness\\n\\n" +
               "Compatibility Checks:\\n" +
               "• Genome and annotation file compatibility\\n" +
               "• Sequence ID matching\\n" +
               "• Coordinate system consistency\\n\\n" +
               "Automated Repair\\n" +
               "----------------\\n" +
               "The system can automatically fix:\\n" +
               "• Missing directory structure\\n" +
               "• Missing FASTA index files\\n" +
               "• Corrupted statistics files\\n" +
               "• Missing metadata values\\n" +
               "• Temporary file cleanup\\n\\n" +
               "Validation Reports:\\n" +
               "• Detailed issue descriptions\\n" +
               "• Severity levels (Error, Warning, Info)\\n" +
               "• Repair recommendations\\n" +
               "• Export capability for documentation";
    }
    
    /**
     * Get file formats content
     */
    private String getFileFormatsContent() {
        return "Supported File Formats\\n" +
               "======================\\n\\n" +
               "FASTA Files (.fa, .fasta, .fas)\\n" +
               "-------------------------------\\n" +
               "Used for genome sequences:\\n" +
               "• Standard FASTA header format: >sequence_id description\\n" +
               "• DNA sequences (A, T, G, C, N)\\n" +
               "• Multiple sequences per file supported\\n" +
               "• Automatic indexing (.fai files) for fast access\\n\\n" +
               "Example FASTA format:\\n" +
               ">Chr1 Chromosome 1\\n" +
               "ATGCATGCATGCATGC...\\n" +
               ">Chr2 Chromosome 2\\n" +
               "GCATGCATGCATGCAT...\\n\\n" +
               "GFF3 Files (.gff, .gff3)\\n" +
               "-------------------------\\n" +
               "Used for genomic annotations:\\n" +
               "• Tab-delimited format with 9 columns\\n" +
               "• Features: gene, mRNA, CDS, exon, etc.\\n" +
               "• Hierarchical relationships via Parent attribute\\n" +
               "• Supports custom attributes\\n\\n" +
               "GFF3 columns:\\n" +
               "1. seqid     - Sequence ID\\n" +
               "2. source    - Annotation source\\n" +
               "3. type      - Feature type\\n" +
               "4. start     - Start position (1-based)\\n" +
               "5. end       - End position\\n" +
               "6. score     - Confidence score\\n" +
               "7. strand    - Strand (+, -, .)\\n" +
               "8. phase     - CDS frame (0, 1, 2)\\n" +
               "9. attributes - Key=Value pairs\\n\\n" +
               "GTF Files (.gtf)\\n" +
               "----------------\\n" +
               "Gene Transfer Format (similar to GFF2):\\n" +
               "• Tab-delimited with 9 columns\\n" +
               "• Primarily used for gene structures\\n" +
               "• Required attributes: gene_id, transcript_id\\n" +
               "• Compatible with many analysis tools\\n\\n" +
               "JSON Statistics Files\\n" +
               "---------------------\\n" +
               "Automatically generated genome statistics:\\n" +
               "• Total sequence length\\n" +
               "• Number of sequences\\n" +
               "• GC content\\n" +
               "• Gene counts by type\\n" +
               "• N50 statistics\\n\\n" +
               "Index Files\\n" +
               "-----------\\n" +
               "FASTA Index (.fai):\\n" +
               "• Created automatically by samtools faidx\\n" +
               "• Enables fast sequence extraction\\n" +
               "• Tab-delimited: name, length, offset, linebases, linewidth\\n\\n" +
               "File Validation\\n" +
               "---------------\\n" +
               "The system validates:\\n" +
               "• File format compliance\\n" +
               "• Sequence ID consistency\\n" +
               "• Coordinate validity\\n" +
               "• Character set compliance\\n" +
               "• File integrity";
    }
    
    /**
     * Get performance content
     */
    private String getPerformanceContent() {
        return "Performance Optimization\\n" +
               "========================\\n\\n" +
               "System Architecture\\n" +
               "-------------------\\n" +
               "SimpleGenomeHub uses several optimization strategies:\\n\\n" +
               "Lazy Loading:\\n" +
               "• Species metadata loaded immediately\\n" +
               "• Genome statistics loaded on demand\\n" +
               "• Large files processed in background\\n\\n" +
               "Caching:\\n" +
               "• Genome statistics cached in memory\\n" +
               "• File validation results cached\\n" +
               "• Search results cached temporarily\\n\\n" +
               "Parallel Processing:\\n" +
               "• Multi-threaded species loading\\n" +
               "• Background genome analysis\\n" +
               "• Concurrent file operations\\n\\n" +
               "Performance Monitoring\\n" +
               "----------------------\\n" +
               "The Performance dialog shows:\\n\\n" +
               "Memory Usage:\\n" +
               "• Total, used, and free memory\\n" +
               "• Maximum available memory\\n" +
               "• Garbage collection statistics\\n\\n" +
               "Operation Timing:\\n" +
               "• Average execution times\\n" +
               "• Minimum and maximum times\\n" +
               "• Operation counts\\n\\n" +
               "Cache Statistics:\\n" +
               "• Number of cached items\\n" +
               "• Cache hit rates\\n" +
               "• Memory usage by cache\\n\\n" +
               "Optimization Tips\\n" +
               "-----------------\\n" +
               "For Large Datasets (>100 species):\\n" +
               "• Use batch import for faster loading\\n" +
               "• Clear cache periodically to free memory\\n" +
               "• Close unused dialogs\\n" +
               "• Monitor memory usage\\n\\n" +
               "For Slow Operations:\\n" +
               "• Check disk space and permissions\\n" +
               "• Ensure FASTA files have index files\\n" +
               "• Use SSD storage for better I/O performance\\n" +
               "• Close other applications to free resources\\n\\n" +
               "Memory Management:\\n" +
               "• Use 'Clear Cache' button in Performance dialog\\n" +
               "• Restart application if memory usage is high\\n" +
               "• Increase JVM heap size for large datasets\\n\\n" +
               "File System Optimization:\\n" +
               "• Keep data directory on fast storage\\n" +
               "• Avoid network drives for data storage\\n" +
               "• Regular cleanup of temporary files\\n" +
               "• Defragment disk periodically\\n\\n" +
               "Troubleshooting\\n" +
               "---------------\\n" +
               "Slow Loading:\\n" +
               "• Check file permissions\\n" +
               "• Verify disk space\\n" +
               "• Look for corrupted files\\n" +
               "• Check antivirus software interference\\n\\n" +
               "Memory Issues:\\n" +
               "• Clear performance cache\\n" +
               "• Restart application\\n" +
               "• Increase JVM memory allocation\\n" +
               "• Close other memory-intensive applications";
    }
    
    /**
     * Get FAQ content
     */
    private String getFAQContent() {
        return "Frequently Asked Questions\\n" +
               "==========================\\n\\n" +
               "Q: How do I import my first genome?\\n" +
               "A: 1) Click 'Configure' and set a data directory\\n" +
               "   2) Click 'Import Species'\\n" +
               "   3) Enter species name and version\\n" +
               "   4) Select your FASTA and annotation files\\n" +
               "   5) Click 'Import'\\n\\n" +
               "Q: What file formats are supported?\\n" +
               "A: FASTA files (.fa, .fasta, .fas) for sequences\\n" +
               "   GFF3 files (.gff, .gff3) for annotations\\n" +
               "   GTF files (.gtf) for gene annotations\\n\\n" +
               "Q: Can I import multiple species at once?\\n" +
               "A: Yes, use 'Batch Import' to import multiple species.\\n" +
               "   Organize your data in Species.Version directories\\n" +
               "   with sequences/ and annotations/ subdirectories.\\n\\n" +
               "Q: How do I search for specific species?\\n" +
               "A: Click the 'Search' button for advanced search options.\\n" +
               "   You can search by name, version, file properties,\\n" +
               "   genome size, and import date.\\n\\n" +
               "Q: What does validation do?\\n" +
               "A: Validation checks file integrity, format compliance,\\n" +
               "   directory structure, and data completeness.\\n" +
               "   It can also automatically repair some issues.\\n\\n" +
               "Q: How do I extract sequences from a genome?\\n" +
               "A: Right-click on a species and select 'Export Data'\\n" +
               "   or use the 'Batch Export' feature.\\n" +
               "   Choose sequence extraction and specify your criteria.\\n\\n" +
               "Q: Why is the application running slowly?\\n" +
               "A: Check the Performance monitor for memory usage.\\n" +
               "   Clear cache if memory is high.\\n" +
               "   Ensure FASTA files have index files (.fai).\\n" +
               "   Use fast storage (SSD) for data directory.\\n\\n" +
               "Q: Can I change the data directory after setup?\\n" +
               "A: Yes, click 'Configure' and select a new directory.\\n" +
               "   The system will reload species from the new location.\\n\\n" +
               "Q: How do I backup my data?\\n" +
               "A: Simply copy the entire data directory.\\n" +
               "   All species data is stored in the configured\\n" +
               "   data directory with a clear structure.\\n\\n" +
               "Q: What if I get validation errors?\\n" +
               "A: Run validation to see specific issues.\\n" +
               "   Many problems can be auto-repaired.\\n" +
               "   Check file permissions and formats.\\n" +
               "   Regenerate statistics if needed.\\n\\n" +
               "Q: How do I update an existing species?\\n" +
               "A: Import the new version with a different version number\\n" +
               "   (e.g., v2.0 instead of v1.0).\\n" +
               "   Or replace files manually and re-validate.\\n\\n" +
               "Q: Can I work with large genomes (>1GB)?\\n" +
               "A: Yes, the system uses lazy loading and indexing\\n" +
               "   for efficient handling of large files.\\n" +
               "   Ensure adequate memory and disk space.\\n\\n" +
               "Q: How do I report bugs or request features?\\n" +
               "A: Contact the development team through the\\n" +
               "   project's issue tracking system or\\n" +
               "   email the maintainers directly.";
    }
}
