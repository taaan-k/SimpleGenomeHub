package simplegenomehub.config;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves the application layout for both bat-launched and packaged runs.
 */
public final class ApplicationLayout {

    public static final String APP_HOME_PROPERTY = "simplegenomehub.app.home";

    private static final Logger logger = Logger.getLogger(ApplicationLayout.class.getName());

    private ApplicationLayout() {
    }

    public static File getAppHomeDirectory() {
        File fromProperty = resolveFromProperty();
        if (fromProperty != null) {
            return fromProperty;
        }

        File fromCodeSource = resolveFromCodeSource();
        if (fromCodeSource != null) {
            return fromCodeSource;
        }

        String userDir = System.getProperty("user.dir", ".");
        return new File(userDir).getAbsoluteFile();
    }

    public static File findBundledBlastDir() {
        return findBundledToolsDir();
    }

    public static File findBundledDiamondDir() {
        return findBundledToolsDir();
    }

    public static File findBundledToolsDir() {
        File toolsDir = new File(getAppHomeDirectory(), "bin" + File.separator + "tools");
        return toolsDir.isDirectory() ? toolsDir : null;
    }

    public static File findBundledRuntimeJava() {
        File runtimeDir = new File(getAppHomeDirectory(), "runtime" + File.separator + "bin");
        File javaExecutable = resolveExecutable(runtimeDir, "java");
        return javaExecutable != null && javaExecutable.isFile() ? javaExecutable : null;
    }

    public static File resolveExecutable(File directory, String baseName) {
        if (directory == null || baseName == null || baseName.trim().isEmpty()) {
            return null;
        }

        String normalizedBaseName = baseName.trim();
        if (isWindows() && !normalizedBaseName.toLowerCase().endsWith(".exe")) {
            File candidate = new File(directory, normalizedBaseName + ".exe");
            if (candidate.isFile()) {
                return candidate;
            }
        }

        File candidate = new File(directory, normalizedBaseName);
        return candidate.isFile() ? candidate : null;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static File resolveFromProperty() {
        String configuredAppHome = System.getProperty(APP_HOME_PROPERTY, "").trim();
        if (configuredAppHome.isEmpty()) {
            return null;
        }
        return new File(configuredAppHome).getAbsoluteFile();
    }

    private static File resolveFromCodeSource() {
        try {
            URL location = ApplicationLayout.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) {
                return null;
            }

            File codeSource = toFile(location);
            if (codeSource == null) {
                return null;
            }

            if (codeSource.isFile()) {
                File jarDir = codeSource.getParentFile();
                if (jarDir != null && "app".equalsIgnoreCase(jarDir.getName()) && jarDir.getParentFile() != null) {
                    return jarDir.getParentFile().getAbsoluteFile();
                }
                return jarDir == null ? null : jarDir.getAbsoluteFile();
            }

            if (codeSource.isDirectory()) {
                File normalized = codeSource.getAbsoluteFile();
                if ("classes".equalsIgnoreCase(normalized.getName())
                    && normalized.getParentFile() != null
                    && ("build".equalsIgnoreCase(normalized.getParentFile().getName())
                    || "out".equalsIgnoreCase(normalized.getParentFile().getName()))
                    && normalized.getParentFile().getParentFile() != null) {
                    return normalized.getParentFile().getParentFile().getAbsoluteFile();
                }
                return normalized;
            }
        } catch (Exception ex) {
            logger.log(Level.FINE, "Failed to resolve application home from code source.", ex);
        }
        return null;
    }

    private static File toFile(URL url) {
        try {
            URI uri = url.toURI();
            return new File(uri);
        } catch (URISyntaxException ex) {
            logger.log(Level.FINE, "Failed to convert URL to file: " + url, ex);
            return null;
        }
    }
}
