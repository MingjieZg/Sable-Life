package dev.sablelife.compat.ironspells;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;
/** Only the inspected 1.21.1 release receives method injections. */
public final class IronSpellsMixinPlugin implements IMixinConfigPlugin {
 @Override public void onLoad(String p){}
 @Override public String getRefMapperConfig(){return null;}
 @Override public boolean shouldApplyMixin(String target,String mixin){var list=FMLLoader.getLoadingModList();return list!=null&&list.getMods().stream().anyMatch(m->m.getModId().equals("irons_spellbooks")&&m.getVersion().toString().equals("1.21.1-3.16.3"));}
 @Override public void acceptTargets(Set<String> a,Set<String> b){}
 @Override public List<String> getMixins(){return null;}
 @Override public void preApply(String n,ClassNode c,String m,IMixinInfo i){}
 @Override public void postApply(String n,ClassNode c,String m,IMixinInfo i){}
}
