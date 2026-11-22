package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.AuthoringLimits;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.VirtualModule;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Varint21FrameDecoder;
import net.minecraft.network.Varint21LengthFieldPrepender;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
    private static CompoundTag reference(ItemStack copier) {
        return copier.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound("SableLifeClipboard");
    }
    private static CompoundTag clipboard(FakePlayer player) {
        var data = reference(player.getMainHandItem());
        var id = data.getString("Id");
        if (id.isEmpty()) return new CompoundTag();
        var fileName = (data.hasUUID("Owner") ? data.getUUID("Owner") + "_" : "") + id + ".nbt";
        var path = player.server.getWorldPath(LevelResource.ROOT).resolve("data/sablelife_clipboards").resolve(fileName);
        try {
            return NbtIo.readCompressed(path, NbtAccounter.create(AuthoringLimits.MAX_CONFIGURATION_BYTES));
        } catch (IOException e) {
            throw new AssertionError("No saved clipboard for " + id, e);
        }
    }
    private static void balance(HeartBlockEntity core, float current, long energy) {
        var saved=core.saveWithoutMetadata(core.getLevel().registryAccess());
        saved.putFloat("Health",current);saved.putLong("SurvivalTokens",energy);
        core.loadWithComponents(saved,core.getLevel().registryAccess());
    }

    @GameTest(template="combat", batch="copier_capacity")
    public static void CopierCarriesMaximumModuleList(GameTestHelper h) {
        var source = core(h, SableLife.HEART.get(), 2);
        var target = core(h, SableLife.HEART.get(), 5);
        var entries = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < HeartModules.LIMIT; i++) {
            var module = new dev.sablelife.module.VirtualModule("item_display");
            module.edit("name", 0, "Display " + i);
            module.edit("displaySize", 0.75, "");
            entries.add(module.save());
        }
        var saved = new CompoundTag();
        saved.put("Modules", entries);
        source.modules().load(saved);
        var player = player(h, GameType.CREATIVE);
        h.assertTrue(use(player, source) == InteractionResult.CONSUME, "Copier rejected the full source core");
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, player.getMainHandItem());
            System.out.println("MODULE_CAPACITY_COPIER_BYTES=" + buffer.readableBytes());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
        h.assertTrue(use(player, target) == InteractionResult.CONSUME, "Copier rejected the full clipboard");
        var pasted = target.modules().all();
        h.assertTrue(pasted.size() == HeartModules.LIMIT && pasted.getLast().name().equals("Display " + (HeartModules.LIMIT - 1))
                && pasted.getLast().value("displaySize") == 0.75, "Paste lost the last module or its settings");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "copier_large_item_data")
    public static void TwoLargeClipboardsSurviveCompressedInventorySync(GameTestHelper h) {
        var source = core(h, SableLife.HEART.get(), 2);
        var target = core(h, SableLife.HEART.get(), 5);
        var first = source.modules().add("item_display");
        var second = source.modules().add("item_display");
        var random = new Random(925L);
        byte[] firstModel = new byte[995_000];
        byte[] secondModel = new byte[995_000];
        random.nextBytes(firstModel);
        random.nextBytes(secondModel);
        setDisplayModel(h, first, firstModel);
        setDisplayModel(h, second, secondModel);

        var player = player(h, GameType.CREATIVE);
        h.assertTrue(use(player, source) == InteractionResult.CONSUME, "Copier rejected the large displays");
        var firstCopier = player.getMainHandItem();
        var saved = firstCopier.get(DataComponents.CUSTOM_DATA);
        byte[] changedModel = new byte[995_000];
        random.nextBytes(changedModel);
        setDisplayModel(h, second, changedModel);
        var secondHolder = player(h, GameType.CREATIVE);
        h.assertTrue(use(secondHolder, source) == InteractionResult.CONSUME, "Second large clipboard was rejected");
        var secondCopier = secondHolder.getMainHandItem();
        int firstBytes = reference(firstCopier).getInt("Bytes");
        int secondBytes = reference(secondCopier).getInt("Bytes");
        h.assertTrue(firstBytes > 1_990_000 && firstBytes <= 2_000_000
                        && secondBytes > 1_990_000 && secondBytes <= 2_000_000
                        && !reference(firstCopier).getString("Id").equals(reference(secondCopier).getString("Id")),
                "Fixture needs two distinct clipboards close to the limit");
        var synced = inventoryRoundTrip(h, firstCopier, secondCopier);
        h.assertTrue(saved.equals(synced.get(36).get(DataComponents.CUSTOM_DATA))
                        && reference(secondCopier).equals(reference(synced.get(37))), "Inventory sync lost clipboard references");
        var registries = h.getLevel().registryAccess();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.parse(registries, synced.get(36).save(registries)).orElseThrow());
        secondHolder.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.parse(registries, synced.get(37).save(registries)).orElseThrow());

        byte[] largerModel = new byte[1_020_000];
        random.nextBytes(largerModel);
        setDisplayModel(h, second, largerModel);
        player.setShiftKeyDown(true);
        h.assertTrue(use(player, source) == InteractionResult.FAIL, "Copier accepted a clipboard over the new limit");
        h.assertTrue(saved.equals(player.getMainHandItem().get(DataComponents.CUSTOM_DATA)),
                "Rejected copy modified the existing clipboard");
        player.setShiftKeyDown(false);

        h.assertTrue(use(player, target) == InteractionResult.CONSUME, "Copier rejected the synchronized clipboard");
        var modules = target.modules().all();
        h.assertTrue(modules.size() == 2 && Arrays.equals(firstModel, displayModel(h, modules.getFirst()))
                && Arrays.equals(secondModel, displayModel(h, modules.getLast())), "First paste lost a model");
        h.assertTrue(use(secondHolder, target) == InteractionResult.CONSUME
                && Arrays.equals(changedModel, displayModel(h, target.modules().all().getLast())), "Second paste lost the independent clipboard");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "copier_nested_model")
    public static void CopierPreservesNestedModelsWithABoundedDepth(GameTestHelper h) {
        var source = core(h, SableLife.HEART.get(), 2);
        var target = core(h, SableLife.HEART.get(), 5);
        var display = source.modules().add("item_display");
        var registries = h.getLevel().registryAccess();
        var model = nestedDisplayItem(35);
        display.projectileItem(model, registries);

        var player = player(h, GameType.CREATIVE);
        h.assertTrue(use(player, source) == InteractionResult.CONSUME, "Copier rejected a nested display model");
        h.assertTrue(use(player, target) == InteractionResult.CONSUME, "Copier failed to paste the nested display model");
        h.assertTrue(ItemStack.matches(model, target.modules().all().getFirst().projectileItem(registries)),
                "Pasting changed the nested display item");

        var reference = reference(player.getMainHandItem());
        display.projectileItem(nestedDisplayItem(65), registries);
        player.setShiftKeyDown(true);
        h.assertTrue(use(player, source) == InteractionResult.FAIL && reference.equals(reference(player.getMainHandItem())),
                "Over-depth copy was accepted or replaced the clipboard");
        player.setShiftKeyDown(false);
        h.succeed();
    }

    private static ItemStack nestedDisplayItem(int groups) {
        var part = new CompoundTag();
        part.putString("Material", "minecraft:iron_block");
        for (int index = 0; index < groups; index++) {
            var children = new net.minecraft.nbt.ListTag();
            children.add(part);
            part = new CompoundTag();
            part.put("Blocks", children);
        }
        var item = new ItemStack(Items.PAPER);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(part));
        return item;
    }

    private static void setDisplayModel(GameTestHelper h, VirtualModule module, byte[] model) {
        var item = new ItemStack(Items.PAPER);
        var tag = new CompoundTag();
        tag.putByteArray("test_model", model);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        module.projectileItem(item, h.getLevel().registryAccess());
    }

    private static byte[] displayModel(GameTestHelper h, VirtualModule module) {
        return module.projectileItem(h.getLevel().registryAccess())
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getByteArray("test_model");
    }

    private static List<ItemStack> inventoryRoundTrip(GameTestHelper h, ItemStack first, ItemStack second) {
        var slots = NonNullList.withSize(46, new ItemStack(Items.STONE));
        slots.set(36, first);
        slots.set(37, second);
        var protocol = GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                RegistryFriendlyByteBuf.decorator(h.getLevel().registryAccess()));
        var channel = new EmbeddedChannel(new Varint21FrameDecoder(null), new CompressionDecoder(256, true),
                new Varint21LengthFieldPrepender(), new CompressionEncoder(256));
        try {
            var raw = Unpooled.buffer();
            protocol.codec().encode(raw, new ClientboundContainerSetContentPacket(0, 1, slots, ItemStack.EMPTY));
            channel.writeOutbound(raw);
            ByteBuf encoded = channel.readOutbound();
            int frameBytes = encoded.readableBytes();
            channel.writeInbound(encoded);
            ByteBuf decoded = channel.readInbound();
            try {
                h.assertTrue(frameBytes < 1024, "Inventory sync sent more than clipboard references");
                System.out.println("DATA_COPIER_CLIPBOARD_BYTES=" + reference(first).getInt("Bytes")
                        + "," + reference(second).getInt("Bytes") + " INVENTORY_FRAME_BYTES=" + frameBytes);
                var packet = (ClientboundContainerSetContentPacket) protocol.codec().decode(decoded);
                return packet.getItems();
            } finally {
                decoded.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @GameTest(template = "combat", batch = "copier_archive")
    public static void ClipboardCanBeTradedArchivedAndRestoredByAnOperator(GameTestHelper h) {
        var source = core(h, SableLife.HEART.get(), 2);
        var target = core(h, SableLife.HEART.get(), 5);
        var creator = player(h, GameType.CREATIVE);
        var holder = player(h, GameType.CREATIVE);
        source.modules().add("item_display");
        h.assertTrue(use(creator, source) == InteractionResult.CONSUME, "Could not copy a clipboard");
        holder.setItemInHand(InteractionHand.MAIN_HAND, creator.getMainHandItem().copy());
        var clipboard = reference(holder.getMainHandItem());
        h.assertTrue(creator.getUUID().equals(clipboard.getUUID("Owner")), "Clipboard did not retain its originating account");
        String arguments = clipboard.getUUID("Owner") + " " + clipboard.getString("Id");
        var commands = h.getLevel().getServer().getCommands();
        var operator = h.getLevel().getServer().createCommandSourceStack();
        commands.performPrefixedCommand(operator.withPermission(0), "sablelife clipboard archive " + arguments);
        h.assertTrue(use(holder, target) == InteractionResult.CONSUME, "Non-operator archived the shared clipboard");

        commands.performPrefixedCommand(operator, "sablelife clipboard archive " + arguments);
        h.assertTrue(use(holder, target) == InteractionResult.FAIL, "Archived clipboard remained accessible");
        h.assertTrue(clipboard.equals(reference(holder.getMainHandItem())), "Archival dropped the item reference");

        commands.performPrefixedCommand(operator, "sablelife clipboard restore " + arguments);
        h.assertTrue(use(holder, target) == InteractionResult.CONSUME, "Restored clipboard could not be pasted");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "copier_legacy_item")
    public static void OldCopierLoadsEmptyAndCanCopyAgain(GameTestHelper h) {
        var registries = h.getLevel().registryAccess();
        var old = new ItemStack(SableLife.DATA_COPIER.get());
        var data = new CompoundTag();
        data.putByteArray("SableLifeConfiguration", new byte[1_995_000]);
        data.put("SableLifeConfigurationSummary", new CompoundTag());
        data.putString("unrelated", "keep");
        old.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        var loaded = ItemStack.parse(registries, old.save(registries)).orElseThrow();
        var custom = loaded.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        h.assertTrue(!loaded.hasFoil() && custom.size() == 1 && custom.getString("unrelated").equals("keep"),
                "Old clipboard must load empty while retaining other item data");
        var player = player(h, GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, loaded);
        var source = core(h, SableLife.HEART.get(), 2);
        source.modules().add("item_display");
        h.assertTrue(use(player, source) == InteractionResult.CONSUME && !reference(loaded).getString("Id").isEmpty(),
                "Old copier cannot copy a new clipboard");
        h.succeed();
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
