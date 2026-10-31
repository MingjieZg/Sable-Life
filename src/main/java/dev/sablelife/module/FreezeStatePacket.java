package dev.sablelife.module;
import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
public record FreezeStatePacket(int ticks,Vec3 anchor) implements CustomPacketPayload {
    public static final Type<FreezeStatePacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"freeze_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FreezeStatePacket> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.ticks);b.writeDouble(p.anchor.x);b.writeDouble(p.anchor.y);b.writeDouble(p.anchor.z);},b->new FreezeStatePacket(b.readVarInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
    @Override public Type<FreezeStatePacket> type(){return TYPE;}
}
