package la.manga.app;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.NotificationCompat;

public class DownloadService extends Service {
    private Looper looper;
    private ServiceHandler handler;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        doStartForeground();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void doStartForeground() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setContentTitle("Title text")
                .setContentText("Content text");


        startForeground(R.integer.downloadService, builder.build());
    }

    private static class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }
}
