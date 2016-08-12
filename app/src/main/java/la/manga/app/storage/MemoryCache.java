package la.manga.app.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * Implements an in-memory non-persistent cache.
 */
public class MemoryCache implements Cache {
    private HashMap<String, ByteArrayOutputStream> entries = new HashMap<>();

    @Override
    public OutputStream createEntry(String name) {
        return entries.put(name, new ByteArrayOutputStream());
    }

    @Override
    public InputStream readEntry(String name) {
        ByteArrayOutputStream os = entries.get(name);

        if (os == null)
            return null;

        byte[] array = os.toByteArray();

        return new ByteArrayInputStream(array);
    }

    @Override
    public OutputStream appendToEntry(String name) {
        return entries.get(name);
    }

    @Override
    public void deleteEntry(String name) {
        entries.remove(name);
    }

    @Override
    public boolean hasEntry(String name) {
        return entries.containsKey(name);
    }
}
