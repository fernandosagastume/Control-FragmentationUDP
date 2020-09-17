package client_server_mode;

import sun.nio.cs.ext.ISO2022_CN;

import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.util.Arrays;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {
    private static ServerSocket socket;
    private static Socket accept;
    private static final int WAITING_CONNECTION = 0;
    private static final int ABOUT_TO_CONNECT = 1;
    private static final int DATA_RECEIVE = 2;
    private static final int END_CONNECTION = 3;
    private static final int CONNECTION_ENDED = 4;
    private static int SERVER_STATE = WAITING_CONNECTION;
    private static String file_name;
    private static String currentAck;
    private static String currentSeqN;
    private static final Logger LOGGER = Logger.getLogger(Server.class.getName());

    public Server(int port) throws Exception{
        socket = new ServerSocket(port);
        ConsoleHandler consoleHandler = new ConsoleHandler();
        FileHandler fileHandler = new FileHandler(Server.class.getName()+".txt");
        consoleHandler.setLevel(Level.INFO);
        fileHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(new FormatLogger());
        fileHandler.setFormatter(new FormatLogger());
        LOGGER.addHandler(consoleHandler);
        LOGGER.addHandler(fileHandler);
        LOGGER.setUseParentHandlers(false);
        tcp_con.start();
        LOGGER.log(Level.INFO, "<Servidor> INICIA SERVIDOR. Listo para escuchar al cliente.");
    }
    private static final Thread tcp_con = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                accept = socket.accept();
                //Utilizado para recibir el input del cliente
                DataInputStream dis = new DataInputStream(accept.getInputStream());
                //Contador de data que ha llegado
                int cont = 0;
                //Guarda el string que manda el client
                String mensaje = "";
                StringBuilder fileHex = new StringBuilder();

                while (true) {
                    byte[] buffer = new byte[1472];
                    //String para que se lea el paquete del cliente
                    dis.read(buffer);
                    /*int conta= buffer.length - 1;
                    while (conta >= 0 && buffer[conta] == 0) {
                        --conta;
                    }
                    buffer = Arrays.copyOf(buffer, conta+1);*/

                    final StringBuilder builderh = new StringBuilder();
                    for(byte b : buffer) {
                        builderh.append(String.format("%02x", b));
                    }

                    mensaje = (builderh.toString()).toUpperCase();

                    //Se construye el paquete del cliente
                    PacketSC psc = PacketSC.buildPacket(mensaje);

                    if(!psc.packetHEX().equals("")){
                        psc.setDATA((psc.DATA).replaceAll("0+$", ""));
                        LOGGER.log(Level.INFO, "<Client>" + "Server State:  " + SERVER_STATE + " Mensaje del cliente: \n" +
                                "Source Port: " + psc.SOURCE_PORT + "\nDestination Port: " +
                                psc.DESTINATION_PORT + "\nSequence Number: " +
                                psc.SEQ_NUM + "\nACK Number: " + psc.ACK_NUM +
                                "\nACK Flag: " + psc.ACK_FLAG + "\nSYN Flag: " + psc.SYN_FLAG +
                                "\nFYN Flag: " + psc.FYN_FLAG + "\n Window Size: " + psc.WINDOW_SIZE +
                                "\nChecksum: " + psc.CHECKSUM + "\n DATA: " + psc.DATA);
                    }

                    boolean correctChecksum;
                    if(SERVER_STATE == WAITING_CONNECTION || SERVER_STATE == ABOUT_TO_CONNECT ||
                         SERVER_STATE == END_CONNECTION || psc.FYN_FLAG.equals("01")){
                        //Se suman los campos de paquete recibido para verificar checksum
                        String sumFields = fieldAdd(psc.SOURCE_PORT, psc.DESTINATION_PORT,
                                psc.SEQ_NUM + psc.ACK_NUM, psc.ACK_FLAG + psc.SYN_FLAG,
                                psc.FYN_FLAG + psc.WINDOW_SIZE);
                        //Si es verdadero, significa que el checksum esta correcto
                        //(i.e. todos los bits de la suma de los campos y el checksum son 1)
                        correctChecksum = verifyChecksum(sumFields, psc.CHECKSUM);
                    }
                    else{ //CASO: DATA RECEIVE
                        String sumas = fieldAdd(psc.SOURCE_PORT, psc.DESTINATION_PORT,
                                psc.SEQ_NUM + psc.ACK_NUM, psc.ACK_FLAG + psc.SYN_FLAG,
                                psc.FYN_FLAG + psc.WINDOW_SIZE);
                        //Se parte la data en chunks de 16 bits
                        String [] arr = (psc.DATA).split("(?<=\\G.{4})");

                        //Se suman los campos con los chunks de la data
                        for (int i = 0; i < arr.length; i++) {
                            sumas = binaryAdd(sumas, hexToBinary(arr[i]));
                        }
                        //Si es verdadero, significa que el checksum esta correcto
                        //(i.e. todos los bits de la suma de los campos y el checksum son 1)
                        correctChecksum = verifyChecksum(sumas, psc.CHECKSUM);
                    }
                    //Se obtiene el numero de sequencia
                    currentSeqN = psc.SEQ_NUM;
                    //Se obtiene el ultimo ack number que tiene el cliente
                    currentAck = psc.ACK_NUM;
                    //Si no está bien el checksum se bota el paquete
                    if (!correctChecksum)
                        continue;

                    if (SERVER_STATE == WAITING_CONNECTION) {
                        //Se verifica que realmente sea el primer mensaje
                        if (psc.SEQ_NUM.equals("00") && psc.ACK_NUM.equals("00")
                                && psc.SYN_FLAG.equals("01")) {
                            PacketSC firstAckPack = new PacketSC();
                            //Source port mis inciales FS
                            firstAckPack.setSOURCE_PORT("4653");
                            firstAckPack.setDESTINATION_PORT(psc.SOURCE_PORT);
                            firstAckPack.setSEQ_NUM("00");
                            firstAckPack.setACK_NUM("00");
                            firstAckPack.setACK_FLAG("01");
                            firstAckPack.setSYN_FLAG("01");
                            firstAckPack.setFYN_FLAG("00");
                            firstAckPack.setWINDOW_SIZE("01");
                            //Se realiza el checksum del paquete a enviar
                            String chsm = checksumComputation(firstAckPack.SOURCE_PORT,
                                    firstAckPack.DESTINATION_PORT,
                                    firstAckPack.SEQ_NUM + firstAckPack.ACK_NUM,
                                    firstAckPack.ACK_FLAG + firstAckPack.SYN_FLAG,
                                    firstAckPack.FYN_FLAG + firstAckPack.WINDOW_SIZE);
                            firstAckPack.setCHECKSUM(chsm);
                            //Se envían los datos
                            try {
                                sendData(firstAckPack.packetHEX());
                            } catch (IOException e) {
                                LOGGER.log(Level.SEVERE, "Error al enviar el paquete. " + e.getMessage());
                            }
                            SERVER_STATE = ABOUT_TO_CONNECT;
                            LOGGER.log(Level.INFO, "<Servidor> 3-Way Handshake: 2/3. Se envió el paquete de la siguiente forma: \n" +
                                    "Source Port: " + firstAckPack.SOURCE_PORT + "\nDestination Port: " +
                                    firstAckPack.DESTINATION_PORT + "\nSequence Number: " +
                                    firstAckPack.SEQ_NUM + "\nACK Number: " + firstAckPack.ACK_NUM +
                                    "\nACK Flag: " + firstAckPack.ACK_FLAG + "\nSYN Flag: " + firstAckPack.SYN_FLAG +
                                    "\nFYN Flag: " + firstAckPack.FYN_FLAG + "\n Window Size: " + firstAckPack.WINDOW_SIZE +
                                    "\nChecksum: " + firstAckPack.CHECKSUM);
                        }
                    }
                    //Esperando el tercer mensaje del 3WH
                    else if (SERVER_STATE == ABOUT_TO_CONNECT) {
                        //Se verifica que se esté enviando el ack del primer mensaje
                        if (psc.ACK_FLAG.equals("01") && psc.ACK_NUM.equals("00") &&
                                psc.SEQ_NUM.equals("01") && psc.SYN_FLAG.equals("00")) {
                            //Solamente se verifica que llegué el último mensaje del 3WH
                            SERVER_STATE = DATA_RECEIVE;
                            LOGGER.log(Level.INFO, "<Servidor> DATA TRANSFER. Ultimo ACK, ya se puede empezar " +
                                    "la transferencia de archivos");
                        }
                    }
                    //Fase 2, la conexión ya fue establecida
                    else if (SERVER_STATE == DATA_RECEIVE) {
                        if (psc.ACK_FLAG.equals("00") && psc.SYN_FLAG.equals("00") &&
                                psc.FYN_FLAG.equals("00")) {
                            if (cont == 0) { //Es el primer data transfer, o sea el file name
                                //Se almacena el nombre del archivo
                                file_name = psc.DATA;

                                PacketSC ackPack = new PacketSC();
                                //Source port mis inciales FS
                                ackPack.setSOURCE_PORT("4653");
                                ackPack.setDESTINATION_PORT(psc.SOURCE_PORT);
                                ackPack.setSEQ_NUM(currentSeqN);
                                String ackn = (currentAck.equals("00")) ? "01" : "00";
                                ackPack.setACK_NUM(ackn);
                                ackPack.setACK_FLAG("01");
                                ackPack.setSYN_FLAG("00");
                                ackPack.setFYN_FLAG("00");
                                ackPack.setWINDOW_SIZE("01");
                                //Se realiza el checksum del paquete a enviar
                                String chsm = checksumComputation(ackPack.SOURCE_PORT,
                                        ackPack.DESTINATION_PORT,
                                        ackPack.SEQ_NUM + ackPack.ACK_NUM,
                                        ackPack.ACK_FLAG + ackPack.SYN_FLAG,
                                        ackPack.FYN_FLAG + ackPack.WINDOW_SIZE);
                                ackPack.setCHECKSUM(chsm);
                                try {
                                    sendData(ackPack.packetHEX());
                                } catch (IOException e) {
                                    LOGGER.log(Level.SEVERE, "Error al enviar el paquete. " + e.getMessage());
                                }
                                cont++;
                                LOGGER.log(Level.INFO, "<Servidor> DATA TRANSFER. Se envió el paquete de la siguiente forma: \n" +
                                        "Source Port: " + ackPack.SOURCE_PORT + "\nDestination Port: " +
                                        ackPack.DESTINATION_PORT + "\nSequence Number: " +
                                        ackPack.SEQ_NUM + "\nACK Number: " + ackPack.ACK_NUM +
                                        "\nACK Flag: " + ackPack.ACK_FLAG + "\nSYN Flag: " + ackPack.SYN_FLAG +
                                        "\nFYN Flag: " + ackPack.FYN_FLAG + "\n Window Size: " + ackPack.WINDOW_SIZE +
                                        "\nChecksum: " + ackPack.CHECKSUM);
                            } else {
                                if (psc.DATA != null)
                                    fileHex.append(psc.DATA);

                                PacketSC ackPack = new PacketSC();
                                //Source port mis inciales FS
                                ackPack.setSOURCE_PORT("4653");
                                ackPack.setDESTINATION_PORT(psc.SOURCE_PORT);
                                ackPack.setSEQ_NUM(currentSeqN);
                                String ackn = (currentAck.equals("00")) ? "01" : "00";
                                ackPack.setACK_NUM(ackn);
                                ackPack.setACK_FLAG("01");
                                ackPack.setSYN_FLAG("00");
                                ackPack.setFYN_FLAG("00");
                                ackPack.setWINDOW_SIZE("01");
                                //Se realiza el checksum del paquete a enviar
                                String chsm = checksumComputation(ackPack.SOURCE_PORT,
                                        ackPack.DESTINATION_PORT,
                                        ackPack.SEQ_NUM + ackPack.ACK_NUM,
                                        ackPack.ACK_FLAG + ackPack.SYN_FLAG,
                                        ackPack.FYN_FLAG + ackPack.WINDOW_SIZE);
                                ackPack.setCHECKSUM(chsm);
                                try {
                                    sendData(ackPack.packetHEX());
                                    LOGGER.log(Level.INFO, "Se envió lo siguiente: " + ackPack.packetHEX());
                                } catch (IOException e) {
                                    LOGGER.log(Level.SEVERE, "Error al enviar el paquete. " + e.getMessage());
                                }
                                LOGGER.log(Level.INFO, "<Servidor> DATA TRANSFER. Se envió el paquete de la siguiente forma: \n" +
                                        "Source Port: " + ackPack.SOURCE_PORT + "\nDestination Port: " +
                                        ackPack.DESTINATION_PORT + "\nSequence Number: " +
                                        ackPack.SEQ_NUM + "\nACK Number: " + ackPack.ACK_NUM +
                                        "\nACK Flag: " + ackPack.ACK_FLAG + "\nSYN Flag: " + ackPack.SYN_FLAG +
                                        "\nFYN Flag: " + ackPack.FYN_FLAG + "\n Window Size: " + ackPack.WINDOW_SIZE +
                                        "\nChecksum: " + ackPack.CHECKSUM);
                            }
                        } else {
                            if (psc.ACK_FLAG.equals("00") && psc.SYN_FLAG.equals("00") &&
                                    psc.FYN_FLAG.equals("01")) {
                                PacketSC ackPack = new PacketSC();
                                //Source port mis inciales FS
                                ackPack.setSOURCE_PORT("4653");
                                ackPack.setDESTINATION_PORT(psc.SOURCE_PORT);
                                ackPack.setSEQ_NUM(currentSeqN);
                                String ackn = (currentAck.equals("00")) ? "01" : "00";
                                ackPack.setACK_NUM(ackn);
                                ackPack.setACK_FLAG("01");
                                ackPack.setSYN_FLAG("00");
                                ackPack.setFYN_FLAG("01");
                                ackPack.setWINDOW_SIZE("01");
                                //Se realiza el checksum del paquete a enviar
                                String chsm = checksumComputation(ackPack.SOURCE_PORT,
                                        ackPack.DESTINATION_PORT,
                                        ackPack.SEQ_NUM + ackPack.ACK_NUM,
                                        ackPack.ACK_FLAG + ackPack.SYN_FLAG,
                                        ackPack.FYN_FLAG + ackPack.WINDOW_SIZE);
                                ackPack.setCHECKSUM(chsm);
                                try {
                                    sendData(ackPack.packetHEX());
                                } catch (IOException e) {
                                    LOGGER.log(Level.SEVERE, "Error al enviar el paquete. " + e.getMessage());
                                }
                                SERVER_STATE = END_CONNECTION;
                                LOGGER.log(Level.INFO, "<Servidor> TERMINATION PHASE. Se envió el paquete de la siguiente forma: \n" +
                                        "Source Port: " + ackPack.SOURCE_PORT + "\nDestination Port: " +
                                        ackPack.DESTINATION_PORT + "\nSequence Number: " +
                                        ackPack.SEQ_NUM + "\nACK Number: " + ackPack.ACK_NUM +
                                        "\nACK Flag: " + ackPack.ACK_FLAG + "\nSYN Flag: " + ackPack.SYN_FLAG +
                                        "\nFYN Flag: " + ackPack.FYN_FLAG + "\n Window Size: " + ackPack.WINDOW_SIZE +
                                        "\nChecksum: " + ackPack.CHECKSUM);
                            }
                        }
                    }
                    //Ultima fase, el cliente termino conexión, se recibe el último ACK del cliente
                    else if (SERVER_STATE == END_CONNECTION) {
                        LOGGER.log(Level.INFO, "<Cliente> TERMINATION PHASE. Se recibió el ultimo mensaje del cliete \n" +
                                "Source Port: " + psc.SOURCE_PORT + "\nDestination Port: " +
                                psc.DESTINATION_PORT + "\nSequence Number: " +
                                psc.SEQ_NUM + "\nACK Number: " + psc.ACK_NUM +
                                "\nACK Flag: " + psc.ACK_FLAG + "\nSYN Flag: " + psc.SYN_FLAG +
                                "\nFYN Flag: " + psc.FYN_FLAG + "\n Window Size: " + psc.WINDOW_SIZE +
                                "\nChecksum: " + psc.CHECKSUM);
                        break;
                    }
                }
                StringBuilder filenameBuilder = new StringBuilder();

                for (int i = 0; i < file_name.length(); i += 2) {
                    try {
                        String str = file_name.substring(i, i + 2);
                        filenameBuilder.append((char) Integer.parseInt(str, 16));
                    } catch (Exception e) {
                        //**Solamente para suprimir el error**
                    }
                }
                String NOMBRE_ARCHIVO = filenameBuilder.toString();
                byte[] fileRealBytes = hexStringToByteArray(fileHex.toString());
                int contador = fileRealBytes.length - 1;
                while (contador >= 0 && fileRealBytes[contador] == 0) {
                    --contador;
                }
                fileRealBytes = Arrays.copyOf(fileRealBytes, contador + 1);
                File fileDestination = new File("prueba" + NOMBRE_ARCHIVO);
                convertToFile(fileRealBytes, fileDestination);
                socket.close();
                LOGGER.log(Level.INFO, "<Servidor> Conexion con cliente finalizada");
            } catch (Exception e) {
                System.out.println("Exception message: " + e);
            }
        }
    });


    public static void convertToFile(byte[] data, File destino) throws Exception{

        FileOutputStream fos = new FileOutputStream(destino);
        fos.write(data);
        fos.close();

    }
    private static void sendData(String data) throws IOException {
        byte[] decoded = new BigInteger(data, 16).toByteArray();
        //Se crea el packet para enviarse al servidor
        DataOutputStream dos = new DataOutputStream(accept.getOutputStream());
        try{
            dos.write(decoded);
        }catch(IOException io){
            LOGGER.log(Level.SEVERE, "No se pudo enviar el paquete. " + io.getMessage());
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int index = i * 2;
            try {
                int v = Integer.parseInt(s.substring(index, index + 2), 16);
                b[i] = (byte) v;
            }
            catch (Exception e){

            }
        }
        return b;
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

    //Devuelve el CHECKSUM tomando en cuenta la data
    public static String checksumComputationWithData(String hex1, String hex2, String hex3,
                                                     String hex4, String hex5, String datos){
        //Se hace la suma de los campos junto con la suma del carry out
        String sumas = fieldAdd(hex1,hex2,hex3,hex4,hex5);
        //Se parte la data en chunks de 16 bits
        String [] arr = datos.split("(?<=\\G.{4})");

        //Se suman los campos con los chunks de la data
        for (int i = 0; i < arr.length; i++) {
            sumas = binaryAdd(sumas, hexToBinary(arr[i]));
        }

        //Se le saca el complemento a 1
        String checksumRes = complement1(sumas);
        //Se convierte a hex
        checksumRes = Integer.toString(Integer.parseInt(checksumRes,2),16).toUpperCase();
        return checksumRes;
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
