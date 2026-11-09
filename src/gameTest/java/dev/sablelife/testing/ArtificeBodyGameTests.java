package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.combat.BodyDamageBridge;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.ModuleAnchor;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.List;
import java.util.function.Consumer;

/** Reflection keeps the optional mod out of discovery signatures and production dependencies. */
@GameTestHolder("sablelife_artifice_tests") @PrefixGameTestTemplate(false)
public final class ArtificeBodyGameTests {
    private static final String API="io.redspace.irons_artifice.";
    private static HeartBlockEntity core(GameTestHelper h){
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,6,3));
        l.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        var c=(HeartBlockEntity)l.getBlockEntity(body.getPlot().getCenterBlock());c.configure(1000,60);c.tryActivate(TestActors.creative(l));return c;
    }
    private static Projectile bullet(GameTestHelper h,double speed,int count)throws Exception{
        var id=ResourceLocation.fromNamespaceAndPath("irons_artifice","bullet");
        h.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(id),"Run with -PartificeTests=true and the pinned optional jars");
        var b=(Projectile)BuiltInRegistries.ENTITY_TYPE.get(id).create(h.getLevel());
        var profile=b.getClass().getMethod("getProfile").invoke(b);var map=profile.getClass().getMethod("components").invoke(profile);
        var type=Class.forName(API+"data.ComponentType");var value=Class.forName(API+"data.Value");var components=Class.forName(API+"data.ShotComponents");
        var set=map.getClass().getMethod("set",type,Object.class);
        set.invoke(map,components.getField("DAMAGE").get(null),value.getMethod("of",double.class).invoke(null,40d));
        set.invoke(map,components.getField("PROJECTILE_COUNT").get(null),value.getMethod("of",double.class).invoke(null,(double)count));
        b.setDeltaMovement(speed,0,0);return b;
    }
    private static void impact(Projectile b,HitResult hit)throws Exception{
        var method=b.getClass().getDeclaredMethod("onHit",HitResult.class);method.setAccessible(true);method.invoke(b,hit);
    }
    private static BlockHitResult hit(HeartBlockEntity c){return new BlockHitResult(c.getBlockPos().east().getCenter(),Direction.WEST,c.getBlockPos().east(),false);}
    @GameTest(template="combat",batch="artifice_accessor")
    public static void NativeResolverKeepsSpeedAndPelletScaling(GameTestHelper h)throws Exception{
        var b=bullet(h,2,4);h.assertTrue(BodyDamageBridge.projectileDamage(b,Vec3.ZERO)==10,"Native resolver was not found or pellet scaling changed");
        b.setDeltaMovement(1,0,0);h.assertTrue(BodyDamageBridge.projectileDamage(b,Vec3.ZERO)==5,"Current velocity was not resolved per hit");
        b.setDeltaMovement(12,0,0);h.assertTrue(BodyDamageBridge.projectileDamage(b,Vec3.ZERO)==10,"Native maximum speed scaling changed");h.succeed();
    }
    @GameTest(template="combat",batch="artifice_armor")
    public static void NativeImpactDamagesArmoredBodyOnlyOnce(GameTestHelper h)throws Exception{
        var c=core(h);var armor=c.modules().add("armor");armor.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().east(),Direction.UP)));ArmorTestSupport.set(c,armor,-50);
        var b=bullet(h,2,2);impact(b,hit(c));h.assertTrue(c.health().current()==990,"Native impact did not transfer 20 damage through 50% armor: "+c.health().current());
        impact(b,hit(c));h.assertTrue(c.health().current()==990,"Repeated impact was charged twice");h.succeed();
    }
    @GameTest(template="combat",batch="artifice_canceled")
    public static void CanceledNativeImpactDoesNotDamageCore(GameTestHelper h)throws Exception{
        var c=core(h);var b=bullet(h,2,1);
        Consumer<ProjectileImpactEvent> cancel=e->{if(e.getProjectile()==b)e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,ProjectileImpactEvent.class,cancel);
        try{impact(b,hit(c));h.assertTrue(c.health().current()==1000,"Canceled native impact still damaged the body");}finally{NeoForge.EVENT_BUS.unregister(cancel);}h.succeed();
    }
    @GameTest(template="combat",batch="artifice_dormant")
    public static void DormantCoreDoesNotReceiveNativeBulletDamage(GameTestHelper h)throws Exception{
        var c=core(h);c.resetForCreative(TestActors.creative(h.getLevel()));impact(bullet(h,2,1),hit(c));h.assertTrue(c.health().current()==1000,"Dormant core received damage");h.succeed();
    }
    @GameTest(template="combat",batch="artifice_flight",timeoutTicks=80)
    public static void NativeTickFindsTheMovingStructureInWorldSpace(GameTestHelper h)throws Exception{
        var c=core(h);var target=SableBodyProbe.toWorld(h.getLevel(),c.getBlockPos().east().getCenter());var b=bullet(h,2,1);
        b.setPos(target.add(4,0,0));b.setDeltaMovement(-2,0,0);b.setNoGravity(true);h.getLevel().addFreshEntity(b);
        h.runAfterDelay(6,()->{h.assertTrue(c.health().current()<1000,"Actual bullet flight never damaged the assembled body");b.discard();h.succeed();});
    }
}
