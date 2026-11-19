package com.notelock.app;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.Set;

/**
 * App lock using Android's "Usage access". While the screen is on, it checks about
 * once a second which app is in front. If it's one you chose, the quiz is shown first.
 * Nothing runs while the screen is off.
 */
final class AppWatch {
    private static final long INTERVAL_MS = 800;

    /** Package the user has already passed the quiz for; cleared when they leave it. */
    static volatile String unlockedPkg;
    /** The app that was last in front (used so the lock screen also unlocks it). */
    static volatile String lastForeground;

    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final UsageStatsManager usm;
    private boolean running;
    private long lastQuery;
    private String lastLaunchPkg;
    private long lastLaunchAt;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            check();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    AppWatch(Context c) {
        ctx = c.getApplicationContext();
        usm = ctx.getSystemService(UsageStatsManager.class);
    }

    static void markUnlocked(String pkg) {
        if (pkg != null) unlockedPkg = pkg;
    }

    /** True if the user turned on app lock, granted Usage access and picked at least one app. */
    static boolean active(Context c) {
        return Notes.prefs(c).getBoolean(Notes.KEY_APPLOCK_ON, true)
                && hasUsageAccess(c)
                && !Notes.lockedApps(c).isEmpty();
    }

    static boolean hasUsageAccess(Context c) {
        AppOpsManager aom = c.getSystemService(AppOpsManager.class);
        if (aom == null) return false;
        int mode = Build.VERSION.SDK_INT >= 29
                ? aom.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                : aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return c.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    void start() {
        if (running || usm == null) return;
        running = true;
        lastQuery = System.currentTimeMillis() - 10_000; // catch the app that's already open
        handler.post(tick);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private void check() {
        if (!active(ctx)) return;
        long now = System.currentTimeMillis();
        UsageEvents events;
        try {
            events = usm.queryEvents(lastQuery, now);
        } catch (RuntimeException e) {
            return;
        }
        lastQuery = now;
        String front = null;
        UsageEvents.Event e = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(e);
            // ACTIVITY_RESUMED (API 29+) has the same value as the older MOVE_TO_FOREGROUND.
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) front = e.getPackageName();
        }
        if (front != null) onForeground(front);
    }

    private void onForeground(String pkg) {
        if (pkg.equals(ctx.getPackageName())) return;
        lastForeground = pkg;
        if (!pkg.equals(unlockedPkg)) unlockedPkg = null;
        if (unlockedPkg != null) return;

        Set<String> locked = Notes.lockedApps(ctx);
        if (!locked.contains(pkg)) return;
        if (pkg.equals(LockActivity.showingFor)) return;
        if (!Settings.canDrawOverlays(ctx)) return; // needed to open the quiz over another app
        long t = SystemClock.elapsedRealtime();
        if (pkg.equals(lastLaunchPkg) && t - lastLaunchAt < 1500) return;
        lastLaunchPkg = pkg;
        lastLaunchAt = t;

        Intent i = new Intent(ctx, LockActivity.class)
                .putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_APP)
                .putExtra(LockActivity.EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        ctx.startActivity(i);
    }
}
