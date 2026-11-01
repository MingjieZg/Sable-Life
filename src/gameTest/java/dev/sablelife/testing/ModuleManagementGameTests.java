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
public final class ModuleManagementGameTests {
 private static HeartBlockEntity fixture(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
 @GameTest(template="combat",batch="module_search")
 public static void SearchMatchesNamesTypesLocalizedNamesAndOriginalNumber(GameTestHelper h){var m=new VirtualModule("linear_movement");m.edit("name",0,"Left arm return");h.assertTrue(ModuleSearch.matches(m,"直线移动",60," LEFT RETURN ")&&ModuleSearch.matches(m,"直线移动",60,"linear_movement")&&ModuleSearch.matches(m,"直线移动",60,"直线")&&ModuleSearch.matches(m,"直线移动",60,"#60"),"Search failed name/type/localized/index");h.assertTrue(!ModuleSearch.matches(m,"直线移动",60,"#6")&&!ModuleSearch.matches(m,"直线移动",60,"other")&&ModuleSearch.matches(m,"直线移动",60,""),"Search mismatched exact number/empty query");h.succeed();}
 @GameTest(template="combat",batch="module_copy")
 public static void CopyRetainsMarksRulesAndBindingsWithIndependentIdentity(GameTestHelper h){var c=fixture(h);var m=c.modules().add("armor");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));m.edit("name",0,"Shield");var row=m.armorRules().add();row.edit("modifier",-70);row.edit("channel",4);var tail=c.modules().add("sound");var copy=c.modules().copy(m.id());h.assertTrue(copy!=null&&!copy.id().equals(m.id())&&copy.anchors().equals(m.anchors())&&copy.name().equals(m.name())&&copy.armorRules().rows().getFirst().modifier()==-70&&c.modules().all().get(1)==copy&&c.modules().all().get(2)==tail,"Copy lost Mark/config or insert order");copy.armorRules().rows().getFirst().edit("modifier",-20);copy.edit("name",0,"Other shield");h.assertTrue(m.armorRules().rows().getFirst().modifier()==-70&&m.name().equals("Shield"),"Copy edits mutated original");h.succeed();}
 @GameTest(template="combat",batch="module_copy_refs")
 public static void CopiedMoveSetRetainsStableReferencedModules(GameTestHelper h){var modules=new HeartModules();var target=modules.add("sound");var m=modules.add("move_set");m.moveSet().edit("add",0,"",modules);h.assertTrue(m.moveSet().edit("action_add",0,"0|"+target.id(),modules),"Move action fixture invalid");var copy=modules.copy(m.id());h.assertTrue(copy.moveSet().moves().getFirst()!=m.moveSet().moves().getFirst()&&copy.moveSet().moves().getFirst().actions().getFirst().module().equals(target.id()),"Move references stripped or shared config object");while(modules.add("sound")!=null){}h.assertTrue(modules.copy(m.id())==null,"Copy bypassed module limit");h.succeed();}
 @GameTest(template="combat",batch="module_copy_menu")
 public static void ServerCopyChecksRevisionAndSelectsNewModule(GameTestHelper h){var c=fixture(h);var m=c.modules().add("sound");var p=TestActors.creative(h.getLevel());p.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));var menu=new HeartDashboardMenu(1,p.getInventory(),c);int revision=c.modules().revision();h.assertTrue(!menu.action(p,revision-1,m.id(),"copy","",0,""),"Stale copy accepted");h.assertTrue(menu.action(p,revision,m.id(),"copy","",0,""),"Server copy failed");var copy=c.modules().all().get(1);h.assertTrue(menu.snapshot().getUUID("Selected").equals(copy.id()),"Copy selection not synchronized");h.succeed();}
}
