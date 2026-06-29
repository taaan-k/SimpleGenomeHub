package simplegenomehub.gui;

import simplegenomehub.blast.BlastConfig;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.model.SpeciesManager;
import simplegenomehub.util.fileio.SequenceExtractor;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

final class RegionSetBlastLauncher {

    private static final Logger logger = Logger.getLogger(RegionSetBlastLauncher.class.getName());

    private RegionSetBlastLauncher() {
    }

    static void launchGeneOverlapBlast(Component parentComponent, SpeciesManager speciesManager,
                                       SpeciesInfo species, File regionSetFile,
                                       BlastConfig.SequenceType sequenceType) {
        if (!validateCommonInputs(parentComponent, speciesManager, species, regionSetFile)) {
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(parentComponent);
        JDialog progressDialog = createProgressDialog(owner,
            "Preparing " + getSequenceDisplayName(sequenceType) + " BLAST Query",
            "Reading Region Set and resolving overlapping genes...");

        SwingWorker<PreparedBlastQuery, Void> worker = new SwingWorker<PreparedBlastQuery, Void>() {
            @Override
            protected PreparedBlastQuery doInBackground() throws Exception {
                List<String> overlappingGeneIds = GeneSetImportSupport.collectOverlappingGeneIds(species, regionSetFile);
                if (overlappingGeneIds.isEmpty()) {
                    throw new GeneSetImportSupport.NoGeneFoundException("No Gene Found");
                }
                return buildGeneSequenceQuery(species, overlappingGeneIds, sequenceType);
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    PreparedBlastQuery prepared = get();
                    openBlastDialog(owner, species, speciesManager, sequenceType, prepared.fastaText);
                    if (!prepared.missingIds.isEmpty()) {
                        JOptionPane.showMessageDialog(parentComponent,
                            buildPartialSuccessMessage(prepared, sequenceType),
                            "BLAST Query Prepared with Missing Sequences",
                            JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    handleBlastPreparationException(parentComponent, "prepare BLAST query from region set", ex);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    static void launchRegionSequenceBlast(Component parentComponent, SpeciesManager speciesManager,
                                          SpeciesInfo species, File regionSetFile) {
        if (!validateCommonInputs(parentComponent, speciesManager, species, regionSetFile)) {
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(parentComponent);
        JDialog progressDialog = createProgressDialog(owner,
            "Preparing Region Sequence BLAST Query",
            "Reading Region Set and extracting genomic sequences...");

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                List<GeneSetFileSupport.RegionEntry> regionEntries = loadRegionEntries(regionSetFile);
                if (regionEntries.isEmpty()) {
                    throw new IllegalArgumentException("The selected Region Set does not contain any valid regions.");
                }

                File genomeFile = requireGenomeFile(species);
                Map<String, String> genomeSequences = SequenceExtractor.loadGenomeSequences(genomeFile);
                StringBuilder fastaBuilder = new StringBuilder();

                for (GeneSetFileSupport.RegionEntry entry : regionEntries) {
                    SequenceExtractor.FeatureInfo feature = new SequenceExtractor.FeatureInfo(
                        entry.getChromosomeName(),
                        "region",
                        toIntCoordinate(entry.getStartPos()),
                        toIntCoordinate(entry.getEndPos()),
                        "+"
                    );
                    String sequence = SequenceExtractor.extractFeatureSequence(genomeSequences, feature);
                    if (sequence == null || sequence.trim().isEmpty()) {
                        throw new IllegalStateException("Failed to extract sequence for region: "
                            + entry.getChromosomeName() + ":" + entry.getStartPos() + "-" + entry.getEndPos());
                    }

                    String fastaEntry = GeneSetBlastLauncher.buildFastaEntry(
                        ">" + entry.getChromosomeName() + "_" + entry.getStartPos() + "_" + entry.getEndPos(),
                        entry.getChromosomeName() + "_" + entry.getStartPos() + "_" + entry.getEndPos(),
                        sequence
                    );
                    if (fastaBuilder.length() > 0) {
                        fastaBuilder.append('\n');
                    }
                    fastaBuilder.append(fastaEntry);
                }

                return fastaBuilder.toString().trim();
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    String fastaText = get();
                    openBlastDialog(owner, species, speciesManager, BlastConfig.SequenceType.GENOME, fastaText);
                } catch (Exception ex) {
                    handleBlastPreparationException(parentComponent, "prepare region sequence BLAST query", ex);
                }
            }
        };

        worker.execute();
        progressDialog.setVisible(true);
    }

    private static boolean validateCommonInputs(Component parentComponent, SpeciesManager speciesManager,
                                                SpeciesInfo species, File regionSetFile) {
        if (speciesManager == null || species == null) {
            JOptionPane.showMessageDialog(parentComponent,
                "Unable to resolve the selected region set to a species.",
                "Run BLAST Failed",
                JOptionPane.WARNING_MESSAGE);
            return false;
        }

        if (regionSetFile == null || !regionSetFile.exists()) {
            JOptionPane.showMessageDialog(parentComponent,
                "The selected region set file no longer exists.",
                "Run BLAST Failed",
                JOptionPane.WARNING_MESSAGE);
            return false;
        }

        if (GeneSetFileSupport.detectSetKind(regionSetFile) != GeneSetFileSupport.SetKind.REGION) {
            JOptionPane.showMessageDialog(parentComponent,
                "The selected file is not a Region Set.",
                "Run BLAST Failed",
                JOptionPane.WARNING_MESSAGE);
            return false;
        }

        return true;
    }

    private static PreparedBlastQuery buildGeneSequenceQuery(SpeciesInfo species, List<String> geneIds,
                                                             BlastConfig.SequenceType sequenceType) throws Exception {
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
            String fastaEntry = GeneSetBlastLauncher.buildFastaEntry(header, geneId + "_" + fallbackSuffix, sequence);
            if (fastaEntry.isEmpty()) {
                missingGeneIds.add(geneId);
                continue;
            }
            if (fastaBuilder.length() > 0) {
                fastaBuilder.append('\n');
            }
            fastaBuilder.append(fastaEntry);
            foundCount++;
        }

        if (foundCount == 0) {
            throw new IllegalStateException("No " + getSequenceDisplayName(sequenceType)
                + " sequences were found for genes overlapping the selected Region Set.");
        }

        return new PreparedBlastQuery(fastaBuilder.toString().trim(), geneIds.size(), foundCount, missingGeneIds);
    }

    private static List<GeneSetFileSupport.RegionEntry> loadRegionEntries(File regionSetFile) throws Exception {
        String rawContent = GeneSetFileSupport.readGeneSetContent(regionSetFile);
        return GeneSetFileSupport.parseRegionEntries(rawContent);
    }

    private static File requireGenomeFile(SpeciesInfo species) {
        File genomeFile = species.getGenomeFile();
        if (genomeFile == null || !genomeFile.isFile()) {
            throw new IllegalStateException("Genome FASTA file is missing for species: "
                + species.getSpeciesDirectoryName());
        }
        return genomeFile;
    }

    private static int toIntCoordinate(long coordinate) {
        if (coordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Region coordinate exceeds supported range: " + coordinate);
        }
        return (int) coordinate;
    }

    private static void openBlastDialog(Window owner, SpeciesInfo species, SpeciesManager speciesManager,
                                        BlastConfig.SequenceType sequenceType, String fastaText) {
        BlastDialog dialog = new BlastDialog(owner, species, speciesManager, sequenceType, fastaText);
        dialog.setVisible(true);
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

    private static JDialog createProgressDialog(Window owner, String title, String message) {
        JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JLabel label = new JLabel("<html>" + escapeHtml(message).replace("\n", "<br>") + "</html>");
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

    private static String buildPartialSuccessMessage(PreparedBlastQuery prepared,
                                                     BlastConfig.SequenceType sequenceType) {
        StringBuilder message = new StringBuilder();
        message.append("Loaded ")
            .append(prepared.foundCount)
            .append(" of ")
            .append(prepared.totalInputCount)
            .append(" overlapping genes into the BLAST query.\n\nMissing ")
            .append(getSequenceDisplayName(sequenceType).toLowerCase(Locale.ROOT))
            .append(" sequences for ")
            .append(prepared.missingIds.size())
            .append(" genes.");

        int previewCount = Math.min(12, prepared.missingIds.size());
        if (previewCount > 0) {
            message.append("\n\nFirst missing IDs:\n");
            for (int i = 0; i < previewCount; i++) {
                message.append(prepared.missingIds.get(i)).append('\n');
            }
            if (prepared.missingIds.size() > previewCount) {
                message.append("...");
            }
        }
        return message.toString().trim();
    }

    private static void handleBlastPreparationException(Component parentComponent, String action, Exception ex) {
        Throwable cause = ex;
        if (ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null) {
            cause = ex.getCause();
        }

        if (cause instanceof GeneSetImportSupport.NoGeneFoundException) {
            JOptionPane.showMessageDialog(parentComponent,
                cause.getMessage(),
                "Run BLAST Failed",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        logger.warning("Failed to " + action + ": " + cause.getMessage());
        JOptionPane.showMessageDialog(parentComponent,
            "Failed to " + action + ":\n" + cause.getMessage(),
            "Run BLAST Failed",
            JOptionPane.ERROR_MESSAGE);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    private static final class PreparedBlastQuery {
        private final String fastaText;
        private final int totalInputCount;
        private final int foundCount;
        private final List<String> missingIds;

        private PreparedBlastQuery(String fastaText, int totalInputCount, int foundCount, Collection<String> missingIds) {
            this.fastaText = fastaText;
            this.totalInputCount = totalInputCount;
            this.foundCount = foundCount;
            this.missingIds = new ArrayList<>(missingIds);
        }
    }
}
