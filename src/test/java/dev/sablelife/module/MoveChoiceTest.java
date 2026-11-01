package dev.sablelife.module;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MoveChoiceTest {
    @Test void weightedBoundariesAndAvoidRepeat() {
        UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        var pool=List.of(new MoveChoice.Candidate(a,1,false),new MoveChoice.Candidate(b,3,false));
        assertEquals(a,MoveChoice.choose(pool,null,0));
        assertEquals(a,MoveChoice.choose(pool,null,.249));
        assertEquals(b,MoveChoice.choose(pool,null,.25));
        assertEquals(b,MoveChoice.choose(pool,null,1));
        assertEquals(b,MoveChoice.choose(List.of(new MoveChoice.Candidate(a,100,true),new MoveChoice.Candidate(b,1,true)),a,0));
        assertEquals(a,MoveChoice.choose(List.of(new MoveChoice.Candidate(a,1,true)),a,0));
        assertNull(MoveChoice.choose(List.of(),a,0));
    }
    @Test void shortInputRunsFullPulseButHeldInputNeverRestarts() {
        var pulse=new SignalPulse();
        assertTrue(pulse.tick(true,true,3));
        assertTrue(pulse.tick(false,true,3));
        assertTrue(pulse.tick(false,true,3));
        assertFalse(pulse.tick(false,true,3));
        assertTrue(pulse.tick(true,true,2));
        assertTrue(pulse.tick(true,true,2));
        for(int i=0;i<10;i++) assertFalse(pulse.tick(true,true,2));
        assertFalse(pulse.tick(false,false,2));
        assertTrue(pulse.tick(true,false,2));
    }
}
