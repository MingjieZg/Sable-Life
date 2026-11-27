package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.ownership.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_chunk_tests")
@PrefixGameTestTemplate(false)
public final class CoreChunkLoadingGameTests {
    @GameTest(template="combat",batch="void_height_automatic",timeoutTicks=40)
    public static void LiveHeightChangeTriggersAutomaticDeathOnServerTick(GameTestHelper h){
        var core=fixture(h);var author=TestActors.creative(h.getLevel());core.editCore(author,"delay",1,"");core.tryActivate(author);
        h.assertTrue(core.health().state()==HeartState.ALIVE,"Fixture not alive above default height");
        int threshold=(int)Math.ceil(SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter()).y)+1;
        h.assertTrue(core.editCore(author,"voidDeathHeight",threshold,""),"Live height edit rejected");
        h.runAfterDelay(3,()->{h.assertTrue(core.health().state()==HeartState.DYING&&core.health().current()==0,"Automatic tick did not force death");h.succeed();});
    }
    @GameTest(template="combat",batch="void_height_default_save")
    public static void VoidHeightDefaultsToDimensionFloorAndCustomHeightPersists(GameTestHelper h){
        var core=fixture(h);var level=h.getLevel();var author=TestActors.creative(level);h.assertTrue(core.defaultVoidDeathHeight()&&core.voidDeathHeight()==level.getMinBuildHeight(),"Default was not dimension floor");
        h.assertTrue(core.editCore(author,"voidDeathHeight",-1000,"")&&!core.editCore(author,"voidDeathHeight",-63.5,"")&&!core.editCore(author,"voidDeathHeight",Double.NaN,"")&&!core.editCore(author,"voidDeathHeight",2049,""),"Height validation failed");
        var saved=core.saveWithoutMetadata(level.registryAccess());var restored=new HeartBlockEntity(core.getBlockPos(),core.getBlockState());restored.loadWithComponents(saved,level.registryAccess());h.assertTrue(restored.voidDeathHeight()==-1000&&!restored.defaultVoidDeathHeight()&&core.copyConfiguration().getInt("VoidDeathHeight")==-1000,"Custom height save/copy lost");
        h.assertTrue(core.editCore(author,"voidDeathDefault",0,"")&&core.defaultVoidDeathHeight()&&core.voidDeathHeight()==level.getMinBuildHeight(),"Dimension-default reset failed");
        saved.remove("VoidDeathHeight");restored.loadWithComponents(saved,level.registryAccess());restored.setLevel(level.getServer().getLevel(net.minecraft.world.level.Level.NETHER));h.assertTrue(restored.defaultVoidDeathHeight()&&restored.voidDeathHeight()==restored.getLevel().getMinBuildHeight(),"Old/default save did not adapt to dimension");h.succeed();
    }
    @GameTest(template="combat",batch="void_height_death",timeoutTicks=45)
    public static void WorldHeightForcesDeathBypassesDefenseAndKeepsNormalDelay(GameTestHelper h){
        var core=fixture(h);var level=h.getLevel();var author=TestActors.creative(level);int threshold=(int)Math.ceil(SableBodyProbe.toWorld(level,core.getBlockPos().getCenter()).y)+1;
        core.editCore(author,"voidDeathHeight",threshold,"");core.editCore(author,"delay",1,"");core.editCore(author,"defenseThreshold",1,"");core.editCore(author,"defenseMaximum",95,"");core.editCore(author,"defenseEnabled",1,"");
        h.assertTrue(!core.checkVoidDeath()&&core.health().state()==HeartState.DORMANT,"Dormant construction was killed");core.tryActivate(author);core.damage(10);
        h.assertTrue(core.checkVoidDeath()&&core.health().current()==0&&core.health().state()==HeartState.DYING&&core.health().remainingDeathTicks()==20,"World height used storage coordinates, defense blocked death, or delay changed");
        h.assertTrue(!core.checkVoidDeath()&&LifeLifecycle.get(level.getServer()).isDying(core.creatureId()),"Void death repeated or bypassed lifecycle");
        h.runAfterDelay(30,()->{h.assertTrue(level.getBlockEntity(core.getBlockPos())==null,"Normal death cleanup did not finish");h.succeed();});
    }
    private static HeartBlockEntity fixture(GameTestHelper h){var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,6,3));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());}
    private static boolean ticket(HeartBlockEntity core){var level=(net.minecraft.server.level.ServerLevel)core.getLevel();return SubLevelContainer.getContainer(level).collectForceLoadTickets().values().stream().flatMap(Set::stream).anyMatch(t->t.type().equals(CoreChunkLoading.BODY_TICKET)&&core.creatureId().equals(t.key()));}
    @GameTest(template="combat",batch="chunk_loading_lifecycle",timeoutTicks=40)
    public static void LoadingDefaultOffActiveMovesAndReleaseAreOwnerScoped(GameTestHelper h){
        var core=fixture(h);var level=h.getLevel();var author=TestActors.creative(level);h.assertTrue(!core.forceChunkLoading(),"Default not Off");
        h.assertTrue(core.editCore(author,"forceChunkLoading",1,"")&&!core.editCore(author,"forceChunkLoading",2,"")&&!core.editCore(author,"forceChunkLoading",Double.NaN,""),"Toggle validation failed");CoreChunkLoading.refresh(level.getServer());h.assertTrue(!ticket(core)&&CoreChunkLoading.count(core)==0,"Dormant core force-loaded");
        core.tryActivate(author);CoreChunkLoading.observe(core);CoreChunkLoading.refresh(level.getServer());var first=CoreChunkLoading.chunks(level.getServer(),level,core.creatureId());h.assertTrue(ticket(core)&&!first.isEmpty()&&CoreChunkLoading.status(core).equals("active"),"Active core did not request body/world loading");
        var pos=h.absolutePos(new BlockPos(3,10,3));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());var otherBody=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));var other=(HeartBlockEntity)level.getBlockEntity(otherBody.getPlot().getCenterBlock());other.editCore(author,"forceChunkLoading",1,"");other.tryActivate(author);CoreChunkLoading.observe(other);CoreChunkLoading.refresh(level.getServer());h.assertTrue(CoreChunkLoading.chunks(level.getServer(),level,other.creatureId()).equals(first),"Second creature did not share occupied world chunks");
        core.editCore(author,"forceChunkLoading",0,"");h.assertTrue(ticket(other)&&!CoreChunkLoading.chunks(level.getServer(),level,other.creatureId()).isEmpty(),"Off erased another creature's shared loading");core.editCore(author,"forceChunkLoading",1,"");CoreChunkLoading.refresh(level.getServer());
        var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(level,core.getBlockPos());var pipeline=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level).getPipeline();var position=new org.joml.Vector3d(body.logicalPose().position()).add(64,0,0);pipeline.teleport((dev.ryanhcode.sable.sublevel.ServerSubLevel)body,position,new org.joml.Quaterniond(body.logicalPose().orientation()));
        h.runAfterDelay(3,()->{CoreChunkLoading.refresh(level.getServer());var next=CoreChunkLoading.chunks(level.getServer(),level,core.creatureId());h.assertTrue(!next.isEmpty()&&Collections.disjoint(first,next),"World chunk requests did not follow moved body/release old footprint");core.editCore(author,"forceChunkLoading",0,"");h.assertTrue(!ticket(core)&&CoreChunkLoading.chunks(level.getServer(),level,core.creatureId()).isEmpty(),"Off retained own tickets");core.resetForCreative(author);other.resetForCreative(author);h.succeed();});
    }
    @GameTest(template="combat",batch="chunk_loading_death",timeoutTicks=30)
    public static void DeathDelayKeepsLoadingUntilCleanupAndSavesConfiguration(GameTestHelper h){
        var core=fixture(h);var level=h.getLevel();var author=TestActors.creative(level);core.editCore(author,"forceChunkLoading",1,"");core.editCore(author,"delay",.3,"");
        var tag=core.saveWithoutMetadata(level.registryAccess());var restored=new HeartBlockEntity(core.getBlockPos(),core.getBlockState());restored.loadWithComponents(tag,level.registryAccess());h.assertTrue(restored.forceChunkLoading()&&core.copyConfiguration().getBoolean("ForceChunkLoading"),"Toggle save/copy lost");
        core.tryActivate(author);CoreChunkLoading.observe(core);CoreChunkLoading.refresh(level.getServer());core.damage(core.health().maximum());h.assertTrue(core.health().state()==HeartState.DYING&&ticket(core),"Death immediately lost loading");
        h.runAfterDelay(15,()->{CoreChunkLoading.refresh(level.getServer());h.assertTrue(!ticket(core)&&CoreChunkLoading.chunks(level.getServer(),level,core.creatureId()).isEmpty(),"Completed death cleanup leaked loading tickets");h.succeed();});
    }
    @GameTest(template="combat",batch="chunk_loading_persistence")
    public static void SableTicketPersistenceCanRestoreRuntimeAndResetReleasesLoading(GameTestHelper h){
        var core=fixture(h);var level=h.getLevel();var author=TestActors.creative(level);core.editCore(author,"forceChunkLoading",1,"");core.tryActivate(author);CoreChunkLoading.observe(core);CoreChunkLoading.refresh(level.getServer());
        var data=dev.ryanhcode.sable.sublevel.storage.SubLevelTicketsSavedData.getOrLoad(level).save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());h.assertTrue(data.toString().contains("sablelife:life_core"),"Sable loading ticket did not persist");
        CoreChunkLoading.stopped(new net.neoforged.neoforge.event.server.ServerStoppedEvent(level.getServer()));CoreChunkLoading.refresh(level.getServer());h.assertTrue(ticket(core)&&CoreChunkLoading.count(core)>0,"Lost runtime cache did not restore from persisted body tickets");
        core.resetForCreative(author);h.assertTrue(!ticket(core)&&core.forceChunkLoading(),"Release kept tickets or erased author setting");h.succeed();
    }
    @GameTest(template = "combat", batch = "chunk_loading_shared_ticket_release")
    public static void ReleaseRemovesManyPartTicketsAndKeepsOtherOwners(GameTestHelper h) {
        var level = h.getLevel();
        var container = SubLevelContainer.getContainer(level);
        var core = fixture(h);
        var author = TestActors.creative(level);
        var owner = core.creatureId();
        var otherOwner = UUID.randomUUID();
        var root = (ServerSubLevel) dev.ryanhcode.sable.Sable.HELPER.getContaining(level, core.getBlockPos());
        var unrelated = singlePart(h, new BlockPos(7, 9, 7));
        var parts = new ArrayList<ServerSubLevel>();
        parts.add(root);
        try {
            h.assertTrue(core.editCore(author, "forceChunkLoading", 1, ""), "Could not enable chunk loading");
            core.tryActivate(author);
            CoreChunkLoading.observe(core);
            CoreChunkLoading.refresh(level.getServer());
            h.assertTrue(hasBodyTicket(container, root, owner)
                            && !CoreChunkLoading.chunks(level.getServer(), level, owner).isEmpty(),
                    "Active core had no native body or world ticket");

            // Enough native map entries to exercise a backing-table resize as singleton tickets are removed.
            for (int i = 0; i < 37; i++) {
                var part = singlePart(h, new BlockPos(7, 11 + i * 2, 7));
                parts.add(part);
                container.addForceLoadTicket(part, CoreChunkLoading.BODY_TICKET, owner);
            }
            container.addForceLoadTicket(root, CoreChunkLoading.BODY_TICKET, otherOwner);
            container.addForceLoadTicket(unrelated, CoreChunkLoading.BODY_TICKET, otherOwner);
            h.assertTrue(parts.stream().allMatch(part -> hasBodyTicket(container, part, owner))
                            && hasBodyTicket(container, root, otherOwner) && hasBodyTicket(container, unrelated, otherOwner),
                    "Fixture did not install all native tickets");

            // Keep setup, release, and checks in one tick so background refresh cannot remove test tickets.
            h.assertTrue(core.resetForCreative(author) && core.health().state() == HeartState.DORMANT
                            && core.forceChunkLoading() && !core.creatureId().equals(owner),
                    "Normal release did not reset the creature and retain the chunk loading setting");
            assertReleaseTickets(h, container, parts, unrelated, owner, otherOwner);
            h.assertTrue(CoreChunkLoading.chunks(level.getServer(), level, owner).isEmpty(),
                    "Release retained the old creature's world loading");

            CoreChunkLoading.release(level.getServer(), owner);
            assertReleaseTickets(h, container, parts, unrelated, owner, otherOwner);
            h.assertTrue(CoreChunkLoading.chunks(level.getServer(), level, owner).isEmpty(),
                    "Repeated release left world loading");
        } finally {
            for (var part : parts) container.removeForceLoadTicket(part, CoreChunkLoading.BODY_TICKET, owner);
            container.removeForceLoadTicket(root, CoreChunkLoading.BODY_TICKET, otherOwner);
            container.removeForceLoadTicket(unrelated, CoreChunkLoading.BODY_TICKET, otherOwner);
            for (var part : parts) if (part != root) container.removeSubLevel(part, SubLevelRemovalReason.REMOVED);
            container.removeSubLevel(unrelated, SubLevelRemovalReason.REMOVED);
            if (core.creatureId().equals(owner)) core.resetForCreative(author);
        }
        h.succeed();
    }

    private static void assertReleaseTickets(GameTestHelper h, ServerSubLevelContainer container,
            List<ServerSubLevel> parts, ServerSubLevel unrelated, UUID owner, UUID otherOwner) {
        h.assertTrue(parts.stream().noneMatch(part -> hasBodyTicket(container, part, owner)
                        || hasSavedBodyTicket(container, part, owner)),
                "Release retained an active or persisted ticket from the old creature");
        var shared = parts.getFirst();
        h.assertTrue(hasBodyTicket(container, shared, otherOwner) && hasSavedBodyTicket(container, shared, otherOwner)
                        && hasBodyTicket(container, unrelated, otherOwner) && hasSavedBodyTicket(container, unrelated, otherOwner),
                "Release affected a different creature's ticket");
    }

    private static boolean hasBodyTicket(ServerSubLevelContainer container, ServerSubLevel part, UUID owner) {
        return container.collectForceLoadTickets().getOrDefault(part, Set.of()).stream()
                .anyMatch(ticket -> CoreChunkLoading.BODY_TICKET.equals(ticket.type()) && owner.equals(ticket.key()));
    }

    private static boolean hasSavedBodyTicket(ServerSubLevelContainer container, ServerSubLevel part, UUID owner) {
        var saved = container.getAllTickets().get(part.getUniqueId());
        return saved != null && saved.tickets().stream()
                .anyMatch(ticket -> CoreChunkLoading.BODY_TICKET.equals(ticket.type()) && owner.equals(ticket.key()));
    }

    private static ServerSubLevel singlePart(GameTestHelper h, BlockPos relative) {
        var pos = h.absolutePos(relative);
        var level = h.getLevel();
        level.setBlockAndUpdate(pos, Blocks.IRON_BLOCK.defaultBlockState());
        return SubLevelAssemblyHelper.assembleBlocks(
                level, pos, List.of(pos), new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ()));
    }

    @GameTest(template="combat",batch="death_delay_sixty")
    public static void DeathDelayCapsAtSixtyAndLegacyDyingStateDoesNotRevive(GameTestHelper h){
        var core=fixture(h);var author=TestActors.creative(h.getLevel());h.assertTrue(core.editCore(author,"delay",60,"")&&!core.editCore(author,"delay",60.1,""),"60-second validation failed");
        var tag=core.saveWithoutMetadata(h.getLevel().registryAccess());tag.putInt("DeathDelay",12000);tag.putInt("RemainingDeathTicks",6000);tag.putString("LifeState","DYING");tag.putFloat("Health",0);
        var saved=new HeartBlockEntity(core.getBlockPos(),core.getBlockState());saved.loadWithComponents(tag,h.getLevel().registryAccess());h.assertTrue(saved.health().deathDelay()==1200&&saved.health().remainingDeathTicks()==1200&&saved.health().state()==HeartState.DYING&&saved.health().current()==0,"Legacy delay was not clamped safely");
        tag.putInt("RemainingDeathTicks",200);saved.loadWithComponents(tag,h.getLevel().registryAccess());h.assertTrue(saved.health().remainingDeathTicks()==200,"Short remaining delay restarted");h.succeed();
    }
    @GameTest(template="combat",batch="chunk_footprint")
    public static void FootprintHandlesBordersNegativeCoordinatesAndLimits(GameTestHelper h){
        var one=CoreChunkLoading.footprint(List.of(new AABB(0,0,0,16,3,16))).orElseThrow();h.assertTrue(one.equals(Set.of(new net.minecraft.world.level.ChunkPos(0,0))),"Exact boundary included extra chunk");
        var negative=CoreChunkLoading.footprint(List.of(new AABB(-16,0,-16,0,3,0))).orElseThrow();h.assertTrue(negative.equals(Set.of(new net.minecraft.world.level.ChunkPos(-1,-1))),"Negative boundary wrong");
        h.assertTrue(CoreChunkLoading.footprint(List.of(new AABB(0,0,0,10000,3,10000))).isEmpty(),"Huge body bypassed loading cap");h.succeed();
    }
    @GameTest(template="combat",batch="chunk_core_removed")
    public static void PhysicalCoreRemovalReleasesLoadingAndDoesNotForceRewardChunks(GameTestHelper h){
        var core=fixture(h);var level=h.getLevel();var author=TestActors.creative(level);core.editCore(author,"forceChunkLoading",1,"");core.tryActivate(author);CoreChunkLoading.observe(core);CoreChunkLoading.refresh(level.getServer());var owner=core.creatureId();core.editCore(author,"breaking",1,"");h.assertTrue(level.removeBlock(core.getBlockPos(),false),"Fixture did not remove an unlocked core");
        h.assertTrue(!ticket(core)&&CoreChunkLoading.chunks(level.getServer(),level,owner).isEmpty()&&!LifeLifecycle.get(level.getServer()).loadingRewards(level.dimension().location()).containsKey(owner),"Removed core leaked body/world/reward loading");h.succeed();
    }
    @GameTest(template="combat",batch="chunk_dying_toggle")
    public static void DyingCoreCanDisableLoadingAndDeathJobsKeepSixtySecondCap(GameTestHelper h){
        var core=fixture(h);var level=h.getLevel();var author=TestActors.creative(level);core.editCore(author,"forceChunkLoading",1,"");core.tryActivate(author);CoreChunkLoading.observe(core);CoreChunkLoading.refresh(level.getServer());core.damage(core.health().maximum());h.assertTrue(core.editCore(author,"forceChunkLoading",0,"")&&!ticket(core),"Dying core could not turn loading Off");
        var job=new net.minecraft.nbt.CompoundTag();job.putUUID("Id",UUID.randomUUID());job.putString("Dimension",level.dimension().location().toString());job.putString("Loot","minecraft:empty");job.putInt("Remaining",6000);job.putBoolean("ForceLoading",true);var jobs=new net.minecraft.nbt.ListTag();jobs.add(job);var data=new net.minecraft.nbt.CompoundTag();data.put("Deaths",jobs);var saved=LifeLifecycle.load(data,level.registryAccess()).save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()).getList("Deaths",10).getCompound(0);
        h.assertTrue(saved.getInt("Remaining")==1200&&saved.getBoolean("ForceLoading"),"Saved world death job cap/loading flag lost");h.succeed();
    }
}
