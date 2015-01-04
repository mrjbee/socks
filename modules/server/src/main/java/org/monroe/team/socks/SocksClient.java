package org.monroe.team.socks;

import org.monroe.team.socks.exception.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class SocksClient {

    private final InetAddress serverAddress;
    private final int port;

    public SocksClient(int port, InetAddress serverAddress) {
        this.port = port;
        this.serverAddress = serverAddress;
    }

    public <DataType> SocksConnection<DataType> getConnection(
                                         Class<? extends SocksTransport.Protocol<DataType>> protocol,
                                         SocksTransport.ConnectionObserver<DataType> observer)
            throws ConnectionException, InvalidProtocolException {
        Socket socket = null;
        try {
            socket = new Socket(serverAddress,port);
        }catch (IOException e) {
            throw new ConnectionException("Couldnt open socket",e);
        }

        SocksTransport connection = new SocksTransport(socket);

        try {
            connection.init();
        } catch (IOException e) {
            connection.destroy();
            throw new ConnectionException("Fail to init connection",e);
        }
        connection.setObserver(observer);

        try {
            connection.open(protocol, true);
        } catch (HandshakeException e) {
            connection.destroy();
            throw new ConnectionException("Handshake fails", e);
        } catch (InvalidProtocolException e){
            //TODO: everything good with a sockets so could be reused in future
            connection.destroy();
            throw e;
        } catch (ProtocolInitializationException e) {
            connection.destroy();
            throw new ConnectionException("Protocol initialization fails", e);
        }
        return new DefaultSockClient<DataType>(connection);
    }

    public void closeConnection(SocksConnection<?> connection) {
        DefaultSockClient<?> sockClient = (DefaultSockClient<?>) connection;
        sockClient.socksTransport.destroy();
    }

    private class DefaultSockClient<DataType> implements SocksConnection<DataType>{

        private final SocksTransport socksTransport;

        public DefaultSockClient(SocksTransport socksTransport) {
            this.socksTransport = socksTransport;
        }

        @Override
        public void send(DataType dataType) throws SendFailException {
            socksTransport.send(dataType);
        }
    }

}
