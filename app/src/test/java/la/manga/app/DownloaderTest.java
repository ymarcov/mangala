package la.manga.app;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import la.manga.app.net.Downloader;
import la.manga.app.net.WebService;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class DownloaderTest {
    private Downloader downloader;

    @Before
    public void setUp() {
        downloader = new Downloader();
    }

    @Test
    public void downloadsIndexHtml() throws Exception {
        URL url = new URL(WebService.INDEX_URL);
        InputStream is = downloader.downloadStream(url);

        try {
            String line = readLine(is);
            assertEquals("<!DOCTYPE html>", line);
        } finally {
            is.close();
        }
    }

    @Ignore("too long for routine use")
    @Test
    public void downloadBigFile() throws Exception {
        URL url = new URL("http://ipv4.download.thinkbroadband.com/5MB.zip");
        InputStream is = downloader.downloadStream(url);

        try {
            InputStreamReader isr = new InputStreamReader(is);
            char[] buffer = new char[0x100000];
            int nbytes;

            while ((nbytes = isr.read(buffer)) != -1) {
                continue;
            }
        } finally {
            is.close();
        }
    }

    private String readLine(InputStream is) throws IOException {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        return br.readLine();
    }
}