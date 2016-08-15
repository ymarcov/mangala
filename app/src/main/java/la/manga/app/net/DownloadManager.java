package la.manga.app.net;

import android.support.annotation.NonNull;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<TaskId, Task> activeTasks = new HashMap<>();
    private volatile Downloader downloader = new Downloader();
    private volatile int chunkSize = 0x10000;

    /**
     * Creates a new download manager.
     *
     * @param taskCache The cache in which task state will be saved.
     * @param dataCache The cache in which downloaded data will be saved.
     * @param executor  The executor for running download tasks.
     *                  This executor will not be owned by the manager,
     *                  and therefore shutdown() has to be called
     *                  externally. It has to remain alive for the
     *                  lifetime of this manager.
     */
    public DownloadManager(Cache taskCache, Cache dataCache, Executor executor) {
        this.taskCache = taskCache;
        this.dataCache = dataCache;
        this.executor = executor;
    }

    /**
     * Gets the downloader to be used for downloading content.
     *
     * @return The current downloader.
     */
    public Downloader getDownloader() {
        return downloader;
    }

    /**
     * Sets the downloader to be used for downloading content.
     * This will not affect currently active downloads.
     *
     * @param downloader The new downloader to use.
     */
    public void setDownloader(Downloader downloader) {
        this.downloader = downloader;
    }

    /**
     * Gets the buffer size for discretely downloaded chunks of data.
     *
     * @return The size of the buffer in bytes.
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Sets the buffer size for discretely downloaded chunks of data.
     *
     * @param chunkSize The size of the buffer in bytes.
     */
    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    /**
     * Gets the ids of the tasks associated with this manager.
     *
     * @return A list of task ids.
     * @throws IOException
     */
    public synchronized List<TaskId> getTaskIds() throws IOException {
        List<TaskId> result = new ArrayList<>();

        for (String name : taskCache.getEntryNames())
            result.add(new TaskId(name));

        return result;
    }

    /**
     * Gets the state of the specified task.
     *
     * @param id The id of the task whose state to get.
     * @return The current state of the task.
     * @throws IOException
     */
    public synchronized TaskState getTaskState(TaskId id) throws IOException {
        if (isActive(id))
            return activeTasks.get(id).getState();

        InputStream is = taskCache.readEntry(id.getCacheEntryId());
        ProgressInfo pi = ProgressInfo.deserialize(is);

        if (pi.state == TaskState.IN_PROGRESS || pi.state == TaskState.STARTING)
            pi.state = TaskState.PENDING; // start as inactive

        return pi.state;
    }

    /**
     * Gets whether the task specified by the task id is currently active.
     *
     * @param id The id of the task to check on.
     * @return True if the task is active, false otherwise.
     */
    public synchronized boolean isActive(TaskId id) {
        return activeTasks.containsKey(id);
    }

    /**
     * A thread-safe method for setting the active state of a task.
     */
    private synchronized void setTaskActiveState(Task t, boolean active) {
        TaskId id = t.getId();

        if (active)
            activeTasks.put(id, t);
        else if (activeTasks.containsKey(id))
            activeTasks.remove(id);
    }

    /**
     * Starts a new download.
     *
     * @param url              The URL to download.
     * @param progressListener A progress listener updated on task state changes.
     * @return An async task handle for the specified download.
     */
    public Task startDownload(URL url, ProgressListener progressListener) throws IOException {
        return startTask(new Task(url, progressListener));
    }

    /**
     * Restarts an existing download task.
     *
     * @param taskId           The id of the task to restart.
     * @param progressListener A progress listener updated on task state changes.
     * @return An async task handle for the specified download.
     */
    public Task restartDownload(TaskId taskId, ProgressListener progressListener) throws IOException {
        InputStream is = taskCache.readEntry(taskId.getCacheEntryId());
        ProgressInfo pi = ProgressInfo.deserialize(is);
        return startTask(new RestartedTask(pi, progressListener));
    }

    public Task resumeDownload(TaskId taskId, ProgressListener progressListener) throws IOException {
        InputStream is = taskCache.readEntry(taskId.getCacheEntryId());
        ProgressInfo pi = ProgressInfo.deserialize(is);
        return startTask(new ResumedTask(pi, progressListener));
    }

    private synchronized Task startTask(Task t) throws IOException {
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
        private InputStream result;

        /**
         * The identifier name of this task in the caches.
         */
        private String cacheEntryId;

        /**
         * The TaskId associated with this task.
         */
        private TaskId id;

        /**
         * Holds how many bytes were downloaded so far.
         */
        private volatile int downloadedBytes = 0;

        /**
         * Holds the current state of the task.
         */
        private volatile TaskState state;

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
         * Gets how many bytes were downloaded so far.
         */
        public int getDownloadedBytes() {
            return downloadedBytes;
        }

        /**
         * Gets the current state of the task.
         */
        public TaskState getState() {
            return state;
        }

        /**
         * Gets the identifier name of this task in the caches.
         */
        public TaskId getId() {
            return id;
        }

        /**
         * Starts running the task.
         * This is called by the DownloadManager executor when it's ready.
         */
        @Override
        public void run() {
            InputStream is = null;
            OutputStream os = null;

            setTaskActiveState(this, true);

            try {
                os = openDataCacheEntry();
                is = downloadUrl();
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

                result = dataCache.readEntry(cacheEntryId);
                completed = true;
                onStateChanged(TaskState.DONE);
            } catch (Exception e) {
                exception = e;

                try {
                    onStateChanged(TaskState.ERROR);
                } catch (IOException _) {
                    // ignore failure to persist state change
                }
            } finally {
                tryClose(is);
                tryClose(os);

                setTaskActiveState(this, false);

                // wakeup all waiting threads
                finishEvent.signal();
            }
        }

        /**
         * Prepares the task for starting.
         * This is in a separate function, and not in the constructor,
         * to allow subclasses to override and customize this part.
         */
        void prepare() throws IOException {
            this.cacheEntryId = generateCacheEntryId();
            this.id = new TaskId(this.cacheEntryId);
            onStateChanged(TaskState.STARTING);
        }

        /**
         * Starts the download process.
         * Can be used by subclasses to start downloading
         * from a specific offset in the file, etc.
         *
         * @return An input stream of downloaded bytes.
         * @throws IOException
         */
        protected InputStream downloadUrl() throws IOException {
            return getDownloader().download(url);
        }

        /**
         * Called when the task needs an output stream to write to.
         * Can be used by subclasses to provide existing entries,
         * for resuming suspended downloads or restarting on error.
         */
        protected OutputStream openDataCacheEntry() {
            return dataCache.createEntry(cacheEntryId);
        }

        /**
         * Generates a cache entry identifier name to use.
         * Can be used by subclasses to provide the already
         * existing name of an existing task, in the case
         * of resuming suspended downloads or restarting on error.
         */
        protected String generateCacheEntryId() {
            return String.format("%s-%s", System.nanoTime(), new File(url.getPath()).getName());
        }

        /**
         * Called each time a state change happens.
         * This updates the progress listener, and also
         * persists the new task state to the cache,
         * so that if anything happens, it can be reloaded
         * and possibly resumed from its most recent state.
         */
        private void onStateChanged(TaskState state) throws IOException {
            ProgressInfo progressInfo = makeProgressInfo(getDownloadedBytes(), state);

            persistState(progressInfo);

            if (progressListener != null)
                progressListener.onProgress(progressInfo);
        }

        /**
         * Persists the state of the task to the cache,
         * so that it might be used for resuming or
         * restarting suspended or failed downloads.
         */
        private void persistState(ProgressInfo progressInfo) throws IOException {
            OutputStream os = null;

            try {
                taskCache.deleteEntry(cacheEntryId);
                os = taskCache.createEntry(cacheEntryId);
                ProgressInfo.serialize(progressInfo, os);
            } finally {
                tryClose(os);
            }
        }

        private ProgressInfo makeProgressInfo(int totalBytes, TaskState state) {
            ProgressInfo pi = new ProgressInfo();

            pi.taskId = getId();
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
         *
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
         *
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
         *
         * @param l        Length of time to wait, in specified time units.
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
         *
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
        private final String existingCacheEntryId;

        /**
         * Creates a new restarted task from an existing one.
         * Throws an exception if the specified task is currently running.
         *
         * @throws IllegalArgumentException
         */
        public RestartedTask(ProgressInfo pi, ProgressListener progressListener) throws IllegalArgumentException {
            super(pi.url, progressListener);

            if (isActive(pi.taskId))
                throw new IllegalArgumentException("Attempt to restart a running task.");

            existingCacheEntryId = pi.taskId.getCacheEntryId();
        }

        @Override
        protected OutputStream openDataCacheEntry() {
            dataCache.deleteEntry(existingCacheEntryId);
            return dataCache.createEntry(existingCacheEntryId);
        }

        @Override
        public String generateCacheEntryId() {
            return existingCacheEntryId;
        }
    }

    /**
     * A task which uses the same cache files as
     * the one it continues, and resumes the download
     * from its last known stage.
     */
    private class ResumedTask extends Task {
        private final String existingCacheEntryId;
        private final int downloadedBytes;

        /**
         * Creates a new resumed task from an existing one.
         * Throws an exception if the specified task is currently running.
         *
         * @throws IllegalArgumentException
         */
        public ResumedTask(ProgressInfo pi, ProgressListener progressListener) throws IllegalArgumentException {
            super(pi.url, progressListener);

            if (isActive(pi.taskId))
                throw new IllegalArgumentException("Attempt to resume a running task.");

            existingCacheEntryId = pi.taskId.getCacheEntryId();
            downloadedBytes = pi.downloadedBytes;
        }

        @Override
        protected OutputStream openDataCacheEntry() {
            return dataCache.appendToEntry(existingCacheEntryId);
        }

        @Override
        public String generateCacheEntryId() {
            return existingCacheEntryId;
        }

        @Override
        protected InputStream downloadUrl() throws IOException {
            return getDownloader().downloadWithOffset(getUrl(), downloadedBytes);
        }

        @Override
        public int getDownloadedBytes() {
            return super.getDownloadedBytes() + downloadedBytes;
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
        STARTING,
        IN_PROGRESS,
        CANCELLED,
        DONE,
        ERROR
    }

    public static class TaskId implements Serializable {
        private String cacheEntryId;

        TaskId() {
        }

        TaskId(String cacheEntryId) {
            this.cacheEntryId = cacheEntryId;
        }

        public String getCacheEntryId() {
            return cacheEntryId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TaskId taskId = (TaskId) o;

            return cacheEntryId != null ? cacheEntryId.equals(taskId.cacheEntryId) : taskId.cacheEntryId == null;

        }

        @Override
        public int hashCode() {
            return cacheEntryId != null ? cacheEntryId.hashCode() : 0;
        }
    }

    public static class ProgressInfo implements Serializable {
        static ProgressInfo deserialize(InputStream is) throws IOException {
            ObjectInputStream ois = new ObjectInputStream(is);

            ProgressInfo pi;

            try {
                pi = (ProgressInfo) ois.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException("Serialized class was not found.", e);
            }

            return pi;
        }

        static void serialize(ProgressInfo pi, OutputStream os) throws IOException {
            ObjectOutputStream oos = new ObjectOutputStream(os);
            oos.writeObject(pi);
            oos.flush();
        }

        ProgressInfo() {
        } // package-private creation

        private int compatVersion = 1;
        public TaskId taskId;
        public URL url;
        public TaskState state;
        public int downloadedBytes;
    }
}
