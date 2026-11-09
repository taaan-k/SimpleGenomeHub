package simplegenomehub.util.fileio;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/** Complete-file publication and short locks for configuration and shared caches. */
public final class AtomicFiles {
    private static final ConcurrentHashMap<Path, Object> MONITORS = new ConcurrentHashMap<>();
    private AtomicFiles() { }
    @FunctionalInterface public interface IOAction<T> { T run() throws IOException; }
    @FunctionalInterface public interface WriterAction { void write(Writer writer) throws IOException; }

    public static <T> T withLock(Path target, IOAction<T> action) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Path parent = absolute.getParent().toRealPath();
        Path lockPath = parent.resolve(absolute.getFileName() + ".lock");
        synchronized (MONITORS.computeIfAbsent(lockPath, ignored -> new Object())) {
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                long deadline = System.nanoTime() + 10_000_000_000L;
                FileLock lock = null;
                while (lock == null) {
                    try { lock = channel.tryLock(); } catch (OverlappingFileLockException busy) { }
                    if (lock == null) {
                        if (System.nanoTime() >= deadline) throw new IOException("Shared file is busy: " + target);
                        try { Thread.sleep(25); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted while waiting for " + target, e);
                        }
                    }
                }
                try (FileLock held = lock) { return action.run(); }
            }
        }
    }

    public static void write(Path target, WriterAction action) throws IOException {
        Path absolute = target.toAbsolutePath().normalize();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), ".sgh-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) { action.write(writer); }
            replace(temporary, absolute);
        } finally { Files.deleteIfExists(temporary); }
    }

    public static void replace(Path temporary, Path target) throws IOException {
        try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void copy(Path source, Path target) throws IOException {
        if (Files.exists(target) && Files.isSameFile(source, target)) return;
        withLock(target, () -> {
            copyComplete(source, target);
            return null;
        });
    }

    /** The data and its optional checksum share one short publication lock. */
    public static void copyPair(Path source, Path target, Path sourceChecksum, Path targetChecksum) throws IOException {
        if (Files.exists(target) && Files.isSameFile(source, target)) return;
        withLock(target, () -> {
            copyComplete(source, target);
            if (targetChecksum != null) {
                if (sourceChecksum != null && Files.isRegularFile(sourceChecksum)) copyComplete(sourceChecksum, targetChecksum);
                else Files.deleteIfExists(targetChecksum);
            }
            return null;
        });
    }

    private static void copyComplete(Path source, Path target) throws IOException {
        Path temporary = Files.createTempFile(target.toAbsolutePath().getParent(), ".sgh-cache-", ".tmp");
        try { Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING); replace(temporary, target); }
        finally { Files.deleteIfExists(temporary); }
    }

    public static Properties readProperties(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(file)) try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
        return properties;
    }

    /** Reread under the lock and update only caller-owned keys, retaining unknown fields. */
    public static Properties updateProperties(Path file, Map<String, String> changes, String comment) throws IOException {
        return withLock(file, () -> {
            Properties merged = readProperties(file);
            changes.forEach((key, value) -> { if (value == null) merged.remove(key); else merged.setProperty(key, value); });
            // Published SGH releases use Properties.load(InputStream). Its
            // ISO-8859-1 codec needs non-ASCII values represented as Unicode escapes.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            merged.store(bytes, comment);
            String encoded = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
            write(file, writer -> writer.write(encoded));
            return merged;
        });
    }
}
