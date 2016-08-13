package la.manga.app.storage;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Provides stream storage space.
 */
public interface Cache {
    /**
     * Creates a new cache entry.
     *
     * @param name The name of the entry.
     * @return An output stream for the new entry.
     * @throws IllegalArgumentException Entry name already exists in the cache.
     */
    OutputStream createEntry(String name) throws IllegalArgumentException;

    /**
     * Gets the names of all entries in the cache.
     *
     * @return A list of names of existing cache entries.
     */
    List<String> getEntryNames();

    /**
     * Reads an existing cache entry.
     *
     * @param name The name of the entry.
     * @return An input stream for the entry, or null if the entry does not exist.
     */
    InputStream readEntry(String name);

    /**
     * Appends to an existing cache entry.
     *
     * @param name The name of the entry.
     * @return An output stream for the entry, or null if the entry does not exist.
     */
    OutputStream appendToEntry(String name);

    /**
     * Deletes an existing entry from the cache.
     * If the entry does not exist, does nothing.
     *
     * @param name The name of the entry.
     */
    void deleteEntry(String name);

    /**
     * Checks if an entry exists in the cache.
     *
     * @param name The name of the entry.
     * @return True if the entry exists in the cache, and false otherwise.
     */
    boolean hasEntry(String name);
}
