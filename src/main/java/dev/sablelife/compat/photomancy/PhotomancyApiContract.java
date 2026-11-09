package dev.sablelife.compat.photomancy;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.function.Function;

/** Inspect bytecode without loading or initializing the optional mod's classes. */
public final class PhotomancyApiContract {
    static final String API = "dev/rew1nd/sableschematicapi/";
    private static final String BRIDGE = "dev/sablelife/compat/photomancy/";
    private static final List<String> CONSUMERS = List.of(BRIDGE+"HeartBlueprintMapper", BRIDGE+"HeartBlueprintMapper$1",
            BRIDGE+"SpawnTemplateBridge", BRIDGE+"PhotomancyRespawnBridge", BRIDGE+"PhotomancyRespawnBridge$Build", "dev/sablelife/mixin/RespawnBlueprintNbtMixin");
    public record Result(boolean compatible, String reason) {}
    private record Member(String owner, String name, String descriptor, boolean field, boolean isStatic, Boolean interfaceOwner) {}

    public static Result inspect(Function<String,byte[]> consumers, Function<String,byte[]> provider) {
        try {
            var api = new HashMap<String,ClassNode>();
            for (String name : CONSUMERS) {
                var node = read(consumers,name);
                if (node==null) return new Result(false,"Missing bridge bytecode: "+name);
                String failure=inspectConsumer(node,provider,api);
                if(failure!=null)return new Result(false,failure);
            }
            String descriptor="(L"+API+"blueprint/SableBlueprint;L"+API+"blueprint/SableBlueprint$SubLevelData;L"+API+"blueprint/SableBlueprint$BlockData;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/nbt/CompoundTag;)L"+API+"api/blueprint/survival/BlueprintNbtLoadDecision;";
            var policy=read(provider,API+"survival/BlueprintNbtPolicies");
            if(policy==null||policy.methods.stream().noneMatch(m->m.name.equals("resolve")&&m.desc.equals(descriptor)&&(m.access&Opcodes.ACC_STATIC)!=0))
                return new Result(false,"Missing trusted-template NBT hook: BlueprintNbtPolicies.resolve");
            return new Result(true,"Required blueprint API and trusted-template NBT hook are present");
        } catch (RuntimeException failure) {
            return new Result(false,"Cannot inspect blueprint API: "+failure.getClass().getSimpleName());
        }
    }

    private static String inspectConsumer(ClassNode consumer,Function<String,byte[]> provider,Map<String,ClassNode> cache) {
        var members=new LinkedHashSet<Member>();var types=new LinkedHashSet<String>();
        for(String itf:consumer.interfaces)if(itf.startsWith(API)) {
            var node=read(provider,itf);
            if(node==null||(node.access&Opcodes.ACC_INTERFACE)==0)return "Missing blueprint interface: "+itf;
            String missing=checkInterface(consumer,node,provider,new HashSet<>());
            if(missing!=null)return missing;
            for(var callback:consumer.methods)if((callback.access&Opcodes.ACC_PUBLIC)!=0&&(callback.access&Opcodes.ACC_STATIC)==0&&!callback.name.equals("<init>")) {
                var member=new Member(itf,callback.name,callback.desc,false,false,true);
                if(resolve(node,member,provider,new HashSet<>())==null)return "Changed blueprint callback: "+itf+"."+callback.name+callback.desc;
            }
        }
        for(var method:consumer.methods)for(var instruction:method.instructions) {
            if(instruction instanceof MethodInsnNode m)members.add(new Member(m.owner,m.name,m.desc,false,m.getOpcode()==Opcodes.INVOKESTATIC,m.itf));
            if(instruction instanceof FieldInsnNode f)members.add(new Member(f.owner,f.name,f.desc,true,f.getOpcode()==Opcodes.GETSTATIC||f.getOpcode()==Opcodes.PUTSTATIC,null));
            if(instruction instanceof TypeInsnNode t&&t.desc.startsWith(API))types.add(t.desc);
            if(instruction instanceof InvokeDynamicInsnNode d)for(Object arg:d.bsmArgs)if(arg instanceof Handle h)
                members.add(new Member(h.getOwner(),h.getName(),h.getDesc(),h.getTag()<=Opcodes.H_PUTSTATIC,h.getTag()==Opcodes.H_INVOKESTATIC||h.getTag()==Opcodes.H_GETSTATIC||h.getTag()==Opcodes.H_PUTSTATIC,h.getTag()<=Opcodes.H_PUTSTATIC?null:h.isInterface()));
        }
        for(var member:members)if(member.owner.startsWith(API)) {
            var owner=cache.computeIfAbsent(member.owner,k->read(provider,k));
            if(owner==null)return "Missing blueprint class: "+member.owner;
            if((owner.access&Opcodes.ACC_PUBLIC)==0||member.interfaceOwner!=null&&member.interfaceOwner!=((owner.access&Opcodes.ACC_INTERFACE)!=0))return "Changed blueprint class/interface: "+member.owner;
            Integer access=resolve(owner,member,provider,new HashSet<>());
            if(access==null||(access&Opcodes.ACC_PUBLIC)==0||((access&Opcodes.ACC_STATIC)!=0)!=member.isStatic)
                return "Incompatible blueprint member: "+member.owner+"."+member.name+member.descriptor;
        }
        for(String type:types)if(read(provider,type)==null)return "Missing blueprint class: "+type;
        return null;
    }

    private static String checkInterface(ClassNode consumer,ClassNode itf,Function<String,byte[]> provider,Set<String> seen) {
        if(!seen.add(itf.name))return null;
        for(var m:itf.methods)if((m.access&Opcodes.ACC_ABSTRACT)!=0&&consumer.methods.stream().noneMatch(c->c.name.equals(m.name)&&c.desc.equals(m.desc)))
            return "Unimplemented blueprint interface method: "+itf.name+"."+m.name+m.desc;
        for(String parent:itf.interfaces)if(parent.startsWith(API)) {
            var node=read(provider,parent);if(node==null)return "Missing blueprint interface: "+parent;
            var failure=checkInterface(consumer,node,provider,seen);if(failure!=null)return failure;
        }
        return null;
    }

    private static Integer resolve(ClassNode owner,Member member,Function<String,byte[]> provider,Set<String> seen) {
        if(!seen.add(owner.name))return null;
        if(member.field){for(var f:owner.fields)if(f.name.equals(member.name)&&f.desc.equals(member.descriptor))return f.access;}
        else {for(var m:owner.methods)if(m.name.equals(member.name)&&m.desc.equals(member.descriptor))return m.access;}
        if(member.name.equals("<init>"))return null;
        var parents=new ArrayList<>(owner.interfaces);if(owner.superName!=null)parents.add(owner.superName);
        for(String parent:parents){var node=read(provider,parent);if(node!=null){var result=resolve(node,member,provider,seen);if(result!=null)return result;}}
        return null;
    }

    /** Diagnostic hooks are optional: changes to tool internals must not disable Arena respawn. */
    public static boolean diagnosticHook(String mixin,Function<String,byte[]> provider,Function<String,byte[]> consumers) {
        try {
            String simple=mixin.substring(mixin.lastIndexOf('.')+1);
            var consumer=read(consumers,"dev/sablelife/mixin/"+simple);
            if(consumer==null||inspectConsumer(consumer,provider,new HashMap<>())!=null)return false;
            String owner=switch(simple){
                case "RespawnBuildDiagnosticsMixin" -> API+"survival/SableBlueprintIncrementalPlacer";
                case "BlueprintToolFailureDiagnosticsMixin" -> API+"blueprint/tool/BlueprintToolService";
                case "BlueprintToolFailureMessageMixin" -> API+"network/BlueprintToolServerActions";
                default -> "";
            };
            var node=read(provider,owner);if(node==null)return false;
            for(var m:node.methods) {
                if((m.access&Opcodes.ACC_STATIC)==0)continue;
                if(simple.equals("BlueprintToolFailureDiagnosticsMixin")&&m.name.equals("loadBytes")&&m.desc.equals("(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;[BLjava/lang/String;)L"+API+"blueprint/tool/BlueprintToolResult;"))return true;
                if(simple.equals("RespawnBuildDiagnosticsMixin")&&m.name.equals("step")&&m.desc.startsWith("(Lnet/minecraft/server/level/ServerLevel;L"+API+"blueprint/SableBlueprint;")&&calls(m,"java/lang/RuntimeException","getMessage","()Ljava/lang/String;"))return true;
                if(simple.equals("BlueprintToolFailureMessageMixin")&&m.name.equals("handleLoadBlueprint")&&m.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/nbt/CompoundTag;)V")&&calls(m,API+"blueprint/tool/BlueprintToolResult","success","()Z")&&m.localVariables!=null&&m.localVariables.stream().filter(v->v.desc.equals("L"+API+"blueprint/tool/BlueprintToolResult;")).count()==1)return true;
            }
        }catch(RuntimeException ignored){}
        return false;
    }
    private static boolean calls(MethodNode method,String owner,String name,String descriptor){for(var i:method.instructions)if(i instanceof MethodInsnNode m&&m.owner.equals(owner)&&m.name.equals(name)&&m.desc.equals(descriptor))return true;return false;}
    private static ClassNode read(Function<String,byte[]> source,String name){var data=source.apply(name);if(data==null)return null;var node=new ClassNode();new ClassReader(data).accept(node,0);return node;}
    private PhotomancyApiContract(){}
}
