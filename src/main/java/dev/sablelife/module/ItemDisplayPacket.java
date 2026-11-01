package dev.sablelife.module;
import dev.sablelife.SableLife;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
public record ItemDisplayPacket(BlockPos core,UUID creature,CompoundTag displays) implements CustomPacketPayload {
    public static final Type<ItemDisplayPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"item_displays"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ItemDisplayPacket> CODEC=StreamCodec.of((b,p)->{b.writeBlockPos(p.core);b.writeUUID(p.creature);b.writeNbt(p.displays);},b->new ItemDisplayPacket(b.readBlockPos(),b.readUUID(),b.readNbt()));
    @Override public Type<ItemDisplayPacket> type(){return TYPE;}
}
