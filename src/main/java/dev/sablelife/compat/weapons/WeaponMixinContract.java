package dev.sablelife.compat.weapons;

import java.util.*;
import java.util.function.Function;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Check optional injection points without initializing weapon classes. */
public final class WeaponMixinContract {
    private static final String TACZ_BULLET = "com/tacz/guns/entity/EntityKineticBullet";
    private static final String TACZ_BLOCK_HIT = "com/tacz/guns/api/event/server/AmmoHitBlockEvent";
    private static final String NTGL_MELEE_PRE = "com/nukateam/ntgl/common/event/MeleeAttackEvent$Pre";
    private static final String NTGL_ARRAY_LIST = "java/util/ArrayList";
    public static final String NTGL_MELEE_ATTACK = "tryMeleeAttack(Lcom/nukateam/ntgl/common/data/WeaponData;)V";
    public static final String NTGL_MELEE_TARGET_CHECK = "Ljava/util/ArrayList;isEmpty()Z";

    public record Result(boolean compatible,String reason){}
    public static Result inspect(String mixin,String target,Function<String,byte[]> provider){
        try {
            var node=read(target.replace('.','/'),provider);
            if(node==null)return new Result(false,"Target class is absent");
            boolean valid=switch(mixin.substring(mixin.lastIndexOf('.')+1)){
                case "TaczBodyDamageMixin" -> node.methods.stream().anyMatch(m->method(m,"onHitBlock","(Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)V",false)
                        &&hasCall(m,"net/minecraft/world/entity/projectile/Projectile","onHitBlock","(Lnet/minecraft/world/phys/BlockHitResult;)V"));
                case "TaczBreachingDamageMixin" -> taczBreaching(node, provider);
                case "TaczBlockRayTraceMixin" -> node.methods.stream().anyMatch(m->method(m,"rayTraceBlocks","(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;",true));
                case "TaczBlastProfileMixin" -> field(node,"radius","F",provider,new HashSet<>())&&field(node,"power","F",provider,new HashSet<>());
                case "NtglProjectileMixin" -> ntglProjectile(node);
                case "CgsBlazeProjectileMixin" -> node.methods.stream().anyMatch(m->method(m,"onHitEntity",
                        "(Lcom/nukateam/ntgl/common/util/util/math/ExtendedEntityRayTraceResult;)V",false)
                        &&hasCall(m,"com/nukateam/ntgl/common/foundation/entity/ProjectileEntity","onHitEntity",m.desc)
                        &&hasBlazeBonus(m)
                        &&hasCall(m,"net/minecraft/world/entity/Entity","igniteForSeconds","(F)V"));
                case "NtglExplosionMixin" -> ntglExplosion(node,provider);
                case "NtglMeleeMixin" -> node.methods.stream().anyMatch(m->(m.name+m.desc).equals(NTGL_MELEE_ATTACK)
                        &&concrete(m,false)&&ntglMeleeTargets(m))
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
    private static boolean hasBlazeBonus(MethodNode method) {
        for (var instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals("net/minecraft/world/entity/Entity")
                    || !call.name.equals("hurt") || !call.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")) continue;
            var amount = previousInstruction(call);
            return amount instanceof LdcInsnNode constant && Float.valueOf(5).equals(constant.cst);
        }
        return false;
    }

    private static boolean taczBreaching(ClassNode handler, Function<String, byte[]> provider) {
        var event = read(TACZ_BLOCK_HIT, provider);
        var bullet = read(TACZ_BULLET, provider);
        return handler.methods.stream().anyMatch(m -> method(m, "onAmmoHitBlock", "(L" + TACZ_BLOCK_HIT + ";)V", true)
                    && hasCall(m, TACZ_BLOCK_HIT, "setCanceled", "(Z)V"))
                && event != null && event.interfaces.contains("net/neoforged/bus/api/ICancellableEvent")
                && event.methods.stream().anyMatch(m -> method(m, "getAmmo", "()L" + TACZ_BULLET + ";", false))
                && event.methods.stream().anyMatch(m -> method(m, "getHitResult", "()Lnet/minecraft/world/phys/BlockHitResult;", false))
                && bullet != null && bullet.methods.stream().anyMatch(m -> method(m, "getDamage", "(Lnet/minecraft/world/phys/Vec3;)F", false));
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
    private static boolean ntglMeleeTargets(MethodNode method) {
        if (method.localVariables == null) return false;
        MethodInsnNode anchor = null;
        for (var instruction : method.instructions) {
            if (!isVirtualCall(instruction, NTGL_ARRAY_LIST, "isEmpty", "()Z")) continue;
            if (anchor != null) return false;
            anchor = (MethodInsnNode) instruction;
        }
        if (anchor == null) return false;

        // Cancellation and an empty target list skip the same attack loop.
        var receiver = previousInstruction(anchor);
        var cancelBranch = previousInstruction(receiver);
        var emptyBranch = nextInstruction(anchor);
        if (!(receiver instanceof VarInsnNode targets) || targets.getOpcode() != Opcodes.ALOAD
                || !(cancelBranch instanceof JumpInsnNode canceled) || canceled.getOpcode() != Opcodes.IFNE
                || !isVirtualCall(previousInstruction(canceled), NTGL_MELEE_PRE, "isCanceled", "()Z")
                || !(emptyBranch instanceof JumpInsnNode empty) || empty.getOpcode() != Opcodes.IFNE
                || nextInstruction(canceled.label) != nextInstruction(empty.label)) return false;
        var loopReceiver = nextInstruction(empty);
        if (!(loopReceiver instanceof VarInsnNode loopTargets) || loopTargets.getOpcode() != Opcodes.ALOAD
                || loopTargets.var != targets.var
                || !isVirtualCall(nextInstruction(loopTargets), NTGL_ARRAY_LIST, "iterator", "()Ljava/util/Iterator;")) return false;

        int index = method.instructions.indexOf(anchor);
        int wielders = 0;
        for (var local : method.localVariables) {
            if (index < method.instructions.indexOf(local.start) || index >= method.instructions.indexOf(local.end)) continue;
            if (local.desc.equals("Lnet/minecraft/world/entity/LivingEntity;")) wielders++;
        }
        return wielders == 1;
    }

    private static boolean isVirtualCall(AbstractInsnNode instruction, String owner, String name, String descriptor) {
        return instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor);
    }

    private static AbstractInsnNode previousInstruction(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        var previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
        var next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
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
        var node=new ClassNode();new ClassReader(bytes).accept(node,ClassReader.SKIP_FRAMES);return node;
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
