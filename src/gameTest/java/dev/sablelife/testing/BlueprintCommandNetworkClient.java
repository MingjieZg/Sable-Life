package dev.sablelife.testing;

import dev.sablelife.client.ManualScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import java.util.*;

@EventBusSubscriber(modid="sablelife_tests",value=Dist.CLIENT)
public final class BlueprintCommandNetworkClient {
    private static boolean started,joined;private static int ticks,phase,age;private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestions;
    private static final List<String> messages=new ArrayList<>();
    @SubscribeEvent public static void message(ClientChatReceivedEvent.System event) {
        if(Boolean.getBoolean("sablelife.commandNetworkClient"))messages.add(event.getMessage().getString());
    }
    private static void shot(String name)throws Exception {
        var mc=Minecraft.getInstance();var folder=mc.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(folder);
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(folder.resolve(name+".png"));}
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event)throws Exception {
        if(!Boolean.getBoolean("sablelife.commandNetworkClient"))return;
        var mc=Minecraft.getInstance();boolean second=System.getProperty("sablelife.networkRole","A").equals("B");
        if(++ticks>2600)throw new AssertionError("Command client timeout phase="+phase+" messages="+messages);
        if(!started&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
            started=true;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            System.out.println("BLUEPRINT_COMMAND_CLIENT_ORIGIN "+dev.sablelife.SableLife.class.getProtectionDomain().getCodeSource().getLocation());
            String address="127.0.0.1:"+Integer.getInteger("sablelife.releaseNetworkPort",25591);
            ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Command test",address,ServerData.Type.OTHER),false,null);return;
        }
        if(mc.player!=null)joined=true;
        if(joined&&mc.player==null){System.out.println("BLUEPRINT_COMMAND_CLIENT_COMPLETE phase="+phase);mc.stop();return;}
        if(mc.player==null||mc.getOverlay()!=null)return;age++;
        if(phase==0&&messages.contains("SL_CMD_READY")) {
            mc.player.connection.sendCommand("sablelife help");
            if(!second){var dispatcher=mc.player.connection.getCommands();suggestions=dispatcher.getCompletionSuggestions(dispatcher.parse("sablelife blueprint spawn ",mc.player.connection.getSuggestionsProvider()));}
            phase=1;age=0;
        } else if(phase==1&&age>30&&messages.stream().anyMatch(s->s.contains(second?"Sable Life 指令帮助":"Sable Life commands"))) {
            if(messages.stream().anyMatch(s->s.contains("command.sablelife.")))throw new AssertionError("Untranslated command help");
            if(!second&&!suggestions.isDone()){if(age<200)return;throw new AssertionError("Server blueprint completion timed out");}
            if(!second&&suggestions.get().getList().stream().noneMatch(s->s.getText().equalsIgnoreCase("\"Command Network\"")))throw new AssertionError("Server blueprint completion failed: "+suggestions.get().getList());
            mc.setScreen(new ChatScreen(""));phase=2;age=0;
        } else if(phase==2&&age>12) {
            shot("command-help");mc.setScreen(new ManualScreen(mc.screen,"server_limits"));phase=3;age=0;
        } else if(phase==3&&age>12) {
            for(var child:mc.screen.children())if(child instanceof Button button&&(button.getMessage().getString().toLowerCase(Locale.ROOT).contains("example")||button.getMessage().getString().contains("示例"))){button.onPress();break;}
            phase=4;age=0;
        } else if(phase==4&&age>12) {
            shot("command-manual");mc.screen.onClose();
            mc.player.connection.sendCommand("sablelife blueprint spawn \"Command Network\" "+(second?"false":"true"));phase=5;age=0;
        } else if(phase==5) {
            if(second&&age>35){mc.player.connection.sendCommand("sablelife_command_test_ack");phase=8;}
            if(!second&&messages.stream().filter(s->s.startsWith("Blueprint Command Network placed:")).count()==1){mc.player.connection.sendCommand("sablelife blueprint spawn \"Command Network\" false 24 105 8");phase=6;age=0;}
        } else if(phase==6&&messages.stream().filter(s->s.startsWith("Blueprint Command Network placed:")).count()==2) {
            mc.player.connection.sendCommand("sablelife blueprint spawn \"Command Network\" true");phase=7;age=0;
        } else if(phase==7&&messages.stream().anyMatch(s->s.contains("occupy this space"))) {
            mc.player.connection.sendCommand("sablelife_command_test_ack");phase=8;
        }
    }
}
