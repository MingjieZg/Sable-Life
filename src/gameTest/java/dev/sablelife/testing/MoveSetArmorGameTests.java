package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class MoveSetArmorGameTests {
 @GameTest(template="combat",batch="move_part_armor",timeoutTicks=30)
 public static void PartModifierCanBeSelectedAndRunsOnlyInActionWindow(GameTestHelper h){run(h,false);}
 @GameTest(template="combat",batch="move_whole_armor",timeoutTicks=30)
 public static void WholeModifierCanBeSelectedAndRunsOnlyInActionWindow(GameTestHelper h){run(h,true);}
 private static void run(GameTestHelper h,boolean whole){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,30,4));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));var c=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
  int silentChannel = c.wireless().add();
  c.wireless().channel(silentChannel).set(0, 0);
  var m=c.modules().add("armor");
  if(whole)m.edit("armorWholeBody",1,"");else m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));var row=m.armorRules().add();row.edit("modifier",-50);row.edit("when",1);
  var table=c.modules().add("move_set");table.moveSet().edit("add",0,"",c.modules());h.assertTrue(table.moveSet().edit("action_add",0,"0|"+m.id(),c.modules())&&m.value("trigger")==4,"Damage modifier filtered or trigger refused");
  table.moveSet().edit("action_start",.1,"0|0",c.modules());table.moveSet().edit("action_duration",.15,"0|0",c.modules());var cfg=table.moveSet().get(0).config();cfg.edit("dispatchBusy",1,"");cfg.edit("dispatchCooldown",100,"");cfg.edit("target",1,"");cfg.edit("dispatchFar",16,"");
  var cow=net.minecraft.world.entity.EntityType.COW.create(level);cow.setPos(ModuleArena.corePosition(c).add(2,0,2));cow.setNoAi(true);cow.setNoGravity(true);level.addFreshEntity(cow);c.tryActivate(TestActors.creative(level));c.modules().tick(c);
  h.assertTrue(ModuleMoveSet.status(table.moveSet().get(0),c).equals("ready")&&ModuleArmor.damage(c,c.getBlockPos(),20)==20,"Modifier applied before action start or move unavailable");
  h.runAfterDelay(3,()->h.assertTrue(ModuleArmor.damage(c,c.getBlockPos(),20)==10,"Move Set did not activate damage modifier inside action window"));
  h.runAfterDelay(8,()->{h.assertTrue(ModuleArmor.damage(c,c.getBlockPos(),20)==20,"Move Set modifier stayed on after action ended");cow.discard();h.succeed();});
 }
}
