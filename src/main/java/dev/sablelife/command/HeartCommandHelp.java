package dev.sablelife.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class HeartCommandHelp {
    public static int show(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.sablelife.help.title"), false);
        line(source, "/sablelife help", "help");
        line(source, "/sablelife inspect <x> <y> <z>", "inspect");
        line(source, "/sablelife damage <x> <y> <z> <amount>", "damage");
        line(source, "/sablelife blueprint spawn <name> <true|false> [x y z]", "blueprint");
        line(source, "/sablelife cleanup scan all", "scan");
        line(source, "/sablelife cleanup scan radius <blocks>", "scan_radius");
        line(source, "/sablelife cleanup apply all", "cleanup");
        line(source, "/sablelife cleanup apply radius <blocks>", "cleanup_radius");
        line(source, "/sablelife_respawn cancel", "respawn");
        if (source.hasPermission(2)) {
            line(source, "/sablelife clipboard status [owner UUID|legacy]", "clipboard_status");
            line(source, "/sablelife clipboard archive <owner UUID|legacy> <digest>", "clipboard_archive");
            line(source, "/sablelife clipboard restore <owner UUID|legacy> <digest>", "clipboard_restore");
        }
        return 1;
    }
    private static void line(CommandSourceStack source, String syntax, String key) {
        source.sendSuccess(() -> Component.literal(syntax + " — ")
                .append(Component.translatable("command.sablelife.help." + key)), false);
    }
    private HeartCommandHelp() {}
}
