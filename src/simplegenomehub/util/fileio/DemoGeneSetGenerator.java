package simplegenomehub.util.fileio;

import simplegenomehub.model.SpeciesInfo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Generates a reusable demo Gene Set file for a species.
 * The output contains one representative transcript for each sampled gene.
 */
public final class DemoGeneSetGenerator {

    private static final Logger logger = Logger.getLogger(DemoGeneSetGenerator.class.getName());
    private static final int DEFAULT_DEMO_GENE_COUNT = 100;
    private static final String DEMO_GENE_SET_SUFFIX = ".DemoData.gene.txt";
    private static final long RANDOM_SEED_SALT = 0x5DEECE66DL;

    private DemoGeneSetGenerator() {
    }

    public static Result generateDemoGeneSet(SpeciesInfo species) throws IOException {
        return generateDemoGeneSet(species, DEFAULT_DEMO_GENE_COUNT);
    }

    public static Result generateDemoGeneSet(SpeciesInfo species, int targetGeneCount) throws IOException {
        if (species == null) {
            throw new IllegalArgumentException("Species is required.");
        }
        if (targetGeneCount <= 0) {
            throw new IllegalArgumentException("Target gene count must be greater than 0.");
        }

        File geneSetDir = species.getGeneSetDir();
        if (geneSetDir == null) {
            throw new IOException("GeneSet directory is not available.");
        }
        if (!geneSetDir.exists() && !geneSetDir.mkdirs()) {
            throw new IOException("Failed to create GeneSet directory: " + geneSetDir.getAbsolutePath());
        }

        LinkedHashMap<String, String> representativeTranscriptByGene = loadRepresentativeTranscriptByGene(species);
        if (representativeTranscriptByGene.isEmpty()) {
            throw new IOException("No representative transcripts could be resolved for " + species.getSpeciesDirectoryName());
        }

        List<String> geneIds = new ArrayList<>(representativeTranscriptByGene.keySet());
        Collections.shuffle(geneIds, createRandom(species, targetGeneCount));

        int selectedGeneCount = Math.min(targetGeneCount, geneIds.size());
        List<String> selectedTranscriptIds = new ArrayList<>(selectedGeneCount);
        for (int i = 0; i < selectedGeneCount; i++) {
            String transcriptId = representativeTranscriptByGene.get(geneIds.get(i));
            if (transcriptId != null && !transcriptId.trim().isEmpty()) {
                selectedTranscriptIds.add(transcriptId.trim());
            }
        }

        if (selectedTranscriptIds.isEmpty()) {
            throw new IOException("Resolved demo transcript list is empty for " + species.getSpeciesDirectoryName());
        }

        File outputFile = new File(geneSetDir, buildDemoGeneSetFileName(species));
        writeGeneSetFile(outputFile, selectedTranscriptIds);
        logger.info("Generated demo Gene Set: " + outputFile.getAbsolutePath()
            + " (" + selectedTranscriptIds.size() + " representative transcripts)");
        return new Result(outputFile, selectedTranscriptIds.size(), representativeTranscriptByGene.size());
    }

    public static String buildDemoGeneSetFileName(SpeciesInfo species) {
        return species.getSpeciesName() + "." + species.getVersion() + DEMO_GENE_SET_SUFFIX;
    }

    private static LinkedHashMap<String, String> loadRepresentativeTranscriptByGene(SpeciesInfo species) throws IOException {
        File representativeTranscriptFile = locateRepresentativeTranscriptFile(species);
        if (representativeTranscriptFile != null && representativeTranscriptFile.isFile()) {
            LinkedHashMap<String, String> fromFasta = loadRepresentativeTranscriptByGeneFromFasta(representativeTranscriptFile);
            if (!fromFasta.isEmpty()) {
                return fromFasta;
            }
        }
        return loadRepresentativeTranscriptByGeneFromAnnotation(species.getAnnotationFile());
    }

    private static File locateRepresentativeTranscriptFile(SpeciesInfo species) {
        File sequenceDir = species.getSequenceDir();
        if (sequenceDir == null || !sequenceDir.isDirectory()) {
            return null;
        }

        String expectedPrefix = species.getSpeciesName() + "." + species.getVersion();
        File[] transcriptFiles = sequenceDir.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase(Locale.ROOT);
            return lowerName.endsWith(".transcripts.fasta") && !lowerName.contains(".all_transcripts.");
        });
        if (transcriptFiles == null || transcriptFiles.length == 0) {
            return null;
        }

        for (File transcriptFile : transcriptFiles) {
            String name = transcriptFile.getName();
            if (name.startsWith(expectedPrefix + ".") || name.startsWith(expectedPrefix + "_")) {
                return transcriptFile;
            }
        }
        return transcriptFiles[0];
    }

    private static LinkedHashMap<String, String> loadRepresentativeTranscriptByGeneFromFasta(File transcriptFile)
        throws IOException {
        LinkedHashMap<String, String> transcriptByGene = new LinkedHashMap<>();
        TranscriptToGeneMapper mapper = new TranscriptToGeneMapper();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(transcriptFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(">")) {
                    continue;
                }

                String transcriptId = extractTranscriptIdFromHeader(line);
                if (transcriptId == null) {
                    continue;
                }

                String geneId = mapper.mapTranscriptToGene(transcriptId);
                if (geneId == null || geneId.trim().isEmpty()) {
                    geneId = transcriptId;
                }
                transcriptByGene.putIfAbsent(geneId, transcriptId);
            }
        }
        return transcriptByGene;
    }

    private static LinkedHashMap<String, String> loadRepresentativeTranscriptByGeneFromAnnotation(File annotationFile)
        throws IOException {
        LinkedHashMap<String, String> transcriptByGene = new LinkedHashMap<>();
        if (annotationFile == null || !annotationFile.isFile()) {
            return transcriptByGene;
        }

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(annotationFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }

                String[] fields = line.split("\t");
                if (fields.length < 9) {
                    continue;
                }

                String featureType = fields[2].trim().toLowerCase(Locale.ROOT);
                if (!"mrna".equals(featureType) && !"transcript".equals(featureType)) {
                    continue;
                }

                Map<String, String> attributes = parseAttributes(fields[8]);
                String transcriptId = firstNonBlank(
                    attributes.get("ID"),
                    attributes.get("transcript_id"),
                    attributes.get("Name")
                );
                String geneId = firstNonBlank(
                    normalizeParentGeneId(attributes.get("Parent")),
                    attributes.get("gene_id"),
                    attributes.get("geneID")
                );

                if (transcriptId != null && geneId != null) {
                    transcriptByGene.putIfAbsent(geneId, transcriptId);
                }
            }
        }
        return transcriptByGene;
    }

    private static Map<String, String> parseAttributes(String rawAttributes) {
        Map<String, String> attributes = new LinkedHashMap<>();
        if (rawAttributes == null || rawAttributes.trim().isEmpty()) {
            return attributes;
        }

        String[] parts = rawAttributes.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int separatorIndex = trimmed.indexOf('=');
            if (separatorIndex < 0) {
                separatorIndex = trimmed.indexOf(' ');
            }
            if (separatorIndex <= 0 || separatorIndex >= trimmed.length() - 1) {
                continue;
            }

            String key = trimmed.substring(0, separatorIndex).trim();
            String value = trimmed.substring(separatorIndex + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                attributes.putIfAbsent(key, stripQuotes(value));
            }
        }
        return attributes;
    }

    private static String normalizeParentGeneId(String parentValue) {
        if (parentValue == null || parentValue.trim().isEmpty()) {
            return null;
        }

        String firstParent = parentValue.split(",")[0].trim();
        if (firstParent.startsWith("gene:")) {
            return firstParent.substring("gene:".length());
        }
        return firstParent;
    }

    private static String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String extractTranscriptIdFromHeader(String headerLine) {
        if (headerLine == null || !headerLine.startsWith(">")) {
            return null;
        }
        String header = headerLine.substring(1).trim();
        if (header.isEmpty()) {
            return null;
        }

        int tabIndex = header.indexOf('\t');
        if (tabIndex > 0) {
            header = header.substring(0, tabIndex);
        }

        int whitespaceIndex = header.indexOf(' ');
        if (whitespaceIndex > 0) {
            header = header.substring(0, whitespaceIndex);
        }

        int pipeIndex = header.indexOf('|');
        if (pipeIndex > 0) {
            header = header.substring(0, pipeIndex);
        }

        return header.trim();
    }

    private static Random createRandom(SpeciesInfo species, int targetGeneCount) {
        long seed = RANDOM_SEED_SALT
            ^ species.getSpeciesName().hashCode()
            ^ ((long) species.getVersion().hashCode() << 32)
            ^ targetGeneCount
            ^ System.nanoTime();
        return new Random(seed);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private static void writeGeneSetFile(File outputFile, List<String> transcriptIds) throws IOException {
        Set<String> uniqueTranscriptIds = new LinkedHashSet<>();
        for (String transcriptId : transcriptIds) {
            if (transcriptId != null && !transcriptId.trim().isEmpty()) {
                uniqueTranscriptIds.add(transcriptId.trim());
            }
        }

        try (BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8))) {
            for (String transcriptId : uniqueTranscriptIds) {
                writer.write(transcriptId);
                writer.newLine();
            }
        }
    }

    public static final class Result {
        private final File outputFile;
        private final int selectedTranscriptCount;
        private final int availableGeneCount;

        Result(File outputFile, int selectedTranscriptCount, int availableGeneCount) {
            this.outputFile = outputFile;
            this.selectedTranscriptCount = selectedTranscriptCount;
            this.availableGeneCount = availableGeneCount;
        }

        public File getOutputFile() {
            return outputFile;
        }

        public int getSelectedTranscriptCount() {
            return selectedTranscriptCount;
        }

        public int getAvailableGeneCount() {
            return availableGeneCount;
        }
    }
}
