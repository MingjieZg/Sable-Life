package dev.sablelife.exampleaddon;

import dev.sablelife.api.module.ModuleBehavior;
import dev.sablelife.api.module.ModuleContext;
import dev.sablelife.api.module.ModuleDefinition;
import dev.sablelife.api.module.ModuleDefinition.Category;
import dev.sablelife.api.module.ModuleDefinition.Core;
import dev.sablelife.api.module.ModuleDefinition.MarkMode;
import dev.sablelife.api.module.ModuleDefinition.NumberOption;
import dev.sablelife.api.module.ModuleRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.fml.common.Mod;

import java.util.List;
import java.util.Set;

@Mod(ExampleModuleAddon.ID)
public final class ExampleModuleAddon {
    public static final String ID = "sablelife_example_addon";

    public ExampleModuleAddon() {
        ModuleRegistry.register(new ModuleDefinition(ResourceLocation.fromNamespaceAndPath(ID, "marker_chime"),
                "module.sablelife_example_addon.marker_chime", "module.sablelife_example_addon.marker_chime.help",
                Category.FUNCTION, Set.of(Core.HEART, Core.REDSTONE, Core.SURVIVAL), MarkMode.POINT,
                List.of(new NumberOption("volume", "module.sablelife_example_addon.marker_chime.volume", 0, 2, 1)),
                MarkerChime::new));
    }

    private static final class MarkerChime implements ModuleBehavior {
        @Override public void onStart(ModuleContext context) {
            var point = context.marks().getFirst().position();
            context.level().playSound(null, point.x, point.y, point.z, SoundEvents.NOTE_BLOCK_CHIME.value(),
                    SoundSource.BLOCKS, (float) context.option("volume"), 1);
        }
    }
}
