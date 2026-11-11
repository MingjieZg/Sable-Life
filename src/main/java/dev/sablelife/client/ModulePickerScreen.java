package dev.sablelife.client;

import dev.sablelife.module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Column-major catalog: fill rows downward, then continue in the next column. */
public final class ModulePickerScreen extends Screen {
    private final HeartDashboardScreen parent;
    private List<String> types;
    private int datapackRevision;
    private String category="all",query="";
    private EditBox search;
    private final Map<String,String> english=new HashMap<>();
    private boolean followSorted;
    private record Choice(String type,String name,ModulePresetLibrary.Entry preset){}
    private Choice selected;
    private int page,x,y,w,h,ticks,deleteUntil;
    private long lastClick;
    private net.minecraft.client.gui.components.Button load,delete;
    private Component error=Component.empty();
    public ModulePickerScreen(HeartDashboardScreen parent,List<String> types){super(Component.translatable("dashboard.sablelife.choose_type"));this.parent=parent;this.types=List.copyOf(types);}
    private Component tr(String key){return Component.translatable("module_picker.sablelife."+key);}
    @Override protected void init(){
        datapackRevision = DatapackModules.clientRevision();
        w=Math.min(620,width-12);h=Math.min(370,height-12);x=(width-w)/2;y=(height-h)/2;
        addRenderableWidget(LifeUiStyle.button(tr("back"),x+w-66,y+8,58,18,this::onClose));
        if(english.isEmpty())try(var reader=minecraft.getResourceManager().openAsReader(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sablelife","lang/en_us.json"))){com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e->english.put(e.getKey(),e.getValue().getAsString()));}catch(java.io.IOException ignored){}
        int searchWidth=Math.min(170,Math.max(95,(w-16)/3));
        search=addRenderableWidget(new EditBox(font,x+8,y+34,searchWidth,18,tr("search")));search.setMaxLength(80);search.setHint(tr("search"));search.setValue(query);
        search.setResponder(value->{query=value;page=0;selected=null;deleteUntil=0;lastClick=0;rebuildWidgets();setFocused(search);search.setFocused(true);});
        String[] categories={"all","signal","weapon","function","preset"};int categoryWidth=w-24-searchWidth;
        for(int i=0;i<categories.length;i++){var key=categories[i];int start=categoryWidth*i/categories.length,end=categoryWidth*(i+1)/categories.length;var b=addRenderableWidget(LifeUiStyle.button(tr(key),x+16+searchWidth+start,y+34,end-start-(i+1<categories.length?3:0),18,()->{category=key;page=0;selected=null;deleteUntil=0;rebuildWidgets();}));b.active=!category.equals(key);if(List.of("signal","weapon","function").contains(key))LifeUiStyle.bookmark(b,ModuleCategory.valueOf(key.toUpperCase(Locale.ROOT)).color);}
        var choices=new ArrayList<Choice>();
        if(category.equals("preset")){for(var preset:ModulePresetLibrary.load())if(types.contains(preset.module().getString("Type")))choices.add(new Choice(preset.module().getString("Type"),preset.name(),preset));}
        else for(String type:types)if(category.equals("all")||ModuleCategory.of(type).key.equals(category))choices.add(new Choice(type,"",null));
        String needle=query.strip().toLowerCase(Locale.ROOT);
        choices.removeIf(c->{
            var datapack = DatapackModules.clientPresentation(c.type);
            String englishName = datapack == null ? english.getOrDefault(ModuleText.nameKey(c.type), "") : datapack.name().english();
            return !(c.name+" "+c.type+" "+ModuleText.name(c.type).getString()+" "+englishName).toLowerCase(Locale.ROOT).contains(needle);
        });
        int columns=Math.max(3,(w-16)/125),rows=category.equals("preset")?Math.max(1,(h-102)/24):Math.max(1,Math.min((h-102)/13,(choices.size()+columns-1)/columns)),capacity=columns*rows;
        int pitch=Math.min(24,Math.max(10,(h-102)/rows));
        if(followSorted&&selected!=null){for(int i=0;i<choices.size();i++)if(choices.get(i).name.equals(selected.name)){page=i/capacity;break;}followSorted=false;}
        page=Math.clamp(page,0,Math.max(0,(choices.size()-1)/capacity));int gridWidth=w-16;
        for(int i=0;i<capacity&&page*capacity+i<choices.size();i++){
            int col=i/rows,start=gridWidth*col/columns,end=gridWidth*(col+1)/columns;var choice=choices.get(page*capacity+i);var label=choice.preset==null?ModuleText.name(choice.type):Component.literal(choice.name);
            var b=addRenderableWidget(LifeUiStyle.button(Component.literal(selected!=null&&selected.type.equals(choice.type)&&selected.name.equals(choice.name)?"▶ ":"").append(label),x+8+start,y+62+(i%rows)*pitch,end-start-(col+1<columns?5:0),pitch-3,()->{long now=net.minecraft.Util.getMillis();boolean twice=selected!=null&&selected.type.equals(choice.type)&&selected.name.equals(choice.name)&&now-lastClick<300;selected=choice;lastClick=now;deleteUntil=0;error=Component.empty();if(twice)loadSelected();else rebuildWidgets();}));
            LifeUiStyle.bookmark(b,ModuleCategory.of(choice.type).color);b.setTooltip(Tooltip.create(label.copy().append("\n").append(ModuleText.description(choice.type))));
        }
        if(choices.isEmpty())addRenderableWidget(LifeUiStyle.button(tr(query.isBlank()?"empty":"no_matches"),x+8,y+62,w-16,21,()->{})).active=false;
        addRenderableWidget(LifeUiStyle.button(Component.literal("‹"),x+8,y+h-25,28,18,()->{page--;selected=null;rebuildWidgets();})).active=page>0;
        addRenderableWidget(LifeUiStyle.button(Component.literal("›"),x+w-36,y+h-25,28,18,()->{page++;selected=null;rebuildWidgets();})).active=(page+1)*capacity<choices.size();
        int sortSpace=category.equals("preset")?52:0;int bw=Math.min(120,(w-92-sortSpace)/2),center=x+(w-sortSpace)/2;
        load=addRenderableWidget(LifeUiStyle.button(tr("load"),center-bw-3,y+h-25,bw,18,this::loadSelected));load.active=selected!=null&&parent.canAddModule();
        delete=addRenderableWidget(LifeUiStyle.button(tr(deleteUntil>ticks?"confirm_delete":"delete"),center+3,y+h-25,bw,18,()->{
            if(selected==null||selected.preset==null)return;
            if(deleteUntil<=ticks){deleteUntil=ticks+40;delete.setMessage(tr("confirm_delete"));return;}
            try{ModulePresetLibrary.delete(selected.name);selected=null;deleteUntil=0;rebuildWidgets();}catch(java.io.IOException e){error=tr("failed");}
        }));delete.active=category.equals("preset")&&selected!=null&&selected.preset!=null;delete.setTooltip(Tooltip.create(tr("delete_help")));
        if(category.equals("preset")){
            int index=-1;for(int i=0;i<choices.size();i++)if(selected!=null&&choices.get(i).name.equals(selected.name)){index=i;break;}
            int selectedIndex=index;int sortX=center+3+bw+5;
            var up=addRenderableWidget(LifeUiStyle.button(Component.literal("↑"),sortX,y+h-25,22,18,()->sortPreset(choices.get(selectedIndex-1).name)));up.active=index>0;up.setTooltip(Tooltip.create(tr("move_up")));
            var down=addRenderableWidget(LifeUiStyle.button(Component.literal("↓"),sortX+25,y+h-25,22,18,()->sortPreset(choices.get(selectedIndex+1).name)));down.active=index>=0&&index<choices.size()-1;down.setTooltip(Tooltip.create(tr("move_down")));
        }

    }
    private void sortPreset(String neighbor){if(selected==null||selected.preset==null)return;try{ModulePresetLibrary.moveRelative(selected.name,neighbor);deleteUntil=0;lastClick=0;followSorted=true;rebuildWidgets();}catch(java.io.IOException e){error=tr("failed");}}
    private void loadSelected(){if(selected==null||!parent.canAddModule())return;if(selected.preset==null)parent.addType(selected.type);else parent.addPreset(selected.preset.module());onClose();}
    @Override public void tick(){if(!types.equals(parent.availableTypes())||datapackRevision!=DatapackModules.clientRevision()){types=List.copyOf(parent.availableTypes());selected=null;rebuildWidgets();}ticks++;if(deleteUntil!=0&&deleteUntil<=ticks){deleteUntil=0;delete.setMessage(tr("delete"));}if(minecraft.player==null||minecraft.player.containerMenu!=parent.getMenu())minecraft.setScreen(null);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){super.renderBackground(g,mx,my,partial);g.fill(x-2,y-2,x+w+2,y+h+2,0xFF9B614E);g.fill(x,y,x+w,y+h,0xFF242632);g.drawString(font,title,x+8,y+12,0xFFE6D8EA,false);if(!error.getString().isEmpty())g.drawString(font,error,x+8,y+h-39,0xFFFF7777,false);}
}
