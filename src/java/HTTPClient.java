import java.io.*;
import java.net.*;
import java.io.IOException;

public class HTTPClient {
    private Socket socket;
    private PrintWriter printWriter;
    private BufferedReader bufferedReader;

    //TODO: Start Client
    public void start(String host, int port) throws  IOException{
        socket = new Socket(host, port);
        System.out.println("hello");
        printWriter = new PrintWriter(socket.getOutputStream(), true);
        bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

    }

    //TODO: Close Client
    public void end() throws  IOException{
        socket.close();
        printWriter.close();
        bufferedReader.close();

    }

    //TODO: Serve Get Requests
    public String get() {
        return null;
    }

    //TODO: Serve Post Requests
    public String post(String msg) {
        printWriter.println(msg);
        
    }

    public static void main(String[] args) {
        HTTPClient client = new HTTPClient();
        try {
            //trying to establish connection to the server
            client.start("httpbin.org",80);

        } catch (UnknownHostException e) {
            System.err.println("The Connection has not been made");
        } catch (IOException e) {
            System.err.println("Connection is not established, because the server may have problems."+e.getMessage());
        }

    }
}