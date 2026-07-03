package simplegenomehub.gui;

import simplegenomehub.blast.BlastConfig;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Launches BLAST queries from GeneSet files by resolving gene IDs to sequences.
 */
final class GeneSetBlastLauncher {

    private static final Logger logger = Logger.getLogger(GeneSetBlastLauncher.class.getName());
    private static final int FASTA_LINE_LENGTH = 80;
    private static final int MAX_MISSING_IDS_TO_SHOW = 12;

    private GeneSetBlastLauncher() {
    }

    static String buildFastaEntry(String header, String fallbackHeader, String sequence) {
        StringBuilder builder = new StringBuilder();
        appendFastaEntry(builder, header, fallbackHeader, sequence);
        return builder.toString().trim();
    }

    static void launch(Component parentComponent, SpeciesManager speciesManager, SpeciesInfo species,
                       File geneSetFile, BlastConfig.SequenceType sequenceType) {
        if (speciesManager == null || species == null) {
            JOptionPane.showMessageDialog(parentComponent,
                "Unable to resolve the selected gene set to a species.",
                "Run BLAST Failed",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (geneSetFile == null || !geneSetFile.exists()) {
            JOptionPane.showMessageDialog(parentComponent,
                "The selected gene set file no longer exists.",
                "Run BLAST Failed",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (GeneSetFileSupport.detectSetKind(geneSetFile) == GeneSetFileSupport.SetKind.REGION) {
            JOptionPane.showMessageDialog(parentComponent,
                "BLAST query generation is only available for GeneSet files that contain gene IDs.",
                "Run BLAST Failed",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(parentComponent);
        JDialog progressDialog = createProgressDialog(owner, sequenceType, geneSetFile.getName());

        SwingWorker<PreparedBlastQuery, Void> worker = new SwingWorker<PreparedBlastQuery, Void>() {
            @Override
            protected PreparedBlastQuery doInBackground() throws Exception {
                String rawContent = GeneSetFileSupport.readGeneSetContent(geneSetFile);
                List<String> geneIds = GeneSetFileSupport.parseGeneIds(rawContent);
                if (geneIds.isEmpty()) {
                    throw new IllegalArgumentException("The selected GeneSet file does not contain any gene IDs.");
                }

                GeneDataRetriever dataRetriever = new GeneDataRetriever();
                String sequenceKey = getSequenceKey(sequenceType);
                String fallbackSuffix = getFallbackSuffix(sequenceType);
                StringBuilder fastaBuilder = new StringBuilder();
                List<String> missingGeneIds = new ArrayList<>();
                int foundCount = 0;

                for (String geneId : geneIds) {
                    Map<String, String> sequences = dataRetriever.getGeneSequences(geneId, species);
                    String sequence = sequences.get(sequenceKey);
                    if (sequence == null || sequence.trim().isEmpty()) {
                        missingGeneIds.add(geneId);
                        continue;
                    }

                    String header = sequences.get(sequenceKey + "_header");
                    appendFastaEntry(fastaBuilder, header, geneId + "_" + fallbackSuffix, sequence);
                    foundCount++;
                }

                if (foundCount == 0) {
                    throw new IllegalStateException("No " + getSequenceDisplayName(sequenceType)
                        + " sequences were found for the selected GeneSet.");
                }

                return new PreparedBlastQuery(
                    fastaBuilder.toString().trim(),
                    geneIds.size(),
                    foundCount,
                    missingGeneIds
                );
            }

            @Override
            protected void done() {
                progressDialog.dispose();

                try {
                    PreparedBlastQuery prepared = get();
                    BlastDialog dialog = new BlastDialog(owner, species, speciesManager,
                        sequenceType, prepared.fastaText);
                    dialog.setVisible(true);

                    if (!prepared.missingGeneIds.isEmpty()) {
                        JOptionPane.showMessageDialog(parentComponent,
                            buildPartialSuccessMessage(prepared, sequenceType),
                            "BLAST Query Prepared with Missing Sequences",
                            JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    logger.warning("Failed to prepare BLAST query from gene set: " + ex.getMessage());
                    JOptionPane.showMessageDialog(parentComponent,
                        "Failed to prepare BLAST query:\n" + ex.getMessage(),
                        "Run BLAST Failed",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private static JDialog createProgressDialog(Window owner, BlastConfig.SequenceType sequenceType, String geneSetName) {
        JDialog dialog = new JDialog(owner,
            "Preparing " + getSequenceDisplayName(sequenceType) + " BLAST Query",
            Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JLabel label = new JLabel("<html>Reading GeneSet <b>" + geneSetName
            + "</b><br>Resolving " + getSequenceDisplayName(sequenceType).toLowerCase()
            + " sequences for BLAST...</html>");
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(320, 20));

        content.add(label, BorderLayout.CENTER);
        content.add(progressBar, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        return dialog;
    }

    private static void appendFastaEntry(StringBuilder builder, String header, String fallbackHeader, String sequence) {
        String cleanedSequence = sequence == null ? "" : sequence.replaceAll("\\s+", "");
        if (cleanedSequence.isEmpty()) {
            return;
        }

        String headerLine = header != null ? header.trim() : "";
        if (headerLine.isEmpty()) {
            headerLine = ">" + fallbackHeader;
        } else if (!headerLine.startsWith(">")) {
            headerLine = ">" + headerLine;
        }

        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(headerLine).append('\n');
        builder.append(wrapSequence(cleanedSequence));
    }

    private static String wrapSequence(String sequence) {
        StringBuilder wrapped = new StringBuilder();
        for (int i = 0; i < sequence.length(); i += FASTA_LINE_LENGTH) {
            int end = Math.min(i + FASTA_LINE_LENGTH, sequence.length());
            wrapped.append(sequence, i, end).append('\n');
        }
        return wrapped.toString().trim();
    }

    private static String getSequenceKey(BlastConfig.SequenceType sequenceType) {
        if (sequenceType == BlastConfig.SequenceType.PROTEIN) {
            return "protein";
        }
        if (sequenceType == BlastConfig.SequenceType.CDS) {
            return "cds";
        }
        return "transcript";
    }

    private static String getFallbackSuffix(BlastConfig.SequenceType sequenceType) {
        if (sequenceType == BlastConfig.SequenceType.PROTEIN) {
            return "protein";
        }
        if (sequenceType == BlastConfig.SequenceType.CDS) {
            return "cds";
        }
        return "transcript";
    }

    private static String getSequenceDisplayName(BlastConfig.SequenceType sequenceType) {
        if (sequenceType == BlastConfig.SequenceType.PROTEIN) {
            return "Protein";
        }
        if (sequenceType == BlastConfig.SequenceType.CDS) {
            return "CDS";
        }
        return "Transcript";
    }

    private static String buildPartialSuccessMessage(PreparedBlastQuery prepared, BlastConfig.SequenceType sequenceType) {
        StringBuilder message = new StringBuilder();
        message.append("Loaded ")
            .append(prepared.foundCount)
            .append(" of ")
            .append(prepared.totalGeneCount)
            .append(" gene IDs into the BLAST query.\n\nMissing ")
            .append(getSequenceDisplayName(sequenceType).toLowerCase())
            .append(" sequences for ")
            .append(prepared.missingGeneIds.size())
            .append(" gene IDs.");

        int previewCount = Math.min(MAX_MISSING_IDS_TO_SHOW, prepared.missingGeneIds.size());
        if (previewCount > 0) {
            message.append("\n\nFirst missing IDs:\n");
            for (int i = 0; i < previewCount; i++) {
                message.append(prepared.missingGeneIds.get(i)).append('\n');
            }
            if (prepared.missingGeneIds.size() > previewCount) {
                message.append("...");
            }
        }

        return message.toString().trim();
    }

    private static final class PreparedBlastQuery {
        private final String fastaText;
        private final int totalGeneCount;
        private final int foundCount;
        private final List<String> missingGeneIds;

        private PreparedBlastQuery(String fastaText, int totalGeneCount, int foundCount, List<String> missingGeneIds) {
            this.fastaText = fastaText;
            this.totalGeneCount = totalGeneCount;
            this.foundCount = foundCount;
            this.missingGeneIds = new ArrayList<>(missingGeneIds);
        }
    }
}
