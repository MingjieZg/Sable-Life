package dev.sablelife.testing;

import dev.sablelife.SableLife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.RandomSource;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import com.simibubi.create.content.decoration.copycat.CopycatModel;

@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class RedstoneCoreClientPreview {
    private static int ticks;
    private static boolean opened;
    @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception {
        if(!Boolean.getBoolean("sablelife.redstonePreview"))return;
        var mc=Minecraft.getInstance();
        if(!opened){
            if(mc.screen==null||mc.getOverlay()!=null)return;
            for(var block:java.util.List.of(SableLife.HEART.get(),SableLife.REDSTONE_CORE.get())){
                var state=block.defaultBlockState();var model=mc.getBlockRenderer().getBlockModel(state);String crystal=block==SableLife.HEART.get()?"sablelife:block/heart_crystal":"sablelife:block/redstone_crystal";
                for(var side:Direction.values()){
                    var bare=model.getQuads(state,side,RandomSource.create(),ModelData.EMPTY,net.minecraft.client.renderer.RenderType.cutoutMipped());
                    if(bare.stream().anyMatch(q->q.getSprite().contents().name().toString().equals("sablelife:block/heart_casing")))throw new AssertionError("Opaque backing still present: "+side);
                    var casing=bare.stream().filter(q->q.getSprite().contents().name().toString().equals("create:block/copycat_base")).findFirst().orElseThrow();var pixels=casing.getSprite().contents().getOriginalImage();boolean transparent=false;for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)if((pixels.getPixelRGBA(x,y)>>>24)==0)transparent=true;if(!transparent)throw new AssertionError("Original casing lost transparent holes");
                    var diamond=bare.stream().filter(q->q.getSprite().contents().name().toString().equals(crystal)).findFirst().orElseThrow();var content=diamond.getSprite().contents();if(content.width()!=32||content.height()!=32)throw new AssertionError("Old atlas source still overrides new diamond");
                    try(var input=mc.getResourceManager().open(net.minecraft.resources.ResourceLocation.parse("sablelife:textures/"+crystal.substring("sablelife:".length())+".png"));var expected=com.mojang.blaze3d.platform.NativeImage.read(input)){var actual=content.getOriginalImage();for(int y=0;y<32;y++)for(int x=0;x<32;x++){int a=actual.getPixelRGBA(x,y),b=expected.getPixelRGBA(x,y);if((a>>>24)!=(b>>>24)||(b>>>24)!=0&&a!=b)throw new AssertionError("Loaded diamond pixel differs from new PNG: "+side);}}
                    if(bare.stream().anyMatch(q->q.getSprite().contents().name().getPath().equals("missingno")))throw new AssertionError("Missing core sprite");
                }
                for(var material:java.util.List.of(Blocks.IRON_BLOCK.defaultBlockState(),Blocks.OAK_LOG.defaultBlockState())){
                    var data=ModelData.builder().with(CopycatModel.MATERIAL_PROPERTY,material).build();
                    for(var side:Direction.values()){
                        var quads=model.getQuads(state,side,RandomSource.create(),data,null);
                        if(quads.stream().noneMatch(q->q.getSprite().contents().name().toString().equals(crystal)))throw new AssertionError("Camouflage lost diamond: "+side);
                        if(quads.stream().anyMatch(q->q.getSprite().contents().name().toString().equals("sablelife:block/heart_casing")))throw new AssertionError("Opaque default casing leaked through camouflage");
                        if(quads.stream().noneMatch(q->q.getSprite().contents().name().getNamespace().equals("minecraft")))throw new AssertionError("Donor material missing: "+side);
                    }
                }
            }
            mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(new Screen(Component.literal("Core assets")){
                @Override public void render(GuiGraphics g,int x,int y,float dt){g.fill(0,0,width,height,0xff242430);g.drawCenteredString(font,"Life core / Redstone core",width/2,35,0xffffff);g.pose().pushPose();g.pose().translate(width/2-125,80,0);g.pose().scale(7,7,7);g.renderItem(new ItemStack(SableLife.HEART_ITEM.get()),0,0);g.renderItem(new ItemStack(SableLife.REDSTONE_CORE_ITEM.get()),22,0);g.pose().popPose();}
            });opened=true;return;
        }
        if(++ticks==25){var p=mc.gameDirectory.toPath().resolve("screenshots/core-assets.png");java.nio.file.Files.createDirectories(p.getParent());try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(p);}System.out.println("Both cores: all six faces load exact new PNG pixels; transparent Copycat casing and iron/log camouflage checks passed");mc.stop();}
    }
}
