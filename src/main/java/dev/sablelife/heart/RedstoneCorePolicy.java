package dev.sablelife.heart;

import dev.sablelife.module.*;
import java.util.*;

/** Authoritative whitelist shared by menus, import sanitation and runtime gating. */
public final class RedstoneCorePolicy {
    public static final List<String> TYPES=List.of("move_set","sequence","wireless_redstone","sensor","navigation","auto_facing","signal_turn","trigger_sound","sound","bgm","item_display");
    public static boolean allowed(VirtualModule module){return TYPES.contains(module.type())&&module.value("trigger")>=0&&module.value("trigger")<=4;}
    public static boolean disabledField(VirtualModule module,String key){return module.type().equals("sound")&&key.equals("hurt")||module.type().equals("bgm")&&key.equals("bgmStart");}
    public static boolean audioKeyAllowed(VirtualModule module,String key){return module!=null&&allowed(module)&&module.soundKey(key)&&!disabledField(module,key);}
    public static void sanitize(HeartBlockEntity core){
        for(var module:core.modules().all())if(!TYPES.contains(module.type()))core.modules().remove(module.id());else if(module.value("trigger")>4)module.edit("trigger",0,"");
    }
    private RedstoneCorePolicy(){}
}
