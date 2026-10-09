package dev.sablelife.module;

import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record ParticleTravelPacket(String id,Vec3 from,Vec3 to,int ticks,int life,float size) implements CustomPacketPayload {
    public static final Type<ParticleTravelPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"particle_travel"));
    private static void vector(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 vector(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public static final StreamCodec<RegistryFriendlyByteBuf,ParticleTravelPacket> CODEC=StreamCodec.of((b,p)->{b.writeUtf(p.id,80);vector(b,p.from);vector(b,p.to);b.writeVarInt(p.ticks);b.writeVarInt(p.life);b.writeFloat(p.size);},b->new ParticleTravelPacket(b.readUtf(80),vector(b),vector(b),b.readVarInt(),b.readVarInt(),b.readFloat()));
    @Override public Type<ParticleTravelPacket> type(){return TYPE;}
}
