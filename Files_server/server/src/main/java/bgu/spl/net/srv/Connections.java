package bgu.spl.net.srv;

import java.io.IOException;

public interface Connections<T> {

    public void connect(int connectionId, ConnectionHandler<T> handler);

    public boolean send(int connectionId, T msg);

    public void disconnect(int connectionId);
}
