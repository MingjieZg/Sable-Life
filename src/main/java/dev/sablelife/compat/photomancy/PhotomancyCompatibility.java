package dev.sablelife.compat.photomancy;

import net.neoforged.fml.loading.FMLLoader;
import java.nio.file.Files;

/** One shared decision for early mixin selection, mapper registration and Arena controls. */
public final class PhotomancyCompatibility {
    private static volatile PhotomancyApiContract.Result result;
    public static boolean available(){return status().compatible();}
    public static synchronized PhotomancyApiContract.Result status(){
        if(result!=null)return result;
        var list=FMLLoader.getLoadingModList();
        if(list==null)return new PhotomancyApiContract.Result(false,"Mod discovery is not ready");
        if(list.getModFileById("sable_schematic_api")==null)return result=new PhotomancyApiContract.Result(false,"Sable Photomancy is not installed");
        result=PhotomancyApiContract.inspect(PhotomancyCompatibility::bridgeBytes,PhotomancyCompatibility::apiBytes);
        if(result.compatible())com.mojang.logging.LogUtils.getLogger().info("Sable Life Photomancy interface check passed (version-independent)");
        else com.mojang.logging.LogUtils.getLogger().warn("Sable Life Photomancy integration disabled: {}",result.reason());
        return result;
    }
    public static boolean diagnosticHook(String mixin){return available()&&PhotomancyApiContract.diagnosticHook(mixin,PhotomancyCompatibility::apiBytes,PhotomancyCompatibility::bridgeBytes);}
    static byte[] bridgeBytes(String name){try(var stream=PhotomancyCompatibility.class.getResourceAsStream("/"+name+".class")){return stream==null?null:stream.readAllBytes();}catch(java.io.IOException e){return null;}}
    static byte[] apiBytes(String name){
        var list=FMLLoader.getLoadingModList();var file=list==null?null:list.getModFileById("sable_schematic_api");
        if(file==null)return null;
        try{var path=file.getFile().findResource(name+".class");if(Files.isRegularFile(path))return Files.readAllBytes(path);}catch(java.io.IOException ignored){}
        // Inherited JDK members can be referenced with a blueprint class as their owner.
        if(name.startsWith("java/"))try(var stream=Object.class.getResourceAsStream("/"+name+".class")){return stream==null?null:stream.readAllBytes();}catch(java.io.IOException ignored){}
        return null;
    }
    private PhotomancyCompatibility(){}
}
