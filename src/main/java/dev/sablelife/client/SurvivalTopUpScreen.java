package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.heart.SurvivalTopUpMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid=SableLife.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SurvivalTopUpScreen extends AbstractContainerScreen<SurvivalTopUpMenu> {
    private static final int COLUMNS=15, PER_PAGE=COLUMNS*3;
    private int page;
    private Button previous,next,confirm;

    public SurvivalTopUpScreen(SurvivalTopUpMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);
        imageWidth=295;imageHeight=223;
    }

    @SubscribeEvent public static void register(RegisterMenuScreensEvent event){
        event.register(SableLife.SURVIVAL_TOP_UP_MENU.get(),SurvivalTopUpScreen::new);
    }

    private static Component tr(String key,Object... args){return Component.translatable("survival_tokens.sablelife."+key,args);}
    @Override protected void init(){
        super.init();
        previous=button(Component.literal("<"),12,80,24,()->{page--;sync();});
        next=button(Component.literal(">"),259,80,24,()->{page++;sync();});
        confirm=button(tr("confirm"),181,112,102,()->click(0));
        button(Component.translatable("menu.sablelife.back"),229,5,54,()->click(1));
        sync();
    }
    private Button button(Component text,int x,int y,int width,Runnable click){
        return addRenderableWidget(LifeUiStyle.button(text,leftPos+x,topPos+y,width,16,click));
    }
    private void click(int id){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    private void sync(){
        var data=menu.snapshot();
        int last=Math.max(0,(data.getList("Catalog",10).size()-1)/PER_PAGE);
        page=Math.clamp(page,0,last);
        previous.active=page>0;
        next.active=page<last;
        confirm.active=data.getLong("Value")>0;
        confirm.setMessage(tr(data.getBoolean("Confirming")?"confirm_again":"confirm"));
    }
    @Override protected void containerTick(){super.containerTick();sync();}

    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xFF9B614E);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF242632);
        var catalog=menu.snapshot().getList("Catalog",10);
        for(int i=0;i<PER_PAGE&&page*PER_PAGE+i<catalog.size();i++){
            int x=leftPos+12+(i%COLUMNS)*18,y=topPos+24+(i/COLUMNS)*18;
            slot(g,x,y);
            var row=catalog.getCompound(page*PER_PAGE+i);
            g.renderItem(stack(row.getString("Id")),x,y);
        }
        for(int i=0;i<SurvivalTopUpMenu.INPUTS+36;i++){
            var slot=menu.getSlot(i);
            slot(g,leftPos+slot.x,topPos+slot.y);
        }
    }
    private static void slot(GuiGraphics g,int x,int y){
        g.fill(x-1,y-1,x+17,y+17,0xFF171821);
        g.fill(x,y,x+16,y+16,0xFF373542);
    }
    private static ItemStack stack(String id){return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));}
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        var data=menu.snapshot();
        g.drawString(font,title,12,9,0xE6D8EA,false);
        g.drawString(font,tr("balance",data.getLong("Balance")),95,9,0xFFCA54,false);
        g.drawCenteredString(font,Component.literal((page+1)+" / "+Math.max(1,(data.getList("Catalog",10).size()+PER_PAGE-1)/PER_PAGE)),imageWidth/2,84,0xD9C4ED);
        g.drawString(font,tr("deposit_total",data.getLong("Value")),181,101,0xFFE071,false);
        g.drawString(font,playerInventoryTitle,12,130,0xE6D8EA,false);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        super.render(g,mouseX,mouseY,partial);
        renderTooltip(g,mouseX,mouseY);
    }
    @Override protected void renderTooltip(GuiGraphics g,int mouseX,int mouseY){
        super.renderTooltip(g,mouseX,mouseY);
        if(!menu.getCarried().isEmpty())return;
        int x=mouseX-leftPos-12,y=mouseY-topPos-24;
        if(x<0||y<0||x>=COLUMNS*18||y>=54||x%18>=16||y%18>=16)return;
        var catalog=menu.snapshot().getList("Catalog",10);
        int index=page*PER_PAGE+y/18*COLUMNS+x/18;
        if(index>=catalog.size())return;
        var row=catalog.getCompound(index);
        g.renderComponentTooltip(font,java.util.List.of(stack(row.getString("Id")).getHoverName(),tr("item_value",row.getInt("Price"))),mouseX,mouseY);
    }
}
