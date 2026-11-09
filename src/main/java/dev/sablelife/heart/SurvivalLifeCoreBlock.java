package dev.sablelife.heart;

import com.mojang.serialization.MapCodec;
import dev.sablelife.SableLife;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.TickTask;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** Uses the same Sable block entity and copycat casing as the Life Core. */
@EventBusSubscriber(modid = SableLife.MOD_ID)
public final class SurvivalLifeCoreBlock extends HeartBlock {
    public static final MapCodec<SurvivalLifeCoreBlock> CODEC=simpleCodec(SurvivalLifeCoreBlock::new);

    public SurvivalLifeCoreBlock(Properties properties){super(properties);}

    private static final String CLAIM = "SableLifeTokenClaim";
    private static final String BALANCE = "SableLifeTokenDisplay";

    /** Only call after a terminal item-destruction path, never general discard/pickup. */
    public static void retireDestroyedClaim(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity item) || !item.isRemoved()
                || !(item.level() instanceof ServerLevel level)) return;
        var stack=item.getItem();
        if(!stack.is(SableLife.SURVIVAL_LIFE_CORE_ITEM.get()))return;
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(tag.hasUUID(CLAIM))SurvivalTokenTransfers.get(level.getServer()).redeem(tag.getUUID(CLAIM));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attachTokenClaim(BlockDropsEvent event) {
        if (!(event.getState().getBlock() instanceof SurvivalLifeCoreBlock)
                || !(event.getBlockEntity() instanceof HeartBlockEntity core)
                || !(event.getBreaker() instanceof Player player) || player.isCreative()
                || !event.getLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) return;
        for (var entity : event.getDrops()) {
            var stack = entity.getItem();
            if (!stack.is(SableLife.SURVIVAL_LIFE_CORE_ITEM.get())) continue;
            long balance = core.takeTransferredTokens();
            if (balance <= 0) return;
            var id = SurvivalTokenTransfers.get(event.getLevel().getServer()).issue(balance);
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putUUID(CLAIM, id);
                tag.putLong(BALANCE, balance);
            });
            return;
        }
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof HeartBlockEntity core)) return;
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(CLAIM)) return;
        var id = tag.getUUID(CLAIM);
        var minecraft = server.getServer();
        // A protection mod can roll placement back after setPlacedBy returns.
        minecraft.tell(new TickTask(minecraft.getTickCount(), () -> {
            if (server.getBlockEntity(pos) == core && !core.isRemoved())
                core.restoreTransferredTokens(SurvivalTokenTransfers.get(minecraft).redeem(id));
        }));
    }

    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        long balance = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong(BALANCE);
        tooltip.add(Component.translatable("survival_tokens.sablelife.remaining", balance).withStyle(ChatFormatting.GOLD));
    }

    @Override protected MapCodec<SurvivalLifeCoreBlock> codec(){return CODEC;}
}
