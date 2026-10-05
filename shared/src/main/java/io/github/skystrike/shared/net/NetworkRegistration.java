package io.github.skystrike.shared.net;
import com.esotericsoftware.kryo.Kryo;
public final class NetworkRegistration {
    private NetworkRegistration(){
    }
    public static void register(Kryo k){
        k.register(Packet.class);
        k.register(PacketJoinRequest.class);
        k.register(PacketJoinAccept.class);
        k.register(PacketGameState.class);
    }
}
