import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

public class PacketThread extends Thread {
    DatagramChannel channel;
    Packet packet;
    boolean[] ackFlags;
    long packetNumber;
    int lowestSegment;
    int maxSegment;
    InetSocketAddress SERVER_ADDR = new InetSocketAddress("localhost",8007);
    SocketAddress ROUTER_ADDR = new InetSocketAddress("localhost", 3000);

    public PacketThread(boolean client, DatagramChannel channel, Packet packet)
    {
        if(client){
            this.channel = channel;
            this.ackFlags = HTTPClient.segmentResponses;
            this.packetNumber = packet.getSequenceNumber();
            this.packet = packet;
            this.lowestSegment = HTTPClient.lowestSegment;
            this.maxSegment = HTTPClient.maxSegment;
        }
        else{
            this.channel = channel;
            this.ackFlags = HTTPServer.segmentResponses;
            this.packetNumber = packet.getSequenceNumber();
            this.packet = packet;
            this.lowestSegment = HTTPServer.lowestSegment;
            this.maxSegment = HTTPServer.maxSegment;
        }

    }

    @Override
    public void run() {
        super.run();
    }

    public void timer(DatagramChannel channel, Packet p ) throws IOException {
        // Try to receive a packet within timeout.
        channel.configureBlocking(false);
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);
        selector.select(5000);

        Set<SelectionKey> keys = selector.selectedKeys();
        if(keys.isEmpty()){
            System.out.println("Timed-Out, resending...");
            channel.send(p.toBuffer(), ROUTER_ADDR);
            timer(channel, p);
        }
        keys.clear();
        return;
    }

}
