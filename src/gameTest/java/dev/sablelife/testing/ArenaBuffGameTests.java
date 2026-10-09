package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayer;
import java.util.*;
@GameTestHolder("sablelife_arena_tests") @PrefixGameTestTemplate(false)
public final class ArenaBuffGameTests {
 private static HeartBlockEntity core(GameTestHelper h){var l=h.getLevel();var p=h.absolutePos(new BlockPos(5,40,5));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}
 private static VirtualModule arena(HeartBlockEntity c){var m=c.modules().add("arena");m.arenaCenter(ModuleArena.corePosition(c),c.getLevel().dimension().location().toString());m.edit("warningRange",4,"");m.edit("arenaBuffEnabled",1,"");return m;}
 private static FakePlayer player(GameTestHelper h,HeartBlockEntity c,double distance,GameType mode){var p=TestActors.creative(h.getLevel());p.setGameMode(mode);p.setPos(ModuleArena.corePosition(c).add(distance,0,0));h.getLevel().players().add(p);return p;}
 private static void expire(FakePlayer p,int ticks){try{var m=net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("tickEffects");m.setAccessible(true);for(int i=0;i<ticks;i++)m.invoke(p);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 @GameTest(template="combat",batch="arena_buff_bounds")
 public static void GroupsAreBoundedPersistentAndPresetsUnbindOnlyChannels(GameTestHelper h){var m=new VirtualModule("arena");h.assertTrue(m.value("arenaBuffEnabled")==0&&m.arenaBuffs().groups().isEmpty(),"Legacy behavior changed");var group=m.arenaBuffs().add();
  h.assertTrue(group.effect("add","minecraft:speed",0)&&group.effect("level","minecraft:speed",3),"Persistent effect rejected");
  h.assertTrue(!group.effect("add","minecraft:instant_health",0)&&!group.effect("duration","minecraft:speed",60)&&!group.edit("channel",Double.NaN)&&!group.edit("source",3)&&!group.edit("channel",-1)&&!group.effect("level","minecraft:speed",6),"Unsafe effect/control accepted");
  var ids=net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.keySet().stream().map(Object::toString).filter(ArenaBuffs::persistent).toList();
  for(var id:ids)if(group.editor().effects().size()<ArenaBuffs.EFFECT_LIMIT)group.effect("add",id,0);
  h.assertTrue(group.editor().effects().size()==16&&!group.effect("add","minecraft:luck",0),"Effect cap exceeded");
  group.edit("source",1);group.edit("channel",3);m.edit("arenaBuffEnabled",1,"");var restored=VirtualModule.load(m.save());var g=restored.arenaBuffs().find(group.id());
  h.assertTrue(g!=null&&g.source()==1&&g.channel()==3&&g.editor().effects().getFirst().ticks()==40&&g.editor().effects().getFirst().level()==3,"Group save/reload lost settings");
  var preset=ModulePreset.create(m.save());h.assertTrue(preset.arenaBuffs().groups().getFirst().channel()==0&&preset.arenaBuffs().groups().getFirst().editor().effects().getFirst().level()==3,"Preset lost effects or retained foreign channel");
  m.wirelessChannelDeleted(0);h.assertTrue(group.channel()==2,"Shift did not follow channel");m.wirelessChannelDeleted(1);h.assertTrue(group.channel()==0,"Deleted channel remained bound");
  for(int i=1;i<ArenaBuffs.LIMIT;i++)h.assertTrue(m.arenaBuffs().add()!=null,"Group cap too low");h.assertTrue(m.arenaBuffs().add()==null,"Group cap exceeded");
  var data=m.save();var list=data.getList("ArenaBuffGroups",10);list.add(list.getCompound(0).copy());h.assertTrue(VirtualModule.load(data).arenaBuffs().groups().size()==16,"Oversized load grew groups");
  var second=m.arenaBuffs().groups().get(1);h.assertTrue(m.arenaBuffs().move(second.id(),-1)&&m.arenaBuffs().groups().getFirst()==second,"Reordering failed");h.succeed();
 }
 @GameTest(template="combat",batch="arena_buff_range")
 public static void SharedPlayerRangeRefreshesOnceAndPreservesLongPotion(GameTestHelper h){var c=core(h);var m=arena(c);var g=m.arenaBuffs().add();g.effect("add","minecraft:speed",0);g.effect("add","minecraft:resistance",0);var strong=m.arenaBuffs().add();strong.effect("add","minecraft:speed",0);strong.effect("level","minecraft:speed",3);c.tryActivate(TestActors.creative(h.getLevel()));
  var near=player(h,c,3,GameType.SURVIVAL);near.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,200,2));var far=player(h,c,5,GameType.SURVIVAL);var creative=player(h,c,1,GameType.CREATIVE);var spectator=player(h,c,1,GameType.SPECTATOR);
  var runtime=new ArenaBuffRuntime();runtime.tick(c);h.assertTrue(near.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier()==2&&near.getEffect(MobEffects.MOVEMENT_SPEED).getDuration()==40,"Groups did not merge strongest level");
  h.assertTrue(near.getEffect(MobEffects.DAMAGE_RESISTANCE).getDuration()==200&&!far.hasEffect(MobEffects.MOVEMENT_SPEED)&&!creative.hasEffect(MobEffects.MOVEMENT_SPEED)&&!spectator.hasEffect(MobEffects.MOVEMENT_SPEED),"Range/player filter or potion preservation failed");
  expire(near,1);runtime.tick(c);h.assertTrue(near.getEffect(MobEffects.MOVEMENT_SPEED).getDuration()==39,"Same tick refreshed twice");m.edit("arenaBuffEnabled",0,"");runtime.tick(c);expire(near,39);
  h.assertTrue(!near.hasEffect(MobEffects.MOVEMENT_SPEED)&&near.hasEffect(MobEffects.DAMAGE_RESISTANCE),"Stopped buffs did not expire naturally or removed potion");
  for(var p:List.of(near,far,creative,spectator))h.getLevel().players().remove(p);h.succeed();
 }
 @GameTest(template="combat",batch="arena_buff_phase",timeoutTicks=45)
 public static void HalfHealthSignalsChangeGroupsThenOldBuffExpires(GameTestHelper h){var c=core(h);var m=arena(c);
  for(int i=0;i<2;i++){c.wireless().add();c.wireless().channel(i).set(0,1);c.wireless().channel(i).set(6,3);}
  var high=c.modules().add("health_phase");high.edit("phaseLower",50,"");high.edit("phaseUpper",100,"");high.edit("phaseChannel",1,"");
  var low=c.modules().add("health_phase");low.edit("phaseLower",0,"");low.edit("phaseUpper",49,"");low.edit("phaseChannel",2,"");
  var a=m.arenaBuffs().add();a.edit("source",1);a.edit("channel",1);a.effect("add","minecraft:strength",0);
  var b=m.arenaBuffs().add();b.edit("source",1);b.edit("channel",2);b.effect("add","minecraft:speed",0);c.tryActivate(TestActors.creative(h.getLevel()));
  var p=player(h,c,2,GameType.SURVIVAL);var phases=new ModuleHealthPhase();phases.tick(c);var runtime=new ArenaBuffRuntime();runtime.tick(c);
  h.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST)&&!p.hasEffect(MobEffects.MOVEMENT_SPEED)&&c.wireless().output(0)==15,"Upper phase failed to grant group");
  c.damage(51);phases.tick(c);h.assertTrue(!a.powered(c)&&b.powered(c)&&c.wireless().output(1)==15,"Half-health signal failed to switch");
  h.runAfterDelay(20,()->{expire(p,40);runtime.tick(c);h.assertTrue(!p.hasEffect(MobEffects.DAMAGE_BOOST)&&p.hasEffect(MobEffects.MOVEMENT_SPEED),"Old stage persisted or new group failed");
   c.damage(c.health().maximum());runtime.tick(c);expire(p,40);h.assertTrue(!p.hasEffect(MobEffects.MOVEMENT_SPEED),"Death retained refreshing buff");h.getLevel().players().remove(p);h.succeed();});
 }
 @GameTest(template="combat",batch="arena_buff_external",timeoutTicks=25)
 public static void ExternalFrequencyControlsGroupAndMasterOffStopsIt(GameTestHelper h){var c=core(h);var m=arena(c);c.wireless().add();var row=c.wireless().channel(0);row.set(0,1);row.set(6,1);row.frequency(false,new ItemStack(Items.AMETHYST_SHARD));row.frequency(true,new ItemStack(Items.FEATHER));var g=m.arenaBuffs().add();g.edit("source",2);g.edit("channel",1);g.effect("add","minecraft:speed",0);c.tryActivate(TestActors.creative(h.getLevel()));
  var key=net.createmod.catnip.data.Couple.create(com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency.of(new ItemStack(Items.AMETHYST_SHARD)),com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency.of(new ItemStack(Items.FEATHER)));var location=BlockPos.containing(ModuleArena.corePosition(c).add(0,1,3));
  var transmitter=new com.simibubi.create.content.redstone.link.IRedstoneLinkable(){public int getTransmittedStrength(){return 7;}public void setReceivedStrength(int v){}public boolean isListening(){return false;}public boolean isAlive(){return true;}public net.createmod.catnip.data.Couple<com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency> getNetworkKey(){return key;}public BlockPos getLocation(){return location;}};
  com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(h.getLevel(),transmitter);c.wireless().tick(h.getLevel());
  h.runAfterDelay(5,()->{c.wireless().tick(h.getLevel());com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(h.getLevel(),transmitter);h.assertTrue(g.powered(c),"External channel did not power group");var p=player(h,c,2,GameType.SURVIVAL);var runtime=new ArenaBuffRuntime();runtime.tick(c);h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED),"External powered group did not grant buff");c.wireless().master(false);h.assertTrue(!g.powered(c),"Wireless master off retained group signal");expire(p,40);h.assertTrue(!p.hasEffect(MobEffects.MOVEMENT_SPEED),"External off buff did not expire");h.getLevel().players().remove(p);com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(h.getLevel(),transmitter);h.succeed();});
 }
 @GameTest(template="combat",batch="arena_buff_permissions")
 public static void GroupMenuValidatesRevisionIdentityAndNestedEffects(GameTestHelper h){var c=core(h);var m=arena(c);var p=TestActors.creative(h.getLevel());p.setPos(ModuleArena.corePosition(c));var menu=new ArenaBuffMenu(1,p.getInventory(),c,m,null);int rev=c.modules().revision();
  h.assertTrue(menu.action(p,rev,new UUID(0,0),"add",0),"Group creation rejected");var group=m.arenaBuffs().groups().getFirst();
  h.assertTrue(!menu.action(p,rev,group.id(),"channel",1)&&!menu.action(p,c.modules().revision(),UUID.randomUUID(),"delete",0),"Stale revision or forged identity accepted");
  var effects=EffectSelectionMenu.arena(2,p.getInventory(),c,m,group);h.assertTrue(effects.action(p,group.editor().id(),"add",0,"minecraft:speed"),"Nested effect rejected");
  h.assertTrue(!effects.action(p,group.editor().id(),"duration",600,"minecraft:speed")&&!effects.action(p,group.editor().id(),"add",0,"minecraft:instant_damage"),"Nested editor bypassed buff validation");
  p.setGameMode(GameType.SURVIVAL);h.assertTrue(!menu.action(p,c.modules().revision(),group.id(),"delete",0),"Survival edited groups");p.setGameMode(GameType.CREATIVE);m.arenaBuffs().remove(group.id());h.assertTrue(!effects.stillValid(p)&&!effects.action(p,group.editor().id(),"level",2,"minecraft:speed"),"Deleted group still editable");h.succeed();
 }
}
