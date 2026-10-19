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
@GameTestHolder("sablelife_defense_tests") @PrefixGameTestTemplate(false)
public final class WholeArmorGameTests {
 private static HeartBlockEntity core(GameTestHelper h,int x){var l=h.getLevel();var p=h.absolutePos(new BlockPos(x,30,4));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}

 private static VirtualModule whole(HeartBlockEntity c){var m=c.modules().add("armor");m.edit("armorWholeBody",1,"");var row=m.armorRules().add();row.edit("modifier",-50);c.wireless().add();c.wireless().channel(0).set(0,1);c.wireless().channel(0).set(6,3);return m;}
 @GameTest(template="combat",batch="whole_armor_scope")
 public static void WholeModeUsesSharedRowsAndNeverProtectsForeignBody(GameTestHelper h){
  var c=core(h,2);var foreign=core(h,7);var m=whole(c);c.tryActivate(TestActors.creative(h.getLevel()));
  h.assertTrue(HeartModules.status(m,c).equals("valid")&&m.coreModule(),"Whole mode still requires Mark");c.wireless().programOutputs("whole",Map.of(0,15));
  h.assertTrue(ModuleArmor.damage(c,c.getBlockPos(),20)==10&&ModuleArmor.damage(c,c.getBlockPos().east(),20)==10,"Whole rules missed body blocks");
  m.anchors(List.of(ModuleAnchor.create(foreign,foreign.getBlockPos(),Direction.UP)));h.assertTrue(ModuleArmor.damage(c,c.getBlockPos(),20)==10&&ModuleArmor.damage(c,foreign.getBlockPos(),20)==20,"Whole rules followed Mark or leaked to foreign core");
  m.detach();m.invalidate();h.assertTrue(ModuleArmor.damage(c,c.getBlockPos(),20)==10,"Lost Mark disabled whole rules");
  m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));m.edit("armorWholeBody",0,"");h.assertTrue(ModuleArmor.damage(c,c.getBlockPos(),20)==10&&m.armorRules().rows().getFirst().modifier()==-50,"Scope toggle changed shared rules");h.succeed();
 }
 @GameTest(template="combat",batch="whole_armor_signals")
 public static void MultipleWholeRowsSelectPhasesAndKeepOuterGateAndOverlap(GameTestHelper h){
  var c=core(h,4);var m=whole(c);c.wireless().add();c.wireless().channel(1).set(0,1);c.wireless().channel(1).set(6,3);var r=m.armorRules().rows().getFirst();var second=m.armorRules().add();second.edit("channel",2);second.edit("modifier",100);c.tryActivate(TestActors.creative(h.getLevel()));var hit=c.getBlockPos();
  h.assertTrue(ModuleArmor.damage(c,hit,20)==20,"Unpowered rule applied");c.wireless().programOutputs("whole",Map.of(0,15));h.assertTrue(ModuleArmor.damage(c,hit,20)==10,"First whole phase missing");
  c.wireless().programOutputs("whole",Map.of(1,15));h.assertTrue(ModuleArmor.damage(c,hit,20)==40,"Second whole phase missing");c.wireless().programOutputs("whole",Map.of(0,15,1,15));h.assertTrue(ModuleArmor.damage(c,hit,20)==10,"Whole rows stacked");
  r.edit("when",1);c.wireless().programOutputs("whole",Map.of(0,15));h.assertTrue(ModuleArmor.damage(c,hit,20)==20,"Inverse row did not pause");c.wireless().programOutputs("whole",Map.of());h.assertTrue(ModuleArmor.damage(c,hit,20)==10,"Inverse row did not resume");
  r.edit("channel",0);h.assertTrue(ModuleArmor.damage(c,hit,20)==20,"Unbound inverse row protected");r.edit("channel",3);h.assertTrue(ModuleArmor.damage(c,hit,20)==20,"Missing channel protected");r.edit("channel",1);
  m.edit("trigger",2,"");m.edit("channel",1,"");h.assertTrue(ModuleArmor.damage(c,hit,20)==20,"Rule bypassed outer trigger");m.edit("trigger",0,"");
  var part=c.modules().add("armor");part.anchors(List.of(ModuleAnchor.create(c,hit,Direction.UP)));var row=part.armorRules().add();row.edit("when",1);row.edit("modifier",-75);h.assertTrue(ModuleArmor.damage(c,hit,20)==5,"Part and whole stacked or lost strongest protection");
  part.edit("enabled",0,"");r.edit("enabled",0);h.assertTrue(ModuleArmor.damage(c,hit,20)==20,"Disabled row remained active");second.edit("when",1);h.assertTrue(ModuleArmor.damage(c,hit,20)==40,"Amplification phase failed");m.edit("enabled",0,"");h.assertTrue(ModuleArmor.damage(c,hit,20)==20,"Disabled module remained active");h.succeed();
 }
 @GameTest(template="combat",batch="whole_armor_data")
 public static void SharedRulesSaveUnbindAndRemainEditableInWholeMode(GameTestHelper h){
  var c=core(h,4);var m=whole(c);var row=m.armorRules().rows().getFirst();row.edit("modifier",-75);row.edit("channel",4);row.edit("source",1);row.edit("when",1);
  var saved=VirtualModule.load(m.save());var loaded=saved.armorRules().rows().getFirst();h.assertTrue(saved.value("armorWholeBody")==1&&loaded.channel()==4&&loaded.source()==1&&loaded.when()==1&&loaded.modifier()==-75,"Shared rows did not save");
  h.assertTrue(ModulePreset.create(m.save()).armorRules().rows().getFirst().channel()==0,"Portable preset retained signal binding");m.wirelessChannelDeleted(1);h.assertTrue(row.channel()==3,"Earlier deletion shifted wrong");m.wirelessChannelDeleted(2);h.assertTrue(row.channel()==0,"Deleted channel redirected shield");
  h.assertTrue(!m.edit("armorWholeModifier",-50,"")&&!m.edit("armorWholeBody",.5,""),"Obsolete fields or invalid scope accepted");
  var p=TestActors.creative(h.getLevel());p.setPos(ModuleArena.corePosition(c));var menu=new ArmorRuleMenu(2,p.getInventory(),c,m,null);h.assertTrue(menu.snapshot().getBoolean("Editable")&&menu.action(p,c.modules().revision(),row.id(),"modifier",-90),"Whole rules were not editable");m.edit("armorWholeBody",0,"");h.assertTrue(row.modifier()==-90&&menu.action(p,c.modules().revision(),row.id(),"modifier",-80),"Scope toggle lost edits");
  var old=m.save();old.getCompound("Settings").putDouble("armorWholeModifier",-100);h.assertTrue(VirtualModule.load(old).armorRules().rows().size()==1,"Obsolete single config migrated despite opt-out");h.succeed();
 }
}
