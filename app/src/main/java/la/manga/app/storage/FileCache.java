package la.manga.app.storage;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * A cache made of files in a dedicated directory.
 */
public class FileCache implements Cache {
    private final File dir;

    public FileCache(File dir) {
        this.dir = dir;

        if (!dir.exists())
            if (!dir.mkdir())
                throw new RuntimeException("Failed to create file cache directory.");

        if (!dir.isDirectory())
            throw new IllegalArgumentException("Specified path is not a directory.");
    }


    @Override
    public OutputStream createEntry(String name) {
        File file = new File(dir, name);

        if (file.exists())
            throw new IllegalArgumentException("File already exists in cache");

        try {
            return new FileOutputStream(file);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create file cache entry.", e);
        }
    }

    @Override
    public List<String> getEntryNames() {
        return Lists.transform(Arrays.asList(dir.listFiles()), new Function<File, String>() {
            @Override
            public String apply(File file) {
                return file.getName();
            }
        });
    }

    @Override
    public InputStream readEntry(String name) {
        File file = new File(dir, name);

        if (!file.exists())
            return null;

        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Failed to read file cache entry.", e);
        }
    }

    @Override
    public OutputStream appendToEntry(String name) {
        File file = new File(dir, name);

        if (!file.exists())
            return null;

        try {
            return new FileOutputStream(file, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create file cache entry.", e);
        }
    }

    @Override
    public void deleteEntry(String name) {
        File file = new File(dir, name);

        if (!file.exists())
            return;

        if (!file.delete())
            throw new RuntimeException("Failed to delete file cache entry.");
    }

    @Override
    public boolean hasEntry(String name) {
        File file = new File(dir, name);
        return file.exists();
    }
}
