import joptsimple.OptionParser;
import joptsimple.OptionSet;


import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.*;

import static java.lang.Thread.yield;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;

public class HTTPServer{

    private static BufferedReader input;
    private static File rootPath = new File(Paths.get("").toAbsolutePath().toString() + "/data");
    private static boolean verbose;
    static int currentType = 1;
    static int[] window = {0,0};
    public static boolean[] segmentResponses = {false};
    private static List<String> headers;
    public static ArrayList<Packet> receiveBuffer = new ArrayList<Packet>();
    private static SocketAddress ROUTER_ADDR = new InetSocketAddress("localhost", 3000);
    private static InetSocketAddress CLIENT_ADDR = new InetSocketAddress("localhost", 41830);


    public static void main(String[] args) throws IOException {
        OptionParser optionParser= new OptionParser("vp::d::");
        OptionSet optionSet = optionParser.parse(args);

        String safePath = rootPath.toString();
        if(!rootPath.exists()) {
            rootPath.mkdir();
        }
        String userRoot = !optionSet.has("d") ? "" : optionSet.valueOf("d").toString();
        verbose = optionSet.has("v");
        if(args.length != 0 && args[0].equals("help")) {
            String helpMessage = "httpfs is a simple file server.\n" +
                    "usage: httpfs [-v] [-p PORT] [-d PATH-TO-DIR]\n" +
                    "-v Prints debugging messages.\n" +
                    "-p Specifies the port number that the server will listen and serve at.\n" +
                    "Default is 8080.\n" +
                    "-d Specifies the directory that the server will use to read/write requested files. Default is the current directory when launching the application.";
            quitServer(helpMessage);
        }

        int port = !optionSet.has("p")? 8007 : Integer.parseInt(optionSet.valueOf("p").toString());

        if (port < 1024) {
            quitServer("Illegal port used! Valid ports are 1024 and above.");
        }

        rootPath = new File(Paths.get("").toAbsolutePath().toString() + "/data/" + userRoot);
        if(!rootPath.isDirectory() || !rootPath.exists()) {
            quitServer("Invalid directory\ninitialization failed");
        }
        if(!rootPath.toString().contains(safePath) || rootPath.toString().contains("..")) {
            quitServer("Unsafe directory chosen, you may only choose folders inside the \"data\" folder.");
        }
        System.out.println("HTTPFS is now live on port " + port + "\n");

        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            System.out.println("EchoServer is listening at " + channel.getLocalAddress());
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            for (; ; ) {
                buf.clear();
                SocketAddress router = channel.receive(buf);

                // Parse a packet from the received raw data.
                buf.flip();
                if(buf.limit() < Packet.MIN_LEN) {
                    continue;
                }
                Packet packet = Packet.fromBuffer(buf);
                buf.flip();

                if(packet.getType() != currentType || packet.getSequenceNumber() < window[0] || packet.getSequenceNumber() > window[1]) {
                    continue;
                }

                if(packet.getType() == 1) {
                    System.out.println("Received SYN from " + ROUTER_ADDR);
                    currentType = 3;
                    segmentResponses = new boolean[]{false};
                    threeWayHandshake(channel, packet, router);
                }

                if(packet.getType() == 3) {
                    System.out.println("Received ACK & Request from " + ROUTER_ADDR);
                    currentType = 0;
                    String payloadString = new String(packet.getPayload(),UTF_8);
                    String[] arrOfStr = payloadString.split("\r\n", 4);
                    StringTokenizer st = new StringTokenizer(arrOfStr[0]);
                    String method = st.nextToken();
                    String fileName = st.nextToken();
                    String argument = st.nextToken();
                    String version = st.nextToken();
                    byte[] request = routeRequest(method, fileName, argument, version);
                    Packet[] packets = getPacketList(request);
                    segmentResponses = new boolean[packets.length];
                    window[0] = 0;
                    window[1] = segmentResponses.length / 2;
                    Arrays.fill(segmentResponses, false);
                    for(Packet p : packets) {
                        PacketThread pT = new PacketThread(false, channel, p);
                        pT.start();
                    }
                    while(!isFinished()) {
                        updateWindow();
                        yield();
                    }
                }
            }
        }
    }

    private static void threeWayHandshake(DatagramChannel channel, Packet packet, SocketAddress router) throws IOException {
        Packet response = packet.toBuilder()
                .setType(2)
                .setSequenceNumber(0)
                .create();
        currentType = 3;
        System.out.println("Sending SYN-ACK to:" + ROUTER_ADDR);
        new PacketThread(false, channel, response).start();
        while(!isFinished()){
            updateWindow();
            yield();
        }
    }

    public static void timer(DatagramChannel channel, Packet p ) throws IOException {
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

    public static void updateWindow() {
        System.out.println(window[0] + " <- W[0]  W[1] ->" + window[1]);
        if(segmentResponses[window[0]]){
            if(window[0] < window[1]) {
                System.out.println("W[0] + 1");
                window[0] = window[0] + 1;
            }
            if(window[1] < segmentResponses.length - 1)
            {
                System.out.println("W[1] + 1");
                window[1] = window[1] + 1;
            }
        }
        System.out.println(window[0] + "<- W[0]  W[1] ->" + window[1]);
    }

    private static byte[] routeRequest(String method, String fileName,String argument, String version) throws IOException{

        if (fileName.equals("/exit")) {
            quitServer("Server terminated successfully!");
        }
        switch (method){
            case "GET":
                return processGet(fileName, version);
            case "POST":
                return processPost(fileName,version);
        }

        return new byte[0];
    }

    private static void quitServer(String message) {
        System.out.println(message);
        System.exit(0);
    }

    private static byte[] processGet(String fileName, String version) throws IOException{
        String headers = "";

        if(!fileName.equals("/")) {
            File file = new File(rootPath + fileName);
            try {
                if(file.getPath().contains("..") || (file.exists() &&!file.canRead())) {
                    throw new RuntimeException();
                }

                byte[] fileBytes = Files.readAllBytes(file.toPath());
                StringBuilder sb = new StringBuilder();
                sb.append(version + " 200 OK\r\n");
                sb.append("Content-Type: " + Files.probeContentType(file.toPath()) +"\r\n");
                sb.append("Content-Disposition: inline\r\n");
                sb.append("Content-Length: \r\n");
                sb.append(headers);
                sb.append("Data: "+new String(fileBytes));
                sb.insert(sb.toString().indexOf("Content-Length") + "Content-Length: ".length(), (sb.length() + String.valueOf(sb.length()).length()));

                String echoString = "";
                for(String s : Files.readAllLines(file.toPath())) {
                    echoString += s +"\r\n";
                }

                echo(version + " 200 OK\r\n" +
                        "Content-Type: " + Files.probeContentType(file.toPath()) +"\r\n" +
                        "Content-Disposition: inline\r\n" +
                        "Content-Length: " + fileBytes.length + "\r\n" +
                        headers +
                        "Connection: close\r\n" +
                        "\r\n" +
                        echoString + "\r\n");

                return sb.toString().getBytes();

            }

            catch (IOException e){
                String missingPath = "\"" + file.getName() + "\" doesn't exist, please make sure the file's there or change your root directory.\r\n";
                StringBuilder sb = new StringBuilder();
                sb.append(version + " 404 Not Found\r\n");
                sb.append("Content-Type: text/html\r\n");
                sb.append("Content-Length: " + missingPath.length() + "\r\n");
                sb.append(headers);
                sb.append("\r\n");
                sb.append(missingPath + "\r\n");

                echo(version + " 404 Not Found\r\n" +
                        "Content-Type: text/html\r\n" +
                        "Content-Length: " + missingPath.length() + "\r\n" +
                        headers +
                        "\r\n" +
                        missingPath + "\r\n" +
                        "\r\n");

                return sb.toString().getBytes();
            }
            catch (RuntimeException e){
                return sendForbiddenResponse(version, headers);
            }

        }

        else {
            String availableFiles = "The following files are available at the current directory:\r\n";
            StringBuilder sb = new StringBuilder();
            File[] currentFolderFiles = new File(rootPath.toString()).listFiles();

            if(currentFolderFiles == null || currentFolderFiles.length == 0) {
                availableFiles = "There are no files available at the current directory.\r\n";
            }

            for(File f : currentFolderFiles) {
                if(!f.isDirectory()) {
                    availableFiles += (f.getName() + "\r\n");
                }else{
                    availableFiles += (f.getName() + " <directory>\r\n");
                }
            }


            sb.append(version + " 200 OK\r\n");
            sb.append("Content-Type: text/html\r\n");
            sb.append("Content-Length: " + availableFiles.length() + "\r\n");
            sb.append("Connection: close\r\n");
            sb.append("\r\n");
            sb.append(availableFiles + "\r\n");

            echo(version + " 200 OK\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Content-Length: " + availableFiles.length() + "\r\n" +
                    headers +
                    "Connection: close\r\n" +
                    "\r\n" +
                    availableFiles + "\r\n" +
                    "\r\n");

            return sb.toString().getBytes();
        }
    }

    private static byte[] processPost(String fileName, String version) throws IOException{
        String headers = getHeaders(true);
        if(!fileName.equals("/")) {
            try {
                if(fileName.contains("..")) {
                    throw new RuntimeException();
                }
                int currChar;
                String data = "";
                while(input.ready() && (currChar = input.read()) != -1) {
                    data += (char)currChar;
                }

                String[] parts = fileName.split("(?=/)");
                if(parts.length > 1){
                    File folderDirectory = rootPath;
                    for(int i =0; i+1<parts.length ; i++)
                    {
                        folderDirectory = new File(folderDirectory + parts[i]);
                        if (! folderDirectory.exists()){
                            folderDirectory.mkdir();
                        }
                    }

                }

                File file = new File(rootPath +fileName);

                if(file.exists() && !file.canWrite()) {
                    throw new RuntimeException();
                }
                FileWriter fileWriter = new FileWriter(file.getAbsoluteFile());
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriter.write(data);
                bufferedWriter.close();
                fileWriter.close();

                StringBuilder sb = new StringBuilder();

                String response = "File Created.\r\n";

                sb.append(version + " 201 Created\r\n");
                sb.append("Content-Type: text/html\r\n");
                sb.append("Content-Length: " + response.length() + "\r\n");
                sb.append(headers);
                sb.append("Connection: close\r\n");
                sb.append("\r\n");
                sb.append(response);

                echo(version + " 201 Created\r\n" +
                        "Content-Type: text/html\r\n" +
                        "Content-Length: " + response.length() + "\r\n" +
                        headers +
                        "Connection: close\r\n" +
                        "\r\n" +
                        response + "\r\n");

                return sb.toString().getBytes();
            }

            catch (RuntimeException e){
                return sendForbiddenResponse(version, headers);
            }

            catch (IOException e){
                StringBuilder sb = new StringBuilder();
                String rightFormat = "Please make sure that you send data in the right format.\r\n";

                sb.append(version + " 400 Bad Request\r\n");
                sb.append("Content-Type: text/html\r\n");
                sb.append("Content-Length: " + rightFormat.length() + "\r\n");
                sb.append(headers);
                sb.append("Connection: close\r\n");
                sb.append("\r\n");
                sb.append(rightFormat);

                echo(version + " 400 Bad Request\r\n" +
                        "Content-Type: text/html\r\n" +
                        "Content-Length: " + rightFormat.length() + "\r\n" +
                        headers +
                        "Connection: close\r\n" +
                        "\r\n" +
                        rightFormat + "\r\n" +
                        "\r\n");

                return sb.toString().getBytes();
            }

        }else {
            String rightPath ="Please Make sure to add path and file name with the host.\r\n";
            StringBuilder sb = new StringBuilder();

            sb.append(version + " 400 Bad Request\r\n");
            sb.append("Content-Type: text/html\r\n");
            sb.append("Content-Length: " + rightPath.length() + "\r\n");
            sb.append(headers);
            sb.append("Connection: close\r\n");
            sb.append("\r\n");
            sb.append(rightPath);

            echo(version + " 400 Bad Request\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Content-Length: " + rightPath.length() + "\r\n" +
                    headers +
                    "Connection: close\r\n" +
                    "\r\n" +
                    rightPath + "\r\n" +
                    "\r\n");

            return sb.toString().getBytes();
        }
    }

    private static byte[] sendForbiddenResponse(String version, String headers) throws IOException {
        String errorMessage = "You don't have the permissions to access this.\r\n";
        StringBuilder sb = new StringBuilder();

        sb.append(version + " 403 Forbidden\r\n");
        sb.append("Content-Type: text/html\r\n");
        sb.append("Content-Length: " + errorMessage.length() + "\r\n");
        sb.append(headers);
        sb.append("\r\n");
        sb.append(errorMessage + "\r\n");

        echo(version + " 403 Forbidden\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + errorMessage.length() + "\r\n" +
                headers +
                "\r\n" +
                errorMessage + "\r\n" +
                "\r\n");

        return sb.toString().getBytes();
    }
    private synchronized static void echo(String s) {
        if(verbose) {
            System.out.print(s);
        }
    }

    public static boolean isFinished(){
        for(Boolean seg : segmentResponses)
        {
            if(!seg){
                return false;
            }
        }
        return true;
    }

    private static Packet[] getPacketList(byte[] payload) {
        double totalSize = payload.length;
        double numOfPackets = Math.ceil(totalSize/1013);
        Packet[] packets = new Packet[(int)numOfPackets];
        for(int i = 0; i < numOfPackets; i++) {
            Packet p = new Packet.Builder().setType(0).setSequenceNumber(i).setPeerAddress(CLIENT_ADDR.getAddress()).setPortNumber(CLIENT_ADDR.getPort()).setPayload(new byte[1013]).create();
            packets[i] = p;
        }
        int byteIndex = 0;
        for(int i = 0 ; i < packets.length; i++) {
            byte[] packetPayload = packets[i].getPayload();
            for(int j = 0; j < 1013; j++) {
                if(byteIndex > totalSize - 1) {
                    packets[i] = packets[i].toBuilder().setPayload(Arrays.copyOf(packetPayload, j)).create();
                    return packets;
                }
                packetPayload[j] = payload[byteIndex];
                byteIndex++;
            }
        }
        return packets;
    }

    private static String getHeaders(boolean post) throws IOException {
        String headers = "";
        String currHeader;

        while(input.ready() && !(currHeader = input.readLine()).equals("\r\n") && !currHeader.equals("")) {
            if(post && (currHeader.contains("Content-Length") || currHeader.contains("Content-Type"))){
                continue;
            }
                headers += currHeader + "\r\n";
        }
        return headers;
    }

    public static void ackPacket(long packetNumber) {
        segmentResponses[(int) packetNumber] = true;
    }
}
