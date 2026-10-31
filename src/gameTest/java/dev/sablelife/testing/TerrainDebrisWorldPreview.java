package dev.sablelife.testing;
import dev.sablelife.module.TerrainDebrisPacket;
import net.minecraft.client.*;
import net.minecraft.client.particle.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class TerrainDebrisWorldPreview {
 private static boolean started,built;private static volatile boolean ready,noEntities;private static int ticks;private static final Set<Particle> observed=Collections.newSetFromMap(new IdentityHashMap<>());private static final int[] directions=new int[3];
 private static double field(Particle p,String key)throws Exception{var f=Particle.class.getDeclaredField(key);f.setAccessible(true);return ((Number)f.get(p)).doubleValue();}
 private static void observe(Particle p)throws Exception{if(!(p instanceof TerrainParticle)||!observed.add(p))return;double x=field(p,"x"),z=field(p,"z");int group=x< -2?0:x>2?1:z< -2?2:-1;if(group<0)throw new AssertionError("Unexpected debris origin");double speed=field(p,group==2?"zd":"xd");if((group==1?-speed:speed)<=0)throw new AssertionError("Debris did not follow positive/negative impact direction");directions[group]++;if(field(p,"lifetime")<18||field(p,"lifetime")>32)throw new AssertionError("Native fragment lifetime not bounded");}
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("sablelife.terrainDebrisWorldPreview"))return;var mc=Minecraft.getInstance();
  if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel("terrain-debris-test-"+System.currentTimeMillis(),new LevelSettings("Isolated terrain debris test",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;}
  if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
  if(!built){built=true;mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(level,0,82,10,180,0);ready=true;});return;}
  if(!ready)return;ticks++;
  if(ticks==25)mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();PacketDistributor.sendToPlayer(player,new TerrainDebrisPacket(new Vec3(-4,84,0),new Vec3(.7,0,0),Block.getId(Blocks.STONE.defaultBlockState())));PacketDistributor.sendToPlayer(player,new TerrainDebrisPacket(new Vec3(4,84,0),new Vec3(-.7,0,0),Block.getId(Blocks.DIRT.defaultBlockState())));PacketDistributor.sendToPlayer(player,new TerrainDebrisPacket(new Vec3(0,84,-4),new Vec3(0,0,.7),Block.getId(Blocks.OAK_PLANKS.defaultBlockState())));noEntities=level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(-10,75,-10,10,90,10)).isEmpty();});
  if(ticks>=25&&ticks<40){var active=ParticleEngine.class.getDeclaredField("particles");active.setAccessible(true);for(var queue:((Map<?,?>)active.get(mc.particleEngine)).values())for(var p:(Collection<?>)queue)if(p instanceof Particle particle)observe(particle);var pending=ParticleEngine.class.getDeclaredField("particlesToAdd");pending.setAccessible(true);for(var p:(Collection<?>)pending.get(mc.particleEngine))if(p instanceof Particle particle)observe(particle);}
  if(ticks==29){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("terrain-debris-world.png"));}}
  if(ticks==80){if(directions[0]!=8||directions[1]!=8||directions[2]!=8||observed.stream().anyMatch(Particle::isAlive)||!noEntities)throw new AssertionError("Debris network/lifecycle failed: "+Arrays.toString(directions));System.out.println("Terrain debris world passed: 24 native material particles, +X/-X/+Z direction, bounded expiry, no falling-block entities");mc.stop();}
 }
}
