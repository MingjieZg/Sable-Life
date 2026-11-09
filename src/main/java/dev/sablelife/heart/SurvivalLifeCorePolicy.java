package dev.sablelife.heart;

import dev.sablelife.module.VirtualModule;
import java.util.List;
import java.util.Set;

public final class SurvivalLifeCorePolicy {
    private static final Set<String> UNAVAILABLE=Set.of("command","loot","arena","blueprint_spawn");
    private static final Set<String> FIXED_SETTINGS=Set.of("breaking","miningDrops","lifeAttacks","delay","xp","xpLevels","killHeal",
            "survivalInteraction","forceChunkLoading");
    public static final List<String> TYPES=VirtualModule.AVAILABLE_TYPES.stream().filter(type->!UNAVAILABLE.contains(type)).toList();

    public static boolean allowsSetting(String key){
        return !FIXED_SETTINGS.contains(key)
                &&!key.startsWith("voidDeath")&&!key.startsWith("limbCleanup")&&!key.startsWith("vanish")
                &&!key.startsWith("crush")&&!key.startsWith("defense");
    }

    public static boolean allowsCast(VirtualModule module){
        return targetModeAllowed(module,"releasePosition",module.value("releasePosition"))
                &&targetModeAllowed(module,"quakeOrigin",module.value("quakeOrigin"))
                &&targetModeAllowed(module,"strikeMode",module.value("strikeMode"));
    }

    public static boolean targetModeAllowed(VirtualModule module,String key,double value){
        // Restrict target-position origins, not projectiles aimed from their marked muzzle.
        return switch(key){
            case "releasePosition" -> !Set.of("sphere_blast","pull","freeze","pulse").contains(module.type())||value==0;
            case "quakeOrigin" -> !module.type().equals("quake")||value==0;
            case "strikeMode" -> !module.type().equals("ground_strike")||value!=2;
            default -> true;
        };
    }

    public static boolean visibleField(VirtualModule module,String key){
        return !key.equals("effects")&&!key.equals("castTargetRange")&&(!key.equals("targetMultiple")
                ||!Set.of("sphere_blast","pull","freeze","pulse","quake","ground_strike").contains(module.type()))
                &&(key.equals("strikeMode")||targetModeAllowed(module,key,1));
    }

    public static void constrain(VirtualModule module){
        if(module.type().equals("armor"))for(var row:module.armorRules().rows())
            if(row.modifier() < -dev.sablelife.module.ModuleArmor.SURVIVAL_MAX_REDUCTION)
                row.edit("modifier",-dev.sablelife.module.ModuleArmor.SURVIVAL_MAX_REDUCTION);
        for(var key:List.of("releasePosition","quakeOrigin","strikeMode"))
            if(!targetModeAllowed(module,key,module.value(key)))module.edit(key,0,"");
    }

    public static void sanitize(HeartBlockEntity core){core.modules().all().forEach(SurvivalLifeCorePolicy::constrain);}

    private SurvivalLifeCorePolicy(){}
}
