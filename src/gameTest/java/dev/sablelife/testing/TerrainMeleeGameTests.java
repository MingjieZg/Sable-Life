package dev.sablelife.testing;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.attack.ContactBox;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

@GameTestHolder("sablelife_terrain_melee_tests") @PrefixGameTestTemplate(false)
public final class TerrainMeleeGameTests {
    record Rig(HeartBlockEntity core, VirtualModule melee, BlockPos origin) {
        void tick(){core.modules().tick(core);}
        BlockPos target(){return BlockPos.containing(MeleeGeometry.frame(core,melee).center());}
    }
    static Rig fixture(GameTestHelper h,boolean survival){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(8,50,8));
        level.setBlockAndUpdate(p,(survival?SableLife.SURVIVAL_LIFE_CORE.get():SableLife.HEART.get()).defaultBlockState());level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());var author=TestActors.creative(level);core.configure(100,0);
        for(var floor:BlockPos.betweenClosed(p.offset(-2,-1,-2),p.offset(3,-1,2)))level.setBlockAndUpdate(floor,Blocks.STONE.defaultBlockState());
        var melee=core.modules().add("melee");var a=ModuleAnchor.create(core,core.getBlockPos().east(),Direction.EAST);melee.anchors(List.of(a,a));melee.edit("startDistance",4,"");melee.edit("damage",3,"");melee.edit("knockback",0,"");melee.edit("interval",3,"");
        if(survival)core.depositSurvivalTokens(author,new ItemStack(Items.DIAMOND,64));
        core.tryActivate(author);h.assertTrue(core.health().state()==HeartState.ALIVE,"Core did not activate");return new Rig(core,melee,p);
    }
    static void on(Rig r){r.melee.edit("meleeBreakBlocks",1,"");}
    static void stone(GameTestHelper h,BlockPos p){h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());}
    static void done(GameTestHelper h,Rig r){r.melee.edit("enabled",0,"");h.succeed();}

    @GameTest(template="combat",batch="decor_contact")
    public static void CoreContactsBreakOutlineOnlyPlants(GameTestHelper h){var r=fixture(h,false);r.melee.edit("enabled",0,"");r.core.terrainSettings().edit("crushEnabled",1,"");var p=r.origin.east(2);
        for(var block:List.of(Blocks.SHORT_GRASS,Blocks.FERN,Blocks.DANDELION,Blocks.POPPY,Blocks.CORNFLOWER,Blocks.DEAD_BUSH,Blocks.BROWN_MUSHROOM,Blocks.RED_MUSHROOM)){
            h.getLevel().setBlockAndUpdate(p.below(),Blocks.DIRT.defaultBlockState());h.getLevel().setBlockAndUpdate(p,block.defaultBlockState());
            r.core.terrainBreaker().advance(r.core,LinearMovementGeometry.capture(r.core),new Vec3(1,0,0));h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Outline plant remained: "+block);
        }h.assertTrue(h.getLevel().getBlockState(r.origin.below()).is(Blocks.STONE),"Plant contact destroyed supporting floor");done(h,r);}

    @GameTest(template="combat",batch="decor_tall")
    public static void TallGrassBothHalvesClear(GameTestHelper h){var r=fixture(h,false);r.melee.edit("enabled",0,"");r.core.terrainSettings().edit("crushEnabled",1,"");var p=r.origin.east(2);h.getLevel().setBlockAndUpdate(p.below(),Blocks.DIRT.defaultBlockState());h.getLevel().setBlockAndUpdate(p,Blocks.TALL_GRASS.defaultBlockState());h.getLevel().setBlockAndUpdate(p.above(),Blocks.TALL_GRASS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));r.core.terrainBreaker().advance(r.core,LinearMovementGeometry.capture(r.core),new Vec3(1,0,0));h.assertTrue(h.getLevel().getBlockState(p).isAir()&&h.getLevel().getBlockState(p.above()).isAir(),"Tall plant halves remained");done(h,r);}

    @GameTest(template="combat",batch="decor_floor")
    public static void NonCollidingPlantIsNotProtectedAsSolidSupport(GameTestHelper h){var r=fixture(h,false);r.melee.edit("enabled",0,"");r.core.terrainSettings().edit("crushEnabled",1,"");var p=r.origin.below();h.getLevel().setBlockAndUpdate(p.below(),Blocks.DIRT.defaultBlockState());h.getLevel().setBlockAndUpdate(p,Blocks.TALL_GRASS.defaultBlockState());r.core.terrainBreaker().advance(r.core,LinearMovementGeometry.capture(r.core),Vec3.ZERO);h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Outline-only contact was treated as solid support");done(h,r);}

    @GameTest(template="combat",batch="decor_fluid")
    public static void CoreDoesNotRemoveWaterOrLava(GameTestHelper h){var r=fixture(h,false);r.melee.edit("enabled",0,"");r.core.terrainSettings().edit("crushEnabled",1,"");var p=r.origin.east(2);for(var fluid:List.of(Blocks.WATER,Blocks.LAVA)){h.getLevel().setBlockAndUpdate(p,fluid.defaultBlockState());r.core.terrainBreaker().advance(r.core,LinearMovementGeometry.capture(r.core),new Vec3(1,0,0));h.assertTrue(h.getLevel().getBlockState(p).is(fluid),"Fluid removed by outline fallback");}done(h,r);}

    @GameTest(template="combat",batch="decor_protected")
    public static void CollisionRespectsFlowerTagAndMergedPreset(GameTestHelper h){var r=fixture(h,false);r.melee.edit("enabled",0,"");r.core.terrainSettings().edit("crushEnabled",1,"");r.core.terrainSettings().edit("crushProtectPreset",0,"");var p=r.origin.east(2);h.getLevel().setBlockAndUpdate(p.below(),Blocks.DIRT.defaultBlockState());h.getLevel().setBlockAndUpdate(p,Blocks.DANDELION.defaultBlockState());r.core.terrainBreaker().advance(r.core,LinearMovementGeometry.capture(r.core),new Vec3(1,0,0));h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.DANDELION),"Preset flower tag was ignored");done(h,r);}

    @GameTest(template="combat",batch="terrain_legacy")
    public static void ExistingProtectedListAndLegacyMeleeDefaultStayUnchanged(GameTestHelper h){var config=new TerrainBreakSettings();config.edit("crushProtectAdd",0,"minecraft:oak_planks");var original=config.save();var copy=new TerrainBreakSettings();copy.load(original);h.assertTrue(copy.entries().equals(List.of("minecraft:oak_planks"))&&copy.save().equals(original),"Upgrade silently added vegetation to a custom list");var r=fixture(h,false);var saved=r.melee.save();saved.getCompound("Settings").remove("meleeBreakBlocks");var old=VirtualModule.load(saved);h.assertTrue(old.value("meleeBreakBlocks")==0&&old.anchors().equals(r.melee.anchors()),"Legacy module gained destruction or lost marks");done(h,r);}

    @GameTest(template="combat",batch="melee_blocks_off")
    public static void DefaultOffNeverBreaksTerrain(GameTestHelper h){var r=fixture(h,false);var p=r.target();stone(h,p);r.tick();h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.STONE),"Default melee destroyed terrain");h.assertTrue(!r.melee.edit("meleeBreakBlocks",2,"")&&!r.melee.edit("meleeBreakBlocks",Double.NaN,""),"Invalid flag accepted");done(h,r);}

    @GameTest(template="combat",batch="melee_blocks_on")
    public static void MeleeBreaksTerrainWithoutCoreCollisionMaster(GameTestHelper h){var r=fixture(h,false);on(r);h.assertTrue(!r.core.terrainSettings().enabled(),"Collision breaking was implicitly enabled");var p=r.target();stone(h,p);r.tick();h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Active melee did not break target block");done(h,r);}

    @GameTest(template="combat",batch="melee_flower")
    public static void MeleeBreaksGrassAndFlowers(GameTestHelper h){var r=fixture(h,false);on(r);var p=r.target();for(var block:List.of(Blocks.SHORT_GRASS,Blocks.FERN,Blocks.DANDELION,Blocks.BROWN_MUSHROOM)){h.getLevel().setBlockAndUpdate(p.below(),Blocks.DIRT.defaultBlockState());h.getLevel().setBlockAndUpdate(p,block.defaultBlockState());r.tick();h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Melee missed outline decoration "+block);}done(h,r);}

    @GameTest(template="combat",batch="melee_protected")
    public static void MeleeRespectsOwnProtectedListAndBedrock(GameTestHelper h){var r=fixture(h,false);on(r);var p=r.target();r.melee.meleeProtection().edit("crushProtectAdd",0,"minecraft:stone");stone(h,p);r.tick();h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.STONE),"Protected block removed");h.getLevel().setBlockAndUpdate(p,Blocks.BEDROCK.defaultBlockState());r.tick();h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.BEDROCK),"Unbreakable block removed");done(h,r);}

    @GameTest(template="combat",batch="melee_water")
    public static void MeleePreservesFluidAndOtherSableBody(GameTestHelper h){var r=fixture(h,false);on(r);var p=r.target();h.getLevel().setBlockAndUpdate(p,Blocks.WATER.defaultBlockState());r.tick();h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.WATER),"Melee erased water");stone(h,p);var other=SubLevelAssemblyHelper.assembleBlocks(h.getLevel(),p,List.of(p),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ()));r.tick();h.assertTrue(!other.isRemoved()&&h.getLevel().getBlockState(other.getPlot().getCenterBlock()).is(Blocks.STONE),"Melee dismantled a Sable target plot");done(h,r);}

    @GameTest(template="combat",batch="melee_rotated")
    public static void RotatedBoxKeepsBoundingCornerOutsideAttack(GameTestHelper h){var r=fixture(h,false);on(r);r.melee.edit("meleeLength",6,"");r.melee.edit("meleeHorizontal",45,"");var f=MeleeGeometry.frame(r.core,r.melee);BlockPos yes=null,no=null;var area=new AABB(f.center(),f.center()).inflate(f.half().length());for(var p:BlockPos.betweenClosed(BlockPos.containing(area.minX,area.minY,area.minZ),BlockPos.containing(area.maxX,area.maxY,area.maxZ))){if(p.getY()!=r.origin.getY())continue;if(ContactBox.intersects(f.center(),f.half(),f.rotation(),new AABB(p)))yes=p.immutable();else if(p.getCenter().distanceTo(f.center())<3)no=p.immutable();}h.assertTrue(yes!=null&&no!=null,"Rotated fixture did not expose an outside corner");var outside=no;stone(h,yes);stone(h,no);r.tick();h.assertTrue(h.getLevel().getBlockState(yes).isAir()&&h.getLevel().getBlockState(outside).is(Blocks.STONE),"Rotated attack used bounding box instead of volume");done(h,r);}

    @GameTest(template="combat",batch="melee_sweep",timeoutTicks=35)
    public static void TranslatingMeleeClearsIntermediateBlock(GameTestHelper h){var r=fixture(h,false);on(r);var initial=MeleeGeometry.frame(r.core,r.melee);var p=BlockPos.containing(initial.center().add(2,0,0));stone(h,p);r.tick();h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.STONE),"Sweep target started in endpoint");h.runAfterDelay(1,()->{var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)r.melee.anchors().getFirst().body(r.core);var handle=dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);handle.teleport(new Vector3d(initial.position().x+4,initial.position().y,initial.position().z),initial.bodyRotation());r.tick();h.assertTrue(h.getLevel().getBlockState(p).isAir(),"Intermediate block survived ordinary sweep");done(h,r);});}

    @GameTest(template="combat",batch="melee_teleport",timeoutTicks=35)
    public static void TeleportDoesNotCutBridgeBetweenOldAndNewBoxes(GameTestHelper h){var r=fixture(h,false);on(r);var initial=MeleeGeometry.frame(r.core,r.melee);var p=BlockPos.containing(initial.center().add(10,0,0));stone(h,p);r.tick();h.runAfterDelay(1,()->{var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)r.melee.anchors().getFirst().body(r.core);dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body).teleport(new Vector3d(initial.position().x+20,initial.position().y,initial.position().z),initial.bodyRotation());r.tick();h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.STONE),"Teleport cut remote bridge");done(h,r);});}

    @GameTest(template="combat",batch="melee_off_release")
    public static void DisabledAndReleasedModulesCannotClearTerrain(GameTestHelper h){var r=fixture(h,false);on(r);var p=r.target();stone(h,p);r.melee.edit("enabled",0,"");r.tick();h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.STONE),"Disabled module destroyed terrain");r.melee.edit("enabled",1,"");r.core.resetForCreative(TestActors.creative(h.getLevel()));r.tick();h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.STONE),"Released module destroyed terrain");done(h,r);}

    @GameTest(template="combat",batch="melee_drops")
    public static void MeleeUsesIndependentDropPreference(GameTestHelper h){var r=fixture(h,false);on(r);var p=r.target();var area=new AABB(p).inflate(2);h.getLevel().setBlockAndUpdate(p,Blocks.DIRT.defaultBlockState());r.tick();h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,area).isEmpty(),"No-drop setting spawned dirt");r.melee.edit("meleeDrops",1,"");h.getLevel().setBlockAndUpdate(p,Blocks.DIRT.defaultBlockState());r.tick();h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,area).stream().anyMatch(e->e.getItem().is(Items.DIRT)),"Drop setting ignored");done(h,r);}

    @GameTest(template="combat",batch="melee_budget")
    public static void RepeatedTicksInSameGameTickShareCoreBreakCap(GameTestHelper h){var r=fixture(h,false);on(r);r.melee.edit("startDistance",20,"");for(var k:List.of("meleeLength","meleeWidth","meleeHeight"))r.melee.edit(k,6,"");var center=r.target();var blocks=new ArrayList<BlockPos>();for(var p:BlockPos.betweenClosed(center.offset(-3,-3,-3),center.offset(3,3,3))){stone(h,p);blocks.add(p.immutable());}r.tick();r.tick();long cleared=blocks.stream().filter(p->h.getLevel().getBlockState(p).isAir()).count();h.assertTrue(cleared==64,"Shared core break cap failed: "+cleared);done(h,r);}

    @GameTest(template="combat",batch="melee_survival")
    public static void SurvivalMeleeTerrainPaysEnergyAndStopsWithoutIt(GameTestHelper h){var r=fixture(h,true);on(r);var p=r.target();stone(h,p);long before=r.core.survivalTokenBalance();long cost=SurvivalTokens.meleeCost(r.melee,dev.sablelife.SableLifeServerConfig.meleeTokenRate());h.assertTrue(before>=cost&&cost>0,"Survival fixture lacks energy/cost");r.tick();h.assertTrue(h.getLevel().getBlockState(p).isAir()&&r.core.survivalTokenBalance()==before-cost,"Terrain did not settle one melee energy charge");r.core.spendAttackTokens(r.core.survivalTokenBalance());stone(h,p);r.tick();h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.STONE),"Empty energy still broke terrain");done(h,r);}

    @GameTest(template="combat",batch="melee_codec")
    public static void ActualCodecMenusAndPortableSettingsPreserveFlag(GameTestHelper h){var r=fixture(h,false);var level=h.getLevel();var author=TestActors.creative(level);author.setPos(ModuleArena.corePosition(r.core));r.core.resetForCreative(author);var menu=new HeartDashboardMenu(4,author.getInventory(),r.core);var action=new ModuleNetworking.Action(4,r.core.modules().revision(),r.melee.id(),"edit","meleeBreakBlocks",1,"");var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),level.registryAccess());ModuleNetworking.Action.CODEC.encode(buffer,action);var decoded=ModuleNetworking.Action.CODEC.decode(buffer);buffer.release();h.assertTrue(decoded.equals(action)&&menu.action(author,decoded.revision(),decoded.module(),decoded.operation(),decoded.key(),decoded.value(),decoded.text()),"New flag could not pass normal codec/menu");h.assertTrue(r.melee.value("meleeBreakBlocks")==1,"Flag not delivered");h.assertTrue(!menu.action(author,action.revision(),r.melee.id(),"edit","meleeBreakBlocks",0,""),"Stale revision changed flag");var survival=TestActors.creative(level);survival.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);survival.setPos(author.position());h.assertTrue(!menu.action(survival,r.core.modules().revision(),r.melee.id(),"edit","meleeBreakBlocks",0,""),"Unauthorized player edited flag");var copy=VirtualModule.load(r.melee.save());var preset=ModulePreset.create(r.melee.save());h.assertTrue(copy.value("meleeBreakBlocks")==1&&copy.anchors().equals(r.melee.anchors())&&preset.value("meleeBreakBlocks")==1,"Flag lost on save/preset");h.assertTrue(!r.melee.edit("meleeBreakBlocks",-1,"")&&!r.melee.edit("meleeBreakBlocks",1.5,""),"Nonboolean values accepted");done(h,r);}

    @GameTest(template="combat",batch="melee_blueprint")
    public static void ActualBlueprintRemapsMarksAndPreservesTerrainFlag(GameTestHelper h)throws Exception{var r=fixture(h,false);on(r);r.core.terrainSettings().edit("crushProtectAdd",0,"#minecraft:flowers");r.melee.meleeProtection().edit("crushProtectAdd",0,"minecraft:bricks");var body=Sable.HELPER.getContaining(h.getLevel(),r.core.getBlockPos());var blueprint=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(h.getLevel(),Vec3.ZERO,List.of(body.getUniqueId()));String name="melee terrain "+UUID.randomUUID();var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(h.getLevel().getServer(),name);java.nio.file.Files.createDirectories(path.getParent());net.minecraft.nbt.NbtIo.writeCompressed(blueprint.save(),path);
        try{var at=h.absoluteVec(new Vec3(45,70,45));var server=h.getLevel().getServer();h.getLevel().getChunk(BlockPos.containing(at));int result=server.getCommands().getDispatcher().execute("sablelife blueprint spawn \""+name+"\" false "+at.x+" "+at.y+" "+at.z,server.createCommandSourceStack().withLevel(h.getLevel()));h.assertTrue(result==1,"Blueprint command rejected");dev.sablelife.ownership.CommandBlueprintSpawns.get(server).advance(server);var child=dev.sablelife.ownership.LoadedCoreIndex.all(h.getLevel()).stream().filter(v->v!=r.core&&ModuleArena.corePosition(v).distanceToSqr(at)<16).findFirst().orElseThrow();var module=child.modules().all().stream().filter(m->m.type().equals("melee")).findFirst().orElseThrow();h.assertTrue(module.value("meleeBreakBlocks")==1&&module.anchors().size()==2&&module.anchors().stream().allMatch(a->a.status(child).equals("valid"))&&!module.anchors().getFirst().bodyId().equals(body.getUniqueId()),"Blueprint lost terrain flag or kept source marks");h.assertTrue(child.terrainSettings().protects(Blocks.DANDELION.defaultBlockState()),"Blueprint lost protected flower tag");h.assertTrue(module.meleeProtection(child).protects(Blocks.BRICKS.defaultBlockState())&&!child.terrainSettings().protects(Blocks.BRICKS.defaultBlockState())&&!module.meleeProtection().protects(Blocks.DANDELION.defaultBlockState()),"Native blueprint merged or lost independent Melee protection");}finally{java.nio.file.Files.deleteIfExists(path);}done(h,r);}

    @GameTest(template="combat",batch="melee_arc_terrain",timeoutTicks=35)
    public static void RotatingOffsetBoxCutsArcInsteadOfChord(GameTestHelper h){var r=fixture(h,false);on(r);r.melee.edit("startDistance",16,"");var first=MeleeGeometry.frame(r.core,r.melee);var q=new Quaterniond().rotationY(Math.PI/2);var last=new MeleeGeometry.Frame(first.position(),q,first.localCenter(),first.angleRotation(),first.half(),first.signature());var arc=BlockPos.containing(MeleeGeometry.sample(first,last,.5).center());var chord=BlockPos.containing(first.center().lerp(last.center(),.5));stone(h,arc);stone(h,chord);r.tick();h.assertTrue(h.getLevel().getBlockState(arc).is(Blocks.STONE),"Arc target started at endpoint");h.runAfterDelay(1,()->{var body=(dev.ryanhcode.sable.sublevel.ServerSubLevel)r.melee.anchors().getFirst().body(r.core);dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body).teleport(new Vector3d(first.position().x,first.position().y,first.position().z),q);r.tick();h.assertTrue(h.getLevel().getBlockState(arc).isAir()&&h.getLevel().getBlockState(chord).is(Blocks.STONE),"Terrain sweep cut a chord instead of the actual limb arc");done(h,r);});}
}
