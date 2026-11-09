package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Each supported core/module pair: every discrete field choice, independently of other choices.
 * This verifies persistence, core constraints and actual ticker dispatch; behavioral suites remain separate. */
@GameTestHolder("sablelife_core_pattern_matrix_tests")
@PrefixGameTestTemplate(false)
public final class CorePatternMatrixGameTests {
    private static final List<Map<String,Object>> results=new ArrayList<>();
    private record Variant(String key,int value){}
    private static List<String> types(int kind){return kind==0?VirtualModule.AVAILABLE_TYPES:kind==1?RedstoneCorePolicy.TYPES:SurvivalLifeCorePolicy.TYPES;}
    @GameTestGenerator
    public static Collection<TestFunction> everyCoreAndModule(){
        var cases=new ArrayList<TestFunction>();
        for(int kind=0;kind<3;kind++)for(String type:types(kind)){
            int coreKind=kind;String label=kind+"_"+type;
            cases.add(new TestFunction("core_pattern_"+label,"core_pattern_"+label,
                    "sablelife_core_pattern_matrix_tests:combat",80,0,true,h->run(h,coreKind,type)));
        }
        return cases;
    }
    private static void run(GameTestHelper h,int kind,String type){
        var setting=(ModConfigSpec.BooleanValue)SableLifeServerConfig.SPEC.getValues().get(List.of("modules",kind==0?"heartCore":kind==1?"redstoneCore":"survivalHeartCore",type));
        boolean previous=setting.get();setting.set(true);
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,40,4));
        var block=kind==0?SableLife.HEART.get():kind==1?SableLife.REDSTONE_CORE.get():SableLife.SURVIVAL_LIFE_CORE.get();
        level.setBlockAndUpdate(pos,block.defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
        var actor=TestActors.creative(level);actor.setPos(ModuleArena.corePosition(core));
        var variants=new ArrayList<Variant>();variants.add(new Variant("default",0));
        for(var field:ModuleFields.forType(type))if(field.choice()&&field.choices().size()>1)
            for(int value=0;value<field.choices().size();value++)variants.add(new Variant(field.key(),value));
        // Loot is an event-driven module and deliberately has no general Trigger field.
        if(new VirtualModule(type).edit("trigger",0,"")){
            int maximum=kind==1?RedstoneCorePolicy.maxTrigger(type):5;
            for(int trigger=0;trigger<=maximum;trigger++)if(trigger!=4||MoveSet.TARGETS.contains(type))variants.add(new Variant("trigger",trigger));
            if(kind!=1)for(var field:ModuleFields.HURT_FIELDS)if(field.choice())
                for(int value=0;value<field.choices().size();value++)variants.add(new Variant(field.key(),value));
        }
        var rows=new ArrayList<Map<String,Object>>();var failures=new ArrayList<String>();
        try{
            if(kind==2)h.assertTrue(core.depositSurvivalTokens(actor,new ItemStack(Items.NETHERITE_BLOCK,64)),"Cannot fund core");
            if(kind!=1){core.tryActivate(actor);h.assertTrue(core.health().state()==HeartState.ALIVE,"Cannot activate fixture");}
            for(var variant:variants){
                var row=new LinkedHashMap<String,Object>();row.put("key",variant.key);row.put("value",variant.value);rows.add(row);
                var module=core.modules().add(type);
                try{
                    module.edit("trigger",0,"");
                    if(variant.key.equals("hurtMode"))module.edit("trigger",5,"");
                    if(!variant.key.equals("default"))h.assertTrue(module.edit(variant.key,variant.value,""),"Rejected discrete setting");
                    if(kind==1)RedstoneCorePolicy.sanitize(core);
                    if(kind==2)SurvivalLifeCorePolicy.sanitize(core);
                    var mark=ModuleAnchor.create(core,core.getBlockPos().east(),Direction.EAST);
                    module.anchors(module.area()?List.of(mark,mark):List.of(mark));
                    if(type.equals("arena"))module.arenaCenter(ModuleArena.corePosition(core),level.dimension().location().toString());
                    if(type.equals("blueprint_spawn"))module.arenaCenter(ModuleArena.corePosition(core).add(0,8,0),level.dimension().location().toString());
                    var saved=module.save();var restored=VirtualModule.load(saved);
                    h.assertTrue(restored.save().equals(saved),"Save/load changed selected configuration");
                    h.assertTrue(ModuleAvailability.enabled(core,module),"Supported enabled module was unavailable");
                    row.put("effective",module.value(variant.key));row.put("status",HeartModules.status(module,core));
                    long balance=core.survivalTokenBalance();
                    HeartBlockEntity.serverTick(level,core.getBlockPos(),core.getBlockState(),core);
                    h.assertTrue(core.survivalTokenBalance()>=0,"Negative energy");
                    if(kind==2)h.assertTrue(core.survivalTokenBalance()<=balance,"Module minted energy");
                    row.put("debit",balance-core.survivalTokenBalance());
                    module.edit("enabled",0,"");
                    h.assertTrue(!HeartModules.triggered(module,core),"Disabled module still triggered");
                    HeartBlockEntity.serverTick(level,core.getBlockPos(),core.getBlockState(),core);
                    row.put("passed",true);
                }catch(Throwable failure){
                    row.put("passed",false);row.put("error",failure.toString());failures.add(variant.key+"="+variant.value+": "+failure);
                }finally{core.modules().clearRuntime();core.modules().remove(module.id());}
            }
        }finally{
            var result=new LinkedHashMap<String,Object>();result.put("core",kind);result.put("module",type);result.put("variants",rows);result.put("failures",failures);results.add(result);
            try{java.nio.file.Files.writeString(java.nio.file.Path.of("all-core-pattern-matrix.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));}
            catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
            setting.set(previous);
            if(kind==2)core.resetForSurvival(actor);else if(kind==0)core.resetForCreative(actor);else core.modules().clearRuntime();
            dev.sablelife.compat.StructureRemovalApi.remove(level,body);
        }
        System.out.println("CORE_PATTERN_MATRIX core="+kind+" module="+type+" variants="+rows.size()+" failures="+failures.size());
        h.assertTrue(failures.isEmpty(),"Core "+kind+"/"+type+": "+String.join("; ",failures));h.succeed();
    }
}
