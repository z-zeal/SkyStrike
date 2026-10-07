package io.github.skystrike.fx.lighting;

import com.badlogic.gdx.graphics.Color;
import java.util.Arrays;

/**
 * Fixed-capacity light storage addressed only by integer handles.
 *
 * <p>Slots are reused, but each handle includes a generation so removing one light cannot make a
 * stale handle refer to the next light that occupies that slot. Allocation fails with
 * {@link #INVALID_HANDLE} rather than growing past the configured budget; owners should release
 * handles as their lights stop being needed.
 */
public final class LightPool {

    public static final int DEFAULT_CAPACITY = 64;
    public static final int INVALID_HANDLE = 0;

    private static final int MAX_CAPACITY = 1 << 20;

    private final Light[] lights;
    private final boolean[] active;
    private final int[] generations;
    private final int slotBits;
    private final int slotMask;
    private final int generationMask;

    private int activeCount;
    private int nextSlot;

    public LightPool() {
        this(DEFAULT_CAPACITY);
    }

    public LightPool(int capacity) {
        if (capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException(
                    "light pool capacity must be between 1 and " + MAX_CAPACITY + ": " + capacity);
        }
        this.lights = new Light[capacity];
        this.active = new boolean[capacity];
        this.generations = new int[capacity];
        this.slotBits = bitsForSlots(capacity);
        this.slotMask = (1 << slotBits) - 1;
        this.generationMask = -1 >>> slotBits;
        Arrays.fill(generations, 1);
    }

    /**
     * Copies the supplied light data into a free pool slot.
     *
     * @return a generation-checked integer handle, or {@link #INVALID_HANDLE} when full
     */
    public int allocate(Light light) {
        if (light == null) {
            throw new IllegalArgumentException("light is required");
        }
        return allocate(
                light.x(),
                light.y(),
                light.radius(),
                light.red(),
                light.green(),
                light.blue(),
                light.intensity(),
                light.falloff(),
                light.castsShadow());
    }

    /** Allocates a light without retaining the caller's mutable {@link Color}. */
    public int allocate(
            float x,
            float y,
            float radius,
            Color colour,
            float intensity,
            float falloff,
            boolean castsShadow) {
        if (colour == null) {
            throw new IllegalArgumentException("light colour is required");
        }
        return allocate(x, y, radius, colour.r, colour.g, colour.b, intensity, falloff, castsShadow);
    }

    private int allocate(
            float x,
            float y,
            float radius,
            float red,
            float green,
            float blue,
            float intensity,
            float falloff,
            boolean castsShadow) {
        int slot = findFreeSlot();
        if (slot < 0) {
            return INVALID_HANDLE;
        }

        if (lights[slot] == null) {
            lights[slot] = new Light(x, y, radius, new Color(red, green, blue, 1f), intensity, falloff, castsShadow);
        } else {
            lights[slot].set(x, y, radius, red, green, blue, intensity, falloff, castsShadow);
        }
        active[slot] = true;
        activeCount++;
        nextSlot = (slot + 1) % lights.length;
        return encode(slot);
    }

    /** Replaces every property of a live light; returns false for a stale or unknown handle. */
    public boolean update(
            int handle,
            float x,
            float y,
            float radius,
            Color colour,
            float intensity,
            float falloff,
            boolean castsShadow) {
        if (colour == null) {
            throw new IllegalArgumentException("light colour is required");
        }
        int slot = decode(handle);
        if (slot < 0) {
            return false;
        }
        lights[slot].set(x, y, radius, colour, intensity, falloff, castsShadow);
        return true;
    }

    /** Updates only the position of a live light. */
    public boolean move(int handle, float x, float y) {
        int slot = decode(handle);
        if (slot < 0) {
            return false;
        }
        lights[slot].setPosition(x, y);
        return true;
    }

    /** Releases a handle. A later light in this slot will receive a different generation. */
    public boolean release(int handle) {
        int slot = decode(handle);
        if (slot < 0) {
            return false;
        }
        active[slot] = false;
        activeCount--;
        advanceGeneration(slot);
        if (slot < nextSlot) {
            nextSlot = slot;
        }
        return true;
    }

    /** Releases every active handle and invalidates all previously issued values. */
    public void clear() {
        for (int slot = 0; slot < active.length; slot++) {
            if (active[slot]) {
                active[slot] = false;
                advanceGeneration(slot);
            }
        }
        activeCount = 0;
        nextSlot = 0;
    }

    public boolean contains(int handle) {
        return decode(handle) >= 0;
    }

    public int size() {
        return activeCount;
    }

    public int capacity() {
        return lights.length;
    }

    /** Package-local access for {@link LightPass}; no pooled object escapes to gameplay code. */
    Light lightAtSlot(int slot) {
        return slot >= 0 && slot < lights.length && active[slot] ? lights[slot] : null;
    }

    /**
     * Package-local handle lookup lets the pass treat the local player's own silhouette specially.
     */
    int handleAtSlot(int slot) {
        return slot >= 0 && slot < lights.length && active[slot] ? encode(slot) : INVALID_HANDLE;
    }

    private int findFreeSlot() {
        if (activeCount >= active.length) {
            return -1;
        }
        for (int offset = 0; offset < active.length; offset++) {
            int slot = (nextSlot + offset) % active.length;
            if (!active[slot]) {
                return slot;
            }
        }
        return -1;
    }

    private int encode(int slot) {
        return (generations[slot] << slotBits) | slot;
    }

    private int decode(int handle) {
        if (handle == INVALID_HANDLE) {
            return -1;
        }
        int slot = handle & slotMask;
        if (slot >= active.length || !active[slot]) {
            return -1;
        }
        int generation = handle >>> slotBits;
        return generation != 0 && generations[slot] == generation ? slot : -1;
    }

    private void advanceGeneration(int slot) {
        int next = (generations[slot] + 1) & generationMask;
        generations[slot] = next == 0 ? 1 : next;
    }

    private static int bitsForSlots(int capacity) {
        int bits = 1;
        while ((1 << bits) < capacity) {
            bits++;
        }
        return bits;
    }
}
