package client_server_mode;



import java.io.*;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Scanner;


public class Client {
    @SuppressWarnings("FieldCanBeLocal")
    private static DatagramSocket mySocket;
    private static int TCPCon_phase = 0;
    private static String sourcePort;
    private static String destinationPort;
    private static String FILE_NAME = "sample.jpg";
    public static int port;
    public static InetAddress ip;

    @SuppressWarnings("FieldMayBeFinal")
    private static Thread tcp_con = new Thread(new Runnable(){
        @Override
        public void run()  {
            int cont = 1;
            while(true) {
                //payload data size + header
                byte [] buffer = new byte[1472];
                DatagramPacket dp = new DatagramPacket(buffer,buffer.length);
                File file = new File(FILE_NAME);
                byte[] filetoBytes = new byte[(int) file.length()];
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(file);
                    fis.read(filetoBytes);
                    fis.close();

                } catch (Exception e) {
                    e.printStackTrace();
                }

                try{
                    //Se recibe la respuesta del server
                    mySocket.receive(dp);
                    //Se guarda el header recibido
                    String headerRCV = new String(buffer);
                    //Se obtiene la data y se guarda en un objeto PacketSC
                    PacketSC psc = PacketSC.buildPacket(headerRCV);

                    //Se suman los campos de paquete recibido para verificar checksum
                    String sumFields = fieldAdd(psc.SOURCE_PORT, psc.DESTINATION_PORT,
                    psc.SEQ_NUM+psc.ACK_NUM, psc.ACK_FLAG+psc.SYN_FLAG,psc.FYN_FLAG+psc.WINDOW_SIZE);
                    //Si es verdadero, significa que el checksum esta correcto (i.e. todos los bits son 1)
                    boolean correctChecksum = verifyChecksum(sumFields, psc.CHECKSUM);
                    //Si no está bien el checksum se bota el paquete
                    if(!correctChecksum)
                        continue;

                    //Connection Establishment Phase
                    if(TCPCon_phase == 1){
                        //Se verifica el formato y que coincidan los numeros en los campos
                        if(psc.ACK_NUM.equals("30") && psc.SEQ_NUM.equals("30") &&
                            psc.ACK_FLAG.equals("31") && psc.SYN_FLAG.equals("31")) {
                            PacketSC send_packet_ack = new PacketSC();
                            send_packet_ack.setSOURCE_PORT("4653");
                            send_packet_ack.setDESTINATION_PORT(psc.SOURCE_PORT);
                            send_packet_ack.setSEQ_NUM("31");
                            send_packet_ack.setACK_NUM("30");
                            send_packet_ack.setACK_FLAG("31");
                            send_packet_ack.setSYN_FLAG("30");
                            send_packet_ack.setFYN_FLAG("30");
                            send_packet_ack.setWINDOW_SIZE("31");
                            //Se realiza el checksum del paquete a enviar
                            String chsm = checksumComputation(send_packet_ack.SOURCE_PORT,
                                    send_packet_ack.DESTINATION_PORT,
                                    send_packet_ack.SEQ_NUM+send_packet_ack.ACK_NUM,
                                    send_packet_ack.ACK_FLAG+send_packet_ack.SYN_FLAG,
                                    send_packet_ack.FYN_FLAG+send_packet_ack.WINDOW_SIZE);
                            send_packet_ack.setCHECKSUM(chsm);
                            /*byte[] bt = FILE_NAME.getBytes();
                            String fn = "";
                            for (int i = 0; i < bt.length; i++) {
                                //Se convierte el nombre del archivo en hex
                                fn += String.format("%02x", bt[i]).toUpperCase();
                            }
                            send_packet_ack.setDATA(fn);*/
                            sendData(send_packet_ack.packetHEX());
                            TCPCon_phase = 2;
                        }
                    }
                    //Data Transfer Phase
                    else if(TCPCon_phase == 2){
                        /*
                        byte[] fileData;
                        String fragmentN = "";
                        if((1460*cont) < file.length()){
                                fileData = Arrays.copyOfRange(filetoBytes,(cont-1)*1460,
                                                           cont*1460);
                                final StringBuilder stb = new StringBuilder();

                                for(byte b : fileData) {
                                    stb.append(String.format("%02x", b));
                                }
                                fragmentN = stb.toString().toUpperCase();
                                cont++;
                            }
                            else{ //Ultimo fragmento del file
                                fileData = Arrays.copyOfRange(filetoBytes,(cont-1)*1460,
                                        (int) (file.length()));
                                final StringBuilder stb = new StringBuilder();

                                for(byte b : fileData) {
                                    stb.append(String.format("%02x", b));
                                }
                                fragmentN = stb.toString().toUpperCase();
                            }*/
                        TCPCon_phase = 3;
                    }
                    //Connection Termination Phase
                    else if(TCPCon_phase == 3){
                        System.out.println("Se acabo la conexion");
                    }
                }
                catch (Exception e){
                    System.out.println("No se pudo recibir el paquete" + e.getMessage());
                }

            }
        }
    });

    public Client(int port, InetAddress ip) throws Exception{
        this.port = port;
        this.ip = ip;
        mySocket = new DatagramSocket();
        tcp_con.start();
        //Mis inciales en HEX como source port
        sourcePort = "4653";
        //Son ceros porque no conozco aún el server
        destinationPort = "3030";
        //sequence number empieza en 0
        String seqNum_syn = "30";
        //Ack num 0 porque es el inicio, de la conexion
        String ackNum_syn = "30";
        //0 porque es el primer mensaje del 3-way handshake
        String ACK_FLAG = "30";
        //1 porque es el primer mensaje del 3-way handshake
        String SYN_FLAG = "31";
        //1 porque es el primer mensaje del 3-way handshake
        String FYN_FLAG = "30";
        //Window size de 1
        String WINDOW_SIZE = "31";
        //checksum en 0 porque es el primer mensaje (aun no nos ponemos de acuerdo)
        String checksum = checksumComputation(sourcePort, destinationPort, seqNum_syn+ackNum_syn,
                                                ACK_FLAG+SYN_FLAG, FYN_FLAG+WINDOW_SIZE);
        //Se contruye el header + data
        String headerData = sourcePort + destinationPort + seqNum_syn +
                ackNum_syn + ACK_FLAG + SYN_FLAG + FYN_FLAG + WINDOW_SIZE + checksum;
        //Se envía el payload + header al server (no se envía nada de data(0's))
        send(headerData);
        //Se establece la fase de establecimiento de conexion
        TCPCon_phase = 1;

    }
    private void send(String data){
        sendData(data);
    }
    // Función para enviar data al servidor
    private static void sendData(String data){
        //Se crea el packet para enviarse al servidor
        DatagramPacket dp = new DatagramPacket(data.getBytes(),data.getBytes().length,ip,port);
        try{
            mySocket.send(dp);
        }catch(IOException io){
            System.out.println("No se pudo enviar el paquete: " + io.getMessage());
        }
    }

    public static boolean verifyChecksum(String fieldSUM, String checksum){
        String chk = hexToBinary(checksum);
        String sum = binaryAdd(chk,fieldSUM);
        sum = Integer.toString(Integer.parseInt(sum,2),16).toUpperCase();
        //Si,tdo da 1's, entonces el checksum esta correcto
        return sum.equals("FFFF");
    }

    public static String fieldAdd(String hex1, String hex2, String hex3,
                                  String hex4, String hex5){
        String bin1 = hexToBinary(hex1);
        String bin2 = hexToBinary(hex2);
        String bin3 = hexToBinary(hex3);
        String bin4 = hexToBinary(hex4);
        String bin5 = hexToBinary(hex5);
        return binaryAdd5(bin1,bin2,bin3,bin4,bin5);
    }


    public static String hexToBinary(String hex){
        return completeZeros((new BigInteger(hex, 16)).toString(2));
    }

    //Devuelve el binario
    public static String checksumComputation(String hex1, String hex2, String hex3,
                                             String hex4, String hex5){
        //Se hace la suma de los campos junto con la suma del carry out
        String fieldSum = fieldAdd(hex1,hex2,hex3,hex4,hex5);
        //Se le saca el complemento a 1
        String checksumRes = complement1(fieldSum);
        //Se convierte a hex
        checksumRes = Integer.toString(Integer.parseInt(checksumRes,2),16).toUpperCase();
        return checksumRes;
    }

    public static String completeZeros(String bin){
        String res = bin;
        while(true){
            if(res.length() < 16)
                res="0"+res;
            else
                break;
        }
        return res;
    }

    public static String complement1(String bin){
        String ba = bin;
        for (int i = 0; i < ba.length(); i++) {
            if(ba.charAt(i) == '0'){
                ba = ba.substring(0, i) + '1'
                        + ba.substring(i+1);
            }
            else if(ba.charAt(i) == '1'){
                ba = ba.substring(0, i) + '0'
                        + ba.substring(i+1);
            }
        }
        return ba;
    }

    public static String binaryAdd(String bin1, String bin2) {
        if(bin1==null || bin1.length()==0)
            return bin2;
        if(bin2==null || bin2.length()==0)
            return bin1;

        int b1_len = bin1.length()-1;
        int b2_len = bin2.length()-1;

        int carry = 0;
        StringBuilder sb = new StringBuilder();
        //Se computa la suma de los dos números binarios
        while(b1_len >= 0 || b2_len >=0){
            int v_b1 = 0;
            int v_b2 = 0;

            if(b1_len >= 0){
                v_b1 = bin1.charAt(b1_len)=='0'? 0 : 1;
                b1_len--;
            }
            if(b2_len >= 0){
                v_b2 = bin2.charAt(b2_len)=='0'? 0: 1;
                b2_len--;
            }

            int suma = v_b1 + v_b2 + carry;
            if(suma >= 2){
                sb.append(String.valueOf(suma-2));
                carry = 1;
            }else{
                carry = 0;
                sb.append(String.valueOf(suma));
            }
        }
        //Si hay carry out, se le suma a la suma obtenida
        if(carry == 1){
            String res = sb.reverse().toString();
            res = binaryAdd(res, "1");
            return res;
        }
        //Si no, solamente se devuelve la suma
        else{
            String res = sb.reverse().toString();
            return res;
        }
    }
    public static String binaryAdd5(String bin1, String bin2,String bin3, String bin4,String bin5) {
        int b1_len = bin1.length()-1;
        int b2_len = bin2.length()-1;
        int b3_len = bin3.length()-1;
        int b4_len = bin4.length()-1;
        int b5_len = bin5.length()-1;


        int carry = 0;
        StringBuilder sb = new StringBuilder();
        //Se computa la suma de los cinco números binarios
        while(b1_len >= 0 || b2_len >=0 || b3_len >= 0 || b4_len >=0 || b5_len >=0){
            int v_b1 = 0;
            int v_b2 = 0;
            int v_b3 = 0;
            int v_b4 = 0;
            int v_b5 = 0;

            if(b1_len >= 0){
                v_b1 = bin1.charAt(b1_len)=='0'? 0 : 1;
                b1_len--;
            }
            if(b2_len >= 0){
                v_b2 = bin2.charAt(b2_len)=='0'? 0: 1;
                b2_len--;
            }
            if(b3_len >= 0){
                v_b3 = bin3.charAt(b3_len)=='0'? 0 : 1;
                b3_len--;
            }
            if(b4_len >= 0){
                v_b4 = bin4.charAt(b4_len)=='0'? 0: 1;
                b4_len--;
            }
            if(b5_len >= 0){
                v_b5 = bin5.charAt(b5_len)=='0'? 0 : 1;
                b5_len--;
            }

            int suma = v_b1 + v_b2 + v_b3 + v_b4 + v_b5 + carry;
            if(suma == 2){
                sb.append(String.valueOf(0));
                carry = 1;
            }else if (suma == 3){
                sb.append(String.valueOf(1));
                carry = 1;
            }
            else if (suma == 4){
                sb.append(String.valueOf(0));
                carry = 2;
            }
            else if (suma == 5){
                sb.append(String.valueOf(1));
                carry = 2;
            }
            else if (suma == 6){
                sb.append(String.valueOf(0));
                carry = 3;
            }
            else if (suma == 7){
                sb.append(String.valueOf(1));
                carry = 3;
            }
            else {
                sb.append(String.valueOf(suma));
                carry = 0;
            }
        }
        //Si hay carry out, se le suma a la suma obtenida
        if(carry>=1){
            String res = sb.reverse().toString();
            String c = completeZeros(Integer.toBinaryString(carry));
            res = binaryAdd(res, c);
            return res;
        }
        //Si no, solamente se devuelve la suma
        else{
            String res = sb.reverse().toString();
            return res;
        }
    }

}
