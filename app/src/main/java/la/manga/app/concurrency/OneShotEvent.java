package la.manga.app.concurrency;

import java.util.concurrent.TimeoutException;

/**
 * A manual reset event.
 */
public class OneShotEvent {
    private volatile boolean set = false;
    private final Object syncObject = new Object();

    public void signal() {
        set = true;

        synchronized (syncObject) {
            syncObject.notifyAll();
        }
    }

    public void waitForSignal() throws InterruptedException {
        synchronized (syncObject) {
            while (!set)
                syncObject.wait();
        }
    }

    public void waitForSignal(long millis) throws InterruptedException, TimeoutException {
        synchronized (syncObject) {
            if (!set)
                syncObject.wait(millis);

            if (!set)
                throw new TimeoutException();
        }
    }
}
