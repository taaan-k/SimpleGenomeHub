package simplegenomehub.util.fileio;

import biocjava.bioIO.GeneOntology.obo.GoOboProvider;
import simplegenomehub.config.SimpleGenomeHubConfig;
import simplegenomehub.model.SpeciesInfo;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralizes GO OBO discovery, caching, and TBtools-backed download/update flow.
 */
public final class GoOboManager {

    private static final Logger logger = Logger.getLogger(GoOboManager.class.getName());

    public enum SourceType {
        DIALOG_OVERRIDE,
        SPECIES_CACHE,
        LEGACY_SPECIES_CACHE,
        GLOBAL_CACHE,
        MISSING
    }

    public static final class ResolutionResult {
        private final File resolvedFile;
        private final SourceType sourceType;
        private final boolean exists;
        private final boolean verified;
        private final boolean updateRecommended;
        private final String message;

        private ResolutionResult(File resolvedFile, SourceType sourceType, boolean exists,
                                 boolean verified, boolean updateRecommended, String message) {
            this.resolvedFile = resolvedFile;
            this.sourceType = sourceType;
            this.exists = exists;
            this.verified = verified;
            this.updateRecommended = updateRecommended;
            this.message = message;
        }

        public File getResolvedFile() {
            return resolvedFile;
        }

        public SourceType getSourceType() {
            return sourceType;
        }

        public boolean exists() {
            return exists;
        }

        public boolean isVerified() {
            return verified;
        }

        public boolean isUpdateRecommended() {
            return updateRecommended;
        }

        public String getMessage() {
            return message;
        }
    }

    private GoOboManager() {
    }

    public static ResolutionResult resolveForSpecies(SpeciesInfo species, File overrideFile) {
        if (overrideFile != null && overrideFile.isFile()) {
            return buildResult(overrideFile, SourceType.DIALOG_OVERRIDE);
        }

        File speciesGoObo = getSpeciesGoOboFile(species);
        if (speciesGoObo.isFile()) {
            return buildResult(speciesGoObo, SourceType.SPECIES_CACHE);
        }

        File legacyObo = getLegacySpeciesOboFile(species);
        if (legacyObo.isFile()) {
            return buildResult(legacyObo, SourceType.LEGACY_SPECIES_CACHE);
        }

        File globalObo = getGlobalOboFile();
        if (globalObo.isFile()) {
            return buildResult(globalObo, SourceType.GLOBAL_CACHE);
        }

        return new ResolutionResult(null, SourceType.MISSING, false, false, false,
            "No go-basic.obo found. Choose a local file or download the latest OBO.");
    }

    public static File getSpeciesGoDir(SpeciesInfo species) {
        return species != null ? species.getAnnotationTypeDirectory(simplegenomehub.model.GeneAnnotationData.AnnotationType.GO) : null;
    }

    public static File getSpeciesGoOboFile(SpeciesInfo species) {
        File goDir = getSpeciesGoDir(species);
        return goDir != null ? new File(goDir, "go-basic.obo") : null;
    }

    public static File getSpeciesGoMd5File(SpeciesInfo species) {
        File goDir = getSpeciesGoDir(species);
        return goDir != null ? new File(goDir, "go-basic.obo.md5") : null;
    }

    public static File getLegacySpeciesOboFile(SpeciesInfo species) {
        File functionalDir = species != null ? species.getFunctionalAnnotationDir() : null;
        return functionalDir != null ? new File(functionalDir, "go-basic.obo") : null;
    }

    public static File getGlobalOboFile() {
        return new File(SimpleGenomeHubConfig.getInstance().getHomeDir(), "go-basic.obo");
    }

    public static File getGlobalMd5File() {
        return new File(SimpleGenomeHubConfig.getInstance().getHomeDir(), "go-basic.obo.md5");
    }

    public static boolean cacheOboForSpecies(SpeciesInfo species, File sourceOboFile, boolean updateGlobalCache) {
        if (species == null || sourceOboFile == null || !sourceOboFile.isFile()) {
            return false;
        }

        try {
            File speciesGoDir = getSpeciesGoDir(species);
            if (speciesGoDir != null && !speciesGoDir.exists()) {
                speciesGoDir.mkdirs();
            }

            copyWithOptionalMd5(sourceOboFile, getSpeciesGoOboFile(species), getSpeciesGoMd5File(species));

            if (updateGlobalCache) {
                cacheAsGlobalDefault(sourceOboFile, "species_cache");
            }

            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to cache GO OBO for species", e);
            return false;
        }
    }

    public static boolean cacheAsGlobalDefault(File sourceOboFile, String sourceLabel) {
        if (sourceOboFile == null || !sourceOboFile.isFile()) {
            return false;
        }

        try {
            File homeDir = SimpleGenomeHubConfig.getInstance().getHomeDir();
            if (!homeDir.exists()) {
                homeDir.mkdirs();
            }

            copyWithOptionalMd5(sourceOboFile, getGlobalOboFile(), getGlobalMd5File());

            SimpleGenomeHubConfig config = SimpleGenomeHubConfig.getInstance();
            config.setProperty(SimpleGenomeHubConfig.GO_OBO_GLOBAL_PATH, getGlobalOboFile().getAbsolutePath());
            config.setProperty(SimpleGenomeHubConfig.GO_OBO_LAST_SOURCE, sourceLabel);
            config.setProperty(SimpleGenomeHubConfig.GO_OBO_LAST_VERIFIED_EPOCH, String.valueOf(System.currentTimeMillis()));
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to cache GO OBO globally", e);
            return false;
        }
    }

    public static boolean validateOboFile(File oboFile) {
        if (oboFile == null || !oboFile.isFile()) {
            return false;
        }

        GOOboParser parser = new GOOboParser();
        return parser.parseOboFile(oboFile);
    }

    public static void downloadLatestToGlobalCache(Component parent, Runnable onComplete) {
        File homeDir = SimpleGenomeHubConfig.getInstance().getHomeDir();
        GoOboProvider.downloadLatestOboAndThen(homeDir, parent, oboPath -> {
            SimpleGenomeHubConfig config = SimpleGenomeHubConfig.getInstance();
            config.setProperty(SimpleGenomeHubConfig.GO_OBO_GLOBAL_PATH, oboPath);
            config.setProperty(SimpleGenomeHubConfig.GO_OBO_LAST_SOURCE, "tbtools_feijipan");
            config.setProperty(SimpleGenomeHubConfig.GO_OBO_LAST_VERIFIED_EPOCH, String.valueOf(System.currentTimeMillis()));
        }, onComplete);
    }

    private static ResolutionResult buildResult(File file, SourceType sourceType) {
        boolean hasMd5 = new File(file.getParentFile(), "go-basic.obo.md5").isFile();
        boolean verified = hasMd5 && GoOboProvider.verifyOboWithMd5(file);
        boolean updateRecommended = !verified;

        String message;
        if (verified) {
            message = "Using " + sourceLabel(sourceType) + " go-basic.obo (MD5 verified).";
        } else if (hasMd5) {
            message = "Using " + sourceLabel(sourceType) + " go-basic.obo, but MD5 verification failed. Update recommended.";
        } else {
            message = "Using " + sourceLabel(sourceType) + " go-basic.obo. No MD5 file found; update recommended.";
        }

        return new ResolutionResult(file, sourceType, true, verified, updateRecommended, message);
    }

    private static String sourceLabel(SourceType sourceType) {
        switch (sourceType) {
            case DIALOG_OVERRIDE:
                return "selected";
            case SPECIES_CACHE:
                return "species-local";
            case LEGACY_SPECIES_CACHE:
                return "legacy species-local";
            case GLOBAL_CACHE:
                return "global cache";
            default:
                return "missing";
        }
    }

    private static void copyWithOptionalMd5(File sourceObo, File targetObo, File targetMd5) throws IOException {
        if (targetObo == null) {
            return;
        }

        File parent = targetObo.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        Files.copy(sourceObo.toPath(), targetObo.toPath(), StandardCopyOption.REPLACE_EXISTING);

        File sourceMd5 = new File(sourceObo.getParentFile(), "go-basic.obo.md5");
        if (sourceMd5.isFile() && targetMd5 != null) {
            Files.copy(sourceMd5.toPath(), targetMd5.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
