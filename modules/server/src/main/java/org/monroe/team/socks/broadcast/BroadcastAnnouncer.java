package org.monroe.team.socks.broadcast;


import org.monroe.team.socks.exception.ConnectionException;
import org.monroe.team.socks.exception.InvalidProtocolException;
import org.monroe.team.socks.exception.SendFailException;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.List;

public class BroadcastAnnouncer <MessageType> {

    private final DatagramSocket socket;
    private final BroadcastMessageTransport<? super MessageType> transport;

    public BroadcastAnnouncer(BroadcastMessageTransport<? super MessageType> transport) throws ConnectionException {
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
        } catch (SocketException e) {
            throw new ConnectionException("Couldn`t open UDP socket", e);
        }
        this.transport = transport;
    }

    public synchronized void destroy(){
        forceDestroy();
    }

    public void forceDestroy(){
        socket.close();
    }

    public synchronized boolean isAlive(){
        return !socket.isClosed();
    }

    public synchronized int getPort(){
        if (!isAlive()) return -1;
        return socket.getLocalPort();
    }

    public void sendMessage(int port, MessageType msg) throws InvalidProtocolException, SendFailException {
        sendMessage(port, msg, new NoOpSendStrategy());
    }

    public synchronized void sendMessage(int port, MessageType msg, SendStrategy sendStrategy) throws InvalidProtocolException, SendFailException {
        if (!isAlive()){
            throw new IllegalStateException("Socket already closed");
        }

        byte[] msgAsBytes = transport.toString(msg).getBytes();
        Enumeration<NetworkInterface> networkInterfaceEnumeration = null;
        try {
            networkInterfaceEnumeration = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            throw new SendFailException("Couldn`t fetch interfaces", e);
        }

      /*
        try {
            sendPackage(port, sendStrategy, msgAsBytes, null, InetAddress.getByName("255.255.255.255"));
        } catch (UnknownHostException e) {}
        */
        while (networkInterfaceEnumeration.hasMoreElements()){

            NetworkInterface networkInterface = networkInterfaceEnumeration.nextElement();
            List<InterfaceAddress> addresses = null;

            try {
                if (networkInterface.isUp()){
                    addresses = networkInterface.getInterfaceAddresses();
                }else {continue;}
            } catch (SocketException e) {
                sendStrategy.onInterfaceExplore(networkInterface, new SendFailException("Is it UP?", e));
                continue;
            }

            for (InterfaceAddress address : addresses) {
                InetAddress broadcast = address.getBroadcast();
                if (broadcast == null){
                    continue;
                }
                sendPackage(port, sendStrategy, msgAsBytes, networkInterface, broadcast);
            }
        }
    }

    private void sendPackage(int port, SendStrategy sendStrategy, byte[] msgAsBytes, NetworkInterface networkInterface, InetAddress broadcast) throws SendFailException {
        DatagramPacket packet = new DatagramPacket(msgAsBytes, msgAsBytes.length, broadcast, port);
        try {
            socket.send(packet);
        } catch (Exception e) {
            sendStrategy.onSendError(networkInterface, broadcast, new SendFailException("Fail to send",e));
        }
    }

    ;

    public static interface SendStrategy{
        void onInterfaceExplore(NetworkInterface networkInterface, SendFailException e) throws SendFailException;
        void onSendError(NetworkInterface networkInterface, InetAddress broadcast, SendFailException e) throws SendFailException;
    }

    public static class NoOpSendStrategy implements SendStrategy{

        @Override
        public void onInterfaceExplore(NetworkInterface networkInterface, SendFailException e) throws SendFailException {
            e.printStackTrace();
        }

        @Override
        public void onSendError(NetworkInterface networkInterface, InetAddress broadcast, SendFailException e) throws SendFailException {
            e.printStackTrace();
        }
    }

}
