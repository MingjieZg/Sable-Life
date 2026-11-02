package dev.sablelife.compat.photomancy;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;
public final class RespawnMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){} public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){return mixin.endsWith(".RespawnBlueprintNbtMixin")?PhotomancyCompatibility.available():PhotomancyCompatibility.diagnosticHook(mixin);}
 public void acceptTargets(Set<String>a,Set<String>b){} public List<String>getMixins(){return null;}
 public void preApply(String n,ClassNode c,String m,IMixinInfo i){} public void postApply(String n,ClassNode c,String m,IMixinInfo i){}
}
