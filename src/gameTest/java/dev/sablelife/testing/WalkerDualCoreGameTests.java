package dev.sablelife.testing;

import com.google.gson.JsonObject;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.SableLife;
import dev.sablelife.api.BodyCombatApi;
import dev.sablelife.combat.*;
import dev.sablelife.compat.letitwalk.WalkerLegDamage;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.ownership.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Observation tests against an unchanged release JAR, not a proposed multi-core implementation. */
@GameTestHolder("sablelife_walker_dual_tests") @PrefixGameTestTemplate(false)
public final class WalkerDualCoreGameTests {
    private static final float HP=1000;
    private record Rig(HeartBlockEntity hull,HeartBlockEntity foot,SubLevel hullBody,SubLevel footBody,
                       BlockEntity walker,List<SubLevel> legs,List<BlockPos> legTargets,VirtualModule signal,
                       List<Long> forced,JsonObject report) {}

    private static void start(GameTestHelper h,String name,boolean footFirst,int delay,java.util.function.Consumer<Rig> run) throws Exception {
        h.assertTrue(ModList.get().getModContainerById("let_it_walk").map(c->c.getModInfo().getVersion().toString().equals("0.1.5")).orElse(false),"Exact Let it Walk 0.1.5 is required for this test");
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(8,124,8));var prior=new HashSet<UUID>();
        for(var b:SubLevelContainer.getContainer(level).getAllSubLevels())prior.add(b.getUniqueId());
        var forced=new ArrayList<Long>();
        for(int x=(origin.getX()>>4)-3;x<=(origin.getX()>>4)+3;x++)for(int z=(origin.getZ()>>4)-3;z<=(origin.getZ()>>4)+3;z++){
            long key=net.minecraft.world.level.ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(key)){level.setChunkForced(x,z,true);forced.add(key);}level.getChunk(x,z);
        }
        WalkerLegDamageGameTests.buildWalker(level,origin);
        h.runAfterDelay(12,()->{
            var container=SubLevelContainer.getContainer(level);
            var walker=container.getAllSubLevels().stream().filter(b->!prior.contains(b.getUniqueId()))
                    .flatMap(b->WalkerLegDamageGameTests.blockEntities(b).stream()).filter(be->be.getClass().getName().equals("dev.letitwalk.content.WalkerCoreBlockEntity")).findFirst().orElseThrow();
            var body=Sable.HELPER.getContaining(level,walker.getBlockPos());
            var legs=new ArrayList<SubLevel>();for(var leg:((BlockEntitySubLevelActor)walker).sable$getLoadingDependencies())legs.add(leg);
            h.assertTrue(legs.size()>=2,"Walker did not build multiple leg segments");
            var targets=legs.stream().map(leg->WalkerLegDamageGameTests.solidBlock(level,leg)).distinct().toList();
            var footBody=legs.stream().filter(leg->WalkerLegDamageGameTests.blockEntities(body).stream().anyMatch(be->leg.getUniqueId().equals(WalkerLegDamageGameTests.linkedChild(be)))).findFirst().orElse(legs.getFirst());
            var solid=WalkerLegDamageGameTests.solidBlock(level,footBody);BlockPos footPos=null;
            for(var d:Direction.values()){var p=solid.relative(d);if(level.hasChunkAt(p)&&level.getBlockState(p).isAir()&&Sable.HELPER.getContaining(level,p)==footBody){footPos=p;break;}}
            h.assertTrue(footPos!=null,"No safe location for a foot core without replacing a joint");
            var hullPos=walker.getBlockPos().above();level.setBlockAndUpdate(hullPos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(footPos,SableLife.HEART.get().defaultBlockState());
            var hull=(HeartBlockEntity)level.getBlockEntity(hullPos);var foot=(HeartBlockEntity)level.getBlockEntity(footPos);h.assertTrue(hull.configure(HP,delay)&&foot.configure(HP,0),"Could not configure dormant cores");
            LoadedCoreIndex.observe(hull);LoadedCoreIndex.observe(foot);
            var module=foot.modules().add("contact_sensor");var mark=ModuleAnchor.create(foot,footPos,Direction.UP);h.assertTrue(mark!=null,"Foot core cannot mark its own segment");module.anchors(List.of(mark));module.edit("contactDetectTerrain",0,"");module.edit("contactDetectMobs",1,"");module.edit("sensorChannel",1,"");
            int channel=foot.wireless().add();foot.wireless().channel(channel).set(6,3);foot.wireless().channel(channel).set(0,1);foot.modules().changed();
            var author=TestActors.creative(level);
            if(footFirst){foot.tryActivate(author);hull.tryActivate(author);}else{hull.tryActivate(author);foot.tryActivate(author);}
            h.assertTrue(hull.health().state()==HeartState.ALIVE&&foot.health().state()==HeartState.ALIVE,"Both cores did not activate");
            h.assertTrue(!hull.creatureId().equals(foot.creatureId()),"Two cores share a creature identity");
            double floor=Double.POSITIVE_INFINITY,minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
            for(var pos:targets){var point=world(level,pos);floor=Math.min(floor,point.y-.5);minX=Math.min(minX,point.x);maxX=Math.max(maxX,point.x);minZ=Math.min(minZ,point.z);maxZ=Math.max(maxZ,point.z);}
            int y=(int)Math.floor(floor)-1;for(var p:BlockPos.betweenClosed((int)Math.floor(minX)-6,y,(int)Math.floor(minZ)-6,(int)Math.ceil(maxX)+6,y,(int)Math.ceil(maxZ)+6))level.setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());
            var report=new JsonObject();report.addProperty("case",name);report.addProperty("footFirst",footFirst);report.addProperty("placement","core added to assembled proximal leg segment");report.addProperty("hullScope",SableBodyProbe.inspect(level,hullPos).connectedBodies().size());report.addProperty("footScope",SableBodyProbe.inspect(level,footPos).connectedBodies().size());report.addProperty("totalLegSegments",legs.size());report.addProperty("footMarkableSegments",targets.stream().filter(p->ModuleAnchor.create(foot,p,Direction.UP)!=null).count());report.addProperty("mainCanMarkFoot",ModuleAnchor.create(hull,footPos,Direction.UP)!=null);report.addProperty("footCoreId",foot.creatureId().toString());report.addProperty("hullCoreId",hull.creatureId().toString());
            var rig=new Rig(hull,foot,body,footBody,walker,List.copyOf(legs),targets,module,forced,report);observe(h,rig,"before");
            run.accept(rig);
        });
    }
    private static Vec3 world(ServerLevel level,BlockPos pos){return SableBodyProbe.toWorld(level,pos.getCenter());}
    private static Arrow arrow(ServerLevel level,float damage){var arrow=new Arrow(level,0,0,0,new ItemStack(Items.ARROW),null);arrow.setDeltaMovement(2,0,0);arrow.setBaseDamage(damage/2);return arrow;}
    private static void hit(ServerLevel level,BlockPos pos,float damage){var source=arrow(level,damage);BodyCombat.onProjectileImpact(new ProjectileImpactEvent(source,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false)));}
    private static void hp(GameTestHelper h,HeartBlockEntity core,float expected){h.assertTrue(Math.abs(core.health().current()-expected)<.001,"Unexpected HP: "+core.health().current()+", expected "+expected);}
    private static JsonObject coreState(ServerLevel level,HeartBlockEntity original,SubLevel originalBody){
        var out=new JsonObject();var current=LoadedCoreIndex.find(level,original.creatureId());var body=SubLevelContainer.getContainer(level).getSubLevel(originalBody.getUniqueId());
        out.addProperty("originalBodyPresent",body!=null&&!body.isRemoved());out.addProperty("indexed",current!=null);out.addProperty("originalObjectRemoved",original.isRemoved());out.addProperty("state",(current==null?original:current).health().state().name());out.addProperty("hp",(current==null?original:current).health().current());
        out.addProperty("hasOwnership",BodyOwnership.get(level).hasOwned(original.creatureId()));out.addProperty("coreBlockPresent",level.hasChunkAt(original.getBlockPos())&&level.getBlockEntity(original.getBlockPos()) instanceof HeartBlockEntity found&&found.creatureId().equals(original.creatureId()));out.addProperty("cleanupFinished",LifeLifecycle.get(level.getServer()).finished(original.creatureId()));
        if(current!=null){out.addProperty("operational",current.operational());out.addProperty("modules",current.modules().all().size());out.addProperty("receivers",current.modules().damageBoxes.entities().size());}
        return out;
    }
    private static void observe(GameTestHelper h,Rig rig,String point){
        var state=new JsonObject();var level=h.getLevel();state.add("hull",coreState(level,rig.hull,rig.hullBody));state.add("foot",coreState(level,rig.foot,rig.footBody));state.addProperty("originalLegBodiesRemaining",rig.legs.stream().filter(b->{var found=SubLevelContainer.getContainer(level).getSubLevel(b.getUniqueId());return found!=null&&!found.isRemoved();}).count());state.addProperty("walkerBlockPresent",level.hasChunkAt(rig.walker.getBlockPos())&&level.getBlockEntity(rig.walker.getBlockPos())==rig.walker);
        state.addProperty("footMarkStatus",HeartModules.status(rig.signal,rig.foot));var runtime=rig.foot.modules().contactSensor.snapshot(rig.signal.id());var signal=new JsonObject();signal.addProperty("active",runtime.getBoolean("Active"));signal.addProperty("contact",runtime.getBoolean("Contact"));signal.addProperty("channel",runtime.getString("ChannelStatus"));signal.addProperty("limited",runtime.getBoolean("Limited"));state.add("footSignal",signal);rig.report.add(point,state);
    }
    private static void finish(GameTestHelper h,Rig rig){System.out.println("WALKER_DUAL_RESULT "+rig.report);for(long key:rig.forced)h.getLevel().setChunkForced(net.minecraft.world.level.ChunkPos.getX(key),net.minecraft.world.level.ChunkPos.getZ(key),false);h.succeed();}
    private static void watch(GameTestHelper h,Rig rig){for(int t:new int[]{1,20,80,160,300})h.runAfterDelay(t,()->{
        observe(h,rig,"after_"+t);
        if(t==80&&rig.hull.health().current()==0&&rig.foot.health().state()==HeartState.ALIVE&&!rig.foot.isRemoved()){
            var l=h.getLevel();var before=rig.foot.health().current();hit(l,rig.foot.getBlockPos(),7);rig.report.addProperty("footStillDamageableAfterHullDeath",rig.foot.health().current()==before-7);
            var cow=EntityType.COW.create(l);cow.setNoAi(true);cow.setNoGravity(true);cow.noPhysics=true;var center=ContactSensorGeometry.frame(rig.foot,rig.signal).center();cow.setPos(center.add(0,-cow.getBbHeight()/2,0));l.addFreshEntity(cow);
            h.runAfterDelay(3,()->{rig.report.addProperty("footModuleStillRunsAfterHullDeath",rig.foot.wireless().output(0)==15);cow.discard();});
        }
        if(t==80&&rig.foot.health().current()==0&&rig.hull.health().state()==HeartState.ALIVE&&!rig.hull.isRemoved()){
            var l=h.getLevel();var before=rig.hull.health().current();var remaining=rig.legTargets.stream().filter(p->BodyCombat.findDamageHeart(l,p)==rig.hull).findFirst();rig.report.addProperty("remainingLegStillRoutesToHull",remaining.isPresent());
            if(remaining.isPresent()){hit(l,remaining.get(),7);rig.report.addProperty("hullStillDamageableThroughOtherLeg",rig.hull.health().current()==before-7);}
        }
        if(t==300)finish(h,rig);
    });}

    @GameTest(template="combat",batch="walker_dual_routing",timeoutTicks=110)
    public static void MainFirstActivationDirectAreaAndFootModule(GameTestHelper h)throws Exception{
        start(h,"routing_main_first",false,0,rig->{var l=h.getLevel();h.assertTrue(BodyCombat.findDamageHeart(l,rig.hull.getBlockPos())==rig.hull,"Hull hit routed elsewhere");h.assertTrue(BodyCombat.findDamageHeart(l,rig.foot.getBlockPos())==rig.foot,"Foot did not use own core");
            hit(l,rig.hull.getBlockPos(),7);hp(h,rig.hull,993);hp(h,rig.foot,1000);hit(l,rig.foot.getBlockPos(),11);hp(h,rig.hull,993);hp(h,rig.foot,989);
            var other=rig.legTargets.stream().filter(p->Sable.HELPER.getContaining(l,p)!=rig.footBody).findFirst().orElseThrow();h.assertTrue(BodyCombat.findDamageHeart(l,other)==rig.hull,"Unowned leg no longer routes to hull");hit(l,other,13);hp(h,rig.hull,980);hp(h,rig.foot,989);
            var point=world(l,rig.foot.getBlockPos());int hits=BodyCombatApi.sphere(arrow(l,5),point,point,.2,d->5);h.assertTrue(hits==1,"Foot-only area counted multiple creatures");hp(h,rig.hull,980);hp(h,rig.foot,984);
            var cow=EntityType.COW.create(l);cow.setNoAi(true);cow.setNoGravity(true);var center=ContactSensorGeometry.frame(rig.foot,rig.signal).center();cow.setPos(center.add(0,-cow.getBbHeight()/2,0));l.addFreshEntity(cow);rig.foot.modules().contactSensor.tick(rig.foot);
            h.runAfterDelay(2,()->{rig.report.addProperty("footModuleProducedSignal",rig.foot.wireless().output(0)==15);h.assertTrue(rig.foot.wireless().output(0)==15,"Marked foot module did not run");cow.discard();observe(h,rig,"after_hits");finish(h,rig);});
        });
    }
    @GameTest(template="combat",batch="walker_dual_reverse",timeoutTicks=80)
    public static void FootFirstActivationKeepsSeparateOwnership(GameTestHelper h)throws Exception{start(h,"routing_foot_first",true,0,rig->{var l=h.getLevel();hit(l,rig.foot.getBlockPos(),17);hp(h,rig.hull,1000);hp(h,rig.foot,983);hit(l,rig.hull.getBlockPos(),19);hp(h,rig.hull,981);hp(h,rig.foot,983);observe(h,rig,"after_hits");finish(h,rig);});}
    @GameTest(template="combat",batch="walker_dual_kill_hull",timeoutTicks=400)
    public static void DefeatHullObserveFootAndLegCleanup(GameTestHelper h)throws Exception{start(h,"kill_hull",false,0,rig->{hit(h.getLevel(),rig.hull.getBlockPos(),2000);hp(h,rig.hull,0);hp(h,rig.foot,1000);observe(h,rig,"immediate");watch(h,rig);});}
    @GameTest(template="combat",batch="walker_dual_kill_foot",timeoutTicks=400)
    public static void DefeatFootObserveHullAndLegCleanup(GameTestHelper h)throws Exception{start(h,"kill_foot",false,0,rig->{hit(h.getLevel(),rig.foot.getBlockPos(),2000);hp(h,rig.foot,0);hp(h,rig.hull,1000);observe(h,rig,"immediate");watch(h,rig);});}
    @GameTest(template="combat",batch="walker_dual_both_hull_first",timeoutTicks=400)
    public static void DefeatBothHullFirstSameTick(GameTestHelper h)throws Exception{start(h,"kill_both_hull_first",false,0,rig->{hit(h.getLevel(),rig.hull.getBlockPos(),2000);hit(h.getLevel(),rig.foot.getBlockPos(),2000);hp(h,rig.hull,0);hp(h,rig.foot,0);observe(h,rig,"immediate");watch(h,rig);});}
    @GameTest(template="combat",batch="walker_dual_both_foot_first",timeoutTicks=400)
    public static void DefeatBothFootFirstSameTick(GameTestHelper h)throws Exception{start(h,"kill_both_foot_first",false,0,rig->{hit(h.getLevel(),rig.foot.getBlockPos(),2000);hit(h.getLevel(),rig.hull.getBlockPos(),2000);hp(h,rig.hull,0);hp(h,rig.foot,0);observe(h,rig,"immediate");watch(h,rig);});}
    @GameTest(template="combat",batch="walker_dual_delayed",timeoutTicks=400)
    public static void DyingHullDoesNotImmediatelyDisableFoot(GameTestHelper h)throws Exception{start(h,"kill_hull_delayed",false,40,rig->{hit(h.getLevel(),rig.hull.getBlockPos(),2000);h.assertTrue(rig.hull.health().state()==HeartState.DYING,"No delayed death window");hit(h.getLevel(),rig.foot.getBlockPos(),23);hp(h,rig.foot,977);observe(h,rig,"immediate");watch(h,rig);});}
    @GameTest(template="combat",batch="walker_dual_control",timeoutTicks=400)
    public static void DefeatHullAfterRemovingFootCoreControl(GameTestHelper h)throws Exception{start(h,"kill_hull_no_foot_core_control",false,0,rig->{
        var l=h.getLevel();h.assertTrue(rig.foot.resetForCreative(TestActors.creative(l)),"Could not release foot for control scene");l.setBlockAndUpdate(rig.foot.getBlockPos(),Blocks.AIR.defaultBlockState());h.assertTrue(!BodyOwnership.get(l).hasOwned(rig.foot.creatureId()),"Control retained foot ownership");observe(h,rig,"control_before_death");
        hit(l,rig.hull.getBlockPos(),2000);hp(h,rig.hull,0);observe(h,rig,"immediate");watch(h,rig);
    });}
}
