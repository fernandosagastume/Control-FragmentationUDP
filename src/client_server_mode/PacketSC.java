package client_server_mode;

public class PacketSC {
    public String SOURCE_PORT;
    public String DESTINATION_PORT;
    public String SEQ_NUM;
    public String ACK_NUM;
    public String ACK_FLAG;
    public String SYN_FLAG;
    public String FYN_FLAG;
    public String WINDOW_SIZE;
    public String CHECKSUM;
    public String DATA;

    public PacketSC(String SOURCE_PORT, String DESTINATION_PORT, String SYN_NUM, String ACK_NUM,
                    String ACK_FLAG, String SYN_FLAG, String FYN_FLAG ,String WINDOW_SIZE, String CHECKSUM,
                    String DATA){

        this.SOURCE_PORT = SOURCE_PORT; this.DESTINATION_PORT = DESTINATION_PORT;
        this.SEQ_NUM = SYN_NUM; this.ACK_NUM = ACK_NUM;
        this.ACK_FLAG = ACK_FLAG; this.SYN_FLAG = SYN_FLAG;
        this.WINDOW_SIZE = WINDOW_SIZE; this.CHECKSUM = CHECKSUM;
        this.DATA = DATA; this.FYN_FLAG = FYN_FLAG;
    }

    //Constructor para construir un paquete que se recibe
    public PacketSC(){
        this.SOURCE_PORT = ""; this.DESTINATION_PORT = "";
        this.SEQ_NUM = ""; this.ACK_NUM = "";
        this.ACK_FLAG = ""; this.SYN_FLAG = "";
        this.WINDOW_SIZE = ""; this.CHECKSUM = "";
        this.DATA = "";
    }

    public void setSOURCE_PORT(String source_port){
        SOURCE_PORT = source_port;
    }

    public void setDESTINATION_PORT(String destination_port){
        DESTINATION_PORT = destination_port;
    }

    public void setSEQ_NUM(String syn_num){
        SEQ_NUM = syn_num;
    }

    public void setACK_NUM(String ack_num){
        ACK_NUM = ack_num;
    }

    public void setACK_FLAG(String ack_flag){
        ACK_FLAG = ack_flag;
    }

    public void setSYN_FLAG(String syn_flag){
        SYN_FLAG = syn_flag;
    }

    public void setFYN_FLAG(String fyn_flag){
        FYN_FLAG = fyn_flag;
    }

    public void setWINDOW_SIZE(String window_size){
        WINDOW_SIZE = window_size;
    }

    public void setCHECKSUM(String checksum){
        CHECKSUM = checksum;
    }

    public void setDATA(String data){
        DATA = data;
    }

    public static PacketSC buildPacket(String data){
        //Se crea un paquete vacio
        PacketSC psc = new PacketSC();
        //---------------- Se obtienen los campos del header -----------------------
        String sourcePort = data.substring(0,4);
        String destinationPort = data.substring(4,8);
        String seqNum = data.substring(8,10);
        String ackNum = data.substring(10,12);
        String ackFlag = data.substring(12,14);
        String synFlag = data.substring(14,16);
        String fynFlag = data.substring(16,18);
        String windowSize = data.substring(18,20);
        String chksm = data.substring(20,24);
        String datos = data.substring(24);
        //---------------- Se obtienen los campos del header -----------------------
        //Se guardan en los campos del paquete
        psc.setSOURCE_PORT(sourcePort); psc.setDESTINATION_PORT(destinationPort);
        psc.setSEQ_NUM(seqNum); psc.setACK_NUM(ackNum);
        psc.setACK_FLAG(ackFlag); psc.setSYN_FLAG(synFlag);
        psc.setFYN_FLAG(fynFlag); psc.setWINDOW_SIZE(windowSize);
        psc.setCHECKSUM(chksm); psc.setDATA(datos);

        return psc;
    }

    public String packetHEX(){
        return SOURCE_PORT + DESTINATION_PORT + SEQ_NUM + ACK_NUM + ACK_FLAG +
                SYN_FLAG + FYN_FLAG + WINDOW_SIZE + CHECKSUM + DATA;
    }

}
