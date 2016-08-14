package la.manga.app.net;

import android.support.annotation.NonNull;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import la.manga.app.concurrency.ManualResetEvent;
import la.manga.app.storage.Cache;

/**
 * Manages a downloads dataCache, supporting continuable downloads.
 */
public class DownloadManager {
    private final Cache taskCache;
    private final Cache dataCache;
    private final Executor executor;
    private volatile int chunkSize = 0x10000;

    /**
     * Creates a new download manager.
     *
     * @param taskCache  The cache in which task state will be saved.
     * @param dataCache  The cache in which downloaded data will be saved.
     * @param executor   The executor for running download tasks.
     *                   This executor will not be owned by the manager,
     *                   and therefore shutdown() has to be called
     *                   externally. It has to remain alive for the
     *                   lifetime of this manager.
     */
    public DownloadManager(Cache taskCache, Cache dataCache, Executor executor) {
        this.taskCache = taskCache;
        this.dataCache = dataCache;
        this.executor = executor;
    }

    /**
     * Starts a new download.
     *
     * @param url The URL to download.
     * @param progressListener A progress listener updated on task state changes.
     * @return An async task handle for the specified download.
     */
    public Task startDownload(URL url, ProgressListener progressListener) {
        return startTask(new Task(url, progressListener));
    }

    /**
     * Restarts an existing download task.
     * @param downloadTask The task to restart.
     * @param progressListener A progress listener updated on task state changes.
     * @return An async task handle for the specified download.
     */
    public Task restartDownload(Task downloadTask, ProgressListener progressListener) {
        return startTask(new RestartedTask(downloadTask, progressListener));
    }

    private Task startTask(Task t) {
        t.prepare();
        executor.execute(t);
        return t;
    }

    /**
     * An asynchronous, cancellable task for a single download.
     */
    public class Task implements RunnableFuture<InputStream> {
        /**
         * The downloaded URL.
         */
        private final URL url;

        /**
         * A progress listener to update on each state change.
         */
        private final ProgressListener progressListener;

        /**
         * Finished state synchronization and flags.
         */
        private volatile boolean cancelled = false;
        private volatile Throwable exception = null;
        private volatile boolean completed = false;
        private final ManualResetEvent finishEvent = new ManualResetEvent();

        /**
         * The stream of downloaded bytes to be returned as a result.
         */
        private volatile InputStream result;

        /**
         * The identifier name of this task in the caches.
         */
        private String cacheEntryName;

        /**
         * Holds how many bytes were downloaded so far.
         */
        private int downloadedBytes = 0;

        /**
         * Creates a brand new task with a new task-id in the caches.
         */
        public Task(URL url, ProgressListener progressListener) {
            this.url = url;
            this.progressListener = progressListener;
        }

        /**
         * Gets the URL downloaded by this task.
         */
        public URL getUrl() {
            return url;
        }

        /**
         * Gets the identifier name of this task in the caches.
         */
        public String getCacheEntryName() {
            return cacheEntryName;
        }

        /**
         * Starts running the task.
         * This is called by the DownloadManager executor when it's ready.
         */
        @Override
        public void run() {
            final Downloader downloader = new Downloader();
            InputStream is = null;
            OutputStream os = null;

            try {
                os = openDataCacheEntry();
                is = downloader.download(url);
                byte[] buffer = new byte[chunkSize];
                int nbytes;

                while ((nbytes = is.read(buffer)) != -1) {
                    if (cancelled) {
                        onStateChanged(TaskState.CANCELLED);
                        return;
                    }

                    os.write(buffer, 0, nbytes);

                    downloadedBytes += nbytes;

                    onStateChanged(TaskState.IN_PROGRESS);
                }

                os.close();
                os = null;

                result = DownloadManager.this.dataCache.readEntry(cacheEntryName);
                completed = true;
                onStateChanged(TaskState.DONE);
            } catch (Exception e) {
                exception = e;
                onStateChanged(TaskState.ERROR);
            } finally {
                tryClose(is);
                tryClose(os);

                // wakeup all waiting threads
                finishEvent.signal();
            }
        }

        /**
         * Prepares the task for starting.
         * This is in a separate function, and not in the constructor,
         * to allow subclasses to override and customize this part.
         */
        void prepare() {
            this.cacheEntryName = generateCacheEntryName();
            onStateChanged(TaskState.PENDING);
        }

        /**
         * Called when the task needs an output stream to write to.
         * Can be used by subclasses to provide existing entries,
         * for resuming suspended downloads or restarting on error.
         */
        protected OutputStream openDataCacheEntry() {
            return dataCache.createEntry(cacheEntryName);
        }

        /**
         * Generates a cache entry identifier name to use.
         * Can be used by subclasses to provide the already
         * existing name of an existing task, in the case
         * of resuming suspended downloads or restarting on error.
         */
        protected String generateCacheEntryName() {
            return String.format("%s-%s", System.nanoTime(), new File(url.getPath()).getName());
        }

        /**
         * Called each time a state change happens.
         * This updates the progress listener, and also
         * persists the new task state to the cache,
         * so that if anything happens, it can be reloaded
         * and possibly resumed from its most recent state.
         */
        private void onStateChanged(TaskState state) {
            ProgressInfo progressInfo = makeProgressInfo(downloadedBytes, state);

            persistState(progressInfo);

            if (progressListener != null) {
                progressListener.onProgress(progressInfo);
            }
        }

        /**
         * Persists the state of the task to the cache,
         * so that it might be used for resuming or
         * restarting suspended or failed downloads.
         */
        private void persistState(ProgressInfo progressInfo) {
            SerializableProgressInfo spi = new SerializableProgressInfo();

            spi.url = progressInfo.url;
            spi.state = progressInfo.state;
            spi.downloadedBytes = progressInfo.downloadedBytes;

            OutputStream os = null;

            try {
                taskCache.deleteEntry(cacheEntryName);
                os = taskCache.createEntry(cacheEntryName);

                ObjectOutputStream oos = new ObjectOutputStream(os);
                oos.writeObject(spi);
            } catch(Exception _) {
                // ignored
            } finally {
                tryClose(os);
            }
        }

        private ProgressInfo makeProgressInfo(int totalBytes, TaskState state) {
            ProgressInfo pi = new ProgressInfo();

            pi.task = this;
            pi.url = url;
            pi.downloadedBytes = totalBytes;
            pi.state = state;

            return pi;
        }

        private boolean tryClose(Closeable c) {
            try {
                if (c != null)
                    c.close();

                return true;
            } catch (IOException _) {
                return false;
            }
        }

        /**
         * Cancels the download task.
         * This will trigger cancellation at the earliest
         * possible opportunity. If the task is already
         * close to completion, this may not actually
         * have an effect, since it may complete before
         * it gets a chance to stop.
         * @param mayInterruptIfRunning If false, does nothing.
         * @return True if the task was cancelled before it was completed.
         */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (!mayInterruptIfRunning)
                return false;

            cancelled = !completed;

            return cancelled;
        }

        /**
         * Returns true if the task was cancelled before it was completed.
         */
        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        /**
         * Returns true if the task is no longer running, either
         * because it was completed, cancelled, or aborted.
         */
        @Override
        public boolean isDone() {
            return completed || cancelled || (exception != null);
        }

        /**
         * Gets the input stream containing the downloaded bytes.
         * @return An open, fully downloaded input stream.
         * @throws InterruptedException
         * @throws ExecutionException
         */
        @Override
        public InputStream get() throws InterruptedException, ExecutionException {
            finishEvent.waitForSignal();
            validateState();
            return result;
        }

        /**
         * Gets the input stream containing the downloaded bytes,
         * if the download was completed until the specified time.
         * Otherwise, throws an error.
         * @param l Length of time to wait, in specified time units.
         * @param timeUnit The time unit associated with {@code l}
         * @return An open, fully downloaded input stream.
         * @throws InterruptedException
         * @throws ExecutionException
         * @throws TimeoutException
         */
        @Override
        public InputStream get(long l, @NonNull TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            finishEvent.waitForSignal(timeUnit.toMillis(l));
            validateState();
            return result;
        }

        /**
         * Throws an exception if the task were cancelled or aborted.
         * @throws InterruptedException
         * @throws ExecutionException
         */
        private void validateState() throws InterruptedException, ExecutionException {
            if (cancelled)
                throw new CancellationException();

            if (exception != null) {
                if (exception instanceof InterruptedException)
                    throw (InterruptedException) exception;
                else
                    throw new ExecutionException(exception.getMessage(), exception);
            }
        }
    }

    /**
     * A task which uses the same cache files as
     * the one it continues, but restarts the entire
     * content download from scratch.
     */
    private class RestartedTask extends Task {
        private final String existingCacheEntryName;

        /**
         * Creates a new restarted task from an existing one.
         * Throws an exception if the specified task is currently running.
         * @throws IllegalArgumentException
         */
        public RestartedTask(Task task, ProgressListener progressListener) throws IllegalArgumentException {
            super(task.url, progressListener);

            if (!task.isDone())
                throw new IllegalArgumentException("Attempt to restart a running task.");

            existingCacheEntryName = task.getCacheEntryName();
        }

        @Override
        protected OutputStream openDataCacheEntry() {
            dataCache.deleteEntry(existingCacheEntryName);
            return dataCache.createEntry(existingCacheEntryName);
        }

        @Override
        public String generateCacheEntryName() {
            return existingCacheEntryName;
        }
    }

    public interface ProgressListener {
        /**
         * Called every time the download has progressed.
         *
         * @param progressInfo Info about the current progress state.
         */
        void onProgress(ProgressInfo progressInfo);
    }

    enum TaskState {
        PENDING,
        CANCELLED,
        DONE,
        ERROR,
        IN_PROGRESS
    }

    public class SerializableProgressInfo implements Serializable {
        public int version = 1;
        public URL url;
        public TaskState state;
        public int downloadedBytes;
    }

    public class ProgressInfo {
        public Task task;
        public URL url;
        public TaskState state;
        public int downloadedBytes;
    }
}
