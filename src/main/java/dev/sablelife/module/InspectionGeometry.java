package dev.sablelife.module;

import dev.ryanhcode.sable.Sable;
import dev.sablelife.attack.ContactBox;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.ownership.BlueprintSpawnPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static dev.sablelife.module.InspectionSnapshot.Shape.*;

/** Read-only outlines for configured geometry. Never executes a module or searches for a live target. */
final class InspectionGeometry {
    private static final Vec3 FORWARD = new Vec3(0, 0, 1), UP = new Vec3(0, 1, 0);
    private static final Quaterniond IDENTITY = new Quaterniond();
    private static final Set<String> NO_GEOMETRY = Set.of("move_set", "signal_logic", "combat_event",
            "health_phase", "hit_counter", "health_adjustment", "sequence", "command", "surface_field", "message_display");

    static List<InspectionSnapshot.Piece> pieces(HeartBlockEntity heart, VirtualModule module) {
        var type = module.type();
        if (NO_GEOMETRY.contains(type)) return List.of();
        var out = new ArrayList<InspectionSnapshot.Piece>();
        var marks = module.anchors();
        var first = marks.isEmpty() ? null : valid(heart, marks.getFirst());

        if (type.equals("wireless_redstone")) {
            for (int i = 0; i < Math.min(marks.size(), ModuleRedstone.MAX_OUTPUT_FACES); i++) {
                if (module.redstoneFaceStatus(i, heart).equals("valid")) {
                    var mark = marks.get(i);
                    var facing = normal(mark);
                    out.add(make(FACE, mark, null, false, facing.scale(.503), uniformly(.5), aim(facing)));
                }
            }
        } else if (type.equals("arena")) {
            if (here(heart, module))
                out.add(make(RING, null, null, true, module.arenaCenter().add(0, .2, 0),
                        uniformly(module.value("arenaRadius")), aim(UP)));
            coreShape(out, heart, RING, new Vec3(0, .2, 0), uniformly(module.value("warningRange")), aim(UP));
        } else if (type.equals("bgm")) {
            coreShape(out, heart, SPHERE, Vec3.ZERO, uniformly(module.value("bgmRadius")), IDENTITY);
        } else if (type.equals("damage_accumulation")) {
            watchedPart(out, heart, module);
        } else if (type.equals("part_break")) {
            if (!module.invalidated() && first != null && ModulePartBreak.validSelection(module, marks)
                    && (!module.area() || second(heart, first, marks) != null)) markBox(out, heart, first, marks, false);
            watchedPart(out, heart, module);
        } else if (ModuleLinearMovement.worldDestination(module)) {
            if (ModuleLinearMovement.hasWorldDestination(heart, module))
                out.add(make(CROSS, null, null, true, module.arenaCenter(), uniformly(.55), IDENTITY));
        } else if (type.equals("blueprint_spawn")) {
            var destination = BlueprintSpawnPlacement.resolve(heart, module);
            if (destination != null) {
                var rotation = destination.rotation() == null ? IDENTITY : destination.rotation();
                var frame = BlueprintSpawnPlacement.follows(module) ? first : null;
                out.add(atWorld(heart, frame, CROSS, destination.position(), uniformly(.6), rotation, false));
                out.add(atWorld(heart, frame, ARROW, destination.position(), uniformly(2), rotation, false));
            }
        } else if (TargetCastOrigin.targeted(module) || type.equals("linear_movement") && module.value("moveAim") == 1) {
            String field = type.equals("linear_movement") ? "moveTargetRange" : "castTargetRange";
            coreShape(out, heart, SPHERE, Vec3.ZERO, uniformly(module.value(field)), IDENTITY);
        } else if (first != null) {
            marked(out, heart, module, first, marks);
        }
        return out;
    }

    private static void marked(List<InspectionSnapshot.Piece> out, HeartBlockEntity heart, VirtualModule module,
                               ModuleAnchor mark, List<ModuleAnchor> marks) {
        var face = normal(mark);
        var origin = mark.world(heart, false);
        switch (module.type()) {
            case "melee" -> {
                var second = second(heart, mark, marks);
                if (second == null) return;
                var frame = MeleeGeometry.frame(heart, module);
                out.add(make(BOX, mark, second.offset(), false, localOffset(heart, mark, frame.center()),
                        frame.half(), frame.angleRotation()));
            }
            case "contact_sensor" -> {
                var frame = ContactSensorGeometry.frame(heart,module);
                out.add(atWorld(heart,mark,BOX,frame.center(),frame.half(),frame.rotation(),false));
            }
            case "sensor" -> {
                double radius = module.value("radius");
                if (module.value("shape") == 0) {
                    sphere(out, mark, Vec3.ZERO, radius);
                    sphere(out, mark, Vec3.ZERO, module.value("innerRadius"));
                } else {
                    var forward = toLocalDirection(heart, mark, ModuleCombat.direction(heart, module));
                    out.add(make(FRUSTUM, mark, null, false, Vec3.ZERO, new Vec3(.5, module.value("frontWidth"), radius), aim(forward)));
                }
            }
            case "armor", "loot" -> part(out, heart, mark);
            case "entity_hitbox" -> {
                var center = dev.sablelife.combat.EntityHitboxes.center(heart, module);
                if (center != null) out.add(atWorld(heart, mark, BOX, center,
                        dimensions(module, "hitboxWidth", "hitboxHeight", "hitboxDepth").scale(.5), IDENTITY, true));
            }
            case "block_interaction" -> interaction(out, heart, module, mark);
            case "distortion_trail" -> {
                var pose = ModuleDistortionTrail.pose(heart, module);
                out.add(atWorld(heart, mark, BOX, pose.center(), ModuleDistortionTrail.half(module), pose.rotation(), false));
            }
            case "particles" -> particles(out, heart, module, mark, marks);
            case "trigger_sound", "visual_shockwave" -> {
                markBox(out, heart, mark, marks, false);
                String key = module.type().equals("trigger_sound") ? "contactMode" : "shockContact";
                if (module.value(key) == 1 && SurfaceFieldSettings.visible(module, key)) {
                    out.add(atWorld(heart, mark, ARROW, ModuleEvents.contactStart(heart, module),
                            uniformly(module.value("contactDistance")), aim(ModuleEvents.contactDirection(heart, module)), false));
                }
            }
            case "item_display" -> box(out, mark, face.scale(.55).add(dimensions(module, "displayX", "displayY", "displayZ")),
                    uniformly(module.value("displaySize") * .5), IDENTITY);
            case "health_bar" -> {
                var local = ModuleHealthBar.localPoint(mark, mark.body(heart), module.value("startDistance"))
                        .subtract(mark.position(mark.body(heart)).getCenter());
                box(out, mark, local, new Vec3(module.value("healthBarWidth") / 2, module.value("healthBarHeight") / 2, .04), IDENTITY);
            }
            case "auto_facing", "signal_turn" -> {
                arrow(out, mark, face.scale(.55), face, 3);
                if (module.type().equals("auto_facing") && module.value("facingSource") == 0)
                    coreShape(out, heart, RING, Vec3.ZERO, uniformly(module.value("facingRange")), aim(UP));
            }
            case "navigation" -> {
                var front = TargetNavigation.front(module);
                for (var direction : List.of(front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite(), Direction.UP, Direction.DOWN))
                    arrow(out, mark, Vec3.ZERO, Vec3.atLowerCornerOf(direction.getNormal()), direction == front ? 3 : 2.4);
                sphere(out, mark, Vec3.ZERO, module.value("radius"));
                sphere(out, mark, Vec3.ZERO, module.value("innerRadius"));
            }
            case "gyroscope" -> {
                var down = UP.scale(-1);
                out.add(make(ARROW, mark, null, true, Vec3.ZERO, uniformly(2.6), aim(down)));
                if (module.gyroDown() != null) arrow(out, mark, Vec3.ZERO, module.gyroDown(), 2.2);
                double angle = Math.toRadians(module.value("gyroTilt"));
                out.add(atWorld(heart, mark, RING, origin.add(0, -2 * Math.cos(angle), 0),
                        uniformly(2 * Math.sin(angle)), aim(UP), true));
            }
            case "propulsion" -> {
                var frame=PhysicsModuleSupport.frame(heart,module);
                if(frame!=null)out.add(atWorld(heart,mark,ARROW,PhysicsModuleSupport.origin(frame),uniformly(2),
                        aim(ModulePropulsion.direction(frame,module)),module.value("propFrame")==1));
            }
            case "motion_damping" -> {
                var frame=PhysicsModuleSupport.frame(heart,module);
                if(frame!=null){var axes=ModuleMotionDamping.axes(frame,module.value("dampFrame")==1);
                    for(int i=0;i<6;i++)if(module.value("damp"+ModuleMotionDamping.DIRECTIONS.get(i))>0)
                        out.add(atWorld(heart,mark,ARROW,PhysicsModuleSupport.origin(frame),uniformly(1.8),aim(axes[i]),module.value("dampFrame")==0));
                }
            }
            case "linear_movement" -> {
                var direction = LinearMovementGeometry.direction(heart, module);
                if (GravityTravel.enabled(module)) direction = GravityTravel.horizontal(direction).normalize();
                out.add(atWorld(heart, mark, ARROW, origin, uniformly(module.value("moveDistance")), aim(direction), GravityTravel.enabled(module)));
            }
            case "sound", "pulse" -> sphere(out, mark, Vec3.ZERO, module.value("radius"));
            case "grab" -> out.add(atWorld(heart, mark, SPHERE, ModuleGrab.holdPoint(heart, module),
                    uniformly(module.value("grabRadius")), IDENTITY, false));
            case "freeze" -> freeze(out, heart, module, mark);
            case "pull" -> {
                var start = ModuleCombat.origin(heart, module, true);
                if (module.value("pullShape") == 0)
                    out.add(atWorld(heart, mark, SPHERE, start, uniformly(module.value("pullRadius")), IDENTITY, false));
                else out.add(atWorld(heart, mark, FRUSTUM, start,
                        dimensions(module, "pullStartRadius", "pullEndRadius", "pullLength"), aim(ModuleCombat.direction(heart, module)), false));
            }
            case "sphere_blast" -> {
                var center = ModuleSphereBlast.center(heart, module);
                out.add(atWorld(heart, mark, SPHERE, center, uniformly(module.value("blastRadius")), IDENTITY, false));
                arrow(out, mark, Vec3.ZERO, toLocalDirection(heart, mark, center.subtract(origin)), center.distanceTo(origin));
            }
            case "flame", "gun", "arrow", "laser", "mortar", "slash" -> weapon(out, heart, module, mark);
            case "homing" -> formation(out, heart, mark, ModuleHoming.formation(heart, module));
            case "summoned_weapon" -> formation(out, heart, mark, ModuleSummonedWeapon.formation(heart, module));
            case "ground_strike", "quake" -> ground(out, heart, module, mark);
            default -> markBox(out, heart, mark, marks, true);
        }
    }

    private static void weapon(List<InspectionSnapshot.Piece> out, HeartBlockEntity heart, VirtualModule module, ModuleAnchor mark) {
        var type = module.type();
        var start = ModuleCombat.nozzle(heart, module);
        var direction = ModuleCombat.direction(heart, module);
        out.add(atWorld(heart, mark, CROSS, start, uniformly(.28), IDENTITY, false));
        String targetRange = switch (type) {
            case "mortar" -> module.value("aimMode") != 0 ? "targetRange" : null;
            case "arrow", "gun", "slash" -> module.value("shotAim") != 0 ? "targetRange" : null;
            case "laser" -> module.value("laserAim") != 0 ? "beamLength" : null;
            default -> null;
        };
        if (targetRange != null) {
            out.add(atWorld(heart, mark, SPHERE, start, uniformly(module.value(targetRange)), IDENTITY, false));
            return;
        }
        switch (type) {
            case "arrow", "gun" -> {
                double range = type.equals("arrow") ? 8 : module.value("range");
                double radius = module.value("endRadius") * range / 8;
                var shape = radius < .001 ? ARROW : FRUSTUM;
                var size = shape == ARROW ? uniformly(range) : new Vec3(0, radius, range);
                out.add(atWorld(heart, mark, shape, start, size, aim(direction), false));
            }
            case "flame" -> {
                double length = module.value("range"), width = module.value("width");
                if (module.value("flameShape") == 1)
                    out.add(atWorld(heart, mark, FAN, start, new Vec3(length, width, Math.toRadians(module.value("flameFanAngle")) / 2),
                            aim(direction), false));
                else out.add(atWorld(heart, mark, FRUSTUM, start, new Vec3(0, width, length), aim(direction), false));
            }
            case "laser" -> out.add(atWorld(heart, mark, ARROW, start, uniformly(module.value("beamLength")), aim(direction), false));
            case "slash" -> {
                var angle = MeleeGeometry.angles(direction, module.value("slashHorizontal"), module.value("slashVertical"));
                out.add(atWorld(heart, mark, ARROW, start, uniformly(module.value("range")),
                        aim(ContactBox.rotate(angle, direction)), false));
            }
            default -> {
                var horizontal = new Vec3(direction.x, 0, direction.z).normalize();
                double angle = Math.toRadians(module.value("launchAngle"));
                var lift = horizontal.scale(Math.cos(angle)).add(0, Math.sin(angle), 0);
                out.add(atWorld(heart, mark, ARROW, start, uniformly(4), aim(lift), true));
            }
        }
    }

    private static void ground(List<InspectionSnapshot.Piece> out, HeartBlockEntity heart, VirtualModule module, ModuleAnchor mark) {
        var world = mark.world(heart, false);
        var direction = ModuleCombat.direction(heart, module);
        var forward = new Vec3(direction.x, 0, direction.z).normalize();
        if (forward.lengthSqr() < .001) forward = FORWARD;
        boolean quake = module.type().equals("quake");
        var mode = module.value(quake ? "quakeMode" : "strikeMode");
        double range = module.value(quake ? "quakeRange" : "strikeRange");
        if (quake && module.value("quakeOrigin") == 1) range = module.value("quakeDetectRange");
        if (mode == 0 && (!quake || module.value("quakeOrigin") == 0)) {
            if (quake) {
                var center = world.add(forward.scale(range / 2));
                out.add(atWorld(heart, mark, BOX, center, new Vec3(module.value("quakeWidth") / 2, .05, range / 2), aim(forward), true));
            } else {
                var start = world.add(forward.scale(module.value("strikeStart")));
                out.add(atWorld(heart, mark, ARROW, start, uniformly(range), aim(forward), true));
            }
        } else out.add(atWorld(heart, mark, RING, world, uniformly(range), aim(UP), true));
    }

    private static void freeze(List<InspectionSnapshot.Piece> out, HeartBlockEntity heart, VirtualModule module, ModuleAnchor mark) {
        var volume = ControlVolume.frame(module, ModuleCombat.origin(heart, module, true), ModuleCombat.direction(heart, module));
        if (volume.circle()) {
            var start = volume.center().subtract(0, volume.height() / 2, 0);
            out.add(atWorld(heart, mark, FRUSTUM, start, new Vec3(volume.radius(), volume.radius(), volume.height()), aim(UP), true));
        } else {
            var size = new Vec3(volume.width(), volume.height(), volume.length()).scale(.5);
            out.add(atWorld(heart, mark, BOX, volume.center(), size, aim(volume.forward()), true));
        }
    }

    private static void particles(List<InspectionSnapshot.Piece> out, HeartBlockEntity heart, VirtualModule module,
                                  ModuleAnchor mark, List<ModuleAnchor> marks) {
        var second = second(heart, mark, marks);
        if (module.area() && second == null) return;
        var frame = ParticlePattern.frame(heart, module);
        var worldDir = ContactBox.rotate(frame.rotation(), frame.forward());
        double radius = module.value("particleRadius"), height = module.value("particleRingHeight");
        switch ((int) module.value("particlePattern")) {
            case 0, 1 -> {
                var point = frame.half().lengthSqr() < 1e-8;
                out.add(make(point ? CROSS : BOX, mark, second == null ? null : second.offset(),
                        false, localOffset(heart, mark, frame.center()), point ? uniformly(.18) : frame.half(), IDENTITY));
            }
            case 2 -> out.add(atWorld(heart, mark, ARROW, frame.center(), uniformly(module.value("particleLength")), aim(worldDir), false));
            case 3 -> out.add(atWorld(heart, mark, height > 0 ? FRUSTUM : RING, frame.center(),
                    new Vec3(radius, radius, height), aim(worldDir), false));
            case 4 -> out.add(atWorld(heart, mark, SPHERE, frame.center(), uniformly(radius), IDENTITY, false));
            default -> {}
        }
    }

    private static void interaction(List<InspectionSnapshot.Piece> out, HeartBlockEntity heart, VirtualModule module, ModuleAnchor mark) {
        var area = ModuleBlockInteraction.area(heart, module);
        var half = uniformly(module.value("interactionSize") / 2 + .002);
        var center = area.min().getCenter().add(half).subtract(.502, .502, .502);
        if (area.body() == null) out.add(atWorld(heart, mark, BOX, center, half, IDENTITY, true));
        else box(out, mark, center.subtract(mark.position(area.body()).getCenter()), half, IDENTITY);
    }

    private static void formation(List<InspectionSnapshot.Piece> out, HeartBlockEntity heart, ModuleAnchor mark, List<Vec3> points) {
        if (points.isEmpty()) return;
        var body = mark.body(heart);
        var localPoints = points.stream().map(body.logicalPose()::transformPositionInverse).toList();
        var min = localPoints.getFirst();
        var max = min;
        for (var p : localPoints) {
            min = new Vec3(Math.min(min.x, p.x), Math.min(min.y, p.y), Math.min(min.z, p.z));
            max = new Vec3(Math.max(max.x, p.x), Math.max(max.y, p.y), Math.max(max.z, p.z));
        }
        box(out, mark, min.lerp(max, .5).subtract(mark.position(body).getCenter()), max.subtract(min).scale(.5).add(.2, .2, .2), IDENTITY);
    }

    private static void coreShape(List<InspectionSnapshot.Piece> out, HeartBlockEntity heart, InspectionSnapshot.Shape shape,
                                  Vec3 offset, Vec3 size, Quaterniond rotation) {
        var mark = coreAnchor(heart);
        var world = ModuleArena.corePosition(heart).add(offset);
        out.add(atWorld(heart, mark, shape, world, size, rotation, true));
    }

    private static ModuleAnchor coreAnchor(HeartBlockEntity heart) {
        var body = Sable.HELPER.getContaining(heart.getLevel(), heart.getBlockPos());
        return body == null ? null : new ModuleAnchor(body.getUniqueId(), heart.getBlockPos().subtract(body.getPlot().getCenterBlock()), Direction.UP);
    }

    private static void watchedPart(List<InspectionSnapshot.Piece> out, HeartBlockEntity heart, VirtualModule module) {
        var watched = module.listensToPart() ? valid(heart, module.watchedPart()) : null;
        if (watched != null) part(out, heart, watched);
    }

    private static void part(List<InspectionSnapshot.Piece> out, HeartBlockEntity heart, ModuleAnchor mark) {
        var body = mark.body(heart);
        var bounds = body.getPlot().getBoundingBox();
        if (bounds == null) return;
        var min = new Vec3(bounds.minX(), bounds.minY(), bounds.minZ()).subtract(.05, .05, .05);
        var max = new Vec3(bounds.maxX(), bounds.maxY(), bounds.maxZ()).add(1.05, 1.05, 1.05);
        box(out, mark, min.lerp(max, .5).subtract(mark.position(body).getCenter()), max.subtract(min).scale(.5), IDENTITY);
    }

    private static void markBox(List<InspectionSnapshot.Piece> out, HeartBlockEntity heart, ModuleAnchor mark,
                                List<ModuleAnchor> marks, boolean direction) {
        if (mark == null) return;
        var second = second(heart, mark, marks);
        var delta = second == null ? Vec3.ZERO : Vec3.atLowerCornerOf(second.offset().subtract(mark.offset()));
        var half = new Vec3(Math.abs(delta.x) / 2 + .502, Math.abs(delta.y) / 2 + .502, Math.abs(delta.z) / 2 + .502);
        out.add(make(BOX, mark, second == null ? null : second.offset(), false, delta.scale(.5), half, IDENTITY));
        if (direction && second == null) arrow(out, mark, Vec3.ZERO, normal(mark), 2);
    }

    private static ModuleAnchor second(HeartBlockEntity heart, ModuleAnchor first, List<ModuleAnchor> marks) {
        return marks.size() == 2 && first.bodyId().equals(marks.getLast().bodyId()) ? valid(heart, marks.getLast()) : null;
    }

    private static ModuleAnchor valid(HeartBlockEntity heart, ModuleAnchor mark) {
        return mark != null && mark.status(heart).equals("valid") ? mark : null;
    }

    private static boolean here(HeartBlockEntity heart, VirtualModule module) {
        return module.arenaCenter() != null && heart.getLevel().dimension().location().toString().equals(module.arenaDimension());
    }

    private static Vec3 normal(ModuleAnchor mark) { return Vec3.atLowerCornerOf(mark.face().getNormal()); }
    private static Vec3 uniformly(double n) { return new Vec3(n, n, n); }

    private static Vec3 dimensions(VirtualModule module, String x, String y, String z) {
        return new Vec3(module.value(x), module.value(y), module.value(z));
    }

    private static Quaterniond aim(Vec3 direction) {
        if (direction.lengthSqr() < 1e-8) return new Quaterniond();
        return new Quaterniond().rotationTo(0, 0, 1, direction.x, direction.y, direction.z);
    }

    private static Vec3 toLocalDirection(HeartBlockEntity heart, ModuleAnchor mark, Vec3 direction) {
        return ContactBox.rotate(new Quaterniond(mark.body(heart).logicalPose().orientation()).conjugate(), direction);
    }

    private static void sphere(List<InspectionSnapshot.Piece> out, ModuleAnchor mark, Vec3 center, double radius) {
        if (radius > 0) out.add(make(SPHERE, mark, null, false, center, uniformly(radius), IDENTITY));
    }

    private static void arrow(List<InspectionSnapshot.Piece> out, ModuleAnchor mark, Vec3 center, Vec3 direction, double length) {
        if (length > .001) out.add(make(ARROW, mark, null, false, center, uniformly(length), aim(direction)));
    }

    private static void box(List<InspectionSnapshot.Piece> out, ModuleAnchor mark, Vec3 center, Vec3 half, Quaterniond rotation) {
        out.add(make(BOX, mark, null, false, center, half, rotation));
    }

    private static InspectionSnapshot.Piece atWorld(HeartBlockEntity heart, ModuleAnchor mark, InspectionSnapshot.Shape shape,
                                                     Vec3 center, Vec3 size, Quaterniond rotation, boolean worldAxes) {
        if (mark == null) return make(shape, null, null, true, center, size, rotation);
        var local = worldAxes ? rotation : new Quaterniond(mark.body(heart).logicalPose().orientation()).conjugate().mul(rotation);
        return make(shape, mark, null, worldAxes, localOffset(heart, mark, center), size, local);
    }

    private static Vec3 localOffset(HeartBlockEntity heart, ModuleAnchor mark, Vec3 center) {
        var body = mark.body(heart);
        return body.logicalPose().transformPositionInverse(center).subtract(mark.position(body).getCenter());
    }

    private static InspectionSnapshot.Piece make(InspectionSnapshot.Shape shape, ModuleAnchor mark, BlockPos second,
                                                  boolean worldAxes, Vec3 center, Vec3 size, Quaterniond rotation) {
        return new InspectionSnapshot.Piece(shape, mark, second, worldAxes, mark == null ? center : compact(center),
                compact(size), new Quaternionf(rotation));
    }

    private static Vec3 compact(Vec3 point) { return new Vec3((float) point.x, (float) point.y, (float) point.z); }

    private InspectionGeometry() {}
}
