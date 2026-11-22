package dev.sablelife.compat.ironspells;
import dev.sablelife.compat.OptionalMixinCompatibility;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;
/** Applies the spell hooks together when the native spell code matches the verified contract. */
public final class IronSpellsMixinPlugin implements IMixinConfigPlugin {
 private Boolean supported;
 @Override public void onLoad(String p){}
 @Override public String getRefMapperConfig(){return null;}
 @Override public boolean shouldApplyMixin(String target,String mixin) {
     if (supported == null) supported = OptionalMixinCompatibility.supported("irons_spellbooks", "1.21.1-3.16.3");
     return supported;
 }
 @Override public void acceptTargets(Set<String> a,Set<String> b){}
 @Override public List<String> getMixins(){return null;}
 @Override public void preApply(String n,ClassNode c,String m,IMixinInfo i){}
 @Override public void postApply(String n,ClassNode c,String m,IMixinInfo i){}
}
