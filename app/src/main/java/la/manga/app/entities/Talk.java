package la.manga.app.entities;

import java.net.URL;
import java.util.Calendar;

public class Talk {
    private String title;
    private Calendar date;
    private URL url;

    public Talk(String title, Calendar date, URL url) {
        this.title = title;
        this.date = date;
    }

    public String getTitle() {
        return title;
    }

    public Calendar getDate() {
        return date;
    }

    public URL getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return title;
    }
}
