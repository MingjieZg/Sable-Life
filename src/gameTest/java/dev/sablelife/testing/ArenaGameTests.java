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
    @GameTest(template="combat",timeoutTicks=60)
    public static void DeathTogglePreservesWarningsAndCanBeReenabled(GameTestHelper h) throws Exception {
        var heart=fixture(h); var m=arena(heart);
        h.assertTrue(m.value("arenaDeath")==1&&m.value("arenaForceReturn")==0,"Old Arena behavior must remain the default");
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
            runtime.tick(heart);h.assertTrue(!player.isAlive(),"Enabled punishment did not kill after its full countdown");
            runtime.clear();h.getLevel().players().remove(player);h.succeed();
        });
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
