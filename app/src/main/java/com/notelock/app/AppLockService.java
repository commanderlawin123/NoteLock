package com.notelock.app;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.util.Set;

/**
 * App lock. Android tells this service whenever a new screen opens (no polling,
 * so it uses almost no battery). If that screen belongs to an app you chose,
 * the note quiz is shown first.
 */
public class AppLockService extends AccessibilityService {
    /** Package the user has already passed the quiz for; cleared when they leave it. */
    static volatile String unlockedPkg;
    /** The app that was last in front (used so the lock screen also unlocks it). */
    static volatile String lastForeground;

    private String lastLaunchPkg;
    private long lastLaunchAt;

    static void markUnlocked(String pkg) {
        if (pkg != null) unlockedPkg = pkg;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence p = e.getPackageName();
        CharSequence c = e.getClassName();
        if (p == null || c == null) return;
        String pkg = p.toString();
        if (pkg.equals(getPackageName())) return;
        // Only real app screens count. Dialogs, keyboards, the notification shade
        // and chat bubbles are ignored, so they don't re-lock an app.
        if (!isActivity(pkg, c.toString())) return;

        lastForeground = pkg;
        if (!pkg.equals(unlockedPkg)) unlockedPkg = null;
        if (unlockedPkg != null) return;

        Set<String> locked = Notes.lockedApps(this);
        if (!locked.contains(pkg)) return;
        if (pkg.equals(LockActivity.showingFor)) return;
        long now = SystemClock.elapsedRealtime();
        if (pkg.equals(lastLaunchPkg) && now - lastLaunchAt < 1500) return;
        lastLaunchPkg = pkg;
        lastLaunchAt = now;

        Intent i = new Intent(this, LockActivity.class)
                .putExtra(LockActivity.EXTRA_MODE, LockActivity.MODE_APP)
                .putExtra(LockActivity.EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(i);
    }

    private boolean isActivity(String pkg, String cls) {
        try {
            getPackageManager().getActivityInfo(new ComponentName(pkg, cls), 0);
            return true;
        } catch (PackageManager.NameNotFoundException ex) {
            return false;
        }
    }

    @Override
    public void onInterrupt() {
    }
}
