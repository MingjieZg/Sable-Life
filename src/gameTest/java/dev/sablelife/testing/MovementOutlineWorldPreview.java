package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.sablelife.client.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

/** Native Sable body, actual preview packets and framebuffer checks in a disposable world. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class MovementOutlineWorldPreview {
    private static boolean started,built;private static volatile boolean ready;private static int ticks,shots;
    private static HeartBlockEntity core;private static VirtualModule movement,navigation;
    private static Object field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.movementOutlinePreview"))return;var mc=Minecraft.getInstance();
        if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("movement-lines-"+System.currentTimeMillis(),new LevelSettings("Isolated movement outlines",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;}
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(!built){built=true;mc.getSingleplayerServer().execute(()->{
            var server=mc.getSingleplayerServer();var world=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();world.setDayTime(6000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(world,9,87,14,153,16);
            for(int x=-8;x<=12;x++)for(int z=-12;z<=18;z++)world.setBlockAndUpdate(new BlockPos(x,78,z),Blocks.SMOOTH_STONE.defaultBlockState());
            var pos=new BlockPos(0,82,0);var blocks=new ArrayList<BlockPos>();
            for(var offset:List.of(BlockPos.ZERO,new BlockPos(1,0,0),new BlockPos(2,0,0),new BlockPos(3,0,0),new BlockPos(3,1,0),new BlockPos(3,2,0))){
                var p=pos.offset(offset);world.setBlockAndUpdate(p,offset.equals(BlockPos.ZERO)?SableLife.HEART.get().defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());blocks.add(p);
            }
            var body=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(world,pos,blocks,new dev.ryanhcode.sable.companion.math.BoundingBox3i(0,82,0,3,84,0));
            core=(HeartBlockEntity)world.getBlockEntity(body.getPlot().getCenterBlock());
            movement=core.modules().add("linear_movement");movement.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.NORTH)));movement.edit("moveDistance",6,"");movement.edit("moveObstruction",0,"");
            navigation=core.modules().add("navigation");navigation.anchors(movement.anchors());navigation.edit("radius",4,"");core.modules().changed();ready=true;
        });return;}
        if(!ready)return;ticks++;
        if(ticks<115&&ticks%4==0)mc.getSingleplayerServer().execute(()->ModuleLinearMovement.preview(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(),core,movement));
        if(ticks==75)mc.getSingleplayerServer().execute(()->{var world=mc.getSingleplayerServer().overworld();for(int x=-2;x<=6;x++)for(int y=79;y<=87;y++)world.setBlockAndUpdate(new BlockPos(x,y,-6),Blocks.RED_CONCRETE.defaultBlockState());});
        if(ticks==120)mc.getSingleplayerServer().execute(()->{var server=mc.getSingleplayerServer();for(int x=-2;x<=6;x++)for(int y=79;y<=87;y++)server.overworld().setBlockAndUpdate(new BlockPos(x,y,-6),Blocks.AIR.defaultBlockState());server.getPlayerList().getPlayers().getFirst().teleportTo(server.overworld(),5,83,7,148,12);});
        if(ticks==125)mc.getSingleplayerServer().execute(()->MarkingSessions.showPreview(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(),core,navigation));
        if(ticks==180){if(shots!=3)throw new AssertionError("Missing outline screenshots: "+shots);System.out.println("MOVEMENT_ALIGNMENT_OUTLINE_PASS: actual L-shaped body edges, green/red obstacle result, six colored axes, yellow arrow and expiry");mc.stop();}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event)throws Exception{
        if(!Boolean.getBoolean("sablelife.movementOutlinePreview")||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        int scene=ticks>=155?2:ticks>=100&&ticks<115?1:ticks>=60&&ticks<75?0:-1;if(scene<0||scene!=shots)return;
        boolean alignment=scene==2;
        if(!alignment){
            var packet=(MovementPreview)field(MovementPreviewClient.class,"preview");if(packet==null||packet.clear()!=(scene==0))throw new AssertionError("Wrong actual collision preview: "+packet);
            var meshes=(Map<?,?>)field(MovementPreviewClient.class,"meshes");int edges=0;
            for(var mesh:meshes.values()){var done=mesh.getClass().getDeclaredField("done");done.setAccessible(true);if(!done.getBoolean(mesh))throw new AssertionError("Small body shape scan unfinished");var e=mesh.getClass().getDeclaredField("edges");e.setAccessible(true);edges+=((List<?>)e.get(mesh)).size()/2;}
            if(edges<60)throw new AssertionError("Preview is only a bounding box: "+edges);
        }else{
            if(field(MovementPreviewClient.class,"preview")!=null)throw new AssertionError("Movement preview did not expire");
            var marks=(Map<?,?>)field(BlueprintPreviewClient.class,"marks");int axes=0;
            for(var kind:MarkOutline.Kind.values())if(kind.name().startsWith("ALIGN_")){
                var mark=marks.get(kind);if(mark==null)throw new AssertionError("Missing colored direction "+kind);axes++;
                var method=mark.getClass().getDeclaredMethod("packet");method.setAccessible(true);var packet=(MarkOutline)method.invoke(mark);
                if(kind==MarkOutline.Kind.ALIGN_FRONT&&(packet.color()!=0xFFCC33||packet.endpoints().size()!=10))throw new AssertionError("Yellow front arrow missing");
            }if(axes!=6)throw new AssertionError("Missing axes");
        }
        var mc=Minecraft.getInstance();var file=mc.gameDirectory.toPath().resolve("screenshots/"+new String[]{"movement-clear","movement-blocked","alignment-arrow"}[scene]+".png");java.nio.file.Files.createDirectories(file.getParent());
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            image.writeToFile(file);int pixels=0;
            for(int y=0;y<image.getHeight()*4/5;y++)for(int x=0;x<image.getWidth();x++){
                int color=image.getPixelRGBA(x,y),r=color&255,g=(color>>8)&255,b=(color>>16)&255;
                if(scene==0?g>200&&r<100&&b<100:scene==1?r>200&&g<80&&b<100:r>220&&g>150&&g<230&&b<100)pixels++;
            }
            if(pixels<30)throw new AssertionError("No visible outline pixels for scene "+scene+": "+pixels);
            System.out.println("OUTLINE_PIXELS scene="+scene+" count="+pixels);
        }shots++;
    }
}

