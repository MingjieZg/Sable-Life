package dev.sablelife.testing;

import com.google.gson.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.nio.file.*;
import java.util.*;

/** Actual clean server restarts, opt-in and restricted to an isolated test directory. */
@EventBusSubscriber(modid="sablelife_tests")
public final class ReleaseRestartReplay {
    private static final String STAGE=System.getProperty("sablelife.releaseRestartStage","");
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final List<Map<String,Object>> evidence=new ArrayList<>();
    private static ServerLevel level; private static FakePlayer player;
    private static final List<HeartBlockEntity> cores=new ArrayList<>();
    private static boolean done; private static int ticks,cycles; private static Cow target;
    private static Path dir;
    private static void check(boolean okay,String message){if(!okay)throw new IllegalStateException(message);}
    private static void record(String name,Object data){evidence.add(Map.of("event",name,"tick",ticks,"data",data));System.out.println("RELEASE_REPLAY: "+name+" "+data);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(STAGE.isEmpty()||done)return;
        try{
            level=e.getServer().overworld();dir=e.getServer().getServerDirectory().resolve("release-restart-results");Files.createDirectories(dir);
            if(++ticks==1){
                check(e.getServer().getServerDirectory().toAbsolutePath().toString().contains("release-freeze"),"Refusing non-test world");
                var origin=SableLife.class.getProtectionDomain().getCodeSource().getLocation().toString();
                check(origin.contains(System.getProperty("sablelife.expectedReleaseJar","MISSING")),"Wrong packaged origin "+origin);record("artifact",origin);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,e.getServer());
                level.getGameRules().getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,e.getServer());
                for(int x=254;x<=260;x++)for(int z=254;z<=258;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
                player=TestActors.creative(level);
            }
            if(ticks==40){if(STAGE.equals("seed"))seed();else restore();}
            if(STAGE.equals("verify")&&ticks==80){
                check(cores.get(0).health().state()==HeartState.DORMANT&&cores.get(1).health().state()==HeartState.DORMANT,"Release state lost at second restart");
                var expected=JsonParser.parseString(Files.readString(dir.resolve("post-exercise.json"))).getAsJsonObject();
                check(cores.get(1).health().current()==100&&cores.get(1).survivalTokenBalance()==expected.get("energy").getAsLong(),"Paid health/energy changed after second restart");
                finish(true,"");
            }
            if(STAGE.equals("exercise")&&ticks>=60){
                int offset=(ticks-60)%12;
                if(offset==0)beginAttack();
                if(offset==4)releaseAttack();
                if(cycles==12){Files.writeString(dir.resolve("post-exercise.json"),JSON.toJson(Map.of("energy",cores.get(1).survivalTokenBalance())));finish(true,"");}
            }
            if(ticks>500)throw new IllegalStateException("Replay timeout");
        }catch(Throwable failure){failure.printStackTrace();try{finish(false,failure.toString());}catch(Exception ignored){e.getServer().halt(false);}}
    }
    private static void seed()throws Exception{
        var faction=FactionRegistry.get(level.getServer()).create(player,"FreezeFixture");check(faction!=null,"Cannot create fixture faction");
        var manifest=new JsonArray();
        for(int i=0;i<3;i++){
            var p=new BlockPos(4096+i*24,90,4096);
            var block=i==0?SableLife.HEART.get():i==1?SableLife.SURVIVAL_LIFE_CORE.get():SableLife.REDSTONE_CORE.get();
            level.setBlockAndUpdate(p,block.defaultBlockState());level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
            var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
            var c=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());cores.add(c);
            c.relations().ownerName("ReleaseAuthor");c.relations().exclude("ReleaseFriend");c.relations().faction(faction);
            if(i<2){
                c.configure(100,20);
                var laser=c.modules().add("laser");laser.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));
                laser.edit("enabled",0,"");laser.edit("chargeTime",0,"");laser.edit("beamHits",100,"");laser.edit("laserAim",1,"");laser.edit("target",1,"");laser.edit("damage",0,"");
                var box=c.modules().add("entity_hitbox");box.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));box.edit("hitboxWidth",8,"");box.edit("hitboxHeight",8,"");box.edit("hitboxDepth",8,"");
                if(i==1){c.depositSurvivalTokens(player,new ItemStack(Items.COPPER_INGOT,25));c.tryActivate(player);c.damage(30);check(c.resetForCreative(player),"Initial survival release failed");}
                else for(int n=0;n<6;n++){c.tryActivate(player);check(c.resetForCreative(player),"Seed repeated release failed");}
            }else c.modules().add("wireless_redstone");
            c.setChanged();var row=new JsonObject();row.addProperty("x",c.getBlockPos().getX());row.addProperty("y",c.getBlockPos().getY());row.addProperty("z",c.getBlockPos().getZ());row.addProperty("faction",faction.toString());manifest.add(row);
        }
        check(cores.get(1).survivalTokenBalance()==150&&cores.get(1).health().current()==70,"Unexpected starting survival state");
        Files.writeString(dir.resolve("manifest.json"),JSON.toJson(manifest));record("seed_saved",Map.of("cores",3,"energy",150,"life",70));finish(true,"");
    }
    private static void restore()throws Exception{
        var rows=JsonParser.parseString(Files.readString(dir.resolve("manifest.json"))).getAsJsonArray();
        for(int i=0;i<rows.size();i++){
            var row=rows.get(i).getAsJsonObject();var pos=new BlockPos(row.get("x").getAsInt(),row.get("y").getAsInt(),row.get("z").getAsInt());
            check(level.getBlockEntity(pos) instanceof HeartBlockEntity,"Core missing after real process restart: "+pos);
            var c=(HeartBlockEntity)level.getBlockEntity(pos);cores.add(c);
            check(c.relations().ownerName().equals("ReleaseAuthor")&&c.relations().excludes("releasefriend"),"Owner/whitelist lost");
            check(c.relations().faction().toString().equals(row.get("faction").getAsString())&&FactionRegistry.get(level.getServer()).contains(c.relations().faction()),"Faction lost");
            check(c.modules().all().size()==(i<2?2:1),"Modules lost at restart");
        }
        if(STAGE.equals("exercise")){
            check(cores.get(1).health().current()==70&&cores.get(1).survivalTokenBalance()==150,"Upgrade altered paid health or energy");
            check(cores.get(1).depositSurvivalTokens(player,new ItemStack(Items.NETHERITE_INGOT,3)),"Cannot fund repeated long-duration laser tests");
        }
        record("restored",Map.of("cores",cores.size(),"energy",cores.get(1).survivalTokenBalance(),"life",cores.get(1).health().current()));
    }
    private static void beginAttack(){
        var c=cores.get(cycles%2);player.setGameMode(GameType.CREATIVE);player.setPos(ModuleArena.corePosition(c));
        var laser=c.modules().all().stream().filter(m->m.type().equals("laser")).findFirst().orElseThrow();laser.edit("enabled",1,"");laser.edit("laserAim",0,"");
        var muzzle=ModuleCombat.nozzle(c,laser);target=net.minecraft.world.entity.EntityType.COW.create(level);target.setNoAi(true);target.setNoGravity(true);target.setPos(muzzle.add(0,.5,-3));level.addFreshEntity(target);
        c.tryActivate(player);check(c.health().state()==HeartState.ALIVE,"Could not activate after restart");
    }
    private static void releaseAttack(){
        var c=cores.get(cycles%2);var laser=c.modules().all().stream().filter(m->m.type().equals("laser")).findFirst().orElseThrow();
        check(c.modules().snapshot(c,true).getList("Modules",10).getCompound(0).getString("LaserPhase").equals("firing"),"Laser was not firing during release check: status="+HeartModules.status(laser,c)+", phase="+c.modules().snapshot(c,true).getList("Modules",10).getCompound(0)+", candidates="+ModuleLaser.acquireTargets(level,c,laser,ModuleCombat.nozzle(c,laser)).size()+", target="+target.position());
        var menu=new HeartDashboardMenu(1,player.getInventory(),c);
        check(menu.action(player,c.modules().revision(),laser.id(),"release","",0,""),"Dashboard release failed during firing after restart");
        check(c.health().state()==HeartState.DORMANT&&c.modules().snapshot(c,true).getList("Modules",10).getCompound(0).getString("LaserPhase").equals("waiting"),"Release did not stop active laser");
        laser.edit("enabled",0,"");c.setChanged();target.discard();record("firing_release",Map.of("cycle",cycles,"survival",c.isSurvivalCore(),"energy",c.survivalTokenBalance()));cycles++;
    }
    private static void finish(boolean okay,String failure)throws Exception{
        if(done)return;done=true;Files.writeString(dir.resolve(STAGE+".json"),JSON.toJson(Map.of("success",okay,"failure",failure,"evidence",evidence)));
        System.out.println((okay?"RELEASE_REPLAY_PASS ":"RELEASE_REPLAY_FAIL ")+STAGE+" "+failure);level.getServer().halt(false);
    }
}
