package dev.sablelife.testing;

import com.mojang.authlib.GameProfile;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.ownership.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_audit_tests")
@PrefixGameTestTemplate(false)
public final class AuditSafetyGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h,int x) { return fixture(h,x,12); }
    private static HeartBlockEntity fixture(GameTestHelper h,int x,int y) {
        var p=h.absolutePos(new BlockPos(x,y,3));var level=h.getLevel();
        level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static FakePlayer author(GameTestHelper h,boolean operator) {
        var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),operator?"AuditOperator":"AuditCreator")) {
            @Override public boolean canUseGameMasterBlocks() { return operator; }
        };
        player.setGameMode(GameType.CREATIVE);return player;
    }
    @GameTest(template="combat",batch="audit_import_security")
    public static void CopierCannotBypassCommandPermissionsAndRejectsBeforeMutation(GameTestHelper h) {
        var source=fixture(h,3);var target=fixture(h,8);var command=source.modules().add("command");command.commands().edit("add",0,0,"");command.commands().edit("command",0,0,"say imported");
        var data=source.copyConfiguration();int revision=target.modules().revision();
        h.assertTrue(!target.pasteConfiguration(author(h,false),data)&&target.modules().all().isEmpty()&&target.modules().revision()==revision,"Non-operator command copy mutated target");
        h.assertTrue(target.pasteConfiguration(author(h,true),data)&&target.modules().all().getFirst().commands().rows().size()==1,"Operator copy lost commands");
        h.assertTrue(target.onlyOpCanSetNbt(),"Core block-item NBT bypass remains open");h.succeed();
    }
    @GameTest(template="combat",batch="audit_preset_limits")
    public static void PresetNestingAndNodeBudgetsAreBoundedWithoutRejectingQuotedBraces(GameTestHelper h) {
        String deep="{a:".repeat(AuthoringLimits.MAX_DEPTH+1)+"0"+"}".repeat(AuthoringLimits.MAX_DEPTH+1);
        h.assertTrue(!AuthoringLimits.presetText(deep)&&AuthoringLimits.presetText("{Name:'{[}',Type:'auto_facing'}"),"Depth/quoted string checks wrong");
        var tag=new CompoundTag();var list=new ListTag();for(int i=0;i<AuthoringLimits.MAX_NODES+1;i++)list.add(IntTag.valueOf(i));tag.put("Rows",list);
        h.assertTrue(!AuthoringLimits.compound(tag,AuthoringLimits.MAX_PRESET_BYTES),"Too many nodes accepted");
        h.assertTrue(AuthoringLimits.presetText(new VirtualModule("auto_facing").save().toString()),"Ordinary preset rejected");h.succeed();
    }
    @GameTest(template="combat",batch="audit_request_rate",timeoutTicks=10)
    public static void RequestBudgetLimitsBurstsAndRecoversOnWorldTicks(GameTestHelper h) {
        var player=author(h,false);for(int i=0;i<80;i++)h.assertTrue(AuthoringRateLimit.allow(player,1),"Burst budget too small");
        h.assertTrue(!AuthoringRateLimit.allow(player,1),"Burst budget unbounded");
        h.runAfterDelay(1,()->{for(int i=0;i<4;i++)h.assertTrue(AuthoringRateLimit.allow(player,1),"Budget failed to recover");h.assertTrue(!AuthoringRateLimit.allow(player,1),"Recovered too many tokens");h.succeed();});
    }
    @GameTest(template="combat",batch="audit_particle_copy")
    public static void CopierClearsBothParticleMarkSets(GameTestHelper h) {
        var core=fixture(h,3);var m=core.modules().add("particles");
        var a=ModuleAnchor.create(core,core.getBlockPos(),Direction.UP);var b=ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP);
        m.anchors(List.of(a,b));m.edit("particlePattern",1,"");m.anchors(List.of(a));
        var data=core.copyConfiguration();var saved=data.getList("Modules",10).getCompound(0);
        h.assertTrue(!saved.contains("ParticleAreaAnchors")&&!saved.contains("ParticlePointAnchors"),"Hidden marks escaped copier sanitation");
        h.assertTrue(core.pasteConfiguration(author(h,false),data),"Ordinary non-operator copy refused");var copy=core.modules().all().getFirst();
        h.assertTrue(copy.anchors().isEmpty(),"Point marks copied");copy.edit("particlePattern",0,"");h.assertTrue(copy.anchors().isEmpty(),"Area marks copied");h.succeed();
    }
    @GameTest(template="combat",batch="audit_particle_transfer")
    public static void ReassemblyMigratesInactiveParticleMarks(GameTestHelper h) {
        var core=fixture(h,3);var m=core.modules().add("particles");var p=core.getBlockPos();
        m.anchors(List.of(ModuleAnchor.create(core,p,Direction.UP),ModuleAnchor.create(core,p.east(),Direction.UP)));m.edit("particlePattern",1,"");m.anchors(List.of(ModuleAnchor.create(core,p,Direction.NORTH)));
        var old=m.anchors().getFirst().bodyId();var body=SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        var moved=(HeartBlockEntity)h.getLevel().getBlockEntity(body.getPlot().getCenterBlock());var copy=moved.modules().find(m.id());
        h.assertTrue(!body.getUniqueId().equals(old)&&copy.storedAnchors().size()==3&&copy.storedAnchors().stream().allMatch(a->a.anchor().bodyId().equals(body.getUniqueId())),"Inactive mark retained old body UUID");
        copy.edit("particlePattern",0,"");h.assertTrue(HeartModules.status(copy,moved).equals("valid"),"Migrated area invalid");h.succeed();
    }
    @GameTest(template="combat",batch="audit_target_index")
    public static void TargetIndexSharesBoundsAndIgnoresUnrelatedOwnership(GameTestHelper h) {
        var core=fixture(h,3);var other=fixture(h,8);var p=author(h,true);core.tryActivate(p);other.tryActivate(p);
        var unrelated=UUID.randomUUID();for(int i=0;i<5000;i++)BodyOwnership.get(h.getLevel()).bind(new BlockPos(200000+i,10,200000),unrelated);
        var center=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());var box=new AABB(center,center).inflate(32);int before=LoadedCoreIndex.rebuilds(h.getLevel());
        for(int i=0;i<20;i++)h.assertTrue(ModuleTargets.query(h.getLevel(),core.creatureId(),box,e->ModuleTargets.matches(e,3)).stream().anyMatch(e->e.getUUID().equals(other.creatureId())),"Index lost connected creature target");
        h.assertTrue(LoadedCoreIndex.rebuilds(h.getLevel())==before+1,"Spatial index rebuilt for every query");
        BodyOwnership.get(h.getLevel()).release(other.creatureId());h.assertTrue(ModuleTargets.resolve(h.getLevel(),other.creatureId(),core.creatureId())==null,"Released owner stayed addressable");BodyOwnership.get(h.getLevel()).release(unrelated);h.succeed();
    }
    @GameTest(template="combat",batch="audit_blueprint_native")
    public static void NativeBlueprintSessionRemapsActionsBothMarkSetsAndLocksCommands(GameTestHelper h) {
        var core=fixture(h,3);var particles=core.modules().add("particles");var table=core.modules().add("move_set");var command=core.modules().add("command");
        table.moveSet().edit("add",0,"",core.modules());table.moveSet().edit("action_add",0,"0|"+particles.id(),core.modules());
        var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),core.getBlockPos());
        var tag=core.saveWithoutMetadata(h.getLevel().registryAccess());var list=tag.getCompound("VirtualModules").getList("Modules",10);
        var mark=new CompoundTag();mark.putString("Face","up");var reference=new CompoundTag();reference.putInt("Part",0);reference.putLong("Pos",BlockPos.ZERO.asLong());mark.put("sablelife:blueprint_mark",reference);
        var row=list.getCompound(0);for(var key:List.of("Anchors","ParticleAreaAnchors","ParticlePointAnchors")){var marks=new ListTag();marks.add(mark.copy());if(key.equals("ParticleAreaAnchors")){var second=mark.copy();second.getCompound("sablelife:blueprint_mark").putLong("Pos",new BlockPos(1,0,0).asLong());marks.add(second);}row.put(key,marks);}
        var session=new dev.rew1nd.sableschematicapi.api.blueprint.BlueprintPlaceSession(h.getLevel(),new org.joml.Vector3d(),new CompoundTag());session.mapSubLevel(0,body.getUniqueId(),body,core.getBlockPos());
        var context=new dev.rew1nd.sableschematicapi.api.blueprint.BlueprintBlockPlaceContext(session,body,0,body.getUniqueId(),BlockPos.ZERO,core.getBlockPos(),core.getBlockState());
        new dev.sablelife.compat.photomancy.HeartBlueprintMapper().beforeLoadBlockEntity(context,tag);
        var mappedParticles=VirtualModule.load(list.getCompound(0));var mappedTable=VirtualModule.load(list.getCompound(1));var mappedCommand=VirtualModule.load(list.getCompound(2));
        h.assertTrue(!mappedParticles.id().equals(particles.id())&&mappedTable.moveSet().get(0).actions().getFirst().module().equals(mappedParticles.id()),"Native session left source module reference");
        h.assertTrue(mappedParticles.storedAnchors().size()==3&&mappedParticles.storedAnchors().stream().allMatch(a->a.anchor().bodyId().equals(body.getUniqueId())),"Hidden particle marks did not use placed body");
        h.assertTrue(mappedCommand.commandApprovalRequired(),"Actor-free blueprint API bypassed command approval");h.succeed();
    }
    @GameTest(template="combat",batch="audit_history_compaction")
    public static void LegacySettledJobsCompactWithoutLosingRewardOrLimbDeduplication(GameTestHelper h) {
        var tag=new CompoundTag();var jobs=new ListTag();var parent=UUID.randomUUID();var first=UUID.randomUUID();
        for(int i=0;i<1000;i++){var job=new CompoundTag();job.putUUID("Id",i==0?first:UUID.randomUUID());job.putUUID("Parent",parent);job.putString("PartName","Leg");job.putString("Dimension",h.getLevel().dimension().location().toString());job.putString("Loot","minecraft:empty");job.putBoolean("Cleaned",true);job.putBoolean("Rewarded",true);jobs.add(job);}tag.put("Deaths",jobs);
        var life=LifeLifecycle.load(tag,h.getLevel().registryAccess());h.assertTrue(life.pendingCount()==0&&life.completedCount()==1000&&life.lostPart(parent,"Leg"),"Completed legacy jobs still entered tick work or lost limb history");
        h.assertTrue(!life.schedulePart(h.getLevel(),first,parent,"Leg",h.absolutePos(BlockPos.ZERO).getCenter(),0,net.minecraft.resources.ResourceLocation.withDefaultNamespace("empty"),0),"Settled limb could schedule twice");
        var saved=life.save(new CompoundTag(),h.getLevel().registryAccess());h.assertTrue(saved.getList("Deaths",10).isEmpty()&&saved.getList("CompletedDeaths",10).size()==1000,"Full completed jobs were retained");
        var restored=LifeLifecycle.load(saved,h.getLevel().registryAccess());restored.tickDeaths(h.getLevel().getServer());h.assertTrue(restored.pendingCount()==0&&restored.isDying(first),"Completed UUID protection did not survive restart data");h.succeed();
    }
    @GameTest(template="combat",batch="audit_cleanup_cursor")
    public static void UnloadedCleanupRotatesWithinVisitBudgetAndIndexesSurviveReload(GameTestHelper h) {
        var data=new BodyOwnership();var owner=UUID.randomUUID();var part=UUID.randomUUID();
        for(int i=0;i<100;i++){var pos=new BlockPos(200000+i,10,200000);data.bind(pos,owner);data.bindPart(pos,part);}
        var work=data.cleanBudgeted(h.getLevel(),owner,256,7,false,null);
        h.assertTrue(work.visited()==7&&work.removed()==0&&data.hasOwned(owner),"Unloaded cleanup ignored visit budget or lost ownership");
        var copy=BodyOwnership.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(copy.hasPart(part)&&copy.cleanBudgeted(h.getLevel(),part,256,5,true,null).visited()==5,"Part index failed persisted restoration");
        copy.release(owner);h.assertTrue(!copy.hasOwned(owner)&&!copy.hasPart(part),"Release left stale owner/part index");h.succeed();
    }
    @GameTest(template="combat",batch="audit_health_packet")
    public static void HealthPacketsExcludeAuthoringButFullSaveKeepsConfiguration(GameTestHelper h) {
        var core=fixture(h,3);core.modules().add("particles");core.wireless().add();core.wireless().channel(0).set(0,1);
        var full=core.saveWithoutMetadata(h.getLevel().registryAccess());var packet=core.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(full.contains("VirtualModules")&&full.getCompound("Wireless").getList("Channels",10).size()==1,"Full world configuration lost");
        h.assertTrue(!packet.contains("VirtualModules")&&!packet.contains("Wireless")&&!packet.contains("DeathTemplates")&&!packet.contains("DeathPresentation"),"Health update still carries authoring payload");
        h.assertTrue(packet.getUUID("CreatureId").equals(core.creatureId())&&packet.getFloat("Health")==core.health().current(),"Client identity/health missing");h.succeed();
    }
    @GameTest(template="combat",batch="audit_collision_wait",timeoutTicks=15)
    public static void CollisionBudgetDefersTeleportWithoutCancellingItsCast(GameTestHelper h) {
        var core=fixture(h,3,40);var m=core.modules().add("linear_movement");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.UP)));m.edit("trigger",0,"");m.edit("chargeTime",0,"");m.edit("moveDistance",2,"");m.edit("interval",200,"");core.tryActivate(author(h,true));
        var before=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());
        h.assertTrue(CollisionWorkBudget.begin(h.getLevel()).spend(dev.sablelife.SableLifeServerConfig.collisionChecksPerTick()),"Could not reserve current tick budget");core.modules().tick(core);
        h.assertTrue(core.modules().snapshot(core,true).getList("Modules",10).getCompound(0).getString("MovementPhase").equals("budget"),"Exhaustion was treated as cancelled/blocked movement");
        h.runAfterDelay(1,()->core.modules().tick(core));
        h.runAfterDelay(4,()->{var after=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());var phase=core.modules().snapshot(core,true).getList("Modules",10).getCompound(0).getString("MovementPhase");h.assertTrue(phase.equals("arrived")&&after.y-before.y>1.7,"Deferred cast did not resume: phase="+phase+" before="+before+" after="+after);h.succeed();});
    }
    @GameTest(template="combat",batch="audit_signal_index")
    public static void IndexedSignalsKeepMaxStrengthZeroConnectionsAndScopedClear(GameTestHelper h) {
        var a=fixture(h,3);var b=fixture(h,8);var p=author(h,true);a.tryActivate(p);b.tryActivate(p);var pos=h.absolutePos(new BlockPos(3,20,3));
        ModuleSignals.update(a,Map.of(pos,4));ModuleSignals.update(b,Map.of(pos,11));h.assertTrue(ModuleSignals.signal(h.getLevel(),pos)==11,"Indexed max aggregation wrong");
        ModuleSignals.clear(b);h.assertTrue(ModuleSignals.signal(h.getLevel(),pos)==4,"Scoped removal erased another publisher");
        ModuleSignals.updateFaces(a,Map.of(new ModuleSignals.Face(pos,Direction.NORTH),0));h.assertTrue(ModuleSignals.connects(h.getLevel(),pos,Direction.NORTH),"Zero-strength configured face lost connection");
        ModuleSignals.clear(a);h.assertTrue(ModuleSignals.signal(h.getLevel(),pos)==0&&!ModuleSignals.connects(h.getLevel(),pos,Direction.NORTH),"Index retained cleared output");h.succeed();
    }
    @GameTest(template="combat",batch="audit_signal_teardown",timeoutTicks=80)
    public static void UnloadForgetsOutputsWithoutUpdatingBlocksAfterNativeRemoval(GameTestHelper h) {
        var core=fixture(h,3,40);core.tryActivate(author(h,true));core.modules().tick(core);
        var pos=h.absolutePos(new BlockPos(5,20,5));var lamp=pos.east();
        h.getLevel().setBlockAndUpdate(pos,Blocks.IRON_BLOCK.defaultBlockState());
        h.getLevel().setBlockAndUpdate(lamp,Blocks.REDSTONE_LAMP.defaultBlockState());
        ModuleSignals.update(core,Map.of(pos,15));
        ModuleSignals.updateFaces(core,Map.of(new ModuleSignals.Face(pos,Direction.NORTH),7));
        ModuleSignals.updateWeakFaces(core,Map.of(new ModuleSignals.Face(pos,Direction.SOUTH),9));
        h.assertTrue(h.getLevel().getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),"Fixture did not light lamp");
        var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),core.getBlockPos());
        dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel()).removeSubLevel(body,dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.UNLOADED);
        core.onChunkUnloaded();
        h.assertTrue(ModuleSignals.signal(h.getLevel(),pos)==0&&!ModuleSignals.connects(h.getLevel(),pos,Direction.NORTH)&&!ModuleSignals.connects(h.getLevel(),pos,Direction.SOUTH),"Unload retained signals or configured connections");
        h.runAfterDelay(8,()->{h.assertTrue(h.getLevel().getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT),"Unload sent block updates after native removal");h.succeed();});
    }
    @GameTest(template="combat",batch="audit_hidden_payload")
    public static void PresetsDiscardUnrelatedHiddenGroups(GameTestHelper h) {
        var tag=new VirtualModule("item_display").save();var junk=new CompoundTag();junk.putString("unrelated","x".repeat(12000));tag.put("SoundTimelines",junk);tag.put("LootSamples",junk.copy());tag.put("RedBlue",junk.copy());
        var saved=ModulePreset.create(tag).save();h.assertTrue(saved.getCompound("SoundTimelines").isEmpty()&&saved.getCompound("LootSamples").isEmpty()&&saved.getCompound("RedBlue").isEmpty(),"Unrelated preset payload was persisted");h.succeed();
    }
    @GameTest(template="combat",batch="audit_large_snapshot")
    public static void LargeSnapshotKeepsSelectedDetailsAndCompactsUnselectedModules(GameTestHelper h) {
        var core=fixture(h,3);var p=author(h,true);p.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter()));
        var item=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);var custom=new CompoundTag();custom.putString("model_data","x".repeat(12000));item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(custom));
        for(int i=0;i<HeartModules.authoringLimit();i++)core.modules().add("item_display").projectileItem(item,h.getLevel().registryAccess());
        var selected=core.modules().all().get(17);DashboardSelection.set(core,p.getUUID(),selected.id());var menu=new HeartDashboardMenu(1,p.getInventory(),core);var snapshot=menu.snapshot();
        h.assertTrue(snapshot.getBoolean("PartialAuthoring")&&!snapshot.getBoolean("AuthoringOversized")&&snapshot.sizeInBytes()<1_000_000,"Large snapshot was not bounded");
        var rows=snapshot.getList("Modules",10);h.assertTrue(rows.size()==HeartModules.authoringLimit(),"Compaction hid sidebar modules");
        for(var value:rows){var row=(CompoundTag)value;h.assertTrue(row.getUUID("Id").equals(selected.id())?!row.getCompound("ProjectileItem").isEmpty():!row.contains("ProjectileItem"),"Selected configuration was lost or unselected deep data remained");}h.succeed();
    }
    @GameTest(template="combat",batch="audit_projectile_quota")
    public static void ProjectileQuotaRejectsOnlyNewSpawnsAndReturnsCapacityOnRemoval(GameTestHelper h) {
        var core=fixture(h,3);var m=new VirtualModule("gun");var config=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues().getRaw("maximumProjectilesPerCreature");int previous=config.get();
        var shots=new ArrayList<dev.sablelife.attack.ModuleShot>();int before=dev.sablelife.attack.ProjectileBudget.count(h.getLevel());
        try {
            config.set(64);var point=h.absolutePos(new BlockPos(3,40,3)).getCenter();
            for(int i=0;i<64;i++){var shot=new dev.sablelife.attack.ModuleShot(h.getLevel(),point,net.minecraft.world.phys.Vec3.ZERO,m,core);h.assertTrue(h.getLevel().addFreshEntity(shot),"Quota rejected an allowed spawn");shots.add(shot);}
            var blocked=new dev.sablelife.attack.ModuleShot(h.getLevel(),point,net.minecraft.world.phys.Vec3.ZERO,m,core);h.assertTrue(!h.getLevel().addFreshEntity(blocked)&&shots.stream().noneMatch(net.minecraft.world.entity.Entity::isRemoved),"Quota removed old flights or accepted excess");
            shots.remove(0).discard();var replacement=new dev.sablelife.attack.ModuleShot(h.getLevel(),point,net.minecraft.world.phys.Vec3.ZERO,m,core);h.assertTrue(h.getLevel().addFreshEntity(replacement),"Removed flight did not return capacity");shots.add(replacement);
        } finally {config.set(previous);shots.forEach(net.minecraft.world.entity.Entity::discard);}
        h.assertTrue(dev.sablelife.attack.ProjectileBudget.count(h.getLevel())==before,"Projectile accounting leaked after removal");h.succeed();
    }
}
