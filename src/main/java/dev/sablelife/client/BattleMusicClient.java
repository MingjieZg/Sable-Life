package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.module.ModuleNetworking;
import dev.sablelife.sound.SoundTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.*;

/** One selected creature music mix, with independent voices, delays and the existing range/fade lease. */
@EventBusSubscriber(modid=SableLife.MOD_ID,value=Dist.CLIENT)
public final class BattleMusicClient {
    private static final class Candidate { SoundTimeline timeline;float volume;int fade;Vec3 center;boolean spatial;long expires,priority,generation; }
    private static final Map<UUID,Candidate> candidates=new HashMap<>();private static long tick,order,selectedGeneration;
    private static UUID selected;private static Mix mix;private static net.minecraft.client.multiplayer.ClientLevel world;
    public static void receive(ModuleNetworking.BattleMusic packet){var mc=Minecraft.getInstance();if(mc.level==null)return;if(world!=mc.level){clear();world=mc.level;}
        if(!packet.playing()){candidates.remove(packet.module());return;}if(!Float.isFinite(packet.volume()))return;
        boolean spatial=packet.timeline()!=null&&packet.timeline().contains("Rows");
        var timeline=spatial?SoundTimeline.load(packet.timeline()):SoundTimeline.legacy("bgm",packet.sound(),1,1,256);
        if(!timeline.audible())return;var candidate=candidates.computeIfAbsent(packet.module(),k->new Candidate());
        if(packet.restart()||candidate.timeline==null||!timeline.rows().equals(candidate.timeline.rows())){candidate.priority=++order;candidate.generation++;}
        candidate.timeline=timeline;candidate.volume=Math.clamp(packet.volume(),0,1);candidate.fade=Math.clamp(packet.fade(),0,400);candidate.expires=tick+60;candidate.center=packet.position();candidate.spatial=spatial;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||world!=mc.level){clear();world=mc.level;return;}if(mc.isPaused())return;tick++;candidates.entrySet().removeIf(e->e.getValue().expires<tick);
        var next=candidates.entrySet().stream().max(Comparator.comparingLong(e->e.getValue().priority)).orElse(null);
        if(next!=null){var candidate=next.getValue();if(!next.getKey().equals(selected)||candidate.generation!=selectedGeneration||mix==null){if(mix!=null)mix.stop();mix=new Mix(candidate);selected=next.getKey();selectedGeneration=candidate.generation;}}
        else if(selected!=null){selected=null;if(mix!=null)mix.fadeOut();}
        if(mix!=null){mix.tick();if(mix.finished()){mix=null;}else mc.getMusicManager().stopPlaying();}
    }
    private static final class Mix {
        private final Candidate settings;private final long started=tick;private final Set<UUID> launched=new HashSet<>();private final List<MusicVoice> voices=new ArrayList<>();private boolean fading;private int remaining;
        Mix(Candidate settings){this.settings=settings;}
        void tick(){if(!fading)for(var row:settings.timeline.rows())if(row.enabled()&&row.volume()>0&&!launched.contains(row.id())&&tick-started>=row.start()){launched.add(row.id());var voice=new MusicVoice(row,settings,tick);voices.add(voice);Minecraft.getInstance().getSoundManager().play(voice);}
            if(fading&&remaining--<=0)stop();voices.removeIf(MusicVoice::isStopped);
        }
        void stop(){voices.forEach(v->Minecraft.getInstance().getSoundManager().stop(v));voices.clear();}
        void fadeOut(){if(!fading){fading=true;remaining=settings.fade;voices.forEach(v->v.fadeOut(settings.fade));}}
        // Keep a completed finite mix selected; lease refresh must never restart its delayed rows.
        boolean finished(){return fading&&voices.isEmpty();}
    }
    private static final class MusicVoice extends AbstractTickableSoundInstance {
        private final SoundTimeline.Row row;private final Candidate candidate;private final long started;private int fade=-1,remaining;
        MusicVoice(SoundTimeline.Row row,Candidate candidate,long started){super(SoundEvent.createVariableRangeEvent(ResourceLocation.parse(row.sound())),SoundSource.MASTER,RandomSource.create());this.row=row;this.candidate=candidate;this.started=started;volume=row.volume()*candidate.volume;pitch=row.pitch();looping=row.duration()==0;relative=true;attenuation=SoundInstance.Attenuation.NONE;}
        @Override public boolean canStartSilent(){return true;}
        void fadeOut(int ticks){fade=remaining=ticks;if(ticks==0)stop();}
        @Override public void tick(){if(row.duration()>0&&BattleMusicClient.tick-started>=dev.sablelife.sound.SoundFade.stopAfter(row.duration(),row.fade())){stop();return;}float gain=row.volume()*candidate.volume*dev.sablelife.sound.SoundFade.gain(BattleMusicClient.tick-started,row.duration(),row.fade());
            var player=Minecraft.getInstance().player;if(player==null){stop();return;}
            if(candidate.spatial&&dev.sablelife.compat.sable.SableBodyProbe.toWorld(player.level(),player.position()).distanceToSqr(candidate.center)>row.radius()*row.radius())gain=0;
            if(fade>=0){gain*=Math.max(0,--remaining)/(float)Math.max(1,fade);if(remaining<=0)stop();}volume=gain;
        }
    }
    private static void clear(){if(mix!=null)mix.stop();mix=null;selected=null;candidates.clear();tick=order=selectedGeneration=0;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();world=null;}
}
