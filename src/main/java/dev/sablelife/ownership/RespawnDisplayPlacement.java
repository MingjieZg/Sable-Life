package dev.sablelife.ownership;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
/** Terrain-only display placement; does not alter the blueprint spawn anchor or load chunks. */
public final class RespawnDisplayPlacement {
 public static Vec3 locate(ServerLevel level,Vec3 anchor){
  int x=(int)Math.floor(anchor.x),z=(int)Math.floor(anchor.z);
  if(!level.hasChunk(x>>4,z>>4))return null;
  int top=Math.min(level.getMaxBuildHeight()-1,level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z)-1);
  Vec3 above=null;
  for(int y=top;y>=level.getMinBuildHeight();y--){
   var pos=new BlockPos(x,y,z);var state=level.getBlockState(pos);
   if(state.isAir()||!state.getFluidState().isEmpty())continue;
   if(!level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()||!level.getBlockState(pos.above(2)).getCollisionShape(level,pos.above(2)).isEmpty()||!level.getFluidState(pos.above()).isEmpty()||!level.getFluidState(pos.above(2)).isEmpty())continue;
   var shape=state.getCollisionShape(level,pos);double surface=Double.NEGATIVE_INFINITY;
   for(var box:shape.toAabbs())if(box.minX<=anchor.x-x&&box.maxX>=anchor.x-x&&box.minZ<=anchor.z-z&&box.maxZ>=anchor.z-z)surface=Math.max(surface,box.maxY);
   if(!Double.isFinite(surface))continue;
   var display=new Vec3(anchor.x,y+surface+2.5,anchor.z);
   if(y+surface<=anchor.y)return display;
   above=display; // Lowest standable surface above an underground anchor, if no surface exists below it.
  }
  return above==null?anchor.add(0,2.5,0):above;
 }
 private RespawnDisplayPlacement(){}
}
