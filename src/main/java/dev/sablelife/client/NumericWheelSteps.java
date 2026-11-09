package dev.sablelife.client;

import dev.sablelife.module.ModuleFields;
import java.util.Set;

/** Editing increments in display units; never changes saved defaults or validation ranges. */
public final class NumericWheelSteps {
    private static final Set<String> WHOLE=Set.of("grabCount","slashCount","slashRed","slashGreen","slashBlue","damage","blastDamage","arrows","burn","rolls","density","beamHits","meleeHitLimit","strikeRays","strikeRandomCount","quakeWaves","homingCount","accumThreshold","sensorChannel","sensorPower","accumChannel","accumPower","phaseChannel","phasePower","resetChannel","redstoneChannel","redstonePower","redstoneRampStart","redstoneRampEnd","dispatchWeight","hitThreshold","hitSource","hitChannel","hitPower","phaseUpper","phaseLower");
    private static final Set<String> BLOCKS=Set.of("moveTargetRange","castTargetRange","slashLength","slashSpeed","sphereDistance","range","radius","innerRadius","targetRange","facingRange","arenaRadius","warningRange","bgmRadius","quakeRange","quakeDetectRange","strikeRange","strikeHeight","strikeStep","beamLength","pullRadius","dispatchNear","dispatchFar");
    public static double module(String type,String key,double scale,double minimum){
        if(dev.sablelife.module.ModuleColors.parent(type,key)!=null)return 1;
        if(type.equals("damage_accumulation")&&key.equals("accumRequired"))return 5;
        if(type.equals("damage_accumulation")&&key.equals("accumWindow"))return 4;
        // Explicit user choices: angles ±5, hit limit ±1; sound mixes keep their own policy.
        if((type.equals("signal_turn")||type.equals("navigation"))&&key.endsWith("Channel"))return 1;
        if(type.equals("linear_movement")){
            if(key.equals("moveTargetRange"))return 8;
            if(Set.of("travelSpeed","interval","chargeTime","moveStopDistance","moveDistance").contains(key))return 1;
        }
        if(type.equals("sensor")&&key.equals("frontWidth"))return 1;
        if(type.equals("entity_hitbox")&&Set.of("hitboxWidth","hitboxHeight","hitboxDepth","hitboxOffsetX","hitboxOffsetY","hitboxOffsetZ").contains(key))return 1;
        if(type.equals("health_bar")&&key.equals("healthBarNameSize"))return .05;
        if(type.equals("health_bar")&&key.equals("healthBarNameGap"))return .01;
        if(key.equals("impactCount")||key.equals("impactSpreadRadius"))return 1;
        if(Set.of("summonTurnSpeed","summonYaw","summonPitch","summonFormationRoll","summonFormationYaw","summonFan").contains(key))return 5;
        if(Set.of("summonCount","summonRadius","summonSpacing").contains(key))return 1;
        if(key.equals("turnSpeed"))return 5;
        if(type.equals("laser")&&Set.of("sweepSpeed","verticalSweepSpeed").contains(key))return 5;
        if(type.equals("laser")&&key.equals("laserParticleSpacing"))return .25;
        if(key.equals("hoverStrength"))return 5;
        if(key.equals("shockZoom")||key.equals("shockRadius")||key.equals("shockRange"))return 1;
        if(Set.of("shockDistortion","shockBlur","shockShake").contains(key))return .05;
        if(key.equals("healthAmount")||key.equals("armorWholeModifier"))return 5;
        if(key.equals("armorWholeChannel"))return 1;
        if(key.startsWith("displayRot")||ModuleFields.angle(key)||key.equals("damageModifier"))return 5;
        if(type.equals("arena")&&key.equals("arenaRespawnDelay"))return 1;
        if(key.equals("interactionSize"))return 1;
        if(type.equals("blueprint_spawn")&&Set.of("spawnDelay","spawnCooldown","spawnLimit").contains(key))return 1;
        if(type.equals("surface_field")&&Set.of("fieldRadius","fieldLength","fieldWidth","fieldDuration").contains(key))return 1;
        if(type.equals("freeze")&&Set.of("freezeHeight","fieldLength","fieldWidth").contains(key))return 1;
        if(scale!=1)return .1;
        if(WHOLE.contains(key)||BLOCKS.contains(key)||key.endsWith("Radius")&&minimum>=1)return 1;
        if(Set.of("pullStrength","homingTracking","laserFollow","particleSpeed","drift").contains(key))return .01;
        if(key.endsWith("Hitbox")||key.equals("projectileHitbox")||key.equals("pullSpeed")||type.equals("homing")&&key.equals("speed"))return .05;
        return .1;
    }
    public static double core(String key){if(key.equals("crushDelay"))return .05;
        if(key.equals("knockbackResistance")||key.equals("health")||key.equals("defenseThreshold")||key.equals("regen")||key.equals("regenPercent"))return 5;
        if(key.equals("limbCleanupDelay")||key.equals("voidDeathHeight")||key.equals("regenDelay")||key.equals("deathEffectRadius"))return 1;
        if(key.equals("xp")||key.equals("defenseMaximum")||key.endsWith("Count"))return 1;
        return .1;
    }
    private NumericWheelSteps(){}
}
