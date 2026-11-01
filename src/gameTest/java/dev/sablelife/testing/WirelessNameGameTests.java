package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.inventory.ClickType;
import net.neoforged.neoforge.gametest.*;
import java.util.List;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class WirelessNameGameTests {
 @GameTest(template="combat",batch="wireless_channel_names")
 public static void NamesPersistFollowDeletedRowsAndRenameDoesNotResetSignal(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(4,40,4));l.setBlockAndUpdate(p,SableLife.REDSTONE_CORE.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());var author=TestActors.creative(l);author.setPos(SableBodyProbe.toWorld(l,c.getBlockPos().getCenter()));
  for(int i=0;i<9;i++){c.wireless().add();c.wireless().channel(i).name("Channel "+(i+1));}var channel=c.wireless().channel(7);channel.set(0,1);channel.set(2,100);c.wireless().tick(l);long elapsed=c.wireless().elapsed();int power=c.wireless().output(7);var menu=new HeartWirelessMenu(3,author.getInventory(),c,7);
  h.assertTrue(menu.rename(author,7,"  左臂攻击  ")&&menu.channelName(7).equals("左臂攻击")&&menu.channelName(8).equals("Channel 9"),"Rename/current-page metadata mismatch");h.assertTrue(c.wireless().elapsed()==elapsed&&c.wireless().output(7)==power&&power==15,"Rename reset waveform or output");
  h.assertTrue(!menu.rename(author,6,"wrong")&&!menu.rename(author,Double.NaN,"bad")&&!menu.rename(author,7,"x".repeat(41))&&!menu.rename(author,7,"bad\nname"),"Invalid/stale name edit accepted");
  var loaded=WirelessChannel.load(channel.save(l.registryAccess()),l.registryAccess());h.assertTrue(loaded.name().equals("左臂攻击")&&loaded.value(2)==100,"Channel save lost name or signal settings");var legacy=channel.save(l.registryAccess());legacy.remove("Name");h.assertTrue(WirelessChannel.load(legacy,l.registryAccess()).name().isEmpty(),"Legacy channel gained name");
  var restored=(HeartBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(c.getBlockPos(),c.getBlockState(),c.saveWithFullMetadata(l.registryAccess()),l.registryAccess());h.assertTrue(restored.wireless().channel(7).name().equals("左臂攻击")&&c.copyConfiguration().getCompound("Wireless").getList("Channels",10).getCompound(7).getString("Name").equals("左臂攻击"),"Core save/configuration copy lost name");
  h.assertTrue(menu.clickMenuButton(author,14<<16|3)&&menu.pageSize()==3&&menu.selected()==7&&menu.page()==2&&menu.channelName(6).equals("Channel 7")&&menu.channelName(7).equals("左臂攻击"),"Density pagination redirected selected channel or labels");h.assertTrue(c.wireless().elapsed()==elapsed&&c.wireless().output(7)==power,"Layout request reset signal");h.assertTrue(!menu.clickMenuButton(author,14<<16)&&!menu.clickMenuButton(author,14<<16|65),"Invalid sidebar row count accepted");h.assertTrue(menu.clickMenuButton(author,14<<16|64)&&menu.page()==0&&menu.selected()==7&&menu.channelName(8).equals("Channel 9"),"Expanded page lost channel mapping");
  menu.clicked(2,2,ClickType.CLONE,author);h.assertTrue(menu.getCarried().isEmpty(),"Name metadata could be extracted");author.setGameMode(GameType.SURVIVAL);h.assertTrue(menu.rename(author,7,"Survival control"),"Independent survival control lost rename permission");author.setGameMode(GameType.SPECTATOR);h.assertTrue(!menu.rename(author,7,"unauthorized"),"Spectator renamed control channel");author.setGameMode(GameType.CREATIVE);h.assertTrue(menu.rename(author,7,""),"Empty name rejected");h.assertTrue(channel.name().isEmpty(),"Empty name failed to clear");channel.name("Left arm");c.wireless().remove(0);h.assertTrue(c.wireless().channel(6).name().equals("Left arm")&&c.wireless().channel(7).name().equals("Channel 9"),"Deletion shifted labels away from their channels");h.succeed();
 }
}
