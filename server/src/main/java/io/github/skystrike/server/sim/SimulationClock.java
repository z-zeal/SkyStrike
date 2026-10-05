package io.github.skystrike.server.sim;
public final class SimulationClock {
    private long tick;
    private final double dt;
    public SimulationClock(double dt){
        this.dt=dt;
    }
    public long tick(){
        return tick;
    }
    public double dt(){
        return dt;
    }
    public long advance(){
        return ++tick;
    }
}
