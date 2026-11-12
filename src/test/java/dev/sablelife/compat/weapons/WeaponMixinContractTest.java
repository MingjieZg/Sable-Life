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
        hit.visitMethodInsn(Opcodes.INVOKEVIRTUAL,entity,"hurt","(Lnet/minecraft/world/damagesource/DamageSource;F)Z",false);
        hit.visitMethodInsn(Opcodes.INVOKEVIRTUAL,entity,"igniteForSeconds","(F)V",false);
        hit.visitInsn(Opcodes.RETURN);
        var output=new ClassWriter(0);node.accept(output);
        var classes=new HashMap<String,byte[]>();classes.put(target,output.toByteArray());
        assertTrue(check(hook,target,classes).compatible());
        edit(classes,target,changed->{
            var instructions=changed.methods.getFirst().instructions;
            for(var instruction:instructions.toArray())
                if(instruction instanceof MethodInsnNode call&&call.name.equals("igniteForSeconds"))instructions.remove(call);
        });
        assertFalse(check(hook,target,classes).compatible());
        assertFalse(check(hook,target,Map.of()).compatible());
    }
}
