package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.attack.*;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class CreatureDeathMessageGameTests {
    private static HeartBlockEntity core(GameTestHelper h){var p=h.absolutePos(new BlockPos(3,5,3));h.getLevel().setBlockAndUpdate(p,SableLife.HEART.get().defaultBlockState());return (HeartBlockEntity)h.getLevel().getBlockEntity(p);}
    private static Component killer(GameTestHelper h,Component message){h.assertTrue(message.getContents() instanceof TranslatableContents,"Death message was not translatable");var t=(TranslatableContents)message.getContents();h.assertTrue(t.getKey().equals("death.attack.sablelife.creature")&&t.getArgs().length==2,"Wrong death message key or arguments");return (Component)t.getArgs()[1];}
    @GameTest(template="combat",batch="creature_name_styles")
    public static void CoreNameFormatsColorsBoldResetHexAndPersistsRawInput(GameTestHelper h){
        var heart=core(h);var author=TestActors.creative(h.getLevel());h.assertTrue(heart.editCore(author,"name",0,"&c&lKilling Machine&r Plain"),"Colored name was rejected");
        var name=heart.displayName();var styles=new ArrayList<Style>();name.visit((style,text)->{if(!text.isEmpty())styles.add(style);return Optional.empty();},Style.EMPTY);
        h.assertTrue(name.getString().equals("Killing Machine Plain")&&styles.getFirst().isBold()&&styles.getFirst().getColor().getValue()==0xFF5555&&!styles.getLast().isBold()&&styles.getLast().getColor()==null,"Color, bold or reset parsing failed");
        var loaded=new HeartBlockEntity(heart.getBlockPos(),heart.getBlockState());loaded.loadWithComponents(heart.saveWithoutMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(loaded.creatureName().equals("&c&lKilling Machine&r Plain")&&loaded.displayName().equals(name),"Formatted name failed save/load");
        var hex=CreatureNames.formatted("&#12AB34&lHex");var hexStyles=new ArrayList<Style>();hex.visit((style,text)->{if(!text.isEmpty())hexStyles.add(style);return Optional.empty();},Style.EMPTY);
        h.assertTrue(hex.getString().equals("Hex")&&hexStyles.getFirst().getColor().getValue()==0x12AB34&&hexStyles.getFirst().isBold(),"Hex color failed");
        h.assertTrue(CreatureNames.formatted("AT&T &z §aGreen").getString().equals("AT&T &z Green"),"Invalid codes swallowed ordinary characters");
        h.assertTrue(!heart.editCore(author,"name",0,"bad\nname")&&!heart.editCore(author,"name",0,"x".repeat(65)),"Name validation regressed");h.succeed();
    }
    @GameTest(template="combat",batch="creature_named_damage")
    public static void NamedSourcesPreserveDamageSemanticsAndCombatTrackerAttribution(GameTestHelper h){
        var heart=core(h);heart.editCore(TestActors.creative(h.getLevel()),"name",0,"&c&lKilling Machine");
        var victim=new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE,h.getLevel()){
            Component obituary;
            @Override public void die(net.minecraft.world.damagesource.DamageSource source){obituary=getCombatTracker().getDeathMessage();super.die(source);}
        };victim.setCustomName(Component.literal("Dev"));var original=h.getLevel().damageSources().genericKill();var named=ModuleDamage.named(original,heart);
        h.assertTrue(named.typeHolder().equals(original.typeHolder())&&named.getEntity()==original.getEntity()&&named.getDirectEntity()==original.getDirectEntity()&&Objects.equals(named.sourcePositionRaw(),original.sourcePositionRaw()),"Attribution changed damage mechanics");
        h.assertTrue(killer(h,named.getLocalizedDeathMessage(victim)).getString().equals("Killing Machine"),"Named kill failed");
        ModuleDamage.hurt(victim,named,Float.MAX_VALUE);h.assertTrue(!victim.isAlive()&&killer(h,victim.obituary).getString().equals("Killing Machine"),"Real lethal damage did not retain named source in combat tracker");h.succeed();
    }
    @GameTest(template="combat",batch="creature_arrow_name")
    public static void NativeArrowKeepsLaunchNameAfterCoreRenameAndProjectileReload(GameTestHelper h){
        var heart=core(h);heart.editCore(TestActors.creative(h.getLevel()),"name",0,"&5&lKilling Machine");var arrow=new Arrow(EntityType.ARROW,h.getLevel());ModuleDamage.nameProjectile(arrow,heart);
        var tag=arrow.saveWithoutId(new net.minecraft.nbt.CompoundTag());var loaded=new Arrow(EntityType.ARROW,h.getLevel());loaded.load(tag);
        heart.editCore(TestActors.creative(h.getLevel()),"name",0,"Later name");var victim=EntityType.ZOMBIE.create(h.getLevel());var source=h.getLevel().damageSources().arrow(loaded,null);
        h.assertTrue(killer(h,source.getLocalizedDeathMessage(victim)).equals(CreatureNames.formatted("&5&lKilling Machine")),"Native arrow lost styled launch name or projectile persistence");
        var ordinary=new Arrow(EntityType.ARROW,h.getLevel());var plain=h.getLevel().damageSources().arrow(ordinary,null).getLocalizedDeathMessage(victim);
        h.assertTrue(!((TranslatableContents)plain.getContents()).getKey().equals("death.attack.sablelife.creature"),"Other arrows acquired boss attribution");h.succeed();
    }
    @GameTest(template="combat",batch="creature_default_name")
    public static void UnnamedCoreAndArrowUseBlockWhileBossBarRetainsNormalDefault(GameTestHelper h){
        var heart=core(h);var victim=EntityType.ZOMBIE.create(h.getLevel());h.assertTrue(killer(h,ModuleDamage.named(ModuleDamage.physical(h.getLevel()),heart).getLocalizedDeathMessage(victim)).getString().equals("block"),"Unnamed direct kill did not use block");
        var arrow=new Arrow(EntityType.ARROW,h.getLevel());ModuleDamage.nameProjectile(arrow,heart);h.assertTrue(killer(h,h.getLevel().damageSources().arrow(arrow,null).getLocalizedDeathMessage(victim)).getString().equals("block"),"Unnamed arrow kill did not use block");
        h.assertTrue(!heart.displayName().getString().equals("block")&&CreatureNames.killer("&c&l").getString().equals("block"),"Default display name or formatting-only fallback regressed");h.succeed();
    }
}
