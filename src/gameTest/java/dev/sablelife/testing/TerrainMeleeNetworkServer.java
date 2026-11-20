package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

@EventBusSubscriber(modid="sablelife_tests")
public final class TerrainMeleeNetworkServer {
 static int ticks,ready;static HeartBlockEntity core;static VirtualModule melee;static boolean done;static Set<UUID> acks=new HashSet<>();
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){if(Boolean.getBoolean("sablelife.terrainMeleeNetworkServer"))e.getDispatcher().register(Commands.literal("sablelife_terrain_test_ack").executes(c->{acks.add(c.getSource().getPlayerOrException().getUUID());return 1;}));}
 @SubscribeEvent public static void tick(ServerTickEvent.Post e){
  if(!Boolean.getBoolean("sablelife.terrainMeleeNetworkServer")||done)return;var server=e.getServer();var level=server.overworld();
  try{if(++ticks>3600)throw new AssertionError("Network timeout acks="+acks.size());var players=server.getPlayerList().getPlayers();if(players.size()!=2)return;
   if(ready==0&&ticks>120){for(var p:players){boolean a=p.getGameProfile().getName().equals("ReleaseA");if(a)server.getPlayerList().op(p.getGameProfile());else server.getPlayerList().deop(p.getGameProfile());p.setGameMode(a?GameType.CREATIVE:GameType.SURVIVAL);p.connection.teleport(a?8.5:10.5,105,12.5,180,28);}level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
    for(var p:BlockPos.betweenClosed(3,103,3,14,103,14))level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());var p=new BlockPos(8,104,8);level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p),new BoundingBox3i(8,104,8,8,104,8));core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(1000,0);core.terrainSettings().edit("crushProtectAdd",0,"minecraft:oak_planks");melee=core.modules().add("melee");var mark=ModuleAnchor.create(core,core.getBlockPos(),Direction.SOUTH);melee.anchors(List.of(mark,mark));melee.edit("startDistance",3,"");core.modules().changed();ready=ticks;
   }else if(ready>0&&ticks-ready==40){var ordinary=players.stream().filter(p->p.getGameProfile().getName().equals("ReleaseB")).findFirst().orElseThrow();var denied=new HeartDashboardMenu(93,ordinary.getInventory(),core);if(denied.action(ordinary,core.modules().revision(),melee.id(),"edit","meleeBreakBlocks",1,""))throw new AssertionError("Unauthorized edit succeeded");var settings=new HeartSettingsMenu(94,ordinary.getInventory(),core,5);if(settings.action(ordinary,"crushProtectPreset",0,""))throw new AssertionError("Unauthorized preset succeeded");ordinary.setGameMode(GameType.CREATIVE);for(var p:players){p.getInventory().setItem(9,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BLOCK,12));p.getInventory().setItem(10,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD_BLOCK,13));p.getInventory().setChanged();p.inventoryMenu.broadcastChanges();p.inventoryMenu.sendAllDataToRemote();core.openDashboard(p);}
   }else if(acks.size()==2){if(melee.value("meleeDrops")!=1||melee.value("meleeDebris")!=1||core.terrainSettings().drops()||!melee.meleeProtection().protects(Blocks.DIAMOND_BLOCK.defaultBlockState())||core.terrainSettings().protects(Blocks.DIAMOND_BLOCK.defaultBlockState())||melee.value("meleeBreakBlocks")!=1||!core.terrainSettings().protects(Blocks.DANDELION.defaultBlockState())||!core.terrainSettings().protects(Blocks.OAK_PLANKS.defaultBlockState()))throw new AssertionError("Real UI toggle/preset failed");core.tryActivate(players.stream().filter(p->p.getGameProfile().getName().equals("ReleaseA")).findFirst().orElseThrow());var at=BlockPos.containing(MeleeGeometry.frame(core,melee).center());level.setBlockAndUpdate(at.below(),Blocks.DIRT.defaultBlockState());level.setBlockAndUpdate(at,Blocks.DANDELION.defaultBlockState());core.modules().tick(core);if(!level.getBlockState(at).is(Blocks.DANDELION))throw new AssertionError("Preset did not protect actual flower");level.setBlockAndUpdate(at,Blocks.OBSIDIAN.defaultBlockState());core.modules().tick(core);if(!level.getBlockState(at).isAir())throw new AssertionError("UI-enabled melee did not destroy unprotected terrain");System.out.println("TERRAIN_MELEE_NETWORK_PASS two_real_clients toggle preset permission_denied stale_request bilingual_pages actual_world_break independent_drops_debris inventory_batch_without_consumption");done=true;server.halt(false);}
  }catch(Throwable f){f.printStackTrace();System.out.println("TERRAIN_MELEE_NETWORK_FAIL "+f);done=true;server.halt(false);}
 }
}
