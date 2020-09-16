package client_server_mode;



import java.io.*;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Client {
    @SuppressWarnings("FieldCanBeLocal")
    private static Socket mySocket;
    private static int TCPCon_phase = 0;
    private String sourcePort;
    private static String destinationPort;
    private static String FILE_NAME;
    public static int port;
    public static InetAddress ip;
    public static String currentSeqNum;
    public static String currentAckNum;
    private static final Logger LOGGER = Logger.getLogger(Client.class.getName());
    private static PacketSC currentPacket;
    private static DataInputStream dis;

    public Client(int port, InetAddress ip, String fileName) throws Exception{
        this.port = port;
        this.ip = ip;
        this.FILE_NAME = fileName;

        ConsoleHandler consoleHandler = new ConsoleHandler();
        FileHandler fileHandler = new FileHandler(Client.class.getName()+".txt");
        consoleHandler.setLevel(Level.INFO);
        fileHandler.setLevel(Level.INFO);
        consoleHandler.setFormatter(new FormatLogger());
        fileHandler.setFormatter(new FormatLogger());
        LOGGER.addHandler(consoleHandler);
        LOGGER.addHandler(fileHandler);
        LOGGER.setUseParentHandlers(false);

        mySocket = new Socket(ip,port);
        //Mis inciales en HEX como source port
        sourcePort = "4653";
        //Son ceros porque no conozco aún el server
        destinationPort = "3030";
        //sequence number empieza en 0
        String seqNum_syn = "00";
        //Ack num 0 porque es el inicio, de la conexion
        String ackNum_syn = "00";
        //0 porque es el primer mensaje del 3-way handshake
        String ACK_FLAG = "00";
        //1 porque es el primer mensaje del 3-way handshake
        String SYN_FLAG = "01";
        //0 porque es el primer mensaje del 3-way handshake
        String FYN_FLAG = "00";
        //Window size de 1
        String WINDOW_SIZE = "01";
        //checksum
        String checksum = checksumComputation(sourcePort, destinationPort, seqNum_syn+ackNum_syn,
                                                ACK_FLAG+SYN_FLAG, FYN_FLAG+WINDOW_SIZE);
        //Se contruye el header + data
        String headerData = sourcePort + destinationPort + seqNum_syn +
                ackNum_syn + ACK_FLAG + SYN_FLAG + FYN_FLAG + WINDOW_SIZE + checksum;

        tcp_con.start();

        //Se envía el payload + header al server (no se envía nada de data(0's))
        sendData(headerData);

        //Se establece la fase de establecimiento de conexion
        TCPCon_phase = 1;

    }
    private static final Thread tcp_con = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                //Para recibir el input del server socket
                DataInputStream dis = new DataInputStream(mySocket.getInputStream());
                int cont = 0;
                //Boolean que determina si se está enviando el ultimo fragmento
                boolean lastPieceOfData = false;
                //Boolean que determina si ya se puede acabar la conexión
                boolean okToEndConn = false;
                File file = new File(FILE_NAME);
                //Se convierte el archivo que se desea enviar en bytes
                byte[] filetoBytes = convertFileToBytes(file);
                //ArrayList para guardar la data fragmentada
                ArrayList<byte[]> fragmentedBytes = new ArrayList<>();
                int payload_length = 1460;
                //Se divide en 2 para que se puedan mandar bien los strings
                splitByteArray(fragmentedBytes, filetoBytes, payload_length/2);
                boolean endConnComplete = false;
                while (true) {
                    PacketSC psc = new PacketSC();
                    if(TCPCon_phase!=2) {
                        //Se guarda el header recibido
                        String headerRCV = dis.readUTF();
                        //Se obtiene la data y se guarda en un objeto PacketSC
                        psc = PacketSC.buildPacket(headerRCV);
                        if(!psc.packetHEX().equals(""))
                            LOGGER.log(Level.INFO, "<Server>" + "Fase: " + TCPCon_phase + " Mensaje del cliente: \n" +
                                    "Source Port: " + psc.SOURCE_PORT + "\nDestination Port: " +
                                    psc.DESTINATION_PORT + "\nSequence Number: " +
                                    psc.SEQ_NUM + "\nACK Number: " + psc.ACK_NUM +
                                    "\nACK Flag: " + psc.ACK_FLAG + "\nSYN Flag: " + psc.SYN_FLAG +
                                    "\nFYN Flag: " + psc.FYN_FLAG + "\n Window Size: " + psc.WINDOW_SIZE +
                                    "\nChecksum: " + psc.CHECKSUM + "\n DATA: " + psc.DATA);
                        //Se suman los campos de paquete recibido para verificar checksum
                        String sumFields = fieldAdd(psc.SOURCE_PORT, psc.DESTINATION_PORT,
                                psc.SEQ_NUM + psc.ACK_NUM, psc.ACK_FLAG + psc.SYN_FLAG, psc.FYN_FLAG + psc.WINDOW_SIZE);
                        //Si es verdadero, significa que el checksum esta correcto (i.e. todos los bits son 1)
                        boolean correctChecksum = verifyChecksum(sumFields, psc.CHECKSUM);
                        String theCorrectOne = checksumComputation(psc.SOURCE_PORT, psc.DESTINATION_PORT,
                                psc.SEQ_NUM + psc.ACK_NUM, psc.ACK_FLAG + psc.SYN_FLAG, psc.FYN_FLAG + psc.WINDOW_SIZE);
                        while(theCorrectOne.length() != 4){
                            theCorrectOne = "0" + theCorrectOne;
                        }
                        //Si no está bien el checksum se bota el paquete
                        if (!correctChecksum) {
                            LOGGER.log(Level.SEVERE, "Checksum incorrecto: " + psc.CHECKSUM + "\n el correcto " +
                                    "deberia de ser: " + theCorrectOne);
                            continue;
                        }
                        //Si ya se envió ya data, ya se puede empezar la termination phase
                        if (lastPieceOfData) {
                            okToEndConn = true;
                        }
                    }
                    //Connection Establishment Phase
                    if (TCPCon_phase == 1) {
                        //Se verifica el formato y que coincidan los numeros en los campos
                        if (psc.ACK_NUM.equals("00") && psc.SEQ_NUM.equals("00") &&
                                psc.ACK_FLAG.equals("01") && psc.SYN_FLAG.equals("01")) {
                            PacketSC send_packet_ack = new PacketSC();
                            send_packet_ack.setSOURCE_PORT("4653");
                            send_packet_ack.setDESTINATION_PORT(psc.SOURCE_PORT);
                            send_packet_ack.setSEQ_NUM("01");
                            send_packet_ack.setACK_NUM("00");
                            send_packet_ack.setACK_FLAG("01");
                            send_packet_ack.setSYN_FLAG("00");
                            send_packet_ack.setFYN_FLAG("00");
                            send_packet_ack.setWINDOW_SIZE("01");
                            //Se realiza el checksum del paquete a enviar
                            String chsm = checksumComputation(send_packet_ack.SOURCE_PORT,
                                    send_packet_ack.DESTINATION_PORT,
                                    send_packet_ack.SEQ_NUM + send_packet_ack.ACK_NUM,
                                    send_packet_ack.ACK_FLAG + send_packet_ack.SYN_FLAG,
                                    send_packet_ack.FYN_FLAG + send_packet_ack.WINDOW_SIZE);
                            send_packet_ack.setCHECKSUM(chsm);
                            while(chsm.length() != 4){
                                chsm = "0" + chsm;
                            }
                            sendData(send_packet_ack.packetHEX());
                            LOGGER.log(Level.INFO, "<Cliente> Se envió lo siguiente FASE 1: " + send_packet_ack.packetHEX());
                            currentSeqNum = send_packet_ack.SEQ_NUM;
                            currentAckNum = send_packet_ack.ACK_NUM;
                            destinationPort = psc.SOURCE_PORT;
                            currentPacket = send_packet_ack;
                            TCPCon_phase = 2;

                        }
                    }
                    else if (TCPCon_phase == 2){
                        //De una vez se manda el mensaje con el nombre del archivo
                        String fragmentN = "";
                        byte[] bt = FILE_NAME.getBytes();
                        for (byte b : bt) {
                            //Se convierte el nombre del archivo en hex
                            fragmentN += String.format("%02x", b).toUpperCase();
                        }
                        PacketSC packetName = new PacketSC();
                        String seqN = (currentSeqNum.equals("00")) ? "01" : "00";
                        packetName.setSOURCE_PORT("4653");
                        packetName.setDESTINATION_PORT(destinationPort);
                        packetName.setSEQ_NUM(seqN);
                        packetName.setACK_NUM(currentAckNum);
                        packetName.setACK_FLAG("00");
                        packetName.setSYN_FLAG("00");
                        packetName.setFYN_FLAG("00");
                        packetName.setWINDOW_SIZE("01");
                        packetName.setDATA(fragmentN);
                        //Se realiza el checksum del paquete a enviar
                        String chsmName = checksumComputationWithData(packetName.SOURCE_PORT,
                                packetName.DESTINATION_PORT,
                                packetName.SEQ_NUM + packetName.ACK_NUM,
                                packetName.ACK_FLAG + packetName.SYN_FLAG,
                                packetName.FYN_FLAG + packetName.WINDOW_SIZE,
                                packetName.DATA);
                        while(chsmName.length() != 4){
                            chsmName = "0" + chsmName;
                        }
                        packetName.setCHECKSUM(chsmName);
                        currentSeqNum = packetName.SEQ_NUM;
                        currentAckNum = packetName.ACK_NUM;
                        currentPacket = packetName;
                        sendData(packetName.packetHEX());

                        LOGGER.log(Level.INFO, "<Cliente> DATA TRANSFER. Inicio de la trasferencia de archivos.");
                        TCPCon_phase = 3;
                    }
                    //Data Transfer Phase
                    else if (TCPCon_phase == 3) {
                        if (okToEndConn) {//Si ya se envió el último pedazo de data, finalizar conexión
                            PacketSC send_packet_ack = new PacketSC();
                            String seqN = (currentSeqNum.equals("00")) ? "01" : "00";
                            send_packet_ack.setSOURCE_PORT("4653");
                            send_packet_ack.setDESTINATION_PORT(psc.SOURCE_PORT);
                            send_packet_ack.setSEQ_NUM(seqN);
                            send_packet_ack.setACK_NUM(psc.ACK_NUM);
                            send_packet_ack.setACK_FLAG("00");
                            send_packet_ack.setSYN_FLAG("00");
                            send_packet_ack.setFYN_FLAG("01");
                            send_packet_ack.setWINDOW_SIZE("01");
                            //Se realiza el checksum del paquete a enviar
                            String chsm = checksumComputation(send_packet_ack.SOURCE_PORT,
                                    send_packet_ack.DESTINATION_PORT,
                                    send_packet_ack.SEQ_NUM + send_packet_ack.ACK_NUM,
                                    send_packet_ack.ACK_FLAG + send_packet_ack.SYN_FLAG,
                                    send_packet_ack.FYN_FLAG + send_packet_ack.WINDOW_SIZE);
                            while(chsm.length() != 4){
                                chsm = "0" + chsm;
                            }
                            send_packet_ack.setCHECKSUM(chsm);
                            sendData(send_packet_ack.packetHEX());
                            currentPacket = send_packet_ack;
                            TCPCon_phase = 4;
                            LOGGER.log(Level.INFO, "<Cliente>" + "Fase " + TCPCon_phase + " Mensaje del cliente: \n" +
                                    "Source Port: " + currentPacket.SOURCE_PORT + "\nDestination Port: " +
                                    currentPacket.DESTINATION_PORT + "\nSequence Number: " +
                                    currentPacket.SEQ_NUM + "\nACK Number: " + currentPacket.ACK_NUM +
                                    "\nACK Flag: " + currentPacket.ACK_FLAG + "\nSYN Flag: " + currentPacket.SYN_FLAG +
                                    "\nFYN Flag: " + currentPacket.FYN_FLAG + "\n Window Size: " + currentPacket.WINDOW_SIZE +
                                    "\nChecksum: " + currentPacket.CHECKSUM + "\n DATA: " + currentPacket.DATA);
                            continue;
                        } else {
                            if (psc.ACK_FLAG.equals("01") && psc.SYN_FLAG.equals("00") &&
                                    psc.FYN_FLAG.equals("00")) {
                                PacketSC send_packet_ack = new PacketSC();
                                byte[] fileData;
                                String fragmentN = "";
                                if (file.length() < 1460) { //Solamente se manda un paquete
                                    final StringBuilder stb = new StringBuilder();
                                    for (byte b : filetoBytes) {
                                        stb.append(String.format("%02x", b));
                                    }
                                    fragmentN = stb.toString().toUpperCase();
                                    String seqN = (currentSeqNum.equals("00")) ? "01" : "00";
                                    send_packet_ack.setSOURCE_PORT("4653");
                                    send_packet_ack.setDESTINATION_PORT(psc.SOURCE_PORT);
                                    send_packet_ack.setSEQ_NUM(seqN);
                                    send_packet_ack.setACK_NUM(psc.ACK_NUM);
                                    send_packet_ack.setACK_FLAG("00");
                                    send_packet_ack.setSYN_FLAG("00");
                                    send_packet_ack.setFYN_FLAG("00");
                                    send_packet_ack.setWINDOW_SIZE("01");
                                    send_packet_ack.setDATA(fragmentN);
                                    //Se realiza el checksum del paquete a enviar
                                    String chsm = checksumComputationWithData(send_packet_ack.SOURCE_PORT,
                                            send_packet_ack.DESTINATION_PORT,
                                            send_packet_ack.SEQ_NUM + send_packet_ack.ACK_NUM,
                                            send_packet_ack.ACK_FLAG + send_packet_ack.SYN_FLAG,
                                            send_packet_ack.FYN_FLAG + send_packet_ack.WINDOW_SIZE,
                                             send_packet_ack.DATA);
                                    while(chsm.length() != 4){
                                        chsm = "0" + chsm;
                                    }
                                    send_packet_ack.setCHECKSUM(chsm);
                                    currentSeqNum = send_packet_ack.SEQ_NUM;
                                    currentAckNum = send_packet_ack.ACK_NUM;
                                    currentPacket = send_packet_ack;
                                    sendData(send_packet_ack.packetHEX());
                                    LOGGER.log(Level.INFO, "<Cliente>" + "Fase " + TCPCon_phase + " Mensaje del cliente: \n" +
                                            "Source Port: " + currentPacket.SOURCE_PORT + "\nDestination Port: " +
                                            currentPacket.DESTINATION_PORT + "\nSequence Number: " +
                                            currentPacket.SEQ_NUM + "\nACK Number: " + currentPacket.ACK_NUM +
                                            "\nACK Flag: " + currentPacket.ACK_FLAG + "\nSYN Flag: " + currentPacket.SYN_FLAG +
                                            "\nFYN Flag: " + currentPacket.FYN_FLAG + "\n Window Size: " + currentPacket.WINDOW_SIZE +
                                            "\nChecksum: " + currentPacket.CHECKSUM + "\n DATA: " + currentPacket.DATA);
                                    lastPieceOfData = true;
                                    continue;
                                    }
                                    if ((cont + 1) < fragmentedBytes.size()) {
                                        fileData = fragmentedBytes.get(cont);
                                        final StringBuilder stb = new StringBuilder();

                                        for (byte b : fileData) {
                                            stb.append(String.format("%02x", b));
                                        }
                                        fragmentN = stb.toString().toUpperCase();
                                        cont++;

                                    } else { //Ultimo fragmento del file
                                        fileData = fragmentedBytes.get(cont);
                                        final StringBuilder stb = new StringBuilder();
                                        for (byte b : fileData) {
                                            stb.append(String.format("%02x", b));
                                        }
                                        fragmentN = stb.toString().toUpperCase();
                                        lastPieceOfData = true;
                                    }

                                String seqN = (currentSeqNum.equals("00")) ? "01" : "00";
                                send_packet_ack.setSOURCE_PORT("4653");
                                send_packet_ack.setDESTINATION_PORT(psc.SOURCE_PORT);
                                send_packet_ack.setSEQ_NUM(seqN);
                                send_packet_ack.setACK_NUM(psc.ACK_NUM);
                                send_packet_ack.setACK_FLAG("00");
                                send_packet_ack.setSYN_FLAG("00");
                                send_packet_ack.setFYN_FLAG("00");
                                send_packet_ack.setWINDOW_SIZE("01");
                                send_packet_ack.setDATA(fragmentN);
                                //Se realiza el checksum del paquete a enviar
                                String chsm = checksumComputationWithData(send_packet_ack.SOURCE_PORT,
                                        send_packet_ack.DESTINATION_PORT,
                                        send_packet_ack.SEQ_NUM + send_packet_ack.ACK_NUM,
                                        send_packet_ack.ACK_FLAG + send_packet_ack.SYN_FLAG,
                                        send_packet_ack.FYN_FLAG + send_packet_ack.WINDOW_SIZE,
                                         send_packet_ack.DATA);
                                while(chsm.length() != 4){
                                    chsm = "0" + chsm;
                                }
                                send_packet_ack.setCHECKSUM(chsm);
                                currentSeqNum = send_packet_ack.SEQ_NUM;
                                currentAckNum = send_packet_ack.ACK_NUM;
                                sendData(send_packet_ack.packetHEX());
                                currentPacket = send_packet_ack;
                            }
                        }
                    }
                    //Connection Termination Phase
                    else if (TCPCon_phase == 4) { //Se acaba la conexión, se manda un último ACK
                        PacketSC send_packet_ack = new PacketSC();
                        String seqN = (currentSeqNum.equals("00")) ? "01" : "00";
                        send_packet_ack.setSOURCE_PORT("4653");
                        send_packet_ack.setDESTINATION_PORT(psc.SOURCE_PORT);
                        send_packet_ack.setSEQ_NUM(seqN);
                        send_packet_ack.setACK_NUM(psc.ACK_NUM);
                        send_packet_ack.setACK_FLAG("01");
                        send_packet_ack.setSYN_FLAG("00");
                        send_packet_ack.setFYN_FLAG("00");
                        send_packet_ack.setWINDOW_SIZE("01");
                        //Se realiza el checksum del paquete a enviar
                        String chsm = checksumComputation(send_packet_ack.SOURCE_PORT,
                                send_packet_ack.DESTINATION_PORT,
                                send_packet_ack.SEQ_NUM + send_packet_ack.ACK_NUM,
                                send_packet_ack.ACK_FLAG + send_packet_ack.SYN_FLAG,
                                send_packet_ack.FYN_FLAG + send_packet_ack.WINDOW_SIZE);
                        while(chsm.length() != 4){
                            chsm = "0" + chsm;
                        }
                        send_packet_ack.setCHECKSUM(chsm);
                        sendData(send_packet_ack.packetHEX());
                        currentPacket = send_packet_ack;
                        break;
                    }
                    LOGGER.log(Level.INFO, "<Cliente>" + "Fase " + TCPCon_phase + " Mensaje del cliente: \n" +
                            "Source Port: " + currentPacket.SOURCE_PORT + "\nDestination Port: " +
                            currentPacket.DESTINATION_PORT + "\nSequence Number: " +
                            currentPacket.SEQ_NUM + "\nACK Number: " + currentPacket.ACK_NUM +
                            "\nACK Flag: " + currentPacket.ACK_FLAG + "\nSYN Flag: " + currentPacket.SYN_FLAG +
                            "\nFYN Flag: " + currentPacket.FYN_FLAG + "\n Window Size: " + currentPacket.WINDOW_SIZE +
                            "\nChecksum: " + currentPacket.CHECKSUM + "\n DATA: " + currentPacket.DATA);
                }

                mySocket.close();
                LOGGER.log(Level.INFO, "<Cliente> Conexion con servidor finalizada");
            } catch (UnknownHostException uhe) {
                System.out.println("Unknown Host Exception: " + uhe);
            } catch (IOException ioe) {
                System.out.println("IO Exception: " + ioe);
            }
        }
    });


    // Función para enviar data al servidor
    private static void sendData(String data) throws IOException {
        //Se crea el packet para enviarse al servidor
        DataOutputStream dos = new DataOutputStream(mySocket.getOutputStream());
        try{
            dos.writeUTF(data);
        }catch(IOException io){
            LOGGER.log(Level.SEVERE, "No se pudo enviar el paquete. " + io.getMessage());
        }
    }

    //Divide un arreglo de bytes en chunks para fragmentación
    public static void splitByteArray(ArrayList<byte[]> alb, byte[] file_byte, int payload_length){

        int chunk_cont = (file_byte.length+payload_length-1)/payload_length;
        byte[] chunk = null;
        //Se itera y se guarda cada chunk un el arraylist
        for (int i = 1; i < chunk_cont; i++) {
            int index = (i - 1)*payload_length;
            chunk = Arrays.copyOfRange(file_byte, index, index + payload_length);
            alb.add(chunk);
        }

        int lastIndex = -1;
        if (file_byte.length % payload_length == 0) {
            lastIndex = file_byte.length;
        } else {
            lastIndex = file_byte.length%payload_length+payload_length*(chunk_cont-1);
        }

        chunk = Arrays.copyOfRange(file_byte, (chunk_cont-1)*payload_length, lastIndex);
        alb.add(chunk);
    }

    public static byte[] convertFileToBytes(File file){
        byte[] fileBytes = new byte[(int) file.length()];
        try(FileInputStream inputStream = new FileInputStream(file))
        {
            inputStream.read(fileBytes);
        }
        catch (Exception ex)
        {
            LOGGER.log(Level.SEVERE, "Error al convertir el archivo a bytes. " + ex.getMessage());
        }
        return fileBytes;
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

    //Devuelve el CHECKSUM
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
