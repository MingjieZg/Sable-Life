package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.MovementPreview;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;

/** Bounded viewer-only block-shape outlines. No server scans, temporary blocks or entities. */
@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class MovementPreviewClient {
    private static final int MAX_SCAN=262144,MAX_EDGES=32768,SCAN_PER_TICK=8192;
    private record Section(LevelChunkSection data,int x,int y,int z){}
    private static final class Mesh {
        final ClientSubLevel body;final List<Section> sections=new ArrayList<>();final List<Vec3> edges=new ArrayList<>();
        int section,cell;boolean done;
        Mesh(ClientSubLevel body){
            this.body=body;
            for(var holder:body.getPlot().getLoadedChunks()){
                var chunk=holder.getChunk();var all=chunk.getSections();
                for(int i=0;i<all.length;i++)if(!all[i].hasOnlyAir())sections.add(new Section(all[i],chunk.getPos().getMinBlockX(),chunk.getSectionYFromSectionIndex(i)<<4,chunk.getPos().getMinBlockZ()));
            }
        }
    }
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static MovementPreview preview;
    private static final Map<UUID,Mesh> meshes=new LinkedHashMap<>();
    private static long expires,built;
    private static int scanned,edgeCount;
    public static void receive(MovementPreview packet){
        var level=Minecraft.getInstance().level;if(level==null||!level.dimension().location().equals(packet.dimension()))return;
        if(world!=level||preview==null||!preview.bodies().equals(packet.bodies())){meshes.clear();scanned=0;edgeCount=0;built=level.getGameTime();}
        world=level;preview=packet;expires=level.getGameTime()+6;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var level=Minecraft.getInstance().level;
        if(preview==null)return;
        if(level!=world||level==null||level.getGameTime()>expires){preview=null;meshes.clear();world=null;return;}
        // Refresh changing block shapes, retaining completed geometry while the next scan is due.
        if(level.getGameTime()-built>=20&&meshes.values().stream().allMatch(m->m.done)){
            meshes.clear();scanned=0;edgeCount=0;built=level.getGameTime();
        }
        var container=SubLevelContainer.getContainer(level);if(container==null)return;int work=0;
        for(var id:preview.bodies()){
            var body=container.getSubLevel(id);if(!(body instanceof ClientSubLevel client)||body.isRemoved())continue;
            var mesh=meshes.computeIfAbsent(id,ignored->new Mesh(client));
            while(!mesh.done&&work<SCAN_PER_TICK&&scanned<MAX_SCAN&&edgeCount<MAX_EDGES){
                if(mesh.section>=mesh.sections.size()){mesh.done=true;break;}
                var section=mesh.sections.get(mesh.section);int n=mesh.cell++,x=n&15,z=n>>4&15,y=n>>8;
                var state=section.data.getBlockState(x,y,z);work++;scanned++;
                if(!state.isAir()){
                    var pos=new BlockPos(section.x+x,section.y+y,section.z+z);
                    try{
                        var shape=state.getShape(level,pos);if(shape.isEmpty())shape=net.minecraft.world.phys.shapes.Shapes.block();
                        shape.forAllEdges((x0,y0,z0,x1,y1,z1)->{
                            if(edgeCount>=MAX_EDGES)return;
                            mesh.edges.add(new Vec3(pos.getX()+x0,pos.getY()+y0,pos.getZ()+z0));mesh.edges.add(new Vec3(pos.getX()+x1,pos.getY()+y1,pos.getZ()+z1));edgeCount++;
                        });
                    }catch(RuntimeException ignored){/* An unsupported mod shape does not break the editor. */}
                }
                if(mesh.cell==4096){mesh.cell=0;mesh.section++;}
            }
            if(mesh.section>=mesh.sections.size())mesh.done=true;
            if(work>=SCAN_PER_TICK||scanned>=MAX_SCAN||edgeCount>=MAX_EDGES)break;
        }
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||preview==null||Minecraft.getInstance().level!=world)return;
        var buffers=Minecraft.getInstance().renderBuffers().bufferSource();var lines=buffers.getBuffer(PreviewLineStyle.worldLines());
        var camera=event.getCamera().getPosition();int color=preview.clear()?0x33FF33:0xFF1A33;
        var container=SubLevelContainer.getContainer(world);if(container==null)return;
        for(var id:preview.bodies()){
            var body=container.getSubLevel(id);if(!(body instanceof ClientSubLevel client)||body.isRemoved())continue;
            var mesh=meshes.get(id);
            if(mesh==null||!mesh.done){
                var box=body.boundingBox();
                var edges=dev.sablelife.module.MarkOutline.box(new Vec3(box.minX(),box.minY(),box.minZ()),new Vec3(box.maxX(),box.maxY(),box.maxZ()),p->p.add(preview.offset()).subtract(camera));
                for(int i=0;i<edges.size();i+=2)BlueprintPreviewClient.line(event.getPoseStack(),lines,edges.get(i),edges.get(i+1),color);
            }
            if(mesh==null)continue;var transform=client.renderPose(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            for(int i=0;i<mesh.edges.size();i+=2){
                var a=transform.transformPosition(mesh.edges.get(i)).add(preview.offset()).subtract(camera);
                var b=transform.transformPosition(mesh.edges.get(i+1)).add(preview.offset()).subtract(camera);
                if(a.distanceToSqr(b)>1e-12)BlueprintPreviewClient.line(event.getPoseStack(),lines,a,b,color);
            }
        }
        buffers.endBatch(PreviewLineStyle.worldLines());
    }
    private MovementPreviewClient(){}
}
