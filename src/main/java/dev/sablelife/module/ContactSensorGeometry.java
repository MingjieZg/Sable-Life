package dev.sablelife.module;

import dev.sablelife.attack.AttackVolume;
import dev.sablelife.attack.ContactBox;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;

/** One marked point; offset and full dimensions are local to the moving part. */
public final class ContactSensorGeometry {
    public record Frame(Vec3 center, Vec3 half, Quaterniond rotation) {
        public AttackVolume volume() { return AttackVolume.box(center, half, rotation); }
    }

    public static Frame frame(HeartBlockEntity heart, VirtualModule module) {
        var mark = module.anchors().getFirst();
        var forward = Vec3.atLowerCornerOf(mark.face().getNormal());
        var angles = MeleeGeometry.angles(forward, module.value("contactHorizontal"), module.value("contactVertical"));
        var body = new Quaterniond(mark.body(heart).logicalPose().orientation());
        var center = mark.world(heart, false).add(ContactBox.rotate(body, forward).scale(.5 + module.value("startDistance")));
        return new Frame(center, new Vec3(module.value("contactSizeX"), module.value("contactSizeY"),
                module.value("contactSizeZ")).scale(.5), body.mul(angles));
    }

    public static void preview(ServerPlayer player, HeartBlockEntity heart, VirtualModule module) {
        if (!java.util.List.of("valid", "disabled").contains(HeartModules.status(module, heart))) return;
        var f = frame(heart, module);
        MarkOutline.send(player, MarkOutline.Kind.DETAIL, 0xAD75DF, MarkOutline.box(f.half.scale(-1), f.half,
                point -> f.center.add(ContactBox.rotate(f.rotation, point))));
    }

    private ContactSensorGeometry() {}
}
