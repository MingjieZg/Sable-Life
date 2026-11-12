package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("sablelife_quake_tests")
@PrefixGameTestTemplate(false)
public final class QuakeGameTests {
    @GameTest(template="combat", batch="quake_life_ground", timeoutTicks=60)
    public static void GroundedLifeTakesRingQuakeDamage(GameTestHelper h) {
        var heart=fixture(h);var level=h.getLevel();var center=origin(heart);
        var p=BlockPos.containing(center.add(3,0,0));
        level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(p.above(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.above()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY()+1,p.getZ()));
        var other=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
        var author=TestActors.creative(level);other.tryActivate(author);
        var m=quake(heart);m.edit("target",3,"");m.edit("quakePush",0,"");m.edit("quakeLaunch",0,"");
        h.runAfterDelay(14,()->{
            var target=ModuleTargets.resolve(level,other.creatureId(),heart.creatureId());
            h.assertTrue(other.health().current()==94,"Grounded life was filtered before damage: HP="+other.health().current()+" pos="+target.position()+" velocity="+target.getDeltaMovement());
            h.assertTrue(heart.health().current()==100,"Quake hit its owner");
            m.edit("enabled",0,"");heart.modules().tick(heart);h.succeed();
        });
    }
    @GameTest(template="combat", batch="quake_life_air", timeoutTicks=30)
    public static void AirborneLifeObeysGroundedReachAndLifeDamageGate(GameTestHelper h) {
        var heart=fixture(h);var level=h.getLevel();var p=BlockPos.containing(origin(heart).add(3,1,0));
        level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));
        var other=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());var author=TestActors.creative(level);other.tryActivate(author);
        var m=quake(heart);m.edit("target",3,"");m.edit("quakeMode",2,"");m.edit("warningTime",1,"");m.edit("quakePush",0,"");m.edit("quakeLaunch",0,"");
        var engine=new ModuleQuake();engine.tick(heart);
        h.runAfterDelay(1,()->{
            engine.tick(heart);h.assertTrue(engine.phase(m.id()).equals("wave"),"Test never reached damage phase");engine.clear();
            h.assertTrue(other.health().current()==100,"Grounded-only quake hit airborne life");
            m.edit("quakeGrounded",1,"");m.edit("quakeReach",.25,"");engine.tick(heart);
        });
        h.runAfterDelay(2,()->{
            engine.tick(heart);engine.clear();h.assertTrue(other.health().current()==100,"Air-enabled quake ignored vertical reach");
            m.edit("quakeReach",2,"");other.editCore(author,"lifeAttacks",0,"");engine.tick(heart);
        });
        h.runAfterDelay(3,()->{
            engine.tick(heart);engine.clear();h.assertTrue(other.health().current()==100,"Quake bypassed life attack immunity");
            other.editCore(author,"lifeAttacks",1,"");m.edit("quakeOrigin",1,"");engine.tick(heart);
        });
        h.runAfterDelay(4,()->{
            engine.tick(heart);engine.clear();h.assertTrue(other.health().current()==94,"Target-origin quake failed airborne life with ground check disabled: "+other.health().current());
            h.assertTrue(level.getEntity(other.creatureId())==null,"Life target adapter spawned into world");m.edit("enabled",0,"");h.succeed();
        });
    }
    @GameTest(template="combat", batch="quake_light", timeoutTicks=30)
    public static void SmallLiftUsesSurfaceLightingAndNeverSavesTerrain(GameTestHelper h) {
        var level = h.getLevel(); var base = h.absolutePos(new BlockPos(4, 3, 4));
        level.setBlockAndUpdate(base, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(base.above(), Blocks.AIR.defaultBlockState());
        h.runAfterDelay(5, () -> {
            for (double rise : new double[]{.1, .5, .9, 1.5}) {
                var display = new QuakeGroundBlock(level, base, Blocks.STONE.defaultBlockState(), rise);
                var saved = display.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                var light = saved.getCompound("brightness");
                h.assertTrue(saved.contains("brightness"), "No surface light override");
                h.assertTrue(light.getInt("sky") == level.getBrightness(net.minecraft.world.level.LightLayer.SKY, base.above()), "Sky light sampled inside floor");
                h.assertTrue(light.getInt("block") == level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, base.above()), "Block light differs from surface");
                for (int i = 1; i <= 7; i++) { display.tickCount = i; display.tick(); }
                h.assertTrue(Math.abs(display.getY() - base.getY() - .01 - rise) < .0001, "Small lift changed height");
                h.assertTrue(!display.shouldBeSaved() && level.getBlockState(base).is(Blocks.STONE), "Display changed terrain or became persistent");
                display.discard();
            }
            h.succeed();
        });
    }
    @GameTest(template="combat", batch="random_strike", timeoutTicks=40)
    public static void RandomGroundStrikesStayInsideAnnulusAndPersistMode(GameTestHelper h) {
        var heart = fixture(h); var module = heart.modules().add("ground_strike");
        module.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.NORTH)));
        module.edit("strikeMode", 3, ""); module.edit("strikeStart", 2, ""); module.edit("strikeRange", 5, "");
        module.edit("strikeRandomCount", 12, "");
        var center = module.anchors().getFirst().world(heart, false);
        var points = ModuleGroundStrike.pattern(h.getLevel(), heart, module);
        h.assertTrue(points.size() == 12, "Random cast failed to fill twelve ground points");
        var unique = new java.util.HashSet<BlockPos>();
        for (var point : points) {
            double distance = ModuleArena.horizontalDistance(point, center);
            h.assertTrue(distance >= 2 - .00001 && distance <= 5 + .00001, "Point outside annulus");
            h.assertTrue(unique.add(BlockPos.containing(point)), "Duplicate ground column");
        }
        var loaded = VirtualModule.load(module.save());
        h.assertTrue(loaded.value("strikeMode") == 3 && loaded.value("strikeRandomCount") == 12, "Random settings lost after reload");
        module.edit("strikeStart", 6, "");
        h.assertTrue(ModuleGroundStrike.pattern(h.getLevel(), heart, module).isEmpty(), "Invalid annulus generated strikes");
        h.succeed();
    }
    @GameTest(template="combat", batch="laser_tracking", timeoutTicks=25)
    public static void LaserFiresFromLastWarningPointThenPursuesByDistance(GameTestHelper h) {
        var heart = fixture(h); var center = origin(heart);
        var target = pig(h, center.add(0, .5, 3), true);
        var m = heart.modules().add("laser"); m.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.NORTH)));
        m.edit("laserAim", 1, ""); m.edit("target", 1, ""); m.edit("laserTracking", .1, "");
        m.edit("laserFollow", .1, ""); m.edit("chargeTime", 5, "");
        heart.tryActivate(TestActors.creative(h.getLevel()));
        var laser = new ModuleLaser(); laser.tick(heart); var advertised = laser.aimPoint(m.id());
        h.runAfterDelay(2, () -> { target.setPos(target.position().add(4, 0, 0)); laser.tick(heart); });
        h.runAfterDelay(5, () -> {
            laser.tick(heart);
            h.assertTrue(laser.phase(m.id()).equals("firing"), "Laser did not fire after warning");
            h.assertTrue(laser.aimPoint(m.id()).distanceTo(advertised) < .00001, "Firing snapped away from final warning point");
        });
        h.runAfterDelay(6, () -> {
            laser.tick(heart);
            h.assertTrue(Math.abs(laser.aimPoint(m.id()).distanceTo(advertised) - .1) < .00001, "Firing aim did not obey blocks/tick cap");
            var reached = ModuleLaser.pursue(advertised, advertised.add(.05, 0, 0), .1);
            h.assertTrue(reached.equals(advertised.add(.05, 0, 0)), "Pursuit overshot stationary target");
            h.assertTrue(VirtualModule.load(m.save()).value("laserFollow") == .1, "Firing speed lost after save");
            laser.clear(); h.succeed();
        });
    }
    @GameTest(template="combat", batch="laser_target_lost_during_fire", timeoutTicks=30)
    public static void LaserFinishesBurstInLastDirectionAfterKillingTarget(GameTestHelper h) {
        var heart = fixture(h);
        var module = heart.modules().add("laser");
        module.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.NORTH)));
        module.edit("laserAim", 1, "");
        module.edit("target", 1, "");
        module.edit("chargeTime", 0, "");
        module.edit("beamLength", 8, "");
        module.edit("beamHits", 3, "");
        module.edit("hitInterval", 4, "");
        heart.tryActivate(TestActors.creative(h.getLevel()));
        h.assertTrue(laserFrames(h, heart, module).isEmpty() && laserPhase(heart).equals("waiting"),
                "Tracking laser fired with no initial target");

        var muzzle = ModuleCombat.nozzle(heart, module);
        var target = pig(h, muzzle.add(0, -.45, 3), true);
        target.setHealth(1);
        var initial = laserFrames(h, heart, module);
        h.assertTrue(initial.size() == 1 && !target.isAlive(), "Tracking laser did not kill its first target");
        var heading = initial.getFirst().ends().getFirst().point().subtract(initial.getFirst().start()).normalize();
        h.assertTrue(heading.z > .9, "First beam did not point at the target");
        var crossing = pig(h, muzzle.add(heading.scale(5)).add(0, -.45, 0), true);
        var offAxis = pig(h, muzzle.add(4, -.45, 0), true);
        h.runAfterDelay(1, () -> {
            var frames = laserFrames(h, heart, module);
            h.assertTrue(frames.size() == 1, "Beam disappeared when its first target died");
            var frame = frames.getFirst();
            h.assertTrue(frame.ends().getFirst().point().subtract(frame.start()).normalize().distanceTo(heading) < 1e-5,
                    "Beam snapped away from its final aim direction");
        });
        h.runAfterDelay(4, () -> {
            laserFrames(h, heart, module);
            h.assertTrue(crossing.getHealth() < 10 && offAxis.getHealth() == 10,
                    "The held beam lost its damage path or switched to an off-axis target");
        });
        h.runAfterDelay(11, () -> h.assertTrue(laserFrames(h, heart, module).size() == 1,
                "The beam did not reach the final tick of its configured duration"));
        h.runAfterDelay(12, () -> {
            h.assertTrue(laserFrames(h, heart, module).isEmpty() && laserPhase(heart).equals("waiting"),
                    "The beam continued past the configured duration");
            h.succeed();
        });
    }

    @GameTest(template="combat", batch="laser_target_lost_during_charge", timeoutTicks=20)
    public static void LosingTargetBeforeFirstEmissionDoesNotCreateHeldBeam(GameTestHelper h) {
        var heart = fixture(h);
        var module = heart.modules().add("laser");
        module.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.NORTH)));
        module.edit("laserAim", 1, "");
        module.edit("target", 1, "");
        module.edit("beamLength", 8, "");
        module.edit("chargeTime", 4, "");
        heart.tryActivate(TestActors.creative(h.getLevel()));
        var muzzle = ModuleCombat.nozzle(heart, module);
        var target = pig(h, muzzle.add(0, -.45, 3), true);
        h.assertTrue(laserFrames(h, heart, module).isEmpty() && laserPhase(heart).equals("charging"),
                "Fixture never charged or emitted a beam while charging");
        target.discard();
        var replacement = pig(h, muzzle.add(0, -.45, 5), true);
        h.runAfterDelay(5, () -> {
            h.assertTrue(laserFrames(h, heart, module).isEmpty() && replacement.getHealth() == 10,
                    "A warning direction became a damaging beam without its target");
            h.succeed();
        });
    }

    private static String laserPhase(HeartBlockEntity heart) {
        return heart.modules().snapshot(heart, false).getList("Modules", 10).getCompound(0).getString("LaserPhase");
    }

    private static List<LaserVisual> laserFrames(GameTestHelper h, HeartBlockEntity heart, VirtualModule module) {
        var level = h.getLevel();
        var observer = TestActors.creative(level);
        observer.setGameMode(GameType.SPECTATOR);
        observer.setPos(ModuleCombat.nozzle(heart, module));
        var frames = new java.util.ArrayList<LaserVisual>();
        observer.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                observer, CommonListenerCookie.createInitial(observer.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket payload && payload.payload() instanceof LaserVisual visual
                        && visual.id().equals(module.id()) && !visual.ends().isEmpty()) frames.add(visual);
            }
        };
        level.players().add(observer);
        try {
            heart.modules().tick(heart);
        } finally {
            level.players().remove(observer);
        }
        return frames;
    }
    @GameTest(template="combat", batch="quake_area", timeoutTicks=40)
    public static void WholeAreaRisesAtOnceAroundFrozenTargetFeet(GameTestHelper h) {
        var heart = fixture(h); var center = origin(heart);
        var near = pig(h, center.add(0, 0, 2), false);
        var far = pig(h, center.add(4, 0, 2), false);
        var outside = pig(h, center.add(-7, 0, 2), false);
        var m = quake(heart); m.edit("quakeMode", 2, ""); m.edit("quakeOrigin", 1, "");
        m.edit("quakeRange", 5, ""); m.edit("quakeSpeed", .1, "");
        h.runAfterDelay(5, () -> h.assertTrue(near.getHealth() == 10 && far.getHealth() == 10, "Area hit before warning finished"));
        h.runAfterDelay(10, () -> {
            h.assertTrue(near.getHealth() == 4 && far.getHealth() == 4, "Whole area failed to strike near and far targets simultaneously: HP="+near.getHealth()+"/"+far.getHealth()+" state="+heart.health().state());
            h.assertTrue(outside.getHealth() == 10, "Area crossed advertised radius");
            h.assertTrue(VirtualModule.load(m.save()).value("quakeMode") == 2, "Area mode lost after save");
            h.succeed();
        });
    }
    @GameTest(template="combat", batch="laser_multi", timeoutTicks=30)
    public static void MultiLaserSelectsDistinctBeamsWithoutStackingCrossedHits(GameTestHelper h) {
        var heart = fixture(h); var center = origin(heart);
        var first = pig(h, center.add(0, .5, 3), true);
        var second = pig(h, center.add(0, .5, 3), true);
        var m = heart.modules().add("laser"); m.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.NORTH)));
        m.edit("laserAim", 1, ""); m.edit("target", 1, ""); m.edit("beamLength", 8, ""); m.edit("chargeTime", 0, ""); m.edit("damage", 2, "");
        var start = m.anchors().getFirst().world(heart, false);
        h.assertTrue(ModuleLaser.acquireTargets(h.getLevel(), heart, m, start).size() == 1, "Default laser no longer single target");
        m.edit("targetMultiple", 1, "");
        h.assertTrue(ModuleLaser.acquireTargets(h.getLevel(), heart, m, start).size() == 2, "Multi laser target count=" + ModuleLaser.acquireTargets(h.getLevel(), heart, m, start).size());
        heart.tryActivate(TestActors.creative(h.getLevel()));
        var laser = new ModuleLaser(); laser.tick(heart);
        h.assertTrue(laser.beamCount(m.id()) == 2, "Multi laser did not create two beams");
        h.assertTrue(first.getHealth() == 8 && second.getHealth() == 8, "Crossing beams stacked damage or missed a target");
        h.assertTrue(VirtualModule.load(m.save()).value("targetMultiple") == 1, "Multi laser setting lost on reload");
        laser.clear(); m.edit("laserAim", 0, ""); laser.tick(heart);
        h.assertTrue(laser.beamCount(m.id()) == 1, "Fixed laser was multiplied"); laser.clear(); h.succeed();
    }
    @GameTest(template="combat", batch="quake_detection", timeoutTicks=35)
    public static void QuakeDetectsDistantTargetWithSmallIndependentAttackRadius(GameTestHelper h) {
        var heart = fixture(h); var center = origin(heart);
        var target = pig(h, center.add(0, 0, 6), false);
        var nearby = pig(h, center.add(3, 0, 6), false);
        var m = quake(heart); m.edit("quakeMode", 2, ""); m.edit("quakeOrigin", 1, "");
        m.edit("quakeRange", 2, ""); m.edit("quakeDetectRange", 12, "");
        h.assertTrue(ModuleQuake.detectTargets(h.getLevel(), heart, m, center).size() == 2, "Attack radius restricted detection");
        h.runAfterDelay(10, () -> {
            h.assertTrue(target.getHealth() == 4, "Distant target did not become quake origin");
            h.assertTrue(nearby.getHealth() == 10, "Detection radius also became damage radius");
            var loaded = VirtualModule.load(m.save());
            h.assertTrue(loaded.value("quakeRange") == 2 && loaded.value("quakeDetectRange") == 12, "Independent radii lost on reload");
            h.succeed();
        });
    }
    @GameTest(template="combat", batch="multi_target", timeoutTicks=40)
    public static void MortarAndGroundStrikeSelectEachTargetWithoutChangingSingleMode(GameTestHelper h) {
        var heart = fixture(h); var center = origin(heart);
        var first = pig(h, center.add(0, 0, 3), true);
        var second = pig(h, center.add(4, 0, 3), true);
        var outside = pig(h, center.add(12, 0, 0), true);
        var anchor = ModuleAnchor.create(heart, heart.getBlockPos(), Direction.NORTH);
        var strike = heart.modules().add("ground_strike"); strike.anchors(List.of(anchor));
        strike.edit("target", 1, ""); strike.edit("strikeMode", 2, ""); strike.edit("strikeRange", 8, "");
        h.assertTrue(ModuleGroundStrike.pattern(h.getLevel(), heart, strike).size() == 1, "Default target strike no longer single");
        strike.edit("targetMultiple", 1, "");
        var points = ModuleGroundStrike.pattern(h.getLevel(), heart, strike);
        h.assertTrue(points.size() == 2, "Multi strike failed to select both targets or included out-of-range target");
        h.assertTrue(points.stream().anyMatch(p -> ModuleArena.horizontalDistance(p, first.position()) < .01)
                && points.stream().anyMatch(p -> ModuleArena.horizontalDistance(p, second.position()) < .01), "Warning points do not match target feet");
        var mortar = heart.modules().add("mortar"); mortar.anchors(List.of(anchor)); mortar.edit("target", 1, "");
        mortar.edit("aimMode", 1, ""); mortar.edit("targetRange", 8, "");
        h.assertTrue(dev.sablelife.attack.ShotTrajectory.firingVelocities(h.getLevel(), heart, mortar).size() == 1, "Default mortar no longer single");
        mortar.edit("targetMultiple", 1, "");
        var shots = dev.sablelife.attack.ShotTrajectory.firingVelocities(h.getLevel(), heart, mortar);
        h.assertTrue(shots.size() == 2 && shots.get(0).distanceToSqr(shots.get(1)) > .00001, "Multiple mortar shots failed to aim independently");
        h.assertTrue(VirtualModule.load(mortar.save()).value("targetMultiple") == 1
                && VirtualModule.load(strike.save()).value("targetMultiple") == 1, "Multi-target setting lost after reload");
        mortar.edit("aimMode", 0, "");
        h.assertTrue(dev.sablelife.attack.ShotTrajectory.firingVelocities(h.getLevel(), heart, mortar).size() == 1, "Fixed mortar was multiplied");
        strike.edit("strikeStart", 4, "");
        h.assertTrue(ModuleGroundStrike.pattern(h.getLevel(), heart, strike).size() == 1, "Multi mode ignored inner safety radius");
        h.succeed();
    }
    private static HeartBlockEntity fixture(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(10, 6, 10));
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            level.setBlockAndUpdate(pos.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y < 5; y++) level.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos),
                new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ()));
        return (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    @GameTest(template="combat", batch="quake_multi_targets", timeoutTicks=35)
    public static void MultiTargetQuakeFreezesSeparateCentersAndDoesNotStackOverlap(GameTestHelper h) {
        var heart = fixture(h); var center = origin(heart);
        var near = pig(h, center.add(0,0,3),false);
        var far = pig(h, center.add(5,0,3),false);
        var overlap = pig(h, center.add(1,0,3),false);
        var m = quake(heart); m.edit("quakeOrigin",1,""); m.edit("quakeMode",2,""); m.edit("targetMultiple",1,"");
        m.edit("quakeDetectRange",12,""); m.edit("quakeRange",2,""); m.edit("quakeLaunch",0,""); m.edit("quakePush",0,"");
        h.runAfterDelay(10, () -> {
            h.assertTrue(near.getHealth()==4 && far.getHealth()==4 && overlap.getHealth()==4,
                    "Multi quake missed distant center or stacked overlap: " + near.getHealth()+"/"+far.getHealth()+"/"+overlap.getHealth());
            h.assertTrue(VirtualModule.load(m.save()).value("targetMultiple")==1,"Multi quake flag lost after reload");
            m.edit("enabled",0,""); heart.modules().tick(heart); h.succeed();
        });
    }
    @GameTest(template="combat", batch="particle_properties", timeoutTicks=10)
    public static void ParticlePropertiesPersistIndependentlyAndOldSelectionsUseDefaults(GameTestHelper h) {
        for (var type : List.of("particles","quake","ground_strike","pull","laser","arrow","gun","mortar")) {
            var m = new VirtualModule(type);
            h.assertTrue(m.editParticle("add","minecraft:angry_villager") && m.editParticle("add","minecraft:ash"),"Cannot add selected particles: "+type);
            h.assertTrue(m.editParticle("duration","minecraft:angry_villager",2.5) && m.editParticle("size","minecraft:angry_villager",2),"Cannot edit style: "+type);
            var loaded = VirtualModule.load(m.save());
            h.assertTrue(loaded.particleEntry("minecraft:angry_villager").ticks()==50 && loaded.particleEntry("minecraft:angry_villager").size()==2,"Style lost: "+type);
            h.assertTrue(loaded.particleEntry("minecraft:ash").ticks()==0 && loaded.particleEntry("minecraft:ash").size()==1,"Style leaked to another selection");
            var legacy=m.save(); legacy.remove("ParticleProperties");
            h.assertTrue(VirtualModule.load(legacy).particleEntry("minecraft:angry_villager").ticks()==0,"Legacy particle no longer uses original lifetime");
            loaded.editParticle("remove","minecraft:angry_villager"); loaded.editParticle("add","minecraft:angry_villager");
            h.assertTrue(loaded.particleEntry("minecraft:angry_villager").size()==1,"Removed particle retained stale style");
            h.assertTrue(!loaded.editParticle("size","minecraft:ash",Double.NaN),"Nonfinite style accepted");
        }
        h.succeed();
    }
    @GameTest(template="combat", batch="particle_projectiles", timeoutTicks=10)
    public static void ProjectileStylesFreezeOnLaunchAndSurviveEntityReload(GameTestHelper h) {
        var heart=fixture(h); var level=h.getLevel();
        var m=new VirtualModule("arrow"); m.editParticle("add","minecraft:ash"); m.editParticle("duration","minecraft:ash",3); m.editParticle("size","minecraft:ash",.5);
        var arrow=new dev.sablelife.attack.ModuleArrow(SableLife.MODULE_ARROW.get(),level); arrow.configure(m);
        m.editParticle("duration","minecraft:ash",1);
        var arrowTag=arrow.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        h.assertTrue(arrowTag.getList("ModuleTrailProperties",10).getCompound(0).getInt("Ticks")==60,"Arrow did not freeze launch style");
        var restored=new dev.sablelife.attack.ModuleArrow(SableLife.MODULE_ARROW.get(),level); restored.load(arrowTag);
        h.assertTrue(restored.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getList("ModuleTrailProperties",10).getCompound(0).getFloat("Size")==.5F,"Arrow reload lost style");
        for(var type:List.of("gun","mortar")) {
            var shotModule=new VirtualModule(type); shotModule.editParticle("add","minecraft:ash"); shotModule.editParticle("duration","minecraft:ash",2);
            var shot=new dev.sablelife.attack.ModuleShot(level,origin(heart),Vec3.ZERO,shotModule,heart);
            shotModule.editParticle("duration","minecraft:ash",1);
            var tag=shot.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            var copy=new dev.sablelife.attack.ModuleShot(SableLife.MODULE_SHOT.get(),level); copy.load(tag);
            h.assertTrue(copy.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getList("TrailProperties",10).getCompound(0).getInt("Ticks")==40,"Shot snapshot/reload lost lifetime: "+type);
        }
        h.succeed();
    }
    private static VirtualModule homing(HeartBlockEntity heart) {
        var m=heart.modules().add("homing"); m.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.NORTH)));
        m.edit("target",1,""); m.edit("targetRange",12,""); m.edit("chargeTime",6,""); m.edit("homingDeploy",0,""); m.edit("homingHeight",2,"");
        heart.tryActivate(TestActors.creative((net.minecraft.server.level.ServerLevel)heart.getLevel())); return m;
    }
    @GameTest(template="combat",batch="homing_formation",timeoutTicks=10)
    public static void HomingFormationCentersOneAndFansFiveWithBoundedCount(GameTestHelper h) {
        var heart=fixture(h); var m=homing(heart); var one=ModuleHoming.formation(heart,m);
        h.assertTrue(one.size()==1,"Single formation missing center");
        m.edit("homingCount",5,""); var five=ModuleHoming.formation(heart,m);
        h.assertTrue(five.size()==5 && five.get(2).distanceToSqr(one.getFirst())<.000001,"Five fan does not preserve central shot");
        h.assertTrue(five.getFirst().distanceToSqr(five.getLast())>4,"Fan did not spread out");
        m.edit("homingCount",100,""); h.assertTrue(ModuleHoming.formation(heart,m).size()==32,"Projectile count not bounded");
        m.edit("homingCount",.1,""); h.assertTrue(ModuleHoming.formation(heart,m).size()==1,"Projectile count must stay integral and positive");
        m.editParticle("add","minecraft:ash"); m.editParticle("duration","minecraft:ash",2); m.editEffect("add","minecraft:slowness",0);
        var loaded=VirtualModule.load(m.save()); h.assertTrue(loaded.known() && loaded.effects().size()==1 && loaded.particleEntry("minecraft:ash").ticks()==40,"Homing authoring snapshot lost styles/effects");
        m.edit("enabled",0,""); h.succeed();
    }
    @GameTest(template="combat",batch="homing_hover",timeoutTicks=35)
    public static void HomingHoversBeforeLaunchAndDistributesDifferentTargets(GameTestHelper h) {
        var heart=fixture(h); var m=homing(heart); m.edit("homingCount",2,""); m.edit("targetMultiple",1,"");
        var center=origin(heart); var first=pig(h,center.add(-4,2,-6),true); var second=pig(h,center.add(4,2,-6),true);
        var level=h.getLevel(); var engine=new ModuleHoming(); engine.tick(heart); m.edit("trigger",2,"");
        var shots=level.getEntitiesOfClass(dev.sablelife.attack.ModuleShot.class,new AABB(center,center).inflate(12),e -> m.id().equals(e.homingModule()));
        h.assertTrue(shots.size()==2,"Two hovering shots not spawned"); var positions=shots.stream().map(e -> e.position()).toList();
        // Tick synchronously: GameTest chunk borders may leave only half of a fan entity-ticking.
        for(int i=0;i<3;i++) shots.forEach(e -> e.tick());
        for(int i=0;i<shots.size();i++) h.assertTrue(shots.get(i).hovering() && shots.get(i).position().distanceToSqr(positions.get(i))<.000001,"Projectile moved during hovering charge");
        h.assertTrue(first.getHealth()==10 && second.getHealth()==10,"Hover damaged targets");
        for(int i=0;i<4;i++) shots.forEach(e -> e.tick());
        h.assertTrue(shots.stream().noneMatch(e -> e.hovering() || e.isRemoved()),"Shot did not launch after short input");
        h.assertTrue(shots.stream().map(e -> e.homingTarget()).distinct().count()==2,"Multiple target did not distribute salvo");
        h.assertTrue(shots.stream().allMatch(e -> e.getDeltaMovement().length()>0 && e.getDeltaMovement().length()<=m.value("speed")+.0001),"Homing speed ignored configured bound");
        m.edit("enabled",0,""); engine.tick(heart); h.assertTrue(shots.stream().allMatch(e -> e.isRemoved()),"Disabled module retained projectiles"); h.succeed();
    }
    @GameTest(template="combat",batch="homing_hit",timeoutTicks=60)
    public static void HomingHitsTargetWithPhysicalDamageAndSelectedEffects(GameTestHelper h) {
        var heart=fixture(h); var m=homing(heart); m.edit("homingCount",1,""); m.edit("chargeTime",0,""); m.edit("speed",.5,"");
        m.editEffect("add","minecraft:slowness",0);
        var point=ModuleHoming.formation(heart,m).getFirst(); var victim=pig(h,point.add(0,-.45,-1.5),true);
        var shot=dev.sablelife.attack.ModuleShot.homing(h.getLevel(),point,m.anchors().getFirst().world(heart,true),m,heart,0);
        h.getLevel().addFreshEntity(shot); m.edit("trigger",2,"");
        h.runAfterDelay(20,() -> {
            h.assertTrue(shot.isRemoved() && victim.getHealth()==4,"Homing did not hit once with configured damage: "+victim.getHealth());
            h.assertTrue(victim.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Homing hit lost selected potion effect");
            h.succeed();
        });
    }
    @GameTest(template="combat",batch="homing_reload",timeoutTicks=15)
    public static void HomingReloadRetainsHoverAgeAndOwnerAndTimesOutWithoutTarget(GameTestHelper h) {
        var heart=fixture(h); var m=homing(heart); m.editParticle("add","minecraft:ash"); m.editParticle("duration","minecraft:ash",1.5);
        var point=ModuleHoming.formation(heart,m).getFirst(); var target=pig(h,point.add(0,0,-5),true);
        var shot=dev.sablelife.attack.ModuleShot.homing(h.getLevel(),point,m.anchors().getFirst().world(heart,true),m,heart,0);
        shot.tick(); shot.tick(); var tag=shot.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var copy=new dev.sablelife.attack.ModuleShot(SableLife.MODULE_SHOT.get(),h.getLevel()); copy.load(tag);
        h.assertTrue(copy.hovering() && m.id().equals(copy.homingModule()) && heart.creatureId().equals(copy.attackOwner()),"Reload lost homing identity/phase");
        h.assertTrue(copy.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getList("TrailProperties",10).getCompound(0).getInt("Ticks")==30,"Reload lost styled trail");
        target.discard(); for(int i=0;i<8;i++) copy.tick(); h.assertTrue(copy.isRemoved(),"No-target hover never expired");
        m.edit("enabled",0,""); h.succeed();
    }
    @GameTest(template="combat",batch="homing_deploy",timeoutTicks=15)
    public static void HomingTravelsFromMarkThenChargesAndReloadsDuringTravel(GameTestHelper h) {
        var heart=fixture(h); var m=homing(heart); m.edit("homingDeploy",6,""); m.edit("chargeTime",4,""); m.edit("trigger",2,"");
        var center=m.anchors().getFirst().world(heart,true); var endpoint=ModuleHoming.formation(heart,m).getFirst();
        pig(h,endpoint.add(0,0,-5),true);
        var shot=dev.sablelife.attack.ModuleShot.homing(h.getLevel(),endpoint,center,m,heart,0);
        h.assertTrue(shot.position().distanceToSqr(center)<.000001 && shot.defaultHomingAppearance(),"Default projectile not spawned as native bullet at mark");
        for(int i=1;i<=3;i++) shot.tick();
        h.assertTrue(shot.position().distanceToSqr(center.lerp(endpoint,.5))<.000001,"Deployment missed midpoint");
        var copy=new dev.sablelife.attack.ModuleShot(SableLife.MODULE_SHOT.get(),h.getLevel()); copy.load(shot.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        for(int i=0;i<3;i++) copy.tick();
        h.assertTrue(copy.position().distanceToSqr(endpoint)<.000001 && copy.hovering(),"Reload did not finish deployment at endpoint");
        for(int i=0;i<4;i++) copy.tick(); h.assertTrue(copy.hovering() && copy.position().distanceToSqr(endpoint)<.000001,"Charging did not begin after deployment");
        copy.tick(); h.assertTrue(!copy.hovering() && copy.homingTarget()!=null,"Deployment+charge did not transition to tracking");
        h.succeed();
    }
    @GameTest(template="combat",batch="homing_front_arc",timeoutTicks=10)
    public static void DefaultHomingArcFacesFrontForEveryMarkAndStartDistance(GameTestHelper h) {
        var heart = fixture(h); var m = homing(heart); m.edit("homingCount", 5, "");
        h.assertTrue(m.value("homingPlane") == 0 && m.value("homingRotation") == 0 && m.value("homingVerticalRotation") == 0, "Default arc or rotations changed");
        for (var face : Direction.values()) for (double distance : new double[]{0, 3, 12}) {
            m.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), face)));
            m.edit("startDistance", distance, "");
            var body = m.anchors().getFirst().body(heart);
            var up = dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(body.logicalPose().orientation()), new Vec3(0, 1, 0));
            var heading = ModuleCombat.direction(heart, m);
            heading = heading.subtract(up.scale(heading.dot(up))).normalize();
            if (heading.lengthSqr() < .001) heading = dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(body.logicalPose().orientation()), new Vec3(0, 0, -1));
            var points = ModuleHoming.formation(heart, m); var middle = points.get(2);
            h.assertTrue(middle.subtract(points.getFirst()).dot(up) > .5 && middle.subtract(points.getLast()).dot(up) > .5, "Arc stopped arching upward: " + face + " distance=" + distance);
            for (var point : points) h.assertTrue(Math.abs(point.subtract(middle).dot(heading)) < .000001, "Arc spread into top-view depth: " + face + " distance=" + distance);
            var loaded = ModuleHoming.formation(heart, VirtualModule.load(m.save()));
            for (int i = 0; i < points.size(); i++) h.assertTrue(points.get(i).distanceTo(loaded.get(i)) < .000001, "Reload changed arc plane");
        }
        h.succeed();
    }
    @GameTest(template="combat",batch="homing_planes",timeoutTicks=10)
    public static void HomingShapesRotateAndMigrateSavedChoices(GameTestHelper h) {
        var heart=fixture(h); var m=homing(heart); m.edit("homingCount",5,""); m.edit("trigger",2,"");
        m.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.UP)));
        var arc=ModuleHoming.formation(heart,m); var center=arc.get(2);
        h.assertTrue(center.y>arc.getFirst().y+.5 && Math.abs(arc.getFirst().y-arc.getLast().y)<.000001,"Top marker did not produce an upright arch");
        m.edit("homingPlane",1,""); var line=ModuleHoming.formation(heart,m);
        h.assertTrue(line.stream().allMatch(p -> Math.abs(p.y-center.y)<.000001),"Line was not level");
        var step=line.get(1).subtract(line.getFirst());
        for(int i=2;i<line.size();i++) h.assertTrue(line.get(i).subtract(line.get(i-1)).distanceToSqr(step)<.000001,"Line spacing was not uniform");
        m.edit("homingRotation",90,""); var rotated=ModuleHoming.formation(heart,m);
        h.assertTrue(rotated.getFirst().distanceToSqr(line.getFirst())>1 && rotated.get(2).distanceToSqr(center)<.000001,"Rotation did not rotate around center");
        m.edit("homingRotation",0,"");
        for(int shape=6;shape<=7;shape++) {
            m.edit("homingPlane",shape,""); var diagonal=ModuleHoming.formation(heart,m);
            h.assertTrue(Math.abs(diagonal.getFirst().y-diagonal.getLast().y)>1,"Diagonal remained horizontal");
            h.assertTrue((diagonal.getLast().y-diagonal.getFirst().y)*(shape==6 ? -1 : 1)>0,"Diagonal slopes were not opposite");
        }
        for(int shape:new int[]{2,3,4,5}) {
            m.edit("homingPlane",shape,""); m.edit("homingCount",10,""); var points=ModuleHoming.formation(heart,m);
            for(int i=0;i<points.size();i++) {
                double radius=points.get(i).distanceTo(center);
                double expected=(shape==2 || shape==3) && i%2==1 ? m.value("homingOffset")*.38196601125 : m.value("homingOffset");
                h.assertTrue(Math.abs(radius-expected)<.000001,"Star/circle outline radius incorrect");
            }
            h.assertTrue(points.stream().distinct().count()==10,"Closed outline duplicated endpoint");
            m.edit("homingCount",1,""); h.assertTrue(ModuleHoming.formation(heart,m).getFirst().distanceToSqr(center)<.000001,"One projectile must remain central");
        }
        for(int shape=0;shape<8;shape++) {
            m.edit("homingPlane",shape,""); m.edit("homingRotation",45,"");
            var copy=VirtualModule.load(m.save()); h.assertTrue(copy.value("homingPlane")==shape && copy.value("homingRotation")==45,"Saved shape/rotation lost");
        }
        for(int version=1;version<=2;version++) for(int old=0;old<(version==1 ? 3 : 4);old++) {
            var legacy=m.save(); legacy.putInt("HomingFormationVersion",version); legacy.getCompound("Settings").putDouble("homingPlane",old);
            h.assertTrue(VirtualModule.load(legacy).value("homingPlane")== (version==2 && old>=2 ? 1 : 0),"Legacy shape migration failed");
        }
        h.assertTrue(ModuleFields.find("homing","homingPlane").choices().size()==8,"Expected eight shapes"); h.succeed();
    }
    @GameTest(template="combat",batch="homing_two_axes",timeoutTicks=15)
    public static void HomingVerticalRotationTurnsPlaneWithoutRollingArch(GameTestHelper h) {
        var heart=fixture(h); var m=homing(heart); m.edit("trigger",2,""); m.edit("homingCount",5,"");
        m.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.UP)));
        var front=ModuleHoming.formation(heart,m); var center=front.get(2);
        m.edit("homingVerticalRotation",90,""); var side=ModuleHoming.formation(heart,m);
        h.assertTrue(center.distanceToSqr(side.get(2))<.000001,"Vertical rotation displaced center");
        for(int i=0;i<5;i++) {
            h.assertTrue(Math.abs(side.get(i).y-front.get(i).y)<.000001,"Vertical rotation rolled the upright arch");
            h.assertTrue(Math.abs(side.get(i).distanceTo(center)-front.get(i).distanceTo(center))<.000001,"Rotation changed formation size");
        }
        h.assertTrue(Math.abs(side.getFirst().x-side.getLast().x)<.000001 && Math.abs(side.getFirst().z-side.getLast().z)>1,"90 degree vertical rotation did not face sideways");
        m.edit("homingRotation",45,""); var both=ModuleHoming.formation(heart,m);
        h.assertTrue(both.getFirst().distanceToSqr(side.getFirst())>.1,"Two angles did not combine");
        var copy=VirtualModule.load(m.save()); h.assertTrue(copy.value("homingVerticalRotation")==90 && copy.value("homingRotation")==45,"Two rotations lost on reload");
        var legacy=m.save(); legacy.getCompound("Settings").remove("homingVerticalRotation");
        h.assertTrue(VirtualModule.load(legacy).value("homingVerticalRotation")==0,"Legacy vertical rotation must default to zero");
        h.succeed();
    }
    @GameTest(template="combat",batch="internal_empty_frequency",timeoutTicks=15)
    public static void InternalRepeatWorksWithoutFrequencyAndExternalModesStillRequireIt(GameTestHelper h) {
        var heart=fixture(h); var player=TestActors.creative(h.getLevel()); heart.tryActivate(player);
        int index=heart.wireless().add(); var channel=heart.wireless().channel(index);
        channel.set(0,1); channel.set(6,0); channel.set(2,3); channel.set(3,2);
        h.assertTrue(channel.configured() && channel.powerAt(0)==15 && channel.powerAt(3)==0 && channel.powerAt(5)==15,"Empty-frequency internal waveform failed");
        h.assertTrue(heart.wireless().output(index)==15,"Heart did not expose empty-frequency internal signal");
        var copy=dev.sablelife.heart.WirelessChannel.load(channel.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(copy.configured() && copy.first().isEmpty() && copy.second().isEmpty() && copy.powerAt(5)==15,"Empty internal configuration lost on reload");
        for(int mode : new int[]{1,2,4}) { channel.set(6,mode); h.assertTrue(!channel.configured(),"External channel modes must still require frequency items"); }
        channel.set(6,3); h.assertTrue(channel.configured() && channel.powerAt(0)==0,"Empty sequence/internal channel must be ready without generating its own waveform");
        heart.wireless().programOutputs("test",java.util.Map.of(index,9));
        h.assertTrue(heart.wireless().output(index)==9,"Empty sequence/internal channel rejected authored output");
        var programmedCopy=dev.sablelife.heart.WirelessChannel.load(channel.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(programmedCopy.configured() && programmedCopy.programmed(),"Empty sequence/internal route lost on reload");
        channel.set(6,0); channel.set(0,0); h.assertTrue(channel.powerAt(0)==0,"Disabled internal channel emitted");
        channel.set(0,1); heart.wireless().master(false); h.assertTrue(heart.wireless().output(index)==0,"Master off left an internal signal");
        h.succeed();
    }
    @GameTest(template="combat",batch="core_regen",timeoutTicks=15)
    public static void CoreRegenerationWaitsResetsCapsAndSurvivesCopy(GameTestHelper h) {
        var heart=fixture(h); var player=TestActors.creative(h.getLevel()); player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        h.assertTrue(heart.editCore(player,"regen",5,"") && heart.editCore(player,"regenDelay",.2,""),"Regen authoring failed");
        heart.tryActivate(player); heart.damage(20);
        for(int i=0;i<23;i++) heart.tickRegeneration();
        h.assertTrue(heart.health().current()==80,"Regen ignored quiet delay or one-second cadence");
        heart.tickRegeneration(); h.assertTrue(heart.health().current()==85,"Regen did not restore per-second amount");
        heart.damage(5); for(int i=0;i<23;i++) heart.tickRegeneration();
        h.assertTrue(heart.health().current()==80,"New damage failed to reset regen timing");
        var saved=heart.saveWithoutMetadata(h.getLevel().registryAccess());
        var copy=new HeartBlockEntity(heart.getBlockPos(),heart.getBlockState()); copy.loadWithComponents(saved,h.getLevel().registryAccess());
        copy.tickRegeneration(); h.assertTrue(copy.health().current()==85,"Regen progress did not survive reload");
        var config=heart.copyConfiguration(); var dormant=new HeartBlockEntity(heart.getBlockPos(),heart.getBlockState());
        h.assertTrue(config.getFloat("RegenPerSecond")==5 && config.getInt("RegenDelay")==4,"Copy lost regen configuration");
        for(int i=0;i<100;i++) heart.tickRegeneration(); h.assertTrue(heart.health().current()==100,"Regen exceeded maximum health");
        heart.editCore(player,"regen",0,""); heart.damage(10); for(int i=0;i<100;i++) heart.tickRegeneration();
        h.assertTrue(heart.health().current()==90,"Disabled regen still healed");
        heart.editCore(player,"regen",5,""); heart.damage(1000); for(int i=0;i<100;i++) heart.tickRegeneration();
        h.assertTrue(heart.health().current()==0 && heart.health().state()!=dev.sablelife.heart.HeartState.ALIVE,"Regen revived a dying core"); h.succeed();
    }
    @GameTest(template="combat",batch="homing_closed_planes",timeoutTicks=15)
    public static void StarCircleDefaultHorizontalAndVerticalVariantsStayUpright(GameTestHelper h) {
        var heart=fixture(h); var m=homing(heart); m.edit("trigger",2,""); m.edit("homingCount",10,"");
        m.edit("homingCount",1,""); var center=ModuleHoming.formation(heart,m).getFirst(); m.edit("homingCount",10,"");
        for(int shape:new int[]{2,4}) {
            m.edit("homingPlane",shape,""); var horizontal=ModuleHoming.formation(heart,m);
            h.assertTrue(horizontal.stream().allMatch(p->Math.abs(p.y-center.y)<.000001),"Default closed shape was not horizontal");
            m.edit("homingPlane",shape+1,""); var vertical=ModuleHoming.formation(heart,m);
            h.assertTrue(vertical.stream().anyMatch(p->p.y>center.y+1) && vertical.stream().anyMatch(p->p.y<center.y-1),"Vertical closed shape did not stand upright");
        }
        for(int old=0;old<6;old++) {
            var saved=m.save(); saved.putInt("HomingFormationVersion",3); saved.getCompound("Settings").putDouble("homingPlane",old);
            int expected=old==2 ? 3 : old==3 ? 5 : old>=4 ? old+2 : old;
            h.assertTrue(VirtualModule.load(saved).value("homingPlane")==expected,"Legacy closed-shape appearance changed");
        }
        h.succeed();
    }
    @GameTest(template="combat",batch="homing_blast",timeoutTicks=15)
    public static void HomingImpactAddsBlastAreaAndKeepsSnapshotOnReload(GameTestHelper h) {
        var heart=fixture(h); var m=homing(heart); m.edit("trigger",2,""); m.edit("chargeTime",0,""); m.edit("speed",.5,"");
        m.edit("damage",2,""); m.edit("explode",1,""); m.edit("blastDamage",3,""); m.edit("blastRadius",2,"");
        m.edit("blastSound",0,"minecraft:entity.firework_rocket.blast"); m.edit("blastSoundVolume",.5,""); m.edit("blastSoundRadius",20,"");
        var point=ModuleHoming.formation(heart,m).getFirst(); var center=m.anchors().getFirst().world(heart,true);
        var victim=pig(h,point.add(0,-.45,-2),true); var nearby=pig(h,point.add(1.5,-.45,-2),true); var outside=pig(h,point.add(4,-.45,-2),true);
        var original=dev.sablelife.attack.ModuleShot.homing(h.getLevel(),point,center,m,heart,0);
        var saved=original.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        h.assertTrue(saved.getCompound("Blast").getString("Sound").contains("firework") && saved.getCompound("Blast").getFloat("SoundRadius")==20,"Blast sound snapshot missing");
        m.edit("blastDamage",100,""); m.edit("explode",0,"");
        var shot=new dev.sablelife.attack.ModuleShot(SableLife.MODULE_SHOT.get(),h.getLevel()); shot.load(saved);
        for(int i=0;i<30 && !shot.isRemoved();i++) shot.tick();
        h.assertTrue(shot.isRemoved() && victim.getHealth()==5 && nearby.getHealth()==7 && outside.getHealth()==10,"Homing direct+area blast failed: "+victim.getHealth()+"/"+nearby.getHealth()+"/"+outside.getHealth());
        victim.discard(); nearby.discard(); outside.discard();
        victim=pig(h,point.add(0,-.45,-2),true); nearby=pig(h,point.add(1.5,-.45,-2),true);
        shot=dev.sablelife.attack.ModuleShot.homing(h.getLevel(),point,center,m,heart,0);
        for(int i=0;i<30 && !shot.isRemoved();i++) shot.tick();
        h.assertTrue(shot.isRemoved() && victim.getHealth()==8 && nearby.getHealth()==10,"Blast off still damaged surrounding targets");
        h.assertTrue(VirtualModule.load(m.save()).value("blastRadius")==2,"Blast settings lost on module reload"); h.succeed();
    }
    @GameTest(template="combat",batch="homing_direct",timeoutTicks=15)
    public static void DirectPursuitLocksAllToSingleAndTurnsByStrength(GameTestHelper h) {
        var heart=fixture(h); var m=homing(heart); m.edit("chargeTime",0,""); m.edit("trigger",2,"");
        var point=ModuleHoming.formation(heart,m).getFirst(); var center=m.anchors().getFirst().world(heart,true);
        var nearest=pig(h,point.add(0,0,-4),true); pig(h,point.add(4,0,-5),true);
        m.edit("homingTracking",1,"");
        var strong=dev.sablelife.attack.ModuleShot.homing(h.getLevel(),point,center,m,heart,0);
        strong.tick();
        strong.setDeltaMovement(new Vec3(.3,0,0)); strong.tick();
        m.edit("homingTracking",.01,"");
        var weak=dev.sablelife.attack.ModuleShot.homing(h.getLevel(),point,center,m,heart,1); weak.tick();
        // Initial charging clears velocity, so compare the second tick's sideways inertia.
        weak.setDeltaMovement(new Vec3(.3,0,0)); weak.tick();
        h.assertTrue(nearest.getUUID().equals(strong.homingTarget()) && nearest.getUUID().equals(weak.homingTarget()),"Single salvo split among targets");
        h.assertTrue(Math.abs(strong.getDeltaMovement().x-.24)<.001 && weak.getDeltaMovement().x>.299,"Tracking strength did not control turning");
        m.edit("homingTracking",.5,"");
        var middle=dev.sablelife.attack.ModuleShot.homing(h.getLevel(),point,center,m,heart,2); middle.tick();
        middle.setDeltaMovement(new Vec3(.3,0,0)); middle.tick();
        h.assertTrue(Math.abs(middle.getDeltaMovement().x-.27)<.001,"New 0.5 must match the old 0.1 steering coefficient");
        m.edit("homingTracking",.01,"");
        h.assertTrue(VirtualModule.load(m.save()).value("homingTracking")==.01,"Tracking strength lost on reload");
        m.edit("homingMode",1,""); var segmented=dev.sablelife.attack.ModuleShot.homing(h.getLevel(),point,center,m,heart,0); segmented.tick();
        h.assertTrue(!segmented.hovering() && segmented.getDeltaMovement().length()>0,"Segmented mode failed to launch");
        h.succeed();
    }
    @GameTest(template="combat",batch="homing_moving",timeoutTicks=15)
    public static void DirectPursuitHitsMovingTargetWithinSpeedCap(GameTestHelper h) {
        var heart=fixture(h); var m=homing(heart); m.edit("chargeTime",0,""); m.edit("trigger",2,"");
        m.edit("speed",.5,""); m.edit("homingTracking",1,"");
        var point=ModuleHoming.formation(heart,m).getFirst(); var target=pig(h,point.add(0,-.45,-5),true);
        var shot=dev.sablelife.attack.ModuleShot.homing(h.getLevel(),point,m.anchors().getFirst().world(heart,true),m,heart,0);
        for(int i=0;i<40 && !shot.isRemoved();i++) {
            target.setPos(target.position().add(.08,0,0)); target.setDeltaMovement(new Vec3(.08,0,0)); shot.tick();
            h.assertTrue(shot.getDeltaMovement().length()<=.5001,"Pursuit exceeded flight speed");
        }
        h.assertTrue(shot.isRemoved() && target.getHealth()==4,"Direct pursuit failed actual moving-target collision: "+target.getHealth()); h.succeed();
    }
    @GameTest(template="combat",batch="core_loot_rules",timeoutTicks=15)
    public static void WholeBodyLootRulesAndLifeAttackToggleSurviveCopy(GameTestHelper h) {
        var heart=fixture(h); var player=TestActors.creative(h.getLevel()); player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        heart.setDeathTemplate(player,0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        for(int i=0;i<20;i++) heart.adjustLootRule(player,0,-1,false);
        var drops=dev.sablelife.part.LootSelection.draw(heart.deathTemplates(),heart.chances(),heart.weights(),heart.weighted(),heart.rolls(),h.getLevel().random);
        h.assertTrue(drops.isEmpty(),"Zero whole-body chance dropped a template");
        heart.adjustLootRule(player,31,1,false); heart.adjustLootRule(player,32,1,false);
        drops=dev.sablelife.part.LootSelection.draw(heart.deathTemplates(),heart.chances(),heart.weights(),heart.weighted(),heart.rolls(),h.getLevel().random);
        h.assertTrue(drops.size()==2,"Weighted whole-body rolls not applied");
        var other=java.util.UUID.randomUUID(); h.assertTrue(heart.acceptsLifeAttack(other) && !heart.acceptsLifeAttack(heart.creatureId()),"Default attack policy changed");
        heart.editCore(player,"lifeAttacks",0,"");
        h.assertTrue(!heart.acceptsLifeAttack(other) && heart.acceptsLifeAttack(null),"Life attack toggle blocked ordinary attacks or allowed other lifeforms");
        var data=heart.copyConfiguration(); heart.adjustLootRule(player,31,1,false); heart.editCore(player,"lifeAttacks",1,"");
        h.assertTrue(heart.pasteConfiguration(player,data) && heart.weighted() && heart.rolls()==2 && heart.chance(0)==0 && !heart.allowLifeAttacks(),"Copied authoring rules were lost");
        var copy=new HeartBlockEntity(heart.getBlockPos(),heart.getBlockState()); copy.loadWithComponents(heart.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(copy.weighted() && copy.rolls()==2 && copy.chance(0)==0 && !copy.allowLifeAttacks(),"Saved core rules were lost");
        var menu=new dev.sablelife.heart.HeartLootMenu(123,player.getInventory(),heart);
        h.assertTrue(menu.getRowCount()==6 && menu.getContainer().getItem(31).is(net.minecraft.world.item.Items.ENDER_EYE),"Whole-body menu not using limb rule controls");
        h.succeed();
    }
    private static HeartBlockEntity otherHeart(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(10,6,4)); var level=h.getLevel();
        level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ()));
        return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    @GameTest(template="combat",batch="target_categories",timeoutTicks=15)
    public static void LifeformCategoryIncludesActiveOthersAndKeepsMobAndOldIndices(GameTestHelper h) {
        var heart=fixture(h); var other=otherHeart(h); var player=TestActors.creative(h.getLevel()); player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        heart.tryActivate(player); var center=origin(heart); var pig=pig(h,center.add(3,0,0),true); var box=new AABB(center,center).inflate(20);
        var empty=ModuleTargets.query(h.getLevel(),heart.creatureId(),box,e -> ModuleTargets.matches(e,3));
        h.assertTrue(empty.stream().noneMatch(e -> e.getUUID().equals(other.creatureId())),"Dormant core counted as a lifeform"); other.tryActivate(player);
        var life=ModuleTargets.query(h.getLevel(),heart.creatureId(),box,e -> ModuleTargets.matches(e,3));
        h.assertTrue(life.stream().anyMatch(e -> e.getUUID().equals(other.creatureId())) && life.stream().noneMatch(e -> e.getUUID().equals(heart.creatureId())),"Lifeform selection missed other or included self");
        var mobs=ModuleTargets.query(h.getLevel(),heart.creatureId(),box,e -> ModuleTargets.matches(e,1));
        h.assertTrue(mobs.contains(pig) && mobs.stream().noneMatch(e -> e instanceof ModuleTargets.LifeTarget),"Other mob mixed in mechanical cores");
        var all=ModuleTargets.query(h.getLevel(),heart.creatureId(),box,e -> ModuleTargets.matches(e,2));
        h.assertTrue(all.stream().anyMatch(e -> e.getUUID().equals(other.creatureId())) && all.contains(pig),"All did not include all categories");
        h.assertTrue(!ModuleTargets.matches(player,0),"Creative player unexpectedly targetable");
        var m=new VirtualModule("homing"); m.edit("target",3,"");
        h.assertTrue(VirtualModule.load(m.save()).value("target")==3 && ModuleFields.forType("homing").stream().filter(f -> f.key().equals("target")).findFirst().orElseThrow().choices().equals(ModuleTargets.CHOICES),"Category or save compatibility lost");
        other.damage(other.health().maximum()); h.assertTrue(ModuleTargets.resolve(h.getLevel(),other.creatureId(),heart.creatureId())==null,"Dying core stayed locked"); h.succeed();
    }
    @GameTest(template="combat",batch="target_life_attacks",timeoutTicks=15)
    public static void LifeformTargetingFeedsHomingLaserMortarDispatchAndDamageGate(GameTestHelper h) {
        var heart=fixture(h); var other=otherHeart(h); var player=TestActors.creative(h.getLevel()); player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        heart.tryActivate(player); other.tryActivate(player); var center=origin(heart);
        var m=heart.modules().add("homing"); m.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.NORTH))); m.edit("target",3,""); m.edit("lineOfSight",1,"");
        var targets=ModuleHoming.targets(h.getLevel(),heart,m,center);
        h.assertTrue(targets.size()==1 && targets.getFirst().getUUID().equals(other.creatureId()),"Homing cannot see core through target surface: "+ModuleTargets.query(h.getLevel(),heart.creatureId(),new AABB(center,center).inflate(64),e -> ModuleTargets.matches(e,3)).stream().map(e -> e.getBoundingBox()+" visible="+ModuleTargets.visible(h.getLevel(),heart.creatureId(),center,e)).toList());
        var laser=new VirtualModule("laser"); laser.edit("target",3,"");
        h.assertTrue(ModuleLaser.acquireTargets(h.getLevel(),heart,laser,center).size()==1,"Laser did not acquire other lifeform");
        var mortar=new VirtualModule("mortar"); mortar.anchors(m.anchors()); mortar.edit("target",3,""); mortar.edit("aimMode",1,"");
        h.assertTrue(!dev.sablelife.attack.ShotTrajectory.firingVelocities(h.getLevel(),heart,mortar).isEmpty(),"Mortar did not solve lifeform target");
        var rules=new MoveRules(); rules.edit("target",3,""); h.assertTrue(rules.targetPresent(heart),"Move set conditions did not detect other lifeform");
        other.editCore(player,"lifeAttacks",0,""); var target=targets.getFirst();
        h.assertTrue(!dev.sablelife.attack.ModuleDamage.hurt(target,dev.sablelife.attack.ModuleDamage.physical(h.getLevel()),6) && other.health().current()==100,"Target category bypassed core immunity");
        other.editCore(player,"lifeAttacks",1,"");
        h.assertTrue(dev.sablelife.attack.ModuleDamage.hurt(target,dev.sablelife.attack.ModuleDamage.physical(h.getLevel()),6) && other.health().current()==94,"Adapter failed to damage the real core");
        m.edit("trigger",2,"");
        h.runAfterDelay(2,() -> {
        m.edit("homingDeploy",0,""); m.edit("chargeTime",0,""); m.edit("speed",.5,"");
        var shot=dev.sablelife.attack.ModuleShot.homing(h.getLevel(),ModuleHoming.formation(heart,m).getFirst(),m.anchors().getFirst().world(heart,true),m,heart,0);
        for(int i=0;i<40 && !shot.isRemoved();i++) shot.tick();
        h.assertTrue(shot.isRemoved() && other.health().current()==88,"Homing did not physically hit core: hp="+other.health().current()+" removed="+shot.isRemoved()+" target="+shot.homingTarget()+" pos="+shot.position()+" destination="+target.getBoundingBox());
        var attack=heart.modules().add("laser"); attack.anchors(m.anchors()); attack.edit("target",3,""); attack.edit("laserAim",1,""); attack.edit("chargeTime",0,""); attack.edit("beamHits",1,"");
        var engine=new ModuleLaser(); engine.tick(heart);
        h.assertTrue(other.health().current()==82,"Laser did not hit lifeform surface: "+other.health().current()); engine.clear();
        h.assertTrue(h.getLevel().getEntity(other.creatureId())==null,"Target adapter leaked into entity world"); h.succeed();
        });
    }
    private static Vec3 origin(HeartBlockEntity heart) {
        return SableBodyProbe.toWorld(heart.getLevel(), heart.getBlockPos().getCenter()).add(0, -.5, 0);
    }
    private static Pig pig(GameTestHelper h, Vec3 point, boolean floating) {
        var pig = EntityType.PIG.create(h.getLevel()); pig.setNoAi(true); pig.setNoGravity(floating); pig.setPos(point);
        h.getLevel().addFreshEntity(pig); return pig;
    }
    private static VirtualModule quake(HeartBlockEntity heart) {
        var module = heart.modules().add("quake");
        module.anchors(List.of(ModuleAnchor.create(heart, heart.getBlockPos(), Direction.NORTH)));
        module.edit("target", 1, ""); module.edit("quakeMode", 1, ""); module.edit("quakeRange", 6, ""); module.edit("quakeSpeed", 1, "");
        module.edit("warningTime", 8, ""); module.edit("damage", 6, ""); module.edit("interval", 100, "");
        heart.tryActivate(TestActors.creative((net.minecraft.server.level.ServerLevel) heart.getLevel()));
        return module;
    }
    @GameTest(template="combat", batch="quake_ground", timeoutTicks=80)
    public static void RingLaunchesGroundedTargetButLetsAirborneTargetDodge(GameTestHelper h) {
        var heart = fixture(h); var module = quake(heart); var center = origin(heart);
        var grounded = pig(h, center.add(0, 0, 3), false);
        var airborne = pig(h, center.add(2, .8, 3), true);
        h.runAfterDelay(5, () -> h.assertTrue(grounded.getHealth() == 10, "Wave hit during warning"));
        h.runAfterDelay(13, () -> {
            h.assertTrue(grounded.getHealth() == 4, "Ring failed: hp=" + grounded.getHealth() + " ground=" + grounded.onGround() + " pos=" + grounded.position() + " center=" + center + " phase=" + heart.modules().snapshot(heart,true).getList("Modules",10).getCompound(0).getString("QuakePhase") + " state=" + heart.health().state());
            h.assertTrue(grounded.getDeltaMovement().y > 0, "Wave did not launch grounded target");
            h.assertTrue(airborne.getHealth() == 10, "Grounded-only wave hit airborne target");
            h.assertTrue(h.getLevel().getBlockState(BlockPos.containing(center.add(0, -.1, 3))).is(Blocks.STONE), "Wave edited real terrain");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(QuakeGroundBlock.class, new AABB(center, center).inflate(8)).isEmpty(), "No temporary ground models were created");
            module.edit("enabled", 0, ""); heart.modules().tick(heart);
            h.assertTrue(h.getLevel().getEntitiesOfClass(QuakeGroundBlock.class, new AABB(center, center).inflate(8)).isEmpty(), "Disabling retained temporary ground models");
            h.succeed();
        });
    }
    @GameTest(template="combat", batch="quake_feet", timeoutTicks=80)
    public static void TargetFeetWarningFreezesCenterAndShortInputCompletesCast(GameTestHelper h) {
        var heart = fixture(h); var module = quake(heart); module.edit("target", 1, ""); module.edit("quakeOrigin", 1, "");
        module.edit("quakeLaunch", 0, ""); module.edit("quakePush", 0, "");
        var center = origin(heart); var target = pig(h, center.add(0, 0, 3), false);
        h.runAfterDelay(3, () -> {
            target.setPos(center.add(12, 0, 3));
            module.edit("trigger", 2, ""); // Input falls after warning: the authored cast must still finish.
        });
        final Pig[] replacement = new Pig[1];
        h.runAfterDelay(4, () -> replacement[0] = pig(h, center.add(1, 0, 3), false));
        h.runAfterDelay(14, () -> {
            h.assertTrue(target.getHealth() == 10, "Frozen warning followed escaping target");
            h.assertTrue(replacement[0].getHealth() == 4, "Feet failed: hp=" + replacement[0].getHealth() + " ground=" + replacement[0].onGround() + " pos=" + replacement[0].position() + " phase=" + heart.modules().snapshot(heart,true).getList("Modules",10).getCompound(0).getString("QuakePhase"));
            var loaded = VirtualModule.load(module.save());
            h.assertTrue(loaded.value("quakeOrigin") == 1 && loaded.value("quakeLaunch") == 0 && loaded.anchors().size() == 1, "Quake save/reload lost configuration");
            heart.modules().remove(module.id()); heart.modules().tick(heart); h.succeed();
        });
    }
    @GameTest(template="combat", batch="quake_line", timeoutTicks=80)
    public static void StraightWaveRejectsTargetsBesideAndBehindTheLine(GameTestHelper h) {
        var heart = fixture(h); var module = quake(heart); module.edit("target", 1, "");
        module.edit("quakeMode", 0, ""); module.edit("quakeWidth", 1, ""); module.edit("quakeLaunch", 0, ""); module.edit("quakePush", 0, "");
        var center = origin(heart); var front = pig(h, center.add(0, 0, -3), false);
        var side = pig(h, center.add(2, 0, -3), false); var behind = pig(h, center.add(0, 0, 3), false);
        h.runAfterDelay(14, () -> {
            h.assertTrue(front.getHealth() == 4, "Line failed: hp=" + front.getHealth() + " ground=" + front.onGround() + " pos=" + front.position() + " phase=" + heart.modules().snapshot(heart,true).getList("Modules",10).getCompound(0).getString("QuakePhase"));
            h.assertTrue(side.getHealth() == 10 && behind.getHealth() == 10, "Straight wave leaked beside/behind its path");
            module.edit("enabled", 0, ""); heart.modules().tick(heart); h.succeed();
        });
    }
    @GameTest(template="combat", batch="quake_multi", timeoutTicks=80)
    public static void MultipleWavesHitOnceEachAndDeathClearsUnsavedModels(GameTestHelper h) {
        var heart = fixture(h); var module = quake(heart); var center = origin(heart);
        module.edit("damage", 2, ""); module.edit("quakeWaves", 3, ""); module.edit("quakeGap", 3, "");
        module.edit("quakeLaunch", 0, ""); module.edit("quakePush", 0, "");
        var target = pig(h, center.add(0, 0, 3), false);
        h.runAfterDelay(20, () -> {
            h.assertTrue(target.getHealth() == 4, "Three wave fronts did not hit exactly once per wave: HP="+target.getHealth()+" state="+heart.health().state()+" triggered="+HeartModules.triggered(module,heart));
            var models = h.getLevel().getEntitiesOfClass(QuakeGroundBlock.class, new AABB(center, center).inflate(8));
            h.assertTrue(models.stream().allMatch(e -> !e.shouldBeSaved()), "Temporary terrain model can leak into a save");
            heart.damage(heart.health().maximum()); heart.modules().tick(heart);
            h.assertTrue(h.getLevel().getEntitiesOfClass(QuakeGroundBlock.class, new AABB(center, center).inflate(8)).isEmpty(), "Death retained temporary ground models");
            h.succeed();
        });
    }
    @GameTest(template="combat",batch="quake_ring_budget")
    public static void RingDistributesLimitedGroundModelsAroundEntireCircumference(GameTestHelper h)throws Exception{
        var heart=fixture(h);var module=quake(heart);module.edit("damage",0,"");module.edit("quakeRange",8,"");var center=origin(heart);
        var stateType=Class.forName("dev.sablelife.module.ModuleQuake$State");var constructor=stateType.getDeclaredConstructor();constructor.setAccessible(true);
        var draw=ModuleQuake.class.getDeclaredMethod("waveAt",HeartBlockEntity.class,stateType,int.class,double.class,double.class);draw.setAccessible(true);
        for(int budget:new int[]{13,17,31}){
            var renderer=new ModuleQuake();var state=constructor.newInstance();
            for(var setting:java.util.Map.of("cast",VirtualModule.load(module.save()),"origin",center,"direction",new Vec3(0,0,1)).entrySet()){var f=stateType.getDeclaredField(setting.getKey());f.setAccessible(true);f.set(state,setting.getValue());}
            var levelField=ModuleQuake.class.getDeclaredField("level");levelField.setAccessible(true);levelField.set(renderer,h.getLevel());
            var statesField=ModuleQuake.class.getDeclaredField("states");statesField.setAccessible(true);((java.util.Map)statesField.get(renderer)).put(module.id(),state);
            var visualsField=stateType.getDeclaredField("visuals");visualsField.setAccessible(true);var visuals=(java.util.List<QuakeGroundBlock>)visualsField.get(state);
            var base=BlockPos.containing(center.add(0,-1,0));for(int i=0;i<192-budget;i++)visuals.add(new QuakeGroundBlock(h.getLevel(),base,Blocks.STONE.defaultBlockState(),.8));
            int previous=visuals.size();draw.invoke(renderer,heart,state,0,5.,6.);
            h.assertTrue(visuals.size()==192,"Ring did not respect/use remaining budget: "+budget+" -> "+visuals.size());
            int[] quadrants=new int[4];var angles=new java.util.ArrayList<Double>();
            for(int i=previous;i<visuals.size();i++){var block=visuals.get(i);double dx=block.getX()+.5-center.x,dz=block.getZ()+.5-center.z;double angle=Math.atan2(dz,dx);if(angle<0)angle+=Math.PI*2;angles.add(angle);quadrants[Math.min(3,(int)(angle/(Math.PI/2))) ]++;h.assertTrue(h.getLevel().getBlockState(BlockPos.containing(block.position().add(0,-.01,0))).is(Blocks.STONE),"Visual altered real terrain");}
            for(int count:quadrants)h.assertTrue(count>=2,"Display budget filled one side of ring: "+java.util.Arrays.toString(quadrants));angles.sort(Double::compare);double maxGap=0;for(int i=0;i<angles.size();i++){double next=i+1<angles.size()?angles.get(i+1):angles.getFirst()+Math.PI*2;maxGap=Math.max(maxGap,next-angles.get(i));}h.assertTrue(maxGap<Math.PI/2,"Ring left an entire quadrant empty");renderer.clear();
        }
        h.assertTrue(QuakeGeometry.ringSamples(16)>64&&QuakeGeometry.ringSamples(64)<=256&&QuakeGeometry.ringSamples(16)%4==0,"Ring sampling is sparse, unbounded or asymmetric");h.succeed();
    }

}
