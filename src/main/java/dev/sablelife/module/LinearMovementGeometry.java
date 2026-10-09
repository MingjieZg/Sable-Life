package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import org.joml.Vector3d;
import org.joml.Quaterniond;
import java.util.*;

/** Conservative full-limb bounds prevent terrain penetration, including swept Travel steps. */
public final class LinearMovementGeometry {
    public record Body(ServerSubLevel body, Vector3d position, Quaterniond rotation, AABB box) {}
    /** Small fixed contact skin: a one-block bound becomes 0.9, without shrinking large machines by 10%. */
    public static AABB contactBounds(AABB box) {
        return box.deflate(Math.min(.05,box.getXsize()*.25),Math.min(.05,box.getYsize()*.25),Math.min(.05,box.getZsize()*.25));
    }
    public static Vec3 direction(HeartBlockEntity heart,VirtualModule module){
        var forward=ModuleCombat.direction(heart,module);var anchor=module.anchors().getFirst();
        var bodyUp=dev.sablelife.attack.ContactBox.rotate(new Quaterniond(anchor.body(heart).logicalPose().orientation()),new Vec3(0,1,0));
        var right=forward.cross(bodyUp);if(right.lengthSqr()<.001)right=dev.sablelife.attack.ContactBox.rotate(new Quaterniond(anchor.body(heart).logicalPose().orientation()),new Vec3(1,0,0));
        right=right.normalize();var up=right.cross(forward).normalize();double yaw=Math.toRadians(module.value("moveHorizontal")),pitch=Math.toRadians(module.value("moveVertical"));
        return forward.scale(Math.cos(yaw)*Math.cos(pitch)).add(right.scale(Math.sin(yaw)*Math.cos(pitch))).add(up.scale(Math.sin(pitch))).normalize();
    }
    public static List<Body> capture(HeartBlockEntity heart){
        var root=Sable.HELPER.getContaining(heart.getLevel(),heart.getBlockPos());if(root==null||root.isRemoved())return List.of();
        var result=new ArrayList<Body>();
        for(var limb:SubLevelHelper.getConnectedChain(root)){
            if(!(limb instanceof ServerSubLevel server)||limb.isRemoved()||result.size()>=64)return List.of();
            limb.updateBoundingBox();var b=limb.boundingBox();
            result.add(new Body(server,new Vector3d(limb.logicalPose().position()),new Quaterniond(limb.logicalPose().orientation()),contactBounds(new AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()))));
        }return List.copyOf(result);
    }
    public static boolean connected(HeartBlockEntity heart,List<Body> bodies){var current=capture(heart);return current.size()==bodies.size()&&current.stream().allMatch(b->bodies.stream().anyMatch(old->old.body()==b.body()));}
    public static boolean clear(ServerLevel level,List<Body> bodies,Vec3 from,Vec3 to,boolean swept){
        if(bodies.isEmpty())return false;
        var container=SubLevelContainer.getContainer(level);
        long checked=0;
        for(var body:bodies){
            var box=swept?body.box().move(from).expandTowards(to.subtract(from)):body.box().move(to);
            if(box.minY<level.getMinBuildHeight()||box.maxY>level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(box))return false;
            int x0=(int)Math.floor(box.minX),x1=(int)Math.floor(box.maxX),y0=(int)Math.floor(box.minY),y1=(int)Math.floor(box.maxY),z0=(int)Math.floor(box.minZ),z1=(int)Math.floor(box.maxZ);
            checked+=(long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1);if(checked>262144)return false;
            for(int x=x0>>4;x<=x1>>4;x++)for(int z=z0>>4;z<=z1>>4;z++)if(!level.hasChunk(x,z))return false;
            for(var pos:BlockPos.betweenClosed(x0,y0,z0,x1,y1,z1)){
                var state=level.getBlockState(pos);if(state.isAir())continue;
                for(var shape:state.getCollisionShape(level,pos).toAabbs())if(shape.move(pos).intersects(box))return false;
            }
            if(container!=null)for(var other:container.getAllSubLevels()){
                if(other.isRemoved()||bodies.stream().anyMatch(own->own.body()==other))continue;
                other.updateBoundingBox();var b=other.boundingBox();if(new AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()).intersects(box))return false;
            }
        }return true;
    }
    /** Force mode skips colliders but never loads chunks or moves outside the world's valid destination. */
    public static boolean loadedDestination(ServerLevel level,List<Body> bodies){
        if(bodies.isEmpty())return false;
        for(var b:bodies){var box=b.box();if(box.minY<level.getMinBuildHeight()||box.maxY>level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(box))return false;
            int x0=((int)Math.floor(box.minX))>>4,x1=((int)Math.floor(box.maxX))>>4,z0=((int)Math.floor(box.minZ))>>4,z1=((int)Math.floor(box.maxZ))>>4;
            if((long)(x1-x0+1)*(z1-z0+1)>4096)return false;
            for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!level.hasChunk(x,z))return false;
        }return true;
    }
    public static List<Body> forcedDestination(List<Body> start,List<Body> current,Vec3 offset){
        var result=new ArrayList<Body>();for(var now:current){var original=start.stream().filter(b->b.body()==now.body()).findFirst().orElse(null);if(original==null)return List.of();var pos=new Vector3d(original.position()).add(offset.x,offset.y,offset.z);var delta=new Vec3(pos.x-now.position().x,pos.y-now.position().y,pos.z-now.position().z);result.add(new Body(now.body(),pos,now.rotation(),now.box().move(delta)));}return List.copyOf(result);
    }
    public static boolean move(ServerLevel level,List<Body> bodies,Vec3 delta){
        var system=SubLevelPhysicsSystem.get(level);if(system==null||bodies.isEmpty()||bodies.stream().anyMatch(b->b.body().isRemoved()))return false;
        for(var b:bodies){system.getPipeline().resetVelocity(b.body());system.getPipeline().teleport(b.body(),new Vector3d(b.position()).add(delta.x,delta.y,delta.z),new Quaterniond(b.rotation()));}return true;
    }
}
