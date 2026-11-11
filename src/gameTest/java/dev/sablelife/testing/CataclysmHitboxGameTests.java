package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.ownership.LoadedCoreIndex;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

/** Optional runtime fixture: real Cataclysm attacks, ordinary mob as a cadence control. */
@GameTestHolder("sablelife_cataclysm_tests")
@PrefixGameTestTemplate(false)
public final class CataclysmHitboxGameTests {
    private static HeartBlockEntity core(GameTestHelper h,int count){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,40,4));
        level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(1000,100);core.tryActivate(TestActors.creative(level));LoadedCoreIndex.observe(core);
        for(int i=0;i<count;i++){var module=core.modules().add("entity_hitbox");module.edit("hitboxOffsetY",4,"");module.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP)));}
        core.modules().damageBoxes.tick(core);return core;
    }
    private static void compare(GameTestHelper h,int receivers,int attacks,boolean wave) throws Exception {
        h.assertTrue(net.neoforged.fml.ModList.get().isLoaded("cataclysm"),"This fixture requires the real Cataclysm runtime");
        var core=core(h,receivers);var center=core.modules().damageBoxes.entities().getFirst().getBoundingBox().getCenter();
        var pig=EntityType.PIG.create(h.getLevel());pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);pig.setHealth(1000);pig.setNoAi(true);pig.setNoGravity(true);
        var controlPoint=center.add(.25,-.25,0);pig.setPos(controlPoint);h.getLevel().addFreshEntity(pig);
        var caster=TestActors.creative(h.getLevel());caster.setPos(center.add(20,0,0));
        var entities=new ArrayList<Entity>();
        for(int i=0;i<attacks;i++){
            if(wave){
                var type=Class.forName("com.github.L_Ender.cataclysm.entity.effect.Wave_Entity");
                var entity=(Entity)type.getConstructor(Level.class,LivingEntity.class,int.class,float.class).newInstance(h.getLevel(),caster,100,14F);
                entity.setPos(center.x,center.y-.5,center.z);entities.add(entity);
            }else{
                var type=Class.forName("com.github.L_Ender.cataclysm.entity.projectile.Phantom_Halberd_Entity");
                entities.add((Entity)type.getConstructor(Level.class,double.class,double.class,double.class,float.class,int.class,LivingEntity.class,float.class)
                    .newInstance(h.getLevel(),center.x,center.y-.5,center.z,0F,-12,caster,14F));
            }
        }
        for(int i=1;i<=21;i++){h.runAfterDelay(i,()->{
            var current=core.modules().damageBoxes.entities().getFirst().getBoundingBox().getCenter();
            pig.setPos(current.add(.25,-.25,0));pig.setDeltaMovement(Vec3.ZERO);
            for(var entity:entities){
                entity.setPos(current.x,current.y-.5,current.z);
                if(wave){try{var method=entity.getClass().getDeclaredMethod("attackEntities",double.class,double.class,double.class);method.setAccessible(true);method.invoke(entity,.5,1.,0.);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
                else entity.tick();
            }
        });}
        h.runAfterDelay(23,()->{
            core.modules().damageBoxes.flush(core);float lost=1000-core.health().current(),control=1000-pig.getHealth();
            System.out.println("CATACLYSM_DAMAGE_CONTROL: wave="+wave+" receivers="+receivers+" attacks="+attacks+" lifeLoss="+lost+" mobLoss="+control);
            h.assertTrue(control>0,"Native skill did not reach the ordinary mob control");
            h.assertTrue(Math.abs(lost-control)<.001,"Receiver damage differs from native ordinary-mob cadence: life="+lost+", mob="+control);
            entities.forEach(Entity::discard);pig.discard();h.succeed();
        });
    }
    @GameTest(template="combat",batch="cataclysm_single_halberd",timeoutTicks=60)
    public static void SingleHalberdMatchesOrdinaryMob(GameTestHelper h) throws Exception {compare(h,1,1,false);}
    @GameTest(template="combat",batch="cataclysm_overlapping_halberds",timeoutTicks=60)
    public static void OverlappingHalberdsAndFiveBoxesMatchOrdinaryMob(GameTestHelper h) throws Exception {compare(h,5,3,false);}
    @GameTest(template="combat",batch="cataclysm_wave",timeoutTicks=60)
    public static void NativeWaveAndFiveBoxesMatchOrdinaryMob(GameTestHelper h) throws Exception {compare(h,5,1,true);}
}
