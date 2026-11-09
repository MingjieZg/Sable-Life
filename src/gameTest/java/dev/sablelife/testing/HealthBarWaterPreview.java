package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.client.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

/** Shoreline pixel regression: one full bar straddles land and water; a real wall must still hide it. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class HealthBarWaterPreview {
    private static final String[] PACKS={"Bliss_v2.1.2_(Chocapic13_Shaders_edit) + Clrwl_1.0.5.zip","Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip","ComplementaryReimagined_r5.8.1.zip","MakeUp-UltraFast-9.5b.zip","photon_v1.3b.zip"};
    private static final List<Map<String,Object>> results=new ArrayList<>();
    private static boolean started,built;private static volatile boolean ready;private static int ticks,mode=Integer.getInteger("sablelife.healthBarWaterMode",0);private static String pending;
    private static HeartBlockEntity core;private static ModuleAnchor anchor;private static boolean wall;
    private static double brightness(int pixel){return .2126*(pixel&255)+.7152*((pixel>>8)&255)+.0722*((pixel>>16)&255);}
    private static void server(java.util.function.Consumer<net.minecraft.server.MinecraftServer> action){var s=Minecraft.getInstance().getSingleplayerServer();s.execute(()->action.accept(s));}
    private static void select()throws Exception{
        var mc=Minecraft.getInstance();ticks=0;
        if(mode<3){mc.options.graphicsMode().set(GraphicsStatus.values()[mode]);mc.levelRenderer.allChanged();}
        else{
            var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);
            config.getClass().getMethod("setShaderPackName",String.class).invoke(config,PACKS[mode-3]);config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,true);config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);
        }
        System.out.println("HEALTH_BAR_WATER_START mode="+mode);
    }
    private static void wall(boolean present){wall=present;server(s->{for(int x=-6;x<=6;x++)for(int y=81;y<=88;y++)s.overworld().setBlockAndUpdate(new BlockPos(x,y,4),(present?Blocks.STONE:Blocks.AIR).defaultBlockState());});}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.healthBarWaterPreview"))return;var mc=Minecraft.getInstance();
        if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
            if(Boolean.getBoolean("sablelife.healthBarWaterShaders")){var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,false);iris.getMethod("reload").invoke(null);}
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("water-bars-"+System.currentTimeMillis(),new LevelSettings("Isolated shoreline health bars",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;}
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(!built){built=true;server(s->{var l=s.overworld();var player=s.getPlayerList().getPlayers().getFirst();l.setDayTime(6000);l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,s);player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(l,.5,87,16,180,15);
            for(int x=-24;x<=32;x++)for(int z=-40;z<=24;z++){l.setBlockAndUpdate(new BlockPos(x,77,z),Blocks.STONE.defaultBlockState());for(int y=78;y<=80;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),(x<0?Blocks.GRASS_BLOCK:Blocks.WATER).defaultBlockState());}
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)l.setBlockAndUpdate(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState());
            var p=new BlockPos(0,81,0);l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.above(),Blocks.IRON_BLOCK.defaultBlockState());
            var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.above()),new BoundingBox3i(0,81,0,0,82,0));core=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());core.configure(100,100);core.editCore(player,"name",0,"&dScorpion");core.tryActivate(player);
            var bar=core.modules().add("health_bar");anchor=ModuleAnchor.create(core,core.getBlockPos().above(),Direction.UP);bar.anchors(List.of(anchor));bar.edit("healthBarWidth",8,"");bar.edit("healthBarHeight",.4,"");bar.edit("healthBarStyle",1,"");bar.edit("healthBarVisibility",0,"");bar.edit("healthBarName",1,"");bar.edit("healthBarNameSize",.5,"");bar.edit("healthBarNameGap",.2,"");core.modules().changed();ready=true;});return;}
        if(!ready)return;if(ticks==0)select();ticks++;
        if(ticks==90)pending="shore";
        if(ticks==105)wall(true);
        if(ticks==145)pending="wall";
        if(ticks==155)wall(false);
        if(ticks==160)server(s->{var b=core.modules().all().stream().filter(m->m.type().equals("health_bar")).findFirst().orElseThrow();b.edit("healthBarWidth",1.6,"");b.edit("healthBarHeight",.1,"");b.edit("healthBarVisibility",1,"");b.edit("healthBarHideDelay",2000,"");b.edit("healthBarNameSize",.2,"");b.edit("healthBarNameGap",.08,"");core.editCore(s.getPlayerList().getPlayers().getFirst(),"name",0,"&d&l测试Name");core.damage(35);core.modules().changed();});
        if(ticks==170)pending="defaults";
        if(ticks==185){int max=Boolean.getBoolean("sablelife.healthBarWaterShaders")?Integer.getInteger("sablelife.healthBarWaterEnd",8):3;if(++mode==max){var report=new LinkedHashMap<String,Object>();report.put("samples",results);report.put("success",results.stream().allMatch(r->Boolean.TRUE.equals(r.get("pass"))));java.nio.file.Files.writeString(mc.gameDirectory.toPath().resolve("water-health-bar-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println("HEALTH_BAR_WATER_COMPLETE "+report.get("success"));mc.stop();return;}server(s->{var b=core.modules().all().stream().filter(m->m.type().equals("health_bar")).findFirst().orElseThrow();b.edit("healthBarWidth",8,"");b.edit("healthBarHeight",.4,"");b.edit("healthBarVisibility",0,"");b.edit("healthBarNameSize",.5,"");b.edit("healthBarNameGap",.2,"");core.editCore(s.getPlayerList().getPlayers().getFirst(),"name",0,"&dScorpion");core.adjustOwnHealth(100,false);core.modules().changed();});ticks=0;}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event)throws Exception{
        if(!Boolean.getBoolean("sablelife.healthBarWaterPreview")||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||pending==null)return;
        var mc=Minecraft.getInstance();var phase=pending;pending=null;var body=(ClientSubLevel)SubLevelContainer.getContainer(mc.level).getSubLevel(anchor.bodyId());
        var point=body.renderPose(event.getPartialTick().getGameTimeDeltaPartialTick(false)).transformPosition(ModuleHealthBar.localPoint(anchor,body,.75));
        var projection=ShockwaveMath.project(point,event.getCamera().getPosition(),event.getModelViewMatrix(),event.getProjectionMatrix());if(projection==null)throw new AssertionError("Bar outside view");
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            var path=mc.gameDirectory.toPath().resolve("screenshots/"+mode+"-"+phase+".png");java.nio.file.Files.createDirectories(path.getParent());image.writeToFile(path);
            int cx=(int)(projection.x()*image.getWidth()),cy=(int)((1-projection.y())*image.getHeight());double pixels=projection.unitRadius()*image.getHeight();
            var samples=new ArrayList<Integer>();int red=0;
            for(int i=-3;i<=3;i++){int x=cx+(int)(i*pixels*(phase.equals("defaults")?.2:1));int rgb=image.getPixelRGBA(x,cy);samples.add(rgb);int r=rgb&255,g=(rgb>>8)&255,b=(rgb>>16)&255;if(r>70&&r>g+40&&r>b+30)red++;}
            // Bloom may tint an unfilled slot red. Require a darker tail instead of a fixed red-pixel count there.
            boolean pass=wall?red==0:phase.equals("defaults")?red>=3&&(red<=6||brightness(samples.get(0))>brightness(samples.get(6))*1.25):red==7;
            int namePixels=0;
            // Sample only the name, with a color distinct from the red bar and its bloom.
            double barHeight=phase.equals("defaults")?.1:.4,nameGap=phase.equals("defaults")?.08:.2,nameSize=phase.equals("defaults")?.2:.5;
            for(int y=Math.max(0,cy-(int)Math.ceil(pixels*(barHeight/2+nameGap+nameSize))-2);y<Math.min(image.getHeight(),cy-(int)Math.floor(pixels*(barHeight/2+nameGap))+2);y++)for(int x=Math.max(0,cx-(int)Math.ceil(pixels));x<Math.min(image.getWidth(),cx+(int)Math.ceil(pixels));x++){
                int rgb=image.getPixelRGBA(x,y),r=rgb&255,g=(rgb>>8)&255,b=(rgb>>16)&255;if(r>110&&b>100&&g<Math.min(r,b)*.8)namePixels++;
            }
            pass&=wall?namePixels==0:namePixels>3;
            boolean active=false;String pipeline="vanilla";
            if(Boolean.getBoolean("sablelife.healthBarWaterShaders")){
                var api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");active=(boolean)api.getMethod("isShaderPackInUse").invoke(api.getMethod("getInstance").invoke(null));
                var manager=Class.forName("net.irisshaders.iris.Iris").getMethod("getPipelineManager").invoke(null);var value=manager.getClass().getMethod("getPipelineNullable").invoke(manager);pipeline=value==null?"none":value.getClass().getSimpleName();
            }
            pass&=mode<3?!active:active&&!pipeline.contains("Vanilla")&&!pipeline.equals("none");

            var row=new LinkedHashMap<String,Object>();row.put("mode",mode<3?GraphicsStatus.values()[mode].toString():PACKS[mode-3]);row.put("phase",phase);row.put("shaderActive",active);row.put("pipeline",pipeline);row.put("redSamples",red);row.put("namePixels",namePixels);row.put("pixels",samples);row.put("center",List.of(cx,cy));row.put("worldPoint",List.of(point.x,point.y,point.z));row.put("pass",pass);row.put("image",path.toString());results.add(row);System.out.println("HEALTH_BAR_WATER_SAMPLE "+new com.google.gson.Gson().toJson(row));
        }
    }
}
