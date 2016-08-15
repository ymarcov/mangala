package la.manga.app.concurrency;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class OneShotEventTest {
    private static final long timerTolerance_ns = TimeUnit.MILLISECONDS.toNanos(5);

    private OneShotEvent ose;

    private Thread startThread(Runnable r) {
        Thread t = new Thread(r);
        t.start();
        return t;
    }

    @Before
    public void setUp() {
        ose = new OneShotEvent();
    }

    @Test
    public void signals() throws Exception {
        startThread(new Runnable() {
            @Override
            public void run() {
                ose.signal();
            }
        });

        ose.waitForSignal();
    }

    @Test
    public void waitsWithTimeout() throws Exception {
        long expectedElapsed_ms = 100;
        long expectedElapsed_ns = TimeUnit.MILLISECONDS.toNanos(expectedElapsed_ms);
        boolean timeoutOccurred = false;

        long start = System.nanoTime();

        try {
            ose.waitForSignal(expectedElapsed_ms);
        } catch (TimeoutException _) {
            timeoutOccurred = true;
        }

        long end = System.nanoTime();

        assertTrue(timeoutOccurred);
        assertThat(end - start + timerTolerance_ns, greaterThanOrEqualTo(expectedElapsed_ns));
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

                    ose.signal();
                }
            });

            ose.waitForSignal(expectedElapsed_ms * 2);
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
        ose.signal();

        long start = System.nanoTime();

        ose.waitForSignal(100);

        long end = System.nanoTime();

        assertThat(end - start, lessThan(TimeUnit.MILLISECONDS.toNanos(10L)));
    }
}
