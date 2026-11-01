package dev.sablelife.testing;
import dev.sablelife.module.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class HitCounterExternalGameTests {
 @GameTest(template="combat",timeoutTicks=25) public static void ExternalOnlyCounterCanAlsoPublishInternalAndDisableCleanly(GameTestHelper h){
  var c=SlashGuardGameTests.core(h);var attack=c.modules().add("slash");var m=c.modules().add("hit_counter");m.edit("hitThreshold",2,"");m.edit("hitPower",11,"");m.edit("hitPulse",12,"");m.redBlue(false,new ItemStack(Items.EMERALD),h.getLevel().registryAccess());m.redBlue(true,new ItemStack(Items.DIAMOND),h.getLevel().registryAccess());
  var p=h.absolutePos(new BlockPos(5,2,5));var level=h.getLevel();level.setBlockAndUpdate(p.below(),Blocks.IRON_BLOCK.defaultBlockState());level.setBlockAndUpdate(p,com.simibubi.create.AllBlocks.REDSTONE_LINK.getDefaultState().setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.RECEIVER,true).setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.FACING,Direction.UP));
  var receiver=(com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity)level.getBlockEntity(p);receiver.loadWithComponents(new net.minecraft.nbt.CompoundTag(),level.registryAccess());receiver.tick();var link=receiver.getBehaviour(com.simibubi.create.content.redstone.link.LinkBehaviour.TYPE);link.setFrequency(true,new ItemStack(Items.EMERALD));link.setFrequency(false,new ItemStack(Items.DIAMOND));
  var preset=ModulePreset.create(m.save());h.assertTrue(preset.hasRedBlue()&&preset.value("hitThreshold")==2&&ModuleFields.forType("hit_counter").stream().anyMatch(f->f.key().equals("redstoneFrequency")),"Frequency UI or preset missing");
  c.tryActivate(TestActors.creative(level));c.modules().hitCounter.record(c,attack.id(),1);c.modules().hitCounter.record(c,attack.id(),1);c.modules().tick(c);
  h.runAfterDelay(3,()->{c.modules().tick(c);h.assertTrue(receiver.getReceivedSignal()==11&&m.value("hitChannel")==0,"External-only hit output missing");int n=c.wireless().add();c.wireless().channel(n).set(6,3);c.wireless().channel(n).set(0,1);m.edit("hitChannel",n+1,"");});
  h.runAfterDelay(5,()->{c.modules().tick(c);h.assertTrue(receiver.getReceivedSignal()==11&&c.wireless().output(0)==11,"Internal and external outputs did not coexist");m.edit("enabled",0,"");});
  h.runAfterDelay(7,()->{c.modules().tick(c);h.assertTrue(receiver.getReceivedSignal()==0&&c.wireless().output(0)==0,"Disabled counter left stale external/internal power");h.succeed();});
 }
}
