package dev.sablelife.module;

import com.mojang.authlib.GameProfile;
import dev.ryanhcode.sable.Sable;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ModuleBlockInteraction {
    private static final GameProfile ACTOR=new GameProfile(UUID.fromString("16543ce7-37b3-4ccd-bbe8-786035308725"),"[SableLife]");
    private record Activation(long next,long move){}
    private final Map<UUID,Activation> active=new HashMap<>();

    void clear(){active.clear();}

    void tick(HeartBlockEntity core){
        if(!(core.getLevel() instanceof ServerLevel level))return;
        if(!core.wireless().master()||!core.operational()){clear();return;}
        active.keySet().removeIf(id->core.modules().find(id)==null);
        long now=level.getGameTime();
        for(var module:core.modules().all()){
            if(!module.type().equals("block_interaction"))continue;
            var id=module.id();
            if(!HeartModules.triggered(module,core)){active.remove(id);continue;}
            var previous=active.get(id);
            long move=module.value("trigger")==4?core.modules().moveActivation(id):Long.MIN_VALUE;
            if(previous!=null&&previous.move()==move&&(module.value("interactionMode")==0||now<previous.next()))continue;
            if(!core.modules().hurtTriggers.start(module))continue;
            active.put(id,new Activation(now+(long)module.value("interactionInterval"),move));
            interact(level,core,module);
            core.modules().hurtTriggers.completed(module);
        }
    }

    private record Area(Vec3 point, BlockPos min, BlockPos max, Direction face,
                        dev.ryanhcode.sable.sublevel.SubLevel body) {
        Vec3 world(Vec3 point){return body==null?point:body.logicalPose().transformPosition(point);}
    }

    private static Area area(HeartBlockEntity core, VirtualModule module) {
        var anchor=module.anchors().getFirst();
        var body=anchor.body(core);
        var normal=Vec3.atLowerCornerOf(anchor.face().getNormal());
        var pose=body.logicalPose();
        var local=anchor.position(body).getCenter().add(normal.scale(module.value("startDistance")))
                .add(module.value("interactionOffsetX"),module.value("interactionOffsetY"),module.value("interactionOffsetZ"));
        boolean structure=module.value("interactionSpace")==1;
        var point=structure?local:pose.transformPosition(local);
        var forward=pose.transformPosition(local.add(normal)).subtract(point);
        int size=(int)module.value("interactionSize");
        double shift=(size-1)*.5;
        var min=BlockPos.containing(point.subtract(shift,shift,shift));
        return new Area(point,min,min.offset(size-1,size-1,size-1),
                structure?anchor.face():Direction.getNearest(forward.x,forward.y,forward.z),structure?body:null);
    }

    static void preview(ServerPlayer player,HeartBlockEntity core,VirtualModule module){
        var area=area(core,module);
        var selected=target(player.serverLevel(),area);
        var min=Vec3.atLowerCornerOf(area.min()).subtract(.002,.002,.002);
        var max=Vec3.atLowerCornerOf(area.max()).add(1.002,1.002,1.002);
        MarkOutline.send(player,MarkOutline.Kind.VOLUME,MarkOutline.MARK_COLOR,
                MarkOutline.box(min,max,area::world));
        if(selected!=null){
            var corner=Vec3.atLowerCornerOf(selected);
            MarkOutline.send(player,MarkOutline.Kind.DETAIL,0xFFFFFF,
                    MarkOutline.box(corner.subtract(.003,.003,.003),corner.add(1.003,1.003,1.003),area::world));
        }
    }

    private static BlockPos target(ServerLevel level,Area area){
        BlockPos nearest=null;
        double best=Double.POSITIVE_INFINITY;
        for(var pos:BlockPos.betweenClosed(area.min(),area.max())){
            if(!level.hasChunkAt(pos)||area.body()==null&&!level.getWorldBorder().isWithinBounds(pos)
                    ||Sable.HELPER.getContaining(level,pos)!=area.body())continue;
            var block=level.getBlockState(pos).getBlock();
            if(!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft"))continue;
            if(block==Blocks.IRON_DOOR||block==Blocks.IRON_TRAPDOOR)continue;
            if(!(block instanceof DoorBlock||block instanceof TrapDoorBlock||block instanceof FenceGateBlock
                    ||block instanceof ButtonBlock||block instanceof LeverBlock))continue;
            double distance=pos.getCenter().distanceToSqr(area.point());
            if(distance<best){nearest=pos.immutable();best=distance;}
        }
        return nearest;
    }

    private static void interact(ServerLevel level,HeartBlockEntity core,VirtualModule module){
        var area=area(core,module);
        var pos=target(level,area);
        if(pos==null)return;
        var actor=FakePlayerFactory.get(level,ACTOR);
        if(!level.mayInteract(actor,pos))return;
        var face=area.face();
        actor.setPos(area.world(area.point()));
        actor.setYRot(face.getAxis().isHorizontal()?face.getOpposite().toYRot():0);
        var hit=pos.getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
        var result=new BlockHitResult(hit,face,pos,false);
        var event=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(
                actor,net.minecraft.world.InteractionHand.MAIN_HAND,pos,result);
        Runnable use=()->{
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
            if(!event.isCanceled()&&event.getUseBlock()!=net.neoforged.neoforge.common.util.TriState.FALSE)
                level.getBlockState(pos).useWithoutItem(level,actor,result);
        };
        if(area.body()!=null)dev.sablelife.ownership.BodyInteraction.useOwnModule(core,actor,pos,use);
        else use.run();
    }
}
