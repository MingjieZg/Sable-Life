package dev.sablelife.testing;

import java.util.*;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.ownership.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sablelife_relations_tests") @PrefixGameTestTemplate(false)
public final class RelationsAndPullGameTests {
    private static HeartBlockEntity core(GameTestHelper h,int offset){var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4+offset,40,4));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));var c=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());LoadedCoreIndex.observe(c);return c;}
    @GameTest(template="combat",batch="relations_exclusions")
    public static void ExclusionsApplyToPlayersAndAllSurviveReorderingAndSave(GameTestHelper h){var c=core(h,0);var p=TestActors.creative(h.getLevel());p.setGameMode(GameType.SURVIVAL);h.getLevel().addNewPlayer(p);p.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()).add(3,0,0));
        var box=p.getBoundingBox().inflate(4);c.relations().exclude(p.getGameProfile().getName());for(int mode:new int[]{0,2})h.assertTrue(ModuleTargets.query(h.getLevel(),c.creatureId(),box,e->ModuleTargets.matches(e,mode)).stream().noneMatch(e->e.getUUID().equals(p.getUUID())),"Excluded player acquired in mode "+mode);
        var first=c.modules().add("sensor");c.modules().add("surface_field");c.modules().remove(first.id());var saved=c.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(HeartBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(c.getBlockPos(),c.getBlockState(),saved,h.getLevel().registryAccess());h.assertTrue(restored.relations().excludes(p.getGameProfile().getName())&&!CoreRelations.targetAllowed(h.getLevel(),c.creatureId(),p),"Exclusion lost after reorder or reload");
        c.relations().remove(p.getGameProfile().getName());h.assertTrue(ModuleTargets.query(h.getLevel(),c.creatureId(),box,e->ModuleTargets.matches(e,2)).stream().anyMatch(e->e.getUUID().equals(p.getUUID())),"Removing exclusion did not restore acquisition");p.discard();h.succeed();}
    @GameTest(template="combat",batch="relations_faction")
    public static void FactionsBlockAcquisitionAndFriendlyDamageButAllowOptInIncidentalHits(GameTestHelper h){var a=core(h,0);var b=core(h,8);var player=TestActors.creative(h.getLevel());var data=FactionRegistry.get(h.getLevel().getServer());var id=data.create(player,"Test "+UUID.randomUUID().toString().substring(0,8));a.relations().faction(id);b.relations().faction(id);a.tryActivate(player);b.tryActivate(player);LoadedCoreIndex.observe(a);LoadedCoreIndex.observe(b);
        h.assertTrue(CoreRelations.allies(a,b)&&!b.acceptsLifeAttack(a.creatureId()),"Friendly fire default did not block damage");var box=LoadedCoreIndex.bounds(b).inflate(2);h.assertTrue(ModuleTargets.query(h.getLevel(),a.creatureId(),box).stream().noneMatch(e->e.getUUID().equals(b.creatureId())),"Ally auto-acquired");
        var other=TestActors.creative(h.getLevel());h.assertTrue(!data.toggle(other,id),"Non-owner changed faction friendly fire");h.assertTrue(data.toggle(player,id)&&b.acceptsLifeAttack(a.creatureId()),"Owner could not allow incidental hits");h.assertTrue(ModuleTargets.damageQuery(h.getLevel(),a.creatureId(),box,e->true).stream().anyMatch(e->e.getUUID().equals(b.creatureId())),"Opted-in friendly fire excluded hit volume");h.assertTrue(ModuleTargets.resolve(h.getLevel(),b.creatureId(),a.creatureId())==null,"Friendly fire enabled auto lock-on");b.relations().faction(UUID.randomUUID());h.assertTrue(!CoreRelations.allies(a,b),"Unknown foreign-world faction matched");h.succeed();}
    @GameTest(template="combat",batch="relations_permissions")
    public static void CoreRelationsUseServerPermissionsAndCopyByUuid(GameTestHelper h){var c=core(h,0);var p=TestActors.creative(h.getLevel());p.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));var menu=new HeartSettingsMenu(1,p.getInventory(),c);h.assertTrue(menu.action(p,"relation:create",0,"Public "+UUID.randomUUID().toString().substring(0,8)),"Create faction rejected");var id=c.relations().faction();c.relations().exclude(p.getGameProfile().getName());var copy=c.copyConfiguration();h.assertTrue(copy.getCompound("Relations").hasUUID("Faction"),"Portable faction missing");p.setGameMode(GameType.SURVIVAL);h.assertTrue(!menu.action(p,"relation:leave",0,"")&&id.equals(c.relations().faction()),"Survival altered life-core relations");h.succeed();}
    @GameTest(template="combat",batch="relations_faction_delete")
    public static void DeletingFactionRequiresOwnerAndClearsMembership(GameTestHelper h){
        var a=core(h,0);var b=core(h,8);var owner=TestActors.creative(h.getLevel());var other=TestActors.creative(h.getLevel());
        var registry=FactionRegistry.get(h.getLevel().getServer());var id=registry.create(owner,"Delete "+UUID.randomUUID().toString().substring(0,8));
        a.relations().faction(id);b.relations().faction(id);LoadedCoreIndex.observe(a);LoadedCoreIndex.observe(b);
        h.assertTrue(!registry.delete(other,id)&&registry.contains(id),"Unauthorized faction deletion accepted");
        h.assertTrue(registry.delete(owner,id)&&!registry.contains(id),"Owner could not delete faction");
        h.assertTrue(a.relations().faction()==null&&b.relations().faction()==null,"Loaded members retained deleted faction");
        a.relations().faction(id);b.relations().faction(id);
        h.assertTrue(!CoreRelations.allies(a,b)&&CoreRelations.damageAllowed(b,a.creatureId()),"Stale saved membership kept a deleted alliance");
        h.assertTrue(!registry.delete(owner,id),"Missing faction deletion succeeded twice");h.succeed();
    }
    private static net.minecraft.server.level.ServerPlayer factionPlayer(GameTestHelper h,String name){
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),name));
    }
    @GameTest(template="combat",batch="relations_private_invites")
    public static void PrivateInvitationsUseUuidAndDoNotGrantManagement(GameTestHelper h){
        var registry=new FactionRegistry();var owner=factionPlayer(h,"InviteOwner");var friend=factionPlayer(h,"InviteFriend");var outsider=factionPlayer(h,"InviteOutsider");
        registry.observe(friend);var id=registry.create(owner,"Private");h.assertTrue(registry.privacy(owner,id),"Creator cannot make private");
        h.assertTrue(!registry.canAssign(friend,id)&&!registry.joinPlayer(friend,id)&&!registry.accept(friend,id),"Private membership bypassed invitation");
        h.assertTrue(!registry.invite(outsider,id,"InviteFriend")&&!registry.invite(owner,id,"UnknownPlayer"),"Unauthorized or unknown invitation accepted");
        h.assertTrue(registry.invite(owner,id,"invitefriend")&&!registry.canAssign(friend,id),"Pending invitation grants assignment");
        var data=registry.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());registry=FactionRegistry.load(data,h.getLevel().registryAccess());
        var renamed=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(friend.getUUID(),"FriendRenamed"));
        h.assertTrue(!registry.accept(outsider,id)&&registry.accept(renamed,id)&&registry.canAssign(renamed,id),"Saved invitation failed UUID acceptance");
        h.assertTrue(!registry.privacy(renamed,id)&&!registry.invite(renamed,id,"InviteOutsider")&&!registry.toggle(renamed,id),"Membership granted creator powers");
        h.assertTrue(!registry.removeMember(renamed,id,owner.getUUID())&&registry.removeMember(owner,id,friend.getUUID())&&!registry.canAssign(friend,id),"Member removal did not revoke future assignments");
        h.assertTrue(registry.invite(owner,id,"InviteFriend")&&registry.decline(friend,id)&&!registry.accept(friend,id),"Declined invitation can be reused");h.succeed();
    }
    @GameTest(template="combat",batch="relations_alliances")
    public static void AlliancesRequireConfirmationAreNotTransitiveAndNeedBothForDamage(GameTestHelper h){
        var registry=new FactionRegistry();var pa=factionPlayer(h,"AllianceA");var pb=factionPlayer(h,"AllianceB");var pc=factionPlayer(h,"AllianceC");
        var a=registry.create(pa,"A");var b=registry.create(pb,"B");var c=registry.create(pc,"C");
        h.assertTrue(!registry.requestAlliance(pc,a,b)&&!registry.acceptAlliance(pb,b,a),"Unauthorized alliance accepted");
        h.assertTrue(registry.requestAlliance(pa,a,b)&&!registry.allied(a,b)&&registry.acceptAlliance(pb,b,a)&&registry.allied(a,b)&&registry.allied(b,a),"Bilateral confirmation broken");
        h.assertTrue(registry.requestAlliance(pb,b,c)&&registry.acceptAlliance(pc,c,b)&&!registry.allied(a,c),"Alliance propagated transitively");
        h.assertTrue(!registry.toggle(pb,a)&&!registry.damageAllowed(a,b)&&registry.alliedFire(pa,a)&&!registry.damageAllowed(a,b)&&registry.alliedFire(pb,b)&&registry.damageAllowed(a,b)&&registry.damageAllowed(b,a),"Allied damage requires both factions");
        var restored=FactionRegistry.load(registry.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.allied(a,b)&&restored.allied(b,c)&&!restored.allied(a,c)&&restored.damageAllowed(a,b),"Alliance save/load failed");
        h.assertTrue(restored.endAlliance(pb,b,a)&&!restored.allied(a,b)&&restored.allied(b,c),"Ending one alliance removed others");
        h.assertTrue(restored.delete(pb,b)&&!restored.allied(b,c)&&!restored.allied(FactionRegistry.WILDS,c)&&restored.allied(FactionRegistry.WILDS,FactionRegistry.WILDS),"Deleted faction or Wilds matched incorrectly");h.succeed();
    }
    @GameTest(template="combat",batch="relations_private_menu")
    public static void ServerMenusRejectPrivateAssignmentAndConfigurationSpoofs(GameTestHelper h){
        var c=core(h,0);var owner=TestActors.creative(h.getLevel());var other=TestActors.creative(h.getLevel());
        other.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter()));
        var registry=FactionRegistry.get(h.getLevel().getServer());var id=registry.create(owner,"Locked "+UUID.randomUUID().toString().substring(0,8));registry.privacy(owner,id);
        var menu=new HeartSettingsMenu(1,other.getInventory(),c);c.relations().ownerName(other.getGameProfile().getName());
        h.assertTrue(!menu.action(other,"relation:join",0,id.toString())&&c.relations().faction()==null,"Private core assignment bypassed membership via Owner name");
        c.relations().faction(id);var copy=c.copyConfiguration();c.relations().faction(null);
        h.assertTrue(!c.pasteConfiguration(other,copy)&&c.relations().faction()==null,"Config copier bypassed private faction membership");
        h.assertTrue(!menu.action(other,"relation:requestAlliance",0,id+"|"+id),"Self/unauthorized alliance accepted");
        h.assertTrue(menu.action(other,"relation:select",0,id.toString())&&!menu.snapshot().getCompound("FactionDetail").contains("Members"),"Private member data leaked");h.succeed();
    }
    @GameTest(template="combat",batch="relations_public_multi")
    public static void PublicPlayersMayJoinMultipleFactionsButCreatureHasOne(GameTestHelper h){
        var registry=new FactionRegistry();var owner=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PublicOwner")){
            @Override public boolean hasPermissions(int permission){return permission<=2;}
        };var friend=factionPlayer(h,"PublicFriend");
        var a=registry.create(owner,"Public A");var b=registry.create(owner,"Public B");
        h.assertTrue(registry.joinPlayer(friend,a)&&registry.joinPlayer(friend,b)&&registry.detail(friend,a).getBoolean("Member")&&registry.detail(friend,b).getBoolean("Member"),"Player multi-membership lost");
        registry.privacy(owner,a);registry.privacy(owner,b);h.assertTrue(registry.canAssign(friend,a)&&registry.canAssign(friend,b),"Privacy change ejected existing members");
        var c=core(h,0);c.relations().faction(a);c.relations().faction(b);h.assertTrue(b.equals(c.relations().faction()),"Creature has multiple factions");
        registry.removeMember(friend,a,friend.getUUID());h.assertTrue(!registry.canAssign(friend,a)&&registry.canAssign(friend,b),"Leaving one membership affected another");h.succeed();
    }
    @GameTest(template="combat",batch="relations_alliance_runtime")
    public static void DirectAlliesShareTargetAndDamagePolicyWithoutTransitiveProtection(GameTestHelper h){
        var a=core(h,0);var b=core(h,8);var c=core(h,16);var pa=TestActors.creative(h.getLevel());var pb=TestActors.creative(h.getLevel());var pc=TestActors.creative(h.getLevel());
        var registry=FactionRegistry.get(h.getLevel().getServer());String suffix=UUID.randomUUID().toString().substring(0,8);var fa=registry.create(pa,"RuntimeA "+suffix);var fb=registry.create(pb,"RuntimeB "+suffix);var fc=registry.create(pc,"RuntimeC "+suffix);
        a.relations().faction(fa);b.relations().faction(fb);c.relations().faction(fc);a.tryActivate(pa);b.tryActivate(pb);c.tryActivate(pc);LoadedCoreIndex.observe(a);LoadedCoreIndex.observe(b);LoadedCoreIndex.observe(c);
        registry.requestAlliance(pa,fa,fb);registry.acceptAlliance(pb,fb,fa);registry.requestAlliance(pb,fb,fc);registry.acceptAlliance(pc,fc,fb);
        h.assertTrue(CoreRelations.allies(a,b)&&!CoreRelations.allies(a,c)&&ModuleTargets.resolve(h.getLevel(),b.creatureId(),a.creatureId())==null&&ModuleTargets.resolve(h.getLevel(),c.creatureId(),a.creatureId())!=null,"Targeting did not use direct alliance relationships");
        h.assertTrue(!b.acceptsLifeAttack(a.creatureId())&&c.acceptsLifeAttack(a.creatureId()),"Alliance damage protections did not follow direct relationships");
        registry.alliedFire(pa,fa);h.assertTrue(!b.acceptsLifeAttack(a.creatureId()),"One-sided allied damage allowed");registry.alliedFire(pb,fb);
        h.assertTrue(b.acceptsLifeAttack(a.creatureId())&&ModuleTargets.resolve(h.getLevel(),b.creatureId(),a.creatureId())==null,"Allied damage enabled auto-targeting");
        registry.endAlliance(pb,fb,fa);h.assertTrue(!CoreRelations.allies(a,b)&&ModuleTargets.resolve(h.getLevel(),b.creatureId(),a.creatureId())!=null,"Ending alliance did not restore targets immediately");h.succeed();
    }
    @GameTest(template="combat",batch="relations_legacy")
    public static void LegacyFactionsStayPublicAndDanglingAlliancesAreDropped(GameTestHelper h){
        var owner=factionPlayer(h,"LegacyOwner");var stranger=factionPlayer(h,"LegacyStranger");var id=UUID.randomUUID();var tag=new net.minecraft.nbt.CompoundTag();var rows=new net.minecraft.nbt.ListTag();var row=new net.minecraft.nbt.CompoundTag();
        row.putUUID("Id",id);row.putUUID("Owner",owner.getUUID());row.putString("Name","Legacy");row.putBoolean("FriendlyFire",true);var ties=new net.minecraft.nbt.ListTag();ties.add(net.minecraft.nbt.NbtUtils.createUUID(UUID.randomUUID()));row.put("Allies",ties);rows.add(row);tag.put("Factions",rows);
        var data=FactionRegistry.load(tag,h.getLevel().registryAccess());h.assertTrue(data.canAssign(stranger,id)&&data.friendlyFire(id)&&data.detail(owner,id).getBoolean("Member")&&!data.detail(owner,id).getBoolean("Private"),"Legacy factions changed defaults");
        h.assertTrue(data.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess()).getList("Factions",10).getCompound(0).getList("Allies",11).isEmpty(),"Foreign alliance was restored");h.succeed();
    }
    private static VirtualModule death(HeartBlockEntity c){int index=c.wireless().add();var channel=c.wireless().channel(index);channel.set(0,1);channel.set(6,4);channel.frequency(false,new ItemStack(Items.DIAMOND));channel.frequency(true,new ItemStack(Items.EMERALD));var module=c.modules().add("combat_event");for(var old:module.signalRules().rows())module.signalRules().remove(old.id());var row=module.signalRules().add();row.edit("eventKind",3);row.edit("eventChannel",index+1);row.edit("eventPower",9);row.edit("eventPulse",7);return module;}
    @GameTest(template="combat",batch="relations_death")
    public static void LethalDamageOutputsOnceAndPulseSurvivesCoreRemoval(GameTestHelper h){var c=core(h,0);death(c);c.wireless().channel(0).frequency(false,new ItemStack(Items.NETHER_STAR));c.wireless().channel(0).frequency(true,new ItemStack(Items.AMETHYST_SHARD));c.tryActivate(TestActors.creative(h.getLevel()));
        final int[] received={0};var position=BlockPos.containing(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().getCenter())).east(2);
        var receiver=new com.simibubi.create.content.redstone.link.IRedstoneLinkable(){public int getTransmittedStrength(){return 0;}public void setReceivedStrength(int value){received[0]=value;}public boolean isListening(){return true;}public boolean isAlive(){return true;}public net.createmod.catnip.data.Couple<com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency> getNetworkKey(){return net.createmod.catnip.data.Couple.create(com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency.of(new ItemStack(Items.NETHER_STAR)),com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency.of(new ItemStack(Items.AMETHYST_SHARD)));}public BlockPos getLocation(){return position;}};
        com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(h.getLevel(),receiver);var signals=DeathSignals.get(h.getLevel().getServer());int before=signals.size();h.assertTrue(c.forceDeath()&&signals.size()==before+1,"Lethal hit lost death output");h.assertTrue(received[0]==9,"Actual Create receiver did not receive death strength");h.assertTrue(!c.forceDeath()&&signals.size()==before+1,"Repeated death duplicated output");var saved=signals.save(new net.minecraft.nbt.CompoundTag(),h.getLevel().registryAccess());h.assertTrue(saved.getList("Pulses",10).stream().anyMatch(v->((net.minecraft.nbt.CompoundTag)v).getInt("Remaining")==7),"Detached pulse not serialized");c.setRemoved();h.assertTrue(signals.size()==before+1&&received[0]==9,"Removing core canceled detached signal");h.runAfterDelay(9,()->{h.assertTrue(signals.size()==before&&received[0]==0,"Death pulse leaked after expiry");com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(h.getLevel(),receiver);h.succeed();});}
    @GameTest(template="combat",batch="relations_health_death")
    public static void HealthAdjustmentDeathAndDisabledRulesRespectLifecycle(GameTestHelper h){var c=core(h,0);var module=death(c);var row=module.signalRules().rows().getFirst();row.edit("enabled",0);c.tryActivate(TestActors.creative(h.getLevel()));h.assertTrue(DeathSignals.prepare(c).isEmpty(),"Disabled event armed");row.edit("enabled",1);h.assertTrue(DeathSignals.prepare(c).size()==1,"Death event not armed while alive");int before=DeathSignals.get(h.getLevel().getServer()).size();h.assertTrue(c.adjustOwnHealth(-1000000,true)&&DeathSignals.get(h.getLevel().getServer()).size()==before+1,"Health adjustment skipped death event");h.assertTrue(DeathSignals.prepare(c).isEmpty(),"Reloaded dying core can rearm event");h.succeed();}
    @GameTest(template="combat",batch="relations_internal_death")
    public static void DeathEventRejectsInternalOnlyOutputAndPreservesLegacyRules(GameTestHelper h){var c=core(h,0);var m=death(c);c.wireless().channel(0).set(6,3);c.tryActivate(TestActors.creative(h.getLevel()));h.assertTrue(DeathSignals.prepare(c).isEmpty(),"Death output silently armed unavailable internal consumer");var copy=VirtualModule.load(m.save());h.assertTrue(copy.signalRules().rows().getFirst().value("eventKind")==3,"Death event lost on save/load");h.succeed();}
    @GameTest(template="combat",batch="relations_pull_geometry")
    public static void DirectionalFrustumAndPreviewShareFiniteBounds(GameTestHelper h){var axis=new Vec3(1,-1,0).normalize();var shape=new PullGeometry(new Vec3(2,10,3),axis,10,1,5);h.assertTrue(shape.contains(shape.start().add(axis.scale(5))),"Centerline outside frustum");h.assertTrue(!shape.contains(shape.start().subtract(axis))&&!shape.contains(shape.start().add(axis.scale(11))),"Frustum includes points behind/start or beyond end");for(int i=0;i<16;i++)for(double t:new double[]{0,.5,1}){var p=shape.boundary(t,Math.PI*2*i/16);h.assertTrue(shape.bounds().inflate(.000001).contains(p)&&shape.contains(p),"Preview boundary disagrees with selection");}var m=new VirtualModule("pull");m.editParticle("add","minecraft:flame");var packets=PullVisual.particles(m,shape.start(),shape.start(),20,4,shape);h.assertTrue(packets.stream().allMatch(p->p.id().equals("minecraft:flame")&&shape.contains(p.from())&&p.to().equals(shape.start())),"Directional inflow lost selected particle style or geometry");h.succeed();}
    @GameTest(template="combat",batch="relations_pull_angles")
    public static void PullAnglesDoNotOrbitStartDistanceAndDefaultsStaySpherical(GameTestHelper h){var c=core(h,0);var m=c.modules().add("pull");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));m.edit("startDistance",2,"");var start=ModulePull.destination(c,m);m.edit("shotHorizontal",45,"");m.edit("shotVertical",-45,"");h.assertTrue(ModulePull.destination(c,m).distanceTo(start)<1e-8,"Angles moved pull origin");var original=ModuleCombat.baseOrigin(c,m);h.assertTrue(Math.abs(start.distanceTo(original)-2.55)<1e-6,"Start distance applied twice or omitted marked surface offset: "+start.distanceTo(original));h.assertTrue(m.value("pullShape")==0&&m.value("pullMotion")==0,"Old pull changed behavior by default");var fields=ModuleFields.forType("pull");h.assertTrue(fields.stream().map(ModuleFields.Field::key).distinct().count()==fields.size(),"Duplicate pull settings");h.succeed();}

    @GameTest(template="combat",batch="relations_suction")
    public static void StableSuctionConvergesUpwardWithGravityAndVelocityCap(GameTestHelper h){var destination=new Vec3(0,10,0);var point=new Vec3(8,2,0);var velocity=new Vec3(0,0,.4);double initial=point.distanceTo(destination);for(int i=0;i<90;i++){velocity=PullSuction.velocity(velocity,destination.subtract(point),.12,.5,.8,.08);velocity=velocity.subtract(0,.08,0);h.assertTrue(velocity.length()<=.500001,"Stable suction exceeded configured speed");point=point.add(velocity);velocity=velocity.scale(.98);}h.assertTrue(point.distanceTo(destination)<1&&point.y>9&&point.distanceTo(destination)<initial,"45-degree upward suction lost target to gravity or drift: "+point);h.succeed();}
    @GameTest(template="combat",batch="relations_suction_runtime",timeoutTicks=30)
    public static void RuntimeSuctionSetsVelocityAndStopsWithoutChangingGravityFlag(GameTestHelper h){var c=core(h,0);c.tryActivate(TestActors.creative(h.getLevel()));var point=h.absoluteVec(new Vec3(5,20,5));var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(point.add(4,0,0));h.getLevel().addFreshEntity(cow);var m=new VirtualModule("pull");m.edit("target",1,"");m.edit("pullMotion",1,"");m.edit("pullDuration",15,"");m.edit("pullWarning",5,"");m.edit("lineOfSight",1,"");m.emitter(new VirtualModule.Emitter(point,new Vec3(1,0,0),new org.joml.Quaterniond(),UUID.randomUUID()));var runtime=new ModulePull();runtime.tickEmitter(c,m);
        h.runAfterDelay(6,()->{runtime.tickEmitter(c,m);h.assertTrue(runtime.victims(m.id())>0&&cow.getDeltaMovement().x<0,"Actual runtime did not pull captured mob");h.assertTrue(cow.isNoGravity(),"Suction mutated gravity flag");runtime.clear();cow.setNoGravity(false);h.assertTrue(!cow.isNoGravity(),"Cleared suction retained gravity override");cow.discard();h.succeed();});}
}
