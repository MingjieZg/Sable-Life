package dev.sablelife.testing;
import dev.sablelife.client.*;
import dev.sablelife.module.ShockwavePacket;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class ShockwaveWorldPreview {
 private static final java.util.UUID FILTER=java.util.UUID.randomUUID();
 private static boolean filterHeld,filterCleared;
 private static boolean started,built,sent;private static int ticks;private static volatile boolean serverReady;private static double baseline=70,minFov=999,finalFov=0,maxPitch;private static boolean shot;
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("sablelife.shockwaveWorldPreview"))return;var mc=Minecraft.getInstance();
  if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;mc.options.pauseOnLostFocus=false;var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel("shockwave-test-"+System.currentTimeMillis(),new LevelSettings("Isolated shockwave test",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;}
  if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
  if(!built){built=true;var server=mc.getSingleplayerServer();server.execute(()->{var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();for(int x=-12;x<=12;x++)for(int y=74;y<=90;y++)level.setBlockAndUpdate(new BlockPos(x,y,-6),((x+y)%2==0?Blocks.WHITE_CONCRETE:Blocks.BLACK_CONCRETE).defaultBlockState());level.setBlockAndUpdate(new BlockPos(-2,83,0),Blocks.REDSTONE_BLOCK.defaultBlockState());player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(level,0,80,10,180,0);serverReady=true;});return;}
  if(!serverReady)return;ticks++;
  if(ticks==25&&!sent){MovingSlashAudioCheck.run();sent=true;var server=mc.getSingleplayerServer();server.execute(()->{var level=server.overworld();var player=server.getPlayerList().getPlayers().getFirst();PacketDistributor.sendToPlayer(player,new ShockwavePacket(level.dimension().location(),new Vec3(-2,83,0),8,100,1.5f,.7f,Boolean.getBoolean("sablelife.radialBlurWorldPreview")?.8F:.3F,.35f,100,10,100,64,false,1,Boolean.getBoolean("sablelife.radialBlurWorldPreview")?1:0,.08F));});}
  if(ticks>=25&&ticks<100&&ticks%10==5||ticks==100){var server=mc.getSingleplayerServer();boolean active=ticks<100;server.execute(()->{var player=server.getPlayerList().getPlayers().getFirst();PacketDistributor.sendToPlayer(player,new dev.sablelife.module.VisualFilterPacket(server.overworld().dimension().location(),FILTER,0xB333FF,.25F,4,8,active));});}
  if(ticks==70||ticks==140){var f=ShockwaveClient.class.getDeclaredField("filters");f.setAccessible(true);var state=(VisualFilterState)f.get(null);float alpha=state.tint(mc.level.getGameTime()).alpha();if(ticks==70){if(Math.abs(alpha-.25F)>.001)throw new AssertionError("Native world filter not held: "+alpha);filterHeld=true;}else{if(alpha!=0)throw new AssertionError("Native world filter did not release: "+alpha);filterCleared=true;}}
  if(ticks==160){var field=ShockwaveRenderer.class.getDeclaredField("renderedPasses");field.setAccessible(true);long passes=field.getLong(null);if(!filterHeld||!filterCleared||!ShockwaveRenderer.ready()||passes<5||!shot||minFov>=baseline-.5||maxPitch<.01||Math.abs(finalFov-baseline)>.5||!ShockwaveClient.waves().isEmpty())throw new AssertionError("World shader/camera lifecycle failed: passes="+passes+", baseline="+baseline+", min="+minFov+", final="+finalFov+", pitch="+maxPitch+", waves="+ShockwaveClient.waves().size());System.out.println("Native world shockwave/filter passed: network receipt, held purple filter and release, render stage passes="+passes+", baseline FOV="+baseline+", min FOV="+minFov+", recovered FOV="+finalFov+", shake pitch="+maxPitch+", expired list empty");mc.stop();}
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void fov(ViewportEvent.ComputeFov e){if(!Boolean.getBoolean("sablelife.shockwaveWorldPreview")||!e.usedConfiguredFov())return;if(!sent)baseline=e.getFOV();if(!ShockwaveClient.waves().isEmpty())minFov=Math.min(minFov,e.getFOV());if(sent&&ShockwaveClient.waves().isEmpty())finalFov=e.getFOV();}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void angles(ViewportEvent.ComputeCameraAngles e){if(Boolean.getBoolean("sablelife.shockwaveWorldPreview")&&!ShockwaveClient.waves().isEmpty())maxPitch=Math.max(maxPitch,Math.abs(e.getPitch()));}
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void render(RenderLevelStageEvent e)throws Exception{if(!Boolean.getBoolean("sablelife.shockwaveWorldPreview")||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||ticks<60||ticks>90||shot||ShockwaveClient.waves().isEmpty())return;var mc=Minecraft.getInstance();var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(Boolean.getBoolean("sablelife.radialBlurWorldPreview")?"radial-native-world.png":"shockwave-native-world.png"));}shot=true;}
}
