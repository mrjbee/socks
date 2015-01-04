package org.monroe.team.socks;


import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class SocksServer {

    public static final int PORT_ANY = 0;
    private final Object controlMonitor = new Object();
    private ServerThread serverThread;
    private ErrorHandlingStrategy errorHandlingStrategy = new DefaultErrorHandlingStrategy();
    private Servlet transportServlet = new NoOpServlet();

    public ErrorHandlingStrategy getErrorHandlingStrategy() {
        return errorHandlingStrategy;
    }

    public void setErrorHandlingStrategy(ErrorHandlingStrategy errorHandlingStrategy) {
        this.errorHandlingStrategy = errorHandlingStrategy;
    }

    public Servlet getTransportServlet() {
        return transportServlet;
    }

    public void setTransportServlet(Servlet transportServlet) {
        this.transportServlet = transportServlet;
    }

    public void start(int port, InetAddress address) throws IOException {
        if (serverThread != null){
            shutdown();
        }
        synchronized (controlMonitor){
            ServerSocket serverSocket = new ServerSocket(port,0, address);
            serverThread = new ServerThread(serverSocket);
            serverThread.start();
        }
    }

    public int getListenPort(){
       synchronized (controlMonitor) {
           if (serverThread == null) throw new IllegalStateException("Seems not running");
           return serverThread.socket.getLocalPort();
       }
    }

    public void shutdown() {
        synchronized (controlMonitor){
            if (serverThread == null ) return;
            serverThread.kill();
            serverThread = null;
            controlMonitor.notifyAll();
        }
    }

    public void awaitShutdown() throws InterruptedException {
        synchronized (controlMonitor) {
            if (serverThread == null || !serverThread.isActive) return;
            controlMonitor.wait();
        }
    }

    private void onClient(Socket clientSocket){
        //TODO: more here
        final SocksTransport socksTransport = new SocksTransport(clientSocket);
        try {
            socksTransport.init();
        } catch (IOException e) {
            onClientError(socksTransport, e);
        }

        socksTransport.setObserver(new SocksTransport.ConnectionObserver() {
            @Override
            public void onData(Object data) {
                transportServlet.onData(data, socksTransport);
            }

            @Override
            public void onReadError(Exception e) {
                transportServlet.onError(e, socksTransport);
            }

            @Override
            public void onDisconnected(boolean requestByPartner) {
                System.out.println("Client disconnected [error = "+!requestByPartner+"]");
            }
        });

        try {
            socksTransport.accept();
        } catch (Exception e) {
            onClientError(socksTransport, e);
        }

    }

    private void onCriticalError(Exception e) {
        errorHandlingStrategy.processCriticalError(e);
    }

    private void onError(IOException e) {
        errorHandlingStrategy.onException(e);
    }

    private void onClientError(SocksTransport transport, Exception e) {
        errorHandlingStrategy.onClientInitializationError(transport, e);
    }

    private final class ServerThread extends Thread {

        private final ServerSocket socket;
        private boolean isActive = true;

        private ServerThread(ServerSocket serverSocket) {
            this.socket = serverSocket;
        }

        @Override
        public void run() {
            try {
                startListing();
            }catch (Exception e){
                SocksServer.this.onCriticalError(e);
            }
        }

        private void startListing() throws IOException {
            int errorChecker = 0;
            while (isActive && !isInterrupted()){
                Socket clientSocket = null;
                try {
                    clientSocket = socket.accept();
                    errorChecker = 0;
                } catch (IOException e) {
                    //DO nothing if closed by request
                    if (!isActive) return;
                    errorChecker ++;
                    if (errorChecker > 20) {
                        throw new IllegalStateException("Too much errors without success connections.");
                    }else {
                        SocksServer.this.onError(e);
                    }
                }

                if (clientSocket != null){
                    SocksServer.this.onClient(clientSocket);
                }

            }
            if (isInterrupted()){
                throw new IllegalStateException("Thread was interrupted");
            }
        }

        public void kill() {
            isActive = false;
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public interface ErrorHandlingStrategy {
        void processCriticalError(Exception e);
        void onException(IOException e);
        void onClientInitializationError(SocksTransport transport, Exception e);
    }

    public interface Servlet {
        public void onData(Object data, SocksTransport transport);
        public void onError(Exception e, SocksTransport transport);
    }

    //TODO Enhance with logging
    private class DefaultErrorHandlingStrategy implements ErrorHandlingStrategy {

        @Override
        public void processCriticalError(Exception e) {
            System.out.println("Server going to shutdown");
            e.printStackTrace();
            shutdown();
        }

        @Override
        public void onException(IOException e) {
            System.out.println("Server error");
            e.printStackTrace();
        }

        @Override
        public void onClientInitializationError(SocksTransport transport, Exception e) {
            System.out.println("Client connection rejected");
            e.printStackTrace();
            transport.destroy();
        }
    }

    private final class NoOpServlet implements Servlet {

        @Override
        public void onData(Object data, SocksTransport transport) {
            System.out.println("Obtain data:"+data +" "+transport);
        }

        @Override
        public void onError(Exception e, SocksTransport transport) {
            System.out.println("Error:"+e.getMessage() +" "+transport);
            e.printStackTrace();
        }
    }

}
