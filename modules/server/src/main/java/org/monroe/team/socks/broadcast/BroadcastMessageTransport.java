package org.monroe.team.socks.broadcast;

import org.monroe.team.socks.exception.InvalidProtocolException;

public interface BroadcastMessageTransport<MessageType> {
    public MessageType fromString(String msg) throws InvalidProtocolException;
    public String toString(MessageType msg) throws InvalidProtocolException;
}
