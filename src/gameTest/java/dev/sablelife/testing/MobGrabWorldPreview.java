package dev.sablelife.testing;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class MobGrabWorldPreview {
    private static boolean started,built;private static volatile boolean ready,released;private static int ticks;private static UUID pig,zombie;private static int observations;
    private static final Vec3[] bases={new Vec3(-2,68,0),new Vec3(2,68,0)};
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!Boolean.getBoolean("sablelife.mobGrabWorldPreview"))return;var mc=Minecraft.getInstance();
        if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel("mob-grab-"+System.currentTimeMillis(),new LevelSettings("Isolated ordinary mob restraint",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;}
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(!built){built=true;mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();level.setDayTime(18000);var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.getAbilities().flying=true;p.onUpdateAbilities();p.teleportTo(level,0,68,10,180,8);var animal=EntityType.PIG.create(level);animal.setPos(bases[0]);level.addFreshEntity(animal);pig=animal.getUUID();var monster=EntityType.ZOMBIE.create(level);monster.setPos(bases[1]);level.addFreshEntity(monster);zombie=monster.getUUID();ready=true;});return;}
        if(!ready)return;ticks++;
        if(ticks==20)mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();long start=level.getGameTime();if(Boolean.getBoolean("sablelife.mobFreezeWorldPreview")){
            for(var id:List.of(pig,zombie))((Mob)level.getEntity(id)).setPos(id.equals(pig)?bases[0]:bases[1]);
            var core=new dev.sablelife.heart.HeartBlockEntity(new net.minecraft.core.BlockPos(0,68,0),dev.sablelife.SableLife.HEART.get().defaultBlockState());core.setLevel(level);var m=core.modules().add("freeze");m.edit("freezeTarget",1,"");m.edit("freezeDuration",60,"");
            if(ModuleFreeze.capture(level,core,m,List.of(new Vec3(0,68,0)))!=2)throw new AssertionError("Actual area freeze did not capture both mobs");return;}
            for(int i=0;i<2;i++){final int index=i;var mob=(Mob)level.getEntity(i==0?pig:zombie);if(!MobGrab.grab(mob,new PlayerFreeze.Source(UUID.randomUUID(),UUID.randomUUID()),60,m->bases[index].add((level.getGameTime()-start)*.025,Math.sin((level.getGameTime()-start)*.06)*.15,0)))throw new AssertionError("Native mob attachment failed");}});
        if(ticks>=35&&ticks<=65){for(var id:List.of(pig,zombie)){var entity=java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false).filter(ent->ent.getUUID().equals(id)).findFirst().orElse(null);if(entity!=null){if(entity.getY()<67.5||entity.getX()<(id.equals(pig)?-2:2)-.5)throw new AssertionError("Client mob did not follow held server position");if(Boolean.getBoolean("sablelife.mobFreezeWorldPreview")&&Math.abs(entity.getX()-(id.equals(pig)?-2:2))>.4)throw new AssertionError("Frozen client mob moved from capture point");observations++;}}}
        if(ticks==50){var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve(Boolean.getBoolean("sablelife.mobFreezeWorldPreview")?"frozen-mobs.png":"held-mobs.png"));}}
        if(ticks==95)mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();var a=(Mob)level.getEntity(pig);var b=(Mob)level.getEntity(zombie);released=!MobGrab.frozen(a)&&!MobGrab.frozen(b)&&!a.isNoAi()&&!b.isNoAi()&&a.getY()<67.5&&b.getY()<67.5;});
        if(ticks==105){if(!released||observations<40)throw new AssertionError("Native mob tracking/expiry recovery failed: "+observations+"/"+released);System.out.println((Boolean.getBoolean("sablelife.mobFreezeWorldPreview")?"Mob freeze world passed:":"Mob grab world passed:")+" pig and zombie tracked while held, no persistent NoAI flags, native gravity resumed after expiry");mc.stop();}
    }
}
