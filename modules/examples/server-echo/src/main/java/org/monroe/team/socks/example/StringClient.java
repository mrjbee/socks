package org.monroe.team.socks.example;

import org.monroe.team.socks.SocksClient;
import org.monroe.team.socks.SocksConnection;
import org.monroe.team.socks.SocksServer;
import org.monroe.team.socks.SocksTransport;
import org.monroe.team.socks.broadcast.BroadcastReceiver;
import org.monroe.team.socks.broadcast.DefaultBroadcastAnnouncer;
import org.monroe.team.socks.broadcast.DefaultBroadcastReceiver;
import org.monroe.team.socks.exception.ConnectionException;
import org.monroe.team.socks.exception.InvalidProtocolException;
import org.monroe.team.socks.exception.SendFailException;
import org.monroe.team.socks.protocol.StringExchangeProtocol;

import java.io.Console;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class StringClient {

    private static boolean active =true;
    private static SocksClient client;
    private static SocksConnection<String> socksConnection;
    private static Object announceAwaitingMonitor = new Object();

    private static InetAddress serverAddress;
    private static int serverPort;
    private static DefaultBroadcastAnnouncer announcer;
    private static DefaultBroadcastReceiver receiver;

    public static void main(String[] args) throws UnknownHostException, ConnectionException, InvalidProtocolException {

        if (args == null || args.length != 1){
            System.out.println("Please specify server announce port");
            return;
        }
        System.out.println("Socks Client [text] "+Version.get());
        Console console = System.console();
        if (console == null) {
            System.out.println("Unable to fetch console");
            return;
        }

        int port = 0;
        try{
           port = Integer.parseInt(args[0]);
        }catch (Exception e){
           System.out.println("Client: something bad with port = "+args[0]);
           throw new RuntimeException(e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                shutdown(false);
            }
        });

        System.out.println("Discovering server by announce port "+port);

        receiver = new DefaultBroadcastReceiver(new BroadcastReceiver.BroadcastMessageObserver<Map<String, String>>() {
            @Override
            public void onMessage(Map<String, String> stringStringMap, InetAddress address) {
                serverAddress = address;
                serverPort = Integer.parseInt(stringStringMap.get("server_port"));
                synchronized (announceAwaitingMonitor){
                    announceAwaitingMonitor.notify();
                }
                receiver.shutdown();
            }
        });
        receiver.start(0);
        int clientAnnouncePort = receiver.getPort();

        announcer = new DefaultBroadcastAnnouncer();
        Map<String,String> announceMsg = new HashMap<String, String>();
        announceMsg.put("client_announce_port",Integer.toString(clientAnnouncePort));
        int tryCount =0;
        synchronized (announceAwaitingMonitor) {
            while (tryCount < 4 && serverAddress == null) {
                tryCount++;
                try {
                    announcer.sendMessage(port, announceMsg);
                    System.out.println(" Send client announce. Try = "+tryCount);
                } catch (SendFailException e) {
                    System.out.println(" Send client announce. [fails] Try = "+tryCount);
                    e.printStackTrace();
                }
                try {
                    announceAwaitingMonitor.wait(5000);
                } catch (InterruptedException e) {}
            }
        }
        if (serverAddress == null){
            System.out.println("Sorry server not found.");
            shutdown(true);
        }

        client = new SocksClient(serverPort, serverAddress);
        socksConnection = client.getConnection(StringExchangeProtocol.class, new SocksTransport.ConnectionObserver<String>() {

            @Override
            public void onData(String data) {
                System.out.println(new Date().toString()+": [Server response] "+data);
            }

            @Override
            public void onReadError(Exception e) {
                System.out.println(new Date().toString()+": Client: [error] " + e.getMessage());
            }

            @Override
            public void onDisconnected(boolean requestByPartner) {
                shutdown(true);
            }
        });

        System.out.println("Type message and hit enter:");
        while (active){
            String line = console.readLine();
            if (active) {
                try {
                    socksConnection.send(line);
                } catch (SendFailException e) {
                    e.printStackTrace();
                    System.out.println("\n\nTry Again \n");
                }
            }
        }
    }

    private static void shutdown(boolean andExit) {
        if (client!=null && socksConnection!=null) {
            System.out.println("Client: shutdown");
            active = false;
            client.closeConnection(socksConnection);
            client = null;
        }
        if (announcer != null){
            announcer.destroy();
        }
        if (receiver != null){
            receiver.shutdown();
        }
        if (andExit) System.exit(0);
    }
}
