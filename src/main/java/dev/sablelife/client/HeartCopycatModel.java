package dev.sablelife.client;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.copycat.CopycatModel;
import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.List;

/** Camouflage replaces the entire casing and rim; only the crystal stays visible. */
@EventBusSubscriber(modid = SableLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HeartCopycatModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<ModelData> MATERIAL_DATA = new ModelProperty<>();
    private static final ChunkRenderTypeSet CRYSTAL_LAYER = ChunkRenderTypeSet.of(RenderType.cutoutMipped());

    public HeartCopycatModel(BakedModel original) { super(original); }

    @SubscribeEvent public static void bake(ModelEvent.ModifyBakingResult event) {
        for(var block:java.util.List.of(SableLife.HEART.get(),SableLife.REDSTONE_CORE.get(),SableLife.SURVIVAL_LIFE_CORE.get()))for (var state : block.getStateDefinition().getPossibleStates()) {
            var key = BlockModelShaper.stateToModelLocation(state);
            event.getModels().computeIfPresent(key, (id, model) -> new HeartCopycatModel(model));
        }
    }

    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Block event) {
        event.register(HeartBlock.wrappedColor(), SableLife.HEART.get(),SableLife.REDSTONE_CORE.get(),SableLife.SURVIVAL_LIFE_CORE.get());
    }

    private static BlockState material(ModelData data) {
        var state = data.get(CopycatModel.MATERIAL_PROPERTY);
        return state == null || AllBlocks.COPYCAT_BASE.has(state) || state.getBlock() instanceof HeartBlock ? null : state;
    }

    private static BakedModel model(BlockState state) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
    }

    @Override public ModelData getModelData(BlockAndTintGetter world, BlockPos pos, BlockState state, ModelData data) {
        var material = material(data);
        return material == null ? data : data.derive().with(MATERIAL_DATA,
                model(material).getModelData(world, pos, material, ModelData.EMPTY)).build();
    }

    private static ModelData materialData(ModelData data) {
        var result = data.get(MATERIAL_DATA);
        return result == null ? ModelData.EMPTY : result;
    }

    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        var material = material(data);
        return material == null ? CRYSTAL_LAYER : ChunkRenderTypeSet.union(CRYSTAL_LAYER,
                model(material).getRenderTypes(material, random, materialData(data)));
    }

    @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType layer) {
        var material = material(data);
        if (material == null) return originalModel.getQuads(state, side, random, data, layer);
        var result = new ArrayList<BakedQuad>();
        if (layer == null || layer == RenderType.cutoutMipped()) {
            for (var quad : originalModel.getQuads(state, side, random)) {
                var sprite = quad.getSprite().contents().name();
                if (sprite.getNamespace().equals(SableLife.MOD_ID)
                        && (sprite.getPath().equals("block/heart_crystal")||sprite.getPath().equals("block/redstone_crystal"))) result.add(quad);
            }
        }
        var donor = model(material);
        if (layer == null || donor.getRenderTypes(material, random, materialData(data)).contains(layer))
            result.addAll(donor.getQuads(material, side, random, materialData(data), layer));
        return result;
    }

    @Override public TextureAtlasSprite getParticleIcon(ModelData data) {
        var material = material(data);
        return material == null ? originalModel.getParticleIcon(data) : model(material).getParticleIcon(materialData(data));
    }
}
