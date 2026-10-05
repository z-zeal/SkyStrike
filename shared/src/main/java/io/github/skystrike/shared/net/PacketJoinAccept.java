package io.github.skystrike.shared.net;
public final class PacketJoinAccept implements Packet {
    public int playerId;
    public PacketJoinAccept(){
    }
    public PacketJoinAccept(int id){
        playerId=id;
    }
}
