package dev.sablelife.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.sablelife.SableLife;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Vector3f;

/** Neutral sprites and geometry: the existing per-entry RGB, lifetime and size apply unchanged. */
@EventBusSubscriber(modid=SableLife.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BundledParticles {
    private BundledParticles() {}
    private static net.minecraft.client.renderer.ShaderInstance softShader;
    private static final ParticleRenderType SOFT = new ParticleRenderType() {
        @Override public com.mojang.blaze3d.vertex.BufferBuilder begin(com.mojang.blaze3d.vertex.Tesselator tess,net.minecraft.client.renderer.texture.TextureManager textures) {
            var rs=softShader;
            com.mojang.blaze3d.systems.RenderSystem.setShader(()->rs==null?net.minecraft.client.renderer.GameRenderer.getParticleShader():rs);
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0,net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_PARTICLES);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
            return tess.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE);
        }
        @Override public String toString(){return "SABLE_SOFT_PARTICLE";}
    };
    @SubscribeEvent public static void shaders(net.neoforged.neoforge.client.event.RegisterShadersEvent event)throws java.io.IOException {
        event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID,"soft_particle"),com.mojang.blaze3d.vertex.DefaultVertexFormat.PARTICLE),shader->softShader=shader);
    }
    @SubscribeEvent public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(SableLife.ARC.get(), sprites -> (type,level,x,y,z,vx,vy,vz) -> new Arc(level,x,y,z,vx,vy,vz,sprites));
        event.registerSpriteSet(SableLife.SMOKE.get(), sprites -> (type,level,x,y,z,vx,vy,vz) -> new Smoke(level,x,y,z,vx,vy,vz,sprites));
        event.registerSpriteSet(SableLife.ENERGY_MOTE.get(), sprites -> (type,level,x,y,z,vx,vy,vz) -> new Mote(level,x,y,z,vx,vy,vz,sprites));
        event.registerSpriteSet(SableLife.BRANCHING_ARC.get(), sprites -> (type,level,x,y,z,vx,vy,vz) -> new Shape(level,x,y,z,vx,vy,vz,sprites,0));
        event.registerSpriteSet(SableLife.SPARK.get(), sprites -> (type,level,x,y,z,vx,vy,vz) -> new Shape(level,x,y,z,vx,vy,vz,sprites,1));
        event.registerSpriteSet(SableLife.LIGHT_RING.get(), sprites -> (type,level,x,y,z,vx,vy,vz) -> new Shape(level,x,y,z,vx,vy,vz,sprites,2));
        event.registerSpriteSet(SableLife.INWARD_RING.get(), sprites -> (type,level,x,y,z,vx,vy,vz) -> new Shape(level,x,y,z,vx,vy,vz,sprites,3));
        event.registerSpriteSet(SableLife.CRESCENT.get(), sprites -> (type,level,x,y,z,vx,vy,vz) -> new Shape(level,x,y,z,vx,vy,vz,sprites,4));
        event.registerSpriteSet(SableLife.SOFT_FOG.get(), sprites -> (type,level,x,y,z,vx,vy,vz) -> new Shape(level,x,y,z,vx,vy,vz,sprites,5));
        event.registerSpriteSet(SableLife.STARBURST.get(), sprites -> (type,level,x,y,z,vx,vy,vz) -> new Shape(level,x,y,z,vx,vy,vz,sprites,6));
        event.registerSpriteSet(SableLife.RANDOM_STARBURST.get(), sprites -> (type,level,x,y,z,vx,vy,vz) -> new Shape(level,x,y,z,vx,vy,vz,sprites,7));
    }
    public static final class Smoke extends SmokeParticle {
        Smoke(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites) {
            super(level,x,y,z,vx,vy,vz,1,sprites);
            rCol=gCol=bCol=1;quadSize=.35F; xd=vx;yd=vy;zd=vz;lifetime=32+random.nextInt(17);
        }
        @Override public ParticleRenderType getRenderType(){return SOFT;}
        @Override public void tick(){super.tick();alpha=.7F*(1-age/(float)lifetime);}
    }
    public static final class Mote extends TextureSheetParticle {
        Mote(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites) {
            super(level,x,y,z);xd=vx;yd=vy;zd=vz;friction=.95F;hasPhysics=false;
            quadSize=.09F;lifetime=20;pickSprite(sprites);
        }
        @Override public int getLightColor(float partial){return 0xF000F0;}
        @Override public ParticleRenderType getRenderType(){return SOFT;}
        @Override public void render(VertexConsumer out,Camera camera,float partial){
            billboard(new net.minecraft.world.phys.Vec3(Mth.lerp(partial,xo,x),Mth.lerp(partial,yo,y),Mth.lerp(partial,zo,z)),out,camera,quadSize,0,alpha*envelope(age+partial,lifetime),rCol,gCol,bCol,0xF000F0,getU0(),getU1(),getV0(),getV1());
        }
    }
    /** Bounded procedural shapes use neutral atlas texels; no per-particle entities or render targets. */
    public static final class Shape extends TextureSheetParticle {
        private final int kind;
        private final float angle;
        private final float[] bolts=new float[9],branches=new float[6],previousBolts=new float[9],previousBranches=new float[6];
        Shape(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites,int kind) {
            super(level,x,y,z);this.kind=kind;xd=vx;yd=vy;zd=vz;hasPhysics=false;friction=kind==1?.98F:.96F;
            quadSize=kind==1?.4F:.7F;lifetime=switch(kind){case 0->12;case 1->10;case 2,3->20;case 4->14;case 5->48;default->12;};
            gravity=kind==1?.025F:kind==5?-.005F:0;
            if(kind==1&&vx*vx+vy*vy+vz*vz<1e-10){xd=(random.nextDouble()-.5)*.2;yd=.04+random.nextDouble()*.1;zd=(random.nextDouble()-.5)*.2;}
            angle=kind==6?0:random.nextFloat()*Mth.TWO_PI;pickSprite(sprites);reshape();System.arraycopy(bolts,0,previousBolts,0,bolts.length);System.arraycopy(branches,0,previousBranches,0,branches.length);
        }
        private void reshape(){for(int i=1;i<bolts.length-1;i++)bolts[i]=(random.nextFloat()-.5F)*.55F;for(int i=0;i<branches.length;i++)branches[i]=(random.nextFloat()-.5F)*.4F;}
        @Override public void tick(){super.tick();if(kind==0&&isAlive()&&age%2==0){System.arraycopy(bolts,0,previousBolts,0,bolts.length);System.arraycopy(branches,0,previousBranches,0,branches.length);reshape();}}
        @Override public int getLightColor(float partial){return kind==5?super.getLightColor(partial):0xF000F0;}
        @Override public ParticleRenderType getRenderType(){return SOFT;}
        @Override public void render(VertexConsumer out,Camera camera,float partial) {
            var eye=camera.getPosition();float t=Mth.clamp((age+partial)/(float)lifetime,0,1),fade=envelope(age+partial,lifetime);
            if(kind>=2){
                float scale=kind==2||kind==3?.08F+.92F*(kind==2?t:1-t):kind==5?.55F+t*.65F:kind>=6?.7F+.3F*Mth.sin(t*Mth.PI):1;
                float opacity=kind==5?fade*.45F:fade;
                billboard(new net.minecraft.world.phys.Vec3(Mth.lerp(partial,xo,x),Mth.lerp(partial,yo,y),Mth.lerp(partial,zo,z)),out,camera,quadSize*scale,kind==2||kind==3?0:angle,alpha*opacity,rCol,gCol,bCol,getLightColor(partial),getU0(),getU1(),getV0(),getV1());return;
            }
            var origin=new Vector3f((float)(Mth.lerp(partial,xo,x)-eye.x),(float)(Mth.lerp(partial,yo,y)-eye.y),(float)(Mth.lerp(partial,zo,z)-eye.z));
            var pen=new Pen(out,camera,origin,quadSize,kind==2||kind==3?0:angle,rCol,gCol,bCol,alpha,getLightColor(partial),(getU0()+getU1())*.5F,(getV0()+getV1())*.5F);
            switch(kind) {
                case 0 -> {
                    float blend=((age%2)+partial)*.5F;
                    for(int i=1;i<bolts.length;i++)pen.glow(-1+(i-1)*.25F,Mth.lerp(blend,previousBolts[i-1],bolts[i-1]),-1+i*.25F,Mth.lerp(blend,previousBolts[i],bolts[i]),.026F,fade);
                    for(int branch=0;branch<2;branch++) {
                        int root=3+branch*2;float ax=-1+root*.25F,ay=Mth.lerp(blend,previousBolts[root],bolts[root]),sign=branch==0?1:-1;
                        for(int i=0;i<3;i++){float bx=ax+.15F+Mth.lerp(blend,previousBranches[branch*3+i],branches[branch*3+i])*.2F,by=ay+sign*(.17F+Math.abs(Mth.lerp(blend,previousBranches[branch*3+i],branches[branch*3+i])));pen.glow(ax,ay,bx,by,.01F,fade*.85F);ax=bx;ay=by;}
                    }
                }
                case 1 -> {pen.glow(-.85F,0,.85F,0,.025F,fade);}

            }
        }
    }
    /** One ordinary atlas quad per shape avoids coplanar fan seams and overlap self-occlusion. */
    private static void billboard(net.minecraft.world.phys.Vec3 world,VertexConsumer out,Camera camera,float size,float angle,float alpha,float r,float g,float b,int light,float u0,float u1,float v0,float v1){
        var origin=world.subtract(camera.getPosition());
        float c=Mth.cos(angle),s=Mth.sin(angle);
        for(int i=0;i<4;i++){float a=i<2?1:-1,cornerY=i==0||i==3?-1:1;var p=new Vector3f((a*c-cornerY*s)*size,(a*s+cornerY*c)*size,0).rotate(camera.rotation()).add((float)origin.x,(float)origin.y,(float)origin.z);out.addVertex(p.x,p.y,p.z).setUv(i<2?u1:u0,i==0||i==3?v1:v0).setColor(r,g,b,alpha).setLight(light);}
    }
    // Brief smooth entry and a continuous tail avoid hard flashes on repeated emission.
    private static float envelope(float age,int lifetime){float t=Mth.clamp(age/Math.max(1,lifetime),0,1);float enter=Mth.clamp(age/Math.min(2,Math.max(1,lifetime*.15F)),0,1),exit=Mth.clamp((1-t)/.35F,0,1);return enter*enter*(3-2*enter)*exit*exit*(3-2*exit);}
    private record Pen(VertexConsumer out,Camera camera,Vector3f origin,float size,float angle,float r,float g,float b,float alpha,int light,float u,float v) {
        void vertex(float x,float y,float opacity){float c=Mth.cos(angle),s=Mth.sin(angle);var p=new Vector3f((x*c-y*s)*size,(x*s+y*c)*size,0).rotate(camera.rotation()).add(origin);out.addVertex(p.x,p.y,p.z).setUv(u,v).setColor(r,g,b,alpha*opacity).setLight(light);}
        void quad(float ax,float ay,float bx,float by,float cx,float cy,float dx,float dy,float opacity){vertex(ax,ay,opacity);vertex(bx,by,opacity);vertex(cx,cy,opacity);vertex(dx,dy,opacity);}
        void line(float ax,float ay,float bx,float by,float width,float opacity){float dx=bx-ax,dy=by-ay,len=Mth.sqrt(dx*dx+dy*dy);if(len<.00001F)return;float nx=-dy/len*width,ny=dx/len*width;quad(bx+nx,by+ny,ax+nx,ay+ny,ax-nx,ay-ny,bx-nx,by-ny,opacity);}
        void glow(float ax,float ay,float bx,float by,float width,float opacity){line(ax,ay,bx,by,width*2,opacity*.10F);line(ax,ay,bx,by,width,opacity);}

    }
    public static final class Arc extends TextureSheetParticle {
        private final float[] px=new float[7],py=new float[7],previousX=new float[7],previousY=new float[7];
        private final float angle;
        Arc(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites) {
            super(level,x,y,z);xd=vx;yd=vy;zd=vz;hasPhysics=false;friction=.9F;
            quadSize=.65F;lifetime=6;pickSprite(sprites);
            angle=random.nextFloat()*Mth.TWO_PI;reshape();System.arraycopy(px,0,previousX,0,px.length);System.arraycopy(py,0,previousY,0,py.length);
        }
        private void reshape() {
            float c=Mth.cos(angle),s=Mth.sin(angle);
            for(int i=0;i<px.length;i++) {
                float along=-1+2*i/(float)(px.length-1),side=i==0||i==px.length-1?0:(random.nextFloat()-.5F)*.65F;
                px[i]=along*c-side*s;py[i]=along*s+side*c;
            }
        }
        @Override public void tick(){super.tick();if(isAlive()&&age%2==0){System.arraycopy(px,0,previousX,0,px.length);System.arraycopy(py,0,previousY,0,py.length);reshape();}}
        @Override public int getLightColor(float partial){return 0xF000F0;}
        @Override public ParticleRenderType getRenderType(){return SOFT;}
        @Override public void render(VertexConsumer out,Camera camera,float partial) {
            var eye=camera.getPosition();
            float x=(float)(Mth.lerp(partial,xo,this.x)-eye.x),y=(float)(Mth.lerp(partial,yo,this.y)-eye.y),z=(float)(Mth.lerp(partial,zo,this.z)-eye.z);
            float fade=envelope(age+partial,lifetime),blend=((age%2)+partial)*.5F;
            // Geometry changes on ticks, never per render frame; endpoints stay fixed.
            for(int i=1;i<px.length;i++) {
                segment(out,camera,x,y,z,i,.052F,fade*.10F,blend);
                segment(out,camera,x,y,z,i,.026F,fade,blend);
            }
        }
        private void segment(VertexConsumer out,Camera camera,float x,float y,float z,int i,float width,float opacity,float blend) {
            float ax=Mth.lerp(blend,previousX[i-1],px[i-1]),ay=Mth.lerp(blend,previousY[i-1],py[i-1]),bx=Mth.lerp(blend,previousX[i],px[i]),by=Mth.lerp(blend,previousY[i],py[i]);
            float dx=bx-ax,dy=by-ay,len=Mth.sqrt(dx*dx+dy*dy),nx=-dy/len*width,ny=dx/len*width;
            vertex(out,camera,x,y,z,bx+nx,by+ny,opacity);
            vertex(out,camera,x,y,z,ax+nx,ay+ny,opacity);
            vertex(out,camera,x,y,z,ax-nx,ay-ny,opacity);
            vertex(out,camera,x,y,z,bx-nx,by-ny,opacity);
        }
        private void vertex(VertexConsumer out,Camera camera,float x,float y,float z,float a,float b,float opacity) {
            var v=new Vector3f(a*quadSize,b*quadSize,0).rotate(camera.rotation()).add(x,y,z);
            out.addVertex(v.x,v.y,v.z).setUv((getU0()+getU1())*.5F,(getV0()+getV1())*.5F).setColor(rCol,gCol,bCol,alpha*opacity).setLight(0xF000F0);
        }
    }
}
