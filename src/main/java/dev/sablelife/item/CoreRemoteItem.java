package dev.sablelife.item;

import dev.sablelife.compat.CoreRemoteClaims;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.MarkingSessions;
import dev.sablelife.ownership.LoadedCoreIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class CoreRemoteItem extends Item {
    private static final String BINDING = "SableLifeCoreRemote";
    private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();

    private record Session(HeartBlockEntity core, InteractionHand hand) {}

    public CoreRemoteItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (player == null || !player.isSecondaryUseActive()
                || !(level.getBlockEntity(pos) instanceof HeartBlockEntity core)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !player.canInteractWithBlock(pos, 1)
                || !canAccess(serverPlayer, core)) {
            message(player, "denied");
            return InteractionResult.FAIL;
        }

        var stack = context.getItemInHand();
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var binding = new CompoundTag();
        binding.putUUID("Core", core.bindRemote());
        binding.putString("Dimension", level.dimension().location().toString());
        data.put(BINDING, binding);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        player.displayClientMessage(Component.translatable("message.sablelife.core_remote_bound", core.displayName()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        var binding = binding(stack);
        if (!binding.hasUUID("Core")) {
            message(player, "unbound");
            return InteractionResultHolder.fail(stack);
        }
        var core = sameDimension(binding, level) ? LoadedCoreIndex.findRemote(server, binding.getUUID("Core")) : null;
        if (core == null) {
            message(player, "unavailable");
            return InteractionResultHolder.fail(stack);
        }
        if (!canAccess(serverPlayer, core)) {
            message(player, "denied");
            return InteractionResultHolder.fail(stack);
        }
        if (player.isSecondaryUseActive()) {
            if (core.isControlCore()) {
                message(player, "no_activation");
                return InteractionResultHolder.fail(stack);
            }
            core.tryActivate(player);
            return InteractionResultHolder.consume(stack);
        }
        var oldMenu = player.containerMenu;
        MarkingSessions.cancel(player);
        SESSIONS.put(serverPlayer, new Session(core, hand));
        core.openDashboard(player);
        if (player.containerMenu == oldMenu) {
            SESSIONS.remove(serverPlayer);
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    private static boolean canAccess(ServerPlayer player, HeartBlockEntity core) {
        var level = player.serverLevel();
        return (player.isCreative() || core.isSurvivalCore() || core.isControlCore())
                && LoadedCoreIndex.isLoadedCore(level, core) && core.canAccessEditor(player)
                && level.mayInteract(player, core.getBlockPos())
                && CoreRemoteClaims.allows(player, core.getBlockPos());
    }

    public static boolean editorInRange(HeartBlockEntity core, Player player, double distanceSquared) {
        if (player instanceof ServerPlayer serverPlayer && isRemoteEditor(serverPlayer, core)) {
            return hasAuthorizedSession(serverPlayer, core);
        }
        return player.level() instanceof ServerLevel level && LoadedCoreIndex.isLoadedCore(level, core)
                && SableBodyProbe.toWorld(level, player.getEyePosition()).distanceToSqr(
                        SableBodyProbe.toWorld(level, core.getBlockPos().getCenter())) <= distanceSquared;
    }

    public static boolean isRemoteEditor(ServerPlayer player, HeartBlockEntity core) {
        var session = SESSIONS.get(player);
        return session != null && session.core() == core;
    }

    public static boolean hasAuthorizedSession(ServerPlayer player, HeartBlockEntity core) {
        var session = SESSIONS.get(player);
        if (session == null || session.core() != core) return false;
        var stack = player.getItemInHand(session.hand());
        if (!(stack.getItem() instanceof CoreRemoteItem)) return false;
        var binding = binding(stack);
        return binding.hasUUID("Core") && binding.getUUID("Core").equals(core.remoteId())
                && sameDimension(binding, player.level()) && canAccess(player, core);
    }

    public static void playerTick(PlayerTickEvent.Post event) {
        if (!SESSIONS.isEmpty() && event.getEntity() instanceof ServerPlayer player
                && player.containerMenu == player.inventoryMenu && !MarkingSessions.isMarking(player)) {
            SESSIONS.remove(player);
        }
    }

    private static CompoundTag binding(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(BINDING);
    }

    private static boolean sameDimension(CompoundTag binding, Level level) {
        return binding.getString("Dimension").equals(level.dimension().location().toString());
    }

    private static void message(Player player, String key) {
        player.displayClientMessage(Component.translatable("message.sablelife.core_remote_" + key), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean bound = binding(stack).hasUUID("Core");
        tooltip.add(Component.translatable(bound ? "tooltip.sablelife.core_remote_bound" : "tooltip.sablelife.core_remote_unbound")
                .withStyle(bound ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.core_remote_use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.sablelife.core_remote_activate").withStyle(ChatFormatting.GRAY));
    }
}
