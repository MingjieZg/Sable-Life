package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.sablelife.ownership.ArenaRespawns;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_respawn_optional_tests") @PrefixGameTestTemplate(false)
public final class ArenaRespawnOptionalGameTests {
 @GameTest(template="combat") public static void MissingPhotomancyDisablesOnlyRespawnAndCoreStillActivates(GameTestHelper h){
  h.assertTrue(!ArenaRespawns.available(),"Run optional-absence suite with -PphotomancyRuntime=false");var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,40,5));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());var m=c.modules().add("arena");m.arenaCenter(ModuleArena.corePosition(c),l.dimension().location().toString());var actor=new net.neoforged.neoforge.common.util.FakePlayer(l,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"OptionalAdmin")){@Override public boolean canUseGameMasterBlocks(){return isCreative();}};actor.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
  h.assertTrue(!ArenaRespawns.record(actor,c,m)&&!ArenaRespawns.snapshot(c,m.id()).getBoolean("Available"),"Missing API record succeeded");c.tryActivate(actor);h.assertTrue(c.health().state()==dev.sablelife.heart.HeartState.ALIVE,"Optional API absence broke life activation");h.succeed();
 }
}
