package io.github.skystrike.fx.particle;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

/**
 * One persistent, stateless GPU particle buffer (build plan M7 §8.2, effects plan §7.1).
 *
 * <p>Every particle is a quad of four vertices carrying a full descriptor: spawn position,
 * velocity, spawn time, lifetime, size and colour curves, gravity, drag and turbulence. The
 * descriptor is written exactly once, at spawn, into a slot of a ring buffer; from then on the
 * only the single {@code u_time} uniform changes, and the vertex shader integrates the motion
 * analytically. Nothing per-particle is ever updated, read back or re-uploaded.
 *
 * <p>Slots are a ring: a slot is free once its particle's lifetime has elapsed, and the write
 * cursor resumes from the last allocation. Dead quads are not erased — the vertex shader
 * collapses them to a zero-area triangle, so a dead slot costs nothing but its memory. The whole
 * buffer uploads once per frame, and only on frames where something spawned.
 *
 * <p>Allocation is bounded by the tier budget: {@link #spawn} fails when the buffer is full, and
 * the caller drops the rest of the burst. Degrading by count is the whole point of a budget.
 */
public final class ParticleBuffer implements Disposable {

    /**
     * Interleaved floats per vertex: corner(2) spawn(2) velocity(2) time(1) life(1) sizes(2)
     * colours(8) gravity/drag/turbulence(3).
     */
    public static final int FLOATS_PER_VERTEX = 21;

    public static final int VERTICES_PER_PARTICLE = 4;

    public static final int FLOATS_PER_PARTICLE = FLOATS_PER_VERTEX * VERTICES_PER_PARTICLE;

    private static final float[][] CORNERS = {
        {-1f, -1f},
        {1f, -1f},
        {1f, 1f},
        {-1f, 1f}
    };

    /** A collapsed, already-dead particle: zero size, spawned before the clock began. */
    private static final ParticleDescriptor DEAD = new ParticleDescriptor(
        0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f);

    private final int capacity;
    private final float[] staging;
    private final float[] aliveUntil;
    private final Mesh mesh;
    private final short[] indices;
    private boolean dirty;
    private int cursor;

    public ParticleBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("particle buffer capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        this.staging = new float[capacity * FLOATS_PER_PARTICLE];
        this.aliveUntil = new float[capacity];
        this.indices = new short[capacity * 6];
        for (int particle = 0; particle < capacity; particle++) {
            int base = particle * VERTICES_PER_PARTICLE;
            int indexBase = particle * 6;
            indices[indexBase] = (short) base;
            indices[indexBase + 1] = (short) (base + 1);
            indices[indexBase + 2] = (short) (base + 2);
            indices[indexBase + 3] = (short) (base + 2);
            indices[indexBase + 4] = (short) (base + 3);
            indices[indexBase + 5] = (short) base;
        }

        this.mesh = new Mesh(
            false,
            capacity * VERTICES_PER_PARTICLE,
            capacity * 6,
            new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_corner"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_spawnPos"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_velocity"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_spawnTime"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_lifetime"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_startSize"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_endSize"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 4, "a_startColor"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 4, "a_endColor"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_gravity"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_drag"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_turbulence"));
        this.mesh.setIndices(indices);

        // The VBO starts uninitialised: seed every slot as a collapsed, already-dead particle so
        // the first frame cannot draw garbage before the first upload.
        for (int slot = 0; slot < capacity; slot++) {
            aliveUntil[slot] = -1f;
            writeDescriptor(slot, DEAD);
        }
        mesh.setVertices(staging);
    }

    public int capacity() {
        return capacity;
    }

    /**
     * Claims a slot and writes one particle descriptor into it.
     *
     * @param now the effect clock's current time, used for the lifetime bookkeeping
     * @return true when the particle was written, false when the buffer is full
     */
    public boolean spawn(float now, ParticleDescriptor descriptor) {
        int slot = findFreeSlot(now);
        if (slot < 0) {
            return false;
        }
        writeDescriptor(slot, descriptor);
        aliveUntil[slot] = now + descriptor.lifetime();
        cursor = (slot + 1) % capacity;
        dirty = true;
        return true;
    }

    /** Uploads the staging array if anything spawned since the last upload. Call once per frame. */
    public void uploadIfDirty() {
        if (dirty) {
            mesh.setVertices(staging);
            dirty = false;
        }
    }

    /** Live particles right now — slots whose lifetime has not elapsed. O(capacity). */
    public int liveCount(float now) {
        int live = 0;
        for (int slot = 0; slot < capacity; slot++) {
            if (aliveUntil[slot] > now) {
                live++;
            }
        }
        return live;
    }

    /** Draws the whole buffer; dead quads collapse in the vertex shader. */
    public void renderWith(ShaderProgram shader) {
        mesh.render(shader, GL20.GL_TRIANGLES);
    }

    @Override
    public void dispose() {
        mesh.dispose();
    }

    private int findFreeSlot(float now) {
        for (int offset = 0; offset < capacity; offset++) {
            int slot = (cursor + offset) % capacity;
            if (aliveUntil[slot] <= now) {
                return slot;
            }
        }
        return -1;
    }

    private void writeDescriptor(int slot, ParticleDescriptor descriptor) {
        for (int vertex = 0; vertex < VERTICES_PER_PARTICLE; vertex++) {
            int base = slot * FLOATS_PER_PARTICLE + vertex * FLOATS_PER_VERTEX;
            staging[base] = CORNERS[vertex][0];
            staging[base + 1] = CORNERS[vertex][1];
            staging[base + 2] = descriptor.spawnX();
            staging[base + 3] = descriptor.spawnY();
            staging[base + 4] = descriptor.velocityX();
            staging[base + 5] = descriptor.velocityY();
            staging[base + 6] = descriptor.spawnTime();
            staging[base + 7] = descriptor.lifetime();
            staging[base + 8] = descriptor.startSize();
            staging[base + 9] = descriptor.endSize();
            staging[base + 10] = descriptor.startRed();
            staging[base + 11] = descriptor.startGreen();
            staging[base + 12] = descriptor.startBlue();
            staging[base + 13] = descriptor.startAlpha();
            staging[base + 14] = descriptor.endRed();
            staging[base + 15] = descriptor.endGreen();
            staging[base + 16] = descriptor.endBlue();
            staging[base + 17] = descriptor.endAlpha();
            staging[base + 18] = descriptor.gravity();
            staging[base + 19] = descriptor.drag();
            staging[base + 20] = descriptor.turbulence();
        }
    }

    /**
     * One particle's full descriptor: everything the vertex shader needs to integrate its motion
     * and shade its quad for the particle's entire life.
     */
    public record ParticleDescriptor(
        float spawnX,
        float spawnY,
        float velocityX,
        float velocityY,
        float spawnTime,
        float lifetime,
        float startSize,
        float endSize,
        float startRed,
        float startGreen,
        float startBlue,
        float startAlpha,
        float endRed,
        float endGreen,
        float endBlue,
        float endAlpha,
        float gravity,
        float drag,
        float turbulence) {
    }
}
