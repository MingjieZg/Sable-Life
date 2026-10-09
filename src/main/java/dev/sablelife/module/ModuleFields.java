package dev.sablelife.module;

import java.util.List;

/** Shared defaults and limits for current virtual modules. Scale converts ticks to UI seconds. */
public final class ModuleFields {
    public static boolean angle(String key){return List.of("launchAngle","deadZone","pitchDeadZone","dispatchCone","homingFan","homingRotation","homingVerticalRotation","moveHorizontal","moveVertical","meleeHorizontal","meleeVertical","slashHorizontal","slashVertical","slashFan","slashRoll","sweepAngle","facingSpeed").contains(key);}
    public record Field(String key, double min, double max, double initial, double scale, List<String> choices) {
        public boolean choice() { return !choices.isEmpty(); }
    }
    private static Field n(String key, double min, double max, double value) { return new Field(key, min, max, value, 1, List.of()); }
    private static Field t(String key, double min, double max, double value) { return new Field(key, min, max, value, 20, List.of()); }
    private static Field c(String key, String... values) { return new Field(key, 0, values.length - 1, 0, 1, List.of(values)); }
    private static Field target(int initial) { return new Field("target",0,ModuleTargets.CHOICES.size()-1,initial,1,ModuleTargets.CHOICES); }
    private static Field direction() { return c("direction", "marked", "down", "up", "north", "south", "west", "east"); }
    private static List<Field> explosive(List<Field> fields, boolean enabled) {
        var result = new java.util.ArrayList<>(fields);
        result.add(new Field("explode", 0, 1, enabled ? 1 : 0, 1, List.of("off", "on")));
        result.add(n("blastDamage", 0, 1000, 20)); result.add(n("blastRadius", .5, 16, 4)); result.add(c("blastBlocks", "off", "on"));
        return List.copyOf(result);
    }
    /** Conditions and rhythm belong to Move set, not to a module or wireless channel. */
    public static List<Field> moveRules(){return CACHE.computeIfAbsent("move_rules",ignored -> List.of(n("dispatchWeight",1,1000,10),t("dispatchCooldown",0,12000,100),t("dispatchBusy",1,12000,20),t("dispatchRest",1,12000,20),n("phaseUpper",0,100,100),n("phaseLower",0,100,0),target(2),n("dispatchNear",0,128,0),n("dispatchFar",.5,128,32),n("dispatchCone",0,360,360),c("dispatchFront","north","south","west","east"),c("lineOfSight","off","on"),c("dispatchRepeat","avoid_repeat","allow_repeat")));}
    /** Presentation order only: settings remain stored and addressed by their stable keys. */
    public static List<Field> forType(String type) {
        return VirtualModule.TYPES.contains(type) ? CACHE.computeIfAbsent(type, ModuleFields::buildForType) : List.of();
    }
    private static final java.util.Map<String,List<Field>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static List<Field> buildForType(String type) {
        List<String> order = switch (type) {
            case "damage_accumulation" -> List.of("accumThreshold","accumChannel","accumPower","accumPulse","accumInvert","redstoneFrequency","accumReset");
            case "health_phase" -> List.of("phaseUpper", "phaseLower", "phaseChannel", "phasePower", "phaseMode", "phasePulse");
            case "wireless_redstone" -> List.of("redstonePower", "redstoneMode", "redstonePulse", "redstoneInvert", "redstoneFaces", "redstoneFrequency");
        case "sequence" -> List.of("sequenceSteps", "sequenceRepeat", "stopOnLow", "resetChannel", "resetSource");
            case "arena" -> List.of("arenaRadius", "warningRange", "countdown", "arenaDeath", "arenaForceReturn", "arenaReturn", "arenaBuffEnabled", "arenaBuffGroups");
            case "quake" -> List.of("quakeMode", "quakeOrigin", "target", "targetMultiple", "quakeDetectRange", "quakeRange", "quakeWidth", "quakeSpeed", "warningTime", "damage", "quakeLaunch", "quakePush", "quakeRise", "quakeGrounded", "quakeReach", "quakeWaves", "quakeGap", "interval", "direction", "quakeColor", "warningSound", "quakeSound", "particles");
            case "ground_strike" -> List.of("strikeMode", "target", "targetMultiple", "strikeStart", "strikeRadius", "strikeRange", "strikeRays", "strikeRandomCount", "damage", "interval", "warningTime", "projectile", "projectileHitbox", "particles", "strikeSpeed", "strikeHeight", "strikeColor", "direction", "strikeStep", "waveDelay", "fallSound", "impactSound");
            case "laser" -> List.of("laserAim", "laserTracking", "laserFollow", "target", "targetMultiple", "beamLength", "laserBreakBlocks", "laserBreakMode", "laserBreakSpeed", "damage", "chargeTime", "beamHits", "hitInterval", "interval", "sweepAngle", "beamWidth", "direction", "beamColor", "particles", "chargeSound", "fireSound");
            case "slash" -> List.of("startDistance","slashHorizontal","slashVertical","direction","slashLength","slashWidth","range","slashSpeed","slashCount","slashFan","slashRoll","damage","target","interval","slashVisual","slashColor","particles","effects","flightSound");
            case "sphere_blast" -> List.of("releasePosition","target","castTargetRange","startDistance","sphereDistance","blastRadius","damage","chargeTime","sphereChargeSpeed","interval","direction","blastBlocks","sphereChargeParticles","chargeSound","sphereBlastParticles","blastSound","effects");
            case "armor" -> List.of("damageModifier", "armorWhen");
            case "melee" -> List.of("damage", "interval", "meleeHitLimit", "startDistance", "meleeHorizontal", "meleeVertical", "knockback", "effects");
            case "flame" -> List.of("direction", "range", "damage", "flameDirectHit", "interval", "flameSpeed", "particles", "effects", "width", "burn", "fireSound");
            case "arrow" -> List.of("projectile", "particles", "direction", "damage", "interval", "speed", "arrows", "projectileSize", "arrowHitbox", "arrowResidue", "endRadius", "explode", "blastDamage", "blastRadius", "blastBlocks", "effects", "fireSound");
            case "gun" -> List.of("projectile", "particles", "direction", "damage", "interval", "speed", "range", "arrows", "bulletHitbox", "endRadius", "explode", "blastDamage", "blastRadius", "blastBlocks", "effects", "fireSound");
            case "homing" -> List.of("projectile", "projectileHitbox", "homingCount", "homingOffset", "homingHeight", "homingFan", "homingPlane", "homingRotation", "homingVerticalRotation", "direction", "homingDeploy", "chargeTime", "target", "targetMultiple", "targetRange", "lineOfSight", "homingMode", "homingTracking", "speed", "homingLife", "damage", "explode", "blastDamage", "blastRadius", "blastBlocks", "blastSound", "interval", "particles", "effects", "chargeSound", "fireSound", "impactSound");
            case "mortar" -> List.of("projectile", "particles", "projectileHitbox", "damage", "aimMode", "target", "targetMultiple", "targetRange", "interval", "speed", "launchAngle", "direction", "explode", "blastDamage", "blastRadius", "mortarWarningColor", "blastBlocks", "effects", "fireSound");
            case "pull" -> List.of("releasePosition","target","castTargetRange", "pullRadius", "pullWarning", "pullDuration", "interval", "pullStrength", "pullSpeed", "pullOffset", "pullStop", "pullWarningColor", "pullCenterColor", "lineOfSight", "warningSound", "pullSound", "particles");
            case "pulse" -> List.of("pulseStart", "target", "radius", "knockback", "damage", "interval", "effects", "sound");
            case "item_display" -> List.of("displayItem","displaySize","displayX","displayY","displayZ","displayRotX","displayRotY","displayRotZ","displayAlways");
            case "particles" -> List.of("particlePattern", "particles", "startDistance", "direction", "particleRadius", "particleRingHeight", "particleLength", "particleDistribution", "particleFill", "particleLineMode", "particlePropagation", "particleSpeed", "interval", "density", "trail", "motionOnly", "drift");
            case "sensor" -> List.of("target", "shape", "radius", "lineOfSight", "direction", "innerRadius", "frontWidth");
            case "sound" -> List.of("approach", "hurt", "radius", "volume", "pitch", "cooldown", "ambient", "ambientMin", "ambientMax", "contactMode", "contactSound", "contactDirection", "contactDistance", "contactCooldown");
            case "trigger_sound" -> List.of("triggerSound", "soundMode", "volume", "pitch", "cooldown", "interval");
            case "bgm" -> List.of("bgm", "bgmStart", "bgmRadius", "bgmVolume", "bgmFade");
            case "navigation" -> List.of("target", "radius", "navigationFront", "verticalTracking", "deadZone", "pitchDeadZone", "targetLock", "lineOfSight", "navigationInvert");
            case "loot" -> List.of("weighted", "rolls");
            default -> List.of();
        };
        var fields = new java.util.ArrayList<>(definitions(type));
        if (List.of("melee", "flame", "arrow", "gun", "mortar", "homing", "laser", "sphere_blast", "slash", "quake", "pull", "pulse").contains(type)) {
            fields.add(n("startDistance", 0, 128, 0));
            var withStart = new java.util.ArrayList<>(order);
            if(!withStart.contains("startDistance"))withStart.add(Math.min(3, withStart.size()), "startDistance");
            order = List.copyOf(withStart);
        }
        final var presentationOrder = order;
        if (List.of("arrow", "gun", "mortar").contains(type)) fields.add(c("particles", "particles"));
        if (List.of("mortar", "ground_strike", "laser", "quake", "homing").contains(type)) fields.add(c("targetMultiple", "off", "on"));
        if (ModuleEffects.TYPES.contains(type)) fields.add(c("effects", "effects"));
        var arranged = fields.stream().sorted(java.util.Comparator.comparingInt(f -> { int i = presentationOrder.indexOf(f.key()); return i < 0 ? Integer.MAX_VALUE : i; })).toList();
        var result = new java.util.ArrayList<Field>();
        for (var f : arranged) {
            result.add(f);
            if(ModuleColors.color(type,f.key()))result.addAll(ModuleColors.fields(f.key(),ModuleColors.preset(type,(int)f.initial())));
            if (List.of("warningSound", "chargeSound", "fallSound", "quakeSound").contains(f.key()) || type.equals("homing") && List.of("fireSound", "impactSound", "blastSound").contains(f.key())) {
                result.add(n(f.key() + "Volume", 0, 4, 1)); result.add(n(f.key() + "Radius", 1, 256, 32));
            }
        }
        return List.copyOf(result);
    }

    private static List<Field> turnFields(){var fields=new java.util.ArrayList<Field>();fields.add(n("turnSpeed",0,720,120));fields.add(new Field("turnCollision",0,1,1,1,List.of("off","on")));for(String direction:ModuleSignalTurn.DIRECTIONS){fields.add(c(direction+"Source","turn_internal","turn_external"));fields.add(n(direction+"Channel",0,65535,0));}return List.copyOf(fields);}

    private static List<Field> definitions(String type) { return switch (type) {
        case "item_display" -> List.of(c("displayItem","item"),n("displaySize",.05,32,1),n("displayX",-64,64,0),n("displayY",-64,64,0),n("displayZ",-64,64,0),n("displayRotX",-180,180,0),n("displayRotY",-180,180,0),n("displayRotZ",-180,180,0),c("displayAlways","off","on"));

        case "damage_accumulation" -> List.of(n("accumThreshold",1,1000000,100),n("accumChannel",0,65535,0),n("accumPower",1,15,15),t("accumPulse",1,12000,20),c("accumInvert","off","on"),c("redstoneFrequency","frequency"),c("accumReset","accum_reset"));
        case "health_phase" -> List.of(n("phaseUpper", 0, 100, 100), n("phaseLower", 0, 100, 70), n("phaseChannel", 0, 65535, 0), n("phasePower", 1, 15, 15), c("phaseMode", "phase_hold", "phase_enter"), t("phasePulse", 1, 12000, 20));
        case "wireless_redstone" -> List.of(n("redstonePower", 0, 15, 15), c("redstoneMode", "signal_hold", "signal_pulse"),
                t("redstonePulse", 1, 12000, 20), c("redstoneInvert", "off", "on"), c("redstoneFaces", "all_faces", "marked_face"),
                c("redstoneFrequency", "frequency"));
        case "move_set" -> List.of(c("moveSetEditor", "move_set_editor"));
        case "command" -> List.of(c("commandEditor", "command_editor"));
        case "signal_turn" -> turnFields();
        case "auto_facing" -> List.of(target(0),n("facingRange",1,256,64),n("facingSpeed",1,720,180),c("facingVertical","off","on"),new Field("facingCollision",0,1,1,1,List.of("off","on")),new Field("facingCreative",0,1,1,1,List.of("off","on")),c("lineOfSight","off","on"));
        case "slash" -> List.of(n("slashHorizontal",-180,180,0),n("slashVertical",-180,180,0),direction(),n("slashLength",.5,32,4),n("slashWidth",.1,8,.5),n("range",1,128,24),n("slashSpeed",1,80,16),n("slashCount",1,16,1),n("slashFan",0,180,60),n("slashRoll",-180,180,0),n("damage",0,1000,10),target(2),t("interval",1,12000,40),c("slashVisual","slash_color","slash_particles"),new Field("slashColor",0,6,5,1,List.of("strike_red","strike_orange","strike_blue","strike_purple","strike_green","strike_white","strike_yellow")),c("particles","particles"),c("flightSound","library"));
        case "sphere_blast" -> List.of(c("releasePosition","release_marked","release_target"),n("castTargetRange",1,256,32),n("sphereDistance",0,128,8),n("blastRadius",.5,16,4),target(2),n("damage",0,1000,20),t("chargeTime",0,1200,40),n("sphereChargeSpeed",.1,20,2),t("interval",1,12000,100),direction(),c("blastBlocks","off","on"),c("sphereChargeParticles","particles"),c("sphereBlastParticles","particles"),c("chargeSound","library"),c("blastSound","library"));
        case "linear_movement" -> List.of(c("linearMode","move_teleport","move_travel"),c("moveAim","move_fixed","move_target"),target(0),n("moveTargetRange",1,256,64),n("moveStopDistance",0,128,2),c("moveTargetVertical","off","on"),new Field("facingCreative",0,1,1,1,List.of("off","on")),n("moveDistance",.1,128,8),n("moveHorizontal",-180,180,0),n("moveVertical",-180,180,0),direction(),t("chargeTime",0,1200,20),n("travelSpeed",.1,40,4),c("moveObstruction","on","off"),c("moveForce","off","on"),t("interval",1,12000,100),
                c("moveChargeParticles","particles"),c("moveChargeSound","library"),n("moveChargeSoundVolume",0,4,1),n("moveChargeSoundRadius",1,256,32),
                c("moveDepartureParticles","particles"),c("moveDepartureSound","library"),n("moveDepartureSoundVolume",0,4,1),n("moveDepartureSoundRadius",1,256,32),
                c("moveArrivalParticles","particles"),c("moveArrivalSound","library"),n("moveArrivalSoundVolume",0,4,1),n("moveArrivalSoundRadius",1,256,32));
        case "sequence" -> List.of(c("sequenceSteps", "sequence_editor"), c("sequenceRepeat", "sequence_once", "sequence_repeat"), c("stopOnLow", "off", "on"), n("resetChannel", 0, 65535, 0), c("resetSource", "sequence_external", "sequence_internal"));
        case "arena" -> List.of(n("arenaRadius", 4, 256, 48), n("warningRange", 4, 256, 64), t("countdown", 20, 1200, 100), new Field("arenaDeath", 0, 1, 1, 1, List.of("off", "on")), c("arenaForceReturn", "off", "on"), c("arenaReturn", "off", "on"),c("arenaBuffEnabled","off","on"),c("arenaBuffGroups","arena_buff_editor"));
        case "quake" -> List.of(c("quakeMode", "quake_line", "quake_ring", "quake_area"), c("quakeOrigin", "marked", "quake_target_feet"),
                target(0), n("quakeDetectRange", 1, 128, 32), n("quakeRange", 1, 64, 16), n("quakeWidth", .5, 16, 3), n("quakeSpeed", .1, 4, .6),
                t("warningTime", 1, 1200, 30), n("damage", 0, 1000, 6), n("quakeLaunch", 0, 20, 3), n("quakePush", 0, 3, .5), n("quakeRise", 0, 4, .8),
                c("quakeGrounded", "on", "off"), n("quakeReach", .25, 8, 2), n("quakeWaves", 1, 8, 1), t("quakeGap", 1, 200, 12), t("interval", 1, 12000, 60),
                direction(), c("quakeColor", "strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow"),
                c("warningSound", "library"), c("quakeSound", "library"), c("particles", "particles"));
        case "ground_strike" -> List.of(n("strikeStart", 0, 128, 0), n("strikeRays", 4, 32, 8), n("strikeRandomCount", 1, 128, 16), c("projectile", "item"), n("projectileHitbox", .05, 2, .5), c("particles", "particles"), c("fallSound", "library"), c("impactSound", "library"), c("strikeMode", "strike_line", "strike_radial", "strike_target", "strike_random"), target(0),
                n("strikeRadius", .5, 16, 2), n("strikeRange", 1, 128, 24), n("damage", 0, 1000, 20), t("interval", 5, 12000, 80),
                t("warningTime", 5, 200, 20), n("strikeSpeed", .1, 8, 1), n("strikeHeight", 2, 64, 12),
                c("strikeColor", "strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow"),
                direction(), n("strikeStep", 1, 16, 3), t("waveDelay", 1, 100, 4));
        case "homing" -> explosive(List.of(c("projectile", "item"), n("projectileHitbox", .05, 2, .4), n("homingCount", 1, 32, 1),
                n("homingOffset", .5, 32, 3), n("homingHeight", -16, 32, 2), n("homingFan", 0, 180, 120), c("homingPlane", "homing_arc", "homing_line", "homing_star", "homing_star_vertical", "homing_circle", "homing_circle_vertical", "homing_backslash", "homing_slash"), n("homingRotation", -180, 180, 0), n("homingVerticalRotation", -180, 180, 0), direction(), t("homingDeploy", 0, 200, 20),
                t("chargeTime", 0, 1200, 40), target(0), n("targetRange", 1, 128, 32), c("lineOfSight", "off", "on"),
                c("homingMode", "homing_direct", "homing_shulker"), n("homingTracking", .01, 1, .5), n("speed", .05, 2, .3), t("homingLife", 1, 1200, 200), n("damage", 0, 1000, 6), t("interval", 1, 12000, 80),
                c("particles", "particles"), c("chargeSound", "library"), c("fireSound", "library"), c("impactSound", "library"), c("blastSound", "library")), false);
        case "laser" -> List.of(c("laserAim", "fixed", "laser_auto"), n("laserTracking", .1, 1, 1), n("laserFollow", .01, 1, .1), target(0),
                n("beamLength", 1, 128, 32), c("laserBreakBlocks","off","on"),c("laserBreakMode","laser_clear_line","laser_cut_forward"),n("laserBreakSpeed",.1,128,16), n("damage", .1, 1000, 6), t("chargeTime", 0, 1200, 40),
                n("beamHits", 1, 100, 5), t("hitInterval", 1, 200, 10), t("interval", 1, 12000, 60),
                n("beamWidth", .1, 4, .5), n("sweepAngle", -180, 180, 0), direction(), c("beamColor", "strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow"),
                c("particles", "particles"), c("chargeSound", "library"), c("fireSound", "library"));
        case "armor" -> List.of(n("damageModifier", -100, 1000, 0), c("armorWhen", "powered", "unpowered"));
        case "melee" -> List.of(n("meleeHorizontal", -180, 180, 0), n("meleeVertical", -90, 90, 0), n("meleeHitLimit", 0, 1024, 0), n("damage", .1, 1000, 6), n("knockback", 0, 100, 1), t("interval", 1, 12000, 20));
        case "flame" -> List.of(n("range", 1, 32, 6), n("damage", 1, 1000, 2), new Field("flameDirectHit", 0, 1, 1, 1, List.of("off", "on")), n("burn", 0, 30, 3), t("interval", 1, 100, 10), n("width", .25, 4, .75), direction(), c("fireSound", "library"), n("flameSpeed", .1, 4, .8), c("particles","particles"));
        case "arrow" -> explosive(List.of(c("projectile", "item"), n("projectileSize", .1, 8, 1), n("arrowHitbox", .05, 2, .25), t("arrowResidue", 0, 12000, 200), c("fireSound", "library"), n("damage", 1, 1000, 5), t("interval", 2, 200, 20), n("speed", .5, 5, 2), n("arrows", 1, 9, 1), n("endRadius", 0, 8, 1), direction()), false);
        case "gun" -> explosive(List.of(c("projectile", "item"), n("damage", 1, 1000, 5), t("interval", 1, 200, 10), n("speed", .5, 8, 4), n("arrows", 1, 9, 1), n("endRadius", 0, 8, 0), direction(), n("range", 1, 256, 64), c("fireSound", "library"), n("bulletHitbox", .05, 2, .2)), false);
        case "mortar" -> explosive(List.of(c("projectile", "item"), n("projectileHitbox", .05, 2, .15), n("damage", 0, 1000, 5), t("interval", 5, 1200, 60), n("speed", .5, 4, 2), n("launchAngle", 45, 85, 65), direction(), c("fireSound", "library"),
                c("aimMode", "fixed", "target_position"), target(0), n("targetRange", 1, 128, 64), c("mortarWarningColor", "strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow")), true);
        case "pull" -> List.of(c("releasePosition","release_marked","release_target"),n("castTargetRange",1,256,32),target(0), n("pullRadius", 1, 32, 8), t("pullWarning", 5, 200, 30),
                t("pullDuration", 1, 200, 40), t("interval", 1, 12000, 60), n("pullStrength", .01, 1, .12), n("pullSpeed", .05, 2, .5),
                n("pullOffset", 0, 8, 1), n("pullStop", .25, 4, .8),
                new Field("pullWarningColor", 0, 7, 1, 1, List.of("strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow", "pull_cyan")),
                new Field("pullCenterColor", 0, 7, 4, 1, List.of("strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow", "pull_cyan")), c("lineOfSight", "on", "off"), c("warningSound", "library"), c("pullSound", "library"), c("particles", "particles"));
        case "pulse" -> List.of(c("pulseStart", "periodic", "target_present"), target(2),
                n("radius", 1, 16, 5), n("damage", 1, 1000, 6), n("knockback", 0, 100, 1), t("interval", 5, 200, 40), c("sound", "library"));
        case "particles" -> List.of(c("particlePattern", "particle_area", "particle_point", "particle_line", "particle_ring", "particle_sphere"),
                n("startDistance", 0, 128, 0), direction(), n("particleRadius", .05, 32, 3), n("particleRingHeight",0,128,0), n("particleLength", .1, 128, 8),
                c("particleDistribution", "particle_random", "particle_even"), c("particleFill", "particle_inside", "particle_surface"),
                c("particleLineMode", "particle_line_place", "particle_line_spray"), c("particlePropagation", "particle_default", "particle_outward", "particle_inward"), n("particleSpeed", .01, 4, .25), c("particles", "particles"), t("interval", 1, 200, 2), n("density", 1, 32, 4), new Field("trail", 0, 1, 1, 1, List.of("off", "on")), c("motionOnly", "off", "on"), n("drift", 0, .5, 0));
        case "sensor" -> List.of(n("radius", 1, 32, 12), target(0), c("lineOfSight", "on", "off"), c("shape", "ring", "front"), n("innerRadius", 0, 31, 0), n("frontWidth", .5, 16, 3), direction());
        case "sound" -> List.of(c("approach", "library"), c("hurt", "library"), n("radius", 1, 64, 16), n("volume", 0, 4, 1), n("pitch", .5, 2, 1), t("cooldown", 2, 1200, 20),
                c("ambient", "library"), t("ambientMin", 20, 12000, 100), t("ambientMax", 20, 12000, 200),
                c("contactMode", "off", "on"), c("contactSound", "library"), n("contactDistance", .05, 4, .25), t("contactCooldown", 1, 200, 6),
                c("contactDirection", "down", "up", "north", "south", "west", "east"));
        case "trigger_sound" -> List.of(c("triggerSound", "library"), c("soundMode", "signal_once", "signal_repeat"),
                n("volume", 0, 4, 1), n("pitch", .5, 2, 1), t("cooldown", 0, 12000, 20), t("interval", 1, 12000, 60));
        case "bgm" -> List.of(c("bgm", "library"), c("bgmStart", "after_hit", "on_approach"), n("bgmRadius", 1, 256, 64), t("bgmFade", 0, 400, 60), n("bgmVolume", 0, 1, 1));
        case "navigation" -> List.of(target(0), n("radius", 1, 128, 32), c("lineOfSight", "on", "off"),
                new Field("verticalTracking", 0, 1, 1, 1, List.of("off", "on")), c("targetLock", "locked", "nearest"), n("deadZone", 0, 45, 3), n("pitchDeadZone", 0, 45, 3),
                c("navigationFront", "marked", "north", "south", "west", "east"), c("navigationInvert", "off", "on"));
        case "loot" -> List.of(c("weighted", "chance", "weighted"), n("rolls", 1, 27, 1));
        default -> List.of();
    }; }
    public static final List<Field> HURT_FIELDS = List.of(c("hurtMode", "hurt_once", "hurt_timed", "hurt_stay"), t("hurtCooldown", 0, 12000, 0), t("hurtDuration", 1, 12000, 100));
    private static final java.util.Map<String,java.util.Map<String,Field>> LOOKUP=new java.util.concurrent.ConcurrentHashMap<>();
    public static Field find(String type,String key){
        if(!type.equals("loot"))for(var f:HURT_FIELDS)if(f.key().equals(key))return f;
        if(!VirtualModule.TYPES.contains(type))return null;
        return LOOKUP.computeIfAbsent(type,t->{var fields=new java.util.LinkedHashMap<String,Field>();for(var f:forType(t))fields.putIfAbsent(f.key(),f);return java.util.Map.copyOf(fields);}).get(key);
    }
}
