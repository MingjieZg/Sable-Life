package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_management_tests") @PrefixGameTestTemplate(false)
public final class ModuleManagementGameTests {
 private static HeartBlockEntity core(GameTestHelper h,boolean control){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,30,3));l.setBlockAndUpdate(p,(control?SableLife.REDSTONE_CORE:SableLife.HEART).get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
  var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));var c=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());
  for(int i=0;i<3;i++){int n=c.wireless().add();c.wireless().channel(n).set(0,1);c.wireless().channel(n).set(6,3);}return c;
 }
 private static net.neoforged.neoforge.common.util.FakePlayer player(GameTestHelper h,HeartBlockEntity c){var p=TestActors.creative(h.getLevel());var w=SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter());p.setPos(w.x,w.y,w.z);return p;}

 @GameTest(template = "combat", batch = "management_batch_groups")
 public static void BatchGroupingValidatesWholeSelectionAndPreservesModuleReferences(GameTestHelper h) {
  var core = core(h, false);
  var author = player(h, core);
  var modules = core.modules();
  var gun = modules.add("gun");
  var move = modules.add("move_set");
  var sound = modules.add("sound");
  gun.group("Old group");
  move.moveSet().edit("add", 0, "", modules);
  move.moveSet().edit("action_add", 0, "0|" + gun.id(), modules);
  var ordered = modules.all().stream().map(VirtualModule::id).toList();
  var references = ModuleConnections.links(modules.all());
  var menu = new HeartDashboardMenu(1, author.getInventory(), core);
  h.assertTrue(menu.snapshot().getBoolean("GroupTools"), "Server did not advertise group tools");
  int revision = modules.revision();
  var selected = List.of(gun.id(), sound.id());
  h.assertTrue(!groupRequest(menu, author, revision, List.of(gun.id(), UUID.randomUUID()), "Invalid"), "Unknown module accepted");
  h.assertTrue(!groupRequest(menu, author, revision, List.of(gun.id(), gun.id()), "Invalid"), "Duplicate module accepted");
  h.assertTrue(!groupRequest(menu, author, revision, selected, "x".repeat(33)), "Oversized group accepted");
  h.assertTrue(gun.group().equals("Old group") && modules.revision() == revision, "Invalid batch applied a partial move");
  h.assertTrue(groupRequest(menu, author, revision, selected, "左钳"), "Batch move rejected");
  h.assertTrue(!groupRequest(menu, author, revision, selected, "Stale"), "Stale batch accepted");
  h.assertTrue(gun.group().equals("左钳") && sound.group().equals("左钳") && move.group().isEmpty(), "Wrong group membership");
  h.assertTrue(modules.all().stream().map(VirtualModule::id).toList().equals(ordered)
          && ModuleConnections.links(modules.all()).equals(references), "Batch changed execution order or references");
  var loaded = new HeartModules();
  loaded.load(modules.save());
  h.assertTrue(loaded.find(gun.id()).group().equals("左钳") && loaded.find(sound.id()).group().equals("左钳"), "Batch grouping did not persist");
  int current = modules.revision();
  h.assertTrue(groupRequest(menu, author, current, selected, "左钳") && modules.revision() == current, "Repeated request changed revision");
  h.assertTrue(menu.action(author, current, gun.id(), "add", "particles", 0, "")
          && modules.all().getLast().group().isEmpty(), "Ordinary Add did not create an ungrouped module");
  var particles = modules.all().getLast();
  h.assertTrue(groupRequest(menu, author, modules.revision(), List.of(particles.id()), "左钳")
          && particles.group().equals("左钳"), "Existing group did not accept a module");
  h.assertTrue(groupRequest(menu, author, modules.revision(), List.of(particles.id()), "")
          && particles.group().isEmpty(), "Single module did not leave the group");
  h.assertTrue(groupRequest(menu, author, modules.revision(), selected, "") && gun.group().isEmpty() && sound.group().isEmpty(), "Batch ungroup failed");
  author.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
  h.assertTrue(!groupRequest(menu, author, modules.revision(), selected, "Forbidden"), "Spectator grouped modules");
  h.succeed();
 }

 private static boolean groupRequest(HeartDashboardMenu menu, net.minecraft.world.entity.player.Player player, int revision,
                                     List<UUID> ids, String group) {
  return groupRequest(menu, player, ModuleNetworking.Action.groupBatch(menu.containerId, revision, ids, group));
 }

 private static boolean groupRequest(HeartDashboardMenu menu, net.minecraft.world.entity.player.Player player,
                                     ModuleNetworking.Action request) {
  var decoded = roundTrip(request, player.level().registryAccess());
  return menu.action(player, decoded.revision(), decoded.module(), decoded.operation(), decoded.key(), decoded.value(), decoded.text());
 }

 @GameTest(template = "combat", batch = "management_group_members")
 public static void ConfirmReplacesExistingMembershipAtomically(GameTestHelper h) {
  var core = core(h, false);
  var author = player(h, core);
  var modules = core.modules();
  var outgoing = modules.add("gun");
  var retained = modules.add("sound");
  var incoming = modules.add("particles");
  var unrelated = modules.add("move_set");
  outgoing.group("Phase 2");
  retained.group("Phase 2");
  incoming.group("Other");
  unrelated.group("Other");
  unrelated.moveSet().edit("add", 0, "", modules);
  unrelated.moveSet().edit("action_add", 0, "0|" + outgoing.id(), modules);
  var references = ModuleConnections.links(modules.all());
  var order = modules.all().stream().map(VirtualModule::id).toList();
  var menu = new HeartDashboardMenu(1, author.getInventory(), core);
  h.assertTrue(menu.snapshot().getBoolean("GroupMemberEditing"), "Membership editing capability missing");
  int revision = modules.revision();
  var invalid = ModuleNetworking.Action.groupMembers(1, revision, List.of(incoming.id(), UUID.randomUUID()), "Phase 2");
  h.assertTrue(!groupRequest(menu, author, invalid) && outgoing.group().equals("Phase 2") && incoming.group().equals("Other")
          && modules.revision() == revision, "Invalid replacement partially applied");
  var valid = ModuleNetworking.Action.groupMembers(1, revision, List.of(retained.id(), incoming.id()), "Phase 2");
  h.assertTrue(groupRequest(menu, author, valid) && outgoing.group().isEmpty() && retained.group().equals("Phase 2")
          && incoming.group().equals("Phase 2") && unrelated.group().equals("Other") && modules.revision() == revision + 1,
          "One confirm did not add and remove exactly the requested members");
  h.assertTrue(!groupRequest(menu, author, valid), "Stale replacement was accepted");
  var saved = new HeartModules();
  saved.load(modules.save());
  h.assertTrue(saved.find(outgoing.id()).group().isEmpty() && saved.find(incoming.id()).group().equals("Phase 2")
          && saved.all().stream().map(VirtualModule::id).toList().equals(order)
          && ModuleConnections.links(saved.all()).equals(references), "Saved membership lost edits, order or references");
  var clear = ModuleNetworking.Action.groupMembers(1, modules.revision(), List.of(), "Phase 2");
  author.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
  h.assertTrue(!groupRequest(menu, author, clear) && retained.group().equals("Phase 2"), "Spectator cleared the group");
  author.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
  h.assertTrue(groupRequest(menu, author, clear) && retained.group().isEmpty() && incoming.group().isEmpty()
          && unrelated.group().equals("Other"), "Unchecking all members failed");
  h.succeed();
 }

 @GameTest(template = "combat", batch = "management_batch_codec")
 public static void BatchGroupingCodecAcceptsFullCapacity(GameTestHelper h) {
  var ids = new ArrayList<UUID>();
  for (int i = 0; i < HeartModules.LIMIT; i++) ids.add(UUID.randomUUID());
  var request = ModuleNetworking.Action.groupBatch(1, 0, ids, "🦀".repeat(32));
  h.assertTrue(roundTrip(request, h.getLevel().registryAccess()).equals(request), "Max-size batch did not round trip");
  var replacement = ModuleNetworking.Action.groupMembers(1, 0, ids, "🦀".repeat(32));
  h.assertTrue(roundTrip(replacement, h.getLevel().registryAccess()).equals(replacement), "Max-size membership did not round trip");
  ids.add(UUID.randomUUID());
  boolean rejected = false;
  try {
   roundTrip(ModuleNetworking.Action.groupBatch(1, 0, ids, "Group"), h.getLevel().registryAccess());
  } catch (io.netty.handler.codec.EncoderException expected) {
   rejected = true;
  }
  h.assertTrue(rejected, "Codec accepted a batch beyond the module limit");
  rejected = false;
  try {
   roundTrip(ModuleNetworking.Action.groupMembers(1, 0, ids, "Group"), h.getLevel().registryAccess());
  } catch (io.netty.handler.codec.EncoderException expected) {
   rejected = true;
  }
  h.assertTrue(rejected, "Codec accepted membership beyond the module limit");
  h.succeed();
 }

 private static ModuleNetworking.Action roundTrip(ModuleNetworking.Action action, RegistryAccess registries) {
  var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
  try {
   ModuleNetworking.Action.CODEC.encode(buffer, action);
   return ModuleNetworking.Action.CODEC.decode(buffer);
  } finally {
   buffer.release();
  }
 }
 @GameTest(template="combat",batch="management_metadata")
 public static void GroupsPersistThroughSaveCopyAndPresetWithoutChangingRuntimeOrder(GameTestHelper h){
  var modules=new HeartModules();var a=modules.add("gun");var b=modules.add("arrow");h.assertTrue(a.group("  Phase 2  ")&&a.group().equals("Phase 2"),"Group trim failed");b.group("Phase 2");
  h.assertTrue(!a.group("x".repeat(33))&&!a.group("bad\nname")&&!a.group(null)&&a.group().equals("Phase 2"),"Invalid group changed metadata");
  var loaded=new HeartModules();loaded.load(modules.save());h.assertTrue(loaded.all().get(0).id().equals(a.id())&&loaded.all().get(1).id().equals(b.id())&&loaded.find(a.id()).group().equals("Phase 2"),"Save changed group/order/identity");
  h.assertTrue(modules.copy(a.id()).group().equals("Phase 2")&&ModulePreset.create(a.save()).group().equals("Phase 2"),"Copy/preset lost group");h.assertTrue(new VirtualModule("gun").group().isEmpty(),"Legacy default grouped");h.succeed();
 }
 @GameTest(template="combat",batch="management_folders")
 public static void CollapsedFoldersRevealSearchMatchesAndLeaveExecutionOrderUntouched(GameTestHelper h){
  var a=new VirtualModule("gun");var b=new VirtualModule("arrow");var c=new VirtualModule("flame");a.group("Phase 2");c.group("Phase 2");var original=List.of(a,b,c);
  var rows=ModuleFolders.rows(original,Set.of("Phase 2"),false);h.assertTrue(rows.size()==2&&rows.get(0).header()&&rows.get(0).count()==2&&rows.get(1).module()==b,"Collapse hid unrelated module or lost count");
  var search=ModuleFolders.rows(List.of(c),Set.of("Phase 2"),true);h.assertTrue(search.size()==2&&search.get(1).module()==c,"Search did not expose collapsed match");h.assertTrue(original.equals(List.of(a,b,c)),"Presentation reordered executable list");h.succeed();
 }
 @GameTest(template="combat",batch="management_references")
 public static void ReferencesIdentifyExactMoveAndSurviveReorderAndMissingTargets(GameTestHelper h){
  var modules=new HeartModules();var gun=modules.add("gun");var move=modules.add("move_set");for(int i=0;i<2;i++){move.moveSet().edit("add",0,"",modules);move.moveSet().edit("action_add",0,i+"|"+gun.id(),modules);}
  var summon=modules.add("summoned_weapon");summon.summonAttack(gun.id());var hit=modules.add("hit_counter");hit.hitSource(gun.id(),1);var slash=modules.add("slash");slash.impactField(gun.id());var event=modules.add("combat_event");event.signalRules().add().source(gun.id());
  var before=ModuleConnections.links(modules.all());h.assertTrue(before.size()==6&&before.stream().anyMatch(l->l.kind().equals("move")&&l.move()==1),"Reference kinds or exact move lost");modules.reorder(gun.id(),1);
  var snapshot=ModuleConnections.references(ModuleConnections.links(modules.all()),gun.id());h.assertTrue(snapshot.getInt("Incoming")==6&&snapshot.getUUID("Selected").equals(gun.id()),"Reorder broke incoming references");modules.remove(gun.id());h.assertTrue(ModuleConnections.links(modules.all()).size()==6,"Deletion silently erased references rather than reporting missing targets");h.succeed();
 }
 @GameTest(template="combat",batch="management_trace")
 public static void TraceShowsEachActualContributionAndDoesNotChangeMaximumMerge(GameTestHelper h){
  var c=core(h,true);var a=c.modules().add("wireless_redstone");var b=c.modules().add("wireless_redstone");
  c.wireless().programOutputs("a",Map.of(0,12),Map.of(a.id(),Map.of(0,4),b.id(),Map.of(0,12)));
  h.assertTrue(c.wireless().output(0)==12&&c.wireless().moduleContributions(0).equals(Map.of(a.id(),4,b.id(),12)),"Tracing changed output or discarded weaker source");
  c.wireless().programOutputs("b",Map.of(0,7),Map.of(a.id(),Map.of(0,7)));c.wireless().programOutputs("a",Map.of());h.assertTrue(c.wireless().output(0)==7&&c.wireless().moduleContributions(0).equals(Map.of(a.id(),7)),"Clearing one category removed another category");
  c.wireless().master(false);h.assertTrue(c.wireless().output(0)==0&&c.wireless().moduleContributions(0).isEmpty(),"Master off left phantom sources");h.succeed();
 }
 @GameTest(template="combat",batch="management_actual_publish")
 public static void RealRelayTraceUsesActualFollowPowerAndClearsWhenDisabled(GameTestHelper h){
  var c=core(h,true);var m=c.modules().add("wireless_redstone");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().east(),Direction.UP)));m.edit("redstoneStrengthMode",1,"");m.edit("redstoneChannel",2,"");m.edit("trigger",2,"");m.edit("channel",1,"");
  c.wireless().programOutputs("input",Map.of(0,6));c.modules().tick(c);h.assertTrue(c.wireless().output(1)==6&&c.wireless().moduleContributions(1).getOrDefault(m.id(),0)==6,"Actual follow strength replaced by configured15");
  var snapshot=ChannelDiagnostics.snapshot(c,1,ModuleConnections.endpoints(c.modules().all()));h.assertTrue(snapshot.getInt("Power")==6&&snapshot.getList("Sources",10).size()==1,"Diagnostics missed live relay");
  m.edit("enabled",0,"");c.modules().tick(c);h.assertTrue(c.wireless().moduleContributions(1).isEmpty(),"Disabled relay retained contribution");h.succeed();
 }
 @GameTest(template="combat",batch="management_viewers")
 public static void ViewerInspectAndGroupActionsValidateRevisionAndDoNotActivateLife(GameTestHelper h){
  var c=core(h,true);var p=player(h,c);var m=c.modules().add("signal_logic");m.edit("enabled",0,"");var menu=new HeartDashboardMenu(1,p.getInventory(),c);int revision=c.modules().revision();
  h.assertTrue(menu.action(p,revision,m.id(),"group","",0,"Phase 2")&&!m.enabled()&&c.health().state()==HeartState.DORMANT,"Grouping enabled module or life");
  h.assertTrue(!menu.action(p,revision,m.id(),"group","",0,"stale")&&m.group().equals("Phase 2"),"Stale grouping accepted");h.assertTrue(!menu.action(p,c.modules().revision(),m.id(),"group","",0,"x".repeat(33)),"Oversized grouping accepted");
  var other=new HeartDashboardMenu(2,p.getInventory(),c);h.assertTrue(menu.action(p,0,m.id(),"inspect","",1,"")&&menu.snapshot().getCompound("ChannelDiagnostics").getInt("Channel")==1&&other.snapshot().getCompound("ChannelDiagnostics").getInt("Channel")==0,"Inspection leaked across viewers");
  for(double invalid:new double[]{-1,3,.5,Double.NaN})h.assertTrue(!menu.action(p,0,m.id(),"inspect","",invalid,""),"Invalid inspection accepted");h.succeed();
 }
 @GameTest(template="combat",batch="management_life")
 public static void LifeCoreManagementWorksBeforeActivation(GameTestHelper h){var c=core(h,false);var p=player(h,c);var m=c.modules().add("gun");var menu=new HeartDashboardMenu(1,p.getInventory(),c);h.assertTrue(menu.action(p,c.modules().revision(),m.id(),"group","",0,"Attack")&&c.health().state()==HeartState.DORMANT,"Life core grouping required activation");h.assertTrue(menu.snapshot().getCompound("ChannelDiagnostics").getInt("Count")==3,"Dormant life core channel data absent");h.succeed();}
 @GameTest(template="combat",batch="management_bounds")
 public static void ReferenceSnapshotsStayBoundedWithAccurateTotal(GameTestHelper h){var id=UUID.randomUUID();var rows=new ArrayList<ModuleConnections.Link>();for(int i=0;i<800;i++)rows.add(new ModuleConnections.Link(UUID.randomUUID(),id,"move","",i));var tag=ModuleConnections.references(rows,id);h.assertTrue(tag.getInt("Incoming")==800&&tag.getInt("Total")==800&&tag.getList("Rows",10).size()==512,"Bounded snapshot lost total or exceeded limit");h.succeed();}
}
