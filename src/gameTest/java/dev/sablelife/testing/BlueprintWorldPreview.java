package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import dev.sablelife.ownership.*;
import dev.sablelife.compat.photomancy.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Isolated integrated world: real server preview packet, actual line rendering and expiry. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class BlueprintWorldPreview {
    private static boolean started,built,valkyrie;private static volatile boolean ready;private static int ticks;
    private static dev.sablelife.heart.HeartBlockEntity core;private static VirtualModule module;
    private static Object preview()throws Exception{var f=dev.sablelife.client.BlueprintPreviewClient.class.getDeclaredField("preview");f.setAccessible(true);return f.get(null);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.blueprintWorldPreview"))return;var mc=Minecraft.getInstance();
        if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel("blueprint-preview-"+System.currentTimeMillis(),new LevelSettings("Isolated blueprint outline",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;}
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(!built){built=true;mc.getSingleplayerServer().execute(()->{
            var server=mc.getSingleplayerServer();var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(level,.5,84,17,180,15);level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            var pos=new BlockPos(0,80,0);level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());var body=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new dev.ryanhcode.sable.companion.math.BoundingBox3i(0,80,0,1,80,0));core=(dev.sablelife.heart.HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
            var template=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(level,new Vec3(body.logicalPose().position().x(),body.logicalPose().position().y(),body.logicalPose().position().z()),List.of(body.getUniqueId())).save();
            try {
                var file = dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server, "wild-outline-test");
                java.nio.file.Files.createDirectories(file.getParent());
                net.minecraft.nbt.NbtIo.writeCompressed(template, file);
                WildEncounters.get(server).configure(level, "wild-outline-test", 4, 0, 120, false, false);
                java.nio.file.Files.deleteIfExists(file);
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            module=core.modules().add("blueprint_spawn");module.edit("spawnPosition",1,"");module.edit("startDistance",6,"");module.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.SOUTH)));BlueprintSpawns.install(core,module,"outline-test",SpawnTemplateBridge.inspect(template));ready=true;
        });return;}
        if(!ready)return;ticks++;
        if(ticks>=25&&ticks<=65&&ticks%5==0){
            boolean sendModels=ticks==25;
            mc.getSingleplayerServer().execute(()->BlueprintSpawns.preview(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(),core,module,sendModels));
        }
        if(ticks==40){var packet=(ModuleNetworking.BlueprintPreview)preview();if(packet==null)throw new AssertionError("Missing packet");var center=packet.min().add(packet.max()).scale(.5);var eye=center.add(8,5,10);var delta=center.subtract(eye);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z)));mc.player.setPos(eye.x,eye.y-mc.player.getEyeHeight(),eye.z);mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().teleportTo(mc.getSingleplayerServer().overworld(),eye.x,eye.y-mc.player.getEyeHeight(),eye.z,yaw,pitch));}
        if(ticks>=45&&ticks<=65){var packet=(ModuleNetworking.BlueprintPreview)preview();if(packet!=null){var center=packet.min().add(packet.max()).scale(.5);var eye=center.add(8,5,10);var delta=center.subtract(eye);mc.player.setPos(eye.x,eye.y-mc.player.getEyeHeight(),eye.z);mc.player.setYRot((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));mc.player.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));}}
        if(ticks==65){System.out.println("PREVIEW_CAMERA "+mc.player.position()+" "+mc.player.getYRot()+" "+mc.player.getXRot()+" "+preview());if(preview()==null)throw new AssertionError("Real preview packet did not reach client");var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("blueprint-outline.png"));}}
        if(ticks==75){var packet=(ModuleNetworking.BlueprintPreview)preview();if(packet==null)throw new AssertionError("Missing outline");mc.getSingleplayerServer().execute(()->net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(),new ModuleNetworking.BlueprintPreview(packet.dimension(),packet.min(),packet.max(),packet.anchor(),Vec3.ZERO)));}
        if(ticks==82){var packet=(ModuleNetworking.BlueprintPreview)preview();if(packet==null||packet.forward().lengthSqr()!=0)throw new AssertionError("White entity outline not received");try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("screenshots/entity-white-outline.png"));}}
        if(ticks==86){
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();var pos=new BlockPos(6,80,4);
                level.setBlockAndUpdate(pos,Blocks.DISPENSER.defaultBlockState().setValue(net.minecraft.world.level.block.DispenserBlock.FACING,Direction.SOUTH));
                level.setBlockAndUpdate(pos.east(),SableLife.HEART.get().defaultBlockState());
                for(int dx=-2;dx<=3;dx++)for(int dz=-2;dz<=2;dz++)level.setBlockAndUpdate(pos.offset(dx,-1,dz),Blocks.GRASS_BLOCK.defaultBlockState());
                var eye=pos.getCenter().add(3,2,4.5);var delta=pos.getCenter().subtract(eye);
                player.teleportTo(level,eye.x,eye.y-player.getEyeHeight(),eye.z,(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
            });
        }
        if(ticks>=88&&ticks<=105&&ticks%3==1){
            mc.getSingleplayerServer().execute(()->{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                MarkOutline.send(player,MarkOutline.Kind.MARK,MarkOutline.MARK_COLOR,MarkOutline.box(new Vec3(6,80,4),new Vec3(7,81,5),p->p));
            });
        }
        if(ticks==100){try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("screenshots/mark-yellow-thicker.png"));}}
        if (ticks == 110) {
            if (preview() != null) throw new AssertionError("Blueprint outline did not expire");
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 24);
                player.teleportTo(level, .5, ground + 10, .5, 0, 18);
                try {
                    if (!WildEncounters.get(server).previewOrTry(player, "wild-outline-test", true)) throw new AssertionError("Wild preview failed");
                } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            });
        }
        if (ticks == 135) {
            var packet = (ModuleNetworking.BlueprintPreview) preview();
            if (packet == null || packet.duration() != 100 || Math.abs(packet.min().z - 24) > 2)
                throw new AssertionError("Wild outline did not remain visible ahead of the player");
            var field=dev.sablelife.client.BlueprintPreviewClient.class.getDeclaredField("projection");
            field.setAccessible(true);
            if(!(field.get(null) instanceof BlueprintProjection projection)||!projection.id().equals(packet.projection())
                    ||projection.parts().stream().mapToInt(p->p.blocks().size()).sum()!=2)
                throw new AssertionError("Wild preview did not render the projected blocks");
            try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                image.writeToFile(mc.gameDirectory.toPath().resolve("screenshots/wild-ground-outline.png"));
            }
        }
        if (ticks == 225) {
            if (preview() != null) throw new AssertionError("Wild outline did not expire");
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                if (BlueprintSpawns.snapshot(core, module).getInt("Count") != 0 || WildEncounters.get(server).snapshot(server.overworld(), "wild-outline-test").getBoolean("Active"))
                    throw new AssertionError("Preview generated a blueprint");
            });
            valkyrie=Boolean.getBoolean("sablelife.valkyrieReplay")&&java.nio.file.Files.isRegularFile(mc.gameDirectory.toPath().resolve("Sable-Schematics/valkyrie shuttle.nbt"));
            if(valkyrie){
                mc.getSingleplayerServer().execute(()->{
                    try{
                        var server=mc.getSingleplayerServer();var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();
                        player.teleportTo(level,.5,90,.5,0,0);
                        var data=WildEncounters.get(server);data.loadBlueprint(level,"","valkyrie shuttle",false);
                        data.configure(level,"valkyrie shuttle",0,60,10,false,true,dev.sablelife.heart.FactionRegistry.WILDS,new WildEncounters.Conditions(true,true,false,Set.of(),180));
                        var scan=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues().get("wildPlacementScanVoxels");int oldScan=scan.get();
                        try{
                            scan.set(262144);
                            var result=data.attemptPreviewOrSpawn(player,"valkyrie shuttle",true);
                            if(!result.success()||result.reason().isEmpty())throw new AssertionError("Blocked real Valkyrie did not preview: "+result.reason());
                            System.out.println("VALKYRIE_WORLD_PREVIEW: "+result.reason());
                        }finally{scan.set(oldScan);}
                    }catch(java.io.IOException failure){throw new IllegalStateException(failure);}
                });
            }else{
                System.out.println("BLUEPRINT_WORLD_PASS: shared outline, wild preview ahead, five-second expiry");
                mc.stop();
            }
        }
        if(valkyrie&&ticks>=240&&ticks<=265){
            var packet=(ModuleNetworking.BlueprintPreview)preview();if(packet==null)throw new AssertionError("Real Valkyrie outline did not reach client");
            var box=new AABB(packet.min(),packet.max());var center=box.getCenter();
            var eye=center.add(Math.max(20,box.getXsize()*.7),Math.max(15,box.getYsize()*.7),-Math.max(30,box.getZsize()*.9));var delta=center.subtract(eye);
            float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z)));
            mc.player.setPos(eye.x,eye.y-mc.player.getEyeHeight(),eye.z);mc.player.setYRot(yaw);mc.player.setXRot(pitch);
            if(ticks==240)mc.getSingleplayerServer().execute(()->mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst().teleportTo(mc.getSingleplayerServer().overworld(),eye.x,eye.y-mc.player.getEyeHeight(),eye.z,yaw,pitch));
            if(ticks==265){
                try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("screenshots/valkyrie-natural-preview.png"));}
                mc.getSingleplayerServer().execute(()->{
                    var server=mc.getSingleplayerServer();if(WildEncounters.get(server).snapshot(server.overworld(),"valkyrie shuttle").getInt("ActiveCount")!=0)throw new AssertionError("Real preview spawned the Valkyrie");
                });
            }
        }
        if(valkyrie&&ticks==350){if(preview()!=null)throw new AssertionError("Real Valkyrie outline did not expire");System.out.println("BLUEPRINT_WORLD_PASS: ordinary and real Valkyrie natural preview, camera, read-only state and expiry");mc.stop();}
    }
}
