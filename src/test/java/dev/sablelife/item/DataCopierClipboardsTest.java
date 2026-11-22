package dev.sablelife.item;

import com.google.common.hash.Hashing;
import dev.sablelife.module.AuthoringLimits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class DataCopierClipboardsTest {
    @TempDir Path directory;

    @Test void rejectsAValidatingWriterThatCannotRoundTripWithinTheReadBudget() throws IOException {
        var configuration = configurationOverDecodeBudget();
        assertTrue(AuthoringLimits.configuration(configuration));
        assertTrue(configuration.sizeInBytes() <= AuthoringLimits.MAX_CONFIGURATION_BYTES);
        assertTrue(legacyEncoding(configuration).length < DataCopierClipboards.MAX_CLIPBOARD_BYTES);
        var failure = assertThrows(DataCopierClipboards.TooLarge.class,
                () -> store(4096, 4096).save(UUID.randomUUID(), configuration));
        assertEquals(DataCopierClipboards.Limit.CONFIGURATION, failure.limit);
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test void stopsOversizeCompressionBeforePublishingAReference() throws IOException {
        var configuration = new CompoundTag();
        var model = new byte[2_100_000];
        new Random(2026).nextBytes(model);
        configuration.putByteArray("Model", model);
        assertTrue(AuthoringLimits.configuration(configuration));
        var failure = assertThrows(DataCopierClipboards.TooLarge.class,
                () -> store(4096, 4096).save(UUID.randomUUID(), configuration));
        assertEquals(DataCopierClipboards.Limit.COMPRESSED, failure.limit);
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test void accountAndWorldQuotasAreIndependentAndPersistAcrossRestarts() throws IOException {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var third = UUID.randomUUID();
        var data = configuration("one");
        var different = configuration("two");
        var initial = store(4096, 8192);
        var one = initial.save(first, data).reference();
        assertEquals(one, initial.save(first, data).reference());
        var two = initial.save(second, data).reference();
        assertEquals(one.id(), two.id());
        assertNotEquals(one, two);
        assertTrue(assertThrows(DataCopierClipboards.QuotaExceeded.class, () -> initial.save(first, different)).account);
        assertFalse(assertThrows(DataCopierClipboards.QuotaExceeded.class, () -> initial.save(third, different)).account);

        var reopened = store(4096, 8192);
        assertEquals(data, reopened.read(one));
        assertEquals(8192, reopened.status(false, null).worldActive());
        assertEquals(4096, reopened.status(true, second).active());
        assertTrue(reopened.archive(one));
        assertFalse(reopened.archive(one));
        assertEquals(data, reopened.read(two));
        assertThrows(IOException.class, () -> reopened.read(one));
        assertEquals(4096, reopened.status(false, null).worldArchive());
        assertEquals(one, reopened.save(first, data).reference());
        assertEquals(0, reopened.status(false, null).worldArchive());
        assertEquals(data, reopened.read(one));

        assertTrue(reopened.archive(one));
        var remaining = reopened.save(first, different).reference();
        assertTrue(assertThrows(DataCopierClipboards.QuotaExceeded.class, () -> reopened.restore(one)).account);
        assertEquals(different, reopened.read(remaining));
        assertTrue(reopened.archive(remaining));
        assertTrue(reopened.restore(one));
        assertFalse(reopened.restore(one));
        assertEquals(data, store(4096, 8192).read(one));
    }

    @Test void legacyReferencesKeepTheirAddressWhenArchivedAndRestored() throws IOException {
        var owner = UUID.randomUUID();
        var payload = configuration("legacy");
        byte[] bytes = DataCopierClipboards.encode(payload);
        var key = new DataCopierClipboards.Reference(null, Hashing.sha256().hashBytes(bytes).toString());
        NbtIo.writeCompressed(payload, directory.resolve(key.fileName()));
        var store = store(4096, 8192);
        assertEquals(payload, store.read(key));
        assertEquals(key, store.save(owner, payload).reference());
        assertTrue(store.archive(key));

        var replacement = store.save(owner, payload).reference();
        assertEquals(owner, replacement.owner());
        assertEquals(key.id(), replacement.id());
        assertThrows(IOException.class, () -> store.read(key));
        assertTrue(store.restore(key));
        assertEquals(payload, store.read(key));
        assertEquals(payload, store.read(replacement));
    }

    @Test void aFailedRestoreNeverMovesOrOverwritesArchivedData() throws IOException {
        var owner = UUID.randomUUID();
        var initial = store(4096, 4096);
        var data = configuration("kept");
        var ref = initial.save(owner, data).reference();
        assertTrue(initial.archive(ref));
        var second = initial.save(UUID.randomUUID(), configuration("other")).reference();
        assertFalse(assertThrows(DataCopierClipboards.QuotaExceeded.class, () -> initial.restore(ref)).account);
        assertEquals(4096, initial.status(false, null).worldArchive());
        assertTrue(initial.archive(second));

        var archived = directory.resolve("archive").resolve(ref.fileName());
        Files.writeString(directory.resolve(ref.fileName()), "existing data");
        assertThrows(IOException.class, () -> initial.restore(ref));
        assertEquals(data, NbtIo.readCompressed(archived, NbtAccounter.create(AuthoringLimits.MAX_CONFIGURATION_BYTES)));
        assertEquals("existing data", Files.readString(directory.resolve(ref.fileName())));
    }

    @Test void restoreRejectsUnreadableLegacyDataWithoutChangingActiveUsage() throws IOException {
        var bytes = legacyEncoding(configurationOverDecodeBudget());
        var key = new DataCopierClipboards.Reference(null, Hashing.sha256().hashBytes(bytes).toString());
        var active = directory.resolve(key.fileName());
        Files.write(active, bytes);
        var store = store(64 * 1024L, 64 * 1024L);

        var alreadyActive = assertThrows(DataCopierClipboards.TooLarge.class, () -> store.restore(key));
        assertEquals(DataCopierClipboards.Limit.CONFIGURATION, alreadyActive.limit);
        assertArrayEquals(bytes, Files.readAllBytes(active));
        assertTrue(store.archive(key));

        var archived = directory.resolve("archive").resolve(key.fileName());
        var before = store.status(false, null);
        assertFalse(Files.exists(active));
        assertTrue(before.worldArchive() > 0);
        assertEquals(0, before.worldActive());

        var failure = assertThrows(DataCopierClipboards.TooLarge.class, () -> store.restore(key));
        assertEquals(DataCopierClipboards.Limit.CONFIGURATION, failure.limit);
        assertFalse(Files.exists(active));
        assertArrayEquals(bytes, Files.readAllBytes(archived));
        assertEquals(before, store.status(false, null));
    }

    @Test void archiveDirectorySymlinkCannotRedirectMaintenanceOrRecopy() throws IOException {
        var redirected = Files.createDirectory(directory.resolve("other-storage"));
        try {
            Files.createSymbolicLink(directory.resolve("archive"), redirected);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.assumeTrue(false, "File system does not allow creating a symbolic link: " + e.getMessage());
        }
        var owner = UUID.randomUUID();
        var clipboard = configuration("linked");
        byte[] bytes = DataCopierClipboards.encode(clipboard);
        var key = new DataCopierClipboards.Reference(owner, Hashing.sha256().hashBytes(bytes).toString());
        var redirectedFile = redirected.resolve(key.fileName());
        Files.write(redirectedFile, bytes);

        var store = store(4096, 4096);
        assertThrows(IOException.class, () -> store.restore(key));
        assertThrows(IOException.class, () -> store.status(false, null));
        assertEquals(key, store.save(owner, clipboard).reference());
        assertEquals(clipboard, store.read(key));
        assertThrows(IOException.class, () -> store.archive(key));
        assertArrayEquals(bytes, Files.readAllBytes(redirectedFile));
        assertEquals(clipboard, store.read(key));
    }

    private DataCopierClipboards.Store store(long accountLimit, long worldLimit) {
        return new DataCopierClipboards.Store(directory, accountLimit, worldLimit);
    }

    private static CompoundTag configurationOverDecodeBudget() {
        var data = new CompoundTag();
        var materialNames = new ListTag();
        var material = StringTag.valueOf("石".repeat(1320));
        for (int index = 0; index < 2100; index++) materialNames.add(material);
        data.put("Materials", materialNames);
        return data;
    }

    private static byte[] legacyEncoding(CompoundTag data) throws IOException {
        var output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(data, output);
        return output.toByteArray();
    }

    private static CompoundTag configuration(String model) {
        var data = new CompoundTag();
        data.putString("Model", model);
        return data;
    }
}
