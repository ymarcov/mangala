package la.manga.app;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Random;

import la.manga.app.net.Downloader;
import la.manga.app.net.WebService;

import static org.junit.Assert.*;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class DownloaderTest {
    private Downloader downloader;
    private final String TEST_DOWNLOAD_FILE = "http://vhost2.hansenet.de/1_mb_file.bin";

    @Before
    public void setUp() {
        downloader = new Downloader();
    }

    @Test
    public void downloadsIndexHtml() throws Exception {
        URL url = new URL(WebService.INDEX_URL);
        InputStream is = downloader.download(url);

        try {
            String line = readLine(is);
            assertEquals("<!DOCTYPE html>", line);
        } finally {
            is.close();
        }
    }

    @Test
    public void downloadBigFile() throws Exception {
        URL url = new URL(TEST_DOWNLOAD_FILE);
        final int fileSize = 0x100000;
        int totalBytes = 0;

        InputStream is = downloader.download(url);

        try {
            InputStreamReader isr = new InputStreamReader(is);
            char[] buffer = new char[0x100000];
            int nbytes;

            while ((nbytes = isr.read(buffer)) != -1)
                totalBytes += nbytes;

            assertEquals(fileSize, totalBytes);
        } finally {
            is.close();
        }
    }

    @Test
    public void downloadPartial() throws Exception {
        URL url = new URL(TEST_DOWNLOAD_FILE);
        final int offset = 200;
        final int count = 400;
        int totalBytes = 0;

        InputStream is = downloader.downloadRange(url, offset, count);

        try {
            InputStreamReader isr = new InputStreamReader(is);
            char[] buffer = new char[0x100000];
            int nbytes;

            while ((nbytes = isr.read(buffer)) != -1)
                totalBytes += nbytes;

            assertEquals(count, totalBytes);
        } finally {
            is.close();
        }
    }

    @Test
    public void downloadRangeIsConsistent() throws Exception {
        URL url = new URL(TEST_DOWNLOAD_FILE);
        final int offset = 200;
        final int count = 400;
        final int downloadSize = 0x100000;

        char[] oneShotBuffer = new char[downloadSize];
        char[] chunkBuffer = new char[downloadSize];
        int chunkBufferOffset = 0;
        int nread;

        // start out with random data, just to be sure
        fillWithRandomData(oneShotBuffer);
        fillWithRandomData(chunkBuffer);
4
        // read in one shot
        nread = readIntoOffset(downloader.downloadRange(url, 0, downloadSize), oneShotBuffer, 0);
        assertEquals(downloadSize, nread);

        // read in chunks
        while (chunkBufferOffset != chunkBuffer.length) {
            InputStream is = downloader.downloadRange(url, chunkBufferOffset, 0x40000);
            nread = readIntoOffset(is, chunkBuffer, chunkBufferOffset);
            chunkBufferOffset += nread;
        }

        // make sure both buffers have same content
        assertArrayEquals(oneShotBuffer, chunkBuffer);
    }

    private int readIntoOffset(InputStream is, char[] buffer, int offset) throws IOException {
        try {
            InputStreamReader isr = new InputStreamReader(is);
            int totalBytes = 0;
            int nread;

            while ((nread = isr.read(buffer, offset, buffer.length - offset)) > 0) {
                offset += nread;
                totalBytes += nread;
            }

            return totalBytes;
        } finally {
            is.close();
        }
    }

    private String readLine(InputStream is) throws IOException {
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader br = new BufferedReader(isr);
        return br.readLine();
    }

    private void fillWithRandomData(char[] buffer) {
        Random r = new Random();

        for (int i = 0; i < buffer.length; i++)
            buffer[i] = (char) r.nextInt();
    }
}