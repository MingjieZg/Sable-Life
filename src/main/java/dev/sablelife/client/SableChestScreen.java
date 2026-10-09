package dev.sablelife.client;

import dev.sablelife.SableLife;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Sable Life's copper-and-amethyst chassis around the familiar chest slot layout. */
@EventBusSubscriber(modid = SableLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SableChestScreen extends AbstractContainerScreen<ChestMenu> {
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(SableLife.LOOT_CHEST.get(), SableChestScreen::new);
    }
    public SableChestScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 114 + menu.getRowCount() * 18;
        inventoryLabelY = menu.getRowCount() * 18 + 20;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos, rows = menu.getRowCount();
        // Pixel-built frame stays crisp at every Minecraft GUI scale.
        graphics.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF17171E);
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF9B614E);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFF513B39);
        graphics.fill(x + 5, y + 5, x + imageWidth - 5, y + imageHeight - 5, 0xFF292B35);
        graphics.fill(x + 6, y + 5, x + imageWidth - 6, y + 16, 0xFF41354E);
        graphics.fill(x + 6, y + rows * 18 + 17, x + imageWidth - 6, y + rows * 18 + 29, 0xFF38353F);
        // Amethyst seams and copper rivets recall the life core and release tool.
        graphics.fill(x + 3, y + 15, x + 5, y + imageHeight - 15, 0xFF9670BB);
        graphics.fill(x + imageWidth - 5, y + 15, x + imageWidth - 3, y + imageHeight - 15, 0xFF9670BB);
        for (int dx : new int[]{1, imageWidth - 5}) for (int dy : new int[]{1, imageHeight - 5}) {
            graphics.fill(x + dx, y + dy, x + dx + 4, y + dy + 4, 0xFF352C30);
            graphics.fill(x + dx, y + dy, x + dx + 2, y + dy + 2, 0xFFD9A278);
        }
        for (int row = 0; row < rows; row++) for (int column = 0; column < 9; column++) {
            int sx = x + 7 + column * 18, sy = y + 17 + row * 18;
            slot(graphics, sx, sy);
            if (rows == 6 && menu.getType() == SableLife.LOOT_CHEST.get()) {
                int border = row < 3 ? 0xFF77DDA8 : 0xFFE3A56C, inside = row < 3 ? 0xFF243F37 : 0xFF4A352B;
                graphics.fill(sx, sy, sx + 18, sy + 1, border);
                graphics.fill(sx, sy + 17, sx + 18, sy + 18, border);
                graphics.fill(sx, sy, sx + 1, sy + 18, border);
                graphics.fill(sx + 17, sy, sx + 18, sy + 18, border);
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, inside);
            }
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            slot(graphics, x + 7 + column * 18, y + rows * 18 + 30 + row * 18);
        for (int column = 0; column < 9; column++) slot(graphics, x + 7 + column * 18, y + rows * 18 + 88);
    }
    private void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF15151D);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF53505C);
        graphics.fill(x + 1, y + 17, x + 18, y + 18, 0xFF958790);
        graphics.fill(x + 17, y + 1, x + 18, y + 17, 0xFF958790);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, menu.getType() == SableLife.LOOT_CHEST.get() ? 0x77DDA8 : 0xF2DDD1, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xD5C3E4, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (menu.getType() == SableLife.LOOT_CHEST.get() && hoveredSlot != null && hoveredSlot.index < 54 && !hoveredSlot.hasItem()) {
            graphics.renderTooltip(font, Component.translatable(hoveredSlot.index < 27 ? "menu.sablelife.template_zone" : "menu.sablelife.settings_zone")
                    .withStyle(hoveredSlot.index < 27 ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GOLD), mouseX, mouseY);
        }
    }
}
