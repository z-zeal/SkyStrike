package io.github.skystrike.shared.math;
public final class Lerp {
    private Lerp(){
    }
    public static float factor(float rate,float dt){
        return 1f-(float)Math.exp(-rate*Math.max(0,dt));
    }
    public static float smooth(float a,float b,float rate,float dt){
        return a+(b-a)*factor(rate,dt);
    }
}
