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
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("sablelife_hitbox_mob_geometry_tests")
@PrefixGameTestTemplate(false)
public final class HitboxMobGeometryGameTests {
    private record Fixture(HeartBlockEntity core, EntityHitbox box) {}
    private static HeartBlockEntity previous;
    private static final java.util.Set<net.minecraft.world.level.ChunkPos> forced=new java.util.HashSet<>();

    private static Fixture fixture(GameTestHelper h, double width, double height) {
        var level=h.getLevel();
        if(previous!=null&&previous.getLevel()==level&&!previous.isRemoved())previous.resetForCreative(TestActors.creative(level));
        for(var chunk:forced)level.setChunkForced(chunk.x,chunk.z,false);forced.clear();
        var p=h.absolutePos(new BlockPos(4,40,4));
        // Contact points extend outside the tiny GameTest template's ticking chunks.
        for(int x=(p.getX()-17)>>4;x<=(p.getX()+17)>>4;x++)for(int z=(p.getZ()-17)>>4;z<=(p.getZ()+17)>>4;z++) {
            var chunk=new net.minecraft.world.level.ChunkPos(x,z);forced.add(chunk);level.setChunkForced(x,z,true);
        }
        // The GameTest grid is narrower than a maximum receiver and its terrain pad.
        // Retire the previous opt-in receiver and remove its roof/floor before this fixture.
        for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++)for(int y=-4;y<=20;y++) {
            var at=p.offset(x,y,z);if(!level.getBlockState(at).isAir())level.setBlock(at,Blocks.AIR.defaultBlockState(),2);
        }
        level.setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());
        level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        var c=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
        c.configure(1000,100);c.tryActivate(TestActors.creative(level));LoadedCoreIndex.observe(c);previous=c;
        var hover=c.modules().add("hover_anchor");hover.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));hover.edit("hoverRotation",1,"");
        var m=c.modules().add("entity_hitbox");m.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos().east(),Direction.UP)));
        m.edit("hitboxWidth",width,"");m.edit("hitboxDepth",width,"");m.edit("hitboxHeight",height,"");m.edit("hitboxOffsetY",8,"");m.edit("hitboxProvokeMobs",1,"");
        c.modules().damageBoxes.tick(c);var box=c.modules().damageBoxes.entities().getFirst();
        var center=box.getBoundingBox().getCenter();int floor=(int)Math.floor(box.getBoundingBox().minY)-1;
        for(int x=-15;x<=15;x++)for(int z=-15;z<=15;z++)level.setBlockAndUpdate(BlockPos.containing(center.x+x,floor,center.z+z),Blocks.STONE.defaultBlockState());
        return new Fixture(c,box);
    }

    private static Zombie zombie(GameTestHelper h, Vec3 p, boolean ai) {
        var z=EntityType.ZOMBIE.create(h.getLevel());z.setPos(p);z.setNoAi(!ai);z.setOnGround(true);z.tickCount=40;h.getLevel().addFreshEntity(z);return z;
    }
    private static Vec3 floorPoint(EntityHitbox box,double x,double z) {
        var b=box.getBoundingBox();return new Vec3(b.getCenter().x+x,Math.floor(b.minY),b.getCenter().z+z);
    }
    private static void roof(GameTestHelper h,EntityHitbox box) {
        var b=box.getBoundingBox();int y=(int)Math.floor(b.minY)+3;
        for(int x=(int)Math.floor(b.minX);x<=Math.ceil(b.maxX);x++)for(int z=(int)Math.floor(b.minZ);z<=Math.ceil(b.maxZ);z++)h.getLevel().setBlockAndUpdate(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState());
    }

    @GameTest(template="combat",batch="receiver_edge_sight")
    public static void LargeReceiverVisibleAtReachableLowerEdgeDespiteCoveredCenter(GameTestHelper h) {
        var f=fixture(h,16,12);roof(h,f.box);var z=zombie(h,floorPoint(f.box,6,0),false);
        h.assertTrue(z.isWithinMeleeAttackRange(f.box),"Fixture is not in native melee reach");
        h.assertTrue(z.hasLineOfSight(f.box),"Reachable lower receiver edge was hidden by the roof over its center");z.discard();h.succeed();
    }
    @GameTest(template="combat",batch="receiver_edge_path")
    public static void GroundNavigationUsesNearestReceiverEdgeRatherThanCenter(GameTestHelper h) {
        var f=fixture(h,16,12);var z=zombie(h,floorPoint(f.box,12,0),false);z.setTarget(f.box);
        var path=z.getNavigation().createPath(f.box,0);h.assertTrue(path!=null,"No path to open receiver edge");
        var target=path.getTarget();h.assertTrue(target.getX()>=Math.floor(f.box.getBoundingBox().maxX)-1,"Path still points at receiver center: "+target);
        var waypoint=BlockPos.containing(f.box.getBoundingBox().getCenter().add(2,-6,4));
        var unrelated=z.getNavigation().createPath(waypoint,0);
        h.assertTrue(unrelated!=null&&unrelated.getTarget().getX()==waypoint.getX()&&unrelated.getTarget().getZ()==waypoint.getZ(),"Unrelated waypoint was redirected to receiver");
        z.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=140,batch="receiver_edge_native")
    public static void NativeZombieAttacksLargeReceiverUnderRoofAtItsEdge(GameTestHelper h) {
        var f=fixture(h,16,12);roof(h,f.box);var z=zombie(h,floorPoint(f.box,6,0),true);
        h.runAfterDelay(100,()->{h.assertTrue(z.getTarget()==f.box,"Proactive zombie never acquired receiver");h.assertTrue(f.core.health().current()<1000,"Zombie followed receiver but never damaged core at lower edge");h.assertTrue(z.getX()>f.box.getBoundingBox().getCenter().x+4,"Zombie still crowded the receiver center instead of attacking at its nearby edge: "+describe(z,f.box));z.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="receiver_wall")
    public static void WallBetweenMobAndEntireReceiverStillBlocksSight(GameTestHelper h) {
        var f=fixture(h,1,2);var z=zombie(h,floorPoint(f.box,-5,0),false);var center=f.box.getBoundingBox().getCenter();
        int wall=(int)Math.floor(center.x)-2;
        for(int y=(int)Math.floor(f.box.getBoundingBox().minY)-1;y<=Math.ceil(f.box.getBoundingBox().maxY)+2;y++)for(int dz=-5;dz<=5;dz++)h.getLevel().setBlockAndUpdate(new BlockPos(wall,y,(int)Math.floor(center.z)+dz),Blocks.STONE.defaultBlockState());
        h.assertTrue(!z.hasLineOfSight(f.box),"Receiver sight bypassed an intervening wall");z.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=140,batch="receiver_floating")
    public static void ReceiverAboveNativeMeleeReachDoesNotTakeRemoteDamage(GameTestHelper h) {
        var f=fixture(h,12,2);var z=zombie(h,floorPoint(f.box,3,0).add(0,-5,0),true);z.setNoGravity(true);z.setTarget(f.box);
        h.assertTrue(!z.isWithinMeleeAttackRange(f.box),"Floating fixture unexpectedly within melee reach");
        h.runAfterDelay(90,()->{h.assertTrue(f.core.health().current()==1000,"Mob damaged a receiver outside native vertical reach");z.discard();h.succeed();});
    }
    @GameTest(template="combat",timeoutTicks=100,batch="receiver_edge_follow_range")
    public static void AttractionRangeMeasuresReceiverEdgeRatherThanDistantCenter(GameTestHelper h) {
        var f=fixture(h,16,12);var z=zombie(h,floorPoint(f.box,10,0),true);
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(4);
        h.assertTrue(z.distanceToSqr(f.box)>16,"Fixture center unexpectedly within follow range");
        h.runAfterDelay(50,()->{h.assertTrue(z.getTarget()==f.box,"Nearby receiver edge was rejected because center exceeded follow range");z.discard();h.succeed();});
    }
    @GameTest(template="combat",batch="receiver_normal_mob_sight")
    public static void OrdinaryMobSightStillUsesItsNormalBlockedTargetRay(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,20,4));
        var z=zombie(h,pos.getCenter(),false);var cow=EntityType.COW.create(level);cow.setPos(pos.east(5).getCenter());cow.setNoAi(true);level.addFreshEntity(cow);
        h.assertTrue(z.hasLineOfSight(cow),"Ordinary open target ray was changed");
        for(int dy=0;dy<4;dy++)for(int dz=-3;dz<=3;dz++)level.setBlockAndUpdate(pos.offset(2,dy,dz),Blocks.STONE.defaultBlockState());
        h.assertTrue(!z.hasLineOfSight(cow),"Ordinary target ray bypassed wall");z.discard();cow.discard();h.succeed();
    }
    @GameTest(template="combat",timeoutTicks=140,batch="receiver_outer_faces")
    public static void FourZombiesTouchingDifferentOuterFacesAllDamageCore(GameTestHelper h) {
        var f=fixture(h,16,12);roof(h,f.box);
        var mobs=List.of(zombie(h,floorPoint(f.box,8.25,0),true),zombie(h,floorPoint(f.box,-8.25,0),true),zombie(h,floorPoint(f.box,0,8.25),true),zombie(h,floorPoint(f.box,0,-8.25),true));
        var attackers=new java.util.HashSet<java.util.UUID>();
        java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> listener=e->{
            if(e.getEntity()==f.box&&e.getNewDamage()>0&&e.getSource().getEntity() instanceof Zombie z&&mobs.contains(z))attackers.add(z.getUUID());
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
        h.runAfterDelay(90,()->{
            try {
                h.assertTrue(attackers.size()==4,"Not all outer faces accepted native melee damage: "+attackers.size()+"/4 "+mobs.stream().map(z->"hit="+attackers.contains(z.getUUID())+" "+describe(z,f.box)).toList());
                for(var z:mobs)h.assertTrue(z.position().subtract(f.box.getBoundingBox().getCenter()).horizontalDistanceSqr()>16,"An outer-face attacker still walked into the center");
                h.succeed();
            } finally {
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);for(var z:mobs)z.discard();
            }
        });
    }
    private static String describe(Zombie z,EntityHitbox box){return "mob="+z.position()+" box="+box.getBoundingBox()+" target="+(z.getTarget()==box)+" melee="+z.isWithinMeleeAttackRange(box)+" sight="+z.hasLineOfSight(box)+" path="+z.getNavigation().getTargetPos();}
}
