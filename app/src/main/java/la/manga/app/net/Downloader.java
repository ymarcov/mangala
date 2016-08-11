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
    private static final int connectTimeout = 10 * 1000;
    private static final int readTimeout = 10 * 1000;

    public Downloader() {
    }

    public InputStream downloadWithOffset(URL url, int offset) throws IOException {
        return downloadRange(url, offset, 0);
    }

    public InputStream downloadRange(URL url, int offset, int count) throws IOException {
        InputStream is = null;

        try {
            HttpURLConnection conn = open(url);

            if (offset != 0 || count != 0)
                setDownloadRange(conn, offset, count);

            establishConnection(conn);

            return conn.getInputStream();
        } finally {
            if (is != null)
                is.close();
        }
    }

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
}
