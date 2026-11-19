package com.notelock.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Settings: permissions, what to identify, where to use it, and a test button. */
public class MainActivity extends Activity {
    // Palette (change these to restyle the whole screen)
    static final int BG = 0xFF180D21;          // Void Purple: main background
    static final int CARD = 0xFF2D1B3E;        // Muted Plum: section panels
    static final int BORDER = 0xFFB026FF;      // Overdrive Violet: section border
    static final int VIOLET = 0xFFB026FF;      // Overdrive Violet: buttons, selected states
    static final int VIOLET_DOWN = 0xFF8A1DCC; // pressed button
    static final int DISABLED = 0xFF45305A;    // greyed-out button
    static final int ACCENT = 0xFFFF0055;      // Hot Magenta
    static final int LIGHT = 0xFFD9A3FF;       // light violet for links and icons
    static final int ACCENT_SOFT = 0xFFFF7AA2; // soft magenta for notes that need action
    static final int TEXT = 0xFFF5F5F5;        // Soft Frost
    static final int TEXT_DIM = 0xCCF5F5F5;
    static final int TEXT_FAINT = 0x99F5F5F5;

    private SharedPreferences prefs;
    private Button overlayBtn, notifBtn, batteryBtn, usageBtn, pickBtn, buyBtn;
    private CheckBox notesBox, chordsBox, scalesBox;
    private Spinner chordsSpinner, scalesSpinner;
    private Switch appLockSwitch;
    private TextView appLockNote, proText, restoreLink;
    private Pro pro;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = Notes.prefs(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(28), dp(18), dp(32));
        scroll.addView(col);

        // ---------------------------------------------------- header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_notelock);
        logo.setImageTintList(ColorStateList.valueOf(VIOLET));
        header.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView name = text("Note Lock", 32, TEXT);
        name.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        name.setPadding(dp(8), 0, 0, 0);
        header.addView(name);
        col.addView(header);

        // ---------------------------------------------------- intro
        LinearLayout intro = card(col, null);
        intro.addView(text("Hear a note, chord or scale and pick the right answer to get in. "
                + "In an emergency, swipe up from the bottom to skip.", 15, TEXT_DIM));

        // ---------------------------------------------------- permissions
        LinearLayout perms = card(col, "Permissions");
        overlayBtn = button(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        perms.addView(overlayBtn, margins(4));

        notifBtn = button(v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        });
        perms.addView(notifBtn, margins(8));

        batteryBtn = button(v -> startActivity(new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()))));
        perms.addView(batteryBtn, margins(8));

        usageBtn = button(v -> openUsageAccess());
        perms.addView(usageBtn, margins(8));
        perms.addView(text("Optional. Only needed for \"Before the apps I choose\". It uses slightly "
                + "more battery: while your screen is on, Note Lock checks which app is open about "
                + "once a second. Nothing runs while the screen is off.", 13, TEXT_FAINT), margins(4));

        TextView info = text("Can't turn a permission on? Open App info", 14, LIGHT);
        info.setPaintFlags(info.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        Drawable icon = getDrawable(android.R.drawable.ic_dialog_info);
        if (icon != null) {
            icon = icon.mutate();
            icon.setTint(LIGHT);
            icon.setBounds(0, 0, dp(18), dp(18));
            info.setCompoundDrawablesRelative(icon, null, null, null);
            info.setCompoundDrawablePadding(dp(6));
        }
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(0, dp(6), 0, dp(6));
        info.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        perms.addView(info, margins(12));
        perms.addView(text("If Android says a setting is restricted: open App info, "
                + "tap ⋮ (top right) → \"Allow restricted settings\", then try again.", 13, TEXT_FAINT));

        // ---------------------------------------------------- what to identify
        LinearLayout what = card(col, "What to identify");
        what.addView(text("Turn on any or all. Each question picks one at random, on a random key.",
                13, TEXT_FAINT));

        notesBox = new CheckBox(this);
        chordsBox = new CheckBox(this);
        scalesBox = new CheckBox(this);
        what.addView(typeRow(notesBox, "Notes", Notes.notesOn(prefs), Notes.KEY_NOTES_ON,
                new String[]{"Easy (no sharps)", "Hard (with sharps)"},
                Notes.notesLevel(prefs), Notes.KEY_NOTES_LEVEL), margins(10));
        what.addView(typeRow(chordsBox, "Chords", prefs.getBoolean(Notes.KEY_CHORDS_ON, false),
                Notes.KEY_CHORDS_ON,
                new String[]{"Easy (triads)", "Normal (sus & 7th chords)", "Hard (extended chords)"},
                prefs.getInt(Notes.KEY_CHORDS_LEVEL, 0), Notes.KEY_CHORDS_LEVEL), margins(4));
        what.addView(typeRow(scalesBox, "Scales", prefs.getBoolean(Notes.KEY_SCALES_ON, false),
                Notes.KEY_SCALES_ON,
                new String[]{"Easy (diatonic)", "Normal (pentatonic & blues)", "Hard (7 modes)"},
                prefs.getInt(Notes.KEY_SCALES_LEVEL, 0), Notes.KEY_SCALES_LEVEL), margins(4));

        CheckBox nameKey = check("Name the key too (e.g. \"D Minor\" instead of \"Minor\")",
                prefs.getBoolean(Notes.KEY_NAME_KEY, false));
        nameKey.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(Notes.KEY_NAME_KEY, on).apply());
        what.addView(nameKey, margins(10));
        what.addView(text("For chords and scales. Off: name only the type. On: name the root note too.",
                13, TEXT_FAINT));

        // Note Lock Pro (one-time purchase) unlocks chords and scales.
        proText = text("", 14, TEXT_DIM);
        what.addView(proText, margins(14));
        buyBtn = button(v -> pro.buy());
        buyBtn.setText("Unlock chords & scales");
        what.addView(buyBtn, margins(8));
        restoreLink = text("Already bought it? Restore purchase", 14, LIGHT);
        restoreLink.setPaintFlags(restoreLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        restoreLink.setPadding(0, dp(8), 0, dp(4));
        restoreLink.setOnClickListener(v -> pro.restore(true));
        what.addView(restoreLink, margins(4));

        // ---------------------------------------------------- where to use it
        LinearLayout where = card(col, "Where to use it");
        Switch lockScreen = sw("On the lock screen", Notes.isEnabled(this));
        lockScreen.setOnCheckedChangeListener((b, on) -> setLockScreen(on));
        where.addView(lockScreen, margins(4));

        appLockSwitch = sw("Before the apps I choose", prefs.getBoolean(Notes.KEY_APPLOCK_ON, true));
        appLockSwitch.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean(Notes.KEY_APPLOCK_ON, on).apply();
            refresh();
            LockService.refresh(this);
        });
        where.addView(appLockSwitch, margins(14));
        appLockNote = text("Turn on Usage access under Permissions first.", 13, ACCENT_SOFT);
        where.addView(appLockNote, margins(2));

        pickBtn = button(v -> startActivity(new Intent(this, AppPickerActivity.class)));
        pickBtn.setEnabled(appLockSwitch.isChecked());
        where.addView(pickBtn, margins(8));

        // ---------------------------------------------------- try it
        LinearLayout tryCard = card(col, null);
        Button test = button(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Allow 'Display over other apps' first", Toast.LENGTH_LONG).show();
                return;
            }
            startActivity(new Intent(this, LockActivity.class));
        });
        test.setText("Try it now");
        tryCard.addView(test);

        // ---------------------------------------------------- tips
        LinearLayout tips = card(col, "Tips");
        tips.addView(text("• Set Android's own screen lock to \"Swipe\" or \"None\" so Note Lock is the "
                + "only thing you unlock. If you keep a PIN, Android will ask for it after the quiz.\n"
                + "• On Honor phones: Settings → Apps → App launch → Note Lock → turn off "
                + "\"Manage automatically\" and turn on all three switches. Also lock Note Lock in Recents.\n"
                + "• Sound plays through media volume. Turn it up!", 14, TEXT_DIM));

        pro = new Pro(this, new Pro.Listener() {
            @Override public void onProChanged(boolean owned) { refreshPro(); }
            @Override public void onPrice(String price) {
                // The price is shown by Google Play's checkout, not on the button.
            }
            @Override public void onMessage(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        pro.connect();

        scroll.setClipToPadding(false);
        Ui.fitSystemBars(scroll);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        LockService.refresh(this);
        if (pro != null) {
            pro.refreshProduct();
            pro.restore(false);
        }
        askForOverlayOnce();
    }

    @Override
    protected void onDestroy() {
        if (pro != null) pro.end();
        super.onDestroy();
    }

    private boolean askedOverlay;

    /** First run: the lock screen is on by default, so ask for the one permission it needs. */
    private void askForOverlayOnce() {
        if (askedOverlay || !Notes.isEnabled(this) || Settings.canDrawOverlays(this)) return;
        askedOverlay = true;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Allow Note Lock on your lock screen")
                .setMessage("To show the quiz when you wake your phone, Note Lock needs "
                        + "\"Display over other apps\". Turn it on for Note Lock on the next screen.")
                .setPositiveButton("Allow", (dlg, w) -> startActivity(new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Not now", null)
                .show();
    }

    private void openUsageAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (RuntimeException e) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    private void refreshPro() {
        boolean owned = Pro.owned(this);
        chordsBox.setEnabled(owned);
        scalesBox.setEnabled(owned);
        chordsBox.setText(owned ? "Chords" : "Chords 🔒");
        scalesBox.setText(owned ? "Scales" : "Scales 🔒");
        chordsSpinner.setEnabled(owned && chordsBox.isChecked());
        scalesSpinner.setEnabled(owned && scalesBox.isChecked());
        proText.setText(owned ? "✓ Note Lock Pro is unlocked. Thank you!"
                : "Chords and scales are part of Note Lock Pro, a one-time purchase.");
        buyBtn.setVisibility(owned ? View.GONE : View.VISIBLE);
        restoreLink.setVisibility(owned ? View.GONE : View.VISIBLE);
    }

    private void refresh() {
        boolean overlay = Settings.canDrawOverlays(this);
        overlayBtn.setText((overlay ? "✓ " : "") + "Display over other apps");

        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        notifBtn.setText((notif ? "✓ " : "") + "Notifications (keeps it running)");
        notifBtn.setEnabled(!notif);

        PowerManager pm = getSystemService(PowerManager.class);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        batteryBtn.setText((battery ? "✓ " : "") + "Don't let battery saver stop it");
        batteryBtn.setEnabled(!battery);

        boolean usage = AppWatch.hasUsageAccess(this);
        usageBtn.setText((usage ? "✓ " : "") + "App lock: Usage access (optional)");

        // "Before the apps I choose" needs Usage access first.
        appLockSwitch.setEnabled(usage);
        appLockNote.setVisibility(usage ? View.GONE : View.VISIBLE);
        int n = Notes.lockedApps(this).size();
        pickBtn.setText("Choose apps to lock (" + n + " selected)");
        pickBtn.setEnabled(usage && appLockSwitch.isChecked());
        refreshPro();
    }

    /** One row: [x] Notes   [Easy ▾] */
    private View typeRow(CheckBox box, String label, boolean on, String onKey,
                         String[] levels, int level, String levelKey) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        styleCheck(box, label, on);
        box.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        row.addView(box, new LinearLayout.LayoutParams(dp(104), LinearLayout.LayoutParams.WRAP_CONTENT));

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, levels) {
            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, convert, parent);
                t.setTextColor(spinnerEnabled(parent) ? TEXT : TEXT_FAINT);
                return t;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Math.min(levels.length - 1, level)));
        spinner.setEnabled(on);
        spinner.setBackgroundTintList(ColorStateList.valueOf(LIGHT));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                prefs.edit().putInt(levelKey, pos).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        row.addView(spinner, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (box == chordsBox) chordsSpinner = spinner;
        if (box == scalesBox) scalesSpinner = spinner;

        box.setOnCheckedChangeListener((b, checked) -> {
            if (!checked && !notesBox.isChecked() && !chordsBox.isChecked() && !scalesBox.isChecked()) {
                b.setChecked(true);
                Toast.makeText(this, "Keep at least one turned on", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putBoolean(onKey, checked).apply();
            spinner.setEnabled(checked);
            adapter.notifyDataSetChanged();
        });
        return row;
    }

    private static boolean spinnerEnabled(ViewGroup parent) {
        return parent == null || parent.isEnabled();
    }

    private void setLockScreen(boolean on) {
        if (on && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow 'Display over other apps' so the lock can appear",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        }
        prefs.edit().putBoolean(Notes.KEY_ENABLED, on).apply();
        LockService.refresh(this);
    }

    // ---------------------------------------------------------------- styling helpers

    /** A bordered section card, optionally with a heading. Returns its content column. */
    private LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(2), BORDER);
        c.setBackground(bg);
        if (heading != null) {
            TextView h = text(heading, 19, TEXT);
            h.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            h.setPadding(0, 0, 0, dp(6));
            c.addView(h);
        }
        parent.addView(c, margins(16));
        return c;
    }

    private Button button(View.OnClickListener l) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(new ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{0xB3F5F5F5, TEXT}));
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, rounded(DISABLED));
        states.addState(new int[]{android.R.attr.state_pressed}, rounded(VIOLET_DOWN));
        states.addState(new int[]{}, rounded(VIOLET));
        b.setBackground(states);
        b.setStateListAnimator(null);
        b.setMinHeight(dp(50));
        b.setOnClickListener(l);
        return b;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(14));
        return d;
    }

    private Switch sw(String label, boolean on) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(new ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{0x66F5F5F5, TEXT}));
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        s.setChecked(on);
        s.setThumbTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{TEXT, 0xFFBDB3C7}));
        s.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{VIOLET, 0xFF5A4670}));
        return s;
    }

    private CheckBox check(String label, boolean on) {
        CheckBox c = new CheckBox(this);
        styleCheck(c, label, on);
        return c;
    }

    private void styleCheck(CheckBox c, String label, boolean on) {
        c.setText(label);
        c.setTextColor(new ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{0x66F5F5F5, TEXT}));
        c.setChecked(on);
        c.setButtonTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{VIOLET, 0xFFBDB3C7}));
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
