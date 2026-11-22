package dev.sablelife.compat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

final class OptionalMixinCompatibilityTest {
    @Test void acceptsTheVerifiedBodyWithDifferentDebugMetadata() {
        var verified = new Properties();
        verified.setProperty("Example", OptionalMixinCompatibility.fingerprint(example(1, "Old.java")));
        assertNull(OptionalMixinCompatibility.changedClass(verified, name -> example(1, "New.java")));
        assertEquals("Example", OptionalMixinCompatibility.changedClass(verified, name -> example(2, "New.java")));
        assertEquals("Example", OptionalMixinCompatibility.changedClass(verified, name -> null));
    }

    @Test void acceptsAnIndependentlyVerifiedReplacement() {
        var verified = new Properties();
        var older = OptionalMixinCompatibility.fingerprint(example(1, "Old.java"));
        var newer = OptionalMixinCompatibility.fingerprint(example(2, "New.java"));
        verified.setProperty("Example", older + "," + newer);
        assertNull(OptionalMixinCompatibility.changedClass(verified, name -> example(2, "New.java")));
        assertNotNull(OptionalMixinCompatibility.changedClass(new Properties(), name -> example(1, "Old.java")));
    }

    private static byte[] example(int value, String source) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Example", null, "java/lang/Object", null);
        writer.visitSource(source, null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()I", null, null);
        method.visitCode();
        method.visitLdcInsn(value);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
