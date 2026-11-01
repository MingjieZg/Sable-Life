package dev.sablelife.client;
import dev.sablelife.module.TerrainDebrisPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.*;
import net.minecraft.world.level.block.Block;
/** Native terrain particles carry material/color and gravity; no block entity or gameplay entity is created. */
public final class TerrainDebrisClient {
 public static void receive(TerrainDebrisPacket packet){var mc=Minecraft.getInstance();if(mc.level==null||!Double.isFinite(packet.position().lengthSqr()+packet.velocity().lengthSqr()))return;var state=Block.stateById(packet.state());if(state.isAir())return;var random=mc.level.random;var direction=packet.velocity();if(direction.length()>1)direction=direction.normalize();
  for(int i=0;i<8;i++){var p=packet.position();var particle=mc.particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK,state),p.x+(random.nextDouble()-.5)*.8,p.y+(random.nextDouble()-.5)*.8,p.z+(random.nextDouble()-.5)*.8,0,0,0);if(particle==null)continue;double speed=.45+random.nextDouble()*.4;particle.setParticleSpeed(direction.x*speed+(random.nextDouble()-.5)*.12,direction.y*speed+.12+random.nextDouble()*.16,direction.z*speed+(random.nextDouble()-.5)*.12);particle.setLifetime(18+random.nextInt(15));}
 }
 private TerrainDebrisClient(){}
}
