package dev.sablelife.module;

import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client-only visual data. Carries block states and placement transforms, never block entity data. */
public record BlueprintProjection(UUID id,List<Part> parts) implements CustomPacketPayload {
    public static final int MAX_BLOCKS=32768;
    public static final int MAX_PARTS=32;
    public BlueprintProjection {
        if(parts.size()>MAX_PARTS||parts.stream().anyMatch(part->part.blocks().isEmpty())
                ||parts.stream().mapToLong(part->part.blocks().size()).sum()>MAX_BLOCKS)
            throw new IllegalArgumentException("Blueprint projection exceeds packet limits");
        parts=List.copyOf(parts);
    }

    public record Block(int state,int x,int y,int z){}
    public record Part(Vec3 origin,Quaternionf rotation,Vec3 scale,List<Block> blocks){}
    public static final Type<BlueprintProjection> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"blueprint_projection"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BlueprintProjection> CODEC=StreamCodec.of((buf,packet)->{
        buf.writeUUID(packet.id);
        buf.writeVarInt(packet.parts.size());
        for(var part:packet.parts){
            vector(buf,part.origin);
            var q=part.rotation;
            buf.writeFloat(q.x);buf.writeFloat(q.y);buf.writeFloat(q.z);buf.writeFloat(q.w);
            vector(buf,part.scale);
            buf.writeVarInt(part.blocks.size());
            for(var block:part.blocks){
                buf.writeVarInt(block.state);
                buf.writeInt(block.x);buf.writeInt(block.y);buf.writeInt(block.z);
            }
        }
    },buf->{
        var id=buf.readUUID();
        int count=buf.readVarInt(), remaining=MAX_BLOCKS;
        if(count<0||count>MAX_PARTS)throw new IllegalArgumentException("Invalid blueprint projection");
        var parts=new ArrayList<Part>(count);
        for(int i=0;i<count;i++){
            var origin=vector(buf);
            var rotation=new Quaternionf(buf.readFloat(),buf.readFloat(),buf.readFloat(),buf.readFloat());
            var scale=vector(buf);
            int size=buf.readVarInt();
            if(size<1||size>remaining)throw new IllegalArgumentException("Invalid blueprint projection block count");
            remaining-=size;
            var blocks=new ArrayList<Block>(size);
            for(int j=0;j<size;j++)blocks.add(new Block(buf.readVarInt(),buf.readInt(),buf.readInt(),buf.readInt()));
            parts.add(new Part(origin,rotation,scale,List.copyOf(blocks)));
        }
        return new BlueprintProjection(id,List.copyOf(parts));
    });
    private static void vector(RegistryFriendlyByteBuf buf,Vec3 value){
        buf.writeDouble(value.x);buf.writeDouble(value.y);buf.writeDouble(value.z);
    }
    private static Vec3 vector(RegistryFriendlyByteBuf buf){return new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble());}
    @Override public Type<BlueprintProjection> type(){return TYPE;}
}
