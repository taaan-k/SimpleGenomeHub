/*
 * Gene Info Menu Manager
 * Handles unified menu for Gene Viewer and Species Identification functionality
 */
package simplegenomehub.gui;

import simplegenomehub.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Manages the hierarchical menu for unified Gene Info. functionality
 * Provides access to both gene search and species identification through a single interface
 *
 * @author SimpleGenomeHub
 */
public class GeneInfoMenuManager {

    private final Window parentWindow;
    private final SpeciesManager speciesManager;
    private final SpeciesInfo currentSpecies;

    /**
     * Constructor
     *
     * @param parentWindow Parent window for dialog positioning
     * @param speciesManager Species manager for data access
     * @param currentSpecies Currently selected species (can be null)
     */
    public GeneInfoMenuManager(Window parentWindow, SpeciesManager speciesManager, SpeciesInfo currentSpecies) {
        this.parentWindow = parentWindow;
        this.speciesManager = speciesManager;
        this.currentSpecies = currentSpecies;
    }

    /**
     * Create and show the Gene Info. popup menu
     *
     * @param sourceButton Button that triggered the menu (for positioning)
     */
    public void showGeneInfoMenu(JButton sourceButton) {
        JPopupMenu menu = new JPopupMenu();

        // Gene Search & Analysis section
        JMenuItem geneSearchHeader = new JMenuItem("Gene Search & Analysis");
        geneSearchHeader.setEnabled(false);
        geneSearchHeader.setFont(SimpleGenomeHubStyle.bold(geneSearchHeader.getFont()));
        menu.add(geneSearchHeader);
        menu.addSeparator();

        // Search Gene by ID
        JMenuItem searchGeneItem = new JMenuItem("Search Gene by ID");
        searchGeneItem.setToolTipText("Open comprehensive gene information viewer");
        searchGeneItem.addActionListener(e -> openGeneViewer());
        menu.add(searchGeneItem);

        menu.addSeparator();

        // Species Identification section
        JMenuItem speciesIdHeader = new JMenuItem("Species Identification");
        speciesIdHeader.setEnabled(false);
        speciesIdHeader.setFont(SimpleGenomeHubStyle.bold(speciesIdHeader.getFont()));
        menu.add(speciesIdHeader);
        menu.addSeparator();

        // Identify Species from IDs
        JMenuItem identifySpeciesItem = new JMenuItem("Identify Species from IDs");
        identifySpeciesItem.setToolTipText("Identify species using sequence IDs");
        identifySpeciesItem.addActionListener(e -> openSpeciesIdentification());
        menu.add(identifySpeciesItem);

        // Show the menu
        menu.show(sourceButton, 0, sourceButton.getHeight());
    }

    /**
     * Open the Gene Information Viewer Dialog
     */
    private void openGeneViewer() {
        SwingUtilities.invokeLater(() -> {
            try {
                GeneInfoViewerDialog dialog = new GeneInfoViewerDialog(parentWindow, speciesManager, currentSpecies);
                dialog.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(parentWindow,
                    "Error opening gene viewer: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    
    
    /**
     * Open the Species Identification Dialog
     */
    private void openSpeciesIdentification() {
        SwingUtilities.invokeLater(() -> {
            try {
                SpeciesIdentificationDialog dialog = new SpeciesIdentificationDialog(
                    (Frame) SwingUtilities.getWindowAncestor(parentWindow), speciesManager);
                dialog.setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(parentWindow,
                    "Error opening species identification: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    
    }
