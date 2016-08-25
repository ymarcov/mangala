package la.manga.app.entities;

import java.util.Calendar;

public class Talk {
    private String title;
    private Calendar date;

    public Talk(String title, Calendar date) {
        this.title = title;
        this.date = date;
    }

    public String getTitle() {
        return title;
    }

    public Calendar getDate() {
        return date;
    }

    @Override
    public String toString() {
        return title;
    }
}
