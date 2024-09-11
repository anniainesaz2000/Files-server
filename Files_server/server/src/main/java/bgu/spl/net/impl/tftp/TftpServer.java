package bgu.spl.net.impl.tftp;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.impl.echo.LineMessageEncoderDecoder;
import bgu.spl.net.srv.BaseServer;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.Server;

import java.util.function.Supplier;

public class TftpServer extends BaseServer<byte[]> {
    //TODO: Implement this
    //interrupt and create threads

    public TftpServer(int port, Supplier<BidiMessagingProtocol<byte[]>> protocolFactory, Supplier<MessageEncoderDecoder<byte[]>> encdecFactory){
        super(port, protocolFactory, encdecFactory);
    }

    protected void execute(BlockingConnectionHandler<byte[]> handler) {
        new Thread(handler).start();
    }

    public static void main(String[] args) {

            String arg = (args.length>0) ? args[0] : "7777";
            int port = Integer.decode(arg).intValue();
            TftpServer server = new TftpServer(port, TftpProtocol::new, TftpEncoderDecoder::new);
            server.serve();
//            Server.threadPerClient(
//                    //Integer.parseInt(args[1]), //port
//                    7777,
//                    (() -> new TftpProtocol()), //protocol factory
//                    (() -> new TftpEncoderDecoder())//message encoder decoder factory
//                    //TftpEncoderDecoder::new
//            ).serve();

    }


}
