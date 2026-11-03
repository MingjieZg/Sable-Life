package dev.sablelife.module;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SignalRampTest {
 @Test void reachesEndpointInThreeSecondsAndHolds(){var r=new SignalRamp();assertEquals(0,r.tick(100,true,0,15,60));assertEquals(5,r.tick(120,true,0,15,60));assertEquals(10,r.tick(140,true,0,15,60));assertEquals(15,r.tick(160,true,0,15,60));assertEquals(15,r.tick(260,true,0,15,60));}
 @Test void cancellationAndReactivationRestart(){var r=new SignalRamp();r.tick(0,true,3,12,60);assertEquals(6,r.tick(20,true,3,12,60));assertEquals(0,r.tick(21,false,3,12,60));assertEquals(3,r.tick(22,true,3,12,60));}
 @Test void descendingAndEqualEndpoints(){var r=new SignalRamp();assertEquals(15,r.tick(0,true,15,0,60));assertEquals(10,r.tick(20,true,15,0,60));assertEquals(0,r.tick(60,true,15,0,60));assertEquals(7,r.tick(61,true,7,7,60));assertEquals(7,r.tick(161,true,7,7,60));}
 @Test void repeatedPublicationDoesNotAdvanceTime(){var r=new SignalRamp();r.tick(0,true,0,15,60);for(int i=0;i<20;i++)assertEquals(5,r.tick(20,true,0,15,60));}
 @Test void changedConfigurationAndReversedClockRestart(){var r=new SignalRamp();r.tick(100,true,0,15,60);assertEquals(5,r.tick(120,true,0,15,60));assertEquals(4,r.tick(121,true,4,10,20));assertEquals(4,r.tick(10,true,4,10,20));}
}
