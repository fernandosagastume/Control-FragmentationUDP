package client_server_mode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;

public class Main {
    public static void main(String[] args) throws Exception{
        if(args.length != 1){
            System.out.println("Por favor ingrese el modo en el que quiere ejecutar");
            return;
        }

        String modo = args[0];
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        BufferedReader br2 = new BufferedReader(new InputStreamReader(System.in));
        String pn = "";
        String ipA = "";
        int port = 8080;
        Server s;
        Client c;
        switch (modo.toLowerCase()){
            case "cliente":
                System.out.print("Por favor ingresa numero del puerto para el server: ");
                pn = br.readLine();
                System.out.print("Por favor direccion ip para el server: ");
                ipA = br2.readLine();
                port = Integer.parseInt(pn);
                c = new Client(port,InetAddress.getLocalHost());
                break;
            case "servidor":
                System.out.print("Por favor ingresa numero a conectarse: ");
                pn = br.readLine();
                port = Integer.parseInt(pn);
                s = new Server(port);
                break;
            case "server":
                System.out.print("Por favor ingresa numero a conectarse: ");
                pn = br.readLine();
                port = Integer.parseInt(pn);
                s = new Server(port);
                break;
            case "client":
                System.out.print("Por favor ingresa numero del puerto para el server: ");
                pn = br.readLine();
                System.out.print("Por favor direccion ip para el server: ");
                ipA = br2.readLine();
                port = Integer.parseInt(pn);
                c = new Client(port,InetAddress.getLocalHost());
                break;
            default:
                System.out.println("El modo debe de ser cliente o servidor");
                break;
        }

    }
}
