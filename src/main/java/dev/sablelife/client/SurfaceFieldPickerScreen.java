package dev.sablelife.client;

import dev.sablelife.module.VirtualModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.IntConsumer;

/** Picks an existing template; row numbers are resolved to UUIDs by the server. */
public final class SurfaceFieldPickerScreen extends Screen {
    private final Screen parent;private final List<VirtualModule> modules;private final UUID selected;private final IntConsumer choose;
    private final java.util.Set<String> allowed;
    private String query="";private int page,x,y,w,h;private EditBox search;
    public SurfaceFieldPickerScreen(Screen parent,List<VirtualModule> modules,UUID selected,IntConsumer choose){this(parent,modules,selected,choose,java.util.Set.of("surface_field"),"impactArea");}
    public SurfaceFieldPickerScreen(Screen parent,List<VirtualModule> modules,UUID selected,IntConsumer choose,java.util.Set<String> allowed,String title){super(tr(title));this.parent=parent;this.modules=List.copyOf(modules);this.selected=selected;this.choose=choose;this.allowed=java.util.Set.copyOf(allowed);}
    private static Component tr(String key){return Component.translatable("dashboard.sablelife."+key);}
    @Override protected void init(){
        w=Math.min(460,width-16);h=Math.min(330,height-16);x=(width-w)/2;y=(height-h)/2;
        addRenderableWidget(LifeUiStyle.button(tr("back"),x+w-68,y+8,60,20,this::onClose));
        search=addRenderableWidget(new EditBox(font,x+8,y+36,w-16,20,tr(allowed.contains("surface_field")?"field_search":"summon_search")));search.setHint(tr(allowed.contains("surface_field")?"field_search":"summon_search"));search.setMaxLength(80);search.setValue(query);
        search.setResponder(q->{query=q;page=0;rebuildWidgets();setFocused(search);search.setFocused(true);});
        var choices=modules.stream().filter(m->allowed.contains(m.type())&&(name(m)+" #"+(modules.indexOf(m)+1)).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int rows=Math.max(1,(h-116)/25);page=Math.clamp(page,0,Math.max(0,(choices.size()-1)/rows));
        addRenderableWidget(LifeUiStyle.button(tr("field_none"),x+8,y+62,w-16,20,()->select(0)));
        for(int i=0;i<rows&&page*rows+i<choices.size();i++){var m=choices.get(page*rows+i);addRenderableWidget(LifeUiStyle.button(Component.literal((m.id().equals(selected)?"▶ ":"")+name(m)+" #"+(modules.indexOf(m)+1)),x+8,y+88+i*25,w-16,22,()->select(modules.indexOf(m)+1)));}
        addRenderableWidget(LifeUiStyle.button(Component.literal("‹"),x+8,y+h-25,32,18,()->{page--;rebuildWidgets();})).active=page>0;
        addRenderableWidget(LifeUiStyle.button(Component.literal("›"),x+w-40,y+h-25,32,18,()->{page++;rebuildWidgets();})).active=(page+1)*rows<choices.size();
    }
    private String name(VirtualModule m){return m.name().isBlank()?tr("type_"+m.type()).getString():m.name();}
    private void select(int index){choose.accept(index);onClose();}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(x-2,y-2,x+w+2,y+h+2,0xFF9B614E);g.fill(x,y,x+w,y+h,0xFF242632);g.drawString(font,title,x+8,y+12,0xFFD9C4ED,false);super.render(g,mx,my,partial);}
}
