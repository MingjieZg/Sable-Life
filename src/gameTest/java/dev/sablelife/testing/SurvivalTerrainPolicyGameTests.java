package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.attack.ModuleBlast;
import dev.sablelife.attack.ModuleShot;
import dev.sablelife.heart.SurvivalTokens;
import dev.sablelife.module.LaserBlockCut;
import dev.sablelife.module.LinearMovementGeometry;
import dev.sablelife.module.ModuleSphereBlast;
import dev.sablelife.module.VirtualModule;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sablelife_terrain_melee_tests")
@PrefixGameTestTemplate(false)
public final class SurvivalTerrainPolicyGameTests {
    private static ModConfigSpec.BooleanValue setting() {
        return (ModConfigSpec.BooleanValue) SableLifeServerConfig.SPEC.getValues()
                .getRaw("allowSurvivalHeartCoreTerrainBreaking");
    }

    @GameTest(template = "combat", batch = "survival_terrain_melee_switch")
    public static void MeleeStillDamagesAndOnlyPaysForThatHit(GameTestHelper h) {
        var option = setting();
        boolean original = option.get();
        var rig = TerrainMeleeGameTests.fixture(h, true);
        TerrainMeleeGameTests.on(rig);
        var core = rig.core();
        var wall = rig.target();
        var victim = pig(h, wall.getBottomCenter());
        TerrainMeleeGameTests.stone(h, wall);
        var cost = SurvivalTokens.meleeCost(rig.melee(), SableLifeServerConfig.meleeTokenRate());
        try {
            option.set(false);
            long before = core.survivalTokenBalance();
            rig.tick();
            h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE)
                    && victim.getHealth() == 7 && core.survivalTokenBalance() == before - cost,
                    "Disabled terrain breaking changed melee damage, energy or terrain");
            h.assertTrue(rig.melee().value("meleeBreakBlocks") == 1, "Server switch erased the module setting");

            option.set(true);
            rig.tick();
            h.assertTrue(h.getLevel().getBlockState(wall).isAir(), "Re-enabling terrain breaking did not resume melee");
            victim.discard();
        } finally {
            option.set(original);
        }
        TerrainMeleeGameTests.done(h, rig);
    }

    @GameTest(template = "combat", batch = "survival_terrain_laser_switch")
    public static void DisabledLaserStopsAtWallAndResumesAfterReenable(GameTestHelper h) {
        var option = setting();
        boolean original = option.get();
        var rig = TerrainMeleeGameTests.fixture(h, true);
        var level = h.getLevel();
        var start = rig.target().getCenter();
        var end = start.add(8, 0, 0);
        var wall = BlockPos.containing(start.add(3, 0, 0));
        TerrainMeleeGameTests.stone(h, wall);
        var laser = new VirtualModule("laser");
        var front = new LaserBlockCut.Front();
        try {
            option.set(false);
            var stopped = LaserBlockCut.cut(level, rig.core(), laser, start, end, front, level.getGameTime());
            h.assertTrue(level.getBlockState(wall).is(Blocks.STONE) && stopped.x < end.x,
                    "Disabled cutting destroyed or ignored a wall");

            option.set(true);
            LaserBlockCut.cut(level, rig.core(), laser, start, end, front, level.getGameTime());
            h.assertTrue(level.getBlockState(wall).isAir(), "Re-enabling terrain breaking did not resume cutting");
        } finally {
            option.set(original);
        }
        TerrainMeleeGameTests.done(h, rig);
    }

    @GameTest(template = "combat", batch = "survival_terrain_explosion_switch")
    public static void BlastsKeepDamageAndRespectTheSwitchAtImpact(GameTestHelper h) {
        var option = setting();
        boolean original = option.get();
        var rig = TerrainMeleeGameTests.fixture(h, true);
        var core = rig.core();
        var level = h.getLevel();
        var sphere = new VirtualModule("sphere_blast");
        sphere.edit("blastRadius", 8, "");
        sphere.edit("blastBlocks", 1, "");
        sphere.edit("damage", 3, "");
        var mortar = new VirtualModule("mortar");
        mortar.edit("explode", 1, "");
        mortar.edit("blastRadius", 8, "");
        mortar.edit("blastBlocks", 1, "");
        mortar.edit("blastDamage", 3, "");
        var ordinary = TerrainMeleeGameTests.fixture(h, false);
        ordinary.melee().edit("enabled", 0, "");

        try {
            option.set(true);
            var point = rig.origin().offset(10, 3, 0).getCenter();
            var shot = new ModuleShot(level, point, Vec3.ZERO, mortar, core);
            var saved = shot.saveWithoutId(new CompoundTag());
            var restored = new ModuleShot(SableLife.MODULE_SHOT.get(), level);
            restored.load(saved);

            option.set(false);
            var wall = BlockPos.containing(point);
            level.setBlockAndUpdate(wall, Blocks.GLASS.defaultBlockState());
            var victim = pig(h, point.add(0, 1, 0));
            ModuleBlast.explode(level, restored, point, saved.getCompound("Blast"));
            h.assertTrue(level.getBlockState(wall).is(Blocks.GLASS) && victim.getHealth() == 7,
                    "Restored Survival projectile ignored config or lost damage");
            victim.discard();

            victim = pig(h, point.add(0, 1, 0));
            ModuleSphereBlast.detonate(level, core, sphere, point);
            h.assertTrue(level.getBlockState(wall).is(Blocks.GLASS) && victim.getHealth() == 7,
                    "Survival sphere blast ignored config or lost damage");
            victim.discard();

            ModuleSphereBlast.detonate(level, ordinary.core(), sphere, point);
            h.assertTrue(level.getBlockState(wall).isAir(), "Ordinary Heart Core lost terrain damage");
        } finally {
            option.set(original);
        }
        TerrainMeleeGameTests.done(h, rig);
    }

    @GameTest(template = "combat", batch = "survival_terrain_field_switch")
    public static void SurfaceBlastRetainsItsSupportingTerrainAndDamagesTargets(GameTestHelper h) {
        var option = setting();
        boolean original = option.get();
        var rig = TerrainMeleeGameTests.fixture(h, true);
        var core = rig.core();
        var field = core.modules().add("surface_field");
        field.edit("fieldBlast", 1, "");
        field.edit("fieldBlastDelay", 0, "");
        field.edit("blastBlocks", 1, "");
        field.edit("blastDamage", 3, "");
        field.edit("damage", 0, "");
        var mortar = core.modules().add("mortar");
        mortar.impactField(field.id());
        var floor = rig.origin().offset(8, 1, 0);
        for (var pos : BlockPos.betweenClosed(floor.offset(-2, 0, -2), floor.offset(2, 0, 2))) {
            TerrainMeleeGameTests.stone(h, pos);
        }
        var point = floor.getCenter().add(0, 0.5, 0);
        var victim = pig(h, point);
        try {
            option.set(false);
            core.modules().surfaceFields.impact(core, mortar, point, new Vec3(0, 1, 0), Vec3.ZERO);
            core.modules().surfaceFields.tick(core);
            h.assertTrue(h.getLevel().getBlockState(floor).is(Blocks.STONE) && victim.getHealth() == 7,
                    "Disabled surface blast changed terrain or did not damage its target");
            victim.discard();
        } finally {
            option.set(original);
            core.modules().surfaceFields.clear();
        }
        TerrainMeleeGameTests.done(h, rig);
    }

    @GameTest(template = "combat", batch = "survival_terrain_imported_collision")
    public static void ImportedCollisionSettingsCannotEnableSurvivalCollisionBreaking(GameTestHelper h) {
        var rig = TerrainMeleeGameTests.fixture(h, true);
        var core = rig.core();
        core.terrainSettings().edit("crushEnabled", 1, "");
        var path = rig.origin().east(2);
        TerrainMeleeGameTests.stone(h, path);
        core.terrainBreaker().advance(core, LinearMovementGeometry.capture(core), new Vec3(1, 0, 0));
        h.assertTrue(h.getLevel().getBlockState(path).is(Blocks.STONE) && core.terrainSettings().enabled(),
                "Imported settings let Survival collision breaking clear terrain");
        TerrainMeleeGameTests.done(h, rig);
    }

    private static Pig pig(GameTestHelper h, Vec3 point) {
        var pig = EntityType.PIG.create(h.getLevel());
        pig.setPos(point.x, point.y, point.z);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        h.getLevel().addFreshEntity(pig);
        return pig;
    }
}
