import joptsimple.OptionParser;
import joptsimple.OptionSet;


import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.StringTokenizer;

public class HTTPServer implements Runnable {

    private static Socket client;
    private static DataOutputStream output;
    private static BufferedReader input;
    private static PrintWriter writer;
    private static File rootPath = new File(Paths.get("").toAbsolutePath().toString() + "/data");
    private static boolean verbose;
    private static ArrayList<String> readList = new ArrayList<String>();
    private static ArrayList<String> writeList = new ArrayList<>();

    public HTTPServer(Socket s){
        client=s;
    }

    public synchronized void run(){
        try {
            input = new BufferedReader(new InputStreamReader(client.getInputStream()));
            output = new DataOutputStream(client.getOutputStream());
            writer =  new PrintWriter(client.getOutputStream());
            routeRequest(input.readLine());
            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

        int port = !optionSet.has("p")? 8080 : Integer.parseInt(optionSet.valueOf("p").toString());

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
        ServerSocket server = new ServerSocket(port);
        System.out.println("HTTPFS is now live on port " + port + "\n");

        while(true){
            HTTPServer httpServer = new HTTPServer(server.accept());
            Thread thread = new Thread(httpServer);
            thread.start();
        }
    }
    private static void routeRequest(String requestLine) throws IOException{
        StringTokenizer st = new StringTokenizer(requestLine);
        String method = st.nextToken();
        String fileName = st.nextToken();
        String version = st.nextToken();

        if (fileName.equals("/exit")) {
            quitServer("Server terminated successfully!");
        }
        switch (method){
            case "GET": processGet(fileName, version); break;
            case "POST": processPost(fileName,version); break;
        }

    }

    private static void quitServer(String message) {
        System.out.println(message);
        System.exit(0);
    }

    private static void processGet(String fileName, String version) throws IOException{
        String headers = getHeaders(false);
        while(writeList.contains(fileName)){

        }
        readList.add(fileName);
        if(!fileName.equals("/")) {
            File file = new File(rootPath + fileName);
            try {
                if(file.getPath().contains("..") || (file.exists() &&!file.canRead())) {
                    throw new RuntimeException();
                }

                byte[] fileBytes = Files.readAllBytes(file.toPath());

                output.writeBytes(version + " 200 OK\r\n");
                output.writeBytes("Content-Type: " + Files.probeContentType(file.toPath()) +"\r\n");
                output.writeBytes("Content-Disposition: inline");
                output.writeBytes(headers);
                output.writeBytes("Content-Length: " + fileBytes.length + "\r\n");
                output.writeBytes("Connection: close\r\n");
                output.writeBytes("\r\n");
                output.write(fileBytes);

                String echoString = "";
                for(String s : Files.readAllLines(file.toPath())) {
                    echoString += s +"\r\n";
                }

                echo(version + " 200 OK\r\n" +
                        "Content-Type: " + Files.probeContentType(file.toPath()) +"\r\n" +
                        "Content-Disposition: inline\r\n" +
                        headers +
                        "Content-Length: " + fileBytes.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n" +
                        echoString + "\r\n");

                output.flush();
            }

            catch (IOException e){
                String missingPath = "\"" + file.getName() + "\" doesn't exist, please make sure the file's there or change your root directory.\r\n";

                output.writeBytes(version + " 404 Not Found\r\n");
                output.writeBytes("Content-Type: text/html\r\n");
                output.writeBytes(headers);
                output.writeBytes("Content-Length: " + missingPath.length() + "\r\n");
                output.writeBytes("\r\n");
                output.writeBytes(missingPath + "\r\n");

                echo(version + " 404 Not Found\r\n" +
                        "Content-Type: text/html\r\n" +
                        "Content-Length: " + missingPath.length() + "\r\n" +
                        headers +
                        "\r\n" +
                        missingPath + "\r\n" +
                        "\r\n");

                output.flush();
            }
            catch (RuntimeException e){
                sendForbiddenResponse(version, headers);
            }
            readList.remove(fileName);
        }
        else {
            String availableFiles = "The following files are available at the current directory:\r\n";

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


            output.writeBytes(version + " 200 OK\r\n");
            output.writeBytes("Content-Type: text/html\r\n");
            output.writeBytes("Content-Length: " + availableFiles.length() + "\r\n");
            output.writeBytes("Connection: close\r\n");
            output.writeBytes("\r\n");
            output.writeBytes(availableFiles + "\r\n");

            echo(version + " 200 OK\r\n" +
                    "Content-Type: text/html\r\n" +
                    "Content-Length: " + availableFiles.length() + "\r\n" +
                    headers +
                    "Connection: close\r\n" +
                    "\r\n" +
                    availableFiles + "\r\n" +
                    "\r\n");

            output.flush();
        }
    }

    private static void processPost(String fileName, String version) throws IOException{
        String headers = getHeaders(true);
        while(readList.contains(fileName) || writeList.contains(fileName)){

        }
        writeList.add(fileName);
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

                String response = "File Created.\r\n";
                byte[] bytes = response.getBytes();

                output.writeBytes(version + " 201 Created\r\n");
                output.writeBytes(headers);
                output.writeBytes("Content-Type: text/html\r\n");
                output.writeBytes("Content-Length: " + bytes.length + "\r\n");
                output.writeBytes("Connection: close\r\n");
                output.writeBytes("\r\n");
                output.write(bytes);

                echo(version + " 201 Created\r\n" +
                        headers +
                        "Content-Type: text/html\r\n" +
                        "Content-Length: " + bytes.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n" +
                        response + "\r\n");

                output.flush();
            }

            catch (RuntimeException e){
                sendForbiddenResponse(version, headers);
            }

            catch (IOException e){
                String rightFormat = "Please make sure that you send data in the right format.\r\n";
                byte[] bytes = rightFormat.getBytes();

                output.writeBytes(version + " 400 Bad Request\r\n");
                output.writeBytes("Content-Type: text/html\r\n");
                output.writeBytes(headers);
                output.writeBytes("Content-Length: " + bytes.length + "\r\n");
                output.writeBytes("Connection: close\r\n");
                output.writeBytes("\r\n");
                output.write(bytes);

                echo(version + " 400 Bad Request\r\n" +
                        "Content-Type: text/html\r\n" +
                        headers +
                        "Content-Length: " + bytes.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n" +
                        rightFormat + "\r\n" +
                        "\r\n");
                output.flush();
            }
            writeList.remove(fileName);

        }else {
            String rightPath ="Please Make sure to add path and file name with the host.\r\n";
            byte[] bytes = rightPath.getBytes();

            output.writeBytes(version + " 400 Bad Request\r\n");
            output.writeBytes("Content-Type: text/html\r\n");
            output.writeBytes(headers);
            output.writeBytes("Content-Length: " + bytes.length + "\r\n");
            output.writeBytes("Connection: close\r\n");
            output.writeBytes("\r\n");
            output.write(bytes);

            echo(version + " 400 Bad Request\r\n" +
                    "Content-Type: text/html\r\n" +
                    headers +
                    "Content-Length: " + bytes.length + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    rightPath + "\r\n" +
                    "\r\n");

            output.flush();
        }
    }

    private static void sendForbiddenResponse(String version, String headers) throws IOException {
        String errorMessage = "You don't have the permissions to access this.\r\n";

        output.writeBytes(version + " 403 Forbidden\r\n");
        output.writeBytes("Content-Type: text/html\r\n");
        output.writeBytes(headers);
        output.writeBytes("Content-Length: " + errorMessage.length() + "\r\n");
        output.writeBytes("\r\n");
        output.writeBytes(errorMessage + "\r\n");

        echo(version + " 403 Forbidden\r\n" +
                "Content-Type: text/html\r\n" +
                headers +
                "Content-Length: " + errorMessage.length() + "\r\n" +
                "\r\n" +
                errorMessage + "\r\n" +
                "\r\n");

        output.flush();
    }
    private synchronized static void echo(String s) {
        if(verbose) {
            System.out.print(s);
        }
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
}
