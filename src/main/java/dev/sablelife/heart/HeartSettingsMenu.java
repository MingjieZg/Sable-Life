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
    private final SimpleContainer snapshot = new SimpleContainer(1);
    public HeartSettingsMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public HeartSettingsMenu(int id, Inventory inventory, HeartBlockEntity heart) {
        this(id, inventory, heart, 0);
    }
    public HeartSettingsMenu(int id, Inventory inventory, HeartBlockEntity heart, int page) {
        super(SableLife.SETTINGS_MENU.get(), id); this.heart = heart; viewer = inventory.player;
        initialPage = Math.clamp(page, 0, 4);
        addSlot(new Slot(snapshot, 0, -10000, -10000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean isActive() { return false; }
        }); refresh();
    }
    public CompoundTag snapshot() { return snapshot.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    private void refresh() {
        if (heart == null) return;
        var tag = new CompoundTag(); tag.putBoolean("Ready", true);
        tag.putInt("InitialPage", initialPage);
        tag.putBoolean("ReleaseAllowed",viewer.isCreative()&&!heart.isControlCore());
        tag.putBoolean("Editable", heart.canEdit(viewer)); tag.putBoolean("LiveControls", heart.canToggleBreaking(viewer));
        tag.putString("name", heart.creatureName()); tag.putFloat("health", heart.health().maximum());
        tag.putDouble("delay", heart.health().deathDelay() / 20.); tag.putInt("xp", heart.displayedExperience());tag.putBoolean("xpLevels",heart.deathExperienceLevels());tag.putInt("xpPoints",heart.deathExperience());
        tag.putBoolean("explosion", heart.allowExplosion()); tag.putBoolean("breaking", heart.allowBreaking()); tag.putBoolean("bossbar", heart.showBossBar());
        tag.putBoolean("killHeal",heart.killHeal());tag.putBoolean("survivalInteraction",heart.allowSurvivalInteraction());tag.putBoolean("lifeAttacks",heart.allowLifeAttacks());tag.putInt("bossbarColor",heart.bossBarColor().ordinal());
        tag.putInt("voidDeathHeight",heart.voidDeathHeight());tag.putBoolean("VoidDeathDefault",heart.defaultVoidDeathHeight());
        tag.putBoolean("forceChunkLoading",heart.forceChunkLoading());tag.putBoolean("ChunkLoadingEditable",heart.canEditChunkLoading(viewer));
        tag.putString("ChunkLoadingStatus",dev.sablelife.ownership.CoreChunkLoading.status(heart));tag.putInt("ChunkLoadingCount",dev.sablelife.ownership.CoreChunkLoading.count(heart));
        tag.putDouble("regenPercent",heart.regenPercentPerSecond());tag.putDouble("regen",heart.regenPerSecond()); tag.putDouble("regenDelay",heart.regenDelay()/20.);
        tag.putDouble("knockbackResistance",heart.knockbackResistance());
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
        return heart == null || player.isCreative() && !heart.isRemoved() && player.level() == heart.getLevel()
            && heart.getLevel().getBlockEntity(heart.getBlockPos()) == heart
            && SableBodyProbe.toWorld(player.level(), player.getEyePosition()).distanceToSqr(SableBodyProbe.toWorld(player.level(), heart.getBlockPos().getCenter())) <= 64;
    }
    public boolean action(Player player, String key, double value, String text) {
        if (heart == null || !stillValid(player)) return false;
        if(key.equals("release"))return CoreRelease.release(heart,player);
        if(key.equals("viewPage")){
            if(!Double.isFinite(value)||value<0||value>4||value!=Math.rint(value))return false;
            dev.sablelife.module.CoreEditorPosition.remember(heart,player.getUUID(),dev.sablelife.module.CoreEditorPosition.Section.SETTINGS,(int)value);return true;
        }
        if(key.equals("deathPreview")||key.equals("vanishPreview")){
            if(!heart.canToggleBreaking(player))return false;
            dev.sablelife.module.CoreEditorPosition.remember(heart,player.getUUID(),dev.sablelife.module.CoreEditorPosition.Section.SETTINGS,key.equals("vanishPreview")?3:2);
            player.closeContainer();return heart.editCore(player,key,value,text);
        }
        if(key.startsWith("soundEditor:")){
            String target=key.substring(12);boolean removal=target.startsWith("vanish");
            return dev.sablelife.sound.SoundTimelineMenu.open(player,heart,null,removal?"death"+target.substring(6):target,removal?"vanish":"death");
        }
        boolean result = heart.editCore(player, key, value, text); refresh(); sendAllDataToRemote(); return result;
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (heart == null || !stillValid(player)) return false;
        switch (button) {
            case 0 -> heart.openDashboard(player);
            case 1 -> heart.openWireless(player);
            case 2 -> heart.openDeathLootMenu(player);
            case 3 -> player.closeContainer();
            case 4, 5 -> {
                if(!heart.canToggleBreaking(player)) return false;
                player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,ignored)->button==5?dev.sablelife.module.EffectSelectionMenu.removal(id,inventory,heart):dev.sablelife.module.EffectSelectionMenu.death(id,inventory,heart),net.minecraft.network.chat.Component.translatable("particles.sablelife.title")));
            }
            default -> { return false; }
        }
        return true;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {}
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
