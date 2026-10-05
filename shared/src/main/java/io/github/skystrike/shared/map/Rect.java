package io.github.skystrike.shared.map;
import java.util.Objects;
public record Rect(float x,float y,float width,float height){
    public Rect{
        if(width<0||height<0)throw new IllegalArgumentException("negative size");
    }
    public Rect mirror(float axis){
        return new Rect(2*axis-x-width,y,width,height);
    }
    public boolean contains(float px,float py){
        return px>=x&&px<=x+width&&py>=y&&py<=y+height;
    }
    @Override public boolean equals(Object o){
        return o instanceof Rect r&&Float.compare(x,r.x)==0&&Float.compare(y,r.y)==0&&Float.compare(width,r.width)==0&&Float.compare(height,r.height)==0;
    }
    @Override public int hashCode(){
        return Objects.hash(x,y,width,height);
    }
}
