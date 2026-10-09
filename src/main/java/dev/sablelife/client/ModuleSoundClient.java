package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.ModuleNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** Keep voice handles, so releasing one creature cannot stop another creature's identical sound. */
@EventBusSubscriber(modid = SableLife.MOD_ID, value = Dist.CLIENT)
public final class ModuleSoundClient {
    private record Voice(SoundInstance sound, long started, long stopAt,boolean persistent) {}
    private static final dev.sablelife.sound.SoundCueQueue<ModuleNetworking.ModuleSound> pending = new dev.sablelife.sound.SoundCueQueue<>();
    private static final Map<UUID, List<Voice>> voices = new HashMap<>();
    private static long tick;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    public static void receive(ModuleNetworking.ModuleSound packet) {
        var mc = Minecraft.getInstance(); if (mc.level == null) return;
        if (world != mc.level) { clear(); world = mc.level; }
        if (packet.stop()) {
            pending.cancel(packet.module(),packet.persistent());
            var old = voices.get(packet.module()); if(old!=null){old.removeIf(v->{if(packet.persistent()||!v.persistent){mc.getSoundManager().stop(v.sound);return true;}return false;});if(old.isEmpty())voices.remove(packet.module());}return;
        }
        if(packet.start()<0||packet.start()>12000||packet.duration()<0||packet.duration()>12000)return;
        if(packet.start()>0){
            if(pending.size()>=4096)return;
            pending.add(packet.module(),tick,packet.start(),packet.persistent(),new ModuleNetworking.ModuleSound(packet.module(),packet.sound(),false,packet.position(),packet.volume(),packet.pitch(),packet.radius(),0,packet.duration(),packet.persistent()));return;
        }
        var id = ResourceLocation.tryParse(packet.sound());
        if (id == null || !Float.isFinite(packet.volume()) || !Float.isFinite(packet.pitch())) return;
        var sound = new SimpleSoundInstance(id, SoundSource.HOSTILE, Math.clamp(packet.volume(), 0F, 4F), Math.clamp(packet.pitch(), .5F, 2F),
                RandomSource.create(), false, 0, SoundInstance.Attenuation.LINEAR, packet.position().x, packet.position().y, packet.position().z, false);
        SoundInstance playback = packet.radius() > 0 && Float.isFinite(packet.radius()) ? new RadiusSound(id, packet) : sound;
        var list = voices.computeIfAbsent(packet.module(), key -> new ArrayList<>());
        if (list.size() >= 128) mc.getSoundManager().stop(list.removeFirst().sound);
        list.add(new Voice(playback, tick, packet.duration()>0?tick+packet.duration():Long.MAX_VALUE,packet.persistent())); mc.getSoundManager().play(playback);
    }
    private static final class RadiusSound extends AbstractTickableSoundInstance {
        private final float gain, radius;
        RadiusSound(ResourceLocation id, ModuleNetworking.ModuleSound packet) {
            super(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id), SoundSource.HOSTILE, RandomSource.create());
            gain = Math.clamp(packet.volume(), 0F, 4F); radius = Math.clamp(packet.radius(), 1F, 256F);
            x = packet.position().x; y = packet.position().y; z = packet.position().z;
            pitch = Math.clamp(packet.pitch(), .5F, 2F); attenuation = SoundInstance.Attenuation.NONE;
            tick();
        }
        @Override public boolean canStartSilent() { return true; }
        @Override public void tick() {
            var player = Minecraft.getInstance().player;
            if (player == null) { stop(); return; }
            volume = gain * (float) Math.clamp(1 - dev.sablelife.compat.sable.SableBodyProbe.toWorld(player.level(),player.position()).distanceTo(new net.minecraft.world.phys.Vec3(x,y,z)) / radius, 0, 1);
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || world != mc.level) { clear(); world = mc.level; return; }
        if (mc.isPaused()) return;
        tick++;
        pending.drain(tick).forEach(ModuleSoundClient::receive);
        voices.values().forEach(list -> list.removeIf(v -> { if(tick>=v.stopAt){mc.getSoundManager().stop(v.sound);return true;}return tick - v.started > 20 && !mc.getSoundManager().isActive(v.sound); }));
        voices.values().removeIf(List::isEmpty);
    }
    private static void clear() { voices.values().forEach(list -> list.forEach(v -> Minecraft.getInstance().getSoundManager().stop(v.sound))); voices.clear(); pending.clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); world = null; }
}
