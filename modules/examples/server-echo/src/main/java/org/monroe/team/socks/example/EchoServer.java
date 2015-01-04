package org.monroe.team.socks.example;

import org.monroe.team.socks.SocksServer;
import org.monroe.team.socks.SocksTransport;
import org.monroe.team.socks.broadcast.BroadcastReceiver;
import org.monroe.team.socks.broadcast.DefaultBroadcastAnnouncer;
import org.monroe.team.socks.broadcast.DefaultBroadcastReceiver;
import org.monroe.team.socks.exception.ConnectionException;
import org.monroe.team.socks.exception.InvalidProtocolException;
import org.monroe.team.socks.exception.SendFailException;
import org.monroe.team.socks.protocol.StringExchangeProtocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class EchoServer {

    private static Thread announceThread;

    public static void main(String[] args) throws IOException, InterruptedException, ConnectionException {
        System.out.print("\033[H\033[2J");
        System.out.println("Socks Echo Server "+Version.get());
        if (args == null || args.length == 0){
            System.out.println("Specify port as first argument");
            return;
        }

        int port = 0;

        try {
            port = Integer.parseInt(args[0]);
        }catch (NumberFormatException e){
            System.out.println("Invalid port = "+args[0]);
            return;
        }

        final SocksServer server = createServer(0);

        final DefaultBroadcastAnnouncer announcer = new DefaultBroadcastAnnouncer();
        final DefaultBroadcastReceiver receiver = new DefaultBroadcastReceiver(announceServer(announcer, server.getListenPort()));

        System.out.println("Server announcer started at port:" + port);
        receiver.start(port);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.shutdown();
                if (receiver != null){
                    receiver.shutdown();
                }
                if (announcer != null){
                    announcer.destroy();
                }
            }
        });
        server.awaitShutdown();
    }

    private static BroadcastReceiver.BroadcastMessageObserver<Map<String, String>> announceServer(final DefaultBroadcastAnnouncer announcer, final int serverPort) {
        return new BroadcastReceiver.BroadcastMessageObserver<Map<String, String>>() {
            @Override
            public void onMessage(final Map<String, String> msg, final InetAddress address) {
                System.out.println("Server get announce message from:"+address);
                if (announceThread == null) {
                    System.out.println("Server start announcing port:"+serverPort);
                    announceThread = new Thread(){
                        @Override
                        public void run() {
                            int announceCount = 0;

                            Map<String,String> announceMsg = new HashMap<String, String>();
                            announceMsg.put("server_port",Integer.toString(serverPort));

                            while (announceCount < 5){
                                announceCount+=1;
                                if (isInterrupted() || !announcer.isAlive()) break;
                                try {
                                    announcer.sendMessage(Integer.parseInt(msg.get("client_announce_port")),announceMsg);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                if (isInterrupted() || !announcer.isAlive()) break;
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    break;
                                }
                            }
                            if (!announcer.isAlive()){
                                System.out.println("!!! [Server] Announcer dead");
                            }
                            announceThread = null;
                        }
                    };
                    announceThread.start();
                }
            }
        };
    }

    public static SocksServer createServer(int port) {
        SocksServer instance = new SocksServer();
        instance.setTransportServlet(new SocksServer.Servlet() {
            @Override
            public void onData(Object data, SocksTransport transport) {
                if (!transport.getProtocol().getClass().equals(StringExchangeProtocol.class)){
                    System.out.println("Server get <unexpected protocol>: "+data +" "+transport);
                    transport.destroy();
                } else {
                    System.out.println("Server get: " + data + " " + transport);
                    try {
                        transport.send(prepareEchoString((String) data));
                    } catch (SendFailException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onError(Exception e, SocksTransport transport) {
                System.out.println("Server error: "+e +" "+transport);
                e.printStackTrace();
            }
        });
        try {
            instance.start(port, InetAddress.getByName("0.0.0.0"));
            System.out.println("Server started at port:"+instance.getListenPort());
        } catch (IOException e) {
            throw new RuntimeException("Couldn`t start server", e);
        }
        return instance;
    }

    public static String prepareEchoString(String originString) {
        StringBuffer buffer = new StringBuffer(originString);
        return buffer.reverse().toString();
    }
}
