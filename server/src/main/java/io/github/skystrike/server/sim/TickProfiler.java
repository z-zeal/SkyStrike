package io.github.skystrike.server.sim;
public final class TickProfiler {
    private long start;
    public void begin(){
        start=System.nanoTime();
    }
    public long endNanos(){
        return System.nanoTime()-start;
    }
}
