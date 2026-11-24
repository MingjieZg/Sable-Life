package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class PatternVisualClient {
    public record Birth(PatternVisual packet,long born){}
    private record Frame(Vec3 point, Vec3 normal, Vec3 up) {}
    private static final class Shield {
        final DeployableShieldAnimation animation;
        Frame lastFrame;
        private DeployableShieldGeometry geometry;
        private float geometrySize;

        Shield(ShieldVisualPacket packet, double now) {
            animation = new DeployableShieldAnimation(packet, now);
        }

        List<PatternGeometry.Stroke> strokes(double now) {
            float size = animation.visual().size();
            if (geometry == null || size != geometrySize) {
                geometry = new DeployableShieldGeometry(size);
                geometrySize = size;
            }
            return geometry.strokes(animation.openness(now));
        }
    }

    private static final Map<UUID,Birth> effects=new LinkedHashMap<>();
    private static final Map<UUID,Shield> shields = new LinkedHashMap<>();
    private static ClientLevel world;

    private static ClientLevel currentWorld() {
        var level = Minecraft.getInstance().level;
        if (level != world) {
            effects.clear();
            shields.clear();
            world = level;
        }
        return level;
    }

    public static void receive(PatternVisual packet){
        var level=currentWorld();
        if(level==null||!packet.valid()||packet.pattern()==ShieldVisualPacket.GEOMETRY_PATTERN
                ||!level.dimension().location().equals(packet.dimension()))return;
        effects.put(packet.id(),new Birth(packet,level.getGameTime()));
        while(effects.size()>PatternVisual.MAX_EFFECTS)effects.remove(effects.keySet().iterator().next());
    }

    public static void receiveShield(ShieldVisualPacket packet) {
        var level = currentWorld();
        if (level == null || !packet.valid() || !level.dimension().location().equals(packet.visual().dimension())) return;
        double now = level.getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        var id = packet.visual().id();
        var shield = shields.get(id);
        if (shield != null && shield.animation.advance(now)) {
            shields.remove(id);
            shield = null;
        }
        if (shield != null) {
            shield.animation.update(packet, now);
        } else if (packet.phase() != ShieldVisualPacket.Phase.CLOSE && shields.size() < PatternVisual.MAX_EFFECTS) {
            shields.put(id, new Shield(packet, now));
        }
    }

    public static void clear(){effects.clear();shields.clear();world=null;PatternVisualRenderer.release();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var level=Minecraft.getInstance().level;
        if(level==null||level!=world){if(world!=null)clear();return;}
        effects.values().removeIf(effect->level.getGameTime()-effect.born()>=effect.packet().duration());
        shields.values().removeIf(shield -> shield.animation.advance(level.getGameTime()));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        // Iris text programs must participate in the entity pass. Photon discards later
        // custom text-material geometry, even though ordinary font glyphs remain visible.
        var stage=ShaderParticleCompat.active()?RenderLevelStageEvent.Stage.AFTER_ENTITIES:RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if(event.getStage()!=stage)return;
        var mc=Minecraft.getInstance();if(world==null||world!=mc.level||effects.isEmpty()&&shields.isEmpty())return;
        var camera=event.getCamera().getPosition();float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now=world.getGameTime()+partial;int strokes=0;
        for(var effect:List.copyOf(effects.values())){
            var packet=effect.packet();boolean symbol=packet.pattern()==6;
            double age=now-effect.born();if(age<0||age>=packet.duration())continue;
            var frame = frame(packet, partial);
            if(frame==null){effects.remove(packet.id());continue;}
            if(frame.point().distanceToSqr(camera)>packet.range()*packet.range())continue;
            if(symbol){PatternVisualRenderer.symbol(event,packet,frame.point(),age);continue;}
            var lines=PatternGeometry.strokes(packet.pattern(),age,packet.duration(),packet.speed(),packet.size());
            if(strokes+lines.size()>8192)break;
            PatternVisualRenderer.strokes(event,frame.point(),frame.normal(),frame.up(),packet.rgb(),lines);strokes+=lines.size();
        }
        for (var shield : shields.values()) {
            var animation = shield.animation;
            if (animation.advance(now)) continue;
            var packet = animation.visual();
            var frame = frame(packet, partial);
            if (frame != null) {
                shield.lastFrame = frame;
            } else if (animation.closing()) {
                frame = shield.lastFrame;
            }
            if (frame == null || frame.point().distanceToSqr(camera) > packet.range() * packet.range()) continue;
            var lines = shield.strokes(now);
            if (strokes + lines.size() > 8192) continue;
            if (lines.isEmpty()) continue;
            PatternVisualRenderer.strokes(event, frame.point(), frame.normal(), frame.up(), packet.rgb(), lines);
            strokes += lines.size();
        }
        PatternVisualRenderer.finish();
    }

    private static Frame frame(PatternVisual packet, float partial) {
        if (!packet.follow()) return new Frame(packet.point(), packet.normal(), packet.up());
        var container = SubLevelContainer.getContainer(world);
        var body = container == null ? null : container.getSubLevel(packet.anchor().bodyId());
        if (!(body instanceof ClientSubLevel client) || body.isRemoved()
                || world.getBlockState(packet.anchor().position(body)).isAir()) return null;
        var local = ModuleHealthBar.localPoint(packet.anchor(), body, packet.startDistance());
        var render = client.renderPose(partial);
        var point = render.transformPosition(local);
        var normal = Vec3.atLowerCornerOf(packet.anchor().face().getNormal());
        var right = normal.cross(Math.abs(normal.y) > .9 ? new Vec3(0,0,1) : new Vec3(0,1,0)).normalize();
        return new Frame(point, render.transformPosition(local.add(normal)).subtract(point).normalize(),
                render.transformPosition(local.add(normal.cross(right))).subtract(point).normalize());
    }

    private PatternVisualClient(){}
}
