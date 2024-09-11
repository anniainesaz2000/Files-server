package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {
    private ConcurrentHashMap<Integer, ConnectionHandler<T>> activeClientsMap;
    private static class SingletonHolder{
        private static volatile ConnectionsImpl instance = new ConnectionsImpl();
    }

    private ConnectionsImpl(){
        this.activeClientsMap = new ConcurrentHashMap<>();//do we need Integer, ConnectionHandler<T> in the <>?
    }

    public static ConnectionsImpl getInstance(){
        return SingletonHolder.instance;
    }

    public void connect(int connectionId, ConnectionHandler<T> handler){//return boolean?
        this.activeClientsMap.putIfAbsent(connectionId, handler);

    }
    public boolean send(int connectionId, T msg){
        ConnectionHandler ch = this.activeClientsMap.get((Integer)connectionId);
        try{
            this.activeClientsMap.get((Integer)connectionId).send(msg);
        }catch (Exception e){
            return false;
        }
        return true;
    }
    public void disconnect(int connectionId){
        try{
            this.activeClientsMap.get((Integer)connectionId).close();
            this.activeClientsMap.remove((Integer)connectionId);
        }catch (Exception e){}
    }

}
