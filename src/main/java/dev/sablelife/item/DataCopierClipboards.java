package dev.sablelife.item;

import com.google.common.hash.Hashing;
import dev.sablelife.module.AuthoringLimits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.SYNC;
import static java.nio.file.StandardOpenOption.WRITE;

/** Content-addressed storage shared by the dimensions of one world. Accessed on the server thread. */
final class DataCopierClipboards {
    static final int MAX_CLIPBOARD_BYTES = 2_000_000;
    static final long MAX_ACCOUNT_BYTES = 64L * 1024 * 1024;
    static final long MAX_STORE_BYTES = 2L * 1024 * 1024 * 1024;
    private static final Pattern ID = Pattern.compile("[0-9a-f]{64}");
    private static final int MINIMUM_FILE_CHARGE = 4096;
    private static final Map<MinecraftServer, Store> stores = new WeakHashMap<>();

    record Reference(UUID owner, String id) {
        Reference {
            if (!ID.matcher(id).matches()) throw new IllegalArgumentException("Invalid clipboard digest");
        }

        String fileName() {
            return (owner == null ? "" : owner + "_") + id + ".nbt";
        }
    }

    record Saved(Reference reference, int bytes) {}
    record UsageStatus(long active, long archive, long worldActive, long worldArchive, long limit) {}
    enum Limit {
        COMPRESSED("Clipboard exceeds the compressed size limit"),
        CONFIGURATION("Clipboard exceeds the uncompressed size or complexity limit");

        private final String message;
        Limit(String message) { this.message = message; }
    }

    static final class TooLarge extends IOException {
        final Limit limit;
        TooLarge(Limit limit) { this(limit, null); }
        TooLarge(Limit limit, Throwable cause) {
            super(limit.message, cause);
            this.limit = limit;
        }
    }
    static final class QuotaExceeded extends IOException {
        final boolean account;
        QuotaExceeded(boolean account) { this.account = account; }
    }

    private DataCopierClipboards() {}

    static Store store(MinecraftServer server) {
        return stores.computeIfAbsent(server, key -> new Store(
                key.getWorldPath(LevelResource.ROOT).resolve("data").resolve("sablelife_clipboards"),
                MAX_ACCOUNT_BYTES, MAX_STORE_BYTES));
    }

    static byte[] encode(CompoundTag data) throws IOException {
        if (!AuthoringLimits.configuration(data)) throw new TooLarge(Limit.CONFIGURATION);
        var compressed = new BoundedOutput();
        NbtIo.writeCompressed(data, compressed);
        byte[] bytes = compressed.bytes.toByteArray();
        if (!decode(bytes).equals(data)) throw new IOException("Clipboard data cannot be encoded losslessly");
        return bytes;
    }

    private static CompoundTag decode(byte[] bytes) throws IOException {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes),
                    NbtAccounter.create(AuthoringLimits.MAX_CONFIGURATION_BYTES));
        } catch (NbtAccounterException e) {
            throw new TooLarge(Limit.CONFIGURATION, e);
        } catch (NbtException | ReportedNbtException | NegativeArraySizeException e) {
            throw new IOException("Invalid Data Copier clipboard NBT", e);
        }
    }

    private static final class BoundedOutput extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override public void write(int value) throws IOException {
            requireSpace(1);
            bytes.write(value);
        }

        @Override public void write(byte[] values, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, values.length);
            requireSpace(length);
            bytes.write(values, offset, length);
        }

        private void requireSpace(int length) throws IOException {
            if (length > MAX_CLIPBOARD_BYTES - bytes.size()) throw new TooLarge(Limit.COMPRESSED);
        }
    }

    private static final class Usage {
        private final Map<UUID, Long> accounts = new HashMap<>();
        private long total;

        long account(UUID owner) { return accounts.getOrDefault(owner, 0L); }

        void add(UUID owner, long delta) {
            total += delta;
            accounts.compute(owner, (key, previous) -> {
                long updated = (previous == null ? 0L : previous) + delta;
                return updated == 0L ? null : updated;
            });
        }
    }

    static final class Store {
        private final Path active;
        private final Path archive;
        private final long accountLimit;
        private final long worldLimit;
        private Usage used;

        Store(Path active, long accountLimit, long worldLimit) {
            this.active = active;
            this.archive = active.resolve("archive");
            this.accountLimit = accountLimit;
            this.worldLimit = worldLimit;
        }

        Saved save(UUID owner, CompoundTag clipboard) throws IOException {
            Objects.requireNonNull(owner);
            var data = encode(clipboard);
            var id = Hashing.sha256().hashBytes(data).toString();
            var reference = new Reference(owner, id);
            var destination = active.resolve(reference.fileName());
            if (present(destination)) {
                if (!Arrays.equals(data, readFile(destination))) throw new IOException("Existing clipboard has different data");
                return new Saved(reference, data.length);
            }
            if (Files.isDirectory(archive, NOFOLLOW_LINKS) && present(archive.resolve(reference.fileName()))) {
                restore(reference);
                return new Saved(reference, data.length);
            }
            var legacy = new Reference(null, id);
            if (Files.isRegularFile(active.resolve(legacy.fileName()), NOFOLLOW_LINKS)
                    && Arrays.equals(data, readFile(active.resolve(legacy.fileName())))) return new Saved(legacy, data.length);

            var usage = usage();
            long cost = charge(data.length);
            requireCapacity(usage, owner, cost);
            Files.createDirectories(active);
            var temporary = Files.createTempFile(active, "clipboard-", ".tmp");
            try {
                Files.write(temporary, data, WRITE, SYNC);
                move(temporary, destination);
                usage.add(owner, cost);
            } catch (IOException e) {
                used = null;
                throw e;
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new Saved(reference, data.length);
        }

        CompoundTag read(Reference reference) throws IOException {
            return decode(verifiedBytes(active.resolve(reference.fileName()), reference));
        }

        UsageStatus status(boolean scoped, UUID owner) throws IOException {
            checkArchiveDirectory();
            used = scan(active);
            var archived = scan(archive);
            if (!scoped) return new UsageStatus(used.total, archived.total, used.total, archived.total, worldLimit);
            return new UsageStatus(used.account(owner), archived.account(owner),
                    used.total, archived.total, owner == null ? worldLimit : accountLimit);
        }

        boolean archive(Reference reference) throws IOException {
            checkArchiveDirectory();
            var name = reference.fileName();
            var source = active.resolve(name);
            var destination = archive.resolve(name);
            boolean sourceExists = present(source);
            if (present(destination)) {
                if (sourceExists) throw new IOException("Clipboard already exists in both locations");
                return false;
            }
            if (!sourceExists) throw new IOException("Clipboard file is missing");
            long cost = charge(Files.size(source));
            var usage = usage();
            Files.createDirectories(archive);
            try {
                move(source, destination);
                usage.add(reference.owner(), -cost);
            } catch (IOException e) {
                used = null;
                throw e;
            }
            return true;
        }

        boolean restore(Reference reference) throws IOException {
            checkArchiveDirectory();
            var name = reference.fileName();
            var source = archive.resolve(name);
            var destination = active.resolve(name);
            boolean sourceExists = present(source);
            if (present(destination)) {
                if (sourceExists) throw new IOException("Clipboard already exists in both locations");
                decode(verifiedBytes(destination, reference));
                return false;
            }
            if (!sourceExists) throw new IOException("Clipboard file is missing");
            var data = verifiedBytes(source, reference);
            var usage = usage();
            long cost = charge(data.length);
            requireCapacity(usage, reference.owner(), cost);
            // Verify the same bounded read used by paste before activating the file.
            decode(data);
            try {
                move(source, destination);
                usage.add(reference.owner(), cost);
            } catch (IOException e) {
                used = null;
                throw e;
            }
            return true;
        }

        private void checkArchiveDirectory() throws IOException {
            if (Files.exists(archive, NOFOLLOW_LINKS) && !Files.isDirectory(archive, NOFOLLOW_LINKS)) {
                throw new IOException("Clipboard archive must be a directory inside the clipboard store");
            }
        }

        private Usage usage() throws IOException {
            if (used == null) used = scan(active);
            return used;
        }

        private void requireCapacity(Usage usage, UUID owner, long cost) throws QuotaExceeded {
            if (owner != null && cost > accountLimit - usage.account(owner)) throw new QuotaExceeded(true);
            if (cost > worldLimit - usage.total) throw new QuotaExceeded(false);
        }
    }

    private static long charge(long bytes) {
        return Math.max(MINIMUM_FILE_CHARGE, bytes);
    }

    private static Usage scan(Path directory) throws IOException {
        var usage = new Usage();
        if (!Files.exists(directory, NOFOLLOW_LINKS)) return usage;
        try (var files = Files.newDirectoryStream(directory, "*.nbt")) {
            for (var file : files) {
                if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) continue;
                var reference = referenceFromFileName(file.getFileName().toString());
                if (reference != null) usage.add(reference.owner(), charge(Files.size(file)));
            }
        } catch (DirectoryIteratorException e) {
            throw e.getCause();
        }
        return usage;
    }

    private static Reference referenceFromFileName(String fileName) {
        var name = fileName.substring(0, fileName.length() - ".nbt".length());
        if (ID.matcher(name).matches()) return new Reference(null, name);
        int separator = name.indexOf('_');
        if (separator != 36 || !ID.matcher(name.substring(separator + 1)).matches()) return null;
        try {
            var owner = UUID.fromString(name.substring(0, separator));
            return owner.toString().equals(name.substring(0, separator)) ? new Reference(owner, name.substring(separator + 1)) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean present(Path path) throws IOException {
        if (!Files.exists(path, NOFOLLOW_LINKS)) return false;
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) throw new IOException("Invalid clipboard file");
        return true;
    }

    private static byte[] verifiedBytes(Path path, Reference reference) throws IOException {
        var data = readFile(path);
        if (data.length > MAX_CLIPBOARD_BYTES) throw new TooLarge(Limit.COMPRESSED);
        if (!Hashing.sha256().hashBytes(data).toString().equals(reference.id())) {
            throw new IOException("Invalid Data Copier clipboard file");
        }
        return data;
    }

    private static byte[] readFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) throw new IOException("Invalid clipboard file");
        try (var file = Files.newInputStream(path, NOFOLLOW_LINKS)) {
            return file.readNBytes(MAX_CLIPBOARD_BYTES + 1);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(from, to);
        }
    }
}
