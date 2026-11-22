package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.compat.photomancy.SpawnTemplateBridge;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.*;
import dev.sablelife.ownership.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_blueprint_spawn_tests") @PrefixGameTestTemplate(false)
public final class BlueprintCommandGameTests {
    private record Fixture(String name, HeartBlockEntity core, java.nio.file.Path path) {}
    private static Fixture fixture(GameTestHelper h, int hearts, String suffix) throws Exception {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(3, 40, 3));
        level.setBlockAndUpdate(pos, hearts == 0 ? Blocks.IRON_BLOCK.defaultBlockState() : hearts == 3 ? SableLife.REDSTONE_CORE.get().defaultBlockState() : hearts == 4 ? SableLife.SURVIVAL_LIFE_CORE.get().defaultBlockState() : SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), hearts == 2 ? SableLife.HEART.get().defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, pos.east()), new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, pos.getY(), pos.getZ()));
        var core = level.getBlockEntity(body.getPlot().getCenterBlock()) instanceof HeartBlockEntity value ? value : null;
        if (core != null) {
            var module = core.modules().add("signal_logic"); module.edit("name", 0, "Command test settings"); module.edit("logicChannel", 37, "");
        }
        var tag = dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(level, Vec3.ZERO, List.of(body.getUniqueId())).save();
        String name = "command " + suffix + " " + UUID.randomUUID();
        var path = dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(level.getServer(), name);
        java.nio.file.Files.createDirectories(path.getParent()); NbtIo.writeCompressed(tag, path);
        return new Fixture(name, core, path);
    }
    private static CommandSourceStack source(GameTestHelper h) {
        return h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPosition(h.absoluteVec(new Vec3(15.5, 8, 15.5))).withPermission(2);
    }
    private static int execute(CommandSourceStack source, String text) throws Exception {
        return source.getServer().getCommands().getDispatcher().execute(text, source);
    }
    private static String command(Fixture fixture, boolean active) { return "sablelife blueprint spawn \"" + fixture.name + "\" " + active; }
    private static void drain(GameTestHelper h) { CommandBlueprintSpawns.get(h.getLevel().getServer()).advance(h.getLevel().getServer()); }
    private static void platform(GameTestHelper h) {
        for (int x=12; x<=18; x++) for (int z=12; z<=18; z++) h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(x, 7, z)), Blocks.STONE.defaultBlockState());
    }
    private static HeartBlockEntity spawned(GameTestHelper h, Fixture fixture) {
        var center = h.absoluteVec(new Vec3(15.5, 8, 15.5));
        return LoadedCoreIndex.all(h.getLevel()).stream().filter(core -> core != fixture.core)
                .filter(core -> SableBodyProbe.toWorld(h.getLevel(), core.getBlockPos().getCenter()).distanceToSqr(center) < 15*15)
                .findFirst().orElse(null);
    }
    @GameTest(template="combat", batch="command_surface", timeoutTicks=100)
    public static void SurfaceSpawnUsesFootprintAndKeepsCoreDormant(GameTestHelper h) throws Exception {
        var f=fixture(h,1,"surface"); platform(h);
        h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(16,9,15)),Blocks.STONE.defaultBlockState());
        try {
            h.assertTrue(execute(source(h).withPosition(h.absoluteVec(new Vec3(15.5, 350, 15.5))),command(f,false))==1,"Surface command rejected an executor above build height");
            drain(h); var core=spawned(h,f); h.assertTrue(core!=null,"Surface command produced no core");
            h.assertTrue(core.health().state()==HeartState.DORMANT,"False activated the core");
            var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),core.getBlockPos());
            double bottom=SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter()).y-.5;
            h.assertTrue(Math.abs(bottom-h.absolutePos(new BlockPos(15,10,15)).getY())<.02,"Blueprint block bottom was not aligned to the highest footprint ground: "+bottom);
            h.assertTrue(core.modules().all().stream().anyMatch(m->m.type().equals("signal_logic")&&m.value("logicChannel")==37),"Saved module setting was lost");
            h.assertTrue(!core.creatureId().equals(f.core.creatureId()),"Spawn reused the original creature ID");
        } finally { java.nio.file.Files.deleteIfExists(f.path); }
        h.succeed();
    }
    @GameTest(template="combat", batch="command_active", timeoutTicks=100)
    public static void ExplicitCoordinatesActivateAndPreserveOriginal(GameTestHelper h) throws Exception {
        var f=fixture(h,1,"active");
        try {
            h.assertTrue(execute(source(h),command(f,true)+" ~ ~8 ~")==1,"Explicit relative coordinates failed"); drain(h);
            var core=spawned(h,f); h.assertTrue(core!=null,"Active spawn missing");
            h.assertTrue(core.health().state()==HeartState.ALIVE||core.awaitingBlueprintActivation(),"True did not activate or schedule activation");
            h.assertTrue(f.core.health().state()==HeartState.DORMANT,"Original core was activated");
        } finally { java.nio.file.Files.deleteIfExists(f.path); }
        h.succeed();
    }
    @GameTest(template="combat", batch="command_rejected", timeoutTicks=100)
    public static void PermissionsHelpCompletionAndBadInputs(GameTestHelper h) throws Exception {
        var f=fixture(h,1,"tab");
        try {
            var low=source(h).withPermission(0);
            h.assertTrue(execute(low,"sablelife help")==1&&execute(low,"sablelife")==1,"Help unavailable to ordinary players");
            for(String command:List.of(command(f,false),"sablelife inspect ~ ~ ~","sablelife damage ~ ~ ~ 1","sablelife cleanup apply all")) {
                boolean denied=false;try{execute(low,command);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){denied=true;}
                h.assertTrue(denied,"Ordinary player entered administrator command: "+command);
            }
            var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();
            var suggestions=dispatcher.getCompletionSuggestions(dispatcher.parse("sablelife blueprint spawn ",source(h))).get().getList();
            h.assertTrue(suggestions.stream().anyMatch(s->s.getText().equals("\""+f.name+"\"")),"Names containing spaces were not quoted in suggestions");
            h.assertTrue(execute(source(h),"sablelife blueprint spawn \"../escape\" false")==0,"Traversal name accepted");
            h.assertTrue(execute(source(h),"sablelife blueprint spawn \"missing blueprint\" false")==0,"Missing name accepted");
            boolean rejected=false;try{execute(source(h),command(f,false).replace("false","maybe"));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){rejected=true;}
            h.assertTrue(rejected,"Invalid activation boolean accepted");
        } finally { java.nio.file.Files.deleteIfExists(f.path); }
        h.succeed();
    }
    @GameTest(template="combat", batch="command_failure", timeoutTicks=100)
    public static void ObstructionAtExecutionAndRepeatedRequestsLeaveNoBodies(GameTestHelper h) throws Exception {
        var f=fixture(h,1,"blocked");
        try {
            h.assertTrue(execute(source(h),command(f,false)+" ~ ~ ~")==1,"Clear placement rejected");
            h.assertTrue(execute(source(h),command(f,false)+" ~ ~ ~")==0,"Overlapping queued request accepted");
            var obstacle=h.absolutePos(new BlockPos(15,8,15));h.getLevel().setBlockAndUpdate(obstacle,Blocks.STONE.defaultBlockState());
            drain(h);h.assertTrue(spawned(h,f)==null,"Blocked queued command generated a core");
            h.assertTrue(h.getLevel().getBlockState(obstacle).is(Blocks.STONE),"Command changed terrain");
            h.assertTrue(CommandBlueprintSpawns.get(h.getLevel().getServer()).save(new CompoundTag(),h.getLevel().registryAccess()).getList("Jobs",10).isEmpty(),"Failed job was not retired");
        } finally { java.nio.file.Files.deleteIfExists(f.path); }
        h.succeed();
    }
    @GameTest(template="combat", batch="command_invalid_cores", timeoutTicks=100)
    public static void NoCoreAndMultipleCoreBlueprintsRejectActivation(GameTestHelper h) throws Exception {
        for(int count:new int[]{0,2,3,4}) {
            var f=fixture(h,count,"cores"+count);
            try { h.assertTrue(execute(source(h),command(f,true))==0,"Invalid core count activated"); }
            finally { java.nio.file.Files.deleteIfExists(f.path); }
        }
        h.succeed();
    }
    @GameTest(template="combat", batch="command_rollback", timeoutTicks=100)
    public static void ActivationFailureRemovesOnlyTheNewPartialStructure(GameTestHelper h) throws Exception {
        var f=fixture(h,0,"rollback");
        var container=SubLevelContainer.getContainer(h.getLevel());var before=container.getAllSubLevels().stream().map(b->b.getUniqueId()).collect(java.util.stream.Collectors.toSet());
        try {
            h.assertTrue(execute(source(h),command(f,false)+" ~ ~20 ~")==1,"Fault fixture was not accepted");
            var data=CommandBlueprintSpawns.get(h.getLevel().getServer());
            var field=CommandBlueprintSpawns.class.getDeclaredField("jobs");field.setAccessible(true);var entry=((Map<?,?>)field.get(data)).values().iterator().next();
            var active=entry.getClass().getDeclaredField("activate");active.setAccessible(true);active.setBoolean(entry,true);
            drain(h);
            h.assertTrue(container.getAllSubLevels().stream().allMatch(b->b.isRemoved()||before.contains(b.getUniqueId())),"Failed activation left a new body");
            h.assertTrue(before.stream().allMatch(id->container.getSubLevel(id)!=null),"Rollback removed an existing structure");
            h.assertTrue(data.save(new CompoundTag(),h.getLevel().registryAccess()).getList("Jobs",10).isEmpty(),"Failed activation job remained");
        } finally { java.nio.file.Files.deleteIfExists(f.path); }
        h.succeed();
    }
    @GameTest(template="combat", batch="command_queue", timeoutTicks=100)
    public static void QueueCapacityAndCommandBlockContextRemainBounded(GameTestHelper h) throws Exception {
        var f=fixture(h,0,"capacity");var data=CommandBlueprintSpawns.get(h.getLevel().getServer());
        try {
            var pos=h.absolutePos(new BlockPos(10,12,10));h.getLevel().setBlockAndUpdate(pos,Blocks.COMMAND_BLOCK.defaultBlockState());
            var block=(net.minecraft.world.level.block.entity.CommandBlockEntity)h.getLevel().getBlockEntity(pos);
            var commandSource=block.getCommandBlock().createCommandSourceStack();
            double admission=0,placement=0,maxPlacement=0;
            for(int i=0;i<8;i++) {
                long start=System.nanoTime();h.assertTrue(execute(commandSource,command(f,false)+" ~"+(i*3)+" ~20 ~")==1,"Command block source rejected or capacity too low");admission+=(System.nanoTime()-start)/1e6;
            }
            h.assertTrue(execute(commandSource,command(f,false)+" ~40 ~20 ~")==0,"Ninth command was accepted");
            for(int i=0;i<8;i++){long start=System.nanoTime();drain(h);double ms=(System.nanoTime()-start)/1e6;placement+=ms;maxPlacement=Math.max(maxPlacement,ms);}
            h.assertTrue(data.save(new CompoundTag(),h.getLevel().registryAccess()).getList("Jobs",10).isEmpty(),"Completed queue did not empty");
            System.out.printf(java.util.Locale.ROOT,"COMMAND_BLUEPRINT_PERF {\"jobs\":8,\"blocksPerJob\":2,\"admissionMeanMs\":%.6f,\"placementMeanMs\":%.6f,\"placementMaxMs\":%.6f}%n",admission/8,placement/8,maxPlacement);
        } finally { java.nio.file.Files.deleteIfExists(f.path); }
        h.succeed();
    }
    @GameTest(template="combat", batch="command_save", timeoutTicks=100)
    public static void QueuedSaveLoadRunsOnceAndInterruptedBodyIsRemoved(GameTestHelper h) throws Exception {
        var f=fixture(h,1,"save");
        try {
            var data=CommandBlueprintSpawns.get(h.getLevel().getServer());
            h.assertTrue(execute(source(h),command(f,false)+" ~ ~8 ~")==1,"Queued save fixture failed");
            var tag=data.save(new CompoundTag(),h.getLevel().registryAccess());
            // Use a standalone copy to simulate the saved job's reload, then remove the live queue's copy.
            var jobsField=CommandBlueprintSpawns.class.getDeclaredField("jobs");jobsField.setAccessible(true);((Map<?,?>)jobsField.get(data)).clear();
            var loaded=CommandBlueprintSpawns.load(tag,h.getLevel().registryAccess());loaded.advance(h.getLevel().getServer());
            var core=spawned(h,f);h.assertTrue(core!=null,"Queued save did not survive reload");
            loaded.advance(h.getLevel().getServer());h.assertTrue(loaded.save(new CompoundTag(),h.getLevel().registryAccess()).getList("Jobs",10).isEmpty(),"Completed saved command remained queued");
            var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),core.getBlockPos());
            var row=tag.getList("Jobs",10).getCompound(0);row.putBoolean("Cleanup",true);
            var bodies=new ListTag();bodies.add(NbtUtils.createUUID(body.getUniqueId()));row.put("Bodies",bodies);
            var interrupted=CommandBlueprintSpawns.load(tag,h.getLevel().registryAccess());interrupted.advance(h.getLevel().getServer());
            h.assertTrue(body.isRemoved()||SubLevelContainer.getContainer(h.getLevel()).getSubLevel(body.getUniqueId())==null,"Interrupted body remained");
            h.assertTrue(interrupted.save(new CompoundTag(),h.getLevel().registryAccess()).getList("Jobs",10).isEmpty(),"Interrupted job would replay");
        } finally { java.nio.file.Files.deleteIfExists(f.path); }
        h.succeed();
    }


    @GameTest(template = "combat", batch = "command_unloaded_rollback", timeoutTicks = 100)
    public static void InterruptedCommandPreservesUnloadedBodyAndOwnershipAcrossReload(GameTestHelper h) throws Exception {
        var f = fixture(h, 1, "structure blueprint unload");
        var level = h.getLevel();
        var container = SubLevelContainer.getContainer(level);
        var storage = level.getServer().overworld().getDataStorage();
        var sourceBody = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, f.core.getBlockPos());
        var sourcePlot = sourceBody.getPlot().plotPos;
        int sourceIndex = container.getIndex(sourcePlot.x - container.getOrigin().x, sourcePlot.z - container.getOrigin().y);
        try {
            h.assertTrue(execute(source(h), command(f, false) + " ~ ~8 ~") == 1, "Structure blueprint command failed");
            var queued = CommandBlueprintSpawns.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
            drain(h);
            var core = spawned(h, f);
            h.assertTrue(core != null, "Structure blueprint never placed");
            var body = (ServerSubLevel) dev.ryanhcode.sable.Sable.HELPER.getContaining(level, core.getBlockPos());
            var id = body.getUniqueId();
            var owner = core.creatureId();
            var plot = body.getPlot().plotPos;
            int index = container.getIndex(plot.x - container.getOrigin().x, plot.z - container.getOrigin().y);
            BodyOwnership.get(level).bind(core.getBlockPos(), owner);

            var holding = container.getHoldingChunkMap();
            var worldChunk = new ChunkPos(BlockPos.containing(SableBodyProbe.toWorld(level, core.getBlockPos().getCenter())));
            // The fixture's manually serialized body must remain resident until the actual next server tick.
            boolean forced = level.setChunkForced(worldChunk.x, worldChunk.z, true);
            level.getChunk(worldChunk.x, worldChunk.z);
            holding.moveToUnloaded(body, worldChunk);
            var diskBody = holding.getHoldingSubLevel(id);
            h.assertTrue(diskBody != null && container.getSubLevel(id) == null && container.getOccupancy().get(index),
                    "Native unload did not leave an occupied, reloadable plot");
            var row = queued.getList("Jobs", Tag.TAG_COMPOUND).getCompound(0);
            row.putBoolean("Cleanup", true);
            row.putUUID("Creature", owner);
            var bodies = new ListTag();
            bodies.add(NbtUtils.createUUID(id));
            row.put("Bodies", bodies);
            // Retain the released format with only a UUID, before the new optional placement hint.
            row.remove("Placement");
            storage.set("sablelife_command_blueprints", CommandBlueprintSpawns.load(queued, level.registryAccess()));
            drain(h);
            h.assertTrue(BlueprintCleanup.retainsOwnership(level.getServer(), owner) && BodyOwnership.get(level).hasOwned(owner),
                    "An unloaded plot discarded rollback or released its ownership");

            var pending = BlueprintCleanup.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
            storage.set("sablelife_blueprint_cleanup", BlueprintCleanup.load(pending, level.registryAccess()));
            h.assertTrue(BlueprintCleanup.retainsOwnership(level.getServer(), owner), "Rollback did not survive reload");
            // Drive Sable's ordinary holding-chunk load, including removal of its cached disk entry.
            holding.updateChunkStatus(worldChunk, true);
            holding.processChanges();
            h.assertTrue(container.getSubLevel(id) != null && holding.getHoldingSubLevel(id) == null,
                    "Sable did not reload the held body");
            h.succeedWhen(() -> {
                h.assertTrue(!container.getOccupancy().get(index) && container.getSubLevel(id) == null,
                        "Reloaded rollback target still exists (resident=" + (container.getSubLevel(id) != null)
                                + ", holding=" + (holding.getHoldingSubLevel(id) != null) + ")");
                h.assertTrue(!BlueprintCleanup.retainsOwnership(level.getServer(), owner) && !BodyOwnership.get(level).hasOwned(owner),
                        "Completed native removal did not retire ownership");
                h.assertTrue(container.getOccupancy().get(sourceIndex), "Rollback permanently removed the source structure");
                if (forced) level.setChunkForced(worldChunk.x, worldChunk.z, false);
            });
        } finally {
            java.nio.file.Files.deleteIfExists(f.path);
        }
    }

    @GameTest(template = "combat", batch = "command_reused_plot_rollback", timeoutTicks = 100)
    public static void InterruptedCommandNeverRemovesAnotherBodyInReusedPlot(GameTestHelper h) throws Exception {
        var f = fixture(h, 1, "structure blueprint plot reuse");
        var level = h.getLevel();
        var container = SubLevelContainer.getContainer(level);
        try {
            h.assertTrue(execute(source(h), command(f, false) + " ~ ~8 ~") == 1, "Structure blueprint command failed");
            var queued = CommandBlueprintSpawns.get(level.getServer()).save(new CompoundTag(), level.registryAccess());
            drain(h);
            var core = spawned(h, f);
            h.assertTrue(core != null, "Structure blueprint never placed");
            var body = (ServerSubLevel) dev.ryanhcode.sable.Sable.HELPER.getContaining(level, core.getBlockPos());
            var id = body.getUniqueId();
            var origin = body.getPlot().getCenterBlock();
            var plot = body.getPlot().plotPos;
            var owner = core.creatureId();
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            var replacementId = UUID.randomUUID();
            var replacement = container.allocateSubLevel(replacementId,
                    plot.x - container.getOrigin().x, plot.z - container.getOrigin().y, body.logicalPose());

            var row = queued.getList("Jobs", Tag.TAG_COMPOUND).getCompound(0);
            row.putBoolean("Cleanup", true);
            row.putUUID("Creature", owner);
            var bodies = new ListTag();
            bodies.add(NbtUtils.createUUID(id));
            row.put("Bodies", bodies);
            var placement = new CompoundTag();
            var plots = new CompoundTag();
            plots.putLong(id.toString(), origin.asLong());
            placement.put(BlueprintCleanup.PLOT_CENTERS, plots);
            row.put("Placement", placement);
            CommandBlueprintSpawns.load(queued, level.registryAccess()).advance(level.getServer());
            h.assertTrue(container.getSubLevel(replacementId) == replacement && !replacement.isRemoved(),
                    "Rollback removed the replacement occupying the original plot");
            h.assertTrue(!BlueprintCleanup.retainsOwnership(level.getServer(), owner), "Proven old body deletion did not retire safely");
            container.removeSubLevel(replacement, SubLevelRemovalReason.REMOVED);
        } finally {
            java.nio.file.Files.deleteIfExists(f.path);
        }
        h.succeed();
    }
}
