package dev.sablelife.testing;

import dev.sablelife.client.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Real menu-only checks of conditional laser fields and faction creation feedback. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class LaserControlsClientPreview {
    private static final String[] CASES={"fixed","horizontal","vertical","both","tracking","name_required","personal_limit"};
    private static int phase,ticks;
    private static String language="";
    private static Preview screen;
    private static java.util.concurrent.CompletableFuture<?> reload;
    private static java.lang.reflect.Field field(Class<?> type,String key)throws Exception{var f=type.getDeclaredField(key);f.setAccessible(true);return f;}
    private static Object get(Object object,String key)throws Exception{return field(object.getClass(),key).get(object);}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!Boolean.getBoolean("sablelife.laserControlsPreview"))return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null||reload!=null&&!reload.isDone())return;
        String wanted=phase<CASES.length?"en_us":"zh_cn";
        if(!language.equals(wanted)){if(mc.screen==null)return;language=wanted;mc.getLanguageManager().setSelected(wanted);mc.options.languageCode=wanted;reload=mc.reloadResourcePacks();return;}
        if(screen==null){
            if(mc.level!=null||mc.screen==null)return;
            mc.options.guiScale().set(2);mc.resizeDisplay();while(LifeUiStyle.density()!=1)LifeUiStyle.cycle(1);
            screen=new Preview();mc.setScreen(screen);ticks=0;return;
        }
        if(++ticks!=10)return;
        if(screen.child instanceof HeartDashboardScreen panel){
            var parameters=(List<ModuleFields.Field>)get(panel,"parameters");var keys=parameters.stream().map(ModuleFields.Field::key).toList();
            int c=phase%CASES.length;
            if(keys.contains("sweepSpeed")!=(c==1||c==3)||keys.contains("verticalSweepSpeed")!=(c==2||c==3))throw new AssertionError("Conditional speed controls differ: "+keys);
            if(!keys.contains("laserParticleSpacing"))throw new AssertionError("Missing particle spacing");
            for(var key:List.of("sweepSpeed","verticalSweepSpeed","laserParticleSpacing","laser_sweep_speed_help","laser_particle_spacing_help"))if(net.minecraft.client.resources.language.I18n.get("dashboard.sablelife."+key).equals("dashboard.sablelife."+key))throw new AssertionError("Untranslated laser control: "+key);
        }else{
            String key="relations.sablelife.faction_"+(phase%CASES.length==5?"name_required":"player_limit");
            if(net.minecraft.client.resources.language.I18n.get(key,1).equals(key))throw new AssertionError("Missing creation feedback");
            var input=(net.minecraft.client.gui.components.EditBox)get(screen.child,"factionInput");input.setValue("New Team");
            if(!get(screen.child,"draft").equals("New Team"))throw new AssertionError("Name input lost draft");
            var init=CoreRelationsScreen.class.getDeclaredMethod("init");init.setAccessible(true);init.invoke(screen.child);
            if(!((net.minecraft.client.gui.components.EditBox)get(screen.child,"factionInput")).getValue().equals("New Team"))throw new AssertionError("Snapshot rebuild discarded name");
        }
        var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);
        try(var picture=Screenshot.takeScreenshot(mc.getMainRenderTarget())){picture.writeToFile(dir.resolve(language+"-"+CASES[phase%CASES.length]+".png"));}
        System.out.println("LASER_FACTION_UI_PASS "+language+" "+CASES[phase%CASES.length]);
        if(++phase==CASES.length*2){System.out.println("LASER_FACTION_UI_COMPLETE cases="+phase);mc.stop();}else{screen=null;mc.setScreen(new Screen(Component.empty()){});}
    }

    private static final class Preview extends Screen {
        Screen child;Preview(){super(Component.empty());}
        @Override protected void init(){try{
            int c=phase%CASES.length;var inventory=new Inventory(null);
            if(c<5){
                var modules=new HeartModules();var m=modules.add("laser");
                m.edit("sweepAngle",c==1||c==3||c==4?90:0,"");m.edit("verticalSweepAngle",c==2||c==3||c==4?60:0,"");m.edit("laserAim",c==4?1:0,"");
                m.edit("sweepSpeed",30,"");m.edit("verticalSweepSpeed",60,"");m.edit("laserParticleSpacing",1,"");
                var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",m.id());tag.getList("Modules",10).getCompound(0).putString("Status","unmarked");
                var menu=new HeartDashboardMenu(1,inventory);var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(paper);
                var panel=new HeartDashboardScreen(menu,inventory,title);child=panel;panel.init(minecraft,width,height);
                var parameters=(List<ModuleFields.Field>)get(panel,"parameters");int at=0;for(int i=0;i<parameters.size();i++)if(parameters.get(i).key().equals(c==2?"verticalSweepAngle":"sweepAngle")){at=i;break;}
                field(HeartDashboardScreen.class,"parameterPage").setInt(panel,at/(int)get(panel,"parameterSlots"));
                var sync=HeartDashboardScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);sync.invoke(panel);
            }else{
                var menu=new HeartSettingsMenu(1,inventory);var tag=new net.minecraft.nbt.CompoundTag();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putString("RelationStatus",c==5?"faction_name_required":"faction_player_limit");tag.putInt("FactionCreationLimit",1);
                var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(paper);
                var panel=new CoreRelationsScreen(this,menu);field(CoreRelationsScreen.class,"factions").setBoolean(panel,true);child=panel;panel.init(minecraft,width,height);
            }
        }catch(Exception e){throw new RuntimeException(e);}}
        @Override public void render(GuiGraphics graphics,int x,int y,float partial){child.render(graphics,-100,-100,partial);}
    }
}
