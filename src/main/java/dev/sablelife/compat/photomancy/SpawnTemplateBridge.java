package dev.sablelife.compat.photomancy;

import java.util.*;
import java.nio.file.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.ownership.ArenaRespawnLimits;
import dev.rew1nd.sableschematicapi.blueprint.*;

/** Only server-local operator-selected files enter the native placer. Optional API references stay isolated. */
public final class SpawnTemplateBridge {
    public static dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan plan(SableBlueprint blueprint,net.minecraft.world.phys.Vec3 center,org.joml.Quaterniond rotation){
        if(rotation==null)return dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan.legacy(blueprint,center);
        var identity=dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan.forPose(blueprint,new dev.ryanhcode.sable.companion.math.Pose3d());
        var basis=blueprint.subLevels().stream().filter(p->p.id()==identity.basisSubLevelId()).findFirst().orElseThrow();
        var pose=new dev.ryanhcode.sable.companion.math.Pose3d(new org.joml.Vector3d(center.x,center.y,center.z),new org.joml.Quaterniond(rotation),new org.joml.Vector3d(),new org.joml.Vector3d(basis.relativePose().scale()));
        return dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan.forPose(blueprint,pose);
    }
    private static List<net.minecraft.world.phys.Vec3> corners(SableBlueprint blueprint,dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan plan){
        var result=new ArrayList<net.minecraft.world.phys.Vec3>();
        for(var part:blueprint.subLevels()){
            var bounds=part.anchorBounds();var pose=plan.pose(part);
            for(double x:new double[]{bounds.minX(),bounds.maxX()})for(double y:new double[]{bounds.minY(),bounds.maxY()})for(double z:new double[]{bounds.minZ(),bounds.maxZ()})result.add(pose.transformPosition(new net.minecraft.world.phys.Vec3(x,y,z)));
        }
        return List.copyOf(result);
    }
    public static net.minecraft.world.phys.AABB bounds(SableBlueprint blueprint,dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan plan){
        net.minecraft.world.phys.AABB result=null;for(var point:corners(blueprint,plan)){var box=new net.minecraft.world.phys.AABB(point,point);result=result==null?box:result.minmax(box);}if(result==null)throw new IllegalArgumentException("empty_bounds");return result;
    }
    public static dev.sablelife.ownership.BlueprintSpawnPlacement.Geometry geometry(CompoundTag template){
        var blueprint=SableBlueprint.load(template);var zero=net.minecraft.world.phys.Vec3.ZERO;
        var basisPlan=dev.rew1nd.sableschematicapi.survival.BlueprintPlacementPlan.forPose(blueprint,new dev.ryanhcode.sable.companion.math.Pose3d());
        var basis=blueprint.subLevels().stream().filter(p->p.id()==basisPlan.basisSubLevelId()).findFirst().orElseThrow();
        var fixedForward=dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(basis.relativePose().orientation()),new net.minecraft.world.phys.Vec3(0,0,1));
        return new dev.sablelife.ownership.BlueprintSpawnPlacement.Geometry(corners(blueprint,plan(blueprint,zero,null)),corners(blueprint,plan(blueprint,zero,new org.joml.Quaterniond())),fixedForward);
    }
    public record Info(CompoundTag template,int life,int redstone,int blocks,int parts){
        public boolean canActivate(){return life==1&&redstone==0;}
        public boolean canProject(){return blocks<=SableLifeServerConfig.maximumBlueprintProjectionBlocks()&&parts<=dev.sablelife.module.BlueprintProjection.MAX_PARTS;}
    }
    public static dev.sablelife.module.BlueprintProjection projection(CompoundTag template,UUID id,boolean rotated,net.minecraft.world.phys.Vec3 offset){
        var blueprint=SableBlueprint.load(template);
        var plan=plan(blueprint,net.minecraft.world.phys.Vec3.ZERO,rotated?new org.joml.Quaterniond():null);
        var parts=new ArrayList<dev.sablelife.module.BlueprintProjection.Part>();
        for(var part:blueprint.subLevels()){
            var pose=plan.pose(part);
            var anchor=part.anchorBounds();
            var local=part.localBounds();
            var origin=pose.transformPosition(new net.minecraft.world.phys.Vec3(anchor.minX()-local.minX(),anchor.minY()-local.minY(),anchor.minZ()-local.minZ()));
            var blocks=new ArrayList<dev.sablelife.module.BlueprintProjection.Block>();
            for(var block:part.blocks()){
                var state=part.blockPalette().get(block.paletteId());
                if(state.isAir()||state.getRenderShape()!=net.minecraft.world.level.block.RenderShape.MODEL)continue;
                var pos=block.localPos();
                blocks.add(new dev.sablelife.module.BlueprintProjection.Block(net.minecraft.world.level.block.Block.getId(state),pos.getX(),pos.getY(),pos.getZ()));
            }
            if(!blocks.isEmpty()){
                var scale=pose.scale();
                parts.add(new dev.sablelife.module.BlueprintProjection.Part(origin.add(offset),new org.joml.Quaternionf(pose.orientation()),
                        new net.minecraft.world.phys.Vec3(scale.x(),scale.y(),scale.z()),List.copyOf(blocks)));
            }
        }
        return new dev.sablelife.module.BlueprintProjection(id,List.copyOf(parts));
    }
    public static dev.sablelife.heart.TerrainBreakSettings terrainSettings(CompoundTag template) {
        var settings=new dev.sablelife.heart.TerrainBreakSettings();
        for(var part:SableBlueprint.load(template).subLevels())for(var blockEntity:part.blockEntities()){
            if(blockEntity.getString("id").equals("sablelife:heart")){
                settings.load(blockEntity.getCompound("TerrainBreaking"));
                return settings;
            }
        }
        return settings;
    }
    public static List<net.minecraft.world.phys.AABB> occupiedBounds(CompoundTag template) {
        var blueprint=SableBlueprint.load(template);
        var plan=plan(blueprint,net.minecraft.world.phys.Vec3.ZERO,null);
        var result=new ArrayList<net.minecraft.world.phys.AABB>();
        for(var part:blueprint.subLevels()){
            var pose=plan.pose(part);
            var anchor=part.anchorBounds();
            var local=part.localBounds();
            double dx=anchor.minX()-local.minX(),dy=anchor.minY()-local.minY(),dz=anchor.minZ()-local.minZ();
            for(var block:part.blocks()){
                if(part.blockPalette().get(block.paletteId()).isAir())continue;
                var pos=block.localPos();
                net.minecraft.world.phys.AABB bounds=null;
                for(int corner=0;corner<8;corner++){
                    var p=pose.transformPosition(new net.minecraft.world.phys.Vec3(pos.getX()+dx+(corner&1),
                            pos.getY()+dy+((corner>>1)&1),pos.getZ()+dz+((corner>>2)&1)));
                    var point=new net.minecraft.world.phys.AABB(p,p);
                    bounds=bounds==null?point:bounds.minmax(point);
                }
                result.add(bounds);
            }
        }
        return List.copyOf(result);
    }
    public static List<String> files(MinecraftServer server){try{return SableBlueprintRootFiles.list(server).stream().filter(n->n.length()<=80).sorted().limit(128).toList();}catch(java.io.IOException e){return List.of();}}
    private static Path existingFile(MinecraftServer server,String name)throws java.io.IOException{var root=SableBlueprintRootFiles.directory(server).toAbsolutePath().normalize();var file=SableBlueprintRootFiles.path(server,name);if(!Files.isRegularFile(file)||Files.isSymbolicLink(file)||!file.toRealPath().startsWith(root.toRealPath()))throw new java.io.IOException("Invalid blueprint file");return file;}
    public static void delete(MinecraftServer server,String name)throws java.io.IOException{Files.delete(existingFile(server,name));}
    public static Info read(MinecraftServer server,String name)throws java.io.IOException{var file=existingFile(server,name);ArenaRespawnLimits.check("template",Files.size(file),SableLifeServerConfig.arenaTemplateBytes());try(var stream=Files.newInputStream(file)){return inspect(NbtIo.readCompressed(stream,NbtAccounter.create(SableLifeServerConfig.arenaTemplateBytes())));}}
    public static Info readWild(MinecraftServer server,String name)throws java.io.IOException{
        var file=existingFile(server,name);int bytes=SableLifeServerConfig.maximumWildBlueprintBytes();
        if(Files.size(file)>bytes)throw new IllegalArgumentException("Wild blueprint file exceeds server data limit of "+bytes+" bytes");
        try(var stream=Files.newInputStream(file)){return inspect(NbtIo.readCompressed(stream,NbtAccounter.create(bytes)),SableLifeServerConfig.maximumWildBlueprintBlocks(),bytes,SableLifeServerConfig.maximumWildBlueprintParts());}
        catch(ArenaRespawnLimits.Exceeded failure){throw new IllegalArgumentException("Wild blueprint "+failure.kind+" limit exceeded: "+failure.actual+" > "+failure.maximum,failure);}
    }
    public static Info inspect(CompoundTag tag){return inspect(tag,SableLifeServerConfig.arenaCaptureBlocks(),SableLifeServerConfig.arenaTemplateBytes(),ArenaRespawnLimits.PARTS);}
    private static Info inspect(CompoundTag tag,int maximumBlocks,int maximumBytes,int maximumParts){if(tag==null)throw new IllegalArgumentException("empty");ArenaRespawnLimits.check("template",tag.sizeInBytes(),maximumBytes);var decoded=SableBlueprint.loadWithDiagnostics(tag);if(!decoded.diagnostics().isEmpty())throw new IllegalArgumentException("invalid_template");var b=decoded.blueprint();if(b.isEmpty()||b.blockCount()==0)throw new IllegalArgumentException("empty");ArenaRespawnLimits.check("parts",b.subLevels().size(),maximumParts);ArenaRespawnLimits.check("blocks",b.blockCount(),maximumBlocks);ArenaRespawnLimits.check("block_entities",b.blockEntityCount(),SableLifeServerConfig.arenaCaptureBlockEntities());ArenaRespawnLimits.check("entities",b.entityCount(),ArenaRespawnLimits.ENTITIES);int life=0,redstone=0;for(var part:b.subLevels())for(var block:part.blocks()){var state=part.blockPalette().get(block.paletteId());if(state.is(SableLife.HEART.get()))life++;if(state.is(SableLife.REDSTONE_CORE.get()))redstone++;}return new Info(b.save(),life,redstone,b.blockCount(),b.subLevels().size());}
    private SpawnTemplateBridge(){}
}
