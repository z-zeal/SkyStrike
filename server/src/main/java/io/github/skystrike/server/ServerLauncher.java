package io.github.skystrike.server;
import io.github.skystrike.server.sim.TickLoop;
public class ServerLauncher {
    public static void main(String[] args){
        TickLoop loop=new TickLoop(()->{
        }
        );
        Runtime.getRuntime().addShutdownHook(new Thread(loop::stop));
        System.out.println("SkyStrike server: 60 Hz authoritative tick loop");
        loop.run();
    }
}
