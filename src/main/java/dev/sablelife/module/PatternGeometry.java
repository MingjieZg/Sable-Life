package dev.sablelife.module;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Deterministic local strokes; no particles, entities, terrain visits or per-frame network traffic. */
public final class PatternGeometry {
    public record Stroke(Vec3 a,Vec3 b,float width,float alpha){}
    private static void line(List<Stroke> out,Vec3 a,Vec3 b,double width,double alpha){
        if(out.size()<512&&alpha>.005&&a.distanceToSqr(b)>1E-10)
            out.add(new Stroke(a,b,(float)width,(float)Math.clamp(alpha,0,1)));
    }
    private static Vec3 circle(double angle,double radius,double z){return new Vec3(Math.cos(angle)*radius,Math.sin(angle)*radius,z);}
    private static void arc(List<Stroke> out,double radius,double z,double offset,double span,int steps,double width,double alpha){
        for(int i=0;i<steps;i++)line(out,circle(offset+span*i/steps,radius,z),circle(offset+span*(i+1)/steps,radius,z),width,alpha);
    }
    public static List<Stroke> strokes(int pattern,double age,int duration,double speed,double size){
        if(pattern<0||pattern>=6||!Double.isFinite(age)||!Double.isFinite(size)||!Double.isFinite(speed)
                ||duration<1||age<0||age>=duration||size<.1||size>32||speed<.1||speed>8)return List.of();
        double t=age/duration,spin=age*.055*speed,fade=Math.min(1,age/Math.min(4,duration*.15)+.08)*Math.min(1,(1-t)*7);
        double width=Math.max(.012,size*.014);var out=new ArrayList<Stroke>();
        switch(pattern){
            case 0 -> { // Spirals contract into the muzzle, with short luminous tails.
                for(int i=0;i<36;i++){
                    double phase=(t+i/36.)%1,angle=i*2.399963+spin*2,radius=size*(1-phase);
                    var a=circle(angle,radius,(1-phase)*size*.2);
                    var b=circle(angle+.08,radius*.94,(1-phase)*size*.18);
                    line(out,a,b,width*1.8,fade*(.35+.65*phase));
                }
                arc(out,size*(1-t)*.4,0,-spin,Math.PI*2,32,width,fade*.65);
            }
            case 1 -> { // Counter-rotating broken rings and clockwork graduation marks.
                for(int layer=0;layer<2;layer++){
                    double r=size*(layer==0?1:.68),rotation=spin*(layer==0?1:-1);
                    for(int section=0;section<4;section++)arc(out,r,0,rotation+section*Math.PI/2,Math.PI*.36,12,width,fade);
                    for(int i=0;i<24;i++){
                        double angle=rotation+i*Math.PI/12;
                        line(out,circle(angle,r,0),circle(angle,r*(i%3==0?.87:.94),0),width,fade*.85);
                    }
                }
            }
            case 2 -> { // A faceted dome opens into separating fragments.
                for(int ring=0;ring<4;ring++)for(int i=0;i<12;i++){
                    double a=i*Math.PI/6,b=(i+1)*Math.PI/6,phi=.18+ring*.32,spread=1+Math.max(0,t-.4)*1.4;
                    double r=Math.cos(phi)*size*spread,z=Math.sin(phi)*size*spread;
                    double gap=t>.4?.04+Math.max(0,t-.4)*.25:0;
                    line(out,circle(a+gap,r,z),circle(b-gap,r,z),width,fade*(1-t*.6));
                    if(ring<3)line(out,circle(a+gap,r,z),circle(a+.13,Math.cos(phi+.32)*size*spread,Math.sin(phi+.32)*size*spread),width,fade*.8);
                }
            }
            case 3 -> { // Jets stretch out along the marked face and fade into a plume.
                for(int i=0;i<48;i++){
                    double p=(age*.075*speed+i/48.)%1,angle=i*2.399963+spin*.2;
                    double radius=size*(.08+.2*p),z=p*size*2.7;
                    var a=circle(angle,radius,z);var b=circle(angle+.04,radius*.95,z+size*.13);
                    line(out,a,b,width*(1.4-p*.6),fade*(1-p));
                }
                arc(out,size*.18,0,spin,Math.PI*2,24,width,fade);
            }
            case 4 -> { // Branching fissures spread on the marked plane; no terrain changes.
                double reach=Math.min(1,t*3*speed);
                for(int branch=0;branch<9;branch++){
                    Vec3 last=Vec3.ZERO;double angle=branch*Math.PI*2/9;
                    for(int i=1;i<=7;i++){
                        double distance=size*i/7.;
                        var next=circle(angle+Math.sin(branch*7+i*4)*.18,distance,.015);
                        double fraction=Math.clamp(reach*7-(i-1),0,1);
                        if(fraction==0)break;
                        if(fraction<1){line(out,last,last.lerp(next,fraction),width,fade);break;}
                        line(out,last,next,width,fade);if(i%2==0){var twig=circle(angle+.3,distance+size*.15,.015);line(out,next,twig,width*.7,fade*.65);}
                        last=next;
                    }
                }
            }
            case 5 -> { // Several thin echoes expand from the emitter plane.
                for(int i=0;i<4;i++){
                    double p=(t*speed+i*.18)%1;
                    arc(out,size*(.15+p),p*size*.2,spin*.1,Math.PI*2,64,width*(1-p*.6),fade*(1-p));
                }
            }
            default -> {}
        }
        return List.copyOf(out);
    }
    private PatternGeometry(){}
}
