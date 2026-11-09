package dev.sablelife.module;

import dev.sablelife.SableLife;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record LaserVisual(UUID id,UUID body,Vec3 start,Vec3 localStart,float width,int rgb,List<End> ends) implements CustomPacketPayload {
    public record End(Vec3 point,boolean local){}
    /** Do not blend across groups, body changes or terrain-clipping coordinate changes. */
    public boolean interpolatesFrom(LaserVisual previous) {
        if (previous == null || !id.equals(previous.id) || !java.util.Objects.equals(body, previous.body)
                || ends.isEmpty() || ends.size() != previous.ends.size()) return false;
        for (int i = 0; i < ends.size(); i++) if (ends.get(i).local != previous.ends.get(i).local) return false;
        return true;
    }
    public static final Type<LaserVisual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"laser_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf,LaserVisual> CODEC=StreamCodec.of((buf,visual)->{
        buf.writeUUID(visual.id);
        buf.writeVarInt(visual.ends.size());
        if(visual.ends.isEmpty())return;
        buf.writeBoolean(visual.body!=null);
        if(visual.body!=null){buf.writeUUID(visual.body);point(buf,visual.localStart);}
        point(buf,visual.start);
        buf.writeFloat(visual.width);buf.writeInt(visual.rgb);
        for(var end:visual.ends){point(buf,end.point);buf.writeBoolean(end.local);}
    },buf->{
        var id=buf.readUUID();
        int count=buf.readVarInt();
        if(count==0)return stop(id);
        if(count<0||count>64)throw new IllegalArgumentException("Invalid laser visual count");
        var body=buf.readBoolean()?buf.readUUID():null;
        var local=body==null?null:point(buf);
        var start=point(buf);
        float width=buf.readFloat();int rgb=buf.readInt();
        var ends=new ArrayList<End>(count);
        for(int i=0;i<count;i++)ends.add(new End(point(buf),buf.readBoolean()));
        return new LaserVisual(id,body,start,local,width,rgb,List.copyOf(ends));
    });
    public static LaserVisual stop(UUID id){return new LaserVisual(id,null,null,null,0,0,List.of());}
    private static void point(RegistryFriendlyByteBuf buf,Vec3 value){buf.writeDouble(value.x);buf.writeDouble(value.y);buf.writeDouble(value.z);}
    private static Vec3 point(RegistryFriendlyByteBuf buf){return new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble());}
    @Override public Type<LaserVisual> type(){return TYPE;}
}
