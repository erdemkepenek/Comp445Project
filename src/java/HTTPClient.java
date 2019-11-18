import sun.nio.ch.SocketAdaptor;

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
    private SocketAddress ROUTER_ADDR;
    private InetSocketAddress SERVER_ADDR;
    private DatagramSocket socket;
    private PrintWriter printWriter;
    private BufferedReader bufferedReader;
    private boolean verbose = false;


    public void start() throws  IOException {
        socket = new DatagramSocket();
        SERVER_ADDR = new InetSocketAddress("localhost",8007);
        ROUTER_ADDR = new InetSocketAddress("localhost", 3000);

        try(DatagramChannel channel = DatagramChannel.open()){
                threeWayHandshake(channel);
                String msg = "Hello World";
                Packet p = new Packet.Builder()
                        .setType(0)
                        .setSequenceNumber(1L)
                        .setPortNumber(SERVER_ADDR.getPort())
                        .setPeerAddress(SERVER_ADDR.getAddress())
                        .setPayload(msg.getBytes())
                        .create();
                channel.send(p.toBuffer(), ROUTER_ADDR);
                System.out.println("Sending \"" +msg + "\" to router at " + ROUTER_ADDR);

                timer(channel, p);

                // We just want a single response.
                ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                SocketAddress router = channel.receive(buf);
                buf.flip();
                Packet resp = Packet.fromBuffer(buf);
                String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
                System.out.println(payload);
        }
    }

    //data type 0
    //SYN type 1
    //SYN ACK type 2
    // ACK type 3

    private void threeWayHandshake(DatagramChannel channel) throws IOException {
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

        //receive SYN-ACK
        ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
        SocketAddress router = channel.receive(buf);
        buf.flip();
        Packet resp = Packet.fromBuffer(buf);

        Packet ack = resp.toBuilder()
                .setType(3)
                .setSequenceNumber(resp.getSequenceNumber() + 1)
                .setPayload("Hello World".getBytes())
                .create();
        System.out.println("Sending ACK to router at: " + ROUTER_ADDR);
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

    public void end() throws  IOException{
            if(socket != null){
                socket.close();
            }
            printWriter.close();
            bufferedReader.close();
        }

    public void get(URL url, List<String> headers) throws IOException{
        String pathString = url.getPath().equals("")? "/": url.getPath();
        String queryString = url.getQuery() != null? "?" + url.getQuery(): "";
        StringBuilder sb = new StringBuilder();

        sb.append("GET "+ pathString + queryString + " HTTP/1.0\r\n");
        sb.append("Host: " + url.getHost() + "\r\n");
        for(String header: headers) {
            sb.append(header + "\r\n");
        }
        sb.append("\r\n");
        byte[] payload = sb.toString().getBytes();
        //TODO make the GET packet
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
        //TODO make the POST packet
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    //Main method used to test the library
    public static void main(String[] args) throws IOException{
        HTTPClient client = new HTTPClient();
        client.setVerbose(true);
        try {
            /*//trying to establish connection to the server
            ArrayList<String> headers = new ArrayList<>();
            String host = "http://httpbin.org";
            String arguments = "?hello=true";
            String data = "{\"Assignment\":\"1\"}";
            URL urlGet = new URL(host+"/get"+arguments);
            URL urlPost = new URL(host+"/post"+arguments);
            int getPort = urlGet.getPort() != -1? urlGet.getPort(): urlGet.getDefaultPort();
            int postPort = urlPost.getPort() != -1? urlPost.getPort(): urlPost.getDefaultPort();
            headers.add("User-Agent: Concordia-HTTP/1.0");
            System.out.println("TEST GET REQUEST");
            client.start();
            client.get(urlGet, headers);
            client.end();
            System.out.println("\nTEST POST REQUEST");
            client.start();
            client.post(urlPost,headers,data);*/
            client.start();
        } catch (UnknownHostException e) {
            System.err.println("The Connection has not been made");
        } catch (IOException e) {
            System.err.println("Connection is not established, because the server may have problems."+e.getMessage());
        }
    }
}