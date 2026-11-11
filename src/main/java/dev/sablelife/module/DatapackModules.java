package dev.sablelife.module;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mojang.logging.LogUtils;
import dev.sablelife.SableLife;
import dev.sablelife.api.module.ModuleBehavior;
import dev.sablelife.api.module.ModuleContext;
import dev.sablelife.api.module.ModuleDefinition;
import dev.sablelife.api.module.ModuleDefinition.Core;
import dev.sablelife.api.module.ModuleDefinition.MarkMode;
import dev.sablelife.api.module.ModuleDefinition.NumberOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** The current server resources own the data definitions. Clients receive their presentation only. */
@EventBusSubscriber(modid = SableLife.MOD_ID)
public final class DatapackModules {
    static final int MAX_MODULES = 64;
    static final int MAX_OPTIONS = 4;
    static final int LABEL_LIMIT = 64;
    static final int DESCRIPTION_LIMIT = 192;
    private static final int MAX_FILE_BYTES = 8192;
    private static final String TYPE_PATH = "datapack/";
    private static final FileToIdConverter FILES = FileToIdConverter.json("sablelife/modules");
    private static final com.google.gson.TypeAdapter<JsonElement> JSON = new Gson().getAdapter(JsonElement.class);
    private static final Map<ReloadableServerResources, Catalog> CATALOGS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Catalog EMPTY = new Catalog(Map.of());
    private static volatile Catalog client = EMPTY;
    private static int clientRevision;

    public record Text(String english, String chinese) {
        public String forLocale(String locale) {
            return "zh_cn".equals(locale) && !chinese.isEmpty() ? chinese : english;
        }
    }

    record Option(NumberOption number, Text label) {}

    public record Presentation(ResourceLocation id, Text name, Text description, Set<Core> cores,
                               MarkMode mark, List<Option> options) {
        ModuleDefinition bind(Supplier<? extends ModuleBehavior> factory) {
            return new ModuleDefinition(id, "datapack.module.name", "datapack.module.description",
                    ModuleDefinition.Category.FUNCTION, cores, mark, options.stream().map(Option::number).toList(), factory);
        }

        public Text optionLabel(String key) {
            for (Option option : options) if (option.number().key().equals(key)) return option.label();
            return null;
        }
    }

    record Entry(Presentation presentation, ModuleDefinition definition, List<ModuleFields.Field> fields) {
        Entry(Presentation presentation, ModuleDefinition definition) {
            this(presentation, definition, definition.options().stream().map(option -> new ModuleFields.Field(
                    "addon_" + option.key(), option.minimum(), option.maximum(), option.initial(), 1, List.of())).toList());
        }
    }
    record Catalog(Map<String, Entry> entries) {}

    public static boolean isDatapackType(String type) {
        int separator = type.indexOf(':');
        return separator >= 0 && type.startsWith(TYPE_PATH, separator + 1);
    }

    static Catalog current() {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER && EffectiveSide.get().isClient()) return client;
        return serverCatalog(ServerLifecycleHooks.getCurrentServer());
    }

    private static Catalog serverCatalog(MinecraftServer server) {
        return server == null ? EMPTY : CATALOGS.getOrDefault(server.getServerResources().managers(), EMPTY);
    }

    public static Presentation clientPresentation(String type) {
        Entry entry = client.entries().get(type);
        return entry == null ? null : entry.presentation();
    }

    static Entry entry(String type) {
        return current().entries().get(type);
    }

    public static int clientRevision() {
        return clientRevision;
    }

    public static void receive(DatapackModuleCatalog packet) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (var presentation : packet.modules()) {
            var definition = presentation.bind(() -> {
                throw new IllegalStateException("Datapack behavior runs on the server");
            });
            entries.put(presentation.id().toString(), new Entry(presentation, definition));
        }
        client = new Catalog(Collections.unmodifiableMap(entries));
        clientRevision++;
    }

    public static void disconnectClient() {
        client = EMPTY;
        clientRevision++;
    }

    @SubscribeEvent
    public static void addListener(AddReloadListenerEvent event) {
        var resources = event.getServerResources();
        event.addListener(new SimplePreparableReloadListener<Catalog>() {
            @Override
            protected Catalog prepare(ResourceManager manager, ProfilerFiller profiler) {
                return load(manager);
            }

            @Override
            protected void apply(Catalog catalog, ResourceManager manager, ProfilerFiller profiler) {
                CATALOGS.put(resources, catalog);
            }
        });
    }

    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        Catalog catalog = serverCatalog(event.getPlayerList().getServer());
        var packet = new DatapackModuleCatalog(catalog.entries().values().stream().map(Entry::presentation).toList());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, packet));
    }

    private static Catalog load(ResourceManager manager) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        var files = FILES.listMatchingResources(manager);
        for (var path : files.keySet().stream().sorted().toList()) {
            if (entries.size() >= MAX_MODULES) {
                LogUtils.getLogger().warn("Sable Life datapack module limit reached ({}). Ignoring remaining definitions.", MAX_MODULES);
                break;
            }
            Resource resource = files.get(path);
            try (var stream = resource.open()) {
                byte[] bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
                if (bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("file exceeds " + MAX_FILE_BYTES + " bytes");
                String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                var fileId = FILES.fileToId(path);
                var id = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), TYPE_PATH + fileId.getPath());
                Entry entry;
                try (var reader = new JsonReader(new StringReader(json))) {
                    var value = object(JSON.read(reader), "definition");
                    require(reader.peek() == JsonToken.END_DOCUMENT, "unexpected data after definition");
                    entry = parse(id, value);
                }
                entries.put(id.toString(), entry);
            } catch (IOException | RuntimeException failure) {
                LogUtils.getLogger().warn("Ignoring invalid Sable Life datapack module {} from {}: {}",
                        path, resource.sourcePackId(), failure.getMessage());
            }
        }
        return entries.isEmpty() ? EMPTY : new Catalog(Collections.unmodifiableMap(entries));
    }

    private static Entry parse(ResourceLocation id, JsonObject json) {
        fields(json, "format", "name", "description", "cores", "mark", "options", "on_start");
        require(json.has("format") && number(json.get("format"), "format") == 1, "format must be 1");
        Text name = text(json.get("name"), "name", LABEL_LIMIT);
        Text description = text(json.get("description"), "description", DESCRIPTION_LIMIT);
        var cores = EnumSet.noneOf(Core.class);
        require(json.has("cores") && json.get("cores").isJsonArray(), "cores must be an array");
        for (var core : json.getAsJsonArray("cores")) {
            cores.add(switch (string(core, "core")) {
                case "heart" -> Core.HEART;
                case "redstone" -> Core.REDSTONE;
                case "survival" -> Core.SURVIVAL;
                default -> throw new IllegalArgumentException("unknown core type " + core);
            });
        }
        MarkMode mark = switch (string(json.get("mark"), "mark")) {
            case "none" -> MarkMode.NONE;
            case "point" -> MarkMode.POINT;
            case "area" -> MarkMode.AREA;
            default -> throw new IllegalArgumentException("mark must be none, point or area");
        };
        var options = new ArrayList<Option>();
        if (json.has("options")) {
            require(json.get("options").isJsonArray(), "options must be an array");
            require(json.getAsJsonArray("options").size() <= MAX_OPTIONS, "too many options");
            for (var item : json.getAsJsonArray("options")) {
                JsonObject option = object(item, "option");
                fields(option, "id", "label", "min", "max", "default");
                var value = new NumberOption(string(option.get("id"), "option id"), "datapack.module.option",
                        number(option.get("min"), "min"), number(option.get("max"), "max"),
                        number(option.get("default"), "default"));
                options.add(new Option(value, text(option.get("label"), "option label", LABEL_LIMIT)));
            }
        }
        JsonObject action = object(json.get("on_start"), "on_start");
        fields(action, "type", "sound", "volume", "pitch");
        require("play_sound".equals(string(action.get("type"), "action type")), "unknown on_start action " + action.get("type"));
        ResourceLocation soundId = ResourceLocation.tryParse(string(action.get("sound"), "sound"));
        require(soundId != null && BuiltInRegistries.SOUND_EVENT.containsKey(soundId), "unknown sound " + action.get("sound"));
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundId);
        SoundValue volume = soundValue(action.get("volume"), "volume", 0, 2, options);
        SoundValue pitch = soundValue(action.get("pitch"), "pitch", .5, 2, options);
        var presentation = new Presentation(id, name, description, Set.copyOf(cores), mark, List.copyOf(options));
        return new Entry(presentation, presentation.bind(() -> new PlaySound(sound, volume, pitch)));
    }

    private record PlaySound(SoundEvent sound, SoundValue volume, SoundValue pitch) implements ModuleBehavior {
        @Override
        public void onStart(ModuleContext context) {
            var pos = context.marks().isEmpty() ? context.corePosition() : context.marks().getFirst().position();
            context.level().playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS,
                    volume.read(context), pitch.read(context));
        }
    }

    private record SoundValue(String parameter, float constant) {
        float read(ModuleContext context) {
            return parameter == null ? constant : (float) context.option(parameter);
        }
    }

    private static SoundValue soundValue(JsonElement json, String field, double min, double max, List<Option> options) {
        if (json == null) return new SoundValue(null, 1);
        if (json.isJsonObject()) {
            JsonObject reference = json.getAsJsonObject();
            fields(reference, "option");
            String id = string(reference.get("option"), field + " option");
            NumberOption option = options.stream().map(Option::number).filter(candidate -> candidate.key().equals(id))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("unknown " + field + " option " + id));
            require(option.minimum() >= min && option.maximum() <= max, field + " option must be within " + min + ".." + max);
            return new SoundValue(id, 0);
        }
        double number = number(json, field);
        require(number >= min && number <= max, field + " must be within " + min + ".." + max);
        return new SoundValue(null, (float) number);
    }

    private static Text text(JsonElement value, String field, int limit) {
        if (value != null && value.isJsonObject()) {
            var locales = value.getAsJsonObject();
            fields(locales, "en_us", "zh_cn");
            return new Text(label(locales.get("en_us"), field + ".en_us", limit),
                    locales.has("zh_cn") ? label(locales.get("zh_cn"), field + ".zh_cn", limit) : "");
        }
        return new Text(label(value, field, limit), "");
    }

    private static String label(JsonElement value, String field, int limit) {
        String result = string(value, field).strip();
        require(!result.isBlank() && result.length() <= limit && result.codePoints().noneMatch(Character::isISOControl),
                field + " must be 1.." + limit + " characters without control characters");
        return result;
    }

    private static String string(JsonElement value, String field) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), field + " must be a string");
        return value.getAsString();
    }

    private static double number(JsonElement value, String field) {
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(), field + " must be a number");
        double result = value.getAsDouble();
        require(Double.isFinite(result), field + " must be finite");
        return result;
    }

    private static JsonObject object(JsonElement value, String field) {
        require(value != null && value.isJsonObject(), field + " must be an object");
        return value.getAsJsonObject();
    }

    private static void fields(JsonObject object, String... allowed) {
        require(object != null, "definition must be an object");
        var keys = Set.of(allowed);
        for (String key : object.keySet()) require(keys.contains(key), "unknown field " + key);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private DatapackModules() {}
}
