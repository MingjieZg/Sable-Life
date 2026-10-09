package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.attack.*;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("sablelife_blast_tests")
@PrefixGameTestTemplate(false)
public final class MortarBlastGameTests {
    private static LivingEntity target(GameTestHelper h, Vec3 point) {
        var entity = EntityType.PIG.create(h.getLevel());
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        entity.setHealth(100); entity.setNoAi(true); entity.setNoGravity(true);
        entity.setPos(point); h.getLevel().addFreshEntity(entity); return entity;
    }
    private static HeartBlockEntity heart(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(1, 6, 1)); h.getLevel().setBlockAndUpdate(pos, SableLife.HEART.get().defaultBlockState());
        return (HeartBlockEntity) h.getLevel().getBlockEntity(pos);
    }
    @GameTest(template="combat")
    public static void OwnBlastCannotDestroyOwnBodyEvenWhenExplosionsAllowed(GameTestHelper h) {
        var world = h.getLevel(); var core = heart(h); var own = core.getBlockPos().east();
        var other = own.east(); world.setBlockAndUpdate(own, Blocks.OAK_PLANKS.defaultBlockState());
        world.setBlockAndUpdate(other, Blocks.OAK_PLANKS.defaultBlockState());
        dev.sablelife.ownership.BodyOwnership.get(world).bind(own, core.creatureId());
        dev.sablelife.ownership.BodyOwnership.get(world).bind(core.getBlockPos(), core.creatureId());
        var lifecycle = dev.sablelife.ownership.LifeLifecycle.get(world.getServer());
        lifecycle.setBreakable(core.creatureId(), true); lifecycle.setExplodable(core.creatureId(), true);
        var m = new VirtualModule("mortar"); m.edit("blastBlocks", 1, ""); m.edit("blastRadius", 4, "");
        var shot = new ModuleShot(world, own.getCenter(), Vec3.ZERO, m, core);
        ModuleBlast.explode(world, shot, own.getCenter(), ModuleBlast.settings(m));
        h.assertTrue(world.getBlockState(own).is(Blocks.OAK_PLANKS) && world.getBlockState(core.getBlockPos()).is(SableLife.HEART.get()), "Own blast destroyed own core or body");
        var stored = shot.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        var loaded = new ModuleShot(SableLife.MODULE_SHOT.get(), world); loaded.load(stored);
        h.assertTrue(loaded.getPersistentData().getUUID("SableLifeAttackOwner").equals(core.creatureId()), "Projectile owner attribution lost after reload");
        h.succeed();
    }
    @GameTest(template="combat")
    public static void shellImpactAddsDirectAndSplashDamage(GameTestHelper h) {
        var world = h.getLevel(); var origin = Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 6, 5)));
        var direct = target(h, origin.add(0, -1, 0)); var edge = target(h, origin.add(3.5, -1, 0)); var outside = target(h, origin.add(5, -1, 0));
        var module = new VirtualModule("mortar"); module.edit("damage", 7, ""); module.edit("blastDamage", 13, ""); module.edit("blastRadius", 4, "");
        var shot = new ModuleShot(world, origin.add(0,1,0), new Vec3(0,-2,0), module, heart(h)); world.addFreshEntity(shot); shot.tick();
        h.assertTrue(shot.isRemoved(), "Shell did not impact target");
        h.assertTrue(Math.abs(direct.getHealth()-80) < .01, "Direct target should lose 7 + 13, got " + direct.getHealth());
        h.assertTrue(Math.abs(edge.getHealth()-87) < .01, "Warning edge should lose splash 13, got " + edge.getHealth());
        h.assertTrue(outside.getHealth()==100, "Outside target received splash"); h.succeed();
    }
    @GameTest(template="combat")
    public static void groundImpactDamagesEdgeWithNoDirectDamage(GameTestHelper h) {
        var world=h.getLevel(); var point=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,5,5))).add(0,.53,0);
        world.setBlockAndUpdate(BlockPos.containing(point.add(0,-.1,0)), Blocks.STONE.defaultBlockState());
        var near=target(h,point.add(3.5,0,0)); var outside=target(h,point.add(5,0,0));
        var module=new VirtualModule("mortar"); module.edit("damage",0,""); module.edit("blastDamage",13,""); module.edit("blastRadius",4,"");
        var shot=new ModuleShot(world,point.add(0,1,0),new Vec3(0,-2,0),module,heart(h)); world.addFreshEntity(shot); shot.tick();
        h.assertTrue(shot.isRemoved(),"Shell did not impact ground");
        h.assertTrue(Math.abs(near.getHealth()-87)<.01,"Ground blast lost edge damage, got " + near.getHealth());
        h.assertTrue(outside.getHealth()==100,"Ground blast escaped radius"); h.succeed();
    }
    @GameTest(template="combat")
    public static void disabledBlastKeepsDirectDamageAndSettingsReload(GameTestHelper h) {
        var world=h.getLevel(); var point=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,6,5)));
        var direct=target(h,point.add(0,-1,0)); var near=target(h,point.add(2,0,0));
        var original=new VirtualModule("mortar"); original.edit("damage",7,""); original.edit("blastDamage",13,""); original.edit("explode",0,"");
        var module=VirtualModule.load(original.save());
        h.assertTrue(module.value("damage")==7 && module.value("blastDamage")==13,"Direct and blast settings lost on reload");
        var shot=new ModuleShot(world,point.add(0,1,0),new Vec3(0,-2,0),module,heart(h)); world.addFreshEntity(shot); shot.tick();
        h.assertTrue(Math.abs(direct.getHealth()-93)<.01,"Direct damage missing with blast off");
        h.assertTrue(near.getHealth()==100,"Disabled blast still damaged nearby target"); h.succeed();
    }
    @GameTest(template="combat")
    public static void dyingDisguisedHeartDoesNotRefundMaterial(GameTestHelper h) {
        var core=heart(h); var world=h.getLevel(); var pos=core.getBlockPos();
        core.setMaterial(Blocks.OAK_PLANKS.defaultBlockState());
        core.setConsumedItem(new net.minecraft.world.item.ItemStack(Blocks.OAK_PLANKS));
        core.health().activate(); core.damage(core.health().maximum());
        world.removeBlock(pos,false);
        h.assertTrue(world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(2)).isEmpty(), "Dying Heart refunded disguise material");
        h.succeed();
    }
    @GameTest(template="combat")
    public static void dormantDisguisedHeartStillRefundsMaterial(GameTestHelper h) {
        var core=heart(h); var world=h.getLevel(); var pos=core.getBlockPos();
        core.setMaterial(Blocks.OAK_PLANKS.defaultBlockState());
        core.setConsumedItem(new net.minecraft.world.item.ItemStack(Blocks.OAK_PLANKS));
        world.removeBlock(pos,false);
        h.assertTrue(world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(2)).stream()
                .anyMatch(item -> item.getItem().is(Blocks.OAK_PLANKS.asItem())), "Ordinary dormant removal lost material refund");
        h.succeed();
    }
    @GameTest(template="combat")
    public static void armoredSurvivalPlayerReceivesSplash(GameTestHelper h) throws Exception {
        var world=h.getLevel(); var point=Vec3.atCenterOf(h.absolutePos(new BlockPos(5,6,5)));
        var player=new net.neoforged.neoforge.common.util.FakePlayer(world,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "BlastSurvival")) {
            @Override public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) { return false; }
        };
        // FakePlayer normally ignores all damage; remove only the fixture's spawn grace period.
        var grace=net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
        grace.setAccessible(true); grace.setInt(player,0);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(point.add(3.5,0,0)); player.setNoGravity(true);
        player.setItemSlot(EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE));
        player.setItemSlot(EquipmentSlot.LEGS,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_BOOTS));
        // FakePlayer.tick is empty, so install the equipped netherite attributes explicitly.
        player.getAttribute(Attributes.ARMOR).setBaseValue(20);
        player.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(12);
        world.addNewPlayer(player);
        var originalDifficulty=world.getDifficulty();
        world.getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
        try {
            h.assertTrue(!ModuleDamage.hurt(player,world.damageSources().explosion(null,null),13)
                    && player.getHealth()==20,"Vanilla Peaceful blast fixture did not reproduce missing damage");
            h.assertTrue(ModuleDamage.blast(world,null).is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION),
                    "Custom blast lost explosion protection classification");
            var module=new VirtualModule("mortar"); module.edit("blastDamage",13,""); module.edit("blastRadius",4,"");
            ModuleBlast.explode(world,null,point,ModuleBlast.settings(module));
            h.assertTrue(player.getHealth()<20 && player.getHealth()>7,
                    "Armored survival player should take reduced splash damage, HP="+player.getHealth());
            player.getAttribute(Attributes.ARMOR).setBaseValue(0);
            player.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
            player.setHealth(20);
            ModuleBlast.explode(world,null,point,ModuleBlast.settings(module));
            h.assertTrue(Math.abs(player.getHealth()-7)<.01,"Unarmored Peaceful splash did not deal configured 13");
        } finally {
            world.getServer().setDifficulty(originalDifficulty,true);
            world.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }
}
