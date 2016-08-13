package la.manga.app.net;

import android.support.annotation.NonNull;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
 * Manages a downloads cache, supporting continuable downloads.
 */
public class DownloadManager {
    private final Cache cache;
    private final Executor executor;
    private volatile int chunkSize = 0x10000;

    /**
     * Creates a new download manager.
     *
     * @param cache    The cache to be used as storage space.
     * @param executor The executor for running download tasks.
     *                 This executor will not be owned by the manager,
     *                 and therefore shutdown() has to be called
     *                 externally. It has to remain alive for the
     *                 lifetime of this manager.
     */
    public DownloadManager(Cache cache, Executor executor) {
        this.cache = cache;
        this.executor = executor;
    }

    /**
     * Starts a new download.
     *
     * @param url The URL to download.
     */
    public Task startDownload(URL url, ProgressListener progressListener) {
        Task t = new Task(url, progressListener);
        executor.execute(t);
        return t;
    }

    /**
     * An asynchronous, cancellable task for a single download.
     * <p/>
     * The following error cases may happen:
     * <p/>
     * 1. The file cannot be downloaded, because of a failure
     * in creating a cache entry.
     */
    public class Task implements RunnableFuture<InputStream> {
        private final URL url;
        private final ProgressListener progressListener;
        private volatile boolean cancelled = false;
        private volatile Throwable exception = null;
        private volatile boolean completed = false;
        private final ManualResetEvent finishEvent = new ManualResetEvent();
        private volatile InputStream result;

        public Task(URL url, ProgressListener progressListener) {
            this.url = url;
            this.progressListener = progressListener;
        }

        @Override
        public void run() {
            final Downloader downloader = new Downloader();
            final String cacheEntryName = getCacheEntryName();
            InputStream is = null;
            OutputStream os = null;
            int downloadedBytes = 0;

            try {
                os = DownloadManager.this.cache.createEntry(cacheEntryName);
                is = downloader.download(url);
                byte[] buffer = new byte[DownloadManager.this.chunkSize];
                int nbytes;

                while ((nbytes = is.read(buffer)) != -1) {
                    if (cancelled) {
                        notifyProgress(downloadedBytes, ProgressListener.State.CANCELLED);
                        return;
                    }

                    os.write(buffer, 0, nbytes);

                    downloadedBytes += nbytes;

                    notifyProgress(downloadedBytes, ProgressListener.State.IN_PROGRESS);
                }

                os.close();
                os = null;

                result = DownloadManager.this.cache.readEntry(cacheEntryName);
                completed = true;
                notifyProgress(downloadedBytes, ProgressListener.State.DONE);
            } catch (Exception e) {
                exception = e;
                notifyProgress(downloadedBytes, ProgressListener.State.ERROR);
            } finally {
                tryClose(is);
                tryClose(os);

                // wakeup all waiting threads
                finishEvent.signal();
            }
        }

        protected String getCacheEntryName() {
            return String.format("%s-%s", new File(url.getPath()).getName(), System.nanoTime());
        }

        private void notifyProgress(int downloadedBytes, ProgressListener.State state) {
            if (progressListener != null) {
                progressListener.onProgress(makeProgressInfo(downloadedBytes, state));
            }
        }

        private ProgressListener.ProgressInfo makeProgressInfo(int totalBytes, ProgressListener.State state) {
            ProgressListener.ProgressInfo pi = new ProgressListener.ProgressInfo();

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

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (!mayInterruptIfRunning)
                return false;

            cancelled = true;

            return !completed;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return completed || cancelled || (exception != null);
        }

        @Override
        public InputStream get() throws InterruptedException, ExecutionException {
            finishEvent.waitForSignal();
            validateState();
            return result;
        }

        @Override
        public InputStream get(long l, @NonNull TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            finishEvent.waitForSignal(timeUnit.toMillis(l));
            validateState();
            return result;
        }

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

    public interface ProgressListener {
        enum State {
            CANCELLED,
            DONE,
            ERROR,
            IN_PROGRESS
        }

        class ProgressInfo {
            Task task;
            URL url;
            State state;
            int downloadedBytes;
        }

        /**
         * Called every time the download has progressed.
         *
         * @param progressInfo Info about the current progress state.
         */
        void onProgress(ProgressInfo progressInfo);
    }
}
