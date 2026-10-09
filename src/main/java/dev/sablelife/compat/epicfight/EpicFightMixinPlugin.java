package dev.sablelife.compat.epicfight;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;

/** Unsupported versions keep their own behavior and never receive unverified method injections. */
public final class EpicFightMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName) {
        var list=FMLLoader.getLoadingModList();
        return list!=null&&list.getMods().stream().anyMatch(m->m.getModId().equals("epicfight")&&m.getVersion().toString().equals("21.17.3.1"));
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others) {}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String name,ClassNode node,String mixin,IMixinInfo info) {}
    @Override public void postApply(String name,ClassNode node,String mixin,IMixinInfo info) {}
}
