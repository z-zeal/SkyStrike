package io.github.skystrike.fx.particle;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.sdf.SdfField;
import java.util.Random;

/**
 * The CPU particle tier, at its smallest useful size (build plan M7 §8.2, effects plan §7.2):
 * colliding particles simulated on the CPU against the SDF, written as pre-transformed quads each
 * frame. Today it exists for exactly one effect — the shell casing — which is the cheapest
 * possible proof the tier works: it ejects, bounces off the SDF and settles.
 *
 * <p>Motion is substepped so a fast casing cannot tunnel the thinnest geometry, collision resolves
 * against the CPU {@link SdfField} (the same field the shaders sample), and a casing that runs out
 * of speed near a surface parks and fades. The pool is bounded by the tier budget; a full pool
 * drops the new casing rather than growing.
 *
 * <p>CPU-tier emitters eject perpendicular to the event's angle (a right-hand eject) with an
 * upward bias; gravity, restitution and lifetime come from the {@link EmitterConfig}.
 */
public final class CpuParticleSystem implements Disposable {

    /** Interleaved floats per casing vertex: corner(2) center(2) size(1) colour(4). */
    public static final int FLOATS_PER_VERTEX = 9;

    public static final int VERTICES_PER_PARTICLE = 4;

    public static final int FLOATS_PER_PARTICLE = FLOATS_PER_VERTEX * VERTICES_PER_PARTICLE;

    /** Longest distance one integration substep may travel, in world units (under one texel). */
    private static final float MAX_SUBSTEP_TRAVEL = 1.5f;

    private static final int MAX_SUBSTEPS = 4;

    /** A casing slower than this, resting near a surface, is parked instead of jittering forever. */
    private static final float SETTLE_SPEED = 25f;

    /** Tangential velocity kept through a bounce; the rest is friction. */
    private static final float BOUNCE_TANGENTIAL_KEEP = 0.82f;

    private static final float[][] CORNERS = {
        {-1f, -1f},
        {1f, -1f},
        {1f, 1f},
        {-1f, 1f}
    };

    private final SdfField sdf;
    private final float[] gradientScratch = new float[2];
    private final Color scratchColor = new Color();

    private Mesh mesh;
    private float[] staging;
    private Casing[] pool;
    private int capacity;

    public CpuParticleSystem(int capacity, SdfField sdf) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("cpu particle capacity must be positive: " + capacity);
        }
        this.sdf = sdf;
        setCapacity(capacity);
    }

    /** Resizes the pool, dropping live casings: a tier change is not worth preserving them for. */
    public void setCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("cpu particle capacity must be positive: " + newCapacity);
        }
        if (mesh != null) {
            mesh.dispose();
        }
        this.capacity = newCapacity;
        this.pool = new Casing[newCapacity];
        for (int i = 0; i < newCapacity; i++) {
            pool[i] = new Casing();
        }
        this.staging = new float[newCapacity * FLOATS_PER_PARTICLE];
        this.mesh = new Mesh(
            false,
            newCapacity * VERTICES_PER_PARTICLE,
            newCapacity * 6,
            new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_corner"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_center"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_size"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 4, "a_color"));
        short[] indices = new short[newCapacity * 6];
        for (int particle = 0; particle < newCapacity; particle++) {
            int base = particle * VERTICES_PER_PARTICLE;
            int indexBase = particle * 6;
            indices[indexBase] = (short) base;
            indices[indexBase + 1] = (short) (base + 1);
            indices[indexBase + 2] = (short) (base + 2);
            indices[indexBase + 3] = (short) (base + 2);
            indices[indexBase + 4] = (short) (base + 3);
            indices[indexBase + 5] = (short) base;
        }
        mesh.setIndices(indices);
        // Dead casings are written as zero-size quads, so the uninitialised VBO never shows.
        for (int slot = 0; slot < newCapacity; slot++) {
            writeCasing(slot, 0f, 0f, 0f, Color.CLEAR);
        }
        mesh.setVertices(staging);
    }

    public int capacity() {
        return capacity;
    }

    /**
     * Spawns one CPU-tier particle from an emitter config.
     *
     * @param config the emitter's data; only the CPU-relevant fields are read
     * @param x      spawn position (the ejection point)
     * @param y      spawn position
     * @param angleDegrees the event's direction; the particle ejects perpendicular to it
     * @param scale  size and speed multiplier
     * @param random the deterministic per-event random source
     * @return true when the casing was created, false when the pool is full
     */
    public boolean spawn(
            EmitterConfig config,
            float x,
            float y,
            float angleDegrees,
            float scale,
            Random random) {
        Casing casing = findFreeCasing();
        if (casing == null) {
            return false;
        }
        float jitter = (random.nextFloat() - 0.5f) * config.spreadDegrees();
        double ejectRadians = Math.toRadians(angleDegrees + 90.0 + jitter);
        float speed = lerp(config.speedMin(), config.speedMax(), random.nextFloat()) * scale;
        casing.x = x;
        casing.y = y;
        casing.vx = (float) Math.cos(ejectRadians) * speed;
        casing.vy = (float) Math.sin(ejectRadians) * speed + speed * 0.55f;
        casing.age = 0f;
        casing.lifetime = lerp(
            config.lifetimeMinSeconds(), config.lifetimeMaxSeconds(), random.nextFloat());
        casing.startSize = config.startSize() * scale;
        casing.endSize = config.endSize() * scale;
        casing.startColor.set(config.startColor());
        casing.endColor.set(config.endColor());
        casing.gravity = config.gravity();
        casing.restitution = config.restitution();
        casing.parked = false;
        casing.alive = true;
        return true;
    }

    /** Advances every live casing and rewrites the streaming mesh. Render thread only. */
    public void update(float dt) {
        if (dt <= 0f) {
            return;
        }
        int live = 0;
        for (int slot = 0; slot < capacity; slot++) {
            Casing casing = pool[slot];
            if (!casing.alive) {
                writeCasing(slot, 0f, 0f, 0f, Color.CLEAR);
                continue;
            }
            live++;
            casing.age += dt;
            if (casing.age >= casing.lifetime) {
                casing.alive = false;
                writeCasing(slot, 0f, 0f, 0f, Color.CLEAR);
                continue;
            }
            if (!casing.parked) {
                stepCasing(casing, dt);
            }
            float lifeT = casing.age / Math.max(0.0001f, casing.lifetime);
            float size = lerp(casing.startSize, casing.endSize, lifeT);
            scratchColor.set(
                lerp(casing.startColor.r, casing.endColor.r, lifeT),
                lerp(casing.startColor.g, casing.endColor.g, lifeT),
                lerp(casing.startColor.b, casing.endColor.b, lifeT),
                lerp(casing.startColor.a, casing.endColor.a, lifeT));
            writeCasing(slot, casing.x, casing.y, size, scratchColor);
        }
        if (live > 0) {
            mesh.setVertices(staging);
        }
    }

    public int liveCount() {
        int live = 0;
        for (int slot = 0; slot < capacity; slot++) {
            if (pool[slot].alive) {
                live++;
            }
        }
        return live;
    }

    /** Draws the whole pool; dead casings are zero-size quads and produce no fragments. */
    public void renderWith(ShaderProgram shader) {
        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }

    private void stepCasing(Casing casing, float dt) {
        float speed = (float) Math.hypot(casing.vx, casing.vy);
        int substeps = (int) Math.ceil(speed * dt / MAX_SUBSTEP_TRAVEL);
        substeps = Math.max(1, Math.min(MAX_SUBSTEPS, substeps));
        float h = dt / substeps;
        float lifeT = casing.age / Math.max(0.0001f, casing.lifetime);
        float currentSize = lerp(casing.startSize, casing.endSize, lifeT);
        for (int step = 0; step < substeps; step++) {
            casing.vy += casing.gravity * h;
            float nextX = casing.x + casing.vx * h;
            float nextY = casing.y + casing.vy * h;
            if (sdf == null) {
                casing.x = nextX;
                casing.y = nextY;
                continue;
            }
            float radius = currentSize * 0.5f + 0.5f;
            float distance = sdf.sample(nextX, nextY);
            if (distance < radius) {
                sdf.gradient(nextX, nextY, gradientScratch);
                float into = casing.vx * gradientScratch[0] + casing.vy * gradientScratch[1];
                if (into < 0f) {
                    casing.vx -= (1f + casing.restitution) * into * gradientScratch[0];
                    casing.vy -= (1f + casing.restitution) * into * gradientScratch[1];
                    casing.vx *= BOUNCE_TANGENTIAL_KEEP;
                    casing.vy *= BOUNCE_TANGENTIAL_KEEP;
                }
                casing.x = nextX + gradientScratch[0] * (radius - distance);
                casing.y = nextY + gradientScratch[1] * (radius - distance);
            } else {
                casing.x = nextX;
                casing.y = nextY;
            }
        }
        float remainingSpeed = (float) Math.hypot(casing.vx, casing.vy);
        boolean nearSurface = sdf != null
            && sdf.sample(casing.x, casing.y - currentSize * 0.5f - 1f) < 1f;
        if (remainingSpeed < SETTLE_SPEED && nearSurface) {
            casing.parked = true;
            casing.vx = 0f;
            casing.vy = 0f;
        }
    }

    private Casing findFreeCasing() {
        for (int slot = 0; slot < capacity; slot++) {
            if (!pool[slot].alive) {
                return pool[slot];
            }
        }
        return null;
    }

    private void writeCasing(int slot, float x, float y, float size, Color color) {
        for (int vertex = 0; vertex < VERTICES_PER_PARTICLE; vertex++) {
            int base = slot * FLOATS_PER_PARTICLE + vertex * FLOATS_PER_VERTEX;
            staging[base] = CORNERS[vertex][0];
            staging[base + 1] = CORNERS[vertex][1];
            staging[base + 2] = x;
            staging[base + 3] = y;
            staging[base + 4] = size;
            staging[base + 5] = color.r;
            staging[base + 6] = color.g;
            staging[base + 7] = color.b;
            staging[base + 8] = color.a;
        }
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** One pooled casing. Plain mutable state, reused across spawns; nothing here is shared. */
    private static final class Casing {
        float x;
        float y;
        float vx;
        float vy;
        float age;
        float lifetime;
        float startSize;
        float endSize;
        float gravity;
        float restitution;
        boolean parked;
        boolean alive;
        final Color startColor = new Color();
        final Color endColor = new Color();
    }
}
