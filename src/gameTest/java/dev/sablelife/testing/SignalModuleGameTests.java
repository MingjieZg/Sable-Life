package dev.sablelife.testing;

import dev.sablelife.module.*;
import dev.sablelife.heart.*;
import dev.sablelife.attack.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_signal_tests") @PrefixGameTestTemplate(false)
public final class SignalModuleGameTests {
 private static HeartBlockEntity core(GameTestHelper h){var c=SlashGuardGameTests.core(h);for(int i=0;i<8;i++){int index=c.wireless().add();c.wireless().channel(index).set(6,3);c.wireless().channel(index).set(0,1);}c.tryActivate(TestActors.creative(h.getLevel()));return c;}
 private static VirtualModule logic(HeartBlockEntity c,int input,int output){var m=c.modules().add("signal_logic");m.signalRules().rows().getFirst().edit("signalInput",input);m.edit("logicChannel",output,"");return m;}
 private static SignalRules.Row event(HeartBlockEntity c,int kind,int filter,int channel){var m=c.modules().all().stream().filter(v->v.type().equals("combat_event")).findFirst().orElseGet(()->c.modules().add("combat_event"));var r=m.signalRules().rows().getFirst();r.edit("eventKind",kind);r.edit("eventFilter",filter);r.edit("eventChannel",channel);r.edit("eventPulse",4);r.edit("eventCooldown",0);return r;}
 private static void input(HeartBlockEntity c,Map<Integer,Integer> inputs){c.wireless().programOutputs("test",inputs);}
 @GameTest(template="combat",batch="logic_truth",timeoutTicks=30) public static void AndOrNotAndDisabledRowsUseActualChannels(GameTestHelper h){
  var c=core(h);var m=logic(c,1,3);var second=m.signalRules().add();second.edit("signalInput",2);input(c,Map.of(0,15));c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(2)==0,"AND ignored false input");
  h.runAfterDelay(1,()->{m.edit("logicMatch",1,"");c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(2)==15,"OR failed");second.edit("signalCondition",1);m.edit("logicMatch",0,"");});
  h.runAfterDelay(3,()->{c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(2)==15,"AND with NOT failed");second.edit("enabled",0);input(c,Map.of());});
  h.runAfterDelay(5,()->{c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(2)==0,"Disabled row became true or false input was ignored");m.edit("enabled",0,"");h.succeed();});
 }
 @GameTest(template="combat",batch="logic_invalid") public static void InvalidNotEmptyAndSelfLoopFailClosed(GameTestHelper h){
  var c=core(h);var a=logic(c,0,2);a.signalRules().rows().getFirst().edit("signalCondition",1);var b=logic(c,99,3);b.signalRules().rows().getFirst().edit("signalCondition",1);var self=logic(c,4,4);self.signalRules().rows().getFirst().edit("signalCondition",1);var empty=logic(c,1,5);empty.signalRules().remove(empty.signalRules().rows().getFirst().id());c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(1)==0&&c.wireless().output(2)==0&&c.wireless().output(3)==0&&c.wireless().output(4)==0,"Unbound/missing/self/empty condition produced output");h.succeed();
 }
 @GameTest(template="combat",batch="logic_snapshot",timeoutTicks=20) public static void LogicChainUsesOneSnapshotAndDoesNotDependOnOrder(GameTestHelper h){
  var c=core(h);var a=logic(c,1,2);var b=logic(c,2,3);input(c,Map.of(0,15));c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(1)==15&&c.wireless().output(2)==0,"Logic recursively consumed same-frame output");c.modules().reorder(b.id(),-1);
  h.runAfterDelay(2,()->{c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(2)==15,"Logic chain depends on list order");c.modules().remove(a.id());});
  h.runAfterDelay(5,()->{c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(1)==0&&c.wireless().output(2)==0,"Deleted logic output stayed powered");h.succeed();});
 }
 @GameTest(template="combat",batch="logic_pulse",timeoutTicks=25) public static void RisingEdgePulseDoesNotRepeatAndDisablingClearsOutput(GameTestHelper h){
  var c=core(h);var m=logic(c,1,2);m.edit("logicMode",1,"");m.edit("logicPulse",2,"");input(c,Map.of(0,15));c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(1)==15,"Rising edge failed");
  h.runAfterDelay(4,()->{c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(1)==0,"Held condition repeated pulse");input(c,Map.of());});
  h.runAfterDelay(6,()->input(c,Map.of(0,15)));
  h.runAfterDelay(7,()->{c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(1)==15,"Second rising edge did not pulse");m.edit("enabled",0,"");});
  h.runAfterDelay(9,()->{c.modules().signalLogic.tick(c);h.assertTrue(c.wireless().output(1)==0,"Disable left pulse on");h.succeed();});
 }
 @GameTest(template="combat",batch="signal_save") public static void RulesPersistCopyIndependentlyAndPresetsUnbindReferences(GameTestHelper h){
  var c=core(h);var logic=logic(c,2,5);var event=c.modules().add("combat_event");var row=event.signalRules().rows().getFirst();var slash=c.modules().add("slash");row.source(slash.id());row.edit("eventChannel",6);row.edit("eventPulse",23);
  var copy=c.modules().copy(event.id());copy.signalRules().rows().getFirst().edit("eventPulse",40);h.assertTrue(row.value("eventPulse")==23&&copy.signalRules().rows().getFirst().source().equals(slash.id()),"Copy shared rule data or lost source");
  var restored=VirtualModule.load(event.save());h.assertTrue(restored.signalRules().rows().getFirst().source().equals(slash.id())&&restored.signalRules().rows().getFirst().value("eventChannel")==6,"Rules/source did not persist");
  var preset=ModulePreset.create(event.save());h.assertTrue(preset.signalRules().rows().getFirst().source()==null&&preset.signalRules().rows().getFirst().value("eventChannel")==0,"Portable preset retained foreign references");
  var lp=ModulePreset.create(logic.save());h.assertTrue(lp.value("logicChannel")==0&&lp.signalRules().rows().getFirst().value("signalInput")==0,"Logic preset retained channels");c.wireless().remove(1);h.assertTrue(logic.value("logicChannel")==4&&logic.signalRules().rows().getFirst().value("signalInput")==0&&row.value("eventChannel")==5,"Deleted channel references were redirected");h.succeed();
 }
 @GameTest(template="combat",batch="signal_bounds") public static void RowLimitsAndMalformedValuesAreBounded(GameTestHelper h){
  for(String type:List.of("signal_logic","combat_event")){var rules=new SignalRules(type);for(int i=0;i<SignalRules.limit(type);i++)h.assertTrue(rules.add()!=null,"Limit reached early");h.assertTrue(rules.add()==null,"Row limit exceeded");var row=rules.rows().getFirst();h.assertTrue(!row.edit(type.equals("signal_logic")?"signalInput":"eventPulse",Double.NaN)&&!row.edit("enabled",2),"Invalid numeric input accepted");var saved=rules.save();saved.add(saved.getFirst().copy());rules.load(saved);h.assertTrue(rules.rows().size()==SignalRules.limit(type),"Duplicate row ID accepted");}h.succeed();
 }
 @GameTest(template="combat",batch="event_cooldown",timeoutTicks=30) public static void IndependentEventRulesPulseDeduplicateAndRespectSourceAndCooldown(GameTestHelper h){
  var c=core(h);var source=c.modules().add("slash");var r=event(c,2,0,2);r.source(source.id());r.edit("eventCooldown",6);var module=c.modules().all().stream().filter(m->m.type().equals("combat_event")).findFirst().orElseThrow();var other=module.signalRules().add();other.edit("eventKind",2);other.edit("eventChannel",3);other.edit("eventPulse",2);other.edit("eventCooldown",0);
  c.modules().combatEvents.emit(c,2,1,UUID.randomUUID(),UUID.randomUUID());c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(1)==0&&c.wireless().output(2)==15,"Source filter or independent rule failed");
  h.runAfterDelay(3,()->{c.modules().combatEvents.emit(c,2,1,source.id(),UUID.randomUUID());});
  h.runAfterDelay(5,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(1)==15,"Matching source event did not pulse");c.modules().combatEvents.emit(c,2,1,source.id(),UUID.randomUUID());});
  h.runAfterDelay(9,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(1)==0,"Cooldown extended pulse or stacked repeated hits");module.edit("enabled",0,"");h.succeed();});
 }
 @GameTest(template="combat",batch="event_native_kill",timeoutTicks=20) public static void ActualAttributedKillSignalsOnlyTheKillerAndMatchingModule(GameTestHelper h){
  var c=core(h);var m=c.modules().add("slash");var r=event(c,0,1,2);r.source(m.id());var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setPos(h.absolutePos(new BlockPos(8,4,8)).getCenter());h.getLevel().addFreshEntity(pig);
  ModuleDamage.hurt(pig,ModuleDamage.physical(h.getLevel()),30,c,m);
  h.runAfterDelay(2,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(1)==15,"Actual attributed death did not emit signal");h.succeed();});
 }
 @GameTest(template="combat",batch="event_cancel_death",timeoutTicks=20) public static void CancelledDeathsDoNotBecomeKillEvents(GameTestHelper h){
  var c=core(h);var m=c.modules().add("slash");event(c,0,1,2);var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setPos(h.absolutePos(new BlockPos(8,4,8)).getCenter());h.getLevel().addFreshEntity(pig);
  java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDeathEvent> cancel=e->{if(e.getEntity()==pig)e.setCanceled(true);};net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,cancel);
  try{ModuleDamage.hurt(pig,ModuleDamage.physical(h.getLevel()),30,c,m);}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);}
  h.runAfterDelay(2,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(1)==0,"Cancelled death emitted event");pig.discard();h.succeed();});
 }

 @GameTest(template="combat",batch="signal_menu_security") public static void RuleMenuRejectsStaleRevisionAndWrongModuleAfterRemoval(GameTestHelper h){
  var c=core(h);var m=c.modules().add("combat_event");var p=TestActors.creative(h.getLevel());p.setPos(ModuleArena.corePosition(c));var menu=new SignalRuleMenu(5,p.getInventory(),c,m,null);int revision=c.modules().revision();var id=m.signalRules().rows().getFirst().id();
  h.assertTrue(!menu.action(p,revision,id,"eventChannel",2),"Active creature authoring was allowed");c.resetForCreative(p);revision=c.modules().revision();
  h.assertTrue(!menu.action(p,revision-1,id,"eventChannel",2)&&!menu.action(p,revision,id,"eventSource",999),"Stale or invalid source edit accepted");h.assertTrue(menu.action(p,revision,id,"eventChannel",2),"Valid dormant rule edit rejected");c.modules().remove(m.id());h.assertTrue(!menu.action(p,c.modules().revision(),id,"eventPulse",20),"Removed module menu remained editable");h.succeed();
 }
 @GameTest(template="combat",batch="event_native_shield",timeoutTicks=20) public static void ActualBlockableSlashProducesShieldEventWithoutDamageOrEffects(GameTestHelper h)throws Exception {
  var c=core(h);var slash=SlashGuardGameTests.slash(c);slash.edit("slashBlockable",1,"");slash.edit("damage",4,"");var row=event(c,2,1,2);row.source(slash.id());var player=new SlashGuardGameTests.Defender(h.getLevel());player.setPos(SlashGuardGameTests.position(h,c,slash));player.setYRot(180);player.setYHeadRot(180);var shield=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD);player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,shield);player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);var f=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("useItemRemaining");f.setAccessible(true);f.setInt(player,shield.getUseDuration(player)-6);h.getLevel().addNewPlayer(player);
  new ModuleSlash().tick(c);
  h.runAfterDelay(2,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(1)==15&&player.getHealth()==20&&!player.isOnFire(),"Actual shield block did not produce event or leaked damage/burn");slash.edit("enabled",0,"");h.getLevel().removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);h.succeed();});
 }
 @GameTest(template="combat",batch="signal_paste") public static void WholeConfigurationCopyRemapsEventSourcesAndAreaTemplates(GameTestHelper h){
  var c=core(h);var source=c.modules().add("slash");var area=c.modules().add("surface_field");source.impactField(area.id());var e=c.modules().add("combat_event");e.signalRules().rows().getFirst().source(source.id());e.signalRules().rows().getFirst().edit("eventChannel",2);var l=logic(c,2,3);var data=c.copyConfiguration();var player=TestActors.creative(h.getLevel());c.resetForCreative(player);
  h.assertTrue(c.pasteConfiguration(player,data),"Configuration paste rejected");var modules=c.modules().all();h.assertTrue(modules.get(0).impactFieldId().equals(modules.get(1).id())&&modules.get(2).signalRules().rows().getFirst().source().equals(modules.get(0).id())&&!modules.get(0).id().equals(source.id()),"Paste left foreign source/template identity");h.assertTrue(modules.get(3).signalRules().rows().getFirst().value("signalInput")==2&&modules.get(3).value("logicChannel")==3,"Paste lost channel configuration");h.succeed();
 }
 @GameTest(template="combat",batch="signal_clear",timeoutTicks=25) public static void TriggerAndMasterGatingAndReloadDiscardPendingEvents(GameTestHelper h){
  var c=core(h);event(c,2,0,2);var m=c.modules().all().stream().filter(x->x.type().equals("combat_event")).findFirst().orElseThrow();m.edit("trigger",2,"");m.edit("channel",7,"");c.modules().combatEvents.emit(c,2,1,null,UUID.randomUUID());c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(1)==0,"Unpowered listener accepted event");
  input(c,Map.of(6,15));h.runAfterDelay(1,()->c.modules().combatEvents.emit(c,2,1,null,UUID.randomUUID()));
  h.runAfterDelay(3,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(1)==15,"Powered listener lost event");c.wireless().master(false);h.assertTrue(c.wireless().output(1)==0,"Master off left event powered");c.wireless().master(true);input(c,Map.of(6,15));});
  h.runAfterDelay(5,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(1)==0,"Master restart replayed pulse");c.modules().combatEvents.emit(c,2,1,null,UUID.randomUUID());var saved=c.modules().save();c.modules().load(saved);});
  h.runAfterDelay(7,()->{c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(1)==0,"Reload retained pending event");h.succeed();});
 }
 @GameTest(template="combat",batch="event_source_removed") public static void RemovedAttackSourceDoesNotBecomeAllAttacks(GameTestHelper h){
  var c=core(h);var attack=c.modules().add("slash");var r=event(c,2,0,2);r.source(attack.id());c.modules().remove(attack.id());c.modules().combatEvents.emit(c,2,1,attack.id(),UUID.randomUUID());c.modules().combatEvents.tick(c);h.assertTrue(c.wireless().output(1)==0,"Deleted attack source still accepted events");h.succeed();
 }

}
