package dev.sablelife.testing;

import com.mojang.authlib.GameProfile;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter;
import dev.rew1nd.sableschematicapi.blueprint.SableBlueprintFiles;
import dev.rew1nd.sableschematicapi.network.BlueprintToolServerActions;
import dev.rew1nd.sableschematicapi.tool.SableSchematicApiItems;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.sablelife.SableLife;
import dev.sablelife.compat.StructureRemovalApi;
import dev.sablelife.compat.photomancy.PhotomancyCompatibility;
import dev.sablelife.heart.BlueprintFaction;
import dev.sablelife.heart.CoreRelations;
import dev.sablelife.heart.FactionRegistry;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartSettingsMenu;
import dev.sablelife.module.ModuleNetworking;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sablelife_owner_tests")
@PrefixGameTestTemplate(false)
public final class BlueprintFactionGameTests {
    @GameTest(template = "combat", batch = "blueprint_faction_identity")
    public static void ForeignNameAndUuidCollisionsRequireExplicitBinding(GameTestHelper test) {
        var registry = new FactionRegistry();
        var owner = actor(test, "FactionOwner", false);
        var importer = actor(test, "FactionImporter", false);
        var local = registry.create(owner, "Cobalt");
        var exported = registry.portable(local);
        test.assertTrue(local != null && exported != null && exported.sourceWorld() != null,
                "Could not author a native faction identity");
        test.assertTrue(local.equals(registry.resolveBlueprintFaction(importer, exported)),
                "Same-world public faction did not resolve through ordinary assignment rights");
        test.assertTrue(local.equals(registry.resolveBlueprintFaction(importer, BlueprintFaction.legacy(local))),
                "Old UUID-only public faction did not resolve");

        var foreign = foreign(UUID.randomUUID(), local, "Cobalt");
        var nameOnlyCollision = foreign(foreign.sourceWorld(), UUID.randomUUID(), "Cobalt");
        test.assertTrue(registry.resolveBlueprintFaction(importer, foreign) == null
                        && registry.resolveBlueprintFaction(importer, nameOnlyCollision) == null,
                "A foreign faction attached itself to an unrelated local identity");
        test.assertTrue(!registry.bindBlueprintFaction(importer, foreign, FactionRegistry.WILDS),
                "A blueprint was allowed to bind to the reserved Wilds faction");
        test.assertTrue(registry.bindBlueprintFaction(importer, foreign, local)
                        && local.equals(registry.resolveBlueprintFaction(importer, foreign)),
                "Explicit assignment to an accessible faction did not resolve");
        test.assertTrue(registry.resolveBlueprintFaction(owner, foreign) == null
                        && registry.resolveBlueprintFaction(importer, nameOnlyCollision) == null,
                "Mapping leaked to another actor or source faction");
        test.assertTrue(exported.equals(BlueprintFaction.read(exported.save())), "Portable data did not round-trip");
        test.succeed();
    }

    @GameTest(template = "combat", batch = "blueprint_faction_mapping_permissions")
    public static void SavedBindingsRecheckPrivateAndDeletedDestinations(GameTestHelper test) {
        var registry = new FactionRegistry();
        var owner = actor(test, "PrivateOwner", false);
        var importer = actor(test, "PrivateImporter", false);
        var administrator = actor(test, "FactionOperator", true);
        var local = registry.create(owner, "Local House");
        var fallback = registry.create(administrator, "Origin House");
        var sameWorldSource = registry.portable(fallback);
        var source = foreign(UUID.randomUUID(), UUID.randomUUID(), "Foreign House");
        test.assertTrue(importer.isCreative() && !importer.hasPermissions(2), "Fixture needs a Creative non-operator");
        test.assertTrue(registry.bindBlueprintFaction(importer, source, local)
                        && registry.bindBlueprintFaction(importer, sameWorldSource, local), "Accessible public mapping rejected");
        registry = reload(test, registry);
        test.assertTrue(local.equals(registry.resolveBlueprintFaction(importer, source))
                        && local.equals(registry.resolveBlueprintFaction(importer, sameWorldSource))
                        && registry.resolveBlueprintFaction(owner, source) == null,
                "Registry reload lost actor scope or the mapping");

        test.assertTrue(registry.privacy(owner, local), "Could not make the destination private");
        test.assertTrue(registry.resolveBlueprintFaction(importer, source) == null
                        && registry.resolveBlueprintFaction(importer, sameWorldSource) == null
                        && !registry.bindBlueprintFaction(importer, source, local)
                        && registry.resolveBlueprintFaction(importer, BlueprintFaction.legacy(local)) == null,
                "Private mapped faction bypassed assignment or fell back to its public source");
        test.assertTrue(registry.bindBlueprintFaction(administrator, source, local)
                        && local.equals(registry.resolveBlueprintFaction(administrator, source)),
                "Existing operator allowance was lost");

        registry.observe(importer);
        test.assertTrue(registry.invite(owner, local, importer.getGameProfile().getName()), "Could not issue fixture invitation");
        test.assertTrue(!registry.bindBlueprintFaction(importer, source, local), "Invitation alone granted private assignment");
        test.assertTrue(registry.accept(importer, local) && registry.bindBlueprintFaction(importer, source, local)
                        && local.equals(registry.resolveBlueprintFaction(importer, source)),
                "Accepted local membership did not permit binding");
        test.assertTrue(registry.removeMember(owner, local, importer.getUUID())
                        && registry.resolveBlueprintFaction(importer, source) == null,
                "Removing local membership did not invalidate assignment");
        test.assertTrue(registry.delete(owner, local), "Could not delete the mapped destination");
        var replacement = registry.create(owner, "Local House");
        registry = reload(test, registry);
        test.assertTrue(replacement != null && !replacement.equals(local)
                        && registry.resolveBlueprintFaction(administrator, source) == null
                        && registry.resolveBlueprintFaction(importer, source) == null
                        && registry.resolveBlueprintFaction(importer, sameWorldSource) == null,
                "A deleted destination fell back or resurrected after reload");
        test.succeed();
    }

    @GameTest(template = "combat", batch = "blueprint_faction_create")
    public static void CreateAppliesChosenSettingsOnceWithoutImportingMembers(GameTestHelper test) {
        var origin = new FactionRegistry();
        var author = actor(test, "OriginalAuthor", false);
        var member = actor(test, "OriginalMember", false);
        var localCreator = actor(test, "LocalCreator", false);
        var sourceId = origin.create(author, "Original Faction");
        test.assertTrue(origin.joinPlayer(member, sourceId) && origin.toggle(author, sourceId),
                "Could not author source roster and independent settings");
        var descriptor = origin.portable(sourceId);
        var payload = descriptor.save();
        test.assertTrue(!payload.contains("Owner") && !payload.contains("Members") && !payload.contains("Invitations"),
                "Portable metadata exposed world player records");

        var registry = new FactionRegistry();
        var first = registry.createBlueprintFaction(localCreator, descriptor, "Local Replacement", true, false, true);
        var id = first.id();
        test.assertTrue(id != null && !id.equals(sourceId) && first.error().isEmpty(), "Could not create a fresh local faction");
        var row = registry.detail(localCreator, id);
        test.assertTrue(row.getUUID("Owner").equals(localCreator.getUUID()) && row.getBoolean("Private")
                        && !row.getBoolean("FriendlyFire") && row.getBoolean("AlliedFire"),
                "Imported faction overwrote destination-authored settings or ownership");
        var roster = row.getList("Members", Tag.TAG_COMPOUND);
        test.assertTrue(roster.size() == 1 && roster.getCompound(0).getUUID("Id").equals(localCreator.getUUID())
                        && !registry.canAssign(member, id) && !registry.canAssign(author, id),
                "Foreign player data granted membership in the new faction");

        registry = reload(test, registry);
        var second = registry.createBlueprintFaction(localCreator, descriptor, "Unwanted Duplicate", false, true, false);
        test.assertTrue(id.equals(second.id()) && second.error().isEmpty()
                        && id.equals(registry.resolveBlueprintFaction(localCreator, descriptor))
                        && registry.snapshot(localCreator).size() == 1,
                "Repeated import created a duplicate or lost its saved binding");
        row = registry.detail(localCreator, id);
        test.assertTrue(row.getString("Name").equals("Local Replacement") && row.getBoolean("Private")
                        && !row.getBoolean("FriendlyFire") && row.getBoolean("AlliedFire"),
                "Retry renamed or changed existing destination settings");
        test.succeed();
    }

    @GameTest(template = "combat", batch = "blueprint_faction_alliance")
    public static void ImportedAlliesNeedTheirOwnBindingAndNativeManagerConfirmation(GameTestHelper test) {
        var registry = new FactionRegistry();
        var sourceManager = actor(test, "SourceManager", false);
        var allyManager = actor(test, "AllyManager", false);
        var unrelated = actor(test, "Unrelated", false);
        UUID world = UUID.randomUUID();
        UUID sourceAlly = UUID.randomUUID();
        UUID unlisted = UUID.randomUUID();
        var descriptor = new BlueprintFaction(world, UUID.randomUUID(), "Foreign Home", true,
                false, false, false, List.of(new BlueprintFaction.Ally(sourceAlly, "Foreign Ally")));
        var allyDescriptor = foreign(world, sourceAlly, "Foreign Ally");
        var unlistedDescriptor = foreign(world, unlisted, "Other");
        var own = registry.create(sourceManager, "Own");
        var ally = registry.create(allyManager, "Allied Destination");
        var third = registry.create(unrelated, "Unrelated Destination");
        test.assertTrue(registry.bindBlueprintFaction(sourceManager, descriptor, own), "Failed to bind source faction");
        test.assertTrue(!registry.confirmBlueprintAlliance(sourceManager, descriptor, own, sourceAlly),
                "An unmapped hint created a relationship");
        test.assertTrue(registry.bindBlueprintFaction(sourceManager, allyDescriptor, ally)
                        && registry.bindBlueprintFaction(sourceManager, unlistedDescriptor, third),
                "Could not bind accessible ally fixtures");
        test.assertTrue(!registry.confirmBlueprintAlliance(unrelated, descriptor, own, sourceAlly)
                        && !registry.confirmBlueprintAlliance(sourceManager, descriptor, own, unlisted)
                        && !registry.confirmBlueprintAlliance(sourceManager, descriptor, third, sourceAlly),
                "Foreign metadata authorized an unrelated actor, source or unlisted alliance");
        test.assertTrue(registry.confirmBlueprintAlliance(sourceManager, descriptor, own, sourceAlly)
                        && !registry.allied(own, ally),
                "One faction manager unilaterally established a bilateral alliance");

        registry = reload(test, registry);
        test.assertTrue(registry.acceptAlliance(allyManager, ally, own) && registry.allied(own, ally)
                        && registry.confirmBlueprintAlliance(sourceManager, descriptor, own, sourceAlly)
                        && !registry.allied(own, third) && !registry.damageAllowed(own, ally),
                "Native confirmation did not restore the single direct alliance with damage protection");
        test.succeed();
    }

    @GameTest(template = "combat", batch = "blueprint_faction_pending_core")
    public static void PendingChoiceSurvivesCoreSaveAndCanBeBoundOrSkipped(GameTestHelper test) {
        var source = foreign(UUID.randomUUID(), UUID.randomUUID(), "Pending Faction");
        var relations = new CoreRelations();
        relations.ownerName("LocalOwner");
        relations.priority(2);
        relations.exclude("ExcludedPlayer");
        relations.blueprintFaction(source);
        var saved = relations.save();
        var fresh = new CoreRelations();
        fresh.load(saved);
        test.assertTrue(source.equals(fresh.blueprintFaction()) && fresh.faction() == null && !saved.hasUUID("Faction"),
                "Pending foreign data became active after ordinary core persistence");

        UUID local = UUID.randomUUID();
        fresh.bindBlueprintFaction(local);
        var bound = new CoreRelations();
        bound.load(fresh.save());
        test.assertTrue(local.equals(bound.faction()) && source.equals(bound.blueprintFaction()),
                "Explicit binding did not persist the active local faction and provenance for later alliance choices");
        var skipped = new CoreRelations();
        skipped.load(saved);
        skipped.clearBlueprintFaction();
        fresh.load(skipped.save());
        test.assertTrue(fresh.faction() == null && fresh.blueprintFaction() == null
                        && fresh.ownerName().equals("LocalOwner") && fresh.priority() == 2 && fresh.excludes("ExcludedPlayer"),
                "Skipping kept faction data or modified unrelated relations");
        test.succeed();
    }

    @GameTest(template = "combat", batch = "blueprint_faction_photomancy", timeoutTicks = 100)
    public static void OfficialPhotomancyUsesActualPlayerAndRegistryForFactionPlacement(GameTestHelper test) throws Exception {
        if (!PhotomancyCompatibility.available()) {
            test.succeed();
            return;
        }
        var level = test.getLevel();
        var server = level.getServer();
        var savedData = server.overworld().getDataStorage();
        var container = SubLevelContainer.getContainer(level);
        var previousBodies = bodyIds(level);
        var originalRegistry = FactionRegistry.get(server);
        var importer = actor(test, "FactionAuthor", true);
        var other = actor(test, "DestinationOwner", false);
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var name = "Portable Faction " + suffix;
        var fileName = "faction_fixture_" + suffix;
        var path = SableBlueprintFiles.path(server, fileName);
        var sourceId = originalRegistry.create(importer, name);
        test.assertTrue(sourceId != null, "Could not create the source-world fixture faction");
        try {
            var pos = test.absolutePos(new BlockPos(4, 40, 4));
            var adjacent = pos.east();
            level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
            level.setBlockAndUpdate(adjacent, Blocks.IRON_BLOCK.defaultBlockState());
            var body = SubLevelAssemblyHelper.assembleBlocks(level, pos, List.of(pos, adjacent),
                    new BoundingBox3i(pos.getX(), pos.getY(), pos.getZ(), adjacent.getX(), adjacent.getY(), adjacent.getZ()));
            var original = (HeartBlockEntity) level.getBlockEntity(body.getPlot().getCenterBlock());
            original.relations().faction(sourceId);
            original.relations().priority(2);
            var portable = originalRegistry.portable(sourceId);
            var exported = SableBlueprintExporter.exportSelected(level, pos.getCenter(), List.of(body.getUniqueId()));
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(exported.save(), path);

            var same = place(test, playerSource(test, importer, 12), fileName);
            test.assertTrue(sourceId.equals(same.relations().faction()), "Official same-world placement did not restore the local faction");

            var nativeData = Files.readAllBytes(path);
            var tool = new ItemStack(SableSchematicApiItems.BLUEPRINT_TOOL.get());
            verifyFirstPlacementInEmptyRegistry(test, importer, portable, fileName, nativeData, tool);

            var destination = new FactionRegistry();
            var replacement = destination.create(other, name);
            test.assertTrue(replacement != null && !portable.sourceWorld().equals(destination.portable(replacement).sourceWorld()),
                    "Fixture did not create an independent destination registry");
            savedData.set("sablelife_factions", destination);
            var pending = place(test, playerSource(test, importer, 20), fileName);
            test.assertTrue(pending.relations().faction() == null && portable.equals(pending.relations().blueprintFaction())
                            && pending.relations().priority() == 2,
                    "Cross-world placement bound a same-name local faction or lost pending metadata");

            test.assertTrue(destination.bindBlueprintFaction(importer, portable, replacement), "Could not confirm the actual player's mapping");
            var bound = place(test, playerSource(test, importer, 28), fileName);
            test.assertTrue(replacement.equals(bound.relations().faction()), "Official loader ignored authorized actor-specific binding");

            var console = server.createCommandSourceStack().withLevel(level).withEntity(importer)
                    .withPosition(test.absoluteVec(new Vec3(36, 40, 14)));
            test.assertTrue(console.source != importer && console.getEntity() == importer, "Fixture did not preserve console authority");
            var actorless = place(test, console, fileName);
            test.assertTrue(actorless.relations().faction() == null && portable.equals(actorless.relations().blueprintFaction()),
                    "Console execution borrowed a player's remembered faction binding");

            var beforeTool = bodyIds(level);
            requestToolPlacement(test, importer, fileName, nativeData, 44);
            test.assertTrue(beforeTool.equals(bodyIds(level)), "An operator without the tool placed a structure");
            other.setItemInHand(InteractionHand.MAIN_HAND, tool.copy());
            test.assertTrue(other.isCreative() && !other.hasPermissions(2)
                            && destination.bindBlueprintFaction(other, portable, replacement),
                    "Fixture needs a Creative non-operator with the tool and a valid mapping");
            requestToolPlacement(test, other, fileName, nativeData, 44);
            test.assertTrue(beforeTool.equals(bodyIds(level)), "A Creative non-operator used the tool to place a structure");
            importer.setItemInHand(InteractionHand.OFF_HAND, tool.copy());
            requestToolPlacement(test, importer, fileName, nativeData, 44);
            var toolPlacement = restoredCore(test, beforeTool);
            test.assertTrue(replacement.equals(toolPlacement.relations().faction())
                            && portable.equals(toolPlacement.relations().blueprintFaction()),
                    "Official Blueprint Tool ignored the authorized holder's remembered faction");
        } finally {
            savedData.set("sablelife_factions", originalRegistry);
            originalRegistry.delete(importer, sourceId);
            Files.deleteIfExists(path);
            for (var body : List.copyOf(container.getAllSubLevels())) {
                if (body instanceof ServerSubLevel created && !previousBodies.contains(created.getUniqueId())) {
                    StructureRemovalApi.remove(level, created);
                }
            }
        }
        test.succeed();
    }

    private static void verifyFirstPlacementInEmptyRegistry(GameTestHelper test, ServerPlayer importer,
            BlueprintFaction source, String fileName, byte[] nativeData, ItemStack tool) throws Exception {
        var level = test.getLevel();
        var destination = new FactionRegistry();
        level.getServer().overworld().getDataStorage().set("sablelife_factions", destination);
        importer.setItemInHand(InteractionHand.OFF_HAND, tool.copy());
        try {
            var beforeFirst = bodyIds(level);
            requestToolPlacement(test, importer, fileName, nativeData, 52);
            var first = restoredCore(test, beforeFirst);
            var firstMenu = new HeartSettingsMenu(1, importer.getInventory(), first);
            var initial = firstMenu.snapshot();
            var view = initial.getCompound("BlueprintFactionView");
            test.assertTrue(first.relations().hasPendingBlueprintFaction()
                            && source.equals(BlueprintFaction.read(initial.getCompound("Relations").getCompound(BlueprintFaction.TAG)))
                            && !view.hasUUID("Bound") && !view.hasUUID("Suggested") && initial.getBoolean("LiveControls")
                            && source.name().equals(view.getString("CreatedName"))
                            && initial.getList("Factions", Tag.TAG_COMPOUND).isEmpty(),
                    "First official placement did not expose the pending source and its creation draft in an empty destination");

            test.assertTrue(sendFactionAction(test, firstMenu, importer, "bp_clear", 0, "")
                            && first.relations().blueprintFaction() == null && first.relations().faction() == null
                            && firstMenu.snapshot().getCompound("BlueprintFactionView").isEmpty()
                            && destination.snapshot(importer).isEmpty() && destination.resolveBlueprintFaction(importer, source) == null,
                    "Skipping a pending import created a local faction or mapping");

            var pending = place(test, playerSource(test, importer, 60), fileName);
            test.assertTrue(pending.relations().hasPendingBlueprintFaction() && source.equals(pending.relations().blueprintFaction()),
                    "Placement after skipping did not remain pending");
            var menu = new HeartSettingsMenu(2, importer.getInventory(), pending);
            String draft = menu.snapshot().getCompound("BlueprintFactionView").getString("CreatedName");
            int flags = HeartSettingsMenu.factionFlags(source.privateFaction(), source.friendlyFire(), source.alliedFire());
            test.assertTrue(sendFactionAction(test, menu, importer, "bp_create", flags, draft),
                    "The actual menu could not create and bind the first local blueprint faction");
            var local = pending.relations().faction();
            test.assertTrue(local != null && !local.equals(source.sourceFaction())
                            && local.equals(menu.snapshot().getCompound("BlueprintFactionView").getUUID("Bound"))
                            && local.equals(destination.resolveBlueprintFaction(importer, source))
                            && draft.equals(destination.detail(importer, local).getString("Name")),
                    "Creating the first local faction did not assign the core and remember its mapping");

            var beforeRepeat = bodyIds(level);
            requestToolPlacement(test, importer, fileName, nativeData, 68);
            var repeat = restoredCore(test, beforeRepeat);
            test.assertTrue(local.equals(repeat.relations().faction()) && source.equals(repeat.relations().blueprintFaction())
                            && destination.snapshot(importer).size() == 1,
                    "Later official placement did not bind the same faction or created a duplicate");
        } finally {
            importer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static boolean sendFactionAction(GameTestHelper test, HeartSettingsMenu menu, ServerPlayer actor,
            String action, double value, String text) {
        int revision = menu.snapshot().getCompound("BlueprintFactionView").getInt("Revision");
        var packet = new ModuleNetworking.Action(menu.containerId, 0, new UUID(0, 0), "core",
                "relation:" + action + ":" + revision, value, text);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), test.getLevel().registryAccess());
        try {
            ModuleNetworking.Action.CODEC.encode(buffer, packet);
            var received = ModuleNetworking.Action.CODEC.decode(buffer);
            return menu.action(actor, received.key(), received.value(), received.text());
        } finally {
            buffer.release();
        }
    }

    private static CommandSourceStack playerSource(GameTestHelper test, ServerPlayer actor, int x) {
        actor.setPos(test.absoluteVec(new Vec3(x, 40, 14)));
        var source = actor.createCommandSourceStack();
        test.assertTrue(source.source == actor && source.hasPermission(2), "Fixture did not produce an operator-owned command");
        return source;
    }

    private static HeartBlockEntity place(GameTestHelper test, CommandSourceStack source, String name) throws Exception {
        var level = test.getLevel();
        level.getChunkAt(BlockPos.containing(source.getPosition()));
        var existing = bodyIds(level);
        int placed = level.getServer().getCommands().getDispatcher().execute("sablebp load " + name, source);
        test.assertTrue(placed == 1, "Official Photomancy command did not place one structure");
        return restoredCore(test, existing);
    }

    private static void requestToolPlacement(GameTestHelper test, ServerPlayer actor, String name, byte[] blueprint, int x) {
        var origin = test.absoluteVec(new Vec3(x, 40, 14));
        actor.setPos(origin);
        test.getLevel().getChunkAt(BlockPos.containing(origin));
        var position = new CompoundTag();
        position.putDouble("x", origin.x);
        position.putDouble("y", origin.y);
        position.putDouble("z", origin.z);
        var payload = new CompoundTag();
        payload.putString("name", name);
        payload.put("origin", position);
        payload.putByteArray("data", blueprint);
        BlueprintToolServerActions.registerDefaults();
        BlueprintToolServerActions.handle(BlueprintToolServerActions.LOAD_BLUEPRINT, actor, payload);
    }

    private static Set<UUID> bodyIds(ServerLevel level) {
        return SubLevelContainer.getContainer(level).getAllSubLevels().stream().map(SubLevel::getUniqueId).collect(Collectors.toSet());
    }

    private static HeartBlockEntity restoredCore(GameTestHelper test, Set<UUID> existing) {
        var level = test.getLevel();
        var bodies = SubLevelContainer.getContainer(level).getAllSubLevels().stream()
                .filter(candidate -> !existing.contains(candidate.getUniqueId())).toList();
        test.assertTrue(bodies.size() == 1, "Official placement did not add exactly one structure");
        var restored = level.getBlockEntity(bodies.getFirst().getPlot().getCenterBlock());
        test.assertTrue(restored instanceof HeartBlockEntity, "Official placement lost its Heart Core");
        return (HeartBlockEntity) restored;
    }

    private static BlueprintFaction foreign(UUID world, UUID faction, String name) {
        return new BlueprintFaction(world, faction, name, true, false, false, false, List.of());
    }

    private static FactionRegistry reload(GameTestHelper test, FactionRegistry registry) {
        var registries = test.getLevel().registryAccess();
        return FactionRegistry.load(registry.save(new CompoundTag(), registries), registries);
    }

    private static ServerPlayer actor(GameTestHelper test, String name, boolean operator) {
        var player = new FakePlayer(test.getLevel(), new GameProfile(UUID.randomUUID(), name)) {
            @Override protected int getPermissionLevel() { return operator ? 2 : 0; }
        };
        player.setGameMode(GameType.CREATIVE);
        return player;
    }
}
