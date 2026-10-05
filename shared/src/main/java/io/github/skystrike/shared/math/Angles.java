package io.github.skystrike.shared.math;
public final class Angles {
    private Angles(){
    }
    public static float wrap(float a){
        while(a<=-180)a+=360;
        while(a>180)a-=360;
        return a;
    }
    public static float shortestDelta(float from,float to){
        return wrap(to-from);
    }
}
