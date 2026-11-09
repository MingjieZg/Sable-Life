package dev.sablelife.module;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/** Persistent authoring settings shared by the dashboard and validated server runtime. */
public final class VirtualModule {
    public static final List<String> TYPES = List.of("move_set", "signal_logic", "combat_event", "health_phase", "hit_counter", "health_adjustment", "damage_accumulation", "sequence", "wireless_redstone", "command", "linear_movement", "auto_facing", "signal_turn", "gyroscope", "hover_anchor", "visual_shockwave", "distortion_trail", "sensor", "navigation", "arena", "armor", "melee", "flame", "arrow", "gun", "mortar", "homing", "laser", "sphere_blast", "slash", "ground_strike", "quake", "pull", "freeze", "grab", "pulse", "surface_field", "summoned_weapon", "particles", "health_bar", "item_display", "trigger_sound", "sound", "bgm", "loot", "blueprint_spawn", "entity_hitbox", "block_interaction");
    /** New-module catalog grouped by sidebar color: signal, weapon, function. */
    public static final List<String> AVAILABLE_TYPES = List.of(
            "move_set", "signal_logic", "combat_event", "health_phase", "hit_counter", "damage_accumulation", "sequence", "wireless_redstone", "command", "sensor", "navigation",
            "melee", "flame", "arrow", "gun", "mortar", "homing", "laser", "sphere_blast", "slash", "ground_strike", "quake", "pull", "freeze", "grab", "pulse", "surface_field", "summoned_weapon",
            "arena", "armor", "health_adjustment", "linear_movement", "auto_facing", "signal_turn", "gyroscope", "hover_anchor", "visual_shockwave", "distortion_trail", "particles", "health_bar", "item_display", "trigger_sound", "sound", "bgm", "loot", "blueprint_spawn", "entity_hitbox", "block_interaction");
    public record Emitter(net.minecraft.world.phys.Vec3 position,net.minecraft.world.phys.Vec3 direction,org.joml.Quaterniond rotation,UUID audioOwner){}
    private Emitter emitter;
    public Emitter emitter(){return emitter;}
    public void emitter(Emitter value){emitter=value;}
    public UUID audioId(){return emitter==null?id:emitter.audioOwner();}
    public UUID summonAttackId(){return settings.hasUUID("SummonAttackId")?settings.getUUID("SummonAttackId"):null;}
    public void summonAttack(UUID value){if(value==null)settings.remove("SummonAttackId");else settings.putUUID("SummonAttackId",value);}
    private final java.util.Map<String,VirtualModule> movementParticles = new java.util.HashMap<>();
    public VirtualModule movementParticles(String stage) { return movementParticles.get(stage); }
    public static String particleStage(String key){if(key.startsWith("summon")&&key.endsWith("Particles"))return key.substring(6,key.length()-9);if(key.equals("impactParticles"))return "Impact";if(key.startsWith("pulse")&&key.endsWith("Particles"))return key.substring(5,key.length()-9);if(key.startsWith("move")&&key.endsWith("Particles"))return key.substring(4,key.length()-9);if(key.startsWith("sphere")&&key.endsWith("Particles"))return key.substring(6,key.length()-9);return null;}
    private SignalRules signalRules;
    public SignalRules signalRules(){if(signalRules==null)signalRules=new SignalRules(type);return signalRules;}
    private final HealthPhaseRules healthPhaseRules=new HealthPhaseRules();
    public HealthPhaseRules healthPhaseRules(){return healthPhaseRules;}
    private final ArmorRules armorRules=new ArmorRules();
    public ArmorRules armorRules(){return armorRules;}
    private final CommandProgram commands = new CommandProgram();
    public CommandProgram commands() { return commands; }
    private final MoveSet moveSet = new MoveSet();
    public MoveSet moveSet() { return moveSet; }
    private final List<PulseSequence.Step> sequence = new ArrayList<>();
    public List<PulseSequence.Step> sequence() { return List.copyOf(sequence); }
    public boolean editSequence(String key, int index, double value) {
        if (!type.equals("sequence") || !Double.isFinite(value)) return false;
        if (key.equals("add")) { if (sequence.size() >= PulseSequence.LIMIT) return false; sequence.add(new PulseSequence.Step(false, 1, 15, 20)); return true; }
        if (index < 0 || index >= sequence.size()) return false;
        var old = sequence.get(index);
        switch (key) {
            case "delete" -> sequence.remove(index);
            case "up", "down" -> { int next = index + (key.equals("up") ? -1 : 1); if (next < 0 || next >= sequence.size()) return false; java.util.Collections.swap(sequence, index, next); }
            case "mode" -> sequence.set(index, new PulseSequence.Step(value != 0, old.channel(), old.power(), old.ticks()));
            case "channel" -> sequence.set(index, new PulseSequence.Step(old.waiting(), Math.clamp((int) Math.round(value), 1, 65535), old.power(), old.ticks()));
            case "power" -> sequence.set(index, new PulseSequence.Step(old.waiting(), old.channel(), (int) Math.round(value), old.ticks()));
            case "time" -> sequence.set(index, new PulseSequence.Step(old.waiting(), old.channel(), old.power(), (int) Math.round(value * 20)));
            default -> { return false; }
        }
        return true;
    }
    private net.minecraft.world.phys.Vec3 gyroDown;
    public net.minecraft.world.phys.Vec3 gyroDown() { return gyroDown; }
    public boolean gyroDown(net.minecraft.world.phys.Vec3 value) {
        if (!type.equals("gyroscope")) return false;
        if (value == null) { gyroDown = null; return true; }
        double length = value.lengthSqr();
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z) || !Double.isFinite(length) || length < 1e-10) return false;
        gyroDown = value.normalize(); return true;
    }
    private net.minecraft.world.phys.Vec3 arenaCenter;
    private String arenaDimension = "";
    public net.minecraft.world.phys.Vec3 arenaCenter() { return arenaCenter; }
    public String arenaDimension() { return arenaDimension; }
    public void arenaCenter(net.minecraft.world.phys.Vec3 point, String dimension) {
        if (!List.of("arena","blueprint_spawn").contains(type) || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || Math.abs(point.x) > 30000000 || Math.abs(point.z) > 30000000 || Math.abs(point.y) > 30000000
                || net.minecraft.resources.ResourceLocation.tryParse(dimension) == null) return;
        arenaCenter = point; arenaDimension = dimension; invalidated = detached = false;
    }
    private final UUID id;
    private final String type;
    private String name = "";
    private String group="";
    public String group(){return group;}
    public boolean group(String value){if(!ModuleFolders.valid(value))return false;group=value.trim();return true;}
    private boolean enabled = true;
    private boolean invalidated;
    private boolean detached;
    private UUID rewardOwner;
    public UUID rewardOwner() { return rewardOwner; }
    public void rewardOwner(UUID owner) { rewardOwner = owner; }
    private final CompoundTag settings = new CompoundTag();
    private CompoundTag redBlue = new CompoundTag();
    public net.minecraft.world.item.ItemStack redBlue(boolean blue, net.minecraft.core.HolderLookup.Provider registries) { return net.minecraft.world.item.ItemStack.parseOptional(registries,redBlue.getCompound(blue ? "Blue" : "Red")); }
    public void redBlue(boolean blue, net.minecraft.world.item.ItemStack sample, net.minecraft.core.HolderLookup.Provider registries) {
        if(!List.of("wireless_redstone","damage_accumulation","sensor","hit_counter").contains(type)) return;
        String key=blue ? "Blue" : "Red";
        if(sample.isEmpty()) redBlue.remove(key); else redBlue.put(key,sample.copyWithCount(1).save(registries));
    }
    public boolean hasRedBlue() { return !redBlue.isEmpty(); }
    private CompoundTag lootSamples = new CompoundTag();
    public boolean coreModule() { return SignalRules.supported(type)||type.equals("surface_field")||type.equals("armor")&&value("armorWholeBody")==1||List.of("bgm", "health_phase", "hit_counter", "health_adjustment", "damage_accumulation", "sequence", "move_set", "command").contains(type); }
    public boolean area() { return type.equals("melee") || type.equals("particles") && value("particlePattern") == 0; }
    private final List<String> particles = new ArrayList<>();
    public List<String> particles() { return List.copyOf(particles); }
    private final java.util.Map<String, ModuleParticles.Entry> particleProperties = new java.util.HashMap<>();
    public List<ModuleParticles.Entry> particleEntries() { return particles.stream().map(this::particleEntry).toList(); }
    public ModuleParticles.Entry particleEntry(String id) { return particleProperties.getOrDefault(id, new ModuleParticles.Entry(id, 0, 1F)); }
    public static boolean supportsParticles(String type){return List.of("grab","freeze","surface_field","particles","flame","quake","ground_strike","pull","laser","slash","arrow","gun","mortar","homing").contains(type);}
    public boolean editParticle(String operation, String particle) { return editParticle(operation, particle, 0); }
    public boolean editParticle(String operation, String particle, double value) {
        if (!supportsParticles(type) || !ModuleParticles.valid(particle)) return false;
        if (!Double.isFinite(value)) return false;
        if (operation.equals("remove")) { particleProperties.remove(particle); return particles.remove(particle); }
        if(particles.contains(particle)&&operation.equals("color")){
            if(!ModuleParticles.colorable(particle)||value!=Math.rint(value)||value< -1||value>0xFFFFFF)return false;
            var old=particleEntry(particle);particleProperties.put(particle,new ModuleParticles.Entry(particle,old.ticks(),old.size(),(int)value));return true;
        }
        if (particles.contains(particle) && (operation.equals("duration") || operation.equals("size"))) {
            var old = particleEntry(particle);
            particleProperties.put(particle, new ModuleParticles.Entry(particle,
                    operation.equals("duration") ? (int) Math.round((value <= 0 ? 0 : Math.clamp(value, .05, 60)) * 20) : old.ticks(),
                    operation.equals("size") ? (float) Math.clamp(value, .1, 4) : old.size(),old.color())); return true;
        }
        if (!operation.equals("add") || particles.contains(particle) || particles.size() >= ModuleParticles.LIMIT) return false;
        particles.add(particle); return true;
    }
    private final ArenaBuffs arenaBuffs=new ArenaBuffs();
    public ArenaBuffs arenaBuffs(){return arenaBuffs;}
    private final List<ModuleEffects.Entry> effects = new ArrayList<>();
    public List<ModuleEffects.Entry> effects() { return List.copyOf(effects); }
    public boolean editEffect(String operation, String id, double value) {
        if (!known() || !ModuleEffects.TYPES.contains(type) || !ModuleEffects.valid(id) || !Double.isFinite(value)) return false;
        int index = -1;
        for (int i = 0; i < effects.size(); i++) if (effects.get(i).id().equals(id)) { index = i; break; }
        if (operation.equals("add")) {
            if (index >= 0 || effects.size() >= ModuleEffects.LIMIT) return false;
            effects.add(new ModuleEffects.Entry(id, 60, 1));
        } else {
            if (index < 0) return false;
            var old = effects.get(index);
            switch (operation) {
                case "remove" -> effects.remove(index);
                case "duration" -> effects.set(index, new ModuleEffects.Entry(id, Math.clamp((int) Math.round(value * 20), 0, 1200), old.level()));
                case "level" -> effects.set(index, new ModuleEffects.Entry(id, old.ticks(), Math.clamp((int) Math.round(value), 1, 5)));
                default -> { return false; }
            }
        }
        importData.remove("LegacyEffect"); return true;
    }
    private CompoundTag projectileItem = new CompoundTag();
    public net.minecraft.world.item.ItemStack projectileItem(net.minecraft.core.HolderLookup.Provider registries) { return net.minecraft.world.item.ItemStack.parseOptional(registries, projectileItem); }
    public void projectileItem(net.minecraft.world.item.ItemStack sample, net.minecraft.core.HolderLookup.Provider registries) { projectileItem = sample.isEmpty() ? new CompoundTag() : (CompoundTag) sample.copyWithCount(1).save(registries); }
    private CompoundTag importData = new CompoundTag();
    private ModuleAnchor source;
    public ModuleAnchor source() { return source; }
    public void source(ModuleAnchor anchor) { source = anchor; }
    public CompoundTag importData() { return importData.copy(); }
    public void imported(ModuleAnchor anchor, CompoundTag data) { source = anchor; importData = data.copy(); }
    private int trigger, channel;
    private final List<ModuleAnchor> particlePointAnchors = new ArrayList<>();
    private final List<ModuleAnchor> anchors = new ArrayList<>();
    private CompoundTag unknown;
    public VirtualModule(String type) { this(UUID.randomUUID(), type); }
    private VirtualModule(UUID id, String type) {
        if(type.equals("health_phase"))healthPhaseRules.add();
        this.id = id; this.type = type;
        if(SignalRules.supported(type))signalRules().add();
        for(var f:ModuleFields.HURT_FIELDS) settings.putDouble(f.key(),f.initial());
        ModuleFields.forType(type).forEach(f -> {
            if(f.choices().equals(List.of("library")))settings.putString(f.key(), "");
            else settings.putDouble(f.key(), f.initial());
        });
        if(type.equals("mortar")||type.equals("ground_strike")){var child=new VirtualModule("particles");child.editParticle("add","minecraft:explosion");movementParticles.put("Impact",child);}
        if(type.equals("summoned_weapon")){
            projectileItem.putString("id","minecraft:dispenser");projectileItem.putInt("count",1);
            for(String stage:List.of("Spawn","End")){var child=new VirtualModule("particles");if(stage.equals("Spawn"))child.editParticle("add","sablelife:random_starburst");movementParticles.put(stage,child);}
            settings.putString("summonSpawnSound","minecraft:entity.firework_rocket.launch");
        }
        if(type.equals("surface_field"))particles.add("minecraft:effect");
        if(type.equals("flame"))particles.add("minecraft:flame");
        if (type.equals("linear_movement")) {
            trigger=2;
            for(String stage:List.of("Charge","Departure","Arrival")){var selection=new VirtualModule("particles");selection.editParticle("add",stage.equals("Charge")?"minecraft:portal":stage.equals("Departure")?"minecraft:poof":"minecraft:portal");movementParticles.put(stage,selection);}
            settings.putString("moveChargeSound","minecraft:block.respawn_anchor.charge");settings.putString("moveDepartureSound","minecraft:entity.enderman.teleport");settings.putString("moveArrivalSound","minecraft:entity.enderman.teleport");
        }
        if(type.equals("sphere_blast")){
            for(String stage:List.of("Charge","Blast")){var selection=new VirtualModule("particles");if(stage.equals("Charge")){selection.editParticle("add","minecraft:small_flame");selection.editParticle("add","minecraft:firework");}else selection.editParticle("add","minecraft:explosion");movementParticles.put(stage,selection);}
            settings.putString("chargeSound","minecraft:entity.warden.sonic_charge");settings.putString("blastSound","minecraft:entity.generic.explode");
        }
        if(type.equals("slash"))settings.putString("flightSound","minecraft:entity.player.attack.sweep");
        if(type.equals("grab")){editParticle("add","minecraft:crit");editParticle("duration","minecraft:crit",.6);}
        if(type.equals("freeze")){editParticle("add","minecraft:snowflake");editParticle("duration","minecraft:snowflake",.6);editParticle("size","minecraft:snowflake",.75);}
        if (type.equals("pulse")) {settings.putString("sound", "minecraft:entity.warden.sonic_boom");for(var stage:List.of("Center","Wave")){var child=new VirtualModule("particles");child.editParticle("add",stage.equals("Center")?"minecraft:sonic_boom":"minecraft:electric_spark");movementParticles.put(stage,child);}}
        if (type.equals("flame")) settings.putString("fireSound", "minecraft:item.firecharge.use");
        if (type.equals("arrow")) { settings.putString("fireSound", "minecraft:entity.arrow.shoot"); settings.putString("arrowLandSound", "minecraft:entity.arrow.hit"); }
        if (type.equals("laser")) settings.putString("fireSound", "minecraft:entity.warden.sonic_boom");
        if (type.equals("laser")) settings.putString("chargeSound", "minecraft:entity.warden.sonic_charge");
        if (type.equals("gun")) settings.putString("fireSound", "minecraft:entity.firework_rocket.blast");
        if (type.equals("homing")) { settings.putString("chargeSound", "minecraft:entity.firework_rocket.launch"); settings.putString("fireSound", "minecraft:entity.firework_rocket.shoot"); settings.putString("impactSound", "minecraft:entity.shulker_bullet.hit"); settings.putString("blastSound", "minecraft:entity.generic.explode"); }
        if (type.equals("mortar")) settings.putString("fireSound", "minecraft:entity.generic.explode");
        if (type.equals("bgm")) settings.putString("bgm", "");
        if(type.equals("pull")){settings.putString("warningSound","minecraft:block.beacon.activate");settings.putString("pullSound","minecraft:block.beacon.ambient");}
        if (type.equals("ground_strike")) settings.putString("impactSound", "minecraft:entity.generic.explode");
        if (type.equals("quake")) settings.putString("quakeSound", "minecraft:entity.generic.explode");
        if (type.equals("wireless_redstone")) trigger = 2;
        if (type.equals("trigger_sound")) { trigger = 1; }
        if (type.equals("sound")) {
            settings.putString("approach", "minecraft:entity.ender_dragon.growl"); settings.putString("hurt", "minecraft:entity.iron_golem.hurt");
            settings.putString("ambient", ""); settings.putString("contactSound", "");
            settings.putString("bgm", "");
        }
    }
    public UUID id() { return id; }
    public String type() { return type; }
    public String name() { return name; }
    public boolean enabled() { return enabled; }
    public boolean invalidated() { return invalidated; }
    public boolean detached() { return detached; }
    public void detach() { detached = true; }
    public void invalidate() { invalidated = true; }
    public boolean known() { return unknown == null && TYPES.contains(type); }
    private boolean commandApprovalRequired;
    public boolean commandApprovalRequired() { return type.equals("command") && commandApprovalRequired; }
    public void approveCommands() { commandApprovalRequired = false; }
    public record StoredAnchor(String group, int index, ModuleAnchor anchor) {}
    /** Both particle mark sets are persistent, even when only one set is currently selected. */
    public List<StoredAnchor> storedAnchors() {
        var result = new ArrayList<StoredAnchor>();
        if (source != null) result.add(new StoredAnchor("ImportSource", 0, source));
        for (int i = 0; i < anchors.size(); i++) result.add(new StoredAnchor("Anchors", i, anchors.get(i)));
        if (type.equals("particles")) for (int i = 0; i < particlePointAnchors.size(); i++)
            result.add(new StoredAnchor("ParticlePointAnchors", i, particlePointAnchors.get(i)));
        return List.copyOf(result);
    }
    public boolean replaceStoredAnchor(String group, int index, ModuleAnchor anchor) {
        if (group.equals("ImportSource")) { if (source == null) return false; source = anchor; return true; }
        var values = group.equals("Anchors") ? anchors : group.equals("ParticlePointAnchors") && type.equals("particles") ? particlePointAnchors : null;
        if (values == null || index < 0 || index >= values.size()) return false;
        if (type.equals("gyroscope") && !values.get(index).bodyId().equals(anchor.bodyId())) gyroDown = null;
        values.set(index, anchor); return true;
    }
    private List<ModuleAnchor> activeAnchors() { return type.equals("particles") && !area() ? particlePointAnchors : anchors; }
    public List<ModuleAnchor> anchors() { return List.copyOf(activeAnchors()); }
    public void clearMarks() { anchors(List.of()); arenaCenter=null; arenaDimension=""; }
    public void clearFixedPoint(){if(List.of("arena","blueprint_spawn").contains(type)){arenaCenter=null;arenaDimension="";invalidated=false;detached=false;}}
    public void anchors(List<ModuleAnchor> values) { if (type.equals("gyroscope") && !anchors().equals(values)) gyroDown = null; var active = activeAnchors(); active.clear(); active.addAll(values); invalidated = false; detached = false; }
    public CompoundTag lootSamples() { return lootSamples.copy(); }
    public void lootSamples(CompoundTag tag) { lootSamples = tag.copy(); importData.putBoolean("TableMode", false); }
    void importSound(String key, String value) { settings.putString(key, value); }
    private CompoundTag soundTimelines = new CompoundTag();
    public boolean hasSoundTimeline(String key) { return soundTimelines.contains(key); }
    public boolean soundKey(String key) { return ModuleFields.forType(type).stream().anyMatch(f -> f.key().equals(key) && f.choices().equals(List.of("library"))); }
    public dev.sablelife.sound.SoundTimeline soundTimeline(String key) {
        if (soundTimelines.contains(key)) return dev.sablelife.sound.SoundTimeline.load(soundTimelines.getCompound(key));
        double volume = 1, pitch = 1, radius = 32;
        if (type.equals("sound") || type.equals("trigger_sound")) { volume=value("volume");pitch=value("pitch");radius=Math.max(16,16*volume); }
        else if (key.equals("bgm")) { volume=value("bgmVolume");radius=value("bgmRadius"); }
        else if (ModuleFields.find(type,key+"Volume")!=null) { volume=value(key+"Volume");radius=value(key+"Radius"); }
        return dev.sablelife.sound.SoundTimeline.legacy(key,text(key),volume,pitch,radius);
    }
    public void soundTimeline(String key, dev.sablelife.sound.SoundTimeline timeline) { if(soundKey(key)) soundTimelines.put(key,timeline.save()); }
    public boolean hasSound(String key) { return soundTimeline(key).audible(); }
    /** A known preset has a known schema; do not retain unrelated hidden payload groups. */
    public void sanitizeImportedAuthoring() {
        var sounds=new CompoundTag();
        for(var field:ModuleFields.forType(type))if(field.choices().equals(List.of("library"))&&soundTimelines.contains(field.key()))
            sounds.put(field.key(),dev.sablelife.sound.SoundTimeline.load(soundTimelines.getCompound(field.key())).save());
        soundTimelines=sounds;
        if(!List.of("arrow","gun","mortar","homing","ground_strike","item_display","summoned_weapon").contains(type))projectileItem=new CompoundTag();
        if(!List.of("wireless_redstone","damage_accumulation","sensor","hit_counter").contains(type))redBlue=new CompoundTag();
        else {var frequencies=new CompoundTag();for(var key:List.of("Red","Blue"))if(redBlue.contains(key))frequencies.put(key,redBlue.get(key).copy());redBlue=frequencies;}
        if(!type.equals("loot"))lootSamples=new CompoundTag();
        else {var samples=new CompoundTag();for(int i=0;i<27;i++)for(var prefix:List.of("Slot","Chance","Weight")){var key=prefix+i;if(lootSamples.contains(key))samples.put(key,lootSamples.get(key).copy());}lootSamples=samples;}
        impactField(null);summonAttack(null);
        if(type.equals("hit_counter")){settings.putDouble("hitChannel",0);hitSource(null,0);}
        if(type.equals("wireless_redstone"))settings.putDouble("redstoneChannel",0);
        arenaBuffs.unbind();armorRules.unbind();healthPhaseRules.unbind();
        if(SignalRules.supported(type)){signalRules().unbind();if(type.equals("signal_logic"))settings.putDouble("logicChannel",0);}
        movementParticles.values().forEach(VirtualModule::sanitizeImportedAuthoring);
    }
    public UUID impactFieldId(){return settings.hasUUID("ImpactFieldId")?settings.getUUID("ImpactFieldId"):null;}
    public void impactField(UUID id){if(id==null)settings.remove("ImpactFieldId");else settings.putUUID("ImpactFieldId",id);}
    public UUID hitSourceId(){return settings.hasUUID("HitSourceId")?settings.getUUID("HitSourceId"):null;}
    public void hitSource(UUID id,int index){if(id==null)settings.remove("HitSourceId");else settings.putUUID("HitSourceId",id);settings.putDouble("hitSource",index);}
    public String text(String key) { return settings.getString(key); }
    /** Preserve the referenced signal when rows shift; a deleted reference stays unbound. */
    public boolean wirelessChannelDeleted(int index) {
        boolean changed = false;
        if (channel >= index) { channel = channel == index ? -1 : channel - 1; changed = true; }
        if(type.equals("hit_counter")){int output=(int)value("hitChannel");if(output>index){settings.putDouble("hitChannel",output==index+1?0:output-1);changed=true;}}
        if(type.equals("health_phase"))changed|=healthPhaseRules.channelDeleted(index);
        if(SignalRules.supported(type)){changed|=signalRules().channelDeleted(index);int c=(int)value("logicChannel");if(type.equals("signal_logic")&&c>index){settings.putDouble("logicChannel",c==index+1?0:c-1);changed=true;}}
        if(type.equals("navigation"))for(String direction:TargetNavigation.DIRECTIONS){String key="navigation"+direction+"Channel";int output=(int)value(key);if(output>index){settings.putDouble(key,output==index+1?0:output-1);changed=true;}}
        if(type.equals("signal_turn"))for(String direction:ModuleSignalTurn.DIRECTIONS){int output=(int)value(direction+"Channel");if(output>index){settings.putDouble(direction+"Channel",output==index+1?0:output-1);changed=true;}}
        if(type.equals("wireless_redstone")){int output=(int)value("redstoneChannel");if(output>index){settings.putDouble("redstoneChannel",output==index+1?0:output-1);changed=true;}}
        if(type.equals("sensor")){int output=(int)value("sensorChannel");if(output>index){settings.putDouble("sensorChannel",output==index+1?0:output-1);changed=true;}}
        if (type.equals("damage_accumulation")) {
            int output=(int)value("accumChannel");
            if(output>index){settings.putDouble("accumChannel",output==index+1?0:output-1);changed=true;}
        }
        if (type.equals("sequence")) {
            for (int i = 0; i < sequence.size(); i++) {
                var step = sequence.get(i); if (step.channel() <= index) continue;
                int next = step.channel() == index + 1 ? 0 : step.channel() - 1;
                sequence.set(i, new PulseSequence.Step(step.waiting(), next, step.power(), step.ticks())); changed = true;
            }
            int reset = (int) value("resetChannel");
            if (reset > index) { settings.putDouble("resetChannel", reset == index + 1 ? 0 : reset - 1); changed = true; }
        }
        changed|=arenaBuffs.channelDeleted(index);changed|=armorRules.channelDeleted(index);
        return changed;
    }
    public double value(String key) { if(type.equals("health_phase")&&HealthPhaseRules.FIELDS.contains(key))return healthPhaseRules.rows().isEmpty()?0:healthPhaseRules.rows().getFirst().value(key);if(type.equals("slash")&&List.of("slashRed","slashGreen","slashBlue").contains(key))key="slashColor"+key.substring(5);return switch (key) {
        case "trigger" -> trigger; case "channel" -> channel;
        case "enabled" -> enabled ? 1 : 0; default -> settings.getDouble(key);
    }; }
    public boolean edit(String key, double value, String text) {
        if (!known() || !Double.isFinite(value)) return false;
        if(type.equals("health_phase")&&HealthPhaseRules.FIELDS.contains(key)){if(healthPhaseRules.rows().isEmpty())healthPhaseRules.add();return healthPhaseRules.rows().getFirst().edit(key,value);}
        if(type.equals("wireless_redstone")&&key.equals("redstoneRampRange")){
            var pair=text.split("\\|",-1);if(pair.length!=2)return false;
            try{double from=Double.parseDouble(pair[0]),to=Double.parseDouble(pair[1]);
                if(!Double.isFinite(from)||!Double.isFinite(to)||from!=Math.rint(from)||to!=Math.rint(to)||from<0||from>15||to<0||to>15)return false;
                return edit("redstoneRampStart",from,"")&&edit("redstoneRampEnd",to,"");
            }catch(NumberFormatException invalid){return false;}
        }
        if(type.equals("armor")&&key.equals("armorWholeBody")&&value!=Math.rint(value))return false;
        if(type.equals("armor")&&List.of("damageModifier","reduction","armorWhen").contains(key))return false;
        if(type.equals("slash")&&List.of("slashRed","slashGreen","slashBlue").contains(key)){edit("slashColorRgb",1,"");key="slashColor"+key.substring(5);}
        switch (key) {
            case "name" -> {
                if (text.codePointCount(0, text.length()) > 40 || text.codePoints().anyMatch(Character::isISOControl)) return false;
                name = text.trim();
            }
            case "enabled" -> enabled = value != 0;
            case "trigger" -> { if (type.equals("loot")) return false; trigger = Math.clamp((int) value, 0, 5); if(trigger==4 && !MoveSet.TARGETS.contains(type)) trigger=5; }
            case "channel" -> { if (type.equals("loot") || trigger < 2) return false; channel = Math.clamp((int) value, 1, dev.sablelife.heart.HeartWireless.CHANNELS) - 1; }
            default -> {
                var field = ModuleFields.find(type, key); if (field == null) return false;
                if (key.equals("effect")) importData.remove("LegacyEffect");
                if (field.choices().equals(List.of("library"))) {
                    if (!dev.sablelife.sound.SoundLibrary.contains(text)) return false;
                    soundTimelines.remove(key); settings.putString(key, text); if (!text.isEmpty()) settings.putString(key + "Last", text);
                } else {
                    var color=ModuleColors.parent(type,key);
                    if(color!=null&&key.equals(color+"Rgb")&&!text.isEmpty()){String[] parts=text.split(",",-1);if(parts.length!=3)return false;double[] channels=new double[3];for(int i=0;i<3;i++){try{channels[i]=Double.parseDouble(parts[i]);}catch(NumberFormatException e){return false;}if(!Double.isFinite(channels[i])||channels[i]<0||channels[i]>255)return false;}String[] suffix={"Red","Green","Blue"};for(int i=0;i<3;i++)settings.putDouble(color+suffix[i],Math.round(channels[i]));settings.putDouble(color+"RgbSet",1);}
                    if(color!=null&&key.equals(color+"Rgb")&&value!=0&&settings.getDouble(color+"RgbSet")==0){var rgb=ModuleColors.preset(type,(int)value(color));settings.putDouble(color+"Red",Math.round(rgb.x*255));settings.putDouble(color+"Green",Math.round(rgb.y*255));settings.putDouble(color+"Blue",Math.round(rgb.z*255));settings.putDouble(color+"RgbSet",1);}
                    if(color!=null&&(key.endsWith("Red")||key.endsWith("Green")||key.endsWith("Blue")))settings.putDouble(color+"RgbSet",1);
                    double next = Math.clamp(value, field.min(), field.max());
                    if(color!=null)next=Math.round(next);
                    if (field.scale() == 20 || field.choice() || (type.equals("signal_turn")||type.equals("navigation"))&&key.endsWith("Channel") || List.of("slashCount","slashRed","slashGreen","slashBlue","arrows", "burn", "rolls", "radius", "innerRadius", "direction", "effectLevel", "density", "strikeRays", "strikeRandomCount", "beamHits", "sensorChannel", "sensorPower", "accumChannel", "accumPower", "phaseChannel", "phasePower", "resetChannel", "redstoneChannel", "redstonePower", "quakeWaves", "homingCount", "summonCount", "interactionSize", "meleeHitLimit","hitThreshold","hitSource","hitChannel","hitPower").contains(key)) next = Math.round(next);
                    if (key.equals("innerRadius")) next = Math.min(next, value("radius") - 1);
                    settings.putDouble(key, next);
                    if (key.equals("particlePattern")) { invalidated = false; detached = false; }
                    if (key.equals("radius") && type.equals("sensor")) settings.putDouble("innerRadius", Math.min(value("innerRadius"), next - 1));
                    if (key.equals("ambientMin")) settings.putDouble("ambientMax", Math.max(value("ambientMax"), next));
                    if (key.equals("ambientMax")) settings.putDouble("ambientMin", Math.min(value("ambientMin"), next));
                }
            }
        }
        return true;
    }
    public CompoundTag save() {
        if (unknown != null) return unknown.copy();
        var tag = new CompoundTag(); tag.putInt("ColorControlsVersion",1);tag.putInt("Version", 1); tag.putUUID("Id", id); tag.putString("Type", type);
        if (type.equals("gyroscope") && gyroDown != null) { var down = new CompoundTag(); down.putDouble("X",gyroDown.x); down.putDouble("Y",gyroDown.y); down.putDouble("Z",gyroDown.z); tag.put("GyroDown",down); }
        if(type.equals("health_phase"))tag.put("HealthPhaseRules",healthPhaseRules.save());
        if(SignalRules.supported(type))tag.put("SignalRules",signalRules().save());
        if(type.equals("armor"))tag.put("ArmorRules",armorRules.save());
        if(type.equals("arena"))tag.put("ArenaBuffGroups",arenaBuffs.save());
        if (arenaCenter != null) {
            var center = new CompoundTag(); center.putDouble("X", arenaCenter.x); center.putDouble("Y", arenaCenter.y); center.putDouble("Z", arenaCenter.z);
            center.putString("Dimension", arenaDimension); tag.put("ArenaCenter", center);
        }
        if (type.equals("move_set")) tag.put("Moves", moveSet.save());
        if (type.equals("command")) tag.put("Commands", commands.save());
        if (commandApprovalRequired) tag.putBoolean("CommandApprovalRequired", true);
        if(type.equals("summoned_weapon")||type.equals("linear_movement")||type.equals("sphere_blast")||type.equals("pulse")||type.equals("mortar")||type.equals("ground_strike")){var stages=new CompoundTag();movementParticles.forEach((stage,selection)->stages.put(stage,selection.save()));tag.put("MovementParticles",stages);}
        if (type.equals("homing")) tag.putInt("HomingFormationVersion", 4);
        tag.putString("Group",group);tag.putString("Name", name); tag.putBoolean("Enabled", enabled);
        tag.putBoolean("Invalidated", invalidated);
        tag.putBoolean("Detached", detached);
        if (rewardOwner != null) tag.putUUID("RewardOwner", rewardOwner);
        tag.put("SoundTimelines",soundTimelines.copy());
        tag.put("RedBlue",redBlue.copy());
        if(type.equals("flame"))tag.putInt("FlameParticleSelectionVersion",1);
        tag.put("ParticleProperties", ModuleParticles.saveEntries(particleEntries())); tag.put("Particles", ModuleParticles.save(particles)); tag.put("Effects", ModuleEffects.save(effects)); tag.put("ProjectileItem", projectileItem.copy()); tag.put("Settings", settings.copy()); tag.put("LootSamples", lootSamples.copy());
        var steps = new ListTag();
        for (var step : sequence) { var row = new CompoundTag(); row.putBoolean("Wait", step.waiting()); row.putInt("Channel", step.channel()); row.putInt("Power", step.power()); row.putInt("Ticks", step.ticks()); steps.add(row); }
        if (!steps.isEmpty()) tag.put("Sequence", steps);
        tag.put("ImportData", importData.copy()); if (source != null) tag.put("ImportSource", source.save());
        tag.putInt("Trigger", trigger); tag.putInt("Channel", channel);
        var list = new ListTag(); anchors().forEach(a -> list.add(a.save())); tag.put("Anchors", list);
        if (type.equals("particles")) {
            var areaMarks = new ListTag(); anchors.forEach(a -> areaMarks.add(a.save())); tag.put("ParticleAreaAnchors", areaMarks);
            var pointMarks = new ListTag(); particlePointAnchors.forEach(a -> pointMarks.add(a.save())); tag.put("ParticlePointAnchors", pointMarks);
        }
        return tag;
    }
    public static VirtualModule load(CompoundTag tag) {
        var module = new VirtualModule(tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(), tag.getString("Type"));
        if (!module.known() || tag.getInt("Version") > 1) { module.unknown = tag.copy(); module.enabled = false; return module; }
        module.group(tag.getString("Group"));module.edit("name", 0, tag.getString("Name")); module.enabled = tag.getBoolean("Enabled");
        module.invalidated = tag.getBoolean("Invalidated");
        module.detached = tag.getBoolean("Detached");
        module.commandApprovalRequired = tag.getBoolean("CommandApprovalRequired");
        module.rewardOwner = tag.hasUUID("RewardOwner") ? tag.getUUID("RewardOwner") : null;
        for (String key : List.of("damage", "knockback", "interval", "range")) {
            String nbt = Character.toUpperCase(key.charAt(0)) + key.substring(1);
            if (tag.contains(nbt)) module.edit(key, tag.getDouble(nbt), "");
        }
        if(SignalRules.supported(module.type)&&tag.contains("SignalRules"))module.signalRules().load(tag.getList("SignalRules",10));
        var saved = tag.getCompound("Settings");
        if(ModuleSurfaceField.impactAttack(module)&&saved.hasUUID("ImpactFieldId"))module.impactField(saved.getUUID("ImpactFieldId"));
        if(module.type.equals("summoned_weapon")&&saved.hasUUID("SummonAttackId"))module.summonAttack(saved.getUUID("SummonAttackId"));
        if(module.type.equals("hit_counter")&&saved.hasUUID("HitSourceId"))module.settings.putUUID("HitSourceId",saved.getUUID("HitSourceId"));
        if(module.type.equals("health_phase")){
            if(tag.contains("HealthPhaseRules",9))module.healthPhaseRules.load(tag.getList("HealthPhaseRules",10));
            else for(var key:HealthPhaseRules.FIELDS)if(saved.contains(key))module.edit(key,saved.getDouble(key),"");
        }
        for(var f:ModuleFields.HURT_FIELDS) if(saved.contains(f.key())) module.edit(f.key(),saved.getDouble(f.key()),"");
        for (var field : ModuleFields.forType(module.type)) if (saved.contains(field.key())) {
            if (field.choices().equals(List.of("library"))) module.settings.putString(field.key(), saved.getString(field.key()));
            else module.edit(field.key(), saved.getDouble(field.key()), "");
        }
        if(module.type.equals("slash")&&tag.getInt("ColorControlsVersion")==0&&saved.contains("slashRed")){module.settings.putDouble("slashColorRgb",1);module.settings.putDouble("slashColorRgbSet",1);for(var channel:List.of("Red","Green","Blue"))module.settings.putDouble("slashColor"+channel,Math.clamp(Math.round(saved.getDouble("slash"+channel)),0,255));}
        for (var field : ModuleFields.forType(module.type)) if (field.choices().equals(List.of("library")) && saved.contains(field.key() + "Last")) module.settings.putString(field.key() + "Last", saved.getString(field.key() + "Last"));
        if (module.type.equals("homing")) {
            if (tag.getInt("HomingFormationVersion") < 3) module.edit("homingPlane", tag.getInt("HomingFormationVersion") == 2 && saved.getDouble("homingPlane") >= 2 ? 1 : 0, "");
            if (tag.getInt("HomingFormationVersion") < 4) {
                int old=(int)module.value("homingPlane");
                module.edit("homingPlane", old==2 ? 3 : old==3 ? 5 : old>=4 ? old+2 : old, "");
            }
            for (String suffix : List.of("", "Last")) {
                if (module.text("chargeSound" + suffix).equals("minecraft:entity.shulker.ambient")) module.settings.putString("chargeSound" + suffix,"minecraft:entity.firework_rocket.launch");
                if (module.text("fireSound" + suffix).equals("minecraft:entity.shulker.shoot")) module.settings.putString("fireSound" + suffix,"minecraft:entity.firework_rocket.shoot");
            }
        }
        if(module.type.equals("ground_strike")&&!saved.contains("strikeItemSize"))module.edit("strikeItemSize",Math.clamp(module.value("projectileHitbox"),.1,32),"");
        if (module.type.equals("gyroscope") && tag.contains("GyroDown")) { var down=tag.getCompound("GyroDown"); module.gyroDown(new net.minecraft.world.phys.Vec3(down.getDouble("X"),down.getDouble("Y"),down.getDouble("Z"))); }
        if (tag.contains("ArenaCenter")) { var center = tag.getCompound("ArenaCenter");
            module.arenaCenter(new net.minecraft.world.phys.Vec3(center.getDouble("X"), center.getDouble("Y"), center.getDouble("Z")), center.getString("Dimension"));
        }
        module.projectileItem = tag.getCompound("ProjectileItem").copy();
        module.redBlue=tag.getCompound("RedBlue").copy();
        module.soundTimelines=tag.getCompound("SoundTimelines").copy();
        if (module.type.equals("sequence")) {
            var steps = tag.getList("Sequence", 10);
            for (int i = 0; i < Math.min(steps.size(), PulseSequence.LIMIT); i++) { var row = steps.getCompound(i); module.sequence.add(new PulseSequence.Step(row.getBoolean("Wait"), row.getInt("Channel"), row.getInt("Power"), row.getInt("Ticks"))); }
        }
        if (module.type.equals("move_set")) module.moveSet.load(tag.getList("Moves", 10));
        if (module.type.equals("command")) module.commands.load(tag.getList("Commands", 10));
        if(module.type.equals("summoned_weapon")||module.type.equals("linear_movement")||module.type.equals("sphere_blast")||module.type.equals("pulse")||module.type.equals("mortar")||module.type.equals("ground_strike")){var stages=tag.getCompound("MovementParticles");for(String stage:module.type.equals("summoned_weapon")?List.of("Spawn","End"):module.type.equals("mortar")||module.type.equals("ground_strike")?List.of("Impact"):module.type.equals("pulse")?List.of("Center","Wave"):module.type.equals("sphere_blast")?List.of("Charge","Blast"):List.of("Charge","Departure","Arrival"))if(stages.contains(stage)){var child=stages.getCompound(stage).copy();child.putString("Type","particles");child.remove("MovementParticles");module.movementParticles.put(stage,VirtualModule.load(child));}}
        module.lootSamples = tag.getCompound("LootSamples").copy();
        module.importData = tag.getCompound("ImportData").copy();
        if(module.type.equals("flame")||module.type.equals("surface_field"))module.particles.clear();
        if(module.type.equals("freeze")||module.type.equals("grab")){module.particles.clear();module.particleProperties.clear();}
        module.particles.addAll(ModuleParticles.load(tag.getList("Particles", 8)));
        for (var e : ModuleParticles.loadEntries(tag.getList("ParticleProperties", 10), module.particles)) module.particleProperties.put(e.id(), e);
        if(module.type.equals("flame")&&!tag.contains("FlameParticleSelectionVersion")){
            module.particles.clear();module.particleProperties.clear();
            String particle=saved.getDouble("flameVisual")==1?"minecraft:soul_fire_flame":"minecraft:flame";
            module.particles.add(particle);module.particleProperties.put(particle,new ModuleParticles.Entry(particle,0,saved.contains("flameParticleSize")?(float)saved.getDouble("flameParticleSize"):1F));
        }
        if(module.type.equals("armor"))module.armorRules.load(tag.getList("ArmorRules",10));
        if(module.type.equals("arena"))module.arenaBuffs.load(tag.getList("ArenaBuffGroups",10));
        if (tag.contains("Effects")) module.effects.addAll(ModuleEffects.load(tag.getList("Effects", 10)));
        else if (module.type.equals("melee")) {
            var legacyChoices = List.of("none", "slowness", "weakness", "poison", "wither", "blindness", "mining_fatigue", "hunger", "glowing", "levitation");
            int oldIndex = Math.clamp((int) saved.getDouble("effect"), 0, legacyChoices.size() - 1);
            String effect = module.importData.getString("LegacyEffect");
            if (effect.isEmpty() && oldIndex > 0) effect = "minecraft:" + legacyChoices.get(oldIndex);
            if (ModuleEffects.valid(effect)) module.effects.add(new ModuleEffects.Entry(effect,
                    saved.contains("effectTicks") ? Math.clamp((int) saved.getDouble("effectTicks"), 0, 1200) : 60,
                    saved.contains("effectLevel") ? Math.clamp((int) saved.getDouble("effectLevel"), 1, 5) : 1));
        }
        if (tag.contains("ImportSource")) module.source = ModuleAnchor.load(tag.getCompound("ImportSource"));
        module.edit("trigger", tag.getInt("Trigger"), ""); module.channel = Math.clamp(tag.getInt("Channel"), -1, dev.sablelife.heart.HeartWireless.CHANNELS - 1);
        var list = tag.getList("Anchors", 10);
        for (int i = 0; i < Math.min(2, list.size()); i++) {
            var anchor = ModuleAnchor.load(list.getCompound(i)); if (anchor != null) module.activeAnchors().add(anchor);
        }
        if (module.type.equals("particles") && tag.contains("ParticleAreaAnchors")) {
            module.anchors.clear(); module.particlePointAnchors.clear();
            for (String key : List.of("ParticleAreaAnchors", "ParticlePointAnchors")) {
                var marks = tag.getList(key, 10); var target = key.equals("ParticleAreaAnchors") ? module.anchors : module.particlePointAnchors;
                for (int i = 0; i < Math.min(key.equals("ParticleAreaAnchors") ? 2 : 1, marks.size()); i++) {
                    var anchor = ModuleAnchor.load(marks.getCompound(i)); if (anchor != null) target.add(anchor);
                }
            }
        }
        // Editing the loaded pattern must not erase saved destruction/runtime-detachment state.
        if (module.type.equals("particles")) { module.invalidated = tag.getBoolean("Invalidated"); module.detached = tag.getBoolean("Detached"); }
        return module;
    }
}
