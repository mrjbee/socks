package org.monroe.team.socks.example;

import junit.framework.Assert;
import org.junit.*;
import org.monroe.team.socks.broadcast.BroadcastAnnouncer;
import org.monroe.team.socks.broadcast.BroadcastReceiver;
import org.monroe.team.socks.broadcast.MapBroadcastMessageTransport;
import org.monroe.team.socks.exception.ConnectionException;
import org.monroe.team.socks.exception.InvalidProtocolException;
import org.monroe.team.socks.exception.SendFailException;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BroadcastTest extends TestSupport {

    private static BroadcastReceiver<Map<String,String>> receiver;

    private BroadcastAnnouncer<Map<String,String>> announcer;
    private static List<Map<String,String>> messagesList = new ArrayList<Map<String, String>>();

    @BeforeClass
    public static void init() throws ConnectionException {
        receiver = new BroadcastReceiver<Map<String, String>>(
                new MapBroadcastMessageTransport(),
                createObserver()
        );
        receiver.start(0);
    }

    @Before
    public void prepare() throws ConnectionException {
        Assert.assertEquals(receiver.isAlive(), true);
        messagesList.clear();
        announcer = new BroadcastAnnouncer<Map<String, String>>(new MapBroadcastMessageTransport());
    }

    @After
    public void cleanup(){
        messagesList.clear();
        announcer.destroy();
    }

    @AfterClass
    public static void destroy(){
        receiver.shutdown();
    }

    @Test
    public void shouldReceive() throws InvalidProtocolException, SendFailException, InterruptedException {
        //nothing here yet
        Map<String,String> msg= new HashMap<String, String>();
        msg.put("test", "value");
        announcer.sendMessage(receiver.getPort(), msg);
        waitUnless(messagesList, 1);
        Assert.assertEquals(1, messagesList.size());
        Assert.assertEquals("value",messagesList.get(0).get("test"));
    }

    private static BroadcastReceiver.BroadcastMessageObserver<Map<String, String>> createObserver() {
        return new BroadcastReceiver.BroadcastMessageObserver<Map<String, String>>() {
            @Override
            public void onMessage(Map<String, String> msg, InetAddress address) {
                messagesList.add(msg);
            }
        };
    }
}
