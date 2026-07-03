package simplegenomehub.util.MultipleSynteny;

import simplegenomehub.model.GenomeData;
import simplegenomehub.model.SpeciesInfo;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MultipleSyntenyGenomeModel {

    private static final int DEFAULT_WIDTH = 500;
    private static final int DEFAULT_HEIGHT = 12;
    private static final int MIN_CONTENT_WIDTH = 24;
    private static final int MIN_CHROMOSOME_WIDTH = 2;

    private static int nextId = 1;

    private final int id;
    private int slotNumber;
    private final SpeciesInfo species;
    private int width;
    private final int height;
    private String chromosomeText;
    private final Map<String, Long> chromosomeLengthByName;
    private List<ChromosomeSegment> chromosomeSegments;
    private MultipleSyntenyAnchorMode anchorMode;
    private double leftBottomX;
    private double leftBottomY;
    private int rotation;

    public MultipleSyntenyGenomeModel(int slotNumber, SpeciesInfo species, List<String> chromosomes,
                                      double initialAnchorX, double initialAnchorY) {
        this.id = nextId++;
        this.slotNumber = slotNumber;
        this.species = species;
        this.width = DEFAULT_WIDTH;
        this.height = DEFAULT_HEIGHT;
        this.chromosomeText = normalizeChromosomeText(
            chromosomes == null ? "" : String.join(System.lineSeparator(), chromosomes)
        );
        this.chromosomeLengthByName = buildChromosomeLengthMap(species);
        this.chromosomeSegments = new ArrayList<>();
        this.anchorMode = MultipleSyntenyAnchorMode.CENTER;
        this.leftBottomX = initialAnchorX - width / 2.0;
        this.leftBottomY = initialAnchorY;
        this.rotation = 0;
    }

    public int getId() {
        return id;
    }

    public int getSlotNumber() {
        return slotNumber;
    }

    public void setSlotNumber(int slotNumber) {
        this.slotNumber = slotNumber;
    }

    public SpeciesInfo getSpecies() {
        return species;
    }

    public String getGenomeTitle() {
        return "Genome " + slotNumber;
    }

    public String getDisplayName() {
        return species.getSpeciesName() + " (" + species.getVersion() + ")";
    }

    public String getChromosomeText() {
        return chromosomeText == null ? "" : chromosomeText;
    }

    public void setChromosomeText(String chromosomeText) {
        this.chromosomeText = normalizeChromosomeText(chromosomeText);
    }

    public List<String> getChromosomeList() {
        return parseChromosomeList(getChromosomeText());
    }

    public long getSelectedGenomeLength() {
        long totalLength = 0L;
        for (ResolvedChromosome chromosome : resolveSelectedChromosomes()) {
            totalLength += Math.max(1L, chromosome.getLength());
        }
        return totalLength;
    }

    public int getSelectedChromosomeCount() {
        return getChromosomeList().size();
    }

    public List<ChromosomeSegment> getChromosomeSegments() {
        return Collections.unmodifiableList(chromosomeSegments);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public double getLeftBottomX() {
        return leftBottomX;
    }

    public double getLeftBottomY() {
        return leftBottomY;
    }

    public MultipleSyntenyAnchorMode getAnchorMode() {
        return anchorMode;
    }

    public void setAnchorMode(MultipleSyntenyAnchorMode anchorMode) {
        if (anchorMode != null) {
            this.anchorMode = anchorMode;
        }
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotationAroundSelectedAnchor(int inputRotation) {
        Point2D.Double fixedPoint = getSelectedAnchorPoint();
        rotation = normalizeAngle(inputRotation);
        Point2D.Double newOffset = rotateVector(getAnchorOffset(anchorMode).x, getAnchorOffset(anchorMode).y);
        leftBottomX = fixedPoint.x - newOffset.x;
        leftBottomY = fixedPoint.y - newOffset.y;
    }

    public void moveSelectedAnchorTo(double x, double y) {
        Point2D.Double current = getSelectedAnchorPoint();
        leftBottomX += x - current.x;
        leftBottomY += y - current.y;
    }

    public Point2D.Double getSelectedAnchorPoint() {
        return getAnchorPoint(anchorMode);
    }

    public Point2D.Double getAnchorPoint(MultipleSyntenyAnchorMode mode) {
        Point2D.Double offset = getAnchorOffset(mode);
        Point2D.Double rotatedOffset = rotateVector(offset.x, offset.y);
        return new Point2D.Double(leftBottomX + rotatedOffset.x, leftBottomY + rotatedOffset.y);
    }

    public Point2D.Double getCenterPoint() {
        Point2D.Double offset = rotateVector(width / 2.0, -height / 2.0);
        return new Point2D.Double(leftBottomX + offset.x, leftBottomY + offset.y);
    }

    public Point2D.Double getTopCenterPoint() {
        Point2D.Double offset = rotateVector(width / 2.0, -height);
        return new Point2D.Double(leftBottomX + offset.x, leftBottomY + offset.y);
    }

    public Point2D.Double getBottomCenterPoint() {
        Point2D.Double offset = rotateVector(width / 2.0, 0);
        return new Point2D.Double(leftBottomX + offset.x, leftBottomY + offset.y);
    }

    public Point2D.Double getLeftBottomPoint() {
        return getAnchorPoint(MultipleSyntenyAnchorMode.LEFT);
    }

    public Point2D.Double getRightBottomPoint() {
        return getAnchorPoint(MultipleSyntenyAnchorMode.RIGHT);
    }

    public Point2D.Double getLeftTopPoint() {
        return getPointFromLeftBottom(0, -height);
    }

    public Point2D.Double getRightTopPoint() {
        return getPointFromLeftBottom(width, -height);
    }

    public Point2D.Double getHorizontalEdgeCenter(MultipleSyntenyHorizontalEdgeType edgeType) {
        if (edgeType == MultipleSyntenyHorizontalEdgeType.TOP) {
            return getTopCenterPoint();
        }
        return getBottomCenterPoint();
    }

    public Point2D.Double getHorizontalEdgeNormal(MultipleSyntenyHorizontalEdgeType edgeType) {
        if (edgeType == MultipleSyntenyHorizontalEdgeType.TOP) {
            return getTopNormal();
        }
        return getBottomNormal();
    }

    public Point2D.Double getTopNormal() {
        return rotateVector(0, -1);
    }

    public Point2D.Double getBottomNormal() {
        return rotateVector(0, 1);
    }

    public Shape getShape() {
        Point2D.Double leftBottom = new Point2D.Double(leftBottomX, leftBottomY);
        Point2D.Double rightBottom = add(leftBottom, rotateVector(width, 0));
        Point2D.Double rightTop = add(rightBottom, rotateVector(0, -height));
        Point2D.Double leftTop = add(leftBottom, rotateVector(0, -height));

        Path2D.Double path = new Path2D.Double();
        path.moveTo(leftBottom.x, leftBottom.y);
        path.lineTo(rightBottom.x, rightBottom.y);
        path.lineTo(rightTop.x, rightTop.y);
        path.lineTo(leftTop.x, leftTop.y);
        path.closePath();
        return path;
    }

    public Rectangle getVisualBounds() {
        return getShape().getBounds();
    }

    public boolean contains(java.awt.Point point) {
        return point != null && getShape().contains(point);
    }

    public Point2D.Double getPointFromLeftBottom(double xOffset, double yOffset) {
        Point2D.Double offset = rotateVector(xOffset, yOffset);
        return new Point2D.Double(leftBottomX + offset.x, leftBottomY + offset.y);
    }

    public List<Point2D.Double> getSnapReferencePoints() {
        List<Point2D.Double> points = new ArrayList<>(5);
        points.add(getLeftBottomPoint());
        points.add(getRightBottomPoint());
        points.add(getLeftTopPoint());
        points.add(getRightTopPoint());
        points.add(getCenterPoint());
        return points;
    }

    public void applyVisualScale(long longestGenomeLength, int targetContentWidth,
                                 int gapLength, boolean equalLength) {
        Point2D.Double fixedPoint = getSelectedAnchorPoint();
        List<ResolvedChromosome> selectedChromosomes = resolveSelectedChromosomes();
        chromosomeSegments = buildChromosomeSegments(
            selectedChromosomes,
            longestGenomeLength,
            targetContentWidth,
            Math.max(0, gapLength),
            equalLength
        );

        if (chromosomeSegments.isEmpty()) {
            width = DEFAULT_WIDTH;
        } else {
            int totalWidth = 0;
            for (ChromosomeSegment chromosomeSegment : chromosomeSegments) {
                totalWidth += chromosomeSegment.getWidth();
            }
            totalWidth += Math.max(0, gapLength) * Math.max(0, chromosomeSegments.size() - 1);
            width = Math.max(1, totalWidth);
        }

        Point2D.Double newOffset = rotateVector(getAnchorOffset(anchorMode).x, getAnchorOffset(anchorMode).y);
        leftBottomX = fixedPoint.x - newOffset.x;
        leftBottomY = fixedPoint.y - newOffset.y;
    }

    private Point2D.Double getAnchorOffset(MultipleSyntenyAnchorMode mode) {
        if (mode == MultipleSyntenyAnchorMode.RIGHT) {
            return new Point2D.Double(width, 0);
        }
        if (mode == MultipleSyntenyAnchorMode.CENTER) {
            return new Point2D.Double(width / 2.0, 0);
        }
        return new Point2D.Double(0, 0);
    }

    private Point2D.Double rotateVector(double x, double y) {
        double radians = Math.toRadians(-rotation);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Point2D.Double(x * cos - y * sin, x * sin + y * cos);
    }

    private Point2D.Double add(Point2D.Double left, Point2D.Double right) {
        return new Point2D.Double(left.x + right.x, left.y + right.y);
    }

    private static String normalizeChromosomeText(String chromosomeText) {
        List<String> chromosomes = parseChromosomeList(chromosomeText);
        return chromosomes.isEmpty() ? "" : String.join(System.lineSeparator(), chromosomes);
    }

    private static List<String> parseChromosomeList(String chromosomeText) {
        LinkedHashSet<String> chromosomes = new LinkedHashSet<>();
        if (chromosomeText == null || chromosomeText.trim().isEmpty()) {
            return new ArrayList<>();
        }
        for (String token : chromosomeText.split("[\\s,;]+")) {
            if (token == null) {
                continue;
            }
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                chromosomes.add(trimmed);
            }
        }
        return new ArrayList<>(chromosomes);
    }

    private int normalizeAngle(int inputAngle) {
        int normalized = inputAngle % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        return normalized;
    }

    private Map<String, Long> buildChromosomeLengthMap(SpeciesInfo species) {
        Map<String, Long> chromosomeLengthMap = new LinkedHashMap<>();
        if (species == null || species.getGenomeData() == null) {
            return chromosomeLengthMap;
        }

        for (GenomeData.ChromosomeStat chromosomeStat : species.getGenomeData().getChromosomeStats()) {
            if (chromosomeStat == null || chromosomeStat.getName() == null) {
                continue;
            }
            String name = chromosomeStat.getName().trim();
            if (name.isEmpty()) {
                continue;
            }
            chromosomeLengthMap.put(name, Math.max(1L, chromosomeStat.getSize()));
        }
        return chromosomeLengthMap;
    }

    private List<ResolvedChromosome> resolveSelectedChromosomes() {
        List<ResolvedChromosome> resolvedChromosomes = new ArrayList<>();
        for (String chromosomeName : getChromosomeList()) {
            resolvedChromosomes.add(new ResolvedChromosome(chromosomeName, resolveChromosomeLength(chromosomeName)));
        }
        return resolvedChromosomes;
    }

    private long resolveChromosomeLength(String chromosomeName) {
        if (chromosomeName == null) {
            return 1L;
        }

        Long exactMatch = chromosomeLengthByName.get(chromosomeName);
        if (exactMatch != null && exactMatch > 0L) {
            return exactMatch;
        }

        String lowerName = chromosomeName.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Long> entry : chromosomeLengthByName.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(lowerName) && entry.getValue() != null
                && entry.getValue() > 0L) {
                return entry.getValue();
            }
        }
        return 1L;
    }

    private List<ChromosomeSegment> buildChromosomeSegments(List<ResolvedChromosome> selectedChromosomes,
                                                            long longestGenomeLength,
                                                            int targetContentWidth,
                                                            int gapLength,
                                                            boolean equalLength) {
        if (selectedChromosomes.isEmpty()) {
            return new ArrayList<>();
        }

        long genomeLength = 0L;
        for (ResolvedChromosome chromosome : selectedChromosomes) {
            genomeLength += Math.max(1L, chromosome.getLength());
        }

        int chromosomeCount = selectedChromosomes.size();
        int safeGap = Math.max(0, gapLength);
        int totalGapWidth = safeGap * Math.max(0, chromosomeCount - 1);
        int minimumContentWidth = chromosomeCount * MIN_CHROMOSOME_WIDTH;
        int baseContentWidth = Math.max(MIN_CONTENT_WIDTH, targetContentWidth);
        int contentWidth;
        if (equalLength) {
            // When equal-length mode is enabled, the target width should represent the
            // full rendered genome span, including inter-chromosome gaps.
            contentWidth = Math.max(MIN_CONTENT_WIDTH, baseContentWidth - totalGapWidth);
        } else if (longestGenomeLength > 0L) {
            contentWidth = (int) Math.round(baseContentWidth * (double) genomeLength / (double) longestGenomeLength);
        } else {
            contentWidth = baseContentWidth;
        }

        contentWidth = Math.max(MIN_CONTENT_WIDTH, contentWidth);
        contentWidth = Math.max(contentWidth, minimumContentWidth);

        List<Integer> chromosomeWidths = allocateChromosomeWidths(selectedChromosomes, contentWidth);
        List<ChromosomeSegment> segments = new ArrayList<>(selectedChromosomes.size());
        int currentX = 0;
        for (int i = 0; i < selectedChromosomes.size(); i++) {
            ResolvedChromosome chromosome = selectedChromosomes.get(i);
            int segmentWidth = chromosomeWidths.get(i);
            segments.add(new ChromosomeSegment(chromosome.getName(), chromosome.getLength(), currentX, segmentWidth));
            currentX += segmentWidth + safeGap;
        }
        return segments;
    }

    private List<Integer> allocateChromosomeWidths(List<ResolvedChromosome> selectedChromosomes, int contentWidth) {
        int chromosomeCount = selectedChromosomes.size();
        int minWidth = Math.max(1, Math.min(MIN_CHROMOSOME_WIDTH, contentWidth / Math.max(1, chromosomeCount)));
        List<Integer> widths = new ArrayList<>(chromosomeCount);
        for (int i = 0; i < chromosomeCount; i++) {
            widths.add(minWidth);
        }

        int remainingWidth = Math.max(0, contentWidth - chromosomeCount * minWidth);
        if (remainingWidth == 0) {
            return widths;
        }

        long totalLength = 0L;
        for (ResolvedChromosome chromosome : selectedChromosomes) {
            totalLength += Math.max(1L, chromosome.getLength());
        }

        if (totalLength <= 0L) {
            distributeEqually(widths, remainingWidth);
            return widths;
        }

        double[] fractions = new double[chromosomeCount];
        int distributedWidth = 0;
        for (int i = 0; i < chromosomeCount; i++) {
            double rawWidth = remainingWidth
                * (double) Math.max(1L, selectedChromosomes.get(i).getLength())
                / (double) totalLength;
            int extraWidth = (int) Math.floor(rawWidth);
            widths.set(i, widths.get(i) + extraWidth);
            distributedWidth += extraWidth;
            fractions[i] = rawWidth - extraWidth;
        }

        int leftoverWidth = remainingWidth - distributedWidth;
        while (leftoverWidth > 0) {
            int bestIndex = 0;
            for (int i = 1; i < fractions.length; i++) {
                if (fractions[i] > fractions[bestIndex]) {
                    bestIndex = i;
                }
            }
            widths.set(bestIndex, widths.get(bestIndex) + 1);
            fractions[bestIndex] = -1.0d;
            leftoverWidth--;
        }
        return widths;
    }

    private void distributeEqually(List<Integer> widths, int extraWidth) {
        if (widths.isEmpty() || extraWidth <= 0) {
            return;
        }
        int index = 0;
        while (extraWidth > 0) {
            widths.set(index, widths.get(index) + 1);
            extraWidth--;
            index = (index + 1) % widths.size();
        }
    }

    private static final class ResolvedChromosome {
        private final String name;
        private final long length;

        private ResolvedChromosome(String name, long length) {
            this.name = name;
            this.length = length;
        }

        private String getName() {
            return name;
        }

        private long getLength() {
            return length;
        }
    }

    public static final class ChromosomeSegment {
        private final String name;
        private final long length;
        private final int startX;
        private final int width;

        public ChromosomeSegment(String name, long length, int startX, int width) {
            this.name = name;
            this.length = length;
            this.startX = startX;
            this.width = width;
        }

        public String getName() {
            return name;
        }

        public long getLength() {
            return length;
        }

        public int getStartX() {
            return startX;
        }

        public int getWidth() {
            return width;
        }
    }
}
