package dev.sablelife.module;

import dev.sablelife.attack.ContactBox;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3f;

/** Shared geometry for emission and Preview. Shape coordinates are relative to the marked limb. */
public final class ParticlePattern {
    public record Frame(Vec3 center, Quaterniond rotation, Vec3 half, Vec3 forward) {}
    public static Frame frame(HeartBlockEntity heart, VirtualModule module) {
        var anchor = module.anchors().getFirst(); var body = anchor.body(heart);
        var rotation = new Quaterniond(body.logicalPose().orientation());
        Direction face = switch ((int) module.value("direction")) {
            case 1 -> Direction.DOWN; case 2 -> Direction.UP; case 3 -> Direction.NORTH;
            case 4 -> Direction.SOUTH; case 5 -> Direction.WEST; case 6 -> Direction.EAST; default -> anchor.face();
        };
        var forward = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 local = anchor.position(body).getCenter();
        Vec3 half = Vec3.ZERO;
        if (module.area()) {
            local = local.lerp(module.anchors().get(1).position(body).getCenter(), .5);
            half = dev.sablelife.module.ModuleCombat.halfSize(heart, module);
        } else local = local.add(Vec3.atLowerCornerOf(anchor.face().getNormal()).scale(.55));
        if(module.value("particlePattern")<=1){half=expandedHalf(module,half,forward);local=local.add(forward.scale(module.value("particleExpandLength")/2));}
        var center = body.logicalPose().transformPosition(local).add(ContactBox.rotate(rotation, forward).scale(module.value("startDistance")));
        return new Frame(center, rotation, half, forward);
    }
    /** Same forward/right/up full-dimension extension as Melee; length grows ahead. */
    public static Vec3 expandedHalf(VirtualModule m,Vec3 base,Vec3 forward){return MeleeGeometry.expandedHalf(base,forward,m.value("particleExpandLength"),m.value("particleExpandWidth"),m.value("particleExpandHeight"));}
    public static boolean expanded(VirtualModule m){return m.value("particleExpandLength")>0||m.value("particleExpandWidth")>0||m.value("particleExpandHeight")>0;}
    public static Vec3 side(Vec3 forward) {
        return forward.cross(Math.abs(forward.y) > .9 ? new Vec3(0,0,1) : new Vec3(0,1,0)).normalize();
    }
    public static boolean visibleField(VirtualModule m, String key) {
        int shape=(int)m.value("particlePattern");
        return switch(key) {
            case "particleExpandLength","particleExpandWidth","particleExpandHeight" -> shape<=1;
            case "particleRadius" -> shape==3||shape==4;
            case "particleRingHeight" -> shape==3;
            case "particleLength" -> shape==2;
            case "particleLineMode" -> shape==2&&m.value("particlePropagation")==0;
            case "particleFill" -> shape==4&&!travels(m);
            case "particlePropagation" -> shape>=2;
            case "particleSpeed" -> travels(m);
            case "particleDistribution" -> (shape!=1||expanded(m))&&!(shape==2&&travels(m));
            case "drift" -> !travels(m);
            default -> true;
        };
    }
    public static boolean spray(VirtualModule m) { return m.value("particlePattern") == 2 && m.value("particleLineMode") == 1; }
    public static boolean travels(VirtualModule m){return m.value("particlePattern")>=2&&(m.value("particlePropagation")!=0||spray(m));}
    public static ParticleTravelPacket travel(VirtualModule m,ModuleParticles.Entry particle,Vec3 center,Quaterniond rotation,Vec3 forward,int index,int count,RandomSource random){
        int shape=(int)m.value("particlePattern");
        var sampled=shape==2?forward.scale(m.value("particleLength")):sample(m,Vec3.ZERO,forward,index,count,random);
        // A cylinder expands radially from each section's axis, preserving its axial height.
        Vec3 axial=shape==3?forward.scale(sampled.dot(forward)):Vec3.ZERO;
        Vec3 offset=shape==3||shape==2?sampled:sampled.normalize().scale(m.value("particleRadius"));
        var origin=center.add(ContactBox.rotate(rotation,axial));
        var edge=center.add(ContactBox.rotate(rotation,offset));boolean reverse=m.value("particlePropagation")==2;
        double distance=shape==2?m.value("particleLength"):m.value("particleRadius");
        int ticks=Math.clamp((int)Math.ceil(distance/m.value("particleSpeed")),1,12800);
        int life=particle.ticks()==0?ticks+1:Math.min(particle.ticks(),ticks+1);
        return new ParticleTravelPacket(particle.id(),reverse?edge:origin,reverse?origin:edge,ticks,life,particle.size(),particle.color());
    }
    public static void emit(net.minecraft.server.level.ServerLevel level,VirtualModule m,ModuleParticles.Entry particle,Vec3 center,Quaterniond rotation,Vec3 forward,int index,int count,RandomSource random,ServerPlayer only){
        if(travels(m)){
            var packet=travel(m,particle,center,rotation,forward,index,count,random);
            if(only!=null){if(VisualBudget.allow(only,1))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(only,packet);}
            else for(var player:level.players())if(player.position().distanceToSqr(packet.from())<=512*512&&VisualBudget.allow(player,1))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,packet);
            return;
        }
        var position=center.add(ContactBox.rotate(rotation,sample(m,Vec3.ZERO,forward,index,count,random)));
        var velocity=velocity(m,rotation,forward,random);
        if(only!=null)net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(only,new ModuleNetworking.ParticleVisual(particle.id(),position,0,velocity,1,particle.ticks(),particle.size(),particle.color()));
        else ModuleParticles.send(level,particle,position.x,position.y,position.z,0,velocity.x,velocity.y,velocity.z,1);
    }
    public static double sweepRadius(VirtualModule m, Frame frame) {
        return switch ((int) m.value("particlePattern")) {
            case 0,1 -> frame.half.length(); case 2 -> travels(m)&&m.value("particlePropagation")!=2 ? 0 : m.value("particleLength");
            case 3 -> Math.hypot(m.value("particleRadius"),m.value("particleRingHeight"));
            case 4 -> m.value("particleRadius"); default -> 0;
        };
    }
    /** Random sphere interiors are uniform by volume, rather than clustered at the center. */
    public static Vec3 sample(VirtualModule m, Vec3 half, Vec3 forward, int index, int count, RandomSource random) {
        boolean stochastic = m.value("particleDistribution") == 0;
        double fraction = (index + .5) / Math.max(1, count);
        double angle = stochastic ? random.nextDouble() * Math.PI * 2 : index * Math.PI * (3 - Math.sqrt(5));
        int shape = (int) m.value("particlePattern");
        if(shape==1&&half.lengthSqr()==0)return Vec3.ZERO;
        if (shape == 0 || shape == 1) {
            if (stochastic) return new Vec3((random.nextDouble()*2-1)*half.x, (random.nextDouble()*2-1)*half.y, (random.nextDouble()*2-1)*half.z);
            // Low-discrepancy samples fill the box without concentrating on a single diagonal.
            return new Vec3((fraction*2-1)*half.x, (((index+.5)*.61803398875%1)*2-1)*half.y, (((index+.5)*.41421356237%1)*2-1)*half.z);
        }
        if (shape == 1) return Vec3.ZERO;
        if (shape == 2) return spray(m) ? Vec3.ZERO : forward.scale(m.value("particleLength") * (stochastic ? random.nextDouble() : count <= 1 ? .5 : index / (double)(count-1)));
        var side = side(forward); var up = side.cross(forward).normalize();
        double radius = m.value("particleRadius");
        if (shape == 3) {
            double height=m.value("particleRingHeight");
            // Height zero uses exactly the legacy ring distribution and random stream.
            angle = stochastic ? angle : height==0?Math.PI*2*index/Math.max(1,count):index*Math.PI*(3-Math.sqrt(5));
            double axial=height==0?0:height*(stochastic?random.nextDouble():fraction);
            return side.scale(Math.cos(angle)*radius).add(up.scale(Math.sin(angle)*radius)).add(forward.scale(axial));
        }
        double axial = stochastic ? random.nextDouble()*2-1 : fraction*2-1;
        double radial = Math.sqrt(Math.max(0,1-axial*axial));
        double distance = radius * (m.value("particleFill") == 1 ? 1 : Math.cbrt(stochastic ? random.nextDouble() : ((index+.5)*.754877666%1)));
        return forward.scale(axial*distance).add(side.scale(Math.cos(angle)*radial*distance)).add(up.scale(Math.sin(angle)*radial*distance));
    }
    public static ModuleParticles.Entry emissionStyle(VirtualModule m, ModuleParticles.Entry original) {
        if (!spray(m)) return original;
        int limit = Math.clamp((int)Math.ceil(m.value("particleLength")/m.value("particleSpeed")),1,1200);
        return new ModuleParticles.Entry(original.id(), original.ticks() == 0 ? limit : Math.min(original.ticks(),limit), original.size(),original.color());
    }
    public static Vec3 velocity(VirtualModule m, Quaterniond rotation, Vec3 forward, RandomSource random) {
        double drift=m.value("drift");
        var scatter=new Vec3((random.nextDouble()*2-1)*drift,(random.nextDouble()*2-1)*drift,(random.nextDouble()*2-1)*drift);
        return spray(m) ? scatter.add(ContactBox.rotate(rotation,forward).scale(m.value("particleSpeed"))) : scatter;
    }
    private static void outline(ServerPlayer player, Vec3 point) {
        var dust=new DustParticleOptions(new Vector3f(.2F,1,.4F),1.1F);
        ((net.minecraft.server.level.ServerLevel)player.level()).sendParticles(player,dust,true,point.x,point.y,point.z,1,0,0,0,0);
    }
    public static void preview(ServerPlayer player, HeartBlockEntity heart, VirtualModule m) {
        var frame=frame(heart,m); var forward=frame.forward; var side=side(forward); var up=side.cross(forward).normalize();
        int shape=(int)m.value("particlePattern");double radius=m.value("particleRadius");
        if(shape==0||shape==1&&expanded(m)) {
            MarkOutline.send(player,MarkOutline.Kind.DETAIL,0x33FF66,MarkOutline.box(frame.half.scale(-1),frame.half,
                point->frame.center.add(ContactBox.rotate(frame.rotation,point))));
        } else if(shape==2) {
            for(int i=0;i<=32;i++)outline(player,frame.center.add(ContactBox.rotate(frame.rotation,forward.scale(m.value("particleLength")*i/32.))));
        } else if(shape==3&&m.value("particleRingHeight")>0) {
            double height=m.value("particleRingHeight");
            for(int layer=0;layer<3;layer++)for(int i=0;i<32;i++){
                double angle=Math.PI*2*i/32;var point=forward.scale(height*layer/2).add(side.scale(Math.cos(angle)*radius)).add(up.scale(Math.sin(angle)*radius));
                outline(player,frame.center.add(ContactBox.rotate(frame.rotation,point)));
            }
            for(int edge=0;edge<4;edge++)for(int step=0;step<=16;step++){
                double angle=Math.PI*edge/2;var point=forward.scale(height*step/16).add(side.scale(Math.cos(angle)*radius)).add(up.scale(Math.sin(angle)*radius));
                outline(player,frame.center.add(ContactBox.rotate(frame.rotation,point)));
            }
        } else if(shape==3||shape==4) {
            for(int plane=0;plane<(shape==4?3:1);plane++)for(int i=0;i<32;i++) {
                double angle=Math.PI*2*i/32;var a=plane==2?up:side;var b=plane==0?up:forward;
                outline(player,frame.center.add(ContactBox.rotate(frame.rotation,a.scale(Math.cos(angle)*radius).add(b.scale(Math.sin(angle)*radius)))));
            }
        } else outline(player,frame.center);
        int count=Math.min(Math.min(32,(int)m.value("density")),256/Math.max(1,m.particles().size()));var random=player.level().random;
        // Only the requesting player receives actual effect particles during Preview.
        for(var original:m.particleEntries())for(int i=0;i<count;i++) {
            if(travels(m)){emit((net.minecraft.server.level.ServerLevel)player.level(),m,original,frame.center,frame.rotation,forward,i,count,random,player);continue;}
            var position=frame.center.add(ContactBox.rotate(frame.rotation,sample(m,frame.half,forward,i,count,random)));
            var velocity=velocity(m,frame.rotation,forward,random);var style=emissionStyle(m,original);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ModuleNetworking.ParticleVisual(style.id(),position,0,velocity,1,style.ticks(),style.size(),style.color()));
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("dashboard.sablelife.particle_pattern_help"),true);
    }
    private ParticlePattern() {}
}
