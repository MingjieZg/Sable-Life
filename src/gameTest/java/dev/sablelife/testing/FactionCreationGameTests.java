package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.heart.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_relations_tests") @PrefixGameTestTemplate(false)
public final class FactionCreationGameTests {
    private static ServerPlayer player(GameTestHelper h, boolean op) {
        return new FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),op?"FactionAdmin":"FactionPlayer")) {
            @Override public boolean hasPermissions(int permission) { return op && permission <= 2; }
        };
    }

    @GameTest(template="combat",batch="faction_creation_policy")
    public static void PersonalLimitUsesOwnershipAndSurvivesReloadWithConfigAndAdminExceptions(GameTestHelper h) {
        var config=(ModConfigSpec.IntValue)SableLifeServerConfig.SPEC.getValues().get(List.of("maximumFactionsPerPlayer"));int previous=config.get();
        try {
            config.set(1);var data=new FactionRegistry();var owner=player(h,false);var friend=player(h,false);var admin=player(h,true);
            h.assertTrue(data.creationError(owner,"  ").equals("faction_name_required")&&data.create(owner,"")==null,"Empty name accepted or generic error returned");
            h.assertTrue(data.creationError(owner,"Wilds").equals("faction_name_reserved")&&data.creationError(owner,"a\nb").equals("faction_name_invalid"),"Invalid/reserved name not distinguished");
            var first=data.create(owner,"First");var other=data.create(friend,"Friend");
            h.assertTrue(first!=null&&other!=null&&data.creationError(owner,"second").equals("faction_player_limit")&&data.create(owner,"second")==null,"Normal owner exceeded personal limit");
            h.assertTrue(data.creationError(friend," FIRST ").equals("faction_name_duplicate"),"Duplicate name check lost strip/case matching");
            h.assertTrue(data.joinPlayer(owner,other)&&data.detail(owner,first).getBoolean("Member")&&data.detail(owner,other).getBoolean("Member"),"Creation cap restricted multiple memberships");
            data=FactionRegistry.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(data.creationError(owner,"After restart").equals("faction_player_limit"),"Reload forgot created faction count");
            h.assertTrue(data.delete(owner,first)&&data.create(owner,"Replacement")!=null,"Deleting an owned faction did not free its slot");
            h.assertTrue(data.create(admin,"Admin one")!=null&&data.create(admin,"Admin two")!=null,"Permission level 2 did not bypass personal cap");
            config.set(2);data=new FactionRegistry();h.assertTrue(data.create(owner,"One")!=null&&data.create(owner,"Two")!=null&&data.create(owner,"Three")==null,"Configured two-faction cap ignored");
            config.set(0);h.assertTrue(data.create(owner,"Three")!=null&&data.create(owner,"Four")!=null,"Zero did not disable personal cap");
            config.set(1);data=FactionRegistry.load(data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(data.snapshot(owner).size()==4&&data.create(owner,"Five")==null,"Lowering cap removed saved factions or allowed a new one");
            h.succeed();
        } finally { config.set(previous); }
    }

    @GameTest(template="combat",batch="faction_creation_feedback")
    public static void MenuReturnsSpecificEmptyNameAndLimitReasons(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(2,3,2));h.getLevel().setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());
        var core=(HeartBlockEntity)h.getLevel().getBlockEntity(pos);var actor=player(h,false);actor.setGameMode(net.minecraft.world.level.GameType.CREATIVE);actor.setPos(pos.getCenter());
        var menu=new HeartSettingsMenu(1,actor.getInventory(),core);
        h.assertTrue(!menu.action(actor,"relation:create",0,"")&&menu.snapshot().getString("RelationStatus").equals("faction_name_required"),"Menu did not explain missing name");
        h.assertTrue(menu.action(actor,"relation:create",0,"Menu "+UUID.randomUUID().toString().substring(0,8)),"Valid faction could not be created");
        h.assertTrue(!menu.action(actor,"relation:create",0,"Extra "+UUID.randomUUID().toString().substring(0,8))&&menu.snapshot().getString("RelationStatus").equals("faction_player_limit")&&menu.snapshot().getInt("FactionCreationLimit")==1,"Menu did not explain personal cap");
        FactionRegistry.get(actor.server).delete(actor,core.relations().faction());h.succeed();
    }
}
