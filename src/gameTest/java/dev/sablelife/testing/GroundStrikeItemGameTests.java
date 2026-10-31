package dev.sablelife.testing;
import dev.sablelife.*;
import dev.sablelife.attack.ModuleShot;
import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder("sablelife_blast_tests") @PrefixGameTestTemplate(false)
public final class GroundStrikeItemGameTests {
 @GameTest(template="combat") public static void FallingItemRotationAndGiantSizeDoNotChangeTrajectoryOrHitbox(GameTestHelper h){
  var m=new VirtualModule("ground_strike");m.projectileItem(new ItemStack(Items.DIAMOND_SWORD),h.getLevel().registryAccess());m.edit("strikeItemMode",1,"");m.edit("strikeItemZ",-135,"");m.edit("strikeItemSize",12,"");var point=h.absoluteVec(new Vec3(3,40,3));var shot=ModuleShot.fallingVisual(h.getLevel(),point,.25,100,m);var box=shot.getBoundingBox();
  h.assertTrue(shot.fixedFallingItem()&&shot.fallingItemSize()==12&&shot.projectileDiameter()==.5F,"Visual settings changed collision diameter");var tip=new org.joml.Vector3f(1,1,0).rotate(shot.fallingItemRotation()).normalize();h.assertTrue(tip.y<-.999&&Math.abs(tip.x)<.001,"Sword reference diagonal does not rotate downward");shot.tick();h.assertTrue(shot.position().distanceToSqr(point.add(0,-.25,0))<1e-9&&Math.abs(shot.getBoundingBox().getXsize()-box.getXsize())<1e-9,"Rotation/size changed vertical path or hitbox");h.assertTrue(shot.getBoundingBoxForCulling().getXsize()>=24,"Giant visual can be culled by tiny collision box");
  m.edit("strikeItemZ",45,"");m.edit("strikeItemSize",2,"");h.assertTrue(shot.fallingItemAngles().z==-135&&shot.fallingItemSize()==12,"Already spawned visual followed later edits");shot.discard();h.succeed();
 }
 @GameTest(template="combat") public static void RotationSizeSavePresetAndLegacyDefaultsRemainValid(GameTestHelper h){
  var m=new VirtualModule("ground_strike");h.assertTrue(m.value("strikeItemMode")==0,"Default no longer faces camera");m.edit("strikeItemMode",1,"");m.edit("strikeItemX",30,"");m.edit("strikeItemY",60,"");m.edit("strikeItemZ",-135,"");m.edit("strikeItemSize",16,"");var p=ModulePreset.create(m.save());h.assertTrue(p.value("strikeItemSize")==16&&p.value("strikeItemY")==60,"Preset lost visual settings");
  var shot=ModuleShot.fallingVisual(h.getLevel(),Vec3.ZERO,.2,80,m);var saved=new net.minecraft.nbt.CompoundTag();shot.saveWithoutId(saved);var loaded=new ModuleShot(SableLife.MODULE_SHOT.get(),h.getLevel());loaded.load(saved);h.assertTrue(loaded.fixedFallingItem()&&loaded.fallingItemAngles().equals(30,60,-135)&&loaded.fallingItemSize()==16,"Shot save lost synced appearance");
  var legacy=m.save();legacy.getCompound("Settings").remove("strikeItemSize");legacy.getCompound("Settings").remove("strikeItemMode");legacy.getCompound("Settings").putDouble("projectileHitbox",1.25);var old=VirtualModule.load(legacy);h.assertTrue(old.value("strikeItemMode")==0&&old.value("strikeItemSize")==1.25,"Old appearance changed");
  h.assertTrue(!m.edit("strikeItemX",Double.NaN,""),"Non-finite rotation accepted");m.edit("strikeItemY",181,"");m.edit("strikeItemSize",33,"");h.assertTrue(m.value("strikeItemY")==180&&m.value("strikeItemSize")==32,"Visual input was not clamped to safe bounds");shot.discard();loaded.discard();h.succeed();
 }
}
