package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.compat.StructureRemovalApi;
import net.minecraft.SharedConstants;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;
import java.util.*;

/** Artifact-only native structure validation, Photomancy export and real-world screenshots. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class SharkSchematicPreview {
    private static boolean started,built;private static volatile boolean ready;private static int ticks,shots;
    private static final Path OUT=Path.of(System.getProperty("sablelife.sharkDirectory","creations/shark_head5")).toAbsolutePath();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("sablelife.sharkPreview"))return;var mc=Minecraft.getInstance();
        if(!started){
            if(mc.getOverlay()!=null||mc.screen==null)return;started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(45);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("shark-preview-"+System.currentTimeMillis(),new LevelSettings("Shark schematic preview",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(!built){built=true;mc.getSingleplayerServer().execute(()->{
            try{
                var server=mc.getSingleplayerServer();var world=server.overworld();world.setDayTime(6000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);world.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
                var origin=new BlockPos(-5,80,0);var tag=NbtIo.readCompressed(OUT.resolve("shark_head5_create.nbt"),NbtAccounter.unlimitedHeap());
                var structure=new StructureTemplate();structure.load(world.holderLookup(Registries.BLOCK),tag);
                if(!structure.getSize().equals(new Vec3i(11,12,23)))throw new AssertionError("Unexpected shark dimensions: "+structure.getSize());
                for(int x=-25;x<=25;x++)for(int z=-20;z<=45;z++)world.setBlockAndUpdate(new BlockPos(x,76,z),(Math.floorMod(x,8)==0||Math.floorMod(z,8)==0?Blocks.POLISHED_ANDESITE:Blocks.SMOOTH_STONE).defaultBlockState());
                if(!structure.placeInWorld(world,origin,origin,new StructurePlaceSettings().setKnownShape(true).setIgnoreEntities(true),world.random,2))throw new AssertionError("Native structure placement failed");
                int count=0;var positions=new ArrayList<BlockPos>();
                for(var item:tag.getList("blocks",10)){
                    var row=(CompoundTag)item;var pos=row.getList("pos",3);var local=new BlockPos(pos.getInt(0),pos.getInt(1),pos.getInt(2));
                    var expected=NbtUtils.readBlockState(world.holderLookup(Registries.BLOCK),tag.getList("palette",10).getCompound(row.getInt("state")));
                    if(!world.getBlockState(origin.offset(local)).equals(expected))throw new AssertionError("Placed state differs at "+local);
                    if(!expected.isAir()){
                        count++;var copy=origin.offset(local).offset(128,0,0);world.setBlock(copy,expected,2);positions.add(copy);
                    }
                }
                if(count!=376)throw new AssertionError("Lost occupied blocks: "+count);
                var nativeTag=structure.save(new CompoundTag());nativeTag.putInt("DataVersion",SharedConstants.getCurrentVersion().getDataVersion().getVersion());NbtIo.writeCompressed(nativeTag,OUT.resolve("shark_head5_create.nbt"));
                var corner=origin.offset(128,0,0);var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)SubLevelAssemblyHelper.assembleBlocks(world,positions.getFirst(),positions,new BoundingBox3i(corner.getX(),corner.getY(),corner.getZ(),corner.getX()+10,corner.getY()+11,corner.getZ()+22));
                var blueprint=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(world,corner.getCenter(),List.of(body.getUniqueId()));
                if(blueprint.blockCount()!=376)throw new AssertionError("Photomancy export lost blocks: "+blueprint.blockCount());
                var blueprintTag=blueprint.save();NbtIo.writeCompressed(blueprintTag,OUT.resolve("shark_head5_photomancy.nbt"));
                if(dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.load(blueprintTag).blockCount()!=376)throw new AssertionError("Blueprint round trip failed");
                StructureRemovalApi.remove(world,body);
                var player=server.getPlayerList().getPlayers().getFirst();player.getAbilities().flying=true;player.onUpdateAbilities();
                Files.writeString(OUT.resolve("native-validation.txt"),"PASS: Minecraft structure load, 11x12x23 size, exact placement of every block state, 376 occupied blocks, native re-save; Photomancy native export/reload with 376 blocks; no core or joints.\n");
                ready=true;System.out.println("SHARK_NATIVE_PASS: "+OUT);
            }catch(Throwable failure){failure.printStackTrace();mc.execute(mc::stop);}
        });return;}
        if(!ready)return;ticks++;
        int cameraScene=ticks<120?0:ticks<190?1:2;double[][] fixedCameras={{19,93,-14},{31,87,10},{.5,116,11}};var eye=fixedCameras[cameraScene];double dx=.5-eye[0],dy=85.5-eye[1],dz=10-eye[2];
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        mc.player.getAbilities().flying=true;mc.player.setNoGravity(true);mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);mc.player.setPos(eye[0],eye[1]-mc.player.getEyeHeight(),eye[2]);mc.player.setYRot(yaw);mc.player.setXRot(pitch);
        if(ticks==10||ticks==120||ticks==190){int scene=ticks==10?0:ticks==120?1:2;mc.getSingleplayerServer().execute(()->{
            var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();double[][] cameras={{19,93,-14},{31,87,10},{.5,116,11}};var point=cameras[scene];double cx=.5-point[0],cy=85.5-point[1],cz=10-point[2];
            player.getAbilities().flying=true;player.setNoGravity(true);player.onUpdateAbilities();player.teleportTo(player.serverLevel(),point[0],point[1]-player.getEyeHeight(),point[2],(float)Math.toDegrees(Math.atan2(-cx,cz)),(float)-Math.toDegrees(Math.atan2(cy,Math.hypot(cx,cz))));
        });}
        if(ticks==270){if(shots!=3)throw new AssertionError("Missing shark screenshots");System.out.println("SHARK_PREVIEW_PASS: three actual world views");mc.stop();}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event)throws Exception{
        if(!Boolean.getBoolean("sablelife.sharkPreview")||!ready||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        int scene=ticks>=235?2:ticks>=160&&ticks<180?1:ticks>=90&&ticks<110?0:-1;if(scene<0||scene!=shots)return;
        var mc=Minecraft.getInstance();System.out.println("SHARK_CAMERA: scene="+scene+" position="+mc.player.getEyePosition()+" yaw="+mc.player.getYRot()+" pitch="+mc.player.getXRot());
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve(new String[]{"preview-perspective.png","preview-side.png","preview-top.png"}[scene]));}shots++;
    }
}
