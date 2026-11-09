package dev.sablelife.testing;

import java.util.*;
import dev.sablelife.client.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
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

/** Real-screen validation with snapshots only, in a separate client directory without loading a world. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class RelationsClientPreview {
    private static int ticks,index;private static Screen panel;private static boolean started;
    private static final class Host extends Screen {Host(){super(Component.empty());}@Override protected void init(){panel.init(minecraft,width,height);}@Override public void render(GuiGraphics g,int x,int y,float p){panel.render(g,-100,-100,p);}}
    private static void set(Screen screen,String field,Object value)throws Exception{var f=screen.getClass().getDeclaredField(field);f.setAccessible(true);f.set(screen,value);}
    private static void checkAllianceColors()throws Exception{
        if(index>=24||index%6!=5)return;
        var buttons=panel.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).toList();
        String label=Component.translatable("relations.sablelife.accept_alliance").getString();
        var accept=buttons.stream().filter(b->b.visible&&b.active&&b.getMessage().getString().equals(label)).findFirst().orElseThrow();
        var cancel=buttons.stream().filter(b->b.visible&&b.active&&b.getY()==accept.getY()&&b.getX()>accept.getX()).findFirst().orElseThrow();
        var fill=accept.getClass().getDeclaredField("fill");fill.setAccessible(true);
        if(fill.getInt(accept)!=0xFF24583D||fill.getInt(cancel)!=0xFF7A3038)throw new AssertionError("Incoming alliance colors are incorrect: "+index);
    }
    private static void checkVisibilityColors(){
        if(index>=24||index%6!=1)return;
        var buttons=panel.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).toList();
        for(boolean privateFaction:new boolean[]{false,true}){
            String badge=Component.translatable("relations.sablelife."+(privateFaction?"private_badge":"public_badge")).getString();
            var button=buttons.stream().filter(b->b.getMessage().getString().contains(badge)).findFirst().orElseThrow();
            int expected=(privateFaction?net.minecraft.ChatFormatting.GOLD:net.minecraft.ChatFormatting.GREEN).getColor();
            boolean[] found={false};button.getMessage().visit((style,text)->{if(text.contains(badge)&&style.getColor()!=null&&style.getColor().getValue()==expected)found[0]=true;return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
            if(!found[0])throw new AssertionError("Visibility badge color missing: "+badge);
        }
        String invitation=Component.translatable("relations.sablelife.invitation_badge").getString();
        String privateBadge=Component.translatable("relations.sablelife.private_badge").getString();
        if(buttons.stream().noneMatch(b->b.getMessage().getString().contains(invitation)&&b.getMessage().getString().contains(privateBadge)))throw new AssertionError("Invitation hid faction privacy");
    }
    private static Object get(String field)throws Exception{var f=panel.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(panel);}
    private static void buildRotation(){
        var mc=Minecraft.getInstance();mc.options.guiScale().set((index-24)/3+1);mc.resizeDisplay();
        String type=List.of("arrow","gun","mortar").get((index-24)%3);var modules=new HeartModules();var module=modules.add(type);
        var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",module.id());
        var row=tag.getList("Modules",10).getCompound(0);row.putString("Status","valid");
        var item=new CompoundTag();item.putString("id","minecraft:diamond_sword");item.putInt("count",1);row.put("ProjectileItem",item);
        var inventory=new Inventory(null);var menu=new HeartDashboardMenu(1,inventory);var paper=new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(paper);panel=new HeartDashboardScreen(menu,inventory,Component.empty());mc.setScreen(new Host());
    }
    private static void checkRotationFields()throws Exception{
        if(index<24)return;
        var parameters=(List<ModuleFields.Field>)get("parameters");int slots=(int)get("parameterSlots");
        var keys=parameters.stream().map(ModuleFields.Field::key).toList();int first=keys.indexOf("projectileRotationX");if(first<0||keys.indexOf("projectileRotationY")!=first+1||keys.indexOf("projectileRotationZ")!=first+2)throw new AssertionError("Rotation axes are split by another property");
        var sync=panel.getClass().getDeclaredMethod("sync");sync.setAccessible(true);
        for(String axis:List.of("X","Y","Z")){
            String key="projectileRotation"+axis;int at=-1;for(int i=0;i<parameters.size();i++)if(parameters.get(i).key().equals(key))at=i;
            if(at<0)throw new AssertionError("Missing projectile rotation field: "+key);
            set(panel,"parameterPage",at/slots);sync.invoke(panel);
            var fields=(List<net.minecraft.client.gui.components.EditBox>)get("fields");var input=fields.get(at%slots);
            var choices=(List<net.minecraft.client.gui.components.Button>)get("choices");
            if(!input.visible||!input.active||!input.getValue().equals("0.00")||input.getTooltip()==null)throw new AssertionError("Rotation field unavailable, incorrect default or missing help: "+key+" index="+index+" visible="+input.visible+" active="+input.active+" value="+input.getValue()+" help="+input.getTooltip());
            if(parameters.get(at).min()!=-180||parameters.get(at).max()!=180||!ModuleFields.angle(key))throw new AssertionError("Incorrect rotation range/angle input");
        }
    }
    private static void build()throws Exception{if(index>=24){buildRotation();return;}var mc=Minecraft.getInstance();mc.options.guiScale().set(index/6+1);mc.resizeDisplay();var inventory=new Inventory(null);var parent=new Screen(Component.empty()){};
        if(index%6<2){var menu=new HeartSettingsMenu(1,inventory);var tag=new CompoundTag();tag.putBoolean("Ready",true);tag.putBoolean("LiveControls",true);var relations=new CoreRelations();var players=new ListTag();var factions=new ListTag();
            for(int i=0;i<96;i++){var id=UUID.randomUUID();var row=new CompoundTag();row.putUUID("Id",id);row.putString("Name","Player "+(i+1));players.add(row);relations.exclude("Player"+(i+1));var f=new CompoundTag();f.putUUID("Id",UUID.randomUUID());f.putString("Name","Faction "+(i+1));f.putString("CreatorName","TestOwner");f.putBoolean("Private",i%2==1);f.putBoolean("Invited",i==1);f.putBoolean("Manage",true);f.putBoolean("CanJoin",true);f.putBoolean("FriendlyFire",i%2==0);factions.add(f);}tag.put("Players",players);tag.put("Factions",factions);tag.put("Relations",relations.save());var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(paper);panel=new CoreRelationsScreen(parent,menu);set(panel,"factions",index%6==1);
        }else if(index%6>=3){var menu=new HeartSettingsMenu(1,inventory);var id=UUID.randomUUID();var tag=new CompoundTag();var d=new CompoundTag();d.putUUID("Id",id);d.putUUID("Owner",UUID.randomUUID());d.putString("Name","Faction with private membership");d.putBoolean("Private",true);d.putBoolean("Manage",true);d.putBoolean("Member",true);d.putBoolean("CanJoin",true);var members=new ListTag();var invitations=new ListTag();var ties=new ListTag();for(int i=0;i<32;i++){var r=new CompoundTag();r.putUUID("Id",UUID.randomUUID());r.putString("Name","PlayerOrFaction"+i);members.add(r);invitations.add(r.copy());var t=r.copy();t.putBoolean("Allied",i%4==0);t.putBoolean("Incoming",i%4==1);t.putBoolean("Outgoing",i%4==2);ties.add(t);}d.put("Members",members);d.put("Invitations",invitations);d.put("Alliances",ties);tag.put("FactionDetail",d);var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(paper);panel=new FactionDetailsScreen(parent,menu,id);set(panel,"tab",index%6-3);
        }else{var menu=new SignalRuleMenu(1,inventory);var rules=new SignalRules("combat_event");var row=rules.add();row.edit("eventKind",3);var data=new CompoundTag();data.putBoolean("Ready",true);data.putBoolean("Editable",true);data.putString("Type","combat_event");data.put("Groups",rules.save());var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(data));menu.getSlot(0).set(paper);panel=new SignalRuleScreen(menu,inventory,Component.empty());}
        mc.setScreen(new Host());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{if(!Boolean.getBoolean("sablelife.relationsPreview"))return;var mc=Minecraft.getInstance();if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;build();return;}if(++ticks==16){checkAllianceColors();checkVisibilityColors();checkRotationFields();for(var child:panel.children())if(child instanceof AbstractWidget w&&w.visible&&(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>panel.width||w.getY()+w.getHeight()>panel.height))throw new AssertionError("Relations UI out of bounds at "+index);if(index<6||index>=24){var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve("relations-"+index+".png"));}}}if(ticks==20){ticks=0;if(++index==36){System.out.println("RELATIONS_UI_PASS: player/faction/death/invitation/alliance screens and arrow/gun/mortar rotation fields at 4 GUI scales");mc.stop();}else build();}}
}
