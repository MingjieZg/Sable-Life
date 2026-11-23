package dev.sablelife.compat.photomancy;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class PhotomancyApiContractTest {
    private static byte[] own(String name){try(var in=PhotomancyApiContractTest.class.getResourceAsStream("/"+name+".class")){return in==null?null:in.readAllBytes();}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    private static Map<String,byte[]> provider() throws Exception {
        var classes=new HashMap<String,byte[]>();
        try(var jar=new ZipFile(Path.of(".dev/mods/sable-photomancy-1.0.1.jar").toFile())){
            for(var entry:Collections.list(jar.entries()))if(entry.getName().endsWith(".class"))
                try(var in=jar.getInputStream(entry)){classes.put(entry.getName().replaceFirst("\\.class$",""),in.readAllBytes());}
        }
        return classes;
    }
    private static PhotomancyApiContract.Result check(Map<String,byte[]> provider){
        return PhotomancyApiContract.inspect(PhotomancyApiContractTest::own,name->provider.containsKey(name)?provider.get(name):name.startsWith("java/")?own(name):null);
    }
    private static void change(Map<String,byte[]> provider,String name,Consumer<ClassNode> edit){
        var node=new ClassNode();new ClassReader(provider.get(name)).accept(node,0);edit.accept(node);
        var writer=new ClassWriter(0);node.accept(writer);provider.put(name,writer.toByteArray());
    }
    @Test void originalApiAndCompatibleAdditionsPassWithoutAnyVersionComparison() throws Exception {
        var api=provider();assertTrue(check(api).compatible(),check(api).reason());
        // A future release can add API without breaking the existing bridge contract.
        change(api,PhotomancyApiContract.API+"blueprint/SableBlueprint",node->{
            var method=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"futureExtension","()V",null,null);
            method.instructions.add(new InsnNode(Opcodes.RETURN));node.methods.add(method);
        });
        assertTrue(check(api).compatible(),check(api).reason());
    }
    @Test void absentModAndRemovedRequiredMethodDisableTheBridge() throws Exception {
        assertFalse(check(Map.of()).compatible());
        var api=provider();change(api,PhotomancyApiContract.API+"api/blueprint/BlueprintPlaceSession",node->node.methods.removeIf(m->m.name.equals("mapSubLevel")));
        assertFalse(check(api).compatible());assertTrue(check(api).reason().contains("mapSubLevel"));
    }
    @Test void changedReturnTypeOrStaticContractIsRejected() throws Exception {
        var api=provider();change(api,PhotomancyApiContract.API+"blueprint/SableBlueprint",node->node.methods.stream().filter(m->m.name.equals("blockCount")).forEach(m->m.desc="()J"));
        assertFalse(check(api).compatible());
        api=provider();change(api,PhotomancyApiContract.API+"blueprint/SableBlueprintExporter",node->node.methods.stream().filter(m->m.name.equals("exportSelected")).forEach(m->m.access&=~Opcodes.ACC_STATIC));
        assertFalse(check(api).compatible());
    }
    @Test void newAbstractCallbackAndMissingNbtHookAreRejected() throws Exception {
        var api=provider();change(api,PhotomancyApiContract.API+"api/blueprint/SableBlueprintEvent",node->node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT,"newRequiredCallback","()V",null,null)));
        assertFalse(check(api).compatible());assertTrue(check(api).reason().contains("newRequiredCallback"));
        api=provider();change(api,PhotomancyApiContract.API+"survival/BlueprintNbtPolicies",node->node.methods.removeIf(m->m.name.equals("resolve")));
        assertFalse(check(api).compatible());
    }
    @Test void removedDefaultPlacementCallbackIsNotSilentlyAccepted() throws Exception {
        var api=provider();change(api,PhotomancyApiContract.API+"api/blueprint/SableBlueprintEvent",node->node.methods.removeIf(m->m.name.equals("onPlaceBeforeBlocks")));
        assertFalse(check(api).compatible());assertTrue(check(api).reason().contains("onPlaceBeforeBlocks"));
    }
    @Test void diagnosticInternalsCanChangeWithoutDisablingRespawn() throws Exception {
        var api=provider();change(api,PhotomancyApiContract.API+"blueprint/tool/BlueprintToolService",node->node.methods.removeIf(m->m.name.equals("loadBytes")));
        assertTrue(check(api).compatible(),check(api).reason());
        assertFalse(PhotomancyApiContract.diagnosticHook("dev.sablelife.mixin.BlueprintToolFailureDiagnosticsMixin",api::get,PhotomancyApiContractTest::own));
    }

    @Test void fileLoadDiagnosticRequiresTheNativeExceptionMessageCall() throws Exception {
        var api = provider();
        var mixin = "dev.sablelife.mixin.BlueprintFileLoadDiagnosticsMixin";
        assertTrue(PhotomancyApiContract.diagnosticHook(mixin, api::get, PhotomancyApiContractTest::own));
        change(api, PhotomancyApiContract.API + "blueprint/tool/BlueprintToolService", node -> {
            for (var method : node.methods) if (method.name.equals("load")) {
                for (var insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call && call.owner.equals("java/lang/Exception")
                            && call.name.equals("getMessage")) call.owner = "java/lang/Throwable";
                }
            }
        });
        assertFalse(PhotomancyApiContract.diagnosticHook(mixin, api::get, PhotomancyApiContractTest::own));
        assertTrue(check(api).compatible(), check(api).reason());
    }

    @Test void fileLoadDiagnosticTargetsOnlyTheVerifiedOverload() throws Exception {
        var api = provider();
        var mixin = "dev.sablelife.mixin.BlueprintFileLoadDiagnosticsMixin";
        change(api, PhotomancyApiContract.API + "blueprint/tool/BlueprintToolService", node -> {
            var overload = node.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "load",
                    "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", null, null);
            overload.visitTypeInsn(Opcodes.NEW, "java/lang/Exception");
            overload.visitInsn(Opcodes.DUP);
            overload.visitVarInsn(Opcodes.ALOAD, 0);
            overload.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Exception", "<init>", "(Ljava/lang/String;)V", false);
            overload.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Exception", "getMessage", "()Ljava/lang/String;", false);
            overload.visitInsn(Opcodes.ARETURN);
            overload.visitMaxs(3, 2);
            overload.visitEnd();
        });
        assertTrue(PhotomancyApiContract.diagnosticHook(mixin, api::get, PhotomancyApiContractTest::own));
        var classFile = new ClassNode();
        new ClassReader(own(mixin.replace('.', '/'))).accept(classFile, 0);
        var handler = classFile.methods.stream().filter(method -> method.name.equals("sablelife$logFileLoadFailure"))
                .findFirst().orElseThrow();
        var wrap = handler.visibleAnnotations.stream().filter(annotation -> annotation.desc.endsWith("/WrapOperation;"))
                .findFirst().orElseThrow();
        assertEquals(List.of(PhotomancyApiContract.FILE_LOAD), wrap.values.get(wrap.values.indexOf("method") + 1));
    }

    @Test void actorHooksRequireOneExactNativePlacementCall() throws Exception {
        var api = provider();
        record Hook(String mixin, String owner, String method, String nativeCall) {}
        var hooks = List.of(
                new Hook("BlueprintCommandFactionActorMixin", "command/SableBlueprintCommands",
                        "loadSchematic", "load"),
                new Hook("BlueprintToolFactionActorMixin", "network/BlueprintToolServerActions",
                        "handleLoadBlueprint", "loadBytes"));
        for (var hook : hooks) {
            String mixin = "dev.sablelife.mixin." + hook.mixin();
            String owner = PhotomancyApiContract.API + hook.owner();
            byte[] original = api.get(owner);
            assertTrue(PhotomancyApiContract.diagnosticHook(mixin, api::get, PhotomancyApiContractTest::own));
            change(api, owner, node -> {
                var method = node.methods.stream().filter(m -> m.name.equals(hook.method())).findFirst().orElseThrow();
                var call = Arrays.stream(method.instructions.toArray())
                        .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                        .filter(i -> i.owner.endsWith("/BlueprintToolService") && i.name.equals(hook.nativeCall()))
                        .findFirst().orElseThrow();
                method.instructions.insert(call, new MethodInsnNode(call.getOpcode(), call.owner, call.name,
                        call.desc, call.itf));
            });
            assertFalse(PhotomancyApiContract.diagnosticHook(mixin, api::get, PhotomancyApiContractTest::own));
            assertTrue(check(api).compatible(), check(api).reason());
            api.put(owner, original);
        }
    }
}
