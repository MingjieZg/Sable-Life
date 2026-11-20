package dev.sablelife.module;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.heart.HeartBlockEntity;
import dev.sablelife.item.CoreRemoteItem;
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
    private record Session(HeartBlockEntity heart, UUID module, int revision, long expires, List<ModuleAnchor> points,
                           boolean watchedPart, boolean remote, int outputFace, ChannelFace channelFace) {}
    private record ChannelFace(int channel, dev.sablelife.heart.WirelessChannel identity, List<ModuleAnchor> faces) {}
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private record Preview(HeartBlockEntity heart, UUID module, long expires, int outputFace, int revision) {}
    private static final Map<UUID, Preview> previews = new HashMap<>();
    private record ChannelPreview(HeartBlockEntity heart, ChannelFace channel, int index, long expires) {}
    private static final Map<UUID,ChannelPreview> channelPreviews = new HashMap<>();

    private static boolean sameChannel(HeartBlockEntity heart, ChannelFace target) {
        return target.channel < heart.wireless().size() && heart.wireless().channel(target.channel) == target.identity
                && target.identity.outputFaces() == target.faces;
    }

    public static boolean startChannelFace(Player player, HeartBlockEntity heart, int channelIndex,
                                           dev.sablelife.heart.WirelessChannel channel, int index) {
        if (!(player instanceof ServerPlayer server) || !heart.canEdit(player) || !heart.editorInRange(player,64)
                || !channel.canSetOutputFace(index)) return false;
        var target = new ChannelFace(channelIndex,channel,channel.outputFaces());
        if (!sameChannel(heart,target)) return false;
        sessions.put(player.getUUID(), new Session(heart,null,0,player.level().getGameTime()+2400,
                new ArrayList<>(),false,CoreRemoteItem.isRemoteEditor(server,heart),index,target));
        player.closeContainer();
        PacketDistributor.sendToPlayer(server,new ModuleNetworking.MarkState(true));
        player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_output_face",index+1),true);
        return true;
    }

    public static boolean showChannelFace(ServerPlayer player, HeartBlockEntity heart,
                                          dev.sablelife.heart.WirelessChannel channel, int channelIndex, int index) {
        if (channelIndex < 0 || !heart.canAccessEditor(player) || index < 0 || index >= channel.outputFaces().size()) return false;
        var anchor = channel.outputFaces().get(index);
        if (!anchor.status(heart).equals("valid")) return false;
        channelPreviews.put(player.getUUID(), new ChannelPreview(heart,new ChannelFace(channelIndex,channel,channel.outputFaces()),
                index,player.level().getGameTime()+100));
        previewRedstoneSurface(player,heart,anchor,false,index+1);
        player.closeContainer();
        return true;
    }

    public static boolean isPreviewingChannelFace(Player player, HeartBlockEntity heart) {
        var preview = channelPreviews.get(player.getUUID());
        return preview != null && preview.heart == heart;
    }

    private static void finishChannelFace(Player player, Session session, String message) {
        sessions.remove(player.getUUID());
        if (player instanceof ServerPlayer server) PacketDistributor.sendToPlayer(server,new ModuleNetworking.MarkState(false));
        player.displayClientMessage(Component.translatable("dashboard.sablelife."+message),true);
        session.heart.openWireless(player,session.channelFace.channel,session.outputFace);
    }
    public static void showPreview(ServerPlayer player, HeartBlockEntity heart, VirtualModule module) {
        if(!ModuleAvailability.enabled(heart,module)||module.type().equals("surface_field"))return;
        if (module.type().equals("wireless_redstone")) {
            showOutputFace(player, heart, module, 0);
            return;
        }
        if(module.type().equals("visual_shockwave"))ModuleShockwave.preview(player,heart,module);
        beginPreview(player, heart, module);
        if(List.of("visual_shockwave", "part_break", "damage_accumulation").contains(module.type()))previewModule(player,heart,module);
        if(module.type().equals("blueprint_spawn"))dev.sablelife.ownership.BlueprintSpawns.preview(player,heart,module,true);
        player.closeContainer();
    }
    private static void beginPreview(ServerPlayer player, HeartBlockEntity heart, VirtualModule module) {
        beginPreview(player, heart, module, -1);
    }
    private static void beginPreview(ServerPlayer player, HeartBlockEntity heart, VirtualModule module, int outputFace) {
        previews.put(player.getUUID(), new Preview(heart, module.id(), player.level().getGameTime() + 100,
                outputFace, heart.modules().revision()));
    }
    public static boolean showOutputFace(ServerPlayer player, HeartBlockEntity heart, VirtualModule module, int index) {
        if (!heart.canAccessEditor(player) || !ModuleAvailability.enabled(heart, module)
                || !ModuleRedstone.validIndex(module, index, false) || !module.redstoneFaceStatus(index, heart).equals("valid"))
            return false;
        beginPreview(player, heart, module, index);
        previewOutputFace(player, heart, module, index);
        player.closeContainer();
        return true;
    }
    public static boolean start(Player player, HeartBlockEntity heart, VirtualModule module) {
        return start(player, heart, module, false);
    }
    public static boolean start(Player player, HeartBlockEntity heart, VirtualModule module, boolean watchedPart) {
        if (module.type().equals("wireless_redstone") && !watchedPart) return startOutputFace(player, heart, module, 0);
        return start(player, heart, module, watchedPart, -1);
    }
    public static boolean startOutputFace(Player player, HeartBlockEntity heart, VirtualModule module, int index) {
        return ModuleRedstone.validIndex(module, index, true) && start(player, heart, module, false, index);
    }
    private static boolean start(Player player, HeartBlockEntity heart, VirtualModule module, boolean watchedPart, int outputFace) {
        if (!ModuleAvailability.enabled(heart,module) || (watchedPart ? !module.listensToPart() : module.coreModule())) return false;
        if (!(player instanceof ServerPlayer server) || !heart.canEdit(player)) return false;
        sessions.put(player.getUUID(), new Session(heart, module.id(), heart.modules().revision(), player.level().getGameTime() + 2400,
                new ArrayList<>(), watchedPart, CoreRemoteItem.isRemoteEditor(server, heart), outputFace, null));
        player.closeContainer(); PacketDistributor.sendToPlayer(server, new ModuleNetworking.MarkState(true));
        if (module.type().equals("part_break")) {
            previews.remove(player.getUUID());
            previewModule(server, heart, module);
        }
        if (outputFace >= 0) player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_output_face", outputFace + 1), true);
        else prompt(player,module,watchedPart,false);
        return true;
    }
    private static void prompt(Player player, VirtualModule module, boolean watchedPart, boolean second) {
        String key = watchedPart ? "watched_part" : switch (module.type()) {
            case "blueprint_spawn" -> dev.sablelife.ownership.BlueprintSpawnPlacement.follows(module) ? "blueprint_follow" : "blueprint_point";
            case "arena" -> "arena_point";
            case "linear_movement" -> ModuleLinearMovement.worldDestination(module) ? "move_world_point" : "point";
            case "auto_facing", "signal_turn" -> "auto_facing_point";
            case "navigation" -> "navigation_point";
            default -> module.area() ? (second ? "second" : "first") : "point";
        };
        player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_" + key), true);
    }
    public static void cancel(Player player) {
        if (sessions.remove(player.getUUID()) != null && player instanceof ServerPlayer server) {
            PacketDistributor.sendToPlayer(server, new ModuleNetworking.MarkState(false));
            player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_cancelled"), true);
        }
    }
    public static boolean isMarking(Player player) { return sessions.containsKey(player.getUUID()); }
    /** Clearing requires an active editing session and an authoritative sneak-click into air. */
    public static boolean clearMarks(Player player) {
        var session=sessions.get(player.getUUID());
        if(session==null||!valid(player,session)||!player.isShiftKeyDown()||player.level()!=session.heart.getLevel()||session.heart.isRemoved())return false;
        var eye=SableBodyProbe.toWorld(player.level(),player.getEyePosition());
        var hit=player.level().clip(new ClipContext(eye,eye.add(player.getLookAngle().scale(player.blockInteractionRange()+1)),ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.MISS)return false;
        if (session.channelFace != null) {
            boolean cleared = session.channelFace.identity.removeOutputFace(session.outputFace);
            finishChannelFace(player,session,cleared?"mark_cleared":"mark_cancelled");
            return true;
        }
        var module=session.heart.modules().find(session.module);
        boolean cleared = true;
        if (session.outputFace >= 0) cleared = module.removeRedstoneFace(session.outputFace);
        else if (session.watchedPart) module.watchedPart(null);
        else if (module.type().equals("linear_movement")) {
            if (ModuleLinearMovement.worldDestination(module)) module.clearFixedPoint();
            else module.anchors(List.of());
        } else if(module.type().equals("blueprint_spawn")){if(dev.sablelife.ownership.BlueprintSpawnPlacement.follows(module))module.anchors(List.of());else module.clearFixedPoint();}else module.clearMarks();
        if (cleared) { session.heart.modules().changed(); session.heart.setChanged(); }
        previews.entrySet().removeIf(e->e.getValue().heart==session.heart&&e.getValue().module.equals(session.module));
        sessions.remove(player.getUUID());DashboardSelection.set(session.heart,player.getUUID(),module.id());
        if(player instanceof ServerPlayer server)PacketDistributor.sendToPlayer(server,new ModuleNetworking.MarkState(false));
        player.displayClientMessage(Component.translatable(cleared ? "dashboard.sablelife.mark_cleared" : "dashboard.sablelife.mark_cancelled"), true);
        session.heart.openDashboard(player);return true;
    }
    public static void attack(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide ? dev.sablelife.client.HeartDashboardScreen.marking : sessions.containsKey(event.getEntity().getUUID()))
            event.setCanceled(true);
    }
    private static boolean valid(Player player, Session s) {
        if (s.channelFace != null) return player.level()==s.heart.getLevel() && !s.heart.isRemoved()
                && s.heart.canEdit(player) && s.heart.editorInRange(player,64)
                && (!s.remote || player instanceof ServerPlayer server && CoreRemoteItem.hasAuthorizedSession(server,s.heart))
                && player.level().getGameTime()<s.expires && sameChannel(s.heart,s.channelFace);
        return player.level() == s.heart.getLevel() && s.heart.canEdit(player) && ModuleAvailability.enabled(s.heart,s.heart.modules().find(s.module)) && s.heart.modules().find(s.module) != null
                && (!s.remote || player instanceof ServerPlayer server && CoreRemoteItem.hasAuthorizedSession(server, s.heart))
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
        if (player.isShiftKeyDown()) {
            if (session.channelFace != null) finishChannelFace(player,session,"mark_cancelled");
            else { cancel(player); session.heart.openDashboard(player); }
            return;
        }
        var module = session.heart.modules().find(session.module);
        if (session.channelFace == null && (ModuleLinearMovement.worldDestination(module) || module.type().equals("arena") || module.type().equals("blueprint_spawn")&&!dev.sablelife.ownership.BlueprintSpawnPlacement.follows(module))) {
            // Fixed points use a world-space server ray, independently of ordinary limb marking.
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
        select(player, pos, session.channelFace != null || List.of("wireless_redstone", "particles", "melee", "distortion_trail", "blueprint_spawn", "block_interaction").contains(module.type()) ? event.getFace() : hit.getDirection());
    }
    /** Call only after a server-authoritative reach/shape ray; package API also supports integration tests. */
    public static boolean select(Player player, BlockPos pos, Direction face) {
        var session = sessions.get(player.getUUID()); if (session == null || !valid(player, session)) return false;
        var module = session.heart.modules().find(session.module);
        if (session.channelFace != null) {
            var anchor = ModuleAnchor.create(session.heart,pos,face);
            if (anchor == null) {
                player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_wrong_body"),true);
                return false;
            }
            if (!session.channelFace.identity.setOutputFace(session.outputFace,anchor)) {
                player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_output_duplicate"),true);
                return false;
            }
            finishChannelFace(player,session,"mark_saved");
            return true;
        }
        boolean movementDestination = ModuleLinearMovement.worldDestination(module);
        if (movementDestination || module.type().equals("arena") || module.type().equals("blueprint_spawn")&&!dev.sablelife.ownership.BlueprintSpawnPlacement.follows(module)) {
            if (player.level().getBlockState(pos).isAir()) return false;
            var body = dev.ryanhcode.sable.Sable.HELPER.getContaining(player.level(), pos);
            if (movementDestination && body != null) {
                prompt(player, module, false, false);
                return false;
            }
            Vec3 center = movementDestination ? pos.above().getCenter()
                    : body == null ? pos.getCenter() : body.logicalPose().transformPosition(pos.getCenter());
            if (movementDestination && (player.level().isOutsideBuildHeight(pos.above())
                    || !player.level().getWorldBorder().isWithinBounds(pos.above()))) return false;
            module.arenaCenter(center, player.level().dimension().location().toString());
            if (movementDestination) player.displayClientMessage(Component.translatable("dashboard.sablelife.move_world_marked",
                    coordinates(center)), false);
            else player.displayClientMessage(Component.translatable("dashboard.sablelife.arena_marked",
                    String.format(java.util.Locale.ROOT, "%.1f", center.x), String.format(java.util.Locale.ROOT, "%.1f", center.z)), false);
            session.heart.modules().changed(); session.heart.setChanged(); sessions.remove(player.getUUID());
            if (player instanceof ServerPlayer server) PacketDistributor.sendToPlayer(server, new ModuleNetworking.MarkState(false));
            session.heart.openDashboard(player); return true;
        }
        var anchor = ModuleAnchor.create(session.heart, pos, face);
        if (anchor == null) { player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_wrong_body"), true); return false; }
        if (session.outputFace >= 0) {
            if (!module.setRedstoneFace(session.outputFace, anchor)) {
                player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_output_duplicate"), true);
                return false;
            }
        } else {
            if (!session.points.isEmpty()) {
                var first = session.points.getFirst();
                if (!first.bodyId().equals(anchor.bodyId()) || Math.abs(first.offset().getX() - anchor.offset().getX()) > 31
                        || Math.abs(first.offset().getY() - anchor.offset().getY()) > 31 || Math.abs(first.offset().getZ() - anchor.offset().getZ()) > 31) {
                    player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_same_body"), true); return false;
                }
            }
            session.points.add(anchor);
            if (!session.watchedPart && module.area() && session.points.size() < 2) {
                player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_second"), true); return true;
            }
            if (session.watchedPart) module.watchedPart(anchor);
            else {
                if (module.type().equals("part_break") && !ModulePartBreak.validSelection(module, session.points)) {
                    session.points.removeLast();
                    player.displayClientMessage(Component.translatable("dashboard.sablelife.part_break_limit",ModulePartBreak.MAX_CELLS), true);
                    return false;
                }
                module.anchors(session.points);
            }
        }
        session.heart.modules().changed(); session.heart.setChanged();
        sessions.remove(player.getUUID());
        if (player instanceof ServerPlayer server) {
            PacketDistributor.sendToPlayer(server, new ModuleNetworking.MarkState(false));
            if(module.type().equals("block_interaction"))ModuleBlockInteraction.preview(server,session.heart,module);
            if (module.type().equals("part_break")) {
                beginPreview(server, session.heart, module);
                previewModule(server, session.heart, module);
            }
        }
        session.heart.openDashboard(player); return true;
    }
    public static String coordinates(Vec3 point) {
        return String.format(Locale.ROOT, "%.1f / %.1f / %.1f", point.x, point.y, point.z);
    }
    public static void tick(ServerTickEvent.Post event) {
        var channelIterator = channelPreviews.entrySet().iterator();
        while (channelIterator.hasNext()) {
            var entry = channelIterator.next();
            var preview = entry.getValue();
            var player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || player.containerMenu != player.inventoryMenu || isMarking(player)
                    || player.level()!=preview.heart.getLevel() || preview.heart.isRemoved()
                    || !preview.heart.canAccessEditor(player) || !preview.heart.editorInRange(player,64)
                    || !sameChannel(preview.heart,preview.channel)) {
                channelIterator.remove(); continue;
            }
            if (player.level().getGameTime() >= preview.expires) {
                channelIterator.remove();
                preview.heart.openWireless(player, preview.channel.channel, preview.index);
                continue;
            }
            var anchor = preview.channel.faces.get(preview.index);
            if (player.level().getGameTime()%5==0 && anchor.status(preview.heart).equals("valid"))
                previewRedstoneSurface(player,preview.heart,anchor,false,preview.index+1);
        }
        for (var id : List.copyOf(previews.keySet())) {
            var p = previews.get(id); var player = event.getServer().getPlayerList().getPlayer(id);
            var module = p.heart.modules().find(p.module);
            if (player == null || !p.heart.canAccessEditor(player) || player.level() != p.heart.getLevel() || p.heart.isRemoved()
                    || module == null || !ModuleAvailability.enabled(p.heart, module)
                    || module.invalidated() && !ModuleLinearMovement.worldDestination(module) && !module.type().equals("part_break")
                    || p.outputFace >= 0 && p.revision != p.heart.modules().revision()
                    || player.level().getGameTime() >= p.expires
                    || SableBodyProbe.toWorld(player.level(), player.getEyePosition()).distanceToSqr(SableBodyProbe.toWorld(player.level(), p.heart.getBlockPos().getCenter())) > 4096) {
                previews.remove(id); continue;
            }
            if (player.level().getGameTime() % 5 == 0) {
                if (p.outputFace >= 0) previewOutputFace(player, p.heart, module, p.outputFace);
                else previewModule(player, p.heart, module);
            }
        }
        for (var id : List.copyOf(sessions.keySet())) {
            var player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null) { sessions.remove(id); continue; }
            var session = sessions.get(id);
            if (!valid(player, session)) { cancel(player); continue; }
            if (player.level().getGameTime() % 5 == 0) {
                var module = session.heart.modules().find(session.module);
                if (session.channelFace != null) {
                    player.displayClientMessage(Component.translatable("dashboard.sablelife.mark_output_face",session.outputFace+1),true);
                    continue;
                }
                prompt(player,module,session.watchedPart,!session.points.isEmpty());
                if (module.type().equals("part_break")) previewPartBreak(player, session.heart, module, session.points);
                else preview(player, session.heart, session.points);
                if(module.type().equals("visual_shockwave")&&session.points.isEmpty())previewModule(player,session.heart,module);
            }
        }
    }
    public static void preview(ServerPlayer player, HeartBlockEntity heart, List<ModuleAnchor> anchors) {
        preview(player, heart, anchors, true);
    }
    private static void preview(ServerPlayer player, HeartBlockEntity heart, List<ModuleAnchor> anchors, boolean showFacing) {
        if (anchors.isEmpty()) return;
        var first=anchors.getFirst();
        if(!first.status(heart).equals("valid"))return;
        var body=first.body(heart);
        var start=first.position(body);
        boolean area=anchors.size()==2&&anchors.get(1).status(heart).equals("valid")&&first.bodyId().equals(anchors.get(1).bodyId());
        if (!showFacing && anchors.size() == 2 && !area) return;
        var end=area?anchors.get(1).position(body):start;
        var min=new Vec3(Math.min(start.getX(),end.getX()),Math.min(start.getY(),end.getY()),Math.min(start.getZ(),end.getZ())).subtract(.002,.002,.002);
        var max=new Vec3(Math.max(start.getX(),end.getX())+1,Math.max(start.getY(),end.getY())+1,Math.max(start.getZ(),end.getZ())+1).add(.002,.002,.002);
        var pose=body.logicalPose();
        var edges=MarkOutline.box(min,max,pose::transformPosition);
        if(!area&&showFacing){
            var center=start.getCenter();
            edges.add(pose.transformPosition(center));
            edges.add(pose.transformPosition(center.add(Vec3.atLowerCornerOf(first.face().getNormal()).scale(2))));
        }
        MarkOutline.send(player,MarkOutline.Kind.MARK,MarkOutline.MARK_COLOR,edges);
    }
    private static void previewPartBreak(ServerPlayer player, HeartBlockEntity heart, VirtualModule module,
                                         List<ModuleAnchor> pendingBreakMarks) {
        if (!pendingBreakMarks.isEmpty()) preview(player, heart, pendingBreakMarks, false);
        else if (!module.invalidated() && ModulePartBreak.validSelection(module, module.anchors()))
            preview(player, heart, module.anchors(), false);
        previewWatchedPart(player, heart, module);
    }

    private static void previewWatchedPart(ServerPlayer player, HeartBlockEntity heart, VirtualModule module) {
        var watchedPart = module.watchedPart();
        if (!module.listensToPart() || watchedPart == null || !watchedPart.status(heart).equals("valid")) return;
        var body = watchedPart.body(heart);
        var bounds = body.getPlot().getBoundingBox();
        if (bounds == null) return;
        // Keep both outlines visible when the break selection touches the edge of the watched part.
        double padding = 0.05;
        var min = new Vec3(bounds.minX() - padding, bounds.minY() - padding, bounds.minZ() - padding);
        var max = new Vec3(bounds.maxX() + 1 + padding, bounds.maxY() + 1 + padding, bounds.maxZ() + 1 + padding);
        MarkOutline.send(player, MarkOutline.Kind.DETAIL, 0x33CCFF,
                MarkOutline.box(min, max, body.logicalPose()::transformPosition));
    }
    private static void previewOutputFace(ServerPlayer player, HeartBlockEntity heart, VirtualModule module, int index) {
        if (ModuleRedstone.validIndex(module, index, false) && module.redstoneFaceStatus(index, heart).equals("valid")) {
            previewRedstoneSurface(player, heart, module.redstoneFace(index), module.value("redstoneFaces") == 0, index + 1);
        }
    }

    private static void previewRedstoneSurface(ServerPlayer player, HeartBlockEntity heart, ModuleAnchor anchor,
                                               boolean allFaces, int number) {
        var body = anchor.body(heart);
        var center = anchor.position(body).getCenter();
        var pose = body.logicalPose();
        var edges = new ArrayList<Vec3>();
        for (var face : Direction.values()) {
            if (!allFaces && face != anchor.face()) continue;
            var normal = Vec3.atLowerCornerOf(face.getNormal());
            var u = (face.getAxis() == Direction.Axis.X ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).scale(.45);
            var v = normal.cross(u);
            var surface = center.add(normal.scale(.505));
            var corners = List.of(surface.subtract(u).subtract(v), surface.add(u).subtract(v),
                    surface.add(u).add(v), surface.subtract(u).add(v));
            for (int i = 0; i < corners.size(); i++) {
                edges.add(pose.transformPosition(corners.get(i)));
                edges.add(pose.transformPosition(corners.get((i + 1) % corners.size())));
            }
            if (number == 0) continue;
            for (int stripe = -2; stripe <= 2; stripe++) {
                if (face == anchor.face() && Math.abs(stripe) < 2) continue;
                double t = stripe / 3.0;
                var from = corners.getFirst().add(u.scale(2 * Math.max(t, 0))).add(v.scale(2 * Math.max(-t, 0)));
                var to = corners.get(2).subtract(u.scale(2 * Math.max(-t, 0))).subtract(v.scale(2 * Math.max(t, 0)));
                edges.add(pose.transformPosition(from));
                edges.add(pose.transformPosition(to));
            }
        }
        MarkOutline.send(player, MarkOutline.Kind.DETAIL, 0x33FF66, edges);
        if (number == 0) return;
        var normal = Vec3.atLowerCornerOf(anchor.face().getNormal());
        var up = anchor.face().getAxis().isVertical() ? new Vec3(0, 0, -1) : new Vec3(0, 1, 0);
        var right = up.cross(normal);
        var surface = center.add(normal.scale(.515));
        var digits = new ArrayList<Vec3>();
        String label = Integer.toString(number);
        double scale = label.length() == 1 ? 1 : .88;
        for (int i = 0; i < label.length(); i++) {
            double offset = (i - (label.length() - 1) * .5) * .22;
            for (var point : FACE_DIGITS.get(label.charAt(i) - '0'))
                digits.add(pose.transformPosition(surface.add(right.scale(offset + point.x * scale)).add(up.scale(point.y * scale))));
        }
        MarkOutline.send(player, MarkOutline.Kind.MARK, MarkOutline.MARK_COLOR, digits);
    }

    // Each pair of coordinates is a stroke on the marked surface.
    private static final List<List<Vec3>> FACE_DIGITS = List.of(
            faceDigit(-1, 2, 1, 2, 1, 2, 1, -2, 1, -2, -1, -2, -1, -2, -1, 2),
            faceDigit(-1, 1, 0, 2, 0, 2, 0, -2, -1, -2, 1, -2),
            faceDigit(-1, 2, 1, 2, 1, 2, 1, 0, 1, 0, -1, 0, -1, 0, -1, -2, -1, -2, 1, -2),
            faceDigit(-1, 2, 1, 2, 1, 2, 1, -2, -1, 0, 1, 0, -1, -2, 1, -2),
            faceDigit(-1, 2, -1, 0, -1, 0, 1, 0, 1, 2, 1, -2),
            faceDigit(1, 2, -1, 2, -1, 2, -1, 0, -1, 0, 1, 0, 1, 0, 1, -2, 1, -2, -1, -2),
            faceDigit(1, 2, -1, 2, -1, 2, -1, -2, -1, -2, 1, -2, 1, -2, 1, 0, 1, 0, -1, 0),
            faceDigit(-1, 2, 1, 2, 1, 2, 0, -2),
            faceDigit(-1, 2, 1, 2, 1, 2, 1, -2, 1, -2, -1, -2, -1, -2, -1, 2, -1, 0, 1, 0),
            faceDigit(1, 0, -1, 0, -1, 0, -1, 2, -1, 2, 1, 2, 1, 2, 1, -2, 1, -2, -1, -2));

    private static List<Vec3> faceDigit(int... coordinates) {
        var result = new ArrayList<Vec3>(coordinates.length / 2);
        for (int i = 0; i < coordinates.length; i += 2)
            result.add(new Vec3(coordinates[i] * .09, coordinates[i + 1] * .09, 0));
        return List.copyOf(result);
    }

    private static void previewModule(ServerPlayer player, HeartBlockEntity heart, VirtualModule module) {
        if (module.type().equals("damage_accumulation")) {
            previewWatchedPart(player, heart, module);
            return;
        }
        if (module.type().equals("part_break")) {
            previewPartBreak(player, heart, module, List.of());
            return;
        }
        if(module.type().equals("block_interaction")){
            if(!module.anchors().isEmpty()&&module.anchors().getFirst().status(heart).equals("valid"))
                ModuleBlockInteraction.preview(player,heart,module);
            return;
        }
        if(List.of("propulsion","motion_damping").contains(module.type())) {
            if(!List.of("valid","disabled").contains(HeartModules.status(module,heart)))return;
            preview(player,heart,List.of(module.anchors().getFirst(),module.anchors().getFirst()));
            if(module.type().equals("propulsion"))ModulePropulsion.preview(player,heart,module);
            else ModuleMotionDamping.preview(player,heart,module);
            return;
        }
        if(module.type().equals("contact_sensor")){ContactSensorGeometry.preview(player,heart,module);return;}
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
        if (module.type().equals("wireless_redstone")) {
            previewOutputFace(player, heart, module, 0);
            return;
        }
        if (module.type().equals("sensor")) {
            if (module.anchors().isEmpty() || !List.of("valid","disabled").contains(HeartModules.status(module,heart)) || !module.anchors().getFirst().status(heart).equals("valid")) return;
            previewRedstoneSurface(player, heart, module.anchors().getFirst(), false, 0);
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
        if (module.type().equals("linear_movement")) {
            if (HeartModules.status(module, heart).equals("valid")) {
                ModuleLinearMovement.preview(player, heart, module);
                return;
            }
            if (ModuleLinearMovement.worldDestination(module)) return;
        }
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
            if(module.type().equals("auto_facing") && module.value("facingSource") == 0)for(int i=0;i<64;i++){double angle=i*Math.PI*2/64;particle(level,player,coreCenter.add(Math.cos(angle)*module.value("facingRange"),0,Math.sin(angle)*module.value("facingRange")));}
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
        sessions.clear();previews.clear();channelPreviews.clear();
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
