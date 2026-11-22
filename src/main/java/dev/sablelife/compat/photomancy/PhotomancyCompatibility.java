package dev.sablelife.compat.photomancy;

import com.mojang.logging.LogUtils;
import dev.sablelife.compat.ModVersion;
import dev.sablelife.compat.OptionalMod;
import net.neoforged.fml.loading.FMLLoader;
import java.nio.file.Files;

/** One shared decision for early mixin selection, mapper registration and Arena controls. */
public final class PhotomancyCompatibility {
    private static final String MOD_ID = "sable_schematic_api";
    private static final String MINIMUM_VERSION = "1.0.1";
    private static volatile PhotomancyApiContract.Result result;
    public static boolean available(){return status().compatible();}
    public static synchronized PhotomancyApiContract.Result status(){
        if(result!=null)return result;
        var list=FMLLoader.getLoadingModList();
        if(list==null)return new PhotomancyApiContract.Result(false,"Mod discovery is not ready");
        if(list.getModFileById(MOD_ID)==null)return result=new PhotomancyApiContract.Result(false,"Sable Photomancy is not installed");
        var version = OptionalMod.version(MOD_ID);
        if (ModVersion.atLeast(version, MINIMUM_VERSION)) {
            result = PhotomancyApiContract.inspect(PhotomancyCompatibility::bridgeBytes, PhotomancyCompatibility::apiBytes);
        } else {
            result = new PhotomancyApiContract.Result(false, "Requires Sable Photomancy " + MINIMUM_VERSION + " or newer");
        }
        if (result.compatible()) LogUtils.getLogger().info("Sable Life Photomancy {} interface check passed", version);
        else LogUtils.getLogger().warn("Sable Life skipped Photomancy {} integration ({})", version, result.reason());
        return result;
    }
    public static boolean diagnosticHook(String mixin){return available()&&PhotomancyApiContract.diagnosticHook(mixin,PhotomancyCompatibility::apiBytes,PhotomancyCompatibility::bridgeBytes);}
    static byte[] bridgeBytes(String name){try(var stream=PhotomancyCompatibility.class.getResourceAsStream("/"+name+".class")){return stream==null?null:stream.readAllBytes();}catch(java.io.IOException e){return null;}}
    static byte[] apiBytes(String name){
        var list=FMLLoader.getLoadingModList();var file=list==null?null:list.getModFileById(MOD_ID);
        if(file==null)return null;
        try{var path=file.getFile().findResource(name+".class");if(Files.isRegularFile(path))return Files.readAllBytes(path);}catch(java.io.IOException ignored){}
        // Inherited JDK members can be referenced with a blueprint class as their owner.
        if(name.startsWith("java/"))try(var stream=Object.class.getResourceAsStream("/"+name+".class")){return stream==null?null:stream.readAllBytes();}catch(java.io.IOException ignored){}
        return null;
    }
    private PhotomancyCompatibility(){}
}
