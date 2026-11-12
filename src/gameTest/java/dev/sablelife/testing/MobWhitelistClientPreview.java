package dev.sablelife.testing;

import dev.sablelife.client.*;
import dev.sablelife.heart.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real client screen checks without loading or editing a player's world. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class MobWhitelistClientPreview {
    private static CoreRelationsScreen panel;
    private static HeartSettingsMenu menu;
    private static int index,ticks;
    private static boolean started;
    private static CompletableFuture<Void> reload;
    private static final class Host extends Screen {
        Host(){super(Component.empty());}
        @Override protected void init(){panel.init(minecraft,width,height);}
        @Override public void render(GuiGraphics g,int x,int y,float p){panel.render(g,-100,-100,p);}
    }
    private static Object get(String name)throws Exception{var f=CoreRelationsScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(panel);}
    private static void set(String name,Object value)throws Exception{var f=CoreRelationsScreen.class.getDeclaredField(name);f.setAccessible(true);f.set(panel,value);}
    private static void sync()throws Exception{var m=CoreRelationsScreen.class.getDeclaredMethod("syncMobs");m.setAccessible(true);m.invoke(panel);}
    private static void expect(boolean value,String text){if(!value)throw new AssertionError("MOB_WHITELIST_UI case="+index+" "+text);}
    private static void build()throws Exception{
        var mc=Minecraft.getInstance();int guiScale=index%8/2+1;mc.options.guiScale().set(guiScale);mc.resizeDisplay();expect(mc.options.guiScale().get()==guiScale,"Requested GUI scale was clamped by the test window");
        var density=LifeUiStyle.class.getDeclaredField("density");density.setAccessible(true);density.setInt(null,index%2==0?0:3);
        var inventory=new Inventory(null);menu=new HeartSettingsMenu(1,inventory);var data=new CompoundTag();data.putBoolean("Ready",true);data.putBoolean("LiveControls",true);
        var relations=new CoreRelations();for(var id:CoreRelations.mobTypes().stream().filter(id->!id.toString().equals("minecraft:cow")).limit(32).toList())relations.excludeMob(id.toString());
        data.put("Relations",relations.save());snapshot(data);panel=new CoreRelationsScreen(new Screen(Component.empty()){},menu);set("mobs",true);mc.setScreen(new Host());ticks=0;
    }
    private static void snapshot(CompoundTag data){var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(data));menu.getSlot(0).set(paper);}
    @SuppressWarnings("unchecked") private static void check()throws Exception{
        for(var child:panel.children())if(child instanceof AbstractWidget widget&&widget.visible)expect(widget.getX()>=0&&widget.getY()>=0&&widget.getRight()<=panel.width&&widget.getBottom()<=panel.height,"Widget outside viewport: "+widget.getMessage().getString());
        var available=(List<Button>)get("availableButtons");var excluded=(List<Button>)get("excludedButtons");var search=(EditBox)get("mobSearch");
        var catalog=(List<String>)get("availableMobs");
        expect(catalog.get(0).equals(CoreRelations.MobGroup.PASSIVE.id().toString())
                &&catalog.get(1).equals(CoreRelations.MobGroup.NEUTRAL.id().toString()),"Passive and Neutral groups must lead the catalog");
        var footer=CoreRelationsScreen.class.getDeclaredMethod("footer",String.class);footer.setAccessible(true);
        var warning=((Component)footer.invoke(panel,"")).getSiblings().getLast();
        expect(warning.getStyle().isBold()&&warning.getStyle().getColor().getValue()==0xFFAA00,"Damage warning is not highlighted");
        expect((int)get("mobRows")>0&&available.stream().anyMatch(b->b.visible)&&excluded.stream().anyMatch(b->b.visible),"Missing dual-pane rows");
        var add=(Button)get("addMob");var remove=(Button)get("removeMob");expect(available.getFirst().getRight()<add.getX()&&add.getRight()<excluded.getFirst().getX(),"Transfer buttons overlap a pane");
        expect(available.getFirst().getY()>search.getBottom()&&remove.getBottom()<=((Button)get("leftMobPrev")).getY(),"List/header/footer overlap");
        expect(!add.active&&!remove.active,"Transfer enabled without selection");
        ((Button)get("rightMobNext")).onPress();expect((int)get("rightMobPage")==1,"Right pagination failed");
        search.setValue("minecraft:cow");expect(((List<String>)get("availableMobs")).equals(List.of("minecraft:cow"))&&((int)get("rightMobPage"))==1,"ID search or independent pagination failed");
        available.getFirst().onPress();expect(add.active,"Selected available row does not enable add");
        search.setValue(Component.translatable("entity.minecraft.cow").getString());expect(((List<String>)get("availableMobs")).contains("minecraft:cow"),"Localized search failed");
        search.setValue("");panel.mouseClicked(search.getX()+3,search.getY()+3,0);panel.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_E,0,0);panel.charTyped('e',0);
        expect(mcScreenIsHost()&&search.getValue().equals("e"),"Inventory key closed/blocked search input: "+search.getValue());search.setValue("minecraft:cow");
        var data=menu.snapshot().copy();data.putBoolean("LiveControls",false);snapshot(data);sync();expect(!add.active,"Read-only add still enabled");
        data.putBoolean("LiveControls",true);var relations=data.getCompound("Relations");relations.getList("ExcludedMobs",8).add(StringTag.valueOf("minecraft:cow"));snapshot(data);panel.tick();
        expect(((List<String>)get("excludedMobs")).contains("minecraft:cow")&&((List<String>)get("availableMobs")).isEmpty()&&((EditBox)get("mobSearch")).getValue().equals("minecraft:cow"),"Authoritative snapshot or draft retention failed");
        ((EditBox)get("mobSearch")).setValue("");
        System.out.println("MOB_WHITELIST_UI_CASE_PASS index="+index+" language="+Minecraft.getInstance().options.languageCode+" guiScale="+Minecraft.getInstance().options.guiScale().get()+" density="+LifeUiStyle.density());
    }
    private static boolean mcScreenIsHost(){return Minecraft.getInstance().screen instanceof Host;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.mobWhitelistPreview"))return;var mc=Minecraft.getInstance();
        if(!started){
            if(mc.screen==null||mc.getOverlay()!=null||reload!=null&&!reload.isDone())return;
            String language=index<8?"en_us":"zh_cn";if(!mc.options.languageCode.equals(language)){mc.getLanguageManager().setSelected(language);mc.options.languageCode=language;reload=mc.reloadResourcePacks();return;}
            build();started=true;return;
        }
        if(++ticks==12)check();
        if(ticks==16){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("mob-whitelist-"+index+".png"));}}
        if(ticks==18){if(++index==16){System.out.println("MOB_WHITELIST_UI_PASS: EN/ZH, 4 GUI scales, 2 densities, dual-pane layout/search/selection/pagination/permissions/snapshot retention");mc.stop();}else started=false;}
    }
}
