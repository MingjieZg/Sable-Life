package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkChannel;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.*;
@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class ItemDisplayGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h, boolean control) {
        return fixture(h, control, new BlockPos(3, 6, 3));
    }
    private static HeartBlockEntity fixture(GameTestHelper h,boolean control, BlockPos position){var level=h.getLevel();var p=h.absolutePos(position);level.setBlockAndUpdate(p,(control?SableLife.REDSTONE_CORE.get():SableLife.HEART.get()).defaultBlockState());level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());}
    private static VirtualModule display(HeartBlockEntity core){var m=core.modules().add("item_display");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.NORTH)));m.projectileItem(new ItemStack(Items.DIAMOND_SWORD),core.getLevel().registryAccess());return m;}
    @GameTest(template="combat",batch="display_always") public static void AlwaysDisplayBypassesLifeAndSignalButNotEnableOrMark(GameTestHelper h){var core=fixture(h,false);var m=display(core);m.edit("trigger",2,"");h.assertTrue(!ModuleItemDisplay.showing(core,m),"Dormant normal display visible");m.edit("displayAlways",1,"");h.assertTrue(ModuleItemDisplay.showing(core,m),"Always display waits for life or signal");m.edit("enabled",0,"");h.assertTrue(!ModuleItemDisplay.showing(core,m),"Disabled display visible");m.edit("enabled",1,"");var anchor=m.anchors().getFirst();h.getLevel().setBlockAndUpdate(anchor.position(anchor.body(core)),Blocks.AIR.defaultBlockState());h.assertTrue(!ModuleItemDisplay.showing(core,m)&&ModuleItemDisplay.snapshot(core).getList("Displays",10).isEmpty(),"Destroyed mark still visible");h.succeed();}
    @GameTest(template="combat",batch="display_trigger") public static void OrdinaryDisplayFollowsLifeAndTrigger(GameTestHelper h){var core=fixture(h,false);var m=display(core);core.tryActivate(TestActors.creative(h.getLevel()));h.assertTrue(ModuleItemDisplay.showing(core,m),"Alive always-trigger display missing");m.edit("trigger",2,"");h.assertTrue(!ModuleItemDisplay.showing(core,m),"Unpowered display visible");m.edit("displayAlways",1,"");h.assertTrue(ModuleItemDisplay.showing(core,m),"Override did not bypass trigger");h.succeed();}
    @GameTest(template="combat",batch="display_config") public static void DisplayOffsetsItemDataAndPresetPersist(GameTestHelper h){var core=fixture(h,false);var m=display(core);m.edit("displayAlways",1,"");m.edit("displayX",2,"");m.edit("displayY",-1,"");m.edit("displayZ",.5,"");m.edit("displaySize",1.7,"");m.edit("displayRotZ",45,"");var item=new ItemStack(Items.DIAMOND_SWORD);item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Display blade"));m.projectileItem(item,h.getLevel().registryAccess());var anchor=m.anchors().getFirst();var body=anchor.body(core);var expected=anchor.position(body).getCenter().add(2,-1,-.05);h.assertTrue(ModuleItemDisplay.localPoint(anchor,body,m).distanceTo(expected)<.00001,"Local XYZ offset/frame mismatch");var row=ModuleItemDisplay.snapshot(core).getList("Displays",10).getCompound(0);h.assertTrue(row.getFloat("displayRotZ")==45&&Math.abs(row.getFloat("displaySize")-1.7)<.001,"Render snapshot lost transform");var restored=ModulePreset.create(m.save());h.assertTrue(restored.anchors().isEmpty()&&restored.value("displaySize")==1.7&&restored.projectileItem(h.getLevel().registryAccess()).getHoverName().getString().equals("Display blade"),"Preset lost item components or retained marks");h.succeed();}
    @GameTest(template="combat",batch="display_ghost") public static void RedstoneDisplaySampleIsCopiedNotConsumed(GameTestHelper h){var option=(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues().get(List.of("modules","redstoneCore","item_display"));boolean previous=option.get();option.set(true);try{var core=fixture(h,true);var m=display(core);var actor=TestActors.creative(h.getLevel());var world=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());actor.setPos(world.add(0,1,0));var menu=new ProjectileItemMenu(1,actor.getInventory(),core,m);var carried=new ItemStack(Items.IRON_SWORD,3);menu.setCarried(carried);menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,actor);h.assertTrue(menu.displayOnly()&&menu.getCarried().getCount()==3&&m.projectileItem(h.getLevel().registryAccess()).is(Items.IRON_SWORD),"Display selector consumed item or wrong mode");m.edit("displayAlways",1,"");h.assertTrue(RedstoneCorePolicy.allowed(m)&&ModuleItemDisplay.showing(core,m),"Control core display unavailable");menu.setCarried(ItemStack.EMPTY);menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,actor);h.assertTrue(!ModuleItemDisplay.showing(core,m),"Empty display used projectile fallback");h.succeed();}finally{option.set(previous);}}

    @GameTest(template = "combat", batch = "display_packet_sync")
    public static void LargeDisplaysReachNewAndLegacyPeers(GameTestHelper h) {
        var core = fixture(h, false);
        var modules = new ArrayList<VirtualModule>();
        for (int i = 0; i < 3; i++) modules.add(model(core, 800_000, i));
        var oversized = model(core, ItemDisplaySync.MAX_MODEL_BYTES, 3);
        var modern = new DisplayObserver(core, true);
        var legacy = new DisplayObserver(core, false);
        try {
            core.modules().tick(core);
            h.assertTrue(modern.parts().size() <= 1, "Server sent more than one page on its initial tick");
            h.assertTrue(oversized.save().getCompound("ProjectileItem").getCompound("components")
                    .getCompound("minecraft:custom_data").getByteArray("shape").length == ItemDisplaySync.MAX_MODEL_BYTES,
                    "Skipping an oversized render discarded the persistent model");
        } catch (RuntimeException | Error failure) {
            modern.close();
            legacy.close();
            throw failure;
        }
        h.runAfterDelay(8, () -> {
            try {
                var parts = modern.parts();
                h.assertTrue(parts.size() == 3, "Modern peer did not receive three pages: " + parts.size());
                h.assertTrue(modern.legacy().isEmpty(), "Split display also emitted a legacy frame to the same viewer");
                var reassembly = new ItemDisplaySync.Assembly();
                long now = h.getLevel().getGameTime();
                for (int i = 0; i < parts.size(); i++) {
                    var part = roundTrip(h, ItemDisplayPacket.Part.class, parts.get(i), ItemDisplaySync.MAX_PART_BYTES);
                    var done = reassembly.accept(part, now + i);
                    if (i < 2) h.assertTrue(done == null, "Partial snapshot reached render state");
                    else {
                        h.assertTrue(done != null && done.rows().size() == 3, "Final page did not recover all three rows");
                        for (int row = 0; row < 3; row++) {
                            var data = done.rows().getCompound(row);
                            h.assertTrue(data.getUUID("Id").equals(modules.get(row).id()), "Snapshot order or module ID changed");
                            var item = ItemStack.parseOptional(h.getLevel().registryAccess(), data.getCompound("Item"));
                            var model = item.get(DataComponents.CUSTOM_DATA).copyTag();
                            h.assertTrue(model.getByteArray("shape").length == 800_000 && model.getInt("index") == row,
                                    "Model bytes changed across the actual payload codec");
                        }
                    }
                }
                h.assertTrue(legacy.parts().isEmpty() && !legacy.legacy().isEmpty(), "Old peer received the optional payload");
                var fallback = roundTrip(h, ItemDisplayPacket.class, legacy.legacy().getFirst(), ItemDisplaySync.MAX_PART_BYTES);
                h.assertTrue(fallback.displays().getList("Displays", 10).size() == 1, "Legacy snapshot was not a safely sized page");

                modern.packets.clear();
                legacy.packets.clear();
            } catch (RuntimeException | Error failure) {
                modern.close();
                legacy.close();
                throw failure;
            }
        });
        h.runAfterDelay(28, () -> {
            try {
                h.assertTrue(modern.parts().isEmpty(), "Unchanged model pages were serialized again for a capable peer");
                h.assertTrue(modern.packets.stream().anyMatch(packet -> packet instanceof ItemDisplayPacket.Part p && p.total() == 0),
                        "Stationary modern snapshot was not renewed");
                h.assertTrue(!legacy.legacy().isEmpty(), "Old peer silently lost the fallback snapshot");
                for (var module : modules) module.edit("enabled", 0, "");
                core.modules().tick(core);
                var clear = roundTrip(h, ItemDisplayPacket.class, modern.legacy().getLast(), 1024);
                h.assertTrue(clear.displays().getList("Displays", 10).isEmpty(), "Disabling display left the previous pages visible");

                modules.getFirst().edit("enabled", 1, "");
                core.modules().tick(core);
                ModuleItemDisplay.flush(new net.neoforged.neoforge.event.tick.ServerTickEvent.Post(() -> true, h.getLevel().getServer()));
                var modernSmall = roundTrip(h, ItemDisplayPacket.class, modern.legacy().getLast(), ItemDisplaySync.MAX_PART_BYTES);
                var legacySmall = roundTrip(h, ItemDisplayPacket.class, legacy.legacy().getLast(), ItemDisplaySync.MAX_PART_BYTES);
                h.assertTrue(modernSmall.equals(legacySmall) && modernSmall.displays().getList("Displays", 10).size() == 1,
                        "Single page stopped using the legacy schema for both peers");
                h.succeed();
            } finally {
                modern.close();
                legacy.close();
            }
        });
    }

    @GameTest(template = "combat", batch = "display_connection_queue")
    public static void ThreeLargeCoresCompleteWithoutInterleaving(GameTestHelper h) {
        var cores = List.of(fixture(h, false), fixture(h, false, new BlockPos(7, 6, 3)),
                fixture(h, false, new BlockPos(11, 6, 3)));
        for (var core : cores) for (int i = 0; i < 10; i++) model(core, 800_000, i);
        var observer = new DisplayObserver(cores, true);
        // All three compete for the same connection before the first network flush.
        for (var core : cores) core.modules().tick(core);
        h.runAfterDelay(38, () -> {
            try {
                var framed = observer.parts();
                h.assertTrue(framed.size() == 30, "Viewer did not receive 30 pages from three cores: " + framed.size());
                h.assertTrue(observer.multipartFrames.values().stream().allMatch(count -> count == 1),
                        "A connection sent more than one multipart page per server tick");
                var completed = new HashSet<BlockPos>();
                var assembly = new ItemDisplaySync.Assembly();
                for (int i = 0; i < framed.size(); i++) {
                    var part = roundTrip(h, ItemDisplayPacket.Part.class, framed.get(i), ItemDisplaySync.MAX_PART_BYTES);
                    h.assertTrue(part.index() == i % 10, "Multipart snapshots were interleaved");
                    var snapshot = assembly.accept(part, i);
                    if (part.index() < 9) h.assertTrue(snapshot == null, "A partial snapshot reached the client cache");
                    else {
                        h.assertTrue(snapshot != null && snapshot.rows().size() == 10
                                && snapshot.bytes() <= ItemDisplaySync.MAX_SNAPSHOT_BYTES, "A complete legal snapshot was lost");
                        completed.add(part.core());
                    }
                }
                h.assertTrue(completed.equals(Set.copyOf(cores.stream().map(HeartBlockEntity::getBlockPos).toList())),
                        "Scheduling starved a nearby core");
                h.succeed();
            } finally {
                observer.close();
            }
        });
    }

    @GameTest(template = "combat", batch = "display_queue_departure")
    public static void ClearedAndOutOfRangeViewsLeaveTheQueue(GameTestHelper h) {
        var cores = List.of(fixture(h, false), fixture(h, false, new BlockPos(7, 6, 3)));
        for (var core : cores) for (int i = 0; i < 4; i++) model(core, 800_000, i);
        var observer = new DisplayObserver(cores, true);
        var home = observer.player.position();
        for (var core : cores) core.modules().tick(core);
        h.runAfterDelay(2, () -> {
            try {
                var partial = observer.parts();
                h.assertTrue(!partial.isEmpty() && partial.size() < 4, "No in-flight view to exercise departure");
                var queued = cores.stream().filter(core -> !core.getBlockPos().equals(partial.getFirst().core())).findFirst().orElseThrow();
                for (var module : queued.modules().all()) module.edit("enabled", 0, "");
                queued.modules().tick(queued);
                h.assertTrue(observer.legacy().stream().anyMatch(packet -> packet.core().equals(queued.getBlockPos())
                        && packet.displays().getList("Displays", Tag.TAG_COMPOUND).isEmpty()),
                        "Disabling a queued view did not send the existing clear");
                observer.player.setPos(home.add(600, 0, 0));
                h.runAfterDelay(5, () -> {
                    try {
                        h.assertTrue(observer.parts().size() == partial.size(), "Out-of-range view continued its old transfer");
                        observer.player.setPos(home);
                        observer.packets.clear();
                        for (var module : queued.modules().all()) module.edit("enabled", 1, "");
                        h.runAfterDelay(13, () -> {
                            try {
                                var frames = observer.parts();
                                h.assertTrue(frames.size() == 8 && frames.get(0).index() == 0 && frames.get(4).index() == 0
                                        && !frames.get(0).core().equals(frames.get(4).core()), "Re-entering did not start two fresh snapshots");
                                h.succeed();
                            } finally {
                                observer.close();
                            }
                        });
                    } catch (RuntimeException | Error failure) {
                        observer.close();
                        throw failure;
                    }
                });
            } catch (RuntimeException | Error failure) {
                observer.close();
                throw failure;
            }
        });
    }

    @GameTest(template = "combat", batch = "display_short_stall")
    public static void AHeartbeatCanRevealARecentlyHiddenSnapshot(GameTestHelper h) {
        var lease = ItemDisplaySync.Lease.refreshed(100);
        h.assertTrue(lease.visible(140) && !lease.visible(141), "A stalled view stayed visible");
        h.assertTrue(!lease.expired(145), "A short stall discarded the cached model");
        var renewed = ItemDisplaySync.Lease.refreshed(145);
        h.assertTrue(renewed.visible(145) && !renewed.expired(745) && renewed.expired(746),
                "Heartbeat did not restore or bound the cache lease");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "display_packet_validation")
    public static void PartialDisplayExpiresAndOversizedWireIsRejected(GameTestHelper h) {
        var assembly = new ItemDisplaySync.Assembly();
        var rows = new ListTag();
        rows.add(new CompoundTag());
        var data = new CompoundTag();
        data.put("Displays", rows);
        var core = h.absolutePos(BlockPos.ZERO);
        var owner = UUID.randomUUID();
        var first = new ItemDisplayPacket.Part(core, owner, 1, 0, 2, data, 120);
        var second = new ItemDisplayPacket.Part(core, owner, 1, 1, 2, data, 120);
        h.assertTrue(assembly.accept(first, 0) == null, "Incomplete display was published");
        assembly.expire(101);
        h.assertTrue(assembly.accept(second, 101) == null, "An expired partial display resumed without its first page");
        h.assertTrue(assembly.accept(first, 102) == null && assembly.accept(second, 103) != null,
                "A fresh page sequence failed after expiry");
        assembly.accept(first, 104);
        assembly.forget(core);
        h.assertTrue(assembly.accept(second, 105) == null, "A legacy clear failed to discard an incomplete split display");

        for (int i = 0; i < 9; i++) {
            var part = new ItemDisplayPacket.Part(core, owner, 2, i, 9, data, ItemDisplaySync.MAX_PART_BYTES);
            h.assertTrue(assembly.accept(part, 106 + i) == null, "Over-budget aggregate reached render state");
        }

        var oversized = new CompoundTag();
        oversized.putByteArray("overflow", new byte[ItemDisplaySync.MAX_PART_BYTES]);
        var packet = new ItemDisplayPacket.Part(core, owner, 3, 0, 1, oversized, 0);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            ItemDisplayPacket.Part.CODEC.encode(buffer, packet);
            try {
                ItemDisplayPacket.Part.CODEC.decode(buffer);
                h.fail("Oversized item display part was accepted by the actual codec");
            } catch (DecoderException expected) {
                h.assertTrue(expected.getMessage().contains("wire limit"), "Codec rejected an unexpected field");
            }

            buffer.clear();
            var compactOnWire = new ListTag();
            for (int i = 0; i < 24_000; i++) compactOnWire.add(new CompoundTag());
            var inflated = new CompoundTag();
            inflated.put("inflated", compactOnWire);
            ItemDisplayPacket.Part.CODEC.encode(buffer, new ItemDisplayPacket.Part(core, owner, 4, 0, 1, inflated, 0));
            h.assertTrue(buffer.readableBytes() < ItemDisplaySync.MAX_PART_BYTES, "Heap-limit fixture also exceeds its wire budget");
            try {
                ItemDisplayPacket.Part.CODEC.decode(buffer);
                h.fail("Oversized decoded item display part was accepted by the actual codec");
            } catch (NbtAccounterException expected) {
                // The wire was small, but Minecraft's decoded allocation budget still applies.
            }
        } finally {
            buffer.release();
        }
        h.succeed();
    }

    private static VirtualModule model(HeartBlockEntity core, int bytes, int index) {
        var module = display(core);
        var model = new CompoundTag();
        model.putByteArray("shape", new byte[bytes]);
        model.putInt("index", index);
        var sample = new ItemStack(Items.PAPER);
        sample.set(DataComponents.CUSTOM_DATA, CustomData.of(model));
        module.projectileItem(sample, core.getLevel().registryAccess());
        module.edit("displayAlways", 1, "");
        return module;
    }

    private static <T extends CustomPacketPayload> T roundTrip(GameTestHelper h, Class<T> type, T packet, int wireLimit) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            var codec = ClientboundCustomPayloadPacket.GAMEPLAY_STREAM_CODEC;
            codec.encode(buffer, new ClientboundCustomPayloadPacket(packet));
            h.assertTrue(buffer.readableBytes() <= wireLimit, "Encoded display frame exceeded the payload budget");
            var decoded = codec.decode(buffer);
            h.assertTrue(!buffer.isReadable(), "Display codec left bytes unread");
            return type.cast(decoded.payload());
        } finally {
            buffer.release();
        }
    }

    private static final class DisplayObserver implements AutoCloseable {
        final List<CustomPacketPayload> packets = new ArrayList<>();
        final Map<Integer, Integer> multipartFrames = new HashMap<>();
        final ServerPlayer player;
        final EmbeddedChannel channel;

        DisplayObserver(HeartBlockEntity core, boolean modern) { this(List.of(core), modern); }

        DisplayObserver(List<HeartBlockEntity> cores, boolean modern) {
            var core = cores.getFirst();
            var positions = Set.copyOf(cores.stream().map(HeartBlockEntity::getBlockPos).toList());
            var level = (net.minecraft.server.level.ServerLevel) core.getLevel();
            player = TestActors.creative(level);
            player.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(level, core.getBlockPos().getCenter()));
            var transport = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(transport);
            if (modern) {
                NetworkRegistry.configureMockConnection(transport);
            } else {
                var legacy = ItemDisplayPacket.TYPE.id();
                ChannelAttributes.setPayloadSetup(transport,
                        new NetworkPayloadSetup(Map.of(ConnectionProtocol.PLAY, Map.of(legacy, new NetworkChannel(legacy, "50")))));
            }
            player.connection = new ServerGamePacketListenerImpl(level.getServer(), transport, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {
                    if (packet instanceof ClientboundCustomPayloadPacket payload
                            && ((payload.payload() instanceof ItemDisplayPacket legacy && positions.contains(legacy.core())
                                && legacy.displays().contains("Displays"))
                            || (payload.payload() instanceof ItemDisplayPacket.Part part && positions.contains(part.core())))) {
                        packets.add(payload.payload());
                        if (payload.payload() instanceof ItemDisplayPacket.Part part && part.total() > 0) {
                            multipartFrames.merge(level.getServer().getTickCount(), 1, Integer::sum);
                        }
                    }
                }
            };
            level.players().add(player);
        }

        List<ItemDisplayPacket.Part> parts() {
            return packets.stream().filter(ItemDisplayPacket.Part.class::isInstance)
                    .map(ItemDisplayPacket.Part.class::cast).filter(part -> part.total() > 0).toList();
        }

        List<ItemDisplayPacket> legacy() {
            return packets.stream().filter(ItemDisplayPacket.class::isInstance).map(ItemDisplayPacket.class::cast).toList();
        }

        @Override public void close() {
            player.serverLevel().players().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
