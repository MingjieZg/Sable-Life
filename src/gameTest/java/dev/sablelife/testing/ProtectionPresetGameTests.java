package dev.sablelife.testing;
import dev.sablelife.heart.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("sablelife_terrain_tests") @PrefixGameTestTemplate(false)
public final class ProtectionPresetGameTests {
 @GameTest(template="combat") public static void SurfacePresetMergesDeduplicatesPersistsAndStaysOptional(GameTestHelper h){
  var s=new TerrainBreakSettings();h.assertTrue(s.entries().isEmpty(),"Preset became mandatory");s.edit("crushProtectAdd",0,"minecraft:oak_planks");s.edit("crushProtectAdd",0,"minecraft:stone");
  h.assertTrue(s.edit("crushProtectPreset",0,"")&&s.entries().size()==21,"Preset did not merge 20 blocks with existing entries");h.assertTrue(s.protects(Blocks.GRASS_BLOCK.defaultBlockState())&&s.protects(Blocks.GRAVEL.defaultBlockState())&&s.protects(Blocks.STONE.defaultBlockState())&&s.protects(Blocks.OAK_PLANKS.defaultBlockState()),"Natural or existing protection missing");h.assertTrue(!s.edit("crushProtectPreset",0,"")&&!s.protects(Blocks.COBBLESTONE.defaultBlockState()),"Preset duplicates or unrelated buildings protected");
  var restored=new TerrainBreakSettings();restored.load(s.save());h.assertTrue(restored.entries().equals(s.entries()),"Preset did not persist");h.assertTrue(!s.edit("crushProtectAdd",0,"minecraft:stick"),"Non-block item ID accepted");
  for(var b:net.minecraft.core.registries.BuiltInRegistries.BLOCK){s.edit("crushProtectAdd",0,net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).toString());if(s.entries().size()==TerrainBreakSettings.LIMIT)break;}s.edit("crushProtectPreset",0,"");h.assertTrue(s.entries().size()==128,"Preset exceeded protection cap");h.succeed();
 }
}
