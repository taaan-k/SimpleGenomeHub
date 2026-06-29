/*
 * SimpleGenomeHub Genome Compare Dialog
 * Dialog wrapper for genome compare UI
 */
package simplegenomehub.gui;

import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Dialog for genome compare UI
 */
public class GenomeCompareDialog extends JDialog {

    private final SpeciesInfo initialSpecies;
    private final SpeciesManager speciesManager;
    private GenomeComparePanel genomeComparePanel;

    public GenomeCompareDialog(Window parent, SpeciesInfo initialSpecies, SpeciesManager speciesManager) {
        super(parent, "MCscanX (Pure Java)", ModalityType.MODELESS);

        this.initialSpecies = initialSpecies;
        this.speciesManager = speciesManager;

        initializeDialog();
        setupComparePanel();
        setupLayout();
        setupEventHandlers();

        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setSize(1100, 900);
        setLocationRelativeTo(parent);
    }

    private void initializeDialog() {
        setResizable(true);
        setMinimumSize(new Dimension(760, 600));
    }

    private void setupComparePanel() {
        genomeComparePanel = new GenomeComparePanel(speciesManager);
        if (initialSpecies != null) {
            genomeComparePanel.initializeWithPrimaryGenome(initialSpecies);
        }
    }

    private void setupLayout() {
        setLayout(new BorderLayout());
        add(genomeComparePanel, BorderLayout.CENTER);
    }

    private void setupEventHandlers() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeDialog();
            }
        });

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

    private void closeDialog() {
        if (genomeComparePanel != null) {
            genomeComparePanel.cleanup();
        }
        dispose();
    }
}
