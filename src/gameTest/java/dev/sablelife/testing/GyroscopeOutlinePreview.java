package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.sablelife.client.BlueprintPreviewClient;
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

/** Focused native line-material and preview expiry check, without a standalone prose report. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class GyroscopeOutlinePreview {
    private static final String[] PACKS={"Bliss_v2.1.2_(Chocapic13_Shaders_edit) + Clrwl_1.0.5.zip","Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip","ComplementaryReimagined_r5.8.1.zip","MakeUp-UltraFast-9.5b.zip","photon_v1.3b.zip"};
    private static boolean started,built;private static volatile HeartBlockEntity core;
    private static VirtualModule module;private static int mode,ticks;private static String shot;
    private static final List<Map<String,Object>> results=new ArrayList<>();
    private static boolean shaders(){return Boolean.getBoolean("sablelife.gyroOutlineShaders");}
    private static String name(){return shaders()?PACKS[mode]:new String[]{"fast","fancy","fabulous"}[mode];}
    private static void server(java.util.function.Consumer<net.minecraft.server.MinecraftServer> action){var server=Minecraft.getInstance().getSingleplayerServer();server.execute(()->action.accept(server));}
    private static Map<?,?> marks()throws Exception{var f=BlueprintPreviewClient.class.getDeclaredField("marks");f.setAccessible(true);return (Map<?,?>)f.get(null);}
    private static void select()throws Exception{
        var mc=Minecraft.getInstance();
        if(shaders()){
            var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);
            config.getClass().getMethod("setShaderPackName",String.class).invoke(config,PACKS[mode]);
            config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,true);config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);
        }else{mc.options.graphicsMode().set(new GraphicsStatus[]{GraphicsStatus.FAST,GraphicsStatus.FANCY,GraphicsStatus.FABULOUS}[mode]);mc.levelRenderer.allChanged();}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.gyroOutlinePreview"))return;var mc=Minecraft.getInstance();
        if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.framerateLimit().set(60);org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("gyro-lines-"+System.currentTimeMillis(),new LevelSettings("Isolated gyroscope outlines",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;}
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(!built){built=true;server(server->{
            var world=server.overworld();world.setDayTime(6000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            var player=server.getPlayerList().getPlayers().getFirst();player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(world,6,84,10,149,14);
            for(int x=-12;x<=12;x++)for(int z=-8;z<=16;z++)world.setBlockAndUpdate(new BlockPos(x,78,z),Blocks.SMOOTH_STONE.defaultBlockState());
            var pos=new BlockPos(0,85,0);world.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
            var body=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(world,pos,List.of(pos),new dev.ryanhcode.sable.companion.math.BoundingBox3i(0,85,0,0,85,0));
            var made=(HeartBlockEntity)world.getBlockEntity(body.getPlot().getCenterBlock());module=made.modules().add("gyroscope");module.anchors(List.of(ModuleAnchor.create(made,made.getBlockPos(),Direction.NORTH)));module.gyroDown(new net.minecraft.world.phys.Vec3(.5,-Math.sqrt(.75),0));core=made;
        });return;}
        if(core==null)return;if(ticks==0)select();ticks++;
        if(ticks<=65&&ticks%5==0)server(server->{
            var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)module.anchors().getFirst().body(core);
            var pipeline=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(server.overworld()).getPipeline();pipeline.resetVelocity(body);pipeline.teleport(body,new org.joml.Vector3d(.5,85.5,.5),new org.joml.Quaterniond());
            var player=server.getPlayerList().getPlayers().getFirst();MarkingSessions.preview(player,core,List.of(module.anchors().getFirst(),module.anchors().getFirst()));ModuleGyroscope.preview(player,core,module);
        });
        if(ticks==55)shot="visible";if(ticks==85)shot="expired";
        if(ticks==95){if(++mode==(shaders()?PACKS.length:3)){System.out.println("GYRO_OUTLINE_COMPLETE scenes="+results.size());mc.stop();}else ticks=0;}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event)throws Exception{
        if(!Boolean.getBoolean("sablelife.gyroOutlinePreview")||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||shot==null)return;
        var mc=Minecraft.getInstance();String phase=shot;shot=null;int cyan=0,orange=0,green=0;var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            image.writeToFile(folder.resolve("gyro-"+mode+"-"+phase+".png"));
            for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){
                int p=image.getPixelRGBA(x,y),r=p&255,g=p>>8&255,b=p>>16&255;
                if(b>70&&g>60&&b>r*1.5&&g>r*1.5)cyan++;
                if(r>100&&r>g*1.2&&g>b*1.5)orange++;
                if(g>80&&g>r*1.5&&g>b*1.3)green++;
            }
        }
        if(phase.equals("visible")&&(marks().size()!=4||cyan<12||orange<12||green<12))throw new AssertionError("Missing solid gyro outlines: "+name()+" "+cyan+"/"+orange+"/"+green+" marks="+marks().size());
        if(phase.equals("expired")&&!marks().isEmpty())throw new AssertionError("Gyro preview did not expire");
        var row=new LinkedHashMap<String,Object>();row.put("mode",name());row.put("phase",phase);row.put("cyan",cyan);row.put("orange",orange);row.put("green",green);row.put("marks",marks().size());results.add(row);
        java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("gyro-outline-check.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));
        System.out.println("GYRO_OUTLINE_PASS "+new com.google.gson.Gson().toJson(row));
    }
}
