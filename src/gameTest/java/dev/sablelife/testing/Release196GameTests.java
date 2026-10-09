package dev.sablelife.testing;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import dev.sablelife.ownership.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_audit_tests") @PrefixGameTestTemplate(false)
public final class Release196GameTests {
    @GameTest(template="combat",batch="copycat_death_material")
    public static void CopycatDeathCleanupDoesNotDropItsConsumedMaterial(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,6,3));
        level.setBlockAndUpdate(pos,AllBlocks.COPYCAT_PANEL.getDefaultState());
        var copycat=(CopycatBlockEntity)level.getBlockEntity(pos);
        copycat.setMaterial(Blocks.DIAMOND_BLOCK.defaultBlockState());copycat.setConsumedItem(new ItemStack(Items.DIAMOND_BLOCK));
        var owner=UUID.randomUUID();var ownership=BodyOwnership.get(level);ownership.bind(pos,owner);
        ownership.cleanLoaded(level,owner,256);
        h.assertTrue(level.getBlockState(pos).isAir()&&!ownership.hasOwned(owner),"Copycat was not cleaned");
        var region=new AABB(pos).inflate(2);
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,region).isEmpty(),"Copycat onRemove dropped disguise material");
        // The same callback outside a life body must continue to return the material.
        level.setBlockAndUpdate(pos,AllBlocks.COPYCAT_PANEL.getDefaultState());
        copycat=(CopycatBlockEntity)level.getBlockEntity(pos);copycat.setMaterial(Blocks.DIAMOND_BLOCK.defaultBlockState());copycat.setConsumedItem(new ItemStack(Items.DIAMOND_BLOCK));
        level.removeBlock(pos,false);
        h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,region).stream().anyMatch(e->e.getItem().is(Items.DIAMOND_BLOCK)),"Ordinary Copycat material recovery was suppressed");h.succeed();
    }
    @GameTest(template="combat",batch="sphere_owner_terrain")
    public static void TerrainOnlyBlastRetainsOwnerProtectionWithExplosionPermissionOn(GameTestHelper h) {
        var level=h.getLevel();var owned=h.absolutePos(new BlockPos(3,6,3));var terrain=owned.east();var id=UUID.randomUUID();
        level.setBlockAndUpdate(owned,Blocks.GLASS.defaultBlockState());level.setBlockAndUpdate(terrain,Blocks.GLASS.defaultBlockState());BodyOwnership.get(level).bind(owned,id);
        LifeLifecycle.get(level.getServer()).setBreakable(id,true);LifeLifecycle.get(level.getServer()).setExplodable(id,true);
        var point=owned.getCenter();var explosion=new Explosion(level,null,null,new ModuleTerrainExplosion(id),point.x,point.y,point.z,4,false,Explosion.BlockInteraction.DESTROY,net.minecraft.core.particles.ParticleTypes.POOF,net.minecraft.core.particles.ParticleTypes.POOF,net.minecraft.core.Holder.direct(net.minecraft.sounds.SoundEvents.EMPTY));
        explosion.getToBlow().addAll(List.of(owned,terrain));
        var event=new net.neoforged.neoforge.event.level.ExplosionEvent.Detonate(level,explosion,new ArrayList<>());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        h.assertTrue(!event.getAffectedBlocks().contains(owned)&&event.getAffectedBlocks().contains(terrain),"Source-less module blast lost owner or protected ordinary terrain");
        explosion.finalizeExplosion(false);
        h.assertTrue(level.getBlockState(owned).is(Blocks.GLASS)&&level.getBlockState(terrain).isAir(),"Explosion finalize ignored owner filter");
        BodyOwnership.get(level).release(id);h.succeed();
    }
    @GameTest(template="combat",batch="wireless_inventory_picker")
    public static void WirelessHasNoHiddenInventoryAndIndependentPickerPreservesChannel(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(3,6,3));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());var core=(HeartBlockEntity)level.getBlockEntity(pos);
        core.wireless().add();core.wireless().add();var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PickerAuthor")) {
            @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer) {
                closeContainer();containerMenu=provider.createMenu(3,getInventory(),this);return java.util.OptionalInt.of(3);
            }
        };player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.setPos(pos.getCenter());
        var main=new HeartWirelessMenu(1,player.getInventory(),core,1);
        h.assertTrue(main.slots.size()==2&&main.slots.stream().noneMatch(s->s.container==player.getInventory()),"Main wireless page still exposes inventory slots");
        var picker=new ModuleFrequencyMenu(2,player.getInventory(),core,1);h.assertTrue(picker.slots.size()==38,"Independent picker omitted inventory");
        picker.setCarried(new ItemStack(Items.EMERALD,23));picker.clicked(0,0,ClickType.PICKUP,player);
        h.assertTrue(core.wireless().channel(1).first().is(Items.EMERALD)&&core.wireless().channel(0).first().isEmpty()&&picker.getCarried().getCount()==23,"Picker changed wrong channel or consumed item");
        h.assertTrue(picker.clickMenuButton(player,0)&&player.containerMenu instanceof HeartWirelessMenu back&&back.selected()==1,"Back lost selected channel");
        player.closeContainer();h.succeed();
    }
}
