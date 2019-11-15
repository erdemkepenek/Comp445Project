import java.io.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class httpc {
    static private HTTPClient httpClient = new HTTPClient();

    public static void httpcGet(String[] args) throws IOException{
        OptionParser optionParser = new OptionParser("vh:d::f::o::");
        OptionSet optionSet = optionParser.parse(args);
        boolean verbose = optionSet.has("v");
        httpClient.setVerbose(verbose);
        List headers = optionSet.valuesOf("h");
        if(optionSet.has("d")||optionSet.has("f")) {
            throw new RuntimeException("You cannot use this option while making a GET request.");
        }
        if(optionSet.has("o")){
            System.setOut(new PrintStream(new FileOutputStream(String.valueOf(optionSet.valuesOf("o").get(0)))));
        }
        try{
            String host;
            if(optionSet.has("o")){
                host=args[args.length-3];
            }else{
                host=args[args.length-1];
            }
            URL url = new URL(host);
            int port = url.getPort() != -1? url.getPort(): url.getDefaultPort();
            httpClient.start();
            httpClient.get(url, headers);
        }catch(MalformedURLException e) {
            System.err.println("Invalid URL provided");
        }finally {
            httpClient.end();
        }
    }


    public static void httpcPost(String[] args) throws IOException{
        String dataFile = "";
        OptionParser optionParser = new OptionParser("vh:d::f:o::");
        OptionSet optionSet = optionParser.parse(args);
        boolean verbose = optionSet.has("v");
        httpClient.setVerbose(verbose);
        List headers = optionSet.valuesOf("h");
        if((optionSet.has("d")&&optionSet.has("f")) || (!optionSet.has("d") && !optionSet.has("f"))) {
            if(optionSet.has("d") && optionSet.has("f")) {
                throw new RuntimeException("You cannot use both -d, -f options while making a Post request.");
            }
            if(!optionSet.has("d") && !optionSet.has("f")){
                throw new RuntimeException("You must use at least one of the -d, -f options while making a Post request.");
            }
        }
        if(optionSet.has("d")){
            dataFile = String.valueOf(optionSet.valuesOf("d").get(0));
        }
        if(optionSet.has("f")) {
            BufferedReader br = new BufferedReader(new FileReader(String.valueOf(optionSet.valuesOf("f").get(0))));
            do {
                dataFile = dataFile + br.readLine();
            } while ((br.readLine()) != null);
        }
        if(optionSet.has("o")){
            System.setOut(new PrintStream(new FileOutputStream(String.valueOf(optionSet.valuesOf("o").get(0)))));
        }
        try{
            String host;
            if(optionSet.has("o")){
                host=args[args.length-3];
            }else{
                host=args[args.length-1];
            }
            URL url = new URL(host);
            int port = url.getPort() != -1? url.getPort(): url.getDefaultPort();
            httpClient.start();
            httpClient.post(url, headers,dataFile);
        }catch(MalformedURLException e) {
            System.err.println("Invalid URL provided");
        }finally {
            httpClient.end();
        }

    }

    public static void main(String[] args) throws IOException{
        if(args.length == 0) {
            System.err.println("No arguments entered, please retry with the correct arguments");
            return;
        }
        try{
            checkFirstArgument(args);
        }catch (Exception e) {
            System.out.println(e.getMessage() +"\nConnection closed");
        }
    }

    private static void checkFirstArgument(String[] args) throws IOException{
        switch(args[0].toLowerCase()) {
            case "get":
                httpcGet(args);
                break;

            case "post":
                httpcPost(args);
                break;

            case "help":
                String command = args.length == 1 ? "general" : args[1];
                printHelp(command);
                break;

            default: System.err.println("Invalid command entered, only get, post or help are valid");
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
