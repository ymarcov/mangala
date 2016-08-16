package la.manga.app.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Implements an in-memory non-persistent cache.
 */
public class MemoryCache implements Cache {
    private HashMap<String, ByteArrayOutputStream> entries = new HashMap<>();

    @Override
    public synchronized OutputStream createEntry(String name) {
        if (hasEntry(name))
            throw new IllegalArgumentException("An entry by the specified name already exists in the cache.");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        entries.put(name, os);
        return os;
    }

    @Override
    public synchronized List<String> getEntryNames() {
        return new ArrayList<>(entries.keySet());
    }

    @Override
    public synchronized InputStream readEntry(String name) {
        ByteArrayOutputStream os = entries.get(name);

        if (os == null)
            return null;

        byte[] array = os.toByteArray();

        return new ByteArrayInputStream(array);
    }

    @Override
    public synchronized OutputStream appendToEntry(String name) {
        return entries.get(name);
    }

    @Override
    public synchronized void deleteEntry(String name) {
        entries.remove(name);
    }

    @Override
    public synchronized boolean hasEntry(String name) {
        return entries.containsKey(name);
    }
}
