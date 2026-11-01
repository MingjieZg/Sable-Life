package dev.sablelife.client;

/** HSV authoring with lossless RGB storage; no textures or platform color dependencies. */
public final class ColorPickerModel {
    private double hue,saturation,value;
    private int rgb;
    public ColorPickerModel(int rgb){rgb(rgb);}
    public int rgb(){return rgb;}
    public double hue(){return hue;}
    public double saturation(){return saturation;}
    public double value(){return value;}
    public void rgb(int color){
        rgb=color&0xFFFFFF;double r=((rgb>>16)&255)/255.,g=((rgb>>8)&255)/255.,b=(rgb&255)/255.;double max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min;
        value=max;saturation=max==0?0:d/max;
        if(d>0){double h=max==r?(g-b)/d:max==g?(b-r)/d+2:(r-g)/d+4;hue=((h/6)%1+1)%1;}
    }
    public void hue(double h){hue=Math.clamp(h,0,1);rgb=toRgb(hue,saturation,value);}
    public void square(double s,double v){saturation=Math.clamp(s,0,1);value=Math.clamp(v,0,1);rgb=toRgb(hue,saturation,value);}
    public static int toRgb(double h,double s,double v){
        h=((h%1)+1)%1;s=Math.clamp(s,0,1);v=Math.clamp(v,0,1);double c=v*s,x=c*(1-Math.abs((h*6)%2-1)),m=v-c;double r=0,g=0,b=0;
        switch((int)(h*6)){case 0->{r=c;g=x;}case 1->{r=x;g=c;}case 2->{g=c;b=x;}case 3->{g=x;b=c;}case 4->{r=x;b=c;}default->{r=c;b=x;}}
        return ((int)Math.round((r+m)*255)<<16)|((int)Math.round((g+m)*255)<<8)|(int)Math.round((b+m)*255);
    }
}
