package dev.sablelife.module;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/** Persistent authoring settings shared by the dashboard and validated server runtime. */
public final class VirtualModule {
    public static final List<String> TYPES = List.of("move_set", "health_phase", "damage_accumulation", "sequence", "wireless_redstone", "command", "linear_movement", "auto_facing", "signal_turn", "sensor", "navigation", "arena", "armor", "melee", "flame", "arrow", "gun", "mortar", "homing", "laser", "sphere_blast", "slash", "ground_strike", "quake", "pull", "pulse", "particles", "item_display", "trigger_sound", "sound", "bgm", "loot");
    /** New-module catalog grouped by sidebar color: signal, weapon, function. */
    public static final List<String> AVAILABLE_TYPES = List.of(
            "move_set", "health_phase", "damage_accumulation", "sequence", "wireless_redstone", "command", "sensor", "navigation",
            "melee", "flame", "arrow", "gun", "mortar", "homing", "laser", "sphere_blast", "slash", "ground_strike", "quake", "pull", "pulse",
            "arena", "armor", "linear_movement", "auto_facing", "signal_turn", "particles", "item_display", "trigger_sound", "sound", "bgm", "loot");
    private final java.util.Map<String,VirtualModule> movementParticles = new java.util.HashMap<>();
    public VirtualModule movementParticles(String stage) { return movementParticles.get(stage); }
    public static String particleStage(String key){if(key.startsWith("move")&&key.endsWith("Particles"))return key.substring(4,key.length()-9);if(key.startsWith("sphere")&&key.endsWith("Particles"))return key.substring(6,key.length()-9);return null;}
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
    private net.minecraft.world.phys.Vec3 arenaCenter;
    private String arenaDimension = "";
    public net.minecraft.world.phys.Vec3 arenaCenter() { return arenaCenter; }
    public String arenaDimension() { return arenaDimension; }
    public void arenaCenter(net.minecraft.world.phys.Vec3 point, String dimension) {
        if (!type.equals("arena") || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || Math.abs(point.x) > 30000000 || Math.abs(point.z) > 30000000 || Math.abs(point.y) > 30000000
                || net.minecraft.resources.ResourceLocation.tryParse(dimension) == null) return;
        arenaCenter = point; arenaDimension = dimension; invalidated = detached = false;
    }
    private final UUID id;
    private final String type;
    private String name = "";
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
        if(!List.of("wireless_redstone","damage_accumulation").contains(type)) return;
        String key=blue ? "Blue" : "Red";
        if(sample.isEmpty()) redBlue.remove(key); else redBlue.put(key,sample.copyWithCount(1).save(registries));
    }
    public boolean hasRedBlue() { return !redBlue.isEmpty(); }
    private CompoundTag lootSamples = new CompoundTag();
    public boolean coreModule() { return List.of("bgm", "health_phase", "damage_accumulation", "sequence", "move_set", "command").contains(type); }
    public boolean area() { return type.equals("melee") || type.equals("particles") && value("particlePattern") == 0; }
    private final List<String> particles = new ArrayList<>();
    public List<String> particles() { return List.copyOf(particles); }
    private final java.util.Map<String, ModuleParticles.Entry> particleProperties = new java.util.HashMap<>();
    public List<ModuleParticles.Entry> particleEntries() { return particles.stream().map(this::particleEntry).toList(); }
    public ModuleParticles.Entry particleEntry(String id) { return particleProperties.getOrDefault(id, new ModuleParticles.Entry(id, 0, 1F)); }
    public boolean editParticle(String operation, String particle) { return editParticle(operation, particle, 0); }
    public boolean editParticle(String operation, String particle, double value) {
        if (!(type.equals("particles") || type.equals("flame") || type.equals("quake") || type.equals("ground_strike") || type.equals("pull") || type.equals("laser") || type.equals("slash") || List.of("arrow", "gun", "mortar", "homing").contains(type)) || !ModuleParticles.valid(particle)) return false;
        if (!Double.isFinite(value)) return false;
        if (operation.equals("remove")) { particleProperties.remove(particle); return particles.remove(particle); }
        if (particles.contains(particle) && (operation.equals("duration") || operation.equals("size"))) {
            var old = particleEntry(particle);
            particleProperties.put(particle, new ModuleParticles.Entry(particle,
                    operation.equals("duration") ? (int) Math.round((value <= 0 ? 0 : Math.clamp(value, .05, 60)) * 20) : old.ticks(),
                    operation.equals("size") ? (float) Math.clamp(value, .1, 4) : old.size())); return true;
        }
        if (!operation.equals("add") || particles.contains(particle) || particles.size() >= ModuleParticles.LIMIT) return false;
        particles.add(particle); return true;
    }
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
        this.id = id; this.type = type;
        for(var f:ModuleFields.HURT_FIELDS) settings.putDouble(f.key(),f.initial());
        ModuleFields.forType(type).forEach(f -> settings.putDouble(f.key(), f.initial()));
        if(type.equals("flame"))particles.add("minecraft:flame");
        if (type.equals("linear_movement")) {
            trigger=1;
            for(String stage:List.of("Charge","Departure","Arrival")){var selection=new VirtualModule("particles");selection.editParticle("add",stage.equals("Charge")?"minecraft:portal":stage.equals("Departure")?"minecraft:poof":"minecraft:portal");movementParticles.put(stage,selection);}
            settings.putString("moveChargeSound","minecraft:block.respawn_anchor.charge");settings.putString("moveDepartureSound","minecraft:entity.enderman.teleport");settings.putString("moveArrivalSound","minecraft:entity.enderman.teleport");
        }
        if(type.equals("sphere_blast")){
            for(String stage:List.of("Charge","Blast")){var selection=new VirtualModule("particles");if(stage.equals("Charge")){selection.editParticle("add","minecraft:small_flame");selection.editParticle("add","minecraft:firework");}else selection.editParticle("add","minecraft:explosion");movementParticles.put(stage,selection);}
            settings.putString("chargeSound","minecraft:entity.warden.sonic_charge");settings.putString("blastSound","minecraft:entity.generic.explode");
        }
        if(type.equals("slash"))settings.putString("flightSound","minecraft:entity.player.attack.sweep");
        if (type.equals("pulse")) settings.putString("sound", "minecraft:entity.warden.sonic_boom");
        if (type.equals("flame")) settings.putString("fireSound", "minecraft:item.firecharge.use");
        if (type.equals("arrow")) settings.putString("fireSound", "minecraft:entity.arrow.shoot");
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
        if (type.equals("trigger_sound")) { settings.putString("triggerSound", "minecraft:entity.ravager.roar"); trigger = 1; }
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
    private List<ModuleAnchor> activeAnchors() { return type.equals("particles") && !area() ? particlePointAnchors : anchors; }
    public List<ModuleAnchor> anchors() { return List.copyOf(activeAnchors()); }
    public void clearMarks() { anchors(List.of()); arenaCenter=null; arenaDimension=""; }
    public void anchors(List<ModuleAnchor> values) { var active = activeAnchors(); active.clear(); active.addAll(values); invalidated = false; detached = false; }
    public CompoundTag lootSamples() { return lootSamples.copy(); }
    public void lootSamples(CompoundTag tag) { lootSamples = tag.copy(); importData.putBoolean("TableMode", false); }
    void importSound(String key, String value) { settings.putString(key, value); }
    private CompoundTag soundTimelines = new CompoundTag();
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
    public String text(String key) { return settings.getString(key); }
    /** Preserve the referenced signal when rows shift; a deleted reference stays unbound. */
    public boolean wirelessChannelDeleted(int index) {
        boolean changed = false;
        if (channel >= index) { channel = channel == index ? -1 : channel - 1; changed = true; }
        if (type.equals("health_phase")) {
            int output = (int) value("phaseChannel");
            if (output > index) { settings.putDouble("phaseChannel", output == index + 1 ? 0 : output - 1); changed = true; }
        }
        if(type.equals("signal_turn"))for(String direction:ModuleSignalTurn.DIRECTIONS){int output=(int)value(direction+"Channel");if(output>index){settings.putDouble(direction+"Channel",output==index+1?0:output-1);changed=true;}}
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
        return changed;
    }
    public double value(String key) { if(type.equals("slash")&&List.of("slashRed","slashGreen","slashBlue").contains(key))key="slashColor"+key.substring(5);return switch (key) {
        case "trigger" -> trigger; case "channel" -> channel;
        case "enabled" -> enabled ? 1 : 0; default -> settings.getDouble(key);
    }; }
    public boolean edit(String key, double value, String text) {
        if (!known() || !Double.isFinite(value)) return false;
        // Legacy callers use positive reduction; the editor now uses positive amplification.
        if (type.equals("armor") && key.equals("reduction")) {
            if (value < 0 || value > 100) return false;
            key = "damageModifier"; value = -value;
        }
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
                    if (field.scale() == 20 || field.choice() || type.equals("signal_turn")&&key.endsWith("Channel") || List.of("slashCount","slashRed","slashGreen","slashBlue","arrows", "burn", "rolls", "radius", "innerRadius", "direction", "effectLevel", "density", "strikeRays", "strikeRandomCount", "beamHits", "accumChannel", "accumPower", "phaseChannel", "phasePower", "resetChannel", "redstonePower", "quakeWaves", "homingCount", "meleeHitLimit").contains(key)) next = Math.round(next);
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
        if (arenaCenter != null) {
            var center = new CompoundTag(); center.putDouble("X", arenaCenter.x); center.putDouble("Y", arenaCenter.y); center.putDouble("Z", arenaCenter.z);
            center.putString("Dimension", arenaDimension); tag.put("ArenaCenter", center);
        }
        if (type.equals("move_set")) tag.put("Moves", moveSet.save());
        if (type.equals("command")) tag.put("Commands", commands.save());
        if(type.equals("linear_movement")||type.equals("sphere_blast")){var stages=new CompoundTag();movementParticles.forEach((stage,selection)->stages.put(stage,selection.save()));tag.put("MovementParticles",stages);}
        if (type.equals("homing")) tag.putInt("HomingFormationVersion", 4);
        tag.putString("Name", name); tag.putBoolean("Enabled", enabled);
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
        module.edit("name", 0, tag.getString("Name")); module.enabled = tag.getBoolean("Enabled");
        module.invalidated = tag.getBoolean("Invalidated");
        module.detached = tag.getBoolean("Detached");
        module.rewardOwner = tag.hasUUID("RewardOwner") ? tag.getUUID("RewardOwner") : null;
        for (String key : List.of("damage", "knockback", "interval", "range")) {
            String nbt = Character.toUpperCase(key.charAt(0)) + key.substring(1);
            if (tag.contains(nbt)) module.edit(key, tag.getDouble(nbt), "");
        }
        var saved = tag.getCompound("Settings");
        // Migrate old worlds, copies and personal presets without reversing protection.
        if (module.type.equals("armor") && !saved.contains("damageModifier") && saved.contains("reduction"))
            module.edit("damageModifier", -Math.clamp(saved.getDouble("reduction"), 0, 100), "");
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
        if(module.type.equals("linear_movement")||module.type.equals("sphere_blast")){var stages=tag.getCompound("MovementParticles");for(String stage:module.type.equals("sphere_blast")?List.of("Charge","Blast"):List.of("Charge","Departure","Arrival"))if(stages.contains(stage)){var child=stages.getCompound(stage).copy();child.putString("Type","particles");child.remove("MovementParticles");module.movementParticles.put(stage,VirtualModule.load(child));}}
        module.lootSamples = tag.getCompound("LootSamples").copy();
        module.importData = tag.getCompound("ImportData").copy();
        if(module.type.equals("flame"))module.particles.clear();
        module.particles.addAll(ModuleParticles.load(tag.getList("Particles", 8)));
        for (var e : ModuleParticles.loadEntries(tag.getList("ParticleProperties", 10), module.particles)) module.particleProperties.put(e.id(), e);
        if(module.type.equals("flame")&&!tag.contains("FlameParticleSelectionVersion")){
            module.particles.clear();module.particleProperties.clear();
            String particle=saved.getDouble("flameVisual")==1?"minecraft:soul_fire_flame":"minecraft:flame";
            module.particles.add(particle);module.particleProperties.put(particle,new ModuleParticles.Entry(particle,0,saved.contains("flameParticleSize")?(float)saved.getDouble("flameParticleSize"):1F));
        }
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
