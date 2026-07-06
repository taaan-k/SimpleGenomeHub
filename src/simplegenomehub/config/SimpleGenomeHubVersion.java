package simplegenomehub.config;

/**
 * Centralized application version strings used by startup migration
 * and stat.txt header stamping.
 */
public final class SimpleGenomeHubVersion {

    public static final String APPLICATION_VERSION = "0.4";
    public static final String STATS_FILE_HEADER = "#SimpleGenomeHub v." + APPLICATION_VERSION;

    private SimpleGenomeHubVersion() {
    }
}
