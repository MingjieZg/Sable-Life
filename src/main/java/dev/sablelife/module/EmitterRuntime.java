package dev.sablelife.module;
import dev.sablelife.heart.HeartBlockEntity;
/** Summons bypass only authoring triggers/Marks; attack settings and hit credit remain original. */
public final class EmitterRuntime {
 public static boolean active(VirtualModule m,HeartBlockEntity h){return m.emitter()!=null?h.operational():HeartModules.triggered(m,h);}
 public static boolean start(VirtualModule m,HeartBlockEntity h){return m.emitter()!=null||h.modules().hurtTriggers.start(m);}
 public static void completed(VirtualModule m,HeartBlockEntity h){if(m.emitter()==null)h.modules().hurtTriggers.completed(m);}
 public static VirtualModule copy(VirtualModule m){var copy=VirtualModule.load(m.save());copy.emitter(m.emitter());return copy;}
 private EmitterRuntime(){}
}
