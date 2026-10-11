package io.github.skystrike.fx.sdf;

import com.badlogic.gdx.utils.Disposable;
import io.github.skystrike.shared.map.ArenaMap;
import io.github.skystrike.shared.sdf.SdfBaker;
import io.github.skystrike.shared.sdf.SdfField;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Re-bakes the SDF off the render thread.
 *
 * <p>A bake is a linear pass over the whole field — a million and a half texels on the shipped
 * arena — so it is exactly the kind of work the plan's D4 keeps off the frame: the change happens
 * on the tick it happens, the bake runs beside the next frame, and the finished field is patched
 * into the texture when it is ready, costing one region upload.
 *
 * <p><b>One bake at a time, newest map wins.</b> A request while a bake is running replaces the
 * queued one rather than adding to a queue, so a round that takes three blocks out in one tick
 * costs at most two bakes and the frame after them shows the latest truth. Every bake is of a
 * map, not of a delta: the distance field is global, and only the upload may be a patch.
 *
 * <p><b>Who calls this.</b> The trigger is the authoritative set the client has been told about —
 * the protocol-14 block events of the next milestone. Nothing in this milestone requests a bake,
 * because nothing tells the client a block is gone yet; the path is pinned by the shared
 * determinism tests until the packet carries the news.
 */
public final class SdfRebakeWorker implements Disposable {

    private final Object lock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "skystrike-sdf-rebake");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<SdfField> finished = new AtomicReference<>();

    private ArenaMap requestedMap;
    private float requestedTexelScale;
    private boolean queued;
    private boolean baking;
    private boolean disposed;

    /**
     * Asks for a bake of {@code liveMap} at {@code texelScale}.
     *
     * @param liveMap    the map as it stands, which is what the finished field must name
     * @param texelScale the grid the texture already holds, so the finished field fits it
     */
    public void request(ArenaMap liveMap, float texelScale) {
        Objects.requireNonNull(liveMap, "liveMap");
        synchronized (lock) {
            if (disposed) {
                return;
            }
            requestedMap = liveMap;
            requestedTexelScale = texelScale;
            queued = true;
            if (!baking) {
                baking = true;
                executor.execute(this::drain);
            }
        }
    }

    /**
     * The newest finished field, or {@code null} when no bake has completed since the last call.
     * Takes it: a result is handed out once.
     */
    public SdfField poll() {
        return finished.getAndSet(null);
    }

    /** True while a bake is in flight. Diagnostics and tests. */
    public boolean isBaking() {
        synchronized (lock) {
            return baking;
        }
    }

    private void drain() {
        while (true) {
            ArenaMap map;
            float texelScale;
            synchronized (lock) {
                if (!queued) {
                    baking = false;
                    return;
                }
                queued = false;
                map = requestedMap;
                texelScale = requestedTexelScale;
            }
            finished.set(SdfBaker.bake(map, texelScale));
        }
    }

    /** Stops the worker. A bake in flight is interrupted; its result, if any, is dropped. */
    @Override
    public void dispose() {
        synchronized (lock) {
            disposed = true;
            queued = false;
        }
        executor.shutdownNow();
        finished.set(null);
    }
}
