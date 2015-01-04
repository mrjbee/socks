package org.monroe.team.socks.broadcast;

import org.monroe.team.socks.exception.InvalidProtocolException;

import java.util.HashMap;
import java.util.Map;

public class MapBroadcastMessageTransport implements BroadcastMessageTransport<Map<String,String>> {

    @Override
    public Map<String, String> fromString(String msg) throws InvalidProtocolException {
        if (msg.length() < 2) throw new InvalidProtocolException("Not enough for message.",new IllegalStateException());
        HashMap<String,String> map = new HashMap<String, String>();
        msg = msg.substring(1,msg.length()-1);
        String[] pairs = msg.split(";");
        for (String pair : pairs) {
            String[] keyValue = pair.split("==");
            try {
                map.put(keyValue[0],keyValue[1]);
            }catch (IndexOutOfBoundsException e){
                throw new InvalidProtocolException("Invalid keyValue pair:"+keyValue, e);
            }
        }
        return map;
    }

    @Override
    public String toString(Map<String, String> msg) {
        StringBuilder builder = new StringBuilder("[");
        for (String key : msg.keySet()) {
            builder.append(key)
                    .append("==")
                    .append(msg.get(key))
                    .append(";");
        }
        if (!msg.isEmpty()){
            builder.deleteCharAt(builder.length()-1);
        }
        builder.append("]");
        return builder.toString();
    }
}
