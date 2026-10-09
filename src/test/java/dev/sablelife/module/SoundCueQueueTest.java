package dev.sablelife.module;
import dev.sablelife.sound.SoundCueQueue;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
public final class SoundCueQueueTest {
    @Test void LayersStartAtTheirOwnOffsetAndDoNotReplay(){var q=new SoundCueQueue<String>();var owner=UUID.randomUUID();q.add(owner,100,20,true,"tail");q.add(owner,100,0,true,"attack");q.add(owner,100,10,true,"charge");assertEquals(List.of("attack"),q.drain(100));assertTrue(q.drain(109).isEmpty());assertEquals(List.of("charge"),q.drain(110));assertEquals(List.of("tail"),q.drain(130));assertTrue(q.drain(131).isEmpty());}
    @Test void PhaseEndPreservesAuthoredMixButDisablingOwnerCancelsIt(){var q=new SoundCueQueue<String>();var a=UUID.randomUUID();var b=UUID.randomUUID();q.add(a,0,20,false,"old");q.add(a,0,20,true,"mix tail");q.add(b,0,20,true,"other");q.cancel(a,false);assertEquals(2,q.size());q.cancel(a,true);assertEquals(List.of("other"),q.drain(20));}
    @Test void LogoutClearDropsPendingLayersAndQueueIsBounded(){var q=new SoundCueQueue<String>();var owner=UUID.randomUUID();assertFalse(q.add(owner,0,-1,true,"invalid"));assertFalse(q.add(owner,0,12001,true,"invalid"));for(int i=0;i<4096;i++)assertTrue(q.add(owner,0,12000,true,"cue"));assertFalse(q.add(owner,0,12000,true,"overflow"));q.clear();assertTrue(q.drain(12000).isEmpty());assertTrue(q.add(owner,0,1,true,"new world"));}
}
