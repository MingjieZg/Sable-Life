package dev.sablelife.module;
import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
public record TerrainDebrisPacket(Vec3 position,Vec3 velocity,int state) implements CustomPacketPayload {
 public static final Type<TerrainDebrisPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"terrain_debris"));
 public static final StreamCodec<RegistryFriendlyByteBuf,TerrainDebrisPacket> CODEC=StreamCodec.of((b,p)->{b.writeDouble(p.position.x);b.writeDouble(p.position.y);b.writeDouble(p.position.z);b.writeDouble(p.velocity.x);b.writeDouble(p.velocity.y);b.writeDouble(p.velocity.z);b.writeVarInt(p.state);},b->new TerrainDebrisPacket(new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readVarInt()));
 @Override public Type<TerrainDebrisPacket> type(){return TYPE;}
}
