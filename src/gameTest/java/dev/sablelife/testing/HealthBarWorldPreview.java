package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
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

/** Real server snapshots and Sable-rendered bodies in an isolated screenshot world. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class HealthBarWorldPreview {
    private static boolean started,built,shot,roundedShot;private static volatile boolean ready;private static int ticks;
    private static final List<HeartBlockEntity> cores=new ArrayList<>();
    private static long outsideDraws;
    static List<HeartBlockEntity> fixtures(){return List.copyOf(cores);}
    private static Object field(String name)throws Exception{var f=WorldHealthBarClient.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.healthBarWorldPreview"))return;var mc=Minecraft.getInstance();
        if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            mc.createWorldOpenFlows().createFreshLevel("health-bars-"+System.currentTimeMillis(),new LevelSettings("Isolated marked health bars",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;}
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(!built){built=true;mc.getSingleplayerServer().execute(()->{
            var server=mc.getSingleplayerServer();var world=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();world.setDayTime(6000);world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(world,.5,81.5,15,180,1);
            for(int x=-14;x<=14;x++)for(int z=-6;z<=18;z++)world.setBlockAndUpdate(new BlockPos(x,78,z),(z%5==0?Blocks.SMOOTH_STONE:Blocks.POLISHED_ANDESITE).defaultBlockState());
            for(int i=0;i<3;i++){
                var pos=new BlockPos((i-1)*5,80,0);var blocks=new ArrayList<BlockPos>();
                world.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());blocks.add(pos);
                for(var offset:List.of(new BlockPos(0,1,0),new BlockPos(0,2,0),new BlockPos(-1,1,0),new BlockPos(1,1,0),new BlockPos(-1,0,0),new BlockPos(1,0,0),new BlockPos(-1,-1,0),new BlockPos(1,-1,0))){var p=pos.offset(offset);world.setBlockAndUpdate(p,(offset.getY()==0?Blocks.GRAY_CONCRETE:Blocks.IRON_BLOCK).defaultBlockState());blocks.add(p);}
                var body=dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(world,pos,blocks,new dev.ryanhcode.sable.companion.math.BoundingBox3i(pos.getX()-1,79,0,pos.getX()+1,82,0));
                var core=(HeartBlockEntity)world.getBlockEntity(body.getPlot().getCenterBlock());core.configure(100,100);core.editCore(player,"name",0,new String[]{"&fSentinel","&6&lIron Guardian","&dScorpion"}[i]);core.tryActivate(player);
                var module=core.modules().add("health_bar");module.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().above(2),Direction.UP)));module.edit("healthBarWidth",3.1,"");module.edit("healthBarHeight",.35,"");module.edit("startDistance",.8,"");module.edit("healthBarNameSize",new double[]{.2,.35,.5}[i],"");module.edit("healthBarNameGap",.12,"");module.edit("healthBarStyle",0,"");module.edit("healthBarVisibility",0,"");core.modules().changed();
                if(i>0)core.damage(i==1?45:80);cores.add(core);
            }
            ready=true;
        });return;}
        if(!ready)return;ticks++;
        if(Boolean.getBoolean("sablelife.healthBarShaderTests")){HealthBarShaderPreview.tick();return;}
        if(ticks==70&&(((Map<?,?>)field("cores")).size()!=3||(long)field("renderedBars")<3||(long)field("renderedNames")<3))throw new AssertionError("Marked health bars did not reach/render on the client");
        if(ticks==95)mc.getSingleplayerServer().execute(()->cores.forEach(core->{var module=core.modules().all().stream().filter(m->m.type().equals("health_bar")).findFirst().orElseThrow();module.edit("healthBarStyle",1,"");core.modules().changed();}));
        if(ticks==125)mc.getSingleplayerServer().execute(()->cores.forEach(core->{var module=core.modules().all().stream().filter(m->m.type().equals("health_bar")).findFirst().orElseThrow();module.edit("healthBarViewDistance",1,"");core.modules().changed();}));
        if(ticks==145)outsideDraws=(long)field("renderedBars");
        if(ticks==150){
            if((long)field("renderedBars")!=outsideDraws||((Map<?,?>)field("cores")).size()!=3)throw new AssertionError("Out-of-range bars drew or lost their cached snapshots");
            mc.getSingleplayerServer().execute(()->cores.forEach(core->{var module=core.modules().all().stream().filter(m->m.type().equals("health_bar")).findFirst().orElseThrow();module.edit("healthBarViewDistance",64,"");core.modules().changed();}));
        }
        if(ticks==170){
            if((long)field("renderedBars")<=outsideDraws)throw new AssertionError("Restoring visible range did not resume rendering");
            mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();cores.forEach(core->core.resetForCreative(player));});
        }
        if(ticks==205){if(!shot||!roundedShot||!((Map<?,?>)field("cores")).isEmpty())throw new AssertionError("Screenshot or release cleanup failed");System.out.println("HEALTH_BAR_WORLD_PASS: real core health, network delivery, outlined rectangle/rounded rendering, custom range hide/return and release cleanup");mc.stop();}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event)throws Exception{
        if(!Boolean.getBoolean("sablelife.healthBarWorldPreview")||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        if(Boolean.getBoolean("sablelife.healthBarShaderTests")){HealthBarShaderPreview.render(event);return;}
        boolean rectangle=!shot&&ticks>=80&&ticks<95,rounded=!roundedShot&&ticks>=120&&ticks<140;
        if(!rectangle&&!rounded)return;
        var mc=Minecraft.getInstance();
        for(var group:((Map<?,?>)field("cores")).values()){
            var accessor=group.getClass().getDeclaredMethod("bars");accessor.setAccessible(true);
            for(var bar:(List<?>)accessor.invoke(group)){
                var style=bar.getClass().getDeclaredMethod("style");style.setAccessible(true);
                if((int)style.invoke(bar)!=(rounded?1:0))throw new AssertionError("Style edit did not synchronize to client");
            }
        }
        var file=mc.gameDirectory.toPath().resolve("screenshots/marked-health-bars-"+(rounded?"rounded":"rectangle")+".png");java.nio.file.Files.createDirectories(file.getParent());
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            image.writeToFile(file);int gold=0,pink=0;
            for(int y=image.getHeight()/3;y<image.getHeight()*55/100;y++)for(int x=image.getWidth()/4;x<image.getWidth()*3/4;x++){
                int color=image.getPixelRGBA(x,y),r=color&255,g=(color>>8)&255,b=(color>>16)&255;
                if(r>190&&g>80&&g<220&&b<100)gold++;if(r>190&&g<150&&b>150)pink++;
            }
            if(gold<4||pink<4)throw new AssertionError("Named bars have no visible formatted text pixels: gold="+gold+" pink="+pink);
            System.out.println("HEALTH_BAR_NAME_PIXELS "+(rounded?"rounded":"rectangle")+" gold="+gold+" pink="+pink);
        }if(rounded)roundedShot=true;else shot=true;
    }
}
