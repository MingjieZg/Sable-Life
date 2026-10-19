package dev.sablelife.testing;
import dev.sablelife.client.EffectSelectionScreen;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
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
public final class ParticleColorClientPreview {
    private static int ticks;private static Preview screen;
    private static Object field(Object obj,String name)throws Exception{var f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
    private static void nativeColorCheck()throws Exception{
        var sprites=new net.minecraft.client.particle.SpriteSet(){
            public net.minecraft.client.renderer.texture.TextureAtlasSprite get(int age,int life){return null;}
            public net.minecraft.client.renderer.texture.TextureAtlasSprite get(net.minecraft.util.RandomSource random){return null;}
        };
        var particle=new net.minecraft.client.particle.SpellParticle.Provider(sprites).createParticle(net.minecraft.core.particles.ParticleTypes.EFFECT,null,0,0,0,0,0,0);
        dev.sablelife.client.ParticleVisualClient.color(particle,new ModuleParticles.Entry("minecraft:effect",0,1,0x32CD32));
        for(var component:java.util.List.of("rCol","gCol","bCol")){var f=net.minecraft.client.particle.Particle.class.getDeclaredField(component);f.setAccessible(true);float expected=(component.equals("gCol")?205:50)/255F;if(Math.abs(f.getFloat(particle)-expected)>1e-6)throw new IllegalStateException("Native spell particle RGB was not applied");}
        System.out.println("Native SpellParticle custom RGB passed");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception{
        if(!Boolean.getBoolean("sablelife.particleColorPreview"))return;var mc=Minecraft.getInstance();
        if(screen==null){if(mc.screen==null||mc.getOverlay()!=null||mc.level!=null)return;nativeColorCheck();mc.options.guiScale().set(2);mc.resizeDisplay();screen=new Preview();mc.setScreen(screen);return;}
        ticks++;if(ticks==35){mc.options.guiScale().set(3);mc.resizeDisplay();}
        if(ticks==25||ticks==60){var panel=screen.panel;var color=(Button)field(panel,"color");var life=(EditBox)field(panel,"duration");var size=(EditBox)field(panel,"level");
            if(!color.visible||!color.active||color.getX()+color.getWidth()>life.getX()||life.getX()+life.getWidth()>size.getX()||size.getX()+size.getWidth()>screen.width)throw new IllegalStateException("Color/Life/Size controls overlap or color disabled");
            if(!color.getMessage().getString().contains("32CD32"))throw new IllegalStateException("Saved RGB not displayed");
            var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("particle-color-"+ticks+".png"));}
        }
        if(ticks==80){ParticleTravelClientCheck.run(mc);System.out.println("Particle Color/Life/Size UI passed at GUI scales 2 and 3");mc.stop();}
    }
    private static final class Preview extends Screen {
        EffectSelectionScreen panel;Preview(){super(Component.literal("Particle color preview"));}
        @Override protected void init(){var inv=new Inventory(null);var menu=new EffectSelectionMenu(1,inv);var m=new VirtualModule("particles");for(var id:java.util.List.of("minecraft:effect","minecraft:instant_effect","minecraft:entity_effect","minecraft:dust","minecraft:flame"))m.editParticle("add",id);m.editParticle("color","minecraft:effect",0x32CD32);var tag=new CompoundTag();tag.put("Module",m.save());tag.putBoolean("Editable",true);tag.putBoolean("ParticleMode",true);var stack=new ItemStack(Items.PAPER);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(stack);panel=new EffectSelectionScreen(menu,inv,title);try{var selected=EffectSelectionScreen.class.getDeclaredField("rightSelection");selected.setAccessible(true);selected.set(panel,"minecraft:effect");}catch(Exception e){throw new IllegalStateException(e);}panel.init(minecraft,width,height);try{((EditBox)field(panel,"search")).setValue("effect");}catch(Exception e){throw new IllegalStateException(e);}}
        @Override public void render(GuiGraphics g,int x,int y,float partial){try{var sync=EffectSelectionScreen.class.getDeclaredMethod("sync");sync.setAccessible(true);sync.invoke(panel);}catch(Exception e){throw new IllegalStateException(e);}panel.render(g,-100,-100,partial);}
    }
}
