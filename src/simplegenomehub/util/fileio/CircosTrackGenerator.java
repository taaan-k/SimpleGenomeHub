package simplegenomehub.util.fileio;

import simplegenomehub.model.GenomeData;
import simplegenomehub.model.SpeciesInfo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Generates source data files for Circos tracks inside the project Work directory.
 */
public final class CircosTrackGenerator {

    private static final Logger logger = Logger.getLogger(CircosTrackGenerator.class.getName());

    public static final String WORK_DIR_NAME = "Work";
    public static final String GC_TRACK_OPTION = "GC Track";
    public static final String GENE_DENSITY_TRACK_OPTION = "Gene Density Track";
    public static final String[] TRACK_OPTIONS = {GC_TRACK_OPTION, GENE_DENSITY_TRACK_OPTION};

    public static final String GC_TRACK_FILE_NAME = "GCtrack.tab";
    public static final String GENE_DENSITY_TRACK_FILE_NAME = "GeneDensityTrack.tab";
    public static final String TRACK_INFO_FILE_NAME = "TrackInfo.tab";
    public static final String GENE_POS_FILE_NAME = "GenePos.tab";

    private static final int WINDOW_SIZE = 1_000_000;
    private static final int STEP_SIZE = 250_000;
    private static final int FIRST_TRACK_START_POS = 80;
    private static final int FIRST_TRACK_END_POS = 90;
    private static final int SECOND_TRACK_START_POS = 90;
    private static final int SECOND_TRACK_END_POS = 100;
    private static final int DEFAULT_CHR_BAR_START_POS = 80;
    private static final int DEFAULT_CHR_BAR_END_POS = 90;
    private static final int DEFAULT_CHR_LABEL_POS = 85;
    private static final int DEFAULT_LABEL_START_POS = 90;
    private static final int DEFAULT_LABEL_END_POS = 95;
    private static final int TRACK_BAND_WIDTH = 10;
    private static final int LABEL_BAND_WIDTH = 5;
    public static final String DEFAULT_GENE_POS_COLOR = "100,100,100";
    public static final String HIGHLIGHTED_GENE_POS_COLOR = "228,26,28";

    private CircosTrackGenerator() {
    }

    public static File ensureWorkDirectory(File projectDir) throws IOException {
        if (projectDir == null) {
            throw new IllegalArgumentException("Circos project directory cannot be null.");
        }
        if (!projectDir.exists()) {
            if (!projectDir.mkdirs()) {
                throw new IOException("Failed to create Circos project directory: " + projectDir.getAbsolutePath());
            }
        } else if (!projectDir.isDirectory()) {
            throw new IOException("Circos project path is not a directory: " + projectDir.getAbsolutePath());
        }

        File workDir = new File(projectDir, WORK_DIR_NAME);
        if (!workDir.exists() && !workDir.mkdirs()) {
            throw new IOException("Failed to create Work directory: " + workDir.getAbsolutePath());
        }
        if (!workDir.isDirectory()) {
            throw new IOException("Work path is not a directory: " + workDir.getAbsolutePath());
        }
        return workDir;
    }

    public static void generateSelectedTracks(SpeciesInfo species, File projectDir, List<String> selectedTracks)
        throws IOException {
        if (species == null) {
            throw new IllegalArgumentException("Species is required for Circos track generation.");
        }

        File workDir = ensureWorkDirectory(projectDir);
        Set<String> selectedTrackSet = new LinkedHashSet<>();
        if (selectedTracks != null) {
            for (String selectedTrack : selectedTracks) {
                if (selectedTrack != null) {
                    String trimmed = selectedTrack.trim();
                    if (!trimmed.isEmpty()) {
                        selectedTrackSet.add(trimmed);
                    }
                }
            }
        }

        File gcTrackFile = new File(workDir, GC_TRACK_FILE_NAME);
        if (selectedTrackSet.contains(GC_TRACK_OPTION)) {
            generateGcTrack(species, gcTrackFile);
        } else {
            deleteIfExists(gcTrackFile);
        }

        File geneDensityTrackFile = new File(workDir, GENE_DENSITY_TRACK_FILE_NAME);
        if (selectedTrackSet.contains(GENE_DENSITY_TRACK_OPTION)) {
            generateGeneDensityTrack(species, geneDensityTrackFile);
        } else {
            deleteIfExists(geneDensityTrackFile);
        }

        rewriteTrackInfoFile(projectDir, selectedTracks, gcTrackFile, geneDensityTrackFile);
    }

    public static void generateGenePosFile(SpeciesInfo species, File projectDir, Set<String> geneIds)
        throws IOException {
        if (projectDir == null) {
            throw new IllegalArgumentException("Circos project directory cannot be null.");
        }

        File genePosFile = new File(projectDir, GENE_POS_FILE_NAME);
        LinkedHashSet<String> orderedGeneIds = new LinkedHashSet<>();
        if (geneIds != null) {
            for (String geneId : geneIds) {
                if (geneId == null) {
                    continue;
                }
                String trimmed = geneId.trim();
                if (!trimmed.isEmpty()) {
                    orderedGeneIds.add(trimmed);
                }
            }
        }

        if (orderedGeneIds.isEmpty()) {
            deleteIfExists(genePosFile);
            return;
        }

        if (species == null) {
            throw new IOException("Species is required for GenePos.tab generation.");
        }

        File annotationFile = requireAnnotationFile(species);
        Map<String, GenePositionRecord> positionsById = readGenePositions(annotationFile);
        if (positionsById.isEmpty()) {
            throw new IOException("No gene positions could be parsed from the annotation file.");
        }

        TranscriptToGeneMapper transcriptToGeneMapper = new TranscriptToGeneMapper();
        List<GenePosOutputLine> outputLines = new ArrayList<>();
        for (String requestedGeneId : orderedGeneIds) {
            GenePositionRecord positionRecord = positionsById.get(requestedGeneId);
            if (positionRecord == null) {
                String mappedGeneId = transcriptToGeneMapper.mapTranscriptToGene(requestedGeneId);
                if (mappedGeneId != null && !mappedGeneId.equals(requestedGeneId)) {
                    positionRecord = positionsById.get(mappedGeneId);
                }
            }

            if (positionRecord != null) {
                outputLines.add(new GenePosOutputLine(
                    positionRecord.chromosome,
                    requestedGeneId,
                    positionRecord.start,
                    positionRecord.end
                ));
            }
        }

        if (outputLines.isEmpty()) {
            deleteIfExists(genePosFile);
            throw new IOException("No selected gene IDs could be located in the annotation file.");
        }

        try (BufferedWriter writer = Files.newBufferedWriter(genePosFile.toPath(), StandardCharsets.UTF_8)) {
            for (GenePosOutputLine outputLine : outputLines) {
                writer.write(outputLine.chromosome);
                writer.write('\t');
                writer.write(outputLine.geneId);
                writer.write('\t');
                writer.write(String.valueOf(outputLine.start));
                writer.write('\t');
                writer.write(String.valueOf(outputLine.end));
                writer.write('\t');
                writer.write(DEFAULT_GENE_POS_COLOR);
                writer.newLine();
            }
        }
    }

    public static void updateGenePosColors(File projectDir, Set<String> highlightedGeneIds, String color)
        throws IOException {
        if (projectDir == null) {
            throw new IllegalArgumentException("Circos project directory cannot be null.");
        }

        File genePosFile = new File(projectDir, GENE_POS_FILE_NAME);
        if (!genePosFile.isFile()) {
            return;
        }

        LinkedHashSet<String> normalizedHighlightedGeneIds = new LinkedHashSet<>();
        if (highlightedGeneIds != null) {
            for (String geneId : highlightedGeneIds) {
                if (geneId == null) {
                    continue;
                }
                String trimmed = geneId.trim();
                if (!trimmed.isEmpty()) {
                    normalizedHighlightedGeneIds.add(trimmed);
                }
            }
        }
        if (normalizedHighlightedGeneIds.isEmpty()) {
            return;
        }

        String resolvedColor = color == null || color.trim().isEmpty() ? HIGHLIGHTED_GENE_POS_COLOR : color.trim();
        List<String> existingLines = Files.readAllLines(genePosFile.toPath(), StandardCharsets.UTF_8);
        List<String> updatedLines = new ArrayList<>(existingLines.size());
        for (String existingLine : existingLines) {
            if (existingLine == null) {
                continue;
            }

            String trimmed = existingLine.trim();
            if (trimmed.isEmpty()) {
                updatedLines.add(existingLine);
                continue;
            }

            String[] fields = existingLine.split("\t", -1);
            if (fields.length < 5) {
                updatedLines.add(existingLine);
                continue;
            }

            String geneId = fields[1] == null ? "" : fields[1].trim();
            if (normalizedHighlightedGeneIds.contains(geneId)) {
                fields[4] = resolvedColor;
                updatedLines.add(String.join("\t", fields));
            } else {
                updatedLines.add(existingLine);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(genePosFile.toPath(), StandardCharsets.UTF_8)) {
            for (String updatedLine : updatedLines) {
                writer.write(updatedLine);
                writer.newLine();
            }
        }
    }

    private static void generateGcTrack(SpeciesInfo species, File outputFile) throws IOException {
        File genomeFile = requireGenomeFile(species);
        logger.info("Generating GC track for Circos: " + genomeFile.getAbsolutePath());

        try (BufferedReader reader = Files.newBufferedReader(genomeFile.toPath(), StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {

            String line;
            String currentChromosome = null;
            RollingGcWindow rollingWindow = null;

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                if (trimmed.startsWith(">")) {
                    if (rollingWindow != null) {
                        rollingWindow.finish(writer);
                    }

                    currentChromosome = trimmed.substring(1).split("\\s+")[0];
                    rollingWindow = new RollingGcWindow(currentChromosome);
                    continue;
                }

                if (rollingWindow == null) {
                    continue;
                }

                for (int i = 0; i < trimmed.length(); i++) {
                    rollingWindow.addBase(trimmed.charAt(i), writer);
                }
            }

            if (rollingWindow != null) {
                rollingWindow.finish(writer);
            }
        }
    }

    private static void generateGeneDensityTrack(SpeciesInfo species, File outputFile) throws IOException {
        File annotationFile = requireAnnotationFile(species);
        List<GenomeData.ChromosomeStat> chromosomeStats = resolveChromosomeStats(species);
        Map<String, OverlapCounter> chromosomeGeneCounters = readGeneRegions(annotationFile);

        logger.info("Generating gene density track for Circos: " + annotationFile.getAbsolutePath());

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
            for (GenomeData.ChromosomeStat chromosomeStat : chromosomeStats) {
                if (chromosomeStat == null || chromosomeStat.getName() == null || chromosomeStat.getName().trim().isEmpty()) {
                    continue;
                }

                OverlapCounter overlapCounter = chromosomeGeneCounters.get(chromosomeStat.getName());
                if (overlapCounter == null) {
                    overlapCounter = OverlapCounter.empty();
                }

                for (long windowStart = 1; windowStart <= chromosomeStat.getSize(); windowStart += STEP_SIZE) {
                    long windowEnd = Math.min(windowStart + WINDOW_SIZE - 1L, chromosomeStat.getSize());
                    int overlappingGeneCount = overlapCounter.countOverlapping(windowStart, windowEnd);
                    writeTrackLine(writer, chromosomeStat.getName(), windowStart, windowEnd,
                        String.valueOf(overlappingGeneCount));
                }
            }
        }
    }

    private static File requireGenomeFile(SpeciesInfo species) throws IOException {
        File genomeFile = species.getGenomeFile();
        if (genomeFile == null || !genomeFile.isFile()) {
            throw new IOException("Genome FASTA file is missing for species: " + species.getSpeciesDirectoryName());
        }
        return genomeFile;
    }

    private static File requireAnnotationFile(SpeciesInfo species) throws IOException {
        File annotationFile = species.getAnnotationFile();
        if (annotationFile == null || !annotationFile.isFile()) {
            throw new IOException("Annotation GFF/GTF file is missing for species: " + species.getSpeciesDirectoryName());
        }
        return annotationFile;
    }

    private static List<GenomeData.ChromosomeStat> resolveChromosomeStats(SpeciesInfo species) throws IOException {
        GenomeData genomeData = species.getGenomeData();
        if (genomeData != null && !genomeData.getChromosomeStats().isEmpty()) {
            return genomeData.getChromosomeStats();
        }

        File genomeFile = requireGenomeFile(species);
        File annotationFile = species.getAnnotationFile();
        GenomeData recalculatedGenomeData = GenomeStatsCalculator.calculateGenomeStats(genomeFile, annotationFile);
        if (recalculatedGenomeData.getChromosomeStats().isEmpty()) {
            throw new IOException("Chromosome statistics are not available for species: "
                + species.getSpeciesDirectoryName());
        }
        species.setGenomeData(recalculatedGenomeData);
        return recalculatedGenomeData.getChromosomeStats();
    }

    private static Map<String, OverlapCounter> readGeneRegions(File annotationFile) throws IOException {
        Map<String, List<Interval>> geneRegionsByChromosome = new LinkedHashMap<>();
        Map<String, MergedInterval> transcriptFallbackRegions = new LinkedHashMap<>();
        boolean hasGeneFeature = false;

        try (BufferedReader reader = Files.newBufferedReader(annotationFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] fields = trimmed.split("\t");
                if (fields.length < 9) {
                    continue;
                }

                String chromosome = fields[0].trim();
                String featureType = fields[2].trim().toLowerCase(Locale.ROOT);
                long start = parseCoordinate(fields[3]);
                long end = parseCoordinate(fields[4]);
                if (chromosome.isEmpty() || start <= 0 || end <= 0) {
                    continue;
                }

                long normalizedStart = Math.min(start, end);
                long normalizedEnd = Math.max(start, end);

                if ("gene".equals(featureType)) {
                    geneRegionsByChromosome
                        .computeIfAbsent(chromosome, ignored -> new ArrayList<>())
                        .add(new Interval(normalizedStart, normalizedEnd));
                    hasGeneFeature = true;
                    continue;
                }

                if (isTranscriptLikeFeature(featureType)) {
                    String geneId = extractGeneId(fields[8]);
                    if (geneId == null || geneId.trim().isEmpty()) {
                        geneId = chromosome + ":" + normalizedStart + "-" + normalizedEnd;
                    }
                    String key = chromosome + '\t' + geneId;
                    MergedInterval mergedInterval = transcriptFallbackRegions.get(key);
                    if (mergedInterval == null) {
                        transcriptFallbackRegions.put(key,
                            new MergedInterval(chromosome, normalizedStart, normalizedEnd));
                    } else {
                        mergedInterval.expand(normalizedStart, normalizedEnd);
                    }
                }
            }
        }

        if (!hasGeneFeature) {
            for (MergedInterval mergedInterval : transcriptFallbackRegions.values()) {
                geneRegionsByChromosome
                    .computeIfAbsent(mergedInterval.chromosome, ignored -> new ArrayList<>())
                    .add(new Interval(mergedInterval.start, mergedInterval.end));
            }
        }

        Map<String, OverlapCounter> overlapCounters = new LinkedHashMap<>();
        for (Map.Entry<String, List<Interval>> entry : geneRegionsByChromosome.entrySet()) {
            overlapCounters.put(entry.getKey(), OverlapCounter.fromIntervals(entry.getValue()));
        }
        return overlapCounters;
    }

    private static Map<String, GenePositionRecord> readGenePositions(File annotationFile) throws IOException {
        Map<String, GenePositionRecord> positionsByAlias = new LinkedHashMap<>();
        Map<String, GenePositionAggregate> geneFeatureRecords = new LinkedHashMap<>();
        Map<String, GenePositionAggregate> transcriptFallbackRecords = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(annotationFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] fields = trimmed.split("\t");
                if (fields.length < 9) {
                    continue;
                }

                String chromosome = fields[0].trim();
                String featureType = fields[2].trim().toLowerCase(Locale.ROOT);
                long start = parseCoordinate(fields[3]);
                long end = parseCoordinate(fields[4]);
                if (chromosome.isEmpty() || start <= 0 || end <= 0) {
                    continue;
                }

                long normalizedStart = Math.min(start, end);
                long normalizedEnd = Math.max(start, end);
                String attributes = fields[8];
                String primaryGeneId = extractGeneId(attributes);
                if (primaryGeneId == null || primaryGeneId.trim().isEmpty()) {
                    primaryGeneId = extractAttributeValue(attributes, "ID");
                }
                if (primaryGeneId == null || primaryGeneId.trim().isEmpty()) {
                    continue;
                }
                primaryGeneId = primaryGeneId.trim();

                LinkedHashSet<String> aliasIds = collectGenePositionAliases(attributes);
                aliasIds.add(primaryGeneId);

                if ("gene".equals(featureType)) {
                    GenePositionAggregate aggregate = geneFeatureRecords.get(primaryGeneId);
                    if (aggregate == null) {
                        aggregate = new GenePositionAggregate(chromosome, normalizedStart, normalizedEnd);
                        geneFeatureRecords.put(primaryGeneId, aggregate);
                    } else {
                        aggregate.expand(normalizedStart, normalizedEnd);
                    }
                    aggregate.addAliases(aliasIds);
                    continue;
                }

                if (isTranscriptLikeFeature(featureType)) {
                    GenePositionAggregate aggregate = transcriptFallbackRecords.get(primaryGeneId);
                    if (aggregate == null) {
                        aggregate = new GenePositionAggregate(chromosome, normalizedStart, normalizedEnd);
                        transcriptFallbackRecords.put(primaryGeneId, aggregate);
                    } else {
                        aggregate.expand(normalizedStart, normalizedEnd);
                    }
                    aggregate.addAliases(aliasIds);
                }
            }
        }

        addGenePositionAliases(positionsByAlias, geneFeatureRecords.values());
        addGenePositionAliases(positionsByAlias, transcriptFallbackRecords.values());
        return positionsByAlias;
    }

    private static boolean isTranscriptLikeFeature(String featureType) {
        return "mrna".equals(featureType)
            || "transcript".equals(featureType)
            || "rna".equals(featureType);
    }

    private static String extractGeneId(String attributes) {
        String geneId = extractAttributeValue(attributes, "gene_id");
        if (geneId != null) {
            return geneId;
        }

        geneId = extractAttributeValue(attributes, "gene");
        if (geneId != null) {
            return geneId;
        }

        geneId = extractAttributeValue(attributes, "Parent");
        if (geneId != null) {
            return geneId;
        }

        return extractAttributeValue(attributes, "ID");
    }

    private static LinkedHashSet<String> collectGenePositionAliases(String attributes) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        addAlias(aliases, extractAttributeValue(attributes, "ID"));
        addAlias(aliases, extractAttributeValue(attributes, "Name"));
        addAlias(aliases, extractAttributeValue(attributes, "gene_id"));
        addAlias(aliases, extractAttributeValue(attributes, "geneID"));
        addAlias(aliases, extractAttributeValue(attributes, "gene"));
        addAlias(aliases, extractAttributeValue(attributes, "Parent"));
        return aliases;
    }

    private static void addAlias(Set<String> aliases, String alias) {
        if (alias == null) {
            return;
        }
        String trimmed = alias.trim();
        if (!trimmed.isEmpty()) {
            aliases.add(trimmed);
        }
    }

    private static void addGenePositionAliases(Map<String, GenePositionRecord> positionsByAlias,
                                               java.util.Collection<GenePositionAggregate> records) {
        for (GenePositionAggregate record : records) {
            GenePositionRecord positionRecord = new GenePositionRecord(
                record.chromosome,
                record.start,
                record.end
            );
            for (String alias : record.aliases) {
                positionsByAlias.putIfAbsent(alias, positionRecord);
            }
        }
    }

    private static String extractAttributeValue(String attributes, String targetKey) {
        if (attributes == null || attributes.trim().isEmpty() || targetKey == null || targetKey.trim().isEmpty()) {
            return null;
        }

        String[] parts = attributes.split(";");
        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }

            if (part.contains("=")) {
                String[] keyValue = part.split("=", 2);
                if (keyValue.length == 2 && targetKey.equalsIgnoreCase(keyValue[0].trim())) {
                    return cleanAttributeValue(keyValue[1]);
                }
                continue;
            }

            int separatorIndex = part.indexOf(' ');
            if (separatorIndex > 0) {
                String key = part.substring(0, separatorIndex).trim();
                if (targetKey.equalsIgnoreCase(key)) {
                    return cleanAttributeValue(part.substring(separatorIndex + 1));
                }
            }
        }

        return null;
    }

    private static String cleanAttributeValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.trim();
    }

    private static long parseCoordinate(String rawValue) {
        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException ex) {
            return -1L;
        }
    }

    private static void writeTrackLine(BufferedWriter writer, String chromosome, long start, long end, String value)
        throws IOException {
        writer.write(chromosome);
        writer.write('\t');
        writer.write(String.valueOf(start));
        writer.write('\t');
        writer.write(String.valueOf(end));
        writer.write('\t');
        writer.write(value);
        writer.newLine();
    }

    private static void deleteIfExists(File targetFile) throws IOException {
        if (targetFile != null) {
            Files.deleteIfExists(targetFile.toPath());
        }
    }

    private static void rewriteTrackInfoFile(File projectDir, List<String> selectedTracks, File gcTrackFile,
                                             File geneDensityTrackFile) throws IOException {
        File trackInfoFile = new File(projectDir, TRACK_INFO_FILE_NAME);
        boolean hasGenePosFile = new File(projectDir, GENE_POS_FILE_NAME).isFile();
        List<String> orderedTracks = new ArrayList<>();
        if (selectedTracks != null) {
            for (String selectedTrack : selectedTracks) {
                if (selectedTrack == null) {
                    continue;
                }
                String trimmed = selectedTrack.trim();
                if (trimmed.isEmpty() || orderedTracks.contains(trimmed)) {
                    continue;
                }
                orderedTracks.add(trimmed);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(trackInfoFile.toPath(), StandardCharsets.UTF_8)) {
            int trackIndex = 0;
            for (String trackName : orderedTracks) {
                TrackDefinition trackDefinition = buildTrackDefinition(trackName, gcTrackFile, geneDensityTrackFile);
                if (trackDefinition == null) {
                    continue;
                }
                if (!trackDefinition.trackFile.isFile()) {
                    continue;
                }

                int trackStartPos = resolveTrackStartPos(trackIndex, hasGenePosFile);
                int trackEndPos = resolveTrackEndPos(trackIndex, hasGenePosFile);
                writeTrackBlock(writer, trackDefinition, trackStartPos, trackEndPos);
                trackIndex++;
            }
        }
    }

    private static int resolveTrackStartPos(int trackIndex, boolean hasGenePosFile) {
        if (trackIndex == 0) {
            return FIRST_TRACK_START_POS;
        }
        return SECOND_TRACK_START_POS;
    }

    private static int resolveTrackEndPos(int trackIndex, boolean hasGenePosFile) {
        if (trackIndex == 0) {
            return FIRST_TRACK_END_POS;
        }
        return SECOND_TRACK_END_POS;
    }

    public static void updateOtherParaLayoutPositions(File projectDir, List<String> selectedTracks) throws IOException {
        if (projectDir == null) {
            throw new IllegalArgumentException("Circos project directory cannot be null.");
        }

        File otherParaFile = new File(projectDir, "OtherPara.tab");
        if (!otherParaFile.isFile()) {
            return;
        }

        boolean hasGenePosFile = new File(projectDir, GENE_POS_FILE_NAME).isFile();
        int trackCount = countSelectedTracks(selectedTracks);
        int chrBarStartPos = DEFAULT_CHR_BAR_START_POS + TRACK_BAND_WIDTH * trackCount;
        int chrBarEndPos = DEFAULT_CHR_BAR_END_POS + TRACK_BAND_WIDTH * trackCount;
        int chrLabelPos = DEFAULT_CHR_LABEL_POS + TRACK_BAND_WIDTH * trackCount;
        int labelStartPos = DEFAULT_LABEL_START_POS;
        int labelEndPos = DEFAULT_LABEL_END_POS;
        if (hasGenePosFile) {
            labelStartPos += TRACK_BAND_WIDTH * trackCount;
            labelEndPos = labelStartPos + LABEL_BAND_WIDTH;
        }

        List<String> lines = Files.readAllLines(otherParaFile.toPath(), StandardCharsets.UTF_8);
        List<String> updatedLines = new ArrayList<>(lines.size());
        boolean foundChrBarStart = false;
        boolean foundChrBarEnd = false;
        boolean foundChrLabelPos = false;
        boolean foundStart = false;
        boolean foundEnd = false;
        for (String line : lines) {
            if (line == null) {
                updatedLines.add("");
                continue;
            }

            if (line.regionMatches(true, 0, "ChrBar StartPos:", 0, "ChrBar StartPos:".length())) {
                updatedLines.add("ChrBar StartPos:\t" + chrBarStartPos);
                foundChrBarStart = true;
            } else if (line.regionMatches(true, 0, "ChrBar EndPos:", 0, "ChrBar EndPos:".length())) {
                updatedLines.add("ChrBar EndPos:\t" + chrBarEndPos);
                foundChrBarEnd = true;
            } else if (line.regionMatches(true, 0, "ChrLabel Pos:", 0, "ChrLabel Pos:".length())) {
                updatedLines.add("ChrLabel Pos:\t" + chrLabelPos);
                foundChrLabelPos = true;
            } else if (line.regionMatches(true, 0, "Label StartPos:", 0, "Label StartPos:".length())) {
                updatedLines.add("Label StartPos:\t" + labelStartPos);
                foundStart = true;
            } else if (line.regionMatches(true, 0, "Label EndPos:", 0, "Label EndPos:".length())) {
                updatedLines.add("Label EndPos:\t" + labelEndPos);
                foundEnd = true;
            } else {
                updatedLines.add(line);
            }
        }

        if (!foundChrBarStart) {
            updatedLines.add("ChrBar StartPos:\t" + chrBarStartPos);
        }
        if (!foundChrBarEnd) {
            updatedLines.add("ChrBar EndPos:\t" + chrBarEndPos);
        }
        if (!foundChrLabelPos) {
            updatedLines.add("ChrLabel Pos:\t" + chrLabelPos);
        }
        if (!foundStart) {
            updatedLines.add("Label StartPos:\t" + labelStartPos);
        }
        if (!foundEnd) {
            updatedLines.add("Label EndPos:\t" + labelEndPos);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(otherParaFile.toPath(), StandardCharsets.UTF_8)) {
            for (String updatedLine : updatedLines) {
                writer.write(updatedLine);
                writer.newLine();
            }
        }
    }

    private static int countSelectedTracks(List<String> selectedTracks) {
        LinkedHashSet<String> normalizedTracks = new LinkedHashSet<>();
        if (selectedTracks != null) {
            for (String selectedTrack : selectedTracks) {
                if (selectedTrack == null) {
                    continue;
                }
                String trimmed = selectedTrack.trim();
                if (!trimmed.isEmpty()
                    && (GC_TRACK_OPTION.equals(trimmed) || GENE_DENSITY_TRACK_OPTION.equals(trimmed))) {
                    normalizedTracks.add(trimmed);
                }
            }
        }
        return normalizedTracks.size();
    }

    private static TrackDefinition buildTrackDefinition(String trackName, File gcTrackFile, File geneDensityTrackFile) {
        if (GC_TRACK_OPTION.equals(trackName)) {
            return new TrackDefinition(
                gcTrackFile,
                "Line",
                "255,0,0",
                "255,200,0",
                "255,255,0"
            );
        }

        if (GENE_DENSITY_TRACK_OPTION.equals(trackName)) {
            return new TrackDefinition(
                geneDensityTrackFile,
                "HeatMap",
                "56,108,176",
                "166,206,227",
                "255,255,255"
            );
        }

        return null;
    }

    private static void writeTrackBlock(BufferedWriter writer, TrackDefinition trackDefinition,
                                        int trackStartPos, int trackEndPos) throws IOException {
        writeTrackInfoLine(writer, "Track File:", trackDefinition.trackFile.getAbsolutePath());
        writeTrackInfoLine(writer, "Track Type:", trackDefinition.trackType);
        writeTrackInfoLine(writer, "Track StartPos:", String.valueOf(trackStartPos));
        writeTrackInfoLine(writer, "Track EndPos:", String.valueOf(trackEndPos));
        writeTrackInfoLine(writer, "Track BinMode:", "None");
        writeTrackInfoLine(writer, "Track BinSize:", "100000");
        writeTrackInfoLine(writer, "Track OneColor:", trackDefinition.oneColor);
        writeTrackInfoLine(writer, "Track TwoColor:", trackDefinition.twoColor);
        writeTrackInfoLine(writer, "Track ThreeColor:", trackDefinition.threeColor);
        writeTrackInfoLine(writer, "Track FixMax:", "NaN");
        writeTrackInfoLine(writer, "Track FixMid:", "NaN");
        writeTrackInfoLine(writer, "Track FixMin:", "NaN");
        writeTrackInfoLine(writer, "Track BorderColor:", "0,0,0");
        writeTrackInfoLine(writer, "Track ColorByChr:", "false");
        writeTrackInfoLine(writer, "Track ElementStroke:", "1.0");
        writeTrackInfoLine(writer, "Track Sep Line Value:", "NaN");
    }

    private static void writeTrackInfoLine(BufferedWriter writer, String key, String value) throws IOException {
        writer.write(key);
        writer.write('\t');
        writer.write(value != null ? value : "");
        writer.newLine();
    }

    private static final class RollingGcWindow {
        private final String chromosome;
        private final byte[] buffer;
        private int head;
        private int size;
        private long position;
        private long gcCount;
        private long validBaseCount;
        private long lastFullWindowStart;

        private RollingGcWindow(String chromosome) {
            this.chromosome = chromosome;
            this.buffer = new byte[WINDOW_SIZE];
        }

        private void addBase(char rawBase, BufferedWriter writer) throws IOException {
            byte baseCode = classifyBase(rawBase);

            if (size == WINDOW_SIZE) {
                byte removedBase = buffer[head];
                gcCount -= gcContribution(removedBase);
                validBaseCount -= validBaseContribution(removedBase);
                buffer[head] = baseCode;
                head = (head + 1) % WINDOW_SIZE;
            } else {
                int insertIndex = (head + size) % WINDOW_SIZE;
                buffer[insertIndex] = baseCode;
                size++;
            }

            gcCount += gcContribution(baseCode);
            validBaseCount += validBaseContribution(baseCode);
            position++;

            if (size == WINDOW_SIZE && (position - WINDOW_SIZE) % STEP_SIZE == 0) {
                long windowStart = position - WINDOW_SIZE + 1;
                lastFullWindowStart = windowStart;
                writeTrackLine(writer, chromosome, windowStart, position, formatGcValue(gcCount, validBaseCount));
            }
        }

        private void finish(BufferedWriter writer) throws IOException {
            if (position == 0) {
                return;
            }

            long nextWindowStart = lastFullWindowStart > 0 ? lastFullWindowStart + STEP_SIZE : 1;
            if (nextWindowStart > position) {
                return;
            }

            byte[] orderedWindow = new byte[size];
            for (int i = 0; i < size; i++) {
                orderedWindow[i] = buffer[(head + i) % WINDOW_SIZE];
            }

            int[] gcPrefix = new int[size + 1];
            int[] validPrefix = new int[size + 1];
            for (int i = 0; i < size; i++) {
                gcPrefix[i + 1] = gcPrefix[i] + gcContribution(orderedWindow[i]);
                validPrefix[i + 1] = validPrefix[i] + validBaseContribution(orderedWindow[i]);
            }

            long bufferStart = position - size + 1;
            for (long windowStart = nextWindowStart; windowStart <= position; windowStart += STEP_SIZE) {
                int startIndex = (int) (windowStart - bufferStart);
                if (startIndex < 0 || startIndex >= size) {
                    continue;
                }

                long trailingGcCount = gcPrefix[size] - gcPrefix[startIndex];
                long trailingValidBaseCount = validPrefix[size] - validPrefix[startIndex];
                writeTrackLine(writer, chromosome, windowStart, position,
                    formatGcValue(trailingGcCount, trailingValidBaseCount));
            }
        }

        private static byte classifyBase(char rawBase) {
            char base = Character.toUpperCase(rawBase);
            if (base == 'G' || base == 'C') {
                return 2;
            }
            if (base == 'A' || base == 'T') {
                return 1;
            }
            return 0;
        }

        private static int gcContribution(byte baseCode) {
            return baseCode == 2 ? 1 : 0;
        }

        private static int validBaseContribution(byte baseCode) {
            return baseCode == 0 ? 0 : 1;
        }

        private static String formatGcValue(long gcCount, long validBaseCount) {
            double gcValue = validBaseCount > 0 ? gcCount * 100.0 / validBaseCount : 0.0;
            return String.format(Locale.US, "%.4f", gcValue);
        }
    }

    private static final class Interval {
        private final long start;
        private final long end;

        private Interval(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class MergedInterval {
        private final String chromosome;
        private long start;
        private long end;

        private MergedInterval(String chromosome, long start, long end) {
            this.chromosome = chromosome;
            this.start = start;
            this.end = end;
        }

        private void expand(long newStart, long newEnd) {
            start = Math.min(start, newStart);
            end = Math.max(end, newEnd);
        }
    }

    private static final class OverlapCounter {
        private final long[] starts;
        private final long[] ends;

        private OverlapCounter(long[] starts, long[] ends) {
            this.starts = starts;
            this.ends = ends;
        }

        private static OverlapCounter fromIntervals(List<Interval> intervals) {
            if (intervals == null || intervals.isEmpty()) {
                return empty();
            }

            long[] starts = new long[intervals.size()];
            long[] ends = new long[intervals.size()];
            for (int i = 0; i < intervals.size(); i++) {
                Interval interval = intervals.get(i);
                starts[i] = interval.start;
                ends[i] = interval.end;
            }

            Arrays.sort(starts);
            Arrays.sort(ends);
            return new OverlapCounter(starts, ends);
        }

        private static OverlapCounter empty() {
            return new OverlapCounter(new long[0], new long[0]);
        }

        private int countOverlapping(long windowStart, long windowEnd) {
            int startedBeforeWindowEnd = upperBound(starts, windowEnd);
            int endedBeforeWindowStart = lowerBoundGreaterThanOrEqual(ends, windowStart);
            return Math.max(0, startedBeforeWindowEnd - endedBeforeWindowStart);
        }

        private static int upperBound(long[] values, long target) {
            int left = 0;
            int right = values.length;
            while (left < right) {
                int middle = (left + right) >>> 1;
                if (values[middle] <= target) {
                    left = middle + 1;
                } else {
                    right = middle;
                }
            }
            return left;
        }

        private static int lowerBoundGreaterThanOrEqual(long[] values, long target) {
            int left = 0;
            int right = values.length;
            while (left < right) {
                int middle = (left + right) >>> 1;
                if (values[middle] < target) {
                    left = middle + 1;
                } else {
                    right = middle;
                }
            }
            return left;
        }
    }

    private static final class TrackDefinition {
        private final File trackFile;
        private final String trackType;
        private final String oneColor;
        private final String twoColor;
        private final String threeColor;

        private TrackDefinition(File trackFile, String trackType, String oneColor, String twoColor,
                                String threeColor) {
            this.trackFile = trackFile;
            this.trackType = trackType;
            this.oneColor = oneColor;
            this.twoColor = twoColor;
            this.threeColor = threeColor;
        }
    }

    private static final class GenePositionAggregate {
        private final String chromosome;
        private long start;
        private long end;
        private final LinkedHashSet<String> aliases;

        private GenePositionAggregate(String chromosome, long start, long end) {
            this.chromosome = chromosome;
            this.start = start;
            this.end = end;
            this.aliases = new LinkedHashSet<>();
        }

        private void expand(long newStart, long newEnd) {
            start = Math.min(start, newStart);
            end = Math.max(end, newEnd);
        }

        private void addAliases(Set<String> newAliases) {
            aliases.addAll(newAliases);
        }
    }

    private static final class GenePositionRecord {
        private final String chromosome;
        private final long start;
        private final long end;

        private GenePositionRecord(String chromosome, long start, long end) {
            this.chromosome = chromosome;
            this.start = start;
            this.end = end;
        }
    }

    private static final class GenePosOutputLine {
        private final String chromosome;
        private final String geneId;
        private final long start;
        private final long end;

        private GenePosOutputLine(String chromosome, String geneId, long start, long end) {
            this.chromosome = chromosome;
            this.geneId = geneId;
            this.start = start;
            this.end = end;
        }
    }
}
