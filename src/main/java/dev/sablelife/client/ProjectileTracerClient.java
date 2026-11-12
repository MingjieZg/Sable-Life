package dev.sablelife.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelife.SableLife;
import dev.sablelife.module.ProjectileTracer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;

@EventBusSubscriber(modid = SableLife.MOD_ID, value = Dist.CLIENT)
public final class ProjectileTracerClient {
    private static final int MAX_TRACERS = 512;
    private static final int MAX_POINTS = 64;
    private static final int FADE_TICKS = 3;
    private static final LinkedHashMap<Integer, Trail> trails = new LinkedHashMap<>();
    private static ClientLevel world;

    private static final class Trail {
        final ArrayDeque<Vec3> points = new ArrayDeque<>();
        ProjectileTracer.Segment segment;
        long updated;

        void append(ProjectileTracer.Segment next, long now) {
            if (segment != null && (now - updated > 2 || segment.end().distanceToSqr(next.start()) > .01)) points.clear();
            points.addLast(next.start());
            if (points.size() > MAX_POINTS) points.removeFirst();
            segment = next;
            updated = now;
        }
    }

    public static void receive(ProjectileTracer.Segment segment) {
        syncWorld();
        if (world == null || !segment.valid()) return;
        var trail = trails.remove(segment.projectile());
        if (trail == null) trail = new Trail();
        trail.append(segment, world.getGameTime());
        trails.put(segment.projectile(), trail);
        if (trails.size() > MAX_TRACERS) trails.pollFirstEntry();
    }

    private static void syncWorld() {
        var level = Minecraft.getInstance().level;
        if (level == world) return;
        trails.clear();
        world = level;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        syncWorld();
        if (world != null) trails.values().removeIf(trail -> world.getGameTime() - trail.updated > FADE_TICKS + 1);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || trails.isEmpty() || world != Minecraft.getInstance().level) return;
        var camera = event.getCamera().getPosition();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var type = RenderType.debugQuads();
        var vertices = buffers.getBuffer(type);
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (var trail : trails.values()) {
            double age = world.getGameTime() - trail.updated + partial;
            if (age < FADE_TICKS + 1) draw(event.getPoseStack(), vertices, camera, trail, age);
        }
        buffers.endBatch(type);
    }

    private static void draw(PoseStack pose, VertexConsumer vertices, Vec3 camera, Trail trail, double age) {
        var segment = trail.segment;
        var style = segment.tracer();
        var head = segment.start().lerp(segment.end(), Math.clamp(age, 0, 1));
        var near = head;
        double alpha = 1 - Math.clamp((age - 1) / FADE_TICKS, 0, 1);
        double length = style.length();
        double distance = 0;
        var iterator = trail.points.descendingIterator();
        while (iterator.hasNext() && distance < length) {
            var next = iterator.next();
            double step = near.distanceTo(next);
            if (step < 1e-5) continue;
            double farDistance = Math.min(distance + step, length);
            var far = near.lerp(next, (farDistance - distance) / step);
            drawSegment(pose, vertices, near.subtract(camera), far.subtract(camera), style,
                    1 - distance / length, 1 - farDistance / length, alpha);
            near = far;
            distance = farDistance;
        }
    }

    private static void drawSegment(PoseStack pose, VertexConsumer vertices, Vec3 near, Vec3 far,
                                    ProjectileTracer style, double nearFade, double farFade, double alpha) {
        var axis = near.subtract(far).normalize();
        var side = axis.cross(near.add(far)).normalize();
        if (side.lengthSqr() < 1e-8)
            side = axis.cross(Math.abs(axis.y) > .9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        var nearWidth = side.scale(style.width() * .5 * nearFade);
        var farWidth = side.scale(style.width() * .5 * farFade);
        int color = style.color();
        quad(pose, vertices, near, far, nearWidth, farWidth, color, alpha * 100 * nearFade, alpha * 100 * farFade);
        int core = ((color & 0xFEFEFE) >>> 1) + 0x777777;
        quad(pose, vertices, near, far, nearWidth.scale(.4), farWidth.scale(.4), core,
                alpha * 245 * nearFade, alpha * 245 * farFade);
    }

    private static void quad(PoseStack pose, VertexConsumer vertices, Vec3 near, Vec3 far,
                             Vec3 nearWidth, Vec3 farWidth, int color, double nearAlpha, double farAlpha) {
        vertex(pose, vertices, near.add(nearWidth), color, nearAlpha);
        vertex(pose, vertices, near.subtract(nearWidth), color, nearAlpha);
        vertex(pose, vertices, far.subtract(farWidth), color, farAlpha);
        vertex(pose, vertices, far.add(farWidth), color, farAlpha);
    }

    private static void vertex(PoseStack pose, VertexConsumer vertices, Vec3 point, int color, double alpha) {
        vertices.addVertex(pose.last().pose(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(color >> 16 & 255, color >> 8 & 255, color & 255, (int) alpha);
    }

    private ProjectileTracerClient() {}
}
