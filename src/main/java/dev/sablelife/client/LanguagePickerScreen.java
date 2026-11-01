package dev.sablelife.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Uses Minecraft's resource reload; does not close the container or reset editor navigation. */
public final class LanguagePickerScreen extends Screen {
    private final Screen parent;
    private final Map<Integer,String> drafts=new HashMap<>();
    private final List<Button> buttons=new ArrayList<>();
    private boolean busy,failed,readyToReturn;
    private int x,y;
    public LanguagePickerScreen(Screen parent){
        super(Component.translatable("language_picker.sablelife.title"));this.parent=parent;
        for(int i=0;i<parent.children().size();i++)if(parent.children().get(i) instanceof EditBox box&&box.isFocused())drafts.put(i,box.getValue());
    }
    private Component tr(String key){return Component.translatable("language_picker.sablelife."+key);}
    private boolean validParent(){return !(parent instanceof AbstractContainerScreen<?> container)||minecraft.player!=null&&minecraft.player.containerMenu==container.getMenu();}
    @Override protected void init(){
        x=(width-300)/2;y=(height-128)/2;buttons.clear();
        String[] codes={"en_us","zh_cn"};
        for(int i=0;i<codes.length;i++){String code=codes[i];var b=addRenderableWidget(LifeUiStyle.button(Component.literal(code.equals("en_us")?"English":"简体中文"),x+12,y+35+i*25,276,21,()->choose(code)));b.active=!busy&&!minecraft.getLanguageManager().getSelected().equals(code);buttons.add(b);}
        var back=addRenderableWidget(LifeUiStyle.button(tr("back"),x+218,y+97,70,18,this::onClose));back.active=!busy;buttons.add(back);
    }
    private void choose(String code){
        if(busy||!validParent())return;busy=true;failed=false;buttons.forEach(b->b.active=false);
        String previous=minecraft.getLanguageManager().getSelected();
        minecraft.getLanguageManager().setSelected(code);minecraft.options.languageCode=code;
        minecraft.reloadResourcePacks().whenComplete((ignored,error)->minecraft.execute(()->{
            busy=false;
            if(error!=null){minecraft.getLanguageManager().setSelected(previous);minecraft.options.languageCode=previous;failed=true;rebuildWidgets();return;}
            minecraft.options.save();
            if(!validParent())return;
            // Resource completion still schedules a display/font rebuild. Return only after
            // the loading overlay disappears, otherwise that rebuild can erase restored drafts.
            readyToReturn=true;
        }));
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){if(!busy&&!validParent())minecraft.setScreen(null);else if(readyToReturn&&minecraft.getOverlay()==null)onClose();}
    @Override public void onClose(){
        if(busy||readyToReturn&&minecraft.getOverlay()!=null)return;if(!validParent()){minecraft.setScreen(null);return;}
        minecraft.setScreen(parent);
        drafts.forEach((index,value)->{if(index<parent.children().size()&&parent.children().get(index) instanceof EditBox box){box.setValue(value);parent.setFocused(box);box.setFocused(true);}});
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){
        super.renderBackground(g,mx,my,partial);g.fill(x-2,y-2,x+302,y+130,0xFF9B614E);g.fill(x,y,x+300,y+128,0xFF242632);
        g.drawString(font,title,x+12,y+12,0xFFE6D8EA,false);
        g.drawString(font,tr(busy?"loading":failed?"failed":"global"),x+12,y+101,failed?0xFFFF7777:0xFFAEB8C7,false);
    }
}
