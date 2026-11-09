package simplegenomehub.util.fileio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Recoverable publication of related files in one directory. Readers hide unfinished sets. */
public final class FileSetTransaction {
    private static final String PREFIX = ".sgh-transaction-";
    private static final String JOURNAL = "manifest.properties";
    private FileSetTransaction() { }

    public static Path stageSibling(Path target) throws IOException {
        Path directory = target.toAbsolutePath().normalize().getParent();
        Files.createDirectories(directory);
        recover(directory);
        return Files.createTempFile(directory, ".sgh-stage-", ".tmp");
    }

    /** Remove only staging allocated by this helper and its optional short-lock file. */
    public static void discardStage(Path staged) throws IOException {
        if (staged == null) return;
        String name = staged.getFileName().toString();
        if (!name.startsWith(".sgh-stage-") || !name.endsWith(".tmp"))
            throw new IOException("Not an owned staging path: " + staged);
        Files.deleteIfExists(staged);
        Files.deleteIfExists(staged.resolveSibling(name + ".lock"));
    }

    /** All targets/deletions share one directory. Callers clean up their remaining staged files. */
    public static void commit(Map<Path, Path> stagedToTarget, Collection<Path> deferredDeletes) throws IOException {
        commit(stagedToTarget, deferredDeletes, Collections.emptyList());
    }

    /** expectedAbsent protects create/rename operations against another writer winning the race. */
    public static void commit(Map<Path, Path> stagedToTarget, Collection<Path> deferredDeletes,
                              Collection<Path> expectedAbsent) throws IOException {
        LinkedHashMap<Path, Path> replacements = new LinkedHashMap<>();
        LinkedHashSet<Path> targets = new LinkedHashSet<>();
        for (Map.Entry<Path, Path> entry : stagedToTarget.entrySet()) {
            Path source = entry.getKey().toAbsolutePath().normalize();
            Path target = entry.getValue().toAbsolutePath().normalize();
            if (!targets.add(target)) throw new IOException("Duplicate transaction target: " + target);
            if (source.equals(target) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Missing or invalid staged file: " + source);
            replacements.put(source, target);
        }
        if (deferredDeletes != null) for (Path file : deferredDeletes) {
            Path target = file.toAbsolutePath().normalize();
            if (!targets.add(target)) throw new IOException("A replacement cannot also be deleted: " + target);
        }
        if (targets.isEmpty()) return;
        for (Path source : replacements.keySet()) if (targets.contains(source))
            throw new IOException("A staged source cannot also be a transaction target: " + source);
        Path directory = targets.iterator().next().getParent();
        for (Path target : targets) {
            if (!directory.equals(target.getParent())) throw new IOException("Related transaction targets must share a directory");
            if (target.getFileName().toString().startsWith(".sgh-")) throw new IOException("Reserved transaction target: " + target);
        }
        Files.createDirectories(directory);
        AtomicFiles.withLock(directory.resolve(".sgh-fileset"), () -> {
            recoverLocked(directory);
            for (Path missing : expectedAbsent) {
                Path target = missing.toAbsolutePath().normalize();
                if (!targets.contains(target)) throw new IOException("Expected-absent path is not a target: " + target);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("A file already exists: " + target);
            }
            for (Path target : targets) if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Transaction target is not a regular file: " + target);
            Path transaction = Files.createDirectory(directory.resolve(PREFIX + UUID.randomUUID()));
            Properties journal = new Properties();
            journal.setProperty("count", Integer.toString(targets.size()));
            int i = 0;
            for (Path target : targets) {
                journal.setProperty("target." + i, target.getFileName().toString());
                journal.setProperty("existed." + i, Boolean.toString(Files.exists(target)));
                i++;
            }
            journal.setProperty("state", "PREPARED");
            try {
                writeJournal(transaction, journal);
                i = 0;
                for (Path target : targets) {
                    if (Files.exists(target)) AtomicFiles.replace(target, transaction.resolve("backup." + i));
                    i++;
                }
                for (Map.Entry<Path, Path> entry : replacements.entrySet()) {
                    try (FileChannel channel = FileChannel.open(entry.getKey(), StandardOpenOption.WRITE)) { channel.force(true); }
                    AtomicFiles.replace(entry.getKey(), entry.getValue());
                }
                journal.setProperty("state", "COMMITTED");
                writeJournal(transaction, journal);
            } catch (IOException | RuntimeException failure) {
                try { recoverOne(directory, transaction); }
                catch (IOException recoveryFailure) { failure.addSuppressed(recoveryFailure); }
                throw failure;
            }
            // Once committed, cleanup failure must not report a failed save or roll back good data.
            try { cleanup(transaction); } catch (IOException cleanupFailure) { /* next writer retries */ }
            return null;
        });
    }

    public static void recover(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) return;
        AtomicFiles.withLock(absolute.resolve(".sgh-fileset"), () -> { recoverLocked(absolute); return null; });
    }

    /** A read-only visibility check: ordinary loading never migrates or repairs business data. */
    public static boolean isPublished(Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        if (absolute.getFileName().toString().startsWith(".sgh-")) return false;
        Path directory = absolute.getParent();
        if (!Files.isDirectory(directory)) return true;
        try (DirectoryStream<Path> pending = Files.newDirectoryStream(directory, PREFIX + "*")) {
            for (Path transaction : pending) {
                if (!Files.isDirectory(transaction, LinkOption.NOFOLLOW_LINKS)) continue;
                Properties journal = readJournal(transaction);
                if (journal == null || "COMMITTED".equals(journal.getProperty("state"))) continue;
                int count = count(journal);
                for (int i = 0; i < count; i++)
                    if (absolute.equals(target(directory, journal, i))) return false;
            }
            return true;
        } catch (IOException | RuntimeException unreadable) { return false; }
    }

    private static void recoverLocked(Path directory) throws IOException {
        try (DirectoryStream<Path> pending = Files.newDirectoryStream(directory, PREFIX + "*")) {
            for (Path transaction : pending) {
                if (Files.isDirectory(transaction, LinkOption.NOFOLLOW_LINKS)) recoverOne(directory, transaction);
            }
        }
    }

    private static void recoverOne(Path directory, Path transaction) throws IOException {
        Properties journal = readJournal(transaction);
        if (journal != null) {
            String state = journal.getProperty("state");
            if (!"COMMITTED".equals(state) && !"PREPARED".equals(state))
                throw new IOException("Unknown file transaction state: " + transaction);
            Set<Path> unique = new HashSet<>();
            for (int i = 0; i < count(journal); i++) {
                if (!unique.add(target(directory, journal, i))) throw new IOException("Duplicate recovery target");
                String existed = journal.getProperty("existed." + i);
                if (!"true".equals(existed) && !"false".equals(existed))
                    throw new IOException("Incomplete file transaction journal: " + transaction);
            }
        }
        // A directory without a published journal cannot have changed any target.
        if (journal != null && !"COMMITTED".equals(journal.getProperty("state"))) {
            int count = count(journal);
            for (int i = 0; i < count; i++) {
                Path target = target(directory, journal, i);
                Path backup = transaction.resolve("backup." + i);
                if (Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)) AtomicFiles.replace(backup, target);
                else if (!Boolean.parseBoolean(journal.getProperty("existed." + i))) Files.deleteIfExists(target);
            }
        }
        cleanup(transaction);
    }

    private static int count(Properties journal) throws IOException {
        try {
            int count = Integer.parseInt(journal.getProperty("count"));
            if (count < 0 || count > 100000) throw new NumberFormatException();
            return count;
        } catch (RuntimeException invalid) { throw new IOException("Invalid file transaction journal", invalid); }
    }

    private static Path target(Path directory, Properties journal, int index) throws IOException {
        String name = journal.getProperty("target." + index);
        if (name == null || name.isEmpty() || name.startsWith(".sgh-")) throw new IOException("Invalid transaction target");
        Path target = directory.resolve(name).normalize();
        if (!directory.equals(target.getParent())) throw new IOException("Transaction target escapes directory: " + name);
        return target;
    }

    private static Properties readJournal(Path transaction) throws IOException {
        Path path = transaction.resolve(JOURNAL);
        if (!Files.exists(path)) return null;
        Properties journal = new Properties();
        try (InputStream input = Files.newInputStream(path)) { journal.load(input); }
        return journal;
    }

    private static void writeJournal(Path transaction, Properties journal) throws IOException {
        Path stage = transaction.resolve("manifest.tmp");
        try (OutputStream output = Files.newOutputStream(stage)) { journal.store(output, "SGH file set transaction"); }
        try (FileChannel channel = FileChannel.open(stage, StandardOpenOption.WRITE)) { channel.force(true); }
        AtomicFiles.replace(stage, transaction.resolve(JOURNAL));
    }

    private static void cleanup(Path transaction) throws IOException {
        // Never recurse: only this transaction's journal and numbered backups may be removed.
        List<Path> owned = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(transaction)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.equals(JOURNAL) || name.equals("manifest.tmp") || name.matches("backup\\.[0-9]+"))
                    owned.add(file);
                else throw new IOException("Unexpected transaction file: " + file);
            }
        }
        // Keep the durable decision until every backup is removed, including during cleanup retries.
        for (Path file : owned) if (!file.getFileName().toString().equals(JOURNAL)) Files.deleteIfExists(file);
        Files.deleteIfExists(transaction.resolve(JOURNAL));
        Files.delete(transaction);
    }
}
