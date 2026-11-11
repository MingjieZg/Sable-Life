package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.ModuleTargets;
import dev.sablelife.ownership.LoadedCoreIndex;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("sablelife_relations_tests")
@PrefixGameTestTemplate(false)
public final class MobWhitelistGameTests {
    private static HeartBlockEntity core(GameTestHelper h,int kind){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(4,40,4));var block=kind==1?SableLife.REDSTONE_CORE.get():kind==2?SableLife.SURVIVAL_LIFE_CORE.get():SableLife.HEART.get();
        level.setBlockAndUpdate(p,block.defaultBlockState());level.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());LoadedCoreIndex.observe(core);return core;
    }
    @GameTest(template="combat",batch="mob_whitelist_catalog")
    public static void CatalogIncludesLivingMiscTypesAndRejectsProjectilesPlayersAndReceivers(GameTestHelper h){
        var ids=CoreRelations.mobTypes();for(var type:List.of(EntityType.COW,EntityType.PIG,EntityType.SHEEP,EntityType.IRON_GOLEM))h.assertTrue(ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(type)),"Living mob missing from catalog: "+type);
        var data=new CoreRelations();for(String id:List.of("minecraft:player","minecraft:arrow","minecraft:item","sablelife:entity_hitbox","missing_mod:unknown","broken id",":", "x".repeat(257)))h.assertTrue(!data.excludeMob(id),"Invalid/nonliving type accepted: "+id);
        var mod=ResourceLocation.parse("cataclysm:deepling");if(BuiltInRegistries.ENTITY_TYPE.containsKey(mod))h.assertTrue(ids.contains(mod)&&data.excludeMob(mod.toString()),"Modded living type missing or rejected");
        h.succeed();
    }
    private static void acquisition(GameTestHelper h,int kind){
        var core=core(h,kind);var point=h.absoluteVec(new Vec3(6,60,6));var a=EntityType.COW.create(h.getLevel());var b=EntityType.COW.create(h.getLevel());var pig=EntityType.PIG.create(h.getLevel());
        for(var entity:List.of(a,b,pig)){entity.setNoAi(true);entity.setNoGravity(true);entity.setPos(point);h.getLevel().addFreshEntity(entity);}
        h.assertTrue(core.relations().excludeMob("minecraft:cow"),"Could not add cow type");var area=a.getBoundingBox().inflate(3);
        for(int mode:new int[]{1,2}){
            var targets=ModuleTargets.query(h.getLevel(),core.creatureId(),area,e->ModuleTargets.matches(e,mode));
            h.assertTrue(targets.contains(pig)&&!targets.contains(a)&&!targets.contains(b),"Mob/All acquisition ignored type exclusion, core="+kind+" mode="+mode);
        }
        h.assertTrue(ModuleTargets.resolve(h.getLevel(),a.getUUID(),core.creatureId())==null,"Existing lock can reacquire an excluded type");
        h.assertTrue(ModuleTargets.damageQuery(h.getLevel(),core.creatureId(),area,e->true).contains(a),"Acquisition whitelist incorrectly grants area-damage immunity");
        h.assertTrue(core.relations().removeMob("minecraft:cow")&&ModuleTargets.resolve(h.getLevel(),a.getUUID(),core.creatureId())==a,"Removing type did not restore targeting");
        for(var entity:List.of(a,b,pig))entity.discard();h.succeed();
    }
    @GameTest(template="combat",batch="mob_whitelist_heart") public static void HeartCoreMobAndAllRespectWhitelist(GameTestHelper h){acquisition(h,0);}
    @GameTest(template="combat",batch="mob_whitelist_redstone") public static void RedstoneCoreMobAndAllRespectWhitelist(GameTestHelper h){acquisition(h,1);}
    @GameTest(template="combat",batch="mob_whitelist_survival") public static void SurvivalCoreMobAndAllRespectWhitelist(GameTestHelper h){acquisition(h,2);}
    @GameTest(template="combat",batch="mob_whitelist_persistence")
    public static void CoreSaveAndClipboardRetainTypesAndAbsentMods(GameTestHelper h){
        var core=core(h,0);core.relations().exclude("OfflineFriend");core.relations().excludeMob("minecraft:cow");
        var copy=core.copyConfiguration();var data=copy.getCompound("Relations");var list=data.getList("ExcludedMobs",8);list.add(StringTag.valueOf("missing_mod:future_mob"));list.add(StringTag.valueOf("broken id"));
        var restored=new CoreRelations();restored.load(data);h.assertTrue(restored.excludes("offlinefriend")&&restored.excludesMob(EntityType.COW),"Clipboard lost player or mob whitelist");
        h.assertTrue(restored.save().getList("ExcludedMobs",8).size()==2&&restored.removeMob("missing_mod:future_mob"),"Absent mod ID lost or malformed ID retained");
        var saved=core.saveWithFullMetadata(h.getLevel().registryAccess());var loaded=(HeartBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(core.getBlockPos(),core.getBlockState(),saved,h.getLevel().registryAccess());
        h.assertTrue(loaded.relations().excludesMob(EntityType.COW),"Core save/reload lost type");restored.load(new CompoundTag());h.assertTrue(!restored.excludesMob(EntityType.COW),"Legacy empty relations did not clear types");h.succeed();
    }
    @GameTest(template="combat",batch="mob_whitelist_permissions")
    public static void ServerActionsValidateTypeRangeAndPermissions(GameTestHelper h){
        var core=core(h,0);var actor=TestActors.creative(h.getLevel());var point=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());actor.setPos(point);
        var menu=new HeartSettingsMenu(1,actor.getInventory(),core);
        h.assertTrue(menu.action(actor,"relation:excludeMob",0,"minecraft:cow")&&menu.snapshot().getCompound("Relations").getList("ExcludedMobs",8).size()==1,"Server action/snapshot did not add type");
        h.assertTrue(!menu.action(actor,"relation:excludeMob",0,"minecraft:arrow"),"Forged nonliving ID accepted");
        actor.setPos(point.add(30,0,0));h.assertTrue(!menu.action(actor,"relation:removeMob",0,"minecraft:cow")&&core.relations().excludesMob(EntityType.COW),"Remote action bypassed reach check");
        actor.setPos(point);actor.setGameMode(GameType.ADVENTURE);h.assertTrue(!menu.action(actor,"relation:removeMob",0,"minecraft:cow"),"Unprivileged core edit accepted");
        actor.setGameMode(GameType.CREATIVE);h.assertTrue(menu.action(actor,"relation:removeMob",0,"minecraft:cow"),"Valid type removal rejected");h.succeed();
    }
    @GameTest(template="combat",batch="mob_whitelist_limits")
    public static void LoadedListsAreBoundedAndUnknownIdsCanBeRemoved(GameTestHelper h){
        var t=new CompoundTag();var list=new ListTag();for(int i=0;i<300;i++)list.add(StringTag.valueOf("missing_mod:type_"+i));t.put("ExcludedMobs",list);var data=new CoreRelations();data.load(t);
        h.assertTrue(data.save().getList("ExcludedMobs",8).size()==CoreRelations.LIMIT&&!data.excludeMob("minecraft:cow"),"Whitelist storage bound bypassed");
        h.assertTrue(data.removeMob("missing_mod:type_0")&&data.excludeMob("minecraft:cow")&&data.excludeMob("minecraft:cow"),"Removal did not free a slot or duplicate add was rejected");h.succeed();
    }
}
