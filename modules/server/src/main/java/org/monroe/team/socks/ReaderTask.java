package org.monroe.team.socks;


import java.io.IOException;

public abstract class ReaderTask<Data> implements Runnable {

    private final DataObserver<Data> observer;

    private boolean isActive = true;

    private ReadTaskManageStrategy readTaskManageStrategy = new DefaultTaskManageStrategy();

    protected ReaderTask(DataObserver<Data> observer) {
        this.observer = observer;
    }

    @Override
    public void run() {
        while (isActive && !Thread.currentThread().isInterrupted()){
            long timeout = readTaskManageStrategy.beforeReadTimeout();
            if (timeout != 0){
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }
            }
            try {
                Data result = readForResult();
                readTaskManageStrategy.onResult();
                notifyOwner(result);
            } catch (ShutdownSignalException e){
                doShutdown(true);
            } catch (Exception e) {
                if(readTaskManageStrategy.onError(e)){
                    doShutdown(false);
                    return;
                }
                notifyOwner(e);
            }
        }
    }

    private void doShutdown(boolean normal) {
        notifyOwnerShutdown(normal);
        isActive = false;
    }

    private void notifyOwnerShutdown(boolean normal) {
        observer.onShutdownRequested(normal);
    }

    private synchronized void notifyOwner(Exception e){
        if (!isActive) return;
        observer.onError(e);
    }

    private synchronized void notifyOwner(Data result) {
        if (!isActive) return;
        observer.onDataChunk(result);
    }

    public synchronized void kill(){
        isActive = false;
    }

    protected abstract Data readForResult() throws IOException, ShutdownSignalException;

    public static interface DataObserver<DataType>{
        public void onDataChunk(DataType data);
        void onError(Exception e);
        void onShutdownRequested(boolean normal);
    }

    public static class ShutdownSignalException extends Exception{}

    public interface ReadTaskManageStrategy {
        long beforeReadTimeout();
        void onResult();
        boolean onError(Exception e);
    }

    public static class DefaultTaskManageStrategy implements ReadTaskManageStrategy {

        private static final int MAX_ERRORS = 5;
        private int errors = 0;

        @Override
        public long beforeReadTimeout() {
            return 200 * errors;
        }

        @Override
        public void onResult() {
            errors = 0;
        }

        @Override
        public boolean onError(Exception e) {
            errors += 1;
            return errors > MAX_ERRORS;
        }
    }

}
