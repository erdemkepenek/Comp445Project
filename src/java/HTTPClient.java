import java.io.*;
import java.net.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HTTPClient {
    private Socket socket;
    private PrintWriter printWriter;
    private BufferedReader bufferedReader;

    //TODO: Start Client
    public void start(String host, int port) throws  IOException{
        socket = new Socket(host, port);
        printWriter = new PrintWriter(socket.getOutputStream(), true);
        bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    //TODO: Close Client
    public void end() throws  IOException{
        socket.close();
        printWriter.close();
        bufferedReader.close();
    }

    //TODO: Serve Get Requests
    public void get(String host, String path, List<String> headers, String params) throws IOException{
        printWriter.println("GET "+ path + params + " HTTP/1.1");
        printWriter.println("Host: " + host);
        if(headers != null)
            headers.forEach(printWriter::println);
        printWriter.println("");
        printWriter.flush();

        bufferedReader.lines().forEach(System.out::println);
    }

    //TODO: Serve Post Requests
    public void post(String data) throws IOException {
        printWriter.println("POST /post HTTP/1.0");
        printWriter.println("Host: httpbin.org");
        printWriter.println("Content-Length: "+data.length());
        printWriter.println("Content-Type: application/json");
        printWriter.println("");
        printWriter.println(data);
        printWriter.flush();

       /* System.out.println(bufferedReader.readLine());*/

        bufferedReader.lines().forEach(System.out::println);
    }

    public static void main(String[] args) throws IOException{
        HTTPClient client = new HTTPClient();
        try {
            //trying to establish connection to the server
            ArrayList<String> headers = new ArrayList<>();
            headers.add("User-Agent: Concordia-HTTP/1.0");
            client.start("httpbin.org",80);
            client.post("{\"Assignment\":\"1\"}");
            client.get(client.socket.getInetAddress().getHostName(), "/get", headers, "?hello=true");
        } catch (UnknownHostException e) {
            System.err.println("The Connection has not been made");
        } catch (IOException e) {
            System.err.println("Connection is not established, because the server may have problems."+e.getMessage());
        } finally {
            client.end();
        }

    }
}