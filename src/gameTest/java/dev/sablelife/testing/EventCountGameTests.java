package dev.sablelife.testing;
import dev.sablelife.module.*;
import dev.sablelife.heart.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_signal_tests") @PrefixGameTestTemplate(false)
public final class EventCountGameTests {
 private static HeartBlockEntity core(GameTestHelper h){var c=SlashGuardGameTests.core(h);int n=c.wireless().add();c.wireless().channel(n).set(6,3);c.wireless().channel(n).set(0,1);c.tryActivate(TestActors.creative(h.getLevel()));return c;}
 @GameTest(template="combat",timeoutTicks=25) public static void ThreeBlocksCountDuplicatesOnceAndRepeatNextCycle(GameTestHelper h){
  var c=core(h);var m=c.modules().add("combat_event");var r=m.signalRules().rows().getFirst();r.edit("eventKind",2);r.edit("eventCount",3);r.edit("eventChannel",1);r.edit("eventPulse",2);r.edit("eventCooldown",0);var victim=UUID.randomUUID();
  c.modules().combatEvents.emit(c,2,1,null,victim);c.modules().combatEvents.emit(c,2,1,null,victim);c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(0)==0&&c.modules().combatEvents.snapshot(m.id()).getList("Rows",10).getCompound(0).getInt("Count")==1,"Duplicate block counted or early pulse");
  for(int tick:new int[]{1,2,5,6,7})h.runAfterDelay(tick,()->{c.modules().combatEvents.emit(c,2,1,null,victim);c.modules().combatEvents.tick(c);});
  h.runAfterDelay(3,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(0)==15,"Third block did not trigger");});
  h.runAfterDelay(8,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(0)==15,"Second set of three blocks did not rearm");m.edit("enabled",0,"");});
  h.runAfterDelay(10,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(0)==0,"Disabled rule left pulse high");h.succeed();});
 }
 @GameTest(template="combat",timeoutTicks=20) public static void RespawnedPlayerKillCountsAgainWhileSameTickReportsDeduplicate(GameTestHelper h){
  var c=core(h);var m=c.modules().add("combat_event");var r=m.signalRules().rows().getFirst();r.edit("eventCount",2);r.edit("eventChannel",1);r.edit("eventCooldown",0);var player=UUID.randomUUID();c.modules().combatEvents.emit(c,0,0,null,player);c.modules().combatEvents.emit(c,0,0,null,player);c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(0)==0,"One death counted twice");
  h.runAfterDelay(2,()->c.modules().combatEvents.emit(c,0,0,null,player));
  h.runAfterDelay(3,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(0)==15,"Respawned player's second death did not count");h.succeed();});
 }
 @GameTest(template="combat") public static void OldPartRowsAreDroppedCountsPersistAndDefaultToOne(GameTestHelper h){
  var rules=new SignalRules("combat_event");var r=rules.add();r.edit("eventKind",2);r.edit("eventCount",3);var saved=rules.save();var removed=saved.getCompound(0).copy();removed.putUUID("Id",UUID.randomUUID());removed.getCompound("Values").putDouble("eventKind",1);saved.add(removed);var loaded=new SignalRules("combat_event");loaded.load(saved);h.assertTrue(loaded.rows().size()==1&&loaded.rows().getFirst().value("eventKind")==2&&loaded.rows().getFirst().value("eventCount")==3,"Count save or removal migration failed");saved.getCompound(0).getCompound("Values").remove("eventCount");loaded.load(saved);h.assertTrue(loaded.rows().getFirst().value("eventCount")==1&&!loaded.rows().getFirst().edit("eventKind",1),"Legacy defaults or retired choice unsafe");h.succeed();
 }
}
