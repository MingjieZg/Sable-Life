package dev.sablelife.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EditorInputsTest {
    @Test void anglesAreRecognizedWithoutChangingDistances(){
        for(String key:List.of("launchAngle","deadZone","pitchDeadZone","dispatchCone","homingFan","homingRotation","homingVerticalRotation","moveHorizontal","moveVertical","meleeHorizontal","meleeVertical","sweepAngle"))assertTrue(dev.sablelife.module.ModuleFields.angle(key));
        assertFalse(dev.sablelife.module.ModuleFields.angle("startDistance"));assertFalse(dev.sablelife.module.ModuleFields.angle("particleSpeed"));
    }
    @Test void wheelUsesDisplayUnitsWithoutFloatingPointDrift() {
        assertEquals(.1,SoundTimelineInputs.scroll(0,0,0,1));
        assertEquals(.9,SoundTimelineInputs.scroll(0,1,1,-1));
        assertEquals(33,SoundTimelineInputs.scroll(3,32,32,1));
        assertEquals(1,SoundTimelineInputs.scroll(4,0,0,1));
        double pitch=1;
        for(int i=0;i<10;i++){pitch=SoundTimelineInputs.scroll(1,pitch,1,1);pitch=SoundTimelineInputs.scroll(1,pitch,1,-1);}
        assertEquals(1,pitch);
        assertEquals(1.1,SoundTimelineInputs.scroll(2,1,1,1));
        assertEquals(.9,SoundTimelineInputs.scroll(2,1,1,-1));
    }
    @Test void wheelClampsBoundsAndRecoversIncompleteInput() {
        assertEquals(0,SoundTimelineInputs.scroll(0,0,0,-1));
        assertEquals(600,SoundTimelineInputs.scroll(4,600,0,1));
        assertEquals(.5,SoundTimelineInputs.scroll(1,.5,1,-1));
        assertEquals(2,SoundTimelineInputs.scroll(1,2,1,1));
        assertEquals(4,SoundTimelineInputs.scroll(2,4,1,1));
        assertEquals(1,SoundTimelineInputs.scroll(3,1,32,-1));
        assertEquals(256,SoundTimelineInputs.scroll(3,256,32,1));
        assertEquals(10.1,SoundTimelineInputs.scroll(0,Double.NaN,10,1));
    }
    @Test void returnPositionTracksFirstFieldAcrossLayoutChanges() {
        var id=UUID.randomUUID();var fields=List.of("a","b","c","d","e","f","g","h");
        DashboardPageMemory.remember(id,fields,1,4);
        var saved=DashboardPageMemory.get(id);
        assertEquals(1,saved.restore(fields,4));
        assertEquals(2,saved.restore(fields,2));
        assertEquals(0,saved.restore(fields.subList(0,2),4));
        assertNull(DashboardPageMemory.get(UUID.randomUUID()));
        DashboardPageMemory.clear();assertNull(DashboardPageMemory.get(id));
    }
}
