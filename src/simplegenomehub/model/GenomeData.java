/*
 * Genome Data Statistics Model
 */
package simplegenomehub.model;

import simplegenomehub.config.SimpleGenomeHubVersion;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data model for genome statistics and information
 * Corresponds to the stat.txt file in species directories
 * 
 * @author SimpleGenomeHub
 */
public class GenomeData {
    
    private static final Logger logger = Logger.getLogger(GenomeData.class.getName());
    
    // Statistical data
    private long genomeSize;
    private int geneCount;
    private int transcriptCount;
    private int cdsCount;
    private int proteinCount;
    private int chromosomeCount;
    private int scaffoldCount;
    
    // File information
    private String genomeFileName;
    private String annotationFileName;
    private boolean hasIndex;
    
    // Additional metadata
    private String assemblyLevel; // chromosome, scaffold, contig
    private String annotationSource; // NCBI, Ensembl, etc.
    private double gcContent;
    private long n50;
    private final List<ChromosomeStat> chromosomeStats;
    
    /**
     * Default constructor
     */
    public GenomeData() {
        this.genomeSize = 0;
        this.geneCount = 0;
        this.transcriptCount = 0;
        this.cdsCount = 0;
        this.proteinCount = 0;
        this.chromosomeCount = 0;
        this.scaffoldCount = 0;
        this.hasIndex = false;
        this.gcContent = 0.0;
        this.n50 = 0;
        this.assemblyLevel = "unknown";
        this.annotationSource = "unknown";
        this.chromosomeStats = new ArrayList<>();
    }
    
    /**
     * Load genome data from stat.txt file
     */
    public static GenomeData loadFromFile(File statsFile) {
        GenomeData data = new GenomeData();
        
        if (!statsFile.exists() || !statsFile.canRead()) {
            logger.warning("Stats file not found or unreadable: " + statsFile.getAbsolutePath());
            return data;
        }
        
        Properties props = new Properties();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(statsFile), StandardCharsets.UTF_8))) {
            props.load(reader);
            
            data.genomeSize = getLongProperty(props, "genome.size", 0);
            data.geneCount = getIntProperty(props, "gene.count", 0);
            data.transcriptCount = getIntProperty(props, "transcript.count", 0);
            data.cdsCount = getIntProperty(props, "cds.count", 0);
            data.proteinCount = getIntProperty(props, "protein.count", 0);
            data.chromosomeCount = getIntProperty(props, "chromosome.count", 0);
            data.scaffoldCount = getIntProperty(props, "scaffold.count", 0);
            
            data.genomeFileName = props.getProperty("genome.file", "");
            data.annotationFileName = props.getProperty("annotation.file", "");
            data.hasIndex = Boolean.parseBoolean(props.getProperty("has.index", "false"));
            data.assemblyLevel = props.getProperty("assembly.level", "unknown");
            data.annotationSource = props.getProperty("annotation.source", "unknown");
            data.gcContent = getDoubleProperty(props, "gc.content", 0.0);
            data.n50 = getLongProperty(props, "n50", 0);
            data.loadChromosomeStats(props);
            
            logger.info("Loaded genome data from: " + statsFile.getAbsolutePath());
            
        } catch (IOException | NumberFormatException e) {
            logger.log(Level.WARNING, "Failed to load genome data from: " + statsFile.getAbsolutePath(), e);
        }
        
        return data;
    }
    
    /**
     * Save genome data to stat.txt file
     */
    public boolean saveToFile(File statsFile) {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(statsFile), StandardCharsets.UTF_8))) {
            writer.write(SimpleGenomeHubVersion.STATS_FILE_HEADER);
            writer.write(System.lineSeparator());

            writePropertyLine(writer, "genome.size", String.valueOf(genomeSize));
            writePropertyLine(writer, "gene.count", String.valueOf(geneCount));
            writePropertyLine(writer, "transcript.count", String.valueOf(transcriptCount));
            writePropertyLine(writer, "cds.count", String.valueOf(cdsCount));
            writePropertyLine(writer, "protein.count", String.valueOf(proteinCount));
            writePropertyLine(writer, "chromosome.count", String.valueOf(chromosomeCount));
            writePropertyLine(writer, "scaffold.count", String.valueOf(scaffoldCount));

            if (genomeFileName != null) {
                writePropertyLine(writer, "genome.file", escapePropertyValue(genomeFileName));
            }
            if (annotationFileName != null) {
                writePropertyLine(writer, "annotation.file", escapePropertyValue(annotationFileName));
            }

            writePropertyLine(writer, "has.index", String.valueOf(hasIndex));
            writePropertyLine(writer, "assembly.level", escapePropertyValue(assemblyLevel));
            writePropertyLine(writer, "annotation.source", escapePropertyValue(annotationSource));
            writePropertyLine(writer, "gc.content", String.valueOf(gcContent));
            writePropertyLine(writer, "n50", String.valueOf(n50));

            if (!chromosomeStats.isEmpty()) {
                writer.write(System.lineSeparator());
                writer.write("#####################chromosome stat");
                writer.write(System.lineSeparator());

                for (int i = 0; i < chromosomeStats.size(); i++) {
                    ChromosomeStat chromosomeStat = chromosomeStats.get(i);
                    int index = i + 1;

                    writePropertyLine(writer,
                        "chromosome" + index + ".name",
                        escapePropertyValue(chromosomeStat.getName()));
                    writePropertyLine(writer,
                        "chromosome" + index + ".size",
                        String.valueOf(chromosomeStat.getSize()));
                    writePropertyLine(writer,
                        "chromosome" + index + ".gc",
                        String.format(Locale.US, "%.4f", chromosomeStat.getGcContent()));
                }
            }

            writer.flush();
            logger.info("Saved genome data to: " + statsFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save genome data to: " + statsFile.getAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * Get formatted genome size string
     */
    public String getFormattedGenomeSize() {
        if (genomeSize >= 1_000_000_000) {
            return String.format("%.2f Gb", genomeSize / 1_000_000_000.0);
        } else if (genomeSize >= 1_000_000) {
            return String.format("%.2f Mb", genomeSize / 1_000_000.0);
        } else if (genomeSize >= 1_000) {
            return String.format("%.2f Kb", genomeSize / 1_000.0);
        } else {
            return genomeSize + " bp";
        }
    }
    
    /**
     * Get summary statistics string for display
     */
    public String getSummaryStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("Genome: ").append(getFormattedGenomeSize());
        sb.append(" | Genes: ").append(String.format("%,d", geneCount));
        sb.append(" | Transcripts: ").append(String.format("%,d", transcriptCount));
        sb.append(" | Proteins: ").append(String.format("%,d", proteinCount));
        if (chromosomeCount > 0) {
            sb.append(" | Chr: ").append(chromosomeCount);
        }
        if (scaffoldCount > 0) {
            sb.append(" | Scaffolds: ").append(scaffoldCount);
        }
        return sb.toString();
    }
    
    // Helper methods for loading properties
    private static int getIntProperty(Properties props, String key, int defaultValue) {
        try {
            String value = props.getProperty(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static long getLongProperty(Properties props, String key, long defaultValue) {
        try {
            String value = props.getProperty(key);
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private static double getDoubleProperty(Properties props, String key, double defaultValue) {
        try {
            String value = props.getProperty(key);
            return value != null ? Double.parseDouble(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void loadChromosomeStats(Properties props) {
        chromosomeStats.clear();

        for (int index = 1; ; index++) {
            String prefix = "chromosome" + index;
            String name = props.getProperty(prefix + ".name");

            if (name == null) {
                break;
            }

            chromosomeStats.add(new ChromosomeStat(
                name,
                getLongProperty(props, prefix + ".size", 0),
                getDoubleProperty(props, prefix + ".gc", 0.0)
            ));
        }
    }

    private static void writePropertyLine(BufferedWriter writer, String key, String value) throws IOException {
        writer.write(key);
        writer.write('=');
        writer.write(value != null ? value : "");
        writer.write(System.lineSeparator());
    }

    private static String escapePropertyValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '=':
                case ':':
                    escaped.append('\\').append(ch);
                    break;
                default:
                    if (i == 0 && ch == ' ') {
                        escaped.append("\\ ");
                    } else {
                        escaped.append(ch);
                    }
                    break;
            }
        }
        return escaped.toString();
    }
    
    // Getters and Setters
    public long getGenomeSize() {
        return genomeSize;
    }
    
    public void setGenomeSize(long genomeSize) {
        this.genomeSize = genomeSize;
    }
    
    public int getGeneCount() {
        return geneCount;
    }
    
    public void setGeneCount(int geneCount) {
        this.geneCount = geneCount;
    }
    
    public int getTranscriptCount() {
        return transcriptCount;
    }
    
    public void setTranscriptCount(int transcriptCount) {
        this.transcriptCount = transcriptCount;
    }
    
    public int getCdsCount() {
        return cdsCount;
    }
    
    public void setCdsCount(int cdsCount) {
        this.cdsCount = cdsCount;
    }
    
    public int getProteinCount() {
        return proteinCount;
    }
    
    public void setProteinCount(int proteinCount) {
        this.proteinCount = proteinCount;
    }
    
    public int getChromosomeCount() {
        return chromosomeCount;
    }
    
    public void setChromosomeCount(int chromosomeCount) {
        this.chromosomeCount = chromosomeCount;
    }
    
    public int getScaffoldCount() {
        return scaffoldCount;
    }
    
    public void setScaffoldCount(int scaffoldCount) {
        this.scaffoldCount = scaffoldCount;
    }
    
    public String getGenomeFileName() {
        return genomeFileName;
    }
    
    public void setGenomeFileName(String genomeFileName) {
        this.genomeFileName = genomeFileName;
    }
    
    public String getAnnotationFileName() {
        return annotationFileName;
    }
    
    public void setAnnotationFileName(String annotationFileName) {
        this.annotationFileName = annotationFileName;
    }
    
    public boolean isHasIndex() {
        return hasIndex;
    }
    
    public void setHasIndex(boolean hasIndex) {
        this.hasIndex = hasIndex;
    }
    
    public String getAssemblyLevel() {
        return assemblyLevel;
    }
    
    public void setAssemblyLevel(String assemblyLevel) {
        this.assemblyLevel = assemblyLevel;
    }
    
    public String getAnnotationSource() {
        return annotationSource;
    }
    
    public void setAnnotationSource(String annotationSource) {
        this.annotationSource = annotationSource;
    }
    
    public double getGcContent() {
        return gcContent;
    }
    
    public void setGcContent(double gcContent) {
        this.gcContent = gcContent;
    }
    
    public long getN50() {
        return n50;
    }
    
    public void setN50(long n50) {
        this.n50 = n50;
    }

    public List<ChromosomeStat> getChromosomeStats() {
        return Collections.unmodifiableList(chromosomeStats);
    }

    public void setChromosomeStats(List<ChromosomeStat> chromosomeStats) {
        this.chromosomeStats.clear();
        if (chromosomeStats != null) {
            this.chromosomeStats.addAll(chromosomeStats);
        }
    }

    public void addChromosomeStat(String name, long size, double gcContent) {
        chromosomeStats.add(new ChromosomeStat(name, size, gcContent));
    }

    public static final class ChromosomeStat {
        private final String name;
        private final long size;
        private final double gcContent;

        public ChromosomeStat(String name, long size, double gcContent) {
            this.name = name != null ? name : "";
            this.size = size;
            this.gcContent = gcContent;
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }

        public double getGcContent() {
            return gcContent;
        }
    }
}
