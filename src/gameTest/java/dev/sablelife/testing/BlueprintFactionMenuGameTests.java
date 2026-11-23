package dev.sablelife.testing;

import com.mojang.authlib.GameProfile;
import dev.sablelife.SableLife;
import dev.sablelife.heart.BlueprintFaction;
import dev.sablelife.heart.FactionRegistry;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartSettingsMenu;
import dev.sablelife.module.ModuleNetworking;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sablelife_relations_tests")
@PrefixGameTestTemplate(false)
public final class BlueprintFactionMenuGameTests {
    private static final UUID NO_MODULE = new UUID(0, 0);

    @GameTest(template = "combat", batch = "blueprint_faction_menu_bind")
    public static void BindingChecksCurrentRulesAndNeverJoinsThePlayer(GameTestHelper test) {
        var core = core(test);
        var importer = actor(test, core, "BlueprintImport");
        var otherEditor = actor(test, core, "BlueprintEdit");
        var proprietor = actor(test, core, "BlueprintOwner");
        var registry = FactionRegistry.get(test.getLevel().getServer());
        var local = registry.create(proprietor, unique("Destination"));
        var source = new BlueprintFaction(UUID.randomUUID(), UUID.randomUUID(), "Source", true, false, true, false, List.of());
        core.relations().blueprintFaction(source);
        var menu = new HeartSettingsMenu(1, importer.getInventory(), core);
        var second = new HeartSettingsMenu(2, otherEditor.getInventory(), core);
        try {
            test.assertTrue(source.equals(BlueprintFaction.read(menu.snapshot().getCompound("Relations").getCompound(BlueprintFaction.TAG)))
                    && !menu.snapshot().getCompound("BlueprintFactionView").hasUUID("Bound"), "Menu lost the pending source or assigned it silently");
            int expected = revision(menu), stale = revision(second);
            test.assertTrue(local != null && registry.toggle(proprietor, local), "Could not modify current local faction rules");
            test.assertTrue(!send(test, menu, importer, "bp_bind", expected, 0, local.toString())
                    && menu.snapshot().getString("RelationStatus").equals("bp_stale"), "Changed local rules were accepted without review");
            test.assertTrue(send(test, menu, importer, "bp_bind", expected, 2, local.toString())
                    && local.equals(core.relations().faction()) && source.equals(core.relations().blueprintFaction()), "Valid public binding failed");
            test.assertTrue(!registry.detail(importer, local).getBoolean("Member"), "Blueprint binding silently joined player membership");
            test.assertTrue(!send(test, second, otherEditor, "bp_clear", stale, 0, "")
                    && local.equals(core.relations().faction()), "Concurrent old menu cleared a new assignment");
            otherEditor.setPos(core.getBlockPos().getCenter().add(100, 0, 0));
            test.assertTrue(!send(test, second, otherEditor, "bp_clear", revision(second), 0, ""), "Out-of-range editor unbound the core");
            test.assertTrue(send(test, menu, importer, "bp_clear", revision(menu), 0, "")
                    && core.relations().faction() == null && core.relations().blueprintFaction() == null
                    && local.equals(registry.resolveBlueprintFaction(importer, source)), "Unbind did not clear only this core");
            test.succeed();
        } finally {
            if (local != null) registry.delete(proprietor, local);
        }
    }

    @GameTest(template = "combat", batch = "blueprint_faction_menu_create")
    public static void CreationChecksFlagsAndPreservesTheWireNameLimit(GameTestHelper test) {
        var core = core(test);
        var importer = actor(test, core, "BlueprintCreate");
        var registry = FactionRegistry.get(test.getLevel().getServer());
        core.relations().blueprintFaction(BlueprintFaction.legacy(UUID.randomUUID()));
        var menu = new HeartSettingsMenu(2, importer.getInventory(), core);
        var supplementary = new String(Character.toChars(0x1F300 + Math.floorMod(importer.getUUID().hashCode(), 256)));
        String maxName = supplementary.repeat(40);
        try {
            int expected = revision(menu);
            test.assertTrue(!send(test, menu, importer, "bp_create", expected, Double.NaN, maxName)
                    && core.relations().faction() == null, "Non-integral rule flags were accepted");
            boolean rejected = false;
            try { send(test, menu, importer, "bp_create", expected, 5, maxName + "a"); }
            catch (RuntimeException invalidLength) { rejected = true; }
            test.assertTrue(rejected, "An 81-unit name escaped the existing action limit");
            test.assertTrue(send(test, menu, importer, "bp_create", expected, 5, maxName), "A valid 40-codepoint name failed at the wire limit");
            var id = core.relations().faction();
            var details = registry.detail(importer, id);
            test.assertTrue(id != null && maxName.equals(details.getString("Name")) && details.getBoolean("Private")
                    && !details.getBoolean("FriendlyFire") && details.getBoolean("AlliedFire"), "Chosen names or rule flags were not applied");
            test.assertTrue(!send(test, menu, importer, "bp_create", expected, 2, "Second click")
                    && id.equals(core.relations().faction()), "An old create request was accepted after binding");
            test.succeed();
        } finally {
            if (core.relations().faction() != null) registry.delete(importer, core.relations().faction());
        }
    }

    @GameTest(template = "combat", batch = "blueprint_faction_menu_alliance")
    public static void AlliesRequireTheShownMappingAndLocalBilateralConfirmation(GameTestHelper test) {
        var core = core(test);
        var importer = actor(test, core, "BlueprintAlly");
        var allyOwner = actor(test, core, "BlueprintOther");
        var registry = FactionRegistry.get(test.getLevel().getServer());
        var world = UUID.randomUUID();
        var sourceAlly = UUID.randomUUID();
        var ally = new BlueprintFaction(world, sourceAlly, "Imported Ally", true, false, false, false, List.of());
        core.relations().blueprintFaction(new BlueprintFaction(world, UUID.randomUUID(), "Imported Source",
                true, false, false, false, List.of(new BlueprintFaction.Ally(sourceAlly, ally.name()))));
        var localAlly = registry.create(allyOwner, unique("Local Ally"));
        var menu = new HeartSettingsMenu(3, importer.getInventory(), core);
        try {
            test.assertTrue(localAlly != null && registry.bindBlueprintFaction(importer, ally, localAlly)
                    && send(test, menu, importer, "bp_create", revision(menu), 0, unique("Local Source")), "Could not set up native mapping records");
            var local = core.relations().faction();
            var state = menu.snapshot().getCompound("BlueprintFactionView").getList("Allies", 10).getCompound(0);
            test.assertTrue(state.getUUID("Mapped").equals(localAlly) && !state.getBoolean("Allied"), "Mapping snapshot implied an alliance");
            test.assertTrue(!send(test, menu, importer, "bp_ally", revision(menu), 0, sourceAlly + "|" + UUID.randomUUID())
                    && menu.snapshot().getString("RelationStatus").equals("bp_stale"), "A stale ally mapping was accepted");
            test.assertTrue(send(test, menu, importer, "bp_ally", revision(menu), 0, sourceAlly + "|" + localAlly)
                    && menu.snapshot().getString("RelationStatus").equals("bp_ally_requested")
                    && !registry.allied(local, localAlly), "Alliance bypassed the local ally manager");
            test.assertTrue(registry.acceptAlliance(allyOwner, localAlly, local), "Native bilateral acceptance failed");
            menu.broadcastChanges();
            test.assertTrue(menu.snapshot().getCompound("BlueprintFactionView").getList("Allies", 10).getCompound(0).getBoolean("Allied"),
                    "Accepted alliance was not synced to the existing menu");
            test.succeed();
        } finally {
            if (core.relations().faction() != null) registry.delete(importer, core.relations().faction());
            if (localAlly != null) registry.delete(allyOwner, localAlly);
        }
    }

    private static HeartBlockEntity core(GameTestHelper test) {
        var pos = test.absolutePos(new BlockPos(2, 3, 2));
        test.getLevel().setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        return (HeartBlockEntity) test.getLevel().getBlockEntity(pos);
    }

    private static ServerPlayer actor(GameTestHelper test, HeartBlockEntity core, String name) {
        var actor = new FakePlayer(test.getLevel(), new GameProfile(UUID.randomUUID(), name));
        actor.setGameMode(GameType.CREATIVE);
        actor.setPos(core.getBlockPos().getCenter());
        return actor;
    }

    private static int revision(HeartSettingsMenu menu) {
        return menu.snapshot().getCompound("BlueprintFactionView").getInt("Revision");
    }

    private static String unique(String name) { return name + " " + UUID.randomUUID().toString().substring(0, 8); }

    private static boolean send(GameTestHelper test, HeartSettingsMenu menu, ServerPlayer actor, String action, int revision, double value, String text) {
        var packet = new ModuleNetworking.Action(menu.containerId, 0, NO_MODULE, "core", "relation:" + action + ":" + revision, value, text);
        var bytes = new RegistryFriendlyByteBuf(Unpooled.buffer(), test.getLevel().registryAccess());
        try {
            ModuleNetworking.Action.CODEC.encode(bytes, packet);
            var received = ModuleNetworking.Action.CODEC.decode(bytes);
            return menu.action(actor, received.key(), received.value(), received.text());
        } finally {
            bytes.release();
        }
    }
}
