package dev.sablelife.testing;
import dev.sablelife.combat.BodyCombat;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_combat_tests") @PrefixGameTestTemplate(false)
public final class CombatFeedbackGameTests {
 @GameTest(template="combat",batch="lethal_feedback") public static void LastHitActionbarTurnsRedWhileNormalHitIsWhite(GameTestHelper h)throws Exception{
  var level=h.getLevel();var p=h.absolutePos(new BlockPos(5,35,5));level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());h.assertTrue(core.configure(10,40)&&core.activateForRespawn(),"Core fixture failed");
  var messages=new ArrayList<Component>();var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"FeedbackTest")){@Override public void displayClientMessage(Component message,boolean actionbar){if(actionbar)messages.add(message);}};
  var hit=new BlockHitResult(core.getBlockPos().getCenter(),Direction.UP,core.getBlockPos(),false);var feedback=BodyCombat.class.getDeclaredMethod("feedback",net.minecraft.server.level.ServerLevel.class,BlockHitResult.class,HeartBlockEntity.class,float.class,net.minecraft.world.entity.player.Player.class,boolean.class);feedback.setAccessible(true);core.damage(1);feedback.invoke(null,level,hit,core,1f,player,false);h.assertTrue(messages.getLast().getStyle().getColor().equals(TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.WHITE)),"Non-lethal message not white");
  core.forceDeath();feedback.invoke(null,level,hit,core,9f,player,false);h.assertTrue(messages.getLast().getStyle().getColor().equals(TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.RED)),"Lethal message not red before delayed cleanup");h.succeed();
 }
}
