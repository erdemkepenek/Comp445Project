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
    private boolean verbose = false;

    //TODO: Start Client
    public void start(String host, int port) throws  IOException {
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
    public void get(URL url, List<String> headers) throws IOException{
        String queryString = url.getQuery() != null? "?" + url.getQuery(): "";
        printWriter.print("GET "+ url.getPath() + queryString + " HTTP/1.0\r\n");
        printWriter.print("Host: " + url.getHost() + "\r\n");
        if(headers != null)
            headers.forEach(printWriter::println);
        printWriter.print("\r\n");
        printWriter.flush();

        if(!verbose) {
            String responseLine = bufferedReader.readLine() != null? bufferedReader.readLine(): "";
            while (!responseLine.equals("")) {
                responseLine = bufferedReader.readLine();
            }
        }

        bufferedReader.lines().forEach(System.out::println);
    }


    //TODO: Serve Post Requests
    public void post(String host, String path, List<String> headers, String params, String data) throws IOException {
        printWriter.println("POST "+ path + params+" HTTP/1.0");
        printWriter.println("Host: "+ host);
        printWriter.println("Content-Length: "+data.length());
        printWriter.println("Content-Type: application/json");
        if(headers != null)
            headers.forEach(printWriter::println);
        printWriter.println("");
        printWriter.println(data);
        printWriter.flush();

        bufferedReader.lines().forEach(System.out::println);
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public static void main(String[] args) throws IOException{
        HTTPClient client = new HTTPClient();
        try {
            //trying to establish connection to the server
            ArrayList<String> headers = new ArrayList<>();
            URL url = new URL("http://httpbin.org/get?hello=true");
            int port = url.getPort() != -1? url.getPort(): url.getDefaultPort();
            headers.add("User-Agent: Concordia-HTTP/1.0");
            client.start(url.getHost(), port);
            //client.post(client.socket.getInetAddress().getHostName(), "/post", headers, "?hello=true", "{\"Assignment\":\"1\"}");
            client.get(url, headers);
        } catch (UnknownHostException e) {
            System.err.println("The Connection has not been made");
        } catch (IOException e) {
            System.err.println("Connection is not established, because the server may have problems."+e.getMessage());
        } finally {
            client.end();
        }

    }
}