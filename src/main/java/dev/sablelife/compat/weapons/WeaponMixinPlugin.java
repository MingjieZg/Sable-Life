package dev.sablelife.compat.weapons;

import java.util.*;
import java.nio.file.Files;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

public final class WeaponMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage){}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){
        var result=WeaponMixinContract.inspect(mixin,target,WeaponMixinPlugin::bytes);
        if(!result.compatible()&&!result.reason().equals("Target class is absent"))
            com.mojang.logging.LogUtils.getLogger().warn("Sable Life skipped optional weapon hook {} for {}: {}",mixin,target,result.reason());
        return result.compatible();
    }
    private static byte[] bytes(String name){
        var list=FMLLoader.getLoadingModList();if(list==null)return null;
        for(var mod:list.getMods()){
            try{
                var path=mod.getOwningFile().getFile().findResource(name+".class");
                if(Files.isRegularFile(path))return Files.readAllBytes(path);
            }catch(java.io.IOException ignored){}
        }
        return null;
    }
    public void acceptTargets(Set<String> mine,Set<String> others){}
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
