package dev.sablelife.testing;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import dev.sablelife.compat.photomancy.PhotomancyRespawnBridge;
@GameTestHolder("sablelife_respawn_replay_tests") @PrefixGameTestTemplate(false)
public final class ArenaAddonReplayGameTests {
 @GameTest(template="combat",timeoutTicks=600) public static void CapturedAddonTemplateCompletesNativeBuild(GameTestHelper h)throws Exception {
  var path=System.getProperty("sablelife.arenaReplayTemplate");h.assertTrue(path!=null,"Requires private replay fixture and arenaAddonTests profile");
  var template=NbtIo.readCompressed(java.nio.file.Path.of(path),NbtAccounter.unlimitedHeap());
  var build=PhotomancyRespawnBridge.create(template,new CompoundTag(),Vec3.atCenterOf(h.absolutePos(new net.minecraft.core.BlockPos(10,40,10))));var done=new boolean[]{false};
  h.onEachTick(()->{if(done[0])return;var status=build.step(h.getLevel());h.assertTrue(status.equals("DONE"),"New addon build did not use one-call native placement");if(status.equals("DONE")){done[0]=true;h.assertTrue(build.heart(h.getLevel())!=null,"Completed addon template has no core");var core=build.heart(h.getLevel());var expected=dev.rew1nd.sableschematicapi.blueprint.SableBlueprint.load(template).subLevels().size();
   h.runAfterDelay(20,()->{
    var root=dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),core.getBlockPos());
    h.assertTrue(root!=null&&dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(root).size()==expected,"Addon joints did not reconnect all recorded parts");
    h.assertTrue(core.activateForRespawn(),"Restored connected template cannot activate");build.captureOwnership(core);
    var ids=build.bodies();h.assertTrue(dev.sablelife.ownership.BodyOwnership.get(h.getLevel()).bodyIds(h.getLevel(),core.creatureId()).size()==expected,"Addon parts lack ownership");core.configure(100,0);core.forceDeath();
    h.succeedWhen(()->{h.assertTrue(dev.sablelife.ownership.LifeLifecycle.get(h.getLevel().getServer()).finished(core.creatureId()),"Waiting for addon cleanup");var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(h.getLevel());for(var id:ids)h.assertTrue(container.getSubLevel(id)==null||container.getSubLevel(id).isRemoved(),"Addon part survived death cleanup");});
   });}});
 }
}
