package bgu.spl.net.impl.tftp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder

    private byte[] bytes;
    private Opcode opcode;
    private long optExpectedLen;
    private int current;

    public enum Opcode{
        None, RRQ, WRQ, DATA, ACK, ERROR, DIRQ, LOGRQ, DELRQ, BCAST, DISC;
        public static Opcode fromU16(int opcode) {
            switch (opcode) {
                case 1: return RRQ;
                case 2: return WRQ;
                case 3: return DATA;
                case 4: return ACK;
                case 5: return ERROR;
                case 6: return DIRQ;
                case 7: return LOGRQ;
                case 8: return DELRQ;
                case 9: return BCAST;
                case 10: return DISC;
                default: return None;
            }
        }
    }

    public TftpEncoderDecoder() {
        this.bytes = new byte[1 << 10];//how do we know the size?
        this.opcode = Opcode.None;
        this.optExpectedLen = Long.MAX_VALUE;
        this.current = 0;
    }

    public byte[] poolBytes() {
        byte[] message = new byte[this.current];
        for(int i=0 ; i<this.current; i++){
            message[i] = this.bytes[i];
        }
        this.bytes = new byte[1 << 10];
        this.current = 0;
        this.setOpcode(Opcode.None);
        return message;
    }

    private void setOpcode(Opcode opcode) {
        this.opcode = opcode;
        switch (opcode) {
            case None:
                this.optExpectedLen = Long.MAX_VALUE;
                break;
            case DIRQ:
            case DISC:
                this.optExpectedLen = 2L;
                break;
            case RRQ:
            case WRQ:
            case LOGRQ:
            case DELRQ:
                this.optExpectedLen = 3L;
                break;
            case ACK:
            case BCAST:
                this.optExpectedLen = 4L;
                break;
            case ERROR:
                this.optExpectedLen = 5L;
                break;
            case DATA:
                this.optExpectedLen = 6L;
                break;
        }
    }

    private Opcode getOpcode() {
        return this.opcode;
    }

    private Opcode peekOpcode() {
        assert this.bytes.length >= 2;
        short u16Opcode = (short)((this.bytes[0]) << 8 | ((short)this.bytes[1] & 0x00ff));
        this.opcode = Opcode.fromU16(u16Opcode);
        return this.opcode;
    }



    @Override
    public byte[] decodeNextByte(byte nextByte) {//maybe convert to string?(pop string)
        // TODO: implement this
        System.out.println("decode next byte");
        if (this.current >= this.optExpectedLen && nextByte == 0x0) {
            Opcode opcode = this.getOpcode();
            byte[] message = this.poolBytes();
            this.setOpcode(Opcode.None);
            return message;
        } else {
            pushByte(nextByte);
            if (this.current == 2) {
                this.setOpcode(this.peekOpcode());
            }
            if (!this.haveAddedZero(this.opcode) && this.current == this.optExpectedLen) {
                Opcode opcode = this.getOpcode();
                byte[] message = this.poolBytes();
                this.setOpcode(Opcode.None);
                return message;
            }

            if (this.opcode == Opcode.DATA && this.current == 4) {
                int size = (this.bytes[2] & 0xFF) << 8 | (this.bytes[3] & 0xFF);//short?
                this.optExpectedLen = 6 + size;
            }

            return null;
        }
    }

    private void pushByte(byte nextByte) {
        if (this.current >= this.bytes.length) {
            this.bytes = Arrays.copyOf(this.bytes, this.current * 2);
        }

        this.bytes[this.current++] = nextByte;
    }

    @Override
    public byte[] encode(byte[] message) {
        //TODO: implement this
        Opcode opcode = Opcode.fromU16((short)(((short)message[0]) << 8 | ((short)message[1] & 0x00ff)));
        switch (opcode) {
            case None:
                throw new RuntimeException("Invalid opcode");
            default:
                return message;
        }
    }

    private boolean haveAddedZero(Opcode opcode) {
        switch (opcode) {
            case RRQ:
            case WRQ:
            case ERROR:
            case BCAST:
            case LOGRQ:
            case DELRQ:
            case None:
                return true;
            default:
                return false;
        }
    }


}