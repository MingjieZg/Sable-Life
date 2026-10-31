package dev.sablelife.testing;
import dev.sablelife.attack.ModuleShot;
import dev.sablelife.module.VirtualModule;
import net.minecraft.client.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class GroundStrikeItemWorldPreview {
 private static boolean started,built;private static volatile boolean ready;private static int ticks;
 @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
  if(!Boolean.getBoolean("sablelife.groundStrikeItemPreview"))return;var mc=Minecraft.getInstance();
  if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel("ground-strike-item-"+System.currentTimeMillis(),new LevelSettings("Isolated ground strike visuals",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;}
  if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
  if(!built){built=true;mc.getSingleplayerServer().execute(()->{var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(mc.getSingleplayerServer().overworld(),0,82,12,180,0);ready=true;});return;}
  if(!ready)return;ticks++;
  if(ticks==25)mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();for(int i=0;i<3;i++){var m=new VirtualModule("ground_strike");m.projectileItem(new ItemStack(Items.DIAMOND_SWORD),level.registryAccess());m.edit("strikeItemMode",1,"");m.edit("strikeItemZ",i==1?0:-135,"");m.edit("strikeItemY",i==2?45:0,"");m.edit("strikeItemSize",i==1?2:4,"");level.addFreshEntity(ModuleShot.fallingVisual(level,new Vec3((i-1)*3,85,3),.03,180,m));}});
  if(ticks==40){int count=0;for(var e:mc.level.entitiesForRendering())if(e instanceof ModuleShot shot&&shot.fallingProjectile()){count++;if(!shot.fixedFallingItem()||!shot.getItem().is(Items.DIAMOND_SWORD)||(shot.fallingItemSize()!=2&&shot.fallingItemSize()!=4)||shot.getDeltaMovement().x!=0||shot.getDeltaMovement().z!=0)throw new AssertionError("Client lost falling sword orientation/size or drifted");}if(count!=3)throw new AssertionError("Falling visual sync missing: "+count);}
  if(ticks==45||ticks==60){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("falling-swords-"+ticks+".png"));}}
  if(ticks==65){System.out.println("Ground strike item world passed: three native falling swords with synced fixed XYZ rotations, independent 2/4 block visual sizes and vertical movement");mc.stop();}
 }
}
