package la.manga.app.net;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import la.manga.app.concurrency.ManualResetEvent;
import la.manga.app.storage.Cache;
import la.manga.app.storage.MemoryCache;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DownloadManagerTest {
    private DownloadManager dm;
    private Cache taskCache;
    private Cache dataCache;
    private URL url;
    private TestHttpServer server = new TestHttpServer();
    private Executor executor;

    @Before
    public void setUp() throws Exception {
        taskCache = new MemoryCache();
        dataCache = new MemoryCache();
        executor = new ThreadPoolExecutor(5, 5, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(10));
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
        final HashMap<DownloadManager.TaskId, Integer> downloadedBytes = new HashMap<>();

        for (int i = 0; i < 10; i++)
            tasks.add(dm.startDownload(url, new DownloadManager.ProgressListener() {
                @Override
                public void onProgress(DownloadManager.ProgressInfo progressInfo) {
                    downloadedBytes.put(progressInfo.taskId, progressInfo.downloadedBytes);
                }
            }));

        for (DownloadManager.Task t : tasks) {
            t.get();
            assertFalse(dm.isActive(t.getId()));
        }

        for (Integer db : downloadedBytes.values())
            assertEquals(TestHttpServer.TEST_FILE_SIZE, db.intValue());
    }

    @Test
    public void cancelsDownloadInTheMiddle() throws Exception {
        final boolean[] cancelled = new boolean[]{false};

        ControlledProgressScenario scenario = cancelledScenario(cancelled);

        DownloadManager.Task task = scenario.run();

        assertFalse(dm.isActive(task.getId()));
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
        assertTrue(taskCache.hasEntry(t.getId().getCacheEntryId()));
    }

    @Test
    public void getsAllTaskIds() throws Exception {
        final boolean[] cancelledFlag = new boolean[]{false};

        DownloadManager.Task t1 = dm.startDownload(url, null);
        DownloadManager.Task t2 = cancelledScenario(cancelledFlag).run();

        t1.get();

        List<DownloadManager.TaskId> tasks = dm.getTaskIds();

        assertTrue(Iterables.any(tasks, Predicates.equalTo(t1.getId())));
        assertTrue(Iterables.any(tasks, Predicates.equalTo(t2.getId())));
    }

    @Test
    public void getsTaskStatesFromIds() throws Exception {
        final boolean[] cancelledFlag = new boolean[]{false};

        DownloadManager.Task t1 = dm.startDownload(url, null);
        DownloadManager.Task t2 = cancelledScenario(cancelledFlag).run();

        t1.get();

        assertEquals(DownloadManager.TaskState.DONE, dm.getTaskState(t1.getId()));
        assertEquals(DownloadManager.TaskState.CANCELLED, dm.getTaskState(t2.getId()));
    }

    @Test
    public void restartsDownload() throws Exception {
        final boolean[] cancelled = new boolean[]{false};
        DownloadManager.Task t = cancelledScenario(cancelled).run();

        final boolean[] restarted = new boolean[]{false};

        t = dm.restartDownload(t.getId(), new DownloadManager.ProgressListener() {
            @Override
            public void onProgress(DownloadManager.ProgressInfo progressInfo) {
                if (progressInfo.state == DownloadManager.TaskState.STARTING
                        && progressInfo.downloadedBytes == 0) {
                    restarted[0] = true;
                }
            }
        });

        t.get();

        assertTrue(restarted[0]);
        assertEquals(1, taskCache.getEntryNames().size());
        assertEquals(1, dataCache.getEntryNames().size());
    }

    @Test
    public void resumesDownload() throws Exception {
        final boolean[] cancelled = new boolean[]{false};
        DownloadManager.Task t = cancelledScenario(cancelled).run();

        final boolean[] resumed = new boolean[]{false};
        final int previouslyDownloadedBytes = t.getDownloadedBytes();

        t = dm.resumeDownload(t.getId(), new DownloadManager.ProgressListener() {
            @Override
            public void onProgress(DownloadManager.ProgressInfo progressInfo) {
                if (progressInfo.state == DownloadManager.TaskState.STARTING
                        && progressInfo.downloadedBytes == previouslyDownloadedBytes) {
                    resumed[0] = true;
                }
            }
        });

        t.get();

        assertTrue(resumed[0]);
        assertEquals(TestHttpServer.TEST_FILE_SIZE, t.getDownloadedBytes());
        assertEquals(1, taskCache.getEntryNames().size());
        assertEquals(1, dataCache.getEntryNames().size());
    }

    @Test
    public void resumesInactiveCachedTask() throws Exception {
        FabricatedCaches caches = new FabricatedCaches();
        DownloadManager.ProgressInfo pi = caches.fabricateTask(DownloadManager.TaskState.IN_PROGRESS);

        final boolean[] resumed = new boolean[]{false};
        final int previouslyDownloadedBytes = pi.downloadedBytes;

        DownloadManager dm = caches.createDownloadManager();

        // we expect the state at this point to be pending, i.e. inactive
        assertEquals(DownloadManager.TaskState.PENDING, dm.getTaskState(pi.taskId));

        // resume the task and see that it downloads everything correctly
        DownloadManager.Task task = dm.resumeDownload(pi.taskId, new DownloadManager.ProgressListener() {
            @Override
            public void onProgress(DownloadManager.ProgressInfo progressInfo) {
                if (progressInfo.state == DownloadManager.TaskState.STARTING
                        && progressInfo.downloadedBytes == previouslyDownloadedBytes) {
                    resumed[0] = true;
                } else {
                    assertThat(progressInfo.downloadedBytes, greaterThan(previouslyDownloadedBytes));
                }
            }
        });

        task.get();

        assertTrue(resumed[0]);
        assertEquals(TestHttpServer.TEST_FILE_SIZE, task.getDownloadedBytes());
        assertEquals(1, caches.taskCache.getEntryNames().size());
        assertEquals(1, caches.dataCache.getEntryNames().size());
    }

    private class FabricatedCaches {
        public final Cache taskCache = new MemoryCache();
        public final Cache dataCache = new MemoryCache();

        public void fabricateTask(DownloadManager.ProgressInfo pi) throws IOException {
            OutputStream os = taskCache.createEntry(pi.taskId.getCacheEntryId());
            try {
                DownloadManager.ProgressInfo.serialize(pi, os);
            } finally {
                os.close();
            }
            dataCache.createEntry(pi.taskId.getCacheEntryId()).close();
        }

        public DownloadManager.ProgressInfo fabricateTask(DownloadManager.TaskState state) throws IOException {
            DownloadManager.ProgressInfo pi = new DownloadManager.ProgressInfo();

            pi.taskId = new DownloadManager.TaskId(String.valueOf(System.nanoTime()));
            pi.url = url;
            pi.state = state;
            pi.downloadedBytes = new Random().nextInt(TestHttpServer.TEST_FILE_SIZE);

            fabricateTask(pi);

            return pi;
        }

        public DownloadManager createDownloadManager() {
            return new DownloadManager(taskCache, dataCache, executor);
        }
    }

    private ControlledProgressScenario cancelledScenario(final boolean[] cancelledFlag) {
        return new ControlledProgressScenario() {
            @Override
            protected void onProgress() {
                task.cancel(true);
            }

            @Override
            protected void onCancelled() {
                cancelledFlag[0] = true;
            }
        };
    }

    private abstract class ControlledProgressScenario {
        private final ManualResetEvent madeSomeProgress = new ManualResetEvent();
        private final ManualResetEvent continueEvent = new ManualResetEvent();
        protected DownloadManager.Task task;

        protected void onProgress() {
        }

        protected void onCancelled() {
        }

        protected void onError() {
        }

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

        private void startTask() throws IOException {
            task = dm.startDownload(url, new DownloadManager.ProgressListener() {
                @Override
                public void onProgress(DownloadManager.ProgressInfo progressInfo) {
                    if (progressInfo.state == DownloadManager.TaskState.STARTING)
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
