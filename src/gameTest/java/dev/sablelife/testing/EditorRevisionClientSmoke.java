package dev.sablelife.testing;

import dev.sablelife.client.CommandProgramScreen;
import dev.sablelife.client.SequenceScreen;
import dev.sablelife.module.HeartDashboardMenu;
import dev.sablelife.module.HeartModules;
import dev.sablelife.module.SequenceMenu;
import dev.sablelife.module.VirtualModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@EventBusSubscriber(modid = "sablelife_tests", value = Dist.CLIENT)
public final class EditorRevisionClientSmoke {
    private static boolean tested;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) throws ReflectiveOperationException {
        if (tested || !Boolean.getBoolean("sablelife.editorRevisionSmoke")) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen == null || minecraft.getOverlay() != null) return;
        tested = true;
        checkCommandDraft(minecraft);
        checkSequenceDraft(minecraft);
        checkMessages(minecraft);
        minecraft.stop();
    }

    private static void checkMessages(Minecraft minecraft) {
        String locale = minecraft.getLanguageManager().getSelected();
        String update = Component.translatable("sequence.sablelife.editor_update_required").getString();
        String stale = Component.translatable("sequence.sablelife.editor_stale").getString();
        String oversized = dev.sablelife.module.ProjectileItemMenu.oversizedModelLabel().getString();
        check(!update.startsWith("sequence.sablelife.") && !stale.startsWith("sequence.sablelife.")
                && !oversized.startsWith("display_item.sablelife."), "An editor message did not resolve");
        if (locale.equals("zh_cn"))
            check(update.contains("更新") && stale.contains("序列") && oversized.contains("模型"), "Editor messages did not load in Simplified Chinese");
        System.out.println("EDITOR_MESSAGES_LOCALIZED " + locale);
    }

    private static void checkCommandDraft(Minecraft minecraft) throws ReflectiveOperationException {
        var modules = new HeartModules();
        var module = modules.add("command");
        var program = module.commands();
        program.edit("add", 0, 0, "");
        program.edit("add", 0, 0, "");
        var replacement = program.rows().getLast().id();
        var inventory = new Inventory(null);
        var menu = new HeartDashboardMenu(1, inventory);
        publish(menu, commandSnapshot(modules));
        var screen = new CommandProgramScreen(menu, inventory, Component.empty(), module.id());
        screen.init(minecraft, minecraft.screen.width, minecraft.screen.height);
        var field = (EditBox) field(screen, "command").get(screen);
        // The fixture uses the real EditBox and screen without a player connection for command suggestions.
        field.setResponder(ignored -> {});
        screen.setFocused(field);
        field.setFocused(true);
        field.setValue("say draft for original row");
        invokeSync(screen);
        check(field.isFocused() && field.getValue().equals("say draft for original row"),
                "An unchanged command snapshot discarded a valid draft");
        field(screen, "deleteUntil").setInt(screen, 60);
        program.edit("delete", 0, 0, "");
        modules.changed();
        publish(menu, commandSnapshot(modules));
        invokeSync(screen);
        check(replacement.equals(field(screen, "selected").get(screen)), "Missing commanded row did not select the expected survivor");
        boolean discarded = !field.isFocused() && field.getValue().isEmpty()
                && field(screen, "deleteUntil").getInt(screen) == 0;
        check(discarded, "A draft or confirmation moved to the surviving command row");
        System.out.println("EDITOR_COMMAND_DRAFT_SAFE");
    }

    private static void checkSequenceDraft(Minecraft minecraft) throws ReflectiveOperationException {
        var modules = new HeartModules();
        var sequence = modules.add("sequence");
        sequence.editSequence("add", 0, 0);
        sequence.editSequence("add", 0, 0);
        sequence.editSequence("time", 1, 3);
        var inventory = new Inventory(null);
        var menu = new SequenceMenu(2, inventory);
        publish(menu, sequenceSnapshot(modules, sequence));
        var screen = new SequenceScreen(menu, inventory, Component.empty());
        screen.init(minecraft, minecraft.screen.width, minecraft.screen.height);
        @SuppressWarnings("unchecked")
        var fields = (List<EditBox>) field(screen, "fields").get(screen);
        var duration = fields.get(2);
        screen.setFocused(duration);
        duration.setFocused(true);
        duration.setValue("5.00");
        invokeSync(screen);
        check(duration.isFocused() && duration.getValue().equals("5.00"),
                "An unchanged sequence snapshot discarded a valid draft");
        sequence.editSequence("delete", 0, 0);
        modules.changed();
        publish(menu, sequenceSnapshot(modules, sequence));
        invokeSync(screen);
        boolean discarded = !duration.isFocused() && duration.getValue().equals("3.00");
        check(discarded, "A draft moved to the surviving sequence step");
        System.out.println("EDITOR_SEQUENCE_DRAFT_SAFE");
    }

    private static CompoundTag commandSnapshot(HeartModules modules) {
        var tag = modules.save();
        tag.putBoolean("Ready", true);
        tag.putBoolean("Editable", true);
        tag.putBoolean("CommandEditable", true);
        tag.putBoolean("CommandAllowed", true);
        return tag;
    }

    private static CompoundTag sequenceSnapshot(HeartModules modules, VirtualModule sequence) {
        var tag = new CompoundTag();
        tag.putBoolean("Editable", true);
        tag.putInt("SequenceRevision", modules.revision() + 1);
        tag.putInt("Revision", modules.revision());
        tag.put("Module", sequence.save());
        return tag;
    }

    private static void publish(AbstractContainerMenu menu, CompoundTag tag) {
        var stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        menu.getSlot(0).set(stack);
    }

    private static Field field(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invokeSync(Object target) throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod("sync");
        method.setAccessible(true);
        method.invoke(target);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
