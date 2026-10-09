package dev.sablelife.heart;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperienceUnitsTest {
    @Test void MatchesVanillaThresholdsAndBothFormulaTransitions(){
        assertEquals(55,ExperienceUnits.points(5));assertEquals(352,ExperienceUnits.points(16));assertEquals(394,ExperienceUnits.points(17));assertEquals(1507,ExperienceUnits.points(31));assertEquals(1628,ExperienceUnits.points(32));assertEquals(9843,ExperienceUnits.points(63));assertEquals(30970,ExperienceUnits.points(100));assertEquals(100,ExperienceUnits.levels(30970));
    }
    @Test void WholeLevelDisplayDoesNotRoundUpTheStoredReward(){
        assertEquals(26,ExperienceUnits.levels(1000));assertEquals(997,ExperienceUnits.points(26));assertEquals(4,ExperienceUnits.levels(54));assertEquals(5,ExperienceUnits.levels(55));assertEquals(63,ExperienceUnits.levels(10000));
        for(int level=0;level<=100;level++)assertEquals(level,ExperienceUnits.levels(ExperienceUnits.points(level)));
    }
}
