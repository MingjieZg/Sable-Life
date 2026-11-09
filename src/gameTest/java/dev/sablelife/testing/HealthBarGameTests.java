package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class HealthBarGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,6,3));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.above(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.above()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY()+1,pos.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());core.configure(100,80);return core;
    }
    private static VirtualModule bar(HeartBlockEntity core){var m=core.modules().add("health_bar");m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().above(),Direction.UP)));return m;}
    @GameTest(template="combat",batch="world_health_bar")
    public static void CreatureNameSettingsAndSnapshotsFollowEdits(GameTestHelper h){
        var core=fixture(h);var module=bar(core);var actor=TestActors.creative(h.getLevel());
        h.assertTrue(module.value("healthBarName")==1&&module.value("healthBarNameSize")==.2&&module.value("healthBarNameGap")==.08,"Name defaults changed");
        core.editCore(actor,"name",0,"&c&lSentinel");core.tryActivate(actor);core.damage(1);
        var data=ModuleHealthBar.snapshot(core);var row=data.getList("HealthBars",10).getCompound(0);
        h.assertTrue(data.getString("HealthBarName").equals("&c&lSentinel")&&row.getBoolean("ShowName"),"Owning core's formatted name missing");
        module.edit("healthBarNameSize",.45,"");module.edit("healthBarNameGap",.3,"");
        var saved=VirtualModule.load(module.save());var preset=ModulePreset.create(module.save());
        h.assertTrue(saved.value("healthBarNameSize")==.45&&preset.value("healthBarNameGap")==.3,"Name sizing not portable");
        module.edit("healthBarName",0,"");data=ModuleHealthBar.snapshot(core);
        h.assertTrue(!data.contains("HealthBarName")&&!data.getList("HealthBars",10).getCompound(0).getBoolean("ShowName"),"Hidden name still sent");
        module.edit("healthBarName",1,"");core.editCore(actor,"name",0,"");
        h.assertTrue(ModuleHealthBar.snapshot(core).getString("HealthBarName").isEmpty(),"Cleared name kept old label");
        module.edit("healthBarNameSize",999,"");module.edit("healthBarNameGap",-1,"");
        h.assertTrue(module.value("healthBarNameSize")==2&&module.value("healthBarNameGap")==0&&!module.edit("healthBarNameSize",Double.NaN,""),"Invalid name sizing accepted");h.succeed();
    }
    @GameTest(template="combat",batch="world_health_bar")
    public static void ViewDistancePersistsAndBoundsApply(GameTestHelper h){
        var core=fixture(h);var module=bar(core);
        h.assertTrue(module.value("healthBarViewDistance")==64,"Default viewing distance must be 64 blocks");
        module.edit("healthBarViewDistance",24,"");
        h.assertTrue(ModuleHealthBar.row(core,module).getFloat("ViewDistance")==24,"Snapshot lost viewing distance");
        h.assertTrue(ModulePreset.create(module.save()).value("healthBarViewDistance")==24,"Preset lost viewing distance");
        h.assertTrue(VirtualModule.load(module.save()).value("healthBarViewDistance")==24,"Saved module lost viewing distance");
        var legacy=module.save();legacy.getCompound("Settings").remove("healthBarViewDistance");
        h.assertTrue(VirtualModule.load(legacy).value("healthBarViewDistance")==64,"Legacy module must use default viewing distance");
        module.edit("healthBarViewDistance",999,"");h.assertTrue(module.value("healthBarViewDistance")==256,"Maximum viewing distance ignored");
        module.edit("healthBarViewDistance",0,"");h.assertTrue(module.value("healthBarViewDistance")==1,"Minimum viewing distance ignored");
        h.assertTrue(!module.edit("healthBarViewDistance",Double.NaN,""),"Non-finite viewing distance accepted");h.succeed();
    }
    @GameTest(template="combat",batch="world_health_bar_damage",timeoutTicks=40)
    public static void DamageVisibilityRefreshesOnHealthLossAndResetsOnRelease(GameTestHelper h){
        var core=fixture(h);var module=bar(core);var actor=TestActors.creative(h.getLevel());core.tryActivate(actor);
        module.edit("healthBarVisibility",1,"");module.edit("healthBarHideDelay",4,"");
        h.assertTrue(!ModuleHealthBar.showing(core,module),"Damage mode displayed before any health loss");core.damage(0);
        h.assertTrue(!ModuleHealthBar.showing(core,module),"Zero damage started visibility");core.damage(1);
        h.assertTrue(ModuleHealthBar.showing(core,module),"Actual health loss did not show bar");
        var copy=ModulePreset.create(module.save());h.assertTrue(copy.value("healthBarVisibility")==1&&copy.value("healthBarHideDelay")==4,"Visibility settings lost in preset");
        h.runAtTickTime(3,()->core.damage(1));
        h.runAtTickTime(5,()->{h.assertTrue(ModuleHealthBar.showing(core,module),"Second hit did not refresh hide timer");core.adjustOwnHealth(1,false);});
        h.runAtTickTime(8,()->{
            h.assertTrue(!ModuleHealthBar.showing(core,module),"Healing refreshed timer or no-damage expiry failed");
            module.edit("healthBarVisibility",0,"");h.assertTrue(ModuleHealthBar.showing(core,module),"Always mode remained hidden");
            module.edit("healthBarVisibility",1,"");core.damage(1);core.resetForCreative(actor);core.tryActivate(actor);
            h.assertTrue(!ModuleHealthBar.showing(core,module),"Reactivation retained prior creature damage timer");h.succeed();
        });
    }
    @GameTest(template="combat",batch="world_health_bar")
    public static void MarkedBarUsesOwnHealthAndFollowsLifecycleAndSignals(GameTestHelper h){
        var core=fixture(h);var module=bar(core);var actor=TestActors.creative(h.getLevel());
        h.assertTrue(!ModuleHealthBar.showing(core,module),"Dormant health bar visible");core.tryActivate(actor);
        h.assertTrue(!ModuleHealthBar.showing(core,module),"Default damage-only bar appeared before damage");core.damage(35);
        var row=ModuleHealthBar.snapshot(core).getList("HealthBars",10).getCompound(0);
        h.assertTrue(row.getFloat("Health")==65&&row.getFloat("Maximum")==100,"Bar does not read owning core health");
        module.edit("trigger",2,"");h.assertTrue(!ModuleHealthBar.showing(core,module),"Unpowered bar ignored trigger");module.edit("trigger",0,"");
        module.edit("enabled",0,"");h.assertTrue(ModuleHealthBar.snapshot(core).getList("HealthBars",10).isEmpty(),"Disabled bar remained");module.edit("enabled",1,"");
        core.damage(100);h.assertTrue(core.health().state()==HeartState.DYING&&ModuleHealthBar.showing(core,module),"Death countdown should retain empty bar");
        h.assertTrue(ModuleHealthBar.snapshot(core).getList("HealthBars",10).getCompound(0).getFloat("Health")==0,"Death bar not empty");
        core.resetForCreative(actor);h.assertTrue(ModuleHealthBar.snapshot(core).getList("HealthBars",10).isEmpty(),"Release retained bar");h.succeed();
    }
    @GameTest(template="combat",batch="world_health_bar")
    public static void FaceDistanceSizesAndPortableSettingsAreValidated(GameTestHelper h){
        var core=fixture(h);var module=bar(core);h.assertTrue(module.value("healthBarStyle")==1&&module.value("healthBarVisibility")==1&&module.value("healthBarWidth")==1.6&&module.value("healthBarHeight")==.1&&module.value("startDistance")==.75&&module.value("healthBarViewDistance")==64&&module.value("healthBarHideDelay")==100,"New bar defaults do not match the requested settings");module.edit("healthBarWidth",3.5,"");module.edit("healthBarHeight",.3,"");module.edit("startDistance",2,"");
        var original=module.anchors().getFirst();var body=original.body(core);
        for(var face:Direction.values()){
            var anchor=new ModuleAnchor(original.bodyId(),original.offset(),face);
            var expected=anchor.position(body).getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(2.5));
            h.assertTrue(ModuleHealthBar.localPoint(anchor,body,2).distanceTo(expected)<1e-6,"Start distance uses wrong face: "+face);
        }
        h.assertTrue(ModuleHealthBar.row(core,module).getInt("Style")==1,"Snapshot lost rounded style");
        var legacy=module.save();legacy.getCompound("Settings").remove("healthBarStyle");h.assertTrue(VirtualModule.load(legacy).value("healthBarStyle")==1,"Missing style did not use the current default");
        var copy=ModulePreset.create(module.save());h.assertTrue(copy.anchors().isEmpty()&&copy.value("healthBarStyle")==1&&copy.value("healthBarWidth")==3.5&&copy.value("healthBarHeight")==.3&&copy.value("startDistance")==2,"Preset lost sizes or retained old body mark");
        var saved=module.save();saved.getCompound("Settings").putDouble("healthBarStyle",0);saved.getCompound("Settings").putDouble("healthBarVisibility",0);saved.getCompound("Settings").putDouble("healthBarWidth",2.5);saved.getCompound("Settings").putDouble("healthBarHeight",.2);var retained=VirtualModule.load(saved);
        h.assertTrue(retained.value("healthBarStyle")==0&&retained.value("healthBarVisibility")==0&&retained.value("healthBarWidth")==2.5&&retained.value("healthBarHeight")==.2,"New defaults overwrote saved author settings");
        h.assertTrue(!copy.edit("healthBarWidth",Double.NaN,""),"NaN width accepted");copy.edit("healthBarWidth",999,"");h.assertTrue(copy.value("healthBarWidth")==16,"Width limit ignored");
        core.tryActivate(TestActors.creative(h.getLevel()));dev.sablelife.ownership.LifeLifecycle.get(h.getLevel().getServer()).setBreakable(core.creatureId(),true);h.getLevel().setBlockAndUpdate(original.position(body),Blocks.AIR.defaultBlockState());
        h.assertTrue(ModuleHealthBar.snapshot(core).getList("HealthBars",10).isEmpty(),"Destroyed mark retained bar");h.succeed();
    }
    @GameTest(template="combat",batch="world_health_bar")
    public static void ServerModuleSwitchAndSnapshotBoundApply(GameTestHelper h){
        var core=fixture(h);var module=bar(core);core.tryActivate(TestActors.creative(h.getLevel()));core.damage(1);
        var option=(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues().get(List.of("modules","heartCore","health_bar"));boolean old=option.get();
        try{option.set(false);h.assertTrue(ModuleHealthBar.snapshot(core).getList("HealthBars",10).isEmpty(),"Server-disabled bar visible");}finally{option.set(old);}
        for(int i=0;i<70;i++)bar(core);
        h.assertTrue(ModuleHealthBar.snapshot(core).getList("HealthBars",10).size()==64,"Health bar snapshot is not bounded");h.succeed();
    }
}
