package dev.sablelife.attack;

import dev.sablelife.SableLifeServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import java.util.*;

/** Account existing projectiles; only new spawns are capped, never saved or already flying attacks. */
public final class ProjectileBudget {
    private record Entry(UUID owner) {}
    private static final class State {
        final Map<UUID,Entry> entities=new HashMap<>();
        final Map<UUID,Integer> owners=new HashMap<>();
    }
    private static final Map<ServerLevel,State> levels=new WeakHashMap<>();
    public static void joined(EntityJoinLevelEvent event) {
        if(event.isCanceled()||!(event.getLevel() instanceof ServerLevel level)
                ||!(event.getEntity() instanceof ModuleShot||event.getEntity() instanceof ModuleArrow))return;
        var entity=event.getEntity();var s=levels.computeIfAbsent(level,k->new State());
        if(s.entities.containsKey(entity.getUUID()))return;
        var data=entity.getPersistentData();UUID owner=data.hasUUID("SableLifeAttackOwner")?data.getUUID("SableLifeAttackOwner"):null;
        if(!event.loadedFromDisk()&&(s.entities.size()>=SableLifeServerConfig.maximumProjectilesPerWorld()
                ||owner!=null&&s.owners.getOrDefault(owner,0)>=SableLifeServerConfig.maximumProjectilesPerCreature())){event.setCanceled(true);return;}
        s.entities.put(entity.getUUID(),new Entry(owner));if(owner!=null)s.owners.merge(owner,1,Integer::sum);
    }
    public static void left(EntityLeaveLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level))return;var s=levels.get(level);if(s==null)return;
        var old=s.entities.remove(event.getEntity().getUUID());if(old!=null&&old.owner!=null)s.owners.computeIfPresent(old.owner,(id,count)->count<=1?null:count-1);
    }
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event){levels.clear();}
    public static int count(ServerLevel level){var s=levels.get(level);return s==null?0:s.entities.size();}
    private ProjectileBudget(){}
}
