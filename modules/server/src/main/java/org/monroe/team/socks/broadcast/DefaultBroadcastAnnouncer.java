package org.monroe.team.socks.broadcast;

import org.monroe.team.socks.exception.ConnectionException;

import java.util.Map;

public class DefaultBroadcastAnnouncer extends BroadcastAnnouncer<Map<String,String>>{
    public DefaultBroadcastAnnouncer() throws ConnectionException {
        super(new MapBroadcastMessageTransport());
    }
}
