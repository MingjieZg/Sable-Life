package dev.sablelife.ownership;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ArenaRespawnLimitsTest {
 @Test void ReportsExactReasonAndCountsWhileAcceptingBoundary(){
  assertDoesNotThrow(()->ArenaRespawnLimits.check("scan",ArenaRespawnLimits.SCAN_VOXELS,ArenaRespawnLimits.SCAN_VOXELS));
  var failure=assertThrows(ArenaRespawnLimits.Exceeded.class,()->ArenaRespawnLimits.check("scan",ArenaRespawnLimits.SCAN_VOXELS+1L,ArenaRespawnLimits.SCAN_VOXELS));
  assertEquals("scan",failure.kind);assertEquals(ArenaRespawnLimits.SCAN_VOXELS+1L,failure.actual);assertEquals(ArenaRespawnLimits.SCAN_VOXELS,failure.maximum);
  assertDoesNotThrow(()->ArenaRespawnLimits.check("scan",533288,ArenaRespawnLimits.SCAN_VOXELS));
  assertDoesNotThrow(()->ArenaRespawnLimits.check("blocks",63994,ArenaRespawnLimits.BLOCKS));
  assertDoesNotThrow(()->ArenaRespawnLimits.check("parts",20,ArenaRespawnLimits.PARTS));assertThrows(ArenaRespawnLimits.Exceeded.class,()->ArenaRespawnLimits.check("parts",33,ArenaRespawnLimits.PARTS));
 }

 @Test void SavedTemplateCeilingsRetainTheLargestPreviouslyAdmittedData() {
  int recordLimit = ArenaRespawnLimits.MAX_TEMPLATE_BYTES;
  int worldLimit = ArenaRespawnLimits.MAX_WORLD_TEMPLATE_BYTES;
  assertTrue(ArenaRespawnLimits.fitsSavedArenaTemplate(worldLimit - recordLimit, recordLimit));
  assertFalse(ArenaRespawnLimits.fitsSavedArenaTemplate(0, recordLimit + 1));
  assertFalse(ArenaRespawnLimits.fitsSavedArenaTemplate(worldLimit - recordLimit + 1, recordLimit));
  assertTrue(ArenaRespawnLimits.fitsSavedTemplates(0, worldLimit));
  assertFalse(ArenaRespawnLimits.fitsSavedTemplates(1, worldLimit));
  assertFalse(ArenaRespawnLimits.fitsSavedTemplates(0, -1));
 }
}
