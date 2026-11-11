package dev.sablelife.module;

import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.api.module.ModuleDefinition.Core;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.RedstoneCorePolicy;
import java.util.List;
import java.util.stream.Stream;

/** Core capabilities are a hard boundary; server switches may only narrow them. */
public final class ModuleAvailability {
    public static boolean enabled(boolean redstoneCore,String type){
        var addon = ModuleTypes.find(type);
        if (addon != null) return addon.cores().contains(redstoneCore ? Core.REDSTONE : Core.HEART)
                && SableLifeServerConfig.addonModuleEnabled(type);
        return (redstoneCore?RedstoneCorePolicy.TYPES:VirtualModule.AVAILABLE_TYPES).contains(type)
                &&SableLifeServerConfig.moduleEnabled(redstoneCore,type);
    }
    public static boolean enabled(HeartBlockEntity heart,String type){
        if (heart.isSurvivalCore()) {
            var addon = ModuleTypes.find(type);
            if (addon != null) return addon.cores().contains(Core.SURVIVAL) && SableLifeServerConfig.addonModuleEnabled(type);
        }
        return heart.isSurvivalCore()?SableLifeServerConfig.survivalModuleEnabled(type):enabled(heart.isControlCore(),type);
    }
    public static boolean enabled(HeartBlockEntity heart,VirtualModule module){return module!=null&&enabled(heart,module.type());}
    public static List<String> available(HeartBlockEntity heart){
        return types(VirtualModule.AVAILABLE_TYPES).filter(type->enabled(heart,type)).toList();
    }
    public static long mask(HeartBlockEntity heart){
        long mask=0;
        for(int i=0;i<VirtualModule.AVAILABLE_TYPES.size();i++)if(enabled(heart,VirtualModule.AVAILABLE_TYPES.get(i)))mask|=1L<<i;
        return mask;
    }
    public static List<String> available(boolean redstoneCore){
        return types(redstoneCore?RedstoneCorePolicy.TYPES:VirtualModule.AVAILABLE_TYPES).filter(t->enabled(redstoneCore,t)).toList();
    }
    public static boolean authorable(String type) {
        return VirtualModule.AVAILABLE_TYPES.contains(type) || ModuleTypes.find(type) != null;
    }
    private static Stream<String> types(List<String> builtIn) {
        return Stream.concat(builtIn.stream(), ModuleTypes.extensionIds());
    }
    private ModuleAvailability(){}
}
