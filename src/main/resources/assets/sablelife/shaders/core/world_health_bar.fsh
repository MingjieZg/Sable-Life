#version 150
in vec2 texCoord;
uniform vec2 BarSize;
uniform float Fill;
uniform float Style;
out vec4 fragColor;
float roundedBox(vec2 p,vec2 halfSize,float radius){
    vec2 q=abs(p)-halfSize+vec2(radius);
    return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
void main(){
    vec2 p=(texCoord-0.5)*BarSize;
    float shortSide=min(BarSize.x,BarSize.y);
    vec2 halfSize=BarSize*0.5-vec2(shortSide*0.035);
    float radius=Style>0.5?min(halfSize.x,halfSize.y):shortSide*0.06;
    float d=roundedBox(p,halfSize,radius);
    float feather=max(fwidth(d),shortSide*0.035);
    float outer=1.0-smoothstep(-feather,feather,d);
    // Inventory-slot-colored rim, with the same 15% base thickness as the shader-pack texture.
    float rim=min(shortSide*0.22,max(shortSide*0.15,feather*0.8));
    vec2 innerHalf=halfSize-vec2(rim);
    float innerDistance=roundedBox(p,innerHalf,max(0.0,radius-rim));
    float inner=1.0-smoothstep(-feather,feather,innerDistance);
    float cut=-innerHalf.x+2.0*innerHalf.x*Fill;
    float filled=Fill<=0.0?0.0:Fill>=1.0?1.0:1.0-smoothstep(cut-feather*0.5,cut+feather*0.5,p.x);
    vec3 color=mix(vec3(0.075,0.025,0.035),vec3(0.88,0.035,0.055),filled);
    float border=max(0.0,outer-inner);
    float content=inner*mix(0.78,1.0,filled);
    float alpha=border+content;
    vec3 borderColor=vec3(55.0,53.0,66.0)/255.0; // Inventory slot fill: #373542.
    fragColor=vec4((color*content+borderColor*border)/max(alpha,0.0001),alpha);
    if(fragColor.a<0.004)discard;
}
