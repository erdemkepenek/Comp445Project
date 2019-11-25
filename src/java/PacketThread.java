import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

public class PacketThread extends Thread {
    private DatagramChannel channel;
    private Packet packet;
    private boolean client;
    private long packetNumber;
    private InetSocketAddress SERVER_ADDR = new InetSocketAddress("localhost",8007);
    private SocketAddress ROUTER_ADDR = new InetSocketAddress("localhost", 3000);

    public PacketThread(boolean client, DatagramChannel channel, Packet packet) {
            this.channel = channel;
            this.client = client;
            this.packetNumber = packet.getSequenceNumber();
            this.packet = packet;
    }

    public boolean[] getAckFlags() {
        if(client) {
            return HTTPClient.segmentResponses;
        }
        return HTTPServer.segmentResponses;
    }

    public int[] getWindow() {
        if(client) {
           return HTTPClient.window;
        }
        return HTTPServer.window;
    }

    public int getCurrentType() {
        if(client) {
            return HTTPClient.currentType;
        }
        return HTTPServer.currentType;
    }

    @Override
    public void run() {
        try{
            while(this.packetNumber > getWindow()[1]) {
                yield();
            }
            channel.send(packet.toBuffer(), ROUTER_ADDR);
            timer(channel, packet);
        }catch(IOException e) {

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
                buffer.flip();
                if(received.getType() != getCurrentType()){
                    continue;
                }
                System.out.println("Received ACK for packet" + received.getSequenceNumber());
                if(received.getSequenceNumber() < getAckFlags().length) {
                    ackPacket(received.getSequenceNumber());
                }
                System.out.println("current window[" + getWindow()[0] + ", " + getWindow()[1] + "]");
            }
        }
    }

    private void ackPacket(long packetNumber) {
        if(client){
            HTTPClient.ackPacket(packetNumber);
        }
        else{
            HTTPServer.ackPacket(packetNumber);
        }
    }

    public boolean isAcked() {
        return getAckFlags()[(int) this.packetNumber];
    }

    public void updateWindow() {
        if(client){
            HTTPClient.updateWindow();
        }
        else{
            HTTPServer.updateWindow();
        }
    }

}
