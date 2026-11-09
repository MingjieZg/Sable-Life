package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class CoreReleaseGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h){return fixture(h,SableLife.HEART.get());}
    private static HeartBlockEntity fixture(GameTestHelper h,HeartBlock block){var l=h.getLevel();var p=h.absolutePos(new BlockPos(3,8,3));l.setBlockAndUpdate(p,block.defaultBlockState());l.setBlockAndUpdate(p.east(),Blocks.IRON_BLOCK.defaultBlockState());var b=SubLevelAssemblyHelper.assembleBlocks(l,p,List.of(p,p.east()),new BoundingBox3i(p.getX(),p.getY(),p.getZ(),p.getX()+1,p.getY(),p.getZ()));return (HeartBlockEntity)l.getBlockEntity(b.getPlot().getCenterBlock());}

    @GameTest(template="combat",batch="death_only_mining")
    public static void DeathOnlyMiningUnlocksPartsButKeepsTheCoreProtected(GameTestHelper h){
        var core=fixture(h);
        var level=h.getLevel();
        var player=TestActors.creative(level);
        player.setPos(ModuleArena.corePosition(core));
        h.assertTrue(core.editCore(player,"breaking",2,""),"Death-only mode was rejected");
        h.assertTrue(core.limbCleanup().delay()==3,"Default limb cleanup time changed");
        var saved=core.saveWithoutMetadata(level.registryAccess());
        core.loadWithComponents(saved,level.registryAccess());
        h.assertTrue(core.miningMode()==2,"Death-only mode did not survive reload");
        core.tryActivate(player);
        var part=core.getBlockPos().east();
        h.assertTrue(dev.sablelife.ownership.BodyProtection.locked(level,part),"Living boss was mineable");
        h.assertTrue(core.forceDeath()&&!dev.sablelife.ownership.BodyProtection.locked(level,part)
                &&dev.sablelife.ownership.BodyProtection.explosionLocked(level,part),"Death did not unlock only mining");
        player.setGameMode(GameType.SURVIVAL);
        var hit=new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level,core.getBlockPos(),core.getBlockState(),player);
        dev.sablelife.ownership.BodyProtection.breaking(hit);
        h.assertTrue(hit.isCanceled(),"Survival player could mine the Boss core during the death window");
        h.succeed();
    }

    @GameTest(template="combat",batch="independent_explosion_destruction")
    public static void ExplosionSettingWorksWhileMiningIsDisabled(GameTestHelper h){
        var core=fixture(h);
        var level=h.getLevel();
        var player=TestActors.creative(level);
        core.editCore(player,"explosion",1,"");
        core.tryActivate(player);
        var part=core.getBlockPos().east();
        var point=ModuleArena.corePosition(core);
        h.assertTrue(dev.sablelife.ownership.BodyProtection.locked(level,part),"Fixture enabled mining");
        var explosion=new net.minecraft.world.level.Explosion(level,null,point.x,point.y,point.z,2,false,
                net.minecraft.world.level.Explosion.BlockInteraction.DESTROY,new java.util.ArrayList<>(List.of(part)));
        explosion.finalizeExplosion(false);
        h.assertTrue(level.getBlockState(part).isAir(),"Allowed explosion was blocked by the mining policy");
        h.succeed();
    }

    @GameTest(template="combat",batch="survival_top_up")
    public static void SurvivalTopUpMovesRealItemsAndConfirmsTwice(GameTestHelper h){
        var core=fixture(h,SableLife.SURVIVAL_LIFE_CORE.get());
        var player=TestActors.creative(h.getLevel());
        player.setPos(ModuleArena.corePosition(core));
        var menu=new SurvivalTopUpMenu(1,player.getInventory(),core);
        var copper=net.minecraft.world.item.Items.COPPER_INGOT;
        menu.getSlot(SurvivalTopUpMenu.INPUTS).set(new net.minecraft.world.item.ItemStack(copper,3));
        h.assertTrue(!menu.quickMoveStack(player,SurvivalTopUpMenu.INPUTS).isEmpty()&&menu.getSlot(0).getItem().getCount()==3,"Shift click failed");
        menu.setCarried(new net.minecraft.world.item.ItemStack(copper,2));
        menu.clicked(1,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
        h.assertTrue(menu.getSlot(1).getItem().getCount()==2&&menu.getCarried().isEmpty(),"Manual placement failed after Shift click");
        h.assertTrue(menu.clickMenuButton(player,0)&&core.survivalTokenBalance()==0,"First confirmation consumed items");
        h.assertTrue(menu.clickMenuButton(player,0)&&core.survivalTokenBalance()==50&&menu.getSlot(0).getItem().isEmpty()
                &&menu.getSlot(1).getItem().isEmpty(),"Second confirmation did not exchange all inputs");
        h.assertTrue(core.allowSurvivalInteraction()&&!core.forceChunkLoading(),"Survival settings policy is wrong");
        h.succeed();
    }

    @GameTest(template="combat",batch="survival_release_shears")
    public static void SurvivalShearsReleaseOnlySurvivalCoresAndRetainLifeAndEnergy(GameTestHelper h){
        var core=fixture(h,SableLife.SURVIVAL_LIFE_CORE.get());
        var player=TestActors.creative(h.getLevel());
        player.setPos(ModuleArena.corePosition(core));
        player.setGameMode(GameType.SURVIVAL);
        var fuel=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT,12);
        h.assertTrue(core.depositSurvivalTokens(player,fuel),"Survival refuel failed");
        core.tryActivate(player);
        core.damage(20);
        var old=core.creatureId();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(SableLife.SURVIVAL_RELEASE_TOOL.get()));
        var hit=new net.minecraft.world.phys.BlockHitResult(core.getBlockPos().getCenter(),Direction.UP,core.getBlockPos(),false);
        var context=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        var result=SableLife.SURVIVAL_RELEASE_TOOL.get().onItemUseFirst(player.getMainHandItem(),context);
        h.assertTrue(result.consumesAction()&&core.health().state()==HeartState.DORMANT
                &&core.health().current()==80&&core.survivalTokenBalance()==20&&!core.creatureId().equals(old),
                "Survival shears lost energy/life or failed to release");
        h.assertTrue(core.modules().all().isEmpty(),"Shears introduced unrelated modules");
        var ordinary=new HeartBlockEntity(core.getBlockPos(),SableLife.HEART.get().defaultBlockState());
        h.assertTrue(!ordinary.resetForSurvival(player),"Survival shears could release an ordinary Heart Core");
        h.succeed();
    }

    @GameTest(template="combat",batch="survival_material_catalog")
    public static void DefaultMaterialsHaveMatchingCatalogAndDepositValues(GameTestHelper h){
        var core=fixture(h,SableLife.SURVIVAL_LIFE_CORE.get());
        var player=TestActors.creative(h.getLevel());
        player.setPos(ModuleArena.corePosition(core));
        var menu=new SurvivalTopUpMenu(1,player.getInventory(),core);
        var expected=new java.util.LinkedHashMap<net.minecraft.world.item.Item,Integer>();
        expected.put(net.minecraft.world.item.Items.COPPER_INGOT,10);
        expected.put(net.minecraft.world.item.Items.IRON_INGOT,30);
        expected.put(net.minecraft.world.item.Items.GOLD_INGOT,60);
        expected.put(net.minecraft.world.item.Items.EMERALD,120);
        expected.put(net.minecraft.world.item.Items.DIAMOND,300);
        expected.put(net.minecraft.world.item.Items.NETHERITE_INGOT,3000);
        expected.put(net.minecraft.world.item.Items.COPPER_BLOCK,90);
        expected.put(net.minecraft.world.item.Items.IRON_BLOCK,270);
        expected.put(net.minecraft.world.item.Items.GOLD_BLOCK,540);
        expected.put(net.minecraft.world.item.Items.EMERALD_BLOCK,1080);
        expected.put(net.minecraft.world.item.Items.DIAMOND_BLOCK,2700);
        expected.put(net.minecraft.world.item.Items.NETHERITE_BLOCK,27000);
        var catalog=menu.snapshot().getList("Catalog",10);
        long total=0;
        for(var entry:expected.entrySet()){
            String id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
            h.assertTrue(catalog.stream().anyMatch(tag->{
                var row=(net.minecraft.nbt.CompoundTag)tag;
                return row.getString("Id").equals(id)&&row.getInt("Price")==entry.getValue();
            }),"Catalog value missing for "+id);
            var stack=new net.minecraft.world.item.ItemStack(entry.getKey());
            h.assertTrue(core.depositSurvivalTokens(player,stack)&&stack.isEmpty(),"Deposit failed for "+id);
            total+=entry.getValue();
            h.assertTrue(core.survivalTokenBalance()==total,"Credited value differed from the catalog for "+id);
        }
        h.succeed();
    }

    @GameTest(template="combat",batch="survival_core_release")
    public static void DepletedSurvivalCoreReleasesWithoutDestroyingTheMachine(GameTestHelper h){
        var core=fixture(h,SableLife.SURVIVAL_LIFE_CORE.get());
        var player=TestActors.creative(h.getLevel());
        player.setPos(ModuleArena.corePosition(core));
        h.assertTrue(core.isSurvivalCore()&&core.allowBreaking()&&core.miningDrops()&&core.allowLifeAttacks(),"Survival core policy missing");
        h.assertTrue(!core.editCore(player,"lifeAttacks",0,"")&&!core.editCore(player,"miningDrops",0,"")
                &&!ModuleAvailability.enabled(core,"command")&&ModuleAvailability.enabled(core,"wireless_redstone"),"Survival policy permits forbidden settings or hides control");
        var module=core.modules().add("wireless_redstone");
        var option=(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue)dev.sablelife.SableLifeServerConfig.SPEC.getValues()
                .get(List.of("modules","survivalHeartCore","wireless_redstone"));
        boolean previousSetting=option.get();
        try{
            option.set(false);
            h.assertTrue(!ModuleAvailability.enabled(core,module)&&!ModuleAvailability.available(core).contains(module.type())
                    &&ModuleAvailability.enabled(false,module.type())&&core.modules().find(module.id())==module,
                    "Survival switch affected another core or deleted saved settings");
        }finally{option.set(previousSetting);}
        core.tryActivate(player);
        h.assertTrue(core.health().state()==HeartState.DORMANT&&core.health().current()==0,"Unpaid core activated");
        var rejected=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        h.assertTrue(!core.depositSurvivalTokens(player,rejected)&&rejected.getCount()==1,"Rejected deposit consumed an item");
        var deposit=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT,12);
        h.assertTrue(core.depositSurvivalTokens(player,deposit)&&deposit.isEmpty()&&core.survivalTokenBalance()==120,"Deposit did not consume items or credit tokens");
        var settings=new HeartSettingsMenu(1,player.getInventory(),core);
        h.assertTrue(settings.snapshot().getFloat("CurrentLife")==0,"Settings showed health before activation");
        core.tryActivate(player);
        settings.broadcastChanges();
        h.assertTrue(settings.snapshot().getFloat("CurrentLife")==100,"Settings did not update current life after activation");
        h.assertTrue(core.health().state()==HeartState.ALIVE&&core.survivalTokenBalance()==20,"Activation did not charge for starting health");
        var targetCast=core.modules().add("sphere_blast");
        targetCast.edit("releasePosition",1,"");
        h.assertTrue(HeartModules.status(targetCast,core).equals("survival_cast_blocked")
                &&!HeartModules.triggered(targetCast,core),"Direct target casts bypassed the survival policy");
        core.damage(40);
        h.assertTrue(core.adjustOwnHealth(40,false)&&core.health().current()==80&&core.survivalTokenBalance()==0,"Healing did not stop at the affordable amount");
        h.assertTrue(core.resetForCreative(player)&&core.health().current()==80,"Manual release discarded paid health");
        core.tryActivate(player);
        h.assertTrue(core.health().state()==HeartState.ALIVE&&core.health().current()==80,"Unfunded refill prevented reactivation with remaining health");
        core.resetForCreative(player);
        var refill=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT,3);
        h.assertTrue(core.depositSurvivalTokens(player,refill),"Refill deposit rejected");
        core.tryActivate(player);
        h.assertTrue(core.health().current()==100&&core.survivalTokenBalance()==10,"Reactivation charged something other than missing health");
        var previousLife=core.creatureId();
        h.assertTrue(core.damage(core.health().maximum()),"Lethal damage was rejected");
        h.assertTrue(core.health().state()==HeartState.DORMANT&&core.health().current()==0&&core.survivalTokenBalance()==10
                &&!core.creatureId().equals(previousLife),"Lethal damage did not release immediately or discarded tokens");
        h.assertTrue(!dev.sablelife.ownership.LifeLifecycle.get(h.getLevel().getServer()).isDying(previousLife)
                &&h.getLevel().getBlockState(core.getBlockPos().east()).is(Blocks.IRON_BLOCK)
                &&core.modules().find(module.id())==module,"Release scheduled destruction or removed the machine's configuration");
        h.succeed();
    }
    @GameTest(template="combat",batch="survival_attack_tokens")
    public static void SurvivalAttacksChargeOnlyForFiredArrowsAndSuccessfulHits(GameTestHelper h){
        var core=fixture(h,SableLife.SURVIVAL_LIFE_CORE.get());
        var player=TestActors.creative(h.getLevel());
        player.setPos(ModuleArena.corePosition(core));
        var copper=net.minecraft.world.item.Items.COPPER_INGOT;
        h.assertTrue(core.depositSurvivalTokens(player,new net.minecraft.world.item.ItemStack(copper,11)),"Deposit failed");

        var anchor=ModuleAnchor.create(core,core.getBlockPos().east(),Direction.EAST);
        var arrow=core.modules().add("arrow");
        arrow.anchors(List.of(anchor));
        arrow.edit("arrows",2,"");
        arrow.edit("speed",1,"");
        arrow.edit("damage",5,"");
        var quote=new net.minecraft.nbt.CompoundTag();
        SurvivalTokens.writeEstimate(core,arrow,quote);
        h.assertTrue(quote.getLong("TokenEstimate")==10,"Arrow quote did not reflect speed, count and damage");

        core.tryActivate(player);
        core.modules().tick(core);
        h.assertTrue(core.survivalTokenBalance()==0,"The arrow volley did not charge the quoted amount");
        arrow.edit("enabled",0,"");

        var melee=core.modules().add("melee");
        melee.anchors(List.of(anchor,anchor));
        melee.edit("damage",3,"");
        melee.edit("meleeLength",8,"");
        melee.edit("knockback",0,"");
        var point=MeleeGeometry.frame(core,melee).center();
        var cow=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());
        cow.setNoAi(true);
        cow.setNoGravity(true);
        cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);
        cow.setHealth(20);
        h.getLevel().getChunk(BlockPos.containing(point));
        cow.setPos(point.add(0,-.5,0));
        h.getLevel().addFreshEntity(cow);
        try{
            core.modules().tick(core);
            h.assertTrue(cow.getHealth()==20,"Melee damaged a target without sufficient funds");
            h.assertTrue(core.depositSurvivalTokens(player,new net.minecraft.world.item.ItemStack(copper)),"Refill failed");
            core.modules().tick(core);
            h.assertTrue(cow.getHealth()==17&&core.survivalTokenBalance()==7,"Melee did not charge for one successful hit");
        }finally{
            cow.discard();
        }
        h.succeed();
    }

    @GameTest(template="combat",batch="survival_armor_billing",timeoutTicks=40)
    public static void SurvivalArmorPaysForActiveTimeAndSuspendsWhenCreditRunsOut(GameTestHelper h){
        var core=fixture(h,SableLife.SURVIVAL_LIFE_CORE.get());
        var player=TestActors.creative(h.getLevel());
        player.setPos(ModuleArena.corePosition(core));
        h.assertTrue(ModuleAvailability.enabled(core,"armor"),"Survival armor was unavailable");
        var module=core.modules().add("armor");
        module.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.UP)));
        core.wireless().add();
        core.wireless().channel(0).set(0,1);
        core.wireless().channel(0).set(6,3);
        var rule=module.armorRules().add();
        rule.edit("modifier",-50);
        rule.edit("when",1);
        var quote=new net.minecraft.nbt.CompoundTag();
        SurvivalTokens.writeEstimate(core,module,quote);
        h.assertTrue(quote.getDouble("TokenEstimatePerSecond")==50,"Armor quote did not use percentage-point pricing");
        rule.edit("modifier",-100);
        h.assertTrue(core.depositSurvivalTokens(player,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT,11)),
                "Armor funding deposit failed");
        core.tryActivate(player);
        h.assertTrue(core.spendAttackTokens(5)&&core.survivalTokenBalance()==5,"Armor fixture balance was incorrect");
        core.modules().armorBilling.tick(core);
        h.assertTrue(core.survivalTokenBalance()==0,"Armor did not charge without an incoming hit");
        h.assertTrue(ModuleArmor.damage(core,core.getBlockPos().east(),20)==2
                &&ModuleArmor.damage(core,core.getBlockPos().east(),20)==2&&core.survivalTokenBalance()==0,
                "Paid armor failed or repeated damage charged again within the tick");
        h.runAfterDelay(21,()->{
            h.assertTrue(ModuleArmor.damage(core,core.getBlockPos().east(),20)==20,
                    "Armor stayed protected after its paid time ran out");
            h.assertTrue(core.survivalTokenBalance()==0,"Insufficient funds caused a negative balance");
            module.edit("enabled",0,"");
            h.succeed();
        });
    }

    @GameTest(template="combat",batch="survival_armor_limits",timeoutTicks=40)
    public static void SurvivalArmorCapsAtNinetyAndBillsNinetyPerSecond(GameTestHelper h){
        var core=fixture(h,SableLife.SURVIVAL_LIFE_CORE.get());var player=TestActors.creative(h.getLevel());player.setPos(ModuleArena.corePosition(core));
        var module=core.modules().add("armor");module.anchors(List.of(ModuleAnchor.create(core,core.getBlockPos(),Direction.UP)));
        core.wireless().add();core.wireless().channel(0).set(0,1);core.wireless().channel(0).set(6,3);
        var row=module.armorRules().add();row.edit("when",1);var menu=new ArmorRuleMenu(2,player.getInventory(),core,module,row.id());
        h.assertTrue(menu.snapshot().getDouble("MinimumModifier")==-90&&!menu.action(player,core.modules().revision(),row.id(),"modifier",-91),"Survival menu accepted more than 90 percent reduction");
        h.assertTrue(menu.action(player,core.modules().revision(),row.id(),"modifier",-90),"Survival menu rejected 90 percent reduction");
        row.edit("modifier",-100);SurvivalLifeCorePolicy.constrain(module);h.assertTrue(row.modifier()==-90,"Old immunity rules were not clamped");
        var quote=new net.minecraft.nbt.CompoundTag();SurvivalTokens.writeEstimate(core,module,quote);h.assertTrue(quote.getDouble("TokenEstimatePerSecond")==90,"90 percent quote was not 90/s");
        core.depositSurvivalTokens(player,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT,30));core.tryActivate(player);
        h.assertTrue(core.survivalTokenBalance()==200,"Armor starting funds incorrect");core.modules().armorBilling.tick(core);
        h.assertTrue(ModuleArmor.damage(core,core.getBlockPos().east(),20)==2,"Runtime exceeded survival reduction cap");
        h.runAfterDelay(20,()->{h.assertTrue(core.survivalTokenBalance()==110,"Twenty armor ticks did not cost exactly 90 Energy: "+core.survivalTokenBalance());module.edit("enabled",0,"");h.succeed();});
    }

    @GameTest(template="combat",batch="survival_cast_tokens")
    public static void SurvivalCastQuotesMatchServerDebits(GameTestHelper h){
        var core=fixture(h,SableLife.SURVIVAL_LIFE_CORE.get());
        var player=TestActors.creative(h.getLevel());
        player.setPos(ModuleArena.corePosition(core));
        var sphere=new VirtualModule("sphere_blast");
        sphere.edit("blastRadius",4,"");
        sphere.edit("damage",20,"");
        h.assertTrue(tokenQuote(core,sphere)==80,"Sphere Blast did not use radius-based billing");
        var pulse=new VirtualModule("pulse");
        pulse.edit("radius",8,"");
        pulse.edit("damage",5,"");
        pulse.edit("knockback",1,"");
        h.assertTrue(tokenQuote(core,pulse)==48,"Shockwave radius or knockback price is incorrect");
        pulse.edit("knockback",2,"");
        h.assertTrue(tokenQuote(core,pulse)==56,"Shockwave knockback did not increase cost");
        var freeze=new VirtualModule("freeze");
        long baseFreeze=tokenQuote(core,freeze);
        freeze.edit("freezeHeight",32,"");
        h.assertTrue(tokenQuote(core,freeze)==baseFreeze*2,"Freeze height was not billed");
        freeze.edit("freezeRadius",16,"");
        h.assertTrue(tokenQuote(core,freeze)==baseFreeze*4,"Freeze radius was not billed");
        freeze.edit("fieldShape",1,"");
        freeze.edit("fieldLength",8,"");
        freeze.edit("fieldWidth",8,"");
        freeze.edit("freezeHeight",2,"");
        freeze.edit("freezeDuration",40,"");
        h.assertTrue(tokenQuote(core,freeze)==16,"Rectangular freeze reach or height was not billed");
        freeze.edit("fieldLength",16,"");
        h.assertTrue(tokenQuote(core,freeze)==24,"Rectangular freeze length did not increase cost");
        var homing=new VirtualModule("homing");
        homing.edit("damage",20,"");
        homing.edit("blastDamage",20,"");
        homing.edit("blastRadius",4,"");
        long direct=tokenQuote(core,homing);
        h.assertTrue(direct>=20,"Homing speed discounted the base damage");
        homing.edit("explode",1,"");
        h.assertTrue(tokenQuote(core,homing)-direct==80,"Homing blast cost did not use radius × damage");
        for(var entry:Map.of("slash","slashSpeed","quake","quakeSpeed","flame","flameSpeed","ground_strike","strikeSpeed").entrySet()){
            var attack=new VirtualModule(entry.getKey());
            long before=tokenQuote(core,attack);
            attack.edit(entry.getValue(),attack.value(entry.getValue())*2,"");
            h.assertTrue(tokenQuote(core,attack)>before,"Faster attack was not more expensive: "+entry.getKey());
        }
        var gun=new VirtualModule("gun");
        long shortGun=tokenQuote(core,gun);
        gun.edit("range",128,"");
        h.assertTrue(tokenQuote(core,gun)==shortGun*2,"Doubling Gun range did not increase its direct-fire cost");
        var strike=new VirtualModule("ground_strike");
        strike.edit("strikeRange",6,"");
        strike.edit("strikeStep",3,"");
        h.assertTrue(ModuleGroundStrike.plannedPointCount(strike)==3,"Line point count lost the center");
        long line=tokenQuote(core,strike);
        h.assertTrue(line==120,"Ground Strike did not bill radius × damage × speed × all points");
        strike.edit("strikeRange",12,"");
        h.assertTrue(ModuleGroundStrike.plannedPointCount(strike)==5&&tokenQuote(core,strike)>line,
                "Extending Ground Strike did not charge for the extra points");
        strike.edit("strikeMode",1,"");
        h.assertTrue(ModuleGroundStrike.plannedPointCount(strike)==33,"Radial point count did not include all rays");
        strike.edit("strikeMode",3,"");
        strike.edit("strikeRandomCount",20,"");
        h.assertTrue(ModuleGroundStrike.plannedPointCount(strike)==20,"Random Ground Strike omitted configured points");
        for(var type:SurvivalTokens.CAST_TYPES){
            var module=new VirtualModule(type);
            var quote=new net.minecraft.nbt.CompoundTag();
            SurvivalTokens.writeEstimate(core,module,quote);
            core.spendAttackTokens(core.survivalTokenBalance());
            long cost=quote.getLong("TokenEstimate"),before=core.survivalTokenBalance();
            h.assertTrue(cost>0&&quote.getString("TokenFormula").equals(type),"Missing cost for "+type);
            int units=type.equals("ground_strike")?ModuleGroundStrike.plannedPointCount(module):1;
            h.assertTrue(!SurvivalTokens.chargeCast(core,module,units)&&core.survivalTokenBalance()==before,
                    "An unaffordable cast was charged or accepted for "+type);
            int copper=(int)((cost+9-before)/10);
            h.assertTrue(core.depositSurvivalTokens(player,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT,copper)),
                    "Test deposit failed for "+type);
            long funded=core.survivalTokenBalance();
            h.assertTrue(SurvivalTokens.chargeCast(core,module,units)&&core.survivalTokenBalance()==funded-cost,
                    "Quote differed from the actual debit for "+type);
        }
        h.succeed();
    }

    private static long tokenQuote(HeartBlockEntity core,VirtualModule module){
        var quote=new net.minecraft.nbt.CompoundTag();
        SurvivalTokens.writeEstimate(core,module,quote);
        return quote.getLong("TokenEstimate");
    }

    @GameTest(template="combat",batch="survival_token_transfer")
    public static void SurvivalCoreDropRedeemsBalanceOnlyOnce(GameTestHelper h){
        var block=SableLife.SURVIVAL_LIFE_CORE.get();
        var core=fixture(h,block);
        var level=h.getLevel();
        var player=TestActors.creative(level);
        player.setPos(ModuleArena.corePosition(core));
        player.setGameMode(GameType.SURVIVAL);
        h.assertTrue(core.canAccessEditor(player)&&core.canEdit(player),"Survival editor was still locked");
        var copper=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COPPER_INGOT,3);
        h.assertTrue(core.depositSurvivalTokens(player,copper),"Survival recharge failed");
        var stack=new net.minecraft.world.item.ItemStack(SableLife.SURVIVAL_LIFE_CORE_ITEM.get());
        var drop=new net.minecraft.world.entity.item.ItemEntity(level,0,0,0,stack);
        var drops=new net.neoforged.neoforge.event.level.BlockDropsEvent(level,core.getBlockPos(),core.getBlockState(),
                core,new ArrayList<>(List.of(drop)),player,net.minecraft.world.item.ItemStack.EMPTY);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(drops);
        var itemData=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        h.assertTrue(!drops.isCanceled()&&itemData.getLong("SableLifeTokenDisplay")==30&&core.survivalTokenBalance()==0,
                "Mining drop did not carry the remaining Token balance");
        var first=h.absolutePos(new BlockPos(1,6,1));
        var second=first.east();
        level.setBlockAndUpdate(first,block.defaultBlockState());
        level.setBlockAndUpdate(second,block.defaultBlockState());
        block.setPlacedBy(level,first,block.defaultBlockState(),player,stack);
        block.setPlacedBy(level,second,block.defaultBlockState(),player,stack.copy());
        h.runAfterDelay(3,()->{
            var restored=(HeartBlockEntity)level.getBlockEntity(first);
            var duplicate=(HeartBlockEntity)level.getBlockEntity(second);
            h.assertTrue(restored.survivalTokenBalance()==30&&duplicate.survivalTokenBalance()==0,
                    "Placement lost the original balance or duplicated it");
            h.succeed();
        });
    }

    @GameTest(template="combat",batch="settings_release_alive")
    public static void SettingsReleaseWorksWhileLiveAndPreservesConfigurationAndBody(GameTestHelper h){
        var c=fixture(h);var p=TestActors.creative(h.getLevel());p.setPos(ModuleArena.corePosition(c));c.configure(1500,60);c.editCore(p,"name",0,"Release test");var m=c.modules().add("particles");m.edit("name",0,"Remember me");m.edit("density",24,"");var moduleId=m.id();var body=dev.ryanhcode.sable.Sable.HELPER.getContaining(h.getLevel(),c.getBlockPos());var bodyId=body.getUniqueId();c.tryActivate(p);c.damage(100);var creature=c.creatureId();var menu=new HeartSettingsMenu(1,p.getInventory(),c,3);
        h.assertTrue(!menu.snapshot().getBoolean("Editable")&&menu.snapshot().getBoolean("ReleaseAllowed"),"Live release was tied to dormant-only editing");
        h.assertTrue(menu.action(p,"release",0,"")&&c.health().state()==HeartState.DORMANT&&c.health().current()==1500&&!c.creatureId().equals(creature),"Settings Release did not reset life/runtime identity");
        h.assertTrue(c.creatureName().equals("Release test")&&c.modules().find(moduleId).value("density")==24&&c.modules().find(moduleId).name().equals("Remember me"),"Release lost authored settings");
        h.assertTrue(!body.isRemoved()&&body.getUniqueId().equals(bodyId)&&h.getLevel().getBlockState(c.getBlockPos().east()).is(Blocks.IRON_BLOCK),"Release dismantled the machine");h.succeed();
    }
    @GameTest(template="combat",batch="settings_release_permission")
    public static void SettingsReleaseRejectsSurvivalAndCanCancelDeathCountdown(GameTestHelper h){
        var c=fixture(h);var p=TestActors.creative(h.getLevel());p.setPos(ModuleArena.corePosition(c));c.tryActivate(p);var menu=new HeartSettingsMenu(1,p.getInventory(),c);var id=c.creatureId();p.setGameMode(GameType.SURVIVAL);
        h.assertTrue(!menu.action(p,"release",0,"")&&c.health().state()==HeartState.ALIVE&&c.creatureId().equals(id),"Survival actor released a living creature");p.setGameMode(GameType.CREATIVE);c.forceDeath();h.assertTrue(c.health().state()==HeartState.DYING&&menu.action(p,"release",0,"")&&c.health().state()==HeartState.DORMANT,"Release could not cancel death countdown");h.succeed();
    }
    @GameTest(template="combat",batch="dashboard_release_shared")
    public static void DashboardUsesTheSameReleaseAction(GameTestHelper h){
        var c=fixture(h);var p=TestActors.creative(h.getLevel());p.setPos(ModuleArena.corePosition(c));c.tryActivate(p);var menu=new HeartDashboardMenu(1,p.getInventory(),c);
        h.assertTrue(menu.action(p,c.modules().revision(),new UUID(0,0),"release","",0,"")&&c.health().state()==HeartState.DORMANT,"Dashboard release changed while adding settings entry point");h.succeed();
    }
}
