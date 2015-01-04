package org.monroe.team.socks.exception;

public class SendFailException extends Exception{

    public SendFailException(String s, Throwable e) {
        super(s,e);
    }
}
