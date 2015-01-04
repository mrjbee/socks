package org.monroe.team.socks.broadcast;

import java.util.Map;

public class DefaultBroadcastReceiver extends BroadcastReceiver<Map<String,String>>{
    public DefaultBroadcastReceiver(BroadcastMessageObserver<? super Map<String, String>> observer) {
        super(new MapBroadcastMessageTransport(), observer);
    }
}
