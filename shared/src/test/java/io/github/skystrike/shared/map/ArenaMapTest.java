package io.github.skystrike.shared.map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class ArenaMapTest {
    @Test void standardMapIsMirrorSymmetric(){
        ArenaMap m=ArenaMap.standard();
        Set<Rect> s=new HashSet<>(m.solids());
        for(Rect r:m.solids()) assertTrue(s.contains(r.mirror(ArenaMap.WIDTH/2)),"not mirrored: "+r);
    }
}
