package dev.sablelife.module;

import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.RedstoneCorePolicy;
import java.util.List;

/** Core capabilities are a hard boundary; server switches may only narrow them. */
public final class ModuleAvailability {
    public static boolean enabled(boolean redstoneCore,String type){
        return (redstoneCore?RedstoneCorePolicy.TYPES:VirtualModule.AVAILABLE_TYPES).contains(type)
                &&SableLifeServerConfig.moduleEnabled(redstoneCore,type);
    }
    public static boolean enabled(HeartBlockEntity heart,String type){
        return heart.isSurvivalCore()?SableLifeServerConfig.survivalModuleEnabled(type):enabled(heart.isControlCore(),type);
    }
    public static boolean enabled(HeartBlockEntity heart,VirtualModule module){return module!=null&&enabled(heart,module.type());}
    public static List<String> available(HeartBlockEntity heart){
        return VirtualModule.AVAILABLE_TYPES.stream().filter(type->enabled(heart,type)).toList();
    }
    public static long mask(HeartBlockEntity heart){
        long mask=0;
        for(int i=0;i<VirtualModule.AVAILABLE_TYPES.size();i++)if(enabled(heart,VirtualModule.AVAILABLE_TYPES.get(i)))mask|=1L<<i;
        return mask;
    }
    public static List<String> available(boolean redstoneCore){
        return (redstoneCore?RedstoneCorePolicy.TYPES:VirtualModule.AVAILABLE_TYPES).stream().filter(t->enabled(redstoneCore,t)).toList();
    }
    private ModuleAvailability(){}
}
