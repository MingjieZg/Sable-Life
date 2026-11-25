package dev.sablelife.heart;

import dev.sablelife.SableLife;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

/** Invisible snapshot slot, with all core mutations validated by the server. */
public final class HeartSettingsMenu extends AbstractContainerMenu {
    public static final int HIT_EFFECT_PAGE = 7;
    private final HeartBlockEntity heart;
    private final Player viewer;
    private final int initialPage;
    private String relationStatus="";
    private java.util.UUID inspectedFaction;
    private BlueprintFaction blueprintSource;
    private UUID blueprintLocal;
    private int blueprintRevision;
    private final SimpleContainer snapshot = new SimpleContainer(1);
    public HeartSettingsMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public HeartSettingsMenu(int id, Inventory inventory, HeartBlockEntity heart) {
        this(id, inventory, heart, 0);
    }
    public HeartSettingsMenu(int id, Inventory inventory, HeartBlockEntity heart, int page) {
        super(SableLife.SETTINGS_MENU.get(), id); this.heart = heart; viewer = inventory.player;
        initialPage = page == HIT_EFFECT_PAGE ? page : Math.clamp(page, 0, heart != null && heart.isSurvivalCore() ? 2 : 6);
        addSlot(new Slot(snapshot, 0, -10000, -10000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean isActive() { return false; }
        }); refresh();
    }
    public CompoundTag snapshot() { return snapshot.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    private void refresh() {
        if (heart == null) return;
        var tag = new CompoundTag(); tag.putBoolean("Ready", true);tag.putString("RelationStatus",relationStatus);
        tag.putInt("FactionCreationLimit",dev.sablelife.SableLifeServerConfig.maximumFactionsPerPlayer());
        tag.putInt("InitialPage", initialPage);
        if (viewer instanceof ServerPlayer sp) {
            var registry = FactionRegistry.get(sp.server);
            tag.put("Relations", heart.relations().save());
            tag.put("Factions", registry.snapshot(sp));
            var players = new ListTag();
            for (var player : sp.server.getPlayerList().getPlayers()) {
                var row = new CompoundTag();
                row.putUUID("Id", player.getUUID());
                row.putString("Name", player.getGameProfile().getName());
                players.add(row);
            }
            tag.put("Players", players);
            if (inspectedFaction != null) tag.put("FactionDetail", registry.detail(sp, inspectedFaction));
            var blueprint = blueprintSnapshot(sp, registry);
            if (!blueprint.isEmpty()) tag.put("BlueprintFactionView", blueprint);
        }
        tag.putInt("targetPriority",heart.relations().priority());tag.putString("ownerName",heart.relations().ownerName());
        if(heart.isSurvivalCore()){
            tag.putInt("ActivationTokenRate",dev.sablelife.SableLifeServerConfig.activationTokenRate());
            tag.putFloat("CurrentLife",heart.health().current());
        }
        tag.putBoolean("SurvivalCore",heart.isSurvivalCore());tag.putBoolean("ReleaseAllowed",viewer.isCreative()&&!heart.isControlCore());
        tag.putBoolean("Editable", heart.canEdit(viewer)); tag.putBoolean("LiveControls", heart.canToggleBreaking(viewer));
        tag.putString("name", heart.creatureName()); tag.putFloat("health", heart.health().maximum());
        tag.putDouble("delay", heart.health().deathDelay() / 20.); tag.putInt("xp", heart.displayedExperience());tag.putBoolean("xpLevels",heart.deathExperienceLevels());tag.putInt("xpPoints",heart.deathExperience());
        tag.putBoolean("explosion", heart.allowExplosion()); tag.putInt("MiningMode",heart.miningMode()); tag.putBoolean("miningDrops", heart.miningDrops()); tag.putBoolean("bossbar", heart.showBossBar());
        tag.putBoolean("limbCleanup", heart.limbCleanup().enabled()); tag.putInt("limbCleanupDelay", heart.limbCleanup().delay());
        tag.putBoolean("killHeal",heart.killHeal());tag.putBoolean("survivalInteraction",heart.allowSurvivalInteraction());tag.putBoolean("lifeAttacks",heart.allowLifeAttacks());tag.putInt("bossbarColor",heart.bossBarColor().ordinal());
        tag.putInt("voidDeathHeight",heart.voidDeathHeight());tag.putBoolean("VoidDeathDefault",heart.defaultVoidDeathHeight());
        tag.putBoolean("forceChunkLoading",heart.forceChunkLoading());tag.putBoolean("ChunkLoadingEditable",heart.canEditChunkLoading(viewer));
        tag.putString("ChunkLoadingStatus",dev.sablelife.ownership.CoreChunkLoading.status(heart));tag.putInt("ChunkLoadingCount",dev.sablelife.ownership.CoreChunkLoading.count(heart));
        tag.putDouble("regenPercent",heart.regenPercentPerSecond());tag.putDouble("regen",heart.regenPerSecond()); tag.putDouble("regenDelay",heart.regenDelay()/20.);
        tag.putDouble("knockbackResistance",heart.knockbackResistance());
        var crush=heart.terrainSettings();tag.put("Crush",crush.save());tag.putBoolean("crushEnabled",crush.enabled());tag.putBoolean("crushFloor",crush.floor());tag.putBoolean("crushDebris",crush.debris());tag.putBoolean("crushDrops",crush.drops());tag.putDouble("crushDelay",crush.delay()/20.);
        var defense=heart.adaptiveDefense();tag.putBoolean("defenseEnabled",defense.enabled());tag.putDouble("defenseThreshold",defense.threshold());tag.putDouble("defenseMaximum",defense.maximum());tag.putDouble("defenseWindow",defense.window()/20.);tag.putDouble("DefenseDps",defense.dps());tag.putDouble("DefenseReduction",defense.reduction()*100);
        tag.putString("State", heart.health().state().name());
        var hit = heart.hitPresentation();
        tag.putInt("HitParticleTypes", hit.particleSelection().particles().size());
        tag.putInt("hitParticleCount", hit.count());
        tag.putDouble("hitParticleSpread", hit.spread());
        for (var key : java.util.List.of("deathParticle", "deathSound", "deathSoundLast")) tag.putString(key, heart.deathPresentation().text(key));
        tag.putInt("DeathParticleCount",heart.deathPresentation().particleSelection().particles().size());
        for (var key : java.util.List.of("deathCount", "deathSpread", "deathDuration", "deathScale", "deathVolume", "deathPitch", "deathRadius", "deathEffectRadius", "deathPattern")) tag.putDouble(key, heart.deathPresentation().value(key));
        tag.putInt("VanishParticleCount",heart.removalPresentation().particleSelection().particles().size());
        for(var suffix:java.util.List.of("Sound","SoundLast"))tag.putString("vanish"+suffix,heart.removalPresentation().text("death"+suffix));
        for(var suffix:java.util.List.of("Count","Spread","Volume","Pitch","Radius","Pattern"))tag.putDouble("vanish"+suffix,heart.removalPresentation().value("death"+suffix));
        for(String key:java.util.List.of("deathSound")){
            tag.putInt(key+"Count",heart.deathPresentation().soundTimeline(key).rows().size());tag.putInt("vanish"+key.substring(5)+"Count",heart.removalPresentation().soundTimeline(key).rows().size());
        }
        var stack = new ItemStack(Items.PAPER); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); snapshot.setItem(0, stack);
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) {
        return heart == null || heart.canAccessEditor(player)
            && heart.editorInRange(player, 64);
    }
    public boolean action(Player player, String key, double value, String text) {
        if (heart == null || !stillValid(player)) return false;
        if(key.startsWith("relation:")){boolean result=relations(player,key.substring(9),value,text);refresh();sendAllDataToRemote();return result;}
        if(key.equals("release"))return CoreRelease.release(heart,player);
        if(key.equals("viewPage")){
            if(value!=HIT_EFFECT_PAGE&&(!Double.isFinite(value)||value<0||value>(heart.isSurvivalCore()?2:6)||value!=Math.rint(value)))return false;
            dev.sablelife.module.CoreEditorPosition.remember(heart,player.getUUID(),dev.sablelife.module.CoreEditorPosition.Section.SETTINGS,(int)value);return true;
        }
        if(key.equals("deathPreview")||key.equals("vanishPreview")){
            if(!heart.canToggleBreaking(player)||heart.isSurvivalCore()&&key.equals("vanishPreview"))return false;
            dev.sablelife.module.CoreEditorPosition.remember(heart,player.getUUID(),dev.sablelife.module.CoreEditorPosition.Section.SETTINGS,key.equals("vanishPreview")?3:2);
            player.closeContainer();return heart.editCore(player,key,value,text);
        }
        if(key.startsWith("soundEditor:")){
            String target=key.substring(12);boolean removal=target.startsWith("vanish");
            if(removal&&heart.isSurvivalCore())return false;
            return dev.sablelife.sound.SoundTimelineMenu.open(player,heart,null,removal?"death"+target.substring(6):target,removal?"vanish":"death");
        }
        boolean result = heart.editCore(player, key, value, text); refresh(); sendAllDataToRemote(); return result;
    }
    private void refreshBlueprintRevision() {
        var source = heart.relations().blueprintFaction();
        var local = heart.relations().faction();
        if (!Objects.equals(source, blueprintSource) || !Objects.equals(local, blueprintLocal)) {
            blueprintRevision++;
            blueprintSource = source;
            blueprintLocal = local;
        }
    }

    private CompoundTag blueprintSnapshot(ServerPlayer player, FactionRegistry registry) {
        refreshBlueprintRevision();
        var view = new CompoundTag();
        if (blueprintSource == null) return view;
        view.putInt("Revision", blueprintRevision);
        if (registry.contains(blueprintLocal)) view.putUUID("Bound", blueprintLocal);
        var suggested = registry.resolveBlueprintFaction(player, blueprintSource);
        if (suggested != null) view.putUUID("Suggested", suggested);
        view.putString("CreatedName", registry.suggestBlueprintFactionName(blueprintSource));
        var allies = new ListTag();
        for (var ally : blueprintSource.allies()) {
            var row = new CompoundTag();
            row.putUUID("Id", ally.id());
            var mapped = registry.resolveBlueprintFaction(player, blueprintSource.sourceWorld(), ally.id());
            if (mapped != null) {
                row.putUUID("Mapped", mapped);
                row.putBoolean("Allied", registry.allied(blueprintLocal, mapped));
            }
            allies.add(row);
        }
        view.put("Allies", allies);
        return view;
    }

    private boolean blueprintAction(ServerPlayer player, FactionRegistry registry, String key, double value, String text) {
        int separator = key.lastIndexOf(':');
        relationStatus = "bp_rejected";
        if (separator < 0) return false;
        refreshBlueprintRevision();
        if (blueprintSource == null || Integer.parseInt(key.substring(separator + 1)) != blueprintRevision) {
            relationStatus = "bp_stale";
            return false;
        }
        switch (key.substring(0, separator)) {
            case "bp_bind" -> {
                var target = UUID.fromString(text);
                var current = registry.detail(player, target);
                if (!current.hasUUID("Id")) return false;
                if (value != factionFlags(current.getBoolean("Private"), current.getBoolean("FriendlyFire"), current.getBoolean("AlliedFire"))) {
                    relationStatus = "bp_stale";
                    return false;
                }
                if (!registry.bindBlueprintFaction(player, blueprintSource, target)) return false;
                heart.relations().bindBlueprintFaction(target);
                relationStatus = "bp_bound";
            }
            case "bp_create" -> {
                if (registry.contains(blueprintLocal) || registry.resolveBlueprintFaction(player, blueprintSource) != null) {
                    relationStatus = "bp_use_mapping";
                    return false;
                }
                if (!(value >= 0 && value <= 7 && value == Math.rint(value))) return false;
                int flags = (int) value;
                var creation = registry.createBlueprintFaction(player, blueprintSource, text,
                        (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
                if (creation.id() == null) {
                    if (creation.error().startsWith("faction_")) relationStatus = creation.error();
                    return false;
                }
                heart.relations().bindBlueprintFaction(creation.id());
                relationStatus = "bp_bound";
            }
            case "bp_clear" -> {
                heart.relations().clearBlueprintFaction();
                relationStatus = "bp_cleared";
            }
            case "bp_ally" -> {
                var ids = text.split("\\|", -1);
                if (ids.length != 2 || blueprintLocal == null) return false;
                var sourceAlly = UUID.fromString(ids[0]);
                var expected = UUID.fromString(ids[1]);
                var mapped = registry.resolveBlueprintFaction(player, blueprintSource.sourceWorld(), sourceAlly);
                if (!expected.equals(mapped)) {
                    relationStatus = "bp_stale";
                    return false;
                }
                if (!registry.confirmBlueprintAlliance(player, blueprintSource, blueprintLocal, sourceAlly)) return false;
                relationStatus = registry.allied(blueprintLocal, mapped) ? "bp_ally_done" : "bp_ally_requested";
            }
            default -> { return false; }
        }
        heart.setChanged();
        return true;
    }

    public static int factionFlags(boolean privateFaction, boolean friendlyFire, boolean alliedFire) {
        return (privateFaction ? 1 : 0) | (friendlyFire ? 2 : 0) | (alliedFire ? 4 : 0);
    }

    private boolean relations(Player player,String key,double value,String text){
        if(!heart.canToggleBreaking(player)||!(player instanceof net.minecraft.server.level.ServerPlayer sp)){relationStatus="denied";return false;}
        var registry=FactionRegistry.get(sp.server);boolean result=false;
        if (key.startsWith("bp_")) {
            try { return blueprintAction(sp, registry, key, value, text); }
            catch (IllegalArgumentException ignored) { relationStatus = "bp_rejected"; return false; }
        }
        try{switch(key){
            case "excludeName"->{result=heart.relations().exclude(text);relationStatus=result?"player_added":"invalid_name";}
            case "removeName"->result=heart.relations().remove(text);
            case "excludeMob"->{result=heart.relations().excludeMob(text);relationStatus=result?"mob_added":"invalid_mob";}
            case "removeMob"->result=heart.relations().removeMob(text);
            case "prioritizeMob"->{result=heart.relations().prioritizeMob(text);relationStatus=result?"priority_added":"invalid_mob";}
            case "removePriorityMob"->result=heart.relations().removePriorityMob(text);
            case "create"->{relationStatus=registry.creationError(sp,text);if(relationStatus.isEmpty()){var id=registry.create(sp,text);if(id!=null){heart.relations().faction(id);result=true;}relationStatus=result?"updated":"denied";}}
            case "join"->{var id=java.util.UUID.fromString(text);if(registry.joinPlayer(sp,id)){heart.relations().faction(id);result=true;}}
            case "leave"->{heart.relations().faction(null);result=true;}
            case "friendlyFire"->result=registry.toggle(sp,java.util.UUID.fromString(text));
            case "delete"->result=registry.delete(sp,java.util.UUID.fromString(text));
            case "select"->{var id=java.util.UUID.fromString(text);if(registry.contains(id)){inspectedFaction=id;result=true;}}
            case "privacy"->result=registry.privacy(sp,java.util.UUID.fromString(text));
            case "alliedFire"->result=registry.alliedFire(sp,java.util.UUID.fromString(text));
            case "joinPlayer"->result=registry.joinPlayer(sp,java.util.UUID.fromString(text));
            case "accept"->result=registry.accept(sp,java.util.UUID.fromString(text));
            case "decline"->result=registry.decline(sp,java.util.UUID.fromString(text));
            case "leavePlayer"->result=registry.removeMember(sp,java.util.UUID.fromString(text),sp.getUUID());
            case "invite","removeMember","revoke","requestAlliance","acceptAlliance","endAlliance"->{
                var values=text.split("\\|",2);if(values.length!=2)break;var id=java.util.UUID.fromString(values[0]);
                result=switch(key){
                    case "invite"->registry.invite(sp,id,values[1]);
                    case "removeMember"->registry.removeMember(sp,id,java.util.UUID.fromString(values[1]));
                    case "revoke"->registry.revoke(sp,id,java.util.UUID.fromString(values[1]));
                    case "requestAlliance"->registry.requestAlliance(sp,id,java.util.UUID.fromString(values[1]));
                    case "acceptAlliance"->registry.acceptAlliance(sp,id,java.util.UUID.fromString(values[1]));
                    default->registry.endAlliance(sp,id,java.util.UUID.fromString(values[1]));
                };
            }
        }}catch(IllegalArgumentException ignored){}
        if(!key.equals("excludeName")&&!key.equals("removeName")&&!key.equals("excludeMob")&&!key.equals("prioritizeMob")
                &&!key.equals("create"))relationStatus=result?"updated":"denied";
        if(result)heart.setChanged();return result;
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (heart == null || !stillValid(player)) return false;
        switch (button) {
            case 0 -> heart.openDashboard(player);
            case 1 -> heart.openWireless(player);
            case 2 -> heart.openDeathLootMenu(player);
            case 3 -> player.closeContainer();
            case 4, 5, 6 -> {
                if(!heart.canToggleBreaking(player)||button==5&&heart.isSurvivalCore()) return false;
                player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,ignored)->switch(button){
                    case 5 -> dev.sablelife.module.EffectSelectionMenu.removal(id,inventory,heart);
                    case 6 -> dev.sablelife.module.EffectSelectionMenu.hit(id,inventory,heart);
                    default -> dev.sablelife.module.EffectSelectionMenu.death(id,inventory,heart);
                },net.minecraft.network.chat.Component.translatable("particles.sablelife.title")));
            }
            default -> { return false; }
        }
        return true;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {}
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
