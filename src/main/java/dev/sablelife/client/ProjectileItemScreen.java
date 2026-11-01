package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.ProjectileItemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid=SableLife.MOD_ID, bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class ProjectileItemScreen extends AbstractContainerScreen<ProjectileItemMenu> {
    private Button reset;
    public ProjectileItemScreen(ProjectileItemMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); imageWidth=182; imageHeight=184; }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) { event.register(SableLife.PROJECTILE_MENU.get(), ProjectileItemScreen::new); }
    private Component tr(String key) { return Component.translatable((menu.displayOnly()&&java.util.Set.of("title","help","default").contains(key)?"display_item.sablelife.":"projectile.sablelife.")+key); }
    private void send(int button) { if(minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,button); }
    @Override protected void init() {
        super.init();
        reset=addRenderableWidget(LifeUiStyle.button(tr("default"),leftPos+10,topPos+72,78,18,()->send(1)));
        addRenderableWidget(LifeUiStyle.button(tr("back"),leftPos+94,topPos+72,78,18,()->send(0)));
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        reset.active=menu.editable();
        g.fill(leftPos-2,topPos-2,leftPos+184,topPos+186,0xFF19151D);g.fill(leftPos,topPos,leftPos+182,topPos+184,0xFF9B614E);
        g.fill(leftPos+3,topPos+3,leftPos+179,topPos+181,0xFF242632);
        frame(g,81,39,0xFFAA79C8);
        for(int row=0;row<3;row++) for(int col=0;col<9;col++) frame(g,9+col*18,98+row*18,0xFF706B78);
        for(int col=0;col<9;col++) frame(g,9+col*18,156,0xFF706B78);
    }
    private void frame(GuiGraphics g,int x,int y,int color) {g.fill(leftPos+x,topPos+y,leftPos+x+18,topPos+y+18,color);g.fill(leftPos+x+1,topPos+y+1,leftPos+x+17,topPos+y+17,0xFF403B49);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y) {g.drawString(font,tr("title"),10,8,0xE6D8EA,false);g.drawWordWrap(font,tr("help"),10,22,162,0xBDA5D0);}
    @Override public void render(GuiGraphics g,int x,int y,float partial) {renderBackground(g,x,y,partial);super.render(g,x,y,partial);renderTooltip(g,x,y);}
}
