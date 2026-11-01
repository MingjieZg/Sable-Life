package dev.sablelife.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.BiConsumer;

/** Shared in-game color picker. Edits are local until a single atomic Confirm or Presets action. */
public final class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final ColorPickerModel model;
    private final int original;
    private final BiConsumer<Boolean,Integer> apply;
    private final List<EditBox> numbers=new ArrayList<>();
    private boolean precise,syncing;
    private int x,y,w,h,size,sx,sy,hx,rx,drag;
    private Button confirm,presets;
    public ColorPickerScreen(Screen parent,Component field,int initial,int original,BiConsumer<Boolean,Integer> apply){
        super(Component.translatable("color_picker.sablelife.title",field));this.parent=parent;model=new ColorPickerModel(initial);this.original=original;this.apply=apply;
    }
    private Component tr(String key){return Component.translatable("color_picker.sablelife."+key);}
    private boolean validParent(){return !(parent instanceof AbstractContainerScreen<?> c)||minecraft.player!=null&&minecraft.player.containerMenu==c.getMenu();}
    @Override protected void init(){
        w=Math.min(350,width-16);h=Math.min(270,height-16);x=(width-w)/2;y=(height-h)/2;size=Math.max(48,Math.min(170,Math.min(w-145,h-90)));sx=x+12;sy=y+34;hx=sx+size+8;rx=hx+24;drag=0;numbers.clear();
        var detail=addRenderableWidget(LifeUiStyle.button(tr(precise?"hide_values":"precise"),rx,y+101,w-(rx-x)-12,18,()->{precise=!precise;rebuildWidgets();}));detail.setTooltip(Tooltip.create(tr("help")));
        for(int i=0;i<3;i++){var box=new EditBox(font,rx+16,y+125+i*21,w-(rx-x)-28,16,Component.literal("RGB"));box.setMaxLength(3);box.setFilter(s->s.matches("[0-9]{0,3}"));box.visible=precise;box.setResponder(s->numbersChanged());numbers.add(addRenderableWidget(box));}
        int bw=(w-32)/3;confirm=addRenderableWidget(LifeUiStyle.button(tr("confirm"),x+12,y+h-28,bw,18,()->finish(true)));
        presets=addRenderableWidget(LifeUiStyle.button(tr("presets"),x+16+bw,y+h-28,bw,18,()->finish(false)));
        addRenderableWidget(LifeUiStyle.button(tr("cancel"),x+20+2*bw,y+h-28,bw,18,this::onClose));syncNumbers();
    }
    private void syncNumbers(){syncing=true;int color=model.rgb();for(int i=0;i<numbers.size();i++){numbers.get(i).setValue(Integer.toString((color>>(16-8*i))&255));numbers.get(i).setTextColor(0xE0E0E0);}syncing=false;if(confirm!=null)confirm.active=true;if(presets!=null)presets.active=true;}
    private boolean validNumbers(){if(!precise)return true;if(numbers.size()!=3)return false;for(var box:numbers)try{int n=Integer.parseInt(box.getValue());if(n<0||n>255)return false;}catch(NumberFormatException e){return false;}return true;}
    private void numbersChanged(){
        if(syncing||numbers.size()!=3)return;boolean valid=validNumbers();if(confirm!=null)confirm.active=valid;if(presets!=null)presets.active=valid;
        for(var box:numbers)try{int n=Integer.parseInt(box.getValue());box.setTextColor(n<=255?0xE0E0E0:0xFF7777);}catch(NumberFormatException e){box.setTextColor(0xFF7777);}
        if(valid)model.rgb((Integer.parseInt(numbers.get(0).getValue())<<16)|(Integer.parseInt(numbers.get(1).getValue())<<8)|Integer.parseInt(numbers.get(2).getValue()));
    }
    private void finish(boolean custom){if(!validParent()||!validNumbers())return;apply.accept(custom,model.rgb());onClose();}
    private void pick(double mx,double my){if(drag==1)model.square((mx-sx)/(size-1),1-(my-sy)/(size-1));else if(drag==2)model.hue((my-sy)/(size-1));syncNumbers();}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0&&mx>=sx&&mx<sx+size&&my>=sy&&my<sy+size){drag=1;pick(mx,my);return true;}
        if(button==0&&mx>=hx&&mx<hx+14&&my>=sy&&my<sy+size){drag=2;pick(mx,my);return true;}
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(button==0&&drag!=0){pick(mx,my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(button==0&&drag!=0){drag=0;return true;}return super.mouseReleased(mx,my,button);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){if(!validParent())minecraft.setScreen(null);}
    @Override public void onClose(){minecraft.setScreen(validParent()?parent:null);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){
        super.renderBackground(g,mx,my,partial);g.fill(x-2,y-2,x+w+2,y+h+2,0xFF9B614E);g.fill(x,y,x+w,y+h,0xFF242632);g.drawString(font,title,x+12,y+12,0xFFE6D8EA,false);
        for(int i=0;i<size;i++)g.fillGradient(sx+i,sy,sx+i+1,sy+size,0xFF000000|ColorPickerModel.toRgb(model.hue(),i/(double)(size-1),1),0xFF000000);
        for(int i=0;i<size;i++)g.fill(hx,sy+i,hx+14,sy+i+1,0xFF000000|ColorPickerModel.toRgb(i/(double)(size-1),1,1));
        int cx=sx+(int)Math.round(model.saturation()*(size-1)),cy=sy+(int)Math.round((1-model.value())*(size-1));g.fill(cx-4,cy-4,cx+5,cy+5,0xFF111111);g.fill(cx-3,cy-3,cx+4,cy+4,0xFFFFFFFF);g.fill(cx-2,cy-2,cx+3,cy+3,0xFF000000|model.rgb());
        int hy=sy+(int)Math.round(model.hue()*(size-1));g.fill(hx-2,hy-2,hx+16,hy+3,0xFF111111);g.fill(hx-1,hy-1,hx+15,hy+2,0xFFFFFFFF);
        int previewW=Math.max(20,w-(rx-x)-12);g.drawString(font,tr("preview"),rx,sy,0xFFD9C4ED,false);g.fill(rx,sy+14,rx+previewW/2,sy+38,0xFF000000|original);g.fill(rx+previewW/2,sy+14,rx+previewW,sy+38,0xFF000000|model.rgb());
        g.drawString(font,String.format(Locale.ROOT,"#%06X",model.rgb()),rx,sy+46,0xFFFFFFFF,false);
        if(precise)for(int i=0;i<3;i++)g.drawString(font,List.of("R","G","B").get(i),rx,y+129+i*21,0xFFE6D8EA,false);
    }
}
