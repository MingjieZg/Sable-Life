package dev.sablelife.testing;
import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_freeze_tests") @PrefixGameTestTemplate(false)
public final class FreezeGameTests {
    private static SlashGuardGameTests.Defender player(GameTestHelper h,Vec3 point){var p=new SlashGuardGameTests.Defender(h.getLevel());p.setPos(point);p.setNoGravity(true);h.getLevel().addNewPlayer(p);return p;}
    private static PlayerFreeze.Source source(){return new PlayerFreeze.Source(UUID.randomUUID(),UUID.randomUUID());}
    @GameTest(template="combat") public static void SettingsPresetAndMoveSetIntegration(GameTestHelper h){
        var m=new VirtualModule("freeze");h.assertTrue(m.known()&&VirtualModule.AVAILABLE_TYPES.contains("freeze")&&MoveSet.TARGETS.contains("freeze"),"Freeze missing from catalog/Move set");
        h.assertTrue(m.particles().equals(List.of("minecraft:snowflake"))&&m.value("freezeWarning")==30&&m.value("freezeDuration")==40,"Freeze defaults missing");
        h.assertTrue(m.edit("releasePosition",1,"")&&m.edit("freezeWarning",0,"")&&m.edit("freezeDuration",1200,"")&&m.editParticle("add","sablelife:soft_fog")&&m.editParticle("color","sablelife:soft_fog",0x3344CC),"Freeze edits rejected");
        var copy=VirtualModule.load(m.save());var preset=ModulePreset.create(m.save());h.assertTrue(copy.particles().size()==2&&TargetCastOrigin.targeted(copy)&&copy.value("freezeDuration")==1200&&preset.particleEntry("sablelife:soft_fog").color()==0x3344CC,"Freeze NBT/preset failed");
        m.editParticle("remove","minecraft:snowflake");h.assertTrue(!VirtualModule.load(m.save()).particles().contains("minecraft:snowflake"),"Removed default warning returned");h.succeed();
    }
    @GameTest(template="combat") public static void RangeEligibilityAndNoDamage(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("freeze");m.edit("freezeRadius",2,"");var o=h.absoluteVec(new Vec3(5,3,5));var inside=player(h,o);var outside=player(h,o.add(5,0,0));var creative=player(h,o);creative.setGameMode(GameType.CREATIVE);var spectator=player(h,o);spectator.setGameMode(GameType.SPECTATOR);float hp=inside.getHealth();
        h.assertTrue(ModuleFreeze.capture(h.getLevel(),c,m,List.of(o))==1&&PlayerFreeze.frozen(inside)&&!PlayerFreeze.frozen(outside)&&!PlayerFreeze.frozen(creative)&&!PlayerFreeze.frozen(spectator)&&inside.getHealth()==hp,"Freeze eligibility/range or health changed");
        PlayerFreeze.release(h.getLevel().getServer(),new PlayerFreeze.Source(c.creatureId(),m.id()));for(var p:List.of(inside,outside,creative,spectator))p.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=30) public static void OverlappingSourcesExpireIndependently(GameTestHelper h){
        var p=player(h,h.absoluteVec(new Vec3(5,3,5)));var a=source();var b=source();PlayerFreeze.apply(p,a,4);PlayerFreeze.apply(p,b,12);PlayerFreeze.release(h.getLevel().getServer(),a);h.assertTrue(PlayerFreeze.frozen(p),"One source cleared another");
        h.runAfterDelay(6,()->h.assertTrue(PlayerFreeze.frozen(p),"Remaining source expired early"));h.runAfterDelay(14,()->{h.assertTrue(!PlayerFreeze.frozen(p),"Freeze outlived all leases");p.discard();h.succeed();});
    }
    @GameTest(template="combat") public static void PacketConstraintPreservesLookAndRelease(GameTestHelper h){
        var p=player(h,h.absoluteVec(new Vec3(5,3,5)));var anchor=p.position();var id=source();PlayerFreeze.apply(p,id,40);
        var input=new ServerboundMovePlayerPacket.PosRot(anchor.x+10,anchor.y+1,anchor.z-10,73,-25,false);var fixed=PlayerFreeze.constrain(p,input);
        h.assertTrue(fixed.getX(0)==anchor.x&&fixed.getY(0)==anchor.y&&fixed.getZ(0)==anchor.z&&fixed.getYRot(0)==73&&fixed.getXRot(0)==-25,"Move/jump escaped or view locked");
        p.setDeltaMovement(new Vec3(2,3,4));PlayerFreeze.post(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));h.assertTrue(p.getDeltaMovement().lengthSqr()==0,"Knockback retained");
        PlayerFreeze.release(h.getLevel().getServer(),id);h.assertTrue(PlayerFreeze.constrain(p,input)==input,"Released movement packet still constrained");p.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=30) public static void WarningAllowsEscapeAndCapturesLateEntry(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("freeze");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),net.minecraft.core.Direction.UP)));m.edit("freezeRadius",2,"");m.edit("freezeWarning",8,"");m.edit("freezeDuration",20,"");var o=ModuleFreeze.centers(c,m).getFirst();var escaping=player(h,o);var entering=player(h,o.add(6,0,0));
        c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);h.assertTrue(!PlayerFreeze.frozen(escaping),"Warning froze early");
        h.runAfterDelay(3,()->{escaping.setPos(o.add(6,0,0));entering.setPos(o);});
        h.runAfterDelay(11,()->{h.assertTrue(!PlayerFreeze.frozen(escaping)&&PlayerFreeze.frozen(entering),"Activation did not sample current positions");m.edit("enabled",0,"");c.modules().tick(c);h.assertTrue(!PlayerFreeze.frozen(entering),"Disabled module did not release freeze");escaping.discard();entering.discard();h.succeed();});
    }
    @GameTest(template="combat",timeoutTicks=30) public static void TargetPositionStaysFixedDuringWarning(GameTestHelper h){
        var c=SlashGuardGameTests.core(h);var m=c.modules().add("freeze");m.edit("releasePosition",1,"");m.edit("freezeRadius",1,"");m.edit("freezeWarning",8,"");var o=TargetCastOrigin.searchOrigin(c,m).add(8,4,0);var p=player(h,o);c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);
        h.assertTrue(HeartModules.status(m,c).equals("valid")&&ModuleFreeze.centers(c,m).size()==1,"Target mode requires Mark");h.runAfterDelay(3,()->p.setPos(o.add(6,0,0)));
        h.runAfterDelay(11,()->{h.assertTrue(!PlayerFreeze.frozen(p),"Warning chased target");c.modules().clearRuntime();p.discard();h.succeed();});
    }
    @GameTest(template="combat") public static void DeathAndGameModeReleaseLocks(GameTestHelper h){var p=player(h,h.absoluteVec(new Vec3(5,3,5)));PlayerFreeze.apply(p,source(),30);p.setGameMode(GameType.CREATIVE);h.assertTrue(!PlayerFreeze.frozen(p),"Creative mode remained frozen");p.setGameMode(GameType.SURVIVAL);PlayerFreeze.apply(p,source(),30);p.setHealth(0);h.assertTrue(!PlayerFreeze.frozen(p),"Dead player remained frozen");p.discard();h.succeed();}
    @GameTest(template="combat") public static void FreezePacketRoundTrip(GameTestHelper h){var p=new FreezeStatePacket(40,new Vec3(1.25,64,-2.5));var b=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{FreezeStatePacket.CODEC.encode(b,p);h.assertTrue(FreezeStatePacket.CODEC.decode(b).equals(p),"Freeze payload lost anchor/duration");}finally{b.release();}h.succeed();}
}

