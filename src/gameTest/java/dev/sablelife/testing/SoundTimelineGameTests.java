package dev.sablelife.testing;
import dev.sablelife.SableLife;
import dev.sablelife.sound.*;
import dev.sablelife.module.*;
import dev.sablelife.heart.*;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;
@GameTestHolder("sablelife_sound_tests")
@PrefixGameTestTemplate(false)
public final class SoundTimelineGameTests {
    private static HeartBlockEntity fixture(GameTestHelper h){var level=h.getLevel();var pos=h.absolutePos(new BlockPos(4,6,4));level.setBlockAndUpdate(pos,SableLife.HEART.get().defaultBlockState());level.setBlockAndUpdate(pos.east(),Blocks.IRON_BLOCK.defaultBlockState());var body=SubLevelAssemblyHelper.assembleBlocks(level,pos,List.of(pos,pos.east()),new BoundingBox3i(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY(),pos.getZ()));return (HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());}
    @GameTest(template="combat")
    public static void LayeredRowsAllowDuplicateSoundsAndPersistIndependentTiming(GameTestHelper h){
        if(Boolean.getBoolean("sablelife.localGameTests"))h.assertTrue(Math.abs(h.absolutePos(BlockPos.ZERO).getX())<1000&&Math.abs(h.absolutePos(BlockPos.ZERO).getZ())<1000,"Physics test origin did not use local coordinates");
        var t=SoundTimeline.legacy("fireSound","minecraft:block.note_block.bass",.7,1.3,48);var first=t.rows().getFirst();h.assertTrue(!t.authored()&&first.start()==0&&first.duration()==0&&Math.abs(first.pitch()-1.3)<.0001,"Legacy defaults changed");
        h.assertTrue(t.edit("add",null,0,first.sound()),"Could not layer identical sound");var second=t.rows().getLast();t.edit("start",second.id(),.35,"");t.edit("pitch",second.id(),.8,"");t.edit("volume",second.id(),2,"");t.edit("radius",second.id(),64,"");t.edit("duration",second.id(),1.5,"");t.edit("copy",second.id(),0,"");
        var restored=SoundTimeline.load(t.save());h.assertTrue(restored.authored()&&restored.rows().equals(t.rows())&&restored.rows().get(1).start()==7&&restored.rows().get(1).duration()==30,"Layer settings or natural/cutoff state failed to persist");h.assertTrue(!restored.rows().get(1).id().equals(restored.rows().get(2).id()),"Copy reused row identity");
        h.assertTrue(!t.edit("start",first.id(),-1,"")&&!t.edit("pitch",first.id(),3,"")&&!t.edit("volume",first.id(),Double.NaN,"")&&!t.edit("add",null,0,"not a sound"),"Invalid timeline edits accepted");
        while(t.rows().size()<SoundTimeline.LIMIT)t.edit("add",null,0,first.sound());h.assertTrue(!t.edit("add",null,0,first.sound()),"Unbounded sound layer count");h.succeed();}
    @GameTest(template="combat")
    public static void ModuleAndCoreProgramsSurviveSaveWithoutMutingEmptyLegacyFields(GameTestHelper h){
        var module=new VirtualModule("sound");var program=module.soundTimeline("ambient");h.assertTrue(!program.audible(),"Ambient default unexpectedly played");program.edit("add",null,0,"minecraft:entity.ravager.roar");var row=program.rows().getFirst();program.edit("start",row.id(),1.2,"");module.soundTimeline("ambient",program);
        var restored=VirtualModule.load(module.save());h.assertTrue(restored.text("ambient").isEmpty()&&restored.hasSound("ambient")&&restored.soundTimeline("ambient").rows().equals(program.rows()),"Empty legacy field muted layered program");
        var homing=new VirtualModule("homing");homing.soundTimeline("blastSound",program);var blast=dev.sablelife.attack.ModuleBlast.settings(homing);
        h.assertTrue(blast.getUUID("SoundOwner").equals(homing.id())&&SoundTimeline.load(blast.getCompound("SoundTimeline")).rows().equals(program.rows()),"Projectile blast lost sound owner or program snapshot");
        var presentation=new DeathPresentation();var timeline=presentation.soundTimeline("deathSound");timeline.edit("add",null,0,"minecraft:block.note_block.bass");presentation.soundTimeline("deathSound",timeline);var loaded=new DeathPresentation();loaded.load(presentation.save());h.assertTrue(loaded.soundTimeline("deathSound").rows().equals(timeline.rows()),"Core program was erased during legacy migration");
        var removal=new DeathPresentation(true);h.assertTrue(!removal.soundTimeline("deathSound").audible(),"Removal default changed");removal.soundTimeline("deathSound",program);var removalSaved=new DeathPresentation(true);removalSaved.load(removal.save());h.assertTrue(removalSaved.soundTimeline("deathSound").rows().equals(program.rows()),"Removal program failed save/load");h.succeed();}
    @GameTest(template="combat",timeoutTicks=30)
    public static void EditorValidatesRevisionPermissionsAndStageKeys(GameTestHelper h){var heart=fixture(h);var module=heart.modules().add("trigger_sound");var player=FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"SoundAuthor"));player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.setPos(ModuleArena.corePosition(heart));
        h.assertTrue(!SoundTimelineMenu.open(player,heart,module.id(),"damage","module"),"Non-sound stage opened");h.assertTrue(SoundTimelineMenu.open(player,heart,module.id(),"triggerSound","module"),"Sound editor failed to open");var menu=new SoundTimelineMenu(1,player.getInventory(),heart,module.id(),"triggerSound","module");int revision=menu.snapshot().getInt("Revision");
        h.assertTrue(menu.action(player,revision,"add",null,0,"minecraft:block.note_block.bass"),"Valid layer edit rejected");var row=module.soundTimeline("triggerSound").rows().getLast();h.assertTrue(!menu.action(player,revision,"start",row.id(),1,""),"Stale revision edited sound");
        h.assertTrue(menu.action(player,revision,"add",null,0,"minecraft:block.note_block.bass"),"Rapid additive selection was dropped before its revision acknowledgement");
        h.assertTrue(menu.action(player,menu.snapshot().getInt("Revision"),"start",row.id(),.5,""),"Fresh revision rejected");heart.tryActivate(player);h.assertTrue(!menu.action(player,menu.snapshot().getInt("Revision"),"delete",row.id(),0,""),"Live locked module edited");
        h.assertTrue(SoundTimelineMenu.open(player,heart,null,"deathSound","death"),"Core death editor failed");var core=new SoundTimelineMenu(2,player.getInventory(),heart,null,"deathSound","death");h.assertTrue(core.action(player,core.snapshot().getInt("Revision"),"add",null,0,"minecraft:block.note_block.bass"),"Permitted live core effect edit rejected");
        player.closeContainer();h.succeed();}
    @GameTest(template="combat")
    public static void SoundAndMusicPacketsKeepTimelineMetadata(GameTestHelper h){var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{var sound=new ModuleNetworking.ModuleSound(UUID.randomUUID(),"minecraft:block.note_block.bass",false,new net.minecraft.world.phys.Vec3(1,2,3),.7F,1.4F,48,17,35,true);ModuleNetworking.ModuleSound.CODEC.encode(buffer,sound);h.assertTrue(ModuleNetworking.ModuleSound.CODEC.decode(buffer).equals(sound),"Delayed sound/cutoff metadata lost in packet");
            var timeline=SoundTimeline.legacy("bgm","minecraft:music.dragon",.4,1,64);timeline.edit("add",null,0,"minecraft:music_disc.pigstep");timeline.edit("start",timeline.rows().getLast().id(),2,"");var music=new ModuleNetworking.BattleMusic(UUID.randomUUID(),"",true,true,1,30,timeline.save(),new net.minecraft.world.phys.Vec3(4,5,6));ModuleNetworking.BattleMusic.CODEC.encode(buffer,music);var decoded=ModuleNetworking.BattleMusic.CODEC.decode(buffer);h.assertTrue(decoded.timeline().equals(music.timeline())&&decoded.position().equals(music.position()),"BGM program lost delay/spatial data");h.succeed();
        }finally{buffer.release();}}
}
