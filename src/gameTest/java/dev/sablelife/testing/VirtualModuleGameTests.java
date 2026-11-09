package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.UUID;

@GameTestHolder("sablelife_tests")
@PrefixGameTestTemplate(false)
public final class VirtualModuleGameTests {
    @GameTest(template = "combat", batch = "contact_six_faces")
    public static void contactDetectsAllSixConfiguredFacesAndReloads(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("trigger_sound");
        module.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.DOWN)));
        module.edit("contactDistance", .5, "");
        var choices = ModuleFields.find("trigger_sound", "contactDirection").choices();
        for (int index = 0; index < choices.size(); index++) {
            module.edit("contactDirection", index, "");
            var face = Direction.byName(choices.get(index));
            var expected = dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(module.anchors().getFirst().body(heart).logicalPose().orientation()),
                    net.minecraft.world.phys.Vec3.atLowerCornerOf(face.getNormal()));
            h.assertTrue(ModuleEvents.contactDirection(heart, module).distanceToSqr(expected) < 1e-8, "Contact direction did not follow selected local face");
            var block = BlockPos.containing(ModuleEvents.contactStart(heart, module).add(expected.scale(.25)));
            h.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            h.assertTrue(ModuleEvents.contact(h.getLevel(), heart, module), "Contact missed external block on " + face);
            h.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            h.assertTrue(!ModuleEvents.contact(h.getLevel(), heart, module), "Contact stayed active after removing block on " + face);
            h.assertTrue(VirtualModule.load(module.save()).value("contactDirection") == index, "Contact direction lost on reload");
        }
        h.succeed();
    }
    @GameTest(template = "combat", batch = "virtual_audio", timeoutTicks = 100)
    public static void contactReentryHasIndependentCooldown(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("trigger_sound");
        module.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.DOWN)));
        module.edit("trigger",0,""); module.edit("triggerSound",0,""); module.edit("contactMode", 1, "");
        module.edit("contactSound", 0, "minecraft:block.note_block.bass"); module.edit("contactDistance", .5, "");
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel()));
        var ground = BlockPos.containing(ModuleEvents.contactStart(heart, module).add(0, -.25, 0));
        h.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState()); heart.modules().tick(heart);
        h.getLevel().setBlockAndUpdate(ground, Blocks.AIR.defaultBlockState()); heart.modules().tick(heart);
        h.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState()); heart.modules().tick(heart);
        h.assertTrue(heart.modules().events().emittedSounds() == 1, "Contact jitter bypassed cooldown");
        h.getLevel().setBlockAndUpdate(ground, Blocks.AIR.defaultBlockState()); heart.modules().tick(heart);
        h.runAfterDelay(12, () -> {
            var current = BlockPos.containing(ModuleEvents.contactStart(heart, module).add(0, -.25, 0));
            h.getLevel().setBlockAndUpdate(current, Blocks.STONE.defaultBlockState()); heart.modules().tick(heart);
            h.assertTrue(heart.modules().events().emittedSounds() == 2, "New contact after cooldown did not play");
            h.getLevel().setBlockAndUpdate(current, Blocks.AIR.defaultBlockState()); heart.modules().clearRuntime(); h.succeed();
        });
    }
    @GameTest(template = "combat", batch = "virtual_sensor")
    public static void ringSensorInsideBodySeesOutButExternalWallStillBlocks(GameTestHelper h) {
        var level = h.getLevel(); var origin = h.absolutePos(new BlockPos(4, 6, 4));
        var blocks = new java.util.ArrayList<BlockPos>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            var pos = origin.offset(x, y, z); blocks.add(pos); level.setBlockAndUpdate(pos, Blocks.IRON_BLOCK.defaultBlockState());
        }
        level.setBlockAndUpdate(origin.west(), SableLife.HEART.get().defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, blocks, new BoundingBox3i(origin.getX()-1, origin.getY()-1, origin.getZ()-1, origin.getX()+1, origin.getY()+1, origin.getZ()+1));
        var local = body.getPlot().getCenterBlock(); var heart = (HeartBlockEntity) level.getBlockEntity(local.west());
        var module = heart.modules().add("sensor"); var anchor = ModuleAnchor.create(heart, local, Direction.EAST);
        module.anchors(List.of(anchor)); module.edit("target", 1, ""); module.edit("radius", 6, "");
        var center = anchor.world(heart, false); var victim = cow(h, center.add(4, -.7, 0));
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(level)); heart.modules().tick(heart);
        h.assertTrue(level.getBestNeighborSignal(local) == 15, "Own surrounding body completely blinded ring sensor");
        var wall = BlockPos.containing(center.add(2, 0, 0)); level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        heart.modules().tick(heart); h.assertTrue(level.getBestNeighborSignal(local) == 0, "External wall became transparent");
        level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState()); victim.discard(); heart.modules().clearRuntime(); h.succeed();
    }
    @GameTest(template = "combat", batch = "virtual_sensor")
    public static void omnidirectionalSensorUsesSphericalDistanceAboveAndBelow(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("sensor"); var local = heart.getBlockPos().east();
        var anchor = ModuleAnchor.create(heart, local, Direction.EAST); module.anchors(List.of(anchor)); module.edit("target", 1, ""); module.edit("lineOfSight", 1, "");
        var victim = cow(h, anchor.world(heart, false).add(3, -6, 0)); heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel()));
        heart.modules().tick(heart); h.assertTrue(h.getLevel().getBestNeighborSignal(local) == 15, "Full range does not include target below marker");
        module.edit("radius", 4, ""); heart.modules().tick(heart);
        h.assertTrue(h.getLevel().getBestNeighborSignal(local) == 0, "Target outside spherical radius still detected");
        h.assertTrue(!ModuleEvents.withinShape(heart, module, new net.minecraft.world.phys.Vec3(3, 3, 3))
                && ModuleEvents.withinShape(heart, module, new net.minecraft.world.phys.Vec3(0, 3, 0)), "Full range is a cylinder instead of a sphere");
        victim.discard(); heart.modules().clearRuntime(); h.succeed();
    }
    @GameTest(template = "combat", batch = "virtual_audio", timeoutTicks = 140)
    public static void virtualAmbientRandomTimingStopsWhenDisabled(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("sound");
        module.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST)));
        module.edit("approach", 0, ""); module.edit("hurt", 0, ""); module.edit("ambient", 0, "minecraft:entity.zombie.ambient");
        module.edit("ambientMin", 20, ""); module.edit("ambientMax", 30, "");
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel())); heart.modules().tick(heart);
        h.assertTrue(heart.modules().events().emittedSounds() == 0, "Ambient played immediately instead of waiting");
        h.runAfterDelay(35, () -> {
            h.assertTrue(heart.modules().events().emittedSounds() == 1, "Ambient did not obey randomized 20-30 tick interval");
            module.edit("enabled", 0, ""); heart.modules().tick(heart);
        });
        h.runAfterDelay(75, () -> {
            h.assertTrue(heart.modules().events().emittedSounds() == 1, "Disabled ambient kept playing"); heart.modules().clearRuntime(); h.succeed();
        });
    }
    @GameTest(template = "combat", batch = "virtual_audio")
    public static void contactSoundOnlyPlaysOnNewGroundContact(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("trigger_sound"); var anchor = ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.DOWN);
        module.anchors(List.of(anchor)); module.edit("trigger",0,""); module.edit("triggerSound",0,"");
        module.edit("contactMode", 1, ""); module.edit("contactSound", 0, "minecraft:block.note_block.bass");
        module.edit("contactDistance", .5, "");
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel()));
        var start = ModuleEvents.contactStart(heart, module); var ground = BlockPos.containing(start.add(0, -.25, 0));
        h.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        h.assertTrue(ModuleEvents.contact(h.getLevel(), heart, module), "Marked foot did not detect adjacent terrain");
        heart.modules().tick(heart); heart.modules().tick(heart);
        h.assertTrue(heart.modules().events().emittedSounds() == 1, "Ground contact loops or fails to play");
        h.getLevel().setBlockAndUpdate(ground, Blocks.AIR.defaultBlockState()); heart.modules().tick(heart);
        h.assertTrue(!ModuleEvents.contact(h.getLevel(), heart, module), "Air triggered a footstep");
        module.edit("enabled", 0, ""); heart.modules().tick(heart);
        h.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState()); heart.modules().tick(heart);
        h.assertTrue(heart.modules().events().emittedSounds() == 1, "Disabled contact plays sound");
        h.getLevel().setBlockAndUpdate(ground, Blocks.AIR.defaultBlockState()); heart.modules().clearRuntime(); h.succeed();
    }
    @GameTest(template = "combat", batch="audit_virtualmodulegametests_extendedsoundsettingsreloadandoldmodulesstayquiet")
    public static void extendedSoundSettingsReloadAndOldModulesStayQuiet(GameTestHelper h) {
        var old = new VirtualModule("sound"); var tag = old.save(); tag.getCompound("Settings").remove("ambient");
        tag.getCompound("Settings").remove("contactMode"); tag.getCompound("Settings").remove("contactSound");
        var loaded = VirtualModule.load(tag);
        h.assertTrue(loaded.text("ambient").isEmpty() && loaded.value("contactMode") == 0, "Old module gained unsolicited sounds");
        loaded.edit("ambient", 0, "minecraft:entity.zombie.ambient"); loaded.edit("ambientMin", 300, "");
        loaded.edit("ambientMax", 100, "");
        loaded = VirtualModule.load(loaded.save());
        h.assertTrue(loaded.value("ambientMax") >= loaded.value("ambientMin") && loaded.text("ambient").equals("minecraft:entity.zombie.ambient"), "Extended sound settings lost or invalid interval");
        h.assertTrue(ModuleFields.find("sound","contactMode")==null&&!loaded.edit("contactMode",1,""),"Sounds still accepts contact controls");
        var contact=new VirtualModule("trigger_sound");contact.edit("contactMode",1,"");contact.edit("contactDistance",1.25,"");contact=VirtualModule.load(contact.save());h.assertTrue(contact.value("contactMode")==1&&contact.value("contactDistance")==1.25,"Trigger Sound contact settings did not reload");h.succeed();
    }
    @GameTest(template = "combat", batch = "virtual_sensor")
    public static void ringSensorSeesDiagonalWithSightEnabled(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("sensor");
        var local = heart.getBlockPos().east(); var anchor = ModuleAnchor.create(heart, local, Direction.EAST);
        module.anchors(List.of(anchor)); module.edit("target", 1, ""); module.edit("radius", 6, "");
        var victim = cow(h, anchor.world(heart, false).add(3, -.7, 3));
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel())); heart.modules().tick(heart);
        h.assertTrue(h.getLevel().getBestNeighborSignal(local) == 15, "Ring diagonal ray was blocked by its own marker");
        victim.discard(); heart.modules().clearRuntime(); h.succeed();
    }
    @GameTest(template = "combat", batch = "virtual_sensor")
    public static void virtualSensorChargesSelectedMarkFaceAndClearsOnDisable(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("sensor");
        var local = heart.getBlockPos().east(); var anchor = ModuleAnchor.create(heart, local, Direction.EAST);
        module.anchors(List.of(anchor)); module.edit("target", 1, ""); module.edit("lineOfSight", 1, ""); module.edit("radius", 3, "");
        var victim = cow(h, anchor.world(heart, false).add(2, -.7, 0));
        var lamp = local.east(); h.getLevel().setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel())); heart.modules().tick(heart);
        h.assertTrue(h.getLevel().getBestNeighborSignal(local)==15,"Mark did not receive charge");
        for (var face : Direction.values()) h.assertTrue(h.getLevel().getSignal(local.relative(face),face)==(face==anchor.face()?15:0),"Mark charge escaped selected face: "+face);
        h.assertTrue(h.getLevel().getDirectSignal(lamp,Direction.EAST)==0,"Sensor charge became strong relay power");
        module.edit("enabled", 0, ""); heart.modules().tick(heart);
        h.assertTrue(h.getLevel().getBestNeighborSignal(local) == 0 && h.getLevel().getSignal(local, Direction.EAST) == 0, "Disabled sensor retained signal");
        h.getLevel().setBlockAndUpdate(lamp, Blocks.AIR.defaultBlockState()); victim.discard(); heart.modules().clearRuntime(); h.succeed();
    }
    @GameTest(template = "combat", batch = "virtual_sensor")
    public static void virtualSensorRingFrontAndWallMatchDetection(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("sensor"); var local = heart.getBlockPos().east();
        var anchor = ModuleAnchor.create(heart, local, Direction.EAST); module.anchors(List.of(anchor));
        module.edit("target", 1, ""); module.edit("radius", 6, ""); module.edit("innerRadius", 2, "");
        h.assertTrue(!ModuleEvents.withinShape(heart, module, new net.minecraft.world.phys.Vec3(1, 0, 0))
                && ModuleEvents.withinShape(heart, module, new net.minecraft.world.phys.Vec3(3, 0, 3)), "Ring hollow center or diagonal detection broken");
        module.edit("shape", 1, ""); var forward = ModuleCombat.direction(heart, module);
        h.assertTrue(ModuleEvents.withinShape(heart, module, forward.scale(3))
                && !ModuleEvents.withinShape(heart, module, forward.scale(-3)), "Front detection includes rear");
        var center = anchor.world(heart, false); var victim = cow(h, center.add(forward.scale(3)).add(0, -.7, 0));
        var wall = BlockPos.containing(center.add(forward.scale(1.5)));
        h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel())); heart.modules().tick(heart);
        h.assertTrue(h.getLevel().getBestNeighborSignal(local) == 0, "Sensor sees through wall");
        module.edit("lineOfSight", 1, ""); heart.modules().tick(heart);
        h.assertTrue(h.getLevel().getBestNeighborSignal(local) == 15, "LOS off still blocks detection");
        h.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState()); victim.discard(); heart.modules().clearRuntime(); h.succeed();
    }
    @GameTest(template = "combat", batch = "virtual_sensor")
    public static void virtualSensorChargeStopsAfterDetachment(GameTestHelper h) {
        var level = h.getLevel(); var origin = h.absolutePos(new BlockPos(4, 6, 4));
        level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState()); level.setBlockAndUpdate(origin.above(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.east(), origin.above()),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY() + 1, origin.getZ()));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock()); var sensor = heart.modules().add("sensor");
        var local = heart.getBlockPos().east(); var anchor = ModuleAnchor.create(heart, local, Direction.EAST);
        sensor.anchors(List.of(anchor)); sensor.edit("target", 1, ""); sensor.edit("lineOfSight", 1, ""); sensor.edit("radius", 3, "");
        var victim = cow(h, anchor.world(heart, false).add(2, -.7, 0));
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(level)); heart.modules().tick(heart);
        h.assertTrue(level.getBestNeighborSignal(local)==15, "Sensor failed to charge Mark before detachment");
        SubLevelAssemblyHelper.assembleBlocks(level, local, List.of(local), new BoundingBox3i(local.getX(), local.getY(), local.getZ(), local.getX(), local.getY(), local.getZ()));
        heart.modules().tick(heart);
        h.assertTrue(sensor.detached() && level.getBestNeighborSignal(local) == 0, "Detached sensor retained output");
        victim.discard(); heart.modules().clearRuntime(); h.succeed();
    }
    @GameTest(template = "combat", batch="audit_virtualmodulegametests_virtualsoundhurtcooldownindependentanddeathstops")
    public static void virtualSoundHurtCooldownIndependentAndDeathStops(GameTestHelper h) {
        var heart = fixture(h); var local = heart.getBlockPos().east(); var anchor = ModuleAnchor.create(heart, local, Direction.EAST);
        var first = heart.modules().add("sound"); var second = heart.modules().add("sound");
        first.anchors(List.of(anchor)); second.anchors(List.of(anchor));
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel())); heart.modules().tick(heart);
        long before = heart.modules().events().emittedSounds();
        heart.damage(1); heart.damage(1);
        h.assertTrue(heart.modules().events().emittedSounds() == before + 2, "Sound cooldown shared between modules or damage loops sound");
        first.edit("enabled", 0, ""); second.edit("enabled", 0, ""); heart.damage(1);
        h.assertTrue(heart.modules().events().emittedSounds() == before + 2, "Disabled module still makes hurt sound");
        heart.damage(1000); heart.modules().tick(heart);
        h.assertTrue(heart.health().state() != dev.sablelife.heart.HeartState.ALIVE, "Heart did not enter death countdown"); h.succeed();
    }
    @GameTest(template = "combat", timeoutTicks = 100, batch="audit_virtualmodulegametests_virtualsoundapproachdoesnotloopandreentryplaysagain")
    public static void virtualSoundApproachDoesNotLoopAndReentryPlaysAgain(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("sound");
        var anchor = ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST);
        module.anchors(List.of(anchor)); module.edit("radius", 3, ""); module.edit("cooldown", 20, "");
        var player = FakePlayerFactory.get(h.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "SoundVisitor"));
        var point = anchor.world(heart, false); player.setPos(point); heart.tryActivate(player);
        h.getLevel().players().add(player);
        try {
            heart.modules().tick(heart); heart.modules().tick(heart);
            h.assertTrue(heart.modules().events().emittedSounds() == 1, "Approach sound missing or loops while staying nearby");
            player.setPos(point.add(20, 0, 0)); heart.modules().tick(heart);
            player.setPos(point); heart.modules().tick(heart);
            h.assertTrue(heart.modules().events().emittedSounds() == 1, "Rapid reentry bypassed cooldown");
        } finally { h.getLevel().players().remove(player); }
        h.runAfterDelay(25, () -> {
            var current = anchor.world(heart, false);
            player.setPos(current.add(20, 0, 0)); heart.modules().tick(heart);
            h.getLevel().players().add(player);
            try {
                player.setPos(current); heart.modules().tick(heart);
                h.assertTrue(heart.modules().events().emittedSounds() == 2, "Reentry after cooldown did not play");
            } finally { h.getLevel().players().remove(player); heart.modules().clearRuntime(); }
            h.succeed();
        });
    }
    @GameTest(template = "combat", batch="audit_virtualmodulegametests_dashboardremembersselectionacrossmarkingandreopen")
    public static void dashboardRemembersSelectionAcrossMarkingAndReopen(GameTestHelper h) {
        var heart = fixture(h); var player = FakePlayerFactory.getMinecraft(h.getLevel()); player.setGameMode(GameType.CREATIVE);
        player.setPos(SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter()));
        heart.modules().add("melee"); heart.modules().add("arrow"); var last = heart.modules().add("loot");
        var menu = new HeartDashboardMenu(1, player.getInventory(), heart);
        h.assertTrue(menu.action(player, heart.modules().revision(), last.id(), "select", "", 0, ""), "Selection failed");
        menu.action(player, heart.modules().revision(), last.id(), "mark", "", 0, "");
        MarkingSessions.select(player, heart.getBlockPos().east(), Direction.EAST);
        var reopened = new HeartDashboardMenu(2, player.getInventory(), heart);
        h.assertTrue(reopened.snapshot().getUUID("Selected").equals(last.id()) && !last.edit("trigger", 2, ""), "Reopen reset selection or Loot retained trigger"); h.succeed();
    }
    @GameTest(template = "combat", batch = "virtual_loot", timeoutTicks = 120)
    public static void virtualLimbLootCleansFragmentAndRewardsOnceAfterReload(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("loot"); var local = heart.getBlockPos().east();
        module.anchors(List.of(ModuleAnchor.create(heart, local, Direction.EAST)));
        var tag = new CompoundTag(); tag.put("Slot0", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 4).save(h.getLevel().registryAccess()));
        module.lootSamples(tag); heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel())); heart.modules().tick(heart);
        h.assertTrue(module.rewardOwner() == null, "Connected main body was claimed as loot");
        var fragment = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), local, List.of(local), new BoundingBox3i(local.getX(), local.getY(), local.getZ(), local.getX(), local.getY(), local.getZ()));
        var fragmentPos = fragment.getPlot().getCenterBlock(); var point = module.anchors().getFirst().world(heart, false);
        heart.modules().tick(heart);
        h.assertTrue(heart.creatureId().equals(module.rewardOwner()) && dev.sablelife.ownership.BodyOwnership.get(h.getLevel()).part(fragmentPos) != null, "Detached limb reward not registered");
        var lifecycle = dev.sablelife.ownership.LifeLifecycle.get(h.getLevel().getServer());
        h.getLevel().getServer().overworld().getDataStorage().set("sablelife_lifecycle", dev.sablelife.ownership.LifeLifecycle.load(lifecycle.save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess()));
        heart.modules().load(heart.modules().save()); heart.modules().tick(heart);
        h.runAfterDelay(68, () -> {
            h.assertTrue(h.getLevel().getBlockState(fragmentPos).isAir() && h.getLevel().getBlockEntity(heart.getBlockPos()) == heart, "Loot cleanup removed core or left fragment");
            var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(point, point).inflate(5));
            h.assertTrue(drops.stream().filter(e -> e.getItem().is(net.minecraft.world.item.Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum() == 4, "Saved limb reward missing or duplicated");
            drops.forEach(net.minecraft.world.entity.Entity::discard); h.succeed();
        });
    }
    @GameTest(template = "combat", timeoutTicks=30, batch="audit_virtualmodulegametests_virtualflameusesordinarymarkedblockandsingledamage")
    public static void virtualFlameUsesOrdinaryMarkedBlockAndSingleDamage(GameTestHelper h) {
        var level = h.getLevel(); var origin = h.absolutePos(new BlockPos(4, 6, 4));
        level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState()); level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.east()), new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY(), origin.getZ()));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock()); var m = heart.modules().add("flame");
        m.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST))); m.edit("trigger", 1, ""); m.edit("width", 2, ""); m.edit("burn", 0, ""); m.edit("damage", 3, "");
        var target = cow(h, ModuleCombat.nozzle(heart, m).add(2, -.7, 0));
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(level));
        HeartBlockEntity.serverTick(level, heart.getBlockPos(), heart.getBlockState(), heart);
       
        h.assertTrue(target.getHealth() == 20, "Unpowered flame self-fired");
        level.setBlockAndUpdate(heart.getBlockPos().east().above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        heart.modules().tick(heart);
        h.runAfterDelay(7,()->{h.assertTrue(target.getHealth() == 17, "Moving flame front did not apply one damage hit: "+target.getHealth());target.discard();h.succeed();});
    }
    @GameTest(template = "combat", batch="audit_virtualmodulegametests_virtualanchorcontrolignoresheartbeatandfiresonearrow")
    public static void virtualAnchorControlIgnoresHeartbeatAndFiresOneArrow(GameTestHelper h) {
        var level = h.getLevel(); var origin = h.absolutePos(new BlockPos(4, 6, 4));
        level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState()); level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.east()), new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY(), origin.getZ()));
        var heart = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock()); var m = heart.modules().add("arrow");
        m.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST))); m.edit("trigger", 1, "");
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(level)); HeartBlockEntity.serverTick(level, heart.getBlockPos(), heart.getBlockState(), heart);
       
        var point = ModuleCombat.nozzle(heart, m); var area = new net.minecraft.world.phys.AABB(point, point).inflate(1);
        h.assertTrue(level.getEntitiesOfClass(dev.sablelife.attack.ModuleArrow.class, area).isEmpty(), "Core heartbeat or physical emitter fired unpowered virtual module");
        level.setBlockAndUpdate(heart.getBlockPos().east().above(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        heart.modules().tick(heart);
        var shots = level.getEntitiesOfClass(dev.sablelife.attack.ModuleArrow.class, area);
        h.assertTrue(shots.size() == 1, "Powered virtual emitter did not produce one shot"); shots.forEach(net.minecraft.world.entity.Entity::discard); h.succeed();
    }
    @GameTest(template = "combat", batch="audit_virtualmodulegametests_virtualmeleeappliesindependentdamageeffectsandstopswhendisabled")
    public static void virtualMeleeAppliesIndependentDamageEffectsAndStopsWhenDisabled(GameTestHelper h) {
        var heart = fixture(h); var a = heart.modules().add("melee"); var b = heart.modules().add("melee");
        var anchor = ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST);
        for (var m : List.of(a, b)) { m.anchors(List.of(anchor, anchor)); m.edit("damage", 2, ""); m.edit("knockback", 0, ""); }
        a.editEffect("add", "minecraft:weakness", 0); a.editEffect("level", "minecraft:weakness", 3);
        var cow = cow(h, anchor.world(heart, false).add(0, -.5, 0)); cow.invulnerableTime = 100;
        heart.modules().tick(heart);
        h.assertTrue(cow.getHealth() == 20, "Dormant module attacked");
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel())); heart.modules().tick(heart);
        h.assertTrue(cow.getHealth() == 16 && cow.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)
                && cow.getEffect(net.minecraft.world.effect.MobEffects.WEAKNESS).getAmplifier() == 2, "Independent attacks/debuff bypass failed");
        heart.modules().tick(heart); h.assertTrue(cow.getHealth() == 16, "Same-tick call bypassed per-module cadence");
        a.edit("enabled", 0, ""); b.edit("enabled", 0, ""); heart.modules().clearRuntime(); heart.modules().tick(heart);
        h.assertTrue(cow.getHealth() == 16, "Disabled module attacked"); cow.discard(); h.succeed();
    }
    @GameTest(template = "combat", timeoutTicks=35, batch="audit_virtualmodulegametests_virtualflameobeyswallsandburnsunblockedtargets")
    public static void virtualFlameObeysWallsAndBurnsUnblockedTargets(GameTestHelper h) {
        var heart = fixture(h); var m = heart.modules().add("flame");
        m.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST)));
        m.edit("damage", 3, ""); m.edit("width", 2, "");
        var start = ModuleCombat.nozzle(heart, m); var direction = ModuleCombat.direction(heart, m);
        var target = cow(h, start.add(direction.scale(3)).add(0, -.7, 0));
        var wall = BlockPos.containing(start.add(direction.scale(1.5)));
        h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel())); heart.modules().tick(heart);
        h.runAfterDelay(7,()->{
            h.assertTrue(target.getHealth() == 20, "Flame passed through wall");
            h.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());heart.modules().clearRuntime();target.invulnerableTime=100;
            heart.modules().tick(heart);
            h.runAfterDelay(7,()->{
                h.assertTrue(target.getHealth()==17&&target.isOnFire(),"Open flame did not damage and ignite: "+target.getHealth());
                m.invalidate();heart.modules().clearRuntime();heart.modules().tick(heart);
                h.assertTrue(target.getHealth()==17,"Destroyed nozzle attacked");target.discard();h.succeed();
            });
        });
    }
    @GameTest(template = "combat", batch="audit_virtualmodulegametests_virtualpulsehonorswallandcadence")
    public static void virtualPulseHonorsWallAndCadence(GameTestHelper h) {
        var heart = fixture(h); var m = heart.modules().add("pulse");
        var anchor = ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST); m.anchors(List.of(anchor)); m.edit("knockback", 0, ""); m.edit("damage", 4, "");
        var center = anchor.world(heart, false); var target = cow(h, center.add(3, -.7, 0));
        var wall = BlockPos.containing(center.add(1.5, 0, 0)); h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel())); heart.modules().tick(heart);
        h.assertTrue(target.getHealth() == 20, "Pulse passed through wall");
        h.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState()); heart.modules().clearRuntime(); target.invulnerableTime = 100;
        heart.modules().tick(heart); heart.modules().tick(heart);
        h.assertTrue(target.getHealth() == 16, "Pulse failed bypass or ignored cadence");
        heart.damage(1000); heart.modules().clearRuntime(); heart.modules().tick(heart);
        h.assertTrue(target.getHealth() == 16, "Dying core kept pulsing"); target.discard(); h.succeed();
    }
    @GameTest(template = "combat", batch="audit_virtualmodulegametests_virtualarrowusesnativewirelessscatterandcannotbepickedup")
    public static void virtualArrowUsesNativeWirelessScatterAndCannotBePickedUp(GameTestHelper h) {
        var heart = fixture(h); var m = heart.modules().add("arrow");
        m.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST)));
        m.edit("arrows", 3, ""); m.edit("damage", 9, ""); m.edit("trigger", 2, ""); m.edit("channel", 16, "");
        var box = new net.minecraft.world.phys.AABB(ModuleCombat.nozzle(heart, m), ModuleCombat.nozzle(heart, m)).inflate(1);
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel())); heart.modules().tick(heart);
        h.assertTrue(h.getLevel().getEntitiesOfClass(dev.sablelife.attack.ModuleArrow.class, box).isEmpty(), "Unpowered wireless fired");
        while(heart.wireless().size()<16)heart.wireless().add();
        var channel = heart.wireless().channel(15); channel.frequency(false, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        channel.set(0, 1); channel.set(2, 100); channel.set(4, 15); heart.wireless().start(); heart.wireless().tick(h.getLevel());
        heart.modules().tick(heart); var shots = h.getLevel().getEntitiesOfClass(dev.sablelife.attack.ModuleArrow.class, box);
        h.assertTrue(shots.size() == 3 && shots.stream().allMatch(arrow -> arrow.getBaseDamage() == 9
                && arrow.pickup == net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED
                && arrow.getDeltaMovement().dot(ModuleCombat.direction(heart, m)) > 0), "Wireless scatter damage/direction/pickup failed");
        heart.damage(1000); heart.modules().clearRuntime(); heart.modules().tick(heart);
        h.assertTrue(h.getLevel().getEntitiesOfClass(dev.sablelife.attack.ModuleArrow.class, box).size() == 3, "Death kept firing"); shots.forEach(net.minecraft.world.entity.Entity::discard); h.succeed();
    }
    @GameTest(template = "combat", timeoutTicks = 30, batch="audit_virtualmodulegametests_virtualmeleesweepsmovinglimbandstopsaftersplit")
    public static void virtualMeleeSweepsMovingLimbAndStopsAfterSplit(GameTestHelper h) {
        var heart = fixture(h); var m = heart.modules().add("melee"); var anchor = ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST);
        m.anchors(List.of(anchor, anchor)); m.edit("damage", 3, ""); m.edit("knockback", 0, "");
        var body = (dev.ryanhcode.sable.sublevel.ServerSubLevel) anchor.body(heart); var before = ModuleCombat.meleeCenter(heart, m);
        var victim = cow(h, before.add(2, -.5, 0)); heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel()));
        var runtime = new ModuleCombat(); runtime.tick(heart);
        h.runAfterDelay(1, () -> {
            dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body).teleport(new org.joml.Vector3d(body.logicalPose().position()).add(4, 0, 0), new org.joml.Quaterniond(body.logicalPose().orientation()));
            runtime.tick(heart);
            h.assertTrue(victim.getHealth() == 17, "Moving marked volume skipped intermediate target");
            var local = anchor.position(body); SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), local, List.of(local), new BoundingBox3i(local.getX(), local.getY(), local.getZ(), local.getX(), local.getY(), local.getZ()));
            runtime.tick(heart);
            h.assertTrue(!HeartModules.triggered(m, heart) && m.detached() && VirtualModule.load(m.save()).detached(), "Detached marked limb stayed powered or lost latch"); victim.discard(); h.succeed();
        });
    }
    private static net.minecraft.world.entity.animal.Cow cow(GameTestHelper h, net.minecraft.world.phys.Vec3 feet) {
        var cow = net.minecraft.world.entity.EntityType.COW.create(h.getLevel());
        cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20); cow.setHealth(20);
        cow.setPos(feet); cow.setNoAi(true); cow.setNoGravity(true); h.getLevel().addFreshEntity(cow); return cow;
    }
    @GameTest(template = "combat", batch="audit_virtualmodulegametests_specializedsettingspersistandchannellocksoutsidewireless")
    public static void specializedSettingsPersistAndChannelLocksOutsideWireless(GameTestHelper h) {
        var flame = new VirtualModule("flame"); flame.edit("burn", 99, ""); flame.edit("width", 2.5, "");
        h.assertTrue(!flame.edit("speed", 3, "") && !flame.edit("channel", 16, ""), "Wrong type or non-wireless field accepted");
        flame.edit("trigger", 2, ""); flame.edit("channel", 16, ""); flame.edit("trigger", 1, "");
        h.assertTrue(!flame.edit("channel", 1, ""), "Anchor redstone allowed channel editing");
        var copy = VirtualModule.load(flame.save());
        h.assertTrue(copy.value("burn") == 30 && copy.value("width") == 2.5 && copy.value("channel") == 15,
                "Flame settings or inactive wireless selection lost");
        var arrow = new VirtualModule("arrow"); arrow.edit("arrows", 99, ""); arrow.edit("endRadius", 6.25, ""); arrow.edit("speed", 4, "");
        copy = VirtualModule.load(arrow.save());
        h.assertTrue(copy.value("arrows") == 9 && copy.value("endRadius") == 6.25 && copy.value("speed") == 4, "Scatter settings lost");
        var sensor = new VirtualModule("sensor"); sensor.edit("innerRadius", 20, ""); sensor.edit("radius", 4, ""); sensor.edit("shape", 1, "");
        copy = VirtualModule.load(sensor.save());
        h.assertTrue(copy.value("innerRadius") == 3 && copy.value("shape") == 1, "Detection ring bounds violated");
        var legacy = flame.save(); legacy.remove("Settings"); legacy.putDouble("Damage", 13); legacy.putDouble("Range", 8);
        copy = VirtualModule.load(legacy);
        h.assertTrue(copy.value("damage") == 13 && copy.value("range") == 8 && copy.value("burn") == 3, "Existing authoring settings migration failed");
        h.succeed();
    }
    @GameTest(template = "combat", batch="audit_virtualmodulegametests_virtuallootcopiesincrementspersistsandlockswhenactive")
    public static void virtualLootCopiesIncrementsPersistsAndLocksWhenActive(GameTestHelper h) {
        var heart = fixture(h); var player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.setGameMode(GameType.CREATIVE); player.setPos(SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter()));
        var module = heart.modules().add("loot"); var menu = new VirtualLootMenu(1, player.getInventory(), heart, module);
        var cursor = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 64);
        cursor.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Prize"));
        menu.setCarried(cursor);
        menu.clicked(0, 1, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.clicked(0, 1, net.minecraft.world.inventory.ClickType.PICKUP, player);
        h.assertTrue(menu.sample(0).getCount() == 2 && menu.getCarried().getCount() == 64, "Copy/increment consumed cursor or failed");
        menu.clicked(0, 1, net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
        h.assertTrue(menu.rule(0) == 95, "Chance adjustment failed");
        var restored = VirtualModule.load(module.save());
        var restoredSample = net.minecraft.world.item.ItemStack.parseOptional(h.getLevel().registryAccess(), restored.lootSamples().getCompound("Slot0"));
        h.assertTrue(restoredSample.getCount() == 2 && net.minecraft.world.item.ItemStack.isSameItemSameComponents(restoredSample.copyWithCount(64), cursor), "Template components/count not saved");
        heart.tryActivate(player); var before = module.save();
        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        menu.clicked(31, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        h.assertTrue(module.save().equals(before), "Active core templates edited");
        player.setGameMode(GameType.SURVIVAL);
        h.assertTrue(!menu.stillValid(player), "Survival could view virtual loot templates"); h.succeed();
    }
    @GameTest(template = "combat", batch="audit_virtualmodulegametests_repeatedmodulespersistindependentlyandkeepunknowndata")
    public static void repeatedModulesPersistIndependentlyAndKeepUnknownData(GameTestHelper h) {
        var modules = new HeartModules(); var a = modules.add("melee"); var b = modules.add("melee");
        a.edit("damage", 9999, ""); b.edit("damage", 17, ""); a.edit("knockback", 999, "");
        a.edit("trigger", 2, ""); a.edit("channel", 16, ""); a.edit("name", 0, "Left claw");
        var copy = new HeartModules(); copy.load(modules.save());
        h.assertTrue(copy.all().size() == 2 && !a.id().equals(b.id()) && copy.find(a.id()).value("damage") == 1000
                && copy.find(b.id()).value("damage") == 17 && copy.find(a.id()).value("knockback") == 100
                && copy.find(a.id()).value("channel") == 15 && copy.find(a.id()).name().equals("Left claw"), "Module identity/settings lost");
        h.assertTrue(!a.edit("damage", Double.NaN, "") && !a.edit("name", 0, "Bad\nName"), "Invalid data accepted");
        var unknown = a.save(); unknown.putString("Type", "future_skill"); unknown.putString("ExtensionData", "preserve me");
        var future = VirtualModule.load(unknown);
        h.assertTrue(!future.known() && !future.enabled() && future.save().equals(unknown), "Unknown module was erased or executed");
        unknown.putString("Type", "melee"); unknown.putInt("Version", 99); future = VirtualModule.load(unknown);
        h.assertTrue(!future.known() && !future.edit("damage", 10, "") && future.save().equals(unknown), "Future schema edited");
        for (int i = 2; i < HeartModules.LIMIT; i++) modules.add("flame");
        h.assertTrue(modules.add("flame") == null && modules.add("invalid") == null, "Module limit/type ignored"); h.succeed();
    }
    @GameTest(template = "combat", timeoutTicks = 100, batch="audit_virtualmodulegametests_virtualtriggerreadsexistingwirelesschannelandturnsoffwithheart")
    public static void virtualTriggerReadsExistingWirelessChannelAndTurnsOffWithHeart(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("flame");
        module.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST)));
        module.edit("trigger", 2, ""); module.edit("channel", 16, "");
        while(heart.wireless().size()<16)heart.wireless().add();
        var channel = heart.wireless().channel(15); channel.frequency(false, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD));
        channel.set(0, 1); channel.set(2, 100); channel.set(4, 11);
        h.assertTrue(!HeartModules.triggered(module, heart), "Dormant virtual trigger powered");
        heart.tryActivate(dev.sablelife.testing.TestActors.creative(h.getLevel()));
        h.runAfterDelay(3, () -> {
            h.assertTrue(heart.wireless().output(15) == 11 && HeartModules.triggered(module, heart), "Virtual module did not read original wireless channel 16");
            heart.damage(1000);
            h.assertTrue(!HeartModules.triggered(module, heart) && heart.wireless().output(15) == 0, "Dead core kept virtual trigger powered"); h.succeed();
        });
    }

    @GameTest(template = "combat", batch="audit_virtualmodulegametests_dashboardvalidatesrevisionspermissionsandpreserveswireless")
    public static void dashboardValidatesRevisionsPermissionsAndPreservesWireless(GameTestHelper h) {
        var heart = fixture(h); var level = h.getLevel(); var player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.CREATIVE); player.setPos(SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter()));
        var menu = new HeartDashboardMenu(1, player.getInventory(), heart); var none = new UUID(0, 0);
        h.assertTrue(menu.action(player, 0, none, "add", "melee", 0, ""), "Module add failed");
        h.assertTrue(!menu.action(player, 0, none, "add", "melee", 0, ""), "Stale revision overwrote data");
        var module = heart.modules().all().getFirst();
        h.assertTrue(menu.action(player, 1, module.id(), "edit", "name", 0, "Tail"), "Module rename failed");
        while(heart.wireless().size()<16)heart.wireless().add();
        var c = heart.wireless().channel(15); c.frequency(false, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)); c.set(0, 1); c.set(1, 80);
        var tag = heart.saveWithFullMetadata(level.registryAccess());
        var restored = (HeartBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(heart.getBlockPos(), heart.getBlockState(), tag, level.registryAccess());
        h.assertTrue(restored.modules().find(module.id()).name().equals("Tail") && restored.wireless().channel(15).value(1) == 80,
                "Unified core lost modules or existing wireless configuration");
        menu.clicked(0, 2, net.minecraft.world.inventory.ClickType.CLONE, player);
        h.assertTrue(menu.getCarried().isEmpty(), "Snapshot slot could be extracted");
        player.setGameMode(GameType.SURVIVAL);
        h.assertTrue(!menu.action(player, 2, module.id(), "remove", "", 0, ""), "Survival changed modules");
        player.setGameMode(GameType.CREATIVE); heart.tryActivate(player);
        h.assertTrue(!menu.action(player, 2, module.id(), "remove", "", 0, ""), "Active Heart edited modules");
        h.assertTrue(menu.action(player, 2, module.id(), "wireless", "", 0, ""), "Wireless navigation lost"); h.succeed();
    }

    @GameTest(template = "combat", batch="audit_virtualmodulegametests_anchorsuselimblocalcoordinatesandrejectterrain")
    public static void anchorsUseLimbLocalCoordinatesAndRejectTerrain(GameTestHelper h) {
        var heart = fixture(h); var local = heart.getBlockPos().east();
        var anchor = ModuleAnchor.create(heart, local, Direction.EAST);
        h.assertTrue(anchor != null && anchor.status(heart).equals("valid") && anchor.position(anchor.body(heart)).equals(local), "Local anchor invalid");
        h.assertTrue(ModuleAnchor.create(heart, h.absolutePos(new BlockPos(1, 1, 1)), Direction.UP) == null, "Terrain anchor accepted");
        var copy = ModuleAnchor.load(anchor.save()); h.assertTrue(copy.equals(anchor), "Anchor roundtrip failed");
        var module = heart.modules().add("flame"); module.anchors(List.of(anchor));
        heart.getLevel().setBlockAndUpdate(local, Blocks.AIR.defaultBlockState());
        h.assertTrue(anchor.status(heart).equals("destroyed") && module.invalidated(), "Destroyed anchor remained valid");
        heart.getLevel().setBlockAndUpdate(local, Blocks.IRON_BLOCK.defaultBlockState());
        h.assertTrue(HeartModules.status(module, heart).equals("destroyed"), "Replacement block silently restored destroyed anchor"); h.succeed();
    }
    @GameTest(template = "combat", batch="audit_virtualmodulegametests_markedblocktransferstoexactsplitbodyandcannotremainonparent")
    public static void markedBlockTransfersToExactSplitBodyAndCannotRemainOnParent(GameTestHelper h) {
        var heart = fixture(h); var local = heart.getBlockPos().east(); var module = heart.modules().add("flame");
        var original = ModuleAnchor.create(heart, local, Direction.EAST); module.anchors(List.of(original));
        var fragment = SubLevelAssemblyHelper.assembleBlocks(h.getLevel(), local, List.of(local),
                new BoundingBox3i(local.getX(), local.getY(), local.getZ(), local.getX(), local.getY(), local.getZ()));
        var moved = module.anchors().getFirst();
        h.assertTrue(!moved.bodyId().equals(original.bodyId()) && moved.bodyId().equals(fragment.getUniqueId())
                && moved.position(fragment).equals(fragment.getPlot().getCenterBlock())
                && HeartModules.status(module, heart).equals("disconnected") && !module.invalidated(),
                "Anchor split transfer guessed wrong body or treated move as destruction"); h.succeed();
    }

    @GameTest(template = "combat", timeoutTicks = 100, batch="audit_virtualmodulegametests_anchorfollowsstructurerotationandmovement")
    public static void anchorFollowsStructureRotationAndMovement(GameTestHelper h) {
        var heart = fixture(h); var anchor = ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST);
        var body = (dev.ryanhcode.sable.sublevel.ServerSubLevel) anchor.body(heart); var before = anchor.world(heart, true);
        dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body).teleport(new org.joml.Vector3d(body.logicalPose().position()).add(0, 3, 0), new org.joml.Quaterniond().rotationY(Math.PI / 2));
        h.runAfterDelay(3, () -> {
            var expected = body.logicalPose().transformPosition(anchor.position(body).getCenter().add(.55, 0, 0));
            h.assertTrue(anchor.world(heart, true).distanceToSqr(expected) < .000001
                    && anchor.world(heart, true).distanceToSqr(before) > 1, "Nozzle did not follow limb pose"); h.succeed();
        });
    }

    @GameTest(template = "combat", batch="audit_virtualmodulegametests_markingcancelpreservesoldselectionandrevisionconflictrejectscommit")
    public static void markingCancelPreservesOldSelectionAndRevisionConflictRejectsCommit(GameTestHelper h) {
        var heart = fixture(h); var player = FakePlayerFactory.getMinecraft(h.getLevel()); player.setGameMode(GameType.CREATIVE);
        var module = heart.modules().add("melee"); var anchor = ModuleAnchor.create(heart, heart.getBlockPos().east(), Direction.EAST);
        module.anchors(List.of(anchor, anchor));
        h.assertTrue(MarkingSessions.start(player, heart, module), "Marking did not start");
        h.assertTrue(MarkingSessions.select(player, heart.getBlockPos(), Direction.UP), "First point rejected");
        MarkingSessions.cancel(player);
        h.assertTrue(module.anchors().equals(List.of(anchor, anchor)), "Cancel replaced previous selection");
        MarkingSessions.start(player, heart, module); heart.modules().changed();
        h.assertTrue(!MarkingSessions.select(player, heart.getBlockPos(), Direction.UP), "Conflicting revision accepted"); MarkingSessions.cancel(player);
        MarkingSessions.start(player, heart, module);
        MarkingSessions.select(player, heart.getBlockPos(), Direction.UP);
        h.assertTrue(MarkingSessions.select(player, heart.getBlockPos().east(), Direction.EAST)
                && module.anchors().size() == 2 && !module.anchors().getFirst().equals(anchor), "Two-point atomic commit failed"); h.succeed();
    }
    private static HeartBlockEntity fixture(GameTestHelper h) {
        var level = h.getLevel(); var origin = h.absolutePos(new BlockPos(4, 6, 4));
        level.setBlockAndUpdate(origin, SableLife.HEART.get().defaultBlockState()); level.setBlockAndUpdate(origin.east(), Blocks.IRON_BLOCK.defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, origin, List.of(origin, origin.east()),
                new BoundingBox3i(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 1, origin.getY(), origin.getZ()));
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }
}



