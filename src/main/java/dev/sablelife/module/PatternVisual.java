package dev.sablelife.module;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A bounded cosmetic birth. Following uses the already synchronized Sable render pose. */
public record PatternVisual(ResourceLocation dimension,UUID id,ModuleAnchor anchor,Vec3 point,Vec3 normal,Vec3 up,
        float startDistance,float size,float speed,int duration,int rgb,int pattern,int symbol,boolean follow,float range)
        implements CustomPacketPayload {
    public static final List<String> SYMBOLS=List.of("!","?","!!","?!","+","...");
    public static final int PATTERNS=7,MAX_EFFECTS=32;
    public static final Type<PatternVisual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"pattern_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PatternVisual> CODEC=StreamCodec.of((b,p)->{
        b.writeResourceLocation(p.dimension);b.writeUUID(p.id);b.writeUUID(p.anchor.bodyId());
        b.writeBlockPos(p.anchor.offset());b.writeEnum(p.anchor.face());point(b,p.point);point(b,p.normal);point(b,p.up);
        b.writeFloat(p.startDistance);b.writeFloat(p.size);b.writeFloat(p.speed);b.writeVarInt(p.duration);
        b.writeInt(p.rgb);b.writeVarInt(p.pattern);b.writeVarInt(p.symbol);b.writeBoolean(p.follow);b.writeFloat(p.range);
    },b->new PatternVisual(b.readResourceLocation(),b.readUUID(),new ModuleAnchor(b.readUUID(),b.readBlockPos(),b.readEnum(Direction.class)),
            point(b),point(b),point(b),b.readFloat(),b.readFloat(),b.readFloat(),b.readVarInt(),b.readInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readFloat()));
    private static void point(RegistryFriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
    private static Vec3 point(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public boolean valid(){
        return dimension!=null&&id!=null&&anchor!=null&&anchor.bodyId()!=null&&anchor.face()!=null
                &&Math.abs((long)anchor.offset().getX())<=4096&&Math.abs((long)anchor.offset().getY())<=4096&&Math.abs((long)anchor.offset().getZ())<=4096
                &&point!=null&&Double.isFinite(point.lengthSqr())&&Math.abs(point.x)<=30000000&&Math.abs(point.z)<=30000000&&Math.abs(point.y)<=30000000
                &&normal!=null&&Double.isFinite(normal.lengthSqr())&&normal.lengthSqr()>.9&&normal.lengthSqr()<1.1
                &&up!=null&&Double.isFinite(up.lengthSqr())&&up.lengthSqr()>.9&&up.lengthSqr()<1.1&&Math.abs(up.dot(normal))<.01
                &&Float.isFinite(startDistance)&&startDistance>=0&&startDistance<=128
                &&Float.isFinite(size)&&size>=.1&&size<=32&&Float.isFinite(speed)&&speed>=.1&&speed<=8
                &&duration>=1&&duration<=1200&&pattern>=0&&pattern<PATTERNS&&symbol>=0&&symbol<SYMBOLS.size()
                &&rgb>=0&&rgb<=0xFFFFFF&&Float.isFinite(range)&&range>=1&&range<=256;
    }
    public static PatternVisual create(HeartBlockEntity core,VirtualModule module,Vec3 contact){
        var anchor=module.anchors().getFirst();var body=anchor.body(core);
        var local=ModuleHealthBar.localPoint(anchor,body,module.value("visualStart"));
        var point=body.logicalPose().transformPosition(local);
        var normal=body.logicalPose().transformPosition(local.add(Vec3.atLowerCornerOf(anchor.face().getNormal()))).subtract(point).normalize();
        var localNormal=Vec3.atLowerCornerOf(anchor.face().getNormal());
        var localRight=localNormal.cross(Math.abs(localNormal.y)>.9?new Vec3(0,0,1):new Vec3(0,1,0)).normalize();
        var up=body.logicalPose().transformPosition(local.add(localNormal.cross(localRight))).subtract(point).normalize();
        var color=ModuleColors.vector(module,"visualColor");
        int rgb=(Math.round(color.x*255)<<16)|(Math.round(color.y*255)<<8)|Math.round(color.z*255);
        return new PatternVisual(core.getLevel().dimension().location(),UUID.randomUUID(),anchor,
                contact==null?point:contact.add(normal.scale(.025)),normal,up,(float)module.value("visualStart"),
                (float)module.value("visualSize"),(float)module.value("visualSpeed"),(int)module.value("visualTime"),rgb,
                (int)module.value("shockPattern")-2,(int)module.value("visualSymbol"),contact==null&&module.value("visualPlacement")==0,(float)module.value("shockRange"));
    }
    @Override public Type<PatternVisual> type(){return TYPE;}
}
