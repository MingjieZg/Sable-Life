package dev.sablelife.compat.weapons;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class WeaponMixinContractTest {
    private static Map<String,byte[]> installed() throws Exception {
        var classes=new HashMap<String,byte[]>();
        try(var paths=Files.list(Path.of(".dev/test-mods"))){
            for(var path:paths.filter(p->p.toString().endsWith(".jar")).toList())try(var jar=new ZipFile(path.toFile())){
                for(var entry:Collections.list(jar.entries()))if(entry.getName().endsWith(".class"))
                    try(var stream=jar.getInputStream(entry)){classes.put(entry.getName().replaceFirst("\\.class$",""),stream.readAllBytes());}
            }
        }
        return classes;
    }
    private static WeaponMixinContract.Result check(String hook,String target,Map<String,byte[]> classes){
        return WeaponMixinContract.inspect(hook,target,classes::get);
    }
    private static void edit(Map<String,byte[]> classes,String target,java.util.function.Consumer<ClassNode> edit){
        var node=new ClassNode();new ClassReader(classes.get(target)).accept(node,0);edit.accept(node);
        var out=new ClassWriter(0);node.accept(out);classes.put(target,out.toByteArray());
    }
    private static final String BULLET="com/tacz/guns/entity/EntityKineticBullet";
    @Test void installedTaczAndCbcContractsMatch() throws Exception {
        var classes=installed();
        var hooks=Map.of("TaczBodyDamageMixin",BULLET,
                "TaczBlockRayTraceMixin","com/tacz/guns/util/block/BlockRayTrace",
                "TaczBlastProfileMixin","com/tacz/guns/util/block/ProjectileExplosion",
                "CannonBodyDamageMixin","rbasamoyai/createbigcannons/munitions/big_cannon/AbstractBigCannonProjectile",
                "CannonMountCleanupMixin","rbasamoyai/createbigcannons/cannon_control/cannon_mount/CannonMountBlockEntity");
        hooks.forEach((hook,target)->assertTrue(check(hook,target,classes).compatible(),hook+": "+check(hook,target,classes).reason()));
        assertTrue(check("CannonBodyDamageMixin","rbasamoyai/createbigcannons/munitions/autocannon/AbstractAutocannonProjectile",classes).compatible());
        assertTrue(check("CannonMountCleanupMixin","rbasamoyai/createbigcannons/cannon_control/fixed_cannon_mount/FixedCannonMountBlockEntity",classes).compatible());
    }
    @Test void missingModsAreSkipped(){
        assertFalse(check("TaczBodyDamageMixin",BULLET,Map.of()).compatible());
    }
    @Test void changedBulletSignatureIsSkipped() throws Exception {
        var classes=installed();edit(classes,BULLET,node->node.methods.removeIf(m->m.name.equals("onHitBlock")));
        assertFalse(check("TaczBodyDamageMixin",BULLET,classes).compatible());
    }
    @Test void missingInjectionInvocationIsSkipped() throws Exception {
        var classes=installed();edit(classes,BULLET,node->{for(var m:node.methods)for(var ins:m.instructions.toArray())if(ins instanceof MethodInsnNode call&&call.owner.equals("net/minecraft/world/entity/projectile/Projectile")&&call.name.equals("onHitBlock"))call.name="changedHook";});
        assertFalse(check("TaczBodyDamageMixin",BULLET,classes).compatible());
    }
    @Test void breachingHookRequiresTheCancelableHandlerAndNativeBulletAccessors() throws Exception {
        String hook = "TaczBreachingDamageMixin";
        String handler = "com/victoriomods/taczballisticbreaching/BallisticBlockHitHandler";
        String event = "com/tacz/guns/api/event/server/AmmoHitBlockEvent";
        var classes = installed();
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, handler, null, "java/lang/Object", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "onAmmoHitBlock", "(L" + event + ";)V", null, null);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, event, "setCanceled", "(Z)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
        assertFalse(check(hook, handler, classes).compatible());
        classes.put(handler, writer.toByteArray());
        assertTrue(check(hook, handler, classes).compatible());
        var changed = new HashMap<>(classes);
        edit(changed, handler, node -> node.methods.getFirst().desc = "(Ljava/lang/Object;)V");
        assertFalse(check(hook, handler, changed).compatible());
        edit(classes, event, node -> node.methods.removeIf(m -> m.name.equals("getHitResult")));
        assertFalse(check(hook, handler, classes).compatible());
    }
    @Test void changedShadowTypeIsSkipped() throws Exception {
        var classes=installed();String target="com/tacz/guns/util/block/ProjectileExplosion";
        edit(classes,target,node->node.fields.stream().filter(f->f.name.equals("radius")).forEach(f->f.desc="D"));
        assertFalse(check("TaczBlastProfileMixin",target,classes).compatible());
    }
    @Test void changedMountRunningFieldIsSkipped() throws Exception {
        var classes=installed();String target="rbasamoyai/createbigcannons/cannon_control/cannon_mount/CannonMountBlockEntity";
        edit(classes,target,node->node.fields.removeIf(f->f.name.equals("running")));
        assertFalse(check("CannonMountCleanupMixin",target,classes).compatible());
    }

    @Test void ntglMeleeChecksTheNativeAttackListAndCancellationBranch() {
        String hook = "NtglMeleeMixin";
        String target = "com/nukateam/ntgl/common/util/trackers/MeleeTracker$Tracker";
        String pre = "com/nukateam/ntgl/common/event/MeleeAttackEvent$Pre";
        String list = "java/util/ArrayList";
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, target, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "attackDistance", "D", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "meleeDamage", "F", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "maxTargets", "I", null, null).visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PRIVATE, "tryMeleeAttack",
                "(Lcom/nukateam/ntgl/common/data/WeaponData;)V", null, null);
        var start = new Label();
        var skip = new Label();
        var end = new Label();
        method.visitLabel(start);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitVarInsn(Opcodes.ASTORE, 2);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitVarInsn(Opcodes.ASTORE, 3);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitVarInsn(Opcodes.ASTORE, 4);
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, pre, "isCanceled", "()Z", false);
        method.visitJumpInsn(Opcodes.IFNE, skip);
        // The native target list can be first among ArrayList locals.
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, list, "isEmpty", "()Z", false);
        method.visitJumpInsn(Opcodes.IFNE, skip);
        method.visitVarInsn(Opcodes.ALOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, list, "iterator", "()Ljava/util/Iterator;", false);
        method.visitInsn(Opcodes.POP);
        method.visitLabel(skip);
        method.visitInsn(Opcodes.RETURN);
        method.visitLabel(end);
        method.visitLocalVariable("wielder", "Lnet/minecraft/world/entity/LivingEntity;", null, start, end, 2);
        method.visitLocalVariable("targets", "L" + list + ";", null, start, end, 3);
        method.visitLocalVariable("other", "L" + list + ";", null, start, end, 4);
        method.visitMaxs(1, 5);
        method.visitEnd();
        var classes = new HashMap<String, byte[]>();
        classes.put(target, writer.toByteArray());
        assertTrue(check(hook, target, classes).compatible());
        var differentList = new HashMap<>(classes);
        edit(differentList, target, changed -> {
            for (var instruction : changed.methods.getFirst().instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("isEmpty"))
                    ((VarInsnNode) call.getPrevious()).var = 4;
            }
        });
        assertFalse(check(hook, target, differentList).compatible());
        edit(classes, target, changed -> {
            for (var instruction : changed.methods.getFirst().instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("isCanceled")) call.name = "otherFlag";
            }
        });
        assertFalse(check(hook, target, classes).compatible());
        assertFalse(check(hook, target, Map.of()).compatible());
    }

    @Test void changedBlazeImpactSkipsOptionalHook() {
        String target="com/nukateam/cgs/common/faundation/entity/BlazeProjectile";
        String parent="com/nukateam/ntgl/common/foundation/entity/ProjectileEntity";
        String entity="net/minecraft/world/entity/Entity";
        String hook="CgsBlazeProjectileMixin";
        var node=new ClassNode();
        node.visit(Opcodes.V21,Opcodes.ACC_PUBLIC,target,null,parent,null);
        String descriptor="(Lcom/nukateam/ntgl/common/util/util/math/ExtendedEntityRayTraceResult;)V";
        var hit=node.visitMethod(Opcodes.ACC_PROTECTED,"onHitEntity",descriptor,null,null);
        hit.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"onHitEntity",descriptor,false);
        hit.visitLdcInsn(5.0F);
        hit.visitMethodInsn(Opcodes.INVOKEVIRTUAL,entity,"hurt","(Lnet/minecraft/world/damagesource/DamageSource;F)Z",false);
        hit.visitMethodInsn(Opcodes.INVOKEVIRTUAL,entity,"igniteForSeconds","(F)V",false);
        hit.visitInsn(Opcodes.RETURN);
        var output=new ClassWriter(0);node.accept(output);
        var classes=new HashMap<String,byte[]>();classes.put(target,output.toByteArray());
        assertTrue(check(hook,target,classes).compatible());
        var changedDamage = new HashMap<>(classes);
        edit(changedDamage, target, changed -> {
            for (var instruction : changed.methods.getFirst().instructions) {
                if (instruction instanceof LdcInsnNode constant) constant.cst = 6.0F;
            }
        });
        assertFalse(check(hook,target,changedDamage).compatible());
        edit(classes,target,changed->{
            var instructions=changed.methods.getFirst().instructions;
            for(var instruction:instructions.toArray())
                if(instruction instanceof MethodInsnNode call&&call.name.equals("igniteForSeconds"))instructions.remove(call);
        });
        assertFalse(check(hook,target,classes).compatible());
        assertFalse(check(hook,target,Map.of()).compatible());
    }
}
