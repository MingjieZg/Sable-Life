package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.ownership.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.event.entity.player.*;
import java.util.*;
@GameTestHolder("sablelife_control_tests") @PrefixGameTestTemplate(false)
public final class BodyInteractionGameTests {
 private record Setup(HeartBlockEntity core,BlockPos lever,net.neoforged.neoforge.common.util.FakePlayer player){}
 private static Setup setup(GameTestHelper h){
  var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,40,4));var lever=pos.east().above();
  level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());level.setBlockAndUpdate(pos.west(),Blocks.CHEST.defaultBlockState());level.setBlockAndUpdate(lever,Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE,AttachFace.FLOOR));
  var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east(),lever,pos.west()),new BoundingBox3i(pos.getX()-1,pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+1,pos.getZ()));
  var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(1000,60);core.tryActivate(TestActors.creative(level));
  var actor=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"InteractionTester")){
   @Override public boolean canInteractWithBlock(BlockPos target,double padding){return true;}
   // FakePlayer intentionally suppresses menus; exercise the native provider and container-open event.
   @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extra){
    var menu=provider.createMenu(4,getInventory(),this);if(menu==null)return java.util.OptionalInt.empty();containerMenu=menu;
    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Open(this,menu));return java.util.OptionalInt.of(menu.containerId);
   }
  };actor.setGameMode(GameType.SURVIVAL);actor.setPos(core.getBlockPos().getCenter());return new Setup(core,core.getBlockPos().east().above(),actor);
 }
 private static PlayerInteractEvent.RightClickBlock click(Setup s,ItemStack item){s.player.setItemInHand(InteractionHand.MAIN_HAND,item);return new PlayerInteractEvent.RightClickBlock(s.player,InteractionHand.MAIN_HAND,s.lever,new BlockHitResult(s.lever.getCenter(),Direction.UP,s.lever,false));}
 @GameTest(template="combat",batch="body_interaction_policy")
 public static void ClosedInteractionBlocksHandsToolsAndSamplesButAllowsAuthorsAndToggle(GameTestHelper h){
  var s=setup(h);var level=h.getLevel();h.assertTrue(!s.core.allowSurvivalInteraction(),"Interaction must default closed");
  var wrench=com.simibubi.create.AllItems.WRENCH.asStack();
  for(var item:List.of(ItemStack.EMPTY,wrench,new ItemStack(Items.STICK),new ItemStack(Items.STONE))){var event=click(s,item);BodyInteraction.rightClick(event);h.assertTrue(event.isCanceled()&&event.getUseBlock()==net.neoforged.neoforge.common.util.TriState.FALSE&&event.getUseItem()==net.neoforged.neoforge.common.util.TriState.FALSE,"Survival interaction bypassed with "+item);}
  s.player.setGameMode(GameType.ADVENTURE);var adventure=click(s,ItemStack.EMPTY);BodyInteraction.rightClick(adventure);h.assertTrue(adventure.isCanceled(),"Adventure bypassed device lock");
  s.player.setGameMode(GameType.CREATIVE);var author=click(s,wrench);BodyInteraction.rightClick(author);h.assertTrue(!author.isCanceled(),"Creative author was blocked");
  s.player.setGameMode(GameType.SURVIVAL);h.assertTrue(!s.core.editCore(s.player,"survivalInteraction",1,""),"Survival changed its own permission");
  var admin=TestActors.creative(level);h.assertTrue(!s.core.editCore(admin,"survivalInteraction",Double.NaN,"")&&!s.core.editCore(admin,"survivalInteraction",2,""),"Invalid switch value accepted");
  h.assertTrue(s.core.editCore(admin,"survivalInteraction",1,""),"Author could not enable device use");var enabled=click(s,ItemStack.EMPTY);BodyInteraction.rightClick(enabled);h.assertTrue(!enabled.isCanceled(),"Enabled interaction still blocked");
  var block=click(s,new ItemStack(Items.STONE));dev.sablelife.combat.BodyCombat.onRightClickBlock(block);h.assertTrue(!block.isCanceled()&&block.getUseItem()==net.neoforged.neoforge.common.util.TriState.FALSE,"Device enable changed block placement rule");
  h.assertTrue(s.core.copyConfiguration().getBoolean("AllowSurvivalInteraction"),"Copy/blueprint configuration lost permission");var saved=s.core.saveWithFullMetadata(level.registryAccess());var copy=new HeartBlockEntity(s.core.getBlockPos(),s.core.getBlockState());copy.setLevel(level);copy.loadWithComponents(saved,level.registryAccess());h.assertTrue(copy.allowSurvivalInteraction(),"Core reload lost interaction switch");
  var lifecycle=LifeLifecycle.get(level.getServer());h.assertTrue(lifecycle.interactionAllowed(s.core.creatureId())&&LifeLifecycle.load(lifecycle.save(new CompoundTag(),level.registryAccess()),level.registryAccess()).interactionAllowed(s.core.creatureId()),"Unloaded-core policy did not survive save");
  h.assertTrue(s.core.editCore(admin,"survivalInteraction",0,"")&&!BodyInteraction.allowed(s.player,s.lever),"Closing switch did not relock");s.core.resetForCreative(admin);h.assertTrue(BodyInteraction.allowed(s.player,s.lever),"Released structure remained locked");h.succeed();
 }
 @GameTest(template="combat",batch="body_interaction_native")
 public static void NativeLeverAndCreateConfigurationFollowInteractionSwitch(GameTestHelper h){
  var s=setup(h);var level=h.getLevel();var hit=new BlockHitResult(s.lever.getCenter(),Direction.UP,s.lever,false);s.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
  s.player.gameMode.useItemOn(s.player,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);h.assertTrue(!level.getBlockState(s.lever).getValue(LeverBlock.POWERED),"Native right click operated protected lever");
  var calls=new int[]{0};var packet=new com.simibubi.create.foundation.networking.BlockEntityConfigurationPacket<com.simibubi.create.foundation.blockEntity.SyncedBlockEntity>(s.core.getBlockPos()){
   @Override protected void applySettings(net.minecraft.server.level.ServerPlayer player,com.simibubi.create.foundation.blockEntity.SyncedBlockEntity entity){calls[0]++;}
   @Override public net.createmod.catnip.net.base.BasePacketPayload.PacketTypeProvider getTypeProvider(){return null;}
  };
  packet.handle(s.player);h.assertTrue(calls[0]==0,"Direct Create packet bypassed closed policy");
  var admin=TestActors.creative(level);h.assertTrue(s.core.editCore(admin,"survivalInteraction",1,""),"Could not open interaction");
  s.player.gameMode.useItemOn(s.player,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,hit);h.assertTrue(level.getBlockState(s.lever).getValue(LeverBlock.POWERED),"Enabled native lever still blocked");
  packet.handle(s.player);h.assertTrue(calls[0]==1,"Enabled Create configuration still blocked");
  var chest=s.core.getBlockPos().west();var chestHit=new BlockHitResult(chest.getCenter(),Direction.UP,chest,false);s.player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
  s.player.gameMode.useItemOn(s.player,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,chestHit);h.assertTrue(s.player.containerMenu!=s.player.inventoryMenu&&BodyInteraction.openMenuAllowed(s.player),"Enabled native chest did not open");
  h.assertTrue(s.core.editCore(admin,"survivalInteraction",0,"")&&!BodyInteraction.openMenuAllowed(s.player),"Already open menu kept access after closing switch");s.player.closeContainer();s.core.resetForCreative(admin);h.succeed();
 }
 @GameTest(template="combat",batch="body_contraption_interaction")
 public static void MovingCreateInteractionPacketAndFramesFollowCorePolicy(GameTestHelper h){
  var s=setup(h);var level=h.getLevel();var calls=new int[]{0};
  var assembly=new com.simibubi.create.content.contraptions.bearing.BearingContraption(false,Direction.UP);assembly.anchor=s.core.getBlockPos().east();assembly.bounds=new net.minecraft.world.phys.AABB(0,0,0,1,1,1);
  assembly.getBlocks().put(BlockPos.ZERO,new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo(BlockPos.ZERO,Blocks.IRON_BLOCK.defaultBlockState(),null));
  var entity=new com.simibubi.create.content.contraptions.ControlledContraptionEntity(com.simibubi.create.AllEntityTypes.CONTROLLED_CONTRAPTION.get(),level){
   {setContraption(assembly);}
   @Override public boolean handlePlayerInteraction(net.minecraft.world.entity.player.Player player,BlockPos pos,Direction side,InteractionHand hand){calls[0]++;return true;}
  };entity.setPos(s.core.getBlockPos().above().getCenter());level.addFreshEntity(entity);
  var packet=new com.simibubi.create.content.contraptions.sync.ContraptionInteractionPacket(entity,InteractionHand.MAIN_HAND,BlockPos.ZERO,Direction.UP);
  packet.handle(s.player);h.assertTrue(calls[0]==0,"Moving contraption packet bypassed closed policy");
  var support=s.core.getBlockPos().east();var frame=new net.minecraft.world.entity.decoration.ItemFrame(level,support.north(),Direction.NORTH);
  h.assertTrue(!BodyInteraction.entityAllowed(s.player,frame),"Frame modification bypassed body policy");
  var admin=TestActors.creative(level);h.assertTrue(s.core.editCore(admin,"survivalInteraction",1,""),"Cannot enable moving interaction");
  packet.handle(s.player);h.assertTrue(calls[0]==1&&BodyInteraction.entityAllowed(s.player,frame),"Enabled moving component/frame still blocked");
  entity.discard();s.core.resetForCreative(admin);h.succeed();
 }

}
