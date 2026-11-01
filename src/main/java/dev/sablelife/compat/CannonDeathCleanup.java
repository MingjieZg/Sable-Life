package dev.sablelife.compat;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.lang.reflect.InvocationTargetException;

/** Optional CBC boundary: remove the assembled cannon without restoring its blocks or inventory. */
public final class CannonDeathCleanup {
    public static boolean isMount(BlockEntity entity){
        if(entity==null)return false;
        for(Class<?> type=entity.getClass();type!=null;type=type.getSuperclass()){
            String name=type.getName();
            if(name.equals("rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity")||name.equals("rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity")||name.equals("riftyboi.cbcmodernwarfare.cannon_control.compact_mount.CompactCannonMountBlockEntity"))return true;
        }
        return false;
    }
    public static void discardMounted(BlockEntity mount) {
        try {
            Object entity=mount.getClass().getMethod("getContraption").invoke(mount);
            if (entity instanceof AbstractContraptionEntity cannon) {
                cannon.ejectPassengers();
                cannon.discard();
                clear(mount,"mountedContraption",null);clear(mount,"running",false);clear(mount,"reassemble",false);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unsupported CBC cannon mount cleanup API",error instanceof InvocationTargetException invocation?invocation.getCause():error);
        }
    }
    private static void clear(BlockEntity mount,String name,Object value)throws ReflectiveOperationException{
        for(Class<?> type=mount.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(mount,value);return;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private CannonDeathCleanup() {}
}
