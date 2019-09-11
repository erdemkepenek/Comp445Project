import java.io.*;
import java.net.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
    public void get() throws IOException{
        printWriter.println("GET /status/418 HTTP/1.0");
        printWriter.println("Host: httpbin.org");
        printWriter.println("");
        printWriter.flush();

        bufferedReader.lines().forEach(System.out::println);
    }

    //TODO: Serve Post Requests
    public void post() {
        printWriter.println("POST /post HTTP/1.0");
        printWriter.println("Host: httpbin.org");
        printWriter.flush();

        bufferedReader.lines().forEach(System.out::println);
    }

    public static void main(String[] args) throws IOException{
        HTTPClient client = new HTTPClient();
        try {
            //trying to establish connection to the server
            client.start("httpbin.org",80);
            client.post();
        } catch (UnknownHostException e) {
            System.err.println("The Connection has not been made");
        } catch (IOException e) {
            System.err.println("Connection is not established, because the server may have problems."+e.getMessage());
        } finally {
            client.end();
        }

    }
}