package dev.sablelife.testing;

import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.module.MeleeGeometry;
import dev.sablelife.module.ModuleAnchor;
import dev.sablelife.module.ModuleFields;
import dev.sablelife.module.ModuleNetworking.ModuleSound;
import dev.sablelife.module.VirtualModule;
import dev.sablelife.sound.SoundTimeline;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("sablelife_melee_tests")
@PrefixGameTestTemplate(false)
public final class MeleeShieldGameTests {
    private static final String OPTION = "meleeDisableShield";
    private static final String SOUND_KEY = "meleeShieldBreakSound";
    private static final String SOUND = "minecraft:block.note_block.bell";
    private static final int AUDIBLE_RADIUS = 6;
    private static final float DAMAGE = 6;
    private static final List<Float> NATIVE_IMPULSE = List.of(.4F);

    @GameTest(template = "combat", batch = "melee_shield_runtime")
    public static void MeleeDisablesOnlyTheShieldThatActuallyBlocks(GameTestHelper h) throws ReflectiveOperationException {
        var core = SlashGuardGameTests.core(h);
        var melee = melee(core);
        var timeline = new SoundTimeline();
        h.assertTrue(timeline.edit("add", null, 0, SOUND), "Could not add a shield-break sound");
        var row = timeline.rows().getFirst().id();
        h.assertTrue(timeline.edit("radius", row, AUDIBLE_RADIUS, "")
                        && timeline.edit("start", row, .2, "") && timeline.edit("duration", row, .4, ""),
                "Could not edit the shield-break timeline");
        melee.soundTimeline(SOUND_KEY, timeline);
        h.assertTrue(melee.hasSound(SOUND_KEY), "The Melee sound editor did not retain the shield-break cue");
        core.tryActivate(TestActors.creative(h.getLevel()));

        var remaining = LivingEntity.class.getDeclaredField("useItemRemaining");
        remaining.setAccessible(true);
        h.assertTrue(melee.value(OPTION) == 0, "Melee did not start with shield disabling off");
        check(h, core, melee, remaining, Guard.FRONTAL, InteractionHand.OFF_HAND);
        h.assertTrue(melee.edit(OPTION, 1, "") && melee.value(OPTION) == 1, "Could not enable the Melee shield option");
        check(h, core, melee, remaining, Guard.FRONTAL, InteractionHand.OFF_HAND);
        check(h, core, melee, remaining, Guard.FRONTAL, InteractionHand.MAIN_HAND);
        check(h, core, melee, remaining, Guard.FACING_AWAY, InteractionHand.OFF_HAND);
        check(h, core, melee, remaining, Guard.HELD, InteractionHand.OFF_HAND);
        check(h, core, melee, remaining, Guard.OUTSIDE_BOX, InteractionHand.OFF_HAND);
        timeline.edit("clear", null, 0, "");
        melee.soundTimeline(SOUND_KEY, timeline);
        check(h, core, melee, remaining, Guard.FRONTAL, InteractionHand.OFF_HAND);
        melee.edit("enabled", 0, "");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "melee_vanilla_knockback")
    public static void ZeroMeleeKnockbackHonorsToggleAndPreservesDamageAndShield(GameTestHelper h) throws ReflectiveOperationException {
        var core = SlashGuardGameTests.core(h);
        var melee = melee(core);
        var id = melee.id();
        String option = ModuleFields.MELEE_VANILLA_KNOCKBACK;
        core.tryActivate(TestActors.creative(h.getLevel()));

        h.assertTrue(melee.value(option) == 1 && melee.canEditMeleeVanillaKnockback(), "New zero-knockback Melee changed its native default");
        checkKnockback(h, core, melee, null, NATIVE_IMPULSE, false);
        var legacy = core.modules().save();
        legacy.getList("Modules", 10).getCompound(0).getCompound("Settings").remove(option);
        core.modules().load(legacy);
        melee = core.modules().find(id);
        h.assertTrue(melee != null && melee.value(option) == 1, "An older zero-knockback Melee lost native knockback");
        checkKnockback(h, core, melee, null, NATIVE_IMPULSE, false);
        h.assertTrue(melee.edit(option, 0, "") && melee.value(option) == 0, "Could not disable native knockback at zero");
        checkKnockback(h, core, melee, null, List.of(), true);

        var remaining = LivingEntity.class.getDeclaredField("useItemRemaining");
        remaining.setAccessible(true);
        h.assertTrue(melee.edit(OPTION, 1, ""), "Could not enable shield disabling along with native knockback off");
        checkKnockback(h, core, melee, remaining, List.of(), false);

        h.assertTrue(melee.edit("knockback", .75, "") && !melee.canEditMeleeVanillaKnockback(),
                "The native toggle remained editable with nonzero module knockback");
        h.assertTrue(!melee.edit(option, 1, "") && melee.value(option) == 0, "Locked edit replaced the stored Off preference");
        core.modules().load(core.modules().save());
        melee = core.modules().find(id);
        h.assertTrue(melee != null && melee.value("knockback") == .75 && melee.value(option) == 0,
                "The locked Off preference was lost on save and reload");
        checkKnockback(h, core, melee, null, List.of(.4F, .75F), false);
        h.assertTrue(melee.edit("knockback", 0, "") && melee.canEditMeleeVanillaKnockback()
                        && melee.value(option) == 0,
                "Returning to zero did not restore the stored Off preference");
        checkKnockback(h, core, melee, null, List.of(), false);
        h.assertTrue(melee.edit(option, 1, ""), "Could not restore native knockback");
        checkKnockback(h, core, melee, null, NATIVE_IMPULSE, false);
        melee.edit("enabled", 0, "");
        h.succeed();
    }

    @GameTest(template = "combat", batch = "melee_shield_config")
    public static void ShieldOptionPersistsAndIsOffForLegacyMelee(GameTestHelper h) {
        var melee = new VirtualModule("melee");
        h.assertTrue(melee.value(OPTION) == 0, "New Melee must retain ordinary blocking by default");
        h.assertTrue(melee.edit(OPTION, 1, "") && VirtualModule.load(melee.save()).value(OPTION) == 1,
                "Enabled shield disabling did not persist");

        var legacy = melee.save();
        legacy.getCompound("Settings").remove(OPTION);
        h.assertTrue(VirtualModule.load(legacy).value(OPTION) == 0, "Legacy Melee enabled shield disabling");
        h.assertTrue(melee.edit(OPTION, 0, "") && VirtualModule.load(melee.save()).value(OPTION) == 0,
                "Disabled shield setting did not persist");
        h.assertTrue(ModuleFields.find("slash", OPTION) == null && !new VirtualModule("slash").edit(OPTION, 1, ""),
                "The Melee option leaked into Slash");
        h.succeed();
    }

    private static VirtualModule melee(HeartBlockEntity core) {
        var melee = core.modules().add("melee");
        var mark = ModuleAnchor.create(core, core.getBlockPos(), Direction.SOUTH);
        melee.anchors(List.of(mark, mark));
        melee.edit("startDistance", 3, "");
        melee.edit("damage", DAMAGE, "");
        melee.edit("knockback", 0, "");
        return melee;
    }

    private static void checkKnockback(GameTestHelper h, HeartBlockEntity core, VirtualModule melee, Field shieldTimer,
                                       List<Float> expected, boolean checkVanillaDamage) throws IllegalAccessException {
        var level = h.getLevel();
        var frame = MeleeGeometry.frame(core, melee);
        var outward = frame.center().subtract(frame.position()).normalize();
        var position = frame.center().add(outward.scale(.2)).add(0, -.65, 0);
        var victim = defender(h, position, frame.position(), false);
        var shield = new ItemStack(Items.SHIELD);
        if (shieldTimer != null) raiseShield(victim, InteractionHand.OFF_HAND, shield, shieldTimer);
        var motion = new Vec3(.04, .01, -.03);
        victim.setDeltaMovement(motion);
        level.addNewPlayer(victim);
        var strengths = new ArrayList<Float>();
        Consumer<LivingKnockBackEvent> capture = event -> {
            if (event.getEntity() == victim && event.getStrength() > 0) strengths.add(event.getOriginalStrength());
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, capture);
        try {
            core.modules().tick(core);
            h.assertTrue(strengths.equals(expected), "Unexpected impulses from the actual Melee hit: " + strengths + ", expected " + expected);
            h.assertTrue(expected.isEmpty() ? victim.getDeltaMovement().equals(motion) : victim.getDeltaMovement().distanceTo(motion) > .1,
                    "The Melee hit changed motion unexpectedly: " + victim.getDeltaMovement());
            if (shieldTimer != null) {
                h.assertTrue(victim.getHealth() == 20 && shield.getDamageValue() > 0
                                && victim.getCooldowns().isOnCooldown(Items.SHIELD) && !victim.isUsingItem(),
                        "Suppressing knockback interfered with a frontal shield block or shield disabling");
            } else {
                h.assertTrue(victim.getHealth() == 20 - DAMAGE, "The Melee hit changed damage: " + victim.getHealth());
            }
            if (checkVanillaDamage) {
                strengths.clear();
                victim.invulnerableTime = 0;
                victim.setDeltaMovement(motion);
                var source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(DamageTypes.MOB_ATTACK), frame.position());
                h.assertTrue(victim.hurt(source, DAMAGE) && strengths.equals(NATIVE_IMPULSE)
                                && victim.getDeltaMovement().distanceTo(motion) > .1,
                        "The Melee toggle also suppressed a separate vanilla hit on the same player");
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(capture);
            level.removePlayerImmediately(victim, Entity.RemovalReason.DISCARDED);
        }
    }

    private static void check(GameTestHelper h, HeartBlockEntity core, VirtualModule melee, Field remaining,
                              Guard guard, InteractionHand hand) throws IllegalAccessException {
        boolean raised = guard != Guard.HELD;
        boolean blocked = guard == Guard.FRONTAL;
        boolean enabled = melee.value(OPTION) == 1;
        boolean disabled = blocked && enabled;
        boolean audible = disabled && melee.hasSound(SOUND_KEY);
        String scenario = guard + " with " + hand + " and shield disabling " + (enabled ? "on" : "off");
        var frame = MeleeGeometry.frame(core, melee);
        var position = frame.center().add(0, -.65, guard == Guard.OUTSIDE_BOX ? 2 : 0);
        var victim = defender(h, position, frame.position(), guard == Guard.FACING_AWAY);
        var shield = new ItemStack(Items.SHIELD);
        if (raised) raiseShield(victim, hand, shield, remaining);
        else victim.setItemInHand(hand, shield);
        var level = h.getLevel();
        var outward = frame.center().subtract(frame.position()).normalize();
        var near = TestActors.creative(level);
        near.setPos(position.add(outward.scale(AUDIBLE_RADIUS - 1)));
        var far = TestActors.creative(level);
        far.setPos(position.subtract(outward.scale(AUDIBLE_RADIUS + 1)));
        var defenderSound = captureAudio(victim, melee.audioId());
        var nearSound = captureAudio(near, melee.audioId());
        var farSound = captureAudio(far, melee.audioId());
        level.addNewPlayer(victim);
        level.players().add(near);
        level.players().add(far);
        try {
            h.assertTrue(level.players().contains(victim), "Shield holder was not present in the sound audience");
            h.assertTrue(victim.isBlocking() == raised, "Shield was not in the intended state for " + scenario);
            core.modules().tick(core);

            float expectedHealth = blocked || guard == Guard.OUTSIDE_BOX ? 20 : 20 - DAMAGE;
            h.assertTrue(victim.getHealth() == expectedHealth, "Unexpected health after " + scenario + ": " + victim.getHealth());
            h.assertTrue((shield.getDamageValue() > 0) == blocked, "Unexpected shield wear after " + scenario);
            h.assertTrue(victim.getCooldowns().isOnCooldown(Items.SHIELD) == disabled, "Unexpected shield cooldown after " + scenario);
            h.assertTrue(victim.isUsingItem() == (raised && !disabled), "Unexpected item use after " + scenario);
            h.assertTrue(defenderSound.isEmpty(), "Shield holder received the custom shield-break cue after " + scenario);
            h.assertTrue(farSound.isEmpty(), "Out-of-range player received the shield-break cue after " + scenario);
            h.assertTrue(nearSound.size() == (audible ? 1 : 0),
                    "Unexpected shield-break cue count after " + scenario + ": " + nearSound.size());
            if (audible) {
                var cue = nearSound.getFirst();
                h.assertTrue(cue.sound().equals(SOUND) && cue.position().distanceTo(victim.position()) < .0001,
                        "Shield-break cue did not use the defender's world position or selected sound");
                h.assertTrue(cue.radius() == AUDIBLE_RADIUS && cue.start() == 4 && cue.duration() == 8 && cue.persistent(),
                        "Shield-break cue did not preserve the authored timeline");
            }
            if (disabled) {
                var cooldowns = victim.getCooldowns();
                // At the next-tick interpolation point, exactly 99 of the original 100 ticks should remain.
                h.assertTrue(cooldowns.getCooldownPercent(Items.SHIELD, 0) == 1
                                && Math.abs(cooldowns.getCooldownPercent(Items.SHIELD, 1) - .99) < .00001,
                        "A frontal block did not start a fresh 100-tick shield cooldown");
            }
        } finally {
            level.players().remove(far);
            level.players().remove(near);
            level.removePlayerImmediately(victim, Entity.RemovalReason.DISCARDED);
        }
    }

    private static ServerPlayer defender(GameTestHelper h, Vec3 position, Vec3 origin, boolean facingAway) {
        var towardAttacker = origin.subtract(position);
        float yaw = (float) Math.toDegrees(Math.atan2(-towardAttacker.x, towardAttacker.z));
        var player = new SlashGuardGameTests.Defender(h.getLevel());
        player.setPos(position);
        player.setYRot(yaw + (facingAway ? 180 : 0));
        player.setYHeadRot(player.getYRot());
        return player;
    }

    private static void raiseShield(ServerPlayer player, InteractionHand hand, ItemStack shield, Field timer) throws IllegalAccessException {
        player.setItemInHand(hand, shield);
        player.startUsingItem(hand);
        timer.setInt(player, shield.getUseDuration(player) - 6);
    }

    private static List<ModuleSound> captureAudio(ServerPlayer player, UUID module) {
        var received = new ArrayList<ModuleSound>();
        player.connection = new ServerGamePacketListenerImpl(player.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            @Override
            public void send(Packet<?> packet) {
                if (packet instanceof ClientboundCustomPayloadPacket payload && payload.payload() instanceof ModuleSound sound
                        && sound.module().equals(module) && !sound.stop()) received.add(sound);
            }
        };
        return received;
    }

    private enum Guard { FRONTAL, FACING_AWAY, HELD, OUTSIDE_BOX }
}
