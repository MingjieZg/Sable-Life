package dev.sablelife.testing;

import dev.sablelife.client.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Five copied local shader packs; inspect real final pixels rather than only draw callbacks. */
final class HealthBarShaderPreview {
    private static final String[] PACKS={"Bliss_v2.1.2_(Chocapic13_Shaders_edit) + Clrwl_1.0.5.zip","Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip","ComplementaryReimagined_r5.8.1.zip","MakeUp-UltraFast-9.5b.zip","photon_v1.3b.zip"};
    private static final String[] SLUGS={"bliss-clrwl","bliss","complementary","makeup","photon"};
    private static final List<Map<String,Object>> results=new ArrayList<>();
    private static final List<String> errors=new ArrayList<>();
    private static final int LIMIT=Math.clamp(Integer.getInteger("sablelife.healthBarShaderLimit",PACKS.length),1,PACKS.length);
    private static int pack,tick;private static boolean started;private static String pending;
    private static List<int[]> regions=List.of();private static int[] visibleCounts;
    private static Object access(Object value,String name)throws Exception{var m=value.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(value);}
    private static Map<?,?> groups()throws Exception{var f=WorldHealthBarClient.class.getDeclaredField("cores");f.setAccessible(true);return (Map<?,?>)f.get(null);}
    private static void server(java.util.function.Consumer<net.minecraft.server.MinecraftServer> action){var s=Minecraft.getInstance().getSingleplayerServer();s.execute(()->action.accept(s));}
    private static void style(int value){server(s->HealthBarWorldPreview.fixtures().forEach(core->{var m=core.modules().all().stream().filter(module->module.type().equals("health_bar")).findFirst().orElseThrow();m.edit("healthBarStyle",value,"");m.edit("healthBarVisibility",0,"");core.modules().changed();}));}
    private static void wall(boolean present){server(s->{for(int x=-2;x<=2;x++)for(int y=81;y<=86;y++)s.overworld().setBlockAndUpdate(new BlockPos(x,y,3),(present?Blocks.GRAY_CONCRETE:Blocks.AIR).defaultBlockState());});}
    private static String pipeline()throws Exception{var iris=Class.forName("net.irisshaders.iris.Iris");var manager=iris.getMethod("getPipelineManager").invoke(null);var value=manager.getClass().getMethod("getPipelineNullable").invoke(manager);return value==null?"none":value.getClass().getSimpleName();}
    private static boolean active()throws Exception{var type=Class.forName("net.irisshaders.iris.api.v0.IrisApi");return (boolean)type.getMethod("isShaderPackInUse").invoke(type.getMethod("getInstance").invoke(null));}
    private static void select()throws Exception{
        style(0);wall(false);server(s->s.overworld().setDayTime(6000));
        var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);
        config.getClass().getMethod("setShaderPackName",String.class).invoke(config,PACKS[pack]);config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,true);
        config.getClass().getMethod("save").invoke(config);
        try{iris.getMethod("reload").invoke(null);}catch(Exception failure){errors.add(PACKS[pack]+": reload "+failure.getCause());}
        System.out.println("HEALTH_BAR_SHADER_START "+(pack+1)+"/"+PACKS.length+" "+PACKS[pack]);
    }
    static void tick()throws Exception{
        if(!started){started=true;select();return;}
        tick++;
        if(tick==80)pending="rectangle";
        if(tick==95)style(1);
        if(tick==120)pending="rounded";
        if(tick==140)wall(true);
        if(tick==180)pending="occluded";
        if(tick==185)wall(false);
        if(tick==200)server(s->s.overworld().setDayTime(18000));
        if(tick==280)pending="night";
        if(tick==290)server(s->HealthBarWorldPreview.fixtures().forEach(core->{var m=core.modules().all().stream().filter(module->module.type().equals("health_bar")).findFirst().orElseThrow();m.edit("healthBarVisibility",1,"");m.edit("healthBarHideDelay",20,"");core.damage(1);core.modules().changed();}));
        if(tick==330)pending="hidden";
        if(tick==360){
            if(++pack==LIMIT){writeReport();System.out.println("HEALTH_BAR_SHADER_COMPLETE: packs="+PACKS.length+", samples="+results.size()+", errors="+errors);Minecraft.getInstance().stop();return;}
            tick=0;pending=null;regions=List.of();visibleCounts=null;writeReport();select();
        }
    }
    static void render(RenderLevelStageEvent event)throws Exception{
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||pending==null)return;
        String phase=pending;pending=null;var mc=Minecraft.getInstance();
        var sample=new LinkedHashMap<String,Object>();sample.put("pack",PACKS[pack]);sample.put("phase",phase);sample.put("active",active());sample.put("pipeline",pipeline());
        var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            var file=folder.resolve(String.format(Locale.ROOT,"%02d-%s-%s.png",pack+1,SLUGS[pack],phase));image.writeToFile(file);sample.put("image",file.toString());
            if(!phase.equals("hidden")){
                var positions=new ArrayList<double[]>();var container=SubLevelContainer.getContainer(mc.level);
                for(var group:groups().values())for(var bar:(List<?>)access(group,"bars")){
                    var anchor=(ModuleAnchor)access(bar,"anchor");var body=container.getSubLevel(anchor.bodyId());if(!(body instanceof ClientSubLevel client))continue;
                    var point=client.renderPose(event.getPartialTick().getGameTimeDeltaPartialTick(false)).transformPosition(ModuleHealthBar.localPoint(anchor,body,(float)access(bar,"distance")));
                    var projection=ShockwaveMath.project(point,event.getCamera().getPosition(),event.getModelViewMatrix(),event.getProjectionMatrix());if(projection==null)continue;
                    positions.add(new double[]{point.x,projection.x()*image.getWidth(),(1-projection.y())*image.getHeight(),(float)access(bar,"width")*projection.unitRadius()*image.getHeight()/2+5,(float)access(bar,"height")*projection.unitRadius()*image.getHeight()/2+5});
                }
                positions.sort(Comparator.comparingDouble(p->p[0]));var boxes=new ArrayList<int[]>();for(var p:positions)boxes.add(new int[]{(int)(p[1]-p[3]),(int)(p[2]-p[4]),(int)(p[1]+p[3]),(int)(p[2]+p[4])});regions=List.copyOf(boxes);
            }
            var counts=new int[regions.size()];
            for(int i=0;i<regions.size();i++){var box=regions.get(i);for(int y=Math.max(0,box[1]);y<Math.min(image.getHeight(),box[3]);y++)for(int x=Math.max(0,box[0]);x<Math.min(image.getWidth(),box[2]);x++){int pixel=image.getPixelRGBA(x,y),r=pixel&255,g=(pixel>>8)&255,b=(pixel>>16)&255;if(r>45&&r>g+25&&r>b+20)counts[i]++;}}
            sample.put("redPixels",counts);sample.put("regions",regions);
            boolean okay=active()&&!pipeline().contains("Vanilla")&&counts.length==3;
            if(phase.equals("hidden"))okay&=groups().isEmpty()&&Arrays.stream(counts).allMatch(n->n<20);
            else if(phase.equals("occluded"))okay&=counts.length==3&&visibleCounts!=null&&counts[0]>20&&counts[2]>20&&counts[1]<Math.max(20,visibleCounts[1]/10);
            else okay&=Arrays.stream(counts).allMatch(n->n>20);
            if(phase.equals("rounded"))visibleCounts=counts.clone();sample.put("pass",okay);if(!okay)errors.add(PACKS[pack]+": "+phase+" red="+Arrays.toString(counts)+" pipeline="+pipeline());
        }
        results.add(sample);writeReport();System.out.println("HEALTH_BAR_SHADER_SAMPLE "+new com.google.gson.Gson().toJson(sample));
    }
    private static void writeReport()throws Exception{
        var report=new LinkedHashMap<String,Object>();report.put("renderer",org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER));report.put("samples",results);report.put("errors",errors);report.put("success",results.size()==LIMIT*5&&errors.isEmpty());
        java.nio.file.Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("health-bar-shader-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
