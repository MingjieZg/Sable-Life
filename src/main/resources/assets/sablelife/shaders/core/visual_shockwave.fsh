#version 150
uniform sampler2D Scene;
uniform vec2 ViewportSize;
uniform vec4 FilterColor;
uniform vec4 Wave0; uniform vec4 Wave1; uniform vec4 Wave2; uniform vec4 Wave3;
uniform vec4 Wave4; uniform vec4 Wave5; uniform vec4 Wave6; uniform vec4 Wave7;
uniform vec4 Fx4; uniform vec4 Fx5; uniform vec4 Fx6; uniform vec4 Fx7;
uniform vec4 Fx0; uniform vec4 Fx1; uniform vec4 Fx2; uniform vec4 Fx3;
in vec2 texCoord;
out vec4 fragColor;
void main(){
 vec4 waves[8]=vec4[8](Wave0,Wave1,Wave2,Wave3,Wave4,Wave5,Wave6,Wave7);vec4 effects[8]=vec4[8](Fx0,Fx1,Fx2,Fx3,Fx4,Fx5,Fx6,Fx7);
 float aspect=ViewportSize.x/ViewportSize.y;vec2 offset=vec2(0);float blur=0.0;
 for(int i=0;i<8;i++){
  if(waves[i].w<=0.0)continue;
  vec2 delta=(texCoord-waves[i].xy)*vec2(aspect,1);float distance=length(delta);float relative=(distance-waves[i].z)/waves[i].w;
  float band=1.0-smoothstep(0.0,1.0,abs(relative));
  vec2 direction=distance>0.00001?delta/distance:vec2(0);
  offset+=direction/vec2(aspect,1)*sin(relative*3.141593)*band*effects[i].x*0.035;
  blur=max(blur,band*effects[i].y);
 }
 vec2 uv=clamp(texCoord+offset,vec2(0.0001),vec2(0.9999));vec4 base=texture(Scene,uv);
 if(blur>0.0001){vec2 stepUV=vec2(1.0)/ViewportSize*4.0*blur;vec4 soft=(base+texture(Scene,clamp(uv+vec2(stepUV.x,0),vec2(0),vec2(1)))+texture(Scene,clamp(uv-vec2(stepUV.x,0),vec2(0),vec2(1)))+texture(Scene,clamp(uv+vec2(0,stepUV.y),vec2(0),vec2(1)))+texture(Scene,clamp(uv-vec2(0,stepUV.y),vec2(0),vec2(1))))/5.0;base=mix(base,soft,blur);}
 fragColor=vec4(mix(base.rgb,FilterColor.rgb,FilterColor.a),base.a);
}
