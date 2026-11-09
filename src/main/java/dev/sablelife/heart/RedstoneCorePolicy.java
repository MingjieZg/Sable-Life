package dev.sablelife.heart;

import dev.sablelife.module.*;
import java.util.*;

/** Authoritative capability list shared by menus, legacy disabling and runtime gating. */
public final class RedstoneCorePolicy {
    /** Hard capability boundary. Server configuration may narrow this list, never expand it. */
    public static final java.util.Set<String> OPTIONAL_TYPES=java.util.Set.of("visual_shockwave","distortion_trail","particles","item_display","trigger_sound");
    public static boolean defaultEnabled(String type){return TYPES.contains(type);}
    public static final List<String> TYPES=List.of("move_set","signal_logic","sequence","wireless_redstone","sensor","navigation","auto_facing","signal_turn","gyroscope","hover_anchor","linear_movement","visual_shockwave","distortion_trail","particles","item_display","trigger_sound","block_interaction");
    public static int maxTrigger(String type){return MoveSet.TARGETS.contains(type)?4:3;}
    public static boolean allowed(VirtualModule module){return TYPES.contains(module.type())&&module.value("trigger")>=0&&module.value("trigger")<=maxTrigger(module.type());}
    public static boolean disabledField(VirtualModule module,String key){
        return module.type().equals("linear_movement")&&List.of("linearMode","moveForce","moveObstruction").contains(key)
                ||module.type().equals("move_set")&&key.equals("ignoreConditions")||module.type().equals("sound")&&key.equals("hurt")||module.type().equals("bgm")&&key.equals("bgmStart");
    }
    public static void constrainMovement(VirtualModule module){
        if(!module.type().equals("linear_movement"))return;
        if(module.value("linearMode")!=1)module.edit("linearMode",1,"");
        if(module.value("moveForce")!=0)module.edit("moveForce",0,"");
        if(module.value("moveObstruction")!=0)module.edit("moveObstruction",0,"");
    }
    public static boolean audioKeyAllowed(VirtualModule module,String key){return module!=null&&allowed(module)&&module.soundKey(key)&&!disabledField(module,key);}
    public static void sanitize(HeartBlockEntity core){
        // Retain legacy configuration. Capability checks prevent it from running on this core.
        for(var module:core.modules().all()){
            constrainMovement(module);
            if(!allowed(module))module.edit("enabled",0,"");
        }
    }
    private RedstoneCorePolicy(){}
}
