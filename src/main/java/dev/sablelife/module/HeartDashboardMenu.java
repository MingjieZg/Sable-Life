package dev.sablelife.module;

import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import java.util.UUID;

/** One invisible, non-extractable snapshot slot synchronizes bounded module data to this viewer only. */
public final class HeartDashboardMenu extends AbstractContainerMenu {
    private final HeartBlockEntity heart;
    private final Player viewer;
    private final SimpleContainer snapshot = new SimpleContainer(1);
    public HeartDashboardMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public HeartDashboardMenu(int id, Inventory inventory, HeartBlockEntity heart) {
        super(SableLife.DASHBOARD_MENU.get(), id); this.heart = heart; viewer = inventory.player;
        addSlot(new Slot(snapshot, 0, -10000, -10000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean isActive() { return false; }
        }); refresh();
    }
    public CompoundTag snapshot() { return snapshot.getItem(0).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    private void refresh() {
        if (heart == null) return;
        var tag = heart.modules().snapshot(heart, heart.canEdit(viewer)); tag.putBoolean("Ready", true); tag.putBoolean("CommandEditable", viewer.canUseGameMasterBlocks()); tag.putBoolean("CommandAllowed", heart.getLevel().getServer().isCommandBlockEnabled());tag.putBoolean("RespawnAllowed",viewer.isCreative()&&viewer.canUseGameMasterBlocks());
        var selected = DashboardSelection.get(heart, viewer.getUUID());if(selected==null&&!heart.modules().all().isEmpty())selected=heart.modules().all().getFirst().id(); if (selected != null) tag.putUUID("Selected", selected);
        // A large core still exposes its complete selected module, but does not resend every other module's deep config.
        if(tag.sizeInBytes()>1_000_000){
            var list=tag.getList("Modules",10);var detailed=selected;
            for(var value:list){var module=(CompoundTag)value;if(detailed==null||!module.hasUUID("Id")||!module.getUUID("Id").equals(detailed))compactHeader(module);}
            tag.putBoolean("PartialAuthoring",true);
            if(tag.sizeInBytes()>1_000_000){for(var value:list)compactHeader((CompoundTag)value);tag.putBoolean("Editable",false);tag.putBoolean("AuthoringOversized",true);}
        }
        var stack = new ItemStack(Items.PAPER); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        snapshot.setItem(0, stack);
    }
    private static void compactHeader(CompoundTag module){
        var keep=java.util.Set.of("Version","Id","Type","Name","Enabled","Invalidated","Detached","Trigger","Channel","Status","TriggerPowered","HurtRuntime","PhaseRuntime","HitRuntime","PhaseActive","PhaseRemaining","PhaseChannelStatus","PhaseRangeValid","MovementPhase","TurnState","FacingState","LaserPhase","QuakePhase","PullPhase","PullVictims","ArenaDistance","ArenaWarning","NavigationTarget","NavigationLeft","NavigationRight","NavigationUp","NavigationDown","ItemDisplayVisible","RespawnRuntime");
        for(var key:java.util.Set.copyOf(module.getAllKeys()))if(!keep.contains(key))module.remove(key);
    }
    @Override public void broadcastChanges() { if (heart != null && heart.getLevel().getGameTime() % 5 == 0) refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) {
        return heart == null || heart.canAccessEditor(player) && !heart.isRemoved() && player.level() == heart.getLevel()
                && heart.getLevel().getBlockEntity(heart.getBlockPos()) == heart
                && SableBodyProbe.toWorld(player.level(), player.getEyePosition()).distanceToSqr(SableBodyProbe.toWorld(player.level(), heart.getBlockPos().getCenter())) <= 64;
    }
    public boolean action(Player player, int revision, UUID id, String operation, String key, double value, String text) {
        if (heart == null || !stillValid(player)) return false;
        if(heart.isControlCore()){
            if(!java.util.Set.of("add","add_preset","projectile","select","wireless","sequence","frequency","preview","mark","edit","remove","reorder","move_set","sound_editor").contains(operation))return false;
            if((operation.equals("add")||operation.equals("add_preset"))&&!dev.sablelife.heart.RedstoneCorePolicy.TYPES.contains(key))return false;
            var existing=heart.modules().find(id);if(!operation.equals("add")&&!operation.equals("add_preset")&&!operation.equals("wireless")&&(existing==null||!dev.sablelife.heart.RedstoneCorePolicy.TYPES.contains(existing.type())))return false;
            if(operation.equals("edit")&&(key.startsWith("hurt")||existing!=null&&dev.sablelife.heart.RedstoneCorePolicy.disabledField(existing,key)||key.equals("trigger")&&(value<0||value>4)))return false;
        }
        if(heart.isControlCore()&&operation.equals("sound_editor")&&!dev.sablelife.heart.RedstoneCorePolicy.audioKeyAllowed(heart.modules().find(id),key))return false;
        if(heart.isControlCore()&&operation.equals("move_set")){
            if(key.equals("phaseLower")||key.equals("phaseUpper"))return false;
            if(key.equals("action_add")){
                try {var parts=text.split("\\|",3);var target=heart.modules().find(UUID.fromString(parts[1]));if(target==null||!dev.sablelife.heart.RedstoneCorePolicy.allowed(target))return false;}catch(IllegalArgumentException|IndexOutOfBoundsException e){return false;}
            }
        }
        if(operation.equals("release"))return dev.sablelife.heart.CoreRelease.release(heart,player);
        DashboardSelection.set(heart, player.getUUID(), id);
        if (operation.equals("select")) { refresh(); broadcastChanges(); return heart.modules().find(id) != null; }
        if (operation.equals("settings")) { heart.openSettings(player); return true; }
        if (operation.equals("wireless")) { heart.openWireless(player); return true; }
        if (operation.equals("loot")) { heart.openDeathLootMenu(player); return true; }
        if(operation.equals("phase_rules")){var module=heart.modules().find(id);if(module==null||!module.type().equals("health_phase"))return false;HealthPhaseRuleMenu.open(player,heart,module,null);return true;}
        if(operation.equals("armor_rules")){var module=heart.modules().find(id);if(module==null||!module.type().equals("armor"))return false;ArmorRuleMenu.open(player,heart,module,null);return true;}
        if(operation.equals("arena_buffs")){var module=heart.modules().find(id);if(module==null||!module.type().equals("arena"))return false;ArenaBuffMenu.open(player,heart,module,null);return true;}
        if (operation.equals("sequence")) {
            var module = heart.modules().find(id); if (module == null || !module.type().equals("sequence")) return false;
            player.openMenu(new net.minecraft.world.SimpleMenuProvider((container, inventory, ignored) -> new SequenceMenu(container, inventory, heart, module),
                    net.minecraft.network.chat.Component.translatable("sequence.sablelife.title"))); return true;
        }
        if (operation.equals("effects")) {
            var module = heart.modules().find(id);
            if(module!=null&&(module.type().equals("linear_movement")||module.type().equals("sphere_blast")||module.type().equals("pulse"))&&VirtualModule.particleStage(key)!=null){
                String stage=VirtualModule.particleStage(key);if(module.movementParticles(stage)==null)return false;
                player.openMenu(new net.minecraft.world.SimpleMenuProvider((container,inventory,ignored)->EffectSelectionMenu.movement(container,inventory,heart,module,stage),net.minecraft.network.chat.Component.translatable("effects.sablelife.title")));return true;
            }
            if (module == null || !module.known() || !(ModuleEffects.TYPES.contains(module.type()) || (module.type().equals("particles") || module.type().equals("quake") || module.type().equals("ground_strike") || module.type().equals("pull") || module.type().equals("laser")))) return false;
            player.openMenu(new net.minecraft.world.SimpleMenuProvider((container, inventory, ignored) -> new EffectSelectionMenu(container, inventory, heart, module, key.equals("particles")),
                    net.minecraft.network.chat.Component.translatable("effects.sablelife.title"))); return true;
        }
        if (operation.equals("projectile")) {
            var module = heart.modules().find(id);
            if (module == null || !module.known() || !java.util.List.of("arrow", "gun", "mortar", "ground_strike", "homing", "item_display").contains(module.type())) return false;
            player.openMenu(new net.minecraft.world.SimpleMenuProvider((container, inventory, ignored) -> new ProjectileItemMenu(container, inventory, heart, module),
                    net.minecraft.network.chat.Component.translatable("projectile.sablelife.title"))); return true;
        }
        if(operation.equals("frequency")) {
            var module=heart.modules().find(id);if(module==null||!java.util.List.of("wireless_redstone","damage_accumulation","sensor").contains(module.type()))return false;
            player.openMenu(new net.minecraft.world.SimpleMenuProvider((container,inventory,ignored)->new ModuleFrequencyMenu(container,inventory,heart,module),net.minecraft.network.chat.Component.translatable("frequency.sablelife.title")));return true;
        }
        if (operation.equals("samples")) {
            var module = heart.modules().find(id); if (module == null || !module.type().equals("loot") || !module.known()) return false;
            if (module.importData().getBoolean("TableMode")) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.sablelife.template_table", module.importData().getString("Loot")), false);
            player.openMenu(new net.minecraft.world.SimpleMenuProvider((container, inventory, ignored) -> new VirtualLootMenu(container, inventory, heart, module),
                    net.minecraft.network.chat.Component.translatable("menu.sablelife.loot_templates"))); return true;
        }
        if(operation.equals("sound_editor"))return dev.sablelife.sound.SoundTimelineMenu.open(player,heart,id,key,"module");
        if (operation.equals("preview") && player instanceof net.minecraft.server.level.ServerPlayer server) {
            var module = heart.modules().find(id);
            if (module == null) return false;
            if (module.invalidated()) return false;
            if(!module.type().equals("bgm")&&!TargetCastOrigin.targeted(module)&&module.arenaCenter()==null&&module.anchors().isEmpty())return false;
            MarkingSessions.showPreview(server, heart, module); return true;
        }
        if(operation.equals("accum_reset")){var target=heart.modules().find(id);if(!player.isCreative()||target==null||!target.type().equals("damage_accumulation"))return false;heart.modules().damageAccumulation.reset(id);refresh();broadcastChanges();return true;}
        if(operation.equals("hurt_reset")) { if(!player.isCreative() || heart.modules().find(id)==null) return false; heart.modules().hurtTriggers.reset(id); refresh(); broadcastChanges(); return true; }
        if(operation.equals("arena_record")||operation.equals("arena_clear")){
            if(revision!=heart.modules().revision()||!(player instanceof net.minecraft.server.level.ServerPlayer server))return false;
            var target=heart.modules().find(id);boolean ok=operation.equals("arena_record")?dev.sablelife.ownership.ArenaRespawns.record(server,heart,target):dev.sablelife.ownership.ArenaRespawns.clear(server,heart,target);refresh();broadcastChanges();return ok;
        }
        if(operation.equals("edit")&&key.startsWith("arenaRespawn")&&(!dev.sablelife.ownership.ArenaRespawns.available()||!player.canUseGameMasterBlocks()))return false;
        if (!heart.canEdit(player) || revision != heart.modules().revision()) { refresh(); sendAllDataToRemote(); return false; }
        var module = heart.modules().find(id); boolean result;
        switch (operation) {
            case "add_preset" -> {
                if(!AuthoringLimits.presetText(text)||!VirtualModule.AVAILABLE_TYPES.contains(key)||key.equals("command")&&!player.canUseGameMasterBlocks())return false;
                try {
                    var tag=net.minecraft.nbt.TagParser.parseTag(text);if(!AuthoringLimits.compound(tag,AuthoringLimits.MAX_PRESET_BYTES)||!tag.getString("Type").equals(key)||tag.getInt("Version")>1)return false;
                    if(heart.isControlCore()&&tag.getInt("Trigger")==5)tag.putInt("Trigger",0);
                    var added=heart.modules().addPreset(tag);result=added!=null;if(added!=null)DashboardSelection.set(heart,player.getUUID(),added.id());
                    if (added != null && added.type().equals("command")) added.approveCommands();
                }catch(com.mojang.brigadier.exceptions.CommandSyntaxException|IllegalArgumentException e){return false;}
            }
            case "add" -> {
                if(key.equals("command")&&!player.canUseGameMasterBlocks()||!VirtualModule.AVAILABLE_TYPES.contains(key))return false;
                var added=heart.modules().add(key);result=added!=null;if(added!=null)DashboardSelection.set(heart,player.getUUID(),added.id());
            }
            case "commands" -> {
                if (!player.canUseGameMasterBlocks() || module == null || !module.type().equals("command")) return false;
                try { int split=text.indexOf('|'); int index=Integer.parseInt(split<0?text:text.substring(0,split)); result=module.commands().edit(key,index,value,split<0?"":text.substring(split+1)); } catch (IllegalArgumentException e) { return false; }
                if(result){module.approveCommands();heart.modules().changed();}
            }
            case "move_set" -> {
                if (key.equals("action_add") && !player.canUseGameMasterBlocks()) {
                    try { var target = heart.modules().find(UUID.fromString(text.split("\\|", 3)[1])); if (target != null && target.type().equals("command")) return false; }
                    catch (IllegalArgumentException | IndexOutOfBoundsException e) { return false; }
                }
                result = module != null && module.type().equals("move_set") && module.moveSet().edit(key, value, text, heart.modules());
                if (result) heart.modules().changed();
            }
            case "gyro_calibrate" -> { result=ModuleGyroscope.calibrate(heart,module); if(result)heart.modules().changed(); }
            case "reorder" -> result=Double.isFinite(value)&&(value==-1||value==1)&&heart.modules().reorder(id,(int)value);
            case "remove" -> { if(module!=null&&module.type().equals("command")&&!player.canUseGameMasterBlocks())return false;result=heart.modules().remove(id); }
            case "mark" -> { return module != null && module.known() && MarkingSessions.start(player, heart, module); }
            case "edit" -> { if (module != null && module.type().equals("command") && !player.canUseGameMasterBlocks()) return false; if(module!=null&&module.type().equals("hit_counter")&&key.equals("hitSource")){if(!Double.isFinite(value)||value!=Math.rint(value)||value<0||value>heart.modules().all().size())return false;var source=value==0?null:heart.modules().all().get((int)value-1);if(source!=null&&!ModuleHitCounter.ATTACKS.contains(source.type()))return false;module.hitSource(source==null?null:source.id(),(int)value);result=true;}else result = module != null && module.edit(key, value, text); if (result) { if (module.type().equals("command")) module.approveCommands(); if(key.equals("trigger") || key.startsWith("hurt") || key.equals("enabled")) heart.modules().hurtTriggers.reset(id); heart.modules().changed(); } }
            default -> { return false; }
        }
        if (result) { heart.setChanged(); refresh(); broadcastChanges(); }
        return result;
    }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {}
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
}
