package dev.sablelife.testing;
import dev.sablelife.client.BundledParticles;
import dev.sablelife.module.ModuleParticles;
import net.minecraft.client.*;
import net.minecraft.client.particle.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class BundledParticleWorldPreview {
    private static boolean started,built;private static volatile boolean ready;private static int ticks;
    private static final String[] ids={"sablelife:arc","sablelife:smoke","sablelife:energy_mote","sablelife:branching_arc","sablelife:spark","sablelife:light_ring","sablelife:inward_ring","sablelife:crescent","sablelife:soft_fog","sablelife:starburst","sablelife:random_starburst"};
    private static final int[] colors={0x36BFFF,0xBA60E8,0x62FF72,0xE196FF,0xFFBB44,0x60FFFF,0xFF60BB,0xA0D0FF,0x8080FF,0xFFFF70,0xFF80D0};
    private static final Set<Particle> observed=Collections.newSetFromMap(new IdentityHashMap<>());
    private static double field(Particle p,Class<?> owner,String key)throws Exception{var f=owner.getDeclaredField(key);f.setAccessible(true);return ((Number)f.get(p)).doubleValue();}
    private static List<org.joml.Vector3f> vertices(Particle p,float partial){
        var points=new ArrayList<org.joml.Vector3f>();
        var sink=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(Particle.class.getClassLoader(),new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(proxy,method,args)->{
            if(method.getName().equals("addVertex")&&args.length==3&&args[0] instanceof Float)points.add(new org.joml.Vector3f((float)args[0],(float)args[1],(float)args[2]));
            return method.getReturnType()==void.class?null:proxy;
        });p.render(sink,Minecraft.getInstance().gameRenderer.getMainCamera(),partial);return points;
    }
    private static void continuity(Particle p)throws Exception{
        var age=Particle.class.getDeclaredField("age");age.setAccessible(true);age.setInt(p,1);
        var before=vertices(p,.9999F);p.tick();var after=vertices(p,0);
        if(before.isEmpty()||before.size()!=after.size())throw new AssertionError("Lightning render geometry missing");
        for(int i=0;i<before.size();i++)if(before.get(i).distance(after.get(i))>.001F)throw new AssertionError("Lightning geometry popped at tick boundary");
    }
    private static void observe(Particle p)throws Exception {
        int group=p instanceof BundledParticles.Arc?0:p instanceof BundledParticles.Smoke?1:p instanceof BundledParticles.Mote?2:-1;
        if(p instanceof BundledParticles.Shape){var k=BundledParticles.Shape.class.getDeclaredField("kind");k.setAccessible(true);group=3+k.getInt(p);}
        if(group<0||!observed.add(p))return;
        if(!p.getRenderType().toString().equals("SABLE_SOFT_PARTICLE"))throw new AssertionError("Bundled particle still uses hard alpha cutoff");
        if(group==2||group>=5){if(vertices(p,.5F).size()!=4)throw new AssertionError("Shape still consists of a coplanar fan");}
        if(group==10&&field(p,BundledParticles.Shape.class,"angle")==0)throw new AssertionError("Random starburst is fixed");
        if(group==9&&field(p,BundledParticles.Shape.class,"angle")!=0)throw new AssertionError("Repeated starburst orientation is randomized");
        double y=field(p,Particle.class,"y");boolean custom=y>77;
        for(int i=0;i<3;i++) {
            double expected=custom?((colors[group]>>(16-8*i))&255)/255.:1;
            if(Math.abs(field(p,Particle.class,new String[]{"rCol","gCol","bCol"}[i])-expected)>1e-5)throw new AssertionError("RGB not retained");
        }
        if(custom&&field(p,Particle.class,"lifetime")!=80)throw new AssertionError("Life override lost");
        if(custom&&Math.abs(field(p,SingleQuadParticle.class,"quadSize")-(group==0?1.3:group==1?.7:group==2?.18:group==4?.8:1.4))>1e-5)throw new AssertionError("Size override lost");
        var f=TextureSheetParticle.class.getDeclaredField("sprite");f.setAccessible(true);
        if(((net.minecraft.client.renderer.texture.TextureAtlasSprite)f.get(p)).contents().name().getPath().contains("missing"))throw new AssertionError("Missing sprite");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!Boolean.getBoolean("sablelife.bundledParticleWorldPreview"))return;var mc=Minecraft.getInstance();
        if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel("bundled-particles-"+System.currentTimeMillis(),new LevelSettings("Isolated bundled particles",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;}
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(!built){built=true;mc.getSingleplayerServer().execute(()->{var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayers().getFirst();player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(server.overworld(),0,82,10,180,0);ready=true;});return;}
        if(!ready)return;ticks++;
        if(ticks==25)mc.getSingleplayerServer().execute(()->{
            var level=mc.getSingleplayerServer().overworld();
            for(int i=0;i<ids.length;i++) {
                ModuleParticles.send(level,new ModuleParticles.Entry(ids[i],80,2,colors[i]),(i%5-2)*2,85-(i/5)*3,3,1,0,0,0,0);
                ModuleParticles.send(level,new ModuleParticles.Entry(ids[i],0,1), (i%5-2)*2,76-(i/5)*3,3,1,0,0,0,0);
            }
        });
        if(ticks>=25&&ticks<40){for(var key:List.of("particles","particlesToAdd")){var f=ParticleEngine.class.getDeclaredField(key);f.setAccessible(true);var data=f.get(mc.particleEngine);if(data instanceof Map<?,?> map){for(var queue:map.values())for(var p:(Collection<?>)queue)observe((Particle)p);}else for(var p:(Collection<?>)data)observe((Particle)p);}}
        if(ticks==40){
            BundledParticleGpuCheck.run(mc);
            for(var p:observed)if(p.isAlive()&&(p instanceof BundledParticles.Arc||p instanceof BundledParticles.Shape&&field(p,BundledParticles.Shape.class,"kind")==0)){
                var array=p.getClass().getDeclaredField(p instanceof BundledParticles.Arc?"py":"bolts");array.setAccessible(true);float[] first=((float[])array.get(p)).clone();
                var tick=ParticleEngine.class.getDeclaredMethod("tickParticle",Particle.class);tick.setAccessible(true);tick.invoke(mc.particleEngine,p);tick.invoke(mc.particleEngine,p);
                if(Arrays.equals(first,(float[])array.get(p)))throw new AssertionError("Lightning geometry did not animate");
                if(first[0]!=((float[])array.get(p))[0]||first[first.length-1]!=((float[])array.get(p))[first.length-1])throw new AssertionError("Lightning endpoints drifted");
                continuity(p);
            }
        }
        if(ticks==32||ticks==42){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("bundled-particles-world-"+ticks+".png"));}}
        if(ticks==130){if(observed.size()!=22||observed.stream().anyMatch(Particle::isAlive))throw new AssertionError("Native providers/network/expiry failed: "+observed.size());System.out.println("Bundled particles world passed: 11 native providers, default and custom network paths, RGB/Life/Size, real sprites, bounded expiry; soft alpha path, stable starburst, lightning render continuity");mc.stop();}
    }
}
