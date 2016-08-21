package la.manga.app.net;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;

import org.junit.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RssTest {
    @Test
    public void testLibrary() throws Exception {
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new StringReader(RssData.evening));

        assertEquals("http://myurl.org/path", feed.getLink());

        List<SyndEntry> entries = feed.getEntries();

        assertEquals("First Entry", entries.get(0).getTitle());
        assertEquals("http://myurl.org/path/to/first", entries.get(0).getLink());

        assertEquals("Second Entry", entries.get(1).getTitle());
        assertEquals("http://myurl.org/path/to/second", entries.get(1).getLink());
    }

    private class RssData {
        public static final String evening = "\n" +
                "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<rss version=\"2.0\" xml:base=\"http://myurl.org/path\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\" xmlns:foaf=\"http://xmlns.com/foaf/0.1/\" xmlns:og=\"http://ogp.me/ns#\" xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\" xmlns:schema=\"http://schema.org/\" xmlns:sioc=\"http://rdfs.org/sioc/ns#\" xmlns:sioct=\"http://rdfs.org/sioc/types#\" xmlns:skos=\"http://www.w3.org/2004/02/skos/core#\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema#\">\n" +
                "  <channel>\n" +
                "    <title></title>\n" +
                "    <link>http://myurl.org/path</link>\n" +
                "    <description></description>\n" +
                "    <language>en</language>\n" +
                "    \n" +
                "    <item>\n" +
                "  <title>First Entry</title>\n" +
                "  <link>http://myurl.org/path/to/first</link>\n" +
                "  <description>" +
                "</description>\n" +
                "  <pubDate>Fri, 06 May 2016 11:37:25 +0000</pubDate>\n" +
                "    <dc:creator>admin</dc:creator>\n" +
                "    <guid isPermaLink=\"false\">5940 at http://myurl.org</guid>\n" +
                "    </item>\n" +
                "<item>\n" +
                "  <title>Second Entry</title>\n" +
                "  <link>http://myurl.org/path/to/second</link>\n" +
                "  <description>" +
                "</description>\n" +
                "  <pubDate>Fri, 06 May 2016 11:37:24 +0000</pubDate>\n" +
                "    <dc:creator>admin</dc:creator>\n" +
                "    <guid isPermaLink=\"false\">5939 at http://myurl.org</guid>\n" +
                "    </item>\n" +
                "  </channel>\n" +
                "</rss>\n";
    }
}
