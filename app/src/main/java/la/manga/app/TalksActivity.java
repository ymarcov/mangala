package la.manga.app;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;

import com.google.common.base.Function;

import java.util.List;

import la.manga.app.entities.DtTalkProvider;
import la.manga.app.entities.Talk;
import la.manga.app.entities.TalkProvider;
import la.manga.app.ui.TalkAdapter;

public class TalksActivity extends AppCompatActivity {
    private static final String TAG = TalksActivity.class.getName();

    private TalkProvider talkProvider = new DtTalkProvider(DtTalkProvider.TalkType.EVENING);
    private RecyclerView talksView;
    private TalkAdapter talkAdapter;
    private int fetchCount = 10;
    private volatile boolean fetching = false;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_talks);
        Toolbar toolbar = (Toolbar) findViewById(R.id.talksToolbar);
        setSupportActionBar(toolbar);

        handler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();

        talksView = (RecyclerView) findViewById(R.id.talksListView);
        talkAdapter = new TalkAdapter(R.layout.talk_list_item);
        final LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        talksView.setLayoutManager(llm);
        talksView.setAdapter(talkAdapter);

        talksView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                fetchCount = (int)Math.pow(10, Math.max(1, Math.floor(Math.log10(dy))));

                int lastVisibleItem = llm.findLastVisibleItemPosition();

                if (lastVisibleItem + fetchCount >= talkAdapter.getItemCount())
                    fetchMoreTalks(fetchCount, addTalksTo(talkAdapter));
            }
        });

        fetchMoreTalks(fetchCount, addTalksTo(talkAdapter));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_talks, menu);
        return true;
    }

    private Function<List<Talk>, Void> addTalksTo(final TalkAdapter adapter) {
        return new Function<List<Talk>, Void>() {
            @Override
            public Void apply(List<Talk> talks) {
                adapter.addAll(talks);
                return null;
            }
        };
    }

    private void fetchMoreTalks(final int n, final Function<List<Talk>, Void> onComplete) {
        if (fetching)
            return;

        Log.i(TAG, "FETCHING MORE TALKS");

        fetching = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Talk> fetched = talkProvider.fetch(n);

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            onComplete.apply(fetched);
                            fetching = false;
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to fetch more talks", e);
                }
            }
        }).start();
    }
}
