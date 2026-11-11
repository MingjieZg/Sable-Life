package dev.sablelife.module;

import java.util.List;

/** Shared defaults and limits for current virtual modules. Scale converts ticks to UI seconds. */
public final class ModuleFields {
    public static boolean angle(String key){return List.of("projectileRotationX","projectileRotationY","projectileRotationZ","spawnYaw","spawnPitch","spawnRoll","flameFanAngle","shotHorizontal","shotVertical","strikeItemX","strikeItemY","strikeItemZ","fieldAngle","launchAngle","deadZone","pitchDeadZone","dispatchCone","homingFan","homingRotation","homingVerticalRotation","moveHorizontal","moveVertical","meleeHorizontal","meleeVertical","slashHorizontal","slashVertical","slashFan","slashRoll","sweepAngle","verticalSweepAngle","facingSpeed","gyroTilt","gyroSpeed").contains(key);}
    public record Field(String key, double min, double max, double initial, double scale, List<String> choices) {
        public boolean choice() { return !choices.isEmpty(); }
    }
    private static List<Field> impactVisualFields(List<Field> base){var result=new java.util.ArrayList<>(base);result.addAll(List.of(c("impactVisual","impact_default","impact_custom"),c("impactParticles","particles"),c("impactSpreadMode","impact_follow","impact_independent"),n("impactSpreadRadius",0,32,2),n("impactCount",1,128,24)));return List.copyOf(result);}
    private static Field n(String key, double min, double max, double value) { return new Field(key, min, max, value, 1, List.of()); }
    private static Field t(String key, double min, double max, double value) { return new Field(key, min, max, value, 20, List.of()); }
    private static Field c(String key, String... values) { return new Field(key, 0, values.length - 1, 0, 1, List.of(values)); }
    private static Field target(int initial) { return new Field("target",0,ModuleTargets.CHOICES.size()-1,initial,1,ModuleTargets.CHOICES); }
    private static Field ownerTarget(int initial){return new Field("target",0,4,initial,1,ModuleTargets.OWNER_CHOICES);}
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
        if (DatapackModules.isDatapackType(type)) {
            var entry = DatapackModules.entry(type);
            return entry == null ? List.of() : entry.fields();
        }
        var addon = ModuleTypes.find(type);
        if (addon != null) {
            return CACHE.computeIfAbsent(type, ignored -> addon.options().stream()
                    .map(option -> n("addon_" + option.key(), option.minimum(), option.maximum(), option.initial())).toList());
        }
        return VirtualModule.TYPES.contains(type) ? CACHE.computeIfAbsent(type, ModuleFields::buildForType) : List.of();
    }
    private static final java.util.Map<String,List<Field>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static List<Field> buildForType(String type) {
        List<String> order = switch (type) {
            case "damage_accumulation" -> List.of("accumMode","accumThreshold","accumWindow","accumRequired","accumRepeat","accumChannel","accumPower","accumPulse","accumInvert","redstoneFrequency","accumReset");
            case "gyroscope" -> List.of("gyroCalibrate","gyroTilt","gyroSpeed","gyroReturn","gyroCollision");
            case "signal_logic" -> List.of("logicMatch","signalRules","logicChannel","logicPower","logicMode","logicPulse");
            case "combat_event" -> List.of("signalRules");
            case "health_phase" -> List.of("healthPhaseRules");
            case "wireless_redstone" -> List.of("redstoneStrengthMode", "redstonePower", "redstoneRampStart", "redstoneRampEnd", "redstoneRampTime", "redstoneMode", "redstonePulse", "redstoneInvert", "redstoneFaces", "redstoneChannel", "redstoneFrequency");
        case "sequence" -> List.of("sequenceSteps", "sequenceRepeat", "stopOnLow", "resetChannel", "resetSource");
            case "arena" -> List.of("arenaRadius", "warningRange", "countdown", "arenaDeath", "arenaForceReturn", "arenaReturn", "arenaBuffEnabled", "arenaBuffGroups", "arenaRespawnEnabled", "arenaRespawnDelay", "arenaRespawnRecord", "arenaRespawnClear");
            case "quake" -> List.of("quakeMode", "quakeOrigin", "target", "targetMultiple", "quakeDetectRange", "quakeRange", "quakeWidth", "quakeSpeed", "warningTime", "damage", "quakeLaunch", "quakePush", "quakeRise", "quakeGrounded", "quakeReach", "quakeWaves", "quakeGap", "interval", "direction", "quakeColor", "warningSound", "quakeSound", "particles");
            case "ground_strike" -> List.of("strikeMode", "target", "targetMultiple", "strikeStart", "strikeRadius", "strikeRange", "strikeRays", "strikeRandomCount", "damage", "interval", "warningTime", "projectile", "projectileHitbox", "strikeItemSize", "strikeItemMode", "strikeItemX", "strikeItemY", "strikeItemZ", "particles", "strikeSpeed", "strikeHeight", "strikeColor", "direction", "strikeStep", "waveDelay", "fallSound", "impactSound", "impactVisual","impactParticles","impactSpreadMode","impactSpreadRadius","impactCount");
            case "laser" -> List.of("laserAim", "shotHorizontal", "shotVertical", "laserTracking", "laserFollow", "target", "targetMultiple", "beamLength", "laserBreakBlocks", "laserBreakMode", "laserBreakSpeed", "damage", "chargeTime", "beamHits", "hitInterval", "interval", "sweepAngle", "sweepSpeed", "verticalSweepAngle", "verticalSweepSpeed", "beamWidth", "direction", "laserVisualStyle", "beamColor", "particles", "laserParticleSpacing", "chargeSound", "fireSound");
            case "slash" -> List.of("shotAim","target","targetMultiple","targetRange","startDistance","slashHorizontal","slashVertical","direction","slashLength","slashWidth","range","slashSpeed","slashCount","slashFan","slashRoll","damage","slashBlockable","slashIgnite","burn","interval","slashVisual","slashColor","particles","effects","flightSound","slashFlightSound");
            case "sphere_blast" -> List.of("releasePosition","target","targetMultiple","castTargetRange","startDistance","sphereDistance","blastRadius","damage","chargeTime","sphereChargeSpeed","interval","direction","blastBlocks","sphereChargeParticles","chargeSound","sphereBlastParticles","blastSound","effects");
            case "armor" -> List.of("armorWholeBody","armorRules");
            case "melee" -> List.of("damage", "interval", "meleeHitLimit", "startDistance", "meleeLength", "meleeWidth", "meleeHeight", "meleeHorizontal", "meleeVertical", "knockback", "burn", "effects");
            case "surface_field" -> List.of("fieldRelease","target","fieldShape","fieldRadius","fieldLength","fieldWidth","fieldAngle","fieldPlacement","fieldDuration","damage","hitInterval","fieldBurn","burn","particles","density","effects");
            case "flame" -> List.of("direction", "shotHorizontal", "shotVertical", "range", "damage", "flameDirectHit", "interval", "flameSpeed", "particles", "effects", "flameShape", "flameFanAngle", "width", "burn", "fireSound");
            case "arrow" -> List.of("shotAim","target","targetMultiple","targetRange","startDistance", "projectile", "projectileRotationX", "projectileRotationY", "projectileRotationZ", "arrowFaceCamera", "particles", "direction", "shotHorizontal", "shotVertical", "damage", "interval", "speed", "arrows", "projectileSize", "arrowHitbox", "arrowResidue", "endRadius", "explode", "blastDamage", "blastRadius", "blastBlocks", "effects", "fireSound", "arrowLandSound");
            case "gun" -> List.of("shotAim","target","targetMultiple","targetRange","startDistance", "projectile", "projectileRotationX", "projectileRotationY", "projectileRotationZ", "particles", "direction", "shotHorizontal", "shotVertical", "damage", "interval", "speed", "range", "arrows", "bulletHitbox", "endRadius", "explode", "blastDamage", "blastRadius", "blastBlocks", "effects", "fireSound");
            case "homing" -> List.of("projectile", "projectileHitbox", "homingCount", "homingOffset", "homingHeight", "homingFan", "homingPlane", "homingRotation", "homingVerticalRotation", "direction", "homingDeploy", "chargeTime", "target", "targetMultiple", "targetRange", "lineOfSight", "homingMode", "homingTracking", "speed", "homingLife", "damage", "explode", "blastDamage", "blastRadius", "blastBlocks", "blastSound", "interval", "particles", "effects", "chargeSound", "fireSound", "impactSound");
            case "mortar" -> List.of("startDistance", "projectile", "projectileRotationX", "projectileRotationY", "projectileRotationZ", "particles", "projectileHitbox", "damage", "aimMode", "target", "targetMultiple", "targetRange", "interval", "speed", "launchAngle", "direction", "explode", "blastDamage", "blastRadius", "mortarWarningColor", "impactVisual","impactParticles","impactSpreadMode","impactSpreadRadius","impactCount", "blastBlocks", "effects", "fireSound");
            case "grab" -> List.of("grabTarget","grabRadius","grabWarning","grabDuration","startDistance","grabOffsetX","grabOffsetY","interval","particles","warningSound","grabSound");
            case "freeze" -> List.of("freezeTarget","releasePosition","targetMultiple","castTargetRange","direction","startDistance","fieldShape","freezeRadius","fieldLength","fieldWidth","freezeHeight","fieldAngle","fieldPlacement","freezeWarning","freezeDuration","interval","particles","warningSound","freezeSound");
        case "pull" -> List.of("releasePosition","target","targetMultiple","castTargetRange", "pullShape", "pullMotion", "startDistance", "direction", "shotHorizontal", "shotVertical", "pullLength", "pullStartRadius", "pullEndRadius", "pullRadius", "pullWarning", "pullDuration", "interval", "pullStrength", "pullSpeed", "pullStop", "pullWarningColor", "pullCenterColor", "lineOfSight", "warningSound", "pullSound", "particles");
            case "pulse" -> List.of("releasePosition","castTargetRange","targetMultiple","pulseStart", "target", "radius", "knockback", "damage", "interval", "pulseVisual", "pulseExpandTime", "pulseCenterParticles", "pulseWaveParticles", "effects", "sound");
            case "health_bar" -> List.of("healthBarStyle","healthBarWidth","healthBarHeight","startDistance","healthBarName","healthBarNameSize","healthBarNameGap","healthBarViewDistance","healthBarVisibility","healthBarHideDelay");
        case "item_display" -> List.of("displayItem","displaySize","displayX","displayY","displayZ","displayRotX","displayRotY","displayRotZ","displayAlways");
            case "particles" -> List.of("particlePattern", "particles", "particleExpandLength", "particleExpandWidth", "particleExpandHeight", "startDistance", "direction", "particleRadius", "particleRingHeight", "particleLength", "particleDistribution", "particleFill", "particleLineMode", "particlePropagation", "particleSpeed", "interval", "density", "trail", "motionOnly", "drift");
            case "sensor" -> List.of("target", "shape", "radius", "lineOfSight", "direction", "innerRadius", "frontWidth", "sensorChannel", "sensorPower", "sensorInvert", "redstoneFrequency");
            case "sound" -> List.of("approach", "hurt", "radius", "volume", "pitch", "cooldown", "ambient", "ambientMin", "ambientMax");
            case "trigger_sound" -> List.of("triggerSound", "soundMode", "volume", "pitch", "cooldown", "interval", "contactMode", "contactSound", "contactDirection", "contactDistance", "contactCooldown");
            case "bgm" -> List.of("bgm", "bgmStart", "bgmRadius", "bgmVolume", "bgmFade");
            case "navigation" -> List.of("target", "radius", "navigationFront", "verticalTracking", "deadZone", "pitchDeadZone", "targetLock", "lineOfSight", "navigationInvert", "navigationLeftChannel", "navigationRightChannel", "navigationUpChannel", "navigationDownChannel", "navigationForwardChannel", "navigationBackwardChannel");
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
        if(ModuleSurfaceField.IMPACT_ATTACKS.contains(type)){
            fields.add(c("impactArea","field_select"));
            var impactOrder=new java.util.ArrayList<>(order);impactOrder.add("impactArea");order=List.copyOf(impactOrder);
        }
        if (List.of("flame","arrow","gun","laser").contains(type)) fields.addAll(List.of(n("shotHorizontal",-180,180,0),n("shotVertical",-90,90,0)));
        final var presentationOrder = order;
        if (List.of("arrow", "gun", "mortar").contains(type)) fields.add(c("particles", "particles"));
        if (List.of("arrow","gun","slash").contains(type)) {
            fields.add(c("shotAim","shot_straight","target_position"));
            fields.add(n("targetRange",1,256,64));
            if(!type.equals("slash"))fields.add(target(2));
        }
        if (List.of("mortar", "ground_strike", "laser", "quake", "homing", "arrow", "gun", "slash", "sphere_blast", "pull", "freeze", "pulse").contains(type)) fields.add(c("targetMultiple", "off", "on"));
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
        case "distortion_trail" -> List.of(n("trailLength",.1,64,3),n("trailWidth",.1,64,1),n("trailHeight",.1,64,1),n("trailOffset",-64,64,0),n("trailThreshold",0,100,.5),t("trailLife",1,60,10),n("trailDistortion",0,1,.35),n("trailSpacing",.1,4,.35),n("trailRange",1,256,64));
        case "summoned_weapon" -> List.of(t("summonWindup",0,12000,0),c("projectile","item"),c("summonAttack","summon_select"),n("displaySize",.05,32,1),n("summonCount",1,32,1),c("summonFormation","homing_arc","homing_line","homing_star","homing_star_vertical","homing_circle","homing_circle_vertical","homing_backslash","homing_slash"),n("summonRadius",0,32,3),n("summonSpacing",0,32,2),n("summonFan",0,180,120),n("summonFormationRoll",-180,180,0),n("summonFormationYaw",-180,180,0),t("summonDeployGap",0,200,0),direction(),n("startDistance",0,64,2),n("displayX",-64,64,0),n("displayY",-64,64,0),n("displayZ",-64,64,0),c("summonPosition","summon_follow","summon_world"),c("summonAim","summon_fixed","summon_target"),target(2),n("targetRange",1,256,64),n("summonTurnSpeed",1,180,15),n("summonYaw",-180,180,0),n("summonPitch",-90,90,0),n("displayRotX",-180,180,0),n("displayRotY",-180,180,0),n("displayRotZ",-180,180,0),n("summonMuzzleX",-32,32,0),n("summonMuzzleY",-32,32,0),n("summonMuzzleZ",-32,32,0),t("summonDuration",1,12000,100),c("summonSpawnParticles","particles"),c("summonSpawnSound","library"),c("summonEndParticles","particles"),c("summonEndSound","library"));
        case "block_interaction" -> List.of(c("interactionSpace","interaction_world","interaction_structure"),c("interactionMode","interaction_once","interaction_repeat"),t("interactionInterval",1,1200,20),
                new Field("interactionSize",1,3,1,1,List.of()),n("startDistance",0,128,0),n("interactionOffsetX",-128,128,0),n("interactionOffsetY",-128,128,0),n("interactionOffsetZ",-128,128,0));
        case "health_bar" -> List.of(new Field("healthBarVisibility",0,1,1,1,List.of("health_bar_always","health_bar_on_damage")),t("healthBarHideDelay",2,12000,100),n("healthBarViewDistance",1,256,64),new Field("healthBarStyle",0,1,1,1,List.of("health_bar_rectangle","health_bar_capsule")),n("healthBarWidth",.25,16,1.6),n("healthBarHeight",.05,2,.1),n("startDistance",0,128,.75),new Field("healthBarName",0,1,1,1,List.of("off","on")),n("healthBarNameSize",.05,2,.2),n("healthBarNameGap",0,4,.08));
        case "item_display" -> List.of(c("displayItem","item"),n("displaySize",.05,32,1),n("displayX",-64,64,0),n("displayY",-64,64,0),n("displayZ",-64,64,0),n("displayRotX",-180,180,0),n("displayRotY",-180,180,0),n("displayRotZ",-180,180,0),c("displayAlways","off","on"));

        case "damage_accumulation" -> List.of(c("accumMode","accum_cumulative","accum_timed"),t("accumWindow",1,12000,100),n("accumRequired",1,1000000,200),c("accumRepeat","accum_once","accum_loop"),n("accumThreshold",1,1000000,100),n("accumChannel",0,65535,0),n("accumPower",1,15,15),t("accumPulse",1,12000,20),c("accumInvert","off","on"),c("redstoneFrequency","frequency"),c("accumReset","accum_reset"));
        case "signal_logic" -> List.of(c("logicMatch","signal_all","signal_any"),c("signalRules","signal_rule_editor"),n("logicChannel",0,65535,0),n("logicPower",1,15,15),c("logicMode","signal_hold","signal_pulse"),t("logicPulse",1,12000,10));
        case "combat_event" -> List.of(c("signalRules","signal_rule_editor"));
        case "health_phase" -> List.of(c("healthPhaseRules","phase_rule_editor"));
        case "wireless_redstone" -> List.of(c("redstoneStrengthMode","redstone_fixed","redstone_follow","redstone_ramp"),n("redstonePower", 0, 15, 15),n("redstoneRampStart",0,15,0),n("redstoneRampEnd",0,15,15),t("redstoneRampTime",1,12000,60), c("redstoneMode", "signal_hold", "signal_pulse"),
                t("redstonePulse", 1, 12000, 20), c("redstoneInvert", "off", "on"), new Field("redstoneFaces", 0, 1, 1, 1, List.of("all_faces", "marked_face")), n("redstoneChannel",0,65535,0),
                c("redstoneFrequency", "frequency"));
        case "entity_hitbox" -> List.of(new Field("hitboxDamageMode",0,1,1,1,List.of("hitbox_entity_only","hitbox_hybrid")),c("hitboxProvokeMobs","off","on"),n("hitboxWidth",.25,16,1),n("hitboxHeight",.25,16,1),n("hitboxDepth",.25,16,1),n("hitboxOffsetX",-16,16,0),n("hitboxOffsetY",-16,16,0),n("hitboxOffsetZ",-16,16,0));
        case "blueprint_spawn" -> List.of(c("blueprintEditor","blueprint_editor"),c("spawnPosition","spawn_fixed","spawn_follow"),c("spawnAnchor","spawn_base","spawn_center"),direction(),n("startDistance",0,128,0),n("spawnOffsetX",-128,128,0),n("spawnOffsetY",-128,128,0),n("spawnOffsetZ",-128,128,0),n("spawnYaw",-180,180,0),n("spawnPitch",-180,180,0),n("spawnRoll",-180,180,0),c("spawnEvent","spawn_signal","spawn_death"),c("spawnActivate","off","on"),t("spawnDelay",0,72000,20),t("spawnCooldown",1,72000,100),n("spawnLimit",1,64,1));
        case "move_set" -> List.of(c("moveSetEditor", "move_set_editor"),c("ignoreConditions","off","on"));
        case "command" -> List.of(c("commandEditor", "command_editor"));
        case "signal_turn" -> turnFields();
        case "hit_counter" -> List.of(n("hitThreshold",1,10000,5),n("hitSource",0,HeartModules.LIMIT,0),t("hitGap",0,1200,0),n("hitChannel",0,65535,0),n("hitPower",1,15,15),t("hitPulse",1,12000,20),c("redstoneFrequency","frequency"));
        case "surface_field" -> List.of(c("fieldRelease","field_impact"),target(2),c("fieldShape","field_circle","field_rectangle"),n("fieldRadius",.25,16,4),n("fieldLength",.25,32,8),n("fieldWidth",.25,32,2),n("fieldAngle",-180,180,0),c("fieldPlacement","field_centered","field_ahead"),t("fieldDuration",1,12000,160),n("damage",0,1000,3),t("hitInterval",1,12000,20),c("fieldBurn","off","on"),n("burn",0,30,3),c("particles","particles"),n("density",0,128,24));
        case "health_adjustment" -> List.of(n("healthAmount",-1000000,1000000,10),c("healthUnits","health_hp","health_percent"),c("healthMode","health_once","health_periodic"),t("healthInterval",1,12000,20),c("healthLethal","off","on"));
        case "visual_shockwave" -> List.of(c("filterEnabled","off","on"),new Field("filterColor",0,6,3,1,List.of("strike_red","strike_orange","strike_blue","strike_purple","strike_green","strike_white","strike_yellow")),n("filterStrength",0,1,.25),t("filterFadeIn",0,200,4),t("filterFadeOut",0,200,8),new Field("shockWaveEnabled",0,1,1,1,List.of("off","on")),c("shockPattern","shock_ring","shock_radial","visual_gather","visual_mechanical","visual_shield","visual_jet","visual_fissures","visual_echo","visual_symbols"),new Field("visualColor",0,6,2,1,List.of("strike_red","strike_orange","strike_blue","strike_purple","strike_green","strike_white","strike_yellow")),n("visualSize",.1,32,2),t("visualTime",1,1200,30),n("visualSpeed",.1,8,1),n("visualStart",0,128,.05),c("visualPlacement","visual_follow","visual_world"),c("visualSymbol","symbol_exclamation","symbol_question","symbol_double_exclamation","symbol_question_exclamation","symbol_plus","symbol_ellipsis"),n("shockRadialStrength",0,1,.35),t("shockRadialTime",1,200,12),n("shockRadialClear",0,50,8),new Field("shockShakeEnabled",0,1,1,1,List.of("off","on")),new Field("shockZoomEnabled",0,1,1,1,List.of("off","on")),c("shockContact","off","on"),c("contactDirection","down","up","north","south","west","east"),n("contactDistance",.05,4,.25),t("contactCooldown",1,200,6),n("shockRadius",.5,128,18),c("shockReverse","off","on"),t("shockExpansion",1,200,9),n("shockWidth",.1,16,1.5),n("shockDistortion",0,1,.35),n("shockBlur",0,1,.25),n("shockShake",0,1,.15),n("shockShakeFrequency",.1,2,1),t("shockShakeTime",1,200,8),n("shockZoom",-100,30,4),t("shockZoomTime",1,200,10),n("shockRange",1,256,64),c("shockMode","shock_once","shock_repeat"),t("shockInterval",1,12000,40));
        case "hover_anchor" -> List.of(new Field("hoverX",0,1,1,1,List.of("off","on")),new Field("hoverY",0,1,1,1,List.of("off","on")),new Field("hoverZ",0,1,1,1,List.of("off","on")),c("hoverRotation","off","on"),c("hoverMode","hover_hard","hover_elastic"),n("hoverStrength",0,1000,20),n("hoverDamping",0,100,0),n("hoverOffset",0,16,2));
        case "gyroscope" -> List.of(c("gyroCalibrate","gyro_record"),n("gyroTilt",0,180,20),n("gyroSpeed",.1,720,90),c("gyroReturn","gyro_level","gyro_limit"),new Field("gyroCollision",0,1,1,1,List.of("off","on")));
        case "auto_facing" -> List.of(ownerTarget(0),n("facingRange",1,256,64),n("facingSpeed",1,720,180),c("facingVertical","off","on"),new Field("facingCollision",0,1,1,1,List.of("off","on")),new Field("facingCreative",0,1,1,1,List.of("off","on")),c("lineOfSight","off","on"));
        case "slash" -> List.of(n("slashHorizontal",-180,180,0),n("slashVertical",-180,180,0),direction(),n("slashLength",.5,32,4),n("slashWidth",.1,8,.5),n("range",1,128,24),n("slashSpeed",1,80,16),n("slashCount",1,16,1),n("slashFan",0,180,60),n("slashRoll",-180,180,0),n("damage",0,1000,10),c("slashBlockable","off","on"),c("slashIgnite","off","on"),n("burn",0,30,3),target(2),t("interval",1,12000,40),c("slashVisual","slash_color","slash_particles"),new Field("slashColor",0,6,5,1,List.of("strike_red","strike_orange","strike_blue","strike_purple","strike_green","strike_white","strike_yellow")),c("particles","particles"),c("flightSound","library"),c("slashFlightSound","library"));
        case "sphere_blast" -> List.of(c("releasePosition","release_marked","release_target"),n("castTargetRange",1,256,32),n("sphereDistance",0,128,8),n("blastRadius",.5,16,4),target(2),n("damage",0,1000,20),t("chargeTime",0,1200,40),n("sphereChargeSpeed",.1,20,2),t("interval",1,12000,100),direction(),c("blastBlocks","off","on"),c("sphereChargeParticles","particles"),c("sphereBlastParticles","particles"),c("chargeSound","library"),c("blastSound","library"));
        case "linear_movement" -> List.of(c("linearMode","move_teleport","move_travel"),c("moveAim","move_fixed","move_target"),ownerTarget(0),n("moveTargetRange",1,256,64),n("moveStopDistance",0,128,2),c("moveTargetVertical","off","on"),new Field("facingCreative",0,1,1,1,List.of("off","on")),n("moveDistance",.1,128,8),n("moveHorizontal",-180,180,0),n("moveVertical",-180,180,0),direction(),t("chargeTime",0,1200,20),n("travelSpeed",.1,40,4),c("moveGravity","off","on"),c("moveStep","off","on"),n("moveStepHeight",.1,2,1),t("moveStepCooldown",4,200,6),c("moveObstruction","on","off"),c("moveForce","off","on"),t("interval",0,12000,100),
                c("moveChargeParticles","particles"),c("moveChargeSound","library"),n("moveChargeSoundVolume",0,4,1),n("moveChargeSoundRadius",1,256,32),
                c("moveDepartureParticles","particles"),c("moveDepartureSound","library"),n("moveDepartureSoundVolume",0,4,1),n("moveDepartureSoundRadius",1,256,32),
                c("moveArrivalParticles","particles"),c("moveArrivalSound","library"),n("moveArrivalSoundVolume",0,4,1),n("moveArrivalSoundRadius",1,256,32));
        case "sequence" -> List.of(c("sequenceSteps", "sequence_editor"), c("sequenceRepeat", "sequence_once", "sequence_repeat"), c("stopOnLow", "off", "on"), n("resetChannel", 0, 65535, 0), c("resetSource", "sequence_external", "sequence_internal"));
        case "arena" -> List.of(n("arenaRadius", 4, 256, 48), n("warningRange", 4, 256, 64), t("countdown", 20, 1200, 100), new Field("arenaDeath", 0, 1, 1, 1, List.of("off", "on")), c("arenaForceReturn", "off", "on"), c("arenaReturn", "off", "on"),c("arenaBuffEnabled","off","on"),c("arenaBuffGroups","arena_buff_editor"),c("arenaRespawnEnabled","off","on"),t("arenaRespawnDelay",100,72000,1200),c("arenaRespawnRecord","arena_respawn_record"),c("arenaRespawnClear","arena_respawn_clear"));
        case "quake" -> List.of(c("quakeMode", "quake_line", "quake_ring", "quake_area"), c("quakeOrigin", "marked", "quake_target_feet"),
                target(0), n("quakeDetectRange", 1, 128, 32), n("quakeRange", 1, 64, 16), n("quakeWidth", .5, 16, 3), n("quakeSpeed", .1, 4, .6),
                t("warningTime", 1, 1200, 30), n("damage", 0, 1000, 6), n("quakeLaunch", 0, 20, 3), n("quakePush", 0, 3, .5), n("quakeRise", 0, 4, .8),
                c("quakeGrounded", "on", "off"), n("quakeReach", .25, 8, 2), n("quakeWaves", 1, 8, 1), t("quakeGap", 1, 200, 12), t("interval", 1, 12000, 60),
                direction(), c("quakeColor", "strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow"),
                c("warningSound", "library"), c("quakeSound", "library"), c("particles", "particles"));
        case "ground_strike" -> impactVisualFields(List.of(n("strikeItemSize",.1,32,.5),c("strikeItemMode","strike_item_camera","strike_item_fixed"),n("strikeItemX",-180,180,0),n("strikeItemY",-180,180,0),n("strikeItemZ",-180,180,0),n("strikeStart", 0, 128, 0), n("strikeRays", 4, 32, 8), n("strikeRandomCount", 1, 128, 16), c("projectile", "item"), n("projectileHitbox", .05, 2, .5), c("particles", "particles"), c("fallSound", "library"), c("impactSound", "library"), c("strikeMode", "strike_line", "strike_radial", "strike_target", "strike_random"), target(0),
                n("strikeRadius", .5, 16, 2), n("strikeRange", 1, 128, 24), n("damage", 0, 1000, 20), t("interval", 5, 12000, 80),
                t("warningTime", 5, 200, 20), n("strikeSpeed", .1, 8, 1), n("strikeHeight", 2, 64, 12),
                c("strikeColor", "strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow"),
                direction(), n("strikeStep", 1, 16, 3), t("waveDelay", 1, 100, 4)));
        case "homing" -> explosive(List.of(c("projectile", "item"), n("projectileHitbox", .05, 2, .4), n("homingCount", 1, 32, 1),
                n("homingOffset", .5, 32, 3), n("homingHeight", -16, 32, 2), n("homingFan", 0, 180, 120), c("homingPlane", "homing_arc", "homing_line", "homing_star", "homing_star_vertical", "homing_circle", "homing_circle_vertical", "homing_backslash", "homing_slash"), n("homingRotation", -180, 180, 0), n("homingVerticalRotation", -180, 180, 0), direction(), t("homingDeploy", 0, 200, 20),
                t("chargeTime", 0, 1200, 40), target(0), n("targetRange", 1, 128, 32), c("lineOfSight", "off", "on"),
                c("homingMode", "homing_direct", "homing_shulker"), n("homingTracking", .01, 1, .5), n("speed", .05, 2, .3), t("homingLife", 1, 1200, 200), n("damage", 0, 1000, 6), t("interval", 1, 12000, 80),
                c("particles", "particles"), c("chargeSound", "library"), c("fireSound", "library"), c("impactSound", "library"), c("blastSound", "library")), false);
        case "laser" -> List.of(c("laserAim", "fixed", "laser_auto"), n("laserTracking", .1, 1, 1), n("laserFollow", .01, 1, .1), target(0),
                n("beamLength", 1, 128, 32), c("laserBreakBlocks","off","on"),c("laserBreakMode","laser_clear_line","laser_cut_forward"),n("laserBreakSpeed",.1,128,16), n("damage", .1, 1000, 6), t("chargeTime", 0, 1200, 40),
                n("beamHits", 1, 100, 5), t("hitInterval", 1, 200, 10), t("interval", 1, 12000, 60),
                n("beamWidth", .1, 4, .5), n("sweepAngle", -180, 180, 0), n("sweepSpeed", 0, 720, 0), n("verticalSweepAngle", -180, 180, 0), n("verticalSweepSpeed", 0, 720, 0), n("laserParticleSpacing", .25, 32, 4), direction(), c("laserVisualStyle","laser_continuous","laser_legacy"), c("beamColor", "strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow"),
                c("particles", "particles"), c("chargeSound", "library"), c("fireSound", "library"));
        case "armor" -> List.of(c("armorWholeBody","off","on"),c("armorRules","armor_rule_editor"));
        case "melee" -> List.of(n("meleeLength",0,128,0), n("meleeWidth",0,128,0), n("meleeHeight",0,128,0), n("meleeHorizontal", -180, 180, 0), n("meleeVertical", -90, 90, 0), n("meleeHitLimit", 0, 1024, 0), n("damage", .1, 1000, 6), n("knockback", 0, 100, 1), n("burn", 0, 30, 0), t("interval", 1, 12000, 20));
        case "flame" -> List.of(n("range", 1, 32, 6), n("damage", 1, 1000, 2), new Field("flameDirectHit", 0, 1, 1, 1, List.of("off", "on")), n("burn", 0, 30, 3), t("interval", 1, 100, 10), c("flameShape", "flame_cone", "flame_fan"), n("flameFanAngle", 5, 180, 60), n("width", .25, 4, .75), direction(), c("fireSound", "library"), n("flameSpeed", .1, 4, .8), c("particles","particles"));
        case "arrow" -> explosive(List.of(c("projectile", "item"), n("projectileRotationX",-180,180,0), n("projectileRotationY",-180,180,0), n("projectileRotationZ",-180,180,0),c("arrowFaceCamera","off","on"), n("projectileSize", .1, 8, 1), n("arrowHitbox", .05, 2, .25), t("arrowResidue", 0, 12000, 200), c("fireSound", "library"), c("arrowLandSound", "library"), n("damage", 1, 1000, 5), t("interval", 2, 200, 20), n("speed", .5, 5, 2), n("arrows", 1, 9, 1), n("endRadius", 0, 8, 1), direction()), false);
        case "gun" -> explosive(List.of(c("projectile", "item"), n("projectileRotationX",-180,180,0), n("projectileRotationY",-180,180,0), n("projectileRotationZ",-180,180,0), n("damage", 1, 1000, 5), t("interval", 1, 200, 10), n("speed", .5, 8, 4), n("arrows", 1, 9, 1), n("endRadius", 0, 8, 0), direction(), n("range", 1, 256, 64), c("fireSound", "library"), n("bulletHitbox", .05, 2, .2)), false);
        case "mortar" -> impactVisualFields(explosive(List.of(c("projectile", "item"), n("projectileRotationX",-180,180,0), n("projectileRotationY",-180,180,0), n("projectileRotationZ",-180,180,0), n("projectileHitbox", .05, 2, .15), n("damage", 0, 1000, 5), t("interval", 5, 1200, 60), n("speed", .5, 4, 2), n("launchAngle", 45, 85, 65), direction(), c("fireSound", "library"),
                c("aimMode", "fixed", "target_position"), target(0), n("targetRange", 1, 128, 64), c("mortarWarningColor", "strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow")), true));
        case "grab" -> List.of(c("grabTarget","players","mobs","grab_players_mobs"),n("grabRadius",.25,8,1.5),t("grabWarning",0,1200,5),t("grabDuration",1,1200,60),n("startDistance",0,16,1.5),n("grabOffsetX",-16,16,0),n("grabOffsetY",-16,16,0),t("interval",1,12000,100),c("particles","particles"),c("warningSound","library"),c("grabSound","library"));
        case "freeze" -> List.of(c("freezeTarget","players","mobs","grab_players_mobs"),c("releasePosition","release_marked","release_target"),n("castTargetRange",1,256,32),direction(),n("startDistance",0,128,0),c("fieldShape","field_circle","field_rectangle"),n("freezeRadius",.5,32,8),n("fieldLength",.5,64,8),n("fieldWidth",.5,64,4),n("freezeHeight",.5,64,16),n("fieldAngle",-180,180,0),c("fieldPlacement","freeze_centered","freeze_ahead"),t("freezeWarning",0,1200,30),t("freezeDuration",1,1200,40),t("interval",1,12000,100),c("particles","particles"),c("warningSound","library"),c("freezeSound","library"));
        case "pull" -> List.of(c("pullShape","pull_sphere","pull_directional"),c("pullMotion","pull_ordinary","pull_stable"),direction(),n("shotHorizontal",-180,180,0),n("shotVertical",-90,90,0),n("pullLength",.5,128,16),n("pullStartRadius",.25,32,1),n("pullEndRadius",.25,32,8),c("releasePosition","release_marked","release_target"),n("castTargetRange",1,256,32),target(0), n("pullRadius", 1, 32, 8), t("pullWarning", 5, 200, 30),
                t("pullDuration", 1, 200, 40), t("interval", 1, 12000, 60), n("pullStrength", .01, 1, .12), n("pullSpeed", .05, 2, .5),
                n("pullStop", .25, 4, .8),
                new Field("pullWarningColor", 0, 7, 1, 1, List.of("strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow", "pull_cyan")),
                new Field("pullCenterColor", 0, 7, 4, 1, List.of("strike_red", "strike_orange", "strike_blue", "strike_purple", "strike_green", "strike_white", "strike_yellow", "pull_cyan")), c("lineOfSight", "on", "off"), c("warningSound", "library"), c("pullSound", "library"), c("particles", "particles"));
        case "pulse" -> List.of(c("releasePosition","release_marked","release_target"),n("castTargetRange",1,256,32),c("pulseStart", "periodic", "target_present"), target(2),
                n("radius", 1, 16, 5), n("damage", 1, 1000, 6), n("knockback", 0, 100, 1), t("interval", 5, 200, 40), c("pulseVisual","pulse_fixed","pulse_outward"), t("pulseExpandTime",1,200,10), c("pulseCenterParticles","particles"), c("pulseWaveParticles","particles"), c("sound", "library"));
        case "particles" -> List.of(c("particlePattern", "particle_area", "particle_point", "particle_line", "particle_ring", "particle_sphere"),n("particleExpandLength",0,128,0),n("particleExpandWidth",0,128,0),n("particleExpandHeight",0,128,0),
                n("startDistance", 0, 128, 0), direction(), n("particleRadius", .05, 32, 3), n("particleRingHeight",0,128,0), n("particleLength", .1, 128, 8),
                c("particleDistribution", "particle_random", "particle_even"), c("particleFill", "particle_inside", "particle_surface"),
                c("particleLineMode", "particle_line_place", "particle_line_spray"), c("particlePropagation", "particle_default", "particle_outward", "particle_inward"), n("particleSpeed", .01, 4, .25), c("particles", "particles"), t("interval", 1, 200, 2), n("density", 1, 32, 4), new Field("trail", 0, 1, 1, 1, List.of("off", "on")), c("motionOnly", "off", "on"), n("drift", 0, .5, 0));
        case "sensor" -> List.of(n("radius", 1, 256, 12), ownerTarget(0), c("lineOfSight", "on", "off"), c("shape", "ring", "front"), n("innerRadius", 0, 31, 0), n("frontWidth", .5, 16, 3), direction(),n("sensorChannel",0,65535,0),n("sensorPower",1,15,15),c("sensorInvert","off","on"),c("redstoneFrequency","frequency"));
        case "sound" -> List.of(c("approach", "library"), c("hurt", "library"), n("radius", 1, 64, 16), n("volume", 0, 4, 1), n("pitch", .5, 2, 1), t("cooldown", 2, 1200, 20),
                c("ambient", "library"), t("ambientMin", 20, 12000, 100), t("ambientMax", 20, 12000, 200));
        case "trigger_sound" -> List.of(c("triggerSound", "library"), c("soundMode", "signal_once", "signal_repeat"),
                n("volume", 0, 4, 1), n("pitch", .5, 2, 1), t("cooldown", 0, 12000, 20), t("interval", 1, 12000, 60), c("contactMode", "off", "on"), c("contactSound", "library"),
                c("contactDirection", "down", "up", "north", "south", "west", "east"), n("contactDistance", .05, 4, .25), t("contactCooldown", 1, 200, 6));
        case "bgm" -> List.of(c("bgm", "library"), c("bgmStart", "after_hit", "on_approach"), n("bgmRadius", 1, 256, 64), t("bgmFade", 0, 400, 60), n("bgmVolume", 0, 1, 1));
        case "navigation" -> List.of(ownerTarget(0), n("radius", 1, 128, 32), c("lineOfSight", "on", "off"),
                new Field("verticalTracking", 0, 1, 1, 1, List.of("off", "on")), c("targetLock", "locked", "nearest"), n("deadZone", 0, 45, 3), n("pitchDeadZone", 0, 45, 3),
                c("navigationFront", "marked", "north", "south", "west", "east"), c("navigationInvert", "off", "on"), n("navigationLeftChannel",0,65535,0), n("navigationRightChannel",0,65535,0), n("navigationUpChannel",0,65535,0), n("navigationDownChannel",0,65535,0), n("navigationForwardChannel",0,65535,0), n("navigationBackwardChannel",0,65535,0));
        case "loot" -> List.of(c("weighted", "chance", "weighted"), n("rolls", 1, 27, 1));
        default -> List.of();
    }; }
    public static final List<Field> HURT_FIELDS = List.of(c("hurtMode", "hurt_once", "hurt_timed", "hurt_stay"), t("hurtCooldown", 0, 12000, 0), t("hurtDuration", 1, 12000, 100));
    private static final java.util.Map<String,java.util.Map<String,Field>> LOOKUP=new java.util.concurrent.ConcurrentHashMap<>();
    public static Field find(String type,String key){
        if(!type.equals("loot"))for(var f:HURT_FIELDS)if(f.key().equals(key))return f;
        if (DatapackModules.isDatapackType(type)) {
            for (var field : forType(type)) if (field.key().equals(key)) return field;
            return null;
        }
        if(!VirtualModule.supportsType(type))return null;
        return LOOKUP.computeIfAbsent(type,t->{var fields=new java.util.LinkedHashMap<String,Field>();for(var f:forType(t))fields.putIfAbsent(f.key(),f);return java.util.Map.copyOf(fields);}).get(key);
    }
}
