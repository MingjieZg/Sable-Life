package dev.sablelife.sound;
import java.util.*;
/** Bounded delayed playback, independent of the renderer and sound engine. */
public final class SoundCueQueue<T> {
    private record Cue<T>(UUID owner,long due,boolean persistent,T payload){}
    private final List<Cue<T>> cues=new ArrayList<>();
    public boolean add(UUID owner,long now,int delay,boolean persistent,T payload){if(delay<0||delay>12000||cues.size()>=4096)return false;cues.add(new Cue<>(owner,now+delay,persistent,payload));return true;}
    public List<T> drain(long now){var ready=cues.stream().filter(c->c.due<=now).sorted(Comparator.comparingLong(c->c.due)).toList();cues.removeAll(ready);return ready.stream().map(Cue::payload).toList();}
    public void cancel(UUID owner,boolean all){cues.removeIf(c->c.owner.equals(owner)&&(all||!c.persistent));}
    public void clear(){cues.clear();}
    public int size(){return cues.size();}
}
