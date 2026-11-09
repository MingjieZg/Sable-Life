package dev.sablelife.ownership;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WildSpawnDistancesTest {
    @Test void smallTemplatesKeepTheConfiguredRange(){
        int min=WildSpawnDistances.minimum(5,5,48);
        assertEquals(48,min);assertEquals(49,WildSpawnDistances.choices(min,96));
    }
    @Test void largeTemplatesCannotMoveCandidatesPastTheConfiguredMaximum(){
        int min=WildSpawnDistances.minimum(128,128,48);
        assertEquals(99,min);assertEquals(0,WildSpawnDistances.choices(min,96));
        int choices=WildSpawnDistances.choices(min,256);
        assertEquals(256,min+choices-1);
    }
    @Test void exactFootprintLimitAllowsOneDistance(){
        assertEquals(1,WildSpawnDistances.choices(99,99));
    }
}
