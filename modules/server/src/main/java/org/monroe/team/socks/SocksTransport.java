package org.monroe.team.socks;

import org.monroe.team.socks.exception.SendFailException;
import org.monroe.team.socks.exception.HandshakeException;
import org.monroe.team.socks.exception.InvalidProtocolException;
import org.monroe.team.socks.exception.ProtocolInitializationException;

import java.io.*;
import java.net.Socket;

public class SocksTransport implements ReaderTask.DataObserver{

    private final Socket socket;
    private Protocol  protocol;
    private Thread readThread;
    private ReaderTask  task;
    private DataInputStream in;
    private DataOutputStream out;
    private ConnectionObserver<Object> observer;

    public SocksTransport(Socket socket) {
        this.socket = socket;
    }

    void init() throws IOException {
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    public void send(Object msg) throws SendFailException {
        try {
            protocol.send(msg);
        } catch (Exception e) {
            throw new SendFailException("Couldn`t send", e);
        }
    }

    void setObserver(ConnectionObserver observer) {
        this.observer = observer;
    }

    void open(Class<? extends Protocol > protocolType, boolean doHandshake) throws InvalidProtocolException, ProtocolInitializationException {
        if (doHandshake){
            handshake(protocolType);
        }
        try {
            protocol = protocolType.newInstance();
        } catch (Exception e) {
            throw new InvalidProtocolException("Couldn`t instantiate protocol",e);
        }
        try {
            protocol.create(in,out);
        } catch (Exception e) {
            throw new ProtocolInitializationException("Could create protocol",e);
        }

        task = protocol.createReaderTask(this);
        readThread = new Thread(task);
        readThread.start();
    }

    void accept() throws InvalidProtocolException, ProtocolInitializationException {
        String protocolName = null;
        try {
            protocolName = in.readUTF();
        } catch (IOException e) {
            throw new HandshakeException("Couldn`t read class",e);
        }
        Class<? extends Protocol > protocolClass = null;
        try {
           protocolClass = (Class<? extends Protocol >) Class.forName(protocolName);
        } catch (ClassNotFoundException e) {
            sendHandshakeRespond(out,"INVALID_PROTOCOL");
            throw new InvalidProtocolException("Protocol class not found",e);
        }

        sendHandshakeRespond(out, "OK");
        open(protocolClass, false);
    }

    private void sendHandshakeRespond(DataOutputStream writer, String handShakeRespond) {
        try {
            writer.writeUTF(handShakeRespond);
            writer.flush();
        } catch (IOException e) {
            throw new HandshakeException("Could`n send "+handShakeRespond+" ",e);
        }
    }

    private boolean handshake(Class<? extends Protocol> protocolType) throws InvalidProtocolException {
        String response;
        try {
            out.writeUTF(protocolType.getName());
            out.flush();
            //TODO: implement timeout here
            response = in.readUTF();
        } catch (IOException ex){
            throw new HandshakeException("Handshake fail",ex);
        }
        if ("OK".equals(response)) {
            return false;
        } else if ("INVALID_PROTOCOL".equals(response)){
            throw new InvalidProtocolException("Server protcol invalid",null);
        }
        throw new HandshakeException("Invalid response "+response,null);
    }


    public void destroy() {

        if (task != null){
            task.kill();
            readThread.interrupt();
        }

        if(protocol != null){
            try {
                protocol.sendShutdownSignal();
                protocol.clearResources();
            } catch (Exception e) {
                //TODO: log it
                e.printStackTrace();
            }
        }

        if (in != null){
            try {
                in.close();
            } catch (IOException e) {e.printStackTrace();}
        }
        if (out != null){
            try {
                out.close();
            } catch (IOException e) {e.printStackTrace();}
        }
        if (socket!=null){
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void onDataChunk(Object data) {
        observer.onData(data);
    }

    @Override
    public void onError(Exception e) {
        observer.onReadError(e);
    }

    @Override
    public void onShutdownRequested(boolean normal) {
        if(protocol != null){
            protocol.clearResources();
            protocol = null;
        }
        destroy();
        observer.onDisconnected(normal);
    }

    public Protocol getProtocol() {
        return protocol;
    }

    @Override
    public String toString() {
        return "SocksTransport{" +
                "socket=" + socket +
                ", protocol=" + protocol +
                '}';
    }

    public static interface Protocol<DataType>{
        void create(DataInputStream inputStream, DataOutputStream outputStream) throws Exception;
        void send(DataType message) throws Exception;
        void sendShutdownSignal() throws Exception;
        ReaderTask<DataType> createReaderTask(ReaderTask.DataObserver observer);
        void clearResources();
    }

    public static interface ConnectionObserver<DataType>{
        public void onData(DataType data);
        public void onReadError(Exception e);
        void onDisconnected(boolean requestByPartner);
    }

}
