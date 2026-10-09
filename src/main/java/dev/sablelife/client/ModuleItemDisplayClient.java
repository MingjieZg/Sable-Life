package dev.sablelife.client;
import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import dev.sablelife.heart.HeartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;
@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class ModuleItemDisplayClient {
    private record Display(UUID id,ModuleAnchor anchor,ItemStack item,float size,Vec3 offset,org.joml.Quaternionf rotation){}
    private record Core(UUID creature,List<Display> displays,long expires){}
    private static final Map<BlockPos,Core> cores=new LinkedHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel previous;
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){if(Minecraft.getInstance().level==null){cores.clear();previous=null;}}
    public static void receive(ItemDisplayPacket packet){
        var level=Minecraft.getInstance().level;if(level==null)return;if(level!=previous){cores.clear();previous=level;}
        var list=new ArrayList<Display>();if(packet.displays()==null)return;
        for(var value:packet.displays().getList("Displays",10)){
            if(list.size()>=64)break;var row=(net.minecraft.nbt.CompoundTag)value;var anchor=ModuleAnchor.load(row.getCompound("Anchor"));var item=ItemStack.parseOptional(level.registryAccess(),row.getCompound("Item"));if(anchor==null||item.isEmpty()||!row.hasUUID("Id"))continue;
            var rotation=new org.joml.Quaternionf().rotationXYZ((float)Math.toRadians(row.getFloat("displayRotX")),(float)Math.toRadians(row.getFloat("displayRotY")),(float)Math.toRadians(row.getFloat("displayRotZ")));
            list.add(new Display(row.getUUID("Id"),anchor,item,row.getFloat("displaySize"),new Vec3(row.getFloat("displayX"),row.getFloat("displayY"),row.getFloat("displayZ")),rotation));
        }
        if(list.isEmpty())cores.remove(packet.core());else cores.put(packet.core(),new Core(packet.creature(),List.copyOf(list),level.getGameTime()+40));
        while(cores.size()>256)cores.remove(cores.keySet().iterator().next());
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();var level=mc.level;
        if(level==null||level!=previous){cores.clear();previous=level;return;}cores.entrySet().removeIf(e->e.getValue().expires<level.getGameTime());
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();int count=0;
        for(var entry:cores.entrySet()){
            if(!(level.getBlockEntity(entry.getKey()) instanceof HeartBlockEntity core)||!core.creatureId().equals(entry.getValue().creature))continue;
            for(var display:entry.getValue().displays){
                var body=SubLevelContainer.getContainer(level).getSubLevel(display.anchor.bodyId());if(!(body instanceof ClientSubLevel client)||body.isRemoved()||level.getBlockState(display.anchor.position(body)).isAir())continue;
                var local=display.anchor.position(body).getCenter().add(Vec3.atLowerCornerOf(display.anchor.face().getNormal()).scale(.55)).add(display.offset);var renderPose=client.renderPose(event.getPartialTick().getGameTimeDeltaPartialTick(false));var world=renderPose.transformPosition(local);if(world.distanceToSqr(camera)>256*256)continue;if(++count>512)break;
                pose.pushPose();pose.translate(world.x-camera.x,world.y-camera.y,world.z-camera.z);pose.mulPose(new org.joml.Quaternionf(renderPose.orientation()));pose.mulPose(display.rotation);
                ProjectileItemRenderer.render(mc.getItemRenderer(),display.item,level,display.id.hashCode(),display.size,pose,buffers,net.minecraft.client.renderer.LevelRenderer.getLightColor(level,BlockPos.containing(world)));pose.popPose();
            }
        }
    }
    private ModuleItemDisplayClient(){}
}
