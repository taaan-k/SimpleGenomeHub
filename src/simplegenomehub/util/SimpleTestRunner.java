/*
 * Simple Test Runner for BasicTesting
 */
package simplegenomehub.util;

import simplegenomehub.config.SimpleGenomeHubConfig;
import simplegenomehub.model.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Simple test runner for basic functionality validation
 * 
 * @author SimpleGenomeHub
 */
public class SimpleTestRunner {
    
    public static void main(String[] args) {
        System.out.println("=== SimpleGenomeHub Basic Functionality Tests ===");
        
        try {
            testConfiguration();
            testSpeciesInfo();
            testGenomeData();
            testSpeciesManager();
            
            System.out.println("\n=== All tests completed successfully ===");
            
        } catch (Exception e) {
            System.err.println("Test failed with error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testConfiguration() throws IOException {
        System.out.println("\n1. Testing Configuration Management:");
        
        SimpleGenomeHubConfig config = SimpleGenomeHubConfig.getInstance();
        System.out.println("   ✓ Config instance created");
        
        // Test property operations
        config.setProperty("test.key", "test.value");
        String value = config.getProperty("test.key");
        assert "test.value".equals(value) : "Property not set correctly";
        System.out.println("   ✓ Property operations work");
        
        // Test integer property
        config.setIntProperty("test.int", 42);
        int intValue = config.getIntProperty("test.int", 0);
        assert intValue == 42 : "Integer property not set correctly";
        System.out.println("   ✓ Integer property operations work");
        
        // Test data directory validation
        File tempDir = Files.createTempDirectory("SGHTest").toFile();
        tempDir.deleteOnExit();
        
        boolean result = config.setDataRootDir(tempDir);
        assert result : "Failed to set valid data directory";
        System.out.println("   ✓ Data directory validation works");
        
        System.out.println("   Configuration tests passed!");
    }
    
    private static void testSpeciesInfo() throws IOException {
        System.out.println("\n2. Testing SpeciesInfo Model:");
        
        SpeciesInfo species = new SpeciesInfo("Arabidopsis_thaliana", "TAIR10");
        System.out.println("   ✓ SpeciesInfo created");
        
        assert "Arabidopsis_thaliana".equals(species.getSpeciesName()) : "Species name not set";
        assert "TAIR10".equals(species.getVersion()) : "Version not set";
        System.out.println("   ✓ Basic properties work");
        
        String dirName = species.getSpeciesDirectoryName();
        assert "Arabidopsis_thaliana.TAIR10".equals(dirName) : "Directory name format incorrect";
        System.out.println("   ✓ Directory name format correct");
        
        // Test file structure initialization
        File tempDir = Files.createTempDirectory("SGHSpeciesTest").toFile();
        tempDir.deleteOnExit();
        
        species.initializeFileStructure(tempDir);
        boolean created = species.createDirectoryStructure();
        assert created : "Failed to create directory structure";
        System.out.println("   ✓ Directory structure creation works");
        
        System.out.println("   SpeciesInfo tests passed!");
    }
    
    private static void testGenomeData() throws IOException {
        System.out.println("\n3. Testing GenomeData Model:");
        
        GenomeData genomeData = new GenomeData();
        System.out.println("   ✓ GenomeData created");
        
        genomeData.setGenomeSize(120000000L);
        genomeData.setGeneCount(27416);
        genomeData.setTranscriptCount(48359);
        genomeData.addChromosomeStat("chr1", 50000000L, 41.25);
        genomeData.addChromosomeStat("chr2", 70000000L, 39.75);
        
        String formattedSize = genomeData.getFormattedGenomeSize();
        assert formattedSize.contains("120.00 Mb") : "Genome size formatting incorrect";
        System.out.println("   ✓ Size formatting works: " + formattedSize);
        
        String summary = genomeData.getSummaryStats();
        assert summary.contains("27,416") : "Gene count formatting incorrect";
        System.out.println("   ✓ Summary statistics work: " + summary);
        
        // Test file operations
        File tempFile = Files.createTempFile("SGHStats", ".txt").toFile();
        tempFile.deleteOnExit();
        
        boolean saved = genomeData.saveToFile(tempFile);
        assert saved : "Failed to save genome data";
        System.out.println("   ✓ File save works");
        
        String statsText = new String(Files.readAllBytes(tempFile.toPath()), StandardCharsets.UTF_8);
        assert statsText.contains("#####################chromosome stat") : "Chromosome stat block missing";
        assert statsText.contains("chromosome1.name=chr1") : "Chromosome 1 name not saved";

        GenomeData loaded = GenomeData.loadFromFile(tempFile);
        assert loaded.getGeneCount() == 27416 : "Failed to load genome data correctly";
        assert loaded.getChromosomeStats().size() == 2 : "Failed to load chromosome stats";
        assert "chr1".equals(loaded.getChromosomeStats().get(0).getName()) : "Chromosome 1 name not loaded";
        System.out.println("   ✓ File load works");
        
        System.out.println("   GenomeData tests passed!");
    }
    
    private static void testSpeciesManager() throws IOException {
        System.out.println("\n4. Testing SpeciesManager:");
        
        SpeciesManager manager = new SpeciesManager();
        System.out.println("   ✓ SpeciesManager created");
        
        int initialCount = manager.getSpeciesCount();
        System.out.println("   ✓ Initial species count: " + initialCount);
        
        // Set up temporary data directory
        File tempDataDir = Files.createTempDirectory("SGHManagerTest").toFile();
        tempDataDir.deleteOnExit();
        
        boolean dirSet = manager.setDataRootDirectory(tempDataDir);
        assert dirSet : "Failed to set data root directory";
        System.out.println("   ✓ Data root directory set");
        
        // Test species addition
        SpeciesInfo testSpecies = new SpeciesInfo("Test_species", "v1.0");
        testSpecies.setNotes("Test species for validation");
        
        boolean added = manager.addSpecies(testSpecies);
        assert added : "Failed to add species";
        System.out.println("   ✓ Species addition works");
        
        assert manager.getSpeciesCount() == initialCount + 1 : "Species count not updated";
        System.out.println("   ✓ Species count updated");
        
        // Test species retrieval
        SpeciesInfo retrieved = manager.getSpecies("Test_species", "v1.0");
        assert retrieved != null : "Failed to retrieve species";
        assert "Test_species".equals(retrieved.getSpeciesName()) : "Retrieved wrong species";
        System.out.println("   ✓ Species retrieval works");
        
        System.out.println("   SpeciesManager tests passed!");
    }
}
