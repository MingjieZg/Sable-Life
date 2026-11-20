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
public final class SignalBufferNetworkServer {
 static int ticks,ready,stage;static long fell=-1;static HeartBlockEntity core;static VirtualModule module;static boolean done;static Set<UUID> acks=new HashSet<>();
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){if(Boolean.getBoolean("sablelife.signalBufferNetworkServer"))e.getDispatcher().register(Commands.literal("sablelife_buffer_ack").then(Commands.argument("stage",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,2)).executes(c->{if(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"stage")==stage)acks.add(c.getSource().getPlayerOrException().getUUID());return 1;})));}
 @SubscribeEvent public static void tick(ServerTickEvent.Post e){if(!Boolean.getBoolean("sablelife.signalBufferNetworkServer")||done)return;var server=e.getServer();var level=server.overworld();try{
  if(++ticks>3600)throw new AssertionError("Buffer network timeout stage="+stage+" acks="+acks.size());var players=server.getPlayerList().getPlayers();if(players.size()!=2)return;
  if(ready==0&&ticks>100){for(var p:players){boolean a=p.getGameProfile().getName().equals("ReleaseA");if(a)server.getPlayerList().op(p.getGameProfile());else server.getPlayerList().deop(p.getGameProfile());p.setGameMode(a?GameType.CREATIVE:GameType.SURVIVAL);p.connection.teleport(a?8.5:10.5,105,12.5,180,28);}level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);for(var p:BlockPos.betweenClosed(3,103,3,14,103,14))level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());var p=new BlockPos(8,104,8);level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p),new BoundingBox3i(8,104,8,8,104,8));core=(HeartBlockEntity)level.getBlockEntity(b.getPlot().getCenterBlock());core.configure(1000,0);for(int i=0;i<2;i++){core.wireless().add();core.wireless().channel(i).set(6,3);}module=core.modules().add("wireless_redstone");module.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.EAST)));module.edit("trigger",2,"");module.edit("channel",1,"");module.edit("redstoneChannel",2,"");core.modules().changed();ready=ticks;
  }else if(ready>0&&ticks-ready==30){var b=players.stream().filter(p->p.getGameProfile().getName().equals("ReleaseB")).findFirst().orElseThrow();var denied=new HeartDashboardMenu(93,b.getInventory(),core);if(denied.action(b,core.modules().revision(),module.id(),"edit","redstoneBufferTime",20,""))throw new AssertionError("Ordinary player edited buffer");b.setGameMode(GameType.CREATIVE);for(var p:players)core.openDashboard(p);
  }else if(acks.size()==2&&stage<3){if(module.value("redstoneBufferTime")!=6)throw new AssertionError("Typed/wheel value did not reach server");acks.clear();stage++;if(stage<3){module.edit("redstoneStrengthMode",stage==1?1:2,"");module.edit("redstoneMode",stage==1?1:0,"");core.modules().changed();for(var p:players)core.openDashboard(p);}else{module.edit("redstoneStrengthMode",1,"");module.edit("redstoneMode",0,"");core.tryActivate(players.stream().filter(p->p.getGameProfile().getName().equals("ReleaseA")).findFirst().orElseThrow());core.wireless().programOutputs("network_input",Map.of(0,7));core.modules().events().tick(core);core.wireless().programOutputs("network_input",Map.of());core.modules().events().tick(core);if(core.wireless().output(1)!=7)throw new AssertionError("Real UI buffer did not retain output");fell=level.getGameTime();}}
  else if(stage==3&&level.getGameTime()>=fell+7){core.modules().events().tick(core);if(core.wireless().output(1)!=0)throw new AssertionError("Real UI buffer did not expire");System.out.println("SIGNAL_BUFFER_NETWORK_PASS two_real_clients hold_pulse_ramp typed_value wheel_005 permissions bilingual_densities real_output");done=true;server.halt(false);}
 }catch(Throwable f){f.printStackTrace();System.out.println("SIGNAL_BUFFER_NETWORK_FAIL "+f);done=true;server.halt(false);}}
}
