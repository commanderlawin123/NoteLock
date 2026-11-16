package com.notelock.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Synthesises soft piano-like tones, so the app needs no sound files.
 * Plays a single note, a chord (all notes together) or a scale (one after another).
 */
public final class TonePlayer {
    private static final int SAMPLE_RATE = 44100;
    private static final double[] HARMONICS = {1.0, 0.5, 0.3, 0.18, 0.1, 0.06};

    private AudioTrack track;
    private int generation;

    public void play(int category, int[] midis) {
        final int gen;
        synchronized (this) {
            releaseTrack();
            gen = ++generation;
        }
        // Render off the main thread so the screen never stutters.
        new Thread(() -> {
            short[] pcm = render(category, midis);
            synchronized (TonePlayer.this) {
                if (gen != generation) return; // a newer play/stop happened meanwhile
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(pcm.length * 2)
                        .build();
                track.write(pcm, 0, pcm.length);
                track.play();
            }
        }).start();
    }

    public synchronized void stop() {
        generation++;
        releaseTrack();
    }

    private void releaseTrack() {
        if (track != null) {
            try {
                track.stop();
            } catch (IllegalStateException ignored) {
            }
            track.release();
            track = null;
        }
    }

    static short[] render(int category, int[] midis) {
        double[] starts = new double[midis.length];
        double noteLen;
        double decay;
        double total;
        if (category == Music.CAT_SCALE) {
            double step = 0.34;
            for (int i = 0; i < midis.length; i++) starts[i] = i * step;
            noteLen = 0.9;
            decay = 3.5;
            total = starts[midis.length - 1] + 1.3;
        } else if (category == Music.CAT_CHORD) {
            noteLen = 2.2;
            decay = 1.6;
            total = noteLen;
        } else {
            noteLen = 1.6;
            decay = 2.5;
            total = noteLen;
        }

        int n = (int) (SAMPLE_RATE * total);
        double[] mix = new double[n];
        double norm = 0;
        for (double a : HARMONICS) norm += a;

        for (int k = 0; k < midis.length; k++) {
            double freq = 440.0 * Math.pow(2, (midis[k] - 69) / 12.0);
            int s0 = (int) (starts[k] * SAMPLE_RATE);
            int len = Math.min((int) (noteLen * SAMPLE_RATE), n - s0);
            int fade = SAMPLE_RATE / 30;
            for (int i = 0; i < len; i++) {
                double t = (double) i / SAMPLE_RATE;
                double env = Math.min(1.0, t / 0.005) * Math.exp(-t * decay);
                if (i > len - fade) env *= (double) (len - i) / fade;
                double s = 0;
                for (int h = 0; h < HARMONICS.length; h++) {
                    double hf = freq * (h + 1);
                    if (hf >= SAMPLE_RATE / 2.0) break;
                    s += HARMONICS[h] * Math.exp(-t * h * 1.5) * Math.sin(2 * Math.PI * hf * t);
                }
                mix[s0 + i] += s / norm * env;
            }
        }

        double peak = 1e-9;
        for (double v : mix) peak = Math.max(peak, Math.abs(v));
        double gain = 0.85 / peak;
        short[] out = new short[n];
        for (int i = 0; i < n; i++) out[i] = (short) (mix[i] * gain * Short.MAX_VALUE);
        return out;
    }
}
