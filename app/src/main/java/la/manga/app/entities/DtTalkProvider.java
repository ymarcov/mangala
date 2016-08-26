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
    private static final String SERVER_IP = "46.101.209.138";

    private final JSONParser parser = new JSONParser();
    private final String baseUrl;
    private int currentPage = 0;
    private boolean endOfData = false;
    private List<Talk> cachedTalks = new ArrayList<>();
    private JSONArray cachedEntries = new JSONArray();

    public DtTalkProvider(TalkType type) {
        if (type == TalkType.EVENING)
            baseUrl = "http://" + SERVER_IP + "/en/evening.json";
        else if (type == TalkType.MORNING)
            baseUrl = "http://" + SERVER_IP + "/en/morning.json";
        else
            throw new IllegalArgumentException("Unsupported talk type.");
    }

    @Override
    public synchronized List<Talk> fetch(int n) {
        if (endOfData)
            return new ArrayList<>();

        List<Talk> result = new ArrayList<>();
        JSONArray salvaged = new JSONArray();

        try {
            while (n > 0) {
                JSONObject entry = fetchNextEntry();
                salvaged.add(entry);

                if (entry == null) {
                    endOfData = true;
                    break;
                }

                Talk talk = parseEntry(entry);
                result.add(talk);

                n--;
            }
        } catch (Exception e) {
            cachedEntries.addAll(0, salvaged);
            throw e;
        }

        return result;
    }

    private JSONObject fetchNextEntry() {
        if (cachedEntries.size() == 0)
            cachedEntries = fetchNextPage();

        if (cachedEntries.size() == 0)
            return null;

        return (JSONObject) cachedEntries.remove(0);
    }

    private JSONArray fetchNextPage() {
        try {
            JSONArray result = (JSONArray) parser.parse(new InputStreamReader(openStream()));
            currentPage++;
            return result;
        } catch (ParseException e) {
            Log.e(TAG, "Parse error while fetching talks: " + e.getMessage(), e);
        } catch (IOException e) {
            Log.e(TAG, "IO error while fetching talks: " + e.getMessage(), e);
        }

        return new JSONArray();
    }

    private InputStream openStream() throws IOException {
        URL url = new URL(String.format(Locale.getDefault(), "%s?page=%d", baseUrl, currentPage));
        Log.i(TAG, "Fetching entries from URL: " + url);
        return url.openStream();
    }

    private Talk parseEntry(JSONObject entry) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(Date.valueOf(entry.get("date").toString()));
        return new Talk(entry.get("title").toString(), cal);
    }
}
