package io.github.skystrike.shared.map;
public final class MapQueries {
    private MapQueries(){
    }
    public static boolean blocked(ArenaMap m,float x,float y){
        return m.solids().stream().anyMatch(r->r.contains(x,y));
    }
}
