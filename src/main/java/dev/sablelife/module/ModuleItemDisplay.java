package dev.sablelife.module;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.List;
/** Cosmetic snapshots only: no entities, item drops, collision or damage. */
public final class ModuleItemDisplay {
    private CompoundTag last;
    private java.util.UUID lastCreature;
    public static boolean showing(HeartBlockEntity core,VirtualModule m){return m.type().equals("item_display")&&m.enabled()&&HeartModules.status(m,core).equals("valid")&&!m.projectileItem(core.getLevel().registryAccess()).isEmpty()&&(m.value("displayAlways")==1||HeartModules.triggered(m,core));}
    public static CompoundTag snapshot(HeartBlockEntity core){
        var tag=new CompoundTag();var rows=new ListTag();
        for(var m:core.modules().all())if(showing(core,m)){
            var row=new CompoundTag();row.putUUID("Id",m.id());row.put("Anchor",m.anchors().getFirst().save());row.put("Item",m.save().getCompound("ProjectileItem"));
            for(String key:List.of("displaySize","displayX","displayY","displayZ","displayRotX","displayRotY","displayRotZ"))row.putFloat(key,(float)m.value(key));rows.add(row);
        }tag.put("Displays",rows);return tag;
    }
    public static Vec3 localPoint(ModuleAnchor anchor,dev.ryanhcode.sable.sublevel.SubLevel body,VirtualModule m){return anchor.position(body).getCenter().add(Vec3.atLowerCornerOf(anchor.face().getNormal()).scale(.55)).add(m.value("displayX"),m.value("displayY"),m.value("displayZ"));}
    public static void preview(net.minecraft.server.level.ServerPlayer player,HeartBlockEntity core,VirtualModule m){
        var anchor=m.anchors().getFirst();var body=anchor.body(core);if(body==null)return;
        var center=localPoint(anchor,body,m);double half=m.value("displaySize")/2;
        var rotation=new org.joml.Quaterniond().rotationXYZ(Math.toRadians(m.value("displayRotX")),Math.toRadians(m.value("displayRotY")),Math.toRadians(m.value("displayRotZ")));
        var extent=new Vec3(half,half,half);
        MarkOutline.send(player,MarkOutline.Kind.DETAIL,0xFFD940,MarkOutline.box(extent.scale(-1),extent,point->{
            var rotated=rotation.transform(new org.joml.Vector3d(point.x,point.y,point.z));
            return body.logicalPose().transformPosition(center.add(rotated.x,rotated.y,rotated.z));
        }));
    }
    public void tick(HeartBlockEntity core){
        if(!(core.getLevel() instanceof ServerLevel level))return;
        var data=snapshot(core);boolean visible=!data.getList("Displays",10).isEmpty();
        if(!visible&&(last==null||last.getList("Displays",10).isEmpty()))return;
        if(data.equals(last)&&core.creatureId().equals(lastCreature)&&level.getGameTime()%20!=0)return;last=data;lastCreature=core.creatureId();
        var center=dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,core.getBlockPos().getCenter());
        for(var player:level.players())if(dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,player.position()).distanceToSqr(center)<256*256)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ItemDisplayPacket(core.getBlockPos(),core.creatureId(),data));
    }
}
