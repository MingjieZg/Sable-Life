package dev.sablelife.module;

import java.util.List;

/** Shared mode visibility, also exercised without a client. Stored hidden values are retained. */
public final class SurfaceFieldSettings {
    public static boolean visible(VirtualModule m,String key){
        if(m.type().equals("slash")&&key.equals("burn"))return m.value("slashIgnite")==1;
        if(!m.type().equals("surface_field"))return true;
        if(key.equals("burn"))return m.value("fieldBurn")==1;
        if(key.equals("fieldRadius"))return m.value("fieldShape")==0;
        if(List.of("fieldLength","fieldWidth","fieldAngle","fieldPlacement").contains(key))return m.value("fieldShape")==1;
        return true;
    }
    private SurfaceFieldSettings(){}
}
