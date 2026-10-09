package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sablelife_core_ui_tests")
@PrefixGameTestTemplate(false)
public final class CorePreviewGameTests {
    @GameTest(template="combat")
    public static void TransparentCoreKeepsAllAdjacentFacesWithoutChangingCollision(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(2,5,2));var level=h.getLevel();
        for(var block:java.util.List.of(SableLife.HEART.get(),SableLife.REDSTONE_CORE.get()))for(boolean active:new boolean[]{false,true}){
            var state=block.defaultBlockState().setValue(dev.sablelife.heart.HeartBlock.ACTIVE,active);level.setBlockAndUpdate(pos,state);
            h.assertTrue(!state.canOcclude(),"Transparent core still masks adjacent faces");
            h.assertTrue(state.getCollisionShape(level,pos).bounds().equals(new net.minecraft.world.phys.AABB(0,0,0,1,1,1)),"Transparency fix changed physical collision");
            for(var side:net.minecraft.core.Direction.values()){
                var adjacent=pos.relative(side);var neighbor=net.minecraft.world.level.block.Blocks.OAK_LOG.defaultBlockState();level.setBlockAndUpdate(adjacent,neighbor);
                h.assertTrue(net.minecraft.world.level.block.Block.shouldRenderFace(neighbor,level,adjacent,side.getOpposite(),pos),"Neighbor face disappeared behind transparent core: "+side);
            }
            var core=(HeartBlockEntity)level.getBlockEntity(pos);core.setMaterial(net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());h.assertTrue(core.getMaterial().is(net.minecraft.world.level.block.Blocks.IRON_BLOCK),"Camouflage no longer applies");
        }h.succeed();
    }
    @GameTest(template="combat")
    public static void ExperienceUnitsPreservePointsAndLootBackReturnsToModules(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(2,6,2));h.getLevel().setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());var heart=(HeartBlockEntity)h.getLevel().getBlockEntity(pos);var player=new MenuPlayer(h.getLevel());player.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
        h.assertTrue(heart.editCore(player,"xp",1000,"")&&heart.editCore(player,"xpLevels",1,""),"Experience mode edit rejected");
        h.assertTrue(heart.deathExperience()==1000&&heart.displayedExperience()==26,"Unit switch changed exact reward");
        h.assertTrue(heart.editCore(player,"xp",5,"")&&heart.deathExperience()==55,"Level reward did not convert to orb points");
        h.assertTrue(!heart.editCore(player,"xp",101,"")&&!heart.editCore(player,"xp",5.5,""),"Invalid level amount accepted");
        var data=heart.copyConfiguration();h.assertTrue(data.getBoolean("DeathExperienceLevels")&&data.getInt("DeathExperience")==55,"Copy lost units or raw reward");
        h.assertTrue(heart.editCore(player,"xpLevels",0,"")&&heart.deathExperience()==55&&heart.displayedExperience()==55,"Switching to points changed reward");
        var module=heart.modules().add("sound");DashboardSelection.set(heart,player.getUUID(),module.id());heart.openDeathLootMenu(player);var menu=(HeartLootMenu)player.containerMenu;
        menu.clicked(53,0,net.minecraft.world.inventory.ClickType.PICKUP,player);h.assertTrue(player.containerMenu instanceof HeartDashboardMenu,"Loot Back did not open Modules");h.assertTrue(((HeartDashboardMenu)player.containerMenu).snapshot().getUUID("Selected").equals(module.id()),"Loot Back lost module selection");
        heart.health().activate();h.assertTrue(!heart.canEdit(player),"Loot fixture is not read-only");heart.openDeathLootMenu(player);((HeartLootMenu)player.containerMenu).clicked(53,0,net.minecraft.world.inventory.ClickType.PICKUP,player);h.assertTrue(player.containerMenu instanceof HeartDashboardMenu,"Read-only loot Back failed");h.succeed();
    }
    /** Menu-capable fake actor avoids unrelated login events from third-party mods. */
    private static final class MenuPlayer extends net.neoforged.neoforge.common.util.FakePlayer {
        private int nextMenu;
        MenuPlayer(net.minecraft.server.level.ServerLevel level){super(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"CorePreviewAuthor"));setGameMode(net.minecraft.world.level.GameType.CREATIVE);}
        @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> writer){
            closeContainer();containerMenu=provider.createMenu(++nextMenu,getInventory(),this);return java.util.OptionalInt.of(nextMenu);
        }
    }
    @GameTest(template="combat")
    public static void LongEditorKeysRoundTripWithoutDisconnectEncoding(GameTestHelper h){
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            for(String key:java.util.List.of("soundEditor:deathTailSound","soundEditor:vanishTailSound","soundEditor:vanishSound")){
                var request=new ModuleNetworking.Action(1,0,java.util.UUID.randomUUID(),"core",key,0,"");ModuleNetworking.Action.CODEC.encode(buffer,request);
                h.assertTrue(ModuleNetworking.Action.CODEC.decode(buffer).equals(request),"Editor key failed codec round trip: "+key);
            }
        }finally{buffer.release();}h.succeed();
    }
    @GameTest(template="combat")
    public static void RestoreAffectsOnlyCurrentPresentationAndDiscardsLegacySecondary(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(2,6,2));h.getLevel().setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());var heart=(HeartBlockEntity)h.getLevel().getBlockEntity(pos);
        var player=new MenuPlayer(h.getLevel());player.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);heart.configure(123,77);
        heart.editCore(player,"deathCount",2,"");heart.editCore(player,"deathPattern",3,"");heart.editCore(player,"vanishCount",12,"");
        var mix=dev.sablelife.sound.SoundTimeline.legacy("deathSound","minecraft:block.note_block.bass",2,.8,12);heart.deathPresentation().soundTimeline("deathSound",mix);
        h.assertTrue(heart.editCore(player,"deathRestore",0,""),"Death restore rejected");
        var death=heart.deathPresentation();h.assertTrue(death.value("deathCount")==80&&death.value("deathPattern")==0&&death.particleSelection().particles().contains("minecraft:firework")&&death.particleSelection().particles().contains("minecraft:flash"),"Death defaults were not restored");
        h.assertTrue(death.soundTimeline("deathSound").rows().size()==1&&death.soundTimeline("deathSound").rows().getFirst().sound().equals("minecraft:entity.firework_rocket.blast"),"Custom primary mix survived restore");
        h.assertTrue(heart.removalPresentation().value("deathCount")==12&&heart.health().maximum()==123&&heart.health().deathDelay()==77,"Restore changed other stage or health/delay");
        h.assertTrue(heart.editCore(player,"vanishRestore",0,"")&&heart.removalPresentation().value("deathCount")==6&&heart.removalPresentation().particleSelection().particles().contains("minecraft:poof")&&heart.removalPresentation().soundTimeline("deathSound").rows().isEmpty(),"Vanish defaults did not restore");
        var old=death.save();old.putString("deathTailSound","minecraft:entity.firework_rocket.twinkle");old.getCompound("SoundTimelines").put("deathTailSound",mix.save());var loaded=new DeathPresentation();loaded.load(old);
        h.assertTrue(loaded.text("deathTailSound").isEmpty()&&!loaded.save().getCompound("SoundTimelines").contains("deathTailSound")&&loaded.soundTimeline("deathTailSound").rows().isEmpty(),"Legacy secondary remained enabled");
        h.assertTrue(!dev.sablelife.sound.SoundTimelineMenu.open(player,heart,null,"deathTailSound","death"),"Removed secondary editor still opens");h.succeed();
    }
    @GameTest(template="combat")
    public static void PreviewClosesEditorAndReopensAtTheSavedCorePage(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(2,6,2));h.getLevel().setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
        var heart=(HeartBlockEntity)h.getLevel().getBlockEntity(pos);var player=new MenuPlayer(h.getLevel());player.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
        for(int page:new int[]{2,3}) {
            heart.openSettings(player,page);h.assertTrue(player.containerMenu instanceof HeartSettingsMenu,"Core editor did not open");
            var menu=(HeartSettingsMenu)player.containerMenu;var before=heart.health().state();float hp=heart.health().current();
            h.assertTrue(menu.action(player,page==2?"deathPreview":"vanishPreview",0,""),"Preview request rejected");
            h.assertTrue(player.containerMenu==player.inventoryMenu,"Preview left the editor open");
            h.assertTrue(heart.health().state()==before&&heart.health().current()==hp,"Preview changed life state/HP");
            heart.openLastEditor(player);h.assertTrue(player.containerMenu instanceof HeartSettingsMenu&&((HeartSettingsMenu)player.containerMenu).snapshot().getInt("InitialPage")==page,"Reopen lost effect page");
        }
        var menu=(HeartSettingsMenu)player.containerMenu;h.assertTrue(menu.action(player,"viewPage",4,""),"Page navigation failed");
        h.assertTrue(!menu.action(player,"viewPage",Double.NaN,"")&&!menu.action(player,"viewPage",10,""),"Invalid page accepted");
        player.closeContainer();heart.openLastEditor(player);h.assertTrue(((HeartSettingsMenu)player.containerMenu).snapshot().getInt("InitialPage")==4,"Normal close lost settings page");
        heart.openDashboard(player);h.assertTrue(CoreEditorPosition.get(heart,player.getUUID()).section()==CoreEditorPosition.Section.MODULES,"Explicit Modules navigation ignored");
        heart.openSettings(player);h.assertTrue(((HeartSettingsMenu)player.containerMenu).snapshot().getInt("InitialPage")==4,"Switching sections erased last settings page");
        h.assertTrue(CoreEditorPosition.get(heart,java.util.UUID.randomUUID()).section()==CoreEditorPosition.Section.MODULES,"Viewer navigation leaked");
        h.succeed();
    }
}
