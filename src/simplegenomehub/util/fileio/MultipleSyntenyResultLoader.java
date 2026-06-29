package simplegenomehub.util.fileio;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads a Multiple Synteny text bundle for result viewing.
 */
public final class MultipleSyntenyResultLoader {

    private static final String GENOME_INFO_NAME = "Genome.info.txt";
    private static final String LINK_INFO_NAME = "Link.info.txt";
    private static final String HIGHLIGHT_INFO_NAME = "Highlight.info.txt";
    private static final String HIGHLIGHT_LINK_INFO_NAME = "Highlight.Link.info.txt";
    private static final String RENDER_SETTINGS_NAME = "Render.settings.txt";

    private MultipleSyntenyResultLoader() {
    }

    public static ResultScene load(File resultDir) throws IOException {
        if (resultDir == null || !resultDir.isDirectory()) {
            throw new IOException("Result directory does not exist: "
                + (resultDir == null ? "" : resultDir.getAbsolutePath()));
        }

        File genomeInfoFile = requireFile(resultDir, GENOME_INFO_NAME);
        File linkInfoFile = requireFile(resultDir, LINK_INFO_NAME);
        File highlightInfoFile = new File(resultDir, HIGHLIGHT_INFO_NAME);
        File highlightLinkInfoFile = new File(resultDir, HIGHLIGHT_LINK_INFO_NAME);
        File renderSettingsFile = requireFile(resultDir, RENDER_SETTINGS_NAME);

        List<String> warnings = new ArrayList<>();
        RenderSettings settings = readRenderSettings(renderSettingsFile, warnings);
        LinkedHashMap<String, GenomeInfo> genomeById = readGenomeInfo(genomeInfoFile, warnings);
        List<HighlightGeneInfo> highlightGenes = readHighlightInfo(highlightInfoFile, settings, warnings);
        Map<String, HighlightLinkStyle> highlightStyles =
            readHighlightLinkInfo(highlightLinkInfoFile, settings, warnings);
        List<LinkInfo> links = readLinkInfo(linkInfoFile, settings, highlightStyles, warnings);

        if (genomeById.isEmpty()) {
            throw new IOException("Genome.info.txt does not contain any genome entries.");
        }

        List<GenomeInfo> genomes = new ArrayList<>(genomeById.values());
        genomes.sort(Comparator.comparingInt(GenomeInfo::getZOrder));
        for (GenomeInfo genome : genomes) {
            genome.sortChromosomes();
        }
        links.sort(Comparator.comparingInt(LinkInfo::getZOrder));

        return new ResultScene(
            resultDir,
            settings,
            genomes,
            links,
            highlightGenes,
            new ArrayList<>(warnings)
        );
    }

    private static File requireFile(File resultDir, String fileName) throws IOException {
        File file = new File(resultDir, fileName);
        if (!file.isFile()) {
            throw new IOException("Missing required result file: " + file.getAbsolutePath());
        }
        return file;
    }

    private static RenderSettings readRenderSettings(File renderSettingsFile,
                                                     List<String> warnings) throws IOException {
        int canvasWidth = 1500;
        int canvasHeight = 1000;
        String coordOrigin = "top_left";
        String rectAnchor = "left_bottom";
        String rotationPositive = "clockwise";
        String bpCoordinateMode = "1_based_inclusive";
        int defaultChrGap = 3;
        Color defaultLinkColor = parseColor(MultipleSyntenyService.DEFAULT_LINK_COLOR, Color.LIGHT_GRAY);
        double defaultLinkAlpha = MultipleSyntenyService.DEFAULT_LINK_ALPHA;
        Color defaultHighlightLinkColor = parseColor(MultipleSyntenyService.DEFAULT_HIGHLIGHT_COLOR, new Color(252, 141, 98));
        Color defaultHighlightGeneColor = parseColor(MultipleSyntenyService.DEFAULT_HIGHLIGHT_COLOR, new Color(252, 141, 98));
        int defaultBendValue = 50;
        Color defaultRectBorderColor = new Color(150, 164, 184);
        Color defaultChrFillColor = new Color(218, 235, 255);

        try (BufferedReader reader = Files.newBufferedReader(renderSettingsFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }

                int separatorIndex = trimmed.indexOf('=');
                String key = trimmed.substring(0, separatorIndex).trim();
                String value = trimmed.substring(separatorIndex + 1).trim();

                if ("canvas_width".equalsIgnoreCase(key)) {
                    canvasWidth = parseInt(value, canvasWidth);
                } else if ("canvas_height".equalsIgnoreCase(key)) {
                    canvasHeight = parseInt(value, canvasHeight);
                } else if ("coord_origin".equalsIgnoreCase(key)) {
                    coordOrigin = value;
                } else if ("rect_anchor".equalsIgnoreCase(key)) {
                    rectAnchor = value;
                } else if ("rotation_positive".equalsIgnoreCase(key)) {
                    rotationPositive = value;
                } else if ("bp_coordinate_mode".equalsIgnoreCase(key)) {
                    bpCoordinateMode = value;
                } else if ("default_chr_gap".equalsIgnoreCase(key)) {
                    defaultChrGap = parseInt(value, defaultChrGap);
                } else if ("default_link_color".equalsIgnoreCase(key)) {
                    defaultLinkColor = parseColor(value, defaultLinkColor);
                } else if ("default_link_alpha".equalsIgnoreCase(key)) {
                    defaultLinkAlpha = parseDouble(value, defaultLinkAlpha);
                } else if ("default_highlight_link_color".equalsIgnoreCase(key)) {
                    defaultHighlightLinkColor = parseColor(value, defaultHighlightLinkColor);
                } else if ("default_highlight_gene_color".equalsIgnoreCase(key)) {
                    defaultHighlightGeneColor = parseColor(value, defaultHighlightGeneColor);
                } else if ("default_bend_value".equalsIgnoreCase(key)) {
                    defaultBendValue = parseInt(value, defaultBendValue);
                } else if ("default_rect_border_color".equalsIgnoreCase(key)) {
                    defaultRectBorderColor = parseColor(value, defaultRectBorderColor);
                } else if ("default_chr_fill_color".equalsIgnoreCase(key)) {
                    defaultChrFillColor = parseColor(value, defaultChrFillColor);
                }
            }
        }

        if (!"top_left".equalsIgnoreCase(coordOrigin)) {
            addWarning(warnings, "Only coord_origin=top_left is currently rendered explicitly. Found: " + coordOrigin);
        }
        if (!"left_bottom".equalsIgnoreCase(rectAnchor)) {
            addWarning(warnings, "Only rect_anchor=left_bottom is currently rendered explicitly. Found: " + rectAnchor);
        }
        if (!"clockwise".equalsIgnoreCase(rotationPositive)) {
            addWarning(warnings, "Only rotation_positive=clockwise is currently rendered explicitly. Found: " + rotationPositive);
        }

        return new RenderSettings(
            Math.max(1, canvasWidth),
            Math.max(1, canvasHeight),
            coordOrigin,
            rectAnchor,
            rotationPositive,
            bpCoordinateMode,
            Math.max(0, defaultChrGap),
            defaultLinkColor,
            defaultLinkAlpha,
            defaultHighlightLinkColor,
            defaultHighlightGeneColor,
            Math.max(0, defaultBendValue),
            defaultRectBorderColor,
            defaultChrFillColor
        );
    }

    private static LinkedHashMap<String, GenomeInfo> readGenomeInfo(File genomeInfoFile,
                                                                    List<String> warnings) throws IOException {
        LinkedHashMap<String, GenomeInfo> genomeById = new LinkedHashMap<>();
        String section = "";

        try (BufferedReader reader = Files.newBufferedReader(genomeInfoFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if ("[Genome]".equalsIgnoreCase(trimmed)) {
                    section = "genome";
                    continue;
                }
                if ("[Chromosome]".equalsIgnoreCase(trimmed)) {
                    section = "chromosome";
                    continue;
                }
                if (trimmed.startsWith("genome_id\t")) {
                    continue;
                }

                String[] columns = line.split("\t");
                if ("genome".equals(section)) {
                    if (columns.length < 8) {
                        addWarning(warnings, "Skipped malformed [Genome] row: " + line);
                        continue;
                    }
                    String genomeId = columns[0].trim();
                    if (genomeId.isEmpty()) {
                        addWarning(warnings, "Skipped [Genome] row with empty genome_id.");
                        continue;
                    }
                    genomeById.put(genomeId, new GenomeInfo(
                        genomeId,
                        columns[1].trim(),
                        parseZOrder(columns[2]),
                        parseDouble(columns[3], 0.0d),
                        parseDouble(columns[4], 0.0d),
                        Math.max(1, parseInt(columns[5], 1)),
                        Math.max(1, parseInt(columns[6], 1)),
                        parseInt(columns[7], 0)
                    ));
                } else if ("chromosome".equals(section)) {
                    if (columns.length < 6) {
                        addWarning(warnings, "Skipped malformed [Chromosome] row: " + line);
                        continue;
                    }
                    GenomeInfo genomeInfo = genomeById.get(columns[0].trim());
                    if (genomeInfo == null) {
                        addWarning(warnings, "Skipped chromosome row for unknown genome_id: " + columns[0].trim());
                        continue;
                    }
                    genomeInfo.addChromosome(new ChromosomeInfo(
                        columns[0].trim(),
                        Math.max(1, parseInt(columns[1], 1)),
                        columns[2].trim(),
                        parseDouble(columns[3], 0.0d),
                        parseDouble(columns[4], 0.0d),
                        Math.max(1L, parseLong(columns[5], 1L))
                    ));
                }
            }
        }

        return genomeById;
    }

    private static List<HighlightGeneInfo> readHighlightInfo(File highlightInfoFile,
                                                             RenderSettings settings,
                                                             List<String> warnings) throws IOException {
        if (!highlightInfoFile.isFile()) {
            return new ArrayList<>();
        }

        List<HighlightGeneInfo> highlights = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(highlightInfoFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("gene_id\t")) {
                    continue;
                }

                String[] columns = line.split("\t");
                if (columns.length < 5) {
                    addWarning(warnings, "Skipped malformed Highlight.info row: " + line);
                    continue;
                }

                Color color = settings.getDefaultHighlightGeneColor();
                if (columns.length >= 6 && hasText(columns[5])) {
                    color = parseColor(columns[5], color);
                }
                String label = columns.length >= 7 ? columns[6].trim() : "";

                highlights.add(new HighlightGeneInfo(
                    columns[0].trim(),
                    columns[1].trim(),
                    columns[2].trim(),
                    parseLong(columns[3], 0L),
                    parseLong(columns[4], 0L),
                    color,
                    label
                ));
            }
        }
        return highlights;
    }

    private static Map<String, HighlightLinkStyle> readHighlightLinkInfo(File highlightLinkInfoFile,
                                                                         RenderSettings settings,
                                                                         List<String> warnings) throws IOException {
        if (!highlightLinkInfoFile.isFile()) {
            return Collections.emptyMap();
        }

        LinkedHashMap<String, HighlightLinkStyle> styles = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(highlightLinkInfoFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("genome1_id\t")) {
                    continue;
                }

                String[] columns = line.split("\t");
                if (columns.length < 8) {
                    addWarning(warnings, "Skipped malformed Highlight.Link.info row: " + line);
                    continue;
                }

                Color color = settings.getDefaultHighlightLinkColor();
                double alpha = 0.95d;
                int zOrder = 10;
                if (columns.length >= 9 && hasText(columns[8])) {
                    color = parseColor(columns[8], color);
                }
                if (columns.length >= 10 && hasText(columns[9])) {
                    alpha = parseDouble(columns[9], alpha);
                }
                if (columns.length >= 11 && hasText(columns[10])) {
                    zOrder = parseInt(columns[10], zOrder);
                }

                styles.put(buildLinkKey(columns), new HighlightLinkStyle(color, alpha, zOrder));
            }
        }

        return styles;
    }

    private static List<LinkInfo> readLinkInfo(File linkInfoFile,
                                               RenderSettings settings,
                                               Map<String, HighlightLinkStyle> highlightStyles,
                                               List<String> warnings) throws IOException {
        List<LinkInfo> links = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(linkInfoFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("genome1_id\t")) {
                    continue;
                }

                String[] columns = line.split("\t");
                if (columns.length < 8) {
                    addWarning(warnings, "Skipped malformed Link.info row: " + line);
                    continue;
                }

                Color color = settings.getDefaultLinkColor();
                double alpha = settings.getDefaultLinkAlpha();
                int zOrder = 1;

                if (columns.length >= 14 && hasText(columns[13])) {
                    color = parseColor(columns[13], color);
                }
                if (columns.length >= 15 && hasText(columns[14])) {
                    alpha = parseDouble(columns[14], alpha);
                }
                if (columns.length >= 16 && hasText(columns[15])) {
                    zOrder = parseInt(columns[15], zOrder);
                }

                HighlightLinkStyle highlightStyle = highlightStyles.get(buildLinkKey(columns));
                if (highlightStyle != null) {
                    color = highlightStyle.color;
                    alpha = highlightStyle.alpha;
                    zOrder = highlightStyle.zOrder;
                }

                links.add(new LinkInfo(
                    columns[0].trim(),
                    columns[1].trim(),
                    parseLong(columns[2], 0L),
                    parseLong(columns[3], 0L),
                    columns[4].trim(),
                    columns[5].trim(),
                    parseLong(columns[6], 0L),
                    parseLong(columns[7], 0L),
                    columns.length >= 9 && hasText(columns[8]) ? columns[8].trim().toLowerCase(Locale.ROOT) : "double_arc",
                    columns.length >= 10 && hasText(columns[9]) ? columns[9].trim().toLowerCase(Locale.ROOT) : "top",
                    columns.length >= 11 && hasText(columns[10]) ? columns[10].trim().toLowerCase(Locale.ROOT) : "top",
                    columns.length >= 12 && hasText(columns[11]) ? parseInt(columns[11], settings.getDefaultBendValue())
                        : settings.getDefaultBendValue(),
                    columns.length >= 13 && hasText(columns[12]) ? columns[12].trim().toLowerCase(Locale.ROOT) : "auto",
                    color,
                    alpha,
                    zOrder
                ));
            }
        }
        return links;
    }

    private static String buildLinkKey(String[] columns) {
        StringBuilder keyBuilder = new StringBuilder();
        for (int i = 0; i < 8 && i < columns.length; i++) {
            if (i > 0) {
                keyBuilder.append('\t');
            }
            keyBuilder.append(columns[i].trim());
        }
        return keyBuilder.toString();
    }

    private static int parseZOrder(String rawValue) {
        if (!hasText(rawValue)) {
            return 1;
        }
        String trimmed = rawValue.trim();
        if (trimmed.startsWith("z") || trimmed.startsWith("Z")) {
            trimmed = trimmed.substring(1);
        }
        return Math.max(1, parseInt(trimmed, 1));
    }

    private static int parseInt(String rawValue, int defaultValue) {
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static long parseLong(String rawValue, long defaultValue) {
        try {
            return Long.parseLong(rawValue.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static double parseDouble(String rawValue, double defaultValue) {
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static Color parseColor(String rawValue, Color defaultColor) {
        if (!hasText(rawValue) || ".".equals(rawValue.trim())) {
            return defaultColor;
        }

        String[] parts = rawValue.trim().split(",");
        if (parts.length != 3) {
            return defaultColor;
        }

        try {
            int red = Math.max(0, Math.min(255, Integer.parseInt(parts[0].trim())));
            int green = Math.max(0, Math.min(255, Integer.parseInt(parts[1].trim())));
            int blue = Math.max(0, Math.min(255, Integer.parseInt(parts[2].trim())));
            return new Color(red, green, blue);
        } catch (Exception ex) {
            return defaultColor;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static void addWarning(List<String> warnings, String warning) {
        if (warnings.size() < 30) {
            warnings.add(warning);
        } else if (warnings.size() == 30) {
            warnings.add("Additional parse warnings were suppressed.");
        }
    }

    public static final class ResultScene {
        private final File resultDir;
        private final RenderSettings renderSettings;
        private final List<GenomeInfo> genomes;
        private final List<LinkInfo> links;
        private final List<HighlightGeneInfo> highlightGenes;
        private final List<String> warnings;

        private ResultScene(File resultDir, RenderSettings renderSettings,
                            List<GenomeInfo> genomes, List<LinkInfo> links,
                            List<HighlightGeneInfo> highlightGenes, List<String> warnings) {
            this.resultDir = resultDir;
            this.renderSettings = renderSettings;
            this.genomes = new ArrayList<>(genomes);
            this.links = new ArrayList<>(links);
            this.highlightGenes = new ArrayList<>(highlightGenes);
            this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
        }

        public File getResultDir() {
            return resultDir;
        }

        public RenderSettings getRenderSettings() {
            return renderSettings;
        }

        public List<GenomeInfo> getGenomes() {
            return new ArrayList<>(genomes);
        }

        public List<LinkInfo> getLinks() {
            return new ArrayList<>(links);
        }

        public List<HighlightGeneInfo> getHighlightGenes() {
            return new ArrayList<>(highlightGenes);
        }

        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
    }

    public static final class RenderSettings {
        private final int canvasWidth;
        private final int canvasHeight;
        private final String coordOrigin;
        private final String rectAnchor;
        private final String rotationPositive;
        private final String bpCoordinateMode;
        private final int defaultChrGap;
        private final Color defaultLinkColor;
        private final double defaultLinkAlpha;
        private final Color defaultHighlightLinkColor;
        private final Color defaultHighlightGeneColor;
        private final int defaultBendValue;
        private final Color defaultRectBorderColor;
        private final Color defaultChrFillColor;

        private RenderSettings(int canvasWidth, int canvasHeight, String coordOrigin,
                               String rectAnchor, String rotationPositive,
                               String bpCoordinateMode, int defaultChrGap,
                               Color defaultLinkColor, double defaultLinkAlpha,
                               Color defaultHighlightLinkColor, Color defaultHighlightGeneColor,
                               int defaultBendValue, Color defaultRectBorderColor,
                               Color defaultChrFillColor) {
            this.canvasWidth = canvasWidth;
            this.canvasHeight = canvasHeight;
            this.coordOrigin = coordOrigin;
            this.rectAnchor = rectAnchor;
            this.rotationPositive = rotationPositive;
            this.bpCoordinateMode = bpCoordinateMode;
            this.defaultChrGap = defaultChrGap;
            this.defaultLinkColor = defaultLinkColor;
            this.defaultLinkAlpha = defaultLinkAlpha;
            this.defaultHighlightLinkColor = defaultHighlightLinkColor;
            this.defaultHighlightGeneColor = defaultHighlightGeneColor;
            this.defaultBendValue = defaultBendValue;
            this.defaultRectBorderColor = defaultRectBorderColor;
            this.defaultChrFillColor = defaultChrFillColor;
        }

        public int getCanvasWidth() {
            return canvasWidth;
        }

        public int getCanvasHeight() {
            return canvasHeight;
        }

        public String getCoordOrigin() {
            return coordOrigin;
        }

        public String getRectAnchor() {
            return rectAnchor;
        }

        public String getRotationPositive() {
            return rotationPositive;
        }

        public String getBpCoordinateMode() {
            return bpCoordinateMode;
        }

        public int getDefaultChrGap() {
            return defaultChrGap;
        }

        public Color getDefaultLinkColor() {
            return defaultLinkColor;
        }

        public double getDefaultLinkAlpha() {
            return defaultLinkAlpha;
        }

        public Color getDefaultHighlightLinkColor() {
            return defaultHighlightLinkColor;
        }

        public Color getDefaultHighlightGeneColor() {
            return defaultHighlightGeneColor;
        }

        public int getDefaultBendValue() {
            return defaultBendValue;
        }

        public Color getDefaultRectBorderColor() {
            return defaultRectBorderColor;
        }

        public Color getDefaultChrFillColor() {
            return defaultChrFillColor;
        }
    }

    public static final class GenomeInfo {
        private final String genomeId;
        private final String displayName;
        private final int zOrder;
        private final double leftBottomX;
        private final double leftBottomY;
        private final int rectWidth;
        private final int rectHeight;
        private final int rotationDeg;
        private final List<ChromosomeInfo> chromosomes;
        private final Map<String, ChromosomeInfo> chromosomeByName;

        private GenomeInfo(String genomeId, String displayName, int zOrder,
                           double leftBottomX, double leftBottomY,
                           int rectWidth, int rectHeight, int rotationDeg) {
            this.genomeId = genomeId;
            this.displayName = displayName;
            this.zOrder = zOrder;
            this.leftBottomX = leftBottomX;
            this.leftBottomY = leftBottomY;
            this.rectWidth = rectWidth;
            this.rectHeight = rectHeight;
            this.rotationDeg = rotationDeg;
            this.chromosomes = new ArrayList<>();
            this.chromosomeByName = new LinkedHashMap<>();
        }

        private void addChromosome(ChromosomeInfo chromosomeInfo) {
            chromosomes.add(chromosomeInfo);
            chromosomeByName.put(chromosomeInfo.getChromosomeName(), chromosomeInfo);
        }

        private void sortChromosomes() {
            chromosomes.sort(Comparator.comparingInt(ChromosomeInfo::getChromosomeOrder));
        }

        public String getGenomeId() {
            return genomeId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getZOrder() {
            return zOrder;
        }

        public double getLeftBottomX() {
            return leftBottomX;
        }

        public double getLeftBottomY() {
            return leftBottomY;
        }

        public int getRectWidth() {
            return rectWidth;
        }

        public int getRectHeight() {
            return rectHeight;
        }

        public int getRotationDeg() {
            return rotationDeg;
        }

        public List<ChromosomeInfo> getChromosomes() {
            return new ArrayList<>(chromosomes);
        }

        public ChromosomeInfo getChromosome(String chromosomeName) {
            return chromosomeByName.get(chromosomeName);
        }
    }

    public static final class ChromosomeInfo {
        private final String genomeId;
        private final int chromosomeOrder;
        private final String chromosomeName;
        private final double displayStart;
        private final double displayEnd;
        private final long bpLength;

        private ChromosomeInfo(String genomeId, int chromosomeOrder, String chromosomeName,
                               double displayStart, double displayEnd, long bpLength) {
            this.genomeId = genomeId;
            this.chromosomeOrder = chromosomeOrder;
            this.chromosomeName = chromosomeName;
            this.displayStart = Math.min(displayStart, displayEnd);
            this.displayEnd = Math.max(displayStart, displayEnd);
            this.bpLength = bpLength;
        }

        public String getGenomeId() {
            return genomeId;
        }

        public int getChromosomeOrder() {
            return chromosomeOrder;
        }

        public String getChromosomeName() {
            return chromosomeName;
        }

        public double getDisplayStart() {
            return displayStart;
        }

        public double getDisplayEnd() {
            return displayEnd;
        }

        public long getBpLength() {
            return bpLength;
        }

        public double getDisplayWidth() {
            return Math.max(1.0d, displayEnd - displayStart);
        }
    }

    public static final class LinkInfo {
        private final String genome1Id;
        private final String chromosome1;
        private final long start1;
        private final long end1;
        private final String genome2Id;
        private final String chromosome2;
        private final long start2;
        private final long end2;
        private final String linkType;
        private final String edge1;
        private final String edge2;
        private final int bendValue;
        private final String bulgeDirection;
        private final Color color;
        private final double alpha;
        private final int zOrder;

        private LinkInfo(String genome1Id, String chromosome1, long start1, long end1,
                         String genome2Id, String chromosome2, long start2, long end2,
                         String linkType, String edge1, String edge2, int bendValue,
                         String bulgeDirection, Color color, double alpha, int zOrder) {
            this.genome1Id = genome1Id;
            this.chromosome1 = chromosome1;
            this.start1 = Math.min(start1, end1);
            this.end1 = Math.max(start1, end1);
            this.genome2Id = genome2Id;
            this.chromosome2 = chromosome2;
            this.start2 = Math.min(start2, end2);
            this.end2 = Math.max(start2, end2);
            this.linkType = linkType;
            this.edge1 = edge1;
            this.edge2 = edge2;
            this.bendValue = bendValue;
            this.bulgeDirection = bulgeDirection;
            this.color = color;
            this.alpha = alpha;
            this.zOrder = zOrder;
        }

        public String getGenome1Id() {
            return genome1Id;
        }

        public String getChromosome1() {
            return chromosome1;
        }

        public long getStart1() {
            return start1;
        }

        public long getEnd1() {
            return end1;
        }

        public String getGenome2Id() {
            return genome2Id;
        }

        public String getChromosome2() {
            return chromosome2;
        }

        public long getStart2() {
            return start2;
        }

        public long getEnd2() {
            return end2;
        }

        public String getLinkType() {
            return linkType;
        }

        public String getEdge1() {
            return edge1;
        }

        public String getEdge2() {
            return edge2;
        }

        public int getBendValue() {
            return bendValue;
        }

        public String getBulgeDirection() {
            return bulgeDirection;
        }

        public Color getColor() {
            return color;
        }

        public double getAlpha() {
            return alpha;
        }

        public int getZOrder() {
            return zOrder;
        }
    }

    public static final class HighlightGeneInfo {
        private final String geneId;
        private final String genomeId;
        private final String chromosomeName;
        private final long start;
        private final long end;
        private final Color color;
        private final String label;

        private HighlightGeneInfo(String geneId, String genomeId, String chromosomeName,
                                  long start, long end, Color color, String label) {
            this.geneId = geneId;
            this.genomeId = genomeId;
            this.chromosomeName = chromosomeName;
            this.start = Math.min(start, end);
            this.end = Math.max(start, end);
            this.color = color;
            this.label = label;
        }

        public String getGeneId() {
            return geneId;
        }

        public String getGenomeId() {
            return genomeId;
        }

        public String getChromosomeName() {
            return chromosomeName;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        public Color getColor() {
            return color;
        }

        public String getLabel() {
            return label;
        }
    }

    private static final class HighlightLinkStyle {
        private final Color color;
        private final double alpha;
        private final int zOrder;

        private HighlightLinkStyle(Color color, double alpha, int zOrder) {
            this.color = color;
            this.alpha = alpha;
            this.zOrder = zOrder;
        }
    }
}
