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
    private Cache taskCache;
    private Cache dataCache;
    private URL url;
    private TestHttpServer server = new TestHttpServer();

    @Before
    public void setUp() throws Exception {
        taskCache = new MemoryCache();
        dataCache = new MemoryCache();
        Executor executor = new ThreadPoolExecutor(5, 5, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
        dm = new DownloadManager(taskCache, dataCache, executor);
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
                public void onProgress(DownloadManager.ProgressInfo progressInfo) {
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
        final boolean[] cancelled = new boolean[]{false};

        ControlledProgressScenario scenario = new ControlledProgressScenario() {
            @Override
            protected void onProgress() {
                task.cancel(true);
            }

            @Override
            protected void onCancelled() {
                cancelled[0] = true;
            }
        };

        scenario.run();

        assertTrue(cancelled[0]);
    }

    @Test
    public void errorCausedByServerShutdown() throws Exception {
        final boolean[] aborted = new boolean[]{false};

        server.setFailAlways(true);

        ControlledProgressScenario scenario = new ControlledProgressScenario() {
            @Override
            protected void onError() {
                aborted[0] = true;
            }
        };

        scenario.run();

        assertTrue(aborted[0]);
    }

    @Test
    public void persistsTaskToCache() throws Exception {
        DownloadManager.Task t = dm.startDownload(url, null);
        t.get();
        assertTrue(taskCache.hasEntry(t.getCacheEntryName()));
    }

    @Test
    public void restartsDownload() throws Exception {
        final boolean[] cancelled = new boolean[]{false};

        ControlledProgressScenario scenario = new ControlledProgressScenario() {
            @Override
            protected void onProgress() {
                task.cancel(true);
            }

            @Override
            protected void onCancelled() {
                cancelled[0] = true;
            }
        };

        DownloadManager.Task t = scenario.run();

        assertTrue(cancelled[0]);

        final boolean[] restarted = new boolean[]{false};

        t = dm.restartDownload(t, new DownloadManager.ProgressListener() {
            @Override
            public void onProgress(DownloadManager.ProgressInfo progressInfo) {
                if (progressInfo.state == DownloadManager.TaskState.PENDING
                        && progressInfo.downloadedBytes == 0)
                    restarted[0] = true;
            }
        });

        t.get();

        assertTrue(restarted[0]);
        assertEquals(1, taskCache.getEntryNames().size());
        assertEquals(1, dataCache.getEntryNames().size());
    }

    /**
     * TODO
     * 4. resume download
     * 5. restart download
     */

    private abstract class ControlledProgressScenario {
        private final ManualResetEvent madeSomeProgress = new ManualResetEvent();
        private final ManualResetEvent continueEvent = new ManualResetEvent();
        protected DownloadManager.Task task;

        protected void onProgress() {}
        protected void onCancelled() {}
        protected void onError() {}

        public DownloadManager.Task run() throws Exception {
            try {
                startTask();
                madeSomeProgress.waitForSignal();
                onProgress();
                continueEvent.signal();
                task.get();
            } catch (ExecutionException _) {
                onError();
            } catch (CancellationException _) {
                onCancelled();
            }

            return task;
        }

        private void startTask() {
            task = dm.startDownload(url, new DownloadManager.ProgressListener() {
                @Override
                public void onProgress(DownloadManager.ProgressInfo progressInfo) {
                    if (progressInfo.state == DownloadManager.TaskState.PENDING)
                        return;

                    madeSomeProgress.signal();

                    try {
                        continueEvent.waitForSignal();
                    } catch (InterruptedException _) {
                        fail("Interrupted");
                    }
                }
            });
        }
    }
}
