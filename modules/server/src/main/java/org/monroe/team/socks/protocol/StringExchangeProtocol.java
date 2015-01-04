package org.monroe.team.socks.protocol;

import org.monroe.team.socks.ReaderTask;
import org.monroe.team.socks.SocksTransport;

import java.io.*;

public class StringExchangeProtocol implements SocksTransport.Protocol<String>{

    private static final String KILL_SIGNAL = "KILL_SIGNAL_asdasbnqwkejbwqe_FROM_CLIENT";
    private DataInputStream reader;
    private DataOutputStream writer;

    @Override
    public void create(DataInputStream inputStream, DataOutputStream outputStream) throws Exception {
        reader = inputStream;
        writer = outputStream;
    }

    @Override
    public void send(String message) throws Exception {
        writer.writeUTF((String) message);
        writer.flush();
    }

    @Override
    public void sendShutdownSignal() throws Exception {
        send(KILL_SIGNAL);
    }

    @Override
    public ReaderTask<String> createReaderTask(ReaderTask.DataObserver observer) {
        return new ReaderTask<String>(observer) {
            @Override
            protected String readForResult() throws IOException, ShutdownSignalException {
                String text =  reader.readUTF();
                if (KILL_SIGNAL.equals(text)) throw new ShutdownSignalException();
                return text;
            }
        };
    }


    @Override
    public void clearResources() {

    }

    @Override
    public String toString() {
        return "Protocol{" +
                "type" + String.class +
                '}';
    }
}
