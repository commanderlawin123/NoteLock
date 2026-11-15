package com.notelock.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Settings screen: turn the lock on, grant permissions, try it out. */
public class MainActivity extends Activity {
    private Switch enable;
    private Button overlayBtn, notifBtn, batteryBtn, pickBtn, accessBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF1B1035);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF12101F);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(32), dp(24), dp(32));
        scroll.addView(col);

        col.addView(text("Note Lock", 30, Color.WHITE));
        col.addView(text("When your screen turns on, you'll hear a note. Pick the right one "
                + "of six to unlock. To skip it in an emergency, swipe up from the bottom.", 15, 0xB3FFFFFF));

        enable = new Switch(this);
        enable.setText("Lock screen: name a note to unlock");
        enable.setTextColor(Color.WHITE);
        enable.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        enable.setChecked(Notes.isEnabled(this));
        enable.setOnCheckedChangeListener((b, on) -> setEnabled(on));
        col.addView(enable, margins(24));

        CheckBox naturals = new CheckBox(this);
        naturals.setText("Easy mode: natural notes only (no sharps)");
        naturals.setTextColor(Color.WHITE);
        naturals.setChecked(Notes.prefs(this).getBoolean(Notes.KEY_NATURALS_ONLY, false));
        naturals.setOnCheckedChangeListener((b, on) ->
                Notes.prefs(this).edit().putBoolean(Notes.KEY_NATURALS_ONLY, on).apply());
        col.addView(naturals, margins(12));

        col.addView(text("Permissions", 18, Color.WHITE), margins(28));

        overlayBtn = button(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        col.addView(overlayBtn, margins(8));

        notifBtn = button(v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        });
        col.addView(notifBtn, margins(8));

        batteryBtn = button(v -> {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        col.addView(batteryBtn, margins(8));

        Button test = button(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow 'Display over other apps' first", Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, LockActivity.class));
        });
        test.setText("Try the lock screen now");
        col.addView(test, margins(28));

        col.addView(text("App lock", 18, Color.WHITE), margins(32));
        col.addView(text("Pick apps (like Facebook, TikTok or Instagram) that need the note "
                + "quiz before they open. Once you pass, the app stays open until you leave it.",
                14, 0xB3FFFFFF));

        pickBtn = button(v -> startActivity(new Intent(this, AppPickerActivity.class)));
        col.addView(pickBtn, margins(8));

        accessBtn = button(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        col.addView(accessBtn, margins(8));

        Button restricted = button(v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        restricted.setText("Can't turn it on? Open App info");
        col.addView(restricted, margins(8));
        col.addView(text("If Android says the setting is restricted: tap the button above, "
                + "tap ⋮ (top right) → \"Allow restricted settings\", then try again. "
                + "Android will warn that the app can see your screen. Note Lock only checks "
                + "which app opened, and it has no internet access.", 13, 0x99FFFFFF), margins(4));

        col.addView(text("Tips\n"
                + "• Set Android's own screen lock to \"Swipe\" or \"None\" so Note Lock is the "
                + "only thing you unlock. If you keep a PIN, Android will ask for it after the note.\n"
                + "• On Xiaomi, Oppo, Vivo or Realme phones, also allow \"Show on lock screen\", "
                + "\"Autostart\" and \"Pop-up windows\" for Note Lock in its app settings.\n"
                + "• The note plays through media volume. Turn it up!", 14, 0x99FFFFFF), margins(28));

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if (Notes.isEnabled(this) && Settings.canDrawOverlays(this)) LockService.start(this);
    }

    private void setEnabled(boolean on) {
        if (on && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps' so the lock can appear",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        }
        Notes.prefs(this).edit().putBoolean(Notes.KEY_ENABLED, on).apply();
        if (on) LockService.start(this); else LockService.stop(this);
    }

    private void refresh() {
        boolean overlay = Settings.canDrawOverlays(this);
        overlayBtn.setText((overlay ? "✓ " : "1. ") + "Display over other apps");

        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        notifBtn.setText((notif ? "✓ " : "2. ") + "Notifications (keeps it running)");
        notifBtn.setEnabled(!notif);

        PowerManager pm = getSystemService(PowerManager.class);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        batteryBtn.setText((battery ? "✓ " : "3. ") + "Don't let battery saver stop it");
        batteryBtn.setEnabled(!battery);

        int n = Notes.lockedApps(this).size();
        pickBtn.setText("Choose apps to lock (" + n + " selected)");
        accessBtn.setText((appLockOn() ? "✓ App lock is on" : "Turn on app lock")
                + " (Accessibility → Note Lock)");
    }

    private boolean appLockOn() {
        String on = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (on == null) return false;
        String me = new android.content.ComponentName(this, AppLockService.class).flattenToString();
        String meShort = getPackageName() + "/.AppLockService";
        for (String s : on.split(":")) {
            if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }

    private Button button(android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    private LinearLayout.LayoutParams margins(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
