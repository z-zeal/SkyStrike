package io.github.skystrike.server.sim;
public final class TickLoop implements Runnable {
    public static final int TPS=60;
    private final SimulationClock clock=new SimulationClock(1.0/TPS);
    private final TickProfiler profiler=new TickProfiler();
    private volatile boolean running=true;
    private final Runnable tick;
    public TickLoop(Runnable tick){
        this.tick=tick;
    }
    public void stop(){
        running=false;
    }
    public SimulationClock clock(){
        return clock;
    }
    public void run(){
        long next=System.nanoTime();
        long period=1_000_000_000L/TPS;
        while(running){
            next+=period;
            profiler.begin();
            tick.run();
            clock.advance();
            long wait=next-System.nanoTime();
            if(wait>0)try{
                Thread.sleep(wait/1_000_000,(int)(wait%1_000_000));
            }
            catch(InterruptedException e){
                Thread.currentThread().interrupt();
                break;
            }
            else next=System.nanoTime();
        }
    }
}
