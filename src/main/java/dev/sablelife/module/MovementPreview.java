package dev.sablelife.module;

import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Geometry is already present on the viewer: send body references, never a block list. */
public record MovementPreview(ResourceLocation dimension,List<UUID> bodies,Vec3 offset,boolean clear) implements CustomPacketPayload {
    public static final Type<MovementPreview> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"movement_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MovementPreview> CODEC=StreamCodec.of((buf,p)->{
        buf.writeResourceLocation(p.dimension);buf.writeVarInt(p.bodies.size());for(var id:p.bodies)buf.writeUUID(id);
        buf.writeDouble(p.offset.x);buf.writeDouble(p.offset.y);buf.writeDouble(p.offset.z);buf.writeBoolean(p.clear);
    },buf->{
        var dimension=buf.readResourceLocation();int count=buf.readVarInt();if(count<1||count>64)throw new IllegalArgumentException("Invalid movement preview body count");
        var ids=new ArrayList<UUID>(count);for(int i=0;i<count;i++)ids.add(buf.readUUID());
        return new MovementPreview(dimension,ids,new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readBoolean());
    });
    public MovementPreview {
        if(bodies.isEmpty()||bodies.size()>64||bodies.stream().distinct().count()!=bodies.size()||!Double.isFinite(offset.lengthSqr())||offset.lengthSqr()>256*256)
            throw new IllegalArgumentException("Invalid movement preview");
        bodies=List.copyOf(bodies);
    }
    @Override public Type<MovementPreview> type(){return TYPE;}
}
