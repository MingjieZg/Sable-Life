package dev.sablelife.testing;

import dev.sablelife.client.HeartSettingsScreen;
import dev.sablelife.heart.HeartSettingsMenu;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class CoreEffectsClientPreview {
    private static int ticks,page=2;private static boolean levels;private static Preview screen;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{
        if(!Boolean.getBoolean("sablelife.coreEffectsPreview"))return;
        if(screen==null&&dev.sablelife.compat.epicfight.EpicFightCompatibility.supported())Class.forName("yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch");
        var mc=Minecraft.getInstance();
        if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}
        ticks++;
        if(ticks==40||ticks==110){page=3;screen.init(mc,screen.width,screen.height);}
        if(ticks==75){page=2;mc.options.guiScale().set(3);mc.resizeDisplay();}
        if(ticks==148){page=0;screen.init(mc,screen.width,screen.height);}
        if(ticks==175){levels=true;screen.init(mc,screen.width,screen.height);}
        if(ticks==210){page=1;screen.init(mc,screen.width,screen.height);}
        if(ticks==240){mc.options.guiScale().set(2);mc.resizeDisplay();}
        if(ticks==25||ticks==60||ticks==100||ticks==130||ticks==160||ticks==190||ticks==225||ticks==255){
            var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("core-effects-"+ticks+".png"));}
        }
        if(ticks==270){System.out.println("Core effects, XP unit and chunk-loading layout checks passed");mc.stop();}
    }
    private static final class Preview extends Screen {
        HeartSettingsScreen panel;Preview(){super(Component.empty());}
        @Override protected void init(){
            var inv=new Inventory(null);var menu=new HeartSettingsMenu(1,inv);var tag=new CompoundTag();tag.putBoolean("Ready",true);tag.putBoolean("ReleaseAllowed",true);tag.putBoolean("Editable",true);tag.putBoolean("LiveControls",true);tag.putString("State","DORMANT");
            tag.putBoolean("killHeal",true);tag.putInt("bossbarColor",net.minecraft.world.BossEvent.BossBarColor.RED.ordinal());tag.putBoolean("forceChunkLoading",true);tag.putBoolean("ChunkLoadingEditable",true);tag.putString("ChunkLoadingStatus","active");tag.putInt("ChunkLoadingCount",4);
            tag.putInt("voidDeathHeight",-64);tag.putBoolean("VoidDeathDefault",true);tag.putDouble("health",1000);tag.putDouble("delay",3);tag.putInt("xp",levels?100:30970);tag.putInt("xpPoints",30970);tag.putBoolean("xpLevels",levels);
            tag.putDouble("deathEffectRadius",0);tag.putDouble("deathCount",80);tag.putDouble("deathSpread",.8);tag.putDouble("deathPattern",0);tag.putDouble("vanishCount",6);tag.putDouble("vanishSpread",.35);tag.putDouble("vanishPattern",5);tag.putInt("DeathParticleCount",2);tag.putInt("VanishParticleCount",1);tag.putInt("deathSoundCount",1);
            var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);panel=new HeartSettingsScreen(menu,inv,title);
            try{
                var p=HeartSettingsScreen.class.getDeclaredField("page");p.setAccessible(true);p.setInt(panel,page);panel.init(minecraft,width,height);
                var sounds=HeartSettingsScreen.class.getDeclaredField("sounds");sounds.setAccessible(true);if(((java.util.Map<?,?>)sounds.get(panel)).size()!=((page==2||page==3)?1:0))throw new AssertionError("Secondary row remains");
                if(page==2){var f=HeartSettingsScreen.class.getDeclaredField("inputs");f.setAccessible(true);var fields=(java.util.List<?>)f.get(panel);if(fields.size()!=3)throw new AssertionError("Missing effect radius field");var radius=(net.minecraft.client.gui.components.EditBox)fields.get(2);if(radius.getY()+radius.getHeight()>panel.height)throw new AssertionError("Radius field clipped");}
                if(page==2||page==3){var r=HeartSettingsScreen.class.getDeclaredField("restore");r.setAccessible(true);var v=HeartSettingsScreen.class.getDeclaredField("preview");v.setAccessible(true);var restore=(net.minecraft.client.gui.components.Button)r.get(panel);var preview=(net.minecraft.client.gui.components.Button)v.get(panel);if(restore.getX()+restore.getWidth()>=preview.getX())throw new AssertionError("Restore overlaps Preview");}
                else if(page==0){var unit=HeartSettingsScreen.class.getDeclaredField("experienceMode");unit.setAccessible(true);var button=(net.minecraft.client.gui.components.Button)unit.get(panel);var inputs=HeartSettingsScreen.class.getDeclaredField("inputs");inputs.setAccessible(true);var xp=(net.minecraft.client.gui.components.EditBox)((java.util.List<?>)inputs.get(panel)).get(3);if(button.getX()+button.getWidth()>=xp.getX())throw new AssertionError("XP unit overlaps its input");}

                if(page==1){var f=HeartSettingsScreen.class.getDeclaredField("bossColor");f.setAccessible(true);var color=(net.minecraft.client.gui.components.Button)f.get(panel);if(color==null||color.getWidth()!=85)throw new AssertionError("Boss color selector missing");}
                if(page==1){var inputs=HeartSettingsScreen.class.getDeclaredField("inputs");inputs.setAccessible(true);var field=(net.minecraft.client.gui.components.EditBox)((java.util.List<?>)inputs.get(panel)).get(0);field.setValue("-123");if(!field.getValue().equals("-123"))throw new AssertionError("Negative height rejected");field.setValue("-63.5");if(!field.getValue().equals("-123"))throw new AssertionError("Fractional height accepted");field.setValue("-64");}
                if(page==1)for(var widget:panel.children())if(widget instanceof net.minecraft.client.gui.components.Button button&&button.visible&&button.getY()+button.getHeight()>height)throw new AssertionError("Protection-page button clipped at current GUI scale");
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        @Override public void render(GuiGraphics g,int x,int y,float partial){panel.render(g,-100,-100,partial);}
    }
}
