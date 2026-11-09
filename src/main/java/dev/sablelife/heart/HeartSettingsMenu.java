package dev.sablelife.heart;

import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

/** Invisible snapshot slot, with all core mutations validated by the server. */
public final class HeartSettingsMenu extends AbstractContainerMenu {
    private final HeartBlockEntity heart;
    private final Player viewer;
    private final int initialPage;
    private String relationStatus="";
    private java.util.UUID inspectedFaction;
    private final SimpleContainer snapshot = new SimpleContainer(1);
    public HeartSettingsMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public HeartSettingsMenu(int id, Inventory inventory, HeartBlockEntity heart) {
        this(id, inventory, heart, 0);
    }
    public HeartSettingsMenu(int id, Inventory inventory, HeartBlockEntity heart, int page) {
        super(SableLife.SETTINGS_MENU.get(), id); this.heart = heart; viewer = inventory.player;
        initialPage = Math.clamp(page, 0, 6);
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
        if(viewer instanceof net.minecraft.server.level.ServerPlayer sp){tag.put("Relations",heart.relations().save());tag.put("Factions",FactionRegistry.get(sp.server).snapshot(sp));var players=new net.minecraft.nbt.ListTag();for(var p:sp.server.getPlayerList().getPlayers()){var r=new CompoundTag();r.putUUID("Id",p.getUUID());r.putString("Name",p.getGameProfile().getName());players.add(r);}tag.put("Players",players);}
        if(viewer instanceof net.minecraft.server.level.ServerPlayer sp&&inspectedFaction!=null)tag.put("FactionDetail",FactionRegistry.get(sp.server).detail(sp,inspectedFaction));
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
            && heart.getLevel().getBlockEntity(heart.getBlockPos()) == heart
            && SableBodyProbe.toWorld(player.level(), player.getEyePosition()).distanceToSqr(SableBodyProbe.toWorld(player.level(), heart.getBlockPos().getCenter())) <= 64;
    }
    public boolean action(Player player, String key, double value, String text) {
        if (heart == null || !stillValid(player)) return false;
        if(key.startsWith("relation:")){boolean result=relations(player,key.substring(9),text);refresh();sendAllDataToRemote();return result;}
        if(key.equals("release"))return CoreRelease.release(heart,player);
        if(key.equals("viewPage")){
            if(!Double.isFinite(value)||value<0||value>(heart.isSurvivalCore()?2:6)||value!=Math.rint(value))return false;
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
    private boolean relations(Player player,String key,String text){
        if(!heart.canToggleBreaking(player)||!(player instanceof net.minecraft.server.level.ServerPlayer sp)){relationStatus="denied";return false;}
        var registry=FactionRegistry.get(sp.server);boolean result=false;
        try{switch(key){
            case "excludeName"->{result=heart.relations().exclude(text);relationStatus=result?"player_added":"invalid_name";}
            case "removeName"->result=heart.relations().remove(text);
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
        if(!key.equals("excludeName")&&!key.equals("removeName")&&!key.equals("create"))relationStatus=result?"updated":"denied";
        if(result)heart.setChanged();return result;
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (heart == null || !stillValid(player)) return false;
        switch (button) {
            case 0 -> heart.openDashboard(player);
            case 1 -> heart.openWireless(player);
            case 2 -> heart.openDeathLootMenu(player);
            case 3 -> player.closeContainer();
            case 4, 5 -> {
                if(!heart.canToggleBreaking(player)||button==5&&heart.isSurvivalCore()) return false;
                player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,ignored)->button==5?dev.sablelife.module.EffectSelectionMenu.removal(id,inventory,heart):dev.sablelife.module.EffectSelectionMenu.death(id,inventory,heart),net.minecraft.network.chat.Component.translatable("particles.sablelife.title")));
            }
            default -> { return false; }
        }
        return true;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {}
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
