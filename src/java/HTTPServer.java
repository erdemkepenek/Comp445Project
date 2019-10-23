import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.StringTokenizer;

public class HTTPServer {

    private static Socket client;
    private static DataOutputStream output;
    private static BufferedReader input;
    private static PrintWriter writer;
    private static File rootPath = new File(Paths.get("").toAbsolutePath().toString() + "/data");
    private static String data = "";

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(8080);
        while(true){
            client = server.accept();
            input = new BufferedReader(new InputStreamReader(client.getInputStream()));
            output = new DataOutputStream(client.getOutputStream());
            writer =  new PrintWriter(client.getOutputStream());
            routeRequest(input.readLine());
        }
    }
    private static void routeRequest(String requestLine) throws IOException{
        StringTokenizer st = new StringTokenizer(requestLine);
        String method = st.nextToken();
        String fileName = st.nextToken();
        String version = st.nextToken();
        String data ="{\"Assignment\":\"1\"}";
        switch (method){
            case "GET": processGet(fileName, version); break;
            case "POST": processPost(fileName,version); break;
        }
    }
    private static void processGet(String fileName, String version) throws IOException{
        if(!fileName.equals("/")) {
            File file = new File(rootPath + fileName);
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                output.writeBytes(version + " 200 OK\r\n");
                output.writeBytes("Content-Type: text/html\r\n");
                output.writeBytes("Content-Length: " + fileBytes.length + "\r\n");
                output.writeBytes("Connection: close\r\n");
                output.writeBytes("\r\n");
                output.write(fileBytes);
                output.flush();
            }
            catch (Exception e){
                String missingPath = file.getPath().toString() + " doesn't exist, please make sure the file's there or change your root directory.\r\n";

                output.writeBytes(version + " 404 Not Found\r\n");
                output.writeBytes("Content-Type: text/tml\r\n");
                output.writeBytes("Content-Length: " + missingPath.length() + "\r\n");
                output.writeBytes("\r\n");
                output.writeBytes(missingPath + "\r\n");
                output.flush();
            }
        }
        else {
            String availableFiles = "The following files are available at the current directory:\r\n";

            for(String name : rootPath.list()) {
                availableFiles += (name +"\r\n");
            }
            if(rootPath.list().length == 0) {
                availableFiles = "There are no files available at the current directory.\r\n";
            }

            output.writeBytes(version + " 200 OK\r\n");
            output.writeBytes("Content-Type: text/html\r\n");
            output.writeBytes("Content-Length: " + availableFiles.length() + "\r\n");
            output.writeBytes("Connection: close\r\n");
            output.writeBytes("\r\n");
            output.writeBytes(availableFiles + "\r\n");
            output.flush();
        }
    }

    private static void processPost(String fileName, String version) throws IOException{
        if(!fileName.equals("/")) {
            try {
                String response = "File Created.\r\n";
                byte[] bytes = response.getBytes();
                /*byte[] bytes = bos.toByteArray();*/
                output.writeBytes(version + " 200 OK\r\n");
                output.writeBytes("Content-Type: application/json\r\n");
                output.writeBytes("Content-Length: " + bytes.length + "\r\n");
                output.writeBytes("Connection: close\r\n");
                output.writeBytes("\r\n");
                output.write(bytes);
                output.flush();
                ArrayList<String> line = new ArrayList<String>();
                do{
                    line.add(input.readLine());
                }
                while (input.readLine() != null);
                data = line.get(line.size()-1);
                String[] parts = fileName.split("(?=/)");
                if(parts.length > 1){
                    File folderDirectory=rootPath;
                    for(int i =0; i+1<parts.length ; i++)
                    {
                        folderDirectory = new File(folderDirectory+parts[i]);
                        if (! folderDirectory.exists()){
                            folderDirectory.mkdir();
                        }
                    }

                }
                File file = new File(rootPath +fileName);
                FileWriter fileWriter = new FileWriter(file.getAbsoluteFile());
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriter.write(data);
                bufferedWriter.close();
                fileWriter.close();
            }
            catch (Exception e){
                String rightFormat = "Please make sure that you send data in the right format.\r\n";
                byte[] bytes = rightFormat.getBytes();
                output.writeBytes(version + " 404 Not Found\r\n");
                output.writeBytes("Content-Type: application/json\r\n");
                output.writeBytes("Content-Length: " + bytes.length + "\r\n");
                output.writeBytes("Connection: close\r\n");
                output.writeBytes("\r\n");
                output.write(bytes);
                output.flush();
            }
        }else {
            String rightPath ="Please Make sure to add path and file name with the host.\r\n";

            byte[] bytes = rightPath.getBytes();
            output.writeBytes(version + " 200 OK\r\n");
            output.writeBytes("Content-Type: application/json\r\n");
            output.writeBytes("Content-Length: " + bytes.length + "\r\n");
            output.writeBytes("Connection: close\r\n");
            output.writeBytes("\r\n");
            output.write(bytes);
            output.flush();
        }
    }
}
