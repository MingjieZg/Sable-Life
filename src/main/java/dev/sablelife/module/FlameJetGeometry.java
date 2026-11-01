package dev.sablelife.module;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Births occupy the nozzle plane; velocity, rather than spawned positions, forms the cone. */
public final class FlameJetGeometry {
    public record Birth(Vec3 origin, Vec3 velocity, double phase) {}
    public static List<Birth> births(ModuleNetworking.FlameJet jet) {
        var forward=jet.direction().normalize();
        var side=forward.cross(Math.abs(forward.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        var up=side.cross(forward).normalize();
        int slices=Math.clamp((int)Math.ceil(jet.speed()/.2),1,20);
        var births=new ArrayList<Birth>();
        var random=new java.util.Random(jet.seed());
        for(int i=0;i<slices;i++)for(int ray=0;ray<4;ray++) {
            double angle=random.nextDouble()*Math.PI*2;
            double radius=Math.pow(random.nextDouble(),.8);
            var radial=side.scale(Math.cos(angle)*radius).add(up.scale(Math.sin(angle)*radius));
            // Fill the cone's interior; new seeds on each emission prevent fixed spokes and rows.
            births.add(new Birth(jet.origin().add(radial.scale(.12)),forward.scale(jet.speed())
                    .add(radial.scale((jet.width()-.12)*jet.speed()/jet.range())),(i+.15*random.nextDouble())/slices));
        }
        return List.copyOf(births);
    }
}
