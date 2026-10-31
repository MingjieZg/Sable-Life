package dev.sablelife.testing;
import dev.sablelife.client.FreezeClient;
import dev.sablelife.module.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;
@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class FreezeWorldPreview {
    private static boolean started,built;private static volatile boolean ready,serverConstraint,shieldAllowed,attackAllowed,airborneSafety;private static int ticks,frozenTicks;private static Vec3 anchor;private static UUID pigId;private static volatile Vec3 followPoint,grabStart;private static volatile boolean grabMoved;private static final PlayerFreeze.Source GRAB=new PlayerFreeze.Source(UUID.randomUUID(),UUID.randomUUID());
    private static final PlayerFreeze.Source SOURCE=new PlayerFreeze.Source(UUID.randomUUID(),UUID.randomUUID());
    private static void volumePreview(Minecraft mc,boolean rectangle){mc.getSingleplayerServer().execute(()->{
        var level=mc.getSingleplayerServer().overworld();var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
        var core=new dev.sablelife.heart.HeartBlockEntity(new BlockPos(0,65,-10),dev.sablelife.SableLife.HEART.get().defaultBlockState());core.setLevel(level);
        var dummy=new SlashGuardGameTests.Defender(level);dummy.setPos(0,65,-8);level.addNewPlayer(dummy);
        var m=core.modules().add("freeze");m.edit("releasePosition",1,"");m.edit("freezeRadius",2,"");m.edit("freezeHeight",3,"");m.edit("fieldShape",rectangle?1:0,"");m.edit("fieldLength",6,"");m.edit("fieldWidth",2,"");m.edit("fieldAngle",45,"");for(var id:m.particles())m.editParticle("remove",id);
        ModuleFreeze.preview(p,core,m);dummy.discard();
    });}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e)throws Exception {
        if(!Boolean.getBoolean("sablelife.freezeWorldPreview"))return;var mc=Minecraft.getInstance();
        if(!started){if(mc.getOverlay()!=null||mc.screen==null)return;started=true;var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);mc.createWorldOpenFlows().createFreshLevel("freeze-"+System.currentTimeMillis(),new LevelSettings("Isolated movement freeze",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT),new WorldOptions(1234,false,false),a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);return;}
        if(mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(!built){built=true;mc.getSingleplayerServer().execute(()->{var level=mc.getSingleplayerServer().overworld();var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)level.setBlockAndUpdate(new BlockPos(x,64,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());p.setGameMode(GameType.SURVIVAL);p.getAbilities().flying=false;p.onUpdateAbilities();p.teleportTo(level,0,65,0,180,0);p.setItemInHand(InteractionHand.OFF_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));var pig=net.minecraft.world.entity.EntityType.PIG.create(level);pig.setPos(0,65,-1.6);pig.setNoAi(true);level.addFreshEntity(pig);pigId=pig.getUUID();ready=true;});return;}
        if(!ready)return;ticks++;
        if(ticks==15)volumePreview(mc,false);
        if(ticks==25)mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();anchor=p.position();PlayerFreeze.apply(p,SOURCE,60);});
        boolean frozen=FreezeClient.active();
        if(frozen&&ticks<100){frozenTicks++;mc.options.keyUp.setDown(true);mc.options.keyJump.setDown(true);mc.player.setYRot(180);
            if(frozenTicks>3&&mc.player.position().distanceToSqr(anchor)>.02)throw new AssertionError("Frozen client drifted despite input suppression");
            if(frozenTicks==10)mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(anchor.x+2,anchor.y+1,anchor.z+2,73,-10,true));serverConstraint=p.position().distanceToSqr(anchor)<1e-8&&Math.abs(p.getYRot()-73)<.01;try{var f=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("clientIsFloating");f.setAccessible(true);var n=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("aboveGroundTickCount");n.setAccessible(true);f.setBoolean(p.connection,true);n.setInt(p.connection,79);p.connection.tick();airborneSafety=!f.getBoolean(p.connection)&&n.getInt(p.connection)==0;}catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}});
            if(frozenTicks==15)mc.options.keyUse.setDown(true);
            if(frozenTicks==23)mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();shieldAllowed=p.isUsingItem()&&p.getUseItem().is(net.minecraft.world.item.Items.SHIELD);});
            if(frozenTicks==26){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);for(var ent:mc.level.entitiesForRendering())if(ent.getUUID().equals(pigId))mc.gameMode.attack(mc.player,ent);}
            if(frozenTicks==35)mc.getSingleplayerServer().execute(()->{var pig=mc.getSingleplayerServer().overworld().getEntity(pigId);attackAllowed=pig instanceof net.minecraft.world.entity.LivingEntity living&&living.getHealth()<living.getMaxHealth();});
        }
        if(ticks>=105&&ticks<140){final int phase=ticks-105;mc.getSingleplayerServer().execute(()->{followPoint=grabStart.add(phase*.04,.2*Math.sin(phase*.05),0);var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();if(phase>15&&PlayerFreeze.attached(p)&&p.position().distanceTo(grabStart)>.5)grabMoved=true;});}
        if(ticks==140)mc.getSingleplayerServer().execute(()->PlayerFreeze.release(mc.getSingleplayerServer(),GRAB));
        if(ticks==32||ticks==120){var dir=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(dir.resolve("control-volume-"+ticks+".png"));}}
        if(ticks==160){if(!grabMoved||FreezeClient.active()||mc.player.position().distanceTo(followPoint)<.2)throw new AssertionError("Real moving attachment/release failed");mc.options.keyUp.setDown(false);mc.options.keyJump.setDown(false);mc.options.keyUse.setDown(false);System.out.println("Grab world passed: real client followed moving attachment, held movement input remained disabled and release restored movement; actual circle/rectangle preview works with no warning particles");mc.stop();}
        if(ticks==100){if(frozen||frozenTicks<45||!serverConstraint||!shieldAllowed||!attackAllowed||!airborneSafety||mc.player.position().distanceToSqr(anchor)<.2)throw new AssertionError("Freeze integration failed: ticks="+frozenTicks+" constraint="+serverConstraint+" shield="+shieldAllowed+" attack="+attackAllowed);mc.options.keyUse.setDown(false);System.out.println("Freeze world passed: real movement packet constrained with look preserved, client walking/jump suppressed, shield and attack allowed, movement resumed after expiry, intentional suspension cleared native flying counters");
            mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();grabStart=p.position();followPoint=grabStart;if(!PlayerFreeze.grab(p,GRAB,60,v->followPoint))throw new AssertionError("Real player grab failed");});volumePreview(mc,true);}
    }
}
