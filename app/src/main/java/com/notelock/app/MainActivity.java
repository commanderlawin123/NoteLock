package com.notelock.app;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Settings: what to identify, where to use it, permissions, and a test button. */
public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private Button overlayBtn, notifBtn, batteryBtn, pickBtn, accessBtn;
    private CheckBox notesBox, chordsBox, scalesBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF1B1035);
        prefs = Notes.prefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF12101F);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(32), dp(24), dp(32));
        scroll.addView(col);

        col.addView(text("Note Lock", 30, Color.WHITE));
        col.addView(text("Hear a note, chord or scale and pick the right answer to get in. "
                + "In an emergency, swipe up from the bottom to skip.", 15, 0xB3FFFFFF));

        // ---------------------------------------------------- what to identify
        col.addView(text("What to identify", 18, Color.WHITE), margins(28));
        col.addView(text("Turn on any or all. Each question picks one at random, on a random key.",
                13, 0x99FFFFFF));

        notesBox = new CheckBox(this);
        col.addView(typeRow(notesBox, "Notes", Notes.notesOn(prefs), Notes.KEY_NOTES_ON,
                new String[]{"Easy (no sharps)", "Hard (with sharps)"},
                Notes.notesLevel(prefs), Notes.KEY_NOTES_LEVEL), margins(8));

        chordsBox = new CheckBox(this);
        col.addView(typeRow(chordsBox, "Chords", prefs.getBoolean(Notes.KEY_CHORDS_ON, false),
                Notes.KEY_CHORDS_ON,
                new String[]{"Easy (triads)", "Normal (sus & 7th chords)", "Hard (extended chords)"},
                prefs.getInt(Notes.KEY_CHORDS_LEVEL, 0), Notes.KEY_CHORDS_LEVEL), margins(4));

        scalesBox = new CheckBox(this);
        col.addView(typeRow(scalesBox, "Scales", prefs.getBoolean(Notes.KEY_SCALES_ON, false),
                Notes.KEY_SCALES_ON,
                new String[]{"Easy (diatonic)", "Normal (pentatonic & blues)", "Hard (7 modes)"},
                prefs.getInt(Notes.KEY_SCALES_LEVEL, 0), Notes.KEY_SCALES_LEVEL), margins(4));

        CheckBox nameKey = new CheckBox(this);
        nameKey.setText("Name the key too (e.g. \"D Minor\" instead of \"Minor\")");
        nameKey.setTextColor(Color.WHITE);
        nameKey.setChecked(prefs.getBoolean(Notes.KEY_NAME_KEY, false));
        nameKey.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(Notes.KEY_NAME_KEY, on).apply());
        col.addView(nameKey, margins(8));
        col.addView(text("For chords and scales. Off: name only the type. On: name the root note too.",
                13, 0x99FFFFFF));

        // ---------------------------------------------------- where to use it
        col.addView(text("Where to use it", 18, Color.WHITE), margins(28));

        Switch lockScreen = sw("On the lock screen", Notes.isEnabled(this));
        lockScreen.setOnCheckedChangeListener((b, on) -> setLockScreen(on));
        col.addView(lockScreen, margins(8));

        Switch appLock = sw("Before the apps I choose", prefs.getBoolean(Notes.KEY_APPLOCK_ON, true));
        appLock.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean(Notes.KEY_APPLOCK_ON, on).apply();
            if (on && !appLockOn()) {
                Toast.makeText(this, "Also turn on Note Lock in Accessibility (button below)",
                        Toast.LENGTH_LONG).show();
            }
        });
        col.addView(appLock, margins(12));

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

        // ---------------------------------------------------- permissions
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

        batteryBtn = button(v -> startActivity(new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()))));
        col.addView(batteryBtn, margins(8));

        Button test = button(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow 'Display over other apps' first", Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, LockActivity.class));
        });
        test.setText("Try it now");
        col.addView(test, margins(28));

        col.addView(text("Tips\n"
                + "• Set Android's own screen lock to \"Swipe\" or \"None\" so Note Lock is the "
                + "only thing you unlock. If you keep a PIN, Android will ask for it after the quiz.\n"
                + "• On Honor phones: Settings → Apps → App launch → Note Lock → turn off "
                + "\"Manage automatically\" and turn on all three switches. Also lock Note Lock in Recents.\n"
                + "• Sound plays through media volume. Turn it up!", 14, 0x99FFFFFF), margins(28));

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if (Notes.isEnabled(this) && Settings.canDrawOverlays(this)) LockService.start(this);
    }

    /** One row: [x] Notes   [Easy ▾] */
    private View typeRow(CheckBox box, String label, boolean on, String onKey,
                         String[] levels, int level, String levelKey) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        box.setText(label);
        box.setTextColor(Color.WHITE);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        box.setChecked(on);
        row.addView(box, new LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, levels) {
            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, convert, parent);
                t.setTextColor(Color.WHITE);
                return t;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(levels.length - 1, level)));
        spinner.setEnabled(on);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                prefs.edit().putInt(levelKey, pos).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        row.addView(spinner, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        box.setOnCheckedChangeListener((b, checked) -> {
            if (!checked && !notesBox.isChecked() && !chordsBox.isChecked() && !scalesBox.isChecked()) {
                b.setChecked(true);
                Toast.makeText(this, "Keep at least one turned on", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putBoolean(onKey, checked).apply();
            spinner.setEnabled(checked);
        });
        return row;
    }

    private void setLockScreen(boolean on) {
        if (on && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps' so the lock can appear",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        }
        prefs.edit().putBoolean(Notes.KEY_ENABLED, on).apply();
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
        String me = new ComponentName(this, AppLockService.class).flattenToString();
        String meShort = getPackageName() + "/.AppLockService";
        for (String s : on.split(":")) {
            if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(meShort)) return true;
        }
        return false;
    }

    private Switch sw(String label, boolean on) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(Color.WHITE);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        s.setChecked(on);
        return s;
    }

    private Button button(View.OnClickListener l) {
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
