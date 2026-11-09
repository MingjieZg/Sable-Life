package dev.sablelife.testing;

import dev.sablelife.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import java.nio.file.*;

@EventBusSubscriber(modid="sablelife_tests")
public final class ReleaseNetworkServer {
    private static int ticks,phase,since,readyTicks;private static boolean done;
    private static HeartBlockEntity normal,survival;private static final List<String> evidence=new ArrayList<>();
    private static void check(boolean v,String s){if(!v)throw new IllegalStateException(s);}
    private static void log(String s){evidence.add(s);System.out.println("RELEASE_NETWORK: "+s);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(!Boolean.getBoolean("sablelife.releaseNetworkServer")||done)return;
        var server=e.getServer();var level=server.overworld();
        try{
            if(++ticks>6000)throw new IllegalStateException("Network scenario timed out at phase "+phase);
            var players=server.getPlayerList().getPlayers();
            if(phase==0){
                if(players.size()!=2)return;
                // Wait for initial terrain loading/teleport acknowledgements before opening menus.
                if(++readyTicks<120)return;
                check(players.stream().allMatch(p->p.getGameProfile().getName().startsWith("Release")),"Unexpected test player");
                check(players.stream().noneMatch(p->p instanceof net.neoforged.neoforge.common.util.FakePlayer),"Network players must be real connections");
                log("two_real_clients_connected: "+players.stream().map(p->p.getGameProfile().getName()).toList());
                for(var p:players)p.setGameMode(GameType.CREATIVE);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                normal=make(level,new BlockPos(0,100,0),false);survival=make(level,new BlockPos(16,100,0),true);
                phase=5;since=ticks;
            }else if(phase==5&&ticks-since>=40){
                // Native plot/chunk integration can replace the assembly-time block entity.
                // A player opens the current block entity, not this fixture's earlier reference.
                var oldNormal=normal;var oldSurvival=survival;
                normal=(HeartBlockEntity)level.getBlockEntity(oldNormal.getBlockPos());
                survival=(HeartBlockEntity)level.getBlockEntity(oldSurvival.getBlockPos());
                check(normal!=null&&survival!=null&&!normal.isRemoved()&&!survival.isRemoved(),"Native assembly did not produce live cores");
                log("assembly_reference_refresh: ordinary="+(normal!=oldNormal)+", survival="+(survival!=oldSurvival));
                normal.modules().add("wireless_redstone");normal.modules().changed();normal.setChanged();
                open(normal,players);phase=1;since=ticks;
            }else if(phase==1&&ticks-since>=200){
                check(normal.modules().all().size()==2,"Stale edit check: modules="+normal.modules().all().size()+", core="+ModuleArena.corePosition(normal)+", removed="+normal.isRemoved()+", sameBlockEntity="+(level.getBlockEntity(normal.getBlockPos())==normal)+", players="+players.stream().map(p->p.position()+" "+p.containerMenu.getClass().getSimpleName()+" access="+normal.canAccessEditor(p)).toList());
                log("one_edit_accepted_stale_revision_rejected");
                fire(normal,players.getFirst());phase=2;since=ticks;
            }else if(phase==2&&ticks-since==5){
                check(firing(normal),"Ordinary core laser did not fire before network release");open(normal,players);
            }else if(phase==2&&ticks-since>30&&normal.health().state()==HeartState.DORMANT){
                check(!firing(normal),"Ordinary network Release left laser firing");log("ordinary_firing_release_over_network");
                survival.depositSurvivalTokens(players.getFirst(),new ItemStack(Items.NETHERITE_INGOT,3));
                fire(survival,players.getFirst());phase=3;since=ticks;
            }else if(phase==3&&ticks-since==5){
                check(firing(survival),"Survival laser did not fire before network release");open(survival,players);
            }else if(phase==3&&ticks-since>30&&survival.health().state()==HeartState.DORMANT){
                check(!firing(survival)&&survival.health().current()>0&&survival.survivalTokenBalance()>0,"Survival release lost balance or left runtime");
                log("survival_firing_release_preserved_life_and_energy: "+survival.health().current()+" / "+survival.survivalTokenBalance());
                check(players.size()==2,"Client disconnected during test");phase=4;since=ticks;
            }else if(phase==4&&ticks-since>=40){
                check(players.size()==2,"Client disconnected before final synchronization");log("post_release_sync_window_completed");finish(server,true,"");
            }
        }catch(Throwable failure){failure.printStackTrace();finish(server,false,failure.toString());}
    }
    private static HeartBlockEntity make(ServerLevel level,BlockPos pos,boolean survival){
        for(var floor:BlockPos.betweenClosed(pos.offset(-3,-1,-3),pos.offset(3,-1,3)))level.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
        level.getChunk(pos);level.setBlockAndUpdate(pos,(survival?SableLife.SURVIVAL_LIFE_CORE.get():SableLife.HEART.get()).defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var b=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        var c=(HeartBlockEntity)level.getBlockEntity(b.getPlot().getCenterBlock());c.configure(100,20);return c;
    }
    private static void open(HeartBlockEntity c,List<ServerPlayer> players){
        for(var p:players){p.connection.teleport(ModuleArena.corePosition(c).x,ModuleArena.corePosition(c).y+2,ModuleArena.corePosition(c).z,0,0);p.getAbilities().flying=true;p.onUpdateAbilities();p.setNoGravity(true);p.openMenu(new SimpleMenuProvider((id,inv,ignored)->new HeartDashboardMenu(id,inv,c),Component.literal("Release candidate network test")));}
    }
    private static void fire(HeartBlockEntity c,ServerPlayer player){
        player.connection.teleport(ModuleArena.corePosition(c).x,ModuleArena.corePosition(c).y+2,ModuleArena.corePosition(c).z,0,0);
        var m=c.modules().add("laser");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));m.edit("chargeTime",0,"");m.edit("beamHits",100,"");m.edit("damage",.1,"");m.edit("laserAim",0,"");c.modules().changed();c.tryActivate(player);
    }
    private static boolean firing(HeartBlockEntity c){var list=c.modules().snapshot(c,true).getList("Modules",10);for(var t:list)if(((net.minecraft.nbt.CompoundTag)t).getString("LaserPhase").equals("firing"))return true;return false;}
    private static void finish(net.minecraft.server.MinecraftServer server,boolean pass,String error){
        if(done)return;done=true;
        try{Files.writeString(server.getServerDirectory().resolve("release-network-result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("success",pass,"error",error,"evidence",evidence)));}catch(Exception ex){ex.printStackTrace();}
        System.out.println(pass?"RELEASE_REPLAY_PASS network":"RELEASE_REPLAY_FAIL network "+error);server.halt(false);
    }
}
