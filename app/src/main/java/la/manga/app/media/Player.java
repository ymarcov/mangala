package la.manga.app.media;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * A media player.
 */
public class Player {
    private MediaPlayer player;
    private final Context context;

    public Player(Context context) {
        this.context = context;
    }

    public void setSource(final URL url, final SourceReadyListener listener) throws IOException {
        setSource(listener, new DataSourceSetter() {
            @Override
            public void onSetSource() throws IOException {
                player.setDataSource(context, parseUrl(url));
            }
        });
    }

    public void setSource(final String path, final SourceReadyListener listener) throws IOException {
        setSource(listener, new DataSourceSetter() {
            @Override
            public void onSetSource() throws IOException {
                player.setDataSource(path);
            }
        });
    }

    private void setSource(final SourceReadyListener listener, DataSourceSetter setter) throws IOException {
        player.reset();

        setter.onSetSource();

        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                listener.onSourceReady(Player.this);
            }
        });

        player.prepareAsync();
    }

    public void play() {
        player.start();
    }

    public void pause() {
        player.pause();
    }

    public void stop() {
        player.stop();
    }

    public void acquire() {
        player = new MediaPlayer();
    }

    public void release() {
        player.release();
        player = null;
    }

    private Uri parseUrl(URL url) {
        try {
            return Uri.parse(url.toURI().toString());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public interface SourceReadyListener {
        void onSourceReady(Player player);
    }

    private interface DataSourceSetter {
        void onSetSource() throws IOException;
    }
}
