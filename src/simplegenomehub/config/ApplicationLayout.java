package simplegenomehub.config;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        List<File> candidateDirectories = getBundledToolSearchDirectories();
        return candidateDirectories.isEmpty() ? null : candidateDirectories.get(0);
    }

    public static File findBundledToolsRootDir() {
        File toolsDir = new File(getAppHomeDirectory(), "bin" + File.separator + "tools");
        return toolsDir.isDirectory() ? toolsDir : null;
    }

    public static List<File> getBundledToolSearchDirectories() {
        return getToolSearchDirectories(getAppHomeDirectory());
    }

    public static List<File> getToolSearchDirectories(File baseDirectory) {
        List<File> directories = new ArrayList<>();
        addToolSearchDirectories(directories, baseDirectory);
        return directories;
    }

    public static void addToolSearchDirectories(List<File> directories, File baseDirectory) {
        if (directories == null || baseDirectory == null) {
            return;
        }

        addToolSearchDirectoriesForRoot(directories, new File(baseDirectory, "bin" + File.separator + "tools"));
        addToolSearchDirectoriesForRoot(directories,
            new File(baseDirectory, "SimpleGenomeHub" + File.separator + "bin" + File.separator + "tools"));
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
        return normalizeOsName().contains("win");
    }

    public static boolean isMac() {
        String normalizedOsName = normalizeOsName();
        return normalizedOsName.contains("mac") || normalizedOsName.contains("darwin");
    }

    public static boolean isLinux() {
        return normalizeOsName().contains("nux");
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

    private static void addToolSearchDirectoriesForRoot(List<File> directories, File toolsRoot) {
        if (toolsRoot == null || !toolsRoot.isDirectory()) {
            return;
        }

        for (String candidateName : getPlatformToolDirectoryNames()) {
            addUniqueDirectory(directories, new File(toolsRoot, candidateName));
        }
        addUniqueDirectory(directories, toolsRoot);
    }

    private static void addUniqueDirectory(List<File> directories, File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }

        String candidatePath = directory.getAbsolutePath();
        for (File existing : directories) {
            if (existing.getAbsolutePath().equalsIgnoreCase(candidatePath)) {
                return;
            }
        }
        directories.add(directory);
    }

    private static String[] getPlatformToolDirectoryNames() {
        if (isWindows()) {
            return new String[]{"windows-x64", "windows", "win-x64", "win"};
        }
        if (isMac()) {
            return isArmArchitecture()
                ? new String[]{"macos-arm64", "macos-universal", "macos-x64", "macos"}
                : new String[]{"macos-x64", "macos-universal", "macos"};
        }
        if (isLinux()) {
            return isArmArchitecture()
                ? new String[]{"linux-arm64", "linux-aarch64", "linux"}
                : new String[]{"linux-x64", "linux-amd64", "linux"};
        }
        return new String[0];
    }

    private static boolean isArmArchitecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arch.contains("aarch64") || arch.contains("arm64") || arch.startsWith("arm");
    }

    private static String normalizeOsName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }
}
