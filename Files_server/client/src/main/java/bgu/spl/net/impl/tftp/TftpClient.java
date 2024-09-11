package bgu.spl.net.impl.tftp;

import com.sun.source.tree.SynchronizedTree;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;




public class TftpClient {

    static boolean KeyboardON = true;
    static Boolean userControl = true;
    static Boolean LockKeyBoard = true;
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    public static void main(String[] args) {
        Thread inputThread;
        Thread listenerThread;


        if (args.length == 0) {
            args = new String[]{"localhost", "hello"};
        }

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, message");
            System.exit(1);
        }

        AtomicReference<String> user = new AtomicReference<>("");
        try (Socket sock = new Socket(args[0], Integer.parseInt(args[1]));
             BufferedInputStream in = new BufferedInputStream(new BufferedInputStream(sock.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(new BufferedOutputStream(sock.getOutputStream()))) {

            TftpClientProtocol protocol = new TftpClientProtocol();
            TftpEncoderDecoder encdec = new TftpEncoderDecoder();

            inputThread = new Thread(() -> {
                Scanner scanner = new Scanner(System.in);

                while (!protocol.shouldTerminate()) {
                    user.set(scanner.nextLine());
                    preparingTask(user.get(), out, encdec, protocol);
                    while (!KeyboardON) {
                        user.set(scanner.nextLine());
                        preparingTask(user.get(), out, encdec, protocol);
                    }
                    synchronized (LockKeyBoard) {
                        while (userControl) {
                            try {
                                LockKeyBoard.wait();
                            } catch (InterruptedException e) {
                            }
                        }

                    }
                }

            }, "inputThread");


            listenerThread = new Thread(() -> {
                int read;
                while (!protocol.shouldTerminate() && sock.isConnected()) {
                    try {
                        while ((read = in.read()) >= 0) {
                            synchronized (LockKeyBoard) {
                                byte[] response = encdec.decodeNextByte((byte) read);
                                if (response != null) {
                                    byte[] MSG = protocol.process(response);
                                    if (MSG != null) {
                                        sendClientPacket(MSG, out);
                                    }
                                }
                                userControl = false;
                                LockKeyBoard.notifyAll();

                            }

                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, "listenerThread");
            inputThread.start();
            listenerThread.start();
        }
        catch (IOException e){
            throw new RuntimeException(e);
        }
        try {
            listenerThread.join();
            inputThread.join();
        } catch (InterruptedException e) {
        }
    }

    private static void preparingTask(String message, BufferedOutputStream buffer, TftpEncoderDecoder encoderdecoder, TftpClientProtocol protocol) {
        //byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        String[] split = message.split(" ");
        protocol.setClientOpcode(split[0]);

        switch (protocol.clientOpcode) {
            case LOGRQ:
                if (split.length <= 1) {

                    System.out.println("invalid command");
                    userControl = false;
                } else {
                    List<Byte> userNameBytes = new ArrayList<>();

                    int userNameSize = 0;
                    for (int i = 1; i < split.length; i++) {
                        byte[] byteArray = split[i].getBytes();
                        for (int j = 0; j < byteArray.length; j++) {
                            userNameBytes.add(byteArray[j]);
                        }
                        userNameSize = userNameSize + byteArray.length;
                    }
                    byte[] messageToBytes = new byte[3 + userNameSize];
                    messageToBytes[0] = 0;
                    messageToBytes[1] = 7;
                    int index = 2;
                    for (byte b : userNameBytes) {
                        messageToBytes[index] = b;
                        index++;
                    }
                    messageToBytes[messageToBytes.length - 1] = 0;
                    userControl = true;

                    sendClientPacket(messageToBytes, buffer);

                }
                break;

            case DELRQ:
                if (split.length <= 1) {
                    userControl = false;
                    System.out.println("invalid command");
                } else {
                    List<Byte> fileNameBytes = new ArrayList<>();

                    int fileNameSize = 0;
                    for (int i = 1; i < split.length; i++) {
                        byte[] byteArray = split[i].getBytes();
                        for (int j = 0; j < byteArray.length; j++) {
                            fileNameBytes.add(byteArray[j]);
                        }
                        fileNameSize = fileNameSize + byteArray.length;
                    }
                    byte[] messageToBytes = new byte[3 + fileNameSize];
                    messageToBytes[0] = 0;
                    messageToBytes[1] = 8;
                    int index = 2;
                    for (byte b : fileNameBytes) {
                        messageToBytes[index] = b;
                        index++;
                    }
                    messageToBytes[messageToBytes.length - 1] = 0;
                    userControl = true;

                    sendClientPacket(messageToBytes, buffer);

                }
                break;

            case RRQ:
                String fileName = "";
                if (split.length > 1) {
                    for (int i = 1; i < split.length; i++) {
                        fileName = fileName + " " + split[i];
                    }
                }

                if (split.length <= 1) {
                    userControl = false;
                    System.out.println("invalid command");
                } else if (holder.getFileNames().contains(fileName)) {
                    userControl = false;
                    System.out.println("file already exists!");
                } else {
                    List<Byte> fileNameBytes = new ArrayList<>();

                    int fileNameSize = 0;
                    for (int i = 1; i < split.length; i++) {
                        byte[] byteArray = split[i].getBytes();
                        for (int j = 0; j < byteArray.length; j++) {
                            fileNameBytes.add(byteArray[j]);
                        }
                        fileNameSize = fileNameSize + byteArray.length;
                    }
                    byte[] messageToBytes = new byte[3 + fileNameSize];
                    messageToBytes[0] = 0;
                    messageToBytes[1] = 1;
                    int index = 2;
                    for (byte b : fileNameBytes) {
                        messageToBytes[index] = b;
                        index++;
                    }
                    messageToBytes[messageToBytes.length - 1] = 0;

                    protocol.setFileNameCreated(fileName);

                    userControl = true;
                    sendClientPacket(messageToBytes, buffer);


                }
                break;

            case WRQ:
                String fileNameW = "";
                if (split.length > 1) {
                    for (int i = 1; i < split.length; i++) {
                        fileName = fileNameW + " " + split[i];
                    }
                }

                if (split.length <= 1) {
                    userControl = false;
                    System.out.println("invalid command");
                } else if (!holder.getFileNames().contains(fileNameW)) {
                    userControl = false;
                    System.out.println("file doesn't exist!");
                } else {
                    List<Byte> fileNameBytes = new ArrayList<>();

                    int fileNameSize = 0;
                    for (int i = 1; i < split.length; i++) {
                        byte[] byteArray = split[i].getBytes();
                        for (int j = 0; j < byteArray.length; j++) {
                            fileNameBytes.add(byteArray[j]);
                        }
                        fileNameSize = fileNameSize + byteArray.length;
                    }
                    byte[] messageToBytes = new byte[3 + fileNameSize];
                    messageToBytes[0] = 0;
                    messageToBytes[1] = 2;
                    int index = 2;
                    for (byte b : fileNameBytes) {
                        messageToBytes[index] = b;
                        index++;
                    }
                    messageToBytes[messageToBytes.length - 1] = 0;
                    userControl = true;
                    protocol.setFileNameCreated(fileNameW);
                    sendClientPacket(messageToBytes, buffer);

                }

                break;

            case DIRQ:

                byte[] messageBytesDIRQ = new byte[2];
                messageBytesDIRQ[0] = 0;
                messageBytesDIRQ[1] = 6;
                userControl = true;

                sendClientPacket(messageBytesDIRQ, buffer);


                break;

            case DISC:
                byte[] messageBytesDISC = new byte[2];
                messageBytesDISC[0] = 0;
                messageBytesDISC[1] = 10;
                userControl = true;

                sendClientPacket(messageBytesDISC, buffer);

                break;

            default:
                userControl = false;
                System.out.println("invalid command");
                break;
        }

    }


    public static void sendClientPacket(byte[] message, BufferedOutputStream out) {
        try {
            out.write(message);
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
