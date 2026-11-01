package dev.sablelife.testing;
import dev.sablelife.client.ModuleSoundClient;
import dev.sablelife.module.ModuleNetworking;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;
/** Exercises the actual sound-instance loop flag and position updates, without audible playback. */
public final class MovingSlashAudioCheck {
 public static void run()throws Exception{
  var field=ModuleSoundClient.class.getDeclaredField("moving");field.setAccessible(true);var points=(Map<UUID,Object>)field.get(null);
  var moving=Class.forName("dev.sablelife.client.ModuleSoundClient$Moving").getDeclaredConstructor(Vec3.class,long.class);moving.setAccessible(true);
  var constructor=Class.forName("dev.sablelife.client.ModuleSoundClient$RadiusSound").getDeclaredConstructor(ResourceLocation.class,ModuleNetworking.ModuleSound.class);constructor.setAccessible(true);
  var source=UUID.randomUUID();var id=ResourceLocation.withDefaultNamespace("entity.blaze.ambient");
  try{points.put(source,moving.newInstance(new Vec3(1,2,3),0L));var packet=new ModuleNetworking.ModuleSound(source,id.toString(),false,Vec3.ZERO,1,1,32);
   var sound=(TickableSoundInstance)constructor.newInstance(id,packet);if(!sound.isLooping()||sound.getX()!=1||sound.getY()!=2)throw new AssertionError("Flight voice did not loop/follow initial position");
   points.put(source,moving.newInstance(new Vec3(9,8,7),1L));sound.tick();if(sound.getX()!=9||sound.getY()!=8||sound.getZ()!=7)throw new AssertionError("Flight voice stayed at launch point");
   var release=(SoundInstance)constructor.newInstance(id,new ModuleNetworking.ModuleSound(UUID.randomUUID(),id.toString(),false,Vec3.ZERO,1,1,32));if(release.isLooping())throw new AssertionError("Release voice unexpectedly loops");
   System.out.println("Moving flight audio instance passed: looping, updated position, one-shot release");
  }finally{points.remove(source);}
 }
}
