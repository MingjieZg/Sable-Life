package dev.sablelife.testing;
import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_freeze_tests") @PrefixGameTestTemplate(false)
public final class FreezeMultipleGameTests {
    @GameTest(template = "combat", batch = "freeze_multiple_origin_isolation", timeoutTicks = 35)
    public static void MultipleOriginsFreezeBothMobsWithOneWarningClock(GameTestHelper h) {
        var core = SlashGuardGameTests.core(h);
        var freeze = core.modules().add("freeze");
        freeze.edit("freezeTarget", 1, "");
        freeze.edit("releasePosition", 1, "");
        freeze.edit("castTargetRange", 5, "");
        freeze.edit("freezeRadius", .75, "");
        freeze.edit("freezeWarning", 5, "");
        freeze.edit("freezeDuration", 10, "");
        freeze.edit("interval", 400, "");
        var origin = TargetCastOrigin.searchOrigin(core, freeze);

        var first = EntityType.PIG.create(h.getLevel());
        var second = EntityType.PIG.create(h.getLevel());
        first.setPos(origin.add(3, 2, 0));
        second.setPos(origin.add(-3, 2, 0));
        first.setNoAi(true);
        first.setNoGravity(true);
        second.setNoAi(true);
        second.setNoGravity(true);
        Runnable cleanup = () -> {
            try {
                core.modules().clearRuntime();
            } finally {
                first.discard();
                second.discard();
            }
        };

        try {
            h.getLevel().addFreshEntity(first);
            h.getLevel().addFreshEntity(second);
            h.assertTrue(freeze.value("targetMultiple") == 0 && TargetCastOrigin.points(core, freeze).size() == 1,
                    "Single-target default changed");
            freeze.edit("targetMultiple", 1, "");
            int originCount = TargetCastOrigin.points(core, freeze).size();
            h.assertTrue(originCount == 2, "Expected two target origins, got " + originCount);
            h.assertTrue(VirtualModule.load(freeze.save()).value("targetMultiple") == 1
                            && ModulePreset.create(freeze.save()).value("targetMultiple") == 1,
                    "Multiple-target setting was lost when saved");
            core.tryActivate(TestActors.creative(h.getLevel()));
            core.modules().tick(core);
            h.assertTrue(!MobGrab.frozen(first) && !MobGrab.frozen(second), "Multiple casts bypassed warning");
            h.runAfterDelay(7, () -> {
                try {
                    h.assertTrue(MobGrab.frozen(first) && MobGrab.frozen(second),
                            "Both selected centers did not activate together");
                } catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
            });
            h.runAfterDelay(19, () -> {
                try {
                    h.assertTrue(!MobGrab.frozen(first) && !MobGrab.frozen(second),
                            "Multiple cast duplicated duration or ignored shared cooldown");
                } finally {
                    cleanup.run();
                }
                h.succeed();
            });
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }
    @GameTest(template = "combat", batch = "freeze_overlap_isolation")
    public static void OverlappingRangesCaptureEachVictimOnceAndMarkHidesCheckbox(GameTestHelper h) {
        var core = SlashGuardGameTests.core(h);
        var freeze = core.modules().add("freeze");
        freeze.edit("freezeTarget", 1, "");
        freeze.edit("releasePosition", 1, "");
        freeze.edit("targetMultiple", 1, "");
        freeze.edit("castTargetRange", 4, "");
        freeze.edit("freezeRadius", 1.25, "");
        var center = TargetCastOrigin.searchOrigin(core, freeze).add(0, 2, 0);
        var first = EntityType.PIG.create(h.getLevel());
        var second = EntityType.PIG.create(h.getLevel());
        first.setPos(center.add(-.5, 0, 0));
        second.setPos(center.add(.5, 0, 0));
        first.setNoAi(true);
        first.setNoGravity(true);
        second.setNoAi(true);
        second.setNoGravity(true);
        h.getLevel().addFreshEntity(first);
        h.getLevel().addFreshEntity(second);
        try {
            var volumes = ModuleFreeze.volumes(core, freeze);
            h.assertTrue(volumes.size() == 2, "Expected two target origins, got " + volumes.size());
            h.assertTrue(volumes.stream().allMatch(v ->
                    v.intersects(first.getBoundingBox()) && v.intersects(second.getBoundingBox())),
                    "Both freeze volumes must reach both pigs");
            int captured = ModuleFreeze.captureVolumes(h.getLevel(), core, freeze, volumes);
            h.assertTrue(captured == 2, "Overlapping casts should capture two victims once, got " + captured);
            h.assertTrue(SurfaceFieldSettings.visible(freeze, "targetMultiple"), "Target checkbox hidden");
            freeze.edit("releasePosition", 0, "");
            h.assertTrue(!SurfaceFieldSettings.visible(freeze, "targetMultiple") && freeze.value("targetMultiple") == 1,
                    "Mark mode exposed or reset the multiple target setting");
            h.succeed();
        } finally {
            MobGrab.release(h.getLevel().getServer(), new PlayerFreeze.Source(core.creatureId(), freeze.audioId()));
            first.discard();
            second.discard();
        }
    }
}
