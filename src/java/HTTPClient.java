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

   public void timer(DatagramChannel channel, Packet p ) throws IOException {
        // Try to receive a packet within timeout.
        channel.configureBlocking(false);
        Selector selector = Selector.open();
        channel.register(selector, OP_READ);
        selector.select(5000);

        Set<SelectionKey> keys = selector.selectedKeys();
        if(keys.isEmpty()){
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
        printWriter.print("GET "+ pathString + queryString + " HTTP/1.0\r\n");
        printWriter.print("Host: " + url.getHost() + "\r\n");
        for(String header: headers) {
            printWriter.print(header + "\r\n");
        }
        printWriter.print("\r\n");
        printWriter.flush();

        bufferedReader.mark(1000);
        if(!verbose) {
            String responseLine = bufferedReader.readLine() != null? bufferedReader.readLine(): "";
            while (!responseLine.equals("")) {
                responseLine = bufferedReader.readLine();
            }
        }

        bufferedReader.lines().forEach(System.out::println);
    }




    public void post(URL url, List<String> headers, String data) throws IOException {
        String pathString = url.getPath().equals("")? "/": url.getPath();
        String queryString = url.getQuery() != null? "?" + url.getQuery(): "";
        printWriter.println("POST "+ pathString + queryString+" HTTP/1.0");
        printWriter.println("Host: "+ url.getHost());
        printWriter.println("Content-Length: "+data.length());
        printWriter.println("Content-Type: application/json");
        if(headers != null)
            headers.forEach(printWriter::println);
        printWriter.println("");
        printWriter.println(data);
        printWriter.flush();

        bufferedReader.mark(1000);
        if(!verbose) {
            String responseLine = bufferedReader.readLine() != null? bufferedReader.readLine(): "";
            while (!responseLine.equals("")) {
                responseLine = bufferedReader.readLine();
            }
        }

        bufferedReader.lines().forEach(System.out::println);
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
        } finally {
            client.end();
        }

    }
}