/*
 * SimpleGenomeHub Multiple Synteny Dialog
 * Dialog wrapper for multiple synteny UI
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
 * Dialog for multiple synteny UI
 */
public class MultipleSyntenyDialog extends JDialog {

    private final SpeciesInfo initialSpecies;
    private final SpeciesManager speciesManager;
    private MultipleSyntenyPanel multipleSyntenyPanel;

    public MultipleSyntenyDialog(Window parent, SpeciesInfo initialSpecies, SpeciesManager speciesManager) {
        super(parent, "Multiple Synteny", ModalityType.MODELESS);

        this.initialSpecies = initialSpecies;
        this.speciesManager = speciesManager;

        initializeDialog();
        setupPanel();
        setupLayout();
        setupEventHandlers();

        setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        setSize(1600, 800);
        setLocationRelativeTo(parent);
    }

    private void initializeDialog() {
        setResizable(true);
        setMinimumSize(new Dimension(1200, 720));
    }

    private void setupPanel() {
        multipleSyntenyPanel = new MultipleSyntenyPanel(speciesManager);
        if (initialSpecies != null) {
            multipleSyntenyPanel.initializeWithPrimaryGenome(initialSpecies);
        }
    }

    private void setupLayout() {
        setLayout(new BorderLayout());
        add(multipleSyntenyPanel, BorderLayout.CENTER);
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
        if (multipleSyntenyPanel != null) {
            multipleSyntenyPanel.cleanup();
        }
        dispose();
    }
}
