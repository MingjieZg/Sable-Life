package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.attack.AttackHitCredit;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class SurvivalAimGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h){
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,40,4));
        level.setBlockAndUpdate(pos,SableLife.SURVIVAL_LIFE_CORE.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());
        var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));
        return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
    }
    @GameTest(template="combat",batch="survival_aim_settings")
    public static void ProjectileAimingCanBeEditedSavedAndImported(GameTestHelper h){
        var core=fixture(h);var actor=TestActors.creative(h.getLevel());actor.setPos(ModuleArena.corePosition(core));
        var menu=new HeartDashboardMenu(1,actor.getInventory(),core);
        for(String type:List.of("arrow","gun","slash","mortar")){
            var m=core.modules().add(type);String key=type.equals("mortar")?"aimMode":"shotAim";
            h.assertTrue(SurvivalLifeCorePolicy.visibleField(m,key)&&SurvivalLifeCorePolicy.visibleField(m,"targetMultiple"),"Aiming controls hidden: "+type);
            h.assertTrue(menu.action(actor,core.modules().revision(),m.id(),"edit",key,1,"")
                    &&menu.action(actor,core.modules().revision(),m.id(),"edit","targetMultiple",1,""),"Server rejected aiming edit: "+type);
            var restored=VirtualModule.load(m.save());SurvivalLifeCorePolicy.constrain(restored);
            h.assertTrue(restored.value(key)==1&&restored.value("targetMultiple")==1&&SurvivalLifeCorePolicy.allowsCast(restored),"Save/import stripped aiming: "+type);
        }
        SurvivalLifeCorePolicy.sanitize(core);
        for(var m:core.modules().all())h.assertTrue(m.value(m.type().equals("mortar")?"aimMode":"shotAim")==1,"Activation sanitation removed aiming");
        h.succeed();
    }
    @GameTest(template="combat",batch="survival_aim_placement")
    public static void DirectTargetPlacementRemainsBlocked(GameTestHelper h){
        var core=fixture(h);var actor=TestActors.creative(h.getLevel());actor.setPos(ModuleArena.corePosition(core));
        var menu=new HeartDashboardMenu(1,actor.getInventory(),core);
        for(String type:List.of("sphere_blast","pull","freeze","pulse","quake","ground_strike")){
            var m=core.modules().add(type);String key=type.equals("quake")?"quakeOrigin":type.equals("ground_strike")?"strikeMode":"releasePosition";
            int value=type.equals("ground_strike")?2:1;
            h.assertTrue(!menu.action(actor,core.modules().revision(),m.id(),"edit",key,value,""),"Server accepted direct placement: "+type);
            m.edit(key,value,"");
            h.assertTrue(!SurvivalLifeCorePolicy.allowsCast(m)&&HeartModules.status(m,core).equals("survival_cast_blocked"),"Imported direct placement could run: "+type);
            SurvivalLifeCorePolicy.constrain(m);h.assertTrue(m.value(key)==0,"Direct placement was not constrained: "+type);
            h.assertTrue(!SurvivalLifeCorePolicy.visibleField(m,"targetMultiple"),"Direct-placement multiple control shown: "+type);
        }
        h.succeed();
    }
    private static List<Entity> projectiles(GameTestHelper h,VirtualModule m,Vec3 origin){
        return h.getLevel().getEntitiesOfClass(Entity.class,new AABB(origin,origin).inflate(16),e->e.getPersistentData().hasUUID(AttackHitCredit.MODULE)&&e.getPersistentData().getUUID(AttackHitCredit.MODULE).equals(m.id()));
    }
    private static void launch(GameTestHelper h,String type){
        var core=fixture(h);var actor=TestActors.creative(h.getLevel());actor.setPos(ModuleArena.corePosition(core));
        h.assertTrue(core.depositSurvivalTokens(actor,new ItemStack(Items.COPPER_INGOT,10)),"Health funding failed");core.tryActivate(actor);
        h.assertTrue(core.health().state()==HeartState.ALIVE&&core.survivalTokenBalance()==0,"Fixture did not activate at zero Energy");
        var m=core.modules().add(type);m.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos().east(),Direction.UP)));
        m.edit(type.equals("mortar")?"aimMode":"shotAim",1,"");m.edit("trigger",0,"");m.edit("target",1,"");m.edit("targetRange",16,"");
        m.edit("startDistance",2,"");m.edit("arrows",1,"");m.edit("slashCount",1,"");m.edit("endRadius",0,"");m.edit("speed",2,"");m.edit("damage",4,"");m.edit("explode",0,"");
        var origin=ModuleCombat.nozzle(core,m);var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);
        cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40);cow.setHealth(40);
        cow.setPos(origin.add(6,-cow.getBbHeight()/2,0));h.getLevel().addFreshEntity(cow);
        h.assertTrue(HeartModules.triggered(m,core),"Target aim blocked by policy: "+type+" / "+HeartModules.status(m,core));
        if(!type.equals("mortar"))h.assertTrue(ProjectileAim.directions(core,m,origin).getFirst().x>.99,"Shot did not aim sideways at target: "+type);
        var emptySlash=new ModuleSlash();var paidSlash=new ModuleSlash();
        if(type.equals("slash"))emptySlash.tick(core);else new ModuleCombat().tick(core);
        h.assertTrue(projectiles(h,m,origin).isEmpty()&&core.survivalTokenBalance()==0&&cow.getHealth()==40,"Unfunded aiming fired/damaged: "+type);
        h.assertTrue(core.depositSurvivalTokens(actor,new ItemStack(Items.DIAMOND,64)),"Cast funding failed");
        var quote=new CompoundTag();SurvivalTokens.writeEstimate(core,m,quote);long before=core.survivalTokenBalance();
        if(type.equals("slash"))paidSlash.tick(core);else new ModuleCombat().tick(core);
        h.assertTrue(before-core.survivalTokenBalance()==quote.getLong("TokenEstimate")&&quote.getLong("TokenEstimate")>0,"Target aim did not pay its quote: "+type);
        var shots=projectiles(h,m,origin);
        if(!type.equals("slash")){
            h.assertTrue(shots.size()==1,"Expected one targeted projectile: "+type);
            h.assertTrue(shots.getFirst().position().distanceToSqr(origin)<1e-8&&shots.getFirst().getDeltaMovement().x>0,"Projectile spawned at target or used fixed facing: "+type);
        }
        m.edit("enabled",0,"");emptySlash.clear();paidSlash.clear();shots.forEach(Entity::discard);cow.discard();core.resetForSurvival(actor);h.succeed();
    }
    @GameTest(template="combat",batch="survival_aim_arrow") public static void ArrowAimsFromMuzzleAndPays(GameTestHelper h){launch(h,"arrow");}
    @GameTest(template="combat",batch="survival_aim_gun") public static void GunAimsFromMuzzleAndPays(GameTestHelper h){launch(h,"gun");}
    @GameTest(template="combat",batch="survival_aim_mortar") public static void MortarAimsFromMuzzleAndPays(GameTestHelper h){launch(h,"mortar");}
    @GameTest(template="combat",batch="survival_aim_slash") public static void SlashAimsAndPays(GameTestHelper h){launch(h,"slash");}
}
