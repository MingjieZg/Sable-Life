package dev.sablelife.module;
import dev.sablelife.sound.SoundFade;
import dev.sablelife.client.SoundTimelineInputs;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SoundFadeTest {
    @Test void fadeOccupiesTheFinalSecondsInsideTheLimit() {
        assertEquals(1F,SoundFade.gain(19,30,10));
        assertEquals(1F,SoundFade.gain(20,30,10));
        assertEquals(.5F,SoundFade.gain(25,30,10));
        assertEquals(.1F,SoundFade.gain(29,30,10));
        assertEquals(0F,SoundFade.gain(30,30,10));
        assertEquals(0F,SoundFade.gain(100,30,10));
        assertEquals(30,SoundFade.stopAfter(30,10));
        assertEquals(1F,SoundFade.gain(0,30,60));
        assertEquals(.5F,SoundFade.gain(15,30,60));
        assertEquals(0F,SoundFade.gain(30,30,60));
        assertEquals(30,SoundFade.stopAfter(30,60));
    }
    @Test void zeroFadeCutsOffAndZeroLimitHasNoScheduledEnd() {
        assertEquals(1F,SoundFade.gain(29,30,0));
        assertEquals(0F,SoundFade.gain(30,30,0));
        assertEquals(30,SoundFade.stopAfter(30,0));
        assertEquals(1F,SoundFade.gain(99999,0,10));
        assertEquals(Long.MAX_VALUE,SoundFade.stopAfter(0,10));
    }
    @Test void fadeWheelClampsAtZeroAndTenMinutes() {
        assertEquals(.1,SoundTimelineInputs.scroll(5,0,0,1));
        assertEquals(0,SoundTimelineInputs.scroll(5,0,0,-1));
        assertEquals(600,SoundTimelineInputs.scroll(5,600,0,1));
    }
}
