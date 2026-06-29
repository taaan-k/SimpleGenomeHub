package simplegenomehub.util.fileio;

import biocjava.bioDoer.Kegg.KeggBackEndConstants;
import biocjava.bioDoer.Kegg.KeggBackEndProvider;
import simplegenomehub.config.SimpleGenomeHubConfig;
import simplegenomehub.model.GeneAnnotationData;
import simplegenomehub.model.SpeciesInfo;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralizes KEGG backend discovery, caching, and TBtools-backed preset downloads.
 */
public final class KeggBackendManager {

    private static final Logger logger = Logger.getLogger(KeggBackendManager.class.getName());

    public enum BackendMode {
        PRESET,
        CUSTOM
    }

    public enum SourceType {
        CUSTOM_OVERRIDE,
        SPECIES_CACHE,
        LEGACY_SPECIES_CACHE,
        GLOBAL_PRESET_CACHE,
        MISSING
    }

    public static final class ResolutionResult {
        private final File resolvedFile;
        private final BackendMode backendMode;
        private final SourceType sourceType;
        private final String backendType;
        private final boolean exists;
        private final boolean verified;
        private final boolean updateRecommended;
        private final String message;

        private ResolutionResult(File resolvedFile, BackendMode backendMode, SourceType sourceType,
                                 String backendType, boolean exists, boolean verified,
                                 boolean updateRecommended, String message) {
            this.resolvedFile = resolvedFile;
            this.backendMode = backendMode;
            this.sourceType = sourceType;
            this.backendType = backendType;
            this.exists = exists;
            this.verified = verified;
            this.updateRecommended = updateRecommended;
            this.message = message;
        }

        public File getResolvedFile() { return resolvedFile; }
        public BackendMode getBackendMode() { return backendMode; }
        public SourceType getSourceType() { return sourceType; }
        public String getBackendType() { return backendType; }
        public boolean exists() { return exists; }
        public boolean isVerified() { return verified; }
        public boolean isUpdateRecommended() { return updateRecommended; }
        public String getMessage() { return message; }
    }

    private KeggBackendManager() {
    }

    public static ResolutionResult resolveForSpecies(SpeciesInfo species, BackendMode mode,
                                                     String backendType, File customOverride) {
        if (mode == BackendMode.CUSTOM) {
            if (customOverride != null && customOverride.isFile()) {
                return buildCustomResult(customOverride, SourceType.CUSTOM_OVERRIDE);
            }

            File lastCustom = getSpeciesCustomBackendFile(species);
            if (lastCustom != null && lastCustom.isFile()) {
                return buildCustomResult(lastCustom, SourceType.SPECIES_CACHE);
            }
            return new ResolutionResult(null, mode, SourceType.MISSING, backendType, false, false, false,
                "No custom KEGG backend selected.");
        }

        File speciesPreset = getSpeciesPresetBackendFile(species, backendType);
        if (speciesPreset != null && speciesPreset.isFile()) {
            return buildPresetResult(speciesPreset, backendType, SourceType.SPECIES_CACHE);
        }

        File legacyPreset = findLegacyPresetBackendFile(species, backendType);
        if (legacyPreset != null && legacyPreset.isFile()) {
            return buildPresetResult(legacyPreset, backendType, SourceType.LEGACY_SPECIES_CACHE);
        }

        File globalPreset = getGlobalPresetBackendFile(backendType);
        if (globalPreset != null && globalPreset.isFile()) {
            return buildPresetResult(globalPreset, backendType, SourceType.GLOBAL_PRESET_CACHE);
        }

        return new ResolutionResult(null, mode, SourceType.MISSING, backendType, false, false, false,
            "No KEGG preset backend found for " + backendType + ". Download it or switch to custom backend.");
    }

    public static File getGlobalCacheDir() {
        return new File(SimpleGenomeHubConfig.getInstance().getHomeDir(), KeggBackEndConstants.CACHE_SUBDIR);
    }

    public static File getSpeciesKeggDir(SpeciesInfo species) {
        return species != null ? species.getAnnotationTypeDirectory(GeneAnnotationData.AnnotationType.KEGG) : null;
    }

    public static File getGlobalPresetBackendFile(String backendType) {
        if (!KeggBackEndConstants.isPredefinedType(backendType)) {
            return null;
        }
        return KeggBackEndProvider.getBackendFileInCacheDir(getGlobalCacheDir(), backendType);
    }

    public static File getSpeciesPresetBackendFile(SpeciesInfo species, String backendType) {
        if (species == null || !KeggBackEndConstants.isPredefinedType(backendType)) {
            return null;
        }
        File keggDir = getSpeciesKeggDir(species);
        return keggDir != null ? new File(keggDir, KeggBackEndConstants.getBackendFileName(backendType)) : null;
    }

    public static File getSpeciesCustomBackendFile(SpeciesInfo species) {
        File keggDir = getSpeciesKeggDir(species);
        if (keggDir == null || !keggDir.isDirectory()) {
            return null;
        }

        File[] files = keggDir.listFiles((dir, name) ->
            name.startsWith("custom-kegg-backend") || name.contains("TBtoolsKeggBackEnd"));
        if (files == null || files.length == 0) {
            return null;
        }
        return files[0];
    }

    public static boolean validateBackendFile(File backendFile) {
        if (backendFile == null || !backendFile.isFile()) {
            return false;
        }
        try {
            return !KEGGBackgroundParser.parseKEGGBackground(backendFile).isEmpty();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Invalid KEGG backend file", e);
            return false;
        }
    }

    public static void ensurePresetBackend(Component parent, String backendType, Runnable onComplete) {
        File cacheDir = getGlobalCacheDir();
        KeggBackEndProvider.ensureBackendAndThen(cacheDir, backendType, parent, path -> {
            SimpleGenomeHubConfig config = SimpleGenomeHubConfig.getInstance();
            config.setProperty(SimpleGenomeHubConfig.KEGG_BACKEND_MODE, BackendMode.PRESET.name());
            config.setProperty(SimpleGenomeHubConfig.KEGG_BACKEND_TYPE, backendType);
        }, onComplete);
    }

    public static void downloadLatestPresetBackend(Component parent, String backendType, Runnable onComplete) {
        File cacheDir = getGlobalCacheDir();
        KeggBackEndProvider.downloadLatestBackendAndThen(cacheDir, backendType, parent, path -> {
            SimpleGenomeHubConfig config = SimpleGenomeHubConfig.getInstance();
            config.setProperty(SimpleGenomeHubConfig.KEGG_BACKEND_MODE, BackendMode.PRESET.name());
            config.setProperty(SimpleGenomeHubConfig.KEGG_BACKEND_TYPE, backendType);
        }, onComplete);
    }

    public static boolean cachePresetForSpecies(SpeciesInfo species, String backendType, File sourceBackend) {
        if (species == null || sourceBackend == null || !sourceBackend.isFile() || !KeggBackEndConstants.isPredefinedType(backendType)) {
            return false;
        }
        try {
            File speciesDir = getSpeciesKeggDir(species);
            if (speciesDir != null && !speciesDir.exists()) {
                speciesDir.mkdirs();
            }
            File target = getSpeciesPresetBackendFile(species, backendType);
            copyWithMd5(sourceBackend, target);
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to cache preset KEGG backend for species", e);
            return false;
        }
    }

    public static boolean cacheCustomForSpecies(SpeciesInfo species, File customBackend) {
        if (species == null || customBackend == null || !customBackend.isFile()) {
            return false;
        }
        try {
            File speciesDir = getSpeciesKeggDir(species);
            if (speciesDir != null && !speciesDir.exists()) {
                speciesDir.mkdirs();
            }
            File target = new File(speciesDir, "custom-kegg-backend." + customBackend.getName());
            copyWithMd5(customBackend, target);
            SimpleGenomeHubConfig.getInstance().setProperty(SimpleGenomeHubConfig.KEGG_CUSTOM_BACKEND_PATH, target.getAbsolutePath());
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to cache custom KEGG backend for species", e);
            return false;
        }
    }

    private static ResolutionResult buildPresetResult(File file, String backendType, SourceType sourceType) {
        boolean verified = KeggBackEndProvider.verifyBackendWithMd5(file);
        boolean hasMd5 = new File(file.getParentFile(), file.getName() + ".md5").isFile();
        String message;
        if (verified) {
            message = "Using " + backendType + " preset backend from " + sourceLabel(sourceType) + " (MD5 verified).";
        } else if (hasMd5) {
            message = "Using " + backendType + " preset backend from " + sourceLabel(sourceType) + ", but MD5 verification failed.";
        } else {
            message = "Using " + backendType + " preset backend from " + sourceLabel(sourceType) + ". No MD5 file found.";
        }
        return new ResolutionResult(file, BackendMode.PRESET, sourceType, backendType, true, verified, !verified, message);
    }

    private static ResolutionResult buildCustomResult(File file, SourceType sourceType) {
        boolean valid = validateBackendFile(file);
        String message = valid
            ? "Using custom KEGG backend from " + sourceLabel(sourceType) + "."
            : "Custom KEGG backend could not be validated.";
        return new ResolutionResult(file, BackendMode.CUSTOM, sourceType, KeggBackEndConstants.TYPE_CUSTOM,
            valid, false, false, message);
    }

    private static File findLegacyPresetBackendFile(SpeciesInfo species, String backendType) {
        if (species == null || !KeggBackEndConstants.isPredefinedType(backendType)) {
            return null;
        }
        File functionalDir = species.getFunctionalAnnotationDir();
        if (functionalDir == null || !functionalDir.isDirectory()) {
            return null;
        }
        File exact = new File(functionalDir, KeggBackEndConstants.getBackendFileName(backendType));
        if (exact.isFile()) {
            return exact;
        }
        File[] matches = functionalDir.listFiles((dir, name) -> name.contains(backendType) && name.endsWith("TBtoolsKeggBackEnd"));
        return matches != null && matches.length > 0 ? matches[0] : null;
    }

    private static String sourceLabel(SourceType sourceType) {
        switch (sourceType) {
            case CUSTOM_OVERRIDE:
                return "selected file";
            case SPECIES_CACHE:
                return "species cache";
            case LEGACY_SPECIES_CACHE:
                return "legacy species cache";
            case GLOBAL_PRESET_CACHE:
                return "global cache";
            default:
                return "missing";
        }
    }

    private static void copyWithMd5(File source, File target) throws IOException {
        if (target == null) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        File sourceMd5 = new File(source.getParentFile(), source.getName() + ".md5");
        if (sourceMd5.isFile()) {
            Files.copy(sourceMd5.toPath(), new File(target.getParentFile(), target.getName() + ".md5").toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
