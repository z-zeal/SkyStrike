// Door occluder array (map plan D3). Doors never enter the SDF — the bake only runs when
// terrain is destroyed and a door swings in seconds — so the visibility and light shaders test
// a sight or light segment against the closed door rectangles analytically, beside the SDF.
// The CPU side (DoorOccluders, after the SmokeVolumes pattern) uploads exactly the doors whose
// state still blocks; the zero-filled tail keeps u_doorCount the only bound the loop obeys.

uniform int u_doorCount;
uniform vec4 u_doorRects[16]; // xy = lower-left corner, zw = size

// 1.0 when the segment reaches its end untouched by every blocking door, else 0.0 — a closed
// door blocks sight and light absolutely, like the SDF wall beside it does; a panel mid-swing
// still occludes, which is what "the state blocks, the pose only draws" means here.
float doorOccluderVisibility(vec2 p0, vec2 p1) {
    vec2 d = p1 - p0;
    for (int i = 0; i < 16; i++) {
        if (i >= u_doorCount) break;
        vec4 r = u_doorRects[i];

        // Slab test per axis: the ray is inside the rect exactly while it is inside both slabs.
        float tEnter = 0.0;
        float tExit = 1.0;
        if (abs(d.x) < 1e-6) {
            if (p0.x < r.x || p0.x > r.x + r.z) continue;
        } else {
            float inv = 1.0 / d.x;
            float t1 = (r.x - p0.x) * inv;
            float t2 = (r.x + r.z - p0.x) * inv;
            tEnter = max(tEnter, min(t1, t2));
            tExit = min(tExit, max(t1, t2));
        }
        if (abs(d.y) < 1e-6) {
            if (p0.y < r.y || p0.y > r.y + r.w) continue;
        } else {
            float inv = 1.0 / d.y;
            float t1 = (r.y - p0.y) * inv;
            float t2 = (r.y + r.w - p0.y) * inv;
            tEnter = max(tEnter, min(t1, t2));
            tExit = min(tExit, max(t1, t2));
        }
        if (tEnter <= tExit) {
            return 0.0;
        }
    }
    return 1.0;
}
