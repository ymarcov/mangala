package la.manga.app.storage;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides stream storage space.
 */
public interface Cache {
    OutputStream createEntry(String name);
    InputStream readEntry(String name);
    OutputStream appendToEntry(String name);
    void deleteEntry(String name);
    boolean hasEntry(String name);
}
