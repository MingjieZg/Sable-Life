package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.heart.FactionRegistry;
import dev.sablelife.ownership.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_workbench_tests")
@PrefixGameTestTemplate(false)
public final class NaturalWorkbenchRuntimeGameTests {
    @GameTest(template="combat",batch="workbench_automatic_runtime",timeoutTicks=600)
    public static void ActualAutomaticSpawnerRespectsWorldTablesAndIgnoresTablesInsideSable(GameTestHelper h)throws Exception{
        var level=h.getLevel();var server=level.getServer();int x=12288,z=12288;
        h.assertTrue(SableLifeServerConfig.wildWorkbenchProtectionRadius()==64,"This test requires the real default protection");
        for(int cx=(x>>4)-6;cx<=(x>>4)+6;cx++)for(int cz=(z>>4)-6;cz<=(z>>4)+6;cz++){level.setChunkForced(cx,cz,true);level.getChunk(cx,cz);}
        var source=new BlockPos(x+20,-40,z+20);level.setBlockAndUpdate(source,Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,source,List.of(source),new BoundingBox3i(source.getX(),source.getY(),source.getZ(),source.getX(),source.getY(),source.getZ()));
        var pose=body.logicalPose().position();
        var template=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintExporter.exportSelected(level,new Vec3(pose.x(),pose.y(),pose.z()),List.of(body.getUniqueId())).save();
        String name="automatic_workbench_"+UUID.randomUUID();var path=dev.rew1nd.sableschematicapi.blueprint.SableBlueprintRootFiles.path(server,name);
        java.nio.file.Files.createDirectories(path.getParent());net.minecraft.nbt.NbtIo.writeCompressed(template,path);
        var rules=new WildEncounters();rules.loadBlueprint(level,"",name,false);
        rules.configure(level,name,0,0,10,true,false,FactionRegistry.WILDS,
                new WildEncounters.Conditions(true,true,false,Set.of(),0,true),1,false,new WildEncounters.Density(1,8,32,32));
        var actor=TestActors.creative(level);actor.setNoGravity(true);actor.setPos(x+.5,-56,z+.5);level.addNewPlayer(actor);
        var table=new BlockPos(x,-59,z);level.setBlockAndUpdate(table,Blocks.CRAFTING_TABLE.defaultBlockState());
        var index=WildWorkbenchProtection.get(level);var sample=new AABB(x+31,-60,z,x+33,-59,z+1);
        var advance=WildEncounters.class.getDeclaredMethod("advance",net.minecraft.server.MinecraftServer.class);advance.setAccessible(true);
        var phase=new int[]{0};var retryAt=new long[]{0};
        h.onEachTick(()->{
            try{
                if(phase[0]==0){
                    if(index.statistics().get("pending")!=0)return;
                    h.assertTrue(WildWorkbenchProtection.check(level,sample).equals("workbench"),"World table failed to protect candidate footprint");
                    advance.invoke(rules,server);
                    h.assertTrue(rules.snapshot(level,name).getInt("ActiveCount")==0&&rules.snapshot(level,name).getBoolean("Enabled"),"Automatic spawn bypassed workbench or disabled its rule");
                    level.setBlockAndUpdate(table,Blocks.AIR.defaultBlockState());
                    var portable=new BlockPos(x+2,-55,z);level.setBlockAndUpdate(portable,Blocks.CRAFTING_TABLE.defaultBlockState());
                    var movingTable=SubLevelAssemblyHelper.assembleBlocks(level,portable,List.of(portable),new BoundingBox3i(portable.getX(),portable.getY(),portable.getZ(),portable.getX(),portable.getY(),portable.getZ()));
                    h.assertTrue(WildWorkbenchProtection.check(level,sample).isEmpty(),"Crafting table inside Sable still protected physical terrain");
                    retryAt[0]=level.getGameTime()+25;phase[0]=1;
                }else if(phase[0]==1&&level.getGameTime()>=retryAt[0]){
                    long reads=index.statistics().get("blockReads"),probes=index.statistics().get("sectionProbes");
                    advance.invoke(rules,server);
                    h.assertTrue(rules.snapshot(level,name).getInt("ActiveCount")==1,"Automatic spawning did not resume after world table removal");
                    h.assertTrue(index.statistics().get("blockReads")==reads&&index.statistics().get("sectionProbes")==probes,"Automatic candidate checks rescanned terrain");
                    var menu=new WildEncounterMenu(1,actor.getInventory(),actor);
                    h.assertTrue(menu.snapshot().getInt("WorkbenchProtectionRadius")==64,"Natural-spawn UI did not receive the server radius");
                    var radius=(net.neoforged.neoforge.common.ModConfigSpec.IntValue)SableLifeServerConfig.SPEC.getValues().get("wildWorkbenchProtectionRadius");
                    try{for(int configured:new int[]{0,128}){radius.set(configured);h.assertTrue(new WildEncounterMenu(2,actor.getInventory(),actor).snapshot().getInt("WorkbenchProtectionRadius")==configured,"UI used a hardcoded workbench radius");}}
                    finally{radius.set(64);}
                    System.out.println("NATURAL_WORKBENCH_RUNTIME blockedWithWorldTable=true resumedWithSableTable=true warmCandidateBlockReads=0");
                    phase[0]=2;actor.discard();java.nio.file.Files.deleteIfExists(path);h.succeed();
                }
            }catch(ReflectiveOperationException|java.io.IOException error){throw new RuntimeException(error);}
        });
    }
}
