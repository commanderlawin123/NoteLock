package com.notelock.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Lets you tick which apps need the note quiz before they open. */
public class AppPickerActivity extends Activity {
    private static final class AppItem {
        final String pkg;
        final String label;
        final Drawable icon;

        AppItem(String pkg, String label, Drawable icon) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private final List<AppItem> all = new ArrayList<>();
    private final List<AppItem> shown = new ArrayList<>();
    private Set<String> locked;
    private Adapter adapter;
    private TextView status;
    private String filter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(MainActivity.BG);
        locked = Notes.lockedApps(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(MainActivity.BG);
        col.setPadding(dp(16), dp(24), dp(16), 0);

        TextView title = new TextView(this);
        title.setText("Choose apps to lock");
        title.setTextColor(MainActivity.TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        col.addView(title);

        status = new TextView(this);
        status.setTextColor(MainActivity.TEXT_DIM);
        status.setText("Loading your apps…");
        status.setPadding(0, dp(4), 0, dp(8));
        col.addView(status);

        EditText search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine(true);
        search.setTextColor(MainActivity.TEXT);
        search.setHintTextColor(MainActivity.TEXT_FAINT);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                filter = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });
        col.addView(search);

        ListView list = new ListView(this);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, pos, id) -> toggle(shown.get(pos)));
        col.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(col);

        new Thread(this::loadApps).start();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = pm.queryIntentActivities(launcher, 0);
        Map<String, AppItem> byPkg = new HashMap<>();
        for (ResolveInfo ri : infos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || byPkg.containsKey(pkg)) continue;
            byPkg.put(pkg, new AppItem(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm)));
        }
        List<AppItem> items = new ArrayList<>(byPkg.values());
        // Already-locked apps first, then A to Z.
        items.sort((a, b) -> {
            boolean la = locked.contains(a.pkg), lb = locked.contains(b.pkg);
            if (la != lb) return la ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        new Handler(Looper.getMainLooper()).post(() -> {
            all.clear();
            all.addAll(items);
            applyFilter();
        });
    }

    private void applyFilter() {
        shown.clear();
        for (AppItem a : all) {
            if (filter.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(filter)) shown.add(a);
        }
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void toggle(AppItem a) {
        if (!locked.remove(a.pkg)) locked.add(a.pkg);
        Notes.setLockedApps(this, locked);
        adapter.notifyDataSetChanged();
        updateStatus();
    }

    private void updateStatus() {
        if (all.isEmpty()) return;
        status.setText(locked.size() + " app" + (locked.size() == 1 ? "" : "s")
                + " locked. Tap an app to lock or unlock it.");
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            LinearLayout row;
            ImageView icon;
            TextView label;
            CheckBox box;
            if (convert == null) {
                row = new LinearLayout(AppPickerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(4), dp(10), dp(4), dp(10));
                icon = new ImageView(AppPickerActivity.this);
                row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
                label = new TextView(AppPickerActivity.this);
                label.setTextColor(MainActivity.TEXT);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                label.setPadding(dp(14), 0, dp(8), 0);
                row.addView(label, new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                box = new CheckBox(AppPickerActivity.this);
                box.setClickable(false);
                box.setFocusable(false);
                row.addView(box);
            } else {
                row = (LinearLayout) convert;
                icon = (ImageView) row.getChildAt(0);
                label = (TextView) row.getChildAt(1);
                box = (CheckBox) row.getChildAt(2);
            }
            AppItem a = shown.get(pos);
            icon.setImageDrawable(a.icon);
            label.setText(a.label);
            box.setChecked(locked.contains(a.pkg));
            return row;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
