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
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("sablelife_hitbox_tests")
@PrefixGameTestTemplate(false)
public final class HitboxHurtWindowGameTests {
    private static HeartBlockEntity core(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,40,4));level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(1000,100);core.tryActivate(TestActors.creative(level));LoadedCoreIndex.observe(core);
        for(int i=0;i<3;i++){var module=core.modules().add("entity_hitbox");module.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP)));}
        core.modules().damageBoxes.tick(core);return core;
    }
    private static List<EntityHitbox> boxes(HeartBlockEntity core){return core.modules().damageBoxes.entities();}
    private static void hit(HeartBlockEntity core,DamageSource source,float damage){for(var box:boxes(core))box.hurt(source,damage);core.modules().damageBoxes.flush(core);}
    private static void hp(GameTestHelper h,HeartBlockEntity core,float expected){h.assertTrue(Math.abs(core.health().current()-expected)<.001,"Expected HP "+expected+", got "+core.health().current());}

    @GameTest(template="combat",batch="hitbox_shared_window",timeoutTicks=40)
    public static void SwitchingBoxesDoesNotResetTheSharedHurtWindow(GameTestHelper h){
        var core=core(h);var source=h.getLevel().damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel()));hit(core,source,14);
        for(int i=1;i<10;i++)h.runAfterDelay(i,()->{hit(core,source,14);hp(h,core,986);});
        h.runAfterDelay(10,()->{hit(core,source,14);hp(h,core,972);h.succeed();});
    }
    @GameTest(template="combat",batch="hitbox_window_stronger",timeoutTicks=30)
    public static void StrongerContactOnlyAddsTheRawDifferenceBeforePartArmor(GameTestHelper h){
        var core=core(h);var armor=core.modules().add("armor");armor.anchors(core.modules().all().getFirst().anchors());var rule=armor.armorRules().add();rule.edit("channel",core.wireless().add()+1);rule.edit("when",1);rule.edit("modifier",-50);
        var source=h.getLevel().damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel()));hit(core,source,20);hp(h,core,990);
        h.runAfterDelay(1,()->{hit(core,source,30);hp(h,core,985);});
        h.runAfterDelay(2,()->{hit(core,source,30);hp(h,core,985);h.succeed();});
    }
    @GameTest(template="combat",batch="hitbox_window_independence",timeoutTicks=30)
    public static void SeparateAttackersAndRapidProjectilesRemainIndependent(GameTestHelper h){
        var core=core(h);var a=EntityType.ZOMBIE.create(h.getLevel());var b=EntityType.ZOMBIE.create(h.getLevel());hit(core,h.getLevel().damageSources().mobAttack(a),14);
        h.runAfterDelay(1,()->{hit(core,h.getLevel().damageSources().mobAttack(b),14);hp(h,core,972);
            for(int i=0;i<2;i++){var arrow=new Arrow(h.getLevel(),0,0,0,new ItemStack(Items.ARROW),null);arrow.setOwner(a);hit(core,h.getLevel().damageSources().arrow(arrow,a),10);}hp(h,core,952);h.succeed();});
    }
    @GameTest(template="combat",batch="hitbox_window_bypass",timeoutTicks=30)
    public static void NativeBypassesCooldownDamageKeepsItsOwnCadence(GameTestHelper h){
        var core=core(h);var type=h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).listElements().filter(t->t.is(DamageTypeTags.BYPASSES_COOLDOWN)).findFirst().orElseThrow();
        var source=new DamageSource(type,EntityType.ZOMBIE.create(h.getLevel()));hit(core,source,14);
        h.runAfterDelay(1,()->{hit(core,source,14);hp(h,core,972);h.succeed();});
    }
    @GameTest(template="combat",batch="hitbox_window_override",timeoutTicks=30)
    public static void IncomingDamageEventCanDisableTheNativeHurtWindow(GameTestHelper h){
        var core=core(h);var source=h.getLevel().damageSources().mobAttack(EntityType.ZOMBIE.create(h.getLevel()));
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent> listener=e->{if(e.getEntity() instanceof EntityHitbox box&&box.core()==core)e.getContainer().setPostAttackInvulnerabilityTicks(0);};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        try{hit(core,source,14);}catch(Throwable e){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);throw e;}
        h.runAfterDelay(1,()->{try{hit(core,source,14);hp(h,core,972);h.succeed();}finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}});
    }
}
