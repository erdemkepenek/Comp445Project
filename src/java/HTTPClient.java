import sun.security.ssl.SSLSocketImpl;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HTTPClient {
    private Socket socket;
    private SSLSocket httpsSocket;
    private PrintWriter printWriter;
    private BufferedReader bufferedReader;
    private boolean verbose = false;
    private int redirects = 0;

    public void start(String host, int port, String scheme) throws  IOException {
        if(scheme.equals("https")) {
            httpsSocket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(InetAddress.getByName(host), port);
            printWriter = new PrintWriter(httpsSocket.getOutputStream(), true);
            bufferedReader = new BufferedReader(new InputStreamReader(httpsSocket.getInputStream(), StandardCharsets.UTF_8));
        }else{
            socket = new Socket(InetAddress.getByName(host), port);
            printWriter = new PrintWriter(socket.getOutputStream(), true);
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        }
    }

    public void end() throws  IOException{
        if(socket !=null || httpsSocket !=null) {
            if(socket != null){
                socket.close();
            }
            if(httpsSocket != null) {
                httpsSocket.close();
            }
            printWriter.close();
            bufferedReader.close();
        }
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
        verifyStatusCode(headers, null);

        if(!verbose) {
            String responseLine = bufferedReader.readLine() != null? bufferedReader.readLine(): "";
            while (!responseLine.equals("")) {
                responseLine = bufferedReader.readLine();
            }
        }

        bufferedReader.lines().forEach(System.out::println);
    }

    private void verifyStatusCode(List<String> headers, String data) throws IOException {
        String statusCode = bufferedReader.readLine();
        if(statusCode.contains("301") || statusCode.contains("302")) {
            redirect(headers, data);
        }else{
            bufferedReader.reset();
        }
    }

    private void redirect(List<String> headers, String data) throws IOException {
        redirects ++;
        if(redirects > 5) {
            throw new RuntimeException("The server requested a redirect over 5 times, this might be due to an infinite redirect loop.");
        }
        String newLocation = bufferedReader.readLine();
        while(!newLocation.contains("Location")) {
            newLocation = bufferedReader.readLine();
        }
        String newHost = newLocation.substring(newLocation.indexOf(' ')+1);
        if(!newHost.contains("http")) {
            newHost = socket != null? "http://" + socket.getInetAddress().getHostName() + newHost: "https://" + httpsSocket.getInetAddress().getHostName() + newHost;
        }
        URL redirectURL = new URL(newHost);
        end();
        int port = redirectURL.getPort() != -1? redirectURL.getPort(): redirectURL.getDefaultPort();
        start(redirectURL.getHost(), port, redirectURL.getProtocol());
        if(data!=null) {
            post(redirectURL, headers, data);
        }else {
            get(redirectURL, headers);
        }
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
        verifyStatusCode(headers, data);
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
            //trying to establish connection to the server
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
            client.start(urlGet.getHost(), getPort, urlGet.getProtocol());
            client.get(urlGet, headers);
            client.end();
            System.out.println("\nTEST POST REQUEST");
            client.start(urlPost.getHost(), postPort, urlPost.getProtocol());
            client.post(urlPost,headers,data);
        } catch (UnknownHostException e) {
            System.err.println("The Connection has not been made");
        } catch (IOException e) {
            System.err.println("Connection is not established, because the server may have problems."+e.getMessage());
        } finally {
            client.end();
        }

    }
}