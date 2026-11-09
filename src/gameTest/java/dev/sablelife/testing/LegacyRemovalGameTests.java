package dev.sablelife.testing;

import dev.sablelife.SableLife;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("sablelife_cleanup_tests")
@PrefixGameTestTemplate(false)
public final class LegacyRemovalGameTests {
    @GameTest(template="combat",batch="cleanup_registry")
    public static void OnlyCurrentCoreBlocksRemainRegistered(GameTestHelper h){
        for(String id:List.of("melee_module","flame_module","arrow_module","pulse_module","target_sensor","sound_module","part_marker")){
            var key=ResourceLocation.fromNamespaceAndPath("sablelife",id);
            h.assertTrue(!BuiltInRegistries.BLOCK.containsKey(key)&&!BuiltInRegistries.ITEM.containsKey(key)&&!BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(key),"Removed physical registration remains: "+id);
        }
        h.assertTrue(BuiltInRegistries.BLOCK.stream().filter(b->BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals("sablelife")).count()==3
                &&BuiltInRegistries.BLOCK.getKey(SableLife.SURVIVAL_LIFE_CORE.get()).getNamespace().equals("sablelife"),"Current three cores removed or extra blocks remain");
        h.assertTrue(!BuiltInRegistries.MENU.containsKey(ResourceLocation.fromNamespaceAndPath("sablelife","config_chest"))&&BuiltInRegistries.MENU.containsKey(ResourceLocation.fromNamespaceAndPath("sablelife","loot_chest")),"Removed menu remains or current loot menu missing");h.succeed();
    }
    @GameTest(template="combat",batch="cleanup_core_save")
    public static void CurrentCoresAndEveryAvailableVirtualModuleStillReload(GameTestHelper h){
        for(boolean control:List.of(false,true)){
            var pos=h.absolutePos(new BlockPos(control?5:2,6,2));var state=(control?SableLife.REDSTONE_CORE.get():SableLife.HEART.get()).defaultBlockState();h.getLevel().setBlockAndUpdate(pos,state);
            var core=(HeartBlockEntity)h.getLevel().getBlockEntity(pos);
            for(var type:control?RedstoneCorePolicy.TYPES:VirtualModule.AVAILABLE_TYPES){var m=core.modules().add(type);if(control)RedstoneCorePolicy.constrainMovement(m);m.edit("name",0,"Saved "+type);}
            var before=core.modules().save();var normalized=new HeartModules();normalized.load(before);var tag=core.saveWithFullMetadata(h.getLevel().registryAccess());
            var reloaded=(HeartBlockEntity)BlockEntity.loadStatic(pos,state,tag,h.getLevel().registryAccess());
            // Compare against normal module loading: existing schema/default migrations need not be byte-identical.
            h.assertTrue(reloaded!=null&&reloaded.isControlCore()==control&&reloaded.modules().save().equals(normalized.save()),"Core load lost or changed virtual modules beyond normal schema loading");
            h.assertTrue(reloaded.modules().all().size()==core.modules().all().size(),"Available virtual module lost on reload");
            for(var m:core.modules().all())h.assertTrue(reloaded.modules().find(m.id())!=null&&reloaded.modules().find(m.id()).name().equals(m.name()),"Module identity/name lost: "+m.type());
            h.assertTrue(tag.getString("id").equals("sablelife:heart"),"Current core BE registry identity changed");
        }h.succeed();
    }
}
