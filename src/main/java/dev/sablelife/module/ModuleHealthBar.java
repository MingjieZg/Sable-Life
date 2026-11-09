package dev.sablelife.module;

import dev.sablelife.heart.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;

/** Cosmetic health snapshots. Anchors are transformed on the client, not streamed every frame. */
public final class ModuleHealthBar {
    private CompoundTag last;
    private UUID lastCreature;
    private UUID damagedCreature;
    private long lastDamageTick=Long.MIN_VALUE;
    private boolean damagePending;
    public void damaged(HeartBlockEntity core){damagedCreature=core.creatureId();lastDamageTick=core.getLevel().getGameTime();damagePending=true;}
    private boolean recentlyDamaged(HeartBlockEntity core,VirtualModule module){
        long now=core.getLevel().getGameTime();return core.creatureId().equals(damagedCreature)&&now>=lastDamageTick&&now-lastDamageTick<(long)module.value("healthBarHideDelay");
    }
    public static boolean showing(HeartBlockEntity core,VirtualModule module){
        return module.type().equals("health_bar")&&!core.isControlCore()
                &&(core.health().state()==HeartState.ALIVE||core.health().state()==HeartState.DYING)
                &&HeartModules.status(module,core).equals("valid")&&HeartModules.controlPowered(module,core)
                &&(module.value("healthBarVisibility")==0||core.modules().healthBars.recentlyDamaged(core,module));
    }
    public static Vec3 localPoint(ModuleAnchor anchor,dev.ryanhcode.sable.sublevel.SubLevel body,double distance){
        return anchor.position(body).getCenter().add(Vec3.atLowerCornerOf(anchor.face().getNormal()).scale(.5+distance));
    }
    public static CompoundTag row(HeartBlockEntity core,VirtualModule module){
        var row=new CompoundTag();row.putUUID("Id",module.id());row.put("Anchor",module.anchors().getFirst().save());
        row.putInt("Style",(int)module.value("healthBarStyle"));row.putFloat("Width",(float)module.value("healthBarWidth"));row.putFloat("Height",(float)module.value("healthBarHeight"));
        row.putBoolean("ShowName",module.value("healthBarName")==1);row.putFloat("NameSize",(float)module.value("healthBarNameSize"));row.putFloat("NameGap",(float)module.value("healthBarNameGap"));
        row.putFloat("Distance",(float)module.value("startDistance"));row.putFloat("ViewDistance",(float)module.value("healthBarViewDistance"));row.putFloat("Health",core.health().current());row.putFloat("Maximum",core.health().maximum());return row;
    }
    public static CompoundTag snapshot(HeartBlockEntity core){
        var data=new CompoundTag();var rows=new ListTag();
        for(var module:core.modules().all())if(rows.size()<64&&showing(core,module))rows.add(row(core,module));
        data.put("HealthBars",rows);if(rows.stream().anyMatch(value->((CompoundTag)value).getBoolean("ShowName")))data.putString("HealthBarName",core.creatureName());return data;
    }
    public void tick(HeartBlockEntity core){
        if(!(core.getLevel() instanceof ServerLevel level)||level.getGameTime()%4!=0&&!damagePending)return;
        damagePending=false;
        var data=snapshot(core);boolean empty=data.getList("HealthBars",10).isEmpty();
        if(empty&&(last==null||last.getList("HealthBars",10).isEmpty()))return;
        if(data.equals(last)&&core.creatureId().equals(lastCreature)&&level.getGameTime()%20!=0)return;
        last=data;lastCreature=core.creatureId();var center=dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,core.getBlockPos().getCenter());
        for(var player:level.players())if(dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,player.position()).distanceToSqr(center)<256*256)
            PacketDistributor.sendToPlayer(player,new ItemDisplayPacket(core.getBlockPos(),core.creatureId(),data));
    }
    public static void preview(ServerPlayer player,HeartBlockEntity core,VirtualModule module){
        var data=new CompoundTag();var rows=new ListTag();rows.add(row(core,module));data.put("HealthBars",rows);data.putBoolean("HealthBarPreview",true);if(module.value("healthBarName")==1)data.putString("HealthBarName",core.creatureName());
        PacketDistributor.sendToPlayer(player,new ItemDisplayPacket(core.getBlockPos(),core.creatureId(),data));
    }
}
