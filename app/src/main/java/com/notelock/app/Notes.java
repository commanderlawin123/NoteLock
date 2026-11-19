package com.notelock.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;

/** Note names, question generation and app settings. */
public final class Notes {
    public static final String[] NAMES =
            {"C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B"};
    private static final int[] NATURALS = {0, 2, 4, 5, 7, 9, 11};

    static final String PREFS = "notelock";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_NATURALS_ONLY = "naturals_only";
    static final String KEY_STREAK = "streak";
    static final String KEY_BEST = "best";

    private Notes() {}

    /** One question: the note to play and six answer choices (pitch classes 0-11). */
    public static final class Question {
        public final int midi;
        public final int answer;
        public final int[] options;

        Question(int midi, int answer, int[] options) {
            this.midi = midi;
            this.answer = answer;
            this.options = options;
        }
    }

    public static Question newQuestion(Random rnd, boolean naturalsOnly) {
        List<Integer> pool = new ArrayList<>();
        if (naturalsOnly) {
            for (int n : NATURALS) pool.add(n);
        } else {
            for (int n = 0; n < 12; n++) pool.add(n);
        }
        int answer = pool.get(rnd.nextInt(pool.size()));
        int octave = 3 + rnd.nextInt(3);              // octaves 3, 4 or 5
        int midi = 12 * (octave + 1) + answer;        // C4 = MIDI 60

        List<Integer> others = new ArrayList<>(pool);
        others.remove(Integer.valueOf(answer));
        Collections.shuffle(others, rnd);
        List<Integer> choices = new ArrayList<>();
        choices.add(answer);
        for (int i = 0; i < 5; i++) choices.add(others.get(i));
        Collections.shuffle(choices, rnd);

        int[] options = new int[6];
        for (int i = 0; i < 6; i++) options[i] = choices.get(i);
        return new Question(midi, answer, options);
    }

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context c) {
        return prefs(c).getBoolean(KEY_ENABLED, true); // lock screen is on by default
    }

    static final String KEY_LOCKED_APPS = "locked_apps";
    static final String KEY_APPLOCK_ON = "applock_on";
    static final String KEY_NOTES_ON = "q_notes";
    static final String KEY_NOTES_LEVEL = "q_notes_level";
    static final String KEY_CHORDS_ON = "q_chords";
    static final String KEY_CHORDS_LEVEL = "q_chords_level";
    static final String KEY_SCALES_ON = "q_scales";
    static final String KEY_SCALES_LEVEL = "q_scales_level";
    static final String KEY_NAME_KEY = "name_key";
    static final String KEY_PRO = "pro_owned";

    static boolean notesOn(SharedPreferences p) {
        return p.getBoolean(KEY_NOTES_ON, true);
    }

    /** 0 = easy (no sharps), 1 = hard. Carries over the old "easy mode" setting. */
    static int notesLevel(SharedPreferences p) {
        return p.getInt(KEY_NOTES_LEVEL, p.getBoolean(KEY_NATURALS_ONLY, false) ? 0 : 1);
    }

    public static Set<String> lockedApps(Context c) {
        return new HashSet<>(prefs(c).getStringSet(KEY_LOCKED_APPS, new HashSet<>()));
    }

    public static void setLockedApps(Context c, Set<String> apps) {
        prefs(c).edit().putStringSet(KEY_LOCKED_APPS, new HashSet<>(apps)).apply();
    }
}
