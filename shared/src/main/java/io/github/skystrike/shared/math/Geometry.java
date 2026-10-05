package io.github.skystrike.shared.math;
import io.github.skystrike.shared.map.Rect;
public final class Geometry {
    private Geometry(){
    }
    public static boolean segmentIntersectsAabb(float x0,float y0,float x1,float y1,Rect r){
        float dx=x1-x0,dy=y1-y0,t0=0,t1=1;
        float[] p={
            -dx,dx,-dy,dy}
            ,q={
                x0-r.x(),r.x()+r.width()-x0,y0-r.y(),r.y()+r.height()-y0}
                ;
                for(int i=0;
                i<4;
                i++){
                    if(p[i]==0){
                        if(q[i]<0)return false;
                    }
                    else{
                        float t=q[i]/p[i];
                        if(p[i]<0)t0=Math.max(t0,t);
                        else t1=Math.min(t1,t);
                        if(t0>t1)return false;
                    }
                }
                return true;
            }
        }
