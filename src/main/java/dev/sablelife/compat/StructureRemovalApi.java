package dev.sablelife.compat;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import java.lang.reflect.*;
import java.util.*;

/** Same native path as Photomancy; verified Toolgun coordinator also cleans its own rope/control state. */
public final class StructureRemovalApi {
    private static final class Toolgun {
        static final Method coordinator=lookup();
        private static Method lookup() {
            if(!ModList.get().getModContainerById("create_aeronautics_toolgun").map(c->c.getModInfo().getVersion().toString().equals("0.3.6")).orElse(false))return null;
            try {return Class.forName("com.enxv.aeronauticsstructuretool.blueprint.lifecycle.SubLevelRemovalCoordinator").getMethod("remove",ServerLevel.class,ServerSubLevelContainer.class,Collection.class);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Toolgun 0.3.6 structure removal API is unavailable",failure);}
        }
    }
    public static void remove(ServerLevel level,ServerSubLevel body) {
        var container=SubLevelContainer.getContainer(level);
        if(container==null||container.getSubLevel(body.getUniqueId())!=body)throw new IllegalStateException("Structure is no longer registered");
        if(Toolgun.coordinator==null){container.removeSubLevel(body,SubLevelRemovalReason.REMOVED);return;}
        try {Toolgun.coordinator.invoke(null,level,container,List.of(body));}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException cause)throw cause;throw new IllegalStateException("Toolgun structure removal failed",failure.getCause());}
        catch(IllegalAccessException failure){throw new IllegalStateException("Toolgun structure removal is inaccessible",failure);}
    }
    private StructureRemovalApi() {}
}
