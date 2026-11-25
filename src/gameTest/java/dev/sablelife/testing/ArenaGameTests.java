package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;

@GameTestHolder("sablelife_arena_tests")
@PrefixGameTestTemplate(false)
public final class ArenaGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(4,6,4));
        level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),
                new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static VirtualModule arena(HeartBlockEntity heart) {
        var m=heart.modules().add("arena"); m.edit("trigger",0,""); m.edit("arenaRadius",4,""); m.edit("countdown",20,"");
        m.arenaCenter(ModuleArena.corePosition(heart).add(8,0,0),heart.getLevel().dimension().location().toString());
        return m;
    }
    @GameTest(template="combat",timeoutTicks=80)
    public static void DeathTogglePreservesWarningsAndCanBeReenabled(GameTestHelper h) throws Exception {
        var heart=fixture(h); var m=arena(heart);
        h.assertTrue(m.value("arenaDeath")==1&&m.value("arenaForceReturn")==0,"Old Arena behavior must remain the default");
        h.assertTrue(m.value("arenaPlayerDeathCountdown")==100,"New player death countdown must default to five seconds");
        m.edit("arenaPlayerDeathCountdown",40,"");
        var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ArenaTester")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        var grace=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        grace.setAccessible(true);grace.setInt(player,0);
        player.setGameMode(GameType.SURVIVAL);player.setPos(ModuleArena.corePosition(heart));
        heart.tryActivate(player);h.getLevel().players().add(player); m.edit("arenaDeath",0,"");
        var runtime=new ModuleArena();runtime.tick(heart);
        h.assertTrue(runtime.warning(m.id()),"Nonlethal mode removed the warning");
        h.runAfterDelay(21,()->{
            runtime.tick(heart);h.assertTrue(player.isAlive(),"Disabled punishment killed the player");
            m.edit("arenaDeath",1,"");runtime.tick(heart);
        });
        h.runAfterDelay(42,()->{
            runtime.tick(heart);h.assertTrue(player.isAlive(),"The nonlethal countdown ended the independent player death countdown too soon");
            m.edit("countdown",70,"");
            runtime.tick(heart);
        });
        h.runAfterDelay(62,()->{
            runtime.tick(heart);h.assertTrue(!player.isAlive(),"Enabled punishment did not kill after its full countdown");
            runtime.clear();h.getLevel().players().remove(player);h.succeed();
        });
    }
    @GameTest(template="combat",timeoutTicks=20)
    public static void LegacyCountdownMigratesWithoutRelinkingPlayerDeath(GameTestHelper h) {
        var legacy = new VirtualModule("arena").save();
        var settings = legacy.getCompound("Settings");
        settings.putDouble("countdown", 180);
        settings.remove("arenaPlayerDeathCountdown");
        var migrated = VirtualModule.load(legacy);
        h.assertTrue(migrated.value("countdown") == 180 && migrated.value("arenaPlayerDeathCountdown") == 180,
                "The legacy countdown was not preserved for both warning modes");
        migrated.edit("countdown", 140, "");
        var reloaded = VirtualModule.load(migrated.save());
        h.assertTrue(reloaded.value("countdown") == 140 && reloaded.value("arenaPlayerDeathCountdown") == 180,
                "Modern saves must keep both countdowns independent");
        h.succeed();
    }

    @GameTest(template="combat", batch="arena_shape_settings_and_warnings")
    public static void SquareIncludesCornersAndKeepsCircularPlayerWarnings(GameTestHelper h) {
        var heart = fixture(h);
        var module = arena(heart);
        var level = h.getLevel();
        var center = ModuleArena.corePosition(heart);
        var dimension = level.dimension().location().toString();
        var author = TestActors.creative(level);
        author.setPos(center);

        var player = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ArenaShapeTester"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(center);
        level.players().add(player);
        module.edit("warningRange", 4, "");
        module.edit("arenaDeath", 0, "");
        module.arenaCenter(center.add(4, 0, 4), dimension);

        var runtime = new ModuleArena();
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            h.assertTrue(module.value("arenaShape") == 0, "The arena no longer defaults to a circle");
            var menu = new HeartDashboardMenu(1, author.getInventory(), heart);
            var packet = new ModuleNetworking.Action(1, heart.modules().revision(), module.id(), "edit", "arenaShape", 1, "");
            ModuleNetworking.Action.CODEC.encode(buffer, packet);
            var decoded = ModuleNetworking.Action.CODEC.decode(buffer);
            h.assertTrue(menu.action(author, decoded.revision(), decoded.module(), decoded.operation(),
                            decoded.key(), decoded.value(), decoded.text()) && module.value("arenaShape") == 1,
                    "Arena shape edit did not reach the server menu");

            module.edit("arenaShape", 0, "");
            heart.tryActivate(author);
            runtime.tick(heart);
            h.assertTrue(runtime.warning(module.id()), "The default circle did not warn at the square corner");

            module.edit("arenaShape", 1, "");
            runtime.tick(heart);
            h.assertTrue(!runtime.warning(module.id()), "The square excluded its exact corner");
            module.arenaCenter(center.add(4.25, 0, 0), dimension);
            runtime.tick(heart);
            h.assertTrue(runtime.warning(module.id()), "The square accepted a point beyond its X edge");

            player.setPos(center.add(4, 0, 4));
            runtime.tick(heart);
            h.assertTrue(!runtime.warning(module.id()), "The player warning radius became square");
            player.setPos(center);
            module.arenaCenter(center.add(0, 30, -4), dimension);
            runtime.tick(heart);
            h.assertTrue(!runtime.warning(module.id()), "The horizontal square rejected its exact Z edge");

            var saved = module.save();
            var restored = VirtualModule.load(saved);
            h.assertTrue(restored.value("arenaShape") == 1 && restored.value("arenaRadius") == 4
                            && restored.arenaCenter().equals(module.arenaCenter())
                            && ModulePreset.create(saved).value("arenaShape") == 1,
                    "The square shape or arena settings were lost on save or in a preset");
            saved.getCompound("Settings").remove("arenaShape");
            var legacy = VirtualModule.load(saved);
            h.assertTrue(legacy.value("arenaShape") == 0 && legacy.value("arenaRadius") == 4
                            && legacy.value("arenaDeath") == 0 && legacy.value("warningRange") == 4,
                    "Old arena settings did not preserve their circle or other options");
        } finally {
            buffer.release();
            runtime.clear();
            level.players().remove(player);
        }
        h.succeed();
    }

    @GameTest(template="combat",timeoutTicks=40)
    public static void ForcedReturnRestoresHealthAndOverridesPlayerWarning(GameTestHelper h) {
        var heart=fixture(h);
        // GameTest randomly places structures millions of blocks away; use a local physics pose
        // so float coordinate quantization does not mask the Arena return behavior.
        var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),heart.getBlockPos());
        dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(h.getLevel()).getPipeline()
                .teleport(body,new org.joml.Vector3d(0,64,0),new org.joml.Quaterniond(body.logicalPose().orientation()));
        h.runAfterDelay(3,()->{
            var m=arena(heart);
            heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.damage(25);
            var nearby=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ArenaResetTester"));
            nearby.setGameMode(GameType.SURVIVAL);nearby.setPos(ModuleArena.corePosition(heart));
            h.getLevel().players().add(nearby);
            var runtime=new ModuleArena();runtime.tick(heart);
            h.assertTrue(runtime.warning(m.id()),"Nearby player did not receive the initial warning");
            m.edit("arenaForceReturn",1,"");var saved=VirtualModule.load(m.save());
            h.assertTrue(saved.value("arenaDeath")==1&&saved.value("arenaForceReturn")==1,"Arena toggles did not persist");
            runtime.tick(heart);
            h.getLevel().players().remove(nearby);
            h.assertTrue(nearby.isAlive(),"Forced return killed the nearby player");
            h.assertTrue(heart.health().current()==heart.health().maximum(),"Successful forced return did not restore full HP");
            h.assertTrue(!runtime.warning(m.id()),"Forced return retained player punishment");
            h.runAfterDelay(5,()->{
                h.assertTrue(ModuleArena.horizontalDistance(ModuleArena.corePosition(heart),m.arenaCenter())<0.2,"Creature did not return to its marked center");
                heart.damage(heart.health().maximum());heart.restoreFullHealth();
                h.assertTrue(heart.health().current()==0,"Arena healing revived a dying creature");h.succeed();
            });
        });
    }
}
