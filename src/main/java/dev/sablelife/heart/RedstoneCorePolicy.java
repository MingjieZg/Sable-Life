package dev.sablelife.heart;

import dev.sablelife.module.*;
import java.util.*;

/** Authoritative capability list shared by menus, legacy disabling and runtime gating. */
public final class RedstoneCorePolicy {
    /** Hard capability boundary. Server configuration may narrow this list, never expand it. */
    public static final java.util.Set<String> OPTIONAL_TYPES=java.util.Set.of("visual_shockwave","distortion_trail","particles","item_display","trigger_sound");
    public static boolean defaultEnabled(String type){return !OPTIONAL_TYPES.contains(type);}
    public static final List<String> TYPES=List.of("signal_logic","sequence","wireless_redstone","sensor","navigation","auto_facing","signal_turn","gyroscope","hover_anchor","visual_shockwave","distortion_trail","particles","item_display","trigger_sound");
    public static boolean allowed(VirtualModule module){return TYPES.contains(module.type())&&module.value("trigger")>=0&&module.value("trigger")<=3;}
    public static boolean disabledField(VirtualModule module,String key){return module.type().equals("sound")&&key.equals("hurt")||module.type().equals("bgm")&&key.equals("bgmStart");}
    public static boolean audioKeyAllowed(VirtualModule module,String key){return module!=null&&allowed(module)&&module.soundKey(key)&&!disabledField(module,key);}
    public static void sanitize(HeartBlockEntity core){
        // Retain legacy configuration. Capability checks prevent it from running on this core.
        for(var module:core.modules().all())if(!allowed(module))module.edit("enabled",0,"");
    }
    private RedstoneCorePolicy(){}
}
