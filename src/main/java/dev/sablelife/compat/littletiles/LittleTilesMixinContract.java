package dev.sablelife.compat.littletiles;

import dev.sablelife.compat.ModVersion;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.function.Function;

/** Inspect optional classes as bytes; no LittleTiles or client class is initialized here. */
public final class LittleTilesMixinContract {
    public static final String CACHE = "team/creative/littletiles/client/render/cache/BlockBufferCache";
    public static final String PIPELINE = "team/creative/littletiles/client/render/cache/pipeline/LittleRenderPipelineType";
    private static final String BASE = "team/creative/littletiles/client/render/";
    private static final String BUFFER = "L" + BASE + "cache/buffer/BufferCache;";
    static final String CHUNK_LAYER_MAP = "team/creative/creativecore/common/util/type/map/ChunkLayerMap";
    private static final String MAP = "L" + CHUNK_LAYER_MAP + ";";
    private static final String UPLOAD = "(L" + BASE + "cache/buffer/ChunkBufferUploader;L" + BASE + "cache/buffer/BufferCollection;" + BUFFER + ")" + BUFFER;
    public static final String CACHE_GET_UPLOADED = "getUploaded(Lnet/minecraft/client/renderer/RenderType;)" + BUFFER;
    public static final String CACHE_UPLOAD = "upload(Ljava/util/function/Function;Ljava/util/function/Function;)V";
    public static final String CACHE_SET_EMPTY = "setEmpty()V";
    public static final String CACHE_SET_BUFFERS = "setBuffers(" + MAP + ")V";
    public static final String PIPELINE_COMPILE = "compile(JLteam/creative/littletiles/common/block/entity/BETiles;Ljava/util/function/Function;Ljava/util/function/Function;)V";
    public static final String PIPELINE_UPLOAD_INVOCATION = "L" + PIPELINE + ";upload" + UPLOAD;

    public static boolean supported(String tiles, String core, String sable) {
        String tilesMinimum = tiles.startsWith("1.6.0-pre") ? "1.6.0-pre235" : "1.6.0";
        return ModVersion.atLeast(tiles, tilesMinimum)
                && ModVersion.atLeast(core, "2.13.54") && ModVersion.atLeast(sable, "2.0.5");
    }

    public static boolean inspect(Function<String, byte[]> reader) {
        try {
            var cache = read(CACHE, reader);
            var pipeline = read(PIPELINE, reader);
            var render = read(BASE + "block/BERenderManager", reader);
            var tiles = read("team/creative/littletiles/common/block/entity/BETiles", reader);
            var buffer = read(BASE + "cache/buffer/BufferCache", reader);
            if (cache == null || pipeline == null || render == null || tiles == null || buffer == null) return false;
            var upload = method(cache, CACHE_UPLOAD, false);
            return method(cache, CACHE_GET_UPLOADED, false) != null
                    && method(cache, CACHE_SET_BUFFERS, false) != null
                    && method(cache, CACHE_SET_EMPTY, false) != null
                    && cache.fields.stream().anyMatch(f -> f.name.equals("uploaded") && f.desc.equals(MAP) && (f.access & Opcodes.ACC_STATIC) == 0)
                    && upload != null && Arrays.stream(upload.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call
                            && call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(PIPELINE) && call.name.equals("upload") && call.desc.equals(UPLOAD)).count() == 1
                    && method(pipeline, PIPELINE_COMPILE, true) != null
                    && method(pipeline, "upload" + UPLOAD, true) != null
                    && method(render, "buffers()L" + BASE + "cache/IBlockBufferCache;", false) != null
                    && method(render, "queue(ZZJ)V", false) != null
                    && tiles.fields.stream().anyMatch(f -> f.name.equals("render") && f.desc.equals("L" + BASE + "block/BERenderManager;") && (f.access & Opcodes.ACC_PUBLIC) != 0 && (f.access & Opcodes.ACC_STATIC) == 0)
                    && method(buffer, "isInvalid()Z", false) != null
                    && method(buffer, "isEmpty()Z", false) != null;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    static boolean inspectCreativeCore(byte[] bytes) {
        try {
            var map = read(CHUNK_LAYER_MAP, ignored -> bytes);
            if (map == null || (map.access & Opcodes.ACC_PUBLIC) == 0) return false;
            var get = method(map, "get(Lnet/minecraft/client/renderer/RenderType;)Ljava/lang/Object;", false);
            return get != null && (get.access & Opcodes.ACC_PUBLIC) != 0;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static ClassNode read(String name, Function<String, byte[]> reader) {
        var bytes = reader.apply(name);
        if (bytes == null) return null;
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static MethodNode method(ClassNode node, String selector, boolean isStatic) {
        return node.methods.stream().filter(m -> (m.name + m.desc).equals(selector)
                && (!(node.name.equals(CACHE) || node.name.equals(PIPELINE))
                    || (m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0)
                && ((m.access & Opcodes.ACC_STATIC) != 0) == isStatic).findFirst().orElse(null);
    }

    private LittleTilesMixinContract() {}
}
