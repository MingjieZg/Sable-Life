package dev.sablelife.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Client-only preference: never changes world configuration or GUI input coordinates. */
public final class LifeUiStyle {
    private static int density = loadDensity();
    public static int density() { return density; }
    public static float scale() { return density == 0 ? 1f : density == 1 ? .85f : density == 2 ? .75f : .5f; }
    private static java.nio.file.Path file() { return Minecraft.getInstance().gameDirectory.toPath().resolve("config/sablelife/ui-density.txt"); }
    private static int loadDensity() {
        try { return Math.clamp(Integer.parseInt(java.nio.file.Files.readString(file()).trim()), 0, 3); }
        catch (java.io.IOException | NumberFormatException ignored) { return 1; }
    }
    public static void cycle() {
        cycle(1);
    }
    public static void cycle(int direction) {
        density = Math.floorMod(density + direction, 4);
        try { java.nio.file.Files.createDirectories(file().getParent()); java.nio.file.Files.writeString(file(), Integer.toString(density)); }
        catch (java.io.IOException failure) { com.mojang.logging.LogUtils.getLogger().warn("Could not save UI density", failure); }
    }
    public static Button button(Component label, int x, int y, int width, int height, Runnable action) { return new StyledButton(label, x, y, width, height, action); }
    public static void reverse(Button button, Runnable action) { if (button instanceof StyledButton styled) styled.reverse = action; }
    public static void bookmark(Button button, int color) { if (button instanceof StyledButton styled) styled.bookmark = color; }
    public static void fill(Button button,int color){if(button instanceof StyledButton styled)styled.fill=color;}
    private static final class StyledButton extends Button {
        private Runnable reverse;
        private int bookmark;
        private int fill;
        StyledButton(Component label, int x, int y, int width, int height, Runnable action) { super(x, y, width, height, label, b -> action.run(), DEFAULT_NARRATION); }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button == 1 && reverse != null && active && visible && isMouseOver(x, y)) {
                playDownSound(Minecraft.getInstance().getSoundManager()); reverse.run(); return true;
            }
            return super.mouseClicked(x, y, button);
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            var font = Minecraft.getInstance().font;
            int x = getX(), y = getY();
            g.fill(x, y, x + getWidth(), y + getHeight(), active && isHoveredOrFocused() ? 0xFFD2BADF : 0xFF16141C);
            g.fill(x + 1, y + 1, x + getWidth() - 1, y + getHeight() - 1, active ? (fill==0?0xFF625968:fill) : 0xFF34313B);
            int inset = bookmark == 0 ? 0 : 7;
            if (bookmark != 0) {
                // An inset bookmark keeps the original hover/focus outline visible.
                int tint = active ? bookmark : 0xFF000000 | ((bookmark & 0xFEFEFE) >> 1);
                g.fill(x + 2, y + 2, x + 6, y + getHeight() - 2, tint);
                g.fill(x + 2, y + 2, x + 3, y + getHeight() - 3, active ? 0x55FFFFFF : 0x22FFFFFF);
            }
            float scale = Math.min(LifeUiStyle.scale(), (getWidth() - inset - 6f) / Math.max(1, font.width(getMessage())));
            g.pose().pushPose(); g.pose().translate(x + inset + (getWidth() - inset - font.width(getMessage()) * scale) / 2f, y + (getHeight() - font.lineHeight * scale) / 2f, 0);
            g.pose().scale(scale, scale, 1); g.drawString(font, getMessage(), 0, 0, active ? 0xEEE4F5 : 0x827C89, false); g.pose().popPose();
        }
    }
}
