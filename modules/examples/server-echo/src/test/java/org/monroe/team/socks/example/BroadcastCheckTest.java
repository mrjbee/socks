package org.monroe.team.socks.example;


import junit.framework.Assert;
import org.junit.Test;
import org.monroe.team.socks.broadcast.BroadcastReceiver;
import org.monroe.team.socks.broadcast.DefaultBroadcastReceiver;
import org.monroe.team.socks.exception.ConnectionException;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BroadcastCheckTest extends TestSupport{

    @Test
    public void testBroadcast() throws InterruptedException, ConnectionException {

        final List<Map<String,String>> msgList = new ArrayList<Map<String, String>>();

        DefaultBroadcastReceiver receiver = new DefaultBroadcastReceiver(new BroadcastReceiver.BroadcastMessageObserver<Map<String, String>>() {
            @Override
            public void onMessage(Map<String, String> stringStringMap, InetAddress address) {
                msgList.add(stringStringMap);
            }
        });

        receiver.start(12399);

        waitUnless(msgList, 2, 15,1000);
        Assert.assertEquals(msgList.size(),2);
    }

}
