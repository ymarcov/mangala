package la.manga.app.concurrency;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class ManualResetEventTest {
    private ManualResetEvent mre;

    private Thread startThread(Runnable r) {
        Thread t = new Thread(r);
        t.start();
        return t;
    }

    @Before
    public void setUp() {
        mre = new ManualResetEvent();
    }

    @Test
    public void signals() throws Exception {
        startThread(new Runnable() {
            @Override
            public void run() {
                mre.signal();
            }
        });

        mre.waitForSignal();
    }

    @Test
    public void waitsWithTimeout() throws Exception {
        long expectedElapsed_ms = 100;
        long expectedElapsed_ns = TimeUnit.MILLISECONDS.toNanos(expectedElapsed_ms);
        boolean timeoutOccurred = false;

        long start = System.nanoTime();

        try {
            mre.waitForSignal(expectedElapsed_ms);
        } catch (TimeoutException _) {
            timeoutOccurred = true;
        }

        long end = System.nanoTime();

        assertTrue(timeoutOccurred);
        assertThat(end - start, greaterThanOrEqualTo(expectedElapsed_ns));
    }

    @Test
    public void returnsBeforeTimeout() throws Exception {
        final long expectedElapsed_ms = 50;
        long expectedElapsed_ns = TimeUnit.MILLISECONDS.toNanos(expectedElapsed_ms);
        boolean timeoutOccurred = false;

        long start = System.nanoTime();

        try {
            startThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(expectedElapsed_ms);
                    } catch (Exception _) {
                        // ignored
                    }

                    mre.signal();
                }
            });

            mre.waitForSignal(expectedElapsed_ms * 2);
        } catch (TimeoutException _) {
            timeoutOccurred = true;
        }

        long end = System.nanoTime();

        assertFalse(timeoutOccurred);
        assertThat(end - start, greaterThanOrEqualTo(expectedElapsed_ns));
        assertThat(end - start, lessThan(expectedElapsed_ns * 2));
    }

    @Test
    public void doesntWaitWithTimeoutIfAlreadySignalled() throws Exception {
        mre.signal();

        long start = System.nanoTime();

        mre.waitForSignal(100);

        long end = System.nanoTime();

        assertThat(end - start, lessThan(TimeUnit.MILLISECONDS.toNanos(10L)));
    }
}
