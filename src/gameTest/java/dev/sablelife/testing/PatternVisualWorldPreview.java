package dev.sablelife.testing;

import dev.sablelife.client.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Native pixels, real network receipt, wall occlusion and expiry in independent local worlds. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class PatternVisualWorldPreview {
    private static final String[] PACKS={"Bliss_v2.1.2_(Chocapic13_Shaders_edit) + Clrwl_1.0.5.zip","Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip","ComplementaryReimagined_r5.8.1.zip","MakeUp-UltraFast-9.5b.zip","photon_v1.3b.zip"};
    private static final String[] SLUGS={"bliss-clrwl","bliss","complementary","makeup","photon"};
    private static boolean started,built,lifecycle;private static volatile boolean ready;
    private static volatile dev.sablelife.heart.HeartBlockEntity followCore;
    private static long outsideStrokes;
    private static final List<String> lifecycleChecks=new ArrayList<>();
    private static int mode,ticks;private static String pending;
    private static final List<Map<String,Object>> samples=new ArrayList<>();
    private static final List<String> errors=new ArrayList<>();
    private static int[] visible;
    private static boolean shaders(){return Boolean.getBoolean("sablelife.patternShaders");}
    private static String name(){return shaders()?SLUGS[mode]:new String[]{"fast","fancy","fabulous"}[mode];}
    private static int count()throws Exception{var field=PatternVisualClient.class.getDeclaredField("effects");field.setAccessible(true);return ((Map<?,?>)field.get(null)).size();}
    private static void server(java.util.function.Consumer<net.minecraft.server.MinecraftServer> action){var server=Minecraft.getInstance().getSingleplayerServer();server.execute(()->action.accept(server));}
    private static Vec3 point(int i){return new Vec3((i%4-1.5)*5, i<4?86:81,0);}
    private static void emit(int index,int duration){server(server->{
        var player=server.getPlayerList().getPlayers().getFirst();
        var packet=new PatternVisual(server.overworld().dimension().location(),UUID.randomUUID(),new ModuleAnchor(UUID.randomUUID(),BlockPos.ZERO,Direction.SOUTH),
                point(index),new Vec3(0,0,1),new Vec3(0,1,0),0,1.65F,1,duration,0xFF25D5,Math.min(index,6),index==7?1:0,false,64);
        PacketDistributor.sendToPlayer(player,packet);
    });}
    private static void wall(boolean present){server(server->{
        for(int x=4;x<=10;x++)for(int y=78;y<=84;y++)server.overworld().setBlockAndUpdate(new BlockPos(x,y,5),(present?Blocks.GRAY_CONCRETE:Blocks.AIR).defaultBlockState());
    });}
    private static void select()throws Exception{
        PatternVisualClient.clear();visible=null;wall(false);server(server->server.overworld().setDayTime(6000));
        var mc=Minecraft.getInstance();
        if(shaders()){
            var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);
            config.getClass().getMethod("setShaderPackName",String.class).invoke(config,PACKS[mode]);
            config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,true);config.getClass().getMethod("save").invoke(config);
            iris.getMethod("reload").invoke(null);
        }else{mc.options.graphicsMode().set(new GraphicsStatus[]{GraphicsStatus.FAST,GraphicsStatus.FANCY,GraphicsStatus.FABULOUS}[mode]);mc.levelRenderer.allChanged();}
        System.out.println("PATTERN_VISUAL_MODE "+name());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.patternVisualPreview"))return;
        var mc=Minecraft.getInstance();
        if(!started){
            if(mc.getOverlay()!=null||mc.screen==null)return;started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.framerateLimit().set(60);
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("pattern-visuals-"+System.currentTimeMillis(),
                    new LevelSettings("Isolated visual patterns",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(1234,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;
        }
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(!built){built=true;server(server->{
            var world=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();
            world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);world.setDayTime(6000);
            for(int x=-15;x<=15;x++)for(int z=-7;z<=21;z++)world.setBlockAndUpdate(new BlockPos(x,78,z),((x+z)%2==0?Blocks.SMOOTH_STONE:Blocks.POLISHED_ANDESITE).defaultBlockState());
            for(int x=-15;x<=15;x++)for(int y=79;y<=89;y++)world.setBlockAndUpdate(new BlockPos(x,y,-7),((x+y)%2==0?Blocks.WHITE_CONCRETE:Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState());
            for(int x=-15;x<=15;x++)for(int z=-5;z<=-2;z++)world.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.WATER.defaultBlockState());
            player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(world,0,82.7,20,180,0);ready=true;
        });return;}
        if(!ready)return;
        if(lifecycle){lifecycleTick();return;}
        if(ticks==0)select();ticks++;
        if(mode==0&&ticks==20){server(server->{var player=server.getPlayerList().getPlayers().getFirst();for(int i=0;i<40;i++)PacketDistributor.sendToPlayer(player,
            new PatternVisual(server.overworld().dimension().location(),UUID.randomUUID(),new ModuleAnchor(UUID.randomUUID(),BlockPos.ZERO,Direction.UP),
                new Vec3(100,84,0),new Vec3(0,0,1),new Vec3(0,1,0),0,1,1,8,0xFF25D5,6,0,false,16));});}
        if(mode==0&&ticks==24&&count()!=32)errors.add("Client cap: "+count());
        if(mode==0&&ticks==38&&count()!=0)errors.add("Expired cap fixture remained: "+count());
        if(ticks==60)for(int i=0;i<8;i++)emit(i,180);
        if(ticks==80)pending="day";
        if(ticks==100)server(server->server.overworld().setDayTime(18000));
        if(ticks==120)pending="night";
        if(ticks==140)wall(true);
        if(ticks==160)pending="occluded";
        if(ticks==165)wall(false);
        if(ticks==250)pending="expired";
        if(ticks==265){
            write();if(++mode==(shaders()?PACKS.length:3)){
                if(!shaders()){mode=2;ticks=0;lifecycle=true;PatternVisualClient.clear();}
                else{boolean success=errors.isEmpty();System.out.println("PATTERN_VISUAL_COMPLETE success="+success+" samples="+samples.size()+" errors="+errors);mc.stop();}
            }else{ticks=0;pending=null;}
        }
    }
    private static long rendered()throws Exception{var f=PatternVisualRenderer.class.getDeclaredField("renderedStrokes");f.setAccessible(true);return f.getLong(null);}
    private static void lifecycleTick()throws Exception{
        ticks++;
        if(ticks==1)server(server->{
            var world=server.overworld();world.setDayTime(6000);var pos=new BlockPos(0,84,0);
            world.setBlockAndUpdate(pos,dev.sablelife.SableLife.HEART.get().defaultBlockState());
            var body=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(world,pos,List.of(pos),new dev.ryanhcode.sable.companion.math.BoundingBox3i(0,84,0,0,84,0));
            followCore=(dev.sablelife.heart.HeartBlockEntity)world.getBlockEntity(body.getPlot().getCenterBlock());
            var m=followCore.modules().add("visual_shockwave");m.anchors(List.of(ModuleAnchor.create(followCore,followCore.getBlockPos(),Direction.SOUTH)));
            m.edit("shockPattern",3,"");m.edit("visualSize",2,"");m.edit("visualTime",200,"");m.edit("visualStart",1,"");
            m.edit("visualColorRgb",1,"");m.edit("visualColorRed",255,"");m.edit("visualColorGreen",37,"");m.edit("visualColorBlue",213,"");
        });
        if(ticks==35)server(server->PacketDistributor.sendToPlayer(server.getPlayerList().getPlayers().getFirst(),PatternVisual.create(followCore,followCore.modules().all().getFirst(),null)));
        if(ticks==55)pending="follow-before";
        if(ticks==65)server(server->{var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)followCore.modules().all().getFirst().anchors().getFirst().body(followCore);
            dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(server.overworld()).getPipeline().teleport(body,new org.joml.Vector3d(body.logicalPose().position()).add(6,0,0),new org.joml.Quaterniond().rotateZ(Math.PI/2));});
        if(ticks==95)pending="follow-moved";
        if(ticks==110)server(server->server.overworld().setBlockAndUpdate(followCore.getBlockPos(),Blocks.AIR.defaultBlockState()));
        if(ticks==130){if(count()!=0)errors.add("Removed mark retained following effect");else lifecycleChecks.add("removed marked block clears following effect");
            server(server->PacketDistributor.sendToPlayer(server.getPlayerList().getPlayers().getFirst(),new PatternVisual(server.overworld().dimension().location(),UUID.randomUUID(),new ModuleAnchor(UUID.randomUUID(),BlockPos.ZERO,Direction.SOUTH),new Vec3(0,84,0),new Vec3(0,0,1),new Vec3(0,1,0),0,2,1,30,0xFF25D5,1,0,false,1)));}
        if(ticks==138)outsideStrokes=rendered();
        if(ticks==145){if(count()!=1||rendered()!=outsideStrokes)errors.add("Out-of-range effect rendered or lost snapshot");else lifecycleChecks.add("out-of-range effect skips geometry rendering");}
        if(ticks==170){if(count()!=0)errors.add("Out-of-range effect failed to expire");else lifecycleChecks.add("out-of-range effect expires");
            write();System.out.println("PATTERN_VISUAL_COMPLETE success="+errors.isEmpty()+" samples="+samples.size()+" lifecycle="+lifecycleChecks+" errors="+errors);Minecraft.getInstance().stop();}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event)throws Exception{
        if(!Boolean.getBoolean("sablelife.patternVisualPreview")||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||pending==null)return;
        var mc=Minecraft.getInstance();String phase=pending;pending=null;
        if(phase.startsWith("follow-")){captureFollow(event,phase);return;}
        var sample=new LinkedHashMap<String,Object>();sample.put("mode",name());sample.put("phase",phase);sample.put("activeEffects",count());
        boolean enabled=false;if(shaders()){
            var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");enabled=(boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
        }
        sample.put("shaderActive",enabled);var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
        var counts=new int[8];
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            image.writeToFile(folder.resolve(name()+"-"+phase+".png"));
            for(int i=0;i<8;i++){
                var projection=ShockwaveMath.project(point(i),event.getCamera().getPosition(),event.getModelViewMatrix(),event.getProjectionMatrix());
                if(projection==null)continue;int cx=(int)(projection.x()*image.getWidth()),cy=(int)((1-projection.y())*image.getHeight());
                int radius=(int)(projection.unitRadius()*image.getHeight()*2.7);
                for(int y=Math.max(0,cy-radius);y<Math.min(image.getHeight(),cy+radius);y++)for(int x=Math.max(0,cx-radius);x<Math.min(image.getWidth(),cx+radius);x++){
                    int pixel=image.getPixelRGBA(x,y),r=pixel&255,g=pixel>>8&255,b=pixel>>16&255;
                    // MakeUp's night exposure can dim visible magenta below 55/40;
                    // require chroma separation as well as a dark-scene visibility floor.
                    if(r>20&&b>15&&r>g*1.35&&b>g*1.2)counts[i]++;
                }
            }
        }
        boolean pass=(!shaders()||enabled);
        if(phase.equals("expired"))pass&=count()==0&&Arrays.stream(counts).allMatch(value->value<12);
        else if(phase.equals("occluded"))pass&=visible!=null&&counts[7]<Math.max(12,visible[7]/8)&&counts[0]>8&&counts[6]>8;
        else pass&=Arrays.stream(counts).allMatch(value->value>8)&&count()==8;
        if(phase.equals("day"))visible=counts.clone();sample.put("magentaPixels",counts);sample.put("pass",pass);
        if(!pass)errors.add(name()+"/"+phase+": "+Arrays.toString(counts)+" active="+count());samples.add(sample);write();
        System.out.println("PATTERN_VISUAL_SAMPLE "+new com.google.gson.Gson().toJson(sample));
    }
    private static void captureFollow(RenderLevelStageEvent event,String phase)throws Exception{
        var mc=Minecraft.getInstance();var anchor=followCore.modules().all().getFirst().anchors().getFirst();
        var body=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(mc.level).getSubLevel(anchor.bodyId());
        var p=((dev.ryanhcode.sable.sublevel.ClientSubLevel)body).renderPose(event.getPartialTick().getGameTimeDeltaPartialTick(false)).transformPosition(ModuleHealthBar.localPoint(anchor,body,1));
        var projected=ShockwaveMath.project(p,event.getCamera().getPosition(),event.getModelViewMatrix(),event.getProjectionMatrix());int pixels=0;
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            image.writeToFile(mc.gameDirectory.toPath().resolve("screenshots/"+phase+".png"));
            int cx=(int)(projected.x()*image.getWidth()),cy=(int)((1-projected.y())*image.getHeight()),radius=(int)(projected.unitRadius()*image.getHeight()*2.4);
            for(int y=Math.max(0,cy-radius);y<Math.min(image.getHeight(),cy+radius);y++)for(int x=Math.max(0,cx-radius);x<Math.min(image.getWidth(),cx+radius);x++){
                int color=image.getPixelRGBA(x,y),r=color&255,g=color>>8&255,b=color>>16&255;if(r>55&&b>40&&r>g*1.35&&b>g*1.2)pixels++;
            }
        }
        if(count()!=1||pixels<20||phase.equals("follow-moved")&&p.x<4)errors.add(phase+": pixels="+pixels+" point="+p+" effects="+count());else lifecycleChecks.add(phase+": visible effect at native Sable render pose");
        System.out.println("PATTERN_VISUAL_LIFECYCLE "+phase+" pixels="+pixels+" point="+p);write();
    }
    private static void write()throws Exception{
        var report=new LinkedHashMap<String,Object>();report.put("samples",samples);report.put("errors",errors);report.put("lifecycleChecks",lifecycleChecks);
        report.put("success",errors.isEmpty()&&samples.size()==(shaders()?PACKS.length:3)*4);
        java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("pattern-visual-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
