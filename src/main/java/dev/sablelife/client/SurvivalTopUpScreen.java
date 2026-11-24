package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.heart.SurvivalLiquidFuel;
import dev.sablelife.heart.SurvivalTopUpMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.List;

@EventBusSubscriber(modid=SableLife.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SurvivalTopUpScreen extends AbstractContainerScreen<SurvivalTopUpMenu> {
    private static final int COLUMNS=12, PER_PAGE=COLUMNS*3;
    private static final int CATALOG_X=12, CATALOG_Y=24;
    private static final int RAIL_WIDTH=18;
    private static final int SLOT_FRAME=0xFF171821, SLOT_SURFACE=0xFF373542;
    private static final int TANK_Y=45, TANK_HEIGHT=46;
    private static final int DUMP_Y=96, DUMP_HEIGHT=10;
    private static final ResourceLocation FILLED_BUCKET_GHOST=ResourceLocation.withDefaultNamespace("item/lava_bucket");
    private static final ResourceLocation EMPTY_BUCKET_GHOST=ResourceLocation.withDefaultNamespace("item/bucket");
    private int page;
    private int tankMouseButton=-1;
    private Button previous,next,confirm,automatic;
    private DumpButton dump;

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
        next=button(Component.literal(">"),CATALOG_X+COLUMNS*18-26,80,24,()->{page++;sync();});
        confirm=button(tr("confirm"),181,140,102,()->click(SurvivalTopUpMenu.CONFIRM));
        automatic=button(tr("automatic_off"),181,158,102,()->click(menu.snapshot().getBoolean("Automatic")
                ? SurvivalTopUpMenu.DISABLE_AUTO : SurvivalTopUpMenu.ENABLE_AUTO));
        automatic.setTooltip(Tooltip.create(tr("automatic_hint")));
        dump=addRenderableWidget(new DumpButton(leftPos+tankX(),topPos+DUMP_Y,
                ()->click(menu.snapshot().getInt("TankDumpAction"))));
        button(Component.translatable("menu.sablelife.back"),229,5,54,()->click(SurvivalTopUpMenu.BACK));
        sync();
    }
    private Button button(Component text,int x,int y,int width,Runnable click){
        return addRenderableWidget(LifeUiStyle.button(text,leftPos+x,topPos+y,width,16,click));
    }
    private void click(int id){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id);}
    private void sync(){
        var data=menu.snapshot();
        int last=Math.max(0,(data.getList("Catalog",Tag.TAG_COMPOUND).size()-1)/PER_PAGE);
        page=Math.clamp(page,0,last);
        previous.active=page>0;
        next.active=page<last;
        confirm.active=data.contains("CanConfirm")?data.getBoolean("CanConfirm"):data.getLong("Value")>0;
        confirm.setMessage(tr(data.getBoolean("Confirming")?"confirm_again":"confirm"));
        boolean enabled=data.getBoolean("Automatic");
        automatic.active=data.contains("Automatic");
        automatic.setMessage(tr(enabled?"automatic_on":"automatic_off"));
        LifeUiStyle.fill(automatic,enabled?0xFF24543B:0);
        dump.visible=data.contains("TankAmount",Tag.TAG_INT);
        dump.active=dump.visible&&data.contains("TankDumpAction",Tag.TAG_INT)
                &&(data.getInt("TankAmount")>0||data.getBoolean("TankUnavailable"));
        dump.updateConfirmation(data.getBoolean("TankDumpConfirming"));
    }
    @Override protected void containerTick(){super.containerTick();sync();}

    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xFF9B614E);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF242632);
        var data=menu.snapshot();
        var catalog=data.getList("Catalog",Tag.TAG_COMPOUND);
        for(int i=0;i<PER_PAGE&&page*PER_PAGE+i<catalog.size();i++){
            int x=leftPos+CATALOG_X+(i%COLUMNS)*18,y=topPos+CATALOG_Y+(i/COLUMNS)*18;
            slot(g,x,y);
            var row=catalog.getCompound(page*PER_PAGE+i);
            String id=row.getString("Id");
            if(row.getBoolean("Fluid"))drawFluid(g,fluid(id,FluidType.BUCKET_VOLUME),x,y,16,16);
            else g.renderItem(stack(id),x,y);
        }
        for(int i=0;i<SurvivalTopUpMenu.INPUTS+36;i++){
            var slot=menu.getSlot(i);
            slot(g,leftPos+slot.x,topPos+slot.y);
        }
        if(data.contains("TankAmount",Tag.TAG_INT)){
            drawTank(g,data);
            bucketSlot(g,SurvivalTopUpMenu.BUCKET_INPUT,FILLED_BUCKET_GHOST);
            bucketSlot(g,SurvivalTopUpMenu.BUCKET_OUTPUT,EMPTY_BUCKET_GHOST);
        }
    }
    private static void slot(GuiGraphics g,int x,int y){
        g.fill(x-1,y-1,x+17,y+17,SLOT_FRAME);
        g.fill(x,y,x+16,y+16,SLOT_SURFACE);
    }
    private void bucketSlot(GuiGraphics g,int index,ResourceLocation ghost){
        var bucket=menu.getSlot(index);
        int x=leftPos+bucket.x,y=topPos+bucket.y;
        slot(g,x,y);
        if(!bucket.hasItem())blitTinted(g,sprite(ghost),x,y,0x70FFFFFF);
    }

    private void drawTank(GuiGraphics g,CompoundTag data){
        int x=leftPos+tankX(),y=topPos+TANK_Y;
        boolean unavailable=data.getBoolean("TankUnavailable");
        g.fill(x,y,x+RAIL_WIDTH,y+TANK_HEIGHT,unavailable?0xFF87674F:SLOT_FRAME);
        g.fill(x+1,y+1,x+RAIL_WIDTH-1,y+TANK_HEIGHT-1,SLOT_SURFACE);
        int amount=tankAmount(data);
        if(amount>0){
            int height=Math.max(1,(TANK_HEIGHT-2)*amount/SurvivalLiquidFuel.CAPACITY);
            int width=RAIL_WIDTH-2,bottom=y+TANK_HEIGHT-1,top=bottom-height;
            var stack=fluid(data.getString("TankFluid"),amount);
            if(unavailable||stack.isEmpty())g.fill(x+1,top,x+1+width,bottom,0xFF6E6475);
            else drawFluid(g,stack,x+1,top,width,height);
        }
        for(int tick=1;tick<=3;tick++){
            int tickY=y+TANK_HEIGHT*tick/4;
            g.fill(x+RAIL_WIDTH-4,tickY,x+RAIL_WIDTH-1,tickY+1,0xA0A89CAF);
        }
    }

    private void drawFluid(GuiGraphics g,FluidStack stack,int x,int y,int width,int height){
        if(stack.isEmpty())return;
        var appearance=IClientFluidTypeExtensions.of(stack.getFluid());
        var texture=appearance.getStillTexture(stack);
        if(texture==null)texture=appearance.getFlowingTexture(stack);
        if(texture==null)return;
        var sprite=sprite(texture);
        int tint=appearance.getTintColor(stack);
        if(width==16&&height==16){
            blitTinted(g,sprite,x,y,tint);
            return;
        }
        g.enableScissor(x,y,x+width,y+height);
        try{
            for(int tileY=y+height-16;tileY+16>y;tileY-=16)
                for(int tileX=x;tileX<x+width;tileX+=16)blitTinted(g,sprite,tileX,tileY,tint);
        }finally{
            g.disableScissor();
        }
    }

    private TextureAtlasSprite sprite(ResourceLocation id){
        return minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
    }

    private static void blitTinted(GuiGraphics g,TextureAtlasSprite sprite,int x,int y,int tint){
        g.blit(x,y,0,16,16,sprite,((tint>>>16)&255)/255F,((tint>>>8)&255)/255F,(tint&255)/255F,(tint>>>24)/255F);
    }

    private static ItemStack stack(String id){
        var key=ResourceLocation.tryParse(id);
        return key==null?ItemStack.EMPTY:BuiltInRegistries.ITEM.getOptional(key).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static FluidStack fluid(String id,int amount){
        var key=ResourceLocation.tryParse(id);
        if(key==null||amount<=0)return FluidStack.EMPTY;
        return BuiltInRegistries.FLUID.getOptional(key).map(value->new FluidStack(value,amount)).orElse(FluidStack.EMPTY);
    }

    private static int tankAmount(CompoundTag data){
        return Math.clamp(data.getInt("TankAmount"),0,SurvivalLiquidFuel.CAPACITY);
    }

    private int tankX(){
        return menu.getSlot(SurvivalTopUpMenu.BUCKET_INPUT).x-1;
    }

    private boolean overTank(double mouseX,double mouseY){
        return isHovering(tankX(),TANK_Y,RAIL_WIDTH,TANK_HEIGHT,mouseX,mouseY);
    }

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if((button==0||button==1)&&!isQuickCrafting&&overTank(mouseX,mouseY)){
            var data=menu.snapshot();
            if(data.contains("TankAmount",Tag.TAG_INT)){
                tankMouseButton=button;
                if(!menu.getCarried().isEmpty()&&!data.getBoolean("TankUnavailable"))
                    click(button==1?SurvivalTopUpMenu.FILL_TANK:SurvivalTopUpMenu.DRAIN_TANK);
                return true;
            }
        }
        return super.mouseClicked(mouseX,mouseY,button);
    }

    @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double dragX,double dragY){
        return button==tankMouseButton||super.mouseDragged(mouseX,mouseY,button,dragX,dragY);
    }

    @Override public boolean mouseReleased(double mouseX,double mouseY,int button){
        if(button!=tankMouseButton)return super.mouseReleased(mouseX,mouseY,button);
        tankMouseButton=-1;
        return true;
    }

    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        var data=menu.snapshot();
        g.drawString(font,title,12,9,0xE6D8EA,false);
        g.drawString(font,tr("balance",data.getLong("Balance")),95,9,0xFFCA54,false);
        g.drawCenteredString(font,Component.literal((page+1)+" / "+Math.max(1,(data.getList("Catalog",Tag.TAG_COMPOUND).size()+PER_PAGE-1)/PER_PAGE)),CATALOG_X+COLUMNS*9,84,0xD9C4ED);
        g.drawString(font,tr("deposit_total",data.getLong("Value")),181,129,0xFFE071,false);
        g.drawString(font,playerInventoryTitle,12,130,0xE6D8EA,false);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        super.render(g,mouseX,mouseY,partial);
        renderTooltip(g,mouseX,mouseY);
    }
    @Override protected void renderTooltip(GuiGraphics g,int mouseX,int mouseY){
        super.renderTooltip(g,mouseX,mouseY);
        var data=menu.snapshot();
        if(data.contains("TankAmount",Tag.TAG_INT)){
            if(overTank(mouseX,mouseY)){
                int amount=tankAmount(data);
                String id=data.getString("TankFluid");
                var stack=fluid(id,amount);
                boolean unavailable=data.getBoolean("TankUnavailable")||(amount>0&&stack.isEmpty());
                Component name;
                if(unavailable)name=id.isEmpty()?tr("tank_unknown_fluid"):Component.literal(id);
                else name=stack.isEmpty()?tr("tank_empty"):stack.getHoverName();
                var usage=tr(unavailable?"tank_unavailable":"tank_use");
                g.renderComponentTooltip(font,List.of(name,tr("tank_amount",amount,SurvivalLiquidFuel.CAPACITY),usage),mouseX,mouseY);
                return;
            }
            if(bucketTooltip(g,SurvivalTopUpMenu.BUCKET_INPUT,"tank_bucket_input",mouseX,mouseY)
                    ||bucketTooltip(g,SurvivalTopUpMenu.BUCKET_OUTPUT,"tank_bucket_output",mouseX,mouseY))return;
        }
        if(!menu.getCarried().isEmpty())return;
        int x=mouseX-leftPos-CATALOG_X,y=mouseY-topPos-CATALOG_Y;
        if(x<0||y<0||x>=COLUMNS*18||y>=54||x%18>=16||y%18>=16)return;
        var catalog=data.getList("Catalog",Tag.TAG_COMPOUND);
        int index=page*PER_PAGE+y/18*COLUMNS+x/18;
        if(index>=catalog.size())return;
        var row=catalog.getCompound(index);
        String id=row.getString("Id");
        boolean liquid=row.getBoolean("Fluid");
        Component name;
        if(liquid){
            var stack=fluid(id,FluidType.BUCKET_VOLUME);
            name=stack.isEmpty()?Component.literal(id):stack.getHoverName();
        }else{
            var stack=stack(id);
            name=stack.isEmpty()?Component.literal(id):stack.getHoverName();
        }
        g.renderComponentTooltip(font,List.of(name,tr(liquid?"fluid_value":"item_value",row.getInt("Price"))),mouseX,mouseY);
    }

    private boolean bucketTooltip(GuiGraphics g,int index,String text,int mouseX,int mouseY){
        var slot=menu.getSlot(index);
        if(slot.hasItem()||!isHovering(slot.x,slot.y,16,16,mouseX,mouseY))return false;
        g.renderTooltip(font,tr(text),mouseX,mouseY);
        return true;
    }

    private static final class DumpButton extends Button {
        private boolean confirming;

        private DumpButton(int x,int y,Runnable action){
            super(x,y,RAIL_WIDTH,DUMP_HEIGHT,tr("tank_dump"),button->action.run(),DEFAULT_NARRATION);
            setTooltip(Tooltip.create(tr("tank_dump_hint")));
        }

        private void updateConfirmation(boolean next){
            if(confirming==next)return;
            confirming=next;
            setMessage(tr(next?"tank_dump_confirm":"tank_dump"));
            setTooltip(Tooltip.create(tr(next?"tank_dump_confirm_hint":"tank_dump_hint")));
        }

        @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partial){
            int left=getX(),top=getY(),right=left+getWidth(),bottom=top+getHeight();
            boolean highlighted=active&&isHoveredOrFocused();
            int surface=SLOT_SURFACE,icon=0xFF827C89;
            if(active){
                if(confirming){
                    surface=highlighted?0xFF65434F:0xFF51343E;
                    icon=0xFFF1CDD7;
                }else{
                    if(highlighted)surface=0xFF464052;
                    icon=highlighted?0xFFE1D5EC:0xFFADA3B7;
                }
            }
            g.fill(left,top,right,bottom,SLOT_FRAME);
            g.fill(left+1,top+1,right-1,bottom-1,surface);
            int x=left+getWidth()/2,y=top+getHeight()/2;
            if(confirming){
                g.fill(x-3,y-1,x-2,y+1,icon);
                g.fill(x-2,y,x-1,y+2,icon);
                for(int step=0;step<4;step++)g.fill(x-1+step,y+1-step,x+step,y+3-step,icon);
            }else{
                g.fill(x-1,y-3,x+1,y-2,icon);
                g.fill(x-3,y-2,x+3,y-1,icon);
                g.fill(x-2,y-1,x-1,y+2,icon);
                g.fill(x+1,y-1,x+2,y+2,icon);
                g.fill(x-2,y+2,x+2,y+3,icon);
            }
        }
    }
}
