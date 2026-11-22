package dev.sablelife.item;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.UUID;

/** Operator-controlled archive maintenance leaves item references intact. */
public final class DataCopierCommands {
    private DataCopierCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("clipboard")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource(), null))
                        .then(Commands.argument("owner", StringArgumentType.word())
                                .executes(context -> status(context.getSource(), StringArgumentType.getString(context, "owner")))))
                .then(mutation("archive", true))
                .then(mutation("restore", false));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mutation(String name, boolean archive) {
        return Commands.literal(name).then(Commands.argument("owner", StringArgumentType.word())
                .then(Commands.argument("digest", StringArgumentType.word()).executes(context -> mutate(context, archive))));
    }

    private static UUID parseOwner(String owner) {
        if (owner.equals("legacy")) return null;
        var uuid = UUID.fromString(owner);
        if (!uuid.toString().equalsIgnoreCase(owner)) throw new IllegalArgumentException("Invalid clipboard account");
        return uuid;
    }

    private static int status(CommandSourceStack source, String owner) {
        try {
            var scope = owner == null ? null : parseOwner(owner);
            var usage = DataCopierClipboards.store(source.getServer()).status(owner != null, scope);
            if (owner != null) {
                source.sendSuccess(() -> Component.translatable("message.sablelife.copier_admin_scope",
                        owner, usage.active(), usage.limit(), usage.archive()), false);
            }
            source.sendSuccess(() -> Component.translatable("message.sablelife.copier_admin_world",
                    usage.worldActive(), DataCopierClipboards.MAX_STORE_BYTES, usage.worldArchive()), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("message.sablelife.copier_admin_invalid"));
        } catch (IOException e) {
            source.sendFailure(Component.translatable("message.sablelife.copier_admin_failed", e.getMessage()));
        }
        return 0;
    }

    private static int mutate(CommandContext<CommandSourceStack> context, boolean archive) {
        var source = context.getSource();
        try {
            var key = new DataCopierClipboards.Reference(parseOwner(StringArgumentType.getString(context, "owner")),
                    StringArgumentType.getString(context, "digest"));
            var store = DataCopierClipboards.store(source.getServer());
            boolean moved = archive ? store.archive(key) : store.restore(key);
            var message = !moved ? "message.sablelife.copier_admin_already"
                    : archive ? "message.sablelife.copier_admin_archived" : "message.sablelife.copier_admin_restored";
            source.sendSuccess(() -> Component.translatable(message, key.id()), false);
            return 1;
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("message.sablelife.copier_admin_invalid"));
        } catch (DataCopierClipboards.QuotaExceeded e) {
            source.sendFailure(Component.translatable(e.account
                    ? "message.sablelife.copier_account_full" : "message.sablelife.copier_store_failed"));
        } catch (IOException e) {
            source.sendFailure(Component.translatable("message.sablelife.copier_admin_failed", e.getMessage()));
        }
        return 0;
    }
}
