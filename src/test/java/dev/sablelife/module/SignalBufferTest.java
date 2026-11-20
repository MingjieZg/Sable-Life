package dev.sablelife.module;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SignalBufferTest {
 @Test void offDelayStartsWhenOutputFallsRatherThanWhenItStarts(){var b=new SignalBuffer();assertEquals(8,b.tick(0,8,4));assertEquals(8,b.tick(100,8,4));assertEquals(8,b.tick(101,0,4));assertEquals(8,b.tick(104,0,4));assertEquals(0,b.tick(105,0,4));}
 @Test void analogTailKeepsLastValueAndNewPowerReplacesIt(){var b=new SignalBuffer();b.tick(0,12,5);b.tick(1,3,5);assertEquals(3,b.tick(2,0,5));assertEquals(6,b.tick(4,6,5));assertEquals(6,b.tick(5,0,5));assertEquals(6,b.tick(9,0,5));assertEquals(0,b.tick(10,0,5));}
 @Test void repeatedSameTickPublicationDoesNotExtendDeadline(){var b=new SignalBuffer();b.tick(9,15,4);for(int i=0;i<20;i++)assertEquals(15,b.tick(10,0,4));assertEquals(15,b.tick(13,0,4));assertEquals(0,b.tick(14,0,4));}
 @Test void zeroDefaultAndNoInputNeverProducePower(){var b=new SignalBuffer();assertEquals(7,b.tick(0,7,0));assertEquals(0,b.tick(1,0,0));assertEquals(0,b.tick(2,0,100));assertEquals(0,b.tick(200,0,100));}
 @Test void turningBufferOffOrChangingDurationDropsPendingTail(){var b=new SignalBuffer();b.tick(0,8,5);b.tick(1,0,5);assertEquals(0,b.tick(2,0,0));assertEquals(0,b.tick(3,0,5));b.tick(4,9,5);b.tick(5,0,5);assertEquals(0,b.tick(6,0,10));}
 @Test void expiredBufferCannotRestartWithoutNewPower(){var b=new SignalBuffer();b.tick(0,4,2);assertEquals(4,b.tick(1,0,2));assertEquals(0,b.tick(3,0,2));assertEquals(0,b.tick(4,0,2));assertEquals(0,b.tick(100,0,2));}
 @Test void backwardsClockAndOverflowAreBounded(){var b=new SignalBuffer();b.tick(100,6,5);b.tick(101,0,5);assertEquals(0,b.tick(20,0,5));b.tick(Long.MAX_VALUE-2,9,10);assertEquals(9,b.tick(Long.MAX_VALUE-1,0,10));assertEquals(0,b.tick(Long.MAX_VALUE,0,10));}
}
