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
    private List<String> codes=List.of();
    private int x,y,panelWidth,panelHeight,page,pageSize;
    private boolean positioned;
    public LanguagePickerScreen(Screen parent){
        super(Component.translatable("language_picker.sablelife.title"));this.parent=parent;
        for(int i=0;i<parent.children().size();i++)if(parent.children().get(i) instanceof EditBox box&&box.isFocused())drafts.put(i,box.getValue());
    }
    private Component tr(String key){return Component.translatable("language_picker.sablelife."+key);}
    private boolean validParent(){return !(parent instanceof AbstractContainerScreen<?> container)||minecraft.player!=null&&minecraft.player.containerMenu==container.getMenu();}
    @Override protected void init(){
        var available=minecraft.getLanguageManager().getLanguages();
        var pinned=List.of("en_us","zh_cn","ru_ru");
        var found=new HashSet<String>();found.add("en_us");
        for(var id:minecraft.getResourceManager().listResources("lang",id->id.getNamespace().equals("sablelife")&&id.getPath().matches("lang/[a-z0-9_]+\\.json")).keySet()){
            String code=id.getPath().substring(5,id.getPath().length()-5);
            if(available.containsKey(code))found.add(code);
        }
        codes=found.stream().sorted(Comparator.comparingInt((String code)->pinned.contains(code)?pinned.indexOf(code):pinned.size())
                .thenComparing(code->available.get(code).name(),String.CASE_INSENSITIVE_ORDER).thenComparing(code->code)).toList();
        int selectedIndex=Math.max(0,codes.indexOf(minecraft.getLanguageManager().getSelected()));
        int previousSize=pageSize,previousFirst=page*pageSize;
        boolean selectedVisible=selectedIndex>=previousFirst&&selectedIndex<previousFirst+pageSize;
        pageSize=Math.max(1,Math.min(6,(height-88)/25));
        if(!positioned){page=selectedIndex/pageSize;positioned=true;}
        else if(previousSize!=pageSize)page=(selectedVisible?selectedIndex:previousFirst)/pageSize;
        page=Math.clamp(page,0,pageCount()-1);
        panelWidth=Math.min(300,width-12);panelHeight=76+Math.min(pageSize,codes.size())*25;
        x=(width-panelWidth)/2;y=(height-panelHeight)/2;buttons.clear();
        for(int i=0;i<pageSize&&page*pageSize+i<codes.size();i++){
            String code=codes.get(page*pageSize+i);boolean selected=minecraft.getLanguageManager().getSelected().equals(code);
            var label=Component.literal((selected?"✓ ":"")+available.get(code).name());
            var b=addRenderableWidget(LifeUiStyle.button(label,x+12,y+42+i*25,panelWidth-24,21,()->choose(code)));
            b.active=!busy&&!selected;buttons.add(b);
        }
        int footer=y+panelHeight-29;
        var previous=addRenderableWidget(LifeUiStyle.button(Component.literal("‹"),x+12,footer,22,21,()->changePage(-1)));
        previous.visible=pageCount()>1;previous.active=!busy&&page>0;buttons.add(previous);
        var next=addRenderableWidget(LifeUiStyle.button(Component.literal("›"),x+104,footer,22,21,()->changePage(1)));
        next.visible=pageCount()>1;next.active=!busy&&page+1<pageCount();buttons.add(next);
        var back=addRenderableWidget(LifeUiStyle.button(tr("back"),x+panelWidth-82,footer,70,21,this::onClose));back.active=!busy;buttons.add(back);
    }
    private int pageCount(){return Math.max(1,(codes.size()+pageSize-1)/Math.max(1,pageSize));}
    private void changePage(int direction){page=Math.clamp(page+direction,0,pageCount()-1);rebuildWidgets();}
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
        super.renderBackground(g,mx,my,partial);g.fill(x-2,y-2,x+panelWidth+2,y+panelHeight+2,0xFF9B614E);g.fill(x,y,x+panelWidth,y+panelHeight,0xFF242632);
        g.drawString(font,title,x+12,y+10,0xFFE6D8EA,false);
        g.drawString(font,tr(busy?"loading":failed?"failed":"global"),x+12,y+26,failed?0xFFFF7777:0xFFAEB8C7,false);
        if(pageCount()>1)g.drawCenteredString(font,Component.literal((page+1)+" / "+pageCount()),x+69,y+panelHeight-23,0xFFD9C4ED);
    }
}
