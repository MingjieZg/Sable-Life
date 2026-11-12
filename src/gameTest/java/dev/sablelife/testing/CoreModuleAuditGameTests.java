package dev.sablelife.testing;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Cross-core boundary checks plus funded/unfunded casts through the actual server ticker. */
@GameTestHolder("sablelife_core_module_audit_tests")
@PrefixGameTestTemplate(false)
public final class CoreModuleAuditGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h,int kind){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,3,4));
        var block=kind==0?SableLife.HEART.get():kind==1?SableLife.REDSTONE_CORE.get():SableLife.SURVIVAL_LIFE_CORE.get();
        level.setBlockAndUpdate(pos,block.defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    private static ModConfigSpec.BooleanValue option(int kind,String type){
        return (ModConfigSpec.BooleanValue)SableLifeServerConfig.SPEC.getValues().get(List.of("modules",kind==0?"heartCore":kind==1?"redstoneCore":"survivalHeartCore",type));
    }
    private static void catalog(GameTestHelper h,int kind){
        var core=fixture(h,kind);var before=new LinkedHashMap<ModConfigSpec.BooleanValue,Boolean>();
        var allowed=kind==0?VirtualModule.AVAILABLE_TYPES:kind==1?RedstoneCorePolicy.TYPES:SurvivalLifeCorePolicy.TYPES;
        try{
            for(var type:allowed){var setting=option(kind,type);h.assertTrue(setting!=null,"Missing config: "+kind+"/"+type);before.put(setting,setting.get());setting.set(true);}
            h.assertTrue(new HashSet<>(ModuleAvailability.available(core)).equals(new HashSet<>(allowed)),"Catalog disagrees with core boundary: "+kind);
            for(var type:VirtualModule.AVAILABLE_TYPES){
                var module=core.modules().add(type);h.assertTrue(module!=null,"Could not retain module "+type);
                module.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.EAST)));
                if(module.area())module.anchors(List.of(module.anchors().getFirst(),module.anchors().getFirst()));
                module.edit("trigger",0,"");
                boolean supported=allowed.contains(type);
                h.assertTrue(ModuleAvailability.enabled(core,type)==supported,"Capability bypass: "+kind+"/"+type);
                var saved=module.save();var restored=VirtualModule.load(saved);
                if(!restored.save().equals(saved))System.out.println("CORE_AUDIT roundtrip difference "+type+": "+saved.getCompound("Settings").getAllKeys().stream().filter(key->!Objects.equals(saved.getCompound("Settings").get(key),restored.save().getCompound("Settings").get(key))).toList());
                h.assertTrue(restored.save().equals(saved),"Module save/load changed settings: "+kind+"/"+type);
                if(supported){
                    var setting=option(kind,type);setting.set(false);
                    h.assertTrue(!ModuleAvailability.available(core).contains(type)&&HeartModules.status(module,core).equals("server_disabled")&&!HeartModules.triggered(module,core),"Disabled config still exposed/running: "+kind+"/"+type);
                    h.assertTrue(saved.equals(module.save()),"Disabling erased settings: "+kind+"/"+type);setting.set(true);
                }else h.assertTrue(HeartModules.status(module,core).equals("server_disabled"),"Forbidden imported module not gated: "+kind+"/"+type);
            }
            var restored=new HeartModules();restored.load(core.modules().save());
            h.assertTrue(restored.all().stream().map(VirtualModule::type).toList().equals(VirtualModule.AVAILABLE_TYPES),
                    "Stored module inventory changed during reload");
            System.out.println("CORE_AUDIT catalog core="+kind+" allowed="+allowed.size()+" stored="+restored.all().size());
            h.succeed();
        }finally{before.forEach(ModConfigSpec.BooleanValue::set);}
    }
    @GameTest(template="combat",batch="audit_catalog_heart") public static void HeartCatalogEveryModulePersistsAndHonorsConfig(GameTestHelper h){catalog(h,0);}
    @GameTest(template="combat",batch="audit_catalog_redstone") public static void RedstoneCatalogEveryModulePersistsAndHonorsConfig(GameTestHelper h){catalog(h,1);}
    @GameTest(template="combat",batch="audit_catalog_survival") public static void SurvivalCatalogEveryModulePersistsAndHonorsConfig(GameTestHelper h){catalog(h,2);}

    private static void cast(GameTestHelper h,String type){
        var core=fixture(h,2);var actor=TestActors.creative(h.getLevel());actor.setPos(ModuleArena.corePosition(core));
        h.assertTrue(core.depositSurvivalTokens(actor,new ItemStack(Items.COPPER_INGOT,10)),"Initial health funding failed");core.tryActivate(actor);
        h.assertTrue(core.health().state()==HeartState.ALIVE&&core.survivalTokenBalance()==0,"Unfunded cast fixture is not alive at zero Energy");
        var module=core.modules().add(type);var mark=ModuleAnchor.create(core,core.getBlockPos().east(),Direction.EAST);
        module.anchors(module.area()?List.of(mark,mark):List.of(mark));module.edit("trigger",0,"");
        module.edit("interval",200,"");module.edit("warningTime",0,"");module.edit("chargeTime",0,"");
        module.edit("pullWarning",0,"");module.edit("freezeWarning",0,"");module.edit("grabWarning",0,"");
        module.edit("strikeRange",0,"");module.edit("strikeStart",0,"");module.edit("strikeHeight",2,"");
        module.edit("quakeRange",4,"");module.edit("quakeWaves",1,"");module.edit("lineOfSight",0,"");
        module.edit("grabTarget",1,"");module.edit("target",1,"");
        if(type.equals("summoned_weapon")){
            var attack=core.modules().add("gun");attack.edit("enabled",0,"");attack.edit("shotAim",0,"");
            module.summonAttack(attack.id());module.projectileItem(new ItemStack(Items.DIAMOND_SWORD),h.getLevel().registryAccess());
        }
        var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);
        cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);cow.setHealth(1000);
        var center=type.equals("melee")?ModuleCombat.meleeCenter(core,module):ModuleCombat.origin(core,module,true).add(.6,0,0);cow.setPos(center.add(0,-cow.getBbHeight()/2,0));h.getLevel().addFreshEntity(cow);
        h.assertTrue(HeartModules.triggered(module,core),"Actual cast was not triggered: "+type+" / "+HeartModules.status(module,core));
        core.modules().tick(core);
        h.runAfterDelay(8,()->{
            h.assertTrue(core.survivalTokenBalance()==0&&cow.getHealth()==1000,"Unfunded module damaged or overspent: "+type);
            var quote=new CompoundTag();SurvivalTokens.writeEstimate(core,module,quote);long price=quote.getLong("TokenEstimate");
            h.assertTrue(price>0,"Missing price: "+type);
            h.assertTrue(core.depositSurvivalTokens(actor,new ItemStack(Items.NETHERITE_BLOCK,64)),"Funded cast deposit failed");
            long balance=core.survivalTokenBalance();core.modules().clearRuntime();
            var point=type.equals("melee")?ModuleCombat.meleeCenter(core,module):ModuleCombat.origin(core,module,true).add(.6,0,0);
            cow.setPos(point.add(0,-cow.getBbHeight()/2,0));
            core.modules().tick(core);
            h.runAfterDelay(8,()->{
                h.assertTrue(core.survivalTokenBalance()<balance,"Funded module never reached its runtime debit: "+type+" / "+HeartModules.status(module,core));
                long debited=balance-core.survivalTokenBalance();
                h.assertTrue(debited>=price,"Runtime discounted the displayed cost: "+type+" quoted="+price+" debited="+debited);
                module.edit("enabled",0,"");core.modules().tick(core);long stopped=core.survivalTokenBalance();
                h.runAfterDelay(5,()->{
                    // A summoned group intentionally outlives its deployment trigger; its template keeps paying.
                    if(!type.equals("summoned_weapon"))h.assertTrue(core.survivalTokenBalance()==stopped,"Disabled module kept debiting: "+type);
                    else h.assertTrue(core.modules().summonedWeapons.snapshot(module.id(),core).getBoolean("Active"),"Trigger loss incorrectly canceled an existing summon");
                    h.assertTrue(core.resetForSurvival(actor)&&core.health().state()==HeartState.DORMANT,"Release failed after active cast: "+type);
                    h.assertTrue(core.modules().damageBoxes.entities().isEmpty()&&core.modules().surfaceFields.count()==0&&!core.modules().summonedWeapons.snapshot(module.id(),core).getBoolean("Active"),"Release retained owned runtime: "+type);
                    long released=core.survivalTokenBalance();core.modules().tick(core);h.assertTrue(core.survivalTokenBalance()==released,"Released core kept debiting: "+type);
                    cow.discard();System.out.println("CORE_AUDIT runtime type="+type+" quote="+price+" debit="+debited);h.succeed();
                });
            });
        });
    }
    @GameTest(template="combat",batch="audit_cast_arrow",timeoutTicks=50) public static void ArrowFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"arrow");}
    @GameTest(template="combat",batch="audit_cast_melee",timeoutTicks=50) public static void MeleeFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"melee");}
    @GameTest(template="combat",batch="audit_cast_flame",timeoutTicks=50) public static void FlameFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"flame");}
    @GameTest(template="combat",batch="audit_cast_gun",timeoutTicks=50) public static void GunFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"gun");}
    @GameTest(template="combat",batch="audit_cast_mortar",timeoutTicks=50) public static void MortarFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"mortar");}
    @GameTest(template="combat",batch="audit_cast_homing",timeoutTicks=50) public static void HomingFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"homing");}
    @GameTest(template="combat",batch="audit_cast_laser",timeoutTicks=50) public static void LaserFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"laser");}
    @GameTest(template="combat",batch="audit_cast_sphere",timeoutTicks=50) public static void SphereFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"sphere_blast");}
    @GameTest(template="combat",batch="audit_cast_slash",timeoutTicks=50) public static void SlashFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"slash");}
    @GameTest(template="combat",batch="audit_cast_strike",timeoutTicks=50) public static void GroundStrikeFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"ground_strike");}
    @GameTest(template="combat",batch="audit_cast_quake",timeoutTicks=50) public static void QuakeFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"quake");}
    @GameTest(template="combat",batch="audit_cast_pull",timeoutTicks=50) public static void PullFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"pull");}
    @GameTest(template="combat",batch="audit_cast_freeze",timeoutTicks=50) public static void FreezeFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"freeze");}
    @GameTest(template="combat",batch="audit_cast_grab",timeoutTicks=50) public static void GrabFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"grab");}
    @GameTest(template="combat",batch="audit_cast_pulse",timeoutTicks=50) public static void PulseFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"pulse");}
    @GameTest(template="combat",batch="audit_cast_summon",timeoutTicks=50) public static void SummonFundedAndUnfundedRuntime(GameTestHelper h){cast(h,"summoned_weapon");}

    @GameTest(template="combat",batch="audit_cast_surface")
    public static void SurfaceImpactNeedsFundingAndBillsServerMultiplier(GameTestHelper h){
        var core=fixture(h,2);var actor=TestActors.creative(h.getLevel());actor.setPos(ModuleArena.corePosition(core));
        core.depositSurvivalTokens(actor,new ItemStack(Items.COPPER_INGOT,10));core.tryActivate(actor);
        var field=core.modules().add("surface_field");field.edit("fieldRadius",1,"");field.edit("fieldDuration",40,"");
        var attack=core.modules().add("gun");attack.impactField(field.id());attack.edit("trigger",1,"");
        var ground=h.absolutePos(new BlockPos(7,1,7));for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)h.getLevel().setBlockAndUpdate(ground.offset(x,0,z),Blocks.STONE.defaultBlockState());
        var point=ground.getBottomCenter().add(0,1,0);var up=new net.minecraft.world.phys.Vec3(0,1,0);
        core.modules().surfaceFields.impact(core,attack,point,up,up.scale(-1));
        h.assertTrue(core.modules().surfaceFields.count()==0&&core.survivalTokenBalance()==0,"Unfunded impact created an area");
        var rate=(ModConfigSpec.IntValue)SableLifeServerConfig.SPEC.getValues().get(List.of("survivalTokens","moduleMultipliers","surface_field"));int previous=rate.get();
        try{
            rate.set(3);var quote=new CompoundTag();SurvivalTokens.writeEstimate(core,field,quote);
            long withoutBlast=quote.getLong("TokenEstimate");
            field.edit("fieldBlast",1,"");field.edit("blastDamage",7,"");
            SurvivalTokens.writeEstimate(core,field,quote);
            h.assertTrue(quote.getLong("TokenEstimate")==withoutBlast+21,"Blast estimate should add radius times damage once");
            core.depositSurvivalTokens(actor,new ItemStack(Items.NETHERITE_INGOT));long before=core.survivalTokenBalance();
            core.modules().surfaceFields.impact(core,attack,point,up,up.scale(-1));
            h.assertTrue(core.modules().surfaceFields.count()==1&&before-core.survivalTokenBalance()==quote.getLong("TokenEstimate"),"Impact area debit disagrees with configured multiplier");
            long balance=core.survivalTokenBalance();
            h.assertTrue(SurvivalTokens.chargeFieldExtension(core,field,20)&&balance-core.survivalTokenBalance()==29,"Area refresh charged again for the blast");
            core.resetForSurvival(actor);h.assertTrue(core.modules().surfaceFields.count()==0,"Release retained impact area");h.succeed();
        }finally{rate.set(previous);}
    }

    @GameTest(template="combat",batch="audit_survival_healing")
    public static void HealingModuleUsesActualRestoredHealthAndCannotRefillForFree(GameTestHelper h){
        var core=fixture(h,2);var actor=TestActors.creative(h.getLevel());actor.setPos(ModuleArena.corePosition(core));
        core.depositSurvivalTokens(actor,new ItemStack(Items.COPPER_INGOT,10));core.tryActivate(actor);core.damage(40);
        var heal=core.modules().add("health_adjustment");heal.edit("healthAmount",30,"");heal.edit("trigger",0,"");
        core.modules().tick(core);h.assertTrue(core.health().current()==60,"Unfunded healing restored health");
        core.modules().clearRuntime();core.depositSurvivalTokens(actor,new ItemStack(Items.COPPER_INGOT));core.modules().tick(core);
        h.assertTrue(core.health().current()==70&&core.survivalTokenBalance()==0,"Healing did not stop at affordable restored health");
        core.resetForSurvival(actor);core.tryActivate(actor);
        h.assertTrue(core.health().current()==70&&core.survivalTokenBalance()==0,"Reactivation refilled paid health for free");h.succeed();
    }
}
