package dev.sablelife.compat.photomancy;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Keep optional API classes outside the normal mod-loading path. */
public final class PhotomancyCompat {
    public static void setup(FMLCommonSetupEvent event) {
        if (PhotomancyCompatibility.available()) event.enqueueWork(HeartBlueprintMapper::register);
    }
    private PhotomancyCompat() {}
}
