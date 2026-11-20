package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_contact_tests") @PrefixGameTestTemplate(false)
public final class ContactSensorGameTests {
    public static HeartBlockEntity core(GameTestHelper h, Block block, int x) {
        var level=h.getLevel(); var p=h.absolutePos(new BlockPos(x,40,3));
        level.setBlockAndUpdate(p,block.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
        for(int i=0;i<2;i++){int index=core.wireless().add();core.wireless().channel(index).set(6,3);core.wireless().channel(index).set(0,1);}
        return core;
    }
    public static VirtualModule sensor(HeartBlockEntity core) {
        var m=core.modules().add("contact_sensor");
        if(m==null)throw new AssertionError("Contact Sensor missing from catalog");
        m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.SOUTH)));
        m.edit("startDistance",3,""); m.edit("sensorChannel",1,""); m.edit("contactSizeX",2,"");m.edit("contactSizeY",2,"");m.edit("contactSizeZ",2,"");
        return m;
    }
    public static void activate(GameTestHelper h, HeartBlockEntity c) {
        var p=TestActors.creative(h.getLevel());
        if(c.isSurvivalCore())c.depositSurvivalTokens(p,new ItemStack(Items.COPPER_INGOT,10));
        if(!c.isControlCore())c.tryActivate(p);
        h.assertTrue(c.operational(),"Core did not become operational");
    }
    private static boolean query(GameTestHelper h,HeartBlockEntity c,VirtualModule m) {var q=ContactSensorQuery.query(h.getLevel(),c,m);h.assertTrue(q.complete(),"Unexpected query limit");return q.touching()||!q.projectiles().isEmpty();}
    private static void categories(VirtualModule m,String key){m.edit("contactDetectTerrain",0,"");m.edit(key,1,"");}
    private static net.minecraft.world.entity.animal.Cow cow(GameTestHelper h,Vec3 point){var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);cow.setPos(point.add(0,-cow.getBbHeight()/2,0));h.getLevel().addFreshEntity(cow);return cow;}
    private static Arrow arrow(GameTestHelper h,Vec3 point){var a=new Arrow(h.getLevel(),point.x,point.y,point.z,new ItemStack(Items.ARROW),null);a.setNoGravity(true);a.setDeltaMovement(Vec3.ZERO);h.getLevel().addFreshEntity(a);a.xo=point.x;a.yo=point.y;a.zo=point.z;return a;}

    @GameTest(template="combat",batch="contact_terrain") public static void TerrainUsesCollisionShapesAndIndependentCheckboxes(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);activate(h,c);var p=BlockPos.containing(ContactSensorGeometry.frame(c,m).center());
        h.assertTrue(!query(h,c,m),"Empty air touched");h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());h.assertTrue(query(h,c,m),"Terrain missed");
        m.edit("contactDetectTerrain",0,"");h.assertTrue(!query(h,c,m),"Unchecked terrain detected");m.edit("contactDetectTerrain",1,"");h.getLevel().setBlockAndUpdate(p,Blocks.WATER.defaultBlockState());h.assertTrue(!query(h,c,m),"Fluid became solid contact");h.succeed();
    }
    @GameTest(template="combat",batch="contact_shapes") public static void RotatedBoxDoesNotUseItsEnclosingAabbAsContact(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectMobs");m.edit("contactSizeX",.1,"");m.edit("contactSizeZ",6,"");m.edit("contactHorizontal",45,"");activate(h,c);
        var f=ContactSensorGeometry.frame(c,m);var victim=cow(h,f.center().add(2,0,2));h.assertTrue(!query(h,c,m),"Enclosing-box corner falsely detected");victim.setPos(f.center().add(-1,-victim.getBbHeight()/2,1));h.assertTrue(query(h,c,m),"Rotated long axis missed");victim.discard();h.succeed();
    }
    @GameTest(template="combat",batch="contact_entities") public static void MobHitboxEdgeCountsAndProxyHitboxesAreExcluded(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectMobs");m.edit("contactSizeX",1,"");activate(h,c);var f=ContactSensorGeometry.frame(c,m);
        var victim=cow(h,f.center().add(.8,0,0));h.assertTrue(query(h,c,m),"Touching edge ignored because target center is outside");victim.discard();
        var proxy=SableLife.ENTITY_HITBOX.get().create(h.getLevel());proxy.setPos(f.center());h.getLevel().addFreshEntity(proxy);h.assertTrue(!query(h,c,m),"Proxy counted as mob");proxy.discard();h.succeed();
    }
    @GameTest(template="combat",batch="contact_players") public static void PlayerContactsRespectGameModeAndNameWhitelist(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectPlayers");activate(h,c);
        var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ContactActor"));player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(ContactSensorGeometry.frame(c,m).center());h.getLevel().addNewPlayer(player);
        try{h.assertTrue(query(h,c,m),"Survival player contact missed");player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);h.assertTrue(!query(h,c,m),"Creative player detected");player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);h.assertTrue(!query(h,c,m),"Spectator detected");player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);c.relations().exclude("ContactActor");h.assertTrue(!query(h,c,m),"Whitelisted player detected");m.edit("contactTargetsOnly",0,"");h.assertTrue(query(h,c,m),"Unfiltered player contact missed");}finally{player.discard();}h.succeed();
    }
    @GameTest(template="combat",batch="contact_filters") public static void EligibleTargetFilterHonorsMobWhitelist(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectMobs");activate(h,c);var victim=cow(h,ContactSensorGeometry.frame(c,m).center());
        c.relations().excludeMob("minecraft:cow");h.assertTrue(!query(h,c,m),"Whitelisted mob detected");m.edit("contactTargetsOnly",0,"");h.assertTrue(query(h,c,m),"Physical contact option still applied whitelist");victim.discard();h.succeed();
    }
    @GameTest(template="combat",batch="contact_structures") public static void OrdinarySableBlocksCountOnlyWhenSelectedAndOwnBodyIsIgnored(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectStructures");activate(h,c);m.edit("startDistance",0,"");h.assertTrue(!query(h,c,m),"Own body triggered sensor");
        m.edit("startDistance",3,"");var p=BlockPos.containing(ContactSensorGeometry.frame(c,m).center());h.getLevel().setBlockAndUpdate(p,Blocks.IRON_BLOCK.defaultBlockState());
        var other=SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));
        h.runAfterDelay(3,()->{h.assertTrue(query(h,c,m),"Non-living Sable structure missed");m.edit("contactDetectStructures",0,"");m.edit("contactDetectTerrain",1,"");h.assertTrue(!query(h,c,m),"Sable plot treated as terrain");h.succeed();});
    }
    @GameTest(template="combat",batch="contact_projectiles") public static void ProjectileCheckboxDetectsArrowAndAlwaysExcludesOwnTag(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectProjectiles");activate(h,c);var a=arrow(h,ContactSensorGeometry.frame(c,m).center());
        h.assertTrue(query(h,c,m),"Arrow missed");a.getPersistentData().putUUID("SableLifeAttackOwner",c.creatureId());m.edit("contactTargetsOnly",0,"");h.assertTrue(!query(h,c,m),"Own arrow triggered with filters off");a.discard();h.succeed();
    }
    @GameTest(template="combat",batch="contact_swept_arrow") public static void OrdinaryArrowCrossingThinBoxBetweenSamplesIsDetected(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectProjectiles");m.edit("contactSizeZ",.05,"");activate(h,c);var center=ContactSensorGeometry.frame(c,m).center();
        var a=arrow(h,center.add(0,0,1.5));a.xo=center.x;a.yo=center.y;a.zo=center.z-1.5;h.assertTrue(query(h,c,m),"3-block arrow crossing missed");a.discard();h.succeed();
    }
    @GameTest(template="combat",batch="contact_hold",timeoutTicks=30) public static void HoldClearsWhenContactLeaves(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectMobs");activate(h,c);var mob=cow(h,ContactSensorGeometry.frame(c,m).center());c.modules().contactSensor.tick(c);
        h.assertTrue(c.wireless().output(0)==15,"Hold did not output");mob.discard();h.runAfterDelay(2,()->{h.assertTrue(c.wireless().output(0)==0,"Hold latched after exit");h.succeed();});
    }
    @GameTest(template="combat",batch="contact_pulse",timeoutTicks=30) public static void PulseHasConfiguredDurationAndPersistentContactDoesNotExtendIt(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectMobs");m.edit("contactOutputMode",1,"");m.edit("contactPulse",4,"");activate(h,c);var mob=cow(h,ContactSensorGeometry.frame(c,m).center());c.modules().contactSensor.tick(c);
        h.assertTrue(c.wireless().output(0)==15,"Pulse did not begin");h.runAfterDelay(2,()->h.assertTrue(c.wireless().output(0)==15,"Pulse ended early"));h.runAfterDelay(6,()->{h.assertTrue(c.wireless().output(0)==0,"Contact extended pulse");mob.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="contact_disable",timeoutTicks=30) public static void DisableCancelsPulseImmediatelyAndDoesNotClearOtherSignalSources(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectMobs");m.edit("contactOutputMode",1,"");m.edit("contactPulse",200,"");activate(h,c);var mob=cow(h,ContactSensorGeometry.frame(c,m).center());c.modules().contactSensor.tick(c);m.edit("enabled",0,"");
        c.wireless().programOutputs("contact_test_other",Map.of(1,7));h.runAfterDelay(2,()->{h.assertTrue(c.wireless().output(0)==0&&c.wireless().output(1)==7,"Disable left output or cleared another source");mob.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="contact_new_projectile",timeoutTicks=35) public static void NewProjectileCanPulseWhileAnotherRemainsInside(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectProjectiles");m.edit("contactOutputMode",1,"");m.edit("contactPulse",2,"");m.edit("contactCooldown",4,"");activate(h,c);var a=arrow(h,ContactSensorGeometry.frame(c,m).center());c.modules().contactSensor.tick(c);
        h.runAfterDelay(4,()->{h.assertTrue(c.wireless().output(0)==0,"Same projectile repeated");arrow(h,ContactSensorGeometry.frame(c,m).center().add(.1,0,0));});h.runAfterDelay(5,()->{h.assertTrue(c.wireless().output(0)==15,"Second projectile failed to pulse");a.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="contact_moveset",timeoutTicks=35) public static void MoveSetCanOpenAndCloseSensorWindow(GameTestHelper h){
        var c=core(h,SableLife.REDSTONE_CORE.get(),3);var m=sensor(c);categories(m,"contactDetectMobs");m.edit("contactOutputMode",1,"");m.edit("contactPulse",100,"");var table=c.modules().add("move_set");table.moveSet().edit("add",0,"",c.modules());table.moveSet().edit("action_add",0,"0|"+m.id(),c.modules());table.moveSet().edit("action_duration",.2,"0|0",c.modules());table.moveSet().edit("dispatchRest",600,"0",c.modules());activate(h,c);var mob=cow(h,ContactSensorGeometry.frame(c,m).center());c.modules().tick(c);
        h.assertTrue(c.wireless().output(0)==15,"Move Set did not activate sensor");h.runAfterDelay(7,()->{h.assertTrue(c.wireless().output(0)==0,"Action end left long pulse active");mob.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="contact_cores") public static void AllThreeCoreTypesSupportContactSensor(GameTestHelper h){
        int x=3;for(var block:List.of(SableLife.HEART.get(),SableLife.REDSTONE_CORE.get(),SableLife.SURVIVAL_LIFE_CORE.get())){var c=core(h,block,x);x+=5;var m=sensor(c);categories(m,"contactDetectMobs");activate(h,c);var mob=cow(h,ContactSensorGeometry.frame(c,m).center());c.modules().tick(c);h.assertTrue(c.wireless().output(0)==15,"Core type did not output: "+block);mob.discard();}h.succeed();
    }
    @GameTest(template="combat",batch="contact_save") public static void SaveCopyPresetAndChannelDeletionPreserveMeaning(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);m.edit("contactPulse",37,"");m.edit("contactDetectProjectiles",1,"");m.edit("sensorChannel",2,"");
        var restored=VirtualModule.load(m.save());h.assertTrue(restored.value("contactPulse")==37&&restored.anchors().equals(m.anchors())&&restored.value("contactDetectProjectiles")==1,"Save lost authoring");var copy=c.modules().copy(m.id());copy.edit("contactSizeX",5,"");h.assertTrue(m.value("contactSizeX")==2,"Copy shared settings");var preset=ModulePreset.create(m.save());h.assertTrue(preset.anchors().isEmpty()&&preset.value("sensorChannel")==0&&preset.value("contactPulse")==37,"Preset retained external binding or lost duration");c.wireless().remove(0);h.assertTrue(m.value("sensorChannel")==1,"Earlier channel removal misdirected binding");c.wireless().remove(0);h.assertTrue(m.value("sensorChannel")==0,"Deleted output channel rebound");h.succeed();
    }
    @GameTest(template="combat",batch="contact_codec") public static void ActualActionCodecAcceptsEveryNewFieldAndRejectsOversizedKeys(GameTestHelper h){
        for(var f:ModuleFields.forType("contact_sensor")){var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{var action=new ModuleNetworking.Action(1,0,UUID.randomUUID(),"edit",f.key(),f.max(),"");ModuleNetworking.Action.CODEC.encode(buffer,action);var result=ModuleNetworking.Action.CODEC.decode(buffer);h.assertTrue(result.equals(action),"Codec field mismatch");}finally{buffer.release();}}
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());boolean rejected=false;try{ModuleNetworking.Action.CODEC.encode(b,new ModuleNetworking.Action(1,0,UUID.randomUUID(),"set","x".repeat(65),0,""));}catch(RuntimeException expected){rejected=true;}finally{b.release();}h.assertTrue(rejected,"Oversized key accepted");h.succeed();
    }
    @GameTest(template="combat",batch="contact_boundaries") public static void NumericLimitsAndMissingNewSettingsHaveSafeDefaults(GameTestHelper h){
        var m=new VirtualModule("contact_sensor");h.assertTrue(m.value("contactDetectTerrain")==1&&m.value("contactDetectProjectiles")==0&&m.value("contactPulse")==4,"Unsafe defaults");h.assertTrue(!m.edit("contactPulse",Double.NaN,""),"NaN accepted");m.edit("contactSizeX",10000,"");m.edit("contactPulse",0,"");h.assertTrue(m.value("contactSizeX")==16&&m.value("contactPulse")==1,"Numeric bounds failed");var old=m.save();old.getCompound("Settings").remove("contactPulse");var loaded=VirtualModule.load(old);h.assertTrue(loaded.value("contactPulse")==4,"Missing setting lost default");h.succeed();
    }
    @GameTest(template="combat",batch="contact_blueprint") public static void ActualBlueprintPlacementRemapsContactMarkAndKeepsSettings(GameTestHelper h) throws Exception {
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);m.edit("contactPulse",37,"");m.edit("contactDetectProjectiles",1,"");
        var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),c.getBlockPos());
        var blueprint=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(h.getLevel(),Vec3.ZERO,List.of(body.getUniqueId()));
        String name="contact "+UUID.randomUUID();var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(h.getLevel().getServer(),name);java.nio.file.Files.createDirectories(path.getParent());net.minecraft.nbt.NbtIo.writeCompressed(blueprint.save(),path);
        var at=h.absoluteVec(new Vec3(15.5,8,15.5));
        try {
            var server=h.getLevel().getServer();h.getLevel().getChunk(BlockPos.containing(at));
            int result=server.getCommands().getDispatcher().execute("sablelife blueprint spawn \""+name+"\" false "+at.x+" "+at.y+" "+at.z,server.createCommandSourceStack().withLevel(h.getLevel()));
            h.assertTrue(result==1,"Contact blueprint command rejected");dev.sablelife.ownership.CommandBlueprintSpawns.get(server).advance(server);
            var child=dev.sablelife.ownership.LoadedCoreIndex.all(h.getLevel()).stream().filter(v->v!=c&&ModuleArena.corePosition(v).distanceToSqr(at)<16).findFirst().orElseThrow();
            var copy=child.modules().all().stream().filter(v->v.type().equals("contact_sensor")).findFirst().orElseThrow();
            h.assertTrue(copy.value("contactPulse")==37&&copy.value("contactDetectProjectiles")==1&&copy.value("sensorChannel")==1,"Blueprint lost contact settings");
            h.assertTrue(copy.anchors().size()==1&&copy.anchors().getFirst().status(child).equals("valid")&&!copy.anchors().getFirst().bodyId().equals(body.getUniqueId()),"Blueprint kept source-body mark");
        } finally {java.nio.file.Files.deleteIfExists(path);}
        h.succeed();
    }
    @GameTest(template="combat",batch="contact_crowd") public static void DenseProjectileCloudIsBoundedAndDoesNotForgePulse(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectProjectiles");m.edit("contactOutputMode",1,"");activate(h,c);
        var arrows=new ArrayList<Arrow>();for(int i=0;i<270;i++)arrows.add(arrow(h,ContactSensorGeometry.frame(c,m).center()));
        c.modules().contactSensor.tick(c);h.assertTrue(c.modules().contactSensor.snapshot(m.id()).getBoolean("Limited")&&c.wireless().output(0)==0,"Over-limit query produced an unreliable pulse");arrows.forEach(Arrow::discard);h.succeed();
    }
    @GameTest(template="combat",batch="contact_gap") public static void AirBetweenOtherStructureBlocksDoesNotCountAsContact(GameTestHelper h){
        var c=core(h,SableLife.HEART.get(),3);var m=sensor(c);categories(m,"contactDetectStructures");for(String key:List.of("contactSizeX","contactSizeY","contactSizeZ"))m.edit(key,.2,"");activate(h,c);var center=ContactSensorGeometry.frame(c,m).center();var p=BlockPos.containing(center);var a=p.west(2);var b=p.east(2);
        h.getLevel().setBlockAndUpdate(a,Blocks.IRON_BLOCK.defaultBlockState());h.getLevel().setBlockAndUpdate(b,Blocks.IRON_BLOCK.defaultBlockState());SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),a,List.of(a,b),new BoundingBox3i(a.getX(),a.getY(),a.getZ(),b.getX(),b.getY(),b.getZ()));
        h.runAfterDelay(3,()->{h.assertTrue(!query(h,c,m),"Air gap in enclosing bounds triggered contact");h.succeed();});
    }
    @GameTest(template="combat",batch="contact_connected",timeoutTicks=50) public static void NativeSwivelChildInsideTheBoxIsExcluded(GameTestHelper h) throws Exception {
        var level=h.getLevel();var origin=h.absolutePos(new BlockPos(4,40,4));var joint=origin.east();var tip=joint.above();
        var bearing=net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("simulated","swivel_bearing"));
        h.assertTrue(bearing!=Blocks.AIR,"Native swivel fixture dependency missing");
        level.setBlockAndUpdate(origin,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(joint,bearing.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.UP));level.setBlockAndUpdate(tip,Blocks.IRON_BLOCK.defaultBlockState());
        var root=SubLevelAssemblyHelper.assembleBlocks(level,origin,List.of(origin,joint,tip),new BoundingBox3i(origin.getX(),origin.getY(),origin.getZ(),tip.getX(),tip.getY(),tip.getZ()));
        var c=(HeartBlockEntity)level.getBlockEntity(root.getPlot().getCenterBlock());var controller=level.getBlockEntity(c.getBlockPos().east());controller.getClass().getMethod("assemble").invoke(controller);
        h.assertTrue(dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(root).size()>1,"Swivel did not create a logical child structure");
        int ch=c.wireless().add();c.wireless().channel(ch).set(6,3);c.wireless().channel(ch).set(0,1);var m=sensor(c);categories(m,"contactDetectStructures");m.edit("startDistance",0,"");for(String key:List.of("contactSizeX","contactSizeY","contactSizeZ"))m.edit(key,8,"");activate(h,c);
        h.assertTrue(!query(h,c,m),"Own native child triggered contact");
        var p=BlockPos.containing(ContactSensorGeometry.frame(c,m).center().add(0,0,2.5));level.setBlockAndUpdate(p,Blocks.GOLD_BLOCK.defaultBlockState());SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));
        h.runAfterDelay(3,()->{h.assertTrue(query(h,c,m),"Ignoring own child also hid unrelated structure");h.succeed();});
    }
}
