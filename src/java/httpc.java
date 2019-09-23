import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class httpc {
    static HTTPClient httpClient = new HTTPClient();

    public static void httpcGet(String[] args) throws IOException{
        OptionParser optionParser = new OptionParser("vh:");
        OptionSet optionSet = optionParser.parse(args);
        boolean verbose = optionSet.has("v");
        List headers = optionSet.valuesOf("h");
        try{
            URL url = new URL(args[args.length-1]);
            int port = url.getPort() != -1? url.getPort(): url.getDefaultPort();
            httpClient.start(url.getHost(), port);
            httpClient.get(url, headers);
            httpClient.end();
        }catch(MalformedURLException e) {
            System.err.println("Invalid URL provided");
        }

    }


    public static void httpcPost(String[] args){

    }

    public static void main(String[] args) throws IOException{
        checkFirstArgument(args);


    }

    private static void checkFirstArgument(String[] args) throws IOException{
        if(args[0].equals("get")) {
            httpcGet(args);
        }

        if(args[0].equals("post")) {
            httpcPost(args);
        }

        if(args[0].equals("help")) {
            String command = args.length == 1? "general": args[1];
            printHelp(command);
        }
    }

    private static void printHelp(String command) {
        switch(command){
            case "general":
                showGeneralHelp();
                break;

            case "get":
                showGetHelp();
                break;

            case "post":
                showPostHelp();
                break;

            default:
                System.out.println("Invalid help command entered, either ommit it or enter \"get\" or \"post\".");
        }
    }

    private static void showPostHelp() {
        System.out.println("\nUsage: httpc post [-v] [-h key:value] [-d inline-data] [-f file] URL");
        System.out.println("\nPost executes a HTTP POST request for a given URL with inline data or from file.");
        System.out.println("\n\t-v              Prints the detail of the response such as protocol, status and headers.");
        System.out.println("\t-h key:value\tAssociates headers to HTTP Request with the format \'key: value\'.");
        System.out.println("\t-d string       Associates an inline data to the body HTTP POST request.");
        System.out.println("\t-f file         Associates the content of a file to the body HTTP POST request.");
        System.out.println("\nEither [-d] or [-f] can be used but not both.");
    }

    private static void showGetHelp() {
        System.out.println("\nUsage: httpc get [-v] [-h key:value] URL");
        System.out.println("\nGet executes a HTTP GET request for a given URL.");
        System.out.println("\n\t-v              Prints the detail of the response such as protocol, status and headers.");
        System.out.println("\t-h key:value\tAssociates headers to HTTP Request with the format \'key: value\'.");
    }

    private static void showGeneralHelp() {
        System.out.println("\nhttpc is a curl-like application but supports HTTP protocol only.");
        System.out.println("Usage:");
        System.out.println("\thttpc command [arguments].");
        System.out.println("The commands are:");
        System.out.println("\tget     executes a HTTP GET request and prints the response.");
        System.out.println("\tpost\texecutes a HTTP POST request and prints the response.");
        System.out.println("\thelp\tprints this screen.");
        System.out.println("\nUse \"httpc help [command]\" for more information about a command.");
    }
}
