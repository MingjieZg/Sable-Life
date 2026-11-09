package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("sablelife_release_candidate_tests") @PrefixGameTestTemplate(false)
public final class DataCopierGameTests {
    private static HeartBlockEntity core(GameTestHelper h, Block block, int x) {
        var pos=h.absolutePos(new BlockPos(x,3,3));
        h.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());
        return (HeartBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    private static FakePlayer player(GameTestHelper h, GameType mode) {
        var player=TestActors.creative(h.getLevel());player.setGameMode(mode);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(SableLife.DATA_COPIER.get()));
        return player;
    }
    private static InteractionResult use(FakePlayer player, HeartBlockEntity core) {
        player.setPos(core.getBlockPos().getCenter());
        var context=new UseOnContext(player,InteractionHand.MAIN_HAND,
                new BlockHitResult(core.getBlockPos().getCenter(),Direction.UP,core.getBlockPos(),false));
        return SableLife.DATA_COPIER.get().onItemUseFirst(player.getMainHandItem(),context);
    }
    private static CompoundTag clipboard(FakePlayer player) {
        return player.getMainHandItem().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY)
                .copyTag().getCompound("SableLifeConfiguration");
    }
    private static void balance(HeartBlockEntity core, float current, long energy) {
        var saved=core.saveWithoutMetadata(core.getLevel().registryAccess());
        saved.putFloat("Health",current);saved.putLong("SurvivalTokens",energy);
        core.loadWithComponents(saved,core.getLevel().registryAccess());
    }

    @GameTest(template="combat")
    public static void SurvivalCopierPreservesTargetEnergyAndPaidLife(GameTestHelper h) {
        var source=core(h,SableLife.SURVIVAL_LIFE_CORE.get(),2);
        var target=core(h,SableLife.SURVIVAL_LIFE_CORE.get(),5);
        source.configure(200,0);balance(source,150,9000);balance(target,25,70);
        source.modules().add("arrow");
        var player=player(h,GameType.SURVIVAL);
        h.assertTrue(use(player,source)==InteractionResult.CONSUME,"Survival player could not copy survival core");
        var copied=clipboard(player);
        h.assertTrue(!copied.contains("SurvivalTokens")&&!copied.contains("Health")&&!copied.contains("LifeState"),"Clipboard contains runtime balances");
        h.assertTrue(use(player,target)==InteractionResult.CONSUME,"Survival player could not paste survival core");
        h.assertTrue(target.health().maximum()==200&&target.health().current()==25&&target.survivalTokenBalance()==70,
                "Paste copied energy or restored paid health");
        h.assertTrue(source.health().current()==150&&source.survivalTokenBalance()==9000&&target.modules().all().size()==1,
                "Copy mutated source or lost modules");
        player.setShiftKeyDown(true);
        h.assertTrue(use(player,target)==InteractionResult.CONSUME&&clipboard(player).getFloat("MaxHealth")==200,
                "Shift-copy failed for survival player");
        h.succeed();
    }

    @GameTest(template="combat")
    public static void RedstoneCopierWorksInSurvival(GameTestHelper h) {
        var source=core(h,SableLife.REDSTONE_CORE.get(),2);
        var target=core(h,SableLife.REDSTONE_CORE.get(),5);
        source.modules().add("wireless_redstone");
        var player=player(h,GameType.SURVIVAL);
        h.assertTrue(use(player,source)==InteractionResult.CONSUME&&use(player,target)==InteractionResult.CONSUME,
                "Survival copy/paste failed for redstone core");
        h.assertTrue(target.isControlCore()&&target.modules().all().size()==1
                &&target.modules().all().getFirst().type().equals("wireless_redstone"),"Redstone configuration lost");
        h.succeed();
    }

    @GameTest(template="combat",batch="copier_config")
    public static void OrdinaryCoreCopierFollowsServerEditingSwitch(GameTestHelper h) {
        var option=(ModConfigSpec.BooleanValue)SableLifeServerConfig.SPEC.getValues()
                .get(List.of("allowEditHeartCoreUnderSurvivalMode"));
        boolean previous=option.get();
        try {
            var source=core(h,SableLife.HEART.get(),2);
            var target=core(h,SableLife.HEART.get(),5);source.modules().add("gun");
            option.set(false);
            var creative=player(h,GameType.CREATIVE);
            h.assertTrue(use(creative,source)==InteractionResult.CONSUME,"Creative copy regressed");
            var survival=player(h,GameType.SURVIVAL);
            h.assertTrue(use(survival,source)==InteractionResult.FAIL&&clipboard(survival).isEmpty(),"Disabled ordinary editing allowed copy");
            survival.setItemInHand(InteractionHand.MAIN_HAND,creative.getMainHandItem().copy());
            h.assertTrue(use(survival,target)==InteractionResult.FAIL&&target.modules().all().isEmpty(),"Loaded copier bypassed editing switch");
            option.set(true);
            var allowed=player(h,GameType.SURVIVAL);
            h.assertTrue(use(allowed,source)==InteractionResult.CONSUME&&use(allowed,target)==InteractionResult.CONSUME,
                    "Enabled ordinary editing did not allow copy/paste");
            h.assertTrue(target.modules().all().getFirst().type().equals("gun"),"Enabled paste lost module");
        } finally { option.set(previous); }
        h.succeed();
    }

    @GameTest(template="combat")
    public static void CopierRejectsSpectatorsAndActivePasteWithoutMutatingClipboard(GameTestHelper h) {
        for(var block:List.of(SableLife.HEART.get(),SableLife.REDSTONE_CORE.get(),SableLife.SURVIVAL_LIFE_CORE.get())) {
            var source=core(h,block,2);source.modules().add("particles");
            var creative=player(h,GameType.CREATIVE);
            h.assertTrue(use(creative,source)==InteractionResult.CONSUME,"Creative copy failed");
            var stored=clipboard(creative).copy();
            if(!source.isControlCore()) {
                if(source.isSurvivalCore())balance(source,50,0);
                h.assertTrue(source.health().activate(),"Fixture did not enter active state");
                h.assertTrue(use(creative,source)==InteractionResult.FAIL&&clipboard(creative).equals(stored),"Active paste modified data");
            }
            var spectator=player(h,GameType.SPECTATOR);
            h.assertTrue(use(spectator,source)==InteractionResult.FAIL&&clipboard(spectator).isEmpty(),"Spectator copied a core");
        }
        var survival=core(h,SableLife.SURVIVAL_LIFE_CORE.get(),5);
        var adventure=player(h,GameType.ADVENTURE);
        h.assertTrue(use(adventure,survival)==InteractionResult.FAIL,"Copier bypassed survival core mayBuild permission");
        h.succeed();
    }

    @GameTest(template="combat")
    public static void CrossCorePasteRetainsDestinationCapabilities(GameTestHelper h) {
        var source=core(h,SableLife.HEART.get(),2);
        source.modules().add("gun");
        var blast=source.modules().add("sphere_blast");blast.edit("releasePosition",1,"");
        var player=player(h,GameType.CREATIVE);
        h.assertTrue(use(player,source)==InteractionResult.CONSUME,"Cross-core source copy failed");
        player.setGameMode(GameType.SURVIVAL);
        var survival=core(h,SableLife.SURVIVAL_LIFE_CORE.get(),5);
        h.assertTrue(use(player,survival)==InteractionResult.CONSUME,"Cross-core survival paste failed");
        h.assertTrue(survival.modules().all().stream().filter(m->m.type().equals("sphere_blast"))
                .allMatch(m->m.value("releasePosition")==0),"Paste bypassed survival target-position restriction");
        var redstone=core(h,SableLife.REDSTONE_CORE.get(),8);
        h.assertTrue(use(player,redstone)==InteractionResult.CONSUME,"Cross-core redstone paste failed");
        h.assertTrue(redstone.modules().all().stream().noneMatch(m->m.enabled()),"Paste enabled attacks on redstone core");
        h.succeed();
    }
}
