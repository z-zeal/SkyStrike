package io.github.skystrike.shared.net;
public final class PacketJoinRequest implements Packet {
    public String name;
    public PacketJoinRequest(){
    }
    public PacketJoinRequest(String n){
        name=n;
    }
}
