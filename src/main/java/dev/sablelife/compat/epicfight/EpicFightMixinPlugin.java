package dev.sablelife.compat.epicfight;

import dev.sablelife.compat.OptionalMixinCompatibility;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;

/** Keeps the native strike and guard hooks together under one compatibility decision. */
public final class EpicFightMixinPlugin implements IMixinConfigPlugin {
    private static final class Compatibility {
        static final boolean SUPPORTED = OptionalMixinCompatibility.supported("epicfight", "21.17.3.1");
    }

    public static boolean supported() {
        return Compatibility.SUPPORTED;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName) {
        return supported();
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others) {}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String name,ClassNode node,String mixin,IMixinInfo info) {}
    @Override public void postApply(String name,ClassNode node,String mixin,IMixinInfo info) {}
}
