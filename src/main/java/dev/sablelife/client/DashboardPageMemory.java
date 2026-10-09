package dev.sablelife.client;

import java.util.*;

/** Client-session navigation only. First field survives changed density/window sizes. */
public final class DashboardPageMemory {
    public record Position(String field,int page) {
        public int restore(List<String> fields,int slots) {
            int index=fields.indexOf(field);
            return Math.clamp(index>=0?index/Math.max(1,slots):page,0,Math.max(0,(fields.size()-1)/Math.max(1,slots)));
        }
    }
    private static final Map<UUID,Position> pages=new LinkedHashMap<>();
    public static void remember(UUID module,List<String> fields,int page,int slots) {
        if(fields.isEmpty())return;
        int at=Math.clamp(page*Math.max(1,slots),0,fields.size()-1);
        pages.put(module,new Position(fields.get(at),page));
        while(pages.size()>128)pages.remove(pages.keySet().iterator().next());
    }
    public static Position get(UUID module){return pages.get(module);}
    public static void clear(){pages.clear();}
    private DashboardPageMemory(){}
}
