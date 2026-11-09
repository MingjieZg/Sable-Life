package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.compat.photomancy.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.ownership.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Opt-in regression coverage for the retention defects found in the source review. */
@GameTestHolder("sablelife_review_retention_tests")
@PrefixGameTestTemplate(false)
public final class ReviewRetentionGameTests {
    private record EnergyDrop(net.minecraft.world.entity.item.ItemEntity item,net.minecraft.world.item.ItemStack claim,net.minecraft.server.level.ServerPlayer actor){}
    private static EnergyDrop energyDrop(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,6,1));
        level.setBlockAndUpdate(pos,SableLife.SURVIVAL_LIFE_CORE.get().defaultBlockState());
        var core=(HeartBlockEntity)level.getBlockEntity(pos);var actor=TestActors.creative(level);
        actor.setGameMode(GameType.SURVIVAL);actor.setPos(pos.getCenter());
        h.assertTrue(core.depositSurvivalTokens(actor,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT,3)),"Cannot fund energy fixture");
        var stack=new net.minecraft.world.item.ItemStack(SableLife.SURVIVAL_LIFE_CORE_ITEM.get());
        var item=new net.minecraft.world.entity.item.ItemEntity(level,pos.getX(),pos.getY()+2,pos.getZ(),stack);
        var event=new net.neoforged.neoforge.event.level.BlockDropsEvent(level,pos,core.getBlockState(),core,new ArrayList<>(List.of(item)),actor,net.minecraft.world.item.ItemStack.EMPTY);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        h.assertTrue(!event.isCanceled()&&core.survivalTokenBalance()==0,"Drop did not receive one-use energy claim");
        return new EnergyDrop(item,stack.copy(),actor);
    }
    private static void redeemAfter(GameTestHelper h,EnergyDrop drop,long expected){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,6,1));var block=SableLife.SURVIVAL_LIFE_CORE.get();
        level.setBlockAndUpdate(pos,block.defaultBlockState());
        block.setPlacedBy(level,pos,block.defaultBlockState(),drop.actor,drop.claim);
        h.runAfterDelay(3,()->{
            h.assertTrue(((HeartBlockEntity)level.getBlockEntity(pos)).survivalTokenBalance()==expected,"Unexpected retained/revoked energy after item lifecycle: expected "+expected);
            h.succeed();
        });
    }
    @GameTest(template="combat",batch="review_energy_damage")
    public static void DestroyedItemRetiresItsEnergyClaim(GameTestHelper h){
        var d=energyDrop(h);h.assertTrue(d.item.hurt(h.getLevel().damageSources().lava(),100)&&d.item.isRemoved(),"Item damage fixture survived");redeemAfter(h,d,0);
    }
    @GameTest(template="combat",batch="review_energy_expiry")
    public static void DespawnRetiresItsEnergyClaim(GameTestHelper h){
        var d=energyDrop(h);d.item.lifespan=1;d.item.tick();h.assertTrue(d.item.isRemoved(),"Expired item survived");redeemAfter(h,d,0);
    }
    @GameTest(template="combat",batch="review_energy_expiry_extended")
    public static void ExtendedDespawnRetainsItsEnergyClaim(GameTestHelper h){
        var d=energyDrop(h);
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.item.ItemExpireEvent> extend=e->{if(e.getEntity()==d.item)e.addExtraLife(100);};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(extend);
        try{d.item.lifespan=1;d.item.tick();h.assertTrue(!d.item.isRemoved(),"Extended item was destroyed");}
        finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(extend);}
        redeemAfter(h,d,30);
    }
    @GameTest(template="combat",batch="review_energy_pickup")
    public static void PickupRetainsItsEnergyClaim(GameTestHelper h){
        var d=energyDrop(h);d.item.setNoPickUpDelay();d.item.playerTouch(d.actor);h.assertTrue(d.item.isRemoved(),"Item was not picked up");redeemAfter(h,d,30);
    }
    @GameTest(template="combat",batch="review_energy_unload")
    public static void ChunkUnloadRetainsItsEnergyClaim(GameTestHelper h){
        var d=energyDrop(h);d.item.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);redeemAfter(h,d,30);
    }
    @GameTest(template="combat",batch="review_energy_void")
    public static void VoidDestructionRetiresItsEnergyClaim(GameTestHelper h){
        var d=energyDrop(h);d.item.setPos(d.item.getX(),h.getLevel().getMinBuildHeight()-128,d.item.getZ());d.item.tick();
        h.assertTrue(d.item.isRemoved(),"Void fixture survived");redeemAfter(h,d,0);
    }
    @GameTest(template="combat",batch="review_energy_kill")
    public static void ExplicitKillRetiresItsEnergyClaim(GameTestHelper h){
        var d=energyDrop(h);d.item.kill();h.assertTrue(d.item.isRemoved(),"Killed item survived");redeemAfter(h,d,0);
    }
    private record Fixture(HeartBlockEntity core, net.minecraft.server.level.ServerPlayer author) {}
    private static Fixture fixture(GameTestHelper h, int offset) {
        var level=h.getLevel();
        var pos=h.absolutePos(new BlockPos(4+offset,35,4));
        level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),
                new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
        core.configure(100,0);
        var author=new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ReviewAdmin")) {
            @Override public boolean canUseGameMasterBlocks(){return isCreative();}
        };
        author.setGameMode(GameType.CREATIVE);
        author.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,core.getBlockPos().getCenter()));
        return new Fixture(core,author);
    }

    @GameTest(template="combat",batch="review_legacy_orphans")
    public static void ReloadedCoreReclaimsOnlyItsMissingIdleTemplateRecords(GameTestHelper h){
        var f=fixture(h,0);var level=h.getLevel();var blueprint=f.core.modules().add("blueprint_spawn");
        BlueprintSpawns.install(f.core,blueprint,"legacy",SpawnTemplateBridge.inspect(PhotomancyRespawnBridge.capture(f.core,ModuleArena.corePosition(f.core))));
        var arena=f.core.modules().add("arena");arena.arenaCenter(ModuleArena.corePosition(f.core),level.dimension().location().toString());
        h.assertTrue(ArenaRespawns.record(f.author,f.core,arena),"Cannot seed legacy arena");
        f.core.modules().tick(f.core);
        var replacement=new CompoundTag();replacement.putInt("Revision",f.core.modules().revision());
        f.core.modules().load(replacement); // Old saves can have idle world records for absent modules.
        f.core.modules().tick(f.core);
        h.assertTrue(!BlueprintSpawns.snapshot(f.core,blueprint).contains("Name")&&!ArenaRespawns.snapshot(f.core,arena.id()).getBoolean("Recorded"),"Reloaded core retained its proven orphan records");
        h.succeed();
    }

    @GameTest(template="combat",batch="review_detached_job",timeoutTicks=70)
    public static void DeletingModuleKeepsCommittedBlueprintJobUntilOnePlacementAfterReload(GameTestHelper h){
        var f=fixture(h,0);var level=h.getLevel();var server=level.getServer();
        var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        var before=container.getAllSubLevels().stream().map(b->b.getUniqueId()).collect(java.util.stream.Collectors.toSet());
        var info=SpawnTemplateBridge.inspect(PhotomancyRespawnBridge.capture(f.core,ModuleArena.corePosition(f.core)));
        var module=f.core.modules().add("blueprint_spawn");module.edit("spawnAnchor",1,"");
        module.arenaCenter(h.absoluteVec(new net.minecraft.world.phys.Vec3(4,35,12)),level.dimension().location().toString());module.edit("spawnDelay",0,"");
        BlueprintSpawns.install(f.core,module,"detached",info);f.core.tryActivate(f.author);BlueprintSpawns.tickCore(f.core);
        h.assertTrue(BlueprintSpawns.snapshot(f.core,module).getString("Phase").equals("queued"),"Fixture did not queue");
        f.core.modules().remove(module.id());
        var saved=BlueprintSpawns.get(server).save(new CompoundTag(),level.registryAccess());
        h.assertTrue(saved.getList("Entries",10).stream().map(v->(CompoundTag)v).anyMatch(row->row.getUUID("Module").equals(module.id())&&row.getBoolean("Detached")),"Deletion dropped the independent queued job");
        server.overworld().getDataStorage().set("sablelife_blueprint_spawns",BlueprintSpawns.load(saved,level.registryAccess()));
        h.runAfterDelay(20,()->{
            var created=container.getAllSubLevels().stream().filter(b->!before.contains(b.getUniqueId())&&!b.isRemoved()).toList();
            h.assertTrue(created.size()==1&&BlueprintSpawns.snapshot(f.core,module).getString("Name").isEmpty(),"Detached job failed, duplicated placement or retained its template");
            for(var body:created)dev.sablelife.compat.StructureRemovalApi.remove(level,(dev.ryanhcode.sable.sublevel.ServerSubLevel)body);
            h.succeed();
        });
    }

    @GameTest(template="combat",batch="review_blueprint_retention",timeoutTicks=100)
    public static void DeletingBlueprintModulesMustReleaseWorldTemplateSlots(GameTestHelper h) {
        var f=fixture(h,0);var level=h.getLevel();var server=level.getServer();
        var info=SpawnTemplateBridge.inspect(PhotomancyRespawnBridge.capture(f.core,
                dev.sablelife.module.ModuleArena.corePosition(f.core)));
        var menu=new HeartDashboardMenu(1,f.author.getInventory(),f.core);
        try {
            for(int i=0;i<32;i++) {
                var module=f.core.modules().add("blueprint_spawn");
                h.assertTrue(BlueprintSpawns.install(f.core,module,"review_"+i,info),"Fixture install failed");
                h.assertTrue(menu.action(f.author,f.core.modules().revision(),module.id(),"remove","",0,""),"UI removal failed");
            }
            var next=f.core.modules().add("blueprint_spawn");
            boolean accepted;
            try {accepted=BlueprintSpawns.install(f.core,next,"review_next",info);}
            catch(IllegalStateException limit){accepted=false;}
            int retained=BlueprintSpawns.get(server).save(new CompoundTag(),level.registryAccess()).getList("Entries",10).size();
            com.mojang.logging.LogUtils.getLogger().info("REVIEW_BLUEPRINT_RETENTION retained={}, nextAccepted={}",retained,accepted);
            h.assertTrue(accepted,"Deleting 32 modules retained world template slots; next install rejected, retained="+retained);
            h.succeed();
        } finally {BlueprintSpawns.release(f.core);}
    }

    @GameTest(template="combat",batch="review_arena_retention",timeoutTicks=100)
    public static void DeletingRecordedArenaMustAllowReplacementRecording(GameTestHelper h) {
        var f=fixture(h,0);var level=h.getLevel();
        var first=f.core.modules().add("arena");
        first.arenaCenter(ModuleArena.corePosition(f.core),level.dimension().location().toString());
        var menu=new HeartDashboardMenu(1,f.author.getInventory(),f.core);
        try {
            h.assertTrue(ArenaRespawns.record(f.author,f.core,first),"Fixture capture failed");
            h.assertTrue(menu.action(f.author,f.core.modules().revision(),first.id(),"remove","",0,""),"UI removal failed");
            var replacement=f.core.modules().add("arena");
            replacement.arenaCenter(ModuleArena.corePosition(f.core),level.dimension().location().toString());
            boolean accepted=ArenaRespawns.record(f.author,f.core,replacement);
            com.mojang.logging.LogUtils.getLogger().info("REVIEW_ARENA_RETENTION retained={}, replacementAccepted={}",ArenaRespawns.get(level.getServer()).count(),accepted);
            h.assertTrue(accepted,"Deleted arena retains the creature's only recording slot");
            h.succeed();
        } finally {ArenaRespawns.release(f.core);}
    }

    @GameTest(template="combat",batch="review_completed_retention",timeoutTicks=100)
    public static void FinishedUnreferencedRootDeathsMustRetireTheirCompletionRecords(GameTestHelper h) {
        var lifecycle=LifeLifecycle.get(h.getLevel().getServer());
        int before=lifecycle.completedCount();var owners=new ArrayList<UUID>();var staleCores=new ArrayList<HeartBlockEntity>();
        for(int i=0;i<4;i++) {
            var f=fixture(h,i*4);owners.add(f.core.creatureId());staleCores.add(f.core);
            f.core.tryActivate(f.author);f.core.forceDeath();
        }
        h.runAfterDelay(30,()->{
            int after=lifecycle.completedCount();
            int saved=lifecycle.save(new CompoundTag(),h.getLevel().registryAccess()).getList("CompletedDeaths",10).size();
            boolean cleaned=owners.stream().noneMatch(id->BodyOwnership.get(h.getLevel()).hasOwned(id));
            com.mojang.logging.LogUtils.getLogger().info("REVIEW_COMPLETED_RETENTION before={}, after={}, saved={}, allBodiesCleaned={}",before,after,saved,cleaned);
            try {
                h.assertTrue(cleaned,"Fixture cleanup has not completed");
                h.assertTrue(after<=before,"Unreferenced completed root deaths remain in saved data: "+before+" -> "+after);
                staleCores.forEach(lifecycle::schedule);
                h.assertTrue(owners.stream().noneMatch(lifecycle::isDying),"Removed core callback recreated a retired death");
                h.succeed();
            } finally {owners.forEach(lifecycle::cancel);}
        });
    }
}
