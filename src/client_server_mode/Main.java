package client_server_mode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static Logger LOGGER;
    public static void main(String[] args) throws Exception {
        if(args.length != 1){
            System.out.println("Por favor ingrese el modo en el que quiere ejecutar");
            return;
        }

        String modo = args[0];
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        BufferedReader br2 = new BufferedReader(new InputStreamReader(System.in));
        BufferedReader br3 = new BufferedReader(new InputStreamReader(System.in));
        String pn = "";
        String ipA = "";
        String fileName = "";
        int port = 8080;
        Server s;
        Client c;
        switch (modo.toLowerCase()){
            case "cliente":
                        LOGGER = Logger.getLogger(Client.class.getName());
                        ConsoleHandler consoleHandler = new ConsoleHandler();
                        FileHandler fileHandler = new FileHandler(Client.class.getName()+".txt");
                        consoleHandler.setLevel(Level.ALL);
                        fileHandler.setLevel(Level.ALL);
                        consoleHandler.setFormatter(new FormatLogger());
                        fileHandler.setFormatter(new FormatLogger());
                        LOGGER.addHandler(consoleHandler);
                        LOGGER.addHandler(fileHandler);
                        LOGGER.setUseParentHandlers(false);
                        /*System.out.print("Por favor ingresa numero del puerto para el server: ");
                        pn = br.readLine();
                        System.out.print("Por favor direccion ip para el server: ");
                        ipA = br2.readLine();
                        port = Integer.parseInt(pn);*/
                        System.out.print("Por favor ingrese el nombre del archivo que desea enviar: ");
                        fileName = br3.readLine();
                        //c = new Client(port, InetAddress.getByName(ipA), fileName);
                        c = new Client(13849, "13.59.15.185", fileName);
                        LOGGER.log(Level.INFO, "Se inicio el programa como modo CLIENTE");
                        break;
            case "servidor":
                        LOGGER = Logger.getLogger(Server.class.getName());
                        ConsoleHandler consoleHandler1 = new ConsoleHandler();
                        FileHandler fileHandler1 = new FileHandler(Server.class.getName()+".txt");
                        consoleHandler1.setLevel(Level.ALL);
                        fileHandler1.setLevel(Level.ALL);
                        consoleHandler1.setFormatter(new FormatLogger());
                        fileHandler1.setFormatter(new FormatLogger());
                        LOGGER.addHandler(consoleHandler1);
                        LOGGER.addHandler(fileHandler1);
                        LOGGER.setUseParentHandlers(false);
                        System.out.print("Por favor ingresa numero de puerto a conectarse: ");
                        pn = br.readLine();
                        port = Integer.parseInt(pn);
                        s = new Server(port);
                        LOGGER.log(Level.INFO, "Se inicio el programa como modo SERVIDOR");
                        break;
            case "server":
                    LOGGER = Logger.getLogger(Server.class.getName());
                    ConsoleHandler consoleHandler2 = new ConsoleHandler();
                    FileHandler fileHandler2 = new FileHandler(Server.class.getName()+".txt");
                    consoleHandler2.setLevel(Level.ALL);
                    fileHandler2.setLevel(Level.ALL);
                    consoleHandler2.setFormatter(new FormatLogger());
                    fileHandler2.setFormatter(new FormatLogger());
                    LOGGER.addHandler(consoleHandler2);
                    LOGGER.addHandler(fileHandler2);
                    LOGGER.setUseParentHandlers(false);
                    System.out.print("Por favor ingresa numero de puerto a conectarse: ");
                    pn = br.readLine();
                    port = Integer.parseInt(pn);
                    s = new Server(port);
                    LOGGER.log(Level.INFO, "Se inicio el programa como modo SERVIDOR");
                    break;

            case "client":
                    LOGGER = Logger.getLogger(Client.class.getName());
                    ConsoleHandler consoleHandler3 = new ConsoleHandler();
                    FileHandler fileHandler3 = new FileHandler(Client.class.getName()+".txt");
                    consoleHandler3.setLevel(Level.ALL);
                    fileHandler3.setLevel(Level.ALL);
                    consoleHandler3.setFormatter(new FormatLogger());
                    fileHandler3.setFormatter(new FormatLogger());
                    LOGGER.addHandler(consoleHandler3);
                    LOGGER.addHandler(fileHandler3);
                    LOGGER.setUseParentHandlers(false);
                    System.out.print("Por favor ingresa numero del puerto para el server: ");
                    pn = br.readLine();
                    //System.out.print("Por favor direccion ip para el server: ");
                    //ipA = br2.readLine();
                    port = Integer.parseInt(pn);
                    System.out.print("Por favor ingrese el nombre del archivo que desea enviar: ");
                    fileName = br3.readLine();
                    //c = new Client(6666, "127.0.0.1", fileName);
                    c = new Client(port, "127.0.0.1", fileName);
                    LOGGER.log(Level.INFO, "Se inicio el programa como modo CLIENTE");
                    break;
            default:
                System.out.println("El modo debe de ser cliente o servidor");
                break;
        }

    }
}
