package dev.sablelife.module;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import java.util.*;

/** Deterministic local strokes; no particles, entities, terrain visits or per-frame network traffic. */
public final class PatternGeometry {
    public record Stroke(Vec3 a,Vec3 b,float width,float alpha){}
    // Unit coordinates in the marked face's right/up/forward frame.
    private static final Vec3 SHIELD_IMPACT = shieldPoint(-.19, .13);
    private static final List<Vec3> SHIELD_RIM = List.of(
            shieldPoint(0, .97), shieldPoint(.64, .93), shieldPoint(.98, .69),
            shieldPoint(.95, .30), shieldPoint(.72, -.37), shieldPoint(0, -1),
            shieldPoint(-.71, -.39), shieldPoint(-.95, .28), shieldPoint(-.98, .70),
            shieldPoint(-.62, .93));
    private static final List<ShieldSection> SHIELD_SECTIONS = List.of(
            new ShieldSection(0, 3, shieldPoint(.35, .55)),
            new ShieldSection(3, 6, shieldPoint(.30, -.25)),
            new ShieldSection(6, 10, shieldPoint(-.59, .20)));
    private static final List<ShieldShard> SHIELD_SHARDS = shieldShards();

    private record ShieldSection(int start, int end, Vec3 joint) {}

    private record ShieldShard(Vec3 center, Vec3 a, Vec3 b, Vec3 c, Vec3 drift,
                               double pitch, double yaw) {}

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
            case 2 -> shieldFracture(out,t,speed,size,width,fade);
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

    private static Vec3 shieldPoint(double x, double y) {
        return new Vec3(x, y, .065 + .24 * Math.max(0, 1 - .56 * x * x - .65 * y * y));
    }

    private static Vec3 shieldRim(int index) {
        return SHIELD_RIM.get(index % SHIELD_RIM.size());
    }

    private static List<ShieldShard> shieldShards() {
        var shards = new ArrayList<ShieldShard>();
        // Each section fans around a crack junction inside its part of the rim.
        for (var section : SHIELD_SECTIONS) {
            addShieldShard(shards, SHIELD_IMPACT, shieldRim(section.start()), section.joint());
            for (int i = section.start(); i < section.end(); i++) {
                addShieldShard(shards, shieldRim(i), shieldRim(i + 1), section.joint());
            }
            addShieldShard(shards, shieldRim(section.end()), SHIELD_IMPACT, section.joint());
        }
        return List.copyOf(shards);
    }

    private static void addShieldShard(List<ShieldShard> shards, Vec3 a, Vec3 b, Vec3 c) {
        Vec3 center = a.add(b).add(c).scale(1.0 / 3);
        Vec3 outward = new Vec3(center.x - SHIELD_IMPACT.x, center.y - SHIELD_IMPACT.y, 0).normalize();
        int index = shards.size();
        double push = .42 + .08 * (index % 4);
        Vec3 drift = outward.scale(push).add(0, 0, .26 + .09 * (index % 5));
        int tilt = index % 2 == 0 ? 1 : -1;
        shards.add(new ShieldShard(center, a.subtract(center), b.subtract(center), c.subtract(center),
                drift, -tilt * (.7 + .18 * (index % 4)), outward.x * 1.3 + tilt * .22));
    }

    private static void shieldFracture(List<Stroke> out, double progress, double speed,
                                       double size, double width, double fade) {
        double breakAt = Math.clamp(.21 / Math.sqrt(speed), .11, .34);
        double shardsVisible = ease((progress - breakAt) / .09);
        if (shardsVisible < 1) {
            double alpha = fade * (1 - shardsVisible);
            for (int i = 0; i < SHIELD_RIM.size(); i++) {
                line(out, shieldRim(i).scale(size), shieldRim(i + 1).scale(size), width * 1.45, alpha);
            }
            double crack = Math.clamp((progress / breakAt - .10) / .90, 0, 1);
            for (int i = 0; i < SHIELD_SECTIONS.size(); i++) {
                var section = SHIELD_SECTIONS.get(i);
                double delay = i * .075;
                double reach = (crack - delay) / (1 - delay);
                shieldCrack(out, SHIELD_IMPACT, shieldRim(section.start()), reach / .85, size, width, alpha);
                shieldCrack(out, SHIELD_IMPACT, section.joint(), reach / .55, size, width, alpha);
                for (int edge = section.start(); edge <= section.end(); edge++) {
                    shieldCrack(out, section.joint(), shieldRim(edge), (reach - .55) / .45,
                            size, width * .67, alpha * .72);
                }
            }
        }
        if (shardsVisible > 0) {
            double flight = (progress - breakAt) / (1 - breakAt);
            double travel = flight * (.5 + 1.05 * speed / (1 + speed));
            double alpha = fade * shardsVisible * (1 - .38 * flight);
            double shardWidth = width * (1.10 - .30 * flight);
            var rotation = new Quaterniond();
            var rotated = new Vector3d();
            for (var shard : SHIELD_SHARDS) {
                Vec3 center = shard.center().add(shard.drift().scale(travel)).add(0, -.72 * travel * flight, 0);
                rotation.rotationYXZ(shard.yaw() * travel, shard.pitch() * travel, 0);
                Vec3 a = shieldVertex(shard.a(), center, size, rotation, rotated);
                Vec3 b = shieldVertex(shard.b(), center, size, rotation, rotated);
                Vec3 c = shieldVertex(shard.c(), center, size, rotation, rotated);
                line(out, a, b, shardWidth, alpha);
                line(out, b, c, shardWidth, alpha);
                line(out, c, a, shardWidth, alpha);
            }
        }
    }

    private static Vec3 shieldVertex(Vec3 offset, Vec3 center, double size, Quaterniond rotation, Vector3d rotated) {
        rotation.transformUnit(offset.x, offset.y, offset.z, rotated);
        return new Vec3((center.x + rotated.x) * size, (center.y + rotated.y) * size, (center.z + rotated.z) * size);
    }

    private static void shieldCrack(List<Stroke> out, Vec3 from, Vec3 to, double progress,
                                    double size, double width, double alpha) {
        if (progress > 0) {
            line(out, from.scale(size), from.lerp(to, ease(progress)).scale(size), width, alpha);
        }
    }

    private static double ease(double progress) {
        double t = Math.clamp(progress, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private PatternGeometry(){}
}
