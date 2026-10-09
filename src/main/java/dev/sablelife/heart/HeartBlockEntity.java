package dev.sablelife.heart;

import com.mojang.logging.LogUtils;
import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.ownership.BodyOwnership;
import dev.sablelife.ownership.LifeLifecycle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;
import java.util.HashSet;

public final class HeartBlockEntity extends com.simibubi.create.content.decoration.copycat.CopycatBlockEntity {
    public static final float DEFAULT_MAX_HEALTH = 100;
    public static final int DEFAULT_DEATH_DELAY = 60;
    private HeartHealth health = new HeartHealth(DEFAULT_MAX_HEALTH, DEFAULT_DEATH_DELAY);
    private UUID creatureId = UUID.randomUUID();
    private UUID boundBody;
    private int pulseSignal;
    private boolean ownershipCaptured;
    public static final int MIN_VOID_DEATH_HEIGHT=-2048,MAX_VOID_DEATH_HEIGHT=2048;
    private Integer customVoidDeathHeight;
    public int voidDeathHeight(){return customVoidDeathHeight!=null?customVoidDeathHeight:level!=null?level.getMinBuildHeight():-64;}
    public boolean defaultVoidDeathHeight(){return customVoidDeathHeight==null;}
    private void loadVoidDeathHeight(CompoundTag tag){double value=tag.getDouble("VoidDeathHeight");customVoidDeathHeight=tag.contains("VoidDeathHeight",99)&&Double.isFinite(value)&&value==Math.rint(value)?(int)Math.clamp(value,MIN_VOID_DEATH_HEIGHT,MAX_VOID_DEATH_HEIGHT):null;}
    public boolean checkVoidDeath(){return level instanceof ServerLevel&&!isControlCore()&&health.state()==HeartState.ALIVE&&SableBodyProbe.toWorld(level,worldPosition.getCenter()).y<voidDeathHeight()&&forceDeath();}
    private boolean forceChunkLoading;
    public boolean forceChunkLoading(){return forceChunkLoading;}
    public boolean canEditChunkLoading(Player player){return !isControlCore()&&canAccessEditor(player)&&level instanceof ServerLevel&&level.getBlockEntity(worldPosition)==this;}
    private boolean allowBreaking;
    private boolean allowExplosion;
    private boolean allowLifeAttacks = true;
    private final AdaptiveDefense adaptiveDefense=new AdaptiveDefense();
    public AdaptiveDefense adaptiveDefense(){return adaptiveDefense;}
    private CompoundTag defenseConfiguration(){var tag=new CompoundTag();tag.putBoolean("Enabled",adaptiveDefense.enabled());tag.putDouble("Threshold",adaptiveDefense.threshold());tag.putDouble("Maximum",adaptiveDefense.maximum());tag.putInt("Window",adaptiveDefense.window());return tag;}
    private void loadDefense(CompoundTag parent){var tag=parent.getCompound("AdaptiveDefense");double threshold=tag.contains("Threshold")?tag.getDouble("Threshold"):100,maximum=tag.contains("Maximum")?tag.getDouble("Maximum"):80;int window=tag.contains("Window")?tag.getInt("Window"):20;
        if(!adaptiveDefense.configure(tag.getBoolean("Enabled"),threshold,maximum,window))adaptiveDefense.configure(false,100,80,20);adaptiveDefense.reset();}
    private float regenPerSecond;
    private double regenPercentPerSecond;
    public double regenPercentPerSecond(){return regenPercentPerSecond;}
    private int regenDelay = 100, regenWait, regenProgress;
    public float regenPerSecond() { return regenPerSecond; }
    public int regenDelay() { return regenDelay; }
    private void loadRegen(CompoundTag tag) {
        double percent=tag.getDouble("RegenPercentPerSecond");regenPercentPerSecond=Double.isFinite(percent)?Math.clamp(percent,0,100):0;
        float rate=tag.getFloat("RegenPerSecond"); regenPerSecond=Float.isFinite(rate) ? Math.clamp(rate,0,1000000) : 0;
        regenDelay=tag.contains("RegenDelay") ? Math.clamp(tag.getInt("RegenDelay"),0,12000) : 100;
        regenWait=Math.clamp(tag.getInt("RegenWait"),0,12000); regenProgress=Math.clamp(tag.getInt("RegenProgress"),0,19);
    }
    public void tickRegeneration() {
        if (health.state()!=HeartState.ALIVE || regenPerSecond<=0&&regenPercentPerSecond<=0) { regenProgress=0; return; }
        if (regenWait>0) { regenWait--; setChanged(); return; }
        setChanged();
        if (++regenProgress>=20) { regenProgress=0; if(health.heal((float)(regenPerSecond+health.maximum()*regenPercentPerSecond/100))) changed(); }
    }
    private final int[] deathChances = new int[27], deathWeights = new int[27];
    private boolean deathWeighted;
    private int deathRolls = 1;
    { java.util.Arrays.fill(deathChances,100); java.util.Arrays.fill(deathWeights,100); }
    private boolean showBossBar;
    private String creatureName = "";
    private ServerBossEvent bossBar;
    private final HeartWireless wireless = new HeartWireless(this);
    private final dev.sablelife.module.HeartModules modules = new dev.sablelife.module.HeartModules();
    public dev.sablelife.module.HeartModules modules() { return modules; }
    private long combatMusicHits;
    public long combatMusicHits() { return combatMusicHits; }
    public boolean isControlCore(){return getBlockState().getBlock() instanceof RedstoneCoreBlock;}
    public boolean canAccessEditor(Player player){return (player.isCreative()||isControlCore()&&!player.isSpectator())&&!isRemoved()&&player.level()==level;}
    private long checkedCoreTick=Long.MIN_VALUE;private boolean uniqueCore;
    public boolean operational(){
        if(!(level instanceof ServerLevel)||isRemoved())return false;
        if(checkedCoreTick!=level.getGameTime()){checkedCoreTick=level.getGameTime();var graph=SableBodyProbe.inspect(level,worldPosition);uniqueCore=graph.otherHearts()==0&&(!isControlCore()||graph.assembled());}
        return uniqueCore&&(isControlCore()||health.state()==HeartState.ALIVE);
    }
    public void openDashboard(Player player) {
        if (!(level instanceof ServerLevel) || !canAccessEditor(player)) return;
        dev.sablelife.module.CoreEditorPosition.remember(this,player.getUUID(),dev.sablelife.module.CoreEditorPosition.Section.MODULES,0);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, ignored) ->
                new dev.sablelife.module.HeartDashboardMenu(id, inventory, this), Component.translatable("dashboard.sablelife.title")));
    }
    public HeartWireless wireless() { return wireless; }
    public void openWireless(Player player) {
        if (!(level instanceof ServerLevel) || !canAccessEditor(player)) return;
        dev.sablelife.module.CoreEditorPosition.remember(this,player.getUUID(),dev.sablelife.module.CoreEditorPosition.Section.WIRELESS,0);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, ignored) -> new HeartWirelessMenu(id, inventory, this),
                Component.translatable("menu.sablelife.wireless")));
    }
    private ResourceLocation deathLoot = ResourceLocation.withDefaultNamespace("empty");
    private int deathExperience;
    private boolean deathExperienceLevels;
    private final DeathPresentation deathPresentation = new DeathPresentation();
    public DeathPresentation deathPresentation() { return deathPresentation; }
    private final DeathPresentation removalPresentation = new DeathPresentation(true);
    public DeathPresentation removalPresentation() { return removalPresentation; }
    private boolean deathTemplateMode;
    private final net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> deathTemplates =
            net.minecraft.core.NonNullList.withSize(27, net.minecraft.world.item.ItemStack.EMPTY);
    public boolean allowBreaking() { return allowBreaking; }
    public boolean allowExplosion() { return allowExplosion; }
    public boolean allowLifeAttacks() { return allowLifeAttacks; }
    public boolean acceptsLifeAttack(UUID attacker) { return attacker == null || !attacker.equals(creatureId) && allowLifeAttacks; }
    public int chance(int slot) { return deathChances[slot]; }
    public int weight(int slot) { return deathWeights[slot]; }
    public int[] chances() { return deathChances.clone(); }
    public int[] weights() { return deathWeights.clone(); }
    public boolean weighted() { return deathWeighted; }
    public int rolls() { return deathRolls; }
    public boolean canEditTemplates(Player player) { return canEdit(player); }
    public boolean setLootTemplate(Player player,int slot,net.minecraft.world.item.ItemStack sample) { return setDeathTemplate(player,slot,sample); }
    public java.util.List<net.minecraft.world.item.ItemStack> lootTemplates() { return deathTemplates(); }
    public boolean adjustLootRule(Player player,int slot,int direction,boolean large) {
        if(!canEdit(player) || Math.abs(direction)!=1) return false;
        if(slot>=0 && slot<27 && !deathTemplates.get(slot).isEmpty()) {
            int[] rules=deathWeighted ? deathWeights : deathChances;
            rules[slot]=Math.clamp(rules[slot]+direction*(large?25:5),0,100);
        } else if(slot==31) deathWeighted=!deathWeighted;
        else if(slot==32) deathRolls=Math.clamp(deathRolls+direction*(large?5:1),1,27);
        else return false;
        deathTemplateMode=true; changed(); return true;
    }
    private void loadDeathRules(CompoundTag tag) {
        deathWeighted=tag.getBoolean("DeathWeighted"); deathRolls=tag.contains("DeathRolls")?Math.clamp(tag.getInt("DeathRolls"),1,27):1;
        var chances=tag.getIntArray("DeathChances"); var weights=tag.getIntArray("DeathWeights");
        for(int i=0;i<27;i++) { deathChances[i]=i<chances.length?Math.clamp(chances[i],0,100):100; deathWeights[i]=i<weights.length?Math.clamp(weights[i],0,100):100; }
    }
    public boolean showBossBar() { return showBossBar; }
    public String creatureName() { return creatureName; }
    public Component displayName() { return creatureName.isBlank() ? Component.translatable("entity.sablelife.creature") : CreatureNames.formatted(creatureName); }
    public ResourceLocation deathLoot() { return deathLoot; }
    public int deathExperience() { return deathExperience; }
    public boolean deathExperienceLevels(){return deathExperienceLevels;}
    public int displayedExperience(){return deathExperienceLevels?ExperienceUnits.levels(deathExperience):deathExperience;}
    public boolean deathTemplateMode() { return deathTemplateMode; }
    public java.util.List<net.minecraft.world.item.ItemStack> deathTemplates() {
        return deathTemplates.stream().map(net.minecraft.world.item.ItemStack::copy).toList();
    }
    public boolean canEdit(Player player) {
        return canAccessEditor(player) && level instanceof ServerLevel && level.getBlockEntity(worldPosition)==this && health.state()==HeartState.DORMANT;
    }
    /** Portable authoring configuration. Creature identity, ownership and runtime are never copied. */
    public CompoundTag copyConfiguration() {
        var saved = saveWithoutMetadata(level.registryAccess()); var result = new CompoundTag();
        result.putInt("Version", 1);
        for (String key : java.util.List.of("MaxHealth", "DeathDelay", "RegenPerSecond", "RegenPercentPerSecond", "RegenDelay", "AdaptiveDefense", "VoidDeathHeight", "ForceChunkLoading", "AllowBreaking", "AllowExplosion", "AllowLifeAttacks", "DeathChances", "DeathWeights", "DeathWeighted", "DeathRolls", "ShowBossBar", "CreatureName", "DeathLoot", "DeathExperience", "DeathExperienceLevels", "DeathTemplateMode", "DeathTemplates", "Wireless"))
            if (saved.contains(key)) result.put(key, saved.get(key).copy());
        result.getCompound("Wireless").putLong("Elapsed", 0);
        result.put("DeathPresentation", deathPresentation.save());
        result.put("RemovalPresentation", removalPresentation.save());
        var list = new net.minecraft.nbt.ListTag();
        for (var module : modules.all()) if (module.known()) {
            var tag = module.save(); tag.putUUID("CopySourceId", module.id());
            for (String key : java.util.List.of("Id", "Anchors", "ParticleAreaAnchors", "ParticlePointAnchors", "ArenaCenter", "ImportSource", "RewardOwner", "Invalidated", "Detached")) tag.remove(key);
            list.add(tag);
        }
        result.put("Modules", list); return result;
    }
    public boolean pasteConfiguration(Player player, CompoundTag data) {
        if (!canEdit(player) || data.getInt("Version") != 1) return false;
        if (!dev.sablelife.module.AuthoringLimits.compound(data, 1024 * 1024)
                || !player.canUseGameMasterBlocks() && dev.sablelife.module.AuthoringLimits.containsCommands(data)) return false;
        float maximum = data.getFloat("MaxHealth");
        if (!Float.isFinite(maximum) || maximum < 1 || maximum > HeartHealth.MAX_CONFIGURED_HEALTH) return false;
        // Decode first, then replace the target's authoring settings in one server operation.
        var copiedModules = new net.minecraft.nbt.ListTag(); var copiedIds = new java.util.HashMap<java.util.UUID, java.util.UUID>(); var incoming = data.getList("Modules", 10);
        for (int i = 0; i < Math.min(dev.sablelife.module.HeartModules.LIMIT, incoming.size()); i++) {
            var tag = incoming.getCompound(i).copy();
            if (player.canUseGameMasterBlocks()) tag.remove("CommandApprovalRequired");
            for (String key : java.util.List.of("Anchors", "ParticleAreaAnchors", "ParticlePointAnchors", "ArenaCenter", "ImportSource", "RewardOwner", "Invalidated", "Detached")) tag.remove(key);
            var newId = java.util.UUID.randomUUID();
            if (tag.hasUUID("CopySourceId")) copiedIds.put(tag.getUUID("CopySourceId"), newId);
            tag.remove("CopySourceId"); tag.putUUID("Id", newId);
            if (dev.sablelife.module.VirtualModule.load(tag).known()) copiedModules.add(tag);
        }
        for (int i = 0; i < copiedModules.size(); i++) {
            var copied = dev.sablelife.module.VirtualModule.load(copiedModules.getCompound(i));
            if (copied.type().equals("move_set")) { copied.moveSet().remap(copiedIds); copiedModules.set(i, copied.save()); }
        }
        var moduleData = new CompoundTag(); moduleData.put("Modules", copiedModules); moduleData.putInt("Revision", modules.revision() + 1);
        var templates = net.minecraft.core.NonNullList.withSize(27, net.minecraft.world.item.ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(data.getCompound("DeathTemplates"), templates, level.registryAccess());
        String name = data.getString("CreatureName");
        if (name.codePointCount(0, name.length()) > 64 || name.codePoints().anyMatch(Character::isISOControl)) name = "";
        var loot = ResourceLocation.tryParse(data.getString("DeathLoot"));
        health = new HeartHealth(maximum, Math.clamp(data.getInt("DeathDelay"), 0, HeartHealth.MAX_DEATH_DELAY));
        combatMusicHits = 0;
        loadRegen(data); loadDefense(data); regenWait=regenDelay; regenProgress=0;
        allowLifeAttacks = !data.contains("AllowLifeAttacks") || data.getBoolean("AllowLifeAttacks"); loadDeathRules(data);
        creatureName = name; loadVoidDeathHeight(data); forceChunkLoading=data.getBoolean("ForceChunkLoading"); allowBreaking = data.getBoolean("AllowBreaking"); allowExplosion = data.getBoolean("AllowExplosion"); showBossBar = data.getBoolean("ShowBossBar");
        deathLoot = loot == null ? ResourceLocation.withDefaultNamespace("empty") : loot;
        deathExperience = Math.clamp(data.getInt("DeathExperience"), 0, ExperienceUnits.MAX_POINTS);deathExperienceLevels=data.getBoolean("DeathExperienceLevels"); deathTemplateMode = data.getBoolean("DeathTemplateMode");
        deathPresentation.load(data.getCompound("DeathPresentation"));
        removalPresentation.load(data.getCompound("RemovalPresentation"));
        for (int i = 0; i < 27; i++) {
            var stack = templates.get(i); if (!stack.isEmpty()) stack.setCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
            deathTemplates.set(i, stack);
        }
        var wirelessData = data.getCompound("Wireless").copy(); wirelessData.putLong("Elapsed", 0);
        wireless.load(wirelessData, level.registryAccess()); modules.load(moduleData); if(isControlCore()){RedstoneCorePolicy.sanitize(this);health=new HeartHealth(DEFAULT_MAX_HEALTH,DEFAULT_DEATH_DELAY);boundBody=null;ownershipCaptured=false;}wireless.finishMigration(); changed(); return true;
    }
    public boolean canToggleBreaking(Player player) {
        return player.isCreative() && !isRemoved() && level instanceof ServerLevel && player.level() == level
                && level.getBlockEntity(worldPosition) == this
                && (health.state() == HeartState.DORMANT || health.state() == HeartState.ALIVE);
    }
    public boolean editCore(Player player, String key, double value, String text) {
        if(isControlCore())return false;
        if(key.equals("voidDeathHeight")||key.equals("voidDeathDefault")){
            if(!canToggleBreaking(player)||!Double.isFinite(value))return false;
            if(key.equals("voidDeathHeight")){if(value<MIN_VOID_DEATH_HEIGHT||value>MAX_VOID_DEATH_HEIGHT||value!=Math.rint(value))return false;customVoidDeathHeight=(int)value;}
            else customVoidDeathHeight=null;
            changed();return true;
        }
        if(key.equals("forceChunkLoading")){
            if(!canEditChunkLoading(player)||(value!=0&&value!=1))return false;
            forceChunkLoading=value==1;LifeLifecycle.get(((ServerLevel)level).getServer()).deathLoading(creatureId,forceChunkLoading);if(!forceChunkLoading)dev.sablelife.ownership.CoreChunkLoading.release(this);else dev.sablelife.ownership.CoreChunkLoading.observe(this);changed();return true;
        }
        if (key.equals("name")) {
            if (!canToggleBreaking(player)) return false;
            String name = text.trim();
            if (name.codePointCount(0, name.length()) > 64 || name.codePoints().anyMatch(Character::isISOControl)) return false;
            creatureName = name;
        } else if(key.startsWith("defense")) {
            if(!canToggleBreaking(player)||!Double.isFinite(value))return false;
            boolean enabled=adaptiveDefense.enabled();double threshold=adaptiveDefense.threshold(),maximum=adaptiveDefense.maximum();int window=adaptiveDefense.window();
            switch(key){case "defenseEnabled"->{if(value!=0&&value!=1)return false;enabled=value!=0;}case "defenseThreshold"->threshold=value;case "defenseMaximum"->maximum=value;case "defenseWindow"->{if(value<.1||value>10)return false;window=(int)Math.round(value*20);}default->{return false;}}
            if(!adaptiveDefense.configure(enabled,threshold,maximum,window))return false;
        } else if (key.equals("regen") || key.equals("regenPercent") || key.equals("regenDelay")) {
            if (!canToggleBreaking(player) || !Double.isFinite(value) || value<0 || value>(key.equals("regen") ? 1000000 : key.equals("regenPercent")?100:600)) return false;
            if(key.equals("regen")) regenPerSecond=(float)value; else if(key.equals("regenPercent"))regenPercentPerSecond=value;else regenDelay=(int)Math.round(value*20);
            regenWait=regenDelay; regenProgress=0;
        } else if(key.equals("deathRestore")||key.equals("vanishRestore")){
            if(!canToggleBreaking(player))return false;
            (key.equals("vanishRestore")?removalPresentation:deathPresentation).restoreDefaults();
        } else if (key.equals("vanishPreview")) {
            if (!canToggleBreaking(player)) return false;
            removalPresentation.play(this);
            return true;
        } else if (key.startsWith("vanish")) {
            if (!canToggleBreaking(player) || !removalPresentation.edit("death"+key.substring(6),value,text)) return false;
        } else if (key.equals("deathPreview")) {
            if (!canToggleBreaking(player)) return false;
            deathPresentation.play(this);
            return true;
        } else if (key.startsWith("death")) {
            if (!canToggleBreaking(player) || !deathPresentation.edit(key, value, text)) return false;
        } else if (key.equals("breaking") || key.equals("explosion") || key.equals("bossbar") || key.equals("lifeAttacks")) {
            if (!canToggleBreaking(player)) return false;
            if (key.equals("breaking")) {
                allowBreaking = value != 0;
                if (health.state() == HeartState.ALIVE) {
                    LifeLifecycle.get(((ServerLevel) level).getServer()).setBreakable(creatureId, allowBreaking);
                    LifeLifecycle.get(((ServerLevel) level).getServer()).setExplodable(creatureId, allowExplosion);
                }
            } else if (key.equals("explosion")) {
                allowExplosion = value != 0;
                if (health.state() == HeartState.ALIVE) LifeLifecycle.get(((ServerLevel) level).getServer()).setExplodable(creatureId, allowExplosion);
            } else if (key.equals("lifeAttacks")) { allowLifeAttacks = value != 0; } else { showBossBar = value != 0; if (!showBossBar) clearBossBar(); }
        } else {
            if (!canEdit(player) || !Double.isFinite(value)) return false;
            switch (key) {
                case "health" -> { if (value < 1 || value > HeartHealth.MAX_CONFIGURED_HEALTH) return false; health = new HeartHealth((float) value, health.deathDelay()); }
                case "delay" -> { if (value < 0 || value > 60) return false; health = new HeartHealth(health.maximum(), (int) Math.round(value * 20)); }
                case "xp" -> { if (value < 0 || value > (deathExperienceLevels?ExperienceUnits.MAX_LEVELS:ExperienceUnits.MAX_POINTS)||deathExperienceLevels&&value!=Math.rint(value)) return false; deathExperience = deathExperienceLevels?ExperienceUnits.points((int)value):(int)Math.round(value); }
                case "xpLevels" -> {if(value!=0&&value!=1)return false;deathExperienceLevels=value==1;}
                default -> { return false; }
            }
        }
        changed(); return true;
    }
    public boolean setCreatureName(Player player, net.minecraft.world.item.ItemStack sample) {
        if (!canToggleBreaking(player) || !sample.is(net.minecraft.world.item.Items.NAME_TAG)
                || !sample.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) return false;
        String name = sample.getHoverName().getString().trim();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 64 || name.codePoints().anyMatch(Character::isISOControl)) return false;
        creatureName = name;
        changed();
        return true;
    }
    public boolean clearCreatureName(Player player) {
        if (!canToggleBreaking(player)) return false;
        creatureName = "";
        changed();
        return true;
    }
    public void openSettings(Player player) {
        openSettings(player,dev.sablelife.module.CoreEditorPosition.get(this,player.getUUID()).page());
    }
    public void openLastEditor(Player player){
        var position=dev.sablelife.module.CoreEditorPosition.get(this,player.getUUID());
        switch(position.section()){case SETTINGS->openSettings(player,position.page());case WIRELESS->openWireless(player);case LOOT->openDeathLootMenu(player);default->openDashboard(player);}
    }
    public void openSettings(Player player, int page) {
        if(isControlCore()){openDashboard(player);return;}
        if (!(level instanceof ServerLevel) || !canAccessEditor(player)) return;
        dev.sablelife.module.CoreEditorPosition.remember(this,player.getUUID(),dev.sablelife.module.CoreEditorPosition.Section.SETTINGS,page);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, ignored) -> new HeartSettingsMenu(id, inventory, this, page),
                Component.translatable(canEdit(player) ? "menu.sablelife.heart"
                        : canToggleBreaking(player) ? "menu.sablelife.heart_live" : "menu.sablelife.heart_locked")));
    }
    public void openDeathLootMenu(Player player) {
        if(isControlCore()){openDashboard(player);return;}
        if (!(level instanceof ServerLevel) || !canAccessEditor(player)) return;
        dev.sablelife.module.CoreEditorPosition.remember(this,player.getUUID(),dev.sablelife.module.CoreEditorPosition.Section.LOOT,0);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, ignored) -> new HeartLootMenu(id, inventory, this),
                Component.translatable(canEdit(player) ? "menu.sablelife.heart_loot" : "menu.sablelife.heart_loot_locked")));
    }
    public boolean adjust(Player player, int slot, int direction, boolean large) {
        if (slot == 12 && canToggleBreaking(player) && Math.abs(direction) == 1) {
            allowBreaking = !allowBreaking;
            if (health.state() == HeartState.ALIVE) {
                LifeLifecycle.get(((ServerLevel) level).getServer()).setBreakable(creatureId, allowBreaking);
                LifeLifecycle.get(((ServerLevel) level).getServer()).setExplodable(creatureId, allowExplosion);
            }
            changed(); return true;
        }
        if (slot == 15 && canToggleBreaking(player) && Math.abs(direction) == 1) {
            showBossBar = !showBossBar;
            if (!showBossBar) clearBossBar();
            changed(); return true;
        }
        if (!canEdit(player) || Math.abs(direction) != 1) return false;
        switch (slot) {
            case 10 -> health = new HeartHealth(Math.clamp(health.maximum() + direction * (large ? 1000 : 10),
                    1, HeartHealth.MAX_CONFIGURED_HEALTH), health.deathDelay());
            case 11 -> health = new HeartHealth(health.maximum(), Math.clamp(health.deathDelay()
                    + direction * (large ? 100 : 20), 0, HeartHealth.MAX_DEATH_DELAY));
            case 14 -> deathExperience = Math.clamp(deathExperience + direction * (large ? 10 : 1), 0, ExperienceUnits.MAX_POINTS);
            default -> { return false; }
        }
        changed(); return true;
    }
    public boolean setDeathTemplate(Player player, int slot, net.minecraft.world.item.ItemStack sample) {
        if (!canEdit(player) || slot < 0 || slot >= 27) return false;
        var copy = sample.copy();
        if (!copy.isEmpty()) copy.setCount(Math.min(copy.getCount(), copy.getMaxStackSize()));
        deathTemplates.set(slot, copy); if(copy.isEmpty()) { deathChances[slot]=100; deathWeights[slot]=100; } deathTemplateMode = true; changed(); return true;
    }
    public boolean configureOptions(boolean breaking, ResourceLocation loot, int experience) {
        if (!(level instanceof ServerLevel server) || health.state() != HeartState.DORMANT || experience < 0 || experience > ExperienceUnits.MAX_POINTS) return false;
        allowBreaking = breaking; deathLoot = loot; deathExperience = experience;
        deathTemplateMode = false;
        changed();
        return true;
    }

    public HeartBlockEntity(BlockPos position, BlockState state) {
        super(SableLife.HEART_ENTITY.get(), position, state);
    }

    /** Core NBT can contain level-2 command programs; creative mode alone is not authorization. */
    @Override public boolean onlyOpCanSetNbt() { return true; }

    @Override public void onLoad() { super.onLoad();dev.sablelife.ownership.LoadedCoreIndex.observe(this); }
    @Override public void setLevel(Level next) {
        if(level!=null&&level!=next){dev.sablelife.ownership.LoadedCoreIndex.remove(this);dev.sablelife.compat.sable.SableBodyProbe.invalidate(level);}
        super.setLevel(next);checkedCoreTick=Long.MIN_VALUE;
        dev.sablelife.ownership.LoadedCoreIndex.observe(this);
    }

    public HeartHealth health() { return health; }
    public int pulseSignal() { return pulseSignal; }
    public UUID creatureId() { return creatureId; }

    public void tryActivate(Player player) {
        if(isControlCore())return;
        if (level == null || level.isClientSide) return;
        if (health.state() == HeartState.DYING || health.state() == HeartState.DEAD) {
            player.displayClientMessage(Component.translatable("message.sablelife.dead"), false);
            return;
        }
        if (health.state() == HeartState.ALIVE) {
            player.displayClientMessage(Component.translatable("message.sablelife.already_active", health.current(), health.maximum()), false);
            return;
        }
        var body = SableBodyProbe.inspect(level, worldPosition);
        if (!body.assembled()) {
            player.displayClientMessage(Component.translatable("message.sablelife.requires_structure"), false);
            return;
        }
        if (body.otherHearts() > 0) {
            player.displayClientMessage(Component.translatable("message.sablelife.multiple_hearts"), false);
            return;
        }
        boundBody = body.rootId();
        health.activate();adaptiveDefense.reset();
        dev.sablelife.ownership.LoadedCoreIndex.geometryChanged((ServerLevel)level);
        wireless.start();
        BodyOwnership.get((ServerLevel) level).capture(this);
        LifeLifecycle.get(((ServerLevel) level).getServer()).setBreakable(creatureId, allowBreaking);
        LifeLifecycle.get(((ServerLevel) level).getServer()).setExplodable(creatureId, allowExplosion);
        ownershipCaptured = true;
        level.setBlock(worldPosition, getBlockState().setValue(HeartBlock.ACTIVE, true), 3);
        changed();
        player.displayClientMessage(Component.translatable("message.sablelife.activated"), false);
    }

    /** Hit-aware entry point for combat; administrative damage(float) intentionally has no body location. */
    public boolean damageAt(BlockPos hit, float amount) {
        if (!(level instanceof ServerLevel) || health.state() != HeartState.ALIVE) return false;
        return damage(dev.sablelife.module.ModuleArmor.damage(this, hit, amount));
    }
    public boolean damage(float amount) {
        if(level==null||level.isClientSide||health.state()!=HeartState.ALIVE||!Float.isFinite(amount)||amount<=0)return false;
        float reduced=adaptiveDefense.apply(amount,level.getGameTime());
        // Preserve positive health loss when adaptive mitigation would round a valid hit to zero at high HP.
        if(adaptiveDefense.enabled())reduced=Math.min(amount,Math.max(reduced,Math.ulp(health.current())));
        float before=health.current();boolean applied=applyDamage(reduced);
        if(applied)modules.damageAccumulation.record(this,Math.max(0,(double)before-health.current()));
        return applied;
    }
    /** Physical destruction of the core must end life regardless of combat defenses. */
    public boolean forceDeath(){return applyDamage(health.maximum());}
    private boolean applyDamage(float amount) {
        if (level == null || level.isClientSide || !health.hurt(amount)) return false;
        regenWait=regenDelay; regenProgress=0;
        combatMusicHits = combatMusicHits == Long.MAX_VALUE ? 1 : combatMusicHits + 1;
        modules.hurtTriggers.hurt(this);
        modules.events().hurt(this);
        if (health.state() != HeartState.ALIVE) {
            adaptiveDefense.reset();modules.clearRuntime();
            wireless.stop();
            pulseSignal = 0;
            LifeLifecycle.get(((ServerLevel) level).getServer()).schedule(this);
        }
        changed();
        return true;
    }

    public boolean configure(float maximum, int deathDelay) {
        if (level == null || level.isClientSide || health.state() != HeartState.DORMANT) return false;
        health = new HeartHealth(maximum, deathDelay);
        changed();
        return true;
    }

    /** Releases life/runtime state while retaining the author's core and module configuration. */
    public boolean resetForCreative(Player player) {
        if (level == null || level.isClientSide || player.level() != level || !player.isCreative() || isRemoved()) return false;
        dev.sablelife.ownership.CoreChunkLoading.release(this);
        wireless.stop();
        modules.clearRuntime();
        for (var dimension : ((ServerLevel) level).getServer().getAllLevels()) BodyOwnership.get(dimension).release(creatureId);
        LifeLifecycle.get(((ServerLevel) level).getServer()).cancel(creatureId);
        clearBossBar();
        ownershipCaptured = false;
        health = new HeartHealth(health.maximum(), health.deathDelay());
        combatMusicHits = 0; regenWait=regenDelay; regenProgress=0;
        creatureId = UUID.randomUUID();adaptiveDefense.reset();
        boundBody = null;
        pulseSignal = 0;
        level.setBlock(worldPosition, getBlockState().setValue(HeartBlock.ACTIVE, false), 3);
        changed();
        return true;
    }

    public Component status() {
        int count = level == null ? 0 : SableBodyProbe.inspect(level, worldPosition).connectedBodies().size();
        return Component.translatable("message.sablelife.status", health.state().name(), health.current(), health.maximum(), count)
                .append(Component.literal(" | ")).append(displayName())
                .append(Component.literal(" | Breaking: " + allowBreaking + " | Loot: " + deathLoot + " | XP: " + displayedExperience() + (deathExperienceLevels?" levels":" points")))
                .append(Component.literal(" | Lost parts: " + (level instanceof ServerLevel server ? LifeLifecycle.get(server.getServer()).lostParts(creatureId) : "")));
    }

    public Component interactionHint() {
        String key = switch (health.state()) {
            case DORMANT -> "message.sablelife.hint";
            case ALIVE -> "message.sablelife.hint_active";
            case DYING, DEAD -> "message.sablelife.dead";
        };
        return Component.translatable(key);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, HeartBlockEntity heart) {
        dev.sablelife.ownership.LoadedCoreIndex.observe(heart);
        if(heart.isControlCore()){RedstoneCorePolicy.sanitize(heart);heart.wireless.tick((ServerLevel)level);heart.modules.tick(heart);return;}
        heart.checkVoidDeath();
        heart.tickRegeneration();
        heart.updateBossBar((ServerLevel) level);
        heart.wireless.tick((ServerLevel) level);
        heart.adaptiveDefense.tick(level.getGameTime());
        heart.modules.tick(heart);
        if (!heart.ownershipCaptured && heart.health.state() != HeartState.DORMANT) {
            BodyOwnership.get((ServerLevel) level).capture(heart);
            LifeLifecycle.get(((ServerLevel) level).getServer()).setBreakable(heart.creatureId, heart.allowBreaking);
            LifeLifecycle.get(((ServerLevel) level).getServer()).setExplodable(heart.creatureId, heart.allowExplosion);
            heart.ownershipCaptured = true;
        }
        boolean dying = heart.health.state() == HeartState.DYING;
        if (dying || heart.health.state() == HeartState.DEAD) LifeLifecycle.get(((ServerLevel) level).getServer()).schedule(heart);
        boolean finished = heart.health.tick();
        dev.sablelife.ownership.CoreChunkLoading.observe(heart);
        // Core activation never emits an implicit clock; signals come from configured modules.
        int pulse = 0;
        if (pulse != heart.pulseSignal || finished) {
            heart.pulseSignal = pulse;
            heart.changed();
        } else if (dying) {
            heart.setChanged();
        }
    }

    private void updateBossBar(ServerLevel server) {
        if (!showBossBar || health.state() != HeartState.ALIVE) { clearBossBar(); return; }
        if (bossBar == null) bossBar = new ServerBossEvent(displayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        bossBar.setName(displayName());
        bossBar.setProgress(Math.clamp(health.current() / health.maximum(), 0, 1));
        var center = SableBodyProbe.toWorld(server, worldPosition.getCenter());
        var nearby = new HashSet<ServerPlayer>();
        for (var player : server.players()) {
            if (!player.isSpectator() && SableBodyProbe.toWorld(server, player.position()).distanceToSqr(center) <= 64 * 64) {
                nearby.add(player);
                bossBar.addPlayer(player);
            }
        }
        for (var player : java.util.List.copyOf(bossBar.getPlayers())) if (!nearby.contains(player)) bossBar.removePlayer(player);
    }
    private void clearBossBar() {
        if (bossBar != null) { bossBar.removeAllPlayers(); bossBar = null; }
    }
    @Override public void invalidate() { dev.sablelife.ownership.LoadedCoreIndex.remove(this);wireless.stop(); modules.clearRuntime(); adaptiveDefense.reset(); clearBossBar(); super.invalidate();dev.sablelife.compat.sable.SableBodyProbe.invalidate(level); }
    @Override public void onChunkUnloaded() { dev.sablelife.ownership.LoadedCoreIndex.remove(this);clearBossBar();wireless.stop(); modules.clearRuntime(); adaptiveDefense.reset(); super.onChunkUnloaded();dev.sablelife.compat.sable.SableBodyProbe.invalidate(level); }

    /** Restore an active creature after an Arena reset; never revive a dying core. */
    public void restoreFullHealth() {
        if (!(level instanceof ServerLevel)||health.state()!=HeartState.ALIVE)return;
        adaptiveDefense.reset();regenWait = 0; regenProgress = 0;
        if(health.heal(health.maximum()))changed();
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putUUID("CreatureId",creatureId);tag.putString("CreatureName",creatureName);
            tag.putString("LifeState",health.state().name());tag.putFloat("MaxHealth",health.maximum());tag.putFloat("Health",health.current());
            tag.putInt("DeathDelay",health.deathDelay());tag.putInt("RemainingDeathTicks",health.remainingDeathTicks());
            return;
        }
        tag.putInt("DataVersion", 1);
        tag.putLong("CombatMusicHits", combatMusicHits);
        tag.putUUID("CreatureId", creatureId);
        if (boundBody != null) tag.putUUID("BoundBody", boundBody);
        tag.putString("LifeState", health.state().name());
        tag.putFloat("MaxHealth", health.maximum());
        tag.putFloat("Health", health.current());
        tag.putInt("DeathDelay", health.deathDelay());
        tag.putDouble("RegenPercentPerSecond",regenPercentPerSecond);tag.putFloat("RegenPerSecond",regenPerSecond); tag.putInt("RegenDelay",regenDelay);
        tag.put("AdaptiveDefense",defenseConfiguration());tag.putInt("RegenWait",regenWait); tag.putInt("RegenProgress",regenProgress);
        tag.putInt("RemainingDeathTicks", health.remainingDeathTicks());
        tag.putBoolean("AllowLifeAttacks",allowLifeAttacks);
        tag.putIntArray("DeathChances",deathChances); tag.putIntArray("DeathWeights",deathWeights); tag.putBoolean("DeathWeighted",deathWeighted); tag.putInt("DeathRolls",deathRolls);
        if(customVoidDeathHeight!=null)tag.putInt("VoidDeathHeight",customVoidDeathHeight);
        tag.putBoolean("ForceChunkLoading",forceChunkLoading);
        tag.putBoolean("AllowBreaking", allowBreaking); tag.putBoolean("AllowExplosion", allowExplosion);
        tag.putBoolean("ShowBossBar", showBossBar);
        tag.putString("CreatureName", creatureName);
        tag.putString("DeathLoot", deathLoot.toString());
        tag.putInt("DeathExperience", deathExperience);tag.putBoolean("DeathExperienceLevels",deathExperienceLevels);
        tag.put("DeathPresentation", deathPresentation.save());
        tag.put("RemovalPresentation", removalPresentation.save());
        tag.putBoolean("DeathTemplateMode", deathTemplateMode);
        var savedTemplates = new CompoundTag();
        net.minecraft.world.ContainerHelper.saveAllItems(savedTemplates, deathTemplates, registries);
        tag.put("DeathTemplates", savedTemplates);
        tag.put("Wireless", wireless.save(registries));
        tag.put("VirtualModules", modules.save());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket || level != null && level.isClientSide) {
            if(tag.hasUUID("CreatureId"))creatureId=tag.getUUID("CreatureId");
            creatureName=tag.getString("CreatureName");
            if(creatureName.codePointCount(0,creatureName.length())>64||creatureName.codePoints().anyMatch(Character::isISOControl))creatureName="";
            if(tag.contains("LifeState"))try {
                int delay=Math.clamp(tag.getInt("DeathDelay"),0,HeartHealth.MAX_DEATH_DELAY);
                health=HeartHealth.restore(tag.getFloat("MaxHealth"),tag.getFloat("Health"),delay,HeartState.valueOf(tag.getString("LifeState")),Math.clamp(tag.getInt("RemainingDeathTicks"),0,delay));
            }catch(IllegalArgumentException error){health=HeartHealth.restore(100,0,60,HeartState.DEAD,0);}
            return;
        }
        combatMusicHits = Math.max(0, tag.getLong("CombatMusicHits"));
        loadRegen(tag); loadDefense(tag);
        wireless.load(tag.getCompound("Wireless"), registries);
        modules.load(tag.getCompound("VirtualModules"));
        if(isControlCore()){health=new HeartHealth(DEFAULT_MAX_HEALTH,DEFAULT_DEATH_DELAY);ownershipCaptured=false;RedstoneCorePolicy.sanitize(this);}
        wireless.finishMigration();
        ownershipCaptured = false;
        loadVoidDeathHeight(tag);
        forceChunkLoading=tag.getBoolean("ForceChunkLoading");
        allowBreaking = tag.contains("AllowBreaking") && tag.getBoolean("AllowBreaking");
        allowExplosion = tag.getBoolean("AllowExplosion");
        allowLifeAttacks = !tag.contains("AllowLifeAttacks") || tag.getBoolean("AllowLifeAttacks"); loadDeathRules(tag);
        showBossBar = tag.getBoolean("ShowBossBar");
        creatureName = tag.getString("CreatureName");
        if (creatureName.codePointCount(0, creatureName.length()) > 64 || creatureName.codePoints().anyMatch(Character::isISOControl)) creatureName = "";
        var loot = ResourceLocation.tryParse(tag.getString("DeathLoot"));
        deathLoot = loot == null ? ResourceLocation.withDefaultNamespace("empty") : loot;
        deathExperience = Math.clamp(tag.getInt("DeathExperience"), 0, ExperienceUnits.MAX_POINTS);deathExperienceLevels=tag.getBoolean("DeathExperienceLevels");
        deathPresentation.load(tag.getCompound("DeathPresentation"));
        removalPresentation.load(tag.getCompound("RemovalPresentation"));
        deathTemplateMode = tag.getBoolean("DeathTemplateMode");
        deathTemplates.clear();
        net.minecraft.world.ContainerHelper.loadAllItems(tag.getCompound("DeathTemplates"), deathTemplates, registries);
        if (tag.hasUUID("CreatureId")) creatureId = tag.getUUID("CreatureId");
        boundBody = tag.hasUUID("BoundBody") ? tag.getUUID("BoundBody") : null;
        pulseSignal = 0;
        if (!tag.contains("LifeState")) return;
        try {
            int delay=Math.clamp(tag.getInt("DeathDelay"),0,HeartHealth.MAX_DEATH_DELAY);
            health = HeartHealth.restore(tag.getFloat("MaxHealth"), tag.getFloat("Health"), delay,
                    HeartState.valueOf(tag.getString("LifeState")), Math.clamp(tag.getInt("RemainingDeathTicks"),0,delay));
        } catch (IllegalArgumentException error) {
            // Invalid saved cores fail closed rather than reactivating an already rewarded creature.
            health = HeartHealth.restore(100, 0, 60, HeartState.DEAD, 0);
            LogUtils.getLogger().warn("Invalid Heart data at {}; keeping the core dead", worldPosition, error);
        }
        if(isControlCore()){health=new HeartHealth(DEFAULT_MAX_HEALTH,DEFAULT_DEATH_DELAY);ownershipCaptured=false;boundBody=null;}
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag=new CompoundTag();write(tag,registries,true);return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
