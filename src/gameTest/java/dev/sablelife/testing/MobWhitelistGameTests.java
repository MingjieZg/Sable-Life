package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.*;
import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.module.ModuleTargets;
import dev.sablelife.ownership.LoadedCoreIndex;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.UUID;

@GameTestHolder("sablelife_relations_tests")
@PrefixGameTestTemplate(false)
public final class MobWhitelistGameTests {
    private static HeartBlockEntity core(GameTestHelper h,int kind){
        return core(h, kind, 4);
    }
    private static HeartBlockEntity core(GameTestHelper h,int kind,int x){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(x,40,4));var block=kind==1?SableLife.REDSTONE_CORE.get():kind==2?SableLife.SURVIVAL_LIFE_CORE.get():SableLife.HEART.get();
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
    @GameTest(template="combat",batch="mob_whitelist_groups")
    public static void MobGroupsUseTagsAndRetainIndividualSelections(GameTestHelper h){
        var data=new CoreRelations();
        var passive=CoreRelations.MobGroup.PASSIVE.id().toString();
        var neutral=CoreRelations.MobGroup.NEUTRAL.id().toString();
        h.assertTrue(!data.excludesMob(EntityType.COW)&&!data.excludesMob(EntityType.IRON_GOLEM),"Groups were enabled by default");
        h.assertTrue(data.excludeMob(passive)&&data.excludesMob(EntityType.SHEEP)&&data.excludesMob(EntityType.VILLAGER)
                &&!data.excludesMob(EntityType.IRON_GOLEM),"Passive group matched the wrong entities");
        h.assertTrue(data.excludeMob(neutral)&&data.excludesMob(EntityType.IRON_GOLEM)&&data.excludesMob(EntityType.WOLF)
                &&data.excludesMob(EntityType.SPIDER)&&!data.excludesMob(EntityType.ZOMBIE),"Neutral group matched the wrong entities");
        data.excludeMob("minecraft:cow");
        var restored=new CoreRelations();restored.load(data.save());
        h.assertTrue(restored.excludesMob(EntityType.SHEEP)&&restored.excludesMob(EntityType.WOLF),"Saved groups were lost");
        h.assertTrue(restored.removeMob(passive)&&!restored.excludesMob(EntityType.SHEEP)&&restored.excludesMob(EntityType.COW)
                &&restored.excludesMob(EntityType.WOLF),"Removing a group removed separate selections");
        h.succeed();
    }
    @GameTest(template="combat",batch="mob_whitelist_permissions")
    public static void ServerActionsValidateTypeRangeAndPermissions(GameTestHelper h){
        var core=core(h,0);var actor=TestActors.creative(h.getLevel());var point=dev.sablelife.compat.sable.SableBodyProbe.toWorld(h.getLevel(),core.getBlockPos().getCenter());actor.setPos(point);
        var menu=new HeartSettingsMenu(1,actor.getInventory(),core);
        h.assertTrue(menu.action(actor,"relation:excludeMob",0,"minecraft:cow")&&menu.snapshot().getCompound("Relations").getList("ExcludedMobs",8).size()==1,"Server action/snapshot did not add type");
        h.assertTrue(!menu.action(actor,"relation:excludeMob",0,"minecraft:arrow"),"Forged nonliving ID accepted");
        h.assertTrue(menu.action(actor, "relation:prioritizeMob", 0, "minecraft:pig")
                        && core.relations().prioritizesMob(EntityType.PIG),
                "Authorized priority edit was rejected");
        var unchanged = core.relations().save();
        h.assertTrue(!menu.action(actor, "relation:prioritizeMob", 0, "minecraft:arrow")
                        && core.relations().save().equals(unchanged),
                "Forged nonliving ID changed priority rules");

        actor.setPos(point.add(30, 0, 0));
        assertMobRulesNotEditable(h, menu, actor, core, unchanged, "Remote edit");
        actor.setPos(point);
        actor.setGameMode(GameType.ADVENTURE);
        assertMobRulesNotEditable(h, menu, actor, core, unchanged, "Adventure player");

        actor.setGameMode(GameType.CREATIVE);
        h.assertTrue(menu.action(actor, "relation:removeMob", 0, "minecraft:cow")
                        && menu.action(actor, "relation:removePriorityMob", 0, "minecraft:pig")
                        && !core.relations().excludesMob(EntityType.COW) && !core.relations().hasPriorityMobs(),
                "Authorized whitelist or priority removal was rejected");
        h.succeed();
    }

    private static void assertMobRulesNotEditable(GameTestHelper h, HeartSettingsMenu menu,
                                                 net.minecraft.world.entity.player.Player actor,
                                                 HeartBlockEntity core, CompoundTag expected, String role) {
        h.assertTrue(!menu.action(actor, "relation:removeMob", 0, "minecraft:cow")
                        && !menu.action(actor, "relation:prioritizeMob", 0, "minecraft:sheep")
                        && !menu.action(actor, "relation:removePriorityMob", 0, "minecraft:pig")
                        && core.relations().save().equals(expected),
                role + " bypassed authority or changed saved mob rules");
    }
    @GameTest(template="combat",batch="mob_whitelist_limits")
    public static void LoadedListsAreBoundedAndUnknownIdsCanBeRemoved(GameTestHelper h){
        var t=new CompoundTag();var list=new ListTag();for(int i=0;i<300;i++)list.add(StringTag.valueOf("missing_mod:type_"+i));t.put("ExcludedMobs",list);var data=new CoreRelations();data.load(t);
        h.assertTrue(data.save().getList("ExcludedMobs",8).size()==CoreRelations.LIMIT&&!data.excludeMob("minecraft:cow"),"Whitelist storage bound bypassed");
        h.assertTrue(data.removeMob("missing_mod:type_0")&&data.excludeMob("minecraft:cow")&&data.excludeMob("minecraft:cow"),"Removal did not free a slot or duplicate add was rejected");h.succeed();
    }

    @GameTest(template = "combat", batch = "mob_priority_persistence")
    public static void PriorityRulesPersistPerCoreAndKeepLegacyWhitelist(GameTestHelper h) {
        var first = core(h, 0);
        var other = core(h, 0, 9);
        var rules = first.relations();
        h.assertTrue(!rules.hasPriorityMobs() && rules.prioritizeMob("minecraft:cow")
                        && rules.excludeMob("minecraft:*"),
                "Default or registered selectors were rejected");
        h.assertTrue(!rules.prioritizeMob("minecraft:arrow") && !rules.prioritizeMob("minecraft:player")
                        && !rules.prioritizeMob("missing_mod:*") && !rules.excludeMob("broken namespace:*"),
                "An invalid or unregistered selector was accepted");
        h.assertTrue(rules.excludesMob(EntityType.COW) && rules.excludesMob(EntityType.PIG)
                        && !other.relations().hasPriorityMobs() && !other.relations().excludesMob(EntityType.COW),
                "Namespace did not match living types or leaked into another core");

        var clipboard = new CoreRelations();
        clipboard.load(first.copyConfiguration().getCompound("Relations"));
        var saved = first.saveWithFullMetadata(h.getLevel().registryAccess());
        var loaded = (HeartBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                first.getBlockPos(), first.getBlockState(), saved, h.getLevel().registryAccess());
        for (var restored : List.of(clipboard, loaded.relations())) {
            h.assertTrue(restored.excludesMob(EntityType.PIG) && restored.removeMob("minecraft:*")
                            && restored.prioritizesMob(EntityType.COW) && !restored.prioritizesMob(EntityType.PIG),
                    "Namespace whitelist or exact priority was lost in save/clipboard");
            h.assertTrue(restored.prioritizeMob("minecraft:*") && restored.prioritizesMob(EntityType.PIG)
                            && restored.removePriorityMob("minecraft:*") && restored.prioritizesMob(EntityType.COW)
                            && !restored.prioritizesMob(EntityType.PIG),
                    "Priority namespace failed to match or deleting it changed separate exact rules");
        }
        h.assertTrue(rules.excludesMob(EntityType.PIG), "Editing a restored core changed the original rules");
        var legacy = rules.save();
        legacy.remove("PriorityMobs");
        clipboard.load(legacy);
        h.assertTrue(!clipboard.hasPriorityMobs() && clipboard.excludesMob(EntityType.COW),
                "Loading a legacy core kept stale priority rules or dropped its whitelist");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "mob_priority_limits")
    public static void PrioritySelectorsShareTheCapAndUnavailableNamespacesRoundTrip(GameTestHelper h) {
        var entries = new ListTag();
        entries.add(StringTag.valueOf("missing_mod:*"));
        entries.add(StringTag.valueOf("broken namespace:*"));
        for (int i = 0; i < CoreRelations.LIMIT + 10; i++)
            entries.add(StringTag.valueOf("missing_mod:type_" + i));
        var tag = new CompoundTag();
        tag.put("PriorityMobs", entries);
        var rules = new CoreRelations();
        rules.load(tag);
        h.assertTrue(rules.save().getList("PriorityMobs", Tag.TAG_STRING).size() == CoreRelations.LIMIT
                        && !rules.prioritizeMob("minecraft:pig") && !rules.removePriorityMob("broken namespace:*"),
                "Imported rules bypassed the combined cap or retained a malformed namespace");
        h.assertTrue(rules.removePriorityMob("missing_mod:*") && rules.prioritizeMob("minecraft:pig")
                        && rules.prioritizeMob("minecraft:pig") && rules.prioritizesMob(EntityType.PIG),
                "Missing namespace did not survive import or deleting it failed to make a slot");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "mob_priority_codec")
    public static void MobRuleActionsRoundTripWithTheirOwnBoundAndApplyWildcards(GameTestHelper h) {
        var heart = core(h, 0);
        var actor = TestActors.creative(h.getLevel());
        actor.setPos(SableBodyProbe.toWorld(h.getLevel(), heart.getBlockPos().getCenter()));
        var menu = new HeartSettingsMenu(1, actor.getInventory(), heart);
        var rules = heart.relations();
        var coreId = new UUID(0, 0);
        var wildcard = "minecraft:*";
        var longest = "a".repeat(254) + ":*";
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        try {
            for (var key : List.of("relation:excludeMob", "relation:removeMob",
                    "relation:prioritizeMob", "relation:removePriorityMob")) {
                var maximum = new ModuleNetworking.Action(1, 0, coreId, "core", key, 0, longest);
                h.assertTrue(codecRoundTrip(buffer, maximum).equals(maximum) && !buffer.isReadable(),
                        "A 256-character mob-rule action did not round-trip: " + key);
                assertCodecTextRejected(h, buffer, new ModuleNetworking.Action(1, 0, coreId, "core", key, 0, "a" + longest));

                var action = new ModuleNetworking.Action(1, 0, coreId, "core", key, 0, wildcard);
                var decoded = codecRoundTrip(buffer, action);
                h.assertTrue(decoded.equals(action) && menu.action(actor, decoded.key(), decoded.value(), decoded.text()),
                        "The namespace wildcard did not survive the network/menu path: " + key);
                boolean adding = key.equals("relation:excludeMob") || key.equals("relation:prioritizeMob");
                boolean matches = key.equals("relation:excludeMob") || key.equals("relation:removeMob")
                        ? rules.excludesMob(EntityType.PIG) : rules.prioritizesMob(EntityType.PIG);
                h.assertTrue(matches == adding, "Decoded action did not update its intended list: " + key);
            }

            String legacyMaximum = "a".repeat(80);
            var name = new ModuleNetworking.Action(1, 0, coreId, "core", "relation:excludeName", 0, legacyMaximum);
            h.assertTrue(codecRoundTrip(buffer, name).equals(name), "Unrelated core field lost its old 80-character bound");
            assertCodecTextRejected(h, buffer,
                    new ModuleNetworking.Action(1, 0, coreId, "core", name.key(), 0, legacyMaximum + "a"));
            assertCodecTextRejected(h, buffer,
                    new ModuleNetworking.Action(1, 0, coreId, "edit", "relation:excludeMob", 0, legacyMaximum + "a"));
            h.succeed();
        } finally {
            buffer.release();
        }
    }

    private static ModuleNetworking.Action codecRoundTrip(RegistryFriendlyByteBuf buffer, ModuleNetworking.Action action) {
        buffer.clear();
        ModuleNetworking.Action.CODEC.encode(buffer, action);
        return ModuleNetworking.Action.CODEC.decode(buffer);
    }

    private static void assertCodecTextRejected(GameTestHelper h, RegistryFriendlyByteBuf buffer, ModuleNetworking.Action action) {
        buffer.clear();
        try {
            ModuleNetworking.Action.CODEC.encode(buffer, action);
            h.assertTrue(false, "Encoder accepted oversized text for " + action.operation() + " / " + action.key());
        } catch (EncoderException expected) {
        }
        buffer.clear();
        buffer.writeVarInt(action.container());
        buffer.writeVarInt(action.revision());
        buffer.writeUUID(action.module());
        buffer.writeUtf(action.operation(), 16);
        buffer.writeUtf(action.key(), 64);
        buffer.writeDouble(action.value());
        buffer.writeUtf(action.text(), action.text().length());
        try {
            ModuleNetworking.Action.CODEC.decode(buffer);
            h.assertTrue(false, "Decoder accepted oversized text for " + action.operation() + " / " + action.key());
        } catch (DecoderException expected) {
        }
    }

    @GameTest(template = "combat", batch = "mob_priority_selection")
    public static void PreferredEligibleMobBeatsCategoryAndDistanceAndFallsBackToNormalRank(GameTestHelper h) {
        var core = core(h, 0);
        var point = h.absoluteVec(new Vec3(6, 60, 6));
        var near = EntityType.COW.create(h.getLevel());
        var far = EntityType.PIG.create(h.getLevel());
        var farther = EntityType.PIG.create(h.getLevel());
        var player = TestActors.creative(h.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setPos(point.add(0, 0, 3));
        h.getLevel().addNewPlayer(player);
        near.setPos(point.add(1, 0, 0));
        far.setPos(point.add(4, 0, 0));
        farther.setPos(point.add(6, 0, 0));
        try {
            for (var mob : List.of(near, far, farther)) {
                mob.setNoAi(true);
                mob.setNoGravity(true);
                h.getLevel().addFreshEntity(mob);
            }
            var area = new AABB(point, point).inflate(7);
            var order = ModuleTargets.comparator(core, target -> target.distanceToSqr(point));
            java.util.function.IntFunction<net.minecraft.world.entity.LivingEntity> select = mode ->
                    ModuleTargets.query(h.getLevel(), core.creatureId(), area, target -> ModuleTargets.matches(target, mode))
                            .stream().min(order).orElse(null);
            h.assertTrue(select.apply(1) == near, "Default nearest mob was not selected");
            core.relations().priority(1);
            h.assertTrue(select.apply(2) == player, "Fixture did not establish the existing player category priority");
            h.assertTrue(core.relations().prioritizeMob("minecraft:pig") && select.apply(2) == far
                            && select.apply(0) == player,
                    "Preferred mob lost to category/distance, equal priority failed to use distance, or target type was bypassed");
            core.relations().excludeMob("minecraft:pig");
            h.assertTrue(select.apply(2) == player && select.apply(1) == near
                            && ModuleTargets.resolve(h.getLevel(), far.getUUID(), core.creatureId()) == null,
                    "Priority bypassed the whitelist or suppressed ordinary fallback");
            core.relations().excludeMob("minecraft:*");
            h.assertTrue(select.apply(2) == player && select.apply(1) == null,
                    "Namespace whitelist failed to remove mobs or removed non-mob candidates");
            h.succeed();
        } finally {
            near.discard();
            far.discard();
            farther.discard();
            player.discard();
        }
    }
}
