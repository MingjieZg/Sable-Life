package dev.sablelife.compat;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Properties;
import java.util.function.Function;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/** Checks native code for integrations that depend on injection behavior in addition to signatures. */
public final class OptionalMixinCompatibility {
    public static boolean supported(String mod, String minimum) {
        var version = OptionalMod.version(mod);
        if (!ModVersion.atLeast(version, minimum)) return false;
        try (var stream = OptionalMixinCompatibility.class.getResourceAsStream("/sablelife-compat/" + mod + ".properties")) {
            var baseline = new Properties();
            if (stream != null) baseline.load(stream);
            var mismatch = changedClass(baseline, name -> OptionalMod.classBytes(mod, name));
            if (mismatch == null) return true;
            LogUtils.getLogger().warn("Sable Life skipped {} {} hooks: {}", mod, version, mismatch);
        } catch (IOException | RuntimeException failure) {
            LogUtils.getLogger().warn("Sable Life could not verify {} {} hooks", mod, version, failure);
        }
        return false;
    }

    static String changedClass(Properties baseline, Function<String, byte[]> reader) {
        if (baseline.isEmpty()) return "Missing compatibility baseline";
        for (var name : baseline.stringPropertyNames().stream().sorted().toList()) {
            var bytes = reader.apply(name);
            if (bytes == null || !Arrays.asList(baseline.getProperty(name).split(",")).contains(fingerprint(bytes))) return name;
        }
        return null;
    }

    static String fingerprint(byte[] bytes) {
        var writer = new ClassWriter(0);
        new ClassReader(bytes).accept(writer, ClassReader.SKIP_DEBUG);
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(writer.toByteArray());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }

    private OptionalMixinCompatibility() {}
}
