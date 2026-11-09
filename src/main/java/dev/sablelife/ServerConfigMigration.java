package dev.sablelife;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Move legacy names before NeoForge removes unknown configuration keys. */
public record ServerConfigMigration(ModConfigSpec delegate) implements IConfigSpec {
    // Freeze historical catalogs: inserting new defaults must not change their identities.
    private static final java.util.List<String> PRE_EMERALD_DEFAULTS=java.util.List.of(
            "minecraft:copper_ingot=10","minecraft:iron_ingot=30","minecraft:gold_ingot=60",
            "minecraft:diamond=300","minecraft:netherite_ingot=3000","minecraft:copper_block=90",
            "minecraft:iron_block=270","minecraft:gold_block=540","minecraft:diamond_block=2700",
            "minecraft:netherite_block=27000");
    private static final String[][] RENAMES={
            {"allowSurvivalLifeCoreEditing","allowEditHeartCoreUnderSurvivalMode"},
            {"modules.lifeCore","modules.heartCore"},
            {"modules.survivalLifeCore","modules.survivalHeartCore"}
    };

    @Override public boolean isEmpty(){return delegate.isEmpty();}
    @Override public void validateSpec(ModConfig config){delegate.validateSpec(config);}
    @Override public boolean isCorrect(UnmodifiableCommentedConfig config){
        for(var rename:RENAMES)if(config.contains(rename[0]))return false;
        if(config.contains("survivalTokens.depositItem")||config.contains("survivalTokens.tokensPerItem")
                ||previousMaterialDefaults(config.get("survivalTokens.depositValues")))return false;
        return delegate.isCorrect(config);
    }
    @Override public void correct(CommentedConfig config){migrate(config);delegate.correct(config);}
    @Override public void acceptConfig(ILoadedConfig config){delegate.acceptConfig(config);}

    private static boolean previousMaterialDefaults(Object values){
        // Previous catalogs kept copper in a separate setting.
        return PRE_EMERALD_DEFAULTS.subList(1,7).equals(values)
                ||PRE_EMERALD_DEFAULTS.subList(1,PRE_EMERALD_DEFAULTS.size()).equals(values)
                ||PRE_EMERALD_DEFAULTS.equals(values);
    }

    private static void migrateFuel(CommentedConfig config,boolean legacyNames){
        Object saved=config.get("survivalTokens.depositValues");
        boolean upgrade=previousMaterialDefaults(saved)||legacyNames&&saved instanceof java.util.List<?> list&&list.isEmpty();
        boolean single=config.contains("survivalTokens.depositItem")||config.contains("survivalTokens.tokensPerItem");
        if(!upgrade&&!single)return;
        var values=new java.util.ArrayList<Object>();
        if(upgrade||!(saved instanceof java.util.List<?>))values.addAll(SableLifeServerConfig.DEFAULT_TOKEN_VALUES);
        else values.addAll((java.util.List<?>)saved);
        if(single){
            Object item=config.get("survivalTokens.depositItem");
            String id=item instanceof String text?text:"minecraft:copper_ingot";
            Object price=config.get("survivalTokens.tokensPerItem");
            long rate=price instanceof Number number?number.longValue():10;
            boolean explicit=saved instanceof java.util.List<?> list
                    &&list.stream().anyMatch(entry->entry instanceof String text&&text.startsWith(id+"="));
            if(!explicit&&id.matches("[a-z0-9_.-]+:[a-z0-9/_.-]+")&&rate>0&&rate<=1000000){
                values.removeIf(entry->entry instanceof String text&&text.startsWith(id+"="));
                values.addFirst(id+"="+rate);
            }
            config.remove("survivalTokens.depositItem");
            config.remove("survivalTokens.tokensPerItem");
        }
        config.set("survivalTokens.depositValues",values);
    }

    public static void migrate(CommentedConfig config){
        boolean legacy=false;
        for(var rename:RENAMES)legacy|=config.contains(rename[0]);
        migrateFuel(config,legacy);
        for(var rename:RENAMES){
            if(!config.contains(rename[0]))continue;
            Object old=config.get(rename[0]);
            Object current=config.get(rename[1]);
            if(current==null)config.set(rename[1],old);
            else if(old instanceof CommentedConfig previous&&current instanceof CommentedConfig target){
                for(var entry:previous.entrySet())
                    if(!target.contains(java.util.List.of(entry.getKey())))
                        target.set(java.util.List.of(entry.getKey()),entry.getValue());
            }
            config.remove(rename[0]);
        }
    }
}
