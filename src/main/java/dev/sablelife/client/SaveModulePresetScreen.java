package dev.sablelife.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SaveModulePresetScreen extends Screen {
    private final HeartDashboardScreen parent;
    private EditBox name;
    private int x,y;
    private boolean failed;
    public SaveModulePresetScreen(HeartDashboardScreen parent){super(Component.translatable("module_picker.sablelife.save"));this.parent=parent;}
    @Override protected void init(){
        x=(width-330)/2;y=(height-92)/2;
        name=addRenderableWidget(new EditBox(font,x+10,y+35,185,18,Component.translatable("module_picker.sablelife.name")));name.setMaxLength(40);name.setHint(Component.translatable("module_picker.sablelife.name"));
        var confirm=addRenderableWidget(LifeUiStyle.button(Component.translatable("module_picker.sablelife.confirm"),x+201,y+35,54,18,()->{if(parent.savePreset(name.getValue()))onClose();else failed=true;}));confirm.active=false;name.setResponder(value->confirm.active=!value.isBlank());
        addRenderableWidget(LifeUiStyle.button(Component.translatable("module_picker.sablelife.cancel"),x+261,y+35,59,18,this::onClose));setInitialFocus(name);
    }
    @Override public void tick(){if(minecraft.player==null||minecraft.player.containerMenu!=parent.getMenu())minecraft.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){super.renderBackground(g,mx,my,partial);g.fill(x-2,y-2,x+332,y+94,0xFF9B614E);g.fill(x,y,x+330,y+92,0xFF242632);g.drawString(font,title,x+10,y+12,0xFFE6D8EA,false);if(failed)g.drawString(font,Component.translatable("module_picker.sablelife.failed"),x+10,y+64,0xFFFF7777,false);}
}
