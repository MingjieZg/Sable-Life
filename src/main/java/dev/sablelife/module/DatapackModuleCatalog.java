package dev.sablelife.module;

import dev.sablelife.SableLife;
import dev.sablelife.api.module.ModuleDefinition.Core;
import dev.sablelife.api.module.ModuleDefinition.MarkMode;
import dev.sablelife.api.module.ModuleDefinition.NumberOption;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record DatapackModuleCatalog(List<DatapackModules.Presentation> modules) implements CustomPacketPayload {
    public static final Type<DatapackModuleCatalog> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "datapack_module_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DatapackModuleCatalog> CODEC =
            StreamCodec.of(DatapackModuleCatalog::write, DatapackModuleCatalog::read);

    private static void write(RegistryFriendlyByteBuf buf, DatapackModuleCatalog packet) {
        buf.writeVarInt(packet.modules.size());
        for (var module : packet.modules) {
            buf.writeUtf(module.id().toString(), 64);
            writeText(buf, module.name(), DatapackModules.LABEL_LIMIT);
            writeText(buf, module.description(), DatapackModules.DESCRIPTION_LIMIT);
            buf.writeEnum(module.mark());
            for (Core core : Core.values()) buf.writeBoolean(module.cores().contains(core));
            buf.writeVarInt(module.options().size());
            for (var option : module.options()) {
                var number = option.number();
                buf.writeUtf(number.key(), 58);
                writeText(buf, option.label(), DatapackModules.LABEL_LIMIT);
                buf.writeDouble(number.minimum());
                buf.writeDouble(number.maximum());
                buf.writeDouble(number.initial());
            }
        }
    }

    private static DatapackModuleCatalog read(RegistryFriendlyByteBuf buf) {
        int count = readCount(buf, DatapackModules.MAX_MODULES);
        var modules = new ArrayList<DatapackModules.Presentation>(count);
        for (int i = 0; i < count; i++) {
            var id = ResourceLocation.parse(buf.readUtf(64));
            var name = readText(buf, DatapackModules.LABEL_LIMIT);
            var description = readText(buf, DatapackModules.DESCRIPTION_LIMIT);
            var mark = buf.readEnum(MarkMode.class);
            var cores = EnumSet.noneOf(Core.class);
            for (Core core : Core.values()) if (buf.readBoolean()) cores.add(core);
            int size = readCount(buf, DatapackModules.MAX_OPTIONS);
            var options = new ArrayList<DatapackModules.Option>(size);
            for (int j = 0; j < size; j++) {
                String key = buf.readUtf(58);
                var label = readText(buf, DatapackModules.LABEL_LIMIT);
                var number = new NumberOption(key, "datapack.module.option",
                        buf.readDouble(), buf.readDouble(), buf.readDouble());
                options.add(new DatapackModules.Option(number, label));
            }
            modules.add(new DatapackModules.Presentation(id, name, description, Set.copyOf(cores), mark, List.copyOf(options)));
        }
        return new DatapackModuleCatalog(List.copyOf(modules));
    }

    private static int readCount(RegistryFriendlyByteBuf buf, int limit) {
        int value = buf.readVarInt();
        if (value < 0 || value > limit) throw new IllegalArgumentException("Invalid datapack module catalog size");
        return value;
    }

    private static void writeText(RegistryFriendlyByteBuf buf, DatapackModules.Text text, int limit) {
        buf.writeUtf(text.english(), limit);
        buf.writeUtf(text.chinese(), limit);
    }

    private static DatapackModules.Text readText(RegistryFriendlyByteBuf buf, int limit) {
        return new DatapackModules.Text(buf.readUtf(limit), buf.readUtf(limit));
    }

    @Override
    public Type<DatapackModuleCatalog> type() {
        return TYPE;
    }
}
