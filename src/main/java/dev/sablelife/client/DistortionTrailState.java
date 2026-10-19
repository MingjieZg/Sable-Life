package dev.sablelife.client;

import dev.sablelife.module.DistortionTrailPacket;
import java.util.*;

/** World-frozen poses expire independently of current signal/body motion. */
public final class DistortionTrailState {
 public static final int LIMIT=256,PER_MODULE=128;
 public record Birth(DistortionTrailPacket packet,DistortionTrailPacket.Sample sample,long born){}
 private final List<Birth> births=new ArrayList<>();
 public void accept(DistortionTrailPacket p,long now){if(!p.valid())return;tick(now);for(var sample:p.samples()){
  while(births.stream().filter(b->b.packet.module().equals(p.module())).count()>=PER_MODULE){for(int i=0;i<births.size();i++)if(births.get(i).packet.module().equals(p.module())){births.remove(i);break;}}
  if(births.size()>=LIMIT)births.removeFirst();births.add(new Birth(p,sample,now));
 }}
 public void tick(long now){births.removeIf(b->now-b.born>=b.packet.life()||now<b.born);}
 public List<Birth> births(){return List.copyOf(births);}
 public void clear(){births.clear();}
 public static double fade(double age,int life){return age<0||age>=life?0:Math.pow(1-age/life,2);}
}
