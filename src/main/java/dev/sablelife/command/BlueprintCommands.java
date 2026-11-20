package dev.sablelife.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.sablelife.compat.photomancy.PhotomancyCompatibility;
import dev.sablelife.compat.photomancy.SpawnTemplateBridge;
import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.ownership.CommandBlueprintSpawns;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import java.util.Locale;

/** Only the administrator command branch can enter the optional blueprint service. */
public final class BlueprintCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("blueprint").requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    if (PhotomancyCompatibility.available()) {
                                        String prefix = builder.getRemainingLowerCase();
                                        if (prefix.startsWith("\"")) prefix = prefix.substring(1);
                                        for (String name : SpawnTemplateBridge.files(context.getSource().getServer()))
                                            if (name.toLowerCase(Locale.ROOT).startsWith(prefix))
                                                builder.suggest(StringArgumentType.escapeIfRequired(name));
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("activate", BoolArgumentType.bool())
                                        .executes(context -> spawn(context.getSource(), StringArgumentType.getString(context, "name"),
                                                BoolArgumentType.getBool(context, "activate"), context.getSource().getPosition(), true))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(context -> spawn(context.getSource(), StringArgumentType.getString(context, "name"),
                                                        BoolArgumentType.getBool(context, "activate"), Vec3Argument.getVec3(context, "pos"), false))))));
    }

    private static int spawn(CommandSourceStack source, String name, boolean activate, net.minecraft.world.phys.Vec3 position, boolean surface) {
        return CommandBlueprintSpawns.submit(source, name, activate, SableBodyProbe.toWorld(source.getLevel(), position), surface);
    }
    private BlueprintCommands() {}
}
