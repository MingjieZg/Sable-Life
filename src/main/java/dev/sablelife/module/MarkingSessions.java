package dev.sablelife.module;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class MarkingSessions {
    private record Session(HeartBlockEntity heart, UUID module, int revision, long expires, List<ModuleAnchor> points) {}
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private record Preview(HeartBlockEntity heart, UUID module, long expires) {}
    private static final Map<UUID, Preview> previews = new HashMap<>();
    public static void showPreview(ServerPlayer player, HeartBlockEntity heart, VirtualModule module) {
        previews.put(player.getUUID(), new Preview(heart, module.id(), player.level().getGameTime() + 100));
        player.closeContainer();
    }
    public static boolean start(Player player, HeartBlockEntity heart, VirtualModule module) {
        if (module.coreModule()) return false;
        if (!(player instanceof ServerPlayer server) || !heart.canEdit(player)) return false;
        sessions.put(player.getUUID(), new Session(heart, module.id(), heart.modules().revision(), player.level().getGameTime() + 2400, new ArrayList<>()));
        player.closeContainer(); PacketDistributor.sendToPlayer(server, new ModuleNetworking.MarkState(true));
        player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_" + (module.type().equals("arena") ? "arena_point" : List.of("auto_facing","signal_turn").contains(module.type()) ? "auto_facing_point" : module.type().equals("navigation") ? "navigation_point" : module.area() ? "first" : "point")), true); return true;
    }
    public static void cancel(Player player) {
        if (sessions.remove(player.getUUID()) != null && player instanceof ServerPlayer server) {
            PacketDistributor.sendToPlayer(server, new ModuleNetworking.MarkState(false));
            player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_cancelled"), true);
        }
    }
    /** Clearing requires an active editing session and an authoritative sneak-click into air. */
    public static boolean clearMarks(Player player) {
        var session=sessions.get(player.getUUID());
        if(session==null||!valid(player,session)||!player.isShiftKeyDown()||player.level()!=session.heart.getLevel()||session.heart.isRemoved())return false;
        var eye=SableBodyProbe.toWorld(player.level(),player.getEyePosition());
        var hit=player.level().clip(new ClipContext(eye,eye.add(player.getLookAngle().scale(player.blockInteractionRange()+1)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.MISS)return false;
        var module=session.heart.modules().find(session.module);
        module.clearMarks();session.heart.modules().changed();session.heart.setChanged();
        previews.entrySet().removeIf(e->e.getValue().heart==session.heart&&e.getValue().module.equals(session.module));
        sessions.remove(player.getUUID());DashboardSelection.set(session.heart,player.getUUID(),module.id());
        if(player instanceof ServerPlayer server)PacketDistributor.sendToPlayer(server,new ModuleNetworking.MarkState(false));
        player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_cleared"),true);
        session.heart.openDashboard(player);return true;
    }
    public static void attack(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide ? dev.sablelife.client.HeartDashboardScreen.marking : sessions.containsKey(event.getEntity().getUUID()))
            event.setCanceled(true);
    }
    private static boolean valid(Player player, Session s) {
        return s.heart.canEdit(player) && s.heart.modules().find(s.module) != null
                && s.heart.modules().revision() == s.revision && player.level().getGameTime() < s.expires;
    }
    public static void click(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) {
            if (dev.sablelife.client.HeartDashboardScreen.marking) { event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS); }
            return;
        }
        var player = event.getEntity(); var session = sessions.get(player.getUUID()); if (session == null) return;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS);
        if (!valid(player, session)) { cancel(player); return; }
        if (player.isShiftKeyDown()) { cancel(player); session.heart.openDashboard(player); return; }
        var module = session.heart.modules().find(session.module);
        if (module.type().equals("arena")) {
            // Arena marking uses a world-space server ray, independently of ordinary limb marking.
            // Sable returns plot block coordinates for body hits; terrain hits remain world blocks.
            Vec3 eye = player.getEyePosition();
            var hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 1)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && player.level().mayInteract(player, hit.getBlockPos()))
                select(player, hit.getBlockPos(), hit.getDirection());
            return;
        }
        var pos = event.getPos();
        if (!player.canInteractWithBlock(pos, 1) || !player.level().mayInteract(player, pos)) return;
        Vec3 eye = SableBodyProbe.toWorld(player.level(), player.getEyePosition());
        Vec3 center = SableBodyProbe.toWorld(player.level(), pos.getCenter());
        var hit = player.level().clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) {
            hit = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 1)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) return;
        }
        // Reach/occlusion was checked above. A ray to the block center is not the clicked face.
        select(player, pos, List.of("wireless_redstone", "particles", "melee").contains(module.type()) ? event.getFace() : hit.getDirection());
    }
    /** Call only after a server-authoritative reach/shape ray; package API also supports integration tests. */
    public static boolean select(Player player, BlockPos pos, Direction face) {
        var session = sessions.get(player.getUUID()); if (session == null || !valid(player, session)) return false;
        var module = session.heart.modules().find(session.module);
        if (module.type().equals("arena")) {
            if (player.level().getBlockState(pos).isAir()) return false;
            var body = dev.ryanhcode.sable.Sable.HELPER.getContaining(player.level(), pos);
            Vec3 center = body == null ? pos.getCenter() : body.logicalPose().transformPosition(pos.getCenter());
            module.arenaCenter(center, player.level().dimension().location().toString());
            player.displayClientMessage(Component.translatable("dashboard.sablelife.arena_marked",
                    String.format(java.util.Locale.ROOT, "%.1f", center.x), String.format(java.util.Locale.ROOT, "%.1f", center.z)), false);
            session.heart.modules().changed(); session.heart.setChanged(); sessions.remove(player.getUUID());
            if (player instanceof ServerPlayer server) PacketDistributor.sendToPlayer(server, new ModuleNetworking.MarkState(false));
            session.heart.openDashboard(player); return true;
        }
        var anchor = ModuleAnchor.create(session.heart, pos, face);
        if (anchor == null) { player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_wrong_body"), true); return false; }
        if (!session.points.isEmpty()) {
            var first = session.points.getFirst();
            if (!first.bodyId().equals(anchor.bodyId()) || Math.abs(first.offset().getX() - anchor.offset().getX()) > 31
                    || Math.abs(first.offset().getY() - anchor.offset().getY()) > 31 || Math.abs(first.offset().getZ() - anchor.offset().getZ()) > 31) {
                player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_same_body"), true); return false;
            }
        }
        session.points.add(anchor);
        if (module.area() && session.points.size() < 2) {
            player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_second"), true); return true;
        }
        module.anchors(session.points); session.heart.modules().changed(); session.heart.setChanged();
        sessions.remove(player.getUUID());
        if (player instanceof ServerPlayer server) PacketDistributor.sendToPlayer(server, new ModuleNetworking.MarkState(false));
        session.heart.openDashboard(player); return true;
    }
    public static void tick(ServerTickEvent.Post event) {
        for (var id : List.copyOf(previews.keySet())) {
            var p = previews.get(id); var player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null || !p.heart.canAccessEditor(player) || player.level() != p.heart.getLevel() || p.heart.isRemoved()
                    || p.heart.modules().find(p.module) == null || p.heart.modules().find(p.module).invalidated() || player.level().getGameTime() >= p.expires
                    || SableBodyProbe.toWorld(player.level(), player.getEyePosition()).distanceToSqr(SableBodyProbe.toWorld(player.level(), p.heart.getBlockPos().getCenter())) > 4096) {
                previews.remove(id); continue;
            }
            if (player.level().getGameTime() % 5 == 0) previewModule(player, p.heart, p.heart.modules().find(p.module));
        }
        for (var id : List.copyOf(sessions.keySet())) {
            var player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null) { sessions.remove(id); continue; }
            var session = sessions.get(id);
            if (!valid(player, session)) { cancel(player); continue; }
            if (player.level().getGameTime() % 5 == 0) {
                var module = session.heart.modules().find(session.module);
                player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_" + (module.type().equals("arena") ? "arena_point" : List.of("auto_facing","signal_turn").contains(module.type()) ? "auto_facing_point" : module.type().equals("navigation") ? "navigation_point" : module.area()
                        ? session.points.isEmpty() ? "first" : "second" : "point")), true);
                preview(player, session.heart, session.points);
            }
        }
    }
    public static void preview(ServerPlayer player, HeartBlockEntity heart, List<ModuleAnchor> anchors) {
        var level = (ServerLevel) player.level();
        if (anchors.isEmpty()) return;
        var a = anchors.getFirst(); if (!a.status(heart).equals("valid")) return;
        if (anchors.size() == 2 && anchors.get(1).status(heart).equals("valid") && a.bodyId().equals(anchors.get(1).bodyId())) {
            var body = a.body(heart); var ap = a.position(body); var bp = anchors.get(1).position(body);
            double[] min = {Math.min(ap.getX(), bp.getX()), Math.min(ap.getY(), bp.getY()), Math.min(ap.getZ(), bp.getZ())};
            double[] max = {Math.max(ap.getX(), bp.getX()) + 1, Math.max(ap.getY(), bp.getY()) + 1, Math.max(ap.getZ(), bp.getZ()) + 1};
            for (int axis = 0; axis < 3; axis++) for (int p = 0; p < 2; p++) for (int q = 0; q < 2; q++) for (int step = 0; step <= 16; step++) {
                double[] v = min.clone(); v[axis] = min[axis] + (max[axis] - min[axis]) * step / 16.;
                v[(axis + 1) % 3] = p == 0 ? min[(axis + 1) % 3] : max[(axis + 1) % 3];
                v[(axis + 2) % 3] = q == 0 ? min[(axis + 2) % 3] : max[(axis + 2) % 3];
                particle(level, player, body.logicalPose().transformPosition(new Vec3(v[0], v[1], v[2])));
            }
        } else {
            var body = a.body(heart); Vec3 start = a.position(body).getCenter(); Vec3 normal = Vec3.atLowerCornerOf(a.face().getNormal());
            for (int i = 0; i <= 10; i++) particle(level, player, body.logicalPose().transformPosition(start.add(normal.scale(i / 5.))));
        }
    }
    private static void previewModule(ServerPlayer player, HeartBlockEntity heart, VirtualModule module) {
        if (module.type().equals("wireless_redstone") && HeartModules.status(module, heart).equals("valid")) {
            var anchor = module.anchors().getFirst(); var body = anchor.body(heart);
            var center = anchor.position(body).getCenter(); var level = (ServerLevel) player.level();
            var dust = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.2F, 1, .4F), 1.2F);
            for (var face : Direction.values()) if (module.value("redstoneFaces") == 0 || face == anchor.face()) {
                var normal = Vec3.atLowerCornerOf(face.getNormal());
                var u = face.getAxis() == Direction.Axis.X ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
                var v = normal.cross(u); var surface = center.add(normal.scale(.505));
                for (int edge = 0; edge < 4; edge++) for (int i = 0; i <= 8; i++) {
                    double t = -.45 + .9 * i / 8.;
                    var p = surface.add(u.scale(edge < 2 ? t : edge == 2 ? -.45 : .45))
                            .add(v.scale(edge < 2 ? edge == 0 ? -.45 : .45 : t));
                    var world = body.logicalPose().transformPosition(p);
                    level.sendParticles(player, dust, true, world.x, world.y, world.z, 1, 0, 0, 0, 0);
                }
            }
            player.displayClientMessage(Component.translatable("dashboard.sablelife.redstone_help"), true); return;
        }
        if(module.type().equals("item_display")&&!module.anchors().isEmpty()&&module.anchors().getFirst().status(heart).equals("valid")){ModuleItemDisplay.preview(player,heart,module);return;}
        if (module.type().equals("particles")) {
            if (!HeartModules.status(module, heart).equals("valid") && !HeartModules.status(module, heart).equals("disabled")) return;
            ParticlePattern.preview(player, heart, module); return;
        }
        if (module.type().equals("arena")) {
            var level = (ServerLevel) player.level();
            var red = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1, .2F, .2F), 1.4F);
            var yellow = new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1, 1, .1F), 1.4F);
            var playerCenter = ModuleArena.corePosition(heart);
            boolean arenaHere = module.arenaCenter() != null && level.dimension().location().toString().equals(module.arenaDimension());
            for (int i = 0; i < 128; i++) {
                double angle = Math.PI * 2 * i / 128., x = Math.cos(angle), z = Math.sin(angle);
                if (arenaHere) {
                    var p = module.arenaCenter().add(x * module.value("arenaRadius"), .2, z * module.value("arenaRadius"));
                    level.sendParticles(player, red, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                var p = playerCenter.add(x * module.value("warningRange"), .2, z * module.value("warningRange"));
                level.sendParticles(player, yellow, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            player.displayClientMessage(Component.translatable("dashboard.sablelife.arena_help"), true); return;
        }
        if (module.type().equals("ground_strike") && HeartModules.status(module, heart).equals("valid")) {
            for (var point : ModuleGroundStrike.pattern((ServerLevel) player.level(), heart, module)) {
                var dust = ModuleGroundStrike.color(module);
                for (int i = 0; i < 24; i++) { double a = Math.PI * 2 * i / 24; var p = point.add(Math.cos(a) * module.value("strikeRadius"), .12, Math.sin(a) * module.value("strikeRadius"));
                    ((ServerLevel) player.level()).sendParticles(player, dust, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
            return;
        }
        if(module.type().equals("slash")&&HeartModules.status(module,heart).equals("valid")){ModuleSlash.preview(player,heart,module);return;}
        if(module.type().equals("sphere_blast")&&HeartModules.status(module,heart).equals("valid")){ModuleSphereBlast.preview(player,heart,module);return;}
        if (module.type().equals("linear_movement") && HeartModules.status(module,heart).equals("valid")) {ModuleLinearMovement.preview(player,heart,module);return;}
        if (module.type().equals("homing") && HeartModules.status(module, heart).equals("valid")) { ModuleHoming.preview(player, heart, module); return; }
        if (module.type().equals("quake") && HeartModules.status(module, heart).equals("valid")) { ModuleQuake.preview(player, heart, module); return; }
        if (module.type().equals("pull") && (TargetCastOrigin.targeted(module) || !module.anchors().isEmpty() && module.anchors().getFirst().status(heart).equals("valid"))) { ModulePull.preview(player, heart, module); return; }
        if (module.type().equals("laser") && !module.anchors().isEmpty() && module.anchors().getFirst().status(heart).equals("valid")) {
            ModuleLaser.preview(player, heart, module); return;
        }
        if (module.type().equals("bgm")) {
            var level = (ServerLevel) player.level(); var center = SableBodyProbe.toWorld(level, heart.getBlockPos().getCenter());
            for (int plane = 0; plane < 3; plane++) for (int i = 0; i < 64; i++) {
                double a = Math.PI * 2 * i / 64, x = Math.cos(a) * module.value("bgmRadius"), y = Math.sin(a) * module.value("bgmRadius");
                particle(level, player, center.add(switch (plane) { case 0 -> new Vec3(x, y, 0); case 1 -> new Vec3(x, 0, y); default -> new Vec3(0, x, y); }));
            }
            return;
        }
        if (module.anchors().isEmpty() || !module.anchors().getFirst().status(heart).equals("valid")) return;
        var level = (ServerLevel) player.level();
        if (module.type().equals("melee")) {
            var anchor = module.anchors().getFirst(); var body = anchor.body(heart); var geometry=MeleeGeometry.frame(heart,module);var center=geometry.center();
            var size = ModuleCombat.halfSize(heart, module); double[] half = {size.x, size.y, size.z};
            for (int axis = 0; axis < 3; axis++) for (int a : new int[]{-1, 1}) for (int b : new int[]{-1, 1}) for (int step = 0; step <= 20; step++) {
                double[] delta = new double[3]; delta[axis] = -half[axis] + 2 * half[axis] * step / 20.;
                delta[(axis + 1) % 3] = a * half[(axis + 1) % 3]; delta[(axis + 2) % 3] = b * half[(axis + 2) % 3];
                particle(level, player, center.add(dev.sablelife.attack.ContactBox.rotate(geometry.rotation(), new Vec3(delta[0], delta[1], delta[2]))));
            }
        } else if (module.type().equals("loot") || module.type().equals("armor")) {
            var anchor = module.anchors().getFirst(); preview(player, heart, List.of(anchor, anchor));
            var box = anchor.body(heart).boundingBox();
            double[] min = {box.minX(), box.minY(), box.minZ()}, max = {box.maxX(), box.maxY(), box.maxZ()};
            var dust = new net.minecraft.core.particles.DustParticleOptions(module.type().equals("armor") ? new org.joml.Vector3f(.2F, .8F, 1) : new org.joml.Vector3f(.95F, .65F, .2F), 1.2F);
            for (int axis = 0; axis < 3; axis++) for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) for (int step = 0; step <= 20; step++) {
                double[] v = min.clone(); v[axis] = min[axis] + (max[axis] - min[axis]) * step / 20.;
                v[(axis + 1) % 3] = a == 0 ? min[(axis + 1) % 3] : max[(axis + 1) % 3];
                v[(axis + 2) % 3] = b == 0 ? min[(axis + 2) % 3] : max[(axis + 2) % 3];
                level.sendParticles(player, dust, true, v[0], v[1], v[2], 1, 0, 0, 0, 0);
            }
            player.displayClientMessage(Component.translatable("dashboard.sablelife." + (module.type().equals("armor") ? "armor_preview_help" : "loot_preview_help")), true);
        } else if (module.type().equals("sound") && module.value("contactMode") == 1) {
            var start = ModuleEvents.contactStart(heart, module);
            var end = start.add(ModuleEvents.contactDirection(heart, module).scale(module.value("contactDistance")));
            for (int i = 0; i <= 20; i++) particle(level, player, start.lerp(end, i / 20.));
            preview(player, heart, List.of(module.anchors().getFirst(), module.anchors().getFirst()));
        } else if(List.of("auto_facing","signal_turn").contains(module.type())){
            var anchor=module.anchors().getFirst();var center=anchor.world(heart,true);
            var front=dev.sablelife.attack.ContactBox.rotate(new org.joml.Quaterniond(anchor.body(heart).logicalPose().orientation()),Vec3.atLowerCornerOf(anchor.face().getNormal()));
            for(int i=0;i<=20;i++)particle(level,player,center.add(front.scale(i*.15)));
            var coreCenter=SableBodyProbe.toWorld(level,heart.getBlockPos().getCenter());
            if(module.type().equals("auto_facing"))for(int i=0;i<64;i++){double angle=i*Math.PI*2/64;particle(level,player,coreCenter.add(Math.cos(angle)*module.value("facingRange"),0,Math.sin(angle)*module.value("facingRange")));}
            player.displayClientMessage(Component.translatable(module.type().equals("signal_turn")?"dashboard.sablelife.signal_turn_help":"dashboard.sablelife.auto_facing_help"),true);
        } else if (module.type().equals("navigation")) {
            var anchor = module.anchors().getFirst(); var center = anchor.world(heart, false);
            var rotation = new org.joml.Quaterniond(anchor.body(heart).logicalPose().orientation());
            var front = TargetNavigation.front(module);
            var directions = new net.minecraft.core.Direction[] {front, front.getCounterClockWise(), front.getClockWise(), front.getOpposite(), net.minecraft.core.Direction.UP, net.minecraft.core.Direction.DOWN};
            var colors = new org.joml.Vector3f[] {new org.joml.Vector3f(1, .8F, .2F), new org.joml.Vector3f(1, .2F, .2F), new org.joml.Vector3f(.2F, .5F, 1), new org.joml.Vector3f(.7F, .7F, .7F), new org.joml.Vector3f(.2F, 1, .4F), new org.joml.Vector3f(.8F, .3F, 1)};
            for (int d = 0; d < directions.length; d++) {
                var vector = dev.sablelife.attack.ContactBox.rotate(rotation, Vec3.atLowerCornerOf(directions[d].getNormal()));
                var dust = new net.minecraft.core.particles.DustParticleOptions(colors[d], 1.2F);
                for (int i = 1; i <= 20; i++) {
                    var point = center.add(vector.scale(i * .15));
                    level.sendParticles(player, dust, true, point.x, point.y, point.z, 1, 0, 0, 0, 0);
                }
            }
            for (int plane = 0; plane < 3; plane++) for (int i = 0; i < 48; i++) {
                double a = Math.PI * 2 * i / 48, x = Math.cos(a) * module.value("radius"), y = Math.sin(a) * module.value("radius");
                particle(level, player, center.add(switch (plane) { case 0 -> new Vec3(x, y, 0); case 1 -> new Vec3(x, 0, y); default -> new Vec3(0, x, y); }));
            }
            player.displayClientMessage(Component.translatable("dashboard.sablelife.navigation_preview_help"), true);
        } else if (module.type().equals("sensor")) {
            var center = ModuleCombat.origin(heart, module, false); double radius = module.value("radius");
            if (module.value("shape") == 0) {
                for (double r : new double[] {radius, module.value("innerRadius")}) if (r > 0)
                    for (int plane = 0; plane < 3; plane++) for (int i = 0; i < 48; i++) {
                        double angle = Math.PI * 2 * i / 48, a = Math.cos(angle) * r, b = Math.sin(angle) * r;
                        particle(level, player, center.add(switch (plane) { case 0 -> new Vec3(a, b, 0); case 1 -> new Vec3(a, 0, b); default -> new Vec3(0, a, b); }));
                    }
            } else {
                var forward = ModuleCombat.direction(heart, module);
                var side = forward.cross(Math.abs(forward.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
                var up = side.cross(forward).normalize();
                for (int ring = 0; ring <= 4; ring++) for (int i = 0; i < 40; i++) {
                    double t = ring / 4., angle = Math.PI * 2 * i / 40, width = .5 + (module.value("frontWidth") - .5) * t;
                    particle(level, player, center.add(forward.scale(radius * t)).add(side.scale(width * Math.cos(angle))).add(up.scale(width * Math.sin(angle))));
                }
            }
        } else if (module.type().equals("pulse") || module.type().equals("sound")) {
            var center = ModuleCombat.origin(heart, module, false); double radius = module.value("radius");
            for (int plane = 0; plane < 3; plane++) for (int i = 0; i < 48; i++) {
                double angle = Math.PI * 2 * i / 48, a = Math.cos(angle) * radius, b = Math.sin(angle) * radius;
                particle(level, player, center.add(switch (plane) { case 0 -> new Vec3(a, b, 0); case 1 -> new Vec3(a, 0, b); default -> new Vec3(0, a, b); }));
            }
        } else if (module.type().equals("mortar")) {
            var velocities = dev.sablelife.attack.ShotTrajectory.firingVelocities(level, heart, module);
            if (velocities.isEmpty()) { player.displayClientMessage(Component.translatable("dashboard.sablelife.no_mortar_target"), true); return; }
            for (var velocity : velocities) {
                var flight = dev.sablelife.attack.ShotTrajectory.trace(level, ModuleCombat.nozzle(heart, module), velocity);
                for (int i = 0; i < flight.points().size(); i += Math.max(1, flight.points().size() / 150)) particle(level, player, flight.points().get(i));
                dev.sablelife.attack.ShotTrajectory.ring(level, player, flight.landing(), module.value("blastRadius"), dev.sablelife.module.ModuleColors.packed(module,"mortarWarningColor"));
            }
        } else if (module.type().equals("flame") || module.type().equals("arrow") || module.type().equals("gun")) {
            var start = ModuleCombat.nozzle(heart, module); var direction = ModuleCombat.direction(heart, module);
            double range = module.type().equals("flame") ? module.value("range") : module.type().equals("gun") ? module.value("range") : 8;
            double width = module.type().equals("flame") ? module.value("width") : module.value("endRadius") * range / 8;
            if(module.type().equals("flame"))PacketDistributor.sendToPlayer(player,new ModuleNetworking.FlameJet(start,direction,module.value("flameSpeed"),range,width,0,1F,module.id().getLeastSignificantBits()^level.getGameTime(),module.particleEntries()));
            var side = direction.cross(Math.abs(direction.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize(); var up = side.cross(direction).normalize();
            for (int i = 0; i <= 32; i++) particle(level, player, start.add(direction.scale(range * i / 32)));
            for (int ring = 1; ring <= 3; ring++) for (int i = 0; i < 32; i++) {
                double angle = Math.PI * 2 * i / 32, t = ring / 3.;
                particle(level, player, start.add(direction.scale(range * t)).add(side.scale(width * t * Math.cos(angle))).add(up.scale(width * t * Math.sin(angle))));
            }
        } else preview(player, heart, module.anchors());
    }
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        sessions.clear();previews.clear();
    }
    private static void particle(ServerLevel level, ServerPlayer player, Vec3 point) {
        level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, true, point.x, point.y, point.z, 1, 0, 0, 0, 0);
    }
}
