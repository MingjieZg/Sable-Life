#version 150
uniform sampler2D Scene;
uniform vec2 ViewportSize;
uniform vec4 Trail0; uniform vec4 Fx0;
uniform vec4 Trail1; uniform vec4 Fx1;
uniform vec4 Trail2; uniform vec4 Fx2;
uniform vec4 Trail3; uniform vec4 Fx3;
uniform vec4 Trail4; uniform vec4 Fx4;
uniform vec4 Trail5; uniform vec4 Fx5;
uniform vec4 Trail6; uniform vec4 Fx6;
uniform vec4 Trail7; uniform vec4 Fx7;
uniform vec4 Trail8; uniform vec4 Fx8;
uniform vec4 Trail9; uniform vec4 Fx9;
uniform vec4 Trail10; uniform vec4 Fx10;
uniform vec4 Trail11; uniform vec4 Fx11;
uniform vec4 Trail12; uniform vec4 Fx12;
uniform vec4 Trail13; uniform vec4 Fx13;
uniform vec4 Trail14; uniform vec4 Fx14;
uniform vec4 Trail15; uniform vec4 Fx15;
uniform vec4 Trail16; uniform vec4 Fx16;
uniform vec4 Trail17; uniform vec4 Fx17;
uniform vec4 Trail18; uniform vec4 Fx18;
uniform vec4 Trail19; uniform vec4 Fx19;
uniform vec4 Trail20; uniform vec4 Fx20;
uniform vec4 Trail21; uniform vec4 Fx21;
uniform vec4 Trail22; uniform vec4 Fx22;
uniform vec4 Trail23; uniform vec4 Fx23;
uniform vec4 Trail24; uniform vec4 Fx24;
uniform vec4 Trail25; uniform vec4 Fx25;
uniform vec4 Trail26; uniform vec4 Fx26;
uniform vec4 Trail27; uniform vec4 Fx27;
uniform vec4 Trail28; uniform vec4 Fx28;
uniform vec4 Trail29; uniform vec4 Fx29;
uniform vec4 Trail30; uniform vec4 Fx30;
uniform vec4 Trail31; uniform vec4 Fx31;
uniform vec4 End0;
uniform vec4 End1;
uniform vec4 End2;
uniform vec4 End3;
uniform vec4 End4;
uniform vec4 End5;
uniform vec4 End6;
uniform vec4 End7;
uniform vec4 End8;
uniform vec4 End9;
uniform vec4 End10;
uniform vec4 End11;
uniform vec4 End12;
uniform vec4 End13;
uniform vec4 End14;
uniform vec4 End15;
uniform vec4 End16;
uniform vec4 End17;
uniform vec4 End18;
uniform vec4 End19;
uniform vec4 End20;
uniform vec4 End21;
uniform vec4 End22;
uniform vec4 End23;
uniform vec4 End24;
uniform vec4 End25;
uniform vec4 End26;
uniform vec4 End27;
uniform vec4 End28;
uniform vec4 End29;
uniform vec4 End30;
uniform vec4 End31;
in vec2 texCoord;
out vec4 fragColor;
float cross2(vec2 a,vec2 b){return a.x*b.y-a.y*b.x;}
// Barycentric coordinates map both triangles to one continuous ribbon UV.
vec3 bary(vec2 p,vec2 a,vec2 b,vec2 c){float det=cross2(b-a,c-a);if(abs(det)<.00000001)return vec3(-1);float v=cross2(p-a,c-a)/det,w=cross2(b-a,p-a)/det;return vec3(1-v-w,v,w);}
void main(){
 vec4 trails[32]=vec4[32](Trail0,Trail1,Trail2,Trail3,Trail4,Trail5,Trail6,Trail7,Trail8,Trail9,Trail10,Trail11,Trail12,Trail13,Trail14,Trail15,Trail16,Trail17,Trail18,Trail19,Trail20,Trail21,Trail22,Trail23,Trail24,Trail25,Trail26,Trail27,Trail28,Trail29,Trail30,Trail31);
 vec4 ends[32]=vec4[32](End0,End1,End2,End3,End4,End5,End6,End7,End8,End9,End10,End11,End12,End13,End14,End15,End16,End17,End18,End19,End20,End21,End22,End23,End24,End25,End26,End27,End28,End29,End30,End31);
 vec4 effects[32]=vec4[32](Fx0,Fx1,Fx2,Fx3,Fx4,Fx5,Fx6,Fx7,Fx8,Fx9,Fx10,Fx11,Fx12,Fx13,Fx14,Fx15,Fx16,Fx17,Fx18,Fx19,Fx20,Fx21,Fx22,Fx23,Fx24,Fx25,Fx26,Fx27,Fx28,Fx29,Fx30,Fx31);
 float aspect=ViewportSize.x/ViewportSize.y;vec2 offset=vec2(0),p=texCoord*vec2(aspect,1);
 for(int i=0;i<32;i++){
  if(effects[i].x<=0.0)continue;
  vec2 a=trails[i].xy,b=trails[i].zw,c=ends[i].zw,d=ends[i].xy;
  vec3 hit=bary(p,a,b,c);vec2 uv;
  if(min(hit.x,min(hit.y,hit.z))>=-.00001)uv=vec2(hit.z,hit.y+hit.z);
  else{hit=bary(p,a,c,d);if(min(hit.x,min(hit.y,hit.z))<-.00001)continue;uv=vec2(hit.y+hit.z,hit.y);}
  vec2 along=(c+d-a-b)*.5;float motion=length(along);if(motion<.000001)continue;
  along/=motion;vec2 normal=vec2(-along.y,along.x);
  float edge=sin(clamp(uv.y,0.0,1.0)*3.14159265);
  // Parallel refraction streaks follow the swept blade; no animated radial/heat waves.
  float streak=sin(uv.y*18.8495559)*.35+.65;
  vec2 bend=(normal*(uv.y-.5)*1.4+along*.35)*edge*edge*streak;
  offset+=bend/vec2(aspect,1)*effects[i].x*.025;
 }
 vec2 uv=clamp(texCoord+clamp(offset,vec2(-.04),vec2(.04)),vec2(.0001),vec2(.9999));
 fragColor=texture(Scene,uv);
}
