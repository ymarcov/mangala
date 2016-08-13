package la.manga.app.net;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import la.manga.app.concurrency.ManualResetEvent;
import la.manga.app.storage.Cache;
import la.manga.app.storage.MemoryCache;

import static org.junit.Assert.*;

public class DownloadManagerTest {
    private DownloadManager dm;
    private Cache cache;
    private URL url;
    private TestHttpServer server = new TestHttpServer();


    @Before
    public void setUp() throws Exception {
        cache = new MemoryCache();
        Executor executor = new ThreadPoolExecutor(5, 5, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        dm = new DownloadManager(cache, executor);
        url = new URL(TestHttpServer.TEST_FILE);
        server.start();
    }

    @After
    public void tearDown() {
        server.stop();
    }

    @Test
    public void downloadsMultipleItems() throws Exception {
        final List<DownloadManager.Task> tasks = new ArrayList<>();
        final HashMap<DownloadManager.Task, Integer> downloadedBytes = new HashMap<>();

        for (int i = 0; i < 10; i++)
            tasks.add(dm.startDownload(url, new DownloadManager.ProgressListener() {
                @Override
                public void onProgress(ProgressInfo progressInfo) {
                    downloadedBytes.put(progressInfo.task, progressInfo.downloadedBytes);
                }
            }));


        for (DownloadManager.Task t : tasks)
            t.get();

        for (Integer db : downloadedBytes.values())
            assertEquals(TestHttpServer.TEST_FILE_SIZE, db.intValue());
    }

    @Test
    public void cancelsDownloadInTheMiddle() throws Exception {
        final ManualResetEvent madeSomeProgress = new ManualResetEvent();
        final ManualResetEvent requestedCancel = new ManualResetEvent();

        DownloadManager.Task task = dm.startDownload(url, new DownloadManager.ProgressListener() {
            @Override
            public void onProgress(ProgressInfo progressInfo) {
                madeSomeProgress.signal();

                try {
                    requestedCancel.waitForSignal();
                } catch (InterruptedException _) {
                    fail("Interrupted");
                }
            }
        });

        boolean cancelled = false;

        try {
            // wait for some progress to be made
            madeSomeProgress.waitForSignal();

            // cancel task
            task.cancel(true);

            // allow task to enter next progress step and see cancellation
            requestedCancel.signal();

            // now we expect this to throw a cancellation exception
            task.get();
        } catch (CancellationException _) {
            cancelled = true;
        }

        assertTrue(cancelled);
    }

    @Test
    public void errorCausedByServerShutdown() throws Exception {
        final ManualResetEvent madeSomeProgress = new ManualResetEvent();
        final ManualResetEvent requestedShutdown = new ManualResetEvent();

        server.setFailAlways(true);

        DownloadManager.Task task = dm.startDownload(url, new DownloadManager.ProgressListener() {
            @Override
            public void onProgress(ProgressInfo progressInfo) {
                madeSomeProgress.signal();

                try {
                    requestedShutdown.waitForSignal();
                } catch (InterruptedException _) {
                    fail("Interrupted");
                }
            }
        });

        boolean raisedError = false;

        try {
            // wait for some progress to be made
            madeSomeProgress.waitForSignal();

            // cancel task
            server.stop();

            // allow task to enter next progress step and see cancellation
            requestedShutdown.signal();

            // now we expect this to throw a cancellation exception
            task.get();
        } catch (ExecutionException _) {
            raisedError = true;
        }

        assertTrue(raisedError);
    }

    /**
     * TODO
     * 4. resume download
     * 5. restart download
     */
}
