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

    public InputStream downloadStream(URL url) throws IOException {
        InputStream is = null;

        try {
            HttpURLConnection conn = open(url);
            return conn.getInputStream();
        } finally {
            if (is != null)
                is.close();
        }
    }

    private HttpURLConnection open(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        setDefaultSettings(conn);
        connectOrThrow(conn);
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

    private void connectOrThrow(HttpURLConnection conn) throws IOException {
        conn.connect();

        int rc = conn.getResponseCode();

        if (rc != HttpURLConnection.HTTP_OK)
            throw new IOException("HTTP server failed to answer request.");
    }
}
