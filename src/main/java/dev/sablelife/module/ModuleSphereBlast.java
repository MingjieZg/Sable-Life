package dev.sablelife.module;

import dev.sablelife.attack.ModuleDamage;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** A frozen spherical telegraph, followed by one hit per eligible target. No projectile or travel damage. */
public final class ModuleSphereBlast {
    public static final double BURST_SPEED=12;
    private static final class State {long due,fire,lastCharge=Long.MIN_VALUE;List<Vec3> points=List.of();VirtualModule cast;}
    private final Map<UUID,State> states=new HashMap<>();
    private ServerLevel level;
    public void clear(){if(level!=null)states.keySet().forEach(id->ModuleAudio.stop(level,id));states.clear();level=null;}
    public static Vec3 start(HeartBlockEntity heart,VirtualModule m){if(TargetCastOrigin.targeted(m))return TargetCastOrigin.searchOrigin(heart,m);return m.anchors().getFirst().world(heart,false).add(ModuleCombat.direction(heart,m).scale(.55+m.value("startDistance")));}
    public static Vec3 center(HeartBlockEntity heart,VirtualModule m){if(TargetCastOrigin.targeted(m))return TargetCastOrigin.point(heart,m);return start(heart,m).add(ModuleCombat.direction(heart,m).scale(m.value("sphereDistance")));}
    private static Vec3 shell(Vec3 center,double radius,int i,int count){double y=1-2*(i+.5)/count;double a=i*Math.PI*(3-Math.sqrt(5));double r=Math.sqrt(1-y*y);return center.add(Math.cos(a)*r*radius,y*radius,Math.sin(a)*r*radius);}
    /** All charging particles travel from the shell to its fixed world-space center. */
    public static List<ParticleTravelPacket> chargeParticles(VirtualModule m,Vec3 point,long remaining){
        var entries=m.movementParticles("Charge").particleEntries();if(remaining<=0||entries.isEmpty())return List.of();
        double radius=m.value("blastRadius"),distance=radius;int ticks=Math.max(1,(int)Math.ceil(distance*20/m.value("sphereChargeSpeed")));var result=new ArrayList<ParticleTravelPacket>();
        for(int i=0;i<96;i++){var entry=entries.get(i%entries.size());var from=shell(point,radius,i,96);var to=point;int life=(int)Math.max(1,Math.min(remaining,entry.ticks()>0?entry.ticks():ticks+1));result.add(new ParticleTravelPacket(entry.id(),from,to,ticks,life,entry.size(),entry.color()));}
        return List.copyOf(result);
    }
    /** Both selected stages burst from the gathered center out to the configured sphere boundary. */
    public static List<ParticleTravelPacket> burstParticles(VirtualModule m,Vec3 point){
        var entries=new ArrayList<ModuleParticles.Entry>(m.movementParticles("Charge").particleEntries());entries.addAll(m.movementParticles("Blast").particleEntries());if(entries.isEmpty())return List.of();double radius=m.value("blastRadius");int ticks=Math.max(1,(int)Math.ceil(radius*20/BURST_SPEED));var result=new ArrayList<ParticleTravelPacket>();
        for(int i=0;i<96;i++){var entry=entries.get(i%entries.size());var to=shell(point,radius,i,96);result.add(new ParticleTravelPacket(entry.id(),point,to,ticks,Math.max(ticks+1,entry.ticks()),entry.size(),entry.color()));}return List.copyOf(result);
    }
    private static void charge(ServerLevel level,VirtualModule m,Vec3 point,long remaining,ServerPlayer viewer){sendTravel(level,chargeParticles(m,point,remaining),point,viewer);}
    private static void sendTravel(ServerLevel level,List<ParticleTravelPacket> packets,Vec3 point,ServerPlayer viewer){
        for(var packet:packets)if(viewer!=null){if(VisualBudget.allow(viewer,1))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer,packet);}else for(var player:level.players())if(dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,player.position()).distanceToSqr(point)<=512*512&&VisualBudget.allow(player,1))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);
    }
    public void tick(HeartBlockEntity heart){
        if(!(heart.getLevel() instanceof ServerLevel server))return;level=server;long now=server.getGameTime();var keep=new HashSet<UUID>();
        for(var m:heart.modules().all()){
            if(!m.type().equals("sphere_blast")||!HeartModules.triggered(m,heart))continue;
            keep.add(m.id());var s=states.computeIfAbsent(m.id(),id->new State());
            if(s.cast==null){
                var points=TargetCastOrigin.targeted(m)?TargetCastOrigin.points(heart,m):List.of(center(heart,m));if(points.isEmpty()||now<s.due||points.stream().noneMatch(p->server.hasChunkAt(BlockPos.containing(p)))||!heart.modules().hurtTriggers.start(m))continue;
                s.cast=VirtualModule.load(m.save());s.points=points.stream().filter(p->server.hasChunkAt(BlockPos.containing(p))).toList();s.fire=now+(long)m.value("chargeTime");s.due=now+(long)m.value("interval");for(var point:s.points){ModuleAudio.play(server,s.cast,"chargeSound",point);charge(server,s.cast,point,s.fire-now,null);}s.lastCharge=now;
            }
            if(now<s.fire){if(now%5==0&&s.lastCharge!=now){for(var point:s.points)charge(server,s.cast,point,s.fire-now,null);s.lastCharge=now;}continue;}
            // Never generate or load remote terrain merely to execute an attack.
            for(var point:s.points)if(server.hasChunkAt(BlockPos.containing(point)))detonate(server,heart,s.cast,point);
            s.cast=null;heart.modules().hurtTriggers.completed(m);
        }
        for(var id:List.copyOf(states.keySet()))if(!keep.contains(id)){if(states.get(id).cast!=null)ModuleAudio.stop(server,id);states.remove(id);}
    }
    public static void detonate(ServerLevel level,HeartBlockEntity heart,VirtualModule m,Vec3 point){
        double radius=m.value("blastRadius");float damage=(float)m.value("damage");
        for(var victim:ModuleTargets.query(level,heart.creatureId(),new AABB(point,point).inflate(radius),e->ModuleTargets.matches(e,m))){
            var b=victim.getBoundingBox();var closest=new Vec3(Math.clamp(point.x,b.minX,b.maxX),Math.clamp(point.y,b.minY,b.maxY),Math.clamp(point.z,b.minZ,b.maxZ));
            if(closest.distanceToSqr(point)>radius*radius)continue;
            if(damage==0||ModuleDamage.hurt(victim,ModuleDamage.named(ModuleDamage.blast(level,null),heart),damage,heart,m))ModuleEffects.apply(victim,m.effects(),null);
        }
        // Block destruction uses the same vanilla TNT rules as existing explosive projectiles.
        // Entity damage is handled above once; vanilla damage/knockback is disabled.
        if(m.value("blastBlocks")==1)level.explode(null,null,new ModuleTerrainExplosion(heart.creatureId()),point.x,point.y,point.z,(float)radius/2,false,Level.ExplosionInteraction.TNT,
          net.minecraft.core.particles.ParticleTypes.POOF,net.minecraft.core.particles.ParticleTypes.POOF,net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvents.EMPTY));
        sendTravel(level,burstParticles(m,point),point,null);
        ModuleAudio.play(level,m,"blastSound",point);
    }
    public static void preview(ServerPlayer player,HeartBlockEntity heart,VirtualModule m){
        var level=(ServerLevel)heart.getLevel();var start=start(heart,m);var points=TargetCastOrigin.targeted(m)?TargetCastOrigin.points(heart,m):List.of(center(heart,m));if(points.isEmpty()){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.no_cast_target"),true);return;}var dust=new DustParticleOptions(new org.joml.Vector3f(1,.15F,.15F),1);
        for(var end:points){int count=Math.clamp((int)Math.ceil(start.distanceTo(end)*4),1,256);
        for(int i=0;i<=count;i++){var p=start.lerp(end,(double)i/count);level.sendParticles(player,dust,true,p.x,p.y,p.z,1,0,0,0,0);}
        charge(level,m,end,(long)m.value("chargeTime"),player);
        for(int i=0;i<128;i++){var p=shell(end,m.value("blastRadius"),i,128);level.sendParticles(player,dust,true,p.x,p.y,p.z,1,0,0,0,0);}
        }
    }
}
