package dev.sablelife.module;

/** One trigger session: finish a full window, evaluate once, optionally begin another window. */
public final class TimedDamageWindow {
    private boolean active,running,completed,met;
    private long deadline,pulseUntil;
    private double damage,lastDamage;
    public boolean tick(long now,boolean enabled,int ticks,double required,boolean repeat,boolean outputOnSuccess,int pulse){
        if(!enabled){clear();return false;}
        if(!active){active=true;start(now,ticks);}
        if(running&&now>=deadline){
            lastDamage=damage;met=damage>=required;completed=true;running=false;
            if(met==outputOnSuccess)pulseUntil=Math.max(pulseUntil,now+Math.max(1,pulse));
            if(repeat)start(now,ticks);
        }
        return now<pulseUntil;
    }
    private void start(long now,int ticks){running=true;damage=0;deadline=now+Math.max(1,ticks);}
    public void add(double amount){if(running&&Double.isFinite(amount)&&amount>0)damage=Math.min(1e12,damage+amount);}
    public void clear(){active=running=completed=met=false;deadline=pulseUntil=0;damage=lastDamage=0;}
    public boolean running(){return running;}
    public boolean completed(){return completed;}
    public boolean met(){return met;}
    public double damage(){return damage;}
    public double lastDamage(){return lastDamage;}
    public int remaining(long now){return running?(int)Math.max(0,deadline-now):0;}
}
