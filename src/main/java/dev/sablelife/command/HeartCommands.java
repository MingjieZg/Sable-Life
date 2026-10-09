package dev.sablelife.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.heart.HeartHealth;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Development controls. All mutations require operator permission and execute on the server. */
public final class HeartCommands {
    private HeartCommands() {}
    private static final SimpleCommandExceptionType NOT_HEART = new SimpleCommandExceptionType(Component.translatable("message.sablelife.not_heart"));

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sablelife").requires(source -> source.hasPermission(2))
                .then(Commands.literal("options").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("allowBreaking", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .then(Commands.argument("loot", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .then(Commands.argument("experience", IntegerArgumentType.integer(0, 10000)).executes(context -> {
                                            var heart = heart(context);
                                            var loot = net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "loot");
                                            boolean changed = heart.configureOptions(com.mojang.brigadier.arguments.BoolArgumentType.getBool(context, "allowBreaking"), loot,
                                                    IntegerArgumentType.getInteger(context, "experience"));
                                            if (!changed) context.getSource().sendFailure(Component.literal("Configure options before activation."));
                                            else context.getSource().sendSuccess(heart::status, false);
                                            return changed ? 1 : 0;
                                        }))))))
                .then(Commands.literal("inspect").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(context -> {
                    var heart = heart(context);
                    context.getSource().sendSuccess(heart::status, false);
                    return 1;
                })))
                .then(Commands.literal("damage").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.001F, HeartHealth.MAX_CONFIGURED_HEALTH)).executes(context -> {
                            var heart = heart(context);
                            boolean changed = heart.damage(FloatArgumentType.getFloat(context, "amount"));
                            context.getSource().sendSuccess(heart::status, false);
                            return changed ? 1 : 0;
                        }))))
                .then(Commands.literal("configure").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("maxHealth", FloatArgumentType.floatArg(1, HeartHealth.MAX_CONFIGURED_HEALTH))
                                .then(Commands.argument("deathTicks", IntegerArgumentType.integer(0, HeartHealth.MAX_DEATH_DELAY)).executes(context -> {
                                    var heart = heart(context);
                                    boolean changed = heart.configure(FloatArgumentType.getFloat(context, "maxHealth"), IntegerArgumentType.getInteger(context, "deathTicks"));
                                    if (!changed) context.getSource().sendFailure(Component.literal("Configure the Heart before activation."));
                                    else context.getSource().sendSuccess(heart::status, false);
                                    return changed ? 1 : 0;
                                }))))));
    }

    private static HeartBlockEntity heart(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (context.getSource().getLevel().getBlockEntity(pos) instanceof HeartBlockEntity heart) return heart;
        throw NOT_HEART.create();
    }
}

