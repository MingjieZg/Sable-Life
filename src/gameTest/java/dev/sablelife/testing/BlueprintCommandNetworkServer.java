package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.ownership.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

@EventBusSubscriber(modid="sablelife_tests")
public final class BlueprintCommandNetworkServer {
    private static int ticks,ready; private static HeartBlockEntity original; private static boolean done;
    private static final Set<UUID> acks=new HashSet<>(); private static final Map<String,Integer> requests=new HashMap<>();
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if(!Boolean.getBoolean("sablelife.commandNetworkServer"))return;
        event.getDispatcher().register(Commands.literal("sablelife_command_test_ack").executes(ctx->{acks.add(ctx.getSource().getPlayerOrException().getUUID());return 1;}));
    }
    @SubscribeEvent public static void requested(CommandEvent event) {
        if(!Boolean.getBoolean("sablelife.commandNetworkServer"))return;
        var parsed=event.getParseResults();var source=parsed.getContext().getSource();
        if(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player&&parsed.getReader().getString().startsWith("sablelife blueprint spawn"))
            requests.merge(player.getGameProfile().getName(),1,Integer::sum);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(!Boolean.getBoolean("sablelife.commandNetworkServer")||done)return;
        var server=event.getServer();var level=server.overworld();
        try {
            if(++ticks>3500)throw new IllegalStateException("Command network timeout; ready="+ready+", acks="+acks.size()+", requests="+requests);
            var players=server.getPlayerList().getPlayers();if(players.size()!=2)return;
            if(ready==0) {
                if(ticks<120)return;
                var a=players.stream().filter(p->p.getGameProfile().getName().equals("ReleaseA")).findFirst().orElseThrow();
                var b=players.stream().filter(p->p.getGameProfile().getName().equals("ReleaseB")).findFirst().orElseThrow();
                server.getPlayerList().op(a.getGameProfile());server.getPlayerList().deop(b.getGameProfile());
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                for(var p:players){p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();p.setNoGravity(true);p.connection.teleport(8.5,106,8.5,0,30);}
                for(var floor:BlockPos.betweenClosed(4,99,4,12,99,12))level.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
                var pos=new BlockPos(-8,105,-8);level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
                var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(-8,105,-8,-7,105,-8));
                original=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());original.modules().add("signal_logic").edit("logicChannel",37,"");
                var blueprint=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(level,Vec3.ZERO,List.of(body.getUniqueId()));
                var file=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,"Command Network");java.nio.file.Files.createDirectories(file.getParent());NbtIo.writeCompressed(blueprint.save(),file);
                ready=ticks;
            } else if(ticks-ready==60) {
                for(var player:players)player.sendSystemMessage(Component.literal("SL_CMD_READY"));
            } else if(acks.size()==2) {
                var a=players.stream().filter(p->p.getGameProfile().getName().equals("ReleaseA")).findFirst().orElseThrow();
                var b=players.stream().filter(p->p.getGameProfile().getName().equals("ReleaseB")).findFirst().orElseThrow();
                if(!a.hasPermissions(2)||b.hasPermissions(2))throw new IllegalStateException("Permission fixture incorrect");
                var made=LoadedCoreIndex.all(level).stream().filter(c->!c.creatureId().equals(original.creatureId())).toList();
                if(made.size()!=2||made.stream().filter(c->c.health().state()==HeartState.ALIVE).count()!=1||made.stream().filter(c->c.health().state()==HeartState.DORMANT).count()!=1)
                    throw new IllegalStateException("Expected exactly one active and one dormant creation: "+made.size());
                if(requests.getOrDefault("ReleaseB",0)!=1||requests.getOrDefault("ReleaseA",0)!=3)throw new IllegalStateException("Actual client commands were not observed: "+requests);
                if(made.stream().anyMatch(c->c.modules().all().stream().noneMatch(m->m.type().equals("signal_logic")&&m.value("logicChannel")==37)))throw new IllegalStateException("Module settings lost");
                System.out.println("BLUEPRINT_COMMAND_NETWORK_PASS two_real_clients operator_allowed ordinary_denied active_and_dormant preserved_settings");done=true;server.halt(false);
            }
        } catch(Throwable failure){failure.printStackTrace();System.out.println("BLUEPRINT_COMMAND_NETWORK_FAIL "+failure);done=true;server.halt(false);}
    }
}
