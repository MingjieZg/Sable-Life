package dev.sablelife.module;

import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class HeartModules {
    // A full old core may contain 32 sound modules, each with embedded BGM.
    public static final int LIMIT = 256;
    public static int authoringLimit(){return dev.sablelife.SableLifeServerConfig.maximumModulesPerCreature();}
    private final List<VirtualModule> modules = new ArrayList<>();
    private int revision;
    public final ModuleSummonedWeapon summonedWeapons=new ModuleSummonedWeapon();
    private final ModuleItemDisplay itemDisplays=new ModuleItemDisplay();
    public final ModuleSurfaceField surfaceFields=new ModuleSurfaceField();
    private final ModuleCombat combat = new ModuleCombat();
    private final ModuleEvents events = new ModuleEvents();
    private final ModuleArena arena = new ModuleArena();
    private final ModuleGroundStrike groundStrike = new ModuleGroundStrike();
    private final ModuleHoming homing = new ModuleHoming();
    private final ModuleQuake quake = new ModuleQuake();
    private final ModulePull pull = new ModulePull();
    private final ModuleFreeze freeze = new ModuleFreeze();
    private final ModuleGrab grab = new ModuleGrab();
    private final ModuleSlash slash=new ModuleSlash();
    private final ModuleSphereBlast sphereBlast=new ModuleSphereBlast();
    private final ModuleLaser laser = new ModuleLaser();
    public final ModuleDamageAccumulation damageAccumulation=new ModuleDamageAccumulation();
    public final ModuleHitCounter hitCounter=new ModuleHitCounter();
    private final ModuleHealthAdjustment healthAdjustment=new ModuleHealthAdjustment();
    public void movementTurned(HeartBlockEntity heart,net.minecraft.world.phys.Vec3 pivot,org.joml.Quaterniond turn){linearMovement.turned(heart,pivot,turn);}
    public long moveActivation(UUID id){return moveSets.activation(id);}
    public final ModuleSignalLogic signalLogic=new ModuleSignalLogic();
    public final ModuleCombatEvents combatEvents=new ModuleCombatEvents();
    private final ModuleHealthPhase healthPhase = new ModuleHealthPhase();
    private final ModuleSequence sequence = new ModuleSequence();
    private final ModuleDistortionTrail distortionTrail=new ModuleDistortionTrail();
    private final ModuleShockwave shockwave=new ModuleShockwave();
    private final ModuleHoverAnchor hoverAnchor = new ModuleHoverAnchor();
    private final ModuleGyroscope gyroscope = new ModuleGyroscope();
    private final ModuleSignalTurn signalTurn=new ModuleSignalTurn();
    private final ModuleAutoFacing autoFacing = new ModuleAutoFacing();
    private final ModuleLinearMovement linearMovement = new ModuleLinearMovement();
    private final ModuleCommands commands = new ModuleCommands();
    private final ModuleMoveSet moveSets = new ModuleMoveSet();
    public final ModuleHurt hurtTriggers = new ModuleHurt();
    public CompoundTag sequenceSnapshot(UUID id) { return sequence.snapshot(id); }
    public void resetSequence(UUID id) { sequence.reset(id); }
    public ModuleEvents events() { return events; }
    private net.minecraft.server.level.ServerLevel audioLevel;
    private final java.util.Set<UUID> audioModules = new java.util.HashSet<>();
    public void registerAudio(net.minecraft.server.level.ServerLevel level,UUID id){audioLevel=level;audioModules.add(id);}
    private void audioLifecycle(HeartBlockEntity heart){
        if(!(heart.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;audioLevel=level;
        var active=new java.util.HashSet<UUID>();
        if(heart.operational()&&(!heart.isControlCore()||heart.wireless().master()))for(var m:modules)if(m.enabled()&&ModuleFields.forType(m.type()).stream().anyMatch(f->f.choices().equals(List.of("library")))&&status(m,heart).equals("valid"))active.add(m.id());
        for(var id:audioModules)if(!active.contains(id))ModuleAudio.stop(level,id);audioModules.clear();audioModules.addAll(active);
    }
    public void tick(HeartBlockEntity heart) { if(heart.isControlCore()){audioLifecycle(heart);moveSets.tick(heart);sequence.tick(heart);boolean hoverBusy=hoverAnchor.tick(heart);boolean gyroBusy=gyroscope.tick(heart,hoverAnchor.locksRotation(),false,hoverAnchor.rotationPivot(heart));boolean manualBusy=signalTurn.tick(heart,hoverBusy||gyroBusy);autoFacing.tick(heart,hoverBusy||gyroBusy||manualBusy);events.tick(heart);itemDisplays.tick(heart);shockwave.tick(heart);distortionTrail.tick(heart);return;}audioLifecycle(heart); hurtTriggers.tick(heart); healthPhase.tick(heart); combatEvents.tick(heart); signalLogic.tick(heart); moveSets.tick(heart); sequence.tick(heart); hitCounter.tick(heart); healthAdjustment.tick(heart); commands.tick(heart); boolean hoverBusy=hoverAnchor.tick(heart); if(hoverBusy)linearMovement.clear();else linearMovement.tick(heart); boolean gyroBusy=gyroscope.tick(heart,hoverAnchor.locksRotation()||linearMovement.locksRotation(),linearMovement.gravityActive(),hoverAnchor.rotationPivot(heart));boolean manualBusy=signalTurn.tick(heart,hoverBusy||linearMovement.blocksTurning()||gyroBusy,linearMovement.preserveTranslation());autoFacing.tick(heart,hoverBusy||linearMovement.blocksTurning()||gyroBusy||manualBusy,linearMovement.preserveTranslation()); ModuleLoot.tick(heart); events.tick(heart); combat.tick(heart); summonedWeapons.tick(heart); surfaceFields.tick(heart); arena.tick(heart); groundStrike.tick(heart); sphereBlast.tick(heart); slash.tick(heart); laser.tick(heart); pull.tick(heart);freeze.tick(heart);grab.tick(heart); quake.tick(heart); homing.tick(heart);itemDisplays.tick(heart);shockwave.tick(heart);distortionTrail.tick(heart); }
    public void clearRuntime() { summonedWeapons.clear(); signalLogic.clear();combatEvents.clear(); surfaceFields.clear(); distortionTrail.clear(); shockwave.clear(); hitCounter.clear();healthAdjustment.clear(); hoverAnchor.clear(); if(audioLevel!=null)audioModules.forEach(id->ModuleAudio.stop(audioLevel,id));audioModules.clear();audioLevel=null; linearMovement.clear(); gyroscope.clear(); signalTurn.clear(); autoFacing.clear(); commands.clear(); hurtTriggers.clear(); combat.clear(); events.clear(); arena.clear(); groundStrike.clear(); sphereBlast.clear(); slash.clear(); laser.clear(); pull.clear();freeze.clear();grab.clear(); quake.clear(); homing.clear(); moveSets.clear(); sequence.clear(); healthPhase.clear(); damageAccumulation.clear(); }
    public List<VirtualModule> all() { return List.copyOf(modules); }
    public VirtualModule find(UUID id) { return modules.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null); }
    public int revision() { return revision; }
    public void changed() { revision++; }
    public VirtualModule addPreset(CompoundTag tag) {
        var module=ModulePreset.create(tag);if(!module.known()||!VirtualModule.AVAILABLE_TYPES.contains(module.type())||modules.size()>=authoringLimit())return null;modules.add(module);changed();return module;
    }
    public VirtualModule add(String type) {
        if (!VirtualModule.TYPES.contains(type) || modules.size() >= authoringLimit()) return null;
        var module = new VirtualModule(type); modules.add(module); changed(); return module;
    }
    /** Local duplication retains Marks/bindings and deep configuration, never runtime state. */
    public VirtualModule copy(UUID source){
        var original=find(source);if(original==null||!original.known()||!VirtualModule.AVAILABLE_TYPES.contains(original.type())||modules.size()>=authoringLimit())return null;
        var tag=original.save();tag.putUUID("Id",UUID.randomUUID());var duplicate=VirtualModule.load(tag);
        modules.add(modules.indexOf(original)+1,duplicate);changed();return duplicate;
    }
    public boolean remove(UUID id) { boolean removed = modules.removeIf(m -> m.id().equals(id)); if (removed) changed(); return removed; }
    public boolean reorder(UUID id,int direction){
        if(Math.abs(direction)!=1)return false;
        for(int i=0;i<modules.size();i++)if(modules.get(i).id().equals(id)){
            int destination=i+direction;if(destination<0||destination>=modules.size())return false;
            java.util.Collections.swap(modules,i,destination);changed();return true;
        }
        return false;
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); var list = new ListTag(); modules.forEach(m -> list.add(m.save()));
        tag.put("Modules", list); tag.putInt("Revision", revision); return tag;
    }
    public CompoundTag phaseRuntime(UUID module){return healthPhase.snapshot(module);}
    public CompoundTag snapshot(HeartBlockEntity heart, boolean editable) {
        var tag = save();tag.putInt("ModuleLimit",authoringLimit());tag.putBoolean("ControlCore",heart.isControlCore());tag.putBoolean("ControlReady",heart.operational());if(heart.isControlCore()){var graph=dev.sablelife.compat.sable.SableBodyProbe.inspect(heart.getLevel(),heart.getBlockPos());tag.putString("ControlStatus",!graph.assembled()?"unassembled":graph.otherHearts()>0?"duplicate":heart.wireless().master()?"ready":"paused");} tag.putBoolean("Editable", editable); tag.putString("LifeState", heart.health().state().name());
        tag.putString("HeartName", heart.displayName().getString());
        tag.putDouble("HealthPercent", heart.health().current() / (double) heart.health().maximum() * 100);
        tag.putBoolean("BgmStarted", heart.combatMusicHits() > 0);
        var list = tag.getList("Modules", 10);
        for (int i = 0; i < modules.size(); i++) {
            list.getCompound(i).putString("Status", status(modules.get(i), heart));
            list.getCompound(i).putBoolean("TriggerPowered", triggered(modules.get(i), heart));
            var module = modules.get(i);
            if(module.type().equals("arena"))list.getCompound(i).put("RespawnRuntime",dev.sablelife.ownership.ArenaRespawns.snapshot(heart,module.id()));
            list.getCompound(i).put("HurtRuntime", hurtTriggers.snapshot(module.id(),heart.getLevel().getGameTime()));
            if(SignalRules.supported(module.type()))list.getCompound(i).put("SignalRuntime",module.type().equals("signal_logic")?signalLogic.snapshot(module.id()):combatEvents.snapshot(module.id()));
            if (module.type().equals("health_phase")) {
                var data = list.getCompound(i);
                data.put("PhaseRuntime",healthPhase.snapshot(module.id()));
                data.putBoolean("PhaseActive", healthPhase.active(module.id()));
                data.putInt("PhaseRemaining", healthPhase.remaining(module.id()));
                data.putString("PhaseChannelStatus", ModuleHealthPhase.channelStatus(module, heart));
                data.putBoolean("PhaseRangeValid", module.value("phaseLower") < module.value("phaseUpper"));
            }
            if(module.type().equals("damage_accumulation"))list.getCompound(i).put("AccumulationRuntime",damageAccumulation.snapshot(module,heart));
            if(module.type().equals("summoned_weapon"))list.getCompound(i).put("SummonRuntime",summonedWeapons.snapshot(module.id(),heart));
            if(module.type().equals("item_display"))list.getCompound(i).putBoolean("ItemDisplayVisible",ModuleItemDisplay.showing(heart,module));
            if (module.type().equals("move_set")) list.getCompound(i).put("MoveSetRuntime", moveSets.snapshot(module, heart));
            if (module.type().equals("command")) list.getCompound(i).put("CommandRuntime", commands.snapshot(module.id()));
            if(module.type().equals("gyroscope")) { var data=list.getCompound(i); data.putBoolean("GyroCalibrated",module.gyroDown()!=null); data.putString("GyroState",gyroscope.state(module.id())); double tilt=ModuleGyroscope.tilt(ModuleGyroscope.down(heart,module)); if(Double.isFinite(tilt)) data.putDouble("GyroTilt",tilt); }
            if(module.type().equals("hit_counter")){var data=list.getCompound(i);data.put("HitRuntime",hitCounter.snapshot(module.id()));int source=0;for(int j=0;j<modules.size();j++)if(modules.get(j).id().equals(module.hitSourceId()))source=j+1;data.getCompound("Settings").putDouble("hitSource",source);}
            if(module.type().equals("hover_anchor"))list.getCompound(i).putBoolean("HoverActive",hoverAnchor.active(module.id()));
            if(module.type().equals("signal_turn"))list.getCompound(i).putString("TurnState",signalTurn.state(module.id()));
            if (module.type().equals("auto_facing")) list.getCompound(i).putString("FacingState",autoFacing.state(module.id()));
            if (module.type().equals("linear_movement")) list.getCompound(i).putString("MovementPhase",linearMovement.phase(module.id()));
            if (module.type().equals("quake")) list.getCompound(i).putString("QuakePhase", quake.phase(module.id()));
            if (module.type().equals("pull")) { list.getCompound(i).putString("PullPhase", pull.phase(module.id())); list.getCompound(i).putInt("PullVictims", pull.victims(module.id())); }
            if (module.type().equals("laser")) list.getCompound(i).putString("LaserPhase", laser.phase(module.id()));
            if (module.type().equals("sequence")) list.getCompound(i).put("SequenceRuntime", sequence.snapshot(module.id()));
            if (module.type().equals("arena")) {
                list.getCompound(i).putBoolean("ArenaWarning", arena.warning(module.id()));
                list.getCompound(i).putDouble("ArenaDistance", module.arenaCenter() == null ? 0 : ModuleArena.horizontalDistance(ModuleArena.corePosition(heart), module.arenaCenter()));
            }
            if (module.type().equals("pulse")) list.getCompound(i).putBoolean("PulseTargetPresent", combat.pulseTarget(module.id()));
            if (module.type().equals("navigation")) {
                list.getCompound(i).putString("NavigationTarget", events.navigation().name(module.id()));
                list.getCompound(i).putInt("NavigationLeft", events.navigation().left(module.id()));
                list.getCompound(i).putInt("NavigationRight", events.navigation().right(module.id()));
                list.getCompound(i).putInt("NavigationUp", events.navigation().up(module.id()));
                list.getCompound(i).putInt("NavigationDown", events.navigation().down(module.id()));
            }
            if (module.type().equals("sensor") && !module.anchors().isEmpty() && heart.getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
                var anchor = module.anchors().getFirst(); var body = anchor.body(heart);
                list.getCompound(i).putBoolean("SensorPowered", events.sensorPowered(module.id()));
                list.getCompound(i).putBoolean("SensorDetected",events.sensorDetected(module.id()));
            }
        }
        return tag;
    }
    public static String status(VirtualModule module, HeartBlockEntity heart) {
        if(heart.isControlCore()&&!dev.sablelife.heart.RedstoneCorePolicy.allowed(module))return "unknown";
        if (!module.known()) return "unknown";
        if (module.commandApprovalRequired()) return "command_locked";
        if(SignalRules.supported(module.type()))return module.enabled()?"valid":"disabled";
        if(module.type().equals("surface_field"))return module.enabled()?"valid":"disabled";
        if(TargetCastOrigin.targeted(module))return module.enabled()?"valid":"disabled";
        // Whole-creature protection has no dependency on its retained per-part Mark.
        if(module.type().equals("armor")&&module.value("armorWholeBody")==1)return module.enabled()?"valid":"disabled";
        if (module.invalidated()) return "destroyed";
        if (module.detached()) return "disconnected";
        if (module.type().equals("arena")) {
            if (module.arenaCenter() == null) return "unmarked";
            if (!heart.getLevel().dimension().location().toString().equals(module.arenaDimension())) return "unloaded";
            return module.enabled() ? "valid" : "disabled";
        }
        if (module.coreModule()) return module.enabled() ? "valid" : "disabled";
        // Mark and external frequency are optional output destinations, not prerequisites for scheduling.
        if(module.type().equals("wireless_redstone") && module.anchors().isEmpty()) return module.enabled() ? "valid" : "disabled";
        if (module.anchors().isEmpty() || module.area() && module.anchors().size() != 2) return "unmarked";
        if (module.type().equals("particles") && !module.area() && module.anchors().size() != 1) return "unmarked";
        if (module.area() && !module.anchors().getFirst().bodyId().equals(module.anchors().get(1).bodyId())) return "disconnected";
        for (var anchor : module.anchors()) { String state = anchor.status(heart); if (!state.equals("valid")) return state; }
        if (module.area()) {
            var half = ModuleCombat.halfSize(heart, module);
            if (half.x > 16 || half.y > 16 || half.z > 16) return "unmarked";
        }
        return module.enabled() ? "valid" : "disabled";
    }
    /** Existing wireless channels remain the source of truth for virtual abilities. */
    public static boolean triggered(VirtualModule module, HeartBlockEntity heart) {
        if (!(heart.getLevel() instanceof net.minecraft.server.level.ServerLevel server)
                || !heart.operational() || heart.isControlCore()&&!heart.wireless().master() || !status(module, heart).equals("valid")) return false;
        return controlPowered(module, heart);
    }
    /** Also used for the final hurt event, immediately after health enters its death countdown. */
    public static boolean controlPowered(VirtualModule module, HeartBlockEntity heart) {
        if (!(heart.getLevel() instanceof net.minecraft.server.level.ServerLevel server) || module.anchors().isEmpty() && !module.type().equals("bgm") && !module.type().equals("arena") && !module.coreModule() && !module.type().equals("wireless_redstone")&&!TargetCastOrigin.targeted(module)) return false;
        boolean powered = switch ((int) module.value("trigger")) {
            case 0 -> true;
            case 1 -> (module.coreModule() || module.type().equals("arena") || TargetCastOrigin.targeted(module) || module.anchors().isEmpty()) ? dev.sablelife.attack.ModulePower.hasControlSignal(server, heart.getBlockPos()) : module.importData().getBoolean("LegacyPower")
                    ? dev.sablelife.attack.ModulePower.hasSignal(server, module.anchors().getFirst().position(module.anchors().getFirst().body(heart)))
                    : dev.sablelife.attack.ModulePower.hasControlSignal(server, module.anchors().getFirst().position(module.anchors().getFirst().body(heart)));
            case 2 -> heart.wireless().output((int) module.value("channel")) > 0;
            case 3 -> heart.wireless().input((int) module.value("channel")) > 0;
            case 4 -> heart.wireless().master() && heart.modules().moveSets.powered(module.id());
            case 5 -> heart.wireless().master() && heart.modules().hurtTriggers.powered(module.id());
            default -> false;
        };
        return powered;
    }
    public void load(CompoundTag tag) {
        clearRuntime();
        modules.clear(); revision = tag.getInt("Revision"); var list = tag.getList("Modules", 10);
        for (int i = 0; i < Math.min(LIMIT, list.size()); i++) {
            if(list.getCompound(i).getString("Type").equals("dispatch"))continue; // Removed standalone scheduler, not a move row.
            var module = VirtualModule.load(list.getCompound(i)); if (find(module.id()) == null) modules.add(module);
        }
        // Preserve old embedded music settings by moving them to a core-centered module.
        for (int i = 0; i < Math.min(LIMIT, list.size()); i++) {
            var old = list.getCompound(i); var settings = old.getCompound("Settings");
            if (!old.getString("Type").equals("sound") || settings.getString("bgm").isEmpty() || modules.size() >= LIMIT) continue;
            var migrated = old.copy(); migrated.putString("Type", "bgm"); migrated.putUUID("Id", UUID.randomUUID());
            String oldName = old.getString("Name");
            migrated.putString("Name", oldName.isEmpty() ? "" : oldName.substring(0, Math.min(oldName.length(), 36)) + " BGM");
            for (String key : List.of("Anchors", "ImportSource", "ImportData", "RewardOwner", "Detached", "Invalidated", "LootSamples")) migrated.remove(key);
            modules.add(VirtualModule.load(migrated)); revision++;
        }
    }
}
