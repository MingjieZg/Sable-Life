package dev.sablelife.client;

/** Pair physical left clicks on the same item, not on the same screen row number. */
public final class SoundDoubleClick {
    private String item;
    private long when;
    public boolean click(String id,long millis){
        boolean paired=id.equals(item)&&millis>=when&&millis-when<=250;
        item=paired?null:id;when=millis;return paired;
    }
    public void clear(){item=null;}
}
