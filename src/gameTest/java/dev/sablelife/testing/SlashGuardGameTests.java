package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_blast_tests") @PrefixGameTestTemplate(false)
public final class SlashGuardGameTests {
 public static final class Defender extends net.neoforged.neoforge.common.util.FakePlayer {
  public Defender(net.minecraft.server.level.ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SlashDefender"));setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
   try{var f=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");f.setAccessible(true);f.setInt(this,0);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
  @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source){return false;}
 }
 public static HeartBlockEntity core(GameTestHelper h){
  var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,40,3));l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());
  var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));
  var c=(HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());c.configure(100,60);return c;
 }
 public static VirtualModule slash(HeartBlockEntity c){
  var area=c.modules().add("surface_field");area.edit("fieldRadius",.5,"");var m=c.modules().add("slash");m.impactField(area.id());
  m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.SOUTH)));m.edit("slashSpeed",80,"");m.edit("interval",1000,"");m.edit("damage",2,"");m.edit("slashIgnite",1,"");m.edit("burn",4,"");m.editEffect("add","minecraft:slowness",0);return m;
 }
 public static Vec3 position(GameTestHelper h,HeartBlockEntity c,VirtualModule m){
  var p=m.anchors().getFirst().world(c,false).add(0,-.4,3);var ground=BlockPos.containing(p.add(0,-1,0));
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)h.getLevel().setBlockAndUpdate(ground.offset(x,0,z),Blocks.STONE.defaultBlockState());return p;
 }
 @GameTest(template="combat",batch="slash_shield_option")
 public static void ActualSlashHonorsOptionFacingAndEffects(GameTestHelper h)throws Exception {
  var c=core(h);var m=slash(c);var p=position(h,c,m);var victim=new Defender(h.getLevel());victim.setPos(p);h.getLevel().addNewPlayer(victim);
  try {
   h.assertTrue(m.value("slashBlockable")==0,"Existing slashes must remain unblockable by default");
   c.tryActivate(TestActors.creative(h.getLevel()));
   for(boolean option:new boolean[]{false,true})for(boolean front:new boolean[]{true,false})for(int damage:new int[]{0,4}) {
    victim.stopUsingItem();victim.setHealth(20);victim.clearFire();victim.removeAllEffects();victim.setPos(p);victim.setYRot(front?180:0);victim.setYHeadRot(front?180:0);
    var hand=damage==0?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;var shield=new ItemStack(Items.SHIELD);victim.setItemInHand(hand,shield);victim.startUsingItem(hand);
    var remaining=LivingEntity.class.getDeclaredField("useItemRemaining");remaining.setAccessible(true);remaining.setInt(victim,shield.getUseDuration(victim)-6);
    h.assertTrue(victim.isBlocking(),"Fixture shield not raised");m.edit("slashBlockable",option?1:0,"");m.edit("damage",damage,"");c.modules().surfaceFields.clear();
    h.assertTrue(VirtualModule.load(m.save()).value("slashBlockable")==m.value("slashBlockable"),"Block option lost on save");
    var runtime=new ModuleSlash();runtime.tick(c);boolean blocked=option&&front;
    h.assertTrue(victim.getHealth()==(blocked?20:20-damage),"Wrong HP: option="+option+" front="+front+" damage="+damage+" hp="+victim.getHealth());
    h.assertTrue(victim.isOnFire()!=blocked&&victim.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)!=blocked,"Blocked slash applied fire/effects or accepted hit lost effects");
    h.assertTrue(c.modules().surfaceFields.count()==(blocked?0:1),"Blocked slash created impact area");
    if(blocked&&damage>0)h.assertTrue(shield.getDamageValue()>0,"Native shield durability was bypassed");
    int wear=shield.getDamageValue();runtime.tick(c);h.assertTrue(shield.getDamageValue()==wear,"Same blade repeatedly damaged shield");runtime.clear();
   }
   m.edit("slashBlockable",1,"");victim.stopUsingItem();victim.setHealth(20);victim.setPos(p);victim.setYRot(180);victim.setYHeadRot(180);new ModuleSlash().tick(c);
   h.assertTrue(victim.getHealth()==16,"Unraised shield blocked slash");m.edit("enabled",0,"");h.succeed();
  } finally {h.getLevel().removePlayerImmediately(victim,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);}
 }
}
