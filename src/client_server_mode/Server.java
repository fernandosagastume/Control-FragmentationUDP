package client_server_mode;

import java.io.IOException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class Server {
    private static DatagramSocket socket;
    private static final int WAITING_CONNECTION = 0;
    private static final int ABOUT_TO_CONNECT = 1;
    private static final int DATA_RECEIVE = 2;
    private static final int END_CONNECTION = 3;
    private static final int CONNECTION_ENDED = 4;
    private static int SERVER_STATE = WAITING_CONNECTION;

    public Server(int port) throws Exception{
        socket = new DatagramSocket(port);
        tcpListen.start();
    }

    public static Thread tcpListen = new Thread(new Runnable() {
        @Override
        public void run() {
            DatagramPacket clientDatagram = null;
            byte[] buffer = new byte[1472];
            String ucs = "";
            byte messageToSend[] = null;

            String mensaje = "";
            while (true) {
                //Se crea un datagram packet que escuche y reciba el paquete del cliente
                clientDatagram = new DatagramPacket(buffer, buffer.length);
                //Se recibe los datos enviados por el cliente
                try {
                    socket.receive(clientDatagram);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mensaje = new String(buffer, StandardCharsets.US_ASCII);
                ucs = mensaje.toUpperCase();
                //Se construye el paquete del cliente
                PacketSC psc = PacketSC.buildPacket(ucs);

                //Se suman los campos de paquete recibido para verificar checksum
                String sumFields = fieldAdd(psc.SOURCE_PORT, psc.DESTINATION_PORT,
                        psc.SEQ_NUM+psc.ACK_NUM, psc.ACK_FLAG+psc.SYN_FLAG,psc.FYN_FLAG+psc.WINDOW_SIZE);
                //Si es verdadero, significa que el checksum esta correcto
                //(i.e. todos los bits de la suma de los campos y el checksum son 1)
                boolean correctChecksum = verifyChecksum(sumFields, psc.CHECKSUM);
                //Si no está bien el checksum se bota el paquete
                if(!correctChecksum)
                    continue;

                if(SERVER_STATE == WAITING_CONNECTION){
                    //Se verifica que realmente sea el primer mensaje
                    if(psc.SEQ_NUM.equals("30") && psc.ACK_NUM.equals("30")
                       && psc.SYN_FLAG.equals("31")){
                        PacketSC firstAckPack = new PacketSC();
                        //Source port mis inciales FS
                        firstAckPack.setSOURCE_PORT("4653");
                        firstAckPack.setDESTINATION_PORT(psc.SOURCE_PORT);
                        firstAckPack.setSEQ_NUM("30");
                        firstAckPack.setACK_NUM("30");
                        firstAckPack.setACK_FLAG("31");
                        firstAckPack.setSYN_FLAG("31");
                        firstAckPack.setFYN_FLAG("30");
                        firstAckPack.setWINDOW_SIZE("31");
                        //Se realiza el checksum del paquete a enviar
                        String chsm = checksumComputation(firstAckPack.SOURCE_PORT,
                                firstAckPack.DESTINATION_PORT,
                                firstAckPack.SEQ_NUM+firstAckPack.ACK_NUM,
                                firstAckPack.ACK_FLAG+firstAckPack.SYN_FLAG,
                                firstAckPack.FYN_FLAG+firstAckPack.WINDOW_SIZE);
                        firstAckPack.setCHECKSUM(chsm);
                        //Se envían los datos
                        sendData(firstAckPack.packetHEX(), clientDatagram.getAddress(), clientDatagram.getPort());
                        SERVER_STATE = ABOUT_TO_CONNECT;
                    }
                }
                //Esperando el tercer mensaje del 3WH
                else if(SERVER_STATE == ABOUT_TO_CONNECT) {
                    //Se verifica que se esté enviando el ack del primer mensaje
                    if (psc.ACK_FLAG.equals("31") && psc.ACK_NUM.equals("30") &&
                            psc.SEQ_NUM.equals("31")){
                        PacketSC secondAckPack = new PacketSC();
                        //Source port mis inciales FS
                        secondAckPack.setSOURCE_PORT("4653");
                        secondAckPack.setDESTINATION_PORT(psc.SOURCE_PORT);
                        secondAckPack.setSEQ_NUM("31");
                        secondAckPack.setACK_NUM("31");
                        secondAckPack.setACK_FLAG("31");
                        secondAckPack.setSYN_FLAG("30");
                        secondAckPack.setFYN_FLAG("30");
                        secondAckPack.setWINDOW_SIZE("31");
                        //Se realiza el checksum del paquete a enviar
                        String chsm = checksumComputation(secondAckPack.SOURCE_PORT,
                                secondAckPack.DESTINATION_PORT,
                                secondAckPack.SEQ_NUM+secondAckPack.ACK_NUM,
                                secondAckPack.ACK_FLAG+secondAckPack.SYN_FLAG,
                                secondAckPack.FYN_FLAG+secondAckPack.WINDOW_SIZE);
                        secondAckPack.setCHECKSUM(chsm);
                        //Se envían los datos
                        sendData(secondAckPack.packetHEX(), clientDatagram.getAddress(), clientDatagram.getPort());
                        SERVER_STATE = DATA_RECEIVE;
                    }
                }
                //Fase 2, la conexión ya fue establecida
                else if (SERVER_STATE == DATA_RECEIVE){
                    SERVER_STATE = 100;
                }
                //Ultima fase, el cliente termino conexión
                else if(SERVER_STATE == END_CONNECTION){
                    SERVER_STATE = 100;
                }
            }
            //if (SERVER_STATE == CONNECTION_ENDED) {
              //  socket.close();
            //}
        }
    });

    private static void sendData(String data, InetAddress ip, int port){
        //Se crea el packet para enviarse al servidor
        DatagramPacket dp = new DatagramPacket(data.getBytes(),data.getBytes().length,ip,port);
        try{
            socket.send(dp);
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
