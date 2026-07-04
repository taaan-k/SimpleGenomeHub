package simplegenomehub.gui;

import jigplot.engine.JIGBasePanel;
import jigplot.engine.JIGConstants;
import jigplot.engine.JIGSubPanel;
import jigplot.OtherTools.ColorPalette;
import simplegenomehub.util.fileio.MultipleSyntenyResultLoader;
import simplegenomehub.util.fileio.MultipleSyntenyResultLoader.ChromosomeInfo;
import simplegenomehub.util.fileio.MultipleSyntenyResultLoader.GenomeInfo;
import simplegenomehub.util.fileio.MultipleSyntenyResultLoader.HighlightGeneInfo;
import simplegenomehub.util.fileio.MultipleSyntenyResultLoader.LinkInfo;
import simplegenomehub.util.fileio.MultipleSyntenyResultLoader.RenderSettings;
import simplegenomehub.util.fileio.MultipleSyntenyResultLoader.ResultScene;
import simplegenomehub.util.fileio.MultipleSyntenyPreviewImageRenderer;
import simplegenomehub.util.fileio.MultipleSyntenyService;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Displays a rendered Multiple Synteny result bundle and reuses JIGBasePanel exports.
 */
public class MultipleSyntenyResultViewerDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(MultipleSyntenyResultViewerDialog.class.getName());
    private static final String PREVIEW_FILE_NAME = "preview.png";
    private static final double DEFAULT_ZOOM_FACTOR = 1.0d;
    private static final double[] ZOOM_LEVELS = {
        0.25d, 0.5d, 0.75d, 1.0d, 1.5d, 2.0d, 3.0d, 4.0d, 5.0d, 6.0d, 8.0d, 10.0d
    };

    private final File resultDir;
    private final ResultScene scene;
    private MultipleSyntenyResultGraphPanel graphPanel;
    private BufferedImage previewImage;
    private ZoomableGraphView zoomView;
    private JScrollPane graphScrollPane;
    private JLabel zoomLabel;

    public MultipleSyntenyResultViewerDialog(Window parent, File resultDir) throws IOException {
        super(parent, "Multiple Synteny Result Viewer", ModalityType.MODELESS);

        this.resultDir = resultDir;
        this.scene = MultipleSyntenyResultLoader.load(resultDir);

        initializeDialog();
        setupLayout();
    }

    public static BufferedImage renderPreviewImage(File resultDir) throws IOException {
        return renderPreviewImage(MultipleSyntenyResultLoader.load(resultDir));
    }

    public static File exportPreviewImage(File resultDir) throws IOException {
        return exportPreviewImage(resultDir, renderPreviewImage(resultDir));
    }

    static BufferedImage renderPreviewImage(ResultScene scene) {
        return MultipleSyntenyPreviewImageRenderer.render(new MultipleSyntenyResultGraphPanel(scene));
    }

    static File exportPreviewImage(File resultDir, BufferedImage previewImage) throws IOException {
        if (resultDir == null || !resultDir.isDirectory()) {
            throw new IllegalArgumentException("Multiple Synteny result directory is invalid.");
        }
        if (previewImage == null) {
            throw new IllegalArgumentException("Multiple Synteny preview image is required.");
        }

        File previewFile = getPreviewFile(resultDir);
        ImageIO.write(previewImage, "png", previewFile);
        return previewFile;
    }

    static File getPreviewFile(File resultDir) {
        return new File(resultDir, PREVIEW_FILE_NAME);
    }

    private void initializeDialog() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(true);

        int width = Math.max(980, Math.min(1600, scene.getRenderSettings().getCanvasWidth() + 220));
        int height = Math.max(720, Math.min(1000, scene.getRenderSettings().getCanvasHeight() + 220));
        setSize(width, height);
        setLocationRelativeTo(getOwner());
    }

    private void setupLayout() {
        setLayout(new BorderLayout(0, 10));
        add(createHeaderPanel(), BorderLayout.NORTH);

        graphPanel = new MultipleSyntenyResultGraphPanel(scene);
        previewImage = renderPreviewImage(scene);
        try {
            exportPreviewImage(resultDir, previewImage);
        } catch (IOException previewEx) {
            logger.log(Level.WARNING,
                "Failed to export Multiple Synteny preview image for " + resultDir.getAbsolutePath(),
                previewEx
            );
        }
        zoomView = new ZoomableGraphView(previewImage, DEFAULT_ZOOM_FACTOR);
        graphScrollPane = new JScrollPane(zoomView);
        graphScrollPane.setWheelScrollingEnabled(false);
        graphScrollPane.getVerticalScrollBar().setUnitIncrement(32);
        graphScrollPane.getHorizontalScrollBar().setUnitIncrement(32);
        zoomView.addMouseWheelListener(this::handleGraphMouseWheelZoom);
        graphScrollPane.getViewport().addMouseWheelListener(this::handleGraphMouseWheelZoom);
        add(graphScrollPane, BorderLayout.CENTER);

        add(createBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setBorder(new EmptyBorder(12, 12, 0, 12));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Rendered From: " + resultDir.getAbsolutePath());
        titleLabel.setFont(SimpleGenomeHubStyle.FONT_SANS_BOLD_12);
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(6));

        String summary = String.format(Locale.US,
            "Genomes: %d | Links: %d | Highlight Genes: %d | Canvas: %d x %d",
            scene.getGenomes().size(),
            scene.getLinks().size(),
            scene.getHighlightGenes().size(),
            scene.getRenderSettings().getCanvasWidth(),
            scene.getRenderSettings().getCanvasHeight()
        );
        JLabel summaryLabel = new JLabel(summary);
        summaryLabel.setForeground(new Color(97, 111, 133));
        headerPanel.add(summaryLabel);

        if (!scene.getWarnings().isEmpty()) {
            headerPanel.add(Box.createVerticalStrut(8));
            JTextArea warningArea = new JTextArea();
            warningArea.setEditable(false);
            warningArea.setFont(SimpleGenomeHubStyle.FONT_MONOSPACED_PLAIN_12);
            warningArea.setRows(Math.min(4, scene.getWarnings().size()));
            warningArea.setLineWrap(true);
            warningArea.setWrapStyleWord(true);
            warningArea.setBackground(new Color(255, 248, 230));
            warningArea.setBorder(BorderFactory.createTitledBorder("Parse Notes"));
            warningArea.setText(String.join(System.lineSeparator(), scene.getWarnings()));
            headerPanel.add(warningArea);
        }

        return headerPanel;
    }

    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.setBorder(new EmptyBorder(0, 12, 12, 12));

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        infoPanel.setOpaque(false);
        JLabel noteLabel = new JLabel("Export uses TBtools/JIGplot save routines.");
        noteLabel.setForeground(new Color(104, 119, 142));
        infoPanel.add(noteLabel);
        bottomPanel.add(infoPanel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(createZoomControlPanel());
        buttonPanel.add(graphPanel.getSaveGraphButton());

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);

        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        return bottomPanel;
    }

    private JPanel createZoomControlPanel() {
        JPanel zoomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        zoomPanel.setOpaque(false);

        JButton zoomOutButton = new JButton("-");
        zoomOutButton.addActionListener(e -> stepZoom(-1));
        zoomPanel.add(zoomOutButton);

        zoomLabel = new JLabel(formatZoomFactor(DEFAULT_ZOOM_FACTOR));
        zoomLabel.setPreferredSize(new Dimension(54, 28));
        zoomPanel.add(zoomLabel);

        JButton zoomInButton = new JButton("+");
        zoomInButton.addActionListener(e -> stepZoom(1));
        zoomPanel.add(zoomInButton);

        JButton resetZoomButton = new JButton("1x");
        resetZoomButton.addActionListener(e -> setZoom(DEFAULT_ZOOM_FACTOR));
        zoomPanel.add(resetZoomButton);
        return zoomPanel;
    }

    private void stepZoom(int delta) {
        int currentIndex = findNearestZoomIndex(zoomView == null ? DEFAULT_ZOOM_FACTOR : zoomView.getZoomFactor());
        int nextIndex = Math.max(0, Math.min(ZOOM_LEVELS.length - 1, currentIndex + delta));
        setZoom(ZOOM_LEVELS[nextIndex]);
    }

    private int findNearestZoomIndex(double zoomFactor) {
        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < ZOOM_LEVELS.length; i++) {
            double distance = Math.abs(ZOOM_LEVELS[i] - zoomFactor);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void setZoom(double zoomFactor) {
        setZoom(zoomFactor, null);
    }

    private void setZoom(double zoomFactor, Point anchorInViewport) {
        if (zoomView == null || graphScrollPane == null) {
            return;
        }

        JViewport viewport = graphScrollPane.getViewport();
        Rectangle viewRect = viewport.getViewRect();
        Point anchorPoint = anchorInViewport == null
            ? new Point(viewRect.width / 2, viewRect.height / 2)
            : anchorInViewport;
        Dimension oldSize = zoomView.getPreferredSize();
        double anchorRatioX = oldSize.width <= 0
            ? 0.0d
            : (viewRect.x + anchorPoint.x) / (double) oldSize.width;
        double anchorRatioY = oldSize.height <= 0
            ? 0.0d
            : (viewRect.y + anchorPoint.y) / (double) oldSize.height;

        zoomView.setZoomFactor(zoomFactor);
        if (zoomLabel != null) {
            zoomLabel.setText(formatZoomFactor(zoomView.getZoomFactor()));
        }

        Dimension newSize = zoomView.getPreferredSize();
        int targetX = clamp(
            (int) Math.round(anchorRatioX * newSize.width - anchorPoint.x),
            0,
            Math.max(0, newSize.width - viewRect.width)
        );
        int targetY = clamp(
            (int) Math.round(anchorRatioY * newSize.height - anchorPoint.y),
            0,
            Math.max(0, newSize.height - viewRect.height)
        );
        viewport.setViewPosition(new Point(targetX, targetY));
    }

    private void handleGraphMouseWheelZoom(MouseWheelEvent event) {
        if (zoomView == null || graphScrollPane == null || event.getWheelRotation() == 0) {
            return;
        }

        int currentIndex = findNearestZoomIndex(zoomView.getZoomFactor());
        int nextIndex = clamp(currentIndex - Integer.signum(event.getWheelRotation()), 0, ZOOM_LEVELS.length - 1);
        if (nextIndex == currentIndex) {
            event.consume();
            return;
        }

        Point anchorInViewport = SwingUtilities.convertPoint(
            event.getComponent(),
            event.getPoint(),
            graphScrollPane.getViewport()
        );
        setZoom(ZOOM_LEVELS[nextIndex], anchorInViewport);
        event.consume();
    }

    private int clamp(int value, int minValue, int maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    private String formatZoomFactor(double zoomFactor) {
        if (Math.abs(zoomFactor - Math.rint(zoomFactor)) < 1.0e-6d) {
            return String.format(Locale.US, "%.0fx", zoomFactor);
        }
        return String.format(Locale.US, "%.2fx", zoomFactor);
    }

    private static final class ZoomableGraphView extends JPanel {

        private final BufferedImage previewImage;
        private double zoomFactor;

        private ZoomableGraphView(BufferedImage previewImage, double zoomFactor) {
            super(new GridLayout(1, 1));
            this.previewImage = previewImage;
            this.zoomFactor = Math.max(0.1d, zoomFactor);
            setOpaque(true);
            setBackground(Color.WHITE);
            updateViewSize();
        }

        private double getZoomFactor() {
            return zoomFactor;
        }

        private void setZoomFactor(double zoomFactor) {
            this.zoomFactor = Math.max(0.1d, zoomFactor);
            updateViewSize();
        }

        private void updateViewSize() {
            Dimension baseSize = new Dimension(previewImage.getWidth(), previewImage.getHeight());
            int scaledWidth = Math.max(1, (int) Math.round(baseSize.width * zoomFactor));
            int scaledHeight = Math.max(1, (int) Math.round(baseSize.height * zoomFactor));
            setPreferredSize(new Dimension(scaledWidth, scaledHeight));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            try {
                int scaledWidth = Math.max(1, (int) Math.round(previewImage.getWidth() * zoomFactor));
                int scaledHeight = Math.max(1, (int) Math.round(previewImage.getHeight() * zoomFactor));
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(previewImage, 0, 0, scaledWidth, scaledHeight, null);
            } finally {
                g2.dispose();
            }
        }
    }

    private static final class MultipleSyntenyResultGraphPanel extends JIGBasePanel {

        private static final Color GENOME_FILL = Color.WHITE;
        private static final Color LINK_FILL_LIGHT_GRAY = new Color(220, 220, 220);
        private static final float GENOME_BORDER_STROKE = 1.2f;
        private static final float CHROMOSOME_BORDER_STROKE = 0.8f;

        private final ResultScene scene;
        private final RenderSettings settings;
        private final Map<String, PreparedGenome> genomeById;
        private final List<PreparedLink> links;
        private final List<PreparedHighlightGene> highlightGenes;

        private MultipleSyntenyResultGraphPanel(ResultScene scene) {
            super(
                scene.getRenderSettings().getCanvasWidth(),
                scene.getRenderSettings().getCanvasHeight()
            );
            this.scene = scene;
            this.settings = scene.getRenderSettings();
            this.genomeById = new LinkedHashMap<>();
            this.links = new ArrayList<>();
            this.highlightGenes = new ArrayList<>();

            setOpaque(true);
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(settings.getCanvasWidth(), settings.getCanvasHeight()));
            setMinimumSize(new Dimension(Math.max(600, settings.getCanvasWidth()), Math.max(400, settings.getCanvasHeight())));
            setSize(settings.getCanvasWidth(), settings.getCanvasHeight());

            initializeBoundsSubPanel();
            prepareScene();
        }

        private void initializeBoundsSubPanel() {
            JIGSubPanel subPanel = new JIGSubPanel();
            subPanel.setPanelId(1);
            subPanel.setComponentType(JIGConstants.ComponentType.Panel);
            subPanel.setPoints(new Point2D[] {
                new Point2D.Double(0.0d, 0.0d),
                new Point2D.Double(settings.getCanvasWidth(), settings.getCanvasHeight())
            });
            subPanel.setDrawColor(null);
            subPanel.setFillColor(null);
            addSubPanel(subPanel);
        }

        private void prepareScene() {
            List<Color> genomeStripColors = buildGenomeStripColors(scene.getGenomes().size());
            for (int i = 0; i < scene.getGenomes().size(); i++) {
                GenomeInfo genomeInfo = scene.getGenomes().get(i);
                PreparedGenome preparedGenome = PreparedGenome.from(
                    genomeInfo,
                    genomeStripColors.get(i % genomeStripColors.size())
                );
                genomeById.put(preparedGenome.genomeId, preparedGenome);
            }

            for (LinkInfo linkInfo : scene.getLinks()) {
                PreparedLink preparedLink = PreparedLink.from(linkInfo, genomeById);
                if (preparedLink != null) {
                    links.add(preparedLink);
                }
            }
            links.sort(Comparator.comparingInt(preparedLink -> preparedLink.zOrder));

            for (HighlightGeneInfo highlightGeneInfo : scene.getHighlightGenes()) {
                PreparedHighlightGene preparedHighlightGene =
                    PreparedHighlightGene.from(highlightGeneInfo, genomeById);
                if (preparedHighlightGene != null) {
                    highlightGenes.add(preparedHighlightGene);
                }
            }
        }

        @Override
        public void paint(Graphics graphics) {
            Graphics2D baseGraphics = (Graphics2D) graphics;
            Graphics2D jigGraphics = (Graphics2D) baseGraphics.create();
            try {
                super.paint(jigGraphics);
            } finally {
                jigGraphics.dispose();
            }

            Graphics2D g2 = (Graphics2D) baseGraphics.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                renderLinks(g2);
                renderGenomes(g2);
                renderHighlightGenes(g2);
            } finally {
                g2.dispose();
            }
        }

        private void renderLinks(Graphics2D g2) {
            for (PreparedLink link : links) {
                Composite previousComposite = g2.getComposite();
                g2.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER,
                    (float) Math.max(0.0d, Math.min(1.0d, link.alpha))
                ));
                g2.setColor(link.fillColor);
                g2.fill(link.shape);
                g2.setComposite(previousComposite);
            }
        }

        private void renderGenomes(Graphics2D g2) {
            List<PreparedGenome> orderedGenomes = new ArrayList<>(genomeById.values());
            orderedGenomes.sort(Comparator.comparingInt(preparedGenome -> preparedGenome.zOrder));

            for (PreparedGenome genome : orderedGenomes) {
                for (PreparedChromosome chromosome : genome.chromosomes) {
                    g2.setColor(chromosome.fillColor);
                    g2.fill(chromosome.shape);
                    g2.setColor(chromosome.borderColor);
                    g2.setStroke(new BasicStroke(CHROMOSOME_BORDER_STROKE));
                    g2.draw(chromosome.shape);
                }

                g2.setColor(new Color(43, 58, 82));
                g2.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
                FontMetrics metrics = g2.getFontMetrics();
                String label = genome.displayName;
                int labelX = (int) Math.round(genome.labelPoint.getX() - metrics.stringWidth(label) / 2.0d);
                int labelY = (int) Math.round(genome.labelPoint.getY() + metrics.getAscent() / 2.0d);
                g2.drawString(label, labelX, labelY);
            }
        }

        private List<Color> buildGenomeStripColors(int genomeCount) {
            List<Color> palette = ColorPalette.autoSelect(Math.max(3, genomeCount));
            if (palette == null || palette.isEmpty()) {
                palette = new ArrayList<>();
                palette.add(settings.getDefaultChrFillColor());
            }

            List<Color> orderedPalette = new ArrayList<>(palette);
            int paletteOffset = Math.floorMod(scene.getResultDir().getName().hashCode(), orderedPalette.size());
            List<Color> colors = new ArrayList<>(Math.max(1, genomeCount));
            for (int i = 0; i < Math.max(1, genomeCount); i++) {
                colors.add(orderedPalette.get((paletteOffset + i) % orderedPalette.size()));
            }
            return colors;
        }

        private void renderHighlightGenes(Graphics2D g2) {
            for (PreparedHighlightGene highlightGene : highlightGenes) {
                g2.setColor(highlightGene.color);
                g2.fill(highlightGene.shape);
                g2.setColor(deriveBorderColor(highlightGene.color));
                g2.setStroke(new BasicStroke(0.8f));
                g2.draw(highlightGene.shape);

                if (highlightGene.label != null && !highlightGene.label.isEmpty()) {
                    g2.setColor(deriveBorderColor(highlightGene.color));
                    g2.setFont(SimpleGenomeHubStyle.FONT_SANS_PLAIN_12);
                    g2.drawString(
                        highlightGene.label,
                        (int) Math.round(highlightGene.labelPoint.getX() + 4.0d),
                        (int) Math.round(highlightGene.labelPoint.getY() - 4.0d)
                    );
                }
            }
        }

        private static Color deriveBorderColor(Color fillColor) {
            if (fillColor == null) {
                return new Color(120, 120, 120);
            }
            return new Color(
                Math.max(0, (int) Math.round(fillColor.getRed() * 0.62d)),
                Math.max(0, (int) Math.round(fillColor.getGreen() * 0.62d)),
                Math.max(0, (int) Math.round(fillColor.getBlue() * 0.62d))
            );
        }

        private static final class PreparedGenome {
            private final String genomeId;
            private final String displayName;
            private final int zOrder;
            private final double leftBottomX;
            private final double leftBottomY;
            private final int rectWidth;
            private final int rectHeight;
            private final int rotationDeg;
            private final Shape outline;
            private final List<PreparedChromosome> chromosomes;
            private final Map<String, PreparedChromosome> chromosomeByName;
            private final Point2D.Double labelPoint;
            private final Color stripFillColor;

            private PreparedGenome(String genomeId, String displayName, int zOrder,
                                   double leftBottomX, double leftBottomY,
                                   int rectWidth, int rectHeight, int rotationDeg,
                                   Shape outline, List<PreparedChromosome> chromosomes,
                                   Map<String, PreparedChromosome> chromosomeByName,
                                   Point2D.Double labelPoint, Color stripFillColor) {
                this.genomeId = genomeId;
                this.displayName = displayName;
                this.zOrder = zOrder;
                this.leftBottomX = leftBottomX;
                this.leftBottomY = leftBottomY;
                this.rectWidth = rectWidth;
                this.rectHeight = rectHeight;
                this.rotationDeg = rotationDeg;
                this.outline = outline;
                this.chromosomes = chromosomes;
                this.chromosomeByName = chromosomeByName;
                this.labelPoint = labelPoint;
                this.stripFillColor = stripFillColor;
            }

            private static PreparedGenome from(GenomeInfo genomeInfo, Color stripFillColor) {
                Path2D.Double outline = createRectangle(
                    genomeInfo.getLeftBottomX(),
                    genomeInfo.getLeftBottomY(),
                    genomeInfo.getRectWidth(),
                    genomeInfo.getRectHeight(),
                    genomeInfo.getRotationDeg()
                );

                List<PreparedChromosome> chromosomes = new ArrayList<>();
                Map<String, PreparedChromosome> chromosomeByName = new LinkedHashMap<>();
                for (ChromosomeInfo chromosomeInfo : genomeInfo.getChromosomes()) {
                    PreparedChromosome preparedChromosome =
                        PreparedChromosome.from(genomeInfo, chromosomeInfo, stripFillColor);
                    chromosomes.add(preparedChromosome);
                    chromosomeByName.put(preparedChromosome.chromosomeName, preparedChromosome);
                }

                Point2D.Double labelPoint = MultipleSyntenyResultGraphPanel.pointFromLeftBottom(
                    genomeInfo.getLeftBottomX(),
                    genomeInfo.getLeftBottomY(),
                    genomeInfo.getRotationDeg(),
                    genomeInfo.getRectWidth() / 2.0d,
                    18.0d
                );

                return new PreparedGenome(
                    genomeInfo.getGenomeId(),
                    genomeInfo.getDisplayName(),
                    genomeInfo.getZOrder(),
                    genomeInfo.getLeftBottomX(),
                    genomeInfo.getLeftBottomY(),
                    genomeInfo.getRectWidth(),
                    genomeInfo.getRectHeight(),
                    genomeInfo.getRotationDeg(),
                    outline,
                    chromosomes,
                    chromosomeByName,
                    labelPoint,
                    stripFillColor
                );
            }

            private PreparedChromosome getChromosome(String chromosomeName) {
                return chromosomeByName.get(chromosomeName);
            }

            private Point2D.Double pointFromLeftBottom(double xOffset, double yOffset) {
                return MultipleSyntenyResultGraphPanel.pointFromLeftBottom(
                    leftBottomX,
                    leftBottomY,
                    rotationDeg,
                    xOffset,
                    yOffset
                );
            }

            private Point2D.Double rotateVector(double x, double y) {
                double radians = Math.toRadians(-rotationDeg);
                double cos = Math.cos(radians);
                double sin = Math.sin(radians);
                return new Point2D.Double(x * cos - y * sin, x * sin + y * cos);
            }

            private Point2D.Double topNormal() {
                return rotateVector(0.0d, -1.0d);
            }

            private Point2D.Double bottomNormal() {
                return rotateVector(0.0d, 1.0d);
            }
        }

        private static final class PreparedChromosome {
            private final String chromosomeName;
            private final long bpLength;
            private final double displayStart;
            private final double displayEnd;
            private final Shape shape;
            private final Color fillColor;
            private final Color borderColor;

            private PreparedChromosome(String chromosomeName, long bpLength,
                                       double displayStart, double displayEnd, Shape shape,
                                       Color fillColor, Color borderColor) {
                this.chromosomeName = chromosomeName;
                this.bpLength = bpLength;
                this.displayStart = displayStart;
                this.displayEnd = displayEnd;
                this.shape = shape;
                this.fillColor = fillColor;
                this.borderColor = borderColor;
            }

            private static PreparedChromosome from(GenomeInfo genomeInfo, ChromosomeInfo chromosomeInfo,
                                                   Color fillColor) {
                double startX = chromosomeInfo.getDisplayStart();
                double endX = chromosomeInfo.getDisplayEnd();
                Shape shape = createCapsule(
                    genomeInfo.getLeftBottomX(),
                    genomeInfo.getLeftBottomY(),
                    genomeInfo.getRotationDeg(),
                    startX,
                    -genomeInfo.getRectHeight(),
                    Math.max(1.0d, endX - startX),
                    genomeInfo.getRectHeight()
                );

                return new PreparedChromosome(
                    chromosomeInfo.getChromosomeName(),
                    chromosomeInfo.getBpLength(),
                    chromosomeInfo.getDisplayStart(),
                    chromosomeInfo.getDisplayEnd(),
                    shape,
                    fillColor,
                    deriveBorderColor(fillColor)
                );
            }

            private double mapBpToLocalX(long bp) {
                if (bpLength <= 1L) {
                    return displayStart;
                }
                long clampedBp = Math.max(1L, Math.min(bpLength, bp));
                double fraction = (clampedBp - 1.0d) / (bpLength - 1.0d);
                return displayStart + fraction * Math.max(1.0d, displayEnd - displayStart);
            }
        }

        private static final class PreparedLink {
            private final Shape shape;
            private final Color fillColor;
            private final double alpha;
            private final int zOrder;
            private final boolean highlighted;

            private PreparedLink(Shape shape, Color fillColor,
                                 double alpha, int zOrder, boolean highlighted) {
                this.shape = shape;
                this.fillColor = fillColor;
                this.alpha = alpha;
                this.zOrder = zOrder;
                this.highlighted = highlighted;
            }

            private static PreparedLink from(LinkInfo linkInfo, Map<String, PreparedGenome> genomeById) {
                PreparedGenome firstGenome = genomeById.get(linkInfo.getGenome1Id());
                PreparedGenome secondGenome = genomeById.get(linkInfo.getGenome2Id());
                if (firstGenome == null || secondGenome == null) {
                    return null;
                }

                PreparedChromosome firstChromosome = firstGenome.getChromosome(linkInfo.getChromosome1());
                PreparedChromosome secondChromosome = secondGenome.getChromosome(linkInfo.getChromosome2());
                if (firstChromosome == null || secondChromosome == null) {
                    return null;
                }

                RibbonEdge firstEdge = createRibbonEdge(
                    firstGenome,
                    firstChromosome,
                    linkInfo.getStart1(),
                    linkInfo.getEnd1(),
                    linkInfo.getEdge1()
                );
                RibbonEdge secondEdge = createRibbonEdge(
                    secondGenome,
                    secondChromosome,
                    linkInfo.getStart2(),
                    linkInfo.getEnd2(),
                    linkInfo.getEdge2()
                );
                Shape shape = createLinkShape(
                    firstEdge,
                    secondEdge,
                    linkInfo.getLinkType(),
                    linkInfo.getBulgeDirection(),
                    Math.max(0.0d, linkInfo.getBendValue())
                );
                if (shape == null) {
                    return null;
                }

                boolean highlighted = linkInfo.getZOrder() >= MultipleSyntenyService.DEFAULT_HIGHLIGHT_Z_ORDER;
                return new PreparedLink(
                    shape,
                    highlighted ? linkInfo.getColor() : LINK_FILL_LIGHT_GRAY,
                    linkInfo.getAlpha(),
                    linkInfo.getZOrder(),
                    highlighted
                );
            }
        }

        private static final class PreparedHighlightGene {
            private final Shape shape;
            private final Color color;
            private final String label;
            private final Point2D.Double labelPoint;

            private PreparedHighlightGene(Shape shape, Color color, String label, Point2D.Double labelPoint) {
                this.shape = shape;
                this.color = color;
                this.label = label;
                this.labelPoint = labelPoint;
            }

            private static PreparedHighlightGene from(HighlightGeneInfo highlightGeneInfo,
                                                      Map<String, PreparedGenome> genomeById) {
                PreparedGenome genome = genomeById.get(highlightGeneInfo.getGenomeId());
                if (genome == null) {
                    return null;
                }
                PreparedChromosome chromosome = genome.getChromosome(highlightGeneInfo.getChromosomeName());
                if (chromosome == null) {
                    return null;
                }

                double startX = chromosome.mapBpToLocalX(highlightGeneInfo.getStart());
                double endX = chromosome.mapBpToLocalX(highlightGeneInfo.getEnd());
                double leftX = Math.min(startX, endX);
                double width = Math.max(2.0d, Math.abs(endX - startX));
                if (width <= 2.0d) {
                    leftX -= 1.0d;
                }

                Shape shape = createRectangle(
                    genome.leftBottomX,
                    genome.leftBottomY,
                    genome.rotationDeg,
                    leftX,
                    -genome.rectHeight,
                    width,
                    genome.rectHeight
                );
                Point2D.Double labelPoint = genome.pointFromLeftBottom(leftX + width / 2.0d, -genome.rectHeight - 4.0d);
                String label = highlightGeneInfo.getLabel();
                if (label == null || label.trim().isEmpty() || ".".equals(label.trim())) {
                    label = "";
                }

                return new PreparedHighlightGene(shape, highlightGeneInfo.getColor(), label, labelPoint);
            }
        }

        private static final class RibbonEdge {
            private final Point2D.Double leftPoint;
            private final Point2D.Double rightPoint;
            private final Point2D.Double centerPoint;
            private final Point2D.Double outwardNormal;

            private RibbonEdge(Point2D.Double leftPoint, Point2D.Double rightPoint,
                               Point2D.Double centerPoint, Point2D.Double outwardNormal) {
                this.leftPoint = leftPoint;
                this.rightPoint = rightPoint;
                this.centerPoint = centerPoint;
                this.outwardNormal = outwardNormal;
            }
        }

        private static RibbonEdge createRibbonEdge(PreparedGenome genome, PreparedChromosome chromosome,
                                                   long start, long end, String edgeLabel) {
            double startX = chromosome.mapBpToLocalX(start);
            double endX = chromosome.mapBpToLocalX(end);
            double leftX = Math.min(startX, endX);
            double rightX = Math.max(startX, endX);
            if (Math.abs(rightX - leftX) < 1.0d) {
                double centerX = (leftX + rightX) / 2.0d;
                leftX = centerX - 0.5d;
                rightX = centerX + 0.5d;
            }

            boolean topEdge = !"bottom".equalsIgnoreCase(edgeLabel);
            double localY = topEdge ? -genome.rectHeight : 0.0d;

            Point2D.Double leftPoint = genome.pointFromLeftBottom(leftX, localY);
            Point2D.Double rightPoint = genome.pointFromLeftBottom(rightX, localY);
            Point2D.Double centerPoint = genome.pointFromLeftBottom((leftX + rightX) / 2.0d, localY);
            Point2D.Double outwardNormal = topEdge ? genome.topNormal() : genome.bottomNormal();
            outwardNormal = normalize(outwardNormal);
            if (outwardNormal == null) {
                outwardNormal = topEdge ? new Point2D.Double(0.0d, -1.0d) : new Point2D.Double(0.0d, 1.0d);
            }

            return new RibbonEdge(leftPoint, rightPoint, centerPoint, outwardNormal);
        }

        private static Shape createLinkShape(RibbonEdge firstEdge, RibbonEdge secondEdge,
                                             String linkType, String bulgeDirection, double bendAmount) {
            if ("single_arc".equalsIgnoreCase(linkType)) {
                return createSingleArc(firstEdge, secondEdge, bendAmount, bulgeDirection);
            }
            return createDoubleArc(firstEdge, secondEdge, bendAmount, bulgeDirection);
        }

        private static Shape createSingleArc(RibbonEdge firstEdge, RibbonEdge secondEdge,
                                             double bendAmount, String bulgeDirection) {
            Point2D.Double bendDirection = resolveBendDirection(
                firstEdge.outwardNormal,
                secondEdge.outwardNormal,
                bulgeDirection
            );

            Point2D.Double rightControl = add(
                midpoint(firstEdge.rightPoint, secondEdge.rightPoint),
                scale(bendDirection, bendAmount)
            );
            Point2D.Double leftControl = add(
                midpoint(secondEdge.leftPoint, firstEdge.leftPoint),
                scale(bendDirection, bendAmount)
            );

            Path2D.Double path = new Path2D.Double();
            path.moveTo(firstEdge.leftPoint.x, firstEdge.leftPoint.y);
            path.lineTo(firstEdge.rightPoint.x, firstEdge.rightPoint.y);
            path.quadTo(rightControl.x, rightControl.y, secondEdge.rightPoint.x, secondEdge.rightPoint.y);
            path.lineTo(secondEdge.leftPoint.x, secondEdge.leftPoint.y);
            path.quadTo(leftControl.x, leftControl.y, firstEdge.leftPoint.x, firstEdge.leftPoint.y);
            path.closePath();
            return path;
        }

        private static Shape createDoubleArc(RibbonEdge firstEdge, RibbonEdge secondEdge,
                                             double bendAmount, String bulgeDirection) {
            Point2D.Double firstNormal = resolveEdgeNormal(firstEdge.outwardNormal, bulgeDirection);
            Point2D.Double secondNormal = resolveEdgeNormal(secondEdge.outwardNormal, bulgeDirection);

            Point2D.Double rightControl1 = add(firstEdge.rightPoint, scale(firstNormal, bendAmount));
            Point2D.Double rightControl2 = add(secondEdge.rightPoint, scale(secondNormal, bendAmount));
            Point2D.Double leftControl1 = add(secondEdge.leftPoint, scale(secondNormal, bendAmount));
            Point2D.Double leftControl2 = add(firstEdge.leftPoint, scale(firstNormal, bendAmount));

            Path2D.Double path = new Path2D.Double();
            path.moveTo(firstEdge.leftPoint.x, firstEdge.leftPoint.y);
            path.lineTo(firstEdge.rightPoint.x, firstEdge.rightPoint.y);
            path.curveTo(
                rightControl1.x, rightControl1.y,
                rightControl2.x, rightControl2.y,
                secondEdge.rightPoint.x, secondEdge.rightPoint.y
            );
            path.lineTo(secondEdge.leftPoint.x, secondEdge.leftPoint.y);
            path.curveTo(
                leftControl1.x, leftControl1.y,
                leftControl2.x, leftControl2.y,
                firstEdge.leftPoint.x, firstEdge.leftPoint.y
            );
            path.closePath();
            return path;
        }

        private static Point2D.Double resolveBendDirection(Point2D.Double firstNormal,
                                                           Point2D.Double secondNormal,
                                                           String bulgeDirection) {
            if ("up".equalsIgnoreCase(bulgeDirection)) {
                return new Point2D.Double(0.0d, -1.0d);
            }
            if ("down".equalsIgnoreCase(bulgeDirection)) {
                return new Point2D.Double(0.0d, 1.0d);
            }

            Point2D.Double combined = normalize(add(firstNormal, secondNormal));
            if (combined != null) {
                return combined;
            }
            Point2D.Double normalizedFirst = normalize(firstNormal);
            if (normalizedFirst != null) {
                return normalizedFirst;
            }
            return new Point2D.Double(0.0d, 1.0d);
        }

        private static Point2D.Double resolveEdgeNormal(Point2D.Double defaultNormal,
                                                        String bulgeDirection) {
            if ("up".equalsIgnoreCase(bulgeDirection)) {
                return new Point2D.Double(0.0d, -1.0d);
            }
            if ("down".equalsIgnoreCase(bulgeDirection)) {
                return new Point2D.Double(0.0d, 1.0d);
            }
            Point2D.Double normalized = normalize(defaultNormal);
            return normalized == null ? new Point2D.Double(0.0d, 1.0d) : normalized;
        }

        private static Shape createCapsule(double leftBottomX, double leftBottomY,
                                           int rotationDeg, double localX, double localY,
                                           double width, double height) {
            double safeWidth = Math.max(1.0d, width);
            double safeHeight = Math.max(1.0d, height);
            double arcSize = Math.min(safeWidth, safeHeight);

            RoundRectangle2D.Double localCapsule =
                new RoundRectangle2D.Double(localX, localY, safeWidth, safeHeight, arcSize, arcSize);
            AffineTransform transform = new AffineTransform();
            transform.translate(leftBottomX, leftBottomY);
            transform.rotate(Math.toRadians(-rotationDeg));
            return transform.createTransformedShape(localCapsule);
        }

        private static Path2D.Double createRectangle(double leftBottomX, double leftBottomY,
                                                     int rotationDeg, double localX, double localY,
                                                     double width, double height) {
            Point2D.Double p1 = pointFromLeftBottom(leftBottomX, leftBottomY, rotationDeg, localX, localY + height);
            Point2D.Double p2 = pointFromLeftBottom(leftBottomX, leftBottomY, rotationDeg, localX + width, localY + height);
            Point2D.Double p3 = pointFromLeftBottom(leftBottomX, leftBottomY, rotationDeg, localX + width, localY);
            Point2D.Double p4 = pointFromLeftBottom(leftBottomX, leftBottomY, rotationDeg, localX, localY);

            Path2D.Double path = new Path2D.Double();
            path.moveTo(p1.x, p1.y);
            path.lineTo(p2.x, p2.y);
            path.lineTo(p3.x, p3.y);
            path.lineTo(p4.x, p4.y);
            path.closePath();
            return path;
        }

        private static Path2D.Double createRectangle(double leftBottomX, double leftBottomY,
                                                     int rectWidth, int rectHeight, int rotationDeg) {
            Point2D.Double p1 = pointFromLeftBottom(leftBottomX, leftBottomY, rotationDeg, 0.0d, 0.0d);
            Point2D.Double p2 = pointFromLeftBottom(leftBottomX, leftBottomY, rotationDeg, rectWidth, 0.0d);
            Point2D.Double p3 = pointFromLeftBottom(leftBottomX, leftBottomY, rotationDeg, rectWidth, -rectHeight);
            Point2D.Double p4 = pointFromLeftBottom(leftBottomX, leftBottomY, rotationDeg, 0.0d, -rectHeight);

            Path2D.Double path = new Path2D.Double();
            path.moveTo(p1.x, p1.y);
            path.lineTo(p2.x, p2.y);
            path.lineTo(p3.x, p3.y);
            path.lineTo(p4.x, p4.y);
            path.closePath();
            return path;
        }

        private static Point2D.Double pointFromLeftBottom(double leftBottomX, double leftBottomY,
                                                          int rotationDeg, double xOffset, double yOffset) {
            Point2D.Double offset = rotateVector(rotationDeg, xOffset, yOffset);
            return new Point2D.Double(leftBottomX + offset.x, leftBottomY + offset.y);
        }

        private static Point2D.Double rotateVector(int rotationDeg, double x, double y) {
            double radians = Math.toRadians(-rotationDeg);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            return new Point2D.Double(x * cos - y * sin, x * sin + y * cos);
        }

        private static Point2D.Double midpoint(Point2D.Double first, Point2D.Double second) {
            return new Point2D.Double(
                (first.x + second.x) * 0.5d,
                (first.y + second.y) * 0.5d
            );
        }

        private static Point2D.Double add(Point2D.Double first, Point2D.Double second) {
            return new Point2D.Double(first.x + second.x, first.y + second.y);
        }

        private static Point2D.Double scale(Point2D.Double vector, double factor) {
            return new Point2D.Double(vector.x * factor, vector.y * factor);
        }

        private static Point2D.Double normalize(Point2D.Double vector) {
            if (vector == null) {
                return null;
            }
            double length = Math.sqrt(vector.x * vector.x + vector.y * vector.y);
            if (length < 1.0e-6d) {
                return null;
            }
            return new Point2D.Double(vector.x / length, vector.y / length);
        }
    }
}
