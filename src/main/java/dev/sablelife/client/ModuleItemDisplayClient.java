package dev.sablelife.client;
import dev.sablelife.SableLife;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.sablelife.module.*;
import dev.sablelife.heart.HeartBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
    private record Core(UUID creature, long revision, List<Display> displays, int bytes, ItemDisplaySync.Lease lease) {}
    private record Preview(net.minecraft.world.entity.Display.ItemDisplay entity,Vec3 muzzle,long expires){}
    private static final Map<UUID,List<Preview>> previews=new LinkedHashMap<>();
    private static final int MAX_CACHED_BYTES = 4 * ItemDisplaySync.MAX_SNAPSHOT_BYTES;
    private static final Map<BlockPos, Core> cores = new LinkedHashMap<>();
    private static final ItemDisplaySync.Assembly assembly = new ItemDisplaySync.Assembly();
    private static ClientLevel previous;
    private static int cachedBytes;

    private static ClientLevel currentLevel() {
        var level = Minecraft.getInstance().level;
        if (level != previous) {
            cores.clear();
            previews.clear();
            assembly.clear();
            cachedBytes = 0;
            previous = level;
        }
        return level;
    }

    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var level = currentLevel();
        if (level == null) return;
        long now = level.getGameTime();
        assembly.expire(now);
        var iterator = cores.values().iterator();
        while (iterator.hasNext()) {
            var core = iterator.next();
            if (core.lease.expired(now)) {
                cachedBytes -= core.bytes;
                iterator.remove();
            }
        }
        previews.entrySet().removeIf(entry -> entry.getValue().isEmpty() || entry.getValue().getFirst().expires < now);
    }

    public static void receive(ItemDisplayPacket packet){
        var level = currentLevel();
        if (level == null) return;
        if(packet.displays()==null)return;
        if(packet.displays().contains("HealthBars")){WorldHealthBarClient.receive(packet);return;}
        if(packet.displays().contains("SummonPreview")){
            var rows=new ArrayList<Preview>();
            for(var value:packet.displays().getList("SummonPreview",10)){
                if(rows.size()>=32)break;var row=(net.minecraft.nbt.CompoundTag)value;var item=ItemStack.parseOptional(level.registryAccess(),row.getCompound("Item"));if(item.isEmpty())continue;
                var entity=new net.minecraft.world.entity.Display.ItemDisplay(net.minecraft.world.entity.EntityType.ITEM_DISPLAY,level);
                var tag=new net.minecraft.nbt.CompoundTag();tag.put("item",row.getCompound("Item").copy());tag.putString("item_display","fixed");entity.load(tag);
                entity.setPos(row.getDouble("X"),row.getDouble("Y"),row.getDouble("Z"));
                var rotation=new org.joml.Quaternionf((float)row.getDouble("QX"),(float)row.getDouble("QY"),(float)row.getDouble("QZ"),(float)row.getDouble("QW"));
                ((dev.sablelife.mixin.RespawnDisplayAccess)(Object)entity).sablelife$transform(new com.mojang.math.Transformation(new org.joml.Vector3f(),rotation,new org.joml.Vector3f(row.getFloat("Size")),new org.joml.Quaternionf()));
                // Build vanilla render state without adding a collidable/networked world entity.
                var muzzle=row.contains("MuzzleX",6)?new Vec3(row.getDouble("MuzzleX"),row.getDouble("MuzzleY"),row.getDouble("MuzzleZ")):null;
                entity.tick();rows.add(new Preview(entity,muzzle,level.getGameTime()+60));
            }
            previews.put(packet.creature(),List.copyOf(rows));while(previews.size()>16)previews.remove(previews.keySet().iterator().next());return;
        }
        var data = packet.displays();
        long revision = data.getLong(ItemDisplaySync.REVISION);
        var existing = cores.get(packet.core());
        if (existing != null && revision != 0 && revision < existing.revision) return;
        if (existing != null && revision != 0 && revision == existing.revision && existing.creature.equals(packet.creature())) {
            renew(packet.core(), existing, level.getGameTime());
            return;
        }
        assembly.forget(packet.core());
        replace(level, packet.core(), packet.creature(), revision, data.getList("Displays", Tag.TAG_COMPOUND), data.sizeInBytes());
    }

    public static void receive(ItemDisplayPacket.Part packet) {
        var level = currentLevel();
        if (level == null) return;
        var existing = cores.get(packet.core());
        long now = level.getGameTime();
        if (packet.total() == 0) {
            if (existing != null && existing.revision == packet.revision() && existing.creature.equals(packet.creature())) {
                renew(packet.core(), existing, now);
            }
            return;
        }
        if (existing != null && packet.revision() < existing.revision) return;
        if (existing != null && packet.revision() == existing.revision && existing.creature.equals(packet.creature())) {
            renew(packet.core(), existing, now);
            return;
        }
        var complete = assembly.accept(packet, now);
        if (complete != null) replace(level, packet.core(), packet.creature(), packet.revision(), complete.rows(), complete.bytes());
    }

    private static void renew(BlockPos pos, Core existing, long now) {
        cores.put(pos, new Core(existing.creature, existing.revision, existing.displays, existing.bytes, ItemDisplaySync.Lease.refreshed(now)));
    }

    private static void discard(BlockPos pos) {
        var removed = cores.remove(pos);
        if (removed != null) cachedBytes -= removed.bytes;
    }

    private static void replace(ClientLevel level, BlockPos pos, UUID creature, long revision, ListTag rows, int bytes) {
        discard(pos);
        if (rows.isEmpty() || bytes < 1 || bytes > ItemDisplaySync.MAX_SNAPSHOT_BYTES) return;

        var displays = new ArrayList<Display>();
        int checked = 0;
        for (var value : rows) {
            if (++checked > ItemDisplaySync.MAX_ROWS) break;
            var row = (net.minecraft.nbt.CompoundTag) value;
            var anchor = ModuleAnchor.load(row.getCompound("Anchor"));
            if (anchor == null || !row.hasUUID("Id")) continue;
            ItemStack item;
            try {
                item = ItemStack.parseOptional(level.registryAccess(), row.getCompound("Item"));
            } catch (RuntimeException invalidComponent) {
                continue;
            }
            if (item.isEmpty()) continue;
            var rotation = new org.joml.Quaternionf().rotationXYZ((float) Math.toRadians(row.getFloat("displayRotX")),
                    (float) Math.toRadians(row.getFloat("displayRotY")), (float) Math.toRadians(row.getFloat("displayRotZ")));
            displays.add(new Display(row.getUUID("Id"), anchor, item, row.getFloat("displaySize"),
                    new Vec3(row.getFloat("displayX"), row.getFloat("displayY"), row.getFloat("displayZ")), rotation));
        }
        if (displays.isEmpty()) return;
        long now = level.getGameTime();
        while (cores.size() >= 256 || cachedBytes + bytes > MAX_CACHED_BYTES) discard(evictionCandidate(now));
        cores.put(pos, new Core(creature, revision, List.copyOf(displays), bytes, ItemDisplaySync.Lease.refreshed(now)));
        cachedBytes += bytes;
    }

    private static BlockPos evictionCandidate(long now) {
        for (var entry : cores.entrySet()) if (!entry.getValue().lease.visible(now)) return entry.getKey();
        return cores.keySet().iterator().next();
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES){renderMuzzles(event);return;}
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();var level=currentLevel();
        if(level==null)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();int count=0;
        // Use the actual ItemDisplay renderer, including its extra Y half-turn and model pivot.
        for(var group:previews.values())for(var preview:group){var entity=preview.entity;var world=entity.position();if(world.distanceToSqr(camera)>256*256)continue;mc.getEntityRenderDispatcher().render(entity,world.x-camera.x,world.y-camera.y,world.z-camera.z,0,1,pose,buffers,15728880);}
        long now = level.getGameTime();
        for(var entry:cores.entrySet()){
            if (!entry.getValue().lease.visible(now)) continue;
            if(!(level.getBlockEntity(entry.getKey()) instanceof HeartBlockEntity core)||!core.creatureId().equals(entry.getValue().creature))continue;
            for(var display:entry.getValue().displays){
                var body=SubLevelContainer.getContainer(level).getSubLevel(display.anchor.bodyId());if(!(body instanceof ClientSubLevel client)||body.isRemoved()||level.getBlockState(display.anchor.position(body)).isAir())continue;
                var local=display.anchor.position(body).getCenter().add(Vec3.atLowerCornerOf(display.anchor.face().getNormal()).scale(.55)).add(display.offset);var renderPose=client.renderPose(event.getPartialTick().getGameTimeDeltaPartialTick(false));var world=renderPose.transformPosition(local);if(world.distanceToSqr(camera)>256*256)continue;if(++count>512)break;
                pose.pushPose();pose.translate(world.x-camera.x,world.y-camera.y,world.z-camera.z);pose.mulPose(new org.joml.Quaternionf(renderPose.orientation()));pose.mulPose(display.rotation);
                ProjectileItemRenderer.render(mc.getItemRenderer(),display.item,level,display.id.hashCode(),display.size,pose,buffers,net.minecraft.client.renderer.LevelRenderer.getLightColor(level,BlockPos.containing(world)));pose.popPose();
            }
        }
    }
    private static void renderMuzzles(RenderLevelStageEvent event){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.level!=previous||previews.isEmpty())return;
        var camera=event.getCamera();
        var cameraUp=new Vec3(camera.getUpVector());
        var cameraLeft=new Vec3(camera.getLeftVector());
        var buffers=mc.renderBuffers().bufferSource();
        var lines=buffers.getBuffer(MuzzleOverlay.LINES);
        for(var group:previews.values())for(var preview:group){
            if(preview.muzzle==null||preview.expires<mc.level.getGameTime())continue;
            var center=preview.muzzle.subtract(camera.getPosition());
            double distance=center.length();
            if(!Double.isFinite(distance)||distance>256)continue;
            double size=Math.clamp(distance*.012,.1,.6);
            var up=cameraUp.scale(size);
            var left=cameraLeft.scale(size);
            var corners=List.of(center.add(up),center.add(left),center.subtract(up),center.subtract(left));
            for(int i=0;i<4;i++)BlueprintPreviewClient.line(event.getPoseStack(),lines,corners.get(i),corners.get((i+1)%4),0xFFAA33);
        }
        buffers.endBatch(MuzzleOverlay.LINES);
    }
    private abstract static class MuzzleOverlay extends RenderType {
        private MuzzleOverlay(){super("sablelife_muzzle_overlay",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,256,false,false,()->{},()->{});}
        private static final RenderType LINES=create("sablelife_muzzle_overlay",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,256,
            CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(PreviewLineStyle.widthState(1F,false)).setDepthTestState(NO_DEPTH_TEST)
                .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }
    private ModuleItemDisplayClient(){}
}
