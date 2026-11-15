package com.notelock.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Random;

/** The lock screen: play a note, pick it from six buttons, or swipe up to skip. */
public class LockActivity extends Activity {
    private static final int BG_TOP = 0xFF1B1035;
    private static final int BG_BOTTOM = 0xFF0B0B14;
    private static final int BTN = 0xFF2A2350;
    private static final int GOOD = 0xFF2E9E5B;
    private static final int BAD = 0xFFC2413B;
    private static final int ACCENT = 0xFF7C5CFF;

    private final TonePlayer player = new TonePlayer();
    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Button[] options = new Button[6];

    private Notes.Question question;
    private TextView feedback;
    private TextView streakView;
    private boolean played;
    private boolean busy;
    private boolean unlocking;

    private float swipeStartY = -1;
    private float swipeZoneTop;
    private float swipeDistance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        Window w = getWindow();
        w.setStatusBarColor(BG_TOP);
        w.setNavigationBarColor(BG_BOTTOM);

        int screenH = getResources().getDisplayMetrics().heightPixels;
        swipeZoneTop = screenH * 0.78f;  // swipe must start in the bottom part
        swipeDistance = dp(120);

        setContentView(buildUi());
        newQuestion();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        unlocking = false;
        newQuestion();
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeAutoPlay();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) maybeAutoPlay();
    }

    @Override
    protected void onStop() {
        super.onStop();
        player.stop();
        played = false; // replay the same note next time the screen comes on
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        player.stop();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Back does nothing: answer the note or swipe up.
    }

    // ---------------------------------------------------------------- logic

    private void newQuestion() {
        busy = false;
        played = false;
        boolean naturals = Notes.prefs(this).getBoolean(Notes.KEY_NATURALS_ONLY, false);
        question = Notes.newQuestion(rnd, naturals);
        for (int i = 0; i < 6; i++) {
            options[i].setText(Notes.NAMES[question.options[i]]);
            options[i].setBackground(rounded(BTN, 18));
            options[i].setEnabled(true);
        }
        feedback.setText("");
        updateStreak();
        maybeAutoPlay();
    }

    private void maybeAutoPlay() {
        if (played || question == null || !hasWindowFocus()) return;
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) return; // screen is off; wait
        played = true;
        handler.postDelayed(this::playNote, 250);
    }

    private void playNote() {
        if (question != null) player.play(question.midi);
    }

    private void onAnswer(int index) {
        if (busy || unlocking) return;
        busy = true;
        int picked = question.options[index];
        SharedPreferences p = Notes.prefs(this);
        String name = Notes.NAMES[question.answer];

        if (picked == question.answer) {
            options[index].setBackground(rounded(GOOD, 18));
            int streak = p.getInt(Notes.KEY_STREAK, 0) + 1;
            int best = Math.max(streak, p.getInt(Notes.KEY_BEST, 0));
            p.edit().putInt(Notes.KEY_STREAK, streak).putInt(Notes.KEY_BEST, best).apply();
            feedback.setTextColor(0xFF8FE3B0);
            feedback.setText("Correct — " + name);
            updateStreak();
            handler.postDelayed(this::unlock, 450);
        } else {
            options[index].setBackground(rounded(BAD, 18));
            for (int i = 0; i < 6; i++) {
                if (question.options[i] == question.answer) {
                    options[i].setBackground(rounded(GOOD, 18));
                }
            }
            p.edit().putInt(Notes.KEY_STREAK, 0).apply();
            feedback.setTextColor(0xFFFFA8A3);
            feedback.setText("That was " + name + ". Here's a new note…");
            shake(options[index]);
            handler.postDelayed(this::newQuestion, 1600);
        }
    }

    private void unlock() {
        if (unlocking) return;
        unlocking = true;
        player.stop();
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && km.isKeyguardLocked()) {
            // Swipe lock: dismissed straight away. PIN/pattern/fingerprint: Android asks for it next.
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { finish(); }
                @Override public void onDismissCancelled() { finish(); }
                @Override public void onDismissError() { finish(); }
            });
        } else {
            finish();
        }
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    /** Swipe up from the bottom of the screen = emergency unlock. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                swipeStartY = e.getRawY() >= swipeZoneTop ? e.getRawY() : -1;
                break;
            case MotionEvent.ACTION_MOVE:
                if (swipeStartY >= 0 && swipeStartY - e.getRawY() > swipeDistance) {
                    swipeStartY = -1;
                    unlock();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                swipeStartY = -1;
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    private void updateStreak() {
        SharedPreferences p = Notes.prefs(this);
        streakView.setText("Streak " + p.getInt(Notes.KEY_STREAK, 0)
                + "   ·   Best " + p.getInt(Notes.KEY_BEST, 0));
    }

    // ---------------------------------------------------------------- UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP, BG_BOTTOM}));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        clock.setGravity(Gravity.CENTER);
        root.addView(clock);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTextColor(0xB3FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        date.setGravity(Gravity.CENTER);
        root.addView(date);

        root.addView(space(1f));

        TextView title = text("Name the note to unlock", 20, Color.WHITE);
        root.addView(title);

        Button speaker = new Button(this);
        speaker.setText("🔊");
        speaker.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(ACCENT);
        speaker.setBackground(circle);
        speaker.setContentDescription("Play the note again");
        speaker.setOnClickListener(v -> playNote());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(112), dp(112));
        sp.topMargin = dp(20);
        sp.bottomMargin = dp(8);
        root.addView(speaker, sp);

        TextView hint = text("Tap to hear it again", 13, 0x99FFFFFF);
        root.addView(hint);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gp.topMargin = dp(24);
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                final int idx = r * 2 + c;
                Button b = new Button(this);
                b.setAllCaps(false);
                b.setTextColor(Color.WHITE);
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                b.setBackground(rounded(BTN, 18));
                b.setOnClickListener(v -> onAnswer(idx));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(64), 1f);
                bp.setMargins(dp(6), dp(6), dp(6), dp(6));
                row.addView(b, bp);
                options[idx] = b;
            }
            grid.addView(row);
        }
        root.addView(grid, gp);

        feedback = text("", 16, Color.WHITE);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32));
        fp.topMargin = dp(12);
        root.addView(feedback, fp);

        streakView = text("", 13, 0x80FFFFFF);
        root.addView(streakView);

        root.addView(space(1f));

        TextView arrow = text("︿", 24, 0xCCFFFFFF);
        root.addView(arrow);
        TextView swipe = text("Swipe up for emergency unlock", 14, 0xCCFFFFFF);
        root.addView(swipe);
        return root;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private View space(float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, 0, weight));
        return v;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private void shake(View v) {
        v.animate().translationX(dp(10)).setDuration(50).withEndAction(() ->
                v.animate().translationX(-dp(10)).setDuration(80).withEndAction(() ->
                        v.animate().translationX(0).setDuration(50).start()).start()).start();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
