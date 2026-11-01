package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;

@GameTestHolder("sablelife_marker_tests")
@PrefixGameTestTemplate(false)
public final class MarkerFaceGameTests {
    @GameTest(template="combat",batch="marker_clear",timeoutTicks=30)
    public static void SneakAirClearsOnlyCurrentMarksAndCancelPreservesOldSelection(GameTestHelper h) {
        var heart=fixture(h);var player=FakePlayerFactory.getMinecraft(h.getLevel());player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter()).add(0,8,0));player.setXRot(-90);
        var module=output(heart,Direction.UP,0);var other=output(heart,Direction.NORTH,1);
        h.assertTrue(!MarkingSessions.clearMarks(player),"Clear accepted without an active marking session");
        h.assertTrue(MarkingSessions.start(player,heart,module),"Failed to start re-marking");MarkingSessions.cancel(player);
        h.assertTrue(!module.anchors().isEmpty(),"Escape/cancel erased existing marks");
        h.assertTrue(MarkingSessions.start(player,heart,module)&&!MarkingSessions.clearMarks(player),"Non-sneak click erased marks");
        player.setShiftKeyDown(true);
        var obstruction=net.minecraft.core.BlockPos.containing(player.getEyePosition().add(0,2,0));h.getLevel().setBlockAndUpdate(obstruction,Blocks.IRON_BLOCK.defaultBlockState());
        h.assertTrue(!MarkingSessions.clearMarks(player),"Sneak click at a block erased marks");h.getLevel().removeBlock(obstruction,false);
        h.assertTrue(MarkingSessions.clearMarks(player)&&module.anchors().isEmpty()&&HeartModules.status(module,heart).equals("valid")&&!other.anchors().isEmpty(),"Air clear failed or affected another module; optional wireless output must remain schedulable");
        h.assertTrue(MarkingSessions.start(player,heart,module)&&MarkingSessions.select(player,heart.getBlockPos().east(3),Direction.EAST),"Cleared module could not be marked again");
        var arena=heart.modules().add("arena");arena.arenaCenter(player.position(),h.getLevel().dimension().location().toString());
        h.assertTrue(MarkingSessions.start(player,heart,arena)&&MarkingSessions.clearMarks(player)&&arena.arenaCenter()==null,"Arena center was not cleared");
        MarkingSessions.cancel(player);player.setShiftKeyDown(false);h.succeed();
    }
    @GameTest(template="combat", batch="attack_start_distance", timeoutTicks=30)
    public static void AttackStartDistancePersistsAndMovesOriginsWithMarkedFace(GameTestHelper h) {
        var heart = fixture(h);
        for (var type : List.of("melee", "flame", "arrow", "gun", "mortar", "homing", "laser", "quake", "pull", "pulse")) {
            var module = new VirtualModule(type);
            h.assertTrue(ModuleFields.find(type, "startDistance") != null && module.value("startDistance") == 0, "Missing compatible default for " + type);
            module.edit("startDistance", 3, "");
            module = VirtualModule.load(module.save());
            h.assertTrue(module.value("startDistance") == 3, "Offset lost after reload: " + type);
            for (var face : Direction.values()) {
                var anchor = ModuleAnchor.create(heart, heart.getBlockPos().east(3), face);
                module.anchors(List.of(anchor, anchor));
                var expected = ModuleCombat.direction(heart, module).scale(3);
                var raw = anchor.world(heart, false);
                h.assertTrue(ModuleCombat.origin(heart, module, false).subtract(raw).distanceTo(expected) < .00001, "Wrong face offset: " + type + face);
                if (type.equals("melee")) h.assertTrue(ModuleCombat.meleeCenter(heart, module).subtract(raw).distanceTo(expected) < .00001, "Melee contact volume stayed at Mark");
                if (type.equals("homing")) {
                    var shifted = ModuleHoming.formation(heart, module);
                    module.edit("startDistance", 0, ""); var original = ModuleHoming.formation(heart, module); module.edit("startDistance", 3, "");
                    h.assertTrue(shifted.getFirst().subtract(original.getFirst()).distanceTo(expected) < .00001, "Homing formation stayed at Mark");
                }
            }
        }
        h.assertTrue(ModuleFields.find("ground_strike", "strikeStart") != null && ModuleFields.find("ground_strike", "startDistance") == null, "Ground strike's established distance changed");
        // An oversized bullet near an adjacent wall collides; moving the origin past it clears the outlet.
        var gun = new VirtualModule("gun"); gun.edit("bulletHitbox", 2, "");
        var anchor = ModuleAnchor.create(heart, heart.getBlockPos().east(3), Direction.UP); gun.anchors(List.of(anchor));
        var origin = ModuleCombat.nozzle(heart, gun); var direction = ModuleCombat.direction(heart, gun);
        var wall = BlockPos.containing(origin.add(direction.scale(.6))); h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        var blocked = new dev.sablelife.attack.ModuleShot(h.getLevel(), origin, direction.scale(.8), gun, heart); blocked.tick();
        h.assertTrue(blocked.isRemoved(), "Collision fixture did not obstruct the original outlet");
        gun.edit("startDistance", 8, ""); var start = ModuleCombat.nozzle(heart, gun);
        var clear = new dev.sablelife.attack.ModuleShot(h.getLevel(), start, direction.scale(.8), gun, heart); clear.tick();
        h.assertTrue(!clear.isRemoved() && clear.position().distanceTo(start) > .5, "Offset bullet still collided at its outlet: origin=" + origin + " start=" + start + " final=" + clear.position() + " wall=" + wall);
        clear.discard(); h.getLevel().removeBlock(wall, false); h.succeed();
    }
    private static HeartBlockEntity fixture(GameTestHelper h) {
        var level = h.getLevel(); var core = h.absolutePos(new BlockPos(4, 6, 4));
        var center = core.east(3); var blocks = new ArrayList<BlockPos>(); blocks.add(core);
        level.setBlockAndUpdate(core, SableLife.HEART.get().defaultBlockState());
        blocks.add(core.east()); level.setBlockAndUpdate(core.east(), Blocks.IRON_BLOCK.defaultBlockState());
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            var p = center.offset(x, 0, z); blocks.add(p);
            level.setBlockAndUpdate(p, Blocks.REDSTONE_LAMP.defaultBlockState());
        }
        for (var p : List.of(center.above(), center.below())) { blocks.add(p); level.setBlockAndUpdate(p, Blocks.REDSTONE_LAMP.defaultBlockState()); }
        var body = SubLevelAssemblyHelper.assembleBlocks(level, core, blocks,
                new BoundingBox3i(core.getX(), core.getY()-1, core.getZ()-1, center.getX()+1, core.getY()+1, core.getZ()+1));
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static VirtualModule output(HeartBlockEntity heart, Direction face, int mode) {
        var module = heart.modules().add("wireless_redstone"); module.edit("trigger", 0, "");
        module.edit("redstoneFaces", mode, "");
        module.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos().east(3), face)));
        return module;
    }
    private static boolean lit(GameTestHelper h, BlockPos p) { return h.getLevel().getBlockState(p).getValue(RedstoneLampBlock.LIT); }
    @GameTest(template="combat", batch="marker_six", timeoutTicks=80)
    public static void SixFacesLightsOnlySixAdjacentLampsWithoutDiagonalRelay(GameTestHelper h) {
        var heart = fixture(h); var module = output(heart, Direction.UP, 0);
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel())); heart.modules().tick(heart);
        var center = heart.getBlockPos().east(3);
        for (var face : Direction.values()) {
            h.assertTrue(h.getLevel().getBlockState(center.relative(face)).is(Blocks.REDSTONE_LAMP), "Lamp missing on " + face + " center=" + center + " state=" + h.getLevel().getBlockState(center.relative(face)));
            h.assertTrue(lit(h, center.relative(face)), "Six-face output missed " + face);
            h.assertTrue(h.getLevel().getBlockState(center).getDirectSignal(h.getLevel(), center, face)==0, "Marker still emits strong relay signal");
        }
        for (int x : new int[]{-1,1}) for (int z : new int[]{-1,1})
            h.assertTrue(!lit(h, center.offset(x,0,z)), "Six-face output leaked to diagonal lamp");
        h.assertTrue(!lit(h, center), "Marker became an offset/self-powered block");
        module.edit("redstoneFaces", 1, ""); heart.modules().tick(heart);
        h.runAfterDelay(6, () -> {
            for (var face : Direction.values()) h.assertTrue(!lit(h, center.relative(face)), "Selected surface lit adjacent lamp " + face);
            h.assertTrue(lit(h, center), "Selected surface did not power the marked source lamp");
            heart.modules().remove(module.id()); heart.modules().tick(heart); h.succeed();
        });
    }
    @GameTest(template="combat", batch="marker_surface", timeoutTicks=80)
    public static void SurfaceModePreservesSourceAndExactlyOneFaceForAllSixNormals(GameTestHelper h) {
        var heart = fixture(h); var module = output(heart, Direction.UP, 1);
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel())); var center = heart.getBlockPos().east(3);
        for (var selected : Direction.values()) {
            module.anchors(List.of(ModuleAnchor.create(heart, center, selected))); heart.modules().tick(heart);
            var anchor = module.anchors().getFirst();
            h.assertTrue(anchor.position(anchor.body(heart)).equals(center), "Face marking shifted source block");
            for (var outward : Direction.values()) {
                int power = h.getLevel().getSignal(center.relative(outward), outward);
                h.assertTrue(power == (outward == selected ? 15 : 0), "Surface output leaked: " + selected + " -> " + outward);
            }
        }
        heart.wireless().master(false); heart.modules().tick(heart);
        for (var face : Direction.values()) h.assertTrue(h.getLevel().getSignal(center.relative(face),face)==0, "Master off retained marker signal");
        h.succeed();
    }
}
