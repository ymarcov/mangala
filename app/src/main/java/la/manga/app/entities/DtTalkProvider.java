package la.manga.app.entities;

import android.util.Log;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DtTalkProvider implements TalkProvider {
    public enum TalkType {
        EVENING,
        MORNING
    }

    private static final String TAG = DtTalkProvider.class.getName();

    private final JSONParser parser = new JSONParser();
    private final String baseUrl;
    private int currentPage = 0;

    public DtTalkProvider(TalkType type) {
        if (type == TalkType.EVENING)
            baseUrl = "http://beta.dhammatalks.org/en/evening.json";
        else if (type == TalkType.MORNING)
            baseUrl = "http://beta.dhammatalks.org/en/morning.json";
        else
            throw new IllegalArgumentException("Unsupported talk type.");
    }

    @Override
    public synchronized List<Talk> fetch() {
        List<Talk> result = new ArrayList<>();

        for (Object entry : fetchEntries())
            result.add(parseEntry((JSONObject) entry));

        currentPage++;

        return result;
    }

    private JSONArray fetchEntries() {
        try {
            return (JSONArray) parser.parse(new InputStreamReader(openStream()));
        } catch (ParseException e) {
            Log.e(TAG, "Parse error while fetching talks: " + e.getMessage(), e);
        } catch (IOException e) {
            Log.e(TAG, "IO error while fetching talks: " + e.getMessage(), e);
        }

        return new JSONArray();
    }

    private InputStream openStream() throws IOException {
        URL url = new URL(String.format(Locale.getDefault(), "%s?page=%d", baseUrl, currentPage));
        return url.openStream();
    }

    private Talk parseEntry(JSONObject entry) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(Date.valueOf(entry.get("date").toString()));
        return new Talk(entry.get("title").toString(), cal);
    }
}
