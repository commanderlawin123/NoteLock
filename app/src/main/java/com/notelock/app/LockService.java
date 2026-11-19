package com.notelock.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Runs in the background while the lock screen or app lock is on.
 * - Lock screen: when the screen turns off, the quiz is put up so it's waiting next time.
 * - App lock: while the screen is on, AppWatch checks which app is in front.
 */
public class LockService extends Service {
    private static final String CHANNEL = "notelock_service";

    private AppWatch watch;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                watch.stop(); // nothing runs while the screen is off
                showLock(context);
            } else if (Intent.ACTION_SCREEN_ON.equals(a)) {
                if (AppWatch.active(context)) watch.start();
            }
        }
    };

    /** Should the background service be running at all? */
    static boolean shouldRun(Context c) {
        return Notes.isEnabled(c) || AppWatch.active(c);
    }

    /** Start or stop the service to match the current settings. */
    public static void refresh(Context c) {
        if (shouldRun(c)) {
            c.startForegroundService(new Intent(c, LockService.class));
        } else {
            c.stopService(new Intent(c, LockService.class));
        }
    }

    static void showLock(Context c) {
        if (!Notes.isEnabled(c) || !Settings.canDrawOverlays(c)) return;
        if (LockActivity.screenLockShowing) return; // already waiting; don't reopen it
        Intent lock = new Intent(c, LockActivity.class);
        lock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        c.startActivity(lock);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        watch = new AppWatch(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "Note Lock running", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notelock)
                .setContentTitle("Note Lock is on")
                .setContentText("Name the note to get in")
                .setOngoing(true)
                .setContentIntent(open)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, f);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!shouldRun(this)) {
            watch.stop();
            stopSelf();
            return START_NOT_STICKY;
        }
        PowerManager pm = getSystemService(PowerManager.class);
        boolean screenOn = pm == null || pm.isInteractive();
        if (AppWatch.active(this) && screenOn) watch.start(); else watch.stop();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        watch.stop();
        try {
            unregisterReceiver(screenReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
