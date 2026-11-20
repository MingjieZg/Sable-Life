package dev.sablelife.testing;

import dev.sablelife.compat.photomancy.PhotomancyCompatibility;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class CommandHelpGameTests {
    @GameTest(template="combat",batch="command_help")
    public static void OrdinaryPlayersCanReadHelpButNotUseAdminCommands(GameTestHelper h) throws Exception {
        var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPermission(0);
        var dispatcher=source.getServer().getCommands().getDispatcher();
        h.assertTrue(dispatcher.execute("sablelife help",source)==1&&dispatcher.execute("sablelife",source)==1,"Public help unavailable");
        for(String cmd:java.util.List.of("sablelife inspect ~ ~ ~","sablelife damage ~ ~ ~ 1","sablelife blueprint spawn test false","sablelife cleanup scan all","sablelife_respawn cancel")) {
            boolean denied=false;try{dispatcher.execute(cmd,source);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected){denied=true;}
            h.assertTrue(denied,"Admin command accessible without permission: "+cmd);
        }
        h.succeed();
    }
    @GameTest(template="combat",batch="command_optional")
    public static void CommandRegistrationAndHelpDoNotRequirePhotomancy(GameTestHelper h) throws Exception {
        var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPermission(2);
        var dispatcher=source.getServer().getCommands().getDispatcher();
        h.assertTrue(dispatcher.execute("sablelife help",source)==1,"Help failed");
        var parsed=dispatcher.parse("sablelife blueprint spawn ",source);
        dispatcher.getCompletionSuggestions(parsed).get();
        if(!PhotomancyCompatibility.available())h.assertTrue(dispatcher.execute("sablelife blueprint spawn nonexistent false",source)==0,"Missing optional API did not reject safely");
        h.succeed();
    }
}
