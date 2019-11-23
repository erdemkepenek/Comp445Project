import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

public class PacketThread extends Thread {
    private DatagramChannel channel;
    private Packet packet;
    private boolean[] ackFlags;
    private long packetNumber;
    private int lowestSegment;
    private int maxSegment;
    private int currentType;

    private InetSocketAddress SERVER_ADDR = new InetSocketAddress("localhost",8007);
    private SocketAddress ROUTER_ADDR = new InetSocketAddress("localhost", 3000);

    public PacketThread(boolean client, DatagramChannel channel, Packet packet)
    {
        if(client){
            this.channel = channel;
            this.ackFlags = HTTPClient.segmentResponses;
            this.packetNumber = packet.getSequenceNumber();
            this.packet = packet;
            this.lowestSegment = HTTPClient.lowestSegment;
            this.maxSegment = HTTPClient.maxSegment;
            this.currentType = HTTPClient.currentType;
        }
        else{
            this.channel = channel;
            this.ackFlags = HTTPServer.segmentResponses;
            this.packetNumber = packet.getSequenceNumber();
            this.packet = packet;
            this.lowestSegment = HTTPServer.lowestSegment;
            this.maxSegment = HTTPServer.maxSegment;
            this.currentType = HTTPServer.currentType;
        }

    }

    @Override
    public void run() {
        try{
            while(this.packetNumber > maxSegment) {
                yield();
            }
            if(!isAcked()) {
            channel.send(packet.toBuffer(), ROUTER_ADDR);
            timer(channel, packet);
            }
        }catch(IOException e){

        }
    }

    public void timer(DatagramChannel channel, Packet p ) throws IOException {
        ByteBuffer buffer = ByteBuffer
                .allocate(Packet.MAX_LEN)
                .order(ByteOrder.BIG_ENDIAN);

        while(!this.isAcked()) {
            buffer.clear();
            channel.configureBlocking(false);
            Selector selector = Selector.open();
            channel.register(selector, OP_READ);
            selector.select(5000);

            Set<SelectionKey> keys = selector.selectedKeys();
            if (keys.isEmpty()) {
                if(isAcked()) {
                    return;
                }
                System.out.println("Timed-Out, resending...");
                channel.send(p.toBuffer(), ROUTER_ADDR);
            } else {
                keys.clear();
                channel.receive(buffer);
                buffer.flip();
                if(buffer.limit() < Packet.MIN_LEN) {
                    continue;
                }
                Packet received = Packet.fromBuffer(buffer);
                if(received.getType() != currentType){
                    continue;
                }
                buffer.flip();
                synchronized (this){
                    if(!this.ackFlags[(int) this.packetNumber]){
                        this.ackFlags[(int) this.packetNumber] = true;
                        updateWindow();
                        notifyAll();
                    }
                }
            }
        }
    }

    public boolean isAcked() {
        return ackFlags[(int) this.packetNumber];
    }

    public void updateWindow() {
        if(ackFlags[lowestSegment]){
            if(lowestSegment < maxSegment) {
                lowestSegment++;
            }
            if(maxSegment < ackFlags.length)
            {
                maxSegment++;
            }
        }
    }

}
