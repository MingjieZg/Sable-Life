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
public final class ContactSensorNetworkServer {
    private static int ticks,ready;private static HeartBlockEntity core;private static VirtualModule sensor;private static boolean done;private static final Set<UUID> acks=new HashSet<>();
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){if(Boolean.getBoolean("sablelife.contactNetworkServer"))e.getDispatcher().register(Commands.literal("sablelife_contact_test_ack").executes(c->{acks.add(c.getSource().getPlayerOrException().getUUID());return 1;}));}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!Boolean.getBoolean("sablelife.contactNetworkServer")||done)return;
        var server=event.getServer();var level=server.overworld();
        try{
            if(++ticks>3200)throw new AssertionError("Contact network timeout acks="+acks.size());
            var players=server.getPlayerList().getPlayers();if(players.size()!=2)return;
            if(ready==0&&ticks>120){
                for(var p:players){boolean a=p.getGameProfile().getName().equals("ReleaseA");if(a)server.getPlayerList().op(p.getGameProfile());else server.getPlayerList().deop(p.getGameProfile());p.setGameMode(a?GameType.CREATIVE:GameType.SURVIVAL);p.connection.teleport(a?8.5:10.5,105,12.5,a?180:153,28);}
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                for(var p:BlockPos.betweenClosed(3,103,3,14,103,14))level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());
                var p=new BlockPos(8,104,8);level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p),new BoundingBox3i(8,104,8,8,104,8));core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
                int channel=core.wireless().add();core.wireless().channel(channel).set(6,3);core.wireless().channel(channel).set(0,1);
                sensor=core.modules().add("contact_sensor");sensor.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.SOUTH)));sensor.edit("sensorChannel",1,"");sensor.edit("contactOutputMode",1,"");sensor.edit("contactSizeX",3,"");sensor.edit("contactSizeY",.25,"");sensor.edit("contactSizeZ",3,"");core.modules().changed();ready=ticks;
            } else if(ready>0&&ticks-ready==40){
                var ordinary=players.stream().filter(p->p.getGameProfile().getName().equals("ReleaseB")).findFirst().orElseThrow();
                var denied=new HeartDashboardMenu(93,ordinary.getInventory(),core);
                if(denied.action(ordinary,core.modules().revision(),sensor.id(),"edit","contactDetectPlayers",1,""))throw new AssertionError("Ordinary Survival player edited Heart Core");
                ordinary.setGameMode(GameType.CREATIVE);
                for(var p:players)core.openDashboard(p);
            }
            else if(acks.size()==2){if(sensor.value("contactDetectTerrain")!=0||sensor.value("contactDetectPlayers")!=0)throw new AssertionError("Actual checkbox edit failed or ordinary player changed settings");System.out.println("CONTACT_NETWORK_PASS two_real_clients checkboxes permission_denied bilingual_pages preview_return manual");done=true;server.halt(false);}
        }catch(Throwable failure){failure.printStackTrace();System.out.println("CONTACT_NETWORK_FAIL "+failure);done=true;server.halt(false);}
    }
}
