package la.manga.app;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

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
    private TextView tvStatus;
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
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        talkAdapter = new TalkAdapter(R.layout.talk_list_item);
        final LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        talksView.setLayoutManager(llm);
        talksView.setAdapter(talkAdapter);

        talksView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int lastVisibleItem = llm.findLastVisibleItemPosition();

                if (lastVisibleItem + fetchCount >= talkAdapter.getItemCount())
                    fetchMoreTalks(fetchCount, addTalksTo(talkAdapter));
            }
        });

        talkAdapter.setOnClickListener(new TalkAdapter.OnClickListener() {
            @Override
            public void onClick(Talk talk) {
                Log.i(TAG, "Clicked " + talk.getTitle());
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

        fetching = true;

        tvStatus.setText(R.string.loading);
        tvStatus.setVisibility(View.VISIBLE);

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
                            tvStatus.setVisibility(View.GONE);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Failed to fetch more talks", e);
                }
            }
        }).start();
    }
}
