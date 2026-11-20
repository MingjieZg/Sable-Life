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

/** Exact-JAR regression for directed Walker death cleanup. */
@GameTestHolder("sablelife_walker_cleanup_tests") @PrefixGameTestTemplate(false)
public final class WalkerDeathCleanupGameTests {
    private static final float HP=1000;
    record Rig(HeartBlockEntity hull,HeartBlockEntity foot,SubLevel hullBody,SubLevel footBody,
                       BlockEntity walker,List<SubLevel> legs,List<BlockPos> legTargets,VirtualModule signal,
                       List<Long> forced,JsonObject report) {}

    static void start(GameTestHelper h,String name,boolean footFirst,int delay,java.util.function.Consumer<Rig> run) throws Exception {
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
            if(name.equals("parent_reward_only")||name.equals("both_rewards")){
                h.assertTrue(hull.setDeathTemplate(author,0,new ItemStack(Items.DIAMOND)),"Main dormant loot setup failed");
                h.assertTrue(foot.setDeathTemplate(author,0,new ItemStack(Items.EMERALD)),"Foot dormant loot setup failed");
            }
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
    static void hit(ServerLevel level,BlockPos pos,float damage){var source=arrow(level,damage);BodyCombat.onProjectileImpact(new ProjectileImpactEvent(source,new BlockHitResult(pos.getCenter(),Direction.UP,pos,false)));}
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
    static void finish(GameTestHelper h,Rig rig){System.out.println("WALKER_DUAL_RESULT "+rig.report);for(long key:rig.forced)h.getLevel().setChunkForced(net.minecraft.world.level.ChunkPos.getX(key),net.minecraft.world.level.ChunkPos.getZ(key),false);h.succeed();}
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
        if(t==300){
            var level=h.getLevel();var container=SubLevelContainer.getContainer(level);
            if(rig.report.get("case").getAsString().equals("kill_foot")){
                h.assertTrue(!rig.hull.isRemoved()&&rig.hull.health().state()==HeartState.ALIVE,"Foot death removed hull");
                h.assertTrue(rig.legs.stream().filter(b->container.getSubLevel(b.getUniqueId())!=null).count()==rig.legs.size()-1,"Foot death removed unrelated leg segments");
            } else {
                h.assertTrue(rig.legs.stream().noneMatch(b->container.getSubLevel(b.getUniqueId())!=null),"Hull death left Walker leg bodies");
                h.assertTrue(!BodyOwnership.get(level).hasOwned(rig.hull.creatureId())&&!BodyOwnership.get(level).hasOwned(rig.foot.creatureId()),"Death retained ownership");
                h.assertTrue(LoadedCoreIndex.find(level,rig.hull.creatureId())==null&&LoadedCoreIndex.find(level,rig.foot.creatureId())==null,"Death retained core index");
                h.assertTrue(rig.foot.modules().damageBoxes.entities().isEmpty(),"Death retained foot receiver entities");
            }
            finish(h,rig);
        }
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
    @GameTest(template="combat",batch="walker_cleanup_foot_then_hull",timeoutTicks=420)
    public static void HullDeathAlsoRemovesDistalSegmentsAfterFootDied(GameTestHelper h)throws Exception{start(h,"foot_then_hull",false,0,rig->{
        hit(h.getLevel(),rig.foot.getBlockPos(),2000);
        h.runAfterDelay(20,()->{h.assertTrue(rig.foot.isRemoved(),"Foot should be gone first");hit(h.getLevel(),rig.hull.getBlockPos(),2000);watch(h,rig);});
    });}
    @GameTest(template="combat",batch="walker_cleanup_release",timeoutTicks=130)
    public static void ReleasingHullKeepsAttachedFootCoreAndLegs(GameTestHelper h)throws Exception{start(h,"release_hull",false,0,rig->{
        h.assertTrue(rig.hull.resetForCreative(TestActors.creative(h.getLevel())),"Release failed");
        h.runAfterDelay(80,()->{h.assertTrue(!rig.foot.isRemoved()&&rig.foot.health().state()==HeartState.ALIVE,"Release deleted foot core");h.assertTrue(rig.legs.stream().noneMatch(SubLevel::isRemoved),"Release deleted legs");observe(h,rig,"after_release");finish(h,rig);});
    });}
    @GameTest(template="combat",batch="walker_cleanup_nbt",timeoutTicks=110)
    public static void PendingAttachmentsSurviveNbtAndOldRecordsStayCompatible(GameTestHelper h)throws Exception{start(h,"saved_pending",false,40,rig->{
        var level=h.getLevel();hit(level,rig.hull.getBlockPos(),2000);var id=rig.hull.creatureId();
        var saved=LifeLifecycle.get(level.getServer()).save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
        var row=saved.getList("Deaths",10).stream().map(net.minecraft.nbt.CompoundTag.class::cast).filter(t->t.hasUUID("Id")&&t.getUUID("Id").equals(id)).findFirst().orElseThrow();
        h.assertTrue(row.getList("WalkerAttachments",10).size()==rig.legs.size(),"Death did not persist all leg IDs");
        var restored=LifeLifecycle.load(saved,level.registryAccess()).save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
        h.assertTrue(saved.equals(restored),"New saved job changed during read/write");
        var old=saved.copy();old.getList("Deaths",10).forEach(t->((net.minecraft.nbt.CompoundTag)t).remove("WalkerAttachments"));
        var legacy=LifeLifecycle.load(old,level.registryAccess());h.assertTrue(legacy.isDying(id),"Old death record was lost");
        h.assertTrue(legacy.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()).equals(old),"Legacy missing field did not retain old meaning");
        h.assertTrue(rig.hull.resetForCreative(TestActors.creative(level)),"Pending death cancel failed");
        h.runAfterDelay(70,()->{h.assertTrue(rig.legs.stream().noneMatch(SubLevel::isRemoved)&&!rig.foot.isRemoved(),"Cancelled death still removed legs");observe(h,rig,"after_cancel");finish(h,rig);});
    });}

    @GameTest(template="combat",batch="walker_cleanup_foreign",timeoutTicks=180)
    public static void ReclaimedFootDuringDelayIsNotDeletedByOldOwner(GameTestHelper h)throws Exception{start(h,"reclaimed_foot",false,40,rig->{
        var level=h.getLevel();hit(level,rig.hull.getBlockPos(),2000);var old=rig.foot.creatureId();
        h.assertTrue(rig.foot.resetForCreative(TestActors.creative(level)),"Could not release old foot identity");rig.foot.tryActivate(TestActors.creative(level));
        h.assertTrue(!old.equals(rig.foot.creatureId()),"Foot identity did not change");
        h.runAfterDelay(110,()->{h.assertTrue(rig.hull.isRemoved(),"Hull was not removed");h.assertTrue(!rig.foot.isRemoved()&&rig.foot.health().state()==HeartState.ALIVE,"Old death removed a newly reclaimed creature");h.assertTrue(rig.legs.stream().filter(b->!b.isRemoved()).count()==1,"Unrelated original legs remained");observe(h,rig,"after_reclaim");finish(h,rig);});
    });}

    @GameTest(template="combat",batch="walker_cleanup_natural",timeoutTicks=380)
    public static void EncounterRemovalAlsoRemovesWalkerAttachments(GameTestHelper h)throws Exception{start(h,"natural_hull",false,0,rig->{
        h.assertTrue(rig.hull.expireWildEncounter(),"Encounter removal failed");watch(h,rig);
    });}

    private static void rewardCase(GameTestHelper h,String name,boolean defeatFoot)throws Exception{start(h,name,false,0,rig->{
        var level=h.getLevel();var player=TestActors.creative(level);
        h.assertTrue(!rig.hull.deathTemplates().getFirst().isEmpty(),"Main loot fixture missing");
        h.assertTrue(!rig.foot.deathTemplates().getFirst().isEmpty(),"Foot loot fixture missing");
        var point=world(level,rig.hull.getBlockPos());var area=new AABB(point,point).inflate(60);
        var before=new HashSet<UUID>();for(var e:level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area))before.add(e.getUUID());
        if(defeatFoot)hit(level,rig.foot.getBlockPos(),2000);hit(level,rig.hull.getBlockPos(),2000);
        h.runAfterDelay(90,()->{
            var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().filter(e->!before.contains(e.getUUID())).toList();
            int main=drops.stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum();
            int foot=drops.stream().filter(e->e.getItem().is(Items.EMERALD)).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(main==1&&foot==(defeatFoot?1:0),"Unexpected main/foot rewards "+main+"/"+foot);
            rig.report.addProperty("mainDrops",main);rig.report.addProperty("footDrops",foot);observe(h,rig,"after_rewards");finish(h,rig);
        });
    });}
    @GameTest(template="combat",batch="walker_cleanup_reward_parent",timeoutTicks=150)
    public static void ParentCleanupDoesNotAwardAnUndefeatedFootKill(GameTestHelper h)throws Exception{rewardCase(h,"parent_reward_only",false);}
    @GameTest(template="combat",batch="walker_cleanup_reward_both",timeoutTicks=150)
    public static void IndependentlyDefeatedFootStillSettlesExactlyOnce(GameTestHelper h)throws Exception{rewardCase(h,"both_rewards",true);}

    @GameTest(template="combat",batch="walker_cleanup_receivers_tickets",timeoutTicks=170)
    public static void MainDeathRetiresFootReceiversAndBothLoadingTickets(GameTestHelper h)throws Exception{start(h,"receivers_and_tickets",false,0,rig->{
        var level=h.getLevel();var author=TestActors.creative(level);
        h.assertTrue(rig.hull.editCore(author,"forceChunkLoading",1,"")&&rig.foot.editCore(author,"forceChunkLoading",1,""),"Could not enable both loading groups");
        var module=rig.foot.modules().add("entity_hitbox");module.anchors(List.of(ModuleAnchor.create(rig.foot,rig.foot.getBlockPos(),Direction.UP)));rig.foot.modules().changed();rig.foot.modules().damageBoxes.tick(rig.foot);
        var receivers=List.copyOf(rig.foot.modules().damageBoxes.entities());h.assertTrue(!receivers.isEmpty(),"No real foot receiver spawned");
        CoreChunkLoading.observe(rig.hull);CoreChunkLoading.observe(rig.foot);CoreChunkLoading.refresh(level.getServer());
        h.assertTrue(!CoreChunkLoading.chunks(level.getServer(),level,rig.hull.creatureId()).isEmpty()&&!CoreChunkLoading.chunks(level.getServer(),level,rig.foot.creatureId()).isEmpty(),"Loading ticket fixture missing");
        hit(level,rig.hull.getBlockPos(),2000);
        h.runAfterDelay(100,()->{CoreChunkLoading.refresh(level.getServer());
            h.assertTrue(receivers.stream().allMatch(EntityHitbox::isRemoved),"Parent death left foot proxies");
            h.assertTrue(CoreChunkLoading.chunks(level.getServer(),level,rig.hull.creatureId()).isEmpty()&&CoreChunkLoading.chunks(level.getServer(),level,rig.foot.creatureId()).isEmpty(),"Parent death retained world tickets");
            h.assertTrue(SubLevelContainer.getContainer(level).collectForceLoadTickets().values().stream().flatMap(Set::stream).noneMatch(t->t.type().equals(CoreChunkLoading.BODY_TICKET)&&(t.key().equals(rig.hull.creatureId())||t.key().equals(rig.foot.creatureId()))),"Parent death retained body tickets");
            observe(h,rig,"after_receivers_and_tickets");finish(h,rig);
        });
    });}

}
