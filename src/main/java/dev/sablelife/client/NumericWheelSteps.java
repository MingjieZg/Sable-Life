package dev.sablelife.client;

import dev.sablelife.module.ModuleFields;
import java.util.Set;

/** Editing increments in display units; never changes saved defaults or validation ranges. */
public final class NumericWheelSteps {
    private static final Set<String> WHOLE=Set.of("slashCount","slashRed","slashGreen","slashBlue","damage","blastDamage","arrows","burn","rolls","density","beamHits","meleeHitLimit","strikeRays","strikeRandomCount","quakeWaves","homingCount","accumThreshold","accumChannel","accumPower","phaseChannel","phasePower","resetChannel","redstonePower","dispatchWeight","phaseUpper","phaseLower");
    private static final Set<String> BLOCKS=Set.of("moveTargetRange","castTargetRange","slashLength","slashSpeed","sphereDistance","range","radius","innerRadius","targetRange","facingRange","arenaRadius","warningRange","bgmRadius","quakeRange","quakeDetectRange","strikeRange","strikeHeight","strikeStep","beamLength","pullRadius","dispatchNear","dispatchFar");
    public static double module(String type,String key,double scale,double minimum){
        if(dev.sablelife.module.ModuleColors.parent(type,key)!=null)return 1;
        // Explicit user choices: angles ±5, hit limit ±1; sound mixes keep their own policy.
        if(type.equals("signal_turn")&&key.endsWith("Channel"))return 1;
        if(key.equals("turnSpeed"))return 5;
        if(key.startsWith("displayRot")||ModuleFields.angle(key)||key.equals("damageModifier"))return 5;
        if(scale!=1)return .1;
        if(WHOLE.contains(key)||BLOCKS.contains(key)||key.endsWith("Radius")&&minimum>=1)return 1;
        if(Set.of("pullStrength","homingTracking","laserFollow","particleSpeed","drift").contains(key))return .01;
        if(key.endsWith("Hitbox")||key.equals("projectileHitbox")||key.equals("pullSpeed")||type.equals("homing")&&key.equals("speed"))return .05;
        return .1;
    }
    public static double core(String key){
        if(key.equals("health")||key.equals("defenseThreshold")||key.equals("regen")||key.equals("regenPercent"))return 5;
        if(key.equals("voidDeathHeight")||key.equals("regenDelay")||key.equals("deathEffectRadius"))return 1;
        if(key.equals("xp")||key.equals("defenseMaximum")||key.endsWith("Count"))return 1;
        return .1;
    }
    private NumericWheelSteps(){}
}
