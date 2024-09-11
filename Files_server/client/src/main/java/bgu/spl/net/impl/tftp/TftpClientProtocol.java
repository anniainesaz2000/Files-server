package bgu.spl.net.impl.tftp;
import bgu.spl.net.api.MessagingProtocol;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.time.LocalDateTime;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class holder{
    private static List<String> fileNames;

    static {
        initializeFileNames();
    }

    private static void initializeFileNames() {
        Path dir = Paths.get("./");

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            fileNames = new ArrayList<>();

            // Iterate through the files in the folder
            for (Path file : stream) {
                // Add the file name to the list
                fileNames.add(file.getFileName().toString());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Public method to access fileNames from another class
    public static List<String> getFileNames() {
        return fileNames;
    }

    // Public method to add a new file name to the list
    public static void addFileName(String fileName) {
        fileNames.add(fileName);
    }

    public static void deleteFileName(String fileName) {
        fileNames.remove(fileName);
    }


}

public class TftpClientProtocol implements MessagingProtocol<byte[]> {

    private boolean shouldTerminate = false;

    ByteBuffer buffer = ByteBuffer.allocate(518); // the message i sent

    protected byte[] protocolResponse = new byte[518];

    public TftpEncoderDecoder.Opcode clientOpcode = TftpEncoderDecoder.Opcode.None;

    TftpEncoderDecoder.Opcode RecievedOpcode = TftpEncoderDecoder.Opcode.None;

    String fileActs = "";

    protected short indexACK = 0;

    private List<Byte> DIRQString = new ArrayList<>();

    private List<Byte> clientFile = new ArrayList<>();


    //private HashMapErrors hashMapError = new HashMapErrors();

    private List<Byte> dataFromClient = new ArrayList<>();
    private String fileNameInProcess ="";


    private TftpEncoderDecoder.Opcode peekOpcode(byte[] message) {
        assert message.length >= 2;
        int u16Opcode = (message[1] & 0xFF) << 8 | (message[0] & 0xFF);
        return TftpEncoderDecoder.Opcode.fromU16(u16Opcode);
    }

    @Override
    public byte[] process(byte[] msg) {
        this.RecievedOpcode = peekOpcode(msg);
        switch (this.RecievedOpcode) {

            case DATA:
                switch (this.clientOpcode) {
                    case RRQ:
                        for (int i = 6; i < msg.length; i++) {
                            clientFile.add(msg[i]);
                        }
                        short size = (short) (((short) msg[2]) << 8 | ((short) msg[3] & 0x00ff));
                        if (size < 512) {
                            this.clientOpcode = TftpEncoderDecoder.Opcode.None;
                            String filePath = "./";
                            String fullPath = filePath + this.fileNameInProcess;
                            try (FileOutputStream fos = new FileOutputStream(fullPath)) {
                                for (Byte b : clientFile) {
                                    fos.write(b);
                                }

                                System.out.println("Bytes written to file successfully.");
                                this.clientFile.clear();
                                this.clientOpcode = TftpEncoderDecoder.Opcode.None;
                                holder.addFileName(this.fileNameInProcess);
                            } catch (IOException e) {
                                System.out.println("can't create file");
                            }
                        }


                        byte[] ackRRQ = new byte[]{0, 4, msg[4], msg[5]};
                        return ackRRQ;

                    case DIRQ:
                        for (int i = 6; i < msg.length; i++) {
                            this.DIRQString.add(msg[i]);
                        }
                        short sizeDIRQ = (short) (((short) msg[2]) << 8 | ((short) msg[3] & 0x00ff));
                        if (sizeDIRQ < 512) {
                            List<Byte> dirq = new ArrayList<>();
                            for (int i = 0; i < this.DIRQString.size(); i++) {
                                if (this.DIRQString.get(i) != (byte) 0) {
                                    dirq.add(this.DIRQString.get(i));
                                } else {
                                    Byte[] byteArray = dirq.toArray(new Byte[dirq.size()]);
                                    byte[] bytePrimitiveArray = new byte[byteArray.length];
                                    for (int j = 0; j < byteArray.length; j++) {
                                        bytePrimitiveArray[j] = byteArray[j];
                                    }
                                    String dirqString = new String(bytePrimitiveArray, StandardCharsets.UTF_8);
                                    System.out.println(dirqString);
                                    System.out.println("\n"); // new line?
                                    dirq.clear();

                                }
                            }
                            this.DIRQString.clear();
                            this.clientOpcode = TftpEncoderDecoder.Opcode.None;
                        }
                        byte[] ackDIRQ = new byte[]{0, 4, msg[4], msg[5]};
                        return ackDIRQ;
                }


            case ACK:
                switch (this.clientOpcode) {
                    case WRQ:

                        short blockNum = (short) (((short) msg[2]) << 8 | ((short) msg[3] & 0x00ff));
                        if(blockNum==0){

                            String filePath = "./";
                            String fullPath = filePath + this.fileNameInProcess;
                            try (FileInputStream fis = new FileInputStream(fullPath)) {
                                int byteValue;
                                while ((byteValue = fis.read()) != -1) {
                                    this.dataFromClient.add((byte)byteValue);
                                }
                                readData((short)1);
                                return this.protocolResponse;

                            }
                            catch (IOException exp) {
                                System.out.println("sending file failed!");

                            }
                            return null;
                        }
                        else{
                            readData((short)(blockNum +1));
                            return this.protocolResponse;
                        }
                    case LOGRQ:
                    case DELRQ:
                        printAck(msg);
                        this.clientOpcode = TftpEncoderDecoder.Opcode.None;
                        return null;
                    case DISC:
                        System.out.println("entered ack disc protocol");
                        printAck(msg);
                        this.clientOpcode = TftpEncoderDecoder.Opcode.None;;
                        this.shouldTerminate = true;
                        return null;

                }



            case BCAST:
                String fileName = new String(msg, 3, msg.length - 2, StandardCharsets.UTF_8); // we need to save the file name and made it after receiving accept to the operation
                if (msg[2] == 0) // if the file is not found in the server
                    System.out.println("BCAST" + "del" + fileName);
                else
                    System.out.println("BCAST" + "add" + fileName);
                return null;

            case ERROR:
                int errorCode = ((msg[2] & 0xFF) << 8) | (msg[3] & 0xFF);
                String errorMsg = new String(msg, 4, msg.length - 2, StandardCharsets.UTF_8); // we need to save the file name and made it after receiving accept to the operation
                System.out.println("Error " + errorCode + errorMsg);
                return null;

            default:
                return null;

        }
    }

    /**
     * @return true if the connection should be terminated
     */
    public boolean shouldTerminate() {
        // Thread interrupt ?
        return this.shouldTerminate;
    }

    private void readData(short blockNumber) {
        List<Byte> data = new ArrayList<>();
        short capacity = 0;
        for(int i = this.indexACK; i < this.dataFromClient.size(); i++){
            data.add(this.dataFromClient.get(i));
            this.indexACK++;
            capacity++;
            if(capacity == 512 ||  i == this.dataFromClient.size()-1){
                sendDataPacket(capacity, blockNumber, data);
            }
        }

    }
    public void sendDataPacket(short size, short blockNumber,  List<Byte> data){
        byte[] packetSize = new byte[]{(byte) (size >> 8), (byte) (size & 0xff)};

        byte[] dataPacket = new byte[size + 7];
        dataPacket[0] = (byte)0;
        dataPacket[1] = (byte)3;
        dataPacket[2] = packetSize[0];
        dataPacket[3] = packetSize[1];

        byte[] blockNumberByte = new byte[]{(byte) (blockNumber >> 8), (byte) (blockNumber & 0xff)};
        dataPacket[4] = blockNumberByte[0];
        dataPacket[5] = blockNumberByte[1];

        int j = 6;
        for (int i = 0; i < data.size(); i++) {
            dataPacket[j] = data.get(i);
            j++;
        }

        dataPacket[dataPacket.length-1] = (byte) 0;
        this.protocolResponse = dataPacket;

        if(this.indexACK == this.dataFromClient.size()-1){
            this.dataFromClient.clear();
            this.indexACK = 0;
        }
    }


    public void setClientOpcode(String opcode) {
        switch (opcode) {
            case ("LOGRQ"):
                this.clientOpcode = TftpEncoderDecoder.Opcode.fromU16(7);
                break;
            case ("RRQ"):
                this.clientOpcode = TftpEncoderDecoder.Opcode.fromU16(1);
                break;
            case ("WRQ"):
                this.clientOpcode = TftpEncoderDecoder.Opcode.fromU16(2);
                break;
            case ("DIRQ"):
                this.clientOpcode = TftpEncoderDecoder.Opcode.fromU16(6);
                break;
            case ("DELRQ"):
                this.clientOpcode = TftpEncoderDecoder.Opcode.fromU16(8);
                break;
            case ("DISC"):
                this.clientOpcode = TftpEncoderDecoder.Opcode.fromU16(10);
                break;
        }
    }

    public void setFileNameCreated(String name){
        this.fileNameInProcess = name;
    }

    public void printAck(byte[] ack){
        short blockNum = (short) (((short) ack[2]) << 8 | ((short) ack[3] & 0x00ff));
        System.out.println("ACK " + blockNum);

    }
}

