package dev.sablelife.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumericWheelStepsTest {
    @Test void ExplicitUserStepsStayProtected(){
        assertEquals(5,NumericWheelSteps.core("health"));
        assertEquals(5,NumericWheelSteps.module("part_break","partBreakThreshold",1,1));
        assertEquals(5,NumericWheelSteps.core("regen"));
        assertEquals(5,NumericWheelSteps.core("regenPercent"));
        assertEquals(1,NumericWheelSteps.core("regenDelay"));
        assertEquals(1,NumericWheelSteps.module("arena","arenaRespawnDelay",20,100));
        assertEquals(1,NumericWheelSteps.module("melee","meleeHitLimit",1,0));
        for(String key:new String[]{"meleeHorizontal","meleeVertical","moveHorizontal","moveVertical","homingRotation","homingVerticalRotation","dispatchCone"})assertEquals(5,NumericWheelSteps.module("any",key,1,-180));
        for(int i=0;i<3;i++)assertEquals(1.1,SoundTimelineInputs.scroll(i,1,1,1));
        assertEquals(9,SoundTimelineInputs.scroll(3,1,1,1));
        for(int i=4;i<6;i++)assertEquals(1.1,SoundTimelineInputs.scroll(i,1,1,1));
    }
    @Test void WholeSignalsCountsPercentagesAndRangesUseWholeSteps(){
        for(String key:new String[]{"redstonePower","dispatchWeight","phaseUpper","phaseLower","homingCount","damage","targetRange","facingRange","beamLength","dispatchNear"})assertEquals(1,NumericWheelSteps.module("any",key,1,0));
        assertEquals(1,NumericWheelSteps.core("xp"));assertEquals(1,NumericWheelSteps.core("defenseMaximum"));
    }
    @Test void FineMotionAndCollisionSizesRetainUsablePrecision(){
        assertEquals(.01,NumericWheelSteps.module("pull","pullStrength",1,.01));
        assertEquals(.01,NumericWheelSteps.module("laser","laserFollow",1,.01));
        assertEquals(.01,NumericWheelSteps.module("particles","drift",1,0));
        assertEquals(.05,NumericWheelSteps.module("arrow","arrowHitbox",1,.05));
        assertEquals(.05,NumericWheelSteps.module("homing","speed",1,.05));
        assertEquals(.1,NumericWheelSteps.module("melee","startDistance",1,0));
        assertEquals(.1,NumericWheelSteps.module("gun","interval",20,1));
        assertEquals(.1,NumericWheelSteps.core("delay"));
    }
}
