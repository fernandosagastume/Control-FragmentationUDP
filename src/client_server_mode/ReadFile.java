package client_server_mode;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReadFile {
    private static final Logger LOGGER = Logger.getLogger(ReadFile.class.getName());
    private  BufferedReader br;
    private StringBuilder sb = new StringBuilder();

    public ReadFile(String file) {
        try {
            br = new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.WARNING, "File Not Found: " + e.getMessage());
        }
    }

    public String getContent() {
        String everything = "";
        sb = new StringBuilder();
        try {
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            everything = sb.toString().trim();
            br.close();
        } catch(IOException e){
            LOGGER.log(Level.WARNING, "IOException: " + e.getMessage());
        }
        //LOGGER.log(Level.INFO, "ReadFile Content: " + everything);
        return everything;
    }

}
