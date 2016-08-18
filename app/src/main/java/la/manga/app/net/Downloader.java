package la.manga.app.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

/**
 * Downloads stuff from the network.
 */
public class Downloader {
    private int connectTimeout = 10 * 1000;
    private int readTimeout = 10 * 1000;

    /**
     * Returns the timeout for connection attempts.
     *
     *  @return The timeout in milliseconds.
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Sets the timeout for connection attempts.
     *
     * @param timeout The timeout in milliseconds.
     */
    public void setConnectTimeout(int timeout) {
        connectTimeout = timeout;
    }

    /**
     * Returns the timeout for read attempts.
     *
     *  @return The timeout in milliseconds.
     */
    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Sets the timeout for read attempts.
     *
     * @param timeout The timeout in milliseconds.
     */
    public void setReadTimeout(int timeout) {
        readTimeout = timeout;
    }

    /**
     * Downloads the content of a URL starting at a specified offset.
     *
     * @param url The URL to download.
     * @param offset The beginning offset to start downloading from.
     * @return An input stream of the downloaded content.
     * @throws IOException
     */
    public InputStream downloadWithOffset(URL url, int offset) throws IOException {
        return downloadRange(url, offset, 0);
    }

    /**
     * Downloads a byte range of the content of a URL.
     *
     * @param url The URL to download.
     * @param offset The beginning offset to start downloading from.
     * @param count The number of bytes to download.
     * @return An input stream of the downloaded content.
     * @throws IOException
     */
    public InputStream downloadRange(URL url, int offset, int count) throws IOException {
        HttpURLConnection conn = open(url);

        if (offset != 0 || count != 0)
            setDownloadRange(conn, offset, count);

        establishConnection(conn);

        return new InputStream(conn);
    }

    /**
     * Downloads the content of a URL.
     *
     * @param url The URL to download.
     * @return An input stream of the downloaded content.
     * @throws IOException
     */
    public InputStream download(URL url) throws IOException {
        return downloadRange(url, 0, 0);
    }

    private HttpURLConnection open(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        setDefaultSettings(conn);
        return conn;
    }

    private void setDefaultSettings(HttpURLConnection conn) throws ProtocolException {
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64) "
                                                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                                + "Chrome/52.0.2743.116 Safari/537.36");
    }

    private void setDownloadRange(HttpURLConnection conn, int offset, int count) {
        StringBuilder sb = new StringBuilder("bytes=");
        sb.append(offset);
        sb.append("-");

        if (count != 0)
            sb.append(offset + count - 1);

        conn.setRequestProperty("Range", sb.toString());
    }

    private void establishConnection(HttpURLConnection conn) throws IOException {
        conn.connect();

        int rc = conn.getResponseCode();

        if (rc != HttpURLConnection.HTTP_OK && rc != HttpURLConnection.HTTP_PARTIAL)
            throw new IOException("HTTP server responded with error: " + rc);
    }

    public class InputStream extends java.io.InputStream {
        private final HttpURLConnection conn;
        private final java.io.InputStream is;
        private final int contentLength;

        InputStream(HttpURLConnection conn) throws IOException {
            this.conn = conn;
            this.contentLength = conn.getContentLength();
            this.is = conn.getInputStream();
        }

        public int getLength() {
            return contentLength;
        }

        @Override
        public int read() throws IOException {
            return is.read();
        }

        @Override
        public void close() throws IOException {
            is.close();
        }

        @Override
        public long skip(long n) throws IOException {
            return is.skip(n);
        }

        @Override
        public int available() throws IOException {
            return is.available();
        }

        @Override
        public synchronized void mark(int readlimit) {
            is.mark(readlimit);
        }

        @Override
        public synchronized void reset() throws IOException {
            super.reset();
        }

        @Override
        public boolean markSupported() {
            return is.markSupported();
        }
    }
}
