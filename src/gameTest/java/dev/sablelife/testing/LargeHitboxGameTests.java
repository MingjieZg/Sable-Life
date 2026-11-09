package dev.sablelife.testing;

import dev.sablelife.*;
import dev.sablelife.combat.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class LargeHitboxGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,48,4));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(1000,20);core.tryActivate(TestActors.creative(level));return core;
    }
    private static VirtualModule add(HeartBlockEntity core){
        var m=core.modules().add("entity_hitbox");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP)));m.edit("hitboxWidth",16,"");m.edit("hitboxHeight",16,"");m.edit("hitboxDepth",16,"");return m;
    }
    @GameTest(template="combat",batch="large_hitbox_bounds")
    public static void OversizedInputsClampAndMaximumShapeRemainsSixteen(GameTestHelper h){
        var core=fixture(h);var m=add(core);
        for(String key:List.of("hitboxWidth","hitboxHeight","hitboxDepth")){m.edit(key,1024,"");h.assertTrue(m.value(key)==16,"Oversized input escaped dimension bound");}
        core.modules().damageBoxes.tick(core);var box=core.modules().damageBoxes.entities().getFirst().getBoundingBox();
        h.assertTrue(box.getXsize()==16&&box.getYsize()==16&&box.getZsize()==16,"Maximum shape disagrees with dimensions");
        var restored=VirtualModule.load(m.save());h.assertTrue(restored.value("hitboxWidth")==16,"Maximum shape did not persist");h.succeed();
    }
    @GameTest(template="combat",batch="large_hitbox_edge_query")
    public static void MaximumBoxIsQueryableAtDistantEdgesAndCorners(GameTestHelper h){
        var core=fixture(h);add(core);core.modules().damageBoxes.tick(core);var receiver=core.modules().damageBoxes.entities().getFirst();var center=receiver.getBoundingBox().getCenter();
        for(double x:List.of(-7.9,0.,7.9))for(double y:List.of(-7.9,0.,7.9))for(double z:List.of(-7.9,0.,7.9)){
            var point=center.add(x,y,z);var found=h.getLevel().getEntitiesOfClass(EntityHitbox.class,new AABB(point,point).inflate(.02),e->e==receiver);
            h.assertTrue(found.size()==1,"Maximum receiver missing at edge/corner "+x+","+y+","+z);
            h.assertTrue(h.getLevel().getEntities((Entity)null,new AABB(point,point).inflate(.02),e->e==receiver).size()==1,"Untyped query missed maximum receiver");
        }
        var outside=center.add(8.1,0,0);h.assertTrue(h.getLevel().getEntitiesOfClass(EntityHitbox.class,new AABB(outside,outside).inflate(.02),e->e==receiver).isEmpty(),"Query hit beyond receiver boundary");h.succeed();
    }
    @GameTest(template="combat",batch="large_hitbox_query_lifecycle")
    public static void LargeQueryBoundsResizeMoveAndDiscardWithExactFiltering(GameTestHelper h)throws Exception{
        var core=fixture(h);var module=add(core);core.modules().damageBoxes.tick(core);var receiver=core.modules().damageBoxes.entities().getFirst();
        var field=EntityHitbox.class.getDeclaredField("queryBounds");field.setAccessible(true);var bounds=(HitboxQueryBounds)field.get(receiver);
        var countField=HitboxQueryBounds.class.getDeclaredField("largeReceivers");countField.setAccessible(true);int count=countField.getInt(bounds);
        h.assertTrue(count>0,"Large receiver was not registered with its section storage");
        for(String key:List.of("hitboxWidth","hitboxHeight","hitboxDepth"))module.edit(key,1,"");receiver.follow(module);
        h.assertTrue(countField.getInt(bounds)==count-1,"Shrink retained large-query expansion");
        for(String key:List.of("hitboxWidth","hitboxHeight","hitboxDepth"))module.edit(key,16,"");receiver.follow(module);
        h.assertTrue(countField.getInt(bounds)==count,"Growth failed to restore query expansion");
        receiver.setPos(receiver.getX(),receiver.getY()+16,receiver.getZ());
        h.assertTrue(countField.getInt(bounds)==count,"Section transfer lost or duplicated large-query registration");
        var query=new AABB(receiver.getBoundingBox().getCenter(),receiver.getBoundingBox().getCenter()).inflate(20);var selected=new ArrayList<EntityHitbox>();
        h.getLevel().getEntities(EntityTypeTest.forClass(EntityHitbox.class),query,e->e==receiver,selected,1);
        h.assertTrue(selected.size()==1&&selected.getFirst()==receiver,"Limited typed query duplicated or omitted receiver");
        h.assertTrue(h.getLevel().getEntities((Entity)receiver,query,e->e==receiver).isEmpty(),"Query ignored excluded entity");
        h.assertTrue(h.getLevel().getEntitiesOfClass(EntityHitbox.class,query,e->false).isEmpty(),"Query ignored predicate");
        receiver.discard();h.assertTrue(countField.getInt(bounds)==count-1,"Discard retained large-query expansion");h.succeed();
    }
    @GameTest(template="combat",batch="large_hitbox_overlap")
    public static void ThirtyTwoOverlappingMaximumBoxesDeduplicateAndRelease(GameTestHelper h){
        var setting=(ModConfigSpec.IntValue)SableLifeServerConfig.SPEC.getValues().get("maximumEntityHitboxesPerCreature");int previous=setting.get();
        try{
            setting.set(32);var core=fixture(h);for(int i=0;i<40;i++)add(core);core.modules().damageBoxes.tick(core);var boxes=core.modules().damageBoxes.entities();
            h.assertTrue(boxes.size()==32,"Server maximum receiver count was not enforced");var source=h.getLevel().damageSources().generic();
            for(var box:boxes)box.hurt(source,10);core.modules().damageBoxes.flush(core);
            h.assertTrue(core.health().current()==990,"Overlapping receivers multiplied one damage source");
            core.resetForCreative(TestActors.creative(h.getLevel()));h.assertTrue(boxes.stream().allMatch(e->e.isRemoved())&&core.modules().damageBoxes.entities().isEmpty(),"Release left maximum receivers alive");
        }finally{setting.set(previous);}
        h.succeed();
    }
}
