package dev.sablelife.ownership;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.util.TriState;
/** Right-click author policy is independent of block destruction and life-to-life damage. */
public final class BodyInteraction {
 private record Click(BlockPos pos,long tick,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension){}
 private record OpenMenu(BlockPos pos,net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,int containerId){}
 private static final java.util.Map<Player,Click> clicks=new java.util.WeakHashMap<>();
 private static final java.util.Map<Player,OpenMenu> menus=new java.util.WeakHashMap<>();
 public static void opened(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open event){
  var player=event.getEntity();if(!(player.level() instanceof ServerLevel level))return;
  var click=clicks.remove(player);if(click!=null&&click.dimension.equals(level.dimension())&&level.getGameTime()-click.tick<=2){
   menus.put(player,new OpenMenu(click.pos,click.dimension,event.getContainer().containerId));if(!allowed(player,click.pos))player.closeContainer();
  }
 }
 public static void closed(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Close event){if(event.getEntity().level() instanceof ServerLevel)menus.remove(event.getEntity());}
 public static boolean openMenuAllowed(Player player){
  var menu=menus.get(player);if(menu==null)return true;
  if(menu.containerId!=player.containerMenu.containerId||!menu.dimension.equals(player.level().dimension())){menus.remove(player);return true;}
  return allowed(player,menu.pos);
 }
 public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){var player=event.getEntity();if(player instanceof net.minecraft.server.level.ServerPlayer&&!openMenuAllowed(player))player.closeContainer();}

 private record ModuleClick(Player actor,BlockPos pos){}
 private static final ThreadLocal<ModuleClick> moduleClick=new ThreadLocal<>();
 public static void useOwnModule(HeartBlockEntity core,Player actor,BlockPos pos,Runnable action){
  var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(core.getLevel(),pos);
  if(!(actor instanceof net.neoforged.neoforge.common.util.FakePlayer)||actor.level()!=core.getLevel()
      ||!core.operational()||body==null
      ||!SableBodyProbe.connected(core.getLevel(),core.getBlockPos(),body.getUniqueId()))return;
  var previous=moduleClick.get();
  moduleClick.set(new ModuleClick(actor,pos.immutable()));
  try{action.run();}finally{if(previous==null)moduleClick.remove();else moduleClick.set(previous);}
 }

 public static boolean allowed(Player player,BlockPos pos){
  var use=moduleClick.get();
  if(use!=null&&use.actor()==player&&use.pos().equals(pos))return true;
  if(player.isCreative())return true;
  if(player.isSpectator())return false;
  var level=player.level();
  if(level instanceof ServerLevel server){
   var owner=BodyOwnership.get(server).owner(pos);
   if(owner!=null){var core=BodyOwnership.get(server).loadedHeart(server,owner);return core!=null?core.allowSurvivalInteraction():LifeLifecycle.get(server.getServer()).interactionAllowed(owner);}
  }
  return SableBodyProbe.findConnectedHearts(level,pos).stream().noneMatch(core->!core.isControlCore()&&core.health().state()!=HeartState.DORMANT&&!core.allowSurvivalInteraction());
 }
 public static boolean entityAllowed(Player player,net.minecraft.world.entity.Entity target){
  if(target instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity contraption){
   var data=contraption.getContraption();if(data!=null&&data.anchor!=null&&!allowed(player,data.anchor))return false;
   return allowed(player,target.blockPosition());
  }
  if(target instanceof net.minecraft.world.entity.decoration.ItemFrame frame)return allowed(player,frame.getPos().relative(frame.getDirection().getOpposite()));
  return true;
 }
 public static void entityClick(PlayerInteractEvent.EntityInteract event){if(!event.isCanceled()&&!entityAllowed(event.getEntity(),event.getTarget())){event.setCancellationResult(net.minecraft.world.InteractionResult.PASS);event.setCanceled(true);}}
 public static void entityClickSpecific(PlayerInteractEvent.EntityInteractSpecific event){if(!event.isCanceled()&&!entityAllowed(event.getEntity(),event.getTarget())){event.setCancellationResult(net.minecraft.world.InteractionResult.PASS);event.setCanceled(true);}}
 public static void rightClick(PlayerInteractEvent.RightClickBlock event){
  if(event.isCanceled()||event.getEntity().isSpectator())return;
  if(allowed(event.getEntity(),event.getPos())){
   if(event.getEntity().level() instanceof ServerLevel level){
    if(!(event.getEntity().isSecondaryUseActive()&&(!event.getEntity().getMainHandItem().isEmpty()||!event.getEntity().getOffhandItem().isEmpty()))&&level.getBlockState(event.getPos()).getMenuProvider(level,event.getPos())!=null)clicks.put(event.getEntity(),new Click(event.getPos().immutable(),level.getGameTime(),level.dimension()));
    else clicks.remove(event.getEntity());
   }
   return;
  }
  clicks.remove(event.getEntity());
  event.setUseBlock(TriState.FALSE);event.setUseItem(TriState.FALSE);event.setCancellationResult(net.minecraft.world.InteractionResult.PASS);event.setCanceled(true);
  // Retain inspection of the Life Core itself without executing its interactive block callbacks.
  if(!event.getLevel().isClientSide&&!event.getEntity().isShiftKeyDown()&&event.getLevel().getBlockEntity(event.getPos()) instanceof HeartBlockEntity core&&!core.isControlCore()){
   var player = event.getEntity();
   player.displayClientMessage(core.status(),false);player.displayClientMessage(core.interactionHint(),true);
   if (event.getItemStack().isEmpty() && core.canAccessEditor(player)) core.openLastEditor(player);
  }
 }
 private BodyInteraction(){}
}
