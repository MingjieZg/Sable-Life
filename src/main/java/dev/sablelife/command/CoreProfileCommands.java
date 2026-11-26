package dev.sablelife.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.sablelife.diagnostics.CoreProfiler;
import dev.sablelife.diagnostics.CoreProfiler.CoreSample;
import dev.sablelife.diagnostics.CoreProfiler.Location;
import dev.sablelife.diagnostics.CoreProfiler.Report;
import dev.sablelife.diagnostics.CoreProfiler.Work;
import dev.sablelife.diagnostics.CoreProfiler.WorkSample;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Displays server-side measurements and provides an operator-controlled way to find sampled cores. */
final class CoreProfileCommands {
    private static final String COMMAND = "/sablelife profile ";
    private static final String KEY = "command.sablelife.profile.";
    private static final int DEFAULT_SECONDS = 15;
    private static final int TOP_CORES = 10, TOP_WORK = 3;

    private enum Ranking {
        COST, ACTIVITY;

        long coreMetric(CoreSample sample) {
            return this == COST ? sample.totalNanos() : operations(sample);
        }

        long workMetric(WorkSample sample) {
            return this == COST ? sample.nanos() : sample.count();
        }

        Component label() {
            return this == COST ? text("cost", "Core tick cost") : text("activity", "Monitored activity");
        }
    }

    static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("profile").requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(context -> start(context.getSource(), DEFAULT_SECONDS))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(CoreProfiler.MIN_SECONDS, CoreProfiler.MAX_SECONDS))
                                .executes(context -> start(context.getSource(), IntegerArgumentType.getInteger(context, "seconds")))))
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                .then(Commands.literal("report")
                        .executes(context -> report(context.getSource(), Ranking.COST))
                        .then(Commands.literal("cost").executes(context -> report(context.getSource(), Ranking.COST)))
                        .then(Commands.literal("activity").executes(context -> report(context.getSource(), Ranking.ACTIVITY))))
                .then(Commands.literal("locate").then(Commands.argument("core", UuidArgument.uuid())
                        .executes(context -> locate(context.getSource(), UuidArgument.getUuid(context, "core")))));
    }

    static void help(CommandSourceStack source) {
        helpLine(source, "start [seconds]", text("help_start", "Record core activity for 15 seconds by default (1–60 seconds)."));
        helpLine(source, "stop", text("help_stop", "Finish the current profile and keep the results."));
        helpLine(source, "report [cost|activity]", text("help_report", "Show the top 10 cores by tick cost or recorded activity."));
        helpLine(source, "locate <UUID>", text("help_locate", "Look up the current or last recorded position of a core."));
    }

    private static void helpLine(CommandSourceStack source, String usage, Component description) {
        send(source, Component.literal(COMMAND + usage + " — ").append(description));
    }

    private static int start(CommandSourceStack source, int seconds) {
        if (!CoreProfiler.start(source.getServer(), seconds)) {
            source.sendFailure(text("already_active", "A core profile is already running."));
            return 0;
        }
        send(source, text("started", "Core profile started for %s seconds.", seconds));
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        if (!CoreProfiler.stop(source.getServer())) {
            source.sendFailure(text("not_active", "There is no running core profile."));
            return 0;
        }
        send(source, text("stopped", "Core profile saved. Run %s to read it.", COMMAND + "report"));
        return 1;
    }

    private static int report(CommandSourceStack source, Ranking ranking) {
        var server = source.getServer();
        Report report = CoreProfiler.report(server);
        if (report == null) {
            source.sendFailure(text("no_report", "There is no core profile yet. Run %s to start one.", COMMAND + "start"));
            return 0;
        }
        boolean live = CoreProfiler.active(server);
        var cores = report.cores().stream()
                .sorted(Comparator.comparingLong(ranking::coreMetric).reversed().thenComparing(CoreSample::id))
                .limit(TOP_CORES)
                .toList();
        double elapsedSeconds = report.elapsedNanos() / 1_000_000_000.0;
        var phase = live ? text("live", "Live snapshot") : text("finished", "Finished profile");
        send(source, text("summary", "%s · %s · %s s wall time · %s server ticks · %s of %s cores",
                phase, ranking.label(), decimal(elapsedSeconds, 2), report.ticks(), cores.size(), report.cores().size()));
        send(source, text("scope", "Measurements cover server-side core logic. Mean times include every sampled server tick.")
                .withStyle(ChatFormatting.GRAY));
        if (report.truncated())
            send(source, text("truncated", "The sample limit was reached. The ranking covers the recorded cores.").withStyle(ChatFormatting.GOLD));
        if (cores.isEmpty()) {
            send(source, text("empty", "No core activity has been recorded in this profile."));
            return 1;
        }
        boolean interactive = source.source instanceof ServerPlayer;
        for (int i = 0; i < cores.size(); i++)
            send(source, coreLine(cores.get(i), i + 1, report.ticks(), elapsedSeconds, ranking, interactive));
        return cores.size();
    }

    private static Component coreLine(CoreSample core, int rank, int ticks, double seconds, Ranking ranking, boolean interactive) {
        Component name = core.name().isBlank() ? text("unnamed", "Unnamed core") : Component.literal(core.name());
        String uuid = core.id().toString();
        var identity = name.copy().append(" [").append(interactive ? uuid.substring(0, 8) : uuid).append("]")
                .withStyle(ChatFormatting.GOLD);
        if (interactive) identity.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(uuid))));
        var row = text("core", "%s. %s · %s ms/tick mean · %s ms peak · %s operations/s",
                rank, identity, meanMillis(core.totalNanos(), ticks), millis(core.peakNanos()), rate(operations(core), seconds));
        if (interactive) row.append(" ").append(button(text("locate_button", "[Locate]"), ClickEvent.Action.RUN_COMMAND,
                COMMAND + "locate " + uuid, text("locate_tip", "Show this core's current or last recorded position.")));

        if (core.peak() != null) {
            var peakPosition = position(core.peak());
            if (interactive)
                suggestTeleport(peakPosition, core.peak(), text("teleport_peak_tip", "Fill in a teleport command two blocks above the recorded peak."));
            row.append("\n").append(text("peak_at", "Peak at %s · game tick %s", peakPosition, core.peak().gameTime()));
        }
        if (core.last() != null) row.append("\n").append(text("last_at", "Last recorded at %s · game tick %s", position(core.last()), core.last().gameTime()));

        var work = core.work().entrySet().stream()
                .filter(entry -> entry.getValue().count() > 0 || entry.getValue().nanos() > 0)
                .sorted(Comparator.<Map.Entry<Work, WorkSample>>comparingLong(entry -> ranking.workMetric(entry.getValue()))
                        .reversed().thenComparing(Map.Entry::getKey))
                .limit(TOP_WORK).toList();
        if (!work.isEmpty()) {
            var metrics = Component.empty();
            for (var entry : work) {
                if (!metrics.getSiblings().isEmpty()) metrics.append(" · ");
                var sample = entry.getValue();
                metrics.append(text("metric", "%s %s/s (%s ms/tick)", workLabel(entry.getKey()),
                        rate(sample.count(), seconds), meanMillis(sample.nanos(), ticks)));
            }
            row.append("\n").append(text("main_work", "Main monitored operations · %s", metrics).withStyle(ChatFormatting.GRAY));
        }
        return row;
    }

    private static Component workLabel(Work work) {
        return switch (work) {
            case SENSOR_SCAN -> text("work_sensor_scan", "Sensor scans");
            case BLOCK_SCAN -> text("work_block_scan", "Block searches");
            case BLOCK_USE -> text("work_block_use", "Block interactions");
            case HOMING_PREFLIGHT -> text("work_homing_preflight", "Homing target checks");
            case HOMING_DEPLOY -> text("work_homing_deploy", "Homing deployments");
            case MOVE_SELECT -> text("work_move_select", "Move selections");
            case MOVE_START -> text("work_move_start", "Move starts");
        };
    }

    private static int locate(CommandSourceStack source, UUID id) {
        var found = CoreProfiler.locate(source.getServer(), id);
        if (found == null) {
            source.sendFailure(text("unknown_core", "No current or recorded location for core %s.", id.toString()));
            return 0;
        }
        var where = found.location();
        var line = found.current()
                ? text("current_location", "Core %s is currently at %s.", id.toString(), position(where))
                : text("recorded_location", "Core %s was last recorded at %s · game tick %s.", id.toString(), position(where), where.gameTime());
        if (source.source instanceof ServerPlayer) {
            var label = found.current() ? text("teleport_button", "[Teleport]") : text("teleport_recorded_button", "[Teleport to recorded point]");
            line.append(" ").append(suggestTeleport(label, where, text("teleport_tip", "Fill in a teleport command two blocks above this location.")));
        }
        send(source, line);
        return 1;
    }

    private static MutableComponent position(Location location) {
        return Component.literal(location.dimension() + " " + coordinates(location.position()));
    }

    private static String coordinates(Vec3 pos) {
        // Vanilla centers integer X/Z inputs. Keep decimal points in coordinates used by the teleport suggestion.
        return decimal(pos.x, 3) + " " + decimal(pos.y, 3) + " " + decimal(pos.z, 3);
    }

    private static MutableComponent suggestTeleport(MutableComponent label, Location where, Component hover) {
        var pos = where.position();
        if (!(Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z))) return label;
        return button(label, ClickEvent.Action.SUGGEST_COMMAND,
                "/execute in " + where.dimension() + " run tp @s " + coordinates(pos.add(0, 2, 0)), hover)
                .withStyle(ChatFormatting.UNDERLINE);
    }
    private static MutableComponent button(MutableComponent label, ClickEvent.Action action, String command, Component hover) {
        return label.withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(action, command)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }
    private static long operations(CoreSample core) { return core.work().values().stream().mapToLong(WorkSample::count).sum(); }
    private static String rate(long operations, double seconds) { return decimal(seconds > 0 ? operations / seconds : 0, 2); }
    private static String millis(long nanos) { return decimal(nanos / 1_000_000.0, 3); }
    private static String meanMillis(long nanos, int ticks) { return decimal(ticks > 0 ? nanos / (ticks * 1_000_000.0) : 0, 3); }
    private static String decimal(double value, int places) { return String.format(Locale.ROOT, "%." + places + "f", value); }
    private static MutableComponent text(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(KEY + key, fallback, args);
    }
    private static void send(CommandSourceStack source, Component message) { source.sendSuccess(() -> message, false); }
    private CoreProfileCommands() {}
}
