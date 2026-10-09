package dev.sablelife.sound;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.network.chat.Component;

@EventBusSubscriber(modid = "sablelife", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CustomSoundPack {
    @SubscribeEvent public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(consumer -> {
            var pack = Pack.readMetaAndCreate(new PackLocationInfo("sablelife_custom_sounds", Component.literal("Sable Life custom sounds"), PackSource.BUILT_IN, java.util.Optional.empty()),
                    new PathPackResources.PathResourcesSupplier(SoundLibrary.packFolder()), PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, true));
            if (pack != null) consumer.accept(pack);
        });
    }
}
