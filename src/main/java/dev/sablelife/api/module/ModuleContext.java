package dev.sablelife.api.module;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Valid during a module callback. Coordinates are in the world, including Sable transforms. */
public interface ModuleContext {
    ServerLevel level();
    UUID moduleId();
    UUID creatureId();
    Vec3 corePosition();
    List<Mark> marks();

    /** Returns the current value of a declared option, using its unprefixed key. */
    double option(String key);

    record Mark(Vec3 position, Vec3 normal) {}
}
