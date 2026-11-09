package dev.sablelife.testing;

import dev.sablelife.SableLife;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class SurvivalCoreIdGameTests {
    @GameTest(template="combat",batch="survival_heart_registry_id")
    public static void RegistryRecipeLootAndSavedItemUseSurvivalHeartCore(GameTestHelper h) {
        var id=ResourceLocation.parse("sablelife:survival_heart_core");
        var retired=ResourceLocation.parse("sablelife:survival_life_core");
        var block=SableLife.SURVIVAL_LIFE_CORE.get();var item=SableLife.SURVIVAL_LIFE_CORE_ITEM.get();
        h.assertTrue(id.equals(BuiltInRegistries.BLOCK.getKey(block))&&id.equals(BuiltInRegistries.ITEM.getKey(item)),"Block/item kept old registry ID");
        h.assertTrue(!BuiltInRegistries.BLOCK.containsKey(retired)&&!BuiltInRegistries.ITEM.containsKey(retired),"Unexpected legacy item alias");
        var saved=(net.minecraft.nbt.CompoundTag)new ItemStack(item).save(h.getLevel().registryAccess());
        h.assertTrue(saved.getString("id").equals(id.toString()),"Saved stack uses wrong ID");
        var recipe=h.getLevel().getRecipeManager().byKey(id);
        h.assertTrue(recipe.isPresent()&&recipe.get().value().getResultItem(h.getLevel().registryAccess()).is(item),"Renamed recipe missing or output mismatched");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(retired).isEmpty(),"Legacy recipe left active");
        var pos=h.absolutePos(new BlockPos(2,2,2));h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
        var drops=Block.getDrops(block.defaultBlockState(),h.getLevel(),pos,h.getLevel().getBlockEntity(pos));
        h.assertTrue(drops.size()==1&&drops.getFirst().is(item),"Renamed loot table did not drop the correct core");
        h.assertTrue(block.defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE),"Renamed core lost mining tag");
        h.succeed();
    }
}
