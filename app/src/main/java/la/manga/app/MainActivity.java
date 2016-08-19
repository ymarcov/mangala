package la.manga.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler = new Handler();
        //startService(new Intent(this, DownloadService.class));
    }

    @Override
    protected void onResume() {
        super.onResume();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                TextView subtitle = (TextView) findViewById(R.id.subtitle);
                subtitle.setVisibility(View.INVISIBLE);

                RelativeLayout layout = (RelativeLayout) findViewById(R.id.contentArea);
                layout.setVisibility(View.VISIBLE);

                startActivity(new Intent(MainActivity.this, DownloadsActivity.class));
            }
        }, 1500);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
}
