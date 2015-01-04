package org.monroe.team.socks.example;


import java.util.Collection;

public class TestSupport {

    final public void waitUnless(Collection<?> awaitList,int waitForCount) throws InterruptedException {
        waitUnless(awaitList,waitForCount,10,500);
    }

    final public void waitUnless(Collection<?> awaitList,int waitForCount, int tryCount, int tryTimeout) throws InterruptedException {
        int tries = 0;
        while (awaitList.size() < waitForCount && tries<tryCount){
            Thread.sleep(tryTimeout);
            tries++;
        }
        if (awaitList.size() != waitForCount){
            throw new AssertionError("Expected answer count "+waitForCount+" , but get "+awaitList);
        }
    }
}
