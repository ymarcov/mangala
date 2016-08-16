package la.manga.app.storage;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class FileCacheTest {
    private FileCache cache;
    private File dir;
    private int nextEntryId = 0;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        dir = folder.newFolder();
        cache = new FileCache(dir);
    }

    @Test
    public void singleEntry() throws Exception {
        String entryName = nextEntryName();

        commitBytes(cache.createEntry(entryName), new byte[]{1, 2, 3, 4});

        assertTrue(cache.hasEntry(entryName));

        assertArrayEquals(new byte[]{1, 2, 3, 4}, readBytes(cache.readEntry(entryName)));

        assertTrue(cache.hasEntry(entryName));

        assertThat(entryName, in(cache.getEntryNames()));
    }

    @Test
    public void appendsToEntry() throws Exception {
        String entryName = nextEntryName();

        commitBytes(cache.createEntry(entryName), new byte[]{1, 2});

        commitBytes(cache.appendToEntry(entryName), new byte[]{3, 4});

        assertTrue(cache.hasEntry(entryName));

        assertArrayEquals(new byte[]{1, 2, 3, 4}, readBytes(cache.readEntry(entryName)));

        assertTrue(cache.hasEntry(entryName));
    }

    @Test
    public void deletesEntry() throws Exception {
        String entryName = nextEntryName();

        cache.createEntry(entryName).close();

        cache.deleteEntry(entryName);

        assertFalse(cache.hasEntry(entryName));

        assertThat(entryName, not(in(cache.getEntryNames())));
    }

    @Test
    public void multipleEntries() throws Exception {
        String e1 = nextEntryName();
        String e2 = nextEntryName();

        commitBytes(cache.createEntry(e1), new byte[]{1});
        commitBytes(cache.createEntry(e2), new byte[]{2});

        assertThat(e1, in(cache.getEntryNames()));
        assertThat(e2, in(cache.getEntryNames()));
        assertTrue(cache.hasEntry(e1));
        assertTrue(cache.hasEntry(e2));

        assertArrayEquals(new byte[]{1}, readBytes(cache.readEntry(e1)));
        assertArrayEquals(new byte[]{2}, readBytes(cache.readEntry(e2)));
    }

    @Test
    public void clearsCache() throws Exception {
        cache.createEntry(nextEntryName()).close();
        cache.createEntry(nextEntryName()).close();
        cache.createEntry(nextEntryName()).close();

        assertEquals(3, cache.getEntryNames().size());

        cache.clear();

        assertEquals(0, cache.getEntryNames().size());
    }

    private void commitBytes(OutputStream os, byte[] buffer) throws IOException {
        try {
            os.write(buffer);
        } finally {
            os.close();
        }
    }

    private byte[] readBytes(InputStream is) throws IOException {
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buffer = new byte[0x10];
            int nbytes;

            while ((nbytes = is.read(buffer)) != -1)
                os.write(buffer, 0, nbytes);

            return os.toByteArray();
        } finally {
            is.close();
        }
    }

    private String nextEntryName() {
        return "test" + nextEntryId++;
    }
}
