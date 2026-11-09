package dev.sablelife.module;

import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Short-lived world-space edges sent only to the player editing or previewing a mark. */
public record MarkOutline(ResourceLocation dimension, Kind kind, int color, List<Vec3> endpoints) implements CustomPacketPayload {
    public enum Kind { MARK, VOLUME, DETAIL, ALIGN_FRONT, ALIGN_LEFT, ALIGN_RIGHT, ALIGN_BACK, ALIGN_UP, ALIGN_DOWN }
    public static final int MARK_COLOR=0xFFF1AD;
    private static final int MAX_POINTS=108;
    public static final Type<MarkOutline> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"mark_outline"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MarkOutline> CODEC=StreamCodec.of((buf,packet)->{
        buf.writeResourceLocation(packet.dimension());buf.writeEnum(packet.kind());buf.writeInt(packet.color());
        buf.writeCollection(packet.endpoints(),(out,p)->{out.writeDouble(p.x);out.writeDouble(p.y);out.writeDouble(p.z);});
    },buf->{
        var dimension=buf.readResourceLocation();var kind=buf.readEnum(Kind.class);int color=buf.readInt();
        int count=buf.readVarInt();
        if(count<0||count>MAX_POINTS||count%2!=0)throw new IllegalArgumentException("Invalid mark outline size");
        var endpoints=new ArrayList<Vec3>(count);
        for(int i=0;i<count;i++)endpoints.add(new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()));
        return new MarkOutline(dimension,kind,color,endpoints);
    });

    public MarkOutline {
        if(endpoints.size()>MAX_POINTS||endpoints.size()%2!=0)throw new IllegalArgumentException("Invalid mark outline");
        endpoints=List.copyOf(endpoints);
    }

    public static List<Vec3> box(Vec3 min, Vec3 max, UnaryOperator<Vec3> transform){
        var points=new ArrayList<Vec3>(24);
        for(int corner=0;corner<8;corner++){
            var from=transform.apply(corner(min,max,corner));
            for(int axis=1;axis<=4;axis<<=1)if((corner&axis)==0){
                points.add(from);points.add(transform.apply(corner(min,max,corner|axis)));
            }
        }
        return points;
    }

    private static Vec3 corner(Vec3 min,Vec3 max,int corner){
        return new Vec3((corner&1)==0?min.x:max.x,(corner&2)==0?min.y:max.y,(corner&4)==0?min.z:max.z);
    }

    public static void send(ServerPlayer player,Kind kind,int color,List<Vec3> endpoints){
        PacketDistributor.sendToPlayer(player,new MarkOutline(player.level().dimension().location(),kind,color,endpoints));
    }

    @Override public Type<MarkOutline> type(){return TYPE;}
}
