package dev.sablelife.ownership;

/** Limits apply to trusted administrator templates, never client packet data. */
public final class ArenaRespawnLimits {
 public static final int PARTS=32, SCAN_VOXELS=4194304, BLOCKS=131072, BLOCK_ENTITIES=16384, ENTITIES=256;
 public static final int TEMPLATE_BYTES=32*1024*1024, WORLD_TEMPLATE_BYTES=128*1024*1024;
 // Persist anything previously admitted by a supported configuration.
 public static final int MAX_TEMPLATE_BYTES=128*1024*1024, MAX_WORLD_TEMPLATE_BYTES=512*1024*1024;

 static boolean fitsSavedArenaTemplate(long loadedBytes, int templateBytes) {
  return templateBytes <= MAX_TEMPLATE_BYTES && fitsSavedTemplates(loadedBytes, templateBytes);
 }

 static boolean fitsSavedTemplates(long loadedBytes, int templateBytes) {
  return templateBytes >= 0 && templateBytes <= MAX_WORLD_TEMPLATE_BYTES - loadedBytes;
 }

 static void warnSkippedTemplates(String storage, int rejected) {
  if (rejected > 0) com.mojang.logging.LogUtils.getLogger().warn(
      "Skipped {} saved templates in {} that exceeded the fixed storage limits", rejected, storage);
 }
 public static final class Exceeded extends IllegalArgumentException {
  public final String kind; public final long actual,maximum;
  private Exceeded(String kind,long actual,long maximum){super("limit_"+kind);this.kind=kind;this.actual=actual;this.maximum=maximum;}
 }
 public static void check(String kind,long actual,long maximum){if(actual>maximum)throw new Exceeded(kind,actual,maximum);}
 private ArenaRespawnLimits(){}
}
