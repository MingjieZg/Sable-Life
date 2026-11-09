package dev.sablelife;

import com.electronwill.nightconfig.toml.TomlParser;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

/** Keep each generated server setting and its comments visually separated. */
@EventBusSubscriber(modid=SableLife.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class ServerConfigSpacing {
    private static final Pattern BOUNDARY=Pattern.compile(
            "(?m)^([\\t ]*(?:[A-Za-z0-9_-]+[\\t ]*=.*|\\][\\t ]*(?:#.*)?))(\\r?\\n)(?=[\\t ]*(?:#|\\[|[A-Za-z0-9_-]+[\\t ]*=))");

    @SubscribeEvent public static void loaded(ModConfigEvent.Loading event){formatFile(event.getConfig());}
    @SubscribeEvent public static void reloaded(ModConfigEvent.Reloading event){formatFile(event.getConfig());}

    public static String format(String original){
        String formatted=compactModules(BOUNDARY.matcher(original).replaceAll("$1$2$2"));
        if(formatted.equals(original))return original;
        var parser=new TomlParser();
        // A multiline literal may contain assignments. Never change its value.
        return parser.parse(original).valueMap().equals(parser.parse(formatted).valueMap())?formatted:original;
    }

    private static String compactModules(String text){
        var lines=text.split("(?<=\n)");
        var result=new StringBuilder();
        boolean modules=false;
        String previous="";
        for(int i=0;i<lines.length;i++){
            String stripped=lines[i].strip();
            if(stripped.startsWith("["))modules=java.util.List.of("[modules.heartCore]","[modules.lifeCore]",
                    "[modules.survivalHeartCore]","[modules.survivalLifeCore]","[modules.redstoneCore]").contains(stripped);
            if(modules&&stripped.isEmpty()&&previous.matches("[A-Za-z0-9_-]+[ \t]*=[ \t]*(true|false)")){
                int next=i+1;
                while(next<lines.length&&lines[next].isBlank())next++;
                if(next<lines.length&&lines[next].strip().matches("[A-Za-z0-9_-]+[ \t]*=[ \t]*(true|false)"))continue;
            }
            result.append(lines[i]);
            if(!stripped.isEmpty())previous=stripped;
        }
        return result.toString();
    }

    private static synchronized void formatFile(ModConfig config){
        if(config.getSpec()!=SableLifeServerConfig.LOADER_SPEC)return;
        java.nio.file.Path path;
        try {
            path=config.getFullPath();
        } catch (IllegalStateException nonFileConfig) {
            // Remote SERVER configs are synchronized in memory and have no local file.
            // FML throws here rather than returning null; formatting must not break login.
            return;
        }
        if(!Files.isRegularFile(path))return;
        java.nio.file.Path temporary=null;
        try{
            String original=Files.readString(path);
            String formatted=format(original);
            if(formatted.equals(original))return;
            temporary=Files.createTempFile(path.getParent(),"sablelife-spacing-",".tmp");
            Files.writeString(temporary,formatted);
            if(!Files.readString(path).equals(original))return;
            Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }catch(IOException|RuntimeException failure){
            com.mojang.logging.LogUtils.getLogger().warn("Could not apply Sable Life server config spacing",failure);
        }finally{
            if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}
        }
    }

    private ServerConfigSpacing(){}
}
