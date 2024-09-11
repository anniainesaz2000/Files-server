package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Arrays;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


class holder{
    static ConcurrentHashMap<Integer,Boolean> ids_login = new ConcurrentHashMap<>();
    static ConcurrentHashMap<Integer,String> idsToUsermames = new ConcurrentHashMap<>();
    private static List<String> fileNames; // Make it private to encapsulate it

    static {
        initializeFileNames();
    }

    private static void initializeFileNames() {
        Path dir = Paths.get("./Flies/");

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

class HashMapErrors{
    public static ConcurrentHashMap<Integer,TFTPErrorPacket> errors = new ConcurrentHashMap<>();
    public HashMapErrors(){
        TFTPErrorPacket errorPacket0 = new TFTPErrorPacket((short) 0, "Not defined, see error message (if any)");
        errors.putIfAbsent(0, errorPacket0);

        TFTPErrorPacket errorPacket1 = new TFTPErrorPacket((short) 1, "File not found – RRQ DELRQ of non-existing file");
        errors.putIfAbsent(1, errorPacket1);

        TFTPErrorPacket errorPacket2 = new TFTPErrorPacket((short) 2, "Access violation – File cannot be written, read or deleted");
        errors.putIfAbsent(2, errorPacket2);

        TFTPErrorPacket errorPacket3 = new TFTPErrorPacket((short) 3, "Disk full or allocation exceeded – No room in disk");
        errors.putIfAbsent(3, errorPacket3);

        TFTPErrorPacket errorPacket4 = new TFTPErrorPacket((short) 4, "Illegal TFTP operation – Unknown Opcode");
        errors.putIfAbsent(4, errorPacket4);

        TFTPErrorPacket errorPacket5 = new TFTPErrorPacket((short) 5, "File already exists – File name exists on WRQ");
        errors.putIfAbsent(5, errorPacket5);

        TFTPErrorPacket errorPacket6 = new TFTPErrorPacket((short) 6, "User not logged in – Any opcode received before Login completes");
        errors.putIfAbsent(6, errorPacket6);

        TFTPErrorPacket errorPacket7 = new TFTPErrorPacket((short) 7, " User already logged in – Login username already connected");
        errors.putIfAbsent(7, errorPacket7);


    }

}
public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections; //should be String?
    private String userName;
    private HashMapErrors hashMapError = new HashMapErrors();
    private List<Byte> clientFile = new ArrayList<>();
    private List<Byte> dataFromServer = new ArrayList<>();
    private int indexACK = 0;
    private String clientFileName;




    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        this.shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        holder.ids_login.put(connectionId, false);

    }

    @Override
    public void process(byte[] message) {//cope with different kinds of opcodes (client requests)
        // TODO implement this

        TftpEncoderDecoder.Opcode opcode = peekOpcode(message);
        switch (opcode) {
            case None:
                break;
            case LOGRQ:
                if(holder.ids_login.get(this.connectionId)){
                    byte[] errorAlreadyLoggedIn = makeError(7);
                    this.connections.send(this.connectionId, errorAlreadyLoggedIn);
                    System.out.println("LOGRQ failed! (already logged in)");
                }

                else{
                    //byte[] userNameBytes = Arrays.copyOfRange(message,2, message.length -2);
                    this.userName = new String(message,2,message.length -2, StandardCharsets.UTF_8);
                    if(holder.idsToUsermames.containsValue(this.userName)){
                        byte[] errorUserNameExists = makeError(0);//value 7?
                        this.connections.send(this.connectionId, errorUserNameExists);
                        System.out.println("LOGRQ failed! (user name exists)");
                    }

                    else{
                        holder.ids_login.put(this.connectionId,true);
                        holder.idsToUsermames.put(this.connectionId, this.userName);
                        byte[] ack ={0,4,0,0};
                        this.connections.send(this.connectionId, ack);
                        System.out.println("LOGRQ done!");
                    }
                }
                break;
            case RRQ:
                if(!holder.ids_login.get(this.connectionId)){
                    byte[] errorAlreadyLoggedIn = makeError(6);
                    this.connections.send(this.connectionId, errorAlreadyLoggedIn);
                    System.out.println("RRQ failed!");
                }
                else{
                    String fileName = new String(message,2,message.length -2, StandardCharsets.UTF_8);

                    String filePath = "./Flies/";
                    String fullPath = filePath + fileName;
                    try (FileInputStream fis = new FileInputStream(fullPath)) {
                        int byteValue;
                        while ((byteValue = fis.read()) != -1) {
                            this.dataFromServer.add((byte)byteValue);
                            }
                        readData((short)1);
                        System.out.println("RRQ done!");

                        }
                    catch (IOException exp) {
                        byte[] errorFileDoesntExists = makeError(1);
                        this.connections.send(this.connectionId, errorFileDoesntExists);
                        System.out.println("RRQ failed!");

                    }
                    //readData((short)1);
                }

                break;

            case WRQ:
                if(!holder.ids_login.get(this.connectionId)){
                    byte[] errorAlreadyLoggedIn = makeError(6);
                    this.connections.send(this.connectionId, errorAlreadyLoggedIn);
                }
                else{
                    this.clientFileName = new String(message,2,message.length -2, StandardCharsets.UTF_8);
                    String filePath = "./Flies/";
                    String fullPath = filePath + this.clientFileName;
                    try (FileInputStream fis = new FileInputStream(fullPath)) {
                        byte[] errorAlreadyExist = makeError(5);
                        this.connections.send(this.connectionId, errorAlreadyExist);
                    }catch (IOException e){
                        byte[] ack ={0,4,0,0};
                        this.connections.send(this.connectionId, ack);

                    }

                }


                break;
            case ACK:

                if(!holder.ids_login.get(this.connectionId)){
                    byte[] errorAlreadyLoggedIn = makeError(6);
                    this.connections.send(this.connectionId, errorAlreadyLoggedIn);
                }
                else{
                    System.out.println("server got ACK");
                    short blockNum = (short)(((short)message[2]) << 8 | ((short)message[3] & 0x00ff));//parameter for readFile
                    System.out.println("block number ack: " + blockNum);
                    readData((short)(blockNum + 1));
                }
                break;
            //case ERROR:

                //break;
            case DATA:
                if(!holder.ids_login.get(this.connectionId)){
                    byte[] errorAlreadyLoggedIn = makeError(6);
                    this.connections.send(this.connectionId, errorAlreadyLoggedIn);
                }
                else{
                    short size  = (short)(((short)message[2]) << 8 | ((short)message[3] &  0x00ff));
                    for (int i = 6; i < message.length; i++) {
                        this.clientFile.add(message[i]);
                    }

                    if(size<512){
                        String filePath = "./Flies/";
                        String fullPath = filePath + this.clientFileName;
                        try (FileOutputStream fos = new FileOutputStream(fullPath)) {
                            //create a list that contains all the filenames exist in files folder and done written - field + change evey place that we check if exists in the folder to check from the list
                            byte[] dataToWrite = new byte[this.clientFile.size()];
                            for (int i = 0; i < this.clientFile.size(); i++) {
                                dataToWrite[i] = this.clientFile.get(i);
                            }
                            fos.write(dataToWrite);
                            holder.addFileName(this.clientFileName);
                            this.clientFile.clear();
                            holder.ids_login.forEach((key, value) -> {
                                if (value) {
                                    byte[] bcast = bcastPacket(true, this.clientFileName);
                                    this.connections.send(key, bcast);
                                }
                            });

                        } catch (IOException er) {
                            byte[] errorAlreadyLoggedIn = makeError(2);
                            this.connections.send(this.connectionId, errorAlreadyLoggedIn);
                        }
                    }

                    byte[] ack ={0,4,message[4],message[5]};
                    this.connections.send(this.connectionId, ack);
                }

                break;

            case DIRQ:
                if(!holder.ids_login.get(this.connectionId)){
                    byte[] errorAlreadyLoggedIn = makeError(6);
                    this.connections.send(this.connectionId, errorAlreadyLoggedIn);
                }
                else{
                    for(String name: holder.getFileNames()){
                        byte[] bytename = name.getBytes();

                        for(byte b:bytename){
                            this.dataFromServer.add(b);
                        }
                        this.dataFromServer.add((byte)0x0);

                    }
                    short size;
                    if(this.dataFromServer.size()<512){
                        size = (short)(this.dataFromServer.size() - 1);
                    }
                    else {
                        size = 511;
                    }
                    readData((short)1);
                    //sendDataPacket(size, (short)1,  this.dataFromServer);

                }

                break;

            case DELRQ:
                if(!holder.ids_login.get(this.connectionId)){
                    byte[] errorAlreadyLoggedIn = makeError(6);
                    this.connections.send(this.connectionId, errorAlreadyLoggedIn);
                }
                else{
                //delete from fileNames list
                    String fileToDelete = new String(message,2,message.length -2, StandardCharsets.UTF_8);
                    String filePath = "./Flies/" + fileToDelete;

                    // Create a Path object
                    Path path = Paths.get(filePath);

                    try {
                        // Delete the file using Files.delete method
                        Files.delete(path);
                        holder.deleteFileName(fileToDelete);
                        byte[] ack ={0,4,0,0};
                        this.connections.send(this.connectionId, ack);

                        holder.ids_login.forEach((key, value) -> {
                            if (value) {
                                byte[] bcast = bcastPacket(false, fileToDelete);
                                this.connections.send(key, bcast);
                            }
                        });

                        System.out.println("File deleted successfully");
                    } catch (IOException e) {
                        byte[] errorFileNotFound = makeError(1);
                        this.connections.send(this.connectionId, errorFileNotFound);
                        System.err.println("Error deleting the file: " + e.getMessage());
                    }
                }

                break;

//            case BCAST:
//                if(!holder.ids_login.get(this.connectionId)){
//                    byte[] errorAlreadyLoggedIn = makeError(6);
//                    this.connections.send(this.connectionId, errorAlreadyLoggedIn);
//                }
//                else{
//
//                }
//
//                break;

            case DISC:
                if(!holder.ids_login.get(this.connectionId)){
                    byte[] errorAlreadyLoggedIn = makeError(6);
                    this.connections.send(this.connectionId, errorAlreadyLoggedIn);
                }
                else{
                    if (holder.ids_login.containsKey(this.connectionId)){
                        holder.ids_login.put(this.connectionId, false);
                    }
                    byte[] ack ={0,4,0,0};
                    this.connections.send(this.connectionId, ack);
                    holder.idsToUsermames.remove(this.connectionId);
                    this.connections.disconnect(this.connectionId);
                    this.shouldTerminate = true;
                    System.out.println("User disconnected successfully");

                }

                break;
        }

    }

    private byte[] makeError(int errorCode){

        switch(errorCode){
            case 0:
                return this.hashMapError.errors.get(0).toByteArray();
            case 1:
                return this.hashMapError.errors.get(1).toByteArray();
            case 2:
                return this.hashMapError.errors.get(2).toByteArray();
            case 3:
                return this.hashMapError.errors.get(3).toByteArray();
            case 4:
                return this.hashMapError.errors.get(4).toByteArray();
            case 5:
                return this.hashMapError.errors.get(5).toByteArray();
            case 6:
                return this.hashMapError.errors.get(6).toByteArray();
            case 7:
                return this.hashMapError.errors.get(7).toByteArray();
            default:
                return null;
        }
    }

    private void readData(short blockNumber) {

        List<Byte> data = new ArrayList<>();

        for(int i = this.indexACK; i < this.dataFromServer.size(); i++){
            data.add(this.dataFromServer.get(i));
            this.indexACK++;

            if(data.size()==512 ||  i == this.dataFromServer.size()-1){
                sendDataPacket((short)data.size(), blockNumber, data);
                data.clear();
                break;
            }
        }
        //sendDataPacket(capacity, blockNumber, data);

    }

    public void sendDataPacket(short size, short blockNumber,  List<Byte> data){
        System.out.println("entered sendDataPacket");
        System.out.println("data packet SIZE: " + size);
        System.out.println("should be equals to = : " + data.size());
        byte[] packetSize = new byte[]{(byte) (size >> 8), (byte) (size & 0xff)};
        System.out.println("packetSize length:" + packetSize.length);

        byte[] dataPacket = new byte[size + 6];
        System.out.println("dataPacket length:" + dataPacket.length);
        dataPacket[0] = (byte)0;
        dataPacket[1] = (byte)3;
        dataPacket[2] = packetSize[0];
        dataPacket[3] = packetSize[1];

        byte[] blockNumberByte = new byte[]{(byte) (blockNumber >> 8), (byte) (blockNumber & 0xff)};
        System.out.println("blockNumberByte length:" + blockNumberByte.length);
        dataPacket[4] = blockNumberByte[0];
        dataPacket[5] = blockNumberByte[1];

        int j = 6;
        for (int i = 0; i < data.size(); i++) {
            dataPacket[j] = data.get(i);
            j++;
        }

        System.out.println("out of loop...");
        dataPacket[dataPacket.length-1] = (byte) 0;
        this.connections.send(this.connectionId, dataPacket);
        System.out.println("data packet was sent to client----------------");

        if(this.indexACK == this.dataFromServer.size()-1){
            this.dataFromServer.clear();
            this.indexACK = 0;
        }
    }


    private byte[] bcastPacket(boolean wasAdded, String fileName){
        byte [] fileNameBytes = fileName.getBytes();
        byte [] bcast = new byte [4 + fileNameBytes.length];
        bcast[0] = 0;
        bcast [1] = 9;
        if(wasAdded){
            bcast[2] = 1;
        }
        else{
            bcast[2] = 0;
        }

        int j = 3;
        for(int i = 0; i < fileNameBytes.length; i ++){
            bcast[j] = fileNameBytes[i];
            j++;
        }

        bcast[bcast.length-1] = 0;

        return bcast;

    }

    private TftpEncoderDecoder.Opcode peekOpcode(byte[] message) {//Anni
        assert message.length >= 2;
        short u16Opcode = (short)((message[0]) << 8 | ((short) message[1] & 0x00ff));
        return TftpEncoderDecoder.Opcode.fromU16(u16Opcode);
    }

    @Override
    public boolean shouldTerminate() {//check if we should call it frim DISC
        // TODO implement this
        if(this.shouldTerminate){
            Thread.currentThread().interrupt();
        }
        return this.shouldTerminate;
        //connectionHandler.close()?

    } 


    
}
