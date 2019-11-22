import java.io.*;
import java.net.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

public class HTTPClient {
    static SocketAddress ROUTER_ADDR;
    static InetSocketAddress SERVER_ADDR;
    private DatagramSocket socket;
    static int currentType;
    static int lowestSegment;
    static int maxSegment;
    static boolean[] segmentResponses;
    private ArrayList<Packet> receiveBuffer;
    private boolean verbose = false;

    public void start(URL url, List<String> headers, String method) throws  IOException {
        socket = new DatagramSocket();
        SERVER_ADDR = new InetSocketAddress("localhost",8007);
        ROUTER_ADDR = new InetSocketAddress("localhost", 3000);

        try(DatagramChannel channel = DatagramChannel.open()) {
                threeWayHandshake(channel, url, headers, method);

                System.out.println("Received Data from " + ROUTER_ADDR);
                // We just want a single response.
                ByteBuffer requestResponseBuffer = ByteBuffer.allocate(Packet.MAX_LEN);
                SocketAddress requestResponseRouter = channel.receive(requestResponseBuffer);
                requestResponseBuffer.flip();
                Packet requestResponsePacket = Packet.fromBuffer(requestResponseBuffer);
                String requestResponsePayload = new String(requestResponsePacket.getPayload(), StandardCharsets.UTF_8);
                System.out.println(requestResponsePayload);
                Packet ack = requestResponsePacket.toBuilder()
                    .setType(3)
                    .setSequenceNumber(requestResponsePacket.getSequenceNumber() + 1)
                    .setPayload("Data Received".getBytes())
                    .create();
                System.out.println("Sending ACK to router at: " + ROUTER_ADDR);
                channel.send(ack.toBuffer(), ROUTER_ADDR);
                timer(channel,ack);
                System.out.println("Received ACK for ACK from " + ROUTER_ADDR);
                ByteBuffer bufferAck = ByteBuffer.allocate(Packet.MAX_LEN);
                SocketAddress routerAck = channel.receive(bufferAck);
                bufferAck.flip();
                Packet responseAck = Packet.fromBuffer(bufferAck);
                String payloadAck = new String(responseAck.getPayload(), StandardCharsets.UTF_8);
                System.out.println(payloadAck);
        }
    }

    //data type 0
    //SYN type 1
    //SYN ACK type 2
    // ACK type 3
    // ACK Confirmed type 4

    private void threeWayHandshake(DatagramChannel channel, URL url, List<String> headers, String method) throws IOException {
        Packet p = new Packet.Builder()
                .setType(1)
                .setPortNumber(SERVER_ADDR.getPort())
                .setPeerAddress(SERVER_ADDR.getAddress())
                .setSequenceNumber(0L)
                .setPayload("".getBytes())
                .create();
        System.out.println("Sending SYN to router at: " + ROUTER_ADDR);
        channel.send(p.toBuffer(), ROUTER_ADDR);
        timer(channel, p);
        System.out.println("Received SYN-ACK from " + ROUTER_ADDR);
        //receive SYN-ACK
        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        SocketAddress router = channel.receive(buf);
        buf.flip();
        Packet resp = Packet.fromBuffer(buf);

        Packet ack = resp.toBuilder()
                .setType(0)
                .setSequenceNumber(resp.getSequenceNumber() + 1)
                .setPayload(get(url,headers))
                .create();
        System.out.println("Sending ACK & Request:\r\n\"" +new String(get(url,headers)) + "\"\r\nto router at " + ROUTER_ADDR);
        channel.send(ack.toBuffer(), ROUTER_ADDR);

        timer(channel, ack);
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

    public byte[] get(URL url, List<String> headers) throws IOException{
        String pathString = url.getPath().equals("")? "/": url.getPath();
        String queryString = url.getQuery() != null? "?" + url.getQuery(): "";
        StringBuilder sb = new StringBuilder();

        sb.append("GET "+ pathString +' '+ queryString + " HTTP/1.0\r\n");
        sb.append("Host: " + url.getHost());
        for(String header: headers) {
            sb.append("\r\n"+header);
        }

        return sb.toString().getBytes();
    }

    public boolean isFinished(){
        for(Boolean seg : segmentResponses)
        {
            if(!seg){
                return false;
            }
        }
        return true;
    }

    public boolean isAcked(int seqNum) {
        return segmentResponses[seqNum];
    }



    public void post(URL url, List<String> headers, String data) throws IOException {
        String pathString = url.getPath().equals("")? "/": url.getPath();
        String queryString = url.getQuery() != null? "?" + url.getQuery(): "";
        StringBuilder sb = new StringBuilder();
        sb.append("POST "+ pathString + queryString+" HTTP/1.0\r\n");
        sb.append("Host: "+ url.getHost() +"\r\n");
        sb.append("Content-Length: "+data.length() +"\r\n");
        sb.append("Content-Type: application/json\r\n");
        if(headers != null) {
            for (String header : headers) {
                sb.append(header + "\r\n");
            }
        }
        sb.append("\r\n");
        sb.append(data);

        byte[] payload = sb.toString().getBytes();
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    //Main method used to test the library
    public static void main(String[] args) throws IOException{
        HTTPClient client = new HTTPClient();
        client.setVerbose(true);
        try {
            ArrayList<String> headers = new ArrayList<>();
            String host = "http://localhost:8007";
            String path = "/hello.txt";
            String arguments = "?hello=true";
            headers.add("User-Agent: Concordia-HTTP/1.0");
            URL urlGet = new URL(host+path+arguments);
            client.start(urlGet, headers, "GET");
        } catch (UnknownHostException e) {
            System.err.println("The Connection has not been made");
        } catch (IOException e) {
            System.err.println("Connection is not established, because the server may have problems."+e.getMessage());
        }
    }
}