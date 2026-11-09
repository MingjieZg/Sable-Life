package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class WorldHealthBarClient {
    private record Bar(ModuleAnchor anchor,float width,float height,float distance,float viewDistance,float fraction,int style,boolean showName,float nameSize,float nameGap){}
    private record Group(UUID creature,List<Bar> bars,long expires,boolean preview,Component name){}
    private static final Map<BlockPos,Group> cores=new LinkedHashMap<>(),previews=new LinkedHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel previous;
    private static long renderedBars,renderedNames;
    public static void receive(ItemDisplayPacket packet){
        var level=Minecraft.getInstance().level;if(level==null)return;
        if(previous!=level){cores.clear();previews.clear();previous=level;}
        boolean preview=packet.displays().getBoolean("HealthBarPreview");var target=preview?previews:cores;var bars=new ArrayList<Bar>();
        for(var value:packet.displays().getList("HealthBars",10)){
            if(bars.size()>=64)break;var row=(CompoundTag)value;var anchor=ModuleAnchor.load(row.getCompound("Anchor"));
            float width=row.getFloat("Width"),height=row.getFloat("Height"),distance=row.getFloat("Distance"),health=row.getFloat("Health"),max=row.getFloat("Maximum");
            float viewDistance=row.contains("ViewDistance",99)?row.getFloat("ViewDistance"):64F;
            if(anchor==null||!Float.isFinite(width)||!Float.isFinite(height)||!Float.isFinite(distance)||!Float.isFinite(viewDistance)||!Float.isFinite(health)||!Float.isFinite(max)||max<=0)continue;
            float nameSize=row.contains("NameSize",99)?row.getFloat("NameSize"):.2F,nameGap=row.contains("NameGap",99)?row.getFloat("NameGap"):.08F;
            if(!Float.isFinite(nameSize))nameSize=.2F;if(!Float.isFinite(nameGap))nameGap=.08F;
            bars.add(new Bar(anchor,Math.clamp(width,.25F,16F),Math.clamp(height,.05F,2F),Math.clamp(distance,0F,128F),Math.clamp(viewDistance,1F,256F),Math.clamp(health/max,0F,1F),Math.clamp(row.getInt("Style"),0,1),row.getBoolean("ShowName"),Math.clamp(nameSize,.05F,2F),Math.clamp(nameGap,0F,4F)));
        }
        String rawName=packet.displays().getString("HealthBarName");
        Component name=rawName.codePointCount(0,rawName.length())<=64&&!rawName.codePoints().anyMatch(Character::isISOControl)?CreatureNames.formatted(rawName):Component.empty();
        if(bars.isEmpty())target.remove(packet.core());else target.put(packet.core(),new Group(packet.creature(),List.copyOf(bars),level.getGameTime()+(preview?10:40),preview,name));
        while(target.size()>256)target.remove(target.keySet().iterator().next());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(Minecraft.getInstance().level==null){cores.clear();previews.clear();previous=null;}}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        var stage=event.getStage();
        if(stage!=RenderLevelStageEvent.Stage.AFTER_ENTITIES&&stage!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        // Iris composites font materials during the entity pass; keep the bar's water-safe later pass.
        boolean shaderNames=ShaderParticleCompat.active();
        boolean namesPass=shaderNames&&stage==RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        if(!namesPass&&stage!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();var level=mc.level;if(level==null||level!=previous){cores.clear();previews.clear();previous=level;return;}
        cores.entrySet().removeIf(e->e.getValue().expires<level.getGameTime());previews.entrySet().removeIf(e->e.getValue().expires<level.getGameTime());
        var camera=event.getCamera().getPosition();var pose=event.getPoseStack();var container=SubLevelContainer.getContainer(level);if(container==null)return;int count=0;
        var groups=new LinkedHashMap<>(cores);groups.putAll(previews);
        for(var entry:groups.entrySet()){
            var group=entry.getValue();if(!(level.getBlockEntity(entry.getKey()) instanceof HeartBlockEntity core)||!core.creatureId().equals(group.creature))continue;
            if(!group.preview&&core.health().state()!=HeartState.ALIVE&&core.health().state()!=HeartState.DYING)continue;
            for(var bar:group.bars){
                var body=container.getSubLevel(bar.anchor.bodyId());if(!(body instanceof ClientSubLevel client)||body.isRemoved()||level.getBlockState(bar.anchor.position(body)).isAir())continue;
                var world=client.renderPose(event.getPartialTick().getGameTimeDeltaPartialTick(false)).transformPosition(ModuleHealthBar.localPoint(bar.anchor,body,bar.distance));
                if(world.distanceToSqr(camera)>bar.viewDistance*bar.viewDistance)continue;if(++count>128)return;
                pose.pushPose();pose.translate(world.x-camera.x,world.y-camera.y,world.z-camera.z);pose.mulPose(event.getCamera().rotation());
                if(!namesPass){WorldHealthBarRenderer.draw(pose,mc.renderBuffers().bufferSource(),bar.width,bar.height,bar.fraction,bar.style);renderedBars++;}
                if((namesPass||!shaderNames)&&bar.showName&&!group.name.getString().isBlank()){WorldHealthBarRenderer.drawName(pose,group.name,bar.height,bar.nameSize,bar.nameGap);renderedNames++;}
                pose.popPose();
            }
        }
    }
    private WorldHealthBarClient(){}
}
