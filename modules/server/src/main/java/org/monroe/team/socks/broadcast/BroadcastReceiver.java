package org.monroe.team.socks.broadcast;

import org.monroe.team.socks.exception.ConnectionException;
import org.monroe.team.socks.exception.InvalidProtocolException;

import java.net.*;

public class BroadcastReceiver<MessageType> {

    final int MAX_PACKET_SIZE = 15000;

    private BroadcastListingThread broadcastReceiveThread;
    private ErrorHandlingStrategy errorHandlingStrategy = new DefaultErrorHandlingStrategy(5);
    private int errorIndex = 0;

    private final BroadcastMessageTransport<? extends MessageType> transport;
    private final BroadcastMessageObserver<? super MessageType> observer;

    public BroadcastReceiver(BroadcastMessageTransport<? extends MessageType> transport, BroadcastMessageObserver<? super MessageType> observer) {
        this.transport = transport;
        this.observer = observer;
    }


    public synchronized ErrorHandlingStrategy getErrorHandlingStrategy() {
        return errorHandlingStrategy;
    }

    public synchronized void setErrorHandlingStrategy(ErrorHandlingStrategy errorHandlingStrategy) {
        this.errorHandlingStrategy = errorHandlingStrategy;
    }

    public synchronized void start(int port) throws ConnectionException {
        shutdown();
        try {
            errorIndex = 0;
            broadcastReceiveThread = new BroadcastListingThread(port);
        } catch (SocketException e) {
            throw new ConnectionException("Fail to create datagram socket",e);
        }
        broadcastReceiveThread.start();
    }

    public synchronized void shutdown() {

        if (broadcastReceiveThread == null || broadcastReceiveThread.stillAlive){
            broadcastReceiveThread = null;
            return;
        }
        broadcastReceiveThread.shutdown();
    }

    public synchronized int getPort(){
        if (broadcastReceiveThread == null || !broadcastReceiveThread.stillAlive){
            return -1;
        }
       return broadcastReceiveThread.socket.getLocalPort();
    }


    private synchronized void destroySocket(DatagramSocket socket) {
        socket.close();
    }

    private boolean onReceiveError(Exception e) {
        errorIndex += 1;
        return errorHandlingStrategy.handleReceiveError(e,errorIndex);
    }

    private void onReceiveMessage(String plainMessage, InetAddress address) {
        errorIndex = 0;
        try {
            MessageType message = transport.fromString(plainMessage);
            observer.onMessage(message, address);

        } catch (InvalidProtocolException e) {
            errorHandlingStrategy.handleParseError(e, plainMessage, address);
        }
    }

    public synchronized boolean isAlive() {
        return (broadcastReceiveThread != null && broadcastReceiveThread.stillAlive);
    }


    private class BroadcastListingThread extends Thread {

        private final int port;
        private boolean stillAlive = true;
        private final DatagramSocket socket;

        public BroadcastListingThread(int port) throws SocketException {
            super("BroadcastReceiverThread_"+port);
            this.port = port;
            try {
                socket = new DatagramSocket(port, InetAddress.getByName("0.0.0.0"));
                socket.setBroadcast(true);
            } catch (UnknownHostException e) {
                //should never happends
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            while (stillAlive){
                if (isInterrupted()){
                    stillAlive = false;
                    return;
                }
                byte[] byteData = new byte[MAX_PACKET_SIZE];
                DatagramPacket datagramPacket = new DatagramPacket(byteData,byteData.length);
                boolean readSuccessfully = false;
                try {
                    socket.receive(datagramPacket);
                    readSuccessfully = true;
                }catch (SocketException e){
                    //if not alive most likely socket was closed, but if alive then something else
                    if (stillAlive){
                        stillAlive = BroadcastReceiver.this.onReceiveError(e);
                    }
                }catch (Exception e) {
                    stillAlive = BroadcastReceiver.this.onReceiveError(e);
                }

                if (readSuccessfully){
                    String plainMessage = new String(datagramPacket.getData()).trim();
                    InetAddress address = datagramPacket.getAddress();
                    BroadcastReceiver.this.onReceiveMessage(plainMessage, address);
                }
            }
            BroadcastReceiver.this.destroySocket(socket);
        }

        public void shutdown() {
            stillAlive = false;
            BroadcastReceiver.this.destroySocket(socket);
        }
    }



    public static interface ErrorHandlingStrategy{
        boolean handleReceiveError(Exception e, int errorIndex);
        void handleParseError(InvalidProtocolException e, String plainMessage, InetAddress address);
    }

    public static class DefaultErrorHandlingStrategy implements ErrorHandlingStrategy {

        private final int errorCountBeforeShutdown;

        public DefaultErrorHandlingStrategy(int errorCountBeforeShutdown) {
            this.errorCountBeforeShutdown = errorCountBeforeShutdown;
        }

        @Override
        public boolean handleReceiveError(Exception e, int errorIndex) {
            e.printStackTrace();
            return errorIndex >= errorCountBeforeShutdown;
        }

        @Override
        public void handleParseError(InvalidProtocolException e, String plainMessage, InetAddress address) {
            e.printStackTrace();
        }
    }

    public static interface BroadcastMessageObserver<MessageType>{
        public void onMessage(MessageType messageType, InetAddress address);
    }
}
