package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.heart.FactionRegistry;
import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.ownership.WildEncounterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid=SableLife.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class WildEncounterScreen extends AbstractContainerScreen<WildEncounterMenu> {
    private enum Page { RULES, BLUEPRINTS, CONDITIONS, FACTIONS, DENSITY }
    private record Label(Component text,int x,int y,int width){}
    private record RuleButtons(Button file,Button conditions,Button enable,Button clear,Button delete,int countX,int countY,int countWidth){}
    private static final UUID NO_ID=new UUID(0,0);
    private static final List<String> NUMERIC_KEYS=List.of("Gap","Cooldown","Lifetime","AbsenceSeconds","MaxActive","RetrySeconds","SearchAttempts","MinDistance","MaxDistance");
    private Page page=Page.RULES;
    private final List<Label> labels=new ArrayList<>();
    private final List<RuleButtons> ruleButtons=new ArrayList<>();
    private final List<Button> choices=new ArrayList<>(), allowedChoices=new ArrayList<>();
    private final Map<String,EditBox> numbers=new LinkedHashMap<>();
    private final Map<String,Button> numberChecks=new LinkedHashMap<>();
    private final Map<String,String> numberDraft=new LinkedHashMap<>();
    private final LinkedHashSet<String> allowed=new LinkedHashSet<>();
    private List<String> choicesData=List.of(), allowedData=List.of();
    private List<UUID> factionIds=List.of();
    private static final String OVERWORLD=dev.sablelife.ownership.WildEncounters.OVERWORLD_BIOMES.toString();
    private EditBox search,announcement;
    private String announcementDraft="";
    private Button announcementCheck,announcementCoordinatesCheck;
    private boolean announcementCoordinates;
    private Button previous,next,allowedPrevious,allowedNext,add,load,activate,skyButton,ignoreButton,treesButton,factionButton,release,preview,trySpawn;
    private Button addBiome,removeBiome,densityButton;
    private String selectedAvailable="",selectedAllowed="";
    private int biomePane;
    private boolean opened;
    private String file="", blueprint="", deleteConfirm="";
    private int deleteTicks,clearTicks;
    private String clearConfirm="";
    private int drafts, rulePage, choicePage, allowedPage, pendingRevision;
    private boolean activateCore=true, waitingLoad, editingDraft, loadedConditions, sky, ignoreBounds, clearObstacles, legacyClearTrees, releaseAfterSpawn;
    private UUID faction=FactionRegistry.WILDS;

    public WildEncounterScreen(WildEncounterMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(SableLife.WILD_ENCOUNTER_MENU.get(),WildEncounterScreen::new);}
    private static Component tr(String key,Object... args){return Component.translatable("wild.sablelife."+key,args);}

    private static Component announcementHelp(){
        return Component.empty()
                .append(tr("announcement_title").copy().withStyle(net.minecraft.ChatFormatting.GOLD,net.minecraft.ChatFormatting.BOLD)).append("\n")
                .append(tr("announcement_help").copy().withStyle(net.minecraft.ChatFormatting.GRAY)).append("\n")
                .append(tr("announcement_format_help").copy().withStyle(net.minecraft.ChatFormatting.GRAY)).append("\n")
                .append(tr("announcement_empty").copy().withStyle(net.minecraft.ChatFormatting.YELLOW,net.minecraft.ChatFormatting.BOLD));
    }

    @Override protected void init(){
        imageWidth=Math.min(640,width-12);
        imageHeight=height-12;
        super.init();
        labels.clear();ruleButtons.clear();choices.clear();allowedChoices.clear();numbers.clear();numberChecks.clear();search=null;announcement=null;
        switch(page){
            case RULES -> rules();
            case BLUEPRINTS -> blueprints();
            case CONDITIONS -> conditions();
            case FACTIONS -> factions();
            case DENSITY -> density();
        }
        sync(menu.snapshot());
    }

    private Button button(Component label,int x,int y,int w,Runnable click){
        return addRenderableWidget(LifeUiStyle.button(label,leftPos+x,topPos+y,w,16,click));
    }

    private void rules(){
        button(Component.translatable("dashboard.sablelife.close"),imageWidth-66,4,58,this::onClose);
        int column=(imageWidth-20)/2;
        boolean compact=column<250;
        int stride=compact?38:22, rows=Math.max(1,(imageHeight-86)/stride);
        for(int i=0;i<rows*2;i++){
            int index=i,x=8+(i%2)*(column+4),y=26+(i/2)*stride;
            int countWidth=compact?54:58, deleteWidth=16;
            int name=compact?column-countWidth-deleteWidth-4:column-30-36-countWidth-62-deleteWidth-10;
            int conditionsX=compact?x:x+name+2;
            int conditionsWidth=compact?(column-4)/4:30;
            int enableX=conditionsX+conditionsWidth+2,enableWidth=compact?(column-4)/4:36;
            int countX=compact?x+name+2:enableX+enableWidth+2;
            int clearX=compact?enableX+enableWidth+2:countX+countWidth+2;
            int clearWidth=compact?column-(clearX-x):62;
            int controlY=compact?y+18:y;
            var select=button(Component.empty(),x,y,name,()->openBlueprint(row(index)));
            var conditions=button(tr("conditions_short"),conditionsX,controlY,conditionsWidth,()->openConditions(row(index)));
            var toggle=button(Component.empty(),enableX,controlY,enableWidth,()->sendRule("toggle_rule",row(index).getString("File")));
            var clear=button(tr("clear_current"),clearX,controlY,clearWidth,()->clear(row(index),rulePage*ruleButtons.size()+index));
            clear.setTooltip(Tooltip.create(tr("clear_current_help")));
            var delete=button(Component.literal("X").withStyle(net.minecraft.ChatFormatting.RED),x+column-deleteWidth,y,deleteWidth,()->delete(row(index),rulePage*ruleButtons.size()+index));
            delete.setTooltip(Tooltip.create(tr("delete_rule_help")));
            ruleButtons.add(new RuleButtons(select,conditions,toggle,clear,delete,countX,y,countWidth));
        }
        int y=imageHeight-22;
        add=button(tr("add_rule"),8,y,60,()->{drafts++;rulePage=(rulesData().size()+drafts-1)/ruleButtons.size();syncRules(menu.snapshot());});
        previous=button(Component.literal("<"),imageWidth/2-28,y,24,()->{rulePage--;syncRules(menu.snapshot());});
        next=button(Component.literal(">"),imageWidth/2+4,y,24,()->{rulePage++;syncRules(menu.snapshot());});
    }

    private ListTag rulesData(){return menu.snapshot().getList("Rules",10);}
    private CompoundTag row(int index){return rulesData().getCompound(rulePage*ruleButtons.size()+index);}

    private void syncRules(CompoundTag snapshot){
        var rows=snapshot.getList("Rules",10);
        int count=rows.size()+drafts, perPage=ruleButtons.size();
        rulePage=Math.clamp(rulePage,0,Math.max(0,(count-1)/perPage));
        boolean ready=snapshot.getBoolean("Ready")&&snapshot.getBoolean("Available");
        add.active=ready&&count<64;
        previous.active=rulePage>0;
        next.active=(rulePage+1)*perPage<count;
        for(int i=0;i<perPage;i++){
            int index=rulePage*perPage+i;
            var row=rows.getCompound(index);
            var widgets=ruleButtons.get(i);
            String name=row.getString("File");
            boolean saved=!name.isEmpty(), enabled=row.getBoolean("Enabled");
            for(var b:List.of(widgets.file,widgets.conditions,widgets.enable,widgets.clear,widgets.delete))b.visible=index<count;
            widgets.file.active=ready&&!row.getBoolean("Active");
            widgets.conditions.active=widgets.enable.active=ready&&saved;
            widgets.delete.active=ready&&!row.getBoolean("Active");
            widgets.clear.active=ready&&saved&&row.getInt("ActiveCount")>0&&!row.getBoolean("Clearing");
            widgets.clear.setMessage(tr(row.getBoolean("Clearing")?"clearing":deleteKey(name,index).equals(clearConfirm)?"confirm_clear":"clear_current"));
            widgets.file.setMessage(saved?Component.literal(name):tr("choose_blueprint"));
            widgets.file.setTooltip(Tooltip.create(widgets.file.getMessage()));
            widgets.enable.setMessage(tr(enabled?"rule_enabled":"enable_rule"));
            LifeUiStyle.fill(widgets.enable,enabled?0xFF24583D:0);
            widgets.delete.setMessage(Component.literal(deleteKey(name,index).equals(deleteConfirm)?"?":"X").withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    private static String deleteKey(String file,int index){return file.isEmpty()?"draft:"+index:"rule:"+file;}

    private void delete(CompoundTag row,int index){
        var name=row.getString("File");
        var key=deleteKey(name,index);
        if(key.equals(deleteConfirm)){
            if(name.isEmpty())drafts=Math.max(0,drafts-1);
            else sendRule("remove_rule",name);
            deleteConfirm="";
        }else{
            deleteConfirm=key;
            deleteTicks=100;
        }
        syncRules(menu.snapshot());
    }

    private void clear(CompoundTag row,int index){
        String name=row.getString("File"),key=deleteKey(name,index);
        if(key.equals(clearConfirm)){sendRule("clear_rule",name);clearConfirm="";}
        else{clearConfirm=key;clearTicks=100;deleteConfirm="";}
        syncRules(menu.snapshot());
    }

    private void openBlueprint(CompoundTag row){
        file=row.getString("File");
        blueprint=file;
        activateCore=file.isEmpty()||row.getBoolean("ActivateCore");
        editingDraft=file.isEmpty();
        waitingLoad=false;
        choicePage=0;
        show(Page.BLUEPRINTS);
    }

    private void blueprints(){
        int pane=Math.max(110,imageWidth/3), leftWidth=imageWidth-pane-24;
        back();
        for(int i=0;i<Math.max(1,(imageHeight-58)/18);i++){
            int row=i;
            choices.add(button(Component.empty(),8,26+i*18,leftWidth,()->{blueprint=entry(choicesData,choicePage,choices.size(),row);syncBlueprints(menu.snapshot());}));
        }
        load=button(tr("load_blueprint"),leftWidth+16,26,pane,this::loadBlueprint);
        int toggleWidth=Math.max(42,pane/3);
        labels.add(new Label(tr("activate_core_question"),leftWidth+16,50,pane-toggleWidth-4));
        activate=button(Component.empty(),leftWidth+16+pane-toggleWidth,46,toggleWidth,()->{activateCore=!activateCore;syncBlueprints(menu.snapshot());});
        int y=imageHeight-22;
        previous=button(Component.literal("<"),8,y,24,()->{choicePage--;syncBlueprints(menu.snapshot());});
        next=button(Component.literal(">"),leftWidth-16,y,24,()->{choicePage++;syncBlueprints(menu.snapshot());});
    }

    private void syncBlueprints(CompoundTag snapshot){
        choicesData=strings(snapshot,"BlueprintFiles");
        if(waitingLoad&&snapshot.getInt("RuleRevision")!=pendingRevision){
            waitingLoad=false;
            if(snapshot.getString("Status").equals("rule_loaded")&&snapshot.getString("Selected").equals(blueprint)){
                if(editingDraft)drafts=Math.max(0,drafts-1);
                show(Page.RULES);
                return;
            }
        }
        choicePage=syncList(choices,choicesData,choicePage,previous,next,blueprint,false);
        load.active=!waitingLoad&&choicesData.contains(blueprint);
        activate.active=!waitingLoad;
        activate.setMessage(Component.translatable(activateCore?"gui.yes":"gui.no"));
        LifeUiStyle.fill(activate,activateCore?0xFF24583D:0);
    }

    private void loadBlueprint(){
        var settings=new CompoundTag();
        settings.putString("File",blueprint);settings.putString("Previous",file);
        settings.putBoolean("ActivateCore",activateCore);
        pendingRevision=menu.snapshot().getInt("RuleRevision");
        waitingLoad=true;
        send("load_rule",settings);
    }

    private void openConditions(CompoundTag row){
        file=row.getString("File");
        loadedConditions=false;
        announcementDraft="";
        announcementCoordinates=false;
        numberDraft.clear();
        selectedAvailable=selectedAllowed="";
        choicePage=allowedPage=0;
        sendRaw("select",file);
        show(Page.CONDITIONS);
    }

    private void conditions(){
        back();
        int pane=(imageWidth-24)/2, right=imageWidth-pane-8;
        number("Gap","gap",8,25,pane);
        densityButton=button(tr("density_settings"),right,25,pane,()->show(Page.DENSITY));
        densityButton.setTooltip(Tooltip.create(tr("density_help")));
        number("Lifetime","lifetime",8,43,pane);
        numbers.get("Lifetime").setTooltip(Tooltip.create(tr("lifetime_help")));
        number("AbsenceSeconds","absence_cleanup_short",right,43,pane);
        numbers.get("AbsenceSeconds").setTooltip(Tooltip.create(tr("absence_cleanup_help")));
        number("MaxActive","max_active",8,61,pane);
        numbers.get("MaxActive").setTooltip(Tooltip.create(tr("max_active_help")));
        factionButton=button(Component.empty(),right,61,pane,()->{choicePage=0;show(Page.FACTIONS);});
        skyButton=button(Component.empty(),8,79,pane,()->{sky=!sky;syncConditions(menu.snapshot());save("save_rule");});
        ignoreButton=button(Component.empty(),right,79,pane,()->{ignoreBounds=!ignoreBounds;syncConditions(menu.snapshot());save("save_rule");});
        ignoreButton.setTooltip(Tooltip.create(tr("ignore_bounds_help")));
        treesButton=button(Component.empty(),8,97,pane,()->{clearObstacles=!clearObstacles;legacyClearTrees=false;syncConditions(menu.snapshot());save("save_rule");});
        treesButton.setTooltip(Tooltip.create(tr("clear_obstacles_help")));
        announcement=addRenderableWidget(new EditBox(font,leftPos+right,topPos+97,pane-88,16,tr("announcement_hint")));
        announcement.setMaxLength(dev.sablelife.ownership.WildEncounters.MAX_ANNOUNCEMENT_LENGTH);
        announcement.setHint(Component.literal(font.plainSubstrByWidth(tr("announcement_hint").getString(),announcement.getWidth()-8)));
        announcement.setTooltip(Tooltip.create(announcementHelp()));
        announcement.setValue(announcementDraft);
        announcement.setResponder(value->{announcementDraft=value;if(value.isBlank())announcementCoordinates=false;syncAnnouncementCoordinates(menu.snapshot());});
        announcementCoordinatesCheck=button(Component.empty(),right+pane-84,97,62,()->{
            if(announcementDraft.isBlank())return;
            announcementCoordinates=!announcementCoordinates;syncAnnouncementCoordinates(menu.snapshot());save("save_rule");
        });
        announcementCoordinatesCheck.setTooltip(Tooltip.create(tr("announcement_coordinates_help")));
        announcementCheck=button(Component.literal("✓"),right+pane-18,97,18,()->{save("save_rule");announcement.setFocused(false);});
        announcementCheck.setTooltip(Tooltip.create(announcementHelp()));
        int actionWidth=(imageWidth-24)/3;
        release=button(Component.empty(),8,115,actionWidth,()->{releaseAfterSpawn=!releaseAfterSpawn;syncConditions(menu.snapshot());save("save_rule");});
        release.setTooltip(Tooltip.create(tr("release_after_spawn_help")));
        preview=button(tr("preview"),12+actionWidth,115,actionWidth,()->save("save_preview_rule"));
        trySpawn=button(tr("try"),16+actionWidth*2,115,imageWidth-24-actionWidth*2,()->save("save_try_rule"));
        trySpawn.setTooltip(Tooltip.create(tr("try_help")));
        biomePane=(imageWidth-54)/2;
        right=imageWidth-biomePane-8;
        labels.add(new Label(tr("allowed_biomes"),right+3,139,biomePane-6));
        search=addRenderableWidget(new EditBox(font,leftPos+11,topPos+135,biomePane-6,16,tr("search_biomes")));
        search.setHint(tr("search_biomes"));
        search.setMaxLength(80);
        search.setResponder(query->{choicePage=0;syncConditions(menu.snapshot());});
        int rows=Math.max(1,(imageHeight-180)/18);
        for(int i=0;i<rows;i++){
            int index=i,y=154+i*18;
            var available=button(Component.empty(),11,y,biomePane-6,()->{selectedAvailable=entry(choicesData,choicePage,choices.size(),index);syncConditions(menu.snapshot());});
            LifeUiStyle.reverse(available,()->moveBiome(index,true));
            choices.add(available);
            var selected=button(Component.empty(),right+3,y,biomePane-6,()->{selectedAllowed=entry(allowedData,allowedPage,choices.size(),index);syncConditions(menu.snapshot());});
            LifeUiStyle.reverse(selected,()->moveBiome(index,false));
            allowedChoices.add(selected);
        }
        int middleY=(154+imageHeight-22)/2-17;
        addBiome=button(Component.literal(">>"),imageWidth/2-14,middleY,28,()->transferBiome(selectedAvailable,true));
        removeBiome=button(Component.literal("<<"),imageWidth/2-14,middleY+18,28,()->transferBiome(selectedAllowed,false));
        int y=imageHeight-22;
        previous=button(Component.literal("<"),8,y,24,()->{choicePage--;syncConditions(menu.snapshot());});
        next=button(Component.literal(">"),8+biomePane-24,y,24,()->{choicePage++;syncConditions(menu.snapshot());});
        allowedPrevious=button(Component.literal("<"),right,y,24,()->{allowedPage--;syncConditions(menu.snapshot());});
        allowedNext=button(Component.literal(">"),imageWidth-32,y,24,()->{allowedPage++;syncConditions(menu.snapshot());});
    }

    private void number(String key,String label,int x,int y,int pane){
        labels.add(new Label(tr(label),x,y+4,pane-74));
        var field=addRenderableWidget(new EditBox(font,leftPos+x+pane-70,topPos+y,48,16,tr(label)));
        field.setTooltip(Tooltip.create(tr(label)));
        field.setFilter(value->value.matches("[0-9]{0,5}"));
        field.setValue(numberDraft.getOrDefault(key,""));
        field.setResponder(value->numberDraft.put(key,value));
        numbers.put(key,field);
        numberChecks.put(key,button(Component.literal("✓"),x+pane-18,y,18,()->applyNumber(key)));
    }

    private boolean loadConditionDraft(CompoundTag snapshot){
        boolean ready=snapshot.getBoolean("Ready")&&file.equals(snapshot.getString("Selected"))&&snapshot.getBoolean("Configured");
        if(ready&&!loadedConditions){
            var conditions=snapshot.getCompound("Conditions");
            for(String key:NUMERIC_KEYS){
                int value=key.equals("AbsenceSeconds")?(conditions.contains(key)?conditions.getInt(key):180):snapshot.contains(key)?snapshot.getInt(key):switch(key){case "RetrySeconds"->5;case "SearchAttempts"->1;case "MinDistance"->48;case "MaxDistance"->96;default->0;};
                numberDraft.put(key,Integer.toString(value));
            }
            numbers.forEach((key,field)->field.setValue(numberDraft.get(key)));
            announcementDraft=snapshot.getString("SpawnAnnouncement");
            announcementCoordinates=!announcementDraft.isBlank()&&snapshot.getBoolean("SpawnAnnouncementCoordinates");
            if(announcement!=null)announcement.setValue(announcementDraft);
            faction=snapshot.getUUID("Faction");releaseAfterSpawn=snapshot.getBoolean("ReleaseAfterSpawn");
            sky=!conditions.contains("Sky")||conditions.getBoolean("Sky");ignoreBounds=conditions.getBoolean("IgnoreBounds");
            clearObstacles=conditions.getBoolean("ClearObstacles");legacyClearTrees=conditions.getBoolean("ClearTrees");
            allowed.clear();allowed.addAll(strings(conditions,"Biomes"));loadedConditions=true;
        }
        return ready;
    }

    private void syncNumbers(boolean ready){
        numbers.forEach((key,field)->{
            boolean editable=ready&&numberUsed(key);field.active=editable;field.setEditable(editable);
            if(!editable)field.setFocused(false);
            numberChecks.get(key).active=editable&&numberValue(key)>=0&&distanceValid();
        });
    }

    private boolean distanceValid(){return numberValue("MinDistance")>=0&&numberValue("MaxDistance")>=numberValue("MinDistance");}

    private void density(){
        button(tr("back"),imageWidth-66,4,58,()->show(Page.CONDITIONS));
        int pane=imageWidth-16;
        number("Cooldown","cooldown",8,32,pane);
        number("RetrySeconds","retry_seconds",8,56,pane);
        number("SearchAttempts","search_attempts",8,80,pane);
        number("MinDistance","min_distance",8,104,pane);
        number("MaxDistance","max_distance",8,128,pane);
        for(var entry:numbers.entrySet())entry.getValue().setTooltip(Tooltip.create(tr(switch(entry.getKey()){
            case "Cooldown"->"cooldown_help";case "RetrySeconds"->"retry_help";case "SearchAttempts"->"attempts_help";default->"distance_help";
        })));
    }

    private void syncDensity(CompoundTag snapshot){syncNumbers(loadConditionDraft(snapshot));}

    private void syncConditions(CompoundTag snapshot){
        boolean ready=loadConditionDraft(snapshot);
        String query=search==null?"":search.getValue().toLowerCase(Locale.ROOT);
        choicesData=ready?java.util.stream.Stream.concat(java.util.stream.Stream.of(OVERWORLD),strings(snapshot,"AvailableBiomes").stream()).filter(name->!allowed.contains(name))
                .filter(name->name.toLowerCase(Locale.ROOT).contains(query)||biome(name).getString().toLowerCase(Locale.ROOT).contains(query)).toList():List.of();
        allowedData=ready?allowed.stream().sorted().toList():List.of();
        choicePage=syncList(choices,choicesData,choicePage,previous,next,selectedAvailable,true);
        allowedPage=syncList(allowedChoices,allowedData,allowedPage,allowedPrevious,allowedNext,selectedAllowed,true);
        addBiome.active=choicesData.contains(selectedAvailable)&&allowed.size()<256;
        removeBiome.active=allowedData.contains(selectedAllowed);
        skyButton.setMessage(tr("sky",state(sky)));ignoreButton.setMessage(tr("ignore_bounds",state(ignoreBounds)));
        treesButton.setMessage(legacyClearTrees&&!clearObstacles?tr("clear_obstacles_legacy"):tr("clear_obstacles",state(clearObstacles)));
        var factionName=tr("wilds");
        for(var value:snapshot.getList("Factions",10)){
            var row=(CompoundTag)value;
            if(faction.equals(row.getUUID("Id")))factionName=Component.literal(row.getString("Name"));
        }
        factionButton.setMessage(tr("faction",factionName));
        skyButton.active=factionButton.active=treesButton.active=ready;
        ignoreButton.active=ready&&!clearObstacles;
        syncNumbers(ready);
        densityButton.active=ready;
        announcement.active=announcementCheck.active=ready;
        syncAnnouncementCoordinates(snapshot);
        release.active=ready;
        release.setMessage(Component.literal(releaseAfterSpawn?"[✓] ":"[ ] ").append(tr("release_after_spawn")));
        LifeUiStyle.fill(release,releaseAfterSpawn?0xFF24583D:0);
        preview.active=trySpawn.active=ready&&distanceValid()&&numbers.keySet().stream().allMatch(key->!numberUsed(key)||numberValue(key)>=0);
    }

    private void syncAnnouncementCoordinates(CompoundTag snapshot){
        if(announcementCoordinatesCheck==null)return;
        boolean ready=snapshot.getBoolean("Ready")&&file.equals(snapshot.getString("Selected"))&&snapshot.getBoolean("Configured");
        announcementCoordinatesCheck.active=ready&&!announcementDraft.isBlank();
        announcementCoordinatesCheck.setMessage(Component.literal(announcementCoordinates?"[✓] ":"[ ] ").append(tr("announcement_coordinates")));
        LifeUiStyle.fill(announcementCoordinatesCheck,announcementCoordinates?0xFF24583D:0);
    }

    private void moveBiome(int index,boolean add){
        String id=entry(add?choicesData:allowedData,add?choicePage:allowedPage,choices.size(),index);
        transferBiome(id,add);
    }

    private void transferBiome(String id,boolean add){
        if(id.isEmpty())return;
        if(add&&allowed.size()<256)allowed.add(id);
        if(!add)allowed.remove(id);
        selectedAvailable=selectedAllowed="";
        syncConditions(menu.snapshot());
        save("save_rule");
    }

    private void factions(){
        button(tr("back"),imageWidth-66,4,58,()->{choicePage=0;show(Page.CONDITIONS);});
        int column=(imageWidth-24)/3;
        for(int i=0;i<3*Math.max(1,(imageHeight-58)/20);i++){
            int index=i;
            choices.add(button(Component.empty(),8+(i%3)*(column+4),26+(i/3)*20,column,()->{
                faction=factionIds.get(choicePage*choices.size()+index);
                choicePage=0;
                show(Page.CONDITIONS);
                save("save_rule");
            }));
        }
        int y=imageHeight-22;
        previous=button(Component.literal("<"),imageWidth/2-28,y,24,()->{choicePage--;syncFactions(menu.snapshot());});
        next=button(Component.literal(">"),imageWidth/2+4,y,24,()->{choicePage++;syncFactions(menu.snapshot());});
    }

    private void syncFactions(CompoundTag snapshot){
        var names=new LinkedHashMap<UUID,Component>();
        names.put(FactionRegistry.WILDS,tr("wilds"));
        for(var value:snapshot.getList("Factions",10)){
            var row=(CompoundTag)value;
            names.put(row.getUUID("Id"),Component.literal(row.getString("Name")).append(FactionLabels.visibility(row)));
        }
        factionIds=List.copyOf(names.keySet());
        int size=choices.size();
        choicePage=Math.clamp(choicePage,0,(factionIds.size()-1)/size);
        previous.active=choicePage>0;
        next.active=(choicePage+1)*size<factionIds.size();
        for(int i=0;i<size;i++){
            var button=choices.get(i);
            int index=choicePage*size+i;
            button.visible=button.active=index<factionIds.size();
            if(button.visible){
                var id=factionIds.get(index);
                button.setMessage(id.equals(faction)?Component.literal("✓ ").append(names.get(id)):names.get(id));
            }
        }
    }

    private static int minNumber(String key){return switch(key){case "Cooldown"->10;case "MaxActive","RetrySeconds","SearchAttempts"->1;case "MinDistance","MaxDistance"->16;default->0;};}
    private static int maxNumber(String key){return switch(key){case "Gap"->128;case "MaxActive"->64;case "RetrySeconds"->3600;case "SearchAttempts"->8;case "MinDistance"->512;case "MaxDistance"->1024;default->86400;};}
    private int numberValue(String key){
        var value=numbers.containsKey(key)?numbers.get(key).getValue():numberDraft.getOrDefault(key,"");
        if(value.isEmpty())return -1;
        int number=Integer.parseInt(value);
        return number>=minNumber(key)&&number<=maxNumber(key)?number:-1;
    }

    private void applyNumber(String key){
        int value=numberValue(key);
        if(value<0||!numberChecks.get(key).active)return;
        save("save_rule");
        numbers.get(key).setFocused(false);
    }

    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(page==Page.CONDITIONS&&(key==257||key==335)&&announcement.isFocused()){
            save("save_rule");announcement.setFocused(false);return true;
        }
        if((page==Page.CONDITIONS||page==Page.DENSITY)&&(key==257||key==335))
            for(var field:numbers.entrySet())if(field.getValue().isFocused()){
                applyNumber(field.getKey());
                return true;
            }
        // Printable keys are delivered separately through charTyped. Do not let
        // unhandled editing keys fall through to container shortcuts (E, hotbar, drop).
        if(key!=256&&key!=258)for(var child:children())if(child instanceof EditBox input&&input.canConsumeInput()){
            input.keyPressed(key,scan,modifiers);
            return true;
        }
        return super.keyPressed(key,scan,modifiers);
    }

    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if((page==Page.CONDITIONS||page==Page.DENSITY)&&vertical!=0)for(var entry:numbers.entrySet()){
            var input=entry.getValue();
            if(!input.active||!input.isMouseOver(x,y))continue;
            var key=entry.getKey();
            int step=switch(key){case "Cooldown","AbsenceSeconds"->30;case "Lifetime"->60;default->1;};
            int current=input.getValue().isEmpty()?minNumber(key):Integer.parseInt(input.getValue());
            input.setValue(Integer.toString(Math.clamp(current+(vertical>0?step:-step),minNumber(key),maxNumber(key))));
            sync(menu.snapshot());
            return true;
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }

    private boolean numberUsed(String key){
        return !(key.equals("MaxActive")&&releaseAfterSpawn)
                &&!(releaseAfterSpawn&&(key.equals("Lifetime")||key.equals("AbsenceSeconds")));
    }

    private void save(String operation){
        var settings=new CompoundTag();
        settings.putString("File",file);
        if(!distanceValid())return;
        for(var key:NUMERIC_KEYS){
            int value=numberValue(key);
            if(!numberUsed(key)){
                var current=menu.snapshot();
                value=key.equals("AbsenceSeconds")?current.getCompound("Conditions").getInt(key):current.getInt(key);
                if(key.equals("MaxActive"))value=Math.max(1,value);
            }
            if(value<0)return;
            settings.putInt(key,value);
        }
        settings.putUUID("Faction",faction);settings.putBoolean("ReleaseAfterSpawn",releaseAfterSpawn);
        settings.putString("SpawnAnnouncement",announcementDraft);
        settings.putBoolean("SpawnAnnouncementCoordinates",announcementCoordinates&&!announcementDraft.isBlank());
        settings.putBoolean("Sky",sky);settings.putBoolean("IgnoreBounds",ignoreBounds);settings.putBoolean("ClearTrees",legacyClearTrees);settings.putBoolean("ClearObstacles",clearObstacles);
        var biomes=new ListTag();allowed.forEach(id->biomes.add(StringTag.valueOf(id)));settings.put("Biomes",biomes);
        send(operation,settings);
    }

    private int syncList(List<Button> widgets,List<String> values,int page,Button prev,Button next,String selection,boolean biome){
        int size=widgets.size();
        page=Math.clamp(page,0,Math.max(0,(values.size()-1)/size));
        prev.active=page>0;next.active=(page+1)*size<values.size();
        for(int i=0;i<size;i++){
            var button=widgets.get(i);var id=entry(values,page,size,i);
            button.active=button.visible=!id.isEmpty();
            if(!id.isEmpty()){
                var name=biome?biome(id):Component.literal(id);
                button.setMessage(id.equals(selection)?Component.literal("▶ ").append(name):name);
                button.setTooltip(Tooltip.create(biome?Component.empty().append(name).append(" · ").append(tr("biome_right_click")):name));
            }
        }
        return page;
    }

    private static Component biome(String name){
        if(name.equals(OVERWORLD))return tr("overworld_biomes");
        var id=ResourceLocation.parse(name);
        return Component.translatableWithFallback("biome."+id.getNamespace()+"."+id.getPath(),name);
    }

    private static Component state(boolean on){return Component.translatable("core.sablelife."+(on?"on":"off"));}
    private static String entry(List<String> values,int page,int size,int row){int i=page*size+row;return i<values.size()?values.get(i):"";}
    private static List<String> strings(CompoundTag tag,String key){return tag.getList(key,8).stream().map(value->value.getAsString()).toList();}
    private void back(){button(tr("back"),imageWidth-66,4,58,()->show(Page.RULES));}
    private void show(Page next){deleteConfirm="";clearConfirm="";page=next;rebuildWidgets();}
    private void sendRule(String operation,String file){var tag=new CompoundTag();tag.putString("File",file);send(operation,tag);}
    private void send(String operation,CompoundTag tag){sendRaw(operation,tag.toString());}
    private void sendRaw(String operation,String data){if(minecraft.getConnection()==null)return;PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,0,NO_ID,"wild_encounter",operation,0,data));}
    private void sync(CompoundTag tag){
        switch(page){
            case RULES -> syncRules(tag);
            case BLUEPRINTS -> syncBlueprints(tag);
            case CONDITIONS -> syncConditions(tag);
            case FACTIONS -> syncFactions(tag);
            case DENSITY -> syncDensity(tag);
        }
    }

    @Override protected void containerTick(){
        super.containerTick();
        if(deleteTicks>0&&--deleteTicks==0)deleteConfirm="";
        if(clearTicks>0&&--clearTicks==0)clearConfirm="";
        var tag=menu.snapshot();
        if(!opened&&tag.getBoolean("Ready")){
            opened=true;
            if(tag.getBoolean("ResumeConditions")&&page==Page.RULES){
                file=tag.getString("Selected");
                show(Page.CONDITIONS);
                return;
            }
        }
        sync(tag);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xFF9B614E);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF242632);
        if(page==Page.CONDITIONS){
            int y=topPos+133,bottom=topPos+imageHeight-25;
            for(int x:new int[]{leftPos+8,leftPos+imageWidth-biomePane-8}){
                g.fill(x,y,x+biomePane,bottom,0xFF555067);
                g.fill(x+1,y+1,x+biomePane-1,bottom-1,0xFF282A37);
            }
        }
    }

    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){
        var tag=menu.snapshot();
        var title=switch(page){
            case RULES -> tr("manage");
            case BLUEPRINTS -> tr("choose_blueprint");
            case CONDITIONS -> tr("conditions").copy().append(" · ").append(file);
            case FACTIONS -> tr("choose_faction");
            case DENSITY -> tr("density_title");
        };
        if(page==Page.RULES)g.drawCenteredString(font,title,imageWidth/2,8,0xE6D8EA);
        else draw(g,title,8,8,imageWidth-76);
        for(var label:labels)draw(g,label.text(),label.x(),label.y(),label.width(),.85f);
        if(page==Page.DENSITY){
            draw(g,tr("density_limits",tag.contains("ServerPopulationLimit")?tag.getInt("ServerPopulationLimit"):64,tag.contains("NearbyPopulationLimit")?tag.getInt("NearbyPopulationLimit"):4),8,157,imageWidth-16);
            draw(g,tr("density_limit_help"),8,174,imageWidth-16);
            if(!distanceValid())draw(g,tr("distance_invalid"),8,191,imageWidth-16);
        }
        if(page==Page.RULES){
            int radius=tag.contains("WorkbenchProtectionRadius")?tag.getInt("WorkbenchProtectionRadius"):64;
            draw(g,radius>0?tr("workbench_notice",radius):tr("workbench_disabled"),8,imageHeight-49,imageWidth-16,.85f);
            draw(g,tr("workbench_notice_detail"),8,imageHeight-39,imageWidth-16,.85f);
        }
        if(page==Page.RULES)for(int i=0;i<ruleButtons.size();i++){
            var widgets=ruleButtons.get(i);if(!widgets.file.visible)continue;
            var quantity=Component.literal(Integer.toString(row(i).getInt("ActiveCount"))).withStyle(net.minecraft.ChatFormatting.GREEN);
            var text=tr("current_count",quantity);
            float scale=Math.min(LifeUiStyle.scale(),widgets.countWidth/(float)Math.max(1,font.width(text)));
            g.pose().pushPose();g.pose().translate(widgets.countX,widgets.countY+(16-font.lineHeight*scale)/2f,0);g.pose().scale(scale,scale,1);
            g.drawString(font,text,0,0,0xE6D8EA,false);g.pose().popPose();
        }
        String status=tag.getString("Status");
        if(!tag.getBoolean("Available"))draw(g,tr("requires"),8,imageHeight-34,imageWidth-16);
        else if(!status.isEmpty()){
            int x=page==Page.CONDITIONS?36:8;
            var text=tr(status,tag.getString("Error"));
            int width=page==Page.CONDITIONS?biomePane-54:imageWidth-16;
            float scale=Math.min(1,width/(float)Math.max(1,font.width(text)));
            g.pose().pushPose();g.pose().translate(x,imageHeight-(page==Page.DENSITY?12:page==Page.CONDITIONS?18:34),0);g.pose().scale(scale,scale,1);
            g.drawString(font,text,0,0,status.startsWith("placement_")?0xFFFFCC66:0xE6D8EA,false);g.pose().popPose();
        }
    }

    private void draw(GuiGraphics g,Component text,int x,int y,int width){
        draw(g,text,x,y,width,1);
    }
    private void draw(GuiGraphics g,Component text,int x,int y,int width,float maximumScale){
        float scale=Math.min(maximumScale,width/(float)Math.max(1,font.width(text)));
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.drawString(font,text,0,0,0xE6D8EA,false);g.pose().popPose();
    }
}
