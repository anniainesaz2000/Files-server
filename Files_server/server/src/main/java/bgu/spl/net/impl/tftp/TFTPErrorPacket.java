package bgu.spl.net.impl.tftp;

import java.nio.charset.StandardCharsets;

public class TFTPErrorPacket {
    private final short opcode;
    private final short errorCode;
    private final String errorMessage;

    public TFTPErrorPacket(short errorCode, String errorMessage) {
        this.opcode = 5; // Error opcode is 5
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    // Method to convert the error packet to a byte array for sending
    public byte[] toByteArray() {
        byte[] opcodeBytes = {(byte) ((opcode >> 8) & 0xFF), (byte) (opcode & 0xFF)};
        byte[] errorCodeBytes = {(byte) ((errorCode >> 8) & 0xFF), (byte) (errorCode & 0xFF)};
        byte[] errorMessageBytes = errorMessage.getBytes(StandardCharsets.UTF_8);

        byte[] packet = new byte[5 + errorMessageBytes.length];
        packet[0] = opcodeBytes [0];
        packet[1] = opcodeBytes [1];
        packet[2] = errorCodeBytes[0];
        packet[3] = errorCodeBytes[1];
        int j=4;
        for(int i = 0; i<errorMessageBytes.length; i++){
            packet[j] = errorMessageBytes[i];
            j++;
        }
        packet[packet.length-1] = 0;

        return packet;
    }

//    public static void main(String[] args) {
//        // Example: Create and print an error packet
//        TFTPErrorPacket errorPacket = new TFTPErrorPacket((short) 2, "File not found");
//        byte[] packetBytes = errorPacket.toByteArray();
//
//        System.out.println("Opcode: " + ((packetBytes[0] << 8) | (packetBytes[1] & 0xFF)));
//        System.out.println("ErrorCode: " + ((packetBytes[2] << 8) | (packetBytes[3] & 0xFF)));
//        System.out.println("ErrorMessage: " + new String(packetBytes, 4, packetBytes.length - 4, StandardCharsets.UTF_8));
//    }
}
