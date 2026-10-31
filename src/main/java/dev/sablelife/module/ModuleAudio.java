package dev.sablelife.module;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
public final class ModuleAudio {
    public static void play(ServerLevel level,VirtualModule module,String key,Vec3 point){ play(level,module.audioId(),module.soundTimeline(key),point); }
    public static void play(ServerLevel level,UUID id,dev.sablelife.sound.SoundTimeline timeline,Vec3 point){
        for(var row:timeline.rows()) if(row.enabled()&&row.volume()>0) for(var player:level.players())
            if(dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,player.position()).distanceToSqr(point)<=row.radius()*row.radius())
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ModuleNetworking.ModuleSound(id,row.sound(),false,point,row.volume(),row.pitch(),row.radius(),row.start(),row.duration(),row.fade(),timeline.authored()));
    }
    public static void stop(ServerLevel level,UUID id){for(var player:level.players())net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ModuleNetworking.ModuleSound(id,"",true,Vec3.ZERO,0,1,0,0,0,true));}

    public static void play(ServerLevel level, UUID id, String sound, Vec3 point, double volume, double radius) {
        if (sound.isEmpty() || volume <= 0) return;
        for (var player : level.players()) if (player.position().distanceToSqr(point) <= radius * radius)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ModuleNetworking.ModuleSound(id, sound, false, point, (float) volume, 1, (float) radius));
    }
}
