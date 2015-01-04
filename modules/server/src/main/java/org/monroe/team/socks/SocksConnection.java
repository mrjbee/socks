package org.monroe.team.socks;

import org.monroe.team.socks.exception.SendFailException;

public interface SocksConnection<DataType> {
    public void send(DataType type) throws SendFailException;
}
