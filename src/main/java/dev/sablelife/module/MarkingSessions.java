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
        if(!ModuleAvailability.enabled(heart,module)||module.type().equals("surface_field"))return;
        if(module.type().equals("visual_shockwave"))ModuleShockwave.preview(player,heart,module);
        previews.put(player.getUUID(), new Preview(heart, module.id(), player.level().getGameTime() + 100));
        if(module.type().equals("visual_shockwave"))previewModule(player,heart,module);
        if(module.type().equals("blueprint_spawn"))dev.sablelife.ownership.BlueprintSpawns.preview(player,heart,module,true);
        player.closeContainer();
    }
    public static boolean start(Player player, HeartBlockEntity heart, VirtualModule module) {
        if (!ModuleAvailability.enabled(heart,module)||module.coreModule()) return false;
        if (!(player instanceof ServerPlayer server) || !heart.canEdit(player)) return false;
        sessions.put(player.getUUID(), new Session(heart, module.id(), heart.modules().revision(), player.level().getGameTime() + 2400, new ArrayList<>()));
        player.closeContainer(); PacketDistributor.sendToPlayer(server, new ModuleNetworking.MarkState(true));
        player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_" + (module.type().equals("blueprint_spawn")?(dev.sablelife.ownership.BlueprintSpawnPlacement.follows(module)?"blueprint_follow":"blueprint_point"):module.type().equals("arena") ? "arena_point" : List.of("auto_facing","signal_turn").contains(module.type()) ? "auto_facing_point" : module.type().equals("navigation") ? "navigation_point" : module.area() ? "first" : "point")), true); return true;
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
        if(module.type().equals("blueprint_spawn")){if(dev.sablelife.ownership.BlueprintSpawnPlacement.follows(module))module.anchors(List.of());else module.clearFixedPoint();}else module.clearMarks();session.heart.modules().changed();session.heart.setChanged();
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
        return s.heart.canEdit(player) && ModuleAvailability.enabled(s.heart,s.heart.modules().find(s.module)) && s.heart.modules().find(s.module) != null
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
        if (module.type().equals("arena") || module.type().equals("blueprint_spawn")&&!dev.sablelife.ownership.BlueprintSpawnPlacement.follows(module)) {
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
        select(player, pos, List.of("wireless_redstone", "particles", "melee", "distortion_trail", "blueprint_spawn", "block_interaction").contains(module.type()) ? event.getFace() : hit.getDirection());
    }
    /** Call only after a server-authoritative reach/shape ray; package API also supports integration tests. */
    public static boolean select(Player player, BlockPos pos, Direction face) {
        var session = sessions.get(player.getUUID()); if (session == null || !valid(player, session)) return false;
        var module = session.heart.modules().find(session.module);
        if (module.type().equals("arena") || module.type().equals("blueprint_spawn")&&!dev.sablelife.ownership.BlueprintSpawnPlacement.follows(module)) {
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
        if (player instanceof ServerPlayer server) {
            PacketDistributor.sendToPlayer(server, new ModuleNetworking.MarkState(false));
            if(module.type().equals("block_interaction"))ModuleBlockInteraction.preview(server,session.heart,module);
        }
        session.heart.openDashboard(player); return true;
    }
    public static void tick(ServerTickEvent.Post event) {
        for (var id : List.copyOf(previews.keySet())) {
            var p = previews.get(id); var player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null || !p.heart.canAccessEditor(player) || player.level() != p.heart.getLevel() || p.heart.isRemoved()
                    || !ModuleAvailability.enabled(p.heart,p.heart.modules().find(p.module)) || p.heart.modules().find(p.module) == null || p.heart.modules().find(p.module).invalidated() || player.level().getGameTime() >= p.expires
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
                player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_" + (module.type().equals("blueprint_spawn")?(dev.sablelife.ownership.BlueprintSpawnPlacement.follows(module)?"blueprint_follow":"blueprint_point"):module.type().equals("arena") ? "arena_point" : List.of("auto_facing","signal_turn").contains(module.type()) ? "auto_facing_point" : module.type().equals("navigation") ? "navigation_point" : module.area()
                        ? session.points.isEmpty() ? "first" : "second" : "point")), true);
                preview(player, session.heart, session.points);
                if(module.type().equals("visual_shockwave")&&session.points.isEmpty())previewModule(player,session.heart,module);
            }
        }
    }
    public static void preview(ServerPlayer player, HeartBlockEntity heart, List<ModuleAnchor> anchors) {
        if (anchors.isEmpty()) return;
        var first=anchors.getFirst();
        if(!first.status(heart).equals("valid"))return;
        var body=first.body(heart);
        var start=first.position(body);
        boolean area=anchors.size()==2&&anchors.get(1).status(heart).equals("valid")&&first.bodyId().equals(anchors.get(1).bodyId());
        var end=area?anchors.get(1).position(body):start;
        var min=new Vec3(Math.min(start.getX(),end.getX()),Math.min(start.getY(),end.getY()),Math.min(start.getZ(),end.getZ())).subtract(.002,.002,.002);
        var max=new Vec3(Math.max(start.getX(),end.getX())+1,Math.max(start.getY(),end.getY())+1,Math.max(start.getZ(),end.getZ())+1).add(.002,.002,.002);
        var pose=body.logicalPose();
        var edges=MarkOutline.box(min,max,pose::transformPosition);
        if(!area){
            var center=start.getCenter();
            edges.add(pose.transformPosition(center));
            edges.add(pose.transformPosition(center.add(Vec3.atLowerCornerOf(first.face().getNormal()).scale(2))));
        }
        MarkOutline.send(player,MarkOutline.Kind.MARK,MarkOutline.MARK_COLOR,edges);
    }
    private static void previewModule(ServerPlayer player, HeartBlockEntity heart, VirtualModule module) {
        if(module.type().equals("block_interaction")){
            if(!module.anchors().isEmpty()&&module.anchors().getFirst().status(heart).equals("valid"))
                ModuleBlockInteraction.preview(player,heart,module);
            return;
        }
        if(module.type().equals("entity_hitbox")){dev.sablelife.combat.EntityHitboxes.preview(player,heart,module);return;}
        if(module.type().equals("distortion_trail")){ModuleDistortionTrail.preview(player,heart,module);return;}
        if(module.type().equals("visual_shockwave")){
            if(!List.of("valid","disabled").contains(HeartModules.status(module,heart)))return;
            var anchor=module.anchors().getFirst();
            preview(player,heart,List.of(anchor,anchor));
            if(module.value("shockContact")==1){
                var start=ModuleEvents.contactStart(heart,module);
                var end=start.add(ModuleEvents.contactDirection(heart,module).scale(module.value("contactDistance")));
                var level=(ServerLevel)player.level();
                for(int i=0;i<=20;i++)particle(level,player,start.lerp(end,i/20.));
            }
            return;
        }
        if (module.type().equals("gyroscope") && List.of("valid","disabled").contains(HeartModules.status(module,heart))) { preview(player,heart,List.of(module.anchors().getFirst(),module.anchors().getFirst())); ModuleGyroscope.preview(player,heart,module); return; }
        if (List.of("wireless_redstone", "sensor").contains(module.type())) {
            if (module.anchors().isEmpty() || !List.of("valid","disabled").contains(HeartModules.status(module,heart)) || !module.anchors().getFirst().status(heart).equals("valid")) return;
            var anchor = module.anchors().getFirst(); var body = anchor.body(heart);
            var center=anchor.position(body).getCenter();
            var pose=body.logicalPose();
            var edges=new ArrayList<Vec3>();
            boolean wireless=module.type().equals("wireless_redstone");
            for(var face:Direction.values())if(wireless&&module.value("redstoneFaces")==0||face==anchor.face()){
                var normal=Vec3.atLowerCornerOf(face.getNormal());
                var u=(face.getAxis()==Direction.Axis.X?new Vec3(0,1,0):new Vec3(1,0,0)).scale(.45);
                var v=normal.cross(u);
                var surface=center.add(normal.scale(.505));
                var corners=List.of(surface.subtract(u).subtract(v),surface.add(u).subtract(v),surface.add(u).add(v),surface.subtract(u).add(v));
                for(int i=0;i<4;i++){
                    edges.add(pose.transformPosition(corners.get(i)));
                    edges.add(pose.transformPosition(corners.get((i+1)%4)));
                }
                if(wireless){
                    for(int stripe=-2;stripe<=2;stripe++){
                        double t=stripe/3.0;
                        var from=corners.get(0).add(u.scale(2*Math.max(t,0))).add(v.scale(2*Math.max(-t,0)));
                        var to=corners.get(2).subtract(u.scale(2*Math.max(-t,0))).subtract(v.scale(2*Math.max(t,0)));
                        edges.add(pose.transformPosition(from));
                        edges.add(pose.transformPosition(to));
                    }
                }
            }
            MarkOutline.send(player,MarkOutline.Kind.DETAIL,0x33FF66,edges);
            player.displayClientMessage(Component.translatable(module.type().equals("sensor") ? "dashboard.sablelife.sensor_output_help" : "dashboard.sablelife.redstone_help"), true);
            if (module.type().equals("wireless_redstone")) return;
        }
        if(module.type().equals("summoned_weapon")&&!module.anchors().isEmpty()&&module.anchors().getFirst().status(heart).equals("valid")){ModuleSummonedWeapon.preview(player,heart,module);return;}
        if(module.type().equals("health_bar")&&!module.anchors().isEmpty()&&module.anchors().getFirst().status(heart).equals("valid")){ModuleHealthBar.preview(player,heart,module);return;}
        if(module.type().equals("item_display")&&!module.anchors().isEmpty()&&module.anchors().getFirst().status(heart).equals("valid")){ModuleItemDisplay.preview(player,heart,module);return;}
        if (module.type().equals("particles")) {
            if (!HeartModules.status(module, heart).equals("valid") && !HeartModules.status(module, heart).equals("disabled")) return;
            ParticlePattern.preview(player, heart, module); return;
        }
        if(module.type().equals("blueprint_spawn")){dev.sablelife.ownership.BlueprintSpawns.preview(player,heart,module);return;}
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
            player.displayClientMessage(Component.translatable("dashboard.sablelife.arena_preview_help"), true); return;
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
        if(module.type().equals("grab")&&HeartModules.status(module,heart).equals("valid")){ModuleGrab.preview(player,heart,module);return;}
        if(module.type().equals("freeze")&&HeartModules.status(module,heart).equals("valid")){ModuleFreeze.preview(player,heart,module);return;}
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
        if(module.type().equals("pulse")&&TargetCastOrigin.targeted(module)){
            var centers=TargetCastOrigin.points(heart,module);if(centers.isEmpty()){player.displayClientMessage(Component.translatable("dashboard.sablelife.no_cast_target"),true);return;}
            for(var center:centers)for(int plane=0;plane<3;plane++)for(int i=0;i<48;i++){
                double angle=Math.PI*2*i/48,a=Math.cos(angle)*module.value("radius"),b=Math.sin(angle)*module.value("radius");
                particle(player.serverLevel(),player,center.add(switch(plane){case 0->new Vec3(a,b,0);case 1->new Vec3(a,0,b);default->new Vec3(0,a,b);}));
            }return;
        }
        if (module.anchors().isEmpty() || !module.anchors().getFirst().status(heart).equals("valid")) return;
        var level = (ServerLevel) player.level();
        if (module.type().equals("melee")) {
            var geometry=MeleeGeometry.frame(heart,module);
            var half=geometry.half();
            var edges=MarkOutline.box(half.scale(-1),half,point->geometry.center().add(dev.sablelife.attack.ContactBox.rotate(geometry.rotation(),point)));
            MarkOutline.send(player,MarkOutline.Kind.VOLUME,MarkOutline.MARK_COLOR,edges);
        } else if (module.type().equals("loot") || module.type().equals("armor")) {
            var anchor=module.anchors().getFirst();preview(player,heart,List.of(anchor,anchor));
            var box=anchor.body(heart).boundingBox();
            var min=new Vec3(box.minX(),box.minY(),box.minZ());
            var max=new Vec3(box.maxX(),box.maxY(),box.maxZ());
            MarkOutline.send(player,MarkOutline.Kind.DETAIL,module.type().equals("armor")?0x33CCFF:0xF2A633,
                    MarkOutline.box(min,max,java.util.function.UnaryOperator.identity()));
            player.displayClientMessage(Component.translatable("dashboard.sablelife." + (module.type().equals("armor") ? "armor_preview_help" : "loot_preview_help")), true);
        } else if (module.type().equals("trigger_sound") && module.value("contactMode") == 1) {
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
            int[] colors = {0xFFCC33, 0xFF3333, 0x3380FF, 0xB3B3B3, 0x33FF66, 0xCC4DFF};
            var kinds = new MarkOutline.Kind[] {MarkOutline.Kind.ALIGN_FRONT, MarkOutline.Kind.ALIGN_LEFT,
                    MarkOutline.Kind.ALIGN_RIGHT, MarkOutline.Kind.ALIGN_BACK, MarkOutline.Kind.ALIGN_UP, MarkOutline.Kind.ALIGN_DOWN};
            for (int d = 0; d < directions.length; d++) {
                var vector = dev.sablelife.attack.ContactBox.rotate(rotation, Vec3.atLowerCornerOf(directions[d].getNormal()));
                var tip = center.add(vector.scale(3));
                var edges = new ArrayList<Vec3>();
                edges.add(center); edges.add(tip);
                if (d == 0) {
                    // Four arrowhead wings remain readable while viewing the marked front from above or the side.
                    for (var wing : new Vec3[] {Vec3.atLowerCornerOf(front.getClockWise().getNormal()),
                            Vec3.atLowerCornerOf(front.getCounterClockWise().getNormal()), new Vec3(0, 1, 0), new Vec3(0, -1, 0)}) {
                        edges.add(tip);
                        edges.add(tip.subtract(vector.scale(.6)).add(dev.sablelife.attack.ContactBox.rotate(rotation, wing).scale(.35)));
                    }
                }
                MarkOutline.send(player, kinds[d], colors[d], edges);
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
            previewLaunch(player,heart,module);
            var start = ModuleCombat.nozzle(heart, module);
            var directions=module.type().equals("flame")?List.of(ModuleCombat.direction(heart,module)):ProjectileAim.directions(heart,module,start);
            for(var direction:directions) {
            double range = module.type().equals("flame") ? module.value("range") : module.type().equals("gun") ? module.value("range") : 8;
            double width = module.type().equals("flame") ? module.value("width") : module.value("endRadius") * range / 8;
            if (module.type().equals("flame")) {
                double fanAngle = FlameFanGeometry.angle(module);
                PacketDistributor.sendToPlayer(player,new ModuleNetworking.FlameJet(start,direction,module.value("flameSpeed"),range,width,0,1F,module.id().getLeastSignificantBits()^level.getGameTime(),module.particleEntries(),fanAngle));
                if (fanAngle > 0) {
                    var fan = FlameFanGeometry.create(start, direction, range, width, fanAngle);
                    for (int i = 0; i <= 24; i++) {
                        double angle = fan.halfAngle() * (2 * i / 24. - 1);
                        double distance = range * i / 24.;
                        for (int sign : new int[]{-1, 1}) {
                            particle(level, player, fan.point(range, angle, sign));
                            particle(level, player, fan.point(distance, -fan.halfAngle(), sign));
                            particle(level, player, fan.point(distance, fan.halfAngle(), sign));
                        }
                    }
                    for (int i = -4; i <= 4; i++) for (int edge : new int[]{-1, 1})
                        particle(level, player, fan.point(range, edge * fan.halfAngle(), i / 4.));
                    continue;
                }
            }
            var side = direction.cross(Math.abs(direction.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize(); var up = side.cross(direction).normalize();
            for (int i = 0; i <= 32; i++) particle(level, player, start.add(direction.scale(range * i / 32)));
            for (int ring = 1; ring <= 3; ring++) for (int i = 0; i < 32; i++) {
                double angle = Math.PI * 2 * i / 32, t = ring / 3.;
                particle(level, player, start.add(direction.scale(range * t)).add(side.scale(width * t * Math.cos(angle))).add(up.scale(width * t * Math.sin(angle))));
            }
            }
        } else preview(player, heart, module.anchors());
    }
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        sessions.clear();previews.clear();
    }
    /** Cyan: original-face positioning; yellow: fixed launch point. Attack preview starts here. */
    public static void previewLaunch(ServerPlayer player,HeartBlockEntity heart,VirtualModule module){
        var level=(ServerLevel)heart.getLevel();var from=ModuleCombat.baseOrigin(heart,module);var to=ModuleCombat.nozzle(heart,module);
        var cyan=new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0,.8F,1),.6F);
        int count=Math.clamp((int)Math.ceil(from.distanceTo(to)*4),1,256);
        for(int i=0;i<=count;i++){var p=from.lerp(to,i/(double)count);level.sendParticles(player,cyan,true,p.x,p.y,p.z,1,0,0,0,0);}
        level.sendParticles(player,new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1,.85F,0),1.3F),true,to.x,to.y,to.z,1,0,0,0,0);
    }
    private static void particle(ServerLevel level, ServerPlayer player, Vec3 point) {
        level.sendParticles(player, ParticleTypes.ELECTRIC_SPARK, true, point.x, point.y, point.z, 1, 0, 0, 0, 0);
    }
}
