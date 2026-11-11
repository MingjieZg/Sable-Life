package dev.sablelife.api.module;

/** Server-side behavior. A fresh instance runs for each uninterrupted activation. */
public interface ModuleBehavior {
    default void onStart(ModuleContext context) {}
    default void onTick(ModuleContext context) {}
    default void onStop(ModuleContext context) {}
}
