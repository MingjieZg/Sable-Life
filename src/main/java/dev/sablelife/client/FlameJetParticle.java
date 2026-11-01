package dev.sablelife.client;

import dev.sablelife.compat.sable.SableBodyProbe;
import dev.sablelife.module.FlameJetGeometry;
import dev.sablelife.module.ModuleNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.resources.ResourceLocation;
import dev.sablelife.SableLife;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** No gravity or drag: each particle advances at the configured axial speed, stopping at walls/range. */
@EventBusSubscriber(modid=SableLife.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class FlameJetParticle extends FlameParticle {
    private static SpriteSet sprites;
    private static SpriteSet soulSprites;
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(SableLife.FLAME_JET.get(),set->{sprites=set;return new FlameParticle.Provider(set);});
        event.registerSpriteSet(SableLife.SOUL_FLAME_JET.get(),set->{soulSprites=set;return new FlameParticle.Provider(set);});
    }
    public static boolean spriteReady() {
        return sprites!=null&&soulSprites!=null&&sprites.get(net.minecraft.util.RandomSource.create()).contents().name()
                .equals(ResourceLocation.withDefaultNamespace("flame"))&&soulSprites.get(net.minecraft.util.RandomSource.create()).contents().name()
                .equals(ResourceLocation.withDefaultNamespace("soul_fire_flame"));
    }
    private final Vec3 velocity;
    private final double speed,range;
    private double travelled;
    private static ClientLevel budgetLevel;
    private static long budgetTick=Long.MIN_VALUE;
    private static int births;
    public static void resetBudget() {
        budgetLevel=null;budgetTick=Long.MIN_VALUE;births=0;
    }

    private FlameJetParticle(ClientLevel level,FlameJetGeometry.Birth birth,double speed,double range,int appearance,float size) {
        super(level,birth.origin().x,birth.origin().y,birth.origin().z,0,0,0);
        this.velocity=birth.velocity();this.speed=speed;this.range=range;
        setPos(birth.origin().x,birth.origin().y,birth.origin().z);
        this.xo=x;this.yo=y;this.zo=z;this.quadSize=.22F*size*(.85F+level.random.nextFloat()*.3F);
        this.lifetime=(int)Math.ceil(range/speed)+2;
        pickSprite(appearance==1?soulSprites:sprites);
        // Sub-tick births have already flown for this fraction of the latest emission tick.
        advance(birth.phase());
    }
    private void advance(double fraction) {
        double part=Math.min(fraction,Math.max(0,(range-travelled)/speed));
        var from=new Vec3(x,y,z);var to=from.add(velocity.scale(part));
        var hit=level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,(net.minecraft.world.entity.Entity)null));
        if(hit.getType()!=HitResult.Type.MISS) {
            var point=SableBodyProbe.toWorld(level,hit.getLocation());setPos(point.x,point.y,point.z);remove();return;
        }
        move(to.x-x,to.y-y,to.z-z);travelled+=part*speed;
        if(travelled>=range-.00001)remove();
    }
    @Override public void tick() {
        xo=x;yo=y;zo=z;if(age++>=lifetime){remove();return;}advance(1);
    }
    @Override public float getQuadSize(float partialTick) {
        // A small tongue at birth develops into a fuller flame, rather than identical beads.
        float progress=(float)Math.clamp((travelled+partialTick*speed)/range,0,1);
        return quadSize*(.8F+.45F*(float)Math.sin(progress*Math.PI));
    }
    public static void receive(ModuleNetworking.FlameJet jet) {
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||sprites==null||soulSprites==null)return;
        var o=jet.origin();var d=jet.direction();
        if(!Double.isFinite(o.x+o.y+o.z+d.x+d.y+d.z+jet.speed()+jet.range()+jet.width())
                ||d.lengthSqr()<.99||d.lengthSqr()>1.01||jet.speed()<.1||jet.speed()>4
                ||jet.range()<1||jet.range()>32||jet.width()<.25||jet.width()>4
                ||jet.appearance()<0||jet.appearance()>1||!Float.isFinite(jet.size())||jet.size()<.1F||jet.size()>4
                ||mc.player.position().distanceToSqr(o)>128*128)return;
        if(budgetLevel!=mc.level||budgetTick!=mc.level.getGameTime()) {
            budgetLevel=mc.level;budgetTick=mc.level.getGameTime();births=0;
        }
        for(var birth:FlameJetGeometry.births(jet)) for(var entry:jet.particles()) {
            if(births>=1200)break;births++;
            if(entry.id().equals("minecraft:flame")||entry.id().equals("minecraft:soul_fire_flame")){
                var particle=new FlameJetParticle(mc.level,birth,jet.speed(),jet.range(),entry.id().equals("minecraft:soul_fire_flame")?1:0,entry.size());
                if(entry.ticks()>0)particle.setLifetime(entry.ticks());
                if(particle.isAlive())mc.particleEngine.add(particle);
            }else{
                var type=dev.sablelife.module.ModuleParticles.options(entry);if(type==null)continue;
                var from=birth.origin();var particle=mc.particleEngine.createParticle(type,from.x,from.y,from.z,0,0,0);
                if(particle!=null){ParticleVisualClient.color(particle,entry);particle.scale(entry.size());((ParticleTravel)particle).sablelife$jet(from,birth.velocity(),jet.speed(),jet.range(),birth.phase(),entry.ticks());}
            }
        }
    }
}
