package dev.sablelife.module;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SignalRampTest {
 @Test void reachesEndpointInThreeSecondsAndHolds(){var r=new SignalRamp();assertEquals(0,r.tick(100,true,0,15,60));assertEquals(5,r.tick(120,true,0,15,60));assertEquals(10,r.tick(140,true,0,15,60));assertEquals(15,r.tick(160,true,0,15,60));assertEquals(15,r.tick(260,true,0,15,60));}
 @Test void cancellationAndReactivationRestart(){var r=new SignalRamp();r.tick(0,true,3,12,60);assertEquals(6,r.tick(20,true,3,12,60));assertEquals(0,r.tick(21,false,3,12,60));assertEquals(3,r.tick(22,true,3,12,60));}
 @Test void descendingAndEqualEndpoints(){var r=new SignalRamp();assertEquals(15,r.tick(0,true,15,0,60));assertEquals(10,r.tick(20,true,15,0,60));assertEquals(0,r.tick(60,true,15,0,60));assertEquals(7,r.tick(61,true,7,7,60));assertEquals(7,r.tick(161,true,7,7,60));}
 @Test void repeatedPublicationDoesNotAdvanceTime(){var r=new SignalRamp();r.tick(0,true,0,15,60);for(int i=0;i<20;i++)assertEquals(5,r.tick(20,true,0,15,60));}
 @Test void changedConfigurationAndReversedClockRestart(){var r=new SignalRamp();r.tick(100,true,0,15,60);assertEquals(5,r.tick(120,true,0,15,60));assertEquals(4,r.tick(121,true,4,10,20));assertEquals(4,r.tick(10,true,4,10,20));}

 @Test
 void earlyReleaseFromPublishedPowerReachesZeroAtTheDeadline() {
     var ramp = new SignalRamp();
     assertEquals(3, ramp.tick(0, true, 3, 15, 20, 10));
     assertEquals(9, ramp.tick(10, true, 3, 15, 20, 10));
     assertEquals(9, ramp.tick(11, false, 3, 15, 20, 10));
     assertEquals(5, ramp.tick(16, false, 3, 15, 20, 10));
     assertEquals(5, ramp.tick(16, false, 3, 15, 20, 10));
     assertEquals(1, ramp.tick(20, false, 3, 15, 20, 10));
     assertEquals(0, ramp.tick(21, false, 3, 15, 20, 10));
     assertEquals(3, ramp.tick(22, true, 3, 15, 20, 10));
 }

 @Test
 void retriggerDuringReleaseStartsFromTheActualOutput() {
     var ramp = new SignalRamp();
     assertEquals(4, ramp.tick(0, true, 4, 14, 20, 10));
     assertEquals(14, ramp.tick(20, true, 4, 14, 20, 10));
     assertEquals(14, ramp.tick(21, false, 4, 14, 20, 10));
     assertEquals(6, ramp.tick(27, false, 4, 14, 20, 10));
     assertEquals(6, ramp.tick(28, true, 4, 14, 20, 10));
     assertEquals(10, ramp.tick(38, true, 4, 14, 20, 10));
     assertEquals(14, ramp.tick(48, true, 4, 14, 20, 10));
     ramp.tick(49, false, 4, 14, 20, 10);
     assertEquals(4, ramp.tick(59, true, 4, 14, 20, 10));
 }

 @Test
 void editingReleaseWhileActivePreservesTheRampAndEditingDuringReleaseClearsIt() {
     var ramp = new SignalRamp();
     assertEquals(0, ramp.tick(0, true, 0, 15, 20, 0));
     assertEquals(8, ramp.tick(10, true, 0, 15, 20, 10));
     assertEquals(8, ramp.tick(11, false, 0, 15, 20, 10));
     assertEquals(0, ramp.tick(12, false, 0, 15, 20, 0));
     assertEquals(0, ramp.tick(13, true, 0, 15, 20));
 }

 @Test
 void backwardsTicksAndModeResetDiscardPendingRelease() {
     var ramp = new SignalRamp();
     ramp.tick(0, true, 1, 15, 20, 10);
     ramp.tick(20, true, 1, 15, 20, 10);
     ramp.tick(21, false, 1, 15, 20, 10);
     assertEquals(8, ramp.tick(26, false, 1, 15, 20, 10));
     assertEquals(0, ramp.tick(25, false, 1, 15, 20, 10));
     ramp.tick(27, true, 1, 15, 20, 10);
     ramp.tick(47, true, 1, 15, 20, 10);
     ramp.tick(48, false, 1, 15, 20, 10);
     ramp.reset();
     assertEquals(0, ramp.tick(49, false, 1, 15, 20, 10));
 }
}
