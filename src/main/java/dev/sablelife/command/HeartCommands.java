package dev.sablelife.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
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

/** Administrator inspection and controlled damage for diagnostics; authoring belongs in the core editor. */
public final class HeartCommands {
    private HeartCommands() {}
    private static final SimpleCommandExceptionType NOT_HEART = new SimpleCommandExceptionType(Component.translatable("message.sablelife.not_heart"));

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sablelife").requires(source -> source.hasPermission(2))
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
                        })))));
    }

    private static HeartBlockEntity heart(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (context.getSource().getLevel().getBlockEntity(pos) instanceof HeartBlockEntity heart) return heart;
        throw NOT_HEART.create();
    }
}

