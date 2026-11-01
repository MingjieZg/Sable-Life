package dev.sablelife.module;

import java.util.List;

/** Shared mode visibility, also exercised without a client. Stored hidden values are retained. */
public final class SurfaceFieldSettings {
    public static boolean visible(VirtualModule m,String key){
        if(m.type().equals("visual_shockwave")){
            if(List.of("filterColor","filterStrength","filterFadeIn","filterFadeOut").contains(key))return m.value("filterEnabled")==1;
            if(List.of("shockRadius","shockReverse","shockExpansion","shockWidth","shockDistortion","shockBlur").contains(key))return m.value("shockWaveEnabled")==1;
            if(List.of("shockShake","shockShakeTime").contains(key))return m.value("shockShakeEnabled")==1;
            if(List.of("shockZoom","shockZoomTime").contains(key))return m.value("shockZoomEnabled")==1;
        }
        if(m.type().equals("summoned_weapon")){
            boolean many=m.value("summonCount")>1;int shape=(int)m.value("summonFormation");boolean line=shape==1||shape==6||shape==7;
            if(List.of("summonFormation","summonFormationRoll","summonFormationYaw","summonDeployGap").contains(key))return many;
            if(key.equals("summonRadius"))return many&&!line;
            if(key.equals("summonSpacing"))return many&&line;
            if(key.equals("summonFan"))return many&&shape==0;
        }
        if(m.type().equals("summoned_weapon")&&List.of("targetRange","summonTurnSpeed").contains(key))return m.value("summonAim")==1;
        if(m.type().equals("mortar")||m.type().equals("ground_strike")){
            if(List.of("impactParticles","impactSpreadMode","impactCount").contains(key))return m.value("impactVisual")==1;
            if(key.equals("impactSpreadRadius"))return m.value("impactVisual")==1&&m.value("impactSpreadMode")==1;
        }
        if(m.type().equals("damage_accumulation")){if(key.equals("accumThreshold"))return m.value("accumMode")==0;if(List.of("accumWindow","accumRequired","accumRepeat").contains(key))return m.value("accumMode")==1;}
        if(m.type().equals("signal_logic")&&key.equals("logicPulse"))return m.value("logicMode")==1;
        if(m.type().equals("slash")&&key.equals("burn"))return m.value("slashIgnite")==1;
        if(m.type().equals("freeze")){
            if(key.equals("targetMultiple"))return TargetCastOrigin.targeted(m);
            if(List.of("direction","startDistance").contains(key))return !TargetCastOrigin.targeted(m);
            if(key.equals("freezeRadius"))return m.value("fieldShape")==0;
            if(List.of("fieldLength","fieldWidth","fieldAngle","fieldPlacement").contains(key))return m.value("fieldShape")==1;
        }
        if(!m.type().equals("surface_field"))return true;
        if(key.equals("burn"))return m.value("fieldBurn")==1;
        if(key.equals("fieldRadius"))return m.value("fieldShape")==0;
        if(List.of("fieldLength","fieldWidth","fieldAngle","fieldPlacement").contains(key))return m.value("fieldShape")==1;
        return true;
    }
    private SurfaceFieldSettings(){}
}
