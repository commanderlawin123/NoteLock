package com.notelock.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/** Builds the quiz questions: single notes, chords or scales, on a random root. */
public final class Music {
    public static final int CAT_NOTE = 0;
    public static final int CAT_CHORD = 1;
    public static final int CAT_SCALE = 2;

    static final class Type {
        final String name;
        final int[] intervals;

        Type(String name, int... intervals) {
            this.name = name;
            this.intervals = intervals;
        }
    }

    /** [level][type]: 0 easy, 1 normal, 2 hard. */
    static final Type[][] CHORDS = {
            { // Easy: triads
                    new Type("Major", 0, 4, 7),
                    new Type("Minor", 0, 3, 7),
                    new Type("Diminished", 0, 3, 6),
                    new Type("Augmented", 0, 4, 8)},
            { // Normal: suspended and seventh chords
                    new Type("Sus2", 0, 2, 7),
                    new Type("Sus4", 0, 5, 7),
                    new Type("Major 7", 0, 4, 7, 11),
                    new Type("Dominant 7", 0, 4, 7, 10),
                    new Type("Minor 7", 0, 3, 7, 10),
                    new Type("Half-diminished 7", 0, 3, 6, 10),
                    new Type("Diminished 7", 0, 3, 6, 9)},
            { // Hard: extended chords
                    new Type("Major 9", 0, 4, 7, 11, 14),
                    new Type("Dominant 9", 0, 4, 7, 10, 14),
                    new Type("Minor 9", 0, 3, 7, 10, 14),
                    new Type("Dominant 11", 0, 4, 7, 10, 14, 17),
                    new Type("Minor 11", 0, 3, 7, 10, 14, 17),
                    new Type("Major 13", 0, 4, 7, 11, 14, 21),
                    new Type("Dominant 13", 0, 4, 7, 10, 14, 21)},
    };

    static final Type[][] SCALES = {
            { // Easy: diatonic (major and minor family)
                    new Type("Major", 0, 2, 4, 5, 7, 9, 11, 12),
                    new Type("Natural minor", 0, 2, 3, 5, 7, 8, 10, 12),
                    new Type("Harmonic minor", 0, 2, 3, 5, 7, 8, 11, 12),
                    new Type("Melodic minor", 0, 2, 3, 5, 7, 9, 11, 12)},
            { // Normal: pentatonic and blues
                    new Type("Major pentatonic", 0, 2, 4, 7, 9, 12),
                    new Type("Minor pentatonic", 0, 3, 5, 7, 10, 12),
                    new Type("Minor blues", 0, 3, 5, 6, 7, 10, 12),
                    new Type("Major blues", 0, 2, 3, 4, 7, 9, 12)},
            { // Hard: the seven modes
                    new Type("Ionian", 0, 2, 4, 5, 7, 9, 11, 12),
                    new Type("Dorian", 0, 2, 3, 5, 7, 9, 10, 12),
                    new Type("Phrygian", 0, 1, 3, 5, 7, 8, 10, 12),
                    new Type("Lydian", 0, 2, 4, 6, 7, 9, 11, 12),
                    new Type("Mixolydian", 0, 2, 4, 5, 7, 9, 10, 12),
                    new Type("Aeolian", 0, 2, 3, 5, 7, 8, 10, 12),
                    new Type("Locrian", 0, 1, 3, 5, 6, 8, 10, 12)},
    };

    public static final class Question {
        public final int category;
        public final int[] midis;     // notes to play
        public final String[] options; // button labels
        public final int answer;       // index of the right button

        Question(int category, int[] midis, String[] options, int answer) {
            this.category = category;
            this.midis = midis;
            this.options = options;
            this.answer = answer;
        }

        public String answerLabel() {
            return options[answer];
        }
    }

    private Music() {}

    public static Question next(Context c, Random rnd) {
        SharedPreferences p = Notes.prefs(c);
        List<Integer> cats = new ArrayList<>();
        if (Notes.notesOn(p)) cats.add(CAT_NOTE);
        if (p.getBoolean(Notes.KEY_CHORDS_ON, false)) cats.add(CAT_CHORD);
        if (p.getBoolean(Notes.KEY_SCALES_ON, false)) cats.add(CAT_SCALE);
        if (cats.isEmpty()) cats.add(CAT_NOTE);
        int cat = cats.get(rnd.nextInt(cats.size()));
        boolean nameKey = p.getBoolean(Notes.KEY_NAME_KEY, false);
        switch (cat) {
            case CAT_CHORD:
                return typed(rnd, CAT_CHORD, CHORDS[level(p, Notes.KEY_CHORDS_LEVEL, 2)], nameKey);
            case CAT_SCALE:
                return typed(rnd, CAT_SCALE, SCALES[level(p, Notes.KEY_SCALES_LEVEL, 2)], nameKey);
            default:
                return note(rnd, Notes.notesLevel(p) == 0);
        }
    }

    private static int level(SharedPreferences p, String key, int max) {
        return Math.max(0, Math.min(max, p.getInt(key, 0)));
    }

    static Question note(Random rnd, boolean naturalsOnly) {
        Notes.Question q = Notes.newQuestion(rnd, naturalsOnly);
        String[] labels = new String[6];
        int answer = 0;
        for (int i = 0; i < 6; i++) {
            labels[i] = Notes.NAMES[q.options[i]];
            if (q.options[i] == q.answer) answer = i;
        }
        return new Question(CAT_NOTE, new int[]{q.midi}, labels, answer);
    }

    static Question typed(Random rnd, int cat, Type[] pool, boolean nameKey) {
        int root = rnd.nextInt(12);
        int typeIdx = rnd.nextInt(pool.length);
        Type t = pool[typeIdx];
        int base = (cat == CAT_CHORD ? 48 : 53) + root; // chords from C3, scales from F3
        int[] midis = new int[t.intervals.length];
        for (int i = 0; i < midis.length; i++) midis[i] = base + t.intervals[i];

        List<String> options = new ArrayList<>();
        String correct;
        if (!nameKey) {
            correct = t.name;
            options.add(correct);
            List<Type> others = new ArrayList<>();
            for (Type o : pool) if (o != t) others.add(o);
            Collections.shuffle(others, rnd);
            for (int i = 0; i < others.size() && options.size() < 6; i++) options.add(others.get(i).name);
        } else {
            correct = label(root, t);
            LinkedHashSet<String> set = new LinkedHashSet<>();
            set.add(correct);
            // Same root, different type (tests your ear for the chord/scale type)...
            List<Type> others = new ArrayList<>();
            for (Type o : pool) if (o != t) others.add(o);
            Collections.shuffle(others, rnd);
            for (int i = 0; i < others.size() && i < 2; i++) set.add(label(root, others.get(i)));
            // ...same type, different root (tests your ear for the key)...
            List<Integer> roots = new ArrayList<>();
            for (int r = 0; r < 12; r++) if (r != root) roots.add(r);
            Collections.shuffle(roots, rnd);
            for (int i = 0; i < 2; i++) set.add(label(roots.get(i), t));
            // ...and the rest random.
            while (set.size() < 6) set.add(label(rnd.nextInt(12), pool[rnd.nextInt(pool.length)]));
            options.addAll(set);
        }
        Collections.shuffle(options, rnd);
        return new Question(cat, midis, options.toArray(new String[0]), options.indexOf(correct));
    }

    private static String label(int root, Type t) {
        return Notes.NAMES[root] + " " + t.name;
    }
}
