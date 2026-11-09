package dev.sablelife.testing;

import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.compat.photomancy.SpawnTemplateBridge;
import dev.sablelife.heart.FactionRegistry;
import dev.sablelife.ownership.BlueprintSpawnPlacement;
import dev.sablelife.ownership.WildEncounters;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.UUID;

/** Explicit local replay only: place the user's copied blueprint in the isolated run directory. */
@GameTestHolder("sablelife_valkyrie_preview_tests") @PrefixGameTestTemplate(false)
public final class ValkyrieNaturalPreviewGameTests {
    @GameTest(template="combat",timeoutTicks=1200)
    public static void RealValkyriePreviewRemainsAvailableWhenPlacementIsBlocked(GameTestHelper h)throws Exception{
        var level=h.getLevel();var server=level.getServer();String name="valkyrie shuttle";
        var info=SpawnTemplateBridge.readWild(server,name);
        var geometry=SpawnTemplateBridge.geometry(info.template());
        var basis=geometry.bounds(new BlueprintSpawnPlacement.Destination(level.dimension().location(),Vec3.ZERO,null));
        System.out.println("VALKYRIE_METADATA: blocks="+info.blocks()+" parts="+info.parts()+" life="+info.life()+" bounds="+basis+" projection="+info.canProject());
        var data=WildEncounters.get(server);data.loadBlueprint(level,"",name,false);
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ValkyriePreview"));
        player.setGameMode(GameType.CREATIVE);player.setYRot(0);player.setXRot(0);
        var at=h.absoluteVec(new Vec3(4,90,4));player.setPos(at.x,at.y,at.z);
        var conditions=new WildEncounters.Conditions(true,true,false,Set.of(),180);
        for(int gap:new int[]{0,32,128}){
            data.configure(level,name,gap,60,10,false,true,FactionRegistry.WILDS,conditions);
            var attempt=data.attemptPreviewOrSpawn(player,name,false);
            System.out.println("VALKYRIE_PLACEMENT: gap="+gap+" success="+attempt.success()+" reason="+attempt.reason());
            h.assertTrue(!attempt.success()&&!attempt.reason().isEmpty(),"Replay did not reproduce a blocked placement");
            var preview=data.attemptPreviewOrSpawn(player,name,true);
            h.assertTrue(preview.success()&&preview.reason().equals(attempt.reason()),"Blocked placement hid the read-only preview");
            h.assertTrue(data.snapshot(level,name).getInt("ActiveCount")==0,"Preview generated an object");
        }
        // Force a deterministic budget failure after loading the entire footprint, including when the preview is large.
        double distance=Math.max(24,Math.ceil(Math.hypot(basis.getXsize(),basis.getZsize())/2+8));
        double centerX=at.x,centerZ=at.z+distance;
        int minX=(int)Math.floor(Math.floor(centerX)+.5-basis.getXsize()/2),maxX=(int)Math.ceil(Math.floor(centerX)+.5+basis.getXsize()/2)-1;
        int minZ=(int)Math.floor(Math.floor(centerZ)+.5-basis.getZsize()/2),maxZ=(int)Math.ceil(Math.floor(centerZ)+.5+basis.getZsize()/2)-1;
        for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++)level.getChunk(cx,cz);
        data.configure(level,name,0,60,10,false,true,FactionRegistry.WILDS,new WildEncounters.Conditions(false,true,false,Set.of(),180));
        var scan=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)SableLifeServerConfig.SPEC.getValues().get("wildPlacementScanVoxels");int previous=scan.get();
        try{
            var loaded=data.attemptPreviewOrSpawn(player,name,true);
            System.out.println("VALKYRIE_LOADED_DEFAULT_PREVIEW: reason="+loaded.reason()+" scanBudget="+previous);
            h.assertTrue(loaded.success(),"Loaded real blueprint did not preview at the default scan budget");
            scan.set(262144);
            var attempt=data.attemptPreviewOrSpawn(player,name,false);
            System.out.println("VALKYRIE_LOADED_PLACEMENT: success="+attempt.success()+" reason="+attempt.reason());
            h.assertTrue(data.attemptPreviewOrSpawn(player,name,true).success(),"Loaded large blueprint could not preview");
        }finally{scan.set(previous);}
        var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        var before=container.getAllSubLevels().stream().map(body->body.getUniqueId()).collect(java.util.stream.Collectors.toSet());
        var nearby=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)SableLifeServerConfig.SPEC.getValues().get("maximumNearbyWildEncounters");int oldNearby=nearby.get();
        var water=net.minecraft.core.BlockPos.containing(centerX,level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,(int)Math.floor(centerX),(int)Math.floor(centerZ)),centerZ);
        level.setBlockAndUpdate(water,net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        try{
            nearby.set(64);
            data.configure(level,name,32,60,10,false,true,FactionRegistry.WILDS,new WildEncounters.Conditions(false,true,false,Set.of(),180));
            long start=System.nanoTime();var spawned=data.attemptPreviewOrSpawn(player,name,false);
            long placed=container.getAllSubLevels().stream().filter(body->!before.contains(body.getUniqueId())).count();
            System.out.println("VALKYRIE_ACTUAL_SPAWN: success="+spawned.success()+" reason="+spawned.reason()+" parts="+placed+" elapsedMs="+(System.nanoTime()-start)/1000000);
            h.assertTrue(spawned.success()&&placed==info.parts()&&data.snapshot(level,name).getInt("ActiveCount")==1,"Full Valkyrie did not actually spawn over water: "+spawned.reason());
            h.assertTrue(level.getBlockState(water).is(net.minecraft.world.level.block.Blocks.WATER),"Spawning changed the water below the Valkyrie");
        }finally{
            nearby.set(oldNearby);
            var placed=container.getAllSubLevels().stream().filter(body->!before.contains(body.getUniqueId())).toList();
            for(var body:placed)dev.sablelife.compat.StructureRemovalApi.remove(level,(dev.ryanhcode.sable.sublevel.ServerSubLevel)body);
            data.clearCurrent(level,name);
        }
        h.succeed();
    }
}
