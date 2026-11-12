package dev.sablelife.compat.weapons;

import java.util.*;
import java.util.function.Function;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Check optional injection points without initializing weapon classes. */
public final class WeaponMixinContract {
    public record Result(boolean compatible,String reason){}
    public static Result inspect(String mixin,String target,Function<String,byte[]> provider){
        try {
            var node=read(target.replace('.','/'),provider);
            if(node==null)return new Result(false,"Target class is absent");
            boolean valid=switch(mixin.substring(mixin.lastIndexOf('.')+1)){
                case "TaczBodyDamageMixin" -> node.methods.stream().anyMatch(m->method(m,"onHitBlock","(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",false)
                        &&hasCall(m,"net/minecraft/world/entity/projectile/Projectile","onHitBlock","(Lnet/minecraft/world/phys/BlockHitResult;)V"));
                case "TaczBlockRayTraceMixin" -> node.methods.stream().anyMatch(m->method(m,"rayTraceBlocks","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",true));
                case "TaczBlastProfileMixin" -> field(node,"radius","F",provider,new HashSet<>())&&field(node,"power","F",provider,new HashSet<>());
                case "NtglProjectileMixin" -> ntglProjectile(node);
                case "CgsBlazeProjectileMixin" -> node.methods.stream().anyMatch(m->method(m,"onHitEntity",
                        "(Lcom/nukateam/ntgl/common/util/util/math/ExtendedEntityRayTraceResult;)V",false)
                        &&hasCall(m,"com/nukateam/ntgl/common/foundation/entity/ProjectileEntity","onHitEntity",m.desc)
                        &&hasCall(m,"net/minecraft/world/entity/Entity","hurt","(Lnet/minecraft/world/damagesource/DamageSource;F)Z")
                        &&hasCall(m,"net/minecraft/world/entity/Entity","igniteForSeconds","(F)V"));
                case "NtglExplosionMixin" -> ntglExplosion(node,provider);
                case "NtglMeleeMixin" -> node.methods.stream().anyMatch(m->method(m,"tryMeleeAttack","(Lcom/nukateam/ntgl/common/data/WeaponData;)V",false)
                        &&hasCall(m,"java/util/ArrayList","isEmpty","()Z"))
                        &&field(node,"attackDistance","D",provider,new HashSet<>())
                        &&field(node,"meleeDamage","F",provider,new HashSet<>())
                        &&field(node,"maxTargets","I",provider,new HashSet<>());
                case "CannonMountCleanupMixin" -> node.methods.stream().anyMatch(m->method(m,"disassemble","()V",false))&&field(node,"running","Z",provider,new HashSet<>());
                case "CannonBodyDamageMixin" -> node.methods.stream().anyMatch(m->{
                    if(!m.name.equals("calculateBlockPenetration")||!concrete(m,false))return false;
                    var args=Type.getArgumentTypes(m.desc);var result=Type.getReturnType(m.desc);
                    return args.length==3&&args[0].getSort()==Type.OBJECT
                            &&args[1].getDescriptor().equals("Lnet/minecraft/world/level/block/state/BlockState;")
                            &&args[2].getDescriptor().equals("Lnet/minecraft/world/phys/BlockHitResult;")
                            &&result.getSort()==Type.OBJECT;
                });
                default -> false;
            };
            return new Result(valid,valid?"Injection contract matched":"Required method, field or invocation changed");
        }catch(RuntimeException failure){return new Result(false,"Cannot inspect target: "+failure.getClass().getSimpleName());}
    }
    private static boolean ntglProjectile(ClassNode node){
        String hit="Lnet/minecraft/world/phys/";
        String entity="Lnet/minecraft/world/entity/Entity;";
        return node.methods.stream().anyMatch(m->method(m,"getDamage","()F",false))
                &&node.methods.stream().anyMatch(m->method(m,"getDamageSource","()Lnet/minecraft/world/damagesource/DamageSource;",false))
                &&node.methods.stream().anyMatch(m->method(m,"burnEntity","("+entity+")V",false))
                &&node.methods.stream().anyMatch(m->method(m,"onHit","("+hit+"HitResult;"+hit+"Vec3;"+hit+"Vec3;)V",false)
                    &&hasCall(m,node.name,"onHitBlock","("+hit+"BlockHitResult;Lnet/minecraft/world/level/block/state/BlockState;)V"))
                &&node.methods.stream().anyMatch(m->method(m,"damageEntity","(Lcom/nukateam/ntgl/common/util/util/math/ExtendedEntityRayTraceResult;)V",false)
                    &&hasCall(m,"net/minecraft/world/entity/Entity","hurt","(Lnet/minecraft/world/damagesource/DamageSource;F)Z"));
    }
    private static boolean ntglExplosion(ClassNode node,Function<String,byte[]> provider){
        return "net/minecraft/world/level/Explosion".equals(node.superName)
                &&node.methods.stream().anyMatch(m->method(m,"explode","()V",false))
                &&node.methods.stream().anyMatch(m->method(m,"finalizeExplosion","(Z)V",false))
                &&field(node,"level","Lnet/minecraft/world/level/Level;",provider,new HashSet<>())
                &&field(node,"damageSource","Lnet/minecraft/world/damagesource/DamageSource;",provider,new HashSet<>())
                &&field(node,"radius","F",provider,new HashSet<>())
                &&field(node,"damage","F",provider,new HashSet<>())
                &&field(node,"damageDecreaseWithDistance","Z",provider,new HashSet<>());
    }
    private static ClassNode read(String name,Function<String,byte[]> provider){
        byte[] bytes=provider.apply(name);if(bytes==null)return null;
        var node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);return node;
    }
    private static boolean concrete(MethodNode m,boolean isStatic){
        return (m.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0&&((m.access&Opcodes.ACC_STATIC)!=0)==isStatic;
    }
    private static boolean method(MethodNode m,String name,String descriptor,boolean isStatic){
        return m.name.equals(name)&&m.desc.equals(descriptor)&&concrete(m,isStatic);
    }
    private static boolean hasCall(MethodNode method,String owner,String name,String descriptor){
        for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(descriptor))return true;
        return false;
    }
    private static boolean field(ClassNode node,String name,String descriptor,Function<String,byte[]> provider,Set<String> visited){
        if(node==null||!visited.add(node.name))return false;
        for(var f:node.fields)if(f.name.equals(name)&&f.desc.equals(descriptor)&&(f.access&Opcodes.ACC_STATIC)==0)return true;
        return node.superName!=null&&field(read(node.superName,provider),name,descriptor,provider,visited);
    }
    private WeaponMixinContract(){}
}
