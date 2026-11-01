package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;

@GameTestHolder("sablelife_control_tests")
@PrefixGameTestTemplate(false)
public final class DispatchRedstoneGameTests {
    @GameTest(template="combat",batch="flame_selection_save")
    public static void FlameParticleSelectionMigratesLegacySoulSizeAndKeepsEmptyChoice(GameTestHelper h){
        var m=new VirtualModule("flame");h.assertTrue(m.particles().equals(List.of("minecraft:flame")),"New flame default changed");
        var old=m.save();old.remove("FlameParticleSelectionVersion");old.getCompound("Settings").putDouble("flameVisual",1);old.getCompound("Settings").putDouble("flameParticleSize",2.5);
        var migrated=VirtualModule.load(old);h.assertTrue(migrated.particles().equals(List.of("minecraft:soul_fire_flame"))&&migrated.particleEntry("minecraft:soul_fire_flame").size()==2.5F,"Legacy blue fire/size lost");
        h.assertTrue(m.editParticle("add","minecraft:smoke")&&m.editParticle("size","minecraft:smoke",1.7)&&m.editParticle("duration","minecraft:smoke",.5),"Flame particle editor rejected supported choice/properties");
        h.assertTrue(m.editEffect("add","minecraft:slowness",0)&&m.editEffect("duration","minecraft:slowness",2.5)&&m.editEffect("level","minecraft:slowness",2),"Flame effect selection rejected");
        var saved=VirtualModule.load(m.save());h.assertTrue(saved.particleEntries().equals(m.particleEntries())&&saved.effects().equals(m.effects()),"Selection failed to reload");
        for(var id:m.particles())m.editParticle("remove",id);h.assertTrue(VirtualModule.load(m.save()).particles().isEmpty(),"Explicit empty visual choice reverted to fire");
        h.assertTrue(ModuleFields.find("flame","flameVisual")==null&&ModuleFields.find("flame","particles")!=null&&ModuleFields.find("flame","effects")!=null&&ModuleFields.find("flame","burn")!=null,"Flame controls did not switch to selections or lost Burn");
        var jet=new ModuleNetworking.FlameJet(net.minecraft.world.phys.Vec3.ZERO,new net.minecraft.world.phys.Vec3(0,0,1),.8,6,.75,0,1,42,saved.particleEntries());
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{ModuleNetworking.FlameJet.CODEC.encode(buffer,jet);h.assertTrue(ModuleNetworking.FlameJet.CODEC.decode(buffer).equals(jet),"Mixed particles/lifetime/size lost in jet packet");}finally{buffer.release();}h.succeed();
    }
    @GameTest(template="combat",batch="flame_effect_hit",timeoutTicks=25)
    public static void FlameAppliesSelectedEffectWithoutBurnAndVisualsDoNotIncreaseDamage(GameTestHelper h){
        var heart=fixture(h);var m=heart.modules().add("flame");m.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.SOUTH)));m.edit("trigger",0,"");m.edit("flameSpeed",4,"");m.edit("damage",2,"");m.edit("burn",0,"");m.edit("interval",20,"");m.editEffect("add","minecraft:slowness",0);m.editEffect("duration","minecraft:slowness",2.5);m.editEffect("level","minecraft:slowness",2);m.editParticle("add","minecraft:smoke");
        var target=EntityType.COW.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);target.setHealth(20);target.setPos(ModuleCombat.nozzle(heart,m).add(0,-.7,2));h.getLevel().addFreshEntity(target);heart.tryActivate(TestActors.creative(h.getLevel()));
        h.runAfterDelay(6,()->{var effect=target.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);h.assertTrue(target.getHealth()==18&&!target.isOnFire()&&effect!=null&&effect.getAmplifier()==1&&effect.getDuration()>35,"Flame failed selected effect, disabled Burn, or damage interval");});
        h.runAfterDelay(12,()->{h.assertTrue(target.getHealth()==18,"Multiple particle choices duplicated damage");target.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="redblue_module",timeoutTicks=40)
    public static void DirectRedBlueSamplesTransmitWithoutChannelsAndCleanUp(GameTestHelper h) {
        var heart=fixture(h);var level=h.getLevel();var registries=level.registryAccess();
        var a=output(heart);a.edit("trigger",0,"");a.edit("redstonePower",7,"");a.edit("redstoneFaces",1,"");
        var player=FakePlayerFactory.getMinecraft(level);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter()));
        var menu=new ModuleFrequencyMenu(1,player.getInventory(),heart,a);
        menu.setCarried(new ItemStack(Items.DIAMOND,23));menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
        menu.setCarried(new ItemStack(Items.EMERALD,17));menu.clicked(1,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
        h.assertTrue(menu.getCarried().getCount()==17&&a.redBlue(false,registries).getCount()==1&&a.redBlue(true,registries).is(Items.EMERALD),"RedBlue slots consumed items or stored wrong samples");
        var restored=VirtualModule.load(a.save());h.assertTrue(restored.redBlue(false,registries).is(Items.DIAMOND)&&restored.redBlue(true,registries).is(Items.EMERALD),"RedBlue frequencies failed save/load");
        var b=heart.modules().add("wireless_redstone");b.edit("trigger",0,"");b.redBlue(false,new ItemStack(Items.DIAMOND),registries);b.redBlue(true,new ItemStack(Items.EMERALD),registries);b.edit("redstonePower",12,"");
        var p=h.absolutePos(new BlockPos(4,6,7));level.setBlockAndUpdate(p.below(),Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(p,com.simibubi.create.AllBlocks.REDSTONE_LINK.getDefaultState().setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.RECEIVER,true).setValue(com.simibubi.create.content.redstone.link.RedstoneLinkBlock.FACING,Direction.UP));
        var receiver=(com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity)level.getBlockEntity(p);receiver.loadWithComponents(new net.minecraft.nbt.CompoundTag(),registries);receiver.tick();
        var link=receiver.getBehaviour(com.simibubi.create.content.redstone.link.LinkBehaviour.TYPE);link.setFrequency(true,new ItemStack(Items.DIAMOND));link.setFrequency(false,new ItemStack(Items.EMERALD));
        heart.tryActivate(player);heart.modules().tick(heart);
        h.assertTrue(heart.wireless().size()==0&&HeartModules.status(b,heart).equals("valid"),"External RedBlue required a channel or Mark");
        h.runAfterDelay(5,()->{
            h.assertTrue(receiver.getReceivedSignal()==12,"External receiver failed to match module item frequency");
            var anchor=a.anchors().getFirst();h.assertTrue(level.getSignal(anchor.position(anchor.body(heart)).east(),Direction.EAST)==7,"RedBlue suppressed simultaneous Mark output");
            b.edit("enabled",0,"");heart.modules().tick(heart);h.assertTrue(receiver.getReceivedSignal()==7,"Disabled module erased another module or retained output");
            heart.wireless().master(false);h.assertTrue(receiver.getReceivedSignal()==0,"Master off retained external signal");
            heart.wireless().master(true);heart.modules().tick(heart);h.assertTrue(receiver.getReceivedSignal()==7,"External signal failed to resume");
            heart.damage(heart.health().maximum());h.assertTrue(receiver.getReceivedSignal()==0,"Death retained external signal");h.succeed();
        });
    }
    @GameTest(template="combat",batch="hurt_modes",timeoutTicks=40)
    public static void HurtModesCooldownRefreshLatchAndLifecycleCleanup(GameTestHelper h) {
        var heart=fixture(h);var player=FakePlayerFactory.getMinecraft(h.getLevel());
        var once=output(heart);once.edit("trigger",5,"");once.edit("hurtCooldown",10,"");
        var timed=output(heart);timed.edit("trigger",5,"");timed.edit("hurtMode",1,"");timed.edit("hurtDuration",8,"");timed.edit("hurtCooldown",5,"");
        var stay=output(heart);stay.edit("trigger",5,"");stay.edit("hurtMode",2,"");
        var saved=VirtualModule.load(timed.save());h.assertTrue(saved.value("trigger")==5&&saved.value("hurtDuration")==8&&saved.value("hurtCooldown")==5,"Hurt authoring failed to reload");
        heart.tryActivate(player);heart.modules().tick(heart);
        h.assertTrue(!HeartModules.triggered(once,heart)&&!HeartModules.triggered(timed,heart),"Hurt trigger powered before damage");
        h.assertTrue(!heart.damage(0),"Zero damage was accepted");heart.modules().tick(heart);h.assertTrue(!HeartModules.triggered(stay,heart),"Rejected damage activated hurt trigger");
        heart.damage(1);heart.modules().tick(heart);
        h.assertTrue(HeartModules.triggered(once,heart)&&HeartModules.triggered(timed,heart)&&HeartModules.triggered(stay,heart),"Real damage did not activate independent modules");
        h.runAfterDelay(2,()->{heart.modules().tick(heart);heart.damage(1);heart.modules().tick(heart);h.assertTrue(!HeartModules.triggered(once,heart),"Hurt cooldown was bypassed");});
        h.runAfterDelay(6,()->{heart.damage(1);heart.modules().tick(heart);h.assertTrue(HeartModules.triggered(timed,heart),"Timed trigger did not refresh after cooldown");});
        h.runAfterDelay(10,()->{heart.modules().tick(heart);h.assertTrue(HeartModules.triggered(timed,heart)&&HeartModules.triggered(stay,heart),"Timed refresh or latch expired early");heart.modules().hurtTriggers.reset(stay.id());h.assertTrue(!HeartModules.triggered(stay,heart)&&HeartModules.triggered(timed,heart),"Reset affected another module");});
        h.runAfterDelay(16,()->{heart.modules().tick(heart);h.assertTrue(!HeartModules.triggered(timed,heart),"Timed trigger did not expire");heart.damage(1);heart.modules().tick(heart);h.assertTrue(HeartModules.triggered(stay,heart),"Stay trigger could not reactivate");heart.wireless().master(false);heart.wireless().master(true);heart.modules().tick(heart);h.assertTrue(!HeartModules.triggered(stay,heart),"Master off retained latch");heart.damage(heart.health().maximum());h.assertTrue(!heart.modules().hurtTriggers.powered(stay.id()),"Fatal hit retained hurt activation");h.succeed();});
    }
    @GameTest(template="combat",batch="hurt_moveset",timeoutTicks=45)
    public static void HurtMoveSetFinishesDelayedActionExactlyOnceWithoutWirelessChannels(GameTestHelper h) {
        var heart=fixture(h);var target=EntityType.PIG.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter()).add(0,0,4));h.getLevel().addFreshEntity(target);
        var out=output(heart);var table=heart.modules().add("move_set");table.edit("trigger",5,"");table.edit("hurtCooldown",20,"");
        table.moveSet().edit("add",0,"",heart.modules());table.moveSet().edit("action_add",0,"0|"+out.id(),heart.modules());table.moveSet().edit("action_start",.2,"0|0",heart.modules());table.moveSet().edit("action_duration",.3,"0|0",heart.modules());table.moveSet().get(0).config().edit("dispatchBusy",1,"");
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.damage(1);heart.modules().tick(heart);
        h.assertTrue(heart.wireless().size()==0&&!HeartModules.triggered(out,heart),"Delayed action fired early or required frequency items");
        h.runAfterDelay(5,()->{heart.damage(1);heart.modules().tick(heart);h.assertTrue(HeartModules.triggered(out,heart),"Hurt Once failed to finish delayed timeline or restarted on repeat hurt");});
        h.runAfterDelay(12,()->{heart.modules().tick(heart);h.assertTrue(!HeartModules.triggered(out,heart)&&!HeartModules.triggered(table,heart),"Once move kept selecting moves");});
        h.runAfterDelay(25,()->{heart.damage(1);heart.modules().tick(heart);h.assertTrue(HeartModules.triggered(table,heart),"Move set did not accept next damage after cooldown");heart.modules().clearRuntime();h.assertTrue(!HeartModules.triggered(table,heart),"Runtime clear retained hurt latch");target.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="hurt_laser",timeoutTicks=30)
    public static void HurtOnceLaserKeepsChargeAndFiresOneCast(GameTestHelper h) {
        var heart=fixture(h);var laser=heart.modules().add("laser");laser.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos().east(),Direction.EAST)));laser.edit("trigger",5,"");laser.edit("chargeTime",4,"");laser.edit("beamHits",1,"");laser.edit("hitInterval",2,"");laser.edit("interval",1,"");
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.damage(1);heart.modules().tick(heart);
        h.assertTrue(HeartModules.triggered(laser,heart),"Hurt laser did not start charging");
        h.runAfterDelay(5,()->{heart.modules().tick(heart);h.assertTrue(heart.modules().snapshot(heart,false).getList("Modules",10).getCompound(0).getString("LaserPhase").equals("firing"),"One-tick damage cut off laser charge");});
        h.runAfterDelay(9,()->{heart.modules().tick(heart);heart.modules().tick(heart);h.assertTrue(!HeartModules.triggered(laser,heart),"Laser Once repeated after cast completion");h.succeed();});
    }
    @GameTest(template="combat",batch="moveset_priority",timeoutTicks=40)
    public static void PriorityWaitsForConditionsCurrentMoveRestAndCooldown(GameTestHelper h) {
        var heart=fixture(h);var out=output(heart);var table=heart.modules().add("move_set");
        var pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setNoGravity(true);
        pig.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter()).add(0,0,4));h.getLevel().addFreshEntity(pig);
        for(int i=0;i<3;i++) {
            table.moveSet().edit("add",0,"",heart.modules());
            table.moveSet().edit("action_add",0,i+"|"+out.id(),heart.modules());
            table.moveSet().edit("action_duration",.2,i+"|0",heart.modules());
            table.moveSet().edit("dispatchBusy",1,Integer.toString(i),heart.modules());
            table.moveSet().edit("dispatchRest",2,Integer.toString(i),heart.modules());
            table.moveSet().edit("dispatchCooldown",i==0?0:20,Integer.toString(i),heart.modules());
            if(i>0){table.moveSet().edit("selection_mode",1,Integer.toString(i),heart.modules());table.moveSet().edit("phaseUpper",50,Integer.toString(i),heart.modules());}
        }
        var normal=table.moveSet().get(0).config().id();var first=table.moveSet().get(1).config().id();var second=table.moveSet().get(2).config().id();
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.modules().tick(heart);
        h.assertTrue(heart.modules().snapshot(heart,false).getList("Modules",10).getCompound(1).getCompound("MoveSetRuntime").getUUID("Current").equals(normal),"Unmet priority conditions suppressed weighted move");
        h.runAfterDelay(1,()->{heart.damage(heart.health().maximum()*.6f);heart.modules().tick(heart);h.assertTrue(heart.modules().snapshot(heart,false).getList("Modules",10).getCompound(1).getCompound("MoveSetRuntime").getUUID("Current").equals(normal),"Priority interrupted running move");});
        h.runAfterDelay(5,()->{heart.modules().tick(heart);h.assertTrue(!heart.modules().snapshot(heart,false).getList("Modules",10).getCompound(1).getCompound("MoveSetRuntime").hasUUID("Current"),"Priority bypassed rest");});
        h.runAfterDelay(6,()->{heart.modules().tick(heart);h.assertTrue(heart.modules().snapshot(heart,false).getList("Modules",10).getCompound(1).getCompound("MoveSetRuntime").getUUID("Current").equals(first),"Priority did not precede weighted pool in list order");});
        h.runAfterDelay(12,()->{heart.modules().tick(heart);h.assertTrue(heart.modules().snapshot(heart,false).getList("Modules",10).getCompound(1).getCompound("MoveSetRuntime").getUUID("Current").equals(second),"Priority ignored independent cooldown or list order");});
        h.runAfterDelay(18,()->{heart.modules().tick(heart);h.assertTrue(heart.modules().snapshot(heart,false).getList("Modules",10).getCompound(1).getCompound("MoveSetRuntime").getUUID("Current").equals(normal),"Cooling priority blocked weighted fallback");pig.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="moveset_priority_save",timeoutTicks=10)
    public static void PriorityModeSurvivesSaveCopyAndPreservesWeightedConfiguration(GameTestHelper h) {
        var heart=fixture(h);var table=heart.modules().add("move_set");var set=table.moveSet();set.edit("add",0,"",heart.modules());
        set.edit("dispatchWeight",123,"0",heart.modules());set.edit("selection_mode",1,"0",heart.modules());
        h.assertTrue(!set.edit("selection_mode",2,"0",heart.modules()),"Invalid selection mode accepted");
        var restored=VirtualModule.load(table.save()).moveSet();h.assertTrue(restored.get(0).priority()&&restored.get(0).config().value("dispatchWeight")==123,"Priority or stored weight lost on save/load");
        restored.edit("duplicate",0,"0",heart.modules());h.assertTrue(restored.get(1).priority()&&!restored.get(0).config().id().equals(restored.get(1).config().id()),"Duplicated move lost mode or stable identity");
        restored.edit("selection_mode",0,"0",heart.modules());h.assertTrue(!restored.get(0).priority()&&restored.get(0).config().value("dispatchWeight")==123,"Switching to weighted lost authored weight");
        var old=set.save();old.getCompound(0).remove("Priority");restored.load(old);h.assertTrue(!restored.get(0).priority(),"Old save did not default to Weighted");h.succeed();
    }
    @GameTest(template="combat",batch="virtual_commands",timeoutTicks=40)
    public static void VirtualCommandsImpulseRepeatConditionalChainAndMasterCleanup(GameTestHelper h) {
        var heart=fixture(h);var module=heart.modules().add("command");var program=module.commands();
        var board=h.getLevel().getScoreboard();String objective="vc"+heart.creatureId().toString().substring(0,8);
        h.getLevel().getServer().getCommands().performPrefixedCommand(h.getLevel().getServer().createCommandSourceStack(),"scoreboard objectives add "+objective+" dummy");
        String add="scoreboard players add count "+objective+" ";
        String[] commands={add+"1",add+"2",add+"4",add+"8","execute if entity @e[tag=never_virtual_command] run "+add+"100",add+"100",add+"1"};
        int[] modes={0,2,1,2,0,2,2};
        for(int i=0;i<commands.length;i++){program.edit("add",0,0,"");program.edit("command",i,0,commands[i]);program.edit("mode",i,modes[i],"");}
        program.edit("conditional",1,1,"");program.edit("conditional",3,1,"");program.edit("conditional",5,1,"");program.edit("interval",2,.2,"");
        h.assertTrue(module.anchors().isEmpty()&&HeartModules.status(module,heart).equals("valid"),"Commands required a Mark");
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.modules().tick(heart);
        var score=board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("count"),board.getObjective(objective));
        h.assertTrue(score.get()==16,"Impulse/repeat/conditional chain execution failed: "+score.get());
        h.runAfterDelay(2,()->{heart.modules().tick(heart);h.assertTrue(score.get()==16,"Impulse repeated or interval bypassed");});
        h.runAfterDelay(5,()->{heart.modules().tick(heart);h.assertTrue(score.get()==28,"Repeater did not execute its chain at interval");heart.wireless().master(false);heart.modules().tick(heart);});
        h.runAfterDelay(9,()->{heart.modules().tick(heart);h.assertTrue(score.get()==28,"Commands ran with master off");heart.wireless().master(true);heart.modules().tick(heart);h.assertTrue(score.get()==44,"Master reactivation failed to retrigger impulse");module.edit("enabled",0,"");});
        h.runAfterDelay(14,()->{heart.modules().tick(heart);h.assertTrue(score.get()==44,"Disabled command module still ran");board.removeObjective(board.getObjective(objective));h.succeed();});
    }
    @GameTest(template="combat",batch="virtual_commands_origin",timeoutTicks=15)
    public static void HurtCommandUsesCoreWorldOriginAndHasNoExecutorEntity(GameTestHelper h) {
        var heart=fixture(h);var m=heart.modules().add("command");m.edit("trigger",5,"");m.commands().edit("add",0,0,"");
        String tag="vc_"+heart.creatureId().toString().substring(0,8);m.commands().edit("command",0,0,"summon minecraft:armor_stand ~ ~1 ~ {Tags:[\""+tag+"\"],NoGravity:1b}");
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.modules().tick(heart);
        var origin=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter());
        var box=new net.minecraft.world.phys.AABB(origin,origin).inflate(3);
        h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,box,e->e.getTags().contains(tag)).isEmpty(),"Hurt command executed before damage");
        heart.damage(1);heart.modules().tick(heart);var stands=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,box,e->e.getTags().contains(tag));
        h.assertTrue(stands.size()==1&&stands.getFirst().position().distanceToSqr(origin.add(0,1,0))<.01,"Relative command coordinates did not use core world position");
        heart.modules().tick(heart);h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,box,e->e.getTags().contains(tag)).size()==1,"Hurt Once command duplicated");stands.forEach(net.minecraft.world.entity.Entity::discard);h.succeed();
    }
    @GameTest(template="combat",batch="virtual_commands_save",timeoutTicks=10)
    public static void CommandRowsSaveLoadReorderAndRejectUnauthorizedEditing(GameTestHelper h) {
        var heart=fixture(h);var m=heart.modules().add("command");var p=m.commands();p.edit("add",0,0,"");
        String longCommand="say "+"a".repeat(1000);h.assertTrue(p.edit("command",0,0,longCommand),"Long command rejected");p.edit("mode",0,1,"");p.edit("conditional",0,1,"");p.edit("interval",0,.3,"");p.edit("copy",0,0,"");
        var copy=VirtualModule.load(m.save()).commands();h.assertTrue(copy.rows().size()==2&&copy.rows().getFirst().command().equals(longCommand)&&copy.rows().getFirst().mode()==1&&copy.rows().getFirst().conditional()&&copy.rows().getFirst().interval()==6,"Command configuration failed reload");
        h.assertTrue(!copy.rows().getFirst().id().equals(copy.rows().getLast().id()),"Copy reused row identity");var id=copy.rows().getFirst().id();copy.edit("down",0,0,"");h.assertTrue(copy.rows().getLast().id().equals(id),"Reordering lost row identity");
        h.assertTrue(!copy.edit("command",0,0,"a".repeat(CommandProgram.MAX_TEXT+1))&&!copy.edit("command",0,0,"say a\nb")&&!copy.edit("interval",0,0,"")&&!copy.edit("mode",0,3,""),"Malformed command settings accepted");
        var player=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"NonOpCommandAuthor"));player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.setPos(dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),heart.getBlockPos().getCenter()));
        var menu=new HeartDashboardMenu(1,player.getInventory(),heart);h.assertTrue(!player.canUseGameMasterBlocks(),"Test player unexpectedly operator");
        h.assertTrue(!menu.action(player,heart.modules().revision(),m.id(),"commands","command",0,"0|say unauthorized")&&!menu.action(player,heart.modules().revision(),UUID.randomUUID(),"add","command",0,""),"Non-operator bypassed command authoring gate");h.succeed();
    }
    @GameTest(template="combat",batch="flame_nozzle_births")
    public static void FlameParticlesAreBornAtNozzleAndFlyAtConfiguredSpeed(GameTestHelper h) {
        var start=h.absolutePos(new BlockPos(3,6,3)).getCenter();var direction=new net.minecraft.world.phys.Vec3(0,0,1);
        int previousCount=0;
        for(double speed:new double[]{.1,.8,2,4}){
            var jet=new ModuleNetworking.FlameJet(start,direction,speed,32,.75,1,2.5F,42);
            var births=FlameJetGeometry.births(jet);
            h.assertTrue(births.size()>=previousCount,"Faster flame lost emission density");previousCount=births.size();
            for(var birth:births){
                h.assertTrue(Math.abs(birth.origin().subtract(start).dot(direction))<.00001&&birth.origin().distanceTo(start)<=.12001,"Particle was born along the trail instead of at nozzle");
                h.assertTrue(Math.abs(birth.velocity().dot(direction)-speed)<.00001,"Particle velocity did not follow flame speed");
                h.assertTrue(birth.phase()>=0&&birth.phase()<1,"Birth phase exceeded latest emission tick");
            }
            // Temporal sampling must keep neighbouring moving slices within 0.2 blocks at every speed.
            double gap=speed/(births.size()/4.);h.assertTrue(gap<=.20001,"High speed left gaps between emitted slices");
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
            try{ModuleNetworking.FlameJet.CODEC.encode(buffer,jet);var copy=ModuleNetworking.FlameJet.CODEC.decode(buffer);
                h.assertTrue(copy.equals(jet),"Flame birth packet lost speed or source parameters");
            }finally{buffer.release();}
        }h.succeed();
    }
    @GameTest(template="combat",batch="flame_style")
    public static void FlameStylesPersistAndNewEmissionsDoNotRepeatSpokes(GameTestHelper h) {
        var m=new VirtualModule("flame");m.editParticle("remove","minecraft:flame");m.editParticle("add","minecraft:soul_fire_flame");m.editParticle("size","minecraft:soul_fire_flame",2.5);
        var copy=VirtualModule.load(m.save());h.assertTrue(copy.particles().equals(List.of("minecraft:soul_fire_flame"))&&copy.particleEntry("minecraft:soul_fire_flame").size()==2.5F,"Soul fire or particle size did not reload");
        var old=m.save();old.remove("FlameParticleSelectionVersion");old.getCompound("Settings").remove("flameVisual");old.getCompound("Settings").remove("flameParticleSize");
        var legacy=VirtualModule.load(old);h.assertTrue(legacy.particles().equals(List.of("minecraft:flame"))&&legacy.particleEntry("minecraft:flame").size()==1F,"Old flames lost normal-fire defaults");
        var origin=h.absolutePos(new BlockPos(3,6,3)).getCenter();var direction=new net.minecraft.world.phys.Vec3(0,0,1);
        var a=FlameJetGeometry.births(new ModuleNetworking.FlameJet(origin,direction,.8,6,4,0,1,11));
        var b=FlameJetGeometry.births(new ModuleNetworking.FlameJet(origin,direction,.8,6,4,0,1,12));
        h.assertTrue(!a.equals(b),"New emissions repeated the same spokes");
        var unique=new java.util.HashSet<net.minecraft.world.phys.Vec3>();
        for(var birth:a){unique.add(birth.velocity());var end=birth.origin().add(birth.velocity().scale(6/.8));var delta=end.subtract(origin);
            h.assertTrue(Math.abs(delta.z-6)<.00001&&Math.hypot(delta.x,delta.y)<=4.00001,"Visual cone exceeded configured spread");}
        h.assertTrue(unique.size()==a.size(),"Cone particles collapsed into fixed ray directions");h.succeed();
    }
    @GameTest(template="combat",batch="flame_fast_wall",timeoutTicks=35)
    public static void FastFlameRetainsWallBlockingAndDamageCooldown(GameTestHelper h) {
        var heart=fixture(h);var m=heart.modules().add("flame");
        m.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.NORTH)));
        m.edit("trigger",0,"");m.edit("flameSpeed",4,"");m.edit("range",6,"");m.edit("damage",2,"");m.edit("burn",0,"");m.edit("interval",20,"");
        var start=ModuleCombat.nozzle(heart,m);var target=EntityType.COW.create(h.getLevel());target.setNoAi(true);target.setNoGravity(true);target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);target.setHealth(20);target.setPos(start.add(0,-.7,-3));h.getLevel().addFreshEntity(target);
        var wall=BlockPos.containing(start.add(0,0,-1.5));h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.modules().tick(heart);
        h.runAfterDelay(4,()->{h.assertTrue(target.getHealth()==20,"Fast flame crossed a solid wall");h.getLevel().setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());});
        h.runAfterDelay(8,()->h.assertTrue(target.getHealth()==18,"Fast flame did not reach nearby target promptly"));
        h.runAfterDelay(12,()->{h.assertTrue(target.getHealth()==18,"Denser visual particles increased hit frequency");target.discard();h.succeed();});
    }
    private static VirtualModule movement(HeartBlockEntity heart) {
        var m=heart.modules().add("linear_movement");m.edit("trigger",0,"");m.edit("interval",1000,"");
        m.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos(),Direction.NORTH)));return m;
    }
    @GameTest(template="combat",batch="linear_angles")
    public static void LinearAnglesAndStageEffectsPersist(GameTestHelper h) {
        var heart=fixture(h);var m=movement(heart);var forward=ModuleCombat.direction(heart,m);
        h.assertTrue(LinearMovementGeometry.direction(heart,m).distanceTo(forward)<.00001,"Zero angles changed marked direction");
        m.edit("moveHorizontal",90,"");var right=LinearMovementGeometry.direction(heart,m);
        h.assertTrue(right.dot(forward)<.00001&&right.x>.99,"Horizontal +90 did not turn right from North");
        m.edit("moveHorizontal",0,"");m.edit("moveVertical",90,"");
        h.assertTrue(LinearMovementGeometry.direction(heart,m).y>.99,"Vertical +90 did not turn upward");
        m.edit("moveHorizontal",-35,"");m.edit("moveVertical",20,"");m.movementParticles("Arrival").editParticle("add","minecraft:flame");
        var restored=VirtualModule.load(m.save());
        h.assertTrue(restored.value("moveHorizontal")==-35&&restored.value("moveVertical")==20&&restored.movementParticles("Arrival").particles().contains("minecraft:flame")&&!restored.movementParticles("Charge").particles().contains("minecraft:flame"),"Angles or independent effects lost on reload");
        h.assertTrue(MoveSet.TARGETS.contains("linear_movement"),"Movement cannot be bound to Move set");h.succeed();
    }
    @GameTest(template="combat",batch="linear_geometry")
    public static void GroundContactAllowsSlidingButStillBlocksWallsAndDownwardMovement(GameTestHelper h) {
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,6,4));
        for(int z=0;z<=4;z++)level.setBlockAndUpdate(p.offset(0,-1,z),Blocks.STONE.defaultBlockState());
        var raw=new net.minecraft.world.phys.AABB(p.getX(),p.getY()-.02,p.getZ(),p.getX()+1,p.getY()+.98,p.getZ()+1);
        var box=LinearMovementGeometry.contactBounds(raw);
        var bodies=List.of(new LinearMovementGeometry.Body(null,new org.joml.Vector3d(),new org.joml.Quaterniond(),box));
        var slide=new net.minecraft.world.phys.Vec3(0,0,3);
        h.assertTrue(LinearMovementGeometry.clear(level,bodies,net.minecraft.world.phys.Vec3.ZERO,slide,true),"Travel rejected shallow ground contact");
        h.assertTrue(LinearMovementGeometry.clear(level,bodies,net.minecraft.world.phys.Vec3.ZERO,slide,false),"Teleport rejected grounded endpoint");
        h.assertTrue(!LinearMovementGeometry.clear(level,bodies,net.minecraft.world.phys.Vec3.ZERO,new net.minecraft.world.phys.Vec3(0,-.2,0),true),"Contact tolerance allowed downward ground penetration");
        level.setBlockAndUpdate(p.offset(0,0,2),Blocks.STONE.defaultBlockState());
        h.assertTrue(!LinearMovementGeometry.clear(level,bodies,net.minecraft.world.phys.Vec3.ZERO,slide,true),"Travel passed through wall");
        level.setBlockAndUpdate(p.offset(0,0,3),Blocks.STONE.defaultBlockState());
        h.assertTrue(!LinearMovementGeometry.clear(level,bodies,net.minecraft.world.phys.Vec3.ZERO,slide,false),"Teleport accepted occupied endpoint");
        h.succeed();
    }
    @GameTest(template="combat",batch="linear_geometry")
    public static void WholeBodyPathAndEndpointChecks(GameTestHelper h) {
        var heart=fixture(h);var bodies=LinearMovementGeometry.capture(heart);var delta=new net.minecraft.world.phys.Vec3(0,0,-3);
        h.assertTrue(LinearMovementGeometry.clear(h.getLevel(),bodies,net.minecraft.world.phys.Vec3.ZERO,delta,true),"Open movement path rejected");
        // Obstruct the far limb, away from the mark's center line.
        var outer=bodies.getFirst().box();var wall=BlockPos.containing(outer.maxX-.3,outer.minY+.3,outer.minZ-1);
        h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        h.assertTrue(!LinearMovementGeometry.clear(h.getLevel(),bodies,net.minecraft.world.phys.Vec3.ZERO,delta,true),"Outer limb passed through wall");
        h.assertTrue(LinearMovementGeometry.clear(h.getLevel(),bodies,net.minecraft.world.phys.Vec3.ZERO,delta,false),"Teleport without path checking rejected a free endpoint");
        h.getLevel().setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(BlockPos.containing(outer.maxX-.3,outer.minY+.3,outer.minZ-3),Blocks.STONE.defaultBlockState());
        h.assertTrue(!LinearMovementGeometry.clear(h.getLevel(),bodies,net.minecraft.world.phys.Vec3.ZERO,delta,false),"Occupied endpoint accepted");h.succeed();
    }
    @GameTest(template="combat",batch="linear_teleport",timeoutTicks=35)
    public static void TeleportChargesThenMovesAndCancelsOnMasterOff(GameTestHelper h) {
        var heart=fixture(h);var m=movement(heart);m.edit("chargeTime",6,"");m.edit("moveDistance",3,"");
        var body=m.anchors().getFirst().body(heart);double initial=body.logicalPose().position().z();
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.modules().tick(heart);
        h.assertTrue(body.logicalPose().position().z()==initial,"Teleport moved before charge finished");
        h.runAfterDelay(3,()->h.assertTrue(Math.abs(body.logicalPose().position().z()-initial)<.1,"Charge moved body early"));
        h.runAfterDelay(9,()->{
            h.assertTrue(Math.abs(body.logicalPose().position().z()-(initial-3))<.15,"Teleport did not move the configured distance");
            m.edit("interval",1,"");heart.modules().clearRuntime();heart.modules().tick(heart);heart.wireless().master(false);
        });
        h.runAfterDelay(19,()->{h.assertTrue(Math.abs(body.logicalPose().position().z()-(initial-3))<.15,"Master off failed to cancel charge");h.succeed();});
    }
    @GameTest(template="combat",batch="linear_travel",timeoutTicks=35)
    public static void TravelCannotDisableWallChecks(GameTestHelper h) {
        var heart=fixture(h);var m=movement(heart);m.edit("linearMode",1,"");m.edit("chargeTime",0,"");m.edit("travelSpeed",20,"");m.edit("moveDistance",3,"");m.edit("moveObstruction",1,"");
        var body=m.anchors().getFirst().body(heart);double initial=body.logicalPose().position().z();
        var p=BlockPos.containing(m.anchors().getFirst().world(heart,false));
        for(int x=0;x<3;x++)for(int y=-3;y<=3;y++)h.getLevel().setBlockAndUpdate(p.offset(x,y,-2),Blocks.STONE.defaultBlockState());
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));heart.modules().tick(heart);
        h.runAfterDelay(8,()->{
            double z=body.logicalPose().position().z();
            h.assertTrue(z<initial-.1&&z>initial-1.1,"Travel failed to stop before wall");
            var list=heart.modules().snapshot(heart,false).getList("Modules",10);
            h.assertTrue(list.getCompound(0).getString("MovementPhase").equals("blocked"),"Travel did not report blocking");h.succeed();
        });
    }
    private static HeartBlockEntity fixture(GameTestHelper h) {
        var level=h.getLevel(); var pos=h.absolutePos(new BlockPos(4,6,4));
        level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos.east(2),Blocks.REDSTONE_LAMP.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east(),pos.east(2)),
                new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+2,pos.getY(),pos.getZ()));
        return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static void channel(HeartBlockEntity heart,int mode) {
        int i=heart.wireless().add(); var channel=heart.wireless().channel(i);
        channel.frequency(false,new ItemStack(Items.REDSTONE)); channel.set(0,1); channel.set(6,mode);
    }
    private static VirtualModule output(HeartBlockEntity heart) {
        var output=heart.modules().add("wireless_redstone");
        output.anchors(List.of(ModuleAnchor.create(heart,heart.getBlockPos().east(),Direction.EAST)));
        return output;
    }

    @GameTest(template="combat", batch="core_no_clock", timeoutTicks=65)
    public static void ActivatedCoreDoesNotEmitAutomaticPulse(GameTestHelper h) {
        var heart = fixture(h); heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));
        for (int tick = 1; tick <= 45; tick++) h.runAfterDelay(tick, () -> {
            h.assertTrue(heart.pulseSignal() == 0, "Activated core emitted autonomous heartbeat pulse");
            for (var face : Direction.values()) h.assertTrue(h.getLevel().getBlockState(heart.getBlockPos()).getSignal(h.getLevel(), heart.getBlockPos(), face) == 0, "Core powered adjacent face");
        });
        h.runAfterDelay(46, h::succeed);
    }
    @GameTest(template="combat", batch="core_protection", timeoutTicks=30)
    public static void CreativeCoreRequiresShiftAndCannotLeakRemovalPermission(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(new BlockPos(4, 6, 4));
        level.setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        var player = FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CoreAuthor"));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.setShiftKeyDown(false);
        h.assertTrue(!player.gameMode.destroyBlock(pos) && level.getBlockState(pos).is(SableLife.HEART.get()), "Creative click removed core without Shift");
        var heart = (HeartBlockEntity) level.getBlockEntity(pos);
        dev.sablelife.ownership.BodyOwnership.get(level).bind(pos, heart.creatureId());
        dev.sablelife.ownership.LifeLifecycle.get(level.getServer()).setBreakable(heart.creatureId(), false);
        player.setShiftKeyDown(true);
        h.assertTrue(player.gameMode.destroyBlock(pos) && level.getBlockState(pos).isAir(), "Shift did not permit deliberate core removal");
        h.assertTrue(dev.sablelife.ownership.BodyProtection.AUTHOR_BREAK.get() == null, "Author removal permission leaked beyond one call");
        h.succeed();
    }
    @GameTest(template="combat", batch="self_attack", timeoutTicks=30)
    public static void OwnArrowsDoNotDamageBodyButOtherCreaturesCan(GameTestHelper h) {
        var heart = fixture(h); heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel()));
        var pos = heart.getBlockPos().east(); var point = pos.getCenter();
        var arrow = new dev.sablelife.attack.ModuleArrow(h.getLevel(), point.x, point.y, point.z, ItemStack.EMPTY, ItemStack.EMPTY);
        arrow.setBaseDamage(8); arrow.setDeltaMovement(1, 0, 0);
        arrow.getPersistentData().putUUID("SableLifeAttackOwner", heart.creatureId());
        var impact = new net.minecraft.world.phys.BlockHitResult(point, Direction.WEST, pos, false);
        var hp = heart.health().current();
        dev.sablelife.combat.BodyCombat.onProjectileImpact(new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(arrow, impact));
        h.assertTrue(heart.health().current() == hp, "Own arrow damaged own body");
        arrow.getPersistentData().putUUID("SableLifeAttackOwner", UUID.randomUUID());
        dev.sablelife.combat.BodyCombat.onProjectileImpact(new net.neoforged.neoforge.event.entity.ProjectileImpactEvent(arrow, impact));
        h.assertTrue(heart.health().current() < hp, "Self protection also blocked another creature's arrow");
        h.succeed();
    }


    @GameTest(template="combat",batch="redstone_shared")
    public static void sharedMarkerMaxStrengthDirectionAndDeathCleanup(GameTestHelper h) {
        var heart=fixture(h); var first=output(heart); first.edit("trigger",0,""); first.edit("redstonePower",4,"");
        first.edit("redstoneFaces",1,""); var second=output(heart); second.edit("trigger",0,""); second.edit("redstonePower",11,""); second.edit("redstoneFaces",1,"");
        heart.tryActivate(FakePlayerFactory.getMinecraft(h.getLevel())); heart.modules().tick(heart);
        var marker=heart.getBlockPos().east();
        h.assertTrue(h.getLevel().getSignal(marker.east(),Direction.EAST)==11,"Shared marker did not combine highest power");
        h.assertTrue(h.getLevel().getSignal(marker.west(),Direction.WEST)==0,"Marked-face-only output leaked backwards");
        heart.modules().remove(second.id()); heart.modules().tick(heart);
        h.assertTrue(h.getLevel().getSignal(marker.east(),Direction.EAST)==4,"Deleting one module erased other marker output");
        heart.wireless().master(false); heart.modules().tick(heart);
        h.assertTrue(h.getLevel().getSignal(marker.east(),Direction.EAST)==0,"Master off retained output");
        heart.wireless().master(true); heart.modules().tick(heart); heart.damage(heart.health().maximum()); heart.modules().tick(heart);
        h.assertTrue(h.getLevel().getSignal(marker.east(),Direction.EAST)==0,"Death retained marker output"); h.succeed();
    }

}
