package dev.sablelife.testing;

import dev.sablelife.SableLife;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_release_candidate_tests") @PrefixGameTestTemplate(false)
public final class ReleaseCandidateGameTests {
    @GameTest(template="combat")
    public static void SynchronizedServerConfigurationHasNoFileToFormat(GameTestHelper h) throws Exception {
        var container=net.neoforged.fml.ModList.get().getModContainerById("sablelife").orElseThrow();
        var constructor=net.neoforged.fml.config.ModConfig.class.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var config=(net.neoforged.fml.config.ModConfig)constructor.newInstance(net.neoforged.fml.config.ModConfig.Type.SERVER,
                dev.sablelife.SableLifeServerConfig.LOADER_SPEC,container,"sablelife-server.toml",new java.util.concurrent.locks.ReentrantLock());
        // An unloaded or remotely synchronized configuration has no backing Path.
        boolean hasNoPath=false;try{config.getFullPath();}catch(IllegalStateException expected){hasNoPath=true;}
        h.assertTrue(hasNoPath,"Fixture unexpectedly has a local file");
        dev.sablelife.ServerConfigSpacing.reloaded(new net.neoforged.fml.event.config.ModConfigEvent.Reloading(config));
        h.succeed();
    }
    @GameTest(template="combat")
    public static void CandidateClassesActuallyComeFromFrozenJar(GameTestHelper h) {
        var expected=System.getProperty("sablelife.expectedReleaseJar","");
        var location=SableLife.class.getProtectionDomain().getCodeSource().getLocation().toString();
        System.out.println("RELEASE_ARTIFACT_ORIGIN: "+location);
        h.assertTrue(!expected.isBlank()&&location.contains(expected),"Production classes were not loaded from the requested JAR: "+location);
        h.succeed();
    }

    @GameTest(template="combat")
    public static void NewRecipesWorkInEveryTwoByTwoArrangement(GameTestHelper h) {
        int checked=0;
        for(var material:List.of(Items.PAPER,Items.SHEARS)) {
            var expected=material==Items.PAPER?SableLife.DATA_COPIER.get():SableLife.SURVIVAL_RELEASE_TOOL.get();
            for(int first=0;first<4;first++) for(int second=0;second<4;second++) {
                if(first==second)continue;
                var slots=new ArrayList<ItemStack>(List.of(ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY));
                slots.set(first,new ItemStack(material));slots.set(second,new ItemStack(Items.REDSTONE));
                var input=CraftingInput.of(2,2,slots);
                var recipe=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel());
                h.assertTrue(recipe.isPresent(),"Missing recipe at "+first+","+second);
                var output=recipe.orElseThrow().value().assemble(input,h.getLevel().registryAccess());
                h.assertTrue(output.is(expected)&&output.getCount()==1,"Wrong recipe output: "+output);
                checked++;
            }
        }
        System.out.println("RELEASE_CRAFTING_ARRANGEMENTS: "+checked);
        h.succeed();
    }
}
