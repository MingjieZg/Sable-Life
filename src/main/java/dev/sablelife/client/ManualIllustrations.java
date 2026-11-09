package dev.sablelife.client;

import dev.sablelife.SableLife;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Set;

/** Small localized diagrams using the game's actual item models. These are guides, not live controls. */
final class ManualIllustrations {
    static final Set<String> IDS=Set.of("assemble","dashboard","mark","preview","activate","release");
    static int height(int width){return Math.round(100*Math.min(1.5f,width/320f))+8;}
    private static final int GOLD=0xFFFFD36C,TEXT=0xFFE1D5E8,GREEN=0xFF8EE7A4;
    private static Component tr(String key){return Component.translatable("manual.sablelife.figure."+key);}
    private static void centered(GuiGraphics g,String key,int center,int y,int maxWidth,int color){
        var font=Minecraft.getInstance().font;var text=tr(key);float scale=Math.min(1F,maxWidth/(float)Math.max(1,font.width(text)));
        g.pose().pushPose();g.pose().translate(center,y,0);g.pose().scale(scale,scale,1);g.drawString(font,text,-font.width(text)/2,0,color,false);g.pose().popPose();
    }
    private static void item(GuiGraphics g,ItemStack stack,int x,int y){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(1.7f,1.7f,1);g.renderItem(stack,0,0);g.pose().popPose();}
    private static void core(GuiGraphics g,int x,int y){item(g,new ItemStack(SableLife.HEART.get()),x,y);}
    private static void arrow(GuiGraphics g,int x,int y,int length,int color){g.fill(x,y,x+length,y+2,color);for(int i=0;i<6;i++)g.fill(x+length-6+i,y-5+i,x+length-5+i,y+7-i,color);}
    private static void button(GuiGraphics g,String key,int x,int y,int width,int color){g.fill(x,y,x+width,y+21,0xFF15121C);g.fill(x+2,y+2,x+width-2,y+19,color);g.drawCenteredString(Minecraft.getInstance().font,tr(key),x+width/2,y+7,TEXT);}
    static void render(GuiGraphics g,String id,int x,int y,int width){
        if(!IDS.contains(id))return;
        float scale=Math.min(1.5f,width/320f);
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        g.fill(0,0,320,96,0xFF181B25);g.fill(0,0,320,1,0xFF706078);g.fill(0,95,320,96,0xFF706078);
        switch(id){
            case "assemble" -> {
                item(g,new ItemStack(Items.STONE),39,29);core(g,72,29);item(g,new ItemStack(Items.DISPENSER),105,29);
                g.fill(39,60,134,62,GOLD);centered(g,"one_core",86,72,120,GOLD);
                arrow(g,150,44,32,GOLD);centered(g,"assembled",246,38,132,GREEN);centered(g,"connected",246,53,132,TEXT);
            }
            case "dashboard" -> {
                core(g,25,36);centered(g,"empty_hand",40,73,72,TEXT);arrow(g,69,48,35,GOLD);
                button(g,"add",119,28,75,0xFF62596B);button(g,"arrow",204,28,105,0xFF62596B);
                centered(g,"always",214,64,190,GREEN);
            }
            case "mark" -> {
                button(g,"mark",12,35,103,0xFF62596B);arrow(g,122,45,33,GOLD);
                item(g,new ItemStack(Items.DISPENSER),179,32);g.renderOutline(177,30,31,32,GOLD);
                arrow(g,212,45,34,GOLD);centered(g,"face",281,40,70,GOLD);centered(g,"not_target",160,76,300,TEXT);
            }
            case "preview" -> {
                core(g,20,34);item(g,new ItemStack(Items.DISPENSER),54,34);
                g.renderOutline(52,32,32,32,GOLD);g.fill(87,47,131,49,0xFF62E4E8);g.fill(128,43,135,54,GOLD);
                arrow(g,138,47,119,0xFFFFFFFF);item(g,new ItemStack(Items.ARROW),271,35);
                centered(g,"start",111,68,85,GOLD);centered(g,"no_damage",204,80,215,TEXT);
            }
            case "activate" -> {
                int clickWidth=Math.min(116,Minecraft.getInstance().font.width(tr("empty_click")));
                int plusCenter=Math.round((73+153-clickWidth/2f)/2f);
                button(g,"shift",14,35,59,0xFF62596B);centered(g,"plus",plusCenter,42,15,TEXT);
                centered(g,"empty_click",153,42,116,TEXT);arrow(g,215,45,24,GOLD);core(g,258,32);
                centered(g,"active",272,73,88,GREEN);
            }
            case "release" -> {
                button(g,"release",13,34,95,0xFF62596B);centered(g,"or",128,41,28,TEXT);
                item(g,new ItemStack(SableLife.CREATIVE_RELEASE_TOOL.get()),149,31);arrow(g,193,45,31,GOLD);
                core(g,244,31);centered(g,"dormant",258,73,115,GREEN);
            }
        }
        g.pose().popPose();
    }
    private ManualIllustrations(){}
}
