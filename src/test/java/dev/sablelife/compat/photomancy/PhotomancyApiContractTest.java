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
}
