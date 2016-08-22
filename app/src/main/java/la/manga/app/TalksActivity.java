package la.manga.app;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class TalksActivity extends AppCompatActivity {
    private ListView talksListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_talks);
        Toolbar toolbar = (Toolbar) findViewById(R.id.talksToolbar);
        setSupportActionBar(toolbar);
        talksListView = (ListView) findViewById(R.id.talksListView);
    }

    @Override
    protected void onResume() {
        super.onResume();

        talksListView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new String[] {
                "One", "Two", "Three"
        }));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_talks, menu);
        return true;
    }
}
