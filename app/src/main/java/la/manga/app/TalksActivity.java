package la.manga.app;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;

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
                int lastVisibleItem = llm.findLastCompletelyVisibleItemPosition();

                if (lastVisibleItem == talkAdapter.getItemCount() - 1)
                    fetchMoreTalks(new Runnable() {
                        @Override
                        public void run() {
                            talksView.fling(0, 1000);
                        }
                    });
            }
        });

        fetchMoreTalks(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_talks, menu);
        return true;
    }

    public void fetchMoreTalks(final Runnable onComplete) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Talk> fetched = talkProvider.fetch();

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            talkAdapter.addAll(fetched);
                        }
                    });

                    if (onComplete != null)
                        handler.post(onComplete);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to fetch more talks", e);
                }
            }
        }).start();
    }
}
