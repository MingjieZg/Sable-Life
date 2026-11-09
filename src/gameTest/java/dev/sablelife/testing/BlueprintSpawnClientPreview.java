package dev.sablelife.testing;

import java.util.*;
import dev.sablelife.client.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.client.*;
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

/** Isolated snapshots exercise the real screens without reading a player world or sending a request. */
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class BlueprintSpawnClientPreview {
    private static Screen panel;private static int ticks,index=Integer.getInteger("sablelife.blueprintPreviewStart",0);private static boolean started;
    private static Component helpPreview;private static int helpX,helpY;
    private static java.util.concurrent.CompletableFuture<?> languageReload;
    private static final class Host extends Screen {Host(){super(Component.empty());}@Override protected void init(){panel.init(minecraft,width,height);}@Override public void render(GuiGraphics g,int x,int y,float p){panel.render(g,-100,-100,p);if(helpPreview!=null&&ticks>=15)g.renderTooltip(minecraft.font,Tooltip.splitTooltip(minecraft,helpPreview),helpX,helpY);}}
    private static void set(String key,Object value)throws Exception{var f=panel.getClass().getDeclaredField(key);f.setAccessible(true);f.set(panel,value);}
    private static Object get(String key)throws Exception{var f=panel.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(panel);}
    private static void build()throws Exception{if(index>=20){buildWild();return;}var mc=Minecraft.getInstance();mc.options.guiScale().set(index/5+1);mc.resizeDisplay();var inv=new Inventory(null);var modules=new HeartModules();var module=modules.add("blueprint_spawn");module.edit("spawnPosition",index%2,"");var tag=modules.save();tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putBoolean("RespawnAllowed",true);tag.putUUID("Selected",module.id());var files=new ListTag();for(int i=0;i<20;i++)files.add(StringTag.valueOf("boss_stage_"+(i+1)));tag.put("BlueprintFiles",files);var data=new CompoundTag();data.putBoolean("Available",true);data.putBoolean("CanPreview",true);data.putString("Name","boss_stage_2");data.putString("Phase","ready");data.putString("Position","32.5, 70.5, -18.5");data.putInt("Life",index%5==1?0:index%5==3?2:1);data.putInt("Blocks",1280);data.putBoolean("CanActivate",index%5!=1&&index%5!=3);tag.getList("Modules",10).getCompound(0).put("BlueprintSpawn",data);
        var menu=new HeartDashboardMenu(1,inv);var paper=new ItemStack(Items.PAPER);paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(paper);
        if(index%5==0)panel=new HeartDashboardScreen(menu,inv,Component.empty());else if(index%5<4){panel=new BlueprintSpawnScreen(new Screen(Component.empty()){},menu,module.id());set("requested",true);}else{var settings=new HeartSettingsMenu(1,inv);var names=new CoreRelations();names.exclude("Offline_User");var s=new CompoundTag();s.putBoolean("Ready",true);s.putBoolean("LiveControls",true);s.put("Relations",names.save());var item=new ItemStack(Items.PAPER);item.set(DataComponents.CUSTOM_DATA,CustomData.of(s));settings.getSlot(0).set(item);panel=new CoreRelationsScreen(new Screen(Component.empty()){},settings);}
        mc.setScreen(new Host());
    }
    private static void buildWild() throws Exception {
        if(index>=36){buildCoreHeader();return;}
        helpPreview=null;
        var mc = Minecraft.getInstance();
        int variant=index>=32?4:(index-20)%3;
        mc.options.guiScale().set(index>=32?index-31:(index-20)/3+1);
        mc.resizeDisplay();
        var inventory = new Inventory(null);
        var menu = new dev.sablelife.ownership.WildEncounterMenu(1, inventory);
        var tag = new CompoundTag();
        tag.putBoolean("Ready", true);
        tag.putBoolean("Available", true);
        tag.putInt("WorkbenchProtectionRadius",index==23?0:index==26?128:64);
        if(variant==2)tag.putString("Status","placement_chunks");
        tag.putBoolean("Configured", true);
        tag.putBoolean("Enabled", true);
        tag.putString("Selected", "wandering_sentinel");
        tag.putString("Dimension", "minecraft:overworld");
        tag.putInt("Gap", 4);
        tag.putInt("Lifetime", 120);
        tag.putInt("Cooldown", 240);
        tag.putInt("MaxActive",1);
        tag.putString("SpawnAnnouncement","Machine has arrived in the world!");
        tag.putUUID("Faction",FactionRegistry.WILDS);
        var files = new ListTag();
        files.add(StringTag.valueOf("wandering_sentinel"));
        files.add(StringTag.valueOf("forest_guard"));
        tag.put("BlueprintFiles", files);
        var rules=new ListTag();
        for(int i=0;i<7;i++){
            var rule=new CompoundTag();
            rule.putString("File",i==0?"wandering_sentinel":"forest_guard_"+i);
            rule.putBoolean("Enabled",i%2==0);
            rule.putBoolean("ActivateCore",true);rule.putInt("ActiveCount",i%3);rule.putBoolean("Active",i%3>0);rule.putBoolean("Clearing",i==2);
            rules.add(rule);
        }
        tag.put("Rules",rules);
        var biomes=new ListTag();
        for(var biome:List.of("minecraft:plains","minecraft:forest","minecraft:meadow"))biomes.add(StringTag.valueOf(biome));
        tag.put("AvailableBiomes",biomes);
        var paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        menu.getSlot(0).set(paper);
        panel = new WildEncounterScreen(menu, inventory, Component.translatable("item.sablelife.wild_encounter_tool"));
        var page=panel.getClass().getDeclaredField("page");
        page.setAccessible(true);
        page.set(panel,page.getType().getEnumConstants()[variant]);
        set("file","wandering_sentinel");
        mc.setScreen(new Host());
    }
    private static void checkWildRuleControls(int variant)throws Exception{
        if(variant==0){
            ((Button)get("add")).onPress();
            var remove=panel.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(button->button.visible&&button.active&&button.getMessage().getString().equals("X"))
                    .reduce((first,last)->last).orElseThrow();
            remove.onPress();
            if((int)get("drafts")!=1||!remove.getMessage().getString().equals("?"))
                throw new AssertionError("Draft deleted before confirmation");
            remove.onPress();
            if((int)get("drafts")!=0)throw new AssertionError("Draft not deleted after confirmation");
            if(((Button)get("previous")).active)((Button)get("previous")).onPress();
            var clear=panel.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(button->button.visible&&button.active&&button.getMessage().getString().equals(Component.translatable("wild.sablelife.clear_current").getString())).findFirst().orElseThrow();
            clear.onPress();if(!clear.getMessage().getString().equals(Component.translatable("wild.sablelife.confirm_clear").getString()))throw new AssertionError("Clear population did not require confirmation");
            if(((List<?>)get("ruleButtons")).size()%2!=0)throw new AssertionError("Rules did not use two columns");
            var container=(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)panel;
            int footerTop=container.getGuiTop()+container.getYSize()-49;
            for(var child:panel.children())if(child instanceof Button button&&button.visible&&button.getY()<footerTop
                    &&button.getY()+button.getHeight()>footerTop-3)throw new AssertionError("Rule buttons overlap workbench notice");
            var tag=((WildEncounterScreen)panel).getMenu().snapshot();int radius=tag.getInt("WorkbenchProtectionRadius");
            String notice=Component.translatable(radius>0?"wild.sablelife.workbench_notice":"wild.sablelife.workbench_disabled",radius).getString();
            if(notice.contains("wild.sablelife.")||radius>0&&!notice.contains(Integer.toString(radius)))throw new AssertionError("Workbench notice is untranslated or ignores server radius");
            System.out.println("WORKBENCH_NOTICE_UI language="+net.minecraft.client.Minecraft.getInstance().options.languageCode+" radius="+radius+" scale="+net.minecraft.client.Minecraft.getInstance().options.guiScale().get()+" footerClear=true");
        }else if(variant==2){
            var field=(EditBox)get("announcement");
            if(!field.getValue().equals("Machine has arrived in the world!"))throw new AssertionError("Announcement did not load");
            var coordinates=(Button)get("announcementCoordinatesCheck");
            if(!coordinates.active||(boolean)get("announcementCoordinates"))throw new AssertionError("Coordinate checkbox initial state incorrect");
            field.setValue("   ");
            if(coordinates.active||(boolean)get("announcementCoordinates"))throw new AssertionError("Blank announcement left coordinate checkbox enabled");
            field.setValue("Machin");panel.mouseClicked(field.getX()+5,field.getY()+5,0);field.moveCursorToEnd(false);
            if(!panel.keyPressed(69,0,0)||!field.isFocused()||!panel.charTyped('e',0)||!field.getValue().equals("Machine"))throw new AssertionError("Typing E closed the panel or failed to insert text");
            if(!panel.keyPressed(259,0,0)||!field.getValue().equals("Machin"))throw new AssertionError("Backspace stopped working");
            field.setValue("A new machine has arrived!");field.setFocused(true);
            if(!coordinates.active)throw new AssertionError("Text did not enable coordinate checkbox");
            coordinates.onPress();
            if(!(boolean)get("announcementCoordinates")||!coordinates.getMessage().getString().contains("✓"))throw new AssertionError("Coordinate checkbox did not select");
            var fill=coordinates.getClass().getDeclaredField("fill");fill.setAccessible(true);
            if(fill.getInt(coordinates)!=0xFF24583D)throw new AssertionError("Selected coordinate checkbox is not green");
            if(!panel.keyPressed(257,0,0)||field.isFocused())throw new AssertionError("Announcement Enter was not handled");
            ((Button)get("densityButton")).onPress();
            var back=panel.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(Component.translatable("wild.sablelife.back").getString())).findFirst().orElseThrow();back.onPress();
            if(!((EditBox)get("announcement")).getValue().equals("A new machine has arrived!"))throw new AssertionError("Page switch lost announcement draft");
            coordinates=(Button)get("announcementCoordinatesCheck");field=(EditBox)get("announcement");
            if(!(boolean)get("announcementCoordinates")||!coordinates.active)throw new AssertionError("Page switch lost coordinate choice");
            var trySpawn=(Button)get("trySpawn");var check=(Button)get("announcementCheck");
            if(field.getX()+field.getWidth()>coordinates.getX()||coordinates.getX()+coordinates.getWidth()>check.getX()
                    ||coordinates.getY()!=field.getY()||coordinates.getY()!=check.getY())throw new AssertionError("Announcement controls overlap or are not aligned");
            if(trySpawn.getX()+trySpawn.getWidth()!=check.getX()+check.getWidth()
                    ||trySpawn.getY()!=((Button)get("preview")).getY()||trySpawn.getY()!=((Button)get("release")).getY())throw new AssertionError("Test spawn row is not aligned");
            var help=panel.getClass().getDeclaredMethod("announcementHelp");help.setAccessible(true);var styles=new ArrayList<net.minecraft.network.chat.Style>();
            ((Component)help.invoke(null)).visit((style,text)->{if(!text.isBlank())styles.add(style);return Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
            if(styles.stream().filter(style->style.isBold()&&style.getColor()!=null).count()<2)throw new AssertionError("Announcement tooltip is missing colored emphasis");
            for(String token:new String[]{"&0","&l","&o","&#FF5555","256"})if(!((Component)help.invoke(null)).getString().contains(token))throw new AssertionError("Announcement help omits formatting: "+token);
            if(Tooltip.splitTooltip(Minecraft.getInstance(),(Component)help.invoke(null)).size()<5)throw new AssertionError("Announcement help did not wrap into separate lines");
            if(index==31){helpPreview=(Component)help.invoke(null);var announcement=(EditBox)get("announcement");helpX=announcement.getX()+8;helpY=announcement.getY()+8;}
            var search=(EditBox)get("search");panel.mouseClicked(search.getX()+5,search.getY()+5,0);
            if(!panel.keyPressed(69,0,0)||!panel.charTyped('e',0)||!search.getValue().equals("e"))throw new AssertionError("Biome search E closed the panel");
            search.setValue("");search.setFocused(false);
        }else if(variant==1){
            var toggle=(Button)get("activate");
            var fill=toggle.getClass().getDeclaredField("fill");fill.setAccessible(true);
            if(!toggle.getMessage().getString().equals(Component.translatable("gui.yes").getString()))throw new AssertionError("Missing Yes state");
            if(fill.getInt(toggle)!=0xFF24583D)throw new AssertionError("Yes did not use enabled green");
            toggle.onPress();
            if(!toggle.getMessage().getString().equals(Component.translatable("gui.no").getString()))throw new AssertionError("Missing No state");
            if(fill.getInt(toggle)!=0)throw new AssertionError("No retained green fill");
            toggle.onPress();
            if(fill.getInt(toggle)!=0xFF24583D)throw new AssertionError("Yes did not restore green fill");
        }
    }
    private static void buildCoreHeader(){
        helpPreview=null;
        var mc=Minecraft.getInstance();mc.options.guiScale().set(index>=48?(index-48)/2+1:(index-36)/3+1);mc.resizeDisplay();
        var modules=new HeartModules();var module=modules.add(index>=48?"visual_shockwave":"linear_movement");if(index>=48)module.edit("shockPattern",(index-48)%2,"");var tag=modules.save();tag.getList("Modules",10).getCompound(0).putString("Status","valid");tag.getList("Modules",10).getCompound(0).putString("MovementPhase","idle");
        tag.putBoolean("Ready",true);tag.putBoolean("Editable",true);tag.putUUID("Selected",module.id());
        tag.putBoolean("ControlCore",index<48&&(index-36)%3==1);tag.putBoolean("SurvivalCore",index<48&&(index-36)%3==2);tag.putLong("SurvivalTokens",1285);
        var inv=new Inventory(null);var menu=new HeartDashboardMenu(1,inv);var paper=new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));menu.getSlot(0).set(paper);
        panel=new HeartDashboardScreen(menu,inv,Component.empty());mc.setScreen(new Host());
    }

    private static void checkCoreHeader()throws Exception{
        var close=panel.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b->b.getMessage().getString().equals("×")).findFirst().orElseThrow();
        var bottom=(Button)get("remove");var pager=(Button)get("nextParameters");
        int right=close.getX()+close.getWidth();
        if(right!=bottom.getX()+bottom.getWidth()||right!=pager.getX()+pager.getWidth())throw new AssertionError("Core close button does not align with detail controls: "+index);
        var header=panel.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b->b.visible&&b.getY()==close.getY()).sorted(Comparator.comparingInt(Button::getX)).toList();
        for(int i=1;i<header.size();i++)if(header.get(i).getX()<header.get(i-1).getX()+header.get(i-1).getWidth()+4)throw new AssertionError("Core header buttons overlap or lose spacing: "+index);
        for(var child:header)if(child.getX()<0||child.getX()+child.getWidth()>panel.width)throw new AssertionError("Core header outside viewport: "+index);
        if(index>=48){
            var fields=(List<ModuleFields.Field>)get("parameters");boolean radial=(index-48)%2==1;
            if(fields.stream().noneMatch(f->f.key().equals("shockPattern"))
                    ||fields.stream().anyMatch(f->f.key().equals("shockRadialStrength"))!=radial
                    ||fields.stream().anyMatch(f->f.key().equals("shockRadius"))==radial)throw new AssertionError("Spatial pattern fields are incorrect: "+index);
            String label=Component.translatable("dashboard.sablelife.choice_"+(radial?"shock_radial":"shock_ring")).getString();
            if(panel.children().stream().filter(Button.class::isInstance).map(Button.class::cast).noneMatch(b->b.visible&&b.getMessage().getString().equals(label)))throw new AssertionError("Spatial pattern choice missing/untranslated: "+index);
        }
    }

    private static void checkDensity()throws Exception{
        if(index>=36){checkCoreHeader();return;}
        var fields=(Map<String,EditBox>)get("numbers");var checks=(Map<String,Button>)get("numberChecks");
        if(fields.size()!=5||!fields.keySet().containsAll(List.of("Cooldown","RetrySeconds","SearchAttempts","MinDistance","MaxDistance")))throw new AssertionError("Density panel fields missing");
        var sync=panel.getClass().getDeclaredMethod("syncDensity",CompoundTag.class);sync.setAccessible(true);
        fields.get("MinDistance").setValue("80");fields.get("MaxDistance").setValue("32");sync.invoke(panel,((WildEncounterScreen)panel).getMenu().snapshot());
        if(checks.values().stream().anyMatch(b->b.active))throw new AssertionError("Reversed distance band can be confirmed");
        fields.get("MinDistance").setValue("24");fields.get("MaxDistance").setValue("32");fields.get("RetrySeconds").setValue("1");fields.get("SearchAttempts").setValue("4");sync.invoke(panel,((WildEncounterScreen)panel).getMenu().snapshot());
        if(checks.values().stream().anyMatch(b->!b.active))throw new AssertionError("Valid density settings cannot be confirmed");
        fields.get("Cooldown").setFocused(true);if(!panel.keyPressed(257,0,0))throw new AssertionError("Density Enter was not handled");
        var back=panel.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(Component.translatable("wild.sablelife.back").getString())).findFirst().orElseThrow();back.onPress();
        ((Button)get("densityButton")).onPress();fields=(Map<String,EditBox>)get("numbers");
        if(!fields.get("MinDistance").getValue().equals("24")||!fields.get("SearchAttempts").getValue().equals("4"))throw new AssertionError("Switching pages lost density drafts");
    }
    private static void check()throws Exception{if(index>=32){checkDensity();return;}for(var child:panel.children())if(child instanceof AbstractWidget w&&w.visible&&(w.getX()<0||w.getY()<0||w.getX()+w.getWidth()>panel.width||w.getY()+w.getHeight()>panel.height))throw new AssertionError("Blueprint UI outside viewport at "+index);if(index>=20){checkWildRuleControls((index-20)%3);if((index-20)%3==2){var release=(Button)get("release");release.onPress();if(!(boolean)get("releaseAfterSpawn")||!release.getMessage().getString().contains("✓"))throw new AssertionError("Release checkbox did not turn on");var fields=(Map<String,EditBox>)get("numbers");if(fields.get("Lifetime").active||fields.get("AbsenceSeconds").active||fields.get("MaxActive").active)throw new AssertionError("Release retained managed cleanup controls");release.onPress();if((boolean)get("releaseAfterSpawn")||!fields.get("Lifetime").active||!fields.get("AbsenceSeconds").active||!fields.get("MaxActive").active)throw new AssertionError("Release checkbox did not restore controls");var clearing=(Button)get("treesButton");clearing.onPress();if(!(boolean)get("clearObstacles")||((Button)get("ignoreButton")).active)throw new AssertionError("Full obstacle clearing did not override empty-volume check");clearing.onPress();if((boolean)get("clearObstacles")||!((Button)get("ignoreButton")).active)throw new AssertionError("Clearing toggle did not restore controls");var search=(EditBox)get("search");search.setValue("minecraft:forest");var options=(List<Button>)get("choices");var forest=options.getFirst();if(!forest.visible||!forest.getMessage().getString().contains(Component.translatable("biome.minecraft.forest").getString())||!forest.mouseClicked(forest.getX()+2,forest.getY()+2,1)||!((Set<?>)get("allowed")).contains("minecraft:forest"))throw new AssertionError("Biome search or right-click selection failed");}return;}if(index%5==0){var fields=(List<ModuleFields.Field>)get("parameters");int at=-1;for(int i=0;i<fields.size();i++)if(fields.get(i).key().equals("spawnActivate"))at=i;if(at>=0){int slots=(int)get("parameterSlots");int page=(int)get("parameterPage");if(at/slots==page&&!((List<Button>)get("choices")).get(at%slots).active)throw new AssertionError("Single life template auto activation unavailable");}}if(index%5==4){var inputs=panel.children().stream().filter(c->c instanceof EditBox).toList();if(inputs.size()!=1)throw new AssertionError("Player name entry missing");}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception{if(!Boolean.getBoolean("sablelife.blueprintSpawnPreview"))return;var mc=Minecraft.getInstance();if(!started){if(mc.screen==null||mc.getOverlay()!=null||languageReload!=null&&!languageReload.isDone())return;var language=System.getProperty("sablelife.blueprintPreviewLanguage","en_us");if(!mc.options.languageCode.equals(language)){mc.getLanguageManager().setSelected(language);mc.options.languageCode=language;languageReload=mc.reloadResourcePacks();return;}started=true;build();return;}if(++ticks==14){check();if(index<5||index>=20){var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("blueprint-"+index+".png"));}}}if(ticks==15&&index==31)((Button)get("release")).onPress();if(ticks==17&&index==31){try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(mc.gameDirectory.toPath().resolve("screenshots/wild-release-enabled.png"));}((Button)get("release")).onPress();}if(ticks==18){ticks=0;if(++index==Integer.getInteger("sablelife.blueprintPreviewEnd",56)){System.out.println("BLUEPRINT_UI_PASS range="+Integer.getInteger("sablelife.blueprintPreviewStart",0)+".."+index);mc.stop();}else build();}}
}
