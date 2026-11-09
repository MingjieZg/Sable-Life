package dev.sablelife.ownership;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.sablelife.attack.ContactBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.*;
import org.joml.Quaterniond;
import java.util.List;

/** Optional-API-free destination and outline geometry shared by preview and queued placement. */
public final class BlueprintSpawnPlacement {
    public record Destination(ResourceLocation dimension,Vec3 position,Quaterniond rotation){}
    public record Geometry(List<Vec3> fixed,List<Vec3> oriented,Vec3 fixedForward){
        public AABB bounds(Destination destination){
            AABB result=null;
            for(var corner:destination.rotation()==null?fixed:oriented){
                var point=(destination.rotation()==null?corner:ContactBox.rotate(destination.rotation(),corner)).add(destination.position());
                var box=new AABB(point,point);result=result==null?box:result.minmax(box);
            }
            if(result==null)throw new IllegalArgumentException("empty_bounds");return result;
        }
    }
    public static boolean follows(VirtualModule module){return module.type().equals("blueprint_spawn")&&module.value("spawnPosition")==1;}
    public static Destination resolve(HeartBlockEntity core,VirtualModule module){
        if(core.getLevel()==null||!module.type().equals("blueprint_spawn"))return null;
        var dimension=core.getLevel().dimension().location();
        var offsets=new Vec3(module.value("spawnOffsetX"),module.value("spawnOffsetY"),module.value("spawnOffsetZ"));
        var angles=new Quaterniond().rotateY(Math.toRadians(module.value("spawnYaw"))).rotateX(Math.toRadians(module.value("spawnPitch"))).rotateZ(Math.toRadians(module.value("spawnRoll")));
        if(!follows(module))return module.arenaCenter()!=null&&dimension.toString().equals(module.arenaDimension())?new Destination(dimension,module.arenaCenter().add(offsets),module.value("spawnYaw")==0&&module.value("spawnPitch")==0&&module.value("spawnRoll")==0?null:angles):null;
        if(module.anchors().size()!=1)return null;var anchor=module.anchors().getFirst();if(!anchor.status(core).equals("valid"))return null;
        var body=anchor.body(core);var point=anchor.world(core,false);if(body==null||point==null)return null;
        var direction=ModuleCombat.baseDirection(core,module);
        var local=ContactBox.rotate(new Quaterniond(body.logicalPose().orientation()).conjugate(),direction);
        // Up/down faces control translation, not a forced 90-degree blueprint pitch.
        double heading=local.x*local.x+local.z*local.z<1e-8?0:Math.atan2(local.x,local.z);
        var rotation=new Quaterniond(body.logicalPose().orientation()).rotateY(heading).mul(angles).normalize();
        return new Destination(dimension,point.add(direction.scale(module.value("startDistance"))).add(ContactBox.rotate(new Quaterniond(body.logicalPose().orientation()),offsets)),rotation);
    }
    private BlueprintSpawnPlacement(){}
}
