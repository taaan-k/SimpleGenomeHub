package simplegenomehub.util.fileio;

import java.io.IOException;
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
import java.util.concurrent.ConcurrentHashMap;

/** Complete-file publication and short locks for annotation saves. */
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

}
