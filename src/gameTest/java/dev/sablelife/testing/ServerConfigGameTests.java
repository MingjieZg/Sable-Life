package dev.sablelife.testing;

import dev.sablelife.ServerConfigMigration;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.ServerConfigSpacing;

import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class ServerConfigGameTests {
    @GameTest(template="combat",batch="server_config_spacing")
    public static void SpacingPreservesValuesAndDoesNotGrowOnReload(GameTestHelper h){
        String source="# First\nfirst = 1\n# Second\n# Range\nsecond = 2\n[modules]\narmor = true\nlaser = false\n";
        String expected="# First\nfirst = 1\n\n# Second\n# Range\nsecond = 2\n\n[modules]\narmor = true\n\nlaser = false\n";
        String formatted=ServerConfigSpacing.format(source);
        h.assertTrue(formatted.equals(expected),"Settings were not separated from their comment groups");
        h.assertTrue(ServerConfigSpacing.format(formatted).equals(formatted),"Reload inserted more blank lines");
        String compact="[modules.heartCore]\narrow = true\n\nlaser = false\n\n[survivalTokens]\nrate = 1\n";
        String compacted=ServerConfigSpacing.format(compact);
        h.assertTrue(compacted.contains("arrow = true\nlaser = false"),"Module switches were not compacted");
        h.assertTrue(ServerConfigSpacing.format(compacted).equals(compacted),"Module formatting was not idempotent");
        String array="values = [\r\n  \"minecraft:iron_ingot=30\"\r\n]\r\n# Next\r\nrate = 1\r\n";
        h.assertTrue(ServerConfigSpacing.format(array).contains("]\r\n\r\n# Next"),"Array or line ending spacing failed");
        String literal="text = \"\"\"\nfirst = 1\n# This belongs to the string\n\"\"\"\nvalue = 2\n";
        h.assertTrue(ServerConfigSpacing.format(literal).equals(literal),"Formatting altered a multiline string value");
        h.succeed();
    }

    @GameTest(template="combat",batch="server_config_migration")
    public static void RenamingPreservesOldSwitchesAndHonorsNewNames(GameTestHelper h){
        var config=new TomlParser().parse("allowSurvivalLifeCoreEditing = true\n[modules.lifeCore]\nlaser = false\n[modules.survivalLifeCore]\narmor = false\n");
        ServerConfigMigration.migrate(config);
        h.assertTrue(Boolean.TRUE.equals(config.get("allowEditHeartCoreUnderSurvivalMode")),"Legacy editing permission was reset");
        h.assertTrue(Boolean.FALSE.equals(config.get("modules.heartCore.laser"))&&Boolean.FALSE.equals(config.get("modules.survivalHeartCore.armor")),"Legacy disabled modules were reset");
        h.assertTrue(!config.contains("modules.lifeCore")&&!config.contains("allowSurvivalLifeCoreEditing"),"Legacy names were retained");
        var defaultCatalog=new TomlParser().parse("allowSurvivalLifeCoreEditing = false\n[survivalTokens]\ndepositValues = []\n");
        ServerConfigMigration.migrate(defaultCatalog);
        h.assertTrue(SableLifeServerConfig.DEFAULT_TOKEN_VALUES.equals(defaultCatalog.get("survivalTokens.depositValues")),
                "Legacy empty default catalog was not upgraded");
        var previousCatalog=new TomlParser().parse("[survivalTokens]\ndepositValues = []\n");
        previousCatalog.set("survivalTokens.depositValues",java.util.List.of("minecraft:iron_ingot=30","minecraft:gold_ingot=60",
                "minecraft:diamond=300","minecraft:netherite_ingot=3000","minecraft:copper_block=90","minecraft:iron_block=270"));
        ServerConfigMigration.migrate(previousCatalog);
        h.assertTrue(SableLifeServerConfig.DEFAULT_TOKEN_VALUES.equals(previousCatalog.get("survivalTokens.depositValues")),
                "The 0.1.325 default catalog did not gain the remaining material blocks");
        var preEmerald=new TomlParser().parse("[survivalTokens]\ndepositValues = []\n");
        preEmerald.set("survivalTokens.depositValues",java.util.List.of("minecraft:copper_ingot=10","minecraft:iron_ingot=30",
                "minecraft:gold_ingot=60","minecraft:diamond=300","minecraft:netherite_ingot=3000","minecraft:copper_block=90",
                "minecraft:iron_block=270","minecraft:gold_block=540","minecraft:diamond_block=2700","minecraft:netherite_block=27000"));
        ServerConfigMigration.migrate(preEmerald);
        h.assertTrue(SableLifeServerConfig.DEFAULT_TOKEN_VALUES.equals(preEmerald.get("survivalTokens.depositValues")),
                "Existing ten-item default catalog did not gain emeralds");
        ServerConfigMigration.migrate(preEmerald);
        h.assertTrue(SableLifeServerConfig.DEFAULT_TOKEN_VALUES.equals(preEmerald.get("survivalTokens.depositValues")),
                "New fuel defaults changed on repeated migration");
        var customEmerald=new TomlParser().parse("[survivalTokens]\ndepositValues = [\"minecraft:emerald=777\"]\n");
        ServerConfigMigration.migrate(customEmerald);
        h.assertTrue(java.util.List.of("minecraft:emerald=777").equals(customEmerald.get("survivalTokens.depositValues")),
                "Administrator emerald value was overwritten");
        var customCatalog=new TomlParser().parse("allowSurvivalLifeCoreEditing = false\n[survivalTokens]\ndepositValues = [\"minecraft:diamond=999\"]\n");
        ServerConfigMigration.migrate(customCatalog);
        h.assertTrue(java.util.List.of("minecraft:diamond=999").equals(customCatalog.get("survivalTokens.depositValues")),
                "Custom material values were overwritten");
        var unified=new TomlParser().parse("[survivalTokens]\ndepositItem = \"minecraft:gold_ingot\"\ntokensPerItem = 123\ndepositValues = [\"minecraft:diamond=999\"]\n");
        ServerConfigMigration.migrate(unified);
        h.assertTrue(java.util.List.of("minecraft:gold_ingot=123","minecraft:diamond=999").equals(unified.get("survivalTokens.depositValues")),
                "Legacy fuel item or its custom value was lost");
        h.assertTrue(!unified.contains("survivalTokens.depositItem")&&!unified.contains("survivalTokens.tokensPerItem"),
                "Legacy single-item settings remained");
        var duplicate=new TomlParser().parse("[survivalTokens]\ndepositItem = \"minecraft:gold_ingot\"\ntokensPerItem = 123\ndepositValues = [\"minecraft:gold_ingot=456\"]\n");
        ServerConfigMigration.migrate(duplicate);
        h.assertTrue(java.util.List.of("minecraft:gold_ingot=456").equals(duplicate.get("survivalTokens.depositValues")),
                "Legacy fuel overrode an explicit list value");
        ServerConfigMigration.migrate(unified);
        h.assertTrue(java.util.List.of("minecraft:gold_ingot=123","minecraft:diamond=999").equals(unified.get("survivalTokens.depositValues")),
                "Fuel migration was not idempotent");
        var mixed=new TomlParser().parse("allowSurvivalLifeCoreEditing = true\nallowEditHeartCoreUnderSurvivalMode = false\n[modules.lifeCore]\nlaser = false\narrow = false\n[modules.heartCore]\nlaser = true\n");
        ServerConfigMigration.migrate(mixed);
        ServerConfigMigration.migrate(mixed);
        h.assertTrue(Boolean.FALSE.equals(mixed.get("allowEditHeartCoreUnderSurvivalMode"))&&Boolean.TRUE.equals(mixed.get("modules.heartCore.laser")),"Legacy values overrode explicit new names");
        h.assertTrue(Boolean.FALSE.equals(mixed.get("modules.heartCore.arrow"))&&!mixed.contains("modules.lifeCore"),"Partial migration lost a legacy switch");
        h.succeed();
    }
}
