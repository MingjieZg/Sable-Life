package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.combat.EntityHitbox;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.ownership.LoadedCoreIndex;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("sablelife_hitbox_tests")
@PrefixGameTestTemplate(false)
public final class HitboxEffectsGameTests {
    private static HeartBlockEntity core(GameTestHelper h,int offset,int count){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4+offset,40,4));
        level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(1000,100);core.tryActivate(TestActors.creative(level));LoadedCoreIndex.observe(core);
        for(int i=0;i<count;i++){var m=core.modules().add("entity_hitbox");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP)));}
        core.modules().damageBoxes.tick(core);return core;
    }
    private static List<EntityHitbox> boxes(HeartBlockEntity core){return core.modules().damageBoxes.entities();}

    @GameTest(template="combat",batch="hitbox_effect_immunity",timeoutTicks=90)
    public static void BeneficialHarmfulAndInfiniteStatusesAreRejected(GameTestHelper h){
        var core=core(h,0,1);var box=boxes(core).getFirst();
        h.assertTrue(!box.isAffectedByPotions(),"Receiver accepts potion targeting");
        for(var effect:List.of(MobEffects.POISON,MobEffects.WITHER,MobEffects.MOVEMENT_SLOWDOWN,MobEffects.REGENERATION,MobEffects.DAMAGE_RESISTANCE)){
            var status=new MobEffectInstance(effect,80);
            h.assertTrue(!box.canBeAffected(status)&&!box.addEffect(status),"Receiver accepted a status");
            box.forceAddEffect(new MobEffectInstance(effect,-1),null);
        }
        h.runAfterDelay(60,()->{h.assertTrue(box.getActiveEffects().isEmpty()&&core.health().current()==1000,"Rejected status remained or changed life health");h.succeed();});
    }

    @GameTest(template="combat",batch="hitbox_effect_overlap",timeoutTicks=100)
    public static void FiveReceiversDoNotAccumulateStatusDamage(GameTestHelper h){
        var core=core(h,0,5);
        for(int i=0;i<5;i++){int index=i;h.runAfterDelay(i*3+1,()->{
            var box=boxes(core).get(index);box.addEffect(new MobEffectInstance(MobEffects.POISON,60));box.forceAddEffect(new MobEffectInstance(MobEffects.WITHER,60),null);
        });}
        h.runAfterDelay(80,()->{h.assertTrue(core.health().current()==1000&&boxes(core).stream().allMatch(b->b.getActiveEffects().isEmpty()),"Multiple receivers accumulated statuses or damage");h.succeed();});
    }

    private static net.minecraft.world.entity.animal.Pig control(GameTestHelper h,net.minecraft.world.phys.Vec3 point){
        var pig=net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());pig.setPos(point);pig.setNoAi(true);pig.setNoGravity(true);h.getLevel().addFreshEntity(pig);return pig;
    }

    @GameTest(template="combat",batch="hitbox_instant_potion",timeoutTicks=40)
    public static void NativeHarmingSplashSkipsReceiverButHitsOrdinaryMob(GameTestHelper h) throws Exception {
        var core=core(h,0,1);var box=boxes(core).getFirst();var center=box.getBoundingBox().getCenter();var pig=control(h,center.add(0,0,1));
        var potion=new net.minecraft.world.entity.projectile.ThrownPotion(h.getLevel(),center.x,center.y,center.z);
        var method=net.minecraft.world.entity.projectile.ThrownPotion.class.getDeclaredMethod("applySplash",Iterable.class,net.minecraft.world.entity.Entity.class);method.setAccessible(true);
        method.invoke(potion,List.of(new MobEffectInstance(MobEffects.HARM,1)),box);core.modules().damageBoxes.flush(core);
        h.assertTrue(pig.getHealth()<pig.getMaxHealth(),"Native splash fixture did not harm the control mob");
        h.assertTrue(core.health().current()==1000&&box.getActiveEffects().isEmpty(),"Instant potion harmed the receiver");h.succeed();
    }

    @GameTest(template="combat",batch="hitbox_lingering_potion",timeoutTicks=60)
    public static void NativeLingeringCloudSkipsReceiversAndAffectsOrdinaryMob(GameTestHelper h){
        var core=core(h,0,3);var center=boxes(core).getFirst().position();var pig=control(h,center.add(0,0,1));
        var cloud=new net.minecraft.world.entity.AreaEffectCloud(h.getLevel(),center.x,center.y,center.z);cloud.setRadius(3);cloud.setWaitTime(0);cloud.setDuration(80);cloud.addEffect(new MobEffectInstance(MobEffects.POISON,100));h.getLevel().addFreshEntity(cloud);
        h.runAfterDelay(20,()->{h.assertTrue(pig.hasEffect(MobEffects.POISON),"Native cloud missed the control mob");h.assertTrue(core.health().current()==1000&&boxes(core).stream().allMatch(b->b.getActiveEffects().isEmpty()),"Lingering cloud affected a receiver");cloud.discard();h.succeed();});
    }

    @GameTest(template="combat",batch="hitbox_tipped_arrow",timeoutTicks=60)
    public static void RealTippedArrowKeepsImpactDamageWithoutPoison(GameTestHelper h){
        var core=core(h,0,1);var m=core.modules().find(boxes(core).getFirst().moduleId());m.edit("hitboxOffsetY",4,"");core.modules().damageBoxes.tick(core);var box=boxes(core).getFirst();var center=box.getBoundingBox().getCenter();
        var arrow=new net.minecraft.world.entity.projectile.Arrow(h.getLevel(),center.x-3,center.y,center.z,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TIPPED_ARROW),null);arrow.addEffect(new MobEffectInstance(MobEffects.POISON,200));arrow.setNoGravity(true);arrow.setDeltaMovement(1,0,0);h.getLevel().addFreshEntity(arrow);
        h.runAfterDelay(15,()->{h.assertTrue(core.health().current()<1000,"Tipped arrow lost its direct impact damage");h.assertTrue(box.getActiveEffects().isEmpty(),"Tipped arrow left poison on the receiver");h.succeed();});
    }

    @GameTest(template="combat",batch="hitbox_status_direct_damage")
    public static void PotionImmunityDoesNotBlockOrdinaryMagicDamage(GameTestHelper h){
        var core=core(h,0,1);var box=boxes(core).getFirst();box.hurt(h.getLevel().damageSources().magic(),12);core.modules().damageBoxes.flush(core);
        h.assertTrue(core.health().current()==988,"Potion immunity blocked direct magic damage");h.succeed();
    }
}
