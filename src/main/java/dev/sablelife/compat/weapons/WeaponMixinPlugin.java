package dev.sablelife.compat.weapons;

import dev.sablelife.compat.ModVersion;
import dev.sablelife.compat.OptionalMod;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

public final class WeaponMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage){}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){
        if(mixin.startsWith("dev.sablelife.mixin.Ntgl")&&!supported("ntgl","3.2.0"))return false;
        if (mixin.endsWith(".TaczBreachingDamageMixin")
                && (!supported("tacz", "1.1.8-hotfix-r7") || !supported("tacz_tactical_breaching", "1.0.5"))) return false;
        if(mixin.endsWith(".CgsBlazeProjectileMixin")&&(!supported("ntgl","3.2.0")||!supported("cgs","1.4.9")
                ||!WeaponMixinContract.inspect("NtglProjectileMixin","com.nukateam.ntgl.common.foundation.entity.ProjectileEntity",
                        OptionalMod::classBytes).compatible()))return false;
        var result=WeaponMixinContract.inspect(mixin,target,OptionalMod::classBytes);
        if(!result.compatible()&&!result.reason().equals("Target class is absent"))
            com.mojang.logging.LogUtils.getLogger().warn("Sable Life skipped optional weapon hook {} for {}: {}",mixin,target,result.reason());
        return result.compatible();
    }
    private static boolean supported(String id, String minimum) {
        return ModVersion.atLeast(OptionalMod.version(id), minimum);
    }

    public void acceptTargets(Set<String> mine,Set<String> others){}
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
