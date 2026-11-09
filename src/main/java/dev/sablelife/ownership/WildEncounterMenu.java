package dev.sablelife.ownership;

import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.io.IOException;
import java.util.List;

public final class WildEncounterMenu extends AbstractContainerMenu {
    private record Resume(net.minecraft.resources.ResourceLocation dimension,String file){}
    private static final java.util.Map<ServerPlayer,Resume> RESUME=new java.util.WeakHashMap<>();
    private final ServerPlayer author;
    private final net.minecraft.server.level.ServerLevel dimension;
    private List<String> files;
    private List<String> blueprintFiles=List.of();
    private final SimpleContainer data = new SimpleContainer(1);
    private String selected;
    private String status = "", error = "";
    private String deleteTarget = "";
    private long deleteUntil;
    private CompoundTag clipboard;
    private int ruleRevision;
    private final List<String> biomes;
    private final boolean resumeConditions;

    public WildEncounterMenu(int id, Inventory inventory) { this(id, inventory, null); }

    public WildEncounterMenu(int id, Inventory inventory, ServerPlayer author) {
        super(SableLife.WILD_ENCOUNTER_MENU.get(), id);
        this.author = author;
        dimension = author == null ? null : author.serverLevel();
        files = files();
        biomes=author==null?List.of():WildEncounters.biomes(dimension).stream().map(Object::toString).sorted().toList();
        var resume=author==null?null:RESUME.remove(author);
        resumeConditions=resume!=null&&resume.dimension().equals(dimension.dimension().location())
                &&WildEncounters.get(author.server).snapshot(dimension,resume.file()).getBoolean("Configured");
        selected = resumeConditions?resume.file():files.isEmpty()?"":files.getFirst();
        addSlot(new Slot(data, 0, -10000, -10000) {
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean isActive() { return false; }
        });
        refresh();
    }

    public static boolean allowed(Player player) {
        return player.isCreative() && player.canUseGameMasterBlocks();
    }

    @Override public boolean stillValid(Player player) {
        return author == null || SableLifeServerConfig.wildEncountersEnabled() && player == author && author.serverLevel() == dimension && allowed(player)
                && (player.getMainHandItem().is(SableLife.WILD_ENCOUNTER_TOOL.get()) || player.getOffhandItem().is(SableLife.WILD_ENCOUNTER_TOOL.get()));
    }

    public CompoundTag snapshot() {
        return data.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private List<String> files() {
        if(author==null)return List.of();
        blueprintFiles=BlueprintSpawns.files(author);
        var names = new java.util.TreeSet<>(blueprintFiles);
        names.addAll(WildEncounters.get(author.server).configuredFiles(dimension));
        return List.copyOf(names);
    }

    private static ListTag names(List<String> values){
        var tags=new ListTag();
        for(var value:values)tags.add(StringTag.valueOf(value));
        return tags;
    }

    private void refresh() {
        if (author == null) return;
        if(!deleteTarget.isEmpty()&&dimension.getGameTime()>deleteUntil){
            deleteTarget="";
            if(status.equals("confirm_delete_blueprint"))status="";
        }
        var tag = WildEncounters.get(author.server).snapshot(dimension, selected);
        tag.putBoolean("Ready", true);
        tag.putBoolean("ResumeConditions",resumeConditions);
        tag.putBoolean("Available", BlueprintSpawns.available());
        tag.putInt("WorkbenchProtectionRadius",SableLifeServerConfig.wildWorkbenchProtectionRadius());
        tag.putString("Dimension", dimension.dimension().location().toString());
        tag.putString("Selected", selected);
        tag.putString("Status", status);
        tag.putString("Error", error);
        tag.put("Files", names(files));
        tag.put("BlueprintFiles",names(blueprintFiles));
        var rules=new ListTag();
        var encounters=WildEncounters.get(author.server);
        for(var file:encounters.configuredFiles(dimension)){
            var full=encounters.snapshot(dimension,file);
            var row=new CompoundTag();
            row.putString("File",file);
            for(var key:List.of("Enabled","Active","ActivateCore","Clearing"))row.putBoolean(key,full.getBoolean(key));
            row.putInt("ActiveCount",full.getInt("ActiveCount"));
            rules.add(row);
        }
        tag.put("Rules",rules);
        tag.put("AvailableBiomes",names(biomes));
        tag.putBoolean("Clipboard",clipboard!=null);
        tag.putString("DeleteTarget",deleteTarget);
        tag.putInt("RuleRevision",ruleRevision);
        tag.put("Factions", dev.sablelife.heart.FactionRegistry.get(author.server).snapshot(author));
        if (tag.equals(snapshot())) return;
        var stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        data.setItem(0, stack);
    }

    public void action(Player player, String operation, String text) {
        if (author == null || !stillValid(player) || !BlueprintSpawns.available()) return;
        if(java.util.Set.of("load_rule","toggle_rule","remove_rule","clear_rule","number_rule","save_rule","save_preview_rule","save_try_rule").contains(operation)){
            updateRule(operation,text);
            refresh();
            broadcastChanges();
            return;
        }
        if (operation.equals("select")) {
            if (files.contains(text)) { selected = text; status = ""; deleteTarget=""; }
        } else if(operation.equals("delete_blueprint")&&files.contains(text)){
            long now=dimension.getGameTime();
            try{
                if(!text.equals(deleteTarget)||now>deleteUntil){
                    deleteTarget=text;deleteUntil=now+100;status="confirm_delete_blueprint";
                }else{
                    deleteTarget="";
                    if(WildEncounters.get(author.server).deleteBlueprint(author.server,text)){
                        files=files();if(!files.contains(selected))selected=files.isEmpty()?"":files.getFirst();
                        ruleRevision++;status="deleted_blueprint";
                    }else status="active";
                }
            }catch(IOException|RuntimeException failure){status="error";error=String.valueOf(failure.getMessage());}
        } else if (files.contains(selected)) {
            var encounters = WildEncounters.get(author.server);
            try {
                switch (operation) {
                    case "save" -> {
                        var values = text.split(",", -1);
                        if (values.length<5||values.length>7||!values[3].matches("[01]")||!values[4].matches("[01]")) return;
                        var rule=encounters.snapshot(dimension,selected);
                        var faction=values.length>=6?java.util.UUID.fromString(values[5]):rule.getUUID("Faction");
                        int limit=values.length==7?Integer.parseInt(values[6]):rule.getInt("MaxActive");
                        encounters.configure(dimension, selected, Integer.parseInt(values[0]), Integer.parseInt(values[1]),
                                Integer.parseInt(values[2]), values[3].equals("1"), values[4].equals("1"), faction,
                                WildEncounters.Conditions.load(rule.getCompound("Conditions")),limit);
                        status = "saved";
                        ruleRevision++;
                    }
                    case "conditions" -> {
                        var settings=net.minecraft.nbt.TagParser.parseTag(text);
                        if(!selected.equals(settings.getString("File")))return;
                        var rule=encounters.snapshot(dimension,selected);
                        if(settings.contains("Lifetime"))rule.putInt("Lifetime",settings.getInt("Lifetime"));
                        apply(encounters,rule,WildEncounters.Conditions.load(settings));
                        status="saved";ruleRevision++;
                    }
                    case "copy" -> {
                        var rule=encounters.snapshot(dimension,selected);
                        if(!rule.getBoolean("Configured"))return;
                        clipboard=rule;status="copied";
                    }
                    case "paste" -> {
                        if(clipboard==null)return;
                        apply(encounters,clipboard,WildEncounters.Conditions.load(clipboard.getCompound("Conditions")));
                        status="pasted";ruleRevision++;
                    }
                    case "remove" -> status = encounters.removeRule(dimension, selected) ? "removed" : "active";
                    case "preview", "try" -> {
                        boolean preview = operation.equals("preview");
                        var attempt=encounters.attemptPreviewOrSpawn(author,selected,preview);
                        if (attempt.success()) {
                            author.closeContainer();
                            return;
                        }
                        status = attempt.reason();
                    }
                    default -> { return; }
                }
            } catch (IOException | RuntimeException | com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
                status = "error";
                error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            }
        }
        refresh();
        broadcastChanges();
    }

    private void updateRule(String operation,String text){
        var encounters=WildEncounters.get(author.server);
        try{
            var settings=net.minecraft.nbt.TagParser.parseTag(text);
            var file=settings.getString("File");
            var rule=encounters.snapshot(dimension,file);
            if(!operation.equals("load_rule")&&!rule.getBoolean("Configured"))return;
            error="";
            switch(operation){
                case "load_rule" -> {
                    if(!BlueprintSpawns.files(author).contains(file))return;
                    encounters.loadBlueprint(dimension,settings.getString("Previous"),file,settings.getBoolean("ActivateCore"));
                    files=files();
                    selected=file;
                    status="rule_loaded";
                }
                case "toggle_rule" -> {
                    rule.putBoolean("Enabled",!rule.getBoolean("Enabled"));
                    configure(encounters,file,rule,WildEncounters.Conditions.load(rule.getCompound("Conditions")));
                    status="saved";
                }
                case "remove_rule" -> status=encounters.removeRule(dimension,file)?"removed":"active";
                case "clear_rule" -> status=encounters.clearCurrent(dimension,file)?"clear_queued":"nothing_to_clear";
                case "number_rule" -> {
                    var key=settings.getString("Field");
                    if(!java.util.Set.of("Gap","Cooldown","Lifetime","AbsenceSeconds","MaxActive").contains(key))return;
                    var conditions=rule.getCompound("Conditions");
                    if(key.equals("AbsenceSeconds"))conditions.putInt(key,settings.getInt("Value"));
                    else rule.putInt(key,settings.getInt("Value"));
                    configure(encounters,file,rule,WildEncounters.Conditions.load(conditions));
                    status="saved";
                }
                case "save_rule","save_preview_rule","save_try_rule" -> {
                    settings.putBoolean("Enabled",rule.getBoolean("Enabled"));
                    configure(encounters,file,settings,WildEncounters.Conditions.load(settings));
                    status="saved";
                    if(!operation.equals("save_rule")){
                        boolean preview=operation.equals("save_preview_rule");
                        var attempt=encounters.attemptPreviewOrSpawn(author,file,preview);
                        if(attempt.success()){
                            if(preview)RESUME.put(author,new Resume(dimension.dimension().location(),file));
                            author.closeContainer();
                        }
                        else status=attempt.reason();
                    }
                }
            }
            ruleRevision++;
        }catch(IOException|RuntimeException|com.mojang.brigadier.exceptions.CommandSyntaxException failure){
            status="error";
            error=failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();
            ruleRevision++;
        }
    }

    private void apply(WildEncounters encounters, CompoundTag settings, WildEncounters.Conditions conditions) throws IOException{
        configure(encounters,selected,settings,conditions);
    }

    private void configure(WildEncounters encounters,String file,CompoundTag settings,WildEncounters.Conditions conditions) throws IOException{
        var density=encounters.snapshot(dimension,file);density.merge(settings);
        encounters.configure(dimension,file,settings.getInt("Gap"),settings.getInt("Lifetime"),settings.getInt("Cooldown"),
                settings.getBoolean("Enabled"),false,settings.getUUID("Faction"),conditions,settings.getInt("MaxActive"),
                settings.contains("ReleaseAfterSpawn")?settings.getBoolean("ReleaseAfterSpawn"):encounters.snapshot(dimension,file).getBoolean("ReleaseAfterSpawn"),
                new WildEncounters.Density(density.getInt("RetrySeconds"),density.getInt("SearchAttempts"),density.getInt("MinDistance"),density.getInt("MaxDistance")));
        if(settings.contains("SpawnAnnouncement"))encounters.setAnnouncement(dimension,file,settings.getString("SpawnAnnouncement"));
        if(settings.contains("SpawnAnnouncementCoordinates"))encounters.setAnnouncementCoordinates(dimension,file,settings.getBoolean("SpawnAnnouncementCoordinates"));
    }

    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
