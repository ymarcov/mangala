package la.manga.app.entities;

import java.util.List;

public interface TalkProvider {
    List<Talk> fetch(int n);
}
