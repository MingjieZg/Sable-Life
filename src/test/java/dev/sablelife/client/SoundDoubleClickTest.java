package dev.sablelife.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoundDoubleClickTest {
    @Test void OnlyPairsTheSameItemInsideTheWindow(){
        var clicks=new SoundDoubleClick();assertFalse(clicks.click("library:a",100));assertTrue(clicks.click("library:a",300));
        assertFalse(clicks.click("library:a",350));assertFalse(clicks.click("library:b",400));assertTrue(clicks.click("library:b",500));
    }
    @Test void DelayedOrCrossListClicksDoNotAudition(){
        var clicks=new SoundDoubleClick();assertFalse(clicks.click("library:a",100));assertFalse(clicks.click("library:a",351));
        assertFalse(clicks.click("selected:a",400));assertTrue(clicks.click("selected:a",450));
    }
    @Test void OtherInputAndClockReversalCancelThePair(){
        var clicks=new SoundDoubleClick();assertFalse(clicks.click("a",100));clicks.clear();assertFalse(clicks.click("a",150));
        assertFalse(clicks.click("a",140));assertTrue(clicks.click("a",150));
    }
}
