package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.attack.ContactBox;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import java.util.*;
@GameTestHolder("sablelife_facing_tests") @PrefixGameTestTemplate(false)
public final class SignalTurnGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h,boolean control,boolean ground){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,ground?6:40,3));l.setBlockAndUpdate(p,(control?SableLife.REDSTONE_CORE.get():SableLife.HEART.get()).defaultBlockState());l.setBlockAndUpdate(p.north(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.north()),new BoundingBox3i(p.getX(),p.getY(),p.getZ()-1,p.getX(),p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());}
    private static VirtualModule module(HeartBlockEntity c){var m=c.modules().add("signal_turn");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.NORTH)));m.edit("turnLeftChannel",1,"");int i=c.wireless().add();c.wireless().channel(i).set(0,1);c.wireless().channel(i).set(6,3);if(!c.isControlCore())c.tryActivate(TestActors.creative((net.minecraft.server.level.ServerLevel)c.getLevel()));return m;}
    private static String state(HeartBlockEntity c,int n){return c.modules().snapshot(c,true).getList("Modules",10).getCompound(n).getString("TurnState");}
    @GameTest(template="combat",batch="signal_turn_math")
    public static void AnalogStrengthSubtractsAndAllAxesFollowTheMarkedFront(GameTestHelper h){
        h.assertTrue(SignalTurnGeometry.rate(15,0,120)==120&&SignalTurnGeometry.rate(7,0,120)==56&&SignalTurnGeometry.rate(0,0,120)==0&&SignalTurnGeometry.rate(15,7,120)==64&&SignalTurnGeometry.rate(7,7,120)==0,"Signal speed interpolation/subtraction failed");
        var f=SignalTurnGeometry.frame(new Quaterniond(),Direction.NORTH);h.assertTrue(ContactBox.rotate(SignalTurnGeometry.turn(f,1800,0,0,1),f.front()).distanceTo(new Vec3(-1,0,0))<1e-6,"Left yaw axis inverted");h.assertTrue(ContactBox.rotate(SignalTurnGeometry.turn(f,0,1800,0,1),f.front()).distanceTo(new Vec3(0,1,0))<1e-6,"Up pitch axis inverted");h.assertTrue(ContactBox.rotate(SignalTurnGeometry.turn(f,0,0,1800,1),f.up()).distanceTo(new Vec3(1,0,0))<1e-6,"Clockwise roll axis inverted");
        for(var face:Direction.values()){var axes=SignalTurnGeometry.frame(new Quaterniond().rotateY(.7),face);h.assertTrue(Math.abs(axes.front().dot(axes.up()))<1e-6&&Math.abs(axes.up().dot(axes.right()))<1e-6&&Math.abs(axes.right().length()-1)<1e-6,"Vertical/rotated front frame invalid");}h.succeed();
    }
    @GameTest(template="combat",batch="signal_turn_life",timeoutTicks=30)
    public static void LifeCoreRotatesAndOpposingSignalsStopItWithoutTrackingATarget(GameTestHelper h){
        var c=fixture(h,false,false);var m=module(c);m.edit("turnCollision",0,"");c.wireless().programOutputs("test_turn",Map.of(0,7));c.modules().tick(c);h.assertTrue(state(c,0).equals("turning"),"Manual signal did not claim rotation");
        h.runAfterDelay(4,()->{var front=ContactBox.rotate(new Quaterniond(m.anchors().getFirst().body(c).logicalPose().orientation()),new Vec3(0,0,-1));h.assertTrue(front.x<-.03,"Actual body failed to rotate from analog signal");m.edit("turnRightChannel",1,"");c.modules().tick(c);});
        h.runAfterDelay(5,()->{c.modules().tick(c);h.assertTrue(state(c,0).equals("no_input"),"Equal opposing input did not stop yaw");c.wireless().master(false);c.modules().tick(c);h.assertTrue(state(c,0).equals("idle"),"Master Off retained manual rotation");h.succeed();});
    }
    @GameTest(template="combat",batch="signal_turn_control",timeoutTicks=30)
    public static void RedstoneCoreSupportsSignalRotationAndManualInputPausesAutoFacing(GameTestHelper h){
        var c=fixture(h,true,false);var m=module(c);m.edit("turnCollision",0,"");var auto=c.modules().add("auto_facing");auto.anchors(m.anchors());c.wireless().programOutputs("test_turn",Map.of(0,15));c.modules().tick(c);
        h.assertTrue(state(c,0).equals("turning")&&c.modules().snapshot(c,true).getList("Modules",10).getCompound(1).getString("FacingState").equals("busy"),"Manual signal did not take priority on Redstone Core");
        h.runAfterDelay(4,()->{var front=ContactBox.rotate(new Quaterniond(m.anchors().getFirst().body(c).logicalPose().orientation()),new Vec3(0,0,-1));h.assertTrue(front.x<-.03,"Redstone Core did not rotate");m.edit("enabled",0,"");c.modules().tick(c);h.succeed();});
    }
    @GameTest(template="combat",batch="signal_turn_partial",timeoutTicks=30)
    public static void GroundBlockedPitchStillAllowsHorizontalSignalTurning(GameTestHelper h){
        var c=fixture(h,false,true);for(var p:BlockPos.betweenClosed(h.absolutePos(new BlockPos(0,5,0)),h.absolutePos(new BlockPos(9,5,9))))h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());var m=module(c);m.edit("turnSpeed",180,"");m.edit("turnDownChannel",1,"");c.wireless().programOutputs("test_turn",Map.of(0,15));c.modules().tick(c);
        h.assertTrue(state(c,0).equals("partial"),"Ground fixture did not isolate a blocked pitch");
        h.runAfterDelay(6,()->{var front=ContactBox.rotate(new Quaterniond(m.anchors().getFirst().body(c).logicalPose().orientation()),new Vec3(0,0,-1));h.assertTrue(front.x<-.1&&Math.abs(front.y)<.1,"Blocked vertical axis stopped clear yaw or penetrated floor");h.succeed();});
    }
    @GameTest(template="combat",batch="signal_turn_external",timeoutTicks=25)
    public static void ExternalReceiverStrengthDrivesTheSameAnalogRate(GameTestHelper h){
        var c=fixture(h,false,false);var m=module(c);m.edit("turnLeftSource",1,"");var row=c.wireless().channel(0);row.set(6,1);row.frequency(false,new ItemStack(Items.AMETHYST_SHARD));row.frequency(true,new ItemStack(Items.FEATHER));
        var key=net.createmod.catnip.data.Couple.create(com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency.of(new ItemStack(Items.AMETHYST_SHARD)),com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency.of(new ItemStack(Items.FEATHER)));
        var location=BlockPos.containing(ModuleArena.corePosition(c).add(0,1,3));var source=new com.simibubi.create.content.redstone.link.IRedstoneLinkable(){public int getTransmittedStrength(){return 7;}public void setReceivedStrength(int value){}public boolean isListening(){return false;}public boolean isAlive(){return true;}public net.createmod.catnip.data.Couple<com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency> getNetworkKey(){return key;}public BlockPos getLocation(){return location;}};
        com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(h.getLevel(),source);c.wireless().tick(h.getLevel());
        h.runAfterDelay(5,()->{c.wireless().tick(h.getLevel());com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.updateNetworkOf(h.getLevel(),source);h.assertTrue(ModuleSignalTurn.input(c,m,"turnLeft")==7&&SignalTurnGeometry.rate(ModuleSignalTurn.input(c,m,"turnLeft"),0,m.value("turnSpeed"))==56,"External receiver lost proportional strength: input="+c.wireless().input(0)+", network="+com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.getNetworkOf(h.getLevel(),source).stream().map(e->e.getClass().getSimpleName()+":"+e.isListening()+":"+com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.withinRange(source,e)).toList());com.simibubi.create.Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(h.getLevel(),source);h.succeed();});
    }
    @GameTest(template="combat",batch="signal_turn_save")
    public static void SixDirectionSettingsPresetsAndDeletedChannelReferencesStaySafe(GameTestHelper h){
        var c=fixture(h,false,false);var m=module(c);c.wireless().add();m.edit("turnRightChannel",2,"");m.edit("turnRightSource",1,"");m.edit("turnSpeed",345,"");m.edit("turnCollision",0,"");var copy=ModulePreset.create(m.save());h.assertTrue(copy.value("turnSpeed")==345&&copy.value("turnCollision")==0&&copy.value("turnRightSource")==1&&copy.value("turnRightChannel")==2&&copy.anchors().isEmpty(),"Turn preset lost settings or copied marks");c.wireless().remove(0);h.assertTrue(m.value("turnLeftChannel")==0&&m.value("turnRightChannel")==1,"Deleted channel silently redirected a turn direction");h.succeed();
    }
}
